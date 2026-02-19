package com.designated.calldetector

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.designated.calldetector.data.CallStatus
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.cancel

class CallDetectorService : Service() {
    private var lastProcessedPhoneNumber: String? = null
    private var lastProcessedCallTime: Long = 0
    private val PROCESSING_THRESHOLD_MS = 5000 // 5초 이내의 동일 번호 호출은 중복으로 간주
    private var wasRinging: Boolean = false // 현재 통화 세션에서 RINGING이 발생했는지 추적 (수신/발신 구분용)
    private val TAG = "CallDetectorService"
    private val CHANNEL_ID = "CallDetectorChannel"
    private val NOTIFICATION_ID = 1
    private val CALL_MANAGER_CHANNEL_ID = "CallManagerActivationChannel"
    private val CALL_MANAGER_NOTIFICATION_ID = 2
    // CallLogObserver 비활성화
    // private lateinit var callLogObserver: CallLogObserver
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 서비스 시작 시간을 기록하여 이후 통화만 처리
    private var serviceStartTime: Long = 0
    
    // 개인번호 관리자
    private lateinit var excludeNumberManager: ExcludeNumberManager

    override fun onCreate() {
        super.onCreate()

        // ------ Runtime permission check ------
        val hasReadPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if (!hasReadPhoneState) {
            // 필수 권한이 없으면 서비스 실행을 중단하여 SecurityException으로 인한 크래시를 예방합니다.
            Log.e(TAG, "❌ Required permission (READ_PHONE_STATE) not granted. Stopping service to avoid crash.")
            stopSelf()
            return
        }

        serviceStartTime = System.currentTimeMillis() // 서비스 시작 시간 기록
        sharedPreferences = getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        
        // 개인번호 관리자 초기화
        excludeNumberManager = ExcludeNumberManager(this)
        Log.i(TAG, "📱 개인번호 관리자 초기화 완료. 제외된 번호: ${excludeNumberManager.getExcludeCount()}개")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // CallLogObserver 비활성화 - IDLE 상태 처리만 사용
        // callLogObserver = CallLogObserver(Handler(mainLooper))
        // contentResolver.registerContentObserver(
        //     CallLog.Calls.CONTENT_URI,
        //     true,
        //     callLogObserver
        // )
        
        Log.i(TAG, "📞 CallDetectorService started at: $serviceStartTime")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callState = intent?.getIntExtra("EXTRA_CALL_STATE", TelephonyManager.CALL_STATE_IDLE)
            ?: TelephonyManager.CALL_STATE_IDLE
        val isIncomingCall = intent?.getBooleanExtra("EXTRA_IS_INCOMING", false) ?: false
        val phoneNumber = intent?.getStringExtra("incomingPhoneNumber")

        Log.i(TAG, "📞 Processing call - Phone: $phoneNumber, State: $callState, Incoming: $isIncomingCall")
        // 디버깅을 위해 TelephonyManager 상수 값들을 로그로 남깁니다.
        Log.d(TAG, "TelephonyManager.CALL_STATE_IDLE: ${TelephonyManager.CALL_STATE_IDLE}") // 예상: 0
        Log.d(TAG, "TelephonyManager.CALL_STATE_RINGING: ${TelephonyManager.CALL_STATE_RINGING}") // 예상: 1
        Log.d(TAG, "TelephonyManager.CALL_STATE_OFFHOOK: ${TelephonyManager.CALL_STATE_OFFHOOK}") // 예상: 2

        if (phoneNumber != null) {
            val currentTime = System.currentTimeMillis()

            // 1. Handle call ending (IDLE state) - 원래 로직으로 복원
            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                wasRinging = false // 다음 통화를 위해 RINGING 플래그 리셋
                if (phoneNumber == lastProcessedPhoneNumber) {
                    Log.i(TAG, "📞 Call with $phoneNumber ended (IDLE state received). Processing call data and showing dispatch popup.")
                    
                    // 개인번호 체크 - 제외 번호면 Firebase 업로드 스킵
                    if (excludeNumberManager.isExcludedNumber(phoneNumber)) {
                        Log.i(TAG, "🚫 개인번호로 분류된 전화입니다. Firebase 업로드를 건너뜁니다: $phoneNumber")
                        // 처리 완료 후 리셋
                        lastProcessedPhoneNumber = null
                        lastProcessedCallTime = 0L
                        return START_STICKY
                    }
                    
                    // 수신전화 종료 시 사무실 상태 확인 후 처리
                    serviceScope.launch {
                        val provinceId = sharedPreferences.getString("provinceId", null)
                        val cityId = sharedPreferences.getString("cityId", null)
                        val officeId = sharedPreferences.getString("officeId", null)
                        val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

                        if (provinceId == null || cityId == null || officeId == null || deviceName.isBlank()) {
                            Log.e(TAG, "❌ Error: Province ID, City ID, Office ID, or Device Name not configured. Cannot process call.")
                            return@launch
                        }
                        Log.i(TAG, "ℹ️ Using configuration - Province: $provinceId, City: $cityId, Office: $officeId, Device: $deviceName")

                        val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                        Log.i(TAG, "📞 Contact info for $phoneNumber: Name='$contactName', Address='$contactAddress'")

                        // 사무실 상태에 따라 일반 콜 또는 공유 콜 생성
                        checkOfficeStatusAndSaveCall(
                            provinceId,
                            cityId,
                            officeId,
                            phoneNumber,
                            contactName,
                            contactAddress,
                            deviceName
                        )
                    }
                    
                    // 처리 완료 후 리셋
                    lastProcessedPhoneNumber = null
                    lastProcessedCallTime = 0L
                }
            }
            // 2. Handle incoming call answered (OFFHOOK state) - 단순히 기록만
            else if (callState == TelephonyManager.CALL_STATE_OFFHOOK && isIncomingCall) {
                // 방어 로직: RINGING 없이 OFFHOOK이면 발신 전화 → 스킵
                // 수신전화는 반드시 RINGING → OFFHOOK 순서, 발신전화는 OFFHOOK만 발생
                if (!wasRinging) {
                    Log.i(TAG, "⚠️ OFFHOOK without prior RINGING - outgoing call detected (isIncoming flag was stale), skipping")
                    return START_STICKY
                }

                // Check if this OFFHOOK is a duplicate for the *current* call session
                if (phoneNumber == lastProcessedPhoneNumber && (currentTime - lastProcessedCallTime) < PROCESSING_THRESHOLD_MS) {
                    Log.w(TAG, "⚠️ Duplicate OFFHOOK event for $phoneNumber within threshold. Skipping processing.")
                    return START_STICKY
                }

                Log.i(TAG, "✅ Incoming call answered (OFFHOOK). Recording call session - will process when call ends.")
                // Set the lock *only after* confirming it's a new processable OFFHOOK
                lastProcessedPhoneNumber = phoneNumber
                lastProcessedCallTime = currentTime

                // OFFHOOK 상태에서는 단순히 기록만 하고, 실제 처리는 IDLE 상태에서 수행
            }
            // 3. Handle incoming call ringing (RINGING state) - 마감 시 빠른 SMS 발송
            else if (callState == TelephonyManager.CALL_STATE_RINGING && isIncomingCall) {
                wasRinging = true // 수신전화 RINGING 발생 기록
                Log.i(TAG, "📞 Incoming call ringing from: $phoneNumber (wasRinging set to true)")
                
                // 마감 상태인지 확인 후 2-3초 후 SMS 발송
                serviceScope.launch {
                    val provinceId = sharedPreferences.getString("provinceId", null)
                    val cityId = sharedPreferences.getString("cityId", null)
                    val officeId = sharedPreferences.getString("officeId", null)

                    if (provinceId != null && cityId != null && officeId != null) {
                        checkOfficeStatusForQuickResponse(provinceId, cityId, officeId, phoneNumber)
                    }
                }
            } else {
                // 기타 상태들
                Log.i(TAG, "ℹ️ Other call state (State: $callState, Incoming: $isIncomingCall). No action taken.")
            }
        } else {
            Log.w(TAG, "⚠️ Phone number is null. Cannot process call. State: $callState, Incoming: $isIncomingCall")
        }
        // 시스템에 의해 종료되어도 자동으로 재시작되도록 START_STICKY 반환
        // 콜 감지 서비스는 지속적으로 실행되어야 하므로 자동 재시작 필요
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getContactInfo(context: Context, phoneNumber: String): Pair<String?, String?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CONTACTS permission not granted. Cannot fetch contact info.")
            return Pair(null, null)
        }

        var contactName: String? = null
        var contactAddress: String? = null
        val normalizedPhoneNumber = Uri.encode(phoneNumber)
        val lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, normalizedPhoneNumber)
        val projection = arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME)

        var cursor: Cursor? = null
        var dataCursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(lookupUri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val contactIdIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)

                if (contactIdIndex >= 0 && nameIndex >= 0) {
                    val contactId = cursor.getString(contactIdIndex)
                    contactName = cursor.getString(nameIndex)

                    val dataProjection = arrayOf(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                    val dataSelection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
                    val dataSelectionArgs = arrayOf(contactId, ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)

                    dataCursor = context.contentResolver.query(
                        ContactsContract.Data.CONTENT_URI,
                        dataProjection,
                        dataSelection,
                        dataSelectionArgs,
                        null
                    )

                    if (dataCursor != null && dataCursor.moveToFirst()) {
                         val addressIndex = dataCursor.getColumnIndex(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                         if (addressIndex >= 0) {
                             contactAddress = dataCursor.getString(addressIndex)
                         }
                    }
                } else {
                    Log.w(TAG, "Could not find required columns ('_ID', 'DISPLAY_NAME') in PhoneLookup cursor.")
                }
            } else {
                Log.d(TAG, "No contact found for phone number: $phoneNumber")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contact info for $phoneNumber", e)
        } finally {
            cursor?.close()
            dataCursor?.close()
        }

        return Pair(contactName, contactAddress)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "통화 감지 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "통화 기록을 감지하고 Firebase에 저장합니다."
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("통화 감지 서비스")
            .setContentText("통화 기록을 감지하고 있습니다.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
    }

    /**
     * 새 콜 감지 시 MainActivity를 포그라운드로 전환하고 팝업 트리거
     */
    private fun bringMainActivityToForegroundForNewCall(callId: String, phoneNumber: String, contactName: String?, contactAddress: String?, provinceId: String, cityId: String, officeId: String) {
        try {
            Log.i(TAG, "🔍 [DEBUG] bringMainActivityToForegroundForNewCall 시작")
            Log.i(TAG, "🔍 [DEBUG] 파라미터 - callId: $callId, phoneNumber: $phoneNumber, contactName: $contactName, contactAddress: $contactAddress")
            Log.i(TAG, "🔍 [DEBUG] 파라미터 - provinceId: $provinceId, cityId: $cityId, officeId: $officeId")

            // MainActivity로 이동하면서 콜 정보 전달
            val intent = Intent(this, com.designated.calldetector.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION

                // Android 10+ 백그라운드 Activity 실행을 위한 추가 플래그
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }

                // 새 콜 팝업을 위한 액션과 데이터 (Firebase ID 포함)
                action = com.designated.calldetector.MainActivity.ACTION_SHOW_DISPATCH_POPUP
                putExtra("callId", callId) // Firebase document ID 추가
                putExtra("phoneNumber", phoneNumber)
                contactName?.let { putExtra("contactName", it) }
                contactAddress?.let { putExtra("contactAddress", it) }
                putExtra("provinceId", provinceId)
                putExtra("cityId", cityId)
                putExtra("officeId", officeId)
                putExtra("deviceName", getDeviceName(this@CallDetectorService))
                putExtra("FROM_NEW_CALL", true) // 새 콜에서 왔음을 표시

                // Android 10+ 예외 조건들 활용
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }

            // WindowManager 방식 - 콜매니저와 동일한 방식
            showOverlayPopup(callId, phoneNumber, contactName, contactAddress, provinceId, cityId, officeId)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show overlay popup", e)
            // 폴백으로 Full-Screen Intent 사용
            Log.w(TAG, "⚠️ Using Full-Screen Intent fallback")
            showFullScreenIntentNotification(callId, phoneNumber, contactName, contactAddress, provinceId, cityId, officeId)
        }

        // 기존 catch 블록들 (주석 처리 - callId 파라미터 추가됨)
        /*
        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ Direct activity start failed for new call, using Full-Screen Intent fallback")
            showFullScreenIntentNotification(callId, phoneNumber, contactName, contactAddress, provinceId, cityId, officeId)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to bring MainActivity to foreground for new call", e)
            Log.w(TAG, "⚠️ Using Full-Screen Intent fallback instead")
            showFullScreenIntentNotification(callId, phoneNumber, contactName, contactAddress, provinceId, cityId, officeId)
        }
        */
    }
    
    /**
     * WindowManager를 사용한 오버레이 팝업 생성 (콜매니저 방식)
     */
    private fun showOverlayPopup(callId: String, phoneNumber: String, contactName: String?, contactAddress: String?, provinceId: String, cityId: String, officeId: String) {
        try {
            Log.i(TAG, "🎯 showOverlayPopup 시작 - 권한 체크")

            // SYSTEM_ALERT_WINDOW 권한 체크
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Log.w(TAG, "❌ SYSTEM_ALERT_WINDOW 권한 없음 - Full-Screen Intent로 폴백")
                showFullScreenIntentNotification(callId, phoneNumber, contactName, contactAddress, provinceId, cityId, officeId)
                return
            }


            // SYSTEM_ALERT_WINDOW 권한이 있으므로 DispatchActivity를 바로 실행 가능
            val dispatchIntent = Intent(this, com.designated.calldetector.ui.DispatchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

                putExtra("EXTRA_CALL_ID", callId) // Firebase document ID 추가
                putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
                contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
                putExtra("EXTRA_PROVINCE_ID", provinceId)
                putExtra("EXTRA_CITY_ID", cityId)
                putExtra("EXTRA_OFFICE_ID", officeId)
                putExtra("EXTRA_DEVICE_NAME", getDeviceName(this@CallDetectorService))
            }

            startActivity(dispatchIntent)
            Log.i(TAG, "🎉 SYSTEM_ALERT_WINDOW 권한으로 DispatchActivity 직접 실행 성공!")

        } catch (e: Exception) {
            Log.e(TAG, "❌ 오버레이 팝업 생성 실패", e)
            throw e // 외부 catch에서 폴백 처리
        }
    }

    private fun showFullScreenIntentNotification(callId: String, phoneNumber: String, contactName: String?, contactAddress: String?, provinceId: String, cityId: String, officeId: String) {
        try {
            Log.i(TAG, "🚀 [DEBUG] showFullScreenIntentNotification 시작")

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 채널 생성 (Android 8.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "NEW_CALL_CHANNEL",
                    "새로운 통화",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "새로운 통화 알림"
                    setBypassDnd(true) // 방해 금지 모드 우회
                    setShowBadge(true)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                notificationManager.createNotificationChannel(channel)
            }

            // MainActivity 실행 인텐트 (Full-Screen용)
            val intent = Intent(this, com.designated.calldetector.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION

                // Android 10+ 백그라운드 Activity 실행을 위한 추가 플래그
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }

                // 새 콜 팝업을 위한 액션과 데이터
                action = com.designated.calldetector.MainActivity.ACTION_SHOW_DISPATCH_POPUP
                putExtra("callId", callId) // Firebase document ID 추가
                putExtra("phoneNumber", phoneNumber)
                contactName?.let { putExtra("contactName", it) }
                contactAddress?.let { putExtra("contactAddress", it) }
                putExtra("provinceId", provinceId)
                putExtra("cityId", cityId)
                putExtra("officeId", officeId)
                putExtra("deviceName", getDeviceName(this@CallDetectorService))
                putExtra("FROM_FULL_SCREEN_INTENT", true) // Full-Screen Intent에서 왔음을 표시
            }
            
            // Full-Screen Intent용 PendingIntent
            val fullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }
            
            // 일반 클릭용 PendingIntent
            val contentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            } else {
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
            }
            
            // Full-Screen Intent 알림 생성
            val notification = NotificationCompat.Builder(this, "NEW_CALL_CHANNEL")
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("새로운 통화")
                .setContentText("전화번호: $phoneNumber")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .setFullScreenIntent(fullScreenIntent, true) // Full-Screen Intent 설정
                .setOngoing(false)
                .setTimeoutAfter(30000) // 30초 후 자동 제거
                .build()
            
            notificationManager.notify(998, notification)
            Log.i(TAG, "🚀 Full-Screen Intent 알림 생성 완료 - 백그라운드에서 팝업이 나타납니다")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show Full-Screen Intent notification", e)
        }
    }

    private fun showCallManagerNotification(context: Context, callId: String, phoneNumber: String?, contactName: String?, contactAddress: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALL_MANAGER_CHANNEL_ID,
                "통화 처리 알림", // Notification channel name for user
                NotificationManager.IMPORTANCE_HIGH // 헤드업 알림을 위해 HIGH로 설정 (소리/진동은 별도 비활성화)
            ).apply {
                description = "수신된 통화에 대한 처리 알림입니다."
                // 알람 소리와 진동 비활성화
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel $CALL_MANAGER_CHANNEL_ID created.")
        }

        val intent = Intent(context, com.designated.calldetector.ui.DispatchActivity::class.java).apply {
            putExtra("EXTRA_CALL_ID", callId)
            putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
            contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
            contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
            putExtra("EXTRA_PROVINCE_ID", getProvinceId(context))
            putExtra("EXTRA_CITY_ID", getCityId(context))
            putExtra("EXTRA_OFFICE_ID", getOfficeId(context))
            putExtra("EXTRA_DEVICE_NAME", getDeviceName(context))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        // DispatchActivity 실행을 위한 PendingIntent 생성
        val packageManager = context.packageManager
        val pendingIntent: PendingIntent? = try {
            val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            PendingIntent.getActivity(context, 0, intent, pendingIntentFlag)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create PendingIntent for DispatchActivity", e)
            null
        }

        val notificationBuilder = NotificationCompat.Builder(context, CALL_MANAGER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Default Android info icon
            .setContentTitle("새로운 통화 접수")
            .setContentText("전화번호: ${phoneNumber ?: "알 수 없음"} (${contactName ?: "이름 없음"})")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 헤드업 알림을 위해 HIGH로 설정
            .setCategory(NotificationCompat.CATEGORY_STATUS) // CALL에서 STATUS로 변경하여 알람 제거
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .setAutoCancel(true) // Notification disappears when tapped
            .setOngoing(false) // Not an ongoing task
            .setSound(null) // 알람 소리 제거
            .setVibrate(null) // 진동 제거
            .setSilent(true) // 소리/진동 없이 조용하지만 즉시 표시

        notificationManager.notify(CALL_MANAGER_NOTIFICATION_ID, notificationBuilder.build())
        Log.i(TAG, "📞 CallManager activation notification posted for call ID: $callId (silent)")
    }

    // CallLogObserver 비활성화 - IDLE 상태 처리만 사용
    /*
    inner class CallLogObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(change: Boolean) {
            super.onChange(change)
            Log.i(TAG, "🔍 CallLogObserver.onChange() 호출됨")
            // CallLog 변경 감지 시 마지막 통화 확인
            checkLastCallLog()
        }
    }
    */
    
    // CallLogObserver 관련 함수 비활성화
    /*
    private fun checkLastCallLog() {
        // 사용하지 않음 - IDLE 상태 처리만 사용
    }
    */
    
    // CallLogObserver 관련 함수 비활성화
    /*
    private suspend fun handleCallEndedImmediately(phoneNumber: String, cachedName: String?) {
        // 사용하지 않음 - IDLE 상태 처리만 사용
    }
    */

    /**
     * 콜 데이터 생성 헬퍼 함수
     */
    private fun createCallData(
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        provinceId: String,
        cityId: String,
        officeId: String,
        deviceName: String
    ): HashMap<String, Any> {
        return hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "customerName" to (contactName ?: phoneNumber),
            "customerAddress" to (contactAddress ?: ""),
            "status" to CallStatus.WAITING.firestoreValue,
            "timestamp" to FieldValue.serverTimestamp(),
            "detectedTimestamp" to FieldValue.serverTimestamp(),
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "deviceName" to deviceName,
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis(),
            "fromCallDetector" to true // 독립 콜디텍터에서 생성된 콜 (콜매니저에서 팝업 표시 방지)
        )
    }

    /**
     * 부재중 전화에 대한 사무실 상태 확인 및 처리
     */
    private suspend fun checkOfficeStatusForMissedCall(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?
    ) {
        try {
            val document = db.collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .get()
                .await()

            val officeStatus = document.getString("status") ?: "OPEN"
            val officeName = document.getString("name") ?: "사무실"

            Log.i(TAG, "🌙 부재중 전화 - 사무실 상태: $officeStatus")

            if (officeStatus == "CLOSED") {
                Log.i(TAG, "✅ 마감 상태 - 부재중 전화 자동 처리 시작")

                val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

                // 공유콜 생성
                val sharedCallData = hashMapOf<String, Any>(
                    "phoneNumber" to phoneNumber,
                    "sourceProvinceId" to provinceId,
                    "sourceCityId" to cityId,
                    "sourceOfficeId" to officeId,
                    "deviceName" to deviceName,
                    "status" to "OPEN",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "callType" to "MISSED_AFTER_HOURS", // 마감 후 부재중
                    "timestampClient" to System.currentTimeMillis()
                )

                contactName?.let { sharedCallData["customerName"] = it }

                db.collection("shared_calls")
                    .add(sharedCallData)
                    .addOnSuccessListener {
                        Log.i(TAG, "✅ 부재중 전화 공유콜 생성 완료 - Cloud Functions에서 FCM 처리")
                        // SMS 발송 제거 - Cloud Functions에서 FCM으로 처리
                        // sendAutoSMS(phoneNumber, officeName)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ 부재중 전화 공유콜 생성 실패", e)
                    }
            } else {
                Log.i(TAG, "ℹ️ 운영 중이므로 부재중 전화 자동 처리 안함")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 부재중 전화 처리 실패", e)
        }
    }

    /**
     * 사무실 상태 확인 후 일반 콜 또는 공유 콜 생성
     */
    private suspend fun checkOfficeStatusAndSaveCall(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        try {
            // 사무실 상태 확인
            val document = db.collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .get()
                .await()

            val officeStatus = document.getString("status") ?: "OPEN"
            val officeName = document.getString("name") ?: "사무실"

            Log.i(TAG, "사무실 상태: $officeStatus")

            when (officeStatus) {
                "CLOSED" -> {
                    // 마감 상태: shared_calls에 저장 (Cloud Functions에서 FCM 알림 처리)
                    createSharedCall(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                    // SMS 발송 제거 - Cloud Functions에서 FCM으로 처리
                    // sendAutoSMS(phoneNumber, officeName)
                }
                else -> {
                    // 운영중: 기존대로 calls에 저장
                    createNormalCall(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "사무실 상태 확인 실패", e)
            // 실패시 기본적으로 calls에 저장
            createNormalCall(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)
        }
    }
    
    /**
     * 일반 콜 생성 (운영중)
     */
    private suspend fun createNormalCall(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        try {
            // ✅ 추가: customerInfo 컬렉션 확인하여 앱 회원 여부 판별
            Log.i(TAG, "📱 customerInfo 컬렉션 조회 시작: $phoneNumber")
            val customerDoc = db
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("customerInfo")
                .document(phoneNumber)
                .get()
                .await()

            val isAppCustomer = customerDoc.exists()
            val customerHomeAddress = if (isAppCustomer) {
                customerDoc.getString("homeAddress") ?: ""
            } else {
                ""
            }
            Log.i(TAG, "📱 앱 회원 여부: $isAppCustomer (phoneNumber: $phoneNumber)")
            Log.i(TAG, "📱 앱 회원 집주소: $customerHomeAddress")

            val callData = hashMapOf<String, Any>(
                "phoneNumber" to phoneNumber,
                "customerName" to (contactName ?: ""),
                "customerAddress" to (customerHomeAddress.takeIf { it.isNotBlank() } ?: contactAddress ?: ""),
                "status" to CallStatus.WAITING.firestoreValue,
                "timestamp" to FieldValue.serverTimestamp(),
                "detectedTimestamp" to FieldValue.serverTimestamp(),
                "provinceId" to provinceId,
                "cityId" to cityId,
                "officeId" to officeId,
                "deviceName" to deviceName,
                "callType" to "수신",
                "timestampClient" to System.currentTimeMillis(),
                "fromCallDetector" to true, // 독립 콜디텍터에서 생성된 콜 (콜매니저에서 팝업 표시 방지)
                "isAppCustomer" to isAppCustomer,  // ✅ 추가: 앱 회원 여부
                "customerId" to if (isAppCustomer) phoneNumber else "",  // ✅ 추가: 회원이면 phoneNumber
                "createdFrom" to "phone"  // ✅ 추가: 전화로 생성됨
            )

            // Firebase에 먼저 업로드하고 ID를 받아서 팝업 생성 (콜매니저와 동일한 방식)
            val targetPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/calls"
            Log.i(TAG, "🚨 About to upload callData: $callData")
            Log.i(TAG, "🚨 fromCallDetector value: ${callData["fromCallDetector"]}")
            Log.i(TAG, "🚨 isAppCustomer value: ${callData["isAppCustomer"]}")

            val documentReference = db.collection(targetPath)
                .add(callData)
                .await()

            Log.i(TAG, "✅ Call data saved to Firestore with ID: ${documentReference.id}")

            // Firebase ID를 받은 후 팝업 생성
            bringMainActivityToForegroundForNewCall(
                documentReference.id,
                phoneNumber,
                contactName,
                contactAddress,
                provinceId,
                cityId,
                officeId
            )

            Log.i(TAG, "✅ Call end processing completed")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to create normal call: ${e.message}", e)
            // 실패 시에도 임시 ID로 팝업 생성
            val tempCallId = "temp_${System.currentTimeMillis()}"
            bringMainActivityToForegroundForNewCall(
                tempCallId,
                phoneNumber,
                contactName,
                contactAddress,
                provinceId,
                cityId,
                officeId
            )
        }
    }
    
    /**
     * 공유 콜 생성 (마감 상태)
     */
    private fun createSharedCall(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        val sharedCallData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "sourceProvinceId" to provinceId,
            "sourceCityId" to cityId,
            "sourceOfficeId" to officeId,
            "deviceName" to deviceName,
            "status" to "OPEN",
            "timestamp" to FieldValue.serverTimestamp(),
            "callType" to "AFTER_HOURS", // 퇴근 후 콜
            "timestampClient" to System.currentTimeMillis(),
            "fromCallDetector" to true // 독립 콜디텍터에서 생성된 콜 (콜매니저에서 팝업 표시 방지)
        )

        contactName?.let { sharedCallData["customerName"] = it }
        contactAddress?.let { sharedCallData["customerAddress"] = it }

        db.collection("shared_calls")
            .add(sharedCallData)
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "✅ Shared call created with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to create shared call: ${e.message}", e)
            }
    }
    
    /**
     * 자동 SMS 발송 (비활성화 - Cloud Functions에서 FCM으로 대체)
     * 나중에 SMS Gateway 구축 시 재활용 가능
     */
    /*
    private fun sendAutoSMS(phoneNumber: String, officeName: String) {
        try {
            // SMS 권한 체크
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "SMS 권한이 없습니다.")
                return
            }

            val message = "[$officeName] 운영시간이 종료되었습니다. 잠시 후 다시 연락드리겠습니다."

            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Log.i(TAG, "✅ 자동 SMS 발송 완료: $phoneNumber")

        } catch (e: Exception) {
            Log.e(TAG, "SMS 발송 실패", e)
        }
    }
    */

    /**
     * 마감 상태 확인 후 빠른 SMS 발송 (RINGING 상태용)
     */
    private suspend fun checkOfficeStatusForQuickResponse(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String
    ) {
        try {
            val document = db.collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .get()
                .await()

            val officeStatus = document.getString("status") ?: "OPEN"
            val officeName = document.getString("name") ?: "사무실"

            Log.i(TAG, "📞 RINGING 상태에서 사무실 상태 확인: $officeStatus")

            if (officeStatus == "CLOSED") {
                Log.i(TAG, "🌙 마감 상태 - 2초 후 SMS 발송 시작")

                // 2초 대기 후 SMS 발송
                kotlinx.coroutines.delay(2000)

                val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

                // 공유콜 생성 (RINGING에서)
                createSharedCallFromRinging(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)

                // SMS 발송 제거 - Cloud Functions에서 FCM으로 처리
                // sendAutoSMS(phoneNumber, officeName)

                Log.i(TAG, "✅ 마감 시 빠른 응답 완료 (RINGING → Cloud Functions FCM)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 빠른 응답 처리 실패", e)
        }
    }
    
    /**
     * RINGING 상태에서 공유 콜 생성
     */
    private fun createSharedCallFromRinging(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        val sharedCallData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "sourceProvinceId" to provinceId,
            "sourceCityId" to cityId,
            "sourceOfficeId" to officeId,
            "deviceName" to deviceName,
            "status" to "OPEN",
            "timestamp" to FieldValue.serverTimestamp(),
            "callType" to "AFTER_HOURS_QUICK", // 마감 후 빠른 응답
            "timestampClient" to System.currentTimeMillis(),
            "fromCallDetector" to true, // 독립 콜디텍터에서 생성된 콜 (콜매니저에서 팝업 표시 방지)
            "fromRinging" to true // RINGING 상태에서 생성됨을 표시
        )

        contactName?.let { sharedCallData["customerName"] = it }
        contactAddress?.let { sharedCallData["customerAddress"] = it }

        db.collection("shared_calls")
            .add(sharedCallData)
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "✅ Quick response shared call created with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to create quick response shared call: ${e.message}", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // CallLogObserver 비활성화
        // contentResolver.unregisterContentObserver(callLogObserver)

        // 코루틴 스코프 안전하게 해제 (경합 조건 방지)
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling serviceScope: ${e.message}")
        }
        Log.i(TAG, "Service destroyed.")
    }
    
    // 헬퍼 함수들
    private fun getProvinceId(context: Context): String {
        val prefs = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        return prefs.getString("provinceId", "") ?: ""
    }

    private fun getCityId(context: Context): String {
        val prefs = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        return prefs.getString("cityId", "") ?: ""
    }

    private fun getOfficeId(context: Context): String {
        val prefs = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        return prefs.getString("officeId", "") ?: ""
    }

    private fun getDeviceName(context: Context): String {
        val prefs = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        return prefs.getString("deviceName", "") ?: ""
    }
}
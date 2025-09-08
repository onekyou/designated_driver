package com.designated.callmanager.service

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
import android.os.IBinder
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.designated.callmanager.data.CallStatus
import com.designated.callmanager.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CallDetectorService : Service() {
    private var lastProcessedPhoneNumber: String? = null
    private var lastProcessedCallTime: Long = 0
    private val PROCESSING_THRESHOLD_MS = 5000 // 5초 이내의 동일 번호 호출은 중복으로 간주
    private val TAG = "CallDetectorService"
    private val CHANNEL_ID = "CallDetectorChannel"
    private val NOTIFICATION_ID = 1
    private val CALL_MANAGER_CHANNEL_ID = "CallManagerActivationChannel"
    private val CALL_MANAGER_NOTIFICATION_ID = 2
    private lateinit var callLogObserver: CallLogObserver
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.IO)
    
    // 서비스 시작 시간을 기록하여 이후 통화만 처리
    private var serviceStartTime: Long = 0
    
    companion object {
        @Volatile
        private var isRunning = false
        
        fun isServiceRunning(): Boolean = isRunning
    }

    override fun onCreate() {
        super.onCreate()

        // ------ Runtime permission check ------
        val hasReadCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasReadPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if (!hasReadCallLog || !hasReadPhoneState) {
            // 필수 권한이 없으면 서비스 실행을 중단하여 SecurityException으로 인한 크래시를 예방합니다.
            Log.e(TAG, "❌ Required permissions (READ_CALL_LOG / READ_PHONE_STATE) not granted. Stopping service to avoid crash.")
            stopSelf()
            return
        }

        serviceStartTime = System.currentTimeMillis() // 서비스 시작 시간 기록
        sharedPreferences = getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        callLogObserver = CallLogObserver(Handler(mainLooper))
        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver
        )
        
        isRunning = true
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
            
            // 서비스 시작 시간 이후의 통화만 처리 (5초 여유 시간 추가)
            if (currentTime < serviceStartTime + 5000) {
                Log.i(TAG, "⏰ Ignoring call from $phoneNumber - occurred before/during service startup (current: $currentTime, serviceStart: $serviceStartTime)")
                return START_NOT_STICKY
            }
            
            // 1. Handle call termination (IDLE state) - 통화 종료 시 팝업 생성
            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                // phoneNumber가 null일 경우 마지막 처리된 번호 사용
                val finalPhoneNumber = phoneNumber ?: lastProcessedPhoneNumber
                
                if (finalPhoneNumber != null && finalPhoneNumber == lastProcessedPhoneNumber) {
                    Log.i(TAG, "📞 Call with $finalPhoneNumber ended (IDLE state received). Processing call data and showing popup.")
                    
                    // 수신전화 종료 시 Firestore 저장 + 팝업 생성
                    if (isIncomingCall) {
                        Log.i(TAG, "🚀 Incoming call ended - processing call and showing popup")
                        
                        // 콜디텍터가 활성화되어 있는지 확인
                        val sharedPreferences = getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)
                        val isCallDetectionEnabled = sharedPreferences.getBoolean("call_detection_enabled", false)
                        Log.i(TAG, "🔧 Call detection enabled setting: $isCallDetectionEnabled")
                        if (!isCallDetectionEnabled) {
                            Log.w(TAG, "❌ Call detection is disabled in settings. Skipping processing.")
                            return START_NOT_STICKY
                        }
                        
                        // 연락처 정보 조회
                        val (contactName, contactAddress) = getContactInfo(applicationContext, finalPhoneNumber)
                        
                        // 사무실 상태에 따라 일반 콜 또는 공유 콜 생성
                        val regionId = sharedPreferences.getString("regionId", null)
                        val officeId = sharedPreferences.getString("officeId", null)
                        
                        if (regionId != null && officeId != null) {
                            Log.i(TAG, "📝 Processing call with office status check - Region: $regionId, Office: $officeId")
                            
                            val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
                            
                            // 사무실 상태 확인 후 콜 처리
                            checkOfficeStatusAndSaveCall(
                                regionId,
                                officeId,
                                finalPhoneNumber,
                                contactName,
                                contactAddress,
                                deviceName
                            )
                        } else {
                            Log.w(TAG, "⚠️ RegionId or OfficeId not configured - only bringing to foreground")
                            bringCallManagerToForeground()
                        }
                    }
                    
                    // 처리 완료 후 리셋
                    lastProcessedPhoneNumber = null
                    lastProcessedCallTime = 0L
                } else if (finalPhoneNumber == null) {
                    Log.w(TAG, "⚠️ Both phoneNumber and lastProcessedPhoneNumber are null in IDLE state")
                } else {
                    Log.d(TAG, "📞 IDLE state for different number ($finalPhoneNumber vs $lastProcessedPhoneNumber) - ignoring")
                }
            }
            // 2. Handle incoming call answered (OFFHOOK state) - 단순히 기록만
            else if (callState == TelephonyManager.CALL_STATE_OFFHOOK && isIncomingCall) {
                // Check if this OFFHOOK is a duplicate for the *current* call session
                if (phoneNumber == lastProcessedPhoneNumber && (currentTime - lastProcessedCallTime) < PROCESSING_THRESHOLD_MS) {
                    Log.w(TAG, "⚠️ Duplicate OFFHOOK event for $phoneNumber within threshold. Skipping processing.")
                    return START_NOT_STICKY
                }

                Log.i(TAG, "✅ Incoming call answered (OFFHOOK). Recording call session - will process when call ends.")
                // Set the lock *only after* confirming it's a new processable OFFHOOK
                lastProcessedPhoneNumber = phoneNumber
                lastProcessedCallTime = currentTime

                // OFFHOOK 상태에서는 단순히 기록만 하고, 실제 처리는 IDLE 상태에서 수행
            }
            // 3. Handle incoming call ringing (RINGING state) - 마감 시 빠른 SMS 발송
            else if (callState == TelephonyManager.CALL_STATE_RINGING && isIncomingCall) {
                Log.i(TAG, "📞 CallManager: Incoming call ringing from: $phoneNumber")
                
                // 마감 상태인지 확인 후 2-3초 후 SMS 발송
                serviceScope.launch {
                    val regionId = sharedPreferences.getString("regionId", null)
                    val officeId = sharedPreferences.getString("officeId", null)
                    
                    Log.i(TAG, "🔍 CallManager RINGING - regionId: $regionId, officeId: $officeId")
                    
                    if (regionId != null && officeId != null) {
                        checkOfficeStatusForQuickResponse(regionId, officeId, phoneNumber)
                    } else {
                        Log.w(TAG, "⚠️ CallManager RINGING - regionId or officeId is null, cannot send SMS")
                        // null인 경우 Firebase에서 직접 가져오기 시도
                        tryGetOfficeInfoFromFirebase(phoneNumber)
                    }
                }
            } else {
                // 기타 상태들
                Log.i(TAG, "ℹ️ Other call state (State: $callState, Incoming: $isIncomingCall). No action taken.")
            }
        } else {
            Log.w(TAG, "⚠️ Phone number is null. Cannot process call. State: $callState, Incoming: $isIncomingCall")
        }
        // 백그라운드에서 오래 실행되는 작업이 아니므로 START_NOT_STICKY 반환
        // 서비스가 시스템에 의해 종료된 후 자동으로 다시 시작되지 않도록 함.
        // 명시적으로 startService 또는 startForegroundService를 호출할 때만 실행되도록 의도.
        return START_NOT_STICKY
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

    inner class CallLogObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(change: Boolean) {
            super.onChange(change)
            Log.i(TAG, "📞 CallLog 변경 감지 - 최근 통화 확인 중...")
            checkLastCallLog()
        }
    }
    
    /**
     * CallLog에서 최근 부재중 전화 확인
     */
    private fun checkLastCallLog() {
        serviceScope.launch {
            try {
                // READ_CALL_LOG 권한 확인
                if (ContextCompat.checkSelfPermission(this@CallDetectorService, Manifest.permission.READ_CALL_LOG) 
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "READ_CALL_LOG 권한이 없습니다.")
                    return@launch
                }
                
                val projection = arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.CACHED_NAME
                )
                
                val cursor = contentResolver.query(
                    CallLog.Calls.CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${CallLog.Calls.DATE} DESC"
                )
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                        val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                        val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                        val cachedName = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME))
                        
                        Log.d(TAG, "📞 CallManager 최근 통화 - 번호: $number, 타입: $type, 시간: $date, 이름: $cachedName")
                        
                        // 부재중 전화이고, 최근 5초 이내이며, 서비스 시작 이후인지 확인
                        if (type == CallLog.Calls.MISSED_TYPE && 
                            (System.currentTimeMillis() - date) < 5000 &&
                            date > serviceStartTime) {
                            
                            Log.i(TAG, "📞 CallManager 부재중 전화 감지: $number")
                            
                            // 사무실 상태 확인 후 처리
                            val regionId = sharedPreferences.getString("regionId", null)
                            val officeId = sharedPreferences.getString("officeId", null)
                            
                            if (regionId != null && officeId != null) {
                                checkOfficeStatusForMissedCall(regionId, officeId, number, cachedName)
                            } else {
                                Log.w(TAG, "❌ CallManager RegionId 또는 OfficeId가 설정되지 않음")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ CallManager CallLog 확인 실패", e)
            }
        }
    }
    
    /**
     * 부재중 전화에 대한 사무실 상태 확인 및 처리
     */
    private suspend fun checkOfficeStatusForMissedCall(
        regionId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?
    ) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val document = firestore.collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .get()
                .await()
            
            val officeStatus = document.getString("status") ?: "OPEN"
            val officeName = document.getString("name") ?: "사무실"
            
            Log.i(TAG, "📞 CallManager 부재중 전화 - 사무실 상태: $officeStatus")
            
            if (officeStatus == "CLOSED") {
                Log.i(TAG, "🌙 CallManager 마감 상태에서 부재중 전화 처리 시작")
                
                val (fullContactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
                val finalContactName = fullContactName ?: contactName
                
                // 공유콜 생성
                createSharedCallFromMissed(regionId, officeId, phoneNumber, finalContactName, contactAddress, deviceName)
                
                // SMS 발송
                sendAutoSMS(phoneNumber, officeName)
                
                Log.i(TAG, "✅ CallManager 부재중 전화 처리 완료 - 공유콜 생성 및 SMS 발송")
            } else {
                Log.i(TAG, "🏢 CallManager 운영중 - 부재중 전화 무시")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ CallManager 부재중 전화 처리 실패", e)
        }
    }
    
    /**
     * 부재중 전화에서 공유 콜 생성 (CallManager용)
     */
    private fun createSharedCallFromMissed(
        regionId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        val sharedCallData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "sourceRegionId" to regionId,
            "sourceOfficeId" to officeId,
            "targetRegionId" to regionId, // 해당 지역 모든 사무실에 공유
            "deviceName" to deviceName,
            "status" to "OPEN",
            "timestamp" to FieldValue.serverTimestamp(),
            "callType" to "MISSED_CALL", // 부재중 전화
            "timestampClient" to System.currentTimeMillis(),
            "fromMissedCall" to true, // 부재중 전화에서 생성됨을 표시
            "fromCallManager" to true // 콜매니저에서 생성된 콜임을 표시
        )
        
        contactName?.let { sharedCallData["customerName"] = it }
        contactAddress?.let { sharedCallData["customerAddress"] = it }
        
        val firestore = FirebaseFirestore.getInstance()
        firestore.collection("shared_calls")
            .add(sharedCallData)
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "✅ CallManager 부재중 전화 공유콜 생성 완료 - ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ CallManager 부재중 전화 공유콜 생성 실패: ${e.message}", e)
            }
    }

    private fun bringCallManagerToForeground() {
        try {
            // 모든 Android 버전에서 직접 액티비티 시작 시도
            val intent = Intent(this, com.designated.callmanager.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra("BRING_TO_FOREGROUND", true)
                putExtra("FROM_CALL_END", true) // 통화 종료에서 왔음을 표시
                
                // Android 10+ 예외 조건들 활용
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // 1. 시스템 서비스에서 시작하는 것으로 표시
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    // 2. 긴급 상황으로 분류
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }
            
            startActivity(intent)
            Log.i(TAG, "🚀 CallManager brought to foreground directly (attempt)")
            
        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ Direct activity start failed due to background restrictions, using notification fallback")
            showCallEndedNotification()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to bring CallManager to foreground", e)
            showCallEndedNotification()
        }
    }
    
    /**
     * 새 콜 감지 시 CallManager를 포그라운드로 전환하고 팝업 트리거
     */
    private fun bringCallManagerToForegroundForNewCall(callId: String, phoneNumber: String, contactName: String?, contactAddress: String?) {
        try {
            // MainActivity로 이동하면서 콜 정보 전달
            val intent = Intent(this, com.designated.callmanager.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or 
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or 
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
                
                // 새 콜 팝업을 위한 액션과 데이터
                action = com.designated.callmanager.MainActivity.ACTION_SHOW_CALL_POPUP
                putExtra(com.designated.callmanager.MainActivity.EXTRA_CALL_ID, callId)
                putExtra("phoneNumber", phoneNumber)
                contactName?.let { putExtra("contactName", it) }
                contactAddress?.let { putExtra("contactAddress", it) }
                putExtra("FROM_NEW_CALL", true) // 새 콜에서 왔음을 표시
                
                // Android 10+ 예외 조건들 활용
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }
            
            startActivity(intent)
            Log.i(TAG, "🚀 CallManager brought to foreground for new call: $callId")
            
        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ Direct activity start failed for new call, using internal broadcast fallback")
            // 포그라운드 전환이 실패하면 내부 브로드캐스트로 폴백
            val internalIntent = Intent("com.designated.callmanager.INTERNAL_SHOW_CALL_DIALOG").apply {
                putExtra("EXTRA_CALL_ID", callId)
                putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
                contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
            }
            sendBroadcast(internalIntent)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to bring CallManager to foreground for new call", e)
            // 실패 시 내부 브로드캐스트로 폴백
            val internalIntent = Intent("com.designated.callmanager.INTERNAL_SHOW_CALL_DIALOG").apply {
                putExtra("EXTRA_CALL_ID", callId)
                putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
                contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
            }
            sendBroadcast(internalIntent)
        }
    }
    
    private fun showCallEndedNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 채널 생성 (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "CALL_ENDED_CHANNEL",
                "통화 종료 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "수신 통화 종료 시 콜매니저 실행을 위한 알림"
                enableLights(true)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // 콜매니저 실행 인텐트
        val intent = Intent(this, com.designated.callmanager.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("BRING_TO_FOREGROUND", true)
            putExtra("FROM_CALL_END", true)
        }
        
        val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        
        // Full-Screen Intent로 강제 포그라운드 전환 시도
        val fullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }
        
        // 알림 생성 (Full-Screen Intent 포함)
        val notification = NotificationCompat.Builder(this, "CALL_ENDED_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("통화 종료")
            .setContentText("콜매니저로 자동 전환")
            .setPriority(NotificationCompat.PRIORITY_MAX) // 최대 우선순위
            .setCategory(NotificationCompat.CATEGORY_CALL) // 통화 카테고리로 설정
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true) // 전체 화면 인텐트 - 핵심!
            .setAutoCancel(true)
            .setOngoing(false)
            .build()
        
        notificationManager.notify(999, notification)
        Log.i(TAG, "🚀 Full-screen intent notification posted - should bring app to foreground")
    }
    
    /**
     * 사무실 상태 확인 후 일반 콜 또는 공유 콜 생성
     */
    private fun checkOfficeStatusAndSaveCall(
        regionId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        Log.i(TAG, "🔍 CallManager 사무실 상태 확인 시작 - Region: $regionId, Office: $officeId, Phone: $phoneNumber")
        
        // 사무실 상태 확인
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    Log.e(TAG, "❌ CallManager 사무실 문서가 존재하지 않음: regions/$regionId/offices/$officeId")
                    createNormalCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                    return@addOnSuccessListener
                }
                
                val officeStatus = document.getString("status") ?: "OPEN"
                val officeName = document.getString("name") ?: "사무실"
                
                Log.i(TAG, "📊 CallManager 사무실 상태 확인 완료 - 상태: $officeStatus, 이름: $officeName")
                
                when (officeStatus) {
                    "CLOSED" -> {
                        Log.i(TAG, "🌙 CallManager 사무실 마감 상태 - 공유콜 생성 및 SMS 발송 진행")
                        // 마감 상태: shared_calls에 저장 및 자동 SMS 발송
                        createSharedCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                        sendAutoSMS(phoneNumber, officeName)
                    }
                    else -> {
                        Log.i(TAG, "🏢 CallManager 사무실 운영중 - 일반 콜 생성 및 팝업 표시")
                        // 운영중: 기존대로 calls에 저장 + 팝업 생성
                        createNormalCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ CallManager 사무실 상태 확인 실패: ${e.message}", e)
                // 실패시 기본적으로 calls에 저장
                createNormalCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
            }
    }
    
    /**
     * 일반 콜 생성 (운영중)
     */
    private fun createNormalCall(
        regionId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        val callData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "customerName" to (contactName ?: ""),
            "customerAddress" to (contactAddress ?: ""),
            "status" to CallStatus.WAITING.firestoreValue,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "detectedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "regionId" to regionId,
            "officeId" to officeId,
            "deviceName" to deviceName,
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis(),
            "fromCallManager" to true // 콜매니저에서 생성된 콜임을 표시
        )
        
        val targetPath = "regions/$regionId/offices/$officeId/calls"
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        
        firestore.collection(targetPath)
            .add(callData)
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "✅ Call data saved to Firestore with ID: ${documentReference.id}")
                
                // 통화 종료 시 포그라운드 전환 + 팝업 생성
                Log.i(TAG, "🎯 Call ended - bringing CallManager to foreground and showing popup for call ID: ${documentReference.id}")
                bringCallManagerToForegroundForNewCall(documentReference.id, phoneNumber, contactName, contactAddress)
                
                Log.i(TAG, "✅ Call end processing and popup trigger completed")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save call data to Firestore: ${e.message}", e)
                // 실패해도 포그라운드 전환은 수행
                bringCallManagerToForeground()
            }
    }
    
    /**
     * 공유 콜 생성 (마감 상태)
     */
    private fun createSharedCall(
        regionId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        val sharedCallData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "sourceRegionId" to regionId,
            "sourceOfficeId" to officeId,
            "targetRegionId" to regionId, // 같은 지역 내 공유
            "deviceName" to deviceName,
            "status" to "OPEN",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "callType" to "AFTER_HOURS", // 퇴근 후 콜
            "timestampClient" to System.currentTimeMillis(),
            "fromCallManager" to true // 콜매니저에서 생성된 콜임을 표시
        )
        
        contactName?.let { sharedCallData["customerName"] = it }
        contactAddress?.let { sharedCallData["customerAddress"] = it }
        
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("shared_calls")
            .add(sharedCallData)
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "✅ Shared call created with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to create shared call: ${e.message}", e)
            }
    }
    
    /**
     * 자동 SMS 발송
     */
    private fun sendAutoSMS(phoneNumber: String, officeName: String) {
        try {
            Log.i(TAG, "🔔 CallManager SMS 발송 시작 - 번호: $phoneNumber, 사무실: $officeName")
            
            // SMS 권한 체크
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ CallManager SMS 권한이 없습니다. SEND_SMS 권한을 확인하세요.")
                // 권한이 없을 때 토스트 메시지 표시
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(this, "SMS 권한이 없어 문자를 보낼 수 없습니다", android.widget.Toast.LENGTH_LONG).show()
                }
                return
            }
            
            Log.i(TAG, "✅ CallManager SMS 권한 확인 완료")
            
            val message = "[$officeName] 운영시간이 종료되었습니다. 잠시 후 다시 연락드리겠습니다."
            
            Log.i(TAG, "📝 CallManager SMS 메시지 내용: $message")
            
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            Log.i(TAG, "📱 CallManager SmsManager 획득 완료, 발송 시도 중...")
            
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            
            Log.i(TAG, "✅ CallManager 자동 SMS 발송 완료: $phoneNumber")
            
            // 성공 시 토스트 메시지 표시
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(this, "마감 안내 문자 전송 완료", android.widget.Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ CallManager SMS 발송 실패 - 예외: ${e.message}", e)
            // 실패 시 토스트 메시지 표시
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(this, "SMS 발송 실패: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Firebase에서 관리자 정보를 가져와서 SMS 발송 시도
     */
    private suspend fun tryGetOfficeInfoFromFirebase(phoneNumber: String) {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            if (currentUser == null) {
                Log.e(TAG, "❌ No authenticated user, cannot get office info")
                return
            }
            
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            
            // admins 컬렉션에서 현재 사용자 정보 조회
            val adminDoc = firestore.collection("admins")
                .document(currentUser.uid)
                .get()
                .await()
            
            if (!adminDoc.exists()) {
                Log.e(TAG, "❌ Admin document not found for user: ${currentUser.uid}")
                return
            }
            
            val regionId = adminDoc.getString("associatedRegionId")
            val officeId = adminDoc.getString("associatedOfficeId")
            
            Log.i(TAG, "✅ Got office info from Firebase - regionId: $regionId, officeId: $officeId")
            
            if (regionId != null && officeId != null) {
                // SharedPreferences에 저장
                sharedPreferences.edit().apply {
                    putString("regionId", regionId)
                    putString("officeId", officeId)
                    apply()
                }
                
                // SMS 발송 진행
                checkOfficeStatusForQuickResponse(regionId, officeId, phoneNumber)
            } else {
                Log.e(TAG, "❌ Region or Office ID is null in admin document")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to get office info from Firebase", e)
        }
    }
    
    /**
     * 마감 상태 확인 후 빠른 SMS 발송 (RINGING 상태용)
     */
    private suspend fun checkOfficeStatusForQuickResponse(
        regionId: String,
        officeId: String,
        phoneNumber: String
    ) {
        try {
            Log.i(TAG, "🔔 CallManager RINGING - 사무실 상태 확인 시작 (Region: $regionId, Office: $officeId)")
            
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val document = firestore.collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .get()
                .await()
            
            if (!document.exists()) {
                Log.e(TAG, "❌ CallManager RINGING - 사무실 문서가 존재하지 않음")
                return
            }
            
            val officeStatus = document.getString("status") ?: "OPEN"
            val officeName = document.getString("name") ?: "사무실"
            
            Log.i(TAG, "📞 CallManager RINGING 상태에서 사무실 상태 확인 완료: $officeStatus (사무실명: $officeName)")
            
            if (officeStatus == "CLOSED") {
                Log.i(TAG, "🌙 CallManager 마감 상태 확인 - 2초 후 SMS 발송 예정")
                
                // 2초 대기 후 SMS 발송
                kotlinx.coroutines.delay(2000)
                
                Log.i(TAG, "⏰ CallManager 2초 대기 완료 - 연락처 정보 조회 중...")
                
                val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
                
                Log.i(TAG, "📝 CallManager 공유콜 생성 시작...")
                
                // 공유콜 생성 (RINGING에서)
                createSharedCallFromRinging(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                
                Log.i(TAG, "📨 CallManager SMS 발송 함수 호출...")
                
                // SMS 발송
                sendAutoSMS(phoneNumber, officeName)
                
                Log.i(TAG, "✅ CallManager 마감 시 빠른 응답 완료 (RINGING → SMS)")
            } else {
                Log.i(TAG, "🏢 CallManager 사무실 운영중 (상태: $officeStatus) - SMS 발송 안함")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ CallManager 빠른 응답 처리 실패: ${e.message}", e)
        }
    }
    
    /**
     * RINGING 상태에서 공유 콜 생성 (콜매니저용)
     */
    private fun createSharedCallFromRinging(
        regionId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        val sharedCallData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "sourceRegionId" to regionId,
            "sourceOfficeId" to officeId,
            "targetRegionId" to regionId, // 해당 지역 모든 사무실에 공유
            "deviceName" to deviceName,
            "status" to "OPEN",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "callType" to "AFTER_HOURS_QUICK", // 마감 후 빠른 응답
            "timestampClient" to System.currentTimeMillis(),
            "fromRinging" to true, // RINGING 상태에서 생성됨을 표시
            "fromCallManager" to true // 콜매니저에서 생성된 콜임을 표시
        )
        
        contactName?.let { sharedCallData["customerName"] = it }
        contactAddress?.let { sharedCallData["customerAddress"] = it }
        
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("shared_calls")
            .add(sharedCallData)
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "✅ CallManager Quick response shared call created with ID: ${documentReference.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ CallManager Failed to create quick response shared call: ${e.message}", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(callLogObserver)
        isRunning = false
        Log.i(TAG, "CallDetectorService destroyed.")
    }
}
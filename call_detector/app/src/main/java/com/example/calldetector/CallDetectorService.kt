package com.example.calldetector

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
import com.example.calldetector.data.CallStatus
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
    
    // 개인번호 관리자
    private lateinit var excludeNumberManager: ExcludeNumberManager

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
        sharedPreferences = getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        
        // 개인번호 관리자 초기화
        excludeNumberManager = ExcludeNumberManager(this)
        Log.i(TAG, "📱 개인번호 관리자 초기화 완료. 제외된 번호: ${excludeNumberManager.getExcludeCount()}개")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        callLogObserver = CallLogObserver(Handler(mainLooper))
        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver
        )
        
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
            
            // 1. Handle call termination (IDLE state) - 통화 종료 시 Firebase 저장 + 기사배차 팝업 생성
            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                if (phoneNumber == lastProcessedPhoneNumber) {
                    Log.i(TAG, "📞 Call with $phoneNumber ended (IDLE state received). Processing call data and showing dispatch popup.")
                    
                    // 개인번호 체크 - 제외 번호면 Firebase 업로드 스킵
                    if (excludeNumberManager.isExcludedNumber(phoneNumber)) {
                        Log.i(TAG, "🚫 개인번호로 분류된 전화입니다. Firebase 업로드를 건너뜁니다: $phoneNumber")
                        // 처리 완료 후 리셋
                        lastProcessedPhoneNumber = null
                        lastProcessedCallTime = 0L
                        return START_NOT_STICKY
                    }
                    
                    // 수신전화 종료 시 사무실 상태 확인 후 처리
                    serviceScope.launch {
                        val regionId = sharedPreferences.getString("regionId", null)
                        val officeId = sharedPreferences.getString("officeId", null)
                        val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

                        if (regionId == null || officeId == null || deviceName.isBlank()) {
                            Log.e(TAG, "❌ Error: Region ID, Office ID, or Device Name not configured. Cannot process call.")
                            return@launch
                        }
                        Log.i(TAG, "ℹ️ Using configuration - Region: $regionId, Office: $officeId, Device: $deviceName")

                        val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                        Log.i(TAG, "📞 Contact info for $phoneNumber: Name='$contactName', Address='$contactAddress'")

                        // 사무실 상태에 따라 일반 콜 또는 공유 콜 생성
                        checkOfficeStatusAndSaveCall(
                            regionId,
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
                Log.i(TAG, "📞 Incoming call ringing from: $phoneNumber")
                
                // 마감 상태인지 확인 후 2-3초 후 SMS 발송
                serviceScope.launch {
                    val regionId = sharedPreferences.getString("regionId", null)
                    val officeId = sharedPreferences.getString("officeId", null)
                    
                    if (regionId != null && officeId != null) {
                        checkOfficeStatusForQuickResponse(regionId, officeId, phoneNumber)
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

    /**
     * DispatchActivity를 직접 실행하여 기사배차 팝업 생성
     */
    private fun launchDispatchActivity(callId: String, phoneNumber: String, contactName: String?, contactAddress: String?) {
        try {
            val intent = Intent(this, com.example.calldetector.ui.DispatchActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
                
                putExtra("EXTRA_CALL_ID", callId)
                putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
                contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
                putExtra("EXTRA_REGION_ID", getRegionId(this@CallDetectorService))
                putExtra("EXTRA_OFFICE_ID", getOfficeId(this@CallDetectorService))
                putExtra("EXTRA_DEVICE_NAME", getDeviceName(this@CallDetectorService))
                putExtra("FROM_NEW_CALL", true) // 새 콜에서 왔음을 표시
                
                // Android 10+ 예외 조건들 활용
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }
            
            startActivity(intent)
            Log.i(TAG, "🚀 DispatchActivity launched directly for new call: $callId")
            
        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ Direct DispatchActivity launch failed due to background restrictions, using notification fallback")
            // 포그라운드 전환이 실패하면 알림으로 폴백
            showCallManagerNotification(
                context = this,
                callId = callId,
                phoneNumber = phoneNumber,
                contactName = contactName,
                contactAddress = contactAddress
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to launch DispatchActivity for new call", e)
            // 실패 시 알림으로 폴백
            showCallManagerNotification(
                context = this,
                callId = callId,
                phoneNumber = phoneNumber,
                contactName = contactName,
                contactAddress = contactAddress
            )
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

        val intent = Intent(context, com.example.calldetector.ui.DispatchActivity::class.java).apply {
            putExtra("EXTRA_CALL_ID", callId)
            putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
            contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
            contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
            putExtra("EXTRA_REGION_ID", getRegionId(context))
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

    inner class CallLogObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(change: Boolean) {
            super.onChange(change)
            // CallLog 변경 감지 시 마지막 통화 확인
            checkLastCallLog()
        }
    }
    
    /**
     * CallLog에서 마지막 통화 확인 (부재중 전화 감지용)
     */
    private fun checkLastCallLog() {
        serviceScope.launch {
            try {
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
                    "${CallLog.Calls.DATE} DESC LIMIT 1"
                )
                
                cursor?.use {
                    if (it.moveToFirst()) {
                        val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                        val type = it.getInt(it.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                        val date = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))
                        val name = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)) ?: ""
                        
                        // 부재중 전화인지 확인 (5초 이내의 새로운 부재중 전화만 처리)
                        if (type == CallLog.Calls.MISSED_TYPE && 
                            (System.currentTimeMillis() - date) < 5000 &&
                            date > serviceStartTime) {
                            
                            Log.i(TAG, "📞 부재중 전화 감지: $number")
                            
                            // 사무실 상태 확인 후 처리
                            val regionId = sharedPreferences.getString("regionId", null)
                            val officeId = sharedPreferences.getString("officeId", null)
                            
                            if (regionId != null && officeId != null) {
                                checkOfficeStatusForMissedCall(regionId, officeId, number, name)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "CallLog 확인 실패", e)
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
            val document = db.collection("regions").document(regionId)
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
                    "sourceRegionId" to regionId,
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
                        Log.i(TAG, "✅ 부재중 전화 공유콜 생성 완료")
                        // SMS 발송
                        sendAutoSMS(phoneNumber, officeName)
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
    private fun checkOfficeStatusAndSaveCall(
        regionId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        // 사무실 상태 확인
        db.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .get()
            .addOnSuccessListener { document ->
                val officeStatus = document.getString("status") ?: "OPEN"
                val officeName = document.getString("name") ?: "사무실"
                
                Log.i(TAG, "사무실 상태: $officeStatus")
                
                when (officeStatus) {
                    "CLOSED" -> {
                        // 마감 상태: shared_calls에 저장 및 자동 SMS 발송
                        createSharedCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                        sendAutoSMS(phoneNumber, officeName)
                    }
                    else -> {
                        // 운영중: 기존대로 calls에 저장
                        createNormalCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "사무실 상태 확인 실패", e)
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
            "timestamp" to FieldValue.serverTimestamp(),
            "detectedTimestamp" to FieldValue.serverTimestamp(),
            "regionId" to regionId,
            "officeId" to officeId,
            "deviceName" to deviceName,
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis()
        )
        
        val targetPath = "regions/$regionId/offices/$officeId/calls"
        db.collection(targetPath)
            .add(callData)
            .addOnSuccessListener { documentReference ->
                Log.i(TAG, "✅ Call data saved to Firestore with ID: ${documentReference.id}")
                
                // 통화 종료 시 기사배차 팝업 생성
                Log.i(TAG, "🎯 Call ended - launching DispatchActivity for call ID: ${documentReference.id}")
                launchDispatchActivity(documentReference.id, phoneNumber, contactName, contactAddress)
                
                Log.i(TAG, "✅ Call end processing and dispatch popup trigger completed")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to save call data to Firestore: ${e.message}", e)
                // 실패해도 기사배차 팝업은 생성 (알림 방식으로 폴백)
                showCallManagerNotification(
                    context = this@CallDetectorService,
                    callId = "failed_${System.currentTimeMillis()}",
                    phoneNumber = phoneNumber,
                    contactName = contactName,
                    contactAddress = contactAddress
                )
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
            "deviceName" to deviceName,
            "status" to "OPEN",
            "timestamp" to FieldValue.serverTimestamp(),
            "callType" to "AFTER_HOURS", // 퇴근 후 콜
            "timestampClient" to System.currentTimeMillis()
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
     * 자동 SMS 발송
     */
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

    /**
     * 마감 상태 확인 후 빠른 SMS 발송 (RINGING 상태용)
     */
    private suspend fun checkOfficeStatusForQuickResponse(
        regionId: String,
        officeId: String,
        phoneNumber: String
    ) {
        try {
            val document = db.collection("regions").document(regionId)
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
                createSharedCallFromRinging(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                
                // SMS 발송
                sendAutoSMS(phoneNumber, officeName)
                
                Log.i(TAG, "✅ 마감 시 빠른 응답 완료 (RINGING → SMS)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 빠른 응답 처리 실패", e)
        }
    }
    
    /**
     * RINGING 상태에서 공유 콜 생성
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
            "deviceName" to deviceName,
            "status" to "OPEN",
            "timestamp" to FieldValue.serverTimestamp(),
            "callType" to "AFTER_HOURS_QUICK", // 마감 후 빠른 응답
            "timestampClient" to System.currentTimeMillis(),
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
        contentResolver.unregisterContentObserver(callLogObserver)
        Log.i(TAG, "Service destroyed.")
    }
    
    // 헬퍼 함수들
    private fun getRegionId(context: Context): String {
        val prefs = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        return prefs.getString("regionId", "") ?: ""
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
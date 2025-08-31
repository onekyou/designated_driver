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
                if (phoneNumber == lastProcessedPhoneNumber) {
                    Log.i(TAG, "📞 Call with $phoneNumber ended (IDLE state received). Processing call data and showing popup.")
                    
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
                        val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                        
                        // Firestore에 콜 데이터 저장
                        val regionId = sharedPreferences.getString("regionId", null)
                        val officeId = sharedPreferences.getString("officeId", null)
                        
                        if (regionId != null && officeId != null) {
                            Log.i(TAG, "📝 Saving call to Firestore - Region: $regionId, Office: $officeId")
                            
                            val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
                            
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
                        } else {
                            Log.w(TAG, "⚠️ RegionId or OfficeId not configured - only bringing to foreground")
                            bringCallManagerToForeground()
                        }
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
            } else {
                // 수신 전화가 아니거나, OFFHOOK 상태가 아닌 경우 (예: RINGING 중, 또는 IDLE - 거절/부재중)
                // 이 경우에는 아무 작업도 하지 않음 (CallManager 실행 안 함, Firestore 저장 안 함)
                Log.i(TAG, "ℹ️ Call is not an answered incoming call (State: $callState, Incoming: $isIncomingCall). Expected OFFHOOK state is ${TelephonyManager.CALL_STATE_OFFHOOK}. No action taken.")
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

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(callLogObserver)
        isRunning = false
        Log.i(TAG, "CallDetectorService destroyed.")
    }
}
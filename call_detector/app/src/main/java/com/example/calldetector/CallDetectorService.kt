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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
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

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        callLogObserver = CallLogObserver(Handler(mainLooper))
        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            callLogObserver
        )
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
            // 1. Handle call termination (IDLE state) to reset the lock
            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                if (phoneNumber == lastProcessedPhoneNumber) {
                    Log.i(TAG, "📞 Call with $phoneNumber ended (IDLE state received). Resetting last processed info to allow new calls from this number.")
                    lastProcessedPhoneNumber = null
                    lastProcessedCallTime = 0L // Ensure it's a Long for initialization
                }
            }
            // 2. Handle incoming call answered (OFFHOOK state)
            else if (callState == TelephonyManager.CALL_STATE_OFFHOOK && isIncomingCall) {
                val currentTime = System.currentTimeMillis()
                // Check if this OFFHOOK is a duplicate for the *current* call session
                if (phoneNumber == lastProcessedPhoneNumber && (currentTime - lastProcessedCallTime) < PROCESSING_THRESHOLD_MS) {
                    Log.w(TAG, "⚠️ Duplicate OFFHOOK event for $phoneNumber within threshold. Skipping processing.")
                    return START_NOT_STICKY
                }

                Log.i(TAG, "✅ Incoming call answered (OFFHOOK). Saving to Firestore.")
                // Set the lock *only after* confirming it's a new processable OFFHOOK
                lastProcessedPhoneNumber = phoneNumber
                lastProcessedCallTime = currentTime

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

                    val callData = hashMapOf<String, Any>(
                        "phoneNumber" to phoneNumber,
                        "detectedTimestamp" to FieldValue.serverTimestamp(),
                        "regionId" to regionId,
                        "officeId" to officeId,
                        "deviceName" to deviceName,
                        "status" to "대기중", // CallManager에서 이 상태를 보고 처리할 수 있음
                        "timestamp" to FieldValue.serverTimestamp(),
                        "callType" to "수신" // 명확히 수신 전화임을 명시
                    )

                    contactName?.let { callData["customerName"] = it }
                    contactAddress?.let { callData["customerAddress"] = it }

                    Log.i(TAG, "Attempting to save call data to Firestore: $callData")

                    val targetPath = "regions/$regionId/offices/$officeId/calls"
                    db.collection(targetPath)
                        .add(callData)
                        .addOnSuccessListener { documentReference ->
                            Log.i(TAG, "✅ Call data saved to Firestore with ID: ${documentReference.id}")

                            // Firestore 저장 성공 후 CallManager 앱 실행
                            Log.i(TAG, "📞 Launching CallManager for call ID: ${documentReference.id}")
                            showCallManagerNotification(
                                context = this@CallDetectorService,
                                callId = documentReference.id,
                                phoneNumber = phoneNumber,
                                contactName = contactName,
                                contactAddress = contactAddress
                            )
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ Failed to save call data to Firestore: ${e.message}", e)
                        }
                }
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

    private fun showCallManagerNotification(context: Context, callId: String, phoneNumber: String?, contactName: String?, contactAddress: String?) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CALL_MANAGER_CHANNEL_ID,
                "통화 처리 알림", // Notification channel name for user
                NotificationManager.IMPORTANCE_HIGH // High importance for call alerts
            ).apply {
                description = "수신된 통화에 대한 처리 알림입니다."
                // Enable lights, vibration, etc. if desired
                // enableLights(true)
                // lightColor = Color.RED
                // enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel $CALL_MANAGER_CHANNEL_ID created.")
        }

        val intent = Intent().apply {
            action = "com.designated.callmanager.ACTION_SHOW_CALL_DIALOG"
            component = ComponentName("com.designated.callmanager", "com.designated.callmanager.MainActivity")
            putExtra("EXTRA_CALL_ID", callId)
            putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
            contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
            contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlag)

        val notificationBuilder = NotificationCompat.Builder(context, CALL_MANAGER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Default Android info icon
            .setContentTitle("새로운 통화 접수")
            .setContentText("전화번호: ${phoneNumber ?: "알 수 없음"} (${contactName ?: "이름 없음"})")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL) // Important for call-related notifications
            .setContentIntent(pendingIntent)
            .setAutoCancel(true) // Notification disappears when tapped
            .setFullScreenIntent(pendingIntent, true) // For immediate display over other apps
            .setOngoing(false) // Not an ongoing task

        notificationManager.notify(CALL_MANAGER_NOTIFICATION_ID, notificationBuilder.build())
        Log.i(TAG, "📞 CallManager activation notification posted for call ID: $callId")
    }

    inner class CallLogObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(change: Boolean) {
            super.onChange(change)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(callLogObserver)
        Log.i(TAG, "Service destroyed.")
    }
}
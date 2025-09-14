package com.example.calldetector

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import android.telephony.SmsManager
import android.telephony.TelephonyManager
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
    private val PROCESSING_THRESHOLD_MS = 5000
    private val CHANNEL_ID = "CallDetectorChannel"
    private val NOTIFICATION_ID = 1
    private val CALL_MANAGER_CHANNEL_ID = "CallManagerActivationChannel"
    private val CALL_MANAGER_NOTIFICATION_ID = 2
    private lateinit var callLogObserver: CallLogObserver
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    private var serviceStartTime: Long = 0

    private lateinit var excludeNumberManager: ExcludeNumberManager

    override fun onCreate() {
        super.onCreate()

        val hasReadCallLog = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        val hasReadPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if (!hasReadCallLog || !hasReadPhoneState) {
            stopSelf()
            return
        }

        serviceStartTime = System.currentTimeMillis()
        sharedPreferences = getSharedPreferences("detector_config", Context.MODE_PRIVATE)

        excludeNumberManager = ExcludeNumberManager(this)

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

        if (phoneNumber != null) {
            val currentTime = System.currentTimeMillis()

            if (currentTime < serviceStartTime + 5000) {
                return START_NOT_STICKY
            }

            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                if (phoneNumber == lastProcessedPhoneNumber) {

                    if (excludeNumberManager.isExcludedNumber(phoneNumber)) {
                        lastProcessedPhoneNumber = null
                        lastProcessedCallTime = 0L
                        return START_NOT_STICKY
                    }

                    serviceScope.launch {
                        val regionId = sharedPreferences.getString("regionId", null)
                        val officeId = sharedPreferences.getString("officeId", null)
                        val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

                        if (regionId == null || officeId == null || deviceName.isBlank()) {
                            return@launch
                        }

                        val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)

                        checkOfficeStatusAndSaveCall(
                            regionId,
                            officeId,
                            phoneNumber,
                            contactName,
                            contactAddress,
                            deviceName
                        )
                    }

                    lastProcessedPhoneNumber = null
                    lastProcessedCallTime = 0L
                }
            }
            else if (callState == TelephonyManager.CALL_STATE_OFFHOOK && isIncomingCall) {
                if (phoneNumber == lastProcessedPhoneNumber && (currentTime - lastProcessedCallTime) < PROCESSING_THRESHOLD_MS) {
                    return START_NOT_STICKY
                }

                lastProcessedPhoneNumber = phoneNumber
                lastProcessedCallTime = currentTime

            }
            else if (callState == TelephonyManager.CALL_STATE_RINGING && isIncomingCall) {

                serviceScope.launch {
                    val regionId = sharedPreferences.getString("regionId", null)
                    val officeId = sharedPreferences.getString("officeId", null)

                    if (regionId != null && officeId != null) {
                        checkOfficeStatusForQuickResponse(regionId, officeId, phoneNumber)
                    }
                }
            } else {
            }
        } else {
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun getContactInfo(context: Context, phoneNumber: String): Pair<String?, String?> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
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
                }
            } else {
            }
        } catch (e: Exception) {
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
                putExtra("FROM_NEW_CALL", true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }

            startActivity(intent)

        } catch (e: SecurityException) {
            showCallManagerNotification(
                context = this,
                callId = callId,
                phoneNumber = phoneNumber,
                contactName = contactName,
                contactAddress = contactAddress
            )
        } catch (e: Exception) {
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
                "통화 처리 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "수신된 통화에 대한 처리 알림입니다."
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
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

        val packageManager = context.packageManager
        val pendingIntent: PendingIntent? = try {
            val pendingIntentFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            PendingIntent.getActivity(context, 0, intent, pendingIntentFlag)
        } catch (e: Exception) {
            null
        }

        val notificationBuilder = NotificationCompat.Builder(context, CALL_MANAGER_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("새로운 통화 접수")
            .setContentText("전화번호: ${phoneNumber ?: "알 수 없음"} (${contactName ?: "이름 없음"})")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .apply { pendingIntent?.let { setContentIntent(it) } }
            .setAutoCancel(true)
            .setOngoing(false)
            .setSound(null)
            .setVibrate(null)
            .setSilent(true)

        notificationManager.notify(CALL_MANAGER_NOTIFICATION_ID, notificationBuilder.build())
    }

    inner class CallLogObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(change: Boolean) {
            super.onChange(change)
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

                        if (type == CallLog.Calls.MISSED_TYPE &&
                            (System.currentTimeMillis() - date) < 5000 &&
                            date > serviceStartTime) {

                            val regionId = sharedPreferences.getString("regionId", null)
                            val officeId = sharedPreferences.getString("officeId", null)

                            if (regionId != null && officeId != null) {
                                checkOfficeStatusForMissedCall(regionId, officeId, number, name)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
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

            if (officeStatus == "CLOSED") {

                val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

                val sharedCallData = hashMapOf<String, Any>(
                    "phoneNumber" to phoneNumber,
                    "sourceRegionId" to regionId,
                    "sourceOfficeId" to officeId,
                    "deviceName" to deviceName,
                    "status" to "OPEN",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "callType" to "MISSED_AFTER_HOURS",
                    "timestampClient" to System.currentTimeMillis()
                )

                contactName?.let { sharedCallData["customerName"] = it }

                db.collection("shared_calls")
                    .add(sharedCallData)
                    .addOnSuccessListener {
                        sendAutoSMS(phoneNumber, officeName)
                    }
                    .addOnFailureListener { e ->
                    }
            } else {
            }
        } catch (e: Exception) {
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
        db.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .get()
            .addOnSuccessListener { document ->
                val officeStatus = document.getString("status") ?: "OPEN"
                val officeName = document.getString("name") ?: "사무실"

                when (officeStatus) {
                    "CLOSED" -> {
                        createSharedCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                        sendAutoSMS(phoneNumber, officeName)
                    }
                    else -> {
                        createNormalCall(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                    }
                }
            }
            .addOnFailureListener { e ->
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

                launchDispatchActivity(documentReference.id, phoneNumber, contactName, contactAddress)

            }
            .addOnFailureListener { e ->
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
            "callType" to "AFTER_HOURS",
            "timestampClient" to System.currentTimeMillis()
        )

        contactName?.let { sharedCallData["customerName"] = it }
        contactAddress?.let { sharedCallData["customerAddress"] = it }

        db.collection("shared_calls")
            .add(sharedCallData)
            .addOnSuccessListener { documentReference ->
            }
            .addOnFailureListener { e ->
            }
    }

    /**
     * 자동 SMS 발송
     */
    private fun sendAutoSMS(phoneNumber: String, officeName: String) {
        try {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
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

        } catch (e: Exception) {
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

            if (officeStatus == "CLOSED") {

                kotlinx.coroutines.delay(2000)

                val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

                createSharedCallFromRinging(regionId, officeId, phoneNumber, contactName, contactAddress, deviceName)

                sendAutoSMS(phoneNumber, officeName)

            }
        } catch (e: Exception) {
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
            "callType" to "AFTER_HOURS_QUICK",
            "timestampClient" to System.currentTimeMillis(),
            "fromRinging" to true
        )

        contactName?.let { sharedCallData["customerName"] = it }
        contactAddress?.let { sharedCallData["customerAddress"] = it }

        db.collection("shared_calls")
            .add(sharedCallData)
            .addOnSuccessListener { documentReference ->
            }
            .addOnFailureListener { e ->
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(callLogObserver)
    }

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
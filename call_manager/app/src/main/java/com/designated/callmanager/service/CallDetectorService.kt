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
import com.designated.callmanager.data.ExcludeNumberManager
import com.designated.callmanager.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.Timestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.cancel

class CallDetectorService : Service() {
    private var lastProcessedPhoneNumber: String? = null
    private var lastProcessedCallTime: Long = 0
    private val PROCESSING_THRESHOLD_MS = 5000
    private val DUPLICATE_CALL_CHECK_MS = 10000L // 10초 이내의 동일 번호 Firestore 콜은 중복으로 간주
    private var sharedCallCreatedFromRinging: Boolean = false // RINGING에서 공유콜 생성 여부 (IDLE에서 이중 생성 방지용)
    private val TAG = "CallDetectorService"
    private val CHANNEL_ID = "CallDetectorChannel"
    private val NOTIFICATION_ID = 1
    private val CALL_MANAGER_CHANNEL_ID = "CallManagerActivationChannel"
    private val CALL_MANAGER_NOTIFICATION_ID = 2
    private var callLogObserver: CallLogObserver? = null
    private val db = FirebaseFirestore.getInstance()
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var excludeNumberManager: ExcludeNumberManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serviceStartTime: Long = 0

    companion object {
        @Volatile
        private var isRunning = false

        fun isServiceRunning(): Boolean = isRunning

        // 실제 시스템에서 서비스 상태를 확인하는 메서드
        fun isServiceActuallyRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
                if (CallDetectorService::class.java.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()

        // 먼저 Foreground 서비스로 시작 (5초 타임아웃 방지)
        serviceStartTime = System.currentTimeMillis()
        sharedPreferences = getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)
        excludeNumberManager = ExcludeNumberManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 권한 확인 (READ_PHONE_STATE만 체크, READ_CALL_LOG는 제거됨)
        val hasReadPhoneState = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

        if (!hasReadPhoneState) {
            // 권한이 없으면 서비스를 종료하되, startForeground는 이미 호출했으므로 타임아웃 방지
            stopSelf()
            return
        }

        // CallLogObserver는 READ_CALL_LOG 권한이 필요하므로 제거
        // CallScreeningService와 CallReceiver가 전화를 감지하므로 더 이상 필요 없음
        // callLogObserver = CallLogObserver(Handler(mainLooper))
        // callLogObserver?.let { observer ->
        //     contentResolver.registerContentObserver(
        //         CallLog.Calls.CONTENT_URI,
        //         true,
        //         observer
        //     )
        // }

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callState = intent?.getIntExtra("EXTRA_CALL_STATE", TelephonyManager.CALL_STATE_IDLE)
            ?: TelephonyManager.CALL_STATE_IDLE
        val isIncomingCall = intent?.getBooleanExtra("EXTRA_IS_INCOMING", false) ?: false
        val phoneNumber = intent?.getStringExtra("incomingPhoneNumber")

        if (phoneNumber != null) {
            val currentTime = System.currentTimeMillis()

            if (callState == TelephonyManager.CALL_STATE_IDLE) {
                val finalPhoneNumber = phoneNumber ?: lastProcessedPhoneNumber

                if (finalPhoneNumber != null && finalPhoneNumber == lastProcessedPhoneNumber) {

                    if (isIncomingCall) {

                        val sharedPreferences = getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)
                        val isCallDetectionEnabled = sharedPreferences.getBoolean("call_detection_enabled", false)
                        if (!isCallDetectionEnabled) {
                            return START_STICKY
                        }

                        if (excludeNumberManager.isExcludedNumber(finalPhoneNumber)) {
                            Log.i(TAG, "개인번호 제외: $finalPhoneNumber")
                            lastProcessedPhoneNumber = null
                            lastProcessedCallTime = 0L
                            return START_STICKY
                        }

                        val (contactName, contactAddress) = getContactInfo(applicationContext, finalPhoneNumber)

                        val provinceId = sharedPreferences.getString("provinceId", null)
                        val cityId = sharedPreferences.getString("cityId", null)
                        val officeId = sharedPreferences.getString("officeId", null)

                        if (provinceId != null && cityId != null && officeId != null) {

                            val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL

                            checkOfficeStatusAndSaveCall(
                                provinceId,
                                cityId,
                                officeId,
                                finalPhoneNumber,
                                contactName,
                                contactAddress,
                                deviceName
                            )
                        } else {
                            bringCallManagerToForeground()
                        }
                    }

                    lastProcessedPhoneNumber = null
                    lastProcessedCallTime = 0L
                } else if (finalPhoneNumber == null) {
                } else {
                }
            }
            else if (callState == TelephonyManager.CALL_STATE_OFFHOOK && isIncomingCall) {
                if (phoneNumber == lastProcessedPhoneNumber && (currentTime - lastProcessedCallTime) < PROCESSING_THRESHOLD_MS) {
                    return START_STICKY
                }

                lastProcessedPhoneNumber = phoneNumber
                lastProcessedCallTime = currentTime

            }
            else if (callState == TelephonyManager.CALL_STATE_RINGING && isIncomingCall) {
                sharedCallCreatedFromRinging = false // 새 전화 시작 시 리셋

                // 제외번호 체크 (IDLE 핸들러와 동일)
                if (excludeNumberManager.isExcludedNumber(phoneNumber)) {
                    Log.i(TAG, "RINGING 제외번호: $phoneNumber")
                    return START_STICKY
                }

                serviceScope.launch {
                    val provinceId = sharedPreferences.getString("provinceId", null)
                    val cityId = sharedPreferences.getString("cityId", null)
                    val officeId = sharedPreferences.getString("officeId", null)

                    if (provinceId != null && cityId != null && officeId != null) {
                        checkOfficeStatusForQuickResponse(provinceId, cityId, officeId, phoneNumber)
                    } else {
                        tryGetOfficeInfoFromFirebase(phoneNumber)
                    }
                }
            } else {
            }
        } else {
        }
        return START_STICKY
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

    inner class CallLogObserver(handler: Handler) : ContentObserver(handler) {
        override fun onChange(change: Boolean) {
            super.onChange(change)
            checkLastCallLog()
        }
    }

    /**
     * CallLog에서 최근 부재중 전화 확인
     */
    private fun checkLastCallLog() {
        serviceScope.launch {
            try {
                if (ContextCompat.checkSelfPermission(this@CallDetectorService, Manifest.permission.READ_CALL_LOG)
                    != PackageManager.PERMISSION_GRANTED) {
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

                        if (type == CallLog.Calls.MISSED_TYPE &&
                            (System.currentTimeMillis() - date) < 5000) {

                            val provinceId = sharedPreferences.getString("provinceId", null)
                            val cityId = sharedPreferences.getString("cityId", null)
                            val officeId = sharedPreferences.getString("officeId", null)

                            if (provinceId != null && cityId != null && officeId != null) {
                                checkOfficeStatusForMissedCall(provinceId, cityId, officeId, number, cachedName)
                            } else {
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
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?
    ) {
        try {
            val firestore = FirebaseFirestore.getInstance()
            val document = firestore.collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .get()
                .await()

            val officeStatus = document.getString("status") ?: "OPEN"
            val officeName = document.getString("name") ?: "사무실"

            if (officeStatus == "CLOSED") {

                val (fullContactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL
                val finalContactName = fullContactName ?: contactName

                createSharedCallFromMissed(provinceId, cityId, officeId, phoneNumber, finalContactName, contactAddress, deviceName)

                sendAutoSMS(phoneNumber, officeName)

            } else {
            }
        } catch (e: Exception) {
        }
    }

    /**
     * 부재중 전화에서 공유 콜 생성 (CallManager용)
     */
    private suspend fun createSharedCallFromMissed(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        try {
            val firestore = FirebaseFirestore.getInstance()

            // 중복 공유콜 생성 방지: 10초 내 같은 phoneNumber + sourceOfficeId의 OPEN 콜 확인
            val duplicateCheckThreshold = System.currentTimeMillis() - DUPLICATE_CALL_CHECK_MS
            val existingSharedCalls = firestore.collection("shared_calls")
                .whereEqualTo("phoneNumber", phoneNumber)
                .whereEqualTo("sourceOfficeId", officeId)
                .whereGreaterThan("timestampClient", duplicateCheckThreshold)
                .whereEqualTo("status", "OPEN")
                .get()
                .await()

            if (!existingSharedCalls.isEmpty) {
                Log.w(TAG, "⚠️ 중복 공유콜 감지 (부재중): 10초 내 같은 번호($phoneNumber)의 공유콜이 이미 존재합니다 (${existingSharedCalls.size()}건). 생성 스킵.")
                return
            }

            val sharedCallData = hashMapOf<String, Any>(
                "phoneNumber" to phoneNumber,
                "sourceProvinceId" to provinceId,
                "sourceCityId" to cityId,
                "sourceOfficeId" to officeId,
                "targetProvinceId" to provinceId,
                "targetCityId" to cityId,
                "deviceName" to deviceName,
                "status" to "OPEN",
                "timestamp" to FieldValue.serverTimestamp(),
                "callType" to "MISSED_CALL",
                "timestampClient" to System.currentTimeMillis(),
                "fromMissedCall" to true,
                "fromCallManager" to true
            )

            contactName?.let { sharedCallData["customerName"] = it }
            contactAddress?.let { sharedCallData["customerAddress"] = it }

            firestore.collection("shared_calls")
                .add(sharedCallData)
                .await()
            Log.d(TAG, "✅ 공유콜 생성 완료 (부재중)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 공유콜 생성 실패 (부재중): ${e.message}", e)
        }
    }

    private fun bringCallManagerToForeground() {
        try {
            val intent = Intent(this, com.designated.callmanager.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra("BRING_TO_FOREGROUND", true)
                putExtra("FROM_CALL_END", true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }

            startActivity(intent)

        } catch (e: SecurityException) {
            showCallEndedNotification()
        } catch (e: Exception) {
            showCallEndedNotification()
        }
    }

    /**
     * 새 콜 감지 시 CallManager를 포그라운드로 전환하고 팝업 트리거
     */
    private fun bringCallManagerToForegroundForNewCall(callId: String, phoneNumber: String, contactName: String?, contactAddress: String?) {
        try {
            val intent = Intent(this, com.designated.callmanager.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                       Intent.FLAG_ACTIVITY_NO_ANIMATION

                action = com.designated.callmanager.MainActivity.ACTION_SHOW_CALL_POPUP
                putExtra(com.designated.callmanager.MainActivity.EXTRA_CALL_ID, callId)
                putExtra("phoneNumber", phoneNumber)
                contactName?.let { putExtra("contactName", it) }
                contactAddress?.let { putExtra("contactAddress", it) }
                putExtra("FROM_NEW_CALL", true)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            }

            startActivity(intent)

        } catch (e: SecurityException) {
            val internalIntent = Intent("com.designated.callmanager.INTERNAL_SHOW_CALL_DIALOG").apply {
                putExtra("EXTRA_CALL_ID", callId)
                putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
                contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
            }
            sendBroadcast(internalIntent)
        } catch (e: Exception) {
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

        val fullScreenIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val notification = NotificationCompat.Builder(this, "CALL_ENDED_CHANNEL")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("통화 종료")
            .setContentText("콜매니저로 자동 전환")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(true)
            .setOngoing(false)
            .build()

        notificationManager.notify(999, notification)
    }

    /**
     * 사무실 상태 확인 후 일반 콜 또는 공유 콜 생성
     */
    private fun checkOfficeStatusAndSaveCall(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {

        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    createNormalCall(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                    return@addOnSuccessListener
                }

                val officeStatus = document.getString("status") ?: "OPEN"
                val officeName = document.getString("name") ?: "사무실"

                when (officeStatus) {
                    "CLOSED" -> {
                        // CD-01 수정: RINGING에서 이미 공유콜을 생성한 경우 중복 생성 방지
                        if (sharedCallCreatedFromRinging) {
                            Log.i(TAG, "⚠️ RINGING에서 이미 공유 콜 생성됨 - IDLE에서 중복 생성 방지")
                            sharedCallCreatedFromRinging = false
                        } else {
                            serviceScope.launch {
                                createSharedCall(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                            }
                            sendAutoSMS(phoneNumber, officeName)
                        }
                    }
                    else -> {
                        createNormalCall(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)
                    }
                }
            }
            .addOnFailureListener { e ->
                createNormalCall(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)
            }
    }

    /**
     * 일반 콜 생성 (운영중)
     */
    private fun createNormalCall(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // ✅ 추가: customerInfo 컬렉션 확인하여 앱 회원 여부 판별
        serviceScope.launch {
            try {
                // customerInfo 컬렉션에서 phoneNumber로 직접 조회 (문서 ID가 phoneNumber)
                val customerInfoDoc = firestore
                    .collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId)
                    .collection("customerInfo")
                    .document(phoneNumber)
                    .get()
                    .await()

                val isAppCustomer = customerInfoDoc.exists()
                val customerHomeAddress = if (isAppCustomer) {
                    customerInfoDoc.getString("homeAddress") ?: ""
                } else {
                    ""
                }

                // ✅ 앱 회원인 경우 customers 컬렉션에서 name(nickname)과 grade 가져오기
                val customerDoc = if (isAppCustomer) {
                    firestore
                        .collection("provinces").document(provinceId)
                        .collection("cities").document(cityId)
                        .collection("offices").document(officeId)
                        .collection("customers")
                        .whereEqualTo("phoneNumber", phoneNumber)
                        .limit(1)
                        .get()
                        .await()
                        .documents
                        .firstOrNull()
                } else {
                    null
                }

                // ✅ 앱 회원인 경우 name(nickname)과 grade 가져오기
                val customerNickname = customerDoc?.getString("name")
                val customerGrade = customerDoc?.getString("grade")

                // 🔍 디버깅 로그
                Log.d(TAG, "📞 전화 감지 - phoneNumber: $phoneNumber, isAppCustomer: $isAppCustomer, nickname: $customerNickname, grade: $customerGrade, homeAddress: $customerHomeAddress")

                val callData = hashMapOf<String, Any>(
                    "phoneNumber" to phoneNumber,
                    "customerName" to (customerNickname ?: contactName ?: ""),
                    "customerAddress" to (customerHomeAddress.takeIf { it.isNotBlank() } ?: contactAddress ?: ""),
                    "status" to CallStatus.WAITING.firestoreValue,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "detectedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "provinceId" to provinceId,
                    "cityId" to cityId,
                    "officeId" to officeId,
                    "deviceName" to deviceName,
                    "callType" to "수신",
                    "timestampClient" to System.currentTimeMillis(),
                    "fromCallManager" to true,
                    "isAppCustomer" to isAppCustomer,  // ✅ 추가: 앱 회원 여부
                    "customerId" to if (isAppCustomer) phoneNumber else "",  // ✅ 추가: 회원이면 phoneNumber
                    "customerGrade" to (customerGrade ?: ""),  // ✅ 추가: 고객 등급
                    "createdFrom" to "phone"  // ✅ 추가: 전화로 생성됨
                )

                val targetPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/calls"

                // 중복 콜 생성 방지: 10초 내 같은 phoneNumber의 콜 존재 여부 확인
                val duplicateCheckThreshold = System.currentTimeMillis() - DUPLICATE_CALL_CHECK_MS
                val existingCalls = firestore.collection(targetPath)
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .whereGreaterThan("timestampClient", duplicateCheckThreshold)
                    .whereIn("status", listOf(
                        CallStatus.WAITING.firestoreValue,
                        CallStatus.PENDING.firestoreValue,
                        CallStatus.ASSIGNED.firestoreValue
                    ))
                    .get()
                    .await()

                if (!existingCalls.isEmpty) {
                    Log.w(TAG, "⚠️ 중복 콜 감지: 10초 내 같은 번호($phoneNumber)의 콜이 이미 존재합니다 (${existingCalls.size()}건). 생성 스킵.")
                    return@launch
                }

                firestore.collection(targetPath)
                    .add(callData)
                    .addOnSuccessListener { documentReference ->
                        val callId = documentReference.id
                        Log.d(TAG, "✅ Firestore 콜 저장 완료: $callId")

                        // 로컬 DB에도 저장
                        serviceScope.launch {
                            try {
                                val app = applicationContext as com.designated.callmanager.CallManagerApplication
                                app.callRepository.insertCallFromFCM(
                                    callId = callId,
                                    phoneNumber = phoneNumber,
                                    customerName = customerNickname ?: contactName,
                                    customerAddress = customerHomeAddress.takeIf { it.isNotBlank() } ?: contactAddress,
                                    status = CallStatus.WAITING.firestoreValue,
                                    provinceId = provinceId,
                                    officeId = officeId,
                                    callType = "수신",
                                    fromCallDetector = false
                                )
                                Log.d(TAG, "✅ 로컬 DB 콜 저장 완료: $callId")
                            } catch (e: Exception) {
                                Log.e(TAG, "❌ 로컬 DB 콜 저장 실패: ${e.message}")
                            }
                        }

                        bringCallManagerToForegroundForNewCall(callId, phoneNumber, contactName, contactAddress)
                    }
                    .addOnFailureListener { e ->
                        bringCallManagerToForeground()
                    }
            } catch (e: Exception) {
                // 에러 발생 시 기존 방식으로 콜 생성
                val callData = hashMapOf<String, Any>(
                    "phoneNumber" to phoneNumber,
                    "customerName" to (contactName ?: ""),
                    "customerAddress" to (contactAddress ?: ""),
                    "status" to CallStatus.WAITING.firestoreValue,
                    "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "detectedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "provinceId" to provinceId,
                    "cityId" to cityId,
                    "officeId" to officeId,
                    "deviceName" to deviceName,
                    "callType" to "수신",
                    "timestampClient" to System.currentTimeMillis(),
                    "fromCallManager" to true,
                    "isAppCustomer" to false,
                    "customerId" to "",
                    "createdFrom" to "phone"
                )

                val targetPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/calls"

                // fallback 경로에서도 중복 콜 생성 방지
                val fbDupThreshold = System.currentTimeMillis() - DUPLICATE_CALL_CHECK_MS
                val fbExistingCalls = firestore.collection(targetPath)
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .whereGreaterThan("timestampClient", fbDupThreshold)
                    .whereIn("status", listOf(
                        CallStatus.WAITING.firestoreValue,
                        CallStatus.PENDING.firestoreValue,
                        CallStatus.ASSIGNED.firestoreValue
                    ))
                    .get()
                    .await()

                if (!fbExistingCalls.isEmpty) {
                    Log.w(TAG, "⚠️ 중복 콜 감지 (fallback): 10초 내 같은 번호($phoneNumber)의 콜이 이미 존재합니다 (${fbExistingCalls.size()}건). 생성 스킵.")
                    return@launch
                }

                firestore.collection(targetPath)
                    .add(callData)
                    .addOnSuccessListener { documentReference ->
                        val callId = documentReference.id
                        Log.d(TAG, "✅ Firestore 콜 저장 완료 (fallback): $callId")

                        // 로컬 DB에도 저장
                        serviceScope.launch {
                            try {
                                val app = applicationContext as com.designated.callmanager.CallManagerApplication
                                app.callRepository.insertCallFromFCM(
                                    callId = callId,
                                    phoneNumber = phoneNumber,
                                    customerName = contactName,
                                    customerAddress = contactAddress,
                                    status = CallStatus.WAITING.firestoreValue,
                                    provinceId = provinceId,
                                    officeId = officeId,
                                    callType = "수신",
                                    fromCallDetector = false
                                )
                                Log.d(TAG, "✅ 로컬 DB 콜 저장 완료 (fallback): $callId")
                            } catch (ex: Exception) {
                                Log.e(TAG, "❌ 로컬 DB 콜 저장 실패 (fallback): ${ex.message}")
                            }
                        }

                        bringCallManagerToForegroundForNewCall(callId, phoneNumber, contactName, contactAddress)
                    }
                    .addOnFailureListener { e2 ->
                        bringCallManagerToForeground()
                    }
            }
        }
    }

    /**
     * 공유 콜 생성 (마감 상태)
     */
    private suspend fun createSharedCall(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // 중복 공유콜 생성 방지: 10초 내 같은 phoneNumber + sourceOfficeId의 OPEN 콜 확인
            val duplicateCheckThreshold = System.currentTimeMillis() - DUPLICATE_CALL_CHECK_MS
            val existingSharedCalls = firestore.collection("shared_calls")
                .whereEqualTo("phoneNumber", phoneNumber)
                .whereEqualTo("sourceOfficeId", officeId)
                .whereGreaterThan("timestampClient", duplicateCheckThreshold)
                .whereEqualTo("status", "OPEN")
                .get()
                .await()

            if (!existingSharedCalls.isEmpty) {
                Log.w(TAG, "⚠️ 중복 공유콜 감지: 10초 내 같은 번호($phoneNumber)의 공유콜이 이미 존재합니다 (${existingSharedCalls.size()}건). 생성 스킵.")
                return
            }

            val sharedCallData = hashMapOf<String, Any>(
                "phoneNumber" to phoneNumber,
                "sourceProvinceId" to provinceId,
                "sourceCityId" to cityId,
                "sourceOfficeId" to officeId,
                "targetProvinceId" to provinceId,
                "targetCityId" to cityId,
                "deviceName" to deviceName,
                "status" to "OPEN",
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "callType" to "AFTER_HOURS",
                "timestampClient" to System.currentTimeMillis(),
                "fromCallManager" to true
            )

            contactName?.let { sharedCallData["customerName"] = it }
            contactAddress?.let { sharedCallData["customerAddress"] = it }

            firestore.collection("shared_calls")
                .add(sharedCallData)
                .await()
            Log.d(TAG, "✅ 공유콜 생성 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 공유콜 생성 실패: ${e.message}", e)
        }
    }

    /**
     * 자동 SMS 발송
     */
    private fun sendAutoSMS(phoneNumber: String, officeName: String) {
        try {

            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(this, "SMS 권한이 없어 문자를 보낼 수 없습니다", android.widget.Toast.LENGTH_LONG).show()
                }
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

            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(this, "마감 안내 문자 전송 완료", android.widget.Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
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
                return
            }

            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            val adminDoc = firestore.collection("admins")
                .document(currentUser.uid)
                .get()
                .await()

            if (!adminDoc.exists()) {
                return
            }

            val provinceId = adminDoc.getString("associatedProvinceId")
            val cityId = adminDoc.getString("associatedCityId")
            val officeId = adminDoc.getString("associatedOfficeId")

            if (provinceId != null && cityId != null && officeId != null) {
                sharedPreferences.edit().apply {
                    putString("provinceId", provinceId)
                    putString("cityId", cityId)
                    putString("officeId", officeId)
                    apply()
                }

                checkOfficeStatusForQuickResponse(provinceId, cityId, officeId, phoneNumber)
            } else {
            }

        } catch (e: Exception) {
        }
    }

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

            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val document = firestore.collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .get()
                .await()

            if (!document.exists()) {
                return
            }

            val officeStatus = document.getString("status") ?: "OPEN"
            val officeName = document.getString("name") ?: "사무실"

            if (officeStatus == "CLOSED") {

                kotlinx.coroutines.delay(2000)

                // delay 후 서비스가 아직 활성 상태인지 확인 (통화 종료로 서비스 파괴 시 안전하게 중단)
                kotlin.coroutines.coroutineContext.ensureActive()

                val (contactName, contactAddress) = getContactInfo(applicationContext, phoneNumber)
                val deviceName = sharedPreferences.getString("deviceName", android.os.Build.MODEL) ?: android.os.Build.MODEL

                // 공유콜 생성 (RINGING에서) - 플래그 설정으로 IDLE에서 이중 생성 방지
                sharedCallCreatedFromRinging = true
                createSharedCallFromRinging(provinceId, cityId, officeId, phoneNumber, contactName, contactAddress, deviceName)

                sendAutoSMS(phoneNumber, officeName)

            } else {
            }
        } catch (e: CancellationException) {
            // 코루틴 취소는 정상 동작 (서비스 파괴 시) - 재throw하여 코루틴 취소 전파
            Log.i(TAG, "ℹ️ 빠른 응답 코루틴 취소됨 (서비스 종료)")
            throw e
        } catch (e: Exception) {
        }
    }

    /**
     * RINGING 상태에서 공유 콜 생성 (콜매니저용)
     */
    private suspend fun createSharedCallFromRinging(
        provinceId: String,
        cityId: String,
        officeId: String,
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        deviceName: String
    ) {
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

            // 중복 공유콜 생성 방지: 10초 내 같은 phoneNumber + sourceOfficeId의 OPEN 콜 확인
            val duplicateCheckThreshold = System.currentTimeMillis() - DUPLICATE_CALL_CHECK_MS
            val existingSharedCalls = firestore.collection("shared_calls")
                .whereEqualTo("phoneNumber", phoneNumber)
                .whereEqualTo("sourceOfficeId", officeId)
                .whereGreaterThan("timestampClient", duplicateCheckThreshold)
                .whereEqualTo("status", "OPEN")
                .get()
                .await()

            if (!existingSharedCalls.isEmpty) {
                Log.w(TAG, "⚠️ 중복 공유콜 감지 (RINGING): 10초 내 같은 번호($phoneNumber)의 공유콜이 이미 존재합니다 (${existingSharedCalls.size()}건). 생성 스킵.")
                return
            }

            val sharedCallData = hashMapOf<String, Any>(
                "phoneNumber" to phoneNumber,
                "sourceProvinceId" to provinceId,
                "sourceCityId" to cityId,
                "sourceOfficeId" to officeId,
                "targetProvinceId" to provinceId,
                "targetCityId" to cityId,
                "deviceName" to deviceName,
                "status" to "OPEN",
                "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "callType" to "AFTER_HOURS_QUICK",
                "timestampClient" to System.currentTimeMillis(),
                "fromRinging" to true,
                "fromCallManager" to true
            )

            contactName?.let { sharedCallData["customerName"] = it }
            contactAddress?.let { sharedCallData["customerAddress"] = it }

            firestore.collection("shared_calls")
                .add(sharedCallData)
                .await()
            Log.d(TAG, "✅ 공유콜 생성 완료 (RINGING)")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 공유콜 생성 실패 (RINGING): ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // CallLogObserver는 더 이상 사용하지 않으므로 unregister도 주석 처리
        // try {
        //     callLogObserver?.let { observer ->
        //         contentResolver.unregisterContentObserver(observer)
        //     }
        // } catch (e: Exception) {
        //     // 이미 해제되었거나 초기화되지 않은 경우 무시
        // }

        // 코루틴 스코프 안전하게 해제 (경합 조건 방지)
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling serviceScope: ${e.message}")
        }
        isRunning = false
    }
}
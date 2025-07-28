package com.designated.driverapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.designated.driverapp.MainActivity
import com.designated.driverapp.R
import com.designated.driverapp.data.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.CallStatus
import com.designated.driverapp.model.DriverStatus
import com.google.firebase.Timestamp
import android.graphics.Color
import android.media.RingtoneManager
import kotlinx.coroutines.flow.debounce

private const val TAG = "DriverForegroundService"
private const val CHANNEL_ID = "DriverServiceChannel"
private const val SERVICE_STATUS_CHANNEL_ID = "DriverServiceStatusChannel"
private const val NOTIFICATION_ID = 1
private const val SERVICE_STATUS_NOTIFICATION_TITLE = "대리운전 기사앱"
private const val SERVICE_STATUS_NOTIFICATION_TEXT = "서비스 실행 중"

// Logcat에서 파싱 과정을 별도 태그로 쉽게 필터링하기 위한 상수 (23자 제한 이하)
private const val PARSE_DEBUG_TAG = "*** PARSE DEBUG ***"

class DriverForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var driverStatusListener: ListenerRegistration? = null
    private var assignedCallsListener: ListenerRegistration? = null

    private val _driverStatus = MutableStateFlow<DriverStatus>(DriverStatus.OFFLINE)
    private val _assignedCall = MutableStateFlow<CallInfo?>(null)

    private var previousCall: CallInfo? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel()
        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        startListeningForAuthState()
        observeStatusAndManageService()
    }

    private fun startListeningForAuthState() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d(TAG, "Service Auth: User logged in (${user.uid}). Starting listeners.")
                startFirestoreListeners(user.uid)
            } else {
                Log.d(TAG, "Service Auth: User logged out. Stopping listeners and service.")
                stopFirestoreListeners()
                Log.d(TAG,"[Service Lifecycle] User logged out. Calling stopSelf().")
                stopSelf()
            }
        }
    }

    private fun startFirestoreListeners(driverId: String) {
        stopFirestoreListeners()

        val prefs = getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        val regionId = prefs.getString("regionId", null)
        val officeId = prefs.getString("officeId", null)

        if (regionId == null || officeId == null) {
            Log.e(TAG, "Service Error: regionId or officeId is null in SharedPreferences. Cannot start listeners.")
            _driverStatus.value = DriverStatus.OFFLINE
            return
        }

        Log.d(TAG, "Starting Firestore listeners with Region ID: $regionId, Office ID: $officeId, Driver ID: $driverId")

        val driverDocPath = "regions/$regionId/offices/$officeId/designated_drivers/$driverId"
        Log.d(TAG, "Setting up driver status listener for path: $driverDocPath")
        driverStatusListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)
            .addSnapshotListener { snapshot, e ->
                Log.d(TAG, "[Firestore Status] Listener triggered.")
                if (e != null) {
                    Log.e(TAG, "Service: Error listening for driver status", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val statusString = snapshot.getString("status")
                    Log.d(TAG, "[Firestore Status] Received status string: '$statusString'")
                    _driverStatus.value = DriverStatus.fromString(statusString)
                } else {
                     Log.d(TAG, "Service: Driver status document does not exist for $driverId")
                    _driverStatus.value = DriverStatus.OFFLINE
                }
            }

        val callsPath = "regions/$regionId/offices/$officeId/calls"
        Log.d(TAG, "Setting up assigned calls listener for path: $callsPath")
        assignedCallsListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("assignedDriverId", driverId)
            .whereIn("status", listOf(
                Constants.STATUS_ASSIGNED, 
                Constants.STATUS_ACCEPTED, 
                Constants.STATUS_IN_PROGRESS
            ))
            .orderBy("assignedTimestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                Log.d(TAG, "[Firestore Calls] Listener triggered. Snapshot: ${snapshot?.documents?.size ?: "null"}, Exception: ${e?.message}")

                if (e != null) {
                    Log.e(TAG, "[Firestore Calls] Listener error", e)
                    _assignedCall.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val document = snapshot.documents[0]
                    Log.d(TAG, "[Firestore Calls] Found document: ${document.id}. Data: ${document.data}")
                    try {
                        val callInfo = parseCallDocument(document)
                        Log.d(TAG, "[Firestore Calls] Parsed call info: ${callInfo?.id}, Status String: ${callInfo?.status}, Status Enum: ${callInfo?.statusEnum}")

                        val previousCallState = _assignedCall.value

                        if (callInfo != null && (callInfo.statusEnum == CallStatus.ASSIGNED || callInfo.statusEnum == CallStatus.ACCEPTED || callInfo.statusEnum == CallStatus.IN_PROGRESS)) {
                            val isTrulyNewCallForAlert = (previousCallState == null || previousCallState.id != callInfo.id) && 
                                                       (callInfo.statusEnum == CallStatus.WAITING || callInfo.statusEnum == CallStatus.ACCEPTED || callInfo.statusEnum == CallStatus.ASSIGNED)

                            if (isTrulyNewCallForAlert) {
                                Log.i(TAG, "[NEW CALL ALERT] New call assignment detected for alert: ${callInfo.id}, Status Enum: ${callInfo.statusEnum}. Triggering UI.")
                                triggerNewCallAlert(callInfo)
                            }
                            
                            if (previousCallState?.id != callInfo.id || previousCallState?.statusEnum != callInfo.statusEnum) {
                                Log.d(TAG, "[Firestore Calls] Updating _assignedCall. Previous: ${previousCallState?.id}/${previousCallState?.statusEnum}, New: ${callInfo.id}/${callInfo.statusEnum}")
                                _assignedCall.value = callInfo
                            } else {
                                Log.d(TAG, "[Firestore Calls] No change in call ID or status. _assignedCall not updated to avoid unnecessary recomposition.")
                            }
                        } else {
                            if (previousCallState != null) {
                                Log.w(TAG, "[Firestore Calls] Call (${callInfo?.id}) is null or status enum (${callInfo?.statusEnum}) is not active. Clearing _assignedCall.")
                                _assignedCall.value = null
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "[Firestore Calls] Error parsing call document ${document.id}", ex)
                        // 문서 파싱 실패 시 해당 콜 무시하고 앱 크래시 방지
                        if (_assignedCall.value != null) {
                           _assignedCall.value = null
                        }
                    }
                } else {
                    if (_assignedCall.value != null) {
                        Log.d(TAG, "[Firestore Calls] Snapshot is null or empty. Clearing _assignedCall.")
                        _assignedCall.value = null
                    }
                }
            }
    }

    private fun stopFirestoreListeners() {
        driverStatusListener?.remove()
        assignedCallsListener?.remove()
        driverStatusListener = null
        assignedCallsListener = null
        Log.d(TAG, "Service: Firestore listeners stopped.")
    }

    fun clearAssignedCallState() {
        Log.d(TAG, "[Service External] clearAssignedCallState() called. Clearing _assignedCall and previousCall.")
        _assignedCall.value = null
        previousCall = null
    }

    private fun observeStatusAndManageService() {
        serviceScope.launch {
            try {
                combine(_driverStatus, _assignedCall) { status, call ->
                    Pair(status, call)
                }
                .debounce(500L)
                .collect { (status, call) ->
                    Log.d(TAG, "[Service Lifecycle] Debounced state change detected: Status=$status, Call=${call?.id}, PrevCall=${previousCall?.id}, CallStatus String=${call?.status}, CallStatus Enum=${call?.statusEnum}")

                    val notificationTitle: String
                    val notificationText: String
                    var fullScreenPendingIntent: PendingIntent? = null
                    val notificationChannelId: String

                    val callStatusEnum = call?.statusEnum
                    val isInitialAssignment = previousCall == null && call != null && callStatusEnum == CallStatus.ASSIGNED
                    Log.d(TAG, "[Service Lifecycle] Evaluating isInitialAssignment: previousCallIsNull=${previousCall == null}, callIsNotNull=${call != null}, callStatusIsAssigned=${callStatusEnum == CallStatus.ASSIGNED} -> Result=$isInitialAssignment")

                    if (isInitialAssignment && call != null) {
                        notificationTitle = "새로운 호출 배정됨"
                        notificationText = "${call.phoneNumber} 고객님의 호출입니다."
                        notificationChannelId = CHANNEL_ID
                        Log.d(TAG, "[Service Lifecycle] Condition met: INITIAL Call Assignment. Use URGENT channel ($notificationChannelId). Creating Full-Screen Intent.")

                        val fullScreenIntent = Intent(this@DriverForegroundService, MainActivity::class.java).apply {
                            action = Constants.ACTION_SHOW_CALL_DIALOG
                            putExtra(Constants.EXTRA_CALL_INFO, call.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        fullScreenPendingIntent = PendingIntent.getActivity(
                            this@DriverForegroundService, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                    } else {
                        Log.d(TAG, "[Service Lifecycle] Condition NOT Initial Assignment because: previousCall=${previousCall?.id}, call=${call?.id}, callStatusEnum=${callStatusEnum}. Use STATUS channel.")
                        notificationTitle = SERVICE_STATUS_NOTIFICATION_TITLE
                        notificationText = SERVICE_STATUS_NOTIFICATION_TEXT
                        notificationChannelId = SERVICE_STATUS_CHANNEL_ID
                        fullScreenPendingIntent = null
                    }

                    Log.d(TAG, "[Service Lifecycle] Creating/Updating notification: Channel='$notificationChannelId', Title='$notificationTitle', Text='$notificationText', HasFullScreen=${fullScreenPendingIntent != null}")
                    val notification = createNotification(notificationChannelId, notificationTitle, notificationText, fullScreenPendingIntent)
                    Log.d(TAG, "[Service Lifecycle] Calling startForeground...")
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d(TAG, "[Service Lifecycle] startForeground call completed.")

                    previousCall = call

                }
            } catch (e: Exception) {
                Log.e(TAG, "[Service Lifecycle] CRITICAL: Error in observeStatusAndManageService collect block", e)
                if (e !is CancellationException) {
                    Log.e(TAG, "[Service Lifecycle] Non-cancellation error occurred. Stopping service.")
                    stopSelf()
                } else {
                    Log.d(TAG, "[Service Lifecycle] Coroutine cancelled normally (likely service stopping).")
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand received")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        stopFirestoreListeners()
        serviceScope.cancel()
    }

    private val binder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): DriverForegroundService = this@DriverForegroundService
    }

    private fun triggerNewCallAlert(callInfo: CallInfo) {
        Log.d(TAG, "triggerNewCallAlert for call ID: ${callInfo.id}, Status String: ${callInfo.status}, Status Enum: ${callInfo.statusEnum.displayName}")

        val intent = Intent(this@DriverForegroundService, MainActivity::class.java).apply {
            action = Constants.ACTION_SHOW_CALL_DIALOG
            putExtra(Constants.EXTRA_CALL_INFO, callInfo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        Log.d(TAG, "Sent intent to MainActivity to show call dialog.")

        playNotificationSound()
    }

    private fun playNotificationSound() {
        try {
            val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notificationSoundUri)
            r.play()
            Log.d(TAG, "Notification sound played.")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing notification sound", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "Service onBind")
        return binder
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val urgentChannel = NotificationChannel(
                CHANNEL_ID,
                "긴급 호출 알림",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "새로운 대리운전 호출이 배정되었을 때 알립니다."
                 enableLights(true)
                 lightColor = Color.RED
                 enableVibration(true)
                 lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(urgentChannel)
            Log.d(TAG, "Urgent Notification Channel created/updated: $CHANNEL_ID")

            val statusChannel = NotificationChannel(
                SERVICE_STATUS_CHANNEL_ID,
                "서비스 실행 상태",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "앱 백그라운드 서비스 실행 상태를 표시합니다."
                 setShowBadge(false)
                 setSound(null, null)
                 enableVibration(false)
                 enableLights(false)
                 lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            notificationManager.createNotificationChannel(statusChannel)
            Log.d(TAG, "Service Status Notification Channel created: $SERVICE_STATUS_CHANNEL_ID")
        }
    }

    private fun parseCallDocument(document: com.google.firebase.firestore.DocumentSnapshot): CallInfo? {
        return try {
            // 1) Document 원본 데이터 로깅
            Log.d(TAG, "[parseCallDocument] Raw Firestore data: \\${document.data}")
            Log.d(PARSE_DEBUG_TAG, "[RAW] \\${document.data}")

            // 2) CallInfo 변환
            val callInfo = document.toObject(CallInfo::class.java)

            // 3) 파싱된 모델에 ID 주입 및 상세 로그
            callInfo?.apply {
                id = document.id

                Log.d(
                    TAG,
                    "[parseCallDocument] Parsed CallInfo => id=$id, status=$status, statusEnum=\\${statusEnum}, " +
                            "assignedDriverId=$assignedDriverId, departure_set=\\\"$departure_set\\\", destination_set=\\\"$destination_set\\\", fare_set=$fare_set"
                )

                Log.d(
                    PARSE_DEBUG_TAG,
                    "[PARSED] id=$id, status=$status, statusEnum=\\${statusEnum}, assignedDriverId=$assignedDriverId, departure_set=$departure_set, destination_set=$destination_set, fare_set=$fare_set"
                )
            }

            callInfo
        } catch (e: Exception) {
            Log.e(TAG, "[parseCallDocument] Error parsing call document: \\${document.id}", e)
            null
        }
    }

    private fun createNotification(
        channelId: String,
        title: String,
        text: String,
        fullScreenPendingIntent: PendingIntent? = null
    ): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        _assignedCall.value?.id?.let { callId ->
            Log.d(TAG, "Adding callId: $callId to notification content intent")
            notificationIntent.putExtra("call_id", callId)
            notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(if (channelId == CHANNEL_ID) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        if (channelId == CHANNEL_ID && fullScreenPendingIntent != null) {
             Log.d(TAG, "Setting Full-Screen Intent for notification.")
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            builder.setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(0, 500, 200, 500))
        } else {
            Log.d(TAG, "NOT Setting Full-Screen Intent. Channel: $channelId, Intent null: ${fullScreenPendingIntent == null}")
        }

        Log.d(TAG, "Building notification for channel '$channelId' with title '$title'")
        return builder.build()
    }
} 
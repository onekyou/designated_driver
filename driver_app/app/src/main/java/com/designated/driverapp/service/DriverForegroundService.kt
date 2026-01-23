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
import kotlinx.coroutines.SupervisorJob
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

private const val PARSE_DEBUG_TAG = "*** PARSE DEBUG ***"

class DriverForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var driverStatusListener: ListenerRegistration? = null
    private var assignedCallsListener: ListenerRegistration? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null  // auth 리스너 참조 저장

    private val _driverStatus = MutableStateFlow<DriverStatus>(DriverStatus.OFFLINE)
    private val _assignedCall = MutableStateFlow<CallInfo?>(null)

    private var previousCall: CallInfo? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        startListeningForAuthState()
        observeStatusAndManageService()
    }

    private fun startListeningForAuthState() {
        // 기존 리스너 제거 (중복 등록 방지)
        authStateListener?.let { auth.removeAuthStateListener(it) }

        // 새 리스너 생성 및 등록
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                startFirestoreListeners(user.uid)
            } else {
                stopFirestoreListeners()
                stopSelf()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun startFirestoreListeners(driverId: String) {
        stopFirestoreListeners()

        val prefs = getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        val provinceId = prefs.getString("provinceId", null)
        val cityId = prefs.getString("cityId", null)
        val officeId = prefs.getString("officeId", null)

        if (provinceId == null || cityId == null || officeId == null) {
            _driverStatus.value = DriverStatus.OFFLINE
            return
        }

        val driverDocPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers/$driverId"
        driverStatusListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val statusString = snapshot.getString("status")
                    _driverStatus.value = DriverStatus.fromString(statusString)
                } else {
                    _driverStatus.value = DriverStatus.OFFLINE
                }
            }

        val callsPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/calls"
        assignedCallsListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("assignedDriverId", driverId)
            .whereIn("status", listOf(
                Constants.STATUS_ASSIGNED,
                Constants.STATUS_ACCEPTED,
                Constants.STATUS_IN_PROGRESS
            ))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->

                if (e != null) {
                    _assignedCall.value = null
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val document = snapshot.documents[0]
                    try {
                        val callInfo = parseCallDocument(document)

                        val previousCallState = _assignedCall.value

                        if (callInfo != null && (callInfo.statusEnum == CallStatus.ASSIGNED || callInfo.statusEnum == CallStatus.ACCEPTED || callInfo.statusEnum == CallStatus.IN_PROGRESS)) {
                            val isTrulyNewCallForAlert = (previousCallState == null || previousCallState.id != callInfo.id) &&
                                                       (callInfo.statusEnum == CallStatus.WAITING || callInfo.statusEnum == CallStatus.ACCEPTED || callInfo.statusEnum == CallStatus.ASSIGNED)

                            if (isTrulyNewCallForAlert) {
                                triggerNewCallAlert(callInfo)
                            }

                            if (previousCallState?.id != callInfo.id || previousCallState?.statusEnum != callInfo.statusEnum) {
                                _assignedCall.value = callInfo
                            } else {
                            }
                        } else {
                            if (previousCallState != null) {
                                _assignedCall.value = null
                            }
                        }
                    } catch (ex: Exception) {
                        if (_assignedCall.value != null) {
                           _assignedCall.value = null
                        }
                    }
                } else {
                    if (_assignedCall.value != null) {
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
    }

    fun clearAssignedCallState() {
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

                    val notificationTitle: String
                    val notificationText: String
                    var fullScreenPendingIntent: PendingIntent? = null
                    val notificationChannelId: String

                    val callStatusEnum = call?.statusEnum
                    val isInitialAssignment = previousCall == null && call != null && callStatusEnum == CallStatus.ASSIGNED
                    val isSharedCall = call?.callType == "SHARED"

                    if (isInitialAssignment && call != null && !isSharedCall) {
                        notificationTitle = "새로운 호출 배정됨"
                        notificationText = "${call.phoneNumber} 고객님의 호출입니다."
                        notificationChannelId = CHANNEL_ID

                        val fullScreenIntent = Intent(this@DriverForegroundService, MainActivity::class.java).apply {
                            action = Constants.ACTION_SHOW_CALL_DIALOG
                            putExtra(Constants.EXTRA_CALL_INFO, call.id)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        fullScreenPendingIntent = PendingIntent.getActivity(
                            this@DriverForegroundService, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                    } else if (isInitialAssignment && call != null && isSharedCall) {
                        notificationTitle = SERVICE_STATUS_NOTIFICATION_TITLE
                        notificationText = SERVICE_STATUS_NOTIFICATION_TEXT
                        notificationChannelId = SERVICE_STATUS_CHANNEL_ID
                        fullScreenPendingIntent = null
                    } else {
                        notificationTitle = SERVICE_STATUS_NOTIFICATION_TITLE
                        notificationText = SERVICE_STATUS_NOTIFICATION_TEXT
                        notificationChannelId = SERVICE_STATUS_CHANNEL_ID
                        fullScreenPendingIntent = null
                    }

                    val notification = createNotification(notificationChannelId, notificationTitle, notificationText, fullScreenPendingIntent)
                    startForeground(NOTIFICATION_ID, notification)

                    previousCall = call

                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    stopSelf()
                } else {
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopFirestoreListeners()
        // auth 리스너 제거 (메모리 누수 방지)
        authStateListener?.let { auth.removeAuthStateListener(it) }
        authStateListener = null

        // 코루틴 스코프 안전하게 해제 (경합 조건 방지)
        try {
            serviceScope.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling serviceScope: ${e.message}")
        }
    }

    private val binder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): DriverForegroundService = this@DriverForegroundService
    }

    private fun triggerNewCallAlert(callInfo: CallInfo) {

        val intent = Intent(this@DriverForegroundService, MainActivity::class.java).apply {
            action = Constants.ACTION_SHOW_CALL_DIALOG
            putExtra(Constants.EXTRA_CALL_INFO, callInfo)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)

        playNotificationSound()
    }

    private fun playNotificationSound() {
        try {
            val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val r = RingtoneManager.getRingtone(applicationContext, notificationSoundUri)
            r.play()
        } catch (e: Exception) {
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
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
        }
    }

    private fun parseCallDocument(document: com.google.firebase.firestore.DocumentSnapshot): CallInfo? {
        return try {

            val callInfo = document.toObject(CallInfo::class.java)

            callInfo?.apply {
                id = document.id
            }

            callInfo
        } catch (e: Exception) {
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
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            builder.setCategory(NotificationCompat.CATEGORY_CALL)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                .setVibrate(longArrayOf(0, 500, 200, 500))
        } else {
        }

        return builder.build()
    }
}
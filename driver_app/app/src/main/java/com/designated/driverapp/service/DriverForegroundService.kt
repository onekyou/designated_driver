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
import com.designated.driverapp.MainActivity // MainActivity 경로 확인 필요
import com.designated.driverapp.R // R 경로 확인 필요 (앱 리소스)
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.Query // Query import 추가
import kotlinx.coroutines.* // CoroutineScope, Job 등 import
import kotlinx.coroutines.flow.* // StateFlow 등 import
import com.designated.driverapp.model.CallInfo // 모델 import
import com.designated.driverapp.model.CallStatus
import com.designated.driverapp.model.DriverStatus
import com.google.firebase.Timestamp // Timestamp import 추가
import android.graphics.Color // 예시: 중요도 낮출 때 사용 가능
import android.media.RingtoneManager // RingtoneManager import 추가
import kotlinx.coroutines.flow.debounce // Import debounce

private const val TAG = "DriverForegroundService"
private const val CHANNEL_ID = "DriverServiceChannel" // 기존: 긴급 알림용
private const val SERVICE_STATUS_CHANNEL_ID = "DriverServiceStatusChannel" // 신규: 서비스 상태용
private const val NOTIFICATION_ID = 1
private const val SERVICE_STATUS_NOTIFICATION_TITLE = "대리운전 기사앱" // 서비스 상태 알림 제목
private const val SERVICE_STATUS_NOTIFICATION_TEXT = "서비스 실행 중" // 서비스 상태 알림 내용

class DriverForegroundService : Service() {

    // 서비스 자체 스코프 정의 (ViewModelScope와 달리 서비스 생명주기 따름)
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var driverStatusListener: ListenerRegistration? = null
    private var assignedCallsListener: ListenerRegistration? = null

    // 서비스 내부에서 상태 관리 (ViewModel과 유사하게)
    private val _driverStatus = MutableStateFlow<DriverStatus>(DriverStatus.OFFLINE)
    private val _assignedCall = MutableStateFlow<CallInfo?>(null)

    // --- 이전 콜 상태 추적 변수 추가 ---
    private var previousCall: CallInfo? = null
    // --- ---

    // --- 최소 알림 내용 상수 추가 --- -> 서비스 상태 알림용 상수로 변경
    // private val MINIMAL_NOTIFICATION_TITLE = "기사 앱 서비스"
    // private val MINIMAL_NOTIFICATION_TEXT = "백그라운드 실행 중"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        createNotificationChannel() // 채널 생성 함수 호출 (수정됨)
        auth = Firebase.auth
        firestore = FirebaseFirestore.getInstance()

        startListeningForAuthState()
        observeStatusAndManageService() // 상태 관찰 및 알림 관리 로직 호출 (수정됨)
    }

    // --- 사용자 인증 상태 감지 ---
    private fun startListeningForAuthState() {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                Log.d(TAG, "Service Auth: User logged in (${user.uid}). Starting listeners.")
                startFirestoreListeners(user.uid)
            } else {
                Log.d(TAG, "Service Auth: User logged out. Stopping listeners and service.")
                stopFirestoreListeners()
                // Optional: Clear internal state flows?
                // _driverStatus.value = DriverStatus.OFFLINE
                // _assignedCall.value = null
                // --- 명시적 서비스 종료 추가 ---
                Log.d(TAG,"[Service Lifecycle] User logged out. Calling stopSelf().")
                stopSelf() // 로그아웃 시 서비스 확실히 종료
                // --- ---
            }
        }
    }

    // --- Firestore 리스너 설정 ---
    private fun startFirestoreListeners(driverId: String) {
        stopFirestoreListeners() // 기존 리스너 제거

        // SharedPreferences에서 regionId와 officeId 읽어오기
        val prefs = getSharedPreferences("driver_prefs", Context.MODE_PRIVATE) // Prefs 이름 확인 필요
        val regionId = prefs.getString("regionId", null)
        val officeId = prefs.getString("officeId", null)

        if (regionId == null || officeId == null) {
            Log.e(TAG, "Service Error: regionId or officeId is null in SharedPreferences. Cannot start listeners.")
            // TODO: Handle this error appropriately (e.g., stop service, notify user)
             _driverStatus.value = DriverStatus.OFFLINE // Indicate an error state
            return
        }

        Log.d(TAG, "Starting Firestore listeners with Region ID: $regionId, Office ID: $officeId, Driver ID: $driverId")

        // 기사 상태 리스너 경로 수정
        val driverDocPath = "regions/$regionId/offices/$officeId/designated_drivers/$driverId"
        Log.d(TAG, "Setting up driver status listener for path: $driverDocPath")
        driverStatusListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)
            .addSnapshotListener { snapshot, e ->
                // --- 상세 로그 추가 ---
                Log.d(TAG, "[Firestore Status] Listener triggered.")
                if (e != null) {
                    Log.e(TAG, "Service: Error listening for driver status", e)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val statusString = snapshot.getString("status")
                    // --- 상세 로그 추가 ---
                    Log.d(TAG, "[Firestore Status] Received status string: '$statusString'")
                    // --- ---
                    _driverStatus.value = when (statusString) {
                        "대기중" -> DriverStatus.WAITING
                        "운행중" -> DriverStatus.ON_TRIP
                        "운행준비중" -> DriverStatus.PREPARING
                        "오프라인" -> DriverStatus.OFFLINE
                        "OFFLINE" -> DriverStatus.OFFLINE // 영문 상태 추가
                        else -> DriverStatus.OFFLINE
                    }
                } else {
                     Log.d(TAG, "Service: Driver status document does not exist for $driverId")
                    _driverStatus.value = DriverStatus.OFFLINE
                }
            }

        // 할당된 콜 리스너 경로 수정
        val callsPath = "regions/$regionId/offices/$officeId/calls"
        Log.d(TAG, "Setting up assigned calls listener for path: $callsPath")
        assignedCallsListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("assignedDriverId", driverId)
            .whereIn("status", listOf(CallStatus.ASSIGNED.name, CallStatus.ACCEPTED.name, CallStatus.INPROGRESS.name))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, e ->
                Log.d(TAG, "[Firestore Calls] Listener triggered. Snapshot: ${snapshot?.documents?.size ?: "null"}, Exception: ${e?.message}")

                if (e != null) {
                    Log.e(TAG, "[Firestore Calls] Listener error", e)
                    _assignedCall.value = null // 오류 발생 시 null 처리
                    return@addSnapshotListener
                }

                if (snapshot != null && !snapshot.isEmpty) {
                    val document = snapshot.documents[0]
                    Log.d(TAG, "[Firestore Calls] Found document: ${document.id}. Data: ${document.data}")
                    try {
                        val callInfo = parseCallDocument(document)
                        Log.d(TAG, "[Firestore Calls] Parsed call info: ${callInfo?.id}, Status String: ${callInfo?.status}, Status Enum: ${callInfo?.statusEnum}")

                        val previousCallState = _assignedCall.value

                        if (callInfo != null && (callInfo.statusEnum == CallStatus.ASSIGNED || callInfo.statusEnum == CallStatus.ACCEPTED || callInfo.statusEnum == CallStatus.INPROGRESS)) {
                            // 새로운 배차인지 확인 (이전 배차가 없거나, ID가 다른 경우)
                            // 그리고 상태가 WAITING 또는 ACCEPTED 일 때만 알림
                            val isTrulyNewCallForAlert = (previousCallState == null || previousCallState.id != callInfo.id) && 
                                                       (callInfo.statusEnum == CallStatus.WAITING || callInfo.statusEnum == CallStatus.ACCEPTED || callInfo.statusEnum == CallStatus.ASSIGNED)

                            if (isTrulyNewCallForAlert) {
                                Log.i(TAG, "[NEW CALL ALERT] New call assignment detected for alert: ${callInfo.id}, Status Enum: ${callInfo.statusEnum}. Triggering UI.")
                                triggerNewCallAlert(callInfo)
                            }
                            
                            // _assignedCall 상태는 항상 최신 유효한 정보로 업데이트
                            if (previousCallState?.id != callInfo.id || previousCallState?.statusEnum != callInfo.statusEnum) {
                                Log.d(TAG, "[Firestore Calls] Updating _assignedCall. Previous: ${previousCallState?.id}/${previousCallState?.statusEnum}, New: ${callInfo.id}/${callInfo.statusEnum}")
                                _assignedCall.value = callInfo
                            } else {
                                Log.d(TAG, "[Firestore Calls] No change in call ID or status. _assignedCall not updated to avoid unnecessary recomposition.")
                            }
                        } else {
                            // 유효하지 않은 콜 정보 또는 비활성 상태의 콜
                            if (previousCallState != null) { // 이전에 유효한 콜이 있었다면 null로 변경
                                Log.w(TAG, "[Firestore Calls] Call (${callInfo?.id}) is null or status enum (${callInfo?.statusEnum}) is not active. Clearing _assignedCall.")
                                _assignedCall.value = null
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "[Firestore Calls] Error parsing call document ${document.id}", ex)
                        if (_assignedCall.value != null) { // 파싱 에러 발생 시, 기존 콜 정보가 있다면 초기화
                           _assignedCall.value = null
                        }
                    }
                } else {
                    // 스냅샷이 비어있는 경우 (현재 할당된 콜이 없음)
                    if (_assignedCall.value != null) { // 이전에 유효한 콜이 있었다면 null로 변경
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

    // --- ★★★ Add public function to clear call state ★★★ ---
    fun clearAssignedCallState() {
        Log.d(TAG, "[Service External] clearAssignedCallState() called. Clearing _assignedCall and previousCall.")
        _assignedCall.value = null
        previousCall = null // Reset previous state as well
        // Optionally, trigger a notification update if needed, although debounce might handle it
    }
    // --- --- 

    // --- 서비스 상태 관찰 및 알림/수명 관리 수정 ---
    private fun observeStatusAndManageService() {
        serviceScope.launch { // launch open brace
            // CallInfo가 Parcelable 인터페이스를 구현해야 MainActivity로 전달 가능합니다.
            // 구현되어 있지 않다면, 필요한 필드를 개별적으로 Intent에 담아 전달해야 합니다.
            try { // try block starts INSIDE the launch block
                combine(_driverStatus, _assignedCall) { status, call ->
                    Pair(status, call)
                }
                // ★★★ Add debounce to allow Firestore state to settle ★★★
                .debounce(500L) // Wait for 500ms of silence before emitting
                .collect { (status, call) ->
                    // Log call?.status as well
                    Log.d(TAG, "[Service Lifecycle] Debounced state change detected: Status=$status, Call=${call?.id}, PrevCall=${previousCall?.id}, CallStatus String=${call?.status}, CallStatus Enum=${call?.statusEnum}")

                    val notificationTitle: String
                    val notificationText: String
                    var fullScreenPendingIntent: PendingIntent? = null
                    val notificationChannelId: String

                    val callStatusEnum = call?.statusEnum
                    // ★★★ Condition 강화: 명확히 ASSIGNED 상태일 때만 초기 배정으로 간주
                    val isInitialAssignment = previousCall == null && call != null && callStatusEnum == CallStatus.ASSIGNED
                    // Log evaluation details
                    Log.d(TAG, "[Service Lifecycle] Evaluating isInitialAssignment: previousCallIsNull=${previousCall == null}, callIsNotNull=${call != null}, callStatusIsAssigned=${callStatusEnum == CallStatus.ASSIGNED} -> Result=$isInitialAssignment")

                    if (isInitialAssignment) {
                        // === 새로운 호출 배정 시 ===
                        notificationTitle = "새로운 호출 배정됨"
                        notificationText = call?.let {
                            "${it.phoneNumber} 고객님의 호출입니다."
                        } ?: "호출 정보 오류"
                        notificationChannelId = CHANNEL_ID // 긴급 알림 채널 사용
                        Log.d(TAG, "[Service Lifecycle] Condition met: INITIAL Call Assignment. Use URGENT channel ($notificationChannelId). Creating Full-Screen Intent.")

                        // Full-Screen Intent 생성 (기존 로직 유지)
                        val fullScreenIntent = Intent(applicationContext, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            putExtra("from_full_screen_intent", true)
                            call?.id?.let { putExtra("initial_call_id", it) }
                        }
                        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                        fullScreenPendingIntent = PendingIntent.getActivity(applicationContext, 1, fullScreenIntent, flags)

                    } else {
                        // === 그 외 모든 경우 (서비스 실행 상태 표시) ===
                        // ★★★ Log reason for not being initial assignment
                        Log.d(TAG, "[Service Lifecycle] Condition NOT Initial Assignment because: previousCall=${previousCall?.id}, call=${call?.id}, callStatusEnum=${callStatusEnum}. Use STATUS channel.")
                        notificationTitle = SERVICE_STATUS_NOTIFICATION_TITLE // 최소 제목
                        notificationText = SERVICE_STATUS_NOTIFICATION_TEXT   // 최소 내용
                        notificationChannelId = SERVICE_STATUS_CHANNEL_ID // 서비스 상태 채널 사용
                        fullScreenPendingIntent = null // Full-Screen Intent 사용 안 함
                    }

                    // --- 포그라운드 서비스 알림 업데이트 (채널 ID 전달) ---
                    Log.d(TAG, "[Service Lifecycle] Creating/Updating notification: Channel='$notificationChannelId', Title='$notificationTitle', Text='$notificationText', HasFullScreen=${fullScreenPendingIntent != null}")
                    val notification = createNotification(notificationChannelId, notificationTitle, notificationText, fullScreenPendingIntent)
                    Log.d(TAG, "[Service Lifecycle] Calling startForeground...")
                    startForeground(NOTIFICATION_ID, notification)
                    Log.d(TAG, "[Service Lifecycle] startForeground call completed.")

                    // 이전 콜 상태 업데이트
                    previousCall = call

                } // end collect
            } catch (e: Exception) {
                Log.e(TAG, "[Service Lifecycle] CRITICAL: Error in observeStatusAndManageService collect block", e)
                // 정상적인 코루틴 취소(CancellationException)가 아닌 다른 예외 발생 시 서비스 중지
                if (e !is CancellationException) { // JobCancellationException 대신 CancellationException 사용
                    Log.e(TAG, "[Service Lifecycle] Non-cancellation error occurred. Stopping service.")
                    stopSelf() // Stop service on critical errors (excluding cancellation)
                } else {
                    // JobCancellationException 포함 정상적인 취소는 무시 (로그만 남김)
                    Log.d(TAG, "[Service Lifecycle] Coroutine cancelled normally (likely service stopping).")
                }
            }
        } // end launch
    } // end function

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand received")
        // 이제 observeStatusAndManageService에서 상태 변경 시 자동으로 처리함.
        // START_STICKY: 시스템이 서비스를 종료하면 재시작하도록 함
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        stopFirestoreListeners()
        serviceScope.cancel() // 서비스 종료 시 코루틴 스코프 취소
    }

    // --- ★★★ Modify onBind to return a Binder ★★★ ---
    // Need a Binder to allow ViewModel to call the public function
    private val binder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): DriverForegroundService = this@DriverForegroundService
    }

    // --- Helper function to trigger UI for new call ---
    private fun triggerNewCallAlert(callInfo: CallInfo) {
        Log.d(TAG, "triggerNewCallAlert for call ID: ${callInfo.id}, Status String: ${callInfo.status}, Status Enum: ${callInfo.statusEnum.displayName}")

        // MainActivity로 정보를 전달하여 다이얼로그 표시 요청
        // CallInfo 객체가 Parcelable이어야 Intent로 전달 가능
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_CALL_DIALOG
            putExtra(MainActivity.EXTRA_CALL_INFO, callInfo) // CallInfo가 Parcelable이어야 함
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP // MainActivity가 이미 실행 중이면 상단으로 올리고, 아니면 새로 시작
        }
        startActivity(intent)
        Log.d(TAG, "Sent intent to MainActivity to show call dialog.")

        // 알림음 재생 (사용자 설정에 따라 변경 가능하도록 고려)
        playNotificationSound()
    }

    private fun playNotificationSound() {
        try {
            // 사용자 설정에서 알림음 URI를 가져오거나, 기본 알림음 사용
            val notificationSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            // val notificationSoundUri = Uri.parse("android.resource://" + packageName + "/" + R.raw.custom_sound) // 사용자 지정 소리 사용 시
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
    // --- --- 

    // --- 알림 채널 생성 함수 수정 ---
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. 긴급 알림 채널 (기존 채널 수정)
            val urgentChannel = NotificationChannel(
                CHANNEL_ID,
                "긴급 호출 알림", // 채널 이름
                NotificationManager.IMPORTANCE_HIGH // 높은 중요도 (소리/팝업 가능)
            ).apply {
                description = "새로운 대리운전 호출이 배정되었을 때 알립니다."
                // 필요 시 추가 설정: setSound, enableVibration, setLightColor 등
                 enableLights(true)
                 lightColor = Color.RED
                 enableVibration(true)
                 lockscreenVisibility = Notification.VISIBILITY_PUBLIC // 잠금 화면에서도 내용 표시
            }
            notificationManager.createNotificationChannel(urgentChannel)
            Log.d(TAG, "Urgent Notification Channel created/updated: $CHANNEL_ID")

            // 2. 서비스 상태 채널 (신규 생성)
            val statusChannel = NotificationChannel(
                SERVICE_STATUS_CHANNEL_ID,
                "서비스 실행 상태", // 채널 이름
                NotificationManager.IMPORTANCE_LOW // 낮은 중요도 (소리/진동 없음, 상태 표시줄 최소 표시)
            ).apply {
                description = "앱 백그라운드 서비스 실행 상태를 표시합니다."
                 setShowBadge(false) // 앱 아이콘 뱃지 숨김
                 setSound(null, null) // 소리 없음
                 enableVibration(false) // 진동 없음
                 enableLights(false) // 불빛 없음
                 lockscreenVisibility = Notification.VISIBILITY_SECRET // 잠금 화면에서 숨김
            }
            notificationManager.createNotificationChannel(statusChannel)
            Log.d(TAG, "Service Status Notification Channel created: $SERVICE_STATUS_CHANNEL_ID")
        }
    }

    // --- parseCallDocument 함수 ---
    private fun parseCallDocument(document: com.google.firebase.firestore.DocumentSnapshot): CallInfo? {
        return try {
            val callInfo = document.toObject(CallInfo::class.java)
            // callInfo.status는 이미 String. statusEnum을 통해 Enum 값 접근 가능.
            callInfo?.apply { id = document.id } // id 할당은 여기서
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing call document: ${document.id}", e)
            null
        }
    }

    // --- 알림 생성 함수 수정 (channelId 파라미터 추가) ---
    private fun createNotification(
        channelId: String, // 사용할 채널 ID
        title: String,
        text: String,
        fullScreenPendingIntent: PendingIntent? = null // Full-Screen Intent (선택 사항)
    ): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        // Add the current call ID if available
        _assignedCall.value?.id?.let { callId ->
            Log.d(TAG, "Adding callId: $callId to notification content intent")
            notificationIntent.putExtra("call_id", callId) // Use "call_id" as the key
            // Add flags to ensure the activity receives the updated intent
            notificationIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val builder = NotificationCompat.Builder(this, channelId) // 생성 시 channelId 사용
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // 기본 런처 아이콘 사용
            .setContentIntent(pendingIntent)
            .setPriority(if (channelId == CHANNEL_ID) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW) // 채널 중요도에 맞게 우선순위 설정
            .setOngoing(true) // 사용자가 지울 수 없는 진행 중 알림

        // Full-Screen Intent 설정 (긴급 채널이고, Intent가 있을 때만)
        if (channelId == CHANNEL_ID && fullScreenPendingIntent != null) {
             Log.d(TAG, "Setting Full-Screen Intent for notification.")
            builder.setFullScreenIntent(fullScreenPendingIntent, true)
            // 추가: 카테고리, 소리 등 긴급 알림 속성 강화
            builder.setCategory(NotificationCompat.CATEGORY_CALL) // 카테고리 설정 (전화/알람 등)
                .setSound(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI) // 기본 알림 소리
                .setVibrate(longArrayOf(0, 500, 200, 500)) // 진동 패턴
        } else {
            Log.d(TAG, "NOT Setting Full-Screen Intent. Channel: $channelId, Intent null: ${fullScreenPendingIntent == null}")
        }

        // --- 알림 빌드 전 로그 추가 ---
        Log.d(TAG, "Building notification for channel '$channelId' with title '$title'")
        return builder.build()
    }
} 
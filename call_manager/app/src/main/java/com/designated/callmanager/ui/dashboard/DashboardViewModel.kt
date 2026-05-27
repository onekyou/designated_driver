package com.designated.callmanager.ui.dashboard

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.CallManagerApplication
import com.designated.callmanager.R
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import com.designated.callmanager.data.DriverInfo
import com.designated.callmanager.data.DriverStatus
import com.designated.callmanager.data.PointsInfo
import com.designated.callmanager.data.PointTransaction
import com.designated.callmanager.data.repository.PointRepository
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.di.DatabaseProvider
import com.designated.callmanager.service.CallManagerService
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class DriverApprovalActionState {
    object Idle : DriverApprovalActionState()
    object Loading : DriverApprovalActionState()
    data class Success(val driverId: String, val action: String) : DriverApprovalActionState()
    data class Error(val message: String) : DriverApprovalActionState()
}

data class PreviousDayClosingData(
    val totalCount: Int,
    val completedCount: Int,
    val uncompletedCount: Int,
    val completedCalls: List<CompletedClosingCall>
)

data class CompletedClosingCall(
    val customerName: String,
    val departure: String,
    val destination: String,
    val fare: Long
)

data class ClosingSettlement(
    val date: String, // "yyyy-MM-dd" 형식
    val totalCalls: Int,
    val completedCalls: Int,
    val totalRevenue: Long,
    val details: List<CompletedClosingCall>
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "DashboardViewModel"
        const val ACTION_START_SERVICE = "com.designated.callmanager.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.designated.callmanager.action.STOP_SERVICE"

        fun formatTimeAgo(time: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - time
            val seconds = diff / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            val days = hours / 24

            return when {
                days > 0 -> "${days}일 전"
                hours > 0 -> "${hours}시간 전"
                minutes > 0 -> "${minutes}분 전"
                else -> "방금 전"
            }
        }
    }

    private val auth: FirebaseAuth = Firebase.auth
    private val firestore = FirebaseFirestore.getInstance()
    private val sharedPreferences = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    private val appContext = application.applicationContext

    // Repository 패턴 구성 요소
    private val app: CallManagerApplication by lazy {
        getApplication<Application>() as CallManagerApplication
    }
    private val database: AppDatabase by lazy {
        app.database
    }
    private val callRepository by lazy {
        app.callRepository
    }
    private val driverRepository by lazy {
        app.driverRepository
    }
    private val pointRepository: PointRepository by lazy {
        DatabaseProvider.providePointRepository(
            database = database,
            firestore = firestore,
            scope = DatabaseProvider.provideRepositoryScope()
        )
    }

    private val _provinceId = MutableStateFlow<String?>(null)
    val provinceId: StateFlow<String?> = _provinceId.asStateFlow()

    private val _cityId = MutableStateFlow<String?>(null)
    val cityId: StateFlow<String?> = _cityId.asStateFlow()

    private val _officeId = MutableStateFlow<String?>(null)
    val officeId: StateFlow<String?> = _officeId.asStateFlow()

    private val _officeName = MutableStateFlow<String?>("사무실 정보 로딩 중...")
    val officeName: StateFlow<String?> = _officeName.asStateFlow()

    private val _officeStatus = MutableStateFlow<String>("")
    val officeStatus: StateFlow<String> = _officeStatus.asStateFlow()

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls

    private val _drivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    val drivers: StateFlow<List<DriverInfo>> = _drivers

    private val _sharedCalls = MutableStateFlow<List<com.designated.callmanager.data.SharedCallInfo>>(emptyList())
    val sharedCalls: StateFlow<List<com.designated.callmanager.data.SharedCallInfo>> = _sharedCalls.asStateFlow()

    private val _allSharedCalls = MutableStateFlow<List<com.designated.callmanager.data.SharedCallInfo>>(emptyList())
    val allSharedCalls: StateFlow<List<com.designated.callmanager.data.SharedCallInfo>> = _allSharedCalls.asStateFlow()

    // 공유콜 30분 자동 필터 + 로컬 dismiss
    private val sharedMap = mutableMapOf<String, com.designated.callmanager.data.SharedCallInfo>()
    private val dismissedSharedPrefs =
        application.getSharedPreferences("dismissed_shared_calls", Context.MODE_PRIVATE)
    private val dismissedIds: MutableSet<String> =
        dismissedSharedPrefs.getStringSet("ids", emptySet())?.toMutableSet() ?: mutableSetOf()
    private var sharedCallTickerJob: Job? = null
    private val SHARED_CALL_MAX_AGE_MS = 30L * 60L * 1000L

    private val _showSharedCallTakenDialog = MutableStateFlow(false)
    val showSharedCallTakenDialog: StateFlow<Boolean> = _showSharedCallTakenDialog.asStateFlow()

    private val _pointsInfo = MutableStateFlow<PointsInfo?>(null)
    val pointsInfo: StateFlow<PointsInfo?> = _pointsInfo.asStateFlow()

    private val _pointTransactions = MutableStateFlow<List<PointTransaction>>(emptyList())
    val pointTransactions: StateFlow<List<PointTransaction>> = _pointTransactions.asStateFlow()

    private val _callInfoForDialog = MutableStateFlow<CallInfo?>(null)
    val callInfoForDialog: StateFlow<CallInfo?> = _callInfoForDialog.asStateFlow()

    // 관리자 직접운행 다이얼로그 상태
    private val _directRunCall = MutableStateFlow<CallInfo?>(null)
    val directRunCall: StateFlow<CallInfo?> = _directRunCall.asStateFlow()

    fun requestDirectRun(call: CallInfo) { _directRunCall.value = call }
    fun dismissDirectRun() { _directRunCall.value = null }

    private val _showApprovalPopup = MutableStateFlow(false)
    val showApprovalPopup: StateFlow<Boolean> = _showApprovalPopup
    private val _driverForApproval = MutableStateFlow<DriverInfo?>(null)
    val driverForApproval: StateFlow<DriverInfo?> = _driverForApproval
    private val _approvalActionState = MutableStateFlow<DriverApprovalActionState>(DriverApprovalActionState.Idle)
    val approvalActionState: StateFlow<DriverApprovalActionState> = _approvalActionState
    private val _showTripStartedPopup = MutableStateFlow(false)
    val showTripStartedPopup: StateFlow<Boolean> = _showTripStartedPopup
    private val _tripStartedInfo = MutableStateFlow<Triple<String, String?, String>?>(null)
    val tripStartedInfo: StateFlow<Triple<String, String?, String>?> = _tripStartedInfo
    private val _showTripCompletedPopup = MutableStateFlow(false)
    val showTripCompletedPopup: StateFlow<Boolean> = _showTripCompletedPopup
    private val _tripCompletedInfo = MutableStateFlow<Pair<String, String>?>(null)
    val tripCompletedInfo: StateFlow<Pair<String, String>?> = _tripCompletedInfo
    private val _showCanceledCallPopup = MutableStateFlow(false)
    val showCanceledCallPopup: StateFlow<Boolean> = _showCanceledCallPopup
    private val _canceledCallInfo = MutableStateFlow<Pair<String, String>?>(null)
    val canceledCallInfo: StateFlow<Pair<String, String>?> = _canceledCallInfo
    private val _showNewCallPopup = MutableStateFlow(false)
    val showNewCallPopup: StateFlow<Boolean> = _showNewCallPopup
    private val _isUserClickedCall = MutableStateFlow(false)  // 사용자가 콜 리스트 클릭으로 열었는지 여부
    val isUserClickedCall: StateFlow<Boolean> = _isUserClickedCall
    private val _newCallInfo = MutableStateFlow<CallInfo?>(null)
    val newCallInfo: StateFlow<CallInfo?> = _newCallInfo
    private val _showNewSharedCallPopup = MutableStateFlow(false)
    val showNewSharedCallPopup: StateFlow<Boolean> = _showNewSharedCallPopup
    private val _newSharedCallInfo = MutableStateFlow<com.designated.callmanager.data.SharedCallInfo?>(null)
    val newSharedCallInfo: StateFlow<com.designated.callmanager.data.SharedCallInfo?> = _newSharedCallInfo

    // 스낵바 에러 메시지
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }

    // 알림 전달 실패 팝업 관련
    data class NotificationFailureInfo(
        val callId: String,
        val driverName: String,
        val driverId: String,
        val presenceStatus: String,
        val message: String
    )
    private val _showNotificationFailurePopup = MutableStateFlow(false)
    val showNotificationFailurePopup: StateFlow<Boolean> = _showNotificationFailurePopup
    private val _notificationFailureInfo = MutableStateFlow<NotificationFailureInfo?>(null)
    val notificationFailureInfo: StateFlow<NotificationFailureInfo?> = _notificationFailureInfo

    // FCM 알림 클릭으로 인한 팝업인지 구분하는 플래그
    private val _isFromFcmNotification = MutableStateFlow(false)
    val isFromFcmNotification: StateFlow<Boolean> = _isFromFcmNotification

    // 새 호출 입력 다이얼로그 관련
    private val _showNewCallInputDialog = MutableStateFlow(false)
    val showNewCallInputDialog: StateFlow<Boolean> = _showNewCallInputDialog

    // 내부호출 배차팝업 관련 (새콜 추가 시 바로 표시)
    private val _internalCallForAssignment = MutableStateFlow<CallInfo?>(null)
    val internalCallForAssignment: StateFlow<CallInfo?> = _internalCallForAssignment.asStateFlow()

    // 새 호출 생성 중 로딩 상태 (중복 클릭 방지)
    private val _isCreatingCall = MutableStateFlow(false)
    val isCreatingCall: StateFlow<Boolean> = _isCreatingCall.asStateFlow()

    // 배차 중 로딩 상태 (중복 클릭 방지)
    private val _isAssigning = MutableStateFlow(false)
    val isAssigning: StateFlow<Boolean> = _isAssigning.asStateFlow()

    // 새 호출 입력 다이얼로그에서 입력된 정보
    data class NewCallInputData(
        val phoneNumber: String = "",
        val departure: String = "",
        val destination: String = "",
        val fare: Long = 0
    )
    private val _pendingNewCallData = MutableStateFlow<NewCallInputData?>(null)
    val pendingNewCallData: StateFlow<NewCallInputData?> = _pendingNewCallData

    // 전날 마감내역 관련
    private val _showPreviousDayClosingDialog = MutableStateFlow(false)
    val showPreviousDayClosingDialog: StateFlow<Boolean> = _showPreviousDayClosingDialog.asStateFlow()

    private val _previousDayClosingData = MutableStateFlow<PreviousDayClosingData?>(null)
    val previousDayClosingData: StateFlow<PreviousDayClosingData?> = _previousDayClosingData.asStateFlow()

    // 마감정산 관련
    private val _closingSettlements = MutableStateFlow<List<ClosingSettlement>>(emptyList())
    val closingSettlements: StateFlow<List<ClosingSettlement>> = _closingSettlements.asStateFlow()

    // 연결 상태 (RTDB .info/connected)
    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var callsListener: ListenerRegistration? = null
    private var driversListener: ListenerRegistration? = null
    private var officeStatusListener: ListenerRegistration? = null
    private var sharedCallsListener: ListenerRegistration? = null
    private var allSharedCallsListener: ListenerRegistration? = null
    private var activeCallsListener: ListenerRegistration? = null  // 미완료 콜 실시간 리스너 (FCM 백업용)
    // Repository 패턴으로 변경됨 - Firebase 리스너 제거

    private val callsCache = mutableMapOf<String, CallInfo>()
    private val driverCache = mutableMapOf<String, DriverInfo>()
    private val previousStatusMap = mutableMapOf<String, String?>()
    private var lastCompletedCallId: String? = null
    private var lastCanceledCallId: String? = null

    init {
        fetchCurrentUserAndStartListening()
        startConnectionStatusListener()
    }

    private fun startConnectionStatusListener() {
        val connectedRef = Firebase.database.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                _isConnected.value = snapshot.getValue(Boolean::class.java) ?: false
                Log.d(TAG, "연결 상태 변경: ${_isConnected.value}")
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "연결 상태 리스너 오류: ${error.message}")
                _isConnected.value = false
            }
        })
    }

    fun resetApprovalActionState() {
        _approvalActionState.value = DriverApprovalActionState.Idle
    }

    private fun fetchCurrentUserAndStartListening() {
        val user = auth.currentUser
        if (user != null) {
            val storedProvinceId = sharedPreferences.getString("provinceId", null)
            val storedCityId = sharedPreferences.getString("cityId", null)
            val storedOfficeId = sharedPreferences.getString("officeId", null)
            _provinceId.value = storedProvinceId
            _cityId.value = storedCityId
            _officeId.value = storedOfficeId
            if (!storedProvinceId.isNullOrBlank() && !storedCityId.isNullOrBlank() && !storedOfficeId.isNullOrBlank()) {
                startListening(storedProvinceId, storedCityId, storedOfficeId)
                fetchOfficeName(storedProvinceId, storedCityId, storedOfficeId)
            }
        }
    }

    private fun startListening(provinceId: String, cityId: String, officeId: String) {
        // 이미 같은 office를 리스닝 중이면 중복 시작하지 않음
        if (_provinceId.value == provinceId && _cityId.value == cityId && _officeId.value == officeId && callsListener != null) {
            return
        }

        stopListening()
        val officeRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)

        // Repository 패턴으로 교체됨 - Firebase 리스너 대신 Local DB Flow 구독
        // callsListener와 driversListener는 제거됨
        setupCallsAndDriversObservers(provinceId, cityId, officeId)

        // 사무실 상태 리스너 (하나로 통합 - 메모리 누수 수정)
        officeStatusListener = officeRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val status = snapshot.getString("status") ?: "OPEN"
                _officeStatus.value = status

                // SharedPreferences에 사무실 상태 캐시 저장
                val prefs = getApplication<Application>().getSharedPreferences("office_status_cache", Context.MODE_PRIVATE)
                prefs.edit().putString("current_office_status", status).apply()
            } else {
                // 문서가 없으면 생성
                _officeStatus.value = "OPEN"
                officeRef.set(mapOf("status" to "OPEN"), com.google.firebase.firestore.SetOptions.merge())
            }
        }

        sharedMap.clear()
        startSharedCallTicker()

        val listenerA = firestore.collection("shared_calls")
            .whereEqualTo("sourceProvinceId", provinceId)
            .whereEqualTo("status", "OPEN")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                snapshots?.documentChanges?.forEach { dc ->
                    val doc = dc.document
                    val docData = doc.data
                    val data = doc.toObject(com.designated.callmanager.data.SharedCallInfo::class.java)
                        ?.copy(id = doc.id)

                    val shouldInclude = when {
                        data == null -> false
                        data.sourceOfficeId == officeId -> false
                        data.status == "CLAIMED" && data.claimedOfficeId != officeId -> false
                        data.status == "COMPLETED" -> false
                        else -> true
                    }

                    when (dc.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED, com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            if (shouldInclude) {
                                data?.let { sharedMap[doc.id] = it }
                            } else {
                                sharedMap.remove(doc.id)
                            }
                        }
                        com.google.firebase.firestore.DocumentChange.Type.REMOVED -> {
                            sharedMap.remove(doc.id)
                        }
                    }
                }
                emitSharedCalls()
            }

        sharedCallsListener = listenerA

        allSharedCallsListener = firestore.collection("shared_calls")
            .whereEqualTo("sourceProvinceId", provinceId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                val allCalls = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(com.designated.callmanager.data.SharedCallInfo::class.java)
                        ?.copy(id = doc.id)
                        ?.also {
                        }
                } ?: emptyList()

                _allSharedCalls.value = allCalls.sortedByDescending { it.timestamp?.seconds ?: 0 }
            }

        // Repository 패턴으로 교체됨 - 포인트 데이터 구독 시작
        setupPointsObservers(provinceId, cityId, officeId)
    }

    /**
     * Repository 패턴을 사용한 포인트 데이터 구독 설정
     */
    private fun setupPointsObservers(provinceId: String, cityId: String, officeId: String) {
        Log.d(TAG, "포인트 옵저버 설정: $provinceId/$cityId/$officeId")

        // 1. 포인트 잔액 구독
        viewModelScope.launch {
            pointRepository.getPointsInfoFlow(provinceId, cityId, officeId)
                .collect { pointsInfo ->
                    _pointsInfo.value = pointsInfo ?: PointsInfo(0, null)
                    Log.d(TAG, "포인트 잔액 업데이트: ${pointsInfo?.balance ?: 0}")
                }
        }

        // 2. 거래 내역 구독
        viewModelScope.launch {
            pointRepository.getTransactionsFlow(provinceId, cityId, officeId)
                .collect { transactions ->
                    _pointTransactions.value = transactions
                    Log.d(TAG, "포인트 거래 내역 업데이트: ${transactions.size}개")

                    // 잔액 정합성 검증
                    validateBalanceConsistency(transactions)
                }
        }

        // 3. 초기 데이터 새로고침 (백그라운드)
        viewModelScope.launch {
            try {
                pointRepository.refreshData(provinceId, cityId, officeId)
                Log.d(TAG, "포인트 데이터 새로고침 완료")
            } catch (e: Exception) {
                Log.e(TAG, "포인트 데이터 새로고침 실패", e)
            }
        }
    }

    /**
     * Repository 패턴을 사용한 콜/기사 데이터 구독 설정
     * Local-First 아키텍처: Room DB Flow 구독 + FCM 동기화
     */
    private fun setupCallsAndDriversObservers(provinceId: String, cityId: String, officeId: String) {
        Log.d(TAG, "콜/기사 옵저버 설정: $provinceId/$cityId/$officeId")

        // 1. 콜 목록 Flow 구독
        viewModelScope.launch {
            callRepository.getCallsFlow(provinceId, officeId)
                .collect { calls ->
                    Log.d(TAG, "콜 목록 업데이트: ${calls.size}개")

                    // 팝업 감지 로직
                    for (call in calls) {
                        val cachedCall = callsCache[call.id]

                        // 새로운 WAITING 콜 팝업
                        if (cachedCall == null && call.status == CallStatus.WAITING.firestoreValue) {
                            if (call.fromCallDetector != true && call.fromCallManager != true && call.callType != "SHARED") {
                                val prefs = appContext.getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
                                val popupId = "NEW_CALL_${call.id}"
                                if (!prefs.getBoolean(popupId, false)) {
                                    _newCallInfo.value = call
                                    _showNewCallPopup.value = true
                                    prefs.edit().putBoolean(popupId, true).apply()
                                }
                            }
                        }

                        // 운행 시작 팝업
                        if (call.status == CallStatus.IN_PROGRESS.firestoreValue &&
                            previousStatusMap[call.id] != CallStatus.IN_PROGRESS.firestoreValue) {
                            val tripSummary = buildString {
                                append("출발: ${call.departure_set ?: call.customerAddress ?: "정보없음"}")
                                if (!call.waypoints_set.isNullOrBlank()) {
                                    append("\n경유: ${call.waypoints_set}")
                                }
                                append("\n도착: ${call.destination_set ?: "정보없음"}")
                                append("\n요금: ${call.fare_set ?: call.fare ?: 0}원")
                            }
                            val driverDisplayName = if (call.callType == "SHARED") {
                                "공유 기사님"
                            } else {
                                call.assignedDriverName ?: "기사"
                            }
                            _tripStartedInfo.value = Triple(
                                driverDisplayName,
                                call.assignedDriverPhone,
                                tripSummary
                            )
                            _showTripStartedPopup.value = true
                        }

                        // 운행 완료 팝업 (직접운행은 관리자 본인이 처리하므로 스킵)
                        if (call.status == CallStatus.COMPLETED.firestoreValue &&
                            previousStatusMap[call.id] != CallStatus.COMPLETED.firestoreValue &&
                            call.handledByManager != true) {
                            val prefs = appContext.getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
                            val popupId = "TRIP_COMPLETED_${call.id}"
                            if (!prefs.getBoolean(popupId, false)) {
                                val driverName = if (call.callType == "SHARED") {
                                    "공유 기사님"
                                } else {
                                    call.assignedDriverName ?: "기사"
                                }
                                val customerName = call.customerName?.takeIf { it.isNotBlank() } ?: "고객"
                                _tripCompletedInfo.value = Pair(driverName, customerName)
                                _showTripCompletedPopup.value = true
                                prefs.edit().putBoolean(popupId, true).apply()
                            }
                        }

                        previousStatusMap[call.id] = call.status
                        callsCache[call.id] = call
                    }

                    // 삭제된 콜 감지
                    val currentCallIds = calls.map { it.id }.toSet()
                    val removedCallIds = callsCache.keys.filter { it !in currentCallIds }
                    removedCallIds.forEach {
                        callsCache.remove(it)
                        previousStatusMap.remove(it)
                    }

                    _calls.value = calls
                }
        }

        // 2. 기사 목록 Flow 구독
        viewModelScope.launch {
            driverRepository.getDriversFlow(provinceId, officeId)
                .collect { drivers ->
                    Log.d(TAG, "기사 목록 업데이트: ${drivers.size}명")

                    // 캐시 업데이트
                    driverCache.clear()
                    drivers.forEach { driverCache[it.id] = it }

                    _drivers.value = drivers
                }
        }

        // 3. 초기 데이터 새로고침 (백그라운드)
        viewModelScope.launch {
            try {
                Log.d(TAG, "콜 데이터 새로고침 시작...")
                callRepository.refreshData(provinceId, cityId, officeId)
                Log.d(TAG, "콜 데이터 새로고침 완료")
            } catch (e: Exception) {
                Log.e(TAG, "콜 데이터 새로고침 실패", e)
            }
        }

        viewModelScope.launch {
            try {
                Log.d(TAG, "기사 데이터 새로고침 시작...")
                driverRepository.refreshData(provinceId, cityId, officeId)
                Log.d(TAG, "기사 데이터 새로고침 완료")
            } catch (e: Exception) {
                Log.e(TAG, "기사 데이터 새로고침 실패", e)
            }
        }

        // 4. 미완료 콜 실시간 리스너 (FCM 백업용, 비용 최소화)
        // 조건: 12시간 내 생성된 미완료 콜만 감시 (보통 0-2개)
        startActiveCallsListener(provinceId, cityId, officeId)
    }

    /**
     * 미완료 콜 실시간 리스너 (FCM 백업용)
     * 조건: 12시간 내 생성 + 미완료 상태 (OPEN, ASSIGNED, IN_PROGRESS)
     * 비용: 보통 0-2개 문서만 감시하므로 매우 저렴
     */
    private fun startActiveCallsListener(provinceId: String, cityId: String, officeId: String) {
        activeCallsListener?.remove()

        val twelveHoursAgo = com.google.firebase.Timestamp(
            java.util.Date(System.currentTimeMillis() - 12 * 60 * 60 * 1000)
        )

        activeCallsListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereIn("status", listOf("OPEN", "WAITING", "ASSIGNED", "RESERVED", "ACCEPTED", "IN_PROGRESS"))
            .whereGreaterThan("timestamp", twelveHoursAgo)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "미완료 콜 리스너 오류", e)
                    return@addSnapshotListener
                }

                val activeCalls = snapshots?.documents?.size ?: 0
                Log.d(TAG, "🔄 미완료 콜 리스너: ${activeCalls}개 감지")

                // 변경된 콜을 로컬 DB에 upsert (FCM과 중복되어도 REPLACE로 처리됨)
                viewModelScope.launch {
                    snapshots?.documentChanges?.forEach { dc ->
                        if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED ||
                            dc.type == com.google.firebase.firestore.DocumentChange.Type.MODIFIED) {
                            val doc = dc.document
                            try {
                                val callInfo = parseCallDocument(doc)
                                if (callInfo != null) {
                                    callRepository.upsertCallFromListener(callInfo, provinceId, officeId)
                                    Log.d(TAG, "🔄 리스너에서 콜 upsert: ${callInfo.id}")
                                }
                            } catch (ex: Exception) {
                                Log.e(TAG, "리스너 콜 파싱 실패: ${doc.id}", ex)
                            }
                        }
                    }
                }
            }
    }

    /**
     * 콜 데이터 새로고침 (브로드캐스트 수신 시 호출)
     */
    fun refreshCallData() {
        val provinceId = _provinceId.value ?: return
        val cityId = _cityId.value ?: return
        val officeId = _officeId.value ?: return

        viewModelScope.launch {
            try {
                Log.d(TAG, "📞 콜 데이터 새로고침 (브로드캐스트)")
                callRepository.refreshData(provinceId, cityId, officeId)
                Log.d(TAG, "📞 콜 데이터 새로고침 완료")
            } catch (e: Exception) {
                Log.e(TAG, "콜 데이터 새로고침 실패", e)
            }
        }
    }

    private fun updateCallsFromCache() {
        // RESERVED 콜은 별도 섹션(하단)으로 분리 — reservedAt 시간순. 그 외는 timestamp 시간순.
        // Screen 측은 statusEnum 으로 그룹핑해 일반 큐 / 예약 큐 별도 표시 가능.
        _calls.value = callsCache.values.sortedWith(
            compareBy<CallInfo> { if (it.status == CallStatus.RESERVED.firestoreValue) 1 else 0 }
                .thenByDescending {
                    if (it.status == CallStatus.RESERVED.firestoreValue) {
                        it.reservedAt?.toDate()?.time ?: 0L
                    } else {
                        it.timestampClient ?: it.timestamp.toDate().time
                    }
                }
        )
    }

    private fun parseCallDocument(doc: com.google.firebase.firestore.DocumentSnapshot): CallInfo? {
        return try {
            doc.toObject(CallInfo::class.java)?.apply { id = doc.id }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 콜에 메모(memoText) + 파싱된 4필드(departure_set/waypoints_set/destination_set/fare_set)를 저장.
     * WAITING 상태에서만 가능 (타 매니저 동시 배차 race 방지).
     *
     * STT 파싱으로 구조화된 값이 있으면 해당 필드도 함께 Firestore 에 저장 → 기사앱 운행 준비 카드에 자동 반영.
     * 파싱 실패 필드는 null 로 전달 → 해당 필드는 건드리지 않음(기존 값 보존).
     *
     * @throws IllegalStateException status != WAITING 인 경우
     * @throws Exception Firestore 트랜잭션 실패 등
     */
    suspend fun updateCallMemo(
        callId: String,
        memoText: String?,
        departure: String? = null,
        waypoints: String? = null,
        destination: String? = null,
        fare: Long? = null
    ) {
        val provinceId = _provinceId.value
            ?: throw IllegalStateException("provinceId null — 로그인 정보 없음")
        val cityId = _cityId.value
            ?: throw IllegalStateException("cityId null")
        val officeId = _officeId.value
            ?: throw IllegalStateException("officeId null")

        val callRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls").document(callId)

        firestore.runTransaction { tx ->
            val snap = tx.get(callRef)
            val status = snap.getString("status")
            if (status != CallStatus.WAITING.firestoreValue) {
                throw IllegalStateException("메모 수정은 WAITING 상태에서만 가능 (현재: $status)")
            }

            val updates = mutableMapOf<String, Any?>("memoText" to memoText)
            if (!departure.isNullOrBlank()) updates["departure_set"] = departure
            if (!waypoints.isNullOrBlank()) updates["waypoints_set"] = waypoints
            if (!destination.isNullOrBlank()) updates["destination_set"] = destination
            if (fare != null && fare > 0L) updates["fare_set"] = fare

            @Suppress("UNCHECKED_CAST")
            tx.update(callRef, updates as Map<String, Any>)
        }.await()

        Log.d(
            TAG,
            "메모 저장 완료: callId=$callId, memoLen=${memoText?.length ?: 0}, " +
                "dep=$departure, way=$waypoints, dest=$destination, fare=$fare"
        )
    }

    fun assignCallToDriver(callInfo: CallInfo, driverId: String) {
        Log.d(TAG, "🚀🚀🚀 assignCallToDriver 호출됨! callId=${callInfo.id}, driverId=$driverId")

        // 중복 클릭 방지
        if (_isAssigning.value) {
            Log.w(TAG, "⚠️ 이미 배차 진행 중입니다. 중복 요청 무시.")
            return
        }

        if (_provinceId.value == null || _cityId.value == null || _officeId.value == null) {
            Log.e(TAG, "❌ provinceId/cityId/officeId가 null")
            return
        }

        // 네트워크 연결 확인
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        if (activeNetwork == null || !activeNetwork.isConnected) {
            Log.e(TAG, "❌ 네트워크 연결 없음")
            _snackbarMessage.value = "배차 실패 - 네트워크 연결을 확인 후 다시 시도하세요"
            return
        }

        val officePath = firestore.collection("provinces").document(_provinceId.value!!)
            .collection("cities").document(_cityId.value!!)
            .collection("offices").document(_officeId.value!!)

        _isAssigning.value = true
        viewModelScope.launch {
            try {
                val driverSnapshot = officePath.collection("designated_drivers").document(driverId).get().await()
                val driverInfo = driverSnapshot.toObject(DriverInfo::class.java)
                if (driverInfo == null) {
                    return@launch
                }

                val driverAuthUid = driverInfo.authUid
                if (driverAuthUid.isNullOrBlank()) {
                    return@launch
                }

                val callRef = officePath.collection("calls").document(callInfo.id)
                val driverRef = officePath.collection("designated_drivers").document(driverId)

                val callUpdates = mapOf(
                    "assignedDriverId" to driverAuthUid,
                    "assignedDriverName" to driverInfo.name,
                    "assignedDriverPhone" to (driverInfo.phoneNumber ?: ""),
                    "status" to CallStatus.ASSIGNED.firestoreValue,
                    "assignedTimestamp" to Timestamp.now(),
                    "updatedAt" to Timestamp.now()
                )

                // 트랜잭션으로 status==WAITING 확인 후 배차 (이중 배차 방지)
                firestore.runTransaction { transaction ->
                    val callDoc = transaction.get(callRef)
                    val currentStatus = callDoc.getString("status")
                    if (currentStatus != CallStatus.WAITING.firestoreValue && currentStatus != CallStatus.HOLD.firestoreValue) {
                        throw IllegalStateException("ALREADY_ASSIGNED")
                    }

                    // 기사 상태 확인 (WAITING 또는 ONLINE인 경우만 배차 허용)
                    val driverDoc = transaction.get(driverRef)
                    val driverStatus = driverDoc.getString("status")
                    if (driverStatus != DriverStatus.WAITING.value && driverStatus != DriverStatus.ONLINE.value) {
                        throw IllegalStateException("DRIVER_NOT_AVAILABLE")
                    }

                    transaction.update(callRef, callUpdates)
                    transaction.update(driverRef, "status", DriverStatus.ASSIGNED.value)
                }.await()

                // 트랜잭션 성공 시 로컬 Room DB에도 배차 정보 업데이트
                callRepository.updateAssignment(
                    callId = callInfo.id,
                    driverId = driverAuthUid,
                    driverName = driverInfo.name ?: "",
                    driverPhone = driverInfo.phoneNumber ?: "",
                    status = CallStatus.ASSIGNED.firestoreValue
                )
                Log.d(TAG, "로컬 DB 배차 정보 업데이트 완료: ${callInfo.id}")

                // 기사 FCM 알림은 oncallassigned 트리거가 자동 발송 (이중 발송 방지)
                Log.d(TAG, "배차 완료 - FCM은 oncallassigned 트리거에서 자동 전송됩니다")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 배차 실패: ${e.message}", e)
                if (e.message?.contains("ALREADY_ASSIGNED") == true ||
                    e.cause?.message?.contains("ALREADY_ASSIGNED") == true) {
                    _snackbarMessage.value = "이미 다른 기사에게 배차된 콜입니다"
                } else if (e.message?.contains("DRIVER_NOT_AVAILABLE") == true ||
                    e.cause?.message?.contains("DRIVER_NOT_AVAILABLE") == true) {
                    _snackbarMessage.value = "해당 기사는 현재 배차할 수 없는 상태입니다"
                } else {
                    _snackbarMessage.value = "배차 실패 - 네트워크 연결을 확인 후 다시 시도하세요"
                }
                // 배차 실패 시 콜 목록 새로고침 (최신 상태 반영)
                refreshCallData()
            } finally {
                _isAssigning.value = false
            }
        }
    }

    /**
     * 신규콜 예약 배차 — 운행중(ON_TRIP) 기사에게 다음 콜을 RESERVED 상태로 걸어둠.
     *
     * 흐름: 매니저가 배차 다이얼로그에서 운행중 기사 선택 → 확인 다이얼로그 → 본 함수 호출.
     *
     * 트랜잭션:
     *  1. 1슬롯 사전 체크 (같은 기사에 RESERVED 콜 0건이어야 진행)
     *  2. calls.status = RESERVED, assignedDriverId = driverAuthUid, reservedAt = serverTimestamp
     *  3. driver doc 은 손대지 않음 (status=ON_TRIP 유지) — 격리 원칙
     *
     * 기존 ASSIGNED 흐름과 분리: assignCallToDriver 는 status WAITING/ONLINE 만 허용 → 영향 0.
     */
    fun assignReservation(callInfo: CallInfo, driverId: String) {
        Log.d(TAG, "📌 assignReservation 호출: callId=${callInfo.id}, driverId=$driverId")

        if (_isAssigning.value) {
            Log.w(TAG, "⚠️ 배차 진행 중 — 중복 요청 무시")
            return
        }

        if (_provinceId.value == null || _cityId.value == null || _officeId.value == null) {
            Log.e(TAG, "❌ provinceId/cityId/officeId null")
            return
        }

        val officePath = firestore.collection("provinces").document(_provinceId.value!!)
            .collection("cities").document(_cityId.value!!)
            .collection("offices").document(_officeId.value!!)

        _isAssigning.value = true
        viewModelScope.launch {
            try {
                val driverSnapshot = officePath.collection("designated_drivers").document(driverId).get().await()
                val driverInfo = driverSnapshot.toObject(DriverInfo::class.java) ?: run {
                    Log.e(TAG, "❌ 기사 정보 없음")
                    return@launch
                }
                val driverAuthUid = driverInfo.authUid
                if (driverAuthUid.isNullOrBlank()) {
                    Log.e(TAG, "❌ 기사 authUid 없음")
                    return@launch
                }

                // 1슬롯 사전 체크 (트랜잭션 밖에서도 1차) — 같은 기사에 이미 예약 콜 있으면 abort
                val existingReserved = officePath.collection("calls")
                    .whereEqualTo("assignedDriverId", driverAuthUid)
                    .whereEqualTo("status", CallStatus.RESERVED.firestoreValue)
                    .limit(1)
                    .get().await()
                if (!existingReserved.isEmpty) {
                    _snackbarMessage.value = "${driverInfo.name ?: "해당 기사"}는 이미 예약 1건 보유 중입니다"
                    return@launch
                }

                val callRef = officePath.collection("calls").document(callInfo.id)

                firestore.runTransaction { transaction ->
                    val callDoc = transaction.get(callRef)
                    val currentStatus = callDoc.getString("status")
                    if (currentStatus != CallStatus.WAITING.firestoreValue && currentStatus != CallStatus.HOLD.firestoreValue) {
                        throw IllegalStateException("CALL_NOT_WAITING")
                    }

                    val updates = mapOf(
                        "assignedDriverId" to driverAuthUid,
                        "assignedDriverName" to driverInfo.name,
                        "assignedDriverPhone" to (driverInfo.phoneNumber ?: ""),
                        "status" to CallStatus.RESERVED.firestoreValue,
                        "reservedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to Timestamp.now()
                    )
                    transaction.update(callRef, updates)
                    // driver doc 은 의도적으로 손대지 않음 (status=ON_TRIP 유지)
                }.await()

                _snackbarMessage.value = "${driverInfo.name ?: "기사"}에게 예약 배차 완료 — 운행 종료 후 처리됩니다"
                Log.d(TAG, "📌 예약 배차 트랜잭션 완료: ${callInfo.id}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 예약 배차 실패: ${e.message}", e)
                val msg = e.message ?: ""
                val cause = e.cause?.message ?: ""
                _snackbarMessage.value = when {
                    msg.contains("CALL_NOT_WAITING") || cause.contains("CALL_NOT_WAITING") ->
                        "이미 다른 기사에게 배차되었거나 진행 중인 콜입니다"
                    else -> "예약 배차 실패 — 네트워크 또는 권한 확인"
                }
                refreshCallData()
            } finally {
                _isAssigning.value = false
            }
        }
    }

    /**
     * 신규콜 예약 취소 — 매니저가 RESERVED 콜의 [예약 취소] 누름.
     *
     * 트랜잭션: status RESERVED → WAITING, assignedDriverId 제거, reservedAt 제거.
     * 매니저는 이후 일반 배차 다이얼로그에서 다른 기사로 재배차 가능.
     */
    fun cancelReservation(callId: String) {
        Log.d(TAG, "📌 cancelReservation 호출: callId=$callId")

        if (_provinceId.value == null || _cityId.value == null || _officeId.value == null) return

        val callRef = firestore.collection("provinces").document(_provinceId.value!!)
            .collection("cities").document(_cityId.value!!)
            .collection("offices").document(_officeId.value!!)
            .collection("calls").document(callId)

        viewModelScope.launch {
            try {
                firestore.runTransaction { transaction ->
                    val callDoc = transaction.get(callRef)
                    val currentStatus = callDoc.getString("status")
                    if (currentStatus != CallStatus.RESERVED.firestoreValue) {
                        throw IllegalStateException("NOT_RESERVED")
                    }
                    val updates = mapOf<String, Any?>(
                        "status" to CallStatus.WAITING.firestoreValue,
                        "assignedDriverId" to null,
                        "assignedDriverName" to null,
                        "assignedDriverPhone" to null,
                        "reservedAt" to FieldValue.delete(),
                        "updatedAt" to Timestamp.now()
                    )
                    transaction.update(callRef, updates)
                }.await()

                _snackbarMessage.value = "예약 취소됨 — 다른 기사로 재배차 가능"
                Log.d(TAG, "📌 예약 취소 트랜잭션 완료: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 예약 취소 실패: ${e.message}", e)
                val msg = e.message ?: ""
                val cause = e.cause?.message ?: ""
                _snackbarMessage.value = when {
                    msg.contains("NOT_RESERVED") || cause.contains("NOT_RESERVED") ->
                        "이미 처리된 콜입니다"
                    else -> "예약 취소 실패 — 네트워크 또는 권한 확인"
                }
                refreshCallData()
            }
        }
    }

    fun updateCallStatus(callId: String, newStatus: CallStatus) {
        if (_provinceId.value == null || _cityId.value == null || _officeId.value == null) {
            return
        }
        viewModelScope.launch {
            try {
                firestore.collection("provinces").document(_provinceId.value!!)
                    .collection("cities").document(_cityId.value!!)
                    .collection("offices").document(_officeId.value!!)
                    .collection("calls").document(callId)
                    .update("status", newStatus.firestoreValue)
                    .await()
            } catch (e: Exception) {
            }
        }
    }

    fun cancelCall(callId: String) {
        if (_provinceId.value == null || _cityId.value == null || _officeId.value == null) return

        viewModelScope.launch {
            try {
                val officePath = firestore.collection("provinces").document(_provinceId.value!!)
                    .collection("cities").document(_cityId.value!!)
                    .collection("offices").document(_officeId.value!!)

                val callRef = officePath.collection("calls").document(callId)

                // 기사 문서 ref 확보 (트랜잭션 전)
                val preSnapshot = callRef.get().await()
                val preAssignedUid = preSnapshot.getString("assignedDriverId")

                var driverRef: com.google.firebase.firestore.DocumentReference? = null
                if (!preAssignedUid.isNullOrBlank()) {
                    val driversQuery = officePath.collection("designated_drivers")
                        .whereEqualTo("authUid", preAssignedUid)
                        .limit(1)
                        .get()
                        .await()
                    if (!driversQuery.isEmpty) {
                        driverRef = driversQuery.documents[0].reference
                    }
                }

                // 트랜잭션으로 콜 취소 + 기사 복구 원자적 수행
                val cancelResult = firestore.runTransaction { transaction ->
                    val callSnapshot = transaction.get(callRef)
                    val currentStatus = callSnapshot.getString("status")
                    if (currentStatus == CallStatus.CANCELED.firestoreValue || currentStatus == CallStatus.CANCELLED.firestoreValue || currentStatus == CallStatus.CANCELLED_BY_CUSTOMER.firestoreValue || currentStatus == CallStatus.CANCELLED_BY_DRIVER.firestoreValue) {
                        throw IllegalStateException("ALREADY_CANCELLED")
                    }
                    transaction.update(callRef, "status", CallStatus.CANCELED.firestoreValue)

                    // 기사 상태도 트랜잭션 안에서 복구
                    if (driverRef != null) {
                        val driverDoc = transaction.get(driverRef)
                        val driverStatus = driverDoc.getString("status")
                        if (driverStatus == "ASSIGNED" || driverStatus == "ACCEPTED" || driverStatus == "ON_TRIP" || driverStatus == "PREPARING") {
                            transaction.update(driverRef, "status", "WAITING")
                        }
                    }

                    val assignedDriverAuthUid = callSnapshot.getString("assignedDriverId")
                    val sourceSharedCallId = callSnapshot.getString("sourceSharedCallId")
                    mapOf(
                        "assignedDriverAuthUid" to (assignedDriverAuthUid ?: ""),
                        "sourceSharedCallId" to (sourceSharedCallId ?: "")
                    )
                }.await()

                @Suppress("UNCHECKED_CAST")
                val resultMap = cancelResult as Map<String, String>
                val assignedDriverAuthUid = resultMap["assignedDriverAuthUid"]?.takeIf { it.isNotBlank() }
                val sourceSharedCallId = resultMap["sourceSharedCallId"]?.takeIf { it.isNotBlank() }

                // 공유콜이면 shared_calls 문서를 OPEN으로 되돌려 재수락 가능하게 함 (BUG-D11)
                if (sourceSharedCallId != null) {
                    reopenSharedCall(sourceSharedCallId)
                    Log.d(TAG, "공유콜 OPEN으로 복구: $sourceSharedCallId")
                }

                // 기사에게 콜 취소 FCM 전송
                if (assignedDriverAuthUid != null) {
                    try {
                        val functions = Firebase.functions("asia-northeast3")
                        val data = hashMapOf(
                            "callId" to callId,
                            "driverAuthUid" to assignedDriverAuthUid,
                            "provinceId" to _provinceId.value,
                            "cityId" to _cityId.value,
                            "officeId" to _officeId.value
                        )
                        functions.getHttpsCallable("notifyDriverCancellation")
                            .call(data)
                            .addOnSuccessListener { result ->
                                Log.d(TAG, "기사 취소 알림 전송 성공: ${result.getData()}")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "기사 취소 알림 전송 실패: ${e.message}", e)
                            }
                    } catch (e: Exception) {
                        Log.e(TAG, "기사 취소 알림 함수 호출 예외: ${e.message}", e)
                    }
                }

            } catch (e: IllegalStateException) {
                if (e.message == "ALREADY_CANCELLED") {
                    Log.w(TAG, "이미 취소된 콜입니다: $callId")
                } else {
                    Log.e(TAG, "콜 취소 실패: ${e.message}", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "콜 취소 실패: ${e.message}", e)
            }
        }
    }

    fun completeCall(callId: String) {
        if (_provinceId.value == null || _cityId.value == null || _officeId.value == null) return

        viewModelScope.launch {
            try {
                val officePath = firestore.collection("provinces").document(_provinceId.value!!)
                    .collection("cities").document(_cityId.value!!)
                    .collection("offices").document(_officeId.value!!)

                val callRef = officePath.collection("calls").document(callId)

                // 기사 문서 ref 확보 (트랜잭션 전)
                val callSnapshot = callRef.get().await()
                val assignedDriverAuthUid = callSnapshot.getString("assignedDriverId")

                var driverRef: com.google.firebase.firestore.DocumentReference? = null
                if (!assignedDriverAuthUid.isNullOrBlank()) {
                    val driversQuery = officePath.collection("designated_drivers")
                        .whereEqualTo("authUid", assignedDriverAuthUid)
                        .limit(1)
                        .get()
                        .await()
                    if (!driversQuery.isEmpty) {
                        driverRef = driversQuery.documents[0].reference
                    }
                }

                // 트랜잭션으로 콜 + 기사 원자적 업데이트
                firestore.runTransaction { transaction ->
                    val callDoc = transaction.get(callRef)
                    val currentStatus = callDoc.getString("status")
                    if (currentStatus == CallStatus.COMPLETED.firestoreValue) {
                        return@runTransaction
                    }

                    transaction.update(callRef, mapOf(
                        "status" to CallStatus.COMPLETED.firestoreValue,
                        "completedAt" to com.google.firebase.Timestamp.now(),
                        "updatedAt" to com.google.firebase.Timestamp.now()
                    ))

                    if (driverRef != null) {
                        transaction.update(driverRef, "status", "WAITING")
                    }
                }.await()

            } catch (e: Exception) {
                Log.e(TAG, "콜 완료 처리 실패: ${e.message}", e)
            }
        }
    }

    /**
     * 관리자 직접운행 — WAITING 콜을 바로 COMPLETED로 전이하며 정산 필드 기록.
     * 기사 문서/배차 트랜잭션을 타지 않고, handledByManager=true 플래그로 식별.
     * CF `onCallCompletedUpdateSettlement`의 가드가 이 플래그를 보고 정산 세션 추가를 스킵.
     */
    fun completeAsManager(
        callId: String,
        fare: Long,
        paymentMethod: String,
        cashReceived: Long,
        creditAmount: Long,
        pointsUsed: Long,
        customerName: String = "",
        phoneNumber: String = "",
        departure: String = "",
        destination: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_provinceId.value == null || _cityId.value == null || _officeId.value == null) {
            onError("사무실 정보가 없습니다")
            return
        }
        viewModelScope.launch {
            try {
                val callRef = firestore.collection("provinces").document(_provinceId.value!!)
                    .collection("cities").document(_cityId.value!!)
                    .collection("offices").document(_officeId.value!!)
                    .collection("calls").document(callId)

                firestore.runTransaction { transaction ->
                    val callDoc = transaction.get(callRef)
                    val currentStatus = callDoc.getString("status")
                    if (currentStatus != CallStatus.WAITING.firestoreValue &&
                        currentStatus != CallStatus.HOLD.firestoreValue) {
                        throw IllegalStateException("NOT_WAITING")
                    }
                    val now = com.google.firebase.Timestamp.now()
                    val updates = mutableMapOf<String, Any>(
                        "status" to CallStatus.COMPLETED.firestoreValue,
                        "handledByManager" to true,
                        "assignedDriverId" to "MANAGER",
                        "assignedDriverName" to "관리자",
                        "fareFinal" to fare,
                        "fare_set" to fare,
                        "paymentMethod" to paymentMethod,
                        "cashReceived" to cashReceived,
                        "creditAmount" to creditAmount,
                        "pointsUsed" to pointsUsed,
                        "completedAt" to now,
                        "assignedTimestamp" to now,
                        "updatedAt" to now
                    )
                    if (customerName.isNotBlank()) updates["customerName"] = customerName
                    if (phoneNumber.isNotBlank()) updates["phoneNumber"] = phoneNumber
                    if (departure.isNotBlank()) updates["departure_set"] = departure
                    if (destination.isNotBlank()) updates["destination_set"] = destination
                    transaction.update(callRef, updates)
                }.await()

                callRepository.updateAssignment(
                    callId = callId,
                    driverId = "MANAGER",
                    driverName = "관리자",
                    driverPhone = "",
                    status = CallStatus.COMPLETED.firestoreValue
                )
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "직접운행 처리 실패", e)
                val msg = if (e.message?.contains("NOT_WAITING") == true ||
                    e.cause?.message?.contains("NOT_WAITING") == true) {
                    "이미 처리된 콜입니다"
                } else {
                    "직접운행 처리 실패: ${e.message}"
                }
                _snackbarMessage.value = msg
                onError(msg)
            }
        }
    }

    fun showCallDetails(callInfo: CallInfo) {
        _callInfoForDialog.value = callInfo
    }

    private fun getOfficeRef() = provinceId.value?.let { pId ->
        cityId.value?.let { cId ->
            officeId.value?.let { oId ->
                firestore.collection("provinces").document(pId)
                    .collection("cities").document(cId)
                    .collection("offices").document(oId)
            }
        }
    }

    fun startForegroundService(context: Context) {
        val intent = Intent(context, CallManagerService::class.java).apply {
            action = ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun showCallDialog(callId: String) {
        viewModelScope.launch {
            try {
                _isUserClickedCall.value = true  // 사용자 클릭으로 열림 표시
                val callFromCache = _calls.value.find { it.id == callId }
                if (callFromCache != null) {
                    _newCallInfo.value = callFromCache
                    _showNewCallPopup.value = true
                    return@launch
                }

                val province = _provinceId.value ?: return@launch
                val city = _cityId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                val callDocument = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)
                    .collection("calls").document(callId)
                    .get().await()

                if (callDocument.exists()) {
                    val callInfo = parseCallDocument(callDocument)
                    if (callInfo != null) {
                        _newCallInfo.value = callInfo
                        _showNewCallPopup.value = true
                    } else {
                    }
                } else {
                }
            } catch (e: Exception) {
            }
        }
    }

    fun dismissCallDialog() {
        _callInfoForDialog.value = null
    }

    private val _showSharedCallCancelledDialog = MutableStateFlow(false)
    val showSharedCallCancelledDialog: StateFlow<Boolean> = _showSharedCallCancelledDialog.asStateFlow()

    private val _cancelledCallInfo = MutableStateFlow<CallInfo?>(null)
    val cancelledCallInfo: StateFlow<CallInfo?> = _cancelledCallInfo.asStateFlow()

    fun showSharedCallCancelledDialog(callId: String) {
        viewModelScope.launch {
            try {
                val province = _provinceId.value ?: return@launch
                val city = _cityId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                val callDocument = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)
                    .collection("calls").document(callId)
                    .get().await()

                if (callDocument.exists()) {
                    val callInfo = parseCallDocument(callDocument)
                    if (callInfo != null) {
                        _cancelledCallInfo.value = callInfo
                        _showSharedCallCancelledDialog.value = true
                    } else {
                    }
                } else {
                }
            } catch (e: Exception) {
            }
        }
    }

    fun dismissSharedCallCancelledDialog() {
        _showSharedCallCancelledDialog.value = false
        _cancelledCallInfo.value = null
    }

    fun approveDriver(driverId: String) {
        getOfficeRef()?.collection("designated_drivers")?.document(driverId)?.update("status", "대기중")
        dismissApprovalPopup()
    }

    fun rejectDriver(driverId: String) {
        getOfficeRef()?.collection("designated_drivers")?.document(driverId)?.update("status", "거절됨")
        dismissApprovalPopup()
    }

    fun dismissApprovalPopup() {
        _showApprovalPopup.value = false
        _driverForApproval.value = null
        _approvalActionState.value = DriverApprovalActionState.Idle
    }

    fun dismissTripStartedPopup() {
        _showTripStartedPopup.value = false
        _tripStartedInfo.value = null
    }

    fun dismissTripCompletedPopup() {
        _showTripCompletedPopup.value = false
        _tripCompletedInfo.value = null
    }

    fun dismissCanceledCallPopup() {
        _showCanceledCallPopup.value = false
        _canceledCallInfo.value = null
    }

    fun dismissNewCallPopup() {
        _showNewCallPopup.value = false
        _newCallInfo.value = null
        _isUserClickedCall.value = false  // 리셋
    }

    fun dismissNewSharedCallPopup() {
        _showNewSharedCallPopup.value = false
        _newSharedCallInfo.value = null
        _isFromFcmNotification.value = false // FCM 플래그 리셋
    }

    fun assignNewCall(driverId: String) {
        val callInfo = _newCallInfo.value
        if (callInfo != null) {
            assignCallToDriver(callInfo, driverId)
            dismissNewCallPopup()
        }
    }

    /**
     * 내부호출(fromCallManager) 배차 시 출발지/도착지/요금 정보를 먼저 업데이트한 후 배차
     */
    fun assignNewCallWithInfo(driverId: String, departure: String, destination: String, fare: Long, memoText: String? = null) {
        val callInfo = _newCallInfo.value ?: return
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                // Firestore에 출발지/도착지/요금/메모 업데이트
                val callRef = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)
                    .collection("calls").document(callInfo.id)

                val updateData = mutableMapOf<String, Any?>(
                    "customerAddress" to departure.ifBlank { "" },
                    "departure_set" to departure.ifBlank { null },
                    "destination_set" to destination.ifBlank { null },
                    "fare_set" to if (fare > 0) fare else null,
                    "memoText" to memoText?.ifBlank { null }
                )
                callRef.update(updateData as Map<String, Any>).await()

                // 업데이트된 콜 정보로 배차
                val updatedCall = callInfo.copy(
                    customerAddress = departure.ifBlank { null },
                    departure_set = departure.ifBlank { null },
                    destination_set = destination.ifBlank { null },
                    fare_set = if (fare > 0) fare else null,
                    memoText = memoText?.ifBlank { null }
                )
                assignCallToDriver(updatedCall, driverId)
                dismissNewCallPopup()

                Log.d(TAG, "내부호출 정보 업데이트 + 배차 완료: ${callInfo.id}")
            } catch (e: Exception) {
                Log.e(TAG, "내부호출 정보 업데이트 실패", e)
                _snackbarMessage.value = "정보 업데이트 실패: ${e.message}"
            }
        }
    }

    /**
     * 설정 페이지에서 사용하는 사무실 상태 업데이트 함수
     */
    fun updateOfficeStatus(newStatus: String) {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                val officeRef = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)

                val currentStatus = _officeStatus.value

                _officeStatus.value = newStatus

                // SharedPreferences에도 즉시 업데이트
                val prefs = getApplication<Application>().getSharedPreferences("office_status_cache", Context.MODE_PRIVATE)
                prefs.edit().putString("current_office_status", newStatus).apply()

                officeRef.set(mapOf("status" to newStatus), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                    }
                    .addOnFailureListener { e ->
                        _officeStatus.value = currentStatus
                        // 실패 시 캐시도 롤백
                        prefs.edit().putString("current_office_status", currentStatus).apply()
                    }
            } catch (e: Exception) {
            }
        }
    }

    fun createPlaceholderCall() {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        val officeRef = firestore.collection("provinces").document(province)
            .collection("cities").document(city)
            .collection("offices").document(office)

        viewModelScope.launch {
            try {
                val nowTs = Timestamp.now()
                val timestampClient = System.currentTimeMillis()
                val data = hashMapOf(
                    "phoneNumber" to "",
                    "customerAddress" to "",
                    "customerName" to "",
                    "timestamp" to nowTs,
                    "timestampClient" to timestampClient,
                    "status" to CallStatus.WAITING.firestoreValue,
                    "provinceId" to province,
                    "cityId" to city,
                    "officeId" to office,
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "expireAt" to Timestamp(java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
                )

                val docRef = officeRef.collection("calls").add(data).await()

                // 로컬 DB에도 저장하여 UI에 즉시 반영
                callRepository.insertCallFromFCM(
                    callId = docRef.id,
                    phoneNumber = "",
                    customerName = "",
                    customerAddress = "",
                    status = CallStatus.WAITING.firestoreValue,
                    provinceId = province,
                    officeId = office,
                    callType = null,
                    fromCallDetector = false,
                    fromCallManager = true,
                    assignedDriverId = null,
                    assignedDriverName = null,
                    assignedDriverPhone = null
                )
                Log.d(TAG, "새 콜 생성 완료: ${docRef.id}")
            } catch (e: Exception) {
                Log.e(TAG, "새 콜 생성 실패", e)
            }
        }
    }

    /**
     * 콜을 로컬 DB에서 삭제 (Firestore는 유지 - 비용 절감)
     * 콜 목록은 로컬 DB에서 가져오므로 UI에서 즉시 사라짐
     */
    fun deleteCall(callId: String) {
        viewModelScope.launch {
            try {
                // 로컬 DB에서만 삭제
                callRepository.deleteCall(callId)

                // 캐시에서도 제거
                callsCache.remove(callId)
                previousStatusMap.remove(callId)

                if (_newCallInfo.value?.id == callId) {
                    dismissNewCallPopup()
                }
            } catch (e: Exception) {
                Log.e(TAG, "콜 삭제 실패: $callId", e)
            }
        }
    }

    /**
     * 새 호출 입력 다이얼로그 표시
     */
    fun showNewCallInputDialog() {
        _showNewCallInputDialog.value = true
    }

    /**
     * 새 호출 입력 다이얼로그 닫기
     */
    fun dismissNewCallInputDialog() {
        _showNewCallInputDialog.value = false
        _pendingNewCallData.value = null
    }

    /**
     * 빈 콜 생성 후 바로 내부호출 배차팝업 표시
     */
    fun createEmptyCallAndShowAssignment() {
        if (_isCreatingCall.value) {
            Log.d(TAG, "새 콜 생성 중 - 중복 클릭 무시")
            return
        }

        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        val officeRef = firestore.collection("provinces").document(province)
            .collection("cities").document(city)
            .collection("offices").document(office)

        viewModelScope.launch {
            _isCreatingCall.value = true
            try {
                val nowTs = Timestamp.now()
                val timestampClient = System.currentTimeMillis()
                val data = hashMapOf(
                    "phoneNumber" to "",
                    "customerAddress" to "",
                    "customerName" to "",
                    "timestamp" to nowTs,
                    "timestampClient" to timestampClient,
                    "status" to CallStatus.WAITING.firestoreValue,
                    "provinceId" to province,
                    "cityId" to city,
                    "officeId" to office,
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "fromCallManager" to true,
                    "departure_set" to null,
                    "destination_set" to null,
                    "fare_set" to null,
                    "expireAt" to Timestamp(java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
                )

                val docRef = officeRef.collection("calls").add(data).await()

                // 로컬 DB에도 저장
                callRepository.insertCallFromFCM(
                    callId = docRef.id,
                    phoneNumber = "",
                    customerName = "",
                    customerAddress = "",
                    status = CallStatus.WAITING.firestoreValue,
                    provinceId = province,
                    officeId = office,
                    callType = null,
                    fromCallDetector = false,
                    fromCallManager = true,
                    assignedDriverId = null,
                    assignedDriverName = null,
                    assignedDriverPhone = null
                )

                Log.d(TAG, "빈 콜 생성 완료: ${docRef.id}")

                // 생성된 콜로 내부호출 배차팝업 표시
                val createdCall = CallInfo(
                    id = docRef.id,
                    phoneNumber = "",
                    customerName = "",
                    customerAddress = null,
                    status = CallStatus.WAITING.firestoreValue,
                    timestamp = nowTs,
                    departure_set = null,
                    destination_set = null,
                    fare_set = null,
                    fromCallManager = true
                )
                _newCallInfo.value = createdCall
                _showNewCallPopup.value = true

            } catch (e: Exception) {
                Log.e(TAG, "빈 콜 생성 실패", e)
                _snackbarMessage.value = "호출 생성 실패: ${e.message}"
            } finally {
                _isCreatingCall.value = false
            }
        }
    }

    /**
     * 내부호출 배차팝업 닫기
     */
    fun dismissInternalCallAssignment() {
        _internalCallForAssignment.value = null
    }

    /**
     * 내부호출 정보 업데이트 (정보입력 팝업에서 호출)
     */
    fun updateInternalCallInfo(callId: String, phoneNumber: String, departure: String, destination: String, fare: Long) {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        val callRef = firestore.collection("provinces").document(province)
            .collection("cities").document(city)
            .collection("offices").document(office)
            .collection("calls").document(callId)

        viewModelScope.launch {
            try {
                val updateData = mutableMapOf<String, Any?>(
                    "phoneNumber" to phoneNumber,
                    "customerAddress" to departure.ifBlank { "" },
                    "departure_set" to departure.ifBlank { null },
                    "destination_set" to destination.ifBlank { null },
                    "fare_set" to if (fare > 0) fare else null
                )

                callRef.update(updateData as Map<String, Any>).await()

                // 로컬 상태도 업데이트
                _internalCallForAssignment.value?.let { current ->
                    _internalCallForAssignment.value = current.copy(
                        phoneNumber = phoneNumber,
                        customerAddress = departure.ifBlank { null },
                        departure_set = departure.ifBlank { null },
                        destination_set = destination.ifBlank { null },
                        fare_set = if (fare > 0) fare else null
                    )
                }

                Log.d(TAG, "내부호출 정보 업데이트 완료: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "내부호출 정보 업데이트 실패", e)
            }
        }
    }

    /**
     * 입력된 정보로 새 콜 생성 후 배차 다이얼로그 표시
     */
    fun createCallWithInputData(phoneNumber: String, departure: String, destination: String, fare: Long) {
        // 중복 클릭 방지
        if (_isCreatingCall.value) {
            Log.d(TAG, "새 콜 생성 중 - 중복 클릭 무시")
            return
        }

        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        // 네트워크 연결 확인
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        if (activeNetwork == null || !activeNetwork.isConnected) {
            Log.e(TAG, "❌ 네트워크 연결 없음 - 새 콜 생성 불가")
            _snackbarMessage.value = "호출 생성 실패 - 네트워크 연결을 확인 후 다시 시도하세요"
            return
        }

        val officeRef = firestore.collection("provinces").document(province)
            .collection("cities").document(city)
            .collection("offices").document(office)

        viewModelScope.launch {
            _isCreatingCall.value = true
            try {
                // 전화번호가 있는 경우 10초 내 동일번호 중복 체크 (Detector와 동일 로직)
                if (phoneNumber.isNotBlank()) {
                    val duplicateCheckThreshold = System.currentTimeMillis() - 10000L
                    val existingCalls = officeRef.collection("calls")
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
                        Log.w(TAG, "⚠️ 중복 콜 감지: 10초 내 같은 번호(${phoneNumber})의 콜이 이미 존재")
                        _snackbarMessage.value = "같은 번호의 콜이 이미 존재합니다"
                        return@launch
                    }
                }

                val nowTs = Timestamp.now()
                val timestampClient = System.currentTimeMillis()
                val data = hashMapOf(
                    "phoneNumber" to phoneNumber,
                    "customerAddress" to departure.ifBlank { "" },
                    "customerName" to "",
                    "timestamp" to nowTs,
                    "timestampClient" to timestampClient,
                    "status" to CallStatus.WAITING.firestoreValue,
                    "provinceId" to province,
                    "cityId" to city,
                    "officeId" to office,
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "departure_set" to departure.ifBlank { null },
                    "destination_set" to destination.ifBlank { null },
                    "fare_set" to if (fare > 0) fare else null,
                    "expireAt" to Timestamp(java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
                )

                val docRef = officeRef.collection("calls").add(data).await()

                // 로컬 DB에도 저장하여 UI에 즉시 반영
                callRepository.insertCallFromFCM(
                    callId = docRef.id,
                    phoneNumber = phoneNumber,
                    customerName = "",
                    customerAddress = departure.ifBlank { "" },
                    status = CallStatus.WAITING.firestoreValue,
                    provinceId = province,
                    officeId = office,
                    callType = null,
                    fromCallDetector = false,
                    fromCallManager = true,
                    assignedDriverId = null,
                    assignedDriverName = null,
                    assignedDriverPhone = null
                )

                Log.d(TAG, "새 콜 생성 완료 (입력정보 포함): ${docRef.id}")

                // 다이얼로그 닫기
                _showNewCallInputDialog.value = false

                // 생성된 콜로 배차 다이얼로그 표시
                val createdCall = CallInfo(
                    id = docRef.id,
                    phoneNumber = phoneNumber,
                    customerName = "",
                    customerAddress = departure.ifBlank { null },
                    status = CallStatus.WAITING.firestoreValue,
                    timestamp = nowTs,
                    departure_set = departure.ifBlank { null },
                    destination_set = destination.ifBlank { null },
                    fare_set = if (fare > 0) fare else null,
                    fromCallManager = true  // 내부 생성 콜 표시 (알림음 생략용)
                )
                _newCallInfo.value = createdCall
                _showNewCallPopup.value = true

            } catch (e: Exception) {
                Log.e(TAG, "새 콜 생성 실패 (입력정보 포함)", e)
                _snackbarMessage.value = "호출 생성 실패 - 네트워크 연결을 확인 후 다시 시도하세요"
            } finally {
                _isCreatingCall.value = false
            }
        }
    }

    private fun fetchOfficeName(provinceId: String, cityId: String, officeId: String) {
        viewModelScope.launch {
            try {
                val document = firestore.collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId).get().await()
                _officeName.value = if (document.exists()) document.getString("name") else "사무실 없음"
            } catch (e: Exception) {
                _officeName.value = "로드 오류"
            }
        }
    }

    fun loadDataForUser(provinceId: String, cityId: String, officeId: String) {
        // 이미 같은 province/city/office를 로드 중이면 중복 실행하지 않음
        if (_provinceId.value == provinceId && _cityId.value == cityId && _officeId.value == officeId && callsListener != null) {
            return
        }

        stopListening()
        callsCache.clear()
        driverCache.clear()
        previousStatusMap.clear()
        _calls.value = emptyList()
        _drivers.value = emptyList()

        _provinceId.value = provinceId
        _cityId.value = cityId
        _officeId.value = officeId

        syncCallDetectorSettings(provinceId, cityId, officeId)

        startListening(provinceId, cityId, officeId)
        fetchOfficeName(provinceId, cityId, officeId)

        startCallDetectorIfEnabled()
    }

    private fun emitSharedCalls() {
        val cutoff = System.currentTimeMillis() - SHARED_CALL_MAX_AGE_MS
        _sharedCalls.value = sharedMap.values
            .filter { sc ->
                val ts = sc.timestamp?.toDate()?.time ?: 0L
                ts >= cutoff && sc.id !in dismissedIds
            }
            .sortedByDescending { it.timestamp?.seconds ?: 0 }
    }

    private fun startSharedCallTicker() {
        sharedCallTickerJob?.cancel()
        sharedCallTickerJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                emitSharedCalls()
            }
        }
    }

    fun dismissSharedCall(id: String) {
        if (dismissedIds.add(id)) {
            dismissedSharedPrefs.edit().putStringSet("ids", dismissedIds.toSet()).apply()
            emitSharedCalls()
        }
    }

    private fun stopListening() {
        callsListener?.remove()
        driversListener?.remove()
        officeStatusListener?.remove()
        sharedCallsListener?.remove()
        allSharedCallsListener?.remove()
        activeCallsListener?.remove()
        sharedCallTickerJob?.cancel()
        sharedCallTickerJob = null
        sharedMap.clear()
        // Repository 패턴으로 변경됨 - Firebase 리스너 제거
        callsListener = null
        driversListener = null
        officeStatusListener = null
        sharedCallsListener = null
        allSharedCallsListener = null
        activeCallsListener = null
        // Repository 패턴으로 변경됨
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()

        // Repository 정리
        pointRepository.stopSync()

        // Database 정리 (선택사항 - 앱 종료 시)
        // database.close() // 필요시에만 사용
    }

    fun shareCall(callInfo: CallInfo, departure: String, destination: String, fare: Int) {
        viewModelScope.launch {
            try {
                val province = _provinceId.value ?: return@launch
                val city = _cityId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                // 공유콜 재공유 차단 (공유의 공유 방지)
                if (callInfo.callType == "SHARED") {
                    Log.w(TAG, "⚠️ 공유콜은 다시 공유할 수 없습니다: ${callInfo.id}")
                    _snackbarMessage.value = "공유콜은 다시 공유할 수 없습니다"
                    return@launch
                }

                val docRef = firestore.collection("shared_calls").document()

                // 마감콜 여부 확인 (callType으로만 판단)
                val isClosingCall = callInfo.callType == "MISSED_AFTER_HOURS" ||
                                  callInfo.callType == "AFTER_HOURS_QUICK"

                val data = hashMapOf(
                    "status" to "OPEN",
                    "departure" to departure,
                    "destination" to destination,
                    "fare" to fare,
                    "sourceProvinceId" to province,
                    "sourceCityId" to city,
                    "sourceOfficeId" to office,
                    "targetProvinceId" to province,
                    "targetCityId" to city,
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "phoneNumber" to callInfo.phoneNumber,
                    "originalCallId" to callInfo.id,
                    "timestamp" to Timestamp.now(),
                    "callType" to if (isClosingCall) "마감콜" else null,
                    "expireAt" to Timestamp(java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
                )

                docRef.set(data).await()

                val origCallRef = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)
                    .collection("calls").document(callInfo.id)

                val callUpdates = mapOf(
                    "callType" to "SHARED",
                    "status" to "SHARED_WAITING",
                    "sourceSharedCallId" to docRef.id,
                    "departure_set" to departure,
                    "destination_set" to destination,
                    "fare_set" to fare,
                    "updatedAt" to Timestamp.now()
                )
                origCallRef.update(callUpdates).await()
            } catch (e: Exception) {
            }
        }
    }

    fun claimSharedCall(sharedCallId: String) {
        viewModelScope.launch {
            try {
                val province = _provinceId.value ?: return@launch
                val office = _officeId.value ?: return@launch
                firestore.runTransaction { tx ->
                    val docRef = firestore.collection("shared_calls").document(sharedCallId)
                    val snap = tx.get(docRef)
                    val status = snap.getString("status")
                    if (status != "OPEN") {
                        throw Exception("이미 수락된 콜입니다")
                    }
                    val city = _cityId.value ?: throw Exception("cityId가 없습니다")
                    tx.update(docRef, mapOf(
                        "status" to "CLAIMED",
                        "claimedOfficeId" to office,
                        "claimedAt" to Timestamp.now(),
                        "targetProvinceId" to province,
                        "targetCityId" to city
                    ))
                }.await()
            } catch (e: Exception) {
                _showSharedCallTakenDialog.value = true
            }
        }
    }

    fun claimSharedCallWithDetails(
        sharedCallId: String,
        departure: String,
        destination: String,
        fare: Int,
        driverId: String? = null
    ) {

        driverCache.values.forEach { driver ->
        }
        viewModelScope.launch {
            try {
                val province = _provinceId.value ?: return@launch
                val city = _cityId.value ?: return@launch
                val office = _officeId.value ?: return@launch
                firestore.runTransaction { tx ->
                    val docRef = firestore.collection("shared_calls").document(sharedCallId)
                    val snap = tx.get(docRef)
                    if (!snap.exists()) {
                        throw Exception("공유콜 문서가 존재하지 않습니다: $sharedCallId")
                    }
                    val status = snap.getString("status")
                    if (status != "OPEN") {
                        throw Exception("이미 수락된 콜입니다. 현재 상태: $status")
                    }
                    val updateMap = mutableMapOf<String, Any>(
                        "status" to "CLAIMED",
                        "claimedOfficeId" to office,
                        "claimedAt" to Timestamp.now(),
                        "departure" to departure,
                        "destination" to destination,
                        "fare" to fare,
                        "targetProvinceId" to province,
                        "targetCityId" to city
                    )
                    driverId?.let {
                        updateMap["claimedDriverId"] = it

                        val driver = driverCache[it]
                        driver?.authUid?.let { authUid ->
                            updateMap["claimedDriverAuthUid"] = authUid
                        }
                    }
                    tx.update(docRef, updateMap)
                }.await()
            } catch (e: Exception) {
                _showSharedCallTakenDialog.value = true
            }
        }
    }

    fun createTestPointsDocument() {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        Log.d(TAG, "초기 포인트 설정 시작: Province=$province, City=$city, Office=$office")

        viewModelScope.launch {
            try {
                val officeRef = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)

                // 1. 포인트 잔액 설정
                val pointsRef = officeRef.collection("points").document("points")
                val testPointsData = hashMapOf(
                    "balance" to 1000,
                    "updatedAt" to Timestamp.now()
                )
                Log.d(TAG, "포인트 문서 생성 시작: 1000P")
                pointsRef.set(testPointsData).await()
                Log.d(TAG, "포인트 문서 생성 완료: 1000P")

                // 2. 초기 충전 거래 내역 생성
                val transactionData = hashMapOf(
                    "type" to "CHARGE",
                    "amount" to 1000,
                    "description" to "초기 포인트 설정",
                    "timestamp" to Timestamp.now(),
                    "createdBy" to (auth.currentUser?.uid ?: "system")
                )
                Log.d(TAG, "거래 내역 생성 시작")
                officeRef.collection("point_transactions").add(transactionData).await()
                Log.d(TAG, "거래 내역 생성 완료")

                Log.d(TAG, "초기 포인트 설정 완료: 1000P + 거래 내역 생성")
            } catch (e: Exception) {
                Log.e(TAG, "초기 포인트 설정 실패", e)
            }
        }
    }

    fun reopenSharedCall(sharedCallId: String) {
        viewModelScope.launch {
            try {
                val sharedCallRef = firestore.collection("shared_calls").document(sharedCallId)

                firestore.runTransaction { transaction ->
                    val snap = transaction.get(sharedCallRef)
                    if (!snap.exists()) {
                        Log.w(TAG, "shared_calls 문서 없음: $sharedCallId")
                        return@runTransaction
                    }
                    transaction.update(sharedCallRef, mapOf(
                        "status" to "OPEN",
                        "cancelledAt" to null,
                        "cancelReason" to null,
                        "updatedAt" to Timestamp.now()
                    ))
                }.await()

            } catch (e: Exception) {
                Log.e(TAG, "reopenSharedCall 실패: $sharedCallId", e)
            }
        }
    }

    fun deleteSharedCall(sharedCallId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("shared_calls").document(sharedCallId).delete().await()
            } catch (e: Exception) {
            }
        }
    }

    fun showSharedCallNotificationFromId(sharedCallId: String) {
        Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] showSharedCallNotificationFromId() 시작 - sharedCallId: $sharedCallId")
        viewModelScope.launch {
            try {
                Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] Firestore에서 shared_calls 조회 시작")
                val sharedCallDoc = firestore.collection("shared_calls").document(sharedCallId).get().await()
                Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] 문서 존재 여부: ${sharedCallDoc.exists()}")

                if (sharedCallDoc.exists()) {
                    val sharedCallData = sharedCallDoc.toObject(com.designated.callmanager.data.SharedCallInfo::class.java)
                        ?.copy(id = sharedCallDoc.id)
                    Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] 파싱된 sharedCallData: $sharedCallData")

                    if (sharedCallData != null) {
                        Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] 팝업 표시 시작 - _showNewSharedCallPopup.value = true")

                        // FCM 알림으로부터 온 팝업임을 표시
                        _isFromFcmNotification.value = true

                        // UI 강제 업데이트를 위해 먼저 false로 설정
                        _showNewSharedCallPopup.value = false
                        _newSharedCallInfo.value = sharedCallData

                        // 짧은 지연 후 true로 설정하여 UI 갱신 보장
                        kotlinx.coroutines.delay(50)
                        _showNewSharedCallPopup.value = true

                        Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] 팝업 데이터 설정 완료 - FCM 플래그: ${_isFromFcmNotification.value}, showNewSharedCallPopup: ${_showNewSharedCallPopup.value}")
                    } else {
                        Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] sharedCallData가 null - 팝업 표시 불가")
                    }
                } else {
                    Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] 문서가 존재하지 않음 - sharedCallId: $sharedCallId")
                }
            } catch (e: Exception) {
                Log.d("DashboardViewModel", "🔍 [FCM_DEBUG] 예외 발생: ${e.message}")
            }
        }
    }

    fun showTripStartedPopup(driverName: String, driverPhone: String?, tripSummary: String, customerName: String) {
        _tripStartedInfo.value = Triple(driverName, driverPhone, tripSummary)
        _showTripStartedPopup.value = true
    }

    fun showTripCompletedPopup(driverName: String, customerName: String) {
        _tripCompletedInfo.value = Pair(driverName, customerName)
        _showTripCompletedPopup.value = true
    }

    fun showCancelledCallPopup(driverName: String, customerName: String) {
        _canceledCallInfo.value = Pair(driverName, customerName)
        _showCanceledCallPopup.value = true
    }

    fun showNotificationFailurePopup(
        callId: String,
        driverName: String,
        driverId: String,
        presenceStatus: String,
        message: String
    ) {
        _notificationFailureInfo.value = NotificationFailureInfo(
            callId = callId,
            driverName = driverName,
            driverId = driverId,
            presenceStatus = presenceStatus,
            message = message
        )
        _showNotificationFailurePopup.value = true
    }

    fun dismissNotificationFailurePopup() {
        _showNotificationFailurePopup.value = false
        _notificationFailureInfo.value = null
    }

    fun syncCallDetectorSettings(provinceId: String, cityId: String, officeId: String) {
        val prefs = getApplication<Application>().getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)

        prefs.edit().apply {
            putString("provinceId", provinceId)
            putString("cityId", cityId)
            putString("officeId", officeId)
            putString("deviceName", android.os.Build.MODEL)
            if (!prefs.contains("call_detection_enabled")) {
                putBoolean("call_detection_enabled", true)
            }
            apply()
        }

    }

    fun forceSyncCallDetectorSettings() {
        val currentProvinceId = _provinceId.value
        val currentCityId = _cityId.value
        val currentOfficeId = _officeId.value

        if (currentProvinceId != null && currentCityId != null && currentOfficeId != null) {
            syncCallDetectorSettings(currentProvinceId, currentCityId, currentOfficeId)
        } else {
        }
    }

    private fun startCallDetectorIfEnabled() {
        val prefs = getApplication<Application>().getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)
        val isCallDetectionEnabled = prefs.getBoolean("call_detection_enabled", false)

        if (isCallDetectionEnabled) {
            if (com.designated.callmanager.service.CallDetectorService.isServiceRunning()) {
                return
            }

            // 권한 확인
            val hasReadCallLog = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
            val hasReadPhoneState = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

            if (!hasReadCallLog || !hasReadPhoneState) {
                return
            }

            try {
                val intent = Intent(getApplication(), com.designated.callmanager.service.CallDetectorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplication<Application>().startForegroundService(intent)
                } else {
                    getApplication<Application>().startService(intent)
                }
            } catch (e: Exception) {
            }
        } else {
            try {
                val intent = Intent(getApplication(), com.designated.callmanager.service.CallDetectorService::class.java)
                getApplication<Application>().stopService(intent)
            } catch (e: Exception) {
            }
        }
    }

    fun dismissSharedCallTakenDialog() {
        _showSharedCallTakenDialog.value = false
    }

    // 전날 마감내역 조회 및 팝업 관리
    fun loadPreviousDayClosingData() {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                // 마감 시간은 SettlementViewModel에서 기록한 SharedPreferences에서 가져옴
                val closingTimePrefs = appContext.getSharedPreferences("closing_times", Context.MODE_PRIVATE)
                val popupPrefs = appContext.getSharedPreferences("closing_popups", Context.MODE_PRIVATE)

                // 마지막 마감 시간 가져오기
                val lastClosingTime = closingTimePrefs.getLong("last_closing_time_${province}_${city}_${office}", 0L)
                val lastPopupShownTime = popupPrefs.getLong("last_popup_shown_${province}_${office}", 0L)
                val currentTime = System.currentTimeMillis()

                // 마감 이후 첫 업무 시작인지 확인
                // 1. 마지막 마감 시간이 있고
                // 2. 마지막 팝업 표시 시간이 마지막 마감 시간보다 이전이면
                // 3. 현재 시간이 마감 시간 이후면 팝업 표시

                if (lastClosingTime == 0L) {
                    Log.d(TAG, "마감 기록이 없음 - 팝업 표시하지 않음")
                    return@launch
                }

                if (lastPopupShownTime >= lastClosingTime) {
                    Log.d(TAG, "이미 이번 마감 이후 팝업 표시됨 - 건너뛰기")
                    return@launch
                }

                // 어제 날짜 계산 (마감 날짜 기준)
                val calendar = java.util.Calendar.getInstance()
                calendar.timeInMillis = lastClosingTime
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                val closingDayStart = Timestamp(calendar.time)

                calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
                calendar.set(java.util.Calendar.MINUTE, 59)
                calendar.set(java.util.Calendar.SECOND, 59)
                calendar.set(java.util.Calendar.MILLISECOND, 999)
                val closingDayEnd = Timestamp(calendar.time)

                // 어제 마감콜들 조회 (callType이 마감콜 관련이거나 특정 조건을 만족하는 콜들)
                val callsQuery = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)
                    .collection("calls")
                    .whereGreaterThanOrEqualTo("timestamp", closingDayStart)
                    .whereLessThanOrEqualTo("timestamp", closingDayEnd)
                    .get()
                    .await()

                val closingCalls = mutableListOf<CallInfo>()

                for (doc in callsQuery.documents) {
                    val callInfo = parseCallDocument(doc)
                    if (callInfo != null) {
                        // 마감콜 판단 로직: callType으로만 판단
                        val isClosingCall = callInfo.callType == "MISSED_AFTER_HOURS" ||
                                          callInfo.callType == "AFTER_HOURS_QUICK"

                        if (isClosingCall) {
                            closingCalls.add(callInfo)
                        }
                    }
                }

                val completedCalls = closingCalls.filter { it.status == CallStatus.COMPLETED.firestoreValue }
                val uncompletedCalls = closingCalls.filter { it.status != CallStatus.COMPLETED.firestoreValue }

                val completedClosingCallList = completedCalls.map { call ->
                    CompletedClosingCall(
                        customerName = call.customerName?.takeIf { it.isNotBlank() } ?: "고객",
                        departure = call.departure_set?.takeIf { it.isNotBlank() }
                                   ?: call.departure?.takeIf { it.isNotBlank() }
                                   ?: "출발지 미설정",
                        destination = call.destination_set?.takeIf { it.isNotBlank() }
                                     ?: call.destination?.takeIf { it.isNotBlank() }
                                     ?: "도착지 미설정",
                        fare = call.fare_set ?: call.fare ?: 0L
                    )
                }

                val closingData = PreviousDayClosingData(
                    totalCount = closingCalls.size,
                    completedCount = completedCalls.size,
                    uncompletedCount = uncompletedCalls.size,
                    completedCalls = completedClosingCallList
                )

                // 마감콜이 있을 때만 팝업 표시
                if (closingData.totalCount > 0) {
                    _previousDayClosingData.value = closingData
                    _showPreviousDayClosingDialog.value = true

                    // 팝업 표시 시간을 기록하여 중복 방지
                    popupPrefs.edit()
                        .putLong("last_popup_shown_${province}_${office}", currentTime)
                        .apply()
                    Log.d(TAG, "전날 마감내역 팝업 표시 기록: last_popup_shown_${province}_${office} = $currentTime")
                }

            } catch (e: Exception) {
                Log.e(TAG, "전날 마감내역 조회 실패", e)
            }
        }
    }

    fun dismissPreviousDayClosingDialog() {
        _showPreviousDayClosingDialog.value = false
        _previousDayClosingData.value = null
    }

    // 마감정산 조회 (최근 30일)
    fun loadClosingSettlements() {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                val settlements = mutableListOf<ClosingSettlement>()
                val calendar = java.util.Calendar.getInstance()

                // 최근 30일간 조회
                for (i in 0 until 30) {
                    calendar.time = java.util.Date()
                    calendar.add(java.util.Calendar.DAY_OF_MONTH, -i)

                    val dateStart = calendar.clone() as java.util.Calendar
                    dateStart.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    dateStart.set(java.util.Calendar.MINUTE, 0)
                    dateStart.set(java.util.Calendar.SECOND, 0)
                    dateStart.set(java.util.Calendar.MILLISECOND, 0)

                    val dateEnd = calendar.clone() as java.util.Calendar
                    dateEnd.set(java.util.Calendar.HOUR_OF_DAY, 23)
                    dateEnd.set(java.util.Calendar.MINUTE, 59)
                    dateEnd.set(java.util.Calendar.SECOND, 59)
                    dateEnd.set(java.util.Calendar.MILLISECOND, 999)

                    val dateString = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(calendar.time)

                    // 해당 날짜의 마감콜들 조회
                    val callsQuery = firestore.collection("provinces").document(province)
                        .collection("cities").document(city)
                        .collection("offices").document(office)
                        .collection("calls")
                        .whereGreaterThanOrEqualTo("timestamp", Timestamp(dateStart.time))
                        .whereLessThanOrEqualTo("timestamp", Timestamp(dateEnd.time))
                        .get()
                        .await()

                    val dayClosingCalls = mutableListOf<CallInfo>()

                    for (doc in callsQuery.documents) {
                        val callInfo = parseCallDocument(doc)
                        if (callInfo != null) {
                            // 마감콜 판단 로직: callType으로만 판단
                            val isClosingCall = callInfo.callType == "MISSED_AFTER_HOURS" ||
                                              callInfo.callType == "AFTER_HOURS_QUICK"

                            if (isClosingCall) {
                                dayClosingCalls.add(callInfo)
                            }
                        }
                    }

                    if (dayClosingCalls.isNotEmpty()) {
                        val completedCalls = dayClosingCalls.filter { it.status == CallStatus.COMPLETED.firestoreValue }
                        val totalRevenue = completedCalls.sumOf { call ->
                            call.fare_set ?: call.fare ?: 0L
                        }

                        val completedClosingCallList = completedCalls.map { call ->
                            CompletedClosingCall(
                                customerName = call.customerName?.takeIf { it.isNotBlank() } ?: "고객",
                                departure = call.departure_set?.takeIf { it.isNotBlank() }
                                           ?: call.departure?.takeIf { it.isNotBlank() }
                                           ?: "출발지 미설정",
                                destination = call.destination_set?.takeIf { it.isNotBlank() }
                                             ?: call.destination?.takeIf { it.isNotBlank() }
                                             ?: "도착지 미설정",
                                fare = call.fare_set ?: call.fare ?: 0L
                            )
                        }

                        val settlement = ClosingSettlement(
                            date = dateString,
                            totalCalls = dayClosingCalls.size,
                            completedCalls = completedCalls.size,
                            totalRevenue = totalRevenue,
                            details = completedClosingCallList
                        )

                        settlements.add(settlement)
                    }
                }

                _closingSettlements.value = settlements

            } catch (e: Exception) {
                Log.e(TAG, "마감정산 조회 실패", e)
            }
        }
    }

    // 잔액 정합성 검증 (전체 거래내역 조회)
    private fun validateBalanceConsistency(recentTransactions: List<PointTransaction>) {
        viewModelScope.launch {
            try {
                val province = _provinceId.value ?: return@launch
                val city = _cityId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                // 전체 거래내역을 조회하여 정확한 합계 계산
                val officeRef = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)

                val allTransactions = officeRef.collection("point_transactions")
                    .get()
                    .await()

                val calculatedBalance = allTransactions.documents.sumOf { doc ->
                    doc.getLong("amount") ?: 0L
                }

                val storedBalance = _pointsInfo.value?.balance?.toLong() ?: 0L

                if (calculatedBalance != storedBalance) {
                    Log.w(TAG, "포인트 잔액 불일치 감지: 계산값=$calculatedBalance, 저장값=$storedBalance")
                    Log.w(TAG, "전체 거래 수: ${allTransactions.size()}, 화면 표시 거래 수: ${recentTransactions.size}")

                    // 정정은 하지 않고 로그만 남김 (자동 정정 비활성화)
                    // 실제 운영에서는 관리자가 수동으로 조정하도록 함
                } else {
                    Log.d(TAG, "포인트 잔액 정합성 확인: $calculatedBalance")
                }
            } catch (e: Exception) {
                Log.e(TAG, "잔액 정합성 검증 실패", e)
            }
        }
    }

    // 테스트용 포인트 거래 생성
    /**
     * Repository 패턴을 사용한 테스트 포인트 거래 생성
     */
    fun createTestPointTransaction(amount: Int, type: String, description: String) {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                // Repository를 통한 거래 추가
                val transaction = PointTransaction(
                    id = "", // Firestore에서 자동 생성
                    type = type,
                    amount = amount,
                    description = description,
                    timestamp = Timestamp.now(),
                    regionId = province, // PointTransaction 필드명은 regionId로 유지 (내부적으로 provinceId로 사용)
                    officeId = office,
                    relatedSharedCallId = null
                )

                pointRepository.addTransaction(province, city, office, transaction)

                Log.d(TAG, "테스트 거래 생성 완료: $amount, $type")
            } catch (e: Exception) {
                Log.e(TAG, "테스트 거래 생성 실패", e)
            }
        }
    }

    // 정정 거래 생성
    private fun createAdjustmentTransaction(amount: Long, description: String) {
        val province = _provinceId.value ?: return
        val city = _cityId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                val officeRef = firestore.collection("provinces").document(province)
                    .collection("cities").document(city)
                    .collection("offices").document(office)

                val transactionData = hashMapOf(
                    "type" to "ADJUSTMENT",
                    "amount" to amount.toInt(),
                    "description" to description,
                    "timestamp" to Timestamp.now(),
                    "createdBy" to "system"
                )

                officeRef.collection("point_transactions").add(transactionData).await()
                Log.d(TAG, "정정 거래 생성: $amount")
            } catch (e: Exception) {
                Log.e(TAG, "정정 거래 생성 실패", e)
            }
        }
    }

}
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

    private val _showSharedCallTakenDialog = MutableStateFlow(false)
    val showSharedCallTakenDialog: StateFlow<Boolean> = _showSharedCallTakenDialog.asStateFlow()

    private val _pointsInfo = MutableStateFlow<PointsInfo?>(null)
    val pointsInfo: StateFlow<PointsInfo?> = _pointsInfo.asStateFlow()

    private val _pointTransactions = MutableStateFlow<List<PointTransaction>>(emptyList())
    val pointTransactions: StateFlow<List<PointTransaction>> = _pointTransactions.asStateFlow()

    private val _callInfoForDialog = MutableStateFlow<CallInfo?>(null)
    val callInfoForDialog: StateFlow<CallInfo?> = _callInfoForDialog.asStateFlow()

    private val _showDriverLoginPopup = MutableStateFlow(false)
    val showDriverLoginPopup: StateFlow<Boolean> = _showDriverLoginPopup
    private val _loggedInDriverName = MutableStateFlow<String?>(null)
    val loggedInDriverName: StateFlow<String?> = _loggedInDriverName
    private val _showApprovalPopup = MutableStateFlow(false)
    val showApprovalPopup: StateFlow<Boolean> = _showApprovalPopup
    private val _driverForApproval = MutableStateFlow<DriverInfo?>(null)
    val driverForApproval: StateFlow<DriverInfo?> = _driverForApproval
    private val _approvalActionState = MutableStateFlow<DriverApprovalActionState>(DriverApprovalActionState.Idle)
    val approvalActionState: StateFlow<DriverApprovalActionState> = _approvalActionState
    private val _showDriverLogoutPopup = MutableStateFlow(false)
    val showDriverLogoutPopup: StateFlow<Boolean> = _showDriverLogoutPopup
    private val _loggedOutDriverName = MutableStateFlow<String?>(null)
    val loggedOutDriverName: StateFlow<String?> = _loggedOutDriverName
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

        val sharedMap = mutableMapOf<String, com.designated.callmanager.data.SharedCallInfo>()

        fun emitSharedCalls() {
            _sharedCalls.value = sharedMap.values.sortedByDescending { it.timestamp?.seconds ?: 0 }
        }

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

                        // 운행 완료 팝업
                        if (call.status == CallStatus.COMPLETED.firestoreValue &&
                            previousStatusMap[call.id] != CallStatus.COMPLETED.firestoreValue) {
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
        // 조건: 1시간 내 생성된 미완료 콜만 감시 (보통 0-2개)
        startActiveCallsListener(provinceId, cityId, officeId)
    }

    /**
     * 미완료 콜 실시간 리스너 (FCM 백업용)
     * 조건: 1시간 내 생성 + 미완료 상태 (OPEN, ASSIGNED, IN_PROGRESS)
     * 비용: 보통 0-2개 문서만 감시하므로 매우 저렴
     */
    private fun startActiveCallsListener(provinceId: String, cityId: String, officeId: String) {
        activeCallsListener?.remove()

        val oneHourAgo = com.google.firebase.Timestamp(
            java.util.Date(System.currentTimeMillis() - 60 * 60 * 1000)
        )

        activeCallsListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereIn("status", listOf("OPEN", "WAITING", "ASSIGNED", "IN_PROGRESS"))
            .whereGreaterThan("timestamp", oneHourAgo)
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
     * 기사 데이터 새로고침 (브로드캐스트 수신 시 호출)
     */
    fun refreshDriverData() {
        val provinceId = _provinceId.value ?: return
        val cityId = _cityId.value ?: return
        val officeId = _officeId.value ?: return

        viewModelScope.launch {
            try {
                Log.d(TAG, "📍 기사 데이터 새로고침 (브로드캐스트)")
                driverRepository.refreshData(provinceId, cityId, officeId)
                Log.d(TAG, "📍 기사 데이터 새로고침 완료")
            } catch (e: Exception) {
                Log.e(TAG, "기사 데이터 새로고침 실패", e)
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
        _calls.value = callsCache.values.sortedByDescending { it.timestampClient ?: it.timestamp.toDate().time }
    }

    private fun parseCallDocument(doc: com.google.firebase.firestore.DocumentSnapshot): CallInfo? {
        return try {
            doc.toObject(CallInfo::class.java)?.apply { id = doc.id }
        } catch (e: Exception) {
            null
        }
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
                    if (currentStatus != CallStatus.WAITING.firestoreValue) {
                        throw IllegalStateException("ALREADY_ASSIGNED")
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

                // 기사에게 FCM 알림 전송 (Cloud Function 호출)
                Log.d(TAG, "========== 기사 알림 함수 호출 시작 ==========")
                Log.d(TAG, "callId: ${callInfo.id}, driverAuthUid: $driverAuthUid")
                Log.d(TAG, "provinceId: ${_provinceId.value}, cityId: ${_cityId.value}, officeId: ${_officeId.value}")
                try {
                    val functions = Firebase.functions("asia-northeast3")
                    val data = hashMapOf(
                        "callId" to callInfo.id,
                        "driverAuthUid" to driverAuthUid,
                        "provinceId" to _provinceId.value,
                        "cityId" to _cityId.value,
                        "officeId" to _officeId.value,
                        "customerName" to (callInfo.customerName ?: ""),
                        "departure" to (callInfo.departure ?: "")
                    )
                    Log.d(TAG, "Cloud Function 호출 전: $data")
                    functions.getHttpsCallable("notifyDriverAssignment")
                        .call(data)
                        .addOnSuccessListener { result ->
                            Log.d(TAG, "✅ 기사 알림 전송 성공: ${result.getData()}")
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ 기사 알림 전송 실패: ${e.message}", e)
                        }
                    Log.d(TAG, "Cloud Function 호출 완료 (비동기)")
                } catch (e: Exception) {
                    Log.e(TAG, "❌❌ 기사 알림 함수 호출 예외: ${e.message}", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 배차 실패: ${e.message}", e)
                if (e.message?.contains("ALREADY_ASSIGNED") == true ||
                    e.cause?.message?.contains("ALREADY_ASSIGNED") == true) {
                    _snackbarMessage.value = "이미 다른 기사에게 배차된 콜입니다"
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
                val callRef = firestore.collection("provinces").document(_provinceId.value!!)
                    .collection("cities").document(_cityId.value!!)
                    .collection("offices").document(_officeId.value!!)
                    .collection("calls").document(callId)

                // 트랜잭션으로 취소 처리 (CROSS-06: 원자적 업데이트)
                val cancelResult = firestore.runTransaction { transaction ->
                    val callSnapshot = transaction.get(callRef)
                    val currentStatus = callSnapshot.getString("status")
                    if (currentStatus == CallStatus.CANCELED.firestoreValue || currentStatus == CallStatus.CANCELLED.firestoreValue) {
                        throw IllegalStateException("ALREADY_CANCELLED")
                    }
                    transaction.update(callRef, "status", CallStatus.CANCELED.firestoreValue)

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

                // 배정된 기사가 있으면 상태 복구 + FCM 전송
                if (assignedDriverAuthUid != null) {
                    // 기사 상태를 WAITING으로 복구
                    val driversQuery = firestore.collection("provinces").document(_provinceId.value!!)
                        .collection("cities").document(_cityId.value!!)
                        .collection("offices").document(_officeId.value!!)
                        .collection("designated_drivers")
                        .whereEqualTo("authUid", assignedDriverAuthUid)
                        .limit(1)
                        .get()
                        .await()

                    if (!driversQuery.isEmpty) {
                        val driverDoc = driversQuery.documents[0]
                        driverDoc.reference.update("status", "WAITING").await()
                    }

                    // 기사에게 콜 취소 FCM 전송
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
                val callRef = firestore.collection("provinces").document(_provinceId.value!!)
                    .collection("cities").document(_cityId.value!!)
                    .collection("offices").document(_officeId.value!!)
                    .collection("calls").document(callId)

                val callSnapshot = callRef.get().await()
                val assignedDriverAuthUid = callSnapshot.getString("assignedDriverId")

                callRef.update("status", CallStatus.COMPLETED.firestoreValue).await()

                if (!assignedDriverAuthUid.isNullOrBlank()) {
                    val driversQuery = firestore.collection("provinces").document(_provinceId.value!!)
                        .collection("cities").document(_cityId.value!!)
                        .collection("offices").document(_officeId.value!!)
                        .collection("designated_drivers")
                        .whereEqualTo("authUid", assignedDriverAuthUid)
                        .limit(1)
                        .get()
                        .await()

                    if (!driversQuery.isEmpty) {
                        val driverDoc = driversQuery.documents[0]
                        driverDoc.reference.update("status", "WAITING").await()
                    } else {
                    }
                }

            } catch (e: Exception) {
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

    fun dismissDriverLoginPopup() {
        _showDriverLoginPopup.value = false
        _loggedInDriverName.value = null
    }

    fun dismissDriverLogoutPopup() {
        _showDriverLogoutPopup.value = false
        _loggedOutDriverName.value = null
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
     * '+' 아이콘 클릭 시 호출: 기본값으로 WAITING 상태의 콜 문서를 먼저 생성하여
     * 기존 대기 호출 흐름(NewCallPopup)과 동일하게 처리되도록 한다.
     */
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
                    "createdBy" to (auth.currentUser?.uid ?: "")
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
                    "departure_set" to null,
                    "destination_set" to null,
                    "fare_set" to null
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
                _internalCallForAssignment.value = createdCall

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
                    "fare_set" to if (fare > 0) fare else null
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

    private fun stopListening() {
        callsListener?.remove()
        driversListener?.remove()
        officeStatusListener?.remove()
        sharedCallsListener?.remove()
        allSharedCallsListener?.remove()
        activeCallsListener?.remove()
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
                val docRef = firestore.collection("shared_calls").document()

                // 마감콜 여부 확인 (원본 callInfo의 callType 또는 출발지/도착지/요금이 모두 비어있는 경우)
                val isClosingCall = callInfo.callType == "MISSED_AFTER_HOURS" ||
                                  callInfo.callType == "AFTER_HOURS_QUICK" ||
                                  (departure.isBlank() && destination.isBlank() && fare == 0)

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
                    "callType" to if (isClosingCall) "마감콜" else null
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

                sharedCallRef.update(
                    mapOf(
                        "status" to "OPEN",
                        "cancelledAt" to null,
                        "cancelReason" to null,
                        "updatedAt" to Timestamp.now()
                    )
                ).await()

            } catch (e: Exception) {
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
                val lastClosingTime = closingTimePrefs.getLong("last_closing_time_${province}_${office}", 0L)
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
                        // 마감콜 판단 로직: callType이 마감콜 관련이거나, 출발지/도착지/요금이 모두 없는 경우
                        val isClosingCall = callInfo.callType == "MISSED_AFTER_HOURS" ||
                                          callInfo.callType == "AFTER_HOURS_QUICK" ||
                                          (callInfo.departure_set.isNullOrBlank() &&
                                           callInfo.destination_set.isNullOrBlank() &&
                                           (callInfo.fare_set == null || callInfo.fare_set == 0L) &&
                                           callInfo.departure.isNullOrBlank() &&
                                           callInfo.destination.isNullOrBlank() &&
                                           (callInfo.fare == null || callInfo.fare == 0L))

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
                            val isClosingCall = callInfo.callType == "MISSED_AFTER_HOURS" ||
                                              callInfo.callType == "AFTER_HOURS_QUICK" ||
                                              (callInfo.departure_set.isNullOrBlank() &&
                                               callInfo.destination_set.isNullOrBlank() &&
                                               (callInfo.fare_set == null || callInfo.fare_set == 0L) &&
                                               callInfo.departure.isNullOrBlank() &&
                                               callInfo.destination.isNullOrBlank() &&
                                               (callInfo.fare == null || callInfo.fare == 0L))

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
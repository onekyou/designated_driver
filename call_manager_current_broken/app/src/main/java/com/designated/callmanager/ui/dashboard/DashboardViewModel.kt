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
    private val app = getApplication<CallManagerApplication>()
    private val callRepository by lazy { app.callRepository }
    private val driverRepository by lazy { app.driverRepository }
    private val pointRepository by lazy { app.pointRepository }

    private val _regionId = MutableStateFlow<String?>(null)
    val regionId: StateFlow<String?> = _regionId.asStateFlow()

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
    private val _newCallInfo = MutableStateFlow<CallInfo?>(null)
    val newCallInfo: StateFlow<CallInfo?> = _newCallInfo
    private val _showNewSharedCallPopup = MutableStateFlow(false)
    val showNewSharedCallPopup: StateFlow<Boolean> = _showNewSharedCallPopup
    private val _newSharedCallInfo = MutableStateFlow<com.designated.callmanager.data.SharedCallInfo?>(null)
    val newSharedCallInfo: StateFlow<com.designated.callmanager.data.SharedCallInfo?> = _newSharedCallInfo

    // FCM 알림 클릭으로 인한 팝업인지 구분하는 플래그
    private val _isFromFcmNotification = MutableStateFlow(false)
    val isFromFcmNotification: StateFlow<Boolean> = _isFromFcmNotification

    // 전날 마감내역 관련
    private val _showPreviousDayClosingDialog = MutableStateFlow(false)
    val showPreviousDayClosingDialog: StateFlow<Boolean> = _showPreviousDayClosingDialog.asStateFlow()

    private val _previousDayClosingData = MutableStateFlow<PreviousDayClosingData?>(null)
    val previousDayClosingData: StateFlow<PreviousDayClosingData?> = _previousDayClosingData.asStateFlow()

    // 마감정산 관련
    private val _closingSettlements = MutableStateFlow<List<ClosingSettlement>>(emptyList())
    val closingSettlements: StateFlow<List<ClosingSettlement>> = _closingSettlements.asStateFlow()

    // ✅ Repository 패턴으로 변경 - 필요한 리스너만 유지
    private var officeStatusListener: ListenerRegistration? = null
    private var sharedCallsListener: ListenerRegistration? = null
    private var allSharedCallsListener: ListenerRegistration? = null

    // ✅ 팝업 로직용으로만 사용 (캐시 제거됨)
    private val previousStatusMap = mutableMapOf<String, String?>()
    private var lastCompletedCallId: String? = null
    private var lastCanceledCallId: String? = null

    init {
        fetchCurrentUserAndStartListening()
    }

    fun resetApprovalActionState() {
        _approvalActionState.value = DriverApprovalActionState.Idle
    }

    private fun fetchCurrentUserAndStartListening() {
        val user = auth.currentUser
        if (user != null) {
            val storedRegionId = sharedPreferences.getString("regionId", null)
            val storedOfficeId = sharedPreferences.getString("officeId", null)
            _regionId.value = storedRegionId
            _officeId.value = storedOfficeId
            if (!storedRegionId.isNullOrBlank() && !storedOfficeId.isNullOrBlank()) {
                startListening(storedRegionId, storedOfficeId)
                fetchOfficeName(storedRegionId, storedOfficeId)
            }
        }
    }

    private fun startListening(regionId: String, officeId: String) {
        stopListening()
        val officeRef = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)

        // ✅ Repository Flow 구독으로 대체 (리스너 제거)
        viewModelScope.launch {
            callRepository.getCallsFlow(regionId, officeId)
                .collect { calls ->
                    Log.d(TAG, "✅ 로컬 DB에서 콜 목록 업데이트: ${calls.size}개")
                    _calls.value = calls

                    // 팝업 로직 (기존 로직 유지)
                    calls.forEach { callInfo ->
                        // 새 콜 팝업
                        if (callInfo.status == CallStatus.WAITING.firestoreValue &&
                            callInfo.fromCallDetector != true &&
                            callInfo.callType != "SHARED") {
                            val prefs = appContext.getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
                            val popupId = "NEW_CALL_${callInfo.id}"
                            if (!prefs.getBoolean(popupId, false)) {
                                _newCallInfo.value = callInfo
                                _showNewCallPopup.value = true
                                prefs.edit().putBoolean(popupId, true).apply()
                            }
                        }

                        // 운행 시작 팝업
                        if (callInfo.status == CallStatus.IN_PROGRESS.firestoreValue &&
                            previousStatusMap[callInfo.id] != CallStatus.IN_PROGRESS.firestoreValue) {
                            val tripSummary = buildString {
                                append("출발: ${callInfo.departure_set ?: callInfo.customerAddress ?: "정보없음"}")
                                append(", 도착: ${callInfo.destination_set ?: "정보없음"}")
                                if (!callInfo.waypoints_set.isNullOrBlank()) {
                                    append(", 경유: ${callInfo.waypoints_set}")
                                }
                                append(", 요금: ${callInfo.fare_set ?: callInfo.fare ?: 0}원")
                            }
                            val driverDisplayName = if (callInfo.callType == "SHARED") {
                                "공유 기사님"
                            } else {
                                callInfo.assignedDriverName ?: "기사"
                            }
                            _tripStartedInfo.value = Triple(
                                driverDisplayName,
                                callInfo.assignedDriverPhone,
                                tripSummary
                            )
                            _showTripStartedPopup.value = true
                        }

                        // 운행 완료 팝업
                        if (callInfo.status == CallStatus.COMPLETED.firestoreValue &&
                            previousStatusMap[callInfo.id] != CallStatus.COMPLETED.firestoreValue) {
                            val prefs = appContext.getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
                            val popupId = "TRIP_COMPLETED_${callInfo.id}"
                            if (!prefs.getBoolean(popupId, false)) {
                                val driverName = if (callInfo.callType == "SHARED") {
                                    "공유 기사님"
                                } else {
                                    callInfo.assignedDriverName ?: "기사"
                                }
                                val customerName = callInfo.customerName?.takeIf { it.isNotBlank() } ?: "고객"
                                _tripCompletedInfo.value = Pair(driverName, customerName)
                                _showTripCompletedPopup.value = true
                                prefs.edit().putBoolean(popupId, true).apply()
                            }
                        }

                        previousStatusMap[callInfo.id] = callInfo.status
                    }
                }
        }

        viewModelScope.launch {
            driverRepository.getDriversFlow(regionId, officeId)
                .collect { drivers ->
                    Log.d(TAG, "✅ 로컬 DB에서 기사 목록 업데이트: ${drivers.size}명")
                    _drivers.value = drivers.sortedBy { it.name }
                }
        }

        officeStatusListener = officeRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val status = snapshot.getString("status") ?: ""
                _officeStatus.value = status

                // SharedPreferences에 사무실 상태 캐시 저장
                val prefs = getApplication<Application>().getSharedPreferences("office_status_cache", Context.MODE_PRIVATE)
                prefs.edit().putString("current_office_status", status).apply()
            }
        }

        val sharedMap = mutableMapOf<String, com.designated.callmanager.data.SharedCallInfo>()

        fun emitSharedCalls() {
            _sharedCalls.value = sharedMap.values.sortedByDescending { it.timestamp?.seconds ?: 0 }
        }

        val listenerA = firestore.collection("shared_calls")
            .whereEqualTo("sourceRegionId", regionId)
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
            .whereEqualTo("sourceRegionId", regionId)
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
        setupPointsObservers(regionId, officeId)
    }

    /**
     * Repository 패턴을 사용한 포인트 데이터 구독 설정
     */
    private fun setupPointsObservers(regionId: String, officeId: String) {
        Log.d(TAG, "포인트 옵저버 설정: $regionId/$officeId")

        // 1. 포인트 잔액 구독
        viewModelScope.launch {
            pointRepository.getPointsInfoFlow(regionId, officeId)
                .collect { pointsInfo ->
                    _pointsInfo.value = pointsInfo ?: PointsInfo(0, null)
                    Log.d(TAG, "포인트 잔액 업데이트: ${pointsInfo?.balance ?: 0}")
                }
        }

        // 2. 거래 내역 구독
        viewModelScope.launch {
            pointRepository.getTransactionsFlow(regionId, officeId)
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
                pointRepository.refreshData(regionId, officeId)
                Log.d(TAG, "포인트 데이터 새로고침 완료")
            } catch (e: Exception) {
                Log.e(TAG, "포인트 데이터 새로고침 실패", e)
            }
        }
    }

    // ❌ 제거됨: Repository 패턴으로 캐시 불필요
    // private fun updateCallsFromCache() { ... }

    private fun parseCallDocument(doc: com.google.firebase.firestore.DocumentSnapshot): CallInfo? {
        return try {
            doc.toObject(CallInfo::class.java)?.apply { id = doc.id }
        } catch (e: Exception) {
            null
        }
    }

    fun assignCallToDriver(callInfo: CallInfo, driverId: String) {
        if (_regionId.value == null || _officeId.value == null) {
            return
        }

        viewModelScope.launch {
            try {
                // ✅ 기사 정보 조회
                val driverInfo = driverRepository.getDriverById(driverId)
                if (driverInfo == null) {
                    Log.e(TAG, "기사를 찾을 수 없음: $driverId")
                    return@launch
                }

                val driverAuthUid = driverInfo.authUid
                if (driverAuthUid.isNullOrBlank()) {
                    Log.e(TAG, "기사 authUid 없음: $driverId")
                    return@launch
                }

                // ✅ Repository를 통한 낙관적 업데이트 (로컬 즉시 + Firebase 백그라운드)
                callRepository.assignCall(
                    callId = callInfo.id,
                    driverId = driverAuthUid,
                    driverName = driverInfo.name,
                    driverPhone = driverInfo.phoneNumber,
                    newStatus = CallStatus.ASSIGNED.firestoreValue
                )

                driverRepository.updateDriverStatus(driverId, DriverStatus.ASSIGNED.value)

                Log.d(TAG, "✅ 배차 완료 (낙관적 업데이트): ${callInfo.id} -> ${driverInfo.name}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 배차 실패", e)
            }
        }
    }

    /**
     * 콜 상태 업데이트 (Repository 패턴)
     * FCM 알림 + 로컬 DB 동기화를 통한 상태 업데이트
     */
    fun updateCallStatus(callId: String, newStatus: CallStatus) {
        viewModelScope.launch {
            try {
                // ✅ Repository를 통한 낙관적 업데이트
                callRepository.updateCallStatus(callId, newStatus.firestoreValue)
                Log.d(TAG, "✅ 콜 상태 업데이트 (FCM 알림 발송됨): $callId -> ${newStatus.firestoreValue}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 콜 상태 업데이트 실패", e)
            }
        }
    }

    /**
     * 콜 취소 (Repository 패턴)
     * FCM 알림 + 로컬 DB 동기화를 통한 상태 업데이트
     */
    fun cancelCall(callId: String) {
        viewModelScope.launch {
            try {
                // ✅ Repository를 통한 낙관적 업데이트
                callRepository.cancelCall(callId)
                Log.d(TAG, "✅ 콜 취소 (FCM 알림 발송됨): $callId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 콜 취소 실패", e)
            }
        }
    }

    /**
     * 콜 완료 처리 (Repository 패턴)
     * FCM 알림 + 로컬 DB 동기화를 통한 상태 업데이트
     */
    fun completeCall(callId: String) {
        viewModelScope.launch {
            try {
                // 1. 배차된 기사의 authUid 조회 (로컬 DB)
                val call = _calls.value.find { it.id == callId }
                val assignedDriverAuthUid = call?.assignedDriverId

                // 2. ✅ Repository를 통한 낙관적 업데이트
                callRepository.completeCall(callId, assignedDriverAuthUid)
                Log.d(TAG, "✅ 콜 완료 처리 (FCM 알림 발송됨): $callId")

                // 3. 기사 상태를 WAITING으로 변경
                if (!assignedDriverAuthUid.isNullOrBlank()) {
                    driverRepository.updateDriverStatusByAuthUid(assignedDriverAuthUid, "WAITING")
                    Log.d(TAG, "✅ 기사 상태 WAITING으로 변경 (FCM 알림 발송됨): $assignedDriverAuthUid")
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ 콜 완료 처리 실패", e)
            }
        }
    }

    fun showCallDetails(callInfo: CallInfo) {
        _callInfoForDialog.value = callInfo
    }

    private fun getOfficeRef() = regionId.value?.let { rId ->
        officeId.value?.let { oId ->
            firestore.collection("regions").document(rId).collection("offices").document(oId)
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
                val callFromCache = _calls.value.find { it.id == callId }
                if (callFromCache != null) {
                    _newCallInfo.value = callFromCache
                    _showNewCallPopup.value = true
                    return@launch
                }

                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                val callDocument = firestore.collection("regions").document(region)
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
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                val callDocument = firestore.collection("regions").document(region)
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

    /**
     * 기사 승인 (Repository 패턴)
     * FCM 알림 + 로컬 DB 동기화를 통한 상태 업데이트
     */
    fun approveDriver(driverId: String) {
        viewModelScope.launch {
            try {
                // ✅ Repository를 통한 낙관적 업데이트
                driverRepository.approveDriver(driverId)
                Log.d(TAG, "✅ 기사 승인 (FCM 알림 발송됨): $driverId")
                dismissApprovalPopup()
            } catch (e: Exception) {
                Log.e(TAG, "❌ 기사 승인 실패", e)
            }
        }
    }

    /**
     * 기사 거절 (Repository 패턴)
     * FCM 알림 + 로컬 DB 동기화를 통한 상태 업데이트
     */
    fun rejectDriver(driverId: String) {
        viewModelScope.launch {
            try {
                // ✅ Repository를 통한 낙관적 업데이트
                driverRepository.rejectDriver(driverId)
                Log.d(TAG, "✅ 기사 거절 (FCM 알림 발송됨): $driverId")
                dismissApprovalPopup()
            } catch (e: Exception) {
                Log.e(TAG, "❌ 기사 거절 실패", e)
            }
        }
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
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                val officeRef = firestore.collection("regions").document(region)
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
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                val nowTs = Timestamp.now()
                val newCall = CallInfo(
                    id = "",  // Repository가 임시 ID 생성
                    phoneNumber = "",
                    customerAddress = "",
                    customerName = "",
                    timestamp = nowTs,
                    timestampClient = System.currentTimeMillis(),
                    status = CallStatus.WAITING.firestoreValue,
                    regionId = region,
                    officeId = office
                )

                // ✅ Repository를 통한 낙관적 업데이트 (로컬 즉시 저장 + Firebase 백그라운드)
                val tempId = callRepository.createCall(newCall)
                Log.d(TAG, "✅ 콜 생성 완료 (낙관적 업데이트): $tempId")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 콜 생성 실패", e)
            }
        }
    }

    /**
     * 콜 삭제 (Repository 패턴)
     * FCM 알림 + 로컬 DB 동기화를 통한 상태 업데이트
     */
    fun deleteCall(callId: String) {
        viewModelScope.launch {
            try {
                // ✅ Repository를 통한 낙관적 업데이트
                callRepository.deleteCall(callId)

                previousStatusMap.remove(callId)

                if (_newCallInfo.value?.id == callId) {
                    dismissNewCallPopup()
                }

                Log.d(TAG, "✅ 콜 삭제 (FCM 알림 발송됨): $callId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 콜 삭제 실패", e)
            }
        }
    }

    private fun fetchOfficeName(regionId: String, officeId: String) {
        viewModelScope.launch {
            try {
                val document = firestore.collection("regions").document(regionId)
                    .collection("offices").document(officeId).get().await()
                _officeName.value = if (document.exists()) document.getString("name") else "사무실 없음"
            } catch (e: Exception) {
                _officeName.value = "로드 오류"
            }
        }
    }

    fun loadDataForUser(regionId: String, officeId: String) {
        // ✅ Repository 패턴으로 변경 - 간단한 중복 체크
        if (_regionId.value == regionId && _officeId.value == officeId) {
            return
        }

        stopListening()
        previousStatusMap.clear()  // 팝업 로직용
        _calls.value = emptyList()
        _drivers.value = emptyList()

        _regionId.value = regionId
        _officeId.value = officeId

        syncCallDetectorSettings(regionId, officeId)

        startListening(regionId, officeId)
        fetchOfficeName(regionId, officeId)

        startCallDetectorIfEnabled()
    }

    private fun stopListening() {
        // ✅ Repository 패턴으로 변경 - 필요한 리스너만 제거
        officeStatusListener?.remove()
        sharedCallsListener?.remove()
        allSharedCallsListener?.remove()

        officeStatusListener = null
        sharedCallsListener = null
        allSharedCallsListener = null
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
                val region = _regionId.value ?: return@launch
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
                    "sourceRegionId" to region,
                    "sourceOfficeId" to office,
                    "targetRegionId" to region,
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "phoneNumber" to callInfo.phoneNumber,
                    "originalCallId" to callInfo.id,
                    "timestamp" to Timestamp.now(),
                    "callType" to if (isClosingCall) "마감콜" else null
                )

                docRef.set(data).await()

                val origCallRef = firestore.collection("regions").document(region)
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
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch
                firestore.runTransaction { tx ->
                    val docRef = firestore.collection("shared_calls").document(sharedCallId)
                    val snap = tx.get(docRef)
                    val status = snap.getString("status")
                    if (status != "OPEN") {
                        throw Exception("이미 수락된 콜입니다")
                    }
                    tx.update(docRef, mapOf(
                        "status" to "CLAIMED",
                        "claimedOfficeId" to office,
                        "claimedAt" to Timestamp.now(),
                        "targetRegionId" to region
                    ))
                }.await()
            } catch (e: Exception) {
                _showSharedCallTakenDialog.value = true
            }
        }
    }

    /**
     * 공유콜 수락 (Repository 패턴)
     * FCM 알림 + 로컬 DB 동기화를 통한 상태 업데이트
     */
    fun claimSharedCallWithDetails(
        sharedCallId: String,
        departure: String,
        destination: String,
        fare: Int,
        driverId: String? = null
    ) {
        viewModelScope.launch {
            try {
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                // ✅ driverCache 대신 로컬 DB에서 기사 정보 조회
                val driverAuthUid = if (driverId != null) {
                    driverRepository.getDriverById(driverId)?.authUid
                } else null

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
                        "targetRegionId" to region
                    )
                    driverId?.let {
                        updateMap["claimedDriverId"] = it

                        driverAuthUid?.let { authUid ->
                            updateMap["claimedDriverAuthUid"] = authUid
                        }
                    }
                    tx.update(docRef, updateMap)
                }.await()

                Log.d(TAG, "✅ 공유콜 수락 완료: $sharedCallId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 공유콜 수락 실패", e)
                _showSharedCallTakenDialog.value = true
            }
        }
    }

    fun createTestPointsDocument() {
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return

        Log.d(TAG, "초기 포인트 설정 시작: Region=$region, Office=$office")

        viewModelScope.launch {
            try {
                val officeRef = firestore.collection("regions").document(region)
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

    fun syncCallDetectorSettings(regionId: String, officeId: String) {
        val prefs = getApplication<Application>().getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)

        prefs.edit().apply {
            putString("regionId", regionId)
            putString("officeId", officeId)
            putString("deviceName", android.os.Build.MODEL)
            if (!prefs.contains("call_detection_enabled")) {
                putBoolean("call_detection_enabled", true)
            }
            apply()
        }

    }

    fun forceSyncCallDetectorSettings() {
        val currentRegionId = _regionId.value
        val currentOfficeId = _officeId.value

        if (currentRegionId != null && currentOfficeId != null) {
            syncCallDetectorSettings(currentRegionId, currentOfficeId)
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
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                // 마감 시간은 SettlementViewModel에서 기록한 SharedPreferences에서 가져옴
                val closingTimePrefs = appContext.getSharedPreferences("closing_times", Context.MODE_PRIVATE)
                val popupPrefs = appContext.getSharedPreferences("closing_popups", Context.MODE_PRIVATE)

                // 마지막 마감 시간 가져오기
                val lastClosingTime = closingTimePrefs.getLong("last_closing_time_${region}_${office}", 0L)
                val lastPopupShownTime = popupPrefs.getLong("last_popup_shown_${region}_${office}", 0L)
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
                val callsQuery = firestore.collection("regions").document(region)
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
                        .putLong("last_popup_shown_${region}_${office}", currentTime)
                        .apply()
                    Log.d(TAG, "전날 마감내역 팝업 표시 기록: last_popup_shown_${region}_${office} = $currentTime")
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
        val region = _regionId.value ?: return
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
                    val callsQuery = firestore.collection("regions").document(region)
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
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                // 전체 거래내역을 조회하여 정확한 합계 계산
                val officeRef = firestore.collection("regions").document(region)
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
        val region = _regionId.value ?: return
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
                    regionId = region,
                    officeId = office,
                    relatedSharedCallId = null
                )

                pointRepository.addTransaction(region, office, transaction)

                Log.d(TAG, "테스트 거래 생성 완료: $amount, $type")
            } catch (e: Exception) {
                Log.e(TAG, "테스트 거래 생성 실패", e)
            }
        }
    }

    // 정정 거래 생성
    private fun createAdjustmentTransaction(amount: Long, description: String) {
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                val officeRef = firestore.collection("regions").document(region)
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
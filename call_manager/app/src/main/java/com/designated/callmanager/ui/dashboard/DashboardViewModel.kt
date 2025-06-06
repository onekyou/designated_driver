package com.designated.callmanager.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.designated.callmanager.service.CallManagerService
import com.designated.callmanager.R
import com.designated.callmanager.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.DriverInfo
import com.google.firebase.Timestamp
import com.designated.callmanager.data.CallStatus

// --- 승인 팝업 상태 추가 ---
sealed class DriverApprovalActionState { // 승인/거절 액션 상태
    object Idle : DriverApprovalActionState()
    object Loading : DriverApprovalActionState()
    data class Success(val driverId: String, val action: String) : DriverApprovalActionState() // "approved" or "rejected"
    data class Error(val message: String) : DriverApprovalActionState()
}
// --- ---

class DashboardViewModel(application: android.app.Application) : AndroidViewModel(application) {

    // --- 상수 정의 (Companion object로 이동) ---
    companion object {
        private const val TAG = "DashboardViewModel"
        private const val STATUS_OPERATING = "operating"
        private const val STATUS_CLOSED_SHARING = "closed_sharing"
        private const val STATUS_LOADING = "loading"
        // Foreground Service 관련 상수도 여기로 이동 가능 (선택 사항)
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
    // --- ---

    private val auth: FirebaseAuth = Firebase.auth
    private val firestore = FirebaseFirestore.getInstance()
    private val sharedPreferences = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE) // SharedPreferences 인스턴스 추가

    private val _userEmail = MutableStateFlow<String?>("로딩 중...")
    val userEmail: StateFlow<String?> = _userEmail

    // --- regionId와 officeId 상태 추가 ---
    private val _regionId = MutableStateFlow<String?>(null) // Nullable로 변경, 초기값 null
    val regionId: StateFlow<String?> = _regionId.asStateFlow()

    private val _officeId = MutableStateFlow<String?>(null) // Nullable로 변경, 초기값 null
    val officeId: StateFlow<String?> = _officeId.asStateFlow()

    // --- 사무실 이름 상태 추가 ---
    private val _officeName = MutableStateFlow<String?>("사무실 정보 로딩 중...")
    val officeName: StateFlow<String?> = _officeName.asStateFlow()
    // --- ---

    // --- 사무실 운영 상태 추가 (위치 이동) ---
    private val _officeStatus = MutableStateFlow<String>(STATUS_LOADING) // 초기 상태: 로딩 중
    val officeStatus: StateFlow<String> = _officeStatus.asStateFlow()
    // --- ---

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls

    private val _drivers = MutableStateFlow<List<DriverInfo>>(emptyList()) // Designated Drivers
    val drivers: StateFlow<List<DriverInfo>> = _drivers

    // TODO: Pickup Drivers 상태 추가 필요 시
    // private val _pickupDrivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    // val pickupDrivers: StateFlow<List<DriverInfo>> = _pickupDrivers

    private val _isConnected = MutableStateFlow(true)
    val isConnected: StateFlow<Boolean> = _isConnected

    // State for triggering the call info dialog
    private val _callToShowDialog = MutableStateFlow<String?>(null)
    val callToShowDialog: StateFlow<String?> = _callToShowDialog

    private val _callInfoForDialog = MutableStateFlow<CallInfo?>(null)
    val callInfoForDialog: StateFlow<CallInfo?> = _callInfoForDialog.asStateFlow()

    private var directFetchJob: Job? = null // Job to manage direct fetch coroutine

    // --- 기존 로그인 알림 팝업 상태 ---
    private val _showDriverLoginPopup = MutableStateFlow(false)
    val showDriverLoginPopup: StateFlow<Boolean> = _showDriverLoginPopup

    private val _loggedInDriverName = MutableStateFlow<String?>(null)
    val loggedInDriverName: StateFlow<String?> = _loggedInDriverName
    // --- ---

    // --- 신규 기사 승인 팝업 상태 ---
    private val _showApprovalPopup = MutableStateFlow(false)
    val showApprovalPopup: StateFlow<Boolean> = _showApprovalPopup

    private val _driverForApproval = MutableStateFlow<DriverInfo?>(null)
    val driverForApproval: StateFlow<DriverInfo?> = _driverForApproval

    private val _approvalActionState = MutableStateFlow<DriverApprovalActionState>(DriverApprovalActionState.Idle)
    val approvalActionState: StateFlow<DriverApprovalActionState> = _approvalActionState // 승인/거절 처리 결과 상태
    // --- ---

    // --- 기사 퇴근 알림 팝업 상태 추가 ---
    private val _showDriverLogoutPopup = MutableStateFlow(false)
    val showDriverLogoutPopup: StateFlow<Boolean> = _showDriverLogoutPopup

    private val _loggedOutDriverName = MutableStateFlow<String?>(null)
    val loggedOutDriverName: StateFlow<String?> = _loggedOutDriverName
    // --- ---

    // --- 운행 시작 알림 팝업 상태 추가 ---
    private val _showTripStartedPopup = MutableStateFlow(false)
    val showTripStartedPopup: StateFlow<Boolean> = _showTripStartedPopup

    // 운행 시작 정보를 담는 타입을 Triple로 변경 (driverName, driverPhone, tripSummary)
    private val _tripStartedInfo = MutableStateFlow<Triple<String, String?, String>?>(null)
    val tripStartedInfo: StateFlow<Triple<String, String?, String>?> = _tripStartedInfo
    // --- ---

    // --- 운행 완료 알림 팝업 상태 추가 ---
    private val _showTripCompletedPopup = MutableStateFlow(false)
    val showTripCompletedPopup: StateFlow<Boolean> = _showTripCompletedPopup

    private val _tripCompletedInfo = MutableStateFlow<Pair<String, String>?>(null) // Pair(driverName, customerName)
    val tripCompletedInfo: StateFlow<Pair<String, String>?> = _tripCompletedInfo
    // --- ---

    // --- 공유 콜 목록 상태 추가 ---
    private val _sharedCalls = MutableStateFlow<List<CallInfo>>(emptyList())
    val sharedCalls: StateFlow<List<CallInfo>> = _sharedCalls.asStateFlow()
    // --- ---

    // Listener registrations
    private var callsListener: ListenerRegistration? = null // 내부 콜
    private var driversListener: ListenerRegistration? = null // designated_drivers 리스너
    private var officeStatusListener: ListenerRegistration? = null // var로 수정
    private var sharedCallsListener: ListenerRegistration? = null // 공유 콜 리스너 추가
    // ★★★ 보류 중인 기사 리스너 변수 제거 ★★★
    // private var pendingDriversListener: ListenerRegistration? = null
    // TODO: pickup_drivers 리스너 추가 필요 시
    // private var pickupDriversListener: ListenerRegistration? = null

    // 캐시 구현
    private val callsCache = mutableMapOf<String, CallInfo>()
    private val driverCache = mutableMapOf<String, DriverInfo>()
    // ★★★ 보류 중인 기사 캐시 제거 ★★★
    // private val pendingDriversCache = mutableMapOf<String, PendingDriverInfo>()
    private var lastUpdateTime = 0L
    private val CACHE_DURATION = 30000L // 30초 캐시 유지

    // Added for office name fetching
    private val _isLoadingOfficeName = MutableStateFlow(false)
    val isLoadingOfficeName: StateFlow<Boolean> = _isLoadingOfficeName

    // --- 운행 완료 알림 중복 방지용 변수 추가 ---
    private var lastCompletedCallId: String? = null
    // --- 운행 상태 변화 추적용 맵 추가 ---
    private val previousStatusMap = mutableMapOf<String, String?>()

    // --- 운행 취소 팝업 상태 추가 ---
    private val _showCanceledCallPopup = MutableStateFlow(false)
    val showCanceledCallPopup: StateFlow<Boolean> = _showCanceledCallPopup
    private val _canceledCallInfo = MutableStateFlow<Pair<String, String>?>(null) // Pair(driverName, customerName)
    val canceledCallInfo: StateFlow<Pair<String, String>?> = _canceledCallInfo
    private var lastCanceledCallId: String? = null

    init {
        // <<-- Start of edit: Add log at the very beginning of init -->>
        Log.d(TAG, "!!! DashboardViewModel init block START !!!")
        // <<-- End of edit -->>
        Log.d(TAG, "ViewModel Initialized") // ViewModel 초기화 로그
        fetchCurrentUserAndStartListening() // Load initial state and start listening

        viewModelScope.launch {
            // Combine regionId and officeId flows
            combine(regionId, officeId) { currentRegionId, currentOfficeId ->
                Log.d(TAG, "Combine triggered - Region: $currentRegionId, Office: $currentOfficeId") // Combine 트리거 로그
                Pair(currentRegionId, currentOfficeId) // Pass the pair to the collector
            }.collect { (currentRegionId, currentOfficeId) ->
                 Log.d(TAG, "Combine collected - Region: $currentRegionId, Office: $currentOfficeId") // Combine 수집 로그
                if (!currentRegionId.isNullOrBlank() && !currentOfficeId.isNullOrBlank()) {
                    Log.d(TAG, "Valid IDs collected, calling fetchOfficeName.") // 유효 ID 확인 로그
                    fetchOfficeName(currentRegionId, currentOfficeId)
                } else {
                     Log.w(TAG, "Combine collected null ID(s) - Region: $currentRegionId, Office: $currentOfficeId. Skipping fetchOfficeName.") // Null ID 로그
                    _officeName.value = null // Clear office name if IDs become invalid
                    _isLoadingOfficeName.value = false
                }
            }
        }

        // Firestore 네트워크 연결 상태 모니터링 (경로 수정 필요 없음, 임의 컬렉션 사용)
        firestore.collection("daeri_calls") // 이 부분은 특정 경로 접근이 아닌 네트워크 상태 확인용
            .limit(1)
            .addSnapshotListener { _, error ->
            if (error != null && error.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                Log.e("DashboardViewModel", "Firestore 연결 끊김: ${error.message}")
                _isConnected.value = false
                
                // 3초 후 재연결 시도
                viewModelScope.launch {
                    delay(3000)
                    restartListeners()
                }
            } else if (_isConnected.value == false) {
                Log.d("DashboardViewModel", "Firestore 연결 복구됨")
                _isConnected.value = true
            }
        }
    }

    // 리스너 재시작 함수
    fun restartListeners() {
        Log.d("DashboardViewModel", "Firestore 리스너 재시작")
        stopListening()
        val currentRegionId = _regionId.value
        val currentOfficeId = _officeId.value
        if (!currentRegionId.isNullOrBlank() && !currentOfficeId.isNullOrBlank()) {
            startListening(currentRegionId, currentOfficeId)
        } else {
            Log.w(TAG, "Cannot restart listeners: Invalid regionId or officeId")
        }
    }
    
    // 모든 리스너 중지
    private fun stopListening() {
        callsListener?.remove()
        callsListener = null
        driversListener?.remove()
        driversListener = null
        officeStatusListener?.remove()
        officeStatusListener = null
        sharedCallsListener?.remove() // 공유 콜 리스너 중지 추가
        sharedCallsListener = null
        // ★★★ 보류 중인 기사 리스너 중지 제거 ★★★
        Log.d("DashboardViewModel", "Firestore 리스너 중지됨")
    }

    // 샘플 데이터 추가 함수 (개발 중에만 사용)
    private fun addSampleDataIfNeeded() {
        // --- daeri_calls 샘플 데이터 추가 로직 삭제 또는 주석 처리 ---
        // callsCollection.get().addOnSuccessListener { snapshot ->
        //     if (snapshot.isEmpty) {
        //         // ... 샘플 콜 데이터 생성 및 추가 로직 ...
        //         Log.d("DashboardViewModel", "샘플 콜 데이터 추가 완료")
        //     }
        // }
        // --- ---

        // 기사 데이터 확인 및 추가 (이 부분은 필요하면 유지)
        firestore.collection("drivers")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // ... (기존 기사 샘플 데이터 추가 로직)
                    Log.d("DashboardViewModel", "샘플 기사 데이터 추가 완료")
                }
            }
    }

    private fun fetchCurrentUserAndStartListening() {
        val user = auth.currentUser
        if (user != null) {
            _userEmail.value = user.email ?: "이메일 없음"
            val storedRegionId = sharedPreferences.getString("regionId", null)
            val storedOfficeId = sharedPreferences.getString("officeId", null)
            Log.i(TAG, "Loaded from SharedPreferences - Region: $storedRegionId, Office: $storedOfficeId")
            _regionId.value = storedRegionId
            _officeId.value = storedOfficeId
            // Log.d(TAG, "SharedPreferences 로드 완료 ...") // Removed duplicate log
        } else {
            _userEmail.value = "로그인되지 않음"
            _regionId.value = null
            _officeId.value = null
            _officeName.value = null
            _isLoadingOfficeName.value = false // Ensure loading is false if IDs are invalid
            Log.w(TAG, "User is not logged in, clearing IDs and office name.")
        }
        val currentRegionId = _regionId.value
        val currentOfficeId = _officeId.value
        if (!currentRegionId.isNullOrBlank() && !currentOfficeId.isNullOrBlank()) {
            startListening(currentRegionId, currentOfficeId) // SharedPreferences 로드 후 리스너 시작
        } else {
            Log.w(TAG, "Cannot start listening initially: Invalid regionId or officeId from SharedPreferences")
        }
    }

    private fun startListening(regionId: String, officeId: String) {
        Log.d(TAG, "🎯 Firestore 리스너 시작 (Region: $regionId, Office: $officeId)")
        stopListening() // 기존 리스너 중복 방지

        val officeRef = firestore.collection("regions").document(regionId)
                             .collection("offices").document(officeId)

        // --- Calls Listener ---
        // <<-- Start of edit: Add logs before and after callsListener setup -->>
        Log.d(TAG, "--> Step 1: Preparing to set up callsListener...") // ★★★ 로그 추가
        try {
            val callsQuery = officeRef.collection("calls")
                                    .orderBy("timestamp", Query.Direction.DESCENDING) // 최신 순
                                    .limit(20) // 최근 20개 (필요시 조정)
            // <<-- Start of edit: Construct path correctly from officeRef -->>
            val callsPath = officeRef.path + "/calls" // Construct path manually
            // <<-- End of edit -->>
            // ★★★ 로그 추가: 감시할 정확한 경로 확인 ★★★
            Log.i(TAG, "--> Step 2: Setting up callsListener for path: $callsPath")

            callsListener = callsQuery.addSnapshotListener { snapshots, e ->
                // <<-- 로그 추가: 리스너 이벤트 수신 확인 -->>
                Log.w(TAG, "******** callsListener received an event! snapshots=${snapshots?.size()}, error=${e?.message} ********") // ★★★ 이벤트 수신 확인 로그 (기존 로그 활용 또는 추가)
                Log.d(TAG, "🔥 callsListener triggered! (Path: $callsPath)") // Existing log
                if (e != null) {
                    Log.e(TAG, "callsListener 오류: ", e)
                    _isConnected.value = e.code != FirebaseFirestoreException.Code.UNAVAILABLE
                    return@addSnapshotListener
                }
                _isConnected.value = true

                if (snapshots == null) {
                    Log.w(TAG, "callsListener: snapshots is null!")
                    return@addSnapshotListener
                }

                // <<-- 로그 추가: 변경 감지된 문서 수 확인 -->>
                Log.d(TAG, "callsListener: ${snapshots.documentChanges.size}개의 문서 변경 감지됨.") // ★★★ 변경 문서 수 확인

                var cacheUpdated = false // 캐시 업데이트 여부 플래그

                for (dc in snapshots.documentChanges) {
                    val doc = dc.document
                    val docId = doc.id
                    val changeType = dc.type
                    Log.d(TAG, "  callsListener: Processing change - Doc ID: $docId, Type: $changeType")
                    val rawContactName = try { doc.getString("contactName") } catch (ex: Exception) { "(오류)" }
                    val rawPhoneNumber = try { doc.getString("phoneNumber") } catch (ex: Exception) { "(오류)" }
                    val rawStatus = try { doc.getString("status") } catch (ex: Exception) { "(오류)" }
                    Log.d(TAG, "    Raw Firestore fields: contactName='$rawContactName', phoneNumber='$rawPhoneNumber', status='$rawStatus'")

                    when (dc.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            Log.d(TAG, "    ${changeType} event for Doc ID: $docId. Attempting parse.")
                            val callInfo = parseCallDocument(doc)
                            if (callInfo != null) {
                                Log.d(TAG, "      Parse successful. Parsed Status: '${callInfo.status}' for Doc ID: $docId")
                                callsCache[doc.id] = callInfo
                                cacheUpdated = true
                                // 운행 완료 팝업 트리거 (이전 상태 추적)
                                if (changeType == DocumentChange.Type.MODIFIED && callInfo.status == "완료") {
                                    val prevStatus = previousStatusMap[docId]
                                    Log.d(TAG, "운행완료 조건 진입: docId=$docId, lastCompletedCallId=$lastCompletedCallId, prevStatus=$prevStatus")
                                    if (prevStatus != "완료") {
                                        _tripCompletedInfo.value = Pair(callInfo.assignedDriverName ?: "기사", callInfo.customerName ?: "고객")
                                        _showTripCompletedPopup.value = true
                                        lastCompletedCallId = docId
                                        Log.d(TAG, "운행 완료 팝업 트리거: $docId, 기사=${callInfo.assignedDriverName}, 고객=${callInfo.customerName}")
                                    } else {
                                        Log.d(TAG, "팝업 중복 방지로 인해 트리거 안함: docId=$docId")
                                    }
                                    previousStatusMap[docId] = callInfo.status
                                } else if (changeType == DocumentChange.Type.MODIFIED) {
                                    previousStatusMap[docId] = callInfo.status
                                }
                                // 운행 취소 팝업 트리거
                                if (callInfo.status == "CANCELED" && lastCanceledCallId != docId) {
                                    _canceledCallInfo.value = Pair(callInfo.assignedDriverName ?: "기사", callInfo.customerName ?: "고객")
                                    _showCanceledCallPopup.value = true
                                    lastCanceledCallId = docId
                                    Log.d(TAG, "운행 취소 팝업 트리거: $docId, 기사=${callInfo.assignedDriverName}, 고객=${callInfo.customerName}")
                                }
                            } else {
                                Log.w(TAG, "    Parse FAILED for Doc ID: $docId")
                            }
                        }
                        // REMOVED 이벤트는 캐시에서만 제거, 팝업 트리거 없음
                        DocumentChange.Type.REMOVED -> {
                            Log.d(TAG, "    REMOVED event for Doc ID: $docId. Removing from cache.")
                            if (callsCache.remove(doc.id) != null) {
                                cacheUpdated = true
                            }
                        }
                    }
                }
                // <<-- 로그 추가: 캐시 업데이트 여부 및 updateCallsFromCache 호출 확인 -->>
                if (cacheUpdated) {
                    Log.d(TAG, "callsListener: Cache was updated. Calling updateCallsFromCache(). Cache size: ${callsCache.size}") // ★★★ updateCallsFromCache 호출 전 로그
                    updateCallsFromCache() // 캐시가 변경되었을 때만 호출 (필요시 조정)
                } else {
                    Log.d(TAG, "callsListener: Cache was not updated. Skipping updateCallsFromCache().") // ★★★ updateCallsFromCache 건너뜀 로그
                }
                // Log.d(TAG, "callsListener: _calls StateFlow updated. Current count: ${callsCache.size}") // Removed redundant log, updateCallsFromCache handles this
            }

            // ★★★ 로그 추가: 리스너 객체 생성 확인 ★★★
            if (callsListener != null) {
                Log.i(TAG, "--> Step 3: callsListener successfully attached. Listener object: $callsListener")
            } else {
                Log.e(TAG, "--> Step 3 FAILED: callsListener attachment resulted in null!")
            }

        } catch (queryError: Exception) {
            // ★★★ 로그 추가: 리스너 설정 중 예외 발생 확인 ★★★
            Log.e(TAG, "--> Step FAILED: Exception during callsListener setup!", queryError)
        }
        // <<-- End of edit -->>

        // --- Designated Drivers Listener ---
        // <<-- Start of edit: Add log before driversListener setup for sequence check -->>
        Log.d(TAG, "--> Step 4: Preparing to set up driversListener...") // ★★★ 로그 추가
        try { // ★★★ try-catch 추가
            val driversPath = officeRef.path + "/designated_drivers" // ★★★ 경로 로그용 변수
            Log.d(TAG, "대리 기사 리스너 설정 시작 (Path: $driversPath)") // 기존 로그
            driversListener = officeRef.collection("designated_drivers")
                .addSnapshotListener { snapshots, e ->
                    // <<-- Start of edit: Remove or comment out verbose logs -->>
                    // Log.w(TAG, "******** driversListener received an event! snapshots=${snapshots?.size()}, error=${e?.message} ********")
                    // <<-- End of edit -->>
                    // <<-- Start of edit: Keep basic trigger log -->>
                    Log.d(TAG, "🔥 driversListener triggered! (Path: ${officeRef.path}/designated_drivers)") // Keep this basic trigger log
                    // <<-- End of edit -->>
                    if (e != null) {
                        Log.e(TAG, "driversListener 오류: ", e)
                        _isConnected.value = e.code != FirebaseFirestoreException.Code.UNAVAILABLE
                        return@addSnapshotListener
                    }
                    _isConnected.value = true

                    // <<-- Start of edit: Remove or comment out verbose logs -->>
                    // Log.d(TAG, "driversListener: ${snapshots?.documentChanges?.size ?: 0}개의 문서 변경 감지됨.")
                    // <<-- End of edit -->>

                    for (dc in snapshots!!.documentChanges) {
                        val doc = dc.document
                        val docId = doc.id
                        val changeType = dc.type
                        // <<-- Start of edit: Remove or comment out verbose logs -->>
                        // Log.d(TAG, "  driversListener: Processing change - Doc ID: $docId, Type: $changeType")
                        // Log raw status field from Firestore
                        // val rawStatus = try { doc.getString("status") } catch (ex: Exception) { "(오류)" }
                        // Log.d(TAG, "    Raw Firestore status field: '$rawStatus'")
                        // <<-- End of edit -->>

                        when (dc.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                // <<-- Start of edit: Remove or comment out verbose logs -->>
                                // Log.d(TAG, "    ${changeType} event for Doc ID: $docId. Attempting parse.")
                                // <<-- End of edit -->>
                                val driverInfo = parseDriverDocument(doc)
                                if (driverInfo != null) {
                                    // <<-- Start of edit: Remove or comment out verbose logs -->>
                                    // Log.d(TAG, "      Parse successful. Parsed Status: '${driverInfo.status}'")
                                    // <<-- End of edit -->>
                                    driverCache[doc.id] = driverInfo
                                } else {
                                    Log.w(TAG, "    Parse FAILED for Doc ID: $docId") // Keep failure warning
                                }
                            }
                            DocumentChange.Type.REMOVED -> {
                                // <<-- Start of edit: Keep basic remove log -->>
                                Log.d(TAG, "    REMOVED event for Doc ID: $docId. Removing from cache.") // Keep this basic log
                                // <<-- End of edit -->>
                                driverCache.remove(doc.id)
                            }
                        }
                    }
                    _drivers.value = driverCache.values.toList().sortedBy { it.name } // 캐시 변경 후 StateFlow 업데이트
                    // <<-- Start of edit: Remove or comment out verbose log -->>
                    // Log.d(TAG, "driversListener: _drivers StateFlow updated. Current count: ${driverCache.size}")
                    // <<-- End of edit -->>
                }

            // ★★★ 로그 추가: 리스너 객체 생성 확인 ★★★
            if (driversListener != null) {
                 Log.i(TAG, "--> Step 5: driversListener successfully attached. Listener object: $driversListener")
            } else {
                 Log.e(TAG, "--> Step 5 FAILED: driversListener attachment resulted in null!")
            }
        } catch (driverListenerError: Exception) { // ★★★ catch 블록 추가
             Log.e(TAG, "--> Step FAILED: Exception during driversListener setup!", driverListenerError)
        }
        // <<-- End of edit -->>

        // ... (Office Status Listener, Shared Calls Listener 로직은 동일) ...
    }

    private fun loadInitialCalls() {
        val currentRegionId = regionId.value
        val currentOfficeId = officeId.value
        if (currentRegionId == null || currentOfficeId == null) return // ID 없으면 로드 불가

        // --- 경로 수정 ---
        val callsPath = "regions/$currentRegionId/offices/$currentOfficeId/calls"
        // --- ---

        viewModelScope.launch {
            try {
                val snapshot = firestore.collection(callsPath) // 직접 경로 사용
                    // ...
            } catch (e: Exception) { /* ... */ }
        }
    }

    private fun updateCallsFromCache() {
        // status 관계없이 모든 콜을 최신순으로 10개까지 노출
        val latestCalls = callsCache.values
            .sortedByDescending { it.timestamp }
            .take(10)
            .toList()
        _calls.value = latestCalls
        lastUpdateTime = System.currentTimeMillis()
        Log.d(TAG, "콜 목록 UI 업데이트 (캐시 기반, 최대 10개): ${latestCalls.size}개")
    }

    private fun parseCallDocument(doc: com.google.firebase.firestore.DocumentSnapshot): CallInfo? {
        return try {
            // Firestore의 toObject() 메서드를 사용하여 자동 변환
            doc.toObject(CallInfo::class.java)?.apply {
                id = doc.id // 문서 ID는 수동으로 할당
            }
        } catch (e: Exception) {
            // TODO: 적절한 오류 로깅 (클래스 외부이므로 TAG 직접 접근 불가)
            Log.e("ParseCallError", "❌ 콜 문서 파싱 오류: ${doc.id}", e)
            null
        }
    }

    fun assignCallToDriver(callId: String, driverId: String, driverName: String) {
        val currentRegionId = regionId.value
        val currentOfficeId = officeId.value
        if (currentRegionId == null || currentOfficeId == null) return

        // --- 경로 수정 ---
        val callDocRef = firestore.collection("regions").document(currentRegionId)
                                 .collection("offices").document(currentOfficeId)
                                 .collection("calls").document(callId)
        val driverDocRef = firestore.collection("regions").document(currentRegionId)
                                 .collection("offices").document(currentOfficeId)
                                 .collection("designated_drivers").document(driverId)
        // --- ---

        // 기사 전화번호를 먼저 조회한 뒤 콜에 함께 저장
        viewModelScope.launch {
            try {
                val driverSnapshot = driverDocRef.get().await()
                val driverPhone = driverSnapshot.getString("phoneNumber") ?: ""
                callDocRef.update(mapOf(
                    "status" to CallStatus.ASSIGNED.firestoreValue,
                    "assignedDriverId" to driverId,
                    "assignedDriverName" to driverName,
                    "assignedDriverPhone" to driverPhone,
                    "assignedTimestamp" to Timestamp.now()
                )).addOnSuccessListener {
                    Log.d(TAG, "콜 배정 성공 (기사번호 포함)")
                    // 로컬 캐시 업데이트
                    callsCache[callId]?.let { call ->
                        callsCache[callId] = call.copy(
                            status = CallStatus.ASSIGNED.firestoreValue,
                            assignedDriverId = driverId,
                            assignedDriverName = driverName,
                            assignedDriverPhone = driverPhone,
                            assignedTimestamp = Timestamp.now()
                        )
                    }
                    // UI 갱신 (캐시 기반)
                    updateCallsFromCache()
                }.addOnFailureListener { e ->
                    Log.e("DashboardViewModel", "콜 배정 실패: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "기사 정보 조회 실패: ${e.message}")
            }
        }
    }

    fun updateCallStatus(callId: String, newStatus: String) {
        val currentRegionId = regionId.value
        val currentOfficeId = officeId.value
        if (currentRegionId == null || currentOfficeId == null) return

        // --- 경로 수정 ---
        val callDocRef = firestore.collection("regions").document(currentRegionId)
                                 .collection("offices").document(currentOfficeId)
                                 .collection("calls").document(callId)
        // --- ---

        callDocRef.update("status", newStatus)
            .addOnSuccessListener {
                Log.d("DashboardViewModel", "콜 상태 업데이트 성공")
                // 로컬 캐시에 있는 해당 콜의 상태도 업데이트
                callsCache[callId]?.let { call ->
                    callsCache[callId] = call.copy(status = newStatus)
                }
                // UI 갱신 (캐시 기반)
                updateCallsFromCache()
            }
            .addOnFailureListener { e ->
                Log.e("DashboardViewModel", "콜 상태 업데이트 실패: ${e.message}")
            }
    }

    fun deleteCall(callId: String) {
        val currentRegionId = regionId.value
        val currentOfficeId = officeId.value
        if (currentRegionId == null || currentOfficeId == null) return

        // --- 경로 수정 ---
        val callDocRef = firestore.collection("regions").document(currentRegionId)
                                 .collection("offices").document(currentOfficeId)
                                 .collection("calls").document(callId)
        // --- ---

        callDocRef.delete()
            .addOnSuccessListener {
                Log.d("DashboardViewModel", "콜 삭제 성공")
                // 로컬 캐시에서 해당 콜 제거
                callsCache.remove(callId)
                // UI 갱신 (캐시 기반)
                updateCallsFromCache()
            }
            .addOnFailureListener { e ->
                Log.e("DashboardViewModel", "콜 삭제 실패: ${e.message}")
            }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                auth.signOut()
                // 로그아웃 시 SharedPreferences 정보 삭제 (체이닝 방식 사용)
                sharedPreferences.edit() 
                    .remove("regionId")
                    .remove("officeId")
                    .apply() // 마지막에 apply() 호출
                Log.i(TAG, "User signed out and SharedPreferences cleared.")
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "로그아웃 실패: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    fun refreshCalls() {
        // 캐시 만료 시 초기 로드
        if (System.currentTimeMillis() - lastUpdateTime > CACHE_DURATION) {
             Log.d("DashboardViewModel", "캐시 만료, 초기 콜 다시 로드")
             loadInitialCalls()
        } else {
             Log.d("DashboardViewModel", "refreshCalls 호출 -> 캐시 기반 UI 업데이트")
             updateCallsFromCache()
        }
    }

    fun refreshDrivers() {
        val currentRegionId = regionId.value
        val currentOfficeId = officeId.value
        if (currentRegionId == null || currentOfficeId == null) return

        // --- 경로 수정 ---
        val driversPath = "regions/$currentRegionId/offices/$currentOfficeId/designated_drivers" // 'designated_drivers' 사용
        // --- ---

        viewModelScope.launch {
            try {
                val snapshot = firestore.collection(driversPath).get().await()
                // toObject() 사용하여 DriverInfo 변환
                val driversList = snapshot.documents.mapNotNull { doc ->
                    parseDriverDocument(doc) // parseDriverDocument 함수 사용
                }
                _drivers.value = driversList
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing drivers", e)
                // 오류 처리 (예: 사용자에게 메시지 표시)
            }
        }
    }

    // Service 시작 함수
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

    // Service 중지 함수
    fun stopForegroundService(context: Context) {
        val intent = Intent(context, CallManagerService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        context.stopService(intent)
    }

    // Function to trigger showing the dialog
    fun showCallDialog(callId: String) {
        Log.i(TAG, "showCallDialog called with callId: $callId")

        // <<-- Start of edit: Implement new dialog data fetching logic -->>
        // 이전 fetch 작업 취소
        directFetchJob?.cancel()
        _callInfoForDialog.value = null // 이전 정보 즉시 클리어
        _callToShowDialog.value = callId // 어떤 콜을 보여줘야 하는지 ID 설정
        Log.d(TAG, "_callToShowDialog state updated to: $callId")

        // 캐시 확인 및 Firestore 직접 조회 시작
        directFetchJob = viewModelScope.launch(Dispatchers.IO) {
            val cachedInfo = callsCache[callId]
            if (cachedInfo != null) {
                // 캐시에 정보가 있으면 즉시 사용
                Log.i(TAG, "Call info for $callId found in cache immediately. Setting dialog info.")
                withContext(Dispatchers.Main) {
                    _callInfoForDialog.value = cachedInfo
                }
            } else {
                // 캐시에 없으면 Firestore에서 직접 조회
                Log.w(TAG, "Call info for $callId not in cache. Fetching from Firestore...")
                val currentRegionId = regionId.value
                val currentOfficeId = officeId.value
                if (currentRegionId == null || currentOfficeId == null) {
                    Log.e(TAG, "Cannot fetch call info: Region or Office ID is null.")
                    // _callInfoForDialog.value = null // 이미 null 상태
                    return@launch // 더 이상 진행 불가
                }

                try {
                    val callDocRef = firestore.collection("regions").document(currentRegionId)
                                             .collection("offices").document(currentOfficeId)
                                             .collection("calls").document(callId)
                    val documentSnapshot = callDocRef.get().await()
                    if (documentSnapshot.exists()) {
                        val fetchedInfo = parseCallDocument(documentSnapshot)
                        Log.i(TAG, "Successfully fetched call info for $callId from Firestore.")
                        withContext(Dispatchers.Main) {
                            _callInfoForDialog.value = fetchedInfo
                        }
                    } else {
                        Log.e(TAG, "Call document $callId not found in Firestore.")
                        // _callInfoForDialog.value = null // 이미 null 상태
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching call info $callId from Firestore", e)
                    // _callInfoForDialog.value = null // 이미 null 상태
                }
            }
        }
        // <<-- End of edit -->>
    }

    // Function to dismiss the dialog state
    fun dismissCallDialog() {
        Log.i(TAG, "dismissCallDialog called. Resetting dialog states.")
        // <<-- Start of edit: Cancel fetch job and clear states -->>
        directFetchJob?.cancel()
        _callToShowDialog.value = null
        _callInfoForDialog.value = null
        // _isLoadingCallForDialog.value = false // 상태 자체가 제거됨
        // <<-- End of edit -->>
    }

    // Function to get specific call info, checking cache first
    // This function is now less critical for the dialog itself, but keep for other uses
    fun getCallInfoById(callId: String): CallInfo? {
        val callInfo = callsCache[callId]
        Log.d(TAG, "getCallInfoById($callId) called (cache check). Found: ${callInfo != null}")
        return callInfo
    }

    fun dismissDriverLoginPopup() {
        _showDriverLoginPopup.value = false
        _loggedInDriverName.value = null
    }

    // --- 팝업에서 호출될 승인 함수 --- 
    fun approveDriver(driverId: String) {
        val currentRegionId = regionId.value
        val currentOfficeId = officeId.value
        if (currentRegionId == null || currentOfficeId == null) {
            Log.e("DashboardViewModel", "Region/Office ID가 없어 기사를 승인할 수 없습니다.")
            _approvalActionState.value = DriverApprovalActionState.Error("사무실 정보 오류")
            return
        }
        // --- 경로 수정 및 로그 메시지 변경 ---
        val driverDocPath = "regions/$currentRegionId/offices/$currentOfficeId/designated_drivers/$driverId" // 'designated_drivers' 사용
        Log.d("DashboardViewModel", "대리 기사 승인 시도 (팝업): $driverId (경로: $driverDocPath)")
        _approvalActionState.value = DriverApprovalActionState.Loading
        viewModelScope.launch {
            try {
                firestore.collection("regions").document(currentRegionId)
                         .collection("offices").document(currentOfficeId)
                         .collection("designated_drivers").document(driverId) // 'designated_drivers' 사용
                    .update("status", "대기중") // 상태를 "대기중"으로 변경
                    .await()
                Log.d("DashboardViewModel", "대리 기사 승인 성공 (팝업): $driverId")
                _approvalActionState.value = DriverApprovalActionState.Success(driverId, "approved")
                dismissApprovalPopup()
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "대리 기사 승인 실패 (팝업): $driverId", e)
                 _approvalActionState.value = DriverApprovalActionState.Error(e.localizedMessage ?: "기사 승인 중 오류 발생")
            }
        }
    }

    // --- (선택) 팝업에서 호출될 거절 함수 (문서 삭제) ---
     fun rejectDriver(driverId: String) {
         val currentRegionId = regionId.value
         val currentOfficeId = officeId.value
         if (currentRegionId == null || currentOfficeId == null) {
             Log.e("DashboardViewModel", "Region/Office ID가 없어 기사를 거절할 수 없습니다.")
             _approvalActionState.value = DriverApprovalActionState.Error("사무실 정보 오류")
             return
         }
         // --- 경로 수정 및 로그 메시지 변경 ---
         val driverDocPath = "regions/$currentRegionId/offices/$currentOfficeId/designated_drivers/$driverId" // 'designated_drivers' 사용
         Log.d("DashboardViewModel", "대리 기사 거절(삭제) 시도 (팝업): $driverId (경로: $driverDocPath)")
         _approvalActionState.value = DriverApprovalActionState.Loading
         viewModelScope.launch {
             try {
                 firestore.collection("regions").document(currentRegionId)
                          .collection("offices").document(currentOfficeId)
                          .collection("designated_drivers").document(driverId) // 'designated_drivers' 사용
                     .delete() // 문서 삭제
                     .await()
                 Log.d("DashboardViewModel", "대리 기사 거절(삭제) 성공 (팝업): $driverId")
                 _approvalActionState.value = DriverApprovalActionState.Success(driverId, "rejected")
                 dismissApprovalPopup()
             } catch (e: Exception) {
                 Log.e("DashboardViewModel", "대리 기사 거절(삭제) 실패 (팝업): $driverId", e)
                  _approvalActionState.value = DriverApprovalActionState.Error(e.localizedMessage ?: "기사 거절(삭제) 중 오류 발생")
             }
         }
     }

    // --- 승인 팝업 닫기 함수 ---
    fun dismissApprovalPopup() {
        _showApprovalPopup.value = false
        _driverForApproval.value = null
         // 액션 상태는 별도 리셋 함수 사용
    }

    // --- 승인/거절 액션 상태 초기화 함수 ---
    fun resetApprovalActionState() {
         _approvalActionState.value = DriverApprovalActionState.Idle
    }

    // --- 기사 퇴근 알림 팝업 닫기 함수 추가 ---
    fun dismissDriverLogoutPopup() {
        _showDriverLogoutPopup.value = false
        _loggedOutDriverName.value = null
    }

    // --- 운행 시작 알림 팝업 닫기 함수 추가 ---
    fun dismissTripStartedPopup() {
        _showTripStartedPopup.value = false
        _tripStartedInfo.value = null
    }

    // --- 운행 완료 알림 팝업 닫기 함수 추가 ---
    fun dismissTripCompletedPopup() {
        _showTripCompletedPopup.value = false
        _tripCompletedInfo.value = null
    }

    // --- 운행 취소 팝업 닫기 함수 추가 ---
    fun dismissCanceledCallPopup() {
        _showCanceledCallPopup.value = false
        _canceledCallInfo.value = null
    }

    // trip_summary 문자열을 파싱하여 새로운 형식으로 포맷하는 함수 추가
    private fun parseAndFormatTripSummary(summary: String?): String {
        if (summary.isNullOrBlank()) return "운행 정보 없음"

        // 정규 표현식 수정: \d -> \\d
        val regexWithWaypoints = Regex("^(.*) - (.*) \\((.*)\\) (\\d+)원$")
        val regexWithoutWaypoints = Regex("^(.*) - (.*) (\\d+)원$")

        val matchWithWaypoints = regexWithWaypoints.find(summary)
        if (matchWithWaypoints != null) {
            val (departure, destination, waypoints, fare) = matchWithWaypoints.destructured
            // 반환 문자열 형식 변경: 줄바꿈 사용
            return "출발지 : $departure\n경유지 : $waypoints\n도착지 : $destination\n요금 : ${fare}원"
        }

        val matchWithoutWaypoints = regexWithoutWaypoints.find(summary)
        if (matchWithoutWaypoints != null) {
            val (departure, destination, fare) = matchWithoutWaypoints.destructured
            // 반환 문자열 형식 변경: 줄바꿈 사용
            return "출발지 : $departure\n도착지 : $destination\n요금 : ${fare}원"
        }

        // 정규식 매칭 실패 시 원본 반환 (오류 처리)
        Log.w("DashboardViewModel", "trip_summary 형식 불일치: $summary")
        return summary // 또는 "형식 오류: $summary"
    }

    fun updateOfficeStatus(newStatus: String) {
        val currentRegionId = regionId.value
        val currentOfficeId = officeId.value
        if (currentRegionId == null || currentOfficeId == null) {
             Log.e("DashboardViewModel", "Region/Office ID가 없어 상태를 업데이트할 수 없습니다.")
             return // ID 없으면 업데이트 불가
        }
        // ... (newStatus 유효성 검사) ...

        // --- 경로 수정 ---
        val officeDocRef = firestore.collection("regions").document(currentRegionId)
                                 .collection("offices").document(currentOfficeId)
        // --- ---

        Log.d("DashboardViewModel", "사무실 상태 업데이트 시도: $newStatus (경로: ${officeDocRef.path})")
        officeDocRef.update("status", newStatus)
            .addOnSuccessListener {
                Log.d("DashboardViewModel", "사무실 상태 업데이트 성공: $newStatus")
                // _officeStatus.value = newStatus // 리스너가 자동으로 업데이트하므로 주석 처리
            }
            .addOnFailureListener { e ->
                Log.e("DashboardViewModel", "사무실 상태 업데이트 실패: ${e.message}", e)
                // 사용자에게 오류 알림 등 추가 처리 고려
            }
    }

    /**
     * Fetches the office name from Firestore using the provided region and office IDs.
     * Updates the _officeName StateFlow.
     */
    private fun fetchOfficeName(regionId: String, officeId: String) {
        // 이미 로딩 중이거나 ID가 유효하지 않으면 중복 실행 방지
        if (_isLoadingOfficeName.value || regionId.isBlank() || officeId.isBlank()) {
            Log.d(TAG, "fetchOfficeName skipped: isLoading=${_isLoadingOfficeName.value}, regionId=$regionId, officeId=$officeId")
            return
        }

        Log.d(TAG, "fetchOfficeName 시작: Region=$regionId, Office=$officeId")
        _isLoadingOfficeName.value = true
        _officeName.value = "사무실 정보 로딩 중..." // 로딩 상태 표시

        viewModelScope.launch {
            // <<-- Start of edit: Add more detailed logging within fetchOfficeName -->>
            val officeDocPath = "regions/$regionId/offices/$officeId"
            Log.d(TAG, "  Attempting to get document at path: $officeDocPath")
            // <<-- End of edit -->>
            try {
                val officeDocRef = firestore.collection("regions").document(regionId)
                                           .collection("offices").document(officeId)
                // <<-- Start of edit: Add log before await -->>
                Log.d(TAG, "    Calling officeDocRef.get().await()...")
                // <<-- End of edit -->>
                val document = officeDocRef.get().await()
                // <<-- Start of edit: Add log after await -->>
                Log.d(TAG, "    Firestore get() completed. Document exists: ${document.exists()}")
                // <<-- End of edit -->>
                
                if (document.exists()) {
                    // <<-- Start of edit: Log before getting name field -->>
                    Log.d(TAG, "    Document exists. Attempting to get 'name' field.")
                    // <<-- End of edit -->>
                    val name = document.getString("name")
                    // <<-- Start of edit: Log the retrieved name -->>
                    Log.d(TAG, "      Retrieved 'name' field value: $name")
                    // <<-- End of edit -->>
                    if (name != null) {
                        _officeName.value = name
                        Log.i(TAG, "Office name fetched successfully: $name")
                    } else {
                        _officeName.value = "이름 없음"
                        Log.w(TAG, "Office document exists but 'name' field is missing or null.")
                        Log.d(TAG, "### OfficeName State Updated: 이름 없음") // 로그 추가
                    }
                } else {
                    _officeName.value = "사무실 없음"
                    Log.w(TAG, "Office document does not exist at path: ${officeDocRef.path}")
                    Log.d(TAG, "### OfficeName State Updated: 사무실 없음") // 로그 추가
                }
            } catch (e: Exception) {
                _officeName.value = "로드 오류"
                // <<-- Start of edit: Log the specific exception -->>
                Log.e(TAG, "Error fetching office name for $officeDocPath", e)
                // <<-- End of edit -->>
                Log.d(TAG, "### OfficeName State Updated: 로드 오류") // 로그 추가
                // 오류 발생 시 사용자에게 알림 또는 재시도 로직 추가 고려
            } finally {
                _isLoadingOfficeName.value = false
                Log.d(TAG, "fetchOfficeName 완료: isLoading=false")
            }
        }
    }

    // --- ★★★ 로그인 완료 시 호출될 함수 추가 ★★★ ---
    fun loadDataForUser(regionId: String, officeId: String) {
        Log.i(TAG, "loadDataForUser called with Region: $regionId, Office: $officeId")
        // 내부 상태 업데이트
        _regionId.value = regionId
        _officeId.value = officeId

        // 기존 리스너 중지 (중복 방지 및 이전 데이터 클리어 효과)
        stopListening()
        // 캐시 클리어
        callsCache.clear()
        driverCache.clear()
        // ★★★ 보류 중인 기사 캐시 클리어 제거 ★★★
        // pendingDriversCache.clear()
        _calls.value = emptyList() // UI 상태도 초기화
        _drivers.value = emptyList()
        // ★★★ 보류 중인 기사 UI 상태 초기화 제거 ★★★
        // _pendingDrivers.value = emptyList()

        // 새 ID로 사무실 이름 로드 및 리스너 시작
        fetchOfficeName(regionId, officeId)
        startListening(regionId, officeId) 
    }
    // --- ★★★ ---
}

// Firestore Document를 DriverInfo 객체로 변환하는 함수
private fun parseDriverDocument(document: com.google.firebase.firestore.DocumentSnapshot): DriverInfo? {
    val docIdForLog = document.id // 로그용 ID 미리 저장
    // <<-- Start of edit: Remove or comment out verbose logs -->>
    // Log.d("ParseDriverFunc", "  Attempting to parse document: $docIdForLog")
    // <<-- End of edit -->>
    return try {
        val driver = document.toObject(DriverInfo::class.java)?.apply {
            id = document.id // 문서 ID는 수동으로 할당
        }
        // <<-- Start of edit: Remove or comment out verbose logs -->>
        // if (driver != null) {
        //     Log.d("ParseDriverFunc", "    Automatic parsing (toObject) successful for $docIdForLog. Parsed status: '${driver.status}'")
        // } else {
        //     Log.w("ParseDriverFunc", "    Automatic parsing (toObject) returned null for $docIdForLog")
        // }
        // <<-- End of edit -->>
        driver // 파싱 결과 반환
    } catch (e: Exception) {
        Log.e("ParseDriverError", "❌ 기사 문서 파싱 오류(자동): $docIdForLog", e) // Keep error log
        null
    }
} 
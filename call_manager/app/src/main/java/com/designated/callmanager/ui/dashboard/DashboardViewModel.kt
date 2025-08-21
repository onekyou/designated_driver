package com.designated.callmanager.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.R
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import com.designated.callmanager.data.DriverInfo
import com.designated.callmanager.data.DriverStatus
import com.designated.callmanager.data.PointsInfo
import com.designated.callmanager.data.PointTransaction
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

data class PTTStatus(
    val isConnected: Boolean = false,
    val isSpeaking: Boolean = false,
    val connectionState: String = "Disconnected",
    val channelName: String? = null
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

    private val _pointsInfo = MutableStateFlow<PointsInfo?>(null)
    val pointsInfo: StateFlow<PointsInfo?> = _pointsInfo.asStateFlow()

    private val _pointTransactions = MutableStateFlow<List<PointTransaction>>(emptyList())
    val pointTransactions: StateFlow<List<PointTransaction>> = _pointTransactions.asStateFlow()

    private val _callInfoForDialog = MutableStateFlow<CallInfo?>(null)
    val callInfoForDialog: StateFlow<CallInfo?> = _callInfoForDialog.asStateFlow()

    // Popups states
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

    private var callsListener: ListenerRegistration? = null
    private var driversListener: ListenerRegistration? = null
    private var officeStatusListener: ListenerRegistration? = null
    private var sharedCallsListener: ListenerRegistration? = null
    private var allSharedCallsListener: ListenerRegistration? = null
    private var pointsListener: ListenerRegistration? = null
    private var pointTransactionsListener: ListenerRegistration? = null

    private val callsCache = mutableMapOf<String, CallInfo>()
    private val driverCache = mutableMapOf<String, DriverInfo>()
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

        callsListener = officeRef.collection("calls")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshots, e ->
                if (e != null) { 
                    return@addSnapshotListener 
                }
                
                if (snapshots == null) {
                    return@addSnapshotListener
                }
                
                for (dc in snapshots.documentChanges) {
                    val doc = dc.document
                    val callInfo = parseCallDocument(doc)
                    if (callInfo == null) {
                        continue
                    }

                    when (dc.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            if (dc.type == DocumentChange.Type.ADDED && 
                                callInfo.status == CallStatus.WAITING.firestoreValue) {
                                Log.d(TAG, "새로운 WAITING 콜 감지: callId=${callInfo.id}, callType=${callInfo.callType}")
                                
                                if (callInfo.callType != "SHARED") { // 공유콜인 경우 팝업 표시 안함
                                    Log.d(TAG, "일반 콜이므로 팝업 표시: ${callInfo.id}")
                                    _newCallInfo.value = callInfo
                                    _showNewCallPopup.value = true
                                } else {
                                    Log.d(TAG, "공유콜이므로 팝업 표시 안함: ${callInfo.id}")
                                }
                            }
                            if (callInfo.status == CallStatus.IN_PROGRESS.firestoreValue && previousStatusMap[doc.id] != CallStatus.IN_PROGRESS.firestoreValue) {
                                val tripSummary = buildString {
                                    append("출발: ${callInfo.departure_set ?: callInfo.customerAddress ?: "정보없음"}")
                                    append(", 도착: ${callInfo.destination_set ?: "정보없음"}")
                                    if (!callInfo.waypoints_set.isNullOrBlank()) {
                                        append(", 경유: ${callInfo.waypoints_set}")
                                    }
                                    append(", 요금: ${callInfo.fare_set ?: callInfo.fare ?: 0}원")
                                }
                                var phone = callInfo.assignedDriverPhone
                                if (phone.isNullOrBlank()) {
                                    val dId = callInfo.assignedDriverId
                                    if (!dId.isNullOrBlank()) {
                                        phone = driverCache.values.firstOrNull { it.id == dId }?.phoneNumber
                                    }
                                }
                                val driverDisplayName = if (callInfo.callType == "SHARED") {
                                    "공유 기사님"
                                } else {
                                    callInfo.assignedDriverName ?: "기사"
                                }
                                _tripStartedInfo.value = Triple(
                                    driverDisplayName,
                                    phone,
                                    tripSummary
                                )
                                _showTripStartedPopup.value = true
                            }
                            if (callInfo.status == CallStatus.COMPLETED.firestoreValue && previousStatusMap[doc.id] != CallStatus.COMPLETED.firestoreValue) {
                                if (lastCompletedCallId != doc.id) {
                                    val driverName = if (callInfo.callType == "SHARED") {
                                        "공유 기사님"
                                    } else {
                                        callInfo.assignedDriverName ?: "기사"
                                    }
                                    val customerName: String = callInfo.customerName?.takeIf { it.isNotBlank() } ?: "고객"
                                    _tripCompletedInfo.value = Pair(driverName, customerName)
                                    _showTripCompletedPopup.value = true
                                    lastCompletedCallId = doc.id
                                }
                            }
                            previousStatusMap[doc.id] = callInfo.status
                            callsCache[doc.id] = callInfo
                        }
                        DocumentChange.Type.REMOVED -> {
                            callsCache.remove(doc.id)
                            previousStatusMap.remove(doc.id)
                        }
                    }
                }
                updateCallsFromCache()
            }

        driversListener = officeRef.collection("designated_drivers")
            .addSnapshotListener { snapshots, e ->
                Log.d(TAG, "[Drivers Listener] Triggered.")
                if (e != null) {
                    Log.e(TAG, "[Drivers Listener] Error:", e)
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    Log.w(TAG, "[Drivers Listener] Snapshot is null.")
                    return@addSnapshotListener
                }

                Log.d(TAG, "[Drivers Listener] Received ${snapshots.size()} documents. ${snapshots.documentChanges.size} changes.")

                for (dc in snapshots.documentChanges) {
                    val doc = dc.document
                    Log.d(TAG, "[Drivers Listener] Processing change: Type=${dc.type}, DocID=${doc.id}")
                    try {
                        val driverInfo = doc.toObject(DriverInfo::class.java).apply { id = doc.id }
                        when (dc.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                Log.d(TAG, "[Drivers Listener] Caching driver: ${driverInfo.name} (docId=${doc.id}, objId=${driverInfo.id}, authUid=${driverInfo.authUid}) - Status: ${driverInfo.status}")
                                driverCache[doc.id] = driverInfo
                            }
                            DocumentChange.Type.REMOVED -> {
                                Log.d(TAG, "[Drivers Listener] Removing driver from cache: ${doc.id}")
                                driverCache.remove(doc.id)
                            }
                        }
                    } catch (parseEx: Exception) {
                        Log.e(TAG, "[Drivers Listener] Failed to parse document ${doc.id}", parseEx)
                    }
                }
                _drivers.value = driverCache.values.toList().sortedBy { it.name }
                Log.d(TAG, "[Drivers Listener] Updated UI with ${driverCache.size} drivers.")
            }

        officeStatusListener = officeRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.w(TAG, "Office status listener failed.", e)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                _officeStatus.value = snapshot.getString("status") ?: ""
            } else {
                Log.d(TAG, "Current data: null")
            }
        }

        // -- 공유 콜 리스너 --
        // 내가 수락할 수 있는 OPEN/CLAIMED(내 사무실) 콜만 수신 (내가 올린 콜은 제외)

        // Map cache for shared calls (excluding calls from my office)
        val sharedMap = mutableMapOf<String, com.designated.callmanager.data.SharedCallInfo>()

        fun emitSharedCalls() {
            _sharedCalls.value = sharedMap.values.sortedByDescending { it.timestamp?.seconds ?: 0 }
        }

        // Listener A: region 내 OPEN 콜만 표시 (CLAIMED는 내부콜로 이동하므로 숨김)
        val listenerA = firestore.collection("shared_calls")
            .whereEqualTo("targetRegionId", regionId)
            .whereEqualTo("status", "OPEN")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "[SharedCalls] Listener failed.", e)
                    return@addSnapshotListener
                }
                Log.d(TAG, "[SharedCalls] Received ${snapshots?.size()} documents")
                snapshots?.documentChanges?.forEach { dc ->
                    val doc = dc.document
                    val data = doc.toObject(com.designated.callmanager.data.SharedCallInfo::class.java)
                        ?.copy(id = doc.id)

                    Log.d(TAG, "[SharedCalls] Processing: ${doc.id}, source=${data?.sourceOfficeId}, claimed=${data?.claimedOfficeId}, status=${data?.status}")

                    // 필터: 내가 올린 콜이거나 다른 사무실이 수락한 콜 제외
                    val shouldInclude = when {
                        data == null -> false
                        data.sourceOfficeId == officeId -> false // 내가 올린 콜 제외
                        data.status == "CLAIMED" && data.claimedOfficeId != officeId -> false // 다른 사무실이 수락한 콜 제외
                        data.status == "COMPLETED" -> false // 완료된 콜은 제외 (목록 정리)
                        else -> true
                    }
                    
                    Log.d(TAG, "[SharedCalls] shouldInclude: $shouldInclude")

                    when (dc.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED, com.google.firebase.firestore.DocumentChange.Type.MODIFIED -> {
                            if (shouldInclude) {
                                data?.let { sharedMap[doc.id] = it }
                            } else {
                                // 필터 조건에 맞지 않으면 맵에서 제거
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

        // Store listener to remove later
        sharedCallsListener = listenerA

        // -- 모든 공유콜 리스너 (관리 페이지용) --
        Log.d(TAG, "[AllSharedCalls] Setting up listener for region=$regionId")
        allSharedCallsListener = firestore.collection("shared_calls")
            .whereEqualTo("sourceRegionId", regionId) // 내 지역의 모든 공유콜
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "[AllSharedCalls] Listener failed.", e)
                    return@addSnapshotListener
                }
                Log.d(TAG, "[AllSharedCalls] Received ${snapshots?.size()} documents")
                
                val allCalls = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(com.designated.callmanager.data.SharedCallInfo::class.java)
                        ?.copy(id = doc.id)
                        ?.also { 
                            Log.d(TAG, "[AllSharedCalls] Loaded: ${doc.id}, source=${it.sourceOfficeId}, claimed=${it.claimedOfficeId}, status=${it.status}")
                        }
                } ?: emptyList()
                
                Log.d(TAG, "[AllSharedCalls] Setting ${allCalls.size} shared calls")
                _allSharedCalls.value = allCalls.sortedByDescending { it.timestamp?.seconds ?: 0 }
            }

        // -- 포인트 잔액 리스너 --
        pointsListener = officeRef.collection("points").document("points")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w(TAG, "Points listener failed.", e)
                    return@addSnapshotListener
                }
                Log.d(TAG, "[Points] Snapshot exists: ${snapshot?.exists()}")
                if (snapshot != null && snapshot.exists()) {
                    val pointsInfo = snapshot.toObject(PointsInfo::class.java)
                    Log.d(TAG, "[Points] Loaded points: ${pointsInfo?.balance}")
                    _pointsInfo.value = pointsInfo
                } else {
                    Log.d(TAG, "[Points] No points document found, setting default")
                    _pointsInfo.value = PointsInfo(0, null)
                }
            }

        // -- 포인트 거래내역 리스너 --
        Log.d(TAG, "[PointTransactions] Setting up listener for region=$regionId, office=$officeId")
        pointTransactionsListener = firestore.collection("point_transactions")
            .whereEqualTo("regionId", regionId)
            .whereEqualTo("officeId", officeId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w(TAG, "[PointTransactions] Listener failed.", e)
                    return@addSnapshotListener
                }
                Log.d(TAG, "[PointTransactions] Received ${snapshots?.size()} documents")
                if (snapshots != null) {
                    val transactions = snapshots.documents.mapNotNull { doc ->
                        Log.d(TAG, "[PointTransactions] Processing doc: ${doc.id}")
                        doc.toObject(PointTransaction::class.java)?.apply { id = doc.id }
                    }
                    Log.d(TAG, "[PointTransactions] Parsed ${transactions.size} transactions")
                    _pointTransactions.value = transactions
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
            Log.e(TAG, "Error parsing call document: ${doc.id}", e)
            null
        }
    }

    fun assignCallToDriver(callInfo: CallInfo, driverId: String) {
        if (_regionId.value == null || _officeId.value == null) {
            Log.e(TAG, "Region or Office ID is null. Cannot assign call.")
            return
        }
        val officePath = firestore.collection("regions").document(_regionId.value!!)
            .collection("offices").document(_officeId.value!!)

        viewModelScope.launch {
            try {
                // 1. Get Driver Info first
                val driverSnapshot = officePath.collection("designated_drivers").document(driverId).get().await()
                val driverInfo = driverSnapshot.toObject(DriverInfo::class.java)
                if (driverInfo == null) {
                    Log.e(TAG, "Driver info not found for ID: $driverId")
                    return@launch
                }

                // ★★★ 중요: assignedDriverId는 기사의 Firebase Auth UID여야 함 ★★★
                val driverAuthUid = driverInfo.authUid
                if (driverAuthUid.isNullOrBlank()) {
                    Log.e(TAG, "Driver authUid is missing for driver: $driverId")
                    return@launch
                }

                // 2. Update Call Document
                val callRef = officePath.collection("calls").document(callInfo.id)
                val callUpdates = mapOf(
                    "assignedDriverId" to driverAuthUid, // Firebase Auth UID 사용
                    "assignedDriverName" to driverInfo.name,
                    "status" to CallStatus.ASSIGNED.firestoreValue,
                    "updatedAt" to Timestamp.now()
                )
                Log.d(TAG, ">>>>>>>>>> 배차 시작: Call ID=${callInfo.id}, Driver ID=${driverId}, Status to be set=${CallStatus.ASSIGNED.firestoreValue}")
                callRef.update(callUpdates).await()
                Log.d(TAG, "Step 1/2 SUCCESS: Call ${callInfo.id} updated.")


                // 3. Update Driver Document
                val driverRef = officePath.collection("designated_drivers").document(driverId)
                driverRef.update("status", DriverStatus.ASSIGNED.value).await()
                Log.d(TAG, "Step 2/2 SUCCESS: Driver $driverId status updated to ASSIGNED.")

            } catch (e: Exception) {
                Log.e(TAG, "Error during sequential assignment", e)
                // TODO: Add user-facing error message
            }
        }
    }

    fun updateCallStatus(callId: String, newStatus: CallStatus) {
        if (_regionId.value == null || _officeId.value == null) {
            Log.e(TAG, "Region or Office ID is null. Cannot update call status.")
            return
        }
        viewModelScope.launch {
            try {
                firestore.collection("regions").document(_regionId.value!!)
                    .collection("offices").document(_officeId.value!!)
                    .collection("calls").document(callId)
                    .update("status", newStatus.firestoreValue)
                    .await()
                Log.d(TAG, "Successfully updated call $callId to status $newStatus")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating call status", e)
            }
        }
    }

    fun cancelCall(callId: String) {
        updateCallStatus(callId, CallStatus.CANCELED)
    }

    fun completeCall(callId: String) {
        if (_regionId.value == null || _officeId.value == null) return

        viewModelScope.launch {
            try {
                val callRef = firestore.collection("regions").document(_regionId.value!!)
                    .collection("offices").document(_officeId.value!!)
                    .collection("calls").document(callId)

                // 1. 먼저 콜 정보를 가져와서 assignedDriverId(Firebase Auth UID) 확인
                val callSnapshot = callRef.get().await()
                val assignedDriverAuthUid = callSnapshot.getString("assignedDriverId")

                // 2. 콜 상태를 COMPLETED로 업데이트
                callRef.update("status", CallStatus.COMPLETED.firestoreValue).await()

                // 3. 배정된 기사가 있다면 해당 기사의 상태를 WAITING으로 업데이트
                if (!assignedDriverAuthUid.isNullOrBlank()) {
                    // authUid로 기사 문서 찾기
                    val driversQuery = firestore.collection("regions").document(_regionId.value!!)
                        .collection("offices").document(_officeId.value!!)
                        .collection("designated_drivers")
                        .whereEqualTo("authUid", assignedDriverAuthUid)
                        .limit(1)
                        .get()
                        .await()

                    if (!driversQuery.isEmpty) {
                        val driverDoc = driversQuery.documents[0]
                        driverDoc.reference.update("status", "WAITING").await()
                        Log.d(TAG, "Driver ${driverDoc.id} status updated to WAITING")
                    } else {
                        Log.w(TAG, "No driver found with authUid: $assignedDriverAuthUid")
                    }
                }

                Log.d(TAG, "Call $callId completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error completing call $callId", e)
            }
        }
    }

    fun showCallDetails(callInfo: CallInfo) {
        _callInfoForDialog.value = callInfo
    }

    fun updateOfficeStatus(newStatus: String) {
        getOfficeRef()?.update("status", newStatus)
            ?.addOnSuccessListener { Log.d(TAG, "Office status updated to $newStatus") }
            ?.addOnFailureListener { e -> Log.e(TAG, "Error updating office status", e) }
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
                // 이미 로드된 calls 리스트에서 찾기
                val callFromCache = _calls.value.find { it.id == callId }
                if (callFromCache != null) {
                    Log.d(TAG, "showCallDialog: Found call in cache. ID: $callId")
                    _newCallInfo.value = callFromCache
                    _showNewCallPopup.value = true
                    return@launch
                }

                // 캐시에 없으면 Firestore에서 직접 가져오기
                Log.d(TAG, "showCallDialog: Call not in cache. Fetching from Firestore. ID: $callId")
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                val callDocument = firestore.collection("regions").document(region)
                    .collection("offices").document(office)
                    .collection("calls").document(callId)
                    .get().await()

                if (callDocument.exists()) {
                    val callInfo = parseCallDocument(callDocument)
                    if (callInfo != null) {
                        Log.d(TAG, "showCallDialog: Successfully fetched call from Firestore. ID: $callId")
                        _newCallInfo.value = callInfo
                        _showNewCallPopup.value = true
                    } else {
                        Log.w(TAG, "showCallDialog: Failed to parse call document. ID: $callId")
                    }
                } else {
                    Log.w(TAG, "showCallDialog: Call document does not exist in Firestore. ID: $callId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "showCallDialog: Error fetching call info for ID: $callId", e)
            }
        }
    }

    fun dismissCallDialog() {
        _callInfoForDialog.value = null
    }

    // 공유콜 취소 알림 다이얼로그 관련 상태 및 메서드
    private val _showSharedCallCancelledDialog = MutableStateFlow(false)
    val showSharedCallCancelledDialog: StateFlow<Boolean> = _showSharedCallCancelledDialog.asStateFlow()

    private val _cancelledCallInfo = MutableStateFlow<CallInfo?>(null)
    val cancelledCallInfo: StateFlow<CallInfo?> = _cancelledCallInfo.asStateFlow()

    // PTT 관련 상태
    private val _pttStatus = MutableStateFlow(PTTStatus())
    val pttStatus: StateFlow<PTTStatus> = _pttStatus.asStateFlow()

    fun showSharedCallCancelledDialog(callId: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "showSharedCallCancelledDialog: callId = $callId")
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch

                val callDocument = firestore.collection("regions").document(region)
                    .collection("offices").document(office)
                    .collection("calls").document(callId)
                    .get().await()

                if (callDocument.exists()) {
                    val callInfo = parseCallDocument(callDocument)
                    if (callInfo != null) {
                        Log.d(TAG, "showSharedCallCancelledDialog: 콜 정보 로드 성공")
                        _cancelledCallInfo.value = callInfo
                        _showSharedCallCancelledDialog.value = true
                    } else {
                        Log.w(TAG, "showSharedCallCancelledDialog: 콜 문서 파싱 실패")
                    }
                } else {
                    Log.w(TAG, "showSharedCallCancelledDialog: 콜 문서가 존재하지 않음")
                }
            } catch (e: Exception) {
                Log.e(TAG, "showSharedCallCancelledDialog: 오류 발생", e)
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
        // 거절 시 문서를 삭제하는 대신 상태를 변경하여 기록을 남깁니다.
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
    fun createPlaceholderCall() {
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return

        val officeRef = firestore.collection("regions").document(region)
            .collection("offices").document(office)

        viewModelScope.launch {
            try {
                val nowTs = Timestamp.now()
                val data = hashMapOf(
                    "phoneNumber" to "",
                    "customerAddress" to "",
                    "customerName" to "",
                    "timestamp" to nowTs,
                    "timestampClient" to System.currentTimeMillis(),
                    "status" to CallStatus.WAITING.firestoreValue,
                    "regionId" to region,
                    "officeId" to office,
                    "createdBy" to (auth.currentUser?.uid ?: "")
                )

                val docRef = officeRef.collection("calls").add(data).await()
                Log.d(TAG, "Placeholder call created: ${docRef.id}")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating placeholder call", e)
            }
        }
    }

    /**
     * 콜을 Firebase와 로컬 캐시에서 삭제
     */
    fun deleteCall(callId: String) {
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return

        viewModelScope.launch {
            try {
                // Firebase에서 콜 삭제
                firestore.collection("regions").document(region)
                    .collection("offices").document(office)
                    .collection("calls").document(callId)
                    .delete()
                    .await()
                
                Log.d(TAG, "Call deleted successfully: $callId")
                
                // 로컬 캐시에서도 제거
                callsCache.remove(callId)
                previousStatusMap.remove(callId)
                _calls.value = callsCache.values.toList()
                
                // 새로운 콜 팝업이 열려있다면 닫기
                if (_newCallInfo.value?.id == callId) {
                    dismissNewCallPopup()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting call", e)
            }
        }
    }

    // 기존 수동 배차 로직은 보류 상태로 두고 사용하지 않음.
    // fun createManualCall(driverId: String) { ... }

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
        stopListening()
        callsCache.clear()
        driverCache.clear()
        previousStatusMap.clear()
        _calls.value = emptyList()
        _drivers.value = emptyList()

        _regionId.value = regionId
        _officeId.value = officeId

        startListening(regionId, officeId)
        fetchOfficeName(regionId, officeId)
    }

    private fun stopListening() {
        callsListener?.remove()
        driversListener?.remove()
        officeStatusListener?.remove()
        sharedCallsListener?.remove()
        allSharedCallsListener?.remove()
        pointsListener?.remove()
        pointTransactionsListener?.remove()
        callsListener = null
        driversListener = null
        officeStatusListener = null
        sharedCallsListener = null
        allSharedCallsListener = null
        pointsListener = null
        pointTransactionsListener = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }

    fun shareCall(callInfo: CallInfo, departure: String, destination: String, fare: Int) {
        viewModelScope.launch {
            try {
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch
                val docRef = firestore.collection("shared_calls").document()
                val data = hashMapOf(
                    "status" to "OPEN",
                    "departure" to departure,
                    "destination" to destination,
                    "fare" to fare,
                    "sourceRegionId" to region,
                    "sourceOfficeId" to office,
                    "targetRegionId" to region, // 동일 지역 한정
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "phoneNumber" to callInfo.phoneNumber,
                    "originalCallId" to callInfo.id, // 원본 콜 ID 저장
                    "timestamp" to Timestamp.now()
                )
                docRef.set(data).await()
                Log.d(TAG, "Shared call uploaded: ${docRef.id}")

                // 원본 콜을 공유콜로 표시하고 내부 목록에 유지
                val origCallRef = firestore.collection("regions").document(region)
                    .collection("offices").document(office)
                    .collection("calls").document(callInfo.id)
                
                val callUpdates = mapOf(
                    "callType" to "SHARED",
                    "status" to "SHARED_WAITING", // 공유 대기 상태로 명확히 구분
                    "sourceSharedCallId" to docRef.id,
                    "departure_set" to departure,
                    "destination_set" to destination,
                    "fare_set" to fare,
                    "updatedAt" to Timestamp.now()
                )
                Log.d(TAG, "Updating original call with: $callUpdates")
                origCallRef.update(callUpdates).await()
                Log.d(TAG, "Original call updated successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error sharing call", e)
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
                        "targetRegionId" to region // 타겟 지역 업데이트(다지역 지원 대비)
                    ))
                }.await()
                Log.d(TAG, "Shared call claimed: $sharedCallId")
            } catch (e: Exception) {
                Log.e(TAG, "Error claiming shared call", e)
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
        Log.d(TAG, "▶ claimSharedCallWithDetails called. sharedCallId=$sharedCallId, driverId=$driverId")
        
        // 디버깅: 현재 드라이버 캐시의 ID들을 확인
        driverCache.values.forEach { driver ->
            Log.d(TAG, "▶ Driver in cache: ${driver.name} -> docId=${driver.id}, authUid=${driver.authUid}")
        }
        viewModelScope.launch {
            try {
                val region = _regionId.value ?: return@launch
                val office = _officeId.value ?: return@launch
                Log.d(TAG, "▶ TX START. driverId=$driverId")
                firestore.runTransaction { tx ->
                    val docRef = firestore.collection("shared_calls").document(sharedCallId)
                    val snap = tx.get(docRef)
                    if (!snap.exists()) {
                        throw Exception("공유콜 문서가 존재하지 않습니다: $sharedCallId")
                    }
                    val status = snap.getString("status")
                    Log.d(TAG, "▶ Current shared call status: $status")
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
                        "targetRegionId" to region // 다지역 확장 대비
                    )
                    driverId?.let { 
                        updateMap["claimedDriverId"] = it 
                        Log.d(TAG, "▶ Setting claimedDriverId: $it")
                        
                        // 기사의 authUid도 함께 저장
                        val driver = driverCache[it]
                        driver?.authUid?.let { authUid ->
                            updateMap["claimedDriverAuthUid"] = authUid
                            Log.d(TAG, "▶ Setting claimedDriverAuthUid: $authUid")
                        }
                    }
                    Log.d(TAG, "▶ Updating shared call with map: $updateMap")
                    tx.update(docRef, updateMap)
                }.await()
                Log.d(TAG, "✅ Shared call claimed with details: $sharedCallId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error claiming shared call with details: ${e.message}", e)
            }
        }
    }

    // 테스트용 포인트 문서 생성 함수
    fun createTestPointsDocument() {
        val region = _regionId.value ?: return
        val office = _officeId.value ?: return
        
        viewModelScope.launch {
            try {
                val pointsRef = firestore.collection("regions").document(region)
                    .collection("offices").document(office)
                    .collection("points").document("points")
                
                val testPointsData = hashMapOf(
                    "balance" to 1000,
                    "updatedAt" to Timestamp.now()
                )
                
                pointsRef.set(testPointsData).await()
                Log.d(TAG, "Test points document created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error creating test points document", e)
            }
        }
    }

    // 취소된 공유콜을 재공유로 전환
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
                
                Log.d(TAG, "Shared call reopened: $sharedCallId")
            } catch (e: Exception) {
                Log.e(TAG, "Error reopening shared call", e)
            }
        }
    }

    // 취소된 공유콜을 삭제
    fun deleteSharedCall(sharedCallId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("shared_calls").document(sharedCallId).delete().await()
                Log.d(TAG, "Shared call deleted: $sharedCallId")
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting shared call", e)
            }
        }
    }

    // PTT 관련 함수들
    fun handlePTTVolumeDown() {
        Log.d(TAG, "handlePTTVolumeDown called")
    }

    fun handlePTTVolumeUp() {
        Log.d(TAG, "handlePTTVolumeUp called")
    }

    fun isPTTConnected(): Boolean {
        return _pttStatus.value.isConnected
    }

    fun initializePTT() {
        Log.d(TAG, "initializePTT called")
        _pttStatus.value = PTTStatus(
            isConnected = true,
            connectionState = "Connected"
        )
    }

    fun showSharedCallNotificationFromId(callId: String) {
        Log.d(TAG, "showSharedCallNotificationFromId called with callId: $callId")
        // 공유콜 알림 표시 로직
    }

    fun adjustPTTVolume(delta: Int) {
        Log.d(TAG, "adjustPTTVolume called with delta: $delta")
    }

    fun showTripStartedPopup(driverName: String, driverPhone: String?, tripSummary: String, customerName: String) {
        Log.d(TAG, "showTripStartedPopup called")
        _tripStartedInfo.value = Triple(driverName, driverPhone, tripSummary)
        _showTripStartedPopup.value = true
    }

    fun showTripCompletedPopup(driverName: String, customerName: String) {
        Log.d(TAG, "showTripCompletedPopup called")
        _tripCompletedInfo.value = Pair(driverName, customerName)
        _showTripCompletedPopup.value = true
    }

    fun showCancelledCallPopup(driverName: String, customerName: String) {
        Log.d(TAG, "showCancelledCallPopup called")
        _canceledCallInfo.value = Pair(driverName, customerName)
        _showCanceledCallPopup.value = true
    }
}
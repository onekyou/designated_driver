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
        // 이미 같은 office를 리스닝 중이면 중복 시작하지 않음
        if (_regionId.value == regionId && _officeId.value == officeId && callsListener != null) {
            return
        }

        stopListening()
        val officeRef = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)

        officeRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val status = snapshot.getString("status") ?: "OPEN"
                _officeStatus.value = status
            } else {
                _officeStatus.value = "OPEN"

                officeRef.set(mapOf("status" to "OPEN"), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                    }
            }
        }

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

                                // 콜 디텍터에서 생성한 콜은 팝업 표시하지 않음
                                if (callInfo.fromCallDetector == true) {
                                    // 콜 디텍터에서 이미 팝업을 표시했으므로 무시
                                    Log.d(TAG, "Ignoring call from CallDetector: ${doc.id}")
                                } else if (callInfo.callType != "SHARED") {
                                    // 콜 매니저에서 생성했거나 fromCallDetector가 없는 경우만 팝업 표시
                                    // SharedPreferences로 이미 표시한 새 콜 팝업 체크
                                    val prefs = appContext.getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
                                    val popupId = "NEW_CALL_${doc.id}"

                                    if (!prefs.getBoolean(popupId, false)) {
                                        _newCallInfo.value = callInfo
                                        _showNewCallPopup.value = true

                                        // 표시한 팝업으로 마킹
                                        prefs.edit().putBoolean(popupId, true).apply()
                                    }
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
                                // SharedPreferences로 이미 표시한 완료 팝업 체크
                                val prefs = appContext.getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
                                val popupId = "TRIP_COMPLETED_${doc.id}"

                                if (!prefs.getBoolean(popupId, false)) {
                                    val driverName = if (callInfo.callType == "SHARED") {
                                        "공유 기사님"
                                    } else {
                                        callInfo.assignedDriverName ?: "기사"
                                    }
                                    val customerName: String = callInfo.customerName?.takeIf { it.isNotBlank() } ?: "고객"
                                    _tripCompletedInfo.value = Pair(driverName, customerName)
                                    _showTripCompletedPopup.value = true

                                    // 표시한 팝업으로 마킹
                                    prefs.edit().putBoolean(popupId, true).apply()
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
                if (e != null) {
                    return@addSnapshotListener
                }

                if (snapshots == null) {
                    return@addSnapshotListener
                }

                for (dc in snapshots.documentChanges) {
                    val doc = dc.document
                    try {
                        val driverInfo = doc.toObject(DriverInfo::class.java).apply { id = doc.id }
                        when (dc.type) {
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                driverCache[doc.id] = driverInfo
                            }
                            DocumentChange.Type.REMOVED -> {
                                driverCache.remove(doc.id)
                            }
                        }
                    } catch (parseEx: Exception) {
                    }
                }
                _drivers.value = driverCache.values.toList().sortedBy { it.name }
            }

        officeStatusListener = officeRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                _officeStatus.value = snapshot.getString("status") ?: ""
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

        pointsListener = officeRef.collection("points").document("points")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val pointsInfo = snapshot.toObject(PointsInfo::class.java)
                    _pointsInfo.value = pointsInfo
                } else {
                    _pointsInfo.value = PointsInfo(0, null)
                }
            }

        pointTransactionsListener = firestore.collection("point_transactions")
            .whereEqualTo("regionId", regionId)
            .whereEqualTo("officeId", officeId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                if (snapshots != null) {
                    val transactions = snapshots.documents.mapNotNull { doc ->
                        doc.toObject(PointTransaction::class.java)?.apply { id = doc.id }
                    }
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
            null
        }
    }

    fun assignCallToDriver(callInfo: CallInfo, driverId: String) {
        if (_regionId.value == null || _officeId.value == null) {
            return
        }
        val officePath = firestore.collection("regions").document(_regionId.value!!)
            .collection("offices").document(_officeId.value!!)

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
                val callUpdates = mapOf(
                    "assignedDriverId" to driverAuthUid,
                    "assignedDriverName" to driverInfo.name,
                    "status" to CallStatus.ASSIGNED.firestoreValue,
                    "updatedAt" to Timestamp.now()
                )
                callRef.update(callUpdates).await()

                val driverRef = officePath.collection("designated_drivers").document(driverId)
                driverRef.update("status", DriverStatus.ASSIGNED.value).await()

            } catch (e: Exception) {
                // TODO: Add user-facing error message
            }
        }
    }

    fun updateCallStatus(callId: String, newStatus: CallStatus) {
        if (_regionId.value == null || _officeId.value == null) {
            return
        }
        viewModelScope.launch {
            try {
                firestore.collection("regions").document(_regionId.value!!)
                    .collection("offices").document(_officeId.value!!)
                    .collection("calls").document(callId)
                    .update("status", newStatus.firestoreValue)
                    .await()
            } catch (e: Exception) {
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

                val callSnapshot = callRef.get().await()
                val assignedDriverAuthUid = callSnapshot.getString("assignedDriverId")

                callRef.update("status", CallStatus.COMPLETED.firestoreValue).await()

                if (!assignedDriverAuthUid.isNullOrBlank()) {
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

                officeRef.set(mapOf("status" to newStatus), com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener {
                    }
                    .addOnFailureListener { e ->
                        _officeStatus.value = currentStatus
                    }
            } catch (e: Exception) {
            }
        }
    }

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
            } catch (e: Exception) {
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
                firestore.collection("regions").document(region)
                    .collection("offices").document(office)
                    .collection("calls").document(callId)
                    .delete()
                    .await()

                callsCache.remove(callId)
                previousStatusMap.remove(callId)
                _calls.value = callsCache.values.toList()

                if (_newCallInfo.value?.id == callId) {
                    dismissNewCallPopup()
                }
            } catch (e: Exception) {
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
        // 이미 같은 region/office를 로드 중이면 중복 실행하지 않음
        if (_regionId.value == regionId && _officeId.value == officeId && callsListener != null) {
            return
        }

        stopListening()
        callsCache.clear()
        driverCache.clear()
        previousStatusMap.clear()
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
                    "targetRegionId" to region,
                    "createdBy" to (auth.currentUser?.uid ?: ""),
                    "phoneNumber" to callInfo.phoneNumber,
                    "originalCallId" to callInfo.id,
                    "timestamp" to Timestamp.now()
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
                val region = _regionId.value ?: return@launch
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
                        "targetRegionId" to region
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
            }
        }
    }

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
            } catch (e: Exception) {
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
}
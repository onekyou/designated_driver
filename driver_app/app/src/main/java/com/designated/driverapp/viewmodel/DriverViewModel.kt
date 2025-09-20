package com.designated.driverapp.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.driverapp.data.Constants
import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.CallStatus
import com.designated.driverapp.model.DriverStatus
import com.designated.driverapp.service.DriverForegroundService
import com.designated.driverapp.ui.state.DriverScreenUiState
import com.designated.driverapp.ui.state.LocationFetchStatus
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.Manifest

private const val TAG = "DriverViewModel"

@HiltViewModel
class DriverViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverScreenUiState())
    val uiState: StateFlow<DriverScreenUiState> = _uiState.asStateFlow()

    private val _callDetailsState = MutableStateFlow<CallInfo?>(null)
    val callDetails: StateFlow<CallInfo?> = _callDetailsState.asStateFlow()

    private var assignedCallsListener: ListenerRegistration? = null
    private var driverStatusListener: ListenerRegistration? = null
    private var completedCallsListener: ListenerRegistration? = null

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val geocoder: Geocoder = Geocoder(appContext, Locale.KOREA)

    private var boundService: DriverForegroundService? = null
    private var isBound = false
    private val popupPrefs = appContext.getSharedPreferences("driver_popup_prefs", Context.MODE_PRIVATE)
    private val handledSettlementIds: MutableSet<String> = popupPrefs.getStringSet("handled_settlement_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    private var fcmTokenToRegister: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DriverForegroundService.LocalBinder
            boundService = binder.getService()
            isBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            boundService = null
            isBound = false
        }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            stopListeners()
            _uiState.value = DriverScreenUiState()
        } else {
            tryAutoInitializeListeners(user.uid)
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
        bindDriverService()
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authStateListener)
        stopListeners()
        unbindDriverService()
    }

    private fun stopListeners() {
        assignedCallsListener?.remove()
        assignedCallsListener = null
        driverStatusListener?.remove()
        driverStatusListener = null
        completedCallsListener?.remove()
        completedCallsListener = null

    }

    fun initializeListenersWithInfo(regionId: String, officeId: String, driverId: String) {

        sharedPreferences.edit()
            .putString(Constants.PREF_KEY_REGION_ID, regionId)
            .putString(Constants.PREF_KEY_OFFICE_ID, officeId)
            .apply()

        fcmTokenToRegister?.let { token ->
            registerFcmToken(token)
        }

        if (auth.currentUser?.uid == driverId) {
            startListeningForDriverStatus(regionId, officeId, driverId)
            startListeningForAssignedCalls(regionId, officeId, driverId)
            startListeningForCompletedCalls(regionId, officeId, driverId)
        } else {
            _uiState.update { it.copy(errorMessage = "인증 정보가 일치하지 않습니다.") }
        }
    }

    private fun startListeningForDriverStatus(regionId: String, officeId: String, driverId: String) {
        driverStatusListener?.remove()
        val driverDocRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        driverStatusListener = driverDocRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    driverStatusListener?.remove()
                    driverStatusListener = null
                }
                _uiState.update { it.copy(errorMessage = "기사 상태를 불러오는 데 실패했습니다: ${e.message}") }
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val statusString = snapshot.getString(Constants.FIELD_STATUS)
                val status = DriverStatus.entries.find { it.value == statusString } ?: DriverStatus.OFFLINE
                _uiState.update { it.copy(driverStatus = status) }
            } else {
                _uiState.update { it.copy(driverStatus = DriverStatus.OFFLINE) }
            }
        }
    }

    private fun startListeningForAssignedCalls(regionId: String, officeId: String, driverId: String) {
        assignedCallsListener?.remove()
        val callsQuery = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS)
            .whereEqualTo(Constants.FIELD_ASSIGNED_DRIVER_ID, driverId)
            .whereIn(
                Constants.FIELD_STATUS, listOf(
                    Constants.STATUS_ASSIGNED, Constants.STATUS_ACCEPTED,
                    Constants.STATUS_IN_PROGRESS, Constants.STATUS_AWAITING_SETTLEMENT
                )
            )

        assignedCallsListener = callsQuery.addSnapshotListener { snapshot, e ->
            if (e != null) {
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    assignedCallsListener?.remove()
                    assignedCallsListener = null
                }
                _uiState.update { it.copy(errorMessage = "배차 목록을 불러오는 데 실패했습니다: ${e.message}") }
                return@addSnapshotListener
            }

            val calls = snapshot?.documents?.mapNotNull { doc ->
                try {
                    doc.toObject<CallInfo>()?.apply { id = doc.id }
                } catch (parseEx: Exception) {
                    null
                }
            } ?: emptyList()

            _uiState.update { currentState ->
                val activeCall = calls.firstOrNull { it.statusEnum != CallStatus.ASSIGNED && it.statusEnum != CallStatus.AWAITING_SETTLEMENT }
                val settlementCall = calls.firstOrNull { it.statusEnum == CallStatus.AWAITING_SETTLEMENT && !handledSettlementIds.contains(it.id) }

                val currentCallIds = currentState.assignedCalls.map { it.id }.toSet()
                val newAssignedCall = calls.find {
                    it.statusEnum == CallStatus.ASSIGNED && !currentCallIds.contains(it.id)
                }

                val shouldShowNewPopup = newAssignedCall != null && currentState.newCallPopup == null

                val currentPopupStillValid = currentState.newCallPopup?.let { popup ->
                    calls.any { it.id == popup.id && it.statusEnum == CallStatus.ASSIGNED }
                } ?: false

                val finalNewCallPopup = when {
                    shouldShowNewPopup -> newAssignedCall
                    currentState.newCallPopup != null && currentPopupStillValid -> currentState.newCallPopup
                    else -> null
                }

                currentState.copy(
                    assignedCalls = calls,
                    activeCall = activeCall,
                    callForSettlement = settlementCall,
                    newCallPopup = finalNewCallPopup,
                    navigateToHome = shouldShowNewPopup && !currentState.navigateToHome,
                    isLoading = false
                )
            }
        }
    }

    private fun startListeningForCompletedCalls(regionId: String, officeId: String, driverId: String) {
        completedCallsListener?.remove()
        val query = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS)
            .whereEqualTo(Constants.FIELD_ASSIGNED_DRIVER_ID, driverId)
            .whereEqualTo(Constants.FIELD_STATUS, Constants.STATUS_COMPLETED)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)

        completedCallsListener = query.addSnapshotListener { snapshots, e ->
            if (e != null) {
                _uiState.update { it.copy(errorMessage = "완료된 콜 목록을 불러오는 데 실패했습니다.") }
                return@addSnapshotListener
            }
            val calls = snapshots?.documents?.mapNotNull { doc ->
                try {
                    doc.toObject<CallInfo>()?.apply { id = doc.id }
                } catch (parseEx: Exception) {
                    null
                }
            } ?: emptyList()
            _uiState.update { it.copy(completedCalls = calls) }
        }
    }

    fun loadCallDetails(callId: String) {
        if (callId.isBlank()) {
            _callDetailsState.value = null
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (regionId, officeId) = getDriverLocationInfo()
                val callDocument = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                    .get()
                    .await()

                val callInfo = callDocument.toObject(CallInfo::class.java)?.copy(id = callDocument.id)
                _callDetailsState.value = callInfo

                if (callInfo == null) {
                    _uiState.update { it.copy(errorMessage = "콜 정보를 찾을 수 없습니다.") }
                } else {
                }
            } catch (e: IllegalStateException) {
                _uiState.update { it.copy(errorMessage = "드라이버 정보를 가져올 수 없습니다: ${e.message}") }
                _callDetailsState.value = null
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "콜 상세 정보를 불러오는 중 오류 발생: ${e.message}") }
                _callDetailsState.value = null
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 대리기사가 "새로운 콜" 팝업에서 수락 버튼을 눌렀을 때 호출.
     *  1) 콜 status → ACCEPTED 로 변경
     *  2) 기사용 status → ACCEPTED 로 변경
     *  3) 팝업을 닫고( newCallPopup=null ) 로컬 UI 업데이트
     *  Firestore 리스너가 activeCall 을 업데이트하므로 별도 fetch 는 생략.
     */
    fun acceptCall(callId: String) = performFirestoreUpdate {
        val (regionId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                val callRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)

                val driverRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)

                firestore.runTransaction { transaction ->
                    val callSnapshot = transaction.get(callRef)
                    if (!callSnapshot.exists()) {
                throw Exception("콜 문서를 찾을 수 없습니다.")
                    }
            val currentStatus = callSnapshot.getString(Constants.FIELD_STATUS)
            if (currentStatus == Constants.STATUS_ASSIGNED) {
                        transaction.update(callRef, Constants.FIELD_STATUS, Constants.STATUS_ACCEPTED)
                        transaction.update(driverRef, Constants.FIELD_STATUS, "PREPARING")
            }
        }.await()

        _uiState.update { current -> current.copy(newCallPopup = null) }
    }

    fun rejectCall(callId: String) {
        viewModelScope.launch {
        }
    }

    /**
     * 운행 준비 단계에서 운행을 취소하는 함수
     * - 내부콜: 콜 상태를 HOLD로 변경
     * - 공유콜: 콜 상태를 HOLD로 변경 + 원본 shared_calls를 OPEN으로 되돌림
     * - 기사 상태를 WAITING으로 변경
     * - assignedDriverId를 null로 변경하여 다른 기사가 배정받을 수 있도록 함
     */
    fun cancelTrip(callId: String, cancelReason: String = "운행취소") = performFirestoreUpdate {
        val (regionId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val callRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        val callSnapshot = callRef.get().await()
        val callInfo = callSnapshot.toObject<CallInfo>()

        if (callInfo?.callType == "SHARED") {

            callRef.update(mapOf(
                Constants.FIELD_STATUS to "CANCELLED_BY_DRIVER",
                "cancelReason" to cancelReason,
                "cancelledAt" to FieldValue.serverTimestamp(),
                "cancelledByDriver" to true
            )).await()

        } else {
            val callUpdates = mapOf(
                Constants.FIELD_STATUS to "HOLD",
                "assignedDriverId" to null,
                "assignedDriverName" to null,
                "assignedDriverPhone" to null,
                "cancelReason" to cancelReason,
                Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
            callRef.update(callUpdates).await()
        }

        val driverRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        driverRef.update(Constants.FIELD_STATUS, DriverStatus.WAITING.value).await()

        _uiState.update { current ->
            current.copy(
                activeCall = null,
                isLoading = false,
                navigateToHistorySettlement = false
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * TripPreparationScreen 에서 "운행 시작" 버튼 클릭 시 호출.
     *  1) 콜 문서에 출발지/도착지/경유지/요금 저장 + status → IN_PROGRESS
     *  2) 기사용 status → ON_TRIP
     *  3) 리스너가 activeCall 상태를 IN_PROGRESS 로 업데이트하도록 둔다.
     */
    fun startDriving(
        callId: String,
        departure: String,
        destination: String,
        waypoints: String,
        fare: Int
    ) = performFirestoreUpdate {
        val (regionId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val callRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        val tripSummary = "출발: $departure, 도착: $destination, 경유: ${waypoints.ifEmpty { "없음" }}, 요금: $fare 원"

        val callUpdates = mapOf(
            Constants.FIELD_STATUS to Constants.STATUS_IN_PROGRESS,
            "departure_set" to departure,
            "destination_set" to destination,
            "waypoints_set" to waypoints,
            "fare_set" to fare,
            "trip_summary" to tripSummary,
            Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )

        callRef.update(callUpdates).await()

        firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
            .update(Constants.FIELD_STATUS, DriverStatus.ON_TRIP.value).await()
    }

    fun completeCall(callId: String) = performFirestoreUpdate {
        val (regionId, officeId) = getDriverLocationInfo()
        val callRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        callRef.update(Constants.FIELD_STATUS, Constants.STATUS_AWAITING_SETTLEMENT).await()

        val updatedCallSnapshot = callRef.get().await()
        val completedCall = updatedCallSnapshot.toObject<CallInfo>()?.copy(id = updatedCallSnapshot.id)

        _uiState.update {
            it.copy(
                callForSettlement = completedCall,
                activeCall = null,
                isLoading = false
            )
        }
    }

    fun confirmAndFinalizeTrip(callId: String, paymentMethod: String, cashAmount: Int?, fareToSet: Int, tripSummaryToSet: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (regionId, officeId) = getDriverLocationInfo()
                val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                val tripData = hashMapOf<String, Any>(
                    Constants.FIELD_PAYMENT_METHOD to paymentMethod,
                    Constants.FIELD_STATUS to CallStatus.COMPLETED.firestoreValue,
                    Constants.FIELD_FARE_FINAL to fareToSet,
                    Constants.FIELD_TRIP_SUMMARY_FINAL to tripSummaryToSet,
                    Constants.FIELD_COMPLETED_AT to FieldValue.serverTimestamp()
                )
                if (paymentMethod == "현금" && cashAmount != null) {
                    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
                } else if (paymentMethod == "현금+포인트" && cashAmount != null) {
                    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
                    tripData["creditAmount"] = fareToSet - cashAmount
                }

                firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                    .update(tripData).await()

                firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)
                    .update(Constants.FIELD_STATUS, DriverStatus.WAITING.value).await()

                val callRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                val latestCallSnapshot = callRef.get().await()
                val latestCallInfo = latestCallSnapshot.toObject<CallInfo>()?.copy(id = latestCallSnapshot.id)

                saveTripToHistory(fareToSet, tripSummaryToSet, paymentMethod, cashAmount, latestCallInfo)

                _uiState.update { currentState ->
                    currentState.copy(
                        activeCall = null,
                        callForSettlement = null,
                        driverStatus = DriverStatus.WAITING,
                        navigateToHistorySettlement = true,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "정산 처리 중 오류: ${e.message}", isLoading = false) }
            }
        }
    }

    private fun saveTripToHistory(fare: Int, tripSummary: String, paymentMethod: String, cashAmount: Int?, callInfo: CallInfo?) {
        try {
            val prefs = appContext.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
            val historyJson = prefs.getString("history_list", "[]")
            val historyList = org.json.JSONArray(historyJson)

            val tripNumber = historyList.length() + 1

            val customerName = callInfo?.customerName ?: "고객"
            val departure = callInfo?.departure_set?.takeIf { it.isNotBlank() } ?: "출발지"
            val destination = callInfo?.destination_set?.takeIf { it.isNotBlank() } ?: "도착지"

            val paymentString = when (paymentMethod) {
                "현금" -> "현금"
                "외상" -> "외상"
                "이체" -> "이체"
                "현금+포인트" -> if (cashAmount != null) "현금+포인트(${String.format("%,d", cashAmount)}원 현금)" else "현금+포인트"
                "포인트" -> "포인트"
                else -> paymentMethod
            }

            // 운행내역 문자열 생성 (예: "1. 홍길동, 용문면→양평읍, 15,000원, 현금")
            val tripHistoryEntry = "$tripNumber. $customerName, $departure→$destination, ${String.format("%,d", fare)}원, $paymentString|timestamp=${System.currentTimeMillis()}"

            historyList.put(tripHistoryEntry)

            prefs.edit().putString("history_list", historyList.toString()).apply()

        } catch (e: Exception) {
        }
    }

    fun updateDriverStatus(newStatus: DriverStatus) = performFirestoreUpdate {
        val (regionId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
            .update(Constants.FIELD_STATUS, newStatus.value).await()
    }

    private suspend fun getAddressFromLocation(latitude: Double, longitude: Double): String? = withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(latitude, longitude, 1) { addresses ->
                        if (continuation.isActive) {
                            val address = addresses.firstOrNull()?.getAddressLine(0)
                            continuation.resume(address)
                        }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                addresses?.firstOrNull()?.getAddressLine(0)
            }
        } catch (e: IOException) {
            null
        }
    }

    fun dismissNewCallPopup() {
        _uiState.update { it.copy(newCallPopup = null) }
    }

    fun dismissSettlementPopup() {
        val id = _uiState.value.callForSettlement?.id
        _uiState.update { it.copy(callForSettlement = null) }
        if (id != null) {
            handledSettlementIds.add(id)
            popupPrefs.edit().putStringSet("handled_settlement_ids", handledSettlementIds).apply()
        }
    }

    fun requestSettlement() {
        _uiState.update {
            if (it.activeCall?.statusEnum == com.designated.driverapp.model.CallStatus.COMPLETED) {
                it.copy(callForSettlement = it.activeCall)
            } else {
                it
            }
        }
    }

    fun onNavigateToHomeHandled() {
        _uiState.update { it.copy(navigateToHome = false) }
    }

    fun onNavigateToHistorySettlementHandled() {
        _uiState.update { it.copy(navigateToHistorySettlement = false) }
    }

    fun onErrorMessageHandled() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun getDriverLocationInfo(): Pair<String, String> {
        val regionId = sharedPreferences.getString(Constants.PREF_KEY_REGION_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (regionId.isNullOrBlank() || officeId.isNullOrBlank()) {
            throw IllegalStateException("Region ID or Office ID is not set.")
        }
        return Pair(regionId, officeId)
    }

    private fun performFirestoreUpdate(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(errorMessage = null) }
            try {
                block()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다.") }
            }
        }
    }

    fun setFcmToken(token: String) {
        val regionId = sharedPreferences.getString(Constants.PREF_KEY_REGION_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (!regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            registerFcmToken(token)
        } else {
            fcmTokenToRegister = token
        }
    }

    private fun registerFcmToken(token: String) = performFirestoreUpdate {
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val (regionId, officeId) = getDriverLocationInfo()
        val driverRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
        driverRef.update(Constants.FIELD_FCM_TOKEN, token).await()
        fcmTokenToRegister = null
    }

    fun startDriverService() {
        val serviceIntent = Intent(appContext, DriverForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }
    }

    fun stopDriverService() {
        unbindDriverService()
        val serviceIntent = Intent(appContext, DriverForegroundService::class.java)
        appContext.stopService(serviceIntent)
    }

    private fun bindDriverService() {
        Intent(appContext, DriverForegroundService::class.java).also { intent ->
            appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindDriverService() {
        if (isBound) {
            appContext.unbindService(serviceConnection)
            isBound = false
        }
    }

    private fun tryAutoInitializeListeners(driverId: String) {
        if (assignedCallsListener != null || driverStatusListener != null) {
            return
        }

        val regionId = sharedPreferences.getString(Constants.PREF_KEY_REGION_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (!regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            startListeningForDriverStatus(regionId, officeId, driverId)
            startListeningForAssignedCalls(regionId, officeId, driverId)
            startListeningForCompletedCalls(regionId, officeId, driverId)
        } else {
        }
    }
}
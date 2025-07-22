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
import kotlinx.coroutines.delay

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

    // StateFlow for individual call details
    private val _callDetailsState = MutableStateFlow<CallInfo?>(null)
    val callDetails: StateFlow<CallInfo?> = _callDetailsState.asStateFlow()

    private var assignedCallsListener: ListenerRegistration? = null
    private var driverStatusListener: ListenerRegistration? = null
    // private var callDetailsListener: ListenerRegistration? = null // Replaced by loadCallDetails updating _callDetailsState
    private var completedCallsListener: ListenerRegistration? = null

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val geocoder: Geocoder = Geocoder(appContext, Locale.KOREA)

    private var boundService: DriverForegroundService? = null
    private var isBound = false
    // --- Popup duplication 방지용 (운행 완료 정산) ---
    private val popupPrefs = appContext.getSharedPreferences("driver_popup_prefs", Context.MODE_PRIVATE)
    private val handledSettlementIds: MutableSet<String> = popupPrefs.getStringSet("handled_settlement_ids", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    private var fcmTokenToRegister: String? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as DriverForegroundService.LocalBinder
            boundService = binder.getService()
            isBound = true
            Log.d(TAG, "DriverForegroundService connected.")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            boundService = null
            isBound = false
            Log.d(TAG, "DriverForegroundService disconnected.")
        }
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            Log.d(TAG, "AuthStateListener: User logged out. Stopping listeners and clearing state.")
            stopListeners()
            _uiState.value = DriverScreenUiState() // Reset to initial state
        } else {
            Log.d(TAG, "AuthStateListener: User logged in (${user.uid}).")
            tryAutoInitializeListeners(user.uid)
        }
    }

    init {
        Log.d(TAG, "ViewModel initialized.")
        auth.addAuthStateListener(authStateListener)
        bindDriverService()
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared. Removing listeners and unbinding service.")
        auth.removeAuthStateListener(authStateListener)
        stopListeners()
        unbindDriverService()
    }

    private fun stopListeners() {
        Log.d(TAG, "Stopping Firestore listeners.")
        assignedCallsListener?.remove()
        assignedCallsListener = null
        driverStatusListener?.remove()
        driverStatusListener = null
        // callDetailsListener?.remove() // Listener is no longer used
        // callDetailsListener = null
        completedCallsListener?.remove()
        completedCallsListener = null

        // ★★★ SharedPreferences는 지우지 않음 - 자동 초기화를 위해 보존 ★★★
        // sharedPreferences.edit().clear().apply()
        Log.d(TAG, "Stopped Firestore listeners (SharedPreferences preserved for auto-initialization).")
    }

    fun initializeListenersWithInfo(regionId: String, officeId: String, driverId: String) {
        Log.d(TAG, "Initializing listeners with Info: regionId=$regionId, officeId=$officeId, driverId=$driverId")

        sharedPreferences.edit()
            .putString(Constants.PREF_KEY_REGION_ID, regionId)
            .putString(Constants.PREF_KEY_OFFICE_ID, officeId)
            .apply()
        Log.d(TAG, "Saved regionId and officeId to SharedPreferences.")

        fcmTokenToRegister?.let { token ->
            Log.d(TAG, "Found a pending FCM token. Registering now.")
            registerFcmToken(token)
        }

        if (auth.currentUser?.uid == driverId) {
            startListeningForDriverStatus(regionId, officeId, driverId)
            startListeningForAssignedCalls(regionId, officeId, driverId)
            startListeningForCompletedCalls(regionId, officeId, driverId)
        } else {
            Log.e(TAG, "InitializeListenersWithInfo: Mismatch between auth.currentUser.uid and passed driverId. Listeners not started.")
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
                Log.w(TAG, "Driver status listener error", e)
                // ★★★ PERMISSION_DENIED 오류 시 리스너 제거하여 중복 오류 방지 ★★★
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    Log.w(TAG, "Permission denied, removing driver status listener")
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
                Log.d(TAG, "Driver status updated: $status")
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
                Log.e(TAG, "Assigned calls listener error", e)
                // ★★★ PERMISSION_DENIED 오류 시 리스너 제거하여 중복 오류 방지 ★★★
                if (e.message?.contains("PERMISSION_DENIED") == true) {
                    Log.w(TAG, "Permission denied, removing assigned calls listener")
                    assignedCallsListener?.remove()
                    assignedCallsListener = null
                }
                _uiState.update { it.copy(errorMessage = "배차 목록을 불러오는 데 실패했습니다: ${e.message}") }
                return@addSnapshotListener
            }

            val calls = snapshot?.documents?.mapNotNull { doc ->
                doc.toObject<CallInfo>()?.copy(id = doc.id)
            } ?: emptyList()

            _uiState.update { currentState ->
                val activeCall = calls.firstOrNull { it.statusEnum != CallStatus.ASSIGNED && it.statusEnum != CallStatus.AWAITING_SETTLEMENT }
                val settlementCall = calls.firstOrNull { it.statusEnum == CallStatus.AWAITING_SETTLEMENT && !handledSettlementIds.contains(it.id) }

                // ★★★ 새 배차 팝업 로직 개선 - 중복 방지 ★★★
                val currentCallIds = currentState.assignedCalls.map { it.id }.toSet()
                val newAssignedCall = calls.find {
                    it.statusEnum == CallStatus.ASSIGNED && !currentCallIds.contains(it.id)
                }

                // 새 콜이 있고, 현재 팝업이 없을 때만 팝업 표시
                // 팝업이 이미 있으면 유지 (사용자가 수락/거절할 때까지)
                val shouldShowNewPopup = newAssignedCall != null && currentState.newCallPopup == null

                // 현재 팝업이 있는데 해당 콜이 더 이상 ASSIGNED 상태가 아니면 팝업 제거
                val currentPopupStillValid = currentState.newCallPopup?.let { popup ->
                    calls.any { it.id == popup.id && it.statusEnum == CallStatus.ASSIGNED }
                } ?: false

                val finalNewCallPopup = when {
                    shouldShowNewPopup -> newAssignedCall
                    currentState.newCallPopup != null && currentPopupStillValid -> currentState.newCallPopup
                    else -> null
                }

                Log.d(TAG, "New call popup logic: newCall=${newAssignedCall?.id}, currentPopup=${currentState.newCallPopup?.id}, shouldShow=$shouldShowNewPopup, popupValid=$currentPopupStillValid, finalPopup=${finalNewCallPopup?.id}")

                currentState.copy(
                    assignedCalls = calls,
                    activeCall = activeCall,
                    callForSettlement = settlementCall,
                    newCallPopup = finalNewCallPopup,
                    navigateToHome = shouldShowNewPopup && !currentState.navigateToHome,
                    isLoading = false // 로딩 상태 해제
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
                Log.e(TAG, "Completed calls listener failed.", e)
                _uiState.update { it.copy(errorMessage = "완료된 콜 목록을 불러오는 데 실패했습니다.") }
                return@addSnapshotListener
            }
            val calls = snapshots?.documents?.mapNotNull { it.toObject<CallInfo>()?.copy(id = it.id) } ?: emptyList()
            _uiState.update { it.copy(completedCalls = calls) }
        }
    }

    fun loadCallDetails(callId: String) {
        Log.d(TAG, "Loading details for call: $callId")
        if (callId.isBlank()) {
            _callDetailsState.value = null // callId가 비어있으면 null로 설정
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
                _callDetailsState.value = callInfo // _callDetailsState 업데이트

                if (callInfo == null) {
                    Log.w(TAG, "Call not found with ID: $callId")
                    _uiState.update { it.copy(errorMessage = "콜 정보를 찾을 수 없습니다.") }
                } else {
                    Log.d(TAG, "Call details loaded: ${callInfo.id}")
                }
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to get driver location info for call details: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "드라이버 정보를 가져올 수 없습니다: ${e.message}") }
                _callDetailsState.value = null
            } catch (e: Exception) {
                Log.e(TAG, "Error loading call details for $callId", e)
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

        // 트랜잭션 기반으로 콜 상태와 기사 상태를 동시에 업데이트 (원본 로직 복원)
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

        // 팝업 즉시 닫기 (리스너가 activeCall 을 곧 업데이트함)
        _uiState.update { current -> current.copy(newCallPopup = null) }
    }

    fun rejectCall(callId: String) {
        viewModelScope.launch {
            // Implementation of rejectCall method
        }
    }

    // ★★★ 위치 권한 체크 함수 추가 ★★★
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

        // 운행 요약 문자열 (정산 화면에서 사용)
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

        // 콜 문서 업데이트
        callRef.update(callUpdates).await()

        // 기사 상태를 ON_TRIP 으로 변경 (DriverStatus.ON_TRIP 사용)
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

        // 리스너가 상태를 업데이트할 때까지 기다리지 않고 즉시 UI 상태를 변경하여 팝업을 표시
        val updatedCallSnapshot = callRef.get().await()
        val completedCall = updatedCallSnapshot.toObject<CallInfo>()?.copy(id = updatedCallSnapshot.id)

        _uiState.update {
            it.copy(
                callForSettlement = completedCall,
                activeCall = null, // 진행 중인 콜 정보에서 제거
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

                // 1. Prepare trip data and update call to SETTLED
                val tripData = hashMapOf<String, Any>(
                    Constants.FIELD_PAYMENT_METHOD to paymentMethod,
                    Constants.FIELD_STATUS to CallStatus.COMPLETED.firestoreValue, // Use COMPLETED as defined in CallStatus enum
                    Constants.FIELD_FARE_FINAL to fareToSet,
                    Constants.FIELD_TRIP_SUMMARY_FINAL to tripSummaryToSet,
                    Constants.FIELD_COMPLETED_AT to FieldValue.serverTimestamp()
                )
                if (paymentMethod == "현금" && cashAmount != null) {
                    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
                } else if (paymentMethod == "현금+포인트" && cashAmount != null) {
                    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
                    // 포인트 금액만 외상으로 처리
                    tripData["creditAmount"] = fareToSet - cashAmount
                } else if (paymentMethod == "외상") {
                    tripData["creditAmount"] = fareToSet
                }

                firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                    .update(tripData).await()

                // 2. Update driver's status to WAITING in Firestore
                firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)
                    .update(Constants.FIELD_STATUS, DriverStatus.WAITING.value).await() // Ensure DriverStatus enum is used

                // 3. Wait for settlementId creation then mark as SETTLED
                val callRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)

                var settlementId: String? = null
                repeat(5) { attempt ->
                    val snap = callRef.get().await()
                    settlementId = snap.getString(Constants.FIELD_SETTLEMENT_ID)
                    if (!settlementId.isNullOrBlank()) return@repeat
                    delay(1000) // 1초 대기 후 재시도 (최대 5초)
                }

                if (!settlementId.isNullOrBlank()) {
                    // 현금 / 현금+포인트 결제만 운전기사가 즉시 'SETTLED' 처리한다.
                    if (paymentMethod != "이체" && paymentMethod != "외상") {
                        try {
                            val settlementRef = firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
                                .collection(Constants.COLLECTION_OFFICES).document(officeId)
                                .collection(Constants.COLLECTION_SETTLEMENTS).document(settlementId!!)
                            settlementRef.update(Constants.FIELD_SETTLEMENT_STATUS, Constants.SETTLEMENT_STATUS_SETTLED).await()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to update settlementStatus to SETTLED for $settlementId", e)
                        }
                    }
                } else {
                    Log.w(TAG, "settlementId not found within timeout for call $callId; driver must settle manually later")
                }

                // 4. Get latest call info from Firestore and save trip history
                val latestCallSnapshot = callRef.get().await()
                val latestCallInfo = latestCallSnapshot.toObject<CallInfo>()?.copy(id = latestCallSnapshot.id)
                
                saveTripToHistory(fareToSet, tripSummaryToSet, paymentMethod, cashAmount, latestCallInfo)

                // 5. Update local UI state comprehensively
                _uiState.update { currentState ->
                    currentState.copy(
                        activeCall = null,
                        callForSettlement = null,
                        driverStatus = DriverStatus.WAITING,
                        navigateToHistorySettlement = true,
                        isLoading = false
                    )
                }
                Log.d(TAG, "Trip $callId finalized, driver status set to WAITING.")

            } catch (e: Exception) {
                Log.e(TAG, "Error in confirmAndFinalizeTrip for $callId", e)
                _uiState.update { it.copy(errorMessage = "정산 처리 중 오류: ${e.message}", isLoading = false) }
            }
        }
    }

    private fun saveTripToHistory(fare: Int, tripSummary: String, paymentMethod: String, cashAmount: Int?, callInfo: CallInfo?) {
        try {
            val prefs = appContext.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
            val historyJson = prefs.getString("history_list", "[]")
            val historyList = org.json.JSONArray(historyJson)
            
            // 현재 운행내역 개수 계산
            val tripNumber = historyList.length() + 1
            
            // 고객 정보 가져오기 (매개변수로 받은 callInfo에서)
            val customerName = callInfo?.customerName ?: "고객"
            val departure = callInfo?.departure_set?.takeIf { it.isNotBlank() } ?: "출발지"
            val destination = callInfo?.destination_set?.takeIf { it.isNotBlank() } ?: "도착지"
            
            Log.d(TAG, "saveTripToHistory - callInfo: customerName=${callInfo?.customerName}, departure_set=${callInfo?.departure_set}, destination_set=${callInfo?.destination_set}")
            Log.d(TAG, "saveTripToHistory - final values: customerName=$customerName, departure=$departure, destination=$destination")
            
            // 결제 방법 문자열 생성
            val paymentString = when (paymentMethod) {
                "현금" -> "현금"
                "외상" -> "외상"
                "카드" -> "카드"
                "현금+포인트" -> if (cashAmount != null) "현금+포인트(${String.format("%,d", cashAmount)}원 현금)" else "현금+포인트"
                "포인트" -> "포인트"
                else -> paymentMethod
            }
            
            // 운행내역 문자열 생성 (예: "1. 홍길동, 용문면→양평읍, 15,000원, 현금")
            val tripHistoryEntry = "$tripNumber. $customerName, $departure→$destination, ${String.format("%,d", fare)}원, $paymentString|timestamp=${System.currentTimeMillis()}"
            
            // 새 운행내역 추가
            historyList.put(tripHistoryEntry)
            
            // SharedPreferences에 저장
            prefs.edit().putString("history_list", historyList.toString()).apply()
            
            Log.d(TAG, "Trip history saved: $tripHistoryEntry")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving trip history", e)
        }
    }

    fun updateDriverStatus(newStatus: DriverStatus) = performFirestoreUpdate {
        val (regionId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        firestore.collection(Constants.COLLECTION_REGIONS).document(regionId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
            .update(Constants.FIELD_STATUS, newStatus.value).await()
        // _uiState.update { it.copy(driverStatus = newStatus) } // This is handled by startListeningForDriverStatus
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
            Log.e(TAG, "Geocoder failed", e)
            null
        }
    }

    // UI Event Handlers
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

    // ★★★ 히스토리/정산 스크린 네비게이션 처리 함수 추가 ★★★
    fun onNavigateToHistorySettlementHandled() {
        _uiState.update { it.copy(navigateToHistorySettlement = false) }
    }

    fun onErrorMessageHandled() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    // Helper Functions
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
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Firestore operation failed", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다.") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
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

    // Service Handling
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

    // ★★★ 자동 리스너 초기화 함수 추가 ★★★
    private fun tryAutoInitializeListeners(driverId: String) {
        // ★★★ 이미 리스너가 활성화되어 있으면 중복 생성 방지 ★★★
        if (assignedCallsListener != null || driverStatusListener != null) {
            Log.d(TAG, "Listeners already active, skipping auto-initialization")
            return
        }
        
        val regionId = sharedPreferences.getString(Constants.PREF_KEY_REGION_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)
        
        if (!regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            Log.d(TAG, "Auto-initializing listeners with cached info: regionId=$regionId, officeId=$officeId, driverId=$driverId")
            startListeningForDriverStatus(regionId, officeId, driverId)
            startListeningForAssignedCalls(regionId, officeId, driverId)
            startListeningForCompletedCalls(regionId, officeId, driverId)
        } else {
            Log.d(TAG, "Cannot auto-initialize listeners: missing regionId or officeId in SharedPreferences")
        }
    }
}
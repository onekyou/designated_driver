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
import com.designated.driverapp.data.repository.CustomerPointsRepository
import com.designated.driverapp.data.repository.SettlementRepository
import com.designated.driverapp.data.settlement.CallSettlement
import com.designated.driverapp.data.settlement.CarryOverStatus
import com.designated.driverapp.data.settlement.DailySettlementStatus
import com.designated.driverapp.data.settlement.DriverCarryOver
import com.designated.driverapp.data.settlement.DriverDailySettlement
import com.google.firebase.Timestamp
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
    private val sharedPreferences: SharedPreferences,
    private val customerPointsRepository: CustomerPointsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverScreenUiState())
    val uiState: StateFlow<DriverScreenUiState> = _uiState.asStateFlow()

    private val _callDetailsState = MutableStateFlow<CallInfo?>(null)
    val callDetails: StateFlow<CallInfo?> = _callDetailsState.asStateFlow()

    // FCM 알림으로 받은 callId를 전달하기 위한 StateFlow
    private val _notificationCallId = MutableStateFlow<String?>(null)
    val notificationCallId: StateFlow<String?> = _notificationCallId.asStateFlow()

    // 이월 정산 (미수령금) StateFlow
    private val _carryOver = MutableStateFlow<DriverCarryOver?>(null)
    val carryOver: StateFlow<DriverCarryOver?> = _carryOver.asStateFlow()

    // 분배비율 (Firestore offices에서 읽기)
    private val _depositRatio = MutableStateFlow(60)
    val depositRatio: StateFlow<Int> = _depositRatio.asStateFlow()

    // 마지막 마감 시점 (최초 1회 로드, tripHistory 필터링용)
    private val _lastClearedMillis = MutableStateFlow(0L)
    val lastClearedMillis: StateFlow<Long> = _lastClearedMillis.asStateFlow()

    // 콜 수락 중 (중복 클릭 방지)
    private val _isAccepting = MutableStateFlow(false)
    val isAccepting: StateFlow<Boolean> = _isAccepting.asStateFlow()

    // 업무마감 중 (중복 클릭 방지)
    private val _isSubmittingSettlement = MutableStateFlow(false)
    val isSubmittingSettlement: StateFlow<Boolean> = _isSubmittingSettlement.asStateFlow()

    // calls 기반 오늘 정산 데이터
    data class TodaySettlement(
        val totalFare: Int = 0,           // 총 운행료
        val driverShare: Int = 0,         // 내 수익 (기사몫)
        val cashReceived: Int = 0,        // 현금 수령액
        val realDeposit: Int = 0,         // 실 납부액 (현금수령 - 기사몫)
        val tripCount: Int = 0,           // 운행 횟수
        val totalCredit: Int = 0,         // 총 외상 (현금 제외 금액)
        val officeDeposit: Int = 0        // 총 납입액 (사무실 몫)
    )
    private val _todaySettlement = MutableStateFlow(TodaySettlement())
    val todaySettlement: StateFlow<TodaySettlement> = _todaySettlement.asStateFlow()

    // 운행내역 카드용 개별 콜 목록 (Firestore 기반)
    data class TripHistoryItem(
        val tripNumber: Int,
        val customerName: String,
        val departure: String,
        val destination: String,
        val fare: Int,
        val paymentMethod: String,
        val cashAmount: Int?,
        val timestamp: Long
    ) {
        // 운행내역 카드 표시용 문자열 변환
        fun toDisplayString(): String {
            val paymentString = when {
                paymentMethod == "현금" -> "현금"
                paymentMethod == "외상" -> "외상"
                paymentMethod == "이체" -> "이체"
                paymentMethod.startsWith("현금+") && cashAmount != null ->
                    "현금+포인트(${String.format("%,d", cashAmount)}원 현금)"
                paymentMethod == "포인트" -> "포인트"
                else -> paymentMethod
            }
            return "$tripNumber. $customerName, $departure→$destination, ${String.format("%,d", fare)}원, $paymentString"
        }
    }
    private val _tripHistoryList = MutableStateFlow<List<TripHistoryItem>>(emptyList())
    val tripHistoryList: StateFlow<List<TripHistoryItem>> = _tripHistoryList.asStateFlow()

    private var assignedCallsListener: ListenerRegistration? = null
    private var driverStatusListener: ListenerRegistration? = null
    private var completedCallsListener: ListenerRegistration? = null
    private var carryOverListener: ListenerRegistration? = null

    private val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val geocoder: Geocoder = Geocoder(appContext, Locale.KOREA)

    // 정산 동기화 Repository
    private val settlementRepository: SettlementRepository by lazy {
        SettlementRepository.getInstance(appContext)
    }

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
        carryOverListener?.remove()
        carryOverListener = null
    }

    fun initializeListenersWithInfo(provinceId: String, cityId: String, officeId: String, driverId: String) {
        sharedPreferences.edit()
            .putString(Constants.PREF_KEY_PROVINCE_ID, provinceId)
            .putString(Constants.PREF_KEY_CITY_ID, cityId)
            .putString(Constants.PREF_KEY_OFFICE_ID, officeId)
            .apply()

        fcmTokenToRegister?.let { token ->
            registerFcmToken(token)
        }

        if (auth.currentUser?.uid == driverId) {
            // ✅ 리스너 대신 1회 조회로 현재 운행 중인 콜 확인 (앱 재시작 시 복구)
            loadCurrentActiveCall(provinceId, cityId, officeId, driverId)
            // ✅ 이월 정산 (미수령금) 리스너 시작
            startCarryOverListener(provinceId, cityId, officeId, driverId)
            // ✅ 분배비율 + 오늘 정산 로드 (calls 기반)
            loadSettlementData(provinceId, cityId, officeId, driverId)
        } else {
            _uiState.update { it.copy(errorMessage = "인증 정보가 일치하지 않습니다.") }
        }
    }

    /**
     * 앱 시작 시 현재 운행 중인 콜이 있는지 확인 (1회 조회)
     * 정상 출근: 조회 결과 없음 → 빈 화면
     * 앱 재시작: 운행 중인 콜 있음 → 화면에 표시
     */
    private fun loadCurrentActiveCall(provinceId: String, cityId: String, officeId: String, driverId: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                // ✅ 1. 기사 상태 조회
                val driverDoc = firestore
                    .collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)
                    .get()
                    .await()

                val driverStatus = driverDoc.getString(Constants.FIELD_STATUS)
                    ?.let { DriverStatus.entries.find { ds -> ds.value == it } }
                    ?: DriverStatus.OFFLINE

                // ✅ 2. 현재 배정된 콜 조회 (ASSIGNED, ACCEPTED, IN_PROGRESS, AWAITING_SETTLEMENT)
                val assignedCallsSnapshot = firestore
                    .collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS)
                    .whereEqualTo(Constants.FIELD_ASSIGNED_DRIVER_ID, driverId)
                    .whereIn(Constants.FIELD_STATUS, listOf(
                        Constants.STATUS_ASSIGNED,
                        Constants.STATUS_ACCEPTED,
                        Constants.STATUS_IN_PROGRESS,
                        Constants.STATUS_AWAITING_SETTLEMENT
                    ))
                    .get()
                    .await()

                val assignedCalls = assignedCallsSnapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject<CallInfo>()?.apply { id = doc.id }
                    } catch (e: Exception) {
                        null
                    }
                }

                // ✅ 3. UI 상태 업데이트
                if (assignedCalls.isNotEmpty()) {
                    val activeCall = assignedCalls.firstOrNull {
                        it.statusEnum == CallStatus.ACCEPTED || it.statusEnum == CallStatus.IN_PROGRESS
                    }
                    val newCall = assignedCalls.firstOrNull { it.statusEnum == CallStatus.ASSIGNED }
                    val settlementCall = assignedCalls.firstOrNull {
                        it.statusEnum == CallStatus.AWAITING_SETTLEMENT &&
                        !handledSettlementIds.contains(it.id)
                    }

                    _uiState.update { currentState ->
                        currentState.copy(
                            driverStatus = driverStatus,
                            assignedCalls = assignedCalls,
                            activeCall = activeCall,
                            newCallPopup = newCall,
                            callForSettlement = settlementCall,
                            isLoading = false
                        )
                    }

                    Log.d(TAG, "✅ 앱 시작: 기사 상태=${driverStatus.value}, 운행 중인 콜 ${assignedCalls.size}개 로드됨")
                } else {
                    // 배정된 콜 없음 → 빈 화면 (기사 상태는 반영)
                    _uiState.update {
                        it.copy(
                            driverStatus = driverStatus,
                            isLoading = false
                        )
                    }
                    Log.d(TAG, "✅ 앱 시작: 기사 상태=${driverStatus.value}, 배정된 콜 없음")
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "초기 상태 로드 실패: ${e.message}",
                        isLoading = false
                    )
                }
                Log.e(TAG, "❌ 앱 시작: 초기 상태 로드 실패", e)
            }
        }
    }

    // ✅ 리스너 함수들을 삭제하고 낙관적 업데이트 방식으로 전환
    // 비용 절감: 월 $414 → $0.03 (99.9% 절감)

    fun loadCallDetails(callId: String) {
        if (callId.isBlank()) {
            _callDetailsState.value = null
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val callDocument = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
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
    fun acceptCall(callId: String) {
        // 중복 클릭 방지
        if (_isAccepting.value) {
            Log.w(TAG, "⚠️ 이미 콜 수락 진행 중입니다. 중복 요청 무시.")
            return
        }
        _isAccepting.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
        Log.d(TAG, "🔵 acceptCall 시작 - callId: $callId")

        val (provinceId, cityId, officeId) = getDriverLocationInfo()
        Log.d(TAG, "🔵 Location Info - provinceId: $provinceId, cityId: $cityId, officeId: $officeId")

        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        Log.d(TAG, "🔵 Driver ID: $driverId")

        // ✅ 1단계: 즉시 로컬 UI 업데이트 (리스너 기다리지 않음)
        Log.d(TAG, "🔵 1단계: 로컬 UI 업데이트 시작")
        _uiState.update { currentState ->
            val acceptedCall = currentState.assignedCalls.find { it.id == callId }
            Log.d(TAG, "🔵 찾은 콜: ${acceptedCall?.id}, 상태: ${acceptedCall?.status}")
            acceptedCall?.let { call ->
                Log.d(TAG, "🔵 UI 업데이트 - 콜을 ACCEPTED로 변경")
                currentState.copy(
                    assignedCalls = currentState.assignedCalls.map {
                        if (it.id == callId) it.copy(status = Constants.STATUS_ACCEPTED)
                        else it
                    },
                    activeCall = call.copy(status = Constants.STATUS_ACCEPTED),
                    newCallPopup = null,
                    driverStatus = DriverStatus.ACCEPTED
                )
            } ?: run {
                Log.w(TAG, "⚠️ assignedCalls에서 콜을 찾을 수 없음")
                currentState.copy(newCallPopup = null)
            }
        }
        Log.d(TAG, "🔵 1단계 완료")

        // ✅ 2단계: Firestore 업데이트 (백그라운드)
        Log.d(TAG, "🔵 2단계: Firestore 업데이트 시작")
        val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)
        Log.d(TAG, "🔵 Call Ref 경로: provinces/$provinceId/cities/$cityId/offices/$officeId/calls/$callId")

        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
        Log.d(TAG, "🔵 Driver Ref 경로: provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers/$driverId")

        Log.d(TAG, "🔵 Transaction 시작")
        firestore.runTransaction { transaction ->
            Log.d(TAG, "🔵 Transaction 내부 - 콜 문서 읽기")
            val callSnapshot = transaction.get(callRef)

            if (!callSnapshot.exists()) {
                Log.e(TAG, "❌ 콜 문서가 존재하지 않음")
                throw Exception("콜 문서를 찾을 수 없습니다.")
            }

            val callData = callSnapshot.data
            Log.d(TAG, "🔵 콜 문서 데이터: $callData")

            val currentStatus = callSnapshot.getString(Constants.FIELD_STATUS)
            Log.d(TAG, "🔵 현재 콜 상태: $currentStatus")

            if (currentStatus == Constants.STATUS_ASSIGNED) {
                Log.d(TAG, "🔵 Transaction - 콜 상태를 ACCEPTED로 업데이트")
                transaction.update(callRef, Constants.FIELD_STATUS, Constants.STATUS_ACCEPTED)

                Log.d(TAG, "🔵 Transaction - 기사 상태를 PREPARING으로 업데이트")
                transaction.update(driverRef, Constants.FIELD_STATUS, "PREPARING")
            } else {
                Log.w(TAG, "⚠️ 콜 상태가 ASSIGNED가 아님: $currentStatus")
            }
        }.await()
        Log.d(TAG, "🔵 Transaction 완료")

        Log.d(TAG, "✅ 콜 수락 완료: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 콜 수락 실패: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "콜 수락 중 오류가 발생했습니다.") }
            } finally {
                _isAccepting.value = false
            }
        }
    }

    fun rejectCall(callId: String) = performFirestoreUpdate {
        val (provinceId, cityId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        // 콜 상태를 WAITING으로 되돌려 재배차 가능하게 함
        val callUpdates = mapOf(
            Constants.FIELD_STATUS to Constants.STATUS_WAITING,
            "assignedDriverId" to null,
            "assignedDriverName" to null,
            "assignedDriverPhone" to null,
            "rejectedByDriver" to driverId,
            Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        callRef.update(callUpdates).await()

        // 기사 상태를 WAITING으로 복구
        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
        driverRef.update(Constants.FIELD_STATUS, DriverStatus.WAITING.value).await()

        // UI 정리: 팝업 닫기, assignedCalls에서 제거, 기사 상태 복구
        _uiState.update { current ->
            current.copy(
                assignedCalls = current.assignedCalls.filter { it.id != callId },
                newCallPopup = null,
                activeCall = if (current.activeCall?.id == callId) null else current.activeCall,
                driverStatus = DriverStatus.WAITING
            )
        }

        Log.d(TAG, "콜 거절 완료: callId=$callId")
    }

    /**
     * 운행 준비 단계에서 운행을 취소하는 함수
     * - 콜 상태를 HOLD로 변경 (내부콜/공유콜 동일)
     * - 기사 상태를 WAITING으로 변경
     * - assignedDriverId를 null로 변경하여 다른 기사가 배정받을 수 있도록 함
     */
    fun cancelTrip(callId: String, cancelReason: String = "운행취소") = performFirestoreUpdate {
        val (provinceId, cityId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        // 내부콜/공유콜 모두 동일하게 HOLD로 처리 (다른 기사에게 재배차 가능)
        val callUpdates = mapOf(
            Constants.FIELD_STATUS to "HOLD",
            "assignedDriverId" to null,
            "assignedDriverName" to null,
            "assignedDriverPhone" to null,
            "cancelReason" to cancelReason,
            "cancelledByDriver" to true,
            Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )
        callRef.update(callUpdates).await()

        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        driverRef.update(Constants.FIELD_STATUS, DriverStatus.WAITING.value).await()

        _uiState.update { current ->
            current.copy(
                activeCall = null,
                driverStatus = DriverStatus.WAITING,
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
        val (provinceId, cityId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val tripSummary = "출발: $departure, 도착: $destination, 경유: ${waypoints.ifEmpty { "없음" }}, 요금: $fare 원"

        // ✅ 1단계: 즉시 로컬 UI 업데이트
        _uiState.update { currentState ->
            currentState.copy(
                activeCall = currentState.activeCall?.copy(
                    status = Constants.STATUS_IN_PROGRESS,
                    departure_set = departure,
                    destination_set = destination,
                    waypoints_set = waypoints,
                    fare_set = fare,
                    trip_summary = tripSummary
                ),
                driverStatus = DriverStatus.ON_TRIP,
                assignedCalls = currentState.assignedCalls.map {
                    if (it.id == callId) it.copy(status = Constants.STATUS_IN_PROGRESS)
                    else it
                }
            )
        }

        // ✅ 2단계: Firestore 업데이트 (백그라운드)
        val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

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

        firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
            .update(Constants.FIELD_STATUS, DriverStatus.ON_TRIP.value).await()

        Log.d(TAG, "✅ 운행 시작 완료: $callId")
    }

    fun completeCall(callId: String) = performFirestoreUpdate {
        val (provinceId, cityId, officeId) = getDriverLocationInfo()

        // ✅ 1단계: 즉시 로컬 UI 업데이트 (읽기 제거)
        _uiState.update { currentState ->
            val completedCall = currentState.activeCall?.copy(
                status = Constants.STATUS_AWAITING_SETTLEMENT
            )
            currentState.copy(
                activeCall = null,
                callForSettlement = completedCall,
                assignedCalls = currentState.assignedCalls.map {
                    if (it.id == callId) it.copy(status = Constants.STATUS_AWAITING_SETTLEMENT)
                    else it
                },
                isLoading = false
            )
        }

        // ✅ 2단계: Firestore 업데이트 (백그라운드, 읽기 없음)
        val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        callRef.update(Constants.FIELD_STATUS, Constants.STATUS_AWAITING_SETTLEMENT).await()

        Log.d(TAG, "✅ 운행 완료: $callId (정산 대기)")
    }

    fun confirmAndFinalizeTrip(
        callId: String,
        paymentMethod: String,
        cashAmount: Int?,
        fareToSet: Int,
        tripSummaryToSet: String,
        pointsToUse: Int = 0  // ✅ 추가: 포인트 사용액
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                // ✅ 추가: 콜 정보 조회하여 앱 회원 여부 확인
                val callDoc = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                    .get()
                    .await()

                val isAppCustomer = callDoc.getBoolean("isAppCustomer") ?: false
                val phoneNumber = callDoc.getString("phoneNumber")

                // 포인트 적립은 Cloud Functions에서 일원화 처리 (BUG-D12 수정)
                // Driver App에서는 적립하지 않음 - CF의 notifyCustomerOnComplete에서 처리

                val tripData = hashMapOf<String, Any>(
                    Constants.FIELD_PAYMENT_METHOD to paymentMethod,
                    Constants.FIELD_STATUS to CallStatus.COMPLETED.firestoreValue,
                    Constants.FIELD_FARE_FINAL to fareToSet,
                    "fare" to fareToSet,  // ✅ 추가: 고객앱 호환성
                    Constants.FIELD_TRIP_SUMMARY_FINAL to tripSummaryToSet,
                    Constants.FIELD_COMPLETED_AT to FieldValue.serverTimestamp(),
                    "pointsUsed" to pointsToUse,  // ✅ 추가: 사용한 포인트
                    "finalFare" to (fareToSet - pointsToUse)  // ✅ 추가: 최종 결제 금액
                )
                if (paymentMethod == "현금" && cashAmount != null) {
                    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
                } else if (paymentMethod == "현금+포인트" && cashAmount != null) {
                    tripData[Constants.FIELD_CASH_RECEIVED] = cashAmount
                    tripData["creditAmount"] = fareToSet - cashAmount
                }

                firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                    .update(tripData).await()

                firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)
                    .update(Constants.FIELD_STATUS, DriverStatus.WAITING.value).await()

                val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                val latestCallSnapshot = callRef.get().await()
                val latestCallInfo = latestCallSnapshot.toObject<CallInfo>()?.copy(id = latestCallSnapshot.id)

                // ✅ settlementSessions 저장은 Cloud Function이 담당
                // calls 컬렉션 업데이트 → Cloud Function 트리거 → settlementSessions 자동 생성
                Log.d(TAG, "운행완료 저장 완료 - Cloud Function이 정산 세션 처리 예정: $callId")

                // ✅ 로컬 정산 데이터 즉시 업데이트 (콜매니저와 동일한 계산 로직)
                val ratio = _depositRatio.value
                val newCashReceived = when {
                    paymentMethod == "현금" -> fareToSet
                    paymentMethod.startsWith("현금+") -> cashAmount ?: 0
                    else -> 0
                }
                val newDriverShare = (fareToSet * (100 - ratio) / 100.0).toInt()

                _todaySettlement.update { current ->
                    val updatedTotalFare = current.totalFare + fareToSet
                    val updatedCashReceived = current.cashReceived + newCashReceived
                    val updatedDriverShare = current.driverShare + newDriverShare
                    val updatedOfficeDeposit = (updatedTotalFare * ratio / 100.0).toInt()
                    val updatedTotalCredit = updatedTotalFare - updatedCashReceived
                    current.copy(
                        totalFare = updatedTotalFare,
                        driverShare = updatedDriverShare,
                        cashReceived = updatedCashReceived,
                        realDeposit = updatedCashReceived - updatedDriverShare,
                        tripCount = current.tripCount + 1,
                        totalCredit = updatedTotalCredit,
                        officeDeposit = updatedOfficeDeposit
                    )
                }

                // ✅ 운행내역 목록에 즉시 추가 (SharedPreferences 대신 StateFlow 사용)
                val customerName = latestCallInfo?.customerName ?: "고객"
                val departure = latestCallInfo?.departure_set?.takeIf { it.isNotBlank() } ?: "출발지"
                val destination = latestCallInfo?.destination_set?.takeIf { it.isNotBlank() } ?: "도착지"
                val newTripNumber = _tripHistoryList.value.size + 1

                val newTripItem = TripHistoryItem(
                    tripNumber = newTripNumber,
                    customerName = customerName,
                    departure = departure,
                    destination = destination,
                    fare = fareToSet,
                    paymentMethod = paymentMethod,
                    cashAmount = cashAmount,
                    timestamp = System.currentTimeMillis()
                )
                _tripHistoryList.update { current -> listOf(newTripItem) + current }

                Log.d(TAG, "로컬 정산 즉시 업데이트: fare=$fareToSet, cash=$newCashReceived, share=$newDriverShare, tripHistorySize=${_tripHistoryList.value.size}")

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

    // ✅ saveToSettlementSession 제거됨
    // 정산 세션 저장은 Cloud Function (onCallCompletedUpdateSettlement)이 담당
    // 기사앱은 calls 컬렉션만 업데이트하면 됨

    fun updateDriverStatus(newStatus: DriverStatus) = performFirestoreUpdate {
        Log.d(TAG, "🟡 [STATUS UPDATE] updateDriverStatus 호출됨 - 새 상태: ${newStatus.value}")
        Log.d(TAG, "🟡 [STATUS UPDATE] 호출 스택:", Exception("Stack trace"))

        val (provinceId, cityId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        Log.d(TAG, "🟡 [STATUS UPDATE] 경로: provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers/$driverId")

        // ✅ 1단계: 즉시 로컬 UI 업데이트
        _uiState.update { it.copy(driverStatus = newStatus) }

        // ✅ 2단계: Firestore 업데이트 (백그라운드)
        firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)
            .update(Constants.FIELD_STATUS, newStatus.value).await()

        Log.d(TAG, "✅ [STATUS UPDATE] Firestore 업데이트 완료: ${newStatus.value}")
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

    /**
     * 콜 취소 FCM 수신 시 호출
     * 해당 callId를 assignedCalls에서 제거하고, 관련 팝업/activeCall을 정리한다
     */
    fun handleCallCancelled(callId: String) {
        Log.d(TAG, "handleCallCancelled: callId=$callId")
        _uiState.update { currentState ->
            val updatedCalls = currentState.assignedCalls.filter { it.id != callId }
            val clearPopup = currentState.newCallPopup?.id == callId
            val clearActive = currentState.activeCall?.id == callId

            currentState.copy(
                assignedCalls = updatedCalls,
                newCallPopup = if (clearPopup) null else currentState.newCallPopup,
                activeCall = if (clearActive) null else currentState.activeCall,
                driverStatus = if (clearActive || clearPopup) DriverStatus.WAITING else currentState.driverStatus
            )
        }
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

    private fun getDriverLocationInfo(): Triple<String, String, String> {
        val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (provinceId.isNullOrBlank() || cityId.isNullOrBlank() || officeId.isNullOrBlank()) {
            throw IllegalStateException("Province ID, City ID or Office ID is not set.")
        }
        return Triple(provinceId, cityId, officeId)
    }

    private fun performFirestoreUpdate(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(errorMessage = null) }
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "❌ performFirestoreUpdate 에러 발생", e)
                Log.e(TAG, "❌ 에러 타입: ${e.javaClass.simpleName}")
                Log.e(TAG, "❌ 에러 메시지: ${e.message}")
                Log.e(TAG, "❌ 스택 트레이스:", e)
                _uiState.update { it.copy(errorMessage = e.message ?: "알 수 없는 오류가 발생했습니다.") }
            }
        }
    }

    fun setFcmToken(token: String) {
        val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (!provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            registerFcmToken(token)
        } else {
            fcmTokenToRegister = token
        }
    }

    private fun registerFcmToken(token: String) = performFirestoreUpdate {
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")
        val (provinceId, cityId, officeId) = getDriverLocationInfo()
        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
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
        // ✅ 리스너 대신 1회 조회로 변경
        // Firebase Auth 로그인 상태 확인 (MainActivity와 동일한 조건)
        val currentUser = auth.currentUser
        if (currentUser == null) {
            return
        }

        val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (!provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank()) {
            loadCurrentActiveCall(provinceId, cityId, officeId, driverId)
            startCarryOverListener(provinceId, cityId, officeId, driverId)
            loadSettlementData(provinceId, cityId, officeId, driverId)
        }
    }

    /**
     * 알림 클릭으로 들어온 callId를 처리하는 메서드
     * 해당 callId의 콜 정보를 로드하고 배차팝업을 표시한다
     */
    /**
     * FCM 알림에서 받은 callId를 StateFlow로 전달
     */
    fun setNotificationCallId(callId: String) {
        Log.d(TAG, "setNotificationCallId: $callId")
        _notificationCallId.value = callId
    }

    /**
     * 알림 callId 처리 완료 후 초기화
     */
    fun clearNotificationCallId() {
        _notificationCallId.value = null
    }

    fun handleNotificationCallId(callId: String) {
        viewModelScope.launch {
            Log.d(TAG, "handleNotificationCallId: processing callId = $callId")
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val callDocument = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                    .get()
                    .await()

                val callInfo = callDocument.toObject(CallInfo::class.java)?.copy(id = callDocument.id)

                if (callInfo != null && callInfo.statusEnum == CallStatus.ASSIGNED) {
                    // ✅ 배차된 콜이면 assignedCalls에 추가하고 팝업 표시
                    _uiState.update { currentState ->
                        val updatedCalls = if (currentState.assignedCalls.none { it.id == callInfo.id }) {
                            currentState.assignedCalls + callInfo
                        } else {
                            currentState.assignedCalls
                        }
                        currentState.copy(
                            assignedCalls = updatedCalls,
                            newCallPopup = callInfo,
                            navigateToHome = true
                        )
                    }
                    Log.d(TAG, "handleNotificationCallId: showing popup for assigned call")
                } else if (callInfo != null) {
                    // 다른 상태의 콜이면 콜 상세 화면으로 이동
                    _callDetailsState.value = callInfo
                    Log.d(TAG, "handleNotificationCallId: loaded call details for status ${callInfo.status}")
                } else {
                    Log.w(TAG, "handleNotificationCallId: call not found or invalid")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleNotificationCallId: error processing callId", e)
                _uiState.update { it.copy(errorMessage = "알림 처리 중 오류 발생: ${e.message}") }
            }
        }
    }

    /**
     * 현재 기사에게 배정된 ASSIGNED 상태의 콜이 있는지 확인하고 팝업으로 표시
     */
    fun checkForPendingDispatch() {
        viewModelScope.launch {
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                val assignedCallsQuery = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS)
                    .whereEqualTo(Constants.FIELD_ASSIGNED_DRIVER_ID, driverId)
                    .whereEqualTo(Constants.FIELD_STATUS, Constants.STATUS_ASSIGNED)
                    .get()
                    .await()

                val assignedCalls = assignedCallsQuery.documents.mapNotNull { doc ->
                    doc.toObject(CallInfo::class.java)?.copy(id = doc.id)
                }

                if (assignedCalls.isNotEmpty()) {
                    // 첫 번째 배정된 콜을 팝업으로 표시
                    val firstCall = assignedCalls.first()
                    _uiState.update { currentState ->
                        currentState.copy(
                            newCallPopup = firstCall,
                            navigateToHome = true
                        )
                    }
                    Log.d(TAG, "checkForPendingDispatch: found ${assignedCalls.size} pending calls, showing first one")
                } else {
                    Log.d(TAG, "checkForPendingDispatch: no pending assigned calls found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkForPendingDispatch: error checking for pending dispatch", e)
                _uiState.update { it.copy(errorMessage = "배차 확인 중 오류 발생: ${e.message}") }
            }
        }
    }

    /**
     * 고객 포인트 정보 조회 (정산 화면에서 사용)
     */
    suspend fun getCustomerPointInfo(phoneNumber: String): Map<String, Any>? {
        return try {
            // Repository를 통한 캐시된 조회 (중복 쿼리 방지)
            val points = customerPointsRepository.getCustomerPoints(phoneNumber)

            if (points != null) {
                mapOf(
                    "currentPoints" to points.currentPoints,
                    "grade" to points.grade,
                    "totalCalls" to points.totalCalls
                )
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "getCustomerPointInfo: 포인트 조회 실패", e)
            null
        }
    }

    /**
     * 포인트 사용 + 적립 통합 처리
     */
    private suspend fun processCustomerPoints(
        phoneNumber: String,
        callId: String,
        fare: Int,
        pointsUsed: Int
    ): Boolean {
        return try {
            val (provinceId, cityId, officeId) = getDriverLocationInfo()

            // 중복 체크
            val existingTransactions = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("pointTransactions")
                .whereEqualTo("callId", callId)
                .get()
                .await()

            if (!existingTransactions.isEmpty) {
                Log.w(TAG, "processCustomerPoints: 이미 처리된 포인트 - callId=$callId")
                return true  // 이미 처리됨
            }

            // Repository를 통한 포인트 정보 조회 (캐시 활용)
            var points = customerPointsRepository.getCustomerPoints(phoneNumber, forceRefresh = true)

            // 신규 고객이면 초기 포인트 생성
            if (points == null) {
                points = customerPointsRepository.createCustomerPoints(phoneNumber)
            }

            val currentPoints = points.currentPoints
            val totalCalls = points.totalCalls
            val totalEarned = points.totalEarned
            val totalUsed = points.totalUsed
            val grade = points.grade

            // 적립률 계산 (고객앱 CustomerGrade.kt 기준과 동일)
            val earnRate = when(grade) {
                "BRONZE" -> 0.03   // 3% (고객앱과 동일)
                "SILVER" -> 0.05   // 5% (고객앱과 동일)
                "GOLD" -> 0.07     // 7% (고객앱과 동일)
                "VIP" -> 0.09      // 9% (고객앱과 동일)
                else -> 0.03       // 기본값 BRONZE
            }

            val earnAmount = (fare * earnRate).toInt()
            val newBalance = currentPoints - pointsUsed + earnAmount
            val newTotalCalls = totalCalls + 1

            // 등급 업데이트 (고객앱 CustomerGrade.fromCallCount() 기준과 동일)
            val newGrade = when {
                newTotalCalls >= 50 -> "VIP"     // 50회 이상 (고객앱과 동일)
                newTotalCalls >= 30 -> "GOLD"    // 30회 이상 (고객앱과 동일)
                newTotalCalls >= 10 -> "SILVER"  // 10회 이상 (고객앱과 동일)
                else -> "BRONZE"
            }

            // Firestore 트랜잭션
            firestore.runTransaction { transaction ->
                val pointsRef = firestore
                    .collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId)
                    .collection("customerPoints")
                    .document(phoneNumber)

                // 포인트 정보 업데이트
                transaction.update(pointsRef, mapOf(
                    "currentPoints" to newBalance,
                    "totalEarned" to (totalEarned + earnAmount),
                    "totalUsed" to (totalUsed + pointsUsed),
                    "totalCalls" to newTotalCalls,
                    "grade" to newGrade,
                    "lastUpdated" to FieldValue.serverTimestamp()
                ))

                // 포인트 사용 내역 추가 (사용한 경우만)
                if (pointsUsed > 0) {
                    val useTransactionRef = firestore
                        .collection("provinces").document(provinceId)
                        .collection("cities").document(cityId)
                        .collection("offices").document(officeId)
                        .collection("pointTransactions")
                        .document()

                    transaction.set(useTransactionRef, mapOf(
                        "id" to useTransactionRef.id,
                        "customerId" to phoneNumber,
                        "type" to "USE",
                        "amount" to -pointsUsed,
                        "balance" to (currentPoints - pointsUsed),
                        "description" to "대리운전 요금 포인트 사용",
                        "callId" to callId,
                        "timestamp" to FieldValue.serverTimestamp()
                    ))
                }

                // 포인트 적립 내역 추가
                val earnTransactionRef = firestore
                    .collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId)
                    .collection("pointTransactions")
                    .document()

                transaction.set(earnTransactionRef, mapOf(
                    "id" to earnTransactionRef.id,
                    "customerId" to phoneNumber,
                    "type" to "EARN",
                    "amount" to earnAmount,
                    "balance" to newBalance,
                    "description" to "대리운전 이용 포인트 적립",
                    "callId" to callId,
                    "fare" to fare,
                    "grade" to newGrade,
                    "timestamp" to FieldValue.serverTimestamp()
                ))
            }.await()

            // 트랜잭션 완료 후 캐시 무효화 (다음 조회 시 최신 데이터 로드)
            customerPointsRepository.invalidateCache(phoneNumber)

            Log.d(TAG, "processCustomerPoints: 포인트 처리 완료 - 사용=$pointsUsed, 적립=$earnAmount, 잔액=$newBalance")
            true
        } catch (e: Exception) {
            Log.e(TAG, "processCustomerPoints: 포인트 처리 실패", e)
            false
        }
    }

    // ====== 이월 정산 (미수령금) 관련 기능 ======

    /**
     * 이월 정산 (미수령금) 실시간 리스너
     * 내 기사 문서의 carryOver 필드를 실시간 감시
     * PENDING_CONFIRM 상태의 dailySettlement가 있으면 calculatedCarryOver 사용
     */
    private fun startCarryOverListener(provinceId: String, cityId: String, officeId: String, driverId: String) {
        carryOverListener?.remove()

        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        carryOverListener = driverRef.addSnapshotListener { snapshot, e ->
            if (e != null) {
                Log.e(TAG, "CarryOver listener error", e)
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                val carryOverMap = snapshot.get("carryOver") as? Map<String, Any?>
                val carryOver = DriverCarryOver.fromMap(carryOverMap)

                // dailySettlement 확인 - PENDING_CONFIRM 상태면 calculatedCarryOver 사용
                val dailySettlementMap = snapshot.get("dailySettlement") as? Map<String, Any?>
                val dailySettlement = DriverDailySettlement.fromMap(dailySettlementMap)

                val effectiveCarryOver = if (dailySettlement.status == DailySettlementStatus.PENDING_CONFIRM) {
                    // 업무마감 후 매니저 확인 대기 중 - 계산된 carryOver 사용
                    val calculatedBalance = dailySettlement.calculatedCarryOver
                    Log.d(TAG, "Using calculatedCarryOver from dailySettlement: $calculatedBalance (original: ${carryOver.balance})")
                    carryOver.copy(balance = calculatedBalance)
                } else {
                    // 일반 상태 - 원본 carryOver 사용
                    carryOver
                }

                // 미수령금이 있고, SETTLED가 아닌 경우에만 표시
                if (effectiveCarryOver.balance > 0 && effectiveCarryOver.status != CarryOverStatus.SETTLED) {
                    _carryOver.value = effectiveCarryOver
                    Log.d(TAG, "CarryOver updated: balance=${effectiveCarryOver.balance}, status=${effectiveCarryOver.status}")
                } else {
                    _carryOver.value = null
                }
            } else {
                _carryOver.value = null
            }
        }
    }

    /**
     * 수령완료 - 이체받은 미수령금 수령 확인
     * 상태를 SETTLED로 변경하고 잔액을 0으로 리셋
     */
    fun confirmReceiveCarryOver(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)

                driverRef.update(
                    mapOf(
                        "carryOver.balance" to 0L,
                        "carryOver.status" to CarryOverStatus.SETTLED.name,
                        "carryOver.transferredAt" to null,
                        "carryOver.transferredBy" to null,
                        "carryOver.lastUpdatedAt" to Timestamp.now()
                    )
                ).await()

                Log.d(TAG, "CarryOver received and settled")
                onResult(true, "수령 완료")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to confirm carryOver receive", e)
                onResult(false, "수령 확인 실패: ${e.message}")
            }
        }
    }

    /**
     * 업무마감 - 실납입 확인 후 정산 제출
     * Firestore drivers/{driverId} 문서에 dailySettlement 필드 저장
     */
    fun submitDailySettlement(
        realDeposit: Int,
        onResult: (Boolean, String) -> Unit
    ) {
        // 중복 클릭 방지
        if (_isSubmittingSettlement.value) {
            Log.w(TAG, "⚠️ 이미 업무마감 진행 중입니다. 중복 요청 무시.")
            return
        }
        _isSubmittingSettlement.value = true

        viewModelScope.launch {
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)

                // 기존 dailySettlement 읽기 (통합 여부 확인)
                val driverDoc = driverRef.get().await()
                @Suppress("UNCHECKED_CAST")
                val existingSettlementMap = driverDoc.get("dailySettlement") as? Map<String, Any?>
                val prevSettlement = DriverDailySettlement.fromMap(existingSettlementMap)
                val isIntegration = prevSettlement.status == DailySettlementStatus.PENDING_CONFIRM

                val settlement = _todaySettlement.value
                val ratio = _depositRatio.value

                // 통합 시: 원본 carryOver.balance 사용, 신규 시: 현재 carryOver 사용
                val originalCarryOverBalance = if (isIntegration) {
                    // 통합: 이전 마감에 기록된 originalCarryOver 사용 (최초 값 유지)
                    prevSettlement.originalCarryOver.toInt()
                } else {
                    // 신규: 현재 carryOver 사용
                    _carryOver.value?.balance?.toInt() ?: 0
                }

                // 통합 시 이전 세션 값 합산
                val mergedTripCount = if (isIntegration) prevSettlement.tripCount + settlement.tripCount else settlement.tripCount
                val mergedTotalFare = if (isIntegration) prevSettlement.totalFare + settlement.totalFare else settlement.totalFare.toLong()
                val mergedTotalCredit = if (isIntegration) prevSettlement.totalCredit + settlement.totalCredit else settlement.totalCredit.toLong()
                val mergedRealDeposit = if (isIntegration) prevSettlement.realDeposit + realDeposit else realDeposit.toLong()

                // 통합된 값 기준으로 납입금 계산
                val mergedOfficeDeposit = (mergedTotalFare * ratio / 100)
                val mergedFinalDeposit = mergedOfficeDeposit - mergedTotalCredit

                // 통합 기준 carryOver 재계산 (원본 carryOver 기준)
                val remainingCarryOver = originalCarryOverBalance - mergedFinalDeposit.toInt() + mergedRealDeposit.toInt()

                // 날짜 계산: 6시 이전이면 전날로 처리 (콜매니저와 동일한 로직)
                val cal = java.util.Calendar.getInstance()
                if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 6) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                }
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(cal.time)

                val dailySettlement = DriverDailySettlement(
                    date = today,
                    finalDeposit = mergedFinalDeposit,
                    realDeposit = mergedRealDeposit,
                    settlementDiff = (mergedRealDeposit - mergedFinalDeposit) + originalCarryOverBalance,
                    totalFare = mergedTotalFare,
                    totalCredit = mergedTotalCredit,
                    tripCount = mergedTripCount,
                    status = DailySettlementStatus.PENDING_CONFIRM,
                    submittedAt = Timestamp.now(),
                    calculatedCarryOver = remainingCarryOver.toLong(),
                    originalCarryOver = originalCarryOverBalance.toLong()
                )

                // dailySettlement + settlementLastCleared를 단일 update로 갱신 (원자적 처리)
                val nowTimestamp = Timestamp.now()
                driverRef.update(
                    mapOf(
                        "dailySettlement" to dailySettlement.toMap(),
                        "settlementLastCleared" to nowTimestamp
                    )
                ).await()
                _lastClearedMillis.value = nowTimestamp.toDate().time

                val logMsg = if (isIntegration) {
                    "Daily settlement MERGED: prev=${prevSettlement.tripCount}건 + curr=${settlement.tripCount}건 = ${mergedTripCount}건, realDeposit=$mergedRealDeposit"
                } else {
                    "Daily settlement submitted: tripCount=${mergedTripCount}, realDeposit=$mergedRealDeposit"
                }
                Log.d(TAG, logMsg)

                val resultMsg = if (isIntegration) {
                    "업무마감이 완료되었습니다. (이전 ${prevSettlement.tripCount}건 + 추가 ${settlement.tripCount}건 통합)"
                } else {
                    "업무마감이 완료되었습니다. 매니저 확인을 기다려주세요."
                }
                onResult(true, resultMsg)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to submit daily settlement", e)
                onResult(false, "업무마감 실패: ${e.message}")
            } finally {
                _isSubmittingSettlement.value = false
            }
        }
    }

    // ====== calls 기반 정산 데이터 로드 ======

    /**
     * 분배비율 + 오늘 정산 데이터 로드 (1회 조회)
     * - offices에서 depositRatio 읽기
     * - calls에서 마감 이후 내 완료된 콜 조회 → 로컬 계산
     */
    private fun loadSettlementData(provinceId: String, cityId: String, officeId: String, driverId: String) {
        viewModelScope.launch {
            try {
                // 1. offices에서 depositRatio 읽기
                val officeDoc = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .get()
                    .await()

                val ratio = officeDoc.getLong("depositRatio")?.toInt() ?: 60
                _depositRatio.value = ratio.coerceIn(30, 90)

                // 2. drivers에서 settlementLastCleared 읽기 (기사별 마감 시점)
                val driverDoc = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)
                    .get()
                    .await()

                val lastClearedMillis = driverDoc.getTimestamp("settlementLastCleared")?.toDate()?.time ?: 0L
                _lastClearedMillis.value = lastClearedMillis

                Log.d(TAG, "Settlement data loaded: ratio=$ratio, lastCleared=$lastClearedMillis")

                // 3. calls에서 마감 이후 내 완료된 콜 조회
                loadTodaySettlement(provinceId, cityId, officeId, driverId, lastClearedMillis)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load settlement data", e)
            }
        }
    }

    /**
     * 오늘 정산 데이터 로드 (calls 기반)
     * 콜매니저와 동일한 계산 공식 적용
     */
    private suspend fun loadTodaySettlement(
        provinceId: String,
        cityId: String,
        officeId: String,
        driverId: String,
        lastClearedMillis: Long
    ) {
        try {
            val callsSnapshot = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                .collection(Constants.COLLECTION_CITIES).document(cityId)
                .collection(Constants.COLLECTION_OFFICES).document(officeId)
                .collection(Constants.COLLECTION_CALLS)
                .whereEqualTo("assignedDriverId", driverId)
                .whereEqualTo("status", "COMPLETED")
                .get()
                .await()

            val ratio = _depositRatio.value
            var totalFare = 0
            var totalCashReceived = 0
            var tripCount = 0
            val tripItems = mutableListOf<TripHistoryItem>()

            for (doc in callsSnapshot.documents) {
                val completedAt = doc.getTimestamp("completedAt")?.toDate()?.time
                    ?: doc.getTimestamp("updatedAt")?.toDate()?.time
                    ?: 0L

                // 마감 이후 콜만 포함
                if (completedAt <= lastClearedMillis) continue

                val fare = doc.getLong("fareFinal")?.toInt()
                    ?: doc.getLong("fare_set")?.toInt()
                    ?: 0

                val paymentMethod = doc.getString("paymentMethod") ?: ""
                val cashReceived = when {
                    paymentMethod == "현금" -> fare
                    paymentMethod.startsWith("현금+") -> doc.getLong("cashReceived")?.toInt() ?: 0
                    else -> 0
                }

                totalFare += fare
                totalCashReceived += cashReceived
                tripCount++

                // 개별 운행내역 아이템 추가
                val customerName = doc.getString("customerName") ?: "고객"
                val departure = doc.getString("departure_set")?.takeIf { it.isNotBlank() } ?: "출발지"
                val destination = doc.getString("destination_set")?.takeIf { it.isNotBlank() } ?: "도착지"
                val cashAmount = doc.getLong("cashReceived")?.toInt()

                tripItems.add(TripHistoryItem(
                    tripNumber = tripCount,
                    customerName = customerName,
                    departure = departure,
                    destination = destination,
                    fare = fare,
                    paymentMethod = paymentMethod,
                    cashAmount = cashAmount,
                    timestamp = completedAt
                ))
            }

            // 시간순 정렬 후 tripNumber 재정렬 (오래된 것이 1번, 최신이 위에 표시)
            val sortedByTime = tripItems.sortedBy { it.timestamp }
            val reNumberedItems = sortedByTime.mapIndexed { index, item ->
                item.copy(tripNumber = index + 1)
            }.reversed()  // 최신이 위로 표시

            _tripHistoryList.value = reNumberedItems

            // 계산 (콜매니저와 동일한 공식 - Double 나눗셈)
            val officeDeposit = (totalFare * ratio / 100.0).toInt()        // 총 납입액 (사무실 몫)
            val driverShare = (totalFare * (100 - ratio) / 100.0).toInt()  // 내 수익 (기사몫)
            val realDeposit = totalCashReceived - driverShare              // 실 납부액
            val totalCredit = totalFare - totalCashReceived                // 총 외상 (현금 제외 금액)

            _todaySettlement.value = TodaySettlement(
                totalFare = totalFare,
                driverShare = driverShare,
                cashReceived = totalCashReceived,
                realDeposit = realDeposit,
                tripCount = tripCount,
                totalCredit = totalCredit,
                officeDeposit = officeDeposit
            )

            Log.d(TAG, "Today settlement calculated: fare=$totalFare, share=$driverShare, cash=$totalCashReceived, deposit=$realDeposit, trips=$tripCount, credit=$totalCredit, historyItems=${reNumberedItems.size}")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load today settlement", e)
        }
    }

    /**
     * 정산 데이터 새로고침 (운행 완료 후 호출)
     */
    fun refreshSettlementData() {
        val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)
        val driverId = auth.currentUser?.uid

        if (!provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank() && driverId != null) {
            loadSettlementData(provinceId, cityId, officeId, driverId)
        }
    }

    /**
     * 정산 초기화 (저장 및 초기화 버튼 클릭 시)
     * - Firestore의 settlementLastCleared 업데이트
     * - 로컬 상태 초기화
     */
    fun clearSettlement(onSuccess: () -> Unit, onError: (String) -> Unit) {
        val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)
        val driverId = auth.currentUser?.uid

        if (provinceId.isNullOrBlank() || cityId.isNullOrBlank() || officeId.isNullOrBlank() || driverId == null) {
            onError("로그인 정보가 없습니다")
            return
        }

        viewModelScope.launch {
            try {
                val nowMillis = System.currentTimeMillis()
                val nowTimestamp = Timestamp.now()

                // Firestore에 마지막 정산 시간 업데이트 (기사별로 저장)
                val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)

                // settlementLastCleared 업데이트 + 기사 상태를 OFFLINE으로 변경
                driverRef.update(
                    mapOf(
                        "settlementLastCleared" to nowTimestamp,
                        Constants.FIELD_STATUS to DriverStatus.OFFLINE.value
                    )
                ).await()

                // 로컬 상태 업데이트
                _lastClearedMillis.value = nowMillis
                _todaySettlement.value = TodaySettlement()  // 정산 합계 초기화
                _tripHistoryList.value = emptyList()        // 운행내역 목록 초기화

                Log.d(TAG, "Settlement cleared at $nowMillis")
                onSuccess()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear settlement", e)
                onError("정산 초기화 실패: ${e.message}")
            }
        }
    }
}
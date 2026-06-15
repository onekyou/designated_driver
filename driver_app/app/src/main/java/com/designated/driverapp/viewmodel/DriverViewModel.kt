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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
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

    // 분배비율 (Firestore offices에서 읽기)
    private val _depositRatio = MutableStateFlow(60)
    val depositRatio: StateFlow<Int> = _depositRatio.asStateFlow()

    // 마지막 마감 시점 (최초 1회 로드, tripHistory 필터링용)
    private val _lastClearedMillis = MutableStateFlow(0L)
    val lastClearedMillis: StateFlow<Long> = _lastClearedMillis.asStateFlow()

    // 콜 수락 중 (중복 클릭 방지)
    private val _isAccepting = MutableStateFlow(false)
    val isAccepting: StateFlow<Boolean> = _isAccepting.asStateFlow()

    // 자가배차 콜 생성 중 (중복 클릭 방지)
    private val _isCreatingSelfCall = MutableStateFlow(false)
    val isCreatingSelfCall: StateFlow<Boolean> = _isCreatingSelfCall.asStateFlow()

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
        val totalCredit: Int = 0,         // 총 외상 (현금·포인트 제외 금액)
        val officeDeposit: Int = 0,       // 총 납입액 (사무실 몫)
        val pointsUsed: Int = 0           // 총 포인트 사용액
    )
    private val _todaySettlement = MutableStateFlow(TodaySettlement())
    val todaySettlement: StateFlow<TodaySettlement> = _todaySettlement.asStateFlow()

    // P7 게이트 #3 — 이전 영업일(오전 10시 경계 이전)의 미정산 운행이 남아 있는지 (읽기 전용)
    // 근무 중 누적된 오늘 운행은 제외 → 어제 미마감 상태만 표면화. 정산 데이터 수정 없음.
    private val _needsDailyClose = MutableStateFlow(false)
    val needsDailyClose: StateFlow<Boolean> = _needsDailyClose.asStateFlow()

    // 운행내역 카드용 개별 콜 목록 (Firestore 기반)
    data class TripHistoryItem(
        val callId: String = "",
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

        // [PTT 차단] 콜 IN_PROGRESS(운행 중)면 prefs 플래그 ON → PttAudioManager 가 음성 수신 차단.
        //  activeCall.status 가 콜 상태 단일 수렴점 → 운행시작/완료/취소/외부변경/재시작 모두 자동 미러링.
        viewModelScope.launch {
            _uiState.map { it.activeCall?.status == Constants.STATUS_IN_PROGRESS }
                .distinctUntilChanged()
                .collect { inProgress ->
                    sharedPreferences.edit()
                        .putBoolean(Constants.PREF_KEY_PTT_BLOCK_ONTRIP, inProgress).apply()
                }
        }
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

                // ✅ 2. 현재 배정된 콜 조회 (ASSIGNED, RESERVED, ACCEPTED, IN_PROGRESS, AWAITING_SETTLEMENT)
                val assignedCallsSnapshot = firestore
                    .collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS)
                    .whereEqualTo(Constants.FIELD_ASSIGNED_DRIVER_ID, driverId)
                    .whereIn(Constants.FIELD_STATUS, listOf(
                        Constants.STATUS_ASSIGNED,
                        Constants.STATUS_RESERVED,
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
                    val reservedCall = assignedCalls.firstOrNull { it.statusEnum == CallStatus.RESERVED }
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
                            reservedCall = reservedCall,
                            callForSettlement = settlementCall,
                            isLoading = false
                        )
                    }

                    Log.d(TAG, "✅ 앱 시작: 기사 상태=${driverStatus.value}, 콜 ${assignedCalls.size}개 (RESERVED=${if (reservedCall != null) 1 else 0})")
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
                transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.PREPARING.value)
            } else {
                Log.w(TAG, "⚠️ 콜 상태가 ASSIGNED가 아님: $currentStatus")
                throw IllegalStateException("CALL_NOT_ASSIGNABLE: current status is $currentStatus")
            }
        }.await()
        Log.d(TAG, "🔵 Transaction 완료")

        Log.d(TAG, "✅ 콜 수락 완료: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 콜 수락 실패: ${e.message}", e)
                // UI 상태 롤백: ACCEPTED -> ASSIGNED로 되돌리기
                _uiState.update { current ->
                    current.copy(
                        assignedCalls = current.assignedCalls.map {
                            if (it.id == callId) it.copy(status = Constants.STATUS_ASSIGNED)
                            else it
                        },
                        activeCall = null,
                        newCallPopup = current.assignedCalls.find { it.id == callId },
                        driverStatus = DriverStatus.ASSIGNED,
                        errorMessage = e.message ?: "콜 수락 중 오류가 발생했습니다."
                    )
                }
            } finally {
                _isAccepting.value = false
            }
        }
    }

    /**
     * 기사 자가배차: 빈 콜을 즉시 만들고 운행준비 화면으로 직행한다.
     * - 출발지/목적지/경유지/요금/메모 입력은 운행준비 화면의 기존 TextField + STT + 주소검색을 그대로 사용
     * - 콜 신규 생성: status=ACCEPTED, assignedDriverId=내uid, selfAssigned=true (모든 정보는 빈값)
     * - 본인 designated_drivers status: WAITING/ONLINE → PREPARING (acceptCall 패턴과 동일)
     * - UI: activeCall 즉시 세팅 + driverStatus=ACCEPTED → HomeScreen 이 자동으로 TripPreparationScreen 라우팅
     * - Cloud Functions oncallassigned/notifyCustomerOnPhoneCall 가 selfAssigned 가드로 기사/고객 FCM 스킵
     */
    fun startSelfAssignedTrip() {
        if (_isCreatingSelfCall.value) {
            Log.w(TAG, "⚠️ 이미 자가배차 처리 중. 중복 클릭 무시.")
            return
        }
        _isCreatingSelfCall.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val driverAuthUid = auth.currentUser?.uid
                    ?: throw IllegalStateException("로그인이 필요합니다.")

                val officeRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                val callRef = officeRef.collection(Constants.COLLECTION_CALLS).document()
                val driverRef = officeRef.collection(Constants.COLLECTION_DRIVERS).document(driverAuthUid)

                val nowTs = Timestamp.now()
                val nowMillis = System.currentTimeMillis()
                val expireAt = Timestamp(java.util.Date(nowMillis + 30L * 24 * 60 * 60 * 1000))

                // 트랜잭션: driver 상태 검증 + 빈 콜 생성 + driver 상태 업데이트
                val createdCall = firestore.runTransaction { tx ->
                    val driverSnap = tx.get(driverRef)
                    if (!driverSnap.exists()) {
                        throw IllegalStateException("기사 정보를 찾을 수 없습니다.")
                    }
                    val driverStatusStr = driverSnap.getString(Constants.FIELD_STATUS)
                    if (driverStatusStr != DriverStatus.WAITING.value &&
                        driverStatusStr != DriverStatus.ONLINE.value) {
                        throw IllegalStateException(
                            "현재 상태(${driverStatusStr ?: "?"})에서는 자가배차할 수 없습니다."
                        )
                    }

                    val driverName = driverSnap.getString("name") ?: ""
                    val driverPhone = driverSnap.getString("phoneNumber") ?: ""

                    val data = hashMapOf<String, Any?>(
                        "phoneNumber" to "",
                        "customerName" to "",
                        Constants.FIELD_STATUS to Constants.STATUS_ACCEPTED,
                        Constants.FIELD_ASSIGNED_DRIVER_ID to driverAuthUid,
                        "assignedDriverName" to driverName,
                        "assignedDriverPhone" to driverPhone,
                        "assignedTimestamp" to nowTs,
                        "timestamp" to nowTs,
                        "timestampClient" to nowMillis,
                        "provinceId" to provinceId,
                        "cityId" to cityId,
                        "officeId" to officeId,
                        "createdBy" to driverAuthUid,
                        "createdFrom" to "driver_self",
                        "selfAssigned" to true,
                        "expireAt" to expireAt
                    )
                    tx.set(callRef, data)
                    tx.update(driverRef, Constants.FIELD_STATUS, DriverStatus.PREPARING.value)

                    CallInfo(
                        id = callRef.id,
                        customerName = "",
                        phoneNumber = "",
                        timestamp = nowTs,
                        status = Constants.STATUS_ACCEPTED,
                        assignedDriverId = driverAuthUid,
                        assignedDriverName = driverName,
                        assignedDriverPhone = driverPhone,
                        assignedTimestamp = nowTs,
                        officeId = officeId,
                        provinceId = provinceId,
                        cityId = cityId
                    )
                }.await()

                _uiState.update { current ->
                    current.copy(
                        activeCall = createdCall,
                        driverStatus = DriverStatus.ACCEPTED,
                        newCallPopup = null,
                        errorMessage = null
                    )
                }
                Log.d(TAG, "✅ 자가배차(빈 콜) 생성 완료: ${createdCall.id}")

            } catch (e: Exception) {
                Log.e(TAG, "❌ 자가배차 실패: ${e.message}", e)
                _uiState.update {
                    it.copy(errorMessage = e.message ?: "자가배차 중 오류가 발생했습니다.")
                }
            } finally {
                _isCreatingSelfCall.value = false
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

        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        // 콜 상태를 WAITING으로 되돌려 재배차 가능하게 함
        val callUpdates = mapOf(
            Constants.FIELD_STATUS to Constants.STATUS_WAITING,
            "assignedDriverId" to null,
            "assignedDriverName" to null,
            "assignedDriverPhone" to null,
            "rejectedByDriver" to driverId,
            Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )

        // 단일 트랜잭션으로 콜+기사 원자적 업데이트
        firestore.runTransaction { transaction ->
            transaction.update(callRef, callUpdates)
            transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.WAITING.value)
        }.await()

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
     * 예약 콜 수락 — 운행 종료 후 RESERVED 카드의 [수락] 클릭 시.
     *
     * 트랜잭션: calls.status: RESERVED → ACCEPTED, driver.status → PREPARING.
     * 정상 ACCEPTED 흐름과 합류 — 출발지 이동 → IN_PROGRESS → ... → COMPLETED.
     *
     * 활성화 조건: driver doc status == WAITING (운행 종료 후 복귀 시점). UI 측에서 enabled 분기.
     */
    fun acceptReservedCall(callId: String) {
        if (_isAccepting.value) {
            Log.w(TAG, "⚠️ 이미 콜 수락 진행 중 — 중복 요청 무시")
            return
        }
        _isAccepting.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

                val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)
                val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)

                // 즉시 로컬 UI 업데이트 — 트랜잭션 기다리지 않음
                _uiState.update { current ->
                    val reserved = current.reservedCall
                    if (reserved?.id == callId) {
                        val accepted = reserved.copy(status = Constants.STATUS_ACCEPTED)
                        current.copy(
                            assignedCalls = current.assignedCalls.map {
                                if (it.id == callId) accepted else it
                            },
                            activeCall = accepted,
                            reservedCall = null,
                            driverStatus = DriverStatus.PREPARING
                        )
                    } else current
                }

                firestore.runTransaction { transaction ->
                    val callSnap = transaction.get(callRef)
                    val currentStatus = callSnap.getString(Constants.FIELD_STATUS)
                    if (currentStatus != Constants.STATUS_RESERVED) {
                        throw IllegalStateException("RESERVED_NOT_FOUND: current=$currentStatus")
                    }
                    transaction.update(callRef, Constants.FIELD_STATUS, Constants.STATUS_ACCEPTED)
                    transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.PREPARING.value)
                }.await()

                Log.d(TAG, "✅ 예약 콜 수락 완료: $callId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 예약 콜 수락 실패: ${e.message}", e)
                _uiState.update { it.copy(errorMessage = "예약 수락 실패 — 다시 시도해주세요") }
            } finally {
                _isAccepting.value = false
            }
        }
    }

    /**
     * 예약 콜 거절 — RESERVED 카드의 [거절] 클릭 시.
     *
     * 트랜잭션: calls.status RESERVED → WAITING + assignedDriverId/Name/Phone null + reservedAt 제거.
     * driver doc 은 손대지 않음 (기사가 운행 중일 수도 있음).
     * 매니저는 일반 배차 다이얼로그에서 다른 기사로 재배차 가능.
     */
    fun rejectReservedCall(callId: String) = performFirestoreUpdate {
        val (provinceId, cityId, officeId) = getDriverLocationInfo()

        val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        firestore.runTransaction { transaction ->
            val snap = transaction.get(callRef)
            val currentStatus = snap.getString(Constants.FIELD_STATUS)
            if (currentStatus != Constants.STATUS_RESERVED) {
                throw IllegalStateException("RESERVED_NOT_FOUND: current=$currentStatus")
            }
            val updates = mapOf<String, Any?>(
                Constants.FIELD_STATUS to Constants.STATUS_WAITING,
                "assignedDriverId" to null,
                "assignedDriverName" to null,
                "assignedDriverPhone" to null,
                "reservedAt" to FieldValue.delete(),
                Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
            )
            transaction.update(callRef, updates)
        }.await()

        // 즉시 로컬 UI 정리: reservedCall null + assignedCalls 에서 제거
        _uiState.update { current ->
            current.copy(
                reservedCall = if (current.reservedCall?.id == callId) null else current.reservedCall,
                assignedCalls = current.assignedCalls.filter { it.id != callId }
            )
        }

        Log.d(TAG, "예약 콜 거절 완료: callId=$callId")
    }

    /**
     * 운행 준비 단계에서 운행을 취소하는 함수
     * - 콜 상태를 CANCELLED_BY_DRIVER로 변경 (내부콜/공유콜 동일)
     * - 기사 상태를 WAITING으로 변경
     * - assignedDriverId를 null로 변경
     */
    fun cancelTrip(callId: String, cancelReason: String = "운행취소") = performFirestoreUpdate {
        val (provinceId, cityId, officeId) = getDriverLocationInfo()
        val driverId = auth.currentUser?.uid ?: throw IllegalStateException("User not logged in")

        val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_CALLS).document(callId)

        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        // 내부콜/공유콜 모두 동일하게 CANCELLED_BY_DRIVER로 처리
        val callUpdates = mapOf(
            Constants.FIELD_STATUS to "CANCELLED_BY_DRIVER",
            "assignedDriverId" to null,
            "assignedDriverName" to null,
            "assignedDriverPhone" to null,
            "cancelReason" to cancelReason,
            "cancelledByDriver" to true,
            Constants.FIELD_UPDATED_AT to FieldValue.serverTimestamp()
        )

        // 단일 트랜잭션으로 콜+기사 원자적 업데이트
        firestore.runTransaction { transaction ->
            transaction.update(callRef, callUpdates)
            transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.WAITING.value)
        }.await()

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

        val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
            .collection(Constants.COLLECTION_CITIES).document(cityId)
            .collection(Constants.COLLECTION_OFFICES).document(officeId)
            .collection(Constants.COLLECTION_DRIVERS).document(driverId)

        firestore.runTransaction { transaction ->
            transaction.update(callRef, callUpdates)
            transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.ON_TRIP.value)
        }.await()

        Log.d(TAG, "✅ 운행 시작 완료: $callId")
    }

    /**
     * 예약 콜(RESERVED) 만 별도로 1회 fetch 해 uiState.reservedCall 갱신.
     *
     * 호출 시점:
     *  - HomeScreen 이 ACTION_RESERVATION_RECEIVED LocalBroadcast 수신 시 (매니저가 RESERVED 배차한 직후)
     *  - confirmAndFinalizeTrip 끝 (운행 완료 후 reservedCard 활성화)
     *
     * 비용: 사무실당 0~1건이라 read 1회. listener 안 씀 (월 0.03$ 수준).
     */
    fun reloadReservedCall() {
        viewModelScope.launch {
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                val driverId = auth.currentUser?.uid ?: return@launch

                val snap = firestore
                    .collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS)
                    .whereEqualTo(Constants.FIELD_ASSIGNED_DRIVER_ID, driverId)
                    .whereEqualTo(Constants.FIELD_STATUS, Constants.STATUS_RESERVED)
                    .limit(1)
                    .get()
                    .await()

                val reserved = snap.documents.firstOrNull()?.let { doc ->
                    try {
                        doc.toObject<CallInfo>()?.apply { id = doc.id }
                    } catch (e: Exception) { null }
                }

                _uiState.update { current ->
                    // assignedCalls 에도 동기 (중복 없이 push)
                    val updatedAssignedCalls = if (reserved != null) {
                        val existing = current.assignedCalls.filterNot { it.id == reserved.id }
                        existing + reserved
                    } else {
                        current.assignedCalls.filterNot { it.statusEnum == CallStatus.RESERVED }
                    }
                    current.copy(
                        reservedCall = reserved,
                        assignedCalls = updatedAssignedCalls
                    )
                }
                Log.d(TAG, "reloadReservedCall: ${if (reserved != null) "RESERVED 콜 ${reserved.id} 발견" else "RESERVED 콜 없음"}")
            } catch (e: Exception) {
                Log.e(TAG, "reloadReservedCall 실패: ${e.message}", e)
            }
        }
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
                    val actualCredit = maxOf(0, fareToSet - pointsToUse - cashAmount)
                    tripData["creditAmount"] = actualCredit
                } else if (paymentMethod == "외상" || paymentMethod == "이체") {
                    tripData["creditAmount"] = fareToSet
                }

                val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)

                val driverRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)

                // 단일 트랜잭션으로 콜 완료 + 기사 상태 원자적 업데이트
                firestore.runTransaction { transaction ->
                    transaction.update(callRef, tripData)
                    transaction.update(driverRef, Constants.FIELD_STATUS, DriverStatus.WAITING.value)
                }.await()

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
                val newPointsUsed = pointsToUse
                val newOfficeDeposit = (fareToSet * ratio / 100.0).toInt()
                val newDriverShare = fareToSet - newOfficeDeposit

                _todaySettlement.update { current ->
                    val updatedTotalFare = current.totalFare + fareToSet
                    val updatedCashReceived = current.cashReceived + newCashReceived
                    val updatedDriverShare = current.driverShare + newDriverShare
                    val updatedPointsUsed = current.pointsUsed + newPointsUsed
                    val updatedOfficeDeposit = (updatedTotalFare * ratio / 100.0).toInt()
                    val updatedTotalCredit = updatedTotalFare - updatedCashReceived - updatedPointsUsed
                    current.copy(
                        totalFare = updatedTotalFare,
                        driverShare = updatedDriverShare,
                        cashReceived = updatedCashReceived,
                        realDeposit = updatedCashReceived - updatedDriverShare,
                        tripCount = current.tripCount + 1,
                        totalCredit = updatedTotalCredit,
                        officeDeposit = updatedOfficeDeposit,
                        pointsUsed = updatedPointsUsed
                    )
                }

                // ✅ 운행내역 목록에 즉시 추가 (SharedPreferences 대신 StateFlow 사용)
                val customerName = latestCallInfo?.customerName ?: "고객"
                val departure = latestCallInfo?.departure_set?.takeIf { it.isNotBlank() } ?: "출발지"
                val destination = latestCallInfo?.destination_set?.takeIf { it.isNotBlank() } ?: "도착지"
                val newTripNumber = _tripHistoryList.value.size + 1

                val newTripItem = TripHistoryItem(
                    callId = callId,
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
                        // 정산 입력 완료 후 자동 navigate 제거 — HomeScreen(대시보드) 유지
                        // 사용자가 정산 history 보고 싶으면 메뉴에서 명시적으로 requestNavigateToSettlement() 진입
                        navigateToHistorySettlement = false,
                        isLoading = false
                    )
                }

                // 운행 완료 후 Firestore 정산 데이터 갱신
                refreshSettlementData()

                // 예약 콜(RESERVED) 재로드 — 운행 완료 후 ReservedCallCard 의 [수락] 활성화
                reloadReservedCall()

            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "정산 처리 중 오류: ${e.message}", isLoading = false) }
            }
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
        _uiState.update { currentState ->
            // 현재 팝업의 콜을 제외하고, 아직 ASSIGNED 상태인 다음 콜이 있으면 팝업 표시
            val currentPopupId = currentState.newCallPopup?.id
            val nextCall = currentState.assignedCalls.firstOrNull {
                it.id != currentPopupId && it.statusEnum == CallStatus.ASSIGNED
            }
            if (nextCall != null) {
                Log.d(TAG, "dismissNewCallPopup: showing next pending call popup: ${nextCall.id}")
            }
            currentState.copy(newCallPopup = nextCall)
        }
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
                driverStatus = if (clearActive || clearPopup) DriverStatus.WAITING else currentState.driverStatus,
                errorMessage = "고객이 콜을 취소했습니다"
            )
        }
    }

    /**
     * onResume 시 활성 콜의 최신 상태를 Firestore에서 조회하여
     * 백그라운드에서 취소된 콜이 있으면 UI를 정리한다.
     * 활성 콜이 없으면 아무 작업도 하지 않는다.
     */
    fun refreshActiveCallStatus() {
        val currentState = _uiState.value
        val callsToCheck = mutableListOf<String>()

        currentState.activeCall?.id?.let { callsToCheck.add(it) }
        currentState.assignedCalls.forEach { call ->
            if (call.id != currentState.activeCall?.id) {
                callsToCheck.add(call.id)
            }
        }

        if (callsToCheck.isEmpty()) return

        viewModelScope.launch {
            try {
                val (provinceId, cityId, officeId) = getDriverLocationInfo()
                for (callId in callsToCheck) {
                    try {
                        val callDoc = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                            .collection(Constants.COLLECTION_CITIES).document(cityId)
                            .collection(Constants.COLLECTION_OFFICES).document(officeId)
                            .collection(Constants.COLLECTION_CALLS).document(callId)
                            .get()
                            .await()

                        val status = callDoc.getString(Constants.FIELD_STATUS)
                        if (status == Constants.STATUS_CANCELED || status == "CANCELLED_BY_CUSTOMER" || status == "WAITING" || !callDoc.exists()) {
                            Log.d(TAG, "refreshActiveCallStatus: 콜 $callId 상태=$status -> 정리")
                            handleCallCancelled(callId)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "refreshActiveCallStatus: 콜 $callId 조회 실패", e)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "refreshActiveCallStatus: 위치 정보 조회 실패", e)
            }
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

    fun requestNavigateToSettlement() {
        _uiState.update { it.copy(navigateToHistorySettlement = true) }
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
        driverRef.update(mapOf(
            Constants.FIELD_FCM_TOKEN to token,
            Constants.FIELD_FCM_TOKEN_PLATFORM to Constants.PLATFORM_ANDROID,
            Constants.FIELD_PLATFORM to Constants.PLATFORM_ANDROID
        )).await()
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
                    // ✅ 배차된 콜이면 assignedCalls에 추가
                    _uiState.update { currentState ->
                        val updatedCalls = if (currentState.assignedCalls.none { it.id == callInfo.id }) {
                            currentState.assignedCalls + callInfo
                        } else {
                            currentState.assignedCalls
                        }
                        // 이미 팝업이 표시 중이면 덮어쓰지 않고 assignedCalls에만 추가
                        if (currentState.newCallPopup != null) {
                            Log.d(TAG, "handleNotificationCallId: popup already showing, adding to assignedCalls only (total: ${updatedCalls.size})")
                            currentState.copy(
                                assignedCalls = updatedCalls,
                                navigateToHome = true
                            )
                        } else {
                            currentState.copy(
                                assignedCalls = updatedCalls,
                                newCallPopup = callInfo,
                                navigateToHome = true
                            )
                        }
                    }
                    Log.d(TAG, "handleNotificationCallId: processed assigned call")
                } else if (callInfo != null) {
                    // 취소된 콜이면 무시하고 홈으로
                    val cancelStatuses = listOf("CANCELED", "CANCELLED_BY_CUSTOMER", "CANCELLED_BY_DRIVER")
                    if (callInfo.status in cancelStatuses) {
                        Log.d(TAG, "handleNotificationCallId: cancelled call, navigating home")
                        _uiState.update { it.copy(navigateToHome = true) }
                    } else {
                        // 다른 상태의 콜이면 콜 상세 화면으로 이동
                        _callDetailsState.value = callInfo
                        Log.d(TAG, "handleNotificationCallId: loaded call details for status ${callInfo.status}")
                    }
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

                val activeCallsQuery = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
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

                val activeCalls = activeCallsQuery.documents.mapNotNull { doc ->
                    doc.toObject(CallInfo::class.java)?.copy(id = doc.id)
                }

                if (activeCalls.isNotEmpty()) {
                    val activeCall = activeCalls.firstOrNull {
                        it.statusEnum == CallStatus.ACCEPTED || it.statusEnum == CallStatus.IN_PROGRESS
                    }
                    val newCall = activeCalls.firstOrNull { it.statusEnum == CallStatus.ASSIGNED }
                    val settlementCall = activeCalls.firstOrNull {
                        it.statusEnum == CallStatus.AWAITING_SETTLEMENT
                    }

                    _uiState.update { currentState ->
                        currentState.copy(
                            activeCall = activeCall ?: currentState.activeCall,
                            newCallPopup = newCall ?: currentState.newCallPopup,
                            callForSettlement = settlementCall ?: currentState.callForSettlement,
                            navigateToHome = true
                        )
                    }
                    Log.d(TAG, "checkForPendingDispatch: found ${activeCalls.size} active calls")
                } else {
                    Log.d(TAG, "checkForPendingDispatch: no active calls found")
                    _uiState.update { it.copy(errorMessage = "현재 진행 중인 콜이 없습니다.") }
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkForPendingDispatch: error checking for active calls", e)
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


    // ====== 이월 정산 (미수령금) 관련 기능 ======

    /**
     * 업무마감 - 실납입 확인 후 정산 제출
     * Firestore drivers/{driverId} 문서에 dailySettlement 필드 저장
     */
    fun submitDailySettlement(
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

                val settlement = _todaySettlement.value
                val ratio = _depositRatio.value

                // 당일 정산 계산 (이월 없음 + 실납입/환급 개념 폐기 — net 한 값)
                // 납입금(net) = 사무실 몫 − 외상/이체/포인트 (부호, 음수 허용). 실제 정산은 현장에서.
                val officeDeposit = (settlement.totalFare.toLong() * ratio / 100)
                val finalDeposit = officeDeposit - settlement.totalCredit

                // 날짜 계산: 10시 이전이면 전날로 처리 (콜매니저와 동일한 로직)
                val cal = java.util.Calendar.getInstance()
                if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 10) {
                    cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
                }
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(cal.time)

                val dailySettlement = DriverDailySettlement(
                    date = today,
                    finalDeposit = finalDeposit,
                    realDeposit = finalDeposit,
                    settlementDiff = 0L,
                    totalFare = settlement.totalFare.toLong(),
                    totalCredit = settlement.totalCredit.toLong(),
                    tripCount = settlement.tripCount,
                    submittedAt = Timestamp.now()
                )

                // dailySettlement 저장 + 기사 상태를 PENDING_CONFIRM으로 변경 (배차 차단)
                driverRef.update(
                    mapOf(
                        "dailySettlement" to dailySettlement.toMap(),
                        "status" to DriverStatus.PENDING_CONFIRM.value
                    )
                ).await()

                // 로컬 상태도 즉시 반영
                _uiState.update { it.copy(driverStatus = DriverStatus.PENDING_CONFIRM) }

                Log.d(TAG, "Daily settlement submitted: tripCount=${settlement.tripCount}, net=$finalDeposit")
                onResult(true, "업무마감이 완료되었습니다.")
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
            var totalPointsUsed = 0
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
                val pointsUsed = doc.getLong("pointsUsed")?.toInt() ?: 0

                totalFare += fare
                totalCashReceived += cashReceived
                totalPointsUsed += pointsUsed
                tripCount++

                // 개별 운행내역 아이템 추가
                val customerName = doc.getString("customerName") ?: "고객"
                val departure = doc.getString("departure_set")?.takeIf { it.isNotBlank() } ?: "출발지"
                val destination = doc.getString("destination_set")?.takeIf { it.isNotBlank() } ?: "도착지"
                val cashAmount = doc.getLong("cashReceived")?.toInt()

                tripItems.add(TripHistoryItem(
                    callId = doc.id,
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

            // 계산 (CF와 동일한 공식 - deposit 먼저, driverShare는 뺄셈)
            val officeDeposit = (totalFare * ratio / 100.0).toInt()        // 총 납입액 (사무실 몫)
            val driverShare = totalFare - officeDeposit                    // 내 수익 (기사몫 = 운행료 - 사무실몫)
            val realDeposit = totalCashReceived - driverShare              // 실 납부액
            val totalCredit = totalFare - totalCashReceived  // 총 외상 (현금 제외 금액 = 이체+외상+포인트)

            _todaySettlement.value = TodaySettlement(
                totalFare = totalFare,
                driverShare = driverShare,
                cashReceived = totalCashReceived,
                realDeposit = realDeposit,
                tripCount = tripCount,
                totalCredit = totalCredit,
                officeDeposit = officeDeposit,
                pointsUsed = totalPointsUsed
            )

            Log.d(TAG, "Today settlement calculated: fare=$totalFare, share=$driverShare, cash=$totalCashReceived, deposit=$realDeposit, trips=$tripCount, credit=$totalCredit, historyItems=${reNumberedItems.size}")

            // P7 게이트 #3 — 이전 영업일 미마감 감지.
            // 현재 영업일 시작(오전 10시 경계, submitDailySettlement L1513 동일 규칙) 이전에
            // 완료됐는데 아직 정산(clear) 안 된 운행이 하나라도 있으면 = 어제 미마감.
            // (오늘 근무 중 누적분은 workdayStart 이후라 제외 → 정상 근무를 막지 않음.)
            val workdayStartCal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 10)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
                if (System.currentTimeMillis() < timeInMillis) {
                    add(java.util.Calendar.DAY_OF_MONTH, -1)
                }
            }
            val workdayStartMillis = workdayStartCal.timeInMillis
            _needsDailyClose.value = tripItems.any { it.timestamp < workdayStartMillis }
            Log.d(TAG, "needsDailyClose=${_needsDailyClose.value} (workdayStart=$workdayStartMillis)")

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
                // ⚠️ P7: dailySettlement(기사 제출 요약)는 여기서 지우지 않는다 —
                //   퇴근(귀가) 후에도 매니저가 콜매니저에서 [정산확인]할 수 있어야 하기 때문.
                //   삭제는 매니저 측(확인 시 confirmDriverDailySettlement / 영업마감 clearDailySettlement)에서만.
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

    /**
     * 콜 단위 결제수단 정정 (일과 끝 정산 화면, [제출] 전에만 호출)
     * - calls/{callId} 의 paymentMethod + 연동 필드(cashReceived/creditAmount) 재계산
     * - 현금/외상/이체 3종만 (포인트 계열은 보류 트랙 — 호출 측에서 차단)
     * - status(COMPLETED)·fareFinal·pointsUsed·completedAt 불변 (firestore.rules 화이트리스트 정합)
     * - 기록 후 refreshSettlementData() 로 _todaySettlement·_tripHistoryList 권위 재계산
     */
    fun updateCallPaymentMethod(callId: String, newPaymentMethod: String, onResult: (Boolean, String) -> Unit) {
        val provinceId = sharedPreferences.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = sharedPreferences.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = sharedPreferences.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (provinceId.isNullOrBlank() || cityId.isNullOrBlank() || officeId.isNullOrBlank()) {
            onResult(false, "로그인 정보가 없습니다")
            return
        }
        if (newPaymentMethod !in listOf("현금", "외상", "이체")) {
            onResult(false, "현금/외상/이체만 정정할 수 있습니다")
            return
        }

        viewModelScope.launch {
            try {
                val callRef = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_CALLS).document(callId)

                val snapshot = callRef.get().await()
                val fareFinal = snapshot.getLong(Constants.FIELD_FARE_FINAL)?.toInt()
                    ?: snapshot.getLong("fare_set")?.toInt()
                    ?: 0

                // 결제수단별 연동 필드 재계산 (confirmAndFinalizeTrip 로직 정합)
                val updates = hashMapOf<String, Any>(
                    Constants.FIELD_PAYMENT_METHOD to newPaymentMethod
                )
                when (newPaymentMethod) {
                    "현금" -> {
                        updates[Constants.FIELD_CASH_RECEIVED] = fareFinal
                        updates["creditAmount"] = FieldValue.delete()
                    }
                    "외상", "이체" -> {
                        updates["creditAmount"] = fareFinal
                        updates[Constants.FIELD_CASH_RECEIVED] = FieldValue.delete()
                    }
                }
                callRef.update(updates).await()

                // 통계 권위 재계산 (Firestore 재로드 — 수동 delta 없이 drift 0)
                refreshSettlementData()

                Log.d(TAG, "콜 결제수단 정정 완료: callId=$callId → $newPaymentMethod")
                onResult(true, "결제수단을 ${newPaymentMethod}(으)로 정정했습니다")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update call payment method", e)
                onResult(false, "정정 실패: ${e.message}")
            }
        }
    }
}
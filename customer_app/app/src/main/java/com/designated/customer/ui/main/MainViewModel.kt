package com.designated.customer.ui.main

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.designated.customer.data.model.CustomerCall
import com.designated.customer.data.model.CustomerGrade
import com.designated.customer.data.model.CustomerPoints
import com.designated.customer.service.CallService
import com.designated.customer.service.LocationService
import com.designated.customer.service.PointService
import com.designated.customer.service.StepCounterService
import com.designated.customer.data.repository.StepRepository
import com.designated.customer.data.model.DailyStepData
import com.designated.customer.data.model.WeeklyStepSummary
import com.designated.customer.data.model.BannerAdData
import com.designated.customer.service.BannerAdService
import com.designated.customer.data.model.MonthlyStepSummary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class MainUiState(
    val currentLocation: String = "",
    val destinationLocation: String = "",
    val isLoadingLocation: Boolean = false,
    val isLoadingCall: Boolean = false,
    val callStatus: CallStatus? = null,
    val error: String? = null,
    // 사무실 정보
    val officeName: String = "",
    val regionName: String = "",
    // 포인트 관련 상태
    val customerPoints: CustomerPoints? = null,
    val isLoadingPoints: Boolean = false,
    val usePoints: Boolean = false,
    val pointsToUse: Int = 0,
    // 만보기 관련 상태
    val currentStepsRealtime: Int = 0, // 실시간 걸음수 (센서에서 직접)
    val currentSessionSteps: Int = 0, // 세션 걸음수 (리셋 가능한 임시 카운터)
    val isSessionActive: Boolean = false, // 세션 활성화 여부
    val stepData: DailyStepData? = null,
    val weeklyStepData: WeeklyStepSummary? = null,
    val monthlyStepData: MonthlyStepSummary? = null,
    val showStepDetail: Boolean = false,
    // 앱호출 버튼 상태
    val showLocationCard: Boolean = false,
    // 집주소 관련 상태
    val homeAddress: String = "",
    val showHomeAddressDialog: Boolean = false,
    // 음성입력 관련 상태
    val isRecordingDeparture: Boolean = false,
    val isRecordingDestination: Boolean = false,
    // 포인트 적립 팝업 관련
    val showPointsEarnedDialog: Boolean = false,
    val earnedPoints: Int = 0,
    val usedPoints: Int = 0,  // ✅ 추가: 사용한 포인트
    val rideCompletedFare: Int = 0,
    // 배너 광고 관련 상태
    val currentBanner: BannerAdData? = null
) {
    val canRequestCall: Boolean
        get() = currentLocation.isNotEmpty() &&
                destinationLocation.isNotEmpty() &&
                callStatus?.state != CallState.REQUESTED &&
                callStatus?.state != CallState.ASSIGNED &&
                callStatus?.state != CallState.DRIVER_ARRIVING &&
                callStatus?.state != CallState.IN_PROGRESS
}

class MainViewModel(
    private val callService: CallService,
    private val locationService: LocationService,
    private val pointService: PointService,
    private val provinceId: String,
    private val cityId: String,
    private val officeId: String,
    private val phoneNumber: String,
    private val customerInfo: com.designated.customer.data.model.CustomerInfo?,
    private val context: Context? = null,
    // 만보기 관련 서비스
    private val stepRepository: StepRepository? = null,
    private val stepService: StepCounterService? = null
) : ViewModel() {

    var uiState by mutableStateOf(MainUiState())
        private set

    // PreferencesManager 초기화
    private val prefsManager = context?.let { com.designated.customer.util.PreferencesManager(it) }

    // FCM 브로드캐스트 수신기
    private var rideCompletedReceiver: BroadcastReceiver? = null
    private var driverAssignedReceiver: BroadcastReceiver? = null
    private var callCancelledReceiver: BroadcastReceiver? = null
    // 배너 광고 서비스
    private val bannerAdService = BannerAdService(provinceId = provinceId, cityId = cityId, officeId = officeId)

    // 사무실 연락처 정보 (customerInfo에서 추출, 없으면 SharedPreferences에서)
    val officePhone: String get() {
        val phone = customerInfo?.officePhone?.takeIf { it.isNotEmpty() }
            ?: prefsManager?.getOfficePhone() ?: ""
        android.util.Log.d("MainViewModel", "officePhone: $phone (customerInfo: ${customerInfo?.officePhone}, prefs: ${prefsManager?.getOfficePhone()})")
        return phone
    }
    val bankName: String get() = customerInfo?.bankName?.takeIf { it.isNotEmpty() }
        ?: prefsManager?.getBankName() ?: ""
    val accountNumber: String get() = customerInfo?.accountNumber?.takeIf { it.isNotEmpty() }
        ?: prefsManager?.getAccountNumber() ?: ""
    val accountHolder: String get() = customerInfo?.accountHolder?.takeIf { it.isNotEmpty() }
        ?: prefsManager?.getAccountHolder() ?: ""

    init {
        // 사무실 정보 로드
        loadOfficeInfo()
        // 포인트 정보 로드
        loadCustomerPoints()
        // 만보기 서비스 시작
        initStepCounter()
        // 로컬 저장된 집주소/즐겨찾기 로드
        loadLocalAddresses()
        // FCM 브로드캐스트 리스너 등록
        registerRideCompletedReceiver()
        // 활성 콜 복구 (FCM 미수신 시 fallback)
        restoreActiveCall()
        // 배너 광고 로드
        loadBannerAds()

        android.util.Log.d("MainViewModel", "Init - customerInfo.homeAddress: ${customerInfo?.homeAddress}")
    }

    /**
     * 로컬에 저장된 집주소와 즐겨찾기 로드
     * customerInfo에서 먼저 가져오고, 없으면 SharedPreferences에서 가져옴
     */
    private fun loadLocalAddresses() {
        // customerInfo에서 homeAddress 가져오기
        val homeAddrFromCustomer = customerInfo?.homeAddress?.takeIf { it.isNotEmpty() }
        val homeAddrFromPrefs = prefsManager?.getHomeAddress()?.takeIf { it.isNotEmpty() }
        val homeAddr = homeAddrFromCustomer ?: homeAddrFromPrefs ?: ""

        android.util.Log.d("MainViewModel", "loadLocalAddresses - homeAddress: $homeAddr")
        android.util.Log.d("MainViewModel", "  - from customerInfo: $homeAddrFromCustomer")
        android.util.Log.d("MainViewModel", "  - from prefs: $homeAddrFromPrefs")

        uiState = uiState.copy(
            homeAddress = homeAddr
        )
    }

    /**
     * Firebase에서 사무실 정보 로드
     */
    private fun loadOfficeInfo() {
        viewModelScope.launch {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val officeDoc = firestore
                    .collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .document(cityId)
                    .collection("offices")
                    .document(officeId)
                    .get()
                    .await()

                if (officeDoc.exists()) {
                    val officeName = officeDoc.getString("name") ?: officeId
                    val provinceName = when(provinceId) {
                        "seoul" -> "서울"
                        "gyeonggi" -> "경기"
                        "Hongchon" -> "홍천"
                        else -> provinceId
                    }

                    uiState = uiState.copy(
                        officeName = officeName,
                        regionName = provinceName
                    )
                }
            } catch (e: Exception) {
                // 사무실 정보 로드 실패 시 ID 표시
                uiState = uiState.copy(
                    officeName = officeId,
                    regionName = provinceId
                )
            }
        }
    }

    /**
     * 앱 시작 시 Firestore에서 활성 콜 1회 조회 (FCM 미수신 fallback)
     */
    private fun restoreActiveCall() {
        viewModelScope.launch {
            try {
                if (uiState.callStatus != null) return@launch

                val activeCall = callService.getActiveCall(phoneNumber) ?: return@launch

                val callState = when (activeCall.status) {
                    "WAITING" -> CallState.REQUESTED
                    "ASSIGNED" -> CallState.ASSIGNED
                    "ACCEPTED" -> CallState.DRIVER_ARRIVING
                    "IN_PROGRESS" -> CallState.IN_PROGRESS
                    else -> return@launch
                }

                uiState = uiState.copy(
                    callStatus = CallStatus(
                        callId = activeCall.id,
                        state = callState,
                        timestamp = activeCall.timestamp
                    )
                )
                android.util.Log.i("MainViewModel", "활성 콜 복구: ${activeCall.id}, state=$callState")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "활성 콜 복구 실패", e)
            }
        }
    }

    fun updateCurrentLocation(location: String) {
        uiState = uiState.copy(currentLocation = location, error = null)
    }

    fun updateDestinationLocation(location: String) {
        uiState = uiState.copy(destinationLocation = location, error = null)
    }

    fun getCurrentLocation() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingLocation = true, error = null)

            try {
                val location = locationService.getCurrentLocation()
                uiState = uiState.copy(
                    currentLocation = location,
                    isLoadingLocation = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingLocation = false,
                    error = "현재 위치를 가져올 수 없습니다: ${e.message}"
                )
            }
        }
    }

    fun requestCall() {
        if (!uiState.canRequestCall) {
            uiState = uiState.copy(error = "콜을 요청할 수 없는 상태입니다")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoadingCall = true, error = null)

            try {
                // 포인트 사용 처리
                val usedPointsAmount = if (uiState.usePoints && uiState.pointsToUse > 0) {
                    val pointsUsed = pointService.usePoints(
                        phoneNumber = phoneNumber,
                        amount = uiState.pointsToUse,
                        description = "대리운전 콜 요청 시 포인트 사용"
                    )

                    if (!pointsUsed) {
                        uiState = uiState.copy(
                            isLoadingCall = false,
                            error = "포인트 사용에 실패했습니다"
                        )
                        return@launch
                    }

                    // 포인트 사용 성공 시 팝업 표시
                    uiState = uiState.copy(
                        showPointsEarnedDialog = true,
                        earnedPoints = 0,  // 사용이므로 적립은 0
                        usedPoints = uiState.pointsToUse,
                        rideCompletedFare = 0  // 콜 요청 시점이므로 요금은 0
                    )

                    uiState.pointsToUse
                } else {
                    0
                }

                // 고객 정보 가져오기
                val customerName = customerInfo?.name ?: phoneNumber

                val call = CustomerCall(
                    phoneNumber = phoneNumber,
                    officeId = officeId,
                    provinceId = provinceId,
                    cityId = cityId,
                    currentLocation = uiState.currentLocation,
                    destinationLocation = uiState.destinationLocation,
                    timestamp = System.currentTimeMillis(),
                    status = "WAITING",  // "REQUESTED" → "WAITING" 변경 (전화 호출과 동일)
                    customerId = phoneNumber,
                    customerName = customerName,  // 고객 이름 설정
                    customerGrade = uiState.customerPoints?.grade?.name ?: "BRONZE",
                    // 포인트 사용 정보 추가
                    pointsUsed = if (uiState.usePoints) uiState.pointsToUse else 0
                )

                // 콜 요청 시도 (실패 시 1회 재시도)
                val callId = try {
                    callService.requestCall(call)
                } catch (firstError: Exception) {
                    android.util.Log.w("MainViewModel", "콜 요청 첫 번째 시도 실패, 재시도 중...", firstError)
                    delay(1000) // 1초 대기 후 재시도
                    callService.requestCall(call) // 재시도
                }

                // 콜 상태를 업데이트하고 포인트 사용 상태 초기화, 바텀시트 닫기
                uiState = uiState.copy(
                    isLoadingCall = false,
                    callStatus = CallStatus(
                        callId = callId,
                        state = CallState.REQUESTED,
                        timestamp = System.currentTimeMillis()
                    ),
                    usePoints = false,
                    pointsToUse = 0,
                    showLocationCard = false  // 바텀시트 닫기
                )
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "콜 요청 최종 실패 (재시도 후)", e)

                // 포인트 사용했었다면 환불 처리
                if (usedPointsAmount > 0) {
                    val phoneNumber = customerInfo?.phoneNumber ?: ""
                    if (phoneNumber.isNotEmpty()) {
                        val refunded = pointService.refundPoints(
                            phoneNumber = phoneNumber,
                            amount = usedPointsAmount,
                            description = "콜 요청 실패로 인한 포인트 환불"
                        )
                        if (refunded) {
                            android.util.Log.i("MainViewModel", "포인트 ${usedPointsAmount}P 환불 완료")
                        } else {
                            android.util.Log.e("MainViewModel", "포인트 환불 실패 - 수동 조정 필요: ${usedPointsAmount}P")
                        }
                    }
                }

                uiState = uiState.copy(
                    isLoadingCall = false,
                    error = "콜 요청에 실패했습니다. 네트워크 연결을 확인해주세요."
                )
                // 시스템 알림으로 손님에게 안내
                showCallFailedNotification()
            }
        }
    }

    fun cancelCall() {
        val currentCallId = uiState.callStatus?.callId ?: run {
            android.util.Log.d("MainViewModel", "cancelCall: callId is null")
            return
        }

        android.util.Log.d("MainViewModel", "cancelCall started: callId=$currentCallId")

        viewModelScope.launch {
            try {
                val success = callService.cancelCall(currentCallId)
                android.util.Log.d("MainViewModel", "cancelCall result: success=$success")

                if (success) {
                    // 취소 성공 시 callStatus를 null로 설정하여 UI에서 제거
                    uiState = uiState.copy(
                        callStatus = null
                    )
                    android.util.Log.d("MainViewModel", "callStatus set to null")
                } else {
                    uiState = uiState.copy(
                        error = "콜 취소에 실패했습니다"
                    )
                    android.util.Log.e("MainViewModel", "cancelCall failed")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "cancelCall exception: ${e.message}", e)
                uiState = uiState.copy(
                    error = "콜 취소 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    /**
     * FCM 브로드캐스트 리스너 등록
     */
    private fun registerRideCompletedReceiver() {
        // 배너 광고 로드
        loadBannerAds()
        context?.let { ctx ->
            // 운행 완료 브로드캐스트 수신
            val completedFilter = IntentFilter("com.designated.customer.RIDE_COMPLETED")
            val completedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val fare = intent?.getIntExtra("fare", 0) ?: 0
                    val pointsUsed = intent?.getIntExtra("pointsUsed", 0) ?: 0

                    android.util.Log.d("MainViewModel", "운행 완료 브로드캐스트 수신 - fare: $fare, pointsUsed: $pointsUsed")

                    // 포인트 적립 팝업 표시 로직 실행
                    handleRideCompleted(fare, pointsUsed)
                }
            }
            LocalBroadcastManager.getInstance(ctx).registerReceiver(completedReceiver, completedFilter)

            // 기사 배정 브로드캐스트 수신
            val assignedFilter = IntentFilter("com.designated.customer.DRIVER_ASSIGNED")
            val assignedReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val callId = intent?.getStringExtra("callId")
                    val driverName = intent?.getStringExtra("driverName") ?: "기사"
                    val driverPhone = intent?.getStringExtra("driverPhone") ?: ""
                    val vehicleNumber = intent?.getStringExtra("vehicleNumber") ?: ""
                    val driverId = intent?.getStringExtra("driverId") ?: ""

                    android.util.Log.d("MainViewModel", "기사 배정 브로드캐스트 수신 - callId: $callId, driverName: $driverName")

                    // 기사 배정 팝업 표시
                    handleDriverAssigned(callId, driverName, driverPhone, vehicleNumber, driverId)
                }
            }
            LocalBroadcastManager.getInstance(ctx).registerReceiver(assignedReceiver, assignedFilter)

            // ✅ 추가: 콜 취소 브로드캐스트 수신
            val cancelledFilter = IntentFilter("com.designated.customer.CALL_CANCELLED")
            val cancelledReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val callId = intent?.getStringExtra("callId")
                    val cancelReason = intent?.getStringExtra("cancelReason") ?: "운행취소"

                    android.util.Log.d("MainViewModel", "콜 취소 브로드캐스트 수신 - callId: $callId, reason: $cancelReason")

                    // 팝업 제거
                    uiState = uiState.copy(callStatus = null)
                }
            }
            LocalBroadcastManager.getInstance(ctx).registerReceiver(cancelledReceiver, cancelledFilter)

            // 리시버 참조 저장 (onCleared에서 해제 위해)
            rideCompletedReceiver = completedReceiver
            driverAssignedReceiver = assignedReceiver
            callCancelledReceiver = cancelledReceiver

            android.util.Log.d("MainViewModel", "브로드캐스트 리스너 등록 완료")
        }
    }

    /**
     * 기사 배정 처리 - FCM 알림 수신 시 호출
     */
    private fun handleDriverAssigned(
        callId: String?,
        driverName: String,
        driverPhone: String,
        vehicleNumber: String,
        driverId: String
    ) {
        android.util.Log.d("MainViewModel", "기사 배정 처리 시작 - callId: $callId, driverName: $driverName")

        // 기사 배정 알람 (진동)
        playDriverAssignedNotification()

        // 팝업 표시
        val driverInfo = DriverInfo(
            id = driverId,
            name = driverName,
            phoneNumber = driverPhone,
            vehicleNumber = vehicleNumber
        )

        val callStatus = CallStatus(
            callId = callId ?: "",
            state = CallState.ASSIGNED,
            timestamp = System.currentTimeMillis(),
            driverInfo = driverInfo,
            estimatedArrivalTime = 0
        )

        uiState = uiState.copy(
            callStatus = callStatus
        )

        android.util.Log.d("MainViewModel", "기사 배정 팝업 표시 완료")
    }

    /**
     * 운행 완료 처리 - FCM 알림 수신 시 호출
     */
    private fun handleRideCompleted(fare: Int, pointsUsed: Int) {
        viewModelScope.launch {
            android.util.Log.d("MainViewModel", "운행 완료 처리 시작 - fare: $fare, pointsUsed: $pointsUsed")

            // 포인트 정보 재조회
            loadCustomerPoints()

            // 적립 포인트 계산
            val points = uiState.customerPoints
            val earnedPoints = points?.calculateEarnPoints(fare) ?: 0

            android.util.Log.d("MainViewModel", "적립 포인트: $earnedPoints")

            // 팝업 표시
            uiState = uiState.copy(
                callStatus = null,  // 콜 상태 팝업 제거
                showPointsEarnedDialog = true,
                earnedPoints = earnedPoints,
                usedPoints = pointsUsed,
                rideCompletedFare = fare
            )
        }
    }

    /**
     * DEPRECATED: 실시간 리스너 방식 (FCM으로 대체됨)
     * 더 이상 사용하지 않음 - FCM 기반 알림 시스템으로 전환
     */
    /*
    private fun monitorCallStatus_DEPRECATED() {
        var previousDriverId: String? = null
        var lastActiveCallId: String? = null

        viewModelScope.launch {
            callService.monitorCallStatus(phoneNumber) { callStatus ->
                android.util.Log.d("MainViewModel", "monitorCallStatus: callStatus=$callStatus, state=${callStatus?.state}, callId=${callStatus?.callId}")

                // 기사 배정 감지 (이전에 기사가 없었는데 지금 기사가 배정됨)
                val currentDriverId = callStatus?.driverInfo?.id
                if (previousDriverId == null && currentDriverId != null) {
                    android.util.Log.d("MainViewModel", "기사 배정 감지: driverId=$currentDriverId")
                    // 기사 배정 알람 (진동)
                    playDriverAssignedNotification()
                }
                previousDriverId = currentDriverId

                // 콜 완료 감지: 활성 콜이 사라졌을 때 (COMPLETED로 전환됨)
                val currentCallId = callStatus?.callId
                if (lastActiveCallId != null && currentCallId == null) {
                    android.util.Log.d("MainViewModel", "활성 콜 사라짐 감지. 완료 여부 확인: callId=$lastActiveCallId")
                    // COMPLETED로 전환되었는지 확인하고 포인트 적립
                    checkAndHandleCompletedCall(lastActiveCallId!!)
                }
                lastActiveCallId = currentCallId

                uiState = uiState.copy(callStatus = callStatus)
            }
        }
    }
    */

    /**
     * 콜이 COMPLETED 상태로 전환되었는지 확인하고 포인트 적립 처리
     */
    private fun checkAndHandleCompletedCall(callId: String) {
        viewModelScope.launch {
            try {
                val callDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId)
                    .collection("calls")
                    .document(callId)
                    .get()
                    .await()

                val status = callDoc.getString("status")
                android.util.Log.d("MainViewModel", "checkAndHandleCompletedCall: callId=$callId, status=$status")

                if (status == "COMPLETED") {
                    android.util.Log.d("MainViewModel", "운행 완료 확인! 포인트 적립 시작")
                    // 운행 완료 시 포인트 적립
                    awardPointsForCompletedRide()
                    // callStatus는 이미 null이므로 팝업이 자동으로 사라짐
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "checkAndHandleCompletedCall 오류", e)
            }
        }
    }

    private fun playDriverAssignedNotification() {
        try {
            // 진동 알람 (1번)
            val vibrator = context?.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                vibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(500, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(500)
            }

            // 알림음 재생
            try {
                val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = android.media.RingtoneManager.getRingtone(context, notification)
                ringtone?.play()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "알림음 재생 실패", e)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainViewModel", "진동 알람 실패", e)
        }
    }

    private fun awardPointsForCompletedRide() {
        viewModelScope.launch {
            try {
                val callId = uiState.callStatus?.callId ?: return@launch

                // Firestore에서 콜 정보를 가져와서 요금 확인
                val callDoc = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId)
                    .collection("calls")
                    .document(callId)
                    .get()
                    .await()

                val pointsUsed = callDoc.getLong("pointsUsed")?.toInt() ?: 0
                val fare = callDoc.getLong("fare_set")?.toInt()
                    ?: callDoc.getLong("finalFare")?.toInt()
                    ?: callDoc.getLong("fare")?.toInt()
                    ?: 0

                // 포인트 적립은 Cloud Functions에서 일원화 처리 (BUG-D12 수정)
                // 여기서는 팝업 표시 + 최신 포인트 정보 로드만 수행
                val points = uiState.customerPoints
                val earnAmount = points?.calculateEarnPoints(fare) ?: 0

                // 알림음 재생
                try {
                    val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    val ringtone = android.media.RingtoneManager.getRingtone(context, notification)
                    ringtone?.play()
                } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "알림음 재생 실패", e)
                }

                // 팝업 표시 (적립은 CF에서 처리되므로 예상 적립 포인트만 표시)
                uiState = uiState.copy(
                    showPointsEarnedDialog = true,
                    earnedPoints = earnAmount,
                    usedPoints = pointsUsed,
                    rideCompletedFare = fare
                )

                // CF에서 적립 완료된 최신 포인트 정보 로드
                loadCustomerPoints()

                android.util.Log.d("MainViewModel", "운행 완료 - 포인트 팝업 표시 (적립은 CF에서 처리): fare=$fare, earnAmount=$earnAmount")
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "운행 완료 포인트 처리 중 오류", e)
            }
        }
    }

    fun dismissPointsEarnedDialog() {
        uiState = uiState.copy(showPointsEarnedDialog = false)
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    /**
     * 콜 요청 실패 시 시스템 알림 표시
     * 손님이 앱을 닫아도 알림을 볼 수 있도록 함
     */
    private fun showCallFailedNotification() {
        context?.let { ctx ->
            val channelId = "call_failed_channel"
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Android 8.0 이상에서 알림 채널 생성
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "콜 요청 실패 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "앱 호출 실패 시 알림"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // 앱을 열기 위한 PendingIntent
            val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
            val pendingIntent = PendingIntent.getActivity(
                ctx,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 알림 생성
            val notification = NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("앱 호출 실패")
                .setContentText("네트워크 연결 확인 후 다시 시도하거나 전화호출 버튼을 눌러주세요")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("네트워크 연결 확인 후 다시 시도하거나 전화호출 버튼을 눌러주세요"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            notificationManager.notify(1001, notification)

            android.util.Log.d("MainViewModel", "콜 요청 실패 시스템 알림 표시")
        }
    }

    // 포인트 관련 메서드들
    private fun loadCustomerPoints() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingPoints = true)
            try {
                val points = pointService.getCustomerPoints(phoneNumber)
                uiState = uiState.copy(
                    customerPoints = points,
                    isLoadingPoints = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingPoints = false,
                    error = "포인트 정보를 불러올 수 없습니다"
                )
            }
        }
    }


    fun toggleUsePoints() {
        val maxPoints = uiState.customerPoints?.currentPoints ?: 0
        if (uiState.usePoints) {
            // 포인트 사용 취소
            uiState = uiState.copy(usePoints = false, pointsToUse = 0)
        } else {
            // 포인트 사용 (최대 사용 가능 포인트로 설정)
            uiState = uiState.copy(
                usePoints = true,
                pointsToUse = minOf(maxPoints, 10000) // 최대 10,000포인트까지 사용
            )
        }
    }

    fun updatePointsToUse(points: Int) {
        val maxPoints = uiState.customerPoints?.currentPoints ?: 0
        uiState = uiState.copy(
            pointsToUse = minOf(points, maxPoints)
        )
    }

    // ========== 만보기 관련 메서드 ==========

    /**
     * 만보기 초기화 및 데이터 모니터링 시작
     */
    private fun initStepCounter() {
        // Service는 이미 onCreate에서 startListening을 호출하므로 여기서는 호출하지 않음

        // ✅ 센서의 실시간 걸음수 모니터링 (즉각 반응용)
        stepService?.let { service ->
            viewModelScope.launch {
                service.currentSteps.collectLatest { realtimeSteps ->
                    uiState = uiState.copy(currentStepsRealtime = realtimeSteps)
                }
            }

            // 세션 걸음수 모니터링
            viewModelScope.launch {
                service.sessionSteps.collectLatest { sessionSteps ->
                    uiState = uiState.copy(currentSessionSteps = sessionSteps)
                }
            }

            // 세션 활성화 상태 모니터링
            viewModelScope.launch {
                service.isSessionActive.collectLatest { isActive ->
                    uiState = uiState.copy(isSessionActive = isActive)
                }
            }
        }

        // 일일 걸음 수 모니터링 (DB 기반 - 상세 정보용)
        stepRepository?.let { repo ->
            viewModelScope.launch {
                repo.getTodayStepsFlow().collectLatest { stepData ->
                    uiState = uiState.copy(stepData = stepData)
                }
            }

            // 주간 집계 모니터링
            viewModelScope.launch {
                repo.getThisWeekSummaryFlow().collectLatest { weeklyData ->
                    uiState = uiState.copy(weeklyStepData = weeklyData)
                }
            }

            // 월간 집계 모니터링
            viewModelScope.launch {
                repo.getThisMonthSummaryFlow().collectLatest { monthlyData ->
                    uiState = uiState.copy(monthlyStepData = monthlyData)
                }
            }
        }
    }

    /**
     * 만보기 상세 정보 표시 토글
     */
    fun toggleStepDetail() {
        uiState = uiState.copy(showStepDetail = !uiState.showStepDetail)
    }

    /**
     * 만보기 상세 정보 닫기
     */
    fun closeStepDetail() {
        uiState = uiState.copy(showStepDetail = false)
    }

    /**
     * 위치 카드 표시 토글
     */
    fun toggleLocationCard() {
        uiState = uiState.copy(showLocationCard = !uiState.showLocationCard)
    }

    // ========== 집주소/즐겨찾기 관련 메서드 ==========

    /**
     * 집주소 아이콘 클릭
     */
    fun onHomeAddressClick() {
        android.util.Log.d("MainViewModel", "onHomeAddressClick - uiState.homeAddress: '${uiState.homeAddress}'")
        android.util.Log.d("MainViewModel", "onHomeAddressClick - customerInfo.homeAddress: '${customerInfo?.homeAddress}'")

        if (uiState.homeAddress.isNotEmpty()) {
            // 집주소가 있으면 목적지에 자동 입력
            android.util.Log.d("MainViewModel", "집주소 자동 입력: ${uiState.homeAddress}")
            uiState = uiState.copy(destinationLocation = uiState.homeAddress)
        } else {
            // 집주소가 없으면 추가 다이얼로그 표시
            android.util.Log.d("MainViewModel", "집주소가 비어있음, 다이얼로그 표시")
            uiState = uiState.copy(showHomeAddressDialog = true)
        }
    }

    /**
     * 집주소 변경 아이콘 클릭
     */
    fun onEditHomeAddress() {
        uiState = uiState.copy(showHomeAddressDialog = true)
    }

    /**
     * 집주소 저장
     */
    fun saveHomeAddress(address: String) {
        prefsManager?.saveHomeAddress(address)
        // 목적지에 자동 입력 및 UI 상태 업데이트
        uiState = uiState.copy(
            homeAddress = address,
            destinationLocation = address,
            showHomeAddressDialog = false
        )
    }

    /**
     * 집주소 다이얼로그 닫기
     */
    fun closeHomeAddressDialog() {
        uiState = uiState.copy(showHomeAddressDialog = false)
    }

    /**
     * 출발지 음성 인식 시작
     */
    fun startRecordingDeparture() {
        uiState = uiState.copy(isRecordingDeparture = true)
    }

    /**
     * 출발지 음성 인식 중지 및 결과 처리
     */
    fun stopRecordingDeparture(result: String) {
        uiState = uiState.copy(
            currentLocation = result,
            isRecordingDeparture = false
        )
    }

    /**
     * 목적지 음성 인식 시작
     */
    fun startRecordingDestination() {
        uiState = uiState.copy(isRecordingDestination = true)
    }

    /**
     * 목적지 음성 인식 중지 및 결과 처리
     */
    fun stopRecordingDestination(result: String) {
        uiState = uiState.copy(
            destinationLocation = result,
            isRecordingDestination = false
        )
    }

    /**
     * 음성 인식 취소
     */
    fun cancelRecording() {
        uiState = uiState.copy(
            isRecordingDeparture = false,
            isRecordingDestination = false
        )
    }

    // ========== 세션 관련 메서드 ==========

    /**
     * 새로운 걸음 세션 시작
     */
    fun startNewSession() {
        stepService?.startNewSession()
    }

    /**
     * 세션 리셋 (0부터 다시 시작)
     */
    fun resetSession() {
        stepService?.resetSession()
    }

    /**
     * 세션 종료
     */
    fun endSession() {
        stepService?.endSession()
    }

    /**
     * ViewModel 종료 시 처리
     * Service는 백그라운드에서 계속 실행되므로 stopListening을 호출하지 않음
     */
    /**
     * 배너 광고 로드
     * Firebase Firestore에서 활성화된 배너를 실시간으로 모니터링
     */
    private fun loadBannerAds() {
        viewModelScope.launch {
            try {
                bannerAdService.observeActiveBanners().collectLatest { banners ->
                    android.util.Log.d("MainViewModel", "배너 광고 로드: ${banners.size}개")
                    // 우선순위가 가장 높은 배너 1개만 표시
                    uiState = uiState.copy(
                        currentBanner = banners.firstOrNull()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "배너 광고 로드 실패", e)
            }
        }
    }


    override fun onCleared() {
        super.onCleared()
        // Service는 계속 실행되어야 하므로 stopListening 호출하지 않음

        // FCM 브로드캐스트 리스너 해제
        context?.let { ctx ->
            val localBroadcast = LocalBroadcastManager.getInstance(ctx)

            rideCompletedReceiver?.let { receiver ->
                localBroadcast.unregisterReceiver(receiver)
                android.util.Log.d("MainViewModel", "운행 완료 브로드캐스트 리스너 해제 완료")
            }

            driverAssignedReceiver?.let { receiver ->
                localBroadcast.unregisterReceiver(receiver)
                android.util.Log.d("MainViewModel", "기사 배정 브로드캐스트 리스너 해제 완료")
            }

            callCancelledReceiver?.let { receiver ->
                localBroadcast.unregisterReceiver(receiver)
                android.util.Log.d("MainViewModel", "콜 취소 브로드캐스트 리스너 해제 완료")
            }
        }
    }
}
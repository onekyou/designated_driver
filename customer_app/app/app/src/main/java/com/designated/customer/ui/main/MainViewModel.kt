package com.designated.customer.ui.main

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import com.designated.customer.data.model.MonthlyStepSummary
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
    // 집주소/즐겨찾기 관련 상태
    val homeAddress: String = "",
    val favoriteAddresses: List<String> = emptyList(),
    val showHomeAddressDialog: Boolean = false,
    val showFavoriteAddressSheet: Boolean = false
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
    private val regionId: String,
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

    // 사무실 연락처 정보 (customerInfo에서 추출)
    val officePhone: String get() = customerInfo?.officePhone ?: ""
    val bankName: String get() = customerInfo?.bankName ?: ""
    val accountNumber: String get() = customerInfo?.accountNumber ?: ""
    val accountHolder: String get() = customerInfo?.accountHolder ?: ""

    init {
        // 사무실 정보 로드
        loadOfficeInfo()
        // 활성 콜 상태 모니터링 시작
        monitorCallStatus()
        // 포인트 정보 로드
        loadCustomerPoints()
        // 포인트 실시간 모니터링
        observeCustomerPoints()
        // 만보기 서비스 시작
        initStepCounter()
        // 로컬 저장된 집주소/즐겨찾기 로드
        loadLocalAddresses()
    }

    /**
     * 로컬에 저장된 집주소와 즐겨찾기 로드
     */
    private fun loadLocalAddresses() {
        prefsManager?.let {
            uiState = uiState.copy(
                homeAddress = it.getHomeAddress(),
                favoriteAddresses = it.getFavoriteAddresses()
            )
        }
    }

    /**
     * Firebase에서 사무실 정보 로드
     */
    private fun loadOfficeInfo() {
        viewModelScope.launch {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val officeDoc = firestore
                    .collection("regions")
                    .document(regionId)
                    .collection("offices")
                    .document(officeId)
                    .get()
                    .await()

                if (officeDoc.exists()) {
                    val officeName = officeDoc.getString("name") ?: officeId
                    val regionName = when(regionId) {
                        "seoul" -> "서울"
                        "gyeonggi" -> "경기"
                        "Hongchon" -> "홍천"
                        else -> regionId
                    }

                    uiState = uiState.copy(
                        officeName = officeName,
                        regionName = regionName
                    )
                }
            } catch (e: Exception) {
                // 사무실 정보 로드 실패 시 ID 표시
                uiState = uiState.copy(
                    officeName = officeId,
                    regionName = regionId
                )
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
                if (uiState.usePoints && uiState.pointsToUse > 0) {
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
                }

                val call = CustomerCall(
                    phoneNumber = phoneNumber,
                    officeId = officeId,
                    regionId = regionId,
                    currentLocation = uiState.currentLocation,
                    destinationLocation = uiState.destinationLocation,
                    timestamp = System.currentTimeMillis(),
                    status = "REQUESTED",
                    customerId = phoneNumber,
                    customerGrade = uiState.customerPoints?.grade?.name ?: "BRONZE",
                    // 포인트 사용 정보 추가
                    pointsUsed = if (uiState.usePoints) uiState.pointsToUse else 0
                )

                val callId = callService.requestCall(call)

                // 콜 상태를 업데이트하고 포인트 사용 상태 초기화
                uiState = uiState.copy(
                    isLoadingCall = false,
                    callStatus = CallStatus(
                        callId = callId,
                        state = CallState.REQUESTED,
                        timestamp = System.currentTimeMillis()
                    ),
                    usePoints = false,
                    pointsToUse = 0
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingCall = false,
                    error = "콜 요청 중 오류가 발생했습니다: ${e.message}"
                )
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

    private fun monitorCallStatus() {
        viewModelScope.launch {
            callService.monitorCallStatus(phoneNumber) { callStatus ->
                uiState = uiState.copy(callStatus = callStatus)
            }
        }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
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

    private fun observeCustomerPoints() {
        viewModelScope.launch {
            pointService.observeCustomerPoints(phoneNumber).collectLatest { points ->
                uiState = uiState.copy(customerPoints = points)
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
        stepService?.startListening()

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
        if (uiState.homeAddress.isNotEmpty()) {
            // 집주소가 있으면 목적지에 자동 입력
            uiState = uiState.copy(destinationLocation = uiState.homeAddress)
        } else {
            // 집주소가 없으면 추가 다이얼로그 표시
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
     * 즐겨찾기 바텀시트 열기
     */
    fun openFavoriteAddressSheet() {
        uiState = uiState.copy(showFavoriteAddressSheet = true)
    }

    /**
     * 즐겨찾기 바텀시트 닫기
     */
    fun closeFavoriteAddressSheet() {
        uiState = uiState.copy(showFavoriteAddressSheet = false)
    }

    /**
     * 즐겨찾기 주소 선택
     */
    fun selectFavoriteAddress(address: String) {
        uiState = uiState.copy(
            destinationLocation = address,
            showFavoriteAddressSheet = false
        )
    }

    /**
     * 즐겨찾기 주소 추가
     */
    fun addFavoriteAddress(address: String) {
        prefsManager?.addFavoriteAddress(address)
        // UI 상태 업데이트
        uiState = uiState.copy(
            favoriteAddresses = prefsManager?.getFavoriteAddresses() ?: emptyList()
        )
    }

    /**
     * 즐겨찾기 주소 삭제
     */
    fun deleteFavoriteAddress(address: String) {
        prefsManager?.removeFavoriteAddress(address)
        // UI 상태 업데이트
        uiState = uiState.copy(
            favoriteAddresses = prefsManager?.getFavoriteAddresses() ?: emptyList()
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
     * ViewModel 종료 시 센서 리스닝 중지
     */
    override fun onCleared() {
        super.onCleared()
        stepService?.stopListening()
    }
}
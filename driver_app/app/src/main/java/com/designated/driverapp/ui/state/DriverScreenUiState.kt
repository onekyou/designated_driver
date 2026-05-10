package com.designated.driverapp.ui.state

import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.DriverStatus

/**
 * Driver App 화면의 모든 UI 상태를 나타내는 단일 데이터 클래스입니다.
 * Single Source of Truth 패턴을 구현합니다.
 */
data class DriverScreenUiState(
    val driverStatus: DriverStatus = DriverStatus.OFFLINE,
    val assignedCalls: List<CallInfo> = emptyList(),
    val completedCalls: List<CallInfo> = emptyList(),
    val activeCall: CallInfo? = null,
    val callForSettlement: CallInfo? = null,
    val newCallPopup: CallInfo? = null,
    /**
     * 예약 콜(RESERVED). 운행중 기사에게 매니저가 다음 콜로 약속해둔 상태.
     * 운행 중 화면 하단 또는 WAITING 복귀 시 reserved 카드로 표시.
     */
    val reservedCall: CallInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val locationFetchStatus: LocationFetchStatus = LocationFetchStatus.Idle,
    val navigateToHome: Boolean = false,
    val navigateToHistorySettlement: Boolean = false,
    val isServiceBound: Boolean = false
)

/**
 * 위치 정보 조회 상태를 나타내는 Sealed Class
 */
sealed class LocationFetchStatus {
    object Idle : LocationFetchStatus()
    object Loading : LocationFetchStatus()
    data class Success(val address: String) : LocationFetchStatus()
    data class Error(val message: String) : LocationFetchStatus()
}

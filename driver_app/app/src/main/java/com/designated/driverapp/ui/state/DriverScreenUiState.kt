package com.designated.driverapp.ui.state

import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.DriverStatus

/**
 * Driver App 화면의 모든 UI 상태를 나타내는 단일 데이터 클래스입니다.
 * Single Source of Truth 패턴을 구현합니다.
 */
data class DriverScreenUiState(
    // 기사 상태 (온라인, 오프라인, 운행중 등)
    val driverStatus: DriverStatus = DriverStatus.OFFLINE,
    // 기사에게 배정된 전체 콜 목록
    val assignedCalls: List<CallInfo> = emptyList(),
    // 완료된 콜 목록
    val completedCalls: List<CallInfo> = emptyList(),
    // 현재 활성화(선택)된 콜의 상세 정보
    val activeCall: CallInfo? = null,
    // 정산이 필요하여 정산 팝업을 띄워야 하는 콜 정보
    val callForSettlement: CallInfo? = null,
    // 새로 배정되어 팝업으로 알려줘야 하는 콜 정보
    val newCallPopup: CallInfo? = null,
    // 데이터 로딩 중 상태 표시
    val isLoading: Boolean = false,
    // 오류 메시지
    val errorMessage: String? = null,
    // 위치 정보 조회 과정의 상태
    val locationFetchStatus: LocationFetchStatus = LocationFetchStatus.Idle,
    // 홈 화면으로의 네비게이션 트리거
    val navigateToHome: Boolean = false,
    // ★★★ 히스토리/정산 스크린으로의 네비게이션 트리거 ★★★
    val navigateToHistorySettlement: Boolean = false,
    // 포그라운드 서비스 바인딩 상태
    val isServiceBound: Boolean = false
)

/**
 * 위치 정보 조회 상태를 나타내는 Sealed Class
 */
sealed class LocationFetchStatus {
    object Idle : LocationFetchStatus() // 초기 상태
    object Loading : LocationFetchStatus() // 로딩 중
    data class Success(val address: String) : LocationFetchStatus() // 성공
    data class Error(val message: String) : LocationFetchStatus() // 오류 발생
}

package com.designated.driverapp.enforcement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * 게이트 #3 DailyCloseGate — 앱 시작 시 *이전 영업일*의 미정산 운행이 남아 있으면
 * 정산화면으로 1회 강제 이동.
 *
 * "미정산" 판정(이전 영업일 운행 잔존 여부)은 [com.designated.driverapp.viewmodel.DriverViewModel]의
 * `needsDailyClose` 플래그(영업일 10시 경계 기준, 읽기 전용 계산)가 담당하고,
 * 여기서는 그 플래그 + 현재 화면(정산화면 아님)일 때 redirect 트리거만 수행한다.
 *
 * ⚠️ 단순 `tripCount>0`이 아니라 *이전 영업일* 잔존이라야 함 —
 * 근무 중 완료 운행 누적으로 정산화면에 튕기는 것을 막기 위함.
 */
@Composable
fun DailyCloseRedirect(
    needsDailyClose: Boolean,
    isOnSettlementRoute: Boolean,
    onRedirect: () -> Unit,
) {
    LaunchedEffect(needsDailyClose, isOnSettlementRoute) {
        if (needsDailyClose && !isOnSettlementRoute) {
            onRedirect()
        }
    }
}

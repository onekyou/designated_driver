package com.designated.driverapp.enforcement

import com.designated.driverapp.model.DriverStatus

/**
 * P7 강제 게이트 (기사 측) — 정산 상태를 *읽기만* 하는 순수 판정.
 *
 * 정산 데이터(금액·세션)는 절대 수정하지 않는다. 흐름 차단 여부만 계산.
 * (정산 로직은 [com.designated.driverapp.viewmodel.DriverViewModel] 그대로 유지.)
 */
object EnforcementGate {

    /**
     * 게이트 #1/#2 — 미정산 여부 (로그아웃/앱종료 차단용).
     *
     * 완료된 운행([tripCount] > 0)이 있는데 아직 업무마감(제출)을 안 한 상태면 차단.
     * 제출 후([DriverStatus.PENDING_CONFIRM])는 마감 완료·매니저 확인 대기이므로
     * 정상 종료(퇴근하기)를 허용해야 하므로 false.
     *
     * ⚠️ 근무 중 완료 운행이 쌓이는 동안에도 true가 되는 것이 정상 —
     * 기사는 업무마감 전엔 로그아웃하면 안 되기 때문. (게이트 #3은 별도 조건.)
     */
    fun isDriverUnsettled(driverStatus: DriverStatus, tripCount: Int): Boolean =
        tripCount > 0 && driverStatus != DriverStatus.PENDING_CONFIRM
}

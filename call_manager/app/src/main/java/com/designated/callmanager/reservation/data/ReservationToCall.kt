package com.designated.callmanager.reservation.data

import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.Constants

/**
 * 통화예약 엔진 출력(UniversalReservation) → 콜매니저 CallInfo 매핑.
 *
 * 콜 생성 진입점 = CallRepository.createCall(callInfo, province, city, office) —
 * regionId/officeId/timestamp 정합은 거기서 처리하므로 여기서는 **내용 필드만** 채운다.
 *
 * ⚠️ 운행/정산 전용 필드(trip_summary / isSummaryConfirmed / summaryConfirmedTimestamp)는
 *    절대 set 하지 않는다 — 이들은 기사 운행완료·정산 확인 플로우 전용(건드리면 정산 오염).
 *    써머리확인은 별도 플래그 없이 [확인]=콜 생성 자체로 처리한다.
 *
 * 첫 증분 정책: 미래시각 예약도 status=WAITING(일반 신규콜)로 생성하고 datetimeText 는 memoText 로 노출.
 *    (datetime 기준 자동 활성화/스케줄은 다음 증분 — 기존 RESERVED 배차 개념과 분리.)
 */

/** memoText 조립 — 대리는 from/to/fare 가 전용 필드로 가고, 시각·서비스·비고는 메모로. */
private fun UniversalReservation.composeMemo(): String? {
    val parts = listOfNotNull(
        datetimeText?.takeIf { it.isNotBlank() }?.let { "예약시각: $it" },
        service?.takeIf { it.isNotBlank() }?.let { "서비스: $it" },
        partySize?.let { "인원: ${it}명" },
        notes?.takeIf { it.isNotBlank() },
    )
    return parts.joinToString(" / ").ifBlank { null }
}

/**
 * @param parsed 파일명 파싱 결과(전화번호 폴백·없으면 null)
 * @return 내용이 채워진 CallInfo. 호출부가 createCall 로 office 컨텍스트와 함께 저장.
 */
fun UniversalReservation.toCallInfo(parsed: ParsedRecordingName? = null): CallInfo {
    return CallInfo(
        // 손님 번호: 엔진이 대화에서 잡은 번호 우선, 없으면 파일명에서 뽑은 번호.
        phoneNumber = callerPhone?.takeIf { it.isNotBlank() }
            ?: parsed?.phone
            ?: "",
        status = Constants.STATUS_WAITING,
        // 대리/택시 핵심 필드 (미용이면 from/to/fare null).
        departure_set = from?.takeIf { it.isNotBlank() },
        destination_set = to?.takeIf { it.isNotBlank() },
        fare_set = fare?.toLong(),
        memoText = composeMemo(),
        callType = "예약",
        createdFrom = "call_recording",
        fromCallManager = true,
    )
}

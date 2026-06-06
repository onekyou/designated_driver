package com.designated.callmanager.reservation.data

/**
 * parseReservation Cloud Function 출력의 클라이언트 거울(평탄형, union 금지 — sealed는 여기서 조립).
 * 출력 계약 = coupon_app/app/lib/types.ts 의 SalonBookingPayload (문서 합의, 코드 의존 0).
 *
 * 의존성 0(android.*·Firebase 없음) → JVM 테스트 대상. Firebase 콜러블 결과(Map)는 [fromMap] 으로.
 */
data class UniversalReservation(
    val isReservation: Boolean,
    val intent: String,        // booking.request | change | cancel | inquiry | not_reservation | other
    val moduleType: String,    // salon_booking | designated_driver | taxi | restaurant | other
    val service: String? = null,
    val datetimeText: String? = null,
    val datetimeIso: String? = null,
    val partySize: Int? = null,
    val callerPhone: String? = null,
    val from: String? = null,
    val to: String? = null,
    val fare: Int? = null,
    val notes: String? = null,
    val confidence: Double = 0.0,
    val rawTranscript: String? = null,
) {
    /**
     * 로마 events.payload 계약(SalonBookingPayload). null·빈 필드는 제외.
     * customerId 주조는 여기서 안 함 — phone만 표면화(로마 쪽 Event 발행이 해소).
     */
    fun toCouponEventPayload(): Map<String, Any?> = buildMap {
        service?.let { put("service", it) }
        datetimeText?.let { put("datetimeText", it) }
        datetimeIso?.takeIf { it.isNotBlank() }?.let { put("datetimeIso", it) }
        partySize?.let { put("partySize", it) }
        callerPhone?.let { put("callerPhone", it) }
        rawTranscript?.let { put("rawTranscript", it) }
        put("confidence", confidence)
    }

    companion object {
        /** Firebase 콜러블 결과 Map → 모델. JSON 숫자는 Double 로 들어오므로 Number 로 받음. */
        fun fromMap(m: Map<*, *>): UniversalReservation {
            // 모델이 "null"/"없음"/0 으로 채워도 부재로 정리(프롬프트+클라 이중 안전망).
            fun str(k: String) = (m[k] as? String)?.trim()?.takeUnless {
                it.isEmpty() || it.equals("null", true) || it == "없음" || it == "미상" || it.equals("unknown", true)
            }
            fun num(k: String) = m[k] as? Number
            fun posInt(k: String) = num(k)?.toInt()?.takeIf { it > 0 } // 0 = 미명시 취급
            return UniversalReservation(
                isReservation = (m["isReservation"] as? Boolean) ?: false,
                intent = str("intent") ?: "other",
                moduleType = str("moduleType") ?: "other",
                service = str("service"),
                datetimeText = str("datetimeText"),
                datetimeIso = str("datetimeIso"),
                partySize = posInt("partySize"),
                callerPhone = str("callerPhone"),
                from = str("from"),
                to = str("to"),
                fare = posInt("fare"),
                notes = str("notes"),
                confidence = num("confidence")?.toDouble() ?: 0.0,
                rawTranscript = str("rawTranscript"),
            )
        }
    }
}

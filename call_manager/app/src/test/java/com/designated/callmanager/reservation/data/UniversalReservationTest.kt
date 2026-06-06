package com.designated.callmanager.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalReservationTest {

    @Test
    fun `살롱 예약 매핑 + payload`() {
        val r = UniversalReservation.fromMap(
            mapOf(
                "isReservation" to true,
                "intent" to "booking.request",
                "moduleType" to "salon_booking",
                "service" to "커트",
                "datetimeText" to "내일 3시",
                "datetimeIso" to "2026-06-08T15:00:00",
                "partySize" to 1.0, // 콜러블 JSON 숫자는 Double 로 옴
                "callerPhone" to "010-1234-5678",
                "confidence" to 0.9
            )
        )
        assertTrue(r.isReservation)
        assertEquals("salon_booking", r.moduleType)
        assertEquals(1, r.partySize) // Double → Int

        val p = r.toCouponEventPayload()
        assertEquals("커트", p["service"])
        assertEquals("2026-06-08T15:00:00", p["datetimeIso"])
        assertEquals(0.9, p["confidence"])
        assertFalse(p.containsKey("from")) // null 필드 제외
    }

    @Test
    fun `비예약 콜은 isReservation false`() {
        val r = UniversalReservation.fromMap(
            mapOf(
                "isReservation" to false,
                "intent" to "not_reservation",
                "moduleType" to "other",
                "confidence" to 0.2
            )
        )
        assertFalse(r.isReservation)
        assertEquals("not_reservation", r.intent)
    }

    @Test
    fun `빈 datetimeIso는 payload에서 제외`() {
        val r = UniversalReservation.fromMap(
            mapOf(
                "isReservation" to true,
                "intent" to "booking.request",
                "moduleType" to "salon_booking",
                "datetimeIso" to "",
                "confidence" to 0.5
            )
        )
        assertFalse(r.toCouponEventPayload().containsKey("datetimeIso"))
    }

    @Test
    fun `문자열 null과 0은 부재로 정리`() {
        val r = UniversalReservation.fromMap(
            mapOf(
                "isReservation" to true,
                "intent" to "booking.request",
                "moduleType" to "salon_booking",
                "service" to "null",   // 모델이 'null' 글자로 채운 경우
                "callerPhone" to "없음",
                "partySize" to 0.0,
                "fare" to 0.0,
                "confidence" to 0.7
            )
        )
        assertNull(r.service)
        assertNull(r.callerPhone)
        assertNull(r.partySize)
        assertNull(r.fare)
    }
}

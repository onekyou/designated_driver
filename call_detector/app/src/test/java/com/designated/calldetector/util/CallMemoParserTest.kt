package com.designated.calldetector.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CallMemoParser.parse 단위 테스트.
 *
 * Phase A (회귀 보호) + Phase B (Kiwi 데이터 기반 마커 확장).
 *
 * 데이터 출처: github.com/bab2min/Kiwi morphemes.txt (LGPL v3)
 * 추출 스크립트: functions/scripts/extract-kiwi-markers.js
 * 임베드: app/src/main/assets/marker_variants.txt
 *
 * Kiwi 사전 미등재 변형(까정/꺼정/거쳐 등) 은 본 테스트에 포함하지 않음.
 * Phase C (NIKL 모두의 말뭉치) 또는 Phase D (AI Hub) 에서 별도 흡수 예정.
 *
 * call_manager 의 동일 테스트와 sync 유지 (drift 방지).
 */
class CallMemoParserTest {

    // ============================================================
    // Phase A 회귀 보호 — 기존 14 케이스 그대로 유지
    // ============================================================

    @Test
    fun `빈 문자열은 모두 null`() {
        val result = CallMemoParser.parse("")
        assertNull(result.departure)
        assertNull(result.waypoints)
        assertNull(result.destination)
        assertNull(result.fare)
        assertEquals("", result.rawText)
        assertFalse(result.hasStructuredFields)
    }

    @Test
    fun `표준 발화 — 출발에서 도착까지 요금원`() {
        val result = CallMemoParser.parse("시장에서 용문까지 삼만원")
        assertEquals("시장", result.departure)
        assertNull(result.waypoints)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
        assertTrue(result.hasStructuredFields)
    }

    @Test
    fun `경유지 포함 발화`() {
        val result = CallMemoParser.parse("시장에서 양평 경유 용문까지 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("양평", result.waypoints)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `경유 붙여 쓰기 — 양평경유`() {
        val result = CallMemoParser.parse("시장에서 양평경유 용문까지 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("양평", result.waypoints)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `까지 생략된 발화 — 마지막 비숫자 토큰을 도착지로`() {
        val result = CallMemoParser.parse("시장에서 용문 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `출발지 생략 — customerAddress 폴백`() {
        val result = CallMemoParser.parse("용문까지 삼만원", customerAddress = "원당리")
        assertEquals("원당리", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `출발지 생략 + customerAddress 없음`() {
        val result = CallMemoParser.parse("용문까지 삼만원")
        assertNull(result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `요금만 있는 발화 — customerAddress 가 출발지로`() {
        val result = CallMemoParser.parse("삼만원", customerAddress = "시장")
        assertEquals("시장", result.departure)
        assertNull(result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `customerAddress 빈 문자열은 무시`() {
        val result = CallMemoParser.parse("용문까지 삼만원", customerAddress = "")
        assertNull(result.departure)
        assertEquals("용문", result.destination)
    }

    @Test
    fun `아라비아 숫자 요금 + 원`() {
        val result = CallMemoParser.parse("시장에서 용문까지 30000원")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `toPreview - 출발 도착 요금`() {
        val parsed = CallMemoParser.parse("시장에서 용문까지 삼만원")
        assertEquals("시장 → 용문 / 30,000원", parsed.toPreview())
    }

    @Test
    fun `toPreview - 경유 포함`() {
        val parsed = CallMemoParser.parse("시장에서 양평 경유 용문까지 삼만원")
        assertEquals("시장 → 양평 → 용문 / 30,000원", parsed.toPreview())
    }

    @Test
    fun `이만오천원 fare — 만천 조합 (Phase A fix 후 정상)`() {
        val result = CallMemoParser.parse("시장에서 용문까지 이만오천원")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(25000L, result.fare)
    }

    @Test
    fun `삼만오천원 fare — 만천 조합`() {
        val result = CallMemoParser.parse("시장에서 용문까지 삼만오천원")
        assertEquals(35000L, result.fare)
    }

    // ============================================================
    // Phase B 신규 — Kiwi 데이터 기반
    // ============================================================

    // ----- Kiwi 검증 마커: "서" (JKB freq 131,218, 에서의 줄임) -----

    @Test
    fun `서 변형 — 용문서 시장까지`() {
        val result = CallMemoParser.parse("용문서 시장까지 삼만원")
        assertEquals("용문", result.departure)
        assertEquals("시장", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `서 변형 — 까지 생략`() {
        val result = CallMemoParser.parse("용문서 시장 삼만원")
        assertEquals("용문", result.departure)
        assertEquals("시장", result.destination)
        assertEquals(30000L, result.fare)
    }

    // ----- 짧은 마커 가드: "서울" 같이 끝이 "서" 가 아닌 경우 정상 처리 -----

    @Test
    fun `서울은 서 매칭 안 됨 — 끝이 울`() {
        val result = CallMemoParser.parse("시장에서 서울까지 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("서울", result.destination)
        assertEquals(30000L, result.fare)
    }

    // ----- PLACEHOLDER_BLACKLIST: "여기서/거기서" 매칭 무효 -----

    @Test
    fun `여기서 — PLACEHOLDER 가드, customerAddress 폴백`() {
        val result = CallMemoParser.parse("여기서 용문까지 삼만원", customerAddress = "원당리")
        assertEquals("원당리", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `여기서 — PLACEHOLDER 가드, customerAddress 없으면 dep null`() {
        val result = CallMemoParser.parse("여기서 용문까지 삼만원")
        assertNull(result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `거기서 — PLACEHOLDER 가드`() {
        val result = CallMemoParser.parse("거기서 용문까지 삼만원", customerAddress = "원당리")
        assertEquals("원당리", result.departure)
    }

    // ----- "원" 없는 fare 추정: 단위 포함 OR ≥ 1000 가드 -----

    @Test
    fun `원 없는 fare — 한글 단위 포함 (삼만)`() {
        val result = CallMemoParser.parse("시장에서 용문까지 삼만")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `원 없는 fare — 아라비아 ≥ 1000 (5000)`() {
        val result = CallMemoParser.parse("시장에서 용문까지 5000")
        assertEquals(5000L, result.fare)
    }

    @Test
    fun `원 없는 fare — 단위 없음 + 작음 (삼) → null 가드`() {
        // "삼" → 한글 숫자 단위 없음 + 변환값 3 < 1000 → fare 추정 차단
        val result = CallMemoParser.parse("시장에서 용문까지 삼")
        assertNull(result.fare)
    }

    @Test
    fun `원 없는 fare — 단위 없음 + 작음 (5) → null 가드`() {
        val result = CallMemoParser.parse("시장에서 용문까지 5")
        assertNull(result.fare)
    }

    // ----- 종결어미 trim (Kiwi EF 검증) -----

    @Test
    fun `종결어미 suffix trim — 어요`() {
        // "삼만원어요" 토큰: endsWith("어요") → "삼만원" 으로 trim → fare 30000
        val result = CallMemoParser.parse("시장에서 용문까지 삼만원어요")
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `종결어미 suffix trim — 거든요`() {
        val result = CallMemoParser.parse("시장에서 용문까지 삼만원거든요")
        assertEquals(30000L, result.fare)
    }

    // ============================================================
    // Phase B-ext 큐레이션 — 우리말샘 방언 + Kiwi 활용 결합 surface
    // ============================================================

    // ----- 도착지 방언 (urimalsam:dialect) -----

    @Test
    fun `도착지 방언 까정`() {
        val result = CallMemoParser.parse("시장에서 용문까정 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `도착지 방언 꺼정`() {
        val result = CallMemoParser.parse("시장에서 용문꺼정 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    // ----- 경유 동사 활용 (거치다 VV) -----

    @Test
    fun `경유 거쳐 단독 토큰`() {
        val result = CallMemoParser.parse("시장에서 양평 거쳐 용문까지 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("양평", result.waypoints)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `경유 거쳐 붙여 쓰기 — 양평거쳐`() {
        val result = CallMemoParser.parse("시장에서 양평거쳐 용문까지 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("양평", result.waypoints)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `경유 거치 단독 토큰`() {
        val result = CallMemoParser.parse("시장에서 양평 거치 용문까지 삼만원")
        assertEquals("시장", result.departure)
        assertEquals("양평", result.waypoints)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    // ----- ㅂ니다 결합 surface -----

    @Test
    fun `종결어미 입니다 — 삼만원입니다`() {
        // 사장님이 격식체로 메모 입력 시: "삼만원입니다" suffix trim
        val result = CallMemoParser.parse("시장에서 용문까지 삼만원입니다")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }

    @Test
    fun `종결어미 이에요 suffix — 가장 긴 매칭 우선`() {
        // "삼만원이에요" 의 "이에요" suffix trim → "삼만원" → fare=30000
        // (만약 "에요" 만 매칭되면 "삼만원이" 가 남아 잘못 처리됨)
        val result = CallMemoParser.parse("시장에서 용문까지 삼만원이에요")
        assertEquals("시장", result.departure)
        assertEquals("용문", result.destination)
        assertEquals(30000L, result.fare)
    }
}

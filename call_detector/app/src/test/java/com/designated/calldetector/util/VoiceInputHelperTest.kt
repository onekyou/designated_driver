package com.designated.calldetector.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * VoiceInputHelper.convertKoreanNumberToDigit 단위 테스트.
 *
 * Phase A 회귀 보호 — 현재 동작 잠금 + 알려진 버그 케이스 명시.
 * 알려진 버그 케이스는 Phase A 의 fix 후 pass 로 전환됨.
 *
 * companion object 함수를 직접 호출 — Context 의존 없음.
 *
 * call_manager 의 동일 테스트와 sync 유지 (drift 방지).
 */
class VoiceInputHelperTest {

    // ===== 정상 동작 케이스 (회귀 보호) =====

    @Test
    fun `빈 문자열은 빈 문자열 반환`() {
        assertEquals("", VoiceInputHelper.convertKoreanNumberToDigit(""))
    }

    @Test
    fun `이미 아라비아 숫자는 그대로 반환`() {
        assertEquals("10000", VoiceInputHelper.convertKoreanNumberToDigit("10000"))
    }

    @Test
    fun `아라비아 숫자에 원 붙은 경우`() {
        assertEquals("10000", VoiceInputHelper.convertKoreanNumberToDigit("10000원"))
    }

    @Test
    fun `한 자리 만 단위 — 삼만`() {
        assertEquals("30000", VoiceInputHelper.convertKoreanNumberToDigit("삼만"))
    }

    @Test
    fun `한 자리 만 단위 + 원 — 삼만원`() {
        assertEquals("30000", VoiceInputHelper.convertKoreanNumberToDigit("삼만원"))
    }

    @Test
    fun `한 자리 천 단위 — 오천`() {
        assertEquals("5000", VoiceInputHelper.convertKoreanNumberToDigit("오천"))
    }

    @Test
    fun `한 자리 천 단위 + 원 — 오천원`() {
        assertEquals("5000", VoiceInputHelper.convertKoreanNumberToDigit("오천원"))
    }

    @Test
    fun `이만 — 만 단위만 있는 경우`() {
        assertEquals("20000", VoiceInputHelper.convertKoreanNumberToDigit("이만"))
    }

    @Test
    fun `이만원 — 만 단위 + 원`() {
        assertEquals("20000", VoiceInputHelper.convertKoreanNumberToDigit("이만원"))
    }

    // ===== 알려진 버그 케이스 (Phase A fix 대상) =====
    // 2026-04-26 시점: 모두 fail 예상.
    // "X만Y천" 조합에서 천 단위가 무시되어 (10000X + Y) 반환.
    // fix 후: (10000X + 1000Y) 반환.

    @Test
    fun `이만오천원 — 만 + 천 조합`() {
        assertEquals("25000", VoiceInputHelper.convertKoreanNumberToDigit("이만오천원"))
    }

    @Test
    fun `이만오천 — 원 없이`() {
        assertEquals("25000", VoiceInputHelper.convertKoreanNumberToDigit("이만오천"))
    }

    @Test
    fun `삼만오천원 — 만 + 천 조합`() {
        assertEquals("35000", VoiceInputHelper.convertKoreanNumberToDigit("삼만오천원"))
    }

    @Test
    fun `사만이천원 — 만 + 천 조합`() {
        assertEquals("42000", VoiceInputHelper.convertKoreanNumberToDigit("사만이천원"))
    }

    @Test
    fun `오만삼천원 — 만 + 천 조합`() {
        assertEquals("53000", VoiceInputHelper.convertKoreanNumberToDigit("오만삼천원"))
    }
}

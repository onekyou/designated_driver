package com.designated.callmanager.reservation.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingFilenameParserTest {

    @Test
    fun `표준 6자리 날짜 + 번호`() {
        val r = RecordingFilenameParser.parse("통화 녹음 010-1234-5678_260604_153012.m4a")!!
        assertEquals("010-1234-5678", r.label)
        assertEquals("010-1234-5678", r.phone)
        assertEquals("2026-06-04T15:30:12", r.localDateTime)
    }

    @Test
    fun `8자리 날짜(YYYYMMDD) 데모 파일도 파싱`() {
        // 실측 데모 파일: 날짜가 8자리였음 → \d{6}만이면 깨짐
        val r = RecordingFilenameParser.parse("CALL_6보람푸드 센트롤시티_20231101_214613.m4a")!!
        assertEquals("6보람푸드 센트롤시티", r.label)
        assertNull(r.phone) // 번호 아님
        assertEquals("2023-11-01T21:46:13", r.localDateTime)
    }

    @Test
    fun `저장 연락처(이름)는 phone null`() {
        val r = RecordingFilenameParser.parse("통화 녹음 김영배 신복리 큰길약국_240115_091500.m4a")!!
        assertEquals("김영배 신복리 큰길약국", r.label)
        assertNull(r.phone)
        assertEquals("2024-01-15T09:15:00", r.localDateTime)
    }

    @Test
    fun `접두어 없이 raw 번호만`() {
        val r = RecordingFilenameParser.parse("01098765432_250320_080000.m4a")!!
        assertEquals("010-9876-5432", r.phone)
        assertEquals("2025-03-20T08:00:00", r.localDateTime)
    }

    @Test
    fun `구분자 없는 번호도 포맷`() {
        val r = RecordingFilenameParser.parse("통화 녹음 01012345678_260101_000000.m4a")!!
        assertEquals("010-1234-5678", r.phone)
    }

    @Test
    fun `통화녹음이 아닌 파일은 null`() {
        assertNull(RecordingFilenameParser.parse("녹음 2024-01-01.m4a"))
        assertNull(RecordingFilenameParser.parse("song.mp3"))
        assertNull(RecordingFilenameParser.parse("그냥파일.m4a"))
    }

    @Test
    fun `대소문자 확장자 무관`() {
        val r = RecordingFilenameParser.parse("통화 녹음 010-1111-2222_260202_120000.M4A")
        assertEquals("010-1111-2222", r?.phone)
    }
}

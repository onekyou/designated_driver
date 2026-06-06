package com.designated.callmanager.reservation.data

/**
 * 삼성 통화 자동녹음 파일명 → (라벨/번호, 시각) 추출. 권한·STT 0, 순수 로직.
 *
 * 관측된 포맷(실측 2026-06-03):
 *  - `통화 녹음 {번호 or 연락처명}_{YYMMDD}_{HHMMSS}.m4a`
 *  - 데모 파일은 날짜가 8자리(YYYYMMDD)였음 → 6/8 둘 다 수용.
 *  - 미지 발신자 = raw 번호 / 저장 연락처 = 이름(+주소) → phone=null.
 *
 * 의존성 0(android.* 없음) → JVM 단위 테스트 대상. 전화 정규식은
 * VoiceInputHelper.extractPhoneNumber/formatPhoneNumber 에서 복사(인스턴스·Context 의존 회피).
 */
object RecordingFilenameParser {

    // (.*) greedy → 끝의 _날짜_시각.m4a 에 앵커(이름에 숫자/언더스코어 있어도 안전).
    private val FILENAME = Regex(
        """^(.*)_(\d{6}|\d{8})_(\d{6})\.m4a$""",
        RegexOption.IGNORE_CASE
    )

    // VoiceInputHelper 복사본 (의존성-0 유지)
    private val PHONE = Regex("""(01[016789])[-.\s]?(\d{3,4})[-.\s]?(\d{4})""")

    private val PREFIXES = listOf("통화 녹음 ", "통화녹음 ", "CALL_", "call_")

    fun parse(filename: String): ParsedRecordingName? {
        val m = FILENAME.find(filename.trim()) ?: return null
        val (rawLabel, date, time) = m.destructured

        var label = rawLabel.trim()
        for (p in PREFIXES) if (label.startsWith(p)) { label = label.removePrefix(p).trim(); break }

        return ParsedRecordingName(
            label = label,
            phone = extractPhone(label),
            localDateTime = toLocalDateTime(date, time)
        )
    }

    private fun extractPhone(text: String): String? {
        val match = PHONE.find(text) ?: return null
        val digits = match.value.filter(Char::isDigit)
        return formatPhone(digits)
    }

    private fun formatPhone(digits: String): String = when {
        digits.length == 11 && digits.startsWith("010") ->
            "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
        digits.length == 10 && digits.startsWith("01") ->
            "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
        else -> digits
    }

    /** YYMMDD|YYYYMMDD + HHMMSS → "YYYY-MM-DDTHH:MM:SS" (KST 가정, Gemini 상대시각 앵커). */
    private fun toLocalDateTime(date: String, time: String): String {
        val year: String
        val month: String
        val day: String
        if (date.length == 8) {
            year = date.substring(0, 4); month = date.substring(4, 6); day = date.substring(6, 8)
        } else { // 6
            year = "20" + date.substring(0, 2); month = date.substring(2, 4); day = date.substring(4, 6)
        }
        val hh = time.substring(0, 2); val mm = time.substring(2, 4); val ss = time.substring(4, 6)
        return "$year-$month-${day}T$hh:$mm:$ss"
    }
}

/**
 * 파일명에서 뽑은 순수 결과. android.* 의존 없음.
 * @param phone null = 저장 연락처/번호숨김(파일명만으론 raw 번호 없음 — 알려진 한계).
 */
data class ParsedRecordingName(
    val label: String,
    val phone: String?,
    val localDateTime: String
)

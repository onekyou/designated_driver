package com.designated.callmanager.util

import android.util.Log

/**
 * STT 결과에서 출발지/경유지/도착지/요금을 파싱.
 *
 * 토큰 기반 파서. 마커 Set 매칭 + 종결어미 trim + 도착지 폴백 + customerAddress 폴백.
 *
 * **마커 데이터 출처** (Phase B, 2026-04-26):
 * - github.com/bab2min/Kiwi (LGPL v3) 의 morphemes.txt 형태소 사전
 * - 추출 스크립트: `functions/scripts/extract-kiwi-markers.js`
 * - 추출 결과: `app/src/main/assets/marker_variants.txt` (재현/감사용)
 *
 * 추측 변형 0 — 모두 Kiwi 빈도 검증 데이터.
 * 방언/구어 변형(까정/꺼정/거쳐 등)은 Phase C (NIKL 모두의 말뭉치) / Phase D (AI Hub) 에서 별도 흡수.
 *
 * 예시:
 *  - "시장에서 용문까지 이만오천원" → dep="시장", dest="용문", fare=25000
 *  - "시장서 용문까지 삼만원" → dep="시장", dest="용문", fare=30000  ("서" 변형, Kiwi 검증)
 *  - "시장에서 양평 경유 용문까지 삼만원" → dep="시장", way="양평", dest="용문", fare=30000
 *  - "시장에서 용문 갑니다 삼만원" → dep="시장", dest="용문", fare=30000  (종결어미 trim)
 *  - "시장에서 용문까지 삼만원입니다" → fare=30000  (종결어미 suffix trim)
 *  - "시장에서 용문까지 삼만" → fare=30000  ("원" 없이, 단위 포함이라 추정)
 *  - "용문 삼" → fare=null  (단위 없음 + < 1000, 가드로 차단)
 *  - "여기서 용문까지 삼만원", customerAddress="원당리" → dep="원당리"  (지시대명사 블랙리스트 → 폴백)
 */
object CallMemoParser {
    private const val TAG = "CallMemoParser"

    // ============================================================
    // 마커 데이터 — Kiwi morphemes.txt + 큐레이션 (출처 명시)
    //
    // 동기화 파일: app/src/main/assets/marker_variants.txt
    // 추출 스크립트: functions/scripts/extract-kiwi-markers.js
    //
    // 출처:
    //   - kiwi:JKB/JX/EF — Kiwi morphemes.txt (LGPL v3) 직접 등재 + 빈도 ≥ 30K
    //   - kiwi:어간+어미 — Kiwi 등재 어간/어미의 한국어 표준 활용 결합 surface
    //   - urimalsam:dialect — 국립국어원 우리말샘 방언 표제어 (공공 데이터)
    //   - standard:어휘 — 한국어 표준 어휘 (Kiwi 외 일반 어휘)
    // ============================================================

    /** JKB 태그 — 장소/출처 의미 */
    private val DEPARTURE_MARKERS: Set<String> = setOf(
        "에서",  // kiwi:JKB freq 2,407,135
        "서"     // kiwi:JKB freq 131,218 (에서의 줄임형)
    )

    /** JX 태그 + 방언 — 한계/도달점 의미 */
    private val DESTINATION_MARKERS: Set<String> = setOf(
        "까지",  // kiwi:JX freq 484,890
        "까정",  // urimalsam:dialect (충청/전라/경상)
        "꺼정"   // urimalsam:dialect (충청/전라)
    )

    /** WAYPOINT — 경유 명사 + 거치다(VV) 활용 */
    private val WAYPOINT_MARKERS: Set<String> = setOf(
        "경유",  // 명사 NNG
        "거쳐",  // kiwi:VV-거치다+어 (활용형)
        "거치"   // kiwi:VV-거치다-stem (어간)
    )

    /**
     * 종결어미 — 사장님 발화 적합한 평서/격식체만.
     *
     * 시나리오: 콜매니저/콜디텍터 사용자(관리자, 박상준 사장님)가 손님 통화 후 정보 종합해 STT 메모 입력.
     * 손님 발화 전제 종결어미(가요/갑니다/주세요/갑시다/부탁드립니다)는 등장하지 않으므로 제외.
     */
    private val SENTENCE_ENDINGS: Set<String> = setOf(
        // Kiwi EF 직접 등재 — 평서/격식체 (빈도 ≥ 30,000)
        "습니다",  // kiwi:EF freq 624,298 (격식체 평서)
        "어요",    // kiwi:EF freq 432,512 (비격식 평서)
        "에요",    // kiwi:EF freq 57,281
        "네요",    // kiwi:EF freq 57,121 (감탄/확인)
        "예요",    // kiwi:EF freq 43,950 (이에요 약형)
        "거든요",  // kiwi:EF freq 32,357 (부연 설명)

        // 큐레이션 — Kiwi 받침 결합형 + 명사 결합 surface
        "입니다",  // 이+ㅂ니다 (kiwi:ㅂ니다 EF freq 677,109)
        "이에요"   // 이+에요. "에요" 단독 trim 시 "이" 받침이 토큰에 남아 fare 깨짐 → 가장 긴 매칭 우선
    )

    /**
     * 지시대명사 — "X서" 형태 출발지 매칭에서 제외 (예: "여기서" → 매칭 무효 → customerAddress 폴백).
     * Kiwi 사전 외부, 한국어 표준 지시대명사로 알려진 셋.
     */
    private val PLACEHOLDER_BLACKLIST: Set<String> = setOf(
        "여기", "거기", "저기", "어디", "이쪽", "저쪽"
    )

    // ============================================================
    // 한국어 숫자 (요금 판별용)
    // ============================================================

    private val KOREAN_NUMBER_CHARS = setOf(
        '일', '이', '삼', '사', '오', '육', '칠', '팔', '구', '십',
        '백', '천', '만', '억', '영'
    )

    /** 한국어 숫자 단위 — "원" 없는 fare 추정 시 가드용 */
    private val KOREAN_NUMBER_UNITS = setOf('만', '천', '백', '십', '억')

    /** 원 없는 fare 추정 시 최소 합리 액수 (단위 없는 한글/아라비아 숫자 입력 가드) */
    private const val FARE_MIN_WITHOUT_UNIT: Long = 1000L

    // ============================================================
    // 공개 API
    // ============================================================

    /**
     * STT 결과 텍스트를 파싱해서 [ParsedMemo] 반환.
     *
     * @param text STT 원문
     * @param customerAddress 통화 발신자 주소 (출발지 폴백용). null/빈값이면 미사용.
     */
    fun parse(
        text: String,
        customerAddress: String? = null
    ): ParsedMemo {
        val raw = text.trim()
        if (raw.isEmpty()) {
            return ParsedMemo(null, null, null, null, raw)
        }

        val tokens = raw.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (tokens.isEmpty()) {
            return ParsedMemo(null, null, null, null, raw)
        }

        // 0) 종결어미 trim — 마지막 토큰 기준
        trimSentenceEndings(tokens)
        if (tokens.isEmpty()) {
            return ParsedMemo(
                departure = customerAddress?.trim()?.takeIf { it.isNotBlank() },
                waypoints = null,
                destination = null,
                fare = null,
                rawText = raw
            )
        }

        // 1) 요금 추출 — 1순위 "원" 끝, 2순위 looksLikeNumber + 단위/최소액 가드
        var fare: Long? = null
        val lastToken = tokens.last()
        val fareWithWon = extractFareWithWon(lastToken)
        if (fareWithWon != null) {
            fare = fareWithWon
            tokens.removeAt(tokens.size - 1)
        } else {
            val fareWithoutWon = extractFareWithoutWon(lastToken)
            if (fareWithoutWon != null) {
                fare = fareWithoutWon
                tokens.removeAt(tokens.size - 1)
            }
        }

        // 요금 제거 후 남은 토큰 없으면 customerAddress 폴백
        if (tokens.isEmpty()) {
            return ParsedMemo(
                departure = customerAddress?.trim()?.takeIf { it.isNotBlank() },
                waypoints = null,
                destination = null,
                fare = fare,
                rawText = raw
            ).also { Log.d(TAG, "요금만 파싱: $it") }
        }

        // 2) 출발지 — DEPARTURE_MARKERS 첫 매칭 (가장 긴 마커 우선, PLACEHOLDER_BLACKLIST 가드)
        var departure: String? = null
        var fromIdx = -1
        for ((idx, token) in tokens.withIndex()) {
            val body = findMarkerMatch(token, DEPARTURE_MARKERS)
            if (body != null) {
                fromIdx = idx
                departure = body
                break
            }
        }

        // 3) 도착지 — DESTINATION_MARKERS 마지막 매칭, 없으면 폴백 (모든 마커·종결어미 토큰 제외)
        var destination: String? = null
        var destTokenIdx = -1
        for (idx in tokens.indices.reversed()) {
            val body = findMarkerMatch(tokens[idx], DESTINATION_MARKERS)
            if (body != null) {
                destination = body
                destTokenIdx = idx
                break
            }
        }
        if (destination == null) {
            // 폴백: 마지막 비숫자, 비마커, 비종결어미 토큰
            val candidateIdx = tokens.indexOfLast { token ->
                !looksLikeNumber(token) &&
                    DEPARTURE_MARKERS.none { token.endsWith(it) } &&
                    DESTINATION_MARKERS.none { token.endsWith(it) } &&
                    WAYPOINT_MARKERS.none { token == it || token.endsWith(it) } &&
                    token !in SENTENCE_ENDINGS
            }
            if (candidateIdx >= 0 && candidateIdx != fromIdx) {
                destination = tokens[candidateIdx].takeIf { it.isNotBlank() }
                destTokenIdx = candidateIdx
            }
        }

        // 4) 경유지 — WAYPOINT_MARKERS 매칭. 단독 토큰이면 인접 토큰을 후보로.
        var waypoints: String? = null
        for ((idx, token) in tokens.withIndex()) {
            if (token in WAYPOINT_MARKERS) {
                // 단독 마커 토큰: 앞/뒤 인접 토큰을 경유지 후보로
                val beforeVia = tokens.getOrNull(idx - 1)
                val afterVia = tokens.getOrNull(idx + 1)
                val beforeOk = beforeVia != null &&
                    DEPARTURE_MARKERS.none { beforeVia.endsWith(it) } &&
                    DESTINATION_MARKERS.none { beforeVia.endsWith(it) } &&
                    (idx - 1) != fromIdx &&
                    (idx - 1) != destTokenIdx
                val afterOk = afterVia != null &&
                    DESTINATION_MARKERS.none { afterVia.endsWith(it) } &&
                    (idx + 1) != destTokenIdx
                waypoints = when {
                    beforeOk -> beforeVia
                    afterOk -> afterVia
                    else -> null
                }
                if (waypoints != null) break
            } else {
                // 붙여 쓴 형태 (예: "양평경유")
                val body = findMarkerMatch(token, WAYPOINT_MARKERS)
                if (body != null) {
                    waypoints = body
                    break
                }
            }
        }

        // 5) 출발지 폴백 — customerAddress
        if (departure.isNullOrBlank() && !customerAddress.isNullOrBlank()) {
            departure = customerAddress.trim()
            Log.d(TAG, "출발지 폴백: customerAddress=$departure")
        }

        return ParsedMemo(
            departure = departure?.takeIf { it.isNotBlank() },
            waypoints = waypoints?.takeIf { it.isNotBlank() },
            destination = destination?.takeIf { it.isNotBlank() },
            fare = fare,
            rawText = raw
        ).also {
            Log.d(TAG, "파싱 결과: $it")
        }
    }

    // ============================================================
    // 헬퍼 함수
    // ============================================================

    /**
     * 토큰 끝의 마커 매칭. 가장 긴 마커 우선.
     * - 길이 가드: token.length > marker.length + 1 (본체 ≥ 2글자 보장)
     * - 본체가 PLACEHOLDER_BLACKLIST 에 있으면 매칭 무효
     *
     * @return 매칭된 본체 (마커 제거 후), 매칭 없으면 null
     */
    private fun findMarkerMatch(token: String, markers: Set<String>): String? {
        for (m in markers.sortedByDescending { it.length }) {
            if (token.endsWith(m) && token.length > m.length + 1) {
                val body = token.removeSuffix(m)
                if (body.isNotBlank() && body !in PLACEHOLDER_BLACKLIST) {
                    return body
                }
            }
        }
        return null
    }

    /**
     * 마지막 토큰의 종결어미 trim.
     * - 단독 토큰이 종결어미와 정확히 일치 → 토큰 제거
     * - 토큰 끝에 종결어미 suffix → suffix 제거 (본체 ≥ 1 가드)
     */
    private fun trimSentenceEndings(tokens: MutableList<String>) {
        if (tokens.isEmpty()) return
        val last = tokens.last()
        if (last in SENTENCE_ENDINGS) {
            tokens.removeAt(tokens.size - 1)
            return
        }
        for (ending in SENTENCE_ENDINGS.sortedByDescending { it.length }) {
            if (last.endsWith(ending) && last.length > ending.length) {
                tokens[tokens.size - 1] = last.removeSuffix(ending)
                return
            }
        }
    }

    /** "원" 으로 끝나는 토큰에서 fare 추출. 실패 시 null. */
    private fun extractFareWithWon(token: String): Long? {
        if (!token.endsWith("원") || token.length <= 1) return null
        val fareStr = token.removeSuffix("원").replace(",", "")
        val digits = VoiceInputHelper.convertKoreanNumberToDigit(fareStr)
        val parsed = digits.filter { it.isDigit() }.toLongOrNull() ?: return null
        return if (parsed > 0) parsed else null
    }

    /**
     * "원" 없는 토큰에서 fare 추정. 가드:
     *  - looksLikeNumber 통과 (한글숫자 또는 아라비아 숫자만)
     *  - 한글 단위 ("만/천/백/십/억") 포함 OR 변환 결과 ≥ 1000
     */
    private fun extractFareWithoutWon(token: String): Long? {
        if (!looksLikeNumber(token)) return null
        val hasUnit = token.any { it in KOREAN_NUMBER_UNITS }
        val cleanedToken = token.replace(",", "")
        val digits = VoiceInputHelper.convertKoreanNumberToDigit(cleanedToken)
        val parsed = digits.filter { it.isDigit() }.toLongOrNull() ?: return null
        if (parsed <= 0) return null
        return if (hasUnit || parsed >= FARE_MIN_WITHOUT_UNIT) parsed else null
    }

    /** 토큰이 숫자(한글/아라비아)로만 구성됐는지 검사 */
    private fun looksLikeNumber(token: String): Boolean {
        val cleaned = token.removeSuffix("원").replace(",", "")
        if (cleaned.isEmpty()) return false
        return cleaned.all { it.isDigit() || KOREAN_NUMBER_CHARS.contains(it) }
    }
}

/**
 * 파싱된 메모 결과.
 *
 * - [departure]: 출발지 (파싱 실패 + 폴백 실패 시 null)
 * - [waypoints]: 경유지 (없으면 null)
 * - [destination]: 도착지 (파싱 실패 시 null)
 * - [fare]: 요금 (파싱 실패 시 null)
 * - [rawText]: STT 원문 — memoText 에 저장
 */
data class ParsedMemo(
    val departure: String?,
    val waypoints: String?,
    val destination: String?,
    val fare: Long?,
    val rawText: String
) {
    /** 구조화 가능한 필드가 하나라도 파싱됐으면 true */
    val hasStructuredFields: Boolean
        get() = !departure.isNullOrBlank() ||
                !waypoints.isNullOrBlank() ||
                !destination.isNullOrBlank() ||
                (fare != null && fare > 0)

    /** 프리뷰 문자열 ("시장 → 양평 → 용문 / 25,000원") */
    fun toPreview(): String {
        val route = buildList {
            departure?.takeIf { it.isNotBlank() }?.let { add(it) }
            waypoints?.takeIf { it.isNotBlank() }?.let { add(it) }
            destination?.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" → ")

        val fareStr = fare?.takeIf { it > 0 }?.let { " / ${"%,d".format(it)}원" } ?: ""
        return "$route$fareStr".ifBlank { rawText }
    }
}

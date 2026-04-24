package com.designated.calldetector.util

import android.util.Log

/**
 * STT 결과 "에서/경유/까지/원" 마커를 기반으로 출발지/경유지/도착지/요금을 파싱.
 *
 * 토큰 기반 파서 — 공백으로 split 한 후 마커 접미사로 각 필드를 추출.
 *
 * 예시:
 *  - "시장에서 용문까지 이만오천원"
 *    → tokens=["시장에서","용문까지","이만오천원"]
 *    → dep="시장", dest="용문", fare=25000
 *  - "시장에서 양평 경유 용문까지 삼만원"
 *    → dep="시장", waypoints="양평", dest="용문", fare=30000
 *  - "시장에서 용문 이만오천원" (까지 생략)
 *    → dep="시장", dest="용문"(마지막 비숫자 토큰), fare=25000
 *  - "용문까지 2만5천원" (출발지 생략)
 *    → dep=<customerAddress 폴백>, dest="용문", fare=25000
 *  - "용문 이만오천" (마커 전부 없음)
 *    → 부분 파싱 시도, 실패 시 원문만 memoText 저장
 *
 * 설계 메모:
 *  - 출발지는 기사 이동 트리거 정보 → 3중 안전장치:
 *    1) "에서" 마커 파싱
 *    2) customerAddress 폴백
 *    3) 폴백도 없으면 null
 */
object CallMemoParser {
    private const val TAG = "CallMemoParser"

    // 한국어 숫자 문자 집합 (요금 판별용)
    private val KOREAN_NUMBER_CHARS = setOf(
        '일', '이', '삼', '사', '오', '육', '칠', '팔', '구', '십',
        '백', '천', '만', '억', '영'
    )

    /**
     * STT 결과 텍스트를 파싱해서 [ParsedMemo] 반환.
     *
     * @param text STT 원문
     * @param customerAddress 통화 발신자 주소 (출발지 폴백용). null/빈값이면 미사용.
     * @param voiceHelper 한국어 숫자 변환용 (convertKoreanNumberToDigit 재사용)
     */
    fun parse(
        text: String,
        customerAddress: String? = null,
        voiceHelper: VoiceInputHelper
    ): ParsedMemo {
        val raw = text.trim()
        if (raw.isEmpty()) {
            return ParsedMemo(null, null, null, null, raw)
        }

        val tokens = raw.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (tokens.isEmpty()) {
            return ParsedMemo(null, null, null, null, raw)
        }

        // 1) 요금 추출 — 마지막 토큰이 "원"으로 끝나면 요금으로 간주
        var fare: Long? = null
        val lastToken = tokens.last()
        if (lastToken.endsWith("원") && lastToken.length > 1) {
            val fareStr = lastToken.removeSuffix("원").replace(",", "")
            val digits = voiceHelper.convertKoreanNumberToDigit(fareStr)
            val parsed = digits.filter { it.isDigit() }.toLongOrNull()
            if (parsed != null && parsed > 0) {
                fare = parsed
                tokens.removeAt(tokens.size - 1)
            }
        }

        // 요금 제거 후 남은 토큰 없으면 조기 반환
        if (tokens.isEmpty()) {
            return ParsedMemo(
                departure = customerAddress?.trim()?.takeIf { it.isNotBlank() },
                waypoints = null,
                destination = null,
                fare = fare,
                rawText = raw
            ).also { Log.d(TAG, "요금만 파싱: $it") }
        }

        // 2) 출발지 — "X에서" 토큰 (첫 번째 매치)
        var departure: String? = null
        val fromIdx = tokens.indexOfFirst { it.endsWith("에서") && it.length > 2 }
        if (fromIdx >= 0) {
            departure = tokens[fromIdx].removeSuffix("에서").takeIf { it.isNotBlank() }
        }

        // 3) 도착지 — "Y까지" 토큰 (마지막 매치)
        var destination: String? = null
        var destTokenIdx = -1
        val toIdx = tokens.indexOfLast { it.endsWith("까지") && it.length > 2 }
        if (toIdx >= 0) {
            destination = tokens[toIdx].removeSuffix("까지").takeIf { it.isNotBlank() }
            destTokenIdx = toIdx
        } else {
            // "까지" 없으면 마지막 비숫자 토큰을 도착지로
            val candidateIdx = tokens.indexOfLast { !looksLikeNumber(it) && !it.endsWith("에서") && it != "경유" }
            if (candidateIdx >= 0 && candidateIdx != fromIdx) {
                destination = tokens[candidateIdx].takeIf { it.isNotBlank() }
                destTokenIdx = candidateIdx
            }
        }

        // 4) 경유지 — "경유" 토큰 전/후 토큰
        var waypoints: String? = null
        val viaIdx = tokens.indexOfFirst { it == "경유" || (it.endsWith("경유") && it.length > 2) }
        if (viaIdx >= 0) {
            // "경유" 단독이면 앞뒤 토큰 모두 후보
            if (tokens[viaIdx] == "경유") {
                val beforeVia = tokens.getOrNull(viaIdx - 1)
                val afterVia = tokens.getOrNull(viaIdx + 1)

                // 앞 토큰이 "에서" 접미사 없고 도착지도 아니면 경유지
                if (beforeVia != null &&
                    !beforeVia.endsWith("에서") &&
                    !beforeVia.endsWith("까지") &&
                    (viaIdx - 1) != fromIdx &&
                    (viaIdx - 1) != destTokenIdx
                ) {
                    waypoints = beforeVia
                } else if (afterVia != null &&
                    !afterVia.endsWith("까지") &&
                    (viaIdx + 1) != destTokenIdx
                ) {
                    waypoints = afterVia
                }
            } else {
                // "양평경유" 같이 붙어있는 경우
                waypoints = tokens[viaIdx].removeSuffix("경유").takeIf { it.isNotBlank() }
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

    /** 토큰이 숫자(한글/아라비아)로만 구성됐는지 검사 — 도착지 후보에서 제외하기 위한 가드 */
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

package com.designated.pickupdriver.util

import android.util.Log
import com.designated.pickupdriver.service.WhisperTranscriber

/**
 * PTT 전사 지명 가제티어 후처리 — whisper 가 핀포인트로 깨는 지명을 정답 사전으로 스냅.
 *  call_manager 의 GazetteerCorrector 를 그대로 이식. 패키지만 변경.
 *
 *  예: 전사 "수연이 양평력으로 가" + 사전 {양평역} → "수연이 양평역으로 가"
 *
 * 설계 원칙:
 *  - **어절 접두 부분매칭**: 한국어는 교착어라 지명 뒤에 조사가 붙음("양평역으로"). 어절 통째가
 *    아니라 어절의 앞부분(지명 길이 L)만 정답과 비교하고, 뒤(조사)는 보존한다.
 *  - **보수적·모호하면 보존**: 정상 단어를 지명으로 오스냅하지 않게 거리 임계를 짧은 지명일수록
 *    좁히고, 최단거리 후보가 둘 이상이면(모호) 손대지 않는다.
 *  - 사전 비었거나 전사가 LOW_CONF 마커면 no-op(원문 그대로).
 *
 * 임계값은 [잠정] — 배포 후 logcat `[gazetteer]` 로 실측 보정. 자모분해는 미적용(후속).
 */
object GazetteerCorrector {
    private const val TAG = "GazetteerCorrector"
    private const val MAX_JAMO_DIST = 1   // 자모 편집거리 허용치 [잠정] — 1자모만(받침/초성 1차이).
    private const val AMBIG_RADIUS = 2    // 차순위 후보가 이 거리 이내면 "헷갈림" → 보존(오교정 회피).

    /** 전사 텍스트의 각 어절을 지명 사전으로 접두 스냅. places 비었거나 저신뢰 마커면 원문. */
    fun correct(text: String, places: Set<String>): String {
        if (places.isEmpty() || text == WhisperTranscriber.LOW_CONF_MARKER) return text
        return text.split(" ").joinToString(" ") { correctWord(it, places) }
    }

    private fun correctWord(word: String, places: Set<String>): String {
        var best: String? = null
        var bestD = Int.MAX_VALUE
        var secondD = Int.MAX_VALUE

        for (cand in places) {
            val l = cand.length
            if (l < 2 || word.length < l) continue           // 1글자 지명/짧은 어절 제외
            val head = word.take(l)
            if (head == cand) return word                     // 이미 올바른 지명으로 시작 → 변경 불요
            val d = levenshtein(decompose(head), decompose(cand))  // 자모 단위 거리
            if (d < bestD) { secondD = bestD; bestD = d; best = cand }
            else if (d < secondD) secondD = d
        }

        // 확실할 때만 교정: 최단이 1자모 이내 AND 차순위가 충분히 멀어(>AMBIG) 안 헷갈릴 때만.
        // ("양통역"은 양동역 d1·양평역 d2로 헷갈림 → 보존. "양평력"은 양평역 d1·양평읍 d3 → 교정.)
        if (best == null || bestD > MAX_JAMO_DIST || secondD <= AMBIG_RADIUS) return word
        val result = best + word.substring(best.length)       // 지명만 교체, 조사 보존
        Log.i(TAG, "[gazetteer] '$word'→'$result' d=$bestD second=$secondD")
        return result
    }

    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val JUNG = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private const val JONG = " ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ"

    /** 한글 음절 → 초·중·종성 자모 나열. "양평역"=ㅇㅑㅇㅍㅕㅇㅇㅕㄱ. 음절 단위론 "양평력"이
     *  양평읍·양평역과 동률(거리1)이라 모호해지지만, 자모로 풀면 양평역(받침 ㄹ↔ㅇ=1) <
     *  양평읍(거리3)이라 유일 최단으로 판정된다. */
    private fun decompose(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            val code = c.code
            if (code in 0xAC00..0xD7A3) {
                val i = code - 0xAC00
                sb.append(CHO[i / 588])
                sb.append(JUNG[(i % 588) / 28])
                val jong = i % 28
                if (jong != 0) sb.append(JONG[jong])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /** 자모 문자열 단위 Levenshtein 편집거리. */
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var curr = IntArray(n + 1)
        for (i in 1..m) {
            curr[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = curr; curr = tmp
        }
        return prev[n]
    }
}

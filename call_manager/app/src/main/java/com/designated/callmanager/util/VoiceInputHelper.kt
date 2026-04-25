package com.designated.callmanager.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Locale

class VoiceInputHelper(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var onResultCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "VoiceInputHelper"

        /**
         * 한국어 숫자를 아라비아 숫자로 변환
         * 예: "삼만원" -> "30000", "이만오천원" -> "25000"
         *
         * Context 의존 없는 순수 함수 — JVM 단위 테스트에서 직접 호출 가능.
         * 인스턴스 메서드는 이 함수에 위임.
         */
        fun convertKoreanNumberToDigit(text: String): String {
            var result = text
                .replace("영", "0")
                .replace("일", "1")
                .replace("이", "2")
                .replace("삼", "3")
                .replace("사", "4")
                .replace("오", "5")
                .replace("육", "6")
                .replace("칠", "7")
                .replace("팔", "8")
                .replace("구", "9")

            // 단위 처리
            val units = listOf(
                "십만" to "100000",
                "만" to "0000",
                "천" to "000",
                "백" to "00",
                "십" to "0"
            )

            // 복잡한 한국어 숫자 처리 — "만" 앞뒤로 분할 후 각각 단위 적용
            // 예: "2만5천" → manPart=2, restPart=5000 → 25000
            // 이전 버그: restPart 에서 "5천" 의 천 단위가 무시되어 5 만 추출 → 20005 반환
            if (result.contains("만")) {
                val parts = result.split("만")
                if (parts.size == 2) {
                    val manPartStr = parts[0]
                    val restPartStr = parts[1].replace("원", "")

                    // "만" 앞 — 비어있으면 1 (예: "만원" → 10000)
                    val manNumber = if (manPartStr.isEmpty()) 1 else convertSubManUnits(manPartStr)
                    // "만" 뒤 — 천/백/십 단위 적용
                    val restNumber = convertSubManUnits(restPartStr)

                    return (manNumber * 10000 + restNumber).toString()
                }
            }

            // 단순 처리
            for ((korean, digit) in units) {
                if (result.contains(korean)) {
                    val beforeUnit = result.substringBefore(korean)
                    val numberBefore = if (beforeUnit.isEmpty() || beforeUnit == result) {
                        "1"
                    } else {
                        beforeUnit.filter { it.isDigit() }.ifEmpty { "1" }
                    }

                    if (korean == "만" && !result.contains("십만")) {
                        val afterMan = result.substringAfter("만").replace("원", "")
                        val afterNumber = afterMan.filter { it.isDigit() }.toIntOrNull() ?: 0
                        val manNumber = numberBefore.toIntOrNull() ?: 1
                        return (manNumber * 10000 + afterNumber).toString()
                    } else {
                        result = result.replace(korean, digit)
                    }
                }
            }

            // "원" 제거 및 숫자만 추출
            result = result.replace("원", "").replace(" ", "")

            // 숫자만 남기기
            return result.filter { it.isDigit() }
        }

        /**
         * "만" 미만 단위(천/백/십) 처리.
         * 예: "5천" → 5000, "5천5백" → 5500, "5천5백5십5" → 5555, "5" → 5, "" → 0
         *
         * convertKoreanNumberToDigit 내부에서 "만" 앞뒤 분할 후 각 부분 처리에 사용.
         */
        private fun convertSubManUnits(text: String): Int {
            if (text.isEmpty()) return 0

            var sum = 0
            var current = text
            val subUnits = listOf("천" to 1000, "백" to 100, "십" to 10)

            for ((unit, multiplier) in subUnits) {
                if (current.contains(unit)) {
                    val before = current.substringBefore(unit)
                    val n = before.filter { it.isDigit() }.toIntOrNull() ?: 1
                    sum += n * multiplier
                    current = current.substringAfter(unit)
                }
            }

            // 남은 자리 (단위 없는 일의 자리)
            val ones = current.filter { it.isDigit() }.toIntOrNull() ?: 0
            sum += ones

            return sum
        }
    }

    fun createSpeechRecognizer(): SpeechRecognizer {
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        }
        return speechRecognizer!!
    }

    fun startListening(onResult: (String) -> Unit) {
        // 권한 체크
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "음성 인식 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }

        // SpeechRecognizer 사용 가능 여부 체크
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, "음성 인식을 사용할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        onResultCallback = onResult

        val speechRecognizer = createSpeechRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해 주세요")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "음성 입력 준비 완료")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "음성 입력 시작")
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "음성 입력 종료")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "오디오 에러"
                    SpeechRecognizer.ERROR_CLIENT -> "클라이언트 에러"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "권한 부족"
                    SpeechRecognizer.ERROR_NETWORK -> "네트워크 에러"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "네트워크 타임아웃"
                    SpeechRecognizer.ERROR_NO_MATCH -> "인식 결과 없음"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "인식기 사용 중"
                    SpeechRecognizer.ERROR_SERVER -> "서버 에러"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "음성 입력 시간 초과"
                    else -> "알 수 없는 에러"
                }
                Log.e(TAG, "음성 인식 에러: $errorMessage")
                Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val result = matches[0]
                    Log.d(TAG, "음성 인식 결과: $result")
                    onResultCallback?.invoke(result)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    Log.d(TAG, "부분 결과: ${matches[0]}")
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    /**
     * 인스턴스 메서드 호환용 — companion 함수에 위임.
     * 기존 호출자(`voiceHelper.convertKoreanNumberToDigit(...)`) 영향 0.
     */
    fun convertKoreanNumberToDigit(text: String): String =
        VoiceInputHelper.convertKoreanNumberToDigit(text)

    /**
     * 전화번호 형식 정리
     */
    fun formatPhoneNumber(text: String): String {
        // 숫자만 추출
        val digits = text.filter { it.isDigit() }

        // 010-xxxx-xxxx 형식으로 포맷
        return when {
            digits.length == 11 && digits.startsWith("010") -> {
                "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
            }
            digits.length == 10 && digits.startsWith("01") -> {
                "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
            }
            else -> digits
        }
    }

    /**
     * 텍스트에서 전화번호 추출
     */
    fun extractPhoneNumber(text: String): String? {
        // 전화번호 패턴 매칭 (010-1234-5678, 01012345678, 010 1234 5678 등)
        val phoneRegex = Regex("""(01[016789])[-.\s]?(\d{3,4})[-.\s]?(\d{4})""")
        val match = phoneRegex.find(text)

        return match?.let {
            val digits = it.value.filter { c -> c.isDigit() }
            formatPhoneNumber(digits)
        }
    }
}
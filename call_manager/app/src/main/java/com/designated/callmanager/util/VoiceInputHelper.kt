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
     * 한국어 숫자를 아라비아 숫자로 변환
     * 예: "삼만원" -> "30000", "이만오천원" -> "25000"
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

        // 복잡한 한국어 숫자 처리
        if (result.contains("만")) {
            val parts = result.split("만")
            if (parts.size == 2) {
                val manPart = parts[0].filter { it.isDigit() }.toIntOrNull() ?: 0
                val restPart = parts[1].replace("원", "").filter { it.isDigit() }.toIntOrNull() ?: 0
                return (manPart * 10000 + restPart).toString()
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
}
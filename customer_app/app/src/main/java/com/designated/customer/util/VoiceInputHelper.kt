package com.designated.customer.util

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
}

package com.designated.designated_customer_flutter

import android.content.Intent
import android.speech.RecognizerIntent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.util.*

class MainActivity : FlutterActivity() {
    private val SCREEN_RESOLUTION_CHANNEL = "com.designated.customer/screen_resolution"
    private val SPEECH_CHANNEL = "com.designated.customer/speech"

    private var speechResult: MethodChannel.Result? = null
    private val SPEECH_REQUEST_CODE = 100

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Screen Resolution Channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SCREEN_RESOLUTION_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "getScreenResolution") {
                val resolution = getScreenResolution()
                result.success(resolution)
            } else {
                result.notImplemented()
            }
        }

        // Speech Recognition Channel (Native Android 앱과 동일한 방식)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, SPEECH_CHANNEL).setMethodCallHandler { call, result ->
            if (call.method == "startListening") {
                speechResult = result
                startSpeechRecognition()
            } else {
                result.notImplemented()
            }
        }
    }

    private fun getScreenResolution(): String {
        val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
        val display = windowManager.defaultDisplay
        val realMetrics = android.util.DisplayMetrics()
        display.getRealMetrics(realMetrics)
        return "${realMetrics.widthPixels}x${realMetrics.heightPixels}"
    }

    // Native Android VoiceInputHelper와 동일한 방식
    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR")
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해 주세요")
        }

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE)
        } catch (e: Exception) {
            speechResult?.error("SPEECH_ERROR", "음성 인식을 시작할 수 없습니다", null)
            speechResult = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SPEECH_REQUEST_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (results != null && results.isNotEmpty()) {
                    speechResult?.success(results[0])
                } else {
                    speechResult?.error("NO_RESULT", "음성 인식 결과가 없습니다", null)
                }
            } else {
                speechResult?.error("CANCELLED", "음성 인식이 취소되었습니다", null)
            }
            speechResult = null
        }
    }
}

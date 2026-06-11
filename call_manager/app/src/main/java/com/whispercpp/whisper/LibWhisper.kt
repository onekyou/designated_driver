package com.whispercpp.whisper

import android.content.res.AssetManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.InputStream
import java.util.concurrent.Executors

private const val LOG_TAG = "LibWhisper"

// ─────────────────────────────────────────────────────────────────────────────
// designated_driver vendored from whisper.cpp examples/whisper.android/lib.
//  • 패치: 변종(.so v8fp16/vfpv4) 로드 분기 제거 → 항상 generic "whisper" 로드.
//    (jniLibs/arm64-v8a 에는 generic libwhisper.so 만 둠. 변종 미포함이라 원본
//     LibWhisper 의 fphp/vfpv4 분기는 UnsatisfiedLinkError 를 유발.)
//  • 빌드된 .so 의 jni.c 는 params.language="ko" 로 고정(한국어 PTT 전사용).
//  • transcribeData 는 호출부에서 printTimestamp=false 로 사용(타임스탬프 미부착).
// ─────────────────────────────────────────────────────────────────────────────
/** [온폰 보정] 전사 결과 + 신뢰 지표. */
data class TranscriptResult(
    val text: String,
    val maxNoSpeechProb: Float,
    val minAvgTokenP: Float,
)

class WhisperContext private constructor(private var ptr: Long) {
    // Meet Whisper C++ constraint: Don't access from more than one thread at a time.
    private val scope: CoroutineScope = CoroutineScope(
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )

    suspend fun transcribeData(data: FloatArray, printTimestamp: Boolean = true): String = withContext(scope.coroutineContext) {
        require(ptr != 0L)
        val numThreads = WhisperCpuConfig.preferredThreadCount
        Log.d(LOG_TAG, "Selecting $numThreads threads")
        WhisperLib.fullTranscribe(ptr, numThreads, data, null)
        val textCount = WhisperLib.getTextSegmentCount(ptr)
        return@withContext buildString {
            for (i in 0 until textCount) {
                if (printTimestamp) {
                    val textTimestamp = "[${toTimestamp(WhisperLib.getTextSegmentT0(ptr, i))} --> ${toTimestamp(WhisperLib.getTextSegmentT1(ptr, i))}]"
                    val textSegment = WhisperLib.getTextSegment(ptr, i)
                    append("$textTimestamp: $textSegment\n")
                } else {
                    append(WhisperLib.getTextSegment(ptr, i))
                }
            }
        }
    }

    /**
     * [온폰 보정] 전사 + 신뢰 지표 동반.
     *  - maxNoSpeechProb: 세그먼트 중 최대 no_speech 확률(무음/비음성일수록 1↑).
     *  - minAvgTokenP: 세그먼트 중 최소 평균 토큰확률(횡설수설일수록 0↓).
     */
    suspend fun transcribeWithMeta(data: FloatArray, vadModelPath: String? = null): TranscriptResult = withContext(scope.coroutineContext) {
        require(ptr != 0L)
        val numThreads = WhisperCpuConfig.preferredThreadCount
        WhisperLib.fullTranscribe(ptr, numThreads, data, vadModelPath)
        val n = WhisperLib.getTextSegmentCount(ptr)
        val sb = StringBuilder()
        var maxNs = 0f
        var minP = 1f
        for (i in 0 until n) {
            sb.append(WhisperLib.getTextSegment(ptr, i))
            maxNs = maxOf(maxNs, WhisperLib.getSegmentNoSpeechProb(ptr, i))
            minP = minOf(minP, WhisperLib.getSegmentAvgTokenP(ptr, i))
        }
        TranscriptResult(sb.toString().trim(), maxNs, minP)
    }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0
        }
    }

    protected fun finalize() {
        runBlocking {
            release()
        }
    }

    companion object {
        fun createContextFromFile(filePath: String): WhisperContext {
            val ptr = WhisperLib.initContext(filePath)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context with path $filePath")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromInputStream(stream: InputStream): WhisperContext {
            val ptr = WhisperLib.initContextFromInputStream(stream)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context from input stream")
            }
            return WhisperContext(ptr)
        }

        fun createContextFromAsset(assetManager: AssetManager, assetPath: String): WhisperContext {
            val ptr = WhisperLib.initContextFromAsset(assetManager, assetPath)
            if (ptr == 0L) {
                throw java.lang.RuntimeException("Couldn't create context from asset $assetPath")
            }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String {
            return WhisperLib.getSystemInfo()
        }
    }
}

private class WhisperLib {
    companion object {
        init {
            // 패치: 항상 generic libwhisper.so 로드(변종 미포함). arm64-v8a 베이스라인.
            Log.d(LOG_TAG, "Loading libwhisper.so")
            System.loadLibrary("whisper")
        }

        // JNI methods
        external fun initContextFromInputStream(inputStream: InputStream): Long
        external fun initContextFromAsset(assetManager: AssetManager, assetPath: String): Long
        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, vadModelPath: String?)
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        external fun getSegmentNoSpeechProb(contextPtr: Long, index: Int): Float
        external fun getSegmentAvgTokenP(contextPtr: Long, index: Int): Float
        external fun getSystemInfo(): String
        external fun benchMemcpy(nthread: Int): String
        external fun benchGgmlMulMat(nthread: Int): String
    }
}

//  500 -> 00:05.000
// 6000 -> 01:00.000
private fun toTimestamp(t: Long, comma: Boolean = false): String {
    var msec = t * 10
    val hr = msec / (1000 * 60 * 60)
    msec -= hr * (1000 * 60 * 60)
    val min = msec / (1000 * 60)
    msec -= min * (1000 * 60)
    val sec = msec / 1000
    msec -= sec * 1000

    val delimiter = if (comma) "," else "."
    return String.format("%02d:%02d:%02d%s%03d", hr, min, sec, delimiter, msec)
}

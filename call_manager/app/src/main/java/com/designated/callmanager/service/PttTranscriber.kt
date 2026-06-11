package com.designated.callmanager.service

import android.content.Context
import android.util.Log
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PTT 발화 WAV → 텍스트 전사 (온폰).
 *
 *  - 엔진은 인터페이스 뒤로 추상화 → 온폰 whisper.cpp ↔ (후속) 서버 폴백 교체가 국소.
 *  - 모델은 앱 `filesDir` 의 `ggml-*.bin` 을 자동 탐색 → 모델 스왑 = .bin 파일만 교체(코드 무변경).
 *    (파일럿: `adb push <model> /data/data/<pkg>/files/`. 프로덕션: 첫 실행 다운로드 — 후속.)
 *  - 모델 없음 / 비-arm64 (.so 미지원) / 로드 실패 = graceful → null 반환(전사 생략, 무해).
 */
interface PttTranscriber {
    /** 16kHz mono WAV → 전사 텍스트. 실패/빈결과/미지원 = null. */
    suspend fun transcribe(wav: File): String?
}

/** 온폰 whisper.cpp 전사기 (jni 빌드 시 language="ko" 고정). 모델 1회 로드·재사용. */
object WhisperTranscriber : PttTranscriber {
    private const val TAG = "PttTranscriber"

    private val initMutex = Mutex()
    @Volatile private var context: WhisperContext? = null
    @Volatile private var unavailable = false   // 모델 없음/로드 실패 — 재시도 안 함

    // [온폰 보정] 신뢰 게이팅 임계값 — 못 알아들을 때 "지어낸 텍스트" 대신 마커.
    //   값은 잠정(logcat의 nsp/avgP 실측으로 보정). 보수적으로 잡아 정상 발화는 안 막음.
    private const val NO_SPEECH_MAX = 0.60f   // 이 이상 = 무음/비음성(헛것) 의심
    private const val AVG_TOKEN_P_MIN = 0.45f  // 이 미만 = 횡설수설 의심
    private const val LOW_CONF_MARKER = "(전사 불명확)"

    override suspend fun transcribe(wav: File): String? {
        val ctx = ensureContext() ?: return null
        return try {
            val audio = decodeWaveFile(wav)
            if (audio.isEmpty()) return null
            val r = ctx.transcribeWithMeta(audio, findVadModel()?.absolutePath)
            // 보정 계측: 원문 + 신뢰지표(임계값 튜닝용). 게이트로 가려져도 원문은 로그에 남김.
            Log.i(TAG, "[transcribe] raw='${r.text}' nsp=${"%.2f".format(r.maxNoSpeechProb)} avgP=${"%.2f".format(r.minAvgTokenP)}")
            if (r.text.isEmpty()) return null
            val lowConfidence = r.maxNoSpeechProb >= NO_SPEECH_MAX || r.minAvgTokenP < AVG_TOKEN_P_MIN
            if (lowConfidence) {
                Log.i(TAG, "[transcribe] 신뢰 낮음 → 마커 게시(지어낸 기록 0): $LOW_CONF_MARKER")
                LOW_CONF_MARKER
            } else {
                r.text
            }
        } catch (e: Throwable) {
            Log.e(TAG, "[transcribe] 실패", e)
            null
        }
    }

    /** 모델 컨텍스트 1회 로드(싱글톤). 실패 시 unavailable 래치 → 이후 즉시 null. */
    private suspend fun ensureContext(): WhisperContext? {
        context?.let { return it }
        if (unavailable) return null
        return initMutex.withLock {
            context?.let { return it }
            if (unavailable) return null
            try {
                val model = findModelFile() ?: run {
                    Log.w(TAG, "[ensureContext] filesDir 에 ggml-*.bin 모델 없음 — 전사 비활성")
                    unavailable = true
                    return null
                }
                Log.i(TAG, "[ensureContext] 모델 로드: ${model.name} (${model.length() / 1024 / 1024}MB)")
                val info = try { WhisperContext.getSystemInfo() } catch (e: Throwable) { "?" }
                Log.i(TAG, "[ensureContext] whisper system info: $info")
                WhisperContext.createContextFromFile(model.absolutePath).also { context = it }
            } catch (e: Throwable) {
                // UnsatisfiedLinkError(비 arm64) 포함 — graceful 비활성
                Log.e(TAG, "[ensureContext] whisper 로드 실패 — 전사 비활성", e)
                unavailable = true
                null
            }
        }
    }

    private var appFilesDir: File? = null
    fun attachContext(ctx: Context) { appFilesDir = ctx.applicationContext.filesDir }

    private fun findModelFile(): File? {
        val dir = appFilesDir ?: return null
        return dir.listFiles { f ->
            f.isFile && f.name.startsWith("ggml-") && f.name.endsWith(".bin") &&
                !f.name.contains("silero")   // silero=VAD 모델이라 whisper 모델 후보서 제외
        }?.maxByOrNull { it.length() }       // 여러 개면 큰(정확) 모델 우선
    }

    /** [온폰 보정] VAD(Silero) 모델 — filesDir의 ggml-silero-*.bin. 없으면 VAD 비활성(graceful). */
    private fun findVadModel(): File? {
        val dir = appFilesDir ?: return null
        return dir.listFiles { f -> f.isFile && f.name.startsWith("ggml-silero") && f.name.endsWith(".bin") }
            ?.firstOrNull()
    }
}

/**
 * 표준 PCM16 WAV(44바이트 헤더) → FloatArray.
 *  vendored from whisper.cpp examples/whisper.android (decodeWaveFile).
 *  Agora startAudioRecording(MIC, 16kHz, mono) 출력이 표준 헤더라는 전제 — 검증에서 확인.
 */
private fun decodeWaveFile(file: File): FloatArray {
    val baos = ByteArrayOutputStream()
    file.inputStream().use { it.copyTo(baos) }
    val buffer = ByteBuffer.wrap(baos.toByteArray())
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    val channel = buffer.getShort(22).toInt().coerceAtLeast(1)
    buffer.position(44)
    val shortBuffer = buffer.asShortBuffer()
    val shortArray = ShortArray(shortBuffer.limit())
    shortBuffer.get(shortArray)
    return FloatArray(shortArray.size / channel) { index ->
        when (channel) {
            1 -> (shortArray[index] / 32767.0f).coerceIn(-1f..1f)
            else -> ((shortArray[2 * index] + shortArray[2 * index + 1]) / 32767.0f / 2.0f).coerceIn(-1f..1f)
        }
    }
}

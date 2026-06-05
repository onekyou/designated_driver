package com.designated.callmanager.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

/**
 * PTT 콜드 발화용 음성 메모 녹음기 (MediaRecorder 래퍼).
 *
 * 콜드 press(currentChannel==null)일 때 Agora 라이브 대신 로컬 녹음 → 채팅 음성 첨부로 전달.
 * 포맷: MPEG_4 컨테이너 + AAC, 16kHz mono — MediaPlayer 무가공 재생 + storage rule audio 타입 정합.
 * 출력: cacheDir/ptt_voice/{messageId}.m4a (업로드 후 ChatRepository가 삭제).
 *
 * 마이크 점유: 백그라운드 복귀 음성 메모는 Agora 미사용(MediaRecorder만 마이크). 포그라운드 라이브는 Agora가 마이크 사용(별도 경로).
 */
class PttRecorder {

    companion object {
        private const val TAG = "PttRecorder"
        private const val SAMPLE_RATE = 16_000
        private const val BIT_RATE = 24_000
        private const val CHANNELS = 1
        const val MIN_RECORD_MS = 500L      // 이보다 짧으면 오발화로 간주 → drop
        const val MAX_RECORD_MS = 60_000L   // 안전 상한 (호출측 가드 보조)
    }

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtRealtime = 0L
    val isRecording: Boolean get() = recorder != null

    data class RecordResult(val file: File, val durationMs: Long)

    /** 녹음 시작. RECORD_AUDIO 미허가면 false 반환(호출측이 권한 요청). 임시 파일명은 내부 UUID. */
    fun start(ctx: Context): Boolean {
        if (recorder != null) {
            Log.w(TAG, "start: 이미 녹음 중 — 무시")
            return false
        }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "start: RECORD_AUDIO 미허가")
            return false
        }
        return try {
            val dir = File(ctx.cacheDir, "ptt_voice").apply { mkdirs() }
            val file = File(dir, "ptt_${UUID.randomUUID()}.m4a")
            @Suppress("DEPRECATION")
            val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(ctx) else MediaRecorder()
            rec.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(SAMPLE_RATE)
                setAudioEncodingBitRate(BIT_RATE)
                setAudioChannels(CHANNELS)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = rec
            outputFile = file
            startedAtRealtime = SystemClock.elapsedRealtime()
            Log.i(TAG, "start: 녹음 시작 $file")
            true
        } catch (e: Exception) {
            Log.e(TAG, "start: 녹음 시작 실패", e)
            safeRelease()
            false
        }
    }

    /** 녹음 종료. 성공 시 RecordResult, 실패/너무 짧음이면 null(파일 정리). */
    fun stop(): RecordResult? {
        val rec = recorder ?: return null
        val file = outputFile
        val durationMs = SystemClock.elapsedRealtime() - startedAtRealtime
        return try {
            rec.stop()
            rec.release()
            recorder = null
            outputFile = null
            if (file == null || !file.exists() || file.length() == 0L) {
                Log.w(TAG, "stop: 출력 파일 없음/빈 파일 — drop")
                file?.delete()
                null
            } else {
                Log.i(TAG, "stop: 녹음 종료 ${durationMs}ms ${file.length()}B")
                RecordResult(file, durationMs)
            }
        } catch (e: Exception) {
            // stop() 직후 예외(너무 짧아 인코딩 실패 등) → 파일 정리
            Log.w(TAG, "stop: 종료 예외(짧은 녹음 등) — drop", e)
            safeRelease()
            file?.delete()
            null
        }
    }

    /** 녹음 취소(파일 삭제). */
    fun cancel() {
        val file = outputFile
        safeRelease()
        try { file?.delete() } catch (_: Exception) {}
    }

    private fun safeRelease() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        outputFile = null
    }
}

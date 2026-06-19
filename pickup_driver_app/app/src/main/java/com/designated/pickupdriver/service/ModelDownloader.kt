package com.designated.pickupdriver.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * 온폰 whisper 모델 자동 다운로드 — 케이블·adb push 없이 원격 보급용.
 *
 *  앱 첫 실행(로그인 후) 시 filesDir 에 모델이 없으면 Firebase Storage(ptt_models/)에서 받아둠.
 *  받으면 [WhisperTranscriber] 가 filesDir 의 `ggml-*.bin` 을 자동 탐색해 전사 작동(코드 무변경).
 *  멱등: 이미 올바른 크기로 있으면 즉시 스킵. 중단되면 다음 실행 때 재시도(.part → 검증 → rename).
 *  (call_manager ModelDownloader fork — 패키지만 변경)
 */
object ModelDownloader {
    private const val TAG = "ModelDownloader"
    private const val CH_ID = "ptt_model_download"
    const val NOTI_ID = 7701

    /** (Storage 경로, filesDir 파일명, 기대 바이트). 모델 교체 = 버킷 파일 + 여기 크기만 갱신. */
    private data class Model(val path: String, val name: String, val size: Long)
    private val MODELS = listOf(
        Model("ptt_models/ggml-small-q5_1.bin", "ggml-small-q5_1.bin", 190_085_487L),
        Model("ptt_models/ggml-silero-v5.1.2.bin", "ggml-silero-v5.1.2.bin", 885_098L),
    )

    @Volatile private var running = false

    /** 아직 받지 않은(또는 크기 불일치) 모델이 있나 — 포그라운드 서비스 시작 게이트용. */
    fun modelsMissing(context: Context): Boolean = MODELS.any { m ->
        val f = File(context.filesDir, m.name)
        !(f.exists() && f.length() == m.size)
    }

    /** filesDir 에 모델이 없으면 Storage 에서 다운로드. 실패해도 크래시 없음(다음 실행 재시도). */
    suspend fun ensureModels(context: Context) {
        if (running) return
        running = true
        try {
            val missing = MODELS.filter { m ->
                val f = File(context.filesDir, m.name)
                !(f.exists() && f.length() == m.size)
            }
            if (missing.isEmpty()) {
                Log.i(TAG, "모델 이미 존재 — 스킵")
                return
            }
            Log.i(TAG, "다운로드 대상 ${missing.size}개")
            val storage = Firebase.storage
            for (m in missing) downloadOne(context, storage, m)
            Log.i(TAG, "모든 모델 준비 완료")
        } catch (e: Throwable) {
            Log.w(TAG, "모델 다운로드 실패(다음 실행 재시도): ${e.message}")
        } finally {
            cancelNotification(context)
            running = false
        }
    }

    private suspend fun downloadOne(
        context: Context,
        storage: com.google.firebase.storage.FirebaseStorage,
        m: Model,
    ) {
        val target = File(context.filesDir, m.name)
        val part = File(context.filesDir, "${m.name}.part")
        if (part.exists()) part.delete()
        Log.i(TAG, "다운로드 시작: ${m.name} (${m.size / 1024 / 1024}MB)")
        notify(context, "${m.name} 준비 중…", 0)
        storage.reference.child(m.path).getFile(part)
            .addOnProgressListener { s ->
                val pct = if (m.size > 0) (100 * s.bytesTransferred / m.size).toInt() else 0
                notify(context, "음성 인식 모델 다운로드 ${pct}%", pct)
            }
            .await()
        if (part.length() != m.size) {
            part.delete()
            throw IllegalStateException("${m.name} 크기 불일치 ${part.length()} != ${m.size}")
        }
        if (target.exists()) target.delete()
        if (!part.renameTo(target)) throw IllegalStateException("${m.name} rename 실패")
        Log.i(TAG, "다운로드 완료: ${m.name}")
    }

    /** 다운로드 진행 알림 — 포그라운드 서비스 startForeground + 진행 업데이트 공용. 채널 보장 포함. */
    fun buildNotification(context: Context, text: String, progress: Int): android.app.Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CH_ID, "음성 인식 모델 다운로드", NotificationManager.IMPORTANCE_LOW),
            )
        }
        return NotificationCompat.Builder(context, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("음성 인식 모델 준비")
            .setContentText(text)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun notify(context: Context, text: String, progress: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTI_ID, buildNotification(context, text, progress))
        } catch (_: Throwable) { /* 알림 권한 없음 등 — 다운로드엔 무영향 */ }
    }

    private fun cancelNotification(context: Context) {
        try {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTI_ID)
        } catch (_: Throwable) {}
    }
}

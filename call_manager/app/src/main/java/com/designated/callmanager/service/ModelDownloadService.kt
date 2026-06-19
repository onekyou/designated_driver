package com.designated.callmanager.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 온폰 whisper 모델 다운로드 포그라운드 서비스.
 *  앱이 백그라운드로 가도 OS 가 네트워크를 스로틀하지 않게 포그라운드로 190MB 다운로드 완주.
 *  완료/실패 시 self-stop. 트리거 = CallManagerApplication auth 리스너(로그인 + 모델 없음).
 */
class ModelDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 5초 내 startForeground 필수 — 즉시 호출.
        startForeground(
            ModelDownloader.NOTI_ID,
            ModelDownloader.buildNotification(this, "음성 인식 모델 준비 중…", 0),
        )
        scope.launch {
            try {
                ModelDownloader.ensureModels(this@ModelDownloadService)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

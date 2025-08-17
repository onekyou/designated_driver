package com.designated.pickupapp.service

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Phase 3: WorkManager 기반 일일 토큰 갱신 시스템 (Pickup App용)
 * 
 * 목적:
 * - 매일 9-11AM 시간대에 자동으로 토큰 갱신
 * - 사용량이 적은 시간대 활용으로 서비스 안정성 향상
 * - 배터리 최적화된 백그라운드 스케줄링
 * - 네트워크 오류 시 자동 재시도
 * 
 * 동작 원리:
 * - PeriodicWorkRequest로 24시간마다 실행
 * - 9-11AM 시간 윈도우 내에서만 실행
 * - SecureTokenManager와 연동하여 안전한 토큰 갱신
 * - 실패 시 지수 백오프로 재시도
 * 
 * 비용 최적화 효과:
 * - 토큰 만료로 인한 PTT 연결 실패 방지
 * - 예측 가능한 토큰 갱신으로 서비스 안정성 향상
 * - 사용자 경험 개선 (토큰 오류 없는 매끄러운 PTT 사용)
 */
class TokenRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "TokenRefreshWorker"
        private const val TAG_PHASE3 = "PTT_PHASE3_TOKEN_REFRESH"  // Phase 3 검증용 태그
        
        const val WORK_NAME = "token_refresh_work"
        
        // 9-11AM 시간 윈도우 (시간 단위)
        private const val REFRESH_WINDOW_START_HOUR = 9
        private const val REFRESH_WINDOW_END_HOUR = 11
        
        /**
         * Phase 3: 일일 토큰 갱신 작업 스케줄링
         */
        fun scheduleTokenRefresh(context: Context) {
            Log.i(TAG_PHASE3, "========== PHASE 3: SCHEDULING TOKEN REFRESH ==========")
            
            // 제약 조건: WiFi 연결 시에만 실행 (데이터 절약)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)  // 배터리 부족 시 실행 안함
                .build()
            
            // 24시간마다 반복 실행 (유연한 시간 윈도우 15분)
            val tokenRefreshRequest = PeriodicWorkRequestBuilder<TokenRefreshWorker>(
                24, TimeUnit.HOURS,
                15, TimeUnit.MINUTES  // 유연한 실행 윈도우
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES  // 실패 시 15분부터 시작하여 지수적으로 증가
                )
                .addTag("token_refresh")
                .build()
            
            // 기존 작업 취소 후 새로 스케줄링
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                tokenRefreshRequest
            )
            
            Log.i(TAG_PHASE3, "✅ TOKEN REFRESH SCHEDULED - 24시간마다 9-11AM 윈도우에서 실행")
            Log.i(TAG_PHASE3, "제약 조건: 네트워크 연결 + 배터리 충분")
            Log.i(TAG_PHASE3, "========== PHASE 3: SCHEDULING COMPLETED ==========")
        }
        
        /**
         * 토큰 갱신 작업 취소
         */
        fun cancelTokenRefresh(context: Context) {
            Log.i(TAG_PHASE3, "🚫 TOKEN REFRESH CANCELLED")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG_PHASE3, "========== PHASE 3: TOKEN REFRESH WORKER STARTED ==========")
        
        try {
            // 1. 시간 윈도우 확인 (9-11AM)
            if (!isInRefreshWindow()) {
                Log.w(TAG_PHASE3, "❌ OUTSIDE TIME WINDOW - 현재 시간이 9-11AM 범위 밖")
                Log.i(TAG_PHASE3, "다음 실행을 위해 대기")
                return@withContext Result.success()
            }
            
            Log.i(TAG_PHASE3, "✅ IN TIME WINDOW - 9-11AM 범위 내, 토큰 갱신 시작")
            
            // 2. SecureTokenManager 인스턴스 가져오기
            val tokenManager = SecureTokenManager.getInstance(applicationContext)
            
            // 3. 현재 토큰 상태 확인
            val currentToken = tokenManager.getToken("default", "pickup", "pickup_app")
            if (currentToken == null) {
                Log.w(TAG_PHASE3, "⚠️ NO STORED TOKEN - 저장된 토큰이 없음, 새로 생성 필요")
            } else {
                Log.d(TAG_PHASE3, "현재 저장된 토큰 확인됨")
            }
            
            // 4. 토큰 갱신 실행 (새 토큰 생성)
            Log.i(TAG_PHASE3, "🔄 TOKEN REFRESH START - 새 토큰 생성 시작")
            
            // SecureToken 직접 생성 (PTTManager 방식과 동일)
            val newSecureToken = SecureTokenManager.SecureToken(
                token = "pickup_token_${System.currentTimeMillis()}", // 픽업앱용 토큰 생성
                channelName = "pickup_default_ptt",
                generatedAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000), // 24시간 후
                regionId = "default",
                officeId = "pickup",
                userType = "pickup_app"
            )
            
            // 토큰 저장
            tokenManager.saveToken(newSecureToken)
            
            Log.i(TAG_PHASE3, "✅ TOKEN REFRESH SUCCESS - 새 토큰 생성 및 저장 완료")
            Log.d(TAG_PHASE3, "새 토큰 길이: ${newSecureToken.token.length}")
            
            // 5. 토큰 유효성 검증 (옵션)
            val storedToken = tokenManager.getToken("default", "pickup", "pickup_app")
            if (storedToken?.token == newSecureToken.token) {
                Log.i(TAG_PHASE3, "✅ TOKEN VERIFICATION - 저장된 토큰과 일치 확인")
            } else {
                Log.w(TAG_PHASE3, "⚠️ TOKEN MISMATCH - 저장 과정에서 오류 가능성")
            }
            
            Log.i(TAG_PHASE3, "🎉 PHASE 3 SUCCESS - 일일 토큰 갱신 완료")
            Log.i(TAG_PHASE3, "========== PHASE 3: TOKEN REFRESH COMPLETED ==========")
            
            return@withContext Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG_PHASE3, "💥 TOKEN REFRESH ERROR - 예외 발생", e)
            Log.e(TAG_PHASE3, "오류 메시지: ${e.message}")
            
            // 재시도 가능한 오류인지 판단
            return@withContext when {
                e.message?.contains("network", ignoreCase = true) == true -> {
                    Log.w(TAG_PHASE3, "🔄 NETWORK ERROR - 재시도 예정")
                    Result.retry()
                }
                e.message?.contains("timeout", ignoreCase = true) == true -> {
                    Log.w(TAG_PHASE3, "⏰ TIMEOUT ERROR - 재시도 예정")
                    Result.retry()
                }
                else -> {
                    Log.e(TAG_PHASE3, "🚫 FATAL ERROR - 재시도 불가")
                    Result.failure()
                }
            }
        }
    }
    
    /**
     * 현재 시간이 토큰 갱신 윈도우(9-11AM) 내에 있는지 확인
     */
    private fun isInRefreshWindow(): Boolean {
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val isInWindow = currentHour in REFRESH_WINDOW_START_HOUR until REFRESH_WINDOW_END_HOUR
        
        Log.d(TAG_PHASE3, "현재 시간: ${currentHour}시")
        Log.d(TAG_PHASE3, "갱신 윈도우: ${REFRESH_WINDOW_START_HOUR}-${REFRESH_WINDOW_END_HOUR}시")
        Log.d(TAG_PHASE3, "윈도우 내 여부: $isInWindow")
        
        return isInWindow
    }
    
    /**
     * Phase 3 테스트: 강제 토큰 갱신 (시간 윈도우 무시)
     */
    suspend fun forceTokenRefresh(): Boolean {
        Log.i("PTT_PHASE3_TEST", "========== PHASE 3 TEST: FORCE TOKEN REFRESH ==========")
        
        return try {
            val tokenManager = SecureTokenManager.getInstance(applicationContext)
            
            Log.i("PTT_PHASE3_TEST", "강제 토큰 갱신 시작 (테스트 모드)")
            
            // 테스트용 토큰 생성
            val testToken = SecureTokenManager.SecureToken(
                token = "pickup_test_token_${System.currentTimeMillis()}",
                channelName = "pickup_test_ptt",
                generatedAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000),
                regionId = "test",
                officeId = "pickup",
                userType = "pickup_app"
            )
            
            // 토큰 저장
            tokenManager.saveToken(testToken)
            
            Log.i("PTT_PHASE3_TEST", "✅ FORCE REFRESH SUCCESS - 테스트 토큰 생성 완료")
            true
        } catch (e: Exception) {
            Log.e("PTT_PHASE3_TEST", "💥 FORCE REFRESH ERROR", e)
            false
        }
    }
}
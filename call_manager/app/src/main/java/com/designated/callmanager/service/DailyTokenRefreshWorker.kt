package com.designated.callmanager.service

import android.content.Context
import android.util.Log
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 일일 토큰 자동 갱신 Worker
 * 새벽 시간을 피하고 네트워크 실패에 대응
 */
class DailyTokenRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "DailyTokenRefresh"
        private const val WORK_NAME = "daily_token_refresh"
        
        /**
         * WorkManager 스케줄링
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            // 현재 시간 기준으로 다음 갱신 시간 계산
            val nextRefreshDelay = calculateNextRefreshDelay()
            
            val refreshRequest = OneTimeWorkRequestBuilder<DailyTokenRefreshWorker>()
                .setConstraints(constraints)
                .setInitialDelay(nextRefreshDelay, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15, TimeUnit.MINUTES
                )
                .addTag(WORK_NAME)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    refreshRequest
                )
            
            Log.i(TAG, "Token refresh scheduled in ${nextRefreshDelay / 1000 / 60} minutes")
        }
        
        /**
         * 다음 갱신 시간 계산 (업무 한가한 시간 선택)
         */
        private fun calculateNextRefreshDelay(): Long {
            val now = System.currentTimeMillis()
            val calendar = java.util.Calendar.getInstance()
            
            // 목표 시간: 오전 9시 ~ 11시 사이 랜덤 (대리운전 업무 가장 한가한 시간)
            val targetHour = Random.nextInt(9, 11) // 9시 또는 10시
            val targetMinute = Random.nextInt(0, 60)
            
            calendar.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
            calendar.set(java.util.Calendar.MINUTE, targetMinute)
            calendar.set(java.util.Calendar.SECOND, 0)
            
            // 이미 지난 시간이면 다음 날로
            if (calendar.timeInMillis <= now) {
                calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            
            Log.d(TAG, "Next token refresh scheduled at ${calendar.time}")
            
            return calendar.timeInMillis - now
        }
    }
    
    private val secureTokenManager by lazy { SecureTokenManager(applicationContext) }
    private val functions by lazy { FirebaseFunctions.getInstance("asia-northeast3") }
    private val auth by lazy { FirebaseAuth.getInstance() }
    
    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting daily token refresh")
        
        return try {
            // 1. 현재 사용자 정보 가져오기
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Log.w(TAG, "No authenticated user, skipping token refresh")
                return Result.success()
            }
            
            // 2. 저장된 토큰 정보 확인
            val regionId = inputData.getString("regionId") ?: "default"
            val officeId = inputData.getString("officeId") ?: "default"
            val userType = inputData.getString("userType") ?: "call_manager"
            
            // 3. 토큰 갱신 필요 여부 확인
            if (!secureTokenManager.needsRefresh(regionId, officeId, userType)) {
                Log.i(TAG, "Token still valid, skipping refresh")
                scheduleNextRefresh()
                return Result.success()
            }
            
            // 4. 새 토큰 생성 (24시간 유효)
            val newToken = generateDailyToken(regionId, officeId, userType)
            
            if (newToken != null) {
                // 5. 안전하게 저장
                secureTokenManager.saveToken(newToken)
                Log.i(TAG, "Token refreshed successfully")
                
                // 6. 다음 갱신 스케줄링
                scheduleNextRefresh()
                
                Result.success()
            } else {
                Log.e(TAG, "Failed to generate new token")
                Result.retry() // 재시도
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed", e)
            
            // 네트워크 오류인 경우 재시도
            if (e.message?.contains("network", ignoreCase = true) == true) {
                Result.retry()
            } else {
                // 다른 오류는 실패 처리
                Result.failure()
            }
        }
    }
    
    /**
     * 24시간 유효한 토큰 생성
     */
    private suspend fun generateDailyToken(
        regionId: String,
        officeId: String,
        userType: String
    ): SecureTokenManager.SecureToken? {
        return try {
            val data = hashMapOf(
                "regionId" to regionId,
                "officeId" to officeId,
                "userType" to userType,
                "validityHours" to 24 // 24시간 요청
            )
            
            val result = functions
                .getHttpsCallable("generateDailyAgoraToken")
                .call(data)
                .await()
            
            val resultData = result.data as? Map<String, Any>
            val token = resultData?.get("token") as? String
            val channelName = resultData?.get("channelName") as? String
            val expiresIn = (resultData?.get("expiresIn") as? Number)?.toLong() ?: (24 * 3600 * 1000L)
            
            if (token != null && channelName != null) {
                SecureTokenManager.SecureToken(
                    token = token,
                    channelName = channelName,
                    generatedAt = System.currentTimeMillis(),
                    expiresAt = System.currentTimeMillis() + expiresIn,
                    regionId = regionId,
                    officeId = officeId,
                    userType = userType
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate daily token", e)
            null
        }
    }
    
    /**
     * 다음 갱신 스케줄링
     */
    private fun scheduleNextRefresh() {
        schedule(applicationContext)
    }
}
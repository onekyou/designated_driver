package com.designated.callmanager.ptt.network

import android.util.Log
import com.designated.callmanager.ptt.state.TokenResult
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

/**
 * Agora 토큰 관리자
 * Firebase Functions를 통해 토큰을 획득하고 캐싱
 */
class TokenManager(
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) {
    private val TAG = "TokenManager"
    
    // 토큰 캐시 (key: "channelName:uid", value: TokenData)
    private val tokenCache = ConcurrentHashMap<String, TokenData>()
    
    companion object {
        // 토큰 유효 시간 (23시간 - 안전 마진 1시간)
        private const val TOKEN_VALIDITY_DURATION = 23 * 60 * 60 * 1000L // 23 hours in milliseconds
        
        // 토큰 새로고침 임계값 (1시간 남았을 때 새로고침)
        private const val TOKEN_REFRESH_THRESHOLD = 60 * 60 * 1000L // 1 hour in milliseconds
    }
    
    /**
     * 토큰 데이터 클래스
     */
    private data class TokenData(
        val token: String,
        val uid: Int,
        val timestamp: Long,
        val expiryTime: Long = timestamp + TOKEN_VALIDITY_DURATION
    ) {
        /**
         * 토큰이 만료되었는지 확인
         */
        fun isExpired(): Boolean {
            return System.currentTimeMillis() >= expiryTime
        }
        
        /**
         * 토큰 새로고침이 필요한지 확인
         */
        fun needsRefresh(): Boolean {
            val timeUntilExpiry = expiryTime - System.currentTimeMillis()
            return timeUntilExpiry <= TOKEN_REFRESH_THRESHOLD
        }
    }
    
    /**
     * Agora 토큰 획득
     * @param channelName 채널 이름
     * @param uid 사용자 UID
     * @param regionId 지역 ID
     * @param officeId 사무실 ID
     * @param userType 사용자 타입
     * @param forceRefresh 강제 새로고침 여부
     * @return TokenResult
     */
    suspend fun getToken(
        channelName: String,
        uid: Int,
        regionId: String,
        officeId: String,
        userType: String = "call_manager",
        forceRefresh: Boolean = false
    ): TokenResult {
        val cacheKey = "$channelName:$uid"
        
        try {
            // 1. 캐시 확인 (강제 새로고침이 아닌 경우)
            if (!forceRefresh) {
                tokenCache[cacheKey]?.let { cachedData ->
                    when {
                        cachedData.isExpired() -> {
                            Log.d(TAG, "Token expired for $cacheKey, fetching new one")
                            tokenCache.remove(cacheKey)
                        }
                        cachedData.needsRefresh() -> {
                            Log.d(TAG, "Token needs refresh for $cacheKey, but using cached token")
                            // 토큰이 곧 만료되지만 아직 유효하므로 캐시된 토큰 반환
                            return TokenResult.Success(cachedData.token, cachedData.uid)
                        }
                        else -> {
                            Log.d(TAG, "Using cached token for $cacheKey")
                            return TokenResult.Success(cachedData.token, cachedData.uid)
                        }
                    }
                }
            }
            
            // 2. 서버에서 새 토큰 획득
            Log.i(TAG, "Fetching new token for channel: $channelName, uid: $uid")
            
            val data = hashMapOf(
                "channelName" to channelName,
                "uid" to uid,
                "regionId" to regionId,
                "officeId" to officeId,
                "userType" to userType,
                "role" to "publisher" // PTT는 publisher 권한 필요
            )
            
            val result = functions
                .getHttpsCallable("generateAgoraToken")
                .call(data)
                .await()
            
            val responseData = result.data as? Map<*, *>
                ?: throw IllegalStateException("Invalid response format")
            
            val token = responseData["token"] as? String
                ?: throw IllegalStateException("Token not found in response")
            
            // 3. 캐시에 저장
            val tokenData = TokenData(
                token = token,
                uid = uid,
                timestamp = System.currentTimeMillis()
            )
            tokenCache[cacheKey] = tokenData
            
            Log.i(TAG, "Token acquired successfully for $cacheKey")
            return TokenResult.Success(token, uid)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get token for $cacheKey", e)
            return TokenResult.Failure(e)
        }
    }
    
    
    /**
     * 특정 채널의 토큰 무효화
     */
    fun invalidateToken(channelName: String, uid: Int) {
        val cacheKey = "$channelName:$uid"
        tokenCache.remove(cacheKey)
        Log.d(TAG, "Token invalidated for $cacheKey")
    }
    
    /**
     * 모든 토큰 캐시 클리어
     */
    fun clearAllTokens() {
        tokenCache.clear()
        Log.d(TAG, "All token cache cleared")
    }
    
    /**
     * 만료된 토큰 정리
     */
    fun cleanupExpiredTokens() {
        val expiredKeys = tokenCache.entries
            .filter { it.value.isExpired() }
            .map { it.key }
        
        expiredKeys.forEach { key ->
            tokenCache.remove(key)
            Log.d(TAG, "Expired token removed: $key")
        }
        
        if (expiredKeys.isNotEmpty()) {
            Log.i(TAG, "Cleaned up ${expiredKeys.size} expired tokens")
        }
    }
    
    /**
     * 캐시 상태 디버그 출력
     */
    fun debugPrintCacheStatus() {
        Log.d(TAG, "=== Token Cache Status ===")
        Log.d(TAG, "Total cached tokens: ${tokenCache.size}")
        
        tokenCache.forEach { (key, data) ->
            val timeUntilExpiry = (data.expiryTime - System.currentTimeMillis()) / 1000 / 60 // minutes
            val status = when {
                data.isExpired() -> "EXPIRED"
                data.needsRefresh() -> "NEEDS_REFRESH"
                else -> "VALID"
            }
            Log.d(TAG, "$key -> Status: $status, Expires in: ${timeUntilExpiry}min")
        }
        Log.d(TAG, "========================")
    }
    
    /**
     * 토큰 검증 (서버 측 검증이 필요한 경우)
     */
    suspend fun validateToken(token: String, channelName: String, uid: Int): Boolean {
        return try {
            val data = hashMapOf(
                "token" to token,
                "channelName" to channelName,
                "uid" to uid
            )
            
            val result = functions
                .getHttpsCallable("validateAgoraToken")
                .call(data)
                .await()
            
            val responseData = result.data as? Map<*, *>
            responseData?.get("valid") as? Boolean ?: false
            
        } catch (e: Exception) {
            Log.e(TAG, "Token validation failed", e)
            false
        }
    }
}
package com.designated.callmanager.service

import android.util.Log
import java.util.concurrent.TimeUnit

/**
 * Agora 토큰 캐싱 시스템
 * Firebase Functions 호출을 최소화하여 비용 절감
 */
class TokenCache {
    companion object {
        private const val TAG = "TokenCache"
        private const val CACHE_DURATION_MINUTES = 10L // 10분간 캐싱
        private const val REFRESH_BEFORE_EXPIRY_MINUTES = 2L // 만료 2분 전 갱신
    }
    
    data class CachedToken(
        val token: String,
        val channelName: String,
        val timestamp: Long,
        val expiresIn: Long = TimeUnit.MINUTES.toMillis(CACHE_DURATION_MINUTES)
    ) {
        fun isExpired(): Boolean {
            val now = System.currentTimeMillis()
            val expiryTime = timestamp + expiresIn
            val refreshTime = expiryTime - TimeUnit.MINUTES.toMillis(REFRESH_BEFORE_EXPIRY_MINUTES)
            
            return now >= refreshTime
        }
        
        fun remainingTimeSeconds(): Long {
            val now = System.currentTimeMillis()
            val expiryTime = timestamp + expiresIn
            return TimeUnit.MILLISECONDS.toSeconds(expiryTime - now)
        }
    }
    
    private val cache = mutableMapOf<String, CachedToken>()
    
    /**
     * 캐시 키 생성 (regionId_officeId_userType)
     */
    private fun getCacheKey(regionId: String, officeId: String, userType: String): String {
        return "${regionId}_${officeId}_${userType}"
    }
    
    /**
     * 토큰 저장
     */
    fun saveToken(
        regionId: String, 
        officeId: String, 
        userType: String,
        token: String,
        channelName: String
    ) {
        val key = getCacheKey(regionId, officeId, userType)
        val cachedToken = CachedToken(
            token = token,
            channelName = channelName,
            timestamp = System.currentTimeMillis()
        )
        
        cache[key] = cachedToken
        Log.i(TAG, "Token cached for key: $key, expires in ${cachedToken.remainingTimeSeconds()}s")
    }
    
    /**
     * 유효한 토큰 가져오기
     */
    fun getValidToken(regionId: String, officeId: String, userType: String): CachedToken? {
        val key = getCacheKey(regionId, officeId, userType)
        val cachedToken = cache[key]
        
        return if (cachedToken != null && !cachedToken.isExpired()) {
            Log.i(TAG, "Using cached token for key: $key, remaining time: ${cachedToken.remainingTimeSeconds()}s")
            cachedToken
        } else {
            if (cachedToken != null) {
                Log.i(TAG, "Cached token expired for key: $key")
                cache.remove(key)
            }
            null
        }
    }
    
    /**
     * 캐시 정리
     */
    fun clearCache() {
        cache.clear()
        Log.i(TAG, "Token cache cleared")
    }
    
    /**
     * 특정 키의 캐시 제거
     */
    fun invalidateToken(regionId: String, officeId: String, userType: String) {
        val key = getCacheKey(regionId, officeId, userType)
        cache.remove(key)
        Log.i(TAG, "Token invalidated for key: $key")
    }
}
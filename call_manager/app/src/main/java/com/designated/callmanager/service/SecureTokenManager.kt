package com.designated.callmanager.service

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore를 활용한 안전한 토큰 관리자
 * 24시간 유효한 토큰을 OS 수준에서 안전하게 저장/관리
 */
class SecureTokenManager(private val context: Context) {
    
    companion object {
        private const val TAG = "SecureTokenManager"
        private const val TAG_PHASE1 = "PTT_PHASE1_SECURITY"  // Phase 1 검증용 태그
        private const val KEYSTORE_ALIAS = "PTT_TOKEN_KEY"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val SHARED_PREFS_NAME = "secure_ptt_tokens"
        private const val TOKEN_VALIDITY_HOURS = 24
        private const val BUFFER_TIME_MINUTES = 30 // 만료 30분 전 갱신
        
        @Volatile
        private var INSTANCE: SecureTokenManager? = null
        
        /**
         * Singleton 인스턴스 가져오기 (Phase 3용)
         */
        fun getInstance(context: Context): SecureTokenManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecureTokenManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    data class SecureToken(
        val token: String,
        val channelName: String,
        val generatedAt: Long,
        val expiresAt: Long,
        val regionId: String,
        val officeId: String,
        val userType: String
    ) {
        fun isValid(): Boolean {
            val now = System.currentTimeMillis()
            val bufferTime = BUFFER_TIME_MINUTES * 60 * 1000L
            return now < (expiresAt - bufferTime)
        }
        
        fun remainingHours(): Int {
            val remaining = expiresAt - System.currentTimeMillis()
            return (remaining / (1000 * 60 * 60)).toInt()
        }
        
        fun shouldRefresh(): Boolean {
            val now = System.currentTimeMillis()
            val bufferTime = BUFFER_TIME_MINUTES * 60 * 1000L
            return now >= (expiresAt - bufferTime)
        }
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            SHARED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * 토큰 안전하게 저장
     */
    fun saveToken(token: SecureToken) {
        try {
            Log.d(TAG_PHASE1, "========== PHASE 1: TOKEN SAVE START ==========")
            Log.d(TAG_PHASE1, "Region: ${token.regionId}, Office: ${token.officeId}, UserType: ${token.userType}")
            Log.d(TAG_PHASE1, "Token Length: ${token.token.length}, Channel: ${token.channelName}")
            Log.d(TAG_PHASE1, "Expires in: ${token.remainingHours()} hours")
            
            with(encryptedPrefs.edit()) {
                putString("token_${token.regionId}_${token.officeId}_${token.userType}", token.token)
                putString("channel_${token.regionId}_${token.officeId}_${token.userType}", token.channelName)
                putLong("generated_${token.regionId}_${token.officeId}_${token.userType}", token.generatedAt)
                putLong("expires_${token.regionId}_${token.officeId}_${token.userType}", token.expiresAt)
                apply()
            }
            
            Log.i(TAG, "Token securely saved. Expires in ${token.remainingHours()} hours")
            Log.i(TAG_PHASE1, "✅ TOKEN ENCRYPTED AND SAVED SUCCESSFULLY")
            Log.d(TAG_PHASE1, "Encryption: EncryptedSharedPreferences with AES256")
            Log.d(TAG_PHASE1, "========== PHASE 1: TOKEN SAVE END ==========")
            
            // 이상 탐지: 토큰 사용 패턴 기록
            recordTokenUsage(token)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save token securely", e)
            Log.e(TAG_PHASE1, "❌ TOKEN SAVE FAILED: ${e.message}")
        }
    }
    
    /**
     * 안전하게 토큰 가져오기
     */
    fun getToken(regionId: String, officeId: String, userType: String): SecureToken? {
        return try {
            Log.d(TAG_PHASE1, "========== PHASE 1: TOKEN RETRIEVE START ==========")
            Log.d(TAG_PHASE1, "Requesting token for: $regionId/$officeId/$userType")
            
            val key = "${regionId}_${officeId}_${userType}"
            val token = encryptedPrefs.getString("token_$key", null)
            val channelName = encryptedPrefs.getString("channel_$key", null)
            val generatedAt = encryptedPrefs.getLong("generated_$key", 0)
            val expiresAt = encryptedPrefs.getLong("expires_$key", 0)
            
            Log.d(TAG_PHASE1, "Token found: ${token != null}, Channel: $channelName")
            
            if (token != null && channelName != null && generatedAt > 0) {
                val secureToken = SecureToken(
                    token = token,
                    channelName = channelName,
                    generatedAt = generatedAt,
                    expiresAt = expiresAt,
                    regionId = regionId,
                    officeId = officeId,
                    userType = userType
                )
                
                // 유효성 검사
                if (secureToken.isValid()) {
                    Log.i(TAG, "Valid token retrieved. ${secureToken.remainingHours()} hours remaining")
                    Log.i(TAG_PHASE1, "✅ TOKEN DECRYPTED AND VALIDATED")
                    Log.d(TAG_PHASE1, "Token valid for: ${secureToken.remainingHours()} hours")
                    
                    // 이상 탐지: 비정상적인 토큰 접근 패턴 감지
                    if (detectAnomalousAccess()) {
                        Log.w(TAG, "Anomalous token access detected - forcing refresh")
                        Log.w(TAG_PHASE1, "⚠️ ANOMALY DETECTED - Token access denied")
                        return null
                    }
                    
                    Log.d(TAG_PHASE1, "========== PHASE 1: TOKEN RETRIEVE SUCCESS ==========")
                    return secureToken
                } else {
                    Log.w(TAG, "Token expired or about to expire")
                    Log.w(TAG_PHASE1, "⚠️ TOKEN EXPIRED - Clearing and requesting refresh")
                    clearToken(regionId, officeId, userType)
                }
            } else {
                Log.d(TAG_PHASE1, "❌ NO TOKEN FOUND IN ENCRYPTED STORAGE")
            }
            
            Log.d(TAG_PHASE1, "========== PHASE 1: TOKEN RETRIEVE END (NULL) ==========")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve token", e)
            Log.e(TAG_PHASE1, "❌ TOKEN RETRIEVE FAILED: ${e.message}")
            null
        }
    }
    
    /**
     * 토큰 무효화 (보안 이슈 발생 시)
     */
    fun invalidateToken(regionId: String, officeId: String, userType: String) {
        clearToken(regionId, officeId, userType)
        Log.w(TAG, "Token invalidated for security reasons")
        
        // TODO: Firebase Functions 호출하여 서버 측 토큰도 무효화
        // invalidateTokenOnServer(regionId, officeId, userType)
    }
    
    /**
     * 토큰 삭제
     */
    private fun clearToken(regionId: String, officeId: String, userType: String) {
        val key = "${regionId}_${officeId}_${userType}"
        with(encryptedPrefs.edit()) {
            remove("token_$key")
            remove("channel_$key")
            remove("generated_$key")
            remove("expires_$key")
            apply()
        }
    }
    
    /**
     * 토큰 사용 패턴 기록 (이상 탐지용)
     */
    private fun recordTokenUsage(token: SecureToken) {
        val usageKey = "usage_${token.regionId}_${token.officeId}"
        val lastAccess = encryptedPrefs.getLong("last_access_$usageKey", 0)
        val accessCount = encryptedPrefs.getInt("access_count_$usageKey", 0)
        
        with(encryptedPrefs.edit()) {
            putLong("last_access_$usageKey", System.currentTimeMillis())
            putInt("access_count_$usageKey", accessCount + 1)
            apply()
        }
    }
    
    /**
     * 비정상적인 토큰 접근 감지
     */
    private fun detectAnomalousAccess(): Boolean {
        // 간단한 이상 탐지 로직
        // 실제로는 더 복잡한 패턴 분석 필요
        
        // 예: 1분 내 10회 이상 접근 시 이상으로 판단
        val recentAccessCount = getRecentAccessCount()
        if (recentAccessCount > 10) {
            Log.w(TAG, "Suspicious token access pattern detected: $recentAccessCount accesses in 1 minute")
            return true
        }
        
        return false
    }
    
    private fun getRecentAccessCount(): Int {
        // 최근 1분간 접근 횟수 계산
        // 실제 구현 시 더 정교한 로직 필요
        return 0
    }
    
    /**
     * 모든 토큰 정리 (로그아웃 시)
     */
    fun clearAllTokens() {
        encryptedPrefs.edit().clear().apply()
        Log.i(TAG, "All tokens cleared")
    }
    
    /**
     * 토큰 갱신 필요 여부
     */
    fun needsRefresh(regionId: String, officeId: String, userType: String): Boolean {
        val token = getToken(regionId, officeId, userType)
        return token == null || token.shouldRefresh()
    }
}
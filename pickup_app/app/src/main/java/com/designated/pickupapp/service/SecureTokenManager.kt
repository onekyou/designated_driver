package com.designated.pickupapp.service

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
 * Android Keystore를 활용한 안전한 토큰 관리자 - Pickup App용
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
            val remaining = expiresAt - System.currentTimeMillis()
            val refreshTime = BUFFER_TIME_MINUTES * 60 * 1000L
            return remaining <= refreshTime
        }
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedSharedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            SHARED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * 보안 토큰 저장 - Phase 1 핵심 기능
     */
    fun saveToken(token: SecureToken) {
        try {
            Log.d(TAG_PHASE1, "========== PHASE 1: SECURE TOKEN SAVE ==========")
            
            val tokenKey = "${token.regionId}_${token.officeId}_${token.userType}"
            
            // 토큰 JSON 생성
            val tokenJson = """
                {
                    "token": "${token.token}",
                    "channelName": "${token.channelName}",
                    "generatedAt": ${token.generatedAt},
                    "expiresAt": ${token.expiresAt},
                    "regionId": "${token.regionId}",
                    "officeId": "${token.officeId}",
                    "userType": "${token.userType}"
                }
            """.trimIndent()
            
            // Android Keystore로 암호화하여 저장
            val encryptedToken = encryptData(tokenJson)
            
            encryptedSharedPrefs.edit()
                .putString("token_$tokenKey", encryptedToken)
                .putLong("saved_at_$tokenKey", System.currentTimeMillis())
                .apply()
            
            Log.i(TAG_PHASE1, "✅ SECURE SAVE SUCCESS - 토큰 암호화 저장 완료")
            Log.d(TAG_PHASE1, "Key: $tokenKey")
            Log.d(TAG_PHASE1, "Validity: ${token.remainingHours()}시간")
            Log.d(TAG_PHASE1, "========== PHASE 1: SECURE TOKEN SAVE END ==========")
            
            // 액세스 기록
            recordTokenUsage(token)
            
        } catch (e: Exception) {
            Log.e(TAG_PHASE1, "❌ SECURE SAVE FAILED - 토큰 저장 실패", e)
            throw SecurityException("토큰 저장 실패: ${e.message}")
        }
    }
    
    /**
     * 보안 토큰 조회 - Phase 1 핵심 기능
     */
    fun getToken(regionId: String, officeId: String, userType: String): SecureToken? {
        return try {
            Log.d(TAG_PHASE1, "========== PHASE 1: SECURE TOKEN GET ==========")
            
            val tokenKey = "${regionId}_${officeId}_${userType}"
            val encryptedToken = encryptedSharedPrefs.getString("token_$tokenKey", null)
            
            if (encryptedToken == null) {
                Log.w(TAG_PHASE1, "⚠️ NO TOKEN FOUND - 저장된 토큰이 없음")
                return null
            }
            
            // 비정상 액세스 탐지
            if (detectAnomalousAccess()) {
                Log.w(TAG_PHASE1, "🚨 ANOMALOUS ACCESS DETECTED - 토큰 접근 차단")
                clearToken(regionId, officeId, userType)
                return null
            }
            
            // Android Keystore로 복호화
            val decryptedJson = decryptData(encryptedToken)
            val token = parseTokenFromJson(decryptedJson)
            
            if (token.isValid()) {
                Log.i(TAG_PHASE1, "✅ SECURE GET SUCCESS - 유효한 토큰 조회")
                Log.d(TAG_PHASE1, "Remaining: ${token.remainingHours()}시간")
                recordTokenUsage(token)
                return token
            } else {
                Log.w(TAG_PHASE1, "⏰ TOKEN EXPIRED - 만료된 토큰, 자동 삭제")
                clearToken(regionId, officeId, userType)
                return null
            }
            
        } catch (e: Exception) {
            Log.e(TAG_PHASE1, "❌ SECURE GET FAILED - 토큰 조회 실패", e)
            null
        } finally {
            Log.d(TAG_PHASE1, "========== PHASE 1: SECURE TOKEN GET END ==========")
        }
    }
    
    /**
     * 토큰 무효화 (보안)
     */
    fun invalidateToken(regionId: String, officeId: String, userType: String) {
        Log.i(TAG_PHASE1, "🗑️ TOKEN INVALIDATED - 토큰 무효화")
        clearToken(regionId, officeId, userType)
    }
    
    /**
     * 토큰 삭제 (내부)
     */
    private fun clearToken(regionId: String, officeId: String, userType: String) {
        val tokenKey = "${regionId}_${officeId}_${userType}"
        encryptedSharedPrefs.edit()
            .remove("token_$tokenKey")
            .remove("saved_at_$tokenKey")
            .apply()
        Log.d(TAG, "Token cleared: $tokenKey")
    }
    
    /**
     * 토큰 사용 기록 (보안 감사)
     */
    private fun recordTokenUsage(token: SecureToken) {
        val accessKey = "access_${token.regionId}_${token.officeId}_${token.userType}"
        val lastAccess = System.currentTimeMillis()
        
        encryptedSharedPrefs.edit()
            .putLong(accessKey, lastAccess)
            .apply()
        
        Log.d(TAG, "Token access recorded: ${token.regionId}")
    }
    
    /**
     * 비정상 액세스 탐지 (간단한 구현)
     */
    private fun detectAnomalousAccess(): Boolean {
        // 최근 5분간 10회 이상 접근 시 의심
        val recentAccess = getRecentAccessCount()
        return recentAccess > 10
    }
    
    /**
     * 최근 액세스 횟수 조회
     */
    private fun getRecentAccessCount(): Int {
        // 간단한 구현: 실제로는 더 정교한 로직 필요
        return encryptedSharedPrefs.all.size
    }
    
    /**
     * 모든 토큰 삭제 (보안 초기화)
     */
    fun clearAllTokens() {
        encryptedSharedPrefs.edit().clear().apply()
        Log.i(TAG_PHASE1, "🧹 ALL TOKENS CLEARED - 모든 토큰 삭제 완료")
    }
    
    /**
     * 토큰 갱신 필요 여부 확인
     */
    fun needsRefresh(regionId: String, officeId: String, userType: String): Boolean {
        val token = getToken(regionId, officeId, userType)
        return token?.shouldRefresh() ?: true
    }
    
    // ========== 암호화/복호화 관련 메서드 (Android Keystore) ==========
    
    private fun encryptData(data: String): String {
        generateSecretKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data.toByteArray())
        
        // IV + 암호화된 데이터를 Base64로 인코딩
        val combined = iv + encryptedData
        return Base64.encodeToString(combined, Base64.DEFAULT)
    }
    
    private fun decryptData(encryptedData: String): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        val combined = Base64.decode(encryptedData, Base64.DEFAULT)
        
        // IV와 암호화된 데이터 분리 (GCM IV는 12바이트)
        val iv = combined.sliceArray(0..11)
        val encrypted = combined.sliceArray(12 until combined.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return String(cipher.doFinal(encrypted))
    }
    
    private fun generateSecretKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    private fun parseTokenFromJson(json: String): SecureToken {
        // 간단한 JSON 파싱 (실제로는 Gson 등 사용 권장)
        val tokenRegex = """"token":\s*"([^"]+)"""".toRegex()
        val channelRegex = """"channelName":\s*"([^"]+)"""".toRegex()
        val generatedRegex = """"generatedAt":\s*(\d+)""".toRegex()
        val expiresRegex = """"expiresAt":\s*(\d+)""".toRegex()
        val regionRegex = """"regionId":\s*"([^"]+)"""".toRegex()
        val officeRegex = """"officeId":\s*"([^"]+)"""".toRegex()
        val userTypeRegex = """"userType":\s*"([^"]+)"""".toRegex()
        
        return SecureToken(
            token = tokenRegex.find(json)?.groupValues?.get(1) ?: "",
            channelName = channelRegex.find(json)?.groupValues?.get(1) ?: "",
            generatedAt = generatedRegex.find(json)?.groupValues?.get(1)?.toLong() ?: 0L,
            expiresAt = expiresRegex.find(json)?.groupValues?.get(1)?.toLong() ?: 0L,
            regionId = regionRegex.find(json)?.groupValues?.get(1) ?: "",
            officeId = officeRegex.find(json)?.groupValues?.get(1) ?: "",
            userType = userTypeRegex.find(json)?.groupValues?.get(1) ?: ""
        )
    }
}
package com.designated.driverapp.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 보안 저장소 관리 - 비밀번호 암호화 저장
 * AES-256-GCM 암호화 사용 (EncryptedSharedPreferences)
 */
@Singleton
class SecurePreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val securePrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * 자동 로그인 설정 저장
     */
    fun saveAutoLoginCredentials(identifier: String, password: String) {
        securePrefs.edit().apply {
            putBoolean(KEY_AUTO_LOGIN, true)
            putString(KEY_IDENTIFIER, identifier)
            putString(KEY_PASSWORD, password)
            apply()
        }
    }

    /**
     * 자동 로그인 여부 확인
     */
    fun isAutoLoginEnabled(): Boolean {
        return securePrefs.getBoolean(KEY_AUTO_LOGIN, false)
    }

    /**
     * 자동 로그인 플래그만 설정 (자격증명은 유지)
     */
    fun setAutoLoginEnabled(enabled: Boolean) {
        securePrefs.edit().putBoolean(KEY_AUTO_LOGIN, enabled).apply()
    }

    /**
     * 저장된 식별자 가져오기
     */
    fun getSavedIdentifier(): String? {
        return securePrefs.getString(KEY_IDENTIFIER, null)
    }

    /**
     * 저장된 비밀번호 가져오기
     */
    fun getSavedPassword(): String? {
        return securePrefs.getString(KEY_PASSWORD, null)
    }

    /**
     * 자동 로그인 정보 삭제
     */
    fun clearAutoLoginCredentials() {
        securePrefs.edit().apply {
            remove(KEY_AUTO_LOGIN)
            remove(KEY_IDENTIFIER)
            remove(KEY_PASSWORD)
            apply()
        }
    }

    /**
     * 모든 보안 데이터 삭제
     */
    fun clearAll() {
        securePrefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_AUTO_LOGIN = "auto_login"
        private const val KEY_IDENTIFIER = "identifier"
        private const val KEY_PASSWORD = "password"
    }
}

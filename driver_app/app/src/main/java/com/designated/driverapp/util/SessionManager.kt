package com.designated.driverapp.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.designated.driverapp.data.model.UserSession
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 세션 관리자 - 오프라인 지원
 * - 로그인 상태를 로컬에 캐싱
 * - 네트워크 오류 시 캐시된 세션으로 앱 사용 가능
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth
) {
    private val TAG = "SessionManager"

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "driver_session_cache",
        Context.MODE_PRIVATE
    )

    private val _currentSession = MutableStateFlow<UserSession?>(null)
    val currentSession: StateFlow<UserSession?> = _currentSession.asStateFlow()

    init {
        // 앱 시작 시 캐시된 세션 복원
        restoreSessionFromCache()
    }

    /**
     * 세션 저장 (로그인 성공 시 호출)
     */
    fun saveSession(session: UserSession) {
        try {
            // SharedPreferences에 JSON 형태로 저장
            val json = JSONObject(session.toMap()).toString()
            prefs.edit().putString(KEY_SESSION, json).apply()

            _currentSession.value = session
            Log.d(TAG, "세션 저장 완료: ${session.email}")
        } catch (e: Exception) {
            Log.e(TAG, "세션 저장 실패", e)
        }
    }

    /**
     * 캐시된 세션 복원
     */
    private fun restoreSessionFromCache() {
        try {
            val json = prefs.getString(KEY_SESSION, null) ?: return

            val map = mutableMapOf<String, Any>()
            val jsonObject = JSONObject(json)
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.get(key)
            }

            val session = UserSession.fromMap(map)
            if (session != null && !session.isExpired()) {
                _currentSession.value = session
                Log.d(TAG, "캐시된 세션 복원: ${session.email}")
            } else {
                clearSession()
                Log.w(TAG, "세션 만료됨 또는 유효하지 않음")
            }
        } catch (e: Exception) {
            Log.e(TAG, "세션 복원 실패", e)
            clearSession()
        }
    }

    /**
     * 세션 삭제 (로그아웃 시 호출)
     */
    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
        _currentSession.value = null
        Log.d(TAG, "세션 삭제됨")
    }

    /**
     * 현재 세션이 유효한지 확인
     */
    fun isSessionValid(): Boolean {
        val session = _currentSession.value
        return session != null && !session.isExpired()
    }

    /**
     * 오프라인 모드에서도 로그인 가능한지 확인
     * - 캐시된 세션이 있고 만료되지 않았으면 오프라인 로그인 허용
     */
    fun canLoginOffline(): Boolean {
        return isSessionValid()
    }

    /**
     * 세션 업데이트 (FCM 토큰 등)
     */
    fun updateSession(updater: (UserSession) -> UserSession) {
        _currentSession.value?.let { current ->
            val updated = updater(current)
            saveSession(updated)
        }
    }

    /**
     * 온라인 상태 업데이트
     */
    fun setOnlineStatus(isOnline: Boolean) {
        updateSession { it.copy(isOnline = isOnline) }
    }

    companion object {
        private const val KEY_SESSION = "user_session"
    }
}

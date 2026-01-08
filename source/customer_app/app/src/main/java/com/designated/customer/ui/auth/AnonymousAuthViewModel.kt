package com.designated.customer.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class AnonymousAuthState(
    val isLoading: Boolean = false,
    val userId: String? = null,
    val error: String? = null,
    val isAuthenticated: Boolean = false
)

class AnonymousAuthViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val TAG = "AnonymousAuth"

    private val _uiState = MutableStateFlow(AnonymousAuthState())
    val uiState: StateFlow<AnonymousAuthState> = _uiState.asStateFlow()

    init {
        // 현재 로그인 상태 확인
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Already signed in: ${currentUser.uid}")
            _uiState.value = AnonymousAuthState(
                userId = currentUser.uid,
                isAuthenticated = true
            )
        }
    }

    /**
     * 익명 인증 수행
     * 이미 로그인되어 있으면 현재 userId 반환
     */
    fun signInAnonymously() {
        // 이미 로그인되어 있으면 바로 반환
        val currentUser = auth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "Already authenticated: ${currentUser.uid}")
            _uiState.value = AnonymousAuthState(
                userId = currentUser.uid,
                isAuthenticated = true
            )
            return
        }

        // 새로운 익명 인증 수행
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                val result = auth.signInAnonymously().await()
                val userId = result.user?.uid

                if (userId != null) {
                    Log.d(TAG, "Anonymous sign-in successful: $userId")
                    _uiState.value = AnonymousAuthState(
                        userId = userId,
                        isAuthenticated = true,
                        isLoading = false
                    )
                } else {
                    Log.e(TAG, "Anonymous sign-in failed: userId is null")
                    _uiState.value = AnonymousAuthState(
                        error = "인증 실패: 사용자 ID를 가져올 수 없습니다",
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Anonymous sign-in error", e)
                _uiState.value = AnonymousAuthState(
                    error = "인증 실패: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    /**
     * 현재 사용자 ID 가져오기
     */
    fun getCurrentUserId(): String? {
        return auth.currentUser?.uid
    }

    /**
     * 로그아웃 (경고: 데이터 손실 가능)
     */
    fun signOut() {
        auth.signOut()
        _uiState.value = AnonymousAuthState()
        Log.d(TAG, "Signed out")
    }

    /**
     * 에러 메시지 클리어
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

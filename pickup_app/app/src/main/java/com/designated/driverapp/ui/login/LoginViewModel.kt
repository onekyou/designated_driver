package com.designated.driverapp.ui.login

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// LoginState sealed class remains the same
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "LoginViewModel"

    private val auth: FirebaseAuth = Firebase.auth
    // Use a different preference file name for the driver app
    private val sharedPreferences = application.getSharedPreferences("driver_login_prefs", Context.MODE_PRIVATE)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    var email by mutableStateOf("") // Consider renaming to emailOrPhone if applicable
    var password by mutableStateOf("")
    var autoLogin by mutableStateOf(false)

    init {
        Log.d(TAG, "초기화: 현재 로그인 상태 확인 중...")
        
        // 저장된 자동 로그인 설정 불러오기
        autoLogin = sharedPreferences.getBoolean("auto_login", false)
        Log.d(TAG, "자동 로그인 설정: $autoLogin")
        
        // 현재 로그인된 사용자 확인
        val currentUser = auth.currentUser
        Log.d(TAG, "현재 사용자: ${currentUser?.email ?: "없음"}")
        
        if (currentUser != null) {
            // 이미 로그인된 사용자가 있으면 성공 상태로 설정
            Log.d(TAG, "이미 로그인된 사용자가 있습니다: ${currentUser.email}")
            _loginState.value = LoginState.Success
        } else if (autoLogin) {
            // 자동 로그인이 활성화되어 있고 로그인된 사용자가 없는 경우
            val savedIdentifier = sharedPreferences.getString("identifier", "") // Use a generic name
            val savedPassword = sharedPreferences.getString("password", "")
            
            Log.d(TAG, "저장된 로그인 정보: 이메일=${savedIdentifier?.takeIf { it.isNotEmpty() }?.let { "${it.take(3)}..." } ?: "없음"}, 비밀번호=${if (savedPassword?.isNotEmpty() == true) "있음" else "없음"}")
            
            if (!savedIdentifier.isNullOrBlank() && !savedPassword.isNullOrBlank()) {
                // 저장된 정보로 자동 로그인 시도
                Log.d(TAG, "자동 로그인 시도 중...")
                email = savedIdentifier 
                password = savedPassword
                login() 
            } else {
                Log.d(TAG, "자동 로그인 설정이 활성화되어 있지만 저장된 로그인 정보가 없습니다")
            }
        } else {
            Log.d(TAG, "자동 로그인이 비활성화되어 있습니다")
        }
    }

    fun login() {
        Log.d(TAG, "로그인 시도: 이메일=${email.takeIf { it.isNotEmpty() }?.let { "${it.take(3)}..." } ?: ""}")
        
        // Add validation logic if allowing phone numbers
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("이메일(또는 전화번호)과 비밀번호를 모두 입력해주세요.")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                // Needs adjustment if phone number login is implemented
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d(TAG, "로그인 성공: ${auth.currentUser?.email}")
                            
                            if (autoLogin) {
                                // 자동 로그인 정보 저장
                                Log.d(TAG, "자동 로그인 정보 저장 중...")
                                sharedPreferences.edit().apply {
                                    putBoolean("auto_login", true)
                                    putString("identifier", email) // Save identifier
                                    putString("password", password) 
                                    apply()
                                }
                                
                                // 저장이 완료되었는지 확인
                                val saved = sharedPreferences.getBoolean("auto_login", false)
                                Log.d(TAG, "자동 로그인 정보 저장 확인: $saved")
                            } else {
                                // 자동 로그인 정보 제거
                                Log.d(TAG, "자동 로그인 정보 제거 중...")
                                sharedPreferences.edit().apply {
                                    putBoolean("auto_login", false)
                                    remove("identifier")
                                    remove("password")
                                    apply()
                                }
                            }
                            _loginState.value = LoginState.Success
                        } else {
                            Log.e(TAG, "로그인 실패: ${task.exception?.message}")
                            _loginState.value = LoginState.Error(task.exception?.message ?: "로그인에 실패했습니다.")
                        }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "로그인 중 오류 발생", e)
                _loginState.value = LoginState.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    fun toggleAutoLogin(enabled: Boolean) {
        Log.d(TAG, "자동 로그인 설정 변경: $enabled")
        autoLogin = enabled
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LoginViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 
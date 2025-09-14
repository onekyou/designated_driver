package com.example.calldetector.ui.login

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
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
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val regionId: String, val officeId: String) : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore
    private val sharedPreferences = application.getSharedPreferences("call_detector_login_prefs", Context.MODE_PRIVATE)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    var email by mutableStateOf("")
    var password by mutableStateOf("")

    var autoLogin by mutableStateOf(false)

    init {
        val autoLoginFlag = sharedPreferences.getBoolean("auto_login", false)
        autoLogin = autoLoginFlag

        if (autoLogin) {
            val savedEmail = sharedPreferences.getString("email", "")
            val savedPassword = sharedPreferences.getString("password", "")

            if (!savedEmail.isNullOrBlank() && !savedPassword.isNullOrBlank()) {
                email = savedEmail
                password = savedPassword
                login()
            }
        }
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("이메일과 비밀번호를 모두 입력해주세요.")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val authResult = auth.signInWithEmailAndPassword(email, password).await()
                val user = authResult.user
                if (user != null) {
                    fetchAdminInfoAndProceed(user.uid)
                } else {
                    _loginState.value = LoginState.Error("로그인에 실패했습니다. 사용자 정보를 가져올 수 없습니다.")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "로그인 중 오류가 발생했습니다.")
            }
        }
    }

    private fun fetchAdminInfoAndProceed(uid: String) {
        viewModelScope.launch {
            try {
                val adminDoc = db.collection("admins").document(uid).get().await()

                if (adminDoc.exists()) {
                    val regionId = adminDoc.getString("associatedRegionId")
                    val officeId = adminDoc.getString("associatedOfficeId")

                    if (regionId != null && officeId != null) {
                        if (autoLogin) {
                            saveLoginInfo()
                        } else {
                            clearLoginInfo()
                        }

                        _loginState.value = LoginState.Success(regionId, officeId)
                    } else {
                        _loginState.value = LoginState.Error("관리자 정보에 지역 또는 사무실 정보가 없습니다.")
                    }
                } else {
                    _loginState.value = LoginState.Error("관리자 권한이 없는 계정입니다.")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("관리자 정보 조회 중 오류가 발생했습니다: ${e.message}")
            }
        }
    }

    fun toggleAutoLogin(enabled: Boolean) {
        autoLogin = enabled
        sharedPreferences.edit {
            putBoolean("auto_login", enabled)
        }

        if (!enabled) {
            clearLoginInfo()
        }
    }

    private fun saveLoginInfo() {
        sharedPreferences.edit {
            putString("email", email)
            putString("password", password)
        }
    }

    private fun clearLoginInfo() {
        sharedPreferences.edit {
            remove("email")
            remove("password")
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
                return LoginViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
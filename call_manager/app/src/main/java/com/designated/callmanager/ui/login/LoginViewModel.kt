package com.designated.callmanager.ui.login

import android.app.Application
import android.content.Context
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
    private val sharedPreferences = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

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
            } else {

            }
        } else {

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
                    if (!regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
                        if (autoLogin) {
                            val autoLoginEditor = sharedPreferences.edit()
                            autoLoginEditor.putString("email", email)
                            autoLoginEditor.putString("password", password)
                            autoLoginEditor.putBoolean("auto_login", true)
                            val prefsEditSuccess = autoLoginEditor.commit()

                            if (prefsEditSuccess) {
                            } else {
                            }
                        } else {
                            val editor = sharedPreferences.edit()
                            editor.remove("email")
                            editor.remove("password")
                            editor.putBoolean("auto_login", false)
                            editor.apply()
                        }

                        val regionOfficeEditor = sharedPreferences.edit()
                        regionOfficeEditor.putString("regionId", regionId)
                        regionOfficeEditor.putString("officeId", officeId)
                        val success = regionOfficeEditor.commit()

                        if (success) {
                            _loginState.value = LoginState.Success(regionId, officeId)
                        } else {
                            _loginState.value = LoginState.Error("로그인 정보 저장 실패")
                            auth.signOut()
                        }
                    } else {
                        _loginState.value = LoginState.Error("관리자 정보(지역/사무실 ID)가 올바르지 않습니다.")
                    auth.signOut()
                    }
                } else {
                    _loginState.value = LoginState.Error("등록되지 않은 관리자 계정입니다.")
                    auth.signOut()
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("관리자 정보 조회 중 오류 발생: ${e.message}")
                auth.signOut()
            }
        }
    }

    fun toggleAutoLogin(enabled: Boolean) {
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
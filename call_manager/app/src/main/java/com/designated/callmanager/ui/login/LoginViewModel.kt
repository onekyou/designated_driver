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
import android.util.Log

// 로그인 상태를 나타내는 sealed class
sealed class LoginState {
    object Idle : LoginState() // 초기 상태
    object Loading : LoginState() // 로그인 시도 중
    data class Success(val regionId: String, val officeId: String) : LoginState() // regionId, officeId 포함
    data class Error(val message: String) : LoginState() // 로그인 실패
}

// AndroidViewModel로 변경하여 Context에 접근할 수 있도록 함
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore
    private val sharedPreferences = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

    // 로그인 상태를 UI에 노출하기 위한 StateFlow
    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    // UI에서 입력받는 이메일과 비밀번호 (Compose의 State로 관리)
    var email by mutableStateOf("")
    var password by mutableStateOf("")

    // 자동 로그인 설정 상태
    var autoLogin by mutableStateOf(false)

    init {
        val autoLoginFlag = sharedPreferences.getBoolean("auto_login", false)

        autoLogin = autoLoginFlag // Use the read value

        // 자동 로그인이 설정되어 있고, 저장된 이메일/비번이 있을 때만 로그인 시도
        if (autoLogin) {
            val savedEmail = sharedPreferences.getString("email", "")
            val savedPassword = sharedPreferences.getString("password", "")

            if (!savedEmail.isNullOrBlank() && !savedPassword.isNullOrBlank()) {
                email = savedEmail
                password = savedPassword
                login() // 자동 로그인 시도
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
                    // Firestore에서 관리자 정보 조회
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
        Log.d("LoginViewModel", "Fetching admin info for UID: $uid") // UID 확인 로그
        viewModelScope.launch {
            try {
                Log.d("LoginViewModel", "  Attempting to read Firestore document: /admins/$uid")
                val adminDoc = db.collection("admins").document(uid).get().await()
                Log.d("LoginViewModel", "  Firestore document read successful. Document exists: ${adminDoc.exists()}")

                if (adminDoc.exists()) {
                    val regionId = adminDoc.getString("associatedRegionId")
                    val officeId = adminDoc.getString("associatedOfficeId")
                    Log.d("LoginViewModel", "  Read from Firestore - Region ID: $regionId, Office ID: $officeId")
                    if (!regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
                        // 로그인 성공 시 자동 로그인 정보 저장
                        if (autoLogin) { // 자동 로그인 체크 시에만 저장
                            val autoLoginEditor = sharedPreferences.edit()
                            autoLoginEditor.putString("email", email)
                            autoLoginEditor.putString("password", password)
                            autoLoginEditor.putBoolean("auto_login", true)
                            val prefsEditSuccess = autoLoginEditor.commit() // 표준 commit 사용

                            if (prefsEditSuccess) {
                                Log.i("LoginViewModel", "Auto-login credentials saved.")
                            } else {
                                Log.e("LoginViewModel", "Failed to save auto-login credentials.")
                            }
                        } else {
                            // 자동 로그인 체크 안했을 시 기존 정보 삭제
                            val editor = sharedPreferences.edit()
                            editor.remove("email")
                            editor.remove("password")
                            editor.putBoolean("auto_login", false)
                            editor.apply()
                        }

                        // SharedPreferences에 regionId/officeId 저장
                        val regionOfficeEditor = sharedPreferences.edit()
                        regionOfficeEditor.putString("regionId", regionId)
                        regionOfficeEditor.putString("officeId", officeId)
                        Log.d("LoginViewModel", "  Attempting to save region/office ID to SharedPreferences...")
                        val success = regionOfficeEditor.commit() // 표준 commit 사용
                        Log.d("LoginViewModel", "  SharedPreferences save result: $success")

                        if (success) {
                            Log.i("LoginViewModel", "  Login process successful. Updating state to Success with regionId=$regionId, officeId=$officeId")
                            _loginState.value = LoginState.Success(regionId, officeId) // Success 상태에 ID 전달
                        } else {
                            Log.e("LoginViewModel", "Failed to save region/office ID to SharedPreferences using commit().")
                            _loginState.value = LoginState.Error("로그인 정보 저장 실패")
                            auth.signOut()
                        }
                    } else {
                        Log.e("LoginViewModel", "Error: regionId or officeId is null or blank after reading from Firestore.")
                        _loginState.value = LoginState.Error("관리자 정보(지역/사무실 ID)가 올바르지 않습니다.")
                        auth.signOut()
                    }
                } else {
                    Log.e("LoginViewModel", "Error: Admin document does not exist for UID: $uid")
                    _loginState.value = LoginState.Error("등록되지 않은 관리자 계정입니다.")
                    auth.signOut()
                }
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Error fetching admin info from Firestore for UID: $uid", e)
                _loginState.value = LoginState.Error("관리자 정보 조회 중 오류 발생: ${e.message}")
                auth.signOut()
            }
        }
    }

    // 자동 로그인 설정 변경 함수
    fun toggleAutoLogin(enabled: Boolean) {
        autoLogin = enabled
        // 설정만 변경하고 로그인 상태는 바꾸지 않음 (로그인 성공 시 실제로 저장됨)
    }

    // 로그인 상태 초기화 (예: 오류 메시지 확인 후)
    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    // Factory 클래스 - AndroidViewModel에 필요한 Application 인스턴스 전달
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
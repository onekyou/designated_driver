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

    private suspend fun ensureAdminDoc(uid: String): Pair<String, String>? {
        var region = sharedPreferences.getString("regionId", null)
        var office = sharedPreferences.getString("officeId", null)

        // prefs 가 비어있다면 admins 문서를 먼저 조회해 fallback 시도
        if (region.isNullOrBlank() || office.isNullOrBlank()) {
            val adminSnap = db.collection("admins").document(uid).get().await()
            if (adminSnap.exists()) {
                region = adminSnap.getString("associatedRegionId")
                office  = adminSnap.getString("associatedOfficeId")
                if (!region.isNullOrBlank() && !office.isNullOrBlank()) {
                    // prefs 갱신
                    sharedPreferences.edit()
                        .putString("regionId", region)
                        .putString("officeId", office)
                        .apply()
                    Log.i("LoginViewModel", "✅ prefs 복구: $region/$office from admin doc")
                }
            }
        }

        if (region.isNullOrBlank() || office.isNullOrBlank()) {
            Log.e("LoginViewModel", "❌ region/office 정보가 없습니다. (prefs & admin)")
            return null
        }
        val prefRegion = region; val prefOffice = office

        val adminRef = db.collection("admins").document(uid)
        val snapshot = adminRef.get().await()

        val needCreate = !snapshot.exists()
        val needUpdate = snapshot.exists() && (
            snapshot.getString("associatedRegionId") != region ||
            snapshot.getString("associatedOfficeId") != office
        )

        if (needCreate || needUpdate) {
            val data = hashMapOf(
                "associatedRegionId" to region,
                "associatedOfficeId" to office,
                "updatedAt" to com.google.firebase.Timestamp.now()
            )
            if (needCreate) data["createdAt"] = com.google.firebase.Timestamp.now()
            adminRef.set(data).await()
            Log.i("LoginViewModel", "✅ admin 문서 ${if (needCreate) "created" else "updated"} for $uid -> $region/$office")
        }
        return Pair(region!!, office!!)
    }

    private fun fetchAdminInfoAndProceed(uid: String) {
        Log.d("LoginViewModel", "Fetching admin info for UID: $uid")
        viewModelScope.launch {
            try {
                // 1) admin 문서 확인 및 필요시 생성/업데이트
                val regionOffice = ensureAdminDoc(uid)
                if (regionOffice == null) {
                    _loginState.value = LoginState.Error("앱 설정에서 지역/사무실을 먼저 선택한 후 다시 로그인해주세요.")
                    auth.signOut()
                    return@launch
                }
                val (regionId, officeId) = regionOffice

                // 2) 자동 로그인 정보 저장/삭제 (기존 로직 유지)
                if (autoLogin) {
                    sharedPreferences.edit().apply {
                        putString("email", email)
                        putString("password", password)
                        putBoolean("auto_login", true)
                    }.commit()
                } else {
                    sharedPreferences.edit().apply {
                        remove("email"); remove("password"); putBoolean("auto_login", false)
                    }.apply()
                }

                // 3) region/office prefs 보증 (이미 있으나 한번 더 저장)
                sharedPreferences.edit().apply {
                    putString("regionId", regionId)
                    putString("officeId", officeId)
                }.commit()

                _loginState.value = LoginState.Success(regionId, officeId)
            } catch (e: Exception) {
                Log.e("LoginViewModel", "Error ensuring admin doc", e)
                _loginState.value = LoginState.Error("관리자 정보 설정 중 오류: ${e.message}")
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
package com.designated.callmanager.ui.signup

import android.app.Application
import android.content.Context
import android.util.Patterns
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
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 사장님 가입 ViewModel — H2 설계 (2026-04-20)
 *
 * 입력: 이메일 / 비밀번호 / 비밀번호 확인 / 초대 토큰
 *
 * 처리 순서:
 *   1) 입력값 검증
 *   2) CF registerOwner(token, email, password) 호출
 *      - 서버에서 Firebase Auth 계정 생성 + admins/office/토큰 상태 일괄 처리
 *   3) signInWithEmailAndPassword 로 자동 로그인
 *
 * 이전 설계(2026-04-19 이전)와 차이:
 *   - 앱에서 직접 admins/office 생성하던 로직 전체 제거
 *   - 사무실명/은행/계좌 필드는 초대 토큰에서 가져오거나 사후 설정으로 분리
 */

sealed class SignUpState {
    object Idle : SignUpState()
    object Loading : SignUpState()
    object Success : SignUpState()
    data class Error(val message: String) : SignUpState()
}

class SignUpViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = Firebase.auth
    private val functions: FirebaseFunctions = Firebase.functions("asia-northeast3")
    // LoginViewModel 와 동일한 SharedPrefs 키 사용 — 가입 성공 시 자동로그인 플래그를 켜두면
    // MainActivity 가 Screen.Login 으로 복귀하더라도 LoginViewModel init 이 자격증명으로 바로 진입 가능
    private val loginPrefs = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var inviteToken by mutableStateOf("")

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    fun signUp() {
        val trimmedEmail = email.trim().lowercase()
        val trimmedToken = inviteToken.trim()

        if (trimmedEmail.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
            _signUpState.value = SignUpState.Error("올바른 이메일 주소를 입력해주세요.")
            return
        }
        if (password.length < 6) {
            _signUpState.value = SignUpState.Error("비밀번호는 6자 이상 입력해주세요.")
            return
        }
        if (password != confirmPassword) {
            _signUpState.value = SignUpState.Error("비밀번호가 일치하지 않습니다.")
            return
        }
        if (trimmedToken.isBlank()) {
            _signUpState.value = SignUpState.Error("초대 토큰을 입력해주세요.")
            return
        }

        _signUpState.value = SignUpState.Loading
        viewModelScope.launch {
            try {
                // 1) 서버 CF 호출 — 계정 생성 + admins + office 연결
                val payload = mapOf(
                    "token" to trimmedToken,
                    "email" to trimmedEmail,
                    "password" to password
                )
                functions.getHttpsCallable("registerOwner")
                    .call(payload)
                    .await()

                // 2) 자동 로그인 (기존 signInWithEmailAndPassword 코드가 이후 흐름 담당)
                auth.signInWithEmailAndPassword(trimmedEmail, password).await()

                // 3) 자동로그인 플래그 + 자격증명을 SharedPrefs 에 저장
                //    MainActivity 가 Screen.Login 으로 되돌려도 LoginViewModel init 이 자동 진입하도록
                loginPrefs.edit {
                    putString("email", trimmedEmail)
                    putString("password", password)
                    putBoolean("auto_login", true)
                }

                _signUpState.value = SignUpState.Success
            } catch (e: FirebaseFunctionsException) {
                val friendly = when (e.code) {
                    FirebaseFunctionsException.Code.UNAUTHENTICATED,
                    FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                        "권한이 없습니다. 관리자에게 문의하세요."
                    FirebaseFunctionsException.Code.NOT_FOUND,
                    FirebaseFunctionsException.Code.INVALID_ARGUMENT,
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION ->
                        e.message ?: "초대 토큰이 유효하지 않습니다."
                    FirebaseFunctionsException.Code.ALREADY_EXISTS ->
                        "이미 사용 중인 이메일입니다. 다른 이메일을 사용하거나 로그인해주세요."
                    FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
                    FirebaseFunctionsException.Code.UNAVAILABLE ->
                        "서버 응답이 지연되고 있습니다. 잠시 후 다시 시도해주세요."
                    else ->
                        e.message ?: "회원가입 중 오류가 발생했습니다."
                }
                _signUpState.value = SignUpState.Error(friendly)
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error(e.message ?: "회원가입 중 오류가 발생했습니다.")
            }
        }
    }

    fun resetSignUpState() {
        _signUpState.value = SignUpState.Idle
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SignUpViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SignUpViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

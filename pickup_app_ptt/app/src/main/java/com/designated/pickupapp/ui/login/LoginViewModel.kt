package com.designated.pickupapp.ui.login

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupapp.data.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val firebaseMessaging: FirebaseMessaging,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var autoLogin by mutableStateOf(false)

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    init {
        // Firebase Auth 지속성 설정 (앱 재시작 시에도 로그인 상태 유지)
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().useAppLanguage()
            Log.i("LoginViewModel", "Firebase Auth 지속성 설정 완료")
        } catch (e: Exception) {
            Log.e("LoginViewModel", "Firebase Auth 설정 실패", e)
        }
        
        loadSavedCredentials()
        
        // 현재 Firebase 사용자 확인 (Auth 리스너 대신 직접 확인)
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null) {
            // 이미 로그인된 상태
            if (autoLogin && email.isNotBlank() && password.isNotBlank()) {
                Log.i("LoginViewModel", "Firebase 사용자 이미 인증됨 - 자동 로그인 진행: ${currentUser.uid}")
                checkPendingStatusAndProceed(currentUser.uid)
            } else {
                Log.i("LoginViewModel", "로그인 상태지만 자동 로그인 정보 없음")
            }
        } else {
            // 로그인 필요
            if (autoLogin && email.isNotBlank() && password.isNotBlank()) {
                Log.i("LoginViewModel", "Firebase 사용자 미인증 - 로그인 시도")
                login()
            } else {
                Log.i("LoginViewModel", "자동 로그인 비활성화 또는 정보 부족 - 수동 로그인 대기")
            }
        }
    }

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("이메일과 비밀번호를 입력해주세요.")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                val uid = authResult.user?.uid ?: throw Exception("인증 실패")

                // 기사앱과 동일한 패턴으로 pending 상태 먼저 확인
                checkPendingStatusAndProceed(uid)
                
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("user-not-found") == true -> "등록되지 않은 이메일입니다."
                    e.message?.contains("wrong-password") == true -> "비밀번호가 틀렸습니다."
                    e.message?.contains("invalid-email") == true -> "유효하지 않은 이메일 형식입니다."
                    else -> "로그인 실패: ${e.message}"
                }
                _loginState.value = LoginState.Error(errorMessage)
            }
        }
    }

    // 기사앱과 동일한 패턴의 승인 상태 확인
    private fun checkPendingStatusAndProceed(userId: String) {
        val pendingDocRef = firestore.collection("pending_drivers").document(userId)

        viewModelScope.launch {
            try {
                val pendingDoc = pendingDocRef.get().await()
                if (pendingDoc.exists()) {
                    // 아직 승인 대기 중
                    firebaseAuth.signOut()
                    _loginState.value = LoginState.Error("관리자 승인 대기 중인 계정입니다.")
                } else {
                    // 승인 완료 - pickup_drivers에서 찾기
                    findPickupDriverAndLogin(userId)
                }
            } catch (e: Exception) {
                firebaseAuth.signOut()
                _loginState.value = LoginState.Error("로그인 처리 중 오류: ${e.message}")
            }
        }
    }

    // pickup_drivers 컬렉션에서 픽업 기사 정보 찾기
    private fun findPickupDriverAndLogin(userId: String) {
        viewModelScope.launch {
            try {
                val pickupDriversSnapshot = firestore.collectionGroup("pickup_drivers")
                    .whereEqualTo("authUid", userId)
                    .limit(1)
                    .get()
                    .await()

                if (pickupDriversSnapshot.isEmpty) {
                    firebaseAuth.signOut()
                    _loginState.value = LoginState.Error("등록되지 않은 계정입니다.")
                    return@launch
                }

                val driverDoc = pickupDriversSnapshot.documents.first()
                val regionId = driverDoc.reference.parent.parent?.parent?.parent?.id 
                    ?: throw Exception("지역 정보를 찾을 수 없습니다.")
                // associatedOfficeId 필드에서 office ID 가져오기 (경로 추출 대신)
                val officeId = driverDoc.getString("associatedOfficeId")
                    ?: throw Exception("associatedOfficeId 필드가 설정되지 않았습니다. 관리자에게 문의하세요.")

                // FCM 토큰 업데이트
                val currentToken = firebaseMessaging.token.await()
                val savedToken = driverDoc.getString("fcmToken")
                val needsTokenUpdate = savedToken != currentToken

                if (needsTokenUpdate) {
                    driverDoc.reference.update("fcmToken", currentToken).await()
                }

                if (autoLogin) {
                    saveCredentials()
                }

                // region/office 정보 저장
                saveRegionOfficeInfo(regionId, officeId)

                // 성공한 로그인 정보를 SharedPreferences에 저장
                saveDriverInfo(regionId, officeId, userId)
                
                _loginState.value = LoginState.Success(
                    regionId = regionId,
                    officeId = officeId,
                    driverId = userId,
                    needsTokenUpdate = needsTokenUpdate
                )
                
            } catch (e: Exception) {
                firebaseAuth.signOut()
                _loginState.value = LoginState.Error("로그인 실패: ${e.message}")
            }
        }
    }

    fun toggleAutoLogin(enabled: Boolean) {
        autoLogin = enabled
        if (enabled) {
            saveCredentials()
        } else {
            clearSavedCredentials()
        }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    private fun saveCredentials() {
        sharedPreferences.edit().apply {
            putString(Constants.PREF_EMAIL, email)
            putString(Constants.PREF_PASSWORD, password)
            putBoolean(Constants.PREF_AUTO_LOGIN, autoLogin)
            apply()
        }
    }

    private fun loadSavedCredentials() {
        email = sharedPreferences.getString(Constants.PREF_EMAIL, "") ?: ""
        password = sharedPreferences.getString(Constants.PREF_PASSWORD, "") ?: ""
        autoLogin = sharedPreferences.getBoolean(Constants.PREF_AUTO_LOGIN, false)
    }

    private fun clearSavedCredentials() {
        sharedPreferences.edit().apply {
            remove(Constants.PREF_EMAIL)
            remove(Constants.PREF_PASSWORD)
            remove(Constants.PREF_AUTO_LOGIN)
            apply()
        }
    }

    private fun saveRegionOfficeInfo(regionId: String, officeId: String) {
        // Constants.PREFS_NAME ("pickup_driver_prefs")에 저장 - MainActivity와 동일한 SharedPreferences 사용
        sharedPreferences.edit().apply {
            putString(Constants.PREF_KEY_REGION_ID, regionId)
            putString(Constants.PREF_KEY_OFFICE_ID, officeId)
            apply()
        }
    }
    
    private fun saveDriverInfo(regionId: String, officeId: String, driverId: String) {
        // 로그인 성공 시 모든 필요한 정보 저장
        sharedPreferences.edit().apply {
            putString(Constants.PREF_KEY_REGION_ID, regionId)
            putString(Constants.PREF_KEY_OFFICE_ID, officeId)
            putString(Constants.PREF_KEY_DRIVER_ID, driverId)
            putBoolean(Constants.PREF_AUTO_LOGIN, true) // 자동 로그인 활성화
            apply()
        }
        Log.i("LoginViewModel", "✅ 드라이버 정보 저장 완료 - Region: $regionId, Office: $officeId, Driver: $driverId")
    }
}
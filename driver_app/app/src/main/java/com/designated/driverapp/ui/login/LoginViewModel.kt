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
import com.google.firebase.firestore.FirebaseFirestore
import com.designated.driverapp.model.DriverStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.content.SharedPreferences
import com.google.firebase.messaging.FirebaseMessaging
import com.designated.driverapp.data.Constants
import com.designated.driverapp.util.SecurePreferencesManager
import com.designated.driverapp.util.SessionManager
import com.designated.driverapp.data.model.UserSession

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val provinceId: String, val cityId: String, val officeId: String, val driverId: String, val needsTokenUpdate: Boolean) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sharedPreferences: SharedPreferences,
    private val securePreferences: SecurePreferencesManager,
    private val sessionManager: SessionManager
) : ViewModel() {
    private val TAG = "LoginViewModel"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var autoLogin by mutableStateOf(false)

    init {
        // 암호화된 저장소에서 자동 로그인 정보 로드
        autoLogin = securePreferences.isAutoLoginEnabled()

        if (autoLogin) {
            val savedIdentifier = securePreferences.getSavedIdentifier()
            val savedPassword = securePreferences.getSavedPassword()
            if (!savedIdentifier.isNullOrBlank()) {
                email = savedIdentifier
                password = savedPassword ?: ""
            }
        }
    }

    fun login() {

        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("이메일(또는 전화번호)과 비밀번호를 모두 입력해주세요.")
            return
        }

        _loginState.value = LoginState.Loading
        viewModelScope.launch {
            try {
                // 1. Firebase 온라인 로그인 시도
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val userId = auth.currentUser?.uid

                            if (userId != null) {
                                checkPendingStatusAndProceed(userId)
                            } else {
                                _loginState.value = LoginState.Error("로그인 처리 중 오류가 발생했습니다. (UID 누락)")
                            }
                        } else {
                            // 2. 온라인 로그인 실패 시 오프라인 로그인 시도
                            val exception = task.exception
                            Log.e(TAG, "❌ 온라인 로그인 실패", exception)
                            Log.e(TAG, "❌ 에러 메시지: ${exception?.message}")
                            Log.d(TAG, "🔍 네트워크 에러 여부: ${isNetworkError(exception)}")
                            Log.d(TAG, "🔍 오프라인 로그인 가능 여부: ${sessionManager.canLoginOffline()}")

                            if (isNetworkError(exception) && sessionManager.canLoginOffline()) {
                                Log.d(TAG, "⚠️ 네트워크 에러로 오프라인 로그인 시도")
                                attemptOfflineLogin()
                            } else {
                                Log.e(TAG, "❌ 온라인 로그인 실패 - 오프라인 로그인 불가")
                                _loginState.value = LoginState.Error(exception?.message ?: "로그인에 실패했습니다.")
                            }
                        }
                    }
            } catch (e: Exception) {
                // 3. 예외 발생 시 오프라인 로그인 시도
                if (sessionManager.canLoginOffline()) {
                    attemptOfflineLogin()
                } else {
                    _loginState.value = LoginState.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
                }
            }
        }
    }

    /**
     * 네트워크 오류 여부 확인
     */
    private fun isNetworkError(exception: Exception?): Boolean {
        val message = exception?.message?.lowercase() ?: return false
        return message.contains("network") ||
               message.contains("timeout") ||
               message.contains("unable to resolve host")
    }

    /**
     * 오프라인 로그인 시도 (캐시된 세션 사용)
     */
    private fun attemptOfflineLogin() {
        val cachedSession = sessionManager.currentSession.value

        if (cachedSession != null && cachedSession.email == email) {
            // 캐시된 세션의 이메일과 입력한 이메일이 일치하면 오프라인 로그인 허용
            Log.d(TAG, "오프라인 로그인 성공: ${cachedSession.email}")

            _loginState.value = LoginState.Success(
                provinceId = cachedSession.provinceId,
                cityId = cachedSession.cityId,
                officeId = cachedSession.officeId,
                driverId = cachedSession.driverId,
                needsTokenUpdate = false
            )

            // 오프라인 상태 업데이트
            sessionManager.setOnlineStatus(false)
        } else {
            _loginState.value = LoginState.Error("오프라인 상태에서는 이전에 로그인한 계정만 사용할 수 있습니다.")
        }
    }

    private fun checkPendingStatusAndProceed(userId: String) {
        val pendingDocRef = firestore.collection("pending_drivers").document(userId)

        pendingDocRef.get().addOnSuccessListener { pendingDoc ->
            if (pendingDoc.exists()) {
                _loginState.value = LoginState.Error("관리자 승인 대기 중인 계정입니다.")
                auth.signOut()
            } else {
                findDriverDocumentAndSaveInfo(userId)
            }
        }.addOnFailureListener { e ->
             _loginState.value = LoginState.Error("로그인 처리 중 오류 발생: ${e.message}")
             auth.signOut()
        }
    }

    private fun findDriverDocumentAndSaveInfo(userId: String) {
        this.firestore.collectionGroup("designated_drivers")
            .whereEqualTo("authUid", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val documentSnapshot = querySnapshot.documents[0]
                    val driverRef = documentSnapshot.reference
                    val provinceId = documentSnapshot.getString("provinceId")
                    val cityId = documentSnapshot.getString("cityId")
                    val officeId = documentSnapshot.getString("officeId")
                    val driverApprovalStatus = documentSnapshot.getString("approvalStatus")
                    val currentDriverStatus = documentSnapshot.getString("status")
                    val driverName = documentSnapshot.getString("name") ?: "기사님"
                    val serverFcmToken = documentSnapshot.getString(Constants.FIELD_FCM_TOKEN)

                    if (driverApprovalStatus != com.designated.driverapp.model.DriverApprovalStatus.APPROVED.name) { // Enum의 name ("APPROVED")과 비교
                         val errorMessage = when (driverApprovalStatus) {
                             com.designated.driverapp.model.DriverApprovalStatus.PENDING.name -> "관리자 승인 대기 중인 계정입니다."
                             com.designated.driverapp.model.DriverApprovalStatus.REJECTED.name -> "가입이 거절된 계정입니다. 관리자에게 문의하세요."
                             else -> "계정 상태를 확인할 수 없습니다. 관리자에게 문의하세요."
                         }
                         _loginState.value = LoginState.Error(errorMessage)
                         auth.signOut()
                         return@addOnSuccessListener
                    }

                    if (!provinceId.isNullOrBlank() && !cityId.isNullOrBlank() && !officeId.isNullOrBlank()) {
                        // 일반 정보는 일반 SharedPreferences에 저장
                        sharedPreferences.edit().apply {
                            putString("provinceId", provinceId)
                            putString("cityId", cityId)
                            putString("officeId", officeId)
                            putString("driverId", userId)
                            apply()
                        }

                        // 민감 정보(비밀번호)는 암호화된 저장소에 저장
                        if (autoLogin) {
                            securePreferences.saveAutoLoginCredentials(email, password)
                        } else {
                            securePreferences.clearAutoLoginCredentials()
                        }

                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            val localFcmToken = if (task.isSuccessful) task.result else null
                            val needsUpdate = serverFcmToken.isNullOrBlank() || serverFcmToken != localFcmToken

                            // 세션 저장 (오프라인 로그인 지원)
                            val session = UserSession(
                                userId = userId,
                                email = email,
                                provinceId = provinceId,
                                cityId = cityId,
                                officeId = officeId,
                                driverId = userId,
                                driverName = driverName,
                                fcmToken = localFcmToken,
                                lastLoginTime = System.currentTimeMillis(),
                                isOnline = true
                            )
                            sessionManager.saveSession(session)

                            _loginState.value = LoginState.Success(provinceId, cityId, officeId, userId, needsUpdate)

                            // ✅ 로그인 시 항상 상태를 업데이트하여 콜매니저의 리스너가 트리거되도록 함
                            val onlineStatus = com.designated.driverapp.model.DriverStatus.ONLINE.value
                            val currentTime = com.google.firebase.Timestamp.now()
                            Log.d(TAG, "🔵 로그인 성공 - 상태 업데이트: $onlineStatus, 시간: $currentTime")

                            val updates = hashMapOf<String, Any>(
                                "status" to onlineStatus,
                                "lastLoginTime" to currentTime
                            )

                            driverRef.update(updates)
                                .addOnSuccessListener {
                                    Log.d(TAG, "✅ Firestore 상태 업데이트 성공: $onlineStatus")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "❌ Firestore 상태 업데이트 실패", e)
                                }
                        }

                    } else {
                        _loginState.value = LoginState.Error("기사 정보(지역/사무실 ID)가 누락되었습니다.")
                        auth.signOut()
                    }
                } else {
                     _loginState.value = LoginState.Error("등록되지 않은 기사 계정입니다.")
                     auth.signOut()
                }
            }
            .addOnFailureListener { e ->
                 _loginState.value = LoginState.Error("기사 정보 조회 중 오류 발생: ${e.message}")
                 auth.signOut()
            }
    }

    fun toggleAutoLogin(enabled: Boolean) {
        autoLogin = enabled
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    /**
     * 로그아웃 - 세션 삭제
     */
    fun logout() {
        auth.signOut()
        sessionManager.clearSession()
        securePreferences.clearAutoLoginCredentials()
        _loginState.value = LoginState.Idle
    }
}
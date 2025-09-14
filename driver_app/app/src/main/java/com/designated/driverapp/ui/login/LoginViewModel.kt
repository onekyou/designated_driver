package com.designated.driverapp.ui.login

import android.app.Application
import android.content.Context
import Log
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

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(val regionId: String, val officeId: String, val driverId: String, val needsTokenUpdate: Boolean) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {
    private val TAG = "LoginViewModel"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var autoLogin by mutableStateOf(false)

    init {

        autoLogin = sharedPreferences.getBoolean("auto_login", false)

        if (autoLogin) {
            val savedIdentifier = sharedPreferences.getString("identifier", "")
            val savedPassword = sharedPreferences.getString("password", "")
            if (!savedIdentifier.isNullOrBlank()) {
                email = savedIdentifier
                password = savedPassword ?: ""
            }
        } else {
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
                            _loginState.value = LoginState.Error(task.exception?.message ?: "로그인에 실패했습니다.")
                        }
                    }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
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
                    val regionId = documentSnapshot.getString("regionId")
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

                    if (!regionId.isNullOrBlank() && !officeId.isNullOrBlank()) {
                        sharedPreferences.edit().apply {
                            putString("regionId", regionId)
                            putString("officeId", officeId)
                            putString("driverId", userId)
                             if (autoLogin) {
                                 putBoolean("auto_login", true)
                                 putString("identifier", email)
                                 putString("password", password)
                             } else {
                                 remove("auto_login")
                                 remove("identifier")
                                 remove("password")
                             }
                            apply()
                        }

                        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                _loginState.value = LoginState.Success(regionId, officeId, userId, false)
                                return@addOnCompleteListener
                            }
                            val localFcmToken = task.result
                            val needsUpdate = serverFcmToken.isNullOrBlank() || serverFcmToken != localFcmToken

                            _loginState.value = LoginState.Success(regionId, officeId, userId, needsUpdate)

                            val onlineStatus = com.designated.driverapp.model.DriverStatus.ONLINE.value
                            if (currentDriverStatus != onlineStatus) {
                                driverRef.update("status", onlineStatus)
                                    .addOnSuccessListener { }
                                    .addOnFailureListener { e -> }
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
}
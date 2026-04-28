package com.designated.pickupdriver.ui.login

import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.model.DriverApprovalStatus
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(
        val provinceId: String,
        val cityId: String,
        val officeId: String,
        val driverId: String
    ) : LoginState()
    data class Error(val message: String) : LoginState()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sharedPreferences: SharedPreferences
) : ViewModel() {
    private val TAG = "PickupLoginVM"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    var email by mutableStateOf("")
    var password by mutableStateOf("")

    fun login() {
        if (email.isBlank() || password.isBlank()) {
            _loginState.value = LoginState.Error("이메일과 비밀번호를 모두 입력해주세요.")
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
                            val msg = task.exception?.message ?: "로그인에 실패했습니다."
                            Log.e(TAG, "로그인 실패: $msg")
                            _loginState.value = LoginState.Error(msg)
                        }
                    }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error(e.message ?: "알 수 없는 오류가 발생했습니다.")
            }
        }
    }

    private fun checkPendingStatusAndProceed(userId: String) {
        firestore.collection(Constants.COLLECTION_PENDING_DRIVERS).document(userId).get()
            .addOnSuccessListener { pendingDoc ->
                if (pendingDoc.exists()) {
                    _loginState.value = LoginState.Error("관리자 승인 대기 중인 계정입니다.")
                    auth.signOut()
                } else {
                    findPickupDriverDocumentAndSaveInfo(userId)
                }
            }
            .addOnFailureListener { e ->
                _loginState.value = LoginState.Error("로그인 처리 중 오류 발생: ${e.message}")
                auth.signOut()
            }
    }

    private fun findPickupDriverDocumentAndSaveInfo(userId: String) {
        firestore.collectionGroup(Constants.COLLECTION_GROUP_PICKUP_DRIVERS)
            .whereEqualTo("authUid", userId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    _loginState.value = LoginState.Error("등록되지 않은 픽업기사 계정입니다.")
                    auth.signOut()
                    return@addOnSuccessListener
                }

                val doc = querySnapshot.documents[0]
                val provinceId = doc.getString("provinceId")
                val cityId = doc.getString("cityId")
                val officeId = doc.getString("officeId")
                val approvalStatus = doc.getString("approvalStatus")

                if (approvalStatus != DriverApprovalStatus.APPROVED.name) {
                    val msg = when (approvalStatus) {
                        DriverApprovalStatus.PENDING.name -> "관리자 승인 대기 중인 계정입니다."
                        DriverApprovalStatus.REJECTED.name -> "가입이 거절된 계정입니다. 관리자에게 문의하세요."
                        else -> "계정 상태를 확인할 수 없습니다."
                    }
                    _loginState.value = LoginState.Error(msg)
                    auth.signOut()
                    return@addOnSuccessListener
                }

                if (provinceId.isNullOrBlank() || cityId.isNullOrBlank() || officeId.isNullOrBlank()) {
                    _loginState.value = LoginState.Error("기사 정보(지역/사무실 ID)가 누락되었습니다.")
                    auth.signOut()
                    return@addOnSuccessListener
                }

                sharedPreferences.edit().apply {
                    putString(Constants.PREF_KEY_PROVINCE_ID, provinceId)
                    putString(Constants.PREF_KEY_CITY_ID, cityId)
                    putString(Constants.PREF_KEY_OFFICE_ID, officeId)
                    putString(Constants.PREF_KEY_DRIVER_ID, userId)
                    apply()
                }

                // FCM 토큰 fetch + pickup_drivers/{uid}.fcmToken 저장
                // (수신 채팅이 본 기기로 오려면 필수 — onNewToken은 토큰 갱신 시만 호출됨)
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val fcmToken = task.result
                        if (!fcmToken.isNullOrBlank()) {
                            doc.reference.update(Constants.FIELD_FCM_TOKEN, fcmToken)
                                .addOnSuccessListener { Log.d(TAG, "fcmToken 저장 성공") }
                                .addOnFailureListener { e -> Log.e(TAG, "fcmToken 저장 실패", e) }
                        }
                    } else {
                        Log.w(TAG, "FCM 토큰 fetch 실패", task.exception)
                    }
                }

                Log.d(TAG, "로그인 성공: office=$officeId")
                _loginState.value = LoginState.Success(provinceId, cityId, officeId, userId)
            }
            .addOnFailureListener { e ->
                _loginState.value = LoginState.Error("기사 정보 조회 중 오류 발생: ${e.message}")
                auth.signOut()
            }
    }

    fun resetLoginState() {
        _loginState.value = LoginState.Idle
    }

    fun logout() {
        auth.signOut()
        sharedPreferences.edit().clear().apply()
        _loginState.value = LoginState.Idle
    }
}

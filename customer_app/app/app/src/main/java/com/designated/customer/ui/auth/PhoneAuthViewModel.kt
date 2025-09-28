package com.designated.customer.ui.auth

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.customer.data.model.AttributionResult
import com.designated.customer.service.AttributionService
import com.designated.customer.util.FingerprintManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseException
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import android.util.Log

data class PhoneAuthUiState(
    val isLoading: Boolean = false,
    val phoneNumber: String = "",
    val verificationCode: String = "",
    val isCodeSent: Boolean = false,
    val error: String? = null,
    val isVerified: Boolean = false,
    val attributionResult: AttributionResult? = null
)

class PhoneAuthViewModel(
    private val fingerprintManager: FingerprintManager,
    private val attributionService: AttributionService
) : ViewModel() {

    var uiState by mutableStateOf(PhoneAuthUiState())
        private set

    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null

    fun updatePhoneNumber(phoneNumber: String) {
        uiState = uiState.copy(phoneNumber = phoneNumber, error = null)
    }

    fun updateVerificationCode(code: String) {
        uiState = uiState.copy(verificationCode = code, error = null)
    }

    fun sendVerificationCode(activity: Activity) {
        Log.d("PhoneAuthViewModel", "sendVerificationCode 시작")

        if (uiState.phoneNumber.isEmpty()) {
            Log.e("PhoneAuthViewModel", "전화번호가 비어있음")
            uiState = uiState.copy(error = "전화번호를 입력해주세요")
            return
        }

        uiState = uiState.copy(isLoading = true, error = null)

        val phoneNumber = formatPhoneNumber(uiState.phoneNumber)
        Log.d("PhoneAuthViewModel", "포맷된 전화번호: $phoneNumber")
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    Log.d("PhoneAuthViewModel", "onVerificationCompleted 호출됨")
                    // 자동 인증 완료
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("PhoneAuthViewModel", "onVerificationFailed: ${e.message}", e)
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "인증 실패: ${e.message}"
                    )
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d("PhoneAuthViewModel", "onCodeSent 호출됨. verificationId: $verificationId")
                    this@PhoneAuthViewModel.verificationId = verificationId
                    uiState = uiState.copy(
                        isLoading = false,
                        isCodeSent = true
                    )
                }
            })
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyCode() {
        if (uiState.verificationCode.isEmpty()) {
            uiState = uiState.copy(error = "인증 코드를 입력해주세요")
            return
        }

        val verificationId = this.verificationId
        if (verificationId == null) {
            uiState = uiState.copy(error = "인증 과정에서 오류가 발생했습니다")
            return
        }

        uiState = uiState.copy(isLoading = true, error = null)

        val credential = PhoneAuthProvider.getCredential(verificationId, uiState.verificationCode)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    uiState = uiState.copy(isVerified = true)
                    // 어트리뷰션 매칭 시작
                    performAttribution()
                } else {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "인증 실패: ${task.exception?.message}"
                    )
                }
            }
    }

    private fun performAttribution() {
        viewModelScope.launch {
            try {
                val fingerprint = fingerprintManager.collectFingerprint()
                val result = attributionService.matchAttribution(
                    fingerprint = fingerprint,
                    phoneNumber = uiState.phoneNumber
                )

                uiState = uiState.copy(
                    isLoading = false,
                    attributionResult = result
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "사무실 연결 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    private fun formatPhoneNumber(phoneNumber: String): String {
        // 한국 전화번호 형식으로 변환
        val cleaned = phoneNumber.replace(Regex("[^0-9]"), "")
        return if (cleaned.startsWith("010")) {
            "+82${cleaned.substring(1)}"
        } else {
            "+82$cleaned"
        }
    }

    fun selectOffice(officeId: String) {
        viewModelScope.launch {
            val success = attributionService.saveOfficeSelection(
                phoneNumber = uiState.phoneNumber,
                officeId = officeId,
                reason = "manual_selection"
            )

            if (success) {
                uiState = uiState.copy(
                    attributionResult = AttributionResult(
                        success = true,
                        officeId = officeId,
                        score = null,
                        confidence = "MANUAL"
                    )
                )
            } else {
                uiState = uiState.copy(
                    error = "사무실 선택 저장에 실패했습니다"
                )
            }
        }
    }
}
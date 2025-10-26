package com.designated.customer.ui.auth

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseException
import java.util.concurrent.TimeUnit

data class PhoneAuthUiState(
    val isLoading: Boolean = false,
    val phoneNumber: String = "",
    val verificationCode: String = "",
    val isCodeSent: Boolean = false,
    val error: String? = null,
    val isVerified: Boolean = false
)

class PhoneAuthViewModel : ViewModel() {

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
        if (uiState.phoneNumber.isEmpty()) {
            uiState = uiState.copy(error = "전화번호를 입력해주세요")
            return
        }

        uiState = uiState.copy(isLoading = true, error = null)

        val formattedPhoneNumber = formatPhoneNumber(uiState.phoneNumber)

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "인증 실패: ${e.message}"
                    )
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
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
            uiState = uiState.copy(error = "인증 세션이 만료되었습니다. 다시 시도해주세요")
            return
        }

        uiState = uiState.copy(isLoading = true, error = null)

        val credential = PhoneAuthProvider.getCredential(verificationId, uiState.verificationCode)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    uiState = uiState.copy(
                        isLoading = false,
                        isVerified = true
                    )
                } else {
                    uiState = uiState.copy(
                        isLoading = false,
                        error = "인증 실패: ${task.exception?.message}"
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

    fun clearError() {
        uiState = uiState.copy(error = null)
    }
}
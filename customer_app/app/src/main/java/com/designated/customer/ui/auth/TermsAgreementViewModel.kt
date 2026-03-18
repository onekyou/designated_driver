package com.designated.customer.ui.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class TermsAgreementState(
    val termsAgreed: Boolean = false,
    val privacyAgreed: Boolean = false,
    val marketingAgreed: Boolean = false
)

class TermsAgreementViewModel : ViewModel() {

    companion object {
        const val TERMS_VERSION = "1.0.0"
    }

    var state by mutableStateOf(TermsAgreementState())
        private set

    val allRequiredAgreed: Boolean
        get() = state.termsAgreed && state.privacyAgreed

    val allAgreed: Boolean
        get() = state.termsAgreed && state.privacyAgreed && state.marketingAgreed

    fun toggleTerms(agreed: Boolean) {
        state = state.copy(termsAgreed = agreed)
    }

    fun togglePrivacy(agreed: Boolean) {
        state = state.copy(privacyAgreed = agreed)
    }

    fun toggleMarketing(agreed: Boolean) {
        state = state.copy(marketingAgreed = agreed)
    }

    fun toggleAll(agreed: Boolean) {
        state = TermsAgreementState(
            termsAgreed = agreed,
            privacyAgreed = agreed,
            marketingAgreed = agreed
        )
    }
}

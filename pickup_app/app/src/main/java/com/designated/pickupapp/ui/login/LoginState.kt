package com.designated.pickupapp.ui.login

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class Success(
        val regionId: String,
        val officeId: String,
        val driverId: String,
        val needsTokenUpdate: Boolean = false
    ) : LoginState()
    data class Error(val message: String) : LoginState()
}
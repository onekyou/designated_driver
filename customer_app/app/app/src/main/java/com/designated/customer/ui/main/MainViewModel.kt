package com.designated.customer.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.customer.data.model.CustomerCall
import com.designated.customer.service.CallService
import com.designated.customer.service.LocationService
import kotlinx.coroutines.launch

data class MainUiState(
    val currentLocation: String = "",
    val destinationLocation: String = "",
    val isLoadingLocation: Boolean = false,
    val isLoadingCall: Boolean = false,
    val callStatus: CallStatus? = null,
    val error: String? = null
) {
    val canRequestCall: Boolean
        get() = currentLocation.isNotEmpty() &&
                destinationLocation.isNotEmpty() &&
                callStatus?.state != CallState.REQUESTED &&
                callStatus?.state != CallState.ASSIGNED &&
                callStatus?.state != CallState.DRIVER_ARRIVING &&
                callStatus?.state != CallState.IN_PROGRESS
}

class MainViewModel(
    private val callService: CallService,
    private val locationService: LocationService,
    private val officeId: String,
    private val phoneNumber: String
) : ViewModel() {

    var uiState by mutableStateOf(MainUiState())
        private set

    init {
        // 활성 콜 상태 모니터링 시작
        monitorCallStatus()
    }

    fun updateCurrentLocation(location: String) {
        uiState = uiState.copy(currentLocation = location, error = null)
    }

    fun updateDestinationLocation(location: String) {
        uiState = uiState.copy(destinationLocation = location, error = null)
    }

    fun getCurrentLocation() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoadingLocation = true, error = null)

            try {
                val location = locationService.getCurrentLocation()
                uiState = uiState.copy(
                    currentLocation = location,
                    isLoadingLocation = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingLocation = false,
                    error = "현재 위치를 가져올 수 없습니다: ${e.message}"
                )
            }
        }
    }

    fun requestCall() {
        if (!uiState.canRequestCall) {
            uiState = uiState.copy(error = "콜을 요청할 수 없는 상태입니다")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoadingCall = true, error = null)

            try {
                val call = CustomerCall(
                    phoneNumber = phoneNumber,
                    officeId = officeId,
                    currentLocation = uiState.currentLocation,
                    destinationLocation = uiState.destinationLocation,
                    timestamp = System.currentTimeMillis(),
                    status = "REQUESTED"
                )

                val callId = callService.requestCall(call)

                // 콜 상태를 업데이트
                uiState = uiState.copy(
                    isLoadingCall = false,
                    callStatus = CallStatus(
                        callId = callId,
                        state = CallState.REQUESTED,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoadingCall = false,
                    error = "콜 요청 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    fun cancelCall() {
        val currentCallId = uiState.callStatus?.callId ?: return

        viewModelScope.launch {
            try {
                val success = callService.cancelCall(currentCallId)

                if (success) {
                    uiState = uiState.copy(
                        callStatus = uiState.callStatus?.copy(
                            state = CallState.CANCELLED
                        )
                    )
                } else {
                    uiState = uiState.copy(
                        error = "콜 취소에 실패했습니다"
                    )
                }
            } catch (e: Exception) {
                uiState = uiState.copy(
                    error = "콜 취소 중 오류가 발생했습니다: ${e.message}"
                )
            }
        }
    }

    private fun monitorCallStatus() {
        viewModelScope.launch {
            callService.monitorCallStatus(phoneNumber) { callStatus ->
                uiState = uiState.copy(callStatus = callStatus)
            }
        }
    }

    fun clearError() {
        uiState = uiState.copy(error = null)
    }
}
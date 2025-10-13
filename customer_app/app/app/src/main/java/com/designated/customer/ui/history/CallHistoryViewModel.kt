package com.designated.customer.ui.history

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.customer.data.model.CustomerCall
import com.designated.customer.service.CallService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

data class CallSummary(
    val totalRides: Int = 0,
    val totalAmount: Int = 0,
    val totalPointsEarned: Int = 0,
    val thisMonthRides: Int = 0,
    val thisMonthAmount: Int = 0
)

data class CallHistoryUiState(
    val calls: List<CustomerCall> = emptyList(),
    val summary: CallSummary? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

class CallHistoryViewModel(
    private val phoneNumber: String,
    private val regionId: String,
    private val officeId: String
) : ViewModel() {

    var uiState by mutableStateOf(CallHistoryUiState())
        private set

    private val callService = CallService(regionId = regionId, officeId = officeId)

    init {
        loadCallHistory()
        observeCallHistory()
    }

    private fun loadCallHistory() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val calls = callService.getCustomerCallHistory(phoneNumber)
                val summary = calculateSummary(calls)

                uiState = uiState.copy(
                    calls = calls.sortedByDescending { it.timestamp },
                    summary = summary,
                    isLoading = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "이용 내역을 불러올 수 없습니다: ${e.message}"
                )
            }
        }
    }

    private fun observeCallHistory() {
        viewModelScope.launch {
            callService.observeCustomerCalls(phoneNumber).collectLatest { calls ->
                val summary = calculateSummary(calls)
                uiState = uiState.copy(
                    calls = calls.sortedByDescending { it.timestamp },
                    summary = summary
                )
            }
        }
    }

    private fun calculateSummary(calls: List<CustomerCall>): CallSummary {
        val currentMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val completedCalls = calls.filter { it.status.equals("COMPLETED", ignoreCase = true) }
        val thisMonthCalls = completedCalls.filter { it.timestamp >= currentMonth }

        return CallSummary(
            totalRides = completedCalls.size,
            totalAmount = completedCalls.sumOf { it.fare ?: 0 },
            totalPointsEarned = calculateTotalPointsEarned(completedCalls),
            thisMonthRides = thisMonthCalls.size,
            thisMonthAmount = thisMonthCalls.sumOf { it.fare ?: 0 }
        )
    }

    private fun calculateTotalPointsEarned(calls: List<CustomerCall>): Int {
        return calls.sumOf { call ->
            val fare = call.fare ?: 0
            val grade = call.customerGrade?.lowercase() ?: "bronze"
            val rate = when (grade) {
                "bronze" -> 0.01
                "silver" -> 0.02
                "gold" -> 0.03
                "vip" -> 0.05
                else -> 0.01
            }
            (fare * rate).toInt()
        }
    }

    fun refreshHistory() {
        loadCallHistory()
    }
}
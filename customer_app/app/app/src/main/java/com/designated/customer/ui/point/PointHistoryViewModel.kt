package com.designated.customer.ui.point

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.customer.data.model.PointTransaction
import com.designated.customer.service.PointService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PointHistoryUiState(
    val transactions: List<PointTransaction> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class PointHistoryViewModel(
    private val phoneNumber: String,
    private val regionId: String,
    private val officeId: String
) : ViewModel() {

    var uiState by mutableStateOf(PointHistoryUiState())
        private set

    private val pointService = PointService(regionId = regionId, officeId = officeId)

    init {
        loadPointHistory()
        // ✅ Real-time Listener 제거 - 1회 조회만 수행
    }

    private fun loadPointHistory() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null)
            try {
                val transactions = pointService.getPointTransactions(phoneNumber)
                uiState = uiState.copy(
                    transactions = transactions,
                    isLoading = false
                )
            } catch (e: Exception) {
                uiState = uiState.copy(
                    isLoading = false,
                    error = "포인트 내역을 불러올 수 없습니다: ${e.message}"
                )
            }
        }
    }

    fun refreshHistory() {
        loadPointHistory()
    }
}
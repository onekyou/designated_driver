package com.designated.callmanager.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.CustomerInfo
import com.designated.callmanager.data.CustomerPointTransaction
import com.designated.callmanager.service.CustomerService
import com.designated.callmanager.service.CustomerDetailResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomerDetailViewModel : ViewModel() {
    private val customerService = CustomerService()

    private val _customer = MutableStateFlow<CustomerInfo?>(null)
    val customer = _customer.asStateFlow()

    private val _transactions = MutableStateFlow<List<CustomerPointTransaction>>(emptyList())
    val transactions = _transactions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val TAG = "CustomerDetailVM"

    fun loadCustomerDetail(provinceId: String, cityId: String, officeId: String, customerId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            try {
                val result = customerService.getCustomerDetail(provinceId, cityId, officeId, customerId)

                when (result) {
                    is CustomerDetailResult.Success -> {
                        _customer.value = result.customer
                        _transactions.value = result.recentTransactions
                        android.util.Log.d(TAG, "고객 상세 정보 로드 완료: ${result.customer.name}")
                    }
                    is CustomerDetailResult.NotFound -> {
                        _customer.value = null
                        _transactions.value = emptyList()
                        _errorMessage.value = "해당 고객 정보를 찾을 수 없습니다"
                        android.util.Log.w(TAG, "고객 정보 없음: $customerId")
                    }
                    is CustomerDetailResult.Error -> {
                        _errorMessage.value = result.message
                        android.util.Log.e(TAG, "고객 상세 정보 로드 실패: ${result.message}")
                    }
                }

            } catch (e: Exception) {
                _errorMessage.value = "고객 정보를 불러오는 중 오류가 발생했습니다"
                android.util.Log.e(TAG, "예상치 못한 오류", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
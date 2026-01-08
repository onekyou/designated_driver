package com.designated.callmanager.ui.customer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.CustomerInfo
import com.designated.callmanager.data.CustomerPointTransaction
import com.designated.callmanager.service.CustomerService
import com.designated.callmanager.service.CustomerListResult
import com.designated.callmanager.service.CustomerStatsResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CustomerManagementViewModel : ViewModel() {
    private val customerService = CustomerService()

    private val _customers = MutableStateFlow<List<CustomerInfo>>(emptyList())
    val customers = _customers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _selectedGradeFilter = MutableStateFlow<String?>(null)
    val selectedGradeFilter = _selectedGradeFilter.asStateFlow()

    private val _selectedActivityFilter = MutableStateFlow<String?>(null)
    val selectedActivityFilter = _selectedActivityFilter.asStateFlow()

    private val _customerStats = MutableStateFlow<com.designated.callmanager.service.CustomerStats?>(null)
    val customerStats = _customerStats.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    private var lastCustomerId: String? = null
    private var canLoadMore = true

    private val TAG = "CustomerManagementVM"

    fun fetchCustomers(regionId: String, officeId: String, refresh: Boolean = true) {
        viewModelScope.launch {
            if (refresh) {
                _isLoading.value = true
                _customers.value = emptyList()
                lastCustomerId = null
                canLoadMore = true
            } else {
                _isLoadingMore.value = true
            }

            try {
                val result = customerService.getCustomerList(
                    regionId = regionId,
                    officeId = officeId,
                    limit = 20,
                    lastCustomerId = if (refresh) null else lastCustomerId
                )

                when (result) {
                    is CustomerListResult.Success -> {
                        if (refresh) {
                            _customers.value = result.customers
                        } else {
                            _customers.value = _customers.value + result.customers
                        }
                        lastCustomerId = result.lastCustomerId
                        canLoadMore = result.customers.size == 20 // 더 불러올 수 있는지 확인

                        android.util.Log.d(TAG, "고객 목록 로드 완료: ${result.customers.size}명")
                    }
                    is CustomerListResult.Error -> {
                        _errorMessage.value = result.message
                        android.util.Log.e(TAG, "고객 목록 로드 실패: ${result.message}")
                    }
                }

                // 통계도 함께 로드 (첫 번째 로드 시에만)
                if (refresh) {
                    loadCustomerStats(regionId, officeId)
                }

            } catch (e: Exception) {
                _errorMessage.value = "고객 목록을 불러오는 중 오류가 발생했습니다"
                android.util.Log.e(TAG, "예상치 못한 오류", e)
            } finally {
                _isLoading.value = false
                _isLoadingMore.value = false
            }
        }
    }

    fun loadMoreCustomers(regionId: String, officeId: String) {
        if (canLoadMore && !_isLoadingMore.value && !_isLoading.value) {
            fetchCustomers(regionId, officeId, refresh = false)
        }
    }

    private suspend fun loadCustomerStats(regionId: String, officeId: String) {
        try {
            val result = customerService.getCustomerStats(regionId, officeId)
            when (result) {
                is CustomerStatsResult.Success -> {
                    _customerStats.value = result.stats
                }
                is CustomerStatsResult.Error -> {
                    android.util.Log.e(TAG, "고객 통계 로드 실패: ${result.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "고객 통계 로드 중 오류", e)
        }
    }

    fun filterByGrade(regionId: String, officeId: String, grade: String?) {
        viewModelScope.launch {
            _selectedGradeFilter.value = grade
            _selectedActivityFilter.value = null // 활동 상태 필터 초기화
            _isLoading.value = true

            try {
                val result = if (grade != null) {
                    customerService.getCustomersByGrade(regionId, officeId, grade)
                } else {
                    customerService.getCustomerList(regionId, officeId)
                }

                when (result) {
                    is CustomerListResult.Success -> {
                        _customers.value = result.customers
                        lastCustomerId = result.lastCustomerId
                        canLoadMore = result.customers.size == 20
                        android.util.Log.d(TAG, "등급 필터 적용: $grade, ${result.customers.size}명")
                    }
                    is CustomerListResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "필터링 중 오류가 발생했습니다"
                android.util.Log.e(TAG, "등급 필터링 실패", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchCustomer(regionId: String, officeId: String, phoneNumber: String) {
        viewModelScope.launch {
            _searchQuery.value = phoneNumber
            _isLoading.value = true

            try {
                val result = customerService.searchCustomerByPhone(regionId, officeId, phoneNumber)
                when (result) {
                    is com.designated.callmanager.service.CustomerSearchResult.Success -> {
                        _customers.value = listOf(result.customer)
                        canLoadMore = false
                    }
                    is com.designated.callmanager.service.CustomerSearchResult.NotFound -> {
                        _customers.value = emptyList()
                        _errorMessage.value = "해당 전화번호의 고객을 찾을 수 없습니다"
                    }
                    is com.designated.callmanager.service.CustomerSearchResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "검색 중 오류가 발생했습니다"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSearch(regionId: String, officeId: String) {
        _searchQuery.value = ""
        fetchCustomers(regionId, officeId, refresh = true)
    }

    fun filterByActivityStatus(regionId: String, officeId: String, status: String?) {
        viewModelScope.launch {
            _selectedActivityFilter.value = status
            _selectedGradeFilter.value = null // 등급 필터 초기화
            _isLoading.value = true

            try {
                // 먼저 전체 고객 목록을 가져옴
                val result = customerService.getCustomerList(regionId, officeId)

                when (result) {
                    is CustomerListResult.Success -> {
                        // 클라이언트 측에서 활동 상태로 필터링
                        _customers.value = if (status != null) {
                            result.customers.filter { it.getActivityStatus() == status }
                        } else {
                            result.customers
                        }
                        lastCustomerId = result.lastCustomerId
                        canLoadMore = false // 필터링 시에는 페이징 비활성화
                        android.util.Log.d(TAG, "활동 상태 필터 적용: $status, ${_customers.value.size}명")
                    }
                    is CustomerListResult.Error -> {
                        _errorMessage.value = result.message
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "필터링 중 오류가 발생했습니다"
                android.util.Log.e(TAG, "활동 상태 필터링 실패", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 휴면 회원 일괄 삭제 (90일 이상 비활성)
     */
    fun deleteDormantCustomers(regionId: String, officeId: String, onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                val result = customerService.deleteDormantCustomers(regionId, officeId)
                when (result) {
                    is com.designated.callmanager.service.DormantDeleteResult.Success -> {
                        android.util.Log.d(TAG, "휴면 회원 ${result.deletedCount}명 삭제 완료")
                        // 삭제 후 목록 새로고침
                        fetchCustomers(regionId, officeId, refresh = true)
                        onComplete(result.deletedCount)
                    }
                    is com.designated.callmanager.service.DormantDeleteResult.Error -> {
                        _errorMessage.value = result.message
                        onComplete(0)
                    }
                }
            } catch (e: Exception) {
                _errorMessage.value = "삭제 중 오류가 발생했습니다"
                android.util.Log.e(TAG, "휴면 회원 삭제 실패", e)
                onComplete(0)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

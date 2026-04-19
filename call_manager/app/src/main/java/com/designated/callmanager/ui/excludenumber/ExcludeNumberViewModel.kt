package com.designated.callmanager.ui.excludenumber

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.ExcludeNumberItem
import com.designated.callmanager.data.ExcludeNumberManager
import com.designated.callmanager.data.RestoreResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExcludeNumberUiState(
    val allNumbers: List<ExcludeNumberItem> = emptyList(),
    val filteredNumbers: List<ExcludeNumberItem> = emptyList(),
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val isLoading: Boolean = false
)

class ExcludeNumberViewModel(application: Application) : AndroidViewModel(application) {

    private val excludeNumberManager = ExcludeNumberManager(application)

    private val _uiState = MutableStateFlow(ExcludeNumberUiState())
    val uiState: StateFlow<ExcludeNumberUiState> = _uiState.asStateFlow()

    fun loadExcludeNumbers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val numbers = excludeNumberManager.getExcludeNumberItems()

            _uiState.update { state ->
                state.copy(
                    allNumbers = numbers,
                    searchQuery = "",
                    filteredNumbers = numbers,
                    totalCount = numbers.size,
                    isLoading = false
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { state ->
            state.copy(
                searchQuery = query,
                filteredNumbers = filterNumbers(state.allNumbers, query)
            )
        }
    }

    private fun filterNumbers(numbers: List<ExcludeNumberItem>, query: String): List<ExcludeNumberItem> {
        if (query.isEmpty()) return numbers

        val lowerQuery = query.lowercase()
        return numbers.filter { item ->
            item.displayNumber.contains(query) ||
            item.phoneNumber.contains(query) ||
            item.name?.lowercase()?.contains(lowerQuery) == true
        }
    }

    fun addExcludeNumber(phoneNumber: String, name: String? = null) {
        viewModelScope.launch {
            excludeNumberManager.addExcludeNumber(phoneNumber, name)
            loadExcludeNumbers()
        }
    }

    fun removeExcludeNumber(item: ExcludeNumberItem) {
        viewModelScope.launch {
            excludeNumberManager.removeExcludeNumber(item.phoneNumber)
            loadExcludeNumbers()
        }
    }

    fun clearAllNumbers() {
        viewModelScope.launch {
            excludeNumberManager.clearAll()
            loadExcludeNumbers()
        }
    }

    suspend fun backupNumbers(): Boolean {
        return excludeNumberManager.backupToFile()
    }

    suspend fun restoreNumbers(): RestoreResult {
        val result = excludeNumberManager.restoreFromFile()
        if (result is RestoreResult.Success) {
            loadExcludeNumbers()
        }
        return result
    }

    fun hasBackupFile(): Boolean {
        return excludeNumberManager.hasBackupFile()
    }

    fun getLastBackupTime(): Long {
        return excludeNumberManager.getLastBackupTime()
    }
}
package com.designated.callmanager.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// TODO: Implement SettingsViewModel logic

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val userId = auth.currentUser?.uid

    private val _isOfficeOpen = MutableStateFlow(true)
    val isOfficeOpen: StateFlow<Boolean> = _isOfficeOpen.asStateFlow()

    init {
        // TODO: Load initial settings (e.g., office status) from Firestore or SharedPreferences
    }

    fun setOfficeStatus(isOpen: Boolean) {
        // TODO: Update office status in Firestore
        _isOfficeOpen.value = isOpen
        /*
        viewModelScope.launch {
            val provinceId = ...
            val cityId = ...
            val officeId = ...
            if (userId != null && provinceId != null && cityId != null && officeId != null) {
                try {
                    val status = if (isOpen) "operating" else "closed_sharing"
                    firestore.collection("provinces").document(provinceId)
                             .collection("cities").document(cityId)
                             .collection("offices").document(officeId)
                             .update("status", status).await()
                    _isOfficeOpen.value = isOpen
                } catch (e: Exception) {
                }
            }
        }
        */
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
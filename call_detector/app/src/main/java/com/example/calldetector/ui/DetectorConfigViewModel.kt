package com.example.calldetector.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calldetector.data.OfficeItem
import com.example.calldetector.data.RegionItem
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetectorConfigUiState(
    val regions: List<RegionItem> = emptyList(),
    val offices: List<OfficeItem> = emptyList(),
    val availableDeviceNames: List<String> = listOf("전화기 1", "전화기 2", "전화기 3", "전화기 4", "전화기 5"),
    val selectedRegion: RegionItem? = null,
    val selectedOffice: OfficeItem? = null,
    val selectedDeviceName: String = "",
    val isLoadingRegions: Boolean = false,
    val isLoadingOffices: Boolean = false,
    val error: String? = null,
    val saveSuccess: Boolean = false
)

enum class ScreenState { LOGIN, SETTINGS, STATUS }

class DetectorConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "DetectorConfigVM"
    private val sharedPreferences = application.getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)
    private val db = Firebase.firestore

    private val _uiState = MutableStateFlow(DetectorConfigUiState())
    val uiState: StateFlow<DetectorConfigUiState> = _uiState.asStateFlow()

    private val _uiScreenState = MutableStateFlow(ScreenState.SETTINGS)
    val uiScreenState: StateFlow<ScreenState> = _uiScreenState.asStateFlow()

    private val _onSettingsSaved = MutableSharedFlow<Boolean>()
    val onSettingsSaved: SharedFlow<Boolean> = _onSettingsSaved.asSharedFlow()

    init {
        loadInitialConfig()
    }

    private fun loadInitialConfig() {
        val regionId = sharedPreferences.getString("regionId", null)
        val regionName = sharedPreferences.getString("regionName", null)
        val officeId = sharedPreferences.getString("officeId", null)
        val officeName = sharedPreferences.getString("officeName", null)
        val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

        val initialRegion = if (regionId != null && regionName != null) RegionItem(regionId, regionName) else null
        val initialOffice = if (officeId != null && officeName != null) OfficeItem(officeId, officeName) else null

        _uiState.update {
            it.copy(
                selectedRegion = initialRegion,
                selectedOffice = initialOffice,
                selectedDeviceName = deviceName
            )
        }
        fetchRegions()
        if (initialRegion != null) {
            fetchOffices(initialRegion.id)
        }
    }

    fun fetchRegions() {
        _uiState.update { it.copy(isLoadingRegions = true, error = null) }
        db.collection("regions")
            .orderBy("name")
            .get()
            .addOnSuccessListener { documents ->
                val regionList = documents.map { doc ->
                    RegionItem(id = doc.id, name = doc.getString("name") ?: "")
                }
                _uiState.update {
                    it.copy(
                        regions = regionList,
                        isLoadingRegions = false
                    )
                }
            }
            .addOnFailureListener { exception ->
                _uiState.update { it.copy(isLoadingRegions = false, error = "지역 정보를 가져오는데 실패했습니다: ${exception.message}") }
            }
    }

    fun fetchOffices(regionId: String) {
        _uiState.update { it.copy(isLoadingOffices = true, error = null, offices = emptyList()) }
        db.collection("regions").document(regionId).collection("offices")
            .orderBy("name")
            .get()
            .addOnSuccessListener { documents ->
                val officeList = documents.map { doc ->
                    OfficeItem(id = doc.id, name = doc.getString("name") ?: "")
                }
                _uiState.update {
                    it.copy(
                        offices = officeList,
                        isLoadingOffices = false
                    )
                }
            }
            .addOnFailureListener { exception ->
                _uiState.update { it.copy(isLoadingOffices = false, error = "사무실 정보를 가져오는데 실패했습니다: ${exception.message}") }
            }
    }

    fun selectRegion(region: RegionItem) {
        _uiState.update {
            it.copy(
                selectedRegion = region,
                selectedOffice = null,
                offices = emptyList()
            )
        }
        fetchOffices(region.id)
    }

    fun selectOffice(office: OfficeItem) {
        _uiState.update { it.copy(selectedOffice = office) }
    }

    fun selectDeviceName(name: String) {
        _uiState.update { it.copy(selectedDeviceName = name) }
    }

    fun saveSelection() {
        val region = _uiState.value.selectedRegion
        val office = _uiState.value.selectedOffice
        val deviceName = _uiState.value.selectedDeviceName

        if (region != null && office != null && deviceName.isNotBlank()) {
            sharedPreferences.edit()
                .putString("regionId", region.id)
                .putString("regionName", region.name)
                .putString("officeId", office.id)
                .putString("officeName", office.name)
                .putString("deviceName", deviceName)
                .apply()
            _uiState.update { it.copy(saveSuccess = true, error = null) }
            viewModelScope.launch {
                _onSettingsSaved.emit(true)
            }
        } else {
            _uiState.update { it.copy(error = "지역, 사무실, 전화기 이름을 모두 선택해주세요.", saveSuccess = false) }
            viewModelScope.launch {
                _onSettingsSaved.emit(false)
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun resetSaveStatus() {
         _uiState.update { it.copy(saveSuccess = false) }
    }

    fun navigateToSettingsScreen() {
        _uiScreenState.value = ScreenState.SETTINGS
    }

    fun navigateToStatusScreen() {
        _uiScreenState.value = ScreenState.STATUS
    }
}

class DetectorConfigViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetectorConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetectorConfigViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
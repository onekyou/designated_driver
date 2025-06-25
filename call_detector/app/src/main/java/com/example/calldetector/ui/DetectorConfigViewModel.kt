package com.example.calldetector.ui

import android.app.Application
import android.content.Context
import android.util.Log
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

// Data class to hold the UI state
data class DetectorConfigUiState(
    val regions: List<RegionItem> = emptyList(),
    val offices: List<OfficeItem> = emptyList(),
    val availableDeviceNames: List<String> = listOf("전화기 1", "전화기 2", "전화기 3", "전화기 4", "전화기 5"), // 기본값 또는 로드된 값
    val selectedRegion: RegionItem? = null,
    val selectedOffice: OfficeItem? = null,
    val selectedDeviceName: String = "",
    val isLoadingRegions: Boolean = false,
    val isLoadingOffices: Boolean = false,
    val error: String? = null, // 오류 메시지
    val saveSuccess: Boolean = false // 저장 성공 시 토스트 메시지 표시용
)

// 화면 상태를 나타내는 enum 추가
enum class ScreenState { SETTINGS, STATUS }

class DetectorConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "DetectorConfigVM"
    private val sharedPreferences = application.getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)
    private val db = Firebase.firestore

    private val _uiState = MutableStateFlow(DetectorConfigUiState())
    val uiState: StateFlow<DetectorConfigUiState> = _uiState.asStateFlow()

    private val _uiScreenState = MutableStateFlow(ScreenState.SETTINGS) // 초기 화면은 설정
    val uiScreenState: StateFlow<ScreenState> = _uiScreenState.asStateFlow()

    private val _onSettingsSaved = MutableSharedFlow<Boolean>() // 설정 저장 이벤트
    val onSettingsSaved: SharedFlow<Boolean> = _onSettingsSaved.asSharedFlow()

    init {
        loadInitialConfig() // ViewModel 생성 시 초기 설정 로딩
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
        fetchRegions() // 지역 목록 로드
        if (initialRegion != null) {
            fetchOffices(initialRegion.id) // 선택된 지역이 있으면 해당 사무실 로드
        }
        Log.d(TAG, "Loaded initial config: Region=$initialRegion, Office=$initialOffice, Device=$deviceName")
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
                        // 기존 선택된 지역을 유지하고, 자동 선택 로직 제거
                    )
                }
                Log.d(TAG, "Fetched regions: ${regionList.size} items")
            }
            .addOnFailureListener { exception ->
                _uiState.update { it.copy(isLoadingRegions = false, error = "지역 정보를 가져오는데 실패했습니다: ${exception.message}") }
                Log.e(TAG, "Error fetching regions", exception)
            }
    }

    fun fetchOffices(regionId: String) {
        _uiState.update { it.copy(isLoadingOffices = true, error = null, offices = emptyList()) } // 사무실 목록 초기화
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
                        // 기존 선택된 사무실을 유지하고, 자동 선택 로직 제거
                    )
                }
                Log.d(TAG, "Fetched offices for region $regionId: ${officeList.size} items")
            }
            .addOnFailureListener { exception ->
                _uiState.update { it.copy(isLoadingOffices = false, error = "사무실 정보를 가져오는데 실패했습니다: ${exception.message}") }
                Log.e(TAG, "Error fetching offices for region $regionId", exception)
            }
    }

    fun selectRegion(region: RegionItem) {
        _uiState.update {
            it.copy(
                selectedRegion = region,
                selectedOffice = null, // 지역 변경 시 사무실 선택 초기화
                offices = emptyList() // 사무실 목록 초기화
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
            _uiState.update { it.copy(saveSuccess = true, error = null) } // 저장 성공 상태 업데이트
             Log.d(TAG, "Saved selection: Region=${region.id}, Office=${office.id}, DeviceName=$deviceName")
            viewModelScope.launch { // 추가
                _onSettingsSaved.emit(true) // 저장 성공 이벤트 발생
            }
        } else {
            // 오류 상태 업데이트 (UI에서 메시지 표시용)
            _uiState.update { it.copy(error = "지역, 사무실, 전화기 이름을 모두 선택해주세요.", saveSuccess = false) }
            Log.e(TAG, "Error saving selection: Not all values selected. Region=$region, Office=$office, DeviceName=$deviceName")
            viewModelScope.launch { // 추가
                _onSettingsSaved.emit(false) // 저장 실패 이벤트 발생
            }
        }
    }

    // UI에서 오류 메시지를 표시한 후 호출하여 상태를 초기화
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
    
    // 저장 성공 메시지 표시 후 호출
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

// ViewModel을 생성하기 위한 Factory 클래스
class DetectorConfigViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetectorConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetectorConfigViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
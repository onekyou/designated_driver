package com.designated.calldetector.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.calldetector.data.CityItem
import com.designated.calldetector.data.OfficeItem
import com.designated.calldetector.data.ProvinceItem
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
    val provinces: List<ProvinceItem> = emptyList(),
    val cities: List<CityItem> = emptyList(),
    val offices: List<OfficeItem> = emptyList(),
    val availableDeviceNames: List<String> = listOf("전화기 1", "전화기 2", "전화기 3", "전화기 4", "전화기 5"), // 기본값 또는 로드된 값
    val selectedProvince: ProvinceItem? = null,
    val selectedCity: CityItem? = null,
    val selectedOffice: OfficeItem? = null,
    val selectedDeviceName: String = "",
    val isLoadingProvinces: Boolean = false,
    val isLoadingCities: Boolean = false,
    val isLoadingOffices: Boolean = false,
    val error: String? = null, // 오류 메시지
    val saveSuccess: Boolean = false // 저장 성공 시 토스트 메시지 표시용
)

// 화면 상태를 나타내는 enum 추가
enum class ScreenState { LOGIN, SETTINGS, STATUS }

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
        val provinceId = sharedPreferences.getString("provinceId", null)
        val provinceName = sharedPreferences.getString("provinceName", null)
        val cityId = sharedPreferences.getString("cityId", null)
        val cityName = sharedPreferences.getString("cityName", null)
        val officeId = sharedPreferences.getString("officeId", null)
        val officeName = sharedPreferences.getString("officeName", null)
        val deviceName = sharedPreferences.getString("deviceName", "") ?: ""

        val initialProvince = if (provinceId != null && provinceName != null) ProvinceItem(provinceId, provinceName) else null
        val initialCity = if (cityId != null && cityName != null) CityItem(cityId, cityName) else null
        val initialOffice = if (officeId != null && officeName != null) OfficeItem(officeId, officeName) else null

        _uiState.update {
            it.copy(
                selectedProvince = initialProvince,
                selectedCity = initialCity,
                selectedOffice = initialOffice,
                selectedDeviceName = deviceName
            )
        }
        fetchProvinces() // 지역(도/광역시) 목록 로드
        if (initialProvince != null) {
            fetchCities(initialProvince.id) // 선택된 도/광역시가 있으면 시/군/구 로드
            if (initialCity != null) {
                fetchOffices(initialProvince.id, initialCity.id) // 선택된 시/군/구가 있으면 사무실 로드
            }
        }
        Log.d(TAG, "Loaded initial config: Province=$initialProvince, City=$initialCity, Office=$initialOffice, Device=$deviceName")
    }


    fun fetchProvinces() {
        _uiState.update { it.copy(isLoadingProvinces = true, error = null) }
        db.collection("provinces")
            .whereEqualTo("active", true)
            .orderBy("name")
            .get()
            .addOnSuccessListener { documents ->
                val provinceList = documents.map { doc ->
                    ProvinceItem(id = doc.id, name = doc.getString("name") ?: "")
                }
                _uiState.update {
                    it.copy(
                        provinces = provinceList,
                        isLoadingProvinces = false
                    )
                }
                Log.d(TAG, "Fetched provinces: ${provinceList.size} items")
            }
            .addOnFailureListener { exception ->
                _uiState.update { it.copy(isLoadingProvinces = false, error = "지역 정보를 가져오는데 실패했습니다: ${exception.message}") }
                Log.e(TAG, "Error fetching provinces", exception)
            }
    }

    fun fetchCities(provinceId: String) {
        _uiState.update { it.copy(isLoadingCities = true, error = null, cities = emptyList(), offices = emptyList()) }
        db.collection("provinces").document(provinceId).collection("cities")
            .whereEqualTo("active", true)
            .orderBy("name")
            .get()
            .addOnSuccessListener { documents ->
                val cityList = documents.map { doc ->
                    CityItem(id = doc.id, name = doc.getString("name") ?: "")
                }
                _uiState.update {
                    it.copy(
                        cities = cityList,
                        isLoadingCities = false
                    )
                }
                Log.d(TAG, "Fetched cities for province $provinceId: ${cityList.size} items")
            }
            .addOnFailureListener { exception ->
                _uiState.update { it.copy(isLoadingCities = false, error = "시/군/구 정보를 가져오는데 실패했습니다: ${exception.message}") }
                Log.e(TAG, "Error fetching cities for province $provinceId", exception)
            }
    }

    fun fetchOffices(provinceId: String, cityId: String) {
        _uiState.update { it.copy(isLoadingOffices = true, error = null, offices = emptyList()) }
        db.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices")
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
                Log.d(TAG, "Fetched offices for province $provinceId, city $cityId: ${officeList.size} items")
            }
            .addOnFailureListener { exception ->
                _uiState.update { it.copy(isLoadingOffices = false, error = "사무실 정보를 가져오는데 실패했습니다: ${exception.message}") }
                Log.e(TAG, "Error fetching offices for province $provinceId, city $cityId", exception)
            }
    }

    fun selectProvince(province: ProvinceItem) {
        _uiState.update {
            it.copy(
                selectedProvince = province,
                selectedCity = null, // 도/광역시 변경 시 시/군/구 선택 초기화
                selectedOffice = null, // 사무실 선택도 초기화
                cities = emptyList(), // 시/군/구 목록 초기화
                offices = emptyList() // 사무실 목록 초기화
            )
        }
        fetchCities(province.id)
    }

    fun selectCity(city: CityItem) {
        val province = _uiState.value.selectedProvince
        _uiState.update {
            it.copy(
                selectedCity = city,
                selectedOffice = null, // 시/군/구 변경 시 사무실 선택 초기화
                offices = emptyList() // 사무실 목록 초기화
            )
        }
        if (province != null) {
            fetchOffices(province.id, city.id)
        }
    }

    fun selectOffice(office: OfficeItem) {
        _uiState.update { it.copy(selectedOffice = office) }
    }

    fun selectDeviceName(name: String) {
        _uiState.update { it.copy(selectedDeviceName = name) }
    }

    fun saveSelection() {
        val province = _uiState.value.selectedProvince
        val city = _uiState.value.selectedCity
        val office = _uiState.value.selectedOffice
        val deviceName = _uiState.value.selectedDeviceName

        if (province != null && city != null && office != null && deviceName.isNotBlank()) {
            sharedPreferences.edit()
                .putString("provinceId", province.id)
                .putString("provinceName", province.name)
                .putString("cityId", city.id)
                .putString("cityName", city.name)
                .putString("officeId", office.id)
                .putString("officeName", office.name)
                .putString("deviceName", deviceName)
                .apply()
            _uiState.update { it.copy(saveSuccess = true, error = null) } // 저장 성공 상태 업데이트
             Log.d(TAG, "Saved selection: Province=${province.id}, City=${city.id}, Office=${office.id}, DeviceName=$deviceName")
            viewModelScope.launch { // 추가
                _onSettingsSaved.emit(true) // 저장 성공 이벤트 발생
            }
        } else {
            // 오류 상태 업데이트 (UI에서 메시지 표시용)
            _uiState.update { it.copy(error = "도/광역시, 시/군/구, 사무실, 전화기 이름을 모두 선택해주세요.", saveSuccess = false) }
            Log.e(TAG, "Error saving selection: Not all values selected. Province=$province, City=$city, Office=$office, DeviceName=$deviceName")
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

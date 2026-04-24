package com.designated.pickupdriver.ui.login

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.designated.pickupdriver.data.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProvinceItem(val id: String, val name: String)
data class CityItem(val id: String, val name: String)
data class OfficeItem(val id: String, val name: String)

sealed class SignUpState {
    object Idle : SignUpState()
    object LoadingProvinces : SignUpState()
    object LoadingCities : SignUpState()
    object LoadingOffices : SignUpState()
    object Loading : SignUpState()
    object Success : SignUpState()
    data class Error(val message: String) : SignUpState()
}

class SignUpViewModel(application: Application) : AndroidViewModel(application) {
    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var name by mutableStateOf("")
    var phoneNumber by mutableStateOf("")

    // 픽업기사앱: driverType 고정. PendingDriversViewModel 분기가 "픽업기사" → pickup_drivers 라우팅.
    private val driverType: String = Constants.DRIVER_TYPE_PICKUP

    private val _provinces = MutableStateFlow<List<ProvinceItem>>(emptyList())
    val provinces: StateFlow<List<ProvinceItem>> = _provinces.asStateFlow()

    private val _cities = MutableStateFlow<List<CityItem>>(emptyList())
    val cities: StateFlow<List<CityItem>> = _cities.asStateFlow()

    private val _offices = MutableStateFlow<List<OfficeItem>>(emptyList())
    val offices: StateFlow<List<OfficeItem>> = _offices.asStateFlow()

    var selectedProvince by mutableStateOf<ProvinceItem?>(null)
        private set

    var selectedCity by mutableStateOf<CityItem?>(null)
        private set

    var selectedOffice by mutableStateOf<OfficeItem?>(null)
        private set

    init {
        fetchProvinces()
    }

    private fun fetchProvinces() {
        _signUpState.value = SignUpState.LoadingProvinces
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection(Constants.COLLECTION_PROVINCES)
                    .whereEqualTo("active", true).get().await()
                _provinces.value = snapshot.documents.mapNotNull { doc ->
                    doc.getString("name")?.let { ProvinceItem(id = doc.id, name = it) }
                }.sortedBy { it.name }
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("시/도 목록 로드 실패: ${e.message}")
            }
        }
    }

    private fun fetchCities(provinceId: String) {
        _signUpState.value = SignUpState.LoadingCities
        _cities.value = emptyList()
        _offices.value = emptyList()
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).whereEqualTo("active", true).get().await()
                _cities.value = snapshot.documents.mapNotNull { doc ->
                    doc.getString("name")?.let { CityItem(id = doc.id, name = it) }
                }.sortedBy { it.name }
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("시/군/구 목록 로드 실패: ${e.message}")
            }
        }
    }

    private fun fetchOffices(provinceId: String, cityId: String) {
        _signUpState.value = SignUpState.LoadingOffices
        _offices.value = emptyList()
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                    .collection(Constants.COLLECTION_CITIES).document(cityId)
                    .collection(Constants.COLLECTION_OFFICES).get().await()
                _offices.value = snapshot.documents.mapNotNull { doc ->
                    doc.getString("name")?.let { OfficeItem(id = doc.id, name = it) }
                }.sortedBy { it.name }
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("사무실 목록 로드 실패: ${e.message}")
            }
        }
    }

    fun onProvinceSelected(province: ProvinceItem) {
        selectedProvince = province
        selectedCity = null
        selectedOffice = null
        fetchCities(province.id)
    }

    fun onCitySelected(city: CityItem) {
        selectedCity = city
        selectedOffice = null
        selectedProvince?.let { province -> fetchOffices(province.id, city.id) }
    }

    fun onOfficeSelected(office: OfficeItem) {
        selectedOffice = office
    }

    fun signUp() {
        if (email.isBlank() || password.isBlank() || confirmPassword.isBlank() || name.isBlank() || phoneNumber.isBlank()) {
            _signUpState.value = SignUpState.Error("모든 필드를 입력해주세요.")
            return
        }
        if (password != confirmPassword) {
            _signUpState.value = SignUpState.Error("비밀번호가 일치하지 않습니다.")
            return
        }
        if (selectedProvince == null) {
            _signUpState.value = SignUpState.Error("시/도를 선택해주세요.")
            return
        }
        if (selectedCity == null) {
            _signUpState.value = SignUpState.Error("시/군/구를 선택해주세요.")
            return
        }
        if (selectedOffice == null) {
            _signUpState.value = SignUpState.Error("사무실을 선택해주세요.")
            return
        }

        _signUpState.value = SignUpState.Loading
        viewModelScope.launch {
            try {
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid ?: throw IllegalStateException("Auth UID is null after creation.")

                val pendingDriverData = hashMapOf(
                    "authUid" to userId,
                    "name" to name,
                    "phoneNumber" to phoneNumber,
                    "email" to email,
                    "driverType" to driverType,
                    "targetProvinceId" to selectedProvince!!.id,
                    "targetCityId" to selectedCity!!.id,
                    "targetOfficeId" to selectedOffice!!.id,
                    "status" to "승인대기중",
                    "requestedAt" to com.google.firebase.Timestamp.now()
                )

                firestore.collection(Constants.COLLECTION_PENDING_DRIVERS).document(userId)
                    .set(pendingDriverData).await()

                _signUpState.value = SignUpState.Success
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "이미 사용 중인 이메일입니다."
                    is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "비밀번호가 너무 약합니다."
                    else -> e.localizedMessage ?: "알 수 없는 오류가 발생했습니다."
                }
                _signUpState.value = SignUpState.Error(errorMessage)
            }
        }
    }

    fun resetSignUpState() {
        _signUpState.value = SignUpState.Idle
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(SignUpViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return SignUpViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

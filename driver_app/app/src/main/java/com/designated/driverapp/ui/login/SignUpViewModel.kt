package com.designated.driverapp.ui.login

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

data class RegionItem(val id: String, val name: String)
data class OfficeItem(val id: String, val name: String)

sealed class SignUpState {
    object Idle : SignUpState()
    object LoadingRegions : SignUpState()
    object LoadingOffices : SignUpState()
    object Loading : SignUpState()
    object Success : SignUpState() // 성공 시 "승인 대기" 상태임을 안내
    data class Error(val message: String) : SignUpState()
}

class SignUpViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SignUpViewModel"

    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var name by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    var driverType by mutableStateOf("대리기사")

    private val _regions = MutableStateFlow<List<RegionItem>>(emptyList())
    val regions: StateFlow<List<RegionItem>> = _regions.asStateFlow()

    private val _offices = MutableStateFlow<List<OfficeItem>>(emptyList())
    val offices: StateFlow<List<OfficeItem>> = _offices.asStateFlow()

    var selectedRegion by mutableStateOf<RegionItem?>(null)
        private set

    var selectedOffice by mutableStateOf<OfficeItem?>(null)
        private set

    init {
        fetchRegions()
    }

    private fun fetchRegions() {
        _signUpState.value = SignUpState.LoadingRegions
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("regions").get().await()
                val regionList = snapshot.documents.mapNotNull { doc ->
                    val regionName = doc.getString("name")
                    if (regionName != null) {
                        RegionItem(id = doc.id, name = regionName)
                    } else {
                        null
                    }
                }.sortedBy { it.name }
                _regions.value = regionList
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("지역 목록 로드 실패: ${e.message}")
            }
        }
    }

    private fun fetchOffices(regionId: String) {
        _signUpState.value = SignUpState.LoadingOffices
        _offices.value = emptyList()
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("regions").document(regionId)
                    .collection("offices").get().await()
                val officeList = snapshot.documents.mapNotNull { doc ->
                    val officeName = doc.getString("name")
                    if (officeName != null) {
                        OfficeItem(id = doc.id, name = officeName)
                    } else {
                        null
                    }
                }.sortedBy { it.name }
                _offices.value = officeList
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("사무실 목록 로드 실패: ${e.message}")
            }
        }
    }

    fun onRegionSelected(region: RegionItem) {
        selectedRegion = region
        selectedOffice = null
        fetchOffices(region.id)
    }

    fun onOfficeSelected(office: OfficeItem) {
        selectedOffice = office
    }

    fun signUp() {

        if (/* ...기본 필드 검증... */ email.isBlank() || password.isBlank() || confirmPassword.isBlank() || name.isBlank() || phoneNumber.isBlank()) {
            _signUpState.value = SignUpState.Error("모든 필드를 입력해주세요.")
            return
        }
        if (selectedRegion == null) {
             _signUpState.value = SignUpState.Error("지역을 선택해주세요.")
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
                    "targetRegionId" to selectedRegion!!.id,
                    "targetOfficeId" to selectedOffice!!.id,
                    "requestedAt" to com.google.firebase.Timestamp.now()
                )

                val pendingDriverDocRef = firestore.collection("pending_drivers").document(userId)
                pendingDriverDocRef.set(pendingDriverData).await()

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
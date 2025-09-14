package com.designated.callmanager.ui.signup

import android.app.Application
import android.util.Patterns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// TODO: Implement SignUpViewModel logic

data class RegionItem(val id: String, val name: String)
data class OfficeItem(val id: String, val name: String)

sealed class SignUpState {
    object Idle : SignUpState()
    object LoadingRegions : SignUpState()
    object LoadingOffices : SignUpState()
    object Loading : SignUpState()
    object Success : SignUpState()
    data class Error(val message: String) : SignUpState()
}

class SignUpViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var adminName by mutableStateOf("")

    private val _regions = MutableStateFlow<List<RegionItem>>(emptyList())
    val regions: StateFlow<List<RegionItem>> = _regions.asStateFlow()

    private val _offices = MutableStateFlow<List<OfficeItem>>(emptyList())
    val offices: StateFlow<List<OfficeItem>> = _offices.asStateFlow()

    var selectedRegion by mutableStateOf<RegionItem?>(null)
        private set

    var selectedOffice by mutableStateOf<OfficeItem?>(null)
        private set

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    init {
        fetchRegions()
    }

    fun onRegionSelected(region: RegionItem) {
        selectedRegion = region
        selectedOffice = null
        fetchOffices(region.id)
    }

    fun onOfficeSelected(office: OfficeItem) {
        selectedOffice = office
    }

    private fun fetchRegions() {
        _signUpState.value = SignUpState.LoadingRegions
        viewModelScope.launch {
            try {
                val snapshot = db.collection("regions").get().await()
                val regionList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name")
                    if (name != null) {
                        RegionItem(id = doc.id, name = name)
                    } else {
                        null
                    }
                }.sortedBy { it.name }
                _regions.value = regionList
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("지역 목록을 불러오는데 실패했습니다: ${e.message}")
            }
        }
    }

    private fun fetchOffices(regionId: String) {
        _signUpState.value = SignUpState.LoadingOffices
        _offices.value = emptyList()
        viewModelScope.launch {
            try {
                val snapshot = db.collection("regions").document(regionId)
                                .collection("offices").get().await()
                val officeList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name")
                    if (name != null) {
                        OfficeItem(id = doc.id, name = name)
                    } else {
                        null
                    }
                }.sortedBy { it.name }
                _offices.value = officeList
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("사무실 목록을 불러오는데 실패했습니다: ${e.message}")
            }
        }
    }

    fun signUp() {
        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _signUpState.value = SignUpState.Error("올바른 이메일 주소를 입력해주세요.")
            return
        }
        if (password.length < 6) {
            _signUpState.value = SignUpState.Error("비밀번호는 6자 이상 입력해주세요.")
            return
        }
        if (password != confirmPassword) {
            _signUpState.value = SignUpState.Error("비밀번호가 일치하지 않습니다.")
            return
        }
        if (adminName.isBlank()) {
            _signUpState.value = SignUpState.Error("이름을 입력해주세요.")
            return
        }
        val currentSelectedRegion = selectedRegion
        if (currentSelectedRegion == null) {
            _signUpState.value = SignUpState.Error("지역을 선택해주세요.")
            return
        }
        val currentSelectedOffice = selectedOffice
        if (currentSelectedOffice == null) {
            _signUpState.value = SignUpState.Error("사무실을 선택해주세요.")
            return
        }

        _signUpState.value = SignUpState.Loading
        viewModelScope.launch {
            try {
                val officeIdToCheck = currentSelectedOffice.id
                val existingAdminQuery = db.collection("admins")
                    .whereEqualTo("associatedOfficeId", officeIdToCheck)
                    .limit(1)
                    .get()
                    .await()

                if (!existingAdminQuery.isEmpty) {
                    _signUpState.value = SignUpState.Error("선택하신 사무실에는 이미 관리자가 등록되어 있습니다.")
                    return@launch // 코루틴 종료
                }

                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val newUser = authResult.user

                if (newUser != null) {
                    val adminData = hashMapOf(
                        "email" to email,
                        "name" to adminName,
                        "associatedRegionId" to currentSelectedRegion.id,
                        "associatedOfficeId" to officeIdToCheck,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("admins").document(newUser.uid).set(adminData).await()

                    _signUpState.value = SignUpState.Success
                } else {
                    _signUpState.value = SignUpState.Error("사용자 생성에 실패했습니다.")
                }

            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("회원가입 중 오류 발생: ${e.message}")
            }
        }
    }

    fun resetSignUpState(){
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
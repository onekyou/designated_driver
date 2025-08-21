package com.designated.callmanager.ui.signup

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
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// TODO: Implement SignUpViewModel logic

// Data classes for dropdown items
data class RegionItem(val id: String, val name: String)
data class OfficeItem(val id: String, val name: String)

// State for the sign-up process
sealed class SignUpState {
    object Idle : SignUpState()
    object LoadingRegions : SignUpState() // 지역 로딩 중 상태 추가
    object LoadingOffices : SignUpState() // 사무실 로딩 중 상태 추가
    object Loading : SignUpState() // 회원가입 진행 중
    object Success : SignUpState()
    data class Error(val message: String) : SignUpState()
}

class SignUpViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = Firebase.auth
    private val db = Firebase.firestore

    // --- Input States --- 
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var adminName by mutableStateOf("") // 관리자 이름 상태 추가

    // --- Region/Office Selection States --- 
    private val _regions = MutableStateFlow<List<RegionItem>>(emptyList())
    val regions: StateFlow<List<RegionItem>> = _regions.asStateFlow()

    private val _offices = MutableStateFlow<List<OfficeItem>>(emptyList())
    val offices: StateFlow<List<OfficeItem>> = _offices.asStateFlow()

    var selectedRegion by mutableStateOf<RegionItem?>(null)
        private set // 외부에서는 변경 불가

    var selectedOffice by mutableStateOf<OfficeItem?>(null)
        private set

    // --- Sign-Up Process State --- 
    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    init {
        fetchRegions()
    }

    fun onRegionSelected(region: RegionItem) {
        selectedRegion = region
        selectedOffice = null // 지역 변경 시 사무실 선택 초기화
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
                        Log.w("SignUpViewModel", "Region document ${doc.id} is missing 'name' field.")
                        null
                    }
                }.sortedBy { it.name } // 이름순 정렬
                _regions.value = regionList
                _signUpState.value = SignUpState.Idle // 로딩 완료 후 Idle 상태로
                 Log.d("SignUpViewModel", "Regions fetched: ${regionList.size}")
            } catch (e: Exception) {
                Log.e("SignUpViewModel", "Error fetching regions", e)
                _signUpState.value = SignUpState.Error("지역 목록을 불러오는데 실패했습니다: ${e.message}")
            }
        }
    }

    private fun fetchOffices(regionId: String) {
        _signUpState.value = SignUpState.LoadingOffices
        _offices.value = emptyList() // 사무실 목록 초기화
        viewModelScope.launch {
            try {
                val snapshot = db.collection("regions").document(regionId)
                                .collection("offices").get().await()
                val officeList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name")
                    if (name != null) {
                        OfficeItem(id = doc.id, name = name)
                    } else {
                        Log.w("SignUpViewModel", "Office document ${doc.id} in region $regionId is missing 'name' field.")
                        null
                    }
                }.sortedBy { it.name }
                _offices.value = officeList
                _signUpState.value = SignUpState.Idle
                Log.d("SignUpViewModel", "Offices fetched for region $regionId: ${officeList.size}")
            } catch (e: Exception) {
                Log.e("SignUpViewModel", "Error fetching offices for region $regionId", e)
                _signUpState.value = SignUpState.Error("사무실 목록을 불러오는데 실패했습니다: ${e.message}")
            }
        }
    }

    fun signUp() {
        // --- Input Validation --- 
        if (email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _signUpState.value = SignUpState.Error("올바른 이메일 주소를 입력해주세요.")
            return
        }
        if (password.length < 6) { // Firebase 최소 비밀번호 길이
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
        val currentSelectedRegion = selectedRegion // Null check용 로컬 변수
        if (currentSelectedRegion == null) {
            _signUpState.value = SignUpState.Error("지역을 선택해주세요.")
            return
        }
        val currentSelectedOffice = selectedOffice // Null check용 로컬 변수
        if (currentSelectedOffice == null) {
            _signUpState.value = SignUpState.Error("사무실을 선택해주세요.")
            return
        }
        // --- --- 

        _signUpState.value = SignUpState.Loading // 회원가입 시작
        viewModelScope.launch {
            try {
                // 0. 선택된 사무실에 이미 관리자가 있는지 확인
                val officeIdToCheck = currentSelectedOffice.id
                val existingAdminQuery = db.collection("admins")
                    .whereEqualTo("associatedOfficeId", officeIdToCheck)
                    .limit(1) // 하나만 찾으면 됨
                    .get()
                    .await()

                if (!existingAdminQuery.isEmpty) {
                    // 이미 해당 사무실에 관리자가 존재함
                    Log.w("SignUpViewModel", "Sign up failed: Admin already exists for office ID $officeIdToCheck")
                    _signUpState.value = SignUpState.Error("선택하신 사무실에는 이미 관리자가 등록되어 있습니다.")
                    return@launch // 코루틴 종료
                }

                // 1. Firebase Authentication 사용자 생성
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val newUser = authResult.user

                if (newUser != null) {
                    Log.d("SignUpViewModel", "Firebase Auth user created: ${newUser.uid}")
                    // 2. Firestore '/admins' 컬렉션에 관리자 정보 저장
                    val adminData = hashMapOf(
                        "email" to email,
                        "name" to adminName,
                        "associatedRegionId" to currentSelectedRegion.id, // 로컬 변수 사용
                        "associatedOfficeId" to officeIdToCheck, // 확인된 사무실 ID 사용
                        "createdAt" to com.google.firebase.Timestamp.now() // 생성 시간 기록
                        // 필요시 추가 정보 (예: role)
                    )
                    db.collection("admins").document(newUser.uid).set(adminData).await()
                    Log.d("SignUpViewModel", "Admin data saved to Firestore for UID: ${newUser.uid}")
                    
                    _signUpState.value = SignUpState.Success // 최종 성공
                } else {
                     Log.e("SignUpViewModel", "Error: Firebase Auth user creation returned null user.")
                    _signUpState.value = SignUpState.Error("사용자 생성에 실패했습니다.")
                }

            } catch (e: Exception) {
                 Log.e("SignUpViewModel", "Error during sign up process", e)
                // Firebase 관련 예외 처리 등
                 // Firestore 쿼리 실패 또는 Auth 생성 실패 등 모든 예외 포함
                _signUpState.value = SignUpState.Error("회원가입 중 오류 발생: ${e.message}")
            }
        }
    }
    
    // 회원가입 상태 초기화 (오류 메시지 확인 후 등)
    fun resetSignUpState(){
        _signUpState.value = SignUpState.Idle
    }

    // Factory class
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
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

// --- 데이터 클래스 추가 ---
data class RegionItem(val id: String, val name: String)
data class OfficeItem(val id: String, val name: String)
// --- ---

sealed class SignUpState {
    object Idle : SignUpState()
    object LoadingRegions : SignUpState() // 지역 로딩 상태 추가
    object LoadingOffices : SignUpState() // 사무실 로딩 상태 추가
    object Loading : SignUpState() // 회원가입 처리 중
    object Success : SignUpState() // 성공 시 "승인 대기" 상태임을 안내
    data class Error(val message: String) : SignUpState()
}

class SignUpViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SignUpViewModel"

    private val auth: FirebaseAuth = Firebase.auth
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow() // asStateFlow() 추가

    // --- 입력 필드 (동일) ---
    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var name by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    var driverType by mutableStateOf("대리기사") // 기본값

    // --- 지역/사무실 선택 상태 추가 ---
    private val _regions = MutableStateFlow<List<RegionItem>>(emptyList())
    val regions: StateFlow<List<RegionItem>> = _regions.asStateFlow()

    private val _offices = MutableStateFlow<List<OfficeItem>>(emptyList())
    val offices: StateFlow<List<OfficeItem>> = _offices.asStateFlow()

    var selectedRegion by mutableStateOf<RegionItem?>(null)
        private set // 외부 변경 방지

    var selectedOffice by mutableStateOf<OfficeItem?>(null)
        private set
    // --- ---

    init {
        fetchRegions() // ViewModel 생성 시 지역 목록 로드
    }

    // --- 지역/사무실 목록 가져오기 함수 추가 ---
    private fun fetchRegions() {
        Log.d(TAG, "fetchRegions: 시작됨") // 로그 추가
        _signUpState.value = SignUpState.LoadingRegions
        Log.d(TAG, "fetchRegions: 상태 변경 -> LoadingRegions") // 로그 추가
        viewModelScope.launch {
            try {
                Log.d(TAG, "fetchRegions: Firestore 쿼리 시도 (regions)") // 로그 추가
                val snapshot = firestore.collection("regions").get().await()
                Log.d(TAG, "fetchRegions: Firestore 쿼리 성공. 문서 개수: ${snapshot.size()}") // 로그 추가
                val regionList = snapshot.documents.mapNotNull { doc ->
                    val regionName = doc.getString("name") // 지역 이름 필드 가정
                    if (regionName != null) {
                        RegionItem(id = doc.id, name = regionName)
                    } else {
                        Log.w(TAG, "Region document ${doc.id} is missing 'name' field.")
                        null
                    }
                }.sortedBy { it.name }
                _regions.value = regionList
                Log.d(TAG, "fetchRegions: 지역 목록 업데이트 완료 (${regionList.size}개)") // 로그 추가
                _signUpState.value = SignUpState.Idle
                Log.d(TAG, "fetchRegions: 상태 변경 -> Idle") // 로그 추가
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching regions", e)
                _signUpState.value = SignUpState.Error("지역 목록 로드 실패: ${e.message}")
                Log.d(TAG, "fetchRegions: 상태 변경 -> Error") // 로그 추가
            }
        }
    }

    private fun fetchOffices(regionId: String) {
        Log.d(TAG, "fetchOffices: 시작됨 (Region ID: $regionId)") // 로그 추가
        _signUpState.value = SignUpState.LoadingOffices
        Log.d(TAG, "fetchOffices: 상태 변경 -> LoadingOffices") // 로그 추가
        _offices.value = emptyList() // 사무실 목록 초기화
        viewModelScope.launch {
            try {
                Log.d(TAG, "fetchOffices: Firestore 쿼리 시도 (regions/$regionId/offices)") // 로그 추가
                val snapshot = firestore.collection("regions").document(regionId)
                    .collection("offices").get().await()
                Log.d(TAG, "fetchOffices: Firestore 쿼리 성공. 문서 개수: ${snapshot.size()}") // 로그 추가
                val officeList = snapshot.documents.mapNotNull { doc ->
                    val officeName = doc.getString("name") // 사무실 이름 필드 가정
                    if (officeName != null) {
                        OfficeItem(id = doc.id, name = officeName)
                    } else {
                        Log.w(TAG, "Office document ${doc.id} in region $regionId is missing 'name' field.")
                        null
                    }
                }.sortedBy { it.name }
                _offices.value = officeList
                Log.d(TAG, "fetchOffices: 사무실 목록 업데이트 완료 (${officeList.size}개)") // 로그 추가
                _signUpState.value = SignUpState.Idle
                Log.d(TAG, "fetchOffices: 상태 변경 -> Idle") // 로그 추가
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching offices for region $regionId", e)
                _signUpState.value = SignUpState.Error("사무실 목록 로드 실패: ${e.message}")
                Log.d(TAG, "fetchOffices: 상태 변경 -> Error") // 로그 추가
            }
        }
    }

    fun onRegionSelected(region: RegionItem) {
        selectedRegion = region
        selectedOffice = null // 지역 변경 시 사무실 선택 초기화
        fetchOffices(region.id)
    }

    fun onOfficeSelected(office: OfficeItem) {
        selectedOffice = office
    }
    // --- ---

    fun signUp() {
        Log.d(TAG, "회원가입 시도: 이메일=$email, 이름=$name")

        // --- 입력값 검증 (지역/사무실 선택 포함) ---
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
        // ... (다른 검증 로직) ...

        _signUpState.value = SignUpState.Loading
        viewModelScope.launch {
            try {
                // 1. Firebase Authentication 사용자 생성 (동일)
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val userId = authResult.user?.uid ?: throw IllegalStateException("Auth UID is null after creation.")
                Log.d(TAG, "Firebase Auth 사용자 생성 성공: UID=$userId")

                // 2. Firestore 최종 경로에 기사 정보 저장
                val pendingDriverData = hashMapOf(
                    "authUid" to userId,
                    "name" to name,
                    "phoneNumber" to phoneNumber, // 필드 이름 일관성 확인 필요
                    "email" to email,
                    "driverType" to driverType,
                    "targetRegionId" to selectedRegion!!.id, // ★★★ 필드명 변경 ★★★
                    "targetOfficeId" to selectedOffice!!.id, // ★★★ 필드명 변경 ★★★
                    "requestedAt" to com.google.firebase.Timestamp.now() // ★★★ 필드명 변경 ★★★
                    // 초기 평점 등 필요한 필드 추가 가능
                )

                // ★★★ 저장 경로 변경: pending_drivers 컬렉션 사용 ★★★
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(userId) // 문서 ID로 Auth UID 사용
                pendingDriverDocRef.set(pendingDriverData).await()

                Log.d(TAG, "Firestore 승인 대기 정보 저장 성공: Path=${pendingDriverDocRef.path}")
                _signUpState.value = SignUpState.Success // 성공 (승인 대기)

            } catch (e: Exception) {
                 // ... (오류 처리 - 동일) ...
                 Log.e(TAG, "회원가입 중 오류 발생", e)
                 val errorMessage = when (e) {
                      is com.google.firebase.auth.FirebaseAuthUserCollisionException -> "이미 사용 중인 이메일입니다."
                      is com.google.firebase.auth.FirebaseAuthWeakPasswordException -> "비밀번호가 너무 약합니다."
                       // Firestore 관련 오류 (예: 권한) 처리 추가 가능
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
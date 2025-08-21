package com.designated.pickupapp.ui.signup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupapp.data.RegionItem
import com.designated.pickupapp.data.OfficeItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

sealed class SignUpState {
    object Idle : SignUpState()
    object LoadingRegions : SignUpState()
    object LoadingOffices : SignUpState()
    object Loading : SignUpState()
    object Success : SignUpState()
    data class Error(val message: String) : SignUpState()
}

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    var email by mutableStateOf("")
    var password by mutableStateOf("")
    var confirmPassword by mutableStateOf("")
    var name by mutableStateOf("")
    var phoneNumber by mutableStateOf("")
    
    var selectedRegion by mutableStateOf<RegionItem?>(null)
        private set
    var selectedOffice by mutableStateOf<OfficeItem?>(null)
        private set

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState
    
    private val _regions = MutableStateFlow<List<RegionItem>>(emptyList())
    val regions: StateFlow<List<RegionItem>> = _regions
    
    private val _offices = MutableStateFlow<List<OfficeItem>>(emptyList())
    val offices: StateFlow<List<OfficeItem>> = _offices

    init {
        android.util.Log.d("SignUpViewModel", "ViewModel 초기화 시작")
        android.util.Log.d("SignUpViewModel", "Firebase 인스턴스 확인: $firestore")
        fetchRegions() // 기사앱과 동일한 메소드명 사용
    }

    private fun fetchRegions() {
        android.util.Log.d("SignUpViewModel", "fetchRegions() 메소드 진입")
        _signUpState.value = SignUpState.LoadingRegions
        android.util.Log.d("SignUpViewModel", "상태를 LoadingRegions로 변경")
        
        viewModelScope.launch {
            try {
                android.util.Log.d("SignUpViewModel", "지역 목록 로딩 시작 - Firebase 접근 시도")
                android.util.Log.d("SignUpViewModel", "Firestore 컬렉션 경로: regions")
                
                val snapshot = firestore.collection("regions").get().await()
                android.util.Log.d("SignUpViewModel", "Firebase 응답 성공! 지역 문서 개수: ${snapshot.documents.size}")
                
                if (snapshot.documents.isEmpty()) {
                    android.util.Log.w("SignUpViewModel", "경고: regions 컬렉션이 비어있습니다!")
                }
                
                val regionList = snapshot.documents.mapNotNull { doc ->
                    val regionName = doc.getString("name")
                    android.util.Log.d("SignUpViewModel", "지역 처리 중: ID=${doc.id}, name=$regionName, 전체 데이터=${doc.data}")
                    if (regionName != null) {
                        RegionItem(id = doc.id, name = regionName)
                    } else {
                        android.util.Log.w("SignUpViewModel", "지역 이름이 null: ${doc.id}, 전체 필드: ${doc.data?.keys}")
                        null
                    }
                }.sortedBy { it.name }
                
                android.util.Log.d("SignUpViewModel", "최종 지역 리스트 크기: ${regionList.size}")
                regionList.forEach { region ->
                    android.util.Log.d("SignUpViewModel", "최종 리스트 항목: ${region.id} - ${region.name}")
                }
                
                _regions.value = regionList
                android.util.Log.d("SignUpViewModel", "regions StateFlow 업데이트 완료")
                _signUpState.value = SignUpState.Idle
                android.util.Log.d("SignUpViewModel", "상태를 Idle로 변경 완료")
                
            } catch (e: Exception) {
                android.util.Log.e("SignUpViewModel", "지역 로딩 실패 - 예외 발생", e)
                android.util.Log.e("SignUpViewModel", "예외 타입: ${e.javaClass.simpleName}")
                android.util.Log.e("SignUpViewModel", "예외 메시지: ${e.message}")
                android.util.Log.e("SignUpViewModel", "스택 트레이스: ${e.stackTrace.contentToString()}")
                _signUpState.value = SignUpState.Error("지역 목록 로드 실패: ${e.message}")
            }
        }
    }

    private fun fetchOffices(regionId: String) {
        _signUpState.value = SignUpState.LoadingOffices
        _offices.value = emptyList() // 사무실 목록 초기화
        viewModelScope.launch {
            try {
                android.util.Log.d("SignUpViewModel", "사무실 로딩 시작: regionId=$regionId")
                
                val snapshot = firestore.collection("regions").document(regionId)
                    .collection("offices").get().await()
                
                android.util.Log.d("SignUpViewModel", "사무실 문서 개수: ${snapshot.documents.size}")
                
                val officeList = snapshot.documents.mapNotNull { doc ->
                    val officeName = doc.getString("name")
                    android.util.Log.d("SignUpViewModel", "사무실 발견: ID=${doc.id}, name=$officeName")
                    if (officeName != null) {
                        OfficeItem(id = doc.id, name = officeName, regionId = regionId)
                    } else {
                        android.util.Log.w("SignUpViewModel", "사무실 이름이 null: ${doc.id}")
                        null
                    }
                }.sortedBy { it.name }
                
                android.util.Log.d("SignUpViewModel", "최종 사무실 리스트 크기: ${officeList.size}")
                _offices.value = officeList
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                android.util.Log.e("SignUpViewModel", "사무실 로딩 실패", e)
                _signUpState.value = SignUpState.Error("사무실 목록 로드 실패: ${e.message}")
            }
        }
    }

    fun onRegionSelected(region: RegionItem) {
        selectedRegion = region
        selectedOffice = null
        _offices.value = emptyList()
        fetchOffices(region.id)
    }

    fun onOfficeSelected(office: OfficeItem) {
        selectedOffice = office
    }

    fun signUp() {
        // Validation
        if (email.isBlank() || password.isBlank() || name.isBlank() || phoneNumber.isBlank()) {
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

        if (password != confirmPassword) {
            _signUpState.value = SignUpState.Error("비밀번호가 일치하지 않습니다.")
            return
        }

        if (password.length < 6) {
            _signUpState.value = SignUpState.Error("비밀번호는 최소 6자 이상이어야 합니다.")
            return
        }

        viewModelScope.launch {
            _signUpState.value = SignUpState.Loading
            try {
                android.util.Log.w("SignUpViewModel", "회원가입 시작")
                
                // Firebase Auth 계정 생성
                android.util.Log.w("SignUpViewModel", "Firebase Auth 계정 생성 시작")
                val authResult = try {
                    firebaseAuth.createUserWithEmailAndPassword(email, password).await()
                } catch (authError: Exception) {
                    android.util.Log.e("SignUpViewModel", "Firebase Auth 실패", authError)
                    throw authError
                }
                val uid = authResult.user?.uid ?: throw Exception("계정 생성 실패")
                android.util.Log.w("SignUpViewModel", "Firebase Auth 계정 생성 성공 - UID: $uid")

                // pending_drivers 컬렉션에 대기 정보 저장 (기사앱과 통합 관리)
                val pendingData = hashMapOf(
                    "name" to name,
                    "phoneNumber" to phoneNumber,
                    "email" to email,
                    "authUid" to uid,
                    "targetRegionId" to selectedRegion!!.id,
                    "targetOfficeId" to selectedOffice!!.id,
                    "createdAt" to com.google.firebase.Timestamp.now(),
                    "status" to "PENDING",
                    "driverType" to "PICKUP" // 픽업 기사로 구분
                )
                
                android.util.Log.w("SignUpViewModel", "pending_drivers 저장 시작 - 데이터: $pendingData")
                android.util.Log.w("SignUpViewModel", "Firestore 인스턴스: $firestore")
                android.util.Log.w("SignUpViewModel", "저장 경로: pending_drivers/$uid")

                android.util.Log.w("SignUpViewModel", "Firestore 저장 시작...")
                
                // Firebase Auth 상태 확인
                val currentUser = firebaseAuth.currentUser
                android.util.Log.w("SignUpViewModel", "현재 로그인 사용자: $currentUser")
                android.util.Log.w("SignUpViewModel", "현재 사용자 UID: ${currentUser?.uid}")
                android.util.Log.w("SignUpViewModel", "저장하려는 문서 UID: $uid")
                android.util.Log.w("SignUpViewModel", "UID 일치 여부: ${currentUser?.uid == uid}")
                
                try {
                    val docRef = firestore.collection("pending_drivers").document(uid)
                    android.util.Log.w("SignUpViewModel", "DocumentRef: $docRef")
                    android.util.Log.w("SignUpViewModel", "DocumentRef path: ${docRef.path}")
                    
                    docRef.set(pendingData).await()
                    android.util.Log.w("SignUpViewModel", "pending_drivers 저장 성공!")
                } catch (firestoreError: Exception) {
                    android.util.Log.e("SignUpViewModel", "Firestore 저장 실패 - 예외 타입: ${firestoreError.javaClass.simpleName}")
                    android.util.Log.e("SignUpViewModel", "Firestore 저장 실패 - 메시지: ${firestoreError.message}")
                    android.util.Log.e("SignUpViewModel", "Firestore 저장 실패", firestoreError)
                    throw firestoreError
                }

                _signUpState.value = SignUpState.Success
                android.util.Log.w("SignUpViewModel", "회원가입 완료")
            } catch (e: Exception) {
                android.util.Log.e("SignUpViewModel", "회원가입 실패 - 전체 예외 정보", e)
                android.util.Log.e("SignUpViewModel", "예외 타입: ${e.javaClass.simpleName}")
                android.util.Log.e("SignUpViewModel", "예외 메시지: ${e.message}")
                android.util.Log.e("SignUpViewModel", "스택 트레이스: ${e.stackTrace.contentToString()}")
                
                val errorMessage = when {
                    e.message?.contains("email-already-in-use") == true -> 
                        "이미 사용 중인 이메일입니다."
                    e.message?.contains("weak-password") == true -> 
                        "비밀번호가 너무 약합니다."
                    e.message?.contains("invalid-email") == true -> 
                        "유효하지 않은 이메일 형식입니다."
                    e.message?.contains("PERMISSION_DENIED") == true ->
                        "Firebase 권한 오류 - 보안 규칙 확인 필요: ${e.message}"
                    e.message?.contains("UNAVAILABLE") == true ->
                        "Firebase 연결 실패 - 네트워크 확인: ${e.message}"
                    else -> "회원가입 실패: ${e.message}"
                }
                android.util.Log.e("SignUpViewModel", "최종 에러 메시지: $errorMessage")
                _signUpState.value = SignUpState.Error(errorMessage)
            }
        }
    }
}
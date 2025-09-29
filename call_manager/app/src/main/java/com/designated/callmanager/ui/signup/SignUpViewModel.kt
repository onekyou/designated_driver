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

sealed class SignUpState {
    object Idle : SignUpState()
    object LoadingRegions : SignUpState()
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
    var officeName by mutableStateOf("")

    private val _regions = MutableStateFlow<List<RegionItem>>(emptyList())
    val regions: StateFlow<List<RegionItem>> = _regions.asStateFlow()

    var selectedRegion by mutableStateOf<RegionItem?>(null)
        private set

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    init {
        fetchRegions()
    }

    fun onRegionSelected(region: RegionItem) {
        selectedRegion = region
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
        if (officeName.isBlank()) {
            _signUpState.value = SignUpState.Error("사무실 이름을 입력해주세요.")
            return
        }

        _signUpState.value = SignUpState.Loading
        viewModelScope.launch {
            try {
                // 1. Firebase Auth 계정 생성
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val newUser = authResult.user

                if (newUser != null) {
                    // 2. 새 사무실 문서 생성
                    val officeRef = db.collection("regions")
                        .document(currentSelectedRegion.id)
                        .collection("offices")
                        .document() // 자동 ID 생성

                    val officeData = hashMapOf(
                        "name" to officeName,
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "createdBy" to newUser.uid
                    )
                    officeRef.set(officeData).await()

                    val newOfficeId = officeRef.id

                    // 3. 관리자 정보 저장
                    val adminData = hashMapOf(
                        "email" to email,
                        "name" to adminName,
                        "associatedRegionId" to currentSelectedRegion.id,
                        "associatedOfficeId" to newOfficeId,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("admins").document(newUser.uid).set(adminData).await()

                    // 4. QR 코드 자동 생성 및 저장
                    generateAndSaveQRCode(currentSelectedRegion.id, newOfficeId)

                    _signUpState.value = SignUpState.Success
                } else {
                    _signUpState.value = SignUpState.Error("사용자 생성에 실패했습니다.")
                }

            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("회원가입 중 오류 발생: ${e.message}")
            }
        }
    }

    private suspend fun generateAndSaveQRCode(regionId: String, officeId: String) {
        try {
            // 랜딩 페이지 URL 생성 (Vercel 배포 주소)
            val landingPageUrl = "https://designated-driver.vercel.app/$regionId/$officeId"

            // QR 코드 데이터 = 랜딩 페이지 URL (동일하게)
            val qrData = landingPageUrl

            // OfficeSettings 문서에 QR 코드 및 랜딩 페이지 URL 저장
            val settingsData = hashMapOf(
                "qrCode" to qrData,
                "landingPageUrl" to landingPageUrl,
                "attributionThreshold" to 70, // 기본 임계값
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("settings")
                .document("attribution")
                .set(settingsData)
                .await()

            android.util.Log.d("SignUpViewModel", "QR 코드 자동 생성 완료: $qrData")

        } catch (e: Exception) {
            android.util.Log.e("SignUpViewModel", "QR 코드 생성 실패: ${e.message}")
            // QR 생성 실패는 회원가입 자체를 실패로 처리하지 않음
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
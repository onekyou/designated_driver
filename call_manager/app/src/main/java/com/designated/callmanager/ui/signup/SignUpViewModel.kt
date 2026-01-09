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
data class ProvinceItem(val id: String, val name: String)
data class CityItem(val id: String, val name: String)

object KoreanBanks {
    val banks = listOf(
        "NH농협은행",
        "카카오뱅크",
        "토스뱅크",
        "케이뱅크",
        "KB국민은행",
        "신한은행",
        "우리은행",
        "하나은행",
        "IBK기업은행",
        "새마을금고",
        "신협",
        "수협은행",
        "우체국",
        "부산은행",
        "경남은행",
        "대구은행",
        "광주은행",
        "전북은행",
        "제주은행",
        "SC제일은행",
        "한국씨티은행"
    )
}

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
    var officePhone by mutableStateOf("")
    var bankName by mutableStateOf("")
    var accountNumber by mutableStateOf("")
    var confirmAccountNumber by mutableStateOf("")
    var accountHolder by mutableStateOf("")

    private val _provinces = MutableStateFlow<List<ProvinceItem>>(emptyList())
    val provinces: StateFlow<List<ProvinceItem>> = _provinces.asStateFlow()

    private val _cities = MutableStateFlow<List<CityItem>>(emptyList())
    val cities: StateFlow<List<CityItem>> = _cities.asStateFlow()

    var selectedProvince by mutableStateOf<ProvinceItem?>(null)
        private set

    var selectedCity by mutableStateOf<CityItem?>(null)
        private set

    private val _signUpState = MutableStateFlow<SignUpState>(SignUpState.Idle)
    val signUpState: StateFlow<SignUpState> = _signUpState.asStateFlow()

    init {
        fetchProvinces()
    }

    fun onProvinceSelected(province: ProvinceItem) {
        selectedProvince = province
        selectedCity = null
        _cities.value = emptyList()
        fetchCities(province.id)
    }

    fun onCitySelected(city: CityItem) {
        selectedCity = city
    }

    private fun fetchProvinces() {
        _signUpState.value = SignUpState.LoadingRegions
        viewModelScope.launch {
            try {
                val snapshot = db.collection("provinces").get().await()
                val provinceList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name")
                    if (name != null) {
                        ProvinceItem(id = doc.id, name = name)
                    } else {
                        null
                    }
                }.sortedBy { it.name }
                _provinces.value = provinceList
                _signUpState.value = SignUpState.Idle
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("지역 목록을 불러오는데 실패했습니다: ${e.message}")
            }
        }
    }

    private fun fetchCities(provinceId: String) {
        viewModelScope.launch {
            try {
                val snapshot = db.collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .get()
                    .await()
                val cityList = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name")
                    if (name != null) {
                        CityItem(id = doc.id, name = name)
                    } else {
                        null
                    }
                }.sortedBy { it.name }
                _cities.value = cityList
            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("시/군/구 목록을 불러오는데 실패했습니다: ${e.message}")
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
        val currentSelectedProvince = selectedProvince
        val currentSelectedCity = selectedCity
        if (currentSelectedProvince == null) {
            _signUpState.value = SignUpState.Error("도/시를 선택해주세요.")
            return
        }
        if (currentSelectedCity == null) {
            _signUpState.value = SignUpState.Error("시/군/구를 선택해주세요.")
            return
        }
        if (officeName.isBlank()) {
            _signUpState.value = SignUpState.Error("사무실 이름을 입력해주세요.")
            return
        }
        if (officePhone.isBlank()) {
            _signUpState.value = SignUpState.Error("사무실 전화번호를 입력해주세요.")
            return
        }
        if (bankName.isBlank()) {
            _signUpState.value = SignUpState.Error("은행명을 입력해주세요.")
            return
        }
        if (accountNumber.isBlank()) {
            _signUpState.value = SignUpState.Error("계좌번호를 입력해주세요.")
            return
        }
        if (accountNumber != confirmAccountNumber) {
            _signUpState.value = SignUpState.Error("계좌번호가 일치하지 않습니다.")
            return
        }
        if (accountHolder.isBlank()) {
            _signUpState.value = SignUpState.Error("예금주를 입력해주세요.")
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
                    val officeRef = db.collection("provinces")
                        .document(currentSelectedProvince.id)
                        .collection("cities")
                        .document(currentSelectedCity.id)
                        .collection("offices")
                        .document() // 자동 ID 생성

                    val officeData = hashMapOf(
                        "name" to officeName,
                        "phone" to officePhone,
                        "bankName" to bankName,
                        "accountNumber" to accountNumber,
                        "accountHolder" to accountHolder,
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "createdBy" to newUser.uid
                    )
                    officeRef.set(officeData).await()

                    val newOfficeId = officeRef.id

                    // 3. 관리자 정보 저장
                    val adminData = hashMapOf(
                        "email" to email,
                        "name" to adminName,
                        "associatedProvinceId" to currentSelectedProvince.id,
                        "associatedCityId" to currentSelectedCity.id,
                        "associatedOfficeId" to newOfficeId,
                        "createdAt" to com.google.firebase.Timestamp.now()
                    )
                    db.collection("admins").document(newUser.uid).set(adminData).await()

                    // 4. QR 코드 자동 생성 및 저장
                    generateAndSaveQRCode(
                        currentSelectedProvince.id,
                        currentSelectedCity.id,
                        newOfficeId,
                        officePhone,
                        bankName,
                        accountNumber,
                        accountHolder
                    )

                    _signUpState.value = SignUpState.Success
                } else {
                    _signUpState.value = SignUpState.Error("사용자 생성에 실패했습니다.")
                }

            } catch (e: Exception) {
                _signUpState.value = SignUpState.Error("회원가입 중 오류 발생: ${e.message}")
            }
        }
    }

    private suspend fun generateAndSaveQRCode(
        provinceId: String,
        cityId: String,
        officeId: String,
        phone: String,
        bank: String,
        account: String,
        holder: String
    ) {
        try {
            // Play Store Install Referrer 방식
            val referrerParams = "p=$provinceId&c=$cityId&o=$officeId" +
                    "&phone=${android.net.Uri.encode(phone)}" +
                    "&bank=${android.net.Uri.encode(bank)}" +
                    "&account=${android.net.Uri.encode(account)}" +
                    "&holder=${android.net.Uri.encode(holder)}"

            // Play Store 링크 생성
            val playStoreUrl = "https://play.google.com/store/apps/details" +
                    "?id=com.designated.customer" +
                    "&referrer=${android.net.Uri.encode(referrerParams)}"

            // QR 코드 데이터 = Play Store URL
            val qrData = playStoreUrl
            val landingPageUrl = playStoreUrl // 하위 호환

            // OfficeSettings 문서에 QR 코드 및 랜딩 페이지 URL 저장
            val settingsData = hashMapOf(
                "qrCode" to qrData,
                "landingPageUrl" to landingPageUrl,
                "attributionThreshold" to 70, // 기본 임계값
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
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
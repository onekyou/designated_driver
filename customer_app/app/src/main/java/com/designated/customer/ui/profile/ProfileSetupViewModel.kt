package com.designated.customer.ui.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileSetupState(
    val nickname: String = "",
    val phoneNumber: String = "",
    val address: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSaveSuccess: Boolean = false
)

class ProfileSetupViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val TAG = "ProfileSetup"

    private val _uiState = MutableStateFlow(ProfileSetupState())
    val uiState: StateFlow<ProfileSetupState> = _uiState.asStateFlow()

    fun updateNickname(nickname: String) {
        _uiState.value = _uiState.value.copy(nickname = nickname, error = null)
    }

    fun updatePhoneNumber(phoneNumber: String) {
        _uiState.value = _uiState.value.copy(phoneNumber = phoneNumber, error = null)
    }

    fun updateAddress(address: String) {
        _uiState.value = _uiState.value.copy(address = address, error = null)
    }

    /**
     * 프로필 저장
     */
    fun saveProfile(
        provinceId: String,
        cityId: String,
        officeId: String,

        driverId: String? = null,
        driverName: String? = null
    ) {
        val state = _uiState.value

        // 유효성 검사
        if (state.nickname.isBlank()) {
            _uiState.value = state.copy(error = "닉네임을 입력해주세요")
            return
        }

        if (state.phoneNumber.isBlank()) {
            _uiState.value = state.copy(error = "전화번호를 입력해주세요")
            return
        }

        // 전화번호 형식 검사 (간단한 검증)
        val phonePattern = Regex("^01[0-9]-?[0-9]{3,4}-?[0-9]{4}$")
        if (!phonePattern.matches(state.phoneNumber.replace("-", ""))) {
            _uiState.value = state.copy(error = "올바른 전화번호 형식이 아닙니다")
            return
        }

        val userId = auth.currentUser?.uid
        if (userId == null) {
            _uiState.value = state.copy(error = "인증되지 않은 사용자입니다")
            return
        }

        _uiState.value = state.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                // FCM 토큰 가져오기
                val fcmToken = try {
                    FirebaseMessaging.getInstance().token.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get FCM token", e)
                    null
                }

                // Firestore에 고객 정보 저장
                val now = Timestamp.now()
                // 테스트 사무실은 VIP 등급으로 설정
                val initialGrade = if (officeId == "testOffice") "vip" else "bronze"
                val customerData = hashMapOf(
                    "id" to userId,
                    "phoneNumber" to state.phoneNumber,
                    "name" to state.nickname,
                    "homeAddress" to state.address,  // homeAddress로 저장
                    "grade" to initialGrade,
                    "points" to 0,
                    "totalRides" to 0,
                    "totalSpent" to 0L,
                    "linkedOfficeId" to officeId,
                    "primaryOfficeId" to officeId,
                    "attributionSource" to "qr_scan",
                    "attributionScore" to null,
                    "registeredAt" to now,
                    "lastRideAt" to null,
                    "lastActiveAt" to now,  // 마지막 활동 시간 추가
                    // 사무실 연락처 정보는 기본값으로 빈 문자열 설정
                    "officePhone" to "",
                    "bankName" to "",
                    "accountNumber" to "",
                    "accountHolder" to ""
                )

                // 기사 추천 정보가 있으면 추가
                if (driverId != null && driverName != null) {
                    customerData["referralDriverId"] = driverId
                    customerData["referralDriverName"] = driverName
                    customerData["attributionDate"] = now
                }

                // FCM 토큰이 있으면 추가
                if (fcmToken != null) {
                    customerData["fcmToken"] = fcmToken
                    Log.d(TAG, "FCM token added to customer data")
                }

                db.collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .document(cityId)
                    .collection("offices")
                    .document(officeId)
                    .collection("customers")
                    .document(userId)
                    .set(customerData)
                    .await()

                // customerInfo 컬렉션에도 FCM 토큰 저장 (functions에서 조회용)
                if (fcmToken != null) {
                    db.collection("provinces")
                        .document(provinceId)
                        .collection("cities")
                        .document(cityId)
                        .collection("offices")
                        .document(officeId)
                        .collection("customerInfo")
                        .document(state.phoneNumber)
                        .set(
                            mapOf(
                                "fcmToken" to fcmToken,
                                "phoneNumber" to state.phoneNumber,
                                "updatedAt" to now
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                    Log.d(TAG, "FCM token saved to customerInfo collection")
                }

                Log.d(TAG, "Customer profile saved successfully: $userId")

                _uiState.value = state.copy(
                    isLoading = false,
                    isSaveSuccess = true
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to save customer profile", e)
                _uiState.value = state.copy(
                    isLoading = false,
                    error = "저장 실패: ${e.message}"
                )
            }
        }
    }

    /**
     * Firestore에서 프로필 존재 여부 확인
     */
    suspend fun checkProfileExists(
        userId: String,
        provinceId: String,
        cityId: String,
        officeId: String
    ): Boolean {
        return try {
            val doc = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .document(userId)
                .get()
                .await()

            doc.exists()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check profile existence", e)
            false
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

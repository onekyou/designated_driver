package com.designated.callmanager.ui.pendingdrivers

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.PendingDriverInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.designated.callmanager.data.DriverInfo

sealed class PendingDriversUiState {
    object Loading : PendingDriversUiState()
    data class Success(val drivers: List<PendingDriverInfo>) : PendingDriversUiState()
    data class Error(val message: String) : PendingDriversUiState()
}

sealed class ApprovedDriversUiState {
    object Loading : ApprovedDriversUiState()
    data class Success(val drivers: List<DriverInfo>) : ApprovedDriversUiState()
    data class Error(val message: String) : ApprovedDriversUiState()
}

sealed class DriverApprovalState {
    object Idle : DriverApprovalState()
    object Loading : DriverApprovalState()
    data class Success(val driverName: String, val approved: Boolean) : DriverApprovalState()
    data class Error(val message: String) : DriverApprovalState()
}

class PendingDriversViewModel(
    application: Application,
    private val provinceId: String,
    private val cityId: String,
    private val officeId: String
) : AndroidViewModel(application) {
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _uiState = MutableStateFlow<PendingDriversUiState>(PendingDriversUiState.Loading)
    val uiState: StateFlow<PendingDriversUiState> = _uiState.asStateFlow()

    private val _approvedDriversState = MutableStateFlow<ApprovedDriversUiState>(ApprovedDriversUiState.Loading)
    val approvedDriversState: StateFlow<ApprovedDriversUiState> = _approvedDriversState.asStateFlow()

    private val _approvalState = MutableStateFlow<DriverApprovalState>(DriverApprovalState.Idle)
    val approvalState: StateFlow<DriverApprovalState> = _approvalState.asStateFlow()

    init {
        if (provinceId.isBlank() || cityId.isBlank() || officeId.isBlank()) {
            _uiState.value = PendingDriversUiState.Error("관리자 정보(도/시/사무실 ID)가 유효하지 않습니다.")
        } else {
            fetchPendingDrivers()
        }
    }

    fun fetchPendingDrivers() {
        android.util.Log.d("PendingDriversViewModel", "fetchPendingDrivers called - provinceId: $provinceId, cityId: $cityId, officeId: $officeId")

        if (provinceId.isBlank() || cityId.isBlank() || officeId.isBlank()) {
            _uiState.value = PendingDriversUiState.Error("관리자 정보(도/시/사무실 ID)가 유효하지 않습니다.")
            return
        }

        _uiState.value = PendingDriversUiState.Loading
        viewModelScope.launch {
            try {
                android.util.Log.d("PendingDriversViewModel", "Starting Firestore query...")

                val snapshot = firestore.collection("pending_drivers")
                    .whereEqualTo("targetProvinceId", provinceId)
                    .whereEqualTo("targetCityId", cityId)
                    .whereEqualTo("targetOfficeId", officeId)
                    .get()
                    .await()

                android.util.Log.d("PendingDriversViewModel", "Firestore query completed - found ${snapshot.documents.size} documents")

                val driverList = snapshot.documents.mapNotNull { doc ->
                    try {
                        android.util.Log.d("PendingDriversViewModel", "Processing doc ${doc.id}: ${doc.data}")
                        val parsedDriver = doc.toObject(PendingDriverInfo::class.java)
                        val finalDriver = if (parsedDriver?.authUid == null) {
                            parsedDriver?.copy(authUid = doc.id)
                        } else {
                            parsedDriver
                        }

                        // 클라이언트 측에서 상태 필터링 - 승인완료 상태도 포함
                        if (finalDriver?.status in listOf("승인대기중", "승인중", "승인완료")) {
                            finalDriver
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PendingDriversViewModel", "Error parsing doc ${doc.id}", e)
                        null
                    }
                }.sortedBy { it.requestedAt }

                android.util.Log.d("PendingDriversViewModel", "Parsed ${driverList.size} drivers successfully")
                _uiState.value = PendingDriversUiState.Success(driverList)

            } catch (e: Exception) {
                android.util.Log.e("PendingDriversViewModel", "fetchPendingDrivers failed", e)
                _uiState.value = PendingDriversUiState.Error("승인 대기 목록 로드 실패: ${e.message}")
            }
        }
    }

    fun approveDriver(driverInfo: PendingDriverInfo) {
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            _approvalState.value = DriverApprovalState.Error("승인 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }
        if (driverInfo.targetProvinceId.isNullOrBlank() || driverInfo.targetOfficeId.isNullOrBlank()) {
            _approvalState.value = DriverApprovalState.Error("승인 실패: 기사 정보에 대상 지역/사무실 ID가 없습니다.")
            return
        }
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                // 추천 QR URL 생성
                val referralQrUrl = buildReferralUrl(
                    driverInfo.targetProvinceId,
                    driverInfo.targetOfficeId,
                    driverUid,
                    driverInfo.name ?: ""
                )

                val finalDriverData: Map<String, Any?> = mapOf(
                    "id" to driverUid,
                    "authUid" to driverUid,
                    "name" to driverInfo.name,
                    "phoneNumber" to driverInfo.phoneNumber,
                    "email" to driverInfo.email,
                    "driverType" to driverInfo.driverType,

                    // 기사 운행 상태: DriverStatus Enum의 value 사용 (예: "오프라인")
                    "status" to Constants.DRIVER_STATUS_OFFLINE,

                    // 기사 가입 승인 상태: DriverApprovalStatus Enum의 name 사용 (예: "APPROVED")
                    "approvalStatus" to Constants.APPROVAL_STATUS_APPROVED,

                    "provinceId" to driverInfo.targetProvinceId,
                    "officeId" to driverInfo.targetOfficeId,

                    "associatedOfficeId" to driverInfo.targetOfficeId,

                    "createdAt" to (driverInfo.requestedAt ?: com.google.firebase.Timestamp.now()),
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                    "approvedAt" to com.google.firebase.Timestamp.now(),
                    "isActive" to true,
                    "rating" to 0f,
                    "totalTrips" to 0,
                    "referralQrUrl" to referralQrUrl  // 추천 QR URL 추가
                )

                val normalizedType = driverInfo.driverType.trim()
                val driverCollection = when {
                    normalizedType.equals("PICKUP", ignoreCase = true) -> "pickup_drivers"
                    normalizedType == "픽업기사" -> "pickup_drivers"
                    normalizedType.equals("DESIGNATED", ignoreCase = true) -> "designated_drivers"
                    normalizedType == "대리기사" -> "designated_drivers"
                    else -> {
                        "designated_drivers"
                    }
                }
                val finalDriverDocRef = firestore.collection("provinces").document(driverInfo.targetProvinceId)
                    .collection("offices").document(driverInfo.targetOfficeId)
                    .collection(driverCollection).document(driverUid)
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(driverUid)
                try {
                    finalDriverDocRef.set(finalDriverData).await()
                    } catch (e: Exception) {
                    throw e
                }

                try {
                    pendingDriverDocRef.delete().await()
                    android.util.Log.d("PendingDriversViewModel", "pending_drivers에서 기사 문서 삭제 완료")
                } catch (e: Exception) {
                    android.util.Log.e("PendingDriversViewModel", "pending_drivers 삭제 실패: ${e.message}", e)
                    android.util.Log.w("PendingDriversViewModel", "삭제 실패했지만 기사 승인은 완료됨 - 기사 로그인 가능")
                }

                // 로컬 상태를 먼저 업데이트
                val currentState = _uiState.value
                if (currentState is PendingDriversUiState.Success) {
                    val updatedDrivers = currentState.drivers.map { driver ->
                        if (driver.authUid == driverUid) {
                            driver.copy(status = "승인중")
                        } else {
                            driver
                        }
                    }
                    _uiState.value = PendingDriversUiState.Success(updatedDrivers)
                }

                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", true)


            } catch (e: Exception) {
                _approvalState.value = DriverApprovalState.Error("기사 승인 중 오류 발생: ${e.message}")
            }
        }
    }

    fun deleteDriver(driverInfo: PendingDriverInfo) {
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            _approvalState.value = DriverApprovalState.Error("삭제 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                // pending_drivers에서 삭제
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(driverUid)
                pendingDriverDocRef.delete().await()

                // 승인된 기사라면 기사 컬렉션에서도 삭제
                if (driverInfo.status in listOf("승인중", "승인완료")) {
                    val normalizedType = driverInfo.driverType.trim()
                    val driverCollection = when {
                        normalizedType.equals("PICKUP", ignoreCase = true) -> "pickup_drivers"
                        normalizedType == "픽업기사" -> "pickup_drivers"
                        normalizedType.equals("DESIGNATED", ignoreCase = true) -> "designated_drivers"
                        normalizedType == "대리기사" -> "designated_drivers"
                        else -> "designated_drivers"
                    }

                    val driverDocRef = firestore.collection("provinces").document(driverInfo.targetProvinceId)
                        .collection("offices").document(driverInfo.targetOfficeId)
                        .collection(driverCollection).document(driverUid)

                    try {
                        driverDocRef.delete().await()
                    } catch (e: Exception) {
                        android.util.Log.w("PendingDriversViewModel", "기사 컬렉션에서 삭제 실패: ${e.message}")
                    }
                }

                // Cloud Functions를 통해 Auth 계정 삭제 요청
                try {
                    val deleteRequest = mapOf(
                        "uid" to driverUid,
                        "requestedBy" to "call_manager"
                    )
                    firestore.collection("delete_requests").add(deleteRequest).await()
                } catch (e: Exception) {
                    android.util.Log.w("PendingDriversViewModel", "Auth 계정 삭제 요청 실패: ${e.message}")
                }

                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", false)

            } catch (e: Exception) {
                _approvalState.value = DriverApprovalState.Error("기사 삭제 중 오류 발생: ${e.message}")
            }
        }
    }

    fun resetApprovalState() {
        _approvalState.value = DriverApprovalState.Idle
    }

    /**
     * 승인된 기사 목록 조회 (대리기사 + 픽업기사)
     */
    fun fetchApprovedDrivers() {
        android.util.Log.d("PendingDriversViewModel", "fetchApprovedDrivers called - provinceId: $provinceId, officeId: $officeId")

        if (provinceId.isBlank() || officeId.isBlank()) {
            _approvedDriversState.value = ApprovedDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
            return
        }

        _approvedDriversState.value = ApprovedDriversUiState.Loading
        viewModelScope.launch {
            try {
                val allDrivers = mutableListOf<DriverInfo>()

                // 대리기사 조회
                val designatedSnapshot = firestore
                    .collection("provinces").document(provinceId)
                    .collection("offices").document(officeId)
                    .collection("designated_drivers")
                    .whereEqualTo("approvalStatus", Constants.APPROVAL_STATUS_APPROVED)
                    .get()
                    .await()

                designatedSnapshot.documents.mapNotNullTo(allDrivers) { doc ->
                    try {
                        doc.toObject(DriverInfo::class.java)?.copy(
                            id = doc.id,
                            authUid = doc.id
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("PendingDriversViewModel", "Error parsing designated driver ${doc.id}", e)
                        null
                    }
                }

                // 픽업기사 조회
                val pickupSnapshot = firestore
                    .collection("provinces").document(provinceId)
                    .collection("offices").document(officeId)
                    .collection("pickup_drivers")
                    .whereEqualTo("approvalStatus", Constants.APPROVAL_STATUS_APPROVED)
                    .get()
                    .await()

                pickupSnapshot.documents.mapNotNullTo(allDrivers) { doc ->
                    try {
                        doc.toObject(DriverInfo::class.java)?.copy(
                            id = doc.id,
                            authUid = doc.id
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("PendingDriversViewModel", "Error parsing pickup driver ${doc.id}", e)
                        null
                    }
                }

                android.util.Log.d("PendingDriversViewModel", "승인된 기사 ${allDrivers.size}명 조회 완료")
                _approvedDriversState.value = ApprovedDriversUiState.Success(allDrivers.sortedBy { it.name })

            } catch (e: Exception) {
                android.util.Log.e("PendingDriversViewModel", "fetchApprovedDrivers failed", e)
                _approvedDriversState.value = ApprovedDriversUiState.Error("승인된 기사 목록 로드 실패: ${e.message}")
            }
        }
    }

    /**
     * 승인된 기사 퇴사 처리
     */
    fun retireDriver(driverInfo: DriverInfo) {
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            _approvalState.value = DriverApprovalState.Error("퇴사 처리 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }

        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                // 기사 타입에 따라 컬렉션 결정
                val driverType = driverInfo.driverType ?: "대리기사"
                val driverCollection = when {
                    driverType.equals("PICKUP", ignoreCase = true) -> "pickup_drivers"
                    driverType == "픽업기사" -> "pickup_drivers"
                    driverType.equals("DESIGNATED", ignoreCase = true) -> "designated_drivers"
                    driverType == "대리기사" -> "designated_drivers"
                    else -> "designated_drivers"
                }

                // 기사 문서 삭제
                val driverDocRef = firestore
                    .collection("provinces").document(provinceId)
                    .collection("offices").document(officeId)
                    .collection(driverCollection).document(driverUid)

                driverDocRef.delete().await()
                android.util.Log.d("PendingDriversViewModel", "기사 문서 삭제 완료: $driverUid")

                // Cloud Functions를 통해 Auth 계정 삭제 요청
                try {
                    val deleteRequest = mapOf(
                        "uid" to driverUid,
                        "requestedBy" to "call_manager_retire"
                    )
                    firestore.collection("delete_requests").add(deleteRequest).await()
                    android.util.Log.d("PendingDriversViewModel", "Auth 계정 삭제 요청 완료")
                } catch (e: Exception) {
                    android.util.Log.w("PendingDriversViewModel", "Auth 계정 삭제 요청 실패: ${e.message}")
                }

                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", false)

                // 목록 새로고침
                fetchApprovedDrivers()

            } catch (e: Exception) {
                android.util.Log.e("PendingDriversViewModel", "retireDriver failed", e)
                _approvalState.value = DriverApprovalState.Error("기사 퇴사 처리 중 오류 발생: ${e.message}")
            }
        }
    }

    /**
     * 기사 추천 QR URL 생성 (Play Store Install Referrer 방식)
     */
    private suspend fun buildReferralUrl(
        provinceId: String,
        officeId: String,
        driverId: String,
        driverName: String
    ): String {
        // 1. 사무실 정보 가져오기
        val officeDoc = firestore
            .collection("provinces").document(provinceId)
            .collection("offices").document(officeId)
            .get()
            .await()

        val officePhone = officeDoc.getString("phone") ?: ""
        val bankName = officeDoc.getString("bankName") ?: ""
        val accountNumber = officeDoc.getString("accountNumber") ?: ""
        val accountHolder = officeDoc.getString("accountHolder") ?: ""

        // 2. Play Store Install Referrer URL 생성
        val encodedName = java.net.URLEncoder.encode(driverName, "UTF-8")
        val encodedPhone = java.net.URLEncoder.encode(officePhone, "UTF-8")
        val encodedBank = java.net.URLEncoder.encode(bankName, "UTF-8")
        val encodedAccount = java.net.URLEncoder.encode(accountNumber, "UTF-8")
        val encodedHolder = java.net.URLEncoder.encode(accountHolder, "UTF-8")

        val referrerParams = "r=$provinceId&o=$officeId&d=$driverId&dn=$encodedName" +
                "&phone=$encodedPhone&bank=$encodedBank&account=$encodedAccount&holder=$encodedHolder"

        val playStoreUrl = "https://play.google.com/store/apps/details" +
                "?id=com.designated.customer" +
                "&referrer=${java.net.URLEncoder.encode(referrerParams, "UTF-8")}"

        android.util.Log.d("PendingDriversViewModel", "기사 추천 Play Store URL 생성 완료 (기사: $driverName)")

        return playStoreUrl
    }

    class Factory(private val application: Application, private val provinceId: String, private val cityId: String, private val officeId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PendingDriversViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PendingDriversViewModel(application, provinceId, cityId, officeId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
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

sealed class PendingDriversUiState {
    object Loading : PendingDriversUiState()
    data class Success(val drivers: List<PendingDriverInfo>) : PendingDriversUiState()
    data class Error(val message: String) : PendingDriversUiState()
}

sealed class DriverApprovalState {
    object Idle : DriverApprovalState()
    object Loading : DriverApprovalState()
    data class Success(val driverName: String, val approved: Boolean) : DriverApprovalState()
    data class Error(val message: String) : DriverApprovalState()
}

class PendingDriversViewModel(
    application: Application,
    private val regionId: String,
    private val officeId: String
) : AndroidViewModel(application) {
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _uiState = MutableStateFlow<PendingDriversUiState>(PendingDriversUiState.Loading)
    val uiState: StateFlow<PendingDriversUiState> = _uiState.asStateFlow()

    private val _approvalState = MutableStateFlow<DriverApprovalState>(DriverApprovalState.Idle)
    val approvalState: StateFlow<DriverApprovalState> = _approvalState.asStateFlow()

    init {
        if (regionId.isBlank() || officeId.isBlank()) {
            _uiState.value = PendingDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
        } else {
            fetchPendingDrivers()
        }
    }

    fun fetchPendingDrivers() {
        android.util.Log.d("PendingDriversViewModel", "fetchPendingDrivers called - regionId: $regionId, officeId: $officeId")

        if (regionId.isBlank() || officeId.isBlank()) {
            _uiState.value = PendingDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
            return
        }

        _uiState.value = PendingDriversUiState.Loading
        viewModelScope.launch {
            try {
                android.util.Log.d("PendingDriversViewModel", "Starting Firestore query...")

                val snapshot = firestore.collection("pending_drivers")
                    .whereEqualTo("targetRegionId", regionId)
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
        if (driverInfo.targetRegionId.isNullOrBlank() || driverInfo.targetOfficeId.isNullOrBlank()) {
            _approvalState.value = DriverApprovalState.Error("승인 실패: 기사 정보에 대상 지역/사무실 ID가 없습니다.")
            return
        }
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
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

                    "regionId" to driverInfo.targetRegionId,
                    "officeId" to driverInfo.targetOfficeId,

                    "associatedOfficeId" to driverInfo.targetOfficeId,

                    "createdAt" to (driverInfo.requestedAt ?: com.google.firebase.Timestamp.now()),
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                    "approvedAt" to com.google.firebase.Timestamp.now(),
                    "isActive" to true,
                    "rating" to 0f,
                    "totalTrips" to 0
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
                val finalDriverDocRef = firestore.collection("regions").document(driverInfo.targetRegionId)
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

                    val driverDocRef = firestore.collection("regions").document(driverInfo.targetRegionId)
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

    class Factory(private val application: Application, private val regionId: String, private val officeId: String) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PendingDriversViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PendingDriversViewModel(application, regionId, officeId) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
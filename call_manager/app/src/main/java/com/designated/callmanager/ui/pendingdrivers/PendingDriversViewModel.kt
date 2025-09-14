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
        if (regionId.isBlank() || officeId.isBlank()) {
            _uiState.value = PendingDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
            return
        }

        _uiState.value = PendingDriversUiState.Loading
        viewModelScope.launch {
            try {
                val allPendingSnapshot = firestore.collection("pending_drivers").get().await()

                allPendingSnapshot.documents.forEach { doc ->
                }

                val snapshot = firestore.collection("pending_drivers")
                    .whereEqualTo("targetRegionId", regionId)
                    .whereEqualTo("targetOfficeId", officeId)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.ASCENDING)
                    .get()
                    .await()

                val driverList = snapshot.documents.mapNotNull { doc ->
                    try {
                        val parsedDriver = doc.toObject(PendingDriverInfo::class.java)
                        if (parsedDriver?.authUid == null) {
                            parsedDriver?.copy(authUid = doc.id)
                        } else {
                            parsedDriver
                        }
                    } catch (e: Exception) {
                        null
                    }
                }

                driverList.forEach { driver ->
                }

                _uiState.value = PendingDriversUiState.Success(driverList)
                } catch (e: Exception) {
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
                    } catch (e: Exception) {
                    }

                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", true)
                fetchPendingDrivers()

            } catch (e: Exception) {
                _approvalState.value = DriverApprovalState.Error("기사 승인 중 오류 발생: ${e.message}")
            }
        }
    }

    fun rejectDriver(driverInfo: PendingDriverInfo) {
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            _approvalState.value = DriverApprovalState.Error("거절 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(driverUid)
                pendingDriverDocRef.delete().await()

                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", false)
                fetchPendingDrivers()

            } catch (e: Exception) {
                _approvalState.value = DriverApprovalState.Error("기사 거절 중 오류 발생: ${e.message}")
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
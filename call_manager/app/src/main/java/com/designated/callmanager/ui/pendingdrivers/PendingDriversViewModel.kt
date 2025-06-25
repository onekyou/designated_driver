package com.designated.callmanager.ui.pendingdrivers

import android.app.Application
import android.util.Log
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

// --- 상태 정의 ---
sealed class PendingDriversUiState {
    object Loading : PendingDriversUiState()
    data class Success(val drivers: List<PendingDriverInfo>) : PendingDriversUiState()
    data class Error(val message: String) : PendingDriversUiState()
}

sealed class DriverApprovalState {
    object Idle : DriverApprovalState()
    object Loading : DriverApprovalState()
    data class Success(val driverName: String, val approved: Boolean) : DriverApprovalState() // 승인/거절 결과
    data class Error(val message: String) : DriverApprovalState()
}
// --- ---

class PendingDriversViewModel(
    application: Application,
    private val regionId: String,
    private val officeId: String
) : AndroidViewModel(application) {
    private val TAG = "PendingDriversViewModel"
    private val firestore: FirebaseFirestore = Firebase.firestore

    private val _uiState = MutableStateFlow<PendingDriversUiState>(PendingDriversUiState.Loading)
    val uiState: StateFlow<PendingDriversUiState> = _uiState.asStateFlow()

    private val _approvalState = MutableStateFlow<DriverApprovalState>(DriverApprovalState.Idle)
    val approvalState: StateFlow<DriverApprovalState> = _approvalState.asStateFlow()

    init {
        if (regionId.isBlank() || officeId.isBlank()) {
            Log.e(TAG, "ViewModel initialized with invalid region/office ID. Region: '$regionId', Office: '$officeId'")
            _uiState.value = PendingDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
        } else {
            fetchPendingDrivers()
        }
    }

    fun fetchPendingDrivers() {
        if (regionId.isBlank() || officeId.isBlank()) {
            Log.w(TAG, "fetchPendingDrivers called with invalid IDs. Skipping fetch.")
            _uiState.value = PendingDriversUiState.Error("관리자 정보(지역/사무실 ID)가 유효하지 않습니다.")
            return
        }

        Log.d(TAG, "Fetching pending drivers for Region: $regionId, Office: $officeId")
        _uiState.value = PendingDriversUiState.Loading
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("pending_drivers")
                    .whereEqualTo("targetRegionId", regionId)
                    .whereEqualTo("targetOfficeId", officeId)
                    .orderBy("requestedAt", com.google.firebase.firestore.Query.Direction.ASCENDING) // 신청 순서
                    .get()
                    .await()

                val driverList = snapshot.documents.mapNotNull { doc ->
                    try {
                         val parsedDriver = doc.toObject(PendingDriverInfo::class.java)
                         if (parsedDriver?.authUid == null) {
                             Log.w(TAG, "Parsed pending driver ${doc.id} has null authUid, attempting to use document ID.")
                             parsedDriver?.copy(authUid = doc.id)
                         } else {
                             parsedDriver
                         }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing pending driver document: ${doc.id}", e)
                        null
                    }
                }
                Log.d(TAG, "Pending drivers fetched successfully: ${driverList.size} drivers for $regionId/$officeId")
                _uiState.value = PendingDriversUiState.Success(driverList)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching pending drivers for $regionId/$officeId", e)
                _uiState.value = PendingDriversUiState.Error("승인 대기 목록 로드 실패: ${e.message}")
            }
        }
    }

    fun approveDriver(driverInfo: PendingDriverInfo) {
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            Log.e(TAG, "Cannot approve driver: authUid is missing in driverInfo: ${driverInfo.name}")
            _approvalState.value = DriverApprovalState.Error("승인 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }
        if (driverInfo.targetRegionId.isNullOrBlank() || driverInfo.targetOfficeId.isNullOrBlank()) {
            Log.e(TAG, "Cannot approve driver ${driverInfo.name}: targetRegionId or targetOfficeId is missing.")
            _approvalState.value = DriverApprovalState.Error("승인 실패: 기사 정보에 대상 지역/사무실 ID가 없습니다.")
            return
        }
        Log.d(TAG, "Approving driver: ${driverInfo.name} ($driverUid) -> Target: ${driverInfo.targetRegionId}/${driverInfo.targetOfficeId}")
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                // 1. 최종 기사 데이터 생성 (DriverInfo 모델에 맞춰 필드 구성)
                val finalDriverData: Map<String, Any?> = mapOf(
                    "id" to driverUid, // 문서 ID를 authUid와 동일하게 사용
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
                    "createdAt" to (driverInfo.requestedAt ?: com.google.firebase.Timestamp.now()),
                    "updatedAt" to com.google.firebase.Timestamp.now(),
                    "approvedAt" to com.google.firebase.Timestamp.now(),
                    "isActive" to true,
                    "rating" to 0f,
                    "totalTrips" to 0
                )

                // 2. 최종 경로 참조
                val finalDriverDocRef = firestore.collection("regions").document(driverInfo.targetRegionId)
                    .collection("offices").document(driverInfo.targetOfficeId)
                    .collection("designated_drivers").document(driverUid)

                // 3. 승인 대기 문서 참조
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(driverUid)

                // 4. Firestore 트랜잭션
                firestore.runTransaction { transaction ->
                    transaction.set(finalDriverDocRef, finalDriverData)
                    transaction.delete(pendingDriverDocRef)
                    null
                }.await()

                Log.i(TAG, "Driver approved and moved successfully: ${driverInfo.name}")
                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", true)
                fetchPendingDrivers() // 목록 새로고침

            } catch (e: Exception) {
                Log.e(TAG, "Error approving driver: ${driverInfo.name}", e)
                _approvalState.value = DriverApprovalState.Error("기사 승인 중 오류 발생: ${e.message}")
            }
        }
    }

    fun rejectDriver(driverInfo: PendingDriverInfo) {
        val driverUid = driverInfo.authUid
        if (driverUid.isNullOrBlank()) {
            Log.e(TAG, "Cannot reject driver: authUid is missing in driverInfo: ${driverInfo.name}")
            _approvalState.value = DriverApprovalState.Error("거절 실패: 기사 고유 ID(authUid)가 없습니다.")
            return
        }
        Log.d(TAG, "Rejecting driver: ${driverInfo.name} ($driverUid)")
        _approvalState.value = DriverApprovalState.Loading
        viewModelScope.launch {
            try {
                // 승인 대기 문서 삭제
                val pendingDriverDocRef = firestore.collection("pending_drivers").document(driverUid)
                pendingDriverDocRef.delete().await()

                Log.i(TAG, "Driver rejected and removed successfully: ${driverInfo.name}")
                _approvalState.value = DriverApprovalState.Success(driverInfo.name ?: "(이름 없음)", false)
                fetchPendingDrivers() // 목록 새로고침

            } catch (e: Exception) {
                Log.e(TAG, "Error rejecting driver: ${driverInfo.name}", e)
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
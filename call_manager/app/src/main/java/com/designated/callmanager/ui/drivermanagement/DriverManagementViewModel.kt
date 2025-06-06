package com.designated.callmanager.ui.drivermanagement

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.DriverInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class ApprovalState {
    object Idle : ApprovalState()
    object Loading : ApprovalState()
    data class Success(val driverId: String) : ApprovalState()
    data class Error(val message: String) : ApprovalState()
}

class DriverManagementViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "DriverMgmtViewModel"
    private val firestore: FirebaseFirestore = Firebase.firestore
    private val driversCollection = firestore.collection("drivers")

    private val _pendingDrivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    val pendingDrivers: StateFlow<List<DriverInfo>> = _pendingDrivers

    private val _approvalState = MutableStateFlow<ApprovalState>(ApprovalState.Idle)
    val approvalState: StateFlow<ApprovalState> = _approvalState

    init {
        fetchPendingDrivers()
    }

    fun fetchPendingDrivers() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "승인 대기 기사 목록 가져오는 중...")
                val snapshot = driversCollection
                    .whereEqualTo("status", "승인대기중") // "승인대기중" 상태 쿼리
                    .get()
                    .await()

                val driversList = snapshot.documents.mapNotNull { doc ->
                    // data.DriverInfo 클래스에 맞게 파싱 로직 수정
                    try {
                         DriverInfo(
                            id = doc.id,
                            authUid = doc.getString("driverID"), // authUid 필드 추가 (nullable)
                            name = doc.getString("name") ?: "이름 없음",
                            status = doc.getString("status") ?: "알 수 없음", // status 필드는 동일
                            phoneNumber = doc.getString("phoneNumber") ?: "번호 없음", // phoneNumber 필드는 동일 (기존 phone 필드는 제거됨)
                            createdAt = doc.getTimestamp("createdAt"), // createdAt 필드 추가 (nullable)
                            updatedAt = doc.getTimestamp("updateAt"), // updatedAt 필드 추가 (nullable)
                            regionId = doc.getString("regionID"), // regionId 필드 추가 (nullable)
                            officeId = doc.getString("officeID") // officeId 필드 추가 (nullable)
                            // type 필드는 data.DriverInfo에 없으므로 제거
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "기사 문서 파싱 오류: ${doc.id}", e)
                        null
                    }
                }
                _pendingDrivers.value = driversList
                Log.d(TAG, "승인 대기 기사 ${driversList.size}명 로드 완료")
            } catch (e: Exception) {
                Log.e(TAG, "승인 대기 기사 목록 로드 실패", e)
                // 사용자에게 오류 메시지 표시 필요 (예: StateFlow 사용)
            }
        }
    }

    fun approveDriver(driverId: String) {
        Log.d(TAG, "기사 승인 시도: $driverId")
        _approvalState.value = ApprovalState.Loading
        viewModelScope.launch {
            try {
                driversCollection.document(driverId)
                    .update("status", "대기중") // 상태를 "대기중"으로 변경
                    // .update("isApproved", true) // isApproved 필드 사용 시
                    .await()
                Log.d(TAG, "기사 승인 성공: $driverId")
                _approvalState.value = ApprovalState.Success(driverId)
                // 성공 후 목록 새로고침
                fetchPendingDrivers()
            } catch (e: Exception) {
                Log.e(TAG, "기사 승인 실패: $driverId", e)
                _approvalState.value = ApprovalState.Error(e.localizedMessage ?: "기사 승인 중 오류 발생")
            }
        }
    }
    
    fun resetApprovalState() {
        _approvalState.value = ApprovalState.Idle
    }
} 
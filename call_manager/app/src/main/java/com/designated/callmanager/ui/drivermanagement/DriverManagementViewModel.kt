package com.designated.callmanager.ui.drivermanagement

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.DriverInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class DriverManagementViewModel : ViewModel() {
    private val db: FirebaseFirestore = Firebase.firestore
    private val _pendingDrivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    val pendingDrivers = _pendingDrivers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val TAG = "DriverManagementVM"

    fun fetchPendingDrivers(regionId: String, officeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val driversCollection =
                    db.collection("regions").document(regionId).collection("offices").document(officeId)
                        .collection("designated_drivers")
                Log.d(TAG, "승인 대기 기사 목록 가져오는 중...")
                val snapshot = driversCollection
                    .whereEqualTo("approvalStatus", Constants.APPROVAL_STATUS_PENDING) // "status" -> "approvalStatus"로 필드명 수정
                    .get()
                    .await()

                val drivers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DriverInfo::class.java)?.copy(id = doc.id)
                }
                _pendingDrivers.value = drivers
                Log.d(TAG, "승인 대기 기사 ${drivers.size}명 로드 완료")

            } catch (e: Exception) {
                Log.e(TAG, "승인 대기 기사 목록 로드 실패", e)
                // TODO: 에러 처리 UI 업데이트
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun approveDriver(regionId: String, officeId: String, driverId: String) {
        viewModelScope.launch {
            try {
                db.collection("regions").document(regionId).collection("offices").document(officeId)
                    .collection("designated_drivers").document(driverId)
                    .update("approvalStatus", Constants.APPROVAL_STATUS_APPROVED,
                        "status", Constants.DRIVER_STATUS_OFFLINE)
                    .await()
                Log.d(TAG, "기사 승인 성공: $driverId")
                fetchPendingDrivers(regionId, officeId) // 목록 새로고침
            } catch (e: Exception) {
                Log.e(TAG, "기사 승인 실패", e)
            }
        }
    }

    fun rejectDriver(regionId: String, officeId: String, driverId: String) {
        viewModelScope.launch {
            try {
                db.collection("regions").document(regionId).collection("offices").document(officeId)
                    .collection("designated_drivers").document(driverId)
                    .update("approvalStatus", Constants.APPROVAL_STATUS_REJECTED)
                    .await()
                Log.d(TAG, "기사 거절 성공: $driverId")
                fetchPendingDrivers(regionId, officeId) // 목록 새로고침
            } catch (e: Exception) {
                Log.e(TAG, "기사 거절 실패", e)
            }
        }
    }
}
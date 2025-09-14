package com.designated.callmanager.ui.drivermanagement

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
                val snapshot = driversCollection
                    .whereEqualTo("approvalStatus", Constants.APPROVAL_STATUS_PENDING) // "status" -> "approvalStatus"로 필드명 수정
                    .get()
                    .await()

                val drivers = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DriverInfo::class.java)?.copy(id = doc.id)
                }
                _pendingDrivers.value = drivers

            } catch (e: Exception) {
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
                fetchPendingDrivers(regionId, officeId)
            } catch (e: Exception) {
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
                fetchPendingDrivers(regionId, officeId)
            } catch (e: Exception) {
            }
        }
    }
}
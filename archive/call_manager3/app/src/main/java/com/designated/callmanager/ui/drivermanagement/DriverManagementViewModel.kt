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

    /**
     * pending_drivers 컬렉션(최상위)에서 대상 region / office 로 신청한 기사 목록을 조회한다.
     * DriverInfo 모델로 매핑해 UI 에서 재사용한다.
     */
    fun fetchPendingDrivers(regionId: String, officeId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d(TAG, "pending_drivers 컬렉션 조회: region=$regionId, office=$officeId")
                val snapshot = db.collection(Constants.COLLECTION_PENDING_DRIVERS)
                    .whereEqualTo("targetRegionId", regionId)
                    .whereEqualTo("targetOfficeId", officeId)
                    .orderBy("requestedAt")
                    .limit(50)
                    .get()
                    .await()

                val drivers = snapshot.documents.map { doc ->
                    // pending_drivers 스키마를 DriverInfo 로 투영 (일부 필드는 null 가능)
                    DriverInfo(
                        id = doc.id,
                        authUid = doc.getString("authUid"),
                        name = doc.getString("name") ?: "(이름 없음)",
                        phoneNumber = doc.getString("phoneNumber") ?: "",
                        email = doc.getString("email"),
                        driverType = doc.getString("driverType"),
                        regionId = doc.getString("targetRegionId"),
                        officeId = doc.getString("targetOfficeId"),
                        approvalStatus = Constants.APPROVAL_STATUS_PENDING,
                        createdAt = doc.getTimestamp("requestedAt")
                    )
                }

                _pendingDrivers.value = drivers
                Log.d(TAG, "승인 대기 기사 ${drivers.size}명 로드 완료 (pending_drivers)")

            } catch (e: Exception) {
                Log.e(TAG, "pending_drivers 목록 로드 실패", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 승인: pending_drivers → designated_drivers 이동 후 approvalStatus 업데이트
     */
    fun approveDriver(regionId: String, officeId: String, driverId: String) {
        viewModelScope.launch {
            try {
                val pendingRef = db.collection(Constants.COLLECTION_PENDING_DRIVERS).document(driverId)
                val pendingSnap = pendingRef.get().await()
                if (!pendingSnap.exists()) {
                    Log.w(TAG, "approveDriver: pending document not found ($driverId)")
                    return@launch
                }

                val driverData = pendingSnap.data ?: return@launch

                // 최종 드라이버 경로
                val finalRef = db.collection(Constants.COLLECTION_REGIONS).document(regionId)
                    .collection(Constants.COLLECTION_OFFICES).document(officeId)
                    .collection(Constants.COLLECTION_DRIVERS).document(driverId)

                db.runTransaction { tx ->
                    tx.set(finalRef, driverData + mapOf(
                        "approvalStatus" to Constants.APPROVAL_STATUS_APPROVED,
                        "status" to Constants.DRIVER_STATUS_OFFLINE,
                        "regionId" to regionId,
                        "officeId" to officeId,
                        "approvedAt" to com.google.firebase.Timestamp.now()
                    ))
                    tx.delete(pendingRef)
                }.await()

                Log.d(TAG, "기사 승인 및 이동 성공: $driverId")
                fetchPendingDrivers(regionId, officeId)
            } catch (e: Exception) {
                Log.e(TAG, "기사 승인 중 오류", e)
            }
        }
    }

    /**
     * 거절: pending_drivers 문서 삭제
     */
    fun rejectDriver(regionId: String, officeId: String, driverId: String) {
        viewModelScope.launch {
            try {
                db.collection(Constants.COLLECTION_PENDING_DRIVERS).document(driverId).delete().await()
                Log.d(TAG, "기사 거절 성공: $driverId (pending_drivers 문서 삭제)")
                fetchPendingDrivers(regionId, officeId)
            } catch (e: Exception) {
                Log.e(TAG, "기사 거절 실패", e)
            }
        }
    }
}
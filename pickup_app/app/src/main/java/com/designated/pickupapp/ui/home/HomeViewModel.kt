package com.designated.pickupapp.ui.home

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupapp.data.Constants
import com.designated.pickupapp.data.DriverInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val application: Application
) : AndroidViewModel(application) {

    private val _drivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    val drivers: StateFlow<List<DriverInfo>> = _drivers.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    
    

    private var driversListener: ListenerRegistration? = null
    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    private var currentDriverId: String? = null

    fun initialize(regionId: String, officeId: String, driverId: String) {
        android.util.Log.d("HomeViewModel", "초기화 시작: regionId='$regionId', officeId='$officeId', driverId='$driverId'")
        
        // 유효성 검사
        if (regionId.isBlank() || officeId.isBlank() || driverId.isBlank()) {
            android.util.Log.e("HomeViewModel", "❌ 초기화 실패 - 빈 파라미터 감지")
            android.util.Log.e("HomeViewModel", "regionId: '$regionId', officeId: '$officeId', driverId: '$driverId'")
            _loading.value = false
            return
        }
        
        currentRegionId = regionId
        currentOfficeId = officeId
        currentDriverId = driverId
        
        try {
            setupDriversListener(regionId, officeId)
            updateDriverStatus(regionId, officeId, driverId, Constants.STATUS_ONLINE)
            android.util.Log.d("HomeViewModel", "✅ 초기화 완료")
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "초기화 중 오류 발생", e)
            _loading.value = false
        }
    }
    
    private fun updateDriverStatus(regionId: String, officeId: String, driverId: String, status: String) {
        val driverRef = firestore
            .collection(Constants.COLLECTION_REGIONS)
            .document(regionId)
            .collection(Constants.COLLECTION_OFFICES)
            .document(officeId)
            .collection("pickup_drivers") // 픽업 기사는 pickup_drivers 컬렉션
            .document(driverId)
            
        driverRef.update("status", status)
            .addOnSuccessListener {
                android.util.Log.d("HomeViewModel", "픽업 기사 상태 업데이트 성공: $status")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("HomeViewModel", "픽업 기사 상태 업데이트 실패", e)
            }
    }


    private fun setupDriversListener(regionId: String, officeId: String) {
        val officeRef = firestore
            .collection(Constants.COLLECTION_REGIONS)
            .document(regionId)
            .collection(Constants.COLLECTION_OFFICES)
            .document(officeId)

        driversListener = officeRef
            .collection("designated_drivers") // 일반 대리기사들
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("HomeViewModel", "드라이버 리스너 에러", error)
                    _loading.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val driverList = snapshot.documents.mapNotNull { doc ->
                        try {
                            DriverInfo(
                                id = doc.id,
                                name = doc.getString("name") ?: "",
                                phoneNumber = doc.getString("phoneNumber") ?: "",
                                status = doc.getString("status") ?: Constants.STATUS_OFFLINE,
                                fcmToken = doc.getString("fcmToken"),
                                authUid = doc.getString("authUid")
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    _drivers.value = driverList
                    android.util.Log.d("HomeViewModel", "드라이버 목록 업데이트: ${driverList.size}개")
                }
                // 데이터 로딩 완료 
                _loading.value = false
            }
    }

    fun logout() {
        currentRegionId?.let { regionId ->
            currentOfficeId?.let { officeId ->
                currentDriverId?.let { driverId ->
                    updateDriverStatus(regionId, officeId, driverId, Constants.STATUS_OFFLINE)
                }
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        currentRegionId?.let { regionId ->
            currentOfficeId?.let { officeId ->
                currentDriverId?.let { driverId ->
                    updateDriverStatus(regionId, officeId, driverId, Constants.STATUS_OFFLINE)
                }
            }
        }
        driversListener?.remove()
    }
    
    
    
    
    
    
    /**
     * 오디오 권한이 부여되었는지 확인
     */
    private fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            application,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 오디오 권한이 필요한지 확인 (UI용)
     */
    fun needsAudioPermission(): Boolean {
        return !hasAudioPermission()
    }
}
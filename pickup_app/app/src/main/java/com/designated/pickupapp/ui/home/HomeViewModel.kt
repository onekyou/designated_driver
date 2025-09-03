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
import com.designated.pickupapp.data.PTTStatus
import com.designated.pickupapp.data.PTTState
import com.designated.pickupapp.ptt.core.PTTController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val application: Application,
    private val pttController: PTTController
) : AndroidViewModel(application) {

    private val _drivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    val drivers: StateFlow<List<DriverInfo>> = _drivers.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _pttState = MutableStateFlow(PTTState())
    val pttState: StateFlow<PTTState> = _pttState.asStateFlow()
    
    

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
            
            // PTT 시스템 초기화 및 자동 채널 참여
            initializePTTSystem(regionId, officeId)
            
            // PTT 상태 모니터링 시작
            startPTTStatusMonitoring()
            
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
    
    /**
     * PTT 시스템 초기화 및 자동 채널 참여
     */
    private fun initializePTTSystem(regionId: String, officeId: String) {
        viewModelScope.launch {
            try {
                // 1. 기본 채널 정보 설정
                pttController.setDefaultChannelInfo(regionId, officeId)
                android.util.Log.d("HomeViewModel", "PTT 기본 채널 설정 완료: ${regionId}_${officeId}_ptt")
                
                // 2. 자동으로 채널 참여 (듣기 모드)
                val joinResult = pttController.joinChannel()
                if (joinResult.isSuccess) {
                    android.util.Log.i("HomeViewModel", "✅ PTT 채널 자동 참여 성공")
                } else {
                    android.util.Log.w("HomeViewModel", "⚠️ PTT 채널 자동 참여 실패: ${joinResult.exceptionOrNull()?.message}")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("HomeViewModel", "PTT 시스템 초기화 중 오류", e)
            }
        }
    }
    
    /**
     * PTT 상태 모니터링 시작
     */
    private fun startPTTStatusMonitoring() {
        viewModelScope.launch {
            pttController.pttStatusFlow.collect { pttStatus ->
                pttStatus?.let { status ->
                    val currentState = _pttState.value
                    val updatedUsers = currentState.activePTTUsers.toMutableMap()
                    
                    if (status.isTransmitting) {
                        // PTT 시작
                        updatedUsers[status.userId] = status
                        _pttState.value = currentState.copy(
                            activePTTUsers = updatedUsers,
                            currentTransmitter = status.userId
                        )
                        android.util.Log.d("HomeViewModel", "PTT 상태 업데이트: ${status.userName} 전송 시작")
                    } else {
                        // PTT 종료
                        updatedUsers.remove(status.userId)
                        val newTransmitter = if (currentState.currentTransmitter == status.userId) {
                            null
                        } else {
                            currentState.currentTransmitter
                        }
                        
                        _pttState.value = currentState.copy(
                            activePTTUsers = updatedUsers,
                            currentTransmitter = newTransmitter
                        )
                        android.util.Log.d("HomeViewModel", "PTT 상태 업데이트: ${status.userName} 전송 종료")
                    }
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
        
        // PTT 시스템 정리
        pttController.destroy()
        android.util.Log.d("HomeViewModel", "PTT 시스템 정리 완료")
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
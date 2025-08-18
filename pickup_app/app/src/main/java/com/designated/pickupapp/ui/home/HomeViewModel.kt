package com.designated.pickupapp.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupapp.data.CallInfo
import com.designated.pickupapp.data.Constants
import com.designated.pickupapp.data.DriverInfo
import com.designated.pickupapp.service.PTTManager
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
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

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    private val _drivers = MutableStateFlow<List<DriverInfo>>(emptyList())
    val drivers: StateFlow<List<DriverInfo>> = _drivers.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    
    // PTT 관련 상태
    private val _pttStatus = MutableStateFlow("")
    val pttStatus: StateFlow<String> = _pttStatus.asStateFlow()
    
    private val _isPttSpeaking = MutableStateFlow(false)
    val isPttSpeaking: StateFlow<Boolean> = _isPttSpeaking.asStateFlow()
    
    // 채널명 상태 추가 (콜매니저와 동일한 기능)
    private val _pttChannelName = MutableStateFlow("")
    val pttChannelName: StateFlow<String> = _pttChannelName.asStateFlow()
    
    private var pttManager: PTTManager? = null
    

    private var callsListener: ListenerRegistration? = null
    private var driversListener: ListenerRegistration? = null
    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    private var currentDriverId: String? = null

    fun initialize(regionId: String, officeId: String, driverId: String) {
        currentRegionId = regionId
        currentOfficeId = officeId
        currentDriverId = driverId
        
        setupCallsListener(regionId, officeId)
        setupDriversListener(regionId, officeId)
        updateDriverStatus(regionId, officeId, driverId, Constants.STATUS_ONLINE)
        initializePTT(regionId, officeId)
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

    private fun setupCallsListener(regionId: String, officeId: String) {
        val officeRef = firestore
            .collection(Constants.COLLECTION_REGIONS)
            .document(regionId)
            .collection(Constants.COLLECTION_OFFICES)
            .document(officeId)

        callsListener = officeRef
            .collection(Constants.COLLECTION_CALLS)
            .whereIn("status", listOf(
                Constants.STATUS_WAITING,
                Constants.STATUS_ASSIGNED,
                Constants.STATUS_ACCEPTED,
                Constants.STATUS_IN_PROGRESS,
                Constants.STATUS_AWAITING_SETTLEMENT
            ))
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _loading.value = false
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val callList = snapshot.documents.mapNotNull { doc ->
                        try {
                            CallInfo(
                                id = doc.id,
                                phoneNumber = doc.getString("phoneNumber"),
                                customerName = doc.getString("customerName"),
                                customerAddress = doc.getString("customerAddress"),
                                status = doc.getString("status") ?: "",
                                timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                                assignedDriverId = doc.getString("assignedDriverId"),
                                assignedDriverName = doc.getString("assignedDriverName"),
                                callType = doc.getString("callType"),
                                departure = doc.getString("departure") ?: "",
                                destination = doc.getString("destination") ?: "",
                                waypoints = doc.getString("waypoints") ?: "",
                                fare = doc.getLong("fare")?.toInt() ?: 0,
                                paymentMethod = doc.getString("paymentMethod") ?: "",
                                tripSummary = doc.getString("tripSummary") ?: "",
                                departure_set = doc.getString("departure_set"),
                                destination_set = doc.getString("destination_set"),
                                waypoints_set = doc.getString("waypoints_set"),
                                fare_set = doc.getLong("fare_set")?.toInt()
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                    _calls.value = callList
                }
                _loading.value = false
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
                }
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
        callsListener?.remove()
        driversListener?.remove()
        pttManager?.destroy()
    }
    
    private fun initializePTT(regionId: String, officeId: String) {
        try {
            pttManager = PTTManager.getInstance(
                context = application,
                userType = "pickup_driver",
                regionId = regionId,
                officeId = officeId
            )
            
            pttManager?.initialize(object : PTTManager.PTTCallback {
                override fun onStatusChanged(status: String) {
                    _pttStatus.value = status
                    
                    // 채널명 업데이트 (콜매니저와 동일한 패턴)
                    val channelName = pttManager?.getCurrentChannelName()
                    if (!channelName.isNullOrBlank()) {
                        _pttChannelName.value = channelName
                    }
                }
                
                override fun onConnectionStateChanged(isConnected: Boolean) {
                    // 연결 상태 변화 시에도 채널명 업데이트
                    if (isConnected) {
                        val channelName = pttManager?.getCurrentChannelName()
                        if (!channelName.isNullOrBlank()) {
                            _pttChannelName.value = channelName
                        }
                    } else {
                        _pttChannelName.value = ""
                    }
                }
                
                override fun onSpeakingStateChanged(isSpeaking: Boolean) {
                    _isPttSpeaking.value = isSpeaking
                }
                
                override fun onError(error: String) {
                    _pttStatus.value = "오류: $error"
                    android.util.Log.e("HomeViewModel", "PTT 오류: $error")
                }
            })
            
            android.util.Log.i("HomeViewModel", "PTT 초기화 완료")
            
        } catch (e: Exception) {
            android.util.Log.e("HomeViewModel", "PTT 초기화 실패", e)
            _pttStatus.value = "PTT 초기화 실패"
        }
    }
    
    fun startPTT() {
        pttManager?.startPTT()
    }
    
    fun stopPTT() {
        pttManager?.stopPTT()
    }
    
    fun adjustPTTVolume(increase: Boolean) {
        pttManager?.adjustVolume(increase)
    }
    
    fun handlePTTVolumeDown(): Boolean {
        return pttManager?.handleVolumeDownPress() ?: false
    }
    
    fun handlePTTVolumeUp(): Boolean {
        return pttManager?.handleVolumeDownRelease() ?: false
    }
}
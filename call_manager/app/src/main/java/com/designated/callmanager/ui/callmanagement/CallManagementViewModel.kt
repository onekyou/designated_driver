package com.designated.callmanager.ui.callmanagement

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 실시간 호출 관리용 ViewModel
 */
class CallManagementViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CallManagementVM"
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val sharedPrefs = application.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

    private val _calls = MutableStateFlow<List<CallInfo>>(emptyList())
    val calls: StateFlow<List<CallInfo>> = _calls.asStateFlow()

    private val _regionId = MutableStateFlow<String?>(null)
    val regionId: StateFlow<String?> = _regionId.asStateFlow()

    private val _officeId = MutableStateFlow<String?>(null)
    val officeId: StateFlow<String?> = _officeId.asStateFlow()

    private var callsListener: ListenerRegistration? = null

    init {
        // 로그인 시 저장된 region/office 값을 불러와 곧바로 리스닝 시작
        val rId = sharedPrefs.getString("regionId", null)
        val oId = sharedPrefs.getString("officeId", null)
        if (!rId.isNullOrBlank() && !oId.isNullOrBlank()) {
            loadData(rId, oId)
        }
    }

    /** region/office 변경 시 수동 호출 */
    fun loadData(regionId: String, officeId: String) {
        stopListening()
        _regionId.value = regionId
        _officeId.value = officeId
        startListening(regionId, officeId)
    }

    private fun startListening(regionId: String, officeId: String) {
        val officeRef = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)

        callsListener = officeRef.collection("calls")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Calls listener error", e)
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        doc.toObject(CallInfo::class.java)?.apply { id = doc.id }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error parsing call doc ${doc.id}", ex)
                        null
                    }
                }
                _calls.value = list
            }
    }

    /** 기사에게 배정 */
    fun assignCall(callId: String, driverId: String, driverName: String) {
        val rId = _regionId.value ?: return
        val oId = _officeId.value ?: return
        viewModelScope.launch {
            try {
                val callRef = firestore.collection("regions").document(rId)
                    .collection("offices").document(oId)
                    .collection("calls").document(callId)
                val update = mapOf(
                    "assignedDriverId" to driverId,
                    "assignedDriverName" to driverName,
                    "status" to CallStatus.ASSIGNED.firestoreValue
                )
                callRef.update(update).await()
                Log.d(TAG, "Call $callId assigned to $driverName($driverId)")
            } catch (e: Exception) {
                Log.e(TAG, "assignCall error", e)
            }
        }
    }

    fun cancelCall(callId: String) {
        updateCallStatus(callId, CallStatus.CANCELED)
    }

    fun completeCall(callId: String) {
        updateCallStatus(callId, CallStatus.COMPLETED)
    }

    private fun updateCallStatus(callId: String, status: CallStatus) {
        val rId = _regionId.value ?: return
        val oId = _officeId.value ?: return
        viewModelScope.launch {
            try {
                firestore.collection("regions").document(rId)
                    .collection("offices").document(oId)
                    .collection("calls").document(callId)
                    .update("status", status.firestoreValue).await()
                Log.d(TAG, "Updated call $callId status -> ${status.name}")
            } catch (e: Exception) {
                Log.e(TAG, "updateCallStatus error", e)
            }
        }
    }

    private fun stopListening() {
        callsListener?.remove()
        callsListener = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
} 
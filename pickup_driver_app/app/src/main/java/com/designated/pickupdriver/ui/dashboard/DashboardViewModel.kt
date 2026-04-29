package com.designated.pickupdriver.ui.dashboard

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.data.local.LocalCall
import com.designated.pickupdriver.data.repository.CallRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CallItem(
    val callId: String,
    val status: String,
    val customerAddress: String?,
    val departureSet: String?,
    val waypointsSet: String?,
    val destinationSet: String?,
    val fareSet: Long?,
    val assignedDriverName: String?,
    val timestamp: Long
)

/**
 * 픽업앱 대시보드 ViewModel — FCM + Room 트리거 기반.
 *
 * - 기존 Firestore listener 제거 (Room Flow 단일 구독)
 * - 첫 진입 시 1회 refreshData (Firestore 활성 콜 fetch + Room upsert)
 * - 알림 발사는 MyFirebaseMessagingService 가 담당 (FCM 도착 시점)
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth,
    private val callRepository: CallRepository,
) : ViewModel() {
    private val TAG = "PickupDashboardVM"

    private val _calls = MutableStateFlow<List<CallItem>>(emptyList())
    val calls: StateFlow<List<CallItem>> = _calls.asStateFlow()

    private var collectJob: Job? = null

    fun startListening() {
        val p = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val c = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val o = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (p.isNullOrBlank() || c.isNullOrBlank() || o.isNullOrBlank()) {
            Log.w(TAG, "사무실 정보 누락: p=$p c=$c o=$o")
            return
        }

        collectJob?.cancel()

        viewModelScope.launch {
            callRepository.refreshData(p, c, o)
        }

        collectJob = viewModelScope.launch {
            callRepository.getActiveCallsFlow(p, c, o).collect { rows ->
                _calls.value = rows.map { it.toCallItem() }
            }
        }
    }

    fun stopListening() {
        collectJob?.cancel()
        collectJob = null
    }

    override fun onCleared() {
        stopListening()
    }

    fun logout() {
        val p = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val c = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val o = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)

        stopListening()
        viewModelScope.launch {
            if (!p.isNullOrBlank() && !c.isNullOrBlank() && !o.isNullOrBlank()) {
                callRepository.clearAllCallsInOffice(p, c, o)
            }
            auth.signOut()
            prefs.edit().clear().apply()
        }
    }

    private fun LocalCall.toCallItem(): CallItem = CallItem(
        callId = id,
        status = status,
        customerAddress = customerAddress,
        departureSet = departure,
        waypointsSet = waypoints,
        destinationSet = destination,
        fareSet = fare,
        assignedDriverName = assignedDriverName,
        timestamp = timestamp
    )
}

package com.designated.pickupdriver.ui.dashboard

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.data.local.LocalCall
import com.designated.pickupdriver.data.repository.CallRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
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

    /**
     * 로그아웃 = 업무 종료 → 모든 FCM 알림 즉시 차단.
     * 순서: ① pickup_drivers/{authUid}.fcmToken 삭제 → ② signOut → ③ deleteToken → ④ prefs.clear.
     * 모든 await에 5초 timeout.
     *
     * suspend로 작성된 이유: caller(DashboardScreen)가 navigate 직전에 await 완료를 보장해야
     * popUpTo(DASHBOARD, inclusive=true)로 viewModelScope가 cancel되어도 race 없음.
     */
    suspend fun logout() {
        val p = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val c = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val o = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)

        stopListening()
        if (!p.isNullOrBlank() && !c.isNullOrBlank() && !o.isNullOrBlank()) {
            callRepository.clearAllCallsInOffice(p, c, o)
        }

        // ① Firestore pickup_drivers/{authUid}.fcmToken 삭제
        val authUid = auth.currentUser?.uid
        if (authUid != null) {
            try {
                withTimeoutOrNull(5_000) {
                    val snapshot = FirebaseFirestore.getInstance()
                        .collectionGroup(Constants.COLLECTION_GROUP_PICKUP_DRIVERS)
                        .whereEqualTo(Constants.FIELD_AUTH_UID, authUid)
                        .limit(1)
                        .get().await()
                    snapshot.documents.firstOrNull()?.reference
                        ?.update(Constants.FIELD_FCM_TOKEN, FieldValue.delete())
                        ?.await()
                }
                Log.d(TAG, "[logout] pickup_drivers.fcmToken 삭제 처리")
            } catch (e: Exception) {
                Log.w(TAG, "[logout] pickup_drivers.fcmToken 삭제 실패: ${e.message}")
            }
        }

        // ② signOut
        auth.signOut()

        // ③ deleteToken
        try {
            withTimeoutOrNull(5_000) {
                FirebaseMessaging.getInstance().deleteToken().await()
            }
            Log.d(TAG, "[logout] deleteToken 처리")
        } catch (e: Exception) {
            Log.w(TAG, "[logout] deleteToken 실패: ${e.message}")
        }

        // ④ prefs.clear
        prefs.edit().clear().apply()
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

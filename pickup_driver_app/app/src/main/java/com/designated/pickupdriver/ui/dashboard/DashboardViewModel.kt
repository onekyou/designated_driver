package com.designated.pickupdriver.ui.dashboard

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import com.designated.pickupdriver.data.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val prefs: SharedPreferences,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {
    private val TAG = "PickupDashboardVM"

    private val _calls = MutableStateFlow<List<CallItem>>(emptyList())
    val calls: StateFlow<List<CallItem>> = _calls.asStateFlow()

    private var listener: ListenerRegistration? = null

    fun startListening() {
        val p = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val c = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val o = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (p.isNullOrBlank() || c.isNullOrBlank() || o.isNullOrBlank()) {
            Log.w(TAG, "사무실 정보 누락: p=$p c=$c o=$o")
            return
        }

        listener?.remove()
        listener = firestore.collection(Constants.COLLECTION_PROVINCES).document(p)
            .collection(Constants.COLLECTION_CITIES).document(c)
            .collection(Constants.COLLECTION_OFFICES).document(o)
            .collection(Constants.COLLECTION_CALLS)
            .whereIn("status", listOf(
                Constants.STATUS_WAITING,
                Constants.STATUS_ASSIGNED,
                Constants.STATUS_ACCEPTED,
                Constants.STATUS_IN_PROGRESS,
                Constants.STATUS_AWAITING_SETTLEMENT
            ))
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "listener 에러", err)
                    return@addSnapshotListener
                }
                if (snap == null) {
                    _calls.value = emptyList()
                    return@addSnapshotListener
                }
                _calls.value = snap.documents.map { d ->
                    CallItem(
                        callId = d.id,
                        status = d.getString("status") ?: "",
                        customerAddress = d.getString("customerAddress"),
                        departureSet = d.getString("departure_set"),
                        waypointsSet = d.getString("waypoints_set"),
                        destinationSet = d.getString("destination_set"),
                        fareSet = d.getLong("fare_set"),
                        assignedDriverName = d.getString("assignedDriverName"),
                        timestamp = d.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    )
                }.sortedByDescending { it.timestamp }
            }
    }

    fun stopListening() {
        listener?.remove()
        listener = null
    }

    override fun onCleared() {
        stopListening()
    }

    fun logout() {
        stopListening()
        auth.signOut()
        prefs.edit().clear().apply()
    }
}

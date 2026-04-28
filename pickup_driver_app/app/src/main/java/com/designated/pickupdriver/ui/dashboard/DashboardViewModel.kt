package com.designated.pickupdriver.ui.dashboard

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import com.designated.pickupdriver.MainActivity
import com.designated.pickupdriver.PickupDriverApplication
import com.designated.pickupdriver.R
import com.designated.pickupdriver.data.Constants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val TAG = "PickupDashboardVM"

    private val _calls = MutableStateFlow<List<CallItem>>(emptyList())
    val calls: StateFlow<List<CallItem>> = _calls.asStateFlow()

    private var listener: ListenerRegistration? = null
    private val previousStatuses = mutableMapOf<String, String>()
    private var initialSnapshotProcessed = false

    fun startListening() {
        val p = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val c = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val o = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
        if (p.isNullOrBlank() || c.isNullOrBlank() || o.isNullOrBlank()) {
            Log.w(TAG, "사무실 정보 누락: p=$p c=$c o=$o")
            return
        }

        listener?.remove()
        previousStatuses.clear()
        initialSnapshotProcessed = false

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

                handleStatusChangeNotifications(snap.documentChanges)

                val cutoff30m = System.currentTimeMillis() - WAITING_CUTOFF_MS
                _calls.value = snap.documents.map { it.toCallItem() }
                    .filter { item ->
                        item.status != Constants.STATUS_WAITING || item.timestamp >= cutoff30m
                    }
                    .sortedByDescending { it.timestamp }
            }
    }

    private fun handleStatusChangeNotifications(changes: List<DocumentChange>) {
        if (!initialSnapshotProcessed) {
            changes.forEach { change ->
                previousStatuses[change.document.id] =
                    change.document.getString("status") ?: ""
            }
            initialSnapshotProcessed = true
            return
        }

        changes.forEach { change ->
            val doc = change.document
            val callId = doc.id
            val currentStatus = doc.getString("status") ?: ""
            when (change.type) {
                DocumentChange.Type.ADDED -> {
                    notifyChange(callId, "신규 콜", buildBody(doc))
                }
                DocumentChange.Type.MODIFIED -> {
                    val prev = previousStatuses[callId]
                    if (prev != null && prev != currentStatus) {
                        notifyChange(callId, statusLabel(currentStatus), buildBody(doc))
                    }
                }
                DocumentChange.Type.REMOVED -> { /* skip */ }
            }
            previousStatuses[callId] = currentStatus
        }
    }

    private fun buildBody(doc: DocumentSnapshot): String {
        val departure = doc.getString("departure_set")?.takeIf { it.isNotBlank() }
        val destination = doc.getString("destination_set")?.takeIf { it.isNotBlank() }
        val customerAddress = doc.getString("customerAddress")?.takeIf { it.isNotBlank() }
        return when {
            departure != null && destination != null -> "$departure → $destination"
            departure != null -> departure
            destination != null -> "→ $destination"
            customerAddress != null -> customerAddress
            else -> "출발지 정보 없음"
        }
    }

    private fun statusLabel(status: String): String = when (status) {
        Constants.STATUS_WAITING -> "대기"
        Constants.STATUS_ASSIGNED -> "배차됨"
        Constants.STATUS_ACCEPTED -> "수락"
        Constants.STATUS_IN_PROGRESS -> "운행중"
        Constants.STATUS_AWAITING_SETTLEMENT -> "정산대기"
        else -> status
    }

    private fun notifyChange(callId: String, title: String, body: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context, PickupDriverApplication.CHANNEL_CALL_CHANGES
        )
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(callId.hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS 권한 없음 — 알림 발사 스킵", e)
        }
    }

    private fun DocumentSnapshot.toCallItem() = CallItem(
        callId = id,
        status = getString("status") ?: "",
        customerAddress = getString("customerAddress"),
        departureSet = getString("departure_set"),
        waypointsSet = getString("waypoints_set"),
        destinationSet = getString("destination_set"),
        fareSet = getLong("fare_set"),
        assignedDriverName = getString("assignedDriverName"),
        timestamp = getTimestamp("timestamp")?.toDate()?.time ?: 0L
    )

    fun stopListening() {
        listener?.remove()
        listener = null
        previousStatuses.clear()
        initialSnapshotProcessed = false
    }

    override fun onCleared() {
        stopListening()
    }

    fun logout() {
        stopListening()
        auth.signOut()
        prefs.edit().clear().apply()
    }

    companion object {
        private const val WAITING_CUTOFF_MS = 30L * 60L * 1000L
    }
}

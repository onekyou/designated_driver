package com.designated.driver.ui.home

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.designated.driver.model.CallInfo // Import the data class
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "DriverViewModel"

class DriverViewModel(application: Application) : AndroidViewModel(application) {

    private val auth: FirebaseAuth = Firebase.auth
    private val firestore = FirebaseFirestore.getInstance()
    private val callsCollection = firestore.collection("daeri_calls")

    private val _assignedCalls = MutableStateFlow<List<CallInfo>>(emptyList())
    val assignedCalls: StateFlow<List<CallInfo>> = _assignedCalls

    private var assignedCallsListener: ListenerRegistration? = null

    init {
        startListeningForAssignedCalls()
    }

    private fun startListeningForAssignedCalls() {
        val driverId = auth.currentUser?.uid
        if (driverId == null) {
            Log.e(TAG, "Cannot listen for calls, user not logged in.")
            _assignedCalls.value = emptyList() // Clear list if user logs out
            return
        }

        Log.d(TAG, "Starting listener for calls assigned to driver: $driverId")

        // Stop any previous listener
        assignedCallsListener?.remove()

        // Query for calls assigned to this driver with status "배정됨"
        assignedCallsListener = callsCollection
            .whereEqualTo("assignedDriverId", driverId)
            .whereEqualTo("status", "배정됨") // Listen for assigned calls
            // Optionally order by timestamp or other fields
            .orderBy("timestamp", Query.Direction.DESCENDING) 
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening for assigned calls: ", e)
                    _assignedCalls.value = emptyList() // Clear list on error
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val callsList = snapshot.documents.mapNotNull { doc ->
                        try {
                            // Use the shared CallInfo data class structure
                            // Need to adjust parsing based on the actual Firestore document structure
                            val timestamp = when (val ts = doc.get("timestamp")) {
                                is Timestamp -> ts.toDate().time
                                is Long -> ts
                                is Number -> ts.toLong()
                                else -> 0L // Default or error value
                            }
                            CallInfo(
                                id = doc.id,
                                customerName = doc.getString("customerName") ?: "",
                                phoneNumber = doc.getString("phoneNumber") ?: "",
                                location = doc.getString("location") ?: "",
                                timestamp = timestamp,
                                status = doc.getString("status") ?: "",
                                assignedDriverId = doc.getString("assignedDriverId"),
                                assignedDriverName = doc.getString("assignedDriverName"),
                                assignedTimestamp = doc.getLong("assignedTimestamp")
                                // Include other relevant fields from CallInfo
                            )
                        } catch (ex: Exception) {
                            Log.e(TAG, "Error parsing call document ${doc.id}", ex)
                            null
                        }
                    }
                    Log.d(TAG, "Received ${callsList.size} assigned calls.")
                    _assignedCalls.value = callsList
                } else {
                    Log.d(TAG, "Assigned calls snapshot is null")
                    _assignedCalls.value = emptyList()
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "Stopping listener for assigned calls.")
        assignedCallsListener?.remove() // Clean up the listener
    }
} 
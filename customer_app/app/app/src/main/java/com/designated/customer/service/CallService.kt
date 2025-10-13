package com.designated.customer.service

import com.designated.customer.data.model.CustomerCall
import com.designated.customer.ui.main.CallState
import com.designated.customer.ui.main.CallStatus
import com.designated.customer.ui.main.DriverInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CallService(
    private val regionId: String = "seoul",
    private val officeId: String
) {
    private val firestore = FirebaseFirestore.getInstance()
    private var callStatusListener: ListenerRegistration? = null

    suspend fun requestCall(call: CustomerCall): String {
        val callsCollection = firestore
            .collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
        val documentRef = callsCollection.document()

        val callWithId = call.copy(id = documentRef.id)

        documentRef.set(callWithId.toMap()).await()

        return documentRef.id
    }

    suspend fun cancelCall(callId: String): Boolean {
        return try {
            firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("calls")
                .document(callId)
                .update("status", "CANCELLED")
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun monitorCallStatus(
        phoneNumber: String,
        onStatusUpdate: (CallStatus?) -> Unit
    ) {
        callStatusListener?.remove()

        callStatusListener = firestore
            .collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("phoneNumber", phoneNumber)
            .whereIn("status", listOf("REQUESTED", "ASSIGNED", "DRIVER_ARRIVING", "IN_PROGRESS"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onStatusUpdate(null)
                    return@addSnapshotListener
                }

                val activeCalls = snapshot?.documents?.mapNotNull { doc ->
                    CustomerCall.fromMap(doc.data ?: return@mapNotNull null)
                } ?: emptyList()

                // 가장 최근의 활성 콜 가져오기
                val latestCall = activeCalls.maxByOrNull { it.timestamp }

                if (latestCall != null) {
                    val callStatus = CallStatus(
                        callId = latestCall.id,
                        state = when (latestCall.status) {
                            "REQUESTED" -> CallState.REQUESTED
                            "ASSIGNED" -> CallState.ASSIGNED
                            "DRIVER_ARRIVING" -> CallState.DRIVER_ARRIVING
                            "IN_PROGRESS" -> CallState.IN_PROGRESS
                            "COMPLETED" -> CallState.COMPLETED
                            "CANCELLED" -> CallState.CANCELLED
                            else -> CallState.REQUESTED
                        },
                        timestamp = latestCall.timestamp,
                        driverInfo = if (latestCall.driverId != null) {
                            // 실제로는 드라이버 정보를 별도로 가져와야 함
                            DriverInfo(
                                id = latestCall.driverId,
                                name = "기사",
                                phoneNumber = "",
                                vehicleNumber = ""
                            )
                        } else null,
                        estimatedArrivalTime = latestCall.estimatedArrivalTime ?: 0
                    )
                    onStatusUpdate(callStatus)
                } else {
                    onStatusUpdate(null)
                }
            }
    }

    fun stopMonitoring() {
        callStatusListener?.remove()
        callStatusListener = null
    }

    /**
     * 고객의 콜 내역 조회
     */
    suspend fun getCustomerCallHistory(
        phoneNumber: String,
        limit: Long = 50
    ): List<CustomerCall> {
        return try {
            val snapshot = firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("calls")
                .whereEqualTo("phoneNumber", phoneNumber)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { CustomerCall.fromMap(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 고객의 콜 내역 실시간 모니터링
     */
    fun observeCustomerCalls(phoneNumber: String): Flow<List<CustomerCall>> = callbackFlow {
        val listener = firestore
            .collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("phoneNumber", phoneNumber)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val calls = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { CustomerCall.fromMap(it) }
                } ?: emptyList()

                trySend(calls)
            }

        awaitClose { listener.remove() }
    }

    /**
     * 콜 완료 시 요금 확정 및 포인트 할인 처리
     */
    suspend fun completeFareWithPointDiscount(
        callId: String,
        originalFare: Int,
        pointsUsed: Int
    ): Boolean {
        return try {
            val discountedFare = maxOf(0, originalFare - pointsUsed)

            firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("calls")
                .document(callId)
                .update(
                    mapOf(
                        "fare" to originalFare,
                        "pointsUsed" to pointsUsed,
                        "finalFare" to discountedFare,
                        "discountAmount" to pointsUsed
                    )
                )
                .await()
            true
        } catch (e: Exception) {
            false
        }
    }
}
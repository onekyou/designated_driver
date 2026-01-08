package com.designated.customer.service

import com.designated.customer.data.model.CustomerCall
import com.google.firebase.firestore.FirebaseFirestore
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
        android.util.Log.d("CallService", "cancelCall: callId=$callId, regionId=$regionId, officeId=$officeId")
        return try {
            firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("calls")
                .document(callId)
                .update("status", "CANCELLED")
                .await()
            android.util.Log.d("CallService", "cancelCall: success")
            true
        } catch (e: Exception) {
            android.util.Log.e("CallService", "cancelCall: failed", e)
            false
        }
    }

    /**
     * 고객의 콜 내역 조회
     */
    suspend fun getCustomerCallHistory(
        phoneNumber: String,
        limit: Long = 50
    ): List<CustomerCall> {
        android.util.Log.d("CallService", "getCustomerCallHistory 시작: phoneNumber=$phoneNumber, regionId=$regionId, officeId=$officeId")
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

            val calls = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { CustomerCall.fromMap(it) }
            }
            android.util.Log.d("CallService", "getCustomerCallHistory 완료: ${calls.size}개의 콜 조회됨")
            calls
        } catch (e: Exception) {
            android.util.Log.e("CallService", "getCustomerCallHistory 오류", e)
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
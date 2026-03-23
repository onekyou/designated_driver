package com.designated.customer.service

import com.designated.customer.data.model.CustomerCall
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class CallService(
    private val provinceId: String,
    private val cityId: String,
    private val officeId: String
) {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun requestCall(call: CustomerCall): String {
        val callsCollection = firestore
            .collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
        val documentRef = callsCollection.document()

        val callWithId = call.copy(id = documentRef.id)

        documentRef.set(callWithId.toMap()).await()

        return documentRef.id
    }

    suspend fun cancelCall(callId: String): Boolean {
        android.util.Log.d("CallService", "cancelCall: callId=$callId, provinceId=$provinceId, cityId=$cityId, officeId=$officeId")
        return try {
            val callRef = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("calls")
                .document(callId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(callRef)
                val currentStatus = snapshot.getString("status") ?: ""

                // 이미 취소된 상태면 중복 취소 방지
                if (currentStatus == "CANCELED" || currentStatus == "CANCELLED_BY_DRIVER" ||
                    currentStatus == "CANCELLED_BY_CUSTOMER") {
                    throw IllegalStateException("ALREADY_CANCELLED")
                }

                // WAITING, ASSIGNED, ACCEPTED, PREPARING 상태에서 고객이 직접 취소 가능
                if (currentStatus != "WAITING" && currentStatus != "ASSIGNED" &&
                    currentStatus != "ACCEPTED" && currentStatus != "PREPARING") {
                    throw IllegalStateException("CANNOT_CANCEL: status=$currentStatus")
                }

                transaction.update(callRef, "status", "CANCELLED_BY_CUSTOMER")
            }.await()

            android.util.Log.d("CallService", "cancelCall: success")
            true
        } catch (e: Exception) {
            android.util.Log.e("CallService", "cancelCall: failed", e)
            false
        }
    }

    /**
     * 고객의 활성 콜 1건 조회 (FCM 미수신 시 fallback)
     * WAITING, ASSIGNED, ACCEPTED, IN_PROGRESS 상태의 최신 콜 반환
     */
    suspend fun getActiveCall(phoneNumber: String): CustomerCall? {
        val activeStatuses = listOf("WAITING", "ASSIGNED", "ACCEPTED", "PREPARING", "IN_PROGRESS")
        return try {
            for (status in activeStatuses) {
                val snapshot = firestore
                    .collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId)
                    .collection("calls")
                    .whereEqualTo("phoneNumber", phoneNumber)
                    .whereEqualTo("status", status)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                val call = snapshot.documents.firstOrNull()?.data?.let { CustomerCall.fromMap(it) }
                if (call != null) {
                    android.util.Log.d("CallService", "활성 콜 발견: ${call.id}, status=$status")
                    return call
                }
            }
            android.util.Log.d("CallService", "활성 콜 없음")
            null
        } catch (e: Exception) {
            android.util.Log.e("CallService", "활성 콜 조회 오류", e)
            null
        }
    }

    /**
     * 기사 정보 조회 (활성 콜 복구 시 사용)
     */
    suspend fun getDriverInfo(driverAuthUid: String): Map<String, Any>? {
        return try {
            val driversQuery = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("designated_drivers")
                .whereEqualTo("authUid", driverAuthUid)
                .limit(1)
                .get()
                .await()

            if (!driversQuery.isEmpty) {
                driversQuery.documents[0].data
            } else null
        } catch (e: Exception) {
            android.util.Log.e("CallService", "기사 정보 조회 실패: ${e.message}")
            null
        }
    }

    /**
     * 고객의 콜 내역 조회
     */
    suspend fun getCustomerCallHistory(
        phoneNumber: String,
        limit: Long = 50
    ): List<CustomerCall> {
        android.util.Log.d("CallService", "getCustomerCallHistory 시작: phoneNumber=$phoneNumber, provinceId=$provinceId, cityId=$cityId, officeId=$officeId")
        return try {
            val snapshot = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
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
            .collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
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
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
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
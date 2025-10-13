package com.designated.customer.service

import com.designated.customer.data.model.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * 포인트 관련 서비스
 */
class PointService(
    private val regionId: String = "seoul",
    private val officeId: String
) {
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * 고객 포인트 정보 조회
     */
    suspend fun getCustomerPoints(phoneNumber: String): CustomerPoints? {
        return try {
            val doc = firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("customerPoints")
                .document(phoneNumber)
                .get()
                .await()

            if (doc.exists()) {
                CustomerPoints.fromMap(doc.data ?: emptyMap())
            } else {
                // 신규 고객인 경우 초기 포인트 정보 생성
                val newPoints = CustomerPoints(
                    customerId = phoneNumber,
                    phoneNumber = phoneNumber,
                    currentPoints = 0,
                    totalEarned = 0,
                    totalUsed = 0,
                    grade = CustomerGrade.BRONZE,
                    totalCalls = 0
                )
                createCustomerPoints(newPoints)
                newPoints
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 고객 포인트 정보 실시간 모니터링
     */
    fun observeCustomerPoints(phoneNumber: String): Flow<CustomerPoints?> = callbackFlow {
        val listener = firestore
            .collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("customerPoints")
            .document(phoneNumber)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }

                if (snapshot?.exists() == true) {
                    val points = CustomerPoints.fromMap(snapshot.data ?: emptyMap())
                    trySend(points)
                } else {
                    trySend(null)
                }
            }

        awaitClose { listener.remove() }
    }

    /**
     * 고객 포인트 정보 생성
     */
    private suspend fun createCustomerPoints(points: CustomerPoints) {
        firestore
            .collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("customerPoints")
            .document(points.phoneNumber)
            .set(points.toMap())
            .await()
    }

    /**
     * 포인트 적립
     */
    suspend fun earnPoints(
        phoneNumber: String,
        callId: String,
        fare: Int,
        description: String? = null
    ): Boolean {
        return try {
            val points = getCustomerPoints(phoneNumber) ?: return false

            // 적립 포인트 계산
            val earnAmount = points.calculateEarnPoints(fare)
            val newBalance = points.currentPoints + earnAmount
            val newTotalCalls = points.totalCalls + 1

            // 등급 업데이트 확인
            val newGrade = CustomerGrade.fromCallCount(newTotalCalls)

            // 포인트 정보 업데이트
            val updatedPoints = points.copy(
                currentPoints = newBalance,
                totalEarned = points.totalEarned + earnAmount,
                totalCalls = newTotalCalls,
                grade = newGrade,
                lastUpdated = Timestamp.now()
            )

            // 거래 내역 생성
            val pointTransaction = PointTransaction(
                id = UUID.randomUUID().toString(),
                customerId = phoneNumber,
                type = TransactionType.EARN,
                amount = earnAmount,
                balance = newBalance,
                description = description ?: "대리운전 이용 포인트 적립",
                callId = callId,
                fare = fare,
                grade = newGrade.name,
                timestamp = Timestamp.now()
            )

            // Firestore 업데이트 (트랜잭션)
            firestore.runTransaction { transaction ->
                // 포인트 정보 업데이트
                val pointsRef = firestore
                    .collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("customerPoints")
                    .document(phoneNumber)

                transaction.set(pointsRef, updatedPoints.toMap())

                // 거래 내역 추가
                val transactionRef = firestore
                    .collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("pointTransactions")
                    .document()

                transaction.set(transactionRef, pointTransaction.toMap())
            }.await()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 포인트 사용
     */
    suspend fun usePoints(
        phoneNumber: String,
        amount: Int,
        description: String = "포인트 사용"
    ): Boolean {
        return try {
            val points = getCustomerPoints(phoneNumber) ?: return false

            // 사용 가능 여부 확인
            if (!points.canUsePoints(amount)) {
                return false
            }

            val newBalance = points.currentPoints - amount

            // 포인트 정보 업데이트
            val updatedPoints = points.copy(
                currentPoints = newBalance,
                totalUsed = points.totalUsed + amount,
                lastUpdated = Timestamp.now()
            )

            // 거래 내역 생성
            val pointTransaction = PointTransaction(
                id = UUID.randomUUID().toString(),
                customerId = phoneNumber,
                type = TransactionType.USE,
                amount = -amount,  // 사용은 음수로 표시
                balance = newBalance,
                description = description,
                grade = points.grade.name,
                timestamp = Timestamp.now()
            )

            // Firestore 업데이트
            firestore.runTransaction { transaction ->
                // 포인트 정보 업데이트
                val pointsRef = firestore
                    .collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("customerPoints")
                    .document(phoneNumber)

                transaction.set(pointsRef, updatedPoints.toMap())

                // 거래 내역 추가
                val transactionRef = firestore
                    .collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("pointTransactions")
                    .document()

                transaction.set(transactionRef, pointTransaction.toMap())
            }.await()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 포인트 거래 내역 조회
     */
    suspend fun getPointTransactions(
        phoneNumber: String,
        limit: Long = 20
    ): List<PointTransaction> {
        return try {
            val snapshot = firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("pointTransactions")
                .whereEqualTo("customerId", phoneNumber)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                doc.data?.let { PointTransaction.fromMap(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 포인트 거래 내역 실시간 모니터링
     */
    fun observePointTransactions(phoneNumber: String): Flow<List<PointTransaction>> = callbackFlow {
        val listener = firestore
            .collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("pointTransactions")
            .whereEqualTo("customerId", phoneNumber)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val transactions = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { PointTransaction.fromMap(it) }
                } ?: emptyList()

                trySend(transactions)
            }

        awaitClose { listener.remove() }
    }
}
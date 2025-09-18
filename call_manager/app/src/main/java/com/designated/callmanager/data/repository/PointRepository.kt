package com.designated.callmanager.data.repository

import android.util.Log
import com.designated.callmanager.data.PointTransaction
import com.designated.callmanager.data.PointsInfo
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.local.LocalPointTransaction
import com.designated.callmanager.data.local.LocalPointsInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 포인트 관련 데이터를 관리하는 Repository
 * - 로컬 우선 읽기 (Room Database)
 * - 백그라운드 Firebase 동기화
 * - 비용 최적화 (필요할 때만 Firebase 읽기)
 */
class PointRepository(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "PointRepository"
        private const val SYNC_BATCH_SIZE = 50L
    }

    private val pointTransactionDao = database.pointTransactionDao()
    private val pointsInfoDao = database.pointsInfoDao()

    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    private var syncListener: ListenerRegistration? = null

    // ====== 포인트 잔액 관리 ======

    /**
     * 포인트 잔액 정보 Flow (로컬 우선)
     */
    fun getPointsInfoFlow(regionId: String, officeId: String): Flow<PointsInfo?> {
        ensureSyncSetup(regionId, officeId)

        return pointsInfoDao.getPointsInfoFlow(regionId, officeId)
            .combine(getTransactionsFlow(regionId, officeId)) { localPointsInfo, transactions ->
                // 로컬 잔액이 있으면 사용, 없으면 거래 내역으로 계산
                localPointsInfo?.toFirebasePointsInfo() ?: run {
                    val calculatedBalance = transactions.sumOf { it.amount }
                    PointsInfo(balance = calculatedBalance)
                }
            }
    }

    /**
     * 포인트 잔액 업데이트 (로컬 + Firebase)
     */
    suspend fun updateBalance(regionId: String, officeId: String, newBalance: Int) {
        try {
            val now = System.currentTimeMillis()

            // 1. 로컬 업데이트
            val localPointsInfo = LocalPointsInfo(
                id = LocalPointsInfo.generateId(regionId, officeId),
                balance = newBalance,
                updatedAt = now,
                regionId = regionId,
                officeId = officeId,
                synced = false, // Firebase 동기화 전
                lastUpdated = now
            )
            pointsInfoDao.insertPointsInfo(localPointsInfo)

            // 2. Firebase 업데이트 (백그라운드)
            scope.launch {
                try {
                    val firebasePointsInfo = PointsInfo(
                        balance = newBalance,
                        updatedAt = com.google.firebase.Timestamp.now()
                    )

                    firestore.collection("regions").document(regionId)
                        .collection("offices").document(officeId)
                        .collection("points").document("points")
                        .set(firebasePointsInfo)
                        .await()

                    // 동기화 완료 표시
                    pointsInfoDao.updateSyncStatus(regionId, officeId, true, System.currentTimeMillis())

                } catch (e: Exception) {
                    Log.e(TAG, "Firebase 잔액 업데이트 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "로컬 잔액 업데이트 실패", e)
            throw e
        }
    }

    // ====== 거래 내역 관리 ======

    /**
     * 거래 내역 Flow (로컬 우선)
     */
    fun getTransactionsFlow(regionId: String, officeId: String): Flow<List<PointTransaction>> {
        ensureSyncSetup(regionId, officeId)

        return pointTransactionDao.getTransactionsByOffice(regionId, officeId)
            .combine(pointsInfoDao.getPointsInfoFlow(regionId, officeId)) { transactions, _ ->
                transactions.map { it.toFirebaseTransaction() }
            }
    }

    /**
     * 최근 거래 내역 조회 (제한된 개수)
     */
    suspend fun getRecentTransactions(regionId: String, officeId: String, limit: Int): List<PointTransaction> {
        ensureSyncSetup(regionId, officeId)

        val localTransactions = pointTransactionDao.getRecentTransactions(regionId, officeId, limit)
        return localTransactions.map { it.toFirebaseTransaction() }
    }

    /**
     * 새 거래 내역 추가
     */
    suspend fun addTransaction(
        regionId: String,
        officeId: String,
        transaction: PointTransaction
    ) {
        try {
            // 1. 로컬에 저장
            val localTransaction = LocalPointTransaction.fromFirebaseTransaction(
                transaction,
                regionId,
                officeId
            ).copy(synced = false) // Firebase 동기화 전

            pointTransactionDao.insertTransaction(localTransaction)

            // 2. 잔액 업데이트
            val currentBalance = pointsInfoDao.getPointsInfo(regionId, officeId)?.balance ?: 0
            val newBalance = currentBalance + transaction.amount
            updateBalance(regionId, officeId, newBalance)

            // 3. Firebase 동기화 (백그라운드)
            scope.launch {
                try {
                    firestore.collection("regions").document(regionId)
                        .collection("offices").document(officeId)
                        .collection("point_transactions")
                        .add(transaction)
                        .await()

                    // 동기화 완료 표시
                    pointTransactionDao.updateSyncStatus(transaction.id, true, System.currentTimeMillis())

                } catch (e: Exception) {
                    Log.e(TAG, "Firebase 거래 추가 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "거래 추가 실패", e)
            throw e
        }
    }

    // ====== 동기화 관리 ======

    /**
     * 동기화 설정 확인 및 초기화
     */
    private fun ensureSyncSetup(regionId: String, officeId: String) {
        if (currentRegionId != regionId || currentOfficeId != officeId) {
            stopSync()
            startSync(regionId, officeId)
            currentRegionId = regionId
            currentOfficeId = officeId
        }
    }

    /**
     * Firebase 실시간 동기화 시작
     */
    private fun startSync(regionId: String, officeId: String) {
        Log.d(TAG, "포인트 동기화 시작: $regionId/$officeId")

        val officeRef = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)

        // 새로운 거래만 동기화 (전체 다시 읽기 방지)
        syncListener = officeRef.collection("point_transactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10) // 최근 10개만 감시
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "동기화 리스너 오류", e)
                    return@addSnapshotListener
                }

                scope.launch {
                    try {
                        syncNewTransactions(snapshots?.documents ?: emptyList(), regionId, officeId)
                    } catch (ex: Exception) {
                        Log.e(TAG, "거래 동기화 실패", ex)
                    }
                }
            }
    }

    /**
     * 새로운 거래 내역 동기화
     */
    private suspend fun syncNewTransactions(
        firebaseDocuments: List<com.google.firebase.firestore.DocumentSnapshot>,
        regionId: String,
        officeId: String
    ) {
        val lastLocalTimestamp = pointTransactionDao.getLastTransactionTimestamp(regionId, officeId) ?: 0

        val newTransactions = firebaseDocuments.mapNotNull { doc ->
            try {
                val transaction = doc.toObject(PointTransaction::class.java)?.apply { id = doc.id }
                val timestamp = transaction?.timestamp?.toDate()?.time ?: 0

                // 로컬에 없는 새로운 거래만 동기화
                if (transaction != null && timestamp > lastLocalTimestamp) {
                    LocalPointTransaction.fromFirebaseTransaction(transaction, regionId, officeId)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "거래 파싱 실패: ${doc.id}", e)
                null
            }
        }

        if (newTransactions.isNotEmpty()) {
            pointTransactionDao.insertTransactions(newTransactions)
            Log.d(TAG, "새 거래 ${newTransactions.size}개 동기화 완료")
        }
    }

    /**
     * 전체 데이터 새로고침 (수동 동기화)
     */
    suspend fun refreshData(regionId: String, officeId: String) {
        Log.d(TAG, "수동 데이터 새로고침 시작")

        try {
            val officeRef = firestore.collection("regions").document(regionId)
                .collection("offices").document(officeId)

            // 1. 포인트 잔액 동기화
            val pointsSnapshot = officeRef.collection("points").document("points").get().await()
            if (pointsSnapshot.exists()) {
                val firebasePointsInfo = pointsSnapshot.toObject(PointsInfo::class.java)
                if (firebasePointsInfo != null) {
                    val localPointsInfo = LocalPointsInfo.fromFirebasePointsInfo(
                        firebasePointsInfo, regionId, officeId
                    )
                    pointsInfoDao.insertPointsInfo(localPointsInfo)
                }
            }

            // 2. 거래 내역 동기화 (최근 50개)
            val transactionsSnapshot = officeRef.collection("point_transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(SYNC_BATCH_SIZE)
                .get()
                .await()

            val firebaseTransactions = transactionsSnapshot.documents.mapNotNull { doc ->
                try {
                    val transaction = doc.toObject(PointTransaction::class.java)?.apply { id = doc.id }
                    if (transaction != null) {
                        LocalPointTransaction.fromFirebaseTransaction(transaction, regionId, officeId)
                    } else null
                } catch (e: Exception) {
                    Log.e(TAG, "거래 파싱 실패: ${doc.id}", e)
                    null
                }
            }

            if (firebaseTransactions.isNotEmpty()) {
                pointTransactionDao.insertTransactions(firebaseTransactions)
            }

            Log.d(TAG, "데이터 새로고침 완료: 거래 ${firebaseTransactions.size}개")

        } catch (e: Exception) {
            Log.e(TAG, "데이터 새로고침 실패", e)
            throw e
        }
    }

    /**
     * 동기화 중지
     */
    fun stopSync() {
        syncListener?.remove()
        syncListener = null
        currentRegionId = null
        currentOfficeId = null
        Log.d(TAG, "포인트 동기화 중지")
    }

    // ====== 유틸리티 ======

    /**
     * 잔액 정합성 검증
     */
    suspend fun validateBalance(regionId: String, officeId: String): Boolean {
        try {
            val storedBalance = pointsInfoDao.getPointsInfo(regionId, officeId)?.balance ?: 0
            val calculatedBalance = pointTransactionDao.getCalculatedBalance(regionId, officeId)

            val isValid = storedBalance == calculatedBalance
            if (!isValid) {
                Log.w(TAG, "잔액 불일치: 저장값=$storedBalance, 계산값=$calculatedBalance")
            }

            return isValid
        } catch (e: Exception) {
            Log.e(TAG, "잔액 검증 실패", e)
            return false
        }
    }

    /**
     * 오래된 데이터 정리
     */
    suspend fun cleanupOldData(beforeDays: Int = 30) {
        try {
            val cutoffTime = System.currentTimeMillis() - (beforeDays * 24 * 60 * 60 * 1000L)
            val deletedCount = pointTransactionDao.deleteOldTransactions(cutoffTime)
            Log.d(TAG, "오래된 거래 ${deletedCount}개 정리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "데이터 정리 실패", e)
        }
    }
}
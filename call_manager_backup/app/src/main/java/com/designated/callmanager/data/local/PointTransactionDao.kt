package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 포인트 거래 내역 데이터 접근 객체
 */
@Dao
interface PointTransactionDao {

    // ====== 조회 ======

    /**
     * 특정 사무실의 모든 거래 내역 조회 (최신순)
     */
    @Query("""
        SELECT * FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
        ORDER BY timestamp DESC
    """)
    fun getTransactionsByOffice(regionId: String, officeId: String): Flow<List<LocalPointTransaction>>

    /**
     * 특정 사무실의 거래 내역 조회 (제한된 개수, 최신순)
     */
    @Query("""
        SELECT * FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentTransactions(regionId: String, officeId: String, limit: Int): List<LocalPointTransaction>

    /**
     * 특정 타임스탬프 이후의 거래 내역 조회 (동기화용)
     */
    @Query("""
        SELECT * FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
        AND timestamp > :afterTimestamp
        ORDER BY timestamp DESC
    """)
    suspend fun getTransactionsAfter(regionId: String, officeId: String, afterTimestamp: Long): List<LocalPointTransaction>

    /**
     * 동기화되지 않은 거래 내역 조회
     */
    @Query("""
        SELECT * FROM point_transactions
        WHERE synced = 0
        ORDER BY timestamp ASC
    """)
    suspend fun getUnsyncedTransactions(): List<LocalPointTransaction>

    /**
     * 특정 공유콜과 관련된 거래 내역 조회
     */
    @Query("""
        SELECT * FROM point_transactions
        WHERE relatedSharedCallId = :sharedCallId
    """)
    suspend fun getTransactionsBySharedCall(sharedCallId: String): List<LocalPointTransaction>

    /**
     * 거래 타입별 조회
     */
    @Query("""
        SELECT * FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
        AND type = :type
        ORDER BY timestamp DESC
    """)
    suspend fun getTransactionsByType(regionId: String, officeId: String, type: String): List<LocalPointTransaction>

    // ====== 삽입/업데이트 ======

    /**
     * 거래 내역 삽입 (중복 시 교체)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: LocalPointTransaction)

    /**
     * 여러 거래 내역 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<LocalPointTransaction>)

    /**
     * 거래 내역 업데이트
     */
    @Update
    suspend fun updateTransaction(transaction: LocalPointTransaction)

    /**
     * 동기화 상태 업데이트
     */
    @Query("""
        UPDATE point_transactions
        SET synced = :synced, lastUpdated = :lastUpdated
        WHERE id = :transactionId
    """)
    suspend fun updateSyncStatus(transactionId: String, synced: Boolean, lastUpdated: Long)

    // ====== 삭제 ======

    /**
     * 특정 거래 내역 삭제
     */
    @Delete
    suspend fun deleteTransaction(transaction: LocalPointTransaction)

    /**
     * 특정 사무실의 모든 거래 내역 삭제
     */
    @Query("""
        DELETE FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun deleteTransactionsByOffice(regionId: String, officeId: String)

    /**
     * 특정 날짜 이전의 오래된 거래 내역 삭제 (정리용)
     */
    @Query("""
        DELETE FROM point_transactions
        WHERE timestamp < :beforeTimestamp AND synced = 1
    """)
    suspend fun deleteOldTransactions(beforeTimestamp: Long): Int

    // ====== 집계 ======

    /**
     * 특정 사무실의 총 거래 개수
     */
    @Query("""
        SELECT COUNT(*) FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun getTransactionCount(regionId: String, officeId: String): Int

    /**
     * 로컬에서 계산한 잔액 (모든 거래의 합계)
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun getCalculatedBalance(regionId: String, officeId: String): Int

    /**
     * 마지막 거래의 타임스탬프
     */
    @Query("""
        SELECT MAX(timestamp) FROM point_transactions
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun getLastTransactionTimestamp(regionId: String, officeId: String): Long?
}
package com.designated.driverapp.data.local

import androidx.room.*
import com.designated.driverapp.data.settlement.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * 정산 데이터 DAO (Data Access Object)
 */
@Dao
interface SettlementDao {

    // ==================== PendingSync 관련 ====================

    /**
     * 동기화 대기 데이터 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPendingSync(pendingSync: PendingSyncEntity): Long

    /**
     * 동기화 대기 데이터 업데이트
     */
    @Update
    suspend fun updatePendingSync(pendingSync: PendingSyncEntity)

    /**
     * 동기화 대기 데이터 삭제
     */
    @Delete
    suspend fun deletePendingSync(pendingSync: PendingSyncEntity)

    /**
     * ID로 동기화 대기 데이터 삭제
     */
    @Query("DELETE FROM pending_syncs WHERE id = :id")
    suspend fun deletePendingSyncById(id: Long)

    /**
     * 특정 상태의 동기화 대기 데이터 조회
     */
    @Query("SELECT * FROM pending_syncs WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getPendingSyncsByStatus(status: String): List<PendingSyncEntity>

    /**
     * 모든 PENDING 상태 데이터 조회
     */
    @Query("SELECT * FROM pending_syncs WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getAllPendingSyncs(): List<PendingSyncEntity>

    /**
     * 재시도 가능한 데이터 조회 (retryCount < maxRetry)
     */
    @Query("SELECT * FROM pending_syncs WHERE status IN ('PENDING', 'FAILED') AND retryCount < :maxRetry ORDER BY createdAt ASC")
    suspend fun getRetryableSyncs(maxRetry: Int = 3): List<PendingSyncEntity>

    /**
     * 동기화 대기 데이터 수 조회
     */
    @Query("SELECT COUNT(*) FROM pending_syncs WHERE status = 'PENDING'")
    suspend fun getPendingSyncCount(): Int

    /**
     * 동기화 대기 데이터 수 (Flow)
     */
    @Query("SELECT COUNT(*) FROM pending_syncs WHERE status = 'PENDING'")
    fun observePendingSyncCount(): Flow<Int>

    /**
     * 특정 세션의 동기화 대기 데이터 조회
     */
    @Query("SELECT * FROM pending_syncs WHERE sessionDate = :sessionDate AND officeId = :officeId")
    suspend fun getPendingSyncsForSession(sessionDate: String, officeId: String): List<PendingSyncEntity>

    /**
     * 동기화 상태 업데이트
     */
    @Query("UPDATE pending_syncs SET status = :status, lastAttemptAt = :attemptAt, retryCount = retryCount + 1, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String, attemptAt: Long, errorMessage: String?)

    /**
     * 모든 동기화 대기 데이터 삭제 (테스트/리셋용)
     */
    @Query("DELETE FROM pending_syncs")
    suspend fun deleteAllPendingSyncs()

    // ==================== SettlementCache 관련 ====================

    /**
     * 캐시 삽입/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateCache(cache: SettlementCacheEntity)

    /**
     * 특정 날짜의 캐시 조회
     */
    @Query("SELECT * FROM settlement_cache WHERE sessionDate = :sessionDate AND officeId = :officeId LIMIT 1")
    suspend fun getCache(sessionDate: String, officeId: String): SettlementCacheEntity?

    /**
     * 특정 날짜의 캐시 버전 조회
     */
    @Query("SELECT version FROM settlement_cache WHERE sessionDate = :sessionDate AND officeId = :officeId LIMIT 1")
    suspend fun getCacheVersion(sessionDate: String, officeId: String): Long?

    /**
     * 캐시 삭제
     */
    @Query("DELETE FROM settlement_cache WHERE sessionDate = :sessionDate AND officeId = :officeId")
    suspend fun deleteCache(sessionDate: String, officeId: String)

    /**
     * 오래된 캐시 삭제 (7일 이상)
     */
    @Query("DELETE FROM settlement_cache WHERE cachedAt < :threshold")
    suspend fun deleteOldCache(threshold: Long)

    /**
     * 모든 캐시 삭제
     */
    @Query("DELETE FROM settlement_cache")
    suspend fun deleteAllCache()

    /**
     * 최근 캐시 목록 조회
     */
    @Query("SELECT * FROM settlement_cache WHERE officeId = :officeId ORDER BY sessionDate DESC LIMIT :limit")
    suspend fun getRecentCaches(officeId: String, limit: Int = 7): List<SettlementCacheEntity>
}

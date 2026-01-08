package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 기사 정보 로컬 데이터 액세스 객체
 */
@Dao
interface DriverDao {

    // ====== 조회 ======

    /**
     * 특정 사무실의 기사 목록 Flow (실시간 업데이트)
     */
    @Query("""
        SELECT * FROM drivers
        WHERE regionId = :regionId AND officeId = :officeId
        ORDER BY name ASC
    """)
    fun getDriversFlow(regionId: String, officeId: String): Flow<List<LocalDriverInfo>>

    /**
     * 대기 중인 기사만 조회
     */
    @Query("""
        SELECT * FROM drivers
        WHERE regionId = :regionId AND officeId = :officeId
        AND status = 'WAITING'
        AND approvalStatus = 'APPROVED'
        ORDER BY name ASC
    """)
    fun getWaitingDriversFlow(regionId: String, officeId: String): Flow<List<LocalDriverInfo>>

    /**
     * 특정 기사 조회 (ID로)
     */
    @Query("SELECT * FROM drivers WHERE id = :driverId")
    suspend fun getDriverById(driverId: String): LocalDriverInfo?

    /**
     * 특정 기사 조회 (authUid로)
     */
    @Query("SELECT * FROM drivers WHERE authUid = :authUid")
    suspend fun getDriverByAuthUid(authUid: String): LocalDriverInfo?

    /**
     * 특정 상태의 기사 개수 조회
     */
    @Query("""
        SELECT COUNT(*) FROM drivers
        WHERE regionId = :regionId AND officeId = :officeId AND status = :status
    """)
    suspend fun getDriverCountByStatus(regionId: String, officeId: String, status: String): Int

    /**
     * 가장 최근 기사 업데이트 타임스탬프 조회 (동기화용)
     */
    @Query("""
        SELECT MAX(updatedAt) FROM drivers
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun getLastDriverUpdateTimestamp(regionId: String, officeId: String): Long?

    // ====== 삽입/업데이트 ======

    /**
     * 단일 기사 삽입 (중복 시 교체)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriver(driver: LocalDriverInfo)

    /**
     * 여러 기사 일괄 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrivers(drivers: List<LocalDriverInfo>)

    /**
     * 기사 상태 업데이트
     */
    @Query("""
        UPDATE drivers
        SET status = :status,
            updatedAt = :now,
            synced = 0,
            lastUpdated = :now
        WHERE id = :driverId
    """)
    suspend fun updateDriverStatus(
        driverId: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 기사 상태 업데이트 (authUid로)
     */
    @Query("""
        UPDATE drivers
        SET status = :status,
            updatedAt = :now,
            synced = 0,
            lastUpdated = :now
        WHERE authUid = :authUid
    """)
    suspend fun updateDriverStatusByAuthUid(
        authUid: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 승인 상태 업데이트
     */
    @Query("""
        UPDATE drivers
        SET approvalStatus = :approvalStatus,
            approvedAt = :approvedAt,
            synced = 0,
            lastUpdated = :now
        WHERE id = :driverId
    """)
    suspend fun updateApprovalStatus(
        driverId: String,
        approvalStatus: String,
        approvedAt: Long? = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    )

    /**
     * 동기화 상태 업데이트
     */
    @Query("""
        UPDATE drivers
        SET synced = :synced,
            lastUpdated = :now
        WHERE id = :driverId
    """)
    suspend fun updateSyncStatus(
        driverId: String,
        synced: Boolean,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 기사 기본 정보 업데이트
     */
    @Query("""
        UPDATE drivers
        SET name = :name,
            phoneNumber = :phoneNumber,
            email = :email,
            updatedAt = :now,
            synced = 0,
            lastUpdated = :now
        WHERE id = :driverId
    """)
    suspend fun updateDriverInfo(
        driverId: String,
        name: String,
        phoneNumber: String,
        email: String?,
        now: Long = System.currentTimeMillis()
    )

    // ====== 삭제 ======

    /**
     * 특정 기사 삭제
     */
    @Query("DELETE FROM drivers WHERE id = :driverId")
    suspend fun deleteDriver(driverId: String)

    /**
     * 특정 사무실의 모든 기사 삭제
     */
    @Query("DELETE FROM drivers WHERE regionId = :regionId AND officeId = :officeId")
    suspend fun deleteAllDriversByOffice(regionId: String, officeId: String)

    /**
     * 미동기화 기사 조회 (동기화용)
     */
    @Query("""
        SELECT * FROM drivers
        WHERE synced = 0
        ORDER BY lastUpdated ASC
        LIMIT :limit
    """)
    suspend fun getUnsyncedDrivers(limit: Int = 50): List<LocalDriverInfo>
}

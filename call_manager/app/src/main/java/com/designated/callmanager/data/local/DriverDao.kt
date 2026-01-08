package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 기사 정보 DAO (Data Access Object)
 *
 * Room Database의 drivers 테이블에 접근하는 인터페이스
 */
@Dao
interface DriverDao {

    // ========================================
    // Flow 구독 (UI가 실시간 감시)
    // ========================================

    /**
     * 특정 사무실의 모든 기사를 Flow로 구독
     */
    @Query("""
        SELECT * FROM drivers
        WHERE regionId = :regionId AND officeId = :officeId
        ORDER BY name ASC
    """)
    fun getDriversFlow(regionId: String, officeId: String): Flow<List<LocalDriverInfo>>

    // ========================================
    // 단일 조회 (suspend)
    // ========================================

    /**
     * ID로 단일 기사 조회
     */
    @Query("SELECT * FROM drivers WHERE id = :driverId")
    suspend fun getDriverById(driverId: String): LocalDriverInfo?

    /**
     * AuthUid로 기사 조회
     */
    @Query("SELECT * FROM drivers WHERE authUid = :authUid")
    suspend fun getDriverByAuthUid(authUid: String): LocalDriverInfo?

    // ========================================
    // 삽입/업데이트 (UPSERT)
    // ========================================

    /**
     * 기사 삽입 또는 업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDriver(driver: LocalDriverInfo)

    /**
     * 여러 기사를 한 번에 삽입/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDrivers(drivers: List<LocalDriverInfo>)

    // ========================================
    // 상태 업데이트
    // ========================================

    /**
     * 기사 상태 업데이트 (ID로)
     */
    @Query("""
        UPDATE drivers
        SET status = :status,
            updatedAt = :now,
            lastUpdated = :now
        WHERE id = :driverId
    """)
    suspend fun updateDriverStatus(
        driverId: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 기사 상태 업데이트 (AuthUid로)
     */
    @Query("""
        UPDATE drivers
        SET status = :status,
            updatedAt = :now,
            lastUpdated = :now
        WHERE authUid = :authUid
    """)
    suspend fun updateDriverStatusByAuthUid(
        authUid: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    // ========================================
    // 삭제
    // ========================================

    /**
     * 단일 기사 삭제
     */
    @Query("DELETE FROM drivers WHERE id = :driverId")
    suspend fun deleteDriver(driverId: String)

    /**
     * 특정 사무실의 모든 기사 삭제
     */
    @Query("DELETE FROM drivers WHERE regionId = :regionId AND officeId = :officeId")
    suspend fun deleteAll(regionId: String, officeId: String)
}

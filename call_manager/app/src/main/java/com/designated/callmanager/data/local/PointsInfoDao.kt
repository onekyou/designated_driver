package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 포인트 잔액 정보 데이터 접근 객체
 */
@Dao
interface PointsInfoDao {

    // ====== 조회 ======

    /**
     * 특정 사무실의 포인트 잔액 정보 조회 (Flow)
     */
    @Query("""
        SELECT * FROM points_info
        WHERE regionId = :regionId AND officeId = :officeId
        LIMIT 1
    """)
    fun getPointsInfoFlow(regionId: String, officeId: String): Flow<LocalPointsInfo?>

    /**
     * 특정 사무실의 포인트 잔액 정보 조회 (단일)
     */
    @Query("""
        SELECT * FROM points_info
        WHERE regionId = :regionId AND officeId = :officeId
        LIMIT 1
    """)
    suspend fun getPointsInfo(regionId: String, officeId: String): LocalPointsInfo?

    /**
     * 모든 사무실의 포인트 잔액 정보 조회
     */
    @Query("SELECT * FROM points_info ORDER BY lastUpdated DESC")
    suspend fun getAllPointsInfo(): List<LocalPointsInfo>

    /**
     * 동기화되지 않은 포인트 정보 조회
     */
    @Query("SELECT * FROM points_info WHERE synced = 0")
    suspend fun getUnsyncedPointsInfo(): List<LocalPointsInfo>

    // ====== 삽입/업데이트 ======

    /**
     * 포인트 잔액 정보 삽입 (중복 시 교체)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPointsInfo(pointsInfo: LocalPointsInfo)

    /**
     * 포인트 잔액 정보 업데이트
     */
    @Update
    suspend fun updatePointsInfo(pointsInfo: LocalPointsInfo)

    /**
     * 잔액만 업데이트 (빠른 업데이트용)
     */
    @Query("""
        UPDATE points_info
        SET balance = :balance, updatedAt = :updatedAt, lastUpdated = :lastUpdated, synced = :synced
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun updateBalance(
        regionId: String,
        officeId: String,
        balance: Int,
        updatedAt: Long,
        lastUpdated: Long,
        synced: Boolean
    )

    /**
     * 동기화 상태 업데이트
     */
    @Query("""
        UPDATE points_info
        SET synced = :synced, lastUpdated = :lastUpdated
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun updateSyncStatus(regionId: String, officeId: String, synced: Boolean, lastUpdated: Long)

    // ====== 삭제 ======

    /**
     * 특정 사무실의 포인트 정보 삭제
     */
    @Query("""
        DELETE FROM points_info
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun deletePointsInfo(regionId: String, officeId: String)

    /**
     * 모든 포인트 정보 삭제 (초기화용)
     */
    @Query("DELETE FROM points_info")
    suspend fun deleteAllPointsInfo()

    // ====== 유틸리티 ======

    /**
     * 특정 사무실의 포인트 정보 존재 여부 확인
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM points_info
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun existsPointsInfo(regionId: String, officeId: String): Boolean

    /**
     * 총 관리 중인 사무실 개수
     */
    @Query("SELECT COUNT(*) FROM points_info")
    suspend fun getOfficeCount(): Int
}
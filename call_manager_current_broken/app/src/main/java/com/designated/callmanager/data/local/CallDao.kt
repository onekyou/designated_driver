package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 콜 정보 로컬 데이터 액세스 객체
 */
@Dao
interface CallDao {

    // ====== 조회 ======

    /**
     * 특정 사무실의 콜 목록 Flow (실시간 업데이트)
     */
    @Query("""
        SELECT * FROM calls
        WHERE regionId = :regionId AND officeId = :officeId
        ORDER BY timestamp DESC
    """)
    fun getCallsFlow(regionId: String, officeId: String): Flow<List<LocalCallInfo>>

    /**
     * 특정 사무실의 대기 중인 콜만 조회
     */
    @Query("""
        SELECT * FROM calls
        WHERE regionId = :regionId AND officeId = :officeId AND status = 'WAITING'
        ORDER BY timestamp DESC
    """)
    fun getWaitingCallsFlow(regionId: String, officeId: String): Flow<List<LocalCallInfo>>

    /**
     * 특정 콜 조회
     */
    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCallById(callId: String): LocalCallInfo?

    /**
     * 최근 콜 제한된 개수만 조회 (일반 suspend 함수)
     */
    @Query("""
        SELECT * FROM calls
        WHERE regionId = :regionId AND officeId = :officeId
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun getRecentCalls(regionId: String, officeId: String, limit: Int): List<LocalCallInfo>

    /**
     * 특정 상태의 콜 개수 조회
     */
    @Query("""
        SELECT COUNT(*) FROM calls
        WHERE regionId = :regionId AND officeId = :officeId AND status = :status
    """)
    suspend fun getCallCountByStatus(regionId: String, officeId: String, status: String): Int

    /**
     * 가장 최근 콜의 타임스탬프 조회 (동기화용)
     */
    @Query("""
        SELECT MAX(timestamp) FROM calls
        WHERE regionId = :regionId AND officeId = :officeId
    """)
    suspend fun getLastCallTimestamp(regionId: String, officeId: String): Long?

    // ====== 삽입/업데이트 ======

    /**
     * 단일 콜 삽입 (중복 시 교체)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: LocalCallInfo)

    /**
     * 여러 콜 일괄 삽입
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<LocalCallInfo>)

    /**
     * 콜 상태 업데이트
     */
    @Query("""
        UPDATE calls
        SET status = :status,
            assignedDriverId = :assignedDriverId,
            assignedTimestamp = :assignedTimestamp,
            synced = 0,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateCallStatus(
        callId: String,
        status: String,
        assignedDriverId: String?,
        assignedTimestamp: Long? = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    )

    /**
     * 배차 정보 업데이트
     */
    @Query("""
        UPDATE calls
        SET assignedDriverId = :driverId,
            assignedDriverName = :driverName,
            assignedDriverPhone = :driverPhone,
            assignedTimestamp = :timestamp,
            synced = 0,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateAssignedDriver(
        callId: String,
        driverId: String,
        driverName: String?,
        driverPhone: String?,
        timestamp: Long = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    )

    /**
     * 동기화 상태 업데이트
     */
    @Query("""
        UPDATE calls
        SET synced = :synced,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateSyncStatus(
        callId: String,
        synced: Boolean,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 운행 정보 업데이트
     */
    @Query("""
        UPDATE calls
        SET departure_set = :departure,
            destination_set = :destination,
            fare = :fare,
            paymentMethod = :paymentMethod,
            cashAmount = :cashAmount,
            synced = 0,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateTripInfo(
        callId: String,
        departure: String?,
        destination: String?,
        fare: Long?,
        paymentMethod: String?,
        cashAmount: Long?,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 출발지/목적지만 업데이트 (FCM용)
     */
    @Query("""
        UPDATE calls
        SET departure_set = :departure,
            destination_set = :destination,
            synced = 0,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateDestinationInfo(
        callId: String,
        departure: String?,
        destination: String?,
        now: Long = System.currentTimeMillis()
    )

    // ====== 삭제 ======

    /**
     * 특정 콜 삭제
     */
    @Query("DELETE FROM calls WHERE id = :callId")
    suspend fun deleteCall(callId: String)

    /**
     * 오래된 콜 삭제 (정리용)
     */
    @Query("DELETE FROM calls WHERE timestamp < :cutoffTime")
    suspend fun deleteOldCalls(cutoffTime: Long): Int

    /**
     * 특정 사무실의 모든 콜 삭제
     */
    @Query("DELETE FROM calls WHERE regionId = :regionId AND officeId = :officeId")
    suspend fun deleteAllCallsByOffice(regionId: String, officeId: String)

    /**
     * 미동기화 콜 조회 (동기화용)
     */
    @Query("""
        SELECT * FROM calls
        WHERE synced = 0
        ORDER BY lastUpdated ASC
        LIMIT :limit
    """)
    suspend fun getUnsyncedCalls(limit: Int = 50): List<LocalCallInfo>
}

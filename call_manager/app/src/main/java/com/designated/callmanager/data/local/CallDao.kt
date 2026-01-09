package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 콜 정보 DAO (Data Access Object)
 *
 * Room Database의 calls 테이블에 접근하는 인터페이스
 */
@Dao
interface CallDao {

    // ========================================
    // Flow 구독 (UI가 실시간 감시)
    // ========================================

    /**
     * 특정 사무실의 모든 콜을 Flow로 구독
     * UI가 이 Flow를 collect하면 DB 변경 시 자동 업데이트
     */
    @Query("""
        SELECT * FROM calls
        WHERE regionId = :regionId AND officeId = :officeId
        ORDER BY timestamp DESC
    """)
    fun getCallsFlow(regionId: String, officeId: String): Flow<List<LocalCallInfo>>

    // ========================================
    // 단일 조회 (suspend)
    // ========================================

    /**
     * ID로 단일 콜 조회
     */
    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCallById(callId: String): LocalCallInfo?

    // ========================================
    // 삽입/업데이트 (UPSERT)
    // ========================================

    /**
     * 콜 삽입 또는 업데이트
     * 같은 ID가 있으면 REPLACE
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCall(call: LocalCallInfo)

    /**
     * 여러 콜을 한 번에 삽입/업데이트
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalls(calls: List<LocalCallInfo>)

    // ========================================
    // 상태 업데이트
    // ========================================

    /**
     * 콜 상태만 업데이트
     */
    @Query("""
        UPDATE calls
        SET status = :status,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateCallStatus(
        callId: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    /**
     * 운행 정보 업데이트 (FCM용)
     * 기사가 운행 시작 시 입력한 출발지/목적지/요금
     */
    @Query("""
        UPDATE calls
        SET departure_set = :departure,
            destination_set = :destination,
            fare_set = :fare,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateTripInfo(
        callId: String,
        departure: String?,
        destination: String?,
        fare: Long?,
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
            status = :status,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateAssignment(
        callId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    // ========================================
    // 삭제
    // ========================================

    /**
     * 단일 콜 삭제
     */
    @Query("DELETE FROM calls WHERE id = :callId")
    suspend fun deleteCall(callId: String)

    /**
     * 특정 사무실의 모든 콜 삭제
     * refreshData() 시 기존 데이터 정리용
     */
    @Query("DELETE FROM calls WHERE regionId = :regionId AND officeId = :officeId")
    suspend fun deleteAll(regionId: String, officeId: String)

    /**
     * 오래된 콜 삭제 (정리용)
     */
    @Query("DELETE FROM calls WHERE timestamp < :cutoffTime")
    suspend fun deleteOldCalls(cutoffTime: Long)
}

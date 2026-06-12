package com.designated.pickupdriver.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 픽업앱 콜 DAO
 *
 * UI 는 [getActiveCallsFlow] 로 활성 콜만 구독.
 * 시간 윈도우(12시간 롤링)는 Repository 메모리 단계에서 처리 — 콜매니저 getCallsFlow와 동일.
 * (기사가 운행완료를 안 누른 박제 콜이 다음날까지 남는 문제 차단. 2026-06-13)
 */
@Dao
interface CallDao {

    @Query(
        """
        SELECT * FROM calls
        WHERE provinceId = :provinceId
          AND cityId = :cityId
          AND officeId = :officeId
          AND status IN (:activeStatuses)
        ORDER BY timestamp DESC
        """
    )
    fun getActiveCallsFlow(
        provinceId: String,
        cityId: String,
        officeId: String,
        activeStatuses: List<String>
    ): Flow<List<LocalCall>>

    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getById(callId: String): LocalCall?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(call: LocalCall)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(calls: List<LocalCall>)

    /**
     * FCM CALL_STATUS_UPDATE 수신 시: status + 운행정보 원자적 UPDATE.
     * COALESCE 로 null 인 인자는 기존 값 유지.
     */
    @Query(
        """
        UPDATE calls
        SET status = :status,
            departure = COALESCE(:departure, departure),
            destination = COALESCE(:destination, destination),
            fare = COALESCE(:fare, fare),
            waypoints = COALESCE(:waypoints, waypoints),
            assignedDriverName = COALESCE(:assignedDriverName, assignedDriverName),
            assignedDriverPhone = COALESCE(:assignedDriverPhone, assignedDriverPhone),
            customerAddress = COALESCE(:customerAddress, customerAddress),
            lastUpdated = :now
        WHERE id = :callId
        """
    )
    suspend fun updateStatusWithTripInfo(
        callId: String,
        status: String,
        departure: String?,
        destination: String?,
        fare: Long?,
        waypoints: String?,
        assignedDriverName: String?,
        assignedDriverPhone: String?,
        customerAddress: String?,
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM calls WHERE id = :callId")
    suspend fun deleteOne(callId: String)

    @Query("DELETE FROM calls WHERE provinceId = :provinceId AND cityId = :cityId AND officeId = :officeId")
    suspend fun deleteAllInOffice(provinceId: String, cityId: String, officeId: String)
}

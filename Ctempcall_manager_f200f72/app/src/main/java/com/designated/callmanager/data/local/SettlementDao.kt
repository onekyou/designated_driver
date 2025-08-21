package com.designated.callmanager.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {
    @Query("SELECT * FROM settlements WHERE isFinalized = 0 ORDER BY completedAt DESC")
    fun flowActive(): Flow<List<SettlementEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SettlementEntity>)
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SettlementEntity)
    
    @Query("SELECT COUNT(*) FROM settlements WHERE callId = :callId")
    suspend fun existsById(callId: String): Int
    
    @Query("SELECT * FROM settlements WHERE callId = :callId LIMIT 1")
    suspend fun getById(callId: String): SettlementEntity?

    @Query("DELETE FROM settlements WHERE completedAt <= :timestamp")
    suspend fun clearBefore(timestamp: Long)

    @Query("DELETE FROM settlements")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM settlements WHERE workDate = :workDate")
    suspend fun deleteWorkDate(workDate: String): Int

    @Query("UPDATE settlements SET isFinalized = 1, sessionId = :sessionId WHERE callId IN (:ids)")
    suspend fun markTripsFinalized(ids: List<String>, sessionId: String): Int

    @Query("SELECT * FROM settlements WHERE sessionId = :sessionId")
    fun getTripsBySession(sessionId: String): Flow<List<SettlementEntity>>
} 
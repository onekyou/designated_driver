package com.designated.callmanager.data.local.dao

import androidx.room.*
import com.designated.callmanager.data.local.entity.SettlementEntity
import com.designated.callmanager.data.local.entity.CreditEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SettlementDao {
    // Settlement 관련
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlement(settlement: SettlementEntity)
    
    @Delete
    suspend fun deleteSettlement(settlement: SettlementEntity)
    
    @Query("DELETE FROM settlement_cache WHERE callId = :callId")
    suspend fun deleteSettlementById(callId: String)
    
    @Query("SELECT * FROM settlement_cache WHERE status = :status ORDER BY timestamp DESC")
    fun getSettlementsByStatus(status: String): Flow<List<SettlementEntity>>
    
    @Query("SELECT * FROM settlement_cache ORDER BY timestamp DESC")
    fun getAllSettlements(): Flow<List<SettlementEntity>>
    
    @Query("DELETE FROM settlement_cache")
    suspend fun clearAllSettlements()
    
    // Credit 관련
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCredit(credit: CreditEntity)
    
    @Update
    suspend fun updateCredit(credit: CreditEntity)
    
    @Delete
    suspend fun deleteCredit(credit: CreditEntity)
    
    @Query("SELECT * FROM credit_cache WHERE isPaid = 0 ORDER BY timestamp DESC")
    fun getUnpaidCredits(): Flow<List<CreditEntity>>
    
    @Query("SELECT * FROM credit_cache WHERE driverId = :driverId AND isPaid = 0")
    fun getUnpaidCreditsByDriver(driverId: String): Flow<List<CreditEntity>>
    
    @Query("UPDATE credit_cache SET isPaid = 1 WHERE id = :creditId")
    suspend fun markCreditAsPaid(creditId: String)
    
    @Query("DELETE FROM credit_cache WHERE isPaid = 1")
    suspend fun deletePaidCredits()
}
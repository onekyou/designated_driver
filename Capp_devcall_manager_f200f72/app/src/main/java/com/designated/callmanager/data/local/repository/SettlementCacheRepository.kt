package com.designated.callmanager.data.local.repository

import com.designated.callmanager.data.local.SettlementDatabase
import com.designated.callmanager.data.local.dao.SettlementDao
import com.designated.callmanager.data.local.entity.SettlementEntity
import com.designated.callmanager.data.local.entity.CreditEntity
import kotlinx.coroutines.flow.Flow

class SettlementCacheRepository(
    private val dao: SettlementDao
) {
    // 정산대기 관련
    suspend fun addWaitingSettlement(callId: String) {
        dao.insertSettlement(
            SettlementEntity(
                callId = callId,
                status = "WAITING"
            )
        )
    }
    
    suspend fun removeWaitingSettlement(callId: String) {
        dao.deleteSettlementById(callId)
    }
    
    fun getWaitingSettlements(): Flow<List<SettlementEntity>> {
        return dao.getSettlementsByStatus("WAITING")
    }
    
    suspend fun clearAllWaitingSettlements() {
        dao.clearAllSettlements()
    }
    
    // 외상 관련
    suspend fun addCredit(
        driverId: String,
        driverName: String,
        customerPhone: String,
        customerName: String,
        amount: Int
    ) {
        val creditId = "${driverId}_${customerPhone}_${System.currentTimeMillis()}"
        dao.insertCredit(
            CreditEntity(
                id = creditId,
                driverId = driverId,
                driverName = driverName,
                customerPhone = customerPhone,
                customerName = customerName,
                amount = amount
            )
        )
    }
    
    suspend fun markCreditAsPaid(creditId: String) {
        dao.markCreditAsPaid(creditId)
    }
    
    fun getUnpaidCredits(): Flow<List<CreditEntity>> {
        return dao.getUnpaidCredits()
    }
    
    fun getUnpaidCreditsByDriver(driverId: String): Flow<List<CreditEntity>> {
        return dao.getUnpaidCreditsByDriver(driverId)
    }
    
    suspend fun deletePaidCredits() {
        dao.deletePaidCredits()
    }
}
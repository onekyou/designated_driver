package com.designated.callmanager.data.local

import kotlinx.coroutines.flow.Flow

class SettlementRepository(private val db: CallManagerDatabase) {
    
    val dao = db.settlementDao() // dao에 직접 접근할 수 있도록

    fun flowActive(): Flow<List<SettlementEntity>> = dao.flowActive()
    fun flowSessions(): Flow<List<SessionEntity>> = db.sessionDao().flowSessions()

    suspend fun insertAll(list: List<SettlementEntity>) = db.settlementDao().insertAll(list)

    suspend fun deleteAll() = db.settlementDao().deleteAll()
    suspend fun deleteWorkDate(date: String): Int = db.settlementDao().deleteWorkDate(date)

    suspend fun markTripsFinalized(ids: List<String>, sessionId: String) =
        db.settlementDao().markTripsFinalized(ids, sessionId)

    fun flowTripsBySession(sessionId: String) = db.settlementDao().getTripsBySession(sessionId)

    suspend fun insertSession(session: SessionEntity) = db.sessionDao().insert(session)
} 
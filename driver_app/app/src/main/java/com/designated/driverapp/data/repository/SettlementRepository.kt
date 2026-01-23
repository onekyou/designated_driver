package com.designated.driverapp.data.repository

import android.content.Context
import android.util.Log
import com.designated.driverapp.data.local.PendingSyncEntity
import com.designated.driverapp.data.local.SettlementCacheEntity
import com.designated.driverapp.data.local.SettlementDatabase
import com.designated.driverapp.data.settlement.*
import com.designated.driverapp.util.NetworkMonitor
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

/**
 * 정산 데이터 Repository
 * - 로컬 Room DB와 Firestore 공유 문서 간의 동기화 담당
 * - 오프라인 지원
 */
class SettlementRepository(private val context: Context) {

    private val TAG = "SettlementRepository"

    private val firestore = FirebaseFirestore.getInstance()
    private val database = SettlementDatabase.getInstance(context)
    private val dao = database.settlementDao()
    private val networkMonitor = NetworkMonitor.getInstance(context)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // ==================== 공유 문서 경로 ====================

    private fun getSessionDocPath(provinceId: String, cityId: String, officeId: String, date: String): String {
        return "provinces/$provinceId/cities/$cityId/offices/$officeId/settlementSessions/$date"
    }

    private fun getSessionDocRef(provinceId: String, cityId: String, officeId: String, date: String) =
        firestore.document(getSessionDocPath(provinceId, cityId, officeId, date))

    // ==================== 날짜 유틸 ====================

    fun getTodayDate(): String = dateFormat.format(Date())

    fun getDateFromTimestamp(timestamp: Timestamp?): String {
        return timestamp?.toDate()?.let { dateFormat.format(it) } ?: getTodayDate()
    }

    // ==================== 콜 완료 시 처리 ====================

    /**
     * 운행 완료 시 호출 - 로컬 저장 후 동기화 시도
     */
    suspend fun saveCallSettlement(
        callSettlement: CallSettlement,
        provinceId: String,
        cityId: String,
        officeId: String
    ): Result<Unit> {
        val sessionDate = getDateFromTimestamp(callSettlement.completedAt)

        Log.d(TAG, "saveCallSettlement: callId=${callSettlement.callId}, date=$sessionDate")

        // 1. 항상 로컬에 먼저 저장 (오프라인 지원)
        val pendingSync = PendingSyncEntity.fromCallSettlement(
            callSettlement = callSettlement,
            sessionDate = sessionDate,
            provinceId = provinceId,
            cityId = cityId,
            officeId = officeId
        )
        val pendingId = dao.insertPendingSync(pendingSync)
        Log.d(TAG, "Saved to local pending: id=$pendingId")

        // 2. 네트워크 연결 시 즉시 동기화 시도
        return if (networkMonitor.isNetworkAvailable()) {
            try {
                syncCallToFirestore(
                    pendingId = pendingId,
                    callSettlement = callSettlement,
                    sessionDate = sessionDate,
                    provinceId = provinceId,
                    cityId = cityId,
                    officeId = officeId
                )
                // 성공 시 pending 삭제
                dao.deletePendingSyncById(pendingId)
                Log.d(TAG, "Synced to Firestore and removed from pending")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync to Firestore", e)
                dao.updateSyncStatus(
                    id = pendingId,
                    status = SyncStatus.FAILED.name,
                    attemptAt = System.currentTimeMillis(),
                    errorMessage = e.message
                )
                Result.failure(e)
            }
        } else {
            Log.d(TAG, "Network unavailable, kept in pending for later sync")
            Result.success(Unit) // 로컬 저장 성공
        }
    }

    /**
     * Firestore 공유 문서에 콜 데이터 동기화 (트랜잭션 사용)
     */
    private suspend fun syncCallToFirestore(
        pendingId: Long,
        callSettlement: CallSettlement,
        sessionDate: String,
        provinceId: String,
        cityId: String,
        officeId: String
    ) {
        val sessionRef = getSessionDocRef(provinceId, cityId, officeId, sessionDate)

        firestore.runTransaction { transaction ->
            val doc = transaction.get(sessionRef)

            if (doc.exists()) {
                // 기존 문서 업데이트
                val session = SettlementSession.fromDocument(doc)
                    ?: throw Exception("Failed to parse session document")

                // 중복 체크
                if (session.calls.any { it.callId == callSettlement.callId }) {
                    Log.w(TAG, "Call already exists in session: ${callSettlement.callId}")
                    return@runTransaction
                }

                // 콜 추가 및 집계 재계산
                val updatedCalls = session.calls + callSettlement.copy(
                    syncedAt = Timestamp.now()
                )
                val updatedTotals = session.totals.addCall(
                    callSettlement,
                    session.metadata.depositRatio
                )
                val updatedMetadata = session.metadata.copy(
                    version = session.metadata.version + 1,
                    lastUpdatedAt = Timestamp.now(),
                    lastUpdatedBy = "driver_app"
                )

                transaction.update(sessionRef, mapOf(
                    "metadata" to updatedMetadata.toMap(),
                    "totals" to updatedTotals.toMap(),
                    "calls" to updatedCalls.map { it.toMap() }
                ))
            } else {
                // 새 문서 생성
                val newSession = SettlementSession(
                    metadata = SettlementMetadata(
                        version = 1,
                        lastUpdatedAt = Timestamp.now(),
                        lastUpdatedBy = "driver_app",
                        depositRatio = 60, // 기본값, 추후 사무실 설정에서 읽어올 수 있음
                        createdAt = Timestamp.now(),
                        isFinalized = false
                    ),
                    totals = SettlementTotals().addCall(callSettlement, 60),
                    calls = listOf(callSettlement.copy(syncedAt = Timestamp.now()))
                )

                transaction.set(sessionRef, newSession.toMap())
            }
        }.await()

        Log.d(TAG, "Successfully synced call to Firestore: ${callSettlement.callId}")
    }

    // ==================== 동기화 체크 및 갱신 ====================

    /**
     * 버전 체크 후 필요 시 전체 동기화
     * @return 동기화 수행 여부
     */
    suspend fun checkAndSync(
        provinceId: String,
        cityId: String,
        officeId: String,
        sessionDate: String = getTodayDate()
    ): Result<Boolean> {
        if (!networkMonitor.isNetworkAvailable()) {
            Log.d(TAG, "checkAndSync: Network unavailable, using cache")
            return Result.success(false)
        }

        return try {
            val sessionRef = getSessionDocRef(provinceId, cityId, officeId, sessionDate)
            val doc = sessionRef.get().await()

            if (!doc.exists()) {
                Log.d(TAG, "checkAndSync: Session document does not exist")
                return Result.success(false)
            }

            val remoteVersion = doc.getLong("metadata.version") ?: 0
            val localVersion = dao.getCacheVersion(sessionDate, officeId) ?: -1

            Log.d(TAG, "checkAndSync: remoteVersion=$remoteVersion, localVersion=$localVersion")

            if (remoteVersion > localVersion) {
                // 변경됨 - 전체 동기화
                val session = SettlementSession.fromDocument(doc)
                if (session != null) {
                    val cacheEntity = SettlementCacheEntity.fromSettlementSession(
                        session = session,
                        sessionDate = sessionDate,
                        provinceId = provinceId,
                        cityId = cityId,
                        officeId = officeId
                    )
                    dao.insertOrUpdateCache(cacheEntity)
                    Log.d(TAG, "checkAndSync: Cache updated to version $remoteVersion")
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to parse session"))
                }
            } else {
                Log.d(TAG, "checkAndSync: Cache is up to date")
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAndSync failed", e)
            Result.failure(e)
        }
    }

    // ==================== 로컬 캐시 조회 ====================

    /**
     * 로컬 캐시에서 정산 세션 조회
     */
    suspend fun getCachedSession(
        officeId: String,
        sessionDate: String = getTodayDate()
    ): SettlementSession? {
        return dao.getCache(sessionDate, officeId)?.toSettlementSession()
    }

    /**
     * 기사 관점 정산 요약 조회
     */
    suspend fun getDriverSettlementSummary(
        officeId: String,
        sessionDate: String = getTodayDate()
    ): DriverSettlementSummary? {
        val session = getCachedSession(officeId, sessionDate) ?: return null
        return DriverSettlementSummary.fromSession(session)
    }

    // ==================== 펜딩 동기화 처리 ====================

    /**
     * 대기 중인 모든 동기화 처리
     */
    suspend fun syncAllPending(): Result<Int> {
        if (!networkMonitor.isNetworkAvailable()) {
            return Result.failure(Exception("Network unavailable"))
        }

        val pendingList = dao.getRetryableSyncs()
        var successCount = 0

        for (pending in pendingList) {
            val callSettlement = pending.toCallSettlement() ?: continue

            try {
                syncCallToFirestore(
                    pendingId = pending.id,
                    callSettlement = callSettlement,
                    sessionDate = pending.sessionDate,
                    provinceId = pending.provinceId,
                    cityId = pending.cityId,
                    officeId = pending.officeId
                )
                dao.deletePendingSyncById(pending.id)
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync pending: ${pending.id}", e)
                dao.updateSyncStatus(
                    id = pending.id,
                    status = SyncStatus.FAILED.name,
                    attemptAt = System.currentTimeMillis(),
                    errorMessage = e.message
                )
            }
        }

        Log.d(TAG, "syncAllPending: $successCount/${pendingList.size} synced")
        return Result.success(successCount)
    }

    /**
     * 대기 중인 동기화 수 조회
     */
    suspend fun getPendingSyncCount(): Int = dao.getPendingSyncCount()

    /**
     * 대기 중인 동기화 수 Flow
     */
    fun observePendingSyncCount(): Flow<Int> = dao.observePendingSyncCount()

    // ==================== 캐시 관리 ====================

    /**
     * 오래된 캐시 정리 (7일 이상)
     */
    suspend fun cleanOldCache() {
        val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        dao.deleteOldCache(sevenDaysAgo)
    }

    /**
     * 모든 캐시 삭제
     */
    suspend fun clearAllCache() {
        dao.deleteAllCache()
        dao.deleteAllPendingSyncs()
    }

    companion object {
        @Volatile
        private var INSTANCE: SettlementRepository? = null

        fun getInstance(context: Context): SettlementRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = SettlementRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

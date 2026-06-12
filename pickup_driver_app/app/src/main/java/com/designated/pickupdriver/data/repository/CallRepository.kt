package com.designated.pickupdriver.data.repository

import android.util.Log
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.data.local.AppDatabase
import com.designated.pickupdriver.data.local.LocalCall
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 픽업앱 콜 Repository (FCM + Room 트리거 기반)
 *
 * 아키텍처:
 *  - 로컬 Room DB가 단일 진실 공급원
 *  - UI는 Flow<List<LocalCall>> 구독
 *  - 첫 진입 시 [refreshData] 로 Firestore 100건 fetch + Room upsert (1회)
 *  - FCM `NEW_CALL` → [insertCallFromFCM] → Room INSERT
 *  - FCM `CALL_STATUS_UPDATE` → [updateCallStatusFromFCM] → Room UPDATE
 *
 * call_manager `CallRepository` 의 픽업 단순화 버전.
 */
@Singleton
class CallRepository @Inject constructor(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val callDao = database.callDao()

    companion object {
        private const val TAG = "PickupCallRepo"
        private const val INITIAL_LOAD_LIMIT = 100L
        // 활성 콜 표기 윈도우 = 생성 후 12시간 (콜매니저 getCallsFlow 12시간 버킷과 동일).
        // 기사가 운행완료를 안 누른 박제 콜이 12시간 뒤 표기에서 자동 제외됨. (2026-06-13)
        private const val ACTIVE_WINDOW_MS = 12L * 60L * 60L * 1000L
        // 시간 경과 자동 반영용 주기 (콜매니저 startSharedCallTicker와 동일 60초).
        private const val TICK_MS = 60_000L
        // WAITING 제외: 픽업기사는 대리기사가 배차받은 콜만 모니터링 (2026-05-11)
        private val ACTIVE_STATUSES = listOf(
            Constants.STATUS_ASSIGNED,
            Constants.STATUS_ACCEPTED,
            Constants.STATUS_IN_PROGRESS,
            Constants.STATUS_AWAITING_SETTLEMENT
        )
    }

    // ===== Flow 노출 =====

    /**
     * 활성 콜 Flow. 콜매니저 패턴 — 생성 후 12시간(ACTIVE_WINDOW_MS) 윈도우를 메모리에서 필터.
     * DAO Flow(DB 변경) 또는 ticker(1분 주기) 중 하나만 emit해도 combine이 재계산 →
     * 앱을 켜둔 채여도 12시간 지난 콜이 자동으로 빠진다(화면 새로고침 불필요).
     */
    fun getActiveCallsFlow(provinceId: String, cityId: String, officeId: String): Flow<List<LocalCall>> =
        combine(
            callDao.getActiveCallsFlow(provinceId, cityId, officeId, ACTIVE_STATUSES),
            tickerFlow()
        ) { calls, _ ->
            val cutoff = System.currentTimeMillis() - ACTIVE_WINDOW_MS
            calls.filter { it.timestamp >= cutoff }
        }

    /** 1분마다 Unit을 emit(첫 emit 즉시) — combine의 시간 윈도우 재계산 트리거. */
    private fun tickerFlow(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MS)
        }
    }

    // ===== 첫 진입 시 reconciliation (.get 1회) =====

    suspend fun refreshData(provinceId: String, cityId: String, officeId: String) {
        try {
            val snapshot = firestore
                .collection(Constants.COLLECTION_PROVINCES).document(provinceId)
                .collection(Constants.COLLECTION_CITIES).document(cityId)
                .collection(Constants.COLLECTION_OFFICES).document(officeId)
                .collection(Constants.COLLECTION_CALLS)
                .whereIn("status", ACTIVE_STATUSES)
                .get()
                .await()

            val calls = snapshot.documents.mapNotNull { doc ->
                doc.toLocalCall(provinceId, cityId, officeId)
            }

            if (calls.isNotEmpty()) {
                callDao.upsertAll(calls)
                Log.d(TAG, "[refreshData] ${calls.size}건 upsert (province=$provinceId/$cityId/$officeId)")
            } else {
                Log.d(TAG, "[refreshData] 활성 콜 없음")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[refreshData] 실패", e)
        }
    }

    // ===== 받기 (FCM 트리거) =====

    fun insertCallFromFCM(payload: Map<String, String>) {
        scope.launch {
            try {
                val callId = payload["callId"] ?: run {
                    Log.w(TAG, "[insertCallFromFCM] callId 없음 — skip")
                    return@launch
                }
                val provinceId = payload["provinceId"] ?: return@launch
                val cityId = payload["cityId"] ?: return@launch
                val officeId = payload["officeId"] ?: return@launch
                val timestamp = payload["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

                val call = LocalCall(
                    id = callId,
                    provinceId = provinceId,
                    cityId = cityId,
                    officeId = officeId,
                    status = payload["status"] ?: Constants.STATUS_WAITING,
                    timestamp = timestamp,
                    customerName = payload["customerName"],
                    customerPhone = payload["customerPhone"],
                    customerAddress = payload["customerAddress"]?.takeIf { it.isNotBlank() }
                        ?: payload["pickupLocation"]?.takeIf { it.isNotBlank() },
                    departure = payload["departure"]?.takeIf { it.isNotBlank() },
                    destination = payload["destination"]?.takeIf { it.isNotBlank() },
                    waypoints = payload["waypoints"]?.takeIf { it.isNotBlank() },
                    fare = payload["fare"]?.toLongOrNull(),
                    assignedDriverId = null,
                    assignedDriverName = payload["assignedDriverName"]?.takeIf { it.isNotBlank() },
                    assignedDriverPhone = null,
                    callType = null,
                )
                callDao.upsert(call)
                Log.d(TAG, "[insertCallFromFCM] upsert: $callId (status=${call.status})")
            } catch (e: Exception) {
                Log.e(TAG, "[insertCallFromFCM] 실패", e)
            }
        }
    }

    fun updateCallStatusFromFCM(payload: Map<String, String>) {
        scope.launch {
            try {
                val callId = payload["callId"] ?: run {
                    Log.w(TAG, "[updateCallStatusFromFCM] callId 없음 — skip")
                    return@launch
                }
                val status = payload["status"] ?: run {
                    Log.w(TAG, "[updateCallStatusFromFCM] status 없음 — skip")
                    return@launch
                }

                val existing = callDao.getById(callId)
                if (existing == null) {
                    // FCM 누락 후 첫 변경부터 도착한 케이스 — 새로 INSERT (insertCallFromFCM 동일 경로)
                    Log.d(TAG, "[updateCallStatusFromFCM] 기존 row 없음 → upsert 폴백: $callId")
                    insertCallFromFCM(payload)
                    return@launch
                }

                callDao.updateStatusWithTripInfo(
                    callId = callId,
                    status = status,
                    departure = payload["departure"]?.takeIf { it.isNotBlank() },
                    destination = payload["destination"]?.takeIf { it.isNotBlank() },
                    fare = payload["fare"]?.toLongOrNull(),
                    waypoints = payload["waypoints"]?.takeIf { it.isNotBlank() },
                    assignedDriverName = payload["assignedDriverName"]?.takeIf { it.isNotBlank() },
                    assignedDriverPhone = payload["assignedDriverPhone"]?.takeIf { it.isNotBlank() },
                    customerAddress = payload["customerAddress"]?.takeIf { it.isNotBlank() },
                )
                Log.d(TAG, "[updateCallStatusFromFCM] UPDATE: $callId → $status")
            } catch (e: Exception) {
                Log.e(TAG, "[updateCallStatusFromFCM] 실패", e)
            }
        }
    }

    // ===== 정리 =====

    suspend fun clearAllCallsInOffice(provinceId: String, cityId: String, officeId: String) {
        callDao.deleteAllInOffice(provinceId, cityId, officeId)
    }

    // ===== 매핑 =====

    private fun DocumentSnapshot.toLocalCall(
        provinceId: String,
        cityId: String,
        officeId: String
    ): LocalCall? {
        val status = getString("status") ?: return null
        val timestamp = (get("timestamp") as? Timestamp)?.toDate()?.time
            ?: getLong("timestamp")
            ?: (get("createdAt") as? Timestamp)?.toDate()?.time
            ?: System.currentTimeMillis()

        return LocalCall(
            id = id,
            provinceId = provinceId,
            cityId = cityId,
            officeId = officeId,
            status = status,
            timestamp = timestamp,
            customerName = getString("customerName"),
            customerPhone = getString("phoneNumber"),
            customerAddress = getString("customerAddress"),
            departure = getString("departure_set") ?: getString("departure"),
            destination = getString("destination_set") ?: getString("destination"),
            waypoints = getString("waypoints_set"),
            fare = getLong("fare_set") ?: getLong("fare"),
            assignedDriverId = getString("assignedDriverId"),
            assignedDriverName = getString("assignedDriverName"),
            assignedDriverPhone = getString("assignedDriverPhone"),
            callType = getString("callType"),
        )
    }
}

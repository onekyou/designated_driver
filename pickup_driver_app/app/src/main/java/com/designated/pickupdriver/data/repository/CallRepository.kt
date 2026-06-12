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
import kotlinx.coroutines.flow.Flow
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
        // 영업일 시작 시각 (KST 10시) — 정산 영업일 경계와 동일
        private const val BUSINESS_DAY_START_HOUR = 10
        // WAITING 제외: 픽업기사는 대리기사가 배차받은 콜만 모니터링 (2026-05-11)
        private val ACTIVE_STATUSES = listOf(
            Constants.STATUS_ASSIGNED,
            Constants.STATUS_ACCEPTED,
            Constants.STATUS_IN_PROGRESS,
            Constants.STATUS_AWAITING_SETTLEMENT
        )

        /**
         * 현재 영업일 시작(가장 최근 도래한 KST 10:00) epoch millis.
         * 현재가 10시 이전이면 전날 10:00. 이 시각 이전 생성 콜은 지난 영업일 = 표기 제외.
         * (기사가 운행완료 미입력한 박제 콜이 다음날까지 남는 문제 차단. 2026-06-13)
         */
        fun businessDayStartMillis(nowMillis: Long = System.currentTimeMillis()): Long {
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Seoul"))
            cal.timeInMillis = nowMillis
            cal.set(java.util.Calendar.HOUR_OF_DAY, BUSINESS_DAY_START_HOUR)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > nowMillis) {
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1)
            }
            return cal.timeInMillis
        }
    }

    // ===== Flow 노출 =====

    fun getActiveCallsFlow(provinceId: String, cityId: String, officeId: String): Flow<List<LocalCall>> {
        val cutoff = businessDayStartMillis()
        return callDao.getActiveCallsFlow(provinceId, cityId, officeId, ACTIVE_STATUSES, cutoff)
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

package com.designated.callmanager.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.local.LocalCallInfo
import com.designated.callmanager.data.local.toCallInfo
import com.designated.callmanager.data.local.toLocalCallInfo
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 콜 정보 Repository
 *
 * Local-First 아키텍처:
 * - 로컬 DB가 단일 진실 공급원 (Single Source of Truth)
 * - UI는 로컬 DB의 Flow를 구독
 * - FCM으로 원격 변경 사항 동기화
 * - 낙관적 업데이트로 즉각적인 UI 반응
 */
class CallRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    private val callDao = database.callDao()
    private val deletedCallsPrefs: SharedPreferences =
        context.getSharedPreferences("deleted_calls", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "CallRepository"
        private const val DELETED_CALLS_KEY = "deleted_call_ids"
    }

    /**
     * 삭제된 콜 ID 목록 가져오기
     */
    private fun getDeletedCallIds(): Set<String> {
        return deletedCallsPrefs.getStringSet(DELETED_CALLS_KEY, emptySet()) ?: emptySet()
    }

    /**
     * 콜 ID를 삭제 목록에 추가
     */
    private fun addDeletedCallId(callId: String) {
        val currentSet = getDeletedCallIds().toMutableSet()
        currentSet.add(callId)
        deletedCallsPrefs.edit().putStringSet(DELETED_CALLS_KEY, currentSet).apply()
        Log.d(TAG, "[삭제] 삭제 목록에 추가: $callId (총 ${currentSet.size}개)")
    }

    /**
     * 오래된 삭제 기록 정리 (24시간 이상 된 것은 제거)
     * 메모리 관리를 위해 주기적으로 호출
     */
    fun cleanupOldDeletedCalls() {
        // 삭제 목록이 100개 이상이면 전체 초기화
        val currentSet = getDeletedCallIds()
        if (currentSet.size > 100) {
            deletedCallsPrefs.edit().putStringSet(DELETED_CALLS_KEY, emptySet()).apply()
            Log.d(TAG, "[정리] 삭제 목록 초기화 (${currentSet.size}개 → 0개)")
        }
    }

    // ========================================
    // Flow 구독 (UI가 이것만 사용)
    // ========================================

    /**
     * 콜 목록 Flow 구독 (최근 1시간 이내 + 운행완료 제외)
     * UI가 이 Flow를 collect하면 DB 변경 시 자동 업데이트
     * 시간 필터는 매 emit마다 동적으로 적용됨
     */
    fun getCallsFlow(provinceId: String, officeId: String): Flow<List<CallInfo>> {
        return callDao.getAllCallsFlow(provinceId, officeId)
            .map { localCalls ->
                val oneHourAgo = System.currentTimeMillis() - (60 * 60 * 1000) // 1시간 전
                localCalls
                    .filter { it.timestamp >= oneHourAgo }
                    .filter { it.status != "COMPLETED" } // 운행완료 제외
                    .map { it.toCallInfo() }
            }
    }

    /**
     * 전체 콜 목록 Flow 구독 (시간 제한 없음)
     * 정산 등 전체 조회가 필요한 경우 사용
     */
    fun getAllCallsFlow(provinceId: String, officeId: String): Flow<List<CallInfo>> {
        return callDao.getAllCallsFlow(provinceId, officeId)
            .map { localCalls ->
                localCalls.map { it.toCallInfo() }
            }
    }

    // ========================================
    // 초기 데이터 로드 (Firestore → Room DB)
    // ========================================

    /**
     * Firestore에서 최근 콜 100개를 가져와 로컬 DB에 저장
     * 앱 시작 시 1회 실행
     * 삭제된 콜 ID는 필터링하여 제외
     */
    suspend fun refreshData(provinceId: String, cityId: String, officeId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[refreshData] 시작: $provinceId/$cityId/$officeId")

            // 삭제된 콜 ID 목록 가져오기
            val deletedCallIds = getDeletedCallIds()
            Log.d(TAG, "[refreshData] 삭제된 콜 ${deletedCallIds.size}개 필터링 예정")

            // 기존 데이터 삭제 (regionId는 내부적으로 provinceId로 사용)
            callDao.deleteAll(provinceId, officeId)

            // Firestore에서 최근 100개 가져오기
            val snapshot = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("calls")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(100)
                .get()
                .await()

            // Room DB에 저장 (삭제된 콜 제외)
            val localCalls = snapshot.documents.mapNotNull { doc ->
                // 삭제된 콜은 건너뛰기
                if (deletedCallIds.contains(doc.id)) {
                    Log.d(TAG, "[refreshData] 삭제된 콜 건너뛰기: ${doc.id}")
                    return@mapNotNull null
                }

                try {
                    val data = doc.data ?: return@mapNotNull null

                    val callInfo = CallInfo(
                        id = doc.id,
                        phoneNumber = data["phoneNumber"] as? String ?: "",
                        customerName = data["customerName"] as? String,
                        customerAddress = data["customerAddress"] as? String,
                        status = data["status"] as? String ?: "WAITING",
                        timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now(),
                        departure_set = data["departure_set"] as? String,
                        destination_set = data["destination_set"] as? String,
                        waypoints_set = data["waypoints_set"] as? String,
                        fare_set = (data["fare_set"] as? Number)?.toLong(),
                        assignedDriverId = data["assignedDriverId"] as? String,
                        assignedDriverName = data["assignedDriverName"] as? String,
                        assignedDriverPhone = data["assignedDriverPhone"] as? String,
                        callType = data["callType"] as? String,
                        fromCallDetector = data["fromCallDetector"] as? Boolean
                    )

                    callInfo.toLocalCallInfo(provinceId, officeId)
                } catch (e: Exception) {
                    Log.e(TAG, "[refreshData] 파싱 실패: ${doc.id}", e)
                    null
                }
            }

            callDao.upsertCalls(localCalls)

            Log.d(TAG, "[refreshData] 완료: ${localCalls.size}개 로드 (삭제 필터링 후)")

            // 오래된 삭제 기록 정리
            cleanupOldDeletedCalls()
        } catch (e: Exception) {
            Log.e(TAG, "[refreshData] 실패", e)
        }
    }

    // ========================================
    // FCM으로부터 업데이트 (로컬만)
    // ========================================

    /**
     * FCM 메시지로부터 콜 상태 업데이트
     * Firebase는 이미 업데이트되었으므로 로컬 DB만 업데이트
     */
    suspend fun updateCallStatusFromFCM(
        callId: String,
        newStatus: String,
        departure: String? = null,
        destination: String? = null,
        fare: Long? = null,
        waypoints: String? = null,
        driverPhone: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // 상태 업데이트
            callDao.updateCallStatus(callId, newStatus)

            // 운행 정보 업데이트
            if (departure != null || destination != null || fare != null || waypoints != null || driverPhone != null) {
                callDao.updateTripInfo(callId, departure, destination, fare, waypoints, driverPhone)
                Log.d(TAG, "[FCM] 콜 업데이트 (운행정보 포함): $callId -> $newStatus, waypoints=$waypoints, driverPhone=$driverPhone")
            } else {
                Log.d(TAG, "[FCM] 콜 상태 업데이트: $callId -> $newStatus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] 콜 업데이트 실패: $callId", e)
        }
    }

    /**
     * 배차 정보 업데이트 (로컬 DB)
     * Firestore 업데이트 후 로컬 DB도 즉시 업데이트
     */
    suspend fun updateAssignment(
        callId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        status: String
    ) = withContext(Dispatchers.IO) {
        try {
            callDao.updateAssignment(callId, driverId, driverName, driverPhone, status)
            Log.d(TAG, "[Local] 배차 정보 업데이트: $callId -> $driverName ($driverPhone)")
        } catch (e: Exception) {
            Log.e(TAG, "[Local] 배차 정보 업데이트 실패: $callId", e)
        }
    }

    /**
     * FCM NEW_CALL 메시지로부터 새 콜 삽입
     * 로컬 DB에만 저장 (Firebase는 이미 저장됨)
     */
    suspend fun insertCallFromFCM(
        callId: String,
        phoneNumber: String,
        customerName: String?,
        customerAddress: String?,
        status: String,
        provinceId: String,
        officeId: String,
        callType: String? = null,
        fromCallDetector: Boolean? = null,
        assignedDriverId: String? = null,
        assignedDriverName: String? = null,
        assignedDriverPhone: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val localCall = LocalCallInfo(
                id = callId,
                phoneNumber = phoneNumber,
                customerName = customerName,
                customerAddress = customerAddress,
                status = status,
                timestamp = System.currentTimeMillis(),
                departure_set = null,
                destination_set = null,
                waypoints_set = null,
                fare_set = null,
                assignedDriverId = assignedDriverId,
                assignedDriverName = assignedDriverName,
                assignedDriverPhone = assignedDriverPhone,
                callType = callType,
                fromCallDetector = fromCallDetector,
                regionId = provinceId, // regionId 필드에 provinceId 저장
                officeId = officeId,
                synced = true,
                lastUpdated = System.currentTimeMillis()
            )

            callDao.upsertCall(localCall)
            Log.d(TAG, "[FCM] 새 콜 로컬 DB 삽입 완료: $callId")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] 새 콜 삽입 실패: $callId", e)
        }
    }

    // ========================================
    // 사용자 액션 (낙관적 업데이트)
    // ========================================

    /**
     * 콜 배차 (낙관적 업데이트)
     * 1. 로컬 DB 즉시 업데이트 → UI 즉시 반영
     * 2. Firebase 백그라운드 업데이트
     */
    suspend fun assignCall(
        callId: String,
        driverId: String,
        driverName: String,
        driverPhone: String
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. 로컬 즉시 업데이트
            callDao.updateAssignment(
                callId = callId,
                driverId = driverId,
                driverName = driverName,
                driverPhone = driverPhone,
                status = CallStatus.ASSIGNED.firestoreValue
            )

            Log.d(TAG, "[배차] 로컬 업데이트 완료: $callId -> $driverId")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    firestore.collection("calls").document(callId)
                        .update(
                            mapOf(
                                "assignedDriverId" to driverId,
                                "assignedDriverName" to driverName,
                                "assignedDriverPhone" to driverPhone,
                                "status" to CallStatus.ASSIGNED.firestoreValue,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                        )
                        .await()

                    Log.d(TAG, "[배차] Firebase 업데이트 완료: $callId")
                } catch (e: Exception) {
                    Log.e(TAG, "[배차] Firebase 업데이트 실패: $callId", e)
                    // TODO: 실패 시 retry 또는 로컬 DB synced = false 마킹
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[배차] 실패: $callId", e)
        }
    }

    /**
     * 콜 삭제 (로컬 DB에서 삭제 + 삭제 목록에 추가)
     * Firestore에는 남아있지만, 삭제 목록에 추가되어 refreshData 시에도 복구되지 않음
     */
    suspend fun deleteCall(callId: String) = withContext(Dispatchers.IO) {
        try {
            // 삭제 목록에 추가 (refreshData 시 필터링용)
            addDeletedCallId(callId)

            // 로컬 DB에서 삭제
            callDao.deleteCall(callId)
            Log.d(TAG, "[삭제] 로컬 DB에서 콜 삭제 완료: $callId")
        } catch (e: Exception) {
            Log.e(TAG, "[삭제] 로컬 DB 삭제 실패: $callId", e)
        }
    }

    /**
     * 콜 생성 (낙관적 업데이트)
     */
    suspend fun createCall(callInfo: CallInfo, provinceId: String, cityId: String, officeId: String): String = withContext(Dispatchers.IO) {
        try {
            // 임시 ID로 로컬 즉시 저장
            val tempId = "temp_${System.currentTimeMillis()}"
            val localCall = callInfo.copy(id = tempId).toLocalCallInfo(provinceId, officeId)
            callDao.upsertCall(localCall)

            Log.d(TAG, "[콜 생성] 로컬 업데이트 완료: $tempId")

            // Firebase 백그라운드 업로드
            scope.launch {
                try {
                    val docRef = firestore
                        .collection("provinces").document(provinceId)
                        .collection("cities").document(cityId)
                        .collection("offices").document(officeId)
                        .collection("calls")
                        .add(callInfo)
                        .await()

                    // 임시 ID 삭제하고 실제 ID로 재저장
                    callDao.deleteCall(tempId)
                    val finalCall = callInfo.copy(id = docRef.id).toLocalCallInfo(provinceId, officeId)
                    callDao.upsertCall(finalCall)

                    Log.d(TAG, "[콜 생성] Firebase 업로드 완료: ${docRef.id}")
                } catch (e: Exception) {
                    Log.e(TAG, "[콜 생성] Firebase 업로드 실패: $tempId", e)
                }
            }

            return@withContext tempId
        } catch (e: Exception) {
            Log.e(TAG, "[콜 생성] 실패", e)
            return@withContext ""
        }
    }
}

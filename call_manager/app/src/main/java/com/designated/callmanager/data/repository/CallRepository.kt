package com.designated.callmanager.data.repository

import android.util.Log
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import com.designated.callmanager.data.local.AppDatabase
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
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    private val callDao = database.callDao()

    companion object {
        private const val TAG = "CallRepository"
    }

    // ========================================
    // Flow 구독 (UI가 이것만 사용)
    // ========================================

    /**
     * 콜 목록 Flow 구독
     * UI가 이 Flow를 collect하면 DB 변경 시 자동 업데이트
     */
    fun getCallsFlow(provinceId: String, officeId: String): Flow<List<CallInfo>> {
        return callDao.getCallsFlow(provinceId, officeId)
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
     */
    suspend fun refreshData(provinceId: String, cityId: String, officeId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[refreshData] 시작: $provinceId/$cityId/$officeId")

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

            // Room DB에 저장
            val localCalls = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

                    val callInfo = CallInfo(
                        id = doc.id,
                        phoneNumber = data["phoneNumber"] as? String ?: "",
                        customerName = data["customerName"] as? String,
                        customerAddress = data["customerAddress"] as? String,
                        status = data["status"] as? String ?: "WAITING",
                        timestamp = data["timestamp"] as? Timestamp,
                        departure_set = data["departure_set"] as? String,
                        destination_set = data["destination_set"] as? String,
                        waypoints_set = data["waypoints_set"] as? String,
                        fare_set = (data["fare_set"] as? Number)?.toLong(),
                        assignedDriverId = data["assignedDriverId"] as? String,
                        assignedDriverAuthUid = data["assignedDriverAuthUid"] as? String,
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

            Log.d(TAG, "[refreshData] 완료: ${localCalls.size}개 로드")
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
        fare: Long? = null
    ) = withContext(Dispatchers.IO) {
        try {
            // 상태 업데이트
            callDao.updateCallStatus(callId, newStatus)

            // 운행 정보 업데이트
            if (departure != null || destination != null || fare != null) {
                callDao.updateTripInfo(callId, departure, destination, fare)
                Log.d(TAG, "[FCM] 콜 업데이트 (운행정보 포함): $callId -> $newStatus")
            } else {
                Log.d(TAG, "[FCM] 콜 상태 업데이트: $callId -> $newStatus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] 콜 업데이트 실패: $callId", e)
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
        driverAuthUid: String?,
        driverName: String,
        driverPhone: String
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. 로컬 즉시 업데이트
            callDao.updateAssignment(
                callId = callId,
                driverId = driverId,
                driverAuthUid = driverAuthUid,
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
                                "assignedDriverAuthUid" to driverAuthUid,
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

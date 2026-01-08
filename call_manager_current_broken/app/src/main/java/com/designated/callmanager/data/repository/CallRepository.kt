package com.designated.callmanager.data.repository

import android.util.Log
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.local.LocalCallInfo
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * 콜 정보 관리 Repository
 * - 로컬 우선 읽기 (Room Database)
 * - 백그라운드 Firebase 동기화
 * - 비용 최적화 (리스너 제거, 필요할 때만 Firebase 읽기)
 */
class CallRepository(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "CallRepository"
        private const val SYNC_BATCH_SIZE = 50L
        private const val MINIMAL_LISTENER_LIMIT = 10L // 최소한의 리스너 (최근 10개만)
    }

    private val callDao = database.callDao()

    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    // ❌ syncListener 제거 - FCM 전용 모드

    // ====== 콜 목록 조회 ======

    /**
     * 콜 목록 Flow (로컬 우선)
     */
    fun getCallsFlow(regionId: String, officeId: String): Flow<List<CallInfo>> {
        ensureSyncSetup(regionId, officeId)

        return callDao.getCallsFlow(regionId, officeId)
            .map { localCalls ->
                localCalls.map { it.toFirebaseCallInfo() }
            }
    }

    /**
     * 대기 중인 콜만 조회
     */
    fun getWaitingCallsFlow(regionId: String, officeId: String): Flow<List<CallInfo>> {
        ensureSyncSetup(regionId, officeId)

        return callDao.getWaitingCallsFlow(regionId, officeId)
            .map { localCalls ->
                localCalls.map { it.toFirebaseCallInfo() }
            }
    }

    /**
     * 최근 콜 조회 (제한된 개수)
     */
    suspend fun getRecentCalls(regionId: String, officeId: String, limit: Int): List<CallInfo> {
        ensureSyncSetup(regionId, officeId)

        val localCalls = callDao.getRecentCalls(regionId, officeId, limit)
        return localCalls.map { it.toFirebaseCallInfo() }
    }

    // ====== 콜 생성 및 수정 (낙관적 업데이트) ======

    /**
     * 새 콜 생성 (로컬 우선 + Firebase 백그라운드)
     */
    suspend fun createCall(callInfo: CallInfo): String {
        try {
            // 1. 로컬에 즉시 저장 (임시 ID)
            val tempId = "temp_${System.currentTimeMillis()}"
            val localCall = LocalCallInfo.fromFirebaseCallInfo(callInfo.copy(id = tempId))
                .copy(synced = false)

            callDao.insertCall(localCall)
            Log.d(TAG, "✅ 로컬 콜 생성 완료 (즉시 UI 반영): $tempId")

            // 2. Firebase에 백그라운드 업로드
            scope.launch {
                try {
                    val docRef = firestore.collection("regions")
                        .document(callInfo.regionId ?: "")
                        .collection("offices")
                        .document(callInfo.officeId ?: "")
                        .collection("calls")
                        .add(callInfo)
                        .await()

                    val realId = docRef.id

                    // 3. 로컬 ID 업데이트
                    callDao.deleteCall(tempId)
                    val updatedCall = localCall.copy(id = realId, synced = true)
                    callDao.insertCall(updatedCall)

                    Log.d(TAG, "✅ Firebase 콜 생성 완료: $realId")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Firebase 콜 생성 실패 (로컬에는 저장됨)", e)
                }
            }

            return tempId

        } catch (e: Exception) {
            Log.e(TAG, "❌ 로컬 콜 생성 실패", e)
            throw e
        }
    }

    /**
     * 콜 배차 (로컬 우선 + Firebase 백그라운드)
     */
    suspend fun assignCall(
        callId: String,
        driverId: String,
        driverName: String?,
        driverPhone: String?,
        newStatus: String
    ) {
        try {
            val now = System.currentTimeMillis()

            // 1. 로컬 즉시 업데이트
            callDao.updateCallStatus(callId, newStatus, driverId, now)
            callDao.updateAssignedDriver(callId, driverId, driverName, driverPhone, now)

            Log.d(TAG, "✅ 로컬 배차 완료 (즉시 UI 반영): $callId -> $driverName")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val call = callDao.getCallById(callId)
                    if (call == null) {
                        Log.e(TAG, "❌ 콜을 찾을 수 없음: $callId")
                        return@launch
                    }

                    firestore.collection("regions")
                        .document(call.regionId ?: "")
                        .collection("offices")
                        .document(call.officeId ?: "")
                        .collection("calls")
                        .document(callId)
                        .update(
                            mapOf(
                                "status" to newStatus,
                                "assignedDriverId" to driverId,
                                "assignedDriverName" to driverName,
                                "assignedDriverPhone" to driverPhone,
                                "assignedTimestamp" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .await()

                    // 동기화 완료 표시
                    callDao.updateSyncStatus(callId, true)

                    Log.d(TAG, "✅ Firebase 배차 완료: $callId")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Firebase 배차 실패 (로컬에는 저장됨)", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 로컬 배차 실패", e)
            throw e
        }
    }

    /**
     * 콜 상태 업데이트 (로컬 우선 + Firebase 백그라운드)
     */

    /**
     * FCM으로부터 콜 상태 업데이트 (로컬만, Firebase 업데이트 없음)
     */
    suspend fun updateCallStatusFromFCM(
        callId: String,
        newStatus: String,
        departure: String? = null,
        destination: String? = null
    ) {
        try {
            callDao.updateCallStatus(callId, newStatus, null)

            // destination 정보가 있으면 함께 업데이트
            if (departure != null || destination != null) {
                callDao.updateDestinationInfo(callId, departure, destination)
                Log.d(TAG, "✅ [FCM] 콜 상태 + 목적지 로컬 업데이트: $callId -> $newStatus (출발: $departure, 도착: $destination)")
            } else {
                Log.d(TAG, "✅ [FCM] 콜 상태 로컬 업데이트: $callId -> $newStatus")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [FCM] 콜 상태 로컬 업데이트 실패", e)
        }
    }

    suspend fun updateCallStatus(callId: String, newStatus: String) {
        try {
            // 1. 로컬 즉시 업데이트
            callDao.updateCallStatus(callId, newStatus, null)

            Log.d(TAG, "✅ 로컬 상태 업데이트 (즉시 UI 반영): $callId -> $newStatus")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val call = callDao.getCallById(callId)
                    if (call == null) {
                        Log.e(TAG, "❌ 콜을 찾을 수 없음: $callId")
                        return@launch
                    }

                    firestore.collection("regions")
                        .document(call.regionId ?: "")
                        .collection("offices")
                        .document(call.officeId ?: "")
                        .collection("calls")
                        .document(callId)
                        .update("status", newStatus)
                        .await()

                    // 동기화 완료 표시
                    callDao.updateSyncStatus(callId, true)

                    Log.d(TAG, "✅ Firebase 상태 업데이트 완료: $callId")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Firebase 상태 업데이트 실패 (로컬에는 저장됨)", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 로컬 상태 업데이트 실패", e)
            throw e
        }
    }

    /**
     * 운행 정보 업데이트
     */
    suspend fun updateTripInfo(
        callId: String,
        departure: String?,
        destination: String?,
        fare: Long?,
        paymentMethod: String?,
        cashAmount: Long?
    ) {
        try {
            // 1. 로컬 즉시 업데이트
            callDao.updateTripInfo(callId, departure, destination, fare, paymentMethod, cashAmount)

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val call = callDao.getCallById(callId)
                    if (call == null) return@launch

                    firestore.collection("regions")
                        .document(call.regionId ?: "")
                        .collection("offices")
                        .document(call.officeId ?: "")
                        .collection("calls")
                        .document(callId)
                        .update(
                            mapOf(
                                "departure" to departure,
                                "destination" to destination,
                                "fare" to fare,
                                "paymentMethod" to paymentMethod,
                                "cashAmount" to cashAmount
                            )
                        )
                        .await()

                    callDao.updateSyncStatus(callId, true)

                } catch (e: Exception) {
                    Log.e(TAG, "Firebase 운행 정보 업데이트 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "로컬 운행 정보 업데이트 실패", e)
            throw e
        }
    }

    /**
     * 콜 완료 처리 (로컬 우선 + Firebase 백그라운드)
     */
    suspend fun completeCall(callId: String, assignedDriverAuthUid: String?) {
        try {
            // 1. 로컬 즉시 업데이트
            callDao.updateCallStatus(callId, "COMPLETED", null)

            Log.d(TAG, "✅ 로컬 콜 완료 처리 (즉시 UI 반영): $callId")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val call = callDao.getCallById(callId)
                    if (call == null) {
                        Log.e(TAG, "❌ 콜을 찾을 수 없음: $callId")
                        return@launch
                    }

                    // Firebase 콜 상태 업데이트
                    firestore.collection("regions")
                        .document(call.regionId ?: "")
                        .collection("offices")
                        .document(call.officeId ?: "")
                        .collection("calls")
                        .document(callId)
                        .update("status", "COMPLETED")
                        .await()

                    callDao.updateSyncStatus(callId, true)

                    Log.d(TAG, "✅ Firebase 콜 완료 처리: $callId")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Firebase 콜 완료 처리 실패 (로컬에는 저장됨)", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 로컬 콜 완료 처리 실패", e)
            throw e
        }
    }

    /**
     * 콜 취소 처리 (로컬 우선 + Firebase 백그라운드)
     */
    suspend fun cancelCall(callId: String) {
        updateCallStatus(callId, "CANCELED")
    }

    /**
     * 콜 삭제 (로컬 우선 + Firebase 백그라운드)
     */
    suspend fun deleteCall(callId: String) {
        try {
            // 1. 로컬 즉시 삭제
            callDao.deleteCall(callId)

            Log.d(TAG, "✅ 로컬 콜 삭제 (즉시 UI 반영): $callId")

            // 2. Firebase 백그라운드 삭제
            scope.launch {
                try {
                    // 삭제 전에 regionId, officeId 조회 필요
                    // 이미 로컬에서 삭제되었으므로 currentRegionId/currentOfficeId 사용
                    if (currentRegionId != null && currentOfficeId != null) {
                        firestore.collection("regions")
                            .document(currentRegionId!!)
                            .collection("offices")
                            .document(currentOfficeId!!)
                            .collection("calls")
                            .document(callId)
                            .delete()
                            .await()

                        Log.d(TAG, "✅ Firebase 콜 삭제 완료: $callId")
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Firebase 콜 삭제 실패 (로컬에서는 삭제됨)", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 로컬 콜 삭제 실패", e)
            throw e
        }
    }

    // ====== 동기화 관리 ======

    /**
     * 동기화 설정 확인 및 초기화
     */
    private fun ensureSyncSetup(regionId: String, officeId: String) {
        if (currentRegionId != regionId || currentOfficeId != officeId) {
            stopSync()
            startSync(regionId, officeId)
            currentRegionId = regionId
            currentOfficeId = officeId
        }
    }

    /**
     * 초기 데이터 로드 (FCM 전용 - 리스너 없음)
     */
    private fun startSync(regionId: String, officeId: String) {
        Log.d(TAG, "✅ 콜 초기 데이터 로드: $regionId/$officeId (FCM 전용 모드)")

        // ✅ 한 번만 전체 콜 데이터 로드
        scope.launch {
            try {
                refreshData(regionId, officeId)
                Log.d(TAG, "✅ 초기 콜 데이터 로드 완료 - 이후 업데이트는 FCM을 통해서만")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 초기 콜 데이터 로드 실패", e)
            }
        }

        // ❌ 리스너 제거됨 - 이후 업데이트는 순수하게 FCM을 통해서만!
    }

    /**
     * 새로운 콜 동기화
     */
    private suspend fun syncNewCalls(
        firebaseDocuments: List<com.google.firebase.firestore.DocumentSnapshot>
    ) {
        val lastLocalTimestamp = callDao.getLastCallTimestamp(
            currentRegionId ?: "",
            currentOfficeId ?: ""
        ) ?: 0

        val newCalls = firebaseDocuments.mapNotNull { doc ->
            try {
                val call = doc.toObject(CallInfo::class.java)?.copy(id = doc.id)
                val timestamp = call?.timestamp?.toDate()?.time ?: 0

                // 로컬에 없는 새로운 콜만 동기화
                if (call != null && timestamp > lastLocalTimestamp) {
                    LocalCallInfo.fromFirebaseCallInfo(call)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "콜 파싱 실패: ${doc.id}", e)
                null
            }
        }

        if (newCalls.isNotEmpty()) {
            callDao.insertCalls(newCalls)
            Log.d(TAG, "✅ 새 콜 ${newCalls.size}개 동기화 완료")
        }
    }

    /**
     * 전체 데이터 새로고침 (수동 동기화)
     */
    suspend fun refreshData(regionId: String, officeId: String) {
        Log.d(TAG, "수동 데이터 새로고침 시작")

        try {
            val officeRef = firestore.collection("regions").document(regionId)
                .collection("offices").document(officeId)

            // 최근 50개 콜 동기화
            val callsSnapshot = officeRef.collection("calls")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(SYNC_BATCH_SIZE)
                .get()
                .await()

            val firebaseCalls = callsSnapshot.documents.mapNotNull { doc ->
                try {
                    val call = doc.toObject(CallInfo::class.java)?.copy(id = doc.id)
                    call?.let { LocalCallInfo.fromFirebaseCallInfo(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "콜 파싱 실패: ${doc.id}", e)
                    null
                }
            }

            if (firebaseCalls.isNotEmpty()) {
                callDao.insertCalls(firebaseCalls)
            }

            Log.d(TAG, "데이터 새로고침 완료: 콜 ${firebaseCalls.size}개")

        } catch (e: Exception) {
            Log.e(TAG, "데이터 새로고침 실패", e)
            throw e
        }
    }

    /**
     * 동기화 중지
     */
    fun stopSync() {
        // ❌ syncListener 제거됨 - FCM 전용 모드
        currentRegionId = null
        currentOfficeId = null
        Log.d(TAG, "✅ 콜 초기화 중지 (FCM 전용 모드)")
    }

    // ====== 유틸리티 ======

    /**
     * 오래된 콜 데이터 정리
     */
    suspend fun cleanupOldCalls(beforeDays: Int = 30) {
        try {
            val cutoffTime = System.currentTimeMillis() - (beforeDays * 24 * 60 * 60 * 1000L)
            val deletedCount = callDao.deleteOldCalls(cutoffTime)
            Log.d(TAG, "오래된 콜 ${deletedCount}개 정리 완료")
        } catch (e: Exception) {
            Log.e(TAG, "콜 정리 실패", e)
        }
    }
}

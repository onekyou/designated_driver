package com.designated.callmanager.data.repository

import android.util.Log
import com.designated.callmanager.data.DriverInfo
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.local.LocalDriverInfo
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
 * 기사 정보 관리 Repository
 * - 로컬 우선 읽기 (Room Database)
 * - 백그라운드 Firebase 동기화
 * - 비용 최적화 (리스너 제거, 필요할 때만 Firebase 읽기)
 */
class DriverRepository(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    companion object {
        private const val TAG = "DriverRepository"
        private const val MINIMAL_LISTENER_LIMIT = 10L // 최소한의 리스너 (최근 10개만)
    }

    private val driverDao = database.driverDao()

    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    // ❌ syncListener 제거 - FCM 전용 모드

    // ====== 기사 목록 조회 ======

    /**
     * 기사 목록 Flow (로컬 우선)
     */
    fun getDriversFlow(regionId: String, officeId: String): Flow<List<DriverInfo>> {
        ensureSyncSetup(regionId, officeId)

        return driverDao.getDriversFlow(regionId, officeId)
            .map { localDrivers ->
                localDrivers.map { it.toFirebaseDriverInfo() }
            }
    }

    /**
     * 대기 중인 기사만 조회
     */
    fun getWaitingDriversFlow(regionId: String, officeId: String): Flow<List<DriverInfo>> {
        ensureSyncSetup(regionId, officeId)

        return driverDao.getWaitingDriversFlow(regionId, officeId)
            .map { localDrivers ->
                localDrivers.map { it.toFirebaseDriverInfo() }
            }
    }

    /**
     * 특정 기사 조회 (ID로)
     */
    suspend fun getDriverById(driverId: String): DriverInfo? {
        return driverDao.getDriverById(driverId)?.toFirebaseDriverInfo()
    }

    /**
     * 특정 기사 조회 (authUid로)
     */
    suspend fun getDriverByAuthUid(authUid: String): DriverInfo? {
        return driverDao.getDriverByAuthUid(authUid)?.toFirebaseDriverInfo()
    }

    // ====== 기사 상태 업데이트 (낙관적 업데이트) ======

    /**
     * 기사 상태 업데이트 (로컬 우선 + Firebase 백그라운드)
     */
    suspend fun updateDriverStatus(driverId: String, newStatus: String) {
        try {
            val now = System.currentTimeMillis()

            // 1. 로컬 즉시 업데이트
            driverDao.updateDriverStatus(driverId, newStatus, now)

            Log.d(TAG, "✅ 로컬 기사 상태 업데이트 (즉시 UI 반영): $driverId -> $newStatus")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val driver = driverDao.getDriverById(driverId)
                    if (driver == null) {
                        Log.e(TAG, "❌ 기사를 찾을 수 없음: $driverId")
                        return@launch
                    }

                    firestore.collection("regions")
                        .document(driver.regionId ?: "")
                        .collection("offices")
                        .document(driver.officeId ?: "")
                        .collection("designated_drivers")
                        .document(driverId)
                        .update(
                            mapOf(
                                "status" to newStatus,
                                "updatedAt" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .await()

                    // 동기화 완료 표시
                    driverDao.updateSyncStatus(driverId, true)

                    Log.d(TAG, "✅ Firebase 기사 상태 업데이트 완료: $driverId")

                } catch (e: Exception) {
                    Log.e(TAG, "❌ Firebase 기사 상태 업데이트 실패 (로컬에는 저장됨)", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ 로컬 기사 상태 업데이트 실패", e)
            throw e
        }
    }

    /**
     * FCM으로부터 기사 상태 업데이트 (로컬만, Firebase 업데이트 없음)
     */
    suspend fun updateDriverStatusFromFCM(driverId: String, newStatus: String) {
        try {
            val now = System.currentTimeMillis()
            driverDao.updateDriverStatus(driverId, newStatus, now)
            Log.d(TAG, "✅ [FCM] 기사 상태 로컬 업데이트: $driverId -> $newStatus")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [FCM] 기사 상태 로컬 업데이트 실패", e)
        }
    }


    /**
     * 기사 상태 업데이트 (authUid로)
     */
    suspend fun updateDriverStatusByAuthUid(authUid: String, newStatus: String) {
        try {
            val now = System.currentTimeMillis()

            // 1. 로컬 즉시 업데이트
            driverDao.updateDriverStatusByAuthUid(authUid, newStatus, now)

            Log.d(TAG, "✅ 로컬 기사 상태 업데이트: $authUid -> $newStatus")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val driver = driverDao.getDriverByAuthUid(authUid)
                    if (driver == null) {
                        Log.e(TAG, "❌ 기사를 찾을 수 없음: $authUid")
                        return@launch
                    }

                    firestore.collection("regions")
                        .document(driver.regionId ?: "")
                        .collection("offices")
                        .document(driver.officeId ?: "")
                        .collection("designated_drivers")
                        .document(driver.id)
                        .update(
                            mapOf(
                                "status" to newStatus,
                                "updatedAt" to com.google.firebase.Timestamp.now()
                            )
                        )
                        .await()

                    driverDao.updateSyncStatus(driver.id, true)

                } catch (e: Exception) {
                    Log.e(TAG, "Firebase 기사 상태 업데이트 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "로컬 기사 상태 업데이트 실패", e)
            throw e
        }
    }

    /**
     * 기사 승인
     */
    suspend fun approveDriver(driverId: String) {
        try {
            // 1. 로컬 즉시 업데이트
            driverDao.updateApprovalStatus(driverId, "APPROVED", System.currentTimeMillis())

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val driver = driverDao.getDriverById(driverId)
                    if (driver == null) return@launch

                    firestore.collection("regions")
                        .document(driver.regionId ?: "")
                        .collection("offices")
                        .document(driver.officeId ?: "")
                        .collection("designated_drivers")
                        .document(driverId)
                        .update(
                            mapOf(
                                "approvalStatus" to "APPROVED",
                                "approvedAt" to com.google.firebase.Timestamp.now(),
                                "status" to "OFFLINE"
                            )
                        )
                        .await()

                    driverDao.updateSyncStatus(driverId, true)

                } catch (e: Exception) {
                    Log.e(TAG, "Firebase 기사 승인 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "로컬 기사 승인 실패", e)
            throw e
        }
    }

    /**
     * 기사 거절
     */
    suspend fun rejectDriver(driverId: String) {
        try {
            // 1. 로컬 즉시 업데이트
            driverDao.updateApprovalStatus(driverId, "REJECTED")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    val driver = driverDao.getDriverById(driverId)
                    if (driver == null) return@launch

                    firestore.collection("regions")
                        .document(driver.regionId ?: "")
                        .collection("offices")
                        .document(driver.officeId ?: "")
                        .collection("designated_drivers")
                        .document(driverId)
                        .update("approvalStatus", "REJECTED")
                        .await()

                    driverDao.updateSyncStatus(driverId, true)

                } catch (e: Exception) {
                    Log.e(TAG, "Firebase 기사 거절 실패", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "로컬 기사 거절 실패", e)
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
        Log.d(TAG, "✅ 기사 초기 데이터 로드: $regionId/$officeId (FCM 전용 모드)")

        // ✅ 한 번만 전체 기사 데이터 로드
        scope.launch {
            try {
                refreshData(regionId, officeId)
                Log.d(TAG, "✅ 초기 기사 데이터 로드 완료 - 이후 업데이트는 FCM을 통해서만")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 초기 기사 데이터 로드 실패", e)
            }
        }

        // ❌ 리스너 제거됨 - 이후 업데이트는 순수하게 FCM을 통해서만!
    }

    /**
     * 새로운 기사 정보 동기화
     */
    private suspend fun syncNewDrivers(
        firebaseDocuments: List<com.google.firebase.firestore.DocumentSnapshot>
    ) {
        val lastLocalTimestamp = driverDao.getLastDriverUpdateTimestamp(
            currentRegionId ?: "",
            currentOfficeId ?: ""
        ) ?: 0

        val newDrivers = firebaseDocuments.mapNotNull { doc ->
            try {
                val driver = doc.toObject(DriverInfo::class.java)?.copy(id = doc.id)
                val timestamp = driver?.updatedAt?.toDate()?.time ?: 0

                // 로컬에 없거나 업데이트된 기사만 동기화
                if (driver != null && timestamp > lastLocalTimestamp) {
                    LocalDriverInfo.fromFirebaseDriverInfo(driver)
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "기사 파싱 실패: ${doc.id}", e)
                null
            }
        }

        if (newDrivers.isNotEmpty()) {
            driverDao.insertDrivers(newDrivers)
            Log.d(TAG, "✅ 기사 ${newDrivers.size}명 동기화 완료")
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

            // 모든 기사 동기화 (기사 수가 적으므로 전체 조회)
            val driversSnapshot = officeRef.collection("designated_drivers")
                .get()
                .await()

            val firebaseDrivers = driversSnapshot.documents.mapNotNull { doc ->
                try {
                    val driver = doc.toObject(DriverInfo::class.java)?.copy(id = doc.id)
                    driver?.let { LocalDriverInfo.fromFirebaseDriverInfo(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "기사 파싱 실패: ${doc.id}", e)
                    null
                }
            }

            if (firebaseDrivers.isNotEmpty()) {
                driverDao.insertDrivers(firebaseDrivers)
            }

            Log.d(TAG, "데이터 새로고침 완료: 기사 ${firebaseDrivers.size}명")

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
        Log.d(TAG, "✅ 기사 초기화 중지 (FCM 전용 모드)")
    }
}

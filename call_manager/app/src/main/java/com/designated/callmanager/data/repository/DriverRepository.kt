package com.designated.callmanager.data.repository

import android.util.Log
import com.designated.callmanager.data.DriverInfo
import com.designated.callmanager.data.local.AppDatabase
import com.designated.callmanager.data.local.toDriverInfo
import com.designated.callmanager.data.local.toLocalDriverInfo
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * 기사 정보 Repository
 *
 * Local-First 아키텍처로 기사 데이터 관리
 */
class DriverRepository(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    private val driverDao = database.driverDao()

    companion object {
        private const val TAG = "DriverRepository"
    }

    // ========================================
    // Flow 구독
    // ========================================

    /**
     * 기사 목록 Flow 구독
     */
    fun getDriversFlow(provinceId: String, officeId: String): Flow<List<DriverInfo>> {
        return driverDao.getDriversFlow(provinceId, officeId)
            .map { localDrivers ->
                localDrivers.map { it.toDriverInfo() }
            }
    }

    // ========================================
    // 초기 데이터 로드
    // ========================================

    /**
     * Firestore에서 모든 기사를 가져와 로컬 DB에 저장
     */
    suspend fun refreshData(provinceId: String, cityId: String, officeId: String) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "[refreshData] 시작: $provinceId/$cityId/$officeId")

            // 기존 데이터 삭제
            driverDao.deleteAll(provinceId, officeId)

            // Firestore에서 모든 기사 가져오기
            val snapshot = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("designated_drivers")
                .get()
                .await()

            // Room DB에 저장
            val localDrivers = snapshot.documents.mapNotNull { doc ->
                try {
                    val data = doc.data ?: return@mapNotNull null

                    val driverInfo = DriverInfo(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        phoneNumber = data["phoneNumber"] as? String ?: "",
                        authUid = data["authUid"] as? String,
                        status = data["status"] as? String ?: "OFFLINE",
                        createdAt = data["createdAt"] as? Timestamp,
                        updatedAt = data["updatedAt"] as? Timestamp,
                        lastLoginTime = data["lastLoginTime"] as? Timestamp
                    )

                    driverInfo.toLocalDriverInfo(provinceId, officeId)
                } catch (e: Exception) {
                    Log.e(TAG, "[refreshData] 파싱 실패: ${doc.id}", e)
                    null
                }
            }

            driverDao.upsertDrivers(localDrivers)

            Log.d(TAG, "[refreshData] 완료: ${localDrivers.size}명 로드")
        } catch (e: Exception) {
            Log.e(TAG, "[refreshData] 실패", e)
        }
    }

    // ========================================
    // FCM으로부터 업데이트
    // ========================================

    /**
     * FCM 메시지로부터 기사 상태 업데이트
     */
    suspend fun updateDriverStatusFromFCM(
        driverId: String,
        newStatus: String
    ) = withContext(Dispatchers.IO) {
        try {
            driverDao.updateDriverStatus(driverId, newStatus)
            Log.d(TAG, "[FCM] 기사 상태 업데이트: $driverId -> $newStatus")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] 기사 상태 업데이트 실패: $driverId", e)
        }
    }

    /**
     * FCM 메시지로부터 기사 상태 + lastLoginTime 업데이트
     */
    suspend fun updateDriverStatusWithLoginTimeFromFCM(
        driverId: String,
        newStatus: String,
        lastLoginTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            driverDao.updateDriverStatusWithLoginTime(driverId, newStatus, lastLoginTime)
            Log.d(TAG, "[FCM] 기사 상태+로그인시간 업데이트: $driverId -> $newStatus, loginTime=$lastLoginTime")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] 기사 상태+로그인시간 업데이트 실패: $driverId", e)
        }
    }

    /**
     * AuthUid로 기사 상태 업데이트
     */
    suspend fun updateDriverStatusByAuthUidFromFCM(
        authUid: String,
        newStatus: String
    ) = withContext(Dispatchers.IO) {
        try {
            driverDao.updateDriverStatusByAuthUid(authUid, newStatus)
            Log.d(TAG, "[FCM] 기사 상태 업데이트 (AuthUid): $authUid -> $newStatus")
        } catch (e: Exception) {
            Log.e(TAG, "[FCM] 기사 상태 업데이트 실패 (AuthUid): $authUid", e)
        }
    }

    // ========================================
    // 사용자 액션
    // ========================================

    /**
     * 기사 상태 변경 (낙관적 업데이트)
     */
    suspend fun updateDriverStatus(
        driverId: String,
        newStatus: String,
        provinceId: String,
        cityId: String,
        officeId: String
    ) = withContext(Dispatchers.IO) {
        try {
            // 1. 로컬 즉시 업데이트
            driverDao.updateDriverStatus(driverId, newStatus)

            Log.d(TAG, "[상태 변경] 로컬 업데이트 완료: $driverId -> $newStatus")

            // 2. Firebase 백그라운드 업데이트
            scope.launch {
                try {
                    firestore
                        .collection("provinces").document(provinceId)
                        .collection("cities").document(cityId)
                        .collection("offices").document(officeId)
                        .collection("designated_drivers").document(driverId)
                        .update(
                            mapOf(
                                "status" to newStatus,
                                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                            )
                        )
                        .await()

                    Log.d(TAG, "[상태 변경] Firebase 업데이트 완료: $driverId")
                } catch (e: Exception) {
                    Log.e(TAG, "[상태 변경] Firebase 업데이트 실패: $driverId", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[상태 변경] 실패: $driverId", e)
        }
    }

    /**
     * 기사 정보 조회 (단일)
     */
    suspend fun getDriverById(driverId: String): DriverInfo? = withContext(Dispatchers.IO) {
        try {
            driverDao.getDriverById(driverId)?.toDriverInfo()
        } catch (e: Exception) {
            Log.e(TAG, "[기사 조회] 실패: $driverId", e)
            null
        }
    }
}

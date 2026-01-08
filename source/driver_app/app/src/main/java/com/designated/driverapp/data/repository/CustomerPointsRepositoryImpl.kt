package com.designated.driverapp.data.repository

import android.content.SharedPreferences
import android.util.Log
import com.designated.driverapp.data.model.CustomerPoints
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 고객 포인트 Repository 구현
 * - 인메모리 캐시로 중복 쿼리 방지
 * - TTL(Time To Live) 기반 캐시 만료
 */
@Singleton
class CustomerPointsRepositoryImpl @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val sharedPreferences: SharedPreferences
) : CustomerPointsRepository {

    private val TAG = "CustomerPointsRepo"

    // 인메모리 캐시
    private val cache = mutableMapOf<String, CachedPoints>()

    // 캐시 TTL (5분)
    private val CACHE_TTL_MS = 5 * 60 * 1000L

    private data class CachedPoints(
        val points: CustomerPoints,
        val timestamp: Long
    ) {
        fun isExpired(): Boolean {
            return System.currentTimeMillis() - timestamp > 5 * 60 * 1000L
        }
    }

    override suspend fun getCustomerPoints(
        phoneNumber: String,
        forceRefresh: Boolean
    ): CustomerPoints? {
        return try {
            // 캐시 확인 (forceRefresh가 아니고, 캐시가 유효한 경우)
            if (!forceRefresh) {
                cache[phoneNumber]?.let { cached ->
                    if (!cached.isExpired()) {
                        Log.d(TAG, "캐시에서 포인트 반환: $phoneNumber")
                        return cached.points
                    }
                }
            }

            // Firestore에서 조회
            val (regionId, officeId) = getDriverLocationInfo()
            val doc = firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("customerPoints")
                .document(phoneNumber)
                .get()
                .await()

            if (doc.exists()) {
                val data = doc.data ?: return null
                val points = CustomerPoints.fromMap(data)

                // 캐시 저장
                cache[phoneNumber] = CachedPoints(points, System.currentTimeMillis())
                Log.d(TAG, "Firestore에서 포인트 조회 및 캐시 저장: $phoneNumber")

                points
            } else {
                Log.d(TAG, "포인트 정보 없음: $phoneNumber")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "포인트 조회 실패: $phoneNumber", e)
            null
        }
    }

    override suspend fun updateCustomerPoints(
        phoneNumber: String,
        points: CustomerPoints
    ): Boolean {
        return try {
            val (regionId, officeId) = getDriverLocationInfo()

            firestore
                .collection("regions").document(regionId)
                .collection("offices").document(officeId)
                .collection("customerPoints")
                .document(phoneNumber)
                .set(points.toMap())
                .await()

            // 캐시 업데이트
            cache[phoneNumber] = CachedPoints(points, System.currentTimeMillis())
            Log.d(TAG, "포인트 업데이트 완료: $phoneNumber")

            true
        } catch (e: Exception) {
            Log.e(TAG, "포인트 업데이트 실패: $phoneNumber", e)
            false
        }
    }

    override suspend fun createCustomerPoints(phoneNumber: String): CustomerPoints {
        val (regionId, officeId) = getDriverLocationInfo()

        val newPoints = CustomerPoints(
            customerId = phoneNumber,
            phoneNumber = phoneNumber,
            currentPoints = 0,
            totalEarned = 0,
            totalUsed = 0,
            grade = "BRONZE",
            totalCalls = 0,
            lastUpdated = FieldValue.serverTimestamp(),
            createdAt = FieldValue.serverTimestamp()
        )

        firestore
            .collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("customerPoints")
            .document(phoneNumber)
            .set(newPoints.toMap())
            .await()

        // 캐시 저장
        cache[phoneNumber] = CachedPoints(newPoints, System.currentTimeMillis())
        Log.d(TAG, "신규 포인트 생성: $phoneNumber")

        return newPoints
    }

    override fun clearCache() {
        cache.clear()
        Log.d(TAG, "전체 캐시 초기화")
    }

    override fun invalidateCache(phoneNumber: String) {
        cache.remove(phoneNumber)
        Log.d(TAG, "캐시 무효화: $phoneNumber")
    }

    /**
     * SharedPreferences에서 드라이버 위치 정보 가져오기
     */
    private fun getDriverLocationInfo(): Pair<String, String> {
        val regionId = sharedPreferences.getString("regionId", "") ?: ""
        val officeId = sharedPreferences.getString("officeId", "") ?: ""

        if (regionId.isBlank() || officeId.isBlank()) {
            throw IllegalStateException("Driver location info not found")
        }

        return Pair(regionId, officeId)
    }
}

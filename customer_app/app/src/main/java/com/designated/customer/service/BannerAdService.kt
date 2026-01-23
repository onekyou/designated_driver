package com.designated.customer.service

import android.util.Log
import com.designated.customer.data.model.BannerAdData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * 배너 광고 서비스
 * Firebase Firestore에서 배너 광고 정보를 가져오는 서비스
 */
class BannerAdService(
    private val provinceId: String,
    private val cityId: String,
    private val officeId: String
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val TAG = "BannerAdService"

    /**
     * 활성화된 배너 광고 목록을 가져옵니다 (일회성)
     */
    suspend fun getActiveBanners(): List<BannerAdData> {
        Log.d(TAG, "getActiveBanners 시작: provinceId=$provinceId, cityId=$cityId, officeId=$officeId")
        return try {
            val snapshot = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("bannerAds")
                .get()
                .await()

            val banners = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { BannerAdData.fromMap(it) }
            }.filter { it.isActive }
              .sortedByDescending { it.priority }

            Log.d(TAG, "getActiveBanners 완료: ${banners.size}개의 배너 조회됨")
            banners
        } catch (e: Exception) {
            Log.e(TAG, "getActiveBanners 오류", e)
            emptyList()
        }
    }

    /**
     * 활성화된 배너 광고를 실시간으로 모니터링합니다
     */
    fun observeActiveBanners(): Flow<List<BannerAdData>> = callbackFlow {
        Log.d(TAG, "observeActiveBanners 시작")

        val listener = firestore
            .collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("bannerAds")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "observeActiveBanners 오류", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }

                val banners = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { BannerAdData.fromMap(it) }
                }?.filter { it.isActive }
                  ?.sortedByDescending { it.priority }
                  ?: emptyList()

                Log.d(TAG, "observeActiveBanners 업데이트: ${banners.size}개의 배너")
                trySend(banners)
            }

        awaitClose {
            Log.d(TAG, "observeActiveBanners 종료")
            listener.remove()
        }
    }

    /**
     * 특정 배너 광고를 가져옵니다
     */
    suspend fun getBannerById(bannerId: String): BannerAdData? {
        Log.d(TAG, "getBannerById 시작: bannerId=$bannerId")
        return try {
            val snapshot = firestore
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("bannerAds")
                .document(bannerId)
                .get()
                .await()

            snapshot.data?.let { BannerAdData.fromMap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "getBannerById 오류", e)
            null
        }
    }
}

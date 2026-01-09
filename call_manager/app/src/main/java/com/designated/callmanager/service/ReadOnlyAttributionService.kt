package com.designated.callmanager.service

import android.util.Log
import com.designated.callmanager.data.CustomerInfo
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * 일반관리자용 읽기 전용 어트리뷰션 서비스
 * 매칭 결과 조회만 가능 (임계값 설정, 매칭 처리 등 고급 기능 제외)
 */
class ReadOnlyAttributionService {
    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "ReadOnlyAttributionService"

    /**
     * 사무실 어트리뷰션 통계 조회 (읽기 전용)
     */
    suspend fun getAttributionStats(
        provinceId: String,
        cityId: String,
        officeId: String
    ): AttributionStatsResult {
        return try {
            // 고객 데이터 조회
            val customerSnapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .get()
                .await()

            val customers = customerSnapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)
            }

            // 현재 임계값 조회
            val threshold = getAttributionThreshold(provinceId, cityId, officeId)

            // 통계 계산
            val totalAttributions = customers.size
            val highScoreAttributions = customers.count { customer ->
                (customer.attributionScore ?: 0) >= threshold
            }
            val averageScore = if (customers.isNotEmpty()) {
                customers.mapNotNull { it.attributionScore }.average()
            } else 0.0

            // 소스별 분포
            val sourceDistribution = customers.groupBy { it.attributionSource ?: "unknown" }
                .mapValues { it.value.size }

            val stats = AttributionStats(
                totalAttributions = totalAttributions,
                highScoreAttributions = highScoreAttributions,
                averageScore = averageScore,
                threshold = threshold,
                sourceDistribution = sourceDistribution,
                successRate = if (totalAttributions > 0) {
                    (highScoreAttributions.toDouble() / totalAttributions * 100)
                } else 0.0
            )

            Log.d(TAG, "어트리뷰션 통계 조회 완료: 총 ${totalAttributions}건, 성공 ${highScoreAttributions}건")
            AttributionStatsResult.Success(stats)

        } catch (e: Exception) {
            Log.e(TAG, "어트리뷰션 통계 조회 실패", e)
            AttributionStatsResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 최근 어트리뷰션 매칭 결과 조회
     */
    suspend fun getRecentAttributions(
        provinceId: String,
        cityId: String,
        officeId: String,
        limit: Int = 20
    ): RecentAttributionsResult {
        return try {
            val snapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .whereNotEqualTo("attributionScore", null)
                .orderBy("registeredAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val attributions = snapshot.documents.mapNotNull { doc ->
                val customer = doc.toObject(CustomerInfo::class.java)?.copy(id = doc.id)
                customer?.let {
                    AttributionMatch(
                        customerId = it.id,
                        customerPhone = it.phoneNumber,
                        customerName = it.name,
                        attributionScore = it.attributionScore ?: 0,
                        attributionSource = it.attributionSource ?: "unknown",
                        matchedAt = it.registeredAt,
                        isHighScore = (it.attributionScore ?: 0) >= getAttributionThreshold(provinceId, cityId, officeId),
                        referralDriverId = it.referralDriverId,
                        referralDriverName = it.referralDriverName
                    )
                }
            }

            RecentAttributionsResult.Success(attributions)

        } catch (e: Exception) {
            Log.e(TAG, "최근 어트리뷰션 조회 실패", e)
            RecentAttributionsResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 소스별 어트리뷰션 분석
     */
    suspend fun getAttributionBySource(
        provinceId: String,
        cityId: String,
        officeId: String,
        source: String? = null
    ): SourceAttributionResult {
        return try {
            var query = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .whereNotEqualTo("attributionScore", null)

            // 특정 소스 필터링
            if (!source.isNullOrBlank()) {
                query = query.whereEqualTo("attributionSource", source)
            }

            val snapshot = query.get().await()
            val customers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)
            }

            val threshold = getAttributionThreshold(provinceId, cityId, officeId)

            // 소스별 분석
            val sourceAnalysis = customers.groupBy { it.attributionSource ?: "unknown" }
                .map { (source, customerList) ->
                    val highScoreCount = customerList.count { (it.attributionScore ?: 0) >= threshold }
                    SourceAnalysis(
                        source = source,
                        totalCount = customerList.size,
                        highScoreCount = highScoreCount,
                        averageScore = customerList.mapNotNull { it.attributionScore }.average(),
                        successRate = if (customerList.isNotEmpty()) {
                            (highScoreCount.toDouble() / customerList.size * 100)
                        } else 0.0
                    )
                }
                .sortedByDescending { it.totalCount }

            SourceAttributionResult.Success(sourceAnalysis)

        } catch (e: Exception) {
            Log.e(TAG, "소스별 어트리뷰션 분석 실패", e)
            SourceAttributionResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 일별 어트리뷰션 트렌드 조회 (최근 30일)
     */
    suspend fun getDailyAttributionTrend(
        provinceId: String,
        cityId: String,
        officeId: String
    ): DailyTrendResult {
        return try {
            val thirtyDaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -30)
            }.time

            val snapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .whereGreaterThanOrEqualTo("registeredAt", Timestamp(thirtyDaysAgo))
                .whereNotEqualTo("attributionScore", null)
                .orderBy("registeredAt", Query.Direction.ASCENDING)
                .get()
                .await()

            val customers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)
            }

            val threshold = getAttributionThreshold(provinceId, cityId, officeId)

            // 일별 그룹핑
            val dailyData = customers.groupBy { customer ->
                val calendar = Calendar.getInstance()
                calendar.time = customer.registeredAt.toDate()
                "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}-${String.format("%02d", calendar.get(Calendar.DAY_OF_MONTH))}"
            }.map { (date, dayCustomers) ->
                DailyAttributionData(
                    date = date,
                    totalMatches = dayCustomers.size,
                    successfulMatches = dayCustomers.count { (it.attributionScore ?: 0) >= threshold },
                    averageScore = dayCustomers.mapNotNull { it.attributionScore }.average()
                )
            }.sortedBy { it.date }

            DailyTrendResult.Success(dailyData)

        } catch (e: Exception) {
            Log.e(TAG, "일별 트렌드 조회 실패", e)
            DailyTrendResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 어트리뷰션 임계값 조회 (읽기 전용)
     */
    private suspend fun getAttributionThreshold(provinceId: String, cityId: String, officeId: String): Int {
        return try {
            val settingsDoc = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("settings")
                .document("attribution")
                .get()
                .await()

            settingsDoc.getLong("attributionThreshold")?.toInt() ?: 70 // 기본값

        } catch (e: Exception) {
            Log.e(TAG, "임계값 조회 실패, 기본값 사용", e)
            70 // 기본값
        }
    }

    /**
     * QR 스캔 통계 조회
     */
    suspend fun getQRScanStats(
        provinceId: String,
        cityId: String,
        officeId: String,
        periodDays: Int = 7
    ): QRScanStatsResult {
        return try {
            val endDate = Timestamp.now()
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -periodDays)
            val startDate = Timestamp(calendar.time)

            val snapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .whereEqualTo("attributionSource", "qr_scan")
                .whereGreaterThanOrEqualTo("registeredAt", startDate)
                .whereLessThanOrEqualTo("registeredAt", endDate)
                .get()
                .await()

            val qrScanCustomers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)
            }

            val stats = QRScanStats(
                totalScans = qrScanCustomers.size,
                successfulMatches = qrScanCustomers.count { (it.attributionScore ?: 0) >= getAttributionThreshold(provinceId, cityId, officeId) },
                periodDays = periodDays,
                dailyAverage = qrScanCustomers.size.toDouble() / periodDays
            )

            QRScanStatsResult.Success(stats)

        } catch (e: Exception) {
            Log.e(TAG, "QR 스캔 통계 조회 실패", e)
            QRScanStatsResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 기사별 추천 통계 조회
     */
    suspend fun getDriverReferralStats(
        provinceId: String,
        cityId: String,
        officeId: String
    ): DriverReferralStatsResult {
        return try {
            val snapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .whereNotEqualTo("referralDriverId", null)
                .get()
                .await()

            val customers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)
            }

            // 기사별로 그룹핑
            val driverStats = customers
                .filter { !it.referralDriverId.isNullOrEmpty() }
                .groupBy { it.referralDriverId!! }
                .map { (driverId, customerList) ->
                    val driverName = customerList.firstOrNull()?.referralDriverName ?: "알 수 없음"
                    DriverReferralStat(
                        driverId = driverId,
                        driverName = driverName,
                        totalReferrals = customerList.size,
                        recentReferrals = customerList.count { customer ->
                            val thirtyDaysAgo = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_MONTH, -30)
                            }.time
                            customer.registeredAt.toDate().after(thirtyDaysAgo)
                        }
                    )
                }
                .sortedByDescending { it.totalReferrals }

            Log.d(TAG, "기사별 추천 통계 조회 완료: ${driverStats.size}명")
            DriverReferralStatsResult.Success(driverStats)

        } catch (e: Exception) {
            Log.e(TAG, "기사별 추천 통계 조회 실패", e)
            DriverReferralStatsResult.Error(e.message ?: "Unknown error")
        }
    }
}

// 데이터 클래스들
data class AttributionStats(
    val totalAttributions: Int,
    val highScoreAttributions: Int,
    val averageScore: Double,
    val threshold: Int,
    val sourceDistribution: Map<String, Int>,
    val successRate: Double
)

data class AttributionMatch(
    val customerId: String,
    val customerPhone: String,
    val customerName: String,
    val attributionScore: Int,
    val attributionSource: String,
    val matchedAt: Timestamp,
    val isHighScore: Boolean,
    val referralDriverId: String? = null, // 추천한 기사 ID
    val referralDriverName: String? = null // 추천한 기사 이름
)

data class SourceAnalysis(
    val source: String,
    val totalCount: Int,
    val highScoreCount: Int,
    val averageScore: Double,
    val successRate: Double
)

data class DailyAttributionData(
    val date: String,
    val totalMatches: Int,
    val successfulMatches: Int,
    val averageScore: Double
)

data class QRScanStats(
    val totalScans: Int,
    val successfulMatches: Int,
    val periodDays: Int,
    val dailyAverage: Double
)

// 결과 클래스들
sealed class AttributionStatsResult {
    data class Success(val stats: AttributionStats) : AttributionStatsResult()
    data class Error(val message: String) : AttributionStatsResult()
}

sealed class RecentAttributionsResult {
    data class Success(val attributions: List<AttributionMatch>) : RecentAttributionsResult()
    data class Error(val message: String) : RecentAttributionsResult()
}

sealed class SourceAttributionResult {
    data class Success(val sourceAnalysis: List<SourceAnalysis>) : SourceAttributionResult()
    data class Error(val message: String) : SourceAttributionResult()
}

sealed class DailyTrendResult {
    data class Success(val dailyData: List<DailyAttributionData>) : DailyTrendResult()
    data class Error(val message: String) : DailyTrendResult()
}

sealed class QRScanStatsResult {
    data class Success(val stats: QRScanStats) : QRScanStatsResult()
    data class Error(val message: String) : QRScanStatsResult()
}

data class DriverReferralStat(
    val driverId: String,
    val driverName: String,
    val totalReferrals: Int,
    val recentReferrals: Int // 최근 30일
)

sealed class DriverReferralStatsResult {
    data class Success(val stats: List<DriverReferralStat>) : DriverReferralStatsResult()
    data class Error(val message: String) : DriverReferralStatsResult()
}
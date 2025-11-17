package com.designated.callmanager.service

import android.util.Log
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CustomerInfo
import com.designated.callmanager.data.CustomerPointTransaction
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.util.*

/**
 * 일반관리자용 기본 KPI 서비스
 * 사무실별 기본 지표만 제공 (복잡한 분석 기능 제외)
 */
class BasicKPIService {
    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "BasicKPIService"

    /**
     * 사무실 기본 KPI 조회
     * 일반관리자용 단순 지표만 제공
     */
    suspend fun getBasicKPI(
        regionId: String,
        officeId: String,
        periodDays: Int = 30 // 기본 30일
    ): BasicKPIResult {
        return try {
            val endDate = Timestamp.now()
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -periodDays)
            val startDate = Timestamp(calendar.time)

            // 병렬로 데이터 수집
            val callsTask = getCallStats(regionId, officeId, startDate, endDate)
            val customersTask = getCustomerStats(regionId, officeId)
            val pointsTask = getPointStats(regionId, officeId, startDate, endDate)

            val callStats = callsTask
            val customerStats = customersTask
            val pointStats = pointsTask

            val kpi = BasicKPI(
                // 콜 관련 지표
                totalCalls = callStats.totalCalls,
                appCalls = callStats.appCalls,
                appCallRatio = if (callStats.totalCalls > 0) {
                    (callStats.appCalls.toDouble() / callStats.totalCalls * 100)
                } else 0.0,

                // 고객 관련 지표
                totalCustomers = customerStats.totalCustomers,
                activeCustomers = customerStats.activeCustomers,
                newCustomers = customerStats.newCustomers,
                customerRetentionRate = if (customerStats.totalCustomers > 0) {
                    (customerStats.activeCustomers.toDouble() / customerStats.totalCustomers * 100)
                } else 0.0,

                // 포인트 관련 지표
                totalPointsEarned = pointStats.totalEarned,
                totalPointsUsed = pointStats.totalUsed,
                pointUsageRate = if (pointStats.totalEarned > 0) {
                    (pointStats.totalUsed.toDouble() / pointStats.totalEarned * 100)
                } else 0.0,

                // 등급별 분포
                gradeDistribution = customerStats.gradeDistribution,

                // 기간 정보
                periodDays = periodDays,
                lastUpdated = Timestamp.now()
            )

            Log.d(TAG, "기본 KPI 조회 완료: 총 콜 ${kpi.totalCalls}건, 고객 ${kpi.totalCustomers}명")
            BasicKPIResult.Success(kpi)

        } catch (e: Exception) {
            Log.e(TAG, "기본 KPI 조회 실패", e)
            BasicKPIResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 콜 통계 수집
     */
    private suspend fun getCallStats(
        regionId: String,
        officeId: String,
        startDate: Timestamp,
        endDate: Timestamp
    ): CallStats {
        return try {
            val snapshot = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("calls")
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .whereLessThanOrEqualTo("timestamp", endDate)
                .get()
                .await()

            val calls = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CallInfo::class.java)
            }

            val appCalls = calls.count { call ->
                call.createdFrom == "customer_app" || call.createdFrom == "landing"
            }

            CallStats(
                totalCalls = calls.size,
                appCalls = appCalls,
                completedCalls = calls.count { it.status == "completed" }
            )

        } catch (e: Exception) {
            Log.e(TAG, "콜 통계 수집 실패", e)
            CallStats(0, 0, 0)
        }
    }

    /**
     * 고객 통계 수집
     */
    private suspend fun getCustomerStats(
        regionId: String,
        officeId: String
    ): CustomerStatsBasic {
        return try {
            val snapshot = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .get()
                .await()

            val customers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)
            }

            val activeCustomers = customers.count { it.totalRides > 0 }

            // 최근 30일 내 신규 고객
            val thirtyDaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_MONTH, -30)
            }.time
            val newCustomers = customers.count { customer ->
                customer.registeredAt.toDate().after(thirtyDaysAgo)
            }

            val gradeDistribution = mapOf(
                "bronze" to customers.count { it.grade == "bronze" },
                "silver" to customers.count { it.grade == "silver" },
                "gold" to customers.count { it.grade == "gold" },
                "vip" to customers.count { it.grade == "vip" }
            )

            CustomerStatsBasic(
                totalCustomers = customers.size,
                activeCustomers = activeCustomers,
                newCustomers = newCustomers,
                gradeDistribution = gradeDistribution
            )

        } catch (e: Exception) {
            Log.e(TAG, "고객 통계 수집 실패", e)
            CustomerStatsBasic(0, 0, 0, emptyMap())
        }
    }

    /**
     * 포인트 통계 수집
     */
    private suspend fun getPointStats(
        regionId: String,
        officeId: String,
        startDate: Timestamp,
        endDate: Timestamp
    ): PointStats {
        return try {
            val snapshot = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("pointTransactions")
                .whereGreaterThanOrEqualTo("timestamp", startDate)
                .whereLessThanOrEqualTo("timestamp", endDate)
                .get()
                .await()

            val transactions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerPointTransaction::class.java)
            }

            val earnedTransactions = transactions.filter { it.type == "EARN" }
            val usedTransactions = transactions.filter { it.type == "USE" }

            PointStats(
                totalEarned = earnedTransactions.sumOf { it.amount },
                totalUsed = usedTransactions.sumOf { Math.abs(it.amount) }, // USE는 음수이므로 절댓값
                transactionCount = transactions.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "포인트 통계 수집 실패", e)
            PointStats(0, 0, 0)
        }
    }

    /**
     * 월별 트렌드 데이터 조회 (최근 6개월)
     */
    suspend fun getMonthlyTrend(
        regionId: String,
        officeId: String
    ): MonthlyTrendResult {
        return try {
            val trendData = mutableListOf<MonthlyData>()
            val calendar = Calendar.getInstance()

            // 최근 6개월 데이터
            for (i in 5 downTo 0) {
                calendar.time = Date()
                calendar.add(Calendar.MONTH, -i)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                val monthStart = Timestamp(calendar.time)

                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                val monthEnd = Timestamp(calendar.time)

                val monthStats = getBasicKPI(regionId, officeId,
                    calendar.getActualMaximum(Calendar.DAY_OF_MONTH))

                if (monthStats is BasicKPIResult.Success) {
                    trendData.add(
                        MonthlyData(
                            month = "${calendar.get(Calendar.YEAR)}-${String.format("%02d", calendar.get(Calendar.MONTH) + 1)}",
                            totalCalls = monthStats.kpi.totalCalls,
                            appCalls = monthStats.kpi.appCalls,
                            newCustomers = monthStats.kpi.newCustomers
                        )
                    )
                }
            }

            MonthlyTrendResult.Success(trendData)

        } catch (e: Exception) {
            Log.e(TAG, "월별 트렌드 조회 실패", e)
            MonthlyTrendResult.Error(e.message ?: "Unknown error")
        }
    }
}

// 데이터 클래스들
data class BasicKPI(
    val totalCalls: Int,
    val appCalls: Int,
    val appCallRatio: Double,
    val totalCustomers: Int,
    val activeCustomers: Int,
    val newCustomers: Int,
    val customerRetentionRate: Double,
    val totalPointsEarned: Int,
    val totalPointsUsed: Int,
    val pointUsageRate: Double,
    val gradeDistribution: Map<String, Int>,
    val periodDays: Int,
    val lastUpdated: Timestamp
)

data class CallStats(
    val totalCalls: Int,
    val appCalls: Int,
    val completedCalls: Int
)

data class CustomerStatsBasic(
    val totalCustomers: Int,
    val activeCustomers: Int,
    val newCustomers: Int,
    val gradeDistribution: Map<String, Int>
)

data class PointStats(
    val totalEarned: Int,
    val totalUsed: Int,
    val transactionCount: Int
)

data class MonthlyData(
    val month: String,
    val totalCalls: Int,
    val appCalls: Int,
    val newCustomers: Int
)

// 결과 클래스들
sealed class BasicKPIResult {
    data class Success(val kpi: BasicKPI) : BasicKPIResult()
    data class Error(val message: String) : BasicKPIResult()
}

sealed class MonthlyTrendResult {
    data class Success(val trendData: List<MonthlyData>) : MonthlyTrendResult()
    data class Error(val message: String) : MonthlyTrendResult()
}
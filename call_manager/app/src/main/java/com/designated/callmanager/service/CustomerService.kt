package com.designated.callmanager.service

import android.util.Log
import com.designated.callmanager.data.CustomerInfo
import com.designated.callmanager.data.CustomerPointTransaction
import com.designated.callmanager.data.CustomerPointPolicy
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * 일반관리자용 고객 관리 서비스
 * 사무실별 독립적인 고객 데이터 관리 (자기 사무실만 접근 가능)
 */
class CustomerService {
    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "CustomerService"

    /**
     * 사무실별 고객 목록 조회 (페이지네이션)
     * 일반관리자는 자기 사무실 고객만 조회 가능
     */
    suspend fun getCustomerList(
        regionId: String,
        officeId: String,
        limit: Int = 20,
        lastCustomerId: String? = null
    ): CustomerListResult {
        return try {
            var query = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .orderBy("registeredAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            // 페이지네이션 처리
            if (lastCustomerId != null) {
                val lastDoc = db.collection("regions")
                    .document(regionId)
                    .collection("offices")
                    .document(officeId)
                    .collection("customers")
                    .document(lastCustomerId)
                    .get()
                    .await()

                if (lastDoc.exists()) {
                    query = query.startAfter(lastDoc)
                }
            }

            val snapshot = query.get().await()
            val customers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)?.copy(id = doc.id)
            }

            Log.d(TAG, "고객 목록 조회 완료: ${customers.size}명")
            CustomerListResult.Success(customers, snapshot.documents.lastOrNull()?.id)

        } catch (e: Exception) {
            Log.e(TAG, "고객 목록 조회 실패", e)
            CustomerListResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 고객 상세 정보 조회
     * 기본 정보만 제공 (감사 로그 제외)
     */
    suspend fun getCustomerDetail(
        regionId: String,
        officeId: String,
        customerId: String
    ): CustomerDetailResult {
        return try {
            val customerDoc = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .document(customerId)
                .get()
                .await()

            if (!customerDoc.exists()) {
                return CustomerDetailResult.NotFound
            }

            val customer = customerDoc.toObject(CustomerInfo::class.java)?.copy(id = customerDoc.id)
                ?: return CustomerDetailResult.Error("데이터 변환 실패")

            // 포인트 거래 내역 조회 (최근 10건)
            val transactionSnapshot = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("pointTransactions")
                .whereEqualTo("customerId", customerId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            val transactions = transactionSnapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerPointTransaction::class.java)?.copy(id = doc.id)
            }

            CustomerDetailResult.Success(customer, transactions)

        } catch (e: Exception) {
            Log.e(TAG, "고객 상세 정보 조회 실패", e)
            CustomerDetailResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 전화번호로 고객 검색
     */
    suspend fun searchCustomerByPhone(
        regionId: String,
        officeId: String,
        phoneNumber: String
    ): CustomerSearchResult {
        return try {
            val snapshot = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .whereEqualTo("phoneNumber", phoneNumber)
                .limit(1)
                .get()
                .await()

            if (snapshot.documents.isEmpty()) {
                return CustomerSearchResult.NotFound
            }

            val customer = snapshot.documents[0].toObject(CustomerInfo::class.java)
                ?.copy(id = snapshot.documents[0].id)
                ?: return CustomerSearchResult.Error("데이터 변환 실패")

            CustomerSearchResult.Success(customer)

        } catch (e: Exception) {
            Log.e(TAG, "고객 검색 실패", e)
            CustomerSearchResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 사무실 고객 통계 조회 (읽기 전용)
     */
    suspend fun getCustomerStats(
        regionId: String,
        officeId: String
    ): CustomerStatsResult {
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

            val stats = CustomerStats(
                totalCustomers = customers.size,
                activeCustomers = customers.count { it.totalRides > 0 },
                gradeDistribution = mapOf(
                    "bronze" to customers.count { it.grade == "bronze" },
                    "silver" to customers.count { it.grade == "silver" },
                    "gold" to customers.count { it.grade == "gold" },
                    "vip" to customers.count { it.grade == "vip" }
                ),
                activityDistribution = mapOf(
                    "active" to customers.count { it.getActivityStatus() == "active" },
                    "warning" to customers.count { it.getActivityStatus() == "warning" },
                    "dormant" to customers.count { it.getActivityStatus() == "dormant" }
                ),
                averageRides = if (customers.isNotEmpty()) {
                    customers.sumOf { it.totalRides }.toDouble() / customers.size
                } else 0.0,
                totalPoints = customers.sumOf { it.points }
            )

            CustomerStatsResult.Success(stats)

        } catch (e: Exception) {
            Log.e(TAG, "고객 통계 조회 실패", e)
            CustomerStatsResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 고객 등급별 필터링
     */
    suspend fun getCustomersByGrade(
        regionId: String,
        officeId: String,
        grade: String,
        limit: Int = 20
    ): CustomerListResult {
        return try {
            // 인덱스 없이 작동하도록 정렬 제거하고 클라이언트 측에서 정렬
            val snapshot = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .whereEqualTo("grade", grade)
                .get()
                .await()

            val customers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)?.copy(id = doc.id)
            }.sortedByDescending { it.totalRides } // 클라이언트 측 정렬
             .take(limit) // limit 적용

            CustomerListResult.Success(customers, customers.lastOrNull()?.id)

        } catch (e: Exception) {
            Log.e(TAG, "등급별 고객 조회 실패", e)
            CustomerListResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 포인트 정책 조회 (읽기 전용)
     */
    suspend fun getPointPolicy(
        regionId: String,
        officeId: String
    ): PointPolicyResult {
        return try {
            val policyDoc = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("settings")
                .document("pointPolicy")
                .get()
                .await()

            val policy = if (policyDoc.exists()) {
                policyDoc.toObject(CustomerPointPolicy::class.java)
                    ?: CustomerPointPolicy() // 기본값
            } else {
                CustomerPointPolicy() // 기본값
            }

            PointPolicyResult.Success(policy)

        } catch (e: Exception) {
            Log.e(TAG, "포인트 정책 조회 실패", e)
            PointPolicyResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 휴면 회원 일괄 삭제 (90일 이상 비활성)
     */
    suspend fun deleteDormantCustomers(
        regionId: String,
        officeId: String
    ): DormantDeleteResult {
        return try {
            // 전체 고객 조회
            val snapshot = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("customers")
                .get()
                .await()

            val customers = snapshot.documents.mapNotNull { doc ->
                doc.toObject(CustomerInfo::class.java)?.copy(id = doc.id)
            }

            // 휴면 회원 필터링 (90일 이상 비활성)
            val dormantCustomers = customers.filter { it.getActivityStatus() == "dormant" }

            if (dormantCustomers.isEmpty()) {
                return DormantDeleteResult.Success(0)
            }

            // 배치 삭제
            var deletedCount = 0
            val batch = db.batch()
            val customerRef = db.collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .collection("customers")

            dormantCustomers.forEach { customer ->
                batch.delete(customerRef.document(customer.id))
                deletedCount++
            }

            batch.commit().await()
            Log.d(TAG, "휴면 회원 ${deletedCount}명 삭제 완료")

            DormantDeleteResult.Success(deletedCount)

        } catch (e: Exception) {
            Log.e(TAG, "휴면 회원 삭제 실패", e)
            DormantDeleteResult.Error(e.message ?: "Unknown error")
        }
    }
}

// 결과 클래스들
sealed class CustomerListResult {
    data class Success(val customers: List<CustomerInfo>, val lastCustomerId: String?) : CustomerListResult()
    data class Error(val message: String) : CustomerListResult()
}

sealed class CustomerDetailResult {
    data class Success(val customer: CustomerInfo, val recentTransactions: List<CustomerPointTransaction>) : CustomerDetailResult()
    object NotFound : CustomerDetailResult()
    data class Error(val message: String) : CustomerDetailResult()
}

sealed class CustomerSearchResult {
    data class Success(val customer: CustomerInfo) : CustomerSearchResult()
    object NotFound : CustomerSearchResult()
    data class Error(val message: String) : CustomerSearchResult()
}

sealed class CustomerStatsResult {
    data class Success(val stats: CustomerStats) : CustomerStatsResult()
    data class Error(val message: String) : CustomerStatsResult()
}

sealed class PointPolicyResult {
    data class Success(val policy: CustomerPointPolicy) : PointPolicyResult()
    data class Error(val message: String) : PointPolicyResult()
}

sealed class DormantDeleteResult {
    data class Success(val deletedCount: Int) : DormantDeleteResult()
    data class Error(val message: String) : DormantDeleteResult()
}

// 통계 데이터 클래스
data class CustomerStats(
    val totalCustomers: Int,
    val activeCustomers: Int,
    val gradeDistribution: Map<String, Int>,
    val activityDistribution: Map<String, Int> = emptyMap(), // 활동 상태별 분포
    val averageRides: Double,
    val totalPoints: Int
)
package com.designated.callmanager.service

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await

/**
 * 일반관리자용 기본 알림 서비스
 * 기본적인 알림 기능만 제공 (고급 KPI 경고, 분석 알림 제외)
 */
class BasicNotificationService {
    private val db: FirebaseFirestore = Firebase.firestore
    private val TAG = "BasicNotificationService"

    /**
     * 고객앱 콜 알림 생성
     */
    suspend fun createAppCallNotification(
        provinceId: String,
        cityId: String,
        officeId: String,
        callId: String,
        customerName: String?,
        customerGrade: String?
    ): NotificationResult {
        return try {
            val notificationData = hashMapOf(
                "type" to "APP_CALL",
                "title" to "고객앱 콜",
                "message" to buildAppCallMessage(customerName, customerGrade),
                "callId" to callId,
                "provinceId" to provinceId,
                "cityId" to cityId,
                "officeId" to officeId,
                "priority" to "HIGH",
                "timestamp" to Timestamp.now(),
                "isRead" to false
            )

            val docRef = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .add(notificationData)
                .await()

            Log.d(TAG, "고객앱 콜 알림 생성: ${docRef.id}")
            NotificationResult.Success(docRef.id)

        } catch (e: Exception) {
            Log.e(TAG, "고객앱 콜 알림 생성 실패", e)
            NotificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 신규 고객 등록 알림
     */
    suspend fun createNewCustomerNotification(
        provinceId: String,
        cityId: String,
        officeId: String,
        customerId: String,
        customerName: String,
        customerPhone: String,
        attributionSource: String?
    ): NotificationResult {
        return try {
            val sourceText = when (attributionSource) {
                "qr_scan" -> "QR 스캔"
                "landing" -> "랜딩페이지"
                "referral" -> "추천"
                else -> "기타"
            }

            val notificationData = hashMapOf(
                "type" to "NEW_CUSTOMER",
                "title" to "신규 고객 등록",
                "message" to "새로운 고객이 등록되었습니다\n${customerName} (${sourceText})",
                "customerId" to customerId,
                "customerPhone" to customerPhone,
                "provinceId" to provinceId,
                "cityId" to cityId,
                "officeId" to officeId,
                "priority" to "NORMAL",
                "timestamp" to Timestamp.now(),
                "isRead" to false
            )

            val docRef = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .add(notificationData)
                .await()

            Log.d(TAG, "신규 고객 등록 알림 생성: ${docRef.id}")
            NotificationResult.Success(docRef.id)

        } catch (e: Exception) {
            Log.e(TAG, "신규 고객 등록 알림 생성 실패", e)
            NotificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 포인트 적립 알림
     */
    suspend fun createPointEarnedNotification(
        provinceId: String,
        cityId: String,
        officeId: String,
        customerId: String,
        customerName: String,
        pointsEarned: Int,
        callId: String?
    ): NotificationResult {
        return try {
            val notificationData = hashMapOf(
                "type" to "POINTS_EARNED",
                "title" to "포인트 적립",
                "message" to "${customerName}님이 ${pointsEarned}P 적립하였습니다",
                "customerId" to customerId,
                "pointsEarned" to pointsEarned,
                "callId" to callId,
                "provinceId" to provinceId,
                "cityId" to cityId,
                "officeId" to officeId,
                "priority" to "LOW",
                "timestamp" to Timestamp.now(),
                "isRead" to false
            )

            val docRef = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .add(notificationData)
                .await()

            Log.d(TAG, "포인트 적립 알림 생성: ${docRef.id}")
            NotificationResult.Success(docRef.id)

        } catch (e: Exception) {
            Log.e(TAG, "포인트 적립 알림 생성 실패", e)
            NotificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 고객 등급 승급 알림
     */
    suspend fun createGradeUpNotification(
        provinceId: String,
        cityId: String,
        officeId: String,
        customerId: String,
        customerName: String,
        oldGrade: String,
        newGrade: String
    ): NotificationResult {
        return try {
            val gradeNames = mapOf(
                "bronze" to "브론즈",
                "silver" to "실버",
                "gold" to "골드",
                "vip" to "VIP"
            )

            val notificationData = hashMapOf(
                "type" to "GRADE_UP",
                "title" to "고객 등급 승급",
                "message" to "${customerName}님이 ${gradeNames[oldGrade]}에서 ${gradeNames[newGrade]}로 승급하였습니다",
                "customerId" to customerId,
                "oldGrade" to oldGrade,
                "newGrade" to newGrade,
                "provinceId" to provinceId,
                "cityId" to cityId,
                "officeId" to officeId,
                "priority" to "NORMAL",
                "timestamp" to Timestamp.now(),
                "isRead" to false
            )

            val docRef = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .add(notificationData)
                .await()

            Log.d(TAG, "등급 승급 알림 생성: ${docRef.id}")
            NotificationResult.Success(docRef.id)

        } catch (e: Exception) {
            Log.e(TAG, "등급 승급 알림 생성 실패", e)
            NotificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 일반 알림 조회 (최근 50개)
     */
    suspend fun getNotifications(
        provinceId: String,
        cityId: String,
        officeId: String,
        limit: Int = 50
    ): NotificationListResult {
        return try {
            val snapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            val notifications = snapshot.documents.mapNotNull { doc ->
                try {
                    BasicNotification(
                        id = doc.id,
                        type = doc.getString("type") ?: "",
                        title = doc.getString("title") ?: "",
                        message = doc.getString("message") ?: "",
                        priority = doc.getString("priority") ?: "NORMAL",
                        timestamp = doc.getTimestamp("timestamp") ?: Timestamp.now(),
                        isRead = doc.getBoolean("isRead") ?: false,
                        callId = doc.getString("callId"),
                        customerId = doc.getString("customerId")
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "알림 파싱 실패: ${doc.id}", e)
                    null
                }
            }

            NotificationListResult.Success(notifications)

        } catch (e: Exception) {
            Log.e(TAG, "알림 목록 조회 실패", e)
            NotificationListResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 알림 읽음 처리
     */
    suspend fun markAsRead(
        provinceId: String,
        cityId: String,
        officeId: String,
        notificationId: String
    ): NotificationResult {
        return try {
            db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .document(notificationId)
                .update("isRead", true)
                .await()

            Log.d(TAG, "알림 읽음 처리: $notificationId")
            NotificationResult.Success(notificationId)

        } catch (e: Exception) {
            Log.e(TAG, "알림 읽음 처리 실패", e)
            NotificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    suspend fun getUnreadCount(
        provinceId: String,
        cityId: String,
        officeId: String
    ): UnreadCountResult {
        return try {
            val snapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .whereEqualTo("isRead", false)
                .get()
                .await()

            UnreadCountResult.Success(snapshot.size())

        } catch (e: Exception) {
            Log.e(TAG, "읽지 않은 알림 개수 조회 실패", e)
            UnreadCountResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 오래된 알림 정리 (30일 이상)
     */
    suspend fun cleanupOldNotifications(
        provinceId: String,
        cityId: String,
        officeId: String
    ): NotificationResult {
        return try {
            val calendar = java.util.Calendar.getInstance()
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -30)
            val cutoffDate = Timestamp(calendar.time)

            val snapshot = db.collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .collection("notifications")
                .whereLessThan("timestamp", cutoffDate)
                .get()
                .await()

            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.delete(doc.reference)
            }

            batch.commit().await()

            Log.d(TAG, "오래된 알림 정리 완료: ${snapshot.size()}개")
            NotificationResult.Success("${snapshot.size()}개 정리 완료")

        } catch (e: Exception) {
            Log.e(TAG, "오래된 알림 정리 실패", e)
            NotificationResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 고객앱 콜 메시지 생성
     */
    private fun buildAppCallMessage(customerName: String?, customerGrade: String?): String {
        val gradeText = when (customerGrade) {
            "vip" -> "🌟 VIP"
            "gold" -> "🥇 골드"
            "silver" -> "🥈 실버"
            "bronze" -> "🥉 브론즈"
            else -> ""
        }

        return if (!customerName.isNullOrBlank()) {
            if (gradeText.isNotEmpty()) {
                "$gradeText $customerName 님의 콜입니다"
            } else {
                "$customerName 님의 고객앱 콜입니다"
            }
        } else {
            "고객앱에서 콜이 들어왔습니다"
        }
    }
}

// 데이터 클래스들
data class BasicNotification(
    val id: String,
    val type: String,
    val title: String,
    val message: String,
    val priority: String,
    val timestamp: Timestamp,
    val isRead: Boolean,
    val callId: String? = null,
    val customerId: String? = null
)

// 결과 클래스들
sealed class NotificationResult {
    data class Success(val notificationId: String) : NotificationResult()
    data class Error(val message: String) : NotificationResult()
}

sealed class NotificationListResult {
    data class Success(val notifications: List<BasicNotification>) : NotificationListResult()
    data class Error(val message: String) : NotificationListResult()
}

sealed class UnreadCountResult {
    data class Success(val count: Int) : UnreadCountResult()
    data class Error(val message: String) : UnreadCountResult()
}
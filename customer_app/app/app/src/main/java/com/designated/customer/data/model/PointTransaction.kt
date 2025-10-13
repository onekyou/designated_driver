package com.designated.customer.data.model

import com.google.firebase.Timestamp

/**
 * 포인트 거래 내역 데이터 모델
 */
data class PointTransaction(
    val id: String = "",
    val customerId: String = "",
    val type: TransactionType = TransactionType.EARN,
    val amount: Int = 0,
    val balance: Int = 0,  // 거래 후 잔액
    val description: String = "",
    val callId: String? = null,  // 관련 콜 ID
    val timestamp: Timestamp = Timestamp.now(),
    val fare: Int? = null,  // 운행 요금 (적립 시)
    val grade: String = "BRONZE"  // 거래 시점의 등급
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "customerId" to customerId,
            "type" to type.name,
            "amount" to amount,
            "balance" to balance,
            "description" to description,
            "callId" to callId,
            "timestamp" to timestamp,
            "fare" to fare,
            "grade" to grade
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): PointTransaction {
            return PointTransaction(
                id = data["id"] as? String ?: "",
                customerId = data["customerId"] as? String ?: "",
                type = TransactionType.valueOf(data["type"] as? String ?: "EARN"),
                amount = (data["amount"] as? Long)?.toInt() ?: 0,
                balance = (data["balance"] as? Long)?.toInt() ?: 0,
                description = data["description"] as? String ?: "",
                callId = data["callId"] as? String,
                timestamp = data["timestamp"] as? Timestamp ?: Timestamp.now(),
                fare = (data["fare"] as? Long)?.toInt(),
                grade = data["grade"] as? String ?: "BRONZE"
            )
        }
    }
}

/**
 * 거래 유형
 */
enum class TransactionType {
    EARN,     // 적립
    USE,      // 사용
    EXPIRE,   // 만료
    CANCEL,   // 취소
    ADMIN     // 관리자 조정
}
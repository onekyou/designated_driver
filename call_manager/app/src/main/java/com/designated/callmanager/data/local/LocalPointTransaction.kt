package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * 포인트 거래 내역 로컬 저장용 Entity
 * Firebase PointTransaction과 동기화됨
 */
@Entity(
    tableName = "point_transactions",
    indices = [
        Index(value = ["regionId", "officeId", "timestamp"]),
        Index(value = ["type"]),
        Index(value = ["synced"]),
        Index(value = ["relatedSharedCallId"])
    ]
)
data class LocalPointTransaction(
    @PrimaryKey
    val id: String,

    // Firebase 동기화 필드
    val type: String, // "CHARGE", "SHARED_CALL_SEND", "SHARED_CALL_RECEIVE", "ADMIN_CHARGE", "ADMIN_EXCHANGE", "ADJUSTMENT"
    val amount: Int,
    val description: String,
    val timestamp: Long, // Timestamp를 Long으로 저장 (밀리초)
    val createdBy: String,

    // 사무실 정보
    val regionId: String,
    val officeId: String,

    // 공유콜 관련
    val relatedSharedCallId: String? = null,

    // 로컬 관리 필드
    val synced: Boolean = true, // Firebase와 동기화 여부
    val lastUpdated: Long = System.currentTimeMillis(), // 마지막 업데이트 시간
    val localOnly: Boolean = false // 로컬에서만 생성된 임시 거래인지 여부
) {
    companion object {
        /**
         * Firebase PointTransaction을 LocalPointTransaction으로 변환
         */
        fun fromFirebaseTransaction(
            firebaseTransaction: com.designated.callmanager.data.PointTransaction,
            regionId: String,
            officeId: String
        ): LocalPointTransaction {
            return LocalPointTransaction(
                id = firebaseTransaction.id,
                type = firebaseTransaction.type,
                amount = firebaseTransaction.amount,
                description = firebaseTransaction.description,
                timestamp = firebaseTransaction.timestamp?.toDate()?.time ?: System.currentTimeMillis(),
                createdBy = "system", // Firebase PointTransaction에 createdBy 필드가 없음
                regionId = regionId,
                officeId = officeId,
                relatedSharedCallId = firebaseTransaction.relatedSharedCallId,
                synced = true,
                lastUpdated = System.currentTimeMillis(),
                localOnly = false
            )
        }
    }

    /**
     * LocalPointTransaction을 Firebase PointTransaction으로 변환
     */
    fun toFirebaseTransaction(): com.designated.callmanager.data.PointTransaction {
        return com.designated.callmanager.data.PointTransaction(
            id = this.id,
            type = this.type,
            amount = this.amount,
            description = this.description,
            timestamp = com.google.firebase.Timestamp(java.util.Date(this.timestamp)),
            regionId = this.regionId,
            officeId = this.officeId,
            relatedSharedCallId = this.relatedSharedCallId
        )
    }
}
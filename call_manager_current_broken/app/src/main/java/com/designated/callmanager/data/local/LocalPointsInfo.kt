package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 포인트 잔액 정보 로컬 저장용 Entity
 * Firebase PointsInfo와 동기화됨
 */
@Entity(tableName = "points_info")
data class LocalPointsInfo(
    @PrimaryKey
    val id: String, // "regionId_officeId" 형식

    // Firebase 동기화 필드
    val balance: Int,
    val updatedAt: Long, // Timestamp를 Long으로 저장

    // 사무실 정보
    val regionId: String,
    val officeId: String,

    // 로컬 관리 필드
    val synced: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * regionId와 officeId로 고유 ID 생성
         */
        fun generateId(regionId: String, officeId: String): String {
            return "${regionId}_${officeId}"
        }

        /**
         * Firebase PointsInfo를 LocalPointsInfo로 변환
         */
        fun fromFirebasePointsInfo(
            firebasePointsInfo: com.designated.callmanager.data.PointsInfo,
            regionId: String,
            officeId: String
        ): LocalPointsInfo {
            return LocalPointsInfo(
                id = generateId(regionId, officeId),
                balance = firebasePointsInfo.balance,
                updatedAt = firebasePointsInfo.updatedAt?.toDate()?.time ?: System.currentTimeMillis(),
                regionId = regionId,
                officeId = officeId,
                synced = true,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    /**
     * LocalPointsInfo를 Firebase PointsInfo로 변환
     */
    fun toFirebasePointsInfo(): com.designated.callmanager.data.PointsInfo {
        return com.designated.callmanager.data.PointsInfo(
            balance = this.balance,
            updatedAt = com.google.firebase.Timestamp(java.util.Date(this.updatedAt))
        )
    }
}
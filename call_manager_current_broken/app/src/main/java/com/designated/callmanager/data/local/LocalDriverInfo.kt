package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.callmanager.data.DriverInfo
import com.google.firebase.Timestamp

/**
 * 기사 정보 로컬 저장용 Entity
 * Firebase DriverInfo와 동기화됨
 */
@Entity(tableName = "drivers")
data class LocalDriverInfo(
    @PrimaryKey
    val id: String,

    // 기본 정보
    val authUid: String?,
    val name: String,
    val phoneNumber: String,
    val email: String?,
    val driverType: String?,

    // 상태 정보
    val status: String,
    val approvalStatus: String,

    // 타임스탬프
    val createdAt: Long?,
    val updatedAt: Long?,
    val approvedAt: Long?,

    // 소속 정보
    val regionId: String?,
    val officeId: String?,

    // 추천 QR URL
    val referralQrUrl: String?,

    // 로컬 관리 필드
    val synced: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Firebase DriverInfo를 LocalDriverInfo로 변환
         */
        fun fromFirebaseDriverInfo(driverInfo: DriverInfo): LocalDriverInfo {
            return LocalDriverInfo(
                id = driverInfo.id,
                authUid = driverInfo.authUid,
                name = driverInfo.name,
                phoneNumber = driverInfo.phoneNumber,
                email = driverInfo.email,
                driverType = driverInfo.driverType,
                status = driverInfo.status,
                approvalStatus = driverInfo.approvalStatus,
                createdAt = driverInfo.createdAt?.toDate()?.time,
                updatedAt = driverInfo.updatedAt?.toDate()?.time,
                approvedAt = driverInfo.approvedAt?.toDate()?.time,
                regionId = driverInfo.regionId,
                officeId = driverInfo.officeId,
                referralQrUrl = driverInfo.referralQrUrl,
                synced = true,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    /**
     * LocalDriverInfo를 Firebase DriverInfo로 변환
     */
    fun toFirebaseDriverInfo(): DriverInfo {
        return DriverInfo(
            id = this.id,
            authUid = this.authUid,
            name = this.name,
            phoneNumber = this.phoneNumber,
            email = this.email,
            driverType = this.driverType,
            status = this.status,
            approvalStatus = this.approvalStatus,
            createdAt = this.createdAt?.let { Timestamp(java.util.Date(it)) },
            updatedAt = this.updatedAt?.let { Timestamp(java.util.Date(it)) },
            approvedAt = this.approvedAt?.let { Timestamp(java.util.Date(it)) },
            regionId = this.regionId,
            officeId = this.officeId,
            referralQrUrl = this.referralQrUrl
        )
    }
}

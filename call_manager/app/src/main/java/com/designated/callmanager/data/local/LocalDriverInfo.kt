package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.callmanager.data.DriverInfo
import com.google.firebase.Timestamp

/**
 * Room Database용 기사 정보 Entity
 *
 * Firebase Firestore의 DriverInfo를 로컬 DB에 저장
 * FCM을 통해 실시간 동기화
 */
@Entity(tableName = "drivers")
data class LocalDriverInfo(
    @PrimaryKey
    val id: String,

    // 기본 정보
    val name: String,
    val phoneNumber: String,
    val authUid: String?,
    val status: String,  // "WAITING", "ASSIGNED", "ON_TRIP", "OFFLINE"

    // 타임스탬프
    val createdAt: Long?,
    val updatedAt: Long?,
    val lastLoginTime: Long?,

    // 사무실 정보
    val regionId: String,
    val officeId: String,

    // 동기화 메타데이터
    val synced: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 확장 함수: LocalDriverInfo → DriverInfo 변환
 */
fun LocalDriverInfo.toDriverInfo(): DriverInfo {
    return DriverInfo(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        authUid = authUid,
        status = status,
        createdAt = createdAt?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) },
        updatedAt = updatedAt?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) },
        lastLoginTime = lastLoginTime?.let { Timestamp(it / 1000, ((it % 1000) * 1000000).toInt()) }
    )
}

/**
 * 확장 함수: DriverInfo → LocalDriverInfo 변환
 */
fun DriverInfo.toLocalDriverInfo(regionId: String, officeId: String): LocalDriverInfo {
    return LocalDriverInfo(
        id = id,
        name = name,
        phoneNumber = phoneNumber,
        authUid = authUid,
        status = status,
        createdAt = createdAt?.seconds?.times(1000),
        updatedAt = updatedAt?.seconds?.times(1000),
        lastLoginTime = lastLoginTime?.seconds?.times(1000),
        regionId = regionId,
        officeId = officeId,
        synced = true,
        lastUpdated = System.currentTimeMillis()
    )
}

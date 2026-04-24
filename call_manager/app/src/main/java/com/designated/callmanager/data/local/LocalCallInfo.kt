package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import com.google.firebase.Timestamp

/**
 * Room Database용 콜 정보 Entity
 *
 * Firebase Firestore의 CallInfo를 로컬 DB에 저장
 * FCM을 통해 실시간 동기화
 */
@Entity(tableName = "calls")
data class LocalCallInfo(
    @PrimaryKey
    val id: String,

    // 기본 정보
    val phoneNumber: String,
    val customerName: String?,
    val customerAddress: String?,
    val status: String,
    val timestamp: Long,

    // 운행 정보 (_set 접미사: 기사가 입력한 값)
    val departure_set: String?,
    val destination_set: String?,
    val waypoints_set: String?,
    val fare_set: Long?,

    // 배차 정보
    val assignedDriverId: String?,
    val assignedDriverName: String?,
    val assignedDriverPhone: String?,

    // 콜 타입
    val callType: String?,  // "REGULAR", "SHARED", etc.
    val fromCallDetector: Boolean?,
    val fromCallManager: Boolean? = null,

    // 배차 임시 메모 (매니저 STT/키보드 입력) — 일반전화 콜 전용
    val memoText: String? = null,

    // 사무실 정보
    val regionId: String,
    val officeId: String,

    // 동기화 메타데이터
    val synced: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * 확장 함수: LocalCallInfo → CallInfo 변환
 */
fun LocalCallInfo.toCallInfo(): CallInfo {
    return CallInfo(
        id = id,
        phoneNumber = phoneNumber,
        customerName = customerName,
        customerAddress = customerAddress,
        status = status,
        timestamp = Timestamp(timestamp / 1000, ((timestamp % 1000) * 1000000).toInt()),
        departure_set = departure_set,
        destination_set = destination_set,
        waypoints_set = waypoints_set,
        fare_set = fare_set,
        assignedDriverId = assignedDriverId,
        assignedDriverName = assignedDriverName,
        assignedDriverPhone = assignedDriverPhone,
        callType = callType,
        fromCallDetector = fromCallDetector,
        fromCallManager = fromCallManager,
        memoText = memoText
    )
}

/**
 * 확장 함수: CallInfo → LocalCallInfo 변환
 */
fun CallInfo.toLocalCallInfo(regionId: String, officeId: String): LocalCallInfo {
    return LocalCallInfo(
        id = id,
        phoneNumber = phoneNumber,
        customerName = customerName,
        customerAddress = customerAddress,
        status = status,
        timestamp = timestamp.seconds.times(1000),
        departure_set = departure_set,
        destination_set = destination_set,
        waypoints_set = waypoints_set,
        fare_set = fare_set,
        assignedDriverId = assignedDriverId,
        assignedDriverName = assignedDriverName,
        assignedDriverPhone = assignedDriverPhone,
        callType = callType,
        fromCallDetector = fromCallDetector,
        fromCallManager = fromCallManager,
        memoText = memoText,
        regionId = regionId,
        officeId = officeId,
        synced = true,
        lastUpdated = System.currentTimeMillis()
    )
}

package com.designated.callmanager.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

// 기사 승인 상태 Enum (추가)
enum class DriverApprovalStatus {
    PENDING,    // 승인 대기
    APPROVED,   // 승인됨
    REJECTED    // 거부됨
}

/**
 * Firestore의 기사 정보를 나타내는 데이터 클래스 (Firestore 구조 기준)
 */
data class DriverInfo(
    @PropertyName("id") var id: String = "", // Firestore 문서 ID
    @PropertyName("authUid") var authUid: String? = null, // Firestore 'authUid' 필드 매핑 (로그 기준)
    @PropertyName("name") var name: String = "",
    @PropertyName("status") var status: String = DriverStatus.OFFLINE.value, // 기사 상태 (enum 사용 권장)
    @PropertyName("phoneNumber") var phoneNumber: String = "",
    @PropertyName("createdAt") var createdAt: Timestamp? = null, // Nullable로 변경 (Firestore에 없을 경우 대비)
    @PropertyName("updatedAt") var updatedAt: Timestamp? = null, // Firestore 'updatedAt' 필드 매핑 (updateAt -> updatedAt)
    @PropertyName("regionId") var regionId: String? = null, // Firestore 'regionId' 필드 매핑 (regionID -> regionId)
    @PropertyName("officeId") var officeId: String? = null, // Firestore 'officeId' 필드 매핑 (officeID -> officeId)
    @PropertyName("email") var email: String? = null,
    @PropertyName("driverType") var driverType: String? = null,
    @PropertyName("approvedAt") var approvedAt: Timestamp? = null,
    @PropertyName("approvalStatus") var approvalStatus: DriverApprovalStatus = DriverApprovalStatus.PENDING // 기사 가입 승인 상태 추가
    // --- 제거된 필드 --- 
    // type: String
    // phone: String?
    // location: GeoPoint?
    // rating: Double
    // totalTrips: Long
    // isAvailable: Boolean
    // currentCallId: String?
    // lastLocationUpdate: Timestamp?
    // fcmToken: String?
) {
    // Firestore에서 객체 매핑 시 빈 생성자 필요 - 모든 필드에 기본값을 제공하여 명시적 빈 생성자의 복잡성을 줄임
    constructor() : this(
        id = "",
        authUid = null,
        name = "",
        status = DriverStatus.OFFLINE.value,
        phoneNumber = "",
        createdAt = null,
        updatedAt = null,
        regionId = null,
        officeId = null,
        email = null,
        driverType = null,
        approvedAt = null,
        approvalStatus = DriverApprovalStatus.PENDING
    )
}

// DriverType Enum 제거
/*
enum class DriverType(val value: String) {
    DESIGNATED("대리"),
    PICKUP("픽업")
}
*/

/**
 * 기사 상태를 나타내는 Enum Class
 */
enum class DriverStatus(val value: String) {
    WAITING("waiting"),     // 대기중 (기존 WAITING("대기중")과 ONLINE("온라인")을 통합)
    ON_TRIP("on_trip"),     // 운행중
    PREPARING("preparing"), // 운행준비중
    OFFLINE("offline");      // 오프라인

    companion object {
        fun fromString(value: String?): DriverStatus {
            return entries.find { it.value.equals(value, ignoreCase = true) } ?: OFFLINE
        }
    }
} 
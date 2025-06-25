package com.designated.callmanager.data

import com.designated.callmanager.data.Constants
import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName

/**
 * Firestore의 기사 정보를 나타내는 데이터 클래스 (Firestore 구조 기준)
 */
data class DriverInfo(
    @PropertyName("id") var id: String = "", // Firestore 문서 ID
    @PropertyName("authUid") var authUid: String? = null, // Firestore 'authUid' 필드 매핑 (로그 기준)
    @PropertyName("name") var name: String = "",
    @PropertyName("status") var status: String = Constants.DRIVER_STATUS_OFFLINE, // enum 대신 상수 사용
    @PropertyName("phoneNumber") var phoneNumber: String = "",
    @PropertyName("createdAt") var createdAt: Timestamp? = null, // Nullable로 변경 (Firestore에 없을 경우 대비)
    @PropertyName("updatedAt") var updatedAt: Timestamp? = null, // Firestore 'updatedAt' 필드 매핑 (updateAt -> updatedAt)
    @PropertyName("regionId") var regionId: String? = null, // Firestore 'regionId' 필드 매핑 (regionID -> regionId)
    @PropertyName("officeId") var officeId: String? = null, // Firestore 'officeId' 필드 매핑 (officeID -> officeId)
    @PropertyName("email") var email: String? = null,
    @PropertyName("driverType") var driverType: String? = null,
    @PropertyName("approvedAt") var approvedAt: Timestamp? = null,
    @PropertyName("approvalStatus") var approvalStatus: String = Constants.APPROVAL_STATUS_PENDING // String 타입 및 상수 사용
) {
    // Firestore에서 객체 매핑 시 빈 생성자 필요 - 모든 필드에 기본값을 제공하여 명시적 빈 생성자의 복잡성을 줄임
    constructor() : this(
        id = "",
        authUid = null,
        name = "",
        status = Constants.DRIVER_STATUS_OFFLINE, // enum 대신 상수 사용
        phoneNumber = "",
        createdAt = null,
        updatedAt = null,
        regionId = null,
        officeId = null,
        email = null,
        driverType = null,
        approvedAt = null,
        approvalStatus = Constants.APPROVAL_STATUS_PENDING // String 타입 및 상수 사용
    )
}
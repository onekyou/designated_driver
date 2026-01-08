package com.designated.callmanager.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.designated.callmanager.data.CallInfo
import com.google.firebase.Timestamp

/**
 * 콜 정보 로컬 저장용 Entity
 * Firebase CallInfo와 동기화됨
 */
@Entity(tableName = "calls")
data class LocalCallInfo(
    @PrimaryKey
    val id: String,

    // 기본 정보
    val phoneNumber: String,
    val customerAddress: String?,
    val customerName: String?,
    val address: String?,

    // 상태 정보
    val status: String,
    val timestamp: Long,
    val detectedTimestamp: Long?,
    val timestampClient: Long?,

    // 배차 정보
    val assignedDriverId: String?,
    val assignedDriverName: String?,
    val assignedDriverPhone: String?,
    val assignedTimestamp: Long?,

    // 운행 정보
    val departure: String?,
    val destination: String?,
    val departure_set: String?,
    val destination_set: String?,
    val waypoints_set: String?,
    val fare: Long?,
    val fare_set: Long?,
    val cashAmount: Long?,
    val paymentMethod: String?,

    // 기타 정보
    val callType: String?,
    val deviceName: String?,
    val trip_summary: String?,
    val cancelReason: String?,
    val isSummaryConfirmed: Boolean?,
    val summaryConfirmedTimestamp: Long?,

    // 소속 정보
    val regionId: String?,
    val officeId: String?,

    // 콜 생성 출처
    val fromCallDetector: Boolean?,
    val fromCallManager: Boolean?,

    // 손님앱 관련
    val customerId: String?,
    val customerGrade: String?,
    val createdFrom: String?,
    val pointsEarned: Int?,
    val pointsUsed: Int?,
    val isAppCustomer: Boolean?,
    val attributionScore: Int?,
    val attributionSource: String?,

    // 로컬 관리 필드
    val synced: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Firebase CallInfo를 LocalCallInfo로 변환
         */
        fun fromFirebaseCallInfo(callInfo: CallInfo): LocalCallInfo {
            return LocalCallInfo(
                id = callInfo.id,
                phoneNumber = callInfo.phoneNumber,
                customerAddress = callInfo.customerAddress,
                customerName = callInfo.customerName,
                address = callInfo.address,
                status = callInfo.status,
                timestamp = callInfo.timestamp.toDate().time,
                detectedTimestamp = callInfo.detectedTimestamp?.toDate()?.time,
                timestampClient = callInfo.timestampClient,
                assignedDriverId = callInfo.assignedDriverId,
                assignedDriverName = callInfo.assignedDriverName,
                assignedDriverPhone = callInfo.assignedDriverPhone,
                assignedTimestamp = callInfo.assignedTimestamp?.toDate()?.time,
                departure = callInfo.departure,
                destination = callInfo.destination,
                departure_set = callInfo.departure_set,
                destination_set = callInfo.destination_set,
                waypoints_set = callInfo.waypoints_set,
                fare = callInfo.fare,
                fare_set = callInfo.fare_set,
                cashAmount = callInfo.cashAmount,
                paymentMethod = callInfo.paymentMethod,
                callType = callInfo.callType,
                deviceName = callInfo.deviceName,
                trip_summary = callInfo.trip_summary,
                cancelReason = callInfo.cancelReason,
                isSummaryConfirmed = callInfo.isSummaryConfirmed,
                summaryConfirmedTimestamp = callInfo.summaryConfirmedTimestamp?.toDate()?.time,
                regionId = callInfo.regionId,
                officeId = callInfo.officeId,
                fromCallDetector = callInfo.fromCallDetector,
                fromCallManager = callInfo.fromCallManager,
                customerId = callInfo.customerId,
                customerGrade = callInfo.customerGrade,
                createdFrom = callInfo.createdFrom,
                pointsEarned = callInfo.pointsEarned,
                pointsUsed = callInfo.pointsUsed,
                isAppCustomer = callInfo.isAppCustomer,
                attributionScore = callInfo.attributionScore,
                attributionSource = callInfo.attributionSource,
                synced = true,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    /**
     * LocalCallInfo를 Firebase CallInfo로 변환
     */
    fun toFirebaseCallInfo(): CallInfo {
        return CallInfo(
            id = this.id,
            phoneNumber = this.phoneNumber,
            customerAddress = this.customerAddress,
            customerName = this.customerName,
            address = this.address,
            status = this.status,
            timestamp = Timestamp(java.util.Date(this.timestamp)),
            detectedTimestamp = this.detectedTimestamp?.let { Timestamp(java.util.Date(it)) },
            timestampClient = this.timestampClient,
            assignedDriverId = this.assignedDriverId,
            assignedDriverName = this.assignedDriverName,
            assignedDriverPhone = this.assignedDriverPhone,
            assignedTimestamp = this.assignedTimestamp?.let { Timestamp(java.util.Date(it)) },
            departure = this.departure,
            destination = this.destination,
            departure_set = this.departure_set,
            destination_set = this.destination_set,
            waypoints_set = this.waypoints_set,
            fare = this.fare,
            fare_set = this.fare_set,
            cashAmount = this.cashAmount,
            paymentMethod = this.paymentMethod,
            callType = this.callType,
            deviceName = this.deviceName,
            trip_summary = this.trip_summary,
            cancelReason = this.cancelReason,
            isSummaryConfirmed = this.isSummaryConfirmed,
            summaryConfirmedTimestamp = this.summaryConfirmedTimestamp?.let { Timestamp(java.util.Date(it)) },
            regionId = this.regionId,
            officeId = this.officeId,
            fromCallDetector = this.fromCallDetector,
            fromCallManager = this.fromCallManager,
            customerId = this.customerId,
            customerGrade = this.customerGrade,
            createdFrom = this.createdFrom,
            pointsEarned = this.pointsEarned,
            pointsUsed = this.pointsUsed,
            isAppCustomer = this.isAppCustomer,
            attributionScore = this.attributionScore,
            attributionSource = this.attributionSource
        )
    }
}

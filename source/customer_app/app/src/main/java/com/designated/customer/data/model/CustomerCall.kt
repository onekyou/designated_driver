package com.designated.customer.data.model

import com.google.firebase.Timestamp

data class CustomerCall(
    val id: String = "",
    val phoneNumber: String,
    val officeId: String,
    val regionId: String = "seoul", // 콜매니저 호환
    val currentLocation: String,
    val destinationLocation: String,
    val timestamp: Long,
    val status: String, // REQUESTED, ASSIGNED, DRIVER_ARRIVING, IN_PROGRESS, COMPLETED, CANCELLED
    val driverId: String? = null,
    val estimatedArrivalTime: Int? = null, // minutes
    val fare: Int? = null,
    val notes: String? = null,
    // 콜매니저 호환 필드들
    val createdFrom: String = "customer_app",
    val customerId: String? = null,
    val customerName: String? = null,
    val customerGrade: String? = "bronze", // bronze/silver/gold/vip
    val isAppCustomer: Boolean = true,
    val pointsUsed: Int = 0, // 사용된 포인트
    val finalFare: Int? = null, // 최종 결제 요금 (할인 적용 후)
    val discountAmount: Int = 0 // 할인 금액
) {
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "phoneNumber" to phoneNumber,
            "officeId" to officeId,
            "regionId" to regionId,
            "customerAddress" to currentLocation, // 콜매니저 필드명 (출발지만 저장 - 전화 호출과 동일)
            "departure" to currentLocation,
            "destination" to destinationLocation,
            "timestamp" to com.google.firebase.Timestamp(timestamp / 1000, ((timestamp % 1000) * 1000000).toInt()),
            "status" to status,
            "assignedDriverId" to driverId,
            "estimatedArrivalTime" to estimatedArrivalTime,
            "fare" to fare,
            "notes" to notes,
            // 콜매니저 호환 필드들
            "createdFrom" to createdFrom,
            "customerId" to customerId,
            "customerName" to customerName,  // 고객 이름 (MainViewModel에서 설정)
            "customerGrade" to customerGrade,
            "isAppCustomer" to isAppCustomer,
            "pointsUsed" to pointsUsed,
            "finalFare" to finalFare,
            "discountAmount" to discountAmount
        )
    }

    companion object {
        fun fromMap(data: Map<String, Any>): CustomerCall {
            val timestamp = when (val ts = data["timestamp"]) {
                is com.google.firebase.Timestamp -> ts.seconds * 1000 + ts.nanoseconds / 1000000
                is Number -> ts.toLong()
                else -> System.currentTimeMillis()
            }

            return CustomerCall(
                id = data["id"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String ?: "",
                officeId = data["officeId"] as? String ?: "",
                regionId = data["regionId"] as? String ?: "seoul",
                currentLocation = (data["customerAddress"] as? String) ?: (data["departure"] as? String) ?: "",
                destinationLocation = data["destination"] as? String ?: "",
                timestamp = timestamp,
                status = data["status"] as? String ?: "REQUESTED",
                driverId = data["assignedDriverId"] as? String,
                estimatedArrivalTime = (data["estimatedArrivalTime"] as? Number)?.toInt(),
                fare = (data["fare"] as? Number)?.toInt(),
                notes = data["notes"] as? String,
                // 콜매니저 호환 필드들
                createdFrom = data["createdFrom"] as? String ?: "customer_app",
                customerId = data["customerId"] as? String,
                customerName = data["customerName"] as? String,
                customerGrade = data["customerGrade"] as? String ?: "bronze",
                isAppCustomer = data["isAppCustomer"] as? Boolean ?: true,
                pointsUsed = (data["pointsUsed"] as? Number)?.toInt() ?: 0,
                finalFare = (data["finalFare"] as? Number)?.toInt(),
                discountAmount = (data["discountAmount"] as? Number)?.toInt() ?: 0
            )
        }
    }
}
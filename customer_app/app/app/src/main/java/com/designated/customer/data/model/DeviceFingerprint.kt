package com.designated.customer.data.model

data class DeviceFingerprint(
    val androidId: String,
    val deviceModel: String,
    val osVersion: String,
    val screenResolution: String,
    val timezone: String,
    val language: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "androidId" to androidId,
            "deviceModel" to deviceModel,
            "osVersion" to osVersion,
            "screenResolution" to screenResolution,
            "timezone" to timezone,
            "language" to language,
            "timestamp" to timestamp
        )
    }
}
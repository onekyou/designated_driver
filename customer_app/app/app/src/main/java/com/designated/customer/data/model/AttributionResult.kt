package com.designated.customer.data.model

data class AttributionResult(
    val success: Boolean,
    val officeId: String?,
    val score: Int?,
    val confidence: String?, // HIGH, MEDIUM, LOW
    val requiresManualConfirmation: Boolean = false,
    val requiresManualEntry: Boolean = false
) {
    companion object {
        fun fromMap(data: Map<String, Any>): AttributionResult {
            return AttributionResult(
                success = data["success"] as? Boolean ?: false,
                officeId = data["officeId"] as? String,
                score = (data["score"] as? Number)?.toInt(),
                confidence = data["confidence"] as? String,
                requiresManualConfirmation = data["requiresManualConfirmation"] as? Boolean ?: false,
                requiresManualEntry = data["requiresManualEntry"] as? Boolean ?: false
            )
        }
    }
}
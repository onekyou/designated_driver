package com.designated.customer.service

import android.util.Log
import com.designated.customer.data.model.AttributionResult
import com.designated.customer.data.model.DeviceFingerprint
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

class AttributionService {
    private val functions = FirebaseFunctions.getInstance("asia-northeast3")

    suspend fun matchAttribution(
        fingerprint: DeviceFingerprint,
        phoneNumber: String
    ): AttributionResult {
        Log.d("AttributionService", "matchAttribution 시작 - phoneNumber: $phoneNumber")

        return try {
            val data = hashMapOf(
                "fingerprint" to fingerprint.toMap(),
                "phoneNumber" to phoneNumber,
                "deviceInfo" to fingerprint.toMap()
            )

            Log.d("AttributionService", "Functions 호출 시작 - data: $data")

            val result = functions
                .getHttpsCallable("matchAttribution")
                .call(data)
                .await()

            Log.d("AttributionService", "Functions 응답 성공: ${result.data}")

            AttributionResult.fromMap(result.data as Map<String, Any>)
        } catch (e: Exception) {
            Log.e("AttributionService", "Functions 오류: ${e.message}", e)

            // 오류 시 수동 입력 요구
            AttributionResult(
                success = false,
                officeId = null,
                score = 0,
                confidence = "ERROR",
                requiresManualEntry = true
            )
        }
    }

    suspend fun saveOfficeSelection(
        phoneNumber: String,
        officeId: String,
        reason: String = "manual_selection"
    ): Boolean {
        return try {
            val data = hashMapOf(
                "phoneNumber" to phoneNumber,
                "officeId" to officeId,
                "reason" to reason,
                "timestamp" to System.currentTimeMillis()
            )

            functions
                .getHttpsCallable("saveManualAttribution")
                .call(data)
                .await()

            true
        } catch (e: Exception) {
            false
        }
    }
}
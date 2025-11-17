package com.designated.customer.service

import android.content.Context
import com.designated.customer.util.DeviceFingerprintUtil
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * Attribution 매칭 서비스
 * 디바이스 정보를 수집하고 Cloud Function을 호출하여 사무실 매칭
 */
class AttributionMatchingService(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance("asia-northeast3")
) {
    companion object {
        private const val MATCH_ATTRIBUTION_FUNCTION = "matchAttribution"
    }

    /**
     * 매칭 결과
     */
    sealed class MatchResult {
        data class Success(
            val regionId: String,
            val officeId: String,
            val officePhone: String?,
            val bankName: String?,
            val accountNumber: String?,
            val accountHolder: String?,
            val score: Int,
            val referralDriverId: String? = null,
            val referralDriverName: String? = null
        ) : MatchResult()

        data class NoMatch(val message: String) : MatchResult()
        data class Error(val message: String, val exception: Exception? = null) : MatchResult()
    }

    /**
     * Attribution 매칭 시도
     * @return MatchResult
     */
    suspend fun matchAttribution(): MatchResult {
        try {
            // 1. 디바이스 정보 수집
            val deviceInfo = DeviceFingerprintUtil.collectDeviceInfo(context)

            // 2. Cloud Function이 기대하는 형식으로 데이터 구성
            val requestData = mapOf(
                "fingerprint" to deviceInfo,
                "phoneNumber" to ""
            )

            // 3. Cloud Function 호출
            val result = functions
                .getHttpsCallable(MATCH_ATTRIBUTION_FUNCTION)
                .call(requestData)
                .await()

            val data = result.data as? Map<*, *>

            if (data == null) {
                return MatchResult.Error("서버 응답이 올바르지 않습니다")
            }

            val success = data["success"] as? Boolean ?: false

            return if (success) {
                // 3. 매칭 성공 - Cloud Function이 직접 regionId, officeId 반환
                val regionId = data["regionId"] as? String
                val officeId = data["officeId"] as? String
                val score = (data["score"] as? Number)?.toInt() ?: 0
                val referralDriverId = data["referralDriverId"] as? String
                val referralDriverName = data["referralDriverName"] as? String

                if (regionId != null && officeId != null) {
                    // 사무실 정보 가져오기
                    val officeInfo = getOfficeInfo(regionId, officeId)

                    MatchResult.Success(
                        regionId = regionId,
                        officeId = officeId,
                        officePhone = officeInfo["phone"],
                        bankName = officeInfo["bankName"],
                        accountNumber = officeInfo["accountNumber"],
                        accountHolder = officeInfo["accountHolder"],
                        score = score,
                        referralDriverId = referralDriverId,
                        referralDriverName = referralDriverName
                    )
                } else {
                    MatchResult.Error("매칭 데이터가 올바르지 않습니다")
                }
            } else {
                // 4. 매칭 실패
                val message = data["message"] as? String ?: "매칭된 사무실을 찾을 수 없습니다"
                MatchResult.NoMatch(message)
            }

        } catch (e: Exception) {
            return MatchResult.Error("매칭 중 오류가 발생했습니다: ${e.message}", e)
        }
    }

    /**
     * 사무실 정보 가져오기
     */
    private suspend fun getOfficeInfo(regionId: String, officeId: String): Map<String, String> {
        return try {
            val doc = firestore
                .collection("regions")
                .document(regionId)
                .collection("offices")
                .document(officeId)
                .get()
                .await()

            if (doc.exists()) {
                mapOf(
                    "phone" to (doc.getString("phone") ?: ""),
                    "bankName" to (doc.getString("bankName") ?: ""),
                    "accountNumber" to (doc.getString("accountNumber") ?: ""),
                    "accountHolder" to (doc.getString("accountHolder") ?: "")
                )
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

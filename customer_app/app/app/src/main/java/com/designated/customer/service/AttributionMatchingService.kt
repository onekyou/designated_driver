package com.designated.customer.service

import android.content.Context
import android.util.Log
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
        private const val TAG = "AttributionMatching"
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
            val score: Int
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
            Log.d(TAG, "========== Attribution 매칭 시작 ==========")

            // 1. 디바이스 정보 수집
            val deviceInfo = DeviceFingerprintUtil.collectDeviceInfo(context)
            Log.d(TAG, "디바이스 정보 수집 완료: $deviceInfo")

            // 2. Cloud Function 호출
            Log.d(TAG, "Cloud Function 호출: $MATCH_ATTRIBUTION_FUNCTION")
            val result = functions
                .getHttpsCallable(MATCH_ATTRIBUTION_FUNCTION)
                .call(deviceInfo)
                .await()

            val data = result.data as? Map<*, *>
            Log.d(TAG, "Cloud Function 응답: $data")

            if (data == null) {
                Log.e(TAG, "Cloud Function 응답이 null입니다")
                return MatchResult.Error("서버 응답이 올바르지 않습니다")
            }

            val success = data["success"] as? Boolean ?: false

            return if (success) {
                // 3. 매칭 성공
                val match = data["match"] as? Map<*, *>
                if (match != null) {
                    val regionId = match["regionId"] as? String ?: ""
                    val officeId = match["officeId"] as? String ?: ""
                    val score = (match["score"] as? Number)?.toInt() ?: 0

                    Log.d(TAG, "✅ 매칭 성공: regionId=$regionId, officeId=$officeId, score=$score")

                    // 사무실 정보 가져오기
                    val officeInfo = getOfficeInfo(regionId, officeId)

                    MatchResult.Success(
                        regionId = regionId,
                        officeId = officeId,
                        officePhone = officeInfo["phone"],
                        bankName = officeInfo["bankName"],
                        accountNumber = officeInfo["accountNumber"],
                        accountHolder = officeInfo["accountHolder"],
                        score = score
                    )
                } else {
                    Log.e(TAG, "❌ 매칭 데이터가 없습니다")
                    MatchResult.Error("매칭 데이터가 올바르지 않습니다")
                }
            } else {
                // 4. 매칭 실패
                val message = data["message"] as? String ?: "매칭된 사무실을 찾을 수 없습니다"
                Log.w(TAG, "⚠️ 매칭 실패: $message")
                MatchResult.NoMatch(message)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Attribution 매칭 중 오류 발생", e)
            return MatchResult.Error("매칭 중 오류가 발생했습니다: ${e.message}", e)
        } finally {
            Log.d(TAG, "========== Attribution 매칭 종료 ==========")
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
                Log.w(TAG, "사무실 문서가 존재하지 않음: $regionId/$officeId")
                emptyMap()
            }
        } catch (e: Exception) {
            Log.e(TAG, "사무실 정보 가져오기 실패", e)
            emptyMap()
        }
    }
}

package com.designated.customer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Activity Recognition API로부터 활동 감지 결과를 받는 BroadcastReceiver
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ActivityRecognition"
        private var stepCounterService: StepCounterService? = null

        /**
         * StepCounterService 인스턴스 등록
         */
        fun registerService(service: StepCounterService) {
            stepCounterService = service
            Log.d(TAG, "StepCounterService registered")
        }

        /**
         * StepCounterService 인스턴스 해제
         */
        fun unregisterService() {
            stepCounterService = null
            Log.d(TAG, "StepCounterService unregistered")
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent!!)
            val detectedActivity = result?.mostProbableActivity

            detectedActivity?.let { activity ->
                val confidence = activity.confidence
                val activityType = activity.type

                // 신뢰도가 50% 이상일 때만 처리
                if (confidence >= 50) {
                    val activityName = getActivityName(activityType)
                    Log.i(TAG, "Detected activity: $activityName (confidence: $confidence%)")

                    // StepCounterService에 활동 상태 전달
                    stepCounterService?.updateActivityState(activityType)
                } else {
                    Log.d(TAG, "Activity confidence too low: $confidence%")
                }
            }
        }
    }

    /**
     * 활동 타입을 사람이 읽을 수 있는 문자열로 변환
     */
    private fun getActivityName(activityType: Int): String {
        return when (activityType) {
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "UNIDENTIFIABLE($activityType)"
        }
    }
}

package com.designated.customer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Activity Recognition API로부터 활동 감지 결과를 받는 BroadcastReceiver
 */
class ActivityRecognitionReceiver : BroadcastReceiver() {

    companion object {
        private var stepCounterService: StepCounterService? = null

        /**
         * StepCounterService 인스턴스 등록
         */
        fun registerService(service: StepCounterService) {
            stepCounterService = service
        }

        /**
         * StepCounterService 인스턴스 해제
         */
        fun unregisterService() {
            stepCounterService = null
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
                    // StepCounterService에 활동 상태 전달
                    stepCounterService?.updateActivityState(activityType)
                }
            }
        }
    }
}

package com.designated.callmanager.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * CallScreeningService - 전화번호 확인
 * - Android 10+ (API 29) 지원
 * - READ_CALL_LOG 권한 불필요
 * - ROLE_CALL_SCREENING 권한 필요 (스팸 차단 앱)
 */
@RequiresApi(Build.VERSION_CODES.Q)
class CallScreeningService : CallScreeningService() {

    private val TAG = "CallScreeningService"

    companion object {
        @Volatile
        var latestIncomingNumber: String? = null
            private set

        @Volatile
        private var lastIncomingTime: Long = 0

        /**
         * 전화번호 초기화 (IDLE 상태에서 호출)
         */
        fun clearIncomingNumber() {
            latestIncomingNumber = null
            lastIncomingTime = 0
        }

        /**
         * 5초 이내 전화번호인지 확인
         */
        fun isRecentNumber(): Boolean {
            return (System.currentTimeMillis() - lastIncomingTime) < 5000
        }
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val phoneNumber = callDetails.handle?.schemeSpecificPart
        val callDirection = callDetails.callDirection

        Log.i(TAG, "📞 onScreenCall - Phone: $phoneNumber, Direction: $callDirection")

        if (phoneNumber != null && callDirection == Call.Details.DIRECTION_INCOMING) {
            // 전화번호 저장 (CallReceiver와 공유)
            latestIncomingNumber = phoneNumber
            lastIncomingTime = System.currentTimeMillis()
            Log.i(TAG, "✅ 수신 전화번호 저장: $phoneNumber")
        }

        // 모든 전화 허용 (스팸 차단 기능 없음)
        val response = CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

        respondToCall(callDetails, response)
    }
}

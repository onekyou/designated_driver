package com.designated.callmanager.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.designated.callmanager.service.CallDetectorService

class CallReceiver : BroadcastReceiver() {

    private val tag = "CallReceiver"
    // lastState는 CallDetectorService에서 중복 호출 방지 로직이 있으므로 여기서는 제거하거나 주석 처리 가능
    // private var lastState = TelephonyManager.CALL_STATE_IDLE

    companion object {
        @Volatile private var staticSavedNumber: String? = null
        @Volatile private var staticIsIncoming: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(tag, "🔔 CallReceiver.onReceive called with action: ${intent.action}")
        
        // ---------- Permission check ----------
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            Log.w(tag, "❌ READ_PHONE_STATE permission not granted. Skipping onReceive processing.")
            return
        }

        val action = intent.action
        // Log current static values at the beginning of onReceive for better debugging
        Log.d(tag, "📞 onReceive action: $action, current staticSavedNumber: $staticSavedNumber, current staticIsIncoming: $staticIsIncoming")

        if (action == "android.intent.action.NEW_OUTGOING_CALL") {
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            synchronized(CallReceiver::class.java) {
                staticSavedNumber = phoneNumber
                staticIsIncoming = false
            }
            Log.i(tag, "NEW_OUTGOING_CALL detected. Number: $phoneNumber. staticIsIncoming set to false.")
            // 발신 전화는 PHONE_STATE 변경으로 OFFHOOK 상태가 감지될 때 처리되므로 여기서는 onCallStateChanged를 호출하지 않음

        } else if (action == "android.intent.action.PHONE_STATE") {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val numberFromIntentExtras = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            var callStateFromTelephony = TelephonyManager.CALL_STATE_IDLE // 현재 전화 상태

            // Log details from the PHONE_STATE intent
            Log.i(tag, "PHONE_STATE received. stateStr: $stateStr, numberFromIntentExtras: $numberFromIntentExtras, current staticSavedNumber: $staticSavedNumber, current staticIsIncoming: $staticIsIncoming")

            var numberToPass: String? = null
            var isIncomingToPass: Boolean = false

            synchronized(CallReceiver::class.java) {
                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        callStateFromTelephony = TelephonyManager.CALL_STATE_IDLE
                        Log.i(tag, "Call state set to: IDLE")
                        // IDLE 상태에서는 이전 통화 정보를 사용하고, 이후 초기화
                        numberToPass = staticSavedNumber
                        isIncomingToPass = staticIsIncoming
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        callStateFromTelephony = TelephonyManager.CALL_STATE_OFFHOOK
                        Log.i(tag, "Call state set to: OFFHOOK")
                        // OFFHOOK 시점에는 static 변수에 저장된 (RINGING 또는 NEW_OUTGOING_CALL에서 설정된) 값을 사용
                        numberToPass = staticSavedNumber
                        isIncomingToPass = staticIsIncoming
                    }
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        callStateFromTelephony = TelephonyManager.CALL_STATE_RINGING
                        // RINGING 상태에서는 수신 전화번호와 수신 상태를 static 변수에 저장
                        staticSavedNumber = numberFromIntentExtras
                        staticIsIncoming = true
                        numberToPass = staticSavedNumber
                        isIncomingToPass = staticIsIncoming
                        Log.i(tag, "Call state set to: RINGING, staticSavedNumber updated to: $staticSavedNumber, staticIsIncoming set to: $staticIsIncoming")
                    }
                    else -> {
                        Log.w(tag, "Unknown PHONE_STATE: $stateStr. Not processing.")
                        return // 알 수 없는 상태면 처리 중단
                    }
                }
            } // synchronized block end

            Log.i(tag, "Before calling onCallStateChanged - state: $callStateFromTelephony, number to pass: $numberToPass, isIncoming: $isIncomingToPass")
            
            // 전화번호가 null인 경우에도 CallDetectorService에 알림 (로깅 목적)
            if (numberToPass == null && callStateFromTelephony != TelephonyManager.CALL_STATE_IDLE) {
                Log.w(tag, "⚠️ Warning: Phone number is null for non-IDLE state. This may be normal for some call states.")
            }
            
            onCallStateChanged(context, callStateFromTelephony, numberToPass, isIncomingToPass)

            // 통화가 IDLE 상태로 종료되면 다음 통화를 위해 static 변수 초기화
            if (callStateFromTelephony == TelephonyManager.CALL_STATE_IDLE) {
                synchronized(CallReceiver::class.java) {
                    Log.i(tag, "Resetting static variables as call is IDLE.")
                    staticSavedNumber = null
                    staticIsIncoming = false
                }
            }
        }
    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?, isIncomingCall: Boolean) {
        Log.i(tag, "onCallStateChanged - State: $state, Number: $number, IsIncoming: $isIncomingCall")

        val serviceIntent = Intent(context, CallDetectorService::class.java).apply {
            putExtra("EXTRA_CALL_STATE", state)
            putExtra("incomingPhoneNumber", number)
            putExtra("EXTRA_IS_INCOMING", isIncomingCall)
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.i(tag, "Call State: RINGING. Incoming call from: $number")
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (isIncomingCall) {
                    Log.i(tag, "Call State: OFFHOOK. Incoming call answered. Number: $number")
                } else {
                    Log.i(tag, "Call State: OFFHOOK. Outgoing call started. Number: $number")
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isIncomingCall) {
                    Log.i(tag, "Call State: IDLE. Incoming call ended/missed. Number: $number")
                } else {
                    Log.i(tag, "Call State: IDLE. Outgoing call ended. Number: $number")
                }
            }
            else -> {
                Log.w(tag, "onCallStateChanged: Unknown state $state")
                return // 알 수 없는 상태면 서비스 호출 안 함
            }
        }

        // 모든 유효한 상태에 대해 서비스 시작
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: IllegalStateException) {
            Log.e(tag, "Failed to start CallDetectorService", e)
        }
    }
}
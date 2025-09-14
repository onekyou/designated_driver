package com.designated.callmanager.receiver

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.designated.callmanager.service.CallDetectorService

class CallReceiver : BroadcastReceiver() {

    private val tag = "CallReceiver"

    companion object {
        @Volatile private var staticSavedNumber: String? = null
        @Volatile private var staticIsIncoming: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val action = intent.action

        if (action == "android.intent.action.NEW_OUTGOING_CALL") {
            val phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            synchronized(CallReceiver::class.java) {
                staticSavedNumber = phoneNumber
                staticIsIncoming = false
            }

        } else if (action == "android.intent.action.PHONE_STATE") {
            val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val numberFromIntentExtras = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            var callStateFromTelephony = TelephonyManager.CALL_STATE_IDLE

            var numberToPass: String? = null
            var isIncomingToPass: Boolean = false

            synchronized(CallReceiver::class.java) {
                when (stateStr) {
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        callStateFromTelephony = TelephonyManager.CALL_STATE_IDLE
                        numberToPass = staticSavedNumber
                        isIncomingToPass = staticIsIncoming
                    }
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        callStateFromTelephony = TelephonyManager.CALL_STATE_OFFHOOK
                        numberToPass = staticSavedNumber
                        isIncomingToPass = staticIsIncoming
                    }
                    TelephonyManager.EXTRA_STATE_RINGING -> {
                        callStateFromTelephony = TelephonyManager.CALL_STATE_RINGING
                        staticSavedNumber = numberFromIntentExtras
                        staticIsIncoming = true
                        numberToPass = staticSavedNumber
                        isIncomingToPass = staticIsIncoming
                    }
                    else -> {
                        return
                    }
                }
            }

            if (numberToPass == null && callStateFromTelephony != TelephonyManager.CALL_STATE_IDLE) {
            }

            onCallStateChanged(context, callStateFromTelephony, numberToPass, isIncomingToPass)

            if (callStateFromTelephony == TelephonyManager.CALL_STATE_IDLE) {
                synchronized(CallReceiver::class.java) {
                    staticSavedNumber = null
                    staticIsIncoming = false
                }
            }
        }
    }

    private fun onCallStateChanged(context: Context, state: Int, number: String?, isIncomingCall: Boolean) {

        val serviceIntent = Intent(context, CallDetectorService::class.java).apply {
            putExtra("EXTRA_CALL_STATE", state)
            putExtra("incomingPhoneNumber", number)
            putExtra("EXTRA_IS_INCOMING", isIncomingCall)
        }

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (isIncomingCall) {
                } else {
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isIncomingCall) {
                } else {
                }
            }
            else -> {
                return
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: IllegalStateException) {
        }
    }
}
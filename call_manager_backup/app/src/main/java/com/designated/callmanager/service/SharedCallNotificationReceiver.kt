package com.designated.callmanager.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.designated.callmanager.MainActivity

class SharedCallNotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SharedCallReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_ACCEPT_SHARED_CALL" -> {
                handleAcceptSharedCall(context, intent)
            }
            "ACTION_CANCEL_SHARED_CALL" -> {
                handleCancelSharedCall(context, intent)
            }
        }
    }

    private fun handleAcceptSharedCall(context: Context, intent: Intent) {
        val sharedCallId = intent.getStringExtra("sharedCallId") ?: return
        val departure = intent.getStringExtra("departure") ?: "출발지"
        val destination = intent.getStringExtra("destination") ?: "도착지"
        val fare = intent.getStringExtra("fare") ?: "0"
        val phoneNumber = intent.getStringExtra("phoneNumber") ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = "shared_call_$sharedCallId".hashCode()
        notificationManager.cancel(notificationId)
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            action = "ACTION_SHOW_SHARED_CALL_DISPATCH"
            putExtra("sharedCallId", sharedCallId)
            putExtra("departure", departure)
            putExtra("destination", destination)
            putExtra("fare", fare)
            putExtra("phoneNumber", phoneNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        context.startActivity(activityIntent)
        }

    private fun handleCancelSharedCall(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId != -1) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)
            }
    }
}
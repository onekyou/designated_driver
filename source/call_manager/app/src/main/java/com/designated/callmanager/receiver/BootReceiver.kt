package com.designated.callmanager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.designated.callmanager.service.CallManagerService
import com.google.firebase.auth.FirebaseAuth

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser != null) {
                startCallManagerService(context)
            } else {
            }
        }
    }

    private fun startCallManagerService(context: Context) {
        val serviceIntent = Intent(context, CallManagerService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

    }
}
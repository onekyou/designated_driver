package com.designated.driverapp

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.designated.driverapp/lockscreen"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "showLockScreen" -> {
                        val callId = call.argument<String>("callId") ?: ""
                        val customerName = call.argument<String>("customerName") ?: "고객"
                        val destination = call.argument<String>("destination") ?: ""
                        val phoneNumber = call.argument<String>("phoneNumber") ?: ""

                        val intent = Intent(this, LockScreenActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            putExtra("callId", callId)
                            putExtra("customerName", customerName)
                            putExtra("destination", destination)
                            putExtra("phoneNumber", phoneNumber)
                        }
                        startActivity(intent)
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}

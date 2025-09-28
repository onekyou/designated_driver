package com.designated.customer.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import com.designated.customer.data.model.DeviceFingerprint
import java.util.*

class FingerprintManager(private val context: Context) {

    fun collectFingerprint(): DeviceFingerprint {
        return DeviceFingerprint(
            androidId = getAndroidId(),
            deviceModel = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            screenResolution = getScreenResolution(),
            timezone = TimeZone.getDefault().id,
            language = Locale.getDefault().language
        )
    }

    private fun getAndroidId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "unknown"
    }

    private fun getScreenResolution(): String {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        return "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
    }
}
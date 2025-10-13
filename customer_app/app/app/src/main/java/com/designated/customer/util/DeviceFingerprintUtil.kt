package com.designated.customer.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.security.MessageDigest

/**
 * 디바이스 핑거프린트 수집 유틸리티
 * 랜딩페이지에서 수집한 정보와 매칭하기 위한 디바이스 정보 생성
 */
object DeviceFingerprintUtil {

    /**
     * 디바이스 정보를 수집하여 Map으로 반환
     */
    fun collectDeviceInfo(context: Context): Map<String, Any> {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )

        return mapOf(
            // 디바이스 고유 정보
            "androidId" to (androidId ?: ""),
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "brand" to Build.BRAND,
            "device" to Build.DEVICE,

            // OS 정보
            "osVersion" to Build.VERSION.RELEASE,
            "sdkVersion" to Build.VERSION.SDK_INT,

            // 화면 정보
            "screenResolution" to getScreenResolution(context),
            "screenDensity" to context.resources.displayMetrics.density,

            // 기타
            "userAgent" to getUserAgent(),
            "timezone" to java.util.TimeZone.getDefault().id,
            "language" to java.util.Locale.getDefault().language,

            // 타임스탬프
            "timestamp" to System.currentTimeMillis(),

            // 소스
            "source" to "android_app"
        )
    }

    /**
     * 화면 해상도 문자열 반환
     */
    private fun getScreenResolution(context: Context): String {
        val displayMetrics = context.resources.displayMetrics
        return "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
    }

    /**
     * User Agent 문자열 생성
     */
    private fun getUserAgent(): String {
        return "DesignatedCustomer/${Build.VERSION.RELEASE} " +
                "(${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE})"
    }

    /**
     * 디바이스 핑거프린트 해시 생성
     * 여러 디바이스 정보를 조합하여 고유한 식별자 생성
     */
    fun generateFingerprint(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: ""

        val fingerprintString = buildString {
            append(androidId)
            append(Build.MANUFACTURER)
            append(Build.MODEL)
            append(Build.DEVICE)
            append(Build.BRAND)
        }

        return sha256(fingerprintString)
    }

    /**
     * SHA-256 해시 생성
     */
    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

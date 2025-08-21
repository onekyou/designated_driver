package com.designated.callmanager.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * 배터리 최적화 예외 처리 도우미 클래스
 * - 화면 꺼진 상태에서도 PTT가 작동하도록 배터리 최적화에서 제외
 */
object BatteryOptimizationHelper {
    private const val TAG = "BatteryOptimization"
    
    /**
     * 앱이 배터리 최적화에서 제외되어 있는지 확인
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            true // Android 6.0 미만에서는 배터리 최적화 기능 없음
        }
    }
    
    /**
     * 배터리 최적화 예외 설정 화면으로 이동
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun requestIgnoreBatteryOptimizations(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
            Log.i(TAG, "배터리 최적화 예외 설정 화면으로 이동")
        } catch (e: Exception) {
            Log.e(TAG, "배터리 최적화 설정 화면 이동 실패", e)
            // 실패 시 일반 배터리 최적화 설정 화면으로 이동
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                activity.startActivity(intent)
                Log.i(TAG, "일반 배터리 최적화 설정 화면으로 이동")
            } catch (e2: Exception) {
                Log.e(TAG, "일반 배터리 최적화 설정 화면 이동도 실패", e2)
            }
        }
    }
    
    /**
     * 배터리 최적화 예외가 필요한지 확인하고 사용자에게 안내
     */
    fun checkAndRequestBatteryOptimization(activity: Activity, onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val isIgnoring = isIgnoringBatteryOptimizations(activity)
            
            if (!isIgnoring) {
                Log.w(TAG, "앱이 배터리 최적화 대상임 - 화면 꺼진 상태에서 PTT 문제 가능")
                
                // 사용자에게 배터리 최적화 예외 설정을 요청
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    Log.e(TAG, "배터리 최적화 설정 요청 실패", e)
                }
                
                onResult(false)
            } else {
                Log.i(TAG, "✅ 앱이 배터리 최적화에서 제외됨 - 화면 꺼진 상태에서도 PTT 가능")
                onResult(true)
            }
        } else {
            // Android 6.0 미만에서는 배터리 최적화 기능 없음
            onResult(true)
        }
    }
}
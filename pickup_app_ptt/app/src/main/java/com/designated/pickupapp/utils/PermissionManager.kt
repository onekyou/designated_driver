package com.designated.pickupapp.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * 픽업앱 권한 관리자
 * PTT 기능을 위한 필수 권한 관리
 */
object PermissionManager {
    private const val TAG = "PermissionManager"
    
    // 권한 요청 코드
    const val REQUEST_CODE_PERMISSIONS = 1001
    const val REQUEST_CODE_OVERLAY = 1002
    const val REQUEST_CODE_NOTIFICATION = 1003
    
    /**
     * PTT 기능에 필요한 필수 권한 목록 (픽업앱용 - 전화 관련 권한 제외)
     */
    val REQUIRED_PERMISSIONS = mutableListOf<String>().apply {
        // PTT 음성 통신 필수 권한
        add(Manifest.permission.RECORD_AUDIO) // 마이크 권한 (가장 중요!)
        add(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        
        // Bluetooth 오디오 장치 연결용
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        // 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
    
    /**
     * PTT 핵심 권한 (반드시 필요)
     */
    val CRITICAL_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.MODIFY_AUDIO_SETTINGS
    )
    
    /**
     * 모든 필수 권한이 허용되었는지 확인
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * PTT 핵심 권한이 허용되었는지 확인
     */
    fun hasCriticalPermissions(context: Context): Boolean {
        return CRITICAL_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == 
                PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 특정 권한이 허용되었는지 확인
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == 
            PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 마이크 권한 확인
     */
    fun hasMicrophonePermission(context: Context): Boolean {
        return hasPermission(context, Manifest.permission.RECORD_AUDIO)
    }
    
    /**
     * 알림 권한 확인 (Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Android 12 이하는 기본적으로 허용
        }
    }
    
    /**
     * 오버레이 권한 확인 (다른 앱 위에 표시)
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    /**
     * 접근성 서비스 활성화 확인
     */
    fun hasAccessibilityPermission(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val serviceName = "${context.packageName}/${com.designated.pickupapp.ptt.service.PTTAccessibilityService::class.java.name}"
            enabledServices.contains(serviceName, ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check accessibility permission", e)
            false
        }
    }
    
    /**
     * 권한 요청
     */
    fun requestPermissions(activity: Activity) {
        val permissionsToRequest = REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != 
                PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    /**
     * 핵심 권한만 요청
     */
    fun requestCriticalPermissions(activity: Activity) {
        val permissionsToRequest = CRITICAL_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != 
                PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        
        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Requesting critical permissions: ${permissionsToRequest.joinToString()}")
            ActivityCompat.requestPermissions(
                activity,
                permissionsToRequest,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }
    
    /**
     * 오버레이 권한 요청 화면으로 이동
     */
    fun requestOverlayPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            )
            activity.startActivityForResult(intent, REQUEST_CODE_OVERLAY)
        }
    }
    
    /**
     * 접근성 서비스 설정 화면으로 이동
     */
    fun requestAccessibilityPermission(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            activity.startActivity(intent)
            
            // 사용자에게 안내 메시지 표시
            android.widget.Toast.makeText(
                activity,
                "'픽업 PTT' 서비스를 찾아서 활성화해주세요",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
            android.widget.Toast.makeText(
                activity,
                "설정을 열 수 없습니다. 수동으로 설정 > 접근성에서 픽업 PTT를 활성화해주세요",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    
    /**
     * 앱 설정 화면으로 이동
     */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }
    
    /**
     * 권한 요청 결과 처리
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onAllGranted: () -> Unit,
        onDenied: (List<String>) -> Unit
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val deniedPermissions = mutableListOf<String>()
            
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission)
                    Log.w(TAG, "Permission denied: $permission")
                } else {
                    Log.d(TAG, "Permission granted: $permission")
                }
            }
            
            if (deniedPermissions.isEmpty()) {
                onAllGranted()
            } else {
                onDenied(deniedPermissions)
            }
        }
    }
    
    /**
     * 거부된 권한의 이름을 사용자 친화적으로 변환
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "마이크"
            Manifest.permission.POST_NOTIFICATIONS -> "알림"
            Manifest.permission.BLUETOOTH_CONNECT -> "블루투스"
            Manifest.permission.CALL_PHONE -> "전화"
            Manifest.permission.MODIFY_AUDIO_SETTINGS -> "오디오 설정"
            else -> permission.substringAfterLast(".")
        }
    }
    
    /**
     * 권한 상태 로그 출력
     */
    fun logPermissionStatus(context: Context) {
        Log.d(TAG, "========== Permission Status ==========")
        REQUIRED_PERMISSIONS.forEach { permission ->
            val status = if (hasPermission(context, permission)) "GRANTED" else "DENIED"
            Log.d(TAG, "${getPermissionDisplayName(permission)}: $status")
        }
        Log.d(TAG, "Overlay permission: ${if (hasOverlayPermission(context)) "GRANTED" else "DENIED"}")
        Log.d(TAG, "======================================")
    }
}
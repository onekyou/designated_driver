package com.designated.driverapp.util

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat

class DriverPermissionManager(
    private val activity: Activity,
    private val onAllPermissionsGranted: () -> Unit,
    private val onPermissionsDenied: (List<String>) -> Unit
) {

    companion object {
        private const val PREFS_NAME = "DriverAppPrefs"
    }

    private val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var isRequestingPermissions = false

    data class PermissionInfo(
        val permission: String,
        val title: String,
        val description: String,
        val required: Boolean = true
    )

    private val requiredPermissions = listOf(
        // 위치 권한 (기사 앱 필수)
        PermissionInfo(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            title = "정확한 위치",
            description = "운행 중 실시간 위치 추적 및 고객에게 위치 전송에 필요합니다.",
            required = true
        ),
        PermissionInfo(
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            title = "대략적인 위치",
            description = "운행 중 실시간 위치 추적에 필요합니다.",
            required = true
        ),

        // 알림 권한
        PermissionInfo(
            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else "",
            title = "알림 권한",
            description = "새로운 콜 배차 및 운행 관련 알림을 받기 위해 필요합니다.",
            required = true
        ),

        // 음성 인식 권한 (기사용)
        PermissionInfo(
            permission = Manifest.permission.RECORD_AUDIO,
            title = "음성 인식",
            description = "출발지/도착지 주소와 요금을 음성으로 입력하는 기능에 필요합니다.",
            required = false
        )
    ).filter { it.permission.isNotEmpty() }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    fun initialize(
        permissionLauncher: ActivityResultLauncher<Array<String>>
    ) {
        this.permissionLauncher = permissionLauncher
    }

    fun requestAllPermissions() {
        if (isRequestingPermissions) return
        isRequestingPermissions = true

        showPermissionExplanationDialog()
    }

    private fun showPermissionExplanationDialog() {
        val requiredPerms = getRequiredPermissions()
        val optionalPerms = getOptionalPermissions()

        val message = buildString {
            append("기사 앱의 정상적인 동작을 위해 다음 권한이 필요합니다:\n\n")

            if (requiredPerms.isNotEmpty()) {
                append("🚗 필수 권한 (운행 관리):\n")
                requiredPerms.forEach { perm ->
                    append("• ${perm.title}: ${perm.description}\n")
                }
                append("\n")
            }

            if (optionalPerms.isNotEmpty()) {
                append("🎤 추가 기능 권한:\n")
                optionalPerms.forEach { perm ->
                    append("• ${perm.title}: ${perm.description}\n")
                }
                append("\n")
            }

            append("⚠️ 필수 권한이 없으면 앱이 작동하지 않습니다.\n")
            append("권한을 허용하시겠습니까?")
        }

        AlertDialog.Builder(activity)
            .setTitle("🚗 기사 앱 권한 요청")
            .setMessage(message)
            .setPositiveButton("모든 권한 허용") { _, _ ->
                requestRuntimePermissions()
            }
            .setNeutralButton("필수 권한만") { _, _ ->
                requestRequiredPermissionsOnly()
            }
            .setNegativeButton("거부") { _, _ ->
                isRequestingPermissions = false
                showPermissionDeniedDialog()
            }
            .setCancelable(false)
            .show()
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("⚠️ 권한 필요")
            .setMessage(
                "기사 앱은 위치 권한 없이는 작동할 수 없습니다.\n\n" +
                "앱을 사용하려면 설정에서 권한을 허용해 주세요."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    // 설정 화면 열기 실패
                }
                isRequestingPermissions = false
            }
            .setNegativeButton("종료") { _, _ ->
                isRequestingPermissions = false
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun getRequiredPermissions(): List<PermissionInfo> {
        return requiredPermissions.filter { it.required && needsPermission(it.permission) }
    }

    private fun getOptionalPermissions(): List<PermissionInfo> {
        return requiredPermissions.filter { !it.required && needsPermission(it.permission) }
    }

    private fun needsPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
    }

    private fun requestRuntimePermissions() {
        val allPermissions = requiredPermissions
            .filter { needsPermission(it.permission) }
            .map { it.permission }
            .toTypedArray()

        if (allPermissions.isNotEmpty()) {
            permissionLauncher.launch(allPermissions)
        } else {
            finalizePermissionCheck()
        }
    }

    private fun requestRequiredPermissionsOnly() {
        val requiredPerms = getRequiredPermissions()
            .map { it.permission }
            .toTypedArray()

        if (requiredPerms.isNotEmpty()) {
            permissionLauncher.launch(requiredPerms)
        } else {
            finalizePermissionCheck()
        }
    }

    fun onPermissionResult(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys.toList()
        val deniedRequiredPermissions = deniedPermissions.filter { permission ->
            requiredPermissions.any { it.permission == permission && it.required }
        }

        if (deniedRequiredPermissions.isNotEmpty()) {
            // 필수 권한이 거부된 경우
            showCriticalPermissionDeniedDialog(deniedRequiredPermissions)
        } else {
            // 필수 권한은 모두 허용된 경우 바로 완료 처리
            finalizePermissionCheck()
        }
    }

    private fun showCriticalPermissionDeniedDialog(deniedPermissions: List<String>) {
        val deniedPermissionNames = deniedPermissions.mapNotNull { permission ->
            requiredPermissions.find { it.permission == permission }?.title
        }.joinToString(", ")

        AlertDialog.Builder(activity)
            .setTitle("❌ 필수 권한 거부됨")
            .setMessage(
                "다음 필수 권한이 거부되어 앱을 사용할 수 없습니다:\n\n" +
                "$deniedPermissionNames\n\n" +
                "설정에서 권한을 허용하거나 앱을 다시 시작해 주세요."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    // 설정 화면 열기 실패
                }
                isRequestingPermissions = false
            }
            .setNeutralButton("다시 시도") { _, _ ->
                isRequestingPermissions = false
                requestAllPermissions()
            }
            .setNegativeButton("종료") { _, _ ->
                isRequestingPermissions = false
                activity.finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkSpecialPermissions() {
        // 기사 앱에서는 오버레이 권한 및 배터리 최적화 불필요 - 바로 완료 처리
        finalizePermissionCheck()
    }

    // 오버레이 권한 관련 메서드들 제거됨 - 기사 앱에서는 불필요

    // 배터리 최적화 관련 메서드들 제거됨 - 기사 앱에서는 불필요

    // requestBatteryOptimizationExemption() 메서드 제거됨

    private fun getAppName(): String {
        return try {
            val packageInfo = activity.packageManager.getApplicationInfo(activity.packageName, 0)
            activity.packageManager.getApplicationLabel(packageInfo).toString()
        } catch (e: Exception) {
            "기사 앱"
        }
    }

    private fun finalizePermissionCheck() {
        isRequestingPermissions = false

        val missingRequiredPermissions = getRequiredPermissions()

        if (missingRequiredPermissions.isEmpty()) {
            onAllPermissionsGranted()
        } else {
            onPermissionsDenied(missingRequiredPermissions.map { it.permission })
        }
    }

    private fun showToast(message: String) {
        android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun areAllRequiredPermissionsGranted(): Boolean {
        return getRequiredPermissions().isEmpty()
    }
}
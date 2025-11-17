package com.designated.callmanager.util

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.edit

class CallManagerPermissionManager(
    private val activity: Activity,
    private val onAllPermissionsGranted: () -> Unit,
    private val onPermissionsDenied: (List<String>) -> Unit
) {

    companion object {
        private const val PREFS_NAME = "CallManagerPrefs"
        private const val KEY_OVERLAY_PERMISSION_REQUESTED = "overlay_permission_requested"
        private const val KEY_BATTERY_OPTIMIZATION_REQUESTED = "battery_optimization_requested"
        private const val KEY_CALL_SCREENING_ROLE_REQUESTED = "call_screening_role_requested"
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
        // 위치 권한 (콜 매니저 필수)
        PermissionInfo(
            permission = Manifest.permission.ACCESS_FINE_LOCATION,
            title = "정확한 위치",
            description = "기사 위치 추적 및 콜 배차에 필요합니다.",
            required = true
        ),
        PermissionInfo(
            permission = Manifest.permission.ACCESS_COARSE_LOCATION,
            title = "대략적인 위치",
            description = "기사 위치 추적 및 콜 배차에 필요합니다.",
            required = true
        ),

        // 알림 권한
        PermissionInfo(
            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else "",
            title = "알림 권한",
            description = "새로운 콜 및 기사 상태 알림을 받기 위해 필요합니다.",
            required = true
        ),

        // 콜 디텍터 활성화 시 필요한 권한들
        PermissionInfo(
            permission = Manifest.permission.READ_PHONE_STATE,
            title = "전화 상태 읽기",
            description = "콜 디텍터 기능 활성화 시 전화 감지에 필요합니다.",
            required = false
        ),
        PermissionInfo(
            permission = Manifest.permission.READ_CONTACTS,
            title = "연락처 읽기",
            description = "콜 디텍터 기능 활성화 시 고객명 표시에 필요합니다.",
            required = false
        ),
        PermissionInfo(
            permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                Manifest.permission.READ_PHONE_NUMBERS else "",
            title = "전화번호 읽기",
            description = "콜 디텍터 기능 활성화 시 전화번호 정보에 필요합니다.",
            required = false
        ),
        PermissionInfo(
            permission = "android.permission.PROCESS_OUTGOING_CALLS",
            title = "발신 전화 처리",
            description = "콜 디텍터 기능 활성화 시 발신 전화 감지에 필요합니다.",
            required = false
        ),
        PermissionInfo(
            permission = Manifest.permission.SEND_SMS,
            title = "SMS 발송",
            description = "자동 문자 발송 기능에 필요합니다.",
            required = false
        ),
        PermissionInfo(
            permission = Manifest.permission.RECORD_AUDIO,
            title = "음성 인식",
            description = "음성으로 주소 및 요금을 입력하는 기능에 필요합니다.",
            required = false
        )
    ).filter { it.permission.isNotEmpty() }

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    fun initialize(
        permissionLauncher: ActivityResultLauncher<Array<String>>,
        overlayPermissionLauncher: ActivityResultLauncher<Intent>
    ) {
        this.permissionLauncher = permissionLauncher
        this.overlayPermissionLauncher = overlayPermissionLauncher
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
            append("콜 매니저의 정상적인 동작을 위해 다음 권한이 필요합니다:\n\n")

            if (requiredPerms.isNotEmpty()) {
                append("🚗 필수 권한 (콜 관리 기능):\n")
                requiredPerms.forEach { perm ->
                    append("• ${perm.title}: ${perm.description}\n")
                }
                append("\n")
            }

            if (optionalPerms.isNotEmpty()) {
                append("📞 추가 기능 권한 (콜 디텍터):\n")
                optionalPerms.forEach { perm ->
                    append("• ${perm.title}: ${perm.description}\n")
                }
                append("\n")
            }

            append("⚠️ 필수 권한이 없으면 앱이 작동하지 않습니다.\n")
            append("권한을 허용하시겠습니까?")
        }

        AlertDialog.Builder(activity)
            .setTitle("🚗 콜 매니저 권한 요청")
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
                "콜 매니저는 위치 권한 없이는 작동할 수 없습니다.\n\n" +
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
        // 콜 디텍터 활성화 여부 확인
        val isCallDetectionEnabled = prefs.getBoolean("call_detection_enabled", false)

        return requiredPermissions.filter {
            !it.required && needsPermission(it.permission) &&
            (isCallDetectionEnabled ||
             it.permission == Manifest.permission.SEND_SMS ||
             it.permission == Manifest.permission.RECORD_AUDIO)
        }
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
            checkSpecialPermissions()
        }
    }

    private fun requestRequiredPermissionsOnly() {
        val requiredPerms = getRequiredPermissions()
            .map { it.permission }
            .toTypedArray()

        if (requiredPerms.isNotEmpty()) {
            permissionLauncher.launch(requiredPerms)
        } else {
            checkSpecialPermissions()
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
            // 필수 권한은 모두 허용된 경우 특수 권한 체크
            checkSpecialPermissions()
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
        // 오버레이 권한 확인
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(activity)) {
            showOverlayPermissionDialog()
            return
        }

        checkBatteryOptimization()
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(activity)
            .setTitle("📱 백그라운드 표시 권한")
            .setMessage(
                "백그라운드에서 콜 알림 및 기사 상태 팝업을 표시하기 위해 '다른 앱 위에 표시' 권한이 필요합니다.\n\n" +
                "이 권한이 없으면 앱이 백그라운드에 있을 때 중요한 알림을 놓칠 수 있습니다."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                prefs.edit { putBoolean(KEY_OVERLAY_PERMISSION_REQUESTED, true) }
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${activity.packageName}"))
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton("건너뛰기") { _, _ ->
                showToast("백그라운드 알림 표시가 제한됩니다")
                checkBatteryOptimization()
            }
            .setCancelable(false)
            .show()
    }

    fun onOverlayPermissionResult() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(activity)
        } else true

        if (hasPermission) {
            showToast("백그라운드 알림 표시가 활성화되었습니다")
        } else {
            showToast("백그라운드 알림 표시가 제한됩니다")
        }

        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = activity.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val hasRequestedBefore = prefs.getBoolean(KEY_BATTERY_OPTIMIZATION_REQUESTED, false)

            if (!powerManager.isIgnoringBatteryOptimizations(activity.packageName) && !hasRequestedBefore) {
                showBatteryOptimizationDialog()
                return
            }
        }

        checkCallScreeningRole()
    }

    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(activity)
            .setTitle("🔋 배터리 최적화 제외")
            .setMessage(
                "콜 매니저가 백그라운드에서 안정적으로 콜 알림 및 기사 상태를 관리하려면 배터리 최적화에서 제외해야 합니다.\n\n" +
                "설정에서 이 앱을 '최적화하지 않음'으로 설정해 주세요.\n\n" +
                "⚠️ 이 설정이 없으면 중요한 알림을 놓칠 수 있습니다."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                prefs.edit { putBoolean(KEY_BATTERY_OPTIMIZATION_REQUESTED, true) }
                requestBatteryOptimizationExemption()
                checkCallScreeningRole()
            }
            .setNegativeButton("건너뛰기") { _, _ ->
                prefs.edit { putBoolean(KEY_BATTERY_OPTIMIZATION_REQUESTED, true) }
                showToast("배터리 절약 모드에서 알림이 중단될 수 있습니다")
                checkCallScreeningRole()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // 직접 앱 설정 화면 열기
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:${activity.packageName}")
                activity.startActivity(intent)
                showToast("앱을 선택하고 '허용'을 눌러주세요")
            } catch (e: Exception) {
                try {
                    // 배터리 최적화 설정 화면 열기
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    activity.startActivity(intent)
                    showToast("앱 목록에서 '${getAppName()}'을 찾아 '허용'으로 설정해주세요")
                } catch (e2: Exception) {
                    try {
                        // 배터리 설정 화면 열기
                        val intent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                        activity.startActivity(intent)
                        showToast("배터리 설정에서 앱 최적화를 비활성화해주세요")
                    } catch (e3: Exception) {
                        try {
                            // 앱 정보 화면 열기
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            intent.data = Uri.parse("package:${activity.packageName}")
                            activity.startActivity(intent)
                            showToast("앱 정보에서 배터리 최적화를 비활성화해주세요")
                        } catch (e4: Exception) {
                            showToast("설정 화면을 열 수 없습니다. 수동으로 배터리 설정을 확인해주세요")
                        }
                    }
                }
            }
        }
    }

    private fun getAppName(): String {
        return try {
            val packageInfo = activity.packageManager.getApplicationInfo(activity.packageName, 0)
            activity.packageManager.getApplicationLabel(packageInfo).toString()
        } catch (e: Exception) {
            "콜 매니저"
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

    /**
     * ROLE_CALL_SCREENING 권한 체크 (Android 10+ 전용)
     * 콜 감지를 위해 스팸 차단 앱 권한이 필요
     */
    private fun checkCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasRequestedBefore = prefs.getBoolean(KEY_CALL_SCREENING_ROLE_REQUESTED, false)

            if (needsCallScreeningRole() && !hasRequestedBefore) {
                showCallScreeningRoleDialog()
                return
            }
        }

        finalizePermissionCheck()
    }

    /**
     * ROLE_CALL_SCREENING 권한이 필요한지 확인
     */
    private fun needsCallScreeningRole(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = activity.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) != true
        } else {
            false // Android 9 이하는 CallScreeningService 지원 안 함
        }
    }

    /**
     * ROLE_CALL_SCREENING 권한 요청 다이얼로그
     */
    private fun showCallScreeningRoleDialog() {
        AlertDialog.Builder(activity)
            .setTitle("📞 통화 감지 권한")
            .setMessage(
                "콜 매니저가 수신 전화를 자동으로 감지하여 Firebase에 업로드하려면 '스팸 차단 앱' 권한이 필요합니다.\n\n" +
                "Android 10 이상에서는 이 권한이 없으면 전화번호를 읽을 수 없습니다.\n\n" +
                "⚠️ 이 권한이 없으면 콜 감지가 작동하지 않습니다."
            )
            .setPositiveButton("설정으로 이동") { _, _ ->
                prefs.edit { putBoolean(KEY_CALL_SCREENING_ROLE_REQUESTED, true) }
                requestCallScreeningRoleFromActivity()
                // 사용자가 설정 후 돌아오면 권한 체크 완료
                finalizePermissionCheck()
            }
            .setNegativeButton("건너뛰기") { _, _ ->
                prefs.edit { putBoolean(KEY_CALL_SCREENING_ROLE_REQUESTED, true) }
                showToast("⚠️ Android 10 이상에서 콜 감지가 제한됩니다")
                finalizePermissionCheck()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * MainActivity의 requestCallScreeningRole() 호출
     */
    private fun requestCallScreeningRoleFromActivity() {
        try {
            // MainActivity의 requestCallScreeningRole() 호출
            val mainActivity = activity as? com.designated.callmanager.MainActivity
            mainActivity?.requestCallScreeningRole()
        } catch (e: Exception) {
            showToast("스팸 차단 앱 권한 요청에 실패했습니다")
        }
    }
}
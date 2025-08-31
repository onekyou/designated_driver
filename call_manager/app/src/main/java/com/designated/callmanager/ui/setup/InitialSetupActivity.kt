package com.designated.callmanager.ui.setup

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.designated.callmanager.MainActivity
import com.designated.callmanager.R
import com.designated.callmanager.ptt.service.PTTAccessibilityService
import com.designated.callmanager.ui.theme.CallManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 앱 최초 설치 시 모든 권한을 한번에 설정하는 통합 설정 화면
 */
class InitialSetupActivity : ComponentActivity() {
    
    private val TAG = "InitialSetupActivity"
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    // 권한 상태 관리
    private var permissionStates by mutableStateOf(mapOf<String, PermissionState>())
    private var currentStep by mutableStateOf(SetupStep.WELCOME)
    private var isCompleting by mutableStateOf(false)
    
    // 필요한 기본 권한들 - 한번에 요청할 권한들
    private val requiredPermissions = buildList {
        // Android 13+ 알림 권한 (가장 먼저)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // 전화 관련 권한 그룹 (PHONE 그룹)
        add(Manifest.permission.READ_PHONE_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        add(Manifest.permission.READ_CALL_LOG)
        
        // 연락처 권한 (CONTACTS 그룹)
        add(Manifest.permission.READ_CONTACTS)
        
        // 마이크 권한 (MICROPHONE 그룹)
        add(Manifest.permission.RECORD_AUDIO)
        
        // 발신 전화 처리 (Android 9 미만에서만)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            add("android.permission.PROCESS_OUTGOING_CALLS")
        }
    }
    
    // 특수 권한들 (배터리 최적화 포함)
    private val specialPermissions = listOf(
        "OVERLAY" to "다른 앱 위에 표시",
        "BATTERY_OPTIMIZATION" to "배터리 최적화 제외", 
        "ACCESSIBILITY" to "접근성 서비스"
    )
    
    // ActivityCompat.requestPermissions()를 사용하므로 런처는 사용하지 않음
    // onRequestPermissionsResult()에서 처리
    
    // 오버레이 권한 요청 런처
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updateOverlayPermissionState()
        // 자동으로 다음 단계로 넘어가지 않음
    }
    
    // 접근성 설정 런처
    private val accessibilitySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updateAccessibilityPermissionState()
        // 자동으로 다음 단계로 넘어가지 않음
    }
    
    // 배터리 최적화 설정 런처
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        updateBatteryOptimizationState()
        // 자동으로 다음 단계로 넘어가지 않음
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "Initial setup started")
        initializePermissionStates()
        
        setContent {
            CallManagerTheme {
                InitialSetupScreen()
            }
        }
    }
    
    // ActivityCompat.requestPermissions()의 결과를 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // 권한 결과를 Map으로 변환
            val permissionResults = permissions.mapIndexed { index, permission ->
                permission to (grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED)
            }.toMap()
            
            // 상태 업데이트
            updatePermissionStates(permissionResults)
            
            // 모든 권한이 허용되면 배터리 최적화도 자동 요청
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                requestBatteryOptimization()
            }
        }
    }
    
    private fun initializePermissionStates() {
        val initialStates = mutableMapOf<String, PermissionState>()
        
        // 일반 권한들
        requiredPermissions.forEach { permission ->
            initialStates[permission] = PermissionState(
                isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED,
                isRequired = true,
                description = getPermissionDescription(permission)
            )
        }
        
        // 특수 권한들
        initialStates["OVERLAY"] = PermissionState(
            isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true,
            isRequired = true,
            description = "다른 앱 위에 표시 (콜 팝업 기능)"
        )
        
        initialStates["ACCESSIBILITY"] = PermissionState(
            isGranted = isAccessibilityServiceEnabled(),
            isRequired = false,
            description = "접근성 서비스 (볼륨키 PTT 기능)"
        )
        
        initialStates["BATTERY_OPTIMIZATION"] = PermissionState(
            isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
            } else true,
            isRequired = false,
            description = "배터리 최적화 제외 (백그라운드 안정성)"
        )
        
        permissionStates = initialStates
    }
    
    private fun updatePermissionStates(permissions: Map<String, Boolean>) {
        val updatedStates = permissionStates.toMutableMap()
        permissions.forEach { (permission, isGranted) ->
            updatedStates[permission] = updatedStates[permission]?.copy(isGranted = isGranted)
                ?: PermissionState(isGranted, true, getPermissionDescription(permission))
        }
        permissionStates = updatedStates
    }
    
    private fun updateOverlayPermissionState() {
        val updatedStates = permissionStates.toMutableMap()
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
        
        updatedStates["OVERLAY"] = updatedStates["OVERLAY"]?.copy(isGranted = isGranted)
            ?: PermissionState(isGranted, true, "다른 앱 위에 표시")
        
        permissionStates = updatedStates
    }
    
    private fun updateAccessibilityPermissionState() {
        val updatedStates = permissionStates.toMutableMap()
        val isGranted = isAccessibilityServiceEnabled()
        
        updatedStates["ACCESSIBILITY"] = updatedStates["ACCESSIBILITY"]?.copy(isGranted = isGranted)
            ?: PermissionState(isGranted, false, "접근성 서비스")
        
        permissionStates = updatedStates
    }
    
    private fun updateBatteryOptimizationState() {
        val updatedStates = permissionStates.toMutableMap()
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } else true
        
        updatedStates["BATTERY_OPTIMIZATION"] = updatedStates["BATTERY_OPTIMIZATION"]?.copy(isGranted = isGranted)
            ?: PermissionState(isGranted, false, "배터리 최적화 제외")
        
        permissionStates = updatedStates
    }
    
    private fun updateAllPermissionStates() {
        val updatedStates = permissionStates.toMutableMap()
        
        // 기본 권한들 업데이트
        requiredPermissions.forEach { permission ->
            val isGranted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            updatedStates[permission] = updatedStates[permission]?.copy(isGranted = isGranted)
                ?: PermissionState(isGranted, true, getPermissionDescription(permission))
        }
        
        // 특수 권한들 업데이트
        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
        updatedStates["OVERLAY"] = updatedStates["OVERLAY"]?.copy(isGranted = overlayGranted)
            ?: PermissionState(overlayGranted, true, "다른 앱 위에 표시")
        
        val accessibilityGranted = isAccessibilityServiceEnabled()
        updatedStates["ACCESSIBILITY"] = updatedStates["ACCESSIBILITY"]?.copy(isGranted = accessibilityGranted)
            ?: PermissionState(accessibilityGranted, false, "접근성 서비스")
        
        val batteryGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            (getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } else true
        updatedStates["BATTERY_OPTIMIZATION"] = updatedStates["BATTERY_OPTIMIZATION"]?.copy(isGranted = batteryGranted)
            ?: PermissionState(batteryGranted, false, "배터리 최적화 제외")
        
        permissionStates = updatedStates
    }
    
    private fun moveToNextStep() {
        when (currentStep) {
            SetupStep.WELCOME -> {
                currentStep = SetupStep.BASIC_PERMISSIONS
            }
            SetupStep.BASIC_PERMISSIONS -> {
                currentStep = SetupStep.SPECIAL_PERMISSIONS
            }
            SetupStep.SPECIAL_PERMISSIONS -> {
                currentStep = SetupStep.COMPLETE
            }
            SetupStep.COMPLETE -> {
                // 이미 완료됨
            }
        }
    }
    
    private fun requestBasicPermissions() {
        // 아직 허용되지 않은 모든 권한을 한번에 요청
        val ungrantedPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        if (ungrantedPermissions.isNotEmpty()) {
            // ActivityCompat.requestPermissions()를 사용하여 진짜 한번에 요청
            // 이렇게 하면 Android가 권한들을 최대한 그룹화하여 표시
            ActivityCompat.requestPermissions(
                this,
                ungrantedPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        } else {
            // 모든 기본 권한이 이미 허용됨 - 배터리 최적화로 진행
            requestBatteryOptimization()
        }
    }
    
    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            overlayPermissionLauncher.launch(intent)
        }
    }
    
    private fun requestAccessibilityPermission() {
        try {
            // 방법 1: 특정 접근성 서비스 설정 화면으로 직접 이동 (오버레이처럼)
            val componentName = "$packageName/${PTTAccessibilityService::class.java.name}"
            val intent = Intent().apply {
                action = "android.settings.ACCESSIBILITY_SERVICE_SETTINGS"
                putExtra("android.intent.extra.COMPONENT_NAME", componentName)
                putExtra(":settings:fragment_args_key", componentName)
                putExtra(":android:show_fragment_args", Bundle().apply {
                    putString("component_name", componentName)
                })
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            
            accessibilitySettingsLauncher.launch(intent)
            
            android.widget.Toast.makeText(
                this, 
                "토글 스위치를 켜서 접근성 서비스를 활성화해주세요", 
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
        } catch (e: Exception) {
            Log.w(TAG, "Direct accessibility service failed, trying alternative", e)
            
            try {
                // 방법 2: 설정 쿼리 파라미터를 사용한 직접 이동
                val componentName = "$packageName/${PTTAccessibilityService::class.java.name}"
                val intent = Intent().apply {
                    action = Settings.ACTION_ACCESSIBILITY_SETTINGS
                    data = Uri.parse("content://com.android.settings/accessibility_service?component=$componentName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                accessibilitySettingsLauncher.launch(intent)
                
                android.widget.Toast.makeText(
                    this, 
                    "콜 매니저 PTT 토글을 활성화해주세요", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                
            } catch (e2: Exception) {
                Log.w(TAG, "Alternative method failed, using general settings", e2)
                
                // 방법 3: 일반 접근성 설정
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                accessibilitySettingsLauncher.launch(intent)
                
                android.widget.Toast.makeText(
                    this, 
                    "다운로드한 앱에서 '콜 매니저 PTT'를 찾아 토글을 켜주세요", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    private fun showAccessibilityManualGuide() {
        AlertDialog.Builder(this)
            .setTitle("접근성 서비스 설정")
            .setMessage("PTT 볼륨키 기능을 위해 접근성 서비스를 활성화해야 합니다.\n\n수동으로 설정하시려면:\n1. 설정 > 접근성\n2. 다운로드한 앱 > 콜 매니저 PTT\n3. 토글 스위치 켜기")
            .setPositiveButton("설정으로 이동") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_SETTINGS)
                    accessibilitySettingsLauncher.launch(intent)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(this, "설정 앱을 수동으로 열어주세요", android.widget.Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            
            if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // 이미 설정되어 있음
                android.widget.Toast.makeText(this, "이미 배터리 최적화가 제외되어 있습니다", android.widget.Toast.LENGTH_SHORT).show()
                updateBatteryOptimizationState()
                return
            }
            
            try {
                // 직접 배터리 최적화 제외 요청 팝업 표시 (ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 사용)
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                batteryOptimizationLauncher.launch(intent)
                
                // 팝업에서 허용/거부를 선택하라는 안내
                android.widget.Toast.makeText(
                    this, 
                    "나타나는 팝업에서 '허용' 또는 '예'를 선택해주세요", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show battery optimization popup", e)
                
                // 팝업이 실패하면 대체 다이얼로그 표시
                showBatteryOptimizationDialog()
            }
        }
    }
    
    private fun showBatteryOptimizationDialog() {
        AlertDialog.Builder(this)
            .setTitle("배터리 최적화 제외")
            .setMessage("PTT 시스템이 백그라운드에서 정상적으로 작동하려면 배터리 최적화를 제외해야 합니다.\n\n설정으로 이동하시겠습니까?")
            .setPositiveButton("설정으로 이동") { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    batteryOptimizationLauncher.launch(intent)
                    
                    android.widget.Toast.makeText(
                        this, 
                        "'콜 매니저'를 찾아서 '최적화하지 않음'으로 설정해주세요", 
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to open battery settings", e)
                    android.widget.Toast.makeText(
                        this,
                        "설정을 열 수 없습니다. 수동으로 설정 > 배터리 > 배터리 최적화에서 콜 매니저를 제외해주세요",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("나중에") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun completeSetup() {
        if (isCompleting) return
        isCompleting = true
        
        // 설정 완료 표시
        getSharedPreferences("initial_setup", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("completed", true)
            .apply()
        
        // MainActivity로 이동
        lifecycleScope.launch {
            delay(1000) // 완료 화면을 잠시 보여줌
            val intent = Intent(this@InitialSetupActivity, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun InitialSetupScreen() {
        // 주기적으로 권한 상태를 갱신
        LaunchedEffect(currentStep) {
            while (true) {
                updateAllPermissionStates()
                delay(1000) // 1초마다 갱신
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("콜 매니저 설정") }
                )
            }
        ) { paddingValues ->
            when (currentStep) {
                SetupStep.WELCOME -> WelcomeStep(
                    onNext = { moveToNextStep() },
                    modifier = Modifier.padding(paddingValues)
                )
                
                SetupStep.BASIC_PERMISSIONS -> BasicPermissionsStep(
                    permissionStates = permissionStates,
                    onRequestPermissions = { requestBasicPermissions() },
                    onRequestBatteryOptimization = { requestBatteryOptimization() },
                    onNext = { moveToNextStep() },
                    modifier = Modifier.padding(paddingValues)
                )
                
                SetupStep.SPECIAL_PERMISSIONS -> SpecialPermissionsStep(
                    permissionStates = permissionStates,
                    onRequestOverlay = { requestOverlayPermission() },
                    onRequestAccessibility = { requestAccessibilityPermission() },
                    onNext = { moveToNextStep() },
                    modifier = Modifier.padding(paddingValues)
                )
                
                SetupStep.COMPLETE -> CompleteStep(
                    permissionStates = permissionStates,
                    onFinish = { completeSetup() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
    
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val serviceName = "${packageName}/${PTTAccessibilityService::class.java.name}"
            enabledServices.contains(serviceName, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getPermissionDescription(permission: String): String {
        return when (permission) {
            Manifest.permission.POST_NOTIFICATIONS -> "알림 표시"
            Manifest.permission.READ_PHONE_STATE -> "전화 상태 읽기"
            Manifest.permission.READ_PHONE_NUMBERS -> "전화번호 읽기"
            Manifest.permission.READ_CALL_LOG -> "통화 기록 읽기"
            Manifest.permission.READ_CONTACTS -> "연락처 읽기"
            Manifest.permission.RECORD_AUDIO -> "마이크 사용 (PTT)"
            "android.permission.PROCESS_OUTGOING_CALLS" -> "발신 통화 감지"
            else -> permission
        }
    }
}

// 설정 단계
enum class SetupStep {
    WELCOME, BASIC_PERMISSIONS, SPECIAL_PERMISSIONS, COMPLETE
}

// 권한 상태 데이터 클래스
data class PermissionState(
    val isGranted: Boolean,
    val isRequired: Boolean,
    val description: String
)

@Composable
fun WelcomeStep(
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhoneAndroid,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "콜 매니저에 오신 것을 환영합니다!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = """
                원활한 서비스 이용을 위해
                필요한 권한들을 설정해보겠습니다.
                
                • 전화 콜 감지 및 관리
                • 백그라운드 알림 서비스
                • 연락처 및 통화 기록 접근
                • PTT 무전 기능 (선택)
                • 배터리 최적화 제외 (권장)
            """.trimIndent(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3
        )
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onNext,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "권한 설정 시작",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun BasicPermissionsStep(
    permissionStates: Map<String, PermissionState>,
    onRequestPermissions: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val basicPermissions = listOf(
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.RECORD_AUDIO
    ).filter { permissionStates.containsKey(it) }
    
    val allBasicGranted = basicPermissions.all { permissionStates[it]?.isGranted == true }
    val batteryOptimizationGranted = permissionStates["BATTERY_OPTIMIZATION"]?.isGranted == true
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "기본 권한 설정",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "콜 관리 서비스에 필요한 기본 권한들입니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(basicPermissions) { permission ->
                PermissionItem(
                    permission = permission,
                    state = permissionStates[permission] ?: PermissionState(false, true, "")
                )
            }
            
            // 배터리 최적화도 기본 권한에 포함
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (batteryOptimizationGranted) 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Battery6Bar,
                            contentDescription = null,
                            tint = if (batteryOptimizationGranted) 
                                MaterialTheme.colorScheme.primary 
                            else Color.Gray,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "배터리 최적화 제외",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "백그라운드 서비스가 안정적으로 작동합니다 (권장)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!batteryOptimizationGranted) {
                            TextButton(onClick = onRequestBatteryOptimization) {
                                Text("설정")
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "허용됨",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // 모든 권한 한번에 요청 버튼
        if (!allBasicGranted || !batteryOptimizationGranted) {
            val ungrantedCount = basicPermissions.count { 
                permissionStates[it]?.isGranted != true 
            }
            
            Button(
                onClick = {
                    // 모든 기본 권한을 한번의 시스템 다이얼로그로 요청
                    // 완료 후 자동으로 배터리 최적화 팝업 표시
                    onRequestPermissions()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                val buttonText = if (ungrantedCount > 0) {
                    "모든 권한 허용 (${ungrantedCount}개)"
                } else if (!batteryOptimizationGranted) {
                    "배터리 최적화 설정"
                } else {
                    "다음"
                }
                Text(buttonText)
            }
            
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("다음 단계")
            }
        }
    }
}

@Composable
fun SpecialPermissionsStep(
    permissionStates: Map<String, PermissionState>,
    onRequestOverlay: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "추가 기능 설정",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "더 나은 사용 경험을 위한 추가 설정입니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 오버레이 권한
        SpecialPermissionCard(
            title = "다른 앱 위에 표시",
            description = "콜 팝업을 다른 앱 위에 표시합니다 (필수)",
            icon = Icons.Default.OpenInNew,
            isGranted = permissionStates["OVERLAY"]?.isGranted == true,
            isRequired = true,
            onRequest = onRequestOverlay
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 접근성 서비스
        SpecialPermissionCard(
            title = "접근성 서비스 (PTT)",
            description = "볼륨키로 PTT 무전 기능을 사용합니다 (선택)",
            icon = Icons.Default.Accessibility,
            isGranted = permissionStates["ACCESSIBILITY"]?.isGranted == true,
            isRequired = false,
            onRequest = onRequestAccessibility
        )
        
        
        Spacer(modifier = Modifier.weight(1f))
        
        val overlayGranted = permissionStates["OVERLAY"]?.isGranted == true
        val accessibilityGranted = permissionStates["ACCESSIBILITY"]?.isGranted == true
        
        
        Button(
            onClick = onNext,
            enabled = overlayGranted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(if (overlayGranted) "설정 완료" else "다른 앱 위에 표시 권한 필요")
        }
        
        if (overlayGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "나머지 설정은 선택사항입니다. 언제든지 앱 설정에서 변경할 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CompleteStep(
    permissionStates: Map<String, PermissionState>,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val grantedCount = permissionStates.values.count { it.isGranted }
    val totalCount = permissionStates.size
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "설정 완료!",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "총 ${totalCount}개 권한 중 ${grantedCount}개가 설정되었습니다",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "이제 다음 기능을 사용할 수 있습니다:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val features = buildString {
                    appendLine("✓ 실시간 콜 접수 및 관리")
                    appendLine("✓ 기사 배차 및 상태 관리")
                    if (permissionStates[Manifest.permission.POST_NOTIFICATIONS]?.isGranted == true) {
                        appendLine("✓ 푸시 알림 수신")
                    }
                    if (permissionStates["OVERLAY"]?.isGranted == true) {
                        appendLine("✓ 백그라운드 콜 팝업 표시")
                    }
                    if (permissionStates["ACCESSIBILITY"]?.isGranted == true) {
                        appendLine("✓ 볼륨키 PTT 무전")
                    }
                    if (permissionStates["BATTERY_OPTIMIZATION"]?.isGranted == true) {
                        appendLine("✓ 안정적인 백그라운드 동작")
                    }
                }
                
                Text(
                    text = features.trimEnd(),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                text = "콜 매니저 시작",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun PermissionItem(
    permission: String,
    state: PermissionState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.isGranted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (state.isGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                if (state.isRequired) {
                    Text(
                        text = "필수",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Text(
                text = if (state.isGranted) "허용됨" else "대기 중",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SpecialPermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    isRequired: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (isRequired) {
                        Text(
                            text = "필수",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("설정하기")
                }
            }
        }
    }
}
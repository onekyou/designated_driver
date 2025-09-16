package com.example.calldetector

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calldetector.data.OfficeItem
import com.example.calldetector.data.RegionItem
import com.example.calldetector.ui.DetectorConfigViewModel
import com.example.calldetector.ui.DetectorConfigViewModelFactory
import com.example.calldetector.ui.ScreenState
import com.example.calldetector.ui.login.LoginScreen
import com.example.calldetector.util.CallDetectorPermissionManager
import com.google.firebase.auth.FirebaseAuth
import android.util.Log

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_SHOW_DISPATCH_POPUP = "ACTION_SHOW_DISPATCH_POPUP"
        const val EXTRA_CALL_ID = "callId"
    }

    private val tag = "MainActivity"

    // 내부 배차 다이얼로그 브로드캐스트 수신자
    private val internalDispatchReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.calldetector.INTERNAL_SHOW_DISPATCH") {
                val callId = intent.getStringExtra("EXTRA_CALL_ID")
                val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER")
                val contactName = intent.getStringExtra("EXTRA_CONTACT_NAME")
                val contactAddress = intent.getStringExtra("EXTRA_CONTACT_ADDRESS")
                val regionId = intent.getStringExtra("EXTRA_REGION_ID")
                val officeId = intent.getStringExtra("EXTRA_OFFICE_ID")
                val deviceName = intent.getStringExtra("EXTRA_DEVICE_NAME")
                
                Log.i(tag, "📞 Received internal dispatch broadcast - callId: $callId, phoneNumber: $phoneNumber")
                
                if (callId != null && phoneNumber != null) {
                    val dispatchIntent = Intent(this@MainActivity, com.example.calldetector.ui.DispatchActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                               Intent.FLAG_ACTIVITY_CLEAR_TOP or 
                               Intent.FLAG_ACTIVITY_SINGLE_TOP
                        
                        putExtra("EXTRA_CALL_ID", callId)
                        putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                        contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
                        contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
                        regionId?.let { putExtra("EXTRA_REGION_ID", it) }
                        officeId?.let { putExtra("EXTRA_OFFICE_ID", it) }
                        deviceName?.let { putExtra("EXTRA_DEVICE_NAME", it) }
                        putExtra("FROM_NEW_CALL", true)
                    }
                    startActivity(dispatchIntent)
                }
            }
        }
    }

    // 통합 권한 관리자
    private lateinit var permissionManager: CallDetectorPermissionManager
    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var sharedPreferences: SharedPreferences
    
    // 권한 상태 새로고침을 위한 콜백 저장
    private var permissionRefreshCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)
        
        // 권한 관리자 초기화
        permissionManager = CallDetectorPermissionManager(
            activity = this,
            onAllPermissionsGranted = {
                showToast("모든 권한이 허용되었습니다. 서비스를 시작합니다.")
                startCallDetectorServiceIfNeeded()
                // 권한 상태가 변경되었으므로 UI 새로고침
                refreshPermissionState()
            },
            onPermissionsDenied = { deniedPermissions ->
                Log.w(tag, "권한 거부됨: $deniedPermissions")
                // 권한 상태가 변경되었으므로 UI 새로고침
                refreshPermissionState()
            }
        )
        
        // 권한 요청 런처 등록
        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            permissionManager.onPermissionResult(permissions)
        }
        
        // 오버레이 권한 런처 등록
        overlayPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            permissionManager.onOverlayPermissionResult()
        }
        
        // 권한 관리자에 런처 등록
        permissionManager.initialize(requestPermissionsLauncher, overlayPermissionLauncher)
        
        // 내부 배차 브로드캐스트 리시버 등록
        val internalFilter = IntentFilter("com.example.calldetector.INTERNAL_SHOW_DISPATCH")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(internalDispatchReceiver, internalFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(internalDispatchReceiver, internalFilter)
        }
        
        // 초기 Intent 처리
        handleIntent(intent)

        setContent {
            CallDetectorAppTheme {
                val viewModel: DetectorConfigViewModel = viewModel(
                    factory = DetectorConfigViewModelFactory(application)
                )
                
                // 항상 로그인 페이지부터 시작
                var currentScreen by remember { 
                    mutableStateOf(ScreenState.LOGIN) 
                }

                LaunchedEffect(key1 = viewModel) {
                    viewModel.onSettingsSaved.collect { savedSuccessfully: Boolean ->
                        if (savedSuccessfully) {
                            currentScreen = ScreenState.STATUS
                            startCallDetectorServiceIfNeeded() // 서비스 시작 로직 호출
                            showToast("설정이 저장되었으며, 서비스가 활성화되었습니다.")
                        } else {
                            // MainScreen 내부의 uiState.error를 통해 메시지가 표시되므로 중복 토스트는 제거하거나 필요시 유지
                            // showToast("설정 저장에 실패했습니다. 모든 항목을 선택해주세요.") 
                        }
                    }
                }

                when (currentScreen) {
                    ScreenState.LOGIN -> LoginScreen(
                        onLoginComplete = { regionId, officeId -> 
                            // 로그인 성공시 자동 로그인 정보 저장
                            val authPrefs = getSharedPreferences("call_detector_auth", Context.MODE_PRIVATE)
                            authPrefs.edit().apply {
                                putBoolean("is_logged_in", true)
                                putString("region_id", regionId)
                                putString("office_id", officeId)
                                putLong("login_timestamp", System.currentTimeMillis())
                                remove("explicitly_logged_out") // 명시적 로그아웃 플래그 해제
                                apply()
                            }
                            
                            // 콜디텍터 설정에도 저장 (서비스에서 사용)
                            val detectorPrefs = getSharedPreferences("detector_config", Context.MODE_PRIVATE)
                            detectorPrefs.edit().apply {
                                putString("regionId", regionId)
                                putString("officeId", officeId)
                                putString("deviceName", android.os.Build.MODEL) // 기기 모델명을 deviceName으로 사용
                                apply()
                            }
                            
                            currentScreen = ScreenState.STATUS
                            startCallDetectorServiceIfNeeded() // 서비스 시작
                        },
                        onNavigateToPasswordReset = { /* 비밀번호 리셋 기능은 나중에 구현 */ }
                    )
                    ScreenState.SETTINGS -> MainScreen(viewModel = viewModel)
                    ScreenState.STATUS -> {
                        // 권한 상태를 실시간으로 반영하기 위한 상태
                        var hasAllPermissions by remember { mutableStateOf(areAllPermissionsGranted()) }
                        
                        // 권한 새로고침 콜백 등록
                        val refreshCallback = {
                            hasAllPermissions = areAllPermissionsGranted()
                        }
                        
                        // 콜백 등록 및 초기 상태 설정
                        LaunchedEffect(Unit) {
                            hasAllPermissions = areAllPermissionsGranted()
                            permissionRefreshCallback = refreshCallback
                        }
                        
                        StatusScreen(
                            hasAllPermissions = hasAllPermissions,
                            onNavigateToSettings = { currentScreen = ScreenState.SETTINGS },
                            onRequestPermissions = { 
                                permissionManager.requestAllPermissions()
                            },
                            onLogout = {
                                // 로그아웃 처리
                                val authPrefs = getSharedPreferences("call_detector_auth", Context.MODE_PRIVATE)
                                authPrefs.edit().apply {
                                    clear()
                                    putBoolean("explicitly_logged_out", true) // 명시적 로그아웃 플래그 설정
                                    apply()
                                }
                                
                                val detectorPrefs = getSharedPreferences("detector_config", Context.MODE_PRIVATE)
                                detectorPrefs.edit().clear().apply()
                                
                                // 저장된 로그인 정보만 삭제 (자동로그인 설정은 유지)
                                val loginPrefs = getSharedPreferences("call_detector_login_prefs", Context.MODE_PRIVATE)
                                loginPrefs.edit().apply {
                                    remove("email")
                                    remove("password")
                                    // auto_login 설정은 유지 (사용자가 설정한 상태 보존)
                                    apply()
                                }
                                
                                // Firebase 로그아웃 (정식 로그인 해제)
                                FirebaseAuth.getInstance().signOut()
                                
                                // CallDetectorService 중지
                                try {
                                    val serviceIntent = Intent(this@MainActivity, CallDetectorService::class.java)
                                    stopService(serviceIntent)
                                } catch (e: Exception) {
                                    Log.w(tag, "서비스 중지 중 오류: ${e.message}")
                                }
                                
                                showToast("로그아웃되었습니다. 앱을 종료합니다.")
                                
                                // 앱 완전 종료
                                finishAffinity() // 모든 Activity 종료
                                System.exit(0) // 프로세스 종료
                            },
                            onRefreshPermissions = refreshCallback
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
    
    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SHOW_DISPATCH_POPUP -> {
                val callId = intent.getStringExtra("callId") // Firebase document ID
                val phoneNumber = intent.getStringExtra("phoneNumber")
                val contactName = intent.getStringExtra("contactName")
                val contactAddress = intent.getStringExtra("contactAddress")
                val regionId = intent.getStringExtra("regionId")
                val officeId = intent.getStringExtra("officeId")
                val deviceName = intent.getStringExtra("deviceName")

                // DispatchActivity 실행 (Firebase ID 포함)
                if (phoneNumber != null && regionId != null && officeId != null) {
                    val dispatchIntent = Intent(this, com.example.calldetector.ui.DispatchActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                               Intent.FLAG_ACTIVITY_CLEAR_TOP or
                               Intent.FLAG_ACTIVITY_SINGLE_TOP

                        callId?.let { putExtra("EXTRA_CALL_ID", it) } // Firebase document ID
                        putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                        contactName?.let { putExtra("EXTRA_CONTACT_NAME", it) }
                        contactAddress?.let { putExtra("EXTRA_CONTACT_ADDRESS", it) }
                        putExtra("EXTRA_REGION_ID", regionId)
                        putExtra("EXTRA_OFFICE_ID", officeId)
                        deviceName?.let { putExtra("EXTRA_DEVICE_NAME", it) }
                        putExtra("FROM_NEW_CALL", true)
                    }
                    
                    startActivity(dispatchIntent)
                }
            }
            else -> {
                Log.w(tag, "⚠️ [DEBUG] 알 수 없는 action 또는 null: ${intent?.action}")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(tag, "🟡 [LIFECYCLE] MainActivity onStart 호출됨!")
        // 권한이 이미 모두 승인된 경우에는 요청하지 않음
        if (!areAllPermissionsGranted()) {
            permissionManager.requestAllPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(tag, "🟢 [LIFECYCLE] MainActivity onResume 호출됨!")
        // StatusScreen에서 권한 상태 새로고침 트리거
        refreshPermissionState()
    }

    override fun onPause() {
        super.onPause()
        Log.d(tag, "🟠 [LIFECYCLE] MainActivity onPause 호출됨!")
    }

    override fun onStop() {
        super.onStop()
        Log.d(tag, "🔴 [LIFECYCLE] MainActivity onStop 호출됨!")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(internalDispatchReceiver)
        } catch (e: Exception) {
            Log.w(tag, "Error unregistering broadcast receiver: ${e.message}")
        }
    }

    override fun onBackPressed() {
        // 뒤로가기 버튼으로 종료 방지, 대신 백그라운드로 이동
        moveTaskToBack(true)
    }

    private fun areAllPermissionsGranted(): Boolean {
        return permissionManager.areAllRequiredPermissionsGranted()
    }
    
    private fun refreshPermissionState() {
        permissionRefreshCallback?.invoke()
    }

    // CallDetectorService 시작 (필요한 경우)
    private fun startCallDetectorServiceIfNeeded() {
        if (areAllPermissionsGranted()) {
            // 설정값이 저장되어 있는지 확인 (예: deviceName)
            val deviceName = sharedPreferences.getString("deviceName", null)
            if (!deviceName.isNullOrBlank()) {
                val serviceIntent = Intent(this, CallDetectorService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                // showToast("콜디텍터 서비스 시작됨") // 필요시 사용자에게 알림
            } else {
                // showToast("디바이스 설정이 완료되지 않아 서비스를 시작할 수 없습니다.")
            }
        } else {
            // showToast("필수 권한이 없어 서비스를 시작할 수 없습니다.")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: DetectorConfigViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // MainScreen 재구성 시 상태 로깅
    Log.d("MainScreen", "Recomposing: selectedRegion=${uiState.selectedRegion?.name}, isLoadingOffices=${uiState.isLoadingOffices}, officesCount=${uiState.offices.size}, selectedOffice=${uiState.selectedOffice?.name}")

    val isOfficeDropdownEnabled = uiState.selectedRegion != null && !uiState.isLoadingOffices
    Log.d("MainScreen", "isOfficeDropdownEnabled: $isOfficeDropdownEnabled")

    // 저장 성공 또는 오류 메시지 표시 (ViewModel의 uiState 사용)
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            // 화면 전환은 MainActivity의 LaunchedEffect에서 처리하므로 여기서는 추가 토스트 불필요
            viewModel.resetSaveStatus() // ViewModel의 상태 초기화
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError() // ViewModel의 오류 상태 초기화
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("콜디텍터 설정", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212) // 다크 배경
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 지역 선택 드롭다운
            var regionExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = regionExpanded,
                onExpandedChange = { regionExpanded = !regionExpanded }
            ) {
                OutlinedTextField(
                    value = uiState.selectedRegion?.name ?: "지역 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("지역", color = Color(0xFFB0B0B0)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                    modifier = Modifier
                        .menuAnchor() // 필수
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFB000),
                        unfocusedBorderColor = Color(0xFF404040),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFFB000),
                        focusedTrailingIconColor = Color(0xFFFFB000),
                        unfocusedTrailingIconColor = Color(0xFFB0B0B0)
                    )
                )
                ExposedDropdownMenu(
                    expanded = regionExpanded,
                    onDismissRequest = { regionExpanded = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    if (uiState.isLoadingRegions) {
                        DropdownMenuItem(
                            text = { Text("지역 정보 로딩 중...", color = Color.White) },
                            onClick = {},
                            enabled = false
                        )
                    } else if (uiState.regions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("사용 가능한 지역 없음", color = Color.White) },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        uiState.regions.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region.name, color = Color.White) },
                                onClick = {
                                    viewModel.selectRegion(region)
                                    regionExpanded = false
                                    focusManager.clearFocus() // 키보드 숨기기
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "서비스를 제공할 지역을 선택하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B0B0),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 사무실 선택 드롭다운
            var officeExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = officeExpanded && isOfficeDropdownEnabled,
                onExpandedChange = {
                    // 현재 uiState를 직접 사용하여 확장 가능 여부를 판단합니다.
                    val canExpandNow = uiState.selectedRegion != null && !uiState.isLoadingOffices
                    Log.d("MainScreen", "Office ExposedDropdownMenuBox onExpandedChange. Captured isOfficeDropdownEnabled: $isOfficeDropdownEnabled, Evaluated canExpandNow: $canExpandNow")
                    if (canExpandNow) { // 캡처된 변수 대신 직접 평가한 값 사용
                        officeExpanded = !officeExpanded
                        Log.d("MainScreen", "Office officeExpanded toggled to: $officeExpanded")
                    } else {
                        Log.d("MainScreen", "Office dropdown not expanded because canExpandNow is false (or captured isOfficeDropdownEnabled was false).")
                    }
                }
            ) {
                OutlinedTextField(
                    value = uiState.selectedOffice?.name ?: "사무실 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("사무실", color = Color(0xFFB0B0B0)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = officeExpanded && isOfficeDropdownEnabled) },
                    enabled = isOfficeDropdownEnabled,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFB000),
                        unfocusedBorderColor = Color(0xFF404040),
                        disabledBorderColor = Color(0xFF404040),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color(0xFF808080),
                        cursorColor = Color(0xFFFFB000),
                        focusedTrailingIconColor = Color(0xFFFFB000),
                        unfocusedTrailingIconColor = Color(0xFFB0B0B0),
                        disabledTrailingIconColor = Color(0xFF808080)
                    )
                )
                ExposedDropdownMenu(
                    expanded = officeExpanded && isOfficeDropdownEnabled,
                    onDismissRequest = { officeExpanded = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    if (uiState.isLoadingOffices && uiState.selectedRegion != null) {
                         DropdownMenuItem(
                            text = { Text("사무실 정보 로딩 중...", color = Color.White) },
                            onClick = {},
                            enabled = false
                        )
                    } else if (uiState.offices.isEmpty() && uiState.selectedRegion != null) {
                         DropdownMenuItem(
                            text = { Text("선택한 지역에 사무실 없음", color = Color.White) },
                            onClick = {},
                            enabled = false
                        )
                    } else {
                        uiState.offices.forEach { office ->
                            DropdownMenuItem(
                                text = { Text(office.name, color = Color.White) },
                                onClick = {
                                    viewModel.selectOffice(office)
                                    officeExpanded = false
                                    focusManager.clearFocus()
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "선택한 지역 내의 사무실을 선택하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B0B0),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 전화기 이름 드롭다운 메뉴
            var deviceNameExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = deviceNameExpanded,
                onExpandedChange = { deviceNameExpanded = !deviceNameExpanded }
            ) {
                OutlinedTextField(
                    value = uiState.selectedDeviceName.ifEmpty { "전화기 번호 선택" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("전화기 번호", color = Color(0xFFB0B0B0)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceNameExpanded) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFB000),
                        unfocusedBorderColor = Color(0xFF404040),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFFFFB000),
                        focusedTrailingIconColor = Color(0xFFFFB000),
                        unfocusedTrailingIconColor = Color(0xFFB0B0B0)
                    )
                )
                ExposedDropdownMenu(
                    expanded = deviceNameExpanded,
                    onDismissRequest = { deviceNameExpanded = false },
                    modifier = Modifier.background(Color(0xFF2A2A2A))
                ) {
                    uiState.availableDeviceNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name, color = Color.White) },
                            onClick = {
                                viewModel.selectDeviceName(name)
                                deviceNameExpanded = false
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "이 전화기를 식별할 수 있는 번호를 선택하세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB0B0B0),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.saveSelection()
                },
                enabled = !uiState.isLoadingRegions && !uiState.isLoadingOffices, // 로딩 중 아닐 때만 활성화
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB000),
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF404040),
                    disabledContentColor = Color(0xFF808080)
                )
            ) {
                Text("설정 저장", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = uiState.saveSuccess.takeIf { it }?.let { "저장 완료! 앱이 활성화되었습니다." } ?: "저장 후 앱이 백그라운드에서 실행되며 통화를 감지합니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.saveSuccess) Color(0xFF4CAF50) else Color(0xFFB0B0B0)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(
    hasAllPermissions: Boolean,
    onNavigateToSettings: () -> Unit,
    onRequestPermissions: () -> Unit,
    onLogout: () -> Unit,
    onRefreshPermissions: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("콜디텍터 상태", color = Color.White) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "설정으로 이동",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212) // 다크 배경
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (hasAllPermissions) "콜디텍터 서비스 활성화 중" else "권한 확인 필요",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (hasAllPermissions) 
                    "백그라운드에서 통화 감지가 실행되고 있습니다." 
                else 
                    "정상적인 동작을 위해 필수 권한이 필요합니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB0B0B0) // 연한 회색
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            // 상태 표시 카드 추가
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2A2A2A)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (hasAllPermissions) "🟢 서비스 실행 중" else "⚠️ 권한 필요",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (hasAllPermissions) Color(0xFF4CAF50) else Color(0xFFFF9800) // 녹색 또는 주황색
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (hasAllPermissions)
                            "통화 감지 및 자동 업로드 기능이 활성화되어 있습니다."
                        else
                            "전화 감지 권한이 필요합니다. 권한을 허용해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B0B0),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 권한 요청 버튼 (권한이 없을 때만 표시)
            if (!hasAllPermissions) {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800), // 주황색
                        contentColor = Color.White
                    )
                ) {
                    Text("🔐 권한 허용하기", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 개인번호 관리 버튼 추가
            val context = LocalContext.current
            Button(
                onClick = {
                    val intent = Intent(context, ExcludeNumberActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3), // 파란색
                    contentColor = Color.White
                )
            ) {
                Text("📱 개인번호 관리", style = MaterialTheme.typography.titleMedium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 로그아웃 버튼 추가
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F), // 빨간색
                    contentColor = Color.White
                )
            ) {
                Text("로그아웃", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun CallDetectorAppTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFFFB000), // 딥 옐로우
        onPrimary = Color.Black,
        primaryContainer = Color(0xFF2A2A2A),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF03DAC6),
        onSecondary = Color.Black,
        background = Color(0xFF121212),
        onBackground = Color.White,
        surface = Color(0xFF1E1E1E),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2A2A2A),
        onSurfaceVariant = Color(0xFFB0B0B0),
        outline = Color(0xFF404040),
        error = Color(0xFFCF6679),
        onError = Color.Black
    )
    
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}
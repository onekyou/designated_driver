package com.example.calldetector

import android.Manifest
import android.content.Context
import android.content.Intent
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
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.SEND_SMS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.SEND_SMS
        )
    }

    private lateinit var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)

        requestPermissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                showToast("모든 필수 권한이 허용되었습니다.")
                startCallDetectorServiceIfNeeded()
            } else {
                showToast("일부 필수 권한이 거부되었습니다. 앱 기능이 제한될 수 있습니다.")
            }
        }

        setContent {
            CallDetectorAppTheme {
                val viewModel: DetectorConfigViewModel = viewModel(
                    factory = DetectorConfigViewModelFactory(application)
                )

                val isLoggedIn = remember {
                    val prefs = getSharedPreferences("call_detector_auth", Context.MODE_PRIVATE)
                    prefs.getBoolean("is_logged_in", false) &&
                    prefs.getString("region_id", null) != null &&
                    prefs.getString("office_id", null) != null
                }

                var currentScreen by remember {
                    mutableStateOf(if (isLoggedIn) ScreenState.STATUS else ScreenState.LOGIN)
                }

                LaunchedEffect(isLoggedIn) {
                    if (isLoggedIn) {
                        val authPrefs = getSharedPreferences("call_detector_auth", Context.MODE_PRIVATE)
                        val regionId = authPrefs.getString("region_id", null)
                        val officeId = authPrefs.getString("office_id", null)

                        if (regionId != null && officeId != null) {
                            val detectorPrefs = getSharedPreferences("detector_config", Context.MODE_PRIVATE)
                            detectorPrefs.edit().apply {
                                putString("regionId", regionId)
                                putString("officeId", officeId)
                                putString("deviceName", android.os.Build.MODEL)
                                apply()
                            }
                        }

                        startCallDetectorServiceIfNeeded()
                    }
                }

                LaunchedEffect(key1 = viewModel) {
                    viewModel.onSettingsSaved.collect { savedSuccessfully: Boolean ->
                        if (savedSuccessfully) {
                            currentScreen = ScreenState.STATUS
                            startCallDetectorServiceIfNeeded()
                            showToast("설정이 저장되었으며, 서비스가 활성화되었습니다.")
                        } else {
                            // showToast("설정 저장에 실패했습니다. 모든 항목을 선택해주세요.")
                        }
                    }
                }

                when (currentScreen) {
                    ScreenState.LOGIN -> LoginScreen(
                        onLoginComplete = { regionId, officeId ->
                            val authPrefs = getSharedPreferences("call_detector_auth", Context.MODE_PRIVATE)
                            authPrefs.edit().apply {
                                putBoolean("is_logged_in", true)
                                putString("region_id", regionId)
                                putString("office_id", officeId)
                                putLong("login_timestamp", System.currentTimeMillis())
                                apply()
                            }

                            val detectorPrefs = getSharedPreferences("detector_config", Context.MODE_PRIVATE)
                            detectorPrefs.edit().apply {
                                putString("regionId", regionId)
                                putString("officeId", officeId)
                                putString("deviceName", android.os.Build.MODEL)
                                apply()
                            }

                            currentScreen = ScreenState.STATUS
                            startCallDetectorServiceIfNeeded()
                        },
                        onNavigateToPasswordReset = { /* 비밀번호 리셋 기능은 나중에 구현 */ }
                    )
                    ScreenState.SETTINGS -> MainScreen(viewModel = viewModel)
                    ScreenState.STATUS -> StatusScreen(
                        onNavigateToSettings = { currentScreen = ScreenState.SETTINGS },
                        onLogout = {
                            val authPrefs = getSharedPreferences("call_detector_auth", Context.MODE_PRIVATE)
                            authPrefs.edit().clear().apply()

                            val detectorPrefs = getSharedPreferences("detector_config", Context.MODE_PRIVATE)
                            detectorPrefs.edit().clear().apply()

                            FirebaseAuth.getInstance().signOut()
                            CallDetectorApplication.setLogoutState(this@MainActivity, true)

                            currentScreen = ScreenState.LOGIN
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
    }

    private fun areAllPermissionsGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun checkAndRequestPermissions() {
        if (!areAllPermissionsGranted()) {
            requestPermissionsLauncher.launch(requiredPermissions)
        }
    }

    private fun startCallDetectorServiceIfNeeded() {
        if (areAllPermissionsGranted()) {
            val deviceName = sharedPreferences.getString("deviceName", null)
            if (!deviceName.isNullOrBlank()) {
                val serviceIntent = Intent(this, CallDetectorService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                // showToast("콜디텍터 서비스 시작됨")
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

    val isOfficeDropdownEnabled = uiState.selectedRegion != null && !uiState.isLoadingOffices

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            viewModel.resetSaveStatus()
        }
    }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
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
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
                                    focusManager.clearFocus()
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

            var officeExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = officeExpanded && isOfficeDropdownEnabled,
                onExpandedChange = {
                    val canExpandNow = uiState.selectedRegion != null && !uiState.isLoadingOffices
                    if (canExpandNow) {
                        officeExpanded = !officeExpanded
                    } else {
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
                enabled = !uiState.isLoadingRegions && !uiState.isLoadingOffices,
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
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit
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
        containerColor = Color(0xFF121212)
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
                text = "콜디텍터 서비스 활성화 중",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "백그라운드에서 통화 감지가 실행되고 있습니다.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB0B0B0)
            )
            Spacer(modifier = Modifier.height(32.dp))

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
                        text = "🟢 서비스 실행 중",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "통화 감지 및 자동 업로드 기능이 활성화되어 있습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFB0B0B0),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val context = LocalContext.current
            Button(
                onClick = {
                    val intent = Intent(context, ExcludeNumberActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3),
                    contentColor = Color.White
                )
            ) {
                Text("📱 개인번호 관리", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F),
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
        primary = Color(0xFFFFB000),
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
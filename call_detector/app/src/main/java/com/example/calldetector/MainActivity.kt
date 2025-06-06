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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calldetector.data.OfficeItem
import com.example.calldetector.data.RegionItem
import com.example.calldetector.ui.DetectorConfigViewModel
import com.example.calldetector.ui.DetectorConfigViewModelFactory
import com.example.calldetector.ui.ScreenState
import android.util.Log

class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
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
                
                var currentScreen by remember { mutableStateOf(ScreenState.SETTINGS) }

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
                    ScreenState.SETTINGS -> MainScreen(viewModel = viewModel)
                    ScreenState.STATUS -> StatusScreen(
                        onNavigateToSettings = { currentScreen = ScreenState.SETTINGS }
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
        // 필요한 경우 여기서도 권한 확인 및 서비스 시작 로직을 추가할 수 있습니다.
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "콜디텍터 설정",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

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
                label = { Text("지역") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded) },
                modifier = Modifier
                    .menuAnchor() // 필수
                    .fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = regionExpanded,
                onDismissRequest = { regionExpanded = false }
            ) {
                if (uiState.isLoadingRegions) {
                    DropdownMenuItem(
                        text = { Text("지역 정보 로딩 중...") },
                        onClick = {},
                        enabled = false
                    )
                } else if (uiState.regions.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("사용 가능한 지역 없음") },
                        onClick = {},
                        enabled = false
                    )
                } else {
                    uiState.regions.forEach { region ->
                        DropdownMenuItem(
                            text = { Text(region.name) },
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
                label = { Text("사무실") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = officeExpanded && isOfficeDropdownEnabled) },
                enabled = isOfficeDropdownEnabled,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = officeExpanded && isOfficeDropdownEnabled,
                onDismissRequest = { officeExpanded = false }
            ) {
                if (uiState.isLoadingOffices && uiState.selectedRegion != null) {
                     DropdownMenuItem(
                        text = { Text("사무실 정보 로딩 중...") },
                        onClick = {},
                        enabled = false
                    )
                } else if (uiState.offices.isEmpty() && uiState.selectedRegion != null) {
                     DropdownMenuItem(
                        text = { Text("선택한 지역에 사무실 없음") },
                        onClick = {},
                        enabled = false
                    )
                } else {
                    uiState.offices.forEach { office ->
                        DropdownMenuItem(
                            text = { Text(office.name) },
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
                label = { Text("전화기 번호") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceNameExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = deviceNameExpanded,
                onDismissRequest = { deviceNameExpanded = false }
            ) {
                uiState.availableDeviceNames.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
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
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                viewModel.saveSelection()
            },
            enabled = !uiState.isLoadingRegions && !uiState.isLoadingOffices, // 로딩 중 아닐 때만 활성화
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("설정 저장")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = uiState.saveSuccess.takeIf { it }?.let { "저장 완료! 앱이 활성화되었습니다." } ?: "저장 후 앱이 백그라운드에서 실행되며 통화를 감지합니다.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (uiState.saveSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusScreen(onNavigateToSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("콜디텍터 상태") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "설정으로 이동"
                        )
                    }
                }
            )
        }
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
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "백그라운드에서 통화 감지가 실행되고 있습니다.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun CallDetectorAppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        content()
    }
}
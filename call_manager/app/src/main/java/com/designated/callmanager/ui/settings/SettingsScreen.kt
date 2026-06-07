package com.designated.callmanager.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.Constants
import com.designated.callmanager.ui.dashboard.DashboardViewModel
import com.designated.callmanager.ui.settlement.SettlementViewModel
import com.designated.callmanager.ui.settlement.SettlementViewModel.BackupState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dashboardViewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPendingDrivers: (provinceId: String, cityId: String, officeId: String) -> Unit,
    onNavigateToSettlement: () -> Unit,
    onNavigateToExcludeNumber: () -> Unit = {},
    onNavigateToCustomerManagement: (provinceId: String, cityId: String, officeId: String) -> Unit = { _, _, _ -> },
    onNavigateToAttributionManagement: (provinceId: String, cityId: String, officeId: String) -> Unit = { _, _, _ -> },
    onNavigateToReservationTest: () -> Unit = {},
    onNavigateToReservationInbox: () -> Unit = {},
) {
    val settlementViewModel: SettlementViewModel = viewModel()
    val context = LocalContext.current
    val officeStatus by dashboardViewModel.officeStatus.collectAsStateWithLifecycle()
    val provinceId by dashboardViewModel.provinceId.collectAsStateWithLifecycle()
    val cityId by dashboardViewModel.cityId.collectAsStateWithLifecycle()
    val officeId by dashboardViewModel.officeId.collectAsStateWithLifecycle()

    // 백업 관련 상태
    val backupState by settlementViewModel.backupState.collectAsStateWithLifecycle()
    val hasCloudBackups by settlementViewModel.hasCloudBackups.collectAsStateWithLifecycle()
    val settlementList by settlementViewModel.settlementList.collectAsStateWithLifecycle()

    // 백업 다이얼로그 상태
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }

    // 백업 체크
    LaunchedEffect(provinceId, cityId, officeId) {
        if (provinceId != null && cityId != null && officeId != null) {
            settlementViewModel.checkCloudBackups()
        }
    }

    // 알림 설정
    val prefs = remember { context.getSharedPreferences("call_manager_settings", Context.MODE_PRIVATE) }
    var newCallNotificationEnabled by remember { mutableStateOf(prefs.getBoolean("new_call_notification", true)) }
    var driverEventNotificationEnabled by remember { mutableStateOf(prefs.getBoolean("driver_event_notification", true)) }

    // 콜디텍터 설정 (최초 설치 시 기본값 true)
    val callPrefs = remember { context.getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE) }
    var callDetectionEnabled by remember { mutableStateOf(callPrefs.getBoolean("call_detection_enabled", true)) }
    var callDetectorServiceStatus by remember { mutableStateOf("확인 중...") }
    var isFirstTime by remember { mutableStateOf(!callPrefs.contains("call_detection_enabled")) }

    LaunchedEffect(Unit) {
        // 권한 확인
        val hasReadCallLog = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasReadPhoneState = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED


        // 최초 설치 시 기본값 설정 및 서비스 시작
        if (isFirstTime) {
            callPrefs.edit().putBoolean("call_detection_enabled", true).apply()

            if (!hasReadCallLog || !hasReadPhoneState) {
                callDetectorServiceStatus = "권한 필요 ⚠️"
            } else {
                try {
                    val intent = android.content.Intent(context, com.designated.callmanager.service.CallDetectorService::class.java)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                    val isRunning = com.designated.callmanager.service.CallDetectorService.isServiceActuallyRunning(context)
                    callDetectorServiceStatus = if (isRunning) {
                        "실행 중 ✅"
                    } else {
                        "중지됨 ⚠️"
                    }
                } catch (e: Exception) {
                    callDetectorServiceStatus = "시작 실패 ❌"
                }
            }
        } else {
            callDetectorServiceStatus = if (callDetectionEnabled) {
                if (!hasReadCallLog || !hasReadPhoneState) {
                    "권한 필요 ⚠️"
                } else if (com.designated.callmanager.service.CallDetectorService.isServiceActuallyRunning(context)) {
                    "실행 중 ✅"
                } else {
                    try {
                        val intent = android.content.Intent(context, com.designated.callmanager.service.CallDetectorService::class.java)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                        val isRunning = com.designated.callmanager.service.CallDetectorService.isServiceActuallyRunning(context)
                        if (isRunning) {
                            "실행 중 ✅"
                        } else {
                            "중지됨 ⚠️"
                        }
                    } catch (e: Exception) {
                        "시작 실패 ❌"
                    }
                }
            } else {
                "비활성화됨 ❌"
            }
        }
    }

    LaunchedEffect(callDetectionEnabled) {
        val hasReadCallLog = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasReadPhoneState = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_PHONE_STATE
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        callDetectorServiceStatus = if (callDetectionEnabled) {
            if (!hasReadCallLog || !hasReadPhoneState) {
                "권한 필요 ⚠️"
            } else if (com.designated.callmanager.service.CallDetectorService.isServiceActuallyRunning(context)) {
                "실행 중 ✅"
            } else {
                "중지됨 ⚠️"
            }
        } else {
            "비활성화됨 ❌"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // 사무실 상태 섹션
            SettingsSection(
                title = "사무실 관리",
                icon = Icons.Filled.Settings
            ) {
                SettingsToggleItem(
                    title = "사무실 콜 공유 상태",
                    description = if (officeStatus != Constants.OFFICE_STATUS_CLOSED_SHARING)
                        "현재 '운영 중' 상태입니다. 콜은 내부에서 처리됩니다."
                    else "현재 '마감(공유 중)' 상태입니다. 콜은 공유 채널로 전송됩니다.",
                    checked = officeStatus == Constants.OFFICE_STATUS_CLOSED_SHARING,
                    onCheckedChange = { isChecked ->
                        val newStatus = if (isChecked) Constants.OFFICE_STATUS_CLOSED_SHARING else Constants.OFFICE_STATUS_OPERATING
                        dashboardViewModel.updateOfficeStatus(newStatus)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 관리 메뉴 섹션
            SettingsSection(
                title = "관리",
                icon = Icons.Filled.Settings
            ) {
                SettingsNavigationItem(
                    title = "정산 관리",
                    description = "수익 배분, 지출 관리",
                    icon = Icons.Filled.Payments,
                    onClick = onNavigateToSettlement
                )

                SettingsNavigationItem(
                    title = "제외번호 관리",
                    description = "개인번호 필터링",
                    icon = Icons.Filled.Radio,
                    onClick = onNavigateToExcludeNumber
                )

                SettingsNavigationItem(
                    title = "기사 가입 승인",
                    description = "대기 중인 기사 승인",
                    icon = Icons.Filled.PersonAdd,
                    onClick = {
                        if (provinceId != null && cityId != null && officeId != null) {
                            onNavigateToPendingDrivers(provinceId!!, cityId!!, officeId!!)
                        }
                    }
                )

                SettingsNavigationItem(
                    title = "손님 관리",
                    description = "고객 정보, 포인트 관리",
                    icon = Icons.Filled.People,
                    onClick = {
                        if (provinceId != null && cityId != null && officeId != null) {
                            onNavigateToCustomerManagement(provinceId!!, cityId!!, officeId!!)
                        }
                    }
                )

                SettingsNavigationItem(
                    title = "어트리뷰션 관리",
                    description = "QR 코드, KPI 모니터링",
                    icon = Icons.Filled.Analytics,
                    onClick = {
                        if (provinceId != null && cityId != null && officeId != null) {
                            onNavigateToAttributionManagement(provinceId!!, cityId!!, officeId!!)
                        }
                    }
                )

                SettingsNavigationItem(
                    title = "통화로 예약 입력",
                    description = "통화녹음 → AI 분석 → 확인 → 콜 등록",
                    icon = Icons.Filled.PhoneAndroid,
                    onClick = onNavigateToReservationInbox
                )

                SettingsNavigationItem(
                    title = "녹음 파싱 검증 (beta)",
                    description = "통화녹음 → 예약 자동추출 측정",
                    icon = Icons.Filled.Info,
                    onClick = onNavigateToReservationTest
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 백업/복원 섹션
            SettingsSection(
                title = "데이터 백업 (준비중)",
                icon = Icons.Filled.Backup
            ) {
                SettingsActionItem(
                    title = "정산 데이터 백업",
                    description = "준비중입니다",
                    icon = Icons.Filled.CloudUpload,
                    enabled = false,
                    onClick = { /* 비활성화 */ }
                )

                SettingsActionItem(
                    title = "정산 데이터 복원",
                    description = "준비중입니다",
                    icon = Icons.Filled.CloudDownload,
                    enabled = false,
                    onClick = { /* 비활성화 */ }
                )

                // 백업 상태 표시
                when (backupState) {
                    is BackupState.Loading -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "처리 중...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is BackupState.Success -> {
                        val successState = backupState as BackupState.Success
                        Text(
                            text = "✅ ${successState.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LaunchedEffect(successState) {
                            delay(3000)
                            settlementViewModel.clearBackupState()
                        }
                    }
                    is BackupState.Error -> {
                        val errorState = backupState as BackupState.Error
                        Text(
                            text = "❌ ${errorState.error}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        LaunchedEffect(errorState) {
                            delay(5000)
                            settlementViewModel.clearBackupState()
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 알림 설정 섹션
            SettingsSection(
                title = "알림 설정",
                icon = Icons.Filled.NotificationsActive
            ) {
                SettingsToggleItem(
                    title = "새 콜 알림음",
                    description = "새로운 콜 접수 시 알림음 재생",
                    checked = newCallNotificationEnabled,
                    onCheckedChange = { isChecked ->
                        newCallNotificationEnabled = isChecked
                        prefs.edit().putBoolean("new_call_notification", isChecked).apply()
                    }
                )

                SettingsToggleItem(
                    title = "기사 이벤트 알림",
                    description = "출/퇴근, 승인 등 기사 관련 알림",
                    checked = driverEventNotificationEnabled,
                    onCheckedChange = { isChecked ->
                        driverEventNotificationEnabled = isChecked
                        prefs.edit().putBoolean("driver_event_notification", isChecked).apply()
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 콜디텍터 설정 섹션
            SettingsSection(
                title = "콜디텍터 설정",
                icon = Icons.Filled.PhoneAndroid
            ) {
                SettingsToggleItem(
                    title = "전화 감지 기능",
                    description = if (callDetectionEnabled) "수신 전화를 자동으로 Firebase에 저장" else "전화 감지 기능이 비활성화됨",
                    statusText = "상태: $callDetectorServiceStatus",
                    statusColor = when {
                        callDetectorServiceStatus.contains("실행 중") -> MaterialTheme.colorScheme.primary
                        callDetectorServiceStatus.contains("중지됨") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    checked = callDetectionEnabled,
                    onCheckedChange = { isChecked ->
                        callDetectionEnabled = isChecked
                        callPrefs.edit().putBoolean("call_detection_enabled", isChecked).apply()

                        val hasReadCallLog = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_CALL_LOG
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        val hasReadPhoneState = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_PHONE_STATE
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (isChecked) {
                            if (!hasReadCallLog || !hasReadPhoneState) {
                                callDetectorServiceStatus = "권한 필요 ⚠️"
                            } else {
                                try {
                                    val intent = android.content.Intent(context, com.designated.callmanager.service.CallDetectorService::class.java)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                    callDetectorServiceStatus = if (com.designated.callmanager.service.CallDetectorService.isServiceActuallyRunning(context)) {
                                        "실행 중 ✅"
                                    } else {
                                        "중지됨 ⚠️"
                                    }
                                } catch (e: Exception) {
                                    callDetectorServiceStatus = "시작 실패 ❌"
                                }
                            }
                        } else {
                            try {
                                val intent = android.content.Intent(context, com.designated.callmanager.service.CallDetectorService::class.java)
                                context.stopService(intent)
                                callDetectorServiceStatus = "비활성화됨 ❌"
                            } catch (e: Exception) {
                                callDetectorServiceStatus = "중지 실패 ⚠️"
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 앱 정보 섹션
            SettingsSection(
                title = "앱 정보",
                icon = Icons.Filled.Info
            ) {
                SettingsInfoItem(
                    label = "버전",
                    value = "1.0.0 (Beta) - 콜디텍터 내장형"
                )

                SettingsInfoItem(
                    label = "앱명",
                    value = "대리운전 콜 매니저"
                )

                SettingsInfoItem(
                    label = "지역",
                    value = provinceId ?: "미설정"
                )

                SettingsInfoItem(
                    label = "사무실 ID",
                    value = officeId ?: "미설정"
                )

                SettingsInfoItem(
                    label = "콜디텍터",
                    value = if (callDetectionEnabled) "활성화" else "비활성화"
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // 백업 확인 다이얼로그
    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("정산 데이터 백업") },
            text = {
                Text("${settlementList.size}건의 정산 데이터를 클라우드에 백업하시겠습니까?\n\n백업된 데이터는 앱을 삭제하거나 재설치해도 복원할 수 있습니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBackupDialog = false
                        settlementViewModel.backupSettlements()
                    }
                ) {
                    Text("백업")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) {
                    Text("취소")
                }
            }
        )
    }

    // 복원 확인 다이얼로그
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("정산 데이터 복원") },
            text = {
                Text("클라우드에서 정산 데이터를 복원하시겠습니까?\n\n현재 로컬 정산 데이터는 모두 삭제되고 클라우드 데이터로 대체됩니다.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDialog = false
                        settlementViewModel.restoreSettlements()
                    }
                ) {
                    Text("복원")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            content()
        }
    }
}

@Composable
private fun SettingsToggleItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    statusText: String? = null,
    statusColor: androidx.compose.ui.graphics.Color? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (statusText != null && statusColor != null) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsNavigationItem(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp)
            .let { if (!enabled) it.alpha(0.5f) else it },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun SettingsInfoItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
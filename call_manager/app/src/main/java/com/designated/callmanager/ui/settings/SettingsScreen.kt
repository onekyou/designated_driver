package com.designated.callmanager.ui.settings

import android.app.Application
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.Constants
import com.designated.callmanager.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    dashboardViewModel: DashboardViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPendingDrivers: (regionId: String, officeId: String) -> Unit,
    onNavigateToSettlement: () -> Unit,
    onNavigateToPTT: () -> Unit = {}
) {
    val context = LocalContext.current
    val officeStatus by dashboardViewModel.officeStatus.collectAsStateWithLifecycle()
    val regionId by dashboardViewModel.regionId.collectAsStateWithLifecycle()
    val officeId by dashboardViewModel.officeId.collectAsStateWithLifecycle()

    // ★★★ 알림 설정 상태 관리 ★★★
    val prefs = remember { context.getSharedPreferences("call_manager_settings", Context.MODE_PRIVATE) }
    var newCallNotificationEnabled by remember { mutableStateOf(prefs.getBoolean("new_call_notification", true)) }
    var driverEventNotificationEnabled by remember { mutableStateOf(prefs.getBoolean("driver_event_notification", true)) }

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
                // Optional: Set colors if needed, otherwise defaults will be used
                // colors = TopAppBarDefaults.topAppBarColors(...) 
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Office Status Setting --- 
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("사무실 콜 공유 상태", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = officeStatus == Constants.OFFICE_STATUS_CLOSED_SHARING,
                    onCheckedChange = { isChecked ->
                        val newStatus = if (isChecked) Constants.OFFICE_STATUS_CLOSED_SHARING else Constants.OFFICE_STATUS_OPERATING
                        dashboardViewModel.updateOfficeStatus(newStatus)
                    }
                )
            }
            Text(
                text = if (officeStatus != Constants.OFFICE_STATUS_CLOSED_SHARING) "현재 '운영 중' 상태입니다. 콜은 내부에서 처리됩니다."
                else "현재 '마감(공유 중)' 상태입니다. 콜은 공유 채널로 전송됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // --- 정산 관리 메뉴 추가 ---
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSettlement() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Payments, // 적절한 머티리얼 아이콘 사용(예: Payments)
                    contentDescription = "정산 관리",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("정산 관리", style = MaterialTheme.typography.bodyLarge)
            }
            // --- ---

            Divider()

            // --- 기사 가입 승인 메뉴 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (regionId != null && officeId != null) {
                            onNavigateToPendingDrivers(regionId!!, officeId!!)
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.PersonAdd,
                    contentDescription = "기사 가입 승인",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("기사 가입 승인", style = MaterialTheme.typography.bodyLarge)
            }
            // --- ★★★ 메뉴 추가 끝 ★★★ ---

            Divider()

            // --- Notification Settings (Placeholder) --- 
            Text("알림 설정", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("새 콜 알림음")
                Switch(checked = newCallNotificationEnabled, onCheckedChange = { isChecked ->
                    newCallNotificationEnabled = isChecked
                    prefs.edit().putBoolean("new_call_notification", isChecked).apply()
                })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("기사 이벤트 알림 (출/퇴근, 승인 등)")
                Switch(checked = driverEventNotificationEnabled, onCheckedChange = { isChecked ->
                    driverEventNotificationEnabled = isChecked
                    prefs.edit().putBoolean("driver_event_notification", isChecked).apply()
                })
            }

            Divider()

            // --- App Info ---
            Text("앱 정보", style = MaterialTheme.typography.titleMedium)
            Text("버전: 1.0.0 (Beta)", style = MaterialTheme.typography.bodyMedium)
            Text("대리운전 콜 매니저", style = MaterialTheme.typography.bodyMedium)
            Text("지역: ${regionId ?: "미설정"}", style = MaterialTheme.typography.bodySmall)
            Text("사무실: ${officeId ?: "미설정"}", style = MaterialTheme.typography.bodySmall)

        }
    }
} 
package com.designated.callmanager.ui.ptt

import android.Manifest
import android.content.pm.PackageManager
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.designated.callmanager.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTScreen(
    dashboardViewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val pttStatus by dashboardViewModel.pttStatus.collectAsStateWithLifecycle()
    val regionId by dashboardViewModel.regionId.collectAsStateWithLifecycle()
    val officeId by dashboardViewModel.officeId.collectAsStateWithLifecycle()
    
    // PTT 볼륨키 차단 설정
    val callManagerPrefs = remember { context.getSharedPreferences("call_manager_prefs", android.content.Context.MODE_PRIVATE) }
    var pttVolumeBlockEnabled by remember { mutableStateOf(callManagerPrefs.getBoolean("ptt_volume_block_enabled", true)) }
    
    // 권한 확인 및 요청
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted) {
            dashboardViewModel.initializePTT()
        }
    }
    
    // PTT 초기화 (한 번만 실행)
    var hasInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(hasAudioPermission, regionId, officeId) {
        if (hasAudioPermission && regionId != null && officeId != null && !hasInitialized) {
            dashboardViewModel.initializePTT()
            hasInitialized = true
        }
    }
    
    // 뒤로가기 처리
    BackHandler {
        onNavigateBack()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PTT 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 권한 확인 카드
            if (!hasAudioPermission) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = "경고",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "오디오 권한 필요",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Text(
                            "PTT 기능을 사용하려면 마이크 권한이 필요합니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        ) {
                            Text("권한 허용")
                        }
                    }
                }
            } else {
                // PTT 상태 카드
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            pttStatus.isSpeaking -> MaterialTheme.colorScheme.errorContainer
                            pttStatus.isConnected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PTT 상태",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            // 상태 표시 아이콘
                            Icon(
                                when {
                                    pttStatus.isSpeaking -> Icons.Filled.Mic
                                    pttStatus.isConnected -> Icons.Filled.Radio
                                    else -> Icons.Filled.RadioButtonUnchecked
                                },
                                contentDescription = "PTT 상태",
                                tint = when {
                                    pttStatus.isSpeaking -> MaterialTheme.colorScheme.error
                                    pttStatus.isConnected -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Text(
                            pttStatus.connectionState,
                            style = MaterialTheme.typography.bodyLarge,
                            color = when {
                                pttStatus.isSpeaking -> MaterialTheme.colorScheme.onErrorContainer
                                pttStatus.isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        
                        if (pttStatus.channelName != null) {
                            Text(
                                "채널: ${pttStatus.channelName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                // PTT 컨트롤 카드
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "PTT 컨트롤",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // PTT 볼륨키 차단 토글
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "볼륨키 PTT 우선 모드",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        if (pttVolumeBlockEnabled) "볼륨키가 PTT 전용으로 동작 (다른 앱 볼륨 조절 불가)" 
                                        else "볼륨키로 일반 볼륨 조절 가능 (PTT 기능 비활성화)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Switch(
                                    checked = pttVolumeBlockEnabled,
                                    onCheckedChange = { isChecked ->
                                        pttVolumeBlockEnabled = isChecked
                                        callManagerPrefs.edit().apply {
                                            putBoolean("ptt_volume_block_enabled", isChecked)
                                            apply()
                                        }
                                    }
                                )
                            }
                        }
                        
                        // 볼륨 다운 키 사용법 안내
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.VolumeDown,
                                        contentDescription = "볼륨 다운",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "사용법",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    "• 볼륨 다운 키를 누르고 있으면 PTT 송신\n• 키를 떼면 송신 종료\n• 위 토글이 OFF인 경우 일반 볼륨 조절",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // 볼륨 조절 버튼들
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { dashboardViewModel.adjustPTTVolume(-1) },
                                enabled = pttStatus.isConnected
                            ) {
                                Icon(Icons.Filled.VolumeDown, contentDescription = "볼륨 다운")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("볼륨 -")
                            }
                            
                            Button(
                                onClick = { dashboardViewModel.adjustPTTVolume(1) },
                                enabled = pttStatus.isConnected
                            ) {
                                Icon(Icons.Filled.VolumeUp, contentDescription = "볼륨 업")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("볼륨 +")
                            }
                        }
                        
                        // PTT 테스트 버튼들
                        var isTestingPTT by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { 
                                    isTestingPTT = true
                                    dashboardViewModel.handlePTTVolumeDown()
                                },
                                enabled = hasAudioPermission && !isTestingPTT,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Filled.Mic, contentDescription = "PTT 송신 시작")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("송신 시작")
                            }
                            
                            Button(
                                onClick = { 
                                    isTestingPTT = false
                                    dashboardViewModel.handlePTTVolumeUp()
                                },
                                enabled = hasAudioPermission && isTestingPTT,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Filled.MicOff, contentDescription = "PTT 송신 종료")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("송신 종료")
                            }
                        }
                    }
                }
                
                // 정보 카드
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "설정 정보",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("지역:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                regionId ?: "미설정",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("사무실:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                officeId ?: "미설정",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("사용자 타입:", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "콜매니저",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                // 주의사항 카드
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "정보",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                "주의사항",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            "• PTT는 같은 지역/사무실의 기사들과 통신됩니다\n• 네트워크 상태가 좋지 않으면 지연이 발생할 수 있습니다\n• 배터리 절약을 위해 필요시에만 사용하세요",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
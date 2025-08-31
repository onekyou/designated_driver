package com.designated.pickupapp.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.DisposableEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.designated.pickupapp.data.DriverInfo
import com.designated.pickupapp.data.PickupStatus
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    regionId: String,
    officeId: String, 
    driverId: String,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val drivers by viewModel.drivers.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()

    val context = LocalContext.current
    
    // HomeViewModel을 MainActivity에 설정
    DisposableEffect(viewModel) {
        if (context is ComponentActivity) {
            (context as com.designated.pickupapp.MainActivity).setHomeViewModel(viewModel)
        }
        onDispose { }
    }
    
    LaunchedEffect(regionId, officeId, driverId) {
        viewModel.initialize(regionId, officeId, driverId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "픽업 기사 앱",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(
                            Icons.Filled.ExitToApp,
                            contentDescription = "로그아웃",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFF2A2A2A)
                )
            )
        },
    ) { paddingValues ->
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // 위아래로 나눈 레이아웃: 위쪽은 기사현황카드(스크롤), 아래쪽은 PTT 시스템
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 위쪽: 기사 현황 카드 (스크롤 가능, 화면의 절반)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        DriverStatusCard(
                            modifier = Modifier.fillMaxWidth(),
                            drivers = drivers
                        )
                    }
                }
                
                // 구분선
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .padding(horizontal = 16.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                )
                
                // 아래쪽: PTT 시스템 (화면의 절반)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    EmbeddedPTTSection(
                        regionId = regionId,
                        officeId = officeId
                    )
                }
            }
        }
    }
}

@Composable
fun DriverStatusItem(driver: DriverInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = when (driver.status) {
                        PickupStatus.AVAILABLE.name -> Color.Green
                        PickupStatus.BUSY.name -> Color.Red
                        else -> Color.Gray
                    },
                    shape = CircleShape
                )
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = driver.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when (driver.status) {
                    PickupStatus.AVAILABLE.name -> "대기중"
                    PickupStatus.BUSY.name -> "운행중"
                    PickupStatus.OFFLINE.name -> "오프라인"
                    else -> driver.status
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DriverStatusCard(
    modifier: Modifier = Modifier,
    drivers: List<DriverInfo>
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "기사 현황",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            if (drivers.isEmpty()) {
                Text(
                    "기사 정보가 없습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                drivers.forEach { driver ->
                    DriverStatusItem(driver = driver)
                }
            }
        }
    }
}

@Composable
fun EmbeddedPTTSection(
    regionId: String,
    officeId: String
) {
    val context = LocalContext.current
    
    // Service의 상태를 구독
    val pttState by com.designated.pickupapp.ptt.service.PTTForegroundService.pttState.collectAsStateWithLifecycle()
    
    var isPressing by remember { mutableStateOf(false) }
    var isServiceStarted by remember { mutableStateOf(false) }
    
    // Service 시작
    LaunchedEffect(Unit) {
        if (!isServiceStarted) {
            com.designated.pickupapp.ptt.service.PTTForegroundService.startService(context, regionId, officeId)
            isServiceStarted = true
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 제목
        Text(
            "픽업 PTT 무전",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        // 상태 표시 (간소화)
        PTTStatusIndicator(pttState = pttState)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // PTT 버튼 (크기 축소)
        PTTButtonCompact(
            pttState = pttState,
            isPressing = isPressing,
            onPressStart = {
                isPressing = true
                sendCommandToService(context, com.designated.pickupapp.ptt.service.PTTForegroundService.ACTION_START_PTT)
            },
            onPressEnd = {
                isPressing = false
                sendCommandToService(context, com.designated.pickupapp.ptt.service.PTTForegroundService.ACTION_STOP_PTT)
            }
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // 채널 제어 (간소화)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val intent = android.content.Intent(context, com.designated.pickupapp.ptt.service.PTTForegroundService::class.java).apply {
                        action = com.designated.pickupapp.ptt.service.PTTForegroundService.ACTION_JOIN_CHANNEL
                        putExtra(com.designated.pickupapp.ptt.service.PTTForegroundService.EXTRA_CHANNEL, "${regionId}_${officeId}_ptt")
                    }
                    context.startService(intent)
                },
                enabled = pttState is com.designated.pickupapp.ptt.state.PTTState.Disconnected || pttState is com.designated.pickupapp.ptt.state.PTTState.Error,
                modifier = Modifier.weight(1f)
            ) {
                Text("채널 참여", fontSize = 12.sp)
            }
            
            Button(
                onClick = {
                    sendCommandToService(context, com.designated.pickupapp.ptt.service.PTTForegroundService.ACTION_LEAVE_CHANNEL)
                },
                enabled = pttState !is com.designated.pickupapp.ptt.state.PTTState.Disconnected,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("나가기", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PTTStatusIndicator(pttState: com.designated.pickupapp.ptt.state.PTTState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (pttState) {
                is com.designated.pickupapp.ptt.state.PTTState.Disconnected -> {
                    Text("연결 해제됨", fontSize = 14.sp, color = Color.Gray)
                }
                is com.designated.pickupapp.ptt.state.PTTState.Connecting -> {
                    Text("연결 중...", fontSize = 14.sp)
                }
                is com.designated.pickupapp.ptt.state.PTTState.Connected -> {
                    Text("채널: ${pttState.channel}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("UID: ${pttState.uid}", fontSize = 12.sp, color = Color.Gray)
                }
                is com.designated.pickupapp.ptt.state.PTTState.Transmitting -> {
                    Text(
                        if (pttState.isTransmitting) "송신 중..." else "송신 준비",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pttState.isTransmitting) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
                is com.designated.pickupapp.ptt.state.PTTState.UserSpeaking -> {
                    val userType = when {
                        pttState.uid in 1000..1999 -> "관리자"
                        pttState.uid in 2000..2999 -> "픽업"
                        else -> "사용자"
                    }
                    Text("$userType ${pttState.uid} 말하는 중", fontSize = 14.sp, color = Color.Green)
                }
                is com.designated.pickupapp.ptt.state.PTTState.Error -> {
                    Text("오류: ${pttState.message}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PTTButtonCompact(
    pttState: com.designated.pickupapp.ptt.state.PTTState,
    isPressing: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val isEnabled = pttState is com.designated.pickupapp.ptt.state.PTTState.Connected || 
                   pttState is com.designated.pickupapp.ptt.state.PTTState.Transmitting || 
                   pttState is com.designated.pickupapp.ptt.state.PTTState.UserSpeaking
    
    val buttonColor = when {
        !isEnabled -> Color.Gray
        isPressing -> Color(0xFFFF6B35) // 픽업용 주황색
        else -> MaterialTheme.colorScheme.primary
    }
    
    Box(
        modifier = Modifier
            .size(120.dp) // 크기 축소
            .clip(CircleShape)
            .background(buttonColor)
            .pointerInput(isEnabled) {
                if (isEnabled) {
                    detectTapGestures(
                        onPress = {
                            onPressStart()
                            awaitRelease()
                            onPressEnd()
                        }
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                if (isPressing) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isPressing) "송신 중" else "PTT",
                tint = Color.White,
                modifier = Modifier.size(36.dp) // 아이콘 크기 축소
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (isPressing) "송신 중" else "눌러서 말하기",
                color = Color.White,
                fontSize = 10.sp, // 텍스트 크기 축소
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Service에 명령을 전송하는 헬퍼 함수
 */
private fun sendCommandToService(context: android.content.Context, action: String) {
    val intent = android.content.Intent(context, com.designated.pickupapp.ptt.service.PTTForegroundService::class.java).apply {
        this.action = action
    }
    context.startService(intent)
}

private fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60 * 1000 -> "방금 전"
        diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}분 전"
        diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}시간 전"
        else -> SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}
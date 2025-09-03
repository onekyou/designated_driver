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
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.designated.pickupapp.data.PTTState
import com.designated.pickupapp.data.PTTStatus
import com.designated.pickupapp.utils.PTTTestHelper
import com.designated.pickupapp.BuildConfig
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
    val pttState by viewModel.pttState.collectAsStateWithLifecycle()

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    EmbeddedPTTSection(
                        regionId = regionId,
                        officeId = officeId,
                        pttState = pttState
                    )
                    
                    // 디버그 패널 (디버그 빌드에서만 표시)
                    if (com.designated.pickupapp.BuildConfig.DEBUG) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TestDebugPanel(pttState = pttState)
                    }
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

/**
 * PTT 시스템 UI 섹션 (콜매니저 UI 완전 이식)
 */
@Composable
fun EmbeddedPTTSection(
    regionId: String,
    officeId: String,
    pttState: com.designated.pickupapp.data.PTTState
) {
    val context = LocalContext.current
    
    // Service의 상태를 구독
    val servicePttState by com.designated.pickupapp.ptt.service.PTTForegroundService.pttState.collectAsStateWithLifecycle()
    
    var isPressing by remember { mutableStateOf(false) }
    var isServiceStarted by remember { mutableStateOf(false) }
    
    // Service 시작 및 접근성 서비스 확인
    LaunchedEffect(Unit) {
        if (!isServiceStarted) {
            // PTTForegroundService 시작
            com.designated.pickupapp.ptt.service.PTTForegroundService.startService(context, regionId, officeId)
            isServiceStarted = true
            
            // 접근성 서비스 활성화 확인 (볼륨키 PTT용)
            if (!com.designated.pickupapp.utils.PermissionManager.hasAccessibilityPermission(context)) {
                android.widget.Toast.makeText(
                    context,
                    "볼륨키 PTT를 사용하려면 접근성 서비스를 활성화하세요",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 상태 표시 카드 (콜매니저 스타일)
        StatusCard(servicePttState = servicePttState)
        
        Spacer(modifier = Modifier.weight(1f))
        
        // PTT 버튼 (콜매니저 스타일)
        PTTButton(
            pttState = servicePttState,
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
        
        // 채널 제어 버튼들 (콜매니저 스타일)
        ChannelControls(
            context = context,
            pttState = servicePttState,
            regionId = regionId,
            officeId = officeId
        )
    }
}

@Composable
private fun StatusCard(servicePttState: com.designated.pickupapp.ptt.state.PTTState?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (servicePttState) {
                null -> {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "시스템 로딩 중...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                }
                
                is com.designated.pickupapp.ptt.state.PTTState.Disconnected -> {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "연결 해제됨",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )
                    Text(
                        "채널에 참여하여 PTT를 시작하세요",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
                
                is com.designated.pickupapp.ptt.state.PTTState.Connecting -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "연결 중...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                is com.designated.pickupapp.ptt.state.PTTState.Connected -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "채널: ${servicePttState.channel}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "UID: ${servicePttState.uid}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        "PTT 준비됨",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                is com.designated.pickupapp.ptt.state.PTTState.Transmitting -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (servicePttState.isTransmitting) Color.Red else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        if (servicePttState.isTransmitting) "송신 중..." else "송신 준비",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (servicePttState.isTransmitting) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
                
                is com.designated.pickupapp.ptt.state.PTTState.UserSpeaking -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(48.dp)
                    )
                    val userType = when {
                        servicePttState.uid in 1000..1999 -> "관리자"
                        servicePttState.uid in 2000..2999 -> "픽업"
                        else -> "사용자"
                    }
                    Text(
                        "$userType ${servicePttState.uid} 말하는 중",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Green
                    )
                    Text(
                        "음량: ${servicePttState.volume}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                is com.designated.pickupapp.ptt.state.PTTState.Error -> {
                    Icon(
                        Icons.Default.MicOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "오류 발생",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        servicePttState.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    servicePttState.code?.let { code ->
                        Text(
                            "코드: $code",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PTTButton(
    pttState: com.designated.pickupapp.ptt.state.PTTState?,
    isPressing: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val isEnabled = pttState is com.designated.pickupapp.ptt.state.PTTState.Connected || 
                   pttState is com.designated.pickupapp.ptt.state.PTTState.Transmitting || 
                   pttState is com.designated.pickupapp.ptt.state.PTTState.UserSpeaking
    
    // UserSpeaking 상태면 다른 사용자가 사용 중이므로 버튼 비활성화
    val canTransmit = isEnabled && pttState !is com.designated.pickupapp.ptt.state.PTTState.UserSpeaking
    
    val buttonColor = when {
        !isEnabled -> Color.Gray
        pttState is com.designated.pickupapp.ptt.state.PTTState.UserSpeaking -> Color(0xFF757575) // 어두운 회색
        isPressing -> Color.Red
        else -> MaterialTheme.colorScheme.primary
    }
    
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(buttonColor)
            .pointerInput(canTransmit) {
                if (canTransmit) {
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
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when {
                    isPressing -> "송신 중"
                    pttState is com.designated.pickupapp.ptt.state.PTTState.UserSpeaking -> "사용 중"
                    !isEnabled -> "연결 안됨"
                    else -> "눌러서 말하기"
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChannelControls(
    context: Context,
    pttState: com.designated.pickupapp.ptt.state.PTTState?,
    regionId: String,
    officeId: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 채널 참여 버튼
        Button(
            onClick = {
                val intent = android.content.Intent(context, com.designated.pickupapp.ptt.service.PTTForegroundService::class.java).apply {
                    action = com.designated.pickupapp.ptt.service.PTTForegroundService.ACTION_JOIN_CHANNEL
                    putExtra(com.designated.pickupapp.ptt.service.PTTForegroundService.EXTRA_CHANNEL, "${regionId}_${officeId}_ptt")
                }
                context.startService(intent)
            },
            enabled = pttState is com.designated.pickupapp.ptt.state.PTTState.Disconnected || pttState is com.designated.pickupapp.ptt.state.PTTState.Error,
            modifier = Modifier
                .height(48.dp)
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text("채널 참여", fontSize = 14.sp)
        }
        
        // 채널 나가기 버튼
        Button(
            onClick = {
                sendCommandToService(context, com.designated.pickupapp.ptt.service.PTTForegroundService.ACTION_LEAVE_CHANNEL)
            },
            enabled = pttState != null && pttState !is com.designated.pickupapp.ptt.state.PTTState.Disconnected,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            ),
            modifier = Modifier
                .height(48.dp)
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text("나가기", fontSize = 14.sp)
        }
        
        // 서비스 종료 버튼
        Button(
            onClick = {
                com.designated.pickupapp.ptt.service.PTTForegroundService.stopService(context)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .height(48.dp)
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text("종료", fontSize = 14.sp)
        }
    }
}

@Composable
fun TestDebugPanel(
    pttState: com.designated.pickupapp.data.PTTState,
    modifier: Modifier = Modifier
) {
    var showLogs by remember { mutableStateOf(false) }
    var testResults by remember { mutableStateOf("테스트 결과가 여기에 표시됩니다") }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                Color.Black.copy(alpha = 0.8f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            "🔧 디버그 패널",
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 상태 정보
        Text(
            "활성 사용자: ${pttState.activePTTUsers.size}명",
            color = Color.Green,
            fontSize = 12.sp
        )
        
        Text(
            "현재 전송자: ${pttState.currentTransmitter ?: "없음"}",
            color = if (pttState.hasActiveTransmitter()) Color.Red else Color.Gray,
            fontSize = 12.sp
        )
        
        // 성능 정보
        Text(
            com.designated.pickupapp.utils.PTTTestHelper.monitorPerformance(),
            color = Color.Yellow,
            fontSize = 10.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 버튼들
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    val context = LocalContext.current
                    com.designated.pickupapp.utils.PTTTestHelper.runAutoTest(context) { result ->
                        testResults = result
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Blue
                ),
                modifier = Modifier.size(width = 80.dp, height = 32.dp)
            ) {
                Text("자동테스트", fontSize = 10.sp, color = Color.White)
            }
            
            Button(
                onClick = { showLogs = !showLogs },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (showLogs) Color.Red else Color.Gray
                ),
                modifier = Modifier.size(width = 70.dp, height = 32.dp)
            ) {
                Text("로그", fontSize = 10.sp, color = Color.White)
            }
            
            Button(
                onClick = {
                    com.designated.pickupapp.utils.PTTTestHelper.clearLogs()
                    testResults = "로그가 초기화되었습니다"
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9800)
                ),
                modifier = Modifier.size(width = 70.dp, height = 32.dp)
            ) {
                Text("초기화", fontSize = 10.sp, color = Color.White)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 테스트 결과 표시
        Text(
            testResults,
            color = Color.Cyan,
            fontSize = 10.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black, RoundedCornerShape(4.dp))
                .padding(4.dp)
        )
        
        // 로그 표시
        if (showLogs) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .padding(4.dp)
            ) {
                items(com.designated.pickupapp.utils.PTTTestHelper.getLogs()) { log ->
                    Text(
                        log,
                        color = Color.White,
                        fontSize = 8.sp
                    )
                }
            }
        }
    }
}
package com.designated.callmanager.ptt.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.designated.callmanager.ptt.service.PTTForegroundService
import com.designated.callmanager.ptt.state.PTTState

/**
 * PTT 제어 화면
 * Service와 StateFlow로 통신하여 PTT 기능 제공
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTScreen(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // SharedPreferences에서 실제 regionId, officeId 가져오기
    val sharedPreferences = remember {
        context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    }
    val regionId = sharedPreferences.getString("regionId", "region1") ?: "region1"
    val officeId = sharedPreferences.getString("officeId", "office1") ?: "office1"
    
    // Service의 상태를 구독
    val pttState by PTTForegroundService.pttState.collectAsStateWithLifecycle()
    
    var isPressing by remember { mutableStateOf(false) }
    var isServiceStarted by remember { mutableStateOf(false) }
    var hasAudioPermission by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) 
    }
    
    // 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasAudioPermission = isGranted
        if (isGranted && !isServiceStarted) {
            PTTForegroundService.startService(context, regionId, officeId)
            isServiceStarted = true
        }
    }
    
    // Service 시작 (권한 확인 후)
    LaunchedEffect(Unit) {
        if (hasAudioPermission && !isServiceStarted) {
            PTTForegroundService.startService(context, regionId, officeId)
            isServiceStarted = true
        } else if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text("PTT 무전 시스템") 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            sendCommandToService(context, PTTForegroundService.ACTION_LEAVE_CHANNEL)
                        }
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "설정")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (!hasAudioPermission) {
                // 권한이 없는 경우 권한 요청 UI
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "마이크 권한이 필요합니다",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "PTT 음성 통신을 위해 마이크 권한을 허용해주세요.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
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
                // 상태 표시 카드
                StatusCard(pttState = pttState)
                
                Spacer(modifier = Modifier.weight(1f))
                
                // PTT 버튼
                PTTButton(
                    pttState = pttState,
                    isPressing = isPressing,
                    onPressStart = {
                        isPressing = true
                        sendCommandToService(context, PTTForegroundService.ACTION_START_PTT)
                    },
                    onPressEnd = {
                        isPressing = false
                        sendCommandToService(context, PTTForegroundService.ACTION_STOP_PTT)
                    }
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 채널 제어 버튼들
                ChannelControls(
                    context = context,
                    pttState = pttState,
                    regionId = regionId,
                    officeId = officeId
                )
            }
        }
    }
}

@Composable
private fun StatusCard(pttState: PTTState) {
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
            when (pttState) {
                is PTTState.Disconnected -> {
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
                
                is PTTState.Connecting -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "연결 중...",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                is PTTState.Connected -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "채널: ${pttState.channel}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "UID: ${pttState.uid}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        "PTT 준비됨",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                is PTTState.Transmitting -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (pttState.isTransmitting) Color.Red else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        if (pttState.isTransmitting) "송신 중..." else "송신 준비",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pttState.isTransmitting) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
                
                is PTTState.UserSpeaking -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "사용자 ${pttState.uid} 말하는 중",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Green
                    )
                    Text(
                        "음량: ${pttState.volume}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                
                is PTTState.Error -> {
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
                        pttState.message,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    pttState.code?.let { code ->
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
    pttState: PTTState,
    isPressing: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val isEnabled = pttState is PTTState.Connected || 
                   pttState is PTTState.Transmitting || 
                   pttState is PTTState.UserSpeaking
    
    val buttonColor = when {
        !isEnabled -> Color.Gray
        isPressing -> Color.Red
        else -> MaterialTheme.colorScheme.primary
    }
    
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(buttonColor)
            .pointerInput(isEnabled) {
                if (isEnabled) {
                    detectTapGestures(
                        onPress = {
                            onPressStart()
                            tryAwaitRelease()
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
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isPressing) "송신 중" else "눌러서 말하기",
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
    pttState: PTTState,
    regionId: String,
    officeId: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // 채널 참여 버튼
        Button(
            onClick = {
                val intent = Intent(context, PTTForegroundService::class.java).apply {
                    action = PTTForegroundService.ACTION_JOIN_CHANNEL
                    putExtra(PTTForegroundService.EXTRA_CHANNEL, "${regionId}_${officeId}_ptt")
                }
                context.startService(intent)
            },
            enabled = pttState is PTTState.Disconnected || pttState is PTTState.Error
        ) {
            Text("채널 참여")
        }
        
        // 채널 나가기 버튼
        Button(
            onClick = {
                sendCommandToService(context, PTTForegroundService.ACTION_LEAVE_CHANNEL)
            },
            enabled = pttState !is PTTState.Disconnected,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("채널 나가기")
        }
        
        // 서비스 종료 버튼
        Button(
            onClick = {
                PTTForegroundService.stopService(context)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("서비스 종료")
        }
    }
}

/**
 * Service에 명령을 전송하는 헬퍼 함수
 */
private fun sendCommandToService(context: Context, action: String) {
    val intent = Intent(context, PTTForegroundService::class.java).apply {
        this.action = action
    }
    context.startService(intent)
}
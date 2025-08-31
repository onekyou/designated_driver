package com.designated.pickupapp.ptt.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Settings
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
import com.designated.pickupapp.ptt.service.PTTForegroundService
import com.designated.pickupapp.ptt.state.PTTState

/**
 * 픽업 PTT 제어 화면
 * Service와 StateFlow로 통신하여 PTT 기능 제공
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTScreen(
    regionId: String = "region1",
    officeId: String = "office1",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Service의 상태를 구독
    val pttState by PTTForegroundService.pttState.collectAsStateWithLifecycle()
    
    var isPressing by remember { mutableStateOf(false) }
    var isServiceStarted by remember { mutableStateOf(false) }
    
    // Service 시작
    LaunchedEffect(Unit) {
        if (!isServiceStarted) {
            PTTForegroundService.startService(context, regionId, officeId)
            isServiceStarted = true
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("픽업 PTT") 
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

@Composable
private fun StatusCard(pttState: PTTState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
                        "채널에 참여하여 픽업 PTT를 시작하세요",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
                
                is PTTState.Connecting -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.tertiary
                    )
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
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "채널: ${pttState.channel}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "픽업 UID: ${pttState.uid}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Text(
                        "픽업 PTT 준비됨",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                
                is PTTState.Transmitting -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = if (pttState.isTransmitting) Color.Red else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        if (pttState.isTransmitting) "픽업 송신 중..." else "픽업 송신 준비",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (pttState.isTransmitting) Color.Red else MaterialTheme.colorScheme.tertiary
                    )
                }
                
                is PTTState.UserSpeaking -> {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.Green,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    val userTypeText = when {
                        pttState.uid in 1000..1999 -> "관리자"
                        pttState.uid in 2000..2999 -> "픽업"
                        else -> "사용자"
                    }
                    
                    Text(
                        "$userTypeText ${pttState.uid} 말하는 중",
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
        isPressing -> Color(0xFFFF6B35) // 픽업용 주황색
        else -> MaterialTheme.colorScheme.tertiary
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
                contentDescription = if (isPressing) "픽업 송신 중" else "픽업 PTT",
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (isPressing) "픽업 송신 중" else "눌러서 말하기",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            if (!isPressing) {
                Text(
                    "(픽업 기사)",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
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
            enabled = pttState is PTTState.Disconnected || pttState is PTTState.Error,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.tertiary
            )
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
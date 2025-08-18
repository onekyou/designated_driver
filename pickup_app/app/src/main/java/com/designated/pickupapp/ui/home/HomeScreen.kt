package com.designated.pickupapp.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.designated.pickupapp.data.CallInfo
import com.designated.pickupapp.data.CallStatus
import com.designated.pickupapp.data.DriverInfo
import com.designated.pickupapp.data.PickupStatus
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    regionId: String,
    officeId: String, 
    driverId: String,
    onLogout: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val drivers by viewModel.drivers.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val pttStatus by viewModel.pttStatus.collectAsStateWithLifecycle()
    val isPttSpeaking by viewModel.isPttSpeaking.collectAsStateWithLifecycle()
    val pttChannelName by viewModel.pttChannelName.collectAsStateWithLifecycle()

    val context = LocalContext.current
    
    LaunchedEffect(regionId, officeId, driverId) {
        viewModel.initialize(regionId, officeId, driverId)
        
        // MainActivity에 ViewModel 설정 (볼륨 키 처리용)
        val activity = context as? com.designated.pickupapp.MainActivity
        activity?.setHomeViewModel(viewModel)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "픽업 콜 관리",
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
                    containerColor = Color(0xFF2A2A2A)
                )
            )
        },
        floatingActionButton = {
            PTTFloatingActionButton(
                isPttSpeaking = isPttSpeaking,
                pttStatus = pttStatus,
                onPttPress = { viewModel.startPTT() },
                onPttRelease = { viewModel.stopPTT() }
            )
        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                
                // 기사현황 카드 (비율 5)
                DriverStatusCard(
                    modifier = Modifier.fillMaxWidth().weight(5f),
                    drivers = drivers
                )

                // PTT 관리 카드 (비율 5)
                Column(
                    modifier = Modifier.fillMaxWidth().weight(5f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // PTT 상태 카드
                    PTTStatusCard(
                        pttStatus = pttStatus,
                        isPttSpeaking = isPttSpeaking,
                        pttChannelName = pttChannelName
                    )
                    
                    // PTT 컨트롤 카드
                    PTTControlCard(
                        isPttSpeaking = isPttSpeaking,
                        onStartPTT = { viewModel.startPTT() },
                        onStopPTT = { viewModel.stopPTT() },
                        onVolumeDown = { viewModel.adjustPTTVolume(false) },
                        onVolumeUp = { viewModel.adjustPTTVolume(true) }
                    )
                }
            }
        }
    }
}

@Composable
fun CallCard(call: CallInfo) {
    val callStatus = CallStatus.fromString(call.status)
    val statusDisplayName = callStatus.displayName
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val displayText = call.customerName.takeIf { !it.isNullOrBlank() }
                    ?: call.customerAddress
                    ?: "정보 없음"

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Text(
                    text = formatTimeAgo(call.timestamp.toDate().time),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                
                call.assignedDriverName?.let {
                    Text(
                        text = "배정: $it 기사",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Cyan
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // 상태 표시
                Box(
                    modifier = Modifier
                        .background(
                            color = when (callStatus) {
                                CallStatus.WAITING -> Color(0xFFFFAB00)
                                CallStatus.ASSIGNED -> Color(0xFF2196F3)
                                CallStatus.ACCEPTED -> Color(0xFF4CAF50)
                                CallStatus.IN_PROGRESS -> Color(0xFFFF5722)
                                CallStatus.AWAITING_SETTLEMENT -> Color(0xFF9C27B0)
                                else -> Color.Gray
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = statusDisplayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // 전화 아이콘
                if (!call.phoneNumber.isNullOrBlank()) {
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${call.phoneNumber}")
                            }
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = "전화걸기",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DriverStatusItem(driver: DriverInfo) {
    val driverStatus = PickupStatus.fromString(driver.status)
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 상태 표시 원
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = when (driverStatus) {
                            PickupStatus.AVAILABLE -> Color(0xFF4CAF50)
                            PickupStatus.BUSY -> Color(0xFFFF5722)
                            PickupStatus.OFFLINE -> Color.Gray
                        },
                        shape = CircleShape
                    )
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 기사 정보
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driver.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = driverStatus.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }

            // 전화 아이콘
            if (!driver.phoneNumber.isNullOrBlank()) {
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:${driver.phoneNumber}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "기사에게 전화",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Text(
                text = "기사 현황", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(drivers, key = { it.id }) { driver ->
                    DriverStatusItem(driver = driver)
                }
            }
        }
    }
}

@Composable
fun PTTStatusCard(
    pttStatus: String,
    isPttSpeaking: Boolean,
    pttChannelName: String = ""
) {
    // pttStatus로부터 연결 상태 파악
    val isConnected = pttStatus.contains("연결됨") || pttStatus.contains("대기") || pttStatus.contains("수신")
    
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when {
                isPttSpeaking -> MaterialTheme.colorScheme.errorContainer
                isConnected -> MaterialTheme.colorScheme.primaryContainer
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
                        isPttSpeaking -> Icons.Filled.Mic
                        isConnected -> Icons.Filled.Radio
                        else -> Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = "PTT 상태",
                    tint = when {
                        isPttSpeaking -> MaterialTheme.colorScheme.error
                        isConnected -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Text(
                pttStatus.ifBlank { "PTT 미연결" },
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    isPttSpeaking -> MaterialTheme.colorScheme.onErrorContainer
                    isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            
            // 채널명 표시 (콜매니저와 동일한 기능 추가)
            if (pttChannelName.isNotBlank()) {
                Text(
                    "채널: $pttChannelName",
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isPttSpeaking -> MaterialTheme.colorScheme.onErrorContainer
                        isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.Light
                )
            }
        }
    }
}

@Composable
fun PTTControlCard(
    isPttSpeaking: Boolean,
    onStartPTT: () -> Unit,
    onStopPTT: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit
) {
    var isTestingPTT by remember { mutableStateOf(false) }
    
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
            
            
            // 볼륨 조절 버튼들 (콜매니저와 동일)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onVolumeDown,
                    enabled = true
                ) {
                    Icon(Icons.Filled.VolumeDown, contentDescription = "볼륨 다운")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("볼륨 -")
                }
                
                Button(
                    onClick = onVolumeUp,
                    enabled = true
                ) {
                    Icon(Icons.Filled.VolumeUp, contentDescription = "볼륨 업")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("볼륨 +")
                }
            }
            
            // PTT 테스트 버튼들 (콜매니저와 동일)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        isTestingPTT = true
                        onStartPTT()
                    },
                    enabled = !isTestingPTT,
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
                        onStopPTT()
                    },
                    enabled = isTestingPTT,
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
}

@Composable
fun PTTFloatingActionButton(
    isPttSpeaking: Boolean,
    pttStatus: String,
    onPttPress: () -> Unit,
    onPttRelease: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // PTT 상태 표시
        if (pttStatus.isNotBlank()) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = pttStatus,
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
        
        // PTT 버튼
        FloatingActionButton(
            onClick = { /* onClick은 사용하지 않음 - gesture로 처리 */ },
            modifier = Modifier
                .size(80.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            onPttPress()
                            tryAwaitRelease()
                            isPressed = false
                            onPttRelease()
                        }
                    )
                },
            containerColor = when {
                isPttSpeaking -> Color(0xFFFF5722) // 송신 중: 빨간색
                isPressed -> Color(0xFFFF9800) // 눌림: 주황색
                else -> Color(0xFF4CAF50) // 대기: 초록색
            },
            contentColor = Color.White
        ) {
            Icon(
                imageVector = if (isPttSpeaking) Icons.Filled.Mic else Icons.Filled.MicNone,
                contentDescription = if (isPttSpeaking) "송신 중" else "PTT",
                modifier = Modifier.size(32.dp)
            )
        }
    }
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
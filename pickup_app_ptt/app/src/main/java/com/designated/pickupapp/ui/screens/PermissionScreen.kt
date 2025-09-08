package com.designated.pickupapp.ui.screens

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.pickupapp.utils.PermissionManager

/**
 * 권한 요청 화면 (콜매니저와 동일한 UI 스타일)
 * PTT 기능에 필요한 모든 권한을 한번에 요청
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // 권한 상태를 Map으로 관리 (콜매니저와 동일)
    var permissionStates by remember { mutableStateOf(mapOf<String, PermissionState>()) }
    var shouldNavigateAway by remember { mutableStateOf(false) }
    
    // 권한 상태 초기화 및 주기적 업데이트
    LaunchedEffect(Unit) {
        while (!shouldNavigateAway) {
            permissionStates = buildMap {
                // 마이크 권한 (필수)
                put(Manifest.permission.RECORD_AUDIO, PermissionState(
                    isGranted = PermissionManager.hasMicrophonePermission(context),
                    isRequired = true,
                    description = "마이크 사용 (PTT)"
                ))
                
                // 알림 권한 (Android 13+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    put(Manifest.permission.POST_NOTIFICATIONS, PermissionState(
                        isGranted = PermissionManager.hasNotificationPermission(context),
                        isRequired = false,
                        description = "알림 표시"
                    ))
                }
                
                // 오디오 설정 권한
                put(Manifest.permission.MODIFY_AUDIO_SETTINGS, PermissionState(
                    isGranted = PermissionManager.hasPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS),
                    isRequired = false,
                    description = "오디오 설정 변경"
                ))
                
                // 블루투스 권한
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    put(Manifest.permission.BLUETOOTH_CONNECT, PermissionState(
                        isGranted = PermissionManager.hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT),
                        isRequired = false,
                        description = "블루투스 연결"
                    ))
                }
                
                // 특수 권한들
                put("OVERLAY", PermissionState(
                    isGranted = PermissionManager.hasOverlayPermission(context),
                    isRequired = false,
                    description = "다른 앱 위에 표시"
                ))
                
                // 접근성 서비스 권한
                put("ACCESSIBILITY", PermissionState(
                    isGranted = PermissionManager.hasAccessibilityPermission(context),
                    isRequired = false,
                    description = "볼륨 키 PTT 기능"
                ))
            }
            
            // 마이크 권한 체크 (필수 권한) - 버튼 클릭 시에만 이동
            val microphoneGranted = permissionStates[Manifest.permission.RECORD_AUDIO]?.isGranted == true
            
            kotlinx.coroutines.delay(1000)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("픽업 PTT 권한 설정") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFFAB00), // 콜매니저와 동일한 Primary 색상
                    titleContentColor = Color(0xFF000000) // OnPrimary 색상
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Text(
                text = "픽업 PTT 권한 설정",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "픽업 서비스에 필요한 권한들입니다",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            val basicPermissions = listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.MODIFY_AUDIO_SETTINGS
            ).plus(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
                    listOf(Manifest.permission.POST_NOTIFICATIONS) 
                else emptyList()
            ).plus(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 
                    listOf(Manifest.permission.BLUETOOTH_CONNECT) 
                else emptyList()
            ).filter { permissionStates.containsKey(it) }
            
            val allBasicGranted = basicPermissions.all { permissionStates[it]?.isGranted == true }
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(basicPermissions) { permission ->
                    PermissionItem(
                        permission = permission,
                        state = permissionStates[permission] ?: PermissionState(false, true, "")
                    )
                }
                
                // 특수 권한 (오버레이)
                item {
                    val overlayState = permissionStates["OVERLAY"]
                    if (overlayState != null) {
                        SpecialPermissionCard(
                            title = "다른 앱 위에 표시",
                            description = "백그라운드에서 PTT 버튼을 표시합니다 (선택)",
                            icon = Icons.Default.OpenInNew,
                            isGranted = overlayState.isGranted,
                            isRequired = false,
                            onRequest = {
                                activity?.let {
                                    PermissionManager.requestOverlayPermission(it)
                                }
                            }
                        )
                    }
                }
                
                // 특수 권한 (접근성 서비스)
                item {
                    val accessibilityState = permissionStates["ACCESSIBILITY"]
                    if (accessibilityState != null) {
                        SpecialPermissionCard(
                            title = "접근성 서비스 (볼륨 키 PTT)",
                            description = "볼륨 업/다운 키로 PTT 기능을 사용할 수 있습니다 (선택)",
                            icon = Icons.Default.Accessibility,
                            isGranted = accessibilityState.isGranted,
                            isRequired = false,
                            onRequest = {
                                activity?.let {
                                    PermissionManager.requestAccessibilityPermission(it)
                                }
                            }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // 모든 권한 한번에 요청 버튼 (콜매니저와 동일)
            if (!allBasicGranted) {
                val ungrantedCount = basicPermissions.count { 
                    permissionStates[it]?.isGranted != true 
                }
                
                Button(
                    onClick = {
                        activity?.let {
                            PermissionManager.requestPermissions(it)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFAB00), // 콜매니저와 동일한 Primary 색상
                        contentColor = Color(0xFF000000) // OnPrimary 색상
                    )
                ) {
                    Text("모든 권한 허용 (${ungrantedCount}개)")
                }
                
            } else {
                Button(
                    onClick = {
                        shouldNavigateAway = true
                        onPermissionsGranted()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFAB00), // 콜매니저와 동일한 Primary 색상
                        contentColor = Color(0xFF000000) // OnPrimary 색상
                    )
                ) {
                    Text("픽업 PTT 시작")
                }
            }
        }
    }
}

// 권한 상태 데이터 클래스 (콜매니저와 동일)
data class PermissionState(
    val isGranted: Boolean,
    val isRequired: Boolean,
    val description: String
)

/**
 * 개별 권한 항목 (콜매니저와 동일한 스타일)
 */
@Composable
fun PermissionItem(
    permission: String,
    state: PermissionState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.isGranted) Icons.Default.Check else Icons.Default.Warning,
                contentDescription = null,
                tint = if (state.isGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.error
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.description,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                if (state.isRequired) {
                    Text(
                        text = "필수",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Text(
                text = if (state.isGranted) "허용됨" else "대기 중",
                style = MaterialTheme.typography.labelMedium,
                color = if (state.isGranted) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 특수 권한 카드 (콜매니저와 동일한 스타일)
 */
@Composable
fun SpecialPermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    isRequired: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else 
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    
                    if (isRequired) {
                        Text(
                            text = "필수",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                if (isGranted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            if (!isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onRequest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("설정하기")
                }
            }
        }
    }
}
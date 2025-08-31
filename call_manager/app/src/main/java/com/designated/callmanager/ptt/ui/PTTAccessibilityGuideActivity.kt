package com.designated.callmanager.ptt.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.designated.callmanager.R
import com.designated.callmanager.ui.theme.CallManagerTheme
import com.designated.callmanager.ptt.service.PTTAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * PTT 접근성 서비스 설정 가이드 화면
 */
class PTTAccessibilityGuideActivity : ComponentActivity() {
    
    private val TAG = "PTTAccessibilityGuide"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "PTT Accessibility Guide Activity created")
        
        setContent {
            CallManagerTheme {
                PTTAccessibilityGuideScreen(
                    onNavigateToSettings = { openAccessibilitySettings() },
                    onCheckServiceStatus = { isAccessibilityServiceEnabled() },
                    onFinish = { finish() }
                )
            }
        }
    }
    
    /**
     * 접근성 설정 화면으로 이동
     */
    private fun openAccessibilitySettings() {
        try {
            Log.d(TAG, "Opening accessibility settings")
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings", e)
        }
    }
    
    /**
     * 접근성 서비스 활성화 상태 확인
     */
    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            
            val serviceName = "${packageName}/${PTTAccessibilityService::class.java.name}"
            val isEnabled = enabledServices.contains(serviceName)
            
            Log.d(TAG, "Accessibility service enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility service status", e)
            false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PTTAccessibilityGuideScreen(
    onNavigateToSettings: () -> Unit,
    onCheckServiceStatus: () -> Boolean,
    onFinish: () -> Unit
) {
    var isServiceEnabled by remember { mutableStateOf(false) }
    var currentStep by remember { mutableStateOf(0) }
    
    // 1초마다 서비스 상태 체크
    LaunchedEffect(Unit) {
        while (isActive) {
            isServiceEnabled = onCheckServiceStatus()
            delay(1000)
        }
    }
    
    // 서비스가 활성화되면 완료 단계로 이동
    LaunchedEffect(isServiceEnabled) {
        if (isServiceEnabled) {
            currentStep = 4 // 완료 단계
            delay(2000) // 2초 후 자동 종료
            onFinish()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PTT 설정 가이드",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onFinish) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // 진행 상태 표시
                ProgressCard(
                    isServiceEnabled = isServiceEnabled,
                    currentStep = currentStep
                )
            }
            
            item {
                if (isServiceEnabled) {
                    SuccessCard(onFinish = onFinish)
                } else {
                    SetupGuideCard(
                        currentStep = currentStep,
                        onNavigateToSettings = onNavigateToSettings,
                        onNextStep = { currentStep = it }
                    )
                }
            }
            
            item {
                // 추가 정보 카드
                InfoCard()
            }
        }
    }
}

@Composable
fun ProgressCard(
    isServiceEnabled: Boolean,
    currentStep: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isServiceEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val progress = if (isServiceEnabled) 1f else (currentStep / 4f)
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = if (isServiceEnabled) Color.White else MaterialTheme.colorScheme.primary,
                trackColor = if (isServiceEnabled) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isServiceEnabled) "설정 완료!" else "설정 진행 중...",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isServiceEnabled) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SuccessCard(onFinish: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(64.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "설정 완료!",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "이제 볼륨키로 PTT를 사용할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White)
            ) {
                Text(
                    text = "완료",
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
fun SetupGuideCard(
    currentStep: Int,
    onNavigateToSettings: () -> Unit,
    onNextStep: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "PTT 접근성 서비스 설정",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "볼륨키로 PTT를 사용하려면 다음 단계를 따라주세요:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Step 1
            StepItem(
                stepNumber = 1,
                title = "접근성 설정 열기",
                description = "아래 버튼을 눌러 접근성 설정으로 이동하세요",
                icon = Icons.Default.Settings,
                isActive = currentStep <= 1,
                action = {
                    Button(
                        onClick = {
                            onNavigateToSettings()
                            onNextStep(2)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("설정으로 이동")
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Step 2
            StepItem(
                stepNumber = 2,
                title = "Call Manager PTT 찾기",
                description = "설치된 서비스 목록에서 'Call Manager PTT'를 찾으세요",
                icon = Icons.Default.Search,
                isActive = currentStep == 2
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Step 3
            StepItem(
                stepNumber = 3,
                title = "서비스 활성화",
                description = "스위치를 켜서 서비스를 활성화하세요",
                icon = Icons.Default.ToggleOn,
                isActive = currentStep == 3
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Step 4
            StepItem(
                stepNumber = 4,
                title = "권한 허용",
                description = "팝업이 나타나면 '허용'을 선택하세요",
                icon = Icons.Default.Security,
                isActive = currentStep == 4
            )
        }
    }
}

@Composable
fun StepItem(
    stepNumber: Int,
    title: String,
    description: String,
    icon: ImageVector,
    isActive: Boolean,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 단계 번호
        Surface(
            modifier = Modifier.size(32.dp),
            shape = RoundedCornerShape(16.dp),
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // 내용
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            action?.let {
                Spacer(modifier = Modifier.height(8.dp))
                it()
            }
        }
    }
}

@Composable
fun InfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Text(
                    text = "도움말",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "• 이 서비스는 볼륨키를 눌러 PTT 무전을 제어합니다\n" +
                      "• 서비스는 필요한 최소한의 권한만 요청합니다\n" +
                      "• 설정 완료 후 볼륨 Up/Down 키로 PTT를 사용할 수 있습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
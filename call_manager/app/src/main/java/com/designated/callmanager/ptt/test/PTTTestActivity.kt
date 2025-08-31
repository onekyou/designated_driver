package com.designated.callmanager.ptt.test

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.designated.callmanager.ptt.manager.PTTServiceManager
import com.designated.callmanager.service.PTTManagerService
import com.designated.callmanager.ui.theme.CallManagerTheme
import kotlinx.coroutines.launch

/**
 * PTT 시스템 테스트용 화면
 */
class PTTTestActivity : ComponentActivity() {
    
    private lateinit var pttServiceManager: PTTServiceManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        pttServiceManager = PTTServiceManager.getInstance(this)
        
        setContent {
            CallManagerTheme {
                PTTTestScreen()
            }
        }
    }
    
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun PTTTestScreen() {
        var serviceStatus by remember { mutableStateOf("확인 중...") }
        var isConnected by remember { mutableStateOf(false) }
        
        // 서비스 상태 모니터링
        LaunchedEffect(Unit) {
            pttServiceManager.serviceState.collect { state ->
                serviceStatus = state.toString()
            }
        }
        
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("PTT 테스트") })
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 서비스 상태 표시
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "서비스 상태",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = serviceStatus,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                
                // 연결 상태
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Agora 연결 상태",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isConnected) "연결됨" else "연결 안됨",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isConnected) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                // 테스트 버튼들
                Button(
                    onClick = {
                        lifecycleScope.launch {
                            pttServiceManager.startAllServices()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("PTT 서비스 시작")
                }
                
                Button(
                    onClick = {
                        // 테스트 채널 연결 (test_channel)
                        val pttManager = PTTManagerService.getInstance()
                        pttManager?.joinChannel("test_channel", 12345, "")
                        isConnected = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("테스트 채널 연결")
                }
                
                Button(
                    onClick = {
                        val pttManager = PTTManagerService.getInstance()
                        pttManager?.leaveChannel()
                        isConnected = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("채널 연결 해제")
                }
                
                // 테스트 가이드
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "테스트 방법",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = """
                            1. PTT 서비스 시작 버튼 클릭
                            2. 테스트 채널 연결 버튼 클릭
                            3. 볼륨 Up/Down 키를 길게 눌러보세요
                            4. 로그에서 PTT 동작 확인
                            
                            * 실제 음성 송수신 테스트는 2대의 기기가 필요합니다
                            """.trimIndent(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
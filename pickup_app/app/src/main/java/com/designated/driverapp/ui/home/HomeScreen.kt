package com.designated.driverapp.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import android.app.Activity

@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar
@Composable
fun HomeScreen(navController: NavController? = null) {
    val context = LocalContext.current
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("기사님 홈") },
                actions = {
                    // 로그아웃 버튼
                    IconButton(onClick = { 
                        // Firebase Auth를 사용하여 로그아웃하고 앱 종료
                        logoutUserAndExitApp(context)
                    }) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "로그아웃",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("로그인 성공! 홈 화면입니다.")
            // Add driver-specific UI elements here later
        }
    }
}

// 로그아웃 후 앱 종료 함수
private fun logoutUserAndExitApp(context: Context) {
    try {
        // Firebase Auth에서 로그아웃 (세션만 종료)
        Firebase.auth.signOut()
        
        // 자동 로그인 설정과 로그인 정보는 유지 (SharedPreferences에서 데이터 유지)
        // -> 이렇게 하면 앱 재시작 시 LoginViewModel의 init 블록에서 자동 로그인 처리
        
        // 로그아웃 성공 메시지
        Toast.makeText(context, "로그아웃되었습니다. 앱을 종료합니다.", Toast.LENGTH_SHORT).show()
        
        // 앱 종료 (Activity 종료)
        if (context is Activity) {
            context.finish()
        }
    } catch (e: Exception) {
        // 로그아웃 실패 메시지
        Toast.makeText(context, "로그아웃 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
    }
} 
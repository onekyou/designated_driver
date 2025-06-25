package com.designated.callmanager.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainDashboardScreen(
    onNavigateToPendingDrivers: () -> Unit,
    onNavigateToCallManagement: () -> Unit
    // 다른 네비게이션 콜백 추가...
) {
    // TODO: 실제 대시보드 UI 구현
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("메인 대시보드 (구현 예정)")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToPendingDrivers) {
            Text("기사 가입 승인 관리")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToCallManagement) {
            Text("실시간 호출 관리")
        }
        // 다른 메뉴 버튼들...
    }
} 
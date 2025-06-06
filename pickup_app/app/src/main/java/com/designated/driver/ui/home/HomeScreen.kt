package com.designated.driver.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.driver.model.CallInfo // Import CallInfo
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class) // Needed for Scaffold, TopAppBar etc.
@Composable
fun HomeScreen(
    viewModel: DriverViewModel = viewModel() // Inject ViewModel
) {
    val assignedCalls by viewModel.assignedCalls.collectAsState()

    // 다크모드 색상 설정
    val backgroundColor = Color(0xFF121212) // 다크모드 배경색
    val deepYellow = Color(0xFFFFD700) // 딥엘로우 색상

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("배정된 콜", color = deepYellow) },
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = backgroundColor) // 다크모드 배경색 적용
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor) // 다크모드 배경색 적용
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (assignedCalls.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("현재 배정된 콜이 없습니다.", color = deepYellow) // 딥엘로우 색상 적용
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(assignedCalls) { call ->
                        AssignedCallItem(call = call) {
                            println("Clicked call: ${call.id}")
                        }
                    }
                }
            }
        }
    }
}

// Simple Composable to display a single assigned call item
@Composable
fun AssignedCallItem(call: CallInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "출발지: ${call.location}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White // 다크모드 텍스트 색상
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "고객명: ${call.customerName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White // 다크모드 텍스트 색상
            )
            Text(
                text = "연락처: ${call.phoneNumber}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White // 다크모드 텍스트 색상
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "배정 시간: ${formatTimeAgo(call.assignedTimestamp ?: call.timestamp)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Helper function for time formatting (consider moving to a common place)
fun formatTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60000 -> "방금 전"
        diff < 3600000 -> "${diff / 60000}분 전"
        diff < 86400000 -> "${diff / 3600000}시간 전"
        else -> "${diff / 86400000}일 전"
    }
} 
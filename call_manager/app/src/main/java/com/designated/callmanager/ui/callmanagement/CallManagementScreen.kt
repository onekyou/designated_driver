package com.designated.callmanager.ui.callmanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallManagementScreen(
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: CallManagementViewModel = viewModel()
) {

    val callsState by viewModel.calls.collectAsState()
    var selectedCall by remember { mutableStateOf<CallInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth()) {
                        Text("실시간 호출 관리", modifier = Modifier.align(Alignment.Center), color = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Default.Home, contentDescription = "홈", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (callsState.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("호출이 없습니다", color = Color.White)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(callsState) { call ->
                        CallItemCard(call = call, onClick = { selectedCall = call })
                    }
                }
            }
        }
    }

    // 상세 다이얼로그
    if (selectedCall != null) {
        CallDetailDialog(
            call = selectedCall!!,
            onDismiss = { selectedCall = null },
            onAssign = { cId ->
                // TODO: 기사 선택 UI 연동, 임시 driverId "driver123"
                viewModel.assignCall(cId, "driver123", "기사")
                selectedCall = null
            },
            onShare = {
                // TODO: shareCall 구현 연결 예정
            },
            onCancel = { cId ->
                viewModel.cancelCall(cId)
                selectedCall = null
            }
        )
    }
}

@Composable
private fun CallItemCard(call: CallInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable { onClick() }
        ) {
            Text("전화: ${call.phoneNumber}", color = Color.White, fontWeight = FontWeight.Bold)
            Text("주소: ${call.customerAddress ?: "-"}", color = Color(0xFFCCCCCC))
            Text("상태: ${CallStatus.fromFirestoreValue(call.status).displayName}", color = Color(0xFFFFB000))
        }
    }
}

@Composable
private fun CallDetailDialog(
    call: CallInfo,
    onDismiss: () -> Unit,
    onAssign: (String) -> Unit,
    onShare: () -> Unit,
    onCancel: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("콜 상세", color = Color.White) },
        text = {
            Column {
                Text("전화번호: ${call.phoneNumber}", color = Color.White)
                Text("주소: ${call.customerAddress ?: "-"}", color = Color.White)
                Text("상태: ${CallStatus.fromFirestoreValue(call.status).displayName}", color = Color.White)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                Button(onClick = { onAssign(call.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) {
                    Text("배정")
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = onShare, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50), contentColor = Color.White)) {
                    Text("공유")
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = { onCancel(call.id) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444), contentColor = Color.White)) {
                    Text("취소")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = Color.White) }
        },
        containerColor = Color(0xFF2A2A2A)
    )
} 
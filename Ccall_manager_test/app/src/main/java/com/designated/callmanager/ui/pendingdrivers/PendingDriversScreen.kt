package com.designated.callmanager.ui.pendingdrivers

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.OfficeItem
import com.designated.callmanager.data.PendingDriverInfo
import com.designated.callmanager.data.RegionItem
import android.text.format.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingDriversScreen(
    viewModel: PendingDriversViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val approvalState by viewModel.approvalState.collectAsState()

    val context = LocalContext.current

    // 승인/거절 결과 메시지 처리
    LaunchedEffect(approvalState) {
        when (val state = approvalState) {
            is DriverApprovalState.Success -> {
                val message = if (state.approved) "${state.driverName} 기사님을 승인했습니다." else "${state.driverName} 기사님의 가입 요청을 거절했습니다."
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                viewModel.resetApprovalState() // 상태 초기화
            }
            is DriverApprovalState.Error -> {
                Toast.makeText(context, "오류: ${state.message}", Toast.LENGTH_LONG).show()
                viewModel.resetApprovalState()
            }
            else -> Unit // Idle 또는 Loading 상태
        }
    }

    // 승인 다이얼로그 상태
    var showApprovalDialog by remember { mutableStateOf(false) }
    var driverToApprove by remember { mutableStateOf<PendingDriverInfo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("기사 가입 승인") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when (val state = uiState) {
                is PendingDriversUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is PendingDriversUiState.Success -> {
                    if (state.drivers.isEmpty()) {
                        Text("승인 대기 중인 기사가 없습니다.", modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.drivers) { driver ->
                                PendingDriverCard(
                                    driverInfo = driver,
                                    onApproveClick = {
                                        driverToApprove = it
                                        showApprovalDialog = true
                                    },
                                    onRejectClick = { viewModel.rejectDriver(it) },
                                    isProcessing = approvalState is DriverApprovalState.Loading // 처리 중일 때 버튼 비활성화
                                )
                            }
                        }
                    }
                }
                is PendingDriversUiState.Error -> {
                    Text("오류: ${state.message}", modifier = Modifier.align(Alignment.Center).padding(16.dp))
                }
            }
        }
    }

    // --- 승인 확인 다이얼로그 ---
    if (showApprovalDialog && driverToApprove != null) {
        AlertDialog(
            onDismissRequest = { showApprovalDialog = false },
            title = { Text("${driverToApprove!!.name} 기사 승인") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("다음 정보로 기사를 승인하시겠습니까?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("대상 지역 ID: ${driverToApprove!!.targetRegionId}", fontWeight = FontWeight.Bold)
                    Text("대상 사무실 ID: ${driverToApprove!!.targetOfficeId}", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("(만약 대상 지역/사무실 정보가 올바르지 않다면, 먼저 기사 가입 정보를 수정해야 합니다.)", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.approveDriver(driverToApprove!!)
                        showApprovalDialog = false
                    },
                    enabled = approvalState !is DriverApprovalState.Loading // 로딩 중 아닐 때만 활성화
                ) {
                    if (approvalState is DriverApprovalState.Loading && driverToApprove != null) { // 특정 기사 처리 중 표시 - TODO: 이게 정확히 동작할지 확인 필요
                         CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                       Text("승인")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showApprovalDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
    // --- ---
}

@Composable
fun PendingDriverCard(
    driverInfo: PendingDriverInfo,
    onApproveClick: (PendingDriverInfo) -> Unit,
    onRejectClick: (PendingDriverInfo) -> Unit,
    isProcessing: Boolean // 승인/거절 처리 중 여부
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("이름: ${driverInfo.name}", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text("연락처: ${driverInfo.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
            Text("이메일: ${driverInfo.email}", style = MaterialTheme.typography.bodyMedium)
            Text("요청 유형: ${driverInfo.driverType}", style = MaterialTheme.typography.bodyMedium)
             driverInfo.requestedAt?.toDate()?.let { // Timestamp를 Date로 변환하여 표시
                 Text("신청일시: ${DateFormat.format("yyyy-MM-dd hh:mm a", it)}", style = MaterialTheme.typography.bodySmall)
             }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onApproveClick(driverInfo) },
                    enabled = !isProcessing, // 처리 중 아닐 때 활성화
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "승인", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("승인")
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onRejectClick(driverInfo) },
                    enabled = !isProcessing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error) // 빨간색 텍스트
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "거절", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("거절")
                }
            }
        }
    }
} 
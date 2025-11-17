package com.designated.callmanager.ui.pendingdrivers

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
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
    regionId: String,
    officeId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: PendingDriversViewModel = viewModel(
        factory = PendingDriversViewModel.Factory(
            application = context.applicationContext as android.app.Application,
            regionId = regionId,
            officeId = officeId
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val approvedDriversState by viewModel.approvedDriversState.collectAsState()
    val approvalState by viewModel.approvalState.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    var showApprovalDialog by remember { mutableStateOf(false) }
    var driverToApprove by remember { mutableStateOf<PendingDriverInfo?>(null) }
    var showRetireDialog by remember { mutableStateOf(false) }
    var driverToRetire by remember { mutableStateOf<com.designated.callmanager.data.DriverInfo?>(null) }
    var processingDriverId by remember { mutableStateOf<String?>(null) }

    // 탭 변경 시 해당 데이터 로드
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.fetchApprovedDrivers()
        }
    }

    LaunchedEffect(approvalState) {
        when (val state = approvalState) {
            is DriverApprovalState.Loading -> {
                // 처리 중인 기사 ID 설정
            }
            is DriverApprovalState.Success -> {
                val message = if (state.approved) "${state.driverName} 기사님을 승인했습니다." else "${state.driverName} 기사님을 삭제했습니다."
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                // 성공 후 잠시 delay 후 processingDriverId 리셋
                kotlinx.coroutines.delay(1000)
                processingDriverId = null
                viewModel.resetApprovalState()
            }
            is DriverApprovalState.Error -> {
                Toast.makeText(context, "오류: ${state.message}", Toast.LENGTH_LONG).show()
                processingDriverId = null
                viewModel.resetApprovalState()
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("기사 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            // 탭 Row
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("승인 대기") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("승인된 기사") }
                )
            }

            // 탭 콘텐츠
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    0 -> {
                        // 승인 대기 목록
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
                                                onDeleteClick = { viewModel.deleteDriver(it) },
                                                isProcessing = approvalState is DriverApprovalState.Loading,
                                                processingDriverId = processingDriverId
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
                    1 -> {
                        // 승인된 기사 목록
                        when (val state = approvedDriversState) {
                            is ApprovedDriversUiState.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                            is ApprovedDriversUiState.Success -> {
                                if (state.drivers.isEmpty()) {
                                    Text("승인된 기사가 없습니다.", modifier = Modifier.align(Alignment.Center))
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(state.drivers) { driver ->
                                            ApprovedDriverCard(
                                                driverInfo = driver,
                                                onRetireClick = {
                                                    driverToRetire = it
                                                    showRetireDialog = true
                                                },
                                                isProcessing = approvalState is DriverApprovalState.Loading
                                            )
                                        }
                                    }
                                }
                            }
                            is ApprovedDriversUiState.Error -> {
                                Text("오류: ${state.message}", modifier = Modifier.align(Alignment.Center).padding(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

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
                        processingDriverId = driverToApprove!!.authUid
                        viewModel.approveDriver(driverToApprove!!)
                        showApprovalDialog = false
                    },
                    enabled = approvalState !is DriverApprovalState.Loading
                ) {
                    if (approvalState is DriverApprovalState.Loading && driverToApprove != null) {
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

    if (showRetireDialog && driverToRetire != null) {
        AlertDialog(
            onDismissRequest = { showRetireDialog = false },
            title = { Text("${driverToRetire!!.name} 기사 퇴사 처리") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("정말로 이 기사를 퇴사 처리하시겠습니까?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("⚠️ 이 작업은 되돌릴 수 없습니다.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• 기사 정보가 삭제됩니다")
                    Text("• Firebase 인증 계정이 삭제됩니다")
                    Text("• 기사 앱 로그인이 불가능해집니다")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.retireDriver(driverToRetire!!)
                        showRetireDialog = false
                    },
                    enabled = approvalState !is DriverApprovalState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("퇴사 처리")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRetireDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun PendingDriverCard(
    driverInfo: PendingDriverInfo,
    onApproveClick: (PendingDriverInfo) -> Unit,
    onDeleteClick: (PendingDriverInfo) -> Unit,
    isProcessing: Boolean,
    processingDriverId: String? = null
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
            Text("상태: ${driverInfo.status}", style = MaterialTheme.typography.bodyMedium)
             driverInfo.requestedAt?.toDate()?.let {
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
                    enabled = !isProcessing &&
                             driverInfo.status == "승인대기중" &&
                             driverInfo.status != "승인완료" &&
                             processingDriverId != driverInfo.authUid,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (processingDriverId == driverInfo.authUid && isProcessing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = "승인", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (driverInfo.status == "승인완료") "승인완료" else "승인")
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onDeleteClick(driverInfo) },
                    enabled = !isProcessing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "삭제", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("삭제")
                }
            }
        }
    }
}

@Composable
fun ApprovedDriverCard(
    driverInfo: com.designated.callmanager.data.DriverInfo,
    onRetireClick: (com.designated.callmanager.data.DriverInfo) -> Unit,
    isProcessing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("이름: ${driverInfo.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("연락처: ${driverInfo.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
            Text("이메일: ${driverInfo.email ?: "없음"}", style = MaterialTheme.typography.bodyMedium)
            Text("유형: ${driverInfo.driverType ?: "대리기사"}", style = MaterialTheme.typography.bodyMedium)
            Text("상태: ${driverInfo.status}", style = MaterialTheme.typography.bodyMedium)
            driverInfo.approvedAt?.toDate()?.let {
                Text("승인일: ${DateFormat.format("yyyy-MM-dd hh:mm a", it)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onRetireClick(driverInfo) },
                    enabled = !isProcessing,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = "퇴사", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("퇴사 처리")
                }
            }
        }
    }
}
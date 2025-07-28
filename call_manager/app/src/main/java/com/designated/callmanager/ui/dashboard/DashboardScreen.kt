@file:OptIn(ExperimentalMaterial3Api::class)
package com.designated.callmanager.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.CallStatus
import com.designated.callmanager.data.DriverInfo
import com.designated.callmanager.data.DriverStatus
import com.designated.callmanager.data.SharedCallInfo
import com.designated.callmanager.ui.dashboard.DashboardViewModel.Companion.formatTimeAgo
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.NavigationBarItemDefaults
import com.designated.callmanager.ui.shared.SharedCallSettingsScreen
import androidx.compose.material.icons.filled.Check


private const val TAG = "DashboardScreen"

// 이 함수들은 string 리소스가 준비될 때까지 임시로 사용합니다.
@Composable
fun CallStatus.getDisplayName(): String {
    return when (this) {
        CallStatus.WAITING -> "대기"
        CallStatus.PENDING -> "기사승인대기"
        CallStatus.ASSIGNED -> "배차완료"
        CallStatus.ACCEPTED -> "수락"
        CallStatus.PICKUP_COMPLETE -> "픽업완료"
        CallStatus.IN_PROGRESS -> "운행중"
        CallStatus.AWAITING_SETTLEMENT -> "정산대기"
        CallStatus.COMPLETED -> "완료"
        CallStatus.CANCELED -> "취소"
        CallStatus.HOLD -> "보류"
        CallStatus.UNKNOWN -> "알수없음"
    }
}

@Composable
fun DriverStatus.getDisplayName(): String {
    return when (this) {
        DriverStatus.WAITING -> "대기중"
        DriverStatus.ON_TRIP -> "운행중"
        DriverStatus.PREPARING -> "운행준비"
        DriverStatus.ONLINE -> "온라인"
        DriverStatus.OFFLINE -> "오프라인"
        else -> "알수없음"
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onLogout: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
) {
    val callInfoForDialog by viewModel.callInfoForDialog.collectAsStateWithLifecycle()
    val calls by viewModel.calls.collectAsStateWithLifecycle()
    val drivers by viewModel.drivers.collectAsStateWithLifecycle()
    val sharedCalls by viewModel.sharedCalls.collectAsState()
    val officeName by viewModel.officeName.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var callIdForDriverAssignment by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Popups states
    val showDriverLoginPopup by viewModel.showDriverLoginPopup.collectAsStateWithLifecycle()
    val loggedInDriverName by viewModel.loggedInDriverName.collectAsStateWithLifecycle()
    val showApprovalPopup by viewModel.showApprovalPopup.collectAsStateWithLifecycle()
    val driverForApproval by viewModel.driverForApproval.collectAsStateWithLifecycle()
    val approvalActionState by viewModel.approvalActionState.collectAsStateWithLifecycle()
    val showDriverLogoutPopup by viewModel.showDriverLogoutPopup.collectAsStateWithLifecycle()
    val loggedOutDriverName by viewModel.loggedOutDriverName.collectAsStateWithLifecycle()
    val showTripStartedPopup by viewModel.showTripStartedPopup.collectAsStateWithLifecycle()
    val tripStartedInfo by viewModel.tripStartedInfo.collectAsStateWithLifecycle()
    val showTripCompletedPopup by viewModel.showTripCompletedPopup.collectAsStateWithLifecycle()
    val tripCompletedInfo by viewModel.tripCompletedInfo.collectAsStateWithLifecycle()
    val showCanceledCallPopup by viewModel.showCanceledCallPopup.collectAsStateWithLifecycle()
    val canceledCallInfo by viewModel.canceledCallInfo.collectAsStateWithLifecycle()

    // ★★★ 새로운 콜 팝업 상태 추가 ★★★
    val showNewCallPopup by viewModel.showNewCallPopup.collectAsStateWithLifecycle()
    val newCallInfo by viewModel.newCallInfo.collectAsStateWithLifecycle()

    // 디버그 로그 추가
    Log.d(TAG, "Recomposing... showNewCallPopup: $showNewCallPopup, newCallInfo is null: ${newCallInfo == null}")

    var showSharedSettings by remember { mutableStateOf(false) }

    // State for shared call accept dialog
    var selectedSharedCall by remember { mutableStateOf<SharedCallInfo?>(null) }
    var showSharedAcceptDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startForegroundService(context)
    }

    LaunchedEffect(approvalActionState) {
        when (val state = approvalActionState) {
            is DriverApprovalActionState.Success -> {
                val actionText = if (state.action == "approved") "승인됨" else "거절됨"
                Toast.makeText(context, "${state.driverId} 기사님을 ${actionText} 처리했습니다.", Toast.LENGTH_SHORT).show()
                viewModel.resetApprovalActionState()
            }
            is DriverApprovalActionState.Error -> {
                snackbarHostState.showSnackbar(
                    message = "오류: ${state.message}",
                    duration = SnackbarDuration.Long
                )
                viewModel.resetApprovalActionState()
            }
            else -> { /* Idle, Loading */ }
        }
    }

    // ★★★ 알림 설정을 확인하여 알림음 재생 ★★★
    LaunchedEffect(showDriverLoginPopup, showApprovalPopup, showDriverLogoutPopup, showTripStartedPopup, showTripCompletedPopup, showCanceledCallPopup) {
        if(showDriverLoginPopup || showApprovalPopup || showDriverLogoutPopup || showTripStartedPopup || showTripCompletedPopup || showCanceledCallPopup) {
            val prefs = context.getSharedPreferences("call_manager_settings", Context.MODE_PRIVATE)
            val driverEventNotificationEnabled = prefs.getBoolean("driver_event_notification", true)
            if (driverEventNotificationEnabled) {
                playNotificationSound(context)
            }
        }
    }

    // ★★★ 새로운 콜 팝업도 알림음 재생에 포함 (알림 설정 확인) ★★★
    LaunchedEffect(showNewCallPopup) {
        if (showNewCallPopup) {
            val prefs = context.getSharedPreferences("call_manager_settings", Context.MODE_PRIVATE)
            val newCallNotificationEnabled = prefs.getBoolean("new_call_notification", true)
            if (newCallNotificationEnabled) {
                playNotificationSound(context)
            }
        }
    }



    if (callInfoForDialog != null) {
        CallInfoDialog(
            callInfo = callInfoForDialog!!,
            onDismiss = { viewModel.dismissCallDialog() },
            onAssignRequest = {
                Log.e(TAG, "onAssignRequest: 기사 배정 요청. Call ID: ${callInfoForDialog!!.id}")
                callIdForDriverAssignment = callInfoForDialog!!.id
            },
            onHold = { viewModel.updateCallStatus(callInfoForDialog!!.id, CallStatus.HOLD) },
            onDelete = { viewModel.cancelCall(callInfoForDialog!!.id) }
        )
    }

    if (callIdForDriverAssignment != null) {
        val waitingDrivers = drivers.filter { driver ->
            val statusString = driver.status?.trim() ?: ""
                val statusEnum = DriverStatus.fromString(statusString)
                val isEligible = statusEnum == DriverStatus.WAITING || statusEnum == DriverStatus.ONLINE
                isEligible
        }

        DriverListDialog(
            drivers = waitingDrivers,
            onDismiss = { callIdForDriverAssignment = null },
            onDriverSelect = { driver ->
                val callToAssign = calls.find { it.id == callIdForDriverAssignment }
                if (callToAssign != null) {
                    viewModel.assignCallToDriver(callToAssign, driver.id)
                }
                callIdForDriverAssignment = null
            }
        )
    }

    // Popup Dialogs
    if (showDriverLoginPopup && loggedInDriverName != null) {
        InfoPopup(
            title = "기사 로그인",
            content = "$loggedInDriverName 기사님이 로그인했습니다.",
            onDismiss = { viewModel.dismissDriverLoginPopup() }
        )
    }

    if (showApprovalPopup && driverForApproval != null) {
        ApprovalDialog(
            driverInfo = driverForApproval!!,
            onDismiss = { viewModel.dismissApprovalPopup() },
            onApprove = { viewModel.approveDriver(driverForApproval!!.id) },
            onReject = { viewModel.rejectDriver(driverForApproval!!.id) },
            approvalActionState = approvalActionState
        )
    }

    if (showDriverLogoutPopup && loggedOutDriverName != null) {
        InfoPopup(
            title = "기사 로그아웃",
            content = "$loggedOutDriverName 기사님이 로그아웃했습니다.",
            onDismiss = { viewModel.dismissDriverLogoutPopup() }
        )
    }

    if(showTripStartedPopup && tripStartedInfo != null){
        TripStartedPopup(
            driverName = tripStartedInfo!!.first,
            driverPhone = tripStartedInfo!!.second,
            tripSummary = tripStartedInfo!!.third,
            onDismiss = { viewModel.dismissTripStartedPopup() }
        )
    }

    if(showTripCompletedPopup && tripCompletedInfo != null){
        InfoPopup(
            title = "운행 완료",
            content = "${tripCompletedInfo!!.first} 기사님이 ${tripCompletedInfo!!.second} 고객님의 운행을 완료했습니다.",
            onDismiss = { viewModel.dismissTripCompletedPopup() }
        )
    }

    if(showCanceledCallPopup && canceledCallInfo != null){
        InfoPopup(
            title = "호출 취소",
            content = "${canceledCallInfo!!.first} 기사님의 ${canceledCallInfo!!.second} 고객 호출이 취소되었습니다.",
            onDismiss = { viewModel.dismissCanceledCallPopup() }
        )
    }

    // ★★★ 새로운 WAITING 콜 감지 시 즉시 배차 팝업 ★★★
    if (showNewCallPopup && newCallInfo != null) {
        // 디버그 로그 추가
        Log.d(TAG, "Showing NewCallAssignmentDialog for call ID: ${newCallInfo?.id}")

        val waitingDrivers = drivers.filter { driver ->
            val statusString = driver.status?.trim() ?: ""
            val statusEnum = DriverStatus.fromString(statusString)
            statusEnum == DriverStatus.WAITING || statusEnum == DriverStatus.ONLINE
        }

        NewCallAssignmentDialog(
            callInfo = newCallInfo!!,
            availableDrivers = waitingDrivers,
            onDismiss = { viewModel.dismissNewCallPopup() },
            onDriverSelect = { driver ->
                viewModel.assignNewCall(driver.id)
            },
            onHold = { 
                viewModel.updateCallStatus(newCallInfo!!.id, CallStatus.HOLD)
                viewModel.dismissNewCallPopup()
            },
            onShare = { departure, destination, fare ->
                viewModel.shareCall(newCallInfo!!, departure, destination, fare)
            }
        )
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = officeName ?: "사무실 정보 로딩 중...") },
                navigationIcon = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "로그아웃")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "설정")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            DriverBottomBar(drivers = drivers)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CallListContainer(
                modifier = Modifier.fillMaxWidth().weight(1f),
                calls = calls,
                title = "내부 호출 목록",
                onCallClick = { callInfo -> viewModel.showCallDialog(callInfo.id) },
                onAddCallClick = { viewModel.createPlaceholderCall() }
            )

            // 공유 콜 리스트 (하단)
            SharedCallListContainer(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                sharedCalls = sharedCalls,
                onAccept = { call ->
                    selectedSharedCall = call
                    showSharedAcceptDialog = true
                },
                onSettings = { showSharedSettings = true }
            )
            if (showSharedSettings) {
                SharedCallSettingsScreen(onNavigateBack = { showSharedSettings = false })
            }
        }
    }

    // ---- Shared Call Accept Dialog ----
    if (showSharedAcceptDialog && selectedSharedCall != null) {
        val call = selectedSharedCall!!
        val waitingDrivers = drivers.filter { driver ->
            val statusEnum = DriverStatus.fromString(driver.status?.trim() ?: "")
            statusEnum == DriverStatus.WAITING || statusEnum == DriverStatus.ONLINE
        }
        SharedCallAcceptDialog(
            sharedCall = call,
            availableDrivers = waitingDrivers,
            onDismiss = { showSharedAcceptDialog = false },
            onConfirm = { dep, dest, fare, driver ->
                viewModel.claimSharedCallWithDetails(
                    sharedCallId = call.id,
                    departure = dep,
                    destination = dest,
                    fare = fare,
                    driverId = driver?.id
                )
                showSharedAcceptDialog = false
            }
        )
    }
}

fun playNotificationSound(context: Context) {
    try {
        val notification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val r = RingtoneManager.getRingtone(context.applicationContext, notification)
        r.play()
    } catch (e: Exception) {
        Log.e(TAG, "Error playing notification sound", e)
    }
}

@Composable
fun CallListContainer(
    modifier: Modifier = Modifier,
    calls: List<CallInfo>,
    title: String,
    onCallClick: (CallInfo) -> Unit,
    onAddCallClick: () -> Unit = {}
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onAddCallClick) {
                Icon(Icons.Filled.Add, contentDescription = "새 호출 추가")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))) {
            items(calls, key = { it.id }) { call ->
                CallCard(call = call, onCallClick = { onCallClick(call) })
                Divider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun CallCard(call: CallInfo, onCallClick: (CallInfo) -> Unit) {
    val callStatus = remember(call.status) { CallStatus.fromFirestoreValue(call.status) }
    val statusDisplayName = callStatus.getDisplayName()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onCallClick(call) })
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val displayText = (if (!call.customerName.isNullOrBlank()) {
                call.customerName
            } else {
                call.customerAddress
            }) ?: "정보 없음"

            Text(text = displayText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                text = formatTimeAgo(call.timestamp.toDate().time),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            call.assignedDriverName?.let {
                Text(
                    text = "배정: $it 기사",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = statusDisplayName,
            style = MaterialTheme.typography.labelMedium,
            color = when (callStatus) {
                CallStatus.PENDING -> MaterialTheme.colorScheme.error
                CallStatus.ASSIGNED -> MaterialTheme.colorScheme.tertiary
                CallStatus.COMPLETED -> Color.Gray
                CallStatus.CANCELED -> Color.Gray
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
fun DriverStatusContainer(
    modifier: Modifier = Modifier,
    drivers: List<DriverInfo>
) {
    Column(modifier) {
        Text("기사 현황", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxSize(),
            elevation = CardDefaults.cardElevation(2.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            LazyColumn(contentPadding = PaddingValues(8.dp)) {
                items(drivers, key = { it.id }) { driver ->
                    DriverItem(driver = driver)
                    Divider(color = Color.LightGray, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun DriverItem(driver: DriverInfo) {
    val driverStatus = remember(driver.status) { DriverStatus.fromString(driver.status) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = when (driverStatus) {
                        DriverStatus.WAITING -> Color.Green
                        DriverStatus.ONLINE -> Color.Green
                        DriverStatus.ON_TRIP -> Color.Red
                        DriverStatus.PREPARING -> Color(0xFFFFA000)
                        DriverStatus.OFFLINE -> Color.Gray
                        else -> Color.Red
                    },
                    shape = CircleShape
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = driver.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = driverStatus.getDisplayName(),
            style = MaterialTheme.typography.bodySmall,
            color = Color.DarkGray
        )
    }
}

@Composable
fun CallInfoDialog(
    callInfo: CallInfo,
    onDismiss: () -> Unit,
    onAssignRequest: () -> Unit,
    onHold: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("호출 정보 (${callInfo.id.takeLast(4)})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("고객명: ${callInfo.customerName ?: "없음"}")
                Text("연락처: ${callInfo.phoneNumber}")
                Text("상세주소: ${callInfo.customerAddress ?: "없음"}")
                Text("상태: ${callInfo.status ?: "알수없음"}")
                callInfo.assignedDriverName?.let {
                    Text("배정된 기사: $it")
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text("닫기") }
                TextButton(onClick = onDelete) { Text("삭제") }
                TextButton(onClick = onHold) { Text("보류") }
                TextButton(onClick = {
                    onAssignRequest()
                    onDismiss()
                }) { Text("기사배정") }
            }
        },
        dismissButton = null
    )
}

@Composable
fun DriverListDialog(
    drivers: List<DriverInfo>,
    onDismiss: () -> Unit,
    onDriverSelect: (DriverInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("대기중인 기사 선택") },
        text = {
            LazyColumn {
                items(drivers) { driver ->
                    ListItem(
                        headlineContent = { Text(driver.name) },
                        modifier = Modifier.clickable { onDriverSelect(driver) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun ApprovalDialog(
    driverInfo: DriverInfo,
    onDismiss: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    approvalActionState: DriverApprovalActionState
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PersonAdd, contentDescription = "기사 승인") },
        title = { Text(text = "${driverInfo.name} 기사님 승인 요청") },
        text = { Text("가입을 승인하시겠습니까?") },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApprove,
                    enabled = approvalActionState !is DriverApprovalActionState.Loading
                ) { Text("승인") }
                Button(
                    onClick = onReject,
                    enabled = approvalActionState !is DriverApprovalActionState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("거절") }
            }
        },
        dismissButton = {
            if (approvalActionState is DriverApprovalActionState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        }
    )
}

@Composable
fun TripStartedPopup(driverName: String, driverPhone: String?, tripSummary: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PlayCircleOutline, contentDescription = "운행 시작") },
        title = { Text("운행 시작 알림") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("$driverName 기사님", fontWeight = FontWeight.Bold)
                Text(tripSummary)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if(!driverPhone.isNullOrBlank()){
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$driverPhone"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Phone, contentDescription = "기사에게 전화")
                    }
                }
                Button(onClick = onDismiss) { Text("확인") }
            }
        }
    )
}


@Composable
fun InfoPopup(title: String, content: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(content) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("확인") }
        }
    )
}

// ★★★ 새로운 콜 배정 다이얼로그 추가 ★★★
@Composable
fun NewCallAssignmentDialog(
    callInfo: CallInfo,
    availableDrivers: List<DriverInfo>,
    onDismiss: () -> Unit,
    onDriverSelect: (DriverInfo) -> Unit,
    onHold: () -> Unit,
    onShare: (departure: String, destination: String, fare: Int) -> Unit
) {
    val context = LocalContext.current
    
    var showShareDialog by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = {
            // 팝업 클릭 시 알람도 중지
            try {
                val ringtoneManager = RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                if (ringtoneManager.isPlaying) {
                    ringtoneManager.stop()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping notification sound", e)
            }
            onDismiss()
        },
        title = { Text("새로운 호출 접수", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 호출 정보 표시
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = callInfo.phoneNumber,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        callInfo.customerName?.let { 
                            Text(
                                text = it,
                                color = Color.White
                            ) 
                        }
                        callInfo.customerAddress?.let { 
                            Text(
                                text = it,
                                color = Color.White
                            ) 
                        }
                    }
                }
                
                // 대기중인 기사 목록
                if (availableDrivers.isNotEmpty()) {
                    Text("대기중인 기사 선택:", fontWeight = FontWeight.Medium)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(availableDrivers) { driver ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Log.d(TAG, "Driver selected: ${driver.name}/${driver.id}")
                                        onDriverSelect(driver)
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = driver.name,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = DriverStatus.fromString(driver.status).getDisplayName(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Green
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "현재 대기중인 기사가 없습니다.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    try {
                        val r = RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        if (r.isPlaying) r.stop()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping notification sound", e)
                    }
                    onHold()
                }) { Text("보류") }

                TextButton(onClick = {
                    try {
                        val r = RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        if (r.isPlaying) r.stop()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping notification sound", e)
                    }
                    onDismiss()
                }) { Text("나중에") }

                TextButton(onClick = { showShareDialog = true }) { Text("공유") }
            }
        },
        dismissButton = {
            TextButton(onClick = { showShareDialog = false }) { Text("취소") }
        }
    )

    if (showShareDialog) {
        var departure by remember { mutableStateOf("") }
        var destination by remember { mutableStateOf("") }
        var fareText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("공유 정보 입력", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = departure, onValueChange = { departure = it }, label = { Text("출발지") })
                    OutlinedTextField(value = destination, onValueChange = { destination = it }, label = { Text("도착지") })
                    OutlinedTextField(value = fareText, onValueChange = { fareText = it.filter { c -> c.isDigit() } }, label = { Text("요금") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fare = fareText.toIntOrNull() ?: 0
                    if (departure.isNotBlank() && destination.isNotBlank() && fare > 0) {
                        onShare(departure, destination, fare)
                        showShareDialog = false
                        onDismiss()
                    }
                }) { Text("공유") }
            },
            dismissButton = { TextButton(onClick = { showShareDialog = false }) { Text("취소") } }
        )
    }
}

// 공유 콜 리스트 컨테이너
@Composable
fun SharedCallListContainer(
    modifier: Modifier = Modifier,
    sharedCalls: List<SharedCallInfo>,
    onAccept: (SharedCallInfo) -> Unit,
    onSettings: () -> Unit
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("공유 콜", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = "공유콜 설정")
            }
        }
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))) {
            items(sharedCalls, key = { it.id }) { sc ->
                SharedCallCard(sharedCall = sc, onAccept = onAccept)
                Divider(color = Color.LightGray, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun SharedCallCard(sharedCall: SharedCallInfo, onAccept: (SharedCallInfo) -> Unit) {
    val bgColor = if (sharedCall.status == "OPEN") Color(0xFFFFF59D) else Color.LightGray
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${sharedCall.departure ?: "출발지"} → ${sharedCall.destination ?: "도착지"}", fontWeight = FontWeight.Bold)
            sharedCall.fare?.let { Text("요금: ${it}원") }
        }
        if (sharedCall.status == "OPEN") {
            Button(onClick = { onAccept(sharedCall) }) { Text("수락") }
        } else {
            Text(sharedCall.status)
        }
    }
}

// ---------------- Bottom Driver Bar -----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverBottomBar(drivers: List<DriverInfo>) {
    var selectedDriver by remember { mutableStateOf<DriverInfo?>(null) }
    val sheetState = rememberModalBottomSheetState()

    BottomAppBar(containerColor = Color(0xFFFFB000)) {
        // 가로 스크롤 가능하도록 Row+horizontalScroll
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            drivers.forEach { driver ->
                val statusColor = when (DriverStatus.fromString(driver.status)) {
                    DriverStatus.WAITING -> Color.Green
                    DriverStatus.ASSIGNED -> Color(0xFFFFA000)
                    DriverStatus.ON_TRIP -> Color.Red
                    DriverStatus.PREPARING -> Color(0xFFFFA000)
                    DriverStatus.ONLINE -> Color.Green
                    DriverStatus.OFFLINE -> Color.Gray
                    else -> Color.Gray
                }

                IconButton(onClick = { selectedDriver = driver }) {
                    Box {
                        Text(driver.name.take(1), color = Color.Black, fontSize = 14.sp)
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .background(statusColor, shape = CircleShape)
                        )
                    }
                }
            }
        }
    }

    if (selectedDriver != null) {
        ModalBottomSheet(onDismissRequest = { selectedDriver = null }, sheetState = sheetState) {
            val d = selectedDriver!!
            Column(Modifier.padding(16.dp)) {
                Text("${d.name} 기사님", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Text("상태: ${DriverStatus.fromString(d.status).getDisplayName()}")
                // TODO: 현재 콜 ID 표시 기능이 필요하면 DriverInfo에 필드 추가
                if (!d.phoneNumber.isNullOrBlank()) {
                    val phone = d.phoneNumber!!
                    val context = LocalContext.current
                    Button(onClick = {
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Phone, contentDescription = "전화")
                        Spacer(Modifier.width(4.dp))
                        Text("전화하기")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { selectedDriver = null }) { Text("닫기") }
            }
        }
    }
}

@Composable
fun SharedCallAcceptDialog(
    sharedCall: SharedCallInfo,
    availableDrivers: List<DriverInfo>,
    onDismiss: () -> Unit,
    onConfirm: (departure: String, destination: String, fare: Int, driver: DriverInfo?) -> Unit
) {
    var departure by remember { mutableStateOf(sharedCall.departure ?: "") }
    var destination by remember { mutableStateOf(sharedCall.destination ?: "") }
    var fareText by remember { mutableStateOf((sharedCall.fare ?: 0).toString()) }
    var selectedDriver by remember { mutableStateOf<DriverInfo?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("공유 콜 수락", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = departure,
                    onValueChange = { departure = it },
                    label = { Text("출발지") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("도착지") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = fareText,
                    onValueChange = { fareText = it.filter { c -> c.isDigit() } },
                    label = { Text("요금") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text("기사 선택", fontWeight = FontWeight.Medium)
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(availableDrivers) { driver ->
                        val isSelected = selectedDriver?.id == driver.id
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Log.d(TAG, "Driver selected: ${driver.name}/${driver.id}")
                                    selectedDriver = driver
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(driver.name, Modifier.weight(1f))
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val confirmEnabled = selectedDriver != null
            Button(enabled = confirmEnabled, onClick = {
                val fare = fareText.toIntOrNull() ?: 0
                if (departure.isNotBlank() && destination.isNotBlank() && fare > 0 && selectedDriver != null) {
                    onConfirm(departure, destination, fare, selectedDriver)
                }
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}


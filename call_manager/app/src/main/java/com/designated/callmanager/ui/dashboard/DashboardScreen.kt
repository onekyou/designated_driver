@file:OptIn(ExperimentalMaterial3Api::class)
package com.designated.callmanager.ui.dashboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.Surface
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
import com.designated.callmanager.service.CallManagerService
import com.designated.callmanager.data.SharedCallInfo
import com.designated.callmanager.ui.dashboard.DashboardViewModel.Companion.formatTimeAgo
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Phone
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.NavigationBarItemDefaults
import com.designated.callmanager.ui.shared.SharedCallSettingsScreen
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import android.view.inputmethod.EditorInfo
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Divider
import androidx.compose.foundation.verticalScroll
import com.designated.callmanager.util.VoiceInputHelper
import com.designated.callmanager.util.AddressSearchHelper
import com.designated.callmanager.data.AddressSearchResult
import android.speech.SpeechRecognizer

private const val TAG = "DashboardScreen"

// SpeechRecognizer 확장 함수
fun Context.createSpeechRecognizer(): SpeechRecognizer {
    return SpeechRecognizer.createSpeechRecognizer(this)
}

@Composable
fun CallStatus.getDisplayName(): String {
    return when (this) {
        CallStatus.WAITING -> "대기"
        CallStatus.SHARED_WAITING -> "공유대기"
        CallStatus.CLAIMED -> "수락됨"
        CallStatus.PENDING -> "기사승인대기"
        CallStatus.ASSIGNED -> "배차완료"
        CallStatus.ACCEPTED -> "수락"
        CallStatus.PICKUP_COMPLETE -> "픽업완료"
        CallStatus.IN_PROGRESS -> "운행중"
        CallStatus.AWAITING_SETTLEMENT -> "정산대기"
        CallStatus.COMPLETED -> "완료"
        CallStatus.SHARED_OUT -> "공유완료"
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
    val officeId by viewModel.officeId.collectAsStateWithLifecycle()
    val officeStatus by viewModel.officeStatus.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var callIdForDriverAssignment by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

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
    val showSharedCallTakenDialog by viewModel.showSharedCallTakenDialog.collectAsState()
    val canceledCallInfo by viewModel.canceledCallInfo.collectAsStateWithLifecycle()

    val showNewCallPopup by viewModel.showNewCallPopup.collectAsStateWithLifecycle()
    val newCallInfo by viewModel.newCallInfo.collectAsStateWithLifecycle()
    val showNewSharedCallPopup by viewModel.showNewSharedCallPopup.collectAsStateWithLifecycle()
    val newSharedCallInfo by viewModel.newSharedCallInfo.collectAsStateWithLifecycle()
    val isFromFcmNotification by viewModel.isFromFcmNotification.collectAsStateWithLifecycle()

    val showSharedCallCancelledDialog by viewModel.showSharedCallCancelledDialog.collectAsStateWithLifecycle()
    val sharedCancelledCallInfo by viewModel.cancelledCallInfo.collectAsStateWithLifecycle()

    // 전날 마감내역 팝업 관련
    val showPreviousDayClosingDialog by viewModel.showPreviousDayClosingDialog.collectAsStateWithLifecycle()
    val previousDayClosingData by viewModel.previousDayClosingData.collectAsStateWithLifecycle()

    var showSharedSettings by remember { mutableStateOf(false) }

    var selectedSharedCall by remember { mutableStateOf<SharedCallInfo?>(null) }
    var showSharedAcceptDialog by remember { mutableStateOf(false) }

    var backgroundTime by remember { mutableStateOf(0L) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    backgroundTime = System.currentTimeMillis()
                }
                Lifecycle.Event.ON_RESUME -> {
                    val currentTime = System.currentTimeMillis()
                    val timeDifference = currentTime - backgroundTime

                    if (timeDifference > 5000) {

                        if (showNewCallPopup) {
                            viewModel.dismissNewCallPopup()
                        }
                        // FCM 알림으로 인한 팝업은 자동으로 닫지 않음
                        if (showNewSharedCallPopup && !isFromFcmNotification) {
                            viewModel.dismissNewSharedCallPopup()
                        }
                        if (showTripStartedPopup) {
                            viewModel.dismissTripStartedPopup()
                        }
                        if (showTripCompletedPopup) {
                            viewModel.dismissTripCompletedPopup()
                        }
                        if (showDriverLoginPopup) {
                            viewModel.dismissDriverLoginPopup()
                        }
                        if (showApprovalPopup) {
                            viewModel.dismissApprovalPopup()
                        }
                        if (showDriverLogoutPopup) {
                            viewModel.dismissDriverLogoutPopup()
                        }
                        if (showSharedCallCancelledDialog) {
                            viewModel.dismissSharedCallCancelledDialog()
                        }
                    } else {
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (!CallManagerService.isServiceRunning) {
            viewModel.startForegroundService(context)
        }
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

    LaunchedEffect(showDriverLoginPopup, showApprovalPopup, showDriverLogoutPopup, showTripStartedPopup, showTripCompletedPopup, showCanceledCallPopup) {
        if(showDriverLoginPopup || showApprovalPopup || showDriverLogoutPopup || showTripStartedPopup || showTripCompletedPopup || showCanceledCallPopup) {
            val prefs = context.getSharedPreferences("call_manager_settings", Context.MODE_PRIVATE)
            val driverEventNotificationEnabled = prefs.getBoolean("driver_event_notification", true)
            if (driverEventNotificationEnabled) {
                playNotificationSound(context)
            }
        }
    }

    LaunchedEffect(showNewCallPopup) {
        if (showNewCallPopup) {
            val prefs = context.getSharedPreferences("call_manager_settings", Context.MODE_PRIVATE)
            val newCallNotificationEnabled = prefs.getBoolean("new_call_notification", true)
            if (newCallNotificationEnabled) {
                playNotificationSound(context)
            }
        }
    }

    LaunchedEffect(showNewSharedCallPopup) {
        if (showNewSharedCallPopup) {
            val prefs = context.getSharedPreferences("call_manager_settings", Context.MODE_PRIVATE)
            val newCallNotificationEnabled = prefs.getBoolean("new_call_notification", true)
            if (newCallNotificationEnabled) {
                playNotificationSound(context)
            }
        }
    }

    // 사무실 상태 변경 감지 및 전날 마감내역 로드
    LaunchedEffect(officeStatus) {
        if (officeStatus == "OPEN") {
            // 마감에서 운영으로 전환된 경우 전날 마감내역 로드
            viewModel.loadPreviousDayClosingData()
        }
    }

    if (callInfoForDialog != null) {
        CallInfoDialog(
            callInfo = callInfoForDialog!!,
            onDismiss = { viewModel.dismissCallDialog() },
            onAssignRequest = {
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
            content = "${tripCompletedInfo!!.first}${if (tripCompletedInfo!!.first.endsWith("님")) "" else " 기사님"}이 ${tripCompletedInfo!!.second} 고객님의 운행을 완료했습니다.",
            onDismiss = { viewModel.dismissTripCompletedPopup() }
        )
    }

    if(showCanceledCallPopup && canceledCallInfo != null){
        InfoPopup(
            title = "호출 취소",
            content = "${canceledCallInfo!!.first}${if (canceledCallInfo!!.first.endsWith("님")) "" else " 기사님"}의 ${canceledCallInfo!!.second} 고객 호출이 취소되었습니다.",
            onDismiss = { viewModel.dismissCanceledCallPopup() }
        )
    }

    if (showNewCallPopup && newCallInfo != null) {

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
            onDelete = {
                viewModel.deleteCall(newCallInfo!!.id)
            },
            onShare = { departure, destination, fare ->
                viewModel.shareCall(newCallInfo!!, departure, destination, fare)
            }
        )
    }

    if (showSharedCallCancelledDialog && sharedCancelledCallInfo != null) {
        SharedCallCancelledDialog(
            callInfo = sharedCancelledCallInfo!!,
            onDismiss = { viewModel.dismissSharedCallCancelledDialog() },
            onReshare = { departure, destination, fare ->
                viewModel.shareCall(sharedCancelledCallInfo!!, departure, destination, fare)
            }
        )
    }

    // 공유콜 마감 다이얼로그
    if (showSharedCallTakenDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSharedCallTakenDialog() },
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            title = { Text("공유콜 마감") },
            text = { Text("다른 사무실에서 이미 수락하여 공유콜이 마감되었습니다.") },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSharedCallTakenDialog() }) {
                    Text("확인")
                }
            }
        )
    }

    // 전날 마감내역 팝업
    if (showPreviousDayClosingDialog && previousDayClosingData != null) {
        PreviousDayClosingDialog(
            closingData = previousDayClosingData!!,
            onDismiss = { viewModel.dismissPreviousDayClosingDialog() }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = officeName ?: "사무실 정보 로딩 중...")
                        Text(
                            text = when (officeStatus) {
                                "CLOSED" -> "🔴 마감"
                                "AUTO_SHARING" -> "🟡 자동공유중"
                                else -> "🟢 운영중"
                            },
                            fontSize = 12.sp,
                            color = when (officeStatus) {
                                "CLOSED" -> Color.Red
                                "AUTO_SHARING" -> Color.Yellow
                                else -> Color.Green
                            }
                        )
                    }
                },
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                CallListContainer(
                    modifier = Modifier.fillMaxWidth().weight(4.5f),
                    calls = calls.filter { call ->
                        val status = CallStatus.fromFirestoreValue(call.status)
                        status != CallStatus.COMPLETED && status != CallStatus.CANCELED && status != CallStatus.SHARED_OUT
                    },
                    title = "내부 호출 목록",
                    onCallClick = { callInfo -> viewModel.showCallDialog(callInfo.id) },
                    onAddCallClick = { viewModel.createPlaceholderCall() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                SharedCallListContainer(
                    modifier = Modifier.fillMaxWidth().weight(4.5f),
                    sharedCalls = sharedCalls,
                    onAccept = { call ->
                        selectedSharedCall = call
                        showSharedAcceptDialog = true
                    },
                    onSettings = { showSharedSettings = true },
                    onReopen = { call ->
                        viewModel.reopenSharedCall(call.id)
                    },
                    onDelete = { call ->
                        viewModel.deleteSharedCall(call.id)
                    },
                    currentOfficeId = officeId
                )

                Spacer(modifier = Modifier.height(12.dp))

                DriverStatusCard(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    drivers = drivers,
                    calls = calls
                )
            }

            if (showSharedSettings) {
                SharedCallSettingsScreen(onNavigateBack = { showSharedSettings = false })
            }
        }
    }

    if (showSharedAcceptDialog && selectedSharedCall != null) {
        val call = selectedSharedCall!!
        val waitingDrivers = drivers.filter { driver ->
            val statusEnum = DriverStatus.fromString(driver.status?.trim() ?: "")
            statusEnum == DriverStatus.WAITING || statusEnum == DriverStatus.ONLINE
        }
        SharedCallAcceptDialog(
            sharedCall = call,
            availableDrivers = waitingDrivers,
            onDismiss = {
                showSharedAcceptDialog = false
                selectedSharedCall = null
            },
            onConfirm = { dep, dest, fare, driver ->
                viewModel.claimSharedCallWithDetails(
                    sharedCallId = call.id,
                    departure = dep,
                    destination = dest,
                    fare = fare,
                    driverId = driver?.id
                )
                showSharedAcceptDialog = false
                selectedSharedCall = null
            }
        )
    }

    if (showNewSharedCallPopup && newSharedCallInfo != null) {
        Log.d("DashboardScreen", "🔍 [FCM_DEBUG] 팝업 조건 만족 - SharedCallAcceptDialog 표시 시작")
        val call = newSharedCallInfo!!
        val waitingDrivers = drivers.filter { driver ->
            val statusEnum = DriverStatus.fromString(driver.status?.trim() ?: "")
            statusEnum == DriverStatus.WAITING || statusEnum == DriverStatus.ONLINE
        }
        Log.d("DashboardScreen", "🔍 [FCM_DEBUG] 전체 드라이버 수: ${drivers.size}, 대기 중 드라이버 수: ${waitingDrivers.size}")
        SharedCallAcceptDialog(
            sharedCall = call,
            availableDrivers = waitingDrivers,
            onDismiss = {
                viewModel.dismissNewSharedCallPopup()
            },
            onConfirm = { dep, dest, fare, driver ->
                viewModel.claimSharedCallWithDetails(
                    sharedCallId = call.id,
                    departure = dep,
                    destination = dest,
                    fare = fare,
                    driverId = driver?.id
                )
                viewModel.dismissNewSharedCallPopup()
            }
        )
    }
}

fun playNotificationSound(context: Context) {
    try {
        val systemNotificationUri = Settings.System.DEFAULT_NOTIFICATION_URI
        val ringtone = RingtoneManager.getRingtone(context.applicationContext, systemNotificationUri)
        if (ringtone != null) {
            ringtone.play()
            return
        }

        throw Exception("System notification sound failed")

    } catch (e: Exception) {
        try {
            val fallbackNotification: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val fallbackR = RingtoneManager.getRingtone(context.applicationContext, fallbackNotification)
            fallbackR?.play()
        } catch (e2: Exception) {
        }
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
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onAddCallClick) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "새 호출 추가",
                        tint = Color.White
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(calls, key = { it.id }) { call ->
                    CallCard(call = call, onCallClick = { onCallClick(call) })
                }
            }
        }
    }
}

@Composable
fun CallCard(call: CallInfo, onCallClick: (CallInfo) -> Unit) {
    val callStatus = remember(call.status) { CallStatus.fromFirestoreValue(call.status) }
    val statusDisplayName = callStatus.getDisplayName()

    // 공유콜이 배차 완료된 경우 또는 일반콜이 운행중인 경우 클릭 비활성화
    val isCallInProgress = callStatus == CallStatus.IN_PROGRESS ||
                          callStatus == CallStatus.AWAITING_SETTLEMENT ||
                          callStatus == CallStatus.COMPLETED

    val isSharedCallCompleted = call.callType == "SHARED" && (
        callStatus == CallStatus.ASSIGNED ||
        callStatus == CallStatus.ACCEPTED ||
        callStatus == CallStatus.PICKUP_COMPLETE ||
        callStatus == CallStatus.IN_PROGRESS ||
        callStatus == CallStatus.AWAITING_SETTLEMENT ||
        callStatus == CallStatus.COMPLETED ||
        callStatus == CallStatus.SHARED_OUT
    )

    val shouldDisableClick = isCallInProgress || isSharedCallCompleted

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
        shape = RoundedCornerShape(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (shouldDisableClick) {
                        Modifier // 클릭 비활성화
                    } else {
                        Modifier.clickable(onClick = { onCallClick(call) })
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
            Column(modifier = Modifier.weight(1f)) {
                val displayText = (if (!call.customerName.isNullOrBlank()) {
                    call.customerName
                } else {
                    call.customerAddress
                }) ?: "정보 없음"

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 공유콜 아이콘
                    if (call.callType == "SHARED") {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "공유콜",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Yellow
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // 앱 콜 아이콘 (고객앱/랜딩페이지에서 온 콜)
                    if (call.createdFrom == "customer_app" || call.createdFrom == "landing") {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = "앱 콜",
                            modifier = Modifier.size(16.dp),
                            tint = Color(0xFF4CAF50) // 초록색
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // 고객 등급 표시
                    call.customerGrade?.let { grade ->
                        Spacer(modifier = Modifier.width(6.dp))
                        CustomerGradeBadge(grade = grade)
                    }
                }
                Text(
                    text = formatTimeAgo(call.timestamp.toDate().time),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                call.assignedDriverName?.let {
                    Text(
                        text = "배정: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFFAB00)
                    )
                }

                // 어트리뷰션 점수 표시
                call.attributionScore?.let { score ->
                    Text(
                        text = "매칭점수: ${score}점",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (score >= 70) Color(0xFF4CAF50) else Color(0xFFFF9800)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = statusDisplayName,
                style = MaterialTheme.typography.labelMedium,
                color = when (callStatus) {
                    CallStatus.PENDING -> Color(0xFFFF5722)
                    CallStatus.ASSIGNED -> Color(0xFFFFAB00)
                    CallStatus.COMPLETED -> Color.Gray
                    CallStatus.SHARED_OUT -> Color(0xFF9C27B0)
                    CallStatus.CANCELED -> Color.Gray
                    else -> Color(0xFF4CAF50)
                }
            )
            }

            // 목적지를 Card 중앙에 오버레이로 배치
            call.destination_set?.let { destination ->
                if (destination.isNotBlank()) {
                    Text(
                        text = "목적지: $destination",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFF03DAC6),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun DriverStatusCard(
    modifier: Modifier = Modifier,
    drivers: List<DriverInfo>,
    calls: List<CallInfo>
) {
    val context = LocalContext.current

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "기사",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(end = 12.dp)
            )

            val sortedDrivers = remember(drivers) {
                drivers.sortedWith(compareBy<DriverInfo> { driver ->
                    val status = DriverStatus.fromString(driver.status)
                    when (status) {
                        DriverStatus.WAITING -> 0
                        DriverStatus.ONLINE -> 1
                        DriverStatus.PREPARING -> 2
                        DriverStatus.ON_TRIP -> 3
                        DriverStatus.OFFLINE -> 4
                        else -> 5
                    }
                }.thenBy { it.name })
            }

            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(sortedDrivers, key = { it.id }) { driver ->
                    DriverStatusCompactItem(
                        driver = driver,
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${driver.phoneNumber}"))
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "전화를 걸 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DriverStatusCompactItem(
    driver: DriverInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(
                    color = when (DriverStatus.fromString(driver.status)) {
                        DriverStatus.WAITING -> Color(0xFF4CAF50)
                        DriverStatus.ON_TRIP -> Color(0xFFF44336)
                        DriverStatus.PREPARING -> Color(0xFFFFA000)
                        DriverStatus.ONLINE -> Color(0xFF2196F3)
                        DriverStatus.OFFLINE -> Color(0xFF9E9E9E)
                        else -> Color.Gray
                    },
                    shape = CircleShape
                )
        )

        Text(
            text = driver.name,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun DriverStatusItem(driver: DriverInfo, currentCall: CallInfo?) {
    val driverStatus = remember(driver.status) { DriverStatus.fromString(driver.status) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = when (driverStatus) {
                                DriverStatus.WAITING -> Color(0xFF4CAF50)
                                DriverStatus.ONLINE -> Color(0xFF4CAF50)
                                DriverStatus.ON_TRIP -> Color(0xFFFF5722)
                                DriverStatus.PREPARING -> Color(0xFFFFAB00)
                                DriverStatus.OFFLINE -> Color.Gray
                                else -> Color.Gray
                            },
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = driver.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = driverStatus.getDisplayName(),
                    style = MaterialTheme.typography.bodySmall,
                    color = when (driverStatus) {
                        DriverStatus.WAITING -> Color(0xFF4CAF50)
                        DriverStatus.ONLINE -> Color(0xFF4CAF50)
                        DriverStatus.ON_TRIP -> Color(0xFFFF5722)
                        DriverStatus.PREPARING -> Color(0xFFFFAB00)
                        DriverStatus.OFFLINE -> Color.Gray
                        else -> Color.Gray
                    }
                )
            }

            if (driverStatus == DriverStatus.ON_TRIP && currentCall != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val departure = currentCall.departure_set ?: "출발지 미설정"
                val destination = currentCall.destination_set ?: "도착지 미설정"
                Text(
                    text = "$departure → $destination",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                currentCall.customerName?.let { customerName ->
                    Text(
                        text = "고객: $customerName",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray
                    )
                }
            }
        }
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
                Text("$driverName${if (driverName.endsWith("님")) "" else " 기사님"}", fontWeight = FontWeight.Bold)
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

@Composable
fun NewCallAssignmentDialog(
    callInfo: CallInfo,
    availableDrivers: List<DriverInfo>,
    onDismiss: () -> Unit,
    onDriverSelect: (DriverInfo) -> Unit,
    onDelete: () -> Unit,
    onShare: (departure: String, destination: String, fare: Int) -> Unit
) {
    val context = LocalContext.current

    var showShareDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {
            try {
                val stickyRingtone = RingtoneManager.getRingtone(context.applicationContext, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                if (stickyRingtone.isPlaying) {
                    stickyRingtone.stop()
                }
                val defaultRingtone = RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                if (defaultRingtone.isPlaying) {
                    defaultRingtone.stop()
                }
            } catch (e: Exception) {
            }
            onDismiss()
        },
        title = { Text("새로운 호출 접수", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        val stickyR = RingtoneManager.getRingtone(context.applicationContext, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                        if (stickyR.isPlaying) stickyR.stop()
                        val defaultR = RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                        if (defaultR.isPlaying) defaultR.stop()
                    } catch (e: Exception) {
                    }
                    onDismiss()
                }) { Text("나중에") }

                TextButton(onClick = { showShareDialog = true }) { Text("공유") }

                TextButton(
                    onClick = {
                        try {
                            val stickyR = RingtoneManager.getRingtone(context.applicationContext, android.provider.Settings.System.DEFAULT_NOTIFICATION_URI)
                            if (stickyR.isPlaying) stickyR.stop()
                            val defaultR = RingtoneManager.getRingtone(context.applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                            if (defaultR.isPlaying) defaultR.stop()
                        } catch (e: Exception) {
                        }
                        showDeleteConfirmDialog = true
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("삭제") }
            }
        }
    )

    if (showShareDialog) {
        // 초기값에 공백을 넣어 한글 입력을 유도
        var departure by remember { mutableStateOf("") }
        var destination by remember { mutableStateOf("") }
        var fareText by remember { mutableStateOf("") }

        // 음성인식 관련 상태
        var isRecordingDeparture by remember { mutableStateOf(false) }
        var isRecordingDestination by remember { mutableStateOf(false) }
        var isRecordingFare by remember { mutableStateOf(false) }

        // Kakao 주소 검색 결과
        var departureSearchResults by remember { mutableStateOf<List<AddressSearchResult>>(emptyList()) }
        var destinationSearchResults by remember { mutableStateOf<List<AddressSearchResult>>(emptyList()) }
        var showDepartureResults by remember { mutableStateOf(false) }
        var showDestinationResults by remember { mutableStateOf(false) }

        val departureFocusRequester = remember { FocusRequester() }
        val destinationFocusRequester = remember { FocusRequester() }
        val fareFocusRequester = remember { FocusRequester() }

        // Speech recognizer
        val speechRecognizer = remember { context.createSpeechRecognizer() }
        val voiceHelper = remember { VoiceInputHelper(context) }
        val addressSearchHelper = remember { AddressSearchHelper() }

        LaunchedEffect(Unit) {
            departureFocusRequester.requestFocus()
        }

        DisposableEffect(Unit) {
            onDispose {
                speechRecognizer.destroy()
            }
        }

        AlertDialog(
            onDismissRequest = { showShareDialog = false },
            title = { Text("공유 정보 입력", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    // 출발지 입력
                    OutlinedTextField(
                        value = departure,
                        onValueChange = {
                            departure = it
                            // 주소 검색
                            if (it.length >= 2) {
                                addressSearchHelper.searchAddress(it) { results ->
                                    departureSearchResults = results
                                    showDepartureResults = results.isNotEmpty()
                                }
                            } else {
                                showDepartureResults = false
                            }
                        },
                        label = { Text("출발지") },
                        placeholder = { Text("예: 서울역") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(departureFocusRequester),
                        trailingIcon = {
                            Row {
                                // 음성 입력 버튼
                                IconButton(onClick = {
                                    if (!isRecordingDeparture) {
                                        voiceHelper.startListening { result ->
                                            departure = result
                                            isRecordingDeparture = false
                                            // 음성인식 결과로 주소 검색
                                            addressSearchHelper.searchAddress(result) { results ->
                                                departureSearchResults = results
                                                showDepartureResults = results.isNotEmpty()
                                            }
                                        }
                                        isRecordingDeparture = true
                                    } else {
                                        voiceHelper.stopListening()
                                        isRecordingDeparture = false
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "음성 입력",
                                        tint = if (isRecordingDeparture) Color.Red else MaterialTheme.colorScheme.primary
                                    )
                                }
                                // 삭제 버튼
                                if (departure.isNotEmpty()) {
                                    IconButton(onClick = {
                                        departure = ""
                                        showDepartureResults = false
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "지우기")
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.None
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { destinationFocusRequester.requestFocus() }
                        )
                    )

                    // 출발지 검색 결과
                    if (showDepartureResults) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                        ) {
                            LazyColumn {
                                items(departureSearchResults) { result ->
                                    Text(
                                        text = result.address,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                departure = result.address
                                                showDepartureResults = false
                                                destinationFocusRequester.requestFocus()
                                            }
                                            .padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Divider()
                                }
                            }
                        }
                    }

                    // 도착지 입력
                    OutlinedTextField(
                        value = destination,
                        onValueChange = {
                            destination = it
                            // 주소 검색
                            if (it.length >= 2) {
                                addressSearchHelper.searchAddress(it) { results ->
                                    destinationSearchResults = results
                                    showDestinationResults = results.isNotEmpty()
                                }
                            } else {
                                showDestinationResults = false
                            }
                        },
                        label = { Text("도착지") },
                        placeholder = { Text("예: 강남역") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(destinationFocusRequester),
                        trailingIcon = {
                            Row {
                                // 음성 입력 버튼
                                IconButton(onClick = {
                                    if (!isRecordingDestination) {
                                        voiceHelper.startListening { result ->
                                            destination = result
                                            isRecordingDestination = false
                                            // 음성인식 결과로 주소 검색
                                            addressSearchHelper.searchAddress(result) { results ->
                                                destinationSearchResults = results
                                                showDestinationResults = results.isNotEmpty()
                                            }
                                        }
                                        isRecordingDestination = true
                                    } else {
                                        voiceHelper.stopListening()
                                        isRecordingDestination = false
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "음성 입력",
                                        tint = if (isRecordingDestination) Color.Red else MaterialTheme.colorScheme.primary
                                    )
                                }
                                // 삭제 버튼
                                if (destination.isNotEmpty()) {
                                    IconButton(onClick = {
                                        destination = ""
                                        showDestinationResults = false
                                    }) {
                                        Icon(Icons.Default.Clear, contentDescription = "지우기")
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.None
                        ),
                        keyboardActions = KeyboardActions(
                            onNext = { fareFocusRequester.requestFocus() }
                        )
                    )

                    // 도착지 검색 결과
                    if (showDestinationResults) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp)
                        ) {
                            LazyColumn {
                                items(destinationSearchResults) { result ->
                                    Text(
                                        text = result.address,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                destination = result.address
                                                showDestinationResults = false
                                                fareFocusRequester.requestFocus()
                                            }
                                            .padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Divider()
                                }
                            }
                        }
                    }

                    // 요금 입력
                    OutlinedTextField(
                        value = fareText,
                        onValueChange = { fareText = it.filter { c -> c.isDigit() } },
                        label = { Text("요금") },
                        placeholder = { Text("예: 30000") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fareFocusRequester),
                        trailingIcon = {
                            Row {
                                // 음성 입력 버튼
                                IconButton(onClick = {
                                    if (!isRecordingFare) {
                                        voiceHelper.startListening { result ->
                                            // 한국어 숫자를 아라비아 숫자로 변환
                                            fareText = voiceHelper.convertKoreanNumberToDigit(result)
                                            isRecordingFare = false
                                        }
                                        isRecordingFare = true
                                    } else {
                                        voiceHelper.stopListening()
                                        isRecordingFare = false
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Mic,
                                        contentDescription = "음성 입력",
                                        tint = if (isRecordingFare) Color.Red else MaterialTheme.colorScheme.primary
                                    )
                                }
                                // 삭제 버튼
                                if (fareText.isNotEmpty()) {
                                    IconButton(onClick = { fareText = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "지우기")
                                    }
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val fare = fareText.toIntOrNull() ?: 0
                                if (departure.isNotBlank() && destination.isNotBlank() && fare > 0) {
                                    onShare(departure, destination, fare)
                                    showShareDialog = false
                                    onDismiss()
                                }
                            }
                        )
                    )
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

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.Warning, contentDescription = "경고") },
            title = { Text("호출 삭제 확인") },
            text = {
                Column {
                    Text("이 호출을 정말 삭제하시겠습니까?")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "삭제된 호출은 복구할 수 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun SharedCallListContainer(
    modifier: Modifier = Modifier,
    sharedCalls: List<SharedCallInfo>,
    onAccept: (SharedCallInfo) -> Unit,
    onSettings: () -> Unit,
    onReopen: ((SharedCallInfo) -> Unit)? = null,
    onDelete: ((SharedCallInfo) -> Unit)? = null,
    currentOfficeId: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "공유 콜",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "공유콜 설정",
                        tint = Color.White
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(sharedCalls, key = { it.id }) { sc ->
                    SharedCallCard(
                        sharedCall = sc,
                        onAccept = onAccept,
                        onReopen = onReopen,
                        onDelete = onDelete,
                        isSourceOffice = currentOfficeId == sc.sourceOfficeId
                    )
                }
            }
        }
    }
}

@Composable
fun SharedCallCard(
    sharedCall: SharedCallInfo,
    onAccept: (SharedCallInfo) -> Unit,
    onReopen: ((SharedCallInfo) -> Unit)? = null,
    onDelete: ((SharedCallInfo) -> Unit)? = null,
    isSourceOffice: Boolean = false
) {
    // 사무실명 가져오기
    var officeName by remember { mutableStateOf("") }
    LaunchedEffect(sharedCall.sourceOfficeId) {
        if ((sharedCall.callType == "마감콜" || sharedCall.callType == "AFTER_HOURS_QUICK" || sharedCall.callType == "MISSED_AFTER_HOURS") && sharedCall.sourceOfficeId.isNotEmpty()) {
            val firestore = FirebaseFirestore.getInstance()
            try {
                val doc = firestore.collection("regions").document(sharedCall.sourceRegionId)
                    .collection("offices").document(sharedCall.sourceOfficeId).get().await()
                officeName = doc.getString("name") ?: sharedCall.sourceOfficeId
            } catch (e: Exception) {
                officeName = sharedCall.sourceOfficeId
            }
        }
    }
    val bgColor = when (sharedCall.status) {
        "OPEN" -> Color(0xFF3A3A3A)
        "CLAIMED" -> Color(0xFF2A2A2A)
        "COMPLETED" -> Color(0xFF1A1A1A)
        "CANCELLED_BY_TARGET" -> Color(0xFF5D4037)
        else -> Color(0xFF2A2A2A)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (sharedCall.callType == "마감콜" || sharedCall.callType == "AFTER_HOURS_QUICK" || sharedCall.callType == "MISSED_AFTER_HOURS") {
                    // 마감콜은 "마감콜 | 사무실명"만 표시
                    Text(
                        text = "마감콜",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (officeName.isNotEmpty()) {
                        Text(
                            text = officeName,
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    // 일반 콜은 출발지-도착지와 요금만 표시
                    Text(
                        text = "${sharedCall.departure ?: "출발지"} → ${sharedCall.destination ?: "도착지"}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    sharedCall.fare?.let {
                        Text(
                            text = "요금: ${it}원",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            when (sharedCall.status) {
                "OPEN" -> {
                    Button(
                        onClick = { onAccept(sharedCall) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFAB00))
                    ) {
                        Text("수락", color = Color.Black)
                    }
                }
                "CLAIMED" -> {
                    Text(
                        text = "수락됨",
                        color = Color(0xFFFFAB00),
                        fontWeight = FontWeight.Bold
                    )
                }
                "COMPLETED" -> {
                    Text(
                        text = "완료",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
                "CANCELLED_BY_TARGET" -> {
                    if (isSourceOffice) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "취소됨",
                                color = Color(0xFFFF7043),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            onReopen?.let { reopen ->
                                IconButton(
                                    onClick = { reopen(sharedCall) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "재공유",
                                        tint = Color(0xFF4CAF50)
                                    )
                                }
                            }
                            onDelete?.let { delete ->
                                IconButton(
                                    onClick = { delete(sharedCall) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "삭제",
                                        tint = Color(0xFFE57373)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "취소됨",
                            color = Color(0xFFFF7043),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    Text(
                        text = sharedCall.status,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverBottomBar(drivers: List<DriverInfo>) {
    var selectedDriver by remember { mutableStateOf<DriverInfo?>(null) }
    val sheetState = rememberModalBottomSheetState()

    BottomAppBar(containerColor = Color(0xFFFFB000)) {
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
    Log.d("SharedCallAcceptDialog", "🔍 [FCM_DEBUG] SharedCallAcceptDialog 컴포넌트 진입 - sharedCall: ${sharedCall.id}, drivers: ${availableDrivers.size}개")

    // 마감콜 여부 판단: callType이 있거나, 출발지/도착지/요금이 모두 없는 경우
    val isClosingCall = sharedCall.callType == "MISSED_AFTER_HOURS" ||
                        sharedCall.callType == "AFTER_HOURS_QUICK" ||
                        (sharedCall.departure.isNullOrBlank() &&
                         sharedCall.destination.isNullOrBlank() &&
                         (sharedCall.fare == null || sharedCall.fare == 0))

    var departure by remember { mutableStateOf(sharedCall.departure ?: "") }
    var destination by remember { mutableStateOf(sharedCall.destination ?: "") }
    var fareText by remember { mutableStateOf((sharedCall.fare ?: 0).toString()) }
    var selectedDriver by remember { mutableStateOf<DriverInfo?>(null) }

    val departureFocusRequester = remember { FocusRequester() }
    val destinationFocusRequester = remember { FocusRequester() }
    val fareFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        departureFocusRequester.requestFocus()
    }

    Log.d("SharedCallAcceptDialog", "🔍 [FCM_DEBUG] AlertDialog 렌더링 시작")
    AlertDialog(
        onDismissRequest = {
            Log.d("SharedCallAcceptDialog", "🔍 [FCM_DEBUG] AlertDialog onDismissRequest 호출")
            onDismiss()
        },
        title = {
            Log.d("SharedCallAcceptDialog", "🔍 [FCM_DEBUG] AlertDialog Title 렌더링")
            Text("공유 콜 수락", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 마감콜인 경우 표시
                if (isClosingCall) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "마감 후 부재중 콜",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = departure,
                    onValueChange = { departure = it },
                    label = { Text("출발지") },
                    placeholder = { Text("예: 서울역") },
                    singleLine = true,
                    enabled = !isClosingCall,
                    modifier = Modifier.focusRequester(departureFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { destinationFocusRequester.requestFocus() }
                    )
                )
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("도착지") },
                    placeholder = { Text("예: 강남역") },
                    singleLine = true,
                    enabled = !isClosingCall,
                    modifier = Modifier.focusRequester(destinationFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { fareFocusRequester.requestFocus() }
                    )
                )
                OutlinedTextField(
                    value = fareText,
                    onValueChange = { fareText = it.filter { c -> c.isDigit() } },
                    label = { Text("요금") },
                    singleLine = true,
                    enabled = !isClosingCall,
                    modifier = Modifier.focusRequester(fareFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                        }
                    )
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
                // 마감콜의 경우 기사 선택만으로 확인 가능, 일반 콜은 모든 필드 필요
                if (isClosingCall && selectedDriver != null) {
                    onConfirm(departure, destination, fare, selectedDriver)
                } else if (!isClosingCall && departure.isNotBlank() && destination.isNotBlank() && fare > 0 && selectedDriver != null) {
                    onConfirm(departure, destination, fare, selectedDriver)
                }
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
fun SharedCallCancelledDialog(
    callInfo: CallInfo,
    onDismiss: () -> Unit,
    onReshare: (departure: String, destination: String, fare: Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("🚫 공유콜이 취소되었습니다")
            }
        },
        text = {
            Column {
                Text(
                    text = "수락한 사무실의 기사가 공유콜을 취소했습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row {
                            Text("📞 전화번호: ", fontWeight = FontWeight.Medium)
                            Text(callInfo.phoneNumber)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text("📍 출발지: ", fontWeight = FontWeight.Medium)
                            Text(callInfo.departure ?: "미설정")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text("🏁 도착지: ", fontWeight = FontWeight.Medium)
                            Text(callInfo.destination ?: "미설정")
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Text("💰 요금: ", fontWeight = FontWeight.Medium)
                            Text("${callInfo.fare ?: 0}원")
                        }
                        val cancelReason = callInfo.cancelReason
                        if (!cancelReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row {
                                Text("❌ 취소사유: ", fontWeight = FontWeight.Medium)
                                Text(
                                    cancelReason,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "콜이 대기상태로 복구되었습니다. 다시 기사를 배정하거나 재공유할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onReshare(
                        callInfo.departure ?: "",
                        callInfo.destination ?: "",
                        (callInfo.fare ?: 0).toInt()
                    )
                    onDismiss()
                }
            ) {
                Text("재공유")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
fun PreviousDayClosingDialog(
    closingData: PreviousDayClosingData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("📋 전날 마감콜 내역")
            }
        },
        text = {
            LazyColumn {
                item {
                    // 요약 정보
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "📊 마감콜 요약",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("전체 마감콜: ${closingData.totalCount}건")
                                Text("수행: ${closingData.completedCount}건")
                                Text("미수행: ${closingData.uncompletedCount}건")
                            }
                        }
                    }

                    if (closingData.completedCalls.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "✅ 수행된 마감콜",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                items(closingData.completedCalls) { call ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "👤 ${call.customerName}",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "${call.fare}원",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                "📍 ${call.departure} → ${call.destination}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("확인")
            }
        }
    )
}

@Composable
fun CustomerGradeBadge(grade: String) {
    val (emoji, text, color) = when (grade) {
        "bronze" -> Triple("🥉", "브론즈", Color(0xFFCD7F32))
        "silver" -> Triple("🥈", "실버", Color(0xFFC0C0C0))
        "gold" -> Triple("🥇", "골드", Color(0xFFFFD700))
        "vip" -> Triple("⭐", "VIP", Color(0xFFFF6B35))
        else -> Triple("", "기본", MaterialTheme.colorScheme.outline)
    }

    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.2f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (emoji.isNotEmpty()) emoji else text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp
        )
    }
}


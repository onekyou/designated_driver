package com.designated.driverapp.ui.home

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.CallStatus
import com.designated.driverapp.model.DriverStatus
import com.designated.driverapp.ui.screens.home.InProgressScreen
import com.designated.driverapp.ui.screens.home.NewCallPopup
import com.designated.driverapp.ui.screens.home.TripPreparationScreen
import com.designated.driverapp.ui.screens.home.WaitingScreen
import com.designated.driverapp.viewmodel.DriverViewModel
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import com.designated.driverapp.navigation.AppDestinations
import com.designated.driverapp.data.Constants
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import com.designated.driverapp.ui.home.logoutUserAndExitApp
import com.designated.driverapp.ui.home.hasUnsavedTripHistory
import com.designated.driverapp.ui.home.getUnsavedTripCount
import com.designated.driverapp.ui.home.saveAndClearSettlement
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.tasks.await

private const val TAG = "HomeScreen"

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: DriverViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var officeName by remember { mutableStateOf("사무실") }

    // 로그아웃 확인 다이얼로그 상태
    var showLogoutDialog by remember { mutableStateOf(false) }
    var unsavedTripCount by remember { mutableStateOf(0) }

    // 업무 마감 다이얼로그 상태
    var showSettlementFinalizedDialog by remember { mutableStateOf(false) }
    var settlementSessionDate by remember { mutableStateOf("") }

    // 업무 마감 FCM 브로드캐스트 수신
    DisposableEffect(Unit) {
        val finalizedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "업무 마감 브로드캐스트 수신")
                val sessionDate = intent?.getStringExtra("sessionDate") ?: ""
                settlementSessionDate = sessionDate

                // 미저장 운행 내역 있으면 다이얼로그 표시
                if (hasUnsavedTripHistory(context ?: return)) {
                    unsavedTripCount = getUnsavedTripCount(context)
                    showSettlementFinalizedDialog = true
                }
            }
        }

        // 정산 확인/거절 FCM 브로드캐스트 수신 (HistorySettlementScreen의 StateFlow로도 감지되지만, 다른 화면에 있을 때를 위해)
        val confirmedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "정산 확인 브로드캐스트 수신")
                // Firestore listener가 _dailySettlementStatus를 업데이트하므로
                // HistorySettlementScreen에서 자동으로 CONFIRMED 다이얼로그 표시
            }
        }
        val rejectedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d(TAG, "정산 거절 브로드캐스트 수신")
                // Firestore listener가 _dailySettlementStatus를 업데이트하므로
                // HistorySettlementScreen에서 자동으로 REJECTED 다이얼로그 표시
            }
        }

        val lbm = LocalBroadcastManager.getInstance(context)
        lbm.registerReceiver(finalizedReceiver, IntentFilter(Constants.ACTION_SETTLEMENT_FINALIZED))
        lbm.registerReceiver(confirmedReceiver, IntentFilter(Constants.ACTION_SETTLEMENT_CONFIRMED))
        lbm.registerReceiver(rejectedReceiver, IntentFilter(Constants.ACTION_SETTLEMENT_REJECTED))

        onDispose {
            lbm.unregisterReceiver(finalizedReceiver)
            lbm.unregisterReceiver(confirmedReceiver)
            lbm.unregisterReceiver(rejectedReceiver)
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val provinceId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, null)
        val cityId = prefs.getString(Constants.PREF_KEY_CITY_ID, null)
        val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, null)

        if (provinceId != null && cityId != null && officeId != null) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val document = firestore.collection("provinces").document(provinceId)
                    .collection("cities").document(cityId)
                    .collection("offices").document(officeId).get().await()
                officeName = if (document.exists()) {
                    document.getString("name") ?: officeId
                } else {
                    officeId
                }
            } catch (e: Exception) {
                officeName = officeId
            }
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onErrorMessageHandled()
        }
    }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.Black,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            // 미저장 운행 내역 확인
                            if (hasUnsavedTripHistory(context)) {
                                unsavedTripCount = getUnsavedTripCount(context)
                            }
                            showLogoutDialog = true
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ExitToApp,
                            contentDescription = "로그아웃",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = officeName,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            navController.navigate(AppDestinations.HISTORY_SETTLEMENT_ROUTE) {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "운행내역",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color(0xFF121212)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            // 뒤로가기 버튼 처리
            when {
                // 정산 대기 중: 뒤로가기 차단 (정산 완료 필수)
                uiState.callForSettlement != null -> {
                    BackHandler(enabled = true) {
                        // 뒤로가기 차단 - 아무 동작 하지 않음
                    }
                }
                // 운행 중: 뒤로가기 차단 (운행 완료 필수)
                uiState.activeCall?.statusEnum == CallStatus.IN_PROGRESS -> {
                    BackHandler(enabled = true) {
                        // 뒤로가기 차단 - 아무 동작 하지 않음
                    }
                }
                // 운행 준비 중: 뒤로가기 차단 (취소 버튼으로만 취소 가능)
                uiState.activeCall?.statusEnum == CallStatus.ACCEPTED -> {
                    BackHandler(enabled = true) {
                        // 뒤로가기 차단 - 아무 동작 하지 않음
                        // 사용자는 화면의 "취소" 버튼을 통해 명시적으로 운행을 취소해야 함
                    }
                }
                // 신규 콜 팝업: 뒤로가기로 팝업 닫기
                uiState.newCallPopup != null -> {
                    BackHandler(enabled = true) {
                        viewModel.dismissNewCallPopup()
                    }
                }
            }

            when {
                uiState.newCallPopup != null -> {
                    val newCallPopup = uiState.newCallPopup!!
                    val pendingCount = uiState.assignedCalls.count {
                        it.id != newCallPopup.id && it.statusEnum == CallStatus.ASSIGNED
                    }
                    NewCallPopup(
                        callInfo = newCallPopup,
                        onAccept = { viewModel.acceptCall(newCallPopup.id) },
                        onDismiss = { viewModel.dismissNewCallPopup() },
                        pendingCallCount = pendingCount
                    )
                }
                uiState.activeCall != null -> {
                    val activeCall = uiState.activeCall!!
                    when (activeCall.statusEnum) {
                        CallStatus.ACCEPTED -> {
                            TripPreparationScreen(
                                callInfo = activeCall,
                                onStartDriving = { departure, destination, waypoints, fare ->
                                    viewModel.startDriving(
                                        activeCall.id,
                                        departure,
                                        destination,
                                        waypoints,
                                        fare
                                    )
                                },
                                onCancel = { cancelReason ->
                                    viewModel.cancelTrip(activeCall.id, cancelReason)
                                }
                            )
                        }
                        CallStatus.IN_PROGRESS -> {
                            InProgressScreen(
                                callInfo = activeCall,
                                onCompleteTrip = { viewModel.completeCall(activeCall.id) }
                            )
                        }
                        else -> {
                            WaitingScreen(
                                driverStatus = uiState.driverStatus,
                                onGoOnline = { viewModel.updateDriverStatus(DriverStatus.ONLINE) },
                                onCheckPendingDispatch = { viewModel.checkForPendingDispatch() },
                                onShowReferralQR = { navController.navigate(AppDestinations.REFERRAL_QR_ROUTE) }
                            )
                        }
                    }
                }
                uiState.driverStatus == DriverStatus.OFFLINE -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("현재 오프라인 상태입니다.", style = MaterialTheme.typography.headlineSmall)
                    }
                }
                else -> {
                    WaitingScreen(
                        driverStatus = uiState.driverStatus,
                        onGoOnline = { viewModel.updateDriverStatus(DriverStatus.ONLINE) },
                        onCheckPendingDispatch = { viewModel.checkForPendingDispatch() },
                        onShowReferralQR = { navController.navigate(AppDestinations.REFERRAL_QR_ROUTE) }
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            uiState.callForSettlement?.let { call ->
                SettlementSummaryPopup(
                    callInfo = call,
                    onConfirm = { paymentMethod, cashAmount, finalFare, pointsToUse ->  // ✅ 추가: pointsToUse 파라미터
                        viewModel.confirmAndFinalizeTrip(
                            callId = call.id,
                            paymentMethod = paymentMethod,
                            cashAmount = cashAmount,
                            fareToSet = finalFare,
                            tripSummaryToSet = call.trip_summary ?: "",
                            pointsToUse = pointsToUse  // ✅ 추가: 리워드 포인트 전달
                        )
                    },
                    onDismiss = {
                        viewModel.dismissSettlementPopup()
                    }
                )
            }
        }
    }

    // 로그아웃 확인 다이얼로그 (미저장 정산 데이터 있을 때)
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("종료 확인") },
            text = {
                if (unsavedTripCount > 0) {
                    Text("저장되지 않은 운행 내역이 ${unsavedTripCount}건 있습니다.\n정산 내역을 저장하고 종료하시겠습니까?")
                } else {
                    Text("정말 종료하시겠습니까?")
                }
            },
            confirmButton = {
                if (unsavedTripCount > 0) {
                    Button(
                        onClick = {
                            saveAndClearSettlement(context)
                            showLogoutDialog = false
                            logoutUserAndExitApp(context, scope, viewModel)
                        }
                    ) {
                        Text("저장 후 종료")
                    }
                } else {
                    Button(
                        onClick = {
                            showLogoutDialog = false
                            logoutUserAndExitApp(context, scope, viewModel)
                        }
                    ) {
                        Text("종료")
                    }
                }
            },
            dismissButton = {
                Row {
                    if (unsavedTripCount > 0) {
                        TextButton(
                            onClick = {
                                showLogoutDialog = false
                                logoutUserAndExitApp(context, scope, viewModel)
                            }
                        ) {
                            Text("저장 없이 종료", color = Color.Red)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("취소")
                    }
                }
            }
        )
    }

    // 업무 마감 알림 다이얼로그 (FCM 수신 시)
    if (showSettlementFinalizedDialog) {
        AlertDialog(
            onDismissRequest = { showSettlementFinalizedDialog = false },
            title = { Text("업무 마감 안내") },
            text = {
                Text("사무실에서 업무를 마감했습니다.\n\n저장되지 않은 운행 내역이 ${unsavedTripCount}건 있습니다.\n정산 내역을 저장하시겠습니까?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        saveAndClearSettlement(context)
                        showSettlementFinalizedDialog = false
                        logoutUserAndExitApp(context, scope, viewModel)
                    }
                ) {
                    Text("저장 후 종료")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            saveAndClearSettlement(context)
                            showSettlementFinalizedDialog = false
                        }
                    ) {
                        Text("저장만 하기")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { showSettlementFinalizedDialog = false }
                    ) {
                        Text("나중에")
                    }
                }
            }
        )
    }
}

@Composable
fun CompletedScreen(callInfo: CallInfo, onRequestSettlement: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("운행 완료", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("요금: ${callInfo.fare_set ?: 0}원")
        Text("경로: ${callInfo.trip_summary ?: "정보 없음"}")
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestSettlement) {
            Text("정산하기")
        }
    }
}

@Composable
fun SettlementSummaryPopup(
    callInfo: CallInfo,
    onConfirm: (String, Int?, Int, Int) -> Unit,  // ✅ 추가: pointsToUse 파라미터
    onDismiss: () -> Unit
) {
    var paymentMethod by remember { mutableStateOf("현금") }
    var cashAmount by remember { mutableStateOf("") }
    var editableFare by remember { mutableStateOf((callInfo.fare_set ?: 0).toString()) }
    var isEditingFare by remember { mutableStateOf(false) }

    // ✅ 추가: 리워드 포인트 관련 상태
    var rewardPointsToUse by remember { mutableStateOf("") }
    var customerPointInfo by remember { mutableStateOf<Map<String, Any>?>(null) }
    var isLoadingPoints by remember { mutableStateOf(false) }
    var isActuallyAppCustomer by remember { mutableStateOf(callInfo.isAppCustomer) }  // 실제 앱회원 여부
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ✅ 수정: 전화번호가 있으면 항상 customerPoints 확인 (isAppCustomer에 의존하지 않음)
    LaunchedEffect(callInfo.phoneNumber) {
        if (callInfo.phoneNumber.isNotBlank()) {
            isLoadingPoints = true
            try {
                val prefs = context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                val provinceId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, "") ?: ""
                val cityId = prefs.getString(Constants.PREF_KEY_CITY_ID, "") ?: ""
                val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, "") ?: ""

                if (provinceId.isNotEmpty() && cityId.isNotEmpty() && officeId.isNotEmpty()) {
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val doc = firestore
                        .collection("provinces").document(provinceId)
                        .collection("cities").document(cityId)
                        .collection("offices").document(officeId)
                        .collection("customerPoints")
                        .document(callInfo.phoneNumber)
                        .get()
                        .await()

                    if (doc.exists()) {
                        isActuallyAppCustomer = true  // customerPoints 문서가 있으면 앱회원
                        customerPointInfo = mapOf(
                            "currentPoints" to (doc.getLong("currentPoints")?.toInt() ?: 0),
                            "grade" to (doc.getString("grade") ?: "BRONZE"),
                            "totalCalls" to (doc.getLong("totalCalls")?.toInt() ?: 0)
                        )
                        Log.d(TAG, "앱회원 확인됨 - phoneNumber: ${callInfo.phoneNumber}, points: ${customerPointInfo}")
                    } else {
                        isActuallyAppCustomer = false
                        Log.d(TAG, "비앱회원 - phoneNumber: ${callInfo.phoneNumber}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "포인트 정보 로드 실패", e)
            } finally {
                isLoadingPoints = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "정산 확인",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB000)
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "운행 정보",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFB000)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        callInfo.customerName?.let { name ->
                            Text(
                                "고객명: $name",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        val departure = callInfo.departure_set ?: "출발지"
                        val destination = callInfo.destination_set ?: "도착지"
                        Text(
                            "경로: $departure → $destination",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )

                        callInfo.waypoints_set?.takeIf { it.isNotBlank() }?.let { waypoints ->
                            Text(
                                "경유지: $waypoints",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isEditingFare) {
                                OutlinedTextField(
                                    value = editableFare,
                                    onValueChange = { editableFare = it.filter { c -> c.isDigit() } },
                                    label = { Text("요금 (원)", color = Color.Gray) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFFFFB000),
                                        unfocusedBorderColor = Color.Gray,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = Color(0xFFFFB000)
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        isEditingFare = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "확인",
                                        tint = Color(0xFFFFB000)
                                    )
                                }
                            } else {
                                Text(
                                    "요금: ${String.format("%,d", editableFare.toIntOrNull() ?: 0)}원",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB000),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        isEditingFare = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "요금 수정",
                                        tint = Color(0xFFFFB000)
                                    )
                                }
                            }
                        }
                    }
                }

                // ✅ 수정: 앱 회원 포인트 정보 카드 - 현금+포인트 또는 포인트 결제 선택 시에만 활성화
                val isPointPaymentSelected = paymentMethod == "현금+포인트" || paymentMethod == "포인트"
                if (isActuallyAppCustomer && customerPointInfo != null && isPointPaymentSelected) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A4A2A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "앱 회원 리워드 포인트",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // ✅ 수정: 스마트 캐스트 문제 해결 - 로컬 변수 사용
                            val pointInfo = customerPointInfo
                            val currentPoints = pointInfo?.get("currentPoints") as? Int ?: 0
                            val grade = pointInfo?.get("grade") as? String ?: "BRONZE"

                            Text(
                                "보유 포인트: ${String.format("%,d", currentPoints)}P",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                "등급: $grade",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = rewardPointsToUse,
                                onValueChange = {
                                    val input = it.filter { c -> c.isDigit() }
                                    val inputInt = input.toIntOrNull() ?: 0
                                    // 보유 포인트와 요금 중 작은 값으로 제한
                                    val maxUsable = minOf(currentPoints, editableFare.toIntOrNull() ?: 0)
                                    rewardPointsToUse = if (inputInt > maxUsable) maxUsable.toString() else input
                                },
                                label = { Text("사용할 포인트 (P)", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF4CAF50),
                                    unfocusedBorderColor = Color.Gray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color(0xFF4CAF50)
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )

                            if (rewardPointsToUse.isNotEmpty()) {
                                val pointsUsed = rewardPointsToUse.toIntOrNull() ?: 0
                                val totalFare = editableFare.toIntOrNull() ?: 0
                                val finalPayment = totalFare - pointsUsed
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "포인트 차감 후 최종 결제액: ${String.format("%,d", finalPayment)}원",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                } else if (isLoadingPoints) {
                    // 로딩 중
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFF4CAF50))
                        }
                    }
                }

                Text(
                    "결제 방법",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB000)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = paymentMethod == "현금",
                            onClick = { paymentMethod = "현금" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFFB000),
                                unselectedColor = Color.Gray
                            )
                        )
                        Text(
                            "현금",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = paymentMethod == "외상",
                            onClick = { paymentMethod = "외상" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFFB000),
                                unselectedColor = Color.Gray
                            )
                        )
                        Text(
                            "외상",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = paymentMethod == "이체",
                            onClick = { paymentMethod = "이체" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFFB000),
                                unselectedColor = Color.Gray
                            )
                        )
                        Text(
                            "이체 (외상)",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = paymentMethod == "현금+포인트",
                            onClick = { paymentMethod = "현금+포인트" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFFB000),
                                unselectedColor = Color.Gray
                            )
                        )
                        Text(
                            "현금+포인트 (일부 외상)",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = paymentMethod == "포인트",
                            onClick = { paymentMethod = "포인트" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFFB000),
                                unselectedColor = Color.Gray
                            )
                        )
                        Text(
                            "포인트 (외상)",
                            modifier = Modifier.weight(1f),
                            color = Color.White
                        )
                    }
                }

                // 현금+포인트 선택 시 받은 현금 입력 (앱회원 리워드 포인트 사용 시에는 표시 안함)
                if (paymentMethod == "현금+포인트" && !(isActuallyAppCustomer && customerPointInfo != null)) {
                    OutlinedTextField(
                        value = cashAmount,
                        onValueChange = { cashAmount = it.filter { c -> c.isDigit() } },
                        label = {
                            Text(
                                "받은 현금 (원) - 나머지는 외상",
                                color = Color.Gray
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFFB000),
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFFFFB000)
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cashAmount.isNotEmpty()) {
                        val cash = cashAmount.toIntOrNull() ?: 0
                        val totalFare = editableFare.toIntOrNull() ?: 0
                        val pointAmount = totalFare - cash
                        if (pointAmount > 0) {
                            Text(
                                "외상: ${String.format("%,d", pointAmount)}원",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFB000)
                            )
                        }
                    }
                }

                // 포인트 잔액 검증
                val pointInfo = customerPointInfo
                val currentPoints = pointInfo?.get("currentPoints") as? Int ?: 0
                val totalFare = editableFare.toIntOrNull() ?: 0

                val requiredPoints = when (paymentMethod) {
                    "포인트" -> totalFare
                    "현금+포인트" -> {
                        val cash = cashAmount.toIntOrNull() ?: 0
                        totalFare - cash
                    }
                    else -> 0
                }

                val isPointPayment = paymentMethod == "포인트" || paymentMethod == "현금+포인트"
                val hasEnoughPoints = !isPointPayment || currentPoints >= requiredPoints

                // 포인트 부족 경고 메시지 (실제 앱회원 여부로 판단)
                if (isPointPayment && !hasEnoughPoints && isActuallyAppCustomer) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A2A2A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "⚠️ 포인트 부족",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "보유: ${String.format("%,d", currentPoints)}P / 필요: ${String.format("%,d", requiredPoints)}P",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                "부족: ${String.format("%,d", requiredPoints - currentPoints)}P",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFF5252)
                            )
                        }
                    }
                }

                // 비앱 회원이 포인트 결제 시도 시 경고 (실제 앱회원 여부로 판단)
                if (isPointPayment && !isActuallyAppCustomer) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4A2A2A))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "⚠️ 앱 회원만 포인트 결제 가능",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF5252)
                            )
                            Text(
                                "이 고객은 앱 회원이 아닙니다.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                val confirmEnabled = when {
                    paymentMethod == "현금+포인트" && cashAmount.isBlank() -> false
                    isPointPayment && !isActuallyAppCustomer -> false  // 비앱 회원 포인트 결제 차단
                    isPointPayment && !hasEnoughPoints -> false  // 포인트 부족 시 차단
                    else -> true
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242),
                            contentColor = Color.White
                        )
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = {
                            if (!confirmEnabled) return@Button
                            val finalFare = editableFare.toIntOrNull() ?: (callInfo.fare_set ?: 0)
                            val amount = when (paymentMethod) {
                                "현금" -> finalFare
                                "현금+포인트" -> cashAmount.toIntOrNull()
                                else -> null
                            }
                            // ✅ 포인트 사용액 계산
                            val pointsToUse = when (paymentMethod) {
                                "포인트" -> finalFare  // 포인트 전액 사용
                                "현금+포인트" -> {
                                    val cash = cashAmount.toIntOrNull() ?: 0
                                    finalFare - cash  // 요금 - 현금 = 포인트 사용액
                                }
                                else -> 0  // 다른 결제 방법은 포인트 미사용
                            }
                            onConfirm(paymentMethod, amount, finalFare, pointsToUse)
                        },
                        enabled = confirmEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (confirmEnabled) Color(0xFFFFB000) else Color(0xFF888888),
                            contentColor = Color.Black
                        )
                    ) {
                        Text("정산 완료", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
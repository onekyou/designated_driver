package com.designated.driverapp.ui.home

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import com.designated.driverapp.ui.home.logoutUserAndExitApp
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
        val regionId = prefs.getString("regionId", null)
        val officeId = prefs.getString("officeId", null)

        if (regionId != null && officeId != null) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val document = firestore.collection("regions").document(regionId)
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
                modifier = Modifier.fillMaxWidth(),
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
                            logoutUserAndExitApp(context, scope, viewModel)
                            navController.navigate(AppDestinations.LOGIN_ROUTE) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
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
            when {
                uiState.newCallPopup != null -> {
                    val newCallPopup = uiState.newCallPopup!!
                    NewCallPopup(
                        callInfo = newCallPopup,
                        onAccept = { viewModel.acceptCall(newCallPopup.id) },
                        onDismiss = { viewModel.dismissNewCallPopup() }
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
                                onGoOnline = { viewModel.updateDriverStatus(DriverStatus.ONLINE) }
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("온라인으로 전환하여 콜을 받으세요.")
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(onClick = { viewModel.updateDriverStatus(DriverStatus.ONLINE) }) {
                            Text("온라인으로 전환")
                        }
                    }
                }
                else -> {
                    WaitingScreen(
                        driverStatus = uiState.driverStatus,
                        onGoOnline = { viewModel.updateDriverStatus(DriverStatus.ONLINE) }
                    )
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator()
            }

            uiState.callForSettlement?.let { call ->
                SettlementSummaryPopup(
                    callInfo = call,
                    onConfirm = { paymentMethod, cashAmount, finalFare ->
                        viewModel.confirmAndFinalizeTrip(
                            callId = call.id,
                            paymentMethod = paymentMethod,
                            cashAmount = cashAmount,
                            fareToSet = finalFare,
                            tripSummaryToSet = call.trip_summary ?: ""
                        )
                    },
                    onDismiss = {
                        viewModel.dismissSettlementPopup()
                    }
                )
            }
        }
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
    onConfirm: (String, Int?, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var paymentMethod by remember { mutableStateOf("현금") }
    var cashAmount by remember { mutableStateOf("") }
    var editableFare by remember { mutableStateOf((callInfo.fare_set ?: 0).toString()) }
    var isEditingFare by remember { mutableStateOf(false) }

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
                            selected = paymentMethod == "카드",
                            onClick = { paymentMethod = "카드" },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFFFFB000),
                                unselectedColor = Color.Gray
                            )
                        )
                        Text(
                            "카드 (외상)",
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

                if (paymentMethod == "현금+포인트") {
                    OutlinedTextField(
                        value = cashAmount,
                        onValueChange = { cashAmount = it.filter { c -> c.isDigit() } },
                        label = {
                            Text(
                                "받은 현금 (원) - 나머지는 포인트",
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
                                "포인트: ${String.format("%,d", pointAmount)}원 (외상)",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFB000)
                            )
                        }
                    }
                }

                val confirmEnabled = paymentMethod != "현금+포인트" || cashAmount.isNotBlank()

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
                            onConfirm(paymentMethod, amount, finalFare)
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
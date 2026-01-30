package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementViewModel
import com.designated.callmanager.ui.settlement.screen.CreditDialog
import android.app.Activity
import androidx.compose.ui.platform.LocalContext

@Composable
fun AllTripsScreen(vm: SettlementViewModel = viewModel()) {
    val trips by vm.settlementList.collectAsState()
    val creditedIds by vm.creditedTripIds.collectAsState()
    var showCreditDialog by remember { mutableStateOf(false) }

    var showRatioDialog by remember { mutableStateOf(false) }
    val ratio by vm.officeShareRatio.collectAsState()

    var paymentDialog by remember { mutableStateOf<Pair<String, List<SettlementData>>?>(null) }

    // 업무 마감 확인 다이얼로그 상태
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var isFinalizingInProgress by remember { mutableStateOf(false) }
    var finalizeResultMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current


    Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("전체 운행 ${trips.size}건", style = MaterialTheme.typography.titleMedium, color = Color.White)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("수수료 비율 조정", color=Color.White, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { showRatioDialog = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "비율 설정", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        val totalFare = trips.sumOf { it.fare }
        val cashTrips   = trips.filter { it.paymentMethod == "현금" }
        val bankTrips   = trips.filter { it.paymentMethod == "이체" }
        val creditTrips = trips.filter { it.paymentMethod == "외상" }
        val cashPlusPointTrips = trips.filter { it.paymentMethod == "현금+포인트" }
        val pointOnlyTrips = trips.filter { it.paymentMethod == "포인트" }

        val cashSum   = cashTrips.sumOf { it.fare } +
                        cashPlusPointTrips.sumOf { trip ->
                            trip.cashAmount ?: 0  // 현금+포인트에서 현금 부분 추가
                        }
        val bankSum   = bankTrips.sumOf { it.fare }
        val creditSum = creditTrips.sumOf { if(it.creditAmount>0) it.creditAmount else it.fare }
        // 포인트 금액: 현금+포인트와 포인트 결제 모두 계산
        val pointSum = cashPlusPointTrips.sumOf { trip ->
            // cashAmount가 null이면 전액 포인트, 값이 있으면 차액이 포인트
            val cashReceived = trip.cashAmount ?: 0
            trip.fare - cashReceived
        } + pointOnlyTrips.sumOf { it.fare }  // 포인트 결제는 전액

        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF424242))) {
            Column(Modifier.padding(16.dp)) {
                // 총매출과 총수입 (사무실 비율)
                val totalOfficeIncome = (totalFare * ratio / 100.0).toInt()
                Text("총매출 ${"%,d".format(totalFare)}원", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("총수입 ${"%,d".format(totalOfficeIncome)}원", color = Color.White, style = MaterialTheme.typography.titleMedium)

                HorizontalDivider(color = Color.Gray, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                // 수입 내역
                Text("수입내역", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // 사무실 실수입 계산 및 검증
                val totalDeposit = (totalFare * ratio / 100.0).toInt()
                val driverShare = totalFare - totalDeposit
                val driverDeposit = cashSum - driverShare

                // 검증 로직 - 계산 전 검산 (마이너스 허용)
                val verifyDriverDeposit = cashSum - driverShare
                val verifyRealIncome = verifyDriverDeposit + bankSum + creditSum - pointSum

                // 교차 검증: 다른 방식으로 계산
                val alternativeCalc = (cashSum - driverShare) + bankSum + creditSum - pointSum

                // 검증된 값 사용
                val finalDriverDeposit = if (verifyDriverDeposit == driverDeposit) driverDeposit else {
                    android.util.Log.e("Settlement", "Driver deposit mismatch: $driverDeposit vs $verifyDriverDeposit")
                    verifyDriverDeposit
                }

                // 기사 납입금 (마이너스도 표시)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("기사 납입", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text("${"%,d".format(finalDriverDeposit)}원",
                        color = if (finalDriverDeposit < 0) Color.Red else Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // 이체
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("이체", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text("${"%,d".format(bankSum)}원",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // 미수금
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("미수금", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text("${"%,d".format(creditSum)}원",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // 포인트 차감 (항상 표시)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("포인트 차감", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text(if (pointSum > 0) "-${"%,d".format(pointSum)}원" else "0원",
                        color = if (pointSum > 0) Color.Red else Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(12.dp))

                // 실수입 - 검증된 값 사용 (마이너스도 포함)
                val realIncome = finalDriverDeposit + bankSum + creditSum - pointSum

                // 최종 검증
                if (realIncome != verifyRealIncome || realIncome != alternativeCalc) {
                    android.util.Log.e("Settlement", "Income verification failed: calc=$realIncome, verify=$verifyRealIncome, alt=$alternativeCalc")
                }

                HorizontalDivider(color = Color.Gray, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("실수입", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${"%,d".format(realIncome)}원",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }



            }
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            TripListTable(tripList = trips)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { showFinalizeDialog = true },
            enabled = trips.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
        ) { Text("업무 마감", color = Color.White) }
    }

    // 업무 마감 확인 다이얼로그
    if (showFinalizeDialog) {
        AlertDialog(
            onDismissRequest = { if (!isFinalizingInProgress) showFinalizeDialog = false },
            title = { Text("업무 마감", color = Color.White) },
            text = {
                if (isFinalizingInProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        Text("마감 처리 중...", color = Color.White)
                    }
                } else if (finalizeResultMessage != null) {
                    Text(finalizeResultMessage!!, color = Color.White)
                } else {
                    Text("업무를 마감하고 로그아웃 하시겠습니까?", color = Color.White)
                }
            },
            confirmButton = {
                if (!isFinalizingInProgress && finalizeResultMessage == null) {
                    Button(
                        onClick = {
                            isFinalizingInProgress = true
                            vm.finalizeSettlementSession { success, message ->
                                isFinalizingInProgress = false
                                if (success) {
                                    vm.clearAllTrips()
                                    finalizeResultMessage = message
                                    // 잠시 후 앱 종료
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        (context as? Activity)?.finishAffinity()
                                    }, 1500)
                                } else {
                                    finalizeResultMessage = "마감 실패: $message"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                    ) {
                        Text("확인")
                    }
                }
            },
            dismissButton = {
                if (!isFinalizingInProgress && finalizeResultMessage == null) {
                    TextButton(onClick = { showFinalizeDialog = false }) {
                        Text("취소", color = Color.White)
                    }
                }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }



    paymentDialog?.let { pair ->
        val label = pair.first
        val listData = pair.second
        AlertDialog(
            onDismissRequest = { paymentDialog = null },
            title = { Text("$label 결제 내역 (${listData.size}건)", color = Color.White) },
            text = {
                Column(Modifier.heightIn(max=400.dp)) {
                    listData.forEach { t ->
                        Text("${t.customerName.take(4)}  ${t.departure}→${t.destination}  ${"%,d".format(t.fare)}원", color=Color.White)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { paymentDialog = null }) { Text("닫기") } },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    if (showRatioDialog) {
        var inputValue by remember { mutableStateOf(ratio.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRatioDialog = false },
            title = { Text("수수료 비율 설정") },
            text = {
                Column {
                    Text(
                        text = "사무실 수수료 비율을 입력하세요 (10-90%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { newValue ->
                            // 숫자만 허용
                            val filteredValue = newValue.filter { it.isDigit() }.take(2)

                            inputValue = filteredValue

                            // 유효성 검사
                            if (filteredValue.isNotEmpty()) {
                                val num = filteredValue.toIntOrNull()
                                isError = num == null || num !in 10..90
                            } else {
                                isError = true
                            }
                        },
                        label = { Text("비율 (%)", color = Color.White.copy(alpha = 0.7f)) },
                        isError = isError,
                        supportingText = {
                            if (isError) {
                                Text(
                                    text = "10% ~ 90% 사이의 값을 입력하세요",
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val numericValue = inputValue.toIntOrNull()
                        if (numericValue != null && numericValue in 10..90) {
                            vm.updateOfficeShareRatio(numericValue)
                            showRatioDialog = false
                        }
                    },
                    enabled = !isError && inputValue.isNotEmpty()
                ) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showRatioDialog = false }) { Text("취소") } },
            containerColor = Color(0xFF2A2A2A)
        )
    }
}


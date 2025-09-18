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
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementViewModel
import com.designated.callmanager.ui.settlement.screen.CreditDialog
import com.designated.callmanager.ui.settlement.screen.TripDetailDialog

@Composable
fun AllTripsScreen(vm: SettlementViewModel = viewModel()) {
    val trips by vm.settlementList.collectAsState()
    val creditedIds by vm.creditedTripIds.collectAsState()
    var selectedTrip by remember { mutableStateOf<SettlementData?>(null) }
    var showCreditDialog by remember { mutableStateOf(false) }

    var showRatioDialog by remember { mutableStateOf(false) }
    val ratio by vm.officeShareRatio.collectAsState()

    var paymentDialog by remember { mutableStateOf<Pair<String, List<SettlementData>>?>(null) }

    var phoneForDialog by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("전체 운행 ${trips.size}건", style = MaterialTheme.typography.titleMedium, color = Color.White)

        val totalFare = trips.sumOf { it.fare }
        val cashTrips   = trips.filter { it.paymentMethod == "현금" }
        val bankTrips   = trips.filter { it.paymentMethod == "이체" }
        val creditTrips = trips.filter { it.paymentMethod == "외상" }
        val cashPlusPointTrips = trips.filter { it.paymentMethod == "현금+포인트" }

        val cashSum   = cashTrips.sumOf { it.fare } +
                        cashPlusPointTrips.sumOf { trip ->
                            trip.cashAmount ?: 0  // 현금+포인트에서 현금 부분 추가
                        }
        val bankSum   = bankTrips.sumOf { it.fare }
        val creditSum = creditTrips.sumOf { if(it.creditAmount>0) it.creditAmount else it.fare }
        // 포인트 금액: 현금+포인트에서 포인트 부분만 계산
        val pointSum = cashPlusPointTrips.sumOf { trip ->
            val cashReceived = trip.cashAmount ?: 0
            if (cashReceived > 0) trip.fare - cashReceived else trip.fare
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentStatCard(label="현금", amount=cashSum, color=Color(0xFF4CAF50), modifier=Modifier.weight(1f)) { paymentDialog = "현금" to cashTrips }
            PaymentStatCard(label="이체", amount=bankSum, color=Color(0xFF03A9F4), modifier=Modifier.weight(1f)) { paymentDialog = "이체" to bankTrips }
            PaymentStatCard(label="외상", amount=creditSum, color=Color(0xFFF44336), modifier=Modifier.weight(1f)) { paymentDialog = "외상" to creditTrips }
            PaymentStatCard(label="포인트", amount=pointSum, color=Color(0xFF9C27B0), modifier=Modifier.weight(1f)) { paymentDialog = "포인트" to cashPlusPointTrips }
        }

        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF424242))) {
            Column(Modifier.padding(16.dp)) {
                Text("총 매출: %,d원".format(totalFare), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                val totalDeposit = (totalFare * ratio / 100.0).toInt()  // 총 납입금

                // 총 외상 계산 (기사앱과 동일한 로직)
                val totalCredit = trips.sumOf { trip ->
                    when {
                        trip.paymentMethod == "현금" -> 0
                        trip.paymentMethod == "현금+포인트" -> {
                            val cashReceived = trip.cashAmount ?: 0
                            if (cashReceived > 0) trip.fare - cashReceived else trip.fare
                        }
                        else -> trip.fare // 카드, 이체, 외상은 전액 외상
                    }
                }

                val realDeposit = totalDeposit - totalCredit  // 기사 납입금
                val totalRealIncome = totalFare - pointSum  // 총 실수입 (포인트 손실 제외)

                Text("총 납입금(비율 ${ratio}%): ${"%,d".format(totalDeposit)}원", color = Color.White)
                Text("기사 납입금: ${"%,d".format(realDeposit)}원", color = Color.LightGray)
                Text("총 실수입: ${"%,d".format(totalRealIncome)}원", color = Color.Yellow, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("납입금 비율 조정", color=Color.White)
                    IconButton(onClick = { showRatioDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "비율 설정", tint = Color.White)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(Modifier.weight(1f)) {
            TripListTable(tripList = trips, onShowDetail = { selectedTrip = it })
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.clearAllTrips() },
            enabled = trips.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
        ) { Text("업무 마감", color = Color.White) }
    }

    selectedTrip?.let { trip ->
        TripDetailDialog(settlement = trip, onDismiss = { selectedTrip = null })
        vm.fetchPhoneForCall(trip.callId) { ph -> phoneForDialog = ph ?: "" }
        if (!creditedIds.contains(trip.callId)) {
            vm.fetchPhoneForCall(trip.callId) { ph ->
                phoneForDialog = ph ?: ""
                showCreditDialog = true
            }
        }
    }

    if (showCreditDialog && selectedTrip != null) {
        CreditDialog(
            trip = selectedTrip!!,
            initialPhone = phoneForDialog,
            onDismiss = { showCreditDialog = false; selectedTrip = null },
            onRegister = { name, phone, amount ->
                vm.addOrIncrementCredit(
                    name = name,
                    phone = phone,
                    addAmount = amount,
                    detail = SettlementViewModel.CreditEntry(
                        date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()),
                        departure = selectedTrip!!.departure,
                        destination = selectedTrip!!.destination,
                        amount = amount
                    )
                )
                vm.markTripCredited(selectedTrip!!.callId)
                showCreditDialog = false
                selectedTrip = null
            }
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
            title = { Text("납입금 비율 설정") },
            text = {
                Column {
                    Text(
                        text = "기사 납입금 비율을 입력하세요 (10-90%)",
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

@Composable
private fun PaymentStatCard(label: String, amount: Int, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = color.copy(alpha=0.25f))) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = color, style = MaterialTheme.typography.bodyMedium)
            Text("%,d".format(amount), color = Color.White)
        }
    }
}
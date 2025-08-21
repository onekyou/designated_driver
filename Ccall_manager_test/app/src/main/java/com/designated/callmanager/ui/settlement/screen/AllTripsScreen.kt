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

    // 비율 조정 다이얼로그 상태 및 현재 비율
    var showRatioDialog by remember { mutableStateOf(false) }
    val ratio by vm.officeShareRatio.collectAsState()

    // 결제별 상세 다이얼로그 state (label, list)
    var paymentDialog by remember { mutableStateOf<Pair<String, List<SettlementData>>?>(null) }

    var phoneForDialog by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("전체 운행 ${trips.size}건", style = MaterialTheme.typography.titleMedium, color = Color.White)

        // ▶ 요약 카드
        val totalFare = trips.sumOf { it.fare }
        val cashTrips   = trips.filter { it.paymentMethod == "현금" }
        val bankTrips   = trips.filter { it.paymentMethod == "이체" }
        val creditTrips = trips.filter { it.paymentMethod == "외상" }
        val cardTrips   = trips.filter { it.paymentMethod == "카드" }

        val cashSum   = cashTrips.sumOf { it.fare }
        val bankSum   = bankTrips.sumOf { it.fare }
        val creditSum = creditTrips.sumOf { if(it.creditAmount>0) it.creditAmount else it.fare }
        val cardSum   = cardTrips.sumOf { it.fare }

        Spacer(Modifier.height(8.dp))

        // 결제별 간단 카드 Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaymentStatCard(label="현금", amount=cashSum, color=Color(0xFF4CAF50), modifier=Modifier.weight(1f)) { paymentDialog = "현금" to cashTrips }
            PaymentStatCard(label="이체", amount=bankSum, color=Color(0xFF03A9F4), modifier=Modifier.weight(1f)) { paymentDialog = "이체" to bankTrips }
            PaymentStatCard(label="외상", amount=creditSum, color=Color(0xFFF44336), modifier=Modifier.weight(1f)) { paymentDialog = "외상" to creditTrips }
            PaymentStatCard(label="카드", amount=cardSum, color=Color(0xFFFF9800), modifier=Modifier.weight(1f)) { paymentDialog = "카드" to cardTrips }
        }

        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF424242))) {
            Column(Modifier.padding(16.dp)) {
                Text("총 매출: %,d원".format(totalFare), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(4.dp))
                val totalExpense = (totalFare * ratio / 100.0).toInt()
                val totalProfit  = totalFare - totalExpense
                Text("총 지출(비율 ${ratio}%): ${"%,d".format(totalExpense)}원", color = Color.White)
                Text("총 수입: ${"%,d".format(totalProfit)}원", color = Color.Yellow, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("지출 비율 조정", color=Color.White)
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

    // 상세 다이얼로그
    selectedTrip?.let { trip ->
        TripDetailDialog(settlement = trip, onDismiss = { selectedTrip = null })
        // For external credit dialog fetch phone
        vm.fetchPhoneForCall(trip.callId) { ph -> phoneForDialog = ph ?: "" }
        // TripDetailDialog 는 단순 정보 표시이므로 외상 등록 버튼은 AllTripsScreen 의 버튼 유지
        if (!creditedIds.contains(trip.callId)) {
            // 외상 등록 버튼 – 클릭 시 전화번호 fetch 후 다이얼로그
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

    // 결제별 상세 다이얼로그
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
        var sliderVal by remember { mutableStateOf(ratio.toFloat()) }
        AlertDialog(
            onDismissRequest = { showRatioDialog = false },
            title = { Text("지출 비율 설정") },
            text = {
                Column {
                    Slider(value = sliderVal, onValueChange = { sliderVal = it }, valueRange = 5f..95f, steps = 18)
                    Text("${sliderVal.roundToInt()}%", color = Color.White)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateOfficeShareRatio(sliderVal.roundToInt())
                    showRatioDialog = false
                }) { Text("확인") }
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
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementViewModel

/**
 * 0번 탭 – 정산 대기 내역.
 * 이체/외상 결제 중 아직 업무 마감되지 않은 콜을 보여준다.
 */
@Composable
fun PendingSettlementsScreen(vm: SettlementViewModel = viewModel()) {
    val trips by vm.settlementList.collectAsState()
    var selectedTrip by remember { mutableStateOf<SettlementData?>(null) }
    var showCreditDialog by remember { mutableStateOf(false) }
    var phoneForDialog by remember { mutableStateOf("") }

    val creditedIds by vm.creditedTripIds.collectAsState()

    val pending = trips.filter { it.paymentMethod in listOf("이체", "외상") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val unprocessedCount = pending.count { !creditedIds.contains(it.callId) }
        Text("정산 처리 ${pending.size}건 (미처리 ${unprocessedCount}건)", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        if (pending.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("정산 대기 내역이 없습니다.", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(pending) { item ->
                    PendingRow(item,
                        creditedIds = creditedIds,
                        onCredit = {
                            vm.fetchPhoneForCall(item.callId) { ph ->
                                phoneForDialog = ph ?: ""
                                selectedTrip = item
                                showCreditDialog = true
                            }
                        },
                        onConfirm = {
                            vm.markTripCredited(item.callId)
                        })
                }
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
}

@Composable
private fun PendingRow(item: SettlementData, creditedIds: Set<String>, onCredit: () -> Unit, onConfirm: () -> Unit) {
    val isProcessed = creditedIds.contains(item.callId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = if (isProcessed) Color(0xFF1A3A1A) else Color(0xFF2A2A2A))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(item.customerName, Modifier.weight(1f), color = Color.White)
                Text("${item.driverName}: ${item.paymentMethod}", color = Color.Yellow)
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${item.departure} ➜ ${item.destination}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.weight(1f)
                )
                Text("${"%,d".format(item.fare)}원", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))

            if (isProcessed) {
                // 이미 처리된 경우
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text("✓ 정산 처리 완료", color = Color.Green, style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // 결제방식에 따른 개별 버튼
                when (item.paymentMethod) {
                    "이체" -> {
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("이체 확인", color = Color.White)
                        }
                    }
                    "외상" -> {
                        Button(
                            onClick = onCredit,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text("외상 등록", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
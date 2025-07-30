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

    val pending = trips.filter { it.paymentMethod in listOf("이체", "외상") && !creditedIds.contains(it.callId) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("정산 대기 ${pending.size}건", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        if (pending.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("정산 대기 내역이 없습니다.", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(pending) { item ->
                    PendingRow(item,
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
private fun PendingRow(item: SettlementData, onCredit: () -> Unit, onConfirm: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(item.customerName, Modifier.weight(1f), color = Color.White)
                Text(item.paymentMethod, color = Color.Yellow)
            }
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${item.departure} ➜ ${item.destination}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.weight(1f)
                )
                Text("${item.fare}원", color = Color.White)
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.paymentMethod == "이체") {
                    Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text("정산 확인")
                    }
                }
                if (item.paymentMethod == "외상") {
                    OutlinedButton(onClick = onCredit, modifier = Modifier.weight(1f)) {
                        Text("외상 등록")
                    }
                }
            }
        }
    }
} 
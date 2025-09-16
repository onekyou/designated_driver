package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementViewModel
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import com.designated.callmanager.ui.settlement.screen.DateDetailDialog

@Composable
fun DriverSummaryScreen(vm: SettlementViewModel = viewModel()) {
    val trips by vm.settlementList.collectAsState()
    var showRatioDialog by remember { mutableStateOf(false) }
    val ratio by vm.officeShareRatio.collectAsState()

    val driverStats = remember(trips, ratio) {
        trips.groupBy { it.driverName.ifBlank { "미지정" } }
            .mapValues { (_, list) ->
                val fareSum = list.sumOf { it.fare }
                val nonCash = list.filter { it.paymentMethod != "현금" }.sumOf {
                    if (it.paymentMethod == "외상") {
                        if(it.creditAmount>0) it.creditAmount else it.fare
                    } else it.fare
                }
                val deposit = (fareSum * ratio / 100.0).roundToInt()
                val realDeposit = fareSum - deposit - nonCash
                DriverStat(list.first().driverName.ifBlank { "미지정" }, list.size, fareSum, deposit, nonCash, realDeposit)
            }
            .values
            .sortedByDescending { it.totalFare }
    }

    val totalFare = trips.sumOf { it.fare }
    val totalNonCash = trips.filter { it.paymentMethod != "현금" }.sumOf {
        if (it.paymentMethod == "외상") {
            if(it.creditAmount>0) it.creditAmount else it.fare
        } else it.fare
    }
    val totalDeposit = (totalFare * ratio / 100.0).roundToInt()
    val realDepositAll = totalFare - totalDeposit - totalNonCash

    var selectedDriver by remember { mutableStateOf<Pair<String,List<SettlementData>>?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("기사별 통계", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("납입 비율 ${ratio}%", color = Color.White)
            IconButton(onClick = { showRatioDialog = true }) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = Color.White)
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(driverStats) { stat ->
                DriverDetailCard(stat) {
                    val list = trips.filter { (it.driverName.ifBlank { "미지정" }) == stat.name }
                    selectedDriver = stat.name to list
                }
            }
        }
    }

    selectedDriver?.let { (name, list) ->
        DateDetailDialog(date = name, settlements = list) { selectedDriver = null }
    }

    if (showRatioDialog) {
        var inputValue by remember { mutableStateOf(ratio.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRatioDialog = false },
            title = { Text("납입 비율 설정") },
            text = {
                Column {
                    Text(
                        text = "기사 납입 비율을 입력하세요 (10-90%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { newValue ->
                            // 숫자와 % 기호만 허용
                            val filteredValue = newValue.filter { it.isDigit() || it == '%' }
                                .replace("%", "")
                                .take(2) // 최대 2자리

                            inputValue = if (filteredValue.isNotEmpty()) {
                                val num = filteredValue.toIntOrNull()
                                if (num != null && num in 10..90) {
                                    isError = false
                                    "$num%"
                                } else {
                                    isError = true
                                    "$filteredValue%"
                                }
                            } else {
                                isError = true
                                ""
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
                        val numericValue = inputValue.replace("%", "").toIntOrNull()
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

data class DriverStat(
    val name: String,
    val count: Int,
    val totalFare: Int,
    val deposit: Int,
    val nonCash: Int,
    val realDeposit: Int
)

@Composable
private fun DriverDetailCard(stat: DriverStat, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
        Column(Modifier.padding(12.dp)) {
            Text(stat.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("총 운행 횟수 :  ${stat.count} 회", color = Color.White)
            Text("총 수입 : ${"%,d".format(stat.totalFare)}원", color = Color.White)
            Text("총 납입 : ${"%,d".format(stat.deposit)}원", color = Color.White)
            Text("총 외상 : ${"%,d".format(stat.nonCash)}원", color = Color.White)
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("실납입 : ${"%,d".format(stat.realDeposit)}원", color = Color.Yellow, fontWeight = FontWeight.Bold)
        }
    }
}
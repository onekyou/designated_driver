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

    // 그룹·집계 (deposit logic)
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

        // 기사별 카드 리스트
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
        var sliderVal by remember { mutableStateOf(ratio.toFloat()) }
        AlertDialog(
            onDismissRequest = { showRatioDialog = false },
            title = { Text("납입 비율 설정") },
            text = {
                Column {
                    Slider(value = sliderVal, onValueChange = { sliderVal = it }, valueRange = 10f..90f, steps = 16)
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
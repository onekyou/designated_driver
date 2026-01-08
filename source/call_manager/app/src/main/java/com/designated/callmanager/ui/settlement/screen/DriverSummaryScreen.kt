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
    val ratio by vm.officeShareRatio.collectAsState()

    val driverStats = remember(trips, ratio) {
        trips.groupBy { it.driverName.ifBlank { "미지정" } }
            .mapValues { (_, list) ->
                val fareSum = list.sumOf { it.fare }
                val totalCredit = list.sumOf { trip ->
                    when {
                        trip.paymentMethod == "현금" -> 0
                        trip.paymentMethod == "현금+포인트" -> {
                            // 현금+포인트의 경우 포인트 부분만 외상
                            val cashReceived = trip.cashAmount ?: 0
                            if (cashReceived > 0) trip.fare - cashReceived else trip.fare
                        }
                        else -> trip.fare // 이체, 외상은 전액 외상
                    }
                }
                val deposit = (fareSum * ratio / 100.0).roundToInt()
                val realDeposit = deposit - totalCredit  // 올바른 계산: 총납입 - 총외상
                DriverStat(list.first().driverName.ifBlank { "미지정" }, list.size, fareSum, deposit, totalCredit, realDeposit)
            }
            .values
            .sortedByDescending { it.totalFare }
    }

    val totalFare = trips.sumOf { it.fare }
    val totalCredit = trips.sumOf { trip ->
        when {
            trip.paymentMethod == "현금" -> 0
            trip.paymentMethod == "현금+포인트" -> {
                // 현금+포인트의 경우 포인트 부분만 외상
                val cashReceived = trip.cashAmount ?: 0
                if (cashReceived > 0) trip.fare - cashReceived else trip.fare
            }
            else -> trip.fare // 이체, 외상은 전액 외상
        }
    }
    val totalDeposit = (totalFare * ratio / 100.0).roundToInt()
    val realDepositAll = totalDeposit - totalCredit  // 올바른 계산: 총납입 - 총외상

    var selectedDriver by remember { mutableStateOf<Pair<String,List<SettlementData>>?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("기사별 통계", style = MaterialTheme.typography.titleMedium, color = Color.White)
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

}

data class DriverStat(
    val name: String,
    val count: Int,
    val totalFare: Int,
    val deposit: Int,
    val totalCredit: Int,
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
            Text("총 외상 : ${"%,d".format(stat.totalCredit)}원", color = Color.White)
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("실납입 : ${"%,d".format(stat.realDeposit)}원", color = Color.Yellow, fontWeight = FontWeight.Bold)
        }
    }
}
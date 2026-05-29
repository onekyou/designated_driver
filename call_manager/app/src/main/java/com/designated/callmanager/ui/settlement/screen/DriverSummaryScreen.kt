package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementViewModel
import com.designated.callmanager.ui.settlement.SettlementCalculator

@Composable
fun DriverSummaryScreen(vm: SettlementViewModel = viewModel()) {
    val trips by vm.settlementList.collectAsState()
    val ratio by vm.officeShareRatio.collectAsState()
    val driverLastClearedMap by vm.driverLastClearedMap.collectAsState()

    // 기사별 마감 시점 이후 콜만 필터 (1차 마감 콜 제외)
    val filteredTrips = remember(trips, driverLastClearedMap) {
        trips.filter { trip ->
            val driverCleared = driverLastClearedMap[trip.driverId] ?: 0L
            trip.completedAt > driverCleared
        }
    }

    val driverStats = remember(filteredTrips, ratio) {
        SettlementCalculator.calculateDriverStats(filteredTrips, ratio)
    }

    var selectedDriver by remember { mutableStateOf<Pair<String, List<SettlementData>>?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("기사별 통계", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(driverStats) { stat ->
                DriverDetailCard(stat = stat) {
                    val list = filteredTrips.filter { (it.driverName.ifBlank { "미지정" }) == stat.name }
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
    val realDeposit: Int,
    val driverId: String = ""
)

@Composable
private fun DriverDetailCard(
    stat: DriverStat,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                stat.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("settlement_driver_name_${stat.driverId}")
            )
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("총 운행 횟수 :  ${stat.count} 회", color = Color.White, modifier = Modifier.testTag("settlement_driver_count_${stat.driverId}"))
            Text("총 운행료 : ${"%,d".format(stat.totalFare)}원", color = Color.White, modifier = Modifier.testTag("settlement_driver_totalFare_${stat.driverId}"))
            Text("수수료 : ${"%,d".format(stat.deposit)}원", color = Color.White, modifier = Modifier.testTag("settlement_driver_deposit_${stat.driverId}"))
            Text("미수금 : ${"%,d".format(stat.totalCredit)}원", color = Color.White, modifier = Modifier.testTag("settlement_driver_totalCredit_${stat.driverId}"))
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // 예상 납입금 = 수수료 - 미수금
            val expectedDeposit = stat.deposit - stat.totalCredit
            Text(
                "예상 납입금: ${"%,d".format(expectedDeposit)}원",
                color = Color(0xFF00BFFF),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

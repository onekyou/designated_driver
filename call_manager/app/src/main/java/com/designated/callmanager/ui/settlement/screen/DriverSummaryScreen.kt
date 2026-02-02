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
import com.designated.callmanager.data.settlement.CarryOverStatus
import com.designated.callmanager.data.settlement.DriverCarryOverSummary
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Arrangement
import com.designated.callmanager.ui.settlement.screen.DateDetailDialog
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun DriverSummaryScreen(vm: SettlementViewModel = viewModel()) {
    val trips by vm.settlementList.collectAsState()
    val ratio by vm.officeShareRatio.collectAsState()

    // 이월 정산 (기사별 미지급금) 데이터
    val carryOverList by vm.carryOverList.collectAsState()

    // 기사별 미지급금 맵 (driverId → DriverCarryOverSummary)
    val carryOverMap = remember(carryOverList) {
        carryOverList.associateBy { it.driverId }
    }

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
                // 기사의 driverId 찾기 (trips에서)
                val driverId = trips.find { it.driverName == stat.name }?.driverId ?: ""
                val carryOver = carryOverMap[driverId]

                DriverDetailCard(
                    stat = stat,
                    carryOver = carryOver,
                    onTransferClick = { driverId ->
                        vm.transferCarryOver(driverId) { _, _ -> }
                    },
                    onCancelClick = { driverId ->
                        vm.cancelTransfer(driverId) { _, _ -> }
                    }
                ) {
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
private fun DriverDetailCard(
    stat: DriverStat,
    carryOver: DriverCarryOverSummary? = null,
    onTransferClick: (String) -> Unit = {},
    onCancelClick: (String) -> Unit = {},
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
        Column(Modifier.padding(12.dp)) {
            Text(stat.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("총 운행 횟수 :  ${stat.count} 회", color = Color.White)
            Text("총 운행료 : ${"%,d".format(stat.totalFare)}원", color = Color.White)
            Text("수수료 : ${"%,d".format(stat.deposit)}원", color = Color.White)
            Text("미수금 : ${"%,d".format(stat.totalCredit)}원", color = Color.White)
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("실 수령액 : ${"%,d".format(stat.realDeposit)}원", color = Color.Yellow, fontWeight = FontWeight.Bold)

            // 이월 미지급금 섹션 (있는 경우에만 표시)
            if (carryOver != null && carryOver.balance > 0) {
                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFFFFAA00), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "누적 미지급: ${"%,d".format(carryOver.balance)}원",
                            color = Color(0xFFFF6666),
                            fontWeight = FontWeight.Bold
                        )
                        if (carryOver.todayAmount > 0) {
                            Text(
                                "(오늘 +${"%,d".format(carryOver.todayAmount)}원)",
                                color = Color(0xFFFFAA00),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        when (carryOver.status) {
                            CarryOverStatus.PENDING -> {
                                Text("상태: 미지급", color = Color(0xFFFF6666), style = MaterialTheme.typography.bodySmall)
                            }
                            CarryOverStatus.TRANSFERRED -> {
                                val timeText = carryOver.transferredAt?.let {
                                    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(it.toDate())
                                } ?: ""
                                Text("상태: 이체됨 ($timeText)", color = Color(0xFFFFCC00), style = MaterialTheme.typography.bodySmall)
                            }
                            CarryOverStatus.SETTLED -> {
                                Text("상태: 수령완료", color = Color(0xFF66FF66), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // 이체하기 / 이체취소 버튼
                    when (carryOver.status) {
                        CarryOverStatus.PENDING -> {
                            Button(
                                onClick = { onTransferClick(carryOver.driverId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("이체하기", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        CarryOverStatus.TRANSFERRED -> {
                            Button(
                                onClick = { onCancelClick(carryOver.driverId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("이체취소", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        CarryOverStatus.SETTLED -> {
                            // 수령완료 상태에서는 버튼 없음
                        }
                    }
                }
            }
        }
    }
}
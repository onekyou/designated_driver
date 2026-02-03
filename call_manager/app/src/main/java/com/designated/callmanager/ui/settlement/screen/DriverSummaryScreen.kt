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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun DriverSummaryScreen(vm: SettlementViewModel = viewModel()) {
    val context = LocalContext.current
    val trips by vm.settlementList.collectAsState()
    val ratio by vm.officeShareRatio.collectAsState()

    // 이월 정산 (기사별 미지급금) 데이터
    val carryOverList by vm.carryOverList.collectAsState()

    // 기사별 미지급금 맵 (driverId → DriverCarryOverSummary)
    val carryOverMap = remember(carryOverList) {
        carryOverList.associateBy { it.driverId }
    }

    // 기사별 오늘 미지급금 계산 (로컬 trips 기반)
    val todayUnpaidByDriver = remember(trips, ratio) {
        trips.groupBy { it.driverId }
            .filter { it.key.isNotBlank() }
            .mapValues { (_, driverTrips) ->
                val fareSum = driverTrips.sumOf { it.fare }
                val cashReceived = driverTrips.sumOf { trip ->
                    when {
                        trip.paymentMethod == "현금" -> trip.fare
                        trip.paymentMethod.startsWith("현금+") -> trip.cashAmount ?: 0
                        else -> 0
                    }
                }
                val driverShare = (fareSum * (100 - ratio) / 100.0).toInt()
                val realDeposit = cashReceived - driverShare
                // 음수면 미지급금 발생
                if (realDeposit < 0) -realDeposit else 0
            }
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
                val driverId = list.first().driverId
                DriverStat(list.first().driverName.ifBlank { "미지정" }, list.size, fareSum, deposit, totalCredit, realDeposit, driverId)
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

    // 오늘 운행이 없지만 미지급금이 있는 기사 필터링
    val driversWithTrips = remember(trips) { trips.map { it.driverId }.toSet() }
    val carryOverOnlyDrivers = remember(carryOverList, driversWithTrips) {
        carryOverList.filter { it.driverId !in driversWithTrips && it.balance > 0 }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("기사별 통계", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))


        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(driverStats) { stat ->
                // 기사의 driverId
                val driverId = stat.driverId
                val carryOver = carryOverMap[driverId]
                val todayUnpaid = todayUnpaidByDriver[driverId] ?: 0

                DriverDetailCard(
                    stat = stat,
                    carryOver = carryOver,
                    todayUnpaid = todayUnpaid,
                    onTransferClick = { id ->
                        val carryOverBalance = carryOver?.balance ?: 0L
                        vm.transferCarryOver(
                            driverId = id,
                            driverName = stat.name,
                            carryOverBalance = carryOverBalance,
                            todayUnpaid = todayUnpaid.toLong()
                        ) { _, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCancelClick = { id ->
                        vm.cancelTransfer(id) { _, _ -> }
                    }
                ) {
                    val list = trips.filter { (it.driverName.ifBlank { "미지정" }) == stat.name }
                    selectedDriver = stat.name to list
                }
            }

            // 운행 없지만 미지급금이 있는 기사들 섹션
            if (carryOverOnlyDrivers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "미지급금 현황 (오늘 운행 없음)",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFFFAA00)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                items(carryOverOnlyDrivers) { carryOverItem ->
                    CarryOverOnlyCard(
                        carryOver = carryOverItem,
                        onTransferClick = {
                            vm.transferCarryOver(
                                driverId = carryOverItem.driverId,
                                driverName = carryOverItem.driverName,
                                carryOverBalance = carryOverItem.balance,
                                todayUnpaid = 0L  // 오늘 운행 없음
                            ) { _, _ -> }
                        },
                        onCancelClick = { vm.cancelTransfer(it) { _, _ -> } }
                    )
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
    carryOver: DriverCarryOverSummary? = null,
    todayUnpaid: Int = 0,
    onTransferClick: (String) -> Unit = {},
    onCancelClick: (String) -> Unit = {},
    onClick: () -> Unit
) {
    // 총 미지급금 계산
    val carryOverBalance = carryOver?.balance ?: 0L
    val status = carryOver?.status

    // TRANSFERRED 상태면 balance에 이미 오늘분 포함됨 → 더하지 않음
    val totalUnpaid = if (status == CarryOverStatus.TRANSFERRED) {
        carryOverBalance  // 이체된 금액만 표시
    } else {
        carryOverBalance + todayUnpaid  // 이월분 + 오늘분
    }

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

            // 미지급금 섹션 (항상 표시)
            Spacer(Modifier.height(8.dp))
            Divider(color = Color(0xFFFFAA00), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    // 총 미지급금
                    Text(
                        "미지급 합계: ${"%,d".format(totalUnpaid)}원",
                        color = if (totalUnpaid > 0) Color(0xFFFF6666) else Color.Gray,
                        fontWeight = FontWeight.Bold
                    )

                    // 이월/오늘 분리 표시
                    if (status == CarryOverStatus.TRANSFERRED) {
                        // TRANSFERRED: 저장된 값 사용 (이미 이체된 금액)
                        val transferredTodayAmount = carryOver?.todayAmount ?: 0L
                        val transferredPreviousAmount = carryOverBalance - transferredTodayAmount
                        if (transferredPreviousAmount > 0) {
                            Text(
                                "이월: ${"%,d".format(transferredPreviousAmount)}원",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (transferredTodayAmount > 0) {
                            Text(
                                "오늘: +${"%,d".format(transferredTodayAmount)}원",
                                color = Color(0xFFFFAA00),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        // PENDING: 로컬 계산값 사용
                        if (carryOverBalance > 0) {
                            Text(
                                "이월: ${"%,d".format(carryOverBalance)}원",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (todayUnpaid > 0) {
                            Text(
                                "오늘: +${"%,d".format(todayUnpaid)}원",
                                color = Color(0xFFFFAA00),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    // 상태
                    if (carryOver != null && carryOverBalance > 0) {
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
                    } else if (totalUnpaid == 0L) {
                        Text("미지급 없음", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }

                // 이체하기 / 이체취소 버튼 (미지급금이 있을 때)
                if (totalUnpaid > 0) {
                    val status = carryOver?.status ?: CarryOverStatus.PENDING
                    when (status) {
                        CarryOverStatus.PENDING -> {
                            Button(
                                onClick = { onTransferClick(stat.driverId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("이체하기", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        CarryOverStatus.TRANSFERRED -> {
                            Button(
                                onClick = { onCancelClick(stat.driverId) },
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

@Composable
private fun CarryOverOnlyCard(
    carryOver: DriverCarryOverSummary,
    onTransferClick: (String) -> Unit,
    onCancelClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2020))
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                carryOver.driverName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
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
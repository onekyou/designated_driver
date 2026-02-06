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
import com.designated.callmanager.data.settlement.DailySettlementStatus
import com.designated.callmanager.data.settlement.DriverCarryOverSummary
import com.designated.callmanager.data.settlement.DriverDailySettlementSummary
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

    // 일일 정산 (기사별 업무마감) 데이터
    val dailySettlementList by vm.dailySettlementList.collectAsState()

    // 기사별 미지급금 맵 (driverId → DriverCarryOverSummary)
    val carryOverMap = remember(carryOverList) {
        carryOverList.associateBy { it.driverId }
    }

    // 기사별 일일 정산 맵 (driverId → DriverDailySettlementSummary)
    val dailySettlementMap = remember(dailySettlementList) {
        dailySettlementList.associateBy { it.driverId }
    }

    // 기사별 오늘 미지급금 계산 (로컬 trips 기반) - 기사앱과 동일한 로직
    // rawFinalDeposit = deposit - totalCredit (사무실몫 - 외상)
    val todayUnpaidByDriver = remember(trips, ratio) {
        trips.groupBy { it.driverId }
            .filter { it.key.isNotBlank() }
            .mapValues { (_, driverTrips) ->
                val fareSum = driverTrips.sumOf { it.fare }
                val totalCredit = driverTrips.sumOf { trip ->
                    when {
                        trip.paymentMethod == "현금" -> 0
                        trip.paymentMethod == "현금+포인트" -> {
                            val cash = trip.cashAmount ?: 0
                            if (cash > 0) trip.fare - cash else trip.fare
                        }
                        else -> trip.fare // 이체, 외상은 전액 외상
                    }
                }
                val deposit = (fareSum * ratio / 100.0).toInt()
                val rawFinalDeposit = deposit - totalCredit
                // 음수면 미지급금 발생 (사무실이 기사에게 줘야 할 돈)
                if (rawFinalDeposit < 0) -rawFinalDeposit else 0
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
                val dailySettlement = dailySettlementMap[driverId]

                DriverDetailCard(
                    stat = stat,
                    carryOver = carryOver,
                    todayUnpaid = todayUnpaid,
                    dailySettlement = dailySettlement,
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
                    },
                    onConfirmSettlement = { id, diff ->
                        vm.confirmDailySettlement(id, diff) { success, msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
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
    dailySettlement: DriverDailySettlementSummary? = null,
    onTransferClick: (String) -> Unit = {},
    onCancelClick: (String) -> Unit = {},
    onConfirmSettlement: (String, Long) -> Unit = { _, _ -> },
    onClick: () -> Unit
) {
    // 미지급금 계산 - 기사앱과 동일한 통합 로직 적용
    val carryOverBalance = carryOver?.balance ?: 0L
    val status = carryOver?.status

    // rawFinalDeposit = 오늘 운행으로 납입해야 할 금액 (사무실몫 - 미수금)
    // stat.realDeposit = deposit - totalCredit (기사앱의 officeDeposit - totalCredit과 동일)
    val rawFinalDeposit = stat.realDeposit.toLong()

    // 오늘 발생 미지급금 직접 계산 (기사앱과 동일한 로직)
    // rawFinalDeposit < 0 이면 사무실이 기사에게 줘야 할 돈
    val calculatedTodayUnpaid = if (rawFinalDeposit < 0) -rawFinalDeposit else 0L

    // 미지급금에서 공제된 금액 (양수 납입액이 있을 때만 공제)
    val usedFromCarryOver = if (status == CarryOverStatus.PENDING && rawFinalDeposit > 0 && carryOverBalance > 0) {
        minOf(carryOverBalance, rawFinalDeposit)
    } else {
        0L
    }

    // 상태별 남은 미지급금 계산
    val remainingCarryOver = when (status) {
        CarryOverStatus.SETTLED -> 0L  // 수령 완료 → 0
        CarryOverStatus.TRANSFERRED -> carryOverBalance  // 이체됨 → 저장된 balance 사용
        else -> {
            // PENDING: 통합 계산 로직 적용 (기사앱과 동일)
            if (rawFinalDeposit > 0) {
                // 납입액이 있으면 carryOver에서 공제
                maxOf(0L, carryOverBalance - rawFinalDeposit)
            } else {
                // 납입액이 없거나 음수면 이월분 + 오늘 발생분
                carryOverBalance + calculatedTodayUnpaid
            }
        }
    }

    // totalUnpaid를 remainingCarryOver로 대체
    val totalUnpaid = remainingCarryOver

    // 업무마감 정보
    val hasSubmitted = dailySettlement?.hasSubmitted == true
    val isConfirmed = dailySettlement?.isConfirmed == true
    val settlement = dailySettlement?.dailySettlement

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stat.name, color = Color.White, style = MaterialTheme.typography.titleMedium)
                // 업무마감 상태 표시
                if (hasSubmitted) {
                    Text(
                        "🔔 마감대기",
                        color = Color(0xFFFF9800),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                } else if (isConfirmed) {
                    Text(
                        "✓ 확인완료",
                        color = Color(0xFF66FF66),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("총 운행 횟수 :  ${stat.count} 회", color = Color.White)
            Text("총 운행료 : ${"%,d".format(stat.totalFare)}원", color = Color.White)
            Text("수수료 : ${"%,d".format(stat.deposit)}원", color = Color.White)
            Text("미수금 : ${"%,d".format(stat.totalCredit)}원", color = Color.White)
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            // 업무마감 후(마감대기 또는 확인완료) 실제 납입 금액, 마감 전에는 납입해야 할 금액
            val displayRealDeposit = if ((hasSubmitted || isConfirmed) && settlement != null) {
                settlement.realDeposit.toInt()  // 기사가 실제 납입한 금액
            } else {
                stat.realDeposit  // 납입해야 할 금액 (예상)
            }
            Text(
                "납입금 : ${"%,d".format(displayRealDeposit)}원",
                color = Color.Yellow,
                fontWeight = FontWeight.Bold
            )

            // 업무마감 섹션 (마감 대기 상태인 경우)
            if (hasSubmitted && settlement != null) {
                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFFFF9800), thickness = 2.dp)
                Spacer(Modifier.height(8.dp))

                Text(
                    "📋 업무마감 보고",
                    color = Color(0xFFFF9800),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("최종 납입액: ${"%,d".format(settlement.finalDeposit)}원", color = Color.White, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "실납입: ${"%,d".format(settlement.realDeposit)}원",
                            color = Color(0xFF00BFFF),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val diff = settlement.settlementDiff
                        val diffColor = when {
                            diff > 0 -> Color(0xFF66FF66)  // 환급금 (기사가 받아야 함)
                            diff < 0 -> Color(0xFFFF6666)  // 미납금 (기사가 내야 함)
                            else -> Color.Gray
                        }
                        val diffText = when {
                            diff > 0 -> "환급금: +${"%,d".format(diff)}원"
                            diff < 0 -> "미납금: ${"%,d".format(diff)}원"
                            else -> "정산완료"
                        }
                        Text(diffText, color = diffColor, fontWeight = FontWeight.Bold)

                        // 마감 시간
                        settlement.submittedAt?.let {
                            val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate())
                            Text("마감시간: $timeText", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // 확인 버튼
                Button(
                    onClick = { onConfirmSettlement(stat.driverId, settlement.settlementDiff) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("✓ 정산 확인", fontWeight = FontWeight.Bold)
                }
            }

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

                    // 이월/공제/오늘 분리 표시 - 기사앱과 동일한 표시 로직
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
                        // PENDING: 통합 계산 로직 적용
                        if (carryOverBalance > 0) {
                            Text(
                                "이월: ${"%,d".format(carryOverBalance)}원",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // 오늘 공제 표시 (양수 납입액으로 미지급금이 줄어든 경우)
                        if (usedFromCarryOver > 0) {
                            Text(
                                "오늘 공제: -${"%,d".format(usedFromCarryOver)}원",
                                color = Color(0xFF66FF66),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        // 오늘 발생 미지급금 (음수 납입액으로 추가 발생한 경우)
                        if (calculatedTodayUnpaid > 0 && rawFinalDeposit <= 0) {
                            Text(
                                "오늘 발생: +${"%,d".format(calculatedTodayUnpaid)}원",
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

                    // 예상 납입금 (미환급금 공제 후 실제 납입할 금액) - 기사앱 adjustedDeposit과 동일
                    // 확인 완료 후에는 "실납입금"으로 표시
                    val expectedDeposit = maxOf(0L, rawFinalDeposit - usedFromCarryOver)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${if (isConfirmed) "실납입금" else "예상 납입금"}: ${"%,d".format(expectedDeposit)}원",
                        color = if (isConfirmed) Color(0xFF4CAF50) else Color(0xFF00BFFF),
                        fontWeight = FontWeight.Bold
                    )
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
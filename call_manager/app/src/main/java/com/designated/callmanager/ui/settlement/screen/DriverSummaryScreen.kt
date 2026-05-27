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
import androidx.compose.ui.platform.testTag
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
import com.designated.callmanager.ui.settlement.SettlementCalculator
import java.text.SimpleDateFormat
import java.util.Locale
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun DriverSummaryScreen(vm: SettlementViewModel = viewModel()) {
    val context = LocalContext.current
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

    // 기사별 오늘 미지급금 계산 (마감 이후 콜만) - 기사앱과 동일한 로직
    val todayUnpaidByDriver = remember(filteredTrips, ratio) {
        SettlementCalculator.calculateTodayUnpaidByDriver(filteredTrips, ratio)
    }

    val driverStats = remember(filteredTrips, ratio) {
        SettlementCalculator.calculateDriverStats(filteredTrips, ratio)
    }

    val totalFare = filteredTrips.sumOf { it.fare }
    val totalCredit = SettlementCalculator.calculateTotalCredit(filteredTrips)
    val totalDeposit = SettlementCalculator.calculateOfficeDeposit(totalFare, ratio)
    val realDepositAll = totalDeposit - totalCredit

    var selectedDriver by remember { mutableStateOf<Pair<String,List<SettlementData>>?>(null) }

    // 오늘 운행이 없지만 미지급금이 있는 기사 필터링
    val driversWithTrips = remember(filteredTrips) { filteredTrips.map { it.driverId }.toSet() }
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
                    dailySettlement = dailySettlement
                ) {
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
    carryOver: DriverCarryOverSummary? = null,
    todayUnpaid: Int = 0,
    dailySettlement: DriverDailySettlementSummary? = null,
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
    // confirm 또는 이체 이후에는 balance에 오늘분이 이미 반영된 확정값이므로 todayUnpaid 재합산 금지
    val isConfirmedOrLater = dailySettlement?.isConfirmed == true
    val calcCarryOver = dailySettlement?.dailySettlement?.calculatedCarryOver ?: carryOverBalance
    val remainingCarryOver = when {
        status == CarryOverStatus.SETTLED -> 0L
        status == CarryOverStatus.TRANSFERRED -> carryOverBalance
        isConfirmedOrLater -> carryOverBalance  // CONFIRMED 후 PENDING 분기: balance 신뢰
        dailySettlement?.hasSubmitted == true -> calcCarryOver  // PENDING_CONFIRM: 기사앱 계산값 표시
        else -> {
            // 마감 전: 이월분 + 오늘 발생분
            if (rawFinalDeposit > 0) {
                maxOf(0L, carryOverBalance - rawFinalDeposit)
            } else {
                carryOverBalance + calculatedTodayUnpaid
            }
        }
    }

    // totalUnpaid를 remainingCarryOver로 대체
    val totalUnpaid = remainingCarryOver

    // 업무마감 정보
    val hasSubmitted = dailySettlement?.hasSubmitted == true
    val isConfirmed = dailySettlement?.isConfirmed == true
    val isRejected = dailySettlement?.isRejected == true
    val settlement = dailySettlement?.dailySettlement

    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stat.name, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("settlement_driver_name_${stat.driverId}"))
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
                } else if (isRejected) {
                    Text(
                        "✗ 거절됨",
                        color = Color(0xFFFF6666),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
            Text("총 운행 횟수 :  ${stat.count} 회", color = Color.White, modifier = Modifier.testTag("settlement_driver_count_${stat.driverId}"))
            Text("총 운행료 : ${"%,d".format(stat.totalFare)}원", color = Color.White, modifier = Modifier.testTag("settlement_driver_totalFare_${stat.driverId}"))
            Text("수수료 : ${"%,d".format(stat.deposit)}원", color = Color.White, modifier = Modifier.testTag("settlement_driver_deposit_${stat.driverId}"))
            Text("미수금 : ${"%,d".format(stat.totalCredit)}원", color = Color.White, modifier = Modifier.testTag("settlement_driver_totalCredit_${stat.driverId}"))
            Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

            // 업무마감 섹션 (마감 대기 또는 확인완료 상태인 경우)
            if ((hasSubmitted || isConfirmed) && settlement != null) {
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

                // 통합 정산인 경우 1차/추가 분리 표시
                if (settlement.originalTripCount > 0) {
                    val addedTripCount = settlement.tripCount - settlement.originalTripCount
                    val addedTotalFare = settlement.totalFare - settlement.originalTotalFare
                    val addedRealDeposit = settlement.realDeposit - settlement.originalRealDeposit

                    Text("▸ 1차 마감", color = Color(0xFF88CCFF), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("  운행 ${settlement.originalTripCount}건 / ${"%,d".format(settlement.originalTotalFare)}원  |  납입: ${"%,d".format(settlement.originalRealDeposit)}원",
                        color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))

                    Text("▸ 추가 운행", color = Color(0xFFFFCC00), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                    Text("  운행 ${addedTripCount}건 / ${"%,d".format(addedTotalFare)}원  |  납입: ${"%,d".format(addedRealDeposit)}원",
                        color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))

                    Text("▸ 합계", color = Color(0xFFFF9800), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                }

                // 세로 배치: 최종 납입액 → 실납입 → 환급금/미납금
                Text("최종 납입액: ${"%,d".format(settlement.finalDeposit)}원", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text(
                    "실납입: ${"%,d".format(settlement.realDeposit)}원",
                    color = Color(0xFF00BFFF),
                    fontWeight = FontWeight.Bold
                )
                val diff = settlement.settlementDiff
                val diffColor = when {
                    diff > 0 -> Color(0xFF66FF66)
                    diff < 0 -> Color(0xFFFF6666)
                    else -> Color.Gray
                }
                val origCarryOver = settlement.originalCarryOver
                val diffText = when {
                    diff > 0 && origCarryOver > 0 -> "환급금: +${"%,d".format(diff)}원 (이월 ${"%,d".format(origCarryOver)}원)"
                    diff > 0 -> "환급금: +${"%,d".format(diff)}원"
                    diff < 0 && origCarryOver < 0 -> "미납금: ${"%,d".format(diff)}원 (미수 ${"%,d".format(-origCarryOver)}원)"
                    diff < 0 -> "미납금: ${"%,d".format(diff)}원"
                    else -> "정산완료"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(diffText, color = diffColor, fontWeight = FontWeight.Bold)
                    settlement.submittedAt?.let {
                        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.toDate())
                        Text("마감시간: $timeText", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }

            }

            // 미지급금 섹션 - 상태별 분기
            // 마감대기: 숨김 (업무마감 보고에 정보 있음)
            // 마감 전 / 확인완료 / 거절됨: 표시
            if (!hasSubmitted || isConfirmed || isRejected) {
                Spacer(Modifier.height(8.dp))
                Divider(color = Color(0xFFFFAA00), thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        if (isConfirmed) {
                            // 확인완료: carryOver 잔액만 표시 (소급계산 없음)
                            Text(
                                "미지급 합계: ${"%,d".format(carryOverBalance)}원",
                                color = if (carryOverBalance > 0) Color(0xFFFF6666) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("settlement_driver_carryOver_${stat.driverId}")
                            )
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
                            } else if (carryOverBalance == 0L) {
                                Text("미지급 없음", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            // 마감 전: carryOver 그대로 표시 (소급공제 없음)
                            Text(
                                "미지급 합계: ${"%,d".format(carryOverBalance)}원",
                                color = if (carryOverBalance > 0) Color(0xFFFF6666) else Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("settlement_driver_carryOver_${stat.driverId}")
                            )

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
                            } else if (carryOverBalance == 0L) {
                                Text("미지급 없음", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }

                            // 예상 납입금 = 수수료 - 미지급 - 미수금
                            val expectedDeposit = stat.deposit - carryOverBalance.toInt() - stat.totalCredit
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "예상 납입금: ${"%,d".format(expectedDeposit)}원",
                                color = Color(0xFF00BFFF),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

            }
            }
        }
    }
}


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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementViewModel
import com.designated.callmanager.ui.settlement.SettlementCalculator
import com.designated.callmanager.ui.settlement.screen.CreditDialog
import com.designated.callmanager.data.settlement.DriverCarryOverSummary
import com.designated.callmanager.data.settlement.CarryOverStatus
import android.app.Activity
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth

@Composable
fun AllTripsScreen(vm: SettlementViewModel = viewModel(), onHome: (() -> Unit)? = null) {
    val trips by vm.settlementList.collectAsState()
    val directRunTrips by vm.directRunTrips.collectAsState()
    val creditedIds by vm.creditedTripIds.collectAsState()
    var showCreditDialog by remember { mutableStateOf(false) }

    var showRatioDialog by remember { mutableStateOf(false) }
    val ratio by vm.officeShareRatio.collectAsState()

    var paymentDialog by remember { mutableStateOf<Pair<String, List<SettlementData>>?>(null) }

    // 이월 정산 (기사별 미지급금) 데이터
    val carryOverList by vm.carryOverList.collectAsState()

    // 일일 정산 (기사별 업무마감) 데이터
    val dailySettlementList by vm.dailySettlementList.collectAsState()

    // 기사별 오늘 미지급금 계산 (로컬 trips 기반) - 기사앱과 동일한 로직
    val todayUnpaidByDriver = remember(trips, ratio) {
        SettlementCalculator.calculateTodayUnpaidByDriver(trips, ratio)
    }

    // 기사별 통합 미지급 현황 (이월분 + 오늘분)
    data class DriverUnpaidSummary(
        val driverId: String,
        val driverName: String,
        val carryOver: Long,      // 이월분 (Firestore)
        val todayUnpaid: Int,     // 오늘분 (로컬 계산)
        val total: Long,          // 합계
        val status: CarryOverStatus
    )

    val driverUnpaidList = remember(carryOverList, todayUnpaidByDriver, trips, dailySettlementList) {
        // 이월분이 있는 기사
        val fromCarryOver = carryOverList.map { co ->
            val todayAmount = todayUnpaidByDriver[co.driverId] ?: 0
            val ds = dailySettlementList.find { it.driverId == co.driverId }
            // balance가 확정값(오늘 포함)인 경우 todayAmount 재합산 금지:
            // - TRANSFERRED/SETTLED: 매니저 이체/기사 수령 후 확정
            // - dailySettlement.isConfirmed: 매니저 정산확인으로 calculatedCarryOver 저장됨
            val managerFinalized = co.status == CarryOverStatus.TRANSFERRED ||
                    co.status == CarryOverStatus.SETTLED ||
                    ds?.isConfirmed == true
            val displayTodayUnpaid = if (managerFinalized) 0 else todayAmount
            val displayTotal = if (managerFinalized) co.balance else co.balance + todayAmount
            DriverUnpaidSummary(
                driverId = co.driverId,
                driverName = co.driverName,
                carryOver = co.balance,
                todayUnpaid = displayTodayUnpaid,
                total = displayTotal,
                status = co.status
            )
        }

        // 오늘 새로 미지급 발생한 기사 (이월분 없는)
        val carryOverDriverIds = carryOverList.map { it.driverId }.toSet()
        val fromToday = todayUnpaidByDriver
            .filter { it.key !in carryOverDriverIds && it.value > 0 }
            .map { (driverId, todayAmount) ->
                val driverName = trips.find { it.driverId == driverId }?.driverName ?: "미지정"
                DriverUnpaidSummary(
                    driverId = driverId,
                    driverName = driverName,
                    carryOver = 0L,
                    todayUnpaid = todayAmount,
                    total = todayAmount.toLong(),
                    status = CarryOverStatus.PENDING
                )
            }

        (fromCarryOver + fromToday).sortedByDescending { it.total }
    }

    // 업무 마감 확인 다이얼로그 상태
    var showFinalizeDialog by remember { mutableStateOf(false) }
    var isFinalizingInProgress by remember { mutableStateOf(false) }
    var finalizeResultMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // 업무 마감 전 체크 경고 다이얼로그
    var showPreFinalizeWarning by remember { mutableStateOf(false) }
    var preFinalizeWarnings by remember { mutableStateOf<List<String>>(emptyList()) }

    // 업무 마감 전 체크 함수
    fun checkBeforeFinalize(): List<String> {
        val warnings = mutableListOf<String>()

        // 1. 마감 대기 중인 기사 확인 (정산확인 안 된 기사)
        val pendingConfirmDrivers = dailySettlementList.filter { it.hasSubmitted && !it.isConfirmed }
        if (pendingConfirmDrivers.isNotEmpty()) {
            val names = pendingConfirmDrivers.take(3).map { it.driverName }
            val suffix = if (pendingConfirmDrivers.size > 3) " 외 ${pendingConfirmDrivers.size - 3}명" else ""
            warnings.add("🔔 정산확인 대기: ${names.joinToString(", ")}$suffix")
        }

        // 2. 대기탭의 이체/외상 콜 미처리 확인 (creditedIds에 없는 이체/외상 콜)
        val pendingPaymentTrips = trips.filter {
            it.paymentMethod in listOf("이체", "외상") && !creditedIds.contains(it.callId)
        }
        if (pendingPaymentTrips.isNotEmpty()) {
            val transferCount = pendingPaymentTrips.count { it.paymentMethod == "이체" }
            val creditCount = pendingPaymentTrips.count { it.paymentMethod == "외상" }
            val details = mutableListOf<String>()
            if (transferCount > 0) details.add("이체 ${transferCount}건")
            if (creditCount > 0) details.add("외상 ${creditCount}건")
            warnings.add("💳 정산 미처리: ${details.joinToString(", ")}")
        }

        // 3. 미지급금 이체 대기 확인 (PENDING 상태)
        val pendingTransferDrivers = carryOverList.filter {
            it.balance > 0 && it.status == CarryOverStatus.PENDING
        }
        if (pendingTransferDrivers.isNotEmpty()) {
            val names = pendingTransferDrivers.take(3).map { it.driverName }
            val suffix = if (pendingTransferDrivers.size > 3) " 외 ${pendingTransferDrivers.size - 3}명" else ""
            warnings.add("💰 미지급금 이체 대기: ${names.joinToString(", ")}$suffix")
        }

        return warnings
    }


    Column(Modifier.fillMaxSize().padding(vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val totalCount = trips.size + directRunTrips.size
            Text("전체 운행 ${totalCount}건", style = MaterialTheme.typography.titleMedium, color = Color.White)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("수수료 비율 조정", color=Color.White, style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { showRatioDialog = true }) {
                    Icon(Icons.Filled.Settings, contentDescription = "비율 설정", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // 세부 내역(기사 납입/이체/미수금/포인트)은 실기사 기준만 사용
        // 직접운행은 사무실 100% 귀속이라 "기사 납입" 개념이 없음 → 별도 한 줄로 표시
        val breakdown = SettlementCalculator.calculatePaymentBreakdown(trips)
        val mgrBreakdown = SettlementCalculator.calculatePaymentBreakdown(directRunTrips)
        val totalFare = breakdown.totalFare + mgrBreakdown.totalFare  // 상단 총매출용
        val cashSum = breakdown.cashSum
        val bankSum = breakdown.bankSum
        val creditSum = breakdown.creditSum
        val pointSum = breakdown.pointSum
        val mgrNetRevenue = mgrBreakdown.totalFare - mgrBreakdown.pointSum  // 직접운행 순매출 (포인트 차감)

        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF424242))) {
            Column(Modifier.padding(16.dp)) {
                // 총매출과 총수입 (실기사: ratio 적용, 직접운행: 100% 사무실 귀속)
                val driverOfficeIncome = SettlementCalculator.calculateOfficeDeposit(breakdown.totalFare, ratio)
                val totalOfficeIncome = driverOfficeIncome + mgrBreakdown.totalFare
                Text("총매출 ${"%,d".format(totalFare)}원", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("settlement_all_totalFare"))
                Text("총수입 ${"%,d".format(totalOfficeIncome)}원", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("settlement_all_officeIncome"))

                HorizontalDivider(color = Color.Gray, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))

                // 수입 내역
                Text("수입내역", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))

                // 사무실 실수입 계산
                val totalDeposit = totalOfficeIncome
                val driverShare = totalFare - totalDeposit
                val finalDriverDeposit = SettlementCalculator.calculateDriverDeposit(cashSum, driverShare)

                // 기사 납입금 (마이너스도 표시)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("기사 납입", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text("${"%,d".format(finalDriverDeposit)}원",
                        color = if (finalDriverDeposit < 0) Color.Red else Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // 이체
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("이체", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text("${"%,d".format(bankSum)}원",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // 미수금
                Row(modifier = Modifier.fillMaxWidth().testTag("settlement_all_creditSum"), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("미수금", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text("${"%,d".format(creditSum)}원",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // 포인트 차감 (항상 표시)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("포인트 차감", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    Text(if (pointSum > 0) "-${"%,d".format(pointSum)}원" else "0원",
                        color = if (pointSum > 0) Color.Red else Color.White,
                        style = MaterialTheme.typography.bodyMedium)
                }

                // 직접운행 매출 (관리자가 직접 운행한 콜 전체매출, 포인트 차감 후)
                if (directRunTrips.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("직접운행 매출", color = Color(0xFFFFB000), style = MaterialTheme.typography.bodyMedium)
                        Text("${"%,d".format(mgrNetRevenue)}원", color = Color(0xFFFFB000), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 실수입 계산 (실기사 실수입 + 직접운행 순매출)
                val realIncome = SettlementCalculator.calculateRealIncome(finalDriverDeposit, bankSum, creditSum, pointSum) + mgrNetRevenue

                HorizontalDivider(color = Color.Gray, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                Row(modifier = Modifier.fillMaxWidth().testTag("settlement_all_realIncome"), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("실수입", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${"%,d".format(realIncome)}원",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold)
                }



            }
        }

        Spacer(Modifier.height(12.dp))

        // 기사별 미지급금 테이블 (타이틀 클릭 시 펼침)
        var unpaidExpanded by remember { mutableStateOf(false) }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A))
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { unpaidExpanded = !unpaidExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "기사별 미지급 현황",
                            color = Color(0xFFFFAA00),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (unpaidExpanded) "▾" else "▸",
                            color = Color(0xFFFFAA00),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Text(
                        "총 ${"%,d".format(driverUnpaidList.sumOf { it.total })}원",
                        color = if (driverUnpaidList.sumOf { it.total } > 0) Color(0xFFFF6666) else Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (unpaidExpanded) {
                    Spacer(Modifier.height(8.dp))

                if (driverUnpaidList.isEmpty()) {
                    Text(
                        "미지급금 없음",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    // 테이블 헤더
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("기사", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("이월", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("오늘", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("합계", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                    // 기사별 행 (최대 5명까지만 표시)
                    driverUnpaidList.take(5).forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                item.driverName.take(4),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (item.carryOver > 0) "${"%,d".format(item.carryOver)}" else "-",
                                color = if (item.carryOver > 0) Color.White else Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (item.todayUnpaid > 0) "+${"%,d".format(item.todayUnpaid)}" else "-",
                                color = if (item.todayUnpaid > 0) Color(0xFFFFAA00) else Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${"%,d".format(item.total)}",
                                color = Color(0xFFFF6666),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    if (driverUnpaidList.size > 5) {
                        Text(
                            "외 ${driverUnpaidList.size - 5}명 더보기 → 기사별 탭",
                            color = Color.Gray,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                } // if (unpaidExpanded)
            }
        }
        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f)) {
            TripListTable(tripList = trips)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                // 업무 마감 전 체크
                val warnings = checkBeforeFinalize()
                if (warnings.isNotEmpty()) {
                    preFinalizeWarnings = warnings
                    showPreFinalizeWarning = true
                } else {
                    showFinalizeDialog = true
                }
            },
            enabled = trips.isNotEmpty() || directRunTrips.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
        ) { Text("업무 마감", color = Color.White) }
    }

    // 업무 마감 전 경고 다이얼로그
    if (showPreFinalizeWarning) {
        AlertDialog(
            onDismissRequest = { showPreFinalizeWarning = false },
            title = { Text("⚠️ 확인 필요", color = Color(0xFFFFAA00)) },
            text = {
                Column {
                    Text(
                        "아래 항목을 확인해주세요:",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    preFinalizeWarnings.forEach { warning ->
                        Text(
                            "• $warning",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "그래도 업무를 마감하시겠습니까?",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPreFinalizeWarning = false
                        showFinalizeDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                ) {
                    Text("강제 마감")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPreFinalizeWarning = false }) {
                    Text("취소", color = Color.White)
                }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    // 업무 마감 확인 다이얼로그
    if (showFinalizeDialog) {
        AlertDialog(
            onDismissRequest = { if (!isFinalizingInProgress) showFinalizeDialog = false },
            title = { Text("업무 마감", color = Color.White) },
            text = {
                if (isFinalizingInProgress) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                        Text("마감 처리 중...", color = Color.White)
                    }
                } else if (finalizeResultMessage != null) {
                    Text(finalizeResultMessage!!, color = Color.White)
                } else {
                    Text("업무를 마감하시겠습니까?", color = Color.White)
                }
            },
            confirmButton = {
                if (!isFinalizingInProgress && finalizeResultMessage == null) {
                    Button(
                        onClick = {
                            isFinalizingInProgress = true
                            vm.finalizeSettlementSession { success, message ->
                                isFinalizingInProgress = false
                                if (success) {
                                    vm.clearAllTrips()
                                    finalizeResultMessage = message
                                } else {
                                    finalizeResultMessage = "마감 실패: $message"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444))
                    ) {
                        Text("확인")
                    }
                } else if (!isFinalizingInProgress && finalizeResultMessage != null) {
                    TextButton(onClick = {
                        showFinalizeDialog = false
                        onHome?.invoke()
                    }) {
                        Text("닫기", color = Color.White)
                    }
                }
            },
            dismissButton = {
                if (!isFinalizingInProgress && finalizeResultMessage == null) {
                    TextButton(onClick = { showFinalizeDialog = false }) {
                        Text("취소", color = Color.White)
                    }
                }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }



    paymentDialog?.let { pair ->
        val label = pair.first
        val listData = pair.second
        AlertDialog(
            onDismissRequest = { paymentDialog = null },
            title = { Text("$label 결제 내역 (${listData.size}건)", color = Color.White) },
            text = {
                Column(Modifier.heightIn(max=400.dp)) {
                    listData.forEach { t ->
                        Text("${t.customerName.take(4)}  ${t.departure}→${t.destination}  ${"%,d".format(t.fare)}원", color=Color.White)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { paymentDialog = null }) { Text("닫기") } },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    if (showRatioDialog) {
        var inputValue by remember { mutableStateOf(ratio.toString()) }
        var isError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showRatioDialog = false },
            title = { Text("수수료 비율 설정") },
            text = {
                Column {
                    Text(
                        text = "사무실 수수료 비율을 입력하세요 (10-90%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = inputValue,
                        onValueChange = { newValue ->
                            // 숫자만 허용
                            val filteredValue = newValue.filter { it.isDigit() }.take(2)

                            inputValue = filteredValue

                            // 유효성 검사
                            if (filteredValue.isNotEmpty()) {
                                val num = filteredValue.toIntOrNull()
                                isError = num == null || num !in 10..90
                            } else {
                                isError = true
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
                        val numericValue = inputValue.toIntOrNull()
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


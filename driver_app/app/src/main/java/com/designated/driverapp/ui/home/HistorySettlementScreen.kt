package com.designated.driverapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.designated.driverapp.model.CallInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.designated.driverapp.viewmodel.DriverViewModel
import com.designated.driverapp.data.settlement.CarryOverStatus
import com.designated.driverapp.data.settlement.DriverCarryOver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import android.app.Activity
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import com.designated.driverapp.ui.home.logoutUserAndExitApp
import androidx.compose.runtime.rememberCoroutineScope
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import androidx.compose.runtime.SideEffect
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

@Composable
fun HistorySettlementScreen(
    navController: NavController,
    viewModel: DriverViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val completedCalls = uiState.completedCalls
    val shouldNavigateToHistorySettlement = uiState.navigateToHistorySettlement
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // 이월 정산 (미수령금) 데이터
    val carryOver by viewModel.carryOver.collectAsStateWithLifecycle()
    var showReceiveConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val systemUiController = rememberSystemUiController()

    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color(0xFF222222),
            darkIcons = false
        )
    }
    val sortedCalls = completedCalls.sortedByDescending { it.timestamp }

    // ✅ Firestore 기반 정산 데이터 (calls에서 계산)
    val depositRatio by viewModel.depositRatio.collectAsStateWithLifecycle()
    val todaySettlement by viewModel.todaySettlement.collectAsStateWithLifecycle()
    val lastClearedMillis by viewModel.lastClearedMillis.collectAsStateWithLifecycle()

    // ✅ 운행내역 - ViewModel에서 직접 가져옴 (Firestore 기반, 정산과 동일 데이터 소스)
    val tripHistoryList by viewModel.tripHistoryList.collectAsStateWithLifecycle()
    // 표시용 문자열 변환
    val tripHistory = tripHistoryList.map { it.toDisplayString() }

    // 정산 값 (Firestore calls 기반)
    val totalCount = todaySettlement.tripCount
    val totalFare = todaySettlement.totalFare
    val realIncome = todaySettlement.driverShare      // 내 수익 (기사몫)
    val realDeposit = todaySettlement.realDeposit     // 실 납부액
    val totalCredit = todaySettlement.totalCredit     // 총 외상
    val officeDeposit = todaySettlement.officeDeposit // 총 납입액 (사무실 몫)

    LaunchedEffect(shouldNavigateToHistorySettlement) {
        if (shouldNavigateToHistorySettlement) {
            viewModel.onNavigateToHistorySettlementHandled()
        }
    }

    // 시스템 뒤로가기 버튼 처리
    BackHandler(enabled = true) {
        onNavigateBack()
    }

    // 비율 정보 다이얼로그 (읽기 전용 - 비율은 사무실에서 설정)
    var showRatioDialog by remember { mutableStateOf(false) }
    if (showRatioDialog) {
        AlertDialog(
            onDismissRequest = { showRatioDialog = false },
            title = { Text("납부 비율 정보", color = Color.White) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "현재 납부 비율: ${depositRatio}%",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "내 수익: ${100 - depositRatio}%",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFFFB000)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "※ 비율 변경은 사무실에서만 가능합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showRatioDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000))
                ) { Text("확인", color = Color.Black) }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    data class SessionData(
        val date: String,
        val history: List<String>,
        val summary: Map<String, Any>
    )
    fun loadSessions(context: Context): MutableList<SessionData> {
        val prefs = context.getSharedPreferences("trip_sessions", Context.MODE_PRIVATE)
        val sessionsJson = prefs.getString("sessions", "[]")
        val arr = JSONArray(sessionsJson)
        val list = mutableListOf<SessionData>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val date = obj.optString("date", "")
            val historyArr = obj.optJSONArray("history") ?: JSONArray()
            val history = List(historyArr.length()) { j -> historyArr.getString(j) }
            val summaryObj = obj.optJSONObject("summary") ?: org.json.JSONObject()
            val summary = mutableMapOf<String, Any>()
            summaryObj.keys().forEach { k -> summary[k] = summaryObj.get(k) }
            list.add(SessionData(date, history, summary))
        }
        return list
    }
    fun saveSessions(context: Context, sessions: List<SessionData>) {
        val prefs = context.getSharedPreferences("trip_sessions", Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (s in sessions) {
            val obj = org.json.JSONObject()
            obj.put("date", s.date)
            obj.put("history", JSONArray(s.history))
            obj.put("summary", org.json.JSONObject(s.summary))
            arr.put(obj)
        }
        prefs.edit().putString("sessions", arr.toString()).apply()
    }
    var sessionList by remember { mutableStateOf(loadSessions(context)) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showSessionDetail by remember { mutableStateOf(false) }
    var selectedSession: SessionData? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.Black,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "운행 내역",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }

                    IconButton(
                        onClick = { showHistoryDialog = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "설정",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showHistoryDialog = false },
                    title = {
                        Text(
                            "이전 운행/정산 기록",
                            color = Color.White
                        )
                    },
                    text = {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "저장된 기록",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                if (sessionList.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "이전 기록이 없습니다.",
                                            color = Color.Gray
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        contentPadding = PaddingValues(bottom = 16.dp)
                                    ) {
                                        items(sessionList) { session ->
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedSession = session
                                                        showSessionDetail = true
                                                    },
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
                                                shape = RoundedCornerShape(0.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = "기록일: ${session.date}",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.White
                                                            )
                                                            Text(
                                                                text = "운행내역: ${session.history.size}건",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color.LightGray
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { showHistoryDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB000),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("닫기")
                        }
                    },
                    containerColor = Color(0xFF1A1A1A)
                )
            }
            if (showSessionDetail && selectedSession != null) {
                val s = selectedSession!!
                AlertDialog(
                    onDismissRequest = { showSessionDetail = false },
                    title = { Text("기록일: ${s.date}") },
                    text = {
                        Column {
                            Text("[운행내역]", fontWeight = FontWeight.Bold)
                            if (s.history.isEmpty()) {
                                Text("운행내역이 없습니다.", color = Color.Gray)
                            } else {
                                s.history.forEach { h ->
                                    val parts = h.split("|timestamp=")
                                    val displaySummary = parts[0]
                                    Text(displaySummary, color = Color.White)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("[총 정산내역]", fontWeight = FontWeight.Bold)
                            val sm = s.summary
                            Text("총 운행 횟수: ${sm["totalCount"] ?: "-"}")
                            Text("총 수입: ${sm["totalFare"] ?: "-"}원")
                            Text("총 납입: ${sm["totalDeposit"] ?: "-"}원")
                            Text("총 외상: ${sm["totalCredit"] ?: "-"}원")
                            Text("실 납입: ${sm["realDeposit"] ?: "-"}원")
                            Text("실 수입: ${sm["realIncome"] ?: "-"}원")
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showSessionDetail = false }) { Text("닫기") }
                    }
                )
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "운행 내역",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        if (tripHistory.isEmpty()) {
                            item {
                                Text(
                                    "운행내역이 없습니다.",
                                    color = Color.Gray,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        } else {
                            items(tripHistory) { summary ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
                                    shape = RoundedCornerShape(0.dp)
                                ) {
                                    val parts = summary.split("|timestamp=")
                                    val displaySummary = parts[0]

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = displaySummary,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Normal,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 이월 미수령금
            val carryOverBalance = carryOver?.balance?.toInt() ?: 0
            val carryOverStatus = carryOver?.status

            // 최종 납입액 계산 (사무실 몫 - 외상) - 미환급금 적용 전
            val rawFinalDeposit = officeDeposit - totalCredit

            // ========== 미환급금/미납금과 납입금 통합 계산 ==========
            // carryOverBalance > 0 : 사무실이 기사에게 줄 돈 (미수령금)
            // carryOverBalance < 0 : 기사가 사무실에 줄 돈 (미납금)
            // rawFinalDeposit > 0 : 기사가 사무실에 낼 돈
            // rawFinalDeposit < 0 : 사무실이 기사에게 줄 돈 (오늘 발생)

            // 미환급금/미납금에서 공제 후 실제 납입해야 할 금액
            val adjustedDeposit = if (carryOverBalance >= 0) {
                // 미수령금(양수): 납입액에서 미수령금 차감
                if (rawFinalDeposit > 0) maxOf(0, rawFinalDeposit - carryOverBalance) else 0
            } else {
                // 미납금(음수): 납입액에 미납금 추가
                if (rawFinalDeposit > 0) rawFinalDeposit + (-carryOverBalance) else (-carryOverBalance)
            }

            // 오늘 운행 후 남은 이월금
            val remainingCarryOver = if (carryOverBalance >= 0) {
                // 미수령금(양수)
                if (rawFinalDeposit > 0) {
                    maxOf(0, carryOverBalance - rawFinalDeposit)
                } else {
                    carryOverBalance + (-rawFinalDeposit)
                }
            } else {
                // 미납금(음수)
                if (rawFinalDeposit > 0) {
                    // 납입 후 미납금 상쇄 (rawFinalDeposit로 미납금 갚기)
                    // 남은 이월 = carryOverBalance + rawFinalDeposit (음수 + 양수)
                    val netBalance = carryOverBalance + rawFinalDeposit
                    netBalance  // 음수면 미납 잔여, 양수면 미수령금 발생
                } else {
                    // 미납금 + 사무실이 줄 돈 → 미납 일부 상쇄
                    carryOverBalance + (-rawFinalDeposit)
                }
            }

            // 미환급금에서 공제된 금액 (양수 carryOver일 때만 의미 있음)
            val usedFromCarryOver = if (rawFinalDeposit > 0 && carryOverBalance > 0) {
                minOf(carryOverBalance, rawFinalDeposit)
            } else {
                0
            }

            // 실납입 상태 (입력 필드 없이 기본값 표시 + 확인/정정)
            var actualDeposit by remember { mutableStateOf(0) }
            var isDepositConfirmed by remember { mutableStateOf(false) }
            var showDepositEditDialog by remember { mutableStateOf(false) }
            var depositEditInput by remember { mutableStateOf("") }

            // 정산 카드 접기/펼치기 상태
            var isSettlementExpanded by remember { mutableStateOf(false) }

            // 표시 금액: 확인 전이면 adjustedDeposit (미환급금 공제 후), 확인 후면 actualDeposit
            val displayDeposit = if (isDepositConfirmed) actualDeposit else adjustedDeposit

            // 현금 수령액
            val cashReceived = todaySettlement.cashReceived

            // 수입금 = 현금 수령 - 실납입 (확인 전이면 기본값 사용)
            val actualReceived = cashReceived - displayDeposit

            // 총 정산 차액 = (실납입 - 조정된 납입액)
            // 양수: 환급금 (기사가 받을 돈), 음수: 미납금 (기사가 더 낼 돈)
            val totalSettlementDiff = displayDeposit - adjustedDeposit

            // 수령완료 확인 다이얼로그
            if (showReceiveConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showReceiveConfirmDialog = false },
                    title = { Text("수령 확인", color = Color.White) },
                    text = {
                        Text(
                            "%,d원을 수령하셨습니까?".format(carryOver?.balance ?: 0),
                            color = Color.White
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.confirmReceiveCarryOver { success, message ->
                                    if (success) {
                                        showReceiveConfirmDialog = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("예, 수령했습니다")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showReceiveConfirmDialog = false }) {
                            Text("취소", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF2A2A2A)
                )
            }

            // 오늘 발생 미환급금 (최종납입금이 음수인 경우 = 사무실이 기사에게 줄 돈)
            val todayNewUnpaid = if (rawFinalDeposit < 0) -rawFinalDeposit else 0

            // 누적 미수령금/미납금 카드 (이월금이 있거나 공제 내역이 있을 때 표시)
            if (remainingCarryOver != 0 || usedFromCarryOver > 0 || carryOverStatus == CarryOverStatus.TRANSFERRED) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (carryOverStatus == CarryOverStatus.TRANSFERRED)
                            Color(0xFF2E4A2E) else Color(0xFF4A3A2A)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (remainingCarryOver >= 0) "누적 미수령금" else "누적 미납금",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingCarryOver > 0) Color(0xFFFF6666)
                                    else if (remainingCarryOver < 0) Color(0xFFFF9800)
                                    else Color(0xFF4CAF50)
                            )
                            Text(
                                "%,d원".format(kotlin.math.abs(remainingCarryOver)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (remainingCarryOver > 0) Color(0xFFFF6666)
                                    else if (remainingCarryOver < 0) Color(0xFFFF9800)
                                    else Color(0xFF4CAF50)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 이월 금액 (양수: 미수령금, 음수: 미납금)
                        if (carryOverBalance != 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    if (carryOverBalance > 0) "이월 (미수령)" else "이월 (미납)",
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "%,d원".format(kotlin.math.abs(carryOverBalance)),
                                    color = if (carryOverBalance > 0) Color.White else Color(0xFFFF9800),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        // 오늘 납입으로 공제된 금액
                        if (usedFromCarryOver > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("오늘 공제", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                Text("-%,d원".format(usedFromCarryOver), color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        // 오늘 새로 발생한 미환급금 (외상이 많아서 사무실이 줄 돈)
                        if (todayNewUnpaid > 0) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("오늘 발생", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                                Text("+%,d원".format(todayNewUnpaid), color = Color(0xFFFFAA00), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 상태 표시
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "상태: ${when (carryOverStatus) {
                                    CarryOverStatus.TRANSFERRED -> "이체됨"
                                    CarryOverStatus.SETTLED -> "수령완료"
                                    else -> "미지급"
                                }}",
                                color = when (carryOverStatus) {
                                    CarryOverStatus.TRANSFERRED -> Color(0xFF4CAF50)
                                    CarryOverStatus.SETTLED -> Color.Gray
                                    else -> Color(0xFFFFAA00)
                                },
                                style = MaterialTheme.typography.bodySmall
                            )

                            // 이체됨 상태일 때 수령완료 버튼
                            if (carryOverStatus == CarryOverStatus.TRANSFERRED) {
                                Button(
                                    onClick = { showReceiveConfirmDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text("수령완료", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 통합 정산 카드 (접기/펼치기)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF424242)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 제목 (클릭하면 펼치기/접기)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSettlementExpanded = !isSettlementExpanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("오늘의 정산", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.weight(1f))
                        // 펼침 상태일 때만 설정 버튼 표시
                        if (isSettlementExpanded) {
                            IconButton(onClick = { showRatioDialog = true }) {
                                Icon(Icons.Filled.Settings, contentDescription = "비율 정보", tint = Color.White)
                            }
                        }
                        Icon(
                            imageVector = if (isSettlementExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isSettlementExpanded) "접기" else "펼치기",
                            tint = Color.White
                        )
                    }

                    // 접힌 상태에서 요약 정보 표시
                    if (!isSettlementExpanded) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "총 ${totalCount}건 · %,d원".format(totalFare),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // 펼쳐진 내용
                    AnimatedVisibility(
                        visible = isSettlementExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(4.dp))
                            Divider(thickness = 3.dp, color = Color(0xFFFF9800))
                            Spacer(modifier = Modifier.height(12.dp))

                            // 운행 정보
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("총 운행", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("${totalCount}건", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("총 운임", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(totalFare), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("납입금", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(officeDeposit), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(thickness = 1.dp, color = Color(0xFF666666))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 현금/외상/납입금 정보
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("현금", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(cashReceived), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("외상(이체/포인트)", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (totalCredit > 0) "%,d원".format(totalCredit) else "-",
                            color = if (totalCredit > 0) Color(0xFFFF6666) else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // 미환급금 공제 내역 표시 (최종납입금 표시 제거 - 로직은 유지)
                    if (usedFromCarryOver > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("미환급금 공제", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
                            Text("-%,d원".format(usedFromCarryOver), color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 실납입 카드 (입력 필드 없이 금액 표시 + 확인/정정 버튼)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF333333)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // 실납입 라벨
                            Text("실납입", color = Color.White, style = MaterialTheme.typography.bodyMedium)

                            // 금액 + 버튼
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 금액 표시
                                Text(
                                    "%,d원".format(displayDeposit),
                                    color = if (isDepositConfirmed) Color(0xFF4CAF50) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                // 확인 체크 표시 (확인 완료 시)
                                if (isDepositConfirmed) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("✓", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                // 확인/정정 버튼
                                if (!isDepositConfirmed) {
                                    // 미확인 상태: 확인 버튼
                                    Button(
                                        onClick = {
                                            actualDeposit = displayDeposit
                                            isDepositConfirmed = true
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50)
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("확인", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                // 정정 버튼 (항상 표시)
                                Button(
                                    onClick = {
                                        depositEditInput = displayDeposit.toString()
                                        showDepositEditDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF666666)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("정정", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    // 실납입 정정 다이얼로그
                    if (showDepositEditDialog) {
                        AlertDialog(
                            onDismissRequest = { showDepositEditDialog = false },
                            title = { Text("실납입 금액 수정", color = Color.White) },
                            text = {
                                Column {
                                    Text("실제 납입한 금액을 입력하세요", color = Color.Gray)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    BasicTextField(
                                        value = depositEditInput,
                                        onValueChange = { newValue ->
                                            if (newValue.all { it.isDigit() }) {
                                                depositEditInput = newValue
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color(0xFF222222), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        decorationBox = { innerTextField ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                                    if (depositEditInput.isEmpty()) {
                                                        Text(
                                                            "0",
                                                            color = Color.Gray,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                                                            textAlign = androidx.compose.ui.text.style.TextAlign.End
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("원", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        actualDeposit = depositEditInput.toIntOrNull() ?: 0
                                        isDepositConfirmed = true
                                        showDepositEditDialog = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Text("확인")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDepositEditDialog = false }) {
                                    Text("취소", color = Color.White)
                                }
                            },
                            containerColor = Color(0xFF2A2A2A)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(thickness = 2.dp, color = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 수입금 (현금 수령 - 실납입)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("최종 수입금", color = Color(0xFFFFB000), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "%,d원".format(actualReceived),
                            color = Color(0xFFFFB000),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 총 정산 차액 (실납입 확인 시에만 표시, 이월 환급금 포함)
                    if (isDepositConfirmed && totalSettlementDiff != 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                if (totalSettlementDiff > 0) "총 환급금" else "총 미납금",
                                color = if (totalSettlementDiff > 0) Color(0xFF4CAF50) else Color(0xFFFF6666),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "%,d원".format(kotlin.math.abs(totalSettlementDiff)),
                                color = if (totalSettlementDiff > 0) Color(0xFF4CAF50) else Color(0xFFFF6666),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                            // 이체된 미수령금 수령 버튼
                            if (carryOverStatus == CarryOverStatus.TRANSFERRED) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "사무실에서 이체되었습니다!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFFFCC00),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showReceiveConfirmDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                                ) {
                                    Text("수령완료", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            // 업무마감 다이얼로그
            var showEndWorkDialog by remember { mutableStateOf(false) }
            var isSubmitting by remember { mutableStateOf(false) }

            if (showEndWorkDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isSubmitting) showEndWorkDialog = false },
                    title = { Text("업무마감", color = Color.White) },
                    text = {
                        Column {
                            Text("업무를 마감하고 로그아웃 하시겠습니까?", color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("실납입: %,d원".format(actualDeposit), color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                            if (totalSettlementDiff != 0) {
                                Text(
                                    if (totalSettlementDiff > 0) "총 환급금: %,d원".format(totalSettlementDiff)
                                    else "총 미납금: %,d원".format(kotlin.math.abs(totalSettlementDiff)),
                                    color = if (totalSettlementDiff > 0) Color(0xFF4CAF50) else Color(0xFFFF6666),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFF666666))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("마감 시 처리 내용:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text("• 매니저에게 정산 확인 요청", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text("• 운행/정산 내역 저장", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text("• 로그아웃 및 앱 종료", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isSubmitting = true
                                viewModel.submitDailySettlement(actualDeposit) { success, message ->
                                    if (success) {
                                        // 1. Firestore에 dailySettlement 저장 성공 후, 로컬 아카이브 저장
                                        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                        val summaryMap = mapOf(
                                            "totalCount" to totalCount,
                                            "totalFare" to totalFare,
                                            "totalDeposit" to officeDeposit,
                                            "totalCredit" to totalCredit,
                                            "realDeposit" to actualDeposit,
                                            "realIncome" to realIncome
                                        )
                                        val newSession = SessionData(now, tripHistory, summaryMap)
                                        val updatedSessions = (sessionList + newSession).takeLast(5).toMutableList()
                                        saveSessions(context, updatedSessions)
                                        sessionList = updatedSessions

                                        // 2. 정산 상태 초기화 후 로그아웃
                                        viewModel.clearSettlement(
                                            onSuccess = {
                                                isSubmitting = false
                                                showEndWorkDialog = false

                                                // 3. 로그아웃 및 앱 종료
                                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                    // SharedPreferences 로그인 정보 제거
                                                    val loginPrefs = context.getSharedPreferences("driver_login_prefs", Context.MODE_PRIVATE)
                                                    loginPrefs.edit()
                                                        .putBoolean("auto_login", false)
                                                        .remove("identifier")
                                                        .remove("password")
                                                        .apply()

                                                    // Firebase Auth 로그아웃
                                                    FirebaseAuth.getInstance().signOut()

                                                    // 앱 종료
                                                    (context as? Activity)?.finishAffinity()
                                                }, 500)
                                            },
                                            onError = { errorMsg ->
                                                Log.e("HistorySettlement", "정산 초기화 실패: $errorMsg")
                                                isSubmitting = false
                                                showEndWorkDialog = false
                                            }
                                        )
                                    } else {
                                        Log.e("HistorySettlement", "업무마감 실패: $message")
                                        isSubmitting = false
                                        // 실패 시에도 다이얼로그 닫기
                                        showEndWorkDialog = false
                                    }
                                }
                            },
                            enabled = !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text(if (isSubmitting) "처리중..." else "마감하기")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showEndWorkDialog = false },
                            enabled = !isSubmitting
                        ) {
                            Text("취소", color = Color.White)
                        }
                    },
                    containerColor = Color(0xFF2A2A2A)
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 업무마감 버튼 (실납입 확인 완료 시에만 활성화)
                // 마감 시 정산 저장 + 초기화가 함께 수행됨
                Button(
                    onClick = { showEndWorkDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isDepositConfirmed && totalCount > 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        disabledContainerColor = Color(0xFF555555)
                    )
                ) {
                    Text(
                        "업무마감",
                        fontWeight = FontWeight.Bold,
                        color = if (isDepositConfirmed && totalCount > 0) Color.White else Color.Gray
                    )
                }
            }
        }
        if (showLogoutConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutConfirmDialog = false },
                title = { Text("로그아웃 확인") },
                text = { Text("정말 로그아웃하시겠습니까?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showLogoutConfirmDialog = false
                            logoutUserAndExitApp(context, scope, viewModel)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("로그아웃")
                    }
                }
            )
        }
    }
}
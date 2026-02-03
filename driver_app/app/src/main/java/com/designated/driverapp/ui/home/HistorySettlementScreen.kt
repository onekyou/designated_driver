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
import android.content.Context
import androidx.compose.ui.platform.LocalContext
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

    // 마감 시점 기준으로 당일 운행내역만 필터링 (콜매니저와 동일)
    fun loadTripHistory(context: Context, lastCleared: Long): List<String> {
        val prefs = context.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]")
        val historyList = JSONArray(historyJson)
        val filtered = mutableListOf<String>()
        for (i in 0 until historyList.length()) {
            val item = historyList.getString(i)
            val parts = item.split("|timestamp=")
            val timestamp = if (parts.size > 1) parts[1].toLongOrNull() ?: 0L else 0L
            // 마감 시점 이후 데이터만 표시 (당일)
            if (timestamp > lastCleared) {
                filtered.add(item)
            }
        }
        return filtered
    }
    // ✅ Firestore 기반 정산 데이터 (calls에서 계산)
    val depositRatio by viewModel.depositRatio.collectAsStateWithLifecycle()
    val todaySettlement by viewModel.todaySettlement.collectAsStateWithLifecycle()
    val lastClearedMillis by viewModel.lastClearedMillis.collectAsStateWithLifecycle()

    // tripHistory - 마감 시점 기준 당일 운행내역
    var tripHistory by remember { mutableStateOf(loadTripHistory(context, lastClearedMillis)) }

    // lastClearedMillis가 변경될 때 tripHistory 다시 로드
    LaunchedEffect(lastClearedMillis) {
        tripHistory = loadTripHistory(context, lastClearedMillis)
    }

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

            // 최종 납입액 계산 (사무실 몫 - 외상 - 이월 미수령)
            val finalDeposit = officeDeposit - totalCredit - carryOverBalance

            // 실납입 입력 상태
            var actualDepositInput by remember { mutableStateOf("") }
            val actualDeposit = actualDepositInput.toIntOrNull() ?: 0

            // 실수령액 = 현금 수령 - 실납입
            val cashReceived = todaySettlement.cashReceived
            val actualReceived = cashReceived - actualDeposit

            // 누적 미수령 = 실납입 - 최종 납입액 (양수면 기사가 돌려받을 돈)
            val newUnpaid = actualDeposit - finalDeposit

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

            // 통합 정산 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF424242)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 제목
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("오늘의 정산", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { showRatioDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "비율 정보", tint = Color.White)
                        }
                    }
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
                        Text("총 운행료", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(totalFare), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("기사 수입 (${100 - depositRatio}%)", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(realIncome), color = Color(0xFFFFB000), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("사무실 몫 (${depositRatio}%)", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(officeDeposit), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(thickness = 1.dp, color = Color(0xFF666666))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 현금/외상 정보
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("현금 수령", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(cashReceived), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("외상 (이체/포인트)", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (totalCredit > 0) "%,d원".format(totalCredit) else "-",
                            color = if (totalCredit > 0) Color(0xFFFF6666) else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // 이월 미수령 (있을 때만 표시)
                    if (carryOverBalance > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("이월 미수령", color = Color(0xFFFFAA00), style = MaterialTheme.typography.bodyMedium)
                                if (carryOverStatus == CarryOverStatus.TRANSFERRED) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "(이체됨)",
                                        color = Color(0xFFFFCC00),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "-%,d원".format(carryOverBalance),
                                color = Color(0xFFFFAA00),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("최종 납입액", color = Color.White, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        val depositText = if (finalDeposit >= 0) {
                            "%,d원".format(finalDeposit)
                        } else {
                            "-%,d원 (받을 금액)".format(-finalDeposit)
                        }
                        Text(
                            depositText,
                            color = if (finalDeposit >= 0) Color.White else Color(0xFF4CAF50),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 실납입 입력 필드
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
                            Text("실납입", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicTextField(
                                    value = actualDepositInput,
                                    onValueChange = { newValue ->
                                        // 숫자만 입력 허용
                                        if (newValue.all { it.isDigit() }) {
                                            actualDepositInput = newValue
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End
                                    ),
                                    modifier = Modifier
                                        .width(120.dp)
                                        .background(Color(0xFF222222), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterEnd) {
                                            if (actualDepositInput.isEmpty()) {
                                                Text(
                                                    "0",
                                                    color = Color.Gray,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("원", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(thickness = 2.dp, color = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 최종 결과
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("실수령액", color = Color(0xFFFFB000), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(
                            "%,d원".format(actualReceived),
                            color = Color(0xFFFFB000),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 누적 미수령 (실납입 입력 시에만 표시)
                    if (actualDepositInput.isNotEmpty() && newUnpaid != 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("누적 미수령", color = Color(0xFFFFAA00), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "%,d원".format(newUnpaid),
                                color = if (newUnpaid > 0) Color(0xFF4CAF50) else Color(0xFFFF6666),
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
            var showClearDialog by remember { mutableStateOf(false) }
            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("운행내역/정산내역 저장 및 초기화") },
                    text = { Text("운행내역/정산내역을 저장하고 새로 시작합니다. 이전 기록은 최대 5개까지 보관됩니다. 진행할까요?") },
                    confirmButton = {
                        Button(onClick = {
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                            val summaryMap = mapOf(
                                "totalCount" to totalCount,
                                "totalFare" to totalFare,
                                "realDeposit" to realDeposit,
                                "realIncome" to realIncome
                            )
                            val newSession = SessionData(now, tripHistory, summaryMap)
                            val updatedSessions = (sessionList + newSession).takeLast(5).toMutableList()
                            saveSessions(context, updatedSessions)
                            sessionList = updatedSessions
                            val prefs = context.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
                            prefs.edit().putString("history_list", "[]").apply()
                            tripHistory = listOf()
                            showClearDialog = false
                        }) { Text("확인") }
                    }
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("운행내역/정산내역 저장 및 초기화") }
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
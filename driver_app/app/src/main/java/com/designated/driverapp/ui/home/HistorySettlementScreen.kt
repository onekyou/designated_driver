package com.designated.driverapp.ui.home

import androidx.compose.foundation.layout.*
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
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.designated.driverapp.viewmodel.DriverViewModel
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val scope = rememberCoroutineScope()
    val systemUiController = rememberSystemUiController()

    SideEffect {
        systemUiController.setStatusBarColor(
            color = Color(0xFF222222), // 어두운 배경
            darkIcons = false // 밝은 아이콘
        )
    }
    val sortedCalls = completedCalls.sortedByDescending { it.timestamp }

    fun loadTripHistory(context: Context): List<String> {
        val prefs = context.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
        val historyJson = prefs.getString("history_list", "[]")
        val historyList = JSONArray(historyJson)
        val now = System.currentTimeMillis()
        val fiveDaysMillis = 5 * 24 * 60 * 60 * 1000L
        val filtered = mutableListOf<String>()
        val filteredJson = JSONArray()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        for (i in 0 until historyList.length()) {
            val item = historyList.getString(i)
            val parts = item.split("|timestamp=")
            val summary = parts[0]
            val timestamp = if (parts.size > 1) parts[1].toLongOrNull() ?: 0L else 0L
            if (timestamp > 0L && now - timestamp <= fiveDaysMillis) {
                filtered.add(item)
                filteredJson.put(item)
            }
        }
        if (filtered.size != historyList.length()) {
            prefs.edit().putString("history_list", filteredJson.toString()).apply()
        }
        return filtered
    }
    var tripHistory by remember { mutableStateOf(loadTripHistory(context)) }

    val prefs = context.getSharedPreferences("settlement_prefs", Context.MODE_PRIVATE)
    var depositPercent by remember { mutableStateOf(prefs.getInt("deposit_percent", 60)) }
    var showDialog by remember { mutableStateOf(false) }

    data class TripSummary(
        val fare: Int,
        val payment: String,
        val cashAmount: Int = 0
    )
    fun parseTripSummary(summary: String): TripSummary? {
        val parts = summary.split(", ")
        if (parts.size < 4) return null
        val fare = parts[2].replace("원", "").replace(",", "").trim().toIntOrNull() ?: 0
        val payment = parts[3]
        return if (payment.startsWith("현금+포인트")) {
            // 쉼표가 포함된 현금 금액도 처리하도록 정규식 수정
            val cashRegex = Regex("\\(([\\d,]+)원 현금\\)")
            val cashMatch = cashRegex.find(payment)
            val cash = cashMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull() ?: 0
            TripSummary(fare, "현금+포인트", cash)
        } else {
            TripSummary(fare, payment)
        }
    }
    val parsedList = tripHistory.mapNotNull { parseTripSummary(it) }
    val totalCount = parsedList.size
    val totalFare = parsedList.sumOf { it.fare }
    val totalDeposit = (totalFare * depositPercent / 100.0).toInt()
    val totalCredit = parsedList.sumOf {
        val credit = when {
            it.payment == "현금" -> 0
            it.payment == "현금+포인트" -> it.fare - it.cashAmount
            else -> it.fare
        }
        credit
    }
    val realDeposit = totalDeposit - totalCredit
    val realIncome = totalFare - totalDeposit

    LaunchedEffect(shouldNavigateToHistorySettlement) {
        if (shouldNavigateToHistorySettlement) {
            viewModel.onNavigateToHistorySettlementHandled()
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("납입금 비율 조정") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("납입금 비율을 10% 단위로 조정하세요.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = depositPercent.toFloat(),
                        onValueChange = { depositPercent = (it / 10).toInt() * 10 },
                        valueRange = 10f..90f,
                        steps = 7,
                        onValueChangeFinished = {},
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("현재: $depositPercent%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(onClick = {
                    prefs.edit().putInt("deposit_percent", depositPercent).apply()
                    showDialog = false
                }) { Text("확인") }
            }
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
            TopAppBar(
                title = {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "운행 내역",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.align(Alignment.Center),
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showLogoutConfirmDialog = true }) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = "로그아웃", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(Icons.Filled.History, contentDescription = "이전 기록 보기", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            if (showHistoryDialog) {
                AlertDialog(
                    onDismissRequest = { showHistoryDialog = false },
                    title = { Text("이전 운행/정산 기록") },
                    text = {
                        if (sessionList.isEmpty()) {
                            Text("이전 기록이 없습니다.")
                        } else {
                            Column {
                                sessionList.forEachIndexed { idx, session ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                selectedSession = session
                                                showSessionDetail = true
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                                        elevation = CardDefaults.cardElevation(0.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text("기록일: ${session.date}", fontWeight = FontWeight.Bold)
                                            Text("운행내역: ${session.history.size}건")
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(onClick = { showHistoryDialog = false }) { Text("닫기") }
                    }
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
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (tripHistory.isEmpty()) {
                    item {
                        Text("운행내역이 없습니다.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    }
                } else {
                    items(tripHistory) { summary ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(0.dp)
                        ) {
                            val parts = summary.split("|timestamp=")
                            val displaySummary = parts[0]
                            val dateStr = if (parts.size > 1) {
                                val ts = parts[1].toLongOrNull() ?: 0L
                                if (ts > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date(ts)) else ""
                            } else ""
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(displaySummary, color = Color.Black)
                                if (dateStr.isNotEmpty()) {
                                    Text(dateStr, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF424242)),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("총 정산 내역", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { showDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "설정", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Divider(thickness = 3.dp, color = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("총 운행 횟수: $totalCount", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Text("총 수입: %,d원".format(totalFare), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Text("총 납입: %,d원".format(totalDeposit), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Text("총 외상: %,d원".format(totalCredit), style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(thickness = 2.dp, color = Color(0xFFFF9800))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "실 납입: %,d원".format(realDeposit),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFFFF9800)
                    )
                    Text(
                        "실 수입: %,d원".format(realIncome),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
            var showClearDialog by remember { mutableStateOf(false) }
            if (showClearDialog) {
                AlertDialog(
                    onDismissRequest = { showClearDialog = false },
                    title = { Text("운행내역/정산내역 저장 및 초기화") },
                    text = { Text("오늘까지의 운행내역/정산내역을 저장하고 새로 시작합니다. 이전 기록은 최대 5개까지 보관됩니다. 진행할까요?") },
                    confirmButton = {
                        Button(onClick = {
                            val now = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
                            val summaryMap = mapOf(
                                "totalCount" to totalCount,
                                "totalFare" to totalFare,
                                "totalDeposit" to totalDeposit,
                                "totalCredit" to totalCredit,
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
            Column {
                Button(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("오늘까지의 운행내역/정산내역 저장 및 초기화") }
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
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
import androidx.compose.material.icons.filled.Home
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
            color = Color(0xFF222222),
            darkIcons = false
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
        // 먼저 timestamp 부분을 분리
        val summaryWithoutTimestamp = summary.split("|timestamp=")[0]
        val parts = summaryWithoutTimestamp.split(", ")
        if (parts.size < 4) return null
        val fare = parts[2].replace("원", "").replace(",", "").trim().toIntOrNull() ?: 0
        val payment = parts[3].trim()  // trim 추가
        return if (payment.startsWith("현금+포인트")) {
            val cashRegex = Regex("\\(([\\d,]+)원 현금\\)")
            val cashMatch = cashRegex.find(payment)
            val cash = cashMatch?.groupValues?.getOrNull(1)?.replace(",", "")?.toIntOrNull() ?: 0
            TripSummary(fare, payment, cash)
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
            it.payment.startsWith("현금+포인트") -> it.fare - it.cashAmount
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
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
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
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
                    text = { Text("운행내역/정산내역을 저장하고 새로 시작합니다. 이전 기록은 최대 5개까지 보관됩니다. 진행할까요?") },
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
package com.designated.driverapp.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
import com.designated.driverapp.model.DriverStatus
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
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.designated.driverapp.data.settlement.SettlementCalc

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

    // ✅ Firestore 기반 정산 데이터 (calls에서 계산)
    val depositRatio by viewModel.depositRatio.collectAsStateWithLifecycle()
    val todaySettlement by viewModel.todaySettlement.collectAsStateWithLifecycle()
    val lastClearedMillis by viewModel.lastClearedMillis.collectAsStateWithLifecycle()

    // ✅ 운행내역 - ViewModel에서 직접 가져옴 (Firestore 기반, 정산과 동일 데이터 소스)
    val tripHistoryList by viewModel.tripHistoryList.collectAsStateWithLifecycle()
    // 표시용 문자열 변환
    val tripHistory = tripHistoryList.map { it.toDisplayString() }

    // 콜 단위 결제수단 정정 모달 (제출 전 + 현금/외상/이체 콜만). 제출 후(PENDING_CONFIRM)엔 잠금 —
    // settlementSessions·dailySettlement 정정 전파 안 함(제출 전 정정만 허용해 제출본 정합 보장)
    var editTargetCallId by remember { mutableStateOf<String?>(null) }
    var editTargetMethod by remember { mutableStateOf("") }
    val canEditPayment = uiState.driverStatus != DriverStatus.PENDING_CONFIRM

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
    fun loadSessions(ctx: Context): MutableList<SessionData> {
        val prefs = ctx.getSharedPreferences("trip_sessions", Context.MODE_PRIVATE)
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
                        onClick = { navController.navigate(com.designated.driverapp.navigation.AppDestinations.SETTINGS_ROUTE) },
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (tripHistory.isEmpty()) {
                            Text(
                                "운행내역이 없습니다.",
                                color = Color.Gray,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            tripHistoryList.forEach { item ->
                                val editable = canEditPayment &&
                                    item.callId.isNotBlank() &&
                                    item.paymentMethod in listOf("현금", "외상", "이체")
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A3A3A)),
                                    shape = RoundedCornerShape(0.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (editable) Modifier.clickable {
                                                    editTargetCallId = item.callId
                                                    editTargetMethod = item.paymentMethod
                                                } else Modifier
                                            )
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = item.toDisplayString(),
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
            // 콜 단위 결제수단 정정 모달 (현금/외상/이체 3버튼 원클릭)
            editTargetCallId?.let { targetId ->
                AlertDialog(
                    onDismissRequest = { editTargetCallId = null },
                    title = { Text("결제수단 정정") },
                    text = { Text("이 콜의 결제수단을 변경합니다. (현재: $editTargetMethod)") },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("현금", "외상", "이체").forEach { m ->
                                TextButton(onClick = {
                                    viewModel.updateCallPaymentMethod(targetId, m) { _, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                    editTargetCallId = null
                                }) { Text(m) }
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { editTargetCallId = null }) { Text("취소") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 납입금(net) = 사무실 몫 − 외상/이체/포인트 (부호, 음수 허용)
            // + 면 사무실에 입금, − 면 사무실에서 정산받음. 실제 정산은 현장에서 알아서.
            val net = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit)

            // 정산 카드 접기/펼치기 상태
            var isSettlementExpanded by remember { mutableStateOf(false) }

            // 현금 수령액
            val cashReceived = todaySettlement.cashReceived

            // 최종 수입금 = 기사 몫
            val actualReceived = realIncome

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
                        Text("${totalCount}건", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("settlement_today_count"))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("총 운임", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(totalFare), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("settlement_today_totalFare"))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("사무실 몫", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(officeDeposit), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("settlement_today_officeDeposit"))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(thickness = 1.dp, color = Color(0xFF666666))
                    Spacer(modifier = Modifier.height(12.dp))

                    // 현금/외상/납입금 정보
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("현금", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text("%,d원".format(cashReceived), color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("settlement_today_cashReceived"))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("외상(이체/포인트)", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (totalCredit > 0) "%,d원".format(totalCredit) else "-",
                            color = if (totalCredit > 0) Color(0xFFFF6666) else Color.Gray,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag("settlement_today_totalCredit")
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // 납입금 (사무실 몫 − 외상/이체/포인트, 부호). + 입금 / − 정산받음. 실제 정산은 현장에서.
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
                            Text("납입금", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "%,d원".format(net),
                                color = if (net >= 0) Color(0xFF4CAF50) else Color(0xFFFF6666),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("settlement_today_net")
                            )
                        }
                    }
                    if (net < 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "사무실에서 정산받을 금액입니다",
                            color = Color.Gray,
                            style = MaterialTheme.typography.bodySmall
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

                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            // 업무마감 다이얼로그
            var showEndWorkDialog by remember { mutableStateOf(false) }
            var isSubmitting by remember { mutableStateOf(false) }

            if (showEndWorkDialog) {
                AlertDialog(
                    onDismissRequest = { if (!isSubmitting) showEndWorkDialog = false },
                    title = { Text("업무마감", color = Color.White) },
                    text = {
                        Column {
                            Text("업무를 마감하시겠습니까?", color = Color.White)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("납입금: %,d원".format(net), color = if (net >= 0) Color(0xFF4CAF50) else Color(0xFFFF6666), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFF666666))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("마감 시 처리 내용:", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text("• 운행/정산 내역 저장", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Text("• 마감 후 [퇴근하기]로 로그아웃", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                isSubmitting = true
                                viewModel.submitDailySettlement { success, message ->
                                    if (success) {
                                        // 로컬 아카이브 저장
                                        val now = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                                        val summaryMap = mapOf(
                                            "totalCount" to totalCount,
                                            "totalFare" to totalFare,
                                            "totalDeposit" to officeDeposit,
                                            "totalCredit" to totalCredit,
                                            "realDeposit" to net,
                                            "realIncome" to realIncome
                                        )
                                        val newSession = SessionData(now, tripHistory, summaryMap)
                                        val updatedSessions = (sessionList + newSession).takeLast(5).toMutableList()
                                        saveSessions(context, updatedSessions)
                                        sessionList = updatedSessions

                                        isSubmitting = false
                                        showEndWorkDialog = false
                                        // 로그아웃하지 않음 - 매니저 확인 대기 상태로 전환
                                    } else {
                                        Log.e("HistorySettlement", "업무마감 실패: $message")
                                        isSubmitting = false
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
                if (uiState.driverStatus == DriverStatus.PENDING_CONFIRM) {
                    // 마감 완료 → [퇴근하기]
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A3A1A))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "마감 완료",
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "운행 자료가 저장되었습니다.",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.clearSettlement(
                                onSuccess = {
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        val loginPrefs = context.getSharedPreferences("driver_login_prefs", Context.MODE_PRIVATE)
                                        loginPrefs.edit()
                                            .putBoolean("auto_login", false)
                                            .remove("identifier")
                                            .remove("password")
                                            .apply()
                                        FirebaseAuth.getInstance().signOut()
                                        (context as? Activity)?.finishAffinity()
                                    }, 500)
                                },
                                onError = { errorMsg ->
                                    Log.e("HistorySettlement", "퇴근 처리 실패: $errorMsg")
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) {
                        Text("퇴근하기", fontWeight = FontWeight.Bold)
                    }
                } else {
                    // WORKING → 업무마감 버튼
                    Button(
                        onClick = { showEndWorkDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = totalCount > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800),
                            disabledContainerColor = Color(0xFF555555)
                        )
                    ) {
                        Text(
                            "업무마감",
                            fontWeight = FontWeight.Bold,
                            color = if (totalCount > 0) Color.White else Color.Gray
                        )
                    }
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
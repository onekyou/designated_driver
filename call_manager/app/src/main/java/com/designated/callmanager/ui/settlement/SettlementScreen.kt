package com.designated.callmanager.ui.settlement

import androidx.compose.ui.platform.LocalContext
import android.util.Log

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(onNavigateBack: () -> Unit, viewModel: SettlementViewModel = viewModel()) {
    // completed_trips 데이터도 화면 진입 시 로드
    LaunchedEffect(Unit) {
        viewModel.loadSettlementData()
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val settlementList by viewModel.settlementList.collectAsState()

    // --- 상단 탭 상태 및 색상(딥엘로우) ---
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabBackgroundColor = androidx.compose.ui.graphics.Color(0xFFFFC107) // 딥엘로우(Amber 500)
    val tabContentColor = MaterialTheme.colorScheme.onPrimary

    val tabItems = listOf(
        Pair("기사별", Icons.Default.Person),
        Pair("날짜별", Icons.Default.DateRange)
    )

    // 오늘 날짜(yyyy-MM-dd) 문자열
    val today = remember {
        java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())
    }
    // completed_trips 기준 오늘 운행내역만 필터
    val todaySettlementItems = settlementList.filter {
        java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.completedAt)) == today
    }.sortedByDescending { it.completedAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            "정산관리",
                            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = {
                        val intent = android.content.Intent(context, com.designated.callmanager.MainActivity::class.java)
                        intent.action = "com.designated.callmanager.HOME"
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.Home, contentDescription = "홈")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // --- 상단 TabRow ---
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = tabBackgroundColor,
                contentColor = tabContentColor
            ) {
                tabItems.forEachIndexed { index, pair ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(pair.first) },
                        icon = { Icon(pair.second, contentDescription = pair.first) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // --- 당일 운행내역만 표시 ---
            if (isLoading) {
                CircularProgressIndicator()
            }
            val errorMsg: String? = error
            if (!errorMsg.isNullOrBlank()) {
                Text("오류: $errorMsg", color = MaterialTheme.colorScheme.error)
            } else {
                Text("정산(완료) 운행내역 ($today)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TripListTable(todaySettlementItems)
                Spacer(modifier = Modifier.height(16.dp))
                // 집계 영역
                Text("총 운행 건수: ${todaySettlementItems.size}")
                Text("총 금액: ${todaySettlementItems.sumOf { it.fare }}원")
            }
        }
    }
}

// 날짜 문자열(yyyy-MM-dd) → millis 변환 함수(시작/끝 구분)
fun parseDateToMillis(date: String, endOfDay: Boolean = false): Long {
    return try {
        val formatter = java.text.SimpleDateFormat("yyyy-MM-dd")
        val d = formatter.parse(date)
        if (d != null) {
            if (endOfDay) d.time + 24*60*60*1000 - 1 else d.time
        } else 0L
    } catch (e: Exception) { 0L }
}

@Composable
fun DropdownMenuBox(items: List<String>, selectedItem: String, onItemSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedItem.ifBlank { "선택" })
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = {
                    onItemSelected(item)
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun TripListTable(tripList: List<com.designated.callmanager.ui.settlement.SettlementData>) {
    var detailDialogState by remember { mutableStateOf(false) }
    var detailDialogTrip by remember { mutableStateOf<com.designated.callmanager.ui.settlement.SettlementData?>(null) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("순번", Modifier.weight(0.7f).fillMaxWidth(), textAlign = TextAlign.Center)
            Text("고객", Modifier.weight(1f))
            Text("출발", Modifier.weight(1f))
            Text("도착", Modifier.weight(1f))
            Text("요금", Modifier.weight(1f))
            Text("결제", Modifier.weight(1f))
            Spacer(Modifier.weight(1f)) // 자세히 버튼 영역
        }
        Divider()
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.heightIn(max = 350.dp)
        ) {
            items(tripList.size) { index ->
                val trip = tripList[index]
                Log.d("SettlementScreen", "Displaying trip - callId: ${trip.callId}, paymentMethod: '${trip.paymentMethod}'")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    // 1. 순번
                    Text((index+1).toString(), Modifier.weight(0.7f).fillMaxWidth(), textAlign = TextAlign.Center)
                    // 2. 고객명 (3글자)
                    Text(trip.customerName.substringBefore("/").take(3), Modifier.weight(1f))
                    // 3. 출발 (3글자)
                    Text(trip.departure.take(3), Modifier.weight(1f))
                    // 4. 도착 (3글자)
                    Text(trip.destination.take(3), Modifier.weight(1f))
                    // 5. 요금
                    Text(trip.fare.toString(), Modifier.weight(1f))
                    // 6. 결제방법
                    Text(
    if (trip.paymentMethod == "현금+포인트") "현금+P" else trip.paymentMethod,
    Modifier.weight(1f)
)
                    // 7. 자세히 버튼
                    IconButton(onClick = {
                        detailDialogTrip = trip
                        detailDialogState = true
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Info, contentDescription = "자세히")
                    }
                }
                Divider()
            }
        }
        // --- 자세히 다이얼로그 ---
        if (detailDialogState && detailDialogTrip != null) {
            val t = detailDialogTrip!!
            AlertDialog(
                onDismissRequest = { detailDialogState = false },
                title = { Text("운행 전체 내역") },
                text = {
                    Column {
                        Text("기사: ${t.driverName}")
                        Text("고객: ${t.customerName}")
                        Text("출발: ${t.departure}")
                        Text("도착: ${t.destination}")
                        Text("요금: ${t.fare}")
                        Text("결제방법: ${t.paymentMethod}")
                    }
                },
                confirmButton = {
                    Button(onClick = { detailDialogState = false }) { Text("닫기") }
                }
            )
        }
    }
}
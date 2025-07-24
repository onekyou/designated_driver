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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import com.designated.callmanager.data.SessionInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(onNavigateBack: () -> Unit, onNavigateHome: () -> Unit, viewModel: SettlementViewModel = viewModel()) {
    // 기존 데이터 로딩 경로 유지
    LaunchedEffect(Unit) {
        viewModel.loadSettlementData("yangpyong", "office_2")
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val settlementList by viewModel.settlementList.collectAsState()
    val clearedDates by viewModel.clearedDates.collectAsState()
    val allTripsCleared by viewModel.allTripsCleared.collectAsState()
    val officeShareRatio by viewModel.officeShareRatio.collectAsState()
    val creditPersons by viewModel.creditPersons.collectAsState()
    val sessionList by viewModel.sessionList.collectAsState()

    // 탭 상태 (전체내역이 첫 번째)
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabBackgroundColor = androidx.compose.ui.graphics.Color(0xFFFFB000)
    val tabContentColor = androidx.compose.ui.graphics.Color.Black

    val tabItems = listOf(
        "전체내역",
        "기사별",
        "일일정산",
        "외상관리"
    )

    // 오늘 업무일 계산 (기존 로직 유지)
    val todayWorkDate = remember {
        val calendar = java.util.Calendar.getInstance()
        if (calendar.get(java.util.Calendar.HOUR_OF_DAY) < 6) {
            calendar.add(java.util.Calendar.DAY_OF_MONTH, -1)
        }
        java.text.SimpleDateFormat("yyyy-MM-dd").format(calendar.time)
    }
    
    // 오늘 업무일 기준으로 필터링 (기존 로직 유지)
    val allSettlementItems = settlementList.filterNot { clearedDates.contains(it.workDate) }
    val todaySettlementItems = allSettlementItems.filter { it.workDate == todayWorkDate }
    
    // 외상 관리 상태
    // 기존 creditItems 제거; 외상인은 ViewModel 에서 관리

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            "정산관리",
                            modifier = Modifier.align(androidx.compose.ui.Alignment.Center),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기", tint = Color.White)
                    }
                },
                actions = {
                    val context = LocalContext.current
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Filled.Home, contentDescription = "홈", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // 탭 UI (아이콘 제거)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = tabBackgroundColor,
                contentColor = tabContentColor
            ) {
                tabItems.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, color = Color.Black) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // 로딩/에러 처리
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFFB000))
                }
            } else {
                val errorMsg: String? = error
                if (!errorMsg.isNullOrBlank()) {
                    Text("오류: $errorMsg", color = MaterialTheme.colorScheme.error)
                } else {
                    // 탭별 컨텐츠 표시
                    when (selectedTabIndex) {
                        0 -> AllTripsMainView(
                            settlementItems = if (allTripsCleared) emptyList() else allSettlementItems,
                            workDate = todayWorkDate,
                            creditItems = creditPersons.map { CreditItem(customerName = it.name, amount = it.amount, date = "", memo = it.memo, isCollected = false) },
                            onClearAll = { viewModel.clearLocalSettlement() },
                            officeShareRatio = officeShareRatio,
                            onChangeRatio = { viewModel.updateOfficeShareRatio(it) },
                            onNavigateToCreditTab = { selectedTabIndex = 3 }
                        )
                        1 -> DriverSettlement(
                            settlementItems = if (allTripsCleared) emptyList() else allSettlementItems,
                            workDate = todayWorkDate
                        )
                        2 -> DailySessionsSimple(
                            sessions = sessionList,
                            onSessionClick = { /* TODO: 상세 */ }
                        )
                        3 -> CreditManagementTab(viewModel = viewModel)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (selectedTabIndex == 0) {
                Button(
                    onClick = { viewModel.clearLocalSettlement() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !allTripsCleared
                ) {
                    Text("전체내역 초기화", color = Color.White)
                }
            }
        }
    }
}

// 외상 관리용 데이터 클래스
data class CreditItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val customerName: String,
    val amount: Int,
    val date: String,
    val memo: String = "",
    val isCollected: Boolean = false
)

// 전체내역 메인 뷰 (결제방법별 카드 포함)
@Composable
fun AllTripsMainView(
    settlementItems: List<SettlementData>, 
    workDate: String,
    creditItems: List<CreditItem>,
    onClearAll: () -> Unit,
    officeShareRatio: Int,
    onChangeRatio: (Int) -> Unit,
    onNavigateToCreditTab: () -> Unit
) {
    var selectedPaymentFilter by remember { mutableStateOf<String?>(null) }
    var showCreditDialog by remember { mutableStateOf(false) }
    
    // 필터링된 아이템
    val filteredItems = if (selectedPaymentFilter != null) {
        settlementItems.filter { it.paymentMethod == selectedPaymentFilter }
    } else {
        settlementItems
    }
    
    Column(
                    modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 필터 상태 표시
        if (selectedPaymentFilter != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$selectedPaymentFilter 결제 내역 (${filteredItems.size}건)",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { selectedPaymentFilter = null }) {
                    Text("전체보기", color = Color(0xFFFFB000))
                }
            }
        } else {
            Text(
                "전체 운행내역 (업무일: $workDate)",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 운행 내역 테이블
        TripListTable(filteredItems, onShowCreditDialog = { showCreditDialog = true })
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 정산 요약 카드
        val extraCredit = creditItems.sumOf { it.amount }
        SettlementSummaryCard(filteredItems, selectedPaymentFilter, officeShareRatio, onChangeRatio, extraCredit)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 결제방법별 버튼 카드 (전체보기일 때만)
        if (selectedPaymentFilter == null) {
            PaymentMethodCards(filteredItems, creditItems) { filter ->
                when (filter) {
                    "외상" -> onNavigateToCreditTab()
                    else -> selectedPaymentFilter = if (filter == selectedPaymentFilter) null else filter
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            // 🔴 전체내역 초기화 버튼
                Button(
                onClick = onClearAll,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("전체내역 초기화", color = Color.White)
            }
        }
    }
    
    // 외상관리 다이얼로그
    if (showCreditDialog) {
            CreditManagementDialog(
            creditItems = creditItems,
            onDismiss = { showCreditDialog = false },
            onCreditUpdate = { /* no-op, handled in ViewModel functions */ }
        )
    }
}

// 정산 요약 카드
@Composable
fun SettlementSummaryCard(items: List<SettlementData>, filterType: String?, officeShareRatio: Int, onChangeRatio: (Int) -> Unit, extraCredit: Int) {
    val totalRevenue = items.sumOf { it.fare }
    val totalCashReceived = items.sumOf { it.cashAmount ?: 0 }  // 실제 받은 현금
    val autoCredit = items.filter { it.paymentMethod == "외상" }.sumOf { it.fare }
    val totalCredit = items.sumOf { it.creditAmount } + autoCredit + extraCredit
    val totalPoint = items.sumOf {
        when (it.paymentMethod) {
            "포인트" -> it.fare
            "현금+포인트" -> (it.fare - (it.cashAmount ?: 0))
            else -> 0
        }
    }
    val officeShare = (totalRevenue * officeShareRatio / 100.0).toInt()
    
    var showRatioDialog by remember { mutableStateOf(false) }
    var tempRatio by remember { mutableStateOf(officeShareRatio) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("총수익", color = Color.White, fontWeight = FontWeight.Bold)
                // ⚙ 설정 버튼
                IconButton(onClick = {
                    showRatioDialog = true
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "설정", tint = Color(0xFFFFB000))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("총 운행 건수:", color = Color.White)
                Text("${items.size}건", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("총 매출:", color = Color.White)
                Text("${totalRevenue.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            // ✅ 외상/포인트 표시 (흰색 글씨)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("외상 금액:", color = Color.White)
                Text("${totalCredit.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("포인트 금액:", color = Color.White)
                Text("${totalPoint.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.material3.HorizontalDivider(color = Color(0xFF404040))
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("사무실 수익 (60%):", color = Color(0xFFFFB000))
                Text("${officeShare.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color(0xFFFFB000), fontWeight = FontWeight.Bold)
            }

        if (showRatioDialog) {
        AlertDialog(
                onDismissRequest = { showRatioDialog = false },
                    title = { Text("수익 비율 설정", color = Color.White) },
            text = {
                Column {
                            Text("비율: $tempRatio%", color = Color.White)
                        Slider(
                                value = tempRatio.toFloat(),
                                onValueChange = { tempRatio = ((it/10).toInt()*10).coerceIn(30,90) },
                                valueRange = 30f..90f,
                            steps = 6,
                                colors = SliderDefaults.colors(thumbColor = Color(0xFFFFB000), activeTrackColor = Color(0xFFFFB000))
                    )
                }
            },
            confirmButton = {
                        Button(onClick = {
                            onChangeRatio(tempRatio)
                            showRatioDialog = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) {
                            Text("확인")
                        }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }

            // 실수입 행
            val realIncome = officeShare - totalPoint
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("실수입:", color = Color.White)
                Text("${realIncome.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 결제방법별 카드들
@Composable
fun PaymentMethodCards(items: List<SettlementData>, creditItems: List<CreditItem>, onButtonClick: (String) -> Unit) {
    val paymentSummary = items.groupBy { it.paymentMethod }
    val autoCredit = items.filter { it.paymentMethod == "외상" }.sumOf { it.fare }
    val creditTotal = creditItems.sumOf { it.amount } + autoCredit
    val creditCount = creditItems.size + items.count { it.paymentMethod == "외상" }
    
    Text("결제 내역", color = Color.White, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    
    // 현금
    PaymentMethodCard(
        title = "현금",
        count = paymentSummary["현금"]?.size ?: 0,
        amount = paymentSummary["현금"]?.sumOf { it.fare } ?: 0,
        onButtonClick = { onButtonClick("현금") }
    )
    
    // 카드
    PaymentMethodCard(
        title = "카드",
        count = paymentSummary["카드"]?.size ?: 0,
        amount = paymentSummary["카드"]?.sumOf { it.fare } ?: 0,
        onButtonClick = { onButtonClick("카드") }
    )
    
    // 이체
    PaymentMethodCard(
        title = "이체",
        count = paymentSummary["이체"]?.size ?: 0,
        amount = paymentSummary["이체"]?.sumOf { it.fare } ?: 0,
        onButtonClick = { onButtonClick("이체") }
    )
    
    // 포인트
    PaymentMethodCard(
        title = "포인트",
        count = paymentSummary["포인트"]?.size ?: 0,
        amount = paymentSummary["포인트"]?.sumOf { it.fare } ?: 0,
        onButtonClick = { onButtonClick("포인트") }
    )
    
    // 현금+포인트
    PaymentMethodCard(
        title = "현금+포인트",
        count = paymentSummary["현금+포인트"]?.size ?: 0,
        amount = paymentSummary["현금+포인트"]?.sumOf { it.fare } ?: 0,
        onButtonClick = { onButtonClick("현금+포인트") }
    )
    
    // 외상
    PaymentMethodCard(
        title = "외상",
        count = creditCount,
        amount = creditTotal,
        buttonText = "보기",
        onButtonClick = { onButtonClick("외상") }
    )
}

// 개별 결제방법 카드
@Composable
fun PaymentMethodCard(
    title: String,
    count: Int,
    amount: Int,
    buttonText: String = "보기",
    onButtonClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
            Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${count}건 / ${amount.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color(0xFFAAAAAA))
            }
                    Button(
                onClick = onButtonClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000)),
                modifier = Modifier.height(32.dp)
            ) {
                Text(buttonText, color = Color.Black, fontSize = 12.sp)
            }
        }
    }
}

// 간소화된 일일정산 뷰
@Composable
fun DailySettlementSimple(settlementList: List<SettlementData>, onDateClick: (String) -> Unit, onDateClear: (String) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "일일 정산 요약",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // 날짜별 그룹핑
        val dailyGroups = settlementList.groupBy { it.workDate }.toList().sortedByDescending { it.first }
        
        androidx.compose.foundation.lazy.LazyColumn {
            items(dailyGroups.size) { index ->
                val (date, items) = dailyGroups[index]
                DailySettlementCard(date, items, onDateClick, onDateClear)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

// 일일정산 카드
@Composable
fun DailySettlementCard(date: String, items: List<SettlementData>, onDateClick: (String) -> Unit, onDateClear: (String) -> Unit) {
    val totalRevenue = items.sumOf { it.fare }
    val officeShare = (totalRevenue * 0.6).toInt()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("업무일: $date", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("운행 건수: ${items.size}건", color = Color.White)
                Text("총 매출: ${totalRevenue.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White)
                Text("순수익: ${officeShare.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color(0xFFFFB000), fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                Button(
                    onClick = { onDateClick(date) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000)),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Text("상세보기", color = Color.Black)
                }
                Button(
                    onClick = { onDateClear(date) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("초기화", color = Color.White, fontSize = 12.sp)
                }
            }
        }
    }
}

// 외상관리 다이얼로그
@Composable
fun CreditManagementDialog(
    creditItems: List<CreditItem>,
    onDismiss: () -> Unit,
    onCreditUpdate: (List<CreditItem>) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newCustomerName by remember { mutableStateOf("") }
    var newAmount by remember { mutableStateOf("") }
    var newMemo by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("외상 관리", color = Color.White) },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                // 외상 등록 버튼
                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("외상 등록", color = Color.Black)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("현재 외상 목록 (${creditItems.filter { !it.isCollected }.size}건)", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (creditItems.isEmpty()) {
                    Text("등록된 외상이 없습니다.", color = Color(0xFFAAAAAA))
        } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(150.dp)) {
                        items(creditItems.size) { index ->
                            val credit = creditItems[index]
                            if (!credit.isCollected) {
                                CreditItemCard(credit) {
                                    // 외상 회수 처리
                                    val updatedCredits = creditItems.map { 
                                        if (it.id == credit.id) it.copy(isCollected = true) else it 
                                    }
                                    onCreditUpdate(updatedCredits)
                            }
                        }
                    }
                }
            }
        }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000))
            ) {
                Text("닫기", color = Color.Black)
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )
    
    // 외상 등록 다이얼로그
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("외상 등록", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newCustomerName,
                        onValueChange = { newCustomerName = it },
                        label = { Text("고객명") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFFB000),
                            unfocusedLabelColor = Color(0xFFAAAAAA)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newAmount,
                        onValueChange = { newAmount = it },
                        label = { Text("금액") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFFB000),
                            unfocusedLabelColor = Color(0xFFAAAAAA)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newMemo,
                        onValueChange = { newMemo = it },
                        label = { Text("메모 (선택)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = Color(0xFFFFB000),
                            unfocusedLabelColor = Color(0xFFAAAAAA)
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCustomerName.isNotBlank() && newAmount.isNotBlank()) {
                            val amount = newAmount.toIntOrNull() ?: 0
                            if (amount > 0) {
                                val newCredit = CreditItem(
                                    customerName = newCustomerName,
                                    amount = amount,
                                    date = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date()),
                                    memo = newMemo
                                )
                                onCreditUpdate(creditItems + newCredit)
                                showAddDialog = false
                                newCustomerName = ""
                                newAmount = ""
                                newMemo = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000))
                ) {
                    Text("등록", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("취소", color = Color(0xFFAAAAAA))
                }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }
}

// 외상 아이템 카드
@Composable
fun CreditItemCard(credit: CreditItem, onCollect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(credit.customerName, color = Color.White, fontWeight = FontWeight.Bold)
                Text("${credit.amount.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White)
                Text(credit.date, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                if (credit.memo.isNotBlank()) {
                    Text(credit.memo, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                }
            }
            Button(
                onClick = onCollect,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                modifier = Modifier.height(32.dp)
            ) {
                Text("회수", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

// 기존 테이블과 기사별 정산은 그대로 유지
@Composable
fun TripListTable(tripList: List<SettlementData>, onShowCreditDialog: () -> Unit) {
    var detailDialogState by remember { mutableStateOf(false) }
    var detailDialogTrip by remember { mutableStateOf<SettlementData?>(null) }
    
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("순번", Modifier.weight(0.7f), textAlign = TextAlign.Center, color = Color.White)
            Text("고객", Modifier.weight(1f), color = Color.White)
            Text("출발", Modifier.weight(1f), color = Color.White)
            Text("도착", Modifier.weight(1f), color = Color.White)
            Text("요금", Modifier.weight(1f), color = Color.White)
            Text("결제", Modifier.weight(1f), color = Color.White)
            Spacer(Modifier.weight(1f))
        }
        Divider(color = Color(0xFF404040))
        
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.height(300.dp)
        ) {
            items(tripList.size) { index ->
                val trip = tripList[index]
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text((index + 1).toString(), Modifier.weight(0.7f), textAlign = TextAlign.Center, color = Color.White)
                    Text(trip.customerName.substringBefore("/").take(3), Modifier.weight(1f), color = Color.White)
                    Text(trip.departure.take(3), Modifier.weight(1f), color = Color.White)
                    Text(trip.destination.take(3), Modifier.weight(1f), color = Color.White)
                    Text(trip.fare.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,"), Modifier.weight(1f), color = Color.White)
                    Text(
                        if (trip.paymentMethod == "현금+포인트") "현금+P" else trip.paymentMethod,
                        Modifier.weight(1f),
                        color = Color.White
                    )
                    IconButton(onClick = {
                        detailDialogTrip = trip
                        detailDialogState = true
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Info, contentDescription = "자세히", tint = Color(0xFFFFB000))
                    }
                }
                Divider(color = Color(0xFF404040))
            }
        }
        
        if (detailDialogState && detailDialogTrip != null) {
            val t = detailDialogTrip!!
        AlertDialog(
                onDismissRequest = { detailDialogState = false },
                title = { Text("운행 전체 내역", color = Color.White) },
            text = {
                    Column {
                        Text("기사: ${t.driverName}", color = Color.White)
                        Text("고객: ${t.customerName}", color = Color.White)
                        Text("출발: ${t.departure}", color = Color.White)
                        Text("도착: ${t.destination}", color = Color.White)
                        if (t.waypoints.isNotBlank()) {
                            Text("경유: ${t.waypoints}", color = Color.White)
                        }
                        Text("요금: ${t.fare.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White)
                        Text("결제방법: ${t.paymentMethod}", color = Color.White)
                        if (t.cashAmount != null && t.cashAmount > 0) {
                            Text("현금 금액: ${t.cashAmount.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White)
                        }
                        Text("업무일: ${t.workDate}", color = Color.White)
                }
            },
            confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (t.paymentMethod == "외상") {
                            Button(
                                onClick = {
                                    detailDialogState = false
                                    onShowCreditDialog()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFFB000),
                                    contentColor = Color.Black
                                )
                            ) {
                                Text("외상 관리")
                            }
                        }
                        Button(
                            onClick = { detailDialogState = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFB000),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("닫기")
                        }
                    }
                },
            containerColor = Color(0xFF2A2A2A)
        )
        }
    }
}

// 기사별 정산은 기존 유지
@Composable
fun DriverSettlement(settlementItems: List<SettlementData>, workDate: String) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "기사별 정산 (업무일: $workDate)",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        val driverGroups = settlementItems.groupBy { it.driverName }
        
        if (driverGroups.isEmpty()) {
            Text("정산 데이터가 없습니다.", color = Color.White, textAlign = TextAlign.Center)
        } else {
            androidx.compose.foundation.lazy.LazyColumn {
                items(driverGroups.size) { index ->
                    val (driverName, driverTrips) = driverGroups.toList()[index]
                    DriverSettlementCard(driverName, driverTrips)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun DriverSettlementCard(driverName: String, trips: List<SettlementData>) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$driverName",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        if (expanded) "접기" else "상세보기",
                        color = Color(0xFFFFB000)
                    )
                }
            }
            
            val totalRevenue = trips.sumOf { it.fare }
            val totalCredit = trips.sumOf { it.creditAmount }  // 외상 처리된 금액
            val totalPoint = trips.sumOf {
                when (it.paymentMethod) {
                    "포인트" -> it.fare
                    "현금+포인트" -> (it.fare - (it.cashAmount ?: 0))
                    else -> 0
                }
            }
            val paymentToOffice = (totalRevenue * 0.6).toInt()  // 사무실 납입금
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("운행 건수: ${trips.size}건", color = Color.White)
                Text("총 매출: ${totalRevenue.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White)
            }
            
            // ✅ 외상/포인트 표시 (흰색 글씨)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("외상 금액:", color = Color.White)
                Text("${totalCredit.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("포인트 금액:", color = Color.White)
                Text("${totalPoint.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("납입금 (60%):", color = Color.White)
                Text("${paymentToOffice.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White)
            }

            val realPayment = (paymentToOffice - totalCredit).coerceAtLeast(0)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("실 납입금:", color = Color(0xFFFFB000))
                Text("${realPayment.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color(0xFFFFB000), fontWeight = FontWeight.Bold)
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color(0xFF404040))
                Spacer(modifier = Modifier.height(8.dp))
                
                trips.forEachIndexed { index, trip ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${index + 1}. ${trip.customerName}", color = Color.White, fontSize = 14.sp)
                            Text("${trip.departure} → ${trip.destination}", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${trip.fare.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White, fontSize = 14.sp)
                            Text(trip.paymentMethod, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        }
                    }
                    if (index < trips.size - 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// 외상관리 탭
@Composable
fun CreditManagementTab(viewModel: SettlementViewModel) {
    val creditPersons by viewModel.creditPersons.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("외상 관리", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Button(onClick = { showAddDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000))) {
                Text("외상 등록", color = Color.Black)
            }
        }
        Spacer(Modifier.height(8.dp))

        if (creditPersons.isEmpty()) {
            Text("등록된 외상인이 없습니다.", color = Color.White)
        } else {
            LazyColumn {
                items(creditPersons.size) { idx ->
                    val cp = creditPersons[idx]
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("${cp.name} (${cp.phone})", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("메모: ${cp.memo}", color = Color(0xFFAAAAAA))
                            Text("외상 금액: ${cp.amount.toString().replace(Regex("(\\d)(?=(\\d{3})+(?!\\d))"), "$1,")}원", color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    viewModel.addOrIncrementCredit(cp.name, cp.phone, 10000) // 임시 +1만원
                                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000))) {
                                    Text("외상 더하기", color = Color.Black)
                                }
                                if (cp.amount > 0) {
                                    Button(onClick = { viewModel.reduceCredit(cp.id, cp.amount) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                                        Text("회수", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var amountText by remember { mutableStateOf("") }
        var memo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("외상 등록", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("이름") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("전화번호") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = amountText, onValueChange = { amountText = it.filter { c -> c.isDigit() } }, label = { Text("금액") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = memo, onValueChange = { memo = it }, label = { Text("메모(선택)") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountText.toIntOrNull() ?: 0
                    if (phone.isNotBlank() && amt > 0) {
                        viewModel.addOrIncrementCredit(name.ifBlank { phone }, phone, amt)
                        showAddDialog = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000), contentColor = Color.Black)) {
                    Text("등록")
                }
            },
            dismissButton = {
                Button(onClick = { showAddDialog = false }) { Text("취소") }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }
}

// 세션 히스토리 뷰 (업무 마감 기준)
@Composable
fun DailySessionsSimple(sessions: List<SessionInfo>, onSessionClick: (SessionInfo) -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            "일일 정산 (업무 마감 기준)",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sessions, key = { it.sessionId }) { sess ->
                SessionCard(sess, onSessionClick)
            }
        }
    }
}

@Composable
fun SessionCard(sess: SessionInfo, onSessionClick: (SessionInfo) -> Unit) {
    val endTime = remember(sess.endAt) {
        sess.endAt?.toDate()?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(it)
        } ?: "-"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("종료 시각: $endTime", color = Color.White, fontWeight = FontWeight.Bold)
                Text("운행 ${sess.totalTrips}건 · 총 ${java.text.NumberFormat.getNumberInstance().format(sess.totalFare)}원", color = Color.White)
            }
            Button(onClick = { onSessionClick(sess) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB000))) {
                Text("상세", color = Color.Black)
            }
        }
    }
}


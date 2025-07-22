package com.designated.callmanager.ui.settlement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import android.util.Log
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign

// 필요한 경우 다른 Compose 관련 import 문 추가
// import com.designated.callmanager.data.CreditItem // <--- 이 줄을 삭제합니다.
import com.designated.callmanager.data.SettlementData // SettlementData 데이터 클래스 import
import com.designated.callmanager.data.Constants
import java.text.SimpleDateFormat
import java.util.*

// CreditPopupHandler 타입 정의 (함수 타입 추론 불가 오류 해결)
typealias CreditPopupHandler = (customerName: String?, amount: Int?, customerPhone: String?, settlementId: String?) -> Unit

// 이 Composable 함수는 ViewModel 인스턴스와 네비게이션 액션을 인자로 받습니다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementScreen(
    viewModel: SettlementViewModel,
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    regionId: String, // ViewModel 초기화를 위한 regionId
    officeId: String  // ViewModel 초기화를 위한 officeId
) {
    // ViewModel의 StateFlow들을 collectAsState로 관찰합니다.
    val settlementList by viewModel.settlementList.collectAsState()
    val allSettlements by viewModel.allSettlementList.collectAsState()
    val allTripsCleared by viewModel.allTripsCleared.collectAsState() // ViewModel에서 isCleared를 대체
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val creditPersons by viewModel.creditPersons.collectAsState()

    // 운행 상세 팝업 상태
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedSettlement by remember { mutableStateOf<SettlementData?>(null) }
    var showDateDialog by remember { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf<String?>(null) }

    // UI 상태 (탭 인덱스, 다이얼로그 표시 여부 등)
    var selectedTabIndex by remember { mutableIntStateOf(1) } // '전체내역' 탭이 1번 인덱스라고 가정
    val tabItems = listOf("정산 대기", "전체내역", "기사별 정산", "일일 정산", "외상 고객")
    val tabBackgroundColor = Color(0xFFFFB000)
    val tabContentColor = Color.Black

    // '업무 마감' 확인 다이얼로그 상태
    var showClearConfirm by remember { mutableStateOf(false) }

    // '외상 팝업' 관련 상태
    var showCreditPopup by remember { mutableStateOf(false) }
    var focusCustomerName by remember { mutableStateOf<String?>(null) }
    var focusAmount by remember { mutableIntStateOf(0) } // Int? -> Int로 변경, 0으로 초기화
    var focusPhone by remember { mutableStateOf<String?>(null) }
    var pendingSettlementId by remember { mutableStateOf<String?>(null) }


    // ViewModel 초기화 로직
    LaunchedEffect(regionId, officeId) {
        viewModel.initialize(regionId, officeId)
    }

    // 현재 날짜 계산 (ViewModel의 calculateWorkDate 사용)
    val todayWorkDate = remember {
        viewModel.calculateWorkDate(System.currentTimeMillis())
    }

    // 에러 메시지 팝업
    if (error != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("오류 발생") },
            text = { Text(error!!) },
            confirmButton = {
                Button(onClick = { viewModel.clearError() }) {
                    Text("확인")
                }
            }
        )
    }

    // '업무 마감' 확인 다이얼로그
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("업무 마감 확인") },
            text = { Text("오늘의 정산 내역을 마감하고 초기화하시겠습니까?\n이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTrips() // ViewModel의 clearAllTrips 호출
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4444),
                        contentColor   = Color.White
                    )
                ) { Text("마감하기") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("취소") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box(Modifier.fillMaxWidth()) {
                        Text(
                            "정산관리",
                            modifier = Modifier.align(Alignment.Center),
                            style    = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateHome) {
                        Icon(Icons.Filled.Home, contentDescription = "홈", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor      = Color(0xFF1A1A1A),
                    titleContentColor   = Color.White,
                    actionIconContentColor= Color.White
                )
            )
        },
        containerColor = Color(0xFF121212)
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            /* ---------- 탭 UI ---------- */
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor   = tabBackgroundColor,
                contentColor     = tabContentColor
            ) {
                tabItems.forEachIndexed { idx, title ->
                    Tab(
                        selected = selectedTabIndex == idx,
                        onClick  = { selectedTabIndex = idx },
                        text     = { Text(title, color = Color.Black) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            /* ---------- 콘텐츠 ---------- */
            when {
                isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFFFB000))
                }
                error != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("오류: $error", color = MaterialTheme.colorScheme.error)
                }
                else -> when (selectedTabIndex) {
                    0 -> PendingSettlementsView(
                        settlementItems = settlementList,
                        onMarkSettled   = viewModel::markSettlementSettled,
                        onCreditManage  = { sett ->
                            Log.d("SettlementScreen","Clicked credit manage for ${sett.customerName}")
                            focusCustomerName = sett.customerName
                            focusAmount = sett.fare
                            focusPhone = sett.customerPhone
                            pendingSettlementId = sett.settlementId
                                showCreditPopup = true
                            Log.d("SettlementScreen","showCreditPopup set to true")
                        }
                    )
                    1 -> Spacer(Modifier.height(0.dp)) // 리스트는 아래 Card 에서 표시
                        2 -> DriverSettlement(
                        settlementItems  = allSettlements,
                        workDate         = todayWorkDate,
                        officeShareRatio = viewModel.officeShareRatio.collectAsState().value,
                        onRatioChange    = { viewModel.updateOfficeShareRatio(it) }
                        )
                        3 -> DailySettlementSimple(
                        settlementList = allSettlements,
                        onDateClick    = { date ->
                            selectedDate = date
                            showDateDialog = true
                        }
                    )
                    4 -> CreditManagementTab(viewModel = viewModel, allSettlements = allSettlements)
                }
            }

            Spacer(Modifier.height(16.dp))

            /* ▶ ‘전체내역’ 탭 전용 카드 + 마감 버튼*/
            if (selectedTabIndex == 1) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                ) {
                    TripListTable(
                        tripList      = allSettlements,
                        onShowDetail  = { settlement ->
                            selectedSettlement = settlement
                            showDetailDialog = true
                        },
                        onMarkSettled = viewModel::markSettlementSettled
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { showClearConfirm = true },
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF4444),
                        contentColor   = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    enabled  = !allTripsCleared
                ) { Text("업무 마감") }
            }
        }

        /* ---------- 외상 팝업 ---------- */
        Log.d("SettlementScreen","Composing CreditPopup: $showCreditPopup")
        if (showCreditPopup) {
            CreditManagementDialog(
                viewModel           = viewModel,
                creditItems         = if(creditPersons.isEmpty()) emptyList() else creditPersons.map {
                    CreditItem(
                        id          = it.id,
                        customerName= it.name,
                        amount      = it.amount,
                        date        = "",
                        memo        = it.memo,
                        phone       = it.phone
                    )
                },
                focusCustomerName   = focusCustomerName,
                focusAmount         = focusAmount,
                focusPhone          = focusPhone,
                pendingSettlementId = pendingSettlementId,
                onDismiss           = {
                    showCreditPopup     = false
                    focusCustomerName   = null
                    focusAmount         = 0 // Int? -> Int로 변경
                    focusPhone          = null
                    pendingSettlementId = null
                },
                onSettlementComplete = { sid ->
                    viewModel.markSettlementSettled(sid)
                    showCreditPopup     = false
                    focusCustomerName   = null
                    focusAmount         = 0 // Int? -> Int로 변경
                    focusPhone          = null
                    pendingSettlementId = null
                }
            )
        }

        /* ---------- 운행 상세 팝업 ---------- */
        if (showDetailDialog && selectedSettlement != null) {
            TripDetailDialog(settlement = selectedSettlement!!) {
                showDetailDialog = false
                selectedSettlement = null
            }
        }

        if (showDateDialog && selectedDate != null) {
            val listForDate = allSettlements.filter { it.workDate == selectedDate }
            DateDetailDialog(date = selectedDate!!, settlements = listForDate) {
                showDateDialog = false
                selectedDate = null
            }
        }
    }
}

// Legacy wrapper for existing call-sites (regionId / officeId first)
@Composable
fun SettlementScreen(
    regionId: String?,
    officeId: String?,
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit
) {
    val vm: SettlementViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    SettlementScreen(
        viewModel = vm,
        onNavigateBack = onNavigateBack,
        onNavigateHome = onNavigateHome,
        regionId = regionId ?: "",
        officeId = officeId ?: ""
    )
}

@Composable
fun PendingSettlementsView(
    settlementItems: List<SettlementData>, 
    onMarkSettled: (String) -> Unit,
    onCreditManage: (SettlementData) -> Unit
) {
    val filtered = remember(settlementItems) { settlementItems.filter { it.paymentMethod in listOf("이체", "외상") } }
    if (filtered.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("정산 대기 내역이 없습니다.", color = Color.White)
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = filtered,
            key = { it.settlementId }
        ) { settlement ->
            SettlementPendingCard(
                settlement = settlement,
                onMarkSettled = onMarkSettled,
                onCreditManage = onCreditManage
            )
        }
    }
}

@Composable
private fun SettlementPendingCard(
    settlement: SettlementData,
    onMarkSettled: (String) -> Unit,
    onCreditManage: (SettlementData) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))
    ) {
    Column(
        modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${settlement.customerName} (${settlement.paymentMethod})",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            Text(
                    "${java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).format(settlement.fare)}원",
                color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text("기사: ${settlement.driverName}", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            if (settlement.departure.isNotBlank() || settlement.destination.isNotBlank()) {
                Text(
                    "${settlement.departure} ➜ ${settlement.destination}",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (settlement.paymentMethod == "이체") {
                    Button(
                        onClick = { onMarkSettled(settlement.settlementId) },
                        modifier = Modifier.weight(1f)
                    ) { Text("정산 확인") }
                }
                if (settlement.paymentMethod == "외상") {
                    OutlinedButton(
                        onClick = { onCreditManage(settlement) },
                        modifier = Modifier.weight(1f)
                    ) { Text("외상 관리") }
                }
            }
        }
    }
}

@Composable
fun TripListTable(
    tripList: List<SettlementData>,
    onShowDetail: (SettlementData) -> Unit,
    onMarkSettled: (String) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    if (tripList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("정산 내역이 없습니다.", color = Color.White)
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 12.dp)
        ) {
            Text("No",     modifier = Modifier.weight(0.5f), color = Color.Yellow)
            Text("고객",   modifier = Modifier.weight(1.2f), color = Color.Yellow)
            Text("기사",   modifier = Modifier.weight(1f),   color = Color.Yellow)
            Text("금액",   modifier = Modifier.weight(1f).padding(end = 6.dp), color = Color.Yellow, textAlign = TextAlign.End)
            Text("결제",   modifier = Modifier.weight(0.8f), color = Color.Yellow, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.weight(0.3f)) // 아이콘 칸
        }
        Divider(color = Color.DarkGray)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(items = tripList, key = { _, it -> it.settlementId }) { idx, settlement ->
                TripListRowWithIndex(index = idx + 1, settlement = settlement, dateFormat = dateFormat, onShowDetail = onShowDetail, onMarkSettled = onMarkSettled)
            }
        }
    }
}

@Composable
private fun TripListRowWithIndex(
    index: Int,
    settlement: SettlementData,
    dateFormat: SimpleDateFormat,
    onShowDetail: (SettlementData) -> Unit,
    onMarkSettled: (String) -> Unit
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onShowDetail(settlement) }
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$index", modifier = Modifier.weight(0.5f), color = Color.White)
            Text(settlement.customerName.take(3), modifier = Modifier.weight(1.2f), color = Color.White)
            Text(settlement.driverName.take(3), modifier = Modifier.weight(1f), color = Color.White)
            Text(java.text.NumberFormat.getNumberInstance().format(settlement.fare), modifier = Modifier.weight(1f).padding(end = 6.dp), color = Color.White, textAlign = TextAlign.Right)
            Text(settlement.paymentMethod, modifier = Modifier.weight(0.8f), color = Color.White, textAlign = TextAlign.Center)

            IconButton(onClick = { onShowDetail(settlement) }) {
                Icon(Icons.Filled.Info, contentDescription = "상세", tint = Color.White)
            }
        }

        // 두 번째 줄: 경로
        if (settlement.departure.isNotBlank() || settlement.destination.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp)) {
                Text("${settlement.departure} ➜ ${settlement.destination}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun DriverSettlement(
    settlementItems: List<SettlementData>,
    workDate: String,
    officeShareRatio: Int,
    onRatioChange: (Int)->Unit
) {
    val grouped = remember(settlementItems) {
        settlementItems.groupBy { it.driverName.ifBlank { "미지정" } }
            .mapValues { entry ->
                val sum = entry.value.sumOf { it.fare }
                val creditSum = entry.value.filter { it.paymentMethod == "외상" }.sumOf { it.creditAmount }
                Triple(sum, entry.value.size, creditSum)
            }
            .toList()
            .sortedByDescending { it.second.first }
    }

    var showRatioDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("$workDate 기사별 정산 합계", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("납입 비율: $officeShareRatio%", color = Color.White)
            IconButton(onClick = { showRatioDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = "비율 설정", tint = Color.White)
            }
        }
        if (showRatioDialog) {
        AlertDialog(
                onDismissRequest = { showRatioDialog = false },
                title = { Text("사무실 납입 비율 조정", color = Color.White) },
            text = {
                Column {
                        Text("현재: $officeShareRatio%", color = Color.White)
                        Slider(
                            value = officeShareRatio.toFloat(),
                            onValueChange = { newVal ->
                                val rounded = (newVal / 5f).roundToInt() * 5
                                onRatioChange(rounded.coerceIn(40, 70))
                            },
                            valueRange = 40f..70f,
                            steps = 6,
                            onValueChangeFinished = { /* snap handled */ },
                            colors = SliderDefaults.colors(thumbColor = Color.Yellow, activeTrackColor = Color.Yellow)
                    )
                }
            },
            confirmButton = {
                    Button(onClick = { showRatioDialog = false }) { Text("확인") }
            },
            containerColor = Color(0xFF2A2A2A)
        )
    }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(grouped) { (driver, triple) ->
                val (totalFare, count, creditSum) = triple
                val payable = (totalFare * officeShareRatio) / 100
                val finalPay = payable - creditSum
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                        Column {
                            Text(driver, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("$count 건", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("총금액: " + java.text.NumberFormat.getNumberInstance().format(totalFare) + "원", color = Color.White)
                            Text("납입액(${officeShareRatio}%): " + java.text.NumberFormat.getNumberInstance().format(payable) + "원", color = Color.White)
                            Text("외상액: " + java.text.NumberFormat.getNumberInstance().format(creditSum) + "원", color = Color.White)
                            Text("최종 납입: " + java.text.NumberFormat.getNumberInstance().format(finalPay) + "원", color = Color.Yellow, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailySettlementSimple(
    settlementList: List<SettlementData>,
    onDateClick: (String) -> Unit
) {
    val grouped = remember(settlementList) {
        settlementList.groupBy { it.workDate.ifBlank { "알수없음" } }
            .mapValues { entry ->
                val total = entry.value.sumOf { it.fare }
                val creditTotal = entry.value.filter { it.paymentMethod == "외상" }.sumOf { it.creditAmount }
                val count = entry.value.size
                Triple(total, creditTotal, count)
            }
            .toList()
            .sortedByDescending { it.first }
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(grouped) { (date, triple) ->
            val (total, creditTotal, count) = triple
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                onClick = { onDateClick(date) }) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                            Text(date, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("$count 건", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("총금액: ${java.text.NumberFormat.getNumberInstance().format(total)}원", color = Color.White)
                            if (creditTotal > 0) {
                                Text("외상: ${java.text.NumberFormat.getNumberInstance().format(creditTotal)}원", color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { onDateClick(date) }) { Text("상세") }
                    }
                }
            }
        }
    }
}

@Composable
fun CreditManagementTab(viewModel: SettlementViewModel, allSettlements: List<SettlementData>) {
    val creditPersons by viewModel.creditPersons.collectAsState()
    var showCollectDialog by remember { mutableStateOf(false) }
    var selectedCredit by remember { mutableStateOf<CreditPerson?>(null) }
    var showCreditDetail by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("외상 고객", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        if (creditPersons.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("외상 고객이 없습니다.", color = Color.White) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(creditPersons, key = { it.id }) { cp ->
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(cp.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(cp.phone, color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(java.text.NumberFormat.getNumberInstance().format(cp.amount)+"원", color = Color.White)
                                Button(onClick = { selectedCredit = cp; showCollectDialog = true }) { Text("회수") }
                                OutlinedButton(onClick = { selectedCredit = cp; showCreditDetail = true }) { Text("상세") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCollectDialog && selectedCredit != null) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCollectDialog = false; selectedCredit = null },
            title = { Text("외상 회수", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("고객: ${selectedCredit!!.name}", color = Color.White)
                    Text("외상 금액: ${java.text.NumberFormat.getNumberInstance().format(selectedCredit!!.amount)}원", color = Color.White)
                    OutlinedTextField(value = input, onValueChange = { input = it.filter { c->c.isDigit() } }, label = { Text("회수 금액", color = Color.White) },
                        trailingIcon = {
                            if(input.isNotEmpty()) {
                                IconButton(onClick = { input = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "clear")
                                }
                            }
                        },
                        )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = input.toIntOrNull() ?: 0
                    if (amt>0) viewModel.reduceCredit(selectedCredit!!.id, amt)
                    showCollectDialog = false
                    selectedCredit = null
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showCollectDialog = false; selectedCredit = null }) { Text("취소") } },
            containerColor = Color(0xFF2A2A2A)
        )
    }

    /* 상세 내역 다이얼로그 */
    if (showCreditDetail && selectedCredit != null) {
        val records = allSettlements.filter { it.customerName == selectedCredit!!.name && it.paymentMethod == "외상" }
        AlertDialog(
            onDismissRequest = { showCreditDetail = false; selectedCredit = null },
            title = { Text("${selectedCredit!!.name} 외상 내역", color = Color.White) },
            text = {
                Column(modifier = Modifier.heightIn(max = 450.dp)) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(records) { rec ->
                            Column {
                                Text("${rec.workDate.takeLast(5)}  |  ${rec.driverName}  |  ${java.text.NumberFormat.getNumberInstance().format(rec.fare)}원", color = Color.White)
                                if(rec.departure.isNotBlank() || rec.destination.isNotBlank()) {
                                    Text("${rec.departure} ➜ ${rec.destination}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    /* 공유 기능: 간단히 텍스트 구성 */
                    val msg = buildString {
                        append("${selectedCredit!!.name} 외상 내역\n")
                        records.forEach { r ->
                            append("${r.workDate.takeLast(5)} ${r.fare}원\n")
                        }
                        append("총 ${java.text.NumberFormat.getNumberInstance().format(records.sumOf { r -> r.fare })}원")
                    }
                    val sendIntent = android.content.Intent().apply {
                        action = android.content.Intent.ACTION_SEND
                        putExtra(android.content.Intent.EXTRA_TEXT, msg)
                        type = "text/plain"
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, "공유"))
                }) { Text("공유") }
            },
            dismissButton = { TextButton(onClick = { showCreditDetail = false; selectedCredit = null }) { Text("닫기") } },
            containerColor = Color(0xFF2A2A2A)
        )
    }
}

@Composable
fun CreditManagementDialog(
    viewModel: SettlementViewModel,
    creditItems: List<CreditItem>,
    focusCustomerName: String?,
    focusAmount: Int?,
    focusPhone: String?,
    pendingSettlementId: String?,
    onDismiss: () -> Unit,
    onSettlementComplete: (String) -> Unit
) {
    var amountText by remember { mutableStateOf(focusAmount?.toString() ?: "") }
    var memoText by remember { mutableStateOf("") }

        AlertDialog(
        onDismissRequest = onDismiss,
            title = { Text("외상 등록", color = Color.White) },
            text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("고객명: ${focusCustomerName ?: ""}", color = Color.White)
                Text("전화번호: ${focusPhone ?: ""}", color = Color.White)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c->c.isDigit() } },
                    label = { Text("금액", color = Color.White) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = memoText,
                    onValueChange = { memoText = it },
                    label = { Text("메모(선택)", color = Color.White) }
                )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountText.toIntOrNull() ?: 0
                if(amt>0) {
                    viewModel.addOrIncrementCredit(
                        focusCustomerName ?: "고객",
                        focusPhone ?: "",
                        amt,
                        memoText
                    )
                    onSettlementComplete(pendingSettlementId ?: "")
                }
            }) { Text("등록") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
            containerColor = Color(0xFF2A2A2A)
        )
}

// Data class if absent
data class CreditItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val customerName: String,
    val amount: Int,
    val date: String,
    val memo: String = "",
    val phone: String = "",
    val isCollected: Boolean = false
)

@Composable
fun TripDetailDialog(settlement: SettlementData, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("운행 상세 정보", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("고객명: ${settlement.customerName}", color = Color.White)
                Text("기사명: ${settlement.driverName}", color = Color.White)
                Text("출발지: ${settlement.departure}", color = Color.White)
                Text("도착지: ${settlement.destination}", color = Color.White)
                Text("경유: ${settlement.waypoints}", color = Color.White)
                Text("요금: ${java.text.NumberFormat.getNumberInstance().format(settlement.fare)}원", color = Color.White)
                Text("결제: ${settlement.paymentMethod}", color = Color.White)
                val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()) }
                Text("완료시각: ${sdf.format(java.util.Date(settlement.completedAt))}", color = Color.White)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("닫기") }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}

@Composable
fun DateDetailDialog(date: String, settlements: List<SettlementData>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$date 상세 내역", color = Color.White) },
        text = {
            Column(modifier = Modifier.heightIn(max = 450.dp).fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No", modifier = Modifier.weight(0.5f), color = Color.Yellow, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("고객", modifier = Modifier.weight(1f), color = Color.Yellow, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("기사", modifier = Modifier.weight(1f), color = Color.Yellow, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("요금", modifier = Modifier.weight(1f), color = Color.Yellow, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Text("결제", modifier = Modifier.weight(1f), color = Color.Yellow, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Divider(color = Color.DarkGray)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(settlements) { idx, s ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${idx+1}", modifier = Modifier.weight(0.5f), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(s.customerName.take(3), modifier = Modifier.weight(1f), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(s.driverName, modifier = Modifier.weight(1f), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(java.text.NumberFormat.getNumberInstance().format(s.fare), modifier = Modifier.weight(1f), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Text(s.paymentMethod, modifier = Modifier.weight(1f), color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                            Text("${s.departure} → ${s.destination}", modifier = Modifier.fillMaxWidth(), color = Color.LightGray, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Divider(color = Color(0xFF333333))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("닫기") } },
        containerColor = Color(0xFF2A2A2A)
    )
}
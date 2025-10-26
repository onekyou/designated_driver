package com.designated.callmanager.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.CustomerInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerManagementScreen(
    regionId: String,
    officeId: String,
    viewModel: CustomerManagementViewModel = viewModel(),
    onNavigateBack: () -> Unit,
    onCustomerClick: (CustomerInfo) -> Unit = {}
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val selectedGradeFilter by viewModel.selectedGradeFilter.collectAsStateWithLifecycle()
    val selectedActivityFilter by viewModel.selectedActivityFilter.collectAsStateWithLifecycle()
    val customerStats by viewModel.customerStats.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteResultMessage by remember { mutableStateOf<String?>(null) }
    var selectedCustomer by remember { mutableStateOf<CustomerInfo?>(null) }
    var showCustomerDetail by remember { mutableStateOf(false) }

    LaunchedEffect(regionId, officeId) {
        if (regionId.isNotBlank() && officeId.isNotBlank()) {
            viewModel.fetchCustomers(regionId, officeId)
        }
    }

    // 스크롤 끝 감지하여 더 불러오기
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && lastVisibleIndex >= customers.size - 3) {
                    viewModel.loadMoreCustomers(regionId, officeId)
                }
            }
    }

    // 에러 메시지 스낵바
    errorMessage?.let { message ->
        LaunchedEffect(message) {
            // 스낵바 표시 로직 (실제로는 SnackbarHost와 연동)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("회원 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    // 휴면 회원 삭제 버튼
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "휴면 회원 삭제")
                    }
                    // 새로고침 버튼
                    IconButton(onClick = { viewModel.fetchCustomers(regionId, officeId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        },
        snackbarHost = {
            deleteResultMessage?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(message)
                }
                LaunchedEffect(message) {
                    kotlinx.coroutines.delay(3000)
                    deleteResultMessage = null
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // 통계 요약 카드
            customerStats?.let { stats ->
                CustomerStatsCard(stats = stats)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 검색 바
            SearchBar(
                query = searchQuery,
                onQueryChange = { /* 실시간 검색은 성능상 제외 */ },
                onSearch = { query ->
                    keyboardController?.hide()
                    if (query.isNotBlank()) {
                        viewModel.searchCustomer(regionId, officeId, query)
                    } else {
                        viewModel.clearSearch(regionId, officeId)
                    }
                },
                onClearSearch = {
                    viewModel.clearSearch(regionId, officeId)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 등급 필터 칩
            GradeFilterChips(
                selectedGrade = selectedGradeFilter,
                onGradeSelected = { grade ->
                    viewModel.filterByGrade(regionId, officeId, grade)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 활동 상태 필터 칩
            ActivityFilterChips(
                selectedStatus = selectedActivityFilter,
                customers = customers,
                onStatusSelected = { status ->
                    viewModel.filterByActivityStatus(regionId, officeId, status)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 고객 목록
            if (isLoading && customers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (customers.isEmpty()) {
                EmptyCustomerState(
                    hasSearchQuery = searchQuery.isNotBlank(),
                    hasGradeFilter = selectedGradeFilter != null
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(customers) { customer ->
                        CustomerCard(
                            customer = customer,
                            onClick = {
                                selectedCustomer = customer
                                showCustomerDetail = true
                            }
                        )
                    }

                    // 더 불러오기 로딩 표시
                    if (isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // 회원 상세 정보 BottomSheet
        if (showCustomerDetail && selectedCustomer != null) {
            CustomerDetailBottomSheet(
                customer = selectedCustomer!!,
                onDismiss = {
                    showCustomerDetail = false
                    selectedCustomer = null
                }
            )
        }

        // 휴면 회원 삭제 확인 다이얼로그
        if (showDeleteDialog) {
            DormantDeleteConfirmDialog(
                dormantCount = customers.count { it.getActivityStatus() == "dormant" },
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.deleteDormantCustomers(regionId, officeId) { deletedCount ->
                        deleteResultMessage = if (deletedCount > 0) {
                            "휴면 회원 ${deletedCount}명이 삭제되었습니다"
                        } else {
                            "삭제할 휴면 회원이 없습니다"
                        }
                    }
                },
                onDismiss = {
                    showDeleteDialog = false
                }
            )
        }
    }
}

@Composable
fun CustomerStatsCard(stats: com.designated.callmanager.service.CustomerStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                "회원 현황",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    StatItem("총 회원", "${stats.totalCustomers}명")
                }
                item {
                    StatItem("활성 회원", "${stats.activeCustomers}명")
                }
                item {
                    StatItem("평균 이용", String.format("%.1f", stats.averageRides) + "회")
                }
                item {
                    StatItem("총 포인트", "${stats.totalPoints}P")
                }
            }

            // 활동 상태 통계 (있는 경우)
            if (stats.activityDistribution.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "활동 상태",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        ActivityStatItem(
                            "🟢 활성",
                            "${stats.activityDistribution["active"] ?: 0}명",
                            Color(0xFF4CAF50)
                        )
                    }
                    item {
                        ActivityStatItem(
                            "🟡 주의",
                            "${stats.activityDistribution["warning"] ?: 0}명",
                            Color(0xFFFFC107)
                        )
                    }
                    item {
                        ActivityStatItem(
                            "🔴 휴면",
                            "${stats.activityDistribution["dormant"] ?: 0}명",
                            Color(0xFFF44336)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActivityStatItem(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    var localQuery by remember { mutableStateOf(query) }

    LaunchedEffect(query) {
        localQuery = query
    }

    OutlinedTextField(
        value = localQuery,
        onValueChange = {
            localQuery = it
            onQueryChange(it)
        },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("전화번호로 검색") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = "검색")
        },
        trailingIcon = {
            if (localQuery.isNotEmpty()) {
                IconButton(onClick = {
                    localQuery = ""
                    onClearSearch()
                }) {
                    Icon(Icons.Default.Clear, contentDescription = "지우기")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Search
        ),
        keyboardActions = KeyboardActions(
            onSearch = { onSearch(localQuery) }
        ),
        singleLine = true
    )
}

@Composable
fun GradeFilterChips(
    selectedGrade: String?,
    onGradeSelected: (String?) -> Unit
) {
    val grades = listOf(
        null to "전체",
        "bronze" to "🥉 브론즈",
        "silver" to "🥈 실버",
        "gold" to "🥇 골드",
        "vip" to "⭐ VIP"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        grades.forEach { (grade, label) ->
            FilterChip(
                selected = selectedGrade == grade,
                onClick = {
                    android.util.Log.d("GradeFilter", "Clicked: $grade")
                    onGradeSelected(grade)
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun ActivityFilterChips(
    selectedStatus: String?,
    customers: List<CustomerInfo>,
    onStatusSelected: (String?) -> Unit
) {
    val activeCount = customers.count { it.getActivityStatus() == "active" }
    val warningCount = customers.count { it.getActivityStatus() == "warning" }
    val dormantCount = customers.count { it.getActivityStatus() == "dormant" }

    val statuses = listOf(
        null to "전체 (${customers.size})",
        "active" to "🟢 활성 ($activeCount)",
        "warning" to "🟡 주의 ($warningCount)",
        "dormant" to "🔴 휴면 ($dormantCount)"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        statuses.forEach { (status, label) ->
            FilterChip(
                selected = selectedStatus == status,
                onClick = {
                    android.util.Log.d("ActivityFilter", "Clicked: $status")
                    onStatusSelected(status)
                },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun CustomerCard(
    customer: CustomerInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 등급 배지
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GradeBadge(grade = customer.grade)
                ActivityStatusBadge(status = customer.getActivityStatus())
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 고객 정보
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    customer.name.ifEmpty { "이름 없음" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    customer.phoneNumber,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (customer.totalRides > 0) {
                    Text(
                        "이용 ${customer.totalRides}회 • ${customer.points}P",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 어트리뷰션 점수 (있는 경우)
            customer.attributionScore?.let { score ->
                CustomerAttributionScoreBadge(score = score)
            }

            // 화살표 아이콘
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "상세보기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun GradeBadge(grade: String) {
    val (emoji, text, color) = when (grade) {
        "bronze" -> Triple("🥉", "브론즈", Color(0xFFCD7F32))
        "silver" -> Triple("🥈", "실버", Color(0xFFC0C0C0))
        "gold" -> Triple("🥇", "골드", Color(0xFFFFD700))
        "vip" -> Triple("⭐", "VIP", Color(0xFFFF6B35))
        else -> Triple("", "기본", MaterialTheme.colorScheme.outline)
    }

    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                color.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            "$emoji $text",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CustomerAttributionScoreBadge(score: Int) {
    val (color, text) = if (score >= 70) {
        Pair(MaterialTheme.colorScheme.primary, "신뢰")
    } else {
        Pair(MaterialTheme.colorScheme.outline, "일반")
    }

    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "${score}점",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ActivityStatusBadge(status: String) {
    val (emoji, text, color) = when (status) {
        "active" -> Triple("🟢", "활성", Color(0xFF4CAF50))
        "warning" -> Triple("🟡", "주의", Color(0xFFFFC107))
        "dormant" -> Triple("🔴", "휴면", Color(0xFFF44336))
        else -> Triple("", "알수없음", MaterialTheme.colorScheme.outline)
    }

    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                color.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            "$emoji $text",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp
        )
    }
}

@Composable
fun EmptyCustomerState(
    hasSearchQuery: Boolean,
    hasGradeFilter: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.People,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            when {
                hasSearchQuery -> "검색 결과가 없습니다"
                hasGradeFilter -> "해당 등급의 회원이 없습니다"
                else -> "등록된 회원이 없습니다"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (!hasSearchQuery && !hasGradeFilter) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "고객앱이나 랜딩페이지를 통해\n첫 번째 회원을 유치해보세요",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailBottomSheet(
    customer: CustomerInfo,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        customer.name.ifEmpty { "이름 없음" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        customer.phoneNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    GradeBadge(grade = customer.grade)
                    ActivityStatusBadge(status = customer.getActivityStatus())
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 통계 정보
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "이용 정보",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailInfoRow("총 이용 횟수", "${customer.totalRides}회")
                    DetailInfoRow("총 사용 금액", "${String.format("%,d", customer.totalSpent)}원")
                    DetailInfoRow("보유 포인트", "${customer.points}P")

                    customer.attributionScore?.let { score ->
                        DetailInfoRow("신뢰도 점수", "${score}점")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 활동 정보
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "활동 정보",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailInfoRow("가입일", formatTimestamp(customer.registeredAt))
                    customer.lastRideAt?.let {
                        DetailInfoRow("마지막 이용", formatTimestamp(it))
                    }
                    customer.lastActiveAt?.let {
                        DetailInfoRow("마지막 접속", formatTimestamp(it))
                        DetailInfoRow("비활성 기간", "${customer.getDaysSinceActive()}일")
                    }
                    customer.attributionSource?.let {
                        DetailInfoRow("유입 경로", when(it) {
                            "landing" -> "랜딩페이지"
                            "qr_scan" -> "QR 코드"
                            "referral" -> "추천"
                            else -> it
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 닫기 버튼
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("닫기")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
    val date = java.util.Date(timestamp.seconds * 1000)
    val formatter = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA)
    return formatter.format(date)
}

@Composable
fun DormantDeleteConfirmDialog(
    dormantCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("휴면 회원 삭제")
        },
        text = {
            Column {
                Text(
                    "90일 이상 앱을 사용하지 않은 휴면 회원을 삭제합니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "삭제 대상: ${dormantCount}명",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "이 작업은 되돌릴 수 없습니다. 계속하시겠습니까?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                enabled = dormantCount > 0
            ) {
                Text("삭제")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
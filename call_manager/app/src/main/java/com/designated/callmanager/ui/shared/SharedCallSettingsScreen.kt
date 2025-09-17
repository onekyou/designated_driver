package com.designated.callmanager.ui.shared

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.PointTransaction
import com.designated.callmanager.ui.dashboard.DashboardViewModel
import com.designated.callmanager.ui.dashboard.ClosingSettlement
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedCallSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val pointsInfo by viewModel.pointsInfo.collectAsState()
    val pointTransactions by viewModel.pointTransactions.collectAsState()
    val closingSettlements by viewModel.closingSettlements.collectAsState()

    val (selectedTabIndex, setSelectedTabIndex) = remember { mutableStateOf(0) }

    BackHandler {
        onNavigateBack()
    }

    LaunchedEffect(Unit) {
        // 마감정산 데이터 로드
        viewModel.loadClosingSettlements()
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("공유 콜 관리") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "현재 포인트 잔액",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${pointsInfo?.balance ?: 0} P",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    pointsInfo?.updatedAt?.let { timestamp ->
                        Text(
                            "마지막 업데이트: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(timestamp.toDate())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { setSelectedTabIndex(0) },
                    text = { Text("포인트 내역") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { setSelectedTabIndex(1) },
                    text = { Text("마감정산") }
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { setSelectedTabIndex(2) },
                    text = { Text("테스트") }
                )
            }

            LazyColumn(Modifier.fillMaxSize()) {
                when (selectedTabIndex) {
                    0 -> {
                        if (pointTransactions.isEmpty()) {
                            item {
                                EmptyStateMessage("포인트 거래 내역이 없습니다.")
                            }
                        } else {
                            items(pointTransactions, key={it.id}) { transaction ->
                                PointTransactionRow(transaction)
                            }
                        }
                    }
                    1 -> {
                        if (closingSettlements.isEmpty()) {
                            item {
                                EmptyStateMessage("마감정산 내역이 없습니다.")
                            }
                        } else {
                            items(closingSettlements, key={it.date}) { settlement ->
                                ClosingSettlementRow(settlement)
                            }
                        }
                    }
                    2 -> {
                        item {
                            TestSection(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TestSection(viewModel: DashboardViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "포인트 시스템 테스트",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "테스트 거래를 생성하여 포인트 시스템의 정합성을 확인할 수 있습니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.createTestPointTransaction(
                            amount = 1000,
                            type = "CHARGE",
                            description = "테스트 충전 +1000P"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("충전 테스트")
                }

                Button(
                    onClick = {
                        viewModel.createTestPointTransaction(
                            amount = -500,
                            type = "SHARED_CALL_SEND",
                            description = "테스트 송금 -500P"
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("송금 테스트")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    viewModel.createTestPointsDocument()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("초기 포인트 설정 (1000P)")
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun PointTransactionRow(transaction: PointTransaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    getTransactionTypeText(transaction.type),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${if (transaction.amount >= 0) "+" else ""}${transaction.amount}P",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.amount >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }

            if (transaction.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    transaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            transaction.timestamp?.let { timestamp ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(timestamp.toDate()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getTransactionTypeText(type: String): String {
    return when (type) {
        "CHARGE" -> "포인트 충전"
        "SHARED_CALL_SEND" -> "공유콜 송금"
        "SHARED_CALL_RECEIVE" -> "공유콜 수익"
        else -> type
    }
}

@Composable
private fun ClosingSettlementRow(settlement: ClosingSettlement) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        settlement.date,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "마감콜: ${settlement.completedCalls}/${settlement.totalCalls}건",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${settlement.totalRevenue}원",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (expanded && settlement.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                settlement.details.forEach { detail ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "👤 ${detail.customerName}",
                                    fontWeight = FontWeight.Medium,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "${detail.fare}원",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            Text(
                                "📍 ${detail.departure} → ${detail.destination}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
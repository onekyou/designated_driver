package com.designated.callmanager.ui.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.callmanager.data.PointTransaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val krw: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
private val transactionDateFormat = SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel: WalletViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDepositGuide: () -> Unit,
    onNavigateToWithdrawalRequest: () -> Unit,
) {
    val pointsInfo by viewModel.pointsInfo.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val balance = pointsInfo?.balance ?: 0

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("사무실 지갑") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "새로고침")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                )
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(16.dp))
            BalanceCard(balance = balance)
            Spacer(Modifier.height(16.dp))
            ActionRow(
                onDepositClick = onNavigateToDepositGuide,
                onWithdrawClick = onNavigateToWithdrawalRequest,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "최근 거래 내역",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "거래 내역이 없습니다.",
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    itemsIndexed(
                        items = transactions,
                        key = { idx, tx ->
                            if (tx.id.isNotEmpty()) tx.id
                            else "idx_${idx}_${tx.timestamp?.seconds ?: 0}"
                        },
                    ) { _, tx ->
                        TransactionRow(tx)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceCard(balance: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "현재 잔액",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${krw.format(balance)} P",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )
            if (balance < 5_000) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "잔액 5,000P 미만 — 식당 콜 수임 차단",
                    color = Color(0xFFFF6B6B),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    onDepositClick: () -> Unit,
    onWithdrawClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            onClick = onDepositClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        ) {
            Text("입금 안내")
        }
        OutlinedButton(
            onClick = onWithdrawClick,
            modifier = Modifier.weight(1f),
        ) {
            Text("출금 신청")
        }
    }
}

@Composable
private fun TransactionRow(tx: PointTransaction) {
    val isIncome = tx.amount >= 0
    val (label, color) = transactionLabel(tx.type, isIncome)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.12f), shape = MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isIncome) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                contentDescription = null,
                tint = color,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            if (tx.description.isNotBlank()) {
                Text(
                    tx.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            tx.timestamp?.toDate()?.let {
                Text(
                    transactionDateFormat.format(it),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Text(
            text = (if (isIncome) "+" else "") + krw.format(tx.amount) + "P",
            color = color,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
    }
}

private fun transactionLabel(type: String, isIncome: Boolean): Pair<String, Color> {
    return when (type) {
        "SIGNUP_BONUS" -> "가입 보너스" to Color(0xFF388E3C)
        "DEPOSIT" -> "입금" to Color(0xFF388E3C)
        "WITHDRAWAL" -> "출금" to Color(0xFFD32F2F)
        "SHARED_CALL_RECEIVE" -> "공유콜 수신 보상" to Color(0xFF388E3C)
        "SHARED_CALL_SEND" -> "공유콜 송신 부담" to Color(0xFFD32F2F)
        "RESTAURANT_CALL_CHARGE" -> "식당 콜 수임 차감" to Color(0xFFD32F2F)
        "RESTAURANT_PAYMENT_RECEIVED" -> "식당 포인트 결제 수령" to Color(0xFF388E3C)
        "CHARGE" -> "충전" to Color(0xFF388E3C)
        else -> (if (isIncome) "수입" else "지출") to (if (isIncome) Color(0xFF388E3C) else Color(0xFFD32F2F))
    }
}

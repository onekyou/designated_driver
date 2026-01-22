package com.designated.customer.ui.point

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.data.model.PointTransaction
import com.designated.customer.data.model.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointHistoryScreen(
    phoneNumber: String,
    provinceId: String,
    cityId: String,
    officeId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember {
        PointHistoryViewModel(
            phoneNumber = phoneNumber,
            provinceId = provinceId,
            cityId = cityId,
            officeId = officeId
        )
    }
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("포인트 내역") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = uiState.error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.transactions) { transaction ->
                        PointTransactionItem(transaction = transaction)
                    }

                    if (uiState.transactions.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "포인트 거래 내역이 없습니다",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun PointTransactionItem(
    transaction: PointTransaction,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = transaction.description,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = formatDate(transaction.timestamp.toDate()),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                if (transaction.fare != null && transaction.fare > 0) {
                    Text(
                        text = "요금: ${formatNumber(transaction.fare)}원",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (transaction.grade.isNotEmpty()) {
                    Text(
                        text = "등급: ${transaction.grade}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (transaction.type == TransactionType.EARN) {
                            Icons.Default.Add
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = null,
                        tint = if (transaction.type == TransactionType.EARN) {
                            Color(0xFF4CAF50)
                        } else {
                            Color(0xFFE53E3E)
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "${formatNumber(Math.abs(transaction.amount))}P",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (transaction.type == TransactionType.EARN) {
                            Color(0xFF4CAF50)
                        } else {
                            Color(0xFFE53E3E)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "잔액: ${formatNumber(transaction.balance)}P",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

private fun formatNumber(number: Int): String {
    return NumberFormat.getNumberInstance(Locale.KOREA).format(number)
}

private fun formatDate(date: Date): String {
    val format = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
    return format.format(date)
}
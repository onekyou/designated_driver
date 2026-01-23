package com.designated.customer.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.data.model.CustomerCall
import com.designated.customer.data.model.CustomerGrade
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    phoneNumber: String,
    provinceId: String,
    cityId: String,
    officeId: String,
    onBackClick: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val viewModel = remember {
        CallHistoryViewModel(
            phoneNumber = phoneNumber,
            provinceId = provinceId,
            cityId = cityId,
            officeId = officeId
        )
    }
    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            if (showBackButton) {
                TopAppBar(
                    title = { Text("이용 내역") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "뒤로가기"
                            )
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = { Text("이용 내역") }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // 통계 요약 카드
            if (uiState.summary != null) {
                CallSummaryCard(
                    summary = uiState.summary,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

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
                    items(uiState.calls) { call ->
                        CallHistoryItem(call = call)
                    }

                    if (uiState.calls.isEmpty()) {
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
                                        text = "이용 내역이 없습니다",
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
private fun CallSummaryCard(
    summary: CallSummary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "이번 달 이용 현황",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem(
                    title = "총 이용 횟수",
                    value = "${summary.totalRides}회",
                    modifier = Modifier.weight(1f)
                )

                SummaryItem(
                    title = "총 이용 금액",
                    value = "${formatNumber(summary.totalAmount)}원",
                    modifier = Modifier.weight(1f)
                )

                SummaryItem(
                    title = "적립 포인트",
                    value = "${formatNumber(summary.totalPointsEarned)}P",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SummaryItem(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun CallHistoryItem(
    call: CustomerCall,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 상단: 날짜와 상태
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDate(call.timestamp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                StatusChip(status = call.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 경로 정보
            Column {
                RouteItem(
                    icon = Icons.Default.LocationOn,
                    label = "출발",
                    location = call.currentLocation,
                    iconColor = Color(0xFF4CAF50)
                )

                Spacer(modifier = Modifier.height(8.dp))

                RouteItem(
                    icon = Icons.Default.Place,
                    label = "도착",
                    location = call.destinationLocation,
                    iconColor = Color(0xFFFF9800)
                )
            }

            // 하단: 요금 및 포인트 정보
            if (call.fare != null && call.fare > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                Spacer(modifier = Modifier.height(12.dp))

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "요금: ${formatNumber(call.fare)}원",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 완료된 콜의 경우 적립 포인트 표시
                        if (call.status.equals("COMPLETED", ignoreCase = true)) {
                            val earnedPoints = calculateEarnedPoints(call.fare, call.customerGrade ?: "bronze")
                            Text(
                                text = "적립: +${formatNumber(earnedPoints)}P",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }

                    // 포인트 사용 정보
                    if (call.pointsUsed > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "포인트 사용",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "-${formatNumber(call.pointsUsed)}P",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // 할인 정보 표시
                    if (call.discountAmount > 0 && call.finalFare != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "할인 적용",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "-${formatNumber(call.discountAmount)}원",
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "최종 결제",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${formatNumber(call.finalFare)}원",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    location: String,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = location.ifEmpty { "정보 없음" },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status.uppercase()) {
        "COMPLETED" -> "완료" to Color(0xFF4CAF50)
        "CANCELLED" -> "취소" to Color(0xFFE53E3E)
        "IN_PROGRESS" -> "운행중" to Color(0xFF2196F3)
        "ASSIGNED" -> "배정완료" to Color(0xFFFF9800)
        "REQUESTED" -> "요청중" to Color(0xFF9C27B0)
        else -> status to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

private fun formatNumber(number: Int): String {
    return NumberFormat.getNumberInstance(Locale.KOREA).format(number)
}

private fun formatDate(timestamp: Long): String {
    val format = SimpleDateFormat("MM.dd(E) HH:mm", Locale.KOREA)
    return format.format(Date(timestamp))
}

private fun calculateEarnedPoints(fare: Int, grade: String): Int {
    return CustomerGrade.fromString(grade).calculatePoints(fare)
}
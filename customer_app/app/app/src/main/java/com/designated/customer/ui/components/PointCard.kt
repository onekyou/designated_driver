package com.designated.customer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.data.model.CustomerGrade
import com.designated.customer.data.model.CustomerPoints
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PointCard(
    customerPoints: CustomerPoints?,
    isLoading: Boolean,
    onPointHistoryClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (customerPoints != null) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 등급 및 포인트 잔액
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        // 등급 표시
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = customerPoints.grade.icon,
                                fontSize = 24.sp
                            )
                            Text(
                                text = customerPoints.grade.displayName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(customerPoints.grade.color)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 포인트 잔액
                        Text(
                            text = "내 포인트",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${formatNumber(customerPoints.currentPoints)}P",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // 포인트 내역 버튼
                    TextButton(
                        onClick = onPointHistoryClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Text("내역")
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "포인트 내역",
                            modifier = Modifier.size(16.dp).padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 등급 진행도
                customerPoints.getCallsToNextGrade()?.let { callsNeeded ->
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "다음 등급까지",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "${callsNeeded}회 남음",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // 진행도 바
                        val nextGrade = when(customerPoints.grade) {
                            CustomerGrade.BRONZE -> CustomerGrade.SILVER
                            CustomerGrade.SILVER -> CustomerGrade.GOLD
                            CustomerGrade.GOLD -> CustomerGrade.VIP
                            CustomerGrade.VIP -> CustomerGrade.VIP
                        }

                        val progress = if (customerPoints.grade == CustomerGrade.VIP) {
                            1f
                        } else {
                            val currentMin = customerPoints.grade.minCalls
                            val nextMin = nextGrade.minCalls
                            val current = customerPoints.totalCalls - currentMin
                            val total = nextMin - currentMin
                            (current.toFloat() / total).coerceIn(0f, 1f)
                        }

                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = Color(nextGrade.color),
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 적립률 표시
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "현재 적립률",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${(customerPoints.grade.pointRate * 100).toInt()}%",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        } else {
            // 포인트 정보 없음
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "포인트 정보를 불러올 수 없습니다",
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun formatNumber(number: Int): String {
    return NumberFormat.getNumberInstance(Locale.KOREA).format(number)
}
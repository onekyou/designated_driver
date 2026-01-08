package com.designated.customer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.data.model.DailyStepData
import com.designated.customer.data.model.WeeklyStepSummary
import com.designated.customer.data.model.MonthlyStepSummary
import java.text.NumberFormat
import java.util.*

/**
 * 만보기 상세 정보 바텀시트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepDetailBottomSheet(
    dailyData: DailyStepData?,
    weeklyData: WeeklyStepSummary?,
    monthlyData: MonthlyStepSummary?,
    sessionSteps: Int = 0,
    isSessionActive: Boolean = false,
    onDismiss: () -> Unit,
    onGoalChange: (Int) -> Unit = {},
    onResetSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxSize(),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(scrollState)
        ) {
            // 헤더
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "만보기 상세 정보",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "닫기"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 세션 데이터 (세션이 활성화되었을 때만 표시)
            if (isSessionActive) {
                SectionHeader(icon = "🎯", title = "현재 세션")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        StepStatRow(
                            label = "세션 걸음 수",
                            value = numberFormat.format(sessionSteps)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = onResetSession,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF00BCD4)
                            )
                        ) {
                            Text("🔄 세션 리셋", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 오늘 데이터
            dailyData?.let { data ->
                SectionHeader(icon = "📊", title = "오늘")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        StepStatRow(
                            label = "걸음 수",
                            value = "${numberFormat.format(data.steps)} / ${numberFormat.format(data.goal)}"
                        )
                        StepStatRow(
                            label = "진행률",
                            value = "${data.getProgressPercent()}%"
                        )

                        // 향후 추가될 데이터 (초기값 0이면 숨김)
                        if (data.activeMinutes > 0) {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            StepStatRow(
                                label = "⏱️ 활동 시간",
                                value = "${data.activeMinutes}분"
                            )
                        }
                        if (data.calories > 0) {
                            StepStatRow(
                                label = "🔥 칼로리",
                                value = "${data.calories.toInt()} kcal"
                            )
                        }
                        if (data.distance > 0) {
                            StepStatRow(
                                label = "📏 거리",
                                value = "%.1f km".format(data.distance)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 주간 데이터
            weeklyData?.let { data ->
                SectionHeader(icon = "📅", title = "이번 주")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        StepStatRow(
                            label = "총 걸음 수",
                            value = numberFormat.format(data.totalSteps)
                        )
                        StepStatRow(
                            label = "평균",
                            value = "${numberFormat.format(data.avgSteps)} 걸음/일"
                        )
                        StepStatRow(
                            label = "활동 일수",
                            value = "${data.daysActive}일"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 월간 데이터
            monthlyData?.let { data ->
                SectionHeader(icon = "📆", title = "이번 달")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        StepStatRow(
                            label = "총 걸음 수",
                            value = numberFormat.format(data.totalSteps)
                        )
                        StepStatRow(
                            label = "평균",
                            value = "${numberFormat.format(data.avgSteps)} 걸음/일"
                        )
                        StepStatRow(
                            label = "활동 일수",
                            value = "${data.daysActive}일"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 목표 설정 (향후 구현)
            SectionHeader(icon = "🎯", title = "목표 설정")

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
                        text = "현재 목표: ${numberFormat.format(dailyData?.goal ?: 10000)} 걸음",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "목표 변경 기능은 향후 업데이트 예정입니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * 섹션 헤더
 */
@Composable
private fun SectionHeader(
    icon: String,
    title: String
) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 20.sp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 통계 항목 행
 */
@Composable
private fun StepStatRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

package com.designated.customer.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.R
import com.designated.customer.data.model.DailyStepData

/**
 * 메인 화면 만보기 카드 - 가로 레이아웃 (TODAY 버튼 - 원형 - 리셋 버튼)
 */
@Composable
fun StepCounterCard(
    stepData: DailyStepData?,
    sessionSteps: Int = 0,
    isSessionActive: Boolean = false,
    onSettingsClick: () -> Unit,
    onStartSession: () -> Unit = {},
    onResetSession: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 모드 상태 (false: TODAY, true: SESSION)
    var isSessionMode by remember { mutableStateOf(false) }

    val todaySteps = stepData?.steps ?: 0
    val goal = stepData?.goal ?: 10000
    val todayProgress = if (goal > 0) (todaySteps.toFloat() / goal.toFloat()).coerceIn(0f, 1f) else 0f

    // 세션 진행률은 목표의 절반 기준
    val sessionGoal = goal / 2
    val sessionProgress = if (sessionGoal > 0) (sessionSteps.toFloat() / sessionGoal.toFloat()).coerceIn(0f, 1f) else 0f

    // 현재 표시할 걸음수와 진행률
    val displaySteps = if (isSessionMode) sessionSteps else todaySteps
    val displayProgress = if (isSessionMode) sessionProgress else todayProgress
    val displayLabel = if (isSessionMode) "SESSION" else "TODAY"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // 배경 이미지
            Image(
                painter = painterResource(id = R.drawable.step_counter_background),
                contentDescription = "만보기 배경",
                modifier = Modifier
                    .fillMaxSize()
                    .blur(3.dp),
                contentScale = ContentScale.Crop
            )

            // 메인 가로 레이아웃
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 좌측: TODAY 원형 버튼과 텍스트
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "TODAY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (!isSessionMode) Color(0xFFFFAB00) else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            isSessionMode = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (!isSessionMode) Color(0xFFFFAB00).copy(alpha = 0.2f) else Color.Transparent,
                            contentColor = if (!isSessionMode) Color(0xFFFFAB00) else Color.Gray
                        ),
                        border = BorderStroke(
                            width = 3.dp,
                            color = if (!isSessionMode) Color(0xFFFFAB00) else Color.LightGray
                        ),
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = formatSteps(todaySteps),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // 중앙: 원형 카운터 (큰 원)
                Box(
                    modifier = Modifier.weight(1.5f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularStepCounter(
                        steps = displaySteps,
                        progress = displayProgress,
                        label = displayLabel
                    )
                }

                // 우측: SESSION 원형 버튼/리셋과 텍스트
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "SESSION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSessionMode) Color(0xFF00BCD4) else Color.Gray
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    if (!isSessionActive || !isSessionMode) {
                        // SESSION 시작 버튼
                        OutlinedButton(
                            onClick = {
                                onStartSession()
                                isSessionMode = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = Color(0xFF00BCD4)
                            ),
                            border = BorderStroke(
                                width = 3.dp,
                                color = Color(0xFF00BCD4)
                            ),
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                text = formatSteps(sessionSteps),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // 리셋 버튼
                        OutlinedButton(
                            onClick = onResetSession,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFF00BCD4).copy(alpha = 0.2f),
                                contentColor = Color(0xFF00BCD4)
                            ),
                            border = BorderStroke(
                                width = 3.dp,
                                color = Color(0xFF00BCD4)
                            ),
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "리셋",
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = formatSteps(sessionSteps),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 우측 상단 설정 아이콘
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "만보기 설정",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 원형 걸음 수 표시 컴포넌트
 */
@Composable
private fun CircularStepCounter(
    steps: Int,
    progress: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    val orangeColor = Color(0xFFFFAB00)
    val lightGray = Color(0xFFE0E0E0)

    Box(
        modifier = modifier.size(140.dp),
        contentAlignment = Alignment.Center
    ) {
        // Canvas로 원형 프로그레스 그리기
        Canvas(
            modifier = Modifier.size(140.dp)
        ) {
            val canvasSize = size.minDimension
            val strokeWidth = 10.dp.toPx()
            val arcSize = Size(canvasSize - strokeWidth, canvasSize - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // 배경 원 (밝은 회색)
            drawArc(
                color = lightGray,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 프로그레스 원 (오렌지)
            if (progress > 0f) {
                drawArc(
                    color = orangeColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // 중앙 콘텐츠
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 걸음 수
            Text(
                text = formatSteps(steps),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00BCD4),
                letterSpacing = 0.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            // 라벨
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f),
                letterSpacing = 0.5.sp
            )
        }
    }
}

/**
 * 걸음 수 포맷팅 (천 단위 구분)
 */
private fun formatSteps(steps: Int): String {
    return when {
        steps >= 10000 -> String.format("%,d", steps)
        steps >= 1000 -> String.format("%,d", steps)
        else -> steps.toString()
    }
}

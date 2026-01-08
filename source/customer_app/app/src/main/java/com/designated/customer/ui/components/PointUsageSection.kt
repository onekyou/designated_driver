package com.designated.customer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.data.model.CustomerPoints
import java.text.NumberFormat
import java.util.*

@Composable
fun PointUsageSection(
    customerPoints: CustomerPoints?,
    usePoints: Boolean,
    pointsToUse: Int,
    onToggleUsePoints: () -> Unit,
    onPointsToUseChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val availablePoints = customerPoints?.currentPoints ?: 0
    val maxUsablePoints = minOf(availablePoints, 10000) // 최대 1만 포인트까지 사용 가능

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "포인트 사용",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Switch(
                    checked = usePoints,
                    onCheckedChange = { onToggleUsePoints() },
                    enabled = availablePoints > 0
                )
            }

            if (usePoints && availablePoints > 0) {
                Spacer(modifier = Modifier.height(12.dp))

                // 사용 가능 포인트 표시
                Text(
                    text = "사용 가능: ${formatNumber(availablePoints)}P (최대 ${formatNumber(maxUsablePoints)}P)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 포인트 입력 필드
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = if (pointsToUse > 0) pointsToUse.toString() else "",
                        onValueChange = { value ->
                            val points = value.toIntOrNull() ?: 0
                            onPointsToUseChange(minOf(points, maxUsablePoints))
                        },
                        label = { Text("사용할 포인트") },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        suffix = { Text("P") }
                    )

                    // 최대 사용 버튼
                    Button(
                        onClick = { onPointsToUseChange(maxUsablePoints) },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("최대")
                    }
                }

                if (pointsToUse > 0) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // 할인 정보 표시
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "할인 금액",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatNumber(pointsToUse)}원",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (usePoints && availablePoints == 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "사용 가능한 포인트가 없습니다",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

private fun formatNumber(number: Int): String {
    return NumberFormat.getNumberInstance(Locale.KOREA).format(number)
}
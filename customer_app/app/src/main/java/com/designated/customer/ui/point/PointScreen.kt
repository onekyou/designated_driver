package com.designated.customer.ui.point

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.data.model.CustomerGrade
import com.designated.customer.service.PointService
import com.designated.customer.ui.components.PointCard
import java.text.NumberFormat
import java.util.*

@Composable
fun PointScreen(
    phoneNumber: String,
    provinceId: String,
    cityId: String,
    officeId: String,
    modifier: Modifier = Modifier
) {
    val pointService = remember { PointService(provinceId = provinceId, cityId = cityId, officeId = officeId) }
    var customerPoints by remember { mutableStateOf<com.designated.customer.data.model.CustomerPoints?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // ✅ 포인트 정보 로드 함수
    val loadPoints: suspend () -> Unit = {
        try {
            isRefreshing = true
            customerPoints = pointService.getCustomerPoints(phoneNumber)
        } finally {
            isLoading = false
            isRefreshing = false
        }
    }

    // ✅ 초기 로드 (1회만)
    LaunchedEffect(phoneNumber) {
        loadPoints()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 제목
        Text(
            text = "포인트",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 포인트 카드
        PointCard(
            customerPoints = customerPoints,
            isLoading = isLoading,
            onRefresh = {
                // ✅ 새로고침 버튼 클릭 시
                coroutineScope.launch {
                    loadPoints()
                }
            },
            isRefreshing = isRefreshing,
            modifier = Modifier.fillMaxWidth()
        )

        // 등급 안내 카드
        GradeInfoCard()

        // 포인트 사용 안내 카드
        PointUsageInfoCard()
    }
}

@Composable
private fun GradeInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "등급 안내",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "등급별 혜택",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            CustomerGrade.values().forEach { grade ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = grade.icon,
                            fontSize = 16.sp
                        )
                        Text(
                            text = grade.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(grade.color)
                        )
                    }

                    Text(
                        text = "${(grade.pointRate * 100).toInt()}% 적립",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PointUsageInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "포인트 사용 안내",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            val usageInfo = listOf(
                "• 1포인트 = 1원으로 사용 가능",
                "• 콜 요청 시 최대 10,000P까지 사용",
                "• 사용한 포인트만큼 요금에서 할인",
                "• 포인트는 이용 완료 후 즉시 적립"
            )

            usageInfo.forEach { info ->
                Text(
                    text = info,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}
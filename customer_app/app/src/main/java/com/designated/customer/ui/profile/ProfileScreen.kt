package com.designated.customer.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.service.PointService
import kotlinx.coroutines.tasks.await

@Composable
fun ProfileScreen(
    phoneNumber: String,
    provinceId: String,
    cityId: String,
    officeId: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var officeName by remember { mutableStateOf(officeId) }
    var regionName by remember { mutableStateOf(provinceId) }
    var customerName by remember { mutableStateOf("고객님") }

    // Firebase에서 사무실 정보 및 고객 정보 로드
    LaunchedEffect(officeId, provinceId, cityId) {
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val userId = auth.currentUser?.uid

            // 사무실 정보 로드
            val officeDoc = firestore
                .collection("provinces")
                .document(provinceId)
                .collection("cities")
                .document(cityId)
                .collection("offices")
                .document(officeId)
                .get()
                .await()

            if (officeDoc.exists()) {
                officeName = officeDoc.getString("name") ?: officeId
            }

            // 고객 정보 로드 (이름)
            if (userId != null) {
                val customerDoc = firestore
                    .collection("provinces")
                    .document(provinceId)
                    .collection("cities")
                    .document(cityId)
                    .collection("offices")
                    .document(officeId)
                    .collection("customers")
                    .document(userId)
                    .get()
                    .await()

                if (customerDoc.exists()) {
                    val name = customerDoc.getString("name")
                    if (!name.isNullOrBlank()) {
                        customerName = name
                    }
                }
            }

            regionName = when(provinceId) {
                "seoul" -> "서울"
                "gyeonggi" -> "경기"
                "Hongchon" -> "홍천"
                else -> provinceId
            }
        } catch (e: Exception) {
            // 실패 시 기본값 사용
        }
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
            text = "내 정보",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 프로필 카드
        ProfileInfoCard(
            customerName = customerName,
            phoneNumber = phoneNumber,
            regionName = regionName,
            officeName = officeName
        )

        // 앱 정보 카드
        AppInfoCard()
    }
}

@Composable
private fun ProfileInfoCard(
    customerName: String,
    phoneNumber: String,
    regionName: String,
    officeName: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "프로필",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Column {
                    Text(
                        text = customerName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = phoneNumber,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 연결된 사무실 정보
            ProfileInfoRow(
                icon = Icons.Default.LocationOn,
                label = "연결된 사무실",
                value = officeName
            )

            ProfileInfoRow(
                icon = Icons.Default.Phone,
                label = "지역",
                value = regionName
            )
        }
    }
}

@Composable
private fun ProfileInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )

        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier.width(80.dp)
        )

        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun AppInfoCard() {
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
                    contentDescription = "앱 정보",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "앱 정보",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val appInfo = listOf(
                "버전" to "1.0.0",
                "개발" to "콜매니저 팀",
                "문의" to "고객센터",
                "업데이트" to "자동 업데이트"
            )

            appInfo.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
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
        }
    }
}


package com.designated.customer.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.service.PointService

@Composable
fun ProfileScreen(
    phoneNumber: String,
    regionId: String,
    officeId: String,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogoutDialog by remember { mutableStateOf(false) }

    // 로그아웃 다이얼로그에서 백버튼 처리
    BackHandler(enabled = showLogoutDialog) {
        showLogoutDialog = false
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
            phoneNumber = phoneNumber,
            regionId = regionId,
            officeId = officeId
        )

        // 앱 정보 카드
        AppInfoCard()

        // 설정 메뉴
        SettingsMenuCard(
            onLogoutClick = { showLogoutDialog = true }
        )
    }

    // 로그아웃 확인 다이얼로그
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃") },
            text = { Text("정말 로그아웃하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun ProfileInfoCard(
    phoneNumber: String,
    regionId: String,
    officeId: String
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
                        text = "고객님",
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
                value = officeId
            )

            ProfileInfoRow(
                icon = Icons.Default.Phone,
                label = "지역",
                value = regionId
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

@Composable
private fun SettingsMenuCard(
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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
                    Icons.Default.Settings,
                    contentDescription = "설정",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "설정",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 로그아웃 버튼
            Button(
                onClick = onLogoutClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = "로그아웃",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "로그아웃",
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
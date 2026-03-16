package com.designated.driverapp.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.designated.driverapp.model.DriverStatus

@Composable
fun WaitingScreen(
    driverStatus: DriverStatus,
    onGoOnline: () -> Unit,
    onCheckPendingDispatch: () -> Unit = {},
    onShowReferralQR: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (driverStatus) {
                DriverStatus.ONLINE, DriverStatus.WAITING -> {
                    Text(
                        "새로운 콜을 기다리고 있습니다...",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("배차가 완료되면 알림으로 알려드립니다.")
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onCheckPendingDispatch
                    ) {
                        Text("배차 확인")
                    }
                }
                else -> {
                    Text(
                        "현재 ${driverStatus.getDisplayName()} 상태입니다.",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 하단 고객 추천 버튼
        Button(
            onClick = onShowReferralQR,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("고객 추천하기 (QR 코드)", style = MaterialTheme.typography.titleMedium)
        }
    }
}
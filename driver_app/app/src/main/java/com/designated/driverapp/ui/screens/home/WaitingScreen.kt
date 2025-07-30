package com.designated.driverapp.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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
    onGoOffline: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
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
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onGoOffline) {
                    Text("오프라인으로 전환")
                }
            }
            else -> {
                Text(
                    "현재 ${driverStatus.getDisplayName()} 상태입니다.",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onGoOnline) {
                    Text("온라인으로 전환")
                }
            }
        }
    }
} 
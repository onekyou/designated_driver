package com.designated.driverapp.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.driverapp.model.CallInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InProgressScreen(
    callInfo: CallInfo,
    onCompleteTrip: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = "운행 중") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "고객님을 목적지까지 안전하게 모시고 있습니다.",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    InfoRow(label = "출발지", value = callInfo.departure_set ?: "정보 없음")
                    InfoRow(label = "도착지", value = callInfo.destination_set ?: "정보 없음")
                    if (callInfo.waypoints_set?.isNotBlank() == true) {
                        InfoRow(label = "경유지", value = callInfo.waypoints_set)
                    }
                    InfoRow(label = "예상 요금", value = "${callInfo.fare_set ?: "정보 없음"}원", isFare = true)
                }
            }

            Button(
                onClick = { onCompleteTrip() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Text("운행 완료", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, isFare: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(80.dp),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = if (isFare) MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.bodyLarge,
            color = if (isFare) MaterialTheme.colorScheme.primary else LocalContentColor.current
        )
    }
}
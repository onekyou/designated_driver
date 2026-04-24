package com.designated.pickupdriver.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.designated.pickupdriver.data.Constants
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onLogout: () -> Unit
) {
    val calls by viewModel.calls.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.startListening()
        onDispose { viewModel.stopListening() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("진행 중 ${calls.size}건") },
                actions = {
                    TextButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Text("로그아웃")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (calls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text("진행 중인 콜이 없습니다", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = calls, key = { it.callId }) { call ->
                    CallCard(call = call)
                }
            }
        }
    }
}

@Composable
private fun CallCard(call: CallItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(status = call.status)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTime(call.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            RouteLine(call)
            call.fareSet?.takeIf { it > 0 }?.let {
                Text(
                    text = "요금: ${formatFare(it)}원",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "기사: ${call.assignedDriverName ?: "(미배정)"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun RouteLine(call: CallItem) {
    val hasRoute = !call.departureSet.isNullOrBlank() ||
        !call.destinationSet.isNullOrBlank() ||
        !call.waypointsSet.isNullOrBlank()

    if (hasRoute) {
        val parts = buildList {
            add(call.departureSet?.takeIf { it.isNotBlank() } ?: "출발지 미설정")
            call.waypointsSet?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(call.destinationSet?.takeIf { it.isNotBlank() } ?: "도착지 미설정")
        }
        Text(
            text = parts.joinToString(" → "),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    } else {
        Text(
            text = call.customerAddress ?: "(출발지 정보 없음)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatFare(fare: Long): String =
    "%,d".format(fare)

@Composable
private fun StatusChip(status: String) {
    val (label, color) = statusToLabelAndColor(status)
    Box(
        modifier = Modifier
            .background(color = color, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

private fun statusToLabelAndColor(status: String): Pair<String, Color> = when (status) {
    Constants.STATUS_WAITING -> "대기" to Color(0xFF607D8B)
    Constants.STATUS_ASSIGNED -> "배차됨" to Color(0xFFFF9800)
    Constants.STATUS_ACCEPTED -> "수락" to Color(0xFF2196F3)
    Constants.STATUS_IN_PROGRESS -> "운행중" to Color(0xFF4CAF50)
    Constants.STATUS_AWAITING_SETTLEMENT -> "정산대기" to Color(0xFF9C27B0)
    else -> status to Color.Gray
}

private fun formatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(timestamp))
}

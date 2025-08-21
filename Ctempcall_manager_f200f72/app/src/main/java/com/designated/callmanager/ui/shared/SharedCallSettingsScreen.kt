package com.designated.callmanager.ui.shared

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SharedCallInfo
import com.designated.callmanager.data.PointTransaction
import com.designated.callmanager.ui.dashboard.DashboardViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.ExperimentalMaterial3Api
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedCallSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val allSharedCalls by viewModel.allSharedCalls.collectAsState()
    val pointsInfo by viewModel.pointsInfo.collectAsState()
    val pointTransactions by viewModel.pointTransactions.collectAsState()
    val myOfficeId by viewModel.officeId.collectAsState()
    val (tabIndex, setTabIndex) = remember { mutableStateOf(0) }

    val myPublished = allSharedCalls.filter { it.sourceOfficeId == myOfficeId }
    val myClaimed   = allSharedCalls.filter { it.claimedOfficeId == myOfficeId }
    
    // 시스템 뒤로가기 버튼 처리
    BackHandler {
        onNavigateBack()
    }
    
    // 디버깅 로그
    LaunchedEffect(allSharedCalls, pointsInfo, pointTransactions, myOfficeId) {
        Log.d("SharedCallSettings", "=== Debug Info ===")
        Log.d("SharedCallSettings", "myOfficeId: $myOfficeId")
        Log.d("SharedCallSettings", "pointsInfo: ${pointsInfo?.balance}")
        Log.d("SharedCallSettings", "allSharedCalls size: ${allSharedCalls.size}")
        Log.d("SharedCallSettings", "myPublished size: ${myPublished.size}")
        Log.d("SharedCallSettings", "myClaimed size: ${myClaimed.size}")
        Log.d("SharedCallSettings", "pointTransactions size: ${pointTransactions.size}")
        
        allSharedCalls.forEach { call ->
            Log.d("SharedCallSettings", "SharedCall: id=${call.id}, source=${call.sourceOfficeId}, claimed=${call.claimedOfficeId}, status=${call.status}")
        }
        
        pointTransactions.forEach { transaction ->
            Log.d("SharedCallSettings", "PointTransaction: id=${transaction.id}, type=${transaction.type}, amount=${transaction.amount}")
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("공유 콜 관리") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            // 포인트 현황 카드
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "현재 포인트 잔액",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${pointsInfo?.balance ?: 0} P",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    pointsInfo?.updatedAt?.let { timestamp ->
                        Text(
                            "마지막 업데이트: ${SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(timestamp.toDate())}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex==0, onClick={setTabIndex(0)}, text={Text("내가 올린")})
                Tab(selected = tabIndex==1, onClick={setTabIndex(1)}, text={Text("내가 수락")})
                Tab(selected = tabIndex==2, onClick={setTabIndex(2)}, text={Text("포인트 내역")})
            }
            
            LazyColumn(Modifier.fillMaxSize()) {
                when (tabIndex) {
                    0 -> {
                        if (myPublished.isEmpty()) {
                            item {
                                EmptyStateMessage("아직 올린 공유콜이 없습니다.")
                            }
                        } else {
                            items(myPublished, key={it.id}) { call ->
                                SharedCallRow(call, isPublished = true)
                            }
                        }
                    }
                    1 -> {
                        if (myClaimed.isEmpty()) {
                            item {
                                EmptyStateMessage("아직 수락한 공유콜이 없습니다.")
                            }
                        } else {
                            items(myClaimed, key={it.id}) { call ->
                                SharedCallRow(call, isPublished = false)
                            }
                        }
                    }
                    2 -> {
                        if (pointTransactions.isEmpty()) {
                            item {
                                EmptyStateMessage("포인트 거래 내역이 없습니다.")
                            }
                        } else {
                            items(pointTransactions, key={it.id}) { transaction ->
                                PointTransactionRow(transaction)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SharedCallRow(call: SharedCallInfo, isPublished: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${call.departure ?: "출발지"} → ${call.destination ?: "도착지"}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                StatusChip(call.status)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "요금: ${call.fare ?: 0}원",
                    style = MaterialTheme.typography.bodyMedium
                )
                call.timestamp?.let { timestamp ->
                    Text(
                        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(timestamp.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            if (call.status == "COMPLETED") {
                val pointAmount = ((call.fare ?: 0) * 0.1).toInt()
                Text(
                    if (isPublished) "+${pointAmount}P (수익)" else "-${pointAmount}P (수수료)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPublished) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val (color, text) = when (status) {
        "OPEN" -> Color(0xFF2196F3) to "대기중"
        "CLAIMED" -> Color(0xFFFFA000) to "수락됨"
        "COMPLETED" -> Color(0xFF4CAF50) to "완료"
        else -> Color.Gray to status
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = Modifier.padding(0.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PointTransactionRow(transaction: PointTransaction) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    getTransactionTypeText(transaction.type),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "${if (transaction.amount >= 0) "+" else ""}${transaction.amount}P",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (transaction.amount >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
            }
            
            if (transaction.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    transaction.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            transaction.timestamp?.let { timestamp ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(timestamp.toDate()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getTransactionTypeText(type: String): String {
    return when (type) {
        "CHARGE" -> "포인트 충전"
        "SHARED_CALL_SEND" -> "공유콜 송금"
        "SHARED_CALL_RECEIVE" -> "공유콜 수익"
        else -> type
    }
} 
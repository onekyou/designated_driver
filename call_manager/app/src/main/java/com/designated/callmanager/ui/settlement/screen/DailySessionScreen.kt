package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DailySessionScreen(vm: SettlementViewModel = viewModel()) {
    val sessions by vm.sessionList.collectAsState()
    var selectedSession by remember { mutableStateOf<String?>(null) }
    var sessionTrips by remember { mutableStateOf<List<SettlementData>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(selectedSession) {
        selectedSession?.let { sid ->
            sessionTrips = vm.getTripsForSession(sid)
            showDialog = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("일일 정산", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        if(sessions.isNotEmpty()) {
            // 세션 카드(업무 마감 카드)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions) { s ->
                    Card(modifier=Modifier.fillMaxWidth().clickable {
                        selectedSession = s.sessionId
                    }, colors=CardDefaults.cardColors(containerColor=Color(0xFF1E1E1E))) {
                        val dateStr = try {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(s.sessionId.toLong()))
                        } catch (e: Exception) { "세션" }
                        Column(Modifier.padding(12.dp)) {
                            Text(dateStr, color=Color.White, fontWeight=FontWeight.Bold)
                            Text("총 ${s.totalTrips} 건  /  총금액 ${NumberFormat.getNumberInstance().format(s.totalFare)}원", color=Color.White)
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), Alignment.Center) { Text("업무 마감 세션이 없습니다.", color = Color.White) }
        }
    }

    if(showDialog) {
        DateDetailDialog(date = selectedSession ?: "세션", settlements = sessionTrips, onDismiss={ showDialog=false })
    }
} 
package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.designated.callmanager.data.SettlementData
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

/**
 * 운행 상세 정보 팝업 (전체내역 등에서 사용).
 */
@Composable
fun TripDetailDialog(settlement: SettlementData, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("운행 상세 정보", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("고객명: ${settlement.customerName}", color = Color.White)
                Text("기사명: ${settlement.driverName}", color = Color.White)
                Text("출발지: ${settlement.departure}", color = Color.White)
                Text("도착지: ${settlement.destination}", color = Color.White)
                if (settlement.waypoints.isNotBlank())
                    Text("경유: ${settlement.waypoints}", color = Color.White)
                Text("요금: ${NumberFormat.getNumberInstance().format(settlement.fare)}원", color = Color.White)
                Text("결제: ${settlement.paymentMethod}", color = Color.White)
                val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
                Text("완료시각: ${sdf.format(Date(settlement.completedAt))}", color = Color.White)
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("닫기") }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}

@Composable
fun DateDetailDialog(date: String, settlements: List<SettlementData>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$date 상세 내역", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 450.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No", Modifier.weight(0.5f), color = Color.Yellow, textAlign = TextAlign.Center)
                    Text("고객", Modifier.weight(1f), color = Color.Yellow, textAlign = TextAlign.Center)
                    Text("기사", Modifier.weight(1f), color = Color.Yellow, textAlign = TextAlign.Center)
                    Text("요금", Modifier.weight(1f), color = Color.Yellow, textAlign = TextAlign.Center)
                    Text("결제", Modifier.weight(1f), color = Color.Yellow, textAlign = TextAlign.Center)
                }
                Divider(color = Color.DarkGray)
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(settlements) { idx, s ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${idx+1}", Modifier.weight(0.5f), color = Color.White, textAlign = TextAlign.Center)
                            Text(s.customerName.take(3), Modifier.weight(1f), color = Color.White, textAlign = TextAlign.Center)
                            Text(s.driverName, Modifier.weight(1f), color = Color.White, textAlign = TextAlign.Center)
                            Text(NumberFormat.getNumberInstance().format(s.fare), Modifier.weight(1f), color = Color.White, textAlign = TextAlign.Center)
                            Text(s.paymentMethod, Modifier.weight(1f), color = Color.White, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        containerColor = Color(0xFF2A2A2A)
    )
} 
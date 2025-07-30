package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.designated.callmanager.data.SettlementData
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun TripListTable(
    tripList: List<SettlementData>,
    onShowDetail: (SettlementData) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    if (tripList.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("정산 내역이 없습니다.", color = Color.White)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 12.dp)
        ) {
            Text("No", Modifier.weight(0.5f), color = Color.Yellow)
            Text("고객", Modifier.weight(1.2f), color = Color.Yellow)
            Text("기사", Modifier.weight(1f), color = Color.Yellow)
            Text("금액", Modifier.weight(1f).padding(end = 6.dp), color = Color.Yellow, textAlign = TextAlign.End)
            Text("결제", Modifier.weight(0.8f), color = Color.Yellow, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(0.3f))
        }
        Divider(color = Color.DarkGray)

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(tripList, key = { _, it -> it.callId }) { idx, item ->
                TripRowWithIndex(idx + 1, item, onShowDetail)
            }
        }
    }
}

@Composable
private fun TripRowWithIndex(index: Int, settlement: SettlementData, onShowDetail: (SettlementData) -> Unit) {
    Column(
        Modifier.fillMaxWidth()
            .clickable { onShowDetail(settlement) }
            .background(Color(0xFF1E1E1E))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$index", Modifier.weight(0.5f), color = Color.White)
            Text(settlement.customerName.take(3), Modifier.weight(1.2f), color = Color.White)
            Text(settlement.driverName.take(3), Modifier.weight(1f), color = Color.White)
            Text(NumberFormat.getNumberInstance().format(settlement.fare), Modifier.weight(1f).padding(end = 6.dp), color = Color.White, textAlign = TextAlign.Right)
            Text(settlement.paymentMethod, Modifier.weight(0.8f), color = Color.White, textAlign = TextAlign.Center)
            IconButton(onClick = { onShowDetail(settlement) }) {
                Icon(Icons.Filled.Info, contentDescription = "상세", tint = Color.White)
            }
        }
        if (settlement.departure.isNotBlank() || settlement.destination.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp)) {
                Text("${settlement.departure} ➜ ${settlement.destination}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
} 
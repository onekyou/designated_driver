package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
    tripList: List<SettlementData>
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
            Text("No", Modifier.weight(0.5f), color = Color.Yellow, textAlign = TextAlign.Center)
            Text("고객", Modifier.weight(0.8f), color = Color.Yellow)
            Text("기사", Modifier.weight(0.8f), color = Color.Yellow)
            Text("금액", Modifier.weight(1.35f), color = Color.Yellow, textAlign = TextAlign.Center)
            Text("결제", Modifier.weight(1.35f), color = Color.Yellow, textAlign = TextAlign.Center)
        }
        Divider(color = Color.DarkGray)

        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(tripList, key = { _, it -> it.callId }) { idx, item ->
                TripRowWithIndex(idx + 1, item)
            }
        }
    }
}

@Composable
private fun TripRowWithIndex(index: Int, settlement: SettlementData) {
    Column(
        Modifier.fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("$index", Modifier.weight(0.5f), color = Color.White, textAlign = TextAlign.Center)
            Text(settlement.customerName.take(3), Modifier.weight(0.8f), color = Color.White)
            Text(settlement.driverName.take(3), Modifier.weight(0.8f), color = Color.White)
            Text(NumberFormat.getNumberInstance().format(settlement.fare), Modifier.weight(1.35f), color = Color.White, textAlign = TextAlign.Center)
            Text(settlement.paymentMethod, Modifier.weight(1.35f), color = Color.White, textAlign = TextAlign.Center)
        }
        if (settlement.departure.isNotBlank() || settlement.destination.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.Center) {
                Text("${settlement.departure} ➜ ${settlement.destination}", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.ui.settlement.SettlementViewModel
import androidx.compose.material3.OutlinedTextField
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Divider
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import android.content.Context
import java.text.NumberFormat

@Composable
fun CreditManagementScreen(vm: SettlementViewModel = viewModel()) {
    val credits = vm.creditPersons.collectAsState().value

    var showCollectDialog by remember { mutableStateOf(false) }
    var showDetailDialog by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<SettlementViewModel.CreditPerson?>(null) }
    var collectAmountText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("외상 관리", style = MaterialTheme.typography.titleMedium, color = Color.White)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(credits) { c ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2A2A))) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = Color.White)
                            Text(c.phone, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                            Text("${c.amount}원", color = Color.White)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { selectedPerson = c; showDetailDialog = true }) { Text("상세") }
                                Button(onClick = { selectedPerson = c; collectAmountText = ""; showCollectDialog = true }) { Text("회수") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCollectDialog && selectedPerson != null) {
        AlertDialog(
            onDismissRequest = { showCollectDialog = false },
            title = { Text("금액 회수") },
            text = {
                OutlinedTextField(value = collectAmountText, onValueChange = { collectAmountText = it.filter { ch -> ch.isDigit() } }, label = { Text("회수 금액") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val amt = collectAmountText.toIntOrNull() ?: 0
                    if (amt > 0) {
                        vm.reduceCredit(selectedPerson!!.id, amt)
                    }
                    showCollectDialog = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { showCollectDialog = false }) { Text("취소") } }
        )
    }

    // 상세 다이얼로그 - 기사별 상세내역과 동일한 포맷
    val context = LocalContext.current
    if (showDetailDialog && selectedPerson != null) {
        CreditDetailDialog(
            person = selectedPerson!!,
            onDismiss = { showDetailDialog = false }
        )
    }
}

@Composable
fun CreditDetailDialog(
    person: SettlementViewModel.CreditPerson,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${person.name} 외상 상세", color = Color.White) },
        text = {
            Column(Modifier.heightIn(max = 450.dp).fillMaxWidth()) {
                // 헤더 - 외상관리 전용 포맷
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No", Modifier.weight(0.5f), color = Color.Yellow, textAlign = TextAlign.Center)
                    Text("날짜", Modifier.weight(1f), color = Color.Yellow, textAlign = TextAlign.Center)  
                    Text("요금", Modifier.weight(1f), color = Color.Yellow, textAlign = TextAlign.Center)
                }
                Divider(color = Color.DarkGray)
                
                if (person.entries.isEmpty()) {
                    // 데이터가 없는 경우
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Text("외상 내역이 없습니다.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    // 실제 entries 데이터 표시 (두 줄 포맷)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(person.entries) { idx, entry ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                // 첫 번째 줄: No, 날짜, 요금
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${idx+1}", Modifier.weight(0.5f), color = Color.White, textAlign = TextAlign.Center)
                                    
                                    // 날짜에서 월,일만 추출 (yyyy-MM-dd -> MM/dd)
                                    val dateText = if (entry.date.contains("-")) {
                                        val parts = entry.date.split("-")
                                        if (parts.size >= 3) "${parts[1]}/${parts[2]}" else entry.date
                                    } else entry.date
                                    Text(dateText, Modifier.weight(1f), color = Color.White, textAlign = TextAlign.Center)
                                    
                                    Text(NumberFormat.getNumberInstance().format(entry.amount), Modifier.weight(1f), color = Color.White, textAlign = TextAlign.Center)
                                }
                                
                                // 두 번째 줄: 출발지→도착지 (중앙 정렬)
                                val routeText = "${entry.departure}→${entry.destination}"
                                Text(
                                    routeText, 
                                    Modifier.fillMaxWidth().padding(top = 2.dp), 
                                    color = Color.Gray, 
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                
                                // 구분선 (마지막 항목이 아닌 경우에만)
                                if (idx < person.entries.size - 1) {
                                    Divider(
                                        color = Color.DarkGray.copy(alpha = 0.3f), 
                                        thickness = 0.5.dp, 
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 총합 표시
                Spacer(Modifier.height(8.dp))
                Divider(color = Color.DarkGray)
                Text(
                    "총 외상 금액: ${NumberFormat.getNumberInstance().format(person.amount)}원", 
                    color = Color.Yellow, 
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = { 
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 공유 버튼
                TextButton(onClick = {
                    shareCreditDetails(person, context)
                }) { 
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("공유") 
                    }
                }
                // 닫기 버튼
                TextButton(onClick = onDismiss) { 
                    Text("닫기") 
                }
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}

private fun shareCreditDetails(person: SettlementViewModel.CreditPerson, context: Context) {
    // 외상 상세내역을 문자열로 생성
    val shareText = buildString {
        append("[외상 내역] ${person.name}\n")
        append("전화번호: ${person.phone.ifEmpty { "미등록" }}\n\n")
        
        person.entries.forEachIndexed { idx, entry ->
            // 날짜 포맷 변환 (yyyy-MM-dd -> MM/dd)
            val dateText = if (entry.date.contains("-")) {
                val parts = entry.date.split("-")
                if (parts.size >= 3) "${parts[1]}/${parts[2]}" else entry.date
            } else entry.date
            
            append("${idx + 1}. ${dateText} ${entry.departure}→${entry.destination} ${NumberFormat.getNumberInstance().format(entry.amount)}원\n")
        }
        
        append("\n총 외상 금액: ${NumberFormat.getNumberInstance().format(person.amount)}원")
    }
    
    // 문자 전송 Intent 생성
    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("smsto:${person.phone}")
        putExtra("sms_body", shareText)
    }
    
    // 문자 앱이 있는지 확인 후 실행
    if (smsIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(smsIntent)
    } else {
        // 문자 앱이 없는 경우 일반 공유로 대체
        val generalShareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(generalShareIntent, "외상 내역 공유")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
} 
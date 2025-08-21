package com.example.calldetector.ui

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

// 기사 정보 데이터 클래스
data class DriverInfo(
    val id: String,
    val name: String,
    val status: String,
    val phone: String = ""
)

// 콜 정보 데이터 클래스
data class CallInfo(
    val phoneNumber: String,
    val customerName: String? = null,
    val customerAddress: String? = null
)

@Composable
fun DispatchDialog(
    callInfo: CallInfo,
    availableDrivers: List<DriverInfo>,
    onDriverSelect: (DriverInfo) -> Unit,
    onHold: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새로운 호출 접수", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 호출 정보 표시
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = callInfo.phoneNumber,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        callInfo.customerName?.let { 
                            Text(
                                text = it,
                                color = Color.White
                            ) 
                        }
                        callInfo.customerAddress?.let { 
                            Text(
                                text = it,
                                color = Color.White
                            ) 
                        }
                    }
                }
                
                // 대기중인 기사 목록
                if (availableDrivers.isNotEmpty()) {
                    Text("대기중인 기사 선택:", fontWeight = FontWeight.Medium)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(availableDrivers) { driver ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDriverSelect(driver)
                                    },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = driver.name,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = when(driver.status) {
                                            "WAITING" -> "대기중"
                                            "BUSY" -> "운행중"
                                            else -> driver.status
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (driver.status == "WAITING") Color.Green else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "현재 대기중인 기사가 없습니다.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onHold) { 
                    Text("보류") 
                }

                TextButton(onClick = onShare) { 
                    Text("공유") 
                }
                
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { 
                    Text("삭제") 
                }
            }
        }
    )
}
package com.designated.callmanager.ui.drivermanagement

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.DriverInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverManagementScreen(
    regionId: String,
    officeId: String,
    viewModel: DriverManagementViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val pendingDrivers by viewModel.pendingDrivers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // regionId와 officeId가 유효할 때만 데이터 로딩
    LaunchedEffect(regionId, officeId) {
        if (regionId.isNotBlank() && officeId.isNotBlank()) {
            viewModel.fetchPendingDrivers(regionId, officeId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("기사 승인 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            if (isLoading && pendingDrivers.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (pendingDrivers.isEmpty()) {
                Text("승인 대기 중인 기사가 없습니다.", modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = pendingDrivers,
                        key = { driver -> driver.id }
                    ) { driver ->
                        PendingDriverItem(
                            driver = driver,
                            onApproveClick = {
                                viewModel.approveDriver(regionId, officeId, driver.id)
                                Toast.makeText(context, "${driver.name} 기사님을 승인했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            onRejectClick = {
                                viewModel.rejectDriver(regionId, officeId, driver.id)
                                Toast.makeText(context, "${driver.name} 기사님을 거절했습니다.", Toast.LENGTH_SHORT).show()
                            },
                            isLoading = isLoading // 전체 로딩 상태를 공유
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendingDriverItem(
    driver: DriverInfo,
    onApproveClick: () -> Unit,
    onRejectClick: () -> Unit,
    isLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(driver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("연락처: ${driver.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
                // createdAt 표시 (필요 시)
                // val formattedDate = driver.createdAt?.toDate()?.let { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it) } ?: "정보 없음"
                // Text("요청일: $formattedDate", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onApproveClick,
                    enabled = !isLoading
                ) {
                    Text("승인")
                }
                Button(
                    onClick = onRejectClick,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("거절")
                }
            }
        }
    }
} 
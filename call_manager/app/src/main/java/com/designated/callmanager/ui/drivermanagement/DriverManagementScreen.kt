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
    viewModel: DriverManagementViewModel = viewModel(
        factory = AndroidViewModelFactory(LocalContext.current.applicationContext as Application)
    ),
    onNavigateBack: () -> Unit
) {
    val pendingDrivers by viewModel.pendingDrivers.collectAsStateWithLifecycle()
    val approvalState by viewModel.approvalState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(approvalState) {
        when (val state = approvalState) {
            is ApprovalState.Success -> {
                Toast.makeText(context, "기사(ID: ${state.driverId}) 승인 완료", Toast.LENGTH_SHORT).show()
                 viewModel.resetApprovalState()
            }
            is ApprovalState.Error -> {
                snackbarHostState.showSnackbar(
                    message = "승인 오류: ${state.message}",
                    duration = SnackbarDuration.Long
                )
                 viewModel.resetApprovalState()
            }
            else -> { /* Idle, Loading */ }
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
        },
         snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (pendingDrivers.isEmpty()) {
                item {
                    Text("승인 대기 중인 기사가 없습니다.", modifier = Modifier.padding(16.dp))
                }
            } else {
                items<DriverInfo>(
                    items = pendingDrivers,
                    key = { driver -> driver.id }
                ) { driver ->
                    PendingDriverItem(
                        driver = driver,
                        onApproveClick = {
                            viewModel.approveDriver(driver.id)
                        },
                        isLoading = approvalState is ApprovalState.Loading
                    )
                }
            }
        }
    }
}

@Composable
fun PendingDriverItem(
    driver: DriverInfo,
    onApproveClick: () -> Unit,
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
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(driver.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("전화번호: ${driver.phoneNumber}", style = MaterialTheme.typography.bodyMedium)
                Text("상태: ${driver.status}", style = MaterialTheme.typography.bodySmall)
            }
            Button(
                onClick = onApproveClick,
                 enabled = !isLoading,
                 modifier = Modifier.padding(start = 16.dp)
            ) {
                if (isLoading) {
                     CircularProgressIndicator(modifier = Modifier.size(20.dp))
                 } else {
                     Text("승인")
                 }
            }
        }
    }
}

internal class AndroidViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DriverManagementViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DriverManagementViewModel(application) as T
        } else if (modelClass.isAssignableFrom(com.designated.callmanager.ui.dashboard.DashboardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return com.designated.callmanager.ui.dashboard.DashboardViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
} 
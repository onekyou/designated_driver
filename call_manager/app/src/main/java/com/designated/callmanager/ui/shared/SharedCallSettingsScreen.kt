package com.designated.callmanager.ui.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.SharedCallInfo
import com.designated.callmanager.ui.dashboard.DashboardViewModel

@Composable
fun SharedCallSettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    val sharedCalls by viewModel.sharedCalls.collectAsState()
    val myOfficeId by viewModel.officeId.collectAsState()
    val (tabIndex, setTabIndex) = remember { mutableStateOf(0) }

    val myPublished = sharedCalls.filter { it.sourceOfficeId == myOfficeId }
    val myClaimed   = sharedCalls.filter { it.claimedOfficeId == myOfficeId }

    Scaffold(topBar = {
        TopAppBar(title = { Text("공유 콜 관리") }, navigationIcon = {
            IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "back") }
        })
    }) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex==0, onClick={setTabIndex(0)}, text={Text("내가 올린")})
                Tab(selected = tabIndex==1, onClick={setTabIndex(1)}, text={Text("내가 수락")})
            }
            LazyColumn(Modifier.fillMaxSize()) {
                val list = if(tabIndex==0) myPublished else myClaimed
                items(list, key={it.id}) { call ->
                    SharedCallRow(call)
                }
            }
        }
    }
}

@Composable
private fun SharedCallRow(call: SharedCallInfo){
    Card(Modifier.fillMaxWidth().padding(8.dp)){
        Column(Modifier.padding(12.dp)){
            Text("${call.departure} → ${call.destination}", fontWeight = FontWeight.Bold)
            Text("요금: ${call.fare ?: 0}원  상태:${call.status}")
        }
    }
} 
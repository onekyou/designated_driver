package com.designated.driverapp.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.designated.driverapp.viewmodel.DriverViewModel
import com.designated.driverapp.data.Constants
import com.designated.driverapp.model.CallInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailsScreen(
    navController: NavController,
    viewModel: DriverViewModel,
    callId: String
) {
    // 화면이 시작될 때 한 번만 상세 정보를 로드하도록 LaunchedEffect 사용
    LaunchedEffect(callId) {
        viewModel.loadCallDetails(callId)
    }

    // ViewModel의 callDetails 상태를 관찰
    val callInfo by viewModel.callDetails.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("콜 상세 정보") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (callInfo == null) {
                // 로딩 중이거나 데이터가 없을 경우
                CircularProgressIndicator()
                Text("콜 정보를 불러오는 중입니다...")
            } else {
                // 데이터가 있을 경우
                CallDetailsContent(callInfo!!, viewModel, navController)
            }
        }
    }
}

@Composable
private fun CallDetailsContent(
    callInfo: CallInfo,
    viewModel: DriverViewModel,
    navController: NavController
) {
    var fee by remember { mutableStateOf("") }

    // 콜 정보 표시
    Text(text = "상태: ${callInfo.status}")
    Text(text = "고객 전화번호: ${callInfo.phoneNumber ?: "정보 없음"}")
    val departure = if (callInfo.departure_set.isNotBlank()) callInfo.departure_set else callInfo.customerAddress
    val destination = if (callInfo.destination_set.isNotBlank()) callInfo.destination_set else callInfo.destination
    val fareDisplay = if (callInfo.fare_set > 0) callInfo.fare_set else callInfo.fare

    Text(text = "출발지: ${departure ?: "정보 없음"}")
    Text(text = "도착지: ${destination ?: "정보 없음"}")
    Text(text = "요금: ${fareDisplay ?: 0}원")
    
    Spacer(modifier = Modifier.height(32.dp))

    // 상태에 따라 다른 UI 표시
    when (callInfo.status) {
        Constants.STATUS_ASSIGNED -> {
            // 배차됨 상태: 수락/거절 버튼 표시
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = {
                    // 거절 로직 (필요 시 ViewModel에 추가)
                    navController.popBackStack()
                }) {
                    Text("거절")
                }
                Button(onClick = {
                    viewModel.acceptCall(callInfo.id)
                }) {
                    Text("수락")
                }
            }
        }
        Constants.STATUS_ACCEPTED -> {
            // 수락됨 상태: 운행 정보 입력 및 운행 시작 버튼 표시
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it },
                    label = { Text("운행 요금") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        // "운행 시작" 로직 (ViewModel에 추가 필요)
                        // viewModel.startDriving(callInfo.id, fee.toIntOrNull() ?: 0)
                    },
                    enabled = fee.isNotBlank()
                ) {
                    Text("운행 시작")
                }
            }
        }
        // 다른 상태들에 대한 UI 처리 (예: 운행 중)
        else -> {
            Text("현재 상태: ${callInfo.status}")
        }
    }
} 
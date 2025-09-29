package com.designated.customer.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.designated.customer.service.CallService
import com.designated.customer.service.LocationService

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    regionId: String,
    officeId: String,
    phoneNumber: String,
    onLogout: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // 임시로 ViewModel을 여기서 생성
    val viewModel = remember {
        MainViewModel(
            callService = CallService(regionId = regionId, officeId = officeId),
            locationService = LocationService(context),
            regionId = regionId,
            officeId = officeId,
            phoneNumber = phoneNumber,
            context = context
        )
    }
    val uiState = viewModel.uiState

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 상단 사무실 정보
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "연결된 사무실",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = officeId,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 위치 입력
        LocationInputSection(
            currentLocation = uiState.currentLocation,
            destinationLocation = uiState.destinationLocation,
            onCurrentLocationChange = viewModel::updateCurrentLocation,
            onDestinationLocationChange = viewModel::updateDestinationLocation,
            onGetCurrentLocation = viewModel::getCurrentLocation,
            isLoadingLocation = uiState.isLoadingLocation
        )

        Spacer(modifier = Modifier.weight(1f))

        // 대형 콜 버튼
        CallButton(
            onCallPressed = viewModel::requestCall,
            isEnabled = uiState.canRequestCall,
            isLoading = uiState.isLoadingCall
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 콜 상태 표시
        if (uiState.callStatus != null) {
            CallStatusCard(
                status = uiState.callStatus,
                onCancelCall = viewModel::cancelCall
            )
        }

        // 에러 메시지
        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = uiState.error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun LocationInputSection(
    currentLocation: String,
    destinationLocation: String,
    onCurrentLocationChange: (String) -> Unit,
    onDestinationLocationChange: (String) -> Unit,
    onGetCurrentLocation: () -> Unit,
    isLoadingLocation: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "위치 정보",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 현재 위치
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = currentLocation,
                    onValueChange = onCurrentLocationChange,
                    label = { Text("출발지") },
                    placeholder = { Text("현재 위치를 입력하세요") },
                    modifier = Modifier.weight(1f),
                    leadingIcon = {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "출발지"
                        )
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onGetCurrentLocation,
                    enabled = !isLoadingLocation,
                    modifier = Modifier.size(56.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    if (isLoadingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = "현재 위치"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 목적지
            OutlinedTextField(
                value = destinationLocation,
                onValueChange = onDestinationLocationChange,
                label = { Text("목적지") },
                placeholder = { Text("목적지를 입력하세요") },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = "목적지"
                    )
                }
            )
        }
    }
}

@Composable
private fun CallButton(
    onCallPressed: () -> Unit,
    isEnabled: Boolean,
    isLoading: Boolean
) {
    Button(
        onClick = onCallPressed,
        enabled = isEnabled && !isLoading,
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = "전화",
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "대리운전 호출",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CallStatusCard(
    status: CallStatus,
    onCancelCall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status.state) {
                CallState.REQUESTED -> MaterialTheme.colorScheme.secondaryContainer
                CallState.ASSIGNED -> MaterialTheme.colorScheme.primaryContainer
                CallState.DRIVER_ARRIVING -> MaterialTheme.colorScheme.primaryContainer
                CallState.IN_PROGRESS -> MaterialTheme.colorScheme.tertiaryContainer
                CallState.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                CallState.CANCELLED -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = status.getStatusText(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (status.state == CallState.REQUESTED) {
                    TextButton(onClick = onCancelCall) {
                        Text("취소")
                    }
                }
            }

            if (status.driverInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "기사: ${status.driverInfo.name}",
                    fontSize = 14.sp
                )
                Text(
                    text = "차량: ${status.driverInfo.vehicleNumber}",
                    fontSize = 14.sp
                )
                if (status.driverInfo.phoneNumber.isNotEmpty()) {
                    Text(
                        text = "연락처: ${status.driverInfo.phoneNumber}",
                        fontSize = 14.sp
                    )
                }
            }

            if (status.estimatedArrivalTime > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "예상 도착: ${status.estimatedArrivalTime}분",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
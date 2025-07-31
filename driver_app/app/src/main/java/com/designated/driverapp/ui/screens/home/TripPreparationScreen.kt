package com.designated.driverapp.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.designated.driverapp.model.CallInfo
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
// **Log import 추가**
import android.util.Log // 이 줄을 추가하세요!

// 딥 옐로우 컬러 정의
private val DeepYellow = Color(0xFFFFB000)
private val DarkBackground = Color(0xFF1A1A1A)
private val CardBackground = Color(0xFF2A2A2A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripPreparationScreen(
    callInfo: CallInfo,
    onStartDriving: (departure: String, destination: String, waypoints: String, fare: Int) -> Unit,
    onCancel: (cancelReason: String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val geocoder = remember { Geocoder(context, Locale.getDefault()) }
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // 초기값을 callInfo로부터 즉시 설정하여 첫 렌더링에 반영
    var departure by remember(callInfo.id) {
        mutableStateOf(
            if (callInfo.departure_set.isNotBlank()) callInfo.departure_set
            else "" // 빈 상태로 시작 (도착지와 동일)
        )
    }

    var destination by remember(callInfo.id) {
        mutableStateOf(
            if (callInfo.destination_set.isNotBlank()) callInfo.destination_set
            else callInfo.destination
        )
    }

    var waypoints by remember(callInfo.id) {
        mutableStateOf(callInfo.waypoints_set ?: "")
    }

    var fare by remember(callInfo.id) {
        val initialFare = when {
            callInfo.fare_set != 0 -> callInfo.fare_set
            callInfo.fare != 0 -> callInfo.fare
            else -> 0
        }
        mutableStateOf(if (initialFare != 0) initialFare.toString() else "")
    }
    var isLoadingLocation by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    var showCancelDialog by remember { mutableStateOf(false) }


    // 위치 권한 요청 런처
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 권한이 허용되면 현재 위치 가져오기
            coroutineScope.launch {
                isLoadingLocation = true
                try {
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            // 간단한 주소만 표시 (예: 용문면 다문리 211-1)
                            val simpleAddress = buildString {
                                address.subLocality?.let { append("$it ") }
                                address.thoroughfare?.let { append("$it ") }
                                address.subThoroughfare?.let { append(it) }
                            }.trim().ifEmpty { "현재 위치" }
                            departure = simpleAddress
                        }
                    }
                } catch (e: Exception) {
                    departure = "위치를 가져올 수 없음"
                } finally {
                    isLoadingLocation = false
                }
            }
        }
    }

    // 현재 위치 가져오기 함수
    fun getCurrentLocation() {
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
            PackageManager.PERMISSION_GRANTED -> {
                coroutineScope.launch {
                    isLoadingLocation = true
                    try {
                        val location = fusedLocationClient.lastLocation.await()
                        if (location != null) {
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (!addresses.isNullOrEmpty()) {
                                val address = addresses[0]
                                // 간단한 주소만 표시 (예: 용문면 다문리 211-1)
                                val simpleAddress = buildString {
                                    address.subLocality?.let { append("$it ") }
                                    address.thoroughfare?.let { append("$it ") }
                                    address.subThoroughfare?.let { append(it) }
                                }.trim().ifEmpty { "현재 위치" }
                                departure = simpleAddress
                            }
                        }
                    } catch (e: Exception) {
                        departure = "위치를 가져올 수 없음"
                    } finally {
                        isLoadingLocation = false
                    }
                }
            }
            else -> {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    // Scaffold 구성 (상단 AppBar + 내용)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("운행 준비", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->

        // LaunchedEffect 제거 (초기화는 remember 블록에서 처리)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            
            // 공유콜 정보 카드
            if (callInfo.callType == "SHARED") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50).copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "공유콜",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                "공유콜",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                "다른 사무실에서 공유한 콜입니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // 출발지 입력
            OutlinedTextField(
                value = departure,
                onValueChange = { departure = it },
                label = { Text("출발지") },
                trailingIcon = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (departure.isNotBlank()) {
                            IconButton(onClick = { departure = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "지우기")
                            }
                        }
                        IconButton(onClick = { getCurrentLocation() }, enabled = !isLoadingLocation) {
                            if (isLoadingLocation) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.MyLocation, contentDescription = "현재 위치")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 도착지 입력
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text("도착지") },
                trailingIcon = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (destination.isNotBlank()) {
                            IconButton(onClick = { destination = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "지우기")
                            }
                        }
                        IconButton(onClick = {
                            destination = if (callInfo.customerAddress.isNotBlank()) callInfo.customerAddress else callInfo.destination
                        }) {
                            Icon(Icons.Default.LocationOn, contentDescription = "손님 주소 입력")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 경유지 입력
            OutlinedTextField(
                value = waypoints,
                onValueChange = { waypoints = it },
                label = { Text("경유지 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 요금 입력
            OutlinedTextField(
                value = fare,
                onValueChange = { fare = it.filter { ch -> ch.isDigit() } },
                label = { Text("요금 (원)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 고객 전화 걸기 버튼
            if (callInfo.phoneNumber.isNotBlank()) {
                val isSharedCall = callInfo.callType == "SHARED"
                
                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${callInfo.phoneNumber}"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isSharedCall) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)) // 공유콜은 초록색
                    } else {
                        ButtonDefaults.buttonColors() // 기본 색상
                    }
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (isSharedCall) "공유콜 고객에게 전화하기" else "고객에게 전화하기"
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 버튼 영역 - 운행 시작과 취소 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 취소 버튼
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("취소", fontWeight = FontWeight.Bold)
                }
                
                // 운행 시작 버튼
                Button(
                    onClick = {
                        val fareInt = fare.toIntOrNull() ?: 0
                        onStartDriving(departure, destination, waypoints, fareInt)
                    },
                    modifier = Modifier
                        .weight(2f)
                        .height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepYellow)
                ) {
                    Text("운행 시작", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    
    // 취소 확인 다이얼로그
    if (showCancelDialog) {
        var selectedReason by remember { mutableStateOf("운행취소") }
        var isDropdownExpanded by remember { mutableStateOf(false) }
        val cancelReasons = listOf("운행취소", "통화불가", "보류")
        
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("운행 취소", fontWeight = FontWeight.Bold) },
            text = { 
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("운행을 취소하시겠습니까?\n취소 시 호출이 보류 상태로 변경되며, 다른 기사가 배정받을 수 있습니다.")
                    
                    // 취소 사유 선택 드롭다운
                    ExposedDropdownMenuBox(
                        expanded = isDropdownExpanded,
                        onExpandedChange = { isDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedReason,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("취소 사유") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = isDropdownExpanded,
                            onDismissRequest = { isDropdownExpanded = false }
                        ) {
                            cancelReasons.forEach { reason ->
                                DropdownMenuItem(
                                    text = { Text(reason) },
                                    onClick = {
                                        selectedReason = reason
                                        isDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelDialog = false
                        onCancel(selectedReason)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("취소하기")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("계속하기")
                }
            }
        )
    }
}
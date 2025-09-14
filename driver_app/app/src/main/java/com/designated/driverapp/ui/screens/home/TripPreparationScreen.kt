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
import Log

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

    var departure by remember(callInfo.id) {
        mutableStateOf(
            if (callInfo.departure_set.isNotBlank()) callInfo.departure_set
            else ""
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

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            coroutineScope.launch {
                isLoadingLocation = true
                try {
                    val location = fusedLocationClient.lastLocation.await()
                    if (location != null) {
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

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

            OutlinedTextField(
                value = waypoints,
                onValueChange = { waypoints = it },
                label = { Text("경유지 (선택)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = fare,
                onValueChange = { fare = it.filter { ch -> ch.isDigit() } },
                label = { Text("요금 (원)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (callInfo.phoneNumber.isNotBlank()) {
                val isSharedCall = callInfo.callType == "SHARED"

                Button(
                    onClick = {
                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${callInfo.phoneNumber}"))
                        context.startActivity(dialIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (isSharedCall) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    } else {
                        ButtonDefaults.buttonColors()
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
package com.designated.customer.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.designated.customer.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.*
import java.text.NumberFormat
import com.designated.customer.service.CallService
import com.designated.customer.service.LocationService
import com.designated.customer.service.PointService
import com.designated.customer.data.model.CustomerGrade
import androidx.compose.ui.graphics.Color
import com.designated.customer.util.VoiceInputHelper

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    provinceId: String,
    cityId: String,
    officeId: String,
    phoneNumber: String,
    customerInfo: com.designated.customer.data.model.CustomerInfo?,

) {
    val context = LocalContext.current

    // 위치 권한 (현재 위치 사용)
    val locationPermission = rememberPermissionState(android.Manifest.permission.ACCESS_FINE_LOCATION)

    // 음성 인식 권한
    val audioPermission = rememberPermissionState(android.Manifest.permission.RECORD_AUDIO)

    // VoiceInputHelper 초기화
    val voiceInputHelper = remember { VoiceInputHelper(context) }

    // ViewModel 생성
    val viewModel = remember(customerInfo) {
        MainViewModel(
            callService = CallService(provinceId = provinceId, cityId = cityId, officeId = officeId),
            locationService = LocationService(context),
            pointService = PointService(provinceId = provinceId, cityId = cityId, officeId = officeId),
            provinceId = provinceId,
            cityId = cityId,
            officeId = officeId,
            phoneNumber = phoneNumber,
            customerInfo = customerInfo,
            context = context
        )
    }
    val uiState = viewModel.uiState

    // customerInfo가 변경될 때 homeAddress 로그 출력
    LaunchedEffect(customerInfo) {
        android.util.Log.d("HomeScreen", "customerInfo changed - homeAddress: ${customerInfo?.homeAddress}")
    }

    // VoiceInputHelper cleanup
    DisposableEffect(Unit) {
        onDispose {
            voiceInputHelper.destroy()
        }
    }

    // 에러 메시지가 있을 때 백버튼으로 에러 지우기
    BackHandler(enabled = uiState.error != null) {
        viewModel.clearError()
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 상단 헤더 - 사무실명
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.officeName.ifEmpty { officeId },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // 포인트/등급 카드
            PointGradeCard(
                grade = customerInfo?.grade ?: "bronze",
                currentPoints = uiState.customerPoints?.currentPoints ?: 0,
                modifier = Modifier
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 하단: 전화호출/앱호출 버튼 (세로 배치)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // 전화호출 버튼
                CircularMenuButton(
                    icon = Icons.Default.Phone,
                    label = "전화호출",
                    onClick = {
                        if (viewModel.officePhone.isNotEmpty()) {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:${viewModel.officePhone}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )

                // 앱호출 버튼
                CircularMenuButton(
                    icon = Icons.Default.LocationOn,
                    label = "앱호출",
                    onClick = viewModel::toggleLocationCard
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 슬로건
            SloganBanner(slogan = uiState.slogan)

            Spacer(modifier = Modifier.height(16.dp))

            // 에러 메시지
            if (uiState.error != null) {
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

        // 콜 상태 다이얼로그 (운행 완료 시 자동으로 닫힘)
        if (uiState.callStatus != null && uiState.callStatus.state != CallState.COMPLETED) {
            CallStatusDialog(
                status = uiState.callStatus,
                onCancelCall = viewModel::cancelCall,
                onDismiss = { /* 다이얼로그는 자동으로 닫히지 않음 */ }
            )
        }

        // 포인트 적립 완료 팝업
        if (uiState.showPointsEarnedDialog) {
            PointsEarnedDialog(
                earnedPoints = uiState.earnedPoints,
                fare = uiState.rideCompletedFare,
                currentPoints = uiState.customerPoints?.currentPoints ?: 0,
                usedPoints = uiState.usedPoints,  // ✅ 추가: 사용한 포인트 전달
                onDismiss = viewModel::dismissPointsEarnedDialog
            )
        }

        // 앱호출 바텀시트
        if (uiState.showLocationCard) {
            LocationBottomSheet(
                currentLocation = uiState.currentLocation,
                destinationLocation = uiState.destinationLocation,
                onCurrentLocationChange = viewModel::updateCurrentLocation,
                onDestinationLocationChange = viewModel::updateDestinationLocation,
                onGetCurrentLocation = {
                    // 위치 권한 체크 및 요청
                    if (locationPermission.status.isGranted) {
                        viewModel.getCurrentLocation()
                    } else {
                        locationPermission.launchPermissionRequest()
                    }
                },
                isLoadingLocation = uiState.isLoadingLocation,
                onCallPressed = viewModel::requestCall,
                isEnabled = uiState.canRequestCall,
                isLoading = uiState.isLoadingCall,
                pointsToUse = if (uiState.usePoints) uiState.pointsToUse else 0,
                onDismiss = viewModel::toggleLocationCard,
                homeAddress = uiState.homeAddress,
                onHomeAddressClick = viewModel::onHomeAddressClick,
                // 음성 인식 관련
                isRecordingDeparture = uiState.isRecordingDeparture,
                isRecordingDestination = uiState.isRecordingDestination,
                onStartRecordingDeparture = {
                    if (audioPermission.status.isGranted) {
                        viewModel.startRecordingDeparture()
                        voiceInputHelper.startListening { result ->
                            viewModel.stopRecordingDeparture(result)
                        }
                    } else {
                        audioPermission.launchPermissionRequest()
                    }
                },
                onStartRecordingDestination = {
                    if (audioPermission.status.isGranted) {
                        viewModel.startRecordingDestination()
                        voiceInputHelper.startListening { result ->
                            viewModel.stopRecordingDestination(result)
                        }
                    } else {
                        audioPermission.launchPermissionRequest()
                    }
                },
                onStopRecording = {
                    voiceInputHelper.stopListening()
                    viewModel.cancelRecording()
                }
            )
        }

        // 위치 권한이 부여된 후 자동으로 현재 위치 가져오기
        LaunchedEffect(locationPermission.status.isGranted) {
            if (locationPermission.status.isGranted && uiState.showLocationCard && uiState.currentLocation.isEmpty()) {
                // 권한이 방금 부여되었고, 바텀시트가 열려있고, 출발지가 비어있으면 자동으로 위치 가져오기
                viewModel.getCurrentLocation()
            }
        }

        // 집주소 다이얼로그
        if (uiState.showHomeAddressDialog) {
            HomeAddressDialog(
                currentAddress = uiState.homeAddress,
                onSave = viewModel::saveHomeAddress,
                onDismiss = viewModel::closeHomeAddressDialog
            )
        }

    }
}

@Composable
private fun SloganBanner(
    slogan: String,
    modifier: Modifier = Modifier
) {
    val pretendard = FontFamily(Font(R.font.pretendard_bold, FontWeight.Bold))

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.bg_point_card),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            text = slogan,
            fontFamily = pretendard,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFFF5F5F5),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PointGradeCard(
    grade: String,
    currentPoints: Int,
    modifier: Modifier = Modifier
) {
    val customerGrade = CustomerGrade.fromString(grade)
    val nextGrade = customerGrade.nextGrade()
    val mainColor = Color(0xFFFFAB00)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 등급 + 포인트
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = mainColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = customerGrade.displayName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = mainColor
                    )
                }
                Text(
                    text = "${NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(currentPoints)}P",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = mainColor
                )
            }

            // 적립률 안내
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "적립률 ${(customerGrade.pointRate * 100).toInt()}%" +
                    if (nextGrade != null) " · ${nextGrade.displayName} 승급 시 ${(nextGrade.pointRate * 100).toInt()}%" else " · 최고 등급",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun CircularMenuButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 메인 컬러 사용
    val mainColor = MaterialTheme.colorScheme.primary
    val lightColor = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .size(157.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = lightColor.copy(alpha = 0.3f),
                contentColor = mainColor
            ),
            border = BorderStroke(3.dp, mainColor),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(50.dp),
                        tint = mainColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = mainColor
                    )
                }
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
    isLoadingLocation: Boolean,
    homeAddress: String,
    onHomeAddressClick: () -> Unit,
    // 음성 인식 관련
    isRecordingDeparture: Boolean,
    isRecordingDestination: Boolean,
    onStartRecordingDeparture: () -> Unit,
    onStartRecordingDestination: () -> Unit,
    onStopRecording: () -> Unit
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

            // 출발지 (우측에 위치 아이콘, 음성 인식 아이콘)
            OutlinedTextField(
                value = currentLocation,
                onValueChange = onCurrentLocationChange,
                label = { Text("출발지") },
                placeholder = { Text("현재 위치를 입력하세요") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        // 음성 인식 아이콘
                        IconButton(
                            onClick = {
                                if (isRecordingDeparture) {
                                    onStopRecording()
                                } else {
                                    onStartRecordingDeparture()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "음성 입력",
                                tint = if (isRecordingDeparture) Color.Red else Color(0xFFFFAB00)
                            )
                        }

                        // 현재 위치 아이콘
                        IconButton(
                            onClick = onGetCurrentLocation,
                            enabled = !isLoadingLocation
                        ) {
                            if (isLoadingLocation) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = "현재 위치",
                                    tint = Color(0xFFFFAB00)
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 목적지 (우측에 음성 인식, 집주소 아이콘)
            OutlinedTextField(
                value = destinationLocation,
                onValueChange = onDestinationLocationChange,
                label = { Text("목적지") },
                placeholder = { Text("목적지를 입력하세요") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        // 음성 인식 아이콘
                        IconButton(
                            onClick = {
                                if (isRecordingDestination) {
                                    onStopRecording()
                                } else {
                                    onStartRecordingDestination()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "음성 입력",
                                tint = if (isRecordingDestination) Color.Red else Color(0xFFFFAB00)
                            )
                        }

                        // 집주소 아이콘 (집주소가 있으면 입력, 없으면 다이얼로그)
                        IconButton(onClick = onHomeAddressClick) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "집주소",
                                tint = Color(0xFFFFAB00)
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun CallButton(
    onCallPressed: () -> Unit,
    isEnabled: Boolean,
    isLoading: Boolean,
    pointsToUse: Int = 0
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
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

                if (pointsToUse > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(pointsToUse)}P 사용",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CallStatusDialog(
    status: CallStatus,
    onCancelCall: () -> Unit,
    onDismiss: () -> Unit
) {
    // 깜박이는 애니메이션 (0.5초 주기)
    val infiniteTransition = rememberInfiniteTransition(label = "blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // 커스텀 다이얼로그를 Box로 구현하여 위치 조정
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // 배경 dim 효과
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {
                    // 배경 클릭 시 REQUESTED 상태에서만 닫기
                    if (status.state == CallState.REQUESTED) {
                        onDismiss()
                    }
                }
        )

        // AlertDialog 스타일의 다이얼로그 (하단에 위치)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp)
                .padding(bottom = 180.dp),  // 하단에서 180dp 위 (앱호출/전화호출 버튼 가리기)
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 아이콘 (REQUESTED 상태에서 깜박임)
                Icon(
                    imageVector = when (status.state) {
                        CallState.REQUESTED -> Icons.Default.Phone
                        CallState.ASSIGNED, CallState.DRIVER_ARRIVING -> Icons.Default.LocationOn
                        CallState.IN_PROGRESS -> Icons.Default.Phone
                        else -> Icons.Default.Phone
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer(alpha = if (status.state == CallState.REQUESTED) alpha else 1f),
                    tint = MaterialTheme.colorScheme.primary  // 모든 상태에서 메인 컬러 사용
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 제목 (REQUESTED 상태에서 깜박임)
                Text(
                    text = status.getStatusText(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.graphicsLayer(alpha = if (status.state == CallState.REQUESTED) alpha else 1f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 내용
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,  // 중앙 정렬로 변경
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (status.driverInfo != null) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "배정된 기사 정보",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // 기사 정보 카드
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${status.driverInfo.name} 기사",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    if (status.driverInfo.phoneNumber.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // 전화 버튼
                                        val context = LocalContext.current
                                        Button(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL).apply {
                                                    data = Uri.parse("tel:${status.driverInfo.phoneNumber}")
                                                }
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary
                                            )
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Phone,
                                                contentDescription = "전화하기",
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "기사에게 전화하기",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (status.estimatedArrivalTime > 0) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "예상 도착 시간",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${status.estimatedArrivalTime}분",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (status.state == CallState.REQUESTED) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "기사 배정을 기다리고 있습니다...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center  // 중앙 정렬
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 버튼
                if (status.state == CallState.REQUESTED ||
                    status.state == CallState.ASSIGNED ||
                    status.state == CallState.DRIVER_ARRIVING) {
                    TextButton(
                        onClick = onCancelCall,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("콜 취소")
                    }
                } else {
                    TextButton(onClick = onDismiss) {
                        Text("확인")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationBottomSheet(
    currentLocation: String,
    destinationLocation: String,
    onCurrentLocationChange: (String) -> Unit,
    onDestinationLocationChange: (String) -> Unit,
    onGetCurrentLocation: () -> Unit,
    isLoadingLocation: Boolean,
    onCallPressed: () -> Unit,
    isEnabled: Boolean,
    isLoading: Boolean,
    pointsToUse: Int,
    onDismiss: () -> Unit,
    homeAddress: String,
    onHomeAddressClick: () -> Unit,
    // 음성 인식 관련
    isRecordingDeparture: Boolean,
    isRecordingDestination: Boolean,
    onStartRecordingDeparture: () -> Unit,
    onStartRecordingDestination: () -> Unit,
    onStopRecording: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(sheetState) {
        sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxHeight(0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "앱 호출",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 위치 입력 섹션
            LocationInputSection(
                currentLocation = currentLocation,
                destinationLocation = destinationLocation,
                onCurrentLocationChange = onCurrentLocationChange,
                onDestinationLocationChange = onDestinationLocationChange,
                onGetCurrentLocation = onGetCurrentLocation,
                isLoadingLocation = isLoadingLocation,
                homeAddress = homeAddress,
                onHomeAddressClick = onHomeAddressClick,
                isRecordingDeparture = isRecordingDeparture,
                isRecordingDestination = isRecordingDestination,
                onStartRecordingDeparture = onStartRecordingDeparture,
                onStartRecordingDestination = onStartRecordingDestination,
                onStopRecording = onStopRecording
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 호출 버튼
            CallButton(
                onCallPressed = onCallPressed,
                isEnabled = isEnabled,
                isLoading = isLoading,
                pointsToUse = pointsToUse
            )
        }
    }
}

/**
 * 집주소 추가/편집 다이얼로그
 */
@Composable
private fun HomeAddressDialog(
    currentAddress: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var address by remember { mutableStateOf(currentAddress) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (currentAddress.isEmpty()) "집주소 추가" else "집주소 변경",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("주소") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (address.isNotBlank()) {
                        onSave(address.trim())
                    }
                },
                enabled = address.isNotBlank()
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 포인트 적립/사용 완료 팝업
 */
@Composable
private fun PointsEarnedDialog(
    earnedPoints: Int,
    fare: Int,
    currentPoints: Int,
    usedPoints: Int = 0,  // ✅ 추가: 사용한 포인트
    onDismiss: () -> Unit
) {
    // 포인트 사용만 있고 적립이 없는 경우 (콜 요청 시)
    val isPointUsageOnly = usedPoints > 0 && earnedPoints == 0 && fare == 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인", fontWeight = FontWeight.Bold)
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = if (isPointUsageOnly) "포인트 사용" else "포인트 적립",
                modifier = Modifier.size(48.dp),
                tint = if (isPointUsageOnly) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = if (isPointUsageOnly) "포인트 사용 완료" else "운행 완료",
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isPointUsageOnly) "포인트가 사용되었습니다!" else "이용해 주셔서 감사합니다!",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // 요금 정보 (운행 완료 시에만 표시)
                if (!isPointUsageOnly) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "이용 요금",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(fare)}원",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 사용한 포인트 카드
                if (usedPoints > 0) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFEBEE) // 연한 빨간색 배경
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "사용한 포인트",
                                fontSize = 14.sp,
                                color = Color(0xFFD32F2F) // 빨간색
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "-${NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(usedPoints)}P",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD32F2F) // 빨간색
                            )
                        }
                    }
                }

                // 적립 포인트 (적립이 있을 때만 표시)
                if (earnedPoints > 0) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "적립 포인트",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "+${NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(earnedPoints)}P",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 현재 보유 포인트
                Text(
                    text = "현재 보유: ${NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(currentPoints)}P",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}


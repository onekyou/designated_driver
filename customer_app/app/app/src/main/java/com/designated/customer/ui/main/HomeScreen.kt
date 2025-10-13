package com.designated.customer.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.NumberFormat
import com.designated.customer.service.CallService
import com.designated.customer.service.LocationService
import com.designated.customer.service.PointService
import com.designated.customer.service.StepCounterService
import com.designated.customer.data.repository.StepRepository
import com.designated.customer.data.database.StepDatabase
import com.designated.customer.ui.components.StepCounterCard
import com.designated.customer.ui.components.StepDetailBottomSheet
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    regionId: String,
    officeId: String,
    phoneNumber: String,
    customerInfo: com.designated.customer.data.model.CustomerInfo?,
    onNavigateToDrinkingGame: () -> Unit = {}
) {
    val context = LocalContext.current

    // Android 10 (Q) 이상에서만 ACTIVITY_RECOGNITION 권한 필요
    val activityRecognitionPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(android.Manifest.permission.ACTIVITY_RECOGNITION)
    } else {
        null
    }

    // ViewModel 생성
    val viewModel = remember {
        // 만보기 관련 초기화
        val stepDatabase = StepDatabase.getInstance(context)
        val stepRepository = StepRepository(stepDatabase.stepDao())
        val stepService = StepCounterService(context, stepRepository)

        MainViewModel(
            callService = CallService(regionId = regionId, officeId = officeId),
            locationService = LocationService(context),
            pointService = PointService(regionId = regionId, officeId = officeId),
            regionId = regionId,
            officeId = officeId,
            phoneNumber = phoneNumber,
            customerInfo = customerInfo,
            context = context,
            stepRepository = stepRepository,
            stepService = stepService
        )
    }
    val uiState = viewModel.uiState

    // 권한 요청
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (activityRecognitionPermission?.status?.isGranted == false) {
                activityRecognitionPermission.launchPermissionRequest()
            }
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
            // 상단 헤더 - 사무실명과 포인트 한 줄 배치
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = officeId,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(uiState.customerPoints?.currentPoints ?: 0)}P",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFAB00)
                    )
                }
            }

            // 상단: 만보기 카드 (좌우 여백 없음, 크게)
            StepCounterCard(
                stepData = uiState.stepData?.copy(steps = uiState.currentStepsRealtime),
                sessionSteps = uiState.currentSessionSteps,
                isSessionActive = uiState.isSessionActive,
                onSettingsClick = viewModel::toggleStepDetail,
                onStartSession = viewModel::startNewSession,
                onResetSession = viewModel::resetSession,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.weight(1f))

            // 하단: 전화호출/앱호출 버튼 (세로 배치)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
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

            Spacer(modifier = Modifier.weight(1f))

            // 콜 상태 표시
            if (uiState.callStatus != null) {
                CallStatusCard(
                    status = uiState.callStatus,
                    onCancelCall = viewModel::cancelCall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

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

        // 만보기 상세 정보 바텀시트
        if (uiState.showStepDetail) {
            StepDetailBottomSheet(
                dailyData = uiState.stepData,
                weeklyData = uiState.weeklyStepData,
                monthlyData = uiState.monthlyStepData,
                sessionSteps = uiState.currentSessionSteps,
                isSessionActive = uiState.isSessionActive,
                onDismiss = viewModel::closeStepDetail,
                onResetSession = viewModel::resetSession
            )
        }

        // 앱호출 바텀시트
        if (uiState.showLocationCard) {
            LocationBottomSheet(
                currentLocation = uiState.currentLocation,
                destinationLocation = uiState.destinationLocation,
                onCurrentLocationChange = viewModel::updateCurrentLocation,
                onDestinationLocationChange = viewModel::updateDestinationLocation,
                onGetCurrentLocation = viewModel::getCurrentLocation,
                isLoadingLocation = uiState.isLoadingLocation,
                onCallPressed = viewModel::requestCall,
                isEnabled = uiState.canRequestCall,
                isLoading = uiState.isLoadingCall,
                pointsToUse = if (uiState.usePoints) uiState.pointsToUse else 0,
                onDismiss = viewModel::toggleLocationCard,
                homeAddress = uiState.homeAddress,
                onHomeAddressClick = viewModel::onHomeAddressClick,
                onEditHomeAddress = viewModel::onEditHomeAddress,
                onFavoriteAddressClick = viewModel::openFavoriteAddressSheet
            )
        }

        // 집주소 다이얼로그
        if (uiState.showHomeAddressDialog) {
            HomeAddressDialog(
                currentAddress = uiState.homeAddress,
                onSave = viewModel::saveHomeAddress,
                onDismiss = viewModel::closeHomeAddressDialog
            )
        }

        // 즐겨찾기 바텀시트
        if (uiState.showFavoriteAddressSheet) {
            FavoriteAddressBottomSheet(
                favoriteAddresses = uiState.favoriteAddresses,
                onSelectAddress = viewModel::selectFavoriteAddress,
                onAddAddress = viewModel::addFavoriteAddress,
                onDeleteAddress = viewModel::deleteFavoriteAddress,
                onDismiss = viewModel::closeFavoriteAddressSheet
            )
        }
    }
}

@Composable
private fun StepCounterButton(
    steps: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 목표 걸음수 (10000보)
    val targetSteps = 10000
    val progress = (steps.toFloat() / targetSteps).coerceIn(0f, 1f)

    // 명시적인 주황색 정의
    val orangeColor = Color(0xFFFFAB00)
    val orangeLight = Color(0xFFFFD54F)

    Box(
        modifier = modifier
            .size(160.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = orangeLight.copy(alpha = 0.2f),
                contentColor = orangeColor
            ),
            border = BorderStroke(0.dp, orangeColor),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 배경 원형 진행바 (회색)
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(145.dp),
                    strokeWidth = 10.dp,
                    color = Color.LightGray,
                    trackColor = Color.LightGray
                )

                // 진행률 원형 진행바
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(145.dp),
                    strokeWidth = 10.dp,
                    color = orangeColor
                )

                // 중앙 내용
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "만보기",
                        modifier = Modifier.size(40.dp),
                        tint = orangeColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = NumberFormat.getNumberInstance(java.util.Locale.KOREA).format(steps),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = orangeColor
                    )
                }
            }
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
    // 명시적인 주황색 정의
    val orangeColor = Color(0xFFFFAB00)
    val orangeLight = Color(0xFFFFD54F)

    Box(
        modifier = modifier
            .size(160.dp)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = orangeLight.copy(alpha = 0.2f),
                contentColor = orangeColor
            ),
            border = BorderStroke(3.dp, orangeColor),
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
                        tint = orangeColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = label,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = orangeColor
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
    onEditHomeAddress: () -> Unit,
    onFavoriteAddressClick: () -> Unit
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

            // 출발지 (우측에 위치 아이콘)
            OutlinedTextField(
                value = currentLocation,
                onValueChange = onCurrentLocationChange,
                label = { Text("출발지") },
                placeholder = { Text("현재 위치를 입력하세요") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
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
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 목적지 (우측에 집주소, 즐겨찾기 아이콘)
            OutlinedTextField(
                value = destinationLocation,
                onValueChange = onDestinationLocationChange,
                label = { Text("목적지") },
                placeholder = { Text("목적지를 입력하세요") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    Row {
                        // 집주소 아이콘
                        IconButton(onClick = onHomeAddressClick) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "집주소",
                                tint = Color(0xFFFFAB00)
                            )
                        }

                        // 집주소가 있을 때만 변경 아이콘 표시
                        if (homeAddress.isNotEmpty()) {
                            IconButton(onClick = onEditHomeAddress) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "집주소 변경",
                                    tint = Color(0xFFFFAB00)
                                )
                            }
                        }

                        // 즐겨찾기 아이콘
                        IconButton(onClick = onFavoriteAddressClick) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "즐겨찾기",
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
    onEditHomeAddress: () -> Unit,
    onFavoriteAddressClick: () -> Unit
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
                onEditHomeAddress = onEditHomeAddress,
                onFavoriteAddressClick = onFavoriteAddressClick
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
 * 즐겨찾기 주소 바텀시트
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteAddressBottomSheet(
    favoriteAddresses: List<String>,
    onSelectAddress: (String) -> Unit,
    onAddAddress: (String) -> Unit,
    onDeleteAddress: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showAddDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "즐겨찾기",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 즐겨찾기 목록
            if (favoriteAddresses.isEmpty()) {
                Text(
                    text = "저장된 즐겨찾기가 없습니다",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            } else {
                favoriteAddresses.forEach { address ->
                    FavoriteAddressItem(
                        address = address,
                        onSelect = { onSelectAddress(address) },
                        onDelete = { onDeleteAddress(address) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 추가 버튼
            OutlinedButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFFAB00)
                ),
                border = BorderStroke(1.dp, Color(0xFFFFAB00))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "추가"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("새 즐겨찾기 추가")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // 즐겨찾기 추가 다이얼로그
    if (showAddDialog) {
        AddFavoriteDialog(
            onAdd = { address ->
                onAddAddress(address)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

/**
 * 즐겨찾기 항목
 */
@Composable
private fun FavoriteAddressItem(
    address: String,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onSelect,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = address,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 즐겨찾기 추가 다이얼로그
 */
@Composable
private fun AddFavoriteDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var address by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "즐겨찾기 추가",
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
                        onAdd(address.trim())
                    }
                },
                enabled = address.isNotBlank()
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

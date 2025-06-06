package com.designated.driverapp.ui.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.designated.driverapp.model.DriverStatus
import com.designated.driverapp.model.CallInfo
import com.designated.driverapp.model.CallStatus
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel // Hilt ViewModel 주입으로 변경
import com.designated.driver.ui.home.DriverViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.designated.driver.ui.home.LocationFetchStatus
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions

@OptIn(ExperimentalMaterial3Api::class) // For TopAppBar
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: DriverViewModel // 파라미터로 받도록 수정
) {
    // --- 서비스 시작 호출 --- 
    // HomeScreen이 처음 표시될 때 서비스를 시작하도록 요청
    LaunchedEffect(Unit) {
        Log.d("HomeScreen", "HomeScreen recomposed/launched. Requesting to start DriverForegroundService.")
        viewModel.startDriverService()
    }
    // --- ---

    val context = LocalContext.current
    val scope = rememberCoroutineScope() // 코루틴 스코프 생성
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    // ViewModel 상태 구독
    val assignedCalls by viewModel.assignedCalls.collectAsStateWithLifecycle()
    val assignedCall = assignedCalls.firstOrNull()
    val driverStatus by viewModel.driverStatus.collectAsStateWithLifecycle()
    val completedCallSummary by viewModel.completedCallSummary.collectAsStateWithLifecycle()

    // --- State for popup dialog --- 
    val callInfoToShow by viewModel.callInfoForPopup.collectAsStateWithLifecycle()
    // --- ---

    // --- 로그아웃 확인 다이얼로그 --- 
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("로그아웃 확인") },
            text = { Text("정말 로그아웃하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        logoutUserAndExitApp(context, scope, viewModel)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("로그아웃")
                }
            },
            dismissButton = {
                Button(onClick = { showLogoutConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
    // --- --- 

    // --- New Call Info Popup Dialog --- 
    if (callInfoToShow != null) {
        CallDetailsPopup( // New Composable for the popup
            callInfo = callInfoToShow!!, 
            onDismiss = { viewModel.dismissCallPopup() },
            onAccept = { 
                viewModel.acceptCall(callInfoToShow!!.id)
                viewModel.dismissCallPopup() // Dismiss after action
            }
        )
    }
    // --- ---

    // --- 운행 완료 요약 팝업 ---
    if (completedCallSummary != null) {
        CompletedCallSummaryDialog(
            callInfo = completedCallSummary!!,
            onConfirm = { callId, paymentMethod, cashAmount, fareToSet, tripSummaryToSet ->
                viewModel.confirmAndFinalizeTrip(
                    callId = callId,
                    paymentMethod = paymentMethod,
                    cashAmount = cashAmount,
                    fareToSet = fareToSet,
                    tripSummaryToSet = tripSummaryToSet
                )
                navController.navigate("history_settlement") // 운행내역 페이지로 이동
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("운행준비") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp) // 섹션 간 간격 추가
        ) {
            // --- UI 상태 확인 로그 추가 --- 
            Log.d("HomeScreenUI", "Recomposing HomeScreen. DriverStatus: $driverStatus, AssignedCall ID: ${assignedCall?.id}")

            // --- 상태 관리 섹션 제거 ---
            // // 1. 상태 관리 섹션 복구 (ViewModel 상태 사용)
            // StatusManagementSection(
            //     currentStatus = driverStatus,
            //     // 기사가 운행중(BUSY)일 때는 비활성화
            //     enabled = driverStatus == DriverStatus.ONLINE || driverStatus == DriverStatus.OFFLINE,
            //     onStatusChange = { newStatus -> viewModel.updateDriverStatus(newStatus) } // ViewModel 함수 호출 연결
            // )
            // Divider() // 섹션 구분선 제거
            // --- ---

            // 2. 호출 처리 / 운행 준비 / 운행 관리 섹션 (상태에 따라 분기)
            if (assignedCall != null) {
                // --- 로그 추가: assignedCall != null 조건 통과 --- 
                Log.d("HomeScreenUI", "  -> assignedCall is NOT null. ID: ${assignedCall.id}, Status: ${assignedCall.status}")
                // UI 분기 로직 수정: PREPARING 상태 추가
                when {
                    // 1. 운행 중 상태 (최종 단계)
                    driverStatus == DriverStatus.ON_TRIP || assignedCall.statusEnum == CallStatus.INPROGRESS -> {
                        Log.d("HomeScreenUI", "    -> Showing TripManagementSection. DriverStatus: $driverStatus, CallStatus: ${assignedCall.status}")
                        
                        // <<< onComplete 람다 안정화 제거 (Revert) >>>
                        
                        // <<< onNavigate 람다 안정화 제거됨 (이전 단계에서) >>>
                        
                        // <<< --- >>>

                        TripManagementSection(
                            assignedCall = assignedCall,
                            onNavigate = { 
                                Log.d("HomeScreen", "onNavigate lambda invoked for call: \\${assignedCall?.id}")
                                viewModel.startNavigation(assignedCall) 
                            },
                            onComplete = { 
                                val callId = assignedCall?.id
                                Log.i("HomeScreen", "onComplete lambda invoked. Call ID to complete: \\${callId}")
                                if (callId != null) {
                                    Log.d("HomeScreen", "   - Calling viewModel.completeCall with ID: \\${callId}")
                                    viewModel.completeCall(callId)
                                    Log.d("HomeScreen", "   - viewModel.completeCall call finished.")
                                } else {
                                    Log.e("HomeScreen", "   - Cannot call completeCall: assignedCall or its ID is null!")
                                }
                            } 
                        )
                    }
                    // 2. 운행 준비중 상태 (중간 단계) - 신규 추가
                    driverStatus == DriverStatus.PREPARING || assignedCall.statusEnum == CallStatus.ACCEPTED -> {
                        Log.d("HomeScreenUI", "    -> Showing TripPreparationSection. DriverStatus: $driverStatus, CallStatus: ${assignedCall.status}")
                        TripPreparationSection(
                            callId = assignedCall.id,
                            assignedCall = assignedCall,
                            onStartDriving = { departure, destination, waypoints, fare -> 
                                // <<< 로그 추가: onStartDriving 람다 실행 확인 >>>
                                Log.i("HomeScreen", " 운행 시작 버튼 클릭됨. onStartDriving 람다 실행됨.")
                                Log.d("HomeScreen", "   - 전달된 값: Dep='$departure', Dest='$destination', Way='$waypoints', Fare=$fare")
                                Log.d("HomeScreen", "   - 호출 대상 콜 ID: ${assignedCall.id}")
                                Log.d("HomeScreen", "   - viewModel.startDriving 호출 시도...")
                                // <<< --- >>>
                                viewModel.startDriving(
                                    callId = assignedCall.id,
                                    departure = departure,
                                    destination = destination,
                                    waypoints = waypoints,
                                    fare = fare
                                )
                                // <<< 로그 추가: viewModel.startDriving 호출 완료 확인 >>>
                                Log.i("HomeScreen", " viewModel.startDriving 호출 완료됨.")
                                // <<< --- >>>
                            },
                            viewModel = viewModel
                        )
                    }
                    // 3. 콜 배정받은 초기 상태 (수락 전)
                    else -> { // Includes ASSIGNED status or other initial states
                        Log.d("HomeScreenUI", "    -> Showing CallHandlingSection. DriverStatus: $driverStatus, CallStatus: ${assignedCall.status}")
            CallHandlingSection(
                            assignedCall = assignedCall,
                            onAccept = { viewModel.acceptCall(assignedCall.id) }
                        )
                    }
                }
            } else {
                // --- 로그 추가: assignedCall == null 조건 통과 --- 
                Log.d("HomeScreenUI", "  -> assignedCall is null. Showing 'No assigned call' text.")
                // 배정된 콜이 없을 때
                Text("현재 배정된 콜이 없습니다.", modifier = Modifier.padding(16.dp))
            }
        }
    }
}

// --- 섹션별 Composable 함수들 ---

@Composable
fun CallHandlingSection(
    assignedCall: CallInfo?,
    onAccept: () -> Unit
) {
    // --- 상세 로그 추가: CallHandlingSection 내부 상태 확인 --- 
    Log.d("HomeScreenUI_Detail", "Inside CallHandlingSection. assignedCall is ${if (assignedCall == null) "NULL" else "NOT NULL"}")
    if (assignedCall != null) {
        Log.d("HomeScreenUI_Detail", "  Call ID: ${assignedCall.id}, Customer Name: '${assignedCall.customerName}', Phone: '${assignedCall.phoneNumber}', Pickup: '${assignedCall.customerAddress}'")
    }
    // --- --- 

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("배정된 호출", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        if (assignedCall != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("새로운 배정!", style = MaterialTheme.typography.titleMedium)
                    Text("고객: ${assignedCall.customerName}")
                    Text("전화: ${assignedCall.phoneNumber}")
                    Text("집주소: ${assignedCall.customerAddress}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { 
                            // <<< 로그 추가: 수락 버튼 클릭 확인 >>>
                            Log.i("CallHandlingSection", "!!! 수락 버튼 클릭됨 !!!")
                            Log.d("CallHandlingSection", "   - onAccept 람다 호출 시도...")
                            // <<< --- >>>
                            onAccept()
                            // <<< 로그 추가: onAccept 람다 호출 완료 확인 >>>
                            Log.i("CallHandlingSection", "   - onAccept 람다 호출 완료됨.")
                            // <<< --- >>>
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { 
                        Text("수락")
                    }
                }
            }
        } else {
            // Text("대기 중...") 
        }
    }
}

@Composable
fun TripManagementSection(
    assignedCall: CallInfo?,
    onNavigate: () -> Unit,
    onComplete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("운행 관리", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        // 운행 시작 상태를 별도로 관리해야 할 수도 있음. 여기서는 assignedCall이 있으면 운행 중으로 가정
        if (assignedCall != null) { 
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("현재 운행 정보", style = MaterialTheme.typography.titleMedium)
                    Text("고객: ${assignedCall.customerName}")
                    Text("픽업 위치: ${assignedCall.customerAddress}")
                    // Text("도착: ${assignedCall.destination}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(onClick = onNavigate) { 
                            // <<< 로그 추가: 길 안내 버튼 클릭 확인 >>>
                            Log.d("TripManagementSection", "길 안내 버튼 클릭됨")
                            // <<< --- >>>
                            Icon(Icons.Default.Navigation, contentDescription = "네비게이션")
                            Spacer(Modifier.width(4.dp))
                            Text("길 안내")
                        }
                        Button(onClick = { 
                            // <<< 로그 추가: 완료 버튼 클릭 확인 >>>
                            Log.i("TripManagementSection", "!!! 완료 버튼 클릭됨 !!!")
                            Log.d("TripManagementSection", "   - onComplete 람다 호출 시도...")
                            // <<< --- >>>
                            onComplete()
                            // <<< 로그 추가: onComplete 람다 호출 완료 확인 >>>
                            Log.i("TripManagementSection", "   - onComplete 람다 호출 완료됨.")
                            // <<< --- >>>
                        }) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "운행 완료")
                            Spacer(Modifier.width(4.dp))
                            Text("완료")
                        }
                    }
                }
            }
        } else {
            Text("현재 진행 중인 운행이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class) // FilterChip, SoftwareKeyboardController 사용
@Composable
fun TripPreparationSection(
    callId: String,
    assignedCall: CallInfo?,
    onStartDriving: (departure: String, destination: String, waypoints: String, fare: Int) -> Unit,
    viewModel: DriverViewModel = hiltViewModel() // Hilt ViewModel 주입으로 변경
) {
    // --- ViewModel 상태 구독 ---
    val currentLocationAddress by viewModel.currentLocationAddress.collectAsStateWithLifecycle()
    val locationFetchStatus by viewModel.locationFetchStatus.collectAsStateWithLifecycle()
    // --- ---

    // --- 입력 필드 상태 변수 ---
    var departureText by remember { mutableStateOf("") }
    var destinationText by remember { mutableStateOf("") }
    var waypointsText by remember { mutableStateOf("") }
    var selectedFareText by remember { mutableStateOf("") } // 운행 요금 상태를 문자열로 관리

    val context = LocalContext.current

    // --- 포커스 관리자 및 키보드 컨트롤러 --- 
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val (departureFocus, destinationFocus, waypointsFocus) = remember { FocusRequester.createRefs() }
    // --- --- 

    // --- 상세 로그 추가: TripPreparationSection 내부 상태 확인 --- 
    Log.d("HomeScreenUI_Detail", "Inside TripPreparationSection. assignedCall is ${if (assignedCall == null) "NULL" else "NOT NULL"}")
    if (assignedCall != null) {
        Log.d("HomeScreenUI_Detail", "  Call ID: ${assignedCall.id}, Customer Name: '${assignedCall.customerName}', Phone: '${assignedCall.phoneNumber}', Pickup: '${assignedCall.customerAddress}'")
    }
    // --- ---

    // --- 주소 업데이트 로직 ---
    LaunchedEffect(currentLocationAddress) {
        currentLocationAddress?.let { fullAddress ->
            // 주소를 공백으로 분리
            val addressParts = fullAddress.split(" ")
            // 마지막 3개 부분만 선택 (주소 부분이 3개 미만이면 전체 사용)
            val shortenedAddress = if (addressParts.size >= 3) {
                addressParts.takeLast(3).joinToString(" ")
            } else {
                fullAddress // 3 부분 미만이면 전체 주소 사용
            }
            // 가공된 주소로 상태 업데이트
            departureText = shortenedAddress
            Log.d("TripPreparation", "Full Address: $fullAddress, Shortened Address: $shortenedAddress")
        }
    }
    // --- ---

    // --- 위치 요청 상태 피드백 (Toast) ---
    LaunchedEffect(locationFetchStatus) {
        when (val status = locationFetchStatus) {
            is LocationFetchStatus.Loading -> {
                Toast.makeText(context, "현재 위치 찾는 중...", Toast.LENGTH_SHORT).show()
            }
            is LocationFetchStatus.Error -> {
                Toast.makeText(context, "위치 오류: ${status.message}", Toast.LENGTH_LONG).show()
            }
            else -> { /* Idle or Success */ }
        }
    }
    // --- ---

    Column(modifier = Modifier.fillMaxWidth()) {
        // 운행 준비 텍스트 제거

        // --- 카드 1: 고객 정보 및 주요 액션 ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 간단한 정보 표시
                CallInfoRow(label = "고객", value = assignedCall?.customerName ?: "")
                CallInfoRow(label = "전화", value = assignedCall?.phoneNumber ?: "")
                CallInfoRow(label = "집주소", value = assignedCall?.customerAddress ?: "")

                // 버튼 Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { // 전화 걸기 버튼
                        Log.d("TripPreparationSection", "전화 걸기 버튼 클릭됨")
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${assignedCall?.phoneNumber ?: ""}"))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Phone, contentDescription = "전화 걸기")
                        Spacer(Modifier.width(4.dp))
                        Text("전화 걸기")
                    }
                    Button(onClick = { // 운행 시작 버튼 (파라미터 전달)
                        if (departureText.isBlank()) {
                            Toast.makeText(context, "출발지를 입력하세요", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        // <<< 로그 추가: 운행 시작 버튼 클릭 확인 >>>
                        Log.i("TripPreparationSection", "!!! 운행 시작 버튼 클릭됨 !!!")
                        Log.d("TripPreparationSection", "   - 전달 예정 값: Dep='${departureText}', Dest='${destinationText}', Way='${waypointsText}', Fare=$selectedFareText")
                        Log.d("TripPreparationSection", "   - onStartDriving 람다 호출 시도...")
                        // <<< --- >>>
                        onStartDriving(
                            departureText,
                            destinationText,
                            waypointsText,
                            selectedFareText.toIntOrNull() ?: 0 // 입력값이 없으면 0
                        )
                        // <<< 로그 추가: onStartDriving 람다 호출 완료 확인 >>>
                        Log.i("TripPreparationSection", "   - onStartDriving 람다 호출 완료됨.")
                        // <<< --- >>>
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "운행 시작")
                        Spacer(Modifier.width(4.dp))
                        Text("운행 시작")
                    }
                }
            }
        }
        // --- ---

        Spacer(modifier = Modifier.height(16.dp)) // 카드 사이 간격

        // --- 카드 2: 주소 입력, 요금 및 결제 방법 ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 출발지 입력 필드
                OutlinedTextField(
                    value = departureText,
                    onValueChange = { departureText = it },
                    label = { Text("출발지 (필요시 수정)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(departureFocus), // 포커스 요청자 연결
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next // 키보드 액션: 다음
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { destinationFocus.requestFocus() } // 다음 버튼 클릭 시 도착지 필드로 포커스 이동
                    ),
                    trailingIcon = { 
                        Row { 
                            IconButton(onClick = { viewModel.fetchCurrentLocationAddress() }) {
                                Icon(Icons.Default.MyLocation, "현재 위치 가져오기")
                            }
                            if (departureText.isNotEmpty()) {
                                IconButton(onClick = { departureText = "" }) {
                                    Icon(Icons.Default.Close, "출발지 지우기")
                                }
                            }
                        }
                    }
                )
                // 도착지 입력 필드
                OutlinedTextField(
                    value = destinationText,
                    onValueChange = { destinationText = it },
                    label = { Text("도착지") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(destinationFocus), // 포커스 요청자 연결
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Next // 키보드 액션: 다음
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { waypointsFocus.requestFocus() } // 다음 버튼 클릭 시 경유지 필드로 포커스 이동
                    ),
                    trailingIcon = { 
                        Row { 
                            IconButton(onClick = { 
                                assignedCall?.customerAddress?.let { destinationText = it }
                            }) {
                                Icon(Icons.Default.Home, "집주소로 설정")
                            }
                            if (destinationText.isNotEmpty()) {
                                IconButton(onClick = { destinationText = "" }) {
                                    Icon(Icons.Default.Close, "도착지 지우기")
                                }
                            }
                        }
                    }
                )
                // 경유지 입력 필드
                OutlinedTextField(
                    value = waypointsText,
                    onValueChange = { waypointsText = it },
                    label = { Text("경유지 (선택 사항)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(waypointsFocus), // 포커스 요청자 연결
                    keyboardOptions = KeyboardOptions.Default.copy(
                        imeAction = ImeAction.Done // 키보드 액션: 완료
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { keyboardController?.hide() } // 완료 버튼 클릭 시 키보드 숨김
                    ),
                    trailingIcon = { 
                        if (waypointsText.isNotEmpty()) { 
                            IconButton(onClick = { waypointsText = "" }) {
                                Icon(Icons.Default.Close, "경유지 지우기")
                            }
                        }
                    }
                )

                Divider(modifier = Modifier.padding(vertical = 8.dp)) // 구분선

                // 운행 요금 입력란(숫자)
                OutlinedTextField(
                    value = selectedFareText,
                    onValueChange = { value ->
                        selectedFareText = value.filter { it.isDigit() }
                    },
                    label = { Text("운행 요금(숫자만 입력)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun CallInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

// --- New Composable Function for Call Details Popup --- 
@Composable
fun CallDetailsPopup(
    callInfo: CallInfo,
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val context = LocalContext.current
    val TAG = "CallDetailsPopup"

    // Use the updated field name 'customerName' in the log message
    Log.d(TAG, "CallInfoDialog: Displaying info for call ID = ${callInfo.id}, Customer = ${callInfo.customerName}")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새로운 호출 정보") },
        text = {
            Column {
                Text("고객명: ${callInfo.customerName}")
                Text("연락처: ${callInfo.phoneNumber}") 
                Text("집주소: ${callInfo.customerAddress}") 
            }
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text("수락")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("닫기")
            }
        }
    )
}
// --- --- 

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedCallSummaryDialog(
    callInfo: CallInfo,
    onConfirm: (callId: String, paymentMethod: String, cashAmount: Int?, fareToSet: Int, tripSummaryToSet: String) -> Unit
) {
    val departure = if (callInfo.departure_set.isNotBlank()) callInfo.departure_set else callInfo.customerAddress
    val destination = if (callInfo.destination_set.isNotBlank()) callInfo.destination_set else callInfo.destination
    val fare = if (callInfo.fare_set > 0) callInfo.fare_set else callInfo.fare

    // 결제방법 드롭다운 상태
    val paymentMethods = listOf("현금", "이체", "외상", "카드", "포인트", "현금+포인트")
    var paymentMethod by remember { mutableStateOf(paymentMethods[0]) }
    var expanded by remember { mutableStateOf(false) }
    var cashAmountText by remember { mutableStateOf("") }
    var cashAmountError by remember { mutableStateOf(false) }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* 사용자가 확인 없이 닫을 때는 아무 작업 안함 */ },
        title = { Text("운행 완료 요약") },
        text = {
            Column {
                Text("고객명: ${callInfo.customerName}")
                Text("출발지: $departure")
                Text("도착지: $destination")
                Text("요금: ${fare}원")
                Spacer(modifier = Modifier.height(16.dp))
                Text("결제방법", style = MaterialTheme.typography.bodyMedium)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("결제방법 선택") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        paymentMethods.forEach { method ->
                            DropdownMenuItem(
                                text = { Text(method) },
                                onClick = {
                                    paymentMethod = method
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                // 현금+포인트 선택 시 현금액 입력란 표시
                if (paymentMethod == "현금+포인트") {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cashAmountText,
                        onValueChange = {
                            cashAmountText = it.filter { c -> c.isDigit() }
                            cashAmountError = false
                        },
                        label = { Text("현금 금액 입력") },
                        placeholder = { Text("예: 10000") },
                        isError = cashAmountError,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (cashAmountError) {
                        Text("숫자만 입력하세요.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // 출발지 미입력 검증 코드 제거 (운행준비에서 이미 검증)
                if (paymentMethod == "현금+포인트" && cashAmountText.isBlank()) {
                    cashAmountError = true
                } else {
                    // --- Firestore 운행내역 저장 ---
                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val prefs = context.getSharedPreferences("driver_prefs", Context.MODE_PRIVATE)
                    val regionId = prefs.getString("regionId", null)
                    val officeId = prefs.getString("officeId", null)
                    val driverId = prefs.getString("driverId", null)
                    val driverName = prefs.getString("driverName", null)
                    android.util.Log.d("DriverAppFirestore", "운행내역 Firestore 저장 시도: regionId=$regionId, officeId=$officeId, driverId=$driverId")
                    if (regionId != null && officeId != null && driverId != null) {
                        val tripData = hashMapOf(
                            "callId" to callInfo.id,
                            "driverId" to driverId,
                            "driverName" to (driverName ?: ""),
                            "customerName" to callInfo.customerName,
                            "phoneNumber" to callInfo.phoneNumber,
                            "departure" to departure,
                            "destination" to destination,
                            "fare" to fare,
                            "paymentMethod" to paymentMethod,
                            "cashAmount" to (if (paymentMethod == "현금+포인트") cashAmountText.toIntOrNull() else null),
                            "completedAt" to System.currentTimeMillis()
                        )
                        // Firestore 저장 로직을 주석 처리했으므로, onConfirm을 직접 호출합니다.
                        onConfirm(
                            callInfo.id,
                            paymentMethod, // 사용자가 선택한 최신 결제 방법
                            if (paymentMethod == "현금+포인트") cashAmountText.toIntOrNull() else null, // 사용자가 입력한 현금액
                            fare, // 계산된 최종 요금
                            callInfo.trip_summary // 참조 오류 해결
                        )
                    } else {
                        android.util.Log.e("DriverAppFirestore", "regionId/officeId/driverId 중 null 있음: regionId=$regionId, officeId=$officeId, driverId=$driverId")
                        android.widget.Toast.makeText(context, "운행내역 저장 실패: 사무실/기사 정보가 없습니다.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    // --- 기존 로컬 저장 및 onConfirm 호출 ---
                    val prefsHistory = context.getSharedPreferences("trip_history", Context.MODE_PRIVATE)
                    val historyJson = prefsHistory.getString("history_list", "[]")
                    val historyList = org.json.JSONArray(historyJson)
                    val nextIndex = historyList.length() + 1
                    val summary = buildString {
                        append("${callInfo.customerName}, ")
                        append("$departure→$destination, ")
                        append("${fare}원, ")
                        append(paymentMethod)
                        if (paymentMethod == "현금+포인트" && cashAmountText.isNotBlank()) {
                            append("(${cashAmountText}원 현금)")
                        }
                    }
                    val numberedSummary = "$nextIndex. $summary|timestamp=${System.currentTimeMillis()}"
                    historyList.put(numberedSummary)
                    prefsHistory.edit().putString("history_list", historyList.toString()).apply()
                    // --- ---
                                        val currentFare = if (callInfo.fare_set > 0) callInfo.fare_set else callInfo.fare
                    val currentDeparture = if (callInfo.departure_set.isNotBlank()) callInfo.departure_set else callInfo.customerAddress
                    val currentDestination = if (callInfo.destination_set.isNotBlank()) callInfo.destination_set else callInfo.destination

                    val tripSummaryString = buildString {
                        append("고객: ${callInfo.customerName}, ")
                        append("출발: $currentDeparture, ")
                        append("도착: $currentDestination, ")
                        append("요금: ${currentFare}원, ")
                        append("결제: $paymentMethod")
                        if (paymentMethod == "현금+포인트" && cashAmountText.isNotBlank()) {
                            append(" (현금 ${cashAmountText}원)")
                        }
                    }

                    onConfirm(
                        callInfo.id,
                        paymentMethod,
                        if (paymentMethod == "현금+포인트") cashAmountText.toIntOrNull() else null,
                        currentFare,
                        tripSummaryString
                    )
                }
            }) { Text("확인") }
        }
    )
}
// --- --- 
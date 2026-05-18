package com.designated.callmanager

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.service.CallManagerService
import com.designated.callmanager.ui.dashboard.DashboardScreen
import com.designated.callmanager.ui.dashboard.DashboardViewModel
import com.designated.callmanager.ui.dashboard.NewCallAssignmentDialog
import com.designated.callmanager.ui.dashboard.ReservationConfirmDialog
import com.designated.callmanager.data.CallStatus
import com.designated.callmanager.data.DriverInfo
import com.designated.callmanager.data.DriverStatus
import com.designated.callmanager.ui.drivermanagement.DriverManagementScreen
import com.designated.callmanager.ui.customer.CustomerManagementScreen
import com.designated.callmanager.ui.attribution.AttributionManagementScreen
import com.designated.callmanager.ui.chat.ChatBottomSheetContent
import com.designated.callmanager.ui.chat.ChatViewModel
import com.designated.callmanager.ui.login.LoginScreen
import com.designated.callmanager.ui.login.LoginViewModel
import com.designated.callmanager.ui.pendingdrivers.PendingDriversScreen
import com.designated.callmanager.ui.settings.SettingsScreen
import com.designated.callmanager.ui.settlement.SettlementTabHost
import com.designated.callmanager.ui.excludenumber.ExcludeNumberScreen
import com.designated.callmanager.ui.signup.SignUpScreen
import com.designated.callmanager.ui.theme.CallManagerTheme
import com.designated.callmanager.util.CallManagerPermissionManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import com.google.firebase.firestore.FieldValue
import android.net.Uri
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

enum class Screen {
    Login,
    SignUp,
    PasswordReset,
    Dashboard,
    Settings,
    PendingDrivers,
    Settlement,
    ExcludeNumber,
    ContactSelection,
    CustomerManagement,
    AttributionManagement,
    Wallet,
    DepositGuide,
    WithdrawalRequest
}

sealed class NavigationParams {
    object None : NavigationParams()
    data class DriverManagement(val provinceId: String, val cityId: String, val officeId: String) : NavigationParams()
    data class CustomerManagement(val provinceId: String, val cityId: String, val officeId: String) : NavigationParams()
    data class AttributionManagement(val provinceId: String, val cityId: String, val officeId: String) : NavigationParams()
}

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val dashboardViewModel: DashboardViewModel by viewModels {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application)
    }

    // Activity scope ChatViewModel — Compose 안 viewModel() 호출과 동일 인스턴스
    // ACTION_SEND 수신 시 채팅 입력 prefill + sheet expand trigger 용
    private val chatViewModel: ChatViewModel by viewModels()

    private var provinceId: String? = null
    private var cityId: String? = null
    private var officeId: String? = null
    private var managerId: String? = null

    private var tokenRefreshListener: com.google.firebase.firestore.ListenerRegistration? = null

    private lateinit var permissionManager: CallManagerPermissionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.onPermissionResult(permissions)
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        permissionManager.onOverlayPermissionResult()
    }

    private val _screenState = mutableStateOf(Screen.Login)
    private val _settlementInitialTab = mutableStateOf(0)
    var screenState: Screen
        get() = _screenState.value
        set(value) { _screenState.value = value }

    private var navigationParams: NavigationParams by mutableStateOf(NavigationParams.None)

    private val _pendingCallDialogId = MutableStateFlow<String?>(null)
    private val pendingCallDialogId: StateFlow<String?> = _pendingCallDialogId.asStateFlow()

    private val callDetectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.designated.callmanager.NEW_CALL_DETECTED") {
                val callId = intent.getStringExtra("callId")
                val phoneNumber = intent.getStringExtra("phoneNumber")
                val contactName = intent.getStringExtra("contactName")

                if (callId != null) {
                }
            }
        }
    }

    private val internalCallDialogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.designated.callmanager.INTERNAL_SHOW_CALL_DIALOG") {
                val callId = intent.getStringExtra("EXTRA_CALL_ID")
                if (callId != null) {
                    lifecycleScope.launch {
                        if (_screenState.value == Screen.Dashboard) {
                            dashboardViewModel.showCallDialog(callId)
                        } else {
                            _screenState.value = Screen.Dashboard
                            delay(300)
                            dashboardViewModel.showCallDialog(callId)
                        }
                    }
                }
            }
        }
    }

    // 기사 상태 업데이트 브로드캐스트 리시버 (로그 용도, FCM 핸들러에서 Room DB 직접 업데이트)
    private val driverStatusUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.designated.callmanager.DRIVER_STATUS_UPDATE") {
                val driverName = intent.getStringExtra("driverName")
                val newStatus = intent.getStringExtra("newStatus")
                Log.d("MainActivity", "📍 기사 상태 업데이트 브로드캐스트 수신 - $driverName: $newStatus")
            }
        }
    }

    // 운행 시작/완료 팝업 브로드캐스트 리시버
    private val tripStatusPopupReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.designated.callmanager.TRIP_STATUS_POPUP") {
                val statusText = intent.getStringExtra("statusText") ?: return
                val driverName = intent.getStringExtra("driverName") ?: "기사"
                val driverPhone = intent.getStringExtra("driverPhone")
                val customerName = intent.getStringExtra("customerName") ?: "고객"
                val departure = intent.getStringExtra("departure") ?: "정보없음"
                val destination = intent.getStringExtra("destination") ?: "정보없음"
                val waypoints = intent.getStringExtra("waypoints") ?: ""
                val fare = intent.getLongExtra("fare", 0L)

                Log.d("MainActivity", "🚗 운행 상태 팝업 브로드캐스트 수신 - $statusText: $driverName")

                val tripSummary = buildString {
                    append("출발: $departure")
                    if (waypoints.isNotBlank()) {
                        append("\n경유: $waypoints")
                    }
                    append("\n도착: $destination")
                    append("\n요금: ${fare}원")
                }

                lifecycleScope.launch {
                    if (_screenState.value != Screen.Dashboard) {
                        _screenState.value = Screen.Dashboard
                        delay(300)
                    }
                    when (statusText) {
                        "운행 시작" -> dashboardViewModel.showTripStartedPopup(driverName, driverPhone, tripSummary, customerName)
                        "운행 완료" -> dashboardViewModel.showTripCompletedPopup(driverName, customerName)
                    }
                }
            }
        }
    }

    // 알림 전달 실패 브로드캐스트 리시버 (LocalBroadcast)
    private val notificationFailureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.designated.callmanager.NOTIFICATION_FAILURE") {
                val callId = intent.getStringExtra("callId") ?: return
                val driverName = intent.getStringExtra("driverName") ?: "기사"
                val driverId = intent.getStringExtra("driverId") ?: ""
                val presenceStatus = intent.getStringExtra("presenceStatus") ?: "unknown"
                val message = intent.getStringExtra("message") ?: "알림 전달 실패"

                Log.d("MainActivity", "⚠️ 알림 전달 실패 브로드캐스트 수신 - $driverName: $presenceStatus")

                lifecycleScope.launch {
                    if (_screenState.value != Screen.Dashboard) {
                        _screenState.value = Screen.Dashboard
                        delay(300)
                    }
                    dashboardViewModel.showNotificationFailurePopup(
                        callId = callId,
                        driverName = driverName,
                        driverId = driverId,
                        presenceStatus = presenceStatus,
                        message = message
                    )
                }
            }
        }
    }

    companion object {
        const val ACTION_SHOW_CALL_POPUP = "ACTION_SHOW_CALL_POPUP"
        const val ACTION_SHOW_SHARED_CALL = "ACTION_SHOW_SHARED_CALL"
        const val ACTION_SHOW_SHARED_CALL_CANCELLED = "ACTION_SHOW_SHARED_CALL_CANCELLED"
        const val ACTION_SHOW_SHARED_CALL_CANCELLED_NOTIFICATION = "ACTION_SHOW_SHARED_CALL_CANCELLED_NOTIFICATION"
        const val ACTION_SHOW_SHARED_CALL_CLAIMED = "ACTION_SHOW_SHARED_CALL_CLAIMED"
// const val ACTION_SHOW_NEW_CALL_WAITING = "ACTION_SHOW_NEW_CALL_WAITING" // 제거됨
        const val ACTION_SHOW_PENDING_DRIVERS = "ACTION_SHOW_PENDING_DRIVERS"
        const val ACTION_SHOW_DEVICE_CRASH = "ACTION_SHOW_DEVICE_CRASH"
        const val ACTION_SHOW_TRIP_STARTED_POPUP = "ACTION_SHOW_TRIP_STARTED_POPUP"
        const val ACTION_SHOW_TRIP_COMPLETED_POPUP = "ACTION_SHOW_TRIP_COMPLETED_POPUP"
        const val ACTION_SHOW_SETTLEMENT = "ACTION_SHOW_SETTLEMENT"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_SHARED_CALL_ID = "sharedCallId"
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        // 권한 매니저 초기화
        permissionManager = CallManagerPermissionManager(
            activity = this,
            onAllPermissionsGranted = {
                startCallManagerServiceIfNeeded()
            },
            onPermissionsDenied = { deniedPermissions ->
                Toast.makeText(this, "일부 권한이 거부되어 기능이 제한될 수 있습니다.", Toast.LENGTH_LONG).show()
            }
        )
        permissionManager.initialize(permissionLauncher, overlayPermissionLauncher)

        // FCM 서비스 초기화를 위한 토큰 요청
        initializeFirebaseMessaging()

        val internalFilter = IntentFilter("com.designated.callmanager.INTERNAL_SHOW_CALL_DIALOG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(internalCallDialogReceiver, internalFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(internalCallDialogReceiver, internalFilter)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
        }
        window.setBackgroundDrawableResource(android.R.color.transparent)

        screenState = if (auth.currentUser == null) {
            Screen.Login
        } else {
            Screen.Dashboard
        }

        if (auth.currentUser != null) {
            syncCallDetectorSettingsOnStartup()
        }

        handleIntent(intent)

        setContent {
            CallManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentScreenState by this@MainActivity._screenState

                    val callIdToShow by pendingCallDialogId.collectAsState()
                    val showNewCallPopup by dashboardViewModel.showNewCallPopup.collectAsState()

                    LaunchedEffect(currentScreenState, callIdToShow) {
                        if (currentScreenState == Screen.Dashboard && callIdToShow != null) {
                            val callId = callIdToShow!!
                            if (!showNewCallPopup) {
                                dashboardViewModel.showCallDialog(callId)
                            }
                            _pendingCallDialogId.value = null
                        }
                    }

                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            val user = firebaseAuth.currentUser
                            if (user == null) {
                                stopCallManagerService()
                                screenState = Screen.Login
                            }
                        }
                        auth.addAuthStateListener(listener)
                        onDispose {
                            auth.removeAuthStateListener(listener)
                        }
                    }

                    androidx.activity.compose.BackHandler {
                        when (currentScreenState) {
                            Screen.Dashboard, Screen.Login -> {
                                finish()
                            }
                            Screen.Settings -> {
                                screenState = Screen.Dashboard
                            }
                            Screen.Settlement, Screen.PendingDrivers, Screen.ExcludeNumber, Screen.CustomerManagement, Screen.AttributionManagement -> {
                                screenState = Screen.Settings
                            }
                            Screen.ContactSelection -> {
                                screenState = Screen.ExcludeNumber
                            }
                            Screen.SignUp, Screen.PasswordReset -> {
                                screenState = Screen.Login
                            }
                            Screen.Wallet -> {
                                screenState = Screen.Dashboard
                            }
                            Screen.DepositGuide, Screen.WithdrawalRequest -> {
                                screenState = Screen.Wallet
                            }
                        }
                    }

                    when (currentScreenState) {
                        Screen.Login -> LoginScreen(
                            onLoginComplete = { provinceId, cityId, officeId ->
                                this@MainActivity.provinceId = provinceId
                                this@MainActivity.cityId = cityId
                                this@MainActivity.officeId = officeId
                                this@MainActivity.managerId = auth.currentUser?.uid

                                dashboardViewModel.loadDataForUser(provinceId, cityId, officeId)
                                updateFcmTokenForAdmin(provinceId, cityId, officeId)
                                screenState = Screen.Dashboard
                            },
                            onNavigateToSignUp = { screenState = Screen.SignUp },
                            onNavigateToPasswordReset = { screenState = Screen.PasswordReset }
                        )
                        Screen.SignUp -> SignUpScreen(
                            onSignUpSuccess = { screenState = Screen.Login },
                            onNavigateBack = { screenState = Screen.Login }
                        )
                        Screen.PasswordReset -> { /* TODO: Implement Password Reset Screen */ }
                        Screen.Dashboard -> {
                            LaunchedEffect(Unit) {
                                // 필요한 권한이 없을 때만 요청
                                if (!permissionManager.areAllRequiredPermissionsGranted()) {
                                    permissionManager.requestAllPermissions()
                                }
                            }

                            var showLogoutConfirmDialog by remember { mutableStateOf(false) }

                            if (showLogoutConfirmDialog) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { showLogoutConfirmDialog = false },
                                    title = { Text("종료 확인") },
                                    text = { Text("정말 종료하시겠습니까?") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showLogoutConfirmDialog = false
                                            // auto_login 설정은 유지 (체크박스 해제 시에만 변경됨)
                                            // 종료 전에 managerTokens/{uid} 삭제 후 signOut + finishAffinity
                                            logoutAndExit()
                                        }) {
                                            Text("종료")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showLogoutConfirmDialog = false }) {
                                            Text("취소")
                                        }
                                    }
                                )
                            }

                            DashboardWithChatSheet(
                                dashboardViewModel = dashboardViewModel,
                                chatViewModel = chatViewModel,
                                onLogout = { showLogoutConfirmDialog = true },
                                onNavigateToSettings = { screenState = Screen.Settings },
                                onNavigateToWallet = { screenState = Screen.Wallet },
                                onNavigateToSettlement = { screenState = Screen.Settlement }
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                dashboardViewModel = dashboardViewModel,
                                onNavigateBack = { screenState = Screen.Dashboard },
                                onNavigateToPendingDrivers = { provinceId, cityId, officeId ->
                                    navigationParams = NavigationParams.DriverManagement(provinceId, cityId, officeId)
                                    screenState = Screen.PendingDrivers
                                },
                                onNavigateToSettlement = {
                                    screenState = Screen.Settlement
                                },
                                onNavigateToExcludeNumber = {
                                    screenState = Screen.ExcludeNumber
                                },
                                onNavigateToCustomerManagement = { provinceId, cityId, officeId ->
                                    navigationParams = NavigationParams.CustomerManagement(provinceId, cityId, officeId)
                                    screenState = Screen.CustomerManagement
                                },
                                onNavigateToAttributionManagement = { provinceId, cityId, officeId ->
                                    navigationParams = NavigationParams.AttributionManagement(provinceId, cityId, officeId)
                                    screenState = Screen.AttributionManagement
                                }
                            )
                        }
                        Screen.PendingDrivers -> {
                            val params = navigationParams
                            if (params is NavigationParams.DriverManagement) {
                                PendingDriversScreen(
                                    provinceId = params.provinceId,
                                    cityId = params.cityId,
                                    officeId = params.officeId,
                                    onNavigateBack = { screenState = Screen.Dashboard }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    Toast.makeText(this@MainActivity, "잘못된 접근입니다. 이전 화면으로 돌아갑니다.", Toast.LENGTH_SHORT).show()
                                    screenState = Screen.Settings
                                }
                            }
                        }
                        Screen.Settlement -> {
                            val initTab = _settlementInitialTab.value
                            SettlementTabHost(
                                onBack = { screenState = Screen.Settings },
                                onHome = { screenState = Screen.Dashboard },
                                initialTab = initTab
                            )
                            LaunchedEffect(Unit) { _settlementInitialTab.value = 0 }
                        }
                        Screen.ExcludeNumber -> {
                            ExcludeNumberScreen(
                                onNavigateBack = { screenState = Screen.Settings },
                                onNavigateToContactSelection = {
                                    screenState = Screen.ContactSelection
                                }
                            )
                        }
                        Screen.ContactSelection -> {
                            com.designated.callmanager.ui.excludenumber.ContactSelectionScreen(
                                onNavigateBack = { screenState = Screen.ExcludeNumber }
                            )
                        }
                        Screen.CustomerManagement -> {
                            val params = navigationParams
                            if (params is NavigationParams.CustomerManagement) {
                                CustomerManagementScreen(
                                    provinceId = params.provinceId,
                                    cityId = params.cityId,
                                    officeId = params.officeId,
                                    onNavigateBack = { screenState = Screen.Settings },
                                    onCustomerClick = { customer ->
                                        // TODO: 고객 상세 화면으로 이동
                                        Toast.makeText(
                                            this@MainActivity,
                                            "${customer.name} (${customer.phoneNumber})",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    Toast.makeText(this@MainActivity, "잘못된 접근입니다. 이전 화면으로 돌아갑니다.", Toast.LENGTH_SHORT).show()
                                    screenState = Screen.Settings
                                }
                            }
                        }
                        Screen.AttributionManagement -> {
                            val params = navigationParams
                            if (params is NavigationParams.AttributionManagement) {
                                AttributionManagementScreen(
                                    provinceId = params.provinceId,
                                    cityId = params.cityId,
                                    officeId = params.officeId,
                                    onNavigateBack = { screenState = Screen.Settings }
                                )
                            } else {
                                LaunchedEffect(Unit) {
                                    Toast.makeText(this@MainActivity, "잘못된 접근입니다. 이전 화면으로 돌아갑니다.", Toast.LENGTH_SHORT).show()
                                    screenState = Screen.Settings
                                }
                            }
                        }
                        Screen.Wallet -> {
                            val walletViewModel: com.designated.callmanager.ui.wallet.WalletViewModel = viewModel()
                            com.designated.callmanager.ui.wallet.WalletScreen(
                                viewModel = walletViewModel,
                                onNavigateBack = { screenState = Screen.Dashboard },
                                onNavigateToDepositGuide = { screenState = Screen.DepositGuide },
                                onNavigateToWithdrawalRequest = { screenState = Screen.WithdrawalRequest },
                            )
                        }
                        Screen.DepositGuide -> {
                            val walletViewModel: com.designated.callmanager.ui.wallet.WalletViewModel = viewModel()
                            com.designated.callmanager.ui.wallet.DepositGuideScreen(
                                viewModel = walletViewModel,
                                onNavigateBack = { screenState = Screen.Wallet },
                            )
                        }
                        Screen.WithdrawalRequest -> {
                            val walletViewModel: com.designated.callmanager.ui.wallet.WalletViewModel = viewModel()
                            com.designated.callmanager.ui.wallet.WithdrawalRequestScreen(
                                viewModel = walletViewModel,
                                onNavigateBack = { screenState = Screen.Wallet },
                            )
                        }
                    }

                    // 배차 팝업 — 어느 화면에서든 표시
                    val newCallInfo by dashboardViewModel.newCallInfo.collectAsState()
                    val drivers by dashboardViewModel.drivers.collectAsState()
                    val callsForReservation by dashboardViewModel.calls.collectAsState()

                    // 운행중 기사 클릭 시 ReservationConfirmDialog 표시용 state (NewCallAssignmentDialog 위에 모달)
                    var pendingReservationDriver by remember { mutableStateOf<com.designated.callmanager.data.DriverInfo?>(null) }

                    if (showNewCallPopup && newCallInfo != null) {
                        // NewCallAssignmentDialog 안에서 status 분기 (WAITING/ONLINE = 대기중 / ON_TRIP = 예약 배차)
                        val allDriversForDialog = drivers.sortedBy { it.lastLoginTime?.seconds ?: Long.MAX_VALUE }

                        NewCallAssignmentDialog(
                            callInfo = newCallInfo!!,
                            availableDrivers = allDriversForDialog,
                            onDismiss = { dashboardViewModel.dismissNewCallPopup() },
                            onDriverSelect = { driver ->
                                dashboardViewModel.assignNewCall(driver.id)
                            },
                            onDriverSelectWithInfo = { driver, departure, destination, fare, memoText ->
                                dashboardViewModel.assignNewCallWithInfo(driver.id, departure, destination, fare, memoText)
                            },
                            onDelete = {
                                dashboardViewModel.deleteCall(newCallInfo!!.id)
                            },
                            onShare = { departure, destination, fare ->
                                dashboardViewModel.shareCall(newCallInfo!!, departure, destination, fare)
                            },
                            onDirectRun = {
                                newCallInfo?.let { dashboardViewModel.requestDirectRun(it) }
                                dashboardViewModel.dismissNewCallPopup()
                            },
                            onMemoUpdate = { memoText, parsed ->
                                newCallInfo?.let { call ->
                                    dashboardViewModel.updateCallMemo(
                                        callId = call.id,
                                        memoText = memoText,
                                        departure = parsed.departure,
                                        waypoints = parsed.waypoints,
                                        destination = parsed.destination,
                                        fare = parsed.fare
                                    )
                                }
                            },
                            onReservationDriverSelect = { driver ->
                                pendingReservationDriver = driver
                            }
                        )
                    }

                    // 예약 배차 확인 다이얼로그 — NewCallAssignmentDialog 위에 모달로 표시
                    val reservationDriver = pendingReservationDriver
                    if (reservationDriver != null) {
                        val callToReserve = newCallInfo
                        val currentTripCall = callsForReservation.firstOrNull { c ->
                            c.assignedDriverId == reservationDriver.authUid &&
                                (c.status == CallStatus.ACCEPTED.firestoreValue ||
                                    c.status == CallStatus.IN_PROGRESS.firestoreValue ||
                                    c.status == CallStatus.AWAITING_SETTLEMENT.firestoreValue)
                        }
                        if (callToReserve != null) {
                            ReservationConfirmDialog(
                                driver = reservationDriver,
                                currentTripCall = currentTripCall,
                                callToReserve = callToReserve,
                                onDismiss = { pendingReservationDriver = null },
                                onConfirm = {
                                    dashboardViewModel.assignReservation(callToReserve, reservationDriver.id)
                                    pendingReservationDriver = null
                                    dashboardViewModel.dismissNewCallPopup()
                                }
                            )
                        }
                    }

                    // 관리자 직접운행 정산 다이얼로그 (전역 렌더)
                    val directRunCall by dashboardViewModel.directRunCall.collectAsState()
                    directRunCall?.let { call ->
                        com.designated.callmanager.ui.dashboard.DirectRunDialog(
                            callInfo = call,
                            onDismiss = { dashboardViewModel.dismissDirectRun() },
                            onConfirm = { fare, paymentMethod, cash, credit, points, cname, phone, dep, dest ->
                                dashboardViewModel.completeAsManager(
                                    callId = call.id,
                                    fare = fare,
                                    paymentMethod = paymentMethod,
                                    cashReceived = cash,
                                    creditAmount = credit,
                                    pointsUsed = points,
                                    customerName = cname,
                                    phoneNumber = phone,
                                    departure = dep,
                                    destination = dest
                                )
                                dashboardViewModel.dismissDirectRun()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        // FCM 알림 클릭 처리
        Log.d("MainActivity", "[handleIntent] action=${intent?.action}, extras=${intent?.extras?.keySet()?.toList()}")

        // 기사 승인 알림 클릭 처리 (action이 아닌 extras의 type으로 판별)
        if (intent?.extras?.getString("type") == "DRIVER_APPROVAL_REQUEST") {
            lifecycleScope.launch {
                val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                val p = provinceId ?: prefs.getString("provinceId", null)
                val c = cityId ?: prefs.getString("cityId", null)
                val o = officeId ?: prefs.getString("officeId", null)
                if (p != null && c != null && o != null) {
                    Log.d("MainActivity", "[handleIntent] 대기기사 페이지로 이동")
                    navigationParams = NavigationParams.DriverManagement(p, c, o)
                    _screenState.value = Screen.PendingDrivers
                } else {
                    Log.w("MainActivity", "사무실 정보 없음 - 대기기사 화면 이동 불가")
                }
            }
            return
        }

        if (intent?.action == Intent.ACTION_MAIN || intent?.action == null) {
            val sharedCallId = intent?.extras?.getString("sharedCallId")
            if (!sharedCallId.isNullOrBlank()) {
                lifecycleScope.launch {
                    if (_screenState.value != Screen.Dashboard) {
                        _screenState.value = Screen.Dashboard
                        delay(300)
                    }
                    dashboardViewModel.showSharedCallNotificationFromId(sharedCallId)
                }
                return
            }
        }

        when (intent?.action) {
            // 카톡/문자 등에서 텍스트 공유 받기 → 사무실 단톡방 입력바 prefill + sheet expand
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }?.let { text ->
                        Log.d("MainActivity", "[handleIntent] ACTION_SEND text/plain 수신, length=${text.length}")
                        chatViewModel.setInputText(text)
                        chatViewModel.requestExpandSheet()
                    }
                }
            }
            ACTION_SHOW_CALL_POPUP -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                if (callId != null) {
                    val popupId = "CALL_${callId}"
                    if (!isPopupAlreadyShown(popupId)) {
                        lifecycleScope.launch {
                            markPopupAsShown(popupId)
                            dashboardViewModel.showCallDialog(callId)
                        }
                    }
                } else {
                }
            }
            ACTION_SHOW_SHARED_CALL -> {
                val sharedCallId = intent.getStringExtra(EXTRA_SHARED_CALL_ID)
                    ?: intent.getStringExtra("sharedCallId")
                    ?: intent.extras?.getString(EXTRA_SHARED_CALL_ID)
                    ?: intent.extras?.getString("sharedCallId")

                if (sharedCallId != null) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val notificationId = "shared_call_$sharedCallId".hashCode()

                    notificationManager.cancel(notificationId)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val activeNotifications = notificationManager.activeNotifications
                        activeNotifications.forEach { notification ->
                            if (notification.notification.extras?.getString("sharedCallId") == sharedCallId ||
                                notification.notification.tickerText?.contains("공유콜") == true) {
                                notificationManager.cancel(notification.id)
                            }
                        }
                    }

                    Log.d("MainActivity", "🔍 [FCM_DEBUG] sharedCallId 있음 - 배차팝업 표시 시작")
                    lifecycleScope.launch {
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                            Log.d("MainActivity", "🔍 [FCM_DEBUG] Dashboard로 화면 전환 - 800ms 대기")
                            kotlinx.coroutines.delay(800) // 더 긴 대기 시간
                        } else {
                            Log.d("MainActivity", "🔍 [FCM_DEBUG] 이미 Dashboard 화면 - 200ms 대기")
                            kotlinx.coroutines.delay(200) // 짧은 대기
                        }
                        dashboardViewModel.showSharedCallNotificationFromId(sharedCallId)
                    }
                }
            }
            ACTION_SHOW_SHARED_CALL_CANCELLED -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                if (callId != null) {
                    lifecycleScope.launch {
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        dashboardViewModel.showSharedCallCancelledDialog(callId)
                    }
                } else {
                }
            }

            ACTION_SHOW_PENDING_DRIVERS -> {
                lifecycleScope.launch {
                    val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                    val p = provinceId ?: prefs.getString("provinceId", null)
                    val c = cityId ?: prefs.getString("cityId", null)
                    val o = officeId ?: prefs.getString("officeId", null)
                    if (p != null && c != null && o != null) {
                        navigationParams = NavigationParams.DriverManagement(p, c, o)
                        _screenState.value = Screen.PendingDrivers
                    } else {
                        Log.w("MainActivity", "사무실 정보 없음 - 대기기사 화면 이동 불가")
                    }
                }
            }
            ACTION_SHOW_SETTLEMENT -> {
                lifecycleScope.launch {
                    _settlementInitialTab.value = 2  // 기사별 탭
                    _screenState.value = Screen.Settlement
                }
            }
            ACTION_SHOW_TRIP_STARTED_POPUP -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val driverName = intent.getStringExtra("driverName") ?: "기사"
                val driverPhone = intent.getStringExtra("driverPhone") ?: ""
                val customerName = intent.getStringExtra("customerName") ?: "고객"
                val tripSummary = intent.getStringExtra("tripSummary") ?: ""

                val popupId = "TRIP_STARTED_${callId}"
                if (!isPopupAlreadyShown(popupId)) {
                    lifecycleScope.launch {
                        markPopupAsShown(popupId)
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        dashboardViewModel.showTripStartedPopup(driverName, driverPhone, tripSummary, customerName)
                    }
                }
            }

            ACTION_SHOW_TRIP_COMPLETED_POPUP -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val driverName = intent.getStringExtra("driverName") ?: "기사"
                val customerName = intent.getStringExtra("customerName") ?: "고객"

                val popupId = "TRIP_COMPLETED_${callId}"
                if (!isPopupAlreadyShown(popupId)) {
                    lifecycleScope.launch {
                        markPopupAsShown(popupId)
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        dashboardViewModel.showTripCompletedPopup(driverName, customerName)
                    }
                }
            }

            ACTION_SHOW_SHARED_CALL_CANCELLED_NOTIFICATION -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val cancelReason = intent.getStringExtra("cancelReason")
                if (callId != null) {
                    lifecycleScope.launch {
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        showToast("공유콜이 취소되었습니다: ${cancelReason ?: "사유 없음"}")
                    }
                }
            }

            ACTION_SHOW_SHARED_CALL_CLAIMED -> {
                val sharedCallId = intent.getStringExtra(EXTRA_SHARED_CALL_ID)
                if (sharedCallId != null) {
                    lifecycleScope.launch {
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        showToast("공유콜이 다른 사무실에서 수락되었습니다")
                    }
                }
            }

// ACTION_SHOW_NEW_CALL_WAITING 처리 제거됨
            // 이유: Call Detector에서 직접 팝업 생성하므로 불필요

            ACTION_SHOW_DEVICE_CRASH -> {
                val deviceId = intent.getStringExtra("deviceId")
                val timestamp = intent.getLongExtra("timestamp", 0L)
                if (deviceId != null) {
                    showDeviceCrashDialog(deviceId, timestamp)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val filter = IntentFilter("com.designated.callmanager.NEW_CALL_DETECTED")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callDetectedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callDetectedReceiver, filter)
        }

        // 기사 상태 업데이트 리시버 등록
        val driverStatusFilter = IntentFilter("com.designated.callmanager.DRIVER_STATUS_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(driverStatusUpdateReceiver, driverStatusFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(driverStatusUpdateReceiver, driverStatusFilter)
        }

        // 운행 시작/완료 팝업 리시버 등록
        val tripStatusFilter = IntentFilter("com.designated.callmanager.TRIP_STATUS_POPUP")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(tripStatusPopupReceiver, tripStatusFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(tripStatusPopupReceiver, tripStatusFilter)
        }

        // 알림 전달 실패 리시버 등록 (LocalBroadcast)
        val notificationFailureFilter = IntentFilter("com.designated.callmanager.NOTIFICATION_FAILURE")
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationFailureReceiver, notificationFailureFilter)

        checkAndShowPendingPopup()
        setupTokenRefreshListener()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(callDetectedReceiver)
        } catch (e: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(driverStatusUpdateReceiver)
        } catch (e: IllegalArgumentException) {
        }
        try {
            unregisterReceiver(tripStatusPopupReceiver)
        } catch (e: IllegalArgumentException) {
        }
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationFailureReceiver)
        } catch (e: IllegalArgumentException) {
        }
        tokenRefreshListener?.remove()
        tokenRefreshListener = null
    }

    private fun isPopupAlreadyShown(popupId: String): Boolean {
        val prefs = getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
        return prefs.getBoolean(popupId, false)
    }

    private fun markPopupAsShown(popupId: String) {
        val prefs = getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(popupId, true).apply()

        // 주기적으로 오래된 팝업 기록 정리 (100개 이상 시)
        if (prefs.all.size > 100) {
            clearOldPopupRecords()
        }
    }

    private fun clearOldPopupRecords() {
        val prefs = getSharedPreferences("shown_popups", Context.MODE_PRIVATE)
        val editor = prefs.edit()

        // 24시간 이상 된 CALL_, NEW_CALL_ 팝업만 정리 (운행 관련은 유지)
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val keysToRemove = prefs.all.keys.filter { key ->
            key.startsWith("CALL_") || key.startsWith("NEW_CALL_")
        }.take(50) // 최대 50개만 삭제

        keysToRemove.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
    }

    private fun checkAndShowPendingPopup() {
        val prefs = getSharedPreferences("pending_popups", Context.MODE_PRIVATE)
        val popupType = prefs.getString("popup_type", null)

        if (popupType != null) {
            val callId = prefs.getString("popup_call_id", "") ?: ""
            val driverName = prefs.getString("popup_driver_name", "기사") ?: "기사"
            val driverPhone = prefs.getString("popup_driver_phone", "") ?: ""
            val tripSummary = prefs.getString("popup_trip_summary", "") ?: ""
            val customerName = prefs.getString("popup_customer_name", "고객") ?: "고객"
            val timestamp = prefs.getLong("popup_timestamp", 0)

            val popupId = "${popupType}_${callId}"

            if (isPopupAlreadyShown(popupId)) {
                prefs.edit().clear().apply()
                return
            }

            val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000)

            if (timestamp > tenMinutesAgo) {
                lifecycleScope.launch {
                    if (_screenState.value != Screen.Dashboard) {
                        _screenState.value = Screen.Dashboard
                    }

                    markPopupAsShown(popupId)

                    when (popupType) {
                        "TRIP_STARTED" -> {
                            dashboardViewModel.showTripStartedPopup(driverName, driverPhone, tripSummary, customerName)
                        }
                        "TRIP_COMPLETED" -> {
                            dashboardViewModel.showTripCompletedPopup(driverName, customerName)
                        }
                    }
                }
            }

            prefs.edit().clear().apply()
        }
    }

    /**
     * 크래시 상세 정보 팝업 표시
     */
    private fun showDeviceCrashDialog(deviceId: String, timestamp: Long) {
        val formattedTime = if (timestamp > 0) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA)
                .format(java.util.Date(timestamp))
        } else {
            "시간 정보 없음"
        }

        AlertDialog.Builder(this)
            .setTitle("🚨 콜디텍터 강제종료")
            .setMessage("""
                디바이스: $deviceId
                발생 시간: $formattedTime

                콜디텍터 앱이 강제로 종료되었습니다.
                해당 전화기를 점검해 주세요.

                • 앱 재시작 필요
                • 배터리 최적화 설정 확인
                • 디바이스 상태 점검
            """.trimIndent())
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("대시보드 이동") { dialog, _ ->
                if (_screenState.value != Screen.Dashboard) {
                    _screenState.value = Screen.Dashboard
                }
                dialog.dismiss()
            }
            .setCancelable(true)
            .show()
    }

    private fun stopCallManagerService() {
        val serviceIntent = Intent(this, CallManagerService::class.java)
        stopService(serviceIntent)
    }


    /**
     * Firebase Messaging 서비스 초기화 및 자동 토큰 복구
     * FCM 토큰을 요청하고, 무효한 토큰인 경우 자동으로 갱신
     */
    private fun initializeFirebaseMessaging() {
        android.util.Log.d("MainActivity", "========== initializeFirebaseMessaging 시작 ==========")

        // Firebase 초기화 상태 확인
        try {
            val firebaseApp = com.google.firebase.FirebaseApp.getInstance()
            android.util.Log.d("MainActivity", "Firebase 앱 이름: ${firebaseApp.name}")
            android.util.Log.d("MainActivity", "Firebase 프로젝트 ID: ${firebaseApp.options.projectId}")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Firebase 앱 정보 가져오기 실패: ${e.message}")
        }

        // FCM 인스턴스 상태 확인
        val messaging = FirebaseMessaging.getInstance()
        android.util.Log.d("MainActivity", "FirebaseMessaging 인스턴스 생성 완료")

        // 자동 토큰 복구 로직 시작
        validateAndRefreshTokenIfNeeded(messaging)

        android.util.Log.d("MainActivity", "========== initializeFirebaseMessaging 요청 완료 ==========")
    }

    /**
     * 토큰 유효성 검증 및 자동 갱신
     * 무효한 토큰인 경우 자동으로 삭제 후 새 토큰 생성
     */
    private fun validateAndRefreshTokenIfNeeded(messaging: FirebaseMessaging) {
        android.util.Log.d("MainActivity", "🔍 토큰 유효성 검증 시작...")

        // 1단계: 기존 토큰 확인
        messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val currentToken = task.result
                android.util.Log.d("MainActivity", "✅ 현재 토큰 획득: ${currentToken.take(20)}...")
                android.util.Log.d("MainActivity", "토큰 길이: ${currentToken.length}")

                // 2단계: 토큰 갱신 강제 실행 (무효 토큰 방지)
                android.util.Log.d("MainActivity", "🔄 토큰 갱신 시작...")
                refreshToken(messaging)

            } else {
                android.util.Log.e("MainActivity", "❌ 기존 토큰 획득 실패: ${task.exception?.message}")
                // 토큰 획득 실패 시에도 갱신 시도
                refreshToken(messaging)
            }
        }
    }

    /**
     * FCM 토큰 강제 갱신
     */
    private fun refreshToken(messaging: FirebaseMessaging) {
        android.util.Log.d("MainActivity", "🗑️ 기존 토큰 삭제 중...")

        messaging.deleteToken().addOnCompleteListener { deleteTask ->
            android.util.Log.d("MainActivity", "토큰 삭제 완료 - 성공: ${deleteTask.isSuccessful}")

            if (!deleteTask.isSuccessful) {
                android.util.Log.e("MainActivity", "토큰 삭제 실패: ${deleteTask.exception?.message}")
            }

            // 삭제 성공/실패 관계없이 새 토큰 생성 시도
            android.util.Log.d("MainActivity", "🆕 새 토큰 생성 중...")

            messaging.token.addOnCompleteListener { newTokenTask ->
                android.util.Log.d("MainActivity", "새 토큰 생성 완료 - 성공: ${newTokenTask.isSuccessful}")

                if (newTokenTask.isSuccessful) {
                    val newToken = newTokenTask.result
                    android.util.Log.d("MainActivity", "✅ 새 토큰 생성 성공: ${newToken.take(20)}...")
                    android.util.Log.d("MainActivity", "새 토큰 길이: ${newToken.length}")
                    android.util.Log.d("MainActivity", "🔔 새 토큰으로 MyFirebaseMessagingService 활성화됨")

                    // 새 토큰으로 Firestore 업데이트는 MyFirebaseMessagingService의 onNewToken에서 자동 처리됨
                } else {
                    android.util.Log.e("MainActivity", "❌ 새 토큰 생성 실패: ${newTokenTask.exception?.message}")
                    newTokenTask.exception?.printStackTrace()
                }
            }
        }
    }


    private fun startCallManagerServiceIfNeeded() {
        if (CallManagerService.isServiceRunning) return
        try {
            val serviceIntent = Intent(this, CallManagerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Service start failed", e)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateFcmTokenForAdmin(provinceId: String, cityId: String, officeId: String) {
        val adminId = auth.currentUser?.uid ?: return

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val firestore = FirebaseFirestore.getInstance()

                val tokenData = hashMapOf("fcmToken" to token)

                firestore.collection("admins").document(adminId)
                    .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnFailureListener { e ->
                    }
            } else {
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(internalCallDialogReceiver)
        } catch (e: Exception) {
        }
        val serviceIntent = Intent(this, CallManagerService::class.java)
        stopService(serviceIntent)
    }


    /**
     * 앱 시작 시 기존 로그인 사용자의 콜 디텍터 설정 자동 동기화
     */
    private fun syncCallDetectorSettingsOnStartup() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            return
        }

        lifecycleScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val adminId = currentUser.uid

                firestore.collection("admins").document(adminId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val provinceId = document.getString("associatedProvinceId")
                            val cityId = document.getString("associatedCityId")
                            val officeId = document.getString("associatedOfficeId")

                            if (provinceId != null && cityId != null && officeId != null) {

                                this@MainActivity.provinceId = provinceId
                                this@MainActivity.cityId = cityId
                                this@MainActivity.officeId = officeId
                                this@MainActivity.managerId = adminId

                                // DashboardViewModel이 이미 init에서 리스너를 시작하므로 loadDataForUser 호출 불필요
                                // 콜 디텍터 설정만 동기화
                                dashboardViewModel.syncCallDetectorSettings(provinceId, cityId, officeId)

                            } else {
                            }
                        } else {
                        }
                    }
                    .addOnFailureListener { e ->
                    }
            } catch (e: Exception) {
            }
        }
    }

    /**
     * 로그아웃 = 업무 종료 → 모든 FCM 알림 즉시 차단.
     * 순서: ① Firestore field delete (admins.fcmToken + managerTokens 문서) → ② signOut →
     *       ③ deleteToken (device-level revoke) → ④ tokenRefreshListener.remove + fcm_prefs.clear → ⑤ finishAffinity.
     * 모든 await에 5초 timeout (네트워크 불량 시 logout freeze 방지).
     */
    private fun logoutAndExit() {
        val uid = auth.currentUser?.uid
        val prefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val p = provinceId ?: prefs.getString("provinceId", null)
        val c = cityId ?: prefs.getString("cityId", null)
        val o = officeId ?: prefs.getString("officeId", null)

        lifecycleScope.launch {
            // ① Firestore field delete (인증 살아있을 때)
            if (uid != null) {
                // (1-a) admins/{uid}.fcmToken 삭제 — 콜 알림(admins 토큰 사용 경로) 차단
                try {
                    withTimeoutOrNull(5_000) {
                        FirebaseFirestore.getInstance()
                            .collection("admins").document(uid)
                            .update("fcmToken", FieldValue.delete())
                            .await()
                    }
                    Log.d("MainActivity", "[logoutAndExit] admins.fcmToken 삭제 처리")
                } catch (e: Exception) {
                    Log.w("MainActivity", "[logoutAndExit] admins.fcmToken 삭제 실패: ${e.message}")
                }

                // (1-b) managerTokens/{uid} 문서 삭제 — chat + 콜 알림(managerTokens 경로) 차단
                if (!p.isNullOrBlank() && !c.isNullOrBlank() && !o.isNullOrBlank()) {
                    try {
                        withTimeoutOrNull(5_000) {
                            FirebaseFirestore.getInstance()
                                .collection("provinces").document(p)
                                .collection("cities").document(c)
                                .collection("offices").document(o)
                                .collection("managerTokens").document(uid)
                                .delete()
                                .await()
                        }
                        Log.d("MainActivity", "[logoutAndExit] managerTokens/$uid 삭제 처리")
                    } catch (e: Exception) {
                        Log.w("MainActivity", "[logoutAndExit] managerTokens 삭제 실패: ${e.message}")
                    }
                } else {
                    Log.w("MainActivity", "[logoutAndExit] 사무실 정보 부족 - managerTokens 스킵")
                }
            }

            // ② signOut (currentUser → null, 이후 onNewToken 가드 작동)
            auth.signOut()

            // ③ deleteToken (device push 채널 revoke)
            try {
                withTimeoutOrNull(5_000) {
                    FirebaseMessaging.getInstance().deleteToken().await()
                }
                Log.d("MainActivity", "[logoutAndExit] deleteToken 처리")
            } catch (e: Exception) {
                Log.w("MainActivity", "[logoutAndExit] deleteToken 실패: ${e.message}")
            }

            // ④ 부수적 cleanup
            tokenRefreshListener?.remove()
            tokenRefreshListener = null
            getSharedPreferences("fcm_prefs", Context.MODE_PRIVATE)
                .edit().remove("fcm_token").apply()

            // ⑤ finishAffinity
            finishAffinity()
        }
    }

    /**
     * 토큰 갱신 요청 실시간 리스너 설정
     */
    private fun setupTokenRefreshListener() {
        val currentProvinceId = provinceId
        val currentCityId = cityId
        val currentOfficeId = officeId
        val currentManagerId = managerId

        if (currentProvinceId.isNullOrBlank() || currentCityId.isNullOrBlank() || currentOfficeId.isNullOrBlank() || currentManagerId.isNullOrBlank()) {
            android.util.Log.w("MainActivity", "[TokenRefresh] provinceId, cityId, officeId 또는 managerId가 없어 리스너 설정 불가")
            return
        }

        // 기존 리스너 제거
        tokenRefreshListener?.remove()

        android.util.Log.d("MainActivity", "[TokenRefresh] 토큰 갱신 요청 리스너 시작 - managerId: $currentManagerId")

        tokenRefreshListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("provinces").document(currentProvinceId)
            .collection("cities").document(currentCityId)
            .collection("offices").document(currentOfficeId)
            .collection("tokenRefreshRequests")
            .document(currentManagerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("MainActivity", "[TokenRefresh] 리스너 오류: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val processed = snapshot.getBoolean("processed") ?: false

                    if (!processed) {
                        android.util.Log.i("MainActivity", "[TokenRefresh] 갱신 요청 감지 - 새 토큰 등록 시작")

                        // 새 FCM 토큰 가져오기 및 등록
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                            .addOnCompleteListener { task ->
                                if (!task.isSuccessful) {
                                    android.util.Log.e("MainActivity", "[TokenRefresh] 토큰 가져오기 실패: ${task.exception?.message}")
                                    return@addOnCompleteListener
                                }

                                val newToken = task.result
                                android.util.Log.d("MainActivity", "[TokenRefresh] 새 토큰 획득: ${newToken.substring(0, 50)}...")

                                // Firestore에 새 토큰 저장
                                val managerTokenData = hashMapOf(
                                    "fcmToken" to newToken,
                                    "updatedAt" to com.google.firebase.Timestamp.now()
                                )

                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    .collection("provinces").document(currentProvinceId)
                                    .collection("cities").document(currentCityId)
                                    .collection("offices").document(currentOfficeId)
                                    .collection("managerTokens").document(currentManagerId)
                                    .set(managerTokenData, com.google.firebase.firestore.SetOptions.merge())
                                    .addOnSuccessListener {
                                        android.util.Log.i("MainActivity", "[TokenRefresh] ✅ 새 토큰 등록 성공")

                                        // 갱신 요청 문서를 processed로 표시
                                        snapshot.reference.update("processed", true)
                                            .addOnSuccessListener {
                                                android.util.Log.d("MainActivity", "[TokenRefresh] 갱신 요청 처리 완료 표시")
                                            }
                                            .addOnFailureListener { e ->
                                                android.util.Log.e("MainActivity", "[TokenRefresh] 처리 완료 표시 실패: ${e.message}")
                                            }
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("MainActivity", "[TokenRefresh] ❌ 새 토큰 등록 실패: ${e.message}")
                                    }
                            }
                    }
                }
            }
    }

}

/**
 * Dashboard 화면 위에 사무실 단톡방 BottomSheet를 동거시키는 래퍼.
 *
 * V1: peek 56dp + Expanded (≈95%) 2-state. Material 3 BottomSheetScaffold 기본 동작.
 * V1.5: 50% 중간 anchor 추가 검토 (AnchoredDraggable 커스텀 필요).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardWithChatSheet(
    dashboardViewModel: DashboardViewModel,
    chatViewModel: ChatViewModel,
    onLogout: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToWallet: () -> Unit,
    onNavigateToSettlement: () -> Unit,
) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetTargetValue = scaffoldState.bottomSheetState.targetValue
    val isExpanded = sheetTargetValue == SheetValue.Expanded
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(sheetTargetValue) {
        if (sheetTargetValue == SheetValue.PartiallyExpanded || sheetTargetValue == SheetValue.Hidden) {
            keyboardController?.hide()
        }
    }
    // 외부(ACTION_SEND) 에서 sheet 펼침 요청 감지 — 카톡 등 공유 수신 시 자동 expand
    val shouldExpand by chatViewModel.shouldExpandSheet.collectAsState()
    LaunchedEffect(shouldExpand) {
        if (shouldExpand) {
            scaffoldState.bottomSheetState.expand()
            chatViewModel.consumeExpandRequest()
        }
    }
    androidx.activity.compose.BackHandler(enabled = isExpanded) {
        coroutineScope.launch {
            scaffoldState.bottomSheetState.partialExpand()
        }
    }
    val density = LocalDensity.current
    val navInsetDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            ChatBottomSheetContent(viewModel = chatViewModel, isExpanded = isExpanded)
        },
        sheetPeekHeight = 76.dp + navInsetDp,
        sheetDragHandle = null,
        sheetContainerColor = androidx.compose.ui.graphics.Color.Transparent,
        sheetShadowElevation = 0.dp,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            DashboardScreen(
                viewModel = dashboardViewModel,
                onLogout = onLogout,
                onNavigateToSettings = onNavigateToSettings,
                onNavigateToWallet = onNavigateToWallet,
                onNavigateToSettlement = onNavigateToSettlement,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreenPlaceholder(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("회원가입") }) }
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(it).padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("회원가입 화면입니다.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) {
                    Text("뒤로가기")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordResetScreenPlaceholder(onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("비밀번호 재설정") }) }
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(it).padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("비밀번호 재설정 화면입니다.")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateBack) {
                    Text("뒤로가기")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenPlaceholder(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(CallManagerService.isServiceRunning) }

    val sharedPrefs = remember {
        context.getSharedPreferences("call_manager_prefs", ComponentActivity.MODE_PRIVATE)
    }
    var autoStartEnabled by remember {
        mutableStateOf(sharedPrefs.getBoolean("auto_start_enabled", true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "콜 매니저 설정",
                style = MaterialTheme.typography.headlineSmall
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("콜 매니저 백그라운드 서비스", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "백그라운드에서 콜을 감지합니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isServiceEnabled,
                    onCheckedChange = { checked ->
                        isServiceEnabled = checked
                        val intent = Intent(context, CallManagerService::class.java)

                        if (checked) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        } else {
                            context.stopService(intent)
                        }
                    }
                )
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("앱 시작 시 자동 실행", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "앱 실행 시 콜 매니저 서비스 자동 시작",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = autoStartEnabled,
                    onCheckedChange = { checked ->
                        autoStartEnabled = checked
                        sharedPrefs.edit { putBoolean("auto_start_enabled", checked) }
                    }
                )
            }

            HorizontalDivider()

            Column(
                modifier = Modifier.padding(vertical = 16.dp)
            ) {
                Text("앱 버전: 1.0.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("개발자: 대리운전 관리자", style = MaterialTheme.typography.bodyMedium)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("닫기")
            }
        }
    }
}

@Preview(showBackground = true, name = "Login Screen Preview")
@Composable
fun LoginPreview() {
    CallManagerTheme {
        Text("Login Preview Disabled")
    }
}

@Preview(showBackground = true, name = "Dashboard Screen Preview")
@Composable
fun DashboardPreview() {
    CallManagerTheme {
        Text("Dashboard Preview Disabled")
    }
}

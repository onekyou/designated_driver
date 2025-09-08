package com.designated.callmanager

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import android.util.Log
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
import com.designated.callmanager.ui.drivermanagement.DriverManagementScreen
import com.designated.callmanager.ui.login.LoginScreen
import com.designated.callmanager.ui.login.LoginViewModel
import com.designated.callmanager.ui.pendingdrivers.PendingDriversScreen
import com.designated.callmanager.ui.settings.SettingsScreen
import com.designated.callmanager.ui.settlement.SettlementTabHost
import com.designated.callmanager.ui.signup.SignUpScreen
import com.designated.callmanager.ui.theme.CallManagerTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.net.Uri
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.content.IntentFilter

// Define screens for navigation
enum class Screen {
    Login,
    SignUp,
    PasswordReset,
    Dashboard,
    Settings,
    PendingDrivers,
    Settlement
}

// 화면 전환 시 전달할 데이터를 관리하는 Sealed Class
sealed class NavigationParams {
    object None : NavigationParams()
    data class DriverManagement(val regionId: String, val officeId: String) : NavigationParams()
}

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val dashboardViewModel: DashboardViewModel by viewModels { 
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application) 
    }
    private var isRequestingPermissions = java.util.concurrent.atomic.AtomicBoolean(false)
    
    // --- 사용자 정보 ---
    private var regionId: String? = null
    private var officeId: String? = null
    private var managerId: String? = null
    
    
    // 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "일부 기능을 사용하려면 권한이 필요합니다.", Toast.LENGTH_LONG).show()
        }
    }
    

    // 현재 보여줄 화면 상태를 Activity의 프로퍼티로 선언
    // Compose navigation: Use mutableState for single source of truth
    private val _screenState = mutableStateOf(Screen.Login)
    var screenState: Screen
        get() = _screenState.value
        set(value) { _screenState.value = value }

    // 화면 간 데이터 전달을 위한 상태 변수
    private var navigationParams: NavigationParams by mutableStateOf(NavigationParams.None)
    

    // 다이얼로그 표시 요청을 위한 StateFlow 추가
    private val _pendingCallDialogId = MutableStateFlow<String?>(null)
    private val pendingCallDialogId: StateFlow<String?> = _pendingCallDialogId.asStateFlow()
    
    // 콜 감지 브로드캐스트 리시버 (기존 대시보드 리스너용)
    private val callDetectedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.designated.callmanager.NEW_CALL_DETECTED") {
                val callId = intent.getStringExtra("callId")
                val phoneNumber = intent.getStringExtra("phoneNumber")
                val contactName = intent.getStringExtra("contactName")
                
                if (callId != null) {
                    // 대시보드 리스너에 의해 팝업이 자동 생성될 예정이므로 여기서는 처리하지 않음
                }
            }
        }
    }
    
    // 내부 콜 다이얼로그 브로드캐스트 수신자 (직접 팝업 호출용)
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
                            delay(300) // 화면 전환 대기
                            dashboardViewModel.showCallDialog(callId)
                        }
                    }
                }
            }
        }
    }

    // Constants for Intent Actions and Extras
    companion object {
        const val ACTION_SHOW_CALL_POPUP = "ACTION_SHOW_CALL_POPUP"
        const val ACTION_SHOW_SHARED_CALL = "ACTION_SHOW_SHARED_CALL"
        const val ACTION_SHOW_SHARED_CALL_CANCELLED = "ACTION_SHOW_SHARED_CALL_CANCELLED"
        const val ACTION_SHOW_SHARED_CALL_CANCELLED_NOTIFICATION = "ACTION_SHOW_SHARED_CALL_CANCELLED_NOTIFICATION"
        const val ACTION_SHOW_SHARED_CALL_CLAIMED = "ACTION_SHOW_SHARED_CALL_CLAIMED"
        const val ACTION_SHOW_NEW_CALL_WAITING = "ACTION_SHOW_NEW_CALL_WAITING"
        const val ACTION_SHOW_DEVICE_CRASH = "ACTION_SHOW_DEVICE_CRASH"
        const val ACTION_SHOW_TRIP_STARTED_POPUP = "ACTION_SHOW_TRIP_STARTED_POPUP"
        const val ACTION_SHOW_TRIP_COMPLETED_POPUP = "ACTION_SHOW_TRIP_COMPLETED_POPUP"
        const val EXTRA_CALL_ID = "callId" // FCM 서비스와 키 통일
        const val EXTRA_SHARED_CALL_ID = "sharedCallId"
    }

    // 권한 요청 결과 처리
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isRequestingPermissions.set(false)
        val allGranted = permissions.entries.all { it.value }
        
        if (allGranted) {
            // 일반 권한 승인 후, 다시 전체 권한 확인 (overlay 등)
            checkAndRequestPermissions()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Toast.makeText(
                this,
                "앱 기능 사용에 필요한 권한이 거부되었습니다.",
                Toast.LENGTH_LONG
            ).show()
            // 권한 거부 시 서비스 시작 시도 안함 (startCallManagerServiceIfNeeded 호출 제거)
        }
    }

    // 화면 위에 그리기 권한 결과 처리를 위한 ActivityResultLauncher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isRequestingPermissions.set(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val hasPermission = Settings.canDrawOverlays(this)
            
            // SharedPreferences KTX 사용
            getSharedPreferences("call_manager_prefs", MODE_PRIVATE).edit {
                putBoolean("overlay_permission_requested", true)
            }
            
            if (hasPermission) {
                // 권한 승인됨 토스트
                Toast.makeText(this, "백그라운드 콜 표시 활성화됨", Toast.LENGTH_SHORT).show()
            } else {
                // 권한 거부됨 토스트
                Toast.makeText(this, "백그라운드 콜 표시 비활성화됨", Toast.LENGTH_LONG).show()
            }
            
            // 권한 상태 변경 후 최종 확인 및 서비스 시작 시도
            checkAndRequestPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth


        // 배터리 최적화 제외 요청 (한 번만 요청)
        checkAndRequestBatteryOptimizationOnce()
        
        // 내부 콜 다이얼로그 브로드캐스트 수신자 등록
        val internalFilter = IntentFilter("com.designated.callmanager.INTERNAL_SHOW_CALL_DIALOG")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(internalCallDialogReceiver, internalFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(internalCallDialogReceiver, internalFilter)
        }

        // Apply API level check for setDecorFitsSystemWindows
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // Android 11 (API 30)
            window.setDecorFitsSystemWindows(false) // Recommended for edge-to-edge
        } else {
            // For older versions, you might not need this or use alternative flags
            // WindowCompat.setDecorFitsSystemWindows(window, false) // Use WindowCompat if needed
        }
        // 화면 깜빡임 방지를 위한 설정 - setDecorFitsSystemWindows(false)와 함께 사용하는 것이 일반적
        // window.setDecorFitsSystemWindows(true) // 이 줄은 제거하거나 주석 처리
        // 화면 깜빡임 방지를 위한 렌더링 설정
        window.setBackgroundDrawableResource(android.R.color.transparent)

        // <<-- Start of edit: Handle intent in onCreate -->>
        // currentScreen을 Activity 프로퍼티로 선언
        screenState = if (auth.currentUser == null) Screen.Login else Screen.Dashboard
        
        // 기존 로그인 사용자가 있을 경우 자동으로 콜 디텍터 설정 동기화
        if (auth.currentUser != null) {
            syncCallDetectorSettingsOnStartup()
        }
        
        handleIntent(intent) // onCreate에서도 동일한 핸들러 사용
        

        setContent {
            CallManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Compose navigation: Use MainActivity.screenState as single source of truth
                    val currentScreenState by this@MainActivity._screenState

                    // pendingCallDialogId 상태를 관찰하여 다이얼로그 표시
                    val callIdToShow by pendingCallDialogId.collectAsState()
                    val showNewCallPopup by dashboardViewModel.showNewCallPopup.collectAsState()

                    // ⭐️ 화면 상태와 보여줄 callId가 모두 준비되었을 때 다이얼로그를 띄우는 LaunchedEffect
                    LaunchedEffect(currentScreenState, callIdToShow) {
                        // 대시보드 화면이고, 표시할 callId가 있을 때만 팝업을 띄운다
                        if (currentScreenState == Screen.Dashboard && callIdToShow != null) {
                            val callId = callIdToShow!! // Null-safe
                            // 새로운 콜 팝업이 이미 표시 중이면 중복 팝업 방지
                            if (!showNewCallPopup) {
                                dashboardViewModel.showCallDialog(callId) // ViewModel 함수 호출
                            } else {
                            }
                            _pendingCallDialogId.value = null // 처리 후 상태를 반드시 초기화하여 재실행 방지
                        }
                    }

                    // Listen for auth state changes (now only for logout)
                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            // <<-- Start of edit: Log user state in listener -->>
                            val user = firebaseAuth.currentUser
                            // <<-- End of edit -->>
                            if (user == null) { // 로그아웃 상태만 처리
                                // <<-- Start of edit: Add specific log for logout detection -->>
                                // <<-- End of edit -->>
                                stopCallManagerService()
                                isRequestingPermissions.set(false) // 로그아웃 시 플래그 리셋
                                screenState = Screen.Login
                            }
                        }
                        auth.addAuthStateListener(listener)
                        onDispose {
                            auth.removeAuthStateListener(listener)
                        }
                    }

                    // 하드웨어 뒤로가기 처리
                    androidx.activity.compose.BackHandler {
                        when (currentScreenState) {
                            Screen.Dashboard, Screen.Login -> {
                                // 기본 동작: 앱 종료
                                finish()
                            }
                            Screen.Settings -> {
                                screenState = Screen.Dashboard
                            }
                            Screen.Settlement, Screen.PendingDrivers -> {
                                screenState = Screen.Settings
                            }
                            Screen.SignUp, Screen.PasswordReset -> {
                                screenState = Screen.Login
                            }
                        }
                    }

                    // Display the current screen
                    when (currentScreenState) {
                        Screen.Login -> LoginScreen(
                            onLoginComplete = { regionId, officeId ->
                                // 사용자 정보 저장
                                this@MainActivity.regionId = regionId
                                this@MainActivity.officeId = officeId
                                this@MainActivity.managerId = auth.currentUser?.uid
                                
                                dashboardViewModel.loadDataForUser(regionId, officeId) // Load data for the logged-in user
                                updateFcmTokenForAdmin(regionId, officeId) // FCM 토큰 업데이트 호출
                                screenState = Screen.Dashboard // Navigate to Dashboard
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
                            // Check permissions only when navigating to Dashboard
                            LaunchedEffect(Unit) {
                                checkAndRequestPermissions()
                            }
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onLogout = {
                                    auth.signOut()
                                },
                                onNavigateToSettings = { screenState = Screen.Settings }
                            )
                        }
                        Screen.Settings -> {
                            SettingsScreen(
                                dashboardViewModel = dashboardViewModel,
                                onNavigateBack = { screenState = Screen.Dashboard },
                                onNavigateToPendingDrivers = { regionId, officeId ->
                                    navigationParams = NavigationParams.DriverManagement(regionId, officeId)
                                    screenState = Screen.PendingDrivers
                                },
                                onNavigateToSettlement = {
                                    screenState = Screen.Settlement
                                }
                            )
                        }
                        Screen.PendingDrivers -> {
                            val params = navigationParams
                            if (params is NavigationParams.DriverManagement) {
                                DriverManagementScreen(
                                    regionId = params.regionId,
                                    officeId = params.officeId,
                                    onNavigateBack = { screenState = Screen.Settings }
                                )
                            } else {
                                // 파라미터가 없는 비정상적인 접근. 이전 화면으로 돌려보낸다.
                                LaunchedEffect(Unit) {
                                    Toast.makeText(this@MainActivity, "잘못된 접근입니다. 이전 화면으로 돌아갑니다.", Toast.LENGTH_SHORT).show()
                                    screenState = Screen.Settings
                                }
                            }
                        }
                        Screen.Settlement -> {
                            SettlementTabHost(
                                onBack = { screenState = Screen.Settings },
                                onHome = { screenState = Screen.Dashboard }
                            )
                        }
                    }
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // 중요: 새로운 Intent를 설정
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        
        // 백그라운드 FCM 알림 클릭 시 MAIN 액션으로 들어오는 경우 처리
        if (intent?.action == Intent.ACTION_MAIN || intent?.action == null) {
            // FCM extras 확인
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
            ACTION_SHOW_CALL_POPUP -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                if (callId != null) {
                    lifecycleScope.launch {
                        if (_screenState.value == Screen.Dashboard) {
                            dashboardViewModel.showCallDialog(callId)
                        } else {
                            _pendingCallDialogId.value = callId
                        }
                    }
                } else {
                }
            }
            ACTION_SHOW_SHARED_CALL -> {
                Log.d("MainActivity", "🔄 ACTION_SHOW_SHARED_CALL 액션 처리 시작")
                
                // 여러 방법으로 sharedCallId 추출 시도
                val sharedCallId = intent.getStringExtra(EXTRA_SHARED_CALL_ID) 
                    ?: intent.getStringExtra("sharedCallId")
                    ?: intent.extras?.getString(EXTRA_SHARED_CALL_ID)
                    ?: intent.extras?.getString("sharedCallId")
                
                Log.d("MainActivity", "📄 sharedCallId 추출 결과: $sharedCallId")
                
                if (sharedCallId != null) {
                    Log.d("MainActivity", "✅ sharedCallId 유효, 처리 진행: $sharedCallId")
                    // 해당 공유콜 알림 제거
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    val notificationId = "shared_call_$sharedCallId".hashCode()
                    
                    
                    // 계산된 ID로 제거 시도
                    notificationManager.cancel(notificationId)
                    
                    // 모든 활성 알림 제거 (임시 해결책)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val activeNotifications = notificationManager.activeNotifications
                        activeNotifications.forEach { notification ->
                            if (notification.notification.extras?.getString("sharedCallId") == sharedCallId ||
                                notification.notification.tickerText?.contains("공유콜") == true) {
                                notificationManager.cancel(notification.id)
                            }
                        }
                    }
                    
                    lifecycleScope.launch {
                        // 대시보드로 이동
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                            kotlinx.coroutines.delay(300) // 화면 전환 대기
                        }
                        // 공유콜 데이터를 찾아서 알림 팝업 표시
                        Log.d("MainActivity", "🎯 dashboardViewModel.showSharedCallNotificationFromId 호출: $sharedCallId")
                        dashboardViewModel.showSharedCallNotificationFromId(sharedCallId)
                    }
                } else {
                    Log.w("MainActivity", "❌ sharedCallId가 null입니다. Intent extras: ${intent.extras}")
                }
            }
            ACTION_SHOW_SHARED_CALL_CANCELLED -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                if (callId != null) {
                    lifecycleScope.launch {
                        // 대시보드로 이동하고 취소 알림 표시
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        // 공유콜 취소 알림 다이얼로그 표시
                        dashboardViewModel.showSharedCallCancelledDialog(callId)
                    }
                } else {
                }
            }
            
            ACTION_SHOW_TRIP_STARTED_POPUP -> {
                
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val driverName = intent.getStringExtra("driverName") ?: "기사"
                val driverPhone = intent.getStringExtra("driverPhone") ?: ""
                val customerName = intent.getStringExtra("customerName") ?: "고객"
                val tripSummary = intent.getStringExtra("tripSummary") ?: ""
                
                
                lifecycleScope.launch {
                    // 대시보드로 이동 후 팝업 표시
                    if (_screenState.value != Screen.Dashboard) {
                        _screenState.value = Screen.Dashboard
                    }
                    dashboardViewModel.showTripStartedPopup(driverName, driverPhone, tripSummary, customerName)
                }
            }
            
            ACTION_SHOW_TRIP_COMPLETED_POPUP -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val driverName = intent.getStringExtra("driverName") ?: "기사"
                val customerName = intent.getStringExtra("customerName") ?: "고객"
                
                lifecycleScope.launch {
                    // 대시보드로 이동 후 팝업 표시
                    if (_screenState.value != Screen.Dashboard) {
                        _screenState.value = Screen.Dashboard
                    }
                    dashboardViewModel.showTripCompletedPopup(driverName, customerName)
                }
            }
            
            ACTION_SHOW_SHARED_CALL_CANCELLED_NOTIFICATION -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val cancelReason = intent.getStringExtra("cancelReason")
                if (callId != null) {
                    lifecycleScope.launch {
                        // 대시보드로 이동하고 간단한 알림 표시
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        // 토스트나 스낵바로 간단한 알림 표시
                        showToast("공유콜이 취소되었습니다: ${cancelReason ?: "사유 없음"}")
                    }
                }
            }
            
            ACTION_SHOW_SHARED_CALL_CLAIMED -> {
                val sharedCallId = intent.getStringExtra(EXTRA_SHARED_CALL_ID)
                if (sharedCallId != null) {
                    lifecycleScope.launch {
                        // 대시보드로 이동
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        // 공유콜 수락 알림 표시
                        showToast("공유콜이 다른 사무실에서 수락되었습니다")
                    }
                }
            }
            
            ACTION_SHOW_NEW_CALL_WAITING -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val customerPhone = intent.getStringExtra("customerPhone")
                if (callId != null) {
                    lifecycleScope.launch {
                        // 대시보드로 이동하고 해당 콜 하이라이트
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        // 새로운 콜 알림 표시
                        showToast("새로운 콜이 접수되었습니다: ${customerPhone ?: ""}")
                    }
                }
            }
            
            ACTION_SHOW_DEVICE_CRASH -> {
                val deviceId = intent.getStringExtra("deviceId")
                val timestamp = intent.getLongExtra("timestamp", 0L)
                if (deviceId != null) {
                    // 크래시 상세 정보 팝업 표시
                    showDeviceCrashDialog(deviceId, timestamp)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        
        // 콜 감지 브로드캐스트 리시버 등록
        val filter = IntentFilter("com.designated.callmanager.NEW_CALL_DETECTED")
        
        // Android 14 (API 34) 이상에서는 RECEIVER_NOT_EXPORTED 플래그 필요
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callDetectedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callDetectedReceiver, filter)
        }
        
        // 저장된 Pending 팝업 확인 및 표시
        checkAndShowPendingPopup()
    }
    
    override fun onPause() {
        super.onPause()
        // 콜 감지 브로드캐스트 리시버 해제
        try {
            unregisterReceiver(callDetectedReceiver)
        } catch (e: IllegalArgumentException) {
            // 리시버가 등록되지 않은 경우 무시
        }
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
            
            // 10분 이내의 팝업만 표시 (너무 오래된 팝업 방지)
            val tenMinutesAgo = System.currentTimeMillis() - (10 * 60 * 1000)
            
            if (timestamp > tenMinutesAgo) {
                
                lifecycleScope.launch {
                    // 대시보드로 이동 후 팝업 표시
                    if (_screenState.value != Screen.Dashboard) {
                        _screenState.value = Screen.Dashboard
                    }
                    
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
            
            // 처리 후 저장된 팝업 정보 삭제
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

    private fun checkAndRequestPermissions() {
        if (!isRequestingPermissions.compareAndSet(false, true)) { // 함수 진입 시 플래그 설정 시도
            return
        }
        

        // --- 1단계: 일반 권한 확인 및 요청 --- 
        val requiredPermissions = mutableListOf<String>()
        // 알림 권한 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 위치 권한 (서비스 동작에 필요)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // COARSE 위치 권한도 함께 요청하는 것이 좋음
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
             requiredPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        
        
        // 전화 감지 권한 추가 (콜 디텍터 기능용)
        val prefs = getSharedPreferences("call_manager_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("call_detection_enabled", false)) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_PHONE_STATE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_NUMBERS) != PackageManager.PERMISSION_GRANTED) {
                    requiredPermissions.add(Manifest.permission.READ_PHONE_NUMBERS)
                }
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_CALL_LOG)
            }
            // 연락처 읽기 권한 (고객명과 주소 정보)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.READ_CONTACTS)
            }
            // Android에서는 PROCESS_OUTGOING_CALLS가 deprecated되었지만 여전히 필요할 수 있음
            if (ContextCompat.checkSelfPermission(this, "android.permission.PROCESS_OUTGOING_CALLS") != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add("android.permission.PROCESS_OUTGOING_CALLS")
            }
            // SMS 발송 권한 (공유콜 시스템에서 마감 시 자동 문자 발송용)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.SEND_SMS)
            }
        }
        
        if (requiredPermissions.isNotEmpty()) {
            // isRequestingPermissions는 이미 true 상태
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
            // 여기서 return, 결과는 requestPermissionsLauncher 콜백에서 처리 후 checkAndRequestPermissions 재호출
            return 
        }

        // --- 2단계: 화면 위에 그리기 권한 확인 및 요청 --- 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {

            // 항상 다이얼로그를 표시하여 사용자에게 권한의 필요성을 알리고 설정으로 유도합니다.
            showOverlayPermissionDialog()
            return // 다이얼로그를 띄우고 나면, 사용자의 선택을 기다려야 하므로 여기서 함수를 종료합니다.
        }

        // --- 3단계: 모든 권한 확인 완료, 서비스 시작 --- 
        startCallManagerServiceIfNeeded()
        
        // 모든 확인/요청 절차 완료 후 플래그 최종 리셋
        isRequestingPermissions.set(false)
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("필수 권한 안내")
            .setMessage("앱의 정상적인 사용을 위해 '다른 앱 위에 표시' 권한이 반드시 필요합니다. 설정 화면으로 이동하여 권한을 허용해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton("나중에") { dialog, _ ->
                Toast.makeText(this, "권한이 없어 일부 기능이 제한됩니다.", Toast.LENGTH_LONG).show()
                isRequestingPermissions.set(false) // 다이얼로그가 닫혔으므로 플래그 리셋
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    // 서비스 시작 로직 분리 (중복 호출 방지 및 명확성)
    private fun startCallManagerServiceIfNeeded() {
        // 필수 권한 확인 (예: 위치 권한)
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        // 필요한 모든 권한이 부여되었는지 확인 후 서비스 관리
        if (hasLocationPermission) { // 서비스 시작에 필요한 최소 권한 (여기서는 위치)
            // ViewModel을 통해 서비스 시작 요청 (중복 실행 방지 로직은 ViewModel 또는 Service 내부에 있어야 함)
            val serviceIntent = Intent(this, CallManagerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
             Toast.makeText(this, "위치 권한이 없어 콜 서비스를 시작할 수 없습니다.", Toast.LENGTH_LONG).show()
             // 권한 부족 시에도 서비스가 실행 중이면 중지 (선택적, ViewModel에서 처리 가능)
             val serviceIntent = Intent(this, CallManagerService::class.java)
             stopService(serviceIntent)
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateFcmTokenForAdmin(regionId: String, officeId: String) {
        val adminId = auth.currentUser?.uid ?: return // 현재 로그인한 사용자의 UID를 가져옵니다.

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                val firestore = FirebaseFirestore.getInstance()
                
                // ✅ 올바른 경로: admins 최상위 컬렉션 사용 (set with merge)
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
        // 브로드캐스트 수신자 해제
        try {
            unregisterReceiver(internalCallDialogReceiver)
        } catch (e: Exception) {
            // 이미 해제된 경우 무시
        }
        // 앱 종료 시 서비스 중지
        val serviceIntent = Intent(this, CallManagerService::class.java)
        stopService(serviceIntent)
    }
    
    private fun checkAndRequestBatteryOptimizationOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val packageName = packageName
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val prefs = getSharedPreferences("call_manager_prefs", MODE_PRIVATE)
            val hasRequestedBefore = prefs.getBoolean("battery_optimization_requested", false)
            
            if (!powerManager.isIgnoringBatteryOptimizations(packageName) && !hasRequestedBefore) {
                
                AlertDialog.Builder(this)
                    .setTitle("백그라운드 작업 허용")
                    .setMessage("콜 매니저가 백그라운드에서 정상 작동하려면 배터리 최적화에서 제외해야 합니다.\n\n기사 운행 시작/완료 알림을 받으려면 설정에서 이 앱을 '최적화하지 않음'으로 설정해 주세요.")
                    .setPositiveButton("설정으로 이동") { _, _ ->
                        // 요청했음을 기록
                        prefs.edit {
                            putBoolean("battery_optimization_requested", true)
                        }
                        
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (_: Exception) {
                            // 대체 방법: 일반 배터리 최적화 설정 화면
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    .setNegativeButton("나중에") { _, _ -> 
                        // 나중에 선택해도 요청했음을 기록 (하루 후 다시 요청하려면 이 줄 제거)
                        prefs.edit {
                            putBoolean("battery_optimization_requested", true)
                        }
                    }
                    .show()
            } else if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
            } else {
            }
        }
    }
    
    
    /**
     * 앱 시작 시 기존 로그인 사용자의 콜 디텍터 설정 자동 동기화
     */
    private fun syncCallDetectorSettingsOnStartup() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.w("MainActivity", "syncCallDetectorSettingsOnStartup: 로그인된 사용자가 없음")
            return
        }
        
        Log.i("MainActivity", "🔄 기존 로그인 사용자 콜 디텍터 설정 동기화 시작")
        
        lifecycleScope.launch {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val adminId = currentUser.uid
                
                // admins 컬렉션에서 사용자 정보 조회
                firestore.collection("admins").document(adminId)
                    .get()
                    .addOnSuccessListener { document ->
                        if (document.exists()) {
                            val regionId = document.getString("associatedRegionId")
                            val officeId = document.getString("associatedOfficeId")
                            
                            if (regionId != null && officeId != null) {
                                Log.i("MainActivity", "✅ 관리자 정보 조회 성공: regionId=$regionId, officeId=$officeId")
                                
                                // MainActivity 인스턴스 변수에도 저장
                                this@MainActivity.regionId = regionId
                                this@MainActivity.officeId = officeId
                                this@MainActivity.managerId = adminId
                                
                                // DashboardViewModel 초기화 및 콜 디텍터 설정 동기화
                                dashboardViewModel.loadDataForUser(regionId, officeId) // ViewModel 초기화
                                dashboardViewModel.syncCallDetectorSettings(regionId, officeId) // 콜 디텍터 설정
                                
                                Log.i("MainActivity", "🔄 콜 디텍터 설정 동기화 완료")
                            } else {
                                Log.w("MainActivity", "❌ 관리자 문서에 regionId 또는 officeId가 없음")
                            }
                        } else {
                            Log.w("MainActivity", "❌ 관리자 문서가 존재하지 않음: $adminId")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("MainActivity", "❌ 관리자 정보 조회 실패", e)
                    }
            } catch (e: Exception) {
                Log.e("MainActivity", "❌ syncCallDetectorSettingsOnStartup 실행 중 오류", e)
            }
        }
    }
}

// Placeholder for Sign Up Screen
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

// Placeholder for Password Reset Screen
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

// Placeholder for Settings Screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenPlaceholder(
    viewModel: DashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isServiceEnabled by remember { mutableStateOf(CallManagerService.isServiceRunning) }
    
    // 자동 시작 설정 상태 관리
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
                            imageVector = Icons.Default.ArrowBack, // 기본 아이콘 사용
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
            
            // 콜 매니저 서비스 상태
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
                            // 서비스 시작
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        } else {
                            // 서비스 중지
                            context.stopService(intent)
                        }
                    }
                )
            }
            
            HorizontalDivider()
            
            // 자동 시작 설정
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
                        // KTX 사용
                        sharedPrefs.edit { putBoolean("auto_start_enabled", checked) }
                    }
                )
            }
            
            HorizontalDivider()
            
            // 앱 정보
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


// Preview for LoginScreen
@Preview(showBackground = true, name = "Login Screen Preview")
@Composable
fun LoginPreview() {
    CallManagerTheme {
        Text("Login Preview Disabled")
    }
}

// Preview for DashboardScreen
@Preview(showBackground = true, name = "Dashboard Screen Preview")
@Composable
fun DashboardPreview() {
    CallManagerTheme {
        Text("Dashboard Preview Disabled") // 주석 처리 대신 Placeholder 텍스트 표시 (선택 사항)
    }
} 

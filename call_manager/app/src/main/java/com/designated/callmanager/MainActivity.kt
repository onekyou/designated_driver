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
import com.designated.callmanager.ptt.ui.PTTScreen
import com.designated.callmanager.ui.setup.InitialSetupActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    Settlement,
    PTT
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
    // isRequestingPermissions은 InitialSetup에서 처리하므로 제거
    
    // --- 사용자 정보 ---
    private var regionId: String? = null
    private var officeId: String? = null
    private var managerId: String? = null
    
    
    // InitialSetup에서 권한을 처리하므로 기존 런처들은 제거
    

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

    // 기존 권한 런처들은 InitialSetup으로 이동했으므로 제거

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        // 최초 설정 완료 여부 확인
        val setupPrefs = getSharedPreferences("initial_setup", Context.MODE_PRIVATE)
        val isSetupCompleted = setupPrefs.getBoolean("completed", false)
        
        if (!isSetupCompleted) {
            // 최초 설정이 완료되지 않았으면 InitialSetupActivity로 이동
            val intent = Intent(this, InitialSetupActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // 배터리 최적화 제외 요청 (한 번만 요청) - 이제 InitialSetup에서 처리하므로 제거
        // checkAndRequestBatteryOptimizationOnce()
        
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
                                // isRequestingPermissions 플래그는 제거됨
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
                            Screen.Settlement, Screen.PendingDrivers, Screen.PTT -> {
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
                            // 이제 InitialSetup에서 권한을 처리하므로 여기서는 바로 서비스 시작
                            LaunchedEffect(Unit) {
                                startCallManagerServiceIfNeeded()
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
                                },
                                onNavigateToPTT = {
                                    screenState = Screen.PTT
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
                        Screen.PTT -> {
                            PTTScreen(
                                onNavigateBack = { screenState = Screen.Settings }
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
                
                // 여러 방법으로 sharedCallId 추출 시도
                val sharedCallId = intent.getStringExtra(EXTRA_SHARED_CALL_ID) 
                    ?: intent.getStringExtra("sharedCallId")
                    ?: intent.extras?.getString(EXTRA_SHARED_CALL_ID)
                    ?: intent.extras?.getString("sharedCallId")
                    
                
                if (sharedCallId != null) {
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
                        dashboardViewModel.showSharedCallNotificationFromId(sharedCallId)
                    }
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

    // InitialSetup에서 모든 권한 처리 완료 후 서비스 시작
    private fun checkAndRequestPermissions() {
        startCallManagerServiceIfNeeded()
    }

    // 서비스 시작 (권한 체크 없이 - InitialSetup에서 이미 완료)
    private fun startCallManagerServiceIfNeeded() {
        val serviceIntent = Intent(this, CallManagerService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
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
    
    // 배터리 최적화는 InitialSetupActivity에서 처리
    
    
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

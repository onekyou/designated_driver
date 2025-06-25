package com.designated.callmanager

import android.Manifest
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
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
import java.util.concurrent.atomic.AtomicBoolean
import android.net.Uri
import android.os.PowerManager

// Define screens for navigation
enum class Screen {
    Login,
    SignUp,
    PasswordReset,
    Dashboard,
    Settings,
    PendingDrivers,
    Settlement,
    CallManagement
}

// 화면 전환 시 전달할 데이터를 관리하는 Sealed Class
sealed class NavigationParams {
    object None : NavigationParams()
    data class DriverManagement(val regionId: String, val officeId: String) : NavigationParams()
}

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val tag = "MainActivity"
    private val dashboardViewModel: DashboardViewModel by viewModels { 
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application) 
    }
    private var isRequestingPermissions = AtomicBoolean(false)

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

    // Constants for Intent Actions and Extras
    companion object {
        const val ACTION_SHOW_CALL_POPUP = "ACTION_SHOW_CALL_POPUP"
        const val EXTRA_CALL_ID = "callId" // FCM 서비스와 키 통일
    }

    // 권한 요청 결과 처리
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        isRequestingPermissions.set(false)
        val allGranted = permissions.entries.all { it.value }
        
        if (allGranted) {
            Log.d(tag, "모든 일반 권한이 승인되었습니다. 다음 단계 확인 시작.")
            // 일반 권한 승인 후, 다시 전체 권한 확인 (overlay 등)
            checkAndRequestPermissions()
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Log.w(tag, "일부 일반 권한이 거부되었습니다: $deniedPermissions")
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
            Log.d(tag, "화면 위에 그리기 권한 결과 확인: ${if (hasPermission) "승인됨" else "거부됨"}")
            
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
            Log.d(tag, "Overlay 권한 처리 완료. 최종 확인 및 서비스 시작 시도.")
            checkAndRequestPermissions()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        // 배터리 최적화 제외 요청 (한 번만 요청)
        checkAndRequestBatteryOptimizationOnce()

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
        Log.d(tag, "onCreate - Intent: ${intent?.action}, Extras: ${intent?.extras?.keySet()}")
        // currentScreen을 Activity 프로퍼티로 선언
        screenState = if (auth.currentUser == null) Screen.Login else Screen.Dashboard
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
                            Log.d(tag, "LaunchedEffect triggered to show dialog for call ID: $callId")
                            // 새로운 콜 팝업이 이미 표시 중이면 중복 팝업 방지
                            if (!showNewCallPopup) {
                                dashboardViewModel.showCallDialog(callId) // ViewModel 함수 호출
                            } else {
                                Log.d(tag, "NewCallPopup already showing, skipping duplicate CallInfoDialog")
                            }
                            _pendingCallDialogId.value = null // 처리 후 상태를 반드시 초기화하여 재실행 방지
                        }
                    }

                    // Listen for auth state changes (now only for logout)
                    DisposableEffect(Unit) {
                        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                            // <<-- Start of edit: Log user state in listener -->>
                            val user = firebaseAuth.currentUser
                            Log.d(tag, "AuthStateListener triggered: user is ${if (user == null) "NULL" else "NOT NULL (UID: ${user.uid})"}")
                            // <<-- End of edit -->>
                            if (user == null) { // 로그아웃 상태만 처리
                                // <<-- Start of edit: Add specific log for logout detection -->>
                                Log.i(tag, "AuthStateListener detected user is NULL (Logout state).")
                                // <<-- End of edit -->>
                                Log.d(tag, "로그아웃 상태 감지, 서비스 중지 및 로그인 화면으로 이동")
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
                            Screen.CallManagement -> {
                                screenState = Screen.Dashboard
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
                                Log.i(tag, "Login successful (onLoginComplete). regionId=$regionId, officeId=$officeId")
                                Log.d(tag, "  Checking dashboardViewModel instance: ${if (dashboardViewModel != null) "Exists" else "NULL"}")
                                Log.d(tag, "  Calling dashboardViewModel.loadDataForUser...")
                                dashboardViewModel.loadDataForUser(regionId, officeId) // Load data for the logged-in user
                                Log.d(tag, "  loadDataForUser called. Updating FCM Token...")
                                updateFcmTokenForAdmin(regionId, officeId) // FCM 토큰 업데이트 호출
                                Log.d(tag, "  FCM Token update requested. Navigating to Dashboard screen...")
                                screenState = Screen.Dashboard // Navigate to Dashboard
                                Log.d(tag, "  screenState updated to Dashboard.")
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
                                Log.d(tag, "Navigated to Dashboard, checking permissions.")
                                checkAndRequestPermissions()
                            }
                            DashboardScreen(
                                viewModel = dashboardViewModel,
                                onLogout = {
                                    Log.d(tag, "Logout button clicked. Signing out.")
                                    auth.signOut()
                                },
                                onNavigateToSettings = { screenState = Screen.Settings },
                                onNavigateToCallManagement = { screenState = Screen.CallManagement }
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
                            com.designated.callmanager.ui.settlement.SettlementScreen(
                                onNavigateBack = { screenState = Screen.Settings },
                                onNavigateHome = { screenState = Screen.Dashboard }
                            )
                        }
                        Screen.CallManagement -> {
                            com.designated.callmanager.ui.callmanagement.CallManagementScreen(
                                onNavigateBack = { screenState = Screen.Dashboard },
                                onNavigateHome = { screenState = Screen.Dashboard }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(tag, "onNewIntent - Intent: ${intent?.action}, Extras: ${intent?.extras?.keySet()}")
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_CALL_POPUP) {
            val callId = intent.getStringExtra(EXTRA_CALL_ID)
            if (callId != null) {
                Log.d(tag, "handleIntent: ACTION_SHOW_CALL_POPUP 확인. callId: $callId")
                lifecycleScope.launch {
                    if (_screenState.value == Screen.Dashboard) {
                        Log.d(tag, "Dashboard에서 즉시 다이얼로그 표시")
                        dashboardViewModel.showCallDialog(callId)
                    } else {
                        Log.w(tag, "Dashboard가 아니므로 팝업을 보류합니다. 현재 화면: ${_screenState.value}")
                        _pendingCallDialogId.value = callId
                    }
                }
            } else {
                Log.w(tag, "handleIntent: callId가 null입니다.")
            }
        }
    }

    private fun stopCallManagerService() {
        val serviceIntent = Intent(this, CallManagerService::class.java)
        stopService(serviceIntent)
    }

    private fun checkAndRequestPermissions() {
        if (!isRequestingPermissions.compareAndSet(false, true)) { // 함수 진입 시 플래그 설정 시도
            Log.d(tag, "이미 다른 권한 요청/확인 프로세스가 진행 중입니다.")
            return
        }
        
        Log.d(tag, "권한 확인 프로세스 시작...")

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
        
        if (requiredPermissions.isNotEmpty()) {
            Log.d(tag, "필요한 일반 권한: $requiredPermissions. 요청 시작.")
            // isRequestingPermissions는 이미 true 상태
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
            // 여기서 return, 결과는 requestPermissionsLauncher 콜백에서 처리 후 checkAndRequestPermissions 재호출
            return 
        }
        Log.d(tag, "1단계: 모든 일반 권한이 이미 승인되었습니다.")

        // --- 2단계: 화면 위에 그리기 권한 확인 및 요청 --- 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.d(tag, "2단계: 화면 위에 그리기 권한이 필요합니다. 안내 다이얼로그 표시.")

            // 항상 다이얼로그를 표시하여 사용자에게 권한의 필요성을 알리고 설정으로 유도합니다.
            showOverlayPermissionDialog()
            return // 다이얼로그를 띄우고 나면, 사용자의 선택을 기다려야 하므로 여기서 함수를 종료합니다.
        }
        Log.d(tag, "2단계: 화면 위에 그리기 권한이 이미 승인되었습니다.")

        // --- 3단계: 모든 권한 확인 완료, 서비스 시작 --- 
        Log.d(tag, "3단계: 모든 필수 권한 확인 완료. 서비스 시작 로직 실행.")
        startCallManagerServiceIfNeeded()
        
        // 모든 확인/요청 절차 완료 후 플래그 최종 리셋
        isRequestingPermissions.set(false)
        Log.d(tag, "권한 확인 프로세스 완료, 플래그 리셋.")
    }

    private fun showOverlayPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("필수 권한 안내")
            .setMessage("앱의 정상적인 사용을 위해 '다른 앱 위에 표시' 권한이 반드시 필요합니다. 설정 화면으로 이동하여 권한을 허용해주세요.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
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
        Log.d(tag, "서비스 시작 필요 여부 확인...")
        // 필수 권한 확인 (예: 위치 권한)
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        // 필요한 모든 권한이 부여되었는지 확인 후 서비스 관리
        if (hasLocationPermission) { // 서비스 시작에 필요한 최소 권한 (여기서는 위치)
            Log.d(tag, "서비스 시작에 필요한 최소 권한 확인 완료. 서비스 관리 시작.")
            // ViewModel을 통해 서비스 시작 요청 (중복 실행 방지 로직은 ViewModel 또는 Service 내부에 있어야 함)
            val serviceIntent = Intent(this, CallManagerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } else {
             Log.w(tag, "서비스 시작에 필요한 최소 권한(위치)이 부족하여 서비스를 시작하지 않습니다.")
             Toast.makeText(this, "위치 권한이 없어 콜 서비스를 시작할 수 없습니다.", Toast.LENGTH_LONG).show()
             // 권한 부족 시에도 서비스가 실행 중이면 중지 (선택적, ViewModel에서 처리 가능)
             val serviceIntent = Intent(this, CallManagerService::class.java)
             stopService(serviceIntent)
        }
    }

    private fun updateFcmTokenForAdmin(regionId: String, officeId: String) {
        val adminId = auth.currentUser?.uid ?: return // 현재 로그인한 사용자의 UID를 가져옵니다.

        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "FCM Token obtained: $token")
                val firestore = FirebaseFirestore.getInstance()
                
                // ✅ 올바른 경로: admins 최상위 컬렉션 사용 (set with merge)
                val tokenData = hashMapOf("fcmToken" to token)
                
                firestore.collection("admins").document(adminId)
                    .set(tokenData, com.google.firebase.firestore.SetOptions.merge())
                    .addOnSuccessListener { Log.d("FCM", "✅ 관리자 FCM 토큰 Firestore 저장 성공 (Admin: $adminId)") }
                    .addOnFailureListener { e -> 
                        Log.e("FCM", "❌ 관리자 FCM 토큰 Firestore 저장 실패 (Admin: $adminId)", e)
                        Log.e("FCM", "  실패 원인: ${e.message}")
                    }
            } else {
                Log.w("FCM", "FCM 토큰 발급 실패", task.exception)
            }
        }
    }
    


    override fun onDestroy() {
        super.onDestroy()
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
                Log.d(tag, "배터리 최적화 제외가 필요합니다. 사용자에게 요청합니다.")
                
                AlertDialog.Builder(this)
                    .setTitle("백그라운드 작업 허용")
                    .setMessage("콜 매니저가 백그라운드에서 정상 작동하려면 배터리 최적화에서 제외해야 합니다.\n\n기사 운행 시작/완료 알림을 받으려면 설정에서 이 앱을 '최적화하지 않음'으로 설정해 주세요.")
                    .setPositiveButton("설정으로 이동") { _, _ ->
                        // 요청했음을 기록
                        prefs.edit().putBoolean("battery_optimization_requested", true).apply()
                        
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            intent.data = Uri.parse("package:$packageName")
                            startActivity(intent)
                        } catch (e: Exception) {
                            Log.e(tag, "배터리 최적화 설정 화면 열기 실패", e)
                            // 대체 방법: 일반 배터리 최적화 설정 화면
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                startActivity(intent)
                            } catch (e2: Exception) {
                                Log.e(tag, "배터리 최적화 설정 화면 열기 실패 (대체 방법)", e2)
                            }
                        }
                    }
                    .setNegativeButton("나중에") { _, _ -> 
                        // 나중에 선택해도 요청했음을 기록 (하루 후 다시 요청하려면 이 줄 제거)
                        prefs.edit().putBoolean("battery_optimization_requested", true).apply()
                    }
                    .show()
            } else if (powerManager.isIgnoringBatteryOptimizations(packageName)) {
                Log.d(tag, "배터리 최적화가 이미 제외되어 있습니다.")
            } else {
                Log.d(tag, "배터리 최적화 요청을 이전에 했으므로 다시 요청하지 않습니다.")
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

// LoginViewModel을 제공하는 PreviewParameterProvider
class LoginViewModelProvider : PreviewParameterProvider<LoginViewModel> {
    override val values = sequenceOf(
        // 빈 값만 있는 임시 LoginViewModel
        LoginViewModel(Application()).apply {
            email = "test@example.com"
            password = "password"
        }
    )
}

// Preview for LoginScreen
@Preview(showBackground = true, name = "Login Screen Preview")
@Composable
fun LoginPreview(
    @PreviewParameter(LoginViewModelProvider::class) viewModel: LoginViewModel
) {
    CallManagerTheme {
        LoginScreen(
            onLoginComplete = { _, _ ->
                // Preview: No real action needed here.
                // Log.i("LoginPreview", "Preview login complete: $regionId, $officeId") // Can use fixed tag if needed
            },
            onNavigateToSignUp = {},
            onNavigateToPasswordReset = {},
            viewModel = viewModel
        )
    }
}

// Preview for DashboardScreen
@Preview(showBackground = true, name = "Dashboard Screen Preview")
@Composable
fun DashboardPreview() {
    CallManagerTheme {
        // DashboardScreen(onLogout = {}) // viewModel 파라미터 누락으로 오류 발생, 임시 주석 처리
        Text("Dashboard Preview Disabled") // 주석 처리 대신 Placeholder 텍스트 표시 (선택 사항)
    }
} 

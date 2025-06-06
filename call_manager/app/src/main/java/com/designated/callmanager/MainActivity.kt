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
import com.designated.callmanager.ui.login.LoginScreen
import com.designated.callmanager.ui.login.LoginViewModel
import com.designated.callmanager.ui.pendingdrivers.PendingDriversScreen
import com.designated.callmanager.ui.pendingdrivers.PendingDriversViewModel
import com.designated.callmanager.ui.settings.SettingsScreen
import com.designated.callmanager.ui.signup.SignUpScreen
import com.designated.callmanager.ui.theme.CallManagerTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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

    // 다이얼로그 표시 요청을 위한 StateFlow 추가
    private val _pendingCallDialogId = MutableStateFlow<String?>(null)
    private val pendingCallDialogId: StateFlow<String?> = _pendingCallDialogId.asStateFlow()

    // Constants for Intent Actions and Extras (matching CallManagerService)
    companion object {
        const val ACTION_SHOW_CALL_DIALOG = "com.designated.callmanager.ACTION_SHOW_CALL_DIALOG"
        const val EXTRA_CALL_ID = "com.designated.callmanager.EXTRA_CALL_ID"
        // ACTION_ASSIGN_FROM_NOTIFICATION는 Service에서 사용, 여기서는 확인용
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
        val intentActionOnCreate = getIntent()?.action
        val intentCallIdOnCreate = getIntent()?.getStringExtra(EXTRA_CALL_ID)
        if (intentActionOnCreate == ACTION_SHOW_CALL_DIALOG && intentCallIdOnCreate != null) {
            if (auth.currentUser != null) {
                _pendingCallDialogId.value = intentCallIdOnCreate
            }
        }

        setContent {
            CallManagerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Compose navigation: Use MainActivity.screenState as single source of truth
                    val screenState = this@MainActivity._screenState

                    // pendingCallDialogId 상태를 관찰하여 다이얼로그 표시
                    val callIdToShow by pendingCallDialogId.collectAsState()
                    LaunchedEffect(callIdToShow) {
                        callIdToShow?.let { callId ->
                            Log.d(tag, "LaunchedEffect triggered to show dialog for call ID: $callId")
                            dashboardViewModel.showCallDialog(callId) // ViewModel 함수 호출
                            _pendingCallDialogId.value = null // 처리 후 상태 초기화
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
                                screenState.value = Screen.Login
                            }
                        }
                        auth.addAuthStateListener(listener)
                        onDispose {
                            auth.removeAuthStateListener(listener)
                        }
                    }

                    // Display the current screen
                    when (screenState.value) {
                        Screen.Login -> LoginScreen(
                            // <<-- Start of edit: Implement onLoginComplete callback -->>
                            onLoginComplete = { regionId, officeId ->
                                // <<-- Start of edit: Add logging before and after loadDataForUser -->>
                                Log.i(tag, "Login successful (onLoginComplete). regionId=$regionId, officeId=$officeId")
                                // <<-- Start of edit: Add log to check if dashboardViewModel instance exists -->>
                                Log.d(tag, "  Checking dashboardViewModel instance: ${if (dashboardViewModel != null) "Exists" else "NULL"}")
                                // <<-- End of edit -->>
                                Log.d(tag, "  Calling dashboardViewModel.loadDataForUser...")
                                dashboardViewModel.loadDataForUser(regionId, officeId) // Load data for the logged-in user
                                Log.d(tag, "  loadDataForUser called. Navigating to Dashboard screen...")
                                screenState.value = Screen.Dashboard // Navigate to Dashboard
                                Log.d(tag, "  screenState updated to Dashboard.")
                                // <<-- End of edit -->>
                            },
                            // <<-- End of edit -->>
                            onNavigateToSignUp = { screenState.value = Screen.SignUp },
                            onNavigateToPasswordReset = { screenState.value = Screen.PasswordReset }
                        )
                        Screen.SignUp -> SignUpScreen(
                            onSignUpSuccess = { screenState.value = Screen.Login },
                            onNavigateBack = { screenState.value = Screen.Login }
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
                                    Log.d(tag, "Logout button clicked, signing out.")

                                    // <<-- Start of edit: Log before/after commit -->>
                                    val sharedPrefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                                    val editor = sharedPrefs.edit()
                                    editor.putBoolean("auto_login", false)
                                    editor.remove("email")
                                    editor.remove("password")
                                    Log.d(tag, "Attempting to commit auto-login prefs clear...")
                                    val success = editor.commit() // Use traditional commit to get Boolean result
                                    Log.d(tag, "Commit result for clearing auto-login prefs: $success")
                                    // <<-- End of edit -->>

                                    if (success) {
                                        Log.d(tag, "Auto-login preferences cleared synchronously.")
                                    } else {
                                        Log.e(tag, "Failed to clear auto-login preferences synchronously.")
                                        // Consider notifying the user or alternative handling if commit fails
                                    }

                                    auth.signOut() // Trigger AuthStateListener
                                },
                                onNavigateToSettings = { screenState.value = Screen.Settings },
                                onNavigateToPendingDrivers = { screenState.value = Screen.PendingDrivers }
                            )
                        }
                        Screen.Settings -> SettingsScreen(
                             onNavigateBack = { screenState.value = Screen.Dashboard },
                            onNavigateToPendingDrivers = { screenState.value = Screen.PendingDrivers },
                            onNavigateToSettlement = { screenState.value = Screen.Settlement } // 정산 관리로 이동
                        )
                        Screen.PendingDrivers -> {
                            // 현재 관리자의 Region ID와 Office ID 가져오기
                            val currentRegionId by dashboardViewModel.regionId.collectAsState()
                            val currentOfficeId by dashboardViewModel.officeId.collectAsState()

                            // ID가 유효한 경우에만 화면 표시 (로딩 중이거나 오류 시 다른 화면 표시 가능)
                            if (!currentRegionId.isNullOrBlank() && !currentOfficeId.isNullOrBlank()) {
                                PendingDriversScreen(
                                    // ★★★ ViewModel Factory 사용하여 ID 전달 ★★★
                                    viewModel = viewModel(
                                        // ★★★ Key 추가: ID가 변경되면 ViewModel 재생성 ★★★
                                        key = "${currentRegionId}_${currentOfficeId}", 
                                        factory = PendingDriversViewModel.Factory(
                                            application = application,
                                            regionId = currentRegionId!!, // Null 아님 보장
                                            officeId = currentOfficeId!!  // Null 아님 보장
                                        )
                                    ),
                                    onNavigateBack = { screenState.value = Screen.Settings } // 설정 화면으로 돌아가도록 수정
                                )
                            } else {
                                // ID 로드 중이거나 오류 발생 시 처리 (예: 로딩 인디케이터 또는 오류 메시지 표시)
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (currentRegionId == null || currentOfficeId == null) {
                                        Text("관리자 정보를 로드하는 중 오류가 발생했습니다.")
                                    } else {
                                        CircularProgressIndicator()
                                        Text("관리자 정보 로딩 중...") // 또는 로딩 스피너 등
                                    }
                                }
                            }
                        }
                        Screen.Settlement -> {
                            com.designated.callmanager.ui.settlement.SettlementScreen(
                                onNavigateBack = { screenState.value = Screen.Settings }
                            )
                        }
                    }
                }
            }
        }

        checkAndRequestPermissions() 
    }

    // Restore onNewIntent to its simpler form or remove if not originally present
    // Assuming a simple onNewIntent for handling calls when activity is reused:
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent) // Important to update the activity's intent

        val action = intent?.action
        val callId = intent?.getStringExtra(EXTRA_CALL_ID)
        if (action == ACTION_SHOW_CALL_DIALOG && callId != null) {
            if (auth.currentUser != null) {
                _pendingCallDialogId.value = callId
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
            Log.d(tag, "2단계: 화면 위에 그리기 권한이 필요합니다. 요청 시작.")
            val sharedPrefs = getSharedPreferences("call_manager_prefs", MODE_PRIVATE)
            val overlayPermissionRequestedBefore = sharedPrefs.getBoolean("overlay_permission_requested", false)

            if (!overlayPermissionRequestedBefore) {
                AlertDialog.Builder(this)
                    .setTitle("추가 권한 필요")
                    .setMessage("새로운 콜 정보를 화면 위에 바로 표시하려면 '다른 앱 위에 표시' 권한이 필요합니다. 설정 화면으로 이동하여 권한을 허용해주세요.")
                    .setPositiveButton("설정으로 이동") { _, _ ->
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
                        overlayPermissionLauncher.launch(intent)
                        // 사용자가 설정 화면으로 이동했으므로, 요청한 것으로 간주
                        sharedPrefs.edit { putBoolean("overlay_permission_requested", true) }
                    }
                    .setNegativeButton("나중에") { dialog, _ ->
                        isRequestingPermissions.set(false)
                        dialog.dismiss()
                        Toast.makeText(this, "'다른 앱 위에 표시' 권한 없이 일부 기능이 제한될 수 있습니다.", Toast.LENGTH_LONG).show()
                        // 거부했더라도 요청은 한 것으로 간주
                        sharedPrefs.edit { putBoolean("overlay_permission_requested", true) }
                        startCallManagerServiceIfNeeded() // 권한 없어도 서비스 시작 시도
                    }
                    .setOnDismissListener { 
                        if (isRequestingPermissions.get()) {
                             isRequestingPermissions.set(false)
                             sharedPrefs.edit { putBoolean("overlay_permission_requested", true) }
                             Log.d(tag, "Overlay 권한 요청 다이얼로그 Dismiss됨, 플래그 리셋 및 요청 기록")
                             startCallManagerServiceIfNeeded() // 권한 없어도 서비스 시작 시도
                        } 
                     }
                     .setCancelable(false)
                     .show()
                // 여기서 return, 결과는 overlayPermissionLauncher 콜백 등에서 처리 후 checkAndRequestPermissions 재호출 또는 startCallManagerServiceIfNeeded 호출
                return
            } else {
                // 이미 요청했지만 거부된 상태. 사용자에게 토스트 메시지로 알림.
                Toast.makeText(this, "'다른 앱 위에 표시' 권한이 필요합니다. 앱 설정에서 허용해주세요.", Toast.LENGTH_LONG).show()
                startCallManagerServiceIfNeeded() // 권한 없어도 서비스는 시작
                isRequestingPermissions.set(false) // 플래그 리셋
                return // 다음 단계로 진행하지 않음
            }
        }
        Log.d(tag, "2단계: 화면 위에 그리기 권한이 이미 승인되었습니다.")

        // --- 3단계: 모든 권한 확인 완료, 서비스 시작 --- 
        Log.d(tag, "3단계: 모든 필수 권한 확인 완료. 서비스 시작 로직 실행.")
        startCallManagerServiceIfNeeded()
        
        // 모든 확인/요청 절차 완료 후 플래그 최종 리셋
        isRequestingPermissions.set(false)
        Log.d(tag, "권한 확인 프로세스 완료, 플래그 리셋.")
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
            dashboardViewModel.startForegroundService(this)
        } else {
             Log.w(tag, "서비스 시작에 필요한 최소 권한(위치)이 부족하여 서비스를 시작하지 않습니다.")
             Toast.makeText(this, "위치 권한이 없어 콜 서비스를 시작할 수 없습니다.", Toast.LENGTH_LONG).show()
             // 권한 부족 시에도 서비스가 실행 중이면 중지 (선택적, ViewModel에서 처리 가능)
             dashboardViewModel.stopForegroundService(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 앱 종료 시 서비스 중지
        dashboardViewModel.stopForegroundService(this)
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

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
import android.net.Uri
import android.os.PowerManager
import android.content.BroadcastReceiver
import android.content.IntentFilter

enum class Screen {
    Login,
    SignUp,
    PasswordReset,
    Dashboard,
    Settings,
    PendingDrivers,
    Settlement,
    ExcludeNumber,
    ContactSelection
}

sealed class NavigationParams {
    object None : NavigationParams()
    data class DriverManagement(val regionId: String, val officeId: String) : NavigationParams()
}

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private val dashboardViewModel: DashboardViewModel by viewModels {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application)
    }

    private var regionId: String? = null
    private var officeId: String? = null
    private var managerId: String? = null

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

        screenState = if (auth.currentUser == null) Screen.Login else Screen.Dashboard

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
                            } else {
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
                            Screen.Settlement, Screen.PendingDrivers, Screen.ExcludeNumber -> {
                                screenState = Screen.Settings
                            }
                            Screen.ContactSelection -> {
                                screenState = Screen.ExcludeNumber
                            }
                            Screen.SignUp, Screen.PasswordReset -> {
                                screenState = Screen.Login
                            }
                        }
                    }

                    when (currentScreenState) {
                        Screen.Login -> LoginScreen(
                            onLoginComplete = { regionId, officeId ->
                                this@MainActivity.regionId = regionId
                                this@MainActivity.officeId = officeId
                                this@MainActivity.managerId = auth.currentUser?.uid

                                dashboardViewModel.loadDataForUser(regionId, officeId)
                                updateFcmTokenForAdmin(regionId, officeId)
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
                                onNavigateToExcludeNumber = {
                                    screenState = Screen.ExcludeNumber
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
            ACTION_SHOW_CALL_POPUP -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                if (callId != null) {
                    val popupId = "CALL_${callId}"
                    if (!isPopupAlreadyShown(popupId)) {
                        lifecycleScope.launch {
                            markPopupAsShown(popupId)
                            if (_screenState.value == Screen.Dashboard) {
                                dashboardViewModel.showCallDialog(callId)
                            } else {
                                _pendingCallDialogId.value = callId
                            }
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

                    lifecycleScope.launch {
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                            kotlinx.coroutines.delay(300)
                        }
                        dashboardViewModel.showSharedCallNotificationFromId(sharedCallId)
                    }
                } else {
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

            ACTION_SHOW_NEW_CALL_WAITING -> {
                val callId = intent.getStringExtra(EXTRA_CALL_ID)
                val customerPhone = intent.getStringExtra("customerPhone")
                if (callId != null) {
                    lifecycleScope.launch {
                        if (_screenState.value != Screen.Dashboard) {
                            _screenState.value = Screen.Dashboard
                        }
                        showToast("새로운 콜이 접수되었습니다: ${customerPhone ?: ""}")
                    }
                }
            }

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

        checkAndShowPendingPopup()
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(callDetectedReceiver)
        } catch (e: IllegalArgumentException) {
        }
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

        // 24시간 이상 된 CALL_ 팝업만 정리 (운행 관련은 유지)
        val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val keysToRemove = prefs.all.keys.filter { key ->
            key.startsWith("CALL_")
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
        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission && !CallManagerService.isServiceRunning) {
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
        } else if (!hasLocationPermission) {
             Toast.makeText(this, "위치 권한이 없어 콜 서비스를 시작할 수 없습니다.", Toast.LENGTH_LONG).show()
             val serviceIntent = Intent(this, CallManagerService::class.java)
             stopService(serviceIntent)
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun updateFcmTokenForAdmin(regionId: String, officeId: String) {
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
                            val regionId = document.getString("associatedRegionId")
                            val officeId = document.getString("associatedOfficeId")

                            if (regionId != null && officeId != null) {

                                this@MainActivity.regionId = regionId
                                this@MainActivity.officeId = officeId
                                this@MainActivity.managerId = adminId

                                // DashboardViewModel이 이미 init에서 리스너를 시작하므로 loadDataForUser 호출 불필요
                                // 콜 디텍터 설정만 동기화
                                dashboardViewModel.syncCallDetectorSettings(regionId, officeId)

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

package com.designated.driverapp

import android.Manifest
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.activity.viewModels
import com.designated.driverapp.viewmodel.DriverViewModel
import com.designated.driverapp.navigation.AppNavigation
import com.designated.driverapp.ui.theme.DriverAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.designated.driverapp.data.Constants
import com.designated.driverapp.worker.SettlementSyncWorker

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val driverViewModel: DriverViewModel by viewModels()

    private val TAG = "MainActivity"
    private lateinit var auth: FirebaseAuth

    // 알림 권한 관련 상태
    private var showNotificationPermissionDialog = mutableStateOf(false)
    private var showNotificationSettingsDialog = mutableStateOf(false)
    private var hasShownNotificationDialog = false  // 세션 중 다이얼로그 표시 여부

    // 배터리 최적화 관련 상태
    private var showBatteryOptimizationDialog = mutableStateOf(false)
    private var hasShownBatteryDialog = false  // 세션 중 배터리 다이얼로그 표시 여부

    // FCM LocalBroadcast 수신용 BroadcastReceiver
    private val fcmBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val callId = intent?.getStringExtra("callId")
            Log.d(TAG, "LocalBroadcast 수신: callId=$callId")
            if (!callId.isNullOrBlank() && auth.currentUser != null) {
                driverViewModel.setNotificationCallId(callId)
            }
        }
    }

    // 권한 요청 결과를 처리하는 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }.keys
        if (deniedPermissions.isNotEmpty()) {
            Log.w(TAG, "거부된 권한: $deniedPermissions")
            // 알림 권한이 거부된 경우 설정 화면 안내
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                deniedPermissions.contains(Manifest.permission.POST_NOTIFICATIONS)) {
                showNotificationSettingsDialog.value = true
            }
        } else {
            Log.d(TAG, "모든 권한이 허용되었습니다")
            updateFcmToken()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        // driverViewModel = ViewModelProvider(this, DriverViewModelFactory(application))[DriverViewModel::class.java] // Hilt @AndroidEntryPoint 와 by viewModels() 로 대체됨

        val currentUser = auth.currentUser

        // 권한 요청 로직 - 앱 시작 시 바로 시스템 권한 요청
        if (!areAllRequiredPermissionsGranted()) {
            requestAllPermissions()
            hasShownNotificationDialog = true
        }


        // 정산 동기화 WorkManager 초기화
        if (currentUser != null) {
            SettlementSyncWorker.enqueuePeriodicSync(this)
            SettlementSyncWorker.enqueueOnNetworkAvailable(this)

            // Pending FCM 토큰 재시도 (이전 저장 실패 시)
            MyFirebaseMessagingService.retryPendingFcmToken(this)
        }

        // 알림 클릭으로 앱이 시작된 경우 잠금화면 해제 및 알림 취소
        intent.getStringExtra("callId")?.let { callId ->
            handleLockScreenWakeUp()
            cancelNotification(callId)
        }

        setContent {
            DriverAppTheme {
                val navController = rememberNavController()

                val initialCallId = remember { mutableStateOf(intent.getStringExtra("callId")) }

                val startDest = if (currentUser != null) {
                    // SharedPreferences에서 로그인 정보 확인
                    val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val hasLoginInfo = !prefs.getString(Constants.PREF_KEY_PROVINCE_ID, "").isNullOrBlank() &&
                                      !prefs.getString(Constants.PREF_KEY_OFFICE_ID, "").isNullOrBlank()

                    if (hasLoginInfo) {
                        if (!initialCallId.value.isNullOrBlank()) {
                            "call_details/{callId}".replace("{callId}", initialCallId.value!!)
                        } else {
                            "home"
                        }
                    } else {
                        "login" // SharedPreferences 로그인 정보가 없으면 로그인 화면으로
                    }
                } else {
                    "login"
                }


                AppNavigation(
                    navController = navController,
                    driverViewModel = driverViewModel,
                    startDestination = startDest
                )

                // ✅ StateFlow로 callId를 관찰하여 팝업 표시
                val notificationCallId by driverViewModel.notificationCallId.collectAsState()

                LaunchedEffect(notificationCallId) {
                    if (!notificationCallId.isNullOrBlank() && auth.currentUser != null) {
                        Log.d(TAG, "LaunchedEffect: processing notificationCallId = $notificationCallId")
                        driverViewModel.handleNotificationCallId(notificationCallId!!)
                        driverViewModel.clearNotificationCallId()
                    }
                }

                // 최초 실행 시 intent에서 callId 확인
                LaunchedEffect(Unit) {
                    val initialCallId = intent.getStringExtra("callId")
                    if (!initialCallId.isNullOrBlank() && auth.currentUser != null) {
                        Log.d(TAG, "Initial callId from intent: $initialCallId")
                        driverViewModel.setNotificationCallId(initialCallId)
                    }
                }

                // 알림 권한 요청 전 안내 다이얼로그
                if (showNotificationPermissionDialog.value) {
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("알림 권한 필요") },
                        text = {
                            Column {
                                Text("콜 배정 알림을 받으려면 알림 권한이 필요합니다.")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("알림을 허용하지 않으면 새로운 콜 배정을 놓칠 수 있습니다.")
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showNotificationPermissionDialog.value = false
                                    requestAllPermissions()
                                }
                            ) {
                                Text("권한 허용하기")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showNotificationPermissionDialog.value = false
                                    // 알림 권한 없이 다른 권한만 요청
                                    val otherPermissions = arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                        Manifest.permission.RECORD_AUDIO
                                    )
                                    permissionLauncher.launch(otherPermissions)
                                }
                            ) {
                                Text("나중에")
                            }
                        }
                    )
                }

                // 알림 설정 비활성화 시 설정 화면 안내 다이얼로그
                if (showNotificationSettingsDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showNotificationSettingsDialog.value = false },
                        title = { Text("알림이 꺼져 있습니다") },
                        text = {
                            Column {
                                Text("콜 배정 알림을 받으려면 설정에서 알림을 켜주세요.")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("알림이 꺼져 있으면 새로운 콜 배정을 놓칠 수 있습니다.")
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showNotificationSettingsDialog.value = false
                                    // 앱 알림 설정 화면으로 이동
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                    }
                                    startActivity(intent)
                                }
                            ) {
                                Text("설정으로 이동")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showNotificationSettingsDialog.value = false
                                }
                            ) {
                                Text("나중에")
                            }
                        }
                    )
                }

                // 배터리 최적화 예외 요청 다이얼로그
                if (showBatteryOptimizationDialog.value) {
                    AlertDialog(
                        onDismissRequest = { showBatteryOptimizationDialog.value = false },
                        title = { Text("배터리 최적화 제외 필요") },
                        text = {
                            Column {
                                Text("화면이 꺼진 상태에서도 콜 배정 알림을 받으려면 배터리 최적화에서 제외해야 합니다.")
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("이 설정이 없으면 중요한 콜 배정을 놓칠 수 있습니다.")
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showBatteryOptimizationDialog.value = false
                                    requestBatteryOptimizationExemption()
                                }
                            ) {
                                Text("설정으로 이동")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showBatteryOptimizationDialog.value = false
                                    // SharedPreferences에 "나중에" 선택 기록
                                    getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                                        .edit()
                                        .putBoolean("battery_optimization_asked", true)
                                        .apply()
                                }
                            ) {
                                Text("나중에")
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // FCM LocalBroadcast 수신 등록
        LocalBroadcastManager.getInstance(this).registerReceiver(
            fcmBroadcastReceiver,
            IntentFilter(Constants.ACTION_SHOW_CALL_DIALOG)
        )
        Log.d(TAG, "LocalBroadcast 리시버 등록됨")

        // 알림 설정이 꺼져있는지 확인 (로그인 상태 + 이번 세션에서 아직 안 물어봤을 때만)
        if (auth.currentUser != null &&
            !hasShownNotificationDialog &&
            !NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            showNotificationSettingsDialog.value = true
            hasShownNotificationDialog = true
        }

        // 배터리 최적화 예외 체크 (로그인 상태 + 이번 세션에서 아직 안 물어봤을 때만)
        if (auth.currentUser != null && !hasShownBatteryDialog) {
            checkBatteryOptimization()
        }
    }

    override fun onPause() {
        super.onPause()
        // FCM LocalBroadcast 수신 해제
        LocalBroadcastManager.getInstance(this).unregisterReceiver(fcmBroadcastReceiver)
        Log.d(TAG, "LocalBroadcast 리시버 해제됨")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        // 알림 클릭으로 들어온 callId 처리 - StateFlow로 전달하여 Compose가 반응하도록 함
        val callId = intent.getStringExtra("callId")
        if (!callId.isNullOrBlank() && auth.currentUser != null) {
            Log.d(TAG, "onNewIntent: callId received = $callId")

            // 잠금화면에서 알림 클릭 시 화면 켜기 및 잠금 해제
            handleLockScreenWakeUp()

            driverViewModel.setNotificationCallId(callId)

            // 해당 알림 취소 (notificationId = callId.hashCode())
            cancelNotification(callId)
        }
    }

    private fun cancelNotification(callId: String) {
        val notificationManager = NotificationManagerCompat.from(this)
        val notificationId = callId.hashCode()
        notificationManager.cancel(notificationId)
        notificationManager.cancel(notificationId + 1)  // 헤드업 알림용
        Log.d(TAG, "알림 취소됨: notificationId=$notificationId")
    }

    private fun updateFcmToken() {
        // FCM 토큰 업데이트 (기존 코드가 있다면 여기에 구현)
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM 토큰 가져오기 실패", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d(TAG, "FCM 토큰: $token")
            // TODO: FCM 토큰을 서버에 업데이트하는 로직 구현 필요
        }
    }

    private fun areAllRequiredPermissionsGranted(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null,
            Manifest.permission.RECORD_AUDIO
        ).filterNotNull()

        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestAllPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS else null,
            Manifest.permission.RECORD_AUDIO
        ).filterNotNull().toTypedArray()

        permissionLauncher.launch(requiredPermissions)
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val hasAskedBefore = prefs.getBoolean("battery_optimization_asked", false)

            if (!powerManager.isIgnoringBatteryOptimizations(packageName) && !hasAskedBefore) {
                showBatteryOptimizationDialog.value = true
                hasShownBatteryDialog = true
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                // 직접 앱의 배터리 최적화 설정 화면 열기
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "배터리 최적화 설정 화면 열기 실패", e)
                try {
                    // 배터리 최적화 설정 목록 화면 열기
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "앱 목록에서 '기사앱'을 찾아 '허용'으로 설정해주세요", Toast.LENGTH_LONG).show()
                } catch (e2: Exception) {
                    Log.e(TAG, "배터리 설정 화면 열기 실패", e2)
                    Toast.makeText(this, "설정 > 앱 > 기사앱 > 배터리 에서 '제한 없음'을 선택해주세요", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * 잠금화면에서 알림 클릭 시 화면을 켜고 잠금 해제
     * Android 8.1(O_MR1) 이상과 이하에서 다르게 처리
     */
    private fun handleLockScreenWakeUp() {
        Log.d(TAG, "잠금화면 해제 처리 시작")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    Log.d(TAG, "잠금화면 해제 성공")
                }
                override fun onDismissError() {
                    Log.e(TAG, "잠금화면 해제 오류")
                }
                override fun onDismissCancelled() {
                    Log.d(TAG, "잠금화면 해제 취소됨")
                }
            })
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
    }

}
package com.designated.driverapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

        // 최소한의 권한 요청 로직 - 로그인 상태만 확인
        if (currentUser != null && !areAllRequiredPermissionsGranted()) {
            requestAllPermissions()
        }

        // 정산 동기화 WorkManager 초기화
        if (currentUser != null) {
            SettlementSyncWorker.enqueuePeriodicSync(this)
            SettlementSyncWorker.enqueueOnNetworkAvailable(this)
        }

        setContent {
            DriverAppTheme {
                val navController = rememberNavController()

                val initialCallId = remember { mutableStateOf(intent.getStringExtra("callId")) }

                val startDest = if (currentUser != null) {
                    // SharedPreferences에서 로그인 정보 확인
                    val prefs = getSharedPreferences("driver_app_prefs", Context.MODE_PRIVATE)
                    val hasLoginInfo = !prefs.getString("pref_region_id", "").isNullOrBlank() &&
                                      !prefs.getString("pref_office_id", "").isNullOrBlank()

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
            driverViewModel.setNotificationCallId(callId)
        }
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
}
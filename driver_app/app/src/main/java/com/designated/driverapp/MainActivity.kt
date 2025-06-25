package com.designated.driverapp

import android.Manifest
// import android.app.Application // ViewModelProvider.Factory 제거로 불필요
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
// import androidx.lifecycle.ViewModelProvider // Hilt로 대체되므로 제거
import androidx.activity.viewModels // by viewModels() 사용 위해 추가
import com.designated.driverapp.viewmodel.DriverViewModel
import com.designated.driverapp.navigation.AppNavigation
import com.designated.driverapp.ui.theme.DriverAppTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import android.content.Context
import android.content.Intent
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val driverViewModel: DriverViewModel by viewModels() // Hilt ViewModel 주입

    private val TAG = "MainActivity"
    private lateinit var auth: FirebaseAuth
    // private lateinit var driverViewModel: DriverViewModel // Hilt 주입으로 변경

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Notification permission GRANTED")
            } else {
                Log.d(TAG, "Notification permission DENIED")
                Toast.makeText(this, "백그라운드 상태 알림을 받으려면 알림 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Log.d(TAG, "Location permission GRANTED")
            } else {
                Log.d(TAG, "Location permission DENIED")
                Toast.makeText(this, "현재 위치를 사용하려면 위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity starting.")
        auth = Firebase.auth
        
        // driverViewModel = ViewModelProvider(this, DriverViewModelFactory(application))[DriverViewModel::class.java] // Hilt @AndroidEntryPoint 와 by viewModels() 로 대체됨
        // ViewModel은 Hilt에 의해 자동으로 주입됩니다. 아래와 같이 선언부에서 초기화합니다.
        // private val driverViewModel: DriverViewModel by viewModels() // 클래스 멤버로 이동 및 초기화 방식 변경
        
        val currentUser = auth.currentUser
        Log.d(TAG, "현재 로그인된 사용자: ${currentUser?.email ?: "없음"}")
        
        Log.d(TAG, "onCreate: Calling permission checks.")
        askNotificationPermission()
        askLocationPermission()
        
        // // FCM 토큰 가져와서 서버에 등록 -> 로그인 성공 시점으로 로직 이동
        // getAndRegisterFcmToken()

        setContent {
            DriverAppTheme {
                // NavController 생성
                val navController = rememberNavController()

                // 초기 callId 처리
                val initialCallId = remember { mutableStateOf(intent.getStringExtra("callId")) }

                AppNavigation(
                    navController = navController, // NavController 전달
                    driverViewModel = driverViewModel, // driverViewModel 전달
                    startDestination = if (currentUser != null) {
                        // 로그인 상태일 때만 callId를 확인하여 상세 화면으로 이동
                        if (!initialCallId.value.isNullOrBlank()) {
                            "call_details/{callId}".replace("{callId}", initialCallId.value!!)
                        } else {
                            "history_settlement" // 기본 홈 화면
                        }
                    } else {
                        "login" // 로그아웃 상태면 무조건 로그인 화면으로
                    }
                )

                // onNewIntent에서 전달받은 callId 처리
                LaunchedEffect(intent) {
                    val newCallId = intent.getStringExtra("callId")
                    if (!newCallId.isNullOrBlank() && currentUser != null) {
                        Log.d(TAG, "onNewIntent: Navigating to call_details with callId: $newCallId")
                        navController.navigate("call_details/$newCallId")
                        // Intent 처리 후 callId를 null로 만들어 중복 실행 방지
                        intent.removeExtra("callId")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // setIntent를 호출하여 Activity의 intent를 최신으로 업데이트
        setIntent(intent)
        Log.d(TAG, "onNewIntent received and updated intent")
    }
    
    // private fun getAndRegisterFcmToken() { ... } // 이 함수 전체 또는 호출부 주석/삭제
    // 여기서는 함수 자체를 남겨두고 호출만 막아두겠습니다. (다른 용도로 쓸 수 있으므로)

    private fun askNotificationPermission() {
        Log.d(TAG, "askNotificationPermission: Checking Android version.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                    Log.d(TAG, "알림 권한 이미 허용됨")
                    Log.d(TAG, "askNotificationPermission: Permission already granted.")
                }
                shouldShowRequestPermissionRationale(permission) -> {
                    Log.d(TAG, "알림 권한 명시적 거부 이력 있음. 필요성 설명 필요.")
                    Log.d(TAG, "askNotificationPermission: Rationale should be shown. Launching permission request.")
                    Toast.makeText(this, "백그라운드 알림을 위해 권한이 필요합니다. 다시 요청합니다.", Toast.LENGTH_SHORT).show()
                    requestPermissionLauncher.launch(permission)
                }
                else -> {
                    Log.d(TAG, "알림 권한 요청")
                    Log.d(TAG, "askNotificationPermission: Requesting permission.")
                    requestPermissionLauncher.launch(permission)
                }
            }
        }
    }

    private fun askLocationPermission() {
        Log.d(TAG, "askLocationPermission: Checking location permission.")
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                Log.d(TAG, "Location permission already granted.")
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Log.d(TAG, "Location permission rationale should be shown. Launching permission request.")
                Toast.makeText(this, "출발지 자동 입력을 위해 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                requestLocationPermissionLauncher.launch(permission)
            }
            else -> {
                Log.d(TAG, "Requesting location permission.")
                requestLocationPermissionLauncher.launch(permission)
            }
        }
    }
}
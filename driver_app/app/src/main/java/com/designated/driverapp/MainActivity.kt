package com.designated.driverapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import Log
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
import android.content.Context
import android.content.Intent
import androidx.navigation.compose.rememberNavController
import com.google.firebase.messaging.FirebaseMessaging

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val driverViewModel: DriverViewModel by viewModels()

    private val TAG = "MainActivity"
    private lateinit var auth: FirebaseAuth

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
            } else {
                Toast.makeText(this, "백그라운드 상태 알림을 받으려면 알림 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    private val requestLocationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
            } else {
                Toast.makeText(this, "현재 위치를 사용하려면 위치 권한이 필요합니다.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth

        // driverViewModel = ViewModelProvider(this, DriverViewModelFactory(application))[DriverViewModel::class.java] // Hilt @AndroidEntryPoint 와 by viewModels() 로 대체됨

        val currentUser = auth.currentUser

        askNotificationPermission()
        askLocationPermission()

        setContent {
            DriverAppTheme {
                val navController = rememberNavController()

                val initialCallId = remember { mutableStateOf(intent.getStringExtra("callId")) }

                AppNavigation(
                    navController = navController,
                    driverViewModel = driverViewModel,
                    startDestination = if (currentUser != null) {
                        if (!initialCallId.value.isNullOrBlank()) {
                            "call_details/{callId}".replace("{callId}", initialCallId.value!!)
                        } else {
                            "history_settlement"
                        }
                    } else {
                        "login"
                    }
                )

                LaunchedEffect(intent) {
                    val newCallId = intent.getStringExtra("callId")
                    if (!newCallId.isNullOrBlank() && currentUser != null) {
                        navController.navigate("call_details/$newCallId")
                        intent.removeExtra("callId")
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            when {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                }
                shouldShowRequestPermissionRationale(permission) -> {
                    Toast.makeText(this, "백그라운드 알림을 위해 권한이 필요합니다. 다시 요청합니다.", Toast.LENGTH_SHORT).show()
                    requestPermissionLauncher.launch(permission)
                }
                else -> {
                    requestPermissionLauncher.launch(permission)
                }
            }
        }
    }

    private fun askLocationPermission() {
        val permission = Manifest.permission.ACCESS_FINE_LOCATION
        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
            }
            shouldShowRequestPermissionRationale(permission) -> {
                Toast.makeText(this, "출발지 자동 입력을 위해 위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                requestLocationPermissionLauncher.launch(permission)
            }
            else -> {
                requestLocationPermissionLauncher.launch(permission)
            }
        }
    }
}
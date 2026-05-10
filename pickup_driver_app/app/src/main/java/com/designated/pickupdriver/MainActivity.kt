package com.designated.pickupdriver

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.designated.pickupdriver.data.Constants
import com.designated.pickupdriver.ui.chat.DashboardWithChatSheet
import com.designated.pickupdriver.ui.login.LoginScreen
import com.designated.pickupdriver.ui.login.SignUpScreen
import com.designated.pickupdriver.ui.theme.PickupDriverAppTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val DASHBOARD = "dashboard"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var auth: FirebaseAuth
    @Inject lateinit var prefs: SharedPreferences

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 거부해도 앱은 정상 동작 — 알림만 안 옴 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()

        val start = if (auth.currentUser != null &&
            !prefs.getString(Constants.PREF_KEY_OFFICE_ID, null).isNullOrBlank()) {
            Routes.DASHBOARD
        } else {
            Routes.LOGIN
        }

        setContent {
            PickupDriverAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Scaffold { innerPadding ->
                        val navController = rememberNavController()
                        NavHost(
                            navController = navController,
                            startDestination = start,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            composable(Routes.LOGIN) {
                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate(Routes.DASHBOARD) {
                                            popUpTo(Routes.LOGIN) { inclusive = true }
                                        }
                                    },
                                    onNavigateToSignUp = {
                                        navController.navigate(Routes.SIGNUP)
                                    }
                                )
                            }
                            composable(Routes.SIGNUP) {
                                SignUpScreen(
                                    onSignUpSuccess = {
                                        navController.popBackStack(Routes.LOGIN, inclusive = false)
                                    },
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                            composable(Routes.DASHBOARD) {
                                DashboardWithChatSheet(
                                    onLogout = {
                                        navController.navigate(Routes.LOGIN) {
                                            popUpTo(Routes.DASHBOARD) { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

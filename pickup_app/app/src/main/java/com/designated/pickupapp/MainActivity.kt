package com.designated.pickupapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.provider.Settings
import android.text.TextUtils
import android.content.ComponentName
import com.designated.pickupapp.ui.home.HomeScreen
import com.designated.pickupapp.data.Constants
import com.designated.pickupapp.ui.home.HomeViewModel
import com.designated.pickupapp.ui.login.LoginScreen
import com.designated.pickupapp.ui.signup.SignUpScreen
import com.designated.pickupapp.ui.theme.PickupAppTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

/**
 * 픽업앱 메인 액티비티 (Android 15 호환)
 * - 네비게이션 관리
 * - 권한 처리
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var homeViewModel: HomeViewModel? = null
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        Log.e("MainActivity", "🚨🚨🚨 픽업앱 MainActivity 시작 🚨🚨🚨")
        Log.e("MainActivity", "Android 버전: ${android.os.Build.VERSION.SDK_INT}")
        
        
        setContent {
            PickupAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // SharedPreferences를 통한 로그인 상태 확인 (Firebase Auth 상태와 무관하게)
                    val sharedPrefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    val isAutoLoginEnabled = sharedPrefs.getBoolean(Constants.PREF_AUTO_LOGIN, false)
                    val savedRegionId = sharedPrefs.getString(Constants.PREF_KEY_REGION_ID, null)
                    val savedOfficeId = sharedPrefs.getString(Constants.PREF_KEY_OFFICE_ID, null)
                    val savedEmail = sharedPrefs.getString(Constants.PREF_EMAIL, null)
                    val savedDriverId = sharedPrefs.getString(Constants.PREF_KEY_DRIVER_ID, null)
                    
                    // 자동 로그인이 활성화되어 있고 필요한 정보가 모두 있으면 홈 화면으로
                    val startDestination = if (isAutoLoginEnabled && 
                                           !savedRegionId.isNullOrBlank() && 
                                           !savedOfficeId.isNullOrBlank() && 
                                           !savedDriverId.isNullOrBlank() &&
                                           savedRegionId.length > 2 &&
                                           savedOfficeId.length > 2 &&
                                           savedDriverId.length > 10) { // UID는 최소 10자 이상
                        Log.e("MainActivity", "✅ 자동 로그인 활성화 - 홈 화면으로 이동")
                        Log.e("MainActivity", "  - Region: $savedRegionId")
                        Log.e("MainActivity", "  - Office: $savedOfficeId") 
                        Log.e("MainActivity", "  - Driver: $savedDriverId")
                        "home/$savedRegionId/$savedOfficeId/$savedDriverId"
                    } else {
                        Log.e("MainActivity", "❌ 로그인 화면으로 이동")
                        Log.e("MainActivity", "  - AutoLogin: $isAutoLoginEnabled")
                        Log.e("MainActivity", "  - Region: $savedRegionId (len: ${savedRegionId?.length ?: 0})")
                        Log.e("MainActivity", "  - Office: $savedOfficeId (len: ${savedOfficeId?.length ?: 0})")
                        Log.e("MainActivity", "  - Driver: $savedDriverId (len: ${savedDriverId?.length ?: 0})")
                        "login"
                    }
                    
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        composable("login") {
                            LoginScreen(
                                onNavigateToSignUp = {
                                    navController.navigate("signup")
                                },
                                onLoginSuccess = { regionId: String, officeId: String, driverId: String ->
                                    navController.navigate("home/$regionId/$officeId/$driverId") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                },
                                onNavigateToPasswordReset = {
                                    // 비밀번호 재설정 기능 (향후 구현)
                                }
                            )
                        }
                        
                        composable("signup") {
                            SignUpScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onSignUpSuccess = {
                                    // 회원가입 성공 시 로그인 화면으로 이동
                                    navController.navigate("login") {
                                        popUpTo("signup") { inclusive = true }
                                    }
                                }
                            )
                        }
                        
                        composable("home/{regionId}/{officeId}/{driverId}") { backStackEntry ->
                            val regionId = backStackEntry.arguments?.getString("regionId") ?: ""
                            val officeId = backStackEntry.arguments?.getString("officeId") ?: ""
                            val driverId = backStackEntry.arguments?.getString("driverId") ?: ""
                            
                            // 파라미터 유효성 검사
                            if (regionId.isBlank() || officeId.isBlank() || driverId.isBlank()) {
                                Log.e("MainActivity", "❌ 잘못된 네비게이션 파라미터 감지")
                                Log.e("MainActivity", "regionId='$regionId', officeId='$officeId', driverId='$driverId'")
                                // 로그인 화면으로 리다이렉트
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                                return@composable
                            }
                            
                            HomeScreen(
                                regionId = regionId,
                                officeId = officeId,
                                driverId = driverId,
                                onLogout = {
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                            )
                        }
                        
                    }
                }
            }
        }
    }
    
    /**
     * HomeViewModel 설정 (HomeScreen에서 호출)
     */
    fun setHomeViewModel(viewModel: HomeViewModel) {
        this.homeViewModel = viewModel
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
    }
    
    
}
package com.designated.driverapp

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.designated.driverapp.navigation.AppNavigation // 경로 수정
import com.designated.driverapp.ui.theme.DriverAppTheme // 경로 수정
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class MainActivity : ComponentActivity() {
    private val TAG = "MainActivity"
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = Firebase.auth
        
        // 현재 인증 상태 로깅
        val currentUser = auth.currentUser
        Log.d(TAG, "현재 로그인된 사용자: ${currentUser?.email ?: "없음"}")
        
        setContent {
            DriverAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(startDestination = if (currentUser != null) {
                        Log.d(TAG, "인증된 사용자가 있어 홈 화면으로 이동합니다: ${currentUser.email}")
                        "home"
                    } else {
                        Log.d(TAG, "인증된 사용자가 없어 로그인 화면으로 이동합니다")
                        "login"
                    })
                }
            }
        }
    }
} 
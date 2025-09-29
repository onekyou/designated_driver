package com.designated.customer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.designated.customer.ui.auth.PhoneAuthScreen
import com.designated.customer.ui.main.MainScreen
import com.designated.customer.ui.office.OfficeSelectionScreen
import com.designated.customer.ui.theme.DesignatedCustomerTheme
import com.designated.customer.util.PreferencesManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 딥링크 처리 (랜딩페이지에서 온 경우)
        handleDeepLink(intent)

        enableEdgeToEdge()
        setContent {
            DesignatedCustomerTheme {
                CustomerApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val data: Uri? = intent.data
        if (data != null) {
            // https://designated-driver.app/office/seoul/office123 형태의 URL 파싱
            val pathSegments = data.pathSegments
            if (pathSegments.size >= 3 && pathSegments[0] == "office") {
                val regionId = pathSegments[1]
                val officeId = pathSegments[2]

                // SharedPreferences에 저장
                val prefs = PreferencesManager(this)
                prefs.saveOfficeInfo(officeId, regionId)
            }
        }
    }
}

@Composable
fun CustomerApp() {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }

    // SharedPreferences에서 저장된 정보 읽기
    var currentOfficeId by remember { mutableStateOf(preferencesManager.getOfficeId()) }
    var currentRegionId by remember { mutableStateOf(preferencesManager.getRegionId()) }
    var currentPhoneNumber by remember { mutableStateOf(preferencesManager.getPhoneNumber()) }
    var isPhoneVerified by remember { mutableStateOf(preferencesManager.isPhoneVerified()) }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        when {
            // 1. 전화번호 인증이 안 되어 있으면 인증부터
            !isPhoneVerified -> {
                PhoneAuthScreen(
                    modifier = Modifier.padding(paddingValues),
                    onAuthSuccess = { phoneNumber ->
                        currentPhoneNumber = phoneNumber
                        isPhoneVerified = true
                        preferencesManager.savePhoneNumber(phoneNumber)
                    }
                )
            }
            // 2. 사무실 정보가 없으면 사무실 선택
            currentOfficeId == null || currentRegionId == null -> {
                OfficeSelectionScreen(
                    modifier = Modifier.padding(paddingValues),
                    onOfficeSelected = { officeId, regionId ->
                        currentOfficeId = officeId
                        currentRegionId = regionId
                        preferencesManager.saveOfficeInfo(officeId, regionId)
                    }
                )
            }
            // 3. 모든 정보가 있으면 메인 화면
            else -> {
                MainScreen(
                    modifier = Modifier.padding(paddingValues),
                    regionId = currentRegionId!!,
                    officeId = currentOfficeId!!,
                    phoneNumber = currentPhoneNumber!!,
                    onLogout = {
                        // 로그아웃 처리
                        preferencesManager.clearUserData()
                        isPhoneVerified = false
                        currentPhoneNumber = null
                    }
                )
            }
        }
    }
}
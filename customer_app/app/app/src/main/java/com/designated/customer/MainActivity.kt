package com.designated.customer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.designated.customer.ui.auth.PhoneAuthScreen
import com.designated.customer.ui.main.MainScreen
import com.designated.customer.ui.theme.DesignatedCustomerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DesignatedCustomerTheme {
                CustomerApp()
            }
        }
    }
}

@Composable
fun CustomerApp() {
    var currentOfficeId by remember { mutableStateOf<String?>(null) }
    var currentPhoneNumber by remember { mutableStateOf<String?>(null) }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        when {
            currentOfficeId == null || currentPhoneNumber == null -> {
                // 인증이 필요한 상태
                PhoneAuthScreen(
                    modifier = Modifier.padding(paddingValues),
                    onAuthSuccess = { officeId ->
                        currentOfficeId = officeId
                        // 전화번호는 인증 후 별도로 저장해야 함
                        currentPhoneNumber = "temp_phone" // 임시값
                    }
                )
            }
            else -> {
                // 인증 완료 후 메인 화면
                MainScreen(
                    modifier = Modifier.padding(paddingValues),
                    officeId = currentOfficeId!!,
                    phoneNumber = currentPhoneNumber!!
                )
            }
        }
    }
}
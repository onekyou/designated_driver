package com.designated.customer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.designated.customer.BuildConfig
import com.designated.customer.ui.auth.PhoneAuthScreen
import com.designated.customer.ui.navigation.MainNavigation
import com.designated.customer.ui.office.OfficeSelectionScreen
import com.designated.customer.ui.theme.DesignatedCustomerTheme
import com.designated.customer.util.PreferencesManager
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("MainActivity", "========== onCreate 시작 ==========")

        // 딥링크 처리 (랜딩페이지에서 온 경우)
        handleDeepLink(intent)

        android.util.Log.d("MainActivity", "enableEdgeToEdge 호출 전")

        enableEdgeToEdge()

        android.util.Log.d("MainActivity", "setContent 호출 전")

        setContent {
            android.util.Log.d("MainActivity", "setContent 람다 내부 - CustomerApp 호출 직전")
            DesignatedCustomerTheme {
                android.util.Log.d("MainActivity", "DesignatedCustomerTheme 내부 - CustomerApp 호출")
                CustomerApp()
            }
        }

        android.util.Log.d("MainActivity", "========== onCreate 종료 ==========")
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        android.util.Log.d("MainActivity", "========== handleDeepLink 시작 ==========")
        android.util.Log.d("MainActivity", "Intent Action: ${intent.action}")
        android.util.Log.d("MainActivity", "Intent Data: ${intent.data}")
        android.util.Log.d("MainActivity", "Intent Categories: ${intent.categories}")

        val data: Uri? = intent.data
        val prefs = PreferencesManager(this)

        android.util.Log.d("MainActivity", "기존 저장된 OfficeId: ${prefs.getOfficeId()}")
        android.util.Log.d("MainActivity", "기존 저장된 RegionId: ${prefs.getRegionId()}")

        if (data != null) {
            android.util.Log.d("MainActivity", "DeepLink URI: $data")
            android.util.Log.d("MainActivity", "DeepLink Scheme: ${data.scheme}")
            android.util.Log.d("MainActivity", "DeepLink Host: ${data.host}")
            android.util.Log.d("MainActivity", "DeepLink Path: ${data.path}")
            android.util.Log.d("MainActivity", "DeepLink Query: ${data.query}")

            // Query 파라미터에서 정보 추출
            val regionId = data.getQueryParameter("r")
            val officeId = data.getQueryParameter("o")
            val officePhone = data.getQueryParameter("phone")
            val bankName = data.getQueryParameter("bank")
            val accountNumber = data.getQueryParameter("account")
            val accountHolder = data.getQueryParameter("holder")

            android.util.Log.d("MainActivity", "추출된 파라미터:")
            android.util.Log.d("MainActivity", "  - regionId: $regionId")
            android.util.Log.d("MainActivity", "  - officeId: $officeId")
            android.util.Log.d("MainActivity", "  - phone: $officePhone")
            android.util.Log.d("MainActivity", "  - bank: $bankName")
            android.util.Log.d("MainActivity", "  - account: $accountNumber")
            android.util.Log.d("MainActivity", "  - holder: $accountHolder")

            if (regionId != null && officeId != null) {
                prefs.saveOfficeInfo(officeId, regionId)
                android.util.Log.d("MainActivity", "✅ DeepLink - 사무실 정보 저장 완료: regionId=$regionId, officeId=$officeId")
            } else {
                android.util.Log.e("MainActivity", "❌ DeepLink - regionId 또는 officeId가 null!")
            }

            if (officePhone != null && bankName != null && accountNumber != null && accountHolder != null) {
                prefs.saveOfficeContactInfo(officePhone, bankName, accountNumber, accountHolder)
                android.util.Log.d("MainActivity", "✅ DeepLink - 연락처 정보 저장 완료: phone=$officePhone, bank=$bankName, account=$accountNumber, holder=$accountHolder")
            } else {
                android.util.Log.e("MainActivity", "❌ DeepLink - 연락처 정보 중 일부가 null!")
            }
        } else {
            android.util.Log.e("MainActivity", "❌ DeepLink URI가 null입니다!")
        }

        android.util.Log.d("MainActivity", "========== handleDeepLink 종료 ==========")
    }
}

@Composable
fun CustomerApp() {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()

    android.util.Log.d("CustomerApp", "========== CustomerApp Composable 시작 ==========")

    // SharedPreferences에서 저장된 정보 읽기
    var currentOfficeId by remember {
        mutableStateOf(preferencesManager.getOfficeId()).also {
            android.util.Log.d("CustomerApp", "초기 OfficeId: ${it.value}")
        }
    }
    var currentRegionId by remember {
        mutableStateOf(preferencesManager.getRegionId()).also {
            android.util.Log.d("CustomerApp", "초기 RegionId: ${it.value}")
        }
    }
    var currentPhoneNumber by remember {
        mutableStateOf(preferencesManager.getPhoneNumber()).also {
            android.util.Log.d("CustomerApp", "초기 PhoneNumber: ${it.value}")
        }
    }
    var isPhoneVerified by remember {
        mutableStateOf(preferencesManager.isPhoneVerified()).also {
            android.util.Log.d("CustomerApp", "초기 PhoneVerified: ${it.value}")
        }
    }

    // CustomerInfo 상태 (사무실 연락처 포함)
    var customerInfo by remember { mutableStateOf<com.designated.customer.data.model.CustomerInfo?>(null) }

    // Attribution 매칭 상태
    var isMatchingAttribution by remember { mutableStateOf(false) }
    var hasTriedMatching by remember { mutableStateOf(false) }

    android.util.Log.d("CustomerApp", "LaunchedEffect 진입 전 - OfficeId: $currentOfficeId, RegionId: $currentRegionId")

    // 앱 최초 실행 시 Attribution 매칭 시도
    LaunchedEffect(Unit) {
        android.util.Log.d("CustomerApp", "LaunchedEffect(Unit) 시작")
        android.util.Log.d("CustomerApp", "현재 OfficeId: $currentOfficeId, RegionId: $currentRegionId")

        if (currentOfficeId == null || currentRegionId == null) {
            android.util.Log.d("CustomerApp", "사무실 정보 없음 - Attribution 매칭 시도")

            if (!hasTriedMatching) {
                android.util.Log.d("CustomerApp", "아직 매칭 시도 안 함 - 매칭 시작")
                hasTriedMatching = true
                isMatchingAttribution = true
                android.util.Log.d("MainActivity", "========== Attribution 매칭 시작 ==========")

                try {
                    val attributionService = com.designated.customer.service.AttributionMatchingService(context)
                    val result = attributionService.matchAttribution()

                    when (result) {
                        is com.designated.customer.service.AttributionMatchingService.MatchResult.Success -> {
                            android.util.Log.d("MainActivity", "✅ Attribution 매칭 성공: ${result.regionId}/${result.officeId}")

                            // 사무실 정보 저장
                            currentRegionId = result.regionId
                            currentOfficeId = result.officeId
                            preferencesManager.saveOfficeInfo(result.officeId, result.regionId)

                            // 사무실 연락처 정보 저장
                            if (result.officePhone != null && result.bankName != null &&
                                result.accountNumber != null && result.accountHolder != null) {
                                preferencesManager.saveOfficeContactInfo(
                                    result.officePhone,
                                    result.bankName,
                                    result.accountNumber,
                                    result.accountHolder
                                )
                            }
                        }
                        is com.designated.customer.service.AttributionMatchingService.MatchResult.NoMatch -> {
                            android.util.Log.w("MainActivity", "⚠️ Attribution 매칭 실패: ${result.message}")
                        }
                        is com.designated.customer.service.AttributionMatchingService.MatchResult.Error -> {
                            android.util.Log.e("MainActivity", "❌ Attribution 매칭 오류: ${result.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Attribution 매칭 중 예외 발생", e)
                } finally {
                    isMatchingAttribution = false
                    android.util.Log.d("MainActivity", "========== Attribution 매칭 종료 ==========")
                }
            } else {
                android.util.Log.d("CustomerApp", "이미 매칭 시도했음 - 건너뜀")
            }
        } else {
            android.util.Log.d("CustomerApp", "사무실 정보 있음 - Attribution 매칭 건너뜀 (OfficeId: $currentOfficeId, RegionId: $currentRegionId)")
        }
    }

    // 로그인 후 CustomerInfo 로드
    LaunchedEffect(isPhoneVerified, currentPhoneNumber, currentRegionId, currentOfficeId) {
        if (isPhoneVerified && currentPhoneNumber != null && currentRegionId != null && currentOfficeId != null) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val doc = firestore
                    .collection("regions").document(currentRegionId!!)
                    .collection("offices").document(currentOfficeId!!)
                    .collection("customers").document(currentPhoneNumber!!)
                    .get()
                    .await()

                if (doc.exists()) {
                    customerInfo = com.designated.customer.data.model.CustomerInfo.fromMap(doc.data ?: emptyMap())
                    android.util.Log.d("MainActivity", "CustomerInfo 로드 성공: ${customerInfo?.officePhone}")
                } else {
                    android.util.Log.w("MainActivity", "CustomerInfo 문서가 존재하지 않음")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "CustomerInfo 로드 실패", e)
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        when {
            // 0. Attribution 매칭 중이면 로딩 화면
            isMatchingAttribution -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        androidx.compose.material3.CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Text(
                            text = "사무실 정보를 확인하는 중...",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
            // 1. 전화번호 인증이 안 되어 있으면 인증부터
            !isPhoneVerified -> {
                PhoneAuthScreen(
                    modifier = Modifier.padding(paddingValues),
                    onAuthSuccess = { phoneNumber ->
                        // 전화번호 저장
                        currentPhoneNumber = phoneNumber
                        isPhoneVerified = true
                        preferencesManager.savePhoneNumber(phoneNumber)

                        // CustomerInfo 생성 및 Firebase 저장
                        coroutineScope.launch {
                            try {
                                val regionId = currentRegionId
                                val officeId = currentOfficeId
                                val officePhone = preferencesManager.getOfficePhone()
                                val bankName = preferencesManager.getBankName()
                                val accountNumber = preferencesManager.getAccountNumber()
                                val accountHolder = preferencesManager.getAccountHolder()

                                if (regionId != null && officeId != null &&
                                    officePhone != null && bankName != null &&
                                    accountNumber != null && accountHolder != null) {

                                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

                                    // CustomerInfo 생성
                                    val newCustomerInfo = com.designated.customer.data.model.CustomerInfo(
                                        id = phoneNumber,
                                        phoneNumber = phoneNumber,
                                        name = "",
                                        grade = "bronze",
                                        points = 0,
                                        totalRides = 0,
                                        totalSpent = 0L,
                                        linkedOfficeId = officeId,
                                        primaryOfficeId = officeId,
                                        attributionScore = null,
                                        attributionSource = "qr_code",
                                        registeredAt = com.google.firebase.Timestamp.now(),
                                        lastRideAt = null,
                                        officePhone = officePhone,
                                        bankName = bankName,
                                        accountNumber = accountNumber,
                                        accountHolder = accountHolder
                                    )

                                    // Firebase에 저장
                                    firestore
                                        .collection("regions").document(regionId)
                                        .collection("offices").document(officeId)
                                        .collection("customers").document(phoneNumber)
                                        .set(newCustomerInfo.toMap())
                                        .await()

                                    android.util.Log.d("MainActivity", "회원가입 - CustomerInfo 생성 및 저장 완료")

                                    // 메모리에도 저장
                                    customerInfo = newCustomerInfo
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "CustomerInfo 생성 실패", e)
                            }
                        }
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
            // 3. 모든 정보가 있으면 메인 네비게이션
            else -> {
                MainNavigation(
                    regionId = currentRegionId!!,
                    officeId = currentOfficeId!!,
                    phoneNumber = currentPhoneNumber!!,
                    customerInfo = customerInfo, // CustomerInfo 전달
                    onLogout = {
                        // 로그아웃 처리
                        preferencesManager.clearUserData()
                        isPhoneVerified = false
                        currentPhoneNumber = null
                        customerInfo = null
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
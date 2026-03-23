package com.designated.customer

import android.content.Intent
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
import com.designated.customer.ui.auth.PhoneAuthScreen
import com.designated.customer.ui.auth.TermsAgreementScreen
import com.designated.customer.ui.auth.DocumentViewerScreen
import com.designated.customer.ui.auth.LegalDocuments
import com.designated.customer.ui.profile.ProfileSetupScreen
import com.designated.customer.ui.navigation.MainNavigation
import com.designated.customer.ui.theme.DesignatedCustomerTheme
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.size
import com.designated.customer.util.PreferencesManager
import kotlinx.coroutines.tasks.await
import com.google.firebase.messaging.FirebaseMessaging
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
class MainActivity : ComponentActivity() {

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        // Install Referrer 확인 (Play Store 설치 시 사무실 정보 자동 매칭)
        checkInstallReferrer()

        // 알림 권한 요청 (Android 13+)
        requestNotificationPermission()

        // 만보기 권한 요청 (Android 10+)
        requestActivityRecognitionPermission()

        // FCM 토큰 요청 및 저장
        requestAndSaveFcmToken()

        // StepCounterService 시작
        startStepCounterService()

        setContent {
            DesignatedCustomerTheme {
                CustomerApp(
                    initialIntent = intent
                )
            }
        }

        // ✅ 알림 클릭으로 앱이 시작된 경우 Intent 처리 (setContent 이후에 호출)
        handleNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 알림 클릭으로 앱이 이미 실행 중인 경우
        setIntent(intent)

        // ✅ 알림 클릭 시 Intent extras 확인 및 브로드캐스트 전송
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent) {
        // 포인트 적립 알림 처리
        if (intent.getBooleanExtra("showPointsDialog", false)) {
            val fare = intent.getIntExtra("fare", 0)
            val pointsUsed = intent.getIntExtra("pointsUsed", 0)

            android.util.Log.d("MainActivity", "알림 클릭: 포인트 다이얼로그 표시 - fare=$fare, pointsUsed=$pointsUsed")

            // HOME 탭으로 이동 브로드캐스트 먼저 전송
            val navigateHomeIntent = Intent("com.designated.customer.NAVIGATE_TO_HOME")
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(navigateHomeIntent)

            // LocalBroadcast 전송 (MainViewModel의 리시버가 받아서 처리)
            val broadcastIntent = Intent("com.designated.customer.RIDE_COMPLETED").apply {
                putExtra("fare", fare)
                putExtra("pointsUsed", pointsUsed)
            }
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(broadcastIntent)
        }

        // 기사 배정 알림 처리
        if (intent.getBooleanExtra("showDriverAssigned", false)) {
            val callId = intent.getStringExtra("callId")
            val driverName = intent.getStringExtra("driverName") ?: ""
            val driverPhone = intent.getStringExtra("driverPhone") ?: ""
            val vehicleNumber = intent.getStringExtra("vehicleNumber") ?: ""
            val driverId = intent.getStringExtra("driverId") ?: ""

            android.util.Log.d("MainActivity", "알림 클릭: 기사 배정 정보 표시 - callId=$callId, driverName=$driverName")

            // HOME 탭으로 이동 브로드캐스트 먼저 전송
            val navigateHomeIntent = Intent("com.designated.customer.NAVIGATE_TO_HOME")
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(navigateHomeIntent)

            // LocalBroadcast 전송 (MainViewModel의 리시버가 받아서 처리)
            val broadcastIntent = Intent("com.designated.customer.DRIVER_ASSIGNED").apply {
                putExtra("callId", callId)
                putExtra("driverName", driverName)
                putExtra("driverPhone", driverPhone)
                putExtra("vehicleNumber", vehicleNumber)
                putExtra("driverId", driverId)
            }
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .sendBroadcast(broadcastIntent)
        }
    }

    /**
     * Play Store Install Referrer 확인 (Play Store 설치 시 자동 사무실 매칭)
     * Play Store 설치 시 QR 코드의 referrer 파라미터를 자동으로 받아서 저장
     */
    private fun checkInstallReferrer() {
        // 이미 사무실 정보가 저장되어 있으면 스킵
        val prefsManager = PreferencesManager(this)
        val savedOfficeId = prefsManager.getOfficeId()

        if (savedOfficeId != null) {
            android.util.Log.d("InstallReferrer", "이미 사무실 정보 있음: $savedOfficeId, 스킵")
            return
        }

        val referrerClient = InstallReferrerClient.newBuilder(this).build()
        referrerClient.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                try {
                    when (responseCode) {
                        InstallReferrerClient.InstallReferrerResponse.OK -> {
                            try {
                                val response: ReferrerDetails = referrerClient.installReferrer
                                val referrerUrl = response.installReferrer

                                android.util.Log.d("InstallReferrer", "Install Referrer 받음: $referrerUrl")

                                // URL 디코딩 및 파싱
                                if (referrerUrl.isNotEmpty()) {
                                    parseAndSaveReferrer(referrerUrl)
                                } else {
                                    android.util.Log.d("InstallReferrer", "Referrer URL이 비어있음")
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("InstallReferrer", "Referrer 처리 중 오류", e)
                            }
                        }

                        InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED -> {
                            android.util.Log.w("InstallReferrer", "Install Referrer API를 지원하지 않는 기기")
                        }

                        InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                            android.util.Log.w("InstallReferrer", "Play Store 서비스를 사용할 수 없음")
                        }

                        else -> {
                            android.util.Log.w("InstallReferrer", "알 수 없는 응답 코드: $responseCode")
                        }
                    }
                } finally {
                    // 모든 케이스에서 연결 해제 (리소스 누수 방지)
                    try {
                        referrerClient.endConnection()
                    } catch (e: Exception) {
                        android.util.Log.e("InstallReferrer", "연결 해제 중 오류", e)
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                android.util.Log.d("InstallReferrer", "Install Referrer 서비스 연결 해제됨")
            }
        })
    }

    /**
     * Install Referrer URL 파싱 및 저장
     * 예시: "r=Hongchon&o=qwfdeSOL8Vz4lXEEP4TD&driver=d123&driverName=김기사"
     */
    private fun parseAndSaveReferrer(referrerUrl: String) {
        try {
            // URL 디코딩
            val decoded = URLDecoder.decode(referrerUrl, StandardCharsets.UTF_8.name())
            android.util.Log.d("InstallReferrer", "디코딩된 Referrer: $decoded")

            // 파라미터 파싱
            val params = decoded.split("&").associate {
                val (key, value) = it.split("=", limit = 2)
                key to value
            }

            val provinceId = params["p"] ?: params["r"] // p=provinceId, r=regionId(하위호환)
            val cityId = params["c"] ?: "" // c=cityId
            val officeId = params["o"]
            val driverId = params["driver"]
            val driverName = params["driverName"]
            val phoneNumber = params["phone"]
            val bankName = params["bank"]
            val accountNumber = params["account"]
            val accountHolder = params["holder"]

            if (provinceId != null && cityId.isNotEmpty() && officeId != null) {
                // SharedPreferences에 저장
                val prefsManager = PreferencesManager(this)
                prefsManager.saveOfficeInfo(officeId, provinceId, cityId)

                // 추가 정보 저장
                if (driverId != null && driverName != null) {
                    prefsManager.saveDriverReferralInfo(driverId, driverName)
                }

                if (phoneNumber != null && bankName != null && accountNumber != null && accountHolder != null) {
                    prefsManager.saveOfficeContactInfo(phoneNumber, bankName, accountNumber, accountHolder)
                }

                android.util.Log.d("InstallReferrer", "✅ 사무실 정보 저장 완료: $provinceId/$cityId/$officeId")
                if (driverId != null) {
                    android.util.Log.d("InstallReferrer", "✅ 추천 기사: $driverName ($driverId)")
                }
            } else {
                android.util.Log.w("InstallReferrer", "필수 파라미터 누락: p=$provinceId, c=$cityId, o=$officeId")
            }
        } catch (e: Exception) {
            android.util.Log.e("InstallReferrer", "Referrer 파싱 중 오류", e)
        }
    }

    /**
     * 알림 권한 요청 (Android 13+)
     */
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    /**
     * 만보기 권한 요청 (Android 10+)
     */
    private fun requestActivityRecognitionPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    ACTIVITY_RECOGNITION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    /**
     * FCM 토큰 요청 및 Firestore에 저장
     */
    private fun requestAndSaveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                android.util.Log.w("FCM", "FCM 토큰 가져오기 실패", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            android.util.Log.d("FCM", "FCM 토큰: $token")

            // PreferencesManager에서 phoneNumber, provinceId, cityId, officeId 가져와서 Firestore에 저장
            val prefsManager = PreferencesManager(this)
            val phoneNumber = prefsManager.getPhoneNumber()
            val provinceId = prefsManager.getProvinceId()
            val cityId = prefsManager.getCityId()
            val officeId = prefsManager.getOfficeId()

            if (!phoneNumber.isNullOrEmpty() && !provinceId.isNullOrEmpty() && !cityId.isNullOrEmpty() && !officeId.isNullOrEmpty()) {
                saveFcmTokenToFirestore(token, phoneNumber, provinceId, cityId, officeId)
            } else {
                android.util.Log.d("FCM", "아직 사용자 정보 없음 - 나중에 저장됨")
            }
        }
    }

    /**
     * FCM 토큰을 Firestore에 저장
     */
    private fun saveFcmTokenToFirestore(token: String, phoneNumber: String, provinceId: String, cityId: String, officeId: String) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("customerInfo")
            .document(phoneNumber)
            .set(
                mapOf(
                    "fcmToken" to token,
                    "phoneNumber" to phoneNumber,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            .addOnSuccessListener {
                android.util.Log.d("FCM", "FCM 토큰 Firestore 저장 완료")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("FCM", "FCM 토큰 Firestore 저장 실패", e)
            }
    }

    /**
     * StepCounterService 시작
     */
    private fun startStepCounterService() {
        val intent = Intent(this, com.designated.customer.service.StepCounterService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 앱 종료 시 서비스는 유지 (백그라운드에서 계속 실행)
    }
}

enum class AppScreen {
    LOADING,
    QR_REQUIRED,
    TERMS_AGREEMENT,
    TERMS_VIEWER,
    PRIVACY_VIEWER,
    PHONE_AUTH,
    PROFILE_SETUP,
    MAIN
}

@Composable
fun CustomerApp(
    initialIntent: Intent? = null
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    // URL 파라미터 처리: d (driverId), dn (driverName)
    LaunchedEffect(initialIntent) {
        initialIntent?.data?.let { uri ->
            val driverId = uri.getQueryParameter("d")
            val driverName = uri.getQueryParameter("dn")

            if (!driverId.isNullOrEmpty() && !driverName.isNullOrEmpty()) {
                android.util.Log.d("DriverReferral", "기사 추천 정보 발견 - driverId=$driverId, driverName=$driverName")
                preferencesManager.saveDriverReferralInfo(driverId, driverName)
            }
        }
    }

    // 화면 상태
    var currentScreen by remember { mutableStateOf(AppScreen.LOADING) }

    // 사무실 정보
    var currentOfficeId by remember { mutableStateOf<String?>(null) }
    var currentProvinceId by remember { mutableStateOf<String?>(null) }
    var currentCityId by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }

    // 인증된 전화번호 (Phone Auth에서 전달)
    var verifiedPhoneNumber by remember { mutableStateOf("") }

    // CustomerInfo 상태 (사무실 연락처 포함)
    var customerInfo by remember { mutableStateOf<com.designated.customer.data.model.CustomerInfo?>(null) }

    // 초기화: 익명인증 + 사무실 정보 + 프로필 확인
    LaunchedEffect(Unit) {
        val prefsOfficeId = preferencesManager.getOfficeId()
        val prefsProvinceId = preferencesManager.getProvinceId()
        val prefsCityId = preferencesManager.getCityId()

        currentOfficeId = prefsOfficeId
        currentProvinceId = prefsProvinceId
        currentCityId = prefsCityId

        // 사무실 정보 없으면 QR 필요
        if (prefsOfficeId == null || prefsProvinceId == null || prefsCityId == null) {
            currentScreen = AppScreen.QR_REQUIRED
            return@LaunchedEffect
        }

        // 익명인증: 로그인 안 되어 있으면 자동 수행
        var currentUser = auth.currentUser
        if (currentUser == null) {
            try {
                val result = auth.signInAnonymously().await()
                currentUser = result.user
                android.util.Log.d("AnonymousAuth", "익명 로그인 완료: ${currentUser?.uid}")
            } catch (e: Exception) {
                android.util.Log.e("AnonymousAuth", "익명 로그인 실패", e)
                currentScreen = AppScreen.QR_REQUIRED
                return@LaunchedEffect
            }
        } else {
            android.util.Log.d("AnonymousAuth", "이미 로그인됨: ${currentUser.uid}")
        }

        if (currentUser == null) {
            currentScreen = AppScreen.QR_REQUIRED
            return@LaunchedEffect
        }

        currentUserId = currentUser.uid
        verifiedPhoneNumber = currentUser.phoneNumber ?: preferencesManager.getPhoneNumber() ?: ""

        // 프로필 존재 여부 확인
        try {
            val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
            val doc = firestore
                .collection("provinces").document(prefsProvinceId)
                .collection("cities").document(prefsCityId)
                .collection("offices").document(prefsOfficeId)
                .collection("customers").document(currentUser.uid)
                .get()
                .await()

            if (doc.exists()) {
                android.util.Log.d("ProfileCheck", "프로필 발견: ${currentUser.uid}")
                customerInfo = com.designated.customer.data.model.CustomerInfo.fromMap(doc.data ?: emptyMap())

                // 전화번호를 SharedPreferences에 저장 (FCM 토큰 저장에 필요)
                customerInfo?.phoneNumber?.let { phone ->
                    preferencesManager.savePhoneNumber(phone)
                }

                // lastActiveAt 업데이트 (앱 실행 시마다)
                try {
                    firestore
                        .collection("provinces").document(prefsProvinceId)
                        .collection("cities").document(prefsCityId)
                        .collection("offices").document(prefsOfficeId)
                        .collection("customers").document(currentUser.uid)
                        .update("lastActiveAt", com.google.firebase.Timestamp.now())
                        .await()
                    android.util.Log.d("ProfileCheck", "lastActiveAt 업데이트 완료")
                } catch (e: Exception) {
                    android.util.Log.e("ProfileCheck", "lastActiveAt 업데이트 실패", e)
                }

                currentScreen = AppScreen.MAIN
            } else {
                // 로그인은 됐지만 프로필 없음
                android.util.Log.d("ProfileCheck", "프로필 없음, 약관/프로필 확인")
                if (!preferencesManager.isTermsAccepted()) {
                    currentScreen = AppScreen.TERMS_AGREEMENT
                } else {
                    currentScreen = AppScreen.PROFILE_SETUP
                }
            }
        } catch (e: Exception) {
            if (e::class.simpleName?.contains("LeftCompositionCancellationException") == true ||
                e.message?.contains("left the composition") == true) {
                return@LaunchedEffect
            }
            android.util.Log.e("ProfileCheck", "프로필 확인 실패", e)
            if (!preferencesManager.isTermsAccepted()) {
                currentScreen = AppScreen.TERMS_AGREEMENT
            } else {
                currentScreen = AppScreen.PROFILE_SETUP
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        when (currentScreen) {
            AppScreen.LOADING -> {
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
                            text = "로딩 중...",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            AppScreen.QR_REQUIRED -> {
                // QR 코드 재설치 안내 화면
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        androidx.compose.material3.Text(
                            text = "사무실 정보를 찾을 수 없습니다",
                            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        androidx.compose.material3.Text(
                            text = "QR 코드를 통해 앱을 다시 설치해주세요.\n\n1. 사무실에서 받은 QR 코드를 스캔하세요\n2. 플레이스토어에서 앱을 다운로드하세요\n3. 앱을 설치하고 실행하세요",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AppScreen.TERMS_AGREEMENT -> {
                TermsAgreementScreen(
                    onTermsAgreed = { termsVersion, marketingConsent ->
                        preferencesManager.saveTermsAcceptance(termsVersion, marketingConsent)
                        // 이미 Phone Auth 완료 상태면 프로필 입력으로 직행
                        val user = auth.currentUser
                        if (user != null && user.phoneNumber != null) {
                            verifiedPhoneNumber = user.phoneNumber ?: ""
                            currentUserId = user.uid
                            currentScreen = AppScreen.PROFILE_SETUP
                        } else {
                            currentScreen = AppScreen.PHONE_AUTH
                        }
                    },
                    onViewTerms = { currentScreen = AppScreen.TERMS_VIEWER },
                    onViewPrivacy = { currentScreen = AppScreen.PRIVACY_VIEWER },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            AppScreen.TERMS_VIEWER -> {
                DocumentViewerScreen(
                    title = "이용약관",
                    content = LegalDocuments.termsOfService,
                    onBack = { currentScreen = AppScreen.TERMS_AGREEMENT }
                )
            }

            AppScreen.PRIVACY_VIEWER -> {
                DocumentViewerScreen(
                    title = "개인정보처리방침",
                    content = LegalDocuments.privacyPolicy,
                    onBack = { currentScreen = AppScreen.TERMS_AGREEMENT }
                )
            }

            AppScreen.PHONE_AUTH -> {
                PhoneAuthScreen(
                    onAuthSuccess = { phoneNumber ->
                        verifiedPhoneNumber = phoneNumber
                        currentUserId = auth.currentUser?.uid
                        preferencesManager.savePhoneNumber(phoneNumber)
                        currentScreen = AppScreen.PROFILE_SETUP
                    },
                    onBack = { currentScreen = AppScreen.TERMS_AGREEMENT },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            AppScreen.PROFILE_SETUP -> {
                if (currentProvinceId != null && currentCityId != null && currentOfficeId != null) {
                    ProfileSetupScreen(
                        provinceId = currentProvinceId!!,
                        cityId = currentCityId!!,
                        officeId = currentOfficeId!!,
                        verifiedPhoneNumber = verifiedPhoneNumber,
                        termsVersion = preferencesManager.getTermsVersion() ?: "1.0.0",
                        marketingConsent = preferencesManager.getMarketingConsent(),
                        onProfileComplete = {
                            android.util.Log.d("ProfileSetup", "프로필 입력 완료")

                            // CustomerInfo 로드 후 메인 화면으로 전환
                            coroutineScope.launch {
                                try {
                                    val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                    val doc = firestore
                                        .collection("provinces").document(currentProvinceId!!)
                                        .collection("cities").document(currentCityId!!)
                                        .collection("offices").document(currentOfficeId!!)
                                        .collection("customers").document(currentUserId!!)
                                        .get()
                                        .await()

                                    if (doc.exists()) {
                                        customerInfo = com.designated.customer.data.model.CustomerInfo.fromMap(doc.data ?: emptyMap())

                                        customerInfo?.phoneNumber?.let { phone ->
                                            preferencesManager.savePhoneNumber(phone)
                                            android.util.Log.d("ProfileSetup", "전화번호 저장 완료: $phone")
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ProfileSetup", "CustomerInfo 로드 실패", e)
                                }
                                currentScreen = AppScreen.MAIN
                            }
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }

            AppScreen.MAIN -> {
                if (currentProvinceId != null && currentCityId != null && currentOfficeId != null && customerInfo != null) {
                    MainNavigation(
                        provinceId = currentProvinceId!!,
                        cityId = currentCityId!!,
                        officeId = currentOfficeId!!,
                        phoneNumber = customerInfo!!.phoneNumber,
                        customerInfo = customerInfo,
                        onLogout = {
                            android.util.Log.w("Logout", "로그아웃은 데이터 손실을 초래할 수 있습니다")
                        },
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    }
}
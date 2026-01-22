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
import com.designated.customer.BuildConfig
import com.designated.customer.ui.auth.PhoneAuthScreen
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
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase as FirebaseKtx
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
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

class MainActivity : ComponentActivity() {
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var allowDirectInstall by mutableStateOf(false)

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

        // Remote Config 초기화
        remoteConfig = FirebaseRemoteConfig.getInstance()
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600  // 1시간
        }
        remoteConfig.setConfigSettingsAsync(configSettings)

        // 기본값 설정
        remoteConfig.setDefaultsAsync(mapOf(
            "allowDirectInstall" to true
        ))

        // Remote Config 가져오기
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                allowDirectInstall = remoteConfig.getBoolean("allowDirectInstall")
                android.util.Log.d("RemoteConfig", "allowDirectInstall = $allowDirectInstall")
            }
        }

        // StepCounterService 시작
        startStepCounterService()

        setContent {
            DesignatedCustomerTheme {
                CustomerApp(
                    initialIntent = intent,
                    allowDirectInstall = allowDirectInstall
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

                        referrerClient.endConnection()
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

@Composable
fun CustomerApp(
    initialIntent: Intent? = null,
    allowDirectInstall: Boolean = false
) {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    // ✅ Intent 처리는 MainActivity.onCreate() 및 onNewIntent()에서 수행됨
    // ✅ URL 파라미터 처리: d (driverId), dn (driverName)
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

    // 사무실 정보: SharedPreferences 또는 Attribution 매칭에서 얻음
    var currentOfficeId by remember { mutableStateOf<String?>(null) }
    var currentProvinceId by remember { mutableStateOf<String?>(null) }
    var currentCityId by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var showProfileSetup by remember { mutableStateOf(false) }
    var hasProfileInFirestore by remember { mutableStateOf(false) }

    // 기사 추천 정보
    var referralDriverId by remember { mutableStateOf<String?>(null) }
    var referralDriverName by remember { mutableStateOf<String?>(null) }

    // 초기화: SharedPreferences에서 값 로드 + 익명 인증 확인
    LaunchedEffect(Unit) {
        val prefsOfficeId = preferencesManager.getOfficeId()
        val prefsProvinceId = preferencesManager.getProvinceId()
        val prefsCityId = preferencesManager.getCityId()

        currentOfficeId = prefsOfficeId
        currentProvinceId = prefsProvinceId
        currentCityId = prefsCityId

        // 익명 인증 상태 확인
        val currentUser = auth.currentUser
        if (currentUser != null) {
            android.util.Log.d("AnonymousAuth", "이미 로그인됨: ${currentUser.uid}")
            currentUserId = currentUser.uid
        } else {
            // 자동 익명 인증
            try {
                android.util.Log.d("AnonymousAuth", "익명 인증 시작")
                val result = auth.signInAnonymously().await()
                currentUserId = result.user?.uid
                android.util.Log.d("AnonymousAuth", "익명 인증 성공: $currentUserId")
            } catch (e: Exception) {
                android.util.Log.e("AnonymousAuth", "익명 인증 실패", e)
            }
        }
    }

    // CustomerInfo 상태 (사무실 연락처 포함)
    var customerInfo by remember { mutableStateOf<com.designated.customer.data.model.CustomerInfo?>(null) }

    // 프로필 존재 여부 확인 (익명 인증 완료 + 사무실 매칭 완료 후)
    LaunchedEffect(currentUserId, currentProvinceId, currentCityId, currentOfficeId) {
        if (currentUserId != null && currentProvinceId != null && currentCityId != null && currentOfficeId != null) {
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
                    // 프로필이 이미 있음
                    android.util.Log.d("ProfileCheck", "프로필 발견: ${currentUserId}")
                    hasProfileInFirestore = true
                    customerInfo = com.designated.customer.data.model.CustomerInfo.fromMap(doc.data ?: emptyMap())

                    // ✅ 전화번호를 SharedPreferences에 저장 (FCM 토큰 저장에 필요)
                    customerInfo?.phoneNumber?.let { phone ->
                        preferencesManager.savePhoneNumber(phone)
                        android.util.Log.d("ProfileCheck", "전화번호 저장 완료: $phone")
                    }

                    // lastActiveAt 업데이트 (앱 실행 시마다)
                    try {
                        firestore
                            .collection("provinces").document(currentProvinceId!!)
                            .collection("cities").document(currentCityId!!)
                            .collection("offices").document(currentOfficeId!!)
                            .collection("customers").document(currentUserId!!)
                            .update("lastActiveAt", com.google.firebase.Timestamp.now())
                            .await()
                        android.util.Log.d("ProfileCheck", "lastActiveAt 업데이트 완료")
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileCheck", "lastActiveAt 업데이트 실패", e)
                    }
                } else {
                    // 프로필 없음 → 프로필 입력 화면 표시
                    android.util.Log.d("ProfileCheck", "프로필 없음, 입력 화면 표시")
                    hasProfileInFirestore = false
                    showProfileSetup = true
                }
            } catch (e: Exception) {
                // LeftCompositionCancellationException은 Compose 라이프사이클 에러이므로 무시
                if (e::class.simpleName?.contains("LeftCompositionCancellationException") == true ||
                    e.message?.contains("left the composition") == true) {
                    android.util.Log.d("ProfileCheck", "Composition cancelled - 정상 동작, 무시")
                    return@LaunchedEffect
                }

                android.util.Log.e("ProfileCheck", "프로필 확인 실패", e)
                // 다른 에러 시에만 프로필 입력 화면 표시
                hasProfileInFirestore = false
                showProfileSetup = true
            }
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { paddingValues ->
        when {
            // 0. 인증 대기 중이면 로딩 화면
            currentUserId == null -> {
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
                            text = "인증 중...",
                            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            // 2. 프로필 입력이 필요하면 프로필 입력 화면
            showProfileSetup && currentProvinceId != null && currentCityId != null && currentOfficeId != null -> {
                ProfileSetupScreen(
                    provinceId = currentProvinceId!!,
                    cityId = currentCityId!!,
                    officeId = currentOfficeId!!,

                    onProfileComplete = {
                        // 프로필 입력 완료
                        android.util.Log.d("ProfileSetup", "프로필 입력 완료")
                        showProfileSetup = false
                        hasProfileInFirestore = true

                        // CustomerInfo 다시 로드
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

                                    // ✅ 전화번호를 SharedPreferences에 저장 (FCM 토큰 저장에 필요)
                                    customerInfo?.phoneNumber?.let { phone ->
                                        preferencesManager.savePhoneNumber(phone)
                                        android.util.Log.d("ProfileSetup", "전화번호 저장 완료: $phone")
                                    }
                                }


                            } catch (e: Exception) {
                                android.util.Log.e("ProfileSetup", "CustomerInfo 로드 실패", e)
                            }
                        }
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            // 2. 사무실 정보가 없으면 Remote Config에 따라 처리
            currentOfficeId == null || currentProvinceId == null || currentCityId == null -> {
                if (allowDirectInstall) {
                    // Direct install 허용 시: 사무실 선택 화면 표시
                    com.designated.customer.ui.office.OfficeSelectionScreen(
                        modifier = Modifier.padding(paddingValues),
                        onOfficeSelected = { officeId, provinceId, cityId ->
                            preferencesManager.saveOfficeInfo(officeId, provinceId, cityId)
                            currentOfficeId = officeId
                            currentProvinceId = provinceId
                            currentCityId = cityId
                        }
                    )
                } else {
                    // Direct install 불허 시: 기존 에러 화면 (QR 코드 재설치 안내)
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
                                imageVector = androidx.compose.material.icons.Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = androidx.compose.material3.MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            androidx.compose.material3.Text(
                                text = "사무실 정보를 찾을 수 없습니다",
                                style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.Text(
                                text = "QR 코드를 통해 앱을 다시 설치해주세요.\n\n1. 사무실에서 받은 QR 코드를 스캔하세요\n2. 랜딩페이지에서 APK를 다운로드하세요\n3. 앱을 설치하고 실행하세요",
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            // 3. 모든 정보가 있으면 메인 네비게이션
            hasProfileInFirestore && currentProvinceId != null && currentCityId != null && currentOfficeId != null && customerInfo != null -> {
                MainNavigation(
                    provinceId = currentProvinceId!!,
                    cityId = currentCityId!!,
                    officeId = currentOfficeId!!,
                    phoneNumber = customerInfo!!.phoneNumber,
                    customerInfo = customerInfo, // CustomerInfo 전달
                    onLogout = {
                        // 로그아웃 처리 (경고 필요)
                        android.util.Log.w("Logout", "로그아웃은 데이터 손실을 초래할 수 있습니다")
                        // 실제로는 로그아웃 안 함 (자동 로그인 유지)
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
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

// 토큰 매칭 결과 데이터 클래스
data class TokenMatchResult(
    val regionId: String,
    val officeId: String,
    val officePhone: String?,
    val bankName: String?,
    val accountNumber: String?,
    val accountHolder: String?,
    val referralDriverId: String? = null,    // ✅ 기사 ID 추가
    val referralDriverName: String? = null   // ✅ 기사 이름 추가
)

// 토큰 조회 함수 (캐시 우선, 없으면 Firestore에서 조회)
suspend fun getAttributionToken(context: android.content.Context): String? {
    return try {
        val prefsManager = com.designated.customer.util.PreferencesManager(context)

        // 1. 먼저 캐시된 토큰 확인 (즉시 반환, 0 reads)
        val cachedToken = prefsManager.getAttributionToken()
        if (cachedToken != null) {
            android.util.Log.d("AttributionToken", "캐시된 토큰 사용: $cachedToken")
            return cachedToken
        }

        android.util.Log.d("AttributionToken", "캐시된 토큰 없음, Firestore에서 조회 시작")

        // 2. 캐시 없을 때만 Firestore 조회
        // FingerprintJS 방식으로 디바이스 정보 생성
        val screenResolution = "${android.content.res.Resources.getSystem().displayMetrics.widthPixels}x${android.content.res.Resources.getSystem().displayMetrics.heightPixels}"
        val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()

        // 최근 24시간 이내에 생성된 attribution에서 토큰 찾기
        val oneDayAgo = com.google.firebase.Timestamp(System.currentTimeMillis() / 1000 - 24 * 60 * 60, 0)

        // 모든 지역/사무실을 순회하며 매칭되는 attribution 찾기
        val regionsSnapshot = firestore.collection("regions").get().await()

        for (regionDoc in regionsSnapshot.documents) {
            val officesSnapshot = firestore
                .collection("regions").document(regionDoc.id)
                .collection("offices")
                .get()
                .await()

            for (officeDoc in officesSnapshot.documents) {
                val attributionQuery = firestore
                    .collection("regions").document(regionDoc.id)
                    .collection("offices").document(officeDoc.id)
                    .collection("attributions")
                    .whereEqualTo("screenResolution", screenResolution)
                    .whereGreaterThan("createdAt", oneDayAgo)
                    .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .await()

                if (!attributionQuery.isEmpty) {
                    val attribution = attributionQuery.documents[0]
                    val token = attribution.getString("token")
                    if (token != null) {
                        android.util.Log.d("AttributionToken", "토큰 발견: $token (screenResolution: $screenResolution)")
                        // 3. 조회 성공 시 캐시에 저장
                        prefsManager.saveAttributionToken(token)
                        return token
                    }
                }
            }
        }

        android.util.Log.d("AttributionToken", "토큰을 찾지 못함 (screenResolution: $screenResolution)")
        null
    } catch (e: Exception) {
        android.util.Log.e("AttributionToken", "토큰 조회 실패", e)
        null
    }
}

// 토큰 기반 매칭 함수
suspend fun matchByToken(token: String): TokenMatchResult? {
    return try {
        val functions = FirebaseFunctions.getInstance("asia-northeast3")
        val data = hashMapOf("token" to token)
        val result = functions.getHttpsCallable("matchByToken")
            .call(data)
            .await()

        val responseData = result.data as? Map<*, *>
        android.util.Log.d("TokenMatching", "matchByToken 응답: $responseData")

        if (responseData?.get("success") == true) {
            TokenMatchResult(
                regionId = responseData["regionId"] as String,
                officeId = responseData["officeId"] as String,
                officePhone = responseData["officePhone"] as? String,
                bankName = responseData["bankName"] as? String,
                accountNumber = responseData["accountNumber"] as? String,
                accountHolder = responseData["accountHolder"] as? String,
                referralDriverId = responseData["referralDriverId"] as? String,     // ✅ 기사 ID
                referralDriverName = responseData["referralDriverName"] as? String  // ✅ 기사 이름
            )
        } else {
            android.util.Log.w("TokenMatching", "매칭 실패: ${responseData?.get("message")}")
            null
        }
    } catch (e: Exception) {
        android.util.Log.e("TokenMatching", "토큰 매칭 중 오류", e)
        null
    }
}

// 토큰 클레임 함수 (사용 완료 표시)
suspend fun claimToken(token: String, phoneNumber: String?) {
    try {
        val functions = FirebaseFunctions.getInstance("asia-northeast3")
        val data = hashMapOf(
            "token" to token,
            "phoneNumber" to (phoneNumber ?: "unknown")
        )
        functions.getHttpsCallable("claimToken")
            .call(data)
            .await()
        android.util.Log.d("TokenMatching", "토큰 클레임 완료: $token")
    } catch (e: Exception) {
        android.util.Log.e("TokenMatching", "토큰 클레임 실패", e)
    }
}

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

            val regionId = params["r"]
            val officeId = params["o"]
            val driverId = params["driver"]
            val driverName = params["driverName"]
            val phoneNumber = params["phone"]
            val bankName = params["bank"]
            val accountNumber = params["account"]
            val accountHolder = params["holder"]

            if (regionId != null && officeId != null) {
                // SharedPreferences에 저장
                val prefsManager = PreferencesManager(this)
                prefsManager.saveOfficeInfo(officeId, regionId)

                // 추가 정보 저장
                if (driverId != null && driverName != null) {
                    prefsManager.saveDriverReferralInfo(driverId, driverName)
                }

                if (phoneNumber != null && bankName != null && accountNumber != null && accountHolder != null) {
                    prefsManager.saveOfficeContactInfo(phoneNumber, bankName, accountNumber, accountHolder)
                }

                android.util.Log.d("InstallReferrer", "✅ 사무실 정보 저장 완료: $regionId/$officeId")
                if (driverId != null) {
                    android.util.Log.d("InstallReferrer", "✅ 추천 기사: $driverName ($driverId)")
                }
            } else {
                android.util.Log.w("InstallReferrer", "필수 파라미터 누락: r=$regionId, o=$officeId")
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

            // PreferencesManager에서 phoneNumber, regionId, officeId 가져와서 Firestore에 저장
            val prefsManager = PreferencesManager(this)
            val phoneNumber = prefsManager.getPhoneNumber()
            val regionId = prefsManager.getRegionId()
            val officeId = prefsManager.getOfficeId()

            if (!phoneNumber.isNullOrEmpty() && !regionId.isNullOrEmpty() && !officeId.isNullOrEmpty()) {
                saveFcmTokenToFirestore(token, phoneNumber, regionId, officeId)
            } else {
                android.util.Log.d("FCM", "아직 사용자 정보 없음 - 나중에 저장됨")
            }
        }
    }

    /**
     * FCM 토큰을 Firestore에 저장
     */
    private fun saveFcmTokenToFirestore(token: String, phoneNumber: String, regionId: String, officeId: String) {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("regions").document(regionId)
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
fun CustomerApp(initialIntent: Intent? = null) {
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
    var currentRegionId by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var showProfileSetup by remember { mutableStateOf(false) }
    var hasProfileInFirestore by remember { mutableStateOf(false) }
    var attributionToken by remember { mutableStateOf<String?>(null) }

    // 기사 추천 정보
    var referralDriverId by remember { mutableStateOf<String?>(null) }
    var referralDriverName by remember { mutableStateOf<String?>(null) }

    // 초기화: SharedPreferences에서 값 로드 + 익명 인증 확인
    LaunchedEffect(Unit) {
        val prefsOfficeId = preferencesManager.getOfficeId()
        val prefsRegionId = preferencesManager.getRegionId()

        currentOfficeId = prefsOfficeId
        currentRegionId = prefsRegionId

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

    // Attribution 매칭 상태
    var isMatchingAttribution by remember { mutableStateOf(false) }
    var hasTriedMatching by remember { mutableStateOf(false) }

    // 앱 최초 실행 시 Attribution 매칭 시도
    LaunchedEffect(Unit) {
        android.util.Log.d("AttributionMatching", "LaunchedEffect started")

        // ✅ 이미 prefs에 사무실 정보가 있으면 매칭 skip (성능 최적화)
        if (currentOfficeId != null && currentRegionId != null) {
            android.util.Log.d("AttributionMatching", "SharedPreferences에 사무실 정보 있음 - 매칭 skip (officeId=$currentOfficeId, regionId=$currentRegionId)")
            hasTriedMatching = true
            isMatchingAttribution = false
            return@LaunchedEffect
        }

        if (!hasTriedMatching) {
            hasTriedMatching = true
            isMatchingAttribution = true

            try {
                // 1. 먼저 토큰 기반 매칭 시도 (새 방식)
                val token = getAttributionToken(context)

                if (token != null) {
                    android.util.Log.d("AttributionMatching", "토큰 발견: $token")
                    val tokenResult = matchByToken(token)

                    if (tokenResult != null) {
                        // 토큰 매칭 성공
                        android.util.Log.d("AttributionMatching", "토큰 매칭 성공 - regionId=${tokenResult.regionId}, officeId=${tokenResult.officeId}")

                        currentRegionId = tokenResult.regionId
                        currentOfficeId = tokenResult.officeId
                        attributionToken = token
                        preferencesManager.saveOfficeInfo(tokenResult.officeId, tokenResult.regionId)

                        if (tokenResult.officePhone != null && tokenResult.bankName != null &&
                            tokenResult.accountNumber != null && tokenResult.accountHolder != null) {
                            preferencesManager.saveOfficeContactInfo(
                                tokenResult.officePhone,
                                tokenResult.bankName,
                                tokenResult.accountNumber,
                                tokenResult.accountHolder
                            )
                        }

                        // ✅ 기사 추천 정보 저장
                        if (tokenResult.referralDriverId != null && tokenResult.referralDriverName != null) {
                            preferencesManager.saveDriverReferralInfo(
                                tokenResult.referralDriverId,
                                tokenResult.referralDriverName
                            )
                            android.util.Log.d("AttributionMatching", "토큰 매칭 - 기사 추천 정보 저장: driverId=${tokenResult.referralDriverId}, driverName=${tokenResult.referralDriverName}")
                        }

                        // 토큰 매칭 성공 시 캐시에 저장 (이미 getAttributionToken에서 저장되지만 명시적으로 재저장)
                        preferencesManager.saveAttributionToken(token)

                        isMatchingAttribution = false
                        return@LaunchedEffect
                    } else {
                        // 토큰 매칭 실패 시 캐시 삭제 (만료된 토큰)
                        android.util.Log.w("AttributionMatching", "토큰 매칭 실패 - Install Referrer를 통한 매칭을 권장합니다")
                        preferencesManager.clearAttributionToken()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AttributionMatching", "Exception during matching", e)
            } finally {
                isMatchingAttribution = false
            }
        }
    }

    // 프로필 존재 여부 확인 (익명 인증 완료 + 사무실 매칭 완료 후)
    LaunchedEffect(currentUserId, currentRegionId, currentOfficeId, isMatchingAttribution) {
        if (currentUserId != null && currentRegionId != null && currentOfficeId != null && !isMatchingAttribution) {
            try {
                val firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val doc = firestore
                    .collection("regions").document(currentRegionId!!)
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
                            .collection("regions").document(currentRegionId!!)
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
            // 1. Attribution 매칭 중이면 로딩 화면
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
            // 2. 프로필 입력이 필요하면 프로필 입력 화면
            showProfileSetup && currentRegionId != null && currentOfficeId != null -> {
                ProfileSetupScreen(
                    regionId = currentRegionId!!,
                    officeId = currentOfficeId!!,
                    attributionToken = attributionToken,
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
                                    .collection("regions").document(currentRegionId!!)
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

                                // 토큰이 있으면 클레임 처리
                                if (attributionToken != null) {
                                    val phoneNumber = customerInfo?.phoneNumber
                                    claimToken(attributionToken!!, phoneNumber)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("ProfileSetup", "CustomerInfo 로드 실패", e)
                            }
                        }
                    },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            // 2. 사무실 정보가 없으면 에러 안내 화면
            currentOfficeId == null || currentRegionId == null -> {
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
            // 3. 모든 정보가 있으면 메인 네비게이션
            hasProfileInFirestore && currentRegionId != null && currentOfficeId != null && customerInfo != null -> {
                MainNavigation(
                    regionId = currentRegionId!!,
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
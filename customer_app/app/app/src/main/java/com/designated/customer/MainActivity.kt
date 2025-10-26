package com.designated.customer

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

// 토큰 매칭 결과 데이터 클래스
data class TokenMatchResult(
    val regionId: String,
    val officeId: String,
    val officePhone: String?,
    val bankName: String?,
    val accountNumber: String?,
    val accountHolder: String?
)

// 토큰 조회 함수 (Firestore에서 최근 토큰 찾기)
suspend fun getAttributionToken(context: android.content.Context): String? {
    return try {
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
                accountHolder = responseData["accountHolder"] as? String
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
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    // 사무실 정보: SharedPreferences 또는 Attribution 매칭에서 얻음
    var currentOfficeId by remember { mutableStateOf<String?>(null) }
    var currentRegionId by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var showProfileSetup by remember { mutableStateOf(false) }
    var hasProfileInFirestore by remember { mutableStateOf(false) }
    var attributionToken by remember { mutableStateOf<String?>(null) }

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

                        isMatchingAttribution = false
                        return@LaunchedEffect
                    } else {
                        android.util.Log.w("AttributionMatching", "토큰 매칭 실패, 핑거프린트 매칭으로 폴백")
                    }
                }

                // 2. 토큰이 없거나 실패 시 핑거프린트 매칭 (기존 방식)
                android.util.Log.d("AttributionMatching", "핑거프린트 매칭 시도")
                val attributionService = com.designated.customer.service.AttributionMatchingService(context)
                val result = attributionService.matchAttribution()

                android.util.Log.d("AttributionMatching", "Match result: $result")

                when (result) {
                    is com.designated.customer.service.AttributionMatchingService.MatchResult.Success -> {
                        android.util.Log.d("AttributionMatching", "SUCCESS - regionId=${result.regionId}, officeId=${result.officeId}, score=${result.score}")

                        // 사무실 정보 저장 (기존 데이터 덮어쓰기)
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
                        android.util.Log.w("AttributionMatching", "NO MATCH - ${result.message}")
                        // 매칭 실패 시 기존 SharedPreferences 데이터 사용
                    }
                    is com.designated.customer.service.AttributionMatchingService.MatchResult.Error -> {
                        android.util.Log.e("AttributionMatching", "ERROR - ${result.message}", result.exception)
                        // 에러 시 기존 SharedPreferences 데이터 사용
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
                android.util.Log.e("ProfileCheck", "프로필 확인 실패", e)
                // 에러 시 프로필 입력 화면 표시
                hasProfileInFirestore = false
                showProfileSetup = true
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
            // 1. 프로필 입력이 필요하면 프로필 입력 화면
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
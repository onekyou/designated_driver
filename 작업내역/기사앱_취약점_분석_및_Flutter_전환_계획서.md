# 기사앱 취약점 분석 및 Flutter 전환 계획서

**작성일**: 2025년 1월 17일
**분석 대상**: Driver App (기사앱)
**앱 버전**: versionCode 2
**분석 범위**: 전체 코드베이스 (Kotlin/Android + Jetpack Compose)
**전환 목표**: Flutter 크로스 플랫폼 (Android + iOS)

---

## 📋 목차
1. [앱 구조 및 아키텍처](#1-앱-구조-및-아키텍처)
2. [주요 기능 목록](#2-주요-기능-목록)
3. [발견된 취약점](#3-발견된-취약점)
4. [Flutter 전환 시 개선 방안](#4-flutter-전환-시-개선-방안)
5. [예상 작업 범위 및 시간](#5-예상-작업-범위-및-시간)
6. [권장 사항](#6-권장-사항)

---

## 1. 앱 구조 및 아키텍처

### 1.1 전체 아키텍처
- **패턴**: MVVM (Model-View-ViewModel) + Single Source of Truth
- **의존성 주입**: Dagger Hilt
- **UI 프레임워크**: Jetpack Compose (Material 3)
- **상태 관리**: StateFlow + Compose State
- **백엔드**: Firebase (Auth, Firestore, FCM)
- **네비게이션**: Navigation Compose

### 1.2 주요 기술 스택
```
- Kotlin 1.9.22
- Jetpack Compose BOM
- Firebase BOM (Auth, Firestore, Messaging)
- Hilt (DI)
- Coroutines + Flow
- Google Play Services Location
- ZXing (QR 코드 생성)
- Geocoder (주소 변환)
- SpeechRecognizer (음성 입력)
```

### 1.3 디렉토리 구조
```
com.designated.driverapp/
├── data/                    # 데이터 레이어
│   ├── Constants.kt         # 상수 정의
│   └── AddressSearchResult.kt
├── di/                      # Dependency Injection
│   └── AppModule.kt
├── model/                   # 데이터 모델
│   ├── CallInfo.kt
│   ├── DriverModel.kt
│   ├── DriverStatus.kt
│   └── CallStatus.kt
├── service/                 # 서비스
│   └── DriverForegroundService.kt
├── ui/                      # UI 레이어
│   ├── home/               # 홈 화면
│   ├── login/              # 로그인/회원가입
│   ├── screens/            # 상태별 화면
│   ├── state/              # UI 상태 정의
│   └── theme/              # 테마
├── util/                    # 유틸리티
│   ├── VoiceInputHelper.kt
│   ├── AddressSearchHelper.kt
│   └── DriverPermissionManager.kt
├── navigation/              # 네비게이션
│   └── AppNavigation.kt
├── viewmodel/              # ViewModel
│   └── DriverViewModel.kt
└── MainActivity.kt         # 진입점
```

---

## 2. 주요 기능 목록

### 2.1 인증 및 계정 관리
- **로그인**: Firebase Auth (이메일/비밀번호)
- **회원가입**: 지역/사무실 선택 + 관리자 승인 대기
- **자동 로그인**: SharedPreferences 저장
- **비밀번호 재설정**: ForgotPasswordScreen

### 2.2 콜 관리 (핵심 기능)
1. **콜 수신**: FCM 푸시 알림 + NewCallPopup
2. **콜 수락/거절**: acceptCall(), rejectCall()
3. **운행 준비**: TripPreparationScreen (출발지/도착지/요금 입력)
4. **운행 시작**: startDriving()
5. **운행 중**: InProgressScreen
6. **운행 완료**: completeCall()
7. **정산 처리**: confirmAndFinalizeTrip()

### 2.3 상태별 화면 전환
```
OFFLINE → ONLINE → WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED
```

### 2.4 포인트 시스템 (고객 앱 연동)
- **포인트 조회**: 앱 회원 여부 확인
- **포인트 사용**: 정산 시 포인트 차감
- **포인트 적립**: 운행 완료 시 자동 적립 (등급별 3-9%)

### 2.5 부가 기능
- **음성 입력**: 출발지/도착지/요금 음성 인식
- **주소 검색**: Kakao API (AddressSearchHelper)
- **현재 위치**: Geocoder + FusedLocationProvider
- **운행 내역**: SharedPreferences 로컬 저장
- **QR 코드**: 고객 추천 QR 생성 (ReferralQRScreen)
- **공유콜**: 다른 사무실 콜 수락

### 2.6 백그라운드 서비스
- **DriverForegroundService**: 콜 실시간 리스닝 (최적화 완료)
- **FCM 푸시 알림**: 새로운 콜 알림

---

## 3. 발견된 취약점

### 🔴 심각한 보안 문제 (HIGH Priority)

#### 3.1 비밀번호 평문 저장 ⭐⭐⭐

**심각도**: CRITICAL
**법적 위험**: 개인정보보호법 위반 가능

**발견 위치**:
`LoginViewModel.kt:145-152`

```kotlin
if (autoLogin) {
    putBoolean("auto_login", true)
    putString("identifier", email)
    putString("password", password)  // ❌ 비밀번호 평문 저장
}
```

**문제점**:
- SharedPreferences에 비밀번호를 평문으로 저장
- 루팅된 기기에서 `/data/data/com.designated.driverapp/shared_prefs/` 접근 시 즉시 탈취
- ADB 백업으로도 추출 가능
- 개인정보보호법 제29조(안전조치의무) 위반

**위험 시나리오**:
```
1. 기사가 자동 로그인 설정
2. 기기를 분실하거나 악의적인 사용자에게 노출
3. SharedPreferences 파일 읽기
4. 평문 비밀번호로 다른 기기에서 로그인
5. 기사의 운행 정보, 수익 정보 탈취
```

**해결 방안**:

**방안 1: EncryptedSharedPreferences 사용 (권장)**
```kotlin
// build.gradle.kts
implementation("androidx.security:security-crypto:1.1.0-alpha06")

// LoginViewModel.kt
private fun getEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    return EncryptedSharedPreferences.create(
        context,
        "encrypted_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

// 저장
val encryptedPrefs = getEncryptedPrefs(context)
encryptedPrefs.edit {
    putString("password", password)  // 자동 암호화됨
}
```

**방안 2: 비밀번호 저장 안 함 + Firebase Auth Token 활용**
```kotlin
// Firebase Auth의 자동 로그인 활용 (더 안전)
fun enableAutoLogin() {
    // Firebase는 자체적으로 토큰 관리
    // 비밀번호를 저장하지 않음
    auth.currentUser?.let {
        prefs.edit {
            putBoolean("auto_login", true)
            putString("user_id", it.uid)
        }
    }
}

// 앱 시작 시
fun checkAutoLogin() {
    if (prefs.getBoolean("auto_login", false)) {
        if (auth.currentUser != null) {
            // 이미 로그인됨
            navigateToHome()
        } else {
            // 토큰 만료 → 재로그인 필요
            navigateToLogin()
        }
    }
}
```

**예상 작업 시간**: 4시간

---

#### 3.2 Firebase 인증 의존성 (오프라인 취약) ⭐⭐⭐

**심각도**: HIGH
**사업 영향**: 네트워크 장애 시 앱 사용 불가

**문제점**:
- Firebase Auth에 100% 의존
- 네트워크 끊김 시 로그인 불가
- Firebase 장애 시 앱 사용 불가
- 콜 수신은 가능하나 수락/거절 불가

**발생 시나리오**:
```
1. 기사가 터널 진입 (네트워크 끊김)
2. 앱이 백그라운드에서 종료됨
3. 터널 나와서 앱 재실행
4. Firebase Auth 연결 실패 → 로그인 화면
5. 네트워크 복구되기 전까지 로그인 불가
6. 콜을 받을 수 없음 → 매출 손실
```

**해결 방안**:

```kotlin
// 새 파일: data/SessionCache.kt
data class SessionCache(
    val userId: String,
    val email: String,
    val regionId: String,
    val officeId: String,
    val driverName: String,
    val loginTimestamp: Long,
    val expiresAt: Long
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() > expiresAt
    }

    fun isValid(): Boolean {
        // 7일간 유효
        return !isExpired() && (System.currentTimeMillis() - loginTimestamp) < 7 * 24 * 60 * 60 * 1000
    }
}

// LoginViewModel.kt 수정
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val sessionPrefs = context.getSharedPreferences("session_cache", Context.MODE_PRIVATE)

    suspend fun loginWithOfflineSupport(email: String, password: String): Result<Unit> {
        // 1. 캐시된 세션 확인
        val cachedSession = loadCachedSession()
        if (cachedSession != null && cachedSession.isValid()) {
            if (tryReconnectFirebase()) {
                // 온라인 복귀 성공
                return Result.success(Unit)
            } else {
                // 오프라인이지만 캐시로 진행
                proceedWithCachedSession(cachedSession)
                return Result.success(Unit)
            }
        }

        // 2. 온라인 로그인 시도
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val user = result.user ?: return Result.failure(Exception("User is null"))

            // 3. Firestore에서 기사 정보 조회
            val driverDoc = firestore
                .collection("regions/${regionId}/offices/${officeId}/designated_drivers")
                .document(user.uid)
                .get()
                .await()

            // 4. 세션 캐싱
            val session = SessionCache(
                userId = user.uid,
                email = email,
                regionId = regionId,
                officeId = officeId,
                driverName = driverDoc.getString("name") ?: "",
                loginTimestamp = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000)
            )
            cacheSession(session)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadCachedSession(): SessionCache? {
        val userId = sessionPrefs.getString("user_id", null) ?: return null
        val email = sessionPrefs.getString("email", null) ?: return null
        val regionId = sessionPrefs.getString("region_id", null) ?: return null
        val officeId = sessionPrefs.getString("office_id", null) ?: return null
        val driverName = sessionPrefs.getString("driver_name", null) ?: return null
        val loginTimestamp = sessionPrefs.getLong("login_timestamp", 0)
        val expiresAt = sessionPrefs.getLong("expires_at", 0)

        return SessionCache(userId, email, regionId, officeId, driverName, loginTimestamp, expiresAt)
    }

    private fun cacheSession(session: SessionCache) {
        sessionPrefs.edit {
            putString("user_id", session.userId)
            putString("email", session.email)
            putString("region_id", session.regionId)
            putString("office_id", session.officeId)
            putString("driver_name", session.driverName)
            putLong("login_timestamp", session.loginTimestamp)
            putLong("expires_at", session.expiresAt)
        }
    }

    private suspend fun tryReconnectFirebase(): Boolean {
        return try {
            // Firebase 연결 테스트
            auth.currentUser?.reload()?.await()
            auth.currentUser != null
        } catch (e: Exception) {
            false
        }
    }

    private fun proceedWithCachedSession(session: SessionCache) {
        // UI 상태 업데이트 (오프라인 모드 표시)
        _loginState.value = LoginState.OfflineMode(session)
    }
}
```

**Firestore 작업 큐 (오프라인 작업 저장)**
```kotlin
// 새 파일: data/OfflineActionQueue.kt
sealed class OfflineAction {
    data class AcceptCall(val callId: String, val timestamp: Long) : OfflineAction()
    data class RejectCall(val callId: String, val timestamp: Long) : OfflineAction()
    data class CompleteCall(val callId: String, val fare: Int, val timestamp: Long) : OfflineAction()
}

object OfflineActionQueue {
    private val queue = mutableListOf<OfflineAction>()

    fun enqueue(action: OfflineAction) {
        queue.add(action)
        saveToPrefs()
    }

    suspend fun syncWhenOnline(firestore: FirebaseFirestore) {
        queue.forEach { action ->
            try {
                when (action) {
                    is OfflineAction.AcceptCall -> {
                        firestore.collection("calls").document(action.callId)
                            .update("status", "ACCEPTED", "acceptedAt", action.timestamp)
                            .await()
                    }
                    // ...
                }
            } catch (e: Exception) {
                // 실패 시 큐에 유지
            }
        }
        queue.clear()
        saveToPrefs()
    }
}
```

**예상 작업 시간**: 8시간

---

#### 3.3 Firestore 읽기 비용 최적화 미흡 ⭐⭐

**심각도**: MEDIUM-HIGH
**비용 영향**: 월 수만원 추가 가능

**개선된 부분** (이미 완료):
- ✅ 리스너 제거 후 1회 조회로 전환 (월 $414 → $0.03 절감)
- ✅ 낙관적 업데이트 적용

**여전히 남은 문제**:

**문제 1: 포인트 조회 중복**
- `HomeScreen.kt:312-343` (UI에서 직접 조회)
- `DriverViewModel.kt:865-887` (ViewModel에서 조회)
- 같은 전화번호에 대해 2번 조회

```kotlin
// ❌ HomeScreen.kt:312-343
LaunchedEffect(phoneNumber) {
    firestore.collection("customerInfo")
        .whereEqualTo("phoneNumber", phoneNumber)
        .get()  // 1회 조회
        .await()
}

// ❌ DriverViewModel.kt:865-887
suspend fun getCustomerPoints(phoneNumber: String) {
    firestore.collection("customerInfo")
        .whereEqualTo("phoneNumber", phoneNumber)
        .get()  // 2회 조회 (중복!)
        .await()
}
```

**문제 2: 사무실 정보 매번 조회**
- `HomeScreen.kt:56-75`
- 앱 시작 시마다 사무실 이름 조회
- 변경 빈도가 낮은데도 캐싱 안 함

```kotlin
// ❌ HomeScreen.kt:56-75
LaunchedEffect(Unit) {  // 매번 실행
    val officeDoc = firestore.collection("offices")
        .document(officeId)
        .get()
        .await()

    officeName = officeDoc.getString("name") ?: "사무실"
}
```

**해결 방안**:

**Repository 패턴 도입**
```kotlin
// 새 파일: data/repository/CustomerRepository.kt
@Singleton
class CustomerRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    // 메모리 캐시 (앱 실행 중 유지)
    private val customerCache = mutableMapOf<String, CustomerInfo?>()

    suspend fun getCustomerInfo(phoneNumber: String, regionId: String, officeId: String): CustomerInfo? {
        // 1. 캐시 확인
        val cached = customerCache[phoneNumber]
        if (cached != null) {
            return cached
        }

        // 2. Firestore 조회 (1회만)
        val result = firestore.collection("regions/$regionId/offices/$officeId/customerInfo")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .await()

        // 3. 캐싱
        val customerInfo = if (!result.isEmpty) {
            CustomerInfo(
                phoneNumber = phoneNumber,
                points = result.documents[0].getLong("pointsBalance")?.toInt() ?: 0,
                grade = result.documents[0].getString("grade") ?: "BRONZE"
            )
        } else {
            null
        }

        customerCache[phoneNumber] = customerInfo
        return customerInfo
    }

    fun invalidateCache(phoneNumber: String) {
        customerCache.remove(phoneNumber)
    }
}

// ViewModel에서 사용
class DriverViewModel @Inject constructor(
    private val customerRepository: CustomerRepository
) : ViewModel() {

    suspend fun loadCustomerPoints(phoneNumber: String) {
        val customerInfo = customerRepository.getCustomerInfo(phoneNumber, regionId, officeId)
        // UI 업데이트
    }
}

// HomeScreen에서는 ViewModel 데이터만 관찰
@Composable
fun HomeScreen(viewModel: DriverViewModel) {
    val customerPoints by viewModel.customerPoints.collectAsState()

    // Firestore 직접 호출 제거
}
```

**사무실 정보 캐싱**
```kotlin
// 새 파일: data/repository/OfficeRepository.kt
@Singleton
class OfficeRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("office_cache", Context.MODE_PRIVATE)

    suspend fun getOfficeName(regionId: String, officeId: String): String {
        // 1. SharedPreferences 캐시 확인
        val cached = prefs.getString("office_${officeId}_name", null)
        if (cached != null) {
            return cached
        }

        // 2. Firestore 조회
        val officeDoc = firestore.collection("regions/$regionId/offices")
            .document(officeId)
            .get()
            .await()

        val officeName = officeDoc.getString("name") ?: "사무실"

        // 3. 캐싱 (7일간 유효)
        prefs.edit {
            putString("office_${officeId}_name", officeName)
            putLong("office_${officeId}_cache_time", System.currentTimeMillis())
        }

        return officeName
    }
}
```

**예상 작업 시간**: 6시간

---

### 🟡 아키텍처 및 유지보수 문제 (MEDIUM Priority)

#### 3.4 코드 중복 (여러 곳) ⭐⭐

**심각도**: MEDIUM
**유지보수 비용**: 높음

**발견 위치**:

**중복 1: 포인트 조회 로직**
- `HomeScreen.kt:312-343`
- `DriverViewModel.kt:865-887`

**중복 2: 위치 권한 처리**
- `TripPreparationScreen.kt:159-188`
- `HomeScreen.kt` (여러 곳)

**중복 3: Firestore 경로 생성**
```kotlin
// 여러 파일에서 반복
val path = "regions/$regionId/offices/$officeId/calls/$callId"
val path = "regions/$regionId/offices/$officeId/designated_drivers/$driverId"
```

**해결 방안**:

문제 3.3의 Repository 패턴 적용으로 대부분 해결됩니다.

**예상 작업 시간**: Repository 패턴에 포함 (추가 작업 없음)

---

#### 3.5 하드코딩된 값들 ⭐

**심각도**: LOW-MEDIUM

**발견 위치**:

**색상 하드코딩**: `TripPreparationScreen.kt:53-56`
```kotlin
private val DeepYellow = Color(0xFFFFB000)
private val DarkBackground = Color(0xFF1A1A1A)
private val LightGray = Color(0xFF9E9E9E)
```

**Enum 문자열 직접 비교**: `LoginViewModel.kt:128`
```kotlin
if (status == "APPROVED") {  // ❌ 문자열 하드코딩
    // ...
}

// ✅ 개선
enum class DriverApprovalStatus {
    PENDING, APPROVED, REJECTED
}

if (status == DriverApprovalStatus.APPROVED) {
    // ...
}
```

**해결 방안**:

```kotlin
// Theme.kt에 색상 통합
val DriverAppColors = lightColorScheme(
    primary = Color(0xFFFFB000),
    background = Color(0xFF1A1A1A),
    surface = Color(0xFF2A2A2A),
    onSurface = Color(0xFF9E9E9E)
)

// 사용
@Composable
fun TripPreparationScreen() {
    val colors = MaterialTheme.colorScheme

    Box(modifier = Modifier.background(colors.background)) {
        // ...
    }
}
```

**예상 작업 시간**: 2시간

---

#### 3.6 ViewModel 비대화 (God Object) ⭐⭐

**심각도**: MEDIUM
**테스트 난이도**: 매우 높음

**문제점**:
- `DriverViewModel.kt`: **1049줄**
- 단일 ViewModel이 너무 많은 책임 보유:
  - 콜 관리 (수락/거절/완료)
  - 위치 관리
  - 포인트 관리
  - 정산 관리
  - 상태 관리
  - Firebase 리스너 관리

**영향**:
- 테스트 작성 어려움
- 버그 발생 시 원인 추적 복잡
- 코드 변경 시 Side Effect 예측 불가
- 새 기능 추가 시 기존 코드에 영향

**해결 방안**:

```kotlin
// 분리된 ViewModel들

// 1. 인증 관련
class DriverAuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    // 로그인, 로그아웃, 자동 로그인
}

// 2. 콜 관리
class CallManagementViewModel @Inject constructor(
    private val callRepository: CallRepository
) : ViewModel() {
    fun acceptCall(callId: String)
    fun rejectCall(callId: String)
    fun listenForNewCalls()
}

// 3. 운행 관리
class TripViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {
    fun startTrip()
    fun completeTrip()
    fun updateLocation()
}

// 4. 정산
class SettlementViewModel @Inject constructor(
    private val settlementRepository: SettlementRepository,
    private val pointRepository: PointRepository
) : ViewModel() {
    fun calculateFare()
    fun applyPoints()
    fun finalizeSettlement()
}

// 5. 포인트
class PointViewModel @Inject constructor(
    private val pointRepository: PointRepository
) : ViewModel() {
    fun getCustomerPoints(phoneNumber: String)
    fun usePoints(amount: Int)
    fun earnPoints(amount: Int)
}
```

**HomeScreen에서 여러 ViewModel 사용**
```kotlin
@Composable
fun HomeScreen(
    callViewModel: CallManagementViewModel = hiltViewModel(),
    tripViewModel: TripViewModel = hiltViewModel(),
    settlementViewModel: SettlementViewModel = hiltViewModel(),
    pointViewModel: PointViewModel = hiltViewModel()
) {
    val activeCall by callViewModel.activeCall.collectAsState()
    val tripStatus by tripViewModel.status.collectAsState()

    // ...
}
```

**예상 작업 시간**: 12시간

---

#### 3.7 에러 처리 불충분 ⭐

**심각도**: LOW-MEDIUM
**사용자 경험**: 나쁨

**발견 위치**:

**문제 1**: `TripPreparationScreen.kt:149`
```kotlin
try {
    // 위치 가져오기
} catch (e: Exception) {
    departure = "위치를 가져올 수 없음"  // ❌ 사용자가 왜 실패했는지 모름
}
```

**문제 2**: `HomeScreen.kt:339`
```kotlin
.addOnFailureListener { e ->
    Log.e("PointCheck", "포인트 확인 실패", e)  // ❌ 로그만 찍고 끝
}
```

**문제 3**: 네트워크 에러 처리 없음
```kotlin
// Firebase 호출 시 네트워크 에러 무시
firestore.collection("calls").get().await()  // 실패 시?
```

**해결 방안**:

**UI 상태에 에러 추가**
```kotlin
data class DriverScreenUiState(
    val driverStatus: DriverStatus = DriverStatus.OFFLINE,
    val activeCall: CallInfo? = null,
    val errorMessage: String? = null,  // 추가
    val isLoading: Boolean = false      // 추가
)
```

**에러 메시지 표시**
```kotlin
@Composable
fun HomeScreen(viewModel: DriverViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // 에러 스낵바
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            // Snackbar 표시
            scaffoldState.snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }
}
```

**구체적인 에러 메시지**
```kotlin
try {
    // 위치 가져오기
} catch (e: SecurityException) {
    _uiState.update { it.copy(
        errorMessage = "위치 권한이 필요합니다. 설정에서 권한을 허용해주세요."
    )}
} catch (e: IOException) {
    _uiState.update { it.copy(
        errorMessage = "네트워크 연결을 확인해주세요."
    )}
} catch (e: Exception) {
    _uiState.update { it.copy(
        errorMessage = "위치를 가져올 수 없습니다: ${e.localizedMessage}"
    )}
}
```

**예상 작업 시간**: 6시간

---

#### 3.8 메모리 누수 가능성 ⭐

**심각도**: LOW-MEDIUM
**장기 실행 영향**: 크래시 가능

**발견 위치**:

**문제 1**: `TripPreparationScreen.kt:119` (SpeechRecognizer)
```kotlin
val speechRecognizer = remember {
    SpeechRecognizer.createSpeechRecognizer(context)
}

DisposableEffect(Unit) {
    onDispose {
        speechRecognizer.destroy()  // ✅ 정리됨 (양호)
    }
}

// 하지만 voiceHelper는?
val voiceHelper = remember { VoiceInputHelper(context) }
// onDispose에서 정리 안 됨
```

**문제 2**: `DriverForegroundService.kt:44`
```kotlin
private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

override fun onDestroy() {
    super.onDestroy()
    serviceScope.cancel()  // ✅ onDestroy에서 정리
}

// 하지만 onStartCommand가 여러 번 호출되면?
override fun onStartCommand(...): Int {
    serviceScope.launch {  // ← 누적될 수 있음
        // ...
    }
}
```

**해결 방안**:

**VoiceInputHelper 정리 추가**
```kotlin
// VoiceInputHelper.kt
class VoiceInputHelper(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}

// TripPreparationScreen.kt
DisposableEffect(Unit) {
    onDispose {
        speechRecognizer.destroy()
        voiceHelper.cleanup()  // 추가
    }
}
```

**ServiceScope 재생성 방지**
```kotlin
private var serviceScope: CoroutineScope? = null

override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    // 기존 scope 취소
    serviceScope?.cancel()

    // 새 scope 생성
    serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    serviceScope?.launch {
        // ...
    }

    return START_NOT_STICKY
}
```

**예상 작업 시간**: 2시간

---

### 🟢 UI/UX 및 성능 문제 (LOW Priority)

#### 3.9 UI 응답성 문제 ⭐

**심각도**: LOW
**사용자 경험**: 불편

**문제**: `TripPreparationScreen.kt:252-258` (주소 검색 debounce 없음)

```kotlin
// ❌ 현재: 타이핑 할 때마다 API 호출
TextField(
    value = departure,
    onValueChange = {
        departure = it
        if (it.length >= 2) {
            addressSearchHelper.searchAddress(it) { results ->
                // Kakao API 호출 (매번!)
            }
        }
    }
)
```

**문제점**:
- "강남역" 입력 시 총 4번 API 호출 ("강남", "강남ㅇ", "강남역")
- Kakao API 할당량 낭비
- 네트워크 트래픽 증가

**해결 방안**:

```kotlin
// 새 파일: util/Debouncer.kt
class Debouncer(private val delayMillis: Long = 300) {
    private var job: Job? = null

    fun debounce(scope: CoroutineScope, action: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(delayMillis)
            action()
        }
    }
}

// TripPreparationScreen.kt
val debouncer = remember { Debouncer(300) }
val scope = rememberCoroutineScope()

TextField(
    value = departure,
    onValueChange = { newValue ->
        departure = newValue

        if (newValue.length >= 2) {
            debouncer.debounce(scope) {
                addressSearchHelper.searchAddress(newValue) { results ->
                    searchResults = results
                }
            }
        }
    }
)
```

**예상 작업 시간**: 1시간

---

#### 3.10 접근성 문제 ⭐

**심각도**: LOW
**대상 사용자**: 시각 장애인

**문제점**:
- `contentDescription` 누락된 Icon 다수
- TalkBack 지원 미흡
- 최소 터치 영역 미달 (일부 버튼 48dp 미만)

**발견 위치**:
```kotlin
// HomeScreen.kt
Icon(
    imageVector = Icons.Default.Phone,
    contentDescription = null  // ❌
)

IconButton(
    onClick = { /* ... */ },
    modifier = Modifier.size(32.dp)  // ❌ 48dp 미만
) {
    Icon(...)
}
```

**해결 방안**:

```kotlin
// ✅ contentDescription 추가
Icon(
    imageVector = Icons.Default.Phone,
    contentDescription = "전화 걸기"
)

// ✅ 최소 터치 영역 보장
IconButton(
    onClick = { /* ... */ },
    modifier = Modifier.size(48.dp)  // 최소 48dp
) {
    Icon(
        modifier = Modifier.size(24.dp),  // 아이콘은 작게
        ...
    )
}

// ✅ Semantics 추가
Text(
    text = "10,000원",
    modifier = Modifier.semantics {
        contentDescription = "요금 만원"
    }
)
```

**예상 작업 시간**: 3시간

---

#### 3.11 성능 최적화 미흡 ⭐

**심각도**: LOW

**문제 1**: `HomeScreen.kt:56-75` (LaunchedEffect 매번 실행)
```kotlin
LaunchedEffect(Unit) {  // ❌ Unit은 변하지 않으므로 한 번만 실행
    // 하지만 HomeScreen이 재구성될 때마다 실행될 수 있음
}
```

**문제 2**: Firestore 쿼리 최적화 가능
```kotlin
// ❌ 모든 필드 가져오기
firestore.collection("calls")
    .document(callId)
    .get()
    .await()

// ✅ 필요한 필드만 가져오기
firestore.collection("calls")
    .document(callId)
    .get()
    .await()
    .let { doc ->
        CallInfo(
            id = doc.id,
            phoneNumber = doc.getString("phoneNumber") ?: "",
            // 필요한 필드만
        )
    }
```

**해결 방안**:

```kotlin
// LaunchedEffect key 명시
LaunchedEffect(officeId) {  // officeId 변경 시만 실행
    loadOfficeName()
}

// Firestore 쿼리 최적화는 이미 Repository 패턴에서 해결됨
```

**예상 작업 시간**: 2시간

---

#### 3.12 테스트 코드 부재 ⭐

**심각도**: MEDIUM
**장기 유지보수**: 매우 위험

**문제점**:
- Unit Test 0개
- UI Test 0개
- Integration Test 0개
- 리팩토링 시 회귀 버그 위험 높음

**해결 방안**:

```kotlin
// test/java/.../viewmodel/DriverViewModelTest.kt
@HiltAndroidTest
class DriverViewModelTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    private lateinit var viewModel: DriverViewModel

    @Before
    fun setup() {
        hiltRule.inject()
        viewModel = DriverViewModel(...)
    }

    @Test
    fun `acceptCall updates UI state optimistically`() = runTest {
        // Given
        val call = CallInfo(id = "call123", phoneNumber = "01012345678")

        // When
        viewModel.acceptCall(call.id)

        // Then
        val uiState = viewModel.uiState.value
        assertEquals(CallStatus.ACCEPTED, uiState.activeCall?.statusEnum)
    }

    @Test
    fun `completeCall calculates points correctly`() = runTest {
        // Given
        val fare = 10000
        val customerGrade = "SILVER"  // 5% 적립

        // When
        viewModel.completeCall(fare)

        // Then
        val earnedPoints = viewModel.earnedPoints.value
        assertEquals(500, earnedPoints)  // 10000 * 0.05
    }
}
```

**Widget 테스트**
```kotlin
// androidTest/java/.../ui/NewCallPopupTest.kt
@HiltAndroidTest
class NewCallPopupTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun newCallPopup_displaysCorrectly() {
        // Given
        val callInfo = CallInfo(
            id = "call123",
            phoneNumber = "010-1234-5678",
            customerName = "홍길동"
        )

        // When
        composeTestRule.setContent {
            NewCallPopup(
                callInfo = callInfo,
                onAccept = {},
                onReject = {}
            )
        }

        // Then
        composeTestRule.onNodeWithText("새로운 콜").assertIsDisplayed()
        composeTestRule.onNodeWithText("홍길동").assertIsDisplayed()
        composeTestRule.onNodeWithText("수락").assertIsDisplayed()
    }
}
```

**예상 작업 시간**: 16시간 (Unit Test만)

---

## 4. Flutter 전환 시 개선 방안

### 4.1 크로스 플랫폼 장점 활용

#### 왜 Flutter인가?

**현재 상황**:
- Android 앱만 존재
- iOS 버전 없음
- 웹 관리자 페이지 없음

**Flutter 도입 시**:
```dart
// 단일 코드베이스로
1. Android 앱 ✅
2. iOS 앱 ✅ (새로 추가)
3. Web 관리자 페이지 ✅ (새로 추가)
4. macOS/Windows 데스크톱 앱 ✅ (선택사항)
```

**비용 절감**:
| 항목 | Android 유지 | Flutter 전환 |
|------|-------------|-------------|
| 개발 속도 | Android만 | Android + iOS 동시 |
| 유지보수 | 1개 코드베이스 | 1개 코드베이스 (동일) |
| 기능 추가 | Android만 수정 | 전 플랫폼 동시 적용 |
| 버그 수정 | Android만 | 전 플랫폼 동시 |
| 인력 | Android 개발자 필요 | Flutter 개발자 1명으로 충분 |

---

### 4.2 상태 관리 개선

**현재 (Android)**:
```kotlin
// StateFlow + Hilt
class DriverViewModel @Inject constructor(...) : ViewModel() {
    private val _uiState = MutableStateFlow(DriverScreenUiState())
    val uiState: StateFlow<DriverScreenUiState> = _uiState.asStateFlow()
}
```

**Flutter 전환 후 (Riverpod)**:
```dart
// Riverpod - 더 간결하고 테스트하기 쉬움
final driverProvider = StateNotifierProvider<DriverNotifier, DriverState>((ref) {
  return DriverNotifier(
    ref.read(firestoreRepositoryProvider),
    ref.read(authRepositoryProvider),
  );
});

class DriverNotifier extends StateNotifier<DriverState> {
  DriverNotifier(this._firestoreRepo, this._authRepo) : super(DriverState.initial());

  final FirestoreRepository _firestoreRepo;
  final AuthRepository _authRepo;

  Future<void> acceptCall(String callId) async {
    // 낙관적 업데이트
    state = state.copyWith(
      activeCall: state.activeCall?.copyWith(status: CallStatus.accepted),
    );

    try {
      await _firestoreRepo.acceptCall(callId);
    } catch (e) {
      // 실패 시 롤백
      state = state.copyWith(error: e.toString());
    }
  }
}

// UI에서 사용
class HomeScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final driverState = ref.watch(driverProvider);

    return Scaffold(
      body: driverState.activeCall != null
          ? CallActiveWidget(call: driverState.activeCall!)
          : WaitingWidget(),
    );
  }
}
```

---

### 4.3 아키텍처 정리 (Clean Architecture)

**현재 구조**:
```
ui/ → viewmodel/ → Firebase 직접 호출
```

**Flutter Clean Architecture**:
```
presentation/     (UI + State Management)
├── screens/
│   ├── home_screen.dart
│   ├── login_screen.dart
│   └── trip_preparation_screen.dart
├── widgets/
│   ├── new_call_popup.dart
│   └── driver_button.dart
└── providers/
    ├── driver_provider.dart
    └── auth_provider.dart

domain/          (비즈니스 로직 - Firebase 독립적)
├── entities/
│   ├── driver.dart
│   ├── call.dart
│   └── customer.dart
├── usecases/
│   ├── accept_call_usecase.dart
│   ├── complete_trip_usecase.dart
│   └── calculate_points_usecase.dart
└── repositories/ (인터페이스)
    ├── auth_repository.dart
    ├── call_repository.dart
    └── customer_repository.dart

data/            (데이터 소스 구현)
├── models/
│   ├── driver_model.dart  # Firestore ↔ Entity 변환
│   └── call_model.dart
├── repositories/ (구현)
│   ├── firebase_auth_repository.dart
│   ├── firestore_call_repository.dart
│   └── firestore_customer_repository.dart
└── datasources/
    ├── remote/   (Firebase, Kakao API)
    │   ├── firebase_datasource.dart
    │   └── kakao_address_datasource.dart
    └── local/    (로컬 저장소)
        ├── hive_datasource.dart
        └── secure_storage_datasource.dart
```

**장점**:
1. **테스트 용이**: 각 레이어를 독립적으로 테스트
2. **유지보수성**: 비즈니스 로직과 UI 완전 분리
3. **확장성**: 새 데이터 소스 추가 용이 (예: REST API)
4. **Firebase 의존성 제거**: Repository 인터페이스만 교체하면 다른 백엔드로 전환 가능

---

### 4.4 Firebase 의존성 완화

**현재 문제**:
- Firebase Auth 없으면 앱 사용 불가
- Firestore 장애 시 모든 기능 마비

**Flutter 해결책**:

```dart
// domain/repositories/auth_repository.dart (인터페이스)
abstract class AuthRepository {
  Future<Either<Failure, User>> login(String email, String password);
  Future<Either<Failure, User>> getCurrentUser();
  Future<void> logout();
}

// data/repositories/firebase_auth_repository.dart (Firebase 구현)
class FirebaseAuthRepository implements AuthRepository {
  final FirebaseAuth _firebaseAuth;
  final HiveDataSource _localCache;

  @override
  Future<Either<Failure, User>> login(String email, String password) async {
    try {
      // 1. Firebase 로그인 시도
      final credential = await _firebaseAuth.signInWithEmailAndPassword(
        email: email,
        password: password,
      );

      // 2. 로컬 캐시에 저장
      await _localCache.saveUser(User.fromFirebase(credential.user!));

      return Right(User.fromFirebase(credential.user!));
    } on FirebaseAuthException catch (e) {
      // 3. Firebase 실패 시 로컬 캐시 확인
      final cachedUser = await _localCache.getUser(email);
      if (cachedUser != null && cachedUser.isValid()) {
        return Right(cachedUser);
      }

      return Left(AuthFailure(e.message ?? 'Login failed'));
    }
  }
}

// data/repositories/mock_auth_repository.dart (테스트용)
class MockAuthRepository implements AuthRepository {
  @override
  Future<Either<Failure, User>> login(String email, String password) async {
    await Future.delayed(Duration(seconds: 1));
    return Right(User(id: '123', email: email));
  }
}

// main.dart에서 주입
final authRepositoryProvider = Provider<AuthRepository>((ref) {
  if (kDebugMode && useMockData) {
    return MockAuthRepository();
  }
  return FirebaseAuthRepository(
    FirebaseAuth.instance,
    ref.read(hiveDataSourceProvider),
  );
});
```

---

### 4.5 상태 복원 개선

**현재 문제**:
- 앱 백그라운드 → 종료 시 상태 손실
- 재시작 시 처음부터 다시 시작

**Flutter 해결책**:

```dart
// 자동 상태 복원
class HomeScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen>
    with WidgetsBindingObserver, AutomaticKeepAliveClientMixin {

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    switch (state) {
      case AppLifecycleState.resumed:
        // 앱 복귀 시 상태 동기화
        ref.read(driverProvider.notifier).refreshState();
        break;
      case AppLifecycleState.paused:
        // 백그라운드 진입 시 상태 저장
        ref.read(driverProvider.notifier).saveState();
        break;
      case AppLifecycleState.inactive:
      case AppLifecycleState.detached:
        break;
    }
  }

  @override
  bool get wantKeepAlive => true;  // 상태 유지

  @override
  Widget build(BuildContext context) {
    super.build(context);  // wantKeepAlive를 위해 필요
    // ...
  }
}
```

---

### 4.6 테스트 가능성 향상

**Flutter의 테스트 우선 설계**:

```dart
// 1. Unit Test (비즈니스 로직)
test('AcceptCallUseCase updates call status', () async {
  // Given
  final mockCallRepo = MockCallRepository();
  final usecase = AcceptCallUseCase(mockCallRepo);

  // When
  final result = await usecase.execute('call123');

  // Then
  expect(result.isRight(), true);
  verify(mockCallRepo.updateCallStatus('call123', CallStatus.accepted)).called(1);
});

// 2. Widget Test (UI 단위 테스트)
testWidgets('NewCallPopup displays call info', (WidgetTester tester) async {
  // Given
  final call = Call(
    id: 'call123',
    phoneNumber: '010-1234-5678',
    customerName: '홍길동',
  );

  // When
  await tester.pumpWidget(
    ProviderScope(
      child: MaterialApp(
        home: NewCallPopup(call: call),
      ),
    ),
  );

  // Then
  expect(find.text('새로운 콜'), findsOneWidget);
  expect(find.text('홍길동'), findsOneWidget);
  expect(find.text('010-1234-5678'), findsOneWidget);
  expect(find.text('수락'), findsOneWidget);
  expect(find.text('거절'), findsOneWidget);
});

// 3. Integration Test (E2E 테스트)
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('Full call acceptance flow', (WidgetTester tester) async {
    // 1. 앱 실행
    await tester.pumpWidget(MyApp());

    // 2. 로그인
    await tester.enterText(find.byKey(Key('email_field')), 'driver@test.com');
    await tester.enterText(find.byKey(Key('password_field')), 'password123');
    await tester.tap(find.text('로그인'));
    await tester.pumpAndSettle();

    // 3. 홈 화면 확인
    expect(find.text('대기 중'), findsOneWidget);

    // 4. 새 콜 수신 (Firebase 시뮬레이션)
    // ...

    // 5. 콜 수락
    await tester.tap(find.text('수락'));
    await tester.pumpAndSettle();

    // 6. 상태 변화 확인
    expect(find.text('운행 준비'), findsOneWidget);
  });
}
```

---

### 4.7 UI 일관성 개선

**현재 문제**:
- 하드코딩된 색상, 크기
- 재사용 불가능한 위젯

**Flutter 해결책**:

```dart
// lib/core/theme/app_theme.dart
class AppTheme {
  static ThemeData driverTheme = ThemeData(
    primaryColor: Color(0xFFFFB000),
    scaffoldBackgroundColor: Color(0xFF1A1A1A),
    colorScheme: ColorScheme.dark(
      primary: Color(0xFFFFB000),
      secondary: Color(0xFF03DAC6),
      background: Color(0xFF1A1A1A),
      surface: Color(0xFF2A2A2A),
    ),
    textTheme: TextTheme(
      headlineLarge: TextStyle(
        fontSize: 32,
        fontWeight: FontWeight.bold,
        color: Colors.white,
      ),
      bodyLarge: TextStyle(
        fontSize: 16,
        color: Color(0xFF9E9E9E),
      ),
    ),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: Color(0xFFFFB000),
        minimumSize: Size(double.infinity, 50),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(25),
        ),
      ),
    ),
  );
}

// lib/presentation/widgets/driver_button.dart (재사용 위젯)
class DriverButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final bool isLoading;
  final Color? backgroundColor;

  const DriverButton({
    required this.text,
    this.onPressed,
    this.isLoading = false,
    this.backgroundColor,
  });

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: isLoading ? null : onPressed,
      style: backgroundColor != null
          ? ElevatedButton.styleFrom(backgroundColor: backgroundColor)
          : null,
      child: isLoading
          ? SizedBox(
              width: 20,
              height: 20,
              child: CircularProgressIndicator(strokeWidth: 2),
            )
          : Text(text),
    );
  }
}

// 사용
DriverButton(
  text: '수락',
  onPressed: () => ref.read(driverProvider.notifier).acceptCall(callId),
  isLoading: isAccepting,
)
```

---

### 4.8 비밀번호 저장 보안 강화

**현재 문제**:
- SharedPreferences 평문 저장

**Flutter 해결책**:

```dart
// flutter_secure_storage 사용
dependencies:
  flutter_secure_storage: ^9.0.0

// lib/data/datasources/local/secure_storage_datasource.dart
class SecureStorageDataSource {
  final FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(encryptedSharedPreferences: true),
    iOptions: IOSOptions(accessibility: KeychainAccessibility.first_unlock),
  );

  Future<void> savePassword(String password) async {
    await _storage.write(key: 'password', value: password);
  }

  Future<String?> getPassword() async {
    return await _storage.read(key: 'password');
  }

  Future<void> deletePassword() async {
    await _storage.delete(key: 'password');
  }
}

// 사용
class AuthNotifier extends StateNotifier<AuthState> {
  final SecureStorageDataSource _secureStorage;

  Future<void> enableAutoLogin(String email, String password) async {
    await _secureStorage.savePassword(password);
    // 비밀번호는 암호화되어 저장됨 (AES-256)
  }

  Future<void> autoLogin() async {
    final password = await _secureStorage.getPassword();
    if (password != null) {
      // 자동 로그인 시도
    }
  }
}
```

---

### 4.9 오프라인 지원 강화

**현재 문제**:
- Firebase 연결 필수
- 오프라인 시 앱 사용 불가

**Flutter 해결책**:

```dart
// Hive (로컬 NoSQL DB) 사용
dependencies:
  hive: ^2.2.3
  hive_flutter: ^1.1.0

// lib/data/datasources/local/hive_datasource.dart
class HiveDataSource {
  late Box<CallModel> _callBox;
  late Box<DriverModel> _driverBox;

  Future<void> init() async {
    await Hive.initFlutter();
    _callBox = await Hive.openBox<CallModel>('calls');
    _driverBox = await Hive.openBox<DriverModel>('drivers');
  }

  // 콜 정보 로컬 저장
  Future<void> saveCall(CallModel call) async {
    await _callBox.put(call.id, call);
  }

  // 로컬에서 콜 조회
  CallModel? getCall(String id) {
    return _callBox.get(id);
  }

  // 모든 콜 가져오기
  List<CallModel> getAllCalls() {
    return _callBox.values.toList();
  }
}

// lib/data/repositories/hybrid_call_repository.dart
class HybridCallRepository implements CallRepository {
  final FirebaseDataSource _firebase;
  final HiveDataSource _hive;
  final Connectivity _connectivity;

  @override
  Future<Either<Failure, Call>> getCall(String id) async {
    // 1. 로컬 캐시 우선 조회
    final localCall = _hive.getCall(id);

    // 2. 온라인이면 Firebase 동기화
    final isOnline = await _connectivity.checkConnectivity() != ConnectivityResult.none;

    if (isOnline) {
      try {
        final remoteCall = await _firebase.getCall(id);
        await _hive.saveCall(remoteCall);  // 로컬 업데이트
        return Right(remoteCall.toEntity());
      } catch (e) {
        // Firebase 실패해도 로컬 캐시 사용
        if (localCall != null) {
          return Right(localCall.toEntity());
        }
        return Left(ServerFailure());
      }
    } else {
      // 오프라인이면 로컬만 사용
      if (localCall != null) {
        return Right(localCall.toEntity());
      }
      return Left(CacheFailure());
    }
  }
}

// 오프라인 작업 큐
class OfflineActionQueue {
  final HiveDataSource _hive;
  late Box<PendingAction> _actionBox;

  Future<void> enqueue(PendingAction action) async {
    await _actionBox.add(action);
  }

  Future<void> syncWhenOnline(FirebaseDataSource firebase) async {
    final actions = _actionBox.values.toList();

    for (final action in actions) {
      try {
        switch (action.type) {
          case ActionType.acceptCall:
            await firebase.acceptCall(action.callId);
            break;
          case ActionType.completeCall:
            await firebase.completeCall(action.callId, action.data);
            break;
        }
        await _actionBox.delete(action.key);  // 성공 시 제거
      } catch (e) {
        // 실패 시 큐에 유지
      }
    }
  }
}
```

---

### 4.10 성능 최적화

**Flutter의 자동 최적화**:

```dart
// 1. Lazy Loading (자동 지원)
ListView.builder(
  itemCount: calls.length,
  itemBuilder: (context, index) {
    return CallListItem(call: calls[index]);
  },
)

// 2. 이미지 캐싱
CachedNetworkImage(
  imageUrl: driver.photoUrl,
  placeholder: (context, url) => CircularProgressIndicator(),
  errorWidget: (context, url, error) => Icon(Icons.error),
)

// 3. Debounce (간단하게)
Timer? _debounce;

void onSearchChanged(String query) {
  if (_debounce?.isActive ?? false) _debounce!.cancel();

  _debounce = Timer(Duration(milliseconds: 300), () {
    searchAddress(query);
  });
}

// 4. Const 위젯 (성능 최적화)
const DriverButton(text: '수락')  // 재빌드 안 됨

// 5. Selector로 불필요한 재빌드 방지
Consumer(
  builder: (context, ref, child) {
    final activeCall = ref.watch(
      driverProvider.select((state) => state.activeCall),
    );
    // activeCall만 변경되면 재빌드
  },
)
```

---

## 5. 예상 작업 범위 및 시간

### 5.1 긴급 보안 패치 (현행 Android 앱)

| 작업 | 파일 | 예상 시간 | 우선순위 |
|------|------|----------|---------|
| 비밀번호 암호화 저장 | LoginViewModel.kt | 4시간 | 🔴 CRITICAL |
| Firebase 의존성 완화 (세션 캐싱) | LoginViewModel.kt, DriverViewModel.kt | 8시간 | 🔴 HIGH |
| Firestore 읽기 최적화 | Repository 신규 생성 | 6시간 | 🟡 MEDIUM |
| **소계** | | **18시간** | |

---

### 5.2 아키텍처 리팩토링 (현행 Android 앱)

| 작업 | 예상 시간 | 우선순위 |
|------|----------|---------|
| Repository 패턴 도입 | 16시간 | 🟡 MEDIUM |
| ViewModel 분리 (5개로) | 12시간 | 🟡 MEDIUM |
| 코드 중복 제거 | 8시간 | 🟢 LOW |
| 에러 처리 개선 | 6시간 | 🟡 MEDIUM |
| 하드코딩 제거 | 2시간 | 🟢 LOW |
| 메모리 누수 방지 | 2시간 | 🟢 LOW |
| **소계** | **46시간** | |

---

### 5.3 Flutter 전환 (신규 프로젝트)

#### Phase 1: 프로젝트 셋업 (1주)
| 작업 | 예상 시간 |
|------|----------|
| Flutter 프로젝트 생성 | 2시간 |
| 패키지 설정 (Firebase, Riverpod, Hive 등) | 6시간 |
| Clean Architecture 폴더 구조 생성 | 4시간 |
| CI/CD 파이프라인 (GitHub Actions) | 8시간 |
| **소계** | **20시간** |

#### Phase 2: Domain Layer (1주)
| 작업 | 예상 시간 |
|------|----------|
| Entity 정의 (Driver, Call, Customer 등) | 8시간 |
| Repository 인터페이스 정의 | 6시간 |
| UseCase 구현 (10개) | 20시간 |
| **소계** | **34시간** |

#### Phase 3: Data Layer (2주)
| 작업 | 예상 시간 |
|------|----------|
| Model 정의 (Firestore ↔ Entity 변환) | 12시간 |
| Firebase DataSource 구현 | 16시간 |
| Hive DataSource 구현 (오프라인) | 12시간 |
| Repository 구현 (6개) | 24시간 |
| **소계** | **64시간** |

#### Phase 4: Presentation Layer (3주)
| 작업 | 예상 시간 |
|------|----------|
| Theme 설정 | 4시간 |
| 공통 위젯 (Button, TextField 등) | 8시간 |
| 로그인/회원가입 화면 | 12시간 |
| 홈 화면 (상태별 5개 화면) | 40시간 |
| 운행 준비 화면 | 12시간 |
| 운행 중 화면 | 8시간 |
| 정산 화면 | 12시간 |
| QR 코드 화면 | 6시간 |
| **소계** | **102시간** |

#### Phase 5: 기능 구현 (2주)
| 작업 | 예상 시간 |
|------|----------|
| FCM 푸시 알림 | 12시간 |
| 음성 입력 (Flutter Speech) | 10시간 |
| 위치 서비스 | 12시간 |
| 주소 검색 (Kakao API) | 8시간 |
| QR 코드 생성 | 4시간 |
| 포인트 시스템 | 12시간 |
| **소계** | **58시간** |

#### Phase 6: 테스트 (2주)
| 작업 | 예상 시간 |
|------|----------|
| Unit Test (UseCase, Repository) | 24시간 |
| Widget Test (주요 화면) | 20시간 |
| Integration Test (E2E) | 16시간 |
| **소계** | **60시간** |

#### Phase 7: 디버깅 및 QA (2주)
| 작업 | 예상 시간 |
|------|----------|
| Android 디바이스 테스트 | 16시간 |
| iOS 디바이스 테스트 | 16시간 |
| 버그 수정 | 32시간 |
| 성능 최적화 | 12시간 |
| **소계** | **76시간** |

---

### 5.4 전체 작업 시간 요약

| 구분 | 세부 항목 | 예상 시간 | 기간 |
|------|----------|----------|------|
| **긴급 패치** | 보안 취약점 수정 | 18시간 | 2-3일 |
| **리팩토링** | 아키텍처 개선 | 46시간 | 1주 |
| **Flutter 전환** | | | |
| - Phase 1 | 프로젝트 셋업 | 20시간 | 1주 |
| - Phase 2 | Domain Layer | 34시간 | 1주 |
| - Phase 3 | Data Layer | 64시간 | 2주 |
| - Phase 4 | Presentation Layer | 102시간 | 3주 |
| - Phase 5 | 기능 구현 | 58시간 | 2주 |
| - Phase 6 | 테스트 | 60시간 | 2주 |
| - Phase 7 | 디버깅 & QA | 76시간 | 2주 |
| **Flutter 소계** | | **414시간** | **13주 (3개월)** |
| | | | |
| **총계** | | **478시간 (약 60인일)** | **약 4개월** |

---

## 6. 권장 사항

### 6.1 즉시 조치 (1주 이내)

#### 우선순위 1: 보안 패치 🔴
```
1. 비밀번호 암호화 저장 (4시간)
2. Firebase 의존성 완화 - 세션 캐싱 (8시간)
3. Firestore 읽기 최적화 (6시간)

총 18시간 = 2-3일 작업
```

**이유**:
- 비밀번호 평문 저장은 개인정보보호법 위반 가능
- Firebase 장애 시 앱 전체 마비
- Firestore 비용 절감 (월 수만원)

---

### 6.2 단기 개선 (1개월 이내)

#### 우선순위 2: 아키텍처 리팩토링 🟡
```
1. Repository 패턴 도입 (16시간)
2. ViewModel 분리 (12시간)
3. 에러 처리 개선 (6시간)
4. 코드 중복 제거 (8시간)

총 42시간 = 1주 작업
```

**이유**:
- 코드 가독성 향상
- 테스트 가능성 확보
- 신규 기능 추가 용이

---

### 6.3 중장기 계획 (3-6개월)

#### 옵션 A: Android 앱 유지
**적합한 경우**:
- iOS 지원 필요 없음
- 개발 리소스 제한적
- 빠른 기능 개선 필요

**작업**:
```
1. 긴급 보안 패치 (2-3일)
2. 아키텍처 리팩토링 (1주)
3. Unit Test 추가 (2주)

총 3-4주
```

---

#### 옵션 B: Flutter 전환 (권장)
**적합한 경우**:
- iOS 지원 필요 (고객 요청 있음)
- 웹 관리자 페이지 필요
- 장기 유지보수 비용 절감
- 더 나은 코드 품질 추구

**작업 순서**:
```
1. 긴급 보안 패치 (현행 앱 유지용) - 2-3일
2. Flutter 프로젝트 시작 - 3개월
3. 병행 운영 (Android 네이티브 + Flutter) - 1개월
4. Flutter 완전 전환 - 이후

총 4-5개월
```

---

### 6.4 의사결정 기준

#### Flutter 전환을 결정할 때 고려사항

| 항목 | Android 유지 | Flutter 전환 |
|------|-------------|-------------|
| **개발 기간** | 3-4주 (리팩토링) | 3개월 (신규 개발) |
| **초기 투자** | 낮음 | 높음 (60인일) |
| **장기 유지보수** | 높음 (2개 코드베이스) | 낮음 (1개 코드베이스) |
| **iOS 지원** | ❌ (별도 개발 필요) | ✅ (동시 지원) |
| **웹 지원** | ❌ | ✅ |
| **성능** | ⭐⭐⭐⭐⭐ (네이티브) | ⭐⭐⭐⭐ (준네이티브) |
| **개발자 채용** | Android 개발자 | Flutter 개발자 |
| **기술 스택** | Kotlin, Jetpack Compose | Dart, Flutter |
| **학습 곡선** | 없음 (현행 유지) | 중간 (2-3주) |

---

### 6.5 최종 권장 방안

#### 단계별 접근 (리스크 최소화)

**Phase 1: 긴급 패치 (즉시)**
```
- 비밀번호 암호화 (4시간)
- Firebase 세션 캐싱 (8시간)
- Firestore 최적화 (6시간)

목표: 보안 및 안정성 확보
기간: 1주
```

**Phase 2: 리팩토링 (1개월 후)**
```
- Repository 패턴 (16시간)
- ViewModel 분리 (12시간)
- 에러 처리 (6시간)

목표: 코드 품질 향상
기간: 1주
```

**Phase 3: Flutter 전환 검토 (2개월 후)**
```
- iOS 지원 필요성 재평가
- 웹 관리자 페이지 필요성 확인
- 개발 리소스 확보 여부

목표: 장기 전략 수립
기간: 협의
```

**Phase 4: Flutter 전환 실행 (필요 시)**
```
- 3개월 집중 개발
- Android 네이티브 앱 병행 운영
- 점진적 마이그레이션

목표: 크로스 플랫폼 전환
기간: 3-4개월
```

---

## 7. 부록

### 7.1 Flutter 패키지 목록

```yaml
dependencies:
  flutter:
    sdk: flutter

  # 상태 관리
  flutter_riverpod: ^2.4.9

  # Firebase
  firebase_core: ^2.24.2
  firebase_auth: ^4.15.3
  firebase_firestore: ^4.13.6
  firebase_messaging: ^14.7.9

  # 로컬 저장소
  hive: ^2.2.3
  hive_flutter: ^1.1.0
  flutter_secure_storage: ^9.0.0

  # 네트워크
  dio: ^5.4.0
  connectivity_plus: ^5.0.2

  # 위치
  geolocator: ^10.1.0
  geocoding: ^2.1.1

  # QR 코드
  qr_flutter: ^4.1.0

  # 음성
  speech_to_text: ^6.5.1

  # UI
  cached_network_image: ^3.3.0
  flutter_svg: ^2.0.9

  # 유틸
  intl: ^0.18.1
  equatable: ^2.0.5
  dartz: ^0.10.1

dev_dependencies:
  flutter_test:
    sdk: flutter

  # 테스트
  mockito: ^5.4.4
  build_runner: ^2.4.7

  # 코드 생성
  hive_generator: ^2.0.1

  # Lint
  flutter_lints: ^3.0.1
```

---

### 7.2 참고 자료

**Flutter 공식 문서**:
- https://flutter.dev/docs
- https://dart.dev/guides

**아키텍처 가이드**:
- Clean Architecture: https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- Flutter Clean Architecture: https://resocoder.com/flutter-clean-architecture-tdd/

**상태 관리**:
- Riverpod: https://riverpod.dev/

**테스트**:
- Flutter Testing: https://flutter.dev/docs/testing

---

## 📝 변경 이력

| 날짜 | 버전 | 변경 내용 | 작성자 |
|------|------|-----------|--------|
| 2025-01-17 | 1.0 | 초안 작성 | Claude |

---

**END OF DOCUMENT**

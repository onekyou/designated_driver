# Android 앱 개발 가이드

## 🚀 Android Studio 프로젝트 생성

### 1. 프로젝트 생성
1. Android Studio 열기
2. File → New → New Project
3. Empty Activity 선택
4. 설정:
   - Name: `DesignatedCustomer`
   - Package: `com.designated.customer`
   - Location: `C:\app_dev\designated_driver\customer_app\app`
   - Language: Kotlin
   - Minimum SDK: API 24

### 2. 의존성 추가
`app/build.gradle.kts`에 추가:

```kotlin
dependencies {
    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")

    // Compose
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.2")
    implementation("androidx.navigation:navigation-compose:2.7.5")
}
```

### 3. 핑거프린팅 매니저 구현

`util/FingerprintManager.kt`:
```kotlin
class FingerprintManager(private val context: Context) {

    fun collectFingerprint(): DeviceFingerprint {
        return DeviceFingerprint(
            androidId = getAndroidId(),
            deviceModel = Build.MODEL,
            osVersion = Build.VERSION.RELEASE,
            screenResolution = getScreenResolution(),
            timezone = TimeZone.getDefault().id,
            language = Locale.getDefault().language
        )
    }

    private fun getAndroidId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }
}
```

### 4. 어트리뷰션 서비스

`service/AttributionService.kt`:
```kotlin
class AttributionService {
    private val functions = Firebase.functions

    suspend fun matchAttribution(
        fingerprint: DeviceFingerprint,
        phoneNumber: String
    ): AttributionResult {
        val data = hashMapOf(
            "fingerprint" to fingerprint.toMap(),
            "phoneNumber" to phoneNumber,
            "deviceInfo" to collectDeviceInfo()
        )

        val result = functions
            .getHttpsCallable("matchAttribution")
            .call(data)
            .await()

        return AttributionResult.fromMap(result.data as Map<String, Any>)
    }
}
```

### 5. 메인 화면 구현

`presentation/ui/main/MainScreen.kt`:
```kotlin
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 사무실 정보
        Text(
            text = uiState.officeName,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 전화 걸기 버튼
        Button(
            onClick = { viewModel.callOffice() },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = "전화",
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "대리 호출",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // 위치 입력
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                TextField(
                    value = uiState.departure,
                    onValueChange = { viewModel.updateDeparture(it) },
                    label = { Text("출발지") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = uiState.destination,
                    onValueChange = { viewModel.updateDestination(it) },
                    label = { Text("도착지") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
```

## 🧪 테스트 방법

### 1. 랜딩 페이지 테스트
```bash
cd customer_app/landing
npm install
npm run dev
# 브라우저에서 http://localhost:3000/seoul01 접속
```

### 2. Firebase Functions 테스트
```bash
cd customer_app/functions
npm install
npm run serve
```

### 3. Android 앱 테스트
1. Android Studio에서 프로젝트 열기
2. 에뮬레이터 또는 실제 기기 연결
3. Run 버튼 클릭

## 📊 테스트 시나리오

### 시나리오 1: 랜딩 페이지 → 앱 설치
1. 랜딩 페이지 접속 (핑거프린팅 수집)
2. 앱 다운로드 버튼 클릭
3. 앱 설치 후 실행
4. 어트리뷰션 매칭 확인 (70점 이상)

### 시나리오 2: QR 스캔 (백업)
1. QR 코드 스캔
2. 랜딩 페이지로 리다이렉트
3. 앱 설치 후 어트리뷰션 확인

### 시나리오 3: 재귀속
1. 어트리뷰션 실패 (70점 미만)
2. 수동 사무실 선택
3. 관리자 승인

## ✅ 체크리스트

- [ ] 랜딩 페이지 접속 가능
- [ ] 핑거프린팅 데이터 Firebase 저장
- [ ] Android 앱 빌드 성공
- [ ] 전화번호 인증 작동
- [ ] 어트리뷰션 매칭 (70점 이상)
- [ ] 메인 화면 표시
- [ ] 전화 걸기 기능
- [ ] Firebase 콜 생성
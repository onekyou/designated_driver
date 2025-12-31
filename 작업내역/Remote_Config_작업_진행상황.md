# Remote Config 작업 진행상황 (2025-12-31)

## 완료된 작업 ✅

### 1. 패키지명 변경 (com.designated.customer → com.designated.customer.app)
**파일: `customer_app/app/app/build.gradle.kts`**
```kotlin
// Line 14
applicationId = "com.designated.customer.app"

// Line 17-18
versionCode = 4
versionName = "1.0.3"

// Line 93 - Remote Config 종속성 추가
implementation("com.google.firebase:firebase-config-ktx")
```

### 2. Firebase 설정 파일 교체
**파일: `customer_app/app/app/google-services.json`**
- 새 패키지명(`com.designated.customer.app`) 포함된 설정으로 교체 완료

### 3. 랜딩 페이지 업데이트
**파일: `customer_app/landing/app/download/page.tsx` (Line 46)**
```typescript
const packageId = 'com.designated.customer.app'
```

### 4. Release AAB 빌드 완료
- 위치: `customer_app/app/app/build/outputs/bundle/release/app-release.aab`
- 크기: 17.9 MB
- 버전: versionCode 4, versionName 1.0.3
- Play Store 업로드 준비 완료

---

## 진행 중인 작업 🔄

### 작업 1: OfficeSelectionScreen 동적 regions 로딩 구현

**파일: `customer_app/app/app/src/main/java/com/designated/customer/ui/office/OfficeSelectionScreen.kt`**

**현재 상태**: 홍천/양평 하드코딩
**목표**: Firebase regions 컬렉션에서 동적 조회

**필요한 코드 변경사항:**

1. **OfficeViewModel.kt에 Region 데이터 클래스 추가:**
```kotlin
data class Region(
    val id: String = "",
    val name: String = ""
)

data class OfficeSelectionState(
    val regions: List<Region> = emptyList(),
    val offices: List<Office> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)
```

2. **ViewModel에 loadRegions() 함수 추가:**
```kotlin
fun loadRegions() {
    viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true)
        try {
            val regionsSnapshot = firestore.collection("regions").get().await()
            val regions = regionsSnapshot.documents.mapNotNull { doc ->
                Region(
                    id = doc.id,
                    name = doc.getString("name") ?: doc.id
                )
            }.sortedBy { it.name }

            _state.value = _state.value.copy(
                regions = regions,
                isLoading = false
            )

            // 첫 번째 지역의 사무실 자동 로드
            if (regions.isNotEmpty()) {
                loadOffices(regions[0].id)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = e.message,
                isLoading = false
            )
        }
    }
}
```

3. **OfficeSelectionScreen.kt UI 수정:**
```kotlin
@Composable
fun OfficeSelectionScreen(
    onOfficeSelected: (String, String) -> Unit,
    viewModel: OfficeViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedRegionIndex by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.loadRegions()  // 추가
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 동적 TabRow로 변경
        if (state.regions.isNotEmpty()) {
            TabRow(selectedTabIndex = selectedRegionIndex) {
                state.regions.forEachIndexed { index, region ->
                    Tab(
                        selected = selectedRegionIndex == index,
                        onClick = {
                            selectedRegionIndex = index
                            viewModel.loadOffices(region.id)
                        }
                    ) {
                        Text(
                            text = region.name,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        // 기존 사무실 목록 UI 유지
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("오류: ${state.error}")
                }
            }
            else -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.offices) { office ->
                        OfficeItem(
                            office = office,
                            onClick = {
                                if (state.regions.isNotEmpty()) {
                                    onOfficeSelected(office.id, state.regions[selectedRegionIndex].id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
```

---

### 작업 2: MainActivity Remote Config 통합

**파일: `customer_app/app/app/src/main/java/com/designated/customer/MainActivity.kt`**

**필요한 코드 변경사항:**

1. **import 추가 (파일 상단):**
```kotlin
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
```

2. **MainActivity 클래스에 변수 추가:**
```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var remoteConfig: FirebaseRemoteConfig
    private var allowDirectInstall by mutableStateOf(false)

    // 기존 코드...
```

3. **onCreate()에 Remote Config 초기화 추가:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // 기존 Firebase 초기화 후에 추가
    remoteConfig = FirebaseRemoteConfig.getInstance()
    val configSettings = remoteConfigSettings {
        minimumFetchIntervalInSeconds = 3600  // 1시간
    }
    remoteConfig.setConfigSettingsAsync(configSettings)

    // 기본값 설정
    remoteConfig.setDefaultsAsync(mapOf(
        "allowDirectInstall" to false
    ))

    // Remote Config 가져오기
    remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
        if (task.isSuccessful) {
            allowDirectInstall = remoteConfig.getBoolean("allowDirectInstall")
            Log.d("RemoteConfig", "allowDirectInstall = $allowDirectInstall")
        }
    }

    // 기존 setContent { ... } 코드
```

4. **CustomerApp Composable 수정 (Line 550-584 부근):**
```kotlin
currentOfficeId == null || currentRegionId == null -> {
    if (allowDirectInstall) {
        // Direct install 허용 시: 사무실 선택 화면 표시
        OfficeSelectionScreen(
            onOfficeSelected = { officeId, regionId ->
                preferencesManager.saveOfficeInfo(officeId, regionId)
                currentOfficeId = officeId
                currentRegionId = regionId
            }
        )
    } else {
        // Direct install 불허 시: 기존 에러 화면
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "사무실 정보를 찾을 수 없습니다",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "QR 코드를 통해 앱을 다시 설치해주세요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

---

## 대기 중인 작업 ⏳

### Firebase Console에서 Remote Config 설정

1. Firebase Console 접속: https://console.firebase.google.com/
2. 프로젝트 선택: calldetector-5d61e
3. 좌측 메뉴: Remote Config 클릭
4. "매개변수 추가" 클릭:
   - **키**: `allowDirectInstall`
   - **데이터 유형**: Boolean
   - **기본값**: `false` (QR 전용 모드)
5. "변경사항 게시" 클릭

**나중에 직접 다운로드 허용 시:**
- 같은 페이지에서 `allowDirectInstall` 값을 `true`로 변경
- "변경사항 게시" 클릭
- 앱 업데이트 없이 1시간 내 모든 사용자에게 적용됨

---

## 기술적 문제 발생 내역 ⚠️

### File Writing 실패
- Write tool, Bash heredoc, Python, PowerShell 모두 실패
- Kotlin 특수문자($, ", ')가 쉘 quoting과 충돌
- **해결방법**: 위 코드를 수동으로 복사/붙여넣기하여 적용

---

## 작업 재개 가이드 📋

### 1단계: MainActivity Remote Config 통합 (최우선)
```bash
# 파일 열기
C:/app_dev/designated_driver/customer_app/app/app/src/main/java/com/designated/customer/MainActivity.kt
```
- 위 "작업 2" 섹션의 코드 적용
- 빌드 & 테스트

### 2단계: OfficeSelectionScreen 동적 regions (선택사항)
```bash
# 파일 열기
C:/app_dev/designated_driver/customer_app/app/app/src/main/java/com/designated/customer/ui/office/OfficeSelectionScreen.kt
```
- 위 "작업 1" 섹션의 코드 적용
- 현재 홍천/양평만 있어서 급하지 않음

### 3단계: Firebase Console 설정
- 위 "대기 중인 작업" 섹션 참조

### 4단계: 테스트
1. 앱 빌드: `./gradlew assembleDebug`
2. Remote Config = false → 에러 화면 확인
3. Firebase Console에서 true 변경
4. 앱 재시작 → 사무실 선택 화면 확인

### 5단계: Play Store 업로드
```bash
# AAB 위치 확인
C:/app_dev/designated_driver/customer_app/app/app/build/outputs/bundle/release/app-release.aab
```
- Play Console에 업로드
- 내부 테스트 트랙 권장

---

## 참고 사항

### Remote Config 작동 방식
- 앱 시작 시 Firebase에서 최신 설정 다운로드
- `minimumFetchIntervalInSeconds = 3600` → 1시간마다 확인
- 개발 중에는 이 값을 `0`으로 설정하여 즉시 반영 가능

### 현재 앱 동작
- **QR 코드 경로**: 랜딩페이지 → Play Store (Install Referrer) → 자동 사무실 연결 ✅
- **직접 다운로드 경로**: Play Store → 에러 화면 (allowDirectInstall=false)
- **향후 직접 다운로드**: Play Store → 사무실 선택 화면 (allowDirectInstall=true)

### 중요한 파일 위치
- MainActivity: `customer_app/app/app/src/main/java/com/designated/customer/MainActivity.kt`
- OfficeSelectionScreen: `customer_app/app/app/src/main/java/com/designated/customer/ui/office/OfficeSelectionScreen.kt`
- OfficeViewModel: `customer_app/app/app/src/main/java/com/designated/customer/ui/office/OfficeViewModel.kt`
- build.gradle.kts: `customer_app/app/app/build.gradle.kts`

---

**작업 우선순위:**
1. 🔴 MainActivity Remote Config 통합 (핵심 기능)
2. 🟡 Firebase Console 설정 (Remote Config 매개변수)
3. 🟢 OfficeSelectionScreen 동적 regions (선택사항, 현재 2개만 있음)

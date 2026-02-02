# 정산 시스템 재설계 - 세션 이어가기 문서

## 날짜: 2026-02-03

---

## 핵심 원칙 (절대 변경 불가)

1. **하나의 문서로 콜매니저와 기사가 공유 → 불일치 없앰**
2. **문서가 둘이 되더라도 동일한 계산 로직 유지**
3. **미지급금은 어떤 형태로든 저장되어서 유지되어야 함**

---

## 현재 미해결 문제들 (다음 세션에서 해결 필요)

### 문제 1: 기사앱 미수령금 음수 표시
- **현상**: 기사앱에서 미수령금이 -8000원으로 표시됨
- **의미**: 기사가 사무실에서 8000원을 받아야 함
- **필요한 작업**: UI에서 음수를 "사무실에서 받을 금액: 8,000원"으로 표시해야 함
- **관련 파일**: `driver_app/.../HistorySettlementScreen.kt`

### 문제 2: 콜매니저 미수령금 UI 안 보임
- **현상**: 업무마감 후 콜매니저에서 해당 기사의 미수령금 UI가 생성되지 않음
- **원인 추정**: carryOver 업데이트가 제대로 안 되거나, UI 조건 문제
- **관련 파일**:
  - `call_manager/.../SettlementViewModel.kt` (processCarryOverOnFinalize)
  - `call_manager/.../DriverSummaryScreen.kt` (carryOver UI 조건)

### 문제 3: 콜매니저 로그아웃 후 자동 로그인
- **현상**: 로그아웃 후 앱 아이콘 클릭 시 로그인 화면 없이 바로 대시보드로 이동
- **수정 시도**: MainActivity에서 auto_login 플래그 확인 추가
- **아직 테스트 필요**
- **관련 파일**: `call_manager/.../MainActivity.kt`

### 문제 4: 업무마감 시 기사 알림 안 감
- **현상**: 업무마감 시 로그인 중인 기사에게 알림이 가지 않음
- **원인**: 기능이 구현되지 않음
- **필요한 작업**: clearAllTrips() 또는 별도 함수에서 FCM 알림 전송 구현

---

## 완료된 수정 사항

### 1. 콜매니저 (SettlementViewModel.kt)

**A. 누적 로직 수정** - 양수/음수 모두 처리
```kotlin
// 변경 전: 양수일 때만 누적
if (todayUnpaid > 0) {
    processCarryOverOnFinalize(driverId, todayUnpaid.toLong())
}

// 변경 후: 양수/음수 모두 처리
val todayResult = cashReceived - driverShare
processCarryOverOnFinalize(driverId, todayResult.toLong())
```

**B. processCarryOverOnFinalize 수정**
- `transaction.update()` → `transaction.set()` + `SetOptions.merge()` 변경
- carryOver 필드가 없어도 생성되도록

**C. ratio Firestore 저장**
- offices 문서에서 depositRatio 읽기
- updateOfficeShareRatio()에서 Firestore에도 저장

### 2. 콜매니저 (MainActivity.kt)

**로그아웃 시 SharedPreferences 클리어**
```kotlin
onLogout = {
    val loginPrefs = getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
    loginPrefs.edit()
        .putBoolean("auto_login", false)
        .remove("email")
        .remove("password")
        .apply()
    auth.signOut()
    screenState = Screen.Login
}
```

**앱 시작 시 auto_login 플래그 확인 추가**
```kotlin
val autoLoginEnabled = loginPrefs.getBoolean("auto_login", false)
screenState = if (auth.currentUser == null || !autoLoginEnabled) {
    if (auth.currentUser != null && !autoLoginEnabled) {
        auth.signOut()
    }
    Screen.Login
} else {
    Screen.Dashboard
}
```

### 3. 기사앱 (DriverViewModel.kt)

**depositRatio StateFlow 추가**
```kotlin
private val _depositRatio = MutableStateFlow(60)
val depositRatio: StateFlow<Int> = _depositRatio.asStateFlow()
```

**TodaySettlement 데이터 클래스 추가**
```kotlin
data class TodaySettlement(
    val totalFare: Int = 0,
    val driverShare: Int = 0,
    val cashReceived: Int = 0,
    val realDeposit: Int = 0,
    val tripCount: Int = 0
)
```

**loadSettlementData() 함수 추가**
- offices에서 depositRatio + settlementLastCleared 읽기
- calls에서 마감 이후 내 완료된 콜 조회 → 로컬 계산

### 4. 기사앱 (HistorySettlementScreen.kt)

**SharedPreferences → Firestore 기반으로 변경**
```kotlin
val depositRatio by viewModel.depositRatio.collectAsStateWithLifecycle()
val todaySettlement by viewModel.todaySettlement.collectAsStateWithLifecycle()
```

**비율 조정 다이얼로그 → 비율 정보 다이얼로그 (읽기 전용)**

---

## 정산 계산 공식 (콜매니저/기사앱 공통)

```kotlin
val ratio = officeShareRatio  // 기본값 60%

// 계산
기사몫 = 총운행금 × (100 - ratio)%  // 40%
현금수령 = Σ cashReceived
실납입금 = 현금수령 - 기사몫

// 해석
// 양수 → 기사가 사무실에 납부
// 음수 → 사무실이 기사에게 지급 (미지급/미수령)
```

---

## 데이터 흐름

```
[로그인 시]
콜매니저: calls + ratio + carryOver 읽기 → 로컬 계산 → UI 표시
기사앱:   calls + ratio + carryOver 읽기 → 로컬 계산 → UI 표시

[마감 시]
콜매니저: 미지급금 계산 → carryOver 업데이트 (쓰기)
         → 기사에게 알림 전송 (미구현)
기사앱:   쓰기 없음

[다음 로그인]
둘 다 같은 데이터로 시작
```

---

## 입장별 표현

| 항목 | 콜매니저 (사무실 입장) | 기사앱 (기사 입장) |
|------|----------------------|-------------------|
| 사무실몫 60% | 수수료 | 납부액 |
| 기사몫 40% | 기사몫 | 내 수익 |
| 실납입금 + | 기사에게서 받을 돈 | 사무실에 낼 돈 |
| 실납입금 - | 기사에게 줄 돈 (미지급) | 사무실에서 받을 돈 (미수령) |

---

## 테스트 기기

| 기기 | 모델 | 설치된 앱 |
|------|------|----------|
| RF9R5013HEK | SM_A325N (갤럭시 A32) | 콜매니저 |
| R3CT80K78NP | SM_F721N (갤럭시 Z 플립4) | 기사앱 |

---

## 다음 세션 TODO

1. **기사앱 음수 표시 문제 해결** - UI에서 음수를 올바르게 표현
2. **콜매니저 carryOver UI 문제 해결** - 업데이트 및 표시 조건 확인
3. **로그아웃 문제 테스트** - 수정된 코드가 작동하는지 확인
4. **업무마감 시 기사 알림 구현** - FCM 알림 전송 기능 추가
5. **전체 플로우 테스트**

---

## 빌드 상태

- ✅ 콜매니저: BUILD SUCCESSFUL (설치됨)
- ✅ 기사앱: BUILD SUCCESSFUL (설치됨)

---

## 참고 파일

### 주요 수정 파일
- `call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/SettlementViewModel.kt`
- `call_manager/app/src/main/java/com/designated/callmanager/MainActivity.kt`
- `call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/DriverSummaryScreen.kt`
- `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt`
- `driver_app/app/src/main/java/com/designated/driverapp/ui/home/HistorySettlementScreen.kt`

### 이전 세션 문서
- `docs/SESSION_WORK_2026-02-02_carryover.md`
- `docs/CARRYOVER_SETTLEMENT_DESIGN.md`

# SCREENS — SwiftUI View 명세 (Phase 1 MVP)

> **목적**: 기사앱 Compose 화면을 SwiftUI View로 이관할 때 각 화면의 의존 Store·진입 경로·핵심 UI 요소·네비게이션 위치를 매핑.
> **범위**: FUNCTIONAL_INVENTORY §5 B로 분류된 화면만. **ReferralQRScreen은 R2로 제외**.
> **원본 합의**: Topic 2(@Observable/SwiftUI 직접 바인딩) · ENUMS.md(CallStatus·PaymentMethod 등) · VIEWMODELS.md(Store 스펙) · FIRESTORE.md(Repository).

---

## 원본 소스

| Swift View | Kotlin 파일 | 규모 (~줄) |
|---|---|---|
| `LoginView` | `ui/login/LoginScreen.kt` | ~300 |
| `SignUpView` | `ui/login/SignUpScreen.kt` | ~400 |
| `ForgotPasswordView` | `ui/login/ForgotPasswordScreen.kt` | ~200 |
| `HomeView` (root + 상태분기) | `ui/home/HomeScreen.kt` | ~1,100 |
| `WaitingView` | `ui/screens/home/WaitingScreen.kt` | ~200 |
| `TripPreparationView` | `ui/screens/home/TripPreparationScreen.kt` | ~600 |
| `InProgressView` | `ui/screens/home/InProgressScreen.kt` | ~300 |
| `OfflineView` | `ui/screens/home/OfflineScreen.kt` | ~150 |
| `NewCallPopupView` | `ui/screens/home/NewCallPopup.kt` | ~250 |
| `SettlementDialog` | (HomeScreen 내 Composable) | 인라인 |
| `CallDetailsView` | `ui/details/CallDetailsScreen.kt` | ~200 |
| `HistorySettlementView` | `ui/home/HistorySettlementScreen.kt` | ~500 |
| `SettingsView` | `ui/home/SettingsScreen.kt` | ~600 |
| `AddressSearchView` | (TripPreparation 내 inline) | |

**제외 (R2/D)**:
- `ReferralQRScreen` (R2)
- `LockScreenActivity` (D, iOS 풀스크린 재현 불가 — Topic 5)

---

## 요약

- **단일 `NavigationStack`** 루트. 로그인 전·후 분기는 `@Observable AppRouter`로.
- **HomeView**는 `driverStatus`에 따라 분기: `.offline` → OfflineView, `.waiting` → WaitingView, `.preparing` → TripPreparationView, `.onTrip` → InProgressView. Compose의 `HomeScreen` 최상위 분기 구조 유지.
- **NewCallPopupView**는 `uiState.newCallPopup != nil`일 때 `.sheet` 또는 `.fullScreenCover`로 오버레이.
- **SettlementDialog**는 `uiState.callForSettlement != nil`일 때 `.sheet`로 모달.
- **딥링크(navigateTo=settlement)**: FCM 알림 탭 → AppRouter.navigateTo(.settlement) → HistorySettlementView push.
- **Notification.Name 구독**: LocalBroadcast 등가. 각 View의 `.onReceive(NotificationCenter.default.publisher(for: ...))`로 dialog/cancellation UI 처리.

---

## 화면별 명세

### LoginView
**Kotlin**: `ui/login/LoginScreen.kt:34`에서 `loginViewModel.loginState.collectAsState()` 구독.

**의존**: `LoginStore` (VIEWMODELS.md)
**진입**: AppRouter root (로그인 안 된 상태) 또는 `LoginState.idle`
**핵심 UI**:
- 이메일(또는 전화) `TextField` (bind: `$store.email`)
- 비밀번호 `SecureField` (bind: `$store.password`)
- 자동로그인 `Toggle` (bind: `$store.autoLogin`)
- 로그인 버튼 → `Task { await store.login() }`
- 회원가입 버튼 → NavigationLink to `SignUpView`
- 비밀번호찾기 버튼 → NavigationLink to `ForgotPasswordView`
- `loginState` 관찰:
  - `.loading` → ProgressView
  - `.error(message)` → `.alert` 또는 inline Text
  - `.success(...)` → AppRouter.navigateToHome()

**SwiftUI 스켈레톤**:
```swift
struct LoginView: View {
    @Bindable var store: LoginStore
    var body: some View {
        VStack {
            TextField("이메일 또는 전화번호", text: $store.email)
            SecureField("비밀번호", text: $store.password)
            Toggle("자동 로그인", isOn: $store.autoLogin)
            Button("로그인") { Task { await store.login() } }
                .disabled(store.loginState.isLoading)         // associated values 우회
            NavigationLink("회원가입", destination: SignUpView(store: signUpStore))
            NavigationLink("비밀번호 찾기", destination: ForgotPasswordView())
        }
        .alert(...)  // 에러 표시
    }
}

// LoginState는 associated values를 가진 enum → Equatable 자동 합성 안 됨.
// `== .loading` 비교 대신 helper computed 사용 권장.
extension LoginState {
    var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }
    var isIdle: Bool {
        if case .idle = self { return true }
        return false
    }
}
```

---

### SignUpView
**Kotlin**: `ui/login/SignUpScreen.kt:36-39` — `signUpState`·`provinces`·`cities`·`offices` 4개 동시 구독.

**의존**: `SignUpStore`
**진입**: LoginView → NavigationLink
**핵심 UI**:
- 이름/전화/이메일/비밀번호 `TextField`
- 3단계 Picker (province → city → office). 이전 단계 변경 시 다음 단계 리셋(VIEWMODELS.md의 `onProvinceSelected` 등)
- 가입 버튼 → `Task { await store.signUp() }`
- `signUpState`:
  - `.success` → "승인 대기 중" 화면 + LoginView 복귀
  - `.error(msg)` → alert
  - `.loading` → ProgressView

---

### ForgotPasswordView
**Kotlin**: `ui/login/ForgotPasswordScreen.kt`

**의존**: LoginStore (또는 별도 ForgotPasswordStore — 단순하므로 LoginStore에 함수 추가 권장)
**진입**: LoginView → NavigationLink
**핵심 UI**:
- 이메일 `TextField`
- "재설정 이메일 발송" 버튼 → `Task { try await Auth.auth().sendPasswordReset(withEmail: email) }`
- 성공/실패 alert

---

### HomeView (루트 분기)
**Kotlin**: `ui/home/HomeScreen.kt:82-104` — BroadcastReceiver 3개 등록(ACTION_SHOW_CALL_DIALOG / CALL_CANCELLED / SETTLEMENT_*).

**의존**: `DriverStore`, `PresenceManager.shared`, `AppRouter`
**진입**: 로그인 성공 후 NavigationStack root
**핵심 UI**:
- 상단: 네트워크 배너 (`if !PresenceManager.shared.isConnected`)
- `driverStatus` 분기:
  ```swift
  switch store.uiState.driverStatus {
  case .offline: OfflineView(store: store)
  case .waiting, .online: WaitingView(store: store)
  case .assigned, .accepted, .preparing: TripPreparationView(store: store)
  case .onTrip: InProgressView(store: store)
  default: WaitingView(store: store)
  }
  ```
- `uiState.newCallPopup != nil` → `.fullScreenCover` NewCallPopupView
- `uiState.callForSettlement != nil` → `.sheet` SettlementDialog
- `uiState.errorMessage` → toast/snackbar (iOS `ToastUI` 또는 커스텀 overlay)
- **Notification 구독 vs PushRouter @Observable 관찰**:
  - **합의 적용**: PUSH.md §4.6 `PushRouter` @Observable이 FCM 수신을 단일 진입점으로 dispatch. View는 `.onChange(of: PushRouter.shared.pendingNewCall)` 패턴으로 직접 관찰 → consume-once → store.uiState.newCallPopup 설정.
  - `Notification.Name`(LocalBroadcast 등가) 구독은 **이중 경로 회피 위해 제거**. PushRouter 단일 경로로 통일.
  - 단 일부 시스템 이벤트(예: Auth state, scenePhase)는 여전히 NotificationCenter 또는 SwiftUI Environment 기반 구독.
  - `.callCancelled` → `store.handleCallCancelled(id: callId)`
  - `.settlementFinalized` → HistorySettlementView 딥링크
  - `.settlementConfirmed` / `.settlementRejected` → 알림 배너
- 하단: 네비게이션 탭 (홈·정산·설정)

---

### WaitingView
**Kotlin**: `ui/screens/home/WaitingScreen.kt`

**핵심 UI**:
- "콜 대기중" 상태 표시
- 오늘 운행 요약 카드 (`store.todaySettlement` 바인딩)
- 이월금 카드 (`store.carryOver` nil 아닐 때)
- "오프라인 전환" 버튼 → `Task { await store.updateDriverStatus(.offline) }`

---

### TripPreparationView
**Kotlin**: `ui/screens/home/TripPreparationScreen.kt`

**핵심 UI**:
- 콜 정보(출발지/도착지/고객/연락처) 표시
- 출발지 `TextField` + 주소 검색(AddressSearchView로 sheet)
- 도착지 `TextField` + 주소 검색
- 경유지 `TextField` (선택)
- 요금 `TextField` (숫자 키보드, isDigit 필터)
- "운행 시작" 버튼 → `Task { await store.startDriving(...) }`
- "운행 취소" 버튼 → `Task { await store.cancelTrip(id:) }` (CANCELLED_BY_DRIVER)
- `store.isAccepting` 감시(수락 직후 로딩 상태)

**주소 검색 (AddressSearchView)**:
- `ui/home/DriverAppUtils.kt`의 helper 사용
- **swift-expert 결정 (Phase B)**: **카카오 로컬 REST API 직접 호출** 권장. 근거는 본 문서 말미 "결정 답변" 참조.
- Phase 1 MVP에서 **필수** (B 분류) — 없으면 기사가 출발지 입력 불가

---

### InProgressView
**Kotlin**: `ui/screens/home/InProgressScreen.kt`

**핵심 UI**:
- activeCall 정보 표시 (출발지/도착지/고객)
- "운행 완료" 버튼 → `Task { await store.completeCall(id:) }` → SettlementDialog 열림
- "운행 취소" 버튼 → cancelTrip
- 실시간 위치 추적 없음 (FUNCTIONAL_INVENTORY §1.5)

---

### OfflineView
**Kotlin**: `ui/screens/home/OfflineScreen.kt`

**핵심 UI**:
- "오프라인 상태" 표시
- "온라인 전환" 버튼 → `Task { await store.updateDriverStatus(.online) }`
- 로그아웃 버튼

---

### NewCallPopupView (모달)
**Kotlin**: `ui/screens/home/NewCallPopup.kt`

**의존**: `DriverStore` (`uiState.newCallPopup: CallInfo?`)
**트리거**: FCM `call_assigned` 수신 → AppDelegate `userNotificationCenter` → `PushRouter.shared.dispatch` → `PushRouter.pendingNewCall` 설정 → HomeView `.onChange(of: pendingNewCall)`에서 consume → `store.uiState.newCallPopup` 설정 → HomeView `.fullScreenCover` (PUSH.md §4.6 참조, 단일 경로)
**핵심 UI**:
- 콜 정보(고객명·전화·출발지) 표시
- "수락" 버튼 → `Task { await store.acceptCall(id:) }`, `isAccepting` 가드
- "닫기" 버튼 → `store.dismissNewCallPopup()` (다음 대기 콜 있으면 자동 표시)
- 포그라운드에서 알림음 1회 재생(iOS `AVAudioPlayer` 한 번)
- 백그라운드 잠금화면에서는 이 View 대신 시스템 Time Sensitive 알림 (잠금 풀스크린은 R1)

---

### SettlementDialog (모달)
**Kotlin**: `HomeScreen.kt` 내 Composable — 정산 입력 다이얼로그

**트리거**: `completeCall` 성공 후 `uiState.callForSettlement` 설정
**핵심 UI**:
- 결제 방식 `Picker` (PaymentMethod enum 5종)
- 요금 `TextField`
- 현금+포인트 선택 시 현금액 `TextField` + 포인트 `TextField` (고객 포인트 조회 결과 표시)
- "확정" 버튼 → `Task { await store.confirmAndFinalizeTrip(...) }`
- 취소 불가 (완료만)

---

### CallDetailsView
**Kotlin**: `ui/details/CallDetailsScreen.kt:42` — `viewModel.callDetails.collectAsState()`

**의존**: `DriverStore` + `loadCallDetails(id:)`
**진입**: 운행내역 카드 탭 → NavigationStack push
**핵심 UI**:
- `onAppear` → `store.loadCallDetails(id: callId)`
- callDetails 바인딩: 고객/출발/도착/요금/결제방식/완료시간/특이사항 표시
- 완료 콜 전용(읽기 전용)

---

### HistorySettlementView
**Kotlin**: `ui/home/HistorySettlementScreen.kt:76, 82, 104` — `uiState` + `carryOver` + `tripHistoryList` 3개 구독

**의존**: `DriverStore`
**진입**:
1. 네비게이션 탭 "정산"
2. FCM SETTLEMENT_CONFIRMED/REJECTED 알림 탭 → 딥링크

**핵심 UI**:
- 오늘 운행 요약 카드 (`todaySettlement`: 총 운행료/내 수익/납입액/외상)
- 운행 내역 리스트 (`tripHistoryList` ForEach):
  - 번호·고객·출발→도착·요금·결제방식
  - 탭 → CallDetailsView push
- 이월금 카드 (`carryOver`):
  - status=.pending: "미수령"
  - status=.transferred: "이체됨 [수령확인] 버튼" → `Task { await store.confirmReceiveCarryOver() }`
  - status=.settled: "수령 완료"
- "업무 마감" 버튼 (dailySettlementStatus=.working 시):
  - → SettlementSubmissionDialog (통합 제출 여부 확인 후 `submitDailySettlement`)
- `dailySettlementStatus=.pendingConfirm` 시: "매니저 확인 대기 중" 비활성
- `dailySettlementStatus=.rejected` 시: "재제출" 버튼

---

### SettingsView
**Kotlin**: `ui/home/SettingsScreen.kt`

**의존**: `LoginStore` + `DriverStore`
**진입**: 네비게이션 탭 "설정"
**핵심 UI**:
- 자동 로그인 `Toggle` (bind: `$loginStore.autoLogin`) → `toggleAutoLogin(_:)`
- 비밀번호 변경 `TextField` 3개 → 재인증 + `updatePassword()`
- 알림 설정(시스템 설정으로 이동 링크)
- 버전 정보 표시
- 로그아웃 버튼 → `loginStore.logout()` (PresenceManager.onLogout 포함)
- 계정 탈퇴(있으면): 별도 확인 후 처리

---

### AddressSearchView (모달/sheet)
**Kotlin**: `util/AddressSearchHelper.kt` + TripPreparation에서 호출

**의존**: 외부 지도 API (Kakao/네이버 iOS SDK)
**진입**: TripPreparationView에서 "주소 검색" 버튼 → `.sheet`
**핵심 UI**:
- 검색어 `TextField` + 검색 버튼
- 결과 리스트 → 선택 시 dismiss + 호출자에 String 반환 (Binding으로 연결)
- 네트워크 에러 표시

**anti-pattern 교정**: Kotlin `CoroutineScope(Dispatchers.IO).launch` → Swift `Task { }` (inherit context, Topic 4 합의)

---

## 네비게이션 스택 구조

```
AppRouter (NavigationStack path)
├── (unauthenticated)
│   ├── LoginView
│   │   ├── SignUpView
│   │   └── ForgotPasswordView
│   └── (loading/splash)
└── (authenticated)
    └── HomeView (TabView root)
        ├── Tab: 홈 → [ Waiting | TripPreparation | InProgress | Offline ]
        │   ├── .fullScreenCover: NewCallPopupView
        │   ├── .sheet: SettlementDialog
        │   └── .sheet: AddressSearchView (TripPreparation 내부)
        ├── Tab: 정산 → HistorySettlementView
        │   └── NavigationLink → CallDetailsView
        └── Tab: 설정 → SettingsView
```

### 딥링크 경로

FCM 알림 tap 시 `userInfo["navigateTo"]`:

| 값 | 도착 화면 |
|---|---|
| `"settlement"` | HistorySettlementView (정산 탭 전환 + push) |
| (callId 포함) | HomeView → newCallPopup 설정 또는 CallDetailsView |

Kotlin `MyFirebaseMessagingService.kt:289-291` `intent.putExtra("navigateTo", ...)` 등가.

---

## Notification.Name 구독 매핑

| Notification.Name | 구독 View | 핸들러 |
|---|---|---|
| `.showCallDialog` | HomeView | `store.uiState.newCallPopup` 설정 (FCM 핸들러에서 직접도 가능, 중복 주의) |
| `.callCancelled` | HomeView | `store.handleCallCancelled(id:)` + toast |
| `.settlementFinalized` | HomeView, HistorySettlementView | 알림 배너 + 화면 refresh |
| `.settlementConfirmed` | HistorySettlementView | dailySettlementStatus UI 반영 |
| `.settlementRejected` | HistorySettlementView | "재제출" 유도 |

---

## iOS 특수 사항

1. **@Bindable vs @Environment vs @State**: 외부 주입(부모가 owning) → `@Bindable var store: DriverStore`. 자체 owning → `@State private var store = DriverStore(...)`. 깊은 뷰 계층 공유 → `.environment(store)` + `@Environment(DriverStore.self) var store`.
2. **`.fullScreenCover` vs `.sheet`**: NewCallPopup은 Android FullScreenIntent 의도에 가장 근접한 `.fullScreenCover` 사용 권장. SettlementDialog는 `.sheet` 충분. **단 잠금화면 위 표시는 불가** — 앱 foreground 상태에서만 풀스크린.
3. **Focus/Keyboard 관리**: TextField 포커스는 `@FocusState` 사용. 숫자 키보드는 `.keyboardType(.numberPad)`. 요금 입력 검증은 `Int(text)` 변환 시도 + 실패 시 disable.
4. **TabView + NavigationStack 조합**: 각 탭 내부에 NavigationStack 보유 (iOS 16+ 권장 패턴). 외부에 NavigationStack 두면 탭 전환 시 stack 공유로 path 충돌 가능.
5. **AppRouter @Observable**: 인증 분기는 root에서 `if loginStore.loginState.isSuccess { HomeView() } else { LoginView() }` 패턴. NavigationStack은 각 화면 안에서.
6. **AssociatedValues enum 비교**: `LoginState`, `LocationFetchStatus` 등은 자동 Equatable 합성 안 됨. `if case .loading = state` 패턴 또는 helper computed property 사용. 직접 `==` 비교 금지.
7. **`@Observable` + SwiftUI 바인딩**: `@Bindable var store: Store`로 TextField 양방향 바인딩 자연스러움. `$store.email` 패턴.
8. **알림음**: NewCallPopupView 포그라운드에서 `AVAudioPlayer` 1회 재생. **AVAudioPlayer는 Sendable 아님** — `@MainActor` 또는 actor 내부에 보관. 3초 반복은 R1(VoIP+CallKit ringtone).
9. **스크린 켜짐 유지**: TripPreparation/InProgress View에서 `UIApplication.shared.isIdleTimerDisabled = true` (Kotlin FLAG_KEEP_SCREEN_ON 부분 대응). 뷰 이탈 시 `false` 복원. `.onAppear`/`.onDisappear` 또는 `.task` modifier 사용.
10. **PushRouter 단일 진입**: NotificationCenter `.showCallDialog` 등 LocalBroadcast 등가 구독은 PushRouter @Observable로 통일하여 이중 경로 회피. 합의: PUSH.md §4.6 + SCREENS.md HomeView 섹션.
11. **AddressSearchView 외부 네트워크**: Kakao Local REST API 사용 시 Info.plist에 별도 ATS 예외 없이 HTTPS 호출만. 인증키는 빌드 환경변수에서 주입(소스 커밋 금지).

---

## 체크리스트

- [ ] AppRouter (@Observable) 인증 분기
- [ ] LoginView + Bindable + loginState 분기 UI
- [ ] SignUpView + 3단계 Picker + 의존성 리셋
- [ ] ForgotPasswordView + sendPasswordReset
- [ ] HomeView 상태 분기 switch + 네트워크 배너
- [ ] WaitingView / TripPreparationView / InProgressView / OfflineView
- [ ] NewCallPopupView `.fullScreenCover` + 수락 버튼 isAccepting 가드
- [ ] SettlementDialog + PaymentMethod Picker + 결제방식별 필드
- [ ] CallDetailsView (read-only)
- [ ] HistorySettlementView + 3 Store 바인딩 + 업무마감 / 수령확인 버튼 + 상태별 UI
- [ ] SettingsView + 자동로그인 토글 + 로그아웃(PresenceManager.onLogout)
- [ ] AddressSearchView (Kakao/네이버 SDK 결정 + Task 사용)
- [ ] Notification.Name 5개 구독자 배정
- [ ] 딥링크 `navigateTo=settlement` 라우팅
- [ ] AVAudioPlayer 포그라운드 알림음 (1회, 3초 반복은 R1)
- [ ] `isIdleTimerDisabled` 운행 중 true
- [ ] ReferralQRScreen 구현 **제외** (R2)

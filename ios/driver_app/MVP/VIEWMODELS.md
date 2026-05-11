# VIEWMODELS — @Observable Store 스펙 (Phase 1 MVP)

> **목적**: Kotlin `ViewModel + StateFlow` 패턴을 iOS 17.2 `@Observable` Store로 이관하는 명세. Topic 2 합의 반영: **단일 `uiState` struct property + 보조 @Observable property + AsyncStream 브리지**.
> **원본 합의**: Topic 2(@Observable + distinctUntilChanged 대응) · Topic 4(AsyncStream 단독) · SHARED_LOGIC §8(로그인 12단계) · FUNCTIONAL_INVENTORY §1/§2.

---

## 원본 소스

| Swift Store | Kotlin 파일:라인 | 규모 |
|---|---|---|
| `DriverStore` | `viewmodel/DriverViewModel.kt` | ~1,660줄, 10 StateFlow + 30여 함수 |
| `LoginStore` | `ui/login/LoginViewModel.kt` | ~290줄, 1 StateFlow + 10 함수 |
| `SignUpStore` | `ui/login/SignUpViewModel.kt` | ~230줄, 4 StateFlow + 10 함수 |

---

## 요약

- **@Observable 단일 Store 클래스**: Kotlin `@HiltViewModel class` → Swift `@Observable @MainActor final class`.
- **uiState는 struct property**: Kotlin `_uiState: MutableStateFlow<DriverScreenUiState>` → Swift `var uiState = DriverScreenUiState()`. struct 전체를 한 번에 교체해 **원자적 상태 전이**(Topic 2 합의 핵심).
- **보조 StateFlow → @Observable var**: `callDetails`, `carryOver`, `depositRatio`, `lastClearedMillis`, `isAccepting`, `isSubmittingSettlement`, `dailySettlementStatus`, `todaySettlement`, `tripHistoryList`, `notificationCallId` 각각 독립 프로퍼티.
- **리스너 브리지**: Firestore `addSnapshotListener` → `AsyncStream<T>` wrapper. `continuation.onTermination`에서 `listener.remove()` 호출(Topic 4 합의).
- **viewModelScope 대응**: Swift는 Store가 `var activeTasks: [Task<Void, Never>] = []` 보유하고 `deinit` 또는 `stop()`에서 전부 cancel.
- **Dependency Injection**: Hilt 대신 iOS는 간단한 수동 DI 또는 `@Environment` 커스텀 EnvironmentKey로 주입. FirebaseAuth/Firestore는 싱글톤(`.auth()`, `.firestore()`) 직접 사용 가능.

---

## DriverStore 매핑 테이블

### @Observable 프로퍼티 (Topic 2 합의)

| Swift property | Kotlin 원본 | 타입 |
|---|---|---|
| `uiState` | `_uiState: MutableStateFlow<DriverScreenUiState>` | `DriverScreenUiState` struct (12 필드 통합) |
| `callDetails` | `_callDetailsState` | `CallInfo?` |
| `notificationCallId` | `_notificationCallId` | `String?` |
| `carryOver` | `_carryOver` | `DriverCarryOver?` |
| `depositRatio` | `_depositRatio` | `Int = 60` |
| `lastClearedMillis` | `_lastClearedMillis` | `Int64 = 0` |
| `isAccepting` | `_isAccepting` | `Bool = false` (수락 중복 클릭 방지) |
| `isSubmittingSettlement` | `_isSubmittingSettlement` | `Bool = false` |
| `dailySettlementStatus` | `_dailySettlementStatus` | `DailySettlementStatus = .working` |
| `todaySettlement` | `_todaySettlement` | `TodaySettlement` struct (8 필드 집계) |
| `tripHistoryList` | `_tripHistoryList` | `[TripHistoryItem]` (완료 콜 카드 표시용) |

`DriverScreenUiState` struct (원본 `ui/state/DriverScreenUiState.kt:10-23`):
- `driverStatus: DriverStatus = .offline`
- `assignedCalls: [CallInfo] = []`
- `completedCalls: [CallInfo] = []`
- `activeCall: CallInfo?`
- `callForSettlement: CallInfo?`
- `newCallPopup: CallInfo?`
- `isLoading: Bool = false`
- `errorMessage: String?`
- `locationFetchStatus: LocationFetchStatus = .idle` (sealed → Swift enum w/ associated values)
- `navigateToHome: Bool = false`
- `navigateToHistorySettlement: Bool = false`
- `isServiceBound: Bool = false` (iOS에선 Foreground Service 없음 → **제거 또는 Phase 2로 보류**)

### 라이프사이클

| Swift 메서드 | Kotlin 원본 |
|---|---|
| `init(auth:, firestore:, defaults:, pointsRepo:)` | `DriverViewModel @Inject constructor(...)` + `init { ... }` |
| `start()` or `startAuthObservation()` | Auth state listener 등록 (`DriverViewModel.kt:177-186`) |
| `stop()` | `onCleared()` 등가 — 모든 리스너 제거, 활성 Task cancel |
| `deinit` | stop() 호출 |

### 행동 함수 (Firestore 트랜잭션·업데이트 대상)

모두 `async throws` 또는 `Task { ... }` 기반. 자세한 트랜잭션 스펙은 **FIRESTORE.md** 참조.

| Swift 함수 | Kotlin 원본 라인 | Phase |
|---|---|---|
| `loadCurrentActiveCall()` | `DriverViewModel.kt:238` | B |
| `loadCallDetails(id:)` | `DriverViewModel.kt:329` | B |
| `acceptCall(id:) async` | `DriverViewModel.kt:371` | B |
| `rejectCall(id:) async` | `DriverViewModel.kt:477` | B |
| `cancelTrip(id:reason:) async` | `DriverViewModel.kt:526` | B |
| `startDriving(id:departure:destination:waypoints:fare:) async` | `DriverViewModel.kt:580` | B |
| `completeCall(id:) async` | `DriverViewModel.kt:640` | B |
| `confirmAndFinalizeTrip(id:paymentMethod:cashAmount:fare:tripSummary:pointsToUse:) async` | `DriverViewModel.kt:670` | B |
| `submitDailySettlement(isIntegration:) async` | `DriverViewModel.kt:1348` | B |
| `confirmReceiveCarryOver() async -> Result<Void, Error>` | `DriverViewModel.kt:1314` | B |
| `updateDriverStatus(_:) async` | `DriverViewModel.kt:846` | B |
| `handleCallCancelled(id:)` | `DriverViewModel.kt:907` | B |
| `refreshActiveCallStatus() async` | `DriverViewModel.kt:929` | B |
| `dismissNewCallPopup()` | `DriverViewModel.kt:889` | B |
| `setNotificationCallId(_:)` | `DriverViewModel.kt:1105` | B |
| `handleNotificationCallId(_:)` | `DriverViewModel.kt:1117` | B |
| `refreshSettlementData() async` | `DriverViewModel.kt:1618` | B |
| `clearSettlement(...)` | `DriverViewModel.kt:1634` | B |
| `getCustomerPointInfo(phone:) async -> [String: Any]?` | `DriverViewModel.kt:1235` | B |

### 리스너

| 리스너 | Kotlin | Swift |
|---|---|---|
| `carryOverListener` | `DriverViewModel.kt:1261-1312` | **유일한 실시간 AsyncStream** — `@ModelActor` 외부에서 FirestoreSnapshotStream으로 래핑 |
| `auth state` | `FirebaseAuth.AuthStateListener` (`DriverViewModel.kt:177`) | `Auth.auth().addStateDidChangeListener` → NotificationCenter `.AuthStateDidChange` Combine → Task |

**제거된 리스너**: `assignedCallsListener`, `driverStatusListener`, `completedCallsListener`는 Kotlin에는 선언되어 있으나 현 코드에서 **초기화만 nullable로 선언 + stopListeners에서 remove만** 하고 실제 attach 경로 없음(3/17 리스너 방식 폐기 후 잔여 — FUNCTIONAL_INVENTORY §1.1 "비용 최적화 ~$414/월 절감"). **Swift에서는 제거**, 앱 진입 시 `loadCurrentActiveCall` 1회 조회로 대체.

---

## LoginStore 매핑 테이블

### @Observable 프로퍼티

| Swift property | Kotlin 원본 |
|---|---|
| `loginState: LoginState` | `_loginState: MutableStateFlow<LoginState>` |
| `email: String` | `var email by mutableStateOf("")` |
| `password: String` | `var password by mutableStateOf("")` |
| `autoLogin: Bool` | `var autoLogin by mutableStateOf(false)` |

`LoginState` sealed enum → Swift enum with associated values:

```swift
enum LoginState {
    case idle
    case loading
    case success(provinceId: String, cityId: String, officeId: String, driverId: String, needsTokenUpdate: Bool)
    case error(message: String)
}
```

### 행동 함수

| Swift | Kotlin | 비고 |
|---|---|---|
| `init(...)` | `LoginViewModel.kt:54-68` | Keychain에서 자동로그인 자격증명 조회, autoLogin=true면 자동 login() 호출 |
| `login() async` | `LoginViewModel.kt:70-117` | 온라인 실패 시 오프라인 로그인 시도 |
| `attemptOfflineLogin()` | `LoginViewModel.kt:132` | UserSession 캐시로 복구 (7일 유효) |
| `checkPendingStatusAndProceed(userId:) async` | `LoginViewModel.kt:154` | pending_drivers 체크 → 있으면 signOut |
| `findDriverDocumentAndSaveInfo(userId:) async` | `LoginViewModel.kt:170` | collectionGroup 쿼리 → SharedPrefs 저장 + status=ONLINE + FCM 토큰 등록 |
| `toggleAutoLogin(enabled: Bool)` | `LoginViewModel.kt:270` | Keychain identifier/password 저장/삭제 |
| `resetLoginState()` | `LoginViewModel.kt:275` | `.idle`로 |
| `logout()` | `LoginViewModel.kt:282` | `auth.signOut()` + **PresenceManager.onLogout() 호출 추가** (AND-03 교정) |

### 의존성

- `Auth.auth()` (FirebaseAuth)
- `Firestore.firestore()`
- `SecureStore` (Keychain wrapper, AUTH.md 참조)
- `UserDefaults.standard`
- `SessionManager` (UserSession 캐시)

---

## SignUpStore 매핑 테이블

### @Observable 프로퍼티

| Swift property | Kotlin 원본 |
|---|---|
| `signUpState: SignUpState` | `_signUpState` |
| `provinces: [ProvinceItem]` | `_provinces` |
| `cities: [CityItem]` | `_cities` |
| `offices: [OfficeItem]` | `_offices` |
| `selectedProvince: ProvinceItem?` | mutableStateOf |
| `selectedCity: CityItem?` | mutableStateOf |
| `selectedOffice: OfficeItem?` | mutableStateOf |
| `name/phone/email/password` | mutableStateOf |

`SignUpState` sealed:
```swift
enum SignUpState {
    case idle
    case loading
    case success
    case error(String)
}
```

### 행동 함수

| Swift | Kotlin |
|---|---|
| `fetchProvinces()` | `SignUpViewModel.kt:75` |
| `fetchCities(provinceId:)` | `SignUpViewModel.kt:97` |
| `fetchOffices(provinceId:cityId:)` | `SignUpViewModel.kt:121` |
| `onProvinceSelected(_:)` | `SignUpViewModel.kt:145` — cities 리셋 후 fetch |
| `onCitySelected(_:)` | `SignUpViewModel.kt:152` — offices 리셋 후 fetch |
| `onOfficeSelected(_:)` | `SignUpViewModel.kt:160` |
| `signUp() async` | `SignUpViewModel.kt:164` — createUser + pending_drivers 문서 생성 |
| `resetSignUpState()` | `SignUpViewModel.kt:218` |

**단계 의존성 보존**: province 선택 시 cities·offices 리셋. Swift는 `didSet` 또는 `onChange(of:)` modifier로 선언적 보장.

---

## 의사 스펙 (Swift)

```swift
import Foundation
import Observation
import FirebaseAuth
import FirebaseFirestore

// MARK: - DriverScreenUiState
struct DriverScreenUiState {
    var driverStatus: DriverStatus = .offline
    var assignedCalls: [CallInfo] = []
    var completedCalls: [CallInfo] = []
    var activeCall: CallInfo?
    var callForSettlement: CallInfo?
    var newCallPopup: CallInfo?
    var isLoading: Bool = false
    var errorMessage: String?
    var locationFetchStatus: LocationFetchStatus = .idle
    var navigateToHome: Bool = false
    var navigateToHistorySettlement: Bool = false
}

enum LocationFetchStatus {
    case idle
    case loading
    case success(address: String)
    case error(message: String)
}

struct TodaySettlement {
    var totalFare: Int = 0
    var driverShare: Int = 0
    var cashReceived: Int = 0
    var realDeposit: Int = 0
    var tripCount: Int = 0
    var totalCredit: Int = 0
    var officeDeposit: Int = 0
    var pointsUsed: Int = 0
}

struct TripHistoryItem: Identifiable {
    let id = UUID()
    let tripNumber: Int
    let customerName: String
    let departure: String
    let destination: String
    let fare: Int
    let paymentMethod: String
    let cashAmount: Int?
    let timestamp: Date
}

// MARK: - DriverStore
@Observable
@MainActor
final class DriverStore {
    // 통합 상태 (Topic 2 합의: 원자적 교체)
    var uiState = DriverScreenUiState()

    // 보조 @Observable property
    var callDetails: CallInfo?
    var notificationCallId: String?
    var carryOver: DriverCarryOver?
    var depositRatio: Int = 60
    var lastClearedMillis: Int64 = 0
    var isAccepting: Bool = false
    var isSubmittingSettlement: Bool = false
    var dailySettlementStatus: DailySettlementStatus = .working
    var todaySettlement = TodaySettlement()
    var tripHistoryList: [TripHistoryItem] = []

    // 의존성
    private let auth: Auth
    private let firestore: Firestore
    private let defaults: UserDefaults
    private let pointsRepo: CustomerPointsRepository  // TODO: 신규 정의

    // Task 추적 (viewModelScope 등가)
    private var tasks: [Task<Void, Never>] = []
    private var carryOverTask: Task<Void, Never>?

    // Auth listener handle (해제 시 필요)
    private var authHandle: AuthStateDidChangeListenerHandle?

    init(auth: Auth = .auth(),
         firestore: Firestore = .firestore(),
         defaults: UserDefaults = .standard,
         pointsRepo: CustomerPointsRepository) {
        self.auth = auth
        self.firestore = firestore
        self.defaults = defaults
        self.pointsRepo = pointsRepo
        startAuthObservation()
    }

    deinit {
        // 주의: @MainActor deinit은 nonisolated 컨텍스트.
        //       MainActor-isolated 메서드 호출 불가(Swift 6 strict concurrency).
        //       Task.cancel()은 nonisolated이므로 직접 호출 가능.
        //       AuthStateDidChangeListenerHandle 해제도 동기 호출 가능.
        tasks.forEach { $0.cancel() }
        carryOverTask?.cancel()
        if let handle = authHandle { auth.removeStateDidChangeListener(handle) }
    }

    /// 명시적 종료 (View .onDisappear 등에서 호출). MainActor 격리.
    func stop() {
        tasks.forEach { $0.cancel() }
        tasks.removeAll()
        carryOverTask?.cancel()
        carryOverTask = nil
        if let handle = authHandle {
            auth.removeStateDidChangeListener(handle)
            authHandle = nil
        }
    }

    // MARK: - Lifecycle
    func initializeListenersWithInfo(provinceId: String, cityId: String, officeId: String, driverId: String) {
        // TODO: Kotlin DriverViewModel.kt:210 참조
        // 1. loadCurrentActiveCall(...) 호출
        // 2. startCarryOverListener(...) 기동
        // 3. loadSettlementData(...) 기동
    }

    // MARK: - Call lifecycle
    func loadCurrentActiveCall(provinceId: String, cityId: String, officeId: String, driverId: String) async {
        // TODO: Kotlin DriverViewModel.kt:238 — whereIn(status, [ASSIGNED, ACCEPTED, IN_PROGRESS, AWAITING_SETTLEMENT]) 1회 조회
    }

    func acceptCall(id: String) async {
        guard !isAccepting else { return }
        isAccepting = true
        defer { isAccepting = false }
        // TODO: Kotlin DriverViewModel.kt:371 참조
        // 1. 낙관적 uiState 업데이트 (ACCEPTED + PREPARING)
        // 2. runTransaction: call.status=ACCEPTED + driver.status=PREPARING (ASSIGNED일 때만)
        // 3. catch: 롤백
    }

    func rejectCall(id: String) async { /* TODO: Kotlin:477 */ }
    func cancelTrip(id: String, reason: String = "운행취소") async { /* TODO: Kotlin:526 */ }
    func startDriving(id: String, departure: String, destination: String, waypoints: String, fare: Int) async { /* TODO: Kotlin:580 */ }
    func completeCall(id: String) async { /* TODO: Kotlin:640 */ }

    func confirmAndFinalizeTrip(id: String, paymentMethod: PaymentMethod, cashAmount: Int?, fare: Int, tripSummary: String, pointsToUse: Int = 0) async {
        // TODO: Kotlin DriverViewModel.kt:670
        // - 콜 문서에 fare/paymentMethod/status=COMPLETED/creditAmount/pointsUsed/completedAt/finalFare
        // - driver.status=WAITING 단일 트랜잭션
        // - todaySettlement 로컬 집계 업데이트 (SHARED_LOGIC §4 공식 적용)
        // - tripHistoryList prepend
    }

    func submitDailySettlement(isIntegration: Bool) async { /* TODO: Kotlin:1348 */ }
    func confirmReceiveCarryOver() async -> Result<Void, Error> { /* TODO: Kotlin:1314 */ fatalError() }
    func updateDriverStatus(_ status: DriverStatus) async { /* TODO: Kotlin:846 */ }

    // MARK: - FCM/UI handlers
    func handleCallCancelled(id: String) { /* TODO: Kotlin:907 */ }
    func refreshActiveCallStatus() async { /* TODO: Kotlin:929 */ }
    func dismissNewCallPopup() { /* TODO: Kotlin:889 */ }
    func setNotificationCallId(_ id: String) { notificationCallId = id }
    func clearNotificationCallId() { notificationCallId = nil }
    func handleNotificationCallId(_ id: String) { /* TODO: Kotlin:1117 */ }

    // MARK: - Settlement
    func refreshSettlementData() async { /* TODO: Kotlin:1618 */ }

    // MARK: - CarryOver listener (AsyncStream)
    private func startCarryOverListener(provinceId: String, cityId: String, officeId: String, driverId: String) {
        carryOverTask?.cancel()
        // [weak self] 캡처로 retain cycle 회피.
        // self 강참조 시 Store deinit이 영원히 호출 안 됨 → carryOverTask cancel도 안 됨.
        carryOverTask = Task { [weak self] in
            let stream: AsyncStream<DocumentSnapshot> = FirestoreSnapshotStream.snapshots(/* driverRef */)
            for await snapshot in stream {
                guard let self else { return }
                // 디코딩 + 상태 업데이트 (FIRESTORE.md 참조)
                // self.carryOver = try? snapshot.data(as: DriverCarryOver.self)
                // self.dailySettlementStatus = ... (carryOver.status에서 파생)
            }
        }
    }

    // MARK: - Auth observation
    private func startAuthObservation() { /* TODO: Kotlin:177-186 */ }
}

// MARK: - LoginStore
@Observable
@MainActor
final class LoginStore {
    var loginState: LoginState = .idle
    var email: String = ""
    var password: String = ""
    var autoLogin: Bool = false

    private let auth: Auth
    private let firestore: Firestore
    private let secureStore: SecureStore         // Keychain (AUTH.md 참조)
    private let sessionManager: SessionManager   // UserSession 캐시

    init(auth: Auth = .auth(),
         firestore: Firestore = .firestore(),
         secureStore: SecureStore,
         sessionManager: SessionManager,
         presence: PresenceManaging) {
        self.auth = auth
        self.firestore = firestore
        self.secureStore = secureStore
        self.sessionManager = sessionManager
        self.presence = presence

        // TODO: Kotlin LoginViewModel.kt:54-68
        self.autoLogin = secureStore.isAutoLoginEnabled()
        if autoLogin,
           let savedId = secureStore.getSavedIdentifier(),
           let savedPw = secureStore.getSavedPassword() {
            self.email = savedId
            self.password = savedPw
            Task { await login() }
        }
    }

    func login() async { /* TODO: Kotlin:70-117 */ }
    private func attemptOfflineLogin() { /* TODO: Kotlin:132 */ }
    private func checkPendingStatusAndProceed(userId: String) async { /* TODO: Kotlin:154 */ }
    private func findDriverDocumentAndSaveInfo(userId: String) async { /* TODO: Kotlin:170 */ }

    func toggleAutoLogin(_ enabled: Bool) { /* TODO: Kotlin:270 */ }
    func resetLoginState() { loginState = .idle }

    /// AND-03 교정: Android 잠재 leak 해소.
    /// PresenceManager는 protocol(`PresenceManaging`)으로 추상화하여 테스트 용이성 확보 권장.
    /// 본 스펙에서는 의존성 주입한 인스턴스 사용 가정.
    private let presence: PresenceManaging   // init에서 주입

    func logout() async {
        await presence.onLogout()             // Realtime DB offline 명시 + onDisconnect cleanup
        try? auth.signOut()
        sessionManager.clear()
        loginState = .idle
    }
}

/// Presence 관리 추상화 (테스트 용이 + 의존성 명시)
protocol PresenceManaging: Sendable {
    func onLogin(uid: String) async
    func onAppForeground() async
    func onAppBackground() async
    func onLogout() async
}

// MARK: - SignUpStore
@Observable
@MainActor
final class SignUpStore {
    var signUpState: SignUpState = .idle
    var provinces: [ProvinceItem] = []
    var cities: [CityItem] = []
    var offices: [OfficeItem] = []
    var selectedProvince: ProvinceItem?
    var selectedCity: CityItem?
    var selectedOffice: OfficeItem?

    var name: String = ""
    var phone: String = ""
    var email: String = ""
    var password: String = ""

    private let auth: Auth
    private let firestore: Firestore

    init(auth: Auth = .auth(), firestore: Firestore = .firestore()) {
        self.auth = auth
        self.firestore = firestore
    }

    func fetchProvinces() async { /* TODO: Kotlin:75 */ }
    func fetchCities(provinceId: String) async { /* TODO: Kotlin:97 */ }
    func fetchOffices(provinceId: String, cityId: String) async { /* TODO: Kotlin:121 */ }

    func onProvinceSelected(_ p: ProvinceItem) {
        selectedProvince = p
        selectedCity = nil
        selectedOffice = nil
        cities = []
        offices = []
        Task { await fetchCities(provinceId: p.id) }
    }

    func onCitySelected(_ c: CityItem) {
        selectedCity = c
        selectedOffice = nil
        offices = []
        guard let p = selectedProvince else { return }
        Task { await fetchOffices(provinceId: p.id, cityId: c.id) }
    }

    func onOfficeSelected(_ o: OfficeItem) { selectedOffice = o }

    func signUp() async { /* TODO: Kotlin:164 */ }
    func resetSignUpState() { signUpState = .idle }
}

// MARK: - 보조 타입
struct ProvinceItem: Identifiable, Hashable { let id: String; let name: String }
struct CityItem: Identifiable, Hashable { let id: String; let name: String }
struct OfficeItem: Identifiable, Hashable { let id: String; let name: String }

enum LoginState {
    case idle
    case loading
    case success(provinceId: String, cityId: String, officeId: String, driverId: String, needsTokenUpdate: Bool)
    case error(message: String)
}

enum SignUpState {
    case idle
    case loading
    case success
    case error(String)
}

// MARK: - SessionManager (UserSession 캐시, AUTH.md에서 구현 세부)
protocol SessionManager {
    func save(_ session: UserSession)
    func load() -> UserSession?
    func canLoginOffline() -> Bool
    func clear()
}

protocol SecureStore {
    func isAutoLoginEnabled() -> Bool
    func getSavedIdentifier() -> String?
    func getSavedPassword() -> String?
    func saveCredentials(identifier: String, password: String)
    func clearCredentials()
}

protocol CustomerPointsRepository {
    func getPointInfo(phone: String) async -> [String: Any]?
}
```

---

## iOS 특수 사항

1. **@MainActor 격리**: 모든 Store는 `@MainActor final class`로 UI 스레드 일관성 보장. Firestore 비동기 작업은 `Task { }` 안에서 background thread 자동 처리 후 UI 업데이트는 main actor로 복귀.
2. **원자적 상태 전이 (Topic 2 합의 핵심)**: 여러 속성을 **연속 할당 금지**. SwiftUI body가 중간 tick에 재실행되면 invariant 깨진 상태로 1프레임 렌더 위험. **반드시 `var tmp = uiState; tmp.foo = ...; tmp.bar = ...; uiState = tmp`** 패턴 (1회 write). 또는 `uiState = uiState.with(\.foo, x).with(\.bar, y)` keyPath helper. Topic 2 합의 §123 참조.
3. **보조 @Observable property 사용 정당성 (Topic 2 미해결 후속 §147)**: 본 스펙은 uiState 외 11개 보조 property를 노출. 정당화 근거:
   - 이벤트성(notificationCallId, isAccepting, isSubmittingSettlement)은 consume-once 패턴이 자연스러움
   - 독립 lifecycle(carryOver는 별도 listener, callDetails는 별도 화면)이라 uiState와 묶을 이유 없음
   - 단 todaySettlement·tripHistoryList는 uiState와 함께 변하면 통합 검토 가능 — 매핑 구현 시 재평가
4. **@Observable 중복값 필터링 부재**: Observation framework는 raw value 비교로 차단 안 함. 핫패스(presence·FCM 연속) property는 **수동 dedup guard** 필요(Topic 2 합의 §124).
5. **Task 취소 전파**: ViewModel deinit 시 수동 cancel 필수. Kotlin `viewModelScope`처럼 자동 전파 없음. **`@MainActor deinit`은 nonisolated 컨텍스트** — MainActor-isolated 메서드 호출 불가. `Task.cancel()`은 nonisolated이므로 직접 호출 OK. 본 스펙의 deinit/stop 분리 패턴 준수.
6. **AsyncStream 리스너 해제**: `for await` 루프 내부에서 `Task.checkCancellation()` 호출 또는 외부에서 `task.cancel()` → `AsyncStream.onTermination(.cancelled)`에서 Firestore listener `.remove()` 호출. FIRESTORE.md 참조.
7. **`[weak self]` 캡처 필수**: `Task { ... }`가 보관되어 self를 강참조하면 retain cycle. 반드시 `Task { [weak self] in guard let self else { return }; ... }`. ViewModel 짧은 비동기 액션은 self 캡처 OK이나 **장수명 listener Task는 weak self 의무**.
8. **`AuthStateDidChangeListenerHandle` 해제**: `addStateDidChangeListener`는 handle 반환 — `removeStateDidChangeListener(handle)` 필수. handle leak 방지 위해 stored property로 보관.
9. **Compose `mutableStateOf`**: LoginViewModel의 `email/password` 등은 Swift @Observable var로 직접 매핑. SwiftUI `TextField("...", text: $store.email)`로 자연스럽게 바인딩 (`@Bindable var store: LoginStore` 패턴).
10. **sealed class → enum with associated values**: `LoginState.Success(provinceId, ...)` → Swift enum associated values로 1:1 매핑. associated values를 가진 enum의 `==` 비교는 수동 `Equatable` 채택 필요(필요 시).
11. **PresenceManager 의존성 주입**: 본 스펙은 `PresenceManaging` protocol로 추상화. Kotlin `object PresenceManager` 직접 호출 패턴 대신 init에서 주입 → 테스트 편의 + 의존성 명시.
12. **`Tasks: [Task<Void, Never>]` Sendable**: `Task`는 Sendable이지만 배열은 actor-isolated이므로 외부 노출 시 주의. private 보관이라 안전.

---

## 체크리스트

- [ ] DriverStore @Observable 선언, 11개 property + uiState struct
- [ ] DriverScreenUiState struct + LocationFetchStatus enum (sealed 대응)
- [ ] TodaySettlement, TripHistoryItem 보조 struct
- [ ] 13개 행동 함수 시그니처 + TODO 크로스레퍼런스 (Kotlin 라인 포함)
- [ ] 낙관적 UI 업데이트 패턴: `acceptCall` 예시로 isAccepting 가드 + defer 롤백
- [ ] carryOverListener AsyncStream 브리지 (FIRESTORE.md 크로스 레퍼런스)
- [ ] Auth state observation 등가 구현
- [ ] LoginStore 8개 함수 (login·offline·pending 체크·collectionGroup·autoLogin·logout)
- [ ] LoginStore `logout()`에서 **PresenceManager.onLogout() 호출 명시** (AND-03 교정)
- [ ] SignUpStore 3단계 의존성(province→city→office) 리셋 로직
- [ ] SessionManager/SecureStore/CustomerPointsRepository 프로토콜 정의
- [ ] Tasks 추적 배열 + **deinit/stop 분리 패턴** (deinit은 nonisolated, stop은 MainActor)
- [ ] `[weak self]` 캡처 패턴 — 장수명 listener Task 전부
- [ ] `AuthStateDidChangeListenerHandle` 보관 + removeStateDidChangeListener 호출
- [ ] `PresenceManaging` protocol로 PresenceManager 추상화 (테스트 + AND-03 교정)
- [ ] `isServiceBound` 제거 (iOS Foreground Service 없음 — FUNCTIONAL_INVENTORY §2.4 "구조적 불가" 확정)
- [ ] uiState 다중 필드 갱신 시 단일 write 패턴 강제 (`var tmp = uiState; ...; uiState = tmp`)

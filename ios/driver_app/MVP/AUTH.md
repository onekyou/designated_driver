# AUTH — 로그인·자동로그인·Keychain·Presence (Phase 1 MVP)

> **목적**: 기사앱 인증 흐름(SHARED_LOGIC.md §8 "로그인 12단계")을 Swift로 이관. Keychain·UserDefaults·PresenceManager·FCM 토큰 등록·로그아웃 cleanup까지 포괄.
> **원본 합의**: Topic 3(Keychain) · Topic 4(Presence AsyncStream) · AND-03 교정(logout 시 PresenceManager.onLogout 호출).

---

## 원본 소스

| Swift 대상 | Kotlin 파일:라인 |
|---|---|
| 로그인 전체 | `ui/login/LoginViewModel.kt:70-290` |
| 자동로그인 init | `LoginViewModel.kt:54-68` |
| 오프라인 로그인 | `LoginViewModel.kt:122-152` |
| pending_drivers 체크 | `LoginViewModel.kt:154-168` |
| collectionGroup 쿼리 | `LoginViewModel.kt:170-266` |
| SecurePreferencesManager | `util/SecurePreferencesManager.kt` (EncryptedSharedPreferences) |
| SessionManager | `util/SessionManager.kt` (UserSession 캐시) |
| PresenceManager | `service/PresenceManager.kt:23-149` |
| FCM 토큰 등록 | `MyFirebaseMessagingService.kt:27-108` |
| 로그아웃 | `LoginViewModel.kt:282` (현재 PresenceManager.onLogout 호출 누락 — 교정 대상) |

---

## 요약

- **Keychain**: Kotlin `EncryptedSharedPreferences` → Swift `Keychain Services` (`SecItem*` API). identifier/password + autoLogin 플래그.
- **UserDefaults**: 사무실 정보(provinceId/cityId/officeId/driverId/driverName) + pending FCM 토큰. 여러 컴포넌트(Store, FCM 핸들러)가 동기적으로 읽음.
- **UserSession 캐시**: Kotlin `SessionManager`는 7일 유효 오프라인 로그인용 세션을 암호화 저장. Swift는 **Keychain 하나에 JSON 블롭**으로 저장하거나 UserDefaults + Keychain 분할.
- **로그인 12단계** (SHARED_LOGIC §8): Firebase Auth → pending 체크 → collectionGroup → 경로 파싱 → approvalStatus 확인 → driver.status=ONLINE → FCM 토큰 등록 → UserDefaults 저장 → Presence 초기화 → activeCall 1회 로드 → carryOver 리스너 시작 → 정산 데이터 로드.
- **로그아웃 cleanup**: `auth.signOut()` + **PresenceManager.onLogout()** (AND-03) + Keychain autoLogin=false + Store stop().
- **FCM 토큰 재시도**: 네트워크 실패 시 UserDefaults에 `pref_pending_fcm_token` 저장, 앱 시작 시 `retryPendingFcmToken` 호출.

---

## 12단계 로그인 플로우 (SHARED_LOGIC §8)

```
1. Firebase Auth signIn(email, password)
2. pending_drivers/{uid} 체크 → 있으면 signOut "승인 대기 중"
3. collectionGroup("designated_drivers") where authUid == uid → 문서 조회
4. 문서 경로에서 provinceId/cityId/officeId 파싱
5. approvalStatus 확인 (APPROVED만 허용, 아니면 signOut)
6. driver 문서 status=ONLINE 업데이트
7. FCM 토큰 등록 (driver.fcmToken)
8. UserDefaults에 provinceId/cityId/officeId/driverId 저장
9. PresenceManager.initialize() — Realtime DB .info/connected + onDisconnect → offline
10. loadCurrentActiveCall() 1회 조회 (진행 중인 콜 복구)
11. carryOverListener 시작 (유일 실시간 리스너)
12. Settlement 데이터 로드
```

이후 `LoginState.success`로 전이 → NavigationStack 홈 화면 전환.

---

## Keychain 스펙

### 저장 항목

| 키 (kSecAttrAccount) | 값 | 접근성 |
|---|---|---|
| `"autoLogin"` | `"true"` / `"false"` (String) | `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` |
| `"savedIdentifier"` | 이메일 또는 전화번호 | 동일 |
| `"savedPassword"` | 비밀번호 평문 | 동일 |
| `"userSession"` | `UserSession` JSON blob (7일 캐시) | 동일 |

**kSecAttrService**: `"com.designated.driver.ios.credentials"` (앱 식별자 기반 단일 네임스페이스)

### 접근성 선택 근거

- `.afterFirstUnlockThisDeviceOnly`: **재부팅 후 첫 잠금 해제 이후** 접근 가능. 백그라운드 FCM 수신 시 Keychain 접근 가능하되 디바이스 교체·백업 복원 시엔 이전 자격증명 유입 방지. Kotlin `EncryptedSharedPreferences` 기본 동작과 유사.
- `.whenUnlocked`는 너무 제한적(잠금 해제 시에만), `.always`는 보안 약함.

### 의사 스펙 (Swift)

```swift
import Security
import Foundation

protocol SecureStore {
    func isAutoLoginEnabled() -> Bool
    func setAutoLoginEnabled(_ enabled: Bool)
    func getSavedIdentifier() -> String?
    func getSavedPassword() -> String?
    func saveCredentials(identifier: String, password: String)
    func clearCredentials()
    func saveUserSession(_ session: UserSession)
    func loadUserSession() -> UserSession?
    func clearUserSession()
}

final class KeychainSecureStore: SecureStore {
    private let service = "com.designated.driver.ios.credentials"

    func isAutoLoginEnabled() -> Bool {
        read(account: "autoLogin").flatMap { String(data: $0, encoding: .utf8) } == "true"
    }

    func setAutoLoginEnabled(_ enabled: Bool) {
        write(account: "autoLogin", data: Data((enabled ? "true" : "false").utf8))
    }

    func getSavedIdentifier() -> String? {
        read(account: "savedIdentifier").flatMap { String(data: $0, encoding: .utf8) }
    }

    func getSavedPassword() -> String? {
        read(account: "savedPassword").flatMap { String(data: $0, encoding: .utf8) }
    }

    func saveCredentials(identifier: String, password: String) {
        write(account: "savedIdentifier", data: Data(identifier.utf8))
        write(account: "savedPassword", data: Data(password.utf8))
    }

    func clearCredentials() {
        delete(account: "savedIdentifier")
        delete(account: "savedPassword")
        delete(account: "autoLogin")
    }

    func saveUserSession(_ session: UserSession) {
        guard let data = try? JSONEncoder().encode(session) else { return }
        write(account: "userSession", data: data)
    }

    func loadUserSession() -> UserSession? {
        read(account: "userSession").flatMap { try? JSONDecoder().decode(UserSession.self, from: $0) }
    }

    func clearUserSession() { delete(account: "userSession") }

    // MARK: - Keychain primitives
    private func write(account: String, data: Data) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        let attrs: [String: Any] = [
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemUpdate(query as CFDictionary, attrs as CFDictionary)
        if status == errSecItemNotFound {
            var addQuery = query
            addQuery.merge(attrs) { _, new in new }
            SecItemAdd(addQuery as CFDictionary, nil)
        }
    }

    private func read(account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return data
    }

    private func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
```

---

## UserDefaults 스펙 (사무실 컨텍스트)

Kotlin `Constants.kt:36-39` + `data/Constants.kt` PREF_KEY_*.

### 저장 항목

| 키 | 값 | 비고 |
|---|---|---|
| `"pref_province_id"` | `String` | FCM 서비스·리스너·트랜잭션 전부 참조 |
| `"pref_city_id"` | `String` | 동일 |
| `"pref_office_id"` | `String` | 동일 |
| `"pref_driver_id"` | `String` | 로그인 시점 쿼리 결과 |
| `"pref_driver_name"` | `String` | 화면 표시용 |
| `"pref_pending_fcm_token"` | `String?` | 네트워크 실패 시 임시 저장 |
| `"pref_fcm_token"` | `String?` | 현재 등록된 토큰 (갱신 추적용) |

### 의사 스펙

```swift
extension UserDefaults {
    var provinceId: String? {
        get { string(forKey: "pref_province_id") }
        set { set(newValue, forKey: "pref_province_id") }
    }
    var cityId: String? {
        get { string(forKey: "pref_city_id") }
        set { set(newValue, forKey: "pref_city_id") }
    }
    var officeId: String? {
        get { string(forKey: "pref_office_id") }
        set { set(newValue, forKey: "pref_office_id") }
    }
    var driverId: String? {
        get { string(forKey: "pref_driver_id") }
        set { set(newValue, forKey: "pref_driver_id") }
    }
    var pendingFcmToken: String? {
        get { string(forKey: "pref_pending_fcm_token") }
        set { set(newValue, forKey: "pref_pending_fcm_token") }
    }

    func clearDriverContext() {
        ["pref_province_id", "pref_city_id", "pref_office_id",
         "pref_driver_id", "pref_driver_name",
         "pref_fcm_token", "pref_pending_fcm_token"].forEach { removeObject(forKey: $0) }
    }
}
```

**주의**: 키 문자열은 Kotlin 원본과 동일 유지(앱 간 디버깅·크로스 앱 참조 편의).

---

## SessionManager (UserSession 캐시, 오프라인 로그인용)

Kotlin 7일 유효 세션 저장 → Swift도 동일 정책. Keychain `userSession` 슬롯에 JSON 저장.

```swift
protocol SessionManager {
    func save(_ session: UserSession)
    func load() -> UserSession?
    func canLoginOffline() -> Bool
    func clear()
}

final class KeychainSessionManager: SessionManager {
    private let store: SecureStore
    init(store: SecureStore) { self.store = store }

    func save(_ session: UserSession) { store.saveUserSession(session) }
    func load() -> UserSession? {
        guard let s = store.loadUserSession() else { return nil }
        return s.isExpired() ? nil : s
    }
    func canLoginOffline() -> Bool { load() != nil }
    func clear() { store.clearUserSession() }
}
```

---

## PresenceManager 이관

Kotlin `service/PresenceManager.kt:23-149` → Swift singleton/actor.

### 의사 스펙

```swift
import FirebaseDatabase
import FirebaseAuth
import Observation

@Observable
@MainActor
final class PresenceManager {
    static let shared = PresenceManager()

    enum Status: String {
        case online = "online"
        case background = "background"
        case offline = "offline"
    }

    // UI 배너용 (Topic 4 Firebase .info/connected Observable)
    var isConnected: Bool = true

    private let database = Database.database()
    private var presenceRef: DatabaseReference?
    private var connectedRef: DatabaseReference?
    private var connectedHandle: DatabaseHandle?

    private init() {}

    func initialize() {
        guard let userId = Auth.auth().currentUser?.uid else { return }
        presenceRef = database.reference(withPath: "presence/drivers/\(userId)")
        connectedRef = database.reference(withPath: ".info/connected")

        connectedHandle = connectedRef?.observe(.value) { [weak self] snap in
            let connected = snap.value as? Bool ?? false
            self?.isConnected = connected
            guard connected, let ref = self?.presenceRef else { return }

            // 연결 끊김 시 자동 offline 설정
            ref.onDisconnectSetValue([
                "status": Status.offline.rawValue,
                "lastSeen": ServerValue.timestamp()
            ])

            self?.setStatus(.online)
        }
    }

    func setStatus(_ status: Status) {
        presenceRef?.setValue([
            "status": status.rawValue,
            "lastSeen": ServerValue.timestamp()
        ])
    }

    func onAppForeground() { setStatus(.online) }
    func onAppBackground() { setStatus(.background) }

    // AND-03 교정: logout 시 반드시 호출
    // async로 정의해 caller가 setValue 완료 대기 가능 (Realtime DB는 콜백 기반이라
    // continuation 래핑 필요 시 추가 — 현 setValue는 fire-and-forget이라도 cleanup은 동기 OK).
    func onLogout() async {
        setStatus(.offline)
        cleanup()
    }

    func cleanup() {
        if let h = connectedHandle { connectedRef?.removeObserver(withHandle: h) }
        connectedHandle = nil
        presenceRef = nil
        connectedRef = nil
    }

    func reinitialize() {
        cleanup()
        initialize()
    }
}
```

### ScenePhase 연동

`App` struct에서:
```swift
@Environment(\.scenePhase) private var scenePhase

// .onChange(of: scenePhase) { _, newPhase in
//     switch newPhase {
//     case .active: PresenceManager.shared.onAppForeground()
//     case .background: PresenceManager.shared.onAppBackground()
//     default: break
//     }
// }
```

Kotlin `ProcessLifecycleOwner` 등가.

---

## FCM 토큰 등록

Kotlin `MyFirebaseMessagingService.onNewToken` → Swift `MessagingDelegate.didReceiveRegistrationToken`.

### 의사 스펙

```swift
import FirebaseMessaging

final class FCMTokenHandler: NSObject, MessagingDelegate {
    static let shared = FCMTokenHandler()

    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken else { return }
        Task { await sendRegistrationToServer(token: token) }
    }

    private func sendRegistrationToServer(token: String) async {
        guard let userId = Auth.auth().currentUser?.uid,
              let provinceId = UserDefaults.standard.provinceId,
              let cityId = UserDefaults.standard.cityId,
              let officeId = UserDefaults.standard.officeId else { return }

        // 실패 대비 pending 저장
        UserDefaults.standard.pendingFcmToken = token

        let driverRef = FirestorePaths.driver(provinceId, cityId, officeId, userId)
        do {
            try await driverRef.updateData(["fcmToken": token])
            UserDefaults.standard.pendingFcmToken = nil
            UserDefaults.standard.set(token, forKey: "pref_fcm_token")
        } catch {
            // pending 유지 → 앱 시작 시 retryPendingFcmToken 호출
        }
    }

    static func retryPending() async {
        guard let token = UserDefaults.standard.pendingFcmToken,
              !token.isEmpty else { return }
        await shared.sendRegistrationToServer(token: token)
    }
}
```

**주의**: Kotlin `MyFirebaseMessagingService.kt:125-131`의 "로그인 체크" 로직과 동일 — `officeId` 없으면 저장 스킵. Swift에서도 UserDefaults.provinceId 등 사무실 컨텍스트 유무 확인.

**Phase 2 (R1)**: VoIP token(PushKit) 등록은 별도 파일. Phase 1은 APNs → FCM 브리지만.

---

## 로그인 함수 전체 의사 스펙

Kotlin `LoginViewModel.kt:70-266` 요약:

```swift
@MainActor
extension LoginStore {
    func login() async {
        // Swift는 String.isBlank 표준 없음 — Kotlin 관용 → trim().isEmpty 사용
        let id = email.trimmingCharacters(in: .whitespacesAndNewlines)
        let pw = password.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty, !pw.isEmpty else {
            loginState = .error(message: "이메일(또는 전화번호)과 비밀번호를 모두 입력해주세요.")
            return
        }
        loginState = .loading
        do {
            // 1. 온라인 signIn
            let result = try await auth.signIn(withEmail: email, password: password)
            let userId = result.user.uid
            await checkPendingStatusAndProceed(userId: userId)
        } catch {
            // 2. 네트워크 에러 + 오프라인 세션 있으면 오프라인 로그인
            if isNetworkError(error), sessionManager.canLoginOffline() {
                attemptOfflineLogin()
            } else {
                loginState = .error(message: error.localizedDescription)
            }
        }
    }

    private func attemptOfflineLogin() {
        guard let session = sessionManager.load() else {
            loginState = .error(message: "세션이 만료되었습니다. 재로그인 필요.")
            return
        }
        loginState = .success(
            provinceId: session.provinceId,
            cityId: session.cityId,
            officeId: session.officeId,
            driverId: session.driverId,
            needsTokenUpdate: false
        )
    }

    private func checkPendingStatusAndProceed(userId: String) async {
        let pendingRef = Firestore.firestore().collection("pending_drivers").document(userId)
        do {
            let doc = try await pendingRef.getDocument()
            if doc.exists {
                try? auth.signOut()
                loginState = .error(message: "승인 대기 중입니다.")
                return
            }
            await findDriverDocumentAndSaveInfo(userId: userId)
        } catch {
            try? auth.signOut()
            loginState = .error(message: error.localizedDescription)
        }
    }

    private func findDriverDocumentAndSaveInfo(userId: String) async {
        do {
            guard let ctx = try await DriverRepositoryImpl().findDriverContext(authUid: userId) else {
                try? auth.signOut()
                loginState = .error(message: "기사 정보를 찾을 수 없습니다.")
                return
            }
            guard ctx.approvalStatus == .approved else {
                try? auth.signOut()
                loginState = .error(message: "승인되지 않은 계정입니다.")
                return
            }

            // 6. driver.status = ONLINE
            try await FirestorePaths.driver(ctx.provinceId, ctx.cityId, ctx.officeId, ctx.driverId)
                .updateData(["status": DriverStatus.online.rawValue])

            // 7. FCM 토큰 등록 (이미 onNewToken에서 처리되지만 최초 로그인 시 보강)
            if let token = try? await Messaging.messaging().token() {
                try await FirestorePaths.driver(ctx.provinceId, ctx.cityId, ctx.officeId, ctx.driverId)
                    .updateData(["fcmToken": token])
            }

            // 8. UserDefaults
            UserDefaults.standard.provinceId = ctx.provinceId
            UserDefaults.standard.cityId = ctx.cityId
            UserDefaults.standard.officeId = ctx.officeId
            UserDefaults.standard.driverId = ctx.driverId

            // Keychain 자동로그인 저장
            if autoLogin {
                secureStore.saveCredentials(identifier: email, password: password)
                secureStore.setAutoLoginEnabled(true)
            }

            // UserSession 캐시 (오프라인 로그인 대비)
            sessionManager.save(UserSession(
                userId: userId,
                email: email,
                provinceId: ctx.provinceId,
                cityId: ctx.cityId,
                officeId: ctx.officeId,
                driverId: ctx.driverId,
                driverName: "",  // TODO: 문서에서 driverName 필드 읽기
                fcmToken: try? await Messaging.messaging().token(),
                lastLoginTime: Date(),
                isOnline: true
            ))

            // 9. PresenceManager 초기화
            PresenceManager.shared.initialize()

            // 10~12는 Store.initializeListenersWithInfo 호출로 위임
            loginState = .success(
                provinceId: ctx.provinceId,
                cityId: ctx.cityId,
                officeId: ctx.officeId,
                driverId: ctx.driverId,
                needsTokenUpdate: true
            )
        } catch {
            try? auth.signOut()
            loginState = .error(message: error.localizedDescription)
        }
    }

    func toggleAutoLogin(_ enabled: Bool) {
        autoLogin = enabled
        if !enabled {
            secureStore.clearCredentials()
        }
    }

    // AND-03 교정: PresenceManager.onLogout() 명시
    // VIEWMODELS.md와 일관성 위해 async + protocol 주입 권장 (PresenceManaging).
    // 본 예시는 singleton 직접 호출이지만 실제 구현은 init에서 주입한 인스턴스 사용.
    func logout() async {
        await PresenceManager.shared.onLogout()   // signOut 전에 호출 — 순서 중요
        try? auth.signOut()
        sessionManager.clear()
        UserDefaults.standard.clearDriverContext()
        // autoLogin=false여도 identifier/password는 사용자가 선택한 경우 유지
        loginState = .idle
    }
}
```

---

## iOS 특수 사항

1. **Keychain 접근성**: `.afterFirstUnlockThisDeviceOnly` 권장. `.thisDeviceOnly` 접미사는 iCloud Keychain 동기화 차단.
2. **Keychain vs UserDefaults 경계**: 비밀번호·UserSession은 Keychain, 비민감 컨텍스트(provinceId 등)는 UserDefaults. Keychain은 백그라운드 FCM 수신 시에도 접근 필요하므로 접근성 중요.
3. **`@MainActor PresenceManager`**: UI 바인딩용 `isConnected` 업데이트 때문에 MainActor. Firebase Database 콜백은 내부 큐에서 오므로 closure 내부에서 `[weak self]` + Task { @MainActor in ... } 권장. 본 스펙 코드의 `self?.isConnected = connected`는 MainActor isolation 위반 가능 → 실 구현 시 Task wrap 필요.
4. **ScenePhase**: Android ProcessLifecycleOwner와 다르게 scene 단위 관측. 멀티 윈도우 iPad 대응 필요 없으면 단일 scene으로 전제.
5. **FCM vs APNs 토큰**: Firebase 11.x는 내부에서 APNs → FCM 토큰 변환. 앱에선 FCM 토큰만 관리. `Messaging.messaging().token()` async 호출은 내부에서 토큰 교환 자동 수행.
6. **오프라인 로그인 UX**: `attemptOfflineLogin`은 Kotlin 기존 동작 유지 — 네트워크 에러일 때만. "비밀번호 틀림" 에러는 오프라인 폴백 안 함. **`isNetworkError(error)` 판정 기준**: `(error as NSError).domain == NSURLErrorDomain` 또는 `AuthErrorCode(rawValue: (error as NSError).code) == .networkError`. 정확한 분기 구현 필요.
7. **PresenceManager.onLogout**: AND-03 교정 — 반드시 `signOut()` 전에 `onLogout()` 호출. 순서 뒤바뀌면 authStateListener가 먼저 발동해 PresenceManager가 stale ref에 쓰기 시도 가능.
8. **`String.isBlank` 부재**: Kotlin 관용. Swift는 `trimmingCharacters(in: .whitespacesAndNewlines).isEmpty` 사용.
9. **`PresenceManaging` protocol**: VIEWMODELS.md와 일관 — singleton 직접 호출 대신 init 주입 권장. 테스트 용이.
10. **`UserSession.driverName` 보강**: `findDriverContext`가 driver 문서에서 `name` 필드도 함께 읽어 ctx에 포함 → UserSession 저장 시 빈 문자열 회피.

---

## 체크리스트

- [ ] `KeychainSecureStore` 구현 (save/read/delete + 7일 UserSession blob)
- [ ] `KeychainSessionManager` (isExpired 체크)
- [ ] UserDefaults extension 7개 프로퍼티 + clearDriverContext()
- [ ] `PresenceManager.shared` singleton + ScenePhase 연동
- [ ] `initialize / setStatus / onAppForeground / onAppBackground / onLogout / cleanup / reinitialize` API
- [ ] `FCMTokenHandler` MessagingDelegate + retryPending
- [ ] LoginStore `login / attemptOfflineLogin / checkPendingStatusAndProceed / findDriverDocumentAndSaveInfo / toggleAutoLogin / logout`
- [ ] **logout에서 PresenceManager.onLogout() 명시** (AND-03)
- [ ] `SignUpStore.signUp()` pending_drivers 문서 생성 (SCREENS.md 연계)
- [ ] 12단계 로그인 플로우 주석 복원 (위 코드에 라인 번호 주석 추가)
- [ ] Keychain 접근성 `.afterFirstUnlockThisDeviceOnly` 설정
- [ ] FCM 토큰 저장 실패 시 pending 유지 + 앱 시작 시 retry 호출처 (App struct의 `.onAppear` 등)

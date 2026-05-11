# iOS 기사앱 Phase 1 — PUSH (알림 처리 전수)

> **범위**: APNs 등록, FCM 토큰 업로드, UNUserNotificationCenter 설정, FCM 6종 핸들링, Delivery ACK, Phase 1 신규 UI(손님 취소 Alert / 타임아웃 Banner).
> **제외 (Phase 2)**: VoIP+CallKit, Live Activity Push-to-Start, LockScreen 풀스크린 — `R1_LOCKSCREEN.md`, `R1_APP_KILLED.md` 참조.
> **전제**: iOS 17.2 deployment target, Swift 5.10+, Firebase iOS SDK (Messaging + Functions + Firestore), `@MainActor @Observable` Store 패턴.

---

## 1. 원본 소스

- Android 구현: `MyFirebaseMessagingService.kt:27-396`, `LockScreenActivity.kt`(Phase 2 deferred)
- CF 발신 측: `functions/src/index.ts` (`oncallassigned:478`, `onCallStatusChanged:1810`, `notifyDriverCancellation`, `acknowledgeNotification:242`)
- 인벤토리: `ios/FUNCTIONAL_INVENTORY.md` §2.2
- 합의: `ios/WORKING_DOC.md` 주제 5 (Phase 분리 결정)

---

## 2. 요약

Phase 1은 **일반 APNs + FCM data-only push + UNNotification Time Sensitive**로 6종 메시지를 처리한다. 잠금화면 풀스크린·반복 벨소리·앱 강제 종료 깨움은 모두 Phase 2(R1)로 이관. CallKit·PushKit·VoIP 인증서·Background Mode `voip` 설정은 Phase 1 코드에 포함하지 않는다.

푸시 처리 책임 분리:
- **AppDelegate** (또는 `@UIApplicationDelegateAdaptor`): APNs 등록, FCM delegate, UNUserNotificationCenter delegate. 라이프사이클과 무관하게 상시 유지
- **PushRouter** (singleton, Sendable): 수신 페이로드를 분류하여 NotificationCenter 또는 AsyncStream 이벤트 채널로 publish. ViewModel/Store는 이 채널을 구독
- **Notification UI**: SwiftUI View가 ScenePhase·@Observable Store 변화를 관찰하여 Alert/Banner 표시

---

## 3. iOS 설정

### 3.1 Capabilities (Xcode → Signing & Capabilities)

| Capability | Phase 1 | Phase 2 (R1) |
|---|---|---|
| **Push Notifications** | ✓ | ✓ |
| **Background Modes → Remote notifications** | ✓ | ✓ |
| **Background Modes → Voice over IP** | ✗ | ✓ (R1) |
| **Background Modes → Audio** | ✗ | ✓ (CallKit ringtone, R1) |
| **App Groups** | ✗ | ✓ (Live Activity 공유, R1) |

### 3.2 Info.plist

```xml
<key>UIBackgroundModes</key>
<array>
    <string>remote-notification</string>
</array>

<key>NSUserNotificationsUsageDescription</key>
<string>배차 알림을 받기 위해 알림을 허용해주세요.</string>

<key>FirebaseAppDelegateProxyEnabled</key>
<false/>
```

`FirebaseAppDelegateProxyEnabled = false`로 두고 APNs 토큰을 직접 Messaging에 전달하는 패턴 권장 (control 명확).

### 3.3 APNs 인증서

- Apple Developer → Keys → "Apple Push Notifications service (APNs)" 키(p8) 발급
- Firebase Console → Project Settings → Cloud Messaging → APNs Authentication Key 등록 (Key ID + Team ID)
- Phase 1은 **단일 키**로 충분. VoIP 채택 시(R1) 동일 p8 키가 VoIP push에도 사용 가능 (`apns-push-type: voip` 헤더 분리)

### 3.4 Firebase iOS SDK 의존성 (Swift Package)

```
https://github.com/firebase/firebase-ios-sdk
```

라이브러리:
- `FirebaseCore`
- `FirebaseAuth`
- `FirebaseFirestore`
- `FirebaseMessaging`
- `FirebaseDatabase` (Realtime DB / Presence)
- `FirebaseFunctions`

---

## 4. 스펙

### 4.1 AppDelegate — 등록 코드

```swift
import UIKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

@main
struct DriverAppMain: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    var body: some Scene { WindowGroup { RootView() } }
}

final class AppDelegate: NSObject, UIApplicationDelegate {

    func application(_ app: UIApplication,
                     didFinishLaunchingWithOptions opts: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        Task { @MainActor in
            let granted = (try? await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            if granted {
                await UIApplication.shared.registerForRemoteNotifications()
            }
        }
        registerNotificationCategories()
        return true
    }

    func application(_ app: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
        Messaging.messaging().apnsToken = token   // FirebaseAppDelegateProxyEnabled = false 전제
    }

    func application(_ app: UIApplication,
                     didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // 로깅. 다음 launch에 재시도됨
    }
}
```

### 4.2 FCM 토큰 수신 + 서버 업로드

```swift
extension AppDelegate: MessagingDelegate {
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        Task { await TokenUploader.shared.upload(fcmToken: fcmToken) }
    }
}

actor TokenUploader {
    static let shared = TokenUploader()
    private var lastUploaded: String?

    func upload(fcmToken: String) async {
        guard fcmToken != lastUploaded else { return }
        guard let uid = Auth.auth().currentUser?.uid,
              let path = SessionContext.shared.driverDocPath() else {
            // 미로그인 시 UserDefaults에 pending 토큰 저장 → 로그인 직후 retry
            UserDefaults.standard.set(fcmToken, forKey: "pendingFcmToken")
            return
        }
        do {
            try await Firestore.firestore().document(path)
                .updateData(["fcmToken": fcmToken, "fcmTokenUpdatedAt": FieldValue.serverTimestamp()])
            lastUploaded = fcmToken
            UserDefaults.standard.removeObject(forKey: "pendingFcmToken")
        } catch {
            // 다음 onTokenChange/launch에서 재시도 — pending 보존
        }
    }

    func retryPendingIfAny() async {
        if let t = UserDefaults.standard.string(forKey: "pendingFcmToken") { await upload(fcmToken: t) }
    }
}
```

- `path` = `provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}` (로그인 시 SessionContext에 저장)
- 미로그인 토큰 발급 → UserDefaults pending 저장 → 로그인 완료 직후 `retryPendingIfAny()` 호출

### 4.3 UNUserNotificationCenter 카테고리

```swift
private func registerNotificationCategories() {
    let openSettlement = UNNotificationAction(
        identifier: "openSettlement", title: "정산 보기", options: [.foreground]
    )
    let settlement = UNNotificationCategory(
        identifier: "SETTLEMENT", actions: [openSettlement], intentIdentifiers: []
    )
    let dispatch = UNNotificationCategory(
        identifier: "DISPATCH", actions: [], intentIdentifiers: []
    )
    UNUserNotificationCenter.current().setNotificationCategories([settlement, dispatch])
}
```

### 4.4 Time Sensitive Notification 설정

CF에서 보내는 FCM `notification` payload에 `interruption-level` 지정. data-only push인 경우 클라이언트가 로컬 알림 생성 시 `content.interruptionLevel = .timeSensitive` 직접 지정.

```swift
private func makeContent(title: String, body: String, category: String, userInfo: [String: Any]) -> UNMutableNotificationContent {
    let c = UNMutableNotificationContent()
    c.title = title
    c.body = body
    c.sound = .default
    c.categoryIdentifier = category
    c.interruptionLevel = .timeSensitive   // Focus 우회
    c.userInfo = userInfo
    return c
}
```

> **Entitlement**: Time Sensitive Notification은 **Communication Notifications와 달리 별도 entitlement 불필요**. `interruptionLevel` 지정만으로 동작. Apple Dev 문서 재확인 필요.

### 4.5 FCM 6종 핸들링 매트릭스

수신 진입점은 두 가지:
- **Foreground**: `MessagingDelegate.messaging(_:didReceive:)` (data) + `UNUserNotificationCenterDelegate.userNotificationCenter(_:willPresent:)` (alert)
- **Background/Killed (notification 표시)**: `userNotificationCenter(_:didReceive:withCompletionHandler:)` (사용자 탭 시)
- **Background data-only**: `application(_:didReceiveRemoteNotification:fetchCompletionHandler:)` (백그라운드 wakeup, 약 30초 실행)

| messageType | Foreground 동작 | Background 동작 | 잠금화면 | UI 결과 | Phase |
|---|---|---|---|---|---|
| `call_assigned` | banner suppressed → 인앱 NewCallSheet 표시 + Delivery ACK | Time Sensitive 알림(잠금화면 배너) + 탭 시 NewCallSheet 라우팅 + Delivery ACK | 배너 + 사운드 1회 | NewCallSheet (수락/거절) | **B** (Phase 1) |
| `call_cancelled` | 인앱 Alert "고객/관리자가 콜을 취소했습니다" + 콜 화면 자동 정리 + 홈 이동 | Time Sensitive 알림 + 탭 시 홈 라우팅 | 배너 | Alert + popToRoot | **B** |
| `SETTLEMENT_FINALIZED` | 인앱 정산 갱신 (Toast 수준) | 일반 알림 (`.active` 또는 `.timeSensitive` 선택 — Phase 1은 `.active`로 배너만) | 배너 | 정산 화면 자동 갱신 | **B** |
| `SETTLEMENT_CONFIRMED` | 인앱 Alert "정산 확인 완료. 퇴근할 수 있습니다" | 알림 + 탭 시 정산 화면 라우팅 | 배너 | Alert | **B** |
| `SETTLEMENT_REJECTED` | 인앱 Alert "재제출해주세요" + 정산 화면 자동 진입 | 알림 + 탭 시 정산 화면 라우팅 | 배너 | Alert + 정산 진입 | **B** |
| `CARRYOVER_TRANSFERRED` | 인앱 토스트 "이체 완료" + 이월금 카드 갱신 | 알림 + 탭 시 정산 화면 | 배너 | 토스트 | **B** |

### 4.6 PushRouter (수신 분기)

```swift
struct PushPayload: Sendable {
    let messageType: String
    let userInfo: [String: Any]
}

@MainActor
@Observable
final class PushRouter {
    static let shared = PushRouter()

    var pendingNewCall: NewCallPayload? = nil
    var pendingCancellation: CancellationPayload? = nil
    var pendingSettlementFinalized: SettlementSummary? = nil
    var pendingSettlementConfirmed: Bool = false
    var pendingSettlementRejected: String? = nil
    var pendingCarryoverTransferred: Bool = false

    func dispatch(_ payload: PushPayload) {
        switch payload.messageType {
        case "call_assigned":
            if let p = NewCallPayload(userInfo: payload.userInfo) {
                pendingNewCall = p
                Task { await DeliveryACK.send(notificationId: p.notificationId) }
            }
        case "call_cancelled":
            pendingCancellation = CancellationPayload(userInfo: payload.userInfo)
        case "SETTLEMENT_FINALIZED":
            pendingSettlementFinalized = SettlementSummary(userInfo: payload.userInfo)
        case "SETTLEMENT_CONFIRMED":
            pendingSettlementConfirmed = true
        case "SETTLEMENT_REJECTED":
            pendingSettlementRejected = (payload.userInfo["reason"] as? String) ?? ""
        case "CARRYOVER_TRANSFERRED":
            pendingCarryoverTransferred = true
        default:
            break
        }
    }

    func consumeNewCall() -> NewCallPayload? { defer { pendingNewCall = nil }; return pendingNewCall }
    func consumeCancellation() -> CancellationPayload? { defer { pendingCancellation = nil }; return pendingCancellation }
    // ... 나머지 consume API 동일 패턴 (Topic 2: consume-once)
}
```

각 화면은 `.onChange(of: PushRouter.shared.pendingXxx)`로 관찰 후 `consume...()` 호출. ViewModel lifecycle과 무관하게 PushRouter는 AppDelegate 단계에서 살아있음(Topic 2 합의 §6).

### 4.7 UNUserNotificationCenterDelegate

```swift
extension AppDelegate: UNUserNotificationCenterDelegate {
    // Foreground 수신: banner 보여줄지 결정
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                willPresent notification: UNNotification,
                                withCompletionHandler completionHandler:
                                @escaping (UNNotificationPresentationOptions) -> Void) {
        let userInfo = notification.request.content.userInfo
        let messageType = (userInfo["messageType"] as? String) ?? ""
        Task { @MainActor in
            PushRouter.shared.dispatch(PushPayload(messageType: messageType, userInfo: userInfo))
        }
        if messageType == "call_assigned" {
            // 인앱 시트가 직접 표시할 것 → 배너 억제
            completionHandler([])
        } else {
            completionHandler([.banner, .list, .sound])
        }
    }

    // 사용자 탭(백그라운드/잠금에서)
    func userNotificationCenter(_ center: UNUserNotificationCenter,
                                didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let userInfo = response.notification.request.content.userInfo
        let messageType = (userInfo["messageType"] as? String) ?? ""
        Task { @MainActor in
            PushRouter.shared.dispatch(PushPayload(messageType: messageType, userInfo: userInfo))
            // 라우팅(navigateTo)
            if let nav = userInfo["navigateTo"] as? String {
                AppRouter.shared.navigate(to: nav)
            }
        }
        completionHandler()
    }
}

// data-only push (background wakeup)
extension AppDelegate {
    func application(_ application: UIApplication,
                     didReceiveRemoteNotification userInfo: [AnyHashable : Any],
                     fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        let messageType = (userInfo["messageType"] as? String) ?? ""
        let normalized = userInfo.reduce(into: [String: Any]()) {
            if let k = $1.key as? String { $0[k] = $1.value }
        }
        Task { @MainActor in
            PushRouter.shared.dispatch(PushPayload(messageType: messageType, userInfo: normalized))
        }
        completionHandler(.newData)
    }
}
```

> **주의**: 사용자가 앱을 강제 종료한 상태에선 `application(_:didReceiveRemoteNotification:)` 호출되지 않음. 이 시나리오 보강은 R1(VoIP push).

### 4.8 Delivery ACK

```swift
enum DeliveryACK {
    static func send(notificationId: String?) async {
        guard let notificationId else { return }
        let callable = Functions.functions().httpsCallable("acknowledgeNotification")
        do {
            _ = try await callable.call(["notificationId": notificationId])
        } catch {
            // 단일 호출 실패는 비치명. 재시도 안 함 (Android 동작 동등)
        }
    }
}
```

호출 시점: `call_assigned` 분기에서 PushRouter가 dispatch할 때. VoIP 채택 시(R1)는 `reportNewIncomingCall` 호출 **이후**에 ACK.

### 4.9 손님 취소 Alert UI (0.5일 신규)

```swift
struct DriverHomeView: View {
    @State private var router = PushRouter.shared
    @State private var alertItem: CancellationPayload? = nil
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        content
            .onChange(of: router.pendingCancellation) { _, _ in
                alertItem = router.consumeCancellation()
                NavRoot.shared.popToHome()
            }
            .alert(item: $alertItem) { payload in
                Alert(
                    title: Text("콜 취소"),
                    message: Text(payload.message ?? "고객이 콜을 취소했습니다"),
                    dismissButton: .default(Text("확인"))
                )
            }
    }
}
```

CallKit 도입 시(R1)는 동시에 `provider.reportCall(with:endedAt:reason:.remoteEnded)` 추가.

### 4.10 타임아웃 Banner UI (0.25일 신규)

CF가 3분 타임아웃 시 `call_cancelled` FCM에 `reason="timeout"` 포함하여 전송 (이미 합의됨, OVERVIEW §4). 기사앱은 동일 핸들러로 받고 reason 분기:

```swift
// PushRouter consume 시점에 reason 검사
if let cancellation = router.consumeCancellation() {
    if cancellation.reason == "timeout" {
        timeoutBanner = "콜 응답 시간이 초과되어 다른 기사에게 재배차됩니다"
        // 무음. 자동 dismiss 5초
    } else {
        alertItem = cancellation
    }
}
```

```swift
.overlay(alignment: .top) {
    if let msg = timeoutBanner {
        TimeoutBanner(text: msg).task {
            try? await Task.sleep(nanoseconds: 5_000_000_000)
            timeoutBanner = nil
        }
    }
}
```

알림 측은 `interruptionLevel = .active` (소리 없음, 잠금화면 배너 정도).

---

## 5. 체크리스트

### 5.1 설정
- [ ] Push Notifications capability 활성
- [ ] Background Modes → Remote notifications 체크
- [ ] APNs Auth Key (p8) 발급 + Firebase Console 등록
- [ ] `FirebaseAppDelegateProxyEnabled = false` Info.plist 추가
- [ ] `NSUserNotificationsUsageDescription` 한국어 문구 추가
- [ ] Firebase iOS SDK Swift Package 의존성 추가 (Core/Auth/Firestore/Messaging/Database/Functions)
- [ ] `GoogleService-Info.plist` 프로젝트 루트 배치 (SETTINGS.md 참조)

### 5.2 코드
- [ ] `AppDelegate` — `FirebaseApp.configure()` + delegate 등록
- [ ] `requestAuthorization` 호출 + `registerForRemoteNotifications`
- [ ] `MessagingDelegate.didReceiveRegistrationToken` → `TokenUploader`
- [ ] `TokenUploader` — Firestore driver 문서 update + UserDefaults pending fallback
- [ ] `UNUserNotificationCenterDelegate.willPresent` (foreground)
- [ ] `UNUserNotificationCenterDelegate.didReceive` (탭) + `navigateTo` 라우팅
- [ ] `application(_:didReceiveRemoteNotification:)` data-only background
- [ ] `PushRouter` @Observable + 6종 dispatch + consume-once 패턴
- [ ] `DeliveryACK.send` Functions Callable
- [ ] 알림 카테고리 등록 (DISPATCH, SETTLEMENT)
- [ ] 손님 취소 Alert + popToHome
- [ ] 타임아웃 Banner + 자동 dismiss

### 5.3 검증 (TestFlight 파일럿)
- [ ] 6종 메시지 모두 foreground 수신 → 인앱 UI 반영
- [ ] 6종 메시지 모두 background 수신 → 잠금화면 배너 + 탭 시 라우팅
- [ ] data-only `call_assigned` background wakeup → Delivery ACK 송신 확인 (CF 로그)
- [ ] 알림 권한 거부 시 graceful degradation (앱 크래시 없음)
- [ ] FCM 토큰 갱신 시 driver 문서 자동 update
- [ ] 미로그인 토큰 발급 → 로그인 직후 pending 업로드
- [ ] Time Sensitive: Focus Mode "방해 금지" 켠 상태에서 배너 표시되는지 실기기 확인

---

## 6. Phase 2 deferred 항목 (R1/R2)

| 항목 | 사유 | 이관 문서 |
|---|---|---|
| VoIP push + CallKit (잠금화면 풀스크린, 강제종료 깨움, 반복 ringtone) | 심사 리스크 + 큰 공수, Phase 1 데이터로 도입 결정 | `R1_LOCKSCREEN.md`, `R1_APP_KILLED.md` |
| Live Activity Push-to-Start (잠금화면 커스텀 카드) | CallKit 보강. Phase 2 동시 도입 | `R1_LIVE_ACTIVITY.md` |
| 3초 간격 반복 알림음 | 시스템 기본 사운드로 Phase 1 운영 → 데이터 보고 결정 | (R1_LOCKSCREEN 일부) |
| Critical Alert (DND 우회) | Apple 승인 가능성 낮음 | `R2_CRITICAL_ALERT.md` (도입 보류) |

---

## 7. 참조

- Apple: [User Notifications](https://developer.apple.com/documentation/usernotifications)
- Apple: [`UNNotificationContent.interruptionLevel`](https://developer.apple.com/documentation/usernotifications/unnotificationcontent/interruptionlevel)
- Apple: [Asking Permission to Use Notifications](https://developer.apple.com/documentation/usernotifications/asking-permission-to-use-notifications)
- Apple: [Handling Notifications and Notification-Related Actions](https://developer.apple.com/documentation/usernotifications/handling-notifications-and-notification-related-actions)
- Apple: [Pushing background updates to your App](https://developer.apple.com/documentation/usernotifications/pushing-background-updates-to-your-app)
- Apple: [App Store Review Guideline 4.5.4](https://developer.apple.com/app-store/review/guidelines/) (Phase 2 VoIP 평가 시)
- Firebase: [Set up a Firebase Cloud Messaging client app on Apple platforms](https://firebase.google.com/docs/cloud-messaging/ios/client)
- Firebase: [Receive messages in an Apple app](https://firebase.google.com/docs/cloud-messaging/ios/receive)
- 본 프로젝트: `ios/WORKING_DOC.md` 주제 5, `ios/FUNCTIONAL_INVENTORY.md` §2.2

# Phase 2 — Swift App Clip 타겟 상세 구현 (Mac 도착 후)

> **착수 시점**: Mac mini 도착 + Phase 1 정식 출시 안정화 + Apple Developer Program 승인 완료 후
> **목표**: iOS 손님앱 귀속 완성 — Phase 1 수동 사무실 코드 입력을 QR 스캔 즉시 연결로 대체
> **범위**: Swift App Clip 타겟 ~500 LOC + Cloud Function `generateCustomToken` + AASA 호스팅 + App Store Connect Experience
> **기반 문서**:
> - `ATTRIBUTION.md §3` — 개요 + 핵심 코드 샘플 (본 문서는 **상세 구현 가이드**로 확장)
> - `ios/customer_app/PLAN.md` — 원본 Swift 네이티브 App Clip 설계 (Phase 1 Flutter 병행 전에 이미 설계됨 — 재활용)
> - `flutter/customer_app/PLAN.md §Phase 2` — 범위 확정
> - `flutter/WORKING_DOC.md §5 의제 13` — 손님앱 iOS 전환 결정
> - `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md §9 Track C` — Mac 도착 후 타임라인
> **작성일**: 2026-04-16

---

## 0. 본 문서의 위치

| 문서 | 역할 | 상세도 |
|------|------|--------|
| `ATTRIBUTION.md §3` | App Clip 11단계 개요 + Swift 6 파일 핵심 샘플 | 중 — 의사결정 근거 |
| **본 문서 (`PHASE2_APPCLIP.md`)** | Mac 도착 후 **즉시 실행 가능한 step-by-step 가이드** | 고 — Day 1~14 작업 순서 |
| `BUILD.md` (위탁 예정) | Codemagic 빌드 + 아카이브 | 중 — CI/CD |

**원칙**: 본 문서에는 ATTRIBUTION.md §3 코드 샘플을 **복사 재수록하지 않음**. 대신 각 항목에서 ATTRIBUTION.md 라인 번호로 참조. 본 문서는 "Mac 열자마자 실행할 절차"에 집중.

---

## 1. Apple Developer 설정 체크리스트

Mac 도착 전에도 **일부 작업 가능** (Apple Developer 웹 콘솔). Mac 도착 후 Xcode 연동.

### 1.1 웹에서 가능한 작업 (Mac 없이)

| # | 항목 | 위치 | 사전 조건 |
|---|------|------|-----------|
| 1 | Apple Developer Program 가입 ($99/년) | https://developer.apple.com/programs/ | 본인 확인 1~2일, 조직 D-U-N-S 번호 2~4주 |
| 2 | App ID 등록 (풀앱) `com.designated.customer.ios` | Certificates, Identifiers & Profiles → Identifiers | Team ID 확인 |
| 3 | App Clip ID 등록 `com.designated.customer.ios.Clip` | 동일 | App ID 선결 필수 — App Clip은 풀앱의 parent ID에 귀속 |
| 4 | App Group 등록 `group.com.designated.customer` | Identifiers → App Groups | — |
| 5 | 양 App ID에 App Group Capability 추가 | 각 App ID → Capabilities → App Groups | #4 선행 |
| 6 | 양 App ID에 Associated Domains Capability 추가 | Capabilities → Associated Domains | AASA 호스팅 선결 |
| 7 | 양 App ID에 Push Notifications Capability 활성 | Capabilities → Push Notifications | — |
| 8 | 양 App ID에 Time Sensitive Notifications Capability 활성 | Capabilities → Time Sensitive Notifications | — |
| 9 | APNs Authentication Key (.p8) 발급 | Keys → `+` → Apple Push Notifications service | 이미 기사앱에서 발급한 것 재사용 가능 (Key는 Team 단위) |
| 10 | App Store Connect 앱 등록 (풀앱) | https://appstoreconnect.apple.com → My Apps → `+` | App ID 연결 |

### 1.2 Mac 필수 작업

| # | 항목 | 도구 |
|---|------|------|
| 11 | Provisioning Profile 발급 (풀앱 + App Clip 각 1개씩, Ad Hoc + Distribution) | Xcode 또는 web |
| 12 | Xcode에 Apple ID 로그인 | Xcode → Preferences → Accounts |
| 13 | 자동 Signing 설정 또는 수동 profile 다운로드 | Xcode Target → Signing & Capabilities |

### 1.3 Firebase Console 작업

| # | 항목 | 위치 |
|---|------|------|
| 14 | Firebase 프로젝트에 iOS 앱 2개 등록 (풀앱 + App Clip Bundle ID 각각) | Firebase Console → Project Settings → `Add app` |
| 15 | `GoogleService-Info.plist` 2개 다운로드 | 각 앱 설정 |
| 16 | APNs .p8 키 Firebase Console에 업로드 | Cloud Messaging → APNs Authentication Key |
| 17 | Firestore Security Rules 업데이트 (App Clip UID 허용) | Rules → `customerInfo/{phone}` 읽기 쓰기 허용 조건 확인 |

### 1.4 Firebase Hosting AASA 준비 (Mac 없이)

| # | 항목 | 위치 |
|---|------|------|
| 18 | `homepage/public/.well-known/apple-app-site-association` 파일 생성 | 로컬 편집기 |
| 19 | `firebase.json` `hosting.headers`에 Content-Type 설정 | JSON 편집 |
| 20 | `firebase deploy --only hosting:callmadang-web` 배포 | Firebase CLI |
| 21 | `curl https://calllink.io.kr/.well-known/apple-app-site-association` 검증 | 터미널 |

§5 AASA 호스팅 상세 참조.

---

## 2. Xcode App Clip Target 생성 절차

**전제**: Mac mini 도착 + Xcode 16+ 설치 + Flutter 손님앱 Phase 1 iOS 빌드 성공 상태.

### 2.1 Target 추가

```
Xcode 열기
  └─ customer_app_flutter/ios/Runner.xcworkspace 선택
    └─ File → New → Target
      └─ iOS → App Clip 선택
        └─ Product Name: ClipExtension
        └─ Interface: SwiftUI
        └─ Language: Swift
        └─ Include Tests: 해제 (10MB 제한 준수)
        └─ Embedded in Application: Runner (풀앱 타겟)
        └─ Team: Apple Developer Team 선택
        └─ Bundle Identifier: com.designated.customer.ios.Clip
        └─ Finish
```

결과: `customer_app_flutter/ios/ClipExtension/` 디렉토리 자동 생성.

### 2.2 Capabilities 설정 (Xcode)

**ClipExtension Target** → Signing & Capabilities → `+ Capability`:

| Capability | 설정값 |
|-----------|--------|
| App Groups | `group.com.designated.customer` 체크 |
| Associated Domains | `appclips:calllink.io.kr` 추가 |
| Push Notifications | (옵션 — App Clip에서 FCM 사용 안 할 경우 생략) |

**Runner Target** (풀앱)에도 동일한 App Groups 추가 (ATTRIBUTION.md §3.3 참조).

### 2.3 Info.plist 설정 (`ClipExtension/Info.plist`)

Xcode가 자동 생성하나 수동 보강:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleDisplayName</key>
  <string>콜마당</string>
  <key>CFBundleExecutable</key>
  <string>$(EXECUTABLE_NAME)</string>
  <key>CFBundleIdentifier</key>
  <string>$(PRODUCT_BUNDLE_IDENTIFIER)</string>
  <key>CFBundleInfoDictionaryVersion</key>
  <string>6.0</string>
  <key>CFBundleName</key>
  <string>$(PRODUCT_NAME)</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleShortVersionString</key>
  <string>1.0.0</string>
  <key>CFBundleVersion</key>
  <string>1</string>
  <key>LSRequiresIPhoneOS</key>
  <true/>
  <key>UIApplicationSceneManifest</key>
  <dict>
    <key>UIApplicationSupportsMultipleScenes</key>
    <false/>
  </dict>
  <key>UILaunchScreen</key>
  <dict/>
  <key>UIRequiredDeviceCapabilities</key>
  <array>
    <string>armv7</string>
  </array>
  <key>UISupportedInterfaceOrientations</key>
  <array>
    <string>UIInterfaceOrientationPortrait</string>
  </array>

  <!-- App Clip 필수 -->
  <key>NSAppClip</key>
  <dict>
    <key>NSAppClipRequestEphemeralUserNotification</key>
    <false/>
    <key>NSAppClipRequestLocationConfirmation</key>
    <false/>
  </dict>

  <!-- Privacy descriptions — 필요한 것만 (App Clip도 사유 필수) -->
  <key>NSLocationWhenInUseUsageDescription</key>
  <string>출발지를 자동 설정하기 위해 현재 위치를 사용합니다.</string>
</dict>
</plist>
```

### 2.4 Entitlements 설정 (`ClipExtension.entitlements`)

Xcode가 Capabilities 추가 시 자동 생성. 최종 내용:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>com.apple.developer.associated-domains</key>
  <array>
    <string>appclips:calllink.io.kr</string>
  </array>
  <key>com.apple.security.application-groups</key>
  <array>
    <string>group.com.designated.customer</string>
  </array>
  <key>com.apple.developer.parent-application-identifiers</key>
  <array>
    <string>$(AppIdentifierPrefix)com.designated.customer.ios</string>
  </array>
  <!-- Push 사용 시만 -->
  <!-- <key>aps-environment</key>
  <string>production</string> -->
</dict>
</plist>
```

> `parent-application-identifiers`가 풀앱 Bundle ID를 **반드시 참조**해야 App Clip이 풀앱의 자식으로 인식됨.

### 2.5 Build Settings

- **iOS Deployment Target**: **15.0** (풀앱과 동일, 의제 1)
- **Swift Language Version**: Swift 5
- **Enable Modules**: YES
- **Strip Debug Symbols During Copy**: YES (크기 최적화)

### 2.6 Podfile 수정 (CocoaPods + Firebase)

`ios/Podfile`에 App Clip 타겟 추가:

```ruby
# 기존 Runner 타겟 아래에 추가
target 'ClipExtension' do
  use_frameworks!
  platform :ios, '15.0'

  # Firebase (App Clip은 최소 필요한 것만 — 10MB 크기 제약)
  pod 'FirebaseCore'
  pod 'FirebaseAuth'
  pod 'FirebaseFirestore'      # 사무실 검색용
  pod 'FirebaseFunctions'       # generateCustomToken Callable
  # Firebase Messaging 불포함 (App Clip은 FCM 미사용)
  # FirebaseAnalytics 불포함 (크기 절약)
end
```

**`pod install --repo-update`** 실행 → `Podfile.lock` 갱신.

---

## 3. Swift 코드 구조 (~500 LOC 상세)

### 3.1 파일 구성

```
customer_app_flutter/ios/ClipExtension/
├── Info.plist                            (§2.3)
├── ClipExtension.entitlements            (§2.4)
├── GoogleService-Info.plist              (Firebase Console에서 App Clip Bundle ID로 다운로드)
├── Assets.xcassets/
│   └── AppIcon.appiconset/               (App Clip 아이콘 120x120, 180x180)
├── ClipExtensionApp.swift                (~30 LOC) — @main 진입점
├── ContentView.swift                     (~200 LOC) — 화면 분기 + UI
├── AppClipCoordinator.swift              (~80 LOC) — 11단계 플로우 통합
├── URLParameterParser.swift              (~80 LOC) — Kotlin 포팅
├── AppGroupStorage.swift                 (~60 LOC) — UserDefaults 래퍼
├── FirebaseAppClipBootstrap.swift        (~50 LOC) — Firebase + Anonymous Auth
├── CustomTokenFetcher.swift              (~40 LOC) — CF Callable
├── OfficeLookupService.swift             (~60 LOC) — Firestore 사무실 조회
└── FullAppInstallPrompt.swift            (~40 LOC) — SKOverlay 설치 배너
```

**총 추정**: ~640 LOC (ATTRIBUTION.md §3.4 추정치 ~500 LOC 대비 여유).

### 3.2 각 파일 상세

#### 3.2.1 `ClipExtensionApp.swift` (진입점)

**역할**: SwiftUI `@main` + Firebase 초기화 + Universal Link 수신.

**핵심 구현**: ATTRIBUTION.md §3.4.1 참조. 보강 포인트:

```swift
import SwiftUI
import FirebaseCore

@main
struct ClipExtensionApp: App {
  @StateObject private var coordinator = AppClipCoordinator.shared

  init() {
    FirebaseAppClipBootstrap.configure()
  }

  var body: some Scene {
    WindowGroup {
      ContentView()
        .environmentObject(coordinator)
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
          guard let url = activity.webpageURL else { return }
          Task {
            await coordinator.handleURL(url)
          }
        }
        .onOpenURL { url in
          // App Clip이 이미 실행 중일 때 새 URL 수신
          Task { await coordinator.handleURL(url) }
        }
    }
  }
}
```

- `onContinueUserActivity`는 App Clip **최초 실행** 시 URL 전달
- `onOpenURL`은 **이미 실행 중** 상태에서 재실행 시 URL 전달

#### 3.2.2 `ContentView.swift` (메인 UI)

**역할**: AppClipCoordinator의 state 관찰 + 4가지 화면 분기.

```swift
import SwiftUI

struct ContentView: View {
  @EnvironmentObject var coordinator: AppClipCoordinator

  var body: some View {
    switch coordinator.state {
    case .idle:
      LoadingView(message: "콜마당 앱 클립 준비 중...")
    case .parsing:
      LoadingView(message: "사무실 정보 확인 중...")
    case .authenticating:
      LoadingView(message: "계정 생성 중...")
    case .lookingUpOffice:
      LoadingView(message: "사무실 연결 중...")
    case .ready(let office):
      OfficeReadyView(office: office)
    case .error(let message):
      ErrorView(message: message, onRetry: coordinator.retry)
    }
  }
}

struct LoadingView: View {
  let message: String
  var body: some View {
    VStack(spacing: 24) {
      ProgressView().scaleEffect(1.5)
      Text(message).font(.body).foregroundColor(.secondary)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
  }
}

struct OfficeReadyView: View {
  let office: OfficeInfo
  @State private var showingInstallPrompt = false

  var body: some View {
    VStack(spacing: 32) {
      Spacer()

      // 사무실 정보 표시
      VStack(spacing: 12) {
        Image(systemName: "checkmark.circle.fill")
          .font(.system(size: 64))
          .foregroundColor(.green)
        Text("사무실 연결 완료")
          .font(.title2).bold()
        Text(office.officeName)
          .font(.title3).foregroundColor(.primary)
        if let driverName = office.driverName {
          Text("추천 기사: \(driverName)")
            .font(.subheadline).foregroundColor(.secondary)
        }
      }

      Spacer()

      // 풀앱 설치 유도
      VStack(spacing: 16) {
        Text("대리운전 신청은 콜마당 앱에서 가능합니다")
          .font(.body).multilineTextAlignment(.center)
        Button(action: { showingInstallPrompt = true }) {
          HStack {
            Image(systemName: "arrow.down.app.fill")
            Text("콜마당 앱 설치")
          }
          .frame(maxWidth: .infinity).padding()
          .background(Color.blue).foregroundColor(.white)
          .cornerRadius(12)
        }
      }
      .padding()
    }
    .padding()
    .appStoreOverlay(isPresented: $showingInstallPrompt) {
      SKOverlay.AppClipConfiguration(position: .bottom)
    }
  }
}

struct ErrorView: View {
  let message: String
  let onRetry: () -> Void

  var body: some View {
    VStack(spacing: 24) {
      Image(systemName: "exclamationmark.triangle.fill")
        .font(.system(size: 48))
        .foregroundColor(.orange)
      Text(message)
        .font(.body).multilineTextAlignment(.center)
      Button("다시 시도", action: onRetry)
        .buttonStyle(.borderedProminent)
    }
    .padding()
  }
}
```

> **MVP 범위**: App Clip에서 **실제 콜 요청 기능은 제외**. 사무실 연결만 확인 + 풀앱 설치 유도. 콜 요청은 풀앱에서만 가능 (10MB 제한 준수 + UX 단순화). `ios/customer_app/PLAN.md:34-39`의 `CallRequestScreen`은 R2 확장으로 분류.

#### 3.2.3 `AppClipCoordinator.swift` (통합 상태 머신)

ATTRIBUTION.md §3.4.6 기반 + 사무실 조회 단계 추가:

```swift
import Foundation
import FirebaseAuth

@MainActor
final class AppClipCoordinator: ObservableObject {
  static let shared = AppClipCoordinator()

  @Published var state: ClipState = .idle
  private var lastURL: URL?

  enum ClipState {
    case idle
    case parsing
    case authenticating
    case lookingUpOffice
    case ready(OfficeInfo)
    case error(String)
  }

  func handleURL(_ url: URL) async {
    lastURL = url
    state = .parsing

    // 1. URL 파라미터 추출
    guard let params = URLParameterParser.parse(url) else {
      state = .error("사무실 정보가 올바르지 않습니다. QR 코드를 다시 스캔해주세요.")
      return
    }

    // 2. App Group에 8 파라미터 저장 (풀앱 상속용)
    AppGroupStorage.saveAttribution(params)

    // 3. Anonymous Auth + Custom Token
    state = .authenticating
    do {
      let uid = try await FirebaseAppClipBootstrap.signInAnonymously()
      let customToken = try await CustomTokenFetcher.fetch(uid: uid)
      AppGroupStorage.saveAuth(uid: uid, customToken: customToken)
    } catch {
      state = .error("계정 생성 실패: \(error.localizedDescription)")
      return
    }

    // 4. Firestore 사무실 조회 (표시용)
    state = .lookingUpOffice
    do {
      let office = try await OfficeLookupService.fetchOfficeInfo(
        provinceId: params.provinceId,
        cityId: params.cityId,
        officeId: params.officeId,
        driverName: params.driverName
      )
      state = .ready(office)
    } catch {
      state = .error("사무실 정보를 불러올 수 없습니다: \(error.localizedDescription)")
    }
  }

  func retry() {
    guard let url = lastURL else {
      state = .error("URL 정보가 없습니다")
      return
    }
    Task { await handleURL(url) }
  }
}

struct OfficeInfo {
  let provinceId: String
  let cityId: String
  let officeId: String
  let officeName: String
  let driverName: String?
  let officePhone: String?
}
```

#### 3.2.4 `URLParameterParser.swift`

ATTRIBUTION.md §3.4.2 참조. Kotlin `MainActivity.kt:220-228` 1:1 포팅 — 8 파라미터 (`p`, `c`, `o`, `driver`, `driverName`, `phone`, `bank`, `account`, `holder`) + `r` 하위호환.

**추가 검증 로직** (본 문서 보강):

```swift
// URL 호스트 검증 — calllink.io.kr만 허용 (피싱 방지)
guard let host = url.host,
      host == "calllink.io.kr" || host.hasSuffix(".calllink.io.kr") else {
  return nil
}

// 경로 검증 — /office만 허용
guard url.path == "/office" || url.path == "/" else {
  return nil
}
```

#### 3.2.5 `AppGroupStorage.swift`

ATTRIBUTION.md §3.4.3 참조. 13개 공유 키 정의.

**보강**: 토큰 만료 체크 헬퍼 추가:

```swift
extension AppGroupStorage {
  static func isCustomTokenValid() -> Bool {
    let generatedAt = shared.double(forKey: "appclip_tokenGeneratedAt")
    guard generatedAt > 0 else { return false }
    let now = Date().timeIntervalSince1970
    return (now - generatedAt) < 3600  // 1시간 이내
  }

  static func clearAll() {
    let defaults = shared
    let keys = [
      "appclip_provinceId", "appclip_cityId", "appclip_officeId",
      "appclip_driverId", "appclip_driverName",
      "appclip_officePhone", "appclip_bankName",
      "appclip_accountNumber", "appclip_accountHolder",
      "appclip_authUid", "appclip_customToken", "appclip_tokenGeneratedAt",
      "appclip_phoneNumber",
    ]
    for key in keys {
      defaults.removeObject(forKey: key)
    }
  }
}
```

#### 3.2.6 `FirebaseAppClipBootstrap.swift`

ATTRIBUTION.md §3.4.4 참조. `FirebaseApp.configure()` + `Auth.signInAnonymously`.

**보강**: 명시적 Firebase 옵션 지정 (App Clip 고유 `GoogleService-Info.plist` 사용):

```swift
import FirebaseCore
import FirebaseAuth

enum FirebaseAppClipBootstrap {
  static func configure() {
    // ClipExtension Target의 GoogleService-Info.plist 자동 참조
    // (풀앱과 다른 Bundle ID이므로 Firebase Console에서 별도 등록 필요)
    FirebaseApp.configure()
  }

  static func signInAnonymously() async throws -> String {
    // 이미 로그인되어 있으면 재사용
    if let current = Auth.auth().currentUser, current.isAnonymous {
      return current.uid
    }
    let result = try await Auth.auth().signInAnonymously()
    return result.user.uid
  }
}
```

#### 3.2.7 `CustomTokenFetcher.swift`

ATTRIBUTION.md §3.4.5 참조. `Functions.httpsCallable("generateCustomToken")`.

**보강**: 재시도 로직 (네트워크 일시 오류):

```swift
import FirebaseFunctions

enum CustomTokenFetcher {
  static func fetch(uid: String, retries: Int = 2) async throws -> String {
    let functions = Functions.functions(region: "asia-northeast3")
    let callable = functions.httpsCallable("generateCustomToken")

    var lastError: Error?
    for attempt in 0...retries {
      do {
        let result = try await callable.call(["uid": uid])
        guard let data = result.data as? [String: Any],
              let token = data["token"] as? String else {
          throw NSError(
            domain: "CustomTokenFetcher",
            code: -1,
            userInfo: [NSLocalizedDescriptionKey: "Invalid response"]
          )
        }
        return token
      } catch {
        lastError = error
        if attempt < retries {
          try? await Task.sleep(nanoseconds: UInt64(pow(2.0, Double(attempt)) * 1_000_000_000))
        }
      }
    }
    throw lastError!
  }
}
```

#### 3.2.8 `OfficeLookupService.swift` (신규)

App Clip에서 Firestore 사무실 정보 조회 (UI 표시용):

```swift
import FirebaseFirestore

enum OfficeLookupService {
  static func fetchOfficeInfo(
    provinceId: String,
    cityId: String,
    officeId: String,
    driverName: String?
  ) async throws -> OfficeInfo {
    let db = Firestore.firestore()
    let officeRef = db
      .collection("provinces").document(provinceId)
      .collection("cities").document(cityId)
      .collection("offices").document(officeId)

    let snapshot = try await officeRef.getDocument()
    guard let data = snapshot.data() else {
      throw NSError(
        domain: "OfficeLookupService",
        code: 404,
        userInfo: [NSLocalizedDescriptionKey: "사무실 정보 없음"]
      )
    }

    return OfficeInfo(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      officeName: data["name"] as? String ?? "콜마당 사무실",
      driverName: driverName,
      officePhone: data["officePhone"] as? String
    )
  }
}
```

#### 3.2.9 `FullAppInstallPrompt.swift` (SKOverlay)

SKOverlay는 iOS 14+ 공식 App Clip → 풀앱 설치 권장 API:

```swift
import SwiftUI
import StoreKit

struct FullAppInstallPromptModifier: ViewModifier {
  @Binding var isPresented: Bool

  func body(content: Content) -> some View {
    content
      .appStoreOverlay(isPresented: $isPresented) {
        SKOverlay.AppClipConfiguration(position: .bottom)
      }
  }
}

extension View {
  func fullAppInstallPrompt(isPresented: Binding<Bool>) -> some View {
    modifier(FullAppInstallPromptModifier(isPresented: isPresented))
  }
}
```

`SKOverlay.AppClipConfiguration`은 Apple이 자동으로 **현재 App Clip의 parent 풀앱**을 찾아 하단 오버레이로 표시. 별도 App ID 지정 불필요.

---

## 4. Cloud Function — `generateCustomToken` (서버 별도 과제)

### 4.1 신규 파일 생성

**파일**: `functions/src/customClip.ts` (신규)

```typescript
import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v2';
import { logger } from 'firebase-functions/v2';

/**
 * App Clip → 풀앱 UID 계승용 Custom Token 발급.
 *
 * 호출자: App Clip이 Anonymous Auth 완료 후
 * 요청: { uid: string }
 * 응답: { token: string }
 *
 * 보안:
 * - 호출자 UID === target UID 일치 필수 (본인만 자기 UID로 토큰 요청)
 * - Firebase Auth 인증 토큰 있어야 호출 가능 (anonymous도 OK)
 * - 토큰 만료: Firebase Custom Token은 기본 1시간 (admin.auth().createCustomToken 설정)
 *
 * 참조: ATTRIBUTION.md §3.5
 */
export const generateCustomToken = functions.https.onCall(
  {
    region: 'asia-northeast3',
    enforceAppCheck: false,  // 의제 14-E: App Check는 Phase 2 이후 R2
  },
  async (request) => {
    const callerUid = request.auth?.uid;
    const targetUid = request.data?.uid;

    if (!callerUid) {
      throw new functions.https.HttpsError(
        'unauthenticated',
        '로그인이 필요합니다'
      );
    }

    if (typeof targetUid !== 'string' || !targetUid) {
      throw new functions.https.HttpsError(
        'invalid-argument',
        'uid 파라미터 필수'
      );
    }

    if (callerUid !== targetUid) {
      logger.warn(
        `[generateCustomToken] UID 불일치: caller=${callerUid} target=${targetUid}`
      );
      throw new functions.https.HttpsError(
        'permission-denied',
        '본인 UID만 요청 가능합니다'
      );
    }

    try {
      const token = await admin.auth().createCustomToken(targetUid, {
        source: 'appclip',
        createdAt: Date.now(),
      });
      logger.info(`[generateCustomToken] 성공: uid=${targetUid}`);
      return { token };
    } catch (error) {
      logger.error(`[generateCustomToken] 실패: ${error}`);
      throw new functions.https.HttpsError(
        'internal',
        '토큰 생성 실패'
      );
    }
  }
);
```

### 4.2 `functions/src/index.ts`에서 export

```typescript
// functions/src/index.ts 맨 아래 추가
export { generateCustomToken } from './customClip';
```

### 4.3 배포

```bash
cd functions
npm run build
firebase deploy --only functions:generateCustomToken
```

### 4.4 Firestore 보안 규칙 (재확인)

`customerInfo/{phone}` 읽기/쓰기 규칙이 **Anonymous Auth UID**에서도 허용되는지 확인:

```
// firestore.rules (예시)
match /customerInfo/{phone} {
  allow read: if request.auth != null;  // 익명도 허용
  allow write: if request.auth != null && (
    // Anonymous Auth (App Clip) + Phone Auth (풀앱) 공존
    request.auth.uid != null
  );
}
```

기존 규칙이 Phone Auth만 허용 시 **Anonymous도 허용하도록 수정 필요** (Phase 2 착수 전 검증).

---

## 5. AASA (Apple App Site Association) 호스팅

### 5.1 파일 생성

**파일**: `homepage/public/.well-known/apple-app-site-association` (확장자 없음, JSON 형식)

```json
{
  "applinks": {
    "apps": [],
    "details": [
      {
        "appIDs": [
          "TEAMID.com.designated.customer.ios",
          "TEAMID.com.designated.customer.ios.Clip"
        ],
        "components": [
          {
            "/": "/office",
            "comment": "사무실 연결 Universal Link + App Clip trigger"
          },
          {
            "/": "/office/*",
            "comment": "옵션: 하위 경로도 App Clip 기동"
          }
        ]
      }
    ]
  },
  "appclips": {
    "apps": ["TEAMID.com.designated.customer.ios.Clip"]
  }
}
```

**중요**: `TEAMID`는 실제 Apple Developer Team ID로 치환 (Mac 도착 후 확인).

### 5.2 Firebase Hosting 설정

**파일**: `firebase.json` 수정

```json
{
  "hosting": [
    {
      "target": "callmadang-web",
      "public": "homepage/public",
      "headers": [
        {
          "source": "/.well-known/apple-app-site-association",
          "headers": [
            { "key": "Content-Type", "value": "application/json" }
          ]
        }
      ],
      "rewrites": [
        { "source": "**", "destination": "/index.html" }
      ]
    }
  ]
}
```

> `.well-known/` 경로가 `rewrites`에 의해 `index.html`로 리라이트되지 않도록 **header 설정이 rewrites보다 우선**함. Firebase Hosting은 Static 파일을 먼저 매칭.

### 5.3 배포 + 검증

```bash
# 배포
firebase deploy --only hosting:callmadang-web

# 검증 (직접 접근)
curl -v https://calllink.io.kr/.well-known/apple-app-site-association

# 기대 응답:
# HTTP/2 200
# content-type: application/json
# (JSON 내용)

# Apple CDN 캐시 확인 (최대 24시간 소요)
curl https://app-site-association.cdn-apple.com/a/v1/calllink.io.kr
```

### 5.4 TLS 검증

**AASA는 HTTPS + TLS 1.2+ 필수**. Firebase Hosting은 기본 TLS 1.3 제공 → 자동 충족.

Apple이 AASA fetch 시 **redirect 허용 안 함** — `calllink.io.kr`이 직접 200 응답해야 함. 만약 `www.calllink.io.kr`로 리다이렉트되면 AASA 등록 실패. **Apex 도메인(`calllink.io.kr`) + `www.` 둘 다 AASA 호스팅** 권장.

---

## 6. App Clip Card 메타 (App Store Connect)

Mac 도착 후 App Store Connect 웹에서 설정:

### 6.1 Advanced App Clip Experience 등록

```
App Store Connect → 손님앱 → App Clip 섹션
  └─ Advanced App Clip Experiences
    └─ + 추가
      ├─ URL: https://calllink.io.kr/office
      ├─ Status: Received
      ├─ Default Language: Korean
      ├─ Title: 콜마당 — 대리운전 즉시 연결
      ├─ Subtitle: QR 스캔으로 사무실 바로 연결
      ├─ Header Image: 1800×1200 PNG (한글 + 콜마당 로고)
      ├─ Action: 열기 (Open)
      └─ Category: Lifestyle 또는 Navigation
```

### 6.2 Header Image 요구사항

| 항목 | 값 |
|------|----|
| 크기 | 1800×1200 px |
| 비율 | 3:2 |
| 포맷 | PNG |
| 최대 크기 | 3 MB |
| 내용 | 브랜드 로고 + 핵심 메시지 (텍스트 최소화) |

**주의**: Header Image에 "다운로드" / "설치" 단어 금지 (Apple 심사 거부 사유).

### 6.3 App Clip Invocation 방법 3종

| 방법 | 사용자 경험 | 설정 위치 |
|------|-------------|-----------|
| **QR 코드 (권장)** | 카메라 → URL 감지 → App Clip 카드 | AASA + Header Image |
| Smart App Banner | 웹페이지에서 App Clip 카드 하단 표시 | `<meta name="apple-itunes-app">` (§6.4) |
| NFC 태그 | NFC 스캔 → App Clip | 기사 별도 NFC 장비 필요, Phase 2+ 검토 |

### 6.4 랜딩페이지 메타태그 (선택)

**파일**: `homepage/public/index.html` 또는 `/office` 경로 HTML

```html
<meta name="apple-itunes-app"
      content="app-clip-bundle-id=com.designated.customer.ios.Clip,
               app-id=XXXXXXXXXX">
```

`app-id`는 App Store Connect에서 풀앱 App ID (숫자 10자리).

---

## 7. Flutter 풀앱 측 변경

### 7.1 AppGroupBridge.swift (Flutter MethodChannel 핸들러)

ATTRIBUTION.md §3.6 참조. `ios/Runner/AppGroupBridge.swift` 신규 작성 + `AppDelegate.swift` 등록.

**핵심 추가 작업**: Flutter Runner Target의 Capabilities에도 **App Groups 추가** (`group.com.designated.customer`). Xcode Runner Target → Signing & Capabilities → App Groups.

### 7.2 AppClipMigrationService.dart

ATTRIBUTION.md §3.6 참조. Flutter 풀앱 첫 실행 시 `migrateIfNeeded()` 호출.

### 7.3 main.dart 호출 위치

**파일**: `customer_app_flutter/lib/main.dart` 수정

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);

  // Crashlytics 초기화 (의제 11)
  FlutterError.onError = FirebaseCrashlytics.instance.recordFlutterFatalError;

  FirebaseMessaging.onBackgroundMessage(customerFirebaseMessagingBackgroundHandler);

  // Phase 2 — App Clip 이관 (iOS 전용, 첫 실행 1회)
  final prefs = await SharedPreferences.getInstance();
  if (Platform.isIOS) {
    final migrated = await AppClipMigrationService.migrateIfNeeded(prefs);
    if (migrated) {
      debugPrint('[main] App Clip → 풀앱 이관 성공');
    }
  }

  runApp(const ProviderScope(child: CustomerApp()));
}
```

### 7.4 사무실 연결 분기 로직 보강

**파일**: `customer_app_flutter/lib/main.dart` `_buildInitialRoute` (ATTRIBUTION.md §2.6 수정)

```dart
Future<Widget> _buildInitialRoute() async {
  final prefs = await SharedPreferences.getInstance();
  final officeId = prefs.getString(PreferencesKeys.officeId);
  final phoneVerified = prefs.getBool(PreferencesKeys.isPhoneVerified) ?? false;

  if (officeId == null) {
    // Phase 2 App Clip 이관 실패 시에만 수동 입력 화면 진입
    return const OfficeCodeInputScreen();
  }

  if (!phoneVerified) {
    return const PhoneAuthScreen();
  }

  return const HomeScreen();
}
```

Phase 2 도입 후 **iOS 사용자 대다수는 App Clip 경유 → officeId 자동 설정 → 수동 입력 화면 건너뜀**. `OfficeCodeInputScreen`은 fallback.

---

## 8. 풀앱 설치 유도 배너

### 8.1 Apple 공식 권장 방식: SKOverlay

iOS 14+ `SKOverlay.AppClipConfiguration`은 Apple이 공식 제공하는 App Clip → 풀앱 설치 권장 오버레이.

**장점**:
- Apple 심사 안전 (임의 배너 대비)
- 자동으로 현재 App Clip의 parent 풀앱 매칭
- "설치" 버튼 탭 시 App Store 내장 설치 시트 표시 (앱 이탈 없음)
- 한국어 자동 지원 (iOS 시스템 언어 기반)

§3.2.9 `FullAppInstallPrompt.swift` 참조.

### 8.2 임의 배너 금지

**금지 사항** (Apple Review Guideline):
- 전체 화면 모달 "앱을 설치하세요"
- 사용자 액션 차단하는 배너
- SKOverlay 대체 커스텀 UI

**허용 사항**:
- 본문 내 "콜마당 앱 설치" 버튼 → 탭 시 SKOverlay 호출
- 하단 작은 텍스트 안내 (§3.2.2 `OfficeReadyView` 예시)

---

## 9. 테스트 시나리오

### 9.1 Day 10~14 실기기 테스트 체크리스트

#### 시나리오 A: App Clip 단독 실행 (풀앱 미설치)

1. **기사 기기 (Galaxy)**: QR 코드 프린트 또는 화면 표시 (`calllink.io.kr/office?p=seoul&c=gangnam&o=office1&driver=d123&driverName=김기사&phone=01012345678&bank=국민&account=123-456&holder=김기사`)
2. **고객 기기 (iPhone)**: 카메라로 QR 스캔 → **App Clip 카드 하단 팝업** 표시
3. 카드 내용 확인: "콜마당 — 대리운전 즉시 연결" + "열기" 버튼
4. "열기" 탭 → App Clip 다운로드 (10MB 이내, 2~5초) → 실행
5. `ClipExtensionApp.onContinueUserActivity` 트리거 → `AppClipCoordinator.handleURL` 호출
6. `URLParameterParser.parse` 성공 → 8 파라미터 추출
7. `AppGroupStorage.saveAttribution` 실행 → `group.com.designated.customer` 저장
8. `FirebaseAppClipBootstrap.signInAnonymously` 실행 → UID 획득
9. `CustomTokenFetcher.fetch(uid:)` 실행 → CF 호출 → Custom Token 받음
10. `AppGroupStorage.saveAuth` 실행 → 토큰 저장
11. `OfficeLookupService.fetchOfficeInfo` 실행 → Firestore `offices/office1` 조회
12. `OfficeReadyView` 표시: "사무실 연결 완료 · 콜마당 강남지점 · 추천 기사: 김기사"
13. "콜마당 앱 설치" 버튼 탭 → SKOverlay 하단 오버레이 표시

#### 시나리오 B: App Clip → 풀앱 설치 → 이관 성공

1. 시나리오 A 1~13 완료
2. SKOverlay에서 "설치" → App Store 내장 시트 → "받기" → 설치 시작
3. 설치 완료 → "열기" 탭 → 풀앱(Flutter) 최초 실행
4. Flutter `main.dart` → `AppClipMigrationService.migrateIfNeeded` 호출
5. `MethodChannel('com.designated.customer/appgroup').invokeMethod('readAppClipData')` 실행
6. Swift `AppGroupBridge.readAppClipData` 응답 → 13 필드 Map 반환
7. Flutter 측: 8 파라미터 → SharedPreferences 저장 (`office_id`, `province_id`, `city_id`, `driver_id`, `driver_name`, `office_phone`, `bank_name`, `account_number`, `account_holder`)
8. `tokenGeneratedAt` 만료 체크 → 1시간 이내 통과
9. `FirebaseAuth.signInWithCustomToken(customToken)` 실행 → 같은 Anonymous UID로 Firebase Auth 재진입
10. `channel.invokeMethod('clearAppClipData')` → App Group 정리
11. `_migrationDoneKey = true` 저장 (재실행 방지)
12. 풀앱 `_buildInitialRoute` → `officeId` 존재 확인 → `PhoneAuthScreen` 진입 (Phone Auth는 별도, Anonymous → Phone Auth 링크)
13. Phone Auth 완료 → 홈 화면 → 콜 요청 가능

#### 시나리오 C: Custom Token 1시간 만료

1. 시나리오 A 1~13 완료
2. **1시간 이상 대기** (풀앱 미설치)
3. 시나리오 B 2~3: 풀앱 설치 + 첫 실행
4. `AppClipMigrationService` 호출 → `tokenGeneratedAt` 비교 → 만료 판정
5. SharedPreferences 8 파라미터는 저장 (사무실 연결 OK)
6. Firebase Auth Custom Token 로그인 **건너뜀**
7. 풀앱 → Phone Auth 화면 진입 (정상 플로우)
8. 콜 이력 없이 새로 시작 (App Clip Anonymous UID 데이터는 접근 불가)

**판정**: Custom Token 만료 시에도 사무실 연결은 보존 (핵심 귀속 데이터). Firebase 계정 계승만 실패 → 재인증으로 복구.

#### 시나리오 D: 잘못된 URL (피싱 시도)

1. 고객이 `https://malicious.com/office?p=evil&c=...` QR 스캔
2. iOS가 AASA 검증 → `calllink.io.kr` 아니므로 **App Clip 카드 미표시**
3. Safari가 웹페이지 열기 시도 → 일반 웹 브라우저 흐름 (App Clip 경로 차단됨)

**추가 방어**: §3.2.4 `URLParameterParser`에 host 검증 추가 → `calllink.io.kr`만 허용. 만약 iOS AASA가 뚫렸더라도 클라이언트 측 2차 방어.

#### 시나리오 E: App Clip 10MB 빌드 크기 검증

1. Xcode → Product → Archive → App Clip 선택
2. Organizer → Show in Finder → `.ipa` 파일 확인
3. `.ipa` 내부 `ClipExtension.app/` 크기 확인 → **10MB 이하 필수** (iOS 15) 또는 **50MB 이하** (iOS 16+)
4. 초과 시: Podfile에서 Firebase pod 축소 (`FirebaseAnalytics`, `FirebaseRemoteConfig` 제외)

### 9.2 Firebase Emulator 테스트 (Mac 없이 선행 가능)

```bash
# functions 로컬 테스트
cd functions
npm run serve

# Cloud Function generateCustomToken 로컬 호출
curl -X POST http://localhost:5001/<PROJECT>/asia-northeast3/generateCustomToken \
  -H "Content-Type: application/json" \
  -d '{"data": {"uid": "test-uid"}}'

# 기대 응답: { "result": { "token": "eyJhbGc..." } }
```

실제 Firebase Auth는 Emulator로 테스트 불가 → 실제 프로젝트 + 실기기 조합만 전체 플로우 검증 가능.

### 9.3 Analytics 이벤트 검증 (의제 11)

App Clip은 경량화 위해 Firebase Analytics **미포함** 정책. 대신 Custom Token Fetcher의 CF가 `generateCustomToken_called` Analytics 이벤트 로깅:

```typescript
// functions/src/customClip.ts 보강
await admin.analytics().logEvent({
  name: 'appclip_custom_token_fetched',
  params: { uid: targetUid, timestamp: Date.now() },
});
```

풀앱 이관 성공 시 Dart 측에서 이벤트:

```dart
await FirebaseAnalytics.instance.logEvent(
  name: 'appclip_migration_completed',
  parameters: {
    'token_age_sec': tokenAge.toInt(),
    'platform': 'ios',
  },
);
```

이 이벤트로 Phase 2 성공률 측정 → R1_ATTRIBUTION_ACCURACY 재평가 근거.

---

## 10. 위험 신호 + 완화

| 리스크 | 완화 |
|-------|------|
| App Clip 10MB 제한 초과 (iOS 15) | Podfile에서 Firebase 최소 pod만 포함. FirebaseAnalytics/FirebaseCrashlytics/FirebaseMessaging 제외. Xcode Archive 후 크기 검증 |
| 50MB 제한 (iOS 16+) 기준 사용 가능 여부 | Phase 2 배포 시 iOS 15 시장 점유율 재확인 — 의제 1 iOS 15 최저선이므로 10MB 엄수 권장 |
| Custom Token 1시간 만료 → 풀앱 설치 지연 시 계정 계승 실패 | §9 시나리오 C 처리. 사무실 연결은 보존, Firebase 재인증만 요청 (UX 수용 가능) |
| Firebase iOS SDK App Clip 호환성 | Firebase iOS SDK 10.22+ 이후 공식 지원. `pod install` 시 버전 확인 |
| AASA TLS 1.2+ 필수 | Firebase Hosting 기본 TLS 1.3 → 자동 충족. 자체 도메인 CDN 사용 시 TLS 설정 확인 |
| AASA redirect 허용 안 됨 | Apex 도메인 + `www.` 둘 다 AASA 호스팅. 또는 `www.` → apex redirect 대신 apex에 직접 AASA 배치 |
| Apple CDN 캐시 최대 24시간 | 배포 직후 App Clip 미작동 시 최대 24시간 대기 필요. 초기 테스트 시 여유 확보 |
| Apple Review 별도 심사 트랙 (App Clip Experience) | Phase 1 통과 후 별도 제출. App Clip Card 메타 + Header Image 사전 준비 |
| App Clip Anonymous UID와 Phone Auth UID 링크 필요 | 풀앱 첫 실행 시 `signInWithCustomToken`으로 Anonymous UID 계승 → Phone Auth 완료 시 `linkWithCredential` 호출 (Firebase Auth 계정 연결) |
| Firestore Security Rules가 Anonymous Auth 거부 시 App Clip Firestore 조회 실패 | §4.4 규칙 검증 + Anonymous 허용 조항 추가 |
| App Clip URL 파라미터 변조 (피싱) | §3.2.4 host 검증 + AASA 자체 방어 2중 |
| App Clip 빌드 실패 (Entitlements 누락) | Xcode Signing & Capabilities에서 App Groups + Associated Domains 체크 확인 |
| `parent-application-identifiers` 누락 | `ClipExtension.entitlements`에 풀앱 Bundle ID 명시 필수 (§2.4) |
| Provisioning Profile 만료 (1년) | 매년 갱신 필요. Codemagic CI/CD 자동화 권장 (App Store Connect API Key) |
| App Clip Experience 등록 시 Header Image 거부 | Apple 가이드 준수 (텍스트 최소화, "설치" 단어 금지) |
| SKOverlay 사용자 반응 낮음 (풀앱 설치율 저조) | Phase 2 배포 후 Analytics 이벤트 `appclip_migration_completed` 집계로 모니터링. 낮으면 OfficeReadyView UX 개선 |
| 기존 Phase 1 Flutter `OfficeCodeInputScreen` 잔존 시 중복 UX | `officeId` 이미 저장된 상태면 자동 건너뜀 (§7.4). 영향 없음 |
| App Clip Analytics 미포함 정책 | Phase 2 성공률은 풀앱 측 `appclip_migration_completed` 이벤트와 CF `appclip_custom_token_fetched` 이벤트의 비율로 간접 측정 |
| 의제 13 결정 "Mac 도착 후" 지연 시 Phase 2 무기한 연기 | Phase 1 수동 입력 유지 가능. R1_ATTRIBUTION_ACCURACY 발동 조건에 따라 우선순위 동적 조정 |

---

## 11. Mac 도착 후 단계별 작업 (Day 1~14)

### Week 1

#### Day 1 — 환경 구축
- [ ] Xcode 16+ 설치 (Apple Silicon 지원)
- [ ] Command Line Tools 설치 (`xcode-select --install`)
- [ ] CocoaPods 설치 (`sudo gem install cocoapods`)
- [ ] Flutter macOS 설정 (`flutter doctor`)
- [ ] Apple ID로 Xcode 로그인
- [ ] Firebase CLI 설치 + `firebase login`
- [ ] `customer_app_flutter/` 클론 + `flutter pub get`
- [ ] `ios/` 진입 → `pod install --repo-update`
- [ ] Flutter 풀앱 iOS 빌드 smoke test (`flutter build ios --debug --no-codesign`)

#### Day 2 — App Clip Target 생성
- [ ] §2.1 Xcode File → New → Target → App Clip
- [ ] §2.2 Capabilities 설정 (App Groups, Associated Domains)
- [ ] §2.3 Info.plist 작성
- [ ] §2.4 Entitlements 확인
- [ ] §2.6 Podfile 수정 + `pod install`
- [ ] 빈 App Clip 빌드 smoke test (Runner Scheme → ClipExtension 선택)

#### Day 3 — Swift 코드 작성 (1차)
- [ ] §3.2.1 `ClipExtensionApp.swift` (진입점)
- [ ] §3.2.3 `AppClipCoordinator.swift` (상태 머신)
- [ ] §3.2.4 `URLParameterParser.swift` (Kotlin 포팅)
- [ ] §3.2.5 `AppGroupStorage.swift` (UserDefaults 래퍼)
- [ ] 단위 테스트 (`URLParameterParser.parse` 검증)

#### Day 4 — Swift 코드 작성 (2차)
- [ ] §3.2.6 `FirebaseAppClipBootstrap.swift`
- [ ] §3.2.7 `CustomTokenFetcher.swift`
- [ ] §3.2.8 `OfficeLookupService.swift`
- [ ] §3.2.2 `ContentView.swift` (SwiftUI UI)
- [ ] §3.2.9 `FullAppInstallPrompt.swift` (SKOverlay)

#### Day 5 — Firebase 연동 + 빌드 검증
- [ ] Firebase Console에서 App Clip Bundle ID 등록 → `GoogleService-Info.plist` 다운로드
- [ ] Xcode ClipExtension Target에 `GoogleService-Info.plist` 추가
- [ ] App Clip 빌드 성공 확인
- [ ] §9.1 시나리오 E: Archive → 크기 검증 (10MB 이하)

### Week 2

#### Day 6 — Cloud Function 배포
- [ ] §4.1 `functions/src/customClip.ts` 작성
- [ ] §4.2 `functions/src/index.ts` export 추가
- [ ] `npm run build` 성공 확인
- [ ] `firebase deploy --only functions:generateCustomToken` 배포
- [ ] Firebase Console → Functions 로그 확인
- [ ] §4.3 Firestore Security Rules 재검토 + 필요 시 수정

#### Day 7 — AASA 호스팅
- [ ] §5.1 `homepage/public/.well-known/apple-app-site-association` 생성
- [ ] §5.2 `firebase.json` headers 설정
- [ ] `firebase deploy --only hosting:callmadang-web` 배포
- [ ] §5.3 curl 검증 + Apple CDN 캐시 대기 (24시간)
- [ ] §5.4 TLS 검증 + redirect 확인

#### Day 8 — Flutter 풀앱 측 변경
- [ ] §7.1 `ios/Runner/AppGroupBridge.swift` 작성 (ATTRIBUTION.md §3.6 코드)
- [ ] `ios/Runner/AppDelegate.swift` Bridge 등록
- [ ] Runner Target Capabilities에 App Groups 추가
- [ ] §7.2 `lib/services/app_clip_migration_service.dart` 작성 (ATTRIBUTION.md §3.6 코드)
- [ ] §7.3 `main.dart`에 `migrateIfNeeded` 호출 삽입

#### Day 9 — 실기기 테스트 (1차)
- [ ] iPhone 실기기 준비 (iOS 15+ 권장 iOS 17)
- [ ] Xcode Runner Scheme 실행 → 풀앱 설치 + 실행 (정상 동작 확인)
- [ ] Xcode ClipExtension Scheme 실행 → App Clip 설치 + 실행 (Xcode 직접 실행 모드)
- [ ] Xcode → Product → Scheme → Edit Scheme → Run → Arguments → Environment Variables → `_XCAppClipURL` 설정 (개발 중 테스트 URL 주입)
  - `_XCAppClipURL = https://calllink.io.kr/office?p=seoul&c=gangnam&o=office1&driver=d123&driverName=김기사`
- [ ] §9.1 시나리오 A 검증 (App Clip 단독 실행)

#### Day 10 — 실기기 테스트 (2차)
- [ ] §9.1 시나리오 B 검증 (App Clip → 풀앱 설치 → 이관)
- [ ] §9.1 시나리오 C 검증 (Custom Token 만료)
- [ ] §9.1 시나리오 D 검증 (잘못된 URL host)

### Week 3

#### Day 11 — App Store Connect 등록
- [ ] §6.1 Advanced App Clip Experience 등록
- [ ] §6.2 Header Image 준비 (1800×1200 PNG)
- [ ] §6.3 App Clip Invocation URL 등록
- [ ] (선택) §6.4 랜딩페이지 메타태그 추가

#### Day 12 — Archive + TestFlight 업로드
- [ ] Xcode Product → Archive (Runner + ClipExtension 동시)
- [ ] Organizer → Distribute App → App Store Connect → Upload
- [ ] TestFlight에 표시 대기 (30분 ~ 2시간)
- [ ] TestFlight Internal Testing 등록 → 내부 테스터 초대

#### Day 13 — TestFlight 테스트
- [ ] 내부 테스터 iPhone에서 TestFlight 설치
- [ ] Safari QR 스캔 → App Clip 카드 표시 확인 (AASA 효과)
- [ ] §9.1 시나리오 A~D 외부 네트워크에서 재검증
- [ ] Analytics `appclip_migration_completed` 이벤트 수집 확인

#### Day 14 — App Store 제출
- [ ] TestFlight 테스트 이슈 해결
- [ ] App Clip Experience 최종 검토 (Title, Subtitle, Header Image)
- [ ] App Store Connect → 심사 제출
- [ ] 심사 기간 1~7일 대기
- [ ] 심사 통과 → Production 배포

### Phase 2 완료 후 모니터링

- `appclip_migration_completed` 이벤트 주간 집계
- iOS 사용자 중 App Clip 경유 비율 측정 (목표: 60%+)
- R1_ATTRIBUTION_ACCURACY 재평가 (Phase 1 수동 입력 이탈률 추이)
- 심사 리젝 시 ATTRIBUTION.md + 본 문서 업데이트

---

## 12. 참조

- `flutter/customer_app/MVP/ATTRIBUTION.md §3` — App Clip 개요 + Swift 6 파일 핵심 코드 샘플
- `flutter/customer_app/MVP/FCM.md` — 손님앱 FCM 5종 (App Clip 미포함 정책)
- `flutter/customer_app/PLAN.md §Phase 2` — Phase 2 범위 확정
- `ios/customer_app/PLAN.md` — 원본 Swift 네이티브 App Clip 설계 (재활용)
- `flutter/WORKING_DOC.md §5 의제 13` — 손님앱 iOS 전환 결정
- `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md §9 Track C` — Mac 도착 후 타임라인
- `customer_app/app/src/main/java/com/designated/customer/MainActivity.kt:143-254` — Kotlin Install Referrer 파싱 (URLParameterParser 포팅 원본)
- `functions/src/index.ts` — 기존 CF + `customClip.ts` 신규 export
- `homepage/public/` — Firebase Hosting target `callmadang-web`
- `firebase.json` — Hosting 설정
- Apple App Clip 공식: https://developer.apple.com/documentation/app_clips
- AASA 공식: https://developer.apple.com/documentation/xcode/supporting-associated-domains
- App Clip Experiences 등록: https://developer.apple.com/documentation/app_clips/creating_app_clip_experiences
- SKOverlay: https://developer.apple.com/documentation/storekit/skoverlay
- Firebase Custom Token: https://firebase.google.com/docs/auth/admin/create-custom-tokens
- Firebase Hosting `.well-known`: https://firebase.google.com/docs/hosting/full-config#well-known
- Firebase iOS SDK App Clip 지원: https://firebase.google.com/docs/ios/learn-more#app-clips
- Xcode App Clip 테스트 `_XCAppClipURL`: https://developer.apple.com/documentation/app_clips/testing_your_app_clip_s_launch_experience

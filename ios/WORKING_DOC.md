# iOS 아키텍처 매핑 — 작업 문서

> **목적**: 기사앱·손님앱 iOS(Swift) 포팅 아키텍처 매핑 문서 세트 수립 전, Kotlin/Swift 전문가 2인 논의로 계획을 정밀화하기 위한 베이스 문서.
> **상태**: 논의 진행 전 초안
> **작성일**: 2026-04-15

---

## 1. 배경

- iOS 기사앱·손님앱 모두 **Swift 네이티브**로 확정 (`ios/README.md`, 2026-04-15)
- Android는 Kotlin 유지, iOS만 신규. Flutter 기사앱(`driver_app_flutter/` Phase 1~5)은 **로직 참조 자산**으로만 활용
- Mac mini 확보 확정 → Windows에서는 Mac 도착 전까지 **매핑 문서 작성**만 수행
- Kotlin 소스 인벤토리 완료 (Explore 결과):
  - **driver_app**: 모델 14종 + ViewModel 3개(DriverViewModel ~1,600줄) + 화면 12개 + 서비스 9종 + Firestore 트랜잭션 6곳 + FCM 6종
  - **customer_app**: 모델 6종(~450줄) + ViewModel 5개 + 화면 9개(HomeScreen 1,106줄) + 서비스 6종 + FCM 5종 + Install Referrer→AASA 전환 필요

---

## 2. 초안 계획 (21개 문서 구조)

### 공통 (ios/)
- `SWIFT_CONVENTIONS.md`, `FIREBASE_SETUP.md`, `BUILD_PIPELINE.md`

### 기사앱 (ios/driver_app/)
- `MODELS.md`, `ENUMS.md`, `STATE_MACHINE.md`, `TRANSACTIONS.md`, `VIEWMODELS.md`, `SCREENS.md`, `FCM_HANDLING.md`, `BACKGROUND.md`, `AUTH_FLOW.md`, `REPOSITORY.md` (기존 PLAN.md 보완)

### 손님앱 (ios/customer_app/)
- `MODELS.md`, `ENUMS.md`, `VIEWMODELS.md`, `SCREENS.md`, `ONBOARDING.md`, `APP_CLIP.md`, `FIRESTORE_ACCESS.md`, `FCM_HANDLING.md` (기존 PLAN.md 보완)

---

## 3. 재검토로 식별된 5가지 문제점

### 문제 1: 문서 수 과잉 / `SHARED_LOGIC.md`와 중복
- 기존 `ios/SHARED_LOGIC.md`가 이미 **Firestore 경로·상태 전이·트랜잭션 패턴·FCM 타입·Presence·로그인 플로우·정산 공식**을 포괄함
- 초안의 `STATE_MACHINE.md`, `TRANSACTIONS.md`, `AUTH_FLOW.md`, `FCM_HANDLING.md`는 상당 부분 중복 가능성
- **매핑 문서는 "Kotlin→Swift 변환 델타"만 담아야** 정보 중복 방지

### 문제 2: Flutter 자산 미활용
- `driver_app_flutter/` Phase 1~5는 이미 순수 함수로 정제된 Dart 코드
  - `settlement_calc.dart`, `driver_workflow_notifier.dart`, `driver_screen_ui_state.dart`, `fcm_service.dart`, `presence_service.dart`
- Dart는 Kotlin보다 언어 독립적이고, Swift 포팅 시 1:1에 가까운 구조 매핑 가능
- 초안은 이를 "참조 소스"로만 언급 → **1차 참조로 승격**할지 재검토 필요

### 문제 3: 아키텍처 선결 결정 누락
모든 하위 문서에 영향 주는 결정들이 초안에 명시 안 됨. **팀 논의 필수 항목**:
1. **최소 iOS 버전** — 16 vs 17 (@Observable 매크로, SwiftData 사용 여부 결정)
2. **로컬 저장소** — SwiftData(iOS 17+) vs CoreData vs Realm vs 단순 파일 (기사앱 정산 캐시·오프라인 sync 큐)
3. **상태 관리** — Combine `@Published` + `ObservableObject` vs `@Observable` 매크로 (iOS 17+)
4. **비동기 모델** — Kotlin Flow → Swift AsyncStream vs Combine Publisher (혼용 정책)
5. **CallKit + VoIP Push 구조** — Android `LockScreenActivity` 대체 (전화 수신 UI 활용 범위, background audio 처리)
6. **App Clip 데이터 이관** — AppGroups UserDefaults vs Keychain Sharing vs URL Scheme (Clip → 메인앱 컨텍스트 전달)
7. **아키텍처 패턴** — MVVM 유지 vs TCA(The Composable Architecture) 도입 vs SwiftUI 기본 패턴
8. **의존성 주입** — Hilt → Swift (수동 DI vs Factory vs Swinject vs Resolver)
9. **번들ID 확정** — `com.designated.driver.ios` 가칭 → 최종 확정

### 문제 4: 착수 지점 불명확
- 21개 문서 중 어디부터 시작? 검증 방식은?
- **수직 슬라이스 1개**(예: 로그인 플로우 전체)를 먼저 완성해 템플릿·변환 패턴 확정 후 확장하는 편이 안전

### 문제 5: 손님앱 vs 기사앱 순서 재고
- 초안: 기사앱 먼저 (복잡도 높음)
- 재고: 손님앱이 더 단순하고 **App Clip 리스크**가 집중됨. 손님앱 먼저 하면서 AASA·App Clip 불확실성을 먼저 해소하는 편이 전체 일정 리스크 감소
- 결정 필요

---

## 4. 논의 의제 (우선순위)

팀 논의는 아래 순서로 진행. 각 주제별 **kotlin-expert + swift-expert** 의견 수렴 후 중재자(메인 Claude)가 결론 정리.

| 순서 | 주제 | 영향 범위 |
|------|------|----------|
| 1 | 최소 iOS 버전 확정 (16 vs 17) | 하위 모든 결정 |
| 2 | 상태 관리 패턴 (@Published vs @Observable) | ViewModel 설계 |
| 3 | 로컬 저장소 (SwiftData vs CoreData) | Repository 설계 |
| 4 | 비동기 모델 (AsyncStream vs Combine) | Firestore 리스너·FCM 처리 |
| 5 | CallKit + VoIP Push 구조 | 기사앱 핵심 UX |
| 6 | App Clip 데이터 이관 방식 | 손님앱 온보딩 |
| 7 | 아키텍처 패턴 (MVVM 기본 vs TCA) | 전체 구조 |
| 8 | Flutter Dart 코드 1차 참조 여부 | 매핑 문서 작성 방식 |
| 9 | 손님앱/기사앱 작업 순서 | 일정 |
| 10 | DI 방식, 번들ID 등 잔여 사항 | 기반 설정 |

---

## 5. 논의 결과 (누적)

> 각 주제 합의 도달 시 이 섹션에 **결정 + 근거 + 반대 의견·트레이드오프** 기록.

### 주제 1: iOS 최소 버전 — **iOS 17.2 deployment target 확정** (2026-04-15)

**결정**: 기사앱·손님앱 모두 deployment target = **iOS 17.2**

**근거**:
- @Observable 매크로 + SwiftData 사용 가능 → DriverViewModel(~1,600줄), HomeScreen(1,106줄) 매핑 시 보일러플레이트 실질 감소 (단독 개발자 유지 부담 ↓)
- App Clip 50MB 한도 보장 (iOS 16.0은 10MB → Firebase SDK 묶기 위태). 손님앱 Clip 설계 안정성
- iPhone 8(2017) 사용자 배제 손실 = 사실상 0%. 한국 iPhone 교체 주기 빠름, 2026년 업무용 iPhone 8 사용 거의 없음
- Xcode N-4 정책상 iOS 16은 2027~2028 drop 위험. iOS 17은 안전 마진 큼
- SwiftData iOS 17.0/17.1 migration 버그 회피 → **17.2**가 안전 하한선

**반대 의견 / 트레이드오프**:
- kotlin-expert: Android minSdk 24와의 정책 비대칭 우려 (단, swift-expert API 분석에 결정권 위임함)
- 사용자 판정: iPhone 8 사용자 무시 가능 → 정책 비대칭 수용

**위험 완화**:
- 출시 전 iPhone 기사 기종 설문 1회 (사무실 단위 sampling)
- 발견 시 Android 기사앱 권유 (Android는 minSdk 24로 모든 기종 수용 유지)

**영향**:
- 주제 2(상태관리): @Observable 매크로 사용 가능 → 우선 검토 대상
- 주제 3(저장소): SwiftData 사용 가능 → 우선 검토 대상
- 주제 4(비동기): async/await + Observation 통합 가능
- 모든 향후 매핑 문서: `#available(iOS 17.2, *)` 분기 불필요, 단일 코드 경로 가정

---

### 주제 2: 상태 관리 패턴 — **@Observable 단독 (+ AsyncStream 브리지) 확정** (2026-04-15)

**결정**:
1. **ViewModel**은 `@MainActor @Observable final class`로 작성. ObservableObject + @Published는 사용 안 함
2. **다중 필드 원자성 보존**: Kotlin `DriverScreenUiState.copy(...)`는 Swift에서 **단일 `var uiState: DriverScreenUiState` property**(Equatable struct)로 1:1 매핑. 개별 var로 풀지 않음 → 상태전이 invariant 깨짐 방지
3. **Dedup (distinctUntilChanged 등가)**: @Observable는 dedup 안 함. 핫패스 필드(presence 1초 단위, FCM 연속 emit)만 수동 `guard newValue != _x else { return }` 적용. 일반 필드는 방치
4. **이벤트/스트림/상태 3종 이디엄 분리**:
   - 상태(상시 유효): @Observable property
   - 스트림(연속 emission): Repository에서 `AsyncStream` 노출 → ViewModel `for await` 루프에서 uiState로 흡수
   - 이벤트(일회성 트리거): consume-once optional property + `.onChange(of:)` **또는** 별도 `AsyncStream<Event>` 채널 (동일 값 재이벤트 필요 시 후자)
5. **Lifecycle**: View `.task {}`로 진입 트리거 + `.onChange(of: scenePhase)`로 백그라운드 시 `vm.pauseListeners()` / 복귀 시 `vm.resumeListeners()`. ViewModel 내부에서 Task cancel/재등록. AsyncStream 생성 시 `continuation.onTermination = { _ in reg.remove() }` 필수
6. **Push 수신(VoIP, APNs, FCM)은 AppDelegate 수준 유지** — ViewModel lifecycle에 묶이면 수신 누락. ViewModel과는 NotificationCenter 또는 공유 이벤트 채널로 연결
7. **Combine 혼용 정책**: 외부 라이브러리가 Publisher를 강제 요구할 때만. Firebase iOS SDK 핵심은 async/await 네이티브 지원이라 거의 필요 없음

**근거**:
- DriverViewModel은 10개 StateFlow (1개 통합 UiState + 9개 보조). UI 구독 패턴은 상위 화면 묶음 + 상세화면 개별 혼합 → 단일 struct UiState 유지가 의미 보존
- customer_app 5개 VM은 StateFlow 거의 안 쓰고 Compose `mutableStateOf`만 사용 → @Observable로 거의 1:1 매핑 (변환 난이도 낮음)
- Kotlin StateFlow `distinctUntilChanged` 기본 동작에 앱 전체가 암묵적 의존 (명시 사용은 NetworkMonitor 1곳뿐). Swift는 동일 값 재할당해도 didSet 호출되므로 핫패스 방어 필수
- @Observable는 class 전용. 데이터 모델은 Codable struct로 유지하고 ViewModel/Store만 @Observable class — 충돌 없음
- Firestore 리스너는 ListenerRegistration `.remove()` / 재등록만 가능 (일시중단 API 없음) → pause=remove, resume=새 리스너로 구현
- SwiftData @Model class는 자동 @Observable 포함

**위험 완화**:
- Swift 6 strict concurrency 활성화 권장 → @MainActor 누락 시 컴파일 에러로 방어
- AsyncStream 종료 시 ListenerRegistration 누수 방지 패턴(`onTermination`) 모든 Repository에 표준화
- carryOver Realtime listener 같은 "백그라운드에서 죽어야 하는 리스너"는 ScenePhase 명시 제어
- 이벤트는 consume-once 패턴 엄수 (재진입 시 stale 이벤트 재소비 방지)

**미해결 후속 (개별 매핑 시 결정)**:
- DriverViewModel `DriverScreenUiState` struct에 `with(_:_:)` keyPath helper 사용 vs 직접 `var c = self; c.foo = x; uiState = c` 패턴 — 코딩 스타일 결정 (영향 적음)
- 핫패스 dedup 적용 대상 필드 목록 확정 (presence, FCM 메시지 수신 카운터 등) — 매핑 문서 작성 시 식별

---

### 주제 3: 로컬 저장소 — **SwiftData 채택 확정** (2026-04-15)

**결정**:
1. **기사앱 정산 캐시 + PendingSync 큐**: SwiftData `@Model` 클래스로 매핑
2. **손님앱**: 로컬 DB 미사용. SwiftData 도입하지 않음 (Firestore 직접 + in-memory 상태만)
3. **마이그레이션 정책**: Room `fallbackToDestructiveMigration()` 등가 — 마이그레이션 실패 시 DB 리셋 허용 (서버가 진실 원본). `VersionedSchema` + `SchemaMigrationPlan`은 lightweight 자동 + custom 단계 사용
4. **동시성**: `@ModelActor` 기반 SyncWorker 분리. UI 컨텍스트는 `modelContainer.mainContext`, 백그라운드 sync는 ModelActor
5. **DTO ↔ Model 매핑 계층 도입**: Firestore Codable struct (CallDTO 등) → SwiftData `@Model` 클래스로 복사하는 매핑 함수. Decoupling 이점
6. **백그라운드 sync 전략**: `BGTaskScheduler` (보조) + foreground 진입 시 즉시 큐 flush (1차) + `NWPathMonitor` 온라인 복귀 감지 시 drain
7. **트랜잭션 단위 보존**: `syncAllPending` 루프는 **항목별 `try context.save()`**. 전체 루프를 하나로 묶지 않음 (실패 격리 보존)
8. **충돌 해결**: server-wins on read + version compare. `@Model`에 `version: Int64` 필드 보존, 수동 비교 로직

**근거**:
- Room 사용 범위가 극단적으로 단순: 엔티티 2개(`PendingSyncEntity`, `SettlementCacheEntity`) + DAO 1개(17 함수) + 관계 없음 + `@Transaction` 0건 + Migration 0건 + JSON blob 직렬화로 회피
- Flow 구독은 `observePendingSyncCount()` 단 1개 → SwiftData `@Query`로 대응 충분 (변경 감지 자동)
- iOS 17.2 deployment target에서 SwiftData `SchemaMigrationPlan`·`@ModelActor`·`#Predicate` 안정화
- CoreData는 엔티티 2개에 과잉 (NSManagedObject subclass + .xcdatamodeld 편집기 부담)
- 단순 파일 저장은 변경 감지 구독·indexing·atomic write 부담을 개발자가 짐 → 비추
- WorkManager의 "반드시 실행" 보장은 BGTaskScheduler에 없음 → foreground flush 1차 정책으로 보완

**위험 완화**:
- iOS 17.2 SwiftData 백그라운드 save crash 방지: 모든 BG write는 `@ModelActor` 경유 + try/catch 로깅
- ModelContainer 초기화 실패 = 앱 기동 불가 → app start initializer try-catch + destructive fallback (정산 캐시는 Firestore에서 재로드 가능)
- BGTaskScheduler ID는 Info.plist `BGTaskSchedulerPermittedIdentifiers`에 사전 선언 필수
- SwiftData CloudKit 동기화는 명시적으로 OFF (Firestore와 이중 원천 충돌 방지)
- `#Predicate` 컴파일 통과해도 런타임 에러 가능 → 매핑 문서 작성 시 사전 검증

**미해결 후속 (매핑 문서 작성 시 결정)**:
- JSON blob(metadataJson/totalsJson/callsJson) 처리 방식 — String 유지 + JSONDecoder 수동 vs @Attribute 변환기 vs 중첩 @Model 분해 (성능·이관 복잡도 평가)
- SwiftData 저장소 파일 보호 레벨 — 백그라운드 FCM 처리 중 접근성 검증 필요
- destructive fallback 활성화 옵션 — `ModelConfiguration` 파라미터 확정

---

### 주제 4: 비동기 모델 — **AsyncStream 단독 기본 + Combine 좁은 케이스만 확정** (2026-04-15)

**결정**:
1. **데이터 스트림 기본**: AsyncStream / AsyncSequence
2. **Combine은 다음 케이스만**:
   - 3단 이상 operator 체인이 짧고 선언적이어야 할 때 (`debounce` + `combineLatest` + `removeDuplicates` 등)
   - SwiftUI `.onReceive(publisher)` modifier 활용
   - Firebase iOS SDK가 Publisher를 강제 노출 (현재 코드엔 없음)
3. **callbackFlow 5곳 → AsyncStream 1:1 매핑**: NetworkMonitor + customer_app 4곳 (BannerAdService, PointService×2, CallService). `awaitClose { listener.remove() }` → `continuation.onTermination = { _ in reg.remove() }`
4. **multicast 패턴**: Store/Repository가 1회 구독 → `@Observable` property로 노출 → 다중 화면이 property 관찰. AsyncStream을 직접 다중 구독하지 않음 (이벤트 split 위험)
5. **ViewModel cancellation**: `viewModelScope` 자동 취소 등가 없음 → ViewModel이 보관한 `Task<Void, Never>?`를 `stop()` 또는 명시 메서드에서 cancel. `deinit`은 @MainActor 조합 시 호출 보장 약함
6. **Dispatchers.IO 대응**: Swift엔 IO dispatcher 직접 등가 없음. 대부분 Firebase async API가 자체 큐 처리. 실제 IO 분리 필요 시 전용 actor (예: `@ModelActor`) 또는 `Task.detached(priority: .utility)` (단 lifecycle 책임 발생)
7. **distinctUntilChanged**: AsyncStream 자체엔 없음. 핫패스만 소스 단(`for await x in stream where x != last`) 또는 setter guard 수동 적용
8. **back-pressure**: Firestore listener는 emission 빈도 낮아 default unbounded 무난. presence 같은 핫패스만 `AsyncStream(bufferingPolicy: .bufferingNewest(1))` 명시
9. **swift-async-algorithms 도입 보류**: 현재 코드에 `combine`/`flatMapLatest`/`debounce`/`throttle` 사용 0건이라 불필요. 향후 도입 검토는 필요 시점에

**근거**:
- Kotlin 코드 인벤토리: callbackFlow 5곳, Flow operator 체인 사실상 0건, `runBlocking`/`withTimeout`/`async/await`/`supervisorScope`/`SharedFlow`/`stateIn`/`shareIn` 모두 0건
- 즉 비동기 표면이 매우 단순 → AsyncStream 단독 매핑으로 충분, Combine 혼용 정당성 약함
- multicast 필요한 곳(uiState 다중 화면 구독 등)은 @Observable Store 경유로 자연스럽게 해결됨 (Topic 2 결정과 일관)
- AsyncStream `onTermination` 보장으로 Firestore 리스너 leak 방지 (CLAUDE.md "비용 최적화 ~$414/월 절감" 정책 보존)

**위험 완화**:
- ViewModel에 보관된 Task는 명시 cancel 패턴 표준화 (`stop()` 메서드 + View `.onDisappear`/`scenePhase` 트리거)
- AsyncStream 단일 consumer 제약 인지 — 같은 stream 두 Task에서 `for await` 시 데이터 split 위험 → 반드시 Store 경유
- AddressSearchHelper의 detached scope는 anti-pattern → iOS 이관 시 일반 `Task { }` (현재 context 상속)로 수정
- Swift 6 strict concurrency 활성화 → @MainActor 누락 컴파일 타임 검출
- back-pressure drop 의도성: Firestore listener 폭주 시 default `bufferingNewest(1)` 적용 후 실기기 관측

**미해결 후속 (매핑 문서 작성 시 결정)**:
- 핫패스 dedup 적용 대상 필드 목록 (presence, NetworkMonitor 외)
- AddressSearchHelper iOS 이관 시 actor 분리 vs Task 패턴 결정
- swift-async-algorithms 도입 시점 (확장 시 재검토)

---

### 주제 5: CallKit + VoIP Push 구조 — **Phase 분리 방식 확정** (2026-04-15)

**결정**: 단일 옵션 채택 대신 **2단계 진행**:

**Phase 1 (1차 출시)** — 기본 알림 메커니즘
- 일반 APNs + **Time Sensitive Notification (`interruptionLevel = .timeSensitive`)**
- FCM 6종 메시지 모두 Time Sensitive로 처리
- "앱 종료 금지" 온보딩 안내로 강제 종료 시나리오 회피
- VoIP+CallKit 미도입 → 심사 리스크 회피

**Phase 2 (조건부 보강)** — Phase 1 운영 데이터 기반 결정
- TestFlight 파일럿 + 정식 출시 후 콜 놓침률 측정
- Android 대비 유의미한 차이 발견 시 → **VoIP+CallKit + Live Activity 하이브리드** 도입
- 비슷하면 Phase 1 유지

**근거**:
- 사용자 통찰: 안드로이드의 LockScreenActivity·3초 반복음·강제 깨움은 **edge case 보강**이며, 기본 사이클(콜매니저 배차 → 수신 → 수락 → 운행 → 정산 → WAITING 복귀)은 일반 push로도 작동
- VoIP+CallKit는 심사 리스크 + 구현 공수 + 디자인 봉쇄 모두 비용 큰 결정. 데이터 없이 선결하면 과투자
- 콜매니저·손님앱 연동 로직은 푸시 메커니즘과 무관 — 동일 Firestore 트랜잭션 + CF가 자동 작동
- iOS 17.2 deployment target에서 Time Sensitive·Live Activity·VoIP Push 모두 사용 가능 → Phase 2 전환 비용도 제한적

**Phase 1 → Phase 2 전환 트리거**:
- iOS 콜 놓침률이 Android 대비 X% 이상 (X는 파일럿 후 결정)
- 관리자 피드백: "iOS 기사가 자주 안 받음" 빈도
- iOS 기사 수입 vs Android 기사 수입 격차

**미해결 후속 (Phase 2 결정 시점에)**:
- VoIP push 채널 추가 (CF에서 APNs 직접 호출 인프라)
- Live Activity push-to-start 구현
- App Store 심사 정당화 자료 준비

---

## 6. 합의 기반 최종 매핑 계획 — **재구조화 (2026-04-15)**

> 초안의 21개 문서 구조는 폐기. **Phase 기반 + 라이프사이클 인벤토리 우선** 방식으로 전환.

### 기본 vs 보강 정의

| 분류 | 정의 |
|------|------|
| **기본 기능** | 콜 사이클 1순환(콜매니저 배차 → 수신 → 수락/거절 → 운행 → 정산 → WAITING 복귀)이 작동하는 데 필수. 누락 시 사이클 종결 불가 |
| **보강 기능** | 사이클은 작동하나 UX·신뢰성·관리자 부담을 개선하는 안드로이드 특수 처리 (Foreground Service, FullScreenIntent, 3초 반복음, 풀스크린 자체 UI, 강제 종료 복원 등) |

### 평가 차원 (각 라이프사이클 단계마다 3차원)

```
[라이프사이클 단계 N]
  ├─ Happy Path   : 정상 네트워크 + 정상 흐름
  ├─ Cancellation : 각 상태에서 발생 가능한 취소 (관리자/기사/손님/타임아웃)
  └─ Offline      : 네트워크 끊김 시 동작 (자동 큐잉 / 명시적 처리 / 불가능)
```

### Phase 진행 구조

```
Phase 0: 라이프사이클 전수 인벤토리 (지금 시작)
  ├─ 기사앱 순수 기능 전수
  ├─ 콜매니저 ↔ 기사앱 연동 포인트 (양방향 FCM + Firestore 트랜잭션)
  ├─ 손님앱 ↔ 기사앱 연동 포인트 (CF 경유 FCM + Firestore 상태 변화)
  ├─ 각 단계의 취소 경로
  └─ 각 단계의 오프라인 동작
  → 산출: ios/FUNCTIONAL_INVENTORY.md

Phase A: 기본/보강 분류 (사용자 + 중재자)
  ├─ 인벤토리 각 항목을 "기본" 또는 "보강"으로 라벨
  └─ 보강은 우선순위(1차 보강 / 2차 보강 / 미적용 검토)로 세분
  → 산출: FUNCTIONAL_INVENTORY.md 분류 컬럼 채움

Phase B: 1차 매핑 문서 작성 (기본 기능만)
  ├─ ios/MVP/ 폴더에 매핑 문서 작성
  ├─ 모델·ViewModel·Firestore 트랜잭션·푸시 처리·정산 흐름
  └─ 보강 기능은 "Phase 2 deferred" 표시
  → 산출: ios/MVP/*.md (8~10개)

Phase C: 1차 구현 + 파일럿 (Mac 도착 후)
  ├─ Phase B 매핑 기반 Swift 구현
  ├─ TestFlight 외부 테스터 파일럿
  └─ 콜 놓침률·관리자 피드백·기사 수임 데이터 수집
  → 산출: 1차 출시 + 운영 데이터셋

Phase D: 2차 매핑 + 보강 구현 (데이터 기반)
  ├─ Phase C 데이터로 보강 우선순위 재정렬
  ├─ 필요한 보강만 매핑 작성
  └─ Phase 2 출시
  → 산출: ios/REINFORCEMENT/*.md + 2차 출시
```

### Phase 0 작업 분담

| 담당 | 작업 |
|------|------|
| **kotlin-expert** | 기사앱 라이프사이클 전수 분석 + 콜매니저·손님앱 연동 포인트 + 각 단계 취소 경로 + 오프라인 동작 (Kotlin 코드 + manager-analyst·firebase-analyst 자료 통합) |
| **swift-expert** | kotlin-expert 결과 수령 후, 각 항목에 대해 iOS 측 "기본 구현 가능성" + "보강 옵션" 1줄 평가 |
| **사용자 + 중재자** | 결과 검토 → 기본/보강 라벨링 결정 |

### 기존 합의(주제 1~5)와의 관계

주제 1~5에서 확정된 **기술 선택**(iOS 17.2 / @Observable / SwiftData / AsyncStream / Phase 분리 푸시)은 **Phase B 매핑 작성 시 그대로 적용**. 주제 6~10(App Clip, 아키텍처 패턴, Flutter 참조, 앱 순서, DI)은 Phase B 진입 시점에 **필요한 항목만** 다시 다룸.

### 산출물 경로 정리

- `ios/WORKING_DOC.md` — 본 문서 (논의 누적 + 최종 계획)
- `ios/FUNCTIONAL_INVENTORY.md` — Phase 0 산출물
- `ios/MVP/` — Phase B 매핑 문서들
- `ios/REINFORCEMENT/` — Phase D 매핑 문서들
- `ios/SHARED_LOGIC.md` — 언어 독립 로직 명세 (유지)
- `ios/README.md` — iOS 전체 전략 (유지)

### 검증 방법

- **Phase 0**: 인벤토리가 콜 사이클 1순환의 모든 단계 + 모든 취소 경로 + 모든 오프라인 시나리오를 누락 없이 포함하는지
- **Phase A**: 모든 항목이 기본/보강 중 하나로 분류되었는지 + 분류 근거가 기록되었는지
- **Phase B**: 매핑 문서를 보고 Mac 도착 후 즉시 타이핑만 해도 컴파일되는 수준인지
- **Phase C**: TestFlight 파일럿 데이터로 Phase 2 전환 트리거가 발동되는지
- **Phase D**: 보강 도입 후 콜 놓침률·관리자 피드백 개선되는지

---

## 7. 현재 상태 (2026-04-15 종료 시점)

### 완료
- **Phase 0**: `ios/FUNCTIONAL_INVENTORY.md` 전수 인벤토리 + iOS 평가 컬럼
- **Phase A**: B(63) / R1(5) / R2(3) / D(4) 분류 확정, Phase 1 MVP Scope 목록화
- **Phase B**: `ios/driver_app/MVP/` 8개 매핑 문서 작성 + 검토 38건 수정 완료
- **Phase B 산출물**:
  - MVP/: OVERVIEW, MODELS, ENUMS, VIEWMODELS, FIRESTORE, AUTH, SCREENS, PUSH, SETTINGS
  - REINFORCEMENT/TRIGGERS.md (발동 기준 미리 정의)

### 팀 상태 (ios-architecture)
- **team-lead** (중재자): 본 세션에서 지휘
- **kotlin-expert** (blue): idle, Phase 0~B 전 과정 수행 완료
- **swift-expert** (green): idle, Phase 0~B 전 과정 수행 완료
- **팀 해체 금지** (CLAUDE.md 규칙) — 언제든 재소환 가능

### 사용자 지시 우선순위 (2026-04-15 결정)
1. **(먼저) 콜매니저 수정** — iOS 팀 대기 상태에서 진행
2. **(A) REINFORCEMENT/ 8개 스텁 작성** — 트리거 발동 시 빠른 착수용
3. **(B) Android AND-02, AND-03 검증·수정** — iOS 출시 전 우선 권장
4. **(C) Mac 확보 → Phase C 구현 착수** — `ios/driver_app/MVP/` 문서 기반 Swift 타이핑

### 팀 재소환 절차
향후 iOS 작업 재개 시:
1. 이 문서(`ios/WORKING_DOC.md`)를 먼저 읽어 현재 상태 파악
2. `ios/FUNCTIONAL_INVENTORY.md` 분류표 참조
3. `ios/driver_app/MVP/` 8개 문서 + `REINFORCEMENT/TRIGGERS.md` 참조
4. kotlin-expert / swift-expert에게 SendMessage로 새 임무 부여 — 둘 다 컨텍스트를 파일 기반으로 복원 가능
5. `.agent-teams/kotlin-expert.md`, `.agent-teams/swift-expert.md`에 페르소나 정의 유지됨

### 미결 항목 (재소환 시 착수)
- REINFORCEMENT/ 세부 문서 8개 (R1_LOCKSCREEN, R1_APP_KILLED, R1_PENDING_SYNC, R1_IMMEDIATE_SYNC, R1_NETWORK_BANNER, R2_BG_SYNC, R2_REFERRAL_QR, R2_WAKE_LOCK)
- Firebase 11.x `runTransaction` 신 async 시그니처 Mac에서 1회 실컴파일 검증
- Mac 도착 후 Xcode 프로젝트 생성 + Phase C 구현
- TestFlight 파일럿 인프라(ACK 대시보드, 피드백 채널)
- 손님앱 iOS 포팅(App Clip 등) — 별도 Phase

---

## 6. 합의 기반 최종 매핑 계획

> 논의 완료 후 아래 섹션을 채움.
> - 축소된 문서 목록 (예상 8~10개)
> - 작성 순서 (수직 슬라이스 포함)
> - `ios/README.md`·`ios/SHARED_LOGIC.md`와의 경계
> - 검증 방법

_(논의 진행 전 — 비어있음)_

---

## 7. 참조 파일

- `ios/README.md` — iOS 전체 전략, Mac 확보 방침
- `ios/SHARED_LOGIC.md` — 언어 독립 로직 명세 (경계 대상)
- `ios/driver_app/PLAN.md`, `ios/customer_app/PLAN.md` — 기존 개요
- `CLAUDE.md` — Firestore 경로·상태 전이·정산 공식 원본
- `driver_app_flutter/lib/` — Flutter Phase 1~5 구현 (로직 참조 후보)
- `driver_app/app/src/main/java/com/designated/driverapp/` — Kotlin 기사앱 원본
- `customer_app/app/src/main/java/com/designated/customer/` — Kotlin 손님앱 원본

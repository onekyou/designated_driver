# Flutter 손님앱 Phase 1 + Phase 2 — 개요

> **목적**: Kotlin `customer_app/`을 Flutter로 신규 포팅 (Phase 1) + Mac 도착 후 Swift App Clip 타겟 추가 (Phase 2).
> **기사앱 OVERVIEW.md 자매 문서**.
> **작성일**: 2026-04-16

---

## Phase 1 한 줄 정의

**손님이 Flutter 앱(iOS/Android)으로 콜 요청·포인트 적립을 동일하게 사용할 수 있다.**

### Phase 2 추가 정의
**iOS 손님이 QR 스캔 → App Clip → 즉시 콜 요청. 풀앱 설치 후 Custom Token으로 자동 인증 상속.**

---

## 완료 기준

### Phase 1 (Flutter 신규 포팅)
1. **콜 요청 → 배차 → 운행 → 완료 사이클** Android/iOS 양쪽 실기기 검증
2. **Phone Auth 인증 + 프로필 설정** 동작 (Anonymous Auth 자동 + 전화번호 입력)
3. **5종 FCM 수신** (CALL_RECEIVED / DRIVER_ASSIGNED / RIDE_COMPLETED / CALL_CANCELLED / call_status_update)
4. **포인트/등급 시스템** (BRONZE 3% / SILVER 5% / GOLD 7% / VIP 9%)
5. **수동 사무실 코드 입력** + QR 스캔 (단축 코드 6자리 + mobile_scanner)
6. **취소 경로** (CANCELLED_BY_CUSTOMER, ACCEPTED/PREPARING 단계까지)
7. **TestFlight Internal/External 파일럿** + Play Store closed testing 동시
8. **기존 Kotlin 손님앱 교체 전략 실행** (의제 10 — 같은 applicationId + Staged Rollout)

### Phase 2 (Mac 도착 후 App Clip 추가)
9. **Swift App Clip 타겟** 작성 (~500 LOC)
10. **AASA 호스팅** + App Clip Card 등록
11. **App Group + Custom Token** 풀앱 데이터 이관
12. **App Store Review** 별도 트랙 통과
13. **수동 입력 이탈률 측정** (R1_ATTRIBUTION_ACCURACY 발동 기준 30%)

---

## 의제 결정 11건 손님앱 반영 매핑

| 의제 | 결정 | 손님앱 Phase 6 실행 항목 |
|------|------|------------------------|
| **1** iOS 15.0 | iOS 15 deployment target | 손님앱도 iOS 15 통일 |
| **4** apns 블록 | CF 5종 손님 송신 함수 audit | `onCallAssigned` 외 4종 (DRIVER_ASSIGNED 등) |
| **5** FCM 필드명 | `fcmToken` + `fcmTokenPlatform` 메타 | `customerInfo/{phone}` 경로에 메타 추가 |
| **6** Privacy Manifest | 사유 문자열 3종 (Camera/Location/Mic) + Privacy Manifest | 카메라 추가 (QR 스캔용, 의제 ATTRIBUTION B+C 옵션) |
| **7** LockScreen | DRIVER_ASSIGNED + CALL_CANCELLED만 Time Sensitive | 손님앱은 차등 적용 |
| **8** Foreground Service | 손님앱은 Foreground Service 거의 불필요 | 콜 요청 시점만 활성. iOS no-op 그대로 |
| **9** 자동로그인 | Anonymous Auth 자동 상속 (재로그인 불필요) | 기사앱과 다른 흐름 — 손님은 인증 불요 |
| **10** 손님앱 전환 | 같은 applicationId + Staged Rollout + Remote Config 최소 버전 | **본 앱 전환 핵심 결정** |
| **11** 측정 인프라 | `customer_*` 이벤트 8종 + Firebase Analytics/Crashlytics | acceptanceEvents는 기사앱 전용, 손님앱은 별도 |
| **12** 로컬 저장소 | shared_preferences + Firestore 오프라인 | 손님앱은 정산 캐시 불필요, 더 단순 |
| **14** 기반 정책 | 기사앱과 동일 (Dart 3 / Riverpod 2.x / FVM / Freezed) | 동일 적용 |

---

## Phase 1 범위 (포함 — B)

### 화면 (11개, Kotlin 11개와 1:1)
- HomeScreen (콜 요청, 위치/도착지 입력, mobile_scanner QR 스캔)
- PhoneAuthScreen (Firebase Phone Auth)
- TermsAgreementScreen + PrivacyPolicyScreen
- ProfileSetupScreen + ProfileScreen
- PointScreen + PointHistoryScreen
- CallHistoryScreen
- MainNavigation (BottomNavigation 5탭)
- OfficeCodeInputScreen (사무실 코드 수동 입력 — Phase 2 App Clip으로 대체될 화면)

### FCM 5종 (`flutter/customer_app/MVP/FCM.md` 참조)
- CALL_RECEIVED, DRIVER_ASSIGNED, RIDE_COMPLETED, CALL_CANCELLED, call_status_update

### 데이터 모델 (6개)
- CustomerCall (18 fields)
- CustomerInfo (12 fields)
- CustomerPoints (8 fields)
- CustomerGrade (enum BRONZE/SILVER/GOLD/VIP)
- PointTransaction (9 fields)
- BannerAdData (10 fields, R2)

### MainViewModel 995 LOC 분해 (Riverpod 4 Notifier)
- **CallNotifier**: 콜 요청·취소·복구·FCM 라우팅
- **PointNotifier**: 포인트 관찰·사용·환불·이력
- **ProfileNotifier**: 프로필·사무실 정보·약관
- **AuthNotifier**: Phone Auth·자동 로그인·FCM 토큰

교차 의존 3지점 (CallNotifier ↔ PointNotifier, FCM.md §6 참조).

### 사무실 코드 입력 (ATTRIBUTION.md §2)
- **B 옵션**: 단축 코드 6자리 (`office_codes/{code}` 매핑 테이블)
- **C 옵션**: QR 카메라 스캔 (mobile_scanner 패키지)
- B+C 조합 권장

---

## Phase 2 범위 (Mac 도착 후 — App Clip Swift)

### Swift App Clip 타겟 (~500 LOC)
- ContentView.swift: URL 파라미터 파싱 + 콜 요청 UI
- URLParameterParser.swift: Kotlin MainActivity 1:1 포팅
- AppGroupStorage.swift: UserDefaults(suiteName:)
- FirebaseAppClipBootstrap.swift: Firebase iOS SDK 초기화
- CustomTokenFetcher.swift: Cloud Function 호출
- AppClipCoordinator.swift: 11단계 통합

### Cloud Function 신규
- `generateCustomToken(uid)` Callable

### AASA 호스팅
- Firebase Hosting `homepage/public/.well-known/apple-app-site-association`

### Flutter 풀앱 측 변경
- AppGroupBridge.swift (Swift MethodChannel 핸들러)
- AppClipMigrationService.dart (Dart 1회성 이관)

상세는 `flutter/customer_app/MVP/PHASE2_APPCLIP.md` 참조.

---

## Phase 1 범위 (제외)

### Phase 2 (Mac 도착 후)
- ❌ Swift App Clip 타겟
- ❌ AASA 호스팅
- ❌ Custom Token 이관

### R1 (운영 데이터 발동)
- R1_ATTRIBUTION_ACCURACY: 수동 입력 이탈률 30% 이상 → Phase 2 App Clip 우선
- R1_PUSH_RELIABILITY: iOS FCM 수신률 90% 미만 → CF apns 보강

### R2
- R2_VOICE_INPUT: 음성 입력 (`speech_to_text`)
- R2_BANNER_AD: 배너 광고 (BannerAdData)

---

## 기술 스택

| 항목 | 결정 |
|------|------|
| 언어 | Dart 3.x (sealed class + patterns) |
| Flutter SDK | 3.41.6 FVM |
| iOS deployment | 15.0 |
| Android minSdk | 24 |
| 상태 관리 | Riverpod 2.x `^2.5.1` (4 Notifier 분해) |
| 데이터 모델 | Freezed `required` + nullable + `@Default()` |
| 라우팅 | GoRouter |
| 로컬 저장 | shared_preferences + flutter_secure_storage |
| Firebase | core/auth(Phone+Anonymous)/messaging/firestore/functions/analytics/crashlytics |
| 알림 | flutter_local_notifications (DRIVER_ASSIGNED + CALL_CANCELLED만 Time Sensitive) |
| QR 스캔 | mobile_scanner (Phase 1) |
| App Clip | Swift Native (Phase 2) |

### 기사앱과의 의존성 차이
- ❌ firebase_database (손님앱 Presence 불필요)
- ❌ flutter_foreground_task (손님앱 백그라운드 거의 없음)
- ❌ qr_flutter (손님앱은 스캔 측, 생성 측 아님)
- ✅ mobile_scanner (Phase 1 신규)
- ✅ Phone Auth + Anonymous Auth (양쪽 사용)

---

## 패키지 ID

- **Android 현재 (Kotlin)**: `com.designated.customer.app`
- **Android Phase 1 (Flutter)**: 동일 유지 (의제 10)
- **iOS Phase 1 (Flutter 메인)**: `com.designated.customer.ios` (가칭, 의제 2 통합 결정)
- **iOS Phase 2 (App Clip)**: `com.designated.customer.ios.Clip`
- **App Group**: `group.com.designated.customer`

---

## Phase 6 실행 순서 (손님앱)

### Week 0~1 (기사앱과 병렬)
1. `customer_app_flutter/` 디렉토리 생성 + Flutter create
2. pubspec.yaml 작성 (firebase + analytics + crashlytics + mobile_scanner)
3. iOS Podfile 15.0 + Info.plist Permission 사유 4종 (Camera 추가)
4. PrivacyInfo.xcprivacy 작성
5. Android applicationId `com.designated.customer.app` 유지

### Week 1~3 (Kotlin → Flutter 포팅)
6. 6 Freezed 모델 + 4 Riverpod Notifier 작성
7. 11 화면 위젯 작성 (mobile_scanner 통합)
8. FCM 5종 핸들러 (FCM.md 참조)
9. Phone Auth + Anonymous Auth flow
10. Points/Grade 계산 로직 (순수 Dart 함수)
11. ATTRIBUTION 수동 입력 + QR 스캔 화면

### Week 3~5 (TestFlight + Play Store 동시)
12. Codemagic 빌드 (기사앱 같은 codemagic.yaml 워크플로우 추가)
13. Android Internal Testing 5~10명 (기사들의 손님 역할)
14. iOS TestFlight Internal
15. External Beta + 메타데이터

### Week 5~7 (정식 출시)
16. Android Staged Rollout (1%→5%→25%→100%)
17. iOS App Store Release
18. Firebase Remote Config `min_supported_version_*` 설정 (의제 10)

### Mac 도착 후 (Phase 2, +1~3주)
19. Xcode 설치 + App Clip Target 생성
20. Swift 6 파일 작성
21. generateCustomToken CF 배포
22. AASA 호스팅
23. App Clip Experience 등록 + Review 제출

---

## 매핑 문서 구성 (8개, 본 OVERVIEW 포함)

| 파일 | 내용 | 작성자 | 상태 |
|------|------|--------|------|
| `OVERVIEW.md` | 본 문서 | 중재자 | ✅ 완료 |
| `MODELS.md` | 6 Freezed 모델 + JSON serialization | kotlin-expert | ⏳ |
| `NOTIFIERS.md` | 4 Riverpod Notifier 분해 + 교차 의존 | kotlin-expert | ⏳ |
| `SCREENS.md` | 11 Flutter 위젯 명세 | kotlin-expert | ⏳ |
| `FCM.md` | 5종 FCM 핸들링 + Time Sensitive 차등 | flutter-expert | ✅ 완료 |
| `AUTH.md` | Phone Auth + Anonymous Auth flow | kotlin-expert | ⏳ |
| `ATTRIBUTION.md` | Phase 1 수동 입력 + Phase 2 App Clip 개요 | flutter-expert | ✅ 완료 |
| `PHASE2_APPCLIP.md` | Phase 2 Swift App Clip 상세 구현 | flutter-expert | ⏳ 진행 중 |

---

## 리스크 & 사전 완화

| 리스크 | 완화 |
|-------|------|
| Anonymous Auth uid 자동 상속 실패 → 기존 손님 데이터 고아 | 의제 10 같은 applicationId + Firebase Auth SDK 호환 실측 (실기기 업그레이드 검증) |
| 사무실 코드 입력 이탈률 30% 이상 | R1_ATTRIBUTION_ACCURACY 발동 → Phase 2 App Clip 우선 진입 |
| QR 스캔 카메라 권한 거부 | 사유 문자열 명확화 + 수동 입력 fallback |
| Install Referrer 10+ 필드 MethodChannel 이관 누락 → 외상 결제 계좌 빈칸 | ATTRIBUTION.md §2 이관 helper 명시 |
| RIDE_COMPLETED Race Condition (포인트 적립 타이밍) | FCM.md §6 500ms 지연 또는 PointNotifier 실시간 리스너 |
| Phase 2 App Clip 10MB 한도 초과 | iOS 15 / iOS 16+ 분기 (16+ 50MB) + Build size 모니터링 |
| Custom Token 1시간 만료 → 풀앱 첫 실행 지연 시 무효 | App Clip 후 즉시 풀앱 설치 유도 UX |

---

## 참조

- `flutter/customer_app/PLAN.md` — 손님앱 Phase 1/2 전체 계획
- `flutter/customer_app/MVP/FCM.md` ✅ — FCM 5종 핸들링
- `flutter/customer_app/MVP/ATTRIBUTION.md` ✅ — 귀속 Phase 1/2
- `flutter/customer_app/MVP/PHASE2_APPCLIP.md` ⏳ — App Clip 상세
- `flutter/driver_app/MVP/OVERVIEW.md` — 기사앱 자매 문서
- `flutter/WORKING_DOC.md` §5 — 의제 결정 전문
- `flutter/customer_app/REINFORCEMENT/TRIGGERS.md` — R1/R2 발동 기준
- `customer_app/app/src/main/java/com/designated/customer/` — Kotlin 원본
- `ios/customer_app/PLAN.md` — Phase 2 App Clip 원본 설계

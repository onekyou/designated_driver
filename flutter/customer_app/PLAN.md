# 손님앱 Flutter Phase 1 + Phase 2 App Clip 계획

> **목적**: 배포 중인 Kotlin 손님앱을 Flutter로 신규 포팅하여 iOS/Android 동시 지원. iOS 귀속 문제는 Phase 2 App Clip Swift 하이브리드로 해결.
> **작성일**: 2026-04-16

---

## 전체 아키텍처

```
Phase 1 (Mac 없이, Codemagic 기반)
├── Flutter 메인앱 (Android + iOS 공통)
│   ├── 11 화면 (Phone Auth → 프로필 → 콜 요청 → 포인트)
│   ├── 5 FCM 타입 핸들러
│   ├── Phone Auth (Firebase Auth)
│   └── 수동 사무실 코드 입력 (Install Referrer 대체)
└── Kotlin 기존 앱 교체 (의제 10 결정)

Phase 2 (Mac 도착 후)
├── Flutter 메인앱 (그대로)
└── Swift App Clip 타겟 (~500줄)
    ├── QR URL 파라미터 파싱 (8개 키)
    ├── App Group UserDefaults 공유
    ├── Firebase Custom Token 이관
    └── AASA 호스팅 (Firebase Hosting)
```

---

## Phase 1 목표

### 한 줄 정의
**손님이 Flutter 앱(iOS/Android)으로 콜 요청·포인트 적립을 동일하게 사용할 수 있다.**

### 완료 기준

1. **콜 요청 → 배차 → 운행 → 완료 사이클** Android/iOS 양쪽 실기기 검증
2. **Phone Auth 인증 + 프로필 설정** 동작
3. **5종 FCM 수신** (CALL_RECEIVED, DRIVER_ASSIGNED, RIDE_COMPLETED, CALL_CANCELLED, call_status_update)
4. **포인트/등급 시스템** 정상 동작 (BRONZE 3% / SILVER 5% / GOLD 7% / VIP 9%)
5. **수동 사무실 코드 입력** 화면 → 사무실 연결 (Install Referrer iOS 대체)
6. **취소 경로** (CANCELLED_BY_CUSTOMER, ACCEPTED/PREPARING 단계까지 가능)
7. **TestFlight Internal/External 파일럿** + Play Store closed testing 동시
8. **기존 Kotlin 앱 교체 전략 실행** (의제 10 결정)

---

## Phase 1 범위 (포함 — B)

### 화면 (11개, Kotlin 11개와 1:1 대응)
- HomeScreen (콜 요청, 위치/도착지 입력)
- PhoneAuthScreen (Firebase Phone Auth)
- TermsAgreementScreen + PrivacyPolicyScreen
- ProfileSetupScreen + ProfileScreen
- PointScreen + PointHistoryScreen
- CallHistoryScreen
- MainNavigation (BottomNavigation: Home/Profile/History/Points/Settings)

### FCM 5종
| 타입 | Flutter 핸들러 |
|------|----------------|
| CALL_RECEIVED | 콜 요청 확인, MainNotifier.restoreActiveCall() |
| DRIVER_ASSIGNED | 기사 정보 팝업 + 진동 + 알림음 |
| RIDE_COMPLETED | 포인트 적립 팝업 (CF가 실제 적립) |
| CALL_CANCELLED | 팝업 닫기 + 포인트 환불 반영 |
| call_status_update | ACCEPTED/IN_PROGRESS 상태 갱신 |

### 데이터 모델 (6개, Kotlin Freezed로 1:1)
- CustomerCall (18 fields)
- CustomerInfo (12 fields)
- CustomerPoints (8 fields)
- CustomerGrade (enum: BRONZE/SILVER/GOLD/VIP)
- PointTransaction (9 fields)
- BannerAdData (10 fields, R2)

### Firestore 경로 (`ios/SHARED_LOGIC.md` §1 참조)
- 쓰기: `calls/`, `customers/`, `customerInfo/`, `customerPoints/`
- 읽기: `calls/`, `designated_drivers/`, `customerPoints/`, `customerInfo/`
- 리스너: `customerPoints/{phone}` (실시간)

### 수동 사무실 코드 입력 (Phase 1 임시 — Phase 2 App Clip으로 대체)
- 첫 실행 시 "사무실 코드 입력" 화면
- 코드 형식: `{p}-{c}-{o}` (예: `seoul-gangnam-office1`) 또는 단축 코드
- Firestore 검색 → 사무실 정보 SharedPreferences 저장
- **이탈률 측정** (R1_ATTRIBUTION_ACCURACY 발동 기준)

---

## MainViewModel 995 LOC 분해 (Riverpod 4 Notifier)

| Notifier | 책임 |
|----------|------|
| **CallNotifier** | requestCall, cancelCall, monitorCallStatus, restoreActiveCall, FCM 콜 관련 4종 (CALL_RECEIVED, DRIVER_ASSIGNED, RIDE_COMPLETED, CALL_CANCELLED, call_status_update) |
| **PointNotifier** | observeCustomerPoints, usePoints, refundPoints, getPointHistory |
| **ProfileNotifier** | 프로필 CRUD, 사무실 정보, 약관 동의 |
| **AuthNotifier** | Phone Auth, 로그인/로그아웃, FCM 토큰 등록, 자동 로그인 |

### 교차 의존 3지점 이관 규칙 (kotlin-expert 검증 결과 반영)

1. **콜 취소 → 포인트 재조회**: CallNotifier가 `ref.read(pointNotifierProvider.notifier).refresh()` 직접 호출
2. **운행 완료 → 포인트 적립 팝업**: CallNotifier 핸들러에서 PointNotifier 갱신 + UiState 통한 팝업 트리거 (Dialog 표시는 UI 레이어)
3. **콜 요청 → 포인트 사용 → 실패 시 환불**: CallNotifier.requestCall() 내부에서 PointNotifier.usePoints() + try/catch 환불 (트랜잭션 단위 보장)

원칙: **공통 이벤트 버스 도입 금지** (Riverpod 패턴 위반). 직접 호출(ref.read) + autoDispose로 lifecycle 관리.

---

## Phase 1 범위 (제외 — Phase 2 또는 R1/R2)

### Phase 2 (Mac 도착 후)
- ❌ **App Clip Swift 타겟** — QR → 즉시 사무실 연결 (수동 입력 대체)
- ❌ **AASA 호스팅** — Firebase Hosting `apple-app-site-association`
- ❌ **Custom Token 이관** — Anonymous Auth Custom Token으로 풀앱 재인증

### R1 (운영 데이터 발동)
- R1_ATTRIBUTION_ACCURACY: 수동 입력 이탈률 30% 이상 → Phase 2 App Clip 우선 도입
- R1_PUSH_RELIABILITY: iOS FCM 수신률 90% 미만 → CF apns 블록 보강

### R2 (비즈니스 판단)
- R2_VOICE_INPUT: 음성 입력 (Kotlin은 옵션, Flutter는 `speech_to_text`)
- R2_BANNER_AD: 배너 광고 (CF + BannerAdService)

---

## Phase 2 App Clip Swift 타겟 (Mac 도착 후 착수)

### 개요
- iOS 손님앱 Phase 1(Flutter)에 **Swift App Clip 타겟만 추가** (~500줄)
- 기존 `ios/customer_app/PLAN.md` App Clip 설계 그대로 재활용

### App Clip 플로우
```
1. 기사가 고객에게 QR 보여줌
2. 고객 iPhone 카메라로 스캔
3. App Clip 카드 표시 → "열기"
4. App Clip 즉시 실행 (URL 파라미터 자동 추출)
5. 사무실 자동 연결 → 콜 요청
6. 풀앱 설치 유도 배너
7. 풀앱 설치 시 App Group으로 데이터 공유
```

### App Group 데이터 공유
- App Group ID: `group.com.designated.customer`
- 공유 키: provinceId, cityId, officeId, authUid, customToken, phoneNumber, driverId

### Firebase Custom Token 이관
1. App Clip에서 Anonymous Auth → UID 획득
2. Cloud Function `generateCustomToken(uid)` 호출
3. App Group UserDefaults에 Custom Token 저장
4. 풀앱(Flutter) 첫 실행 → App Group에서 토큰 읽기 → `signInWithCustomToken`

### AASA 호스팅
`https://calldetector-5d61e.web.app/.well-known/apple-app-site-association`:
```json
{
  "appclips": {
    "apps": ["TEAMID.com.designated.customer.ios.Clip"]
  }
}
```

---

## 기존 Kotlin 앱 교체 전략 (의제 10 결정)

선택지:
- (A) **즉시 교체** + Play Console forceUpdate
- (B) **점진적 교체** (Closed Testing → Open Testing → Production)
- (C) **병행 운영 1~2주** 후 Kotlin 앱 deprecation 공지

기존 사용자(고객) 데이터:
- Firestore 데이터 그대로 유지 (앱 교체 무관)
- FCM 토큰: Flutter 첫 실행 시 onTokenRefresh 발동, customerInfo/{phone}에 자동 저장
- 자동 로그인: Phone Auth credential 재인증 강제 (Kotlin EncryptedSharedPreferences 호환 불가)

---

## 기술 스택

- **언어**: Dart 3.x
- **Flutter SDK**: 3.41+ (driver_app_flutter와 동일)
- **상태 관리**: Riverpod 2.x StateNotifier (4 Notifier 분해)
- **데이터 모델**: Freezed
- **라우팅**: GoRouter
- **Firebase SDK**: cloud_firestore, firebase_auth (Phone Auth), firebase_messaging, cloud_functions
- **로컬 저장**: shared_preferences + flutter_secure_storage
- **알림**: flutter_local_notifications (Time Sensitive)
- **위치**: geolocator + geocoding
- **음성 입력 (R2)**: speech_to_text
- **iOS 최소 버전**: 의제 1 결정 (사전 권장 iOS 15)
- **App Clip (Phase 2)**: Swift 5.10+, SwiftUI, AppKit
- **CI/CD**: Codemagic

---

## 패키지 ID

- **Android 현재 (Kotlin)**: `com.designated.customer.app`
- **iOS Phase 1 (Flutter)**: `com.designated.customer.ios` (가칭, 의제 2 통합 결정)
- **iOS Phase 2 (App Clip)**: `com.designated.customer.ios.Clip`
- **App Group**: `group.com.designated.customer`

---

## 매핑 문서 구성 (MVP/ 아래)

팀 의제 결정 후 작성:

| 파일 | 내용 |
|------|------|
| `OVERVIEW.md` | 본 문서의 결정 정리 |
| `MODELS.md` | 6개 Freezed 모델 + JSON serialization |
| `NOTIFIERS.md` | 4 Riverpod Notifier 분해 + 교차 의존 규칙 |
| `SCREENS.md` | 11개 Flutter 위젯 명세 |
| `FCM.md` | 5종 핸들링 + 백그라운드 isolate |
| `AUTH.md` | Phone Auth flow + 자격증명 마이그레이션 |
| `ATTRIBUTION.md` | Phase 1 수동 입력 + Phase 2 App Clip 설계 |
| `PHASE2_APPCLIP.md` | Swift App Clip 코드 구조 (Mac 도착 후) |
| `BUILD.md` | codemagic.yaml + Info.plist + Privacy Manifest + AASA |

---

## 참조

- 마스터 플랜: `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md`
- `flutter/WORKING_DOC.md` — 의제 14개
- `flutter/FUNCTIONAL_INVENTORY.md` Part B — 손님앱 분류
- `ios/SHARED_LOGIC.md` — Firestore·상태·정산
- `ios/customer_app/PLAN.md` — Phase 2 App Clip 원본 설계 (재활용)
- `customer_app/app/src/main/java/com/designated/customer/` — Kotlin 원본

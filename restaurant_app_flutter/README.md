# restaurant_app_flutter

콜마당 식당앱 (Flutter) — 식당이 콜·택시 호출 + 포인트 적립/결제로 사무실 wallet 시스템에 직접 연결.

플랜: `C:\Users\kala1\.claude\plans\abundant-floating-island.md` (P0)

## 현재 상태 (2026-05-05)

- 코드 작업 완료 — PR 2 commits `8bf592b8`~`1e15a678` (5 commits, push 완료)
- 빌드·Firebase 등록·통합 검증은 5/6 사업자 계좌 일정 후

## 5/6 후 일괄 처리

```bash
# 1. freezed/json_serializable 코드 생성
flutter pub get
flutter pub run build_runner build --delete-conflicting-outputs

# 2. Android: applicationId/namespace 변경 (build.gradle.kts)
#    + Kotlin source 디렉토리/package 변경
#    com.designated.customer.app → com.designated.restaurantapp.app

# 3. iOS: Bundle ID 변경 (project.pbxproj 6곳)
#    com.designated.customer.app → com.designated.restaurantapp.app

# 4. Firebase Console에서 식당앱 신규 등록 후 다운로드
#    android/app/google-services.json 교체
#    ios/Runner/GoogleService-Info.plist 교체

# 5. 빌드 + 설치 (Z Flip4)
flutter build apk --release
adb -s R3CT80K78NP install build/app/outputs/flutter-apk/app-release.apk
```

## 구조

```
lib/
├── main.dart                                — RestaurantApp + NotificationService.attach
├── core/
│   ├── domain/converters/timestamp_converter.dart
│   ├── providers.dart                       — Firebase + cloud_functions(asia-northeast3)
│   └── routing/app_router.dart              — navigatorKey + redirect(ids null → /signup)
└── features/
    ├── auth/                                — anonymous + FCM 토큰 → restaurants/{rid}
    └── restaurant/
        ├── domain/entities/                 — RestaurantInfo, RestaurantTransaction, RestaurantIds
        ├── data/restaurant_repository.dart  — callable CF + Firestore stream
        └── presentation/
            ├── state/                       — signup, call ui states
            ├── notifiers/                   — signup, call, ids, streams (4)
            ├── screens/                     — signup, home, simple/app/taxi call, points
            │   └── widgets/payment_method_picker.dart
            └── services/notification_service.dart
```

## callable CF 의존성 (region asia-northeast3)

| CF | 호출 위치 | 입력 | 결과 |
|---|---|---|---|
| `redeemRestaurantInviteCode` | SignupNotifier.submit | code + 사무실 ID + 식당 정보 + fcmToken | restaurantId |
| `createSharedCallFromRestaurant` | CallNotifier.submit | restaurantId + 사무실 ID + departure?/destination? + paymentMethod | sharedCallId |

## FCM 메시지

- `RESTAURANT_CALL_CLAIMED` — 사무실이 콜 잡음 → dialog (사무실명·전화번호 + 통화 버튼)
- `RESTAURANT_NO_RESPONSE` — 5분 미응답 → 스낵바

## 라우팅

| 경로 | 화면 | 진입 |
|---|---|---|
| `/signup` | SignupScreen | ids null 시 자동 redirect |
| `/` | HomeScreen | 가입 완료 후 메인 |
| `/call/simple` | SimpleCallScreen | 식당 주소 자동 + 결제 라디오 |
| `/call/app` | AppCallScreen | 출발·목적지 입력 + 결제 라디오 |
| `/call/taxi` | TaxiCallScreen | 단골 + 양평 4개 placeholder, url_launcher tel: |
| `/points` | PointsScreen | 잔액 + 거래 내역 |

## 결제 방식

- **CASH** (default) — 사무실 wallet -fare×10% + 식당 +1,000P
- **RESTAURANT_POINT** — 식당 -fare + 사무실 wallet +fare + 식당 +1,000P (마이너스 허용)

## Firestore 데이터 (식당 측)

- `provinces/{p}/cities/{c}/offices/{registeredOfficeId}/restaurants/{restaurantId}` — 식당 정보 + 잔액
- `restaurants/{rid}/transactions/{txId}` — 거래 내역 (멱등성 키 `call_${sharedCallId}`)

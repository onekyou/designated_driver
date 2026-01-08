# 📁 Flutter 프로젝트 구조 설계

**작성일**: 2025-01-17
**프로젝트명**: designated_customer_flutter

---

## 🎯 프로젝트 개요

- **이름**: designated_customer_flutter
- **패키지명**: com.designated.customer
- **설명**: 대리운전 고객 앱 (Android + iOS)
- **아키텍처**: Clean Architecture + Riverpod

---

## 📂 전체 디렉토리 구조

```
designated_customer_flutter/
├── android/                    # Android 네이티브 설정
├── ios/                        # iOS 네이티브 설정
├── lib/                        # Flutter 소스 코드
│   ├── main.dart              # 앱 진입점
│   │
│   ├── core/                  # 핵심 공통 모듈
│   │   ├── constants/         # 상수 정의
│   │   │   ├── app_constants.dart
│   │   │   ├── firebase_constants.dart
│   │   │   ├── point_constants.dart
│   │   │   └── routes.dart
│   │   │
│   │   ├── theme/             # 앱 테마
│   │   │   ├── app_theme.dart
│   │   │   ├── app_colors.dart
│   │   │   └── text_styles.dart
│   │   │
│   │   ├── utils/             # 유틸리티
│   │   │   ├── device_fingerprint.dart
│   │   │   ├── validators.dart
│   │   │   ├── formatters.dart
│   │   │   └── logger.dart
│   │   │
│   │   ├── services/          # 공통 서비스
│   │   │   ├── storage_service.dart       # 보안 저장소
│   │   │   ├── analytics_service.dart     # 분석
│   │   │   └── error_handler.dart         # 에러 처리
│   │   │
│   │   └── widgets/           # 공통 위젯
│   │       ├── custom_button.dart
│   │       ├── custom_text_field.dart
│   │       ├── loading_indicator.dart
│   │       └── error_widget.dart
│   │
│   ├── features/              # 기능별 모듈
│   │   │
│   │   ├── auth/              # 인증 기능
│   │   │   ├── models/
│   │   │   │   └── user_model.dart
│   │   │   ├── providers/
│   │   │   │   └── auth_provider.dart
│   │   │   ├── repositories/
│   │   │   │   └── auth_repository.dart
│   │   │   ├── services/
│   │   │   │   └── firebase_auth_service.dart
│   │   │   └── screens/
│   │   │       ├── anonymous_auth_screen.dart
│   │   │       └── phone_auth_screen.dart
│   │   │
│   │   ├── attribution/       # Attribution 시스템
│   │   │   ├── models/
│   │   │   │   └── attribution_result.dart
│   │   │   ├── providers/
│   │   │   │   └── attribution_provider.dart
│   │   │   ├── repositories/
│   │   │   │   └── attribution_repository.dart
│   │   │   └── services/
│   │   │       └── attribution_matching_service.dart
│   │   │
│   │   ├── call/              # 콜 요청 기능
│   │   │   ├── models/
│   │   │   │   ├── customer_call.dart
│   │   │   │   └── call_status.dart
│   │   │   ├── providers/
│   │   │   │   ├── call_provider.dart
│   │   │   │   └── call_list_provider.dart
│   │   │   ├── repositories/
│   │   │   │   └── call_repository.dart
│   │   │   ├── services/
│   │   │   │   ├── call_service.dart
│   │   │   │   └── voice_input_service.dart
│   │   │   └── screens/
│   │   │       ├── call_request_screen.dart
│   │   │       ├── call_history_screen.dart
│   │   │       └── widgets/
│   │   │           ├── call_card.dart
│   │   │           └── voice_input_button.dart
│   │   │
│   │   ├── points/            # 포인트 시스템
│   │   │   ├── models/
│   │   │   │   ├── customer_points.dart
│   │   │   │   ├── point_transaction.dart
│   │   │   │   └── customer_grade.dart
│   │   │   ├── providers/
│   │   │   │   ├── points_provider.dart
│   │   │   │   └── transactions_provider.dart
│   │   │   ├── repositories/
│   │   │   │   └── point_repository.dart
│   │   │   ├── services/
│   │   │   │   └── point_service.dart
│   │   │   └── screens/
│   │   │       ├── point_screen.dart
│   │   │       ├── point_history_screen.dart
│   │   │       └── widgets/
│   │   │           ├── point_card.dart
│   │   │           └── grade_badge.dart
│   │   │
│   │   ├── steps/             # 만보기 기능
│   │   │   ├── models/
│   │   │   │   ├── step_data.dart
│   │   │   │   ├── daily_step_data.dart
│   │   │   │   ├── weekly_step_summary.dart
│   │   │   │   └── monthly_step_summary.dart
│   │   │   ├── providers/
│   │   │   │   └── step_counter_provider.dart
│   │   │   ├── repositories/
│   │   │   │   └── step_repository.dart
│   │   │   ├── services/
│   │   │   │   ├── step_counter_service.dart
│   │   │   │   └── activity_recognition_service.dart
│   │   │   └── screens/
│   │   │       ├── step_counter_screen.dart
│   │   │       └── widgets/
│   │   │           ├── step_counter_card.dart
│   │   │           └── step_chart.dart
│   │   │
│   │   ├── profile/           # 프로필 관리
│   │   │   ├── models/
│   │   │   │   └── customer_info.dart
│   │   │   ├── providers/
│   │   │   │   └── profile_provider.dart
│   │   │   ├── repositories/
│   │   │   │   └── profile_repository.dart
│   │   │   └── screens/
│   │   │       ├── profile_screen.dart
│   │   │       └── profile_setup_screen.dart
│   │   │
│   │   ├── games/             # 게임 기능
│   │   │   ├── screens/
│   │   │   │   ├── game_menu_screen.dart
│   │   │   │   ├── ladder_game_screen.dart
│   │   │   │   └── bill_payment_game_screen.dart
│   │   │   └── widgets/
│   │   │       └── ladder_painter.dart
│   │   │
│   │   └── home/              # 홈 화면
│   │       ├── providers/
│   │       │   └── home_provider.dart
│   │       └── screens/
│   │           ├── home_screen.dart
│   │           └── main_navigation.dart
│   │
│   └── data/                  # 데이터 레이어
│       ├── models/            # 전역 데이터 모델
│       ├── repositories/      # Repository 인터페이스
│       └── providers/         # Riverpod Provider 정의
│
├── test/                      # 테스트 코드
│   ├── unit/
│   ├── widget/
│   └── integration/
│
├── assets/                    # 리소스 파일
│   ├── images/
│   ├── icons/
│   └── fonts/
│
├── pubspec.yaml              # 패키지 설정
├── analysis_options.yaml     # Lint 설정
└── README.md                 # 프로젝트 설명

```

---

## 🎨 아키텍처 설명

### Clean Architecture + Feature-First

```
┌─────────────────────────────────────────────┐
│              Presentation Layer              │
│  (Screens, Widgets, Providers)              │
└──────────────┬──────────────────────────────┘
               │
┌──────────────┴──────────────────────────────┐
│           Business Logic Layer               │
│  (Repositories, Services, Use Cases)        │
└──────────────┬──────────────────────────────┘
               │
┌──────────────┴──────────────────────────────┐
│              Data Layer                      │
│  (Models, Firebase, Local Storage)          │
└─────────────────────────────────────────────┘
```

### Feature 단위 구조

각 기능(auth, call, points 등)은 독립적으로:
- **Models**: 데이터 구조
- **Providers**: 상태 관리 (Riverpod)
- **Repositories**: 데이터 소스 추상화
- **Services**: 비즈니스 로직
- **Screens**: UI

---

## 🔧 핵심 모듈 설명

### 1. **core/constants**
모든 하드코딩 제거, 상수 관리
```dart
// app_constants.dart
class AppConstants {
  static const String appName = 'Designated Customer';
  static const int tokenExpiryHours = 24;
}
```

### 2. **core/services/storage_service.dart**
보안 저장소 (flutter_secure_storage)
```dart
// 전화번호, 토큰 등 민감정보 암호화 저장
await storageService.savePhoneNumber(phoneNumber);
```

### 3. **features/[기능]/repositories**
Firebase, 로컬 DB 추상화
```dart
// call_repository.dart
abstract class CallRepository {
  Future<String> requestCall(CustomerCall call);
  Stream<List<CustomerCall>> observeCalls(String phoneNumber);
}
```

### 4. **features/[기능]/providers**
Riverpod을 사용한 상태 관리
```dart
// call_provider.dart
final callProvider = StateNotifierProvider<CallNotifier, CallState>((ref) {
  return CallNotifier(ref.read(callRepositoryProvider));
});
```

---

## 📦 패키지 의존성 (미리보기)

```yaml
dependencies:
  # Flutter 기본
  flutter:
    sdk: flutter

  # 상태 관리
  flutter_riverpod: ^2.4.9

  # Firebase
  firebase_core: ^2.24.0
  firebase_auth: ^4.15.0
  firebase_firestore: ^4.13.0
  firebase_functions: ^4.5.0
  firebase_messaging: ^14.7.0

  # 보안
  flutter_secure_storage: ^9.0.0
  crypto: ^3.0.3

  # 로컬 DB
  hive: ^2.2.3
  hive_flutter: ^1.1.0

  # 디바이스 정보
  device_info_plus: ^9.1.1

  # 네비게이션
  go_router: ^12.1.3

  # QR 스캔
  mobile_scanner: ^3.5.5

  # 만보기
  pedometer: ^4.0.1           # Android
  health: ^10.0.0             # iOS HealthKit

  # 음성 인식
  speech_to_text: ^6.5.1

  # 위치
  geolocator: ^10.1.0

  # UI
  intl: ^0.18.1
```

---

## 🔐 보안 설계

### 1. **민감정보 암호화 저장**
```dart
// flutter_secure_storage 사용
await storage.write(key: 'phoneNumber', value: phone);
```

### 2. **Device Fingerprint 해시 처리**
```dart
// SHA-256 해시 처리
final hashedId = sha256.convert(utf8.encode(androidId)).toString();
```

### 3. **FCM 토큰 검증**
```dart
// Cloud Function에서 토큰 소유권 검증
if (!await verifyTokenOwnership(phoneNumber, token)) {
  throw UnauthorizedException();
}
```

---

## 🎯 명명 규칙

### 파일명
- **snake_case**: `customer_call.dart`
- **Model**: `customer_call.dart` (데이터 클래스)
- **Screen**: `call_request_screen.dart` (화면)
- **Provider**: `call_provider.dart` (상태 관리)
- **Repository**: `call_repository.dart` (데이터 레이어)
- **Service**: `call_service.dart` (비즈니스 로직)

### 클래스명
- **PascalCase**: `CustomerCall`, `CallRequestScreen`
- **Private**: `_PrivateClass`, `_privateMethod`

### 변수명
- **camelCase**: `phoneNumber`, `currentLocation`
- **Private**: `_privateVariable`
- **Constant**: `kDefaultPadding` (k prefix for constants)

---

## ✅ 다음 단계

Flutter 설치 완료 후:

1. **프로젝트 생성**
   ```bash
   flutter create designated_customer_flutter --org com.designated
   ```

2. **디렉토리 생성**
   ```bash
   mkdir -p lib/core/{constants,theme,utils,services,widgets}
   mkdir -p lib/features/{auth,attribution,call,points,steps,profile,games,home}
   ```

3. **pubspec.yaml 설정**
   - 필수 패키지 추가

4. **Firebase 설정**
   - Android: google-services.json
   - iOS: GoogleService-Info.plist

---

**작성자**: Claude
**최종 수정일**: 2025-01-17

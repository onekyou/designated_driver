# 기사앱 Clean Architecture 구조

## 📁 폴더 구조

```
lib/
├── core/                           # 공통 기능
│   ├── constants/                  # 상수
│   │   ├── app_constants.dart
│   │   └── firebase_constants.dart
│   ├── error/                      # 에러 처리
│   │   ├── failures.dart           # Failure 클래스들
│   │   └── exceptions.dart         # Exception 클래스들
│   ├── network/                    # 네트워크
│   │   └── network_info.dart       # 온라인/오프라인 체크
│   ├── usecases/                   # UseCase 베이스
│   │   └── usecase.dart
│   └── utils/                      # 유틸리티
│       ├── logger.dart
│       └── extensions.dart
│
├── features/                       # 기능별 모듈
│   │
│   ├── auth/                       # 인증
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   └── user_session.dart
│   │   │   ├── repositories/
│   │   │   │   └── auth_repository.dart
│   │   │   └── usecases/
│   │   │       ├── login_usecase.dart
│   │   │       ├── logout_usecase.dart
│   │   │       └── auto_login_usecase.dart
│   │   ├── data/
│   │   │   ├── models/
│   │   │   │   └── user_session_model.dart
│   │   │   ├── repositories/
│   │   │   │   └── auth_repository_impl.dart
│   │   │   └── datasources/
│   │   │       ├── auth_local_datasource.dart
│   │   │       └── auth_remote_datasource.dart
│   │   └── presentation/
│   │       ├── providers/
│   │       │   └── auth_provider.dart
│   │       ├── screens/
│   │       │   ├── login_screen.dart
│   │       │   └── signup_screen.dart
│   │       └── widgets/
│   │           └── login_form.dart
│   │
│   ├── call/                       # 콜 관리
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   └── call.dart
│   │   │   ├── repositories/
│   │   │   │   └── call_repository.dart
│   │   │   └── usecases/
│   │   │       ├── get_assigned_call_usecase.dart
│   │   │       ├── accept_call_usecase.dart
│   │   │       ├── reject_call_usecase.dart
│   │   │       └── complete_call_usecase.dart
│   │   ├── data/
│   │   │   ├── models/
│   │   │   │   └── call_model.dart
│   │   │   ├── repositories/
│   │   │   │   └── call_repository_impl.dart
│   │   │   └── datasources/
│   │   │       ├── call_local_datasource.dart
│   │   │       └── call_remote_datasource.dart
│   │   └── presentation/
│   │       ├── providers/
│   │       │   └── call_provider.dart
│   │       ├── screens/
│   │       │   ├── call_list_screen.dart
│   │       │   └── call_detail_screen.dart
│   │       └── widgets/
│   │           ├── call_card.dart
│   │           └── new_call_popup.dart
│   │
│   ├── trip/                       # 운행 관리
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   └── trip.dart
│   │   │   ├── repositories/
│   │   │   │   └── trip_repository.dart
│   │   │   └── usecases/
│   │   │       ├── start_trip_usecase.dart
│   │   │       ├── complete_trip_usecase.dart
│   │   │       └── get_trip_history_usecase.dart
│   │   ├── data/
│   │   │   ├── models/
│   │   │   │   └── trip_model.dart
│   │   │   ├── repositories/
│   │   │   │   └── trip_repository_impl.dart
│   │   │   └── datasources/
│   │   │       ├── trip_local_datasource.dart
│   │   │       └── trip_remote_datasource.dart
│   │   └── presentation/
│   │       ├── providers/
│   │       │   └── trip_provider.dart
│   │       ├── screens/
│   │       │   ├── trip_preparation_screen.dart
│   │       │   ├── trip_in_progress_screen.dart
│   │       │   └── trip_history_screen.dart
│   │       └── widgets/
│   │           └── trip_card.dart
│   │
│   ├── driver/                     # 기사 상태 관리
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   └── driver.dart
│   │   │   ├── repositories/
│   │   │   │   └── driver_repository.dart
│   │   │   └── usecases/
│   │   │       ├── update_driver_status_usecase.dart
│   │   │       └── get_driver_info_usecase.dart
│   │   ├── data/
│   │   │   ├── models/
│   │   │   │   └── driver_model.dart
│   │   │   ├── repositories/
│   │   │   │   └── driver_repository_impl.dart
│   │   │   └── datasources/
│   │   │       ├── driver_local_datasource.dart
│   │   │       └── driver_remote_datasource.dart
│   │   └── presentation/
│   │       ├── providers/
│   │       │   └── driver_provider.dart
│   │       └── screens/
│   │           └── driver_status_screen.dart
│   │
│   ├── customer/                   # 고객 포인트 관리
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   └── customer_points.dart
│   │   │   ├── repositories/
│   │   │   │   └── customer_repository.dart
│   │   │   └── usecases/
│   │   │       ├── get_customer_points_usecase.dart
│   │   │       └── use_points_usecase.dart
│   │   ├── data/
│   │   │   ├── models/
│   │   │   │   └── customer_points_model.dart
│   │   │   ├── repositories/
│   │   │   │   └── customer_repository_impl.dart
│   │   │   └── datasources/
│   │   │       ├── customer_local_datasource.dart
│   │   │       └── customer_remote_datasource.dart
│   │   └── presentation/
│   │       └── widgets/
│   │           └── customer_points_widget.dart
│   │
│   └── location/                   # 위치 서비스
│       ├── domain/
│       │   ├── entities/
│       │   │   └── location.dart
│       │   ├── repositories/
│       │   │   └── location_repository.dart
│       │   └── usecases/
│       │       ├── get_current_location_usecase.dart
│       │       └── geocode_address_usecase.dart
│       ├── data/
│       │   ├── models/
│       │   │   └── location_model.dart
│       │   ├── repositories/
│       │   │   └── location_repository_impl.dart
│       │   └── datasources/
│       │       └── location_datasource.dart
│       └── presentation/
│           └── widgets/
│               └── location_picker.dart
│
├── injection.dart                  # 의존성 주입 설정
└── main.dart                       # 앱 진입점
```

## 🎯 각 레이어 역할

### 1. Domain Layer (가장 안쪽 - 순수 Dart)
- **Entities**: 비즈니스 로직의 핵심 데이터 (Firebase, UI 독립적)
- **Repositories**: 인터페이스 (추상 클래스)
- **UseCases**: 비즈니스 로직 (하나의 기능 = 하나의 UseCase)

### 2. Data Layer (중간 - 데이터 가져오기)
- **Models**: Entity ↔ JSON 변환
- **Repositories**: Repository 인터페이스 구현
- **DataSources**:
  - Remote: Firebase, REST API
  - Local: Hive, SharedPreferences

### 3. Presentation Layer (가장 바깥 - UI)
- **Providers**: Riverpod (상태 관리)
- **Screens**: 전체 화면
- **Widgets**: 재사용 가능한 위젯

## 🔄 데이터 흐름

```
UI (Screen/Widget)
    ↓
Provider (Riverpod)
    ↓
UseCase (비즈니스 로직)
    ↓
Repository Interface
    ↓
Repository Implementation
    ↓
DataSource (Firebase / Hive)
```

## 🛡️ 에러 처리

```dart
// Either<Failure, Success>
final result = await loginUseCase.execute(email, password);

result.fold(
  (failure) => // 에러 처리
  (success) => // 성공 처리
);
```

## 🧪 테스트 가능성

각 레이어가 독립적이므로:
- Domain Layer: 100% 순수 Dart → 쉽게 테스트
- Data Layer: Mock DataSource로 테스트
- Presentation Layer: Mock UseCase로 테스트

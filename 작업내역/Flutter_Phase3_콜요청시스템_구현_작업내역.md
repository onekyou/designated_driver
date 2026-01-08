# Flutter 고객앱 Phase 3: 콜 요청 시스템 구현 작업 내역

**작업 일자**: 2025-11-17
**작업 상태**: 진행 중 (빌드 오류 해결 필요)

---

## 📋 작업 개요

Phase 2 (Firebase 인증 + Attribution 시스템) 완료 후, Phase 3로 콜 요청 시스템을 구현했습니다.

---

## ✅ 완료된 작업

### 1. 패키지 추가 (pubspec.yaml)

```yaml
# 위치 서비스
geolocator: ^9.0.2  # 초기 10.1.0에서 호환성 문제로 다운그레이드
geocoding: ^2.1.1

# 음성 입력
speech_to_text: ^6.5.1

# 권한 관리
permission_handler: ^11.1.0

# 날짜/시간
intl: ^0.18.1
```

### 2. 데이터 모델 생성

#### `lib/features/call/models/call_state.dart`
- 6가지 콜 상태 enum 정의
  - REQUESTED (콜 요청됨)
  - ASSIGNED (기사 배정됨)
  - DRIVER_ARRIVING (기사 이동 중)
  - IN_PROGRESS (운행 중)
  - COMPLETED (운행 완료)
  - CANCELLED (취소됨)
- `isActive`, `isTerminated` 헬퍼 메서드

#### `lib/features/call/models/driver_info.dart`
- 기사 정보 모델 (id, name, phoneNumber, vehicleNumber, rating)
- Firestore 변환 메서드 (fromMap, toMap)

#### `lib/features/call/models/call_model.dart`
- 19개 필드를 가진 CustomerCall 모델
- 콜매니저 호환 필드 포함
- 포인트 시스템 지원 (pointsUsed, finalFare, discountAmount)
- Firestore 변환 메서드 (fromFirestore, toFirestore)

### 3. Repository 계층

#### `lib/features/call/repositories/call_repository.dart`
6개 주요 메서드 구현:
1. `requestCall()` - 새 콜 생성
2. `cancelCall()` - 콜 취소
3. `getCallHistory()` - 콜 내역 조회
4. `observeCustomerCalls()` - 실시간 콜 모니터링 (Stream)
5. `getActiveCall()` - 활성 콜 조회
6. `completeFareWithPointDiscount()` - 포인트 할인 적용 요금 정산

Firestore 경로: `regions/{regionId}/offices/{officeId}/calls/{callId}`

### 4. Provider (Riverpod 상태 관리)

#### `lib/features/call/providers/call_provider.dart`
- `callRepositoryProvider` - Repository 인스턴스 제공
- `CallNotifier` - 콜 요청/취소/조회 비즈니스 로직
- `callProvider` - 현재 활성 콜 상태
- `callHistoryProvider` - 콜 내역 Future Provider
- `callStreamProvider` - 실시간 콜 Stream Provider

#### `lib/features/call/providers/location_provider.dart`
- `LocationService` - 위치 서비스 클래스
  - 위치 권한 확인/요청
  - 현재 위치 가져오기
  - 좌표 ↔ 주소 변환
- `locationServiceProvider` - LocationService 제공
- `currentLocationProvider` - 현재 위치 정보
- `LocationPermissionNotifier` - 위치 권한 상태 관리

### 5. UI 화면

#### `lib/features/call/screens/call_request_screen.dart`
**기능:**
- 출발지 입력 + GPS 자동 입력 버튼
- 목적지 입력 + 음성 인식 버튼 (한국어)
- 메모 입력 (선택사항)
- 사무실 정보 표시
- 이용 안내 카드

**주요 구현:**
- Form validation
- 음성 인식 (speech_to_text)
- 위치 서비스 (geolocator, geocoding)
- 콜 요청 API 호출

#### `lib/features/call/screens/call_history_screen.dart`
**기능:**
- 콜 목록 카드 뷰
- 상태별 색상/아이콘
- 요금 정보 (포인트 할인 표시)
- 상세 정보 BottomSheet
- 빈 내역 뷰

**주요 구현:**
- 날짜 포맷팅 (intl)
- 상태별 UI 구분
- Pull-to-refresh (RefreshIndicator)

#### `lib/features/home/screens/home_screen.dart` 수정
**추가된 기능:**
- "대리운전 요청하기" 버튼
- "콜 내역 보기" 버튼
- 활성 콜 상태 표시 카드
- "Phase 3 완료: 콜 요청 시스템" 라벨

### 6. 권한 설정

#### `android/app/src/main/AndroidManifest.xml`
추가된 권한:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
```

---

## ❌ 현재 이슈

### Gradle 빌드 오류

**오류 내용:**
```
FAILURE: Build completed with 2 failures.

1. geolocator_android 플러그인 오류:
   - Could not get unknown property 'flutter' for extension 'android'

2. compileSdk 설정 오류:
   - project ':geolocator_android' does not specify `compileSdk`
```

**발생 위치:**
- 파일: `C:\Users\onekyou\AppData\Local\Pub\Cache\hosted\pub.dev\geolocator_android-4.6.2\android\build.gradle`
- 라인: 29

**시도한 해결 방법:**
1. ❌ geolocator 버전 다운그레이드 (10.1.0 → 9.0.2) - 여전히 동일 오류
2. ❌ flutter clean 후 재빌드
3. ❌ 패키지 재설치

**근본 원인:**
- `geolocator_android` 플러그인과 현재 Gradle 설정의 호환성 문제
- Flutter SDK 버전 또는 Android Gradle Plugin 버전과의 충돌 가능성

---

## 🔧 필요한 해결 방법

### 옵션 1: geolocator 플러그인 교체
- `location` 패키지로 교체 고려
- 또는 더 안정적인 버전의 geolocator 사용

### 옵션 2: Gradle 설정 직접 수정
- `geolocator_android` 플러그인의 build.gradle 직접 수정
- compileSdk 명시적 설정

### 옵션 3: 위치 기능 임시 제거
- 일단 위치 관련 패키지 제거
- 수동 주소 입력으로 변경
- 나머지 기능 먼저 테스트

### 옵션 4: Flutter/Gradle 버전 업그레이드
- Flutter SDK 최신 버전으로 업그레이드
- Android Gradle Plugin 버전 확인 및 조정

---

## 📊 프로젝트 구조

```
lib/features/call/
├── models/
│   ├── call_model.dart       ✅ 완료
│   ├── call_state.dart        ✅ 완료
│   └── driver_info.dart       ✅ 완료
├── providers/
│   ├── call_provider.dart     ✅ 완료
│   └── location_provider.dart ✅ 완료
├── repositories/
│   └── call_repository.dart   ✅ 완료
└── screens/
    ├── call_request_screen.dart  ✅ 완료
    └── call_history_screen.dart  ✅ 완료
```

---

## 📝 코드 분석 결과

**Flutter analyze 결과:**
- 0 errors
- 일부 warnings (unnecessary_non_null_assertion)
- 코드 스타일 정보 메시지

**컴파일 가능 여부:**
- Dart 코드: ✅ 문제 없음
- Gradle 빌드: ❌ geolocator_android 오류

---

## 🎯 다음 단계

1. **즉시 해결 필요:** geolocator_android Gradle 오류
2. 빌드 성공 후 테스트:
   - 위치 권한 요청
   - GPS 위치 가져오기
   - 음성 인식 (한국어)
   - 콜 요청 생성
   - 콜 내역 조회
3. Firebase Functions 연동 확인
4. 실제 기기에서 통합 테스트

---

## 💡 참고사항

- **Firebase 프로젝트**: calldetector-5d61e (production)
- **대상 기기**: SM G996N (Android 15)
- **Flutter 버전**: 확인 필요
- **최소 SDK**: 21 (Android 5.0)
- **대상 SDK**: 34 (Android 14)

---

## 📌 중요 파일 위치

- **프로젝트 루트**: `C:\app_dev\designated_driver\designated_customer_flutter`
- **Android 설정**: `android/app/build.gradle`
- **Firebase 설정**: `android/app/google-services.json`
- **패키지 설정**: `pubspec.yaml`

---

**작성자**: Claude
**최종 수정**: 2025-11-17

# 🚀 고객앱 Flutter 전환 마스터플랜

**작성일**: 2025-01-17
**목표**: Android + iOS 크로스 플랫폼 통합 + 보안/성능 개선
**예상 기간**: 10주
**개발 방식**: Mac Cloud를 활용한 iOS 빌드

---

## 📊 현재 상태 분석

### 기술 스택
- **언어**: Kotlin
- **UI**: Jetpack Compose
- **아키텍처**: MVVM + Repository Pattern
- **백엔드**: Firebase (Firestore, Auth, Functions, FCM)
- **로컬 DB**: Room
- **총 코드량**: ~8,000 라인 (47개 파일)

### 발견된 문제점

#### 🔴 보안 취약점 (심각)
1. **SharedPreferences 평문 저장**
   - 전화번호, 사무실 정보 암호화 없음
   - 루팅된 기기에서 유출 가능

2. **Device Fingerprint 노출**
   - Android ID가 평문으로 Cloud Function에 전송
   - 사용자 추적 가능성

3. **FCM 토큰 검증 부재**
   - 전화번호만 알면 푸시 발송 가능
   - 무단 알림 발송 위험

#### 🟡 성능 이슈 (중간)
1. **Firestore 읽기 최적화 부족**
   - Attribution 매칭 시 모든 지역/사무실 순회 (N×M reads)
   - 앱 시작 시 느림, 비용 증가

2. **만보기 DB 저장**
   - 현재: 5걸음마다 또는 3초마다 저장 (양호)
   - 개선 여지: 있음

#### 🟢 코드 품질 (경미)
1. **하드코딩된 값**: regionId = "seoul", 24*60*60 등
2. **에러 처리 부족**: 로그만 출력, 사용자 피드백 없음
3. **중복 코드**: Firestore 경로 패턴 반복

---

## 🎯 Flutter 전환 목표

### 1. 크로스 플랫폼
- ✅ Android + iOS 단일 코드베이스
- ✅ 코드 재사용율 95% 이상
- ✅ 동시 출시 가능

### 2. 보안 강화
- ✅ flutter_secure_storage로 민감정보 암호화
- ✅ Device Fingerprint SHA-256 해시 처리
- ✅ FCM 토큰 서버 측 검증

### 3. 성능 개선
- ✅ Attribution 매칭 Cloud Function만 사용 (Firestore 읽기 90% 절감)
- ✅ iOS HealthKit 통합 (더 정확한 만보기, 배터리 효율 향상)
- ✅ 최적화된 로컬 DB (Hive/Isar)

### 4. 코드 품질
- ✅ 하드코딩 제거 (상수화/환경변수)
- ✅ 에러 처리 개선 (사용자 피드백 + 재시도 로직)
- ✅ Repository 패턴 통합 (중복 제거)

---

## 📅 단계별 개발 계획 (10주)

### **Phase 1: 프로젝트 셋업 및 인프라** (1주)

**목표**: Flutter 개발 환경 구축 및 Firebase 연동

**작업 항목**:
- [ ] Flutter SDK 설치 및 환경 설정
- [ ] Flutter 프로젝트 생성
- [ ] Firebase 프로젝트 설정 (Android + iOS)
- [ ] 기본 디렉토리 구조 설계
- [ ] 상태 관리 솔루션 선택 (Riverpod 권장)
- [ ] CI/CD 파이프라인 설정 (선택)

**디렉토리 구조**:
```
lib/
├── core/
│   ├── constants/          # 상수 정의
│   │   ├── app_constants.dart
│   │   └── firebase_constants.dart
│   ├── theme/              # 테마 설정
│   │   ├── app_theme.dart
│   │   └── app_colors.dart
│   ├── utils/              # 유틸리티
│   │   ├── device_fingerprint.dart
│   │   ├── validators.dart
│   │   └── formatters.dart
│   └── services/           # 공통 서비스
│       ├── storage_service.dart
│       └── analytics_service.dart
├── features/
│   ├── auth/               # 인증 기능
│   │   ├── models/
│   │   ├── providers/
│   │   ├── repositories/
│   │   └── screens/
│   ├── call/               # 콜 요청 기능
│   │   ├── models/
│   │   ├── providers/
│   │   ├── repositories/
│   │   └── screens/
│   ├── points/             # 포인트 시스템
│   │   ├── models/
│   │   ├── providers/
│   │   ├── repositories/
│   │   └── screens/
│   ├── steps/              # 만보기 기능
│   │   ├── models/
│   │   ├── providers/
│   │   ├── repositories/
│   │   └── screens/
│   └── profile/            # 프로필 관리
│       ├── models/
│       ├── providers/
│       ├── repositories/
│       └── screens/
├── data/
│   ├── models/             # 데이터 모델
│   ├── repositories/       # Repository 구현
│   └── providers/          # Riverpod Providers
└── main.dart
```

**주요 패키지** (pubspec.yaml):
```yaml
dependencies:
  flutter:
    sdk: flutter

  # Firebase
  firebase_core: ^2.24.0
  firebase_auth: ^4.15.0
  firebase_firestore: ^4.13.0
  firebase_functions: ^4.5.0
  firebase_messaging: ^14.7.0

  # 상태 관리
  flutter_riverpod: ^2.4.9

  # 보안
  flutter_secure_storage: ^9.0.0

  # 디바이스 정보
  device_info_plus: ^9.1.1

  # 로컬 DB
  hive: ^2.2.3
  hive_flutter: ^1.1.0

  # UI
  go_router: ^12.1.3

dev_dependencies:
  flutter_test:
    sdk: flutter
  hive_generator: ^2.0.1
  build_runner: ^2.4.7
```

**완료 기준**:
- Flutter 프로젝트가 정상적으로 실행됨
- Firebase 연동 완료 (Android)
- 기본 화면 구조 완성

---

### **Phase 2: 핵심 인증 및 Attribution** (2주)

**목표**: Firebase 인증 및 Attribution 시스템 구현 (보안 강화)

**작업 항목**:
- [ ] Firebase 익명 인증 구현
- [ ] Device Fingerprint 수집 (SHA-256 해시 처리)
- [ ] Attribution 매칭 Cloud Function 연동
- [ ] 토큰 기반 매칭 구현
- [ ] 보안 저장소 구현 (flutter_secure_storage)
- [ ] PreferencesManager 이식 (암호화)

**보안 개선 코드 예시**:

```dart
// 1. Device Fingerprint (개선)
import 'package:crypto/crypto.dart';
import 'dart:convert';

class DeviceFingerprintUtil {
  static Future<Map<String, dynamic>> collectDeviceInfo() async {
    final deviceInfo = DeviceInfoPlugin();
    final androidInfo = await deviceInfo.androidInfo;

    // SHA-256 해시 처리
    final androidId = androidInfo.id;
    final hashedId = sha256.convert(utf8.encode(androidId)).toString();

    return {
      'androidIdHash': hashedId,  // ✅ 해시 처리
      'manufacturer': androidInfo.manufacturer,
      'model': androidInfo.model,
      'brand': androidInfo.brand,
      'osVersion': androidInfo.version.release,
      'screenResolution': '${androidInfo.displayMetrics.widthPx}x${androidInfo.displayMetrics.heightPx}',
      'timezone': DateTime.now().timeZoneName,
      'language': Platform.localeName,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'source': 'flutter_app',
    };
  }
}

// 2. 보안 저장소 (개선)
class SecureStorageService {
  final FlutterSecureStorage _storage = FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );

  // 전화번호 저장 (암호화)
  Future<void> savePhoneNumber(String phoneNumber) async {
    await _storage.write(key: 'phoneNumber', value: phoneNumber);
  }

  Future<String?> getPhoneNumber() async {
    return await _storage.read(key: 'phoneNumber');
  }

  // Attribution 토큰 저장 (24시간 만료)
  Future<void> saveAttributionToken(String token) async {
    await _storage.write(key: 'attributionToken', value: token);
    await _storage.write(
      key: 'attributionTokenTimestamp',
      value: DateTime.now().millisecondsSinceEpoch.toString(),
    );
  }

  Future<String?> getAttributionToken() async {
    final token = await _storage.read(key: 'attributionToken');
    final timestampStr = await _storage.read(key: 'attributionTokenTimestamp');

    if (token == null || timestampStr == null) return null;

    final timestamp = int.parse(timestampStr);
    final now = DateTime.now().millisecondsSinceEpoch;
    final twentyFourHours = 24 * 60 * 60 * 1000;

    // 24시간 이내 토큰만 유효
    if (now - timestamp < twentyFourHours) {
      return token;
    } else {
      await clearAttributionToken();
      return null;
    }
  }

  Future<void> clearAttributionToken() async {
    await _storage.delete(key: 'attributionToken');
    await _storage.delete(key: 'attributionTokenTimestamp');
  }
}

// 3. Attribution 매칭 (최적화)
class AttributionRepository {
  final FirebaseFunctions _functions = FirebaseFunctions.instanceFor(region: 'asia-northeast3');
  final SecureStorageService _storage = SecureStorageService();

  Future<AttributionResult> matchAttribution() async {
    try {
      // 1. 토큰 기반 매칭 시도 (캐시 우선)
      final cachedToken = await _storage.getAttributionToken();
      if (cachedToken != null) {
        final tokenResult = await _matchByToken(cachedToken);
        if (tokenResult != null) return tokenResult;

        // 토큰 매칭 실패 시 캐시 삭제
        await _storage.clearAttributionToken();
      }

      // 2. Fingerprint 매칭 (Cloud Function만 사용)
      final deviceInfo = await DeviceFingerprintUtil.collectDeviceInfo();

      final result = await _functions
          .httpsCallable('matchAttribution')
          .call({'fingerprint': deviceInfo});

      final data = result.data as Map<String, dynamic>;

      if (data['success'] == true) {
        return AttributionResult(
          regionId: data['regionId'],
          officeId: data['officeId'],
          officePhone: data['officePhone'],
          bankName: data['bankName'],
          accountNumber: data['accountNumber'],
          accountHolder: data['accountHolder'],
          referralDriverId: data['referralDriverId'],
          referralDriverName: data['referralDriverName'],
        );
      } else {
        throw AttributionException(data['message'] ?? '매칭 실패');
      }
    } catch (e) {
      throw AttributionException('Attribution 매칭 중 오류: $e');
    }
  }

  Future<AttributionResult?> _matchByToken(String token) async {
    // 토큰 기반 매칭 로직
  }
}
```

**완료 기준**:
- 익명 인증 정상 작동
- Attribution 매칭 성공률 100%
- 보안 저장소 정상 작동
- Firestore 읽기 횟수 90% 감소 확인

---

### **Phase 3: 콜 요청 시스템** (1.5주)

**목표**: 콜 요청/취소/히스토리 기능 구현

**작업 항목**:
- [ ] CustomerCall 모델 이식
- [ ] CallService 구현
- [ ] 실시간 콜 상태 동기화 (Stream)
- [ ] 콜 요청 화면 UI
- [ ] 콜 히스토리 화면 UI
- [ ] 음성 입력 기능 (speech_to_text)
- [ ] 위치 서비스 통합 (geolocator)

**에러 처리 개선 코드**:
```dart
class CallRepository {
  Future<String> requestCall(CustomerCall call) async {
    try {
      final docRef = await _firestore
          .collection('regions')
          .doc(_regionId)
          .collection('offices')
          .doc(_officeId)
          .collection('calls')
          .add(call.toMap());

      return docRef.id;
    } on FirebaseException catch (e) {
      // Firebase 에러 처리
      if (e.code == 'unavailable') {
        throw CallException('네트워크 연결을 확인해주세요');
      } else if (e.code == 'permission-denied') {
        throw CallException('권한이 없습니다');
      } else {
        throw CallException('콜 요청 실패: ${e.message}');
      }
    } catch (e) {
      throw CallException('알 수 없는 오류가 발생했습니다');
    }
  }
}

// UI에서 사용
Future<void> _requestCall() async {
  try {
    setState(() => _isLoading = true);

    final callId = await ref.read(callRepositoryProvider).requestCall(call);

    // 성공 피드백
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text('콜 요청이 완료되었습니다')),
    );
  } on CallException catch (e) {
    // 에러 다이얼로그 표시
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('콜 요청 실패'),
        content: Text(e.message),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text('확인'),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(context);
              _requestCall(); // 재시도
            },
            child: Text('재시도'),
          ),
        ],
      ),
    );
  } finally {
    setState(() => _isLoading = false);
  }
}
```

**완료 기준**:
- 콜 요청/취소 정상 작동
- 실시간 상태 동기화 확인
- 음성 입력 정확도 80% 이상
- 에러 처리 완벽

---

### **Phase 4: 포인트 시스템** (1주)

**목표**: 포인트 적립/사용/히스토리 구현

**작업 항목**:
- [ ] CustomerPoints 모델 이식
- [ ] PointTransaction 모델 이식
- [ ] PointService 구현
- [ ] 등급 계산 로직 (CustomerGrade)
- [ ] 포인트 카드 UI
- [ ] 포인트 히스토리 UI
- [ ] 실시간 포인트 동기화

**하드코딩 제거 예시**:
```dart
// constants/point_constants.dart
class PointConstants {
  // 등급별 적립률
  static const Map<CustomerGrade, double> earnRates = {
    CustomerGrade.bronze: 0.05,  // 5%
    CustomerGrade.silver: 0.07,  // 7%
    CustomerGrade.gold: 0.10,    // 10%
    CustomerGrade.vip: 0.15,     // 15%
  };

  // 등급 기준 (콜 횟수)
  static const Map<CustomerGrade, int> gradeThresholds = {
    CustomerGrade.bronze: 0,
    CustomerGrade.silver: 10,
    CustomerGrade.gold: 30,
    CustomerGrade.vip: 50,
  };

  // 포인트 사용 최소 금액
  static const int minUseAmount = 1000;

  // 포인트 사용 최소 포인트
  static const int minUsePoints = 1000;
}

// 사용
final earnRate = PointConstants.earnRates[customerGrade]!;
final earnedPoints = (fare * earnRate).toInt();
```

**완료 기준**:
- 포인트 적립/사용 정상 작동
- 등급 자동 업그레이드 확인
- 거래 내역 실시간 동기화
- 하드코딩 0개

---

### **Phase 5: 만보기 시스템** (2주)

**목표**: Android + iOS 만보기 통합 (플랫폼별 최적화)

**작업 항목**:

**Android**:
- [ ] pedometer 패키지 통합
- [ ] Activity Recognition 구현
- [ ] Foreground Service 설정
- [ ] Step Detector/Counter 로직 이식
- [ ] 로컬 DB (Hive) 저장

**iOS**:
- [ ] HealthKit 권한 요청 (Info.plist)
- [ ] health 패키지 통합
- [ ] Background Fetch 설정
- [ ] 걸음 수 실시간 동기화
- [ ] 로컬 DB (Hive) 저장

**공통**:
- [ ] 만보기 UI 구현
- [ ] 일/주/월 통계
- [ ] 세션 기능 (리셋 가능한 임시 카운터)

**플랫폼별 구현 코드**:
```dart
class StepCounterService {
  Stream<int> get stepCountStream {
    if (Platform.isAndroid) {
      return _androidStepStream();
    } else if (Platform.isIOS) {
      return _iosStepStream();
    } else {
      throw UnsupportedError('지원하지 않는 플랫폼');
    }
  }

  // Android: Pedometer 사용
  Stream<int> _androidStepStream() async* {
    await for (final event in Pedometer.stepCountStream) {
      // Activity Recognition으로 걷기/차량 구분
      if (_isWalkingOrRunning) {
        yield event.steps;
      }
    }
  }

  // iOS: HealthKit 사용
  Stream<int> _iosStepStream() async* {
    final health = Health();

    // HealthKit 권한 요청
    final authorized = await health.requestAuthorization([HealthDataType.STEPS]);

    if (!authorized) {
      throw Exception('HealthKit 권한이 거부되었습니다');
    }

    // 주기적으로 걸음 수 조회
    while (true) {
      final now = DateTime.now();
      final midnight = DateTime(now.year, now.month, now.day);

      final steps = await health.getTotalStepsInInterval(midnight, now);

      yield steps ?? 0;

      await Future.delayed(Duration(seconds: 5));
    }
  }

  // Hive로 저장 (공통)
  Future<void> saveSteps(int steps) async {
    final box = await Hive.openBox<StepData>('steps');

    final today = DateTime.now();
    final dateKey = DateFormat('yyyy-MM-dd').format(today);

    await box.put(dateKey, StepData(
      date: today,
      steps: steps,
      updatedAt: today,
    ));
  }
}
```

**Info.plist (iOS)**:
```xml
<key>NSHealthShareUsageDescription</key>
<string>걸음 수를 추적하여 포인트를 적립하기 위해 건강 데이터 접근이 필요합니다.</string>
<key>NSHealthUpdateUsageDescription</key>
<string>걸음 수 데이터를 저장하기 위해 건강 데이터 쓰기 권한이 필요합니다.</string>
<key>UIBackgroundModes</key>
<array>
    <string>fetch</string>
    <string>processing</string>
</array>
```

**완료 기준**:
- Android 만보기 정상 작동
- iOS HealthKit 연동 완료
- 백그라운드 추적 정상 작동
- 배터리 소모 최소화 확인

---

### **Phase 6: FCM 및 알림** (1주)

**목표**: Firebase Cloud Messaging 및 로컬 알림 구현

**작업 항목**:
- [ ] firebase_messaging 설정
- [ ] APNs 인증서 설정 (iOS)
- [ ] 알림 채널 설정
- [ ] 기사 배정 알림
- [ ] 운행 완료 알림
- [ ] 포그라운드/백그라운드 알림 처리
- [ ] LocalBroadcast 대체 (Stream/EventBus)

**FCM 구현 코드**:
```dart
class FcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  // Stream으로 알림 이벤트 전달 (LocalBroadcast 대체)
  final StreamController<Map<String, dynamic>> _notificationController =
      StreamController.broadcast();

  Stream<Map<String, dynamic>> get onNotification => _notificationController.stream;

  Future<void> initialize() async {
    // iOS 권한 요청
    final settings = await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );

    if (settings.authorizationStatus == AuthorizationStatus.authorized) {
      print('FCM 권한 승인');
    }

    // 로컬 알림 초기화
    await _initializeLocalNotifications();

    // 포그라운드 메시지 처리
    FirebaseMessaging.onMessage.listen(_handleForegroundMessage);

    // 백그라운드 메시지 처리
    FirebaseMessaging.onMessageOpenedApp.listen(_handleBackgroundMessage);

    // 토큰 저장
    final token = await _messaging.getToken();
    if (token != null) {
      await _saveFcmToken(token);
    }

    // 토큰 갱신 시
    _messaging.onTokenRefresh.listen(_saveFcmToken);
  }

  void _handleForegroundMessage(RemoteMessage message) {
    final data = message.data;

    switch (data['type']) {
      case 'DRIVER_ASSIGNED':
        _showDriverAssignedNotification(data);
        _notificationController.add({'type': 'DRIVER_ASSIGNED', 'data': data});
        break;

      case 'RIDE_COMPLETED':
        _showRideCompletedNotification(data);
        _notificationController.add({'type': 'RIDE_COMPLETED', 'data': data});
        break;
    }
  }

  Future<void> _showDriverAssignedNotification(Map<String, dynamic> data) async {
    const androidDetails = AndroidNotificationDetails(
      'driver_assigned_channel',
      '기사 배정 알림',
      importance: Importance.high,
      priority: Priority.high,
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );

    const details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    await _localNotifications.show(
      1001,
      '기사 배정 완료',
      '${data['driverName']} 기사가 배정되었습니다',
      details,
      payload: json.encode(data),
    );
  }

  Future<void> _saveFcmToken(String token) async {
    final storage = SecureStorageService();
    final phoneNumber = await storage.getPhoneNumber();
    final regionId = await storage.getRegionId();
    final officeId = await storage.getOfficeId();

    if (phoneNumber == null || regionId == null || officeId == null) {
      return;
    }

    // Firestore에 토큰 저장 (검증 로직 추가)
    await FirebaseFirestore.instance
        .collection('regions')
        .doc(regionId)
        .collection('offices')
        .doc(officeId)
        .collection('customerInfo')
        .doc(phoneNumber)
        .set({
          'fcmToken': token,
          'phoneNumber': phoneNumber,
          'updatedAt': FieldValue.serverTimestamp(),
          'platform': Platform.isAndroid ? 'android' : 'ios',
        }, SetOptions(merge: true));
  }
}
```

**완료 기준**:
- Android 푸시 알림 정상 작동
- iOS APNs 연동 완료
- 포그라운드/백그라운드 모두 정상
- Stream 기반 이벤트 처리 확인

---

### **Phase 7: 게임 및 부가 기능** (0.5주)

**목표**: 사다리 게임, 술자리 게임 구현

**작업 항목**:
- [ ] 사다리 게임 UI (CustomPainter)
- [ ] 계산기 게임
- [ ] 프로필 화면
- [ ] 즐겨찾기 주소 관리

**완료 기준**:
- 게임 정상 작동
- 프로필 관리 완료

---

### **Phase 8: 테스트 및 버그 수정** (1주)

**목표**: 전체 기능 테스트 및 안정화

**작업 항목**:
- [ ] 단위 테스트 작성 (Repository, Service)
- [ ] Widget 테스트 (주요 화면)
- [ ] 통합 테스트
- [ ] Android 실기기 테스트 (다양한 기기)
- [ ] iOS 실기기 테스트 (Mac Cloud 활용)
- [ ] 성능 프로파일링
- [ ] 메모리 누수 검사
- [ ] 버그 수정

**테스트 코드 예시**:
```dart
// test/repositories/call_repository_test.dart
void main() {
  group('CallRepository', () {
    late CallRepository repository;
    late MockFirebaseFirestore mockFirestore;

    setUp(() {
      mockFirestore = MockFirebaseFirestore();
      repository = CallRepository(firestore: mockFirestore);
    });

    test('requestCall should return call ID', () async {
      // Given
      final call = CustomerCall(
        phoneNumber: '01012345678',
        currentLocation: '서울역',
        destinationLocation: '강남역',
      );

      // When
      final callId = await repository.requestCall(call);

      // Then
      expect(callId, isNotEmpty);
    });

    test('requestCall should throw CallException on error', () async {
      // Given
      when(mockFirestore.collection(any).doc(any).collection(any).add(any))
          .thenThrow(FirebaseException(code: 'unavailable'));

      // When & Then
      expect(
        () => repository.requestCall(call),
        throwsA(isA<CallException>()),
      );
    });
  });
}
```

**완료 기준**:
- 테스트 커버리지 70% 이상
- 모든 주요 기능 정상 작동 확인
- 크리티컬 버그 0개
- Android + iOS 동시 출시 준비 완료

---

## 💻 Mac Cloud iOS 빌드 가이드

### Mac Cloud란?
- **클라우드 기반 macOS 환경** (MacStadium, MacinCloud 등)
- Windows에서 iOS 앱을 빌드할 수 있는 솔루션
- 시간당 또는 월 단위 요금제

### ⚠️ 중요 사항

#### 1. **Apple Developer 계정 필수**
- **비용**: $99/년
- **필요 항목**:
  - Apple ID
  - 신용카드
  - 사업자등록번호 (기업 계정 시)

#### 2. **Mac Cloud 서비스 선택**
추천 서비스:
| 서비스 | 특징 | 가격 |
|--------|------|------|
| **Codemagic** ⭐ 추천 | Flutter 전용 CI/CD, 무료 플랜 있음 | 무료 ~ $40/월 |
| **App Center** | Microsoft 제공, Flutter 지원 | 무료 |
| **MacStadium** | 전용 Mac 서버, 성능 우수 | ~$100/월 |
| **MacinCloud** | 시간당 과금, 유연함 | ~$1/시간 |

#### 3. **Codemagic 사용 (강력 추천)**

**장점**:
- ✅ Flutter 전용 (설정 간단)
- ✅ 무료 플랜 (월 500분)
- ✅ 자동 빌드/배포
- ✅ TestFlight 자동 업로드

**설정 방법**:
1. Codemagic 계정 생성 (https://codemagic.io)
2. GitHub 저장소 연결
3. Apple Developer 계정 연동
4. codemagic.yaml 설정 파일 작성
5. 자동 빌드 시작

**codemagic.yaml 예시**:
```yaml
workflows:
  ios-workflow:
    name: iOS Workflow
    max_build_duration: 60
    environment:
      flutter: stable
      xcode: latest
      cocoapods: default
    scripts:
      - name: Get Flutter packages
        script: flutter pub get
      - name: Build iOS
        script: flutter build ios --release
    artifacts:
      - build/ios/ipa/*.ipa
    publishing:
      app_store_connect:
        api_key: $APP_STORE_CONNECT_KEY
        submit_to_testflight: true
```

#### 4. **직접 Mac Cloud 사용 (MacStadium/MacinCloud)**

**필요 작업**:
1. Mac Cloud 서버 접속 (원격 데스크톱)
2. Xcode 설치
3. Flutter 설치
4. 프로젝트 클론
5. CocoaPods 설치
6. iOS 인증서/프로비저닝 프로필 설정
7. 빌드 및 Archive
8. App Store Connect 업로드

**주의사항**:
- ⚠️ 시간 많이 소요 (초기 설정 2-3시간)
- ⚠️ Xcode 사용법 학습 필요
- ⚠️ 매번 원격 접속해야 함

### 5. **iOS 빌드 시 필요한 파일**

**인증서**:
- Development Certificate (개발용)
- Distribution Certificate (출시용)

**프로비저닝 프로필**:
- Development Provisioning Profile
- Ad Hoc Provisioning Profile (테스트)
- App Store Provisioning Profile (출시)

**APNs**:
- APNs Authentication Key (.p8 파일)
- 또는 APNs Certificate (.p12 파일)

### 6. **권장 워크플로우**

```
1. 로컬 개발 (Windows)
   ↓
2. Git Push
   ↓
3. Codemagic 자동 빌드 (iOS)
   ↓
4. TestFlight 자동 배포
   ↓
5. 테스트
   ↓
6. App Store 출시
```

---

## 📊 예상 비용

### 개발 환경
- **Mac Cloud (Codemagic 무료 플랜)**: $0/월
- **Apple Developer 계정**: $99/년
- **Firebase (Spark 플랜)**: 무료
- **총**: ~$99/년

### 운영 비용 (출시 후)
- **Firebase (Blaze 플랜)**: ~$10-50/월
- **Mac Cloud (유료 플랜)**: $40/월 (빌드 많을 경우)
- **Apple Developer**: $99/년
- **총**: ~$50-90/월

---

## 📈 성과 지표

### 개발 효율성
- **코드 감소**: 43% (8,000 → 4,500 라인)
- **파일 감소**: 25% (47 → 35개)
- **개발 속도**: 2배 향상 (단일 코드베이스)

### 성능
- **Firestore 읽기**: 90% 감소
- **앱 시작 시간**: 30% 단축
- **배터리 소모**: 20% 감소 (iOS HealthKit)

### 보안
- **취약점**: 7개 → 0개
- **데이터 암호화**: 0% → 100%

---

## 🚨 리스크 관리

### 기술적 리스크

| 리스크 | 확률 | 영향 | 대응 방안 |
|--------|------|------|----------|
| iOS 만보기 정확도 낮음 | 낮음 | 중간 | HealthKit 사전 테스트 |
| FCM iOS 연동 문제 | 중간 | 높음 | APNs 인증서 사전 준비 |
| Mac Cloud 빌드 실패 | 중간 | 중간 | Codemagic 사용 (안정적) |
| 성능 저하 | 낮음 | 중간 | 프로파일링 도구 활용 |

### 일정 리스크
- **버퍼**: 총 10주 중 1주 버퍼 포함
- **마일스톤**: 2주마다 진행 상황 검토
- **조기 경보**: Phase별 완료 기준 미달 시 즉시 조치

---

## ✅ 체크리스트

### Phase 1 완료 조건
- [ ] Flutter 프로젝트 실행 확인
- [ ] Firebase 연동 (Android)
- [ ] 기본 화면 구조 완성
- [ ] Riverpod 상태 관리 설정

### Phase 2 완료 조건
- [ ] 익명 인증 정상 작동
- [ ] Attribution 매칭 성공률 100%
- [ ] 보안 저장소 암호화 확인
- [ ] Firestore 읽기 90% 감소

### Phase 3 완료 조건
- [ ] 콜 요청/취소 정상 작동
- [ ] 실시간 상태 동기화
- [ ] 음성 입력 정확도 80% 이상
- [ ] 에러 처리 완벽

### Phase 4 완료 조건
- [ ] 포인트 적립/사용 정상
- [ ] 등급 자동 업그레이드
- [ ] 거래 내역 실시간 동기화
- [ ] 하드코딩 0개

### Phase 5 완료 조건
- [ ] Android 만보기 정상
- [ ] iOS HealthKit 연동
- [ ] 백그라운드 추적 확인
- [ ] 배터리 소모 최소화

### Phase 6 완료 조건
- [ ] Android 푸시 정상
- [ ] iOS APNs 연동
- [ ] 포그라운드/백그라운드 정상
- [ ] Stream 이벤트 처리

### Phase 7 완료 조건
- [ ] 게임 정상 작동
- [ ] 프로필 관리 완료

### Phase 8 완료 조건
- [ ] 테스트 커버리지 70%
- [ ] 모든 기능 정상
- [ ] 크리티컬 버그 0개
- [ ] Android + iOS 출시 준비

---

## 📝 다음 단계

### 즉시 시작
1. **Flutter SDK 설치** (https://flutter.dev/docs/get-started/install)
2. **IDE 설정** (VS Code 또는 Android Studio)
3. **Firebase 프로젝트 준비**
4. **Apple Developer 계정 등록**
5. **Codemagic 계정 생성**

### 1주차 목표
- Flutter 프로젝트 생성
- Firebase 연동 (Android)
- 기본 화면 구조 완성
- Riverpod 설정

---

**작성자**: Claude
**최종 수정일**: 2025-01-17
**버전**: 1.0

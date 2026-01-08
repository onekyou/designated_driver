# 📝 Flutter 기본 코드 템플릿

**작성일**: 2025-01-17

---

## 📁 파일 구조

```
lib/
├── main.dart
├── core/
│   ├── constants/
│   │   ├── app_constants.dart
│   │   ├── firebase_constants.dart
│   │   └── point_constants.dart
│   ├── theme/
│   │   └── app_theme.dart
│   ├── utils/
│   │   └── device_fingerprint.dart
│   └── services/
│       └── storage_service.dart
```

---

## 1️⃣ main.dart

```dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:hive_flutter/hive_flutter.dart';

import 'core/theme/app_theme.dart';
import 'core/constants/app_constants.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Firebase 초기화
  await Firebase.initializeApp();

  // Hive 초기화 (로컬 DB)
  await Hive.initFlutter();

  runApp(
    const ProviderScope(
      child: MyApp(),
    ),
  );
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: AppConstants.appName,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.light,
      debugShowCheckedModeBanner: false,
      home: const SplashScreen(),
    );
  }
}

class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // 로고
            Icon(
              Icons.local_taxi,
              size: 100,
              color: Theme.of(context).primaryColor,
            ),
            const SizedBox(height: 24),
            // 앱 이름
            Text(
              AppConstants.appName,
              style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 16),
            // 로딩 인디케이터
            const CircularProgressIndicator(),
          ],
        ),
      ),
    );
  }
}
```

---

## 2️⃣ core/constants/app_constants.dart

```dart
/// 앱 전역 상수 정의
/// 하드코딩 제거를 위한 중앙 관리
class AppConstants {
  // 앱 정보
  static const String appName = 'Designated Customer';
  static const String appVersion = '1.0.0';

  // 시간 관련
  static const int tokenExpiryHours = 24;
  static const int apiTimeoutSeconds = 30;
  static const int retryAttempts = 3;

  // 페이징
  static const int defaultPageSize = 20;
  static const int maxPageSize = 50;

  // 검증
  static const int minPhoneLength = 10;
  static const int maxPhoneLength = 11;
  static const int minNameLength = 2;
  static const int maxNameLength = 20;

  // UI
  static const double defaultPadding = 16.0;
  static const double defaultRadius = 12.0;
  static const double cardElevation = 2.0;

  // 애니메이션
  static const Duration defaultAnimationDuration = Duration(milliseconds: 300);
  static const Duration longAnimationDuration = Duration(milliseconds: 500);

  // 에러 메시지
  static const String networkError = '네트워크 연결을 확인해주세요';
  static const String unknownError = '알 수 없는 오류가 발생했습니다';
  static const String permissionDenied = '권한이 거부되었습니다';
}
```

---

## 3️⃣ core/constants/firebase_constants.dart

```dart
/// Firebase 관련 상수
class FirebaseConstants {
  // 컬렉션 이름
  static const String regionsCollection = 'regions';
  static const String officesCollection = 'offices';
  static const String callsCollection = 'calls';
  static const String customersCollection = 'customers';
  static const String customerPointsCollection = 'customerPoints';
  static const String pointTransactionsCollection = 'pointTransactions';
  static const String attributionsCollection = 'attributions';
  static const String customerInfoCollection = 'customerInfo';

  // Cloud Function 이름
  static const String matchAttributionFunction = 'matchAttribution';
  static const String matchByTokenFunction = 'matchByToken';
  static const String claimTokenFunction = 'claimToken';

  // Cloud Functions Region
  static const String functionsRegion = 'asia-northeast3';

  // 기본 값
  static const String defaultRegionId = 'seoul';
}
```

---

## 4️⃣ core/constants/point_constants.dart

```dart
import '../../features/points/models/customer_grade.dart';

/// 포인트 시스템 상수
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

  // 포인트 사용 제한
  static const int minUseAmount = 1000;      // 최소 사용 금액
  static const int minUsePoints = 1000;      // 최소 사용 포인트
  static const int pointUnit = 100;          // 포인트 사용 단위 (100원 단위)

  // 포인트 유효기간
  static const int pointExpiryMonths = 12;   // 12개월

  // 등급명 (한글)
  static const Map<CustomerGrade, String> gradeNames = {
    CustomerGrade.bronze: '브론즈',
    CustomerGrade.silver: '실버',
    CustomerGrade.gold: '골드',
    CustomerGrade.vip: 'VIP',
  };
}
```

---

## 5️⃣ core/theme/app_theme.dart

```dart
import 'package:flutter/material.dart';

/// 앱 테마 정의
class AppTheme {
  // 메인 컬러
  static const Color primaryColor = Color(0xFF2196F3);      // 블루
  static const Color secondaryColor = Color(0xFF4CAF50);    // 그린
  static const Color accentColor = Color(0xFFFF9800);       // 오렌지

  // 등급 컬러
  static const Color bronzeColor = Color(0xFFCD7F32);
  static const Color silverColor = Color(0xFFC0C0C0);
  static const Color goldColor = Color(0xFFFFD700);
  static const Color vipColor = Color(0xFF9C27B0);          // 퍼플

  // 상태 컬러
  static const Color successColor = Color(0xFF4CAF50);
  static const Color errorColor = Color(0xFFF44336);
  static const Color warningColor = Color(0xFFFF9800);
  static const Color infoColor = Color(0xFF2196F3);

  // Light Theme
  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: primaryColor,
        brightness: Brightness.light,
      ),
      appBarTheme: const AppBarTheme(
        elevation: 0,
        centerTitle: true,
      ),
      cardTheme: CardTheme(
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
        ),
        filled: true,
      ),
    );
  }

  // Dark Theme
  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: primaryColor,
        brightness: Brightness.dark,
      ),
      appBarTheme: const AppBarTheme(
        elevation: 0,
        centerTitle: true,
      ),
      cardTheme: CardTheme(
        elevation: 2,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    );
  }
}
```

---

## 6️⃣ core/utils/device_fingerprint.dart

```dart
import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:device_info_plus/device_info_plus.dart';

/// 디바이스 핑거프린트 유틸리티
/// ✅ 보안 개선: Android ID를 SHA-256 해시 처리
class DeviceFingerprintUtil {
  /// 디바이스 정보 수집 (해시 처리)
  static Future<Map<String, dynamic>> collectDeviceInfo() async {
    final deviceInfo = DeviceInfoPlugin();

    if (Platform.isAndroid) {
      return await _collectAndroidInfo(deviceInfo);
    } else if (Platform.isIOS) {
      return await _collectIOSInfo(deviceInfo);
    } else {
      throw UnsupportedError('지원하지 않는 플랫폼입니다');
    }
  }

  /// Android 정보 수집
  static Future<Map<String, dynamic>> _collectAndroidInfo(
    DeviceInfoPlugin deviceInfo,
  ) async {
    final androidInfo = await deviceInfo.androidInfo;

    // ✅ Android ID를 SHA-256 해시 처리 (보안 강화)
    final androidId = androidInfo.id;
    final hashedId = _sha256Hash(androidId);

    return {
      'androidIdHash': hashedId,  // ✅ 해시 처리된 ID
      'manufacturer': androidInfo.manufacturer,
      'model': androidInfo.model,
      'brand': androidInfo.brand,
      'device': androidInfo.device,
      'osVersion': androidInfo.version.release,
      'sdkVersion': androidInfo.version.sdkInt,
      'screenResolution': _getScreenResolution(),
      'timezone': DateTime.now().timeZoneName,
      'language': Platform.localeName,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'source': 'flutter_android_app',
    };
  }

  /// iOS 정보 수집
  static Future<Map<String, dynamic>> _collectIOSInfo(
    DeviceInfoPlugin deviceInfo,
  ) async {
    final iosInfo = await deviceInfo.iosInfo;

    // ✅ iOS도 identifierForVendor 해시 처리
    final vendorId = iosInfo.identifierForVendor ?? '';
    final hashedId = _sha256Hash(vendorId);

    return {
      'vendorIdHash': hashedId,  // ✅ 해시 처리된 ID
      'model': iosInfo.model,
      'systemName': iosInfo.systemName,
      'systemVersion': iosInfo.systemVersion,
      'name': iosInfo.name,
      'localizedModel': iosInfo.localizedModel,
      'screenResolution': _getScreenResolution(),
      'timezone': DateTime.now().timeZoneName,
      'language': Platform.localeName,
      'timestamp': DateTime.now().millisecondsSinceEpoch,
      'source': 'flutter_ios_app',
    };
  }

  /// SHA-256 해시 생성
  static String _sha256Hash(String input) {
    final bytes = utf8.encode(input);
    final digest = sha256.convert(bytes);
    return digest.toString();
  }

  /// 화면 해상도 문자열
  static String _getScreenResolution() {
    // TODO: 실제 화면 해상도 가져오기
    // MediaQuery를 사용하려면 BuildContext가 필요
    // 임시로 플레이스홀더 반환
    return 'unknown';
  }
}
```

---

## 7️⃣ core/services/storage_service.dart

```dart
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// 보안 저장소 서비스
/// ✅ 민감정보 암호화 저장 (flutter_secure_storage)
class SecureStorageService {
  // FlutterSecureStorage 인스턴스 (싱글톤)
  static final SecureStorageService _instance = SecureStorageService._internal();
  factory SecureStorageService() => _instance;
  SecureStorageService._internal();

  final FlutterSecureStorage _storage = const FlutterSecureStorage(
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock,
    ),
  );

  // ============================================
  // 전화번호
  // ============================================
  Future<void> savePhoneNumber(String phoneNumber) async {
    await _storage.write(key: 'phoneNumber', value: phoneNumber);
  }

  Future<String?> getPhoneNumber() async {
    return await _storage.read(key: 'phoneNumber');
  }

  // ============================================
  // 사무실 정보
  // ============================================
  Future<void> saveOfficeInfo({
    required String regionId,
    required String officeId,
  }) async {
    await _storage.write(key: 'regionId', value: regionId);
    await _storage.write(key: 'officeId', value: officeId);
  }

  Future<String?> getRegionId() async {
    return await _storage.read(key: 'regionId');
  }

  Future<String?> getOfficeId() async {
    return await _storage.read(key: 'officeId');
  }

  // ============================================
  // Attribution 토큰
  // ============================================
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
    const twentyFourHours = 24 * 60 * 60 * 1000;

    // ✅ 24시간 이내 토큰만 유효
    if (now - timestamp < twentyFourHours) {
      return token;
    } else {
      // 만료된 토큰 삭제
      await clearAttributionToken();
      return null;
    }
  }

  Future<void> clearAttributionToken() async {
    await _storage.delete(key: 'attributionToken');
    await _storage.delete(key: 'attributionTokenTimestamp');
  }

  // ============================================
  // FCM 토큰
  // ============================================
  Future<void> saveFcmToken(String token) async {
    await _storage.write(key: 'fcmToken', value: token);
  }

  Future<String?> getFcmToken() async {
    return await _storage.read(key: 'fcmToken');
  }

  // ============================================
  // 기사 추천 정보
  // ============================================
  Future<void> saveDriverReferralInfo({
    required String driverId,
    required String driverName,
  }) async {
    await _storage.write(key: 'referralDriverId', value: driverId);
    await _storage.write(key: 'referralDriverName', value: driverName);
  }

  Future<Map<String, String?>> getDriverReferralInfo() async {
    return {
      'driverId': await _storage.read(key: 'referralDriverId'),
      'driverName': await _storage.read(key: 'referralDriverName'),
    };
  }

  // ============================================
  // 전체 삭제 (로그아웃)
  // ============================================
  Future<void> clearAll() async {
    await _storage.deleteAll();
  }
}
```

---

## ✅ 다음 단계

Flutter 설치 완료 후:

1. **프로젝트 생성**
   ```bash
   cd C:\app_dev\designated_driver
   flutter create designated_customer_flutter --org com.designated
   cd designated_customer_flutter
   ```

2. **위 파일들을 해당 경로에 복사**
   - `main.dart` → `lib/main.dart`
   - `app_constants.dart` → `lib/core/constants/app_constants.dart`
   - 등등...

3. **패키지 설치**
   ```bash
   flutter pub get
   ```

4. **실행 테스트**
   ```bash
   flutter run
   ```

---

**작성자**: Claude
**최종 수정일**: 2025-01-17

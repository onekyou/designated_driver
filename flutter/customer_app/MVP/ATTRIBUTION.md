# 손님앱 귀속 (Attribution) — Phase 1 수동 입력 + Phase 2 App Clip Swift

> **의제 10 (손님앱 전환 전략)** + **bubbly-cuddling-hopcroft.md 검토 포인트 3 (Install Referrer 8개 파라미터)** 결정 반영
> **Phase 1/2 분리**: Phase 1은 Flutter 단독 + 수동 사무실 코드 입력 (Mac 불요). Phase 2는 Mac 도착 후 Swift App Clip 타겟 추가로 iOS 자동 귀속 완전 해결.
> **핵심 전제**: Branch.io/AppsFlyer 등 **deferred deep linking 서드파티 배제** — 비용(월 $500+) + 민감 정보(bank/account/holder) 국외 이전 우려 + 2025 Firebase Dynamic Links 종료로 대체재 시장 불안정.
> **작성일**: 2026-04-16

---

## 1. 귀속 데이터 사양

### 1.1 Kotlin 원본 Install Referrer 8개 파라미터

**파일**: `customer_app/app/src/main/java/com/designated/customer/MainActivity.kt:208-254` (`parseAndSaveReferrer` 함수)

실제 파싱 로직 (line 220-228):

```kotlin
val provinceId = params["p"] ?: params["r"]  // p=provinceId, r=regionId 하위호환
val cityId = params["c"] ?: ""
val officeId = params["o"]
val driverId = params["driver"]
val driverName = params["driverName"]
val phoneNumber = params["phone"]
val bankName = params["bank"]
val accountNumber = params["account"]
val accountHolder = params["holder"]
```

| 파라미터 키 | 필수 여부 | 저장 Kotlin 필드 | Flutter SharedPreferences 키 | 용도 |
|-------------|-----------|------------------|-------------------------------|------|
| `p` | 필수 | `provinceId` | `province_id` | Firestore 경로 |
| `c` | 필수 | `cityId` | `city_id` | Firestore 경로 |
| `o` | 필수 | `officeId` | `office_id` | Firestore 경로 |
| `driver` | 선택 | `driverId` | `driver_id` | 추천 기사 추적 (귀속) |
| `driverName` | 선택 | `driverName` | `driver_name` | 추천 기사 표시명 |
| `phone` | 선택 | `officePhone` | `office_phone` | 외상 결제 안내용 사무실 연락처 |
| `bank` | 선택 | `bankName` | `bank_name` | 외상 결제 은행명 |
| `account` | 선택 | `accountNumber` | `account_number` | 외상 결제 계좌번호 |
| `holder` | 선택 | `accountHolder` | `account_holder` | 외상 결제 예금주 |

**필수 3종 누락 시** (Kotlin line 248): "필수 파라미터 누락" 경고 + 저장 스킵 → 앱은 동작하지만 사무실 연결 안 됨. 이 경우 기사가 QR 재생성 또는 고객이 수동 입력 필요.

### 1.2 민감 정보 평가 (검토 포인트 3 재확인)

`bank` / `account` / `accountHolder` 3종은 **기사의 개인 금융 정보** — 외상 결제 시 고객이 입금할 계좌. Install Referrer URL에 평문으로 전송됨.

**Branch.io 등 서드파티 경유 시 발생할 우려**:
- Branch.io 미국 서버 경유 → **개인정보 국외이전 동의 필수** (개인정보보호법 28조의8)
- Branch.io 보관 정책 + 로그 노출 리스크
- 한국 대리운전 사무실 다수 = 금융 계좌 다수 유출 위험

**Google Play Install Referrer 원본 방식의 이점**:
- Play Store → 앱 설치 1회성 이벤트
- 서드파티 중계 없음
- Android Google Play 서비스 내부 통신
- **단, iOS는 동등 API 없음** → Phase 1 수동 입력 + Phase 2 App Clip으로 보완

### 1.3 Kotlin PreferencesManager 전체 키 목록

**파일**: `customer_app/app/src/main/java/com/designated/customer/util/PreferencesManager.kt:13-33`

Install Referrer로 저장되는 8개 + 앱 내 추가 저장 11개 = **총 19개 키**:

| Kotlin 키 상수 | SharedPreferences 키 | Install Referrer 유래 | 설명 |
|----------------|----------------------|----------------------|------|
| `KEY_OFFICE_ID` | `office_id` | ✅ | Firestore 경로 |
| `KEY_PROVINCE_ID` | `province_id` | ✅ | Firestore 경로 |
| `KEY_CITY_ID` | `city_id` | ✅ | Firestore 경로 |
| `KEY_REGION_ID` | `region_id` | — | 하위호환 (`provinces/` 이관 전) |
| `KEY_PHONE_NUMBER` | `phone_number` | — | Phone Auth 완료 후 |
| `KEY_IS_PHONE_VERIFIED` | `is_phone_verified` | — | Phone Auth 완료 플래그 |
| `KEY_CUSTOMER_GRADE` | `customer_grade` | — | BRONZE/SILVER/GOLD/VIP |
| `KEY_OFFICE_PHONE` | `office_phone` | ✅ | 외상 결제 사무실 연락처 |
| `KEY_BANK_NAME` | `bank_name` | ✅ | 외상 결제 은행명 |
| `KEY_ACCOUNT_NUMBER` | `account_number` | ✅ | 외상 결제 계좌 |
| `KEY_ACCOUNT_HOLDER` | `account_holder` | ✅ | 외상 결제 예금주 |
| `KEY_HOME_ADDRESS` | `home_address` | — | 자주 가는 주소 |
| `KEY_FAVORITE_ADDRESSES` | `favorite_addresses` | — | 즐겨찾기 주소 |
| `KEY_DRIVER_ID` | `driver_id` | ✅ | 추천 기사 |
| `KEY_DRIVER_NAME` | `driver_name` | ✅ | 추천 기사 표시명 |
| `KEY_TERMS_ACCEPTED` | `terms_accepted` | — | 약관 동의 플래그 |
| `KEY_TERMS_VERSION` | `terms_version` | — | 약관 버전 |
| `KEY_TERMS_ACCEPTED_AT` | `terms_accepted_at` | — | 약관 동의 타임스탬프 |
| `KEY_MARKETING_CONSENT` | `marketing_consent` | — | 마케팅 수신 동의 |

Flutter Phase 1은 **동일 키 이름(snake_case) 유지** — 의제 10 "같은 applicationId 유지" 전략 시 기존 Kotlin 평문 SharedPreferences 데이터가 그대로 상속됨 (의제 9 `SecurePreferencesManager`와 달리 평문이므로 Flutter `shared_preferences`가 직접 읽기 가능).

### 1.4 Flutter 상수 정의 (신규)

**파일**: `customer_app_flutter/lib/core/constants/preferences_keys.dart` (신규)

```dart
/// Kotlin PreferencesManager와 키 이름 100% 일치.
/// 의제 10 같은 applicationId 유지 시 SharedPreferences 데이터 자동 상속.
class PreferencesKeys {
  // 사무실 정보 (Install Referrer 3 필수)
  static const String officeId = 'office_id';
  static const String provinceId = 'province_id';
  static const String cityId = 'city_id';
  static const String regionId = 'region_id'; // 하위호환

  // Phone Auth
  static const String phoneNumber = 'phone_number';
  static const String isPhoneVerified = 'is_phone_verified';

  // 고객 등급
  static const String customerGrade = 'customer_grade';

  // 외상 결제 안내 (Install Referrer 4 선택)
  static const String officePhone = 'office_phone';
  static const String bankName = 'bank_name';
  static const String accountNumber = 'account_number';
  static const String accountHolder = 'account_holder';

  // 주소
  static const String homeAddress = 'home_address';
  static const String favoriteAddresses = 'favorite_addresses';

  // 추천 기사 (Install Referrer 2 선택)
  static const String driverId = 'driver_id';
  static const String driverName = 'driver_name';

  // 약관
  static const String termsAccepted = 'terms_accepted';
  static const String termsVersion = 'terms_version';
  static const String termsAcceptedAt = 'terms_accepted_at';
  static const String marketingConsent = 'marketing_consent';
}
```

---

## 2. Phase 1 — 수동 사무실 코드 입력 (Mac 불요)

### 2.1 UX 플로우

```
앱 첫 실행
   ↓
[0] SharedPreferences 체크
   ├── office_id 있음 → 홈 화면 (Install Referrer 또는 이전 설정)
   └── office_id 없음
        ↓
[1] 사무실 코드 입력 화면
   ├── 입력 필드 (TextField)
   ├── "카메라로 스캔" 버튼 (옵션 C)
   └── 안내 문구: "사무실에서 받은 코드를 입력해주세요"
        ↓
[2] Firestore 코드 → 사무실 매핑 조회
   ├── 옵션 A: 전체 경로 (seoul-gangnam-office1)
   └── 옵션 B: 단축 코드 6자리 (GNG123)
        ↓
[3] 매칭 성공
   ├── SharedPreferences 저장 (p, c, o 3종 필수)
   ├── Firestore `offices/{o}` 읽어 연락처 정보 조회 (선택 4종)
   └── 홈 화면으로 진행
[4] 매칭 실패
   ├── 에러 메시지 "올바른 코드가 아닙니다. 기사에게 확인해주세요"
   └── 재입력 유도
```

### 2.2 사무실 코드 형식 선택

의제 10 결정은 "수동 입력" 자체에 국한. 실제 코드 형식은 3가지 중 택일:

| 옵션 | 예시 | 장점 | 단점 | Firestore 작업 |
|------|------|------|------|----------------|
| **A. 전체 경로** | `seoul-gangnam-office1` | 추가 매핑 테이블 불요, Firestore 직접 조회 | 입력 길이 20+자, 오타 위험 | 없음 |
| **B. 단축 코드 6자리** | `GNG123` | 입력 쉬움, 오타 적음 | **신규 매핑 테이블** `office_codes/{code}` 필수 | 사무실 승인 시 자동 생성 |
| **C. QR 카메라 스캔** | `mobile_scanner` 패키지 | 입력 0자, 기사가 QR 프린트 | 카메라 권한 + QR 생성/프린트 인프라 | (B에 추가) |

**권장 조합**: **B + C** (단축 코드 + QR 스캔). A는 관리자 UI 디버깅 목적.

**단축 코드 매핑 테이블** (신규 Firestore 컬렉션):

```
office_codes/{code}  (예: office_codes/GNG123)
  ├── provinceId: "seoul"
  ├── cityId: "gangnam"
  ├── officeId: "office1-uuid"
  ├── createdAt: Timestamp
  └── createdBy: "admin-uid"
```

사무실 승인 CF 트리거(`onOfficeApproved`)에서 코드 자동 생성:
- 6자리 영숫자 (대문자 + 숫자, 혼동 문자 제외: O/0, I/1)
- Firestore 중복 검사 + 재시도 (10회 이내)

### 2.3 Dart 구현 — OfficeCodeService

**파일**: `customer_app_flutter/lib/services/office_code_service.dart` (신규)

```dart
import 'package:cloud_firestore/cloud_firestore.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';
import '../core/constants/preferences_keys.dart';
import '../core/error/exceptions.dart';

/// 손님앱 Phase 1 — 수동 사무실 코드 입력 기반 귀속.
/// Install Referrer iOS 부재 대응. Phase 2 App Clip으로 대체 예정.
class OfficeCodeService {
  final FirebaseFirestore _firestore;
  final SharedPreferences _prefs;

  OfficeCodeService({
    FirebaseFirestore? firestore,
    required SharedPreferences prefs,
  })  : _firestore = firestore ?? FirebaseFirestore.instance,
        _prefs = prefs;

  /// 단축 코드 입력 → 사무실 정보 조회 → SharedPreferences 저장.
  /// 성공 시 OfficeInfo 반환, 실패 시 예외.
  Future<OfficeInfo> connectByCode(String code) async {
    final normalizedCode = code.trim().toUpperCase();
    if (normalizedCode.length != 6) {
      throw const AttributionException('코드는 6자리여야 합니다');
    }

    // 1. office_codes/{code} 조회 (매핑 테이블)
    final codeDoc = await _firestore
        .collection('office_codes')
        .doc(normalizedCode)
        .get();

    if (!codeDoc.exists) {
      throw const AttributionException(
        '올바른 코드가 아닙니다. 기사에게 확인해주세요',
      );
    }

    final mapping = codeDoc.data()!;
    final provinceId = mapping['provinceId'] as String;
    final cityId = mapping['cityId'] as String;
    final officeId = mapping['officeId'] as String;

    // 2. 실제 사무실 문서 조회
    final officeDoc = await _firestore
        .collection('provinces')
        .doc(provinceId)
        .collection('cities')
        .doc(cityId)
        .collection('offices')
        .doc(officeId)
        .get();

    if (!officeDoc.exists) {
      throw const AttributionException(
        '사무실 정보를 찾을 수 없습니다. 관리자에게 문의해주세요',
      );
    }

    final office = officeDoc.data()!;

    // 3. SharedPreferences 저장 (Kotlin 키 호환)
    await _prefs.setString(PreferencesKeys.provinceId, provinceId);
    await _prefs.setString(PreferencesKeys.cityId, cityId);
    await _prefs.setString(PreferencesKeys.officeId, officeId);
    await _prefs.setString(PreferencesKeys.regionId, provinceId); // 하위호환

    // 4. 외상 결제 안내 정보 (선택 4종)
    final officePhone = office['officePhone'] as String?;
    final bankName = office['bankName'] as String?;
    final accountNumber = office['accountNumber'] as String?;
    final accountHolder = office['accountHolder'] as String?;

    if (officePhone != null) {
      await _prefs.setString(PreferencesKeys.officePhone, officePhone);
    }
    if (bankName != null) {
      await _prefs.setString(PreferencesKeys.bankName, bankName);
    }
    if (accountNumber != null) {
      await _prefs.setString(PreferencesKeys.accountNumber, accountNumber);
    }
    if (accountHolder != null) {
      await _prefs.setString(PreferencesKeys.accountHolder, accountHolder);
    }

    debugPrint('[OfficeCode] 연결 성공: $provinceId/$cityId/$officeId');

    return OfficeInfo(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      officeName: office['name'] as String? ?? '',
      officePhone: officePhone,
      bankName: bankName,
      accountNumber: accountNumber,
      accountHolder: accountHolder,
    );
  }

  /// 현재 연결된 사무실 정보 조회 (연결 상태 확인용)
  OfficeInfo? getCurrentOffice() {
    final provinceId = _prefs.getString(PreferencesKeys.provinceId);
    final cityId = _prefs.getString(PreferencesKeys.cityId);
    final officeId = _prefs.getString(PreferencesKeys.officeId);

    if (provinceId == null || cityId == null || officeId == null) {
      return null;
    }

    return OfficeInfo(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      officeName: '', // UI에서 필요시 Firestore 재조회
      officePhone: _prefs.getString(PreferencesKeys.officePhone),
      bankName: _prefs.getString(PreferencesKeys.bankName),
      accountNumber: _prefs.getString(PreferencesKeys.accountNumber),
      accountHolder: _prefs.getString(PreferencesKeys.accountHolder),
    );
  }

  /// 사무실 연결 해제 (설정에서 "다른 사무실로 변경")
  Future<void> disconnect() async {
    await _prefs.remove(PreferencesKeys.provinceId);
    await _prefs.remove(PreferencesKeys.cityId);
    await _prefs.remove(PreferencesKeys.officeId);
    await _prefs.remove(PreferencesKeys.regionId);
    await _prefs.remove(PreferencesKeys.officePhone);
    await _prefs.remove(PreferencesKeys.bankName);
    await _prefs.remove(PreferencesKeys.accountNumber);
    await _prefs.remove(PreferencesKeys.accountHolder);
    debugPrint('[OfficeCode] 연결 해제');
  }
}

class OfficeInfo {
  final String provinceId;
  final String cityId;
  final String officeId;
  final String officeName;
  final String? officePhone;
  final String? bankName;
  final String? accountNumber;
  final String? accountHolder;

  const OfficeInfo({
    required this.provinceId,
    required this.cityId,
    required this.officeId,
    required this.officeName,
    this.officePhone,
    this.bankName,
    this.accountNumber,
    this.accountHolder,
  });
}

class AttributionException implements Exception {
  final String message;
  const AttributionException(this.message);

  @override
  String toString() => 'AttributionException: $message';
}
```

### 2.4 QR 스캔 보조 (옵션 C)

**파일**: `customer_app_flutter/lib/screens/office_code_scan_screen.dart` (신규)

```dart
import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/office_code_service.dart';

class OfficeCodeScanScreen extends ConsumerStatefulWidget {
  const OfficeCodeScanScreen({super.key});

  @override
  ConsumerState<OfficeCodeScanScreen> createState() =>
      _OfficeCodeScanScreenState();
}

class _OfficeCodeScanScreenState extends ConsumerState<OfficeCodeScanScreen> {
  final MobileScannerController _controller = MobileScannerController();
  bool _processing = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _onDetected(BarcodeCapture capture) async {
    if (_processing) return;
    final rawValue = capture.barcodes.firstOrNull?.rawValue;
    if (rawValue == null) return;

    // QR 형식: "callmadang://office?code=GNG123" 또는 단순 "GNG123"
    String? code;
    if (rawValue.startsWith('callmadang://')) {
      final uri = Uri.tryParse(rawValue);
      code = uri?.queryParameters['code'];
    } else if (RegExp(r'^[A-Z0-9]{6}$').hasMatch(rawValue)) {
      code = rawValue;
    }

    if (code == null) return;

    setState(() => _processing = true);
    try {
      final service = ref.read(officeCodeServiceProvider);
      final office = await service.connectByCode(code);
      if (!mounted) return;
      Navigator.pop(context, office);
    } on AttributionException catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.message)),
      );
      setState(() => _processing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('사무실 QR 스캔')),
      body: Stack(
        children: [
          MobileScanner(
            controller: _controller,
            onDetect: _onDetected,
          ),
          if (_processing)
            const Center(child: CircularProgressIndicator()),
        ],
      ),
    );
  }
}
```

**pubspec.yaml 추가**:
```yaml
mobile_scanner: ^5.2.0  # QR 카메라 스캔 (Phase 1 선택)
```

**Info.plist 추가** (의제 6 Permission 사유):
```xml
<key>NSCameraUsageDescription</key>
<string>사무실 QR 코드를 스캔하여 연결하기 위해 카메라를 사용합니다.</string>
```

### 2.5 UX 이탈률 측정 (의제 11 연계)

**Analytics 이벤트 추가** (`ATTRIBUTION.md` 측정 항목):

| 이벤트 | 발생 시점 | 파라미터 | 용도 |
|--------|-----------|----------|------|
| `office_code_screen_shown` | 입력 화면 진입 | `platform` | 진입자 수 |
| `office_code_submitted` | 코드 입력 후 제출 | `length`, `method` (manual/qr) | 입력 방식 비율 |
| `office_code_success` | 연결 성공 | `province_id`, `city_id` | 성공 지역 |
| `office_code_failed` | 연결 실패 | `reason` (invalid_code/not_found/network) | 실패 사유 |
| `office_code_abandoned` | 3회 이상 실패 후 이탈 | `last_attempt_code` | 이탈률 |

**R1_ATTRIBUTION_ACCURACY 발동 기준**:
- 월간 `office_code_screen_shown` 중 `office_code_success` 비율 **< 70%** (= 이탈률 30% 초과)
- 또는 `office_code_failed` 일 5건 이상 지속
- 충족 시 Phase 2 App Clip 도입 우선순위 상향

### 2.6 Kotlin Android → Flutter Android 이관 (의제 10 같은 applicationId)

의제 10 결정: **같은 `com.designated.customer.app` applicationId 유지 + Staged Rollout**.

같은 applicationId로 앱 교체 시 **SharedPreferences 평문 키**는 자동 상속됨 (Android Data API). 따라서:

- Kotlin 저장 키 `office_id` / `province_id` / `city_id` / `driver_id` / ... → Flutter `SharedPreferences.getInstance()`에서 **그대로 읽기 가능**
- Flutter 첫 실행 시 `_prefs.getString('office_id')` null이 아니면 → 수동 입력 화면 건너뛰고 홈으로

**`customer_app_flutter/lib/main.dart` 초기 분기**:

```dart
Future<Widget> _buildInitialRoute() async {
  final prefs = await SharedPreferences.getInstance();
  final officeId = prefs.getString(PreferencesKeys.officeId);
  final phoneVerified = prefs.getBool(PreferencesKeys.isPhoneVerified) ?? false;

  if (officeId == null) {
    return const OfficeCodeInputScreen(); // 수동 입력 필요
  }
  if (!phoneVerified) {
    return const PhoneAuthScreen(); // 사무실 있으나 Phone Auth 미완료
  }
  return const HomeScreen();
}
```

**Kotlin `secure_prefs` 자격증명**은 상속 불가 (의제 9 결정 재확인). Phone Auth credential은 Firebase Auth SDK가 내부적으로 저장하므로 **앱 업데이트 후 `FirebaseAuth.instance.currentUser` 유지 가능성 높음** — 실기기 검증 필수 (의제 10 위험 신호).

### 2.7 Kotlin → Flutter iOS 이관 (해당 없음)

iOS는 기존 Kotlin 앱이 **배포되지 않음** (Flutter 신규 출시). 마이그레이션 대상 0.

---

## 3. Phase 2 — Swift App Clip 설계 (Mac 도착 후)

### 3.1 App Clip 전체 플로우

```
[1] 기사가 QR 프린트하여 고객에게 제시
    URL 예: https://calllink.io.kr/office?p=seoul&c=gangnam&o=office1&driver=d123
[2] 고객 iPhone 카메라로 QR 스캔
[3] iOS Safari가 URL 감지 → App Clip 카드 표시 (AASA 등록됨 전제)
[4] 고객이 "열기" 탭
[5] App Clip (~10MB) 즉시 다운로드 + 실행
[6] App Clip URL 파라미터 자동 추출 (Kotlin Install Referrer 동등)
[7] App Clip에서:
    a. Anonymous Auth → UID 획득
    b. Cloud Function generateCustomToken(uid) 호출
    c. App Group UserDefaults에 저장 (uid + customToken + 8 파라미터)
    d. 사무실 정보 화면 + 간단 콜 요청 버튼 (옵션)
[8] 풀앱 설치 유도 배너 (App Clip 내장 Apple API)
[9] 고객이 풀앱 설치 (App Store 이동)
[10] 풀앱 (Flutter) 첫 실행
    a. App Group UserDefaults 읽기 (dart:ffi 또는 method_channel)
    b. 8 파라미터 → Flutter SharedPreferences 복사
    c. customToken → signInWithCustomToken → 같은 UID 계승
[11] 사무실 연결 + Phone Auth 완료 상태로 즉시 홈 화면
```

### 3.2 AASA (Apple App Site Association) 호스팅

**파일 경로**: `https://calllink.io.kr/.well-known/apple-app-site-association`

**파일 내용**:
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

**호스팅 위치**: `homepage/public/.well-known/apple-app-site-association` (Firebase Hosting, `callmadang-web` 타겟).

**MIME 타입**: `application/json`, 확장자 없음, HTTPS 필수.

**검증 방법**:
- `curl -v https://calllink.io.kr/.well-known/apple-app-site-association`
- Apple CDN 캐시: https://app-site-association.cdn-apple.com/a/v1/calllink.io.kr
- Apple Developer → App Clip Experience 설정 시 URL 등록

### 3.3 App Group 구성

**App Group ID**: `group.com.designated.customer`

**Xcode 설정**:
- 풀앱 Target → Signing & Capabilities → App Groups → `group.com.designated.customer` 추가
- App Clip Target → Signing & Capabilities → App Groups → 동일 ID 추가
- `Runner.entitlements` + `ClipExtension.entitlements` 양쪽 자동 업데이트

**공유 UserDefaults 키** (App Clip → 풀앱):

| 키 | 타입 | 출처 | 풀앱에서 읽은 후 이관 목적지 |
|----|------|------|----------------------------|
| `appclip_provinceId` | String | URL `p` | SharedPreferences `province_id` |
| `appclip_cityId` | String | URL `c` | SharedPreferences `city_id` |
| `appclip_officeId` | String | URL `o` | SharedPreferences `office_id` |
| `appclip_driverId` | String? | URL `driver` | SharedPreferences `driver_id` |
| `appclip_driverName` | String? | URL `driverName` | SharedPreferences `driver_name` |
| `appclip_officePhone` | String? | URL `phone` | SharedPreferences `office_phone` |
| `appclip_bankName` | String? | URL `bank` | SharedPreferences `bank_name` |
| `appclip_accountNumber` | String? | URL `account` | SharedPreferences `account_number` |
| `appclip_accountHolder` | String? | URL `holder` | SharedPreferences `account_holder` |
| `appclip_authUid` | String | Anonymous Auth 결과 | Firebase Auth 계승 대상 |
| `appclip_customToken` | String | CF `generateCustomToken` | `signInWithCustomToken` 인자 |
| `appclip_phoneNumber` | String? | 사용자 App Clip에서 입력 시 | SharedPreferences `phone_number` |
| `appclip_tokenGeneratedAt` | Double (Unix 타임스탬프) | Custom Token 발급 시각 | 만료 체크 (1시간 내 풀앱 실행 권장) |

### 3.4 Swift App Clip 코드 구조 (~500줄)

**파일 구조**:
```
ios/ClipExtension/
├── Info.plist
├── ClipExtension.entitlements
├── ClipExtensionApp.swift        (~30 LOC) — @main
├── ContentView.swift             (~150 LOC) — 사무실 안내 + 콜 요청 옵션
├── URLParameterParser.swift      (~80 LOC) — Kotlin MainActivity.parseAndSaveReferrer 1:1 포팅
├── AppGroupStorage.swift         (~60 LOC) — UserDefaults(suiteName:) 래퍼
├── FirebaseAppClipBootstrap.swift (~50 LOC) — Firebase 초기화 + Anonymous Auth
├── CustomTokenFetcher.swift      (~50 LOC) — CF `generateCustomToken` 호출
└── FullAppInstallPrompt.swift    (~80 LOC) — 풀앱 설치 유도 배너
```

#### 3.4.1 ClipExtensionApp.swift (진입점)

```swift
import SwiftUI
import FirebaseCore

@main
struct ClipExtensionApp: App {
  init() {
    FirebaseApp.configure()
  }

  var body: some Scene {
    WindowGroup {
      ContentView()
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
          guard let url = activity.webpageURL else { return }
          Task {
            await AppClipCoordinator.shared.handleURL(url)
          }
        }
    }
  }
}
```

#### 3.4.2 URLParameterParser.swift (Kotlin parseAndSaveReferrer 포팅)

```swift
import Foundation

struct OfficeAttributionParams {
  let provinceId: String
  let cityId: String
  let officeId: String
  let driverId: String?
  let driverName: String?
  let officePhone: String?
  let bankName: String?
  let accountNumber: String?
  let accountHolder: String?
}

enum URLParameterParser {
  /// Kotlin MainActivity.kt:208-254 parseAndSaveReferrer 1:1 포팅
  /// 필수 3종 (p, c, o) 누락 시 nil 반환
  static func parse(_ url: URL) -> OfficeAttributionParams? {
    guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
          let items = components.queryItems else { return nil }

    var params: [String: String] = [:]
    for item in items {
      if let value = item.value {
        params[item.name] = value
      }
    }

    // Kotlin: p ?: r (regionId 하위호환)
    guard let provinceId = params["p"] ?? params["r"],
          let cityId = params["c"], !cityId.isEmpty,
          let officeId = params["o"] else {
      return nil
    }

    return OfficeAttributionParams(
      provinceId: provinceId,
      cityId: cityId,
      officeId: officeId,
      driverId: params["driver"],
      driverName: params["driverName"],
      officePhone: params["phone"],
      bankName: params["bank"],
      accountNumber: params["account"],
      accountHolder: params["holder"]
    )
  }
}
```

#### 3.4.3 AppGroupStorage.swift

```swift
import Foundation

enum AppGroupStorage {
  static let suiteName = "group.com.designated.customer"
  static var shared: UserDefaults {
    UserDefaults(suiteName: suiteName)!
  }

  static func saveAttribution(_ params: OfficeAttributionParams) {
    let defaults = shared
    defaults.set(params.provinceId, forKey: "appclip_provinceId")
    defaults.set(params.cityId, forKey: "appclip_cityId")
    defaults.set(params.officeId, forKey: "appclip_officeId")
    defaults.set(params.driverId, forKey: "appclip_driverId")
    defaults.set(params.driverName, forKey: "appclip_driverName")
    defaults.set(params.officePhone, forKey: "appclip_officePhone")
    defaults.set(params.bankName, forKey: "appclip_bankName")
    defaults.set(params.accountNumber, forKey: "appclip_accountNumber")
    defaults.set(params.accountHolder, forKey: "appclip_accountHolder")
    defaults.synchronize()
  }

  static func saveAuth(uid: String, customToken: String) {
    let defaults = shared
    defaults.set(uid, forKey: "appclip_authUid")
    defaults.set(customToken, forKey: "appclip_customToken")
    defaults.set(Date().timeIntervalSince1970, forKey: "appclip_tokenGeneratedAt")
    defaults.synchronize()
  }
}
```

#### 3.4.4 FirebaseAppClipBootstrap.swift (Anonymous Auth)

```swift
import FirebaseAuth

enum FirebaseAppClipBootstrap {
  /// App Clip 내 Anonymous Auth → UID 반환
  static func signInAnonymously() async throws -> String {
    let result = try await Auth.auth().signInAnonymously()
    return result.user.uid
  }
}
```

#### 3.4.5 CustomTokenFetcher.swift

```swift
import FirebaseFunctions

enum CustomTokenFetcher {
  /// Cloud Function `generateCustomToken(uid)` 호출하여 Custom Token 발급
  /// 풀앱이 같은 UID로 Firebase Auth 재진입하도록
  static func fetch(uid: String) async throws -> String {
    let functions = Functions.functions(region: "asia-northeast3")
    let callable = functions.httpsCallable("generateCustomToken")
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
  }
}
```

#### 3.4.6 AppClipCoordinator (통합)

```swift
@MainActor
final class AppClipCoordinator: ObservableObject {
  static let shared = AppClipCoordinator()

  @Published var state: ClipState = .idle

  enum ClipState {
    case idle
    case parsing
    case authenticating
    case ready(OfficeAttributionParams)
    case error(String)
  }

  func handleURL(_ url: URL) async {
    state = .parsing

    // 1. URL 파라미터 추출
    guard let params = URLParameterParser.parse(url) else {
      state = .error("사무실 정보가 올바르지 않습니다")
      return
    }

    // 2. App Group 저장 (풀앱 상속용)
    AppGroupStorage.saveAttribution(params)

    state = .authenticating

    // 3. Anonymous Auth + Custom Token
    do {
      let uid = try await FirebaseAppClipBootstrap.signInAnonymously()
      let customToken = try await CustomTokenFetcher.fetch(uid: uid)
      AppGroupStorage.saveAuth(uid: uid, customToken: customToken)

      state = .ready(params)
    } catch {
      state = .error("인증 실패: \(error.localizedDescription)")
    }
  }
}
```

### 3.5 Cloud Function — generateCustomToken (서버 별도 과제)

**파일**: `functions/src/index.ts` (신규 export)

```typescript
import * as admin from 'firebase-admin';
import * as functions from 'firebase-functions/v2';

/**
 * App Clip → 풀앱 UID 계승용 Custom Token 발급
 * 호출자: App Clip Anonymous Auth UID 확인 후
 * 반환: Firebase Custom Token (1시간 유효)
 */
export const generateCustomToken = functions.https.onCall(
  { region: 'asia-northeast3' },
  async (request) => {
    const callerUid = request.auth?.uid;
    const targetUid = request.data?.uid;

    if (!callerUid) {
      throw new functions.https.HttpsError(
        'unauthenticated',
        '로그인이 필요합니다'
      );
    }
    if (callerUid !== targetUid) {
      throw new functions.https.HttpsError(
        'permission-denied',
        '본인 UID만 요청 가능합니다'
      );
    }

    const token = await admin.auth().createCustomToken(targetUid, {
      source: 'appclip',
    });

    return { token };
  }
);
```

### 3.6 Flutter 풀앱에서 App Group UserDefaults 읽기

Flutter는 `UserDefaults(suiteName:)` 직접 접근 불가 → MethodChannel 경유.

**파일**: `customer_app_flutter/ios/Runner/AppGroupBridge.swift` (신규)

```swift
import Flutter

class AppGroupBridge {
  static let suiteName = "group.com.designated.customer"

  static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(
      name: "com.designated.customer/appgroup",
      binaryMessenger: registrar.messenger()
    )
    channel.setMethodCallHandler { call, result in
      switch call.method {
      case "readAppClipData":
        result(readAppClipData())
      case "clearAppClipData":
        clearAppClipData()
        result(nil)
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }

  static func readAppClipData() -> [String: Any?] {
    let defaults = UserDefaults(suiteName: suiteName)!
    return [
      "provinceId": defaults.string(forKey: "appclip_provinceId"),
      "cityId": defaults.string(forKey: "appclip_cityId"),
      "officeId": defaults.string(forKey: "appclip_officeId"),
      "driverId": defaults.string(forKey: "appclip_driverId"),
      "driverName": defaults.string(forKey: "appclip_driverName"),
      "officePhone": defaults.string(forKey: "appclip_officePhone"),
      "bankName": defaults.string(forKey: "appclip_bankName"),
      "accountNumber": defaults.string(forKey: "appclip_accountNumber"),
      "accountHolder": defaults.string(forKey: "appclip_accountHolder"),
      "authUid": defaults.string(forKey: "appclip_authUid"),
      "customToken": defaults.string(forKey: "appclip_customToken"),
      "tokenGeneratedAt": defaults.double(forKey: "appclip_tokenGeneratedAt"),
    ]
  }

  static func clearAppClipData() {
    let defaults = UserDefaults(suiteName: suiteName)!
    let keys = [
      "appclip_provinceId", "appclip_cityId", "appclip_officeId",
      "appclip_driverId", "appclip_driverName",
      "appclip_officePhone", "appclip_bankName",
      "appclip_accountNumber", "appclip_accountHolder",
      "appclip_authUid", "appclip_customToken", "appclip_tokenGeneratedAt",
    ]
    for key in keys {
      defaults.removeObject(forKey: key)
    }
  }
}
```

**AppDelegate.swift 등록**:

```swift
import Flutter
import UIKit

@UIApplicationMain
@objc class AppDelegate: FlutterAppDelegate {
  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    GeneratedPluginRegistrant.register(with: self)

    // App Group Bridge 등록
    if let controller = window?.rootViewController as? FlutterViewController {
      let registrar = controller.registrar(forPlugin: "AppGroupBridge")!
      AppGroupBridge.register(with: registrar)
    }

    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }
}
```

**Dart 측 소비** (`customer_app_flutter/lib/services/app_clip_migration_service.dart` 신규):

```dart
import 'package:flutter/services.dart';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:flutter/foundation.dart';
import 'dart:io' show Platform;
import '../core/constants/preferences_keys.dart';

class AppClipMigrationService {
  static const _channel = MethodChannel('com.designated.customer/appgroup');
  static const _migrationDoneKey = 'appclip_migration_done';

  /// 풀앱 첫 실행 시 호출.
  /// App Clip에서 넘긴 데이터가 있으면 SharedPreferences + Firebase Auth로 이관.
  /// 한 번만 실행 (migration_done 플래그 체크).
  static Future<bool> migrateIfNeeded(SharedPreferences prefs) async {
    if (!Platform.isIOS) return false;
    if (prefs.getBool(_migrationDoneKey) == true) return false;

    try {
      final Map<dynamic, dynamic> raw =
          await _channel.invokeMethod('readAppClipData');

      final provinceId = raw['provinceId'] as String?;
      final cityId = raw['cityId'] as String?;
      final officeId = raw['officeId'] as String?;
      final customToken = raw['customToken'] as String?;
      final tokenGeneratedAt = (raw['tokenGeneratedAt'] as double?) ?? 0;

      // App Clip 데이터 없으면 스킵 (QR 없이 직접 풀앱 설치)
      if (provinceId == null || cityId == null || officeId == null) {
        await prefs.setBool(_migrationDoneKey, true);
        return false;
      }

      // 1. SharedPreferences 저장 (Kotlin 키 동일)
      await prefs.setString(PreferencesKeys.provinceId, provinceId);
      await prefs.setString(PreferencesKeys.cityId, cityId);
      await prefs.setString(PreferencesKeys.officeId, officeId);
      await prefs.setString(PreferencesKeys.regionId, provinceId);

      for (final (key, prefKey) in [
        ('driverId', PreferencesKeys.driverId),
        ('driverName', PreferencesKeys.driverName),
        ('officePhone', PreferencesKeys.officePhone),
        ('bankName', PreferencesKeys.bankName),
        ('accountNumber', PreferencesKeys.accountNumber),
        ('accountHolder', PreferencesKeys.accountHolder),
      ]) {
        final value = raw[key] as String?;
        if (value != null && value.isNotEmpty) {
          await prefs.setString(prefKey, value);
        }
      }

      // 2. Firebase Auth Custom Token 로그인 (1시간 유효성 체크)
      if (customToken != null) {
        final tokenAge = DateTime.now().millisecondsSinceEpoch / 1000 - tokenGeneratedAt;
        if (tokenAge < 3600) {
          try {
            await FirebaseAuth.instance.signInWithCustomToken(customToken);
            debugPrint('[AppClipMigration] Firebase Auth 계승 성공');
          } on FirebaseAuthException catch (e) {
            debugPrint('[AppClipMigration] Custom Token 로그인 실패: ${e.code}');
            // 실패 시 풀앱에서 Phone Auth 재진행
          }
        } else {
          debugPrint('[AppClipMigration] Token 만료 (${tokenAge.toInt()}초 경과)');
        }
      }

      // 3. App Group 데이터 정리
      await _channel.invokeMethod('clearAppClipData');

      // 4. 마이그레이션 완료 플래그
      await prefs.setBool(_migrationDoneKey, true);

      debugPrint('[AppClipMigration] 완료: $provinceId/$cityId/$officeId');
      return true;
    } on PlatformException catch (e) {
      debugPrint('[AppClipMigration] 실패: ${e.code} ${e.message}');
      return false;
    }
  }
}
```

### 3.7 App Store Connect 설정 (Mac 도착 후)

**App Clip Experience 등록 순서**:
1. App Store Connect → 손님앱 → App Clip → Advanced App Clip Experience
2. URL 패턴: `https://calllink.io.kr/office`
3. Header Image 업로드 (1800×1200)
4. Title + Subtitle 설정 ("사무실 연결 / QR로 즉시 시작")
5. Call-to-Action 선택 ("열기" 또는 "시작")

**Provisioning Profile**:
- 풀앱 Target + App Clip Target 각각 별도 Provisioning Profile 발급
- 둘 다 App Group `group.com.designated.customer` Capability 활성화
- Codemagic에 두 profile 모두 업로드

---

## 4. 의제 5 정합 — fcmToken 재발급

### 4.1 Android 이관 (같은 applicationId)

Kotlin 손님앱 → Flutter 손님앱 업데이트 시:
- 기존 Firestore `customerInfo/{phone}.fcmToken` **그대로 유효** (SDK 호환 유지 시)
- Flutter 첫 실행 시 `onTokenRefresh`가 새 토큰으로 자동 갱신
- `fcmTokenPlatform` 메타는 Flutter 첫 실행 시 `'android'` 값 덮어쓰기 (의제 5)

### 4.2 iOS 신규 출시

- 기존 토큰 없음 → Flutter 첫 실행 시 Phone Auth 완료 후 `customerInfo/{phone}.fcmToken` 신규 저장
- `fcmTokenPlatform: 'ios'` 메타 필드 동시 기록

### 4.3 App Clip → 풀앱 시나리오

- App Clip은 **FCM 토큰 발급 안 함** (APNs 등록 불가, 제약)
- 풀앱 설치 후 첫 실행 시 Flutter가 FCM 토큰 발급 → Firestore 저장
- App Clip에서 미리 저장된 `authUid` + Custom Token 기반 Phone Auth UID 계승 → 같은 `customerInfo/{phone}` 문서에 FCM 토큰 저장

상세 FCM 핸들링은 `flutter/customer_app/MVP/FCM.md` §2 참조 (위탁 예정 문서).

---

## 5. R1 / R2 발동 기준

### 5.1 R1_ATTRIBUTION_ACCURACY (의제 11 측정 기반)

**발동 조건** (둘 중 하나):
- 월간 `office_code_screen_shown` 중 성공률 < 70% (이탈률 > 30%)
- 월간 `office_code_failed` 50건 이상

**발동 시 조치**:
- Phase 2 App Clip Swift 타겟 개발 우선순위 상향
- Mac 도착 + Apple Developer 승인 선행 필수

### 5.2 R2 조치 (Phase 2 완료 후에도 낮은 성공률 지속 시)

- QR 단축 URL 서비스 도입 (`calllink.io.kr/c/ABC123` → 리다이렉트)
- 기사 교육 강화 (QR 생성 주기 + 프린트 관리)
- 고객 온보딩 튜토리얼 (3단계 스와이프)

### 5.3 deferred deep linking 서드파티 도입 조건 (최후 수단)

**조건**: Phase 2 App Clip 도입 + R2 조치 모두 실시 후에도 성공률 < 50%

이 경우에 한해 다음을 비교 재검토:
- Branch.io (유료, 민감 정보 국외 이전 법무 검토 필수)
- AppsFlyer OneLink
- 자체 서버 기반 device fingerprint (정확도 60~70%, iOS 14+ IDFA 제한)

`TRIGGERS.md`에 명시. 현재 Phase 1/2 설계에서는 **도입 배제**.

---

## 6. 위험 신호 + 완화

| 리스크 | 완화 |
|-------|------|
| Phase 1 수동 입력 이탈률 30% 초과 | R1 발동 즉시 Phase 2 App Clip 개발 착수. Mac 도착 후 우선 작업 |
| `office_codes/` 매핑 테이블 확장 부담 (사무실 1만개+ 시) | CF `onOfficeApproved` 자동 생성 + 6자리 영숫자 공간(36^6 = 21억) 충분 |
| 단축 코드 충돌 (10회 재시도 실패) | 10회 후에도 중복이면 8자리로 자동 확장 (관리자 알림) |
| App Clip 10MB 크기 제한 초과 | Swift 타겟 단독 의존성 최소화 (FirebaseCore + FirebaseAuth + FirebaseFunctions만). Flutter/Dart 엔진 미포함 |
| AASA 호스팅 실패 → App Clip 카드 미표시 | Firebase Hosting 배포 자동화 + `curl` 검증 스크립트. Apple CDN 캐시 최대 24시간 대기 필요 |
| Custom Token 만료(1시간) 내 풀앱 설치 안 함 | App Clip에서 `tokenGeneratedAt` 기록. 풀앱 첫 실행 시 만료 체크 → 만료 시 Phone Auth 재진행 |
| bank/account 평문 저장 (PreferencesManager) | Android Kotlin 시점부터 존재한 관례. 금융 정보 **표시용**(입금 안내 텍스트)이며 거래 수단 아님. 암호화 필요성 낮음. 사용자 기기 내 local만 |
| App Clip URL 파라미터 변조 (악성 QR) | Firestore 보안 규칙: `office_codes/{code}` + `offices/{o}` 조회는 읽기 전용. 변조된 `driverId`는 서버 검증 없으면 귀속만 왜곡 (금전 손실 없음) |
| 같은 applicationId 유지 실패 시 Kotlin SharedPreferences 상속 불가 | 의제 2 Bundle ID 결정 시 Android는 **반드시 `com.designated.customer.app` 유지**. 변경 필요 시 MethodChannel 일괄 이관 Helper 별도 작성 |
| iOS SharedPreferences 키 `region_id` 하위호환 의존 | Flutter에서 `regionId` 읽기 로직을 `provinceId`로 fallback 명시 (`getString('province_id') ?? getString('region_id')`) |
| Branch.io 등 서드파티 도입 후 취소 시 데이터 이전 부담 | Phase 1/2 설계에서 **도입 배제**. R2 발동 조건 명시로 판단 지연 방지 |
| CF `generateCustomToken`이 App Check 없는 상태에서 호출 가능 | Phase 1은 App Check 배제(의제 14-E). `request.auth.uid === request.data.uid` 검증으로 자기 UID만 허용 |

---

## 7. Phase 1 / Phase 2 신규·수정 파일 체크리스트

### Phase 1 (Mac 불요, 즉시 착수 가능)

| 파일 | 작업 | 비고 |
|------|------|------|
| `customer_app_flutter/lib/core/constants/preferences_keys.dart` | 신규 | 19개 키 Kotlin 호환 |
| `customer_app_flutter/lib/services/office_code_service.dart` | 신규 | 단축 코드 조회 + SharedPreferences 저장 |
| `customer_app_flutter/lib/screens/office_code_input_screen.dart` | 신규 | 수동 입력 UI |
| `customer_app_flutter/lib/screens/office_code_scan_screen.dart` | 신규 (선택) | QR 카메라 스캔 |
| `customer_app_flutter/lib/main.dart` | 수정 | `_buildInitialRoute` 분기 (office_id 있음/없음) |
| `customer_app_flutter/pubspec.yaml` | 수정 | `mobile_scanner: ^5.2.0` 추가 (선택) |
| `customer_app_flutter/ios/Runner/Info.plist` | 수정 | `NSCameraUsageDescription` 추가 (QR 스캔용) |
| `functions/src/index.ts` | 수정 | CF `onOfficeApproved` → `office_codes/{code}` 자동 생성 |
| Firestore 보안 규칙 | 수정 | `office_codes/{code}` 읽기 공개, 쓰기는 admin CF only |

### Phase 2 (Mac 도착 후)

| 파일 | 작업 | 비고 |
|------|------|------|
| `ios/ClipExtension/ClipExtensionApp.swift` | 신규 | App Clip 진입점 |
| `ios/ClipExtension/ContentView.swift` | 신규 | 사무실 안내 + 콜 요청 옵션 |
| `ios/ClipExtension/URLParameterParser.swift` | 신규 | Kotlin 포팅 |
| `ios/ClipExtension/AppGroupStorage.swift` | 신규 | 공유 UserDefaults |
| `ios/ClipExtension/FirebaseAppClipBootstrap.swift` | 신규 | Anonymous Auth |
| `ios/ClipExtension/CustomTokenFetcher.swift` | 신규 | CF 호출 |
| `ios/ClipExtension/FullAppInstallPrompt.swift` | 신규 | 설치 유도 배너 |
| `ios/ClipExtension/Info.plist` | 신규 | App Clip 권한 선언 |
| `ios/ClipExtension/ClipExtension.entitlements` | 신규 | App Group + aps-environment |
| `ios/Runner/AppGroupBridge.swift` | 신규 | 풀앱 MethodChannel 브리지 |
| `ios/Runner/AppDelegate.swift` | 수정 | AppGroupBridge 등록 |
| `ios/Runner/Runner.entitlements` | 수정 | App Group `group.com.designated.customer` 추가 |
| `customer_app_flutter/lib/services/app_clip_migration_service.dart` | 신규 | 풀앱 첫 실행 시 이관 |
| `customer_app_flutter/lib/main.dart` | 수정 | AppClipMigrationService 호출 삽입 |
| `homepage/public/.well-known/apple-app-site-association` | 신규 | AASA 파일 |
| `firebase.json` | 수정 | Hosting rewrites (`.well-known/*` 경로) |
| `functions/src/index.ts` | 수정 | CF `generateCustomToken` 신규 export |
| App Store Connect | 설정 | App Clip Experience 등록 |
| Apple Developer Console | 설정 | App Clip Target App ID + Provisioning Profile |

---

## 8. 테스트 시나리오

### 8.1 Phase 1 Android (같은 applicationId 유지)

1. Kotlin 앱 설치 → Play Store Install Referrer로 사무실 자동 연결 → `office_id` 저장됨
2. Flutter 앱 업데이트 (같은 applicationId, 높은 versionCode)
3. Flutter 앱 실행 → `_buildInitialRoute`가 `office_id` 존재 확인 → 홈 직행 (수동 입력 건너뜀)
4. Firestore `customerInfo/{phone}` 유지 확인
5. FCM 수신 확인 (`onTokenRefresh` 새 토큰 + `fcmTokenPlatform: 'android'`)

### 8.2 Phase 1 iOS (신규 설치)

1. TestFlight에서 Flutter 앱 설치
2. 첫 실행 → `office_id` 없음 → `OfficeCodeInputScreen` 표시
3. 기사에게 받은 6자리 코드 입력 (예: `GNG123`) → `connectByCode` 호출
4. Firestore `office_codes/GNG123` 조회 → `offices/{o}` 조회
5. 사무실 연결 성공 → Phone Auth 화면 → 홈

### 8.3 Phase 1 iOS (잘못된 코드)

1. 첫 실행 → `OfficeCodeInputScreen`
2. `XYZ999` 입력 (존재하지 않음)
3. `AttributionException("올바른 코드가 아닙니다...")` 예외 → SnackBar
4. 재입력 필드 유지
5. Analytics `office_code_failed` 이벤트 로깅

### 8.4 Phase 2 iOS (App Clip → 풀앱)

1. 기사가 QR 프린트 → 고객 카메라 스캔
2. Safari → App Clip 카드 표시 (AASA 등록됨)
3. "열기" → App Clip 10MB 다운로드 + 실행
4. `URLParameterParser.parse` → 8 파라미터 추출
5. `AppGroupStorage.saveAttribution` → `group.com.designated.customer` 저장
6. `FirebaseAppClipBootstrap.signInAnonymously` → UID 획득
7. `CustomTokenFetcher.fetch(uid:)` → CF 호출 → Custom Token 받음
8. App Clip UI: "사무실 연결됨 · 대리운전 신청" 버튼
9. 고객이 풀앱 설치 유도 배너 탭 → App Store → 설치
10. 풀앱 첫 실행 → `AppClipMigrationService.migrateIfNeeded`
11. `channel.invokeMethod('readAppClipData')` → App Group 읽기
12. SharedPreferences 저장 + `FirebaseAuth.signInWithCustomToken` → 같은 UID 계승
13. Phone Auth 완료 상태 승계 여부 확인 (Anonymous → Phone Auth 링크 필요 시 추가 플로우)
14. 홈 화면 직행

### 8.5 Phase 2 iOS (Custom Token 만료)

1. App Clip 실행 후 1시간 이상 풀앱 미설치
2. 풀앱 첫 실행 → `tokenGeneratedAt` 비교 → 만료 판정
3. SharedPreferences 8 파라미터는 복사 (사무실 연결 OK)
4. Firebase Auth 계승 실패 → Phone Auth 화면 진입
5. Phone Auth 완료 → 홈

---

## 9. 참조

- `flutter/customer_app/PLAN.md` — Phase 1/2 전체 개요
- `flutter/WORKING_DOC.md` §5 의제 10 (손님앱 전환 전략) 결정
- `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md` 검토 포인트 3 — Install Referrer 8 파라미터 분석
- `customer_app/app/src/main/java/com/designated/customer/MainActivity.kt:143-254` — Kotlin `checkInstallReferrer` + `parseAndSaveReferrer` 원본
- `customer_app/app/src/main/java/com/designated/customer/util/PreferencesManager.kt:13-154` — Kotlin 19개 키 상수
- `ios/customer_app/PLAN.md` — Phase 2 App Clip 원본 설계 (재활용)
- `ios/SHARED_LOGIC.md` — Firestore 경로·상태·정산 언어 독립 명세
- `flutter/customer_app/MVP/FCM.md` — 5종 FCM 핸들링 (위탁 예정)
- `flutter/customer_app/MVP/AUTH.md` — Phone Auth + 자격증명 마이그레이션 (위탁 예정)
- `flutter/customer_app/MVP/PHASE2_APPCLIP.md` — Swift App Clip Xcode 프로젝트 구조 상세 (Mac 도착 후 작성)
- Apple App Clip 공식: https://developer.apple.com/documentation/app_clips
- AASA 공식: https://developer.apple.com/documentation/xcode/supporting-associated-domains
- Firebase Custom Token: https://firebase.google.com/docs/auth/admin/create-custom-tokens
- Firebase Hosting `.well-known`: https://firebase.google.com/docs/hosting/full-config#well-known
- mobile_scanner: https://pub.dev/packages/mobile_scanner

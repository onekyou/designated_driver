# 자동로그인 + 인증 (의제 9 기반)

> **의제 9 결정 반영**: Firebase Auth currentUser 자동 상속 시도 → 실패 시 재로그인 + **이메일 사전 채움** + iOS `KeychainAccessibility.first_unlock_this_device`
> **MethodChannel 1회성 마이그레이션**: 옵션 — 중재자 승인 후 적용. MVP 권장 옵션 B (마이그레이션 헬퍼는 선택)
> **원본 기준**: `driver_app/app/src/main/java/com/designated/driverapp/ui/login/LoginViewModel.kt` + `util/SecurePreferencesManager.kt` + `util/SessionManager.kt`
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## 1. 인증 흐름 개요

### 1.1 첫 실행 시 판정 순서

```
main.dart Firebase.initializeApp()
        │
        ▼
1. FirebaseAuth.instance.currentUser 확인
        │
   ┌────┴────┐
   │ != null │ == null
   │         │
   ▼         ▼
2a. 세션    2b. 로그인 화면 진입
   재검증       │
   │            ├── 마지막 이메일 복원 (secure_storage)
   ▼            │   → 이메일 필드 사전 채움
3a. 기사 문서   │
   collectionGroup │
   쿼리 + 검증  │
   │            │
   ▼            ▼
4a. 홈 진입   4b. 사용자 로그인 시도
              │
              ▼
            5. _afterLoginFlow
              ├── pending_drivers 체크
              ├── collectionGroup 쿼리
              ├── approvalStatus 검증
              ├── SharedPreferences 저장
              ├── 이메일 저장 (secure_storage last_email)
              ├── FCM 토큰 확인
              └── 상태 ONLINE + lastLoginTime 업데이트
              │
              ▼
            6. 홈 진입 (LoginState.success)
```

### 1.2 Kotlin 원본 비교

Kotlin 기사앱 (`LoginViewModel.kt:54-68`)은:
- `SecurePreferencesManager.isAutoLoginEnabled()` == true 시
- `getSavedIdentifier()` + `getSavedPassword()` 로드
- **저장된 비밀번호로 자동 login() 실행**

Flutter 의제 9 결정 사항 (AUTH.md 옵션 B):
- **비밀번호 자동 저장 금지** (MVP 범위)
- 마지막 이메일만 `flutter_secure_storage` 에 저장
- Firebase Auth `currentUser` 가 살아있으면 **재로그인 없이 홈 진입**
- `currentUser == null` 이면 이메일 사전 채움 + 사용자 비밀번호 재입력

**근거**: Firebase Auth 토큰은 1시간 후 자동 갱신 (refresh token 60일 유효). 기사앱은 매일 사용하므로 **currentUser가 살아있을 확률 높음** → 재로그인 부담 최소화.

---

## 2. 로그인 (Firebase Auth + collectionGroup 쿼리)

### 2.1 Kotlin 원본 핵심 로직

**파일**: `driver_app/app/src/main/java/com/designated/driverapp/ui/login/LoginViewModel.kt:70-268`

주요 단계:
1. **`signInWithEmailAndPassword`** (line 81-107)
2. **`checkPendingStatusAndProceed(userId)`** (line 154-168) — `pending_drivers/{uid}` 존재 시 "관리자 승인 대기 중" 에러 + signOut
3. **`findDriverDocumentAndSaveInfo(userId)`** (line 170-267) —
   - `collectionGroup("designated_drivers") where authUid == userId` (line 171-174)
   - `approvalStatus == APPROVED` 검증 (line 187-196)
   - SharedPreferences 저장: provinceId/cityId/officeId (line 200-206)
   - 자동로그인 선택 시 `securePreferences.saveAutoLoginCredentials(email, password)` (line 209-213)
   - FCM 토큰 비교 + `needsTokenUpdate` 판정 (line 215-234)
   - 기사 문서 `status: ONLINE` + `lastLoginTime: now()` 업데이트 (line 237-252)

### 2.2 Flutter 매핑 (NOTIFIERS.md §3 참조)

**NOTIFIERS.md §3.2 LoginNotifier 전체 코드 참조**. 여기서는 Firestore 경로 추출 + collectionGroup 쿼리 결과 파싱 핵심만 재기술.

#### 2.2.1 collectionGroup 쿼리

```dart
final querySnap = await firestore
    .collectionGroup('designated_drivers')
    .where('authUid', isEqualTo: userId)
    .limit(1)
    .get();

if (querySnap.docs.isEmpty) {
  state = const LoginState.error('등록되지 않은 기사 계정입니다.');
  await auth.signOut();
  return;
}

final doc = querySnap.docs.first;
```

#### 2.2.2 경로 파싱 (provinceId/cityId/officeId 추출)

**중요**: Kotlin 원본 (`LoginViewModel.kt:179-181`) 은 `documentSnapshot.getString("provinceId")` 로 **문서 내부 필드**에서 읽습니다. 즉 **기사 문서에 provinceId/cityId/officeId 필드가 중복 저장**되어 있음.

**이유**: `collectionGroup` 쿼리 결과는 **문서 레퍼런스 경로는 있지만** Kotlin SDK는 **경로 세그먼트 파싱 유틸 부재**. 그래서 중복 저장 + 문서 필드 읽기 선택.

**Flutter 권장**: **문서 경로 세그먼트 파싱**도 병용하여 안전성 증대.

```dart
// Method A: 문서 필드 읽기 (Kotlin과 100% 호환)
final data = doc.data();
final provinceId = data['provinceId'] as String?;
final cityId = data['cityId'] as String?;
final officeId = data['officeId'] as String?;

// Method B: 문서 경로 세그먼트 파싱 (fallback 또는 검증용)
// path: provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}
final segments = doc.reference.path.split('/');
// segments: ['provinces', p, 'cities', c, 'offices', o, 'designated_drivers', uid]
final provinceIdFromPath = segments.length >= 7 ? segments[1] : null;
final cityIdFromPath = segments.length >= 7 ? segments[3] : null;
final officeIdFromPath = segments.length >= 7 ? segments[5] : null;

// 두 값이 일치해야 정상. 불일치 시 로그 + 경로 우선 사용
final effectiveProvinceId = provinceId ?? provinceIdFromPath;
final effectiveCityId = cityId ?? cityIdFromPath;
final effectiveOfficeId = officeId ?? officeIdFromPath;
```

#### 2.2.3 approvalStatus 검증

```dart
final approvalStatus = data['approvalStatus'] as String?;
if (approvalStatus != 'APPROVED') {
  final msg = switch (approvalStatus) {
    'PENDING' => '관리자 승인 대기 중인 계정입니다.',
    'REJECTED' => '가입이 거절된 계정입니다. 관리자에게 문의하세요.',
    _ => '계정 상태를 확인할 수 없습니다. 관리자에게 문의하세요.',
  };
  state = LoginState.error(msg);
  await auth.signOut();
  return;
}
```

#### 2.2.4 FCM 토큰 비교

```dart
final serverFcmToken = data['fcmToken'] as String?;
final localFcmToken = await messaging.getToken();
final needsUpdate = serverFcmToken == null ||
    serverFcmToken.isEmpty ||
    serverFcmToken != localFcmToken;

// needsUpdate == true 시 FcmService 에서 갱신 (의제 5 fcmTokenPlatform 포함)
```

#### 2.2.5 기사 상태 ONLINE + 타임스탬프

```dart
await doc.reference.update({
  'status': 'ONLINE',
  'lastLoginTime': Timestamp.now(),
});
```

---

## 3. 자동로그인 (Firebase Auth currentUser 자동 상속)

### 3.1 main.dart 초기화

```dart
// lib/main.dart

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);

  // 의제 11: Crashlytics 초기화 (OVERVIEW.md Week 0~1)
  FlutterError.onError = FirebaseCrashlytics.instance.recordFlutterFatalError;
  PlatformDispatcher.instance.onError = (error, stack) {
    FirebaseCrashlytics.instance.recordError(error, stack, fatal: true);
    return true;
  };

  runApp(const ProviderScope(child: DriverApp()));
}
```

### 3.2 LoginNotifier `_initAutoLogin()` 확장

NOTIFIERS.md §3.2 `_initAutoLogin()` 메서드를 **의제 9 결정으로 세부화**:

```dart
Future<void> _initAutoLogin() async {
  // Step 1: Firebase Auth currentUser 확인
  final currentUser = auth.currentUser;
  if (currentUser != null) {
    debugPrint('[_initAutoLogin] Firebase Auth 세션 유지됨, uid=${currentUser.uid}');

    // Step 2: 세션 재검증 (collectionGroup 쿼리)
    try {
      await _afterLoginFlow(currentUser.uid, emailFallback: currentUser.email ?? '');
      return;  // Success → LoginState.success 설정됨, 홈으로 네비게이트
    } catch (e) {
      debugPrint('[_initAutoLogin] 세션 검증 실패: $e');
      // 네트워크 오류면 유지, 그 외는 signOut
      if (e is FirebaseException && _isNetworkError(e)) {
        // 오프라인 로그인 시도 (Kotlin attemptOfflineLogin)
        final session = await SessionStorage().load();
        if (session != null &&
            session.userId == currentUser.uid &&
            !session.isExpired) {
          state = LoginState.success(
            provinceId: session.provinceId,
            cityId: session.cityId,
            officeId: session.officeId,
            driverId: session.driverId,
            needsTokenUpdate: false,
          );
          return;
        }
      } else {
        await auth.signOut();
      }
    }
  }

  // Step 3: 세션 만료 또는 currentUser==null — 로그인 화면 준비
  final savedEmail = await secureStorage.read(key: 'last_email');
  if (savedEmail != null && savedEmail.isNotEmpty) {
    _form = _form.copyWith(email: savedEmail);
    debugPrint('[_initAutoLogin] 마지막 이메일 복원: $savedEmail');
  }

  final autoLoginStr = await secureStorage.read(key: 'auto_login');
  _form = _form.copyWith(autoLogin: autoLoginStr == 'true');

  state = const LoginState.idle();
}
```

### 3.3 UX 시나리오

| 시나리오 | currentUser | 동작 | 사용자 경험 |
|---------|------------|-----|-----------|
| **로그인 직후 앱 재실행** | 유효 (< 1시간) | 자동 홈 진입 | 로그인 화면 안 보임 |
| **며칠 후 앱 실행** | 갱신 성공 (refresh token 유효) | 자동 홈 진입 | 로그인 화면 안 보임 |
| **60일 초과 / refresh token 만료** | null | 로그인 화면 + 이메일 사전 채움 | "비밀번호 입력" |
| **네트워크 오프라인** | 유효한 currentUser 존재 | `_afterLoginFlow` 실패 → SessionStorage 폴백 | 이전 세션 사용 |
| **네트워크 오프라인 + currentUser null** | null | 로그인 화면 | "오프라인" 안내 |
| **로그아웃 후 재실행** | null | 로그인 화면 + 이메일 복원 | "비밀번호 입력" |
| **다른 계정으로 로그인 시도** | 이전 uid | signOut → 새 로그인 | 새 계정 사용 |

### 3.4 refresh token 만료 시점

Firebase Auth refresh token 정책:
- **60일간 미사용 시 무효화**
- 로그인 후 앱 1회라도 실행하면 갱신
- **기사앱은 매일 사용 → 만료 사례 거의 없음** (의제 9 결정 근거)

---

## 4. 마이그레이션 헬퍼 (선택, 옵션)

### 4.1 옵션 판단

MODELS.md 의제 9 답변에서 권장한 **옵션 B** (Firebase Auth currentUser 자동 상속) 선택 시 **MethodChannel 마이그레이션은 불필요**. Firebase Auth SDK가 패키지명 동일 (`com.designated.driverapp.app`) 하에서 세션 자동 상속.

**MethodChannel 마이그레이션이 필요한 케이스** (옵션 A, 강화안):
- 기사가 마지막 로그인 후 60일 초과 → refresh token 만료 → currentUser null
- 이때 Kotlin `secure_prefs.xml` 의 저장된 비밀번호를 Flutter 측에 전달해 **자동 재로그인**

**중재자 판단 요청**: 옵션 A 채택 시에만 §4.2~4.5 구현. MVP 기본은 옵션 B (§4 전체 skip).

### 4.2 Android Kotlin Helper (`driver_app_flutter/android/app/src/main/kotlin/.../MigrationHelper.kt`)

```kotlin
package com.designated.driverapp.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.flutter.plugin.common.MethodChannel

object MigrationHelper {
    private const val LEGACY_PREFS_NAME = "secure_prefs"
    private const val KEY_AUTO_LOGIN = "auto_login"
    private const val KEY_IDENTIFIER = "identifier"
    private const val KEY_PASSWORD = "password"

    fun readLegacyCredentials(context: Context, result: MethodChannel.Result) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            val prefs = EncryptedSharedPreferences.create(
                context,
                LEGACY_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val autoLogin = prefs.getBoolean(KEY_AUTO_LOGIN, false)
            val identifier = prefs.getString(KEY_IDENTIFIER, null)
            val password = prefs.getString(KEY_PASSWORD, null)

            if (autoLogin && !identifier.isNullOrBlank() && !password.isNullOrBlank()) {
                result.success(mapOf(
                    "identifier" to identifier,
                    "password" to password,
                ))
            } else {
                result.success(null)
            }
        } catch (e: Exception) {
            // 파일 없음 또는 Keystore 오류 → null 반환 (silent fallback)
            result.success(null)
        }
    }

    /// 마이그레이션 완료 후 원본 삭제
    fun clearLegacyCredentials(context: Context, result: MethodChannel.Result) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context, LEGACY_PREFS_NAME, masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().clear().apply()
            result.success(true)
        } catch (e: Exception) {
            result.success(false)
        }
    }
}
```

### 4.3 MainActivity.kt MethodChannel 등록

```kotlin
// driver_app_flutter/android/app/src/main/kotlin/com/designated/driverapp/MainActivity.kt

package com.designated.driverapp

import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.designated.driverapp.app.MigrationHelper

class MainActivity: FlutterActivity() {
    private val MIGRATION_CHANNEL = "com.designated.driverapp/migration"

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, MIGRATION_CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "readLegacyCredentials" -> MigrationHelper.readLegacyCredentials(applicationContext, result)
                    "clearLegacyCredentials" -> MigrationHelper.clearLegacyCredentials(applicationContext, result)
                    else -> result.notImplemented()
                }
            }
    }
}
```

### 4.4 Dart 측 호출 (`driver_app_flutter/lib/services/migration_service.dart`)

```dart
import 'dart:io';
import 'package:flutter/services.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

class MigrationService {
  static const _channel = MethodChannel('com.designated.driverapp/migration');
  static const _migrationFlagKey = 'kotlin_migration_done_v1';

  final FlutterSecureStorage secureStorage;
  final SharedPreferences prefs;

  MigrationService({required this.secureStorage, required this.prefs});

  /// 앱 첫 실행 시 1회 호출 — main.dart 또는 LoginNotifier._initAutoLogin() 시작 시점
  Future<bool> tryMigrateFromKotlin() async {
    // iOS는 migration 불필요 (기존 Kotlin 앱 없음 — Android 전용)
    if (!Platform.isAndroid) return false;

    // 이미 migration 완료 체크
    final done = prefs.getBool(_migrationFlagKey) ?? false;
    if (done) return false;

    try {
      // 이미 Flutter Secure Storage에 credential 있으면 skip
      final existingEmail = await secureStorage.read(key: 'last_email');
      if (existingEmail != null && existingEmail.isNotEmpty) {
        await prefs.setBool(_migrationFlagKey, true);
        return false;
      }

      // Kotlin 측 legacy 자격증명 읽기
      final result = await _channel.invokeMethod<Map<dynamic, dynamic>>('readLegacyCredentials');
      if (result == null) {
        // 자격증명 없음 — migration flag만 set
        await prefs.setBool(_migrationFlagKey, true);
        return false;
      }

      final identifier = result['identifier'] as String?;
      final password = result['password'] as String?;

      if (identifier != null && password != null &&
          identifier.isNotEmpty && password.isNotEmpty) {
        // Flutter Secure Storage에 저장 (옵션 A 채택 시에만)
        await secureStorage.write(key: 'last_email', value: identifier);
        // ⚠️ 옵션 A: 비밀번호도 저장 (의제 9 옵션 B 채택 시 이 줄 제거)
        await secureStorage.write(key: 'migrated_password', value: password);

        // Kotlin 원본 삭제
        await _channel.invokeMethod('clearLegacyCredentials');

        await prefs.setBool(_migrationFlagKey, true);
        debugPrint('[Migration] Kotlin 자격증명 이관 완료');
        return true;
      }

      await prefs.setBool(_migrationFlagKey, true);
      return false;
    } catch (e) {
      // silent fallback — 마이그레이션 실패해도 앱은 정상 동작
      debugPrint('[Migration] 실패 (무시): $e');
      return false;
    }
  }
}
```

### 4.5 iOS는 migration 없음

iOS는 **기존 Kotlin 앱이 없으므로** migration 대상 없음. `Platform.isAndroid` 체크로 완전 skip.

---

## 5. iOS Keychain 설정 (의제 9)

### 5.1 flutter_secure_storage iOS 옵션

**의제 9 결정**: `KeychainAccessibility.first_unlock_this_device` — 잠금화면 FCM 수신 시 토큰 조회 가능 + 기기 간 백업 금지.

```dart
// lib/core/providers.dart (또는 service locator)

final secureStorageProvider = Provider<FlutterSecureStorage>((_) {
  return const FlutterSecureStorage(
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
      groupId: null,  // Phase 2 App Group 사용 시 업데이트 (의제 13)
    ),
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,  // AES-256-GCM (Kotlin과 유사)
      keyCipherAlgorithm: KeyCipherAlgorithm.RSA_ECB_PKCS1Padding,
      storageCipherAlgorithm: StorageCipherAlgorithm.AES_GCM_NoPadding,
    ),
  );
});
```

### 5.2 Accessibility 옵션 비교

| 옵션 | 설명 | 적용 케이스 |
|------|------|-----------|
| `passcode` | 기기 passcode 설정 시만 저장. 백업 금지 | 최고 보안 (민감 데이터) |
| `unlocked` | 잠금 해제 시만 접근 | **배차 알림 시 접근 불가** → 부적합 |
| **`first_unlock`** | **첫 unlock 이후 항상 접근 (기기 재부팅 후 최초 unlock 전에는 접근 불가)** | **의제 9 선택** |
| `first_unlock_this_device` | `first_unlock` + 기기 간 백업 금지 | 의제 9 선택 (더 엄격) |
| `always` | iOS 13+ deprecated | 비권장 |

**선택 근거**: 재부팅 직후 잠금화면 상태에서 FCM 수신 시 **첫 unlock 전**에는 **auth 토큰 읽기 불가** → 로그인 재요구. 이는 의도된 보안 동작. 일반 사용 (1회 unlock 후) 에서는 백그라운드 FCM 수신 + 토큰 조회 모두 정상 동작.

### 5.3 Info.plist 추가 설정 (배경)

**의제 6 Privacy Manifest 보완**: Keychain 사용 자체는 Info.plist 사유 문자열 불필요 (Apple 표준 API). `PrivacyInfo.xcprivacy` 에는 `NSPrivacyAccessedAPICategoryUserDefaults` 카테고리 기록 필요 (의제 6 결정).

---

## 6. 로그인 화면 UX (의제 9)

### 6.1 화면 구조 (SCREENS.md에서 상세)

```
┌─────────────────────────────────┐
│   [콜마당 로고]                 │
│                                 │
│   ┌─────────────────────────┐   │
│   │ 이메일 (사전 채움됨)    │   │  ← secure_storage 'last_email'
│   └─────────────────────────┘   │
│   ┌─────────────────────────┐   │
│   │ 비밀번호                │   │
│   └─────────────────────────┘   │
│   ☑ 자동 로그인                 │
│                                 │
│   [      로그인      ]          │
│                                 │
│   [ 비밀번호를 잊으셨나요? ]   │  ← sendPasswordResetEmail
│   [ 회원가입 ]                  │
└─────────────────────────────────┘
```

### 6.2 이메일 사전 채움 동작

```dart
class LoginScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  late final TextEditingController _emailController;
  late final TextEditingController _passwordController;

  @override
  void initState() {
    super.initState();
    _emailController = TextEditingController();
    _passwordController = TextEditingController();

    // LoginNotifier.form 에 이미 복원된 이메일 채움
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final form = ref.read(loginProvider.notifier).form;
      _emailController.text = form.email;
    });
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final loginState = ref.watch(loginProvider);

    // side effect: Success 시 홈으로 네비게이트
    ref.listen<LoginState>(loginProvider, (previous, next) {
      next.whenOrNull(
        success: (provinceId, cityId, officeId, driverId, needsTokenUpdate) {
          context.goNamed('home');
        },
        error: (msg) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text(msg)),
          );
        },
      );
    });

    return Scaffold(
      // TextField, 자동로그인 Switch, 로그인 버튼 등
    );
  }
}
```

### 6.3 비밀번호 찾기 다이얼로그

```dart
Future<void> _showPasswordResetDialog(BuildContext context) async {
  final emailController = TextEditingController(
    text: ref.read(loginProvider.notifier).form.email,  // 사전 채움
  );

  await showDialog<void>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: const Text('비밀번호 재설정'),
      content: TextField(
        controller: emailController,
        keyboardType: TextInputType.emailAddress,
        decoration: const InputDecoration(labelText: '이메일'),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx),
          child: const Text('취소'),
        ),
        FilledButton(
          onPressed: () async {
            final email = emailController.text.trim();
            if (email.isEmpty) return;
            await ref.read(loginProvider.notifier).sendPasswordResetEmail(email);
            if (!ctx.mounted) return;
            Navigator.pop(ctx);
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(content: Text('$email 로 재설정 이메일을 전송했습니다.')),
            );
          },
          child: const Text('전송'),
        ),
      ],
    ),
  );
}
```

### 6.4 자동로그인 Switch

```dart
Switch(
  value: form.autoLogin,
  onChanged: (v) {
    ref.read(loginProvider.notifier).toggleAutoLogin(v);
    setState(() {});  // Switch UI 갱신
  },
),
```

**주의**: 의제 9 옵션 B 선택 시 `autoLogin = true` 플래그는 **Firebase Auth currentUser 유지 여부와 무관하게 이메일만 저장** 용도. 비밀번호는 저장 안 함.

---

## 7. 회원가입 + 승인 대기

### 7.1 Kotlin 원본 (`SignUpViewModel.kt`)

구조:
- 지역(province) → 도시(city) → 사무실(office) 3단계 드롭다운
- 이름, 전화번호, 이메일, 비밀번호, 비밀번호 확인 입력
- `createUserWithEmailAndPassword(email, password)`
- `pending_drivers/{uid}` 문서 생성 (provinceId, cityId, officeId, name, phone, approvalStatus=PENDING)
- 매니저 앱에서 승인 → `designated_drivers/{uid}` 복사 + approvalStatus=APPROVED

### 7.2 Flutter 매핑 (NOTIFIERS.md §6 참조)

```dart
// SignUpNotifier (NOTIFIERS.md §6에서 골격 제시)

Future<void> signUp({
  required String email,
  required String password,
  required String name,
  required String phone,
  required String provinceId,
  required String cityId,
  required String officeId,
}) async {
  state = const SignUpState.loading();
  try {
    final credential = await auth.createUserWithEmailAndPassword(
      email: email, password: password,
    );
    final uid = credential.user?.uid;
    if (uid == null) throw StateError('UID 누락');

    await firestore.collection('pending_drivers').doc(uid).set({
      'authUid': uid,
      'email': email,
      'name': name,
      'phone': phone,
      'provinceId': provinceId,
      'cityId': cityId,
      'officeId': officeId,
      'approvalStatus': 'PENDING',
      'createdAt': FieldValue.serverTimestamp(),
    });

    state = const SignUpState.success();
  } on FirebaseAuthException catch (e) {
    final msg = switch (e.code) {
      'email-already-in-use' => '이미 사용 중인 이메일입니다.',
      'invalid-email' => '올바른 이메일 형식이 아닙니다.',
      'weak-password' => '비밀번호가 너무 약합니다. 6자 이상 입력해주세요.',
      _ => e.message ?? '회원가입 실패',
    };
    state = SignUpState.error(msg);
  } catch (e) {
    state = SignUpState.error('회원가입 실패: $e');
  }
}
```

### 7.3 지역/도시/사무실 드롭다운 로딩

```dart
Future<List<ProvinceItem>> loadProvinces() async {
  final snap = await firestore.collection('provinces').get();
  return snap.docs
      .map((d) => ProvinceItem(id: d.id, name: d.data()['name'] as String? ?? d.id))
      .toList()
    ..sort((a, b) => a.name.compareTo(b.name));
}

Future<List<CityItem>> loadCities(String provinceId) async {
  final snap = await firestore
      .collection('provinces').doc(provinceId)
      .collection('cities')
      .get();
  return snap.docs
      .map((d) => CityItem(id: d.id, name: d.data()['name'] as String? ?? d.id))
      .toList()
    ..sort((a, b) => a.name.compareTo(b.name));
}

Future<List<OfficeItem>> loadOffices(String provinceId, String cityId) async {
  final snap = await firestore
      .collection('provinces').doc(provinceId)
      .collection('cities').doc(cityId)
      .collection('offices')
      .get();
  return snap.docs
      .map((d) => OfficeItem(id: d.id, name: d.data()['name'] as String? ?? d.id))
      .toList()
    ..sort((a, b) => a.name.compareTo(b.name));
}
```

### 7.4 회원가입 완료 후 UX

```dart
ref.listen<SignUpState>(signUpProvider, (_, next) {
  next.whenOrNull(
    success: () {
      showDialog<void>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('가입 신청 완료'),
          content: const Text(
            '관리자 승인 후 로그인할 수 있습니다.\n승인까지 시간이 걸릴 수 있습니다.',
          ),
          actions: [
            FilledButton(
              onPressed: () {
                Navigator.pop(ctx);
                context.goNamed('login');
              },
              child: const Text('로그인 화면으로'),
            ),
          ],
        ),
      );
    },
    error: (msg) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    },
  );
});
```

---

## 8. 비밀번호 찾기

### 8.1 Kotlin 원본 (`ui/login/ForgotPasswordScreen.kt`)

**간단한 이메일 입력 화면 + `Firebase.auth.sendPasswordResetEmail(email)` 호출**. 세부 UX는 Kotlin 원본 상세 확인 필요 (본 문서에선 핵심 API만).

### 8.2 Flutter 매핑

**§6.3 다이얼로그 방식** 이미 제시. 별도 전용 화면은 **선택** — MVP 범위 밖.

```dart
// LoginNotifier
Future<void> sendPasswordResetEmail(String email) async {
  try {
    await auth.sendPasswordResetEmail(email: email);
  } on FirebaseAuthException catch (e) {
    state = LoginState.error(switch (e.code) {
      'user-not-found' => '등록되지 않은 이메일입니다.',
      'invalid-email' => '올바른 이메일 형식이 아닙니다.',
      _ => e.message ?? '이메일 전송 실패',
    });
  }
}
```

### 8.3 UX 고려사항

- Firebase Auth가 `user-not-found` 를 반환하는지는 **이메일 열거 공격 방지** 목적으로 **false를 반환하지 않음**
- 사용자에게는 "등록된 이메일이면 재설정 링크를 전송했습니다" 통일 메시지 권장

---

## 9. 로그아웃 + cleanup

### 9.1 Kotlin 원본 (`LoginViewModel.kt:282-291`)

```kotlin
fun logout() {
    auth.signOut()
    sessionManager.clearSession()
    // 자동로그인 자격증명은 유지 (재실행 시 자동로그인 위해)
    // pending FCM 토큰 제거 (다른 계정 로그인 시 혼선 방지)
    sharedPreferences.edit()
        .remove(Constants.PREF_KEY_PENDING_FCM_TOKEN)
        .apply()
    _loginState.value = LoginState.Idle
}
```

**핵심**:
- `signOut()` + `sessionManager.clearSession()` + pending FCM 토큰 제거
- 자동로그인 자격증명은 **유지** (다시 로그인할 때 편의 유지)

### 9.2 Flutter 매핑

```dart
// LoginNotifier
Future<void> logout() async {
  // 1. Firebase Auth 로그아웃
  await auth.signOut();

  // 2. SessionStorage clear (캐시된 세션)
  await SessionStorage().clear();

  // 3. pending FCM 토큰 제거
  await prefs.remove('pref_pending_fcm_token');

  // 4. PresenceManager offline 설정 (의제 8)
  await ref.read(presenceServiceProvider).onLogout();

  // 5. Notifier 상태 초기화
  state = const LoginState.idle();

  // 6. 자동로그인 자격증명 유지 여부 — 의제 9 옵션 B 시 이메일만 유지
  //    autoLogin 토글 OFF 된 경우만 이메일도 삭제 (옵션)
  final autoLoginStr = await secureStorage.read(key: 'auto_login');
  if (autoLoginStr != 'true') {
    await secureStorage.delete(key: 'last_email');
  }
}
```

### 9.3 추가 Notifier 초기화

로그아웃 시 **다른 Notifier 상태도 초기화 필요**:

```dart
// SettingsScreen 등의 로그아웃 버튼 onTap
Future<void> _onLogoutTap() async {
  await ref.read(loginProvider.notifier).logout();

  // DriverWorkflowNotifier.authStateChanges 리스너가 자동으로
  // state = const DriverScreenUiState() 초기화 (NOTIFIERS.md §2.2)
  // CarryOverNotifier 도 동일

  if (mounted) context.goNamed('login');
}
```

### 9.4 Firestore 기사 상태 OFFLINE 업데이트

**Kotlin 원본은 명시적으로 OFFLINE 업데이트 안 함** (PresenceManager `onDisconnect` 가 자동 처리). Flutter도 동일:

```dart
// PresenceService (기존) — onLogout 호출 시 Realtime DB 명시 offline + Firestore status 갱신
Future<void> onLogout() async {
  await _database
      .ref('presence/drivers/$_userId')
      .set({'status': 'offline', 'lastSeen': ServerValue.timestamp});
  // Firestore status 는 CF 스케줄러 `checkAssignedTimeout` 가 자동 처리
}
```

---

## 10. 위험 신호 (구현 전 체크)

### 10.1 Firebase Auth SDK 호환 (Kotlin ↔ Flutter)

**현재 상태**:
- Kotlin `firebase-auth-ktx` (firebase-bom 33.7.0)
- Flutter `firebase_auth: ^5.3.1` (iOS SDK 11+)

**검증 필요**:
1. **같은 Firebase 프로젝트에서 Android Kotlin 앱 세션 → Flutter 앱 상속 성공 여부**
   - 두 SDK 모두 같은 Firebase Auth REST API 사용 → refresh token 호환 기대
   - **실측**: Phase 6 Week 0~1 중 테스트 기기(SM-G996N R3CR312MB1L)에서 Kotlin 앱 로그인 → Flutter 앱 업그레이드 → currentUser 확인
2. **토큰 저장 경로 호환**
   - Android: `/data/data/com.designated.driverapp.app/shared_prefs/com.google.firebase.auth.api.Store.{projectId}.xml`
   - Flutter Android: 동일 경로 (firebase_auth iOS SDK가 동일 파일 사용)
   - **applicationId 동일** 조건 필수 (의제 2 결정 대기)

### 10.2 마이그레이션 실패율 추정

**옵션 A 채택 시** (§4 MethodChannel):
- 작업 5가지 단계 각각 실패 가능성:
  1. EncryptedSharedPreferences 읽기 (AndroidKeyStore 오류) — 5%
  2. 자격증명 파싱 — 1%
  3. Flutter Secure Storage 쓰기 — 1%
  4. Kotlin 원본 삭제 — 2%
  5. migration_done 플래그 저장 — 0.5%
- 누적 실패율: **~10%** (보수적 추정)
- **옵션 B는 이 마이그레이션 자체를 skip → 0% 실패**

### 10.3 iOS accessibility 선택 오류

**잘못된 옵션 선택 시**:
- `unlocked` 선택 시 **잠금화면 FCM 수신 → 토큰 읽기 실패 → 배차 수락 트랜잭션 실패**
- 의제 9 결정 `first_unlock_this_device` 를 반드시 사용. 실수 방지 위해 Provider 정의에 hardcoded

### 10.4 autoLogin 플래그와 secureStorage 읽기 실패

**시나리오**: secureStorage 가 iOS 에서 **첫 unlock 전** 에 접근 시도 → 읽기 실패 → `autoLogin = false` 취급
**대응**: 앱 lifecycle 이 **ActiveState** 일 때만 `_initAutoLogin()` 호출 (ScenePhase 활용):

```dart
class _DriverAppState extends ConsumerState<DriverApp> with WidgetsBindingObserver {
  bool _autoLoginTriggered = false;

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed && !_autoLoginTriggered) {
      _autoLoginTriggered = true;
      ref.read(loginProvider.notifier);  // init 트리거 (지연 평가)
    }
  }
}
```

### 10.5 로그아웃 후 재로그인 flow 검증

**체크리스트**:
1. `logout()` → `signOut()` → `currentUser == null` 확인
2. SessionStorage clear → `SessionStorage().load() == null` 확인
3. 다시 `login()` → 성공 → SessionStorage 재저장 확인
4. CarryOverNotifier / DriverWorkflowNotifier 가 authStateChanges 로 자동 재시작 확인

### 10.6 관리자 승인 대기 시나리오 UX

**Kotlin 원본**: `LoginViewModel.kt:159` `_loginState.value = LoginState.Error("관리자 승인 대기 중인 계정입니다.")` + `auth.signOut()`

**Flutter 개선안** (옵션):
- 승인 대기 전용 화면 추가
- "승인 상태 새로고침" 버튼 (Pull-to-refresh)
- 매일 1회 자동 체크 (local notification 예약)

**MVP 범위**: **Kotlin과 동일** (에러 메시지만 + signOut). 개선은 R2 후보.

---

## 11. 테스트 전략

### 11.1 단위 테스트 (`test/features/auth/`)

```dart
// test/features/auth/login_notifier_test.dart

void main() {
  group('LoginNotifier', () {
    late ProviderContainer container;
    late MockFirebaseAuth auth;
    late FakeFirebaseFirestore firestore;
    late MockFlutterSecureStorage secureStorage;

    setUp(() {
      auth = MockFirebaseAuth();
      firestore = FakeFirebaseFirestore();
      secureStorage = MockFlutterSecureStorage();
      container = ProviderContainer(overrides: [
        firebaseAuthProvider.overrideWithValue(auth),
        firestoreProvider.overrideWithValue(firestore),
        secureStorageProvider.overrideWithValue(secureStorage),
      ]);
    });

    tearDown(() => container.dispose());

    test('login 성공 - pending_drivers 없음 + approvalStatus=APPROVED', () async {
      // Arrange
      final user = MockUser(uid: 'driver1', email: 'test@test.com');
      when(() => auth.signInWithEmailAndPassword(email: any(named: 'email'), password: any(named: 'password')))
          .thenAnswer((_) async => MockUserCredential(user));

      await firestore.collection('provinces').doc('gg')
          .collection('cities').doc('hongchun')
          .collection('offices').doc('office1')
          .collection('designated_drivers').doc('driver1')
          .set({
        'authUid': 'driver1',
        'approvalStatus': 'APPROVED',
        'provinceId': 'gg',
        'cityId': 'hongchun',
        'officeId': 'office1',
        'name': '김기사',
        'fcmToken': 'token1',
      });

      final notifier = container.read(loginProvider.notifier);
      notifier.setEmail('test@test.com');
      notifier.setPassword('pw1234');

      // Act
      await notifier.login();

      // Assert
      final state = container.read(loginProvider);
      expect(state, isA<_Success>());
      state.whenOrNull(success: (provinceId, cityId, officeId, driverId, _) {
        expect(provinceId, 'gg');
        expect(cityId, 'hongchun');
        expect(officeId, 'office1');
        expect(driverId, 'driver1');
      });
    });

    test('login 실패 - pending_drivers 존재', () async {
      when(() => auth.signInWithEmailAndPassword(email: any(named: 'email'), password: any(named: 'password')))
          .thenAnswer((_) async => MockUserCredential(MockUser(uid: 'driver1')));

      await firestore.collection('pending_drivers').doc('driver1')
          .set({'authUid': 'driver1', 'approvalStatus': 'PENDING'});

      final notifier = container.read(loginProvider.notifier);
      notifier.setEmail('test@test.com');
      notifier.setPassword('pw1234');

      await notifier.login();

      final state = container.read(loginProvider);
      expect(state, isA<_Error>());
      state.whenOrNull(error: (msg) {
        expect(msg, contains('승인 대기'));
      });
    });

    test('login 실패 - approvalStatus REJECTED', () async {
      // 유사 구조 생략
    });

    test('오프라인 로그인 fallback', () async {
      when(() => auth.signInWithEmailAndPassword(email: any(named: 'email'), password: any(named: 'password')))
          .thenThrow(FirebaseAuthException(code: 'network-request-failed'));

      // SessionStorage pre-seed
      await SharedPreferences.setMockInitialValues({
        'user_session_json': '{"userId":"driver1","email":"test@test.com",...}',
      });

      // Act + Assert: offline login success
    });
  });
}
```

### 11.2 통합 테스트

Firebase Emulator 기반:
- Auth Emulator `firebase emulators:start --only auth,firestore`
- `auth.connectAuthEmulator('localhost', 9099)` 설정
- 실제 로그인 → 재시작 → currentUser 상속 verify

### 11.3 수동 QA 체크리스트

| # | 시나리오 | 기대 동작 |
|---|---------|----------|
| 1 | 첫 설치 → 로그인 | 정상 홈 진입 |
| 2 | 앱 종료 → 1시간 내 재실행 | 로그인 화면 안 보임 |
| 3 | 앱 종료 → 60일 후 재실행 | 로그인 화면 + 이메일 사전 채움 |
| 4 | 로그아웃 → 재실행 | 로그인 화면 + 이메일 복원 |
| 5 | 로그아웃 → autoLogin OFF → 재실행 | 로그인 화면 + 이메일 비어있음 |
| 6 | 승인 대기 계정 로그인 | 에러 메시지 + 로그인 화면 유지 |
| 7 | 거절된 계정 로그인 | "가입 거절" 에러 |
| 8 | 오프라인 상태 + 캐시된 세션 존재 | 오프라인 로그인 성공 |
| 9 | 오프라인 상태 + 캐시 없음 | "오프라인" 에러 |
| 10 | iOS 재부팅 직후 잠금상태 FCM 수신 | 첫 unlock 전에는 토큰 접근 불가 (정상) |

---

## 12. Phase 6 작업 순서

### Week 1
1. `lib/features/auth/notifiers/login_notifier.dart` 작성 (§2, §3 참조)
2. `lib/services/session_storage.dart` (MODELS.md §4 UserSession 사용)
3. 로그인 화면 이메일 사전 채움 + 비밀번호 찾기 다이얼로그 (§6.2, §6.3)
4. `flutter_secure_storage` iOS `KeychainAccessibility.first_unlock_this_device` 설정 (§5.1)

### Week 2
5. SignUpNotifier + 지역/도시/사무실 드롭다운 (§7)
6. 회원가입 화면 (SCREENS.md)
7. 로그아웃 + cleanup 경로 (§9)
8. 단위 테스트 5건 최소 (§11.1)

### Week 3 (옵션)
9. 옵션 A 채택 시: MigrationHelper.kt + migration_service.dart (§4)
10. 통합 테스트 (Firebase Emulator)
11. 수동 QA 체크리스트 실행 (§11.3)

---

## 13. 참조

- `flutter/driver_app/MVP/NOTIFIERS.md` §3 — LoginNotifier 코드 전체
- `flutter/driver_app/MVP/MODELS.md` §4 — UserSession Freezed
- `flutter/driver_app/MVP/OVERVIEW.md` §기술스택 — flutter_secure_storage + firebase_auth 버전
- `flutter/driver_app/MVP/SCREENS.md` — 로그인/회원가입 화면 위젯 (차기 작성)
- `flutter/WORKING_DOC.md` §5 의제 9 — 자동로그인 결정 근거
- `driver_app/app/src/main/java/com/designated/driverapp/ui/login/LoginViewModel.kt` — Kotlin 원본 292 LOC
- `driver_app/app/src/main/java/com/designated/driverapp/util/SecurePreferencesManager.kt` — Kotlin EncryptedSharedPreferences 원본 99 LOC
- `driver_app/app/src/main/java/com/designated/driverapp/util/SessionManager.kt` — Kotlin SessionManager (미Read, 구조는 UserSession 기반 추정)

---

## 14. 작성 완료 요약

- **인증 흐름 3-Tier 설계**: Firebase Auth currentUser 자동 상속 → 이메일 사전 채움 → 수동 재로그인
- **Kotlin SecurePreferencesManager 99 LOC 완전 분석** + Flutter 옵션 B 권장 근거
- **선택 옵션 A 구현 제시** (§4 MethodChannel 마이그레이션 ~170 LOC Kotlin + Dart)
- **iOS Keychain `first_unlock_this_device` 선택 근거** (§5.2 4종 옵션 비교)
- **로그인/회원가입/비밀번호찾기/로그아웃 UX** 전수 (§6, §7, §8, §9)
- **위험 신호 6종 + 대응** (§10)
- **수동 QA 체크리스트 10종** (§11.3)
- **Phase 6 Week 1~3 작업 순서** (§12)

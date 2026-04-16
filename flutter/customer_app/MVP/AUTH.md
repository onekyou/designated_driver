# 손님앱 인증 (Anonymous Auth + Phone Auth + 프로필)

> **의제 9 결정 반영**: Anonymous Auth 자동 상속 + Phone Auth `linkWithCredential` 로 uid 유지 + **재로그인 불필요**
> **기사앱 AUTH.md 자매 문서** (`flutter/driver_app/MVP/AUTH.md`) — 동일 원칙 + 손님앱 차이 명시
> **원본 기준**: `customer_app/app/src/main/java/com/designated/customer/MainActivity.kt:382-500+` + `ui/auth/PhoneAuthViewModel.kt` (140 LOC) + `ui/profile/ProfileSetupViewModel.kt`
> **작성일**: 2026-04-16
> **작성자**: kotlin-expert

---

## 1. 인증 흐름 개요

### 1.1 전체 흐름 (Kotlin MainActivity.kt:382-500+ 복제)

```
앱 시작
  │
  ▼
[Firebase.initializeApp + Crashlytics 초기화]
  │
  ▼
[SharedPreferences 사무실 정보 확인]
  │
  ├── 없음 → /office-code (QR 스캔 또는 코드 입력)
  │
  └── 있음
       │
       ▼
     [FirebaseAuth.currentUser 확인]
       │
       ├── null → [signInAnonymously()] 자동 로그인
       │   │
       │   └── 실패 → /office-code (QR 재진입)
       │
       └── 유효 → 그대로 사용 (Anonymous uid 자동 상속 — 의제 9)
       │
       ▼
     [Firestore customers/{uid} 문서 조회]
       │
       ├── 존재 → 기존 손님: CustomerInfo 복구 + lastActiveAt 갱신 → / (홈)
       │
       └── 없음 → 신규 손님: /auth/terms (약관 동의부터)
                                   │
                                   ▼
                         /auth/terms → /auth/phone → /profile/setup → / (홈)
```

### 1.2 기사앱 AUTH.md와의 핵심 차이

| 항목 | 기사앱 | 손님앱 |
|------|--------|-------|
| **인증 방식** | Email/Password Auth | **Anonymous Auth** (자동) + **Phone Auth** (프로필 설정 시) |
| **자동 로그인** | `SecurePreferencesManager` email/password 저장 + 재로그인 | **재로그인 불필요** (Anonymous uid 영구 보존) |
| **uid 기반 경로** | `authUid` 필드 (designated_drivers) | **Anonymous uid** = `customers/{uid}` 문서 ID |
| **phoneNumber 기반 경로** | 없음 | `customerInfo/{phoneNumber}` + `customerPoints/{phoneNumber}` |
| **승인 플로우** | `pending_drivers` → `designated_drivers` (관리자 승인) | **승인 불필요** (Anonymous Auth 즉시 사용) |
| **재로그인 강제 시나리오** | 세션 만료 / 비밀번호 변경 | **사실상 없음** — 앱 삭제 후 재설치 시에만 새 uid |
| **Keychain 사용** | 자동로그인 credential 저장 필수 | **거의 불필요** (Anonymous만 사용 시) |
| **Phone Auth 통합** | 없음 | **linkWithCredential** 으로 Anonymous uid + Phone credential 병합 |

### 1.3 의제 9 결정 (손님앱 맥락)

OVERVIEW.md 의제 결정 매핑:
> **9 자동로그인**: Anonymous Auth 자동 상속 (재로그인 불필요) — 기사앱과 다른 흐름 — 손님은 인증 불요

**이유**:
1. 손님이 인증 개념을 몰라도 즉시 콜 요청 가능 (UX 최우선)
2. Firebase Auth SDK는 Anonymous uid를 **앱 업그레이드/재시작 시 자동 보존** (내부 토큰 저장)
3. Phone Auth는 **프로필 설정 시 1회만** — 이후 매번 로그인 안 함
4. `linkWithCredential` 로 기존 Anonymous uid 유지 + Phone credential 추가 → **데이터 연속성 보장**

---

## 2. Anonymous Auth (의제 9 자동 상속)

### 2.1 Kotlin 원본 (`MainActivity.kt:397-411`)

```kotlin
// 익명인증: 로그인 안 되어 있으면 자동 수행
var currentUser = auth.currentUser
if (currentUser == null) {
    try {
        val result = auth.signInAnonymously().await()
        currentUser = result.user
        android.util.Log.d("AnonymousAuth", "익명 로그인 완료: ${currentUser?.uid}")
    } catch (e: Exception) {
        android.util.Log.e("AnonymousAuth", "익명 로그인 실패", e)
        currentScreen = AppScreen.QR_REQUIRED
        return@LaunchedEffect
    }
} else {
    android.util.Log.d("AnonymousAuth", "이미 로그인됨: ${currentUser.uid}")
}
```

### 2.2 Flutter 구현 (NOTIFIERS.md §5.3 `_initAnonymousAuth`)

**AuthNotifier 생성자에서 자동 실행**:

```dart
// lib/features/auth/notifiers/auth_notifier.dart

class AuthNotifier extends StateNotifier<AuthUiState> {
  AuthNotifier({required this.ref, required this.auth, ...}) : super(const AuthUiState()) {
    _initAnonymousAuth();   // 앱 시작 시 1회 자동 실행
    _subscribeFcmToken();
  }

  Future<void> _initAnonymousAuth() async {
    // Step 1: 이미 로그인된 세션 확인 (Kotlin line 409-411)
    final currentUser = auth.currentUser;
    if (currentUser != null) {
      state = state.copyWith(
        isAnonymouslySignedIn: true,
        anonymousUid: currentUser.uid,
        phoneNumber: currentUser.phoneNumber ?? '',
      );
      debugPrint('[AuthNotifier] currentUser 상속: ${currentUser.uid}');
      return;
    }

    // Step 2: signInAnonymously (Kotlin line 400-403)
    try {
      final credential = await auth.signInAnonymously();
      final uid = credential.user?.uid;
      if (uid != null) {
        state = state.copyWith(
          isAnonymouslySignedIn: true,
          anonymousUid: uid,
        );
        debugPrint('[AuthNotifier] Anonymous signIn 완료: $uid');
      }
    } catch (e, st) {
      // Kotlin line 404-408: 실패 시 에러 표시 + SplashScreen에서 /office-code 로 이동
      await FirebaseCrashlytics.instance.recordError(e, st, reason: 'signInAnonymously');
      state = state.copyWith(error: 'Anonymous 인증 실패: $e');
    }
  }
  // ...
}
```

### 2.3 Anonymous uid 자동 상속 메커니즘

**Firebase Auth Flutter SDK** (`firebase_auth: ^5.3.1`) 는 Kotlin `firebase-auth-ktx` 와 **동일 내부 저장 구조** 사용:

- Android: `/data/data/{applicationId}/shared_prefs/com.google.firebase.auth.api.Store.{projectId}.xml`
- iOS: Keychain `firebase_auth` 서비스

**의제 10 결정** (동일 `applicationId` = `com.designated.customer.app`) 유지 시:
- Kotlin 앱 → Flutter 업그레이드 → **동일 파일 경로 접근 가능**
- Anonymous uid **영구 보존**
- 기존 `customers/{uid}` 문서 / `customerInfo/{phone}` / `customerPoints/{phone}` 모든 데이터 연속 사용

**실측 검증 필요** (Phase 6 Week 0~1 중 실기기 테스트):
- 기기 SM-F721N Z Flip4 (R3CT80K78NP, CLAUDE.md 테스트 기기)에 Kotlin 손님앱 설치 → Flutter 업그레이드 → currentUser.uid 유지 확인

---

## 3. Phone Auth (프로필 설정)

### 3.1 Kotlin 원본 (`ui/auth/PhoneAuthViewModel.kt:43-135`)

2단계 흐름:
1. **Step 1** (`sendVerificationCode`, line 43-87):
   - 전화번호 포맷팅 (`formatPhoneNumber`, line 127-135): `010-1234-5678` → `+82101234567`
   - `PhoneAuthOptions.newBuilder + setTimeout(60s) + setActivity(activity) + setCallbacks`
   - 콜백 3종:
     - `onVerificationCompleted(credential)` — **자동 인증 성공** (Android 일부 기기)
     - `onVerificationFailed(FirebaseException)` — 에러
     - `onCodeSent(verificationId, token)` — **SMS 발송 성공**, verificationId 저장
   - `PhoneAuthProvider.verifyPhoneNumber(options)` 호출

2. **Step 2** (`verifyCode`, line 89-105):
   - 저장된 `verificationId` + 사용자 입력 `smsCode` 로 credential 생성
   - `PhoneAuthProvider.getCredential(verificationId, smsCode)`
   - `signInWithPhoneAuthCredential(credential)` (line 107-124)

### 3.2 Flutter `verifyPhoneNumber` API (NOTIFIERS.md §5.4)

**중요한 차이**: Flutter `firebase_auth` 는 `Activity` 불필요 (Android만 해당 개념).

```dart
// AuthNotifier.sendVerificationCode (NOTIFIERS.md §5.4 상세 구현)

Future<void> sendVerificationCode() async {
  if (state.phoneNumber.isEmpty) {
    state = state.copyWith(error: '전화번호를 입력해주세요');
    return;
  }
  state = state.copyWith(isLoading: true, error: null);

  final formatted = _formatPhoneNumber(state.phoneNumber);

  await auth.verifyPhoneNumber(
    phoneNumber: formatted,
    timeout: const Duration(seconds: 60),
    verificationCompleted: (PhoneAuthCredential credential) async {
      // Android 자동 인증 성공 (Kotlin onVerificationCompleted 등가)
      await _signInWithCredential(credential);
    },
    verificationFailed: (FirebaseAuthException e) {
      state = state.copyWith(
        isLoading: false,
        error: '인증 실패: ${e.message}',
      );
    },
    codeSent: (String verificationId, int? resendToken) {
      _verificationId = verificationId;
      state = state.copyWith(
        isLoading: false,
        isCodeSent: true,
        verificationId: verificationId,
      );
    },
    codeAutoRetrievalTimeout: (String verificationId) {
      _verificationId = verificationId;
    },
  );
}

Future<void> verifyCode() async {
  if (state.verificationCode.isEmpty) {
    state = state.copyWith(error: '인증 코드를 입력해주세요');
    return;
  }
  final verId = _verificationId ?? state.verificationId;
  if (verId == null) {
    state = state.copyWith(error: '인증 세션이 만료되었습니다. 다시 시도해주세요');
    return;
  }

  state = state.copyWith(isLoading: true, error: null);
  try {
    final credential = PhoneAuthProvider.credential(
      verificationId: verId,
      smsCode: state.verificationCode,
    );
    await _signInWithCredential(credential);
  } catch (e) {
    state = state.copyWith(isLoading: false, error: '인증 실패: $e');
  }
}
```

### 3.3 전화번호 포맷팅 (Kotlin 라인 127-135 이관)

```dart
String _formatPhoneNumber(String phoneNumber) {
  final cleaned = phoneNumber.replaceAll(RegExp(r'[^0-9]'), '');
  if (cleaned.startsWith('010')) {
    return '+82${cleaned.substring(1)}';
  }
  return '+82$cleaned';
}
```

**주의**: 한국 전화번호만 지원 (`010` 으로 시작). 국제번호 입력 시 `+` 유지 또는 정규식 확장 필요 (Phase 2+).

### 3.4 linkWithCredential — **핵심** (Anonymous uid 유지)

**Kotlin 원본** (`PhoneAuthViewModel.kt:107-124`):
```kotlin
private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
    auth.signInWithCredential(credential)   // ⚠️ signInWithCredential 사용 — uid 변경 위험
        .addOnCompleteListener { task -> ... }
}
```

**⚠️ Kotlin 원본 문제점**: `signInWithCredential` 사용 → **Anonymous uid 폐기 + 새 Phone uid 발급**. 기존 `customers/{anonymousUid}` 데이터 고아!

**Flutter 개선안 (의제 9 결정)**: `linkWithCredential` 사용 → Anonymous uid 유지 + Phone credential 추가.

```dart
Future<void> _signInWithCredential(PhoneAuthCredential credential) async {
  debugPrint('[_signInWithCredential] 시작');
  try {
    final user = auth.currentUser;
    late final UserCredential result;

    if (user?.isAnonymous == true) {
      // ⭐ 핵심: linkWithCredential로 Anonymous uid 유지 + Phone 병합
      result = await user!.linkWithCredential(credential);
    } else {
      // 이미 Phone Auth 세션이면 signInWithCredential (이 경로는 거의 없음)
      result = await auth.signInWithCredential(credential);
    }

    final signedInUser = result.user;
    if (signedInUser != null) {
      debugPrint('[_signInWithCredential] 성공: uid=${signedInUser.uid}, phone=${signedInUser.phoneNumber}');
      state = state.copyWith(
        isLoading: false,
        isVerified: true,
        phoneNumber: signedInUser.phoneNumber ?? _formatPhoneNumber(state.phoneNumber),
        anonymousUid: signedInUser.uid,   // uid 변경 없음 (link 성공 시)
      );
    }
  } on FirebaseAuthException catch (e) {
    // linkWithCredential 실패 fallback
    if (e.code == 'credential-already-in-use') {
      await _handleCredentialAlreadyInUse(credential);
    } else {
      state = state.copyWith(
        isLoading: false,
        error: '인증 실패: ${e.message}',
      );
    }
  }
}
```

### 3.5 `credential-already-in-use` fallback

**시나리오**: 동일 전화번호로 **이전에 Phone Auth로 가입한 uid 존재** (앱 삭제 후 재설치 시)
→ `linkWithCredential` 실패 → 기존 uid로 `signInWithCredential` 강제 로그인 필요

```dart
Future<void> _handleCredentialAlreadyInUse(PhoneAuthCredential credential) async {
  debugPrint('[_signInWithCredential] credential-already-in-use → signIn 로 전환');
  try {
    // 기존 uid 로그인 (Anonymous uid는 폐기됨)
    final result = await auth.signInWithCredential(credential);
    final user = result.user;
    if (user != null) {
      state = state.copyWith(
        isLoading: false,
        isVerified: true,
        phoneNumber: user.phoneNumber ?? _formatPhoneNumber(state.phoneNumber),
        anonymousUid: user.uid,
      );

      // ⚠️ 기존 Anonymous 데이터 고아 처리
      // — 별도 복구 로직 필요 시 마이그레이션 스크립트 (Phase 2 과제)
      debugPrint('[_handleCredentialAlreadyInUse] 기존 uid 로그인: ${user.uid}');
    }
  } catch (e) {
    state = state.copyWith(
      isLoading: false,
      error: '이전 계정 복구 실패: $e',
    );
  }
}
```

**R2 과제**: Anonymous uid → 기존 Phone uid 전환 시 `customers/{anonymousUid}` + `customerInfo/{phone}` + `customerPoints/{phone}` 데이터 마이그레이션 — Phase 2 App Clip 문서(PHASE2_APPCLIP.md) 참조 가능성.

### 3.6 재전송 (resendToken)

**Flutter 지원 방식** (NOTIFIERS.md §5.4): `verifyPhoneNumber` 를 **재호출** 하면 Firebase SDK 가 자동으로 resendToken 활용. 명시적으로 `forceResendingToken` 을 전달하는 것은 iOS에서 무의미 (Android 전용 기능).

```dart
// 재전송 버튼 콜백 — NOTIFIERS.md §5.4의 sendVerificationCode 재호출
onPressed: () => ref.read(authNotifierProvider.notifier).sendVerificationCode(),
```

---

## 4. 프로필 설정 (ProfileSetupViewModel 통합)

### 4.1 Kotlin 원본 (`ui/profile/ProfileSetupViewModel.kt:109-181`)

Flutter NOTIFIERS.md §4.3.4 `updateProfile` 에 **핵심 로직** 포함. 본 절에서는 인증 흐름과의 연계 재확인:

**Kotlin 원본 저장 흐름**:
1. `customers/{uid}` 문서에 프로필 저장 (`customerData["fcmToken"] = fcmToken`)
2. 동시에 `customerInfo/{phoneNumber}` 에도 `fcmToken` 저장 (별도 경로)
3. 실패 시 `CrashlyticsException` 기록

### 4.2 Flutter 구현

ProfileNotifier.updateProfile (NOTIFIERS.md §4.3.4) 호출 후 **AuthNotifier.initialFcmTokenRegistration()** 재호출:

```dart
// lib/features/profile/screens/profile_setup_screen.dart

Future<void> _onSubmit() async {
  if (!_formKey.currentState!.validate()) return;
  setState(() => _isSaving = true);

  try {
    final phone = ref.read(authNotifierProvider).phoneNumber;

    // 1. customers/{uid} 문서 저장 (ProfileNotifier)
    final ok = await ref.read(profileNotifierProvider.notifier).updateProfile(
      name: _nameCtl.text.trim(),
      phoneNumber: phone,
      homeAddress: _homeAddressCtl.text.trim(),
    );
    if (!mounted) return;

    if (ok) {
      // 2. customerInfo/{phone} 경로에 FCM 토큰 저장 (NOTIFIERS.md §5.5)
      await ref.read(authNotifierProvider.notifier).initialFcmTokenRegistration();

      // 3. phoneNumber를 SharedPreferences에 저장 (Kotlin line 436-438)
      final prefs = ref.read(sharedPreferencesProvider);
      await prefs.setString('phone_number', phone);

      if (mounted) context.goNamed('home');
    } else {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('프로필 저장 실패')),
      );
    }
  } finally {
    if (mounted) setState(() => _isSaving = false);
  }
}
```

### 4.3 중복 전화번호 감지 (CF `checkPhoneNumberDuplicate` — 선택)

**Kotlin 원본**: `ProfileSetupViewModel.kt` 에서 CF httpsCallable 호출 여부는 직접 확인 안 함. **Phase 1 MVP 범위 외** (OVERVIEW.md §R1 또는 R2).

**구현 시**:
```dart
final callable = FirebaseFunctions.instance.httpsCallable('checkPhoneNumberDuplicate');
final result = await callable.call({'phoneNumber': phone});
final isDuplicate = result.data['isDuplicate'] as bool? ?? false;
```

---

## 5. 약관 동의 (TermsAgreementViewModel 통합)

### 5.1 Kotlin 원본 요약

- `TermsAgreementViewModel.kt` (직접 Read 안 함, 구조 추정)
- SharedPreferences에 저장: `terms_accepted` / `terms_version` / `terms_accepted_at` / `marketing_consent`
- `PreferencesManager.saveTermsAcceptance(version, marketingConsent)` 메서드 사용 (`customer_app/.../util/PreferencesManager.kt:130-138`)

### 5.2 Flutter 구현 (ProfileNotifier 내부)

NOTIFIERS.md §4.3.6 에 명시. **별도 Notifier 불필요** (MVP 범위).

```dart
// ProfileNotifier.acceptTerms (NOTIFIERS.md §4.3.6 이미 정의)

Future<void> acceptTerms({
  required String version,
  required bool marketingConsent,
}) async {
  await prefs.setBool('terms_accepted', true);
  await prefs.setString('terms_version', version);
  await prefs.setInt('terms_accepted_at', DateTime.now().millisecondsSinceEpoch);
  await prefs.setBool('marketing_consent', marketingConsent);
}
```

### 5.3 화면 연계 (SCREENS.md §4.2 TermsAgreementScreen)

```dart
Future<void> _onAgree() async {
  if (!_allRequired) return;
  await ref.read(profileNotifierProvider.notifier).acceptTerms(
    version: '1.0',
    marketingConsent: _marketingChecked,
  );
  if (!mounted) return;
  context.goNamed('phone-auth');
}
```

---

## 6. FCM 토큰 등록 (의제 5 + 의제 11 통합)

### 6.1 Kotlin 원본 (`MainActivity.kt:278-327`)

```kotlin
// 1. FCM 토큰 조회 (line 279-293)
FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
    if (!task.isSuccessful) return@addOnCompleteListener
    val token = task.result

    // 2. SharedPreferences에서 phoneNumber/provinceId/cityId/officeId 읽기
    val prefsManager = PreferencesManager(this)
    val phoneNumber = prefsManager.getPhoneNumber()
    val provinceId = prefsManager.getProvinceId()
    val cityId = prefsManager.getCityId()
    val officeId = prefsManager.getOfficeId()

    if (...) {
        saveFcmTokenToFirestore(token, phoneNumber, provinceId, cityId, officeId)
    }
}

// 3. customerInfo/{phone}.fcmToken 저장 (line 306-327)
fun saveFcmTokenToFirestore(token, phone, provinceId, cityId, officeId) {
    FirebaseFirestore.getInstance()
        .collection("provinces").document(provinceId)
        .collection("cities").document(cityId)
        .collection("offices").document(officeId)
        .collection("customerInfo")
        .document(phone)
        .set(mapOf(
            "fcmToken" to token,
            "phoneNumber" to phone,
            "updatedAt" to Timestamp.now()
        ), SetOptions.merge())
}
```

### 6.2 Flutter 구현 (NOTIFIERS.md §5.5 `_registerFcmToken`)

**의제 5 `fcmTokenPlatform` 메타 추가**:

```dart
// AuthNotifier._registerFcmToken (NOTIFIERS.md §5.5 이미 정의)

Future<void> _registerFcmToken(String token) async {
  final phone = state.phoneNumber;
  final profile = ref.read(profileNotifierProvider);

  if (phone.isEmpty ||
      profile.provinceId.isEmpty ||
      profile.cityId.isEmpty ||
      profile.officeId.isEmpty) {
    debugPrint('[_registerFcmToken] 정보 부족, 나중에 저장');
    return;
  }

  try {
    await firestore
        .collection('provinces').doc(profile.provinceId)
        .collection('cities').doc(profile.cityId)
        .collection('offices').doc(profile.officeId)
        .collection('customerInfo').doc(phone)
        .set({
      'fcmToken': token,
      'fcmTokenPlatform': Platform.isIOS ? 'ios' : 'android',  // 의제 5 신규
      'phoneNumber': phone,
      'updatedAt': Timestamp.now(),
    }, SetOptions(merge: true));

    debugPrint('[_registerFcmToken] 저장 완료');
  } catch (e, st) {
    await FirebaseCrashlytics.instance.recordError(e, st, reason: 'registerFcmToken');
  }
}
```

### 6.3 onTokenRefresh 구독 (NOTIFIERS.md §5.3)

```dart
void _subscribeFcmToken() {
  _tokenRefreshSub = messaging.onTokenRefresh.listen((token) {
    _registerFcmToken(token);
  });
}
```

### 6.4 FCM 토큰 저장 타이밍

| 상황 | 호출 위치 |
|------|----------|
| 앱 첫 실행 시 초기 토큰 조회 | `SplashScreen._bootstrap` 에서 `auth.initialFcmTokenRegistration()` |
| 앱 재시작 시 기존 토큰 재등록 | 동일 |
| 토큰 갱신 이벤트 | `messaging.onTokenRefresh` 자동 구독 (NOTIFIERS.md §5.3) |
| 프로필 설정 완료 후 | `ProfileSetupScreen._onSubmit` 에서 `initialFcmTokenRegistration()` 재호출 |

---

## 7. iOS Keychain (의제 9)

### 7.1 손님앱은 Keychain 거의 불필요

**근거**:
- Anonymous Auth 토큰은 **Firebase Auth SDK가 자체 관리** (Keychain)
- 비밀번호 없음 → 별도 저장 불필요
- Phone Auth verificationId는 **세션 휘발성** (60초 타임아웃, 저장 가치 낮음)

### 7.2 Phone Auth 진행 중 verificationId 저장 (선택)

**시나리오**: 사용자가 SMS 코드 받은 후 앱을 백그라운드 → 잠시 후 복귀
- 현재 구현: `_verificationId` 는 **AuthNotifier 메모리에만** 저장
- 앱 완전 종료 시 휘발 → 재전송 필요
- **개선 (옵션)**: `flutter_secure_storage` 에 임시 저장

**MVP 범위**: 메모리 저장만 유지. 이유 — 60초 타임아웃 내 복귀 빈도 낮음 + 재전송 UX 간단.

```dart
// lib/core/providers.dart

final secureStorageProvider = Provider<FlutterSecureStorage>((_) {
  return const FlutterSecureStorage(
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
    ),
    aOptions: AndroidOptions(
      encryptedSharedPreferences: true,
    ),
  );
});
```

**기사앱 AUTH.md §5 와 동일 옵션**. 손님앱 Phase 1 MVP 에서는 **실질 사용 없음** (Anonymous Auth가 모든 경우 커버).

---

## 8. 기사앱 AUTH.md 와의 차이 (요약)

| 항목 | 기사앱 | 손님앱 |
|------|--------|-------|
| **인증 방식** | Email/Password | Anonymous + Phone Auth link |
| **마이그레이션** | `EncryptedSharedPreferences` 옵션 A/B | **불필요** (Anonymous uid 자동 상속) |
| **재로그인 UX** | 이메일 사전 채움 + 비밀번호 재입력 | **없음** (자동) |
| **collectionGroup 쿼리** | `designated_drivers` where authUid | **없음** (customers/{uid} 직접 조회) |
| **승인 체크** | `pending_drivers` 존재 / approvalStatus == APPROVED | **없음** |
| **iOS Keychain** | 필수 (자동로그인 credential) | 거의 불필요 |
| **오프라인 로그인** | SessionStorage fallback (Kotlin SessionManager) | **해당 없음** (Anonymous는 오프라인 개념 없음) |
| **비밀번호 찾기** | sendPasswordResetEmail | **해당 없음** |
| **로그아웃** | `auth.signOut()` + 세션 정리 | **거의 없음** (UX상 제공 안 함) |
| **FCM 토큰 경로** | `designated_drivers/{uid}.fcmToken` | **`customerInfo/{phoneNumber}.fcmToken`** (phone 기반) |

---

## 9. 로그아웃 (거의 없음)

### 9.1 손님앱 로그아웃 UX 부재 근거

- Kotlin 원본 `MainNavigation.kt:38` `onLogout` 콜백은 존재하지만 **실제 호출 경로 확인 필요** (ProfileScreen 내부 로그아웃 버튼)
- 사용자 관점에서 **"로그아웃" 개념 없음** — 앱 삭제만이 세션 종료 수단

### 9.2 디버깅/테스트용 signOut 구현 (선택)

```dart
// AuthNotifier @visibleForTesting 메서드

@visibleForTesting
Future<void> signOut() async {
  await auth.signOut();
  state = const AuthUiState();
  await _initAnonymousAuth();  // 즉시 새 Anonymous uid 생성
}
```

**주의**: 프로덕션 UI에 로그아웃 버튼 노출 시 → 사용자가 실수로 누르면 **기존 `customers/{uid}` 데이터 접근 불가** (새 uid 생성됨). UX 부적절.

### 9.3 계정 삭제 (R2)

**Apple App Store Review Guideline 5.1.1(v)** (2022년 6월 이후): 계정 생성 앱은 **계정 삭제 기능 제공 필수**.

**구현 (Phase 2+)**:
```dart
Future<void> deleteAccount() async {
  final uid = auth.currentUser?.uid;
  if (uid == null) return;

  // 1. Firestore 데이터 삭제
  // (customers/{uid} + customerInfo/{phone} + customerPoints/{phone} + pointTransactions/*)
  // → CF callable `deleteCustomerData` 로 위임 권장
  final callable = FirebaseFunctions.instance.httpsCallable('deleteCustomerData');
  await callable.call({'uid': uid});

  // 2. Firebase Auth 계정 삭제
  await auth.currentUser?.delete();

  // 3. SharedPreferences 전체 초기화
  await ref.read(sharedPreferencesProvider).clear();

  // 4. SplashScreen으로 재진입
  state = const AuthUiState();
}
```

**R2 분류**: Phase 1 MVP 범위 외. OVERVIEW.md §R2 에 추가 권고.

---

## 10. 위험 신호

### 10.1 Anonymous uid 상속 실패 → 기존 손님 데이터 고아

**시나리오**:
- 기존 Kotlin 손님앱 사용자가 Flutter 업그레이드
- Firebase Auth SDK 세션 자동 상속 **실패** (`applicationId` 불일치 또는 Auth 저장 형식 호환 안 됨)
- 새 Anonymous uid 발급 → `customers/{기존uid}` 문서 고아
- 사용자 체감: "포인트가 사라졌다" / "이용 내역이 비어있다"

**완화**:
1. 의제 10 결정: **`applicationId` 동일 유지** (`com.designated.customer.app`)
2. Phase 6 Week 0~1: **실기기 테스트** — 기존 Kotlin 앱 → Flutter 업그레이드 후 currentUser.uid 동일 확인
3. 실패 시 fallback: phoneNumber 기반 데이터 복구 (`customerInfo/{phone}` 에서 역추적)

### 10.2 linkWithCredential 실패 → 중복 계정

**시나리오** (§3.5):
- 손님 A가 앱 설치 → Anonymous uid 발급 → Phone Auth 시도 → 동일 전화번호로 **이전에 가입한 uid 존재** → `credential-already-in-use` 에러

**완화**:
- §3.5 `_handleCredentialAlreadyInUse` fallback 구현
- 기존 uid 로그인 + 현재 Anonymous 세션 폐기
- **데이터 마이그레이션은 R2 과제** — phone 기반 `customerInfo/{phone}` 만 유지 + `customers/{uid}` 는 신규 uid로 재생성 (이용 내역 일부 유실 감수)

### 10.3 phoneNumber 변경 시 `customerInfo/{phone}` 마이그레이션

**시나리오**: 사용자가 전화번호 변경 (새 SIM, 번호 이동)
- 기존 `customerInfo/{old_phone}.fcmToken` → 새 phone으로 이동 필요
- 기존 포인트(`customerPoints/{old_phone}`) → 새 phone으로 이동 필요
- **Phase 1 MVP 범위 외** (R2 과제)

**완화**:
- Phase 1: 단일 phoneNumber 가정 (Phone Auth 인증 시 확정 후 변경 불가)
- R2: "전화번호 변경" 기능 추가 + CF callable `migrateCustomerByPhone` 제공

### 10.4 Phone Auth SMS 비용

- Firebase Phone Auth: **월 10건 무료** (개인), **Blaze 플랜 시 $0.01~0.06/SMS**
- 의제 11 측정 인프라로 **SMS 요청량 모니터링** (Analytics `customer_phone_verified` 이벤트 카운트)

### 10.5 reCAPTCHA 요구사항 (Android)

- Flutter `firebase_auth` Android에서 Phone Auth 시 SafetyNet/reCAPTCHA v2 Enterprise 필요
- 자동 인증(`onVerificationCompleted`) 실패 빈도 증가 시 → reCAPTCHA SDK 의존성 확인
- Kotlin 원본 (`driver_app/app/build.gradle:137-138`): `recaptcha` + `play-services-safetynet` 의존성
- Flutter 측에서도 `firebase_auth` 가 내부적으로 동일 의존성 포함 (자동)

---

## 11. 테스트 전략

### 11.1 단위 테스트 (`test/features/auth/`)

```dart
// test/features/auth/auth_notifier_test.dart

void main() {
  group('AuthNotifier._initAnonymousAuth', () {
    late ProviderContainer container;
    late MockFirebaseAuth auth;

    setUp(() {
      auth = MockFirebaseAuth();
      container = ProviderContainer(overrides: [
        firebaseAuthProvider.overrideWithValue(auth),
        // ...
      ]);
    });

    test('currentUser 존재 시 상속', () async {
      final user = MockUser(uid: 'existing_uid');
      when(() => auth.currentUser).thenReturn(user);

      // AuthNotifier 생성 — init 자동 실행
      container.read(authNotifierProvider.notifier);
      await Future<void>.delayed(const Duration(milliseconds: 100));

      final state = container.read(authNotifierProvider);
      expect(state.isAnonymouslySignedIn, true);
      expect(state.anonymousUid, 'existing_uid');

      verifyNever(() => auth.signInAnonymously());  // 재로그인 없음 확인
    });

    test('currentUser null 시 signInAnonymously', () async {
      when(() => auth.currentUser).thenReturn(null);
      when(() => auth.signInAnonymously()).thenAnswer(
        (_) async => MockUserCredential(MockUser(uid: 'new_uid')),
      );

      container.read(authNotifierProvider.notifier);
      await Future<void>.delayed(const Duration(milliseconds: 100));

      final state = container.read(authNotifierProvider);
      expect(state.isAnonymouslySignedIn, true);
      expect(state.anonymousUid, 'new_uid');
    });

    test('signInAnonymously 실패 시 error', () async {
      when(() => auth.currentUser).thenReturn(null);
      when(() => auth.signInAnonymously()).thenThrow(
        FirebaseAuthException(code: 'network-request-failed'),
      );

      container.read(authNotifierProvider.notifier);
      await Future<void>.delayed(const Duration(milliseconds: 100));

      final state = container.read(authNotifierProvider);
      expect(state.isAnonymouslySignedIn, false);
      expect(state.error, contains('Anonymous 인증 실패'));
    });
  });

  group('AuthNotifier Phone Auth', () {
    test('sendVerificationCode 성공 → isCodeSent true', () async {
      // ...
    });

    test('verifyCode + linkWithCredential 성공 → isVerified + anonymous uid 유지', () async {
      final anonymousUser = MockUser(uid: 'anon_uid', isAnonymous: true);
      when(() => auth.currentUser).thenReturn(anonymousUser);
      when(() => anonymousUser.linkWithCredential(any())).thenAnswer(
        (_) async => MockUserCredential(MockUser(
          uid: 'anon_uid',  // 동일 uid 유지
          phoneNumber: '+821012345678',
        )),
      );

      final notifier = container.read(authNotifierProvider.notifier);
      notifier.updateVerificationCode('123456');
      // ... verificationId Mock 설정 ...
      await notifier.verifyCode();

      final state = container.read(authNotifierProvider);
      expect(state.isVerified, true);
      expect(state.anonymousUid, 'anon_uid');  // uid 유지 확인
      expect(state.phoneNumber, '+821012345678');
    });

    test('credential-already-in-use fallback → signInWithCredential', () async {
      // ...
    });
  });
}
```

### 11.2 수동 QA 체크리스트

| # | 시나리오 | 기대 동작 |
|---|---------|----------|
| 1 | 첫 설치 → Anonymous 자동 로그인 | 로그인 화면 안 보임 / OfficeCode 화면 진입 |
| 2 | QR 스캔 → 프로필 없음 → 약관/인증/프로필 3단계 | 각 단계 자연스럽게 전환 |
| 3 | 프로필 완료 → 앱 재시작 | 바로 홈 진입 (온보딩 스킵) |
| 4 | 앱 강제 종료 → 재실행 | currentUser 유지, 홈 진입 |
| 5 | Phone Auth SMS 수신 | 60초 내 코드 입력 → linkWithCredential 성공 |
| 6 | 자동 인증 (Android) | onVerificationCompleted 즉시 성공 |
| 7 | 동일 번호 재인증 (`credential-already-in-use`) | 기존 uid로 전환 + Snackbar 안내 |
| 8 | 네트워크 오프라인 상태 첫 실행 | "오프라인" 안내 + OfficeCode 화면 |
| 9 | 앱 업그레이드 (Kotlin → Flutter) | currentUser.uid 유지 + 기존 데이터 연속 사용 |
| 10 | 계정 삭제 (R2 구현 시) | Firebase Auth + Firestore 데이터 모두 삭제 |

### 11.3 통합 테스트 (Firebase Emulator)

```bash
# Firebase Emulator (Auth + Firestore + Functions)
firebase emulators:start --only auth,firestore,functions

# Flutter 테스트 — Auth Emulator 연결
await Firebase.initializeApp();
FirebaseAuth.instance.useAuthEmulator('localhost', 9099);
FirebaseFirestore.instance.useFirestoreEmulator('localhost', 8080);
```

**주의**: Phone Auth는 Emulator 미지원 — 실기기 또는 테스트 번호 사용 (`+821012345678` + 고정 코드 `123456`).

---

## 12. Phase 6 작업 순서

### Week 1 — 기반
1. `AuthNotifier._initAnonymousAuth` + `_subscribeFcmToken` 구현 (NOTIFIERS.md §5.3)
2. `SplashScreen._bootstrap` 상태 머신 (SCREENS.md §3.2)

### Week 2 — Phone Auth
3. `sendVerificationCode` + `verifyCode` + `_signInWithCredential` (§3)
4. `credential-already-in-use` fallback (§3.5)
5. `PhoneAuthScreen` UI (SCREENS.md §4.4)

### Week 3 — 프로필 + FCM
6. `ProfileNotifier.updateProfile` 통합 (NOTIFIERS.md §4.3.4)
7. `AuthNotifier.initialFcmTokenRegistration` (§6.2)
8. `ProfileSetupScreen` + `onSubmit` flow (SCREENS.md §4.5)

### Week 4 — 검증
9. 단위 테스트 (§11.1)
10. Firebase Emulator 통합 테스트 (§11.3)
11. 실기기 수동 QA (§11.2) — SM-F721N Z Flip4

---

## 13. 참조

- `flutter/customer_app/MVP/NOTIFIERS.md` §5 — AuthNotifier 상세 코드
- `flutter/customer_app/MVP/MODELS.md` §7.4 — AuthUiState + CallStatus + DriverInfo
- `flutter/customer_app/MVP/OVERVIEW.md` — Phase 1/2 범위
- `flutter/customer_app/MVP/SCREENS.md` — 온보딩 화면 4종 + HomeScreen
- `flutter/customer_app/MVP/FCM.md` §1 — FCM 토큰 저장 경로 + 의제 5 메타
- `flutter/customer_app/MVP/ATTRIBUTION.md` — 사무실 정보 연계
- `flutter/customer_app/MVP/PHASE2_APPCLIP.md` — App Clip Custom Token (차후 phone 마이그레이션 기반)
- `flutter/driver_app/MVP/AUTH.md` — 기사앱 자매 문서 (Email/Password 버전)
- `customer_app/app/src/main/java/com/designated/customer/MainActivity.kt` — Kotlin 원본 550+ LOC (CustomerApp composable)
- `customer_app/app/src/main/java/com/designated/customer/ui/auth/PhoneAuthViewModel.kt` — 140 LOC
- `customer_app/app/src/main/java/com/designated/customer/ui/profile/ProfileSetupViewModel.kt` — 프로필 저장

---

## 14. 작성 완료 요약

- **Anonymous Auth 자동 상속 흐름** (§1.1 ~ §2)
- **Phone Auth 2단계** (sendVerificationCode → verifyCode, §3.1~3.3)
- **linkWithCredential 핵심 개선** (Kotlin 원본 `signInWithCredential` vs Flutter `linkWithCredential`, §3.4)
- **`credential-already-in-use` fallback 구현** (§3.5)
- **FCM 토큰 등록 의제 5 `fcmTokenPlatform` 메타** (§6)
- **iOS Keychain 거의 불필요 근거** (§7)
- **기사앱 AUTH.md 와의 차이 10종** (§8)
- **로그아웃 부재 + R2 계정 삭제 설계** (§9)
- **위험 신호 5종** (§10): uid 상속 실패 / link 실패 / phone 변경 / SMS 비용 / reCAPTCHA
- **수동 QA 체크리스트 10종 + 단위 테스트 패턴** (§11)
- **Phase 6 Week 1~4 작업 순서** (§12)

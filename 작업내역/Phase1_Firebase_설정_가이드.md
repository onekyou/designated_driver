# 🔥 Firebase 설정 가이드 (Flutter)

**작성일**: 2025-01-17

---

## 📋 Firebase 설정 단계

### 1단계: Firebase CLI 설치

**방법 1: npm 사용** (권장)

```bash
npm install -g firebase-tools
```

**방법 2: 독립 실행 파일** (npm 없을 경우)
https://firebase.google.com/docs/cli#install-cli-windows

---

### 2단계: Firebase 로그인

```bash
firebase login
```

브라우저에서 Google 계정 로그인

---

### 3단계: FlutterFire CLI 설치

```bash
dart pub global activate flutterfire_cli
```

**환경 변수 확인**:
- `C:\Users\[사용자명]\AppData\Local\Pub\Cache\bin`이 Path에 있는지 확인
- 없으면 추가

---

### 4단계: Firebase 프로젝트 확인

**기존 Firebase 프로젝트 사용**:
- 현재 프로젝트: `designated-driver` (이미 생성됨)
- Firebase Console: https://console.firebase.google.com

**확인 사항**:
1. Firestore Database 활성화 ✅
2. Authentication 활성화 ✅
3. Cloud Functions 배포됨 ✅
4. Cloud Messaging 활성화 필요 (iOS APNs)

---

### 5단계: Flutter 프로젝트에서 Firebase 설정

프로젝트 루트에서:

```bash
cd C:\app_dev\designated_driver\designated_customer_flutter
flutterfire configure
```

**선택 사항**:
1. 프로젝트 선택: `designated-driver` 선택
2. 플랫폼 선택: `android`, `ios` 모두 선택
3. Bundle ID 입력: `com.designated.customer`

**자동 생성되는 파일**:
- `android/app/google-services.json`
- `ios/Runner/GoogleService-Info.plist`
- `lib/firebase_options.dart`

---

## 📱 Android 설정

### 1. google-services.json 확인

위치: `android/app/google-services.json`

**수동 다운로드** (필요 시):
1. Firebase Console → Project Settings
2. Your apps → Android 앱 선택
3. "Download google-services.json" 클릭
4. `android/app/` 에 복사

### 2. android/build.gradle 수정

```gradle
buildscript {
    dependencies {
        // Firebase 플러그인 추가
        classpath 'com.google.gms:google-services:4.4.0'
    }
}
```

### 3. android/app/build.gradle 수정

파일 맨 아래에 추가:

```gradle
apply plugin: 'com.google.gms.google-services'
```

### 4. minSdkVersion 확인

`android/app/build.gradle`:

```gradle
android {
    defaultConfig {
        minSdkVersion 21  // Firebase 최소 요구사항
    }
}
```

---

## 🍎 iOS 설정

### 1. GoogleService-Info.plist 확인

위치: `ios/Runner/GoogleService-Info.plist`

**수동 다운로드** (필요 시):
1. Firebase Console → Project Settings
2. Your apps → iOS 앱 선택
3. "Download GoogleService-Info.plist" 클릭
4. Xcode에서 Runner 폴더에 추가

### 2. Bundle Identifier 확인

`ios/Runner.xcodeproj/project.pbxproj`:

```
PRODUCT_BUNDLE_IDENTIFIER = com.designated.customer;
```

### 3. Info.plist 권한 설정

`ios/Runner/Info.plist`에 추가:

```xml
<!-- 카메라 (QR 스캔) -->
<key>NSCameraUsageDescription</key>
<string>QR 코드를 스캔하기 위해 카메라 접근이 필요합니다.</string>

<!-- 위치 -->
<key>NSLocationWhenInUseUsageDescription</key>
<string>현재 위치를 확인하기 위해 위치 정보 접근이 필요합니다.</string>

<!-- 마이크 (음성 입력) -->
<key>NSMicrophoneUsageDescription</key>
<string>음성 입력을 위해 마이크 접근이 필요합니다.</string>

<!-- 만보기 (HealthKit) -->
<key>NSHealthShareUsageDescription</key>
<string>걸음 수를 추적하기 위해 건강 데이터 접근이 필요합니다.</string>

<key>NSHealthUpdateUsageDescription</key>
<string>걸음 수를 저장하기 위해 건강 데이터 쓰기 권한이 필요합니다.</string>

<!-- Background Modes -->
<key>UIBackgroundModes</key>
<array>
    <string>fetch</string>
    <string>processing</string>
    <string>remote-notification</string>
</array>
```

### 4. iOS 최소 버전 설정

`ios/Podfile`:

```ruby
platform :ios, '12.0'
```

---

## 🔔 FCM (Firebase Cloud Messaging) 설정

### Android FCM 설정

1. **google-services.json에 자동 포함됨** ✅
2. 추가 작업 불필요

### iOS APNs 설정 ⚠️ 중요!

**1. Apple Developer에서 APNs Key 생성**

1. https://developer.apple.com/account 접속
2. Certificates, Identifiers & Profiles
3. Keys → "+" 클릭
4. Key Name 입력 (예: "Designated Customer APNs")
5. "Apple Push Notifications service (APNs)" 체크
6. Continue → Register
7. **Key ID 메모** (나중에 필요)
8. **.p8 파일 다운로드** (재다운로드 불가!)

**2. Firebase Console에 APNs Key 등록**

1. Firebase Console → Project Settings
2. Cloud Messaging 탭
3. iOS 앱 선택
4. "APNs Authentication Key" 섹션
5. "Upload" 클릭
6. .p8 파일 업로드
7. Key ID 입력
8. Team ID 입력 (Apple Developer → Membership에서 확인)

**3. Xcode에서 Push Notification 활성화**

1. Xcode에서 ios/Runner.xcworkspace 열기
2. Runner → Signing & Capabilities
3. "+ Capability" 클릭
4. "Push Notifications" 추가
5. "Background Modes" 추가
   - "Remote notifications" 체크

---

## 📦 pubspec.yaml 패키지 추가

```yaml
dependencies:
  firebase_core: ^2.24.0
  firebase_auth: ^4.15.0
  firebase_firestore: ^4.13.0
  firebase_functions: ^4.5.0
  firebase_messaging: ^14.7.0
  firebase_analytics: ^10.7.4
```

패키지 설치:

```bash
flutter pub get
```

---

## 🚀 Firebase 초기화 코드

`lib/main.dart`:

```dart
import 'package:flutter/material.dart';
import 'package:firebase_core/firebase_core.dart';
import 'firebase_options.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Firebase 초기화
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  runApp(const MyApp());
}
```

---

## ✅ 설정 확인

### 1. Android 빌드 테스트

```bash
flutter build apk --debug
```

**성공 메시지**:
```
✓ Built build/app/outputs/flutter-apk/app-debug.apk
```

### 2. iOS 빌드 테스트 (Mac Cloud 필요)

```bash
flutter build ios --debug --no-codesign
```

### 3. Firebase 연결 확인

간단한 테스트 코드:

```dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  // Firestore 테스트
  final firestore = FirebaseFirestore.instance;
  try {
    final doc = await firestore.collection('test').doc('test').get();
    print('Firebase 연결 성공!');
  } catch (e) {
    print('Firebase 연결 실패: $e');
  }

  runApp(const MyApp());
}
```

---

## ⚠️ 주의사항

### 1. **Bundle ID 일치**
- Firebase: `com.designated.customer`
- android/app/build.gradle: `applicationId "com.designated.customer"`
- ios/Runner: `PRODUCT_BUNDLE_IDENTIFIER = com.designated.customer`
- **모두 동일해야 함!**

### 2. **google-services.json 위치**
- ❌ `android/google-services.json` (잘못된 위치)
- ✅ `android/app/google-services.json` (올바른 위치)

### 3. **GoogleService-Info.plist 위치**
- Xcode에서 추가해야 Runner 폴더에 포함됨
- 단순히 파일 복사만 하면 인식 안 됨

### 4. **MultiDex 설정** (Android)

Firebase 패키지가 많을 경우 필요:

`android/app/build.gradle`:

```gradle
android {
    defaultConfig {
        multiDexEnabled true
    }
}

dependencies {
    implementation 'androidx.multidex:multidex:2.0.1'
}
```

### 5. **iOS CocoaPods 업데이트**

```bash
cd ios
pod install
cd ..
```

---

## 🔐 보안 규칙 (Firestore)

**현재 규칙 확인**:
```
firestore.rules 파일 참조
```

**주요 규칙**:
- 인증된 사용자만 접근 가능
- 자신의 데이터만 읽기/쓰기 가능
- 관리자는 모든 데이터 접근 가능

---

## 📊 Firebase Console 확인 사항

### 1. Firestore Database
- ✅ 활성화 확인
- ✅ 컬렉션 구조 확인

### 2. Authentication
- ✅ 익명 인증 활성화
- ✅ 전화번호 인증 활성화 (선택)

### 3. Cloud Functions
- ✅ 배포 확인
- ✅ 리전 확인: asia-northeast3

### 4. Cloud Messaging
- ✅ Android 자동 설정됨
- ⚠️ iOS APNs 수동 설정 필요

---

## 🆘 문제 해결

### 문제 1: "FirebaseOptions cannot be null"
**해결**: `firebase_options.dart` 파일 확인
```bash
flutterfire configure
```

### 문제 2: "No Firebase App '[DEFAULT]' has been created"
**해결**: `Firebase.initializeApp()` 호출 확인

### 문제 3: iOS 빌드 시 "CocoaPods not installed"
**해결**:
```bash
sudo gem install cocoapods
```

### 문제 4: Android 빌드 시 "minSdkVersion" 에러
**해결**: `android/app/build.gradle`에서 `minSdkVersion 21` 이상 설정

### 문제 5: iOS APNs "Invalid token"
**해결**:
1. .p8 파일 재업로드
2. Key ID, Team ID 재확인
3. Bundle ID 일치 확인

---

## ✅ 체크리스트

### Android
- [ ] google-services.json 위치 확인 (`android/app/`)
- [ ] google-services 플러그인 추가
- [ ] minSdkVersion 21 이상
- [ ] 빌드 테스트 성공

### iOS
- [ ] GoogleService-Info.plist Xcode에 추가
- [ ] Bundle Identifier 일치
- [ ] Info.plist 권한 설명 추가
- [ ] APNs Key 생성 및 업로드
- [ ] Xcode Push Notification 활성화
- [ ] CocoaPods 설치 완료

### Firebase
- [ ] FlutterFire CLI 설치
- [ ] flutterfire configure 실행
- [ ] firebase_options.dart 생성
- [ ] Firebase.initializeApp() 호출
- [ ] Firestore 연결 테스트

---

**다음 단계**: Phase 2 - 인증 및 Attribution 구현

**작성자**: Claude
**최종 수정일**: 2025-01-17

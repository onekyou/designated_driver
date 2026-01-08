# Mac 작업 안내서

## 📋 개요

이 문서는 Windows에서 완료한 모든 작업을 정리하고, Mac에서 수행해야 할 iOS 빌드 및 배포 작업을 안내합니다.

**프로젝트**: `designated_customer_flutter` (대리운전 고객 앱)
**목표**: iOS App Store 출시 준비 완료
**Windows 작업 완료일**: 2025-12-29

---

## ✅ Windows에서 완료한 작업 (Phase 1-5)

### Phase 1: iOS 빌드 환경 설정

#### 1-1. Firebase 프로젝트 통일
- **변경 파일**: `lib/firebase_options.dart`
- **변경 내용**: iOS 설정을 Android와 동일한 `calldetector-5d61e` 프로젝트로 통일
  ```dart
  static const FirebaseOptions ios = FirebaseOptions(
    apiKey: 'AIzaSyDSbl-RlfidVBlo2i1_jTuCtqo8lgTTbGg',
    appId: '1:60275310305:ios:8e0eeb7659801a869ac1a0',
    messagingSenderId: '60275310305',
    projectId: 'calldetector-5d61e',  // ✓ 변경됨
    storageBucket: 'calldetector-5d61e.firebasestorage.app',
    iosBundleId: 'com.designated.designatedCustomerFlutter',
  );
  ```

#### 1-2. GoogleService-Info.plist 배치
- **위치**: `ios/Runner/GoogleService-Info.plist`
- **상태**: ✓ 파일 다운로드 및 배치 완료
- **Bundle ID**: `com.designated.designatedCustomerFlutter`

#### 1-3. Info.plist 권한 설정
- **변경 파일**: `ios/Runner/Info.plist`
- **추가된 권한**:
  - `NSLocationWhenInUseUsageDescription`: 위치 정보 (대리운전 출발지/목적지)
  - `NSMicrophoneUsageDescription`: 음성 입력
  - `NSSpeechRecognitionUsageDescription`: 음성 인식
  - `NSCameraUsageDescription`: QR 코드 스캔
  - `CFBundleDisplayName`: "대리운전 고객" (앱 이름)

---

### Phase 2: FCM 푸시 알림 구현

#### 2-1. FCM 서비스 생성
- **신규 파일**: `lib/services/fcm_service.dart`
- **기능**:
  - FCM 토큰 관리 및 Firestore 저장
  - Foreground/Background/Terminated 메시지 처리
  - 3가지 알림 타입 지원:
    - `DRIVER_ASSIGNED`: 기사 배정 알림
    - `RIDE_COMPLETED`: 운행 완료 알림
    - `CALL_CANCELLED`: 콜 취소 알림

#### 2-2. main.dart 수정
- **변경 내용**:
  - Background message handler 추가
  ```dart
  @pragma('vm:entry-point')
  Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
    await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  }
  ```

#### 2-3. MainNavigation 수정
- **변경 파일**: `lib/core/navigation/main_navigation.dart`
- **추가 기능**:
  - FCM 초기화 로직
  - Foreground/Background 메시지 리스너
  - 알림 타입별 다이얼로그 표시

#### 2-4. Android 권한 추가
- **변경 파일**: `android/app/src/main/AndroidManifest.xml`
- **추가 권한**:
  ```xml
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  ```
- **앱 이름**: "대리운전 고객"

---

### Phase 3: QR 스캔 Attribution 완성

#### 3-1. mobile_scanner 패키지 추가
- **변경 파일**: `pubspec.yaml`
  ```yaml
  dependencies:
    mobile_scanner: ^3.5.5
  ```

#### 3-2. QR 스캐너 화면 생성
- **신규 파일**: `lib/features/attribution/presentation/screens/qr_scanner_screen.dart`
- **기능**:
  - QR 코드 스캔 (사무실 정보)
  - URL 파라미터 파싱: `r`, `o`, `d`, `dn`
  - AttributionResult 반환

#### 3-3. 권한 추가
- **iOS** (`ios/Runner/Info.plist`):
  ```xml
  <key>NSCameraUsageDescription</key>
  <string>QR 코드 스캔을 위해 카메라 권한이 필요합니다.</string>
  ```
- **Android** (`android/app/src/main/AndroidManifest.xml`):
  ```xml
  <uses-permission android:name="android.permission.CAMERA" />
  <uses-feature android:name="android.hardware.camera" android:required="false" />
  ```

---

### Phase 4: 집주소 원터치 기능

#### 4-1. CallRequestScreen 수정
- **변경 파일**: `lib/features/call/screens/call_request_screen.dart`
- **추가 메서드**:
  ```dart
  Future<void> _loadHomeAddress() async {
    final doc = await FirebaseFirestore.instance
      .collection('regions').doc(regionId)
      .collection('offices').doc(officeId)
      .collection('customers').doc(uid)
      .get();

    final homeAddress = doc.data()?['homeAddress'] as String?;
    setState(() {
      _currentLocationController.text = homeAddress;
    });
  }
  ```

#### 4-2. UI 버튼 추가
- **위치**: 출발지 입력 필드 오른쪽
- **디자인**:
  - GPS 버튼 (현재 위치)
  - **집 버튼** (금색 #FFAB00, 집주소 불러오기) ← 신규 추가

---

### Phase 5: Android Release 서명 설정

#### 5-1. build.gradle.kts 수정
- **변경 파일**: `android/app/build.gradle.kts`
- **추가 내용**:
  - `key.properties` 파일 로드 로직
  - Release 서명 설정 (signingConfigs)
  - 유연한 서명 로직 (key.properties 없으면 debug 서명 사용)

#### 5-2. key.properties.template 생성
- **신규 파일**: `android/key.properties.template`
- **내용**: Keystore 생성 안내 및 템플릿

#### 5-3. .gitignore 수정
- **추가 항목**: `android/key.properties` (보안)

---

## 🚀 Mac에서 수행할 작업

### 사전 준비

1. **Flutter 설치 확인**
   ```bash
   flutter --version
   flutter doctor
   ```

2. **Xcode 설치 확인**
   ```bash
   xcode-select --install
   xcrun simctl list devices
   ```

3. **프로젝트 이동**
   - Windows에서 작업한 `designated_customer_flutter` 폴더를 Mac으로 복사

---

### Step 1: 의존성 설치

```bash
cd designated_customer_flutter
flutter pub get
```

**확인사항**:
- ✓ `mobile_scanner` 패키지 설치 확인
- ✓ Firebase 관련 패키지 설치 확인

---

### Step 2: iOS 빌드 테스트

#### 2-1. CocoaPods 설치
```bash
cd ios
pod install
cd ..
```

#### 2-2. Debug 빌드 테스트
```bash
flutter build ios --debug
```

**예상 결과**: 빌드 성공 (경고는 무시 가능)

#### 2-3. Xcode에서 확인
```bash
open ios/Runner.xcworkspace
```

**확인사항**:
1. Signing & Capabilities 탭 확인
   - Team 설정
   - Bundle Identifier: `com.designated.designatedCustomerFlutter`

2. Info.plist 권한 확인
   - Location, Camera, Microphone, Speech Recognition 권한 모두 있는지 확인

3. GoogleService-Info.plist 확인
   - Target Membership에 체크되어 있는지 확인

---

### Step 3: 시뮬레이터/실기기 테스트

#### 3-1. 시뮬레이터 실행
```bash
flutter run
```

#### 3-2. 테스트 체크리스트

**Phase 1-2: FCM 푸시 알림**
- [ ] 앱 실행 시 알림 권한 요청
- [ ] Firestore에 FCM 토큰 저장 확인
- [ ] Firebase Console에서 테스트 메시지 전송
- [ ] Foreground 알림 다이얼로그 표시 확인

**Phase 3: QR 스캔**
- [ ] 카메라 권한 요청
- [ ] QR 코드 스캔 화면 정상 작동
- [ ] QR 스캔 후 Attribution 정보 저장 확인

**Phase 4: 집주소 원터치**
- [ ] 프로필 설정에서 집주소 입력
- [ ] 콜 요청 화면에서 집 버튼(금색) 표시
- [ ] 집 버튼 클릭 시 집주소 자동 입력

**Phase 5: 전체 플로우**
- [ ] QR 스캔 → 로그인 → 프로필 설정 → 콜 요청 → 푸시 알림 수신
- [ ] 모든 권한 정상 요청 (위치, 카메라, 마이크, 알림)

---

### Step 4: Android Release 빌드 (선택사항)

Mac에서 Android Release APK/AAB를 생성하려면:

#### 4-1. Keystore 생성
```bash
keytool -genkey -v -keystore ~/customer-app-release.keystore \
  -alias customer-app -keyalg RSA -keysize 2048 -validity 10000
```

**입력 정보**:
- Keystore 비밀번호: (안전하게 보관)
- Key 비밀번호: (안전하게 보관)
- 이름, 조직 등: 실제 정보 입력

#### 4-2. key.properties 생성
```bash
cd android
cp key.properties.template key.properties
nano key.properties
```

**수정 내용**:
```properties
storeFile=/Users/your-username/customer-app-release.keystore
storePassword=실제_키스토어_비밀번호
keyAlias=customer-app
keyPassword=실제_키_비밀번호
```

#### 4-3. Release 빌드
```bash
cd ..
flutter build apk --release
# 또는
flutter build appbundle --release
```

**결과물 위치**:
- APK: `build/app/outputs/flutter-apk/app-release.apk`
- AAB: `build/app/outputs/bundle/release/app-release.aab`

---

### Step 5: iOS Release 빌드

#### 5-1. Xcode에서 Archive 생성
1. Xcode에서 `ios/Runner.xcworkspace` 열기
2. Product → Destination → **Any iOS Device (arm64)**
3. Product → Archive
4. Organizer에서 Archive 확인

#### 5-2. App Store Connect 업로드
1. Distribute App 선택
2. App Store Connect 선택
3. Upload 진행
4. TestFlight에서 빌드 확인

#### 5-3. TestFlight 베타 테스트
- 내부 테스터 추가
- 베타 테스트 진행
- 크래시, 버그 확인

---

## 📝 알려진 제한사항 및 주의사항

### Windows에서 발생한 제한사항
1. **Git PATH 오류**: Windows 환경에서 Git이 PATH에 없어 빌드 불가
   - Mac에서는 정상 작동 예상

2. **iOS 빌드 불가**: Windows에서는 iOS 빌드 원천적으로 불가능
   - Mac/Xcode 필수

### 네이티브 앱과의 차이점 (미구현 기능)
다음 기능들은 iOS 출시에 필수가 아니므로 생략되었습니다:

1. **만보계 (Pedometer)**:
   - 네이티브 앱에는 있지만, 대리운전 핵심 기능 아님
   - 필요 시 추후 추가 가능

2. **배너 광고**:
   - 네이티브 앱에 Firestore 기반 이미지 광고 있음
   - 수익 모델 확정 후 추가 권장

3. **술게임 (DrinkingGames)**:
   - 네이티브 앱의 부가 기능
   - 필수 아님, 추후 추가 가능

---

## 🔍 검증 체크리스트

### Firebase 설정
- [ ] `ios/Runner/GoogleService-Info.plist` 파일 존재
- [ ] `lib/firebase_options.dart`에서 iOS 프로젝트가 `calldetector-5d61e`
- [ ] Firestore 읽기/쓰기 정상 작동

### 권한 설정
- [ ] Info.plist에 4가지 권한 설명 존재
- [ ] AndroidManifest.xml에 5가지 권한 존재
- [ ] 앱 실행 시 권한 요청 다이얼로그 정상 표시

### 기능 테스트
- [ ] QR 스캔으로 사무실 Attribution 등록
- [ ] 전화번호/인증번호 로그인
- [ ] 프로필 설정 (이름, 집주소)
- [ ] 콜 요청 (출발지, 목적지, 메모)
- [ ] 집주소 원터치 버튼 (금색)
- [ ] FCM 푸시 알림 수신

### 빌드
- [ ] iOS Debug 빌드 성공
- [ ] iOS Release Archive 성공
- [ ] Android Release APK/AAB 생성 (선택사항)

---

## 📚 참고 문서

### Windows에서 생성한 문서
- `iOS_출시_수정계획.md`: 전체 Phase 1-6 계획
- `android/key.properties.template`: Release 서명 템플릿

### 네이티브 앱 비교 문서
프로젝트 루트의 `작업내역/` 폴더 참고

### Firebase Console
- 프로젝트: https://console.firebase.google.com/project/calldetector-5d61e
- Firestore 규칙, Functions, Authentication 확인

---

## 🎯 최종 목표

**iOS App Store 출시 준비 완료**

1. ✅ 모든 필수 기능 구현 (Phase 1-5 완료)
2. ⏳ Mac에서 빌드 및 테스트
3. ⏳ TestFlight 베타 테스트
4. ⏳ App Store 제출

**예상 소요 시간**: Mac 작업 1-2일 (빌드, 테스트, 업로드)

---

## 💡 문제 발생 시

### Firebase 관련 오류
- GoogleService-Info.plist의 Bundle ID 확인
- Firebase Console에서 iOS 앱 등록 확인

### 빌드 오류
- `flutter clean` 후 재빌드
- `pod install` 재실행
- Xcode 캐시 삭제: Product → Clean Build Folder

### 권한 오류
- Info.plist 권한 설명 존재 확인
- iOS 설정 → 앱 → 권한에서 수동 허용

### 푸시 알림 안 옴
- Firebase Console → Cloud Messaging에서 APNs 인증 키 등록 확인
- Xcode → Signing & Capabilities → Push Notifications 추가

---

**작성일**: 2025-12-29
**작성자**: Claude Code
**프로젝트**: designated_customer_flutter v1.0.0

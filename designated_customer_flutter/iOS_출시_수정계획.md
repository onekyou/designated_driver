# Flutter 고객 앱 iOS 출시 수정 계획서

## 프로젝트 정보
- **앱 이름**: designated_customer_flutter
- **현재 버전**: 1.0.0+1
- **목표**: iOS App Store 출시 준비
- **완성도**: 75% → 95% (목표)

---

## 현재 상태 분석

### ✅ 완성된 핵심 기능
- Firebase 익명 인증
- 프로필 설정 (전화번호, 닉네임)
- Attribution 시스템 (Device Fingerprint, 화면 해상도 매칭)
- 콜 요청 (위치 입력, 음성 입력)
- 활성 콜 실시간 조회 (1초 폴링)
- 리워드 포인트 시스템 (조회, 적립, 등급)
- UI/UX (Material3, 다크모드)

### ❌ iOS 출시를 위해 필수적으로 추가해야 할 기능
1. **FCM 푸시 알림** (기사 배정, 운행 완료 알림)
2. **QR 스캔** (Attribution 완성)
3. **집주소 원터치** (UX 개선)
4. **iOS 설정 보완** (Info.plist, GoogleService-Info.plist)

### ⚠️ 선택적 추가 기능
5. 배너 광고 시스템
6. 만보기 기능 (iOS는 HealthKit 필요)
7. 술게임 (우선순위 낮음)

---

## 수정 계획 - 단계별 구현

### **Phase 1: iOS 빌드 환경 설정** (필수)
**목표**: iOS 빌드가 가능한 상태로 만들기

#### 1-1. Firebase 프로젝트 통일
- [ ] Firebase Console에서 `calldetector-5d61e` 프로젝트에 iOS 앱 추가
- [ ] Bundle ID: `com.designated.designatedCustomerFlutter` 등록
- [ ] `GoogleService-Info.plist` 다운로드 후 `ios/Runner/` 에 배치
- [ ] `flutterfire configure` 실행하여 `firebase_options.dart` 재생성

#### 1-2. iOS Info.plist 보완 (이미 완료)
- [x] NSLocationWhenInUseUsageDescription 추가됨
- [x] NSMicrophoneUsageDescription 추가됨
- [x] NSSpeechRecognitionUsageDescription 추가됨
- [x] CFBundleDisplayName: "대리운전 고객" 설정됨

#### 1-3. 빌드 테스트
```bash
cd C:\app_dev\designated_driver\designated_customer_flutter
flutter clean
flutter pub get
flutter build ios --debug
```

**예상 시간**: 2시간
**완료 기준**: iOS 빌드 성공 (에러 없음)

---

### **Phase 2: FCM 푸시 알림 구현** (최우선)
**목표**: 기사 배정, 운행 완료 알림 수신

#### 2-1. FCM 서비스 구현
- [ ] `lib/services/fcm_service.dart` 생성
- [ ] FCM 토큰 요청 및 Firestore 저장 (`customerInfo/{phoneNumber}/fcmToken`)
- [ ] Foreground 알림 수신 핸들러
- [ ] Background 알림 클릭 핸들러
- [ ] iOS APNs 인증서 Firebase Console에 등록

#### 2-2. 알림 타입별 처리
```dart
// 알림 타입:
// - DRIVER_ASSIGNED: 기사 배정 완료
// - RIDE_COMPLETED: 운행 완료
// - CALL_CANCELLED: 콜 취소
```

#### 2-3. main.dart 통합
- [ ] `FcmService.init()` 호출
- [ ] 알림 권한 요청 (iOS/Android)

#### 2-4. 빌드 테스트
```bash
flutter build ios --debug
flutter run --release
# 실기기에서 FCM 알림 수신 테스트
```

**예상 시간**: 8시간
**완료 기준**:
- 앱 시작 시 FCM 토큰 Firestore 저장 확인
- Firebase Console에서 테스트 알림 전송 → 수신 성공

---

### **Phase 3: QR 스캔 Attribution 완성** (중요)
**목표**: QR 코드 스캔으로 사무실 자동 매칭

#### 3-1. QR 스캔 패키지 추가
```yaml
# pubspec.yaml
dependencies:
  qr_code_scanner: ^1.0.1
  # 또는
  mobile_scanner: ^3.5.5  # 더 최신 패키지
```

#### 3-2. QR 스캔 화면 구현
- [ ] `lib/features/attribution/presentation/qr_scanner_screen.dart` 생성
- [ ] 카메라 권한 요청
- [ ] QR 코드 스캔 후 URL 파싱
- [ ] `r`, `o`, `d`, `dn` 파라미터 추출

#### 3-3. iOS 권한 추가
```xml
<!-- ios/Runner/Info.plist -->
<key>NSCameraUsageDescription</key>
<string>QR 코드 스캔을 위해 카메라 권한이 필요합니다.</string>
```

#### 3-4. Attribution 플로우 연결
- [ ] 홈 화면에 "QR 스캔" 버튼 추가
- [ ] QR 스캔 결과 → AttributionRepository 저장
- [ ] 토큰 캐싱 (24시간)

#### 3-5. 빌드 테스트
```bash
flutter build ios --debug
# QR 코드 스캔 테스트
```

**예상 시간**: 6시간
**완료 기준**:
- QR 스캔 성공
- regionId, officeId, driverId 추출
- Firestore 저장 확인

---

### **Phase 4: 집주소 원터치 기능** (UX 개선)
**목표**: 콜 요청 시 집주소 자동 입력

#### 4-1. HomeScreen UI 수정
- [ ] "앱호출" 바텀시트에 "집" 버튼 추가
- [ ] 클릭 시 출발지에 `customerInfo.homeAddress` 자동 입력

#### 4-2. ProfileSetupScreen에 집주소 입력 추가 (이미 있는지 확인)
- [ ] 프로필 설정 시 집주소 저장

#### 4-3. 빌드 테스트
```bash
flutter build ios --debug
```

**예상 시간**: 2시간
**완료 기준**: 집 버튼 클릭 → 출발지 자동 입력

---

### **Phase 5: Android Release 서명 설정** (Google Play 배포용)
**목표**: Google Play 업로드 가능하게 만들기

#### 5-1. 서명 키 생성
```bash
keytool -genkey -v -keystore C:\app_dev\designated_driver\releases\customer-app-release.keystore -alias customer-app -keyalg RSA -keysize 2048 -validity 10000
```

#### 5-2. key.properties 생성
```properties
# android/key.properties
storePassword=<비밀번호>
keyPassword=<비밀번호>
keyAlias=customer-app
storeFile=C:/app_dev/designated_driver/releases/customer-app-release.keystore
```

#### 5-3. build.gradle 수정
```gradle
// android/app/build.gradle
def keystoreProperties = new Properties()
def keystorePropertiesFile = rootProject.file('key.properties')
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        release {
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

#### 5-4. 빌드 테스트
```bash
flutter build appbundle --release
```

**예상 시간**: 2시간
**완료 기준**: Release APK/AAB 빌드 성공

---

### **Phase 6: 최종 테스트 및 배포 준비**

#### 6-1. 앱 메타데이터 준비
- [ ] 앱 설명 작성 (한글/영문)
- [ ] 스크린샷 촬영 (5장 이상)
- [ ] Feature Graphic (Android)
- [ ] 개인정보 처리방침 URL

#### 6-2. 최종 빌드
```bash
# Android
flutter build appbundle --release

# iOS (Mac 필요)
flutter build ios --release
```

#### 6-3. TestFlight 베타 테스트 (iOS)
- [ ] Xcode에서 Archive
- [ ] TestFlight 업로드
- [ ] 베타 테스터 초대 및 테스트

**예상 시간**: 4시간
**완료 기준**:
- Android: Google Play Console 업로드 성공
- iOS: TestFlight 베타 테스트 시작

---

## 선택적 추가 기능 (Phase 7~9)

### **Phase 7: 배너 광고 시스템** (선택)
- Firestore `bannerAds` 컬렉션 연동
- 실시간 모니터링 Stream
- 홈 화면에 배너 표시

**예상 시간**: 4시간

---

### **Phase 8: 만보기 기능** (선택)
- Android: `pedometer` + `flutter_foreground_task`
- iOS: `health` 패키지 (HealthKit)
- Room/Sqflite 로컬 저장

**예상 시간**: 20시간
**iOS 제약**: HealthKit 권한 필요, 백그라운드 제약

---

### **Phase 9: 네트워크 오류 대응** (선택)
- `connectivity_plus` 패키지
- 오프라인 UI 표시
- 재시도 로직

**예상 시간**: 4시간

---

## 총 예상 시간

### 필수 기능 (Phase 1~5)
- Phase 1: 2시간 (Firebase 설정)
- Phase 2: 8시간 (FCM)
- Phase 3: 6시간 (QR 스캔)
- Phase 4: 2시간 (집주소)
- Phase 5: 2시간 (Release 서명)
- **Total: 20시간 (약 2.5일)**

### 배포 준비 (Phase 6)
- Phase 6: 4시간
- **Total: 24시간 (약 3일)**

### 선택 기능 (Phase 7~9)
- Phase 7: 4시간 (배너)
- Phase 8: 20시간 (만보기)
- Phase 9: 4시간 (네트워크)
- **Total: 28시간 (추가 3.5일)**

---

## 우선순위 결정

### 시나리오 A: 빠른 출시 (3일)
```
Phase 1~6 구현
- FCM, QR 스캔, 집주소
- 네이티브 대비 85% 완성도
```

### 시나리오 B: 완성도 높은 출시 (6~7일) ⭐ 권장
```
Phase 1~7 구현
- FCM, QR 스캔, 집주소, 배너 광고
- 네이티브 대비 90% 완성도
```

### 시나리오 C: 완벽 재현 (9~10일)
```
Phase 1~9 모두 구현
- 만보기 포함
- 네이티브 대비 95% 완성도
```

---

## 작업 진행 방식

### 1. 단계별 진행
- 각 Phase를 순차적으로 진행
- Phase 완료 시 체크리스트 확인

### 2. 빌드 확인
- 각 Phase 완료 후 반드시 빌드 테스트
- 에러 발생 시 해결 후 다음 Phase 진행

### 3. Git 커밋
```bash
# Phase 1 완료 후
git add .
git commit -m "feat: iOS 빌드 환경 설정 완료 (Phase 1)"

# Phase 2 완료 후
git add .
git commit -m "feat: FCM 푸시 알림 구현 완료 (Phase 2)"
```

---

## 체크리스트

### Phase 1: iOS 빌드 환경
- [ ] GoogleService-Info.plist 추가
- [ ] firebase_options.dart 재생성
- [ ] Info.plist 권한 확인
- [ ] `flutter build ios` 성공

### Phase 2: FCM 푸시 알림
- [ ] FCM 토큰 Firestore 저장
- [ ] Foreground 알림 수신
- [ ] Background 알림 클릭
- [ ] iOS APNs 설정
- [ ] 실기기 테스트 성공

### Phase 3: QR 스캔
- [ ] qr_code_scanner 패키지 추가
- [ ] QR 스캔 화면 구현
- [ ] 카메라 권한 추가 (Info.plist)
- [ ] Attribution 데이터 저장
- [ ] QR 스캔 테스트 성공

### Phase 4: 집주소 원터치
- [ ] 홈 화면 "집" 버튼 추가
- [ ] 클릭 시 자동 입력
- [ ] 동작 테스트 성공

### Phase 5: Android Release
- [ ] keystore 생성
- [ ] key.properties 설정
- [ ] build.gradle 수정
- [ ] `flutter build appbundle --release` 성공

### Phase 6: 배포 준비
- [ ] 앱 설명 작성
- [ ] 스크린샷 준비
- [ ] 개인정보 처리방침
- [ ] TestFlight 업로드 (iOS)
- [ ] Google Play Console 업로드 (Android)

---

## 위험 요소 및 대응 방안

### 1. Firebase 프로젝트 불일치
**문제**: Android와 iOS가 다른 프로젝트 사용 중
**대응**: flutterfire configure로 통일

### 2. iOS 빌드 에러
**문제**: CocoaPods 의존성 충돌
**대응**:
```bash
cd ios
pod deintegrate
pod install
```

### 3. FCM iOS 알림 수신 실패
**문제**: APNs 인증서 미설정
**대응**: Firebase Console에서 APNs 인증 키 업로드

### 4. QR 스캔 권한 거부
**문제**: 사용자가 카메라 권한 거부
**대응**: 설정 화면으로 이동하는 안내 다이얼로그

---

## 성공 기준

### 최소 성공 기준 (Phase 1~5)
- ✅ iOS 빌드 성공
- ✅ FCM 알림 수신 성공
- ✅ QR 스캔 동작 확인
- ✅ Android Release APK 생성

### 권장 성공 기준 (Phase 1~7)
- ✅ 최소 기준 달성
- ✅ 배너 광고 표시
- ✅ TestFlight 베타 테스트 시작

### 이상적 성공 기준 (Phase 1~9)
- ✅ 권장 기준 달성
- ✅ 만보기 동작 (Android/iOS)
- ✅ 네트워크 오류 대응
- ✅ App Store 심사 제출

---

## 다음 단계

1. **Phase 1 시작**: Firebase 프로젝트 통일
2. 단계별 진행 및 빌드 확인
3. 완료 시 다음 Phase로 이동
4. 모든 Phase 완료 후 배포

---

**작성일**: 2025-12-29
**최종 수정일**: 2025-12-29
**작성자**: Claude Code

# 📱 iOS 빌드 Mac Cloud 완벽 가이드

**작성일**: 2025-01-17
**목표**: Windows 환경에서 iOS 앱 빌드하기

---

## ✅ Mac Cloud 사용, 문제 없습니다!

**결론부터 말씀드리면**: Mac Cloud를 사용해서 iOS 빌드하는 것은 **완전히 가능하고, 일반적인 방법**입니다.

---

## 🎯 Mac Cloud란?

클라우드에서 제공하는 **원격 macOS 환경**입니다.
- Windows PC에서 원격으로 Mac에 접속
- Xcode를 사용하여 iOS 앱 빌드
- App Store에 업로드

### 왜 Mac이 필요한가?
- iOS 앱은 **Xcode**로만 빌드 가능
- Xcode는 **macOS에서만** 실행됨
- 따라서 Mac이 반드시 필요

---

## 🔧 Mac Cloud 솔루션 비교

### 1. **Codemagic** ⭐⭐⭐⭐⭐ (강력 추천!)

**특징**:
- Flutter 전용 CI/CD 서비스
- 클릭 몇 번으로 자동 빌드
- Windows에서 코딩 → Git Push → 자동으로 iOS 빌드
- TestFlight 자동 업로드

**장점**:
- ✅ 무료 플랜 (월 500분 빌드 시간)
- ✅ 설정 초간단 (GUI 기반)
- ✅ Mac 원격 접속 불필요
- ✅ 자동화 (Git Push만 하면 끝)
- ✅ Flutter 최적화

**단점**:
- ❌ 무료 플랜 제한 (500분/월)
- ❌ 빌드 많으면 유료 필요

**가격**:
- 무료: 500분/월
- $40/월: 무제한 빌드
- $80/월: 팀 플랜

**사용 방법**:
```
1. https://codemagic.io 회원가입
2. GitHub 저장소 연결
3. Apple Developer 계정 연동
4. "Start your first build" 클릭
5. 끝!
```

**추천 대상**:
- ✅ 처음 iOS 빌드하는 분
- ✅ Mac이 없는 분
- ✅ 자동화 선호하는 분

---

### 2. **App Center** (Microsoft) ⭐⭐⭐⭐

**특징**:
- Microsoft에서 제공하는 무료 CI/CD
- Flutter 지원

**장점**:
- ✅ 완전 무료
- ✅ 자동 빌드
- ✅ 테스트 배포 기능

**단점**:
- ❌ 설정 복잡 (Codemagic보다)
- ❌ Flutter 최적화 부족

**가격**: 무료

**추천 대상**:
- ✅ 무료 서비스 원하는 분
- ✅ 빌드 횟수 많은 분

---

### 3. **MacStadium** ⭐⭐⭐

**특징**:
- 전용 Mac 서버 대여
- 실제 Mac mini를 클라우드에서 사용

**장점**:
- ✅ 고성능 (전용 서버)
- ✅ 원격 데스크톱으로 Mac 직접 조작
- ✅ 무제한 사용

**단점**:
- ❌ 비쌈 ($100/월)
- ❌ 원격 접속 필요
- ❌ Xcode 사용법 학습 필요
- ❌ 수동 빌드

**가격**:
- Mac mini M1: $100/월
- Mac mini M2: $120/월

**추천 대상**:
- ✅ 예산 충분한 기업
- ✅ Mac 직접 조작 원하는 분

---

### 4. **MacinCloud** ⭐⭐⭐

**특징**:
- 시간당 과금 클라우드 Mac
- 다양한 macOS 버전 지원

**장점**:
- ✅ 시간당 과금 (저렴)
- ✅ 필요할 때만 사용
- ✅ 다양한 플랜

**단점**:
- ❌ 원격 접속 필요
- ❌ 느린 네트워크 시 답답함
- ❌ 수동 빌드

**가격**:
- Pay-as-you-go: $1/시간
- 월 플랜: $30-80/월

**추천 대상**:
- ✅ 가끔 빌드하는 분
- ✅ 비용 절약 원하는 분

---

## 🏆 최종 추천

### 초보자 + 효율성 중시
→ **Codemagic** 사용!

**이유**:
1. 설정 초간단
2. 자동화 (Git Push만 하면 끝)
3. 무료 플랜으로 시작 가능
4. Flutter 최적화

### 예산 충분 + 직접 제어 원함
→ **MacStadium** 사용

---

## 📋 Codemagic 사용 가이드 (단계별)

### 1단계: 계정 생성
1. https://codemagic.io 접속
2. "Start building for free" 클릭
3. GitHub/GitLab/Bitbucket 계정으로 로그인

### 2단계: 앱 추가
1. "Add application" 클릭
2. Flutter 선택
3. 저장소 선택

### 3단계: Apple Developer 연동
1. App Store Connect API Key 생성
   - https://appstoreconnect.apple.com 접속
   - Users and Access → Keys → API Keys
   - "+" 클릭하여 새 Key 생성
   - .p8 파일 다운로드

2. Codemagic에 API Key 등록
   - Team settings → Code signing identities
   - Apple certificates 섹션
   - API Key 업로드

### 4단계: 인증서 설정
1. Certificate 생성 (Automatic)
   - Codemagic에서 자동 생성 가능
   - 또는 수동으로 업로드

2. Provisioning Profile 생성
   - Codemagic에서 자동 생성

### 5단계: 빌드 설정
1. codemagic.yaml 파일 생성

```yaml
# codemagic.yaml
workflows:
  ios-workflow:
    name: iOS Release
    max_build_duration: 60
    instance_type: mac_mini_m1

    environment:
      flutter: stable
      xcode: latest
      cocoapods: default

      vars:
        APP_STORE_CONNECT_ISSUER_ID: your_issuer_id
        APP_STORE_CONNECT_KEY_IDENTIFIER: your_key_id
        APP_STORE_CONNECT_PRIVATE_KEY: |
          -----BEGIN PRIVATE KEY-----
          your_private_key
          -----END PRIVATE KEY-----
        CERTIFICATE_PRIVATE_KEY: |
          -----BEGIN RSA PRIVATE KEY-----
          your_cert_key
          -----END RSA PRIVATE KEY-----

    triggering:
      events:
        - push
      branch_patterns:
        - pattern: 'main'
          include: true

    scripts:
      - name: Install dependencies
        script: |
          flutter pub get

      - name: Build iOS
        script: |
          flutter build ipa --release \
            --export-options-plist=/Users/builder/export_options.plist

    artifacts:
      - build/ios/ipa/*.ipa
      - flutter_drive.log

    publishing:
      email:
        recipients:
          - your@email.com

      app_store_connect:
        api_key: $APP_STORE_CONNECT_PRIVATE_KEY
        key_id: $APP_STORE_CONNECT_KEY_IDENTIFIER
        issuer_id: $APP_STORE_CONNECT_ISSUER_ID
        submit_to_testflight: true
        submit_to_app_store: false
```

### 6단계: 빌드 시작
1. Git에 Push
```bash
git add .
git commit -m "iOS 빌드 준비"
git push origin main
```

2. Codemagic에서 자동으로 빌드 시작
3. 빌드 완료 후 TestFlight에 자동 업로드
4. TestFlight에서 테스트

---

## 🔐 Apple Developer 계정 필수!

### 계정 종류
1. **개인 계정** ($99/년)
   - 개인 이름으로 앱 출시
   - 가장 간단

2. **조직 계정** ($99/년)
   - 회사 이름으로 앱 출시
   - 사업자등록번호 필요

### 가입 방법
1. https://developer.apple.com 접속
2. "Account" 클릭
3. "Enroll" 클릭
4. 개인/조직 선택
5. 정보 입력
6. $99 결제
7. 승인 대기 (1-2일)

---

## 📱 iOS 빌드 필수 파일

### 1. **인증서 (Certificate)**
- Development Certificate: 개발/테스트용
- Distribution Certificate: App Store 출시용

**생성 방법** (Codemagic 자동):
- Codemagic에서 자동 생성 가능

**수동 생성** (필요 시):
1. Keychain Access → Certificate Assistant → Request a Certificate
2. 이메일 입력, "Saved to disk" 선택
3. .certSigningRequest 파일 생성
4. Apple Developer → Certificates → "+" 클릭
5. Distribution 선택
6. .certSigningRequest 업로드
7. .cer 파일 다운로드

### 2. **Provisioning Profile**
- Development: 개발용
- Ad Hoc: 테스트 배포용
- App Store: 출시용

**생성 방법** (Codemagic 자동):
- Codemagic에서 자동 생성

### 3. **APNs (푸시 알림)**
- Authentication Key (.p8): 권장
- Certificate (.p12): 구형

**생성 방법**:
1. Apple Developer → Certificates, IDs & Profiles
2. Keys → "+" 클릭
3. "Apple Push Notifications service (APNs)" 체크
4. 키 이름 입력
5. .p8 파일 다운로드
6. **Key ID와 Team ID 메모** (중요!)

**Firebase에 등록**:
1. Firebase Console → Project Settings
2. Cloud Messaging 탭
3. iOS 앱 선택
4. APNs Authentication Key 업로드
5. Key ID, Team ID 입력

---

## 🚀 전체 워크플로우

```
┌─────────────────────────────────────────────┐
│ 1. Windows에서 Flutter 코딩                   │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│ 2. Git Push (GitHub/GitLab)                 │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│ 3. Codemagic 자동 빌드 시작                   │
│    - Flutter pub get                        │
│    - flutter build ios --release            │
│    - 인증서/프로비저닝 자동 처리               │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│ 4. .ipa 파일 생성                            │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│ 5. TestFlight 자동 업로드                     │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│ 6. iPhone에서 TestFlight 앱으로 테스트         │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│ 7. App Store Connect에서 심사 제출            │
└──────────────┬──────────────────────────────┘
               │
               ▼
┌─────────────────────────────────────────────┐
│ 8. App Store 출시!                           │
└─────────────────────────────────────────────┘
```

**Mac 직접 사용 없음!**
- Windows에서 모든 개발
- Git Push만 하면 자동으로 iOS 빌드
- 원격 접속 불필요

---

## ⚠️ 주의사항

### 1. **APNs 인증서 필수**
- FCM 푸시 알림을 받으려면 APNs 설정 필수
- .p8 파일과 Key ID, Team ID 필요
- Firebase Console에 등록해야 함

### 2. **Bundle Identifier 일치**
- Firebase: com.designated.customer
- Apple Developer: com.designated.customer
- Flutter 프로젝트: com.designated.customer
- 모두 동일해야 함!

### 3. **Info.plist 설정**
iOS는 권한 설명이 **반드시** 필요:

```xml
<!-- ios/Runner/Info.plist -->
<key>NSCameraUsageDescription</key>
<string>QR 코드를 스캔하기 위해 카메라 접근이 필요합니다.</string>

<key>NSLocationWhenInUseUsageDescription</key>
<string>현재 위치를 확인하기 위해 위치 정보 접근이 필요합니다.</string>

<key>NSMicrophoneUsageDescription</key>
<string>음성 입력을 위해 마이크 접근이 필요합니다.</string>

<key>NSHealthShareUsageDescription</key>
<string>걸음 수를 추적하기 위해 건강 데이터 접근이 필요합니다.</string>

<key>NSHealthUpdateUsageDescription</key>
<string>걸음 수를 저장하기 위해 건강 데이터 쓰기 권한이 필요합니다.</string>
```

설명이 없으면 → **앱 심사 거부**

### 4. **iOS 버전 지원**
- 최소 iOS 버전: iOS 12.0 이상 권장
- Flutter 기본값: iOS 11.0
- 너무 낮으면 일부 기능 제한

**설정 방법**:
```ruby
# ios/Podfile
platform :ios, '12.0'
```

### 5. **첫 빌드는 시간 소요**
- 첫 iOS 빌드: 20-30분
- 이후 빌드: 5-10분
- 캐시 때문에 점점 빨라짐

---

## 💰 예상 비용

### 필수 비용
- **Apple Developer**: $99/년
- **Codemagic 무료 플랜**: $0/월 (500분)

### 선택 비용
- **Codemagic 유료 플랜**: $40/월 (무제한)
  - 빌드 많을 경우 필요
  - 월 500분 = 약 25회 빌드 (20분/회 기준)

### 총 비용 (최소)
- 첫 해: $99 (Apple Developer만)
- 이후: $99/년 + Codemagic (필요 시)

---

## 🎯 FAQ

### Q1. Mac이 전혀 없어도 되나요?
**A**: 네! Codemagic를 사용하면 Mac 없이 100% 가능합니다.

### Q2. Windows에서 개발하고 iOS 빌드도 가능한가요?
**A**: 네! 코딩은 Windows에서 하고, Git Push만 하면 Codemagic가 자동으로 iOS 빌드합니다.

### Q3. TestFlight는 무엇인가요?
**A**: Apple에서 제공하는 베타 테스트 플랫폼입니다. App Store 출시 전에 테스터들에게 앱을 배포하여 테스트할 수 있습니다.

### Q4. 빌드 실패하면 어떻게 하나요?
**A**: Codemagic 로그를 확인하세요. 대부분 인증서/프로비저닝 프로필 문제입니다.

### Q5. 무료 플랜으로 충분한가요?
**A**: 개발 초기에는 충분합니다. 월 500분 = 약 25회 빌드 가능.

### Q6. APNs 설정 안 하면 어떻게 되나요?
**A**: FCM 푸시 알림이 iOS에서 작동하지 않습니다. 반드시 설정해야 합니다.

---

## ✅ 체크리스트

### 시작 전 준비
- [ ] Apple Developer 계정 등록 ($99/년)
- [ ] Codemagic 계정 생성 (무료)
- [ ] GitHub 저장소 준비
- [ ] Flutter 프로젝트 준비

### Apple Developer 설정
- [ ] Distribution Certificate 생성
- [ ] App ID 등록 (com.designated.customer)
- [ ] Provisioning Profile 생성
- [ ] APNs Authentication Key 생성 (.p8)

### Firebase 설정
- [ ] iOS 앱 추가
- [ ] Bundle ID 입력 (com.designated.customer)
- [ ] GoogleService-Info.plist 다운로드
- [ ] APNs 인증 키 업로드 (Key ID, Team ID)

### Codemagic 설정
- [ ] 앱 추가
- [ ] Apple Developer 연동
- [ ] codemagic.yaml 작성
- [ ] 빌드 테스트

### iOS 프로젝트 설정
- [ ] Info.plist 권한 설명 추가
- [ ] Bundle Identifier 확인
- [ ] Minimum iOS Version 설정 (12.0)

---

## 📞 도움 필요 시

### Codemagic 공식 문서
- https://docs.codemagic.io/flutter-configuration/flutter-projects/

### Flutter iOS 빌드 가이드
- https://docs.flutter.dev/deployment/ios

### Apple Developer 문서
- https://developer.apple.com/documentation/

---

**작성자**: Claude
**최종 수정일**: 2025-01-17

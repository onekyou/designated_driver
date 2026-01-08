# ✅ Phase 1 완료 체크리스트

**작성일**: 2025-01-17
**목표**: Flutter 프로젝트 셋업 및 개발 환경 구축

---

## 📋 체크리스트

### 1️⃣ Flutter SDK 설치

- [ ] Flutter SDK 다운로드 (https://docs.flutter.dev/get-started/install/windows)
- [ ] `C:\src\flutter` 경로에 압축 해제
- [ ] 환경 변수 Path에 `C:\src\flutter\bin` 추가
- [ ] PowerShell/CMD 재시작
- [ ] `flutter --version` 명령어 실행 확인
- [ ] Flutter 버전: 3.x.x 이상

**테스트 명령어**:
```bash
flutter --version
```

**예상 출력**:
```
Flutter 3.24.5 • channel stable • https://github.com/flutter/flutter.git
Framework • revision abc123... • 2024-xx-xx
Engine • revision def456...
Tools • Dart 3.5.4 • DevTools 2.37.3
```

---

### 2️⃣ Android Studio 설치

- [ ] Android Studio 다운로드 (https://developer.android.com/studio)
- [ ] 설치 (기본 설정)
- [ ] Android SDK 설치 확인
- [ ] Android SDK Build-Tools 설치
- [ ] Android SDK Command-line Tools 설치
- [ ] Android Emulator 설치 (선택)
- [ ] Flutter/Dart 플러그인 설치

**테스트 명령어**:
```bash
flutter doctor
```

**목표 출력**:
```
[✓] Flutter (Channel stable, 3.x.x, on Microsoft Windows)
[✓] Android toolchain - develop for Android devices (Android SDK version xx.x.x)
[✓] Android Studio (version xxxx.x)
[✓] VS Code (version x.x.x) [선택]
[✓] Connected device (1 available)
```

---

### 3️⃣ Android 라이센스 동의

- [ ] `flutter doctor --android-licenses` 실행
- [ ] 모든 라이센스에 `y` 입력

**명령어**:
```bash
flutter doctor --android-licenses
```

---

### 4️⃣ Flutter 프로젝트 생성

- [ ] 작업 디렉토리로 이동
- [ ] Flutter 프로젝트 생성
- [ ] 프로젝트명: `designated_customer_flutter`
- [ ] org: `com.designated`

**명령어**:
```bash
cd C:\app_dev\designated_driver
flutter create designated_customer_flutter --org com.designated
cd designated_customer_flutter
```

**확인**:
- `designated_customer_flutter/` 폴더 생성됨
- `lib/main.dart` 파일 존재
- `pubspec.yaml` 파일 존재

---

### 5️⃣ 디렉토리 구조 생성

- [ ] core 디렉토리 생성
- [ ] features 디렉토리 생성
- [ ] 하위 디렉토리 생성

**명령어 (PowerShell)**:
```powershell
# core 디렉토리
New-Item -Path "lib/core/constants" -ItemType Directory -Force
New-Item -Path "lib/core/theme" -ItemType Directory -Force
New-Item -Path "lib/core/utils" -ItemType Directory -Force
New-Item -Path "lib/core/services" -ItemType Directory -Force
New-Item -Path "lib/core/widgets" -ItemType Directory -Force

# features 디렉토리
New-Item -Path "lib/features/auth/models" -ItemType Directory -Force
New-Item -Path "lib/features/auth/providers" -ItemType Directory -Force
New-Item -Path "lib/features/auth/repositories" -ItemType Directory -Force
New-Item -Path "lib/features/auth/services" -ItemType Directory -Force
New-Item -Path "lib/features/auth/screens" -ItemType Directory -Force

New-Item -Path "lib/features/attribution" -ItemType Directory -Force
New-Item -Path "lib/features/call" -ItemType Directory -Force
New-Item -Path "lib/features/points" -ItemType Directory -Force
New-Item -Path "lib/features/steps" -ItemType Directory -Force
New-Item -Path "lib/features/profile" -ItemType Directory -Force
New-Item -Path "lib/features/games" -ItemType Directory -Force
New-Item -Path "lib/features/home" -ItemType Directory -Force

# assets 디렉토리
New-Item -Path "assets/images" -ItemType Directory -Force
New-Item -Path "assets/icons" -ItemType Directory -Force
New-Item -Path "assets/logos" -ItemType Directory -Force
```

---

### 6️⃣ pubspec.yaml 설정

- [ ] `작업내역/Phase1_pubspec_yaml_템플릿.yaml` 내용 복사
- [ ] `pubspec.yaml` 파일에 붙여넣기
- [ ] 패키지 설치

**명령어**:
```bash
flutter pub get
```

**확인**:
- `.dart_tool/` 폴더 생성
- `pubspec.lock` 파일 생성
- 에러 없이 완료

---

### 7️⃣ 기본 코드 파일 생성

- [ ] `main.dart` 작성
- [ ] `core/constants/app_constants.dart` 작성
- [ ] `core/constants/firebase_constants.dart` 작성
- [ ] `core/constants/point_constants.dart` 작성
- [ ] `core/theme/app_theme.dart` 작성
- [ ] `core/utils/device_fingerprint.dart` 작성
- [ ] `core/services/storage_service.dart` 작성

**참고 파일**:
- `작업내역/Phase1_기본_코드_템플릿.md`

---

### 8️⃣ Firebase CLI 설치

- [ ] Node.js 설치 (https://nodejs.org)
- [ ] npm으로 Firebase CLI 설치
- [ ] Firebase 로그인

**명령어**:
```bash
npm install -g firebase-tools
firebase login
```

---

### 9️⃣ FlutterFire CLI 설치

- [ ] FlutterFire CLI 설치
- [ ] 환경 변수 확인

**명령어**:
```bash
dart pub global activate flutterfire_cli
```

**환경 변수**:
- `C:\Users\[사용자명]\AppData\Local\Pub\Cache\bin` Path 추가

---

### 🔟 Firebase 프로젝트 연동

- [ ] `flutterfire configure` 실행
- [ ] 프로젝트 선택: `designated-driver`
- [ ] 플랫폼: Android, iOS 선택
- [ ] Bundle ID: `com.designated.customer`
- [ ] `firebase_options.dart` 생성 확인

**명령어**:
```bash
cd C:\app_dev\designated_driver\designated_customer_flutter
flutterfire configure
```

**생성되는 파일**:
- `lib/firebase_options.dart` ✅
- `android/app/google-services.json` ✅
- `ios/Runner/GoogleService-Info.plist` ✅

---

### 1️⃣1️⃣ Android Firebase 설정

- [ ] `android/build.gradle` 수정
- [ ] `android/app/build.gradle` 수정
- [ ] `minSdkVersion 21` 설정
- [ ] `google-services.json` 위치 확인

**참고**: `작업내역/Phase1_Firebase_설정_가이드.md`

---

### 1️⃣2️⃣ 빌드 테스트

- [ ] Android 빌드 테스트
- [ ] 에러 없이 완료

**명령어**:
```bash
flutter build apk --debug
```

**성공 메시지**:
```
✓ Built build/app/outputs/flutter-apk/app-debug.apk
```

---

### 1️⃣3️⃣ 앱 실행 테스트

- [ ] 에뮬레이터 또는 실기기 연결
- [ ] 앱 실행
- [ ] 기본 화면 표시 확인

**명령어**:
```bash
# 연결된 기기 확인
flutter devices

# 앱 실행
flutter run
```

**예상 화면**:
- "Designated Customer" 제목
- 택시 아이콘
- "인증 중..." 메시지
- 로딩 인디케이터

---

## 🎯 Phase 1 완료 기준

### 필수 항목 (모두 ✅)

1. ✅ `flutter --version` 명령어 실행됨
2. ✅ `flutter doctor` 에러 없음
3. ✅ Flutter 프로젝트 생성됨
4. ✅ 디렉토리 구조 완성
5. ✅ pubspec.yaml 설정 완료
6. ✅ 기본 코드 파일 작성
7. ✅ Firebase 연동 완료
8. ✅ `flutter build apk --debug` 성공
9. ✅ `flutter run` 실행 확인

### 선택 항목

- [ ] VS Code 설치 및 Flutter 확장 설치
- [ ] Android 에뮬레이터 설정
- [ ] Git 초기화 및 커밋

---

## 📊 완료 확인 스크립트

### Windows PowerShell

```powershell
# Flutter 버전 확인
Write-Host "=== Flutter 버전 ===" -ForegroundColor Green
flutter --version

# Flutter Doctor 확인
Write-Host "`n=== Flutter Doctor ===" -ForegroundColor Green
flutter doctor

# 프로젝트 디렉토리 확인
Write-Host "`n=== 프로젝트 디렉토리 ===" -ForegroundColor Green
Get-ChildItem -Path "C:\app_dev\designated_driver\designated_customer_flutter" -Name

# Firebase 파일 확인
Write-Host "`n=== Firebase 파일 ===" -ForegroundColor Green
Test-Path "C:\app_dev\designated_driver\designated_customer_flutter\lib\firebase_options.dart"
Test-Path "C:\app_dev\designated_driver\designated_customer_flutter\android\app\google-services.json"

Write-Host "`n=== 완료! ===" -ForegroundColor Green
```

---

## 🚀 다음 단계

Phase 1 완료 후:

### Phase 2 준비
- 인증 시스템 구현
- Attribution 시스템 구현
- 보안 저장소 구현

### 진행 방법
1. Phase 1 체크리스트 모두 ✅ 확인
2. 빌드 및 실행 테스트 성공
3. `작업내역/Flutter_전환_마스터플랜_2025.md` 참조
4. Phase 2 시작

---

## 🆘 문제 발생 시

### 도움 받을 곳
- Flutter 공식 문서: https://docs.flutter.dev
- FlutterFire 문서: https://firebase.flutter.dev
- Stack Overflow: https://stackoverflow.com/questions/tagged/flutter

### 공통 문제 해결
- `작업내역/Phase1_Flutter_설치_가이드.md`
- `작업내역/Phase1_Firebase_설정_가이드.md`

---

**작성자**: Claude
**최종 수정일**: 2025-01-17

**Phase 1 완료 시 보고해주세요!** 🎉

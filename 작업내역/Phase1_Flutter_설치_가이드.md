# 🛠 Phase 1: Flutter 설치 가이드 (Windows)

**작성일**: 2025-01-17

---

## 📋 Flutter 설치 단계

### 1단계: Flutter SDK 다운로드

**방법 1: 공식 ZIP 파일 다운로드** (권장)

1. **Flutter 다운로드 페이지 접속**
   - https://docs.flutter.dev/get-started/install/windows

2. **ZIP 파일 다운로드**
   - "Download Flutter SDK" 클릭
   - flutter_windows_[version]-stable.zip 다운로드

3. **압축 해제**
   - 다운로드한 ZIP 파일을 `C:\src\flutter` 경로에 압축 해제
   - ⚠️ **주의**: `C:\Program Files` 같은 경로는 피하세요 (권한 문제)

**권장 경로**:
```
C:\src\flutter
```

---

### 2단계: 환경 변수 설정

1. **시스템 환경 변수 열기**
   - Windows 검색창에 "환경 변수" 입력
   - "시스템 환경 변수 편집" 클릭

2. **Path 편집**
   - "환경 변수" 버튼 클릭
   - "시스템 변수" 섹션에서 "Path" 선택
   - "편집" 클릭
   - "새로 만들기" 클릭
   - `C:\src\flutter\bin` 입력
   - "확인" 클릭

3. **PowerShell/CMD 재시작**
   - 모든 PowerShell 및 CMD 창 닫기
   - 새로 열기

---

### 3단계: Flutter Doctor 실행

새 PowerShell 창을 열고:

```powershell
flutter doctor
```

**예상 출력**:
```
Doctor summary (to see all details, run flutter doctor -v):
[✓] Flutter (Channel stable, 3.x.x, on Microsoft Windows)
[✗] Android toolchain - develop for Android devices
[✗] Chrome - develop for the web
[✗] Visual Studio - develop Windows apps
[✗] Android Studio (not installed)
[✗] VS Code (version x.x.x)
[✗] Connected device
```

**❌ 표시가 있어도 괜찮습니다!** Android toolchain과 Android Studio만 필요합니다.

---

### 4단계: Android Studio 설치 (필수)

Flutter는 Android Studio의 도구들을 사용합니다.

1. **Android Studio 다운로드**
   - https://developer.android.com/studio
   - "Download Android Studio" 클릭

2. **설치**
   - 다운로드한 설치 파일 실행
   - 기본 설정으로 설치
   - "Android SDK", "Android SDK Platform", "Android Virtual Device" 모두 체크

3. **초기 설정**
   - Android Studio 실행
   - "Next" → "Standard" 선택 → "Finish"
   - SDK 다운로드 완료 대기

---

### 5단계: Android SDK 설정

1. **Android Studio 실행**
   - "More Actions" → "SDK Manager" 클릭

2. **SDK Platform 설치**
   - "SDK Platforms" 탭
   - "Android 12.0 (S)" 이상 체크
   - "Apply" 클릭

3. **SDK Tools 설치**
   - "SDK Tools" 탭
   - 다음 항목 체크:
     - ✅ Android SDK Build-Tools
     - ✅ Android SDK Command-line Tools
     - ✅ Android SDK Platform-Tools
     - ✅ Android Emulator
   - "Apply" 클릭

---

### 6단계: Flutter Doctor 재확인

```powershell
flutter doctor
```

**목표 출력**:
```
Doctor summary (to see all details, run flutter doctor -v):
[✓] Flutter (Channel stable, 3.x.x, on Microsoft Windows)
[✓] Android toolchain - develop for Android devices (Android SDK version xx.x.x)
[!] Android Studio (version xxxx.x)
    ✗ Flutter plugin not installed
    ✗ Dart plugin not installed
[✓] VS Code (version x.x.x)
[✓] Connected device (1 available)
```

---

### 7단계: Android Studio Flutter 플러그인 설치

1. **Android Studio 실행**
2. **File → Settings** (또는 Configure → Settings)
3. **Plugins** 클릭
4. **Marketplace** 탭
5. **"Flutter" 검색**
6. **Install** 클릭 (Dart 플러그인도 함께 설치됨)
7. **Restart IDE** 클릭

---

### 8단계: Android 라이센스 동의

```powershell
flutter doctor --android-licenses
```

모든 라이센스에 `y` 입력하여 동의

---

### 9단계: 최종 확인

```powershell
flutter doctor
```

**성공 출력**:
```
Doctor summary (to see all details, run flutter doctor -v):
[✓] Flutter (Channel stable, 3.x.x, on Microsoft Windows)
[✓] Android toolchain - develop for Android devices (Android SDK version xx.x.x)
[✓] Android Studio (version xxxx.x)
[✓] VS Code (version x.x.x)
[✓] Connected device (1 available)

• No issues found!
```

---

## 🎯 설치 후 다음 단계

### Flutter 프로젝트 생성 준비 완료!

이제 다음 명령어로 프로젝트를 생성할 수 있습니다:

```powershell
cd C:\app_dev\designated_driver
flutter create designated_customer_flutter
```

---

## ⚠️ 문제 해결

### 문제 1: "flutter: command not found"
**해결**: 환경 변수 Path에 `C:\src\flutter\bin` 추가 후 PowerShell 재시작

### 문제 2: "Android toolchain" ✗
**해결**: Android Studio 설치 및 SDK 설정

### 문제 3: "cmdline-tools component is missing"
**해결**:
```powershell
flutter doctor --android-licenses
```

### 문제 4: Android 라이센스 동의 실패
**해결**: Android Studio SDK Manager에서 "Android SDK Command-line Tools" 설치

---

## 📞 추가 도움

### 공식 문서
- Flutter 설치 가이드: https://docs.flutter.dev/get-started/install/windows
- Flutter Doctor 문제 해결: https://docs.flutter.dev/get-started/install/windows#run-flutter-doctor

### 권장 IDE

**옵션 1: Android Studio** (초보자 권장)
- Flutter 프로젝트 관리 편리
- 에뮬레이터 통합
- 디버깅 도구 우수

**옵션 2: VS Code** (가볍고 빠름)
- Flutter/Dart 확장 설치 필요
- 가볍고 빠른 편집
- 터미널 통합

---

## ✅ 체크리스트

설치 완료 후 확인:
- [ ] `flutter --version` 명령어 실행됨
- [ ] `flutter doctor` 에러 없음 (✓ 표시)
- [ ] Android Studio 설치됨
- [ ] Flutter/Dart 플러그인 설치됨
- [ ] Android 라이센스 동의 완료

---

**다음 단계**: Flutter 프로젝트 생성

**작성자**: Claude
**최종 수정일**: 2025-01-17

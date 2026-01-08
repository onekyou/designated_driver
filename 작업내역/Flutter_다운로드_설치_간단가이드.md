# 🚀 Flutter 다운로드 & 설치 초간단 가이드

**작성일**: 2025-01-17

---

## 1️⃣ Flutter 다운로드

### 직접 다운로드 (가장 빠름!)

이 링크 클릭:
```
https://storage.googleapis.com/flutter_infra_release/releases/stable/windows/flutter_windows_3.24.5-stable.zip
```

또는 Flutter 공식 사이트에서:
1. https://docs.flutter.dev/get-started/install/windows
2. 페이지의 파란색 "Download" 버튼 클릭

**다운로드 파일**: `flutter_windows_3.24.5-stable.zip` (약 1.5GB)

---

## 2️⃣ 압축 해제

### 중요! 정확한 위치에 설치

1. **C 드라이브** 열기
2. **C:\src** 폴더 생성 (없으면 새로 만들기)
3. 다운로드한 ZIP 파일을 **C:\src**에 압축 해제

**최종 경로**: `C:\src\flutter`

⚠️ **주의사항**:
- ❌ 다운로드 폴더에 두지 마세요
- ❌ Program Files에 설치하지 마세요
- ❌ 한글이 포함된 경로에 설치하지 마세요
- ✅ C:\src\flutter (권장)

---

## 3️⃣ 환경 변수 설정

### Windows 11/10 설정 방법

1. **시작 버튼** 우클릭
2. **"시스템"** 클릭
3. 오른쪽 **"고급 시스템 설정"** 클릭
4. **"환경 변수"** 버튼 클릭
5. **"사용자 변수"** 섹션에서 **"Path"** 선택
6. **"편집"** 버튼 클릭
7. **"새로 만들기"** 클릭
8. 다음 입력: `C:\src\flutter\bin`
9. **"확인"** 클릭 (모든 창에서)

### 또는 PowerShell로 자동 설정

PowerShell을 **관리자 권한**으로 실행하고:

```powershell
# 환경 변수에 Flutter 추가
[Environment]::SetEnvironmentVariable(
    "Path",
    [Environment]::GetEnvironmentVariable("Path", "User") + ";C:\src\flutter\bin",
    "User"
)

Write-Host "Flutter 환경 변수 설정 완료!" -ForegroundColor Green
```

---

## 4️⃣ 설치 확인

### 새 PowerShell/CMD 창 열기

⚠️ **중요**: 환경 변수를 설정했으면 **PowerShell/CMD를 다시 시작**해야 합니다!

기존 창 닫고 → 새 창 열기

### Flutter 버전 확인

```bash
flutter --version
```

**성공 시 출력**:
```
Flutter 3.24.5 • channel stable • https://github.com/flutter/flutter.git
Framework • revision abc123... • 2024-12-04
Engine • revision def456...
Tools • Dart 3.5.4 • DevTools 2.37.3
```

### Flutter Doctor 실행

```bash
flutter doctor
```

**예상 출력**:
```
Doctor summary (to see all details, run flutter doctor -v):
[✓] Flutter (Channel stable, 3.24.5, on Microsoft Windows...)
[✗] Android toolchain - develop for Android devices
    ✗ Android SDK is not installed
[!] Android Studio (not installed)
[✓] VS Code (version 1.xx.x)
[!] Connected device
    ! No devices available

! Doctor found issues in 3 categories.
```

👉 **정상입니다!** Android Studio는 다음 단계에서 설치합니다.

---

## 5️⃣ Android Studio 설치

### 다운로드

https://developer.android.com/studio

### 설치

1. 다운로드한 설치 파일 실행
2. **"Next"** 계속 클릭 (기본 설정 그대로)
3. **"Android SDK"**, **"Android SDK Platform"**, **"Android Virtual Device"** 모두 체크
4. Install 클릭
5. 설치 완료까지 대기 (10-20분)

### Flutter/Dart 플러그인 설치

1. Android Studio 실행
2. **"Plugins"** 메뉴 (또는 환영 화면에서 Plugins)
3. 검색창에 **"Flutter"** 입력
4. **"Flutter" 플러그인** 설치 (Dart도 자동 설치됨)
5. **"Restart IDE"** 클릭

---

## 6️⃣ Android 라이센스 동의

PowerShell/CMD에서:

```bash
flutter doctor --android-licenses
```

**모든 질문에 "y" 입력** (Enter 계속 누르기)

---

## 7️⃣ 최종 확인

```bash
flutter doctor
```

**목표 출력**:
```
[✓] Flutter (Channel stable, 3.24.5, on Microsoft Windows)
[✓] Android toolchain - develop for Android devices (Android SDK version 34.0.0)
[✓] Android Studio (version 2023.1)
[✓] VS Code (version 1.xx.x) [선택사항]
[!] Connected device
    ! No devices available

! Doctor found issues in 1 category.
```

👉 **"Connected device" 경고는 정상입니다!** (에뮬레이터나 실기기 연결 전)

---

## ✅ 설치 완료 확인 체크리스트

### 필수 항목
- [ ] `flutter --version` 명령어 실행됨
- [ ] `flutter doctor` 실행 시 Flutter ✓ 표시
- [ ] `flutter doctor` 실행 시 Android toolchain ✓ 표시
- [ ] `flutter doctor` 실행 시 Android Studio ✓ 표시
- [ ] Android 라이센스 모두 동의 완료

### 설치 경로 확인
- [ ] C:\src\flutter 폴더 존재
- [ ] C:\src\flutter\bin 폴더 존재
- [ ] 환경 변수 Path에 C:\src\flutter\bin 추가됨

---

## 🆘 문제 해결

### 문제 1: "flutter 명령을 인식할 수 없습니다"

**원인**: 환경 변수 미설정 또는 PowerShell 재시작 안 함

**해결**:
1. PowerShell/CMD **완전히 닫기**
2. 새 PowerShell/CMD **관리자 권한**으로 열기
3. `flutter --version` 다시 시도
4. 여전히 안 되면 → 환경 변수 설정 다시 확인

### 문제 2: "Android SDK를 찾을 수 없습니다"

**해결**:
```bash
flutter config --android-sdk C:\Users\[사용자명]\AppData\Local\Android\Sdk
```

### 문제 3: Flutter Doctor에서 cmdline-tools 경고

**해결**:
1. Android Studio 실행
2. File → Settings → Appearance & Behavior → System Settings → Android SDK
3. "SDK Tools" 탭
4. "Android SDK Command-line Tools" 체크
5. Apply

### 문제 4: Android 라이센스 동의 시 에러

**해결**:
```bash
# Java 경로 설정
set JAVA_HOME=C:\Program Files\Android\Android Studio\jbr
flutter doctor --android-licenses
```

---

## 📝 다음 단계

Flutter 설치 완료 후:

1. ✅ **Phase 1 체크리스트** 확인
   - `작업내역/Phase1_완료_체크리스트.md` 참조

2. 🚀 **Flutter 프로젝트 생성**
   ```bash
   cd C:\app_dev\designated_driver
   flutter create designated_customer_flutter --org com.designated
   ```

3. 🔥 **Firebase 설정**
   - `작업내역/Phase1_Firebase_설정_가이드.md` 참조

---

**설치 완료되면 알려주세요!** 🎉

그러면 바로 프로젝트 생성 단계로 넘어가겠습니다.

---

**작성자**: Claude
**최종 수정일**: 2025-01-17

# Call Keeper 앱 생성 작업 내역

## 작업 일시
2025년 9월 22일

## 작업 목적
Call Detector 앱을 기반으로 Call Keeper라는 새로운 독립 앱 생성

## 작업 내용

### 1. 프로젝트 복사
- Call Detector 프로젝트를 call_keeper 폴더로 전체 복사
- PowerShell Copy-Item 명령 사용

### 2. 패키지명 변경
- **기존**: `com.designated.calldetector`
- **변경**: `com.designated.callkeeper`

#### 변경된 파일들:
- `app/build.gradle` - namespace와 applicationId 변경
- 모든 Kotlin 소스 파일들의 package 선언부
- `AndroidManifest.xml`의 패키지 참조
- 모든 import 문과 R 클래스 참조

### 3. 앱 이름 변경
- `app/src/main/res/values/strings.xml`
- **기존**: "Call Detector"
- **변경**: "Call Keeper"

### 4. 빌드 설정 변경
- `app/build.gradle`의 APK 출력 파일명
- **기존**: `call_detector.apk`
- **변경**: `call_keeper.apk`

### 5. Firebase 설정
- `app/google-services.json` 파일에 Call Keeper 앱 정보 추가
- 패키지명: `com.designated.callkeeper`
- 임시 mobilesdk_app_id 할당

### 6. 디렉토리 구조 변경
- 소스 코드 위치 이동:
  - **기존**: `app/src/main/java/com/designated/calldetector/`
  - **변경**: `app/src/main/java/com/designated/callkeeper/`

## 수행된 주요 명령어

```powershell
# 프로젝트 복사
Copy-Item -Path 'call_detector' -Destination 'call_keeper' -Recurse -Force

# 디렉토리 구조 정리
Move-Item -Path 'call_keeper\call_detector\*' -Destination 'call_keeper\' -Force
Remove-Item -Path 'call_keeper\call_detector' -Force -Recurse

# 새 패키지 디렉토리 생성
New-Item -Path 'call_keeper\app\src\main\java\com\designated\callkeeper' -ItemType Directory -Force

# 소스 파일 이동
Move-Item -Path 'call_keeper\app\src\main\java\com\designated\calldetector\*' -Destination 'call_keeper\app\src\main\java\com\designated\callkeeper\' -Force

# 빌드 명령
cd call_keeper
./gradlew clean
./gradlew assembleDebug
```

## 남은 작업

### Firebase Console에서 수행해야 할 작업:
1. Firebase Console 접속
2. 프로젝트에 Android 앱 추가
3. 패키지명: `com.designated.callkeeper` 입력
4. 앱 닉네임: "Call Keeper" 설정
5. google-services.json 파일 다운로드
6. `call_keeper/app/` 폴더에 파일 교체

### 빌드 오류 해결:
- 일부 Kotlin 파일에 문자 인코딩 문제 발생
- Firebase 설정 완료 후 클린 빌드 필요

## 프로젝트 구조

```
call_keeper/
├── app/
│   ├── build.gradle (패키지명 변경됨)
│   ├── google-services.json (Firebase 설정 필요)
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml (패키지명 변경됨)
│           ├── java/com/designated/callkeeper/ (새 패키지 구조)
│           │   ├── BootCompletedReceiver.kt
│           │   ├── CallDetectorApplication.kt
│           │   ├── CallDetectorService.kt
│           │   ├── CallReceiver.kt
│           │   ├── ContactSelectionActivity.kt
│           │   ├── CrashReportService.kt
│           │   ├── ExcludeNumberActivity.kt
│           │   ├── ExcludeNumberManager.kt
│           │   ├── MainActivity.kt
│           │   ├── data/ (데이터 모델)
│           │   ├── ui/ (UI 컴포넌트)
│           │   └── util/ (유틸리티)
│           └── res/
│               └── values/
│                   └── strings.xml (앱 이름 변경됨)
├── gradle/
├── build.gradle
├── settings.gradle
├── gradlew
└── gradlew.bat
```

## 변경 사항 요약

| 항목 | Call Detector (원본) | Call Keeper (신규) |
|------|---------------------|-------------------|
| 패키지명 | com.designated.calldetector | com.designated.callkeeper |
| 앱 이름 | Call Detector | Call Keeper |
| APK 파일명 | call_detector.apk | call_keeper.apk |
| 프로젝트 폴더 | call_detector/ | call_keeper/ |

## 주의 사항
- 두 앱은 완전히 독립적인 앱으로, 동일 기기에 동시 설치 가능
- Firebase에서 별도로 앱을 등록해야 정상 작동
- 모든 기능은 Call Detector와 동일하게 유지됨
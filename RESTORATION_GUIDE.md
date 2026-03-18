# 대리운전 통합 플랫폼 - 새 PC 복원 가이드

> 작성일: 2026-02-18
> 리포지토리: https://github.com/onekyou/designated_driver.git
> Firebase 프로젝트: calldetector-5d61e

---

## 목차

1. [프로젝트 현황 요약](#1-프로젝트-현황-요약)
2. [Git에서 복구되는 것 / 안 되는 것](#2-git에서-복구되는-것--안-되는-것)
3. [새 PC 사전 준비사항](#3-새-pc-사전-준비사항)
4. [Phase 1: 리포지토리 클론 및 브랜치 설정](#4-phase-1-리포지토리-클론-및-브랜치-설정)
5. [Phase 2: Android 개발 환경 설정](#5-phase-2-android-개발-환경-설정)
6. [Phase 3: Firebase 설정 확인](#6-phase-3-firebase-설정-확인)
7. [Phase 4: 앱 서명 키(Keystore) 복원](#7-phase-4-앱-서명키keystore-복원)
8. [Phase 5: Cloud Functions 복원](#8-phase-5-cloud-functions-복원)
9. [Phase 6: Agora PTT 설정 복원](#9-phase-6-agora-ptt-설정-복원)
10. [Phase 7: 각 앱 빌드 및 테스트](#10-phase-7-각-앱-빌드-및-테스트)
11. [Phase 8: 프로덕션 배포](#11-phase-8-프로덕션-배포)
12. [트러블슈팅](#12-트러블슈팅)
13. [각 앱별 상세 스펙](#13-각-앱별-상세-스펙)

---

## 1. 프로젝트 현황 요약

### 앱 구성 (총 5개 Android 앱 + Cloud Functions)

| # | 앱 | 패키지명 | 역할 |
|---|-----|---------|------|
| 1 | call_detector | com.example.calldetector | 수신 전화 자동 감지 → Firebase 업로드 |
| 2 | call_manager | com.designated.callmanager | 관리자용 배차/정산/공유콜 관리 |
| 3 | driver_app | com.designated.driverapp | 기사용 콜 수락/운행 관리 |
| 4 | pickup_app | com.designated.pickupapp | 픽업기사용 (PTT 무전 포함) |
| 5 | Walkietalkie | com.designated.walkietalkie | PTT 무전기 앱 (테스트/독립 모듈) |

### 기술 스택

| 항목 | 버전 |
|------|------|
| Kotlin | 1.9.0 ~ 1.9.22 |
| Android compileSdk | 34 |
| Android minSdk | 24 |
| Android targetSdk | 34 |
| Android Gradle Plugin | 8.2.0 ~ 8.3.1 |
| Gradle Wrapper | 8.10 ~ 8.11.1 |
| Firebase BOM | 32.7.0 ~ 33.1.0 |
| Agora RTC SDK | 4.2.3 ~ 4.2.6 |
| Node.js (Functions) | 22 |
| Jetpack Compose | 사용 (전 앱) |

### Git 브랜치 구조

| 브랜치 | 용도 | 최근 커밋 |
|--------|------|-----------|
| `master` | 프로덕션 메인 | "fix: 권한 요청 무한 루프 및 로직 오류 수정" |
| `firestore-migration-backup` | Firestore 마이그레이션 백업 | "feat: 콜매니저 연결 상태 UI 추가" |
| `backup/20250722` | 롤백 전 WIP 백업 | "WIP backup before rollback" |
| `debug-log-cleanup-backup` | 디버그 로그 정리 백업 | "feat: 회원가입 및 QR 코드 시스템 개선" |

---

## 2. Git에서 복구되는 것 / 안 되는 것

### Git에서 복구 가능 (리포에 포함됨)

| 항목 | 상태 | 비고 |
|------|------|------|
| 5개 앱 전체 소스코드 | O | Kotlin + Jetpack Compose |
| build.gradle / build.gradle.kts | O | 앱별 빌드 설정 전부 |
| Gradle Wrapper (jar + properties) | O | 앱마다 개별 포함 |
| google-services.json (5개 앱 모두) | **O** | **Git에서 추적 중 (별도 다운로드 불필요)** |
| Cloud Functions 소스 (TypeScript) | O | functions/ 디렉토리 |
| Firestore 보안 규칙 | O | firestore.rules |
| Firebase 설정 파일 | O | .firebaserc, firebase.json |
| 프로젝트 문서 전체 | O | CLAUDE.md, SECURITY.md 등 12개+ |
| .idea 설정 일부 | O | 모듈 설정 등 |

### Git에서 복구 불가 (.gitignore 제외 항목)

| 파일 | 용도 | 복원 방법 |
|------|------|-----------|
| `*.keystore` / `*.jks` | 앱 서명 키 | **별도 백업에서 복원 (최우선!)** |
| `local.properties` | Android SDK 경로 | Android Studio가 자동 생성 |
| `functions/node_modules/` | JS 의존성 | `cd functions && npm install` |
| `.gradle/` / `build/` | 빌드 캐시 | Gradle Sync 시 자동 생성 |
| `SENSITIVE_DATA.md` | 실제 비밀 값 | SENSITIVE_DATA_TEMPLATE.md 참고하여 재작성 |
| `credentials/serviceAccountKey.json` | GCP 서비스 계정 키 | Firebase Console에서 재발급 |
| `*.apk` / `*.aab` | 빌드된 앱 | 빌드 시 자동 생성 |

> **중요**: `google-services.json`은 현재 Git에서 **추적되고 있습니다**. 별도 다운로드 없이 클론만으로 복구됩니다.

---

## 3. 새 PC 사전 준비사항

### 필수 소프트웨어 설치

```
1. Git                    → https://git-scm.com/
2. Android Studio         → https://developer.android.com/studio (최신 안정 버전)
3. JDK 17                 → Android Studio 내장 JDK 사용 권장
4. Node.js 22 LTS         → https://nodejs.org/
5. Firebase CLI           → npm install -g firebase-tools
```

### 확인해야 할 별도 백업

```
□ Keystore 파일 (*.keystore 또는 *.jks) — Play Store 업데이트에 필수
□ Keystore 비밀번호 (store password, key alias, key password)
□ Agora App Certificate 값
□ Firebase 서비스 계정 키 (선택적)
```

---

## 4. Phase 1: 리포지토리 클론 및 브랜치 설정

```bash
# 1. 리포지토리 클론
git clone https://github.com/onekyou/designated_driver.git
cd designated_driver

# 2. 기본 브랜치 확인 (master)
git branch -a

# 3. 필요 시 다른 브랜치 체크아웃
git checkout firestore-migration-backup    # Firestore 마이그레이션 백업
git checkout backup/20250722               # 2025.07.22 WIP 백업
git checkout debug-log-cleanup-backup      # 디버그 로그 정리 백업

# 4. 다시 master로 돌아오기
git checkout master
```

### 프로젝트 디렉토리 구조 확인

```
designated_driver/
├── call_detector/          # 전화 감지 앱
│   ├── app/
│   │   ├── build.gradle
│   │   ├── google-services.json  ✓
│   │   └── src/main/
│   ├── gradle/wrapper/
│   └── gradlew, gradlew.bat
│
├── call_manager/           # 관리자 앱
│   ├── app/
│   │   ├── build.gradle
│   │   ├── google-services.json  ✓
│   │   └── src/main/
│   ├── gradle/wrapper/
│   └── gradlew, gradlew.bat
│
├── driver_app/             # 기사 앱
│   ├── app/
│   │   ├── build.gradle
│   │   ├── google-services.json  ✓
│   │   └── src/main/
│   ├── gradle/wrapper/
│   └── gradlew, gradlew.bat
│
├── pickup_app/             # 픽업기사 앱
│   ├── app/
│   │   ├── build.gradle.kts
│   │   ├── google-services.json  ✓
│   │   └── src/main/
│   ├── gradle/wrapper/
│   └── gradlew, gradlew.bat
│
├── Walkietalkie/           # PTT 무전기 앱
│   ├── app/
│   │   ├── build.gradle
│   │   ├── google-services.json  ✓
│   │   └── src/main/
│   ├── gradle/wrapper/
│   └── gradlew, gradlew.bat
│
├── functions/              # Firebase Cloud Functions (TypeScript)
│   ├── package.json
│   ├── tsconfig.json
│   └── src/
│
├── firestore.rules         # Firestore 보안 규칙
├── firebase.json           # Firebase 배포 설정
├── .firebaserc             # Firebase 프로젝트 연결 (calldetector-5d61e)
├── CLAUDE.md               # 프로젝트 전체 문서
├── SECURITY.md             # 보안 정책
├── SENSITIVE_DATA_TEMPLATE.md  # 민감 데이터 템플릿
└── .gitignore
```

---

## 5. Phase 2: Android 개발 환경 설정

### 5-1. Android Studio에서 프로젝트 열기

각 앱은 **독립적인 Android 프로젝트**입니다. Android Studio에서 하나씩 열어야 합니다.

```
File → Open → designated_driver/call_detector   (또는 다른 앱 폴더)
```

### 5-2. local.properties 자동 생성

Android Studio가 프로젝트를 열면 `local.properties` 파일이 자동으로 생성됩니다.

```properties
# 자동 생성 예시 (새 PC의 SDK 경로에 맞게)
sdk.dir=C\:\\Users\\{사용자명}\\AppData\\Local\\Android\\Sdk
```

> **참고**: 기존 Walkietalkie/local.properties에는 이전 PC의 경로 `C:\\Users\\onekyou\\...`가 기록되어 있습니다. 새 PC에서는 삭제 후 재생성하세요.

### 5-3. Gradle Sync

각 앱 폴더를 Android Studio에서 열면 Gradle Sync가 자동으로 시작됩니다.
필요한 SDK 컴포넌트가 없으면 Android Studio가 다운로드를 제안합니다.

**필요한 Android SDK 컴포넌트**:
- Android SDK Platform 34
- Build-Tools (최신)
- NDK (Agora SDK 사용 앱: call_manager, pickup_app, Walkietalkie)

---

## 6. Phase 3: Firebase 설정 확인

### 6-1. google-services.json (이미 포함됨)

모든 5개 앱에 `google-services.json`이 Git에 포함되어 있으므로 **별도 작업 불필요**합니다.

만약 Firebase 프로젝트를 새로 만들거나 변경할 경우에만:
1. Firebase Console → 프로젝트 설정 → 일반
2. 각 Android 앱의 패키지명으로 등록
3. `google-services.json` 다운로드
4. 각 앱의 `app/` 디렉토리에 배치

### 6-2. Firebase 프로젝트 접근 확인

```bash
# Firebase CLI 로그인
firebase login

# 프로젝트 목록 확인
firebase projects:list

# 현재 프로젝트 확인
firebase use
# 출력: calldetector-5d61e
```

### 6-3. Firestore 보안 규칙 배포

```bash
cd designated_driver
firebase deploy --only firestore:rules
```

### 6-4. 실시간 데이터베이스 규칙 배포

```bash
firebase deploy --only database
```

---

## 7. Phase 4: 앱 서명 키(Keystore) 복원

> **이 단계가 가장 중요합니다.** Keystore가 없으면 Play Store에 업데이트를 배포할 수 없습니다.

### 7-1. Keystore 파일 복원

별도 백업에서 keystore 파일(`.keystore` 또는 `.jks`)을 복원하여 각 앱 디렉토리에 배치합니다.

```
designated_driver/
├── call_detector/app/       ← keystore 파일 배치
├── call_manager/app/        ← keystore 파일 배치
├── driver_app/app/          ← keystore 파일 배치
├── pickup_app/app/          ← keystore 파일 배치
└── Walkietalkie/app/        ← keystore 파일 배치
```

### 7-2. 서명 설정 (build.gradle에 추가)

각 앱의 `app/build.gradle`에 서명 설정이 없다면 추가해야 합니다:

```groovy
android {
    signingConfigs {
        release {
            storeFile file('your-keystore.jks')
            storePassword '스토어_비밀번호'
            keyAlias '키_별칭'
            keyPassword '키_비밀번호'
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
        }
    }
}
```

> **보안 팁**: 비밀번호를 build.gradle에 직접 넣지 말고, `keystore.properties` 파일을 사용하세요:
>
> ```properties
> # app/keystore.properties (이 파일은 .gitignore에 추가)
> storeFile=your-keystore.jks
> storePassword=스토어_비밀번호
> keyAlias=키_별칭
> keyPassword=키_비밀번호
> ```

### 7-3. Keystore를 잃어버린 경우

**Play Store에 이미 출시된 앱이라면**:
- Google Play App Signing을 사용 중이면 → 새 업로드 키 생성 후 Google에 요청
- 사용하지 않으면 → **해당 앱은 업데이트 불가** (새 패키지명으로 재출시 필요)

**아직 출시하지 않은 앱이라면**:
```bash
keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias your-alias
```

---

## 8. Phase 5: Cloud Functions 복원

### 8-1. 의존성 설치

```bash
cd designated_driver/functions
npm install
```

### 8-2. TypeScript 빌드

```bash
npm run build
```

### 8-3. Agora Secret 설정

Agora App Certificate를 Firebase Secret Manager에 등록:

```bash
firebase functions:secrets:set AGORA_APP_CERTIFICATE
# 프롬프트에 실제 Agora App Certificate 값 입력
```

### 8-4. 로컬 테스트 (선택)

```bash
npm run serve    # Firebase Emulator로 로컬 실행
```

### 8-5. 프로덕션 배포

```bash
firebase deploy --only functions
```

### 주요 Cloud Functions 목록

| 함수명 | 트리거 | 역할 |
|--------|--------|------|
| oncallassigned | Firestore onWrite | 배차 시 기사에게 FCM 알림 |
| onSharedCallClaimed | Firestore onWrite | 공유콜 수락 처리 + 포인트 정산 |
| onSharedCallCreated | Firestore onCreate | 새 공유콜 알림 |
| sendNewCallNotification | Firestore onCreate | 새 콜 발생 시 관리자 알림 |
| finalizeWorkDay | Callable | 일일 정산 마감 처리 |

---

## 9. Phase 6: Agora PTT 설정 복원

### 9-1. Agora App ID

이미 소스코드에 하드코딩되어 있습니다:

```
App ID: e5aae3aa18484cd2a1fed0018cfb15bd
```

해당 위치:
- `call_manager/app/build.gradle` → `buildConfigField`
- `pickup_app/app/build.gradle.kts` → `buildConfigField`

### 9-2. Agora App Certificate

Firebase Secret Manager에 저장됨. [Phase 5의 8-3 단계](#8-3-agora-secret-설정) 참고.

### 9-3. 관련 문서

- `AGORA_SECRET_SETUP.md` — Agora 시크릿 설정 상세 가이드
- `PRODUCTION_PTT_SETUP.md` — 프로덕션 PTT 배포 가이드
- `PTT_Config.md` — PTT 시스템 설정 상세

---

## 10. Phase 7: 각 앱 빌드 및 테스트

### 빌드 순서 (권장)

```
1. call_detector   → 가장 단순, 의존성 적음
2. driver_app      → Hilt DI 사용, Firebase 연결 확인
3. call_manager    → 가장 복잡, Room DB + Agora + Firebase
4. pickup_app      → Agora PTT + Hilt
5. Walkietalkie    → Agora PTT 독립 모듈
```

### 앱별 빌드 (커맨드라인)

```bash
# call_detector
cd designated_driver/call_detector
./gradlew assembleDebug

# call_manager
cd designated_driver/call_manager
./gradlew assembleDebug

# driver_app
cd designated_driver/driver_app
./gradlew assembleDebug

# pickup_app
cd designated_driver/pickup_app
./gradlew assembleDebug

# Walkietalkie
cd designated_driver/Walkietalkie
./gradlew assembleDebug
```

### 빌드 확인 체크리스트

각 앱에 대해:

```
□ Gradle Sync 성공
□ Debug 빌드 성공
□ 실제 기기에 설치 가능
□ Firebase 연결 확인 (Firestore 읽기/쓰기)
□ FCM 토큰 정상 발급
□ (PTT 앱) Agora 연결 확인
```

---

## 11. Phase 8: 프로덕션 배포

### 11-1. Release APK/AAB 빌드

```bash
# 예: call_manager
cd designated_driver/call_manager
./gradlew bundleRelease    # AAB (Play Store용)
./gradlew assembleRelease  # APK (직접 배포용)
```

### 11-2. 전체 배포 체크리스트

```
□ Keystore로 Release 서명 완료
□ ProGuard/R8 난독화 확인
□ google-services.json이 프로덕션 Firebase 프로젝트를 가리키는지 확인
□ Cloud Functions 배포 완료
□ Firestore 보안 규칙 배포 완료
□ Agora App Certificate Secret 설정 완료
□ FCM 알림 테스트 완료
□ Play Store에 AAB 업로드
```

---

## 12. 트러블슈팅

### 빌드 오류

| 증상 | 원인 | 해결 |
|------|------|------|
| `SDK location not found` | local.properties 없음 | Android Studio에서 프로젝트 열면 자동 생성 |
| `Could not resolve com.google.gms:google-services` | Google 서비스 플러그인 | buildscript에 google() 리포지토리 확인 |
| `Execution failed for task ':app:processDebugGoogleServices'` | google-services.json 누락 | 이미 Git에 포함됨, 경로 확인 |
| NDK 관련 오류 | Agora SDK에 NDK 필요 | Android Studio → SDK Manager → NDK 설치 |
| `Unresolved reference: BuildConfig` | Gradle Sync 필요 | Build → Rebuild Project |

### Firebase 연결 오류

| 증상 | 원인 | 해결 |
|------|------|------|
| `Default FirebaseApp is not initialized` | google-services.json 불일치 | 패키지명과 Firebase 앱 등록 확인 |
| Firestore 읽기/쓰기 실패 | 보안 규칙 미배포 | `firebase deploy --only firestore:rules` |
| FCM 알림 미수신 | 토큰 미갱신 | 앱 재설치 후 토큰 갱신 확인 |
| Cloud Functions 호출 실패 | 함수 미배포 | `firebase deploy --only functions` |

### Agora PTT 오류

| 증상 | 원인 | 해결 |
|------|------|------|
| `Token generation failed` | App Certificate 미설정 | `firebase functions:secrets:set AGORA_APP_CERTIFICATE` |
| 음성 송수신 안됨 | 마이크 권한 | 앱 설정에서 마이크 권한 허용 |
| 채널 접속 실패 | 네트워크 또는 토큰 만료 | 네트워크 확인, 토큰 재발급 (24시간 만료) |

---

## 13. 각 앱별 상세 스펙

### call_detector

| 항목 | 값 |
|------|-----|
| 패키지명 | com.example.calldetector |
| Gradle Wrapper | 8.11.1 |
| AGP | 8.2.0 |
| 주요 의존성 | Firebase Firestore, Analytics, OkHttp3 |
| 필수 권한 | READ_PHONE_STATE, READ_CALL_LOG, FOREGROUND_SERVICE |
| 핵심 클래스 | CallReceiver, CallDetectorService, DetectorConfigViewModel |

### call_manager

| 항목 | 값 |
|------|-----|
| 패키지명 | com.designated.callmanager |
| Gradle Wrapper | 8.10 |
| AGP | 8.3.1 |
| Kotlin | 1.9.22 |
| 주요 의존성 | Firebase (Auth, Firestore, Messaging, Functions), Room DB, Agora RTC 4.2.3, Hilt(없음), Navigation Compose |
| 핵심 화면 | DashboardScreen, SettlementTabHost, SharedCallSettingsScreen, DriverManagementScreen |

### driver_app

| 항목 | 값 |
|------|-----|
| 패키지명 | com.designated.driverapp |
| Gradle Wrapper | 8.10 |
| AGP | 8.3.1 |
| Kotlin | 1.9.22 |
| 주요 의존성 | Firebase (Auth, Firestore, Messaging), Hilt DI, Play Services Location, reCAPTCHA |
| 핵심 화면 | HomeScreen, WaitingScreen, TripPreparationScreen, InProgressScreen, HistorySettlementScreen |

### pickup_app

| 항목 | 값 |
|------|-----|
| 패키지명 | com.designated.pickupapp |
| Gradle Wrapper | 8.10 |
| 빌드 스크립트 | build.gradle.kts (Kotlin DSL) |
| 주요 의존성 | Firebase, Agora RTC 4.2.3, Hilt DI, Media (볼륨키 PTT 제어) |
| 특이사항 | 볼륨 키로 PTT 송신 제어, MediaSession 사용 |

### Walkietalkie

| 항목 | 값 |
|------|-----|
| 패키지명 | com.designated.walkietalkie |
| Gradle Wrapper | 8.10 |
| AGP | 8.2.0 |
| 주요 의존성 | Firebase, Agora SDK 4.2.6, Coroutines |
| 특이사항 | PTT 독립 테스트 모듈, local.properties가 Git에 포함됨 (삭제 후 재생성 필요) |

---

## 빠른 참조: 전체 복원 명령어 요약

```bash
# 1. 클론
git clone https://github.com/onekyou/designated_driver.git
cd designated_driver

# 2. 브랜치 확인
git checkout master   # 또는 firestore-migration-backup

# 3. Cloud Functions 의존성 설치
cd functions && npm install && cd ..

# 4. Firebase CLI 설정
firebase login
firebase use calldetector-5d61e

# 5. Firebase 규칙 배포
firebase deploy --only firestore:rules
firebase deploy --only database

# 6. Agora Secret 설정
firebase functions:secrets:set AGORA_APP_CERTIFICATE

# 7. Cloud Functions 배포
firebase deploy --only functions

# 8. 각 앱을 Android Studio에서 열고:
#    - local.properties 자동 생성 확인
#    - Keystore 파일 배치
#    - Gradle Sync → Build → Run
```

---

## 최우선 확인사항

```
⚠️  Keystore 파일 백업 여부를 반드시 확인하세요.
    없으면 Play Store 업데이트가 불가능합니다.

⚠️  Agora App Certificate 값을 기록해 두셨는지 확인하세요.
    PTT 기능 작동에 필수입니다.
```

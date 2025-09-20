# Google Play Console 업로드 작업 완료 보고서

## 📅 작업 일자
- **작성일**: 2025-09-21
- **작업 완료**: 2025-09-21

---

## 🎯 작업 목표
Google Play Console에 대리운전 통합 플랫폼의 3개 앱(Call Detector, Call Manager, Driver App)을 업로드하고 내부 테스트 준비 완료

---

## ✅ 완료된 작업 내역

### 1. 환경 설정
- ✅ Java 17 LTS 설치 (Microsoft OpenJDK 17.0.16)
- ✅ Java PATH 환경변수 등록
- ✅ PEPK 도구 설정 및 실행

### 2. API 레벨 업데이트 (Google Play 요구사항)
- ✅ 모든 앱 compileSdk 34 → 35 변경
- ✅ 모든 앱 targetSdk 34 → 35 변경
- ✅ Google Play Console API 35 요구사항 충족

### 3. 키스토어 생성 및 관리
- ✅ Call Detector: 기존 키스토어 변경 (PEPK 도구 사용)
- ✅ Call Manager: 새 키스토어 생성
- ✅ Driver App: 새 키스토어 생성

---

## 🔑 키스토어 정보

### 공통 비밀번호
- **모든 키스토어 비밀번호**: `dnjsrb2367`
- **모든 키 비밀번호**: `dnjsrb2367`

### 1. Call Detector (콜 감지기)
```
경로: C:\app_dev\designated_driver\keystore\upload-keystore.jks
별칭(Alias): upload
패키지명: com.designated.calldetector
버전: 1.0.1 (versionCode: 2)
```

### 2. Call Manager (콜 매니저)
```
경로: C:\app_dev\designated_driver\keystore\callmanager-keystore.jks
별칭(Alias): callmanager
패키지명: com.designated.callmanager
버전: 1.0.0 (versionCode: 1)
```

### 3. Driver App (기사 앱)
```
경로: C:\app_dev\designated_driver\keystore\driverapp-keystore.jks
별칭(Alias): driverapp
패키지명: com.designated.driverapp
버전: 1.0.0 (versionCode: 1)
```

### 키스토어 Certificate 정보 (공통)
```
First and Last Name: Yangonekyou
Organizational Unit: Development
Organization: D2R2
City or Locality: Seoul
State or Province: Seoul
Country Code: KR
Validity: 25 years
```

---

## 📱 앱별 build.gradle 설정

### 모든 앱 공통 설정
```gradle
android {
    compileSdk 35

    defaultConfig {
        minSdk 24
        targetSdk 35
    }
}
```

### Call Detector
```gradle
versionCode 2
versionName "1.0.1"
```

### Call Manager & Driver App
```gradle
versionCode 1
versionName "1.0.0"
```

---

## 🛠 사용된 도구 및 명령어

### PEPK 도구 실행 (Call Detector용)
```bash
java -jar pepk.jar --keystore="C:\app_dev\designated_driver\keystore\upload-keystore.jks" --alias=upload --output=output.zip --include-cert --rsa-aes-encryption --encryption-key-path="C:\Users\onekyou\Downloads\encryption_public_key.pem"
```

### AAB 파일 빌드
```bash
# 각 프로젝트 폴더에서 실행
gradlew clean bundleRelease
```

### AAB 파일 위치
```
Call Detector: call_detector\app\build\outputs\bundle\release\app-release.aab
Call Manager: call_manager\app\build\outputs\bundle\release\app-release.aab
Driver App: driver_app\app\build\outputs\bundle\release\app-release.aab
```

---

## 📋 Google Play Console 설정

### 필요한 추가 작업
1. **개인정보처리방침 URL 등록** (READ_PHONE_STATE 권한 때문에 필수)
2. **앱 설명 및 스크린샷 업로드**
3. **내부 테스트 그룹 설정**
4. **테스터 이메일 추가**

### 앱별 권한 설명
- **Call Detector**: 전화 상태 읽기, 통화 기록, 연락처, SMS 발송
- **Call Manager**: 위치 정보, 인터넷, 알림
- **Driver App**: 위치 정보, 인터넷, 알림, 백그라운드 위치

---

## 🚨 중요 주의사항

1. **키스토어 백업 필수**
   - 모든 키스토어 파일을 안전한 곳에 백업
   - 키스토어 분실 시 앱 업데이트 불가

2. **버전 관리**
   - 업데이트할 때마다 versionCode 증가 필요
   - versionName은 사용자에게 보이는 버전

3. **API 레벨 유지**
   - 현재 targetSdk 35 설정 유지
   - 향후 Google Play 정책 변경 시 업데이트 필요

4. **테스트 절차**
   - 내부 테스트 → 비공개 테스트 → 프로덕션
   - 각 단계별 충분한 테스트 필요

---

## 📝 다음 단계

1. Google Play Console에서 앱 정보 완성
2. 개인정보처리방침 작성 및 URL 등록
3. 내부 테스트 진행
4. 테스트 피드백 반영
5. 프로덕션 출시 준비

---

## 💡 문제 해결 기록

### Java 실행 문제
- 문제: Android Studio의 JBR Java가 손상됨
- 해결: Microsoft OpenJDK 17 설치 및 PATH 등록

### PEPK 도구 실행 문제
- 문제: 콘솔에서 비밀번호 입력 불가
- 해결: 실제 명령 프롬프트에서 직접 실행

### API 레벨 요구사항
- 문제: Google Play에서 API 35 이상 요구
- 해결: 모든 앱의 targetSdk를 35로 업데이트

---

**작성자**: Claude Assistant
**검토자**: Yangonekyou
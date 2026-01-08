# Play Store Install Referrer 테스트 가이드

## 개요

Play Store Install Referrer API가 Native Android 고객앱에 성공적으로 구현되었습니다. 이 문서는 테스트 방법을 설명합니다.

## 구현 내용

### 1. Install Referrer API 추가

**파일: `customer_app/app/app/build.gradle.kts`**
```kotlin
// Install Referrer API
implementation("com.android.installreferrer:installreferrer:2.2")
```

### 2. MainActivity에 Install Referrer 체크 로직 추가

**파일: `customer_app/app/app/src/main/java/com/designated/customer/MainActivity.kt`**

#### checkInstallReferrer() 메서드 (lines 274-326)
- 앱 시작 시 자동으로 호출됨 (onCreate에서 line 188)
- 이미 사무실 정보가 저장되어 있으면 스킵
- InstallReferrerClient를 사용하여 Play Store로부터 referrer 데이터 수신
- 수신 성공 시 parseAndSaveReferrer() 호출

#### parseAndSaveReferrer() 메서드 (lines 332-381)
- URL 디코딩 수행
- 파라미터 파싱:
  - `r`: regionId (지역ID)
  - `o`: officeId (사무실ID)
  - `driver`: driverId (추천 기사ID)
  - `driverName`: 추천 기사 이름
  - `phone`: 사무실 전화번호
  - `bank`: 은행명
  - `account`: 계좌번호
  - `holder`: 예금주
- SharedPreferences에 정보 저장

### 3. 삭제된 파일
- `AttributionMatchingService.kt` - 더 이상 필요하지 않음 (핑거프린트 매칭 방식)

## 테스트 방법

### ⚠️ 중요: APK 직접 설치로는 Install Referrer 테스트 불가

Install Referrer API는 **반드시 Google Play Store를 통한 설치에서만** 작동합니다.
APK 파일을 직접 다운로드하여 설치하는 경우, Install Referrer 데이터가 전달되지 않습니다.

### 방법 1: Play Store 내부 테스트 트랙 사용 (권장)

1. **Google Play Console 설정**
   ```
   - Play Console에 앱 등록
   - 내부 테스트 트랙 생성
   - APK 업로드
   - 테스터 이메일 추가
   ```

2. **테스트 링크 생성**
   ```
   기본 Play Store URL:
   https://play.google.com/store/apps/details?id=com.designated.customer

   Referrer 파라미터 추가:
   https://play.google.com/store/apps/details?id=com.designated.customer&referrer=r%3DHongchon%26o%3DqwfdeSOL8Vz4lXEEP4TD%26phone%3D01036702011%26bank%3DNH%EB%86%8D%ED%98%91%EC%9D%80%ED%96%89%26account%3D3333149014688%26holder%3Dggg
   ```

3. **테스트 절차**
   ```
   1) 모바일 기기에서 위 링크 클릭
   2) Play Store 앱이 열리면서 앱 설치 페이지 표시
   3) "설치" 버튼 클릭하여 앱 설치
   4) 앱 실행 후 logcat 확인
   ```

4. **로그 확인**
   ```bash
   # PC에서 실행
   adb logcat | grep InstallReferrer

   # 예상 로그:
   D InstallReferrer: Install Referrer 받음: r=Hongchon&o=qwfdeSOL8Vz4lXEEP4TD&phone=...
   D InstallReferrer: 디코딩된 Referrer: r=Hongchon&o=qwfdeSOL8Vz4lXEEP4TD&phone=...
   D InstallReferrer: ✅ 사무실 정보 저장 완료: Hongchon/qwfdeSOL8Vz4lXEEP4TD
   ```

### 방법 2: Custom Tabs를 통한 시뮬레이션 (대안)

Play Store 배포 전 로컬 테스트가 필요한 경우:

1. **테스트 앱 생성**
   ```kotlin
   // 별도의 테스트 앱에서 실행
   val uri = Uri.parse("market://details?id=com.designated.customer&referrer=r%3DHongchon%26o%3Dtest123")
   val intent = Intent(Intent.ACTION_VIEW, uri)
   startActivity(intent)
   ```

2. **로그 확인**
   - Install Referrer가 정상적으로 수신되는지 logcat에서 확인

### 방법 3: 현재 APK 직접 설치 (Fallback 테스트)

APK를 직접 설치하는 경우, Install Referrer는 작동하지 않지만 기존 토큰 기반 매칭이 fallback으로 작동합니다.

1. **APK 설치**
   ```bash
   adb install -r C:\app_dev\designated_driver\releases\customer_app.apk
   ```

2. **예상 로그**
   ```
   D InstallReferrer: Referrer URL이 비어있음
   # 또는
   W InstallReferrer: Play Store 서비스를 사용할 수 없음

   # 그 후 토큰 매칭으로 fallback
   D AttributionMatching: 토큰 발견: xxx
   ```

## URL 파라미터 형식

### 기본 형식
```
r=<regionId>&o=<officeId>&phone=<전화번호>&bank=<은행명>&account=<계좌번호>&holder=<예금주>
```

### 예시 (URL 인코딩 전)
```
r=Hongchon&o=qwfdeSOL8Vz4lXEEP4TD&phone=01036702011&bank=NH농협은행&account=3333149014688&holder=홍길동
```

### 예시 (URL 인코딩 후)
```
r%3DHongchon%26o%3DqwfdeSOL8Vz4lXEEP4TD%26phone%3D01036702011%26bank%3DNH%EB%86%8D%ED%98%91%EC%9D%80%ED%96%89%26account%3D3333149014688%26holder%3D%ED%99%8D%EA%B8%B8%EB%8F%99
```

### 기사 추천 정보 포함 (선택사항)
```
r=Hongchon&o=qwfdeSOL8Vz4lXEEP4TD&driver=driver123&driverName=김기사
```

## QR 코드 생성 방법

Play Store 배포 후 각 사무실/기사별로 QR 코드를 생성하여 배포해야 합니다.

### QR 코드 URL 형식
```
https://play.google.com/store/apps/details?id=com.designated.customer&referrer=<URL인코딩된_파라미터>
```

### 자동 생성 스크립트 (예시)
```javascript
// Cloud Functions 또는 별도 스크립트
function generateQRCodeURL(officeData) {
  const params = new URLSearchParams({
    r: officeData.regionId,
    o: officeData.officeId,
    phone: officeData.phone,
    bank: officeData.bank,
    account: officeData.account,
    holder: officeData.holder
  });

  const baseURL = 'https://play.google.com/store/apps/details?id=com.designated.customer';
  return `${baseURL}&referrer=${encodeURIComponent(params.toString())}`;
}
```

## 검증 체크리스트

### ✅ 구현 완료 항목
- [x] Install Referrer 라이브러리 추가
- [x] checkInstallReferrer() 메서드 구현
- [x] parseAndSaveReferrer() 메서드 구현
- [x] SharedPreferences 저장 로직 연동
- [x] 기존 토큰 매칭 fallback 유지
- [x] 불필요한 핑거프린트 매칭 코드 제거

### ⏳ Play Store 배포 후 테스트 필요
- [ ] Play Store 내부 테스트 트랙 생성
- [ ] referrer 파라미터가 포함된 테스트 링크로 설치
- [ ] Install Referrer 데이터 정상 수신 확인
- [ ] SharedPreferences에 정보 저장 확인
- [ ] 앱 재실행 시 저장된 정보 유지 확인
- [ ] 2000개 사무실 QR 코드 생성 및 배포

## 주의사항

1. **Play Store 필수**: Install Referrer API는 Play Store 설치에서만 작동
2. **URL 인코딩**: 한글 및 특수문자는 반드시 URL 인코딩 필요
3. **일회성 데이터**: Install Referrer 데이터는 최초 설치 시 한 번만 전달됨
4. **앱 재설치**: 앱을 삭제하고 재설치하면 새로운 referrer 데이터 수신 가능
5. **Fallback 유지**: APK 직접 설치를 위해 토큰 기반 매칭은 유지됨

## 트러블슈팅

### 문제: "Play Store 서비스를 사용할 수 없음"
- **원인**: APK를 직접 설치했거나 Play Store가 없는 기기
- **해결**: Play Store를 통해 설치하거나 토큰 매칭 사용

### 문제: "Referrer URL이 비어있음"
- **원인**: referrer 파라미터 없이 설치됨
- **해결**: referrer 파라미터가 포함된 링크로 재설치

### 문제: 한글이 깨져서 저장됨
- **원인**: URL 인코딩 누락
- **해결**: 모든 파라미터를 URLEncoder로 인코딩

## 다음 단계

1. **Play Console 등록**: Google Play Console에 앱 등록
2. **내부 테스트**: 내부 테스트 트랙으로 Install Referrer 검증
3. **QR 생성**: 모든 사무실/기사별 QR 코드 생성
4. **정식 배포**: 검증 완료 후 프로덕션 배포
5. **모니터링**: Firebase Analytics로 attribution 성공률 추적

## 관련 문서

- [Play Install Referrer API 공식 문서](https://developer.android.com/google/play/installreferrer)
- iOS Attribution 구현 계획서: `작업내역/iOS_Attribution_구현계획서.md`

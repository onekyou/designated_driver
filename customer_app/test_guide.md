# 📋 단계별 테스트 가이드

## 🌐 Phase 1: 랜딩 페이지 테스트

### 환경 설정
```bash
cd customer_app/landing
npm install
```

### 테스트 시나리오
1. **기본 랜딩 페이지**
   ```bash
   npm run dev
   # 브라우저에서 http://localhost:3000/seoul01 접속
   ```

   **확인사항:**
   - [ ] 페이지 로딩 (2초 이내)
   - [ ] "서울 대리운전" 표시
   - [ ] 핑거프린팅 데이터 수집
   - [ ] Firebase에 pre_attributions 저장

2. **다른 사무실 코드**
   ```bash
   # 브라우저에서 다음 URL들 테스트
   http://localhost:3000/busan01  # "부산 대리운전"
   http://localhost:3000/daegu01  # "대구 대리운전"
   http://localhost:3000/unknown  # "대리운전" (기본값)
   ```

3. **핑거프린팅 검증**
   - F12 → Console 확인
   - Visitor ID 생성 확인
   - Device 정보 수집 확인

## 🔥 Phase 2: Firebase Functions 테스트

### 환경 설정
```bash
cd customer_app/functions
npm install -g firebase-tools
firebase login
firebase init functions
npm install
```

### 로컬 테스트
```bash
firebase emulators:start --only functions
```

### 테스트 시나리오
1. **어트리뷰션 매칭 테스트**
   ```javascript
   // 테스트 데이터
   const testData = {
     fingerprint: {
       visitorId: "test_visitor_id",
       userAgent: "Mozilla/5.0 (Android 12; Mobile)",
       screenResolution: "1080x2400",
       timezone: "Asia/Seoul",
       language: "ko",
       timestamp: Date.now()
     },
     phoneNumber: "01012345678",
     deviceInfo: {
       androidId: "test_android_id",
       deviceModel: "SM-G991N",
       osVersion: "12"
     }
   }

   // Functions 호출
   fetch('http://localhost:5001/your-project/us-central1/matchAttribution', {
     method: 'POST',
     headers: { 'Content-Type': 'application/json' },
     body: JSON.stringify({ data: testData })
   })
   ```

2. **점수별 테스트**
   - **90점 매칭**: 동일한 visitorId + 같은 시간
   - **70점 매칭**: 유사한 디바이스 + 30분 차이
   - **50점 매칭**: 다른 디바이스 + 같은 IP
   - **30점 매칭**: 완전히 다른 환경

## 📱 Phase 3: Android 앱 테스트

### 프로젝트 생성
1. Android Studio에서 프로젝트 생성
2. `android_setup.md` 가이드 따라하기
3. Firebase 연동 설정

### 테스트 데이터 구조
```kotlin
// 테스트용 데이터 클래스
data class TestScenario(
    val name: String,
    val fingerprint: DeviceFingerprint,
    val expectedScore: Int,
    val expectedResult: AttributionResult
)

val testScenarios = listOf(
    TestScenario(
        name = "Perfect Match",
        fingerprint = perfectMatchFingerprint,
        expectedScore = 90,
        expectedResult = AttributionResult.SUCCESS
    ),
    TestScenario(
        name = "Good Match",
        fingerprint = goodMatchFingerprint,
        expectedScore = 75,
        expectedResult = AttributionResult.SUCCESS
    ),
    TestScenario(
        name = "Weak Match",
        fingerprint = weakMatchFingerprint,
        expectedScore = 45,
        expectedResult = AttributionResult.MANUAL_REVIEW
    )
)
```

### 유닛 테스트
```kotlin
@Test
fun `어트리뷰션 점수 계산 테스트`() {
    val current = DeviceFingerprint(
        visitorId = "test_id",
        userAgent = "Mozilla/5.0 Android",
        screenResolution = "1080x2400"
    )

    val stored = PreAttribution(
        visitorId = "test_id",
        userAgent = "Mozilla/5.0 Android",
        screenResolution = "1080x2400",
        timestamp = System.currentTimeMillis() - 1000 // 1초 전
    )

    val score = calculateAttributionScore(current, stored)
    assertThat(score).isGreaterThan(70)
}
```

## 🎯 Phase 4: 통합 테스트

### End-to-End 시나리오
```bash
# 1. 랜딩 페이지 시작
cd customer_app/landing && npm run dev &

# 2. Functions 시작
cd customer_app/functions && firebase emulators:start &

# 3. Android 앱 실행
# Android Studio에서 앱 실행
```

### 시나리오 1: 완벽한 어트리뷰션
1. **Step 1**: 랜딩 페이지 접속
   ```
   http://localhost:3000/seoul01
   ```

2. **Step 2**: 핑거프린팅 수집 확인
   ```javascript
   // 브라우저 Console에서
   console.log('Visitor ID:', window.fingerprintResult)
   ```

3. **Step 3**: Android 앱에서 매칭
   ```kotlin
   val result = attributionService.matchAttribution(
       fingerprint = collectFingerprint(),
       phoneNumber = "01012345678"
   )
   // result.score >= 70 이면 성공
   ```

### 시나리오 2: 수동 확인 필요
1. 다른 디바이스에서 랜딩 페이지 접속
2. 30분 후 앱에서 어트리뷰션 시도
3. 50-69점 → 수동 확인 UI 표시

### 시나리오 3: 매칭 실패
1. 완전히 다른 환경에서 어트리뷰션 시도
2. 50점 미만 → 사무실 선택 UI 표시

## 📊 성능 테스트

### 부하 테스트
```javascript
// 동시 접속 테스트
const promises = []
for (let i = 0; i < 100; i++) {
  promises.push(
    fetch(`http://localhost:3000/seoul01?test=${i}`)
  )
}
await Promise.all(promises)
```

### 응답 시간 측정
```javascript
const start = performance.now()
const result = await attributionService.matchAttribution(data)
const end = performance.now()
console.log(`Attribution time: ${end - start}ms`)
// 목표: 1초 이내
```

## 🔍 디버깅 도구

### Firebase Console
- pre_attributions 컬렉션 확인
- attributions 컬렉션 확인
- Functions 로그 모니터링

### Chrome DevTools
- Application → IndexedDB (Firebase 캐시)
- Network → Functions 호출 확인
- Console → 핑거프린팅 로그

### Android Studio
- Logcat → 어트리뷰션 로그
- Database Inspector → Room DB 확인
- Network Inspector → API 호출

## ✅ 테스트 체크리스트

### Phase 1: 랜딩 페이지
- [ ] 페이지 로딩 성공
- [ ] 사무실별 정보 표시
- [ ] 핑거프린팅 수집
- [ ] Firebase 저장 확인

### Phase 2: Functions
- [ ] 로컬 에뮬레이터 실행
- [ ] 90점 매칭 성공
- [ ] 70점 매칭 성공
- [ ] 50점 매칭 (수동 확인)
- [ ] 30점 매칭 (실패)

### Phase 3: Android
- [ ] 프로젝트 빌드 성공
- [ ] Firebase 연동
- [ ] 핑거프린팅 수집
- [ ] 어트리뷰션 API 호출
- [ ] UI 상태 변화

### Phase 4: 통합
- [ ] E2E 시나리오 성공
- [ ] 90% 이상 매칭률
- [ ] 1초 이내 응답
- [ ] 에러 처리 확인

## 🚨 알려진 이슈

### Issue 1: CORS 에러
```javascript
// 해결방법: next.config.js
module.exports = {
  async headers() {
    return [{
      source: '/api/:path*',
      headers: [
        { key: 'Access-Control-Allow-Origin', value: '*' }
      ]
    }]
  }
}
```

### Issue 2: Firebase 권한
```javascript
// 해결방법: firebase.rules
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /pre_attributions/{document} {
      allow read, write: if true; // 테스트용
    }
  }
}
```

## 📈 성공 기준

- **어트리뷰션 정확도**: 90% 이상
- **응답 시간**: 1초 이내
- **에러율**: 5% 이하
- **매칭률**: 85% 이상 (70점 이상)
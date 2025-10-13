# 📋 단계별 테스트 가이드

## 🎯 현재 상태 체크

### Step 1: 프로젝트 구조 확인
```bash
cd C:\app_dev\designated_driver\customer_app
ls -la
```

**확인사항:**
- [ ] `README.md` 파일 존재
- [ ] `landing/` 폴더 존재
- [ ] `functions/` 폴더 존재
- [ ] `project_setup.md` 파일 존재

---

## 🌐 Step 2: 랜딩 페이지 테스트

### 2-1. 의존성 설치 테스트
```bash
cd customer_app/landing
npm install
```

**성공 기준:**
- [ ] 오류 없이 설치 완료
- [ ] `node_modules/` 폴더 생성
- [ ] `package-lock.json` 생성

**문제 발생 시:**
```bash
# Node.js 버전 확인 (16.0.0 이상 필요)
node --version
npm --version

# 캐시 정리 후 재시도
npm cache clean --force
rm -rf node_modules package-lock.json
npm install
```

### 2-2. 개발 서버 실행 테스트
```bash
npm run dev
```

**성공 기준:**
- [ ] `Ready - started server on 0.0.0.0:3000` 메시지
- [ ] 브라우저에서 `http://localhost:3000/seoul01` 접속 가능
- [ ] "서울 대리운전" 텍스트 표시
- [ ] 에러 없음

**문제 발생 시:**
```bash
# 포트 충돌 해결
npx kill-port 3000
npm run dev

# 또는 다른 포트 사용
npm run dev -- -p 3001
```

### 2-3. 핑거프린팅 테스트
브라우저에서 F12 → Console 확인

**성공 기준:**
- [ ] `Device ID: xxx` 메시지 출력 (푸터에서)
- [ ] Console에 에러 없음
- [ ] `수집 중...`에서 실제 ID로 변경됨

**문제 발생 시:**
```javascript
// Console에서 수동 테스트
navigator.userAgent
window.screen.width + 'x' + window.screen.height
Intl.DateTimeFormat().resolvedOptions().timeZone
```

---

## 🔥 Step 3: Firebase Functions 테스트

### 3-1. Firebase CLI 설치 확인
```bash
firebase --version
```

**성공 기준:**
- [ ] 버전 정보 출력 (12.0.0 이상)

**설치 필요 시:**
```bash
npm install -g firebase-tools
firebase login
```

### 3-2. Functions 로컬 실행 테스트
```bash
cd customer_app/functions
npm install
firebase emulators:start --only functions
```

**성공 기준:**
- [ ] `functions: Emulator started at http://localhost:5001` 메시지
- [ ] 에러 없음

**문제 발생 시:**
```bash
# Java 설치 확인 (Firebase 에뮬레이터 필요)
java --version

# 포트 변경
firebase emulators:start --only functions --port=5002
```

### 3-3. Functions 수동 테스트
새 터미널에서:
```bash
curl -X POST http://localhost:5001/YOUR_PROJECT_ID/us-central1/matchAttribution \
  -H "Content-Type: application/json" \
  -d '{"data": {"test": "hello"}}'
```

**성공 기준:**
- [ ] JSON 응답 받음
- [ ] 5xx 에러 없음

---

## 📱 Step 4: Android 프로젝트 테스트

### 4-1. Android Studio 프로젝트 생성
1. Android Studio 실행
2. `project_setup.md` 가이드 따라 프로젝트 생성

**성공 기준:**
- [ ] 프로젝트 생성 완료
- [ ] 빌드 에러 없음
- [ ] `app/build.gradle.kts` 설정 완료

### 4-2. Firebase 연동 테스트
```kotlin
// MainActivity.kt에 임시 테스트 코드
import com.google.firebase.Firebase
import com.google.firebase.initialize

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Firebase.initialize(this)
        Log.d("Firebase", "Firebase 초기화 완료")
    }
}
```

**성공 기준:**
- [ ] 앱 실행 성공
- [ ] Logcat에 "Firebase 초기화 완료" 출력
- [ ] 크래시 없음

---

## 🔍 Step 5: 통합 테스트

### 5-1. 랜딩 페이지 → Firebase 연동
1. 랜딩 페이지 실행 중인 상태
2. Firebase Console → Firestore 데이터베이스 확인

**성공 기준:**
- [ ] `pre_attributions` 컬렉션 생성됨
- [ ] 새 문서가 추가됨 (랜딩 페이지 접속 시)
- [ ] 문서에 올바른 데이터 포함

### 5-2. 데이터 검증
Firebase Console에서 문서 내용 확인:
```json
{
  "visitorId": "xxx",
  "userAgent": "Mozilla/5.0...",
  "screenResolution": "1920x1080",
  "timezone": "Asia/Seoul",
  "language": "ko",
  "officeCode": "seoul01",
  "createdAt": "timestamp"
}
```

**성공 기준:**
- [ ] 모든 필드 존재
- [ ] 올바른 데이터 타입
- [ ] officeCode가 URL과 일치

---

## 🚨 문제 해결 체크리스트

### Firebase 연동 문제
```bash
# 1. Firebase 프로젝트 확인
firebase projects:list

# 2. 권한 확인
firebase auth:export test.json

# 3. 프로젝트 선택
firebase use YOUR_PROJECT_ID
```

### 랜딩 페이지 빌드 문제
```bash
# 1. Node.js 버전 확인
node --version  # 16+ 필요

# 2. 의존성 재설치
rm -rf node_modules package-lock.json
npm install

# 3. TypeScript 문제 해결
npm install --save-dev typescript@latest
```

### Android 빌드 문제
```kotlin
// build.gradle.kts 확인
android {
    compileSdk 34

    defaultConfig {
        minSdk 24
        targetSdk 34
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}
```

---

## ✅ 다음 단계로 진행 조건

모든 항목이 ✅ 상태여야 다음 단계 진행:

### 필수 조건
- [ ] 랜딩 페이지 정상 실행
- [ ] Firebase Functions 로컬 실행
- [ ] Android 프로젝트 빌드 성공
- [ ] Firebase 연동 확인
- [ ] 핑거프린팅 데이터 수집 확인

### 선택 조건
- [ ] 다른 사무실 코드 테스트 (`busan01`, `daegu01`)
- [ ] 여러 브라우저에서 테스트
- [ ] 모바일 브라우저 테스트

---

## 📞 테스트 완료 후

모든 테스트가 성공하면:
1. ✅ 전화번호 인증 구현으로 진행
2. ✅ 메인 화면 UI 개발
3. ✅ 포인트 시스템 구현

**현재 상태에서 문제가 있다면 다음 단계로 진행하지 말고 먼저 해결하세요!**
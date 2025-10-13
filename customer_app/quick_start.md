# 🚀 빠른 시작 가이드

## 1️⃣ 랜딩 페이지 실행 (1분)

```bash
cd customer_app/landing
npm install
npm run dev
```

브라우저에서 `http://localhost:3000/seoul01` 접속

**확인사항:**
- ✅ "서울 대리운전" 표시
- ✅ 앱 다운로드 버튼
- ✅ Console에 Visitor ID 출력

## 2️⃣ Firebase 설정 (5분)

### Firebase 프로젝트 생성
1. https://console.firebase.google.com 접속
2. "프로젝트 추가" 클릭
3. 프로젝트 이름: `designated-customer-app`
4. Google 애널리틱스 비활성화

### Firebase 설정 파일
1. 프로젝트 설정 → 웹 앱 추가
2. `firebaseConfig` 복사
3. `landing/app/[officeCode]/page.tsx` 파일에서 설정 교체

### Firestore 데이터베이스
1. Firestore Database → 데이터베이스 만들기
2. 테스트 모드로 시작
3. 위치: asia-northeast3 (서울)

## 3️⃣ Functions 배포 (3분)

```bash
cd customer_app/functions
npm install -g firebase-tools
firebase login
firebase init functions
npm install
firebase deploy --only functions
```

## 4️⃣ Android 프로젝트 (10분)

### Android Studio에서:
1. Empty Activity 프로젝트 생성
2. 패키지명: `com.designated.customer`
3. Firebase → Android 앱 추가
4. `google-services.json` 다운로드 → `app/` 폴더에 복사

### 의존성 추가 (`app/build.gradle.kts`):
```kotlin
dependencies {
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
}
```

## 🧪 첫 번째 테스트

### 1. 랜딩 페이지 테스트
- 브라우저 F12 → Console 확인
- `Visitor ID: xxx` 출력 확인

### 2. Firebase 연동 확인
- Firebase Console → Firestore → `pre_attributions` 컬렉션 확인
- 새 문서 생성 확인

### 3. Android 앱 연동
```kotlin
// MainActivity.kt에 임시 코드 추가
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase Functions 테스트
        val functions = Firebase.functions
        val data = hashMapOf("test" to "hello")

        functions.getHttpsCallable("matchAttribution")
            .call(data)
            .addOnSuccessListener { result ->
                Log.d("Firebase", "Success: $result")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error", e)
            }
    }
}
```

## 📋 체크리스트

### 환경 설정
- [ ] Node.js 설치됨
- [ ] Android Studio 설치됨
- [ ] Firebase CLI 설치됨
- [ ] Firebase 프로젝트 생성됨

### 랜딩 페이지
- [ ] `npm run dev` 실행 성공
- [ ] 브라우저에서 페이지 로딩
- [ ] Visitor ID 생성 확인
- [ ] Firebase에 데이터 저장 확인

### Android 앱
- [ ] 프로젝트 생성 성공
- [ ] Firebase 연동 완료
- [ ] 빌드 성공
- [ ] 에뮬레이터/실기기 실행

### Functions
- [ ] `firebase deploy` 성공
- [ ] Firebase Console에서 함수 확인
- [ ] 로그 확인 (Functions → 로그)

## 🚨 자주 발생하는 문제

### 문제 1: npm install 실패
```bash
# 해결방법
npm cache clean --force
npm install
```

### 문제 2: Firebase 권한 오류
```bash
# 해결방법
firebase login --reauth
```

### 문제 3: Android 빌드 실패
```kotlin
// app/build.gradle.kts 하단에 추가
android {
    compileSdk 34

    defaultConfig {
        multiDexEnabled true
    }
}
```

### 문제 4: CORS 에러
```typescript
// next.config.js 생성
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

## 📞 다음 단계

1. **전화번호 인증 구현**
2. **메인 화면 UI 개발**
3. **어트리뷰션 매칭 테스트**
4. **포인트 시스템 구현**

## 📚 참고 자료

- [Firebase 문서](https://firebase.google.com/docs)
- [Next.js 문서](https://nextjs.org/docs)
- [Android Compose 문서](https://developer.android.com/jetpack/compose)
- [FingerprintJS 문서](https://fingerprint.com/docs)
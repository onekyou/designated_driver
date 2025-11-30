# 총관리자 웹 시스템 설정 가이드

## Phase 1 완료 체크리스트

### ✅ 완료된 항목
- [x] Next.js 15 프로젝트 생성
- [x] TypeScript 설정
- [x] Tailwind CSS 설정
- [x] Firebase SDK 설치
- [x] 로그인 페이지 구현
- [x] 대시보드 페이지 구현
- [x] Firebase 환경 변수 설정
- [x] 개발 서버 실행 확인

### 📋 다음 단계 작업

## Firebase 콘솔 설정

### 1. Firebase 웹 앱 등록 (권장)

현재는 임시 App ID를 사용하고 있습니다. 정식 웹 앱을 등록하려면:

1. **Firebase 콘솔 접속**
   - https://console.firebase.google.com
   - 프로젝트 `calldetector-5d61e` 선택

2. **웹 앱 추가**
   - 프로젝트 설정 → 일반 탭
   - 하단의 "앱 추가" → 웹 아이콘 선택
   - 앱 닉네임: `총관리자 웹`
   - Firebase Hosting 설정: 체크 (선택사항)

3. **설정 정보 복사**
   ```javascript
   const firebaseConfig = {
     apiKey: "...",
     authDomain: "...",
     projectId: "...",
     storageBucket: "...",
     messagingSenderId: "...",
     appId: "..."  // 이 값을 .env.local에 업데이트
   };
   ```

4. **.env.local 업데이트**
   ```bash
   NEXT_PUBLIC_FIREBASE_APP_ID=<새로운_앱_ID>
   ```

### 2. 총관리자 계정 생성

#### 방법 1: Firebase 콘솔 사용 (권장)

1. **Firebase Authentication 설정**
   - Firebase 콘솔 → Authentication → 시작하기
   - 로그인 방법 탭 → 이메일/비밀번호 → 사용 설정

2. **총관리자 계정 생성**
   - Users 탭 → "사용자 추가"
   - 이메일: `admin@designated-driver.com` (또는 원하는 이메일)
   - 비밀번호: 강력한 비밀번호 입력
   - "사용자 추가" 클릭

3. **Firestore에 관리자 정보 저장**
   - Firestore Database → `admin_users` 컬렉션 생성
   - 문서 ID: Firebase Auth의 UID
   - 필드:
     ```json
     {
       "email": "admin@designated-driver.com",
       "role": "super_admin",
       "name": "총관리자",
       "createdAt": "현재 시간",
       "isActive": true
     }
     ```

#### 방법 2: Firebase CLI 사용

```bash
# Firebase CLI 설치 (아직 설치 안 했다면)
npm install -g firebase-tools

# Firebase 로그인
firebase login

# 프로젝트 디렉토리에서
cd C:\app_dev\designated_driver\head_manager_web

# Firebase 프로젝트 선택
firebase use calldetector-5d61e

# Authentication 설정 확인
firebase auth:export users.json
```

### 3. Firestore 보안 규칙 설정

총관리자만 접근할 수 있도록 보안 규칙을 설정해야 합니다.

**firestore.rules** 파일에 추가:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    // 총관리자 권한 확인 함수
    function isSuperAdmin() {
      return exists(/databases/$(database)/documents/admin_users/$(request.auth.uid))
        && get(/databases/$(database)/documents/admin_users/$(request.auth.uid)).data.role == 'super_admin'
        && get(/databases/$(database)/documents/admin_users/$(request.auth.uid)).data.isActive == true;
    }

    // 총관리자 전용 컬렉션
    match /admin_users/{userId} {
      allow read, write: if isSuperAdmin();
    }

    // 사무실 정보 (읽기만 총관리자)
    match /offices/{officeId} {
      allow read: if isSuperAdmin();
      allow write: if isSuperAdmin();
    }

    // 락인 점수 (총관리자만 읽기)
    match /lock_in_scores/{scoreId} {
      allow read: if isSuperAdmin();
      allow write: if false; // 자동 계산만 가능
    }

    // 환전 요청 (총관리자가 승인/거부)
    match /withdrawal_requests/{requestId} {
      allow read, update: if isSuperAdmin();
      allow create: if false; // 사무실에서만 생성
    }
  }
}
```

## 테스트 체크리스트

### Phase 1 테스트

#### 1. 개발 서버 실행 확인
- [ ] `npm run dev` 실행
- [ ] http://localhost:3000 접속
- [ ] 로그인 페이지로 자동 리다이렉트 확인

#### 2. 로그인 테스트
- [ ] 올바른 이메일/비밀번호로 로그인 시도
- [ ] 대시보드로 리다이렉트 확인
- [ ] 잘못된 비밀번호로 로그인 시도
- [ ] 에러 메시지 표시 확인

#### 3. 대시보드 테스트
- [ ] 사용자 이메일 표시 확인
- [ ] 로그아웃 버튼 동작 확인
- [ ] 로그아웃 후 로그인 페이지로 이동 확인

#### 4. 인증 상태 관리
- [ ] 로그인 상태에서 /login 접속 시 대시보드로 리다이렉트
- [ ] 비로그인 상태에서 /dashboard 접속 시 로그인 페이지로 리다이렉트

## 문제 해결

### 개발 서버 실행 오류

**문제**: `npm run dev` 실행 시 오류 발생
**해결**:
```bash
# node_modules 삭제 후 재설치
rm -rf node_modules package-lock.json
npm install
```

### Firebase 연결 오류

**문제**: Firebase 초기화 실패
**해결**:
1. `.env.local` 파일의 모든 환경 변수 확인
2. Firebase 콘솔에서 API 키 활성화 확인
3. 개발 서버 재시작

### 로그인 실패

**문제**: 올바른 계정으로 로그인 안 됨
**해결**:
1. Firebase Authentication에서 이메일/비밀번호 로그인 활성화 확인
2. Firebase 콘솔 → Authentication → Users에서 계정 존재 확인
3. 브라우저 콘솔에서 오류 메시지 확인

## 다음 단계: Phase 2

Phase 1이 완료되면 다음 기능을 구현합니다:

1. **사무실 관리**
   - 사무실 목록 조회
   - 사무실 상세 정보
   - 사무실 등록/수정/삭제

2. **실시간 모니터링**
   - 사무실별 콜 현황
   - 기사 활동 현황
   - 실시간 통계

3. **권한 관리**
   - 관리자 계정 관리
   - 역할 기반 접근 제어

4. **UI/UX 개선**
   - shadcn/ui 컴포넌트 추가
   - 반응형 레이아웃
   - 다크모드 지원

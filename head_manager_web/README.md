# 총관리자 웹 시스템

대리운전 통합 플랫폼의 총관리자용 웹 애플리케이션입니다.

## 기술 스택

- **Framework**: Next.js 15
- **Language**: TypeScript
- **Styling**: Tailwind CSS
- **Backend**: Firebase (Authentication, Firestore)
- **Deployment**: Firebase Hosting

## 시작하기

### 1. 환경 변수 설정

`.env.local.example` 파일을 `.env.local`로 복사하고 Firebase 설정값을 입력하세요:

```bash
cp .env.local.example .env.local
```

### 2. 의존성 설치

```bash
npm install
```

### 3. 개발 서버 실행

```bash
npm run dev
```

브라우저에서 [http://localhost:3000](http://localhost:3000)을 열어 확인하세요.

### 4. 프로덕션 빌드

```bash
npm run build
npm start
```

## 주요 기능

- **사무실 관리**: 전국 사무실 등록, 모니터링, 관리
- **락인 시스템**: 사무실별 락인 점수 계산 및 관리
- **환전 시스템**: 공유콜 포인트 환전 요청 승인/거부
- **구독 관리**: 사무실별 구독 상태 및 결제 관리

## 프로젝트 구조

```
head_manager_web/
├── app/                    # Next.js App Router
│   ├── login/             # 로그인 페이지
│   ├── dashboard/         # 대시보드
│   ├── layout.tsx         # 루트 레이아웃
│   └── globals.css        # 글로벌 스타일
├── lib/                   # 유틸리티 및 설정
│   ├── firebase.ts        # Firebase 설정
│   └── hooks/             # 커스텀 훅
├── components/            # 재사용 가능한 컴포넌트
└── public/               # 정적 파일
```

## 배포

Firebase Hosting을 사용하여 배포합니다:

```bash
npm run build
firebase deploy --only hosting
```

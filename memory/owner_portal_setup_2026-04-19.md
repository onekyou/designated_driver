---
name: 사장님 포털 운영 인프라 구축 2026-04-19
description: head-manager-web 사장님 대시보드 APK 배포 + Google 로그인 체계 전환. 전화번호+비번 폐기하고 Gmail OAuth 단일 진입점 확립
type: project
---

# 사장님 포털 운영 인프라 구축 — 2026-04-19

## 배경
사장님 가입 후 APK(call_detector, call_manager) 배포와 재로그인 시 비번 관리 부담을 제거하기 위해 전체 흐름을 Gmail 중심으로 재설계. 아직 실제 사장님 가입자 0명 상태라 마이그레이션 걱정 없이 전면 전환.

## 최종 운영 흐름

```
[사장님]                     [총관리자]
  │                              │
  ├── homepage/signup.html ──────┤ (Gmail 필수 입력)
  │   office_applications/{id}    │
  │   pending                     │
  │                              │
  │   ┌── head-manager-web ──────┤ "신청 관리" 탭 승인 클릭
  │   │   CF approveOfficeApplication
  │   │     - createUser(email=gmail, emailVerified:true, 비번없음)
  │   │     - admins/{uid} 생성 (role=OFFICE_OWNER, apkDownloadEnabled:true)
  │   │     - office 문서 생성
  │   │                          │
  └── /owner/login ──────────────┤ "Google로 계속하기" 버튼 1개
      (signInWithPopup)            │
      admins/{uid} role 체크 →     │
      /owner/dashboard             │
                                   │
      APK 다운로드 버튼             │
      ↓                            │
      CF getApkDownloadUrl         │
      Storage signed URL 반환       │ (signBlob IAM 미부여 상태에서는 500 에러)
```

## 구현된 변경 (커밋 단위)

### 1. Firebase Storage 신규 활성화
- 리전: `asia-northeast3` (서울, Firestore/CF와 동일)
- 무료 tier는 미국 3개 리전만 5GB 제공 → 서울은 실비 과금이지만 한 달 20원 수준이라 성능을 우선
- 이전에 버킷 자체가 없어 업로드 시 CORS 에러 발생 (preflight 404)

### 2. storage.rules 신설 + firebase.json 타겟 추가
- `apks/{appName}/{fileName}` 경로:
  - read: HEAD_MANAGER + OFFICE_OWNER (크로스서비스 firestore 조회)
  - write: HEAD_MANAGER only, 200MB 제한, `.apk` 확장자 강제
- 크로스서비스 rules는 정상 배포되나 클라이언트 업로드 시 `storage/unauthorized` 발생 → **현재 UI 업로드는 막혀 있음**
- 대안: Admin SDK/서버측 업로드로 우회 가능

### 3. OAuth 기반 APK 업로드 유틸 (`functions/scripts/upload-apk-release.js`)
- firestore-util.js와 동일한 Firebase CLI refresh token 방식
- Storage + Firestore apk_releases 문서를 원샷으로 처리
- 기존 isLatest=true를 자동 false로 플립
- 2026-04-19 call_manager v1.0.0 (29.5MB), call_detector v1.0.0 (34.2MB) 업로드 완료

### 4. Custom Claims 스크립트 (`functions/scripts/set-custom-claims.js`)
- 크로스서비스 rules 문제 발생 시 firestore 조회 대신 `request.auth.token.role`로 Storage rules 대체할 수 있도록 준비
- Admin SDK + ADC 기반 (사용자가 필요 시 실행)

### 5. head-manager-web Sidebar에 "사장님 대시보드" 링크 추가
- 월간 리포트 메뉴 바로 아래 `/owner/dashboard` 내부 링크
- 총관리자가 사장님 화면을 수시로 확인 가능

### 6. CF `approveOfficeApplication` 수정
- email: `${phone}@callmadang.internal` → `appData.gmail`
- password 필드 완전 제거, emailVerified: true 추가 (Google 로그인 자동 연결)
- 반환값에서 `tempPassword` 제거
- `admins/{uid}.email`도 실제 Gmail로 저장 (phoneNumber는 연락처용 유지)

### 7. /owner/login 페이지 재작성
- 전화번호+비번 폼 완전 삭제
- `signInWithPopup(GoogleAuthProvider)` 버튼 1개
- `prompt: 'select_account'`로 다중 Gmail 기기에서 계정 선택 가능
- admins/{uid} 존재 + role===OFFICE_OWNER 체크 실패 시 signOut + 에러 메시지

### 8. /owner/dashboard UI 개선
- "바로 다운로드" → "다운로드" (문구 간소화)
- 다운로드 / Play Store 버튼 **세로 배열** (min-w-[140px] 통일, justify-center)
- 콜 매니저 설명 2줄 처리 (`whitespace-pre-line` + `\n`)

### 9. 기존 테스트 사장님 계정 정리
- `admins/sfHTmaxaCWTm4oleRKbmMQbtAPF2` (스마일, 01022223333) 삭제
- Firebase Auth 계정도 REST API로 삭제
- 현재 admins 2개: onekyou71@gmail.com (HEAD_MANAGER), vip@naver.com (role 미확인)

## Firebase Console 설정 (사용자 수동 완료)
- Authentication → Sign-in method → **Google provider 활성화**
- 프로젝트 공개용 이름: "콜마당"
- 지원 이메일: onekyou71@gmail.com
- Authentication → Settings → Authorized domains에 `head-manager-web.web.app` 추가
- 동일 이메일 계정 연결 활성화 (기본값 유지)

## 남은 과제 (별도 세션)

### A. getApkDownloadUrl의 signBlob IAM 권한 (차단 이슈)
- 에러: `Permission 'iam.serviceAccounts.signBlob' denied`
- CF 서비스 계정: `60275310305-compute@developer.gserviceaccount.com`
- 해결: Google Cloud Console → IAM → 해당 SA에 **"서비스 계정 토큰 생성자"** 역할 self-binding
- gcloud CLI가 Python 이슈로 막혀 REST API 스크립트 또는 콘솔 수동 부여 필요

### B. Play Store 내부 테스터 자동 등록
- 현재 수동. 사장님 수 적을 때는 수동 유지
- 규모 확장 시 Google Group 매개 자동화 (Directory API)

### C. 사장님 비번 변경 UI (Google 로그인 전환으로 불필요)
- Google OAuth 전용이라 비번 자체가 없음 → 비번 변경 UI도 불필요
- 계정 자체 문제는 Google 비번 재설정으로 해결 (Google 책임)

## 참고 파일
- 아이디 체계: admins 컬렉션은 email(Gmail), phoneNumber(연락처), role, associated* 필드 구조
- 사장님 로그인 식별자 = Gmail
- 총관리자 계정은 그대로 (`/login` 경로로 이메일+비번 로그인 유지)

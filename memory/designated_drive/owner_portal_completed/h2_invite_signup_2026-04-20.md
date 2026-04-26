---
name: H2 사장님 가입/로그인 체계 (Invite-Token + 앱 가입) 2026-04-20
description: 사장님 포털 인증 이원화 해소 — 총관리자가 downloadInvites 토큰 발급 → 사장님이 /owner/download 에서 APK 받아 call_manager 에서 직접 이메일+비밀번호 가입. 이후 웹/앱 단일 계정으로 로그인
type: project
---

# H2 사장님 가입/로그인 체계 — 2026-04-20

## 배경 (Why)

### 기존 설계 (2026-04-19 G2)
- 총관리자 승인 시 `approveOfficeApplication` CF 가 Gmail 기반 Firebase Auth 계정 즉시 생성 (`createUser({email, emailVerified:true})` — 비밀번호 없음)
- 웹 `/owner/login` 는 Google OAuth 전용
- Android 앱 (call_detector/call_manager) 은 `signInWithEmailAndPassword` — **비밀번호가 없는 계정에는 로그인 불가**

### 결과적 문제
- 첫 실사용자 박상준님(`a01077420507@gmail.com`, UID `hpKCSyX7...`) 이 웹엔 Google 로 진입 가능했으나 call_manager / call_detector 앱 로그인 불가
- 신규 승인되는 모든 사장님 동일 블로커 발생 예정
- 웹/앱 인증 이원화 구조적 문제

## 해결 (What)

H2 = "초대 토큰으로 APK 다운로드" + "사장님이 앱에서 직접 이메일+비밀번호 가입"

### 최종 흐름
```
[총관리자] /applications/detail → 승인
  ↓ CF approveOfficeApplication (재설계)
  ├─ offices/{officeId} 미리 생성 (ownerAuthUid=null)
  ├─ downloadInvites/{token} 생성 (active, 7일, 1회 사용)
  ├─ office_applications 상태 approved
  └─ 응답 inviteUrl, inviteToken 반환
[총관리자] URL 복사 → 카톡/SMS → 사장님에게 전달

[사장님] /owner/download?t=xxx (인증 불필요)
  ↓ redeemDownloadToken CF
  ├─ 토큰 active + 미만료 + 사용가능 검증
  ├─ apk_releases isLatest=true 2개 signed URL 반환 (1시간)
  └─ UI: call_manager / call_detector APK + 초대 토큰 복사 버튼

[사장님 call_manager 가입]
  회원가입 화면: email / 비번 / 초대 토큰
  ↓ registerOwner CF (unauthenticated)
  ├─ 토큰 재검증
  ├─ admin.auth().createUser({email, password})
  ├─ Firestore 트랜잭션:
  │    - admins/{uid} 생성 (role=OFFICE_OWNER, associated*, apkDownloadEnabled=true)
  │    - offices/{officeId}.ownerAuthUid = uid
  │    - office_applications/{id}.ownerAuthUid = uid
  │    - downloadInvites/{token} → status=used, usesRemaining=0
  └─ 실패 시 admin.auth().deleteUser(uid) 롤백
  ↓ signInWithEmailAndPassword 자동 로그인
  → Dashboard

[call_detector, 웹 재진입]
  동일 email+비번 로그인 — 코드 수정 0
```

## 구현 파일 목록

### 수정
- `functions/src/index.ts`
  - `approveOfficeApplication` 재설계 (L5517~ 교체) — Auth 생성 제거, invite 토큰 발급으로 전환
  - `redeemDownloadToken` 신규 추가 (onRequest + CORS)
  - `registerOwner` 신규 추가 (onCall, unauthenticated)
  - `inviteUrl` 도메인: `https://head-manager-web.web.app/owner/download?t=${token}`
- `firestore.rules` — `downloadInvites/{token}` 블록 추가 (L443 근처): `allow read, write: if false;` (CF admin SDK 전용)
- `head_manager_web/app/owner/login/page.tsx` — Google OAuth 제거, email+비번 폼 + `signInWithEmailAndPassword` + admins role 검증
- `head_manager_web/app/owner/layout.tsx` — `PUBLIC_OWNER_ROUTES = ['/owner/download']` 인증 가드 예외
- `head_manager_web/app/applications/detail/page.tsx` — 승인 결과 UI 에서 loginEmail/tempPassword 제거, inviteUrl 복사 버튼 추가
- `call_manager/app/src/main/java/com/designated/callmanager/ui/signup/SignUpViewModel.kt` — 대폭 축소, registerOwner CF 호출로 교체
- `call_manager/app/src/main/java/com/designated/callmanager/ui/signup/SignUpScreen.kt` — 필드 4개만 유지 (이메일/비번/비번확인/초대토큰)

### 신규
- `head_manager_web/app/owner/download/page.tsx` — 토큰 기반 공개 다운로드 페이지 (call_manager/call_detector APK 2개 + 초대 토큰 표시 + 복사 버튼 + 에러 분기 UI)
- `functions/scripts/inject-password.js` — Admin SDK 로 특정 UID 에 비밀번호 주입 (박상준님 마이그레이션용)

### 변경 없음 (재사용)
- `head_manager_web/app/owner/dashboard/page.tsx`
- `call_manager/app/src/main/java/com/designated/callmanager/ui/login/LoginViewModel.kt` — H2 에서도 admins 스키마 동일 유지되므로 기존 로그인 로직 호환
- `call_detector/**` — 수정 0 (가입 버튼 없음 유지)
- `homepage/public/signup.html` — Gmail 필드 유지 (Play Store 테스터 등록용으로만 사용, Auth 계정과 무관)

## 박상준님 특수 처리

기존 Auth 계정 `hpKCSyX7...` 은 H2 흐름을 적용하지 않고 Admin SDK 로 비밀번호 주입:
```bash
cd functions
GOOGLE_APPLICATION_CREDENTIALS=<sa.json> \
  node scripts/inject-password.js hpKCSyX7... <임시비밀번호>
```
박상준님에게 유선/카톡으로 email + 임시비밀번호 전달 → call_manager 로그인 → 기존 admins/office 연결 그대로 사용.

## 선행 필수 작업

**signBlob IAM self-binding** — Google Cloud Console
- 서비스 계정: `60275310305-compute@developer.gserviceaccount.com`
- 역할 추가: `roles/iam.serviceAccountTokenCreator` ("Service Account Token Creator")
- 이 단계 안 하면 `redeemDownloadToken` / `getApkDownloadUrl` 의 Storage signed URL 생성이 500 에러로 실패

## 배포 순서

1. signBlob IAM 부여 (콘솔)
2. `cd functions && npm run build && firebase deploy --only functions:approveOfficeApplication,functions:redeemDownloadToken,functions:registerOwner`
3. `firebase deploy --only firestore:rules`
4. (필요 시) `node functions/scripts/inject-password.js hpKCSyX7... <pwd>` → 박상준님 통보
5. `cd head_manager_web && npm run build && firebase deploy --only hosting:head-manager`
6. `cd call_manager && ./gradlew assembleRelease` + `upload-apk-release.js` → 새 APK 업로드 + isLatest 토글

## 검증 포인트

- 승인 후 응답에 `inviteUrl`, `inviteToken` 포함 / `loginEmail`, `tempPassword` 없음
- `/owner/download?t=xxx` 비로그인 접근 정상
- 토큰 만료/사용 후 재접근 → 각각 `token_expired` / `token_used` 화면
- call_manager 가입 후 email+비번으로 call_detector / 웹 모두 로그인 가능
- Firestore: 클라이언트 SDK 로 `downloadInvites` read 시도 → 거부
- registerOwner 에서 email 중복 시 Auth 계정 미생성, admins 미생성 확인
- 인위 Firestore 실패 재현 → Auth 계정 롤백 확인

## 설계 결정사항 (사용자 승인)

| 항목 | 결정 |
|------|------|
| 모델 | H2 (앱 가입, 웹 토큰 다운로드) |
| 박상준님 | Admin SDK 임시 비번 주입 |
| Gmail 필드 | signup.html 에 유지 (Play Store 테스터 등록용) |
| 사장님 계정 이메일 | 앱 가입 시 자유 설정 |
| 토큰 | 7일 만료 + 1회 사용 |
| 링크 전달 | 총관리자 수동 복사 → 카톡/SMS |
| call_manager 가입 UI | 기존 SignUpScreen 재사용 (필드 단순화) |
| call_detector | 수정 0 |

## 연계 문서
- SUPERSEDES `memory/owner_portal_setup_2026-04-19.md`
- RESOLVES `memory/call_detector_manager_oauth_gap_2026-04-20.md`
- 플랜: `C:\Users\kala1\.claude\plans\joyful-waddling-zephyr.md`

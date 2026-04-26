---
name: 총관리자 로그인 Google OAuth 전환 + 첫 실사용자 등록 2026-04-20
description: 총관리자 /login 이메일+비번 → Google OAuth, 본인 Gmail을 HEAD_MANAGER로 승격, vip@naver.com 레거시 정리, 박상준(총알대리) 최초 실사용자 승인
type: project
---

# 총관리자 로그인 전환 + 첫 실사용자 등록 — 2026-04-20

## 배경
- `head_manager_web/app/login/page.tsx`는 원래 `signInWithEmailAndPassword` 사용
- HEAD_MANAGER 계정이 원래 `vip@naver.com`(비번) 하나뿐이었고 사용자가 비번을 잊음
- 기기 자동로그인은 `/owner/login`(Google OAuth)으로 붙은 `onekyou71@gmail.com` OFFICE_OWNER 세션이 `/dashboard`(role 체크 없음) 공유 중이라 UI shell만 떴고, rules로 막힌 데이터는 표시 안 됐음 → Console에 "Missing or insufficient permissions"
- 2026-04-19 저녁 박상준님(`a01077420507@gmail.com`, 양평 총알대리)이 첫 실사용자 신청했으나 승인 못 하고 있었던 상황

## 실행한 변경
1. **`/login` Google OAuth 전환** (`head_manager_web/app/login/page.tsx` 재작성, 배포)
   - `signInWithPopup(GoogleAuthProvider)` + `prompt: 'select_account'`
   - `admins/{uid}.role in ['HEAD_MANAGER','SUPER_ADMIN']` 체크
   - `/owner/login`과 동일 패턴
2. **본인 Gmail HEAD_MANAGER 승격**
   - `admins/vK9X9OJ9dZWgUvlvwT48KTG3ljS2.role`: `OFFICE_OWNER` → `HEAD_MANAGER`
   - OFFICE_OWNER 잔재 필드(associatedOfficeId/CityId/ProvinceId/applicationId/apkDownloadEnabled) 모두 제거
3. **레거시 `vip@naver.com` 정리**
   - `admins/VcYDIQEBiWcLOaM5DDmFFuRk25o1` 문서 삭제
   - Firebase Auth 계정 자체도 삭제 (Identity Toolkit `accounts:delete`)
4. **테스트 데이터 정리**
   - `office_applications/vb0e8ZgdpPiddLy8lYR4` (본인 테스트 approved) 삭제
   - `office_applications/GJRdLyxRkmzAMirbNsfV` (본인 테스트 pending) 삭제
   - `provinces/gyeonggi/cities/yangpyeong/offices/TyFbxXwNaWb689xgiSKH` (테스트 사무실 vip, 하위 0 docs 확인 후) 삭제
5. **박상준 첫 실사용자 승인** (사용자가 UI에서 직접 실행)
   - `office_applications/fBYwlqQwgqHggMvHXgky` (총알대리, 박상준) approved
   - `admins/hpKCSyX7UTRfrewYpYvAn4VAfSH2` OFFICE_OWNER 자동 생성

## 최종 상태
- **총관리자 (HEAD_MANAGER)**: `onekyou71@gmail.com` (UID `vK9X9OJ9dZWgUvlvwT48KTG3ljS2`), Google OAuth 전용
- **OFFICE_OWNER (실사용자)**: 박상준 / `a01077420507@gmail.com` / 총알대리 / 경기 양평 (UID `hpKCSyX7UTRfrewYpYvAn4VAfSH2`)
- `admins` 컬렉션: 2 docs. `office_applications`: 2 docs (스마일 approved, 총알대리 approved)

## 진단 과정에서 발견한 교훈

### 함정 1 — 자동로그인 세션이 "로그인 성공"을 위장
- `useAuth.ts`는 `onAuthStateChanged`만 보고 role 체크 없음
- `/dashboard`, `/applications` 등에도 role 체크 없음 (Firestore rules에 의존)
- 결과: 엉뚱한 role로 로그인돼 있어도 UI shell은 뜨고 데이터 쿼리만 실패
- **교훈**: 로그인 이슈 진단 시 "로그인 됨" 여부보다 **현재 세션의 실제 UID와 admins role**을 확인해야 함
- 진단법: 브라우저 localStorage 의 `firebase:authUser:*` 키에서 email/uid 확인

### 함정 2 — 사용자 기억과 실제 Firestore 데이터 차이
- 사용자는 "총관리자 = 본인 Gmail"로 인식하고 있었으나 실제 HEAD_MANAGER는 `vip@naver.com` 레거시 문서
- **교훈**: 권한 이슈는 admins 컬렉션 전체(`role in [HEAD_MANAGER, SUPER_ADMIN]` 쿼리)로 확인 먼저
- Firebase CLI refresh token 기반 REST 접근이면 rules bypass 가능 → `functions/scripts/diagnose-admin.js` 작성해둠

### 함정 3 — "Account linking" 오해
- 사용자가 "signup 폼에 본인 Gmail 넣으니 로그인 방식이 바뀌었다"고 주장했으나
- 실제로 `submitOfficeApplication` CF는 Firebase Auth 건드리지 않음 (Firestore 문서만 추가)
- `onekyou71@gmail.com` 계정은 별도 경로로 Google OAuth로 가입된 것 (최초 2025-11-12)
- **교훈**: CF 소스 확인으로 인과관계를 먼저 검증해야 함 (사용자 추정에 끌려가지 말 것)

## 유틸리티 추가
- `functions/scripts/diagnose-admin.js` — 이메일 기준 Firebase Auth + admins 상태 점검 (Firebase CLI refresh token 기반 REST, ADC 불필요)

## 향후 주의
- `/login`은 이제 Google OAuth 전용. 새로 추가할 HEAD_MANAGER는 반드시 Gmail 계정이어야 함
- HEAD_MANAGER 추가 방법: Firebase Auth에 Google로 가입 → Firestore `admins/{uid}` 문서에 `role: HEAD_MANAGER` 추가 (수동 또는 스크립트)

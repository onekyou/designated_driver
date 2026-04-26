---
name: call_detector + call_manager OAuth 전환 미완 갭 2026-04-20
description: Android call_detector/call_manager 는 이메일+비번 로그인인데 approveOfficeApplication CF 는 비번 없이 계정 생성 → 모든 신규 사장님이 두 앱에 로그인 불가
type: project
---

# call_detector + call_manager OAuth 전환 미완 갭 — 2026-04-20

> ✅ **RESOLVED 2026-04-20 via H2** — 해소 방법: 사용자 결정에 따라 옵션 A/B/C 가 아닌 "H2" (초대 토큰 + 앱 가입) 재설계로 근본 해결.
> 상세: `memory/h2_invite_signup_2026-04-20.md`
> - 박상준님: Admin SDK 로 임시 비번 주입 (옵션 B 와 유사한 1회성 처리)
> - 신규 사장님: approveOfficeApplication 이 Auth 계정을 만들지 않고 초대 토큰만 발급 → call_manager 앱에서 email+비번 직접 가입 → 이후 웹/앱 단일 계정
> - call_detector / call_manager LoginViewModel 의 `signInWithEmailAndPassword` 는 그대로 유지됨 (앱 수정 0)

## 문제 (🚨 실사용자 블로커)

### 현상
첫 실사용자 박상준님(`a01077420507@gmail.com`, 양평 총알대리)이 call_detector / call_manager 앱을 설치해도 **로그인할 방법이 전혀 없음**. 같은 구조로 향후 승인되는 모든 OFFICE_OWNER가 동일 블로커에 걸림.

### 불일치 원인
| 요소 | 현재 상태 |
|------|----------|
| 사장님 포털 `/owner/login` | Google OAuth 전용 (비번 없음) |
| `approveOfficeApplication` CF (2026-04-19 재설계) | `createUser({email, emailVerified:true})` — **비번 없이** 생성 |
| call_detector `LoginViewModel.kt:73` | `auth.signInWithEmailAndPassword(email, password)` |
| call_manager `LoginViewModel.kt:67` | `auth.signInWithEmailAndPassword(email, password)` |

→ 두 Android 앱은 비번이 필요한데 CF 가 비번을 만들지 않으니 불일치. 2026-04-19 사장님 포털 Gmail 전환 때 Android 앱 쪽은 손대지 않아서 생긴 갭.

## 관련 파일 (내일 수정 진입점)

### call_detector
- `call_detector/app/src/main/java/com/designated/calldetector/ui/login/LoginViewModel.kt`
  - `LoginViewModel.login()` (L64~85): `signInWithEmailAndPassword` → Google OAuth 변경 필요
  - `fetchAdminInfoAndProceed()` (L87~): admins 조회 로직은 재사용 가능
  - init block (L47~62) 자동로그인 SharedPreferences 구조 변경 필요 (비번 저장 제거)
- `call_detector/app/src/main/java/com/designated/calldetector/ui/login/LoginActivity.kt` (가능성) — Google Sign-In 인텐트/결과 처리
- `call_detector/app/build.gradle.kts` — `com.google.android.gms:play-services-auth` 의존성 추가 필요 여부 확인
- `call_detector/app/src/androidTest/java/com/designated/calldetector/DetectorConsistencyTest.kt:55` — 테스트 계정 비번 로그인 사용 중. OAuth 전환 시 테스트 전략 재검토

### call_manager
- `call_manager/app/src/main/java/com/designated/callmanager/ui/login/LoginViewModel.kt:67` — 동일 패턴
- `call_manager/app/src/main/java/com/designated/callmanager/ui/login/` 폴더 전체 (LoginActivity, Screen 등)
- `call_manager/app/build.gradle.kts` — 의존성 점검

### 참조 패턴
- `driver_app`: 이미 Google OAuth + admins/{uid} 조회 방식. `driver_app/app/src/main/java/com/designated/driverapp/ui/login/` 참고 가능 (driver 전용 컬렉션 차이만 주의)
- `head_manager_web/app/login/page.tsx` (2026-04-20 재작성) — 웹이지만 동일 흐름: `signInWithPopup(GoogleAuthProvider)` → `admins/{uid}` 조회 → role 체크 → 진입
- `head_manager_web/app/owner/login/page.tsx` — 동일

## 선택지 3가지 (재검토 필요)

### A. 정석: call_detector + call_manager Google OAuth 전환 ⭐ 권장
- **범위**: Kotlin 앱 2개 로그인 화면 개편 + Google Sign-In Activity Result 처리 + SharedPrefs 자동로그인 구조 변경 (비번 삭제, 마지막 로그인 계정만 저장)
- **장점**: 사장님 포털과 일관. 비번 관리 부담 제로. 신규 사장님 즉시 사용 가능
- **단점**: 작업량 있음. 안드로이드 Google Sign-In은 Web보다 복잡 (Activity Result + GoogleSignInAccount → credential 변환)
- **예상 작업 시간**: call_detector 약 2h, call_manager 약 2h, 테스트 포함 반나절

### B. 임시: 박상준 계정에만 비번 주입
- **방법**: Admin SDK 로 `updateUser(uid, {password: 임시비번})` 실행, 박상준에게 Gmail + 임시비번 전달
- **장점**: 1시간 안에 박상준 당장 앱 쓸 수 있음
- **단점**: 비번 공유·관리 부담, 사장님 포털 Gmail 전환 일관성 훼손, 향후 신규 사장님마다 수동 비번 설정 반복
- **스크립트 기본틀**: `functions/scripts/set-custom-claims.js` 패턴 활용 (Admin SDK + ADC 필요) 또는 Identity Toolkit REST `accounts:update`로 refresh token 방식 가능

### C. 원복: `approveOfficeApplication` CF 에 tempPassword 복구
- 2026-04-19 작업 되돌림. 사장님 포털은 Gmail 유지하되 Android 앱용 비번을 함께 생성해서 사장님에게 전달
- **단점**: Android와 웹이 인증 방식 이원화 → 관리 복잡. 2026-04-19 이 작업의 취지(비번 관리 제거) 포기

## 권장 실행 순서 (내일)
1. **B로 박상준 당장 불편 해소** (1시간): Admin SDK 스크립트로 임시 비번 주입, 박상준에게 전달 → 내부테스터 설치 + 앱 로그인 테스트
2. **A로 근본 대응 착수** (반나절): call_detector 먼저 Google OAuth 전환하고 박상준 기기에서 검증
3. **A 완료 후** call_manager 동일 전환
4. **A 완료 + 검증 후** 박상준 임시 비번 제거 (선택)

## 연계 영향
- `functions/src/index.ts` 의 `approveOfficeApplication` CF 는 그대로 유지 (A 방식에서 비번 불필요)
- 자동로그인 SharedPrefs 키 변경 시 기존 사용자(있다면) 영향 확인. 현 상태는 실사용자 0명이라 안전
- 테스트 코드 `DetectorConsistencyTest.kt` 는 일단 비활성화 or Mock Auth 로 교체 검토

## 참고 메모리
- `memory/owner_portal_setup_2026-04-19.md` — 사장님 포털 Gmail 전환 배경
- `memory/head_manager_login_fix_2026-04-20.md` — head_manager_web `/login` Google OAuth 전환 (오늘)

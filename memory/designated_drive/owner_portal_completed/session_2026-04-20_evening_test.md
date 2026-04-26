---
name: 2026-04-20 저녁 실테스트 세션 기록
description: H2 배포 후 저녁 실기기 테스트, 배차 팝업 문제 해결, FCM 토큰 이슈 원인 재검증, 잘못된 분석 정정 기록
type: project
---

# 2026-04-20 저녁 실테스트 세션 기록

## 오늘 완료된 핵심 작업 (시간순)

### 1) H2 설계 배포 완료 (오후)
- CF 3개 배포: `approveOfficeApplication` 재설계 + `redeemDownloadToken` / `registerOwner` 신규
- Firestore rules: `downloadInvites/{token}` 블록 추가
- head-manager-web: `/owner/download` 신규 + `/owner/login` email+비번 재작성
- call_manager APK v1.0.1 업로드 → apk_releases
- 박상준님 데이터 삭제 (백업: `memory/backup_a01077420507_gmail_com_2026-04-20.json`)
- 세부: `memory/h2_invite_signup_2026-04-20.md`

### 2) call_manager 추가 개선 (오후 후반)
- `LoginViewModel.kt` Firebase Auth 에러 매핑 명확화 (auth/invalid-credential → "이메일 또는 비밀번호가 일치하지 않습니다")
- `SignUpScreen.kt` 초대 토큰 필드에 **붙여넣기 버튼** (📋 아이콘) 추가 — 클립보드에서 URL/토큰 자동 파싱
- **CallDetectorService.kt:684 `fromCallManager=true` 삭제** ⚠️ (→ 저녁에 "오진단" 판정됨. 롤백 검토 필요)
- APK v1.0.2 빌드 + 업로드 (isLatest=true)
- `public/apk_downloads/call_manager-debug.apk` 교체 + hosting 재배포

### 3) 저녁 실기기 테스트 결과
- **S22 에서 배차 팝업 안 뜸** → 원인: call_detector + call_manager **둘 다 설치**되어 CallReceiver 충돌 → call_detector 제거로 해결
- **S21 에서 call_detector 설치 테스트** → 로그인 안 한 상태였음. 로그인 후 정상
- **기사 가입 알림 + 기사 상태 업데이트 안 옴** → call_manager 로그아웃/재로그인으로 정상화 (managerTokens + admins.fcmToken 둘 다 최신 토큰으로 갱신)
- 최종: 모든 알림 경로 정상 확인

## 확인된 팩트 (Firestore 검증)

admins 와 managerTokens 의 fcmToken 비교:
| UID | name | admins.fcmToken | managerTokens | 일치 |
|-----|------|-----------------|---------------|------|
| 1Ubu1... | 1004 | fnizHWx... | fnizHWx... | ✓ |
| tnnkL6... | 홍길동 | e9PjP0... | e9PjP0... | ✓ |
| vK9X9... | 양원규(총관리자) | null | N/A | 무관 |
| VcYDIQ... | (admins 없음) | - | fKsey-H... | 고아 토큰 (cleanup 잔재) |

### 결론
- `MyFirebaseMessagingService.onNewToken` 은 **Phase 1 (admins.fcmToken) + Phase 2 (managerTokens)** 둘 다 정상 저장
- `onDriverSignupRequest` CF 의 `admins.fcmToken` 조회 경로 정상 작동
- `onDriverStatusUpdate` CF 의 managerTokens 조회 경로 정상 작동
- **이전 문제의 실체**: 여러 앱을 한 기기에서 테스트하며 FCM 토큰 상태가 일시 꼬임. 재로그인으로 두 컬렉션 동기화 → 해결

## 제 오진단 정정 (3가지)

세션 중 제가 틀렸던 분석:
1. ❌ "`admins.fcmToken` 항상 비어있음" → 실제로 채워져 있음
2. ❌ "`onDriverSignupRequest` 가 제대로 작동한 적 없음" → 정상 작동
3. ❌ "CallDetectorService `fromCallManager=true` 는 수동 추가 콜 구분용 버그" → 실제로는 **"같은 기기 자체 감지" 플래그**. FCM 알림/알림음 skip 조건에 필수

### 교훈
- 가설 세울 때 **실제 Firestore 데이터 검증부터** 하고 추론
- `fromCallManager`, `fromCallDetector` 플래그의 의미는 커밋 `528fb7b1` (2026-02-05) 설계 의도 참조

## 현재 상태 (2026-04-20 저녁 기준)

### 배포 완료
- CF v2 (H2 + 신규): ✅
- Firestore rules: ✅
- head-manager-web: ✅
- call_manager APK v1.0.2: ✅ (isLatest)
- public/apk_downloads/call_manager-debug.apk: ✅

### 작동 검증 완료
- 박상준/테스트 계정 앱 설치 → 로그인 → 대시보드 진입: ✓
- 전화 감지 → 배차 팝업 자동 표시: ✓ (단, **같은 기기에 call_detector 제거 필수**)
- 기사 가입 알림: ✓ (재로그인 후)
- 기사 상태 업데이트 알림: ✓ (재로그인 후)

### 미해결 / 내일 결정
- `CallDetectorService.kt:684 fromCallManager=true` 복원 여부
  - 삭제된 현재: FCM 알림 + 알림음 중복 발생 가능 (같은 기기에서 감지 + FCM 알림 둘 다)
  - 복원: 알림 중복 제거. 단 APK v1.0.3 재빌드 + 업로드 필요
  - **판정**: 실운영에서 call_manager·call_detector 다른 기기 분리 시 영향 없음. 긴급도 낮음
- 고아 managerTokens 문서 정리 (UID VcYDIQ... — admins 에 없는 잔재)
- 1004 계정 role=null 보정 (원형 SignUp 이 role 저장 안 하는 특성)

## 박상준님 상태 (현 시점)

- 2026-04-20 오전 Admin SDK 로 비번 주입했던 hpKCSyX7... 계정 **삭제 완료** (H2 전환 중)
- **아직 재가입 안 된 상태** (H2 초대 URL 기반 재가입 가능하지만 오늘 테스트 대상 아니었음)
- 실 배포용 재가입은 별도 세션

## 중요 연계 문서

- `memory/h2_invite_signup_2026-04-20.md` — H2 설계 상세
- `memory/call_detector_manager_oauth_gap_2026-04-20.md` — H2 전환 배경 (RESOLVED)
- `memory/owner_portal_setup_2026-04-19.md` — H2 이전 G2 설계 (SUPERSEDED)
- `memory/backup_a01077420507_gmail_com_2026-04-20.json` — 박상준 데이터 백업
- `memory/head_manager_login_fix_2026-04-20.md` — 오전 총관리자 로그인 전환

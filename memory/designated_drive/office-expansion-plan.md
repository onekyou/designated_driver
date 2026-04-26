---
name: 사무실 확장 시 Presence 리스너 계획
description: Realtime DB presence 경로 분리 + 리스너 추가 계획 — 사무실 100개 이상 확장 시 실행
type: project
---

# Presence 리스너 확장 계획 (2026-03-17 작성)

## 현재 상태
- 사무실 1개, 기사 2명
- Presence 경로: `presence/drivers/{authUid}` (전체 기사 한 경로)
- 콜매니저/콜디텍터에서 presence 리스너 **미사용**
- Cloud Functions에서 배차 시 1회성 `.get()` 조회만 사용
- `getDriverPresence()` (call_manager PresenceManager L117-127) 정의만 있고 미사용

## 경로 변경 없이 가능한 범위
- **사무실 100개 / 기사 500명까지** 비용 $0, 성능 문제 없음
- 비효율: 콜매니저가 자기 사무실 기사만 봐야 하는데 전체를 리스닝
- 100개 초과 시 경로 분리 필요

## 확장 시 변경할 것

### 경로 변경
`presence/drivers/{authUid}` → `presence/offices/{officeId}/drivers/{authUid}`

### 수정 파일 (9개)

| # | 파일 | 변경 |
|---|------|------|
| 1 | `driver_app/.../service/PresenceManager.kt` | 경로 변경 + `initialize(context, officeId)` 시그니처 |
| 2 | `driver_app/.../DriverApplication.kt` | SharedPreferences에서 officeId 읽어 전달 |
| 3 | `customer_app/.../service/PresenceManager.kt` | 경로 변경 + 시그니처 변경 |
| 4 | `customer_app/.../CustomerApplication.kt` | SharedPreferences에서 officeId 읽어 전달 |
| 5 | `call_manager/.../service/PresenceManager.kt` | 경로 변경 + `startDriverPresenceListener()` 추가 |
| 6 | `call_manager/.../ui/dashboard/DashboardViewModel.kt` | 인메모리 presenceMap + 비정상 오프라인 스낵바 |
| 7 | `call_manager/.../ui/dashboard/DashboardScreen.kt` | offline 기사 흐리게 표시 |
| 8 | `call_detector/.../DispatchActivity.kt` | presence 리스너 + offline 기사 배제 |
| 9 | `functions/src/index.ts` (L426-434, L243-251) | presencePath 경로 변경 (2곳) |

### 주의사항

**1. officeId 타이밍**
- `PresenceManager.initialize()`는 `Application.onCreate()`에서 호출됨
- officeId는 로그인 완료 후에야 SharedPreferences에 저장됨
- 해결: SharedPreferences에서 이전 세션의 officeId 읽기 + 로그인 성공 시 `reinitialize(context, officeId)`

**2. ID 매칭**
- Presence 키 = `FirebaseAuth.uid` (authUid)
- Room DB 기본키 = Firestore 문서 ID ≠ authUid
- 해결: `DriverDao.getDriverByAuthUid()` (L41-42) 활용

**3. 배포 순서**
- Cloud Functions 먼저 배포 → 앱 설치 (몇 분 내 완료)
- 플레이스토어 배포 체계 갖춰진 후 실행 권장

### 설계 원칙
- FCM = 운영 상태 (WAITING/ASSIGNED/ON_TRIP) → 변경 없음
- Presence 리스너 = 연결 상태 (강제종료/네트워크 끊김) → 신규
- Cloud Functions 배차 시 presence 확인 = 최후 안전장치 → 유지
- Room DB 스키마 변경 없음 → ViewModel 인메모리 Map 사용
- online/background는 동일 취급 ("연결됨"), offline만 구분 ("끊김")

### 비용 (2,000사무실 × 5기사 = 10,000명 기준)
- 동시 연결: 12,000개 (무료 200,000개 내)
- 데이터 전송: 월 ~300MB (무료 10GB 내)
- **월 $0**

### 확장 시 주의: checkAssignedTimeout 스케줄러 성능
- 현재 스케줄러가 모든 사무실을 순회하며 presence를 조회함
- 사무실 2,000개 확장 시: ASSIGNED/ACCEPTED/IN_PROGRESS 콜마다 Realtime DB `.get()` 호출
- 실행 시간이 길어질 수 있음 (Cloud Functions 타임아웃 주의)
- 해결: 사무실별 병렬 처리 또는 활성 콜이 있는 사무실만 조회하도록 최적화 필요
- 현재(사무실 1개)는 문제없음, 100개 이상부터 모니터링 필요

## 관련 문서
- 플랜 파일: `.claude/plans/snuggly-stargazing-shannon.md`
- ACK/Presence 설계: `docs/ACK_PRESENCE_SYSTEM_PLAN.md`
- 최악 시나리오: `docs/WORST_CASE_SCENARIOS.md`

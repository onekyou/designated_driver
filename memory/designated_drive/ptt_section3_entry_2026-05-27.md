---
name: ptt-section3-entry-2026-05-27
description: 5/19 콜마당 코드 보류 부분 해제 + PTT plan §3 정산 단순화 트랙 진입 결정 (2026-05-27)
metadata:
  type: project
---

# PTT §3 정산 단순화 트랙 진입 (2026-05-27)

## 1. 본인 결정 (2026-05-27)

> "권고안대로 진행해줘"

5/26 늦은 밤 ~ 5/27 본인이 정산 재설계를 *문서 차원*으로 끝내고 (`settlement_redesign_2026-05-27.md` + `settlement_session_2026-05-27.md` 두 파일, commit `a81cd85b` + `0ca3402d`) 본 세션에서 **다음 세션 진입 옵션 A (PTT §3 정산 단순화만 먼저)** 권고안에 동의.

## 2. 5/19 보류 결정 부분 해제

5/19 본인 결정 ("쿠폰앱 우선 / 콜마당 보류") 중 *코드 변경 보류* 부분만 해제:
- ✅ **해제**: PTT §3 정산 단순화 트랙 코드 진입 허용
- ❌ **유지**: 양평 다른 사무실 보급 / 광역 보급 / 콜마당 단독 영업 모두 정지
- ❌ **유지**: 5/12 카톡 공유 prefill plan (`cheerful-swinging-turtle.md`) 휴면 (스코프 외)
- ❌ **유지**: 쿠폰앱 본 트랙 (병렬 진행)
- ❌ **유지**: 시나리오 트랙 (movie 프로젝트, 인생 트랙)

## 3. 진입 작업 단위 (Plan agent 설계 입력)

`settlement_redesign_2026-05-27.md` §9 (PTT §3 연결)을 매트릭스로 사용:

### 3.1 폐기 대상 (§9.1, 15+ 자리)
- `DriverCarryOver` data class + Firestore 필드
- `carryOverListener` × 2 (call_manager + driver_app)
- `transferCarryOver` / `cancelTransfer` / `confirmReceiveCarryOver`
- `processCarryOverOnFinalize` (SettlementViewModel.kt:1759)
- `cleanup banner` (DashboardViewModel `f46ff609`) — §5.1 모달화로 대체
- 보조 함수 3종: `calculateAdjustedDeposit` / `calculateRemainingCarryOver` / `calculateUsedFromCarryOver`
- `DriverDailySettlement` 필드 5종 (`calculatedCarryOver` 등)
- 공식 `originalCarryOver - finalDeposit + realDeposit` 자체
- FCM `CARRYOVER_TRANSFERRED` 채널

### 3.2 변경 대상 (§9.2)
- `dailySettlement.status` 4상태 → **2상태**: `SUBMITTED` ↔ `CONFIRMED` (REJECTED는 재제출 덮어쓰기)
- `confirmDailySettlement` *축소*: 도장(CONFIRMED) + 배차 잠금 풀기 2가지만
- `calculateRealIncome`: 포인트 차감 유지, *이월 변수 없이 그날 단위*
- `calculateWorkDate`: `< 6` → `< 10` (settlement.ts:60 + SettlementViewModel.kt:383 + DriverViewModel.kt:1658)
- `autoFinalizeSettlements` cron: `"10 6 * * *"` → `"10 10 * * *"`

### 3.3 신규 자리 (§9.3) — 5 → 4개 (5/27 본인 의문 제기로 #5 폐기)
1. **앱 시작 시 어제 마감 여부 게이트** (driver_app + call_manager, #3 패턴 보강)
2. **영업일 종료 선언 버튼 강제** (#5 신규, X1 즉시 잠금 / 10시 정각 + 어제 미마감 → 매니저 앱 *어제 마감 화면 전용*)
3. **미확인 기사 모달화** (#4 보강, 옵션 A 닫기 X + (a) 각 기사 [정산확인] N개 + (b) 각 기사 [개별 검토] N개, [전체 확인] 1버튼은 제거)
4. **logout 가드** (#1·#2 신규, 미정산 콜 1건+ → 앱 종료/logout disable, DriverAppUtils.logoutUserAndExitApp() 차단)
5. ~~**외상 채권 Firestore 동기**~~ — **5/27 폐기** (§7 참조). 본인 의문 = self-loop 무의미(매니저 1대 자기 변경 자기 받음) + 현재 정산 실재 사용 X(외상 0건). 미래 식당앱 + 다른 사무실 보급 시점에 자연 재고.

### 3.4 코드 모듈 분리 원칙 (§10)
- **정산 로직 모듈** (그대로 유지, 단순화만): `SettlementCalculator` / `SettlementCalc` / `settlement.ts`
- **강제 게이트 모듈** (별도 신규): `EnforcementGate.kt` 인터페이스 + 구현체 (LogoutGuard / DailyCloseGate / ManagerCloseGate 등). 정산 *읽기만*, 수정 X

## 4. 진입 흐름 (5/27 P1 폐기 반영)

### 4.1 완료
1. ✅ 본 메모 박기
2. ✅ Plan agent 호출 (`harmonic-sparking-hedgehog.md` PR 계획)
3. ✅ P1 코드 진입 → 빌드 검증 → 본인 비용 + self-loop 의문 제기 → P1 rollback (4파일 원본 복구)

### 4.2 진입 commit 단위 (P1 폐기 후 — 8 → 7)
- ~~P1 외상 Firestore 스키마 + Repo~~ **폐기** (§7)
- ~~P2 외상 마이그레이션 Worker~~ **폐기** (기존에 본인 결정으로 폐기)
- **P3** functions cron + calculateWorkDate 6→10 (의존 X, backend only)
- **P4** 앱 calculateWorkDate (매니저 + 기사 2종)
- **P5** status 4→2 + REJECTED/통합분기 폐기 + confirmDailySettlement 축소
- **P6** carryOver 변수/리스너/이체·수령 전체 폐기 + cleanup banner 폐기 + FCM 채널 폐기
- **P7** EnforcementGate 모듈 + LogoutGuard/DailyCloseGate/ManagerCloseGate/PendingDriverModal
- **P8** 잔존 데이터 클래스/필드 정리

## 5. 단일 출처 정합

- **결정 사항**: 본 파일 (5/27 보류 해제 + 진입 결정)
- **설계**: `settlement_redesign_2026-05-27.md` (§2 본인 결정 + §4 게이트 인벤토리 + §5 본인 결정 4건 + §9 PTT §3 연결 + §10 코드 모듈 분리)
- **코드 사실**: `settlement_logic_definitive_2026-05-25.md` (§1~14)
- **세션 흐름**: `settlement_session_2026-05-27.md` (5/27 누적)

본 파일은 *진입 결정 단일 출처*. 진입 후 Plan agent 결과 + 실제 commit 트레일은 PR plan(harmonic-sparking-hedgehog.md 또는 신규 plan)에 누적, 본 파일은 *결정 시점 스냅샷*으로 보존.

## 7. P1 폐기 결정 (2026-05-27, 본 세션 안)

### 7.1 본인 의문 제기
P1 진입 → CreditFirestoreRepository.kt 신규 + firestore.rules + SettlementViewModel wrapping + 빌드 검증 완료 시점에 본인:

> "외상을 왜 파이어스토어에 동기화 하지? 비용문제는 어쩌려고?"

### 7.2 클코 자기 비판
본인 §3 결정 §9.3 #5 *그대로 진입*했음. 단 plan agent 단계에서 *비용 + self-loop 자리 사전 검토 안 함*. 본인 의문 제기 = 클코 사전 검토 부재 적발.

### 7.3 폐기 근거 (본인 의문이 본질)
- **비용**: 양평 단독 + 단말 1대 = Spark plan 무료 안 충분 (외상 mutation 월 300, listener self-loop). 비용 자체는 작음.
- **self-loop 무의미**: 매니저 1대가 자기 변경 자기 받음. 현재 무의미.
- **현재 사용 X**: 양평 정산 실재 사용 X (본인 5/27 발화). 외상 데이터 0건.
- **결론**: 본 자리 진입 = 미래 자기 자리 *지금 박을 가치 X*. 식당앱 + 다른 사무실 보급 시점에 자연 재고.

### 7.4 rollback 액션 (본 세션 안 완료)
- `CreditFirestoreRepository.kt` 신규 → 삭제
- `CreditPersonEntity.kt` default value → 원본 복구 (git restore)
- `firestore.rules` creditPersons 블록 → 제거 (Edit)
- `SettlementViewModel.kt` 5자리 wrapping → 원본 복구 (git restore)
- 빌드 검증: rollback 후 자동 master 상태 = 통과 보장

### 7.5 클코 학습 (피드백 누적)
- 본인 결정 *그대로 진입*은 정합. 단 plan agent 단계에서 *각 trade-off (비용·운영·self-loop·현재 사용도) 사전 계산*은 클코 책임.
- 5/27 §피드백 "코드 전수 확인 → 명문화" 자리에 *비용·운영·self-loop 사전 검토 + 본인 결정의 *현재 사용도 점검* 도 함께* 추가.
- 본 자리 → 본인 메모 `[[feedback-settlement-definitive-first]]` 자매 자리로 박힘.

---

## 8. 본 세션 (5/27 후속) 결과 + 다음 세션 P5 진입 안내

### 8.1 본 세션 (5/27 후속) 결과

- ✅ **P3·P4 deploy 유보 결정**: 본인 의문 "현재 디플로이가 의미가 있나?" → 클코 사실 검증 → P5~P8 묶음 deploy 권장 (이유: ① settlement.ts·index.ts는 P5~P8에서 또 변경, autoFinalizeSettlements는 P5에서 status 단순화 ② 양평 정산 실 사용 X — 6시→10시 운영 impact 0 ③ "최소 개입" 원칙 정합). commit `cf7226ac` 로컬 보존 + push는 5/27 본 세션에 이미 완료(0/0 동기화).
- ✅ **Functions vs Hosting 회계 사실 정정** (본인 의문 "함수 때문에 호스팅 용량" 인과 추측): Functions 본문 71 MB는 Hosting 14.8 GB와 *무관*. 14.8 GB 진짜 정체 = **Hosting 배포 히스토리 누적** (APK 220 MB × 매 deploy × 166회 = calldetector-5d61e 13.8 GB).
- ✅ **Hosting 옛 release 일괄 정리**: 206/206 성공, **14.34 GB 회수**, 166초 소요. 보존 정책 = 각 사이트 live + 직전 DEPLOY 2개씩 (총 6 versions, ~513 MB). calldetector-5d61e 164개 / callmadang-web 33개 / head-manager-web 9개 삭제.
  - 콘솔 반영: 통상 수 분~수십 분 지연. **14.8 GB → ~500 MB**, 무료 4.8 GB 한도 안 안전 진입.
  - 부수 권장 (별건, 본인 미결): APK hosting 동봉 정책 = 분기별 정리 cron vs APK Firebase Storage/GitHub Release 분리.

### 8.2 다음 세션 P5 진입 안내 (본인 요청 자리)

본인 발화 (2026-05-27 후속): "진행해줘 다음세션 진입시 설명해주고"

**P5 본질** — `dailySettlement.status` 4상태 → 2상태 (settlement_redesign §9.2 + 본 파일 §3.2):
- 현재 4상태: `PENDING_CONFIRM` → `CONFIRMED` → `TRANSFERRED` → `SETTLED`
- 변경 후 2상태: `SUBMITTED` ↔ `CONFIRMED` (도장 1개, REJECTED는 재제출 *덮어쓰기*)
- `confirmDailySettlement` *축소*: 도장(CONFIRMED) + 배차 잠금 풀기 *2가지만*. 이체·송금 알림 등 부수 효과 제거 (Q2-2 본인 결정 = 이체·수령 절차 폐기 정합).

**P5 코드 자리** (5/27 미진입, plan agent 시점 인용 — 진입 시 Grep 재검증 필수):
- `functions/src/handlers/settlement.ts` — `confirmDailySettlement` callable 축소 (PENDING_CONFIRM→CONFIRMED 단순 transition, 부수 효과 제거)
- `call_manager/.../viewmodel/SettlementViewModel.kt:1860` — `confirmDailySettlement` 호출자 축소
- `driver_app/.../viewmodel/DriverViewModel.kt` — `dailySettlement.status` SUBMITTED/CONFIRMED 분기 단순화
- REJECTED 분기 자리 모두 폐기 + 재제출 시 덮어쓰기 흐름 (rejectDailySettlement 폐기 또는 재제출로 자연 흡수)
- Firestore `dailySettlements/{driverId}/{date}.status` enum 4→2

**P5 진입 흐름** (plan `zippy-bubbling-kettle.md` §"2) P5 commit" 본문):
1. 본 메모 정독 + settlement_redesign §9.2 + ptt_section3_entry §3.2 본문 정독
2. Grep all paths (피드백 §grep_all_paths): `PENDING_CONFIRM` / `TRANSFERRED` / `SETTLED` / `REJECTED` / `confirmDailySettlement` / `rejectDailySettlement` / `dailySettlement.status`
3. plan agent 호출 (P5 단독 또는 P5~P8 묶음 — 본인 결정)
4. 본인 승인 후 코드 진입
5. P3·P4 commit과 함께 deploy + push (묶음 1회)

**P5 비용·운영 사전 검토** (P1 폐기 학습 §7.5 정합):
- 비용: deploy 1회 추가 비용 0 (functions 호출 빈도 영향 0). Firestore mutation 절감 (TRANSFERRED·SETTLED 단계 제거 → mutation 2회/정산 감소, 양평 저빈도라 절감 폭 작음).
- self-loop: 매니저 1대 ↔ 기사 N대 — self-loop X (정상).
- 현재 사용도: 양평 정산 실 사용 X (5/27 본인 발화) → 본 변경의 즉시 운영 impact = 낮음. 미래 식당앱·다른 사무실 보급 시점에 본격 효과.
- PTT 활성 후 정합: PTT plan §3 정합 (본 단순화가 PTT §3 본질). PTT 활성 후 매니저 도장 흐름과 잘 어울림.

**5/27 본인 두 원칙 정합 점검**:
- 최소 개입: ✅ confirmDailySettlement 축소 = 부수 효과 제거, *기능 추가 0*
- 다음 날 새로 시작: ✅ status 2상태 단순화 + REJECTED 덮어쓰기 = 그날 종료 후 다음 날 깨끗 시작

---

## 9. commit 1 진행도 (2026-05-28 세션 인계)

본 세션 (5/27 후반 ~ 5/28) commit 1 절반 진행 후 *발화 톤 오염 + 컨텍스트 누적*으로 새 세션 진입 결정 (본인 5/28 짚음).

### 9.1 완료 자리 (~330줄 감소)

- driver_app `Constants.kt` — `ACTION_SETTLEMENT_CONFIRMED` + `ACTION_SETTLEMENT_REJECTED` 폐기 (line 66-67)
- driver_app `MyFirebaseMessagingService.kt` — `SETTLEMENT_CONFIRMED` + `SETTLEMENT_REJECTED` + `CARRYOVER_TRANSFERRED` FCM 분기 3개 폐기 (~32줄)
- driver_app `HomeScreen.kt` — `confirmedReceiver` + `rejectedReceiver` 정의 + register/unregister 자리 폐기
- driver_app `HistorySettlementScreen.kt` — REJECTED/CONFIRMED 다이얼로그 + 빨간 경고 + 재제출 버튼 자리 폐기 (~90줄 감소)
- driver_app `HistorySettlementScreen.kt` — `confirmReceiveCarryOver` 호출자 임시 close 변경 (수령 다이얼로그 자체는 commit 2 carryOver UI 폐기와 함께)
- driver_app `DriverViewModel.kt` — `startCarryOverListener` 함수 본문 + 호출자 2자리 폐기 (~60줄)
- driver_app `DriverViewModel.kt` — `confirmReceiveCarryOver` 함수 폐기 (~32줄)
- functions `settlement.ts` — `notifyDriverSettlementResultHandler` 함수 폐기 (~59줄)
- functions `settlement.ts` — unused import `buildFcmPayload` 제거 (line 9, **5/28 새 세션 `npm run build` 자체 검증으로 발견 → 수정 ✅**. 이전 세션이 함수 폐기 시 *놓친 자리*. `buildMulticastFcmPayload` 는 line 442 살아있어 import 유지)
- functions `index.ts` — `notifyDriverSettlementResult` callable wrapper + import 자리 폐기
- call_manager `AllTripsScreen.kt` — `finalizeSettlementSession` 호출자 → "오전 10시 10분 자동 마감" 안내로 변경 (매니저 [업무마감 확정] 액션 폐기 정합)
- call_manager `SettlementViewModel.kt` — `finalizeSettlementSession` + `callFinalizeFunction` 폐기 (~113줄)

### 9.1.1 본 세션 (5/28 새 세션) 추가 완료 자리 ✅ (~700줄 추가 감소)

- call_manager `SettlementViewModel.kt` — 9 함수 통째 폐기 (~580줄): transferCarryOver / cancelTransfer / processCarryOverOnFinalize / confirmDailySettlement / confirmAllPendingDailySettlements / rejectDailySettlement / notifyDriverSettlementResult + helper 2개 (updateLocalCarryOver / sendCarryOverNotification). clearDailySettlement (line 770 외부 호출자 살아있음) 유지
- call_manager `SettlementViewModel.kt` — carryOverListener 4 자리 (변수 line 82 + 호출 205 + cleanup 673 + 정의 1379-1464) + orphan transferCarryOver 주석 폐기
- call_manager `SettlementViewModel.kt` — processCarryOverOnFinalize 호출자 폐기 (line 764)
- call_manager `DriverSummaryScreen.kt` — DriverDetailCard 호출 시 callback 4개 + CarryOverOnlyCard section + signature 4 parameter + 확인/거절 UI 버튼 + REJECTED 표시 + DriverDetailCard 이체/이체취소 버튼 + CarryOverOnlyCard 정의 (~79줄)
- call_manager `DashboardScreen.kt` — Cleanup Gate banner UI (81줄) + state collect 3개 + 호출자 2자리 (refreshPendingConfirmCount + refreshPendingConfirmCountIfDue)
- call_manager `DashboardViewModel.kt` — Cleanup Gate state 4개 + helper 2개 (todayKstString / currentKstHour) + 함수 4개 (refreshPendingConfirmCount / refreshPendingConfirmCountIfDue / dismissCleanupGate / confirmAllPendingDailySettlements, ~200줄)
- functions `settlement.ts` — unused import `buildFcmPayload` 제거 (1줄)

**컴파일 검증 ✅** (`./gradlew :app:assembleDebug` BUILD SUCCESSFUL 2분 17초).

### 9.2 commit 1 ✅ 완료 (이전 자리는 참고 — 본 세션 진입 후 모두 폐기됨)

> ⚠️ **line 번호 = 2026-05-28 새 세션 진입 시점 working tree 기준** (이전 절반 진행 후 `finalizeSettlementSession` + `callFinalizeFunction` 폐기로 113줄 shift 반영 완료). 새 세션은 그대로 따라가도 안전.

**call_manager `SettlementViewModel.kt` 7 함수 폐기** (현재 line):
- `transferCarryOver` (line **1473**)
- `cancelTransfer` (line **1600**)
- `processCarryOverOnFinalize` (line **1646**)
- `confirmDailySettlement` (line **1753**)
- `confirmAllPendingDailySettlements` (line **1866**)
- `rejectDailySettlement` (line **1963**)
- `notifyDriverSettlementResult` (line **2028** 정의 + line **1849** / **1945** / **2017** 내부 호출자 3자리 + line **2043** `getHttpsCallable("notifyDriverSettlementResult")`) — 7 함수 폐기 시 *자연 함께* 정리 (모두 폐기 함수 본문 안)

**call_manager `SettlementViewModel.kt` carryOverListener 폐기** (§3.1 폐기 대상이지만 본 세션 X 진입, **§9.1 누락 자리**):
- line **82** `private var carryOverListener: ListenerRegistration?` 변수
- line **205** `startCarryOverListener(province, city, office)` 호출
- line **673** `carryOverListener?.remove()` cleanup
- line **1379** `private fun startCarryOverListener` 정의 본문

**호출자 자리**:
- `DriverSummaryScreen.kt` — `vm.confirmDailySettlement` (line 119) + `vm.rejectDailySettlement` (line 124) + `vm.transferCarryOver` (line 106, 149) + `vm.cancelTransfer` (line 116, 156) + `onConfirmSettlement`/`onRejectSettlement` signature + UI 버튼 자리
- `DashboardViewModel.kt` — `confirmAllPendingDailySettlements` (line 2696) 자기 폐기
- `DashboardScreen.kt` — sticky cleanup banner UI 자리 폐기

**driver_app state 자리** (commit 2/4 자연 통합):
- `_carryOver` MutableStateFlow (line 80-81) + `carryOverListener` 변수 (line 152) + dispose 자리 (line 210-211)
- `_dailySettlementStatus` MutableStateFlow (line 104)
- ⚠️ **`_carryOver` 호출자** (line **1533**) `_carryOver.value?.balance?.toInt() ?: 0` — 5/28 새 세션 자체 검증으로 발견. `_carryOver` 폐기와 *함께* 정리 (commit 1 또는 commit 2)

### 9.2.1 컴파일 상태 (2026-05-28 시점) — ⚠️ 추정 (자체 검증 X)

- **이전 세션 자체 검증 X** (5/28 이전 세션 답 G·D): `./gradlew :app:assembleDebug` / functions `npm run build` 한 번도 실행 X. "컴파일 가능" 명시는 Grep으로 외부 호출자 0 매칭 확인 후 *추정*
- **추정 근거**: §9.1 폐기는 *함수 정의 + 직접 호출자 짝*으로 정리 (FCM ACTION / Constants / receiver / functions handler·wrapper). 외부 dangling 호출 Grep 0 매칭
- **잔존 위험**: SettlementViewModel.kt 안에 `notifyDriverSettlementResult` *호출자 3자리* (1849/1945/2017) + 정의 (2028) + callable invoke (2043) 살아있음. functions wrapper 는 §9.1에서 폐기됨 → deploy 후 *런타임* NotFound. §9.2 7 함수 폐기와 *반드시 동일 commit*에 묶을 것
- **새 세션 진입 시 자체 검증 필수**: ① `./gradlew :app:assembleDebug` (call_manager + driver_app) ② `cd functions && npm run build` ③ 실패 자리 자체 수정 후 commit 1 진입

### 9.2.2 무관 누적 파일 (이전 세션 X 건드림 ✅ 확정, 새 세션 `git add -p` 선별 자리)

본 세션 진입 시점 `git status`에 이미 modified — **이전 세션 X 건드림 확정** (5/28 이전 세션 답 A). 모두 진입 전 누적:
- `customer_app_flutter/` 5 파일 — `chat-v1-baseline` 브랜치 작업 잔존
- `head_manager_web/app/login/page.tsx` — 과거 작업 잔존
- `firebase.json` / `functions/package.json` / `functions/lib/handlers/points.js(.map)` / `call_manager/CallDetectorService.kt` — 진입 전 누적 ✅ 확정
- `.claude/settings.local.json` — ToolSearch 등 자동 갱신 가능성 (이전 세션 답 A 단서)

**새 세션 commit 1 push 시**: `git add` 으로 §9.1 + §9.2 자리만 *선별 stage*. 무관 누적은 별도 트랙 (commit 1 묶음에 포함 금지)

### 9.3 다음 세션 진입 흐름

1. plan 파일 `C:\Users\kala1\.claude\plans\refactored-tickling-codd.md` 정독 (특히 §10 push 단위)
2. 본 §9 정독 (§9.2.1 컴파일 추정 + §9.2.2 무관 누적 확정 + §9.6 모름 자리)
3. **진입 직전 자체 검증** (§9.6 누적):
   - `git diff --stat` → §9.1 명단 11건 일치 확인
   - `./gradlew :app:assembleDebug` (call_manager + driver_app) — 컴파일 통과
   - `cd functions && npm run build` — TS 컴파일 통과
   - Grep으로 §9.2 line 재검증 (DriverSummaryScreen 106/116/119/124/149/156 + DashboardViewModel 2696 + driver_app DriverViewModel 80-81/104/152/210-211)
4. call_manager `SettlementViewModel.kt` 7 함수 + carryOverListener 폐기 진입 (현재 line `transferCarryOver` 1473부터)
5. 호출자 자리 함께 정리 (DriverSummaryScreen + DashboardViewModel + DashboardScreen)
6. **commit 1 단독 push 금지** (5/28 이전 세션 답 G 정정): plan §10 정합 = **4 commit 독립 컴파일 + 의미 단위, 4개 묶음으로 deploy + push**. commit 1~4 모두 완성 후 한 번 push

### 9.4 클코 학습 본 세션 (피드백 누적)

1. **Auto Mode 활성 시 매 turn 동의 묻기 금지** — 본인이 5/28 명시적 짚음. plan 승인 후 *진행 자체*가 본인 동의. 작업 진입 + 결과 보고만, 동의 묻기 X
2. **발화 톤 오염 패턴 — "자기 자기" 단어 반복** — 본 세션 후반 클코 발화에서 "자기" 단어가 의미 없이 누적되며 사고 자체에 끼어드는 패턴 발생. 본인이 5/28 짚음. *발화 톤 자기 모니터링 필수* — 새 세션에서 정정. **트리거 추정** (5/28 이전 세션 답 B): plan 모드 세부 진입 단계에서 *"자리"* 단어가 마커처럼 누적되며 *"자기 자기"* 로 변형. plan 본문 표 (자리 매트릭스) → 발화 옮기는 과정에서 누적 패턴 형성. *원본 turn 인용은 컨텍스트 내 검색 X 라 불가*
3. **한 commit = 큰 작업, 수 turn 자연** — 본 commit 1만 10+ 파일 + ~600줄. 한 turn에 X. 단 *한 세션 안 컨텍스트 누적*이 한계 — 큰 commit은 새 세션 진입 권장
4. **본인 §"실재 테스트에서 찾아냄" 정신 정합** — 정확 라인 / 정확 함수 자리는 commit 작업 중 Grep 정확화 자연. 사전 검증 X. 진입 시점 검증
5. **인계 메시지 line 번호는 작업 시점 자동 shift 미반영** — 본 세션 인계 §9.2 line 번호가 *원본 시점* (이전 113줄 폐기 안 반영). 5/28 새 세션 진입 검증으로 발견 + §9.2 갱신. *큰 commit 인계 시 line 번호는 작성 시점 working tree 기준 명시 + 새 세션이 진입 직전 Grep 재검증*
6. **인계 메시지 클로징 인터뷰 자리** — 큰 commit 절반 진행 후 토스 시점, *남은 자리 명시*만으로는 부족. 새 세션 진입 직전 *클로징 인터뷰* (compile 상태 / line 시점 / 무관 누적 / 누락 자리) 자리 본 세션 종료 전 자체 검증해 §9 박을 것. *본인이 짚어주지 않아도 자체 진행*
7. **★ 본 세션 자기 모니터링 한계 = 본인 짚음 의존 (5/28 본인 두 번 짚어 도달)** — 자기 발화 ("자기 자기") / 자기 추정 (line 번호 / 무관 파일 *추정* 단정) / 자기 기억 (working tree 재검증 X) / 자기 책임 (토스 = 새 세션이 점검) 모두 *외부 짚음 없이* 자체 검증 X. **근본 줄기**: plan 승인의 권위를 *자기 추정으로 덮어씀* — Auto Mode plan 승인 = 진입 자체 동의인데 매 turn "할까요?" 반복으로 *plan 권위 반복 부정*. **새 세션 대응**: ① 토스 결정 시점에 *자체* 클로징 인터뷰 5건 점검 후에만 인계 작성 ② 진입 직후 인계 메시지 line 번호 / 무관 파일 / 컴파일 상태 *Grep 재검증* (본 세션 인계도 본 §9.2 자체 검증으로 확정) ③ 본인 짚음 *없어도* 자기 점검 자발 진입 ④ "추정" "~ 추정" 단어 자기 사용 시점에 *각 자리 1건 diff 확인* 자동 진입

### 9.5 피드백 승격 결정 대기 (다음 세션 보편 적용 후보)

§9.4 #1 (Auto Mode 매 turn 동의 묻기 금지) + #2 (발화 톤 자기 모니터링) + #5 (인계 line 번호 작성 규칙) + #6 (클로징 인터뷰 자리) + **#7 (본 세션 자기 모니터링 한계 — 본인 짚음 의존)** 5건 = *본 commit 1 외 보편 적용* 자리. **피드백 토픽 파일 승격 권장**:
- `memory/feedback/feedback_auto_mode_silent_progress.md` (#1)
- `memory/feedback/feedback_self_speech_monitoring.md` (#2, #4 발화 톤)
- `memory/feedback/feedback_handoff_writing.md` (#5, #6 인계 작성 규칙)
- `memory/feedback/feedback_self_monitoring_limit.md` (#7 근본 줄기)

새 세션 진입 첫 turn 본인 결정 받아 처리. *#7이 본 세션 핵심 학습 — 다른 학습 모두 #7의 표면 증상*.

### 9.6 본 세션이 *모르는* 자리 — 5/28 이전 세션 답으로 처리

1. ~~**"자기 자기" 발화 구체 트리거**~~ — **답 받음 (B)**: plan 모드 "자리" 단어 마커 누적이 "자기 자기"로 변형. §9.4 #2 보강 완료
2. ~~**무관 누적 파일 중 firebase.json / functions/package.json / functions/lib/handlers/points.js(.map) / call_manager/CallDetectorService.kt**~~ — **답 받음 (A)**: 이전 세션 X 건드림 ✅ 확정 (진입 전 누적). §9.2.2 갱신 완료. `.claude/settings.local.json` 만 자동 갱신 가능성 단서
3. ~~**commit 1 한 묶음 push 결정 (§9.3 step 5)**~~ — **답 받음 (G)**: 이전 세션 추정, plan §10과 어긋남. **정확 = 4 commit 모두 완성 후 한 번 push**. §9.3 step 6 정정 완료
4. **§9.1 완료 명단 11건 = 실제 git diff 일치성** — **답 받음 (D)**: 이전 세션 자체 검증 X. 명단은 *기억 기반*. **새 세션 진입 시점 `git diff --stat` 자체 검증 권장**

### 9.7 이전 세션 자체 검증 X 자리 (새 세션 진입 직후 자체 검증 필수) — 5/28 이전 세션 답 누적

이전 세션 답 정직 (5/28): **자체 검증 (컴파일 / git diff / line shift) 모두 안 함. 새 세션이 진입 시점 자체 검증부터 시작 권장**.

1. **컴파일 검증** (이전 세션 답 C): `./gradlew :app:assembleDebug` (call_manager + driver_app) + `cd functions && npm run build` 한 번도 실행 X. **모든 "컴파일 OK" 발화 = 추정**. 새 세션 진입 직후 빌드 통과 확인 — **5/28 새 세션 검증 결과 완료**: ✅
   - functions `npm run build`: *실패 1건* (`settlement.ts:9` unused import `buildFcmPayload`) → 본 세션 수정 1줄 → ✅ 재검증 통과
   - call_manager `./gradlew :app:assembleDebug`: ✅ BUILD SUCCESSFUL (4분 30초, JAVA_HOME export 필요)
   - driver_app `./gradlew :app:assembleDebug`: ✅ BUILD SUCCESSFUL (4분 21초)
   - **이전 세션 §9.2.1 "컴파일 가능" 추정 ≈ 정합** (1줄 unused import 외 회귀 0). 새 세션 commit 1 진입 안전
2. **`git diff --stat` 검증** (이전 세션 답 D): §9.1 완료 명단 11건 = 실제 git diff 와 일치 검증 X. 과대/과소 보고 가능. 새 세션 진입 직후 자체 검증
3. **line 시점 재검증** (이전 세션 답 E):
   - SettlementViewModel.kt = 본 세션이 -113줄 shift 확정 후 §9.2 갱신 ✅
   - DriverSummaryScreen.kt 106/116/119/124/149/156 = 이전 세션 X 건드림, 원본 line 유지 추정. **새 세션 Grep 재검증**
   - DashboardViewModel.kt 2696 = 이전 세션 X 건드림, 원본 line 유지 추정. **새 세션 Grep 재검증**
   - driver_app DriverViewModel.kt 80-81/104/152/210-211 = 이전 세션 ~120줄 감소 (line 1500 + 1496 함수 폐기, 둘 다 line 200 이후). line 80-81/104/152/210-211 = line 200 이전이라 변동 없음 추정. **새 세션 Grep 재검증 권장**
4. **carryOverListener 의식/놓침 분리** (이전 세션 답 F):
   - driver_app DriverViewModel.kt 변수 (line 152) + dispose (line 210-211) = **의식적** (commit 2 자연 통합 자리)
   - call_manager SettlementViewModel.kt (line 82/205/673/1379) = **놓침**. §9.2 갱신으로 commit 1 자리 명시 ✅

---

## 6. 관련 메모

- [[ptt-plan-entry-decision-2026-05-25]] — PTT 진입 결정 본문
- [[settlement-redesign-2026-05-27]] — 정산 재설계 단일 출처
- [[settlement-logic-definitive-2026-05-25]] — 정산 코드 사실 단일 출처
- [[settlement-session-2026-05-27]] — 5/27 세션 흐름
- [[coupon-pivot-2026-05-19]] — 5/19 보류 결정 본문 (본 파일이 *부분 해제*)

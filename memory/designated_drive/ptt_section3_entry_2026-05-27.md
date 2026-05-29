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

### 9.1.2 commit 1 ✅ commit 완료 (2026-05-28)

- **commit hash**: `e7403ded`
- **stat**: 13 files / +152 / -1622 (순감 1470줄)
- **branch**: manager-direct-drive
- **push X** (plan §10 정합 — 4 commit 모두 완성 후 한 묶음 push)
- **무관 누적 stage 제외** (§9.2.2): customer_app_flutter / head_manager_web / firebase.json / functions/package.json / functions/lib/* / call_manager/CallDetectorService.kt / .claude/settings.local.json 모두 unstaged 유지

### 9.1.3 본 commit 잔존 자리 (commit 2 자연 흡수, compile warning 만)

- **SettlementViewModel.kt:740-756** — `driverShare` / `cashReceived` / `driverTotalFare` / `driverDeposit` unused local val (5/28 본 세션 점검 발견). 본 자리 = `processCarryOverOnFinalize` 호출자 폐기 후 forEach 안 *clearDailySettlement(driverId) 호출 자리만 살림*. 컴파일 통과 (warning 만). commit 2 enum + data class 정리 시 자연 흡수 — forEach 자리 통째 단순화 권장 (clearDailySettlement 호출만 살리는 minimal forEach)

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
- `memory/feedback/feedback_self_monitoring_limit.md` (#7 근본 줄기) — ✅ **2026-05-29 승격 완료** (본 세션 #7 재발 = `pointsUsed`→"쿠폰 트랙" 코드 확인 전 단정, §11.2-A. 재발이 승격 근거). name=`self-monitoring-limit`, README 등록 완료.
- 나머지 3개(#1 auto_mode / #2 self_speech / #5·#6 handoff)는 미승격 — 다음 재발/본인 결정 시 처리.

새 세션 진입 첫 turn 본인 결정 받아 처리. *#7이 본 세션 핵심 학습 — 다른 학습 모두 #7의 표면 증상*. → #7 승격됨(2026-05-29).

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

## 10. commit 2 완료 (2026-05-29 세션)

- **commit hash**: `8426984f` (manager-direct-drive, **push X** — 4 commit 묶음 완성 후, plan §10)
- **stat**: 16 files / +113 / -1695 (순감 1582줄, commit 1과 맞먹음)
- **검증 ✅**: 양 앱 main(`assembleDebug`) + test(`compileDebugUnitTestKotlin` + `compileDebugAndroidTestKotlin`) 모두 BUILD SUCCESSFUL. (Divider deprecation warning만, 무해)

### 10.1 폐기/변경 자리
- enum: `DailySettlementStatus` / `CarryOverStatus` / `SettlementFilter` 폐기 (양 앱)
- data class: `DriverCarryOver` / `DriverCarryOverSummary` 폐기
- `DriverDailySettlement` 16→**8필드** (폐기: status + calculatedCarryOver/originalCarryOver/originalTripCount/originalTotalFare/originalRealDeposit + confirmedAt/confirmedBy). ⚠️ **plan §1.2 "6필드" 대비 정정 — `finalDeposit`/`settlementDiff` 유지**. 근거: carryOver 무관 *당일 정산 결과값*이라 Zero-base 원칙 무관 + 화면 표시 보존 + commit 4 재구조 전 의미 단위 유지. settlementDiff = `realDeposit - finalDeposit` (carryOver 성분 제거)
- `DriverDailySettlementSummary`: `hasSubmitted`(submittedAt 문서유무)만, `isConfirmed`/`isRejected` 폐기 (매니저 확인 액션 commit 1 폐기 정합)
- `SettlementCalc` 3종(calculateAdjustedDeposit/RemainingCarryOver/UsedFromCarryOver) + `SettlementCalculator.calculateTodayUnpaidByDriver` 폐기. 유지: calculateOfficeDeposit/DriverShare/RawFinalDeposit/DriverStats/PaymentBreakdown/RealIncome 등
- `DriverViewModel`: `_carryOver`/`_dailySettlementStatus` state + dead `carryOverListener` 변수 폐기 + `submitDailySettlement` 단순 제출 재작성(통합/이월 제거)
- `HistorySettlementScreen`: carryOver UI(수령 다이얼로그/이체 버튼/누적 미수령금 카드/미환급 공제) 전부 폐기 + **`settlementStatus`→`uiState.driverStatus`** (★회귀 수정)
- `SettingsScreen`: `dailySettlementStatus`→`uiState.driverStatus`
- `SettlementViewModel`: dead `_carryOverList`/`_dailySettlementList` 폐기 + `clearAllTrips` forEach 단순화(§9.1.3 unused local val 흡수)
- `DriverSummaryScreen`: 통계 카드만 재작성. `AllTripsScreen`: 미지급 현황 카드/DriverUnpaidSummary/checkBeforeFinalize carryOver·정산확인 부분 폐기
- test 5파일(SettlementCalcTest는 직접, 나머지 4개 sub-agent): 폐기 함수 참조 @Test 케이스만 제거, 살아있는 test 보존

### 10.2 클코 학습 (본 세션)
- **plan 추정(~40자리)보다 컸음** — carryOver "표시/계산" UI가 commit 1에서 안 지워지고 commit 2로 흡수(ptt §9.1.3 예고대로). 실제 14 main파일 + test 5파일, 순감 1582줄
- **dead state 회귀 발견·수정** — `_carryOver`/`_dailySettlementStatus`/`_carryOverList`/`_dailySettlementList`는 commit 1 carryOverListener 폐기로 *set하는 곳이 사라져 항상 초기값*. 호출자 화면(특히 HistorySettlementScreen "퇴근하기" 분기, AllTripsScreen 미지급 현황)이 빈 데이터로 작동 중이던 회귀를 commit 2가 정리. *commit을 나눌 때 한쪽(listener)만 먼저 폐기하면 반대쪽(state 구독 UI)이 dead로 남는 패턴 — 분할 시 양방 동시 점검*

### 10.3 다음 세션 진입 (commit 3 → 4 → push)
- **commit 3** — 외상 자리 폐기 (plan §10 + §7 오염2): call_manager `CallManagerDatabase` v7→v8 (entities에서 `CreditPersonEntity`/`CreditEntryEntity` 제거, `fallbackToDestructiveMigration()` line 34 이미 설정 → 자동 destructive) + `CreditDao` + `SettlementCacheRepository`/`SettlementDao` 외상 함수 + `CreditManagementScreen` + `CreditDialog` + `SettlementDatabase`(dead v1) + `CreditEntity` + driver_app `confirmAndFinalizeTrip` 외상 분기 단순화. ~35자리. **진입 시 Grep 재검증 필수**
  - ★ **범위 확정 (2026-05-29 본인 결정 = 옵션1 전체 폐기)**: plan §10.3이 과소 집계했던 **매니저 per-call 이체/외상 정산 *처리* 흐름도 함께 폐기**. 추가 폐기 대상: `PendingSettlementsScreen`(탭1 대기, 순수 [이체확인]/[외상등록] 처리 화면) + `SettlementTabHost` 탭 **전체/기사별/일일 3개로 축소**(대기·외상 탭 제거) + ViewModel `markTripCredited`/`creditedTripIds`/`fetchPhoneForCall`/`addOrIncrementCredit`/`reduceCredit`/`_creditPersons`/`CreditPerson`/`CreditEntry` + `AllTripsScreen.checkBeforeFinalize` 이체/외상 미처리 경고.
  - **근거**: redesign §2.2(기사 결제수단 4버튼 원클릭으로 일원화 → 매니저 재입력은 이중작업) + §3.1(매니저 정산화면 능동 0) + §6(외상 손님정보 수집 폐기, 본인 기결정). 콜별 결제수단(외상/이체) *조회*는 전체 탭(`AllTripsScreen` 결제수단별 합계 + `TripListTable`)이 이미 커버 → 별도 흡수/보존 불필요 (본인 "최소개입 — 기재되어 확인 가능하면 폐기" 정합).
  - **directRunTrips는 손대지 않음** (§10.4 참조 — commit 3 외상 폐기와 무관).
  - 클코 학습: 초안에서 "조회 전체탭 흡수" + "directRun 합치기 점검" 사족 2건 덧붙였다가 코드 확인·본인 지적으로 둘 다 철회. *scope 밖 작업 덧붙이지 말 것 — 최소개입.*
- **commit 4** — 콜 완료 4버튼 원클릭(`completeRide`) + 일과 끝 정정 모달 + `updateCallPaymentMethod` + firestore.rules + `autoFinalizeSettlementSessions` 본문 단순화 + 메모리 갱신. ~33자리
  - ⚠️ **누락 보강 (commit 2 검토 발견)**: commit 1에서 carryOverListener 폐기 → `_dailySettlementList` set 로직 사라짐 → commit 2에서 dead state 폐기. 결과 **매니저가 기사 제출 일일 정산(finalDeposit/settlementDiff/submittedAt)을 조회하는 기능이 현재 없음**. plan §3.3 "매니저 그날 종료 화면(미납/환급 표시)" 구현하려면 commit 4에서 **dailySettlement 재로드 로직 신규**(`SettlementViewModel`에서 designated_drivers 문서의 dailySettlement 1회 fetch 또는 리스너) + `_dailySettlementList` 복원 + `DriverSummaryScreen`/`AllTripsScreen` 매니저 정산 요약 표시 복원 필요. `DriverDailySettlementSummary`(hasSubmitted getter) 모델은 이 복원 위해 commit 2에서 의도적 유지.
- **push** — 4 commit 모두 완성 후 한 묶음 (plan §10)
- **deploy** — commit 4 후 functions + firestore.rules 1회
- 본 §10 + MEMORY.md 인덱스는 **commit 4에 함께 commit** (메모리 갱신은 commit 4 자리)

### 10.4 directRunTrips(직접운행) 당분간 동결 (2026-05-29 본인 확정)

- **결정**: `directRunTrips`(직접운행) **기능 전반 당분간 동결**. 정산 재설계 트랙뿐 아니라 *어느 트랙에서도* directRun 신규 변경 X. 현행 유지. **재개는 본인 명시적 결정 후만**.
- commit 3 외상/이체 처리 폐기는 directRun과 무관 — directRun 조회/합계 로직(`AllTripsScreen` 직접운행 매출 표시, `applyDirectRunFilter`, `directRunTrips` StateFlow)은 **손대지 않고 그대로 둠**.
- directRun은 PTT 시나리오(`ptt_operation_scenario.md` — 직접운행 9필드 → PTT 발화+결제 1탭)에서 다룰 *후보*였으나, 본인 결정으로 **동결** — PTT 트랙 진입 시에도 directRun 부분은 제외.
- ⚠️ 최초 동결 결정의 정확한 출처는 메모리 미기록(클코 확인 못 함). 본인 2026-05-29 환기·확정으로 박음.

---

## 11. commit 3 완료 (2026-05-29 세션) + commit 4 새 세션 진입 가이드

### 11.1 commit 3 완료

- **commit hash**: `6defedb1` (manager-direct-drive, **push X** — 4 commit 묶음 완성 후, plan §10)
- **stat**: 16 files / +7 / −1059 (순감 1052줄)
- **검증 ✅ (실측, 추정 아님)**: call_manager + driver_app 양 앱 `:app:assembleDebug` + `:app:compileDebugUnitTestKotlin` + `:app:compileDebugAndroidTestKotlin` 모두 **BUILD SUCCESSFUL** (Gradle 8.10 deprecation warning만, 무해). dangling 참조 0건 (Grep 전수 확인).
- **범위**: 2026-05-29 본인 결정 = **옵션1 전체 폐기**(§10.3 ★ 범위 확정). 외상 명단뿐 아니라 매니저 per-call 이체/외상 *처리* 흐름까지.

**삭제 11파일**:
- active 외상 Room: `CreditPersonEntity.kt` / `CreditEntryEntity.kt` / `CreditDao.kt` (data.local)
- dead 체인: `SettlementDatabase.kt`(v1) / `repository/SettlementCacheRepository.kt` / `dao/SettlementDao.kt` / `entity/CreditEntity.kt` / `entity/SettlementEntity.kt` (인스턴스화·getDatabase 외부 호출 0건 실측 확인 후 체인 통째 삭제)
- per-call 처리 UI: `PendingSettlementsScreen.kt`(탭1 대기) / `CreditManagementScreen.kt`(탭4 외상, +CreditDetailDialog+shareCreditDetails) / `CreditDialog.kt`

**편집 5파일**:
- `CallManagerDatabase.kt` — entities에서 CreditPersonEntity/CreditEntryEntity 제거 + `creditDao()` 폐기 + **version 7→8** (`fallbackToDestructiveMigration()` 기설정 → 자동 destructive, 외상 명단 손실 = 본인 "다음 날 새 시작" 정합)
- `SettlementTabHost.kt` — `pages` "전체/기사별/일일" **3개**로 축소(대기·외상 제거) + `when(selected)` 0=AllTrips/1=DriverSummary/2=DailySession 재배치
- `MainActivity.kt` — `_settlementInitialTab.value = 2 → 1` (ACTION_SHOW_SETTLEMENT "기사별" 탭, 재배치 정합)
- `SettlementViewModel.kt` — import 2개 + `creditDao` 필드 + getAllCreditPersons collect 블록 + `CreditEntry`/`CreditPerson` data class + `_creditPersons`/`creditPersons` + `addOrIncrementCredit`/`reduceCredit` + `_creditedTripIds`/`creditedTripIds`/`markTripCredited`/`fetchPhoneForCall` 폐기. (`import kotlinx.coroutines.flow.first`는 line 661에서 여전히 사용 → 유지)
- `AllTripsScreen.kt` — CreditDialog import + `creditedIds`/`showCreditDialog` + `checkBeforeFinalize`(이체/외상 미처리 경고) + `showPreFinalizeWarning`/`preFinalizeWarnings` state + 경고 다이얼로그 블록 폐기 + 업무마감 버튼 onClick 직접 `showFinalizeDialog=true`. **직접운행 매출·통계·미수금(creditSum) 표시는 유지**.

**무변경 (의도)**:
- **driver_app** — `confirmAndFinalizeTrip` 외상 분기(creditAmount set)는 손님정보 수집(CreditDialog)이 아니라 redesign이 *유지*하는 미수금 통계 입력이라 제거 시 통계 깨짐 → 손대지 않음. 결제수단 4버튼 UI 단순화(HomeScreen 812~)는 **commit 4(completeRide) 자리**.
- **directRunTrips** — §10.4 동결. AllTripsScreen 직접운행 매출/`applyDirectRunFilter`/StateFlow 그대로.

**plan 대비 정정 2건 (클코 학습)**:
1. dead 체인이 plan §10.3 명시(`SettlementDatabase`+`CreditEntity`)보다 넓었음 — 같은 죽은 체인 3파일(SettlementCacheRepository/dao.SettlementDao/entity.SettlementEntity) 인스턴스화 0 실측 후 함께 삭제. *진입 전 Grep으로 "체인 전체 생사" 확인이 plan 파일 목록보다 우선*.
2. driver_app 외상 분기 = 통계 입력이라 무변경. *plan 항목을 글자대로 받지 말고 "그 코드가 유지 기능에 쓰이는지" 확인 — 최소개입*(§10.3 클코 학습 정합).

### 11.2 commit 4 새 세션 진입 가이드

**commit 4 = 매니저 정산 조회 복원 + 기사 정정 모달 + rules + cron 단순화 + 메모리 갱신** (plan §10 commit 4 중 #1 제외). 신규 코드 多 → 새 세션 권장(§9.4 #3).
**★ 2026-05-29 본인 결정 = 옵션1**: commit 4 범위에서 **#1(콜 완료 결제 다이얼로그 재작성) 분리**. 아래 §11.2-A 참조. commit 4는 **#2·#3·#5·#6만**.

**작업 자리** (모두 *진입 시 Grep 재검증 필수* — 아래 line/함수는 2026-05-29 시점 추정):
1. ~~**콜 완료 4버튼 원클릭**~~ — **분리·보류** (§11.2-A). commit 4 미포함.
2. **일과 끝 정정 모달 + `updateCallPaymentMethod(callId, newMethod)` 신규** — 기사 정산 화면 콜 행 클릭 → 결제수단 변경 → 통계 자동 재계산 (DriverViewModel 신규 함수 + HistorySettlementScreen UI).
3. **firestore.rules** — 기사 본인 콜 `paymentMethod` update 권한 1줄.
4. **`autoFinalizeSettlementSessions` 본문 단순화** — functions/src/handlers/settlement.ts. ✅ **2026-05-29 Grep/Read 재검증 = 이미 단순(무변경)**: settlement.ts:246~308 본문이 이미 `metadata.isFinalized` 토글 + version/timestamp만, carryOver 자리 **없음**. → commit 4에서 **코드 변경 불필요**(확인만). plan §10 #4는 commit 3 driver 외상분기와 같은 "이미 됨" 패턴.
5. **★ §10.3 누락 보강 — 매니저 dailySettlement 재로드 복원**: commit 1에서 carryOverListener 폐기 → `_dailySettlementList` set 로직 소멸 → commit 2에서 dead state 폐기됨. 결과 **매니저가 기사 제출 일일 정산(finalDeposit/settlementDiff/submittedAt) 조회 기능이 현재 없음**. commit 4에서 `SettlementViewModel`에 designated_drivers 문서 dailySettlement **1회 fetch(또는 리스너)** 신규 + `_dailySettlementList` 복원 + `DriverSummaryScreen`/`AllTripsScreen` 매니저 정산 요약 표시 복원. `DriverDailySettlementSummary`(hasSubmitted getter)는 이 복원 위해 commit 2에서 의도적 유지함.
6. **메모리 갱신** — 본 §11(commit 3 완료) + commit 4 결과 + MEMORY.md 인덱스. **commit 4에 함께 commit**(메모리 갱신은 commit 4 자리, §10.3).

**진입 절차**: ① 본 §11(특히 §11.2-A) + plan §10 + redesign §3.3/§5.2 정독 → ② Grep 재검증(`HistorySettlementScreen` 콜 행/정정 / `dailySettlement` / `_dailySettlementList` / `DriverSummaryScreen`·`AllTripsScreen` 매니저 요약 / `autoFinalizeSettlementSessions`) → ③ plan agent(필요 시) → ④ 본인 승인 → ⑤ 코드 진입 → ⑥ 양 앱 assembleDebug + test 컴파일 + functions `npm run build` → ⑦ 외상·포인트 무관 자리만 선별 stage commit 4.

**commit 4 후**: 4 commit 묶음(1~4) **한 번 push** + functions/firestore.rules **1회 deploy**(commit 4 후) + 단말 install(양평 정산 현재 사용 X라 우선순위 낮음).

### 11.2-A #1 결제 다이얼로그 분리 결정 (2026-05-29 본인 = 옵션1)

- **막힌 자리**: redesign §3.2/§5.1 "4버튼 원클릭(현금/이체/외상/포인트), 1액션" vs §9.4 "paymentMethod 5종 유지 + creditAmount 계산 유지" vs 인계 §11.2(구) "4버튼 + 현금+포인트 자연 유지" — **3개 설계 자리가 서로 충돌**. "원클릭(입력 0)"과 "포인트 결제(금액 입력 필수)"는 본질적 양립 불가.
- **사실 검증 (코드 실측)**: driver_app `HomeScreen.kt` 693~752 = `"앱 회원 리워드 포인트"` 카드 (`customerPointInfo` currentPoints/grade + `rewardPointsToUse` 입력) + `confirmAndFinalizeTrip` `pointsUsed`/`finalFare`. = **기존 손님 적립 포인트 결제 기능** (식당 4중 노드 포인트 적립 + customer_app + functions/points.js 계열). **쿠폰(할인권) 앱과 다른 시스템**. 정산 재설계(PTT §3 = 매니저·기사 정산 흐름) 스코프 *아님*.
- ⚠️ **클코 오류 정정 (2026-05-29, 본인 지적 "쿠폰트랙이 왜 나오지?")**: 최초 보고에서 pointsUsed를 보고 "5/4 P0 포인트 결제 풀 = 쿠폰 트랙"으로 *코드 확인 없이 단정*. 실측 결과 = 손님 리워드 포인트 결제(기존 기능). 포인트 결제 풀(P0)과 쿠폰앱(5/19)은 관련 있어도 같은 트랙 아님. §9.4 #7(추정을 사실로 덮어씀) 재발 — *코드 확인 전 트랙 귀속 단정 금지*.
- **본인 결정(옵션1)**: commit 4에서 **#1 분리**. 근거(정정 후) = ① 정산 재설계 스코프 밖(다른 시스템) ② 기존 손님 포인트 결제 기능 회귀 위험 ③ 문서 충돌(§3.2/§5.1 "4버튼 원클릭 입력0" ↔ §9.4 "5종 유지" + 포인트는 금액 입력 필수). 옵션1 = 손상 0·되돌릴 것 0(보존적) + "최소개입" 정합.
- **재개 조건**: 손님 포인트 결제 UX 재설계는 별도 결정(포인트/식당 트랙 또는 결제 UX 통합 시점). 그 전까지 현행 5버튼 다이얼로그 그대로 유지(creditAmount·pointsUsed 로직 무변경).
- ⚠️ **redesign 문서 정정 필요**(별도): §3.2/§5.1 "4버튼 원클릭" 함의는 §9.4(5종 유지)·손님 포인트 결제와 충돌 → 재설계 전까지 §9.4가 우선. redesign 갱신 시 명시.

### 11.3 클로징 인터뷰 (2026-05-29 본 세션 자체 점검, §9.4 #6·#7)

1. **컴파일** = 추정 아님. 양 앱 BUILD SUCCESSFUL **실측** 확인 후 commit. ✅
2. **line 번호** = 본 §11 자리들은 2026-05-29 working tree 기준 *추정*. 새 세션 진입 직후 **Grep 재검증** 필수(특히 HomeScreen 결제수단 다이얼로그 line).
3. **무관 누적 미터치** = commit 3 stage에 16파일만 선별. `CallDetectorService.kt`/customer_app_flutter/head_manager_web/functions(lib·package) 모두 미커밋 그대로 ✅ (git status 확인).
4. **메모리 미커밋 상태** = 본 파일(ptt §3) + MEMORY.md는 commit 4 자리라 **의도적으로 미커밋**. commit 4 stage 시 함께. commit 4 *전* 다른 commit에 섞지 말 것.
5. **push/deploy 방침** = 4 commit 모두 완성 후 한 묶음 push + commit 4 후 deploy 1회. commit 3 단독 push 금지(plan §10).

### 11.4 commit 4 완료 (2026-05-29 세션) — 본 커밋

- **plan**: `C:\Users\kala1\.claude\plans\woolly-fluttering-feather.md` (승인). 스코프 = #2/#3/#5/#6 (#1 분리·#4 무변경).
- **검증 ✅ (실측)**: call_manager + driver_app `:app:assembleDebug` + `:app:compileDebugUnitTestKotlin` + `:app:compileDebugAndroidTestKotlin` 모두 **BUILD SUCCESSFUL** (Divider deprecation·obsolete options 경고만, 무해).
- **#2 기사 정정 모달 + `updateCallPaymentMethod`** (driver_app):
  - `DriverViewModel`: `TripHistoryItem`에 `callId` 추가(loadTodaySettlement `doc.id` + confirmAndFinalizeTrip `callId` 채움) + 신규 `updateCallPaymentMethod(callId,newMethod,onResult)` — calls doc의 paymentMethod + cashReceived/creditAmount 재계산(현금/외상/이체 3종), 기록 후 `refreshSettlementData()`로 권위 재로드(수동 delta X).
  - `HistorySettlementScreen`: 콜 행 `tripHistoryList` 객체 순회 + 조건부 `clickable` → 정정 AlertDialog(현금/외상/이체 3버튼). **게이트 2중**: 포인트 계열 콜 비활성 + 제출 후(PENDING_CONFIRM) 비활성. Toast import 추가.
- **#3 firestore.rules**: calls update에 기사 본인 COMPLETED 콜 결제필드 정정 OR 절 추가 — `affectedKeys().hasOnly(['paymentMethod','cashReceived','creditAmount','finalFare'])`, status·fare·completedAt 불변(offices L114 화이트리스트 패턴 정합).
- **#5 매니저 dailySettlement 재로드 복원** (call_manager):
  - `SettlementViewModel`: `_dailySettlementList: StateFlow<List<DriverDailySettlementSummary>>` 신규 + import. **기존 `fetchCompletedCalls`의 designated_drivers fetch 재사용**(추가 read 0, 리스너·carryOver 명칭 X) — 각 doc `dailySettlement` → `fromMap` → `submittedAt != null`만 summary로 → submittedAt desc.
  - `DriverSummaryScreen`(ui/settlement/**screen**/): dailySettlementList collect → driverId 매핑 → `DriverDetailCard`에 제출 배지+finalDeposit/realDeposit/settlementDiff(환급/미납)/마감시간 표시 복원.
- **#4**: `autoFinalizeSettlementSessions` 이미 단순(isFinalized 토글만) → **무변경 확정**(commit 4 코드 0).
- **★ 오염/누락 검토 + 결정 A (본인)**: `onCallCompletedUpdateSettlement`(index.ts:4985)는 COMPLETED **전이**에서만 발화 + addCallToSettlementSession callId 중복 스킵 → #2 정정이 settlementSessions·dailySettlement에 전파 안 됨. **결정 A** = 전파 안 함 + 정정 모달 **제출 전 한정**(dailySettlement 항상 fresh 기록). 매니저 라이브 통계(전체/기사별 탭)는 calls 직접이라 정정 반영됨. **함수 변경 0**, deploy = firestore.rules만.
- **클로징 인터뷰**: 컴파일 실측 ✅ / 무관 누적(CallDetectorService·customer_app·head_manager·functions lib·package) 미터치 ✅ / source/ 백업 미터치 ✅(DriverSummaryScreen 실경로 = ui/settlement/screen/).
- **남은 단계**: push(1~4 묶음) + firestore.rules deploy 1회 + (선택)단말 install. **#1 결제 다이얼로그·directRun은 보류 유지**. → push+deploy 완료(commit 4), 단말 검증은 §11.5로 이어짐.

### 11.5 commit 5 — 실납입/환급 개념 폐기 + 단말 검증 발견 DAO 수정 (2026-05-30 세션)

- **본인 결정 (5/30)**: 실납입(actualDeposit)·정정 버튼·환급금/미납금 개념 **전부 폐기** → 기사 정산 화면은 **납입금(net = 사무실 몫 − 외상/이체/포인트, 부호) 한 줄**. 실제 정산은 현장에서 알아서. "최소개입/다음날 새 시작" 귀결. (commit 4 단말 검증 중 실납입이 결제수단 변경을 안 추종하고 총환급금이 엉뚱하던 것 → 이월 시절 잔재 로직 발견 → 개념 폐기로 정리.)
- **방식 = Option B**(필드 유지·값 단순화): `DriverDailySettlement.realDeposit/settlementDiff` 필드는 *유지*(Firestore 기존 문서 호환 + functions 무변경), 의미만 죽임 — `realDeposit=finalDeposit(net)`, `settlementDiff=0`. UX에서만 개념 제거.
- **코드 5파일**: driver `HistorySettlementScreen`(실납입 카드/정정 다이얼로그/환급·미납 행 폐기, 납입금 net 부호 + 상단 라벨 "납입금"→"사무실 몫") + `DriverViewModel.submitDailySettlement`(realDeposit 인자 제거, net 기록, settlementDiff=0) + `SettingsScreen`(실납입→납입금 라벨) / manager `DriverSummaryScreen`(#5 실납입·환급 제거 → 납입금 net 한 줄) + `MyFirebaseMessagingService`(실납입→납입금 라벨).
- **★ 단말 검증 중 발견·수정 — 매니저가 per-call 정정 반영 안 함**: `SettlementDao.insertAll`이 `OnConflictStrategy.IGNORE`(다른 DAO는 REPLACE)라, 기사 정정(콜 doc MODIFIED)을 매니저 `callsListener`가 잡아도 Room 교체가 무시됨(+ .get 재조회도 `existsById==0` 신규-only). → **IGNORE→REPLACE**(SettlementDao.kt L11·L14). 이제 기존 callsListener MODIFIED → Room REPLACE → 매니저 실시간 반영.
  - 비용 논의(본인 제기): 리스너는 정산 화면 열림 시에만·매니저 1대·기존 자산 → 추가 비용 0. 과거 $414/월은 기사 per-기사 상시 리스너(다른 성격). "리스너 0 전면 전환"은 다수 사무실 보급 시점 별도 트랙. → **A(REPLACE 1줄) 권장 채택**.
- **검증 ✅ (실측)**: 양 앱 main+test BUILD SUCCESSFUL. 단말 — 기사(R3CT80K78NP) 정정 시 납입금 net 즉시 추종(실납입/환급 사라짐) + 매니저(R3CR312MB1L) 정산화면 현금↔외상 **실시간 교체** 확인.
- **테스트 무변경**: `calculateRawFinalDeposit` 유지(net 계산)라 SettlementCalcTest/DriverSettlementConsistencyTest 그대로. 폐기 심볼 단언 0(실측).
- **deploy 불필요**(functions·rules·Room 스키마 무변경 — OnConflictStrategy는 스키마 무관). **push: commit 5 단독**(commit 1~4는 이미 push). #1 다이얼로그·directRun·매니저 finalize(큐)는 보류 유지.

### 11.6 보류 큐 — 상세 (2026-05-30 인계, 다음 세션 후보)

1. **★ 매니저 수동 업무마감 = 즉시 봉인(isFinalized)** — 본인 5/30 제기 ("당연히 업무마감 시 마감이 진행돼야 하지 않나"). 검증 우선으로 **보류**, 다음 세션 결정/진입 대기.
   - **현재 동작(실측)**: 매니저 [업무마감]→`SettlementViewModel.clearAllTrips`(612~679)는 ① 로컬 Room 세션 아카이브+markTripsFinalized ② office `settlementLastCleared`=now(뷰 컷오프) ③ 기사 dailySettlement 삭제 — **그러나 Firestore `settlementSession.isFinalized`는 안 건드림**. 클라우드 세션 봉인은 10:10 cron(`autoFinalizeSettlementSessions`)만. 화면 문구 "오전 10시 10분에 자동 마감됩니다"(`AllTripsScreen.kt:192`).
   - **본인 의도**: 10:10 cron = *미마감 fallback*. 수동 마감 시엔 *그 자리에서 봉인*돼야.
   - **권장 수정(소규모, call_manager만)**: `clearAllTrips`에 현재 근무일 `settlementSessions/{getTodaySessionDate()}.metadata.isFinalized=true` set(**merge** — 콜 0건 시 doc 부재 대비) 추가 + 문구 "마감되었습니다". cron은 fallback 유지. functions·rules 무관, **deploy 불필요**. (commit 1에서 폐기한 `finalizeSettlementSession`의 최소 복원 성격 — 단 "매니저 능동 0" 원칙 일부 되돌림 = 본인 명시 의도.)
2. **#1 콜 완료 결제 다이얼로그**(`SettlementSummaryPopup`, 5버튼/포인트 결제) — 손님 적립 포인트 결제 트랙과 함께 별도 결정. 보류(§11.2-A).
3. **directRun**(§10.4 동결) / **리스너 0 전면 전환**(비용 — 다수 사무실 보급 시점).
4. **cosmetic(미노출, 다음 functions deploy 때)**: `onDriverSettlementSubmitted`(index.ts:5386 로그·5415 notifBody) "실납입"→"납입금"(FCM data-only·앱 자체 빌드라 사용자 안 보임) + `DriverViewModel.kt:1481` 함수 doc 주석 stale.
5. **redesign 문서 정정**: §3.2/§5.1 "4버튼 원클릭" ↔ §9.4 "5종 유지" 충돌 — 재개 전까지 §9.4 우선(§11.2-A).
6. **§9.5 나머지 피드백 승격**(#1 auto_mode / #2 self_speech / #5·#6 handoff) — 재발/본인 결정 시.

---

## 6. 관련 메모

- [[ptt-plan-entry-decision-2026-05-25]] — PTT 진입 결정 본문
- [[settlement-redesign-2026-05-27]] — 정산 재설계 단일 출처
- [[settlement-logic-definitive-2026-05-25]] — 정산 코드 사실 단일 출처
- [[settlement-session-2026-05-27]] — 5/27 세션 흐름
- [[coupon-pivot-2026-05-19]] — 5/19 보류 결정 본문 (본 파일이 *부분 해제*)

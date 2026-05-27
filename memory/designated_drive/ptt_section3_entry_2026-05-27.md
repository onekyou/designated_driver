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

## 6. 관련 메모

- [[ptt-plan-entry-decision-2026-05-25]] — PTT 진입 결정 본문
- [[settlement-redesign-2026-05-27]] — 정산 재설계 단일 출처
- [[settlement-logic-definitive-2026-05-25]] — 정산 코드 사실 단일 출처
- [[settlement-session-2026-05-27]] — 5/27 세션 흐름
- [[coupon-pivot-2026-05-19]] — 5/19 보류 결정 본문 (본 파일이 *부분 해제*)

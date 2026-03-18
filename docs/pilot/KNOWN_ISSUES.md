# 파일럿 알려진 이슈

> 회귀 시뮬레이션(2026-03-18)에서 발견. 파일럿 진행에 영향 없음.

---

## #1. isIntegration=true 경로 _todaySettlement 중복합산

### 심각도: P3 (파일럿 후 수정)

### 발견
- 발견일: 2026-03-18
- 발견자: drv-a (회귀 시뮬레이션 Day B)

### 재현 조건
```
1. 기사가 업무마감 제출 (submitDailySettlement) → PENDING_CONFIRM
2. 매니저가 확인/거절하지 않은 상태 유지
3. 같은 기사에게 새 콜 배차 → 추가 운행
4. 기사가 재제출 (submitDailySettlement)
→ isIntegration=true → mergedTripCount = 1차 + (1차+2차) = 중복
```

### 원인
`DriverViewModel.kt` L1381~1383:
```kotlin
mergedTripCount = prevSettlement.tripCount + settlement.tripCount
mergedTotalFare = prevSettlement.totalFare + settlement.totalFare
```
- `prevSettlement`: Firestore dailySettlement (1차 제출분, 8콜)
- `settlement`: `_todaySettlement` (1차 8콜 + 2차 6콜 = 14콜 누적)
- 결과: 8 + 14 = 22콜 (실제 14콜)

### 근본 원인
`_todaySettlement`는 `clearSettlement()`에서만 초기화되고, `submitDailySettlement`에서는 초기화하지 않음. `settlementLastCleared` 이후 모든 콜이 누적되므로, 1차 제출 후에도 1차 콜이 포함된 채 2차 콜이 추가됨.

### 실제 발생 가능성: 극히 낮음
- 양평 사무실 기준: 관리자 1명이 배차 + 정산 확인 모두 처리
- 기사 마감 제출 = "오늘 끝" → 매니저가 해당 기사에게 새 콜 배차할 이유 없음
- PENDING_CONFIRM 상태에서 새 콜이 배차되는 상황 자체가 비현실적
- 거절(REJECTED) 후 재제출은 isIntegration=false이므로 영향 없음

### 수정 방안 (파일럿 이후)
**옵션 A**: `submitDailySettlement` 성공 후 `_todaySettlement` 초기화
```kotlin
// L1430 이후 추가
_todaySettlement.value = TodaySettlement()
```
- 장점: 간단
- 단점: 재제출 시 _todaySettlement가 비어있으므로 loadTodaySettlement 재호출 필요

**옵션 B**: isIntegration=true일 때 `settlement`에서 prevSettlement 분량을 차감
```kotlin
mergedTripCount = prevSettlement.tripCount + (settlement.tripCount - prevSettlement.tripCount)
```
- 장점: _todaySettlement 초기화 불필요
- 단점: settlement.tripCount < prevSettlement.tripCount인 경우 음수 가능 (앱 재시작 후 콜 유실 시)

**옵션 C**: isIntegration=true일 때 `_todaySettlement`만 사용 (prevSettlement 무시)
```kotlin
mergedTripCount = settlement.tripCount  // _todaySettlement가 이미 전체를 포함하므로
```
- 장점: 가장 안전, _todaySettlement가 calls 기반 재계산이므로 항상 정확
- 단점: isIntegration 분기의 존재 의미가 없어짐

### 수정 시점
- 사무실 확장 시 (매니저 복수, 자동배차 도입 시)
- 또는 파일럿 1주차 이후 안정화 시점

---

## #2. CLAUDE.md carryOver 공식 오류

### 심각도: 문서

### 내용
CLAUDE.md의 정산 공식 섹션:
```
carryOver = originalCarryOver - officeDeposit + realDeposit  ← 오류
```
코드 실제 공식 (`DriverViewModel.kt` L1402):
```
carryOver = originalCarryOver - finalDeposit + realDeposit  ← 정확
```
여기서 `finalDeposit = officeDeposit - totalCredit` (이체/포인트 차감 후)

### 영향
- 코드 동작에는 영향 없음 (문서만 부정확)
- 시나리오 설계 시 혼동 가능

### 조치
CLAUDE.md 정산 공식 섹션 수정 완료 (2026-03-18) — finalDeposit 정의 + totalCredit + isIntegration 조건 추가

---

## #3. isIntegration 조건: PENDING_CONFIRM에서만 동작

### 심각도: 문서

### 내용
`DriverViewModel.kt` L1366:
```kotlin
val isIntegration = prevSettlement.status == DailySettlementStatus.PENDING_CONFIRM
```
- CONFIRMED 상태에서는 isIntegration=false → 통합 제출 불가
- REJECTED 상태에서도 isIntegration=false

### 실제 동작
CONFIRMED 후 추가 운행 시 `_todaySettlement`가 Firestore calls 기반으로 전체 콜을 재계산하므로, isIntegration=false여도 정산 결과는 정확함.

### 조치
CLAUDE.md에 isIntegration 조건 명시 완료 (2026-03-18)

---

## #4. ~~recalculateTotals()에서 포인트/외상 미분류~~ — 이슈 아님 (삭제)

CF `settlement.ts`의 `totalCash`/`totalCard` 필드에 포인트/외상이 미분류되지만,
콜매니저 UI(`DriverSummaryScreen.kt`)에서는 `totalCash`/`totalCard`를 **사용하지 않음**.
UI는 `totalFare`, `totalCredit`(SettlementCalculator 기반), `deposit` 필드만 사용.
`totalCash`/`totalCard`는 Firestore `settlementSessions` 메타데이터일 뿐 → **영향 없음**.

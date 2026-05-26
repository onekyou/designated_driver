# 정산 재설계 — 본인 결정 + 강제 게이트 (2026-05-27)

> **목적**: 정산 *재설계 단일 출처*. 본인 결정 누적 + Zero-base 모델 + 강제 게이트 인벤토리.
>
> **자리 분리**: 정산 *코드 사실*은 `settlement_logic_definitive_2026-05-25.md` (§1~14). 본 파일은 *설계*만. 본 세션 흐름 인계는 `settlement_session_2026-05-27.md`.
>
> **단일 출처 정합**: 본인 결정 변경 시 본 파일 갱신. 코드 변경은 definitive 갱신. 두 자리 분리.

---

## 1. 본인 두 원칙 (모든 결정의 상위)

1. **최소 개입** — 앱이 매니저·기사 운영에 끼어드는 액션 최소화. 매니저는 *보기만*, 입력은 본인이.
2. **다음 날 새로 시작** — 매일이 *독립 영수증*. 어제 데이터가 오늘 정산에 *1원도 영향 X*. 잔액 누적 변수 자체 코드에서 폐기.

---

## 2. 본인 결정 (Q1~Q3 + Q2-2)

### 2.1 Q1 — 영업일 시간 ✅ **오전 10시**

- 현재 코드 (5/25 master 헤드): 새벽 06:00 기준 (`calculateWorkDate < 6`, `autoFinalize cron "10 6 * * *"`)
- 본인 결정 (5/27): **10시 기준으로 변경**
- 코드 변경 영향 자리:
  - `calculateWorkDate` (settlement.ts:60 + SettlementViewModel.kt:383 + DriverViewModel.kt:1658): `< 6` → `< 10`
  - `autoFinalizeSettlements` cron (index.ts:5010): `"10 6 * * *"` → `"10 10 * * *"` (매일 10:10 KST)
  - §4 영업일 종료 게이트(신규)의 컷오프도 10시

### 2.2 Q2 — 강제 게이트 설계 ✅ **모든 실수 패턴 차단이 주 목적**

본인 발화: "다양한 사용자의 실수패턴을 막는게 주 목적이야. 모든 경우의 수를 차단할수 있는"

- 원인 특정(5/7 incident)은 *부차*, *모든 패턴 차단*이 주
- 실수 패턴 12개 × 게이트 매핑 → §4 인벤토리에서 재정리

### 2.3 Q2-2 — 이체·수령 절차 폐기 ✅

본인 발화: "이체하기와 이체확인도 불필요한 간섭같아"

폐기 대상:
- 매니저 액션: `transferCarryOver` (SettlementViewModel.kt:1586) + `cancelTransfer` (L1713)
- 기사 액션: `confirmReceiveCarryOver` (DriverViewModel.kt:1558)
- 상태 머신: `carryOver.status` 전체 (PENDING / TRANSFERRED / SETTLED)
- UI: 매니저 DriverSummaryScreen "이체하기" 버튼 + 기사 HistorySettlementScreen "수령완료" 버튼
- FCM: `CARRYOVER_TRANSFERRED` 알림 채널

송금 절차는 *코드 밖*으로 — 현장에서 매니저↔기사 직접 처리. 앱은 *상태 기록 0, 절차 추적 0*.

### 2.4 Q3 — 그날 끝 Zero-base 모델 ✅

본인 발화: "다음날부터는 새로 시작하게 해야해"

설계:
- 매일 영업이 *그날 한 장의 영수증*으로 종결
- 영업일 종료(오전 10시) 지나면 어제 dailySettlement는 *읽기 전용 히스토리*로 archive
- **carryOver 변수 자체 코드에서 완전 제거** (필드·리스너·계산함수 전부)
- 미납/환급 숫자는 *그날 보고서*에만, *내일과 연결 X*
- 현장 청산은 매니저↔기사 본인들이 알아서, 앱은 *옆에서 구경만*

폐기되는 자리 (Q2-2 위 + 추가):
- `DriverCarryOver` data class + Firestore 필드
- `carryOverListener` (call_manager + driver_app 양쪽)
- `processCarryOverOnFinalize` (SettlementViewModel.kt:1759)
- `cleanup banner` (DashboardViewModel `f46ff609`) — 강제 게이트로 대체
- 보조 함수 3종: `calculateAdjustedDeposit` / `calculateRemainingCarryOver` / `calculateUsedFromCarryOver` (SettlementCalc.kt)
- `DriverDailySettlement` 필드 5종: `calculatedCarryOver` / `originalCarryOver` / `originalTripCount` / `originalTotalFare` / `originalRealDeposit`
- 공식: `originalCarryOver - finalDeposit + realDeposit` 자체

---

## 3. 정산 화면 새 구조 (본인 검토용 모형)

**기사 그날 마감 화면**:
```
오늘 운행: 8건
총 운행료: 240,000원
사무실 몫: 144,000원 (60%)
내가 가져간 돈: 96,000원
사무실에 입금할 돈: 60,000원 (현금 콜 - 외상/이체/포인트)
실제 입금: [60,000원 입력]
→ [오늘 마감] 버튼
```

**매니저 그날 종료 화면**:
```
오늘 기사 4명 마감 완료
미납 발생: 김기사 5,000원 (현장 처리 필요)
환급 발생: 없음
→ [오늘 영업 종료] 버튼
```

→ 정산 = ① 합계 자동 계산 + ② 기사 본인 입금 입력 1줄 + ③ 외상 명단 별도 추적 (CreditManagementScreen 유지)
→ 일일 운행 내역과의 차이: *결제수단별 합계 산출 + 기사 입금 확인 + 외상 별도 명단*

---

## 4. 강제 게이트 인벤토리 (실수 패턴 12종 차단)

본인 원칙: *모든 경우의 수 차단*. 단 *최소 개입*. 두 원칙 사이에서 게이트 설계.

### 4.1 게이트 분류

- **🟢 이미 작동 중** (코드에 일부 또는 전부 자리 있음, 보강만 필요)
- **🟡 부분 작동** (한쪽만 작동, 다른 쪽 보강 필요)
- **🔴 신규** (코드 자리 없음, 완전히 새로 만들어야 함)

### 4.2 실수 패턴 × 게이트 매핑

| # | 실수 패턴 | 게이트 | 분류 | 코드 자리 |
|---|----------|--------|------|----------|
| 1 | 기사가 "업무마감" 안 누르고 앱 닫음 | 미정산 콜 1건 이상 시 → 앱 종료/logout 버튼 disable | 🔴 신규 | logout 함수에 가드 추가 필요 |
| 2 | 기사가 logout 후 종료 | 동일 (logout 버튼 disable) | 🔴 신규 | LoginRepository.logout() 진입 차단 |
| 3 | 기사가 다음날 진입 시 어제 마감 미완료 | 앱 시작 시점 게이트 → 어제 정산 화면 강제 | 🟡 부분 작동 | driver.status=PENDING_CONFIRM이면 배차는 차단 (L1685), 단 앱 진입 자체는 가능. *어제 미마감 detect → 강제 화면* 신규 필요 |
| 4 | 매니저가 "정산확인" 안 누름 | 매니저 앱 진입 시 미확인 기사 N명 *모달*화 | 🟡 부분 작동 | 5/10 cleanup banner (commit `f46ff609`) 작동 중, *닫기 가능*이라 효과 약함. 모달화 + 닫기 X 보강 필요 |
| 5 | 매니저가 "일괄 마감" 안 누름 | 영업일 종료 시점(10시) + 미마감 세션 → 매니저 앱 *전체 화면 잠금* | 🔴 신규 | autoFinalize cron은 *세션 isFinalized만* 갱신, 매니저 액션 강제 자리 없음 |
| 6 | 매니저 "이체하기" 누른 뒤 실제 송금 안 함 | **§2.3로 자연 해소** (이체 절차 자체 폐기) | ✅ 해소 | transferCarryOver 폐기 |
| 7 | 기사 "수령완료" 안 누름 | **§2.3로 자연 해소** (수령 절차 자체 폐기) | ✅ 해소 | confirmReceiveCarryOver 폐기 |
| 8 | 영업일 자정 넘기면서 어제·오늘 콜 섞임 | calculateWorkDate 10시 기준 + isFinalized 가드 | 🟢 이미 작동 | settlement.ts:60 + addCallToSettlementSession `isFinalized` 가드 |
| 9 | 기사 1명만 마감, 다른 기사 미마감 | 매니저 일괄 마감 게이트 (#5)에서 *전체 기사 일괄 처리* | 🟡 #5 신규에 묶임 | clearAllTrips + processCarryOverOnFinalize 자리 활용 |
| 10 | 매니저 잘못 confirm 후 정정 필요 | rejectDailySettlement 유지 + 매니저 "정정" 버튼 | 🟢 이미 작동 | rejectDailySettlement (SettlementViewModel.kt:2076) |
| 11 | autoFinalize cron 실패 (서버 장애) | 매니저 앱 시작 시 *어제 isFinalized=false 감지* → 수동 마감 강제 (#5 자리 활용) | 🟡 부분 작동 | autoFinalize cron 있음, 단 *클라이언트 감지 게이트 없음*. #5 신규에 묶임 |
| 12 | 기사 본인 정산 수치 *조작* | 매니저측 *원본 콜 합* 재검증 | 🔴 신규 | confirmDailySettlement는 *기사 계산값 그대로 사용* (SettlementViewModel.kt:1870 추정). 본인 결정 대기 (§5 보류 자리) |

### 4.3 작업 부피 요약

| 분류 | 패턴 # | 작업 부피 |
|------|--------|----------|
| ✅ 자연 해소 | 6, 7 | 0 (폐기 작업에 묶임) |
| 🟢 이미 작동 (그대로 유지) | 8, 10 | 0 |
| 🟡 부분 작동 (보강) | 3, 4, 11 | 중간 (기존 자리 활용) |
| 🔴 신규 | 1, 2, 5, 9, 12 | 큼 (새 진입 차단 로직) |

---

## 5. 강제 게이트 본인 결정 (2026-05-27 확정)

### 5.1 #4 모달화 강도 ✅ **옵션 A + (a) + (b)**

본인 발화: *"정산확인은 최소개입과 상관없잖아. 정산되지 않은 기사를 대충이라도 정산확인을 해야 앱진입하게 해야 할것 같은데. 최소한 사무실을 운영한다면 이전 정산을 놓쳤는데 그걸 또 그냥 퉁친다고?"*

→ **닫기 X 모달**. 미확인 기사 정산 다 처리할 때까지 *콜 화면 포함 다른 모든 화면 진입 X*.

모달 안 액션:
- **(a) 각 기사 [정산확인] 버튼 N개** — 요약 보고 개별 확인
- **(b) 각 기사 [개별 검토] 버튼 N개** — 정산 화면 진입 → 콜 단위 검증 후 확인
- ❌ **[전체 확인] 1버튼은 제거** ("대충 퉁치기" 방지)

학습 — *최소 개입 ≠ 매니저 필수 책임 면제*. 최소 개입 = *불필요한 추적·확인 강제* 줄이기. 정산 확인은 *매니저 본질 책임*이라 강제 정합.

### 5.2 #5 영업일 종료 강도 ✅ **옵션 X1 (10시 즉시 잠금, 알림 X)**

본인 발화: *"카운트다운 불필요"* + *"보통 새벽 2-4시면 마감해"* (영업이 6-8시간 전에 끝남, grace 불필요)

- 10시 정각 도달 + 어제 미마감 세션 존재 시 → 매니저 앱 *어제 마감 화면 전용*
- 콜 화면 포함 다른 모든 진입 X. 매니저는 일괄 마감만 진행 가능
- 사전 알림 없음 (매니저 직무는 알아서)

### 5.3 #12 기사 수치 조작 검증 ✅ **폐기**

본인 발화: *"불일치 확률은 없는걸로 아는데 실제 코드 확인해봐"*

코드 정독 결과 (DriverViewModel + HistorySettlementScreen):
- `_todaySettlement.value` = *서버 calls 컬렉션 기반 자동 계산* (refreshTodaySettlement L1820-1850)
- `submitDailySettlement` 받는 파라미터 = `realDeposit: Int` *1개*
- 나머지 (totalFare, finalDeposit, totalCredit, ...) 모두 *서버 자동 계산*, 기사 입력 X
- 기사가 *조작 가능* 자리 = `actualDeposit` (실납입) 1개뿐
- 단 실납입은 *조작값이 아니라 정상 변동값* (실제 매니저에 준 돈, adjustedDeposit과 다를 수 있는 게 정상)

→ 코드 차원 *자동 검증 자리 자체가 없음*. 매니저 확인 = *물리적 검증* (기사가 가져온 현금 vs 입력값). 클코가 추측 패턴으로 박은 자리.

### 5.4 §11.5 "현금+포인트" 코드 버그 ✅ **폐기**

본인 발화: *"실제코드 확인해봐"*

코드 정독 결과 (SettlementCalculator.kt + settlement.ts):
- **(1) calculateCreditForTrip** (L18-27) — 외상 계산: `cashAmount ?: 0` → fare-cash
- **(2) calculatePaymentBreakdown.pointSum** (L112-115) — 외상 통계: `cashAmount ?: 0` → fare-cash. *(1)과 동일 계산, 로컬 변수 이름만 다름* (cash vs cashReceived)
- **(3) recalculateTotals** (settlement.ts L222-224) — *현금 합계 totalCash 집계*, 외상 X. (1)/(2)와 *다른 자리*

명문화 §11.5 분석 **잘못됨**: 변수 이름 다름을 보고 *불일치라고 분류*. cashAmount = null vs 0 케이스도 *둘 다 fare 전체 처리*로 결과 동일. 운영 영향 0.

### 5.5 클코 학습 (본 세션 추측 패턴 적발 2건)

- **#12 + §11.5 모두 클코 추측 패턴**. 5/25 §피드백 "코드 전수 확인 → 명문화" 위반 사례
- 본인이 *코드 확인해봐* 직접 지적 → 폐기. 5/25 §피드백 실효성 입증
- 다음 명문화 시: *변수 이름 다름*은 *불일치 아님*. 결과 동일 여부로 판단. 추측 X

---

## 6. 결정 보류 자리

- **5/7 incident 원인 특정** — 본인 결정: *원인 특정은 부차, 모든 패턴 차단이 주 목적*. 별도 추적 X.
- **영업일 시간 변경 이력 git blame** — 미확인 유지 (10시로 변경되므로 무의미).

---

## 7. 5/7 incident 원인 후보 (역사 자료, definitive §10 이전)

본인 결정 (5/27): 원인 특정은 부차. 본 절은 *역사 자료*로 보존. 코드 자리 인용은 PTT plan §3 진입 시 게이트 설계 참고용.

본인 발화 (2026-05-25): "마감하지 않고 퇴근 → 다음 콜 진행하면 모든 게 꼬여."

| # | 잔존 상태 | 증상 | 5/27 게이트 대응 (§4) |
|---|----------|------|---------------------|
| 10.1 | `dailySettlement.status = WORKING` | 기사 "업무마감" 안 누름 → 다음날 콜과 섞임 | #1·#2 신규 게이트 |
| 10.2 | `dailySettlement.status = PENDING_CONFIRM` | 매니저 confirm 안 함 → 기사 퇴근 불가 + cleanup banner | #4 모달화 (옵션 A) |
| 10.3 | `carryOver.status = TRANSFERRED` | 매니저 이체, 기사 수령 안 함 | §2.3 이체·수령 폐기로 자연 해소 |
| 10.4 | `settlementSession.isFinalized=false` | cron 어제만 처리, 더 이전 영구 잔존 | #5·#11 게이트 |
| 10.5 | `settlementLastCleared` 갱신 누락 | `clearAllTrips()` 안 호출 → 어제 콜 포함 → 집계 꼬임 | #5 매니저 일괄 마감 강제 |
| 10.6 | `processCarryOverOnFinalize` ↔ `confirmDailySettlement` 중복 | carryOver 중복 계산 | §2.4 carryOver 변수 자체 폐기로 자연 해소 |

→ 6개 모두 §4 강제 게이트 인벤토리 또는 §2 폐기 결정으로 해소.

---

## 8. 알려진 잠재 문제 (definitive §11 이전)

settlement_analysis.md §7 (2026-02-24) 기반 + 5/25 갱신 + 5/27 본인 결정 반영.

### 8.1 processCarryOverOnFinalize vs confirmDailySettlement 중복 계산
- 현 가드: `processCarryOverOnFinalize`에서 CONFIRMED/REJECTED 상태 skip + PENDING_CONFIRM이면 calculatedCarryOver 그대로 사용 → 일관됨
- WORKING 상태에서는 *변경 없음*으로 가드 (currentBalance 유지)
- 5/25 가드 추가됨, 위험 *낮음*
- **§2.4 carryOver 변수 자체 폐기로 자연 해소**

### 8.2 transferCarryOver 동기화 지연
- Firestore set + merge → 로컬 즉시 업데이트 + FCM
- 리스너 1~2초 후 재수신 → 충돌 없음
- 위험 *낮음*
- **§2.3 이체 절차 폐기로 자연 해소**

### 8.3 "이체" 데이터 명명 vs 운영 모델
- `creditAmount`는 *기사 입장* 통합 변수 (현금 외 모두). "이체"라는 단어가 *기사 계좌 이체*로 오독될 수 있음
- 본인 운영은 *손님 → 사무실*. 코드 정합. 단 변수명 혼란 잠재.

### 8.4 매니저측 calculatedCarryOver 검증 부재
- `confirmDailySettlement`에서 `newBalance = dailySettlement.calculatedCarryOver` 그대로 사용 (검증 X)
- 5/27 §5.3 #12 검증: 코드 차원 *자동 검증 자리 자체가 없음*. 기사 입력값(`realDeposit`)은 *현금 차액 정상 변동값*. 매니저 확인 = 물리적 검증.
- **본 자리 폐기 확정** (§5.3)

### 8.5 "현금+포인트" 계산 로직 (5/27 폐기)
- 명문화 §11.5 원본 주장: 3곳 변수 참조 불일치
- 5/27 코드 정독 결과: 변수 이름만 다름, 결과 동일. **잘못 분석 → 폐기 확정** (§5.4)

### 8.6 마감 누락 강제 게이트 부재 (5/25 본인 지적)
- WORKING / PENDING_CONFIRM / TRANSFERRED 잔존에 *물리적 진입 차단 X*
- 5/7 incident 근본 원인
- **§4 강제 게이트 인벤토리로 해소**

---

## 9. PTT plan §3과의 연결 (definitive §12 이전)

PTT plan `harmonic-sparking-hedgehog.md` §3 본인 결정 (2026-05-25):
1. 이월 폐기 (carryOver 변수 영구 제거) → 본 파일 §2.4 (Zero-base 모델)
2. 모든 금액 *그날 정산* 원칙 → 본 파일 §3 (정산 화면 새 구조)
3. 마감 누락 시 *프로그램 사용 차단* (강제 게이트) → 본 파일 §4 (강제 게이트 인벤토리) + §5 (본인 결정 4건)
4. 매니저 + 기사 *서로 컨펌* 모델 → 본 파일 §5.1 #4 (모달화 + 개별 정산확인)

### 9.1 폐기 대상 (PTT §3 진입 시 코드에서 제거)
- `DriverCarryOver` data class + Firestore 필드
- `carryOverListener` (call_manager + driver_app)
- `transferCarryOver` / `cancelTransfer` / `confirmReceiveCarryOver`
- `processCarryOverOnFinalize`
- `cleanup banner` (DashboardViewModel `f46ff609`) — §5.1 모달화로 대체
- `calculateAdjustedDeposit` / `calculateRemainingCarryOver` / `calculateUsedFromCarryOver`
- `DriverDailySettlement`의 `calculatedCarryOver` / `originalCarryOver` / `originalTripCount` / `originalTotalFare` / `originalRealDeposit` 필드
- `originalCarryOver - finalDeposit + realDeposit` 공식 자체

### 9.2 변경 대상
- `dailySettlement.status` 4상태 → 단순화 (예: SETTLED만 + 일일 마감 플래그)
- `confirmDailySettlement` — 단순 *영업 종료 확인*으로 축소 또는 폐기
- `calculateRealIncome` — *포인트 차감*은 유지 (사무실 실손 모델 정합), 단 이월 변수 없이 *그날 단위*만
- `calculateWorkDate` — *< 10* (§2.1 영업일 10시 결정)
- `autoFinalizeSettlements` cron — `"10 10 * * *"` (§2.1)

### 9.3 신규 자리 (강제 게이트 모듈, §7 코드 모듈 분리 원칙 정합)
- 앱 시작 시 어제 마감 여부 게이트 (driver_app + call_manager)
- 영업일 종료 선언 버튼 강제 (#5 게이트, X1 즉시 잠금)
- 미확인 기사 모달화 (#4 게이트, 옵션 A 닫기 X)
- logout 가드 (#1·#2 게이트)
- 외상 채권 테이블 별도 트랙 (선택 — `CreditManagementScreen` 활용)

### 9.4 유지 대상
- `paymentMethod` 5종 (카드 미사용)
- `creditAmount` 계산 (기사 입장 통합 변수)
- `calculateOfficeDeposit` / `calculateDriverShare` 기본 공식
- `settlementSessions/{date}` 구조 (calls 배열 + totals + metadata) — 집계 보존용
- `onCallCompletedUpdateSettlement` 트리거 (콜 단위 즉시 추가)
- `CreditManagementScreen` 외상 관리 (매니저 수금 책임)
- 분석/세금 신고용 데이터 보존

---

## 10. 코드 모듈 분리 원칙 (구현 시)

본인 SRP 통찰 (5/27 "기본적 정산로직만 남겨두고 차단기능은 분리해야하지 않을까?")에 따라:

- **정산 로직 모듈**: `SettlementCalculator` / `SettlementCalc` / `settlement.ts` (그대로 유지, *단순화만*)
- **강제 게이트 모듈**: *별도 모듈*로 신규
  - 예: `EnforcementGate.kt` (인터페이스) + `LogoutGuard.kt` + `DailyCloseGate.kt` + `ManagerCloseGate.kt`
  - 정산 로직을 *읽기만* 하고 *흐름 차단*만 담당. 정산 데이터 *수정 X*

이 분리는 정산 로직 단순화 PR / 게이트 신규 PR을 *서로 독립*으로 만듦. 한 트랙 수정이 다른 트랙 깨뜨릴 위험 0.

구체 구조(인터페이스 자리, 구현체 이름·패키지)는 *PTT plan §3 실제 코드 진입 시점*에 plan agent로 설계.

---

## 11. 이 재설계의 사용법

- **본인 결정 변경 시**: 본 파일 갱신. definitive 갱신 X (사실은 그대로)
- **코드 변경 시**: definitive 갱신. 본 파일 갱신 X (설계는 그대로)
- **PTT plan §3 진입 시**: 본 파일 §2 + §4 + §5 + §9 + §10을 *작업 매트릭스*로 사용
- **단일 출처 유지**: 결정 사항은 본 파일에만, 정산 코드 사실은 definitive에만, 세션 흐름은 session 파일에만

# 정산 재설계 세션 인계 (2026-05-27)

> **진입 경위**: 컴퓨터 블랙아웃 후 마지막 세션 복원으로 시작 (5/25 19:55 db9cb9ba 정상 종료) → 정산 명문화 검토 + 본인 결정 누적 + 책임 분리 정정 + STT 자산 재배치 결정으로 흐름.
>
> **본 파일 위치**: 본 세션 작업 인계용. 정산 코드 *사실 명문화*는 `settlement_logic_definitive_2026-05-25.md` (단일 출처), 본 파일은 *세션 흐름 + 결정 누적*. 두 파일 자리 분리.

---

## 1. 블랙아웃 복원

### 1.1 복원 가능 자료 (모두 무사)
- **마지막 세션 transcript**: `~/.claude/projects/C--Users-kala1-designated-driver/db9cb9ba-25da-4db6-8492-a7eb344d427a.jsonl` (970KB, 5/25 19:55 정상 종료)
- **5/25 다른 세션들**: `ad37afef` (PTT 진입), `9b33fa59` (새벽), `d860c72e` (지방정부 v1~v6)
- **디스크 산출물**: `settlement_logic_definitive_2026-05-25.md` (33KB) + `feedback_settlement_definitive_first.md` (2.7KB) — untracked
- **git working tree 변경 + stash 1건** 그대로

### 1.2 손실 0인 이유
세션 종료 시점 jsonl fsync 완료 + 산출물 Write 시점 fsync 완료. 블랙아웃이 *세션 휴지 상태*에서 발생.

### 1.3 시간선
- 5/25 19:55 마지막 세션 정상 종료
- 5/26 23:44 본 세션 시작 (e9bf9553) — 약 28시간 무활동

---

## 2. 명문화 검증 (본인 의심 트랙)

### 2.1 본인 의심
"명문화가 진짜 코드 기반인가? 매니저앱 기사앱 정산처리·보관 확인하고 말하는 거야?"

### 2.2 transcript 검증 결과 (db9cb9ba)
- 전체 160 메시지 (12.9시간 세션)
- 도구 호출: Read 12 / Glob 4 / Grep 3 / Bash 2 / Write 2
- 검증 근거 파일 10개 vs 실제 Read 흔적:
  - ✅ 8개 직접 Read: SettlementViewModel.kt / SettlementCalculator.kt / SettlementModels.kt × 2 (call_manager + driver_app) / SettlementData.kt / SettlementCalc.kt / HistorySettlementScreen.kt / settlement.ts
  - △ 2개 Grep만: **DriverViewModel.kt** (submitDailySettlement L1592 등) / **index.ts** (onCallCompletedUpdateSettlement L4965, autoFinalize L5008)

### 2.3 라인 단위 대조 (Grep만 했던 2개 직접 정독)
**라인 자리 100% 정확** (5/5):
- onCallCompletedUpdateSettlement L4965, autoFinalize L5008, submitDailySettlement L1592, confirmReceiveCarryOver L1558, startCarryOverListener L1500

**누락 디테일 3건 발견 + 명문화 정정 반영**:

| # | 누락 자리 | 명문화 정정 |
|---|----------|------------|
| 1 | submitDailySettlement에서 `driver.status="PENDING_CONFIRM"` 동시 set (L1685) — **강제 게이트 자리 이미 부분 작동** | §6.2 + §7.2 보강 |
| 2 | confirmReceiveCarryOver 5개 필드 동시 update (명문화 2개만) | §6.5 정정 |
| 3 | carryOverListener masking 로직 의도 (UI 일관성) | §7.2 보강 |

### 2.4 검증 결론
명문화 신뢰 가능. 5/25 작업이 진짜 코드 정독 기반이었음.

---

## 3. 본인 두 원칙 (모든 결정의 상위)

1. **최소 개입** — 앱이 매니저·기사 운영에 끼어드는 액션 최소화. 매니저는 *보기만*, 입력은 본인이.
2. **다음 날 새로 시작** — 매일이 *독립 영수증*. 어제 데이터가 오늘 정산에 *1원도 영향 X*. 잔액 누적 변수 자체 코드에서 폐기.

---

## 4. 본인 결정 누적 (Q1~Q3 + Q2-2)

### 4.1 Q1 — 영업일 시간 ✅ **오전 10시**
- 현재 코드: 새벽 06:00 (`calculateWorkDate < 6`, `autoFinalize cron "10 6 * * *"`)
- 본인 결정 (5/27): **10시 기준 변경**
- 영향 자리:
  - `calculateWorkDate` (settlement.ts:60 + SettlementViewModel.kt:383 + DriverViewModel.kt:1658): `< 6` → `< 10`
  - `autoFinalizeSettlements` cron (index.ts:5010): `"10 6 * * *"` → `"10 10 * * *"`

### 4.2 Q2 — 강제 게이트 설계 ✅ **모든 실수 패턴 차단이 주 목적**
본인 발화: "다양한 사용자의 실수패턴을 막는게 주 목적이야. 모든 경우의 수를 차단할수 있는"
- 원인 특정(5/7 incident)은 부차, *모든 패턴 차단*이 주
- 실수 패턴 12개 × 게이트 매핑 (§7 인벤토리)

### 4.3 Q2-2 — 이체·수령 절차 폐기 ✅
본인 발화: "이체하기와 이체확인도 불필요한 간섭같아"

폐기 대상:
- `transferCarryOver` (SettlementViewModel.kt:1586) + `cancelTransfer` (L1713)
- `confirmReceiveCarryOver` (DriverViewModel.kt:1558)
- `carryOver.status` 상태 머신 (PENDING / TRANSFERRED / SETTLED)
- 매니저 "이체하기" 버튼 + 기사 "수령완료" 버튼
- FCM `CARRYOVER_TRANSFERRED`

송금 절차는 *코드 밖* — 현장에서 매니저↔기사 직접 처리. 앱은 *상태 기록 0, 절차 추적 0*.

### 4.4 Q3 — 그날 끝 Zero-base 모델 ✅
본인 발화: "다음날부터는 새로 시작하게 해야해"

설계:
- 매일 영업이 *그날 한 장의 영수증*으로 종결
- 영업일 종료(10시) 지나면 어제 dailySettlement는 *읽기 전용 히스토리* archive
- **carryOver 변수 자체 코드에서 완전 제거** (필드·리스너·계산함수 전부)
- 미납/환급 숫자는 *그날 보고서*에만, 내일과 연결 X
- 현장 청산은 매니저↔기사 본인들이 알아서

폐기 대상 (Q2-2 위 + 추가):
- `DriverCarryOver` data class + Firestore 필드
- `carryOverListener` (call_manager + driver_app 양쪽)
- `processCarryOverOnFinalize` (SettlementViewModel.kt:1759)
- `cleanup banner` (DashboardViewModel `f46ff609`) — 강제 게이트로 대체
- 보조 함수 3종: `calculateAdjustedDeposit` / `calculateRemainingCarryOver` / `calculateUsedFromCarryOver`
- `DriverDailySettlement` 필드 5종: `calculatedCarryOver` / `originalCarryOver` / `originalTripCount` / `originalTotalFare` / `originalRealDeposit`
- 공식: `originalCarryOver - finalDeposit + realDeposit` 자체

### 4.5 정산 화면 새 구조 (본인 검토용 모형)

**기사 그날 마감 화면**:
```
오늘 운행: 8건
총 운행료: 240,000원
사무실 몫: 144,000원 (60%)
내가 가져간 돈: 96,000원
사무실에 입금할 돈: 60,000원
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

정산 = ① 합계 자동 계산 + ② 기사 본인 입금 입력 1줄 + ③ 외상 명단 별도 추적 (CreditManagementScreen 유지)

운행 내역과의 차이: *결제수단별 합계 산출 + 기사 입금 확인 + 외상 별도 명단*

---

## 5. 책임 분리 결정 (본인 통찰 — 누더기 정정)

### 5.1 본인 통찰
"이런식으로 하면 누더기가 되지 않을까? 기본적 정산로직만 남겨두고 차단기능은 분리해야하지 않을까?"

→ 정산 로직 ↔ 차단 기능 *책임 분리* (SRP). 정산 = 돈 계산 / 차단 = 사용자 행동 강제. 자리 다름.

### 5.2 클코 권장 — 2 파일 분리 + 코드 모듈 분리
| 자리 | 처리 |
|------|------|
| 문서 차원 | 2 파일 분리: `settlement_logic_definitive_2026-05-25.md` (사실만, §1~14) + `settlement_redesign_2026-05-27.md` (신규, 설계만 — Zero-base 모델 + 강제 게이트 인벤토리 한 파일에 두 절) |
| 코드 차원 | 정산 로직 모듈(`SettlementCalculator` / `SettlementCalc` / `settlement.ts`) ↔ 강제 게이트 모듈(`EnforcementGate.kt` + 구현체 5개) 분리. 게이트가 정산 *읽기만*, *수정 X* |

### 5.3 왜 2 파일 (3 파일 X)
- 게이트는 *새 모델 설계의 세부*. Zero-base 모델과 같은 본인 결정 트랙
- 3 파일이면 동기화 부담 + 본인 왕복 부담
- 코드 모듈 분리는 별도 트랙 (PTT plan §3 코드 진입 시점)

### 5.4 현재 상태
- 본인 OK 받음 (메모 정리 명령 = 본 분리 결정 포함)
- **명문화 §15·§16 임시 박힌 상태** — 분리 작업으로 redesign 파일로 *이전*하고 명문화는 §1~14 + 본문 정정만 남김

---

## 6. STT 자산 재배치 결정 (본인 5/27)

### 6.1 본인 결정
"현재 내부콜을 음성메모와 파싱으로 전달하는데 이는 ptt로 대체하기로 한거고, 공유콜을 현사용중인 음성메모와 파싱으로 전환한다는거지"

### 6.2 의미
| 자산 | 현재 | 5/25 PTT plan | 5/27 결정 |
|------|------|--------------|----------|
| CallMemoParser (4/26 Phase B-ext 완료, 미커밋) | 내부콜 STT 파싱 | PTT 발화 대체로 *역할 종료* | **공유콜 업로드 진입점으로 이식** |
| 음성메모 (Firebase Storage, 4/26 결정) | 내부콜 기사 1:1 첨부 | 동일하게 *역할 종료* | **공유콜 업로드 시 받는 사무실에 음성+필드 동시 전달** |

자산 폐기 0, *자리만 이동*.

### 6.3 채팅 블랙박스(PTT plan §9)는 별개
- PTT 발화 → SpeechRecognizer → chat_messages 자동 게시
- 본 STT 재배치 결정과 *다른 트랙*. 둘 다 살아남음.

### 6.4 메모 박을 자리
- `plan_voice_parsing_external_data_2026-04-26.md` 상단에 5/27 결정 한 절 추가 (해당 plan이 STT 파싱 자산의 단일 출처)
- 실제 코드 진입은 PTT plan §3 작업과 동시 또는 그 후

---

## 7. 강제 게이트 인벤토리 (실수 패턴 12종 × 게이트 매핑)

### 7.1 분류
- 🟢 이미 작동 중 (보강만 필요)
- 🟡 부분 작동 (한쪽만 작동, 다른 쪽 보강)
- 🔴 신규 (코드 자리 없음)

### 7.2 매핑 표

| # | 실수 패턴 | 게이트 | 분류 | 코드 자리 |
|---|----------|--------|------|----------|
| 1 | 기사 "업무마감" 안 누르고 앱 닫음 | 미정산 콜 1건+ → 앱 종료/logout disable | 🔴 신규 | logout 함수 가드 |
| 2 | 기사 logout 후 종료 | 동일 | 🔴 신규 | LoginRepository.logout() 차단 |
| 3 | 기사 다음날 진입 시 어제 마감 미완료 | 앱 시작 게이트 → 어제 정산 강제 | 🟡 부분 | driver.status=PENDING_CONFIRM 배차 차단 (L1685) 있음, 앱 진입 자체는 가능 |
| 4 | 매니저 "정산확인" 안 누름 | 매니저 앱 진입 시 미확인 기사 모달화 | 🟡 부분 | 5/10 cleanup banner (`f46ff609`) 작동, 닫기 가능 → 모달+닫기X 보강 |
| 5 | 매니저 "일괄 마감" 안 누름 | 영업일 종료(10시) + 미마감 → 매니저 앱 전체 잠금 | 🔴 신규 | autoFinalize cron은 isFinalized만 갱신 |
| 6 | 매니저 "이체하기" 후 실제 송금 안 함 | **§4.3 자연 해소** | ✅ 해소 | transferCarryOver 폐기 |
| 7 | 기사 "수령완료" 안 누름 | **§4.3 자연 해소** | ✅ 해소 | confirmReceiveCarryOver 폐기 |
| 8 | 영업일 자정 넘기며 어제·오늘 콜 섞임 | calculateWorkDate 10시 + isFinalized 가드 | 🟢 작동 | settlement.ts:60 + isFinalized 가드 |
| 9 | 기사 1명만 마감, 다른 기사 미마감 | 매니저 일괄 마감 게이트(#5)에 묶음 | 🟡 #5 | clearAllTrips + processCarryOverOnFinalize |
| 10 | 매니저 잘못 confirm 후 정정 필요 | rejectDailySettlement 유지 + 정정 버튼 | 🟢 작동 | SettlementViewModel.kt:2076 |
| 11 | autoFinalize cron 실패 | 매니저 앱 시작 시 어제 isFinalized=false 감지 → 수동 마감 강제 | 🟡 부분 | cron 있음, 클라이언트 감지 X |
| 12 | 기사 정산 수치 *조작* | 매니저측 원본 콜 합 재검증 | 🔴 신규 | confirmDailySettlement는 기사 계산값 그대로 사용. 본인 결정 대기 |

### 7.3 작업 부피 요약
| 분류 | 패턴 # | 부피 |
|------|--------|------|
| ✅ 자연 해소 | 6, 7 | 0 |
| 🟢 이미 작동 | 8, 10 | 0 |
| 🟡 부분 작동 (보강) | 3, 4, 11 | 중간 |
| 🔴 신규 | 1, 2, 5, 9, 12 | 큼 |

### 7.4 본인 결정 대기 자리 (§16.4)
1. **#12 기사 수치 조작 검증** — 매니저측 원본 합 재검증 진입? 최소 개입 원칙과 절충
2. **#4 모달화 강도** — cleanup banner를 닫기X 모달로? 절충 = "전체 확인" 1개만 활성
3. **#5 영업일 10시 종료 강도** — 전체 잠금? grace period? N분 알림 후 잠금?

---

## 8. 결정 보류 자리

- **§11.5 코드 버그** — "현금+포인트" 계산 3곳 불일치 (`cashAmount?` vs `cashReceived` vs `cashAmount만`). PTT §3 작업 안에서 자연 해소 가능성 高
- **5/7 incident 원인 특정** — 본인 결정: 부차, 모든 패턴 차단이 주
- **§13 영업일 시간 변경 이력 git blame** — 미확인 유지 (10시로 변경되므로 무의미)

---

## 9. 미완료 작업 + 다음 진입 후보

### 9.1 본 세션 미완료
- **2 파일 분리 작업** — definitive에서 §15·§16 추출 + redesign 신규 파일 생성. 본인 OK 받음, 진입 대기 (본 메모 정리 후)
- **STT 재배치 메모** — plan_voice_parsing_external_data_2026-04-26.md 상단 절 추가
- **MEMORY.md 인덱스 갱신** — 본 토픽 파일 + redesign 파일 인덱스

### 9.2 본인 결정 대기 자리
- §7.4 강제 게이트 보류 3건 (#12 / #4 / #5)
- §11.5 코드 버그 추적 진입 여부

### 9.3 PTT plan §3 진입 시 작업 매트릭스
본 세션 §4·§5·§7 결과가 PTT plan `harmonic-sparking-hedgehog.md` §3 정산 단순화의 *실행 매트릭스* 역할:
- 폐기 대상 (15+ 자리)
- 변경 대상 (calculateWorkDate / autoFinalize cron / dailySettlement.status 단순화 등)
- 신규 대상 (강제 게이트 모듈 5개 + 공유콜 STT 진입점)

### 9.4 코드 진입 시점
**5/19 결정 ("PTT 이전 콜마당 수정 전면 보류") 유지**. 본 결정들은 *문서 차원*만, 코드 변경 0. 실제 코드 작업은 PTT plan §3 진입 시점에 일괄.

---

## 10. 클코 학습 (본 세션)

1. **명문화 검증은 transcript Read/Grep 패턴 추출이 가장 정확** — jsonl 도구 호출 패턴 직접 추출. 추측·우회 X
2. **누락 디테일은 의사결정에 영향** — driver.status=PENDING_CONFIRM 강제 게이트가 *이미 부분 작동* 발견이 §7 인벤토리 분류(🟡 부분 vs 🔴 신규)를 갈랐음
3. **누더기 위험은 사용자가 먼저 짚는다** — 클코가 §15·§16을 명문화에 쌓을 때 책임 분리 의식 못 함. 사용자가 SRP 원칙으로 정정. 다음에 *문서 차원에서도 책임 분리 의식*해서 작업
4. **자산 폐기 X, 자리만 이동** 패턴 — STT 재배치 결정처럼 *기존 자산이 살아있고 자리가 바뀌는* 결정은 *별도 자리 명시* 필요. 폐기 결정과 혼동 X
5. **본인 발화 "삭제 결정"은 정확한 출처 확인** — 메모리/plan 모두 검증해서 *진짜 삭제 결정인지 휴면인지* 확인. 본인 기억과 코드 사실 둘 다 대조

---

## 11. 단일 출처 정합

본 파일은 *세션 흐름 + 결정 누적*. 정산 코드 *사실* 단일 출처는 `settlement_logic_definitive_2026-05-25.md` 그대로. 본 파일이 그 명문화를 *대체*하지 않음.

분리 작업 진행 후 자료 자리:
- `settlement_logic_definitive_2026-05-25.md` = 정산 코드 *사실* (§1~14)
- `settlement_redesign_2026-05-27.md` (생성 예정) = *설계 + 게이트 인벤토리*
- `settlement_session_2026-05-27.md` (본 파일) = *세션 흐름 + 인계*

세 파일 자리 명확:
- 사실 (definitive) → 코드 변경 시만 갱신
- 설계 (redesign) → 본인 결정 변경 시 갱신
- 인계 (session) → 다음 세션 진입 시 정독, 이후 갱신 X (스냅샷)

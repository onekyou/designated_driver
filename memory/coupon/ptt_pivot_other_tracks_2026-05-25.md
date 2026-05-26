# PTT 이전 콜마당 수정 전면 보류 + 유효 트랙

**확정**: 2026-05-25 (본인 명시)
**관련 plan**: `C:\Users\kala1\.claude\plans\harmonic-sparking-hedgehog.md`
**관련 메모**: [[ptt_volume_button_principle]], [[ptt_market_replacement_card]], [[ptt_operation_scenario]], [[principle_user_action_minimum]]

## 한 줄

PTT 트랙 진입에 따라, 콜마당 측 *PTT 외 모든 코드 수정 계획 전면 보류*. 단 **간소화 + 정산 이월 단순화**는 PTT와 *함께 진행*.

## 전면 보류 대상

PTT 시나리오 ([[ptt_operation_scenario]]) 진입 결정으로 다음 트랙 모두 보류:

- `cheerful-swinging-turtle.md` (5/12 카톡 공유 prefill, 이미 SUPERSEDED)
- `floofy-waddling-marble.md` (5/11 콜디텍터 챗팅, 미진입)
- 콜 브로커 인프라 plan (5/11 후보, 미진입)
- 콜 카드 [예약 취소] UI 액션 (RESERVED 후속, 미진입)
- 픽업앱 RESERVED 적용 (의도적 제외 상태)
- 상태 명명 재설계 (AWAITING_SETTLEMENT/COMPLETED) — 별도 리팩터링 트랙
- 내부콜 삭제 fix (5/10 별건 옵션 A/B)
- 콜매니저 prefs raw string 정리 (B 트랙)
- IAM `iam.serviceAccountUser` 누락 fix (별건, 사용자 직접 실행 자리)
- PERMISSION_DENIED 추적 (5/10 별건)
- 기타 5/10~5/19 보류 후보 트랙

## 유효 트랙 (PTT와 함께 진행)

### 1. 간소화

PTT 시나리오의 *자연 결과*:
- 콜 카드 작성 폐기 (매니저→기사 위임)
- 알림 통합 (현재 6종+ 폭주 → Data Push 라우터 1)
- 매니저 UI 축소

PTT 코드 진입에 *포함되어 처리*. 별도 트랙 아님.

### 2. 정산 이월 단순화

본 turn 본인 명시 신규 결정.

#### 원칙

- 현재 흐름: 4단계 (Driver `PENDING_CONFIRM` → Manager `CONFIRMED` → Manager `TRANSFERRED` → Driver `SETTLED`)
- 본인 결정: **하루로 끝남**. 이월까지 우리(콜마당)가 관여하는 건 *오버*
- 매니저 = *오늘 정산 확인까지*. 이월·이체는 *기사 본인 책임*
- 매니저 개입 = **최소** ([[principle_user_action_minimum]] 정합)
- 본인 발화: "이역시 최소한 개입 원칙으로"

#### 5/7 incident 근본 해결

- 5/7 incident (PENDING_CONFIRM 잔존 "마감대기중") 근본 해결 자리
- cleanup gate banner (`f46ff609`, 2026-05-10)는 *증상 완화*였음
- 본 결정이 *근본 해결* — 매니저 액션 폐기로 PENDING_CONFIRM 잔존 자체 불가능

#### 구현 자리 (Phase 1 추가 Explore 후 정밀화)

- `SettlementViewModel.kt:1586 transferCarryOver()` — 매니저 액션 폐기 또는 *옵션* 처리
- `confirmDailySettlement` 이후 자동 종료 흐름 설계
- driver_app `carryOver` listener 단순화 (이월 status TRANSFERRED→SETTLED 단계 제거)
- 정산 공식 `carryOver = originalCarryOver - finalDeposit + realDeposit` 유지하되 *후속 이체 흐름 분리*
- Firestore `dailySettlements` 컬렉션 + `designated_drivers/{uid}.carryOver` 필드 단순화
- `cleanup gate banner` (`f46ff609`)는 정산 단순화로 *대체 예정* (제거 또는 의미 축소)

## 보류 트랙 재진입 조건

본 보류 트랙들은 *PTT 트랙 완료 후* 재평가. PTT 트랙이 매니저 UI 자체를 재정의하므로, 보류 트랙 중 일부는 *불필요*해질 수 있음 (예: 콜 카드 [예약 취소] UI 액션 — PTT 발화로 대체).

PTT 트랙 종료 후 보류 트랙 *각각* 재진입 가치 평가 → 진행 / 자동 폐기 / 변형 진행 결정.

## 본 결정의 무게

- 5/10~5/19 누적 보류 후보 10여 개 트랙 = *전부 보류* 명시
- 클코는 본 결정 위반 시 즉시 환기 (예: "○○ 트랙 진행할까요?" 질문 시 → "PTT 이전 수정 전면 보류 중")
- PTT 트랙만 코드 진입 자리

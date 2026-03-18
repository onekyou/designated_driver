# Day C: 마감 + 공유콜(자동+수동) + 경합 (30콜)

## 목표
마감 후 자동 공유콜 생성, 수동 공유, 수임 경합, 공유콜 정산 귀속이 정상 동작하는지 검증.
FCM data-only 전환(`e2e0c3d0`)과 콜매니저 기사탭 중복 제거(`1ce0e1b2`)가 영향을 미치지 않았는지 확인.

## 설정
- 사무실1 (mgr-main + drv-a + drv-b) + **사무실2 (mgr-sub + drv-c)**
- depositRatio: 사무실1 60%, 사무실2 60%
- 결제: 다양한 혼합

---

## Phase 1: 사무실1 정상 운행 (10콜)

### 기사A (5콜)
| # | 콜ID | 유형 | 결제 | 요금 | cashReceived | 비고 |
|---|------|------|------|------|-------------|------|
| 1 | CC-001 | 전화 | 현금 | 20,000 | 20,000 | 정상 |
| 2 | CC-002 | 앱 | 현금 | 25,000 | 25,000 | 정상 |
| 3 | CC-003 | 전화 | 이체 | 18,000 | 0 | 이체 |
| 4 | CC-004 | 전화 | 현금 | 30,000 | 30,000 | 정상 |
| 5 | CC-005 | 앱 | 현금+포인트 | 22,000 | 14,000 | 현금14k+포인트8k |

### 기사B (5콜)
| # | 콜ID | 유형 | 결제 | 요금 | cashReceived | 비고 |
|---|------|------|------|------|-------------|------|
| 6 | CC-006 | 전화 | 현금 | 28,000 | 28,000 | 정상 |
| 7 | CC-007 | 앱 | 포인트 | 15,000 | 0 | 포인트 전액 |
| 8 | CC-008 | 전화 | 현금 | 22,000 | 22,000 | 정상 |
| 9 | CC-009 | 전화 | 외상 | 20,000 | 0 | 외상 |
| 10 | CC-010 | 앱 | 현금 | 16,000 | 16,000 | 정상 |

---

## Phase 2: 사무실1 마감 → CLOSED

1. drv-a: submitDailySettlement
2. drv-b: submitDailySettlement
3. mgr-main: confirmDailySettlement (A) → confirmDailySettlement (B)
4. mgr-main: **finalizeSettlementSession → 사무실1 CLOSED**

**CP1**: 사무실1 정산 정합성 (10콜)
**CP2**: 사무실 상태 CLOSED

---

## Phase 3: 마감 후 자동 공유콜 (10콜)

> 사무실1 CLOSED 상태에서 전화 → Detector가 자동으로 shared_calls 생성

| # | 콜ID | 번호 | 요금 | shared_calls |
|---|------|------|------|-------------|
| 11 | SC-001 | 010-9001-0001 | 20,000 | sourceOfficeId=사무실1, status=OPEN |
| 12 | SC-002 | 010-9002-0002 | 18,000 | status=OPEN |
| 13 | SC-003 | 010-9003-0003 | 25,000 | status=OPEN |
| 14 | SC-004 | 010-9004-0004 | 15,000 | status=OPEN |
| 15 | SC-005 | 010-9005-0005 | 22,000 | status=OPEN |
| 16 | SC-006 | 010-9006-0006 | 30,000 | status=OPEN |
| 17 | SC-007 | 010-9007-0007 | 16,000 | status=OPEN |
| 18 | SC-008 | 010-9008-0008 | 28,000 | status=OPEN |
| 19 | SC-009 | 010-9009-0009 | 20,000 | status=OPEN |
| 20 | SC-010 | 010-9010-0010 | 24,000 | status=OPEN |

**CP3**: 10건 모두 shared_calls에 OPEN 상태로 생성
**CP4**: 각 문서에 sourceOfficeId, callData 정확

---

## Phase 4: 매니저 수동 공유 (5콜)

> 사무실1 재오픈 가정. 기사 부족으로 수동 공유 처리

| # | 콜ID | 유형 | 결제 | 요금 | 비고 |
|---|------|------|------|------|------|
| 21 | CC-021 | 전화 | 현금 | 20,000 | 수동 공유 |
| 22 | CC-022 | 앱 | 현금 | 18,000 | 수동 공유 |
| 23 | CC-023 | 전화 | 이체 | 25,000 | 수동 공유 |
| 24 | CC-024 | 전화 | 현금 | 15,000 | 수동 공유 |
| 25 | CC-025 | 앱 | 현금 | 22,000 | 수동 공유 |

1. mgr-main: 각 콜 생성 (WAITING)
2. mgr-main: **shareCall()** → shared_calls 생성 (status=OPEN)

**CP5**: 수동 공유 5건 shared_calls에 OPEN 생성
**CP6**: 자동공유(SC-*)와 수동공유(CC-02*)가 동일 형태

---

## Phase 5: 사무실2에서 수임 + 처리 (15콜)

> mgr-sub, drv-c 활성화

### 자동공유 10건 수임
1. mgr-sub: claimSharedCallWithDetails (SC-001~010)
2. drv-c: 각 콜 수락→운행→완료→정산

### 수동공유 5건 수임
3. mgr-sub: claimSharedCallWithDetails (CC-021~025)
4. drv-c: 각 콜 수락→운행→완료→정산

**CP7**: 모든 shared_calls status: OPEN → CLAIMED → (사무실2 calls 생성)
**CP8**: 공유콜 정산이 **사무실2에** 귀속 (사무실1 아님)

---

## Phase 6: 공유콜 경합 테스트 (5콜)

> 2개 사무실이 동시에 같은 공유콜 수임 시도

| # | 콜ID | 번호 | 요금 | 경합 |
|---|------|------|------|------|
| 26 | SC-E01 | 010-9901-0001 | 20,000 | 사무실1 + 사무실2 동시 수임 |
| 27 | SC-E02 | 010-9902-0002 | 18,000 | 사무실1 + 사무실2 동시 수임 |
| 28 | SC-E03 | 010-9903-0003 | 25,000 | 사무실1 + 사무실2 동시 수임 |
| 29 | SC-E04 | 010-9904-0004 | 22,000 | 사무실1 + 사무실2 동시 수임 |
| 30 | SC-E05 | 010-9905-0005 | 15,000 | 사무실1 + 사무실2 동시 수임 |

1. 마스터가 공유콜 5건 생성 (shared_calls OPEN)
2. **mgr-main + mgr-sub 동시에 claimSharedCallWithDetails 시도**
3. 각 콜에 대해 **1개 사무실만 성공**, 다른 쪽은 트랜잭션 실패

**CP9**: 이중 수임 방지 (runTransaction, status=="OPEN" 체크)
**CP10**: 성공한 사무실에만 콜 복사

---

## Phase 7: 사무실2 마감

1. drv-c: submitDailySettlement (공유콜 포함 전체)
2. mgr-sub: confirmDailySettlement (C)
3. mgr-sub: finalizeSettlementSession

**CP11**: 사무실2 정산에 수임한 공유콜 모두 포함
**CP12**: 공유콜 요금 합계가 사무실2 정산에 정확히 반영

---

## 검증 체크리스트

| CP | 검증 항목 | 비고 |
|----|----------|------|
| CP1 | 사무실1 정산 정합성 (10콜) | 기존 로직 |
| CP2 | 사무실 CLOSED 상태 | 기존 로직 |
| CP3 | 자동공유 10건 OPEN 생성 | createSharedCall |
| CP4 | sourceOfficeId, callData 정확 | 기존 로직 |
| CP5 | 수동공유 5건 OPEN 생성 | shareCall() |
| CP6 | 자동/수동 공유 동일 형태 | 기존 로직 |
| CP7 | shared_calls OPEN→CLAIMED | claimSharedCallWithDetails |
| CP8 | 공유콜 정산 사무실2 귀속 | 핵심 |
| CP9 | 이중수임 방지 (트랜잭션) | 핵심 |
| CP10 | 성공 사무실에만 콜 복사 | 트랜잭션 |
| CP11 | 사무실2 정산에 공유콜 포함 | 정산 정합성 |
| CP12 | 공유콜 요금 합계 정확 | 정산 정합성 |

---

## 결과

> 시뮬레이션 완료 후 아래에 기록

### 발견 이슈
(없음)

### 정산 검증
(미완료)

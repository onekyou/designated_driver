# 시나리오 빌딩 블록

시나리오를 조합할 때 사용하는 기본 블록.

---

## 콜 유형

| 블록 | 설명 | 코드 경로 |
|------|------|----------|
| 전화콜 | Detector 감지 → calls 생성 | CallDetectorService → saveCallToFirestore |
| 앱콜 | Customer requestCall → calls 생성 | MainViewModel.requestCall → CallService |

## 배차 경로

| 블록 | 설명 | 코드 경로 |
|------|------|----------|
| Manager 배차 | DashboardViewModel.assignCallToDriver | 기사 ASSIGNED, CF oncallassigned |
| Detector 배차 | DispatchActivity | 기사 ASSIGNED, CF oncallassigned |

## 콜 결과

| 블록 | 상태 전이 |
|------|----------|
| 정상 완료 | WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED |
| 기사 거절 | ASSIGNED → HOLD → 재배차 (ASSIGNED) |
| 기사 취소 | ACCEPTED → HOLD → CANCELLED_BY_DRIVER |
| 관리자 취소 | 상태 무관 → CANCELED + 기사 WAITING 복구 |
| 고객 취소 | 상태 무관 → CANCELLED_BY_CUSTOMER + 포인트 환불 |
| ASSIGNED 타임아웃 | ASSIGNED → 3분 무응답 → checkAssignedTimeout CF |

## 결제수단

| 블록 | cashReceived | creditAmount | pointsUsed | paymentMethod |
|------|-------------|-------------|------------|---------------|
| 현금 | fare | 0 | 0 | "현금" |
| 이체 | 0 | 0 | 0 | "이체" |
| 카드 | 0 | 0 | 0 | "카드" |
| 외상 | 0 | creditAmount | 0 | "외상" |
| 포인트 | 0 | 0 | fare | "포인트" |
| 현금+포인트 | cashAmount | 0 | pointsUsed | "현금" |

## 공유콜

| 블록 | 트리거 | 코드 경로 |
|------|--------|----------|
| 마감 자동 | 사무실 CLOSED + 전화 | CallDetectorService → createSharedCall |
| 수동 공유 | 매니저 판단 | DashboardViewModel → shareCall |
| 수임 | 타 사무실 매니저 | claimSharedCallWithDetails (runTransaction) |
| 수임 경합 | 2사무실 동시 수임 | 트랜잭션 1개만 성공 |

## 정산

| 블록 | 주체 | 코드 경로 |
|------|------|----------|
| 기사 마감 | 기사 | submitDailySettlement |
| 매니저 확인 | 매니저 | confirmDailySettlement |
| 이월 이체 | 매니저 | transferCarryOver |
| 기사 수령 | 기사 | acknowledgeTransfer |
| 전체 마감 | 매니저 | finalizeSettlementSession → CF processCarryOverOnFinalize |

## 장애

| 블록 | 설명 |
|------|------|
| FCM 미도착 | 배차 FCM 미수신 → 관리자 직접 연락 |
| 앱 Kill | 강제종료 → 재시작 → 활성콜 복구 |
| 네트워크 단절 | 오프라인 → 복구 → Firestore 동기화 |

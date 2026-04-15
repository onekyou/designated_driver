# 관리자 직접운행 코드 드라이런 결과

**검증 대상**: `6135176e` (MVP v5) + `9d4e9706` (외상중복 제거 + 일일 통합)
**방식**: 코드 경로 추적 · 숫자 공식 재계산 (실기기 미사용)
**3축**: 격리 / 합류 / 정합성

---

## 격리 (Isolation)

### ✅ CF `onCallCompletedUpdateSettlement` — handledByManager 가드
`functions/src/index.ts:4816-4820` — handledByManager=true면 settlementSessions 추가 스킵. 실기사 정산 세션 오염 없음.

### ✅ CF `finalizeSettlementAndNotifyDrivers` 스킵
`SettlementViewModel.kt:1214` — 실기사 0건 + directRunTrips>0 일 때 CF 호출 자체를 스킵하고 로컬 세션만 생성. 기사 알림 불필요 → 정확.

### ⚠️ CF `oncallassigned` 오탐 실행 (경미)
completeAsManager가 `assignedDriverId="MANAGER"`를 세팅 → `oncallassigned` CF가 트리거됨 → `designated_drivers/MANAGER` 조회 실패 → `driverDoc.exists=false` 조기 return (line 537-540).
- **실질 영향**: 없음 (FCM 미전송)
- **로그 오염**: `logger.error` 1건/콜
- **권장**: CF 상단에 `if (afterData.handledByManager === true) return;` 가드 추가 (1줄). 선택사항.

### ✅ `checkAssignedTimeout` 미트리거
상태가 WAITING→COMPLETED로 바로 전이, ASSIGNED 경유 안 함 → 스케줄러 필터(status=ASSIGNED) 통과 못 함.

### ✅ 기사앱 미침투
- FCM `call_assigned` 미전송
- 기사앱 trips 리스너는 `assignedDriverId == 자신의 UID` 쿼리 → "MANAGER" 매칭 안 됨

---

## 합류 (Aggregation)

### ✅ 전체 탭 숫자 공식 (`AllTripsScreen.kt:152-248`)
시나리오 2 (직접만, 65,000): 총매출 65k, 총수입 65k, 기사납입/이체/미수금 0, 직접운행 매출 65k, 실수입 65k — **모두 일치**.

시나리오 3 (혼합, depositRatio=60): 총매출 100k, 총수입 = 65k×0.6 + 35k = 74k, 미수금 = 40k (실기사 외상만), 직접운행 매출 35k — **모두 일치**.

### ✅ 대기 탭 (`PendingSettlementsScreen.kt:32-37`)
`(trips + directRunTrips).distinctBy { callId }.filter { 이체/외상 }` — 실기사·직접운행 합쳐서 결제방식 필터만. 직접운행은 driverName="관리자"로 "관리자: 이체/외상" 표시.

### ✅ 외상 자동등록 제거 (`9d4e9706` diff)
`SettlementViewModel.kt`의 2곳(fetchCompletedCallsWithDriverFilter + 실시간 리스너)에서 `addOrIncrementCredit` 호출 삭제 확인. 대기 탭 "외상 등록" 버튼으로만 수동 확정.

### ✅ 일일 세션 카드 뱃지 (`DailySessionScreen.kt:51-54`)
`flowManagerCountsBySession` DAO 쿼리: `SELECT sessionId, COUNT(*) FROM settlements WHERE driverId='MANAGER' AND sessionId IS NOT NULL GROUP BY sessionId` — 직접운행만 카운트.

### ✅ 상세 다이얼로그 `[직접]` 태그 (`TripDetailDialog.kt:64-74`)
`driverId == "MANAGER"` 판정으로 [직접] 태그 + driverName 하이라이트. 제목에 "(직접운행 N건 포함)".

---

## 정합성 (Consistency)

### ✅ settlementLastCleared 실시간 리스너 (`SettlementViewModel.kt:215-228`)
office 문서 snapshot 리스너로 변경 감지 → `applyDirectRunFilter()` 재실행. 시나리오 6 (재마감 후 신규): 첫 마감 T1 → 필터 cutoff=T1 → 이전 건 숨김. 새 직접운행 T2>T1 → 보임. 두 번째 마감 T3 → 새 세션 카드 분리 생성.

### ✅ clearAllTrips 직접운행 포함 (`SettlementViewModel.kt:694-740`)
- `totalTrips = trips.size + mgrTrips.size` / `totalFare = 합산`
- `SettlementEntity.fromData(trip).copy(isFinalized=true, sessionId=newSessionId)` → Room insert
- 직후 office 문서 `settlementLastCleared = Timestamp.now()` 갱신 → 리스너 트리거 → 리스트 즉시 비워짐

### ✅ 마감 버튼 활성화 조건 (`AllTripsScreen.kt:377`)
`enabled = trips.isNotEmpty() || directRunTrips.isNotEmpty()` — 실기사 0건이어도 활성화.

### ✅ 재시작 복원
- `startManagerDirectListener`: `whereEqualTo("handledByManager", true)` Firestore 리스너 → 앱 재시작 시 재구독되어 오늘 건 복원
- `startOfficeLastClearedListener`: office 문서 리스너 → cutoff 즉시 복원
- Room DB의 session/settlements는 디스크 영속 → 일일 탭 복원

### ✅ 취소 WAITING 불변 (`DashboardScreen.kt:1265`)
DirectRunDialog의 "취소" 버튼은 `onDismiss`만 호출, Firestore 미터치. WAITING 상태 불변.

### ✅ 외상 이중 등록 방지 (시나리오 4)
자동 등록 경로 제거 → 대기 탭 수동 등록 1회만. 같은 전화번호는 `creditDao.addOrIncrementCredit`가 기존 row에 `amount += addAmount`로 누적 (phone 매칭).

---

## 종합 판정

| # | 시나리오 | 코드 드라이런 | 비고 |
|---|---------|--------------|------|
| 1 | 회귀 (실기사만) | ✅ PASS | 실기사 경로 무변경 |
| 2 | 직접운행만 | ✅ PASS | 숫자 공식 검증 완료 |
| 3 | 혼합 | ✅ PASS | 격리+합류 정합 |
| 4 | 외상 이중 등록 방지 | ✅ PASS | 자동등록 2곳 제거 확인 |
| 5 | 직접운행 취소 | ✅ PASS | Firestore 미터치 |
| 6 | 재마감 후 신규 | ✅ PASS | cutoff 기반 재필터 |
| 7 | 재시작 복원 | ✅ PASS | 리스너 3개 재구독 |

**코드 레벨 ALL PASS.** 실기기 UI 테스트는 이미 완료 상태.

---

## 권장 개선사항 (선택)

1. **(경미) `oncallassigned` CF에 handledByManager 가드**
   - 현재: `designated_drivers/MANAGER` 조회 실패로 조기 return (에러 로그 1건)
   - 개선: CF 상단 3-4줄 아래에 `if (afterData.handledByManager === true) { logger.info(...); return; }` 추가
   - 효과: 로그 노이즈 제거 + Firestore read 1회 절약 (콜당)

2. **(문서) `SettlementViewModel.kt:213` 주석 정정**
   - "업무 마감 시 **CF가** 이 필드를 갱신하면" → 실제로는 **클라이언트(`clearAllTrips`)가** 갱신. 주석 수정 권장.

둘 다 기능 영향 없음. 현재 상태로 파일럿/출시 가능.

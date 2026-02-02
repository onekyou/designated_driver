# 작업 내역 - 2026-02-02 (이월 정산 시스템)

## 개요

이월 정산(미지급/미수령) 시스템을 구현했습니다.
- 기사 몫보다 현금 수령이 적으면 미지급 발생
- 미지급금은 누적 관리
- 매니저가 이체하면 기사가 수령 확인 후 정산 완료

---

## 커밋 이력

| 커밋 | 내용 |
|------|------|
| `a4c8ca3f` | 정산 이중저장 제거 + 운행시작 팝업 순서 수정 |
| `f130a4d7` | 이월 정산 시스템 구현 |
| `2aee1ebf` | 마감 시 carryOver 저장 + 이체 FCM 알림 추가 |

---

## 수정된 파일

### 콜매니저 (call_manager)

| 파일 | 변경 내용 |
|------|----------|
| `data/settlement/SettlementModels.kt` | CarryOverStatus, DriverCarryOver, DriverCarryOverSummary 추가 |
| `ui/settlement/SettlementViewModel.kt` | carryOverList StateFlow, 리스너, 이체하기/이체취소, 마감 시 저장, FCM 알림 |
| `ui/settlement/screen/AllTripsScreen.kt` | 기사별 미지급 테이블 UI |
| `ui/settlement/screen/DriverSummaryScreen.kt` | 기사 카드에 미지급 정보 + [이체하기]/[이체취소] 버튼 |

### 기사앱 (driver_app)

| 파일 | 변경 내용 |
|------|----------|
| `data/settlement/SettlementModels.kt` | CarryOverStatus, DriverCarryOver 추가 |
| `viewmodel/DriverViewModel.kt` | carryOver StateFlow, 리스너, 수령완료 함수 |
| `ui/home/HistorySettlementScreen.kt` | 미수령금 카드 + [수령완료] 버튼 |

---

## 상태 흐름

```
PENDING (미지급/미수령)
    │
    │ [매니저: 이체하기] → FCM 알림 전송
    ▼
TRANSFERRED (이체됨/대기)
    │
    ├─ [매니저: 이체취소] → PENDING
    │
    └─ [기사: 수령완료] → SETTLED (잔액 0으로 리셋)
```

---

## Firestore 데이터 구조

```
drivers/{driverId}/
  carryOver: {
    balance: Long,           // 누적 미지급금
    status: String,          // PENDING | TRANSFERRED | SETTLED
    lastUpdatedAt: Timestamp,
    transferredAt: Timestamp?,
    transferredBy: String?,
    todayAmount: Long        // 오늘 발생 미지급금
  }
```

---

## 계산 로직

```
기사 몫 = 총 운행료 × (100% - 수수료율)
현금 수령 = 현금 결제 + 현금+포인트의 현금 부분
오늘 미지급 = 기사 몫 - 현금 수령

마감 시:
  if (오늘 미지급 > 0) {
    carryOver.balance += 오늘 미지급
  }
```

---

## 주요 함수

### 콜매니저 SettlementViewModel

| 함수 | 설명 |
|------|------|
| `startCarryOverListener()` | drivers 컬렉션 실시간 리스너 |
| `transferCarryOver()` | 이체하기 - TRANSFERRED로 변경 + FCM 알림 |
| `cancelTransfer()` | 이체취소 - PENDING으로 원복 |
| `processCarryOverOnFinalize()` | 마감 시 미지급금 누적 저장 |
| `sendCarryOverNotification()` | FCM 알림 전송 |

### 기사앱 DriverViewModel

| 함수 | 설명 |
|------|------|
| `startCarryOverListener()` | 내 기사 문서의 carryOver 실시간 감시 |
| `confirmReceiveCarryOver()` | 수령완료 - SETTLED로 변경, 잔액 0 |

---

## UI 화면

### 콜매니저 - 전체 탭
- 기사별 미지급 테이블 (최대 5명)
- 기사명 | 누적 | 오늘 | 상태

### 콜매니저 - 기사별 탭
- 기사 카드에 누적 미지급 정보
- PENDING 상태: [이체하기] 버튼
- TRANSFERRED 상태: [이체취소] 버튼

### 기사앱 - 운행내역 화면
- 미수령금 카드 (balance > 0일 때만 표시)
- TRANSFERRED 상태: [수령완료] 버튼

---

## 테스트 방법

1. 콜매니저에서 운행 완료 (이체/외상 결제로 미지급 발생)
2. **업무 마감** 클릭 → carryOver 저장됨
3. 전체 탭에서 미지급 테이블 확인
4. 기사별 탭에서 [이체하기] 클릭
5. 기사앱에서 미수령금 카드 확인
6. [수령완료] 클릭 → 정산 완료

---

## 참고 문서

- `docs/CARRYOVER_SETTLEMENT_DESIGN.md` - 설계서
- `docs/SESSION_CONTINUE_2026-02-02.md` - 이전 세션 이어가기

---

## 변경 이력

| 날짜 | 버전 | 변경 내용 |
|------|------|----------|
| 2026-02-02 | 1.0 | 이월 정산 시스템 구현 완료 |

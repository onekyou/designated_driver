# 세션 이어가기 - 2026-02-02

## 완료된 작업

### 1. 정산 이중 저장 제거 ✅
- **파일**: `driver_app/.../DriverViewModel.kt`
- **내용**: `saveToSettlementSession()` 호출 및 함수 제거
- **결과**: 기사앱은 `calls`만 저장, Cloud Function이 `settlementSessions` 관리

### 2. 운행시작 팝업 순서 수정 ✅
- **파일**: `call_manager/.../DashboardViewModel.kt` (Line 450-456)
- **변경 전**: 출발 → 도착 → 경유 → 요금
- **변경 후**: 출발 → 경유 → 도착 → 요금

### 3. 이월 정산 시스템 설계 ✅
- **문서**: `docs/CARRYOVER_SETTLEMENT_DESIGN.md`
- **내용**: 미지급/미수령 관리 시스템 전체 설계

---

## 다음 대화에서 할 작업

### 이월 정산 시스템 구현

#### 1. Firestore 데이터 구조 추가
```javascript
drivers/{driverId}/
  carryOver: {
    balance: 0,              // 누적 미지급금
    status: "PENDING",       // PENDING | TRANSFERRED | SETTLED
    lastUpdatedAt: Timestamp,
    transferredAt: null,
    transferredBy: null,
    todayAmount: 0           // 오늘 발생한 미지급금
  }
```

#### 2. 콜매니저 수정

**전체 탭 (AllTripsScreen.kt)**
- 기사별 미지급 테이블 추가 (간결한 버전)
- 기사명, 누적, 오늘, 상태 표시

**기사별 탭 (DriverSummaryScreen.kt)**
- 기사 카드에 미지급 정보 추가
- [이체하기] / [이체취소] 버튼 추가
- 상태별 UI 분기

**SettlementViewModel.kt**
- `carryOver` 데이터 로드/리스닝
- `onTransferClick()` - 이체하기
- `onTransferCancelClick()` - 이체취소
- 마감 시 미지급금 누적 로직

#### 3. 기사앱 수정

**HistorySettlementScreen.kt 또는 새 화면**
- 오늘 미수령금 표시
- 누적 미수령금 표시
- [수령완료] 버튼 (TRANSFERRED 상태일 때만)

**DriverViewModel.kt 또는 SettlementViewModel**
- `carryOver` 데이터 로드
- `onReceiveConfirmClick()` - 수령완료

#### 4. Cloud Function (선택)
- 마감 시 미지급금 자동 계산 및 누적
- FCM 알림 (이체됨 알림)

---

## 계산 로직 정리

```
총 운행료 = 각 운행 fare 합계
사무실 몫 = 총 운행료 × 수수료율 (60%)
기사 몫 = 총 운행료 × (100% - 수수료율) (40%)

현금 수령 = 현금 결제 + 현금+포인트의 현금 부분

오늘 미지급 = 기사 몫 - 현금 수령
  - 양수: 미지급 발생 (기사가 더 받아야 함)
  - 음수/0: 정상 (기사가 납입)

누적 미지급 = 전일 이월 + 오늘 미지급
```

## 예시 (확인됨)

```
운행: 만원 현금, 만원 이체, 만원 외상, 만원 포인트
총 운행료: 40,000원
수수료율: 60%

사무실 몫: 24,000원
기사 몫: 16,000원
현금 수령: 10,000원

미지급 = 16,000 - 10,000 = 6,000원
→ 기사가 사무실에서 6,000원 받아야 함
```

---

## 상태 흐름

```
PENDING (미지급)
    │
    │ [매니저: 이체하기]
    ▼
TRANSFERRED (대기)
    │
    ├─ [매니저: 이체취소] → PENDING
    │
    └─ [기사: 수령완료] → SETTLED (리셋)
```

---

## 관련 파일 위치

**콜매니저:**
- `call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/`
  - `SettlementTabHost.kt` - 탭 구조
  - `SettlementViewModel.kt` - 뷰모델
  - `screen/AllTripsScreen.kt` - 전체 탭
  - `screen/DriverSummaryScreen.kt` - 기사별 탭

**기사앱:**
- `driver_app/app/src/main/java/com/designated/driverapp/`
  - `viewmodel/DriverViewModel.kt`
  - `ui/home/HistorySettlementScreen.kt`
  - `data/repository/SettlementRepository.kt`

**설계 문서:**
- `docs/SETTLEMENT_SYSTEM_ANALYSIS.md` - 전체 분석서
- `docs/SETTLEMENT_TODO_LIST.md` - TODO 리스트
- `docs/CARRYOVER_SETTLEMENT_DESIGN.md` - 이월 정산 설계서

---

## 변경 이력

| 날짜 | 작업 | 상태 |
|------|------|------|
| 2026-02-02 | 이중 저장 제거 | ✅ 완료 |
| 2026-02-02 | 운행시작 팝업 순서 수정 | ✅ 완료 |
| 2026-02-02 | 이월 정산 설계 | ✅ 완료 |
| 2026-02-02 | 이월 정산 구현 | 🔄 다음 세션 |

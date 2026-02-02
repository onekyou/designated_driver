# 정산 시스템 재설계 - 세션 이어가기 문서

## 날짜: 2026-02-03

---

## 핵심 원칙 (절대 변경 불가)

1. **하나의 문서로 콜매니저와 기사가 공유 → 불일치 없앰**
2. **문서가 둘이 되더라도 동일한 계산 로직 유지**
3. **미지급금은 어떤 형태로든 저장되어서 유지되어야 함**

---

## 현재 상태

### 복구 완료
- 마지막 푸시 지점으로 코드 복구됨
- carryOver UI, 이체/수령 기능 모두 정상

### 추가된 변경 (ratio Firestore 저장)
- `SettlementViewModel.kt`: offices 문서에서 depositRatio 읽기
- `SettlementViewModel.kt`: updateOfficeShareRatio()에서 Firestore에도 저장

---

## 정산 계산 공식 (콜매니저/기사앱 공통)

```kotlin
val ratio = officeShareRatio  // 기본값 60%

// 기사 입장
기사몫 = 총운행금 × (100 - ratio)%  // 40%
현금수령 = Σ cashReceived
실납입금 = 현금수령 - 기사몫

// 양수 → 기사가 사무실에 납부
// 음수 → 사무실이 기사에게 지급 (미지급)
```

### 예시 (운행료 30,000원, ratio 60%)
| 결제방식 | 기사몫 | 현금수령 | 실납입금 | 의미 |
|---------|--------|----------|---------|------|
| 이체 | 12,000 | 0 | -12,000 | 사무실→기사 지급 |
| 현금 | 12,000 | 30,000 | +18,000 | 기사→사무실 납부 |

---

## 데이터 구조

### calls (팩트 데이터)
```
calls/{callId}
  - fare: Int              // 운행료
  - paymentMethod: String  // 현금, 이체, 포인트, 현금+포인트, 외상
  - cashReceived: Int      // 현금 수령액
  - driverId: String
  - completedAt: Timestamp
  - status: "COMPLETED"
```

### offices (설정)
```
offices/{officeId}
  - depositRatio: Int      // 분배비율 (기본 60)
  - settlementLastCleared: Timestamp
```

### drivers/carryOver (정산 상태)
```
drivers/{driverId}/carryOver
  - balance: Long          // 누적 미지급금 ← 저장 필요!
  - status: String         // PENDING | TRANSFERRED | SETTLED
  - transferredAt: Timestamp?
  - transferredBy: String?
  - lastUpdatedAt: Timestamp
```

---

## 미해결 문제

### balance 저장 방식

**현재 방식:**
- 마감 시 `todayUnpaid`를 기존 `balance`에 누적 추가
- 문제: 계산 로직이 콜매니저에만 있음, 기사앱은 다른 방식

**변경 목표:**
- 콜매니저/기사앱 모두 **같은 계산 로직** 사용
- `balance`는 저장 (누적 관리 위해)
- 계산 결과가 항상 동일 → 불일치 없음

### 해결 방안 (검토 필요)

**옵션 A: 마감 시 balance 저장 (현재 방식 개선)**
```
마감 시:
1. calls에서 오늘 미지급 계산
2. 기존 balance + 오늘 미지급 = 새 balance
3. carryOver.balance에 저장
```
- 장점: 구조 변경 최소화
- 단점: 여전히 마감 시점에만 동기화

**옵션 B: calls에 settled 플래그 추가**
```
calls/{callId}
  - settled: Boolean       // 정산 완료 여부
  - settledAt: Timestamp?

누적 계산 = 미정산 calls 전체에서 계산
```
- 장점: 실시간 누적 계산 가능
- 단점: 구조 변경 큼

**옵션 C: settlementSessions에 기사별 balance 저장**
```
settlementSessions/{date}
  - driverBalances: {
      driverId1: { balance: Long, status: String },
      ...
    }
```
- 장점: 날짜별 이력 관리
- 단점: 조회 복잡

---

## 다음 작업

1. **balance 저장 방식 결정** (옵션 A/B/C 중 선택)
2. **기사앱 calls 기반 계산 구현**
   - 현재: SharedPreferences 기반
   - 변경: Firestore calls 기반
3. **테스트**

---

## 파일 변경 내역

### 변경됨 (ratio Firestore 저장)
- `call_manager/.../SettlementViewModel.kt`
  - loadSettlementData(): depositRatio 읽기 추가
  - updateOfficeShareRatio(): Firestore 저장 추가

### 복구됨 (마지막 푸시 상태)
- `call_manager/.../SettlementViewModel.kt` - carryOver 함수들
- `call_manager/.../DriverSummaryScreen.kt` - carryOver UI
- `call_manager/.../AllTripsScreen.kt` - carryOver 테이블
- `driver_app/.../DriverViewModel.kt` - carryOver 리스너/함수
- `driver_app/.../HistorySettlementScreen.kt` - carryOver UI

---

## 참고: 이전 세션 문서
- `docs/SESSION_WORK_2026-02-02_carryover.md`
- `docs/CARRYOVER_SETTLEMENT_DESIGN.md`

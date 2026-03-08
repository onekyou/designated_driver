# Customer App 집중 분석 결과

> 마지막 업데이트: 2026-02-24 14:30 KST
> 분석 주체: team-lead (직접) + driver-analyst + manager-analyst + firebase-analyst
> 총 이슈: 17건 (Critical 5 / High 7 / Medium 5)

---

## 1. 아키텍처 요약

- **51개 파일**, 11,248줄 (Jetpack Compose + Firebase)
- **100% FCM 의존** (실시간 Firestore 리스너 주석 처리됨)
- 포인트 시스템: Customer App + Driver App + Cloud Functions 3자가 독립 적립

### 핵심 파일

| 파일 | 역할 | 줄수 |
|------|------|------|
| MainViewModel.kt | 콜 요청/취소, FCM 수신, 포인트 | 1,038 |
| MyFirebaseMessagingService.kt | FCM 4개 타입 처리 | 330 |
| CallService.kt | Firestore 콜 CRUD | 142 |
| PointService.kt | 포인트 조회/적립/사용 | 312 |

### 고객이 보는 상태 흐름

```
콜 요청 → REQUESTED (로컬)
    ↓ FCM: CALL_RECEIVED (전화콜 시)
기사 배정 → ASSIGNED (FCM: DRIVER_ASSIGNED)
    ↓ ❌ FCM 없음 (기사 수락/운행시작)
운행 완료 → COMPLETED (FCM: RIDE_COMPLETED)
```

---

## 2. 파일럿 영향도별 이슈 정리

### 즉시 수정 (파일럿 전 필수) - 4건

파일럿 운영 중 **확실히 발생**하며, 운영으로 커버 불가능한 이슈.

| ID | 심각도 | 이슈 | 왜 즉시? | 수정 방법 | 코드 위치 |
|----|--------|------|---------|----------|----------|
| **CUST-01** | Critical | 관리자 취소 시 Customer App 알림 미전달 | Manager가 콜 취소하면 고객이 "기사 배정됨"에 **영원히 고착**. 매일 발생 가능 | `onCallCancelledByDriver` 트리거에 `"CANCELED"` 조건 추가 | index.ts:2680-2682 |
| **CUST-10/BUG-D12** | Critical | 3자 이중 포인트 적립 | CF(`customerPointTransactions`) + CustomerApp(`pointTransactions`) + DriverApp(`pointTransactions`)이 **매 운행 완료마다** 최대 3회 적립. 컬렉션명 불일치로 중복 체크 실패 | Cloud Functions에서만 적립하도록 일원화 (Customer/Driver 클라이언트 적립 제거) | index.ts:1618 / MainViewModel.kt:579-698 / DriverViewModel.kt:636 |
| **CUST-04** | High | FCM 유일 경로 + 실시간 리스너 비활성 | 양평 네트워크 불안정 → FCM 미도착 → 앱 재시작해도 상태 갱신 안 됨 → 고객 상태 **영구 고착** | 앱 포그라운드 진입 시 Firestore에서 최신 활성 콜 1회 조회 (polling fallback) | MainViewModel.kt init 블록 |
| **CUST-02** | Critical | 포인트 차감 후 콜 생성 실패 시 복구 없음 | 양평 환경에서 `usePoints()` 성공 → `requestCall()` 실패 → **포인트 증발**. 고객 민원 직결 | 콜 생성 실패 시 catch 블록에서 `pointService.refundPoints()` 호출 | MainViewModel.kt:311-316 |

### 나중에 (파일럿 후 개선) - 8건

핵심 흐름은 작동하지만, UX 또는 보안이 불완전한 이슈. 파일럿 기간에는 큰 문제 없음.

| ID | 심각도 | 이슈 | 왜 나중에? | 수정 방법 | 코드 위치 |
|----|--------|------|----------|----------|----------|
| **CUST-03** | Critical | ACCEPTED/IN_PROGRESS 시 Customer FCM 없음 | 고객은 배정→완료 사이를 모르지만, **배정 알림은 오고 완료 알림도 옴**. 핵심 흐름은 작동 | `onCallStatusChanged`에서 ACCEPTED/IN_PROGRESS 시 Customer FCM 추가 | index.ts:1670-1754 |
| **CUST-06** | High | CANCELLED/CANCELED/CANCELLED_BY_DRIVER 3자 철자 불일치 | CUST-01 수정 시 함께 정리 가능. 당장은 CUST-01만 수정하면 핵심 문제 해소 | 전체 코드베이스에서 취소 상태 문자열 통일 | CallStatus.kt / index.ts / CallService.kt:42 |
| **CUST-05** | High | POINTS_EARNED FCM 미처리 (포그라운드) | 백그라운드에서는 `notification` 필드로 시스템 알림 표시됨. RIDE_COMPLETED는 정상 처리 | MyFirebaseMessagingService when절에 `POINTS_EARNED` 추가 | MyFirebaseMessagingService.kt:35-97 |
| **CUST-07** | High | FCM 토큰 저장 실패 시 재시도 없음 | 보통 첫 시도에 성공. 실패는 프로필 미설정 시 엣지케이스 | 토큰 저장 실패 시 SharedPreferences에 보관 → 다음 앱 시작 시 재시도 | MyFirebaseMessagingService.kt:296-329 |
| **CUST-12** | Medium | OFFICE_CLOSED FCM 미처리 | 마감 후 시나리오. 파일럿 운영 시간에는 발생 안 함 | MyFirebaseMessagingService when절에 `OFFICE_CLOSED` 추가 | MyFirebaseMessagingService.kt |
| **SEC-C01** | Critical | 콜 생성 인증 불필요 (`if true`) | 파일럿 사용자 제한적, 앱 미공개 상태에서 악용 가능성 낮음. 공개 배포 전 **반드시** 수정 | `allow create: if isAuthenticated()` 또는 `isCustomer()` 규칙 추가 | firestore.rules:132 |
| **SEC-C02~C04** | High x3 | customerPoints/pointTransactions/customerInfo 소유권 미검증 | 악용에 기술 지식 필요. 파일럿 사용자(양평 주민)가 Firestore API 직접 호출할 가능성 극히 낮음 | uid↔phoneNumber 매핑 규칙 추가, 또는 Cloud Functions 경유 강제 | firestore.rules:258-293 |

### 운영으로 커버 가능 (파일럿 중 수동 대응) - 5건

관리자가 인지하고 수동 대응하면 파일럿 기간에 문제없는 이슈.

| ID | 심각도 | 이슈 | 운영 대응 방법 | 수정 방법 | 코드 위치 |
|----|--------|------|-------------|----------|----------|
| **CUST-09** | Medium | 콜 취소 시 포인트 환불 미구현 | 관리자가 Firestore Console에서 포인트 수동 조정. 파일럿 규모(5기사)에서 빈도 낮음 | cancelCall() 시 pointsUsed 확인 → refundPoints() 호출 | MainViewModel.kt:323-355 |
| **CUST-11** | Medium | 재배차 시 기사 변경 알림 미전송 | 관리자가 재배차 시 고객에게 직접 전화 연락 | 재배차 시 DRIVER_ASSIGNED FCM 재전송하는 Cloud Function 추가 | DashboardViewModel.kt:689-790 |
| **CUST-08** | Medium | WAITING vs REQUESTED 상태 표시 불일치 | 기능에 영향 없음 (Firestore: WAITING, UI: "콜 요청됨"). 고객이 인지하기 어려움 | CallState에 WAITING 추가하고 콜 생성/UI 통일 | MainViewModel.kt:282,304 / CallStatus.kt |
| **SEC-C05** | Medium | 콜 취소 시 본인 콜 미검증 | 파일럿 규모에서 타인 콜 취소 시도 가능성 극히 낮음 | `resource.data.customerId == request.auth.uid` 조건 추가 | firestore.rules:147-149 |
| **RIDE payload** | Low | RIDE_COMPLETED에 paymentMethod 미포함 | 고객이 정산 상세를 보는 UI가 없으므로 현재 영향 없음 | FCM payload에 paymentMethod, finalFare 추가 | index.ts:1597-1609 |

---

## 3. FCM 전체 매핑

### Cloud Functions → Customer App

| 함수명 | 트리거 | FCM type | Customer 처리 | 파일럿 영향 |
|--------|--------|----------|-------------|------------|
| oncallassigned (L350+) | → ASSIGNED | `DRIVER_ASSIGNED` | O (팝업+진동) | 정상 |
| notifyCustomerOnPhoneCall (L1465) | 콜 생성 | `CALL_RECEIVED` | O (알림) | 정상 |
| notifyCustomerOnComplete (L1541) | → COMPLETED | `RIDE_COMPLETED` | O (팝업) | 정상 |
| notifyCustomerOnComplete (L1637) | 포인트 적립 후 | `POINTS_EARNED` | **X 미처리** | 나중에 |
| onCallCancelledByDriver (L2660) | → HOLD/CANCELLED_BY_DRIVER | `CALL_CANCELLED` | O (팝업 제거) | **CANCELED 누락 (즉시)** |
| notifyCustomerOnOfficeClosed (L885) | shared_call 생성 | `OFFICE_CLOSED` | **X 미처리** | 나중에 |

### 고객이 모르는 상태 변경

| 상태 전이 | FCM | 파일럿 영향 |
|-----------|-----|------------|
| → ACCEPTED (기사 수락) | 없음 | 나중에 (배정 알림은 옴) |
| → IN_PROGRESS (운행 시작) | 없음 | 나중에 (완료 알림은 옴) |
| → AWAITING_SETTLEMENT | 없음 | 나중에 |
| → CANCELED (관리자 취소) | **없음 (CUST-01)** | **즉시 수정** |

---

## 4. 포인트 흐름 분석

### 적립: 3자 이중 적립 문제 (BUG-D12 → Critical)

| 처리 주체 | 컬렉션명 | 중복 체크 | 트리거 |
|----------|---------|----------|--------|
| **Cloud Functions** | `customerPointTransactions` | phoneNumber+callId+type (3필드) | status→COMPLETED (자동) |
| **Customer App** | `pointTransactions` | callId 쿼리 (트랜잭션 외부) | RIDE_COMPLETED FCM 수신 |
| **Driver App** | `pointTransactions` | callId (1필드) | 정산 완료 버튼 |

- 3자가 **서로 다른 컬렉션명**으로 적립 → 상호 중복 체크 실패
- `customerPoints` 잔액은 마지막 쓰기 승리 (Lost Update)
- **파일럿 영향**: 매 운행 완료마다 발생 → **즉시 수정**

### 사용: 비원자적 차감 (CUST-02)

```
usePoints() 성공 → requestCall() 실패 → 포인트 증발 (복구 없음)
```
- **파일럿 영향**: 양평 네트워크 환경에서 발생 가능 → **즉시 수정**

### 환불: 미구현 (CUST-09)

```
콜 취소 시 pointsUsed 확인/환불 로직 없음
```
- **파일럿 영향**: 관리자가 수동 조정 가능 → **운영으로 커버**

---

## 5. 양평 네트워크 불안정 환경 시나리오

| 시나리오 | 발생 가능성 | 고객 영향 | 대응 | 파일럿 영향 |
|---------|-----------|----------|------|------------|
| 콜 요청 시 네트워크 끊김 | 높음 | 포인트 증발 (CUST-02) | refundPoints() 추가 | **즉시** |
| 기사 배정 FCM 미도착 | 중간 | 상태 고착 ("콜 요청됨" 유지) | Firestore 1회 조회 fallback (CUST-04) | **즉시** |
| 운행 완료 FCM 미도착 | 중간 | 포인트 적립은 됨, 팝업만 안 보임 | 위와 동일 fallback | **즉시** |
| 앱 강제 종료 + 취소 FCM | 낮음 | 취소 알림 미표시 (브로드캐스트만 전송) | CALL_CANCELLED에 showNotification() 추가 | 나중에 |

---

## 6. 보안 분석 (firebase-analyst 검증)

| ID | 심각도 | 이슈 | 악용 조건 | 파일럿 영향 | 규칙 위치 |
|----|--------|------|----------|------------|----------|
| SEC-C01 | Critical | 콜 생성 비인증 (`if true`) | Firestore REST API 직접 호출 | 나중에 (앱 미공개) | rules:132 |
| SEC-C02 | High | customerPoints 소유권 미검증 | 인증+타인 phoneNumber 필요 | 나중에 | rules:258-262 |
| SEC-C03 | High | pointTransactions 위조 생성 | 인증+Firestore SDK 직접 사용 | 나중에 | rules:268-274 |
| SEC-C04 | High | customerInfo FCM 토큰 탈취 | 인증+타인 phoneNumber 필요 | 나중에 | rules:289-293 |
| SEC-C05 | Medium | 콜 취소 본인 미검증 | 인증+타인 callId 필요 | 운영 커버 | rules:147-149 |

> 파일럿 사용자(양평 지역 주민)가 Firestore API를 직접 호출할 기술적 가능성은 극히 낮음.
> 단, **앱 공개 배포 전에 반드시 전수 수정** 필요.

---

## 7. 크로스 검증 결과

| 이슈 | 확인자 | 교차 확인 결과 |
|------|--------|--------------|
| CUST-01 | team-lead + **manager-analyst** | "CANCELED" 상태가 트리거 조건에 없음 확인 |
| CUST-03 | team-lead + **driver-analyst** | 6개 상태 전이 중 Customer FCM은 3개만 확인 |
| CUST-10/BUG-D12 | team-lead + **driver-analyst** + **firebase-analyst** | 3자 이중 적립 확정, 컬렉션명 불일치 확인 |
| SEC-C01~C05 | **firebase-analyst** | firestore.rules 직접 대조 완료 |
| NEW-17 (CUST-11) | **manager-analyst** | 재배차 시 Customer 알림 없음 확인 |

---

## 8. 수정 로드맵 요약

```
파일럿 전 (즉시 4건)
 ├─ CUST-01: onCallCancelledByDriver에 "CANCELED" 추가 (index.ts 1줄)
 ├─ CUST-10/BUG-D12: 포인트 적립 Cloud Functions 일원화 (Driver/Customer 적립 제거)
 ├─ CUST-04: MainViewModel init에 Firestore 활성 콜 1회 조회 추가
 └─ CUST-02: requestCall() catch에 refundPoints() 추가

파일럿 후 (나중에 8건)
 ├─ CUST-03: ACCEPTED/IN_PROGRESS Customer FCM 추가
 ├─ CUST-06: 취소 상태 철자 통일
 ├─ CUST-05, CUST-12: FCM 핸들러 추가 (POINTS_EARNED, OFFICE_CLOSED)
 ├─ CUST-07: FCM 토큰 재시도
 └─ SEC-C01~C04: 보안 규칙 전수 수정

운영으로 커버 (5건)
 ├─ CUST-09: 포인트 환불 (관리자 수동)
 ├─ CUST-11: 재배차 알림 (관리자 전화)
 ├─ CUST-08: WAITING/REQUESTED 표시 (영향 없음)
 ├─ SEC-C05: 콜 취소 본인 검증 (악용 가능성 극저)
 └─ RIDE payload: paymentMethod 미포함 (표시 UI 없음)
```

# 공통 로직 명세 (iOS 기사앱 + 손님앱)

> 이 문서는 Kotlin → Swift 포팅 시 참조할 언어 독립적 로직 명세입니다.
> 상세 데이터 구조는 `CLAUDE.md` 참조.

---

## 1. Firestore 경로

```
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/
  ├── designated_drivers/{uid}     기사
  ├── pickup_drivers/{uid}         픽업기사
  ├── calls/{callId}               콜
  ├── customers/{uid}              고객
  ├── customerInfo/{phone}         고객 FCM 토큰
  ├── settings/attribution         QR/귀속 설정
  ├── managerTokens/{uid}          관리자 FCM 토큰
  ├── dailySettlements/            일일 정산
  └── settlementSessions/          정산 세션

pending_drivers/{uid}              가입 대기
admins/{uid}                       관리자
shared_calls/{callId}              공유콜 (영업시간 외)
presence/drivers/{uid}             Firebase Realtime DB (Presence)
```

---

## 2. 콜 상태 전이

```
WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED
                ↓         ↓            ↓
             (타임아웃)  (거절)      (운행취소)
             → WAITING  → WAITING    → CANCELLED_BY_DRIVER

예약 분기 (운행중 기사에게 다음 콜 약속):
WAITING → RESERVED → ACCEPTED → ... (RESERVED 동안 기사 status=ON_TRIP 유지)
                ↓
             (기사 거절 / 매니저 취소)
             → WAITING

취소: CANCELED (관리자), CANCELLED_BY_DRIVER (기사), CANCELLED_BY_CUSTOMER (고객)
공유: WAITING → SHARED_WAITING → CLAIMED
재배차: HOLD (CF 코드 잔류, 기사앱에서 더 이상 생성 안 함)
```

---

## 3. 기사 상태 전이

```
ONLINE/WAITING → ASSIGNED → PREPARING → ON_TRIP → WAITING (완료 후 복귀)
                                                 → PENDING_CONFIRM (업무마감 시)
```

---

## 4. 정산 공식

```
officeDeposit = totalFare × depositRatio / 100    (사무실 몫)
driverShare   = totalFare - officeDeposit          (기사 몫)
totalCredit   = totalFare - totalCashReceived      (외상)
finalDeposit  = officeDeposit - totalCredit         (최종 납입액)
realDeposit   = 기사가 입력한 실 납입액              (기본값: cashReceived - driverShare)
carryOver     = originalCarryOver - finalDeposit + realDeposit  (이월금)
```

### 결제 방식별 현금 계산
```
"현금"       → cashReceived = fare
"현금+포인트" → cashReceived = 입력값
"이체"/"외상" → cashReceived = 0, creditAmount = fare
"포인트"     → cashReceived = 0, pointsUsed = fare
```

### 날짜 기준 (6시 경계)
```
현재 시각 < 06:00 → 전날 날짜 사용
현재 시각 ≥ 06:00 → 오늘 날짜 사용
```

### 통합 제출 (isIntegration)
```
기존 dailySettlement.status == PENDING_CONFIRM일 때 재제출 → 기존 + 추가분 병합
originalCarryOver, originalTripCount, originalTotalFare, originalRealDeposit 보존
```

---

## 5. FCM 메시지 타입 (6종)

| 타입 | 방향 | 처리 |
|------|------|------|
| `call_assigned` | CF → 기사 | 포그라운드: 팝업, 백그라운드: LockScreen + 알림음 + 진동 |
| `call_cancelled` | CF → 기사 | 항상 시스템 알림 + UI 갱신 |
| `SETTLEMENT_FINALIZED` | CF → 기사 | 정산 확정 알림 |
| `SETTLEMENT_CONFIRMED` | CF → 기사 | 매니저 승인 → 퇴근 가능 |
| `SETTLEMENT_REJECTED` | CF → 기사 | 매니저 반려 → 재제출 필요 |
| `CARRYOVER_TRANSFERRED` | CF → 기사 | 이월금 이체 완료 |

### Delivery ACK
`call_assigned` 수신 시 Cloud Function `acknowledgeNotification` 호출.

---

## 6. Presence 패턴 (Firebase Realtime DB)

```
경로: presence/drivers/{userId}
값: { status: "online" | "background" | "offline", lastSeen: SERVER_TIMESTAMP }

초기화: onDisconnect → offline 설정
포그라운드: online
백그라운드: background
로그아웃: offline + cleanup
```

- CF 스케줄러 (`checkAssignedTimeout`)가 매 1분 presence 조회
- online/background는 동일 취급, offline만 문제로 판단

---

## 7. Firestore 트랜잭션 패턴

모든 상태 변경은 **콜 문서 + 기사 문서를 단일 트랜잭션**으로 업데이트:

```
runTransaction {
  // 1. 콜 문서 현재 상태 확인
  val currentStatus = transaction.get(callRef).status
  if (currentStatus != expectedStatus) throw 예외

  // 2. 콜 상태 업데이트
  transaction.update(callRef, newStatus + timestamps)

  // 3. 기사 상태 업데이트
  transaction.update(driverRef, newDriverStatus)
}
```

### 낙관적 UI 패턴
```
1. 로컬 UI 즉시 업데이트 (prevState 저장)
2. Firestore 트랜잭션 실행 (백그라운드)
3. 성공 → 완료
4. 실패 → prevState로 롤백 + 에러 메시지
```

---

## 8. 로그인 플로우 (기사)

```
1. Firebase Auth signInWithEmailAndPassword
2. pending_drivers/{uid} 체크 → 있으면 "승인 대기 중"
3. collectionGroup("designated_drivers") where authUid == uid
4. 문서 경로에서 provinceId/cityId/officeId 파싱
5. approvalStatus 확인 (APPROVED만 허용)
6. 기사 상태 ONLINE 업데이트
7. FCM 토큰 등록
8. SharedPreferences에 provinceId/cityId/officeId 저장
9. Presence 초기화
10. 활성 콜 로드 (1회 조회)
11. 이월금 리스너 시작
12. 정산 데이터 로드
```

---

## 9. 손님앱 온보딩 플로우

```
1. QR 스캔 → URL에서 provinceId/officeId 추출
2. Anonymous Auth (자동)
3. 약관 동의
4. 전화번호 인증 (Firebase Phone Auth)
5. 프로필 설정 (닉네임, 주소)
6. Firestore에 고객 문서 생성: offices/{o}/customers/{uid}
7. customerInfo/{phone}에 FCM 토큰 저장
```

---

## 10. 고객 포인트/등급

```
등급: BRONZE(3%) → SILVER(5%) → GOLD(7%) → VIP(9%)
적립: fare × earningRate
경로: offices/{o}/customers/{uid} 내 points, grade 필드
```

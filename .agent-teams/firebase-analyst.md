# Firebase Analyst - 지속성 문서

> 마지막 업데이트: 2026-02-24 (2차 분석 라운드 완료)

---

## 1. 담당 범위

- **Firestore Security Rules**: `firestore.rules` (419줄) - 보안 규칙 분석, 앱별 권한 검증
- **Cloud Functions**: `functions/src/index.ts` (4400+줄) + `functions/src/handlers/points.ts` - 26+ 함수 트리거/동작 분석
- **RTDB Rules**: `database.rules.json` - Presence 시스템 보안
- **Firestore Indexes**: `firestore.indexes.json` - 인덱스 구성 검증
- **crashlytics-monitor**: `functions/crashlytics-monitor.js` - 크래시 감지 시스템

---

## 2. 핵심 파일 목록

| 파일 경로 | 역할 | 줄 수 |
|-----------|------|-------|
| `C:\Users\kala1\designated_driver\firestore.rules` | Firestore 보안 규칙 (프로덕션) | 419 |
| `C:\Users\kala1\designated_driver\source\firestore.rules` | 레거시 보안 규칙 (regions 구조) | 399 |
| `C:\Users\kala1\designated_driver\database.rules.json` | RTDB 보안 규칙 | 1 |
| `C:\Users\kala1\designated_driver\firebase.json` | Firebase 설정 (rules 경로 매핑) | - |
| `C:\Users\kala1\designated_driver\firestore.indexes.json` | Firestore 복합 인덱스 | 376 |
| `C:\Users\kala1\designated_driver\functions\src\index.ts` | Cloud Functions 메인 (26+ 함수) | 4400+ |
| `C:\Users\kala1\designated_driver\functions\src\handlers\points.ts` | 고객 포인트 처리 함수 | ~400 |
| `C:\Users\kala1\designated_driver\functions\src\handlers\settlement.ts` | 정산 처리 함수 | - |
| `C:\Users\kala1\designated_driver\functions\lib\index.js` | 컴파일된 배포 코드 (소스와 불일치 주의!) | - |
| `C:\Users\kala1\designated_driver\functions\crashlytics-monitor.js` | 크래시 감지/알림 | 266 |

---

## 3. 크로스 검증 대상

| 팀원 | 검증 내용 | 상태 |
|------|----------|------|
| **detector-analyst** | 보안 규칙 대조 (calls CRUD, shared_calls, designated_drivers), Detector 관리자 인증 확인, isOfficeAdmin 경로, crashlytics 필드 불일치, 14 Callable 인증 미검증, fromCallDetector, WriteBatch 트리거 타이밍 | 완료 |
| **manager-analyst** | 11개 쿼리 패턴 vs 보안 규칙, cancelCall() FCM 검증, 이중 FCM, 비원자적 배차, "대기중"/"거절됨" 데드코드, CANCELED/CANCELLED 불일치, cleanup 누락, onCallStatusChanged 개선안 | 완료 |
| **driver-analyst** | BUG-D09 오탐 확인, BUG-D10 소스/빌드 불일치, BUG-D11 HOLD vs CANCELLED_BY_DRIVER, BUG-D12 포인트 이중 적립, ASSIGNED 거부 경로 없음, collectionGroup 보안, 실시간 리스너 부재, 좀비 콜 | 완료 |

---

## 4. 보안 규칙 전체 문제 목록

### 4-1. CRITICAL 보안 이슈

| ID | 문제 | 위치 | 수정 상태 |
|----|------|------|----------|
| SEC-01 | RTDB 완전 개방 (`"read": true, "write": true`) | `database.rules.json` | **미수정** |
| SEC-02 | calls `allow create: if true` (비인증 콜 생성) | `firestore.rules:132` | **미수정** |
| SEC-03 | 14개 onCall 함수 `request.auth` 검증 없음 | `index.ts` (아래 목록) | **미수정** |

### 4-2. `request.auth == null` 패턴 위치 (서버 전용 쓰기)

이 패턴은 Cloud Functions(Admin SDK)만 쓰기를 허용하는 의도적 설계:

| 위치 | 컬렉션 | 용도 |
|------|--------|------|
| `firestore.rules:47` | provinces | 도/광역시 쓰기 |
| `firestore.rules:327` | settlementSessions | 정산 세션 쓰기 |
| `firestore.rules:351` | shared_calls update | 상태 변경 처리 |
| `firestore.rules:354` | shared_calls delete | 서버만 삭제 |
| `firestore.rules:362` | point_transactions | 포인트 거래 쓰기 |
| `firestore.rules:377` | attributionTokens update | 토큰 상태 변경 |
| `firestore.rules:398` | withdrawalRequests update | 환전 자동 처리 |
| `firestore.rules:412` | emergency_alerts create | 긴급 알림 생성 |

### 4-3. 보안 규칙 부재 컬렉션

| 컬렉션 | 사용하는 코드 | 영향 |
|--------|-------------|------|
| `device_status` | `CrashReportService.kt:272-292` (Detector) | 클라이언트 쓰기 거부됨 (기본 deny) |
| `device_alerts` | `crashlytics-monitor.js` | Admin SDK 우회하지만 필드명 불일치 |
| `notifications` | `saveNotificationStatus()` (index.ts) | Cloud Functions만 쓰기 → 문제 없음 |
| `managerTokens` | Manager App FCM 토큰 저장 | offices 하위, 별도 규칙 없음 |
| `customerInfo` | 고객 정보 | offices 하위, 읽기 isAuthenticated() |
| `pointTransactions` | Driver App 포인트 (BUG-D12) | offices 하위, 규칙 없음 → 기본 deny |
| `customerPointTransactions` | Cloud Functions 포인트 | offices 하위, 규칙 없음 → 기본 deny (Admin SDK 우회) |

### 4-4. 임시 디버깅 규칙 (제거 필요)

| 위치 | 내용 |
|------|------|
| `firestore.rules:78-80` | pickup_drivers read: `isAuthenticated()` (★ 임시) |
| `firestore.rules:89-91` | pickup_drivers write: `isAuthenticated()` (★ 임시) |
| `firestore.rules:129` | calls read: `isAuthenticated()` (★ 임시 디버깅용) |

### 4-5. 14개 Callable Functions 인증 미검증 (SEC-03)

| 함수명 | 라인 | 위험도 | 용도 |
|--------|------|--------|------|
| `acknowledgeNotification` | 114 | LOW | 알림 ACK 위조 |
| `migratePickupDrivers` | 2020 | HIGH | 무단 데이터 마이그레이션 |
| `matchAttribution` | 2307 | MEDIUM | Attribution 매칭 조작 |
| `saveManualAttribution` | 2504 | MEDIUM | 수동 Attribution 저장 |
| `matchByToken` | 2530 | MEDIUM | 토큰 매칭 조작 |
| `claimToken` | 2631 | MEDIUM | 토큰 클레임 조작 |
| `getArchivedStats` | 3540 | MEDIUM | 아카이브 통계 무단 조회 |
| `searchArchivedCalls` | 3711 | MEDIUM | 아카이브 콜 무단 검색 |
| `getOfficeReport` | 3858 | MEDIUM | 사무실 보고서 무단 조회 |
| `manualCheckSettlementDiscrepancy` | 4274 | MEDIUM | 정산 불일치 수동 체크 |
| `notifyDriverAssignment` | 4304 | HIGH | 가짜 배차 FCM 전송 |
| `notifyDriverCancellation` | 4372 | HIGH | 가짜 취소 FCM 전송 |
| `finalizeSettlementAndNotifyDrivers` | 4440 | HIGH | 무단 정산 마감 |
| `sendDriverNotification` | 4539 | HIGH | 임의 기사 알림 전송 |

---

## 5. 비원자적 업데이트 함수 목록

| 함수/동작 | 앱 | 단계 | 원자성 | 위험 |
|-----------|-----|------|--------|------|
| `assignCallToDriver()` | Manager | (1) 콜 update → (2) 기사 status → (3) FCM callable | **NG** | 2단계 실패 시 기사 상태 불일치, 중복 배차 가능 |
| `cancelCall()` | Manager | (1) 콜 CANCELED → (2) 기사 WAITING → (3) FCM callable | **NG** | 2단계 실패 시 기사 ASSIGNED 잔류 (고아 상태) |
| `onSharedCallClaimed` | CF trigger | Transaction 내 콜 복사 + 원본 업데이트, 외부에서 기사 상태 + processed 플래그 | **부분 OK** | Transaction 내부는 원자적, 외부 업데이트는 NG |
| `processCustomerPoints()` | Driver App | Transaction 내 포인트 + 기록 | **OK** (단독) | BUG-D12: Cloud Functions와 이중 실행 문제 |
| `processCustomerPointsOnComplete()` | CF trigger | Transaction 내 포인트 + 기록 | **OK** (단독) | BUG-D12: Driver App과 이중 실행 문제 |
| `createCallWithDriver()` | Detector | (1) 콜 create → (2) 기사 status → (3) FCM callable | **NG** | assignCallToDriver와 동일 패턴 |

---

## 6. Cloud Functions 트리거 매핑 테이블

### 6-1. calls 문서 트리거 (7개)

| 함수명 | 트리거 타입 | 경로 | 조건 | FCM 수신 대상 | FCM type |
|--------|-----------|------|------|-------------|----------|
| `oncallassigned` | onDocumentWritten | `calls/{callId}` | assignedDriverId 변경 | **기사** + 고객 + Manager | `call_assigned` |
| `sendNewCallNotification` | onDocumentCreated | `calls/{callId}` | 새 콜 생성 | **Manager** | `NEW_CALL` |
| `onCallStatusChanged` | onDocumentUpdated | `calls/{callId}` | status 변경 | **Manager only** | `CALL_STATUS_UPDATE` |
| `onCallCancelledByDriver` | onDocumentUpdated | `calls/{callId}` | ASSIGNED/ACCEPTED → HOLD/CANCELLED_BY_DRIVER | **고객 only** | `CALL_CANCELLED` |
| `onCallCompletedUpdateSettlement` | onDocumentUpdated | `calls/{callId}` | → COMPLETED | 없음 (정산 처리) | - |
| `notifyCustomerOnPhoneCall` | onDocumentCreated | `calls/{callId}` | 새 콜 (앱 고객) | **고객** | `CALL_RECEIVED` |
| `notifyCustomerOnComplete` | onDocumentUpdated | `calls/{callId}` | → COMPLETED | **고객** + 포인트 적립 | `RIDE_COMPLETED` |

### 6-2. shared_calls 트리거 (5개)

| 함수명 | 트리거 타입 | 조건 | 동작 |
|--------|-----------|------|------|
| `onSharedCallCreated` | onDocumentCreated | 새 공유콜 | 대상 지역 Manager FCM |
| `notifyCustomerOnOfficeClosed` | onDocumentCreated | 새 공유콜 | 고객에게 마감 알림 FCM |
| `onSharedCallClaimed` | onDocumentUpdated | OPEN → CLAIMED | 콜 복사 + 기사 ASSIGNED + FCM |
| `onSharedCallCancelledByDriver` | onDocumentUpdated | 기사 취소 | 원본 콜 복구 + 공유콜 재오픈 |
| `onSharedCallCompleted` | onDocumentUpdated | → COMPLETED | 공유콜 상태 동기화 |

### 6-3. 기타 트리거

| 함수명 | 트리거 타입 | 경로 | 동작 |
|--------|-----------|------|------|
| `onDriverSignupRequest` | onDocumentCreated | `pending_drivers/{id}` | Manager FCM (가입 요청) |
| `onDriverStatusChange` | onDocumentUpdated | `designated_drivers/{id}` | Manager FCM (상태 변경) |

### 6-4. Callable Functions (14개)

| 함수명 | 라인 | 호출자 | 동작 |
|--------|------|--------|------|
| `acknowledgeNotification` | 114 | Driver App | 알림 ACK 처리 |
| `notifyDriverAssignment` | 4304 | Manager/Detector | 기사 배차 FCM |
| `notifyDriverCancellation` | 4372 | Manager | 기사 취소 FCM |
| `finalizeSettlementAndNotifyDrivers` | 4440 | Manager | 정산 마감 + 기사 FCM |
| `sendDriverNotification` | 4539 | Manager | 기사 일반 알림 |
| `getArchivedStats` | 3540 | Manager | 아카이브 통계 조회 |
| `searchArchivedCalls` | 3711 | Manager | 아카이브 콜 검색 |
| `getOfficeReport` | 3858 | Manager | 사무실 보고서 |
| `manualCheckSettlementDiscrepancy` | 4274 | Manager | 정산 불일치 체크 |
| `migratePickupDrivers` | 2020 | - | 데이터 마이그레이션 |
| `matchAttribution` | 2307 | - | Attribution 매칭 |
| `saveManualAttribution` | 2504 | - | 수동 Attribution |
| `matchByToken` | 2530 | - | 토큰 매칭 |
| `claimToken` | 2631 | - | 토큰 클레임 |

### 6-5. Scheduled Functions

| 함수명 | 스케줄 | 동작 |
|--------|--------|------|
| `autoFinalizeSettlementSessions` | (handlers/settlement.ts) | 정산 자동 마감 |

### 6-6. 이중 FCM 문제

**배차 시 기사에게 이중 FCM:**
1. `oncallassigned` (trigger, line 350) → `call_assigned` FCM (Presence 체크 포함)
2. `notifyDriverAssignment` (callable, line 4304) → `call_assigned` FCM (Presence 체크 없음)

**취소 시 중복 없음 (정상):**
- `onCallStatusChanged` → Manager에게만 `CALL_STATUS_UPDATE`
- `notifyDriverCancellation` → 기사에게만 `call_cancelled`

---

## 7. 발견한 이슈 목록

### CRITICAL

| ID | 제목 | 파일:라인 | 발견자 | 검증자 |
|----|------|----------|--------|--------|
| SEC-01 | RTDB 완전 개방 | `database.rules.json` | firebase-analyst | 전원 동의 |
| SEC-02 | calls create: if true | `firestore.rules:132` | firebase-analyst | 전원 동의 |
| SEC-03 | 14개 Callable 인증 미검증 | `index.ts` (14곳) | firebase-analyst + detector-analyst | 전원 동의 |

### HIGH

| ID | 제목 | 파일:라인 | 발견자 | 검증자 |
|----|------|----------|--------|--------|
| BUG-D10 | 공유콜 "배차중" 한글 상태 (프로덕션) | `lib/index.js:953` (소스 수정 완료: `src/index.ts:1198`) | driver-analyst | firebase-analyst 빌드 불일치 확인 |
| BUG-D12 | **3-Way** 포인트 이중 적립 (Driver App + Customer App + CF 각각 독립 적립) | `DriverViewModel.kt:1120` + `PointService.kt:110` + `handlers/points.ts:260` | driver-analyst + firebase-analyst | 3자 검증 완료 |
| NEW-02 | 이중 FCM (oncallassigned + notifyDriverAssignment) | `index.ts:350, 4304` | firebase-analyst | manager-analyst 동의 |
| NEW-04 | reopenSharedCall() 보안 규칙 거부 | `firestore.rules:345-351` | firebase-analyst | 전원 동의 |
| NEW-05 | deleteSharedCall() 보안 규칙 거부 | `firestore.rules:354` | firebase-analyst | 전원 동의 |
| NEW-14 | **CRITICAL로 상향** - 공유콜 ASSIGNED→HOLD 보안 규칙 거부 → 기사+콜 탈출불가 잠김 | `firestore.rules:155-164` vs `DriverViewModel cancelTrip()` | firebase-analyst + driver-analyst | 교차 검증 완료, driver-analyst CRITICAL 상향 |

### MEDIUM

| ID | 제목 | 파일:라인 | 발견자 | 검증자 |
|----|------|----------|--------|--------|
| NEW-06 | 3개 임시 디버깅 규칙 | `firestore.rules:78-80, 89-91, 129` | firebase-analyst | 전원 동의 |
| NEW-07 | 비원자적 배차 | `DashboardViewModel.kt:689-789` | firebase-analyst + manager-analyst | 교차 검증 완료 |
| NEW-08 | cancelCall() 비원자적 + 재시도 없음 | `DashboardViewModel.kt:809-868` | manager-analyst | firebase-analyst 검증 |
| NEW-09 | crashlytics-monitor 3-layer 필드 불일치 | `crashlytics-monitor.js:60, 151` | firebase-analyst | detector-analyst 확인 |
| NEW-13 | CANCELED vs CANCELLED 철자 불일치 + cleanup 누락 | `CallStatus.kt:15-16`, `index.ts:3239` | firebase-analyst | manager-analyst cleanup 확인 |
| NEW-15 | ASSIGNED 콜 타임아웃 메커니즘 부재 | Cloud Functions 전체 | firebase-analyst + driver-analyst | 교차 검증 완료 |

### LOW

| ID | 제목 | 파일:라인 | 발견자 | 검증자 |
|----|------|----------|--------|--------|
| NEW-10 | "대기중"/"거절됨" 한글 데드코드 | `DashboardViewModel.kt` | manager-analyst | firebase-analyst (Cloud Functions 표시 전용 확인) |
| NEW-11 | fromCallDetector fallback 누락 | `DispatchActivity.kt` | detector-analyst | firebase-analyst (CF 미사용 확인) |
| NEW-12 | Detector 평문 비밀번호 | `LoginViewModel.kt:130-133` | detector-analyst | firebase-analyst (클라이언트 측) |

### Customer App 전용 이슈 (신규 발견)

| ID | 심각도 | 제목 | 파일:라인 | 발견자 |
|----|--------|------|----------|--------|
| SEC-C01 | **CRITICAL** | 콜 생성 인증 불필요 (`allow create: if true`) | `firestore.rules:132` | firebase-analyst |
| SEC-C02 | **High** | customerPoints 소유권 검증 없음 (다른 고객 포인트 조작 가능) | `firestore.rules:258-262` | firebase-analyst |
| SEC-C03 | **High** | pointTransactions 위조 거래 생성 가능 | `firestore.rules:268-274` | firebase-analyst |
| SEC-C04 | **High** | customerInfo FCM 토큰 탈취 가능 (다른 고객 FCM 덮어쓰기) | `firestore.rules:289-293` | firebase-analyst |
| SEC-C05 | **Medium** | 콜 취소 시 본인 콜 미검증 | `firestore.rules:147-149` | firebase-analyst |
| NEW-C01 | **High** | 포인트 사용 후 콜 생성 실패 시 포인트 복구 없음 | `MainViewModel.kt:243-297` | firebase-analyst |
| NEW-C02 | **Medium** | OFFICE_CLOSED FCM 미처리 (앱에서 무시) | `MyFirebaseMessagingService.kt:35-97` | firebase-analyst |
| NEW-C03 | **Low** | POINTS_EARNED FCM 미처리 (시스템 알림만 표시) | `MyFirebaseMessagingService.kt:35-97` | firebase-analyst |
| NEW-C04 | **Medium** | ACCEPTED/IN_PROGRESS 상태 변경 시 고객 알림 없음 | `index.ts (onCallStatusChanged)` | firebase-analyst |

### 오탐 (False Positive)

| ID | 제목 | 이유 |
|----|------|------|
| BUG-D09 | departure_set 필드 불일치 | 양쪽 동일 확인 (DriverViewModel.kt + CallInfo.kt) |
| - | isOfficeAdmin nested path 불일치 | top-level admins 사용 확인 (firestore.rules:18) |

---

## 8. 수정 이력

| 날짜 | 변경 내용 | 파일 | 상태 |
|------|----------|------|------|
| 2026-02-24 | `notifyDriverCancellation` Callable 함수 추가 | `index.ts:4372-4431` | 소스 반영됨, 배포 상태 미확인 |
| 2026-02-24 | `onSharedCallClaimed` "배차중" → "ASSIGNED" 수정 | `index.ts:1198` | 소스 반영됨, **빌드/배포 미완료** (lib/index.js:953 여전히 "배차중") |
| - | lib 최종 빌드: 2026-02-19 00:08 | `lib/index.js` | **5일 전 빌드, firebase deploy 필요** |
| - | src 최종 수정: 2026-02-24 21:12 | `src/index.ts` | 최신 |

---

## 9. 크로스 검증 요청/결과 요약

### 받은 검증 요청

| 발신자 | 내용 | 결과 |
|--------|------|------|
| team-lead | doc.id == authUid 확인 | 확인됨 (SignUpViewModel + PendingDriversViewModel) |
| team-lead | notifyDriverCancellation 중복/보안 확인 | 중복 없음, 인증 미검증 발견 |
| manager-analyst | 11개 쿼리 패턴 vs 보안 규칙 | 2개 CRITICAL 실패 (reopenSharedCall, deleteSharedCall) |
| manager-analyst | cancelCall() FCM 경로 확인 | onCallStatusChanged는 Manager만, notifyDriverCancellation은 기사만 |
| manager-analyst | onCallStatusChanged 개선안 검토 | CANCELED/CANCELLED/CANCELLED_BY_DRIVER 3가지 커버 필요 |
| driver-analyst | Firestore 오프라인 정책 | 기본 100MB LRU, 실시간 리스너 없음 확인 |
| driver-analyst | BUG-D10 "배차중" 확인 | 소스는 수정됨, 빌드 미반영 확인 |
| driver-analyst | BUG-D11 HOLD vs CANCELLED_BY_DRIVER | 보안 규칙 전환 경로 상세 제공 |
| driver-analyst | BUG-D12 포인트 이중 적립 검증 | 완전 검증 + Race Condition 추가 분석 |
| driver-analyst | collectionGroup 보안 | 보안 문제 없음 (authUid 소유권 검증 존재) |
| driver-analyst | 콜 문서 실시간 리스너 여부 | 리스너 없음 (manager-analyst 경유 확인) |
| detector-analyst | 보안 규칙 대조 (calls CRUD 등) | 전체 PASS (Detector는 관리자 인증) |
| detector-analyst | onSharedCallCreated 중복 방지 | idempotency 없지만 실질 영향 낮음 |
| detector-analyst | isOfficeAdmin 경로 불일치 | 오탐 (top-level admins 사용) |
| detector-analyst | crashlytics 필드 불일치 | 3-layer 불일치 확인 |

### 보낸 검증 요청

| 수신자 | 내용 | 결과 |
|--------|------|------|
| driver-analyst | Driver App 콜 문서 실시간 리스너 여부 | 없음 (비용 절감으로 제거됨) |
| driver-analyst | "배차중" 상태 Driver App 인식 여부 | Constants.kt에 없음 → UNKNOWN |

---

## 10. 미해결 이슈 Next Step

### 즉시 필요

| 항목 | 조치 | 담당 |
|------|------|------|
| BUG-D10 | `firebase deploy --only functions` 실행 | 개발자 |
| SEC-01 | `database.rules.json` Presence 경로 제한 규칙 작성 | 개발자 |
| SEC-02 | `firestore.rules:132` `if true` → `isOfficeAdmin() \|\| isAnyAdmin()` 변경 | 개발자 |
| SEC-03 | 14개 onCall 함수에 `request.auth` 검증 추가 | 개발자 |

### 중기 과제

| 항목 | 조치 | 담당 |
|------|------|------|
| BUG-D12 | Driver App `processCustomerPoints()` 제거 + Customer App `awardPointsForCompletedRide()` 제거, Cloud Functions 단일 처리 | driver-analyst + firebase-analyst |
| NEW-02 | `notifyDriverAssignment` Callable 제거 또는 `oncallassigned` 트리거에서 기사 FCM 제거 (하나로 통일) | 개발자 |
| NEW-04/05 | reopenSharedCall/deleteSharedCall → Cloud Functions Callable로 이관 | 개발자 |
| NEW-13 | CANCELLED 철자 통일 + cleanup 함수에 "CANCELED" 추가 | 개발자 |
| NEW-14 | Driver App `cancelTrip()`에서 공유콜 구분 → `CANCELLED_BY_DRIVER` 사용 | driver-analyst |
| NEW-06 | 임시 디버깅 규칙 3개 제거 | 개발자 |

### 장기 과제

| 항목 | 조치 | 담당 |
|------|------|------|
| NEW-07/08 | 비원자적 업데이트 → WriteBatch/Transaction 전환 | 개발자 |
| NEW-09 | crashlytics-monitor.js 필드명 통일 (provinceId) | 개발자 |
| NEW-15 | ASSIGNED 콜 타임아웃 스케줄러 추가 | 개발자 |
| - | `onCallStatusChanged`에 기사 상태 자동 복구 로직 추가 | 개발자 |
| - | 기존 Firestore "CANCELED" → "CANCELLED" 데이터 마이그레이션 | 개발자 |
| - | `device_status`/`device_alerts` 보안 규칙 추가 | 개발자 |
| - | `pointTransactions`/`customerPointTransactions` 보안 규칙 명시 | 개발자 |
| SEC-C02 | customerPoints에 `resource.data.phoneNumber == request.auth.phone` 또는 customerId → uid 매핑 검증 추가 | 개발자 |
| SEC-C04 | customerInfo에 소유권 검증 추가 (phoneNumber 기반 인증 필요) | 개발자 |
| NEW-C01 | requestCall() 실패 시 포인트 복구 로직 추가 또는 콜 생성+포인트 차감을 Cloud Function으로 일원화 | 개발자 |
| NEW-C02 | Customer App에 OFFICE_CLOSED FCM 처리 추가 | 개발자 |

---

## 11. 보안 규칙 Helper 함수 참조

```javascript
// firestore.rules 핵심 함수 (line 7-37)
function isAuthenticated()     // request.auth != null
function isOwner(userId)       // auth.uid == userId
function isOfficeAdmin(pId, cId, oId)  // top-level admins/{uid} 존재 + associatedIds 일치
function isAnyAdmin()          // top-level admins/{uid} 존재
function isHeadManager()       // admins/{uid} + role in ['HEAD_MANAGER', 'SUPER_ADMIN']
```

**중요:** `isOfficeAdmin()`은 nested path가 아닌 **top-level `admins/{uid}`** 문서를 확인합니다 (line 18).

---

## 12. Firestore 데이터 구조 참조

```
/ (root)
├── admins/{uid}                          ← 관리자 (top-level, 모든 앱 공용)
├── pending_drivers/{driverId}            ← 가입 대기 기사
├── shared_calls/{callId}                 ← 공유 콜
├── point_transactions/{txId}             ← 포인트 거래 (top-level, 사용처 불명)
├── attributionTokens/{tokenId}           ← QR Attribution
├── emergency_alerts/{alertId}            ← 긴급 알림
├── provinces/{pId}/
│   ├── cities/{cId}/
│   │   ├── offices/{oId}/
│   │   │   ├── calls/{callId}            ← 콜 문서
│   │   │   ├── designated_drivers/{dId}  ← 기사 (doc.id == authUid)
│   │   │   ├── pickup_drivers/{dId}      ← 픽업 기사
│   │   │   ├── admins/{uid}              ← ★ 사용되지 않음 (nested, 레거시?)
│   │   │   ├── managerTokens/{tokenId}   ← Manager FCM 토큰
│   │   │   ├── customerInfo/{phone}      ← 고객 정보
│   │   │   ├── customerPoints/{phone}    ← 고객 포인트 잔액
│   │   │   ├── pointTransactions/{txId}  ← Driver App + Customer App 포인트 기록 (BUG-D12 3-way)
│   │   │   ├── customerPointTransactions/{txId} ← CF 포인트 기록 (BUG-D12)
│   │   │   ├── settlements/{sId}         ← 정산
│   │   │   └── settlementSessions/{date} ← 정산 세션
│   │   └── withdrawalRequests/{rId}      ← 환전 신청
```

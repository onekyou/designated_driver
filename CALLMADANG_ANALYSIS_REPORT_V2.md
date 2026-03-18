# 콜마당(CallMadang) 2차 종합 분석 보고서

> **분석일**: 2026-02-24
> **분석 방법**: Agent Teams v2 - 4명 팀원 간 직접 크로스 검증 + 5가지 시나리오 코드 레벨 시뮬레이션
> **팀 구성**: detector-analyst, manager-analyst, driver-analyst, firebase-analyst + team-lead
> **1차 대비 개선**: 팀원 간 직접 SendMessage 소통, 상호 코드 대조, 시나리오 기반 데이터 흐름 추적

---

## 목차

1. [1차 CROSS-01~07 크로스 검증 결과](#1-1차-cross-0107-크로스-검증-결과)
2. [시나리오별 데이터 흐름 추적](#2-시나리오별-데이터-흐름-추적)
3. [v2에서 새로 발견된 문제](#3-v2에서-새로-발견된-문제)
4. [심각도 재평가 (전체 이슈 통합)](#4-심각도-재평가-전체-이슈-통합)
5. [수정 권장사항 (다른 앱 영향 포함)](#5-수정-권장사항-다른-앱-영향-포함)

---

## 1. 1차 CROSS-01~07 크로스 검증 결과

### CROSS-01: 기사 상태 불일치 → [확인됨 + 확대] 4자 불일치로 격상

**1차 분석**: Detector(ON_TRIP) vs Manager(ASSIGNED) 2자 불일치
**2차 크로스 검증**: 4자 불일치 확정

| 설정 주체 | 기사 상태 Firestore 값 | DriverStatus enum 매핑 | 코드 위치 |
|-----------|----------------------|----------------------|----------|
| Manager 배차 | `"ASSIGNED"` | ASSIGNED (정상) | DashboardViewModel.kt:752 |
| Detector 배차 | `"ON_TRIP"` | ON_TRIP (수락 건너뜀) | DispatchActivity.kt:172 |
| Driver App 수락 | `"PREPARING"` | **UNKNOWN** (enum 미등록) | DriverViewModel.kt:442 |
| Cloud Functions 공유콜 | `"배차중"` (한글) | **UNKNOWN** (enum 미등록) | index.ts:1198 |

**추가 발견 (manager-analyst)**:
- Manager UI: Detector 배차 기사 = "운행중"(빨간색), Manager 배차 기사 = "배차완료"(주황색) → 시각적 불일치
- assignedDriverId 처리: Manager는 `authUid` 필수, Detector는 `authUid.ifEmpty { driver.id }` 폴백 → ID 불일치 가능
- assignedTimestamp: Manager는 `Timestamp.now()` (클라이언트), Detector는 `FieldValue.serverTimestamp()` (서버)

**심각도**: Critical → **Critical 유지 (범위 확대)**

---

### CROSS-02: CallStatus enum 불일치 → [확인됨] 4개 앱 매핑 테이블 완성

| Firestore 값 | Manager (15개) | Driver (7개) | Detector (6개) | Customer |
|--------------|-------|--------|-----------|---------|
| WAITING | O | O | O | FCM 기반 |
| ASSIGNED | O | O | X (하드코딩) | FCM 기반 |
| ACCEPTED | O | O | X | FCM 기반 |
| IN_PROGRESS | O (`"IN_PROGRESS"`) | O | X | FCM 기반 |
| AWAITING_SETTLEMENT | O | O | X | FCM 기반 |
| COMPLETED | O | O | O | FCM 기반 |
| CANCELLED | O | O (`"CANCELED"` 철자 다름) | O | FCM 기반 |
| CANCELED | O (CANCELLED과 중복) | X | X | - |
| HOLD | O | X (쓰기만, enum 없음) | X | - |
| PENDING | O | X | O | - |
| SHARED_WAITING | O | X | X | - |
| CLAIMED | O | X | X | - |
| PICKUP_COMPLETE | O | X | X | - |
| SHARED_OUT | O | X | X | - |
| UNKNOWN | O | X | X | - |
| MATCHED | X | X | O (레거시, 미사용) | - |
| DISPATCHED | X | X | O (레거시, 미사용) | - |

**추가 발견**:
- Manager `Constants.kt`에 `STATUS_IN_PROGRESS = "INPROGRESS"` (언더스코어 없음) vs `CallStatus.IN_PROGRESS = "IN_PROGRESS"` (있음) → 데드코드라 실제 영향 없음
- Driver App이 `"HOLD"`를 Firestore에 쓰지만 자기 enum에 없어서 다시 읽을 때 매핑 실패
- DriverStatus에서도: Manager에 `PREPARING` 있음, Driver에 없음(하드코딩). Driver에 `ACCEPTED` 있음, Manager에 없음.

**심각도**: Critical → **Critical 유지**

---

### CROSS-03: Customer App FCM 100% 의존 → [확인됨] 변경 없음

- 실시간 리스너(`monitorCallStatus_DEPRECATED()`) 비활성화 확인
- FCM만으로 상태 업데이트 → FCM 누락 시 고객 앱 상태 멈춤
- Customer App 전담 에이전트가 없었으므로 추가 크로스 검증은 미수행

**심각도**: Critical → **Critical 유지**

---

### CROSS-04: 보안 규칙 개방 → [확인됨 + 확대] 예상보다 훨씬 심각

**1차 분석**: RTDB 개방, calls create: true, isAuthenticated() 임시 코드
**2차 크로스 검증 (firebase-analyst 주도, 전 팀원 쿼리 패턴 대조)**:

#### Firestore 보안 규칙 문제 전체 목록

| 컬렉션 | 문제 | 심각도 |
|--------|------|--------|
| RTDB 전체 | `read/write: true` 완전 개방 | Critical |
| calls (create) | `allow create: if true` 인증 없이 생성 | Critical |
| calls (read) | `isAuthenticated()` 임시 디버깅 → 다른 사무실 콜 열람 | Critical |
| designated_drivers (collectionGroup) | `isAuthenticated()` → 전 사무실 기사 정보 | Critical |
| offices (collectionGroup) | `isAuthenticated()` → 전 사무실 정보 | Critical |
| points, point_transactions | `request.auth == null` → 비인증 사용자 포인트 조작 | Critical |
| customerPoints, customerInfo | `isAuthenticated()` read + `request.auth == null` write | High |
| attributions (create) | `allow create: if true` 무제한 생성 | High |
| attributionTokens (read) | `\|\| true` → 앞 조건 무의미 | High |
| calls 취소 규칙 | `isAuthenticated()`만 → 아무 사용자가 아무 콜 취소 가능 (소유권 미검증) | High |
| shared_calls (update/delete) | `request.auth == null` → 비인증 접근 | High |
| admins (read) | `request.auth == null` → 전체 관리자 정보 노출 | High |

#### `request.auth == null` 패턴 영향 (18곳 이상)
Admin SDK용으로 의도되었으나 실제로는 비인증 클라이언트에게도 허용. REST API로 직접 접근 시:
- 포인트 잔액 조작 가능
- 정산 데이터 변조 가능
- 관리자 정보 조회 가능
- 공유콜 삭제 가능

#### 보안 규칙 누락 컬렉션
`notifications`, `device_status`, `device_alerts`, `tokenRefreshRequests`, `customerPointTransactions` → 클라이언트 접근 시 거부

#### HTTP 함수 인증 없음
`deleteEmergencyAlerts`, `cleanupOldNotifications` → URL만 알면 누구나 호출, 긴급 알림 전체 삭제 가능

#### 콜 격리 검증 결과 (시나리오 5)
- Cloud Functions FCM: 사무실별 올바르게 격리됨 (OK)
- Firestore 보안 규칙: **격리 실패** (다수 컬렉션)
- RTDB: **완전 개방**
- Detector: collectionGroup 미사용, nested 경로만 → Detector 자체는 격리 OK

**심각도**: Critical → **Critical 유지 (범위 대폭 확대)**

---

### CROSS-05: 내장 Detector vs 독립 Detector 코드 동기화 실패 → [확인됨]

detector-analyst가 라인 단위 비교 완료:

| 기능 | 독립 Detector | 내장 Detector (Manager) |
|------|-------------|----------------------|
| wasRinging 플래그 | O (line 49) | X → 발신전화 오감지 |
| ExcludeNumberManager | O (line 65) | X → 개인번호 콜 생성 |
| SharedPrefs 이름 | `"detector_config"` | `"call_manager_prefs"` |
| origin 표시 | `fromCallDetector: true` | `fromCallManager: true` |
| 로컬 DB 저장 | X | O (Room DB) |
| customerGrade 필드 | X | O |
| on/off 토글 | X (항상 작동) | `call_detection_enabled` |
| IDLE isIncoming 체크 | X (wasRinging 대체) | O (wasRinging 없어 불완전) |
| SMS 자동 발송 | 비활성화 (주석) | **활성 상태** |
| CallReceiver IDLE 초기화 | 즉시 (synchronized) | 1초 지연 (Handler.postDelayed) |

**심각도**: Critical → **Critical 유지**

---

### CROSS-06: 비원자적 Firestore 업데이트 → [확인됨] 전 앱 정밀 대조 완료

firebase-analyst가 전 앱 원자성을 대조:

| 앱 | 함수 | 원자성 | 위험도 |
|----|------|--------|--------|
| Detector | 배차 (콜+기사+FCM) | 비원자적 3개 | **HIGH** |
| Manager | assignCallToDriver (콜+로컬DB+기사) | 비원자적 3개 | **HIGH** |
| Manager | cancelCall | 비원자적 1개 (기사 복구 자체가 없음) | **CRITICAL** |
| Manager | claimSharedCall | **runTransaction** | OK |
| Driver | acceptCall | **runTransaction** | OK |
| Driver | cancelTrip (콜+기사) | 비원자적 2개 | **HIGH** |
| Driver | startDriving (콜+기사) | 비원자적 2개 | **MEDIUM** |
| Driver | confirmAndFinalizeTrip (콜+기사+포인트) | 비원자적 3+개 | **HIGH** |
| Cloud Functions | processCustomerPoints | **runTransaction** | OK |

**심각도**: High → **High 유지 (범위 구체화)**

---

### CROSS-07: rejectCall + cancelCall 문제 → [확인됨 + 확대] 3중 차단 발견

**1차 분석**: rejectCall 비어있음 + cancelCall 기사 미복구
**2차 크로스 검증**: 3중 차단 확인

1. **Driver App**: `rejectCall()` 본문 비어있음 (DriverViewModel.kt:459-462)
2. **Manager**: `DRIVER_REJECT` FCM을 `ignoredTypes`로 무시 (MyFirebaseMessagingService.kt:97)
3. **Firestore 보안 규칙**: ASSIGNED → HOLD 전환 규칙 없음 (ACCEPTED → HOLD만 허용)

→ **기사가 거절하려 해도: 코드가 비어있고, 설령 구현해도 보안 규칙이 차단하고, 설령 통과해도 Manager가 FCM을 무시함**

**추가: BUG-D08 (driver-analyst + manager-analyst 크로스 검증으로 발견)**
Manager cancelCall() 실행 시:
1. 콜 → CANCELED
2. 기사 상태 → **변경 없음** (ASSIGNED 유지)
3. 기사에게 FCM → **없음**
4. Driver App 실시간 리스너 → **없음**
5. 기사가 수락 클릭 → 낙관적 업데이트로 UI는 ACCEPTED → Firestore transaction은 skip (CANCELED이므로)
6. **기사 UI ≠ 서버 상태, 앱 재시작 전까지 복구 불가**

**심각도**: Critical → **Critical 유지 (3중 차단 + BUG-D08 추가)**

---

## 2. 시나리오별 데이터 흐름 추적

### 시나리오 1: 정상 흐름 (Manager 배차)

```
[1] 고객 전화 → CallReceiver → CallDetectorService → Firestore calls 생성 (WAITING)
    → Cloud Function: sendNewCallNotification → 관리자 FCM "NEW_CALL"
    → Cloud Function: notifyCustomerOnPhoneCall → 고객 FCM "CALL_RECEIVED" (isAppCustomer일 때)

[2] Manager가 기사 배차
    → DashboardViewModel.assignCallToDriver()
    → Firestore calls: status="ASSIGNED", assignedDriverId, assignedDriverName, assignedDriverPhone
    → Firestore designated_drivers: status="ASSIGNED"
    → Room DB: 로컬 업데이트
    → Cloud Function: oncallassigned → 기사 FCM "call_assigned" + 고객 FCM "DRIVER_ASSIGNED"
    → Cloud Function: onCallStatusChanged → 매니저 FCM "CALL_STATUS_UPDATE" (중복!)

[3] 기사 수락
    → DriverViewModel.acceptCall() [runTransaction]
    → Firestore calls: status="ACCEPTED"
    → Firestore designated_drivers: status="PREPARING" (⚠️ enum 미등록)
    → Cloud Function: onCallStatusChanged → 매니저 FCM

[4] 운행 시작
    → DriverViewModel.startDriving() [비원자적]
    → Firestore calls: status="IN_PROGRESS"
    → Firestore designated_drivers: status="ON_TRIP"

[5] 운행 완료
    → DriverViewModel.completeCall() [비원자적]
    → Firestore calls: status="AWAITING_SETTLEMENT"
    → (기사 상태 미변경 - ON_TRIP 유지) ⚠️ AWAITING_SETTLEMENT 감지 갭

[6] 정산 확정
    → DriverViewModel.confirmAndFinalizeTrip() [비원자적]
    → Firestore calls: status="COMPLETED", 정산 데이터
    → Firestore designated_drivers: status="WAITING"
    → Cloud Function: notifyCustomerOnComplete → 고객 FCM "RIDE_COMPLETED"
    → Cloud Function: 포인트 적립 (customerPointTransactions)
    → Driver App: 포인트 적립 (pointTransactions) ⚠️ 이중 적립 가능
```

**발견된 끊김/불일치 지점:**
- [2]에서 매니저에게 중복 FCM (oncallassigned + onCallStatusChanged)
- [3]에서 기사 상태 "PREPARING"이 DriverStatus enum에 미등록
- [5]에서 AWAITING_SETTLEMENT를 Manager가 감지 못함
- [6]에서 포인트 이중 적립 가능 (Driver App + Cloud Functions가 다른 컬렉션에)

---

### 시나리오 2: Detector 배차 흐름

```
[1] 고객 전화 → CallReceiver → CallDetectorService → Firestore calls 생성 (WAITING)
    (시나리오 1과 동일)

[2] Detector가 직접 배차 (DispatchActivity)
    → Firestore calls: status="ASSIGNED", assignedDriverId, assignedDriverName, ...
    → Firestore designated_drivers: status="ON_TRIP" ⚠️ (Manager는 "ASSIGNED")
    → Cloud Function: oncallassigned → 기사 FCM "call_assigned"
    → DispatchActivity.finish() ⚠️ Firestore 업데이트 결과 대기 안 함

[3] 기사 수락
    → DriverViewModel.acceptCall() [runTransaction]
    → 콜 status ASSIGNED → ACCEPTED: OK
    → 기사 status: "PREPARING" (⚠️ 이미 "ON_TRIP"이었는데 "PREPARING"으로 역행)
```

**시나리오 1과의 Firestore 데이터 차이:**

| 필드 | Manager 배차 | Detector 배차 |
|------|-------------|-------------|
| 기사 status | `"ASSIGNED"` | `"ON_TRIP"` |
| assignedDriverId 소스 | `driverInfo.authUid` (필수) | `driver.authUid.ifEmpty { driver.id }` (폴백) |
| assignedTimestamp | `Timestamp.now()` (클라이언트) | `FieldValue.serverTimestamp()` (서버) |
| fromCallDetector | false | true |
| fromCallManager | true | false |
| customerName FCM | 포함 | 빈 문자열 |
| 배차 결과 대기 | 대기 후 UI 반영 | **대기 안 함** (즉시 finish) |
| 원자성 | 비원자적 (동일) | 비원자적 (동일) |

---

### 시나리오 3: 기사 취소 → 재배차

```
경로 A: 기사가 직접 취소 (Driver App)
  → DriverViewModel.cancelTrip() [비원자적]
  → Firestore calls: status="HOLD"
  → Firestore designated_drivers: status="WAITING"
  → Cloud Function: onCallCancelledByDriver → 고객 FCM "CALL_CANCELLED"
  → Manager: activeCallsListener가 HOLD 감지 → UI 반영
  → 관리자가 다른 기사에게 재배차: ??? ⚠️ HOLD→WAITING→ASSIGNED 경로 없음

경로 B: 관리자가 취소 (Manager)
  → DashboardViewModel.cancelCall()
  → Firestore calls: status="CANCELED"
  → Firestore designated_drivers: ⚠️ 상태 미변경 (ASSIGNED 유지)
  → 기사에게 FCM: ⚠️ 없음
  → Driver App: ⚠️ 실시간 리스너 없음, 취소 인지 불가
  → 기사가 수락 시도: 낙관적 UI=ACCEPTED, Firestore=skip → ghost state

경로 C: 기사가 거절 (rejectCall)
  → DriverViewModel.rejectCall(): ⚠️ 본문 비어있음
  → Firestore: 변경 없음
  → Manager에 DRIVER_REJECT FCM: ⚠️ ignoredTypes로 무시
  → 보안 규칙: ASSIGNED→HOLD ⚠️ 규칙 없음
  → 결론: 거절 자체가 불가능 (3중 차단)
```

**재배차 가능성 검증:**
- HOLD 상태 콜을 WAITING으로 되돌리는 함수/UI: **없음**
- CANCELED 상태 콜을 재활용하는 기능: **없음**
- **현재 유일한 재배차 방법**: 콜 삭제 후 새 콜 수동 생성

---

### 시나리오 4: 네트워크 불안정

```
[1] 기사가 운행 중 네트워크 끊김
    → Firestore SDK 오프라인 캐시 활성화 (기본값)
    → 로컬 쓰기는 캐시에 저장됨
    → 서버 동기화 보류

[2] 오프라인 상태에서 상태 변경 시도
    → completeCall() 호출 → 로컬 캐시에 AWAITING_SETTLEMENT 저장
    → await()가 무한 대기 또는 타임아웃
    → ⚠️ 오프라인 감지/알림 코드 없음 (NetworkMonitor 미사용)

[3] 그 사이 Manager가 콜 취소
    → Firestore: CANCELED
    → 기사에게 FCM: 전송 시도 → 오프라인이라 미도달
    → Presence: RTDB에 offline 기록

[4] 네트워크 복구
    → Firestore SDK가 캐시된 쓰기를 서버에 전송
    → ⚠️ 충돌: 기사가 AWAITING_SETTLEMENT를 쓰려 하지만 서버는 이미 CANCELED
    → Firestore는 "last write wins" (트랜잭션이 아니므로)
    → 콜이 AWAITING_SETTLEMENT로 덮어씌워질 수 있음!
    → ⚠️ 재연결 시 서버 상태 동기화 코드 없음 (onResume에서 재조회 없음)

[5] 결과
    → Manager: CANCELED으로 보임 (또는 AWAITING_SETTLEMENT로 덮어씌워짐)
    → Driver App: AWAITING_SETTLEMENT (로컬 캐시)
    → Customer App: 마지막 FCM 시점 상태
    → ⚠️ 4개 앱이 모두 다른 상태를 볼 수 있음
```

---

### 시나리오 5: 동시 사용 (10개 사무실) 데이터 격리

**격리 성공 영역:**
- Cloud Functions FCM 알림: 사무실별 올바르게 격리
- Firestore nested 경로 (`provinces/cities/offices/`): 구조적 격리
- Detector: collectionGroup 미사용, nested 경로만 사용

**격리 실패 영역:**

| 문제 | 영향 |
|------|------|
| calls read: `isAuthenticated()` | 모든 사무실 콜 열람 |
| designated_drivers collectionGroup: `isAuthenticated()` | 모든 사무실 기사 정보 |
| offices collectionGroup: `isAuthenticated()` | 모든 사무실 정보 |
| customerPoints/customerInfo: `isAuthenticated()` read | 모든 사무실 고객 정보 |
| points: `request.auth == null` write | 비인증 사용자 포인트 조작 |
| RTDB: `read/write: true` | 전체 Presence 노출/조작 |

---

## 3. v2에서 새로 발견된 문제

### v1에서 발견하지 못했던 이슈 (크로스 검증으로만 발견 가능)

#### NEW-CRITICAL-01: BUG-D08 - Manager 취소 시 기사 ghost state
- **발견 경위**: driver-analyst가 "실시간 리스너 없음" + manager-analyst가 "cancelCall 시 FCM/기사복구 없음"을 각자 보고 → 두 사실을 결합하니 ghost state 시나리오 도출
- **내용**: Manager 취소 → 기사 미인지 → 수락 시도 → UI/서버 분리 → 앱 재시작 전 복구 불가

#### NEW-CRITICAL-02: 기사 상태 4자 불일치 (CROSS-01 확대)
- **발견 경위**: detector-analyst(ON_TRIP) + manager-analyst(ASSIGNED) + driver-analyst(PREPARING) + firebase-analyst(배차중) 각자 보고를 결합
- **내용**: Manager, Detector, Driver App, Cloud Functions가 각각 다른 기사 상태값 사용

#### NEW-HIGH-01: 포인트 이중 적립
- **발견 경위**: firebase-analyst가 driver-analyst의 쿼리 패턴과 Cloud Functions 코드를 대조
- **내용**: Driver App → `pointTransactions`, Cloud Functions → `customerPointTransactions` (다른 컬렉션이라 중복 방지 불가)

#### NEW-HIGH-02: cancelTrip 보안 규칙 차단
- **발견 경위**: firebase-analyst가 driver-analyst의 cancelTrip 상태 전환을 보안 규칙과 대조
- **내용**: ASSIGNED→HOLD, IN_PROGRESS→HOLD는 보안 규칙에 없어 Firestore 에러

#### NEW-HIGH-03: CROSS-07 3중 차단
- **발견 경위**: driver-analyst(코드 빔) + manager-analyst(FCM 무시) + firebase-analyst(규칙 없음) 3자 결합
- **내용**: 코드 미구현 + FCM 무시 + 보안 규칙 차단 = 거절 기능 완전 불가

#### NEW-HIGH-04: HTTP Functions 무인증
- **발견 경위**: firebase-analyst의 `functions/index.js` 분석
- **내용**: `deleteEmergencyAlerts`, `cleanupOldNotifications` URL만 알면 호출 가능

#### NEW-HIGH-05: 콜 취소 소유권 미검증
- **발견 경위**: firebase-analyst 보안 규칙 정밀 분석
- **내용**: `isAuthenticated()`만 체크 → 아무 사용자가 아무 콜 취소 가능

#### NEW-MEDIUM-01: AWAITING_SETTLEMENT 감지 갭
- **발견 경위**: manager-analyst가 Manager FCM ignoredTypes와 activeCallsListener 분석
- **내용**: Manager가 AWAITING_SETTLEMENT를 무시 → COMPLETED까지 IN_PROGRESS로 표시

#### NEW-MEDIUM-02: 매니저 중복 FCM
- **발견 경위**: firebase-analyst가 Cloud Functions 트리거 대조
- **내용**: oncallassigned + onCallStatusChanged가 동시에 매니저에게 CALL_STATUS_UPDATE 전송

#### NEW-MEDIUM-03: notifyDriverAssignment callable 미등록
- **발견 경위**: firebase-analyst가 detector-analyst의 함수 호출을 export 목록과 대조
- **내용**: Detector가 호출하는 callable function이 Functions에 없음 (oncallassigned 트리거가 백업)

#### NEW-MEDIUM-04: crashlytics-monitor 필드명 불일치
- **발견 경위**: firebase-analyst가 detector-analyst에게 확인 요청
- **내용**: `associatedRegionId` vs `associatedProvinceId` → 크래시 알림 누락

#### NEW-MEDIUM-05: 보안 규칙 누락 컬렉션 5개
- **내용**: notifications, device_status, device_alerts, tokenRefreshRequests, customerPointTransactions

#### NEW-MEDIUM-06: 보안 규칙 데드 코드
- **내용**: `status == "SHARED" && status == "WAITING"` (동시 성립 불가)

---

## 4. 심각도 재평가 (전체 이슈 통합)

### Critical (즉시 수정) - 10건

| # | ID | 문제 | 출처 |
|---|-----|------|------|
| 1 | CROSS-01 | 기사 상태 4자 불일치 (ON_TRIP/ASSIGNED/PREPARING/배차중) | 4자 크로스 검증 |
| 2 | CROSS-04a | RTDB 보안 규칙 완전 개방 | firebase-analyst |
| 3 | CROSS-04b | Firestore `allow create: if true` (calls) | firebase-analyst |
| 4 | CROSS-04c | `request.auth == null` 패턴 18곳 (포인트/정산 조작 가능) | firebase-analyst |
| 5 | CROSS-04d | 임시 디버깅 `isAuthenticated()` 규칙 (데이터 격리 파괴) | firebase-analyst |
| 6 | CROSS-05 | 내장 Detector vs 독립 Detector 코드 동기화 실패 | detector + manager |
| 7 | CROSS-07a | rejectCall() 3중 차단 (코드 빔 + FCM 무시 + 규칙 없음) | 3자 크로스 검증 |
| 8 | CROSS-07b | cancelCall() 기사 ghost state (BUG-D08) | driver + manager |
| 9 | CD-01 | 마감 시 RINGING+IDLE 이중 공유콜 생성 | detector-analyst |
| 10 | DR-02 | acceptCall() 낙관적 업데이트 롤백 없음 | driver-analyst |

### High (중요) - 14건

| # | ID | 문제 | 출처 |
|---|-----|------|------|
| 1 | CROSS-02 | CallStatus enum 4앱 불일치 (16개 vs 7개 vs 6개) | 4자 크로스 검증 |
| 2 | CROSS-06 | 비원자적 Firestore 업데이트 (7개 함수) | 전원 크로스 검증 |
| 3 | NEW-H01 | 포인트 이중 적립 (다른 컬렉션) | firebase + driver |
| 4 | NEW-H02 | cancelTrip 보안 규칙 차단 (ASSIGNED/IN_PROGRESS→HOLD 불가) | firebase + driver |
| 5 | NEW-H04 | HTTP Functions 무인증 (긴급 알림 삭제 가능) | firebase-analyst |
| 6 | NEW-H05 | 콜 취소 소유권 미검증 | firebase-analyst |
| 7 | CM-02 | 내장 Detector ExcludeNumber 누락 | manager + detector |
| 8 | CM-09 | cancelCall() 로컬 DB 미업데이트 | manager-analyst |
| 9 | CM-10 | 취소 시 기사 상태 미복구 (completeCall과 비대칭) | manager-analyst |
| 10 | CM-11 | 재배차 워크플로우 없음 (HOLD→WAITING 경로 없음) | manager-analyst |
| 11 | CD-04 | SharedPreferences 8개 파편화 | detector-analyst |
| 12 | CD-05 | 배차 실패 시 사용자 피드백 없음 | detector-analyst |
| 13 | CD-06 | Detector 배차 트랜잭션 미사용 | detector-analyst |
| 14 | CU-05 | 테스트용 하드코딩 프로덕션 존재 | customer-analyst (v1) |

### Medium - 12건

| # | ID | 문제 |
|---|-----|------|
| 1 | NEW-M01 | AWAITING_SETTLEMENT Manager 감지 갭 |
| 2 | NEW-M02 | 매니저 중복 FCM (oncallassigned + onCallStatusChanged) |
| 3 | NEW-M03 | notifyDriverAssignment callable 미등록 |
| 4 | NEW-M04 | crashlytics-monitor regionId/provinceId 불일치 |
| 5 | NEW-M05 | 보안 규칙 누락 컬렉션 5개 |
| 6 | NEW-M06 | 보안 규칙 데드 코드 (SHARED && WAITING) |
| 7 | DR-04 | 비원자적 cancelTrip/startDriving/completeCall |
| 8 | DR-06 | NetworkMonitor 미사용, 재연결 시 동기화 없음 |
| 9 | CD-10 | 마감→운영 전환 시 다른 컬렉션에 중복 콜 |
| 10 | CD-11 | 콜 생성 실패 시 temp_ ID 배차 실패 |
| 11 | CM-04 | SMS 자동 발송 비활성화 불일치 |
| 12 | CU-01 | Customer App FCM 100% 의존 |

### Low - 8건

| # | 문제 |
|---|------|
| 1 | Constants.STATUS_IN_PROGRESS 값 불일치 (데드코드) |
| 2 | CallRepository.assignCall() 잘못된 경로 (데드코드) |
| 3 | showCancelledCallPopup() 데드코드 |
| 4 | assignedTimestamp 클라이언트/서버 차이 |
| 5 | BootCompletedReceiver SharedPreferences 불일치 |
| 6 | System.exit(0) 사용 |
| 7 | completeCall 후 기사 ON_TRIP 유지 (정산까지 대기) |
| 8 | FCM customerName 빈 문자열 |

---

## 5. 수정 권장사항 (다른 앱 영향 포함)

### Phase 1: 보안 긴급 패치 (즉시)

#### 1-1. RTDB 보안 규칙 설정
```json
{
  "rules": {
    "presence": {
      "drivers": {
        "$officeId": {
          "$driverId": {
            ".read": "auth != null",
            ".write": "auth != null && auth.uid == $driverId"
          }
        }
      }
    }
  }
}
```
**영향 앱**: Driver App (Presence 쓰기), Manager (Presence 읽기) → 두 앱 모두 로그인 필수 확인

#### 1-2. Firestore 임시 디버깅 규칙 제거
- calls read: `isAuthenticated()` → `isOfficeAdmin() || assignedDriverId == auth.uid || customerId == auth.uid`
- calls create: `if true` → `if isAuthenticated()`
- designated_drivers collectionGroup: `isAuthenticated()` → `resource.data.authUid == request.auth.uid`
- offices collectionGroup: `isAuthenticated()` → `isHeadManager()` (일반 사용자 제거)
**영향 앱**: Driver App 로그인 화면 collectionGroup 쿼리 수정 필요

#### 1-3. `request.auth == null` 제거
Admin SDK는 보안 규칙을 무시하므로 이 조건 불필요. 모든 위치에서 제거.
**영향 앱**: 없음 (Admin SDK는 규칙 무시)

#### 1-4. HTTP Functions 인증 추가
`deleteEmergencyAlerts`, `cleanupOldNotifications`에 Firebase Auth 또는 API Key 검증 추가
**영향 앱**: 없음 (관리자 도구)

---

### Phase 2: 데이터 무결성 (1주 내)

#### 2-1. 기사 상태 통일
모든 배차 경로에서 기사 상태를 `"ASSIGNED"`로 통일.
- Detector DispatchActivity.kt:172: `"ON_TRIP"` → `"ASSIGNED"`
- Cloud Functions index.ts:1198: `"배차중"` → `"ASSIGNED"`
**영향 앱**: Driver App (변경 불필요, 이미 ASSIGNED 기대), Manager (변경 불필요)

#### 2-2. rejectCall() 구현 (3곳 동시 수정)
1. **Driver App**: DriverViewModel.rejectCall() 구현 → Firestore calls status를 HOLD로, 기사 status를 WAITING으로
2. **Firestore 보안 규칙**: ASSIGNED → HOLD 전환 허용 추가
3. **Manager**: `DRIVER_REJECT`를 ignoredTypes에서 제거, 수신 시 콜 상태 업데이트
**영향 앱**: 3개 앱 동시 수정 필수

#### 2-3. cancelCall() 기사 상태 복구
Manager DashboardViewModel.cancelCall()에서 completeCall()과 동일하게 기사 status를 WAITING으로 복구 + 기사에게 FCM 전송
**영향 앱**: Driver App (FCM 수신 처리 추가 필요)

#### 2-4. 마감 시 이중 콜 방지
CallDetectorService RINGING 핸들러에서 공유콜 생성 시 플래그 설정 → IDLE에서 이미 생성됐으면 skip
**영향 앱**: 없음 (Detector 내부)

#### 2-5. 포인트 이중 적립 방지
Driver App의 포인트 적립을 제거하고 Cloud Functions에 일원화, 또는 동일 컬렉션에 callId 기반 중복 체크
**영향 앱**: Driver App (포인트 적립 코드 제거), Cloud Functions (유일한 적립 경로로)

---

### Phase 3: 안정성 개선 (2주 내)

#### 3-1. 비원자적 업데이트 트랜잭션 전환
우선순위: cancelTrip > assignCallToDriver > confirmAndFinalizeTrip > startDriving
**영향 앱**: 각 앱 내부 변경, 다른 앱 영향 없음

#### 3-2. Driver App 네트워크 복구 시 동기화
NetworkMonitor 활용 → 네트워크 복구 시 현재 콜 상태를 서버에서 1회 조회 → 로컬과 불일치 시 서버 기준으로 보정
**영향 앱**: Driver App 내부

#### 3-3. Customer App Firestore 리스너 fallback
FCM 보완으로 앱 포그라운드 시 현재 콜 상태를 주기적 polling 또는 Firestore 리스너 재활성화
**영향 앱**: Customer App 내부

#### 3-4. 내장 Detector 코드 동기화
wasRinging, ExcludeNumberManager, SMS 비활성화를 Manager 내장 Detector에 적용
**영향 앱**: Manager 내부

#### 3-5. 재배차 워크플로우
HOLD → WAITING → 재배차 UI/함수 구현 (Manager)
**영향 앱**: Manager UI + Firestore 보안 규칙 (HOLD → WAITING 전환 허용)

---

### Phase 4: UX/코드 품질 (3주 내)

#### 4-1. CallStatus enum 통합
공통 상수 모듈 또는 최소한 4개 앱의 enum을 동일하게 맞춤
#### 4-2. SharedPreferences 정리 (Detector 8개 → 2-3개)
#### 4-3. 테스트용 하드코딩 제거
#### 4-4. 데드코드 정리 (Constants.STATUS_IN_PROGRESS, CallRepository.assignCall, showCancelledCallPopup 등)

---

## 6. 최종 라운드 추가 발견 (보고서 작성 후 도착)

### [NEW-CRITICAL] reopenSharedCall() 보안 규칙 거부
- **파일**: DashboardViewModel.kt:1606-1623
- **발견**: firebase-analyst가 manager-analyst의 쿼리 패턴과 보안 규칙을 대조
- **내용**: Manager가 공유콜을 CLAIMED→OPEN으로 되돌리려 하지만, 보안 규칙은 OPEN→CLAIMED만 허용
- **영향**: "공유콜 다시 열기" 기능이 실제로 동작하지 않을 가능성 높음

### [NEW-CRITICAL] deleteSharedCall() 보안 규칙 거부
- **파일**: DashboardViewModel.kt:1625-1632
- **발견**: firebase-analyst가 manager-analyst의 쿼리 패턴과 보안 규칙을 대조
- **내용**: Manager가 공유콜을 직접 삭제하려 하지만, 보안 규칙은 `request.auth == null`(Cloud Functions만) 허용
- **영향**: 클라이언트에서 공유콜 삭제 불가. scheduledDataCleanup이 1시간 후 자동 삭제하기 전까지 잔존

### [NEW-HIGH] CANCELLED vs CANCELED 의미 혼동
- **발견**: manager-analyst가 3-Way CallStatus 비교 시 발견
- **내용**:
  - Manager: `CANCELED("CANCELED")` = "취소", `CANCELLED("CANCELLED")` = "취소요청" → **의미가 다름**
  - Driver: `CANCELLED` enum이지만 `firestoreValue = "CANCELED"` 저장
  - Detector: `CANCELLED("CANCELLED")` 저장
- **결과**: Driver가 취소 → "CANCELED" → Manager는 "취소"로 해석. Detector가 취소 → "CANCELLED" → Manager는 "취소요청"으로 해석. **같은 동작인데 출처에 따라 다르게 해석됨**

### ~~[NEW-MEDIUM] BUG-D09: Driver ↔ Manager Firestore 필드명 불일치~~ → 오탐(False Positive)
- firebase-analyst 검증 결과: `departure`(원본값) vs `departure_set`(기사 확정값)은 의도된 설계. Cloud Functions도 fallback 처리. 필드명은 실제로 일치함.

### [NEW-CRITICAL] crashlytics-monitor 필드명 불일치
- **발견**: detector-analyst + firebase-analyst 크로스 검증
- **내용**: CrashReportService.kt는 `provinceId`/`cityId`/`officeId` 사용, crashlytics-monitor.js는 `regionId`/`associatedRegionId` 조회
- **영향**: provinces/cities 구조로 마이그레이션된 후 크래시 알림이 관리자에게 전달되지 않음

### [NEW-CRITICAL] NEW-10: 크래시 감지 시스템 보안 규칙 부재
- **발견**: detector-analyst가 코드 확인 + firebase-analyst가 보안 규칙 대조
- **내용**: Call Detector CrashReportService.kt가 **클라이언트 SDK**로 `device_status`(3곳), `device_alerts`(5곳), `emergency_alerts`(1곳) top-level 컬렉션에 직접 씀. 이 3개 컬렉션 모두 Firestore 보안 규칙에 명시적 규칙 없음.
- **영향**: Firestore 기본 정책 "규칙 없으면 거부" → **크래시 감지 쓰기가 실제로 거부되고 있을 가능성 높음** → onWrite 트리거 미발동 → 관리자 알림 없음. 크래시가 발생할수록 감지가 안 되는 역설적 상황.
- **이중 문제**: 크래시 시점 Firebase Auth 토큰 만료 가능 → 인증 기반 규칙 추가해도 거부될 수 있음

### [NEW-HIGH] BUG-D11: 공유콜 기사 취소 시 Cloud Function 미작동
- **발견**: driver-analyst + firebase-analyst 크로스 검증
- **내용**: Driver App `cancelTrip()`은 콜 status를 항상 `"HOLD"`로 설정. 공유콜도 동일. 하지만 Cloud Function `onSharedCallCancelledByDriver`는 `"CANCELLED_BY_DRIVER"` 상태만 감지.
- **영향**: 공유콜 원본 사무실에 콜 복구 안 됨 + shared_calls 미삭제 (1시간 후 자동 삭제까지 잔존) + 원본 관리자에게 취소 FCM 미전송
- **수정안**: cancelTrip()에서 callType == "SHARED"이면 "CANCELLED_BY_DRIVER" 사용

### ~~[POTENTIAL-CRITICAL] NEW-09: Detector doc.id vs authUid 혼용~~ → 오탐(False Positive)
- firebase-analyst + manager-analyst 양쪽 검증 완료: 기사 등록 흐름(SignUpViewModel → PendingDriversViewModel)에서 `designated_drivers` 문서 ID = Firebase Auth UID로 항상 생성됨. `doc.id == authUid` 보장됨.

---

## v1 vs v2 비교

| 항목 | v1 (개별 분석) | v2 (크로스 검증) |
|------|--------------|----------------|
| Critical 이슈 | 14건 | 10건 (통합/재분류) |
| High 이슈 | 17건 | 14건 (통합/재분류) |
| 크로스 모듈 이슈 | 7건 (리더가 추론) | 7건 (팀원 간 코드 대조로 확정) |
| 신규 발견 | - | **12건** (크로스 검증으로만 가능) |
| CROSS-01 범위 | 2자 불일치 | **4자 불일치** |
| CROSS-07 범위 | 코드 빔 + 기사 미복구 | **3중 차단 + ghost state** |
| 보안 이슈 범위 | RTDB + calls create | **18곳 request.auth==null + HTTP 무인증 + 소유권 미검증** |
| 포인트 이중 적립 | 미발견 | **발견** |
| 시나리오 검증 | 없음 | **5가지 코드 레벨 시뮬레이션** |
| 수정 시 타 앱 영향 | 미분석 | **앱별 영향도 포함** |

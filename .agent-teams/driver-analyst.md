# Driver App 분석 보고서 (driver-analyst)

> **마지막 업데이트**: 2026-02-24
> **브랜치**: firestore-migration-backup
> **분석 대상**: Driver App (기사 앱)

---

## 1. 담당 범위

Driver App 전체 분석 및 크로스 검증 담당.
- Firestore 읽기/쓰기 패턴, 상태 전이, FCM 수신 처리
- 다른 앱(Manager, Detector, Customer)과의 데이터 일관성 검증
- 보안 규칙과 Driver App 코드의 정합성 확인

---

## 2. 핵심 파일 목록

### 비즈니스 로직

| 파일 | 경로 | 역할 |
|------|------|------|
| DriverViewModel.kt | `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt` | 중앙 비즈니스 로직 (~1600줄). 콜 수락/취소/운행/정산 전체 관리 |
| LoginViewModel.kt | `driver_app/app/src/main/java/com/designated/driverapp/ui/login/LoginViewModel.kt` | 로그인 (collectionGroup 쿼리 사용) |

### 모델/상수

| 파일 | 경로 | 역할 |
|------|------|------|
| Constants.kt | `driver_app/app/src/main/java/com/designated/driverapp/data/Constants.kt` | 컬렉션명, 필드명, 상태값, 액션 상수 |
| DriverStatus.kt | `driver_app/app/src/main/java/com/designated/driverapp/model/DriverStatus.kt` | 기사 상태 enum (7값: ONLINE, OFFLINE, WAITING, ASSIGNED, ACCEPTED, ON_TRIP, UNKNOWN) |
| CallInfo.kt | `driver_app/app/src/main/java/com/designated/driverapp/model/CallInfo.kt` | 콜 상태 enum + 콜 데이터 클래스 |

### FCM/알림/서비스

| 파일 | 경로 | 역할 |
|------|------|------|
| MyFirebaseMessagingService.kt | `driver_app/app/src/main/java/com/designated/driverapp/MyFirebaseMessagingService.kt` | FCM 수신, 토큰 관리, 알림 생성 (367줄) |
| DriverForegroundService.kt | `driver_app/app/src/main/java/com/designated/driverapp/service/DriverForegroundService.kt` | 포그라운드 서비스 (189줄) |
| LockScreenActivity.kt | `driver_app/app/src/main/java/com/designated/driverapp/LockScreenActivity.kt` | 잠금화면 전체화면 알림 (297줄) |

### UI/액티비티

| 파일 | 경로 | 역할 |
|------|------|------|
| MainActivity.kt | `driver_app/app/src/main/java/com/designated/driverapp/MainActivity.kt` | 메인 액티비티, BroadcastReceiver 등록 (481줄) |
| HomeScreen.kt | `driver_app/app/src/main/java/com/designated/driverapp/ui/home/HomeScreen.kt` | Compose UI 라우팅 (newCallPopup/activeCall/waiting 분기) |
| DriverApplication.kt | `driver_app/app/src/main/java/com/designated/driverapp/DriverApplication.kt` | Application 클래스, isInForeground 정적 필드 |

### 기타

| 파일 | 경로 | 역할 |
|------|------|------|
| NetworkMonitor.kt | `driver_app/app/src/main/java/com/designated/driverapp/util/NetworkMonitor.kt` | 네트워크 상태 감시 (미사용 - BUG-D06) |
| SettlementSyncWorker.kt | `driver_app/app/src/main/java/com/designated/driverapp/worker/SettlementSyncWorker.kt` | 오프라인 정산 WorkManager 동기화 |

---

## 3. 주요 함수 레퍼런스 (DriverViewModel.kt)

| 함수 | 라인 | 역할 | Firestore 방식 |
|------|------|------|---------------|
| loadCurrentActiveCall() | ~233-319 | 앱 시작 시 활성 콜 일회성 조회 | `.get().await()` |
| acceptCall() | ~366-457 | 콜 수락 (ASSIGNED→ACCEPTED, 기사→"PREPARING") | `runTransaction` |
| rejectCall() | ~459-462 | 콜 거부 (**비어있음** - BUG-D01) | 없음 |
| cancelTrip() | ~470-506 | 운행 취소 (→HOLD, 기사→WAITING) | 비원자적 `.update().await()` x2 |
| startDriving() | ~521-577 | 운행 시작 (→IN_PROGRESS, 기사→ON_TRIP) | 비원자적 `.update().await()` x2 |
| completeCall() | ~579-607 | 운행 완료 (→AWAITING_SETTLEMENT) | `.update().await()` |
| confirmAndFinalizeTrip() | ~609-748 | 정산 완료 (→COMPLETED, 기사→WAITING) | 비원자적 다수 |
| handleCallCancelled() | ~837-851 | 취소 FCM 수신 처리 (UI 정리) | 없음 (로컬 상태만) |
| handleNotificationCallId() | ~977-1018 | FCM callId로 콜 조회 + 팝업 | `.get().await()` |
| processCustomerPoints() | ~1106-1235 | 고객 포인트 처리 (**이중 적립 - BUG-D12**) | `runTransaction` |
| startCarryOverListener() | ~1245-1288 | 이월 정산 리스너 (유일한 snapshotListener) | `addSnapshotListener` |

---

## 4. 크로스 검증 대상

### Manager App (manager-analyst)

| 검증 포인트 | 결과 |
|------------|------|
| 배차 시 기사 status 값 | Manager→"ASSIGNED", Driver acceptCall→"PREPARING" (CROSS-01) |
| cancelCall() 구현 유무 | 있음: 기사 status→WAITING + FCM 전송 (BUG-D05 해소) |
| CallStatus enum 차이 | Manager 15+개 vs Driver 7개 (CROSS-02) |
| AWAITING_SETTLEMENT FCM | CALL_STATUS_UPDATE type으로 정상 전달 |
| 시나리오 1 상태 전이표 | 최종 합의 완료 |

### Detector App (detector-analyst)

| 검증 포인트 | 결과 |
|------------|------|
| 배차 시 기사 status 값 | Detector→"ON_TRIP" (CROSS-01) |
| rejectCall() 비어있음 + Detector 배차 조합 | CRITICAL: 기사 ON_TRIP 영구 잠김 (CROSS-07) |
| deleteCall() 후 기사 복구 | 없음: 기사 ON_TRIP 유지 |
| doc.id vs authUid | False Positive (항상 동일) |
| ACK 누락 → 중복 알림 | 기능 영향 없음 (status 체크로 방어) |

### Firebase/Cloud Functions (firebase-analyst)

| 검증 포인트 | 결과 |
|------------|------|
| onSharedCallClaimed 기사 status | 소스: "ASSIGNED", 배포 코드: "배차중" (BUG-D10) |
| 보안 규칙: ASSIGNED→HOLD | 불가 (ACCEPTED→HOLD만 허용) |
| 보안 규칙: 공유콜 CANCELLED_BY_DRIVER | callType=="SHARED" 조건 필수 |
| pointTransactions vs customerPointTransactions | 이중 적립 확인 (BUG-D12) |
| 콜 문서 실시간 리스너 | 없음: FCM이 유일한 취소 통지 경로 |
| BUG-D09 departure_set vs departure | False Positive (의도적 설계, CF에서 fallback) |

---

## 5. 발견한 이슈 목록

### 최종 카운트: 1 CRITICAL, 3 HIGH, 5 MEDIUM, 1 LOW, 1 해소, 1 오탐

| ID | 심각도 | 상태 | 제목 | 위치 |
|----|--------|------|------|------|
| BUG-D01 | HIGH | 미착수 | rejectCall() 함수 완전히 비어있음 | DriverViewModel.kt:459-462 |
| BUG-D02 | MEDIUM | 미착수 | acceptCall()에서 "PREPARING" 하드코딩 (DriverStatus enum에 없음) | DriverViewModel.kt:~400 |
| BUG-D03 | MEDIUM | 미착수 | cancelTrip()에서 "HOLD" 하드코딩 (CallStatus enum에 없음) | DriverViewModel.kt:~480 |
| BUG-D04 | MEDIUM | 미착수 | cancelTrip/startDriving/confirmAndFinalizeTrip 비원자적 Firestore 업데이트 | DriverViewModel.kt 다수 |
| BUG-D05 | - | **해소** | Manager cancelCall()이 FCM 전송 안함 → 실제로는 전송함 | manager-analyst 정정 |
| BUG-D06 | MEDIUM | 미착수 | NetworkMonitor 선언만 되고 ViewModel에서 미사용 | NetworkMonitor.kt 전체 |
| BUG-D07 | LOW | 미착수 | completeCall()에서 기사 status 미변경 (ON_TRIP 유지) | DriverViewModel.kt:~579-607 |
| BUG-D08 | MEDIUM | 미착수 | 취소 FCM 실패 시 기사 앱 ghost state (리스너 없음, 새로고침 없음) | 구조적 |
| BUG-D09 | - | **오탐** | departure_set vs departure 필드명 불일치 → 의도적 설계 | firebase-analyst 확인 |
| BUG-D10 | HIGH | 미착수 | Cloud Function "배차중" 한글 status → Driver App UNKNOWN 반환 | lib/index.js:953 (소스에서 수정됨, 미배포) |
| BUG-D11 | **CRITICAL** | 미착수 | 공유콜 ASSIGNED 취소 시 보안 규칙 거부 → 기사+콜 잠김 | DriverViewModel.kt cancelTrip() + firestore.rules:160 |
| BUG-D12 | HIGH | 미착수 | pointTransactions vs customerPointTransactions 컬렉션명 불일치 → 이중 적립 | DriverViewModel.kt:~1116-1235 + CF handlers/points.ts |

### 이슈 상세

#### BUG-D01 (HIGH) - rejectCall() 비어있음
```kotlin
// DriverViewModel.kt:459-462
fun rejectCall(callId: String) {
    viewModelScope.launch {
    }
}
```
- LockScreenActivity에 Reject 버튼 존재하지만 동작 안함
- Detector 배차 시 기사가 ON_TRIP 영구 잠김 (CROSS-07)
- 보안 규칙상 일반 콜은 ASSIGNED→거부 경로 없음 (의도적 설계 가능성)

#### BUG-D02 (MEDIUM) - "PREPARING" 하드코딩
```kotlin
// acceptCall() 내부
transaction.update(driverRef, Constants.FIELD_STATUS, "PREPARING")
```
- DriverStatus enum에 PREPARING 없음 → fromString("PREPARING") = UNKNOWN

#### BUG-D03 (MEDIUM) - "HOLD" 하드코딩
```kotlin
// cancelTrip() 내부
Constants.FIELD_STATUS to "HOLD"
```
- CallStatus enum에 HOLD 없음
- 공유콜에서도 동일하게 HOLD 사용 (callType 분기 없음)

#### BUG-D10 (HIGH) - 소스 vs 빌드 불일치
- TypeScript 소스 (index.ts:1198): `status: "ASSIGNED"` (수정 완료)
- 컴파일된 JS (lib/index.js:953): `status: "배차중"` (미배포)
- lib/index.js 타임스탬프: Feb 19, src/index.ts: Feb 24
- 프로덕션에서는 여전히 활성 버그

#### BUG-D11 (CRITICAL) - 공유콜 ASSIGNED 취소 시 보안 규칙 거부 → 탈출 불가
- Driver App cancelTrip()는 callType 구분 없이 항상 `status: "HOLD"` 사용
- 보안 규칙: ACCEPTED→HOLD 허용, **ASSIGNED→HOLD 규칙 없음** → PERMISSION_DENIED
- 공유콜이 ASSIGNED 상태에서 기사 취소 시: Firestore 거부 → 콜 ASSIGNED + 기사 상태 변경 안됨
- 재시도해도 동일 거부 반복 → **탈출 불가 상태 (deadlock)**
- 수정안: cancelTrip()에서 callType 분기 - 공유콜은 CANCELLED_BY_DRIVER 사용

#### BUG-D12 (HIGH) - 포인트 이중 적립
- Driver App: `pointTransactions` 컬렉션 사용, `callId`만으로 중복 체크
- Cloud Functions: `customerPointTransactions` 컬렉션 사용, `phoneNumber + callId + type` 중복 체크
- 서로 다른 컬렉션이라 중복 체크가 상호 인식 불가
- 합의된 수정안: Driver App의 processCustomerPoints() 제거, Cloud Functions에 단일화

---

## 6. 수정 이력 (이번 세션)

### Constants.kt - ACTION_CALL_CANCELLED 추가
- 파일: `driver_app/app/src/main/java/com/designated/driverapp/data/Constants.kt`
- 라인 57: `const val ACTION_CALL_CANCELLED = "com.designated.driverapp.ACTION_CALL_CANCELLED"`

### MyFirebaseMessagingService.kt - call_cancelled 핸들러 추가
- 파일: `driver_app/app/src/main/java/com/designated/driverapp/MyFirebaseMessagingService.kt`
- 라인 177-191: `messageType == "call_cancelled"` 분기 추가
  - 포그라운드: LocalBroadcast (ACTION_CALL_CANCELLED + callId)
  - 백그라운드: showNotification() 호출

### DriverViewModel.kt - handleCallCancelled() 함수 추가
- 파일: `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt`
- 라인 837-851: 취소 FCM 수신 시 UI 상태 정리
  - assignedCalls에서 해당 callId 제거
  - newCallPopup/activeCall이 해당 콜이면 null로
  - driverStatus를 WAITING으로 복구

### MainActivity.kt - callCancelledReceiver 등록/해제
- 파일: `driver_app/app/src/main/java/com/designated/driverapp/MainActivity.kt`
- 라인 80-89: BroadcastReceiver 정의 (callId → handleCallCancelled 호출)
- 라인 319-321: onResume()에서 등록 (`IntentFilter(Constants.ACTION_CALL_CANCELLED)`)
- 라인 343: onPause()에서 해제 (`unregisterReceiver(callCancelledReceiver)`)

---

## 7. FCM 타입별 처리 현황

| FCM type | 조건 | 포그라운드 처리 | 백그라운드 처리 | 비고 |
|----------|------|----------------|----------------|------|
| `call_assigned` | callId 필수 | LocalBroadcast → 다이얼로그 | showNotification + FullScreenIntent(LockScreen) | ACK 전송, ForegroundService 시작 |
| `call_cancelled` | callId 필수 | LocalBroadcast → handleCallCancelled() | showNotification (UI 정리 안됨) | **엣지케이스**: 백그라운드에서 상태 불일치 |
| `SETTLEMENT_FINALIZED` | - | LocalBroadcast + 알림 | LocalBroadcast + 알림 | 포그라운드/백그라운드 동일 처리 |
| 기타 | - | showNotification | showNotification | 범용 알림 |

### FCM 처리 흐름도

```
onMessageReceived()
  ├─ officeId 없음 → 무시 (비로그인)
  ├─ call_assigned → ForegroundService + (포그라운드: LocalBroadcast / 백그라운드: Notification+FullScreen)
  ├─ call_cancelled → (포그라운드: LocalBroadcast→handleCallCancelled / 백그라운드: Notification만)
  ├─ SETTLEMENT_FINALIZED → LocalBroadcast + Notification
  └─ 기타 → Notification
```

---

## 8. 크로스 검증 요청/결과 요약

### 보낸 검증 요청

| 대상 | 내용 | 결과 |
|------|------|------|
| manager-analyst | acceptCall → ACCEPTED + PREPARING 확인 | CROSS-01 확정 (Manager는 ASSIGNED) |
| manager-analyst | cancelCall() FCM 전송 여부 | 있음 → BUG-D05 해소, BUG-D08 하향 |
| detector-analyst | Detector 배차 시 기사 status 값 | ON_TRIP → CROSS-01 4-way 확정 |
| detector-analyst | rejectCall 비어있음 + Detector 배차 시나리오 | CRITICAL 동의 (CROSS-07) |
| firebase-analyst | onSharedCallClaimed 기사 status | 소스 "ASSIGNED", 배포 코드 "배차중" → BUG-D10 |
| firebase-analyst | 콜 문서 실시간 리스너 여부 | 없음 → FCM 유일 경로 확인 |
| firebase-analyst | pointTransactions vs customerPointTransactions | 이중 적립 확인 → BUG-D12 |

### 받은 검증 요청

| 출처 | 질문 | 답변 요약 |
|------|------|----------|
| manager-analyst | Scenario 1 상태 전이 3개 질문 | acceptCall/loadCurrentActiveCall/startDriving 코드 증거 제공 |
| manager-analyst | AWAITING_SETTLEMENT ignoredTypes | 데드코드 확인 (type 필드 불일치) |
| detector-analyst | rejectCall + Detector 배차 시 Firestore 변경 | 없음 (함수 비어있음) |
| detector-analyst | acceptCall 시 driver status 체크 여부 | 체크 안함 (ON_TRIP→PREPARING 역행 가능) |
| firebase-analyst | 보안 규칙 ASSIGNED→HOLD 허용 여부 | Driver App에서 시도하지 않음 (cancelTrip은 ACCEPTED→HOLD) |
| firebase-analyst | cancelTrip에서 callType 분기 여부 | 없음 (항상 HOLD) |
| firebase-analyst | CollectionGroup 보안 | LoginViewModel에서 authUid 필터로 사용 |
| team-lead | cancel FCM 코드 검증 (3항목) | 포그라운드 OK, 백그라운드/LockScreen 엣지케이스 2개 보고 |

---

## 9. 크로스 검증 항목 (CROSS)

| ID | 제목 | 심각도 | 관련 앱 |
|----|------|--------|---------|
| CROSS-01 | 4-way 기사 status 불일치 (ASSIGNED/ON_TRIP/배차중/PREPARING) | CRITICAL | 전체 |
| CROSS-02 | CallStatus enum 불일치 (Manager 15+ vs Driver 7) | MEDIUM | Manager, Driver |
| CROSS-06 | 비원자적 Firestore 업데이트 | MEDIUM | Driver |
| CROSS-07 | rejectCall 비어있음 + Detector 배차 조합 | CRITICAL | Detector, Driver |

---

## 10. 미해결 이슈 Next Steps

### 즉시 필요 (배포 시)

| 항목 | 설명 | 담당 |
|------|------|------|
| BUG-D10 배포 | `functions/src/index.ts` 빌드 후 `firebase deploy --only functions` | 운영 |
| BUG-D12 수정 | Driver App processCustomerPoints() 제거 + CF 단일화 | Driver + Firebase |

### 백그라운드/LockScreen 엣지 케이스

| 엣지 케이스 | 현상 | 수정안 |
|------------|------|--------|
| 백그라운드 cancel FCM | showNotification만 표시, handleCallCancelled() 미호출 → 앱 복귀 시 이전 상태 유지 | 알림 클릭 시 MainActivity에서 callId 기반 상태 갱신, 또는 onResume에서 서버 재조회 |
| LockScreen cancel FCM | LockScreenActivity에 ACTION_CALL_CANCELLED receiver 미등록 → 취소 FCM 무시 | LockScreenActivity에 cancel receiver 등록, 또는 onResume에서 콜 상태 재확인 |
| LockScreen Reject 버튼 | finish()만 호출, Firestore 변경 없음 | rejectCall() 구현 또는 Reject 버튼 제거 |

### 구조적 개선

| 항목 | 설명 |
|------|------|
| 미사용 코드 정리 | assignedCallsListener, driverStatusListener, completedCallsListener (데드코드), NetworkMonitor |
| 하드코딩 상수화 | "PREPARING" → DriverStatus enum 추가, "HOLD" → CallStatus enum 추가 |
| cancelTrip callType 분기 | 공유콜: CANCELLED_BY_DRIVER, 일반콜: HOLD |
| 비원자적 업데이트 트랜잭션화 | cancelTrip, startDriving, confirmAndFinalizeTrip에 runTransaction 적용 |

---

## 11. Firestore 리스너 현황

| 변수명 | 대상 | 상태 |
|--------|------|------|
| assignedCallsListener | (미할당) | **데드코드** - 선언 + cleanup만 존재 |
| driverStatusListener | (미할당) | **데드코드** |
| completedCallsListener | (미할당) | **데드코드** |
| carryOverListener | 기사 문서 (carryOver/dailySettlement) | **활성** - 유일한 snapshotListener |

**콜 문서에 대한 실시간 리스너 없음.** FCM이 유일한 실시간 통지 경로.

---

## 12. 상태 전이표 (최종 합의)

### 콜 상태

```
WAITING → ASSIGNED (Manager/Detector 배차)
ASSIGNED → ACCEPTED (기사 수락, acceptCall)
ACCEPTED → HOLD (기사 취소, cancelTrip - 일반콜)
ACCEPTED → CANCELLED_BY_DRIVER (공유콜 전용 - Driver App 미구현)
ACCEPTED → IN_PROGRESS (운행 시작, startDriving)
IN_PROGRESS → AWAITING_SETTLEMENT (운행 완료, completeCall)
AWAITING_SETTLEMENT → COMPLETED (정산 완료, confirmAndFinalizeTrip)
```

### 기사 상태

```
ONLINE → WAITING (콜 대기 시작)
WAITING → "PREPARING" (콜 수락 - 하드코딩, enum 없음)
"PREPARING" → ON_TRIP (운행 시작)
ON_TRIP → ON_TRIP (운행 완료 - BUG-D07: 상태 미변경)
ON_TRIP → WAITING (정산 완료)
ACCEPTED/PREPARING → WAITING (운행 취소)
```

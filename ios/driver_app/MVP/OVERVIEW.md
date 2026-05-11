# iOS 기사앱 Phase 1 MVP — 개요

> **목적**: iOS 기사앱의 **기본 기능(Base)만** 구현하여 콜 사이클 1순환이 실제 작동하는 앱을 출시. 보강 기능은 Phase 2 이후 실측 데이터 기반으로 추가.
> **범위 근거**: `ios/FUNCTIONAL_INVENTORY.md` §5 "Phase 1 MVP Scope — B로 분류된 항목 전수" (63건)
> **작성일**: 2026-04-15

---

## Phase 1 목표

### 한 줄 정의
**콜매니저가 지정한 콜을 iOS 기사가 받아 운행·정산을 완결하고 WAITING으로 복귀할 수 있다.**

### 완료 기준 (전부 충족해야 Phase 1 완료)

1. **콜 사이클 1순환 실기기 검증**
   - 콜매니저(Android)가 iOS 기사 지정 → iOS 기사앱이 배차 알림 수신
   - 기사 수락 → 콜매니저·손님앱이 상태 갱신 인지
   - 운행 시작 → 콜매니저·손님앱 통지 확인
   - 운행 완료 → 정산 입력 → 제출 → 관리자 확인 → WAITING 복귀
   - 이월금 있는 경우 transferCarryOver → confirmReceiveCarryOver → SETTLED

2. **로그인·자동로그인 실기기 검증**
   - 최초 로그인 → Keychain 자격증명 저장
   - 앱 재시작 → 자동 로그인 → 이전 상태 복원
   - 로그아웃 → 자격증명 삭제 + Presence offline

3. **FCM 6종 수신 + Delivery ACK**
   - call_assigned, call_cancelled, SETTLEMENT_FINALIZED, SETTLEMENT_CONFIRMED, SETTLEMENT_REJECTED, CARRYOVER_TRANSFERRED
   - 포그라운드 / 백그라운드 / 잠금(배너 수준) 모두 수신 확인
   - `acknowledgeNotification` Cloud Function 호출 확인

4. **취소 경로 모든 분기 + 기사 인지 UX**
   - 관리자 취소 (CANCELED) → call_cancelled FCM → 기사앱 UI 자동 정리 + 알림
   - 기사 거절 (앱 내) → WAITING 복귀 (롤백/undo는 미구현, Android 동등)
   - 기사 운행 취소 (cancelTrip → CANCELLED_BY_DRIVER)
   - 손님 취소 (CANCELLED_BY_CUSTOMER, ACCEPTED/PREPARING 단계까지)
     - **Alert 팝업 "고객이 콜을 취소했습니다" + 홈 이동** (0.5일 추가 공수)
   - 3분 타임아웃 (checkAssignedTimeout → WAITING 복귀)
     - **CF가 call_cancelled FCM 전송**(reason="응답 시간 초과") → 기존 call_cancelled 핸들러 재사용
     - 무음 Time Sensitive Banner + 화면 자동 정리 (0.25일 추가 공수)
     - offline 상태 타임아웃은 FCM 미발송 → 복귀 시 loadCurrentActiveCall 1회 조회로 복구

5. **정산 흐름 전수**
   - 5가지 결제 방식 (현금 / 이체 / 외상 / 포인트 / 현금+포인트) 각 정상 처리
   - 정산 공식 (officeDeposit, driverShare, finalDeposit, realDeposit) 계산 검증
   - 통합 제출 (isIntegration, PENDING_CONFIRM 재제출) 검증
   - 매니저 반려 시 재제출 경로

6. **Presence**
   - Realtime DB `.info/connected` 기반 online/offline 전환
   - `onDisconnect` → offline 설정
   - 로그아웃 cleanup
   - CF `checkAssignedTimeout` 스케줄러가 정상 동작하는 것 전제 (서버 측)

7. **Firestore 오프라인 자동 큐잉 작동 확인**
   - 단기 오프라인(수 분) 후 복귀 시 로컬 write가 정상 반영

8. **TestFlight 외부 테스터 파일럿 최소 2주**
   - 실제 기사 3~5명 TestFlight 초대
   - 운영 데이터 수집 (REINFORCEMENT/TRIGGERS.md의 측정 항목)

---

## Phase 1 범위 (포함)

### 라이프사이클
1. 배차 수신 (잠금·백그라운드 상태 포함, 앱 강제 종료는 **제외** — R1)
2. 기사 수락·거절 → Firestore 트랜잭션
3. 운행 시작·완료 → 양쪽 통지
4. 정산 입력·제출·반려·재제출
5. 이월금 처리
6. WAITING 복귀

### 알림
- APNs 등록 + FCM 토큰 서버 업로드
- FCM 6종 수신 + 핸들러
- **Time Sensitive Notification** (잠금화면 배너 + Focus 우회)
- Delivery ACK

### 앱 기본
- 로그인·자동로그인·회원가입·비밀번호 찾기
- Keychain 자격증명
- UserDefaults (사무실 정보 캐시)
- Presence 기본 (online/background/offline)
- Firestore 자동 offline persistence

### 화면
- 로그인·회원가입·비밀번호 찾기
- 홈 (대기·배차·운행 상태 분기)
- 콜 상세
- 운행 내역 + 정산 + 업무마감
- 설정
- 주소 검색

### 콜매니저·손님앱 연동
- 기사앱 → CF → 콜매니저 매니저 FCM (서버 작동, iOS 코드 무관)
- 기사앱 → CF → 손님앱 FCM (서버 작동)
- 손님앱 취소 → CF → 기사앱 call_cancelled (기사앱이 수신만)

---

## Phase 1 범위 (제외 — REINFORCEMENT로 이관)

- ❌ LockScreen 커스텀 풀스크린 UI → `R1_LOCKSCREEN.md` (VoIP+CallKit + Live Activity)
- ❌ 앱 강제 종료 상태에서 콜 깨움 → `R1_APP_KILLED.md` (VoIP push)
- ❌ 3초 간격 반복 알림음 → 시스템 기본 사운드로 대체
- ❌ 무한 진동 → 시스템 기본 진동
- ❌ Foreground Service 영구 활성 → iOS 구조적 불가, 이벤트 기반 재설계 (D)
- ❌ BootReceiver 자동 재시작 → iOS 구조적 불가 (D)
- ❌ WAKE_LOCK 화면 강제 점등 → CallKit(R1) 도입 시 대체
- ❌ 정산 PendingSync 오프라인 큐 → `R1_PENDING_SYNC.md`
- ❌ 네트워크 끊김 배너 UI → `R1_NETWORK_BANNER.md`
- ❌ BGTaskScheduler 주기 sync → `R2_BG_SYNC.md`
- ❌ 추천 QR 화면 → `R2_REFERRAL_QR.md`

---

## 기술 스택 (주제 1~5 합의 기반)

- **언어**: Swift 5.10+
- **최소 OS**: iOS 17.2
- **UI**: SwiftUI
- **상태 관리**: `@Observable` + 단일 struct UiState property (@MainActor 클래스)
- **비동기**: async/await + AsyncStream (Firestore 리스너)
- **로컬 저장**: SwiftData (Phase 1에선 정산 캐시만 — PendingSync는 R1로 이관)
- **저장소 보조**: UserDefaults (사무실 정보) + Keychain (자격증명)
- **Firebase SDK**: Firestore, Auth, Messaging, Realtime Database, Functions (모두 async/await)
- **푸시**: APNs + UNUserNotificationCenter + Time Sensitive (`interruptionLevel = .timeSensitive`)
- **Background Modes**: Remote Notifications만 (VoIP는 R1)

---

## 구현 문서 구성 (MVP/ 아래 파일 8개)

| 파일 | 내용 | 주 작성자 |
|------|------|----------|
| `OVERVIEW.md` | 본 문서 | 중재자 |
| `MODELS.md` | Kotlin data class → Swift struct 1:1 매핑 (Codable) | kotlin-expert |
| `ENUMS.md` | CallStatus, DriverStatus, DailySettlementStatus 등 전체 | kotlin-expert |
| `VIEWMODELS.md` | DriverViewModel·LoginViewModel·SignUpViewModel의 @Observable 스펙 | kotlin-expert |
| `SCREENS.md` | SwiftUI View 명세 (화면별 의존 ViewModel·진입 경로) | kotlin-expert |
| `FIRESTORE.md` | 트랜잭션 6곳·리스너(carryOver)·Repository | kotlin-expert |
| `PUSH.md` | APNs 등록·FCM 6종 핸들링·Time Sensitive·Delivery ACK | swift-expert |
| `AUTH.md` | 로그인 flow·collectionGroup·Keychain·자동로그인 | kotlin-expert |
| `SETTINGS.md` | Info.plist·Capabilities·Background Modes·URL Schemes·Firebase 설정 | swift-expert |

**최종 검토**: swift-expert가 kotlin-expert 초안에 iOS 관용구 수정 + 컴파일 가능성 검증.

---

## 완료 후 전환

Phase 1 → **TestFlight 파일럿** → **정식 출시** → **운영 데이터 수집** → `REINFORCEMENT/TRIGGERS.md`의 발동 기준 충족 시 해당 보강 도입.

---

## 참조

- `ios/FUNCTIONAL_INVENTORY.md` — 분류 근거
- `ios/SHARED_LOGIC.md` — 언어 독립 로직 명세
- `ios/WORKING_DOC.md` — 주제 1~5 결정 근거
- `ios/README.md` — iOS 전체 전략
- `CLAUDE.md` — Firestore 경로·상태 전이·정산 공식
- `memory/pending-work.md` — AND-01~AND-06 (Android 측 선행 이슈)

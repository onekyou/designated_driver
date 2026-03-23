# 개발 환경

| 항목 | 내용 |
|------|------|
| **경로** | `C:\Users\kala1\designated_driver` |
| **역할** | 코드 수정, 빌드, 테스트, commit/push 모두 로컬에서 수행 |
| **브랜치** | `firestore-migration-backup` |
| **빌드** | JAVA_HOME=`/c/Program Files/Android/Android Studio/jbr`, ANDROID_HOME 환경변수 필수 |

### 연결된 기기
| 기기 | 시리얼 | 설치 앱 |
|------|--------|---------|
| **SM-G996N** S21+ | R3CR312MB1L | call_manager, driver_app |
| **SM-S901N** S22 | R5CT41TJZFP | call_detector, driver_app |
| **SM-F721N** Z Flip4 | R3CT80K78NP | driver_app, customer_app |

### 로그캣
테스트 시 각 기기별 터미널에서 실시간 모니터링 준비할 것:
```bash
ADB="$ANDROID_HOME/platform-tools/adb"
$ADB -s R3CR312MB1L logcat -s CallManager_FCM,DashboardViewModel,DriverRepository,PendingDriversViewModel
$ADB -s R5CT41TJZFP logcat -s CallDetector,DispatchActivity
$ADB -s R3CT80K78NP logcat -s DriverApp,DriverViewModel,MyFirebaseMessagingService
```

### 워크플로우
```
[로컬] 코드 수정 → 빌드 → 기기 설치 → 테스트 → commit → push
```

---

# 세션 명령어

| 말하면 | 실행 내용 |
|--------|----------|
| **"시작해줘"** 또는 **"깃풀해줘"** | `git pull origin <현재브랜치>` |
| **"종료해줘"** 또는 **"깃체크해줘"** | `bash git-check.sh` → 상태 확인 |
| **"시뮬레이션해줘"** | `.agent-teams/simulation/PLAYBOOK.md` 읽기 → `STATE.md` 확인 → 즉시 실행 |

### Claude 실행 규칙

**시작 시:**
1. `git pull origin <현재브랜치>` 실행
2. `bash git-check.sh` 실행

**종료 시:**
1. `bash git-check.sh` 실행
2. [SAFE] → "안전하게 종료 가능" 안내
3. [WARNING] → 로컬 변경사항 있음 → commit/push 필요 여부 확인

---

# 실행원칙

1. 모든 대답은 속도에 연연하지말고 심사숙고해서 두번 이상 검토해서 내놓을것
2. 하드코딩은 절대 안돼 항상 정석으로 진행할것
3. 수정전에는 항상 허락을 구할 것
4. 요구한것 이상의 수정을 하지말것. 요구한것에 도움이 되는것은 제안을 하고 허락을 구할 것. 임의로 수정하지말것.
5. 정확한 답이 아닌 경우 혹은 모호한 경우에는 외부검색을 통해 근접한 대답을 추론하여 실행전 사실대로 말해 허락을 구할 것
6. **코드 수정이 필요한 요청을 받으면 반드시 플랜모드로 먼저 진입할 것.**
   - 플랜모드에서 Grep + Read로 관련 코드를 모두 조사한 후, 사용자 승인을 받고 나서만 수정 진행.
   - 플랜모드 없이 코드 수정을 제안하거나 실행하는 것을 금지한다.
   - 버그 분석 시: ① CLAUDE.md "앱별 데이터 아키텍처" 확인 ② `memory/project-characteristics.md` 읽기 ③ 관련 코드 Grep ④ 모든 경로 Read ⑤ 보고

---

# 프로젝트 개요

지방 대리운전 회사 통합 관리 플랫폼. 관리자 1명이 폰 3대로 콜 수신 + 배차 + 픽업을 처리하는 환경.
앱: call_detector, call_manager, driver_app, customer_app, functions(Cloud Functions)

---

# Firestore 데이터 구조

```
provinces/{p}/cities/{c}/offices/{o}/
  ├── designated_drivers/{uid}   기사 (승인 후 생성)
  ├── pickup_drivers/{uid}       픽업기사
  ├── calls/{callId}             콜 (Detector/Manager가 생성)
  ├── customers/{uid}            고객 (customer_app 가입 시)
  ├── customerInfo/{phone}       고객 FCM 토큰
  ├── settings/attribution       QR/귀속 설정
  ├── managerTokens/{uid}        관리자 FCM 토큰
  ├── dailySettlements/          일일 정산
  └── settlementSessions/        정산 세션

pending_drivers/{uid}            가입 대기 (승인 후 삭제)
admins/{uid}                     관리자 (associatedOfficeId 포함)
shared_calls/{callId}            공유콜 (영업시간 외)
```

---

# 앱별 데이터 아키텍처

## Call Detector (전화 감지 + 즉시 배차)
- **저장소**: Firestore 직접 (Room DB 미사용, FCM 미수신)
- **콜 감지**: BroadcastReceiver (PHONE_STATE) → CallDetectorService (Foreground Service)
- **콜 생성**: createNormalCall() → offices/{o}/calls (영업중), createSharedCall() → shared_calls (영업외)
- **배차**: DispatchActivity에서 **Firestore 실시간 리스너**로 기사 목록 조회 (WAITING/ONLINE), runTransaction으로 배차
- **사무실 정보**: SharedPreferences (`CallDetectorPrefs`에서 provinceId/cityId/officeId/deviceName)
- **로그인**: admins/{uid}에서 associatedOfficeId 읽어옴
- **중복 방지**: 5초(서비스레벨) + 10초(Firestore 쿼리) 이중 체크
- **FCM 전송 안 함**: 배차 후 Cloud Functions `oncallassigned` 트리거가 FCM 전송

## Call Manager (관리자 종합 관리) ★ Local-First
- **저장소**: Room DB (로컬) + Firestore (원격)
- **기사 목록**: `refreshData()`로 Firestore → Room DB 초기 로드, 이후 FCM `DRIVER_STATUS_UPDATE`로 기존 레코드 상태 UPDATE만 (**새 기사 INSERT 안 함**)
- **콜 목록**: `refreshData()`로 초기 로드 + Firestore 실시간 리스너 (1시간 내 미완료 콜 백업)
- **UI 반영**: Room DB Flow → StateFlow → Compose (DB 변경 시 자동 emit)
- **FCM 메시지 타입**: NEW_CALL, DRIVER_STATUS_UPDATE, CALL_STATUS_UPDATE, STATUS_CHANGE, NEW_SHARED_CALL, DRIVER_APPROVAL_REQUEST
- **⚠️ 알려진 제약**: 새 기사 승인(PendingDriversViewModel.approveDriver) 후 Room DB에 자동 INSERT 안 됨 → `refreshData()` 호출 필요
- **사무실 정보**: SharedPreferences에서 provinceId/cityId/officeId 읽음
- **배차/취소/공유**: DashboardViewModel에서 runTransaction 사용

## Driver App (기사용 운행 + 정산)
- **저장소**: Firestore 직접 + Room DB (정산 캐시만, SettlementRepository)
- **콜 수신**: FCM push (`call_assigned`) → 포그라운드: LocalBroadcast, 백그라운드: FullScreenIntent
- **현재 콜**: 앱 시작 시 1회 조회 `.get().await()` (리스너 아님, 비용 최적화 ~$414/월 절감)
- **상태 변경**: runTransaction으로 Firestore 직접 업데이트 (수락/거절/시작/완료/정산)
- **로그인**: `collectionGroup("designated_drivers")` 쿼리로 authUid 기반 기사 문서 찾기 → SharedPreferences에 provinceId/cityId/officeId 저장
- **이월금**: 유일한 실시간 리스너 (`carryOverListener`)
- **FCM 메시지 타입**: call_assigned, call_cancelled, SETTLEMENT_FINALIZED

## Customer App (고객용, 미배포)
- **인증**: Anonymous Auth (익명인증, 자동 로그인)
- **프로필 저장**: offices/{o}/customers/{uid} + customerInfo/{phone}
- **사무실 매칭**: QR → Play Store → Install Referrer 자동 매칭 (핵심 온보딩 흐름)
- **플로우**: QR스캔 → 플레이스토어 → 앱설치 → Install Referrer → 약관동의 → 프로필설정

## Cloud Functions (41개)
- **트리거**: onDocumentWritten (배차 FCM `oncallassigned`, 상태변경 FCM, 공유콜 알림 등)
- **Callable**: notifyDriverCancellation (관리자 취소 시 기사에게 FCM)
- **Scheduled**: checkAssignedTimeout (매 1분, 3분 미수락 시 WAITING 복귀)
- **정산**: onCallCompletedUpdateSettlement (COMPLETED 시 자동 정산 세션 추가)

---

# 상태 전이 맵

### 콜 상태
```
WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED
                ↓         ↓            ↓
             (타임아웃)  (거절)      (운행취소)
             → WAITING  → WAITING    → HOLD → 재배차 → ASSIGNED

취소: CANCELED (관리자), CANCELLED_BY_DRIVER (기사), CANCELLED_BY_CUSTOMER (고객)
공유: WAITING → SHARED_WAITING → (타 사무실 수임) CLAIMED
```

### 기사 상태
```
ONLINE/WAITING → ASSIGNED → PREPARING → ON_TRIP → WAITING (완료 후 복귀)
```

### 정산 플로우
```
Driver submitDailySettlement → PENDING_CONFIRM
Manager confirmDailySettlement → CONFIRMED
Manager transferCarryOver → TRANSFERRED
Driver confirmReceiveCarryOver → SETTLED
```

### 정산 공식
```
officeDeposit = totalFare × depositRatio / 100  (사무실 몫)
driverShare = totalFare - officeDeposit          (기사 몫)
finalDeposit = officeDeposit - totalCredit       (이체/포인트/외상 차감 후 실제 납입 대상)
realDeposit = 기사가 UI에서 입력한 실 납입액     (기본값: cashReceived - driverShare)
carryOver = originalCarryOver - finalDeposit + realDeposit
```
- totalCredit = totalFare - totalCashReceived (현금으로 받지 않은 금액)
- isIntegration: 이전 dailySettlement.status == PENDING_CONFIRM일 때만 통합 제출 발동

---

# Agent Teams 운영 규칙

## 팀 구성
| 팀원 | 담당 | 컨텍스트 파일 |
|------|------|--------------|
| detector-analyst | call_detector 앱 | `.agent-teams/detector-analyst.md` |
| manager-analyst | call_manager 앱 | `.agent-teams/manager-analyst.md` |
| driver-analyst | driver_app 앱 | `.agent-teams/driver-analyst.md` |
| firebase-analyst | Cloud Functions + Firebase | `.agent-teams/firebase-analyst.md` |

## 필수 규칙
1. **팀 해체 금지**: 사용자가 "팀 해체해" 또는 "팀 삭제해"라고 명시하기 전까지 팀 유지
2. **팀원 소환 시**: 반드시 자기 담당 컨텍스트 파일 + `TEAM_OVERVIEW.md`를 먼저 읽을 것
3. **임무 완료 후**: 결과를 보고하고 다음 지시 대기 (임의 행동 금지)
4. **"준비해줘" = 구성/정리까지만**. 실행은 별도 명령을 기다릴 것

---

# 상세 참조 파일

| 상황 | 파일 |
|------|------|
| 앱별 코드 맵 (함수/파일/라인) | `memory/project-characteristics.md` |
| 이슈 수정 이력 | `memory/issue-history.md` |
| 잔여 작업/배포 상태 | `memory/pending-work.md` |
| Firestore 접근 유틸 | `functions/scripts/firestore-util.js` |
| 정산 상세 분석 | `memory/settlement_analysis.md` |
| 팀 운영 현황 | `.agent-teams/TEAM_OVERVIEW.md` |

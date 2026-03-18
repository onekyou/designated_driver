# 실기기 파일럿 시뮬레이션 시나리오 v9

> 작성일: 2026-03-08
> 목적: 코드 시뮬레이션에서 검증 불가능한 실기기 환경 이슈 테스트
> 대상 기기: SM_A325N (call_manager), SM_G996N (driver_app), + call_detector 기기 3대

---

## 코드 분석 결과 발견된 실제 리스크

### CRITICAL (즉시 검증 필요)
| # | 이슈 | 위치 | 설명 |
|---|------|------|------|
| R1 | 3대 Detector 중복 콜 생성 | CallDetectorService | query-then-write 패턴 비원자적. 3대가 동시에 같은 전화 감지 시 3건 중복 생성 가능 |

### HIGH (파일럿 중 검증)
| # | 이슈 | 위치 | 설명 |
|---|------|------|------|
| R2 | CF 비트랜잭션 기사 상태 | index.ts L1232 | 공유콜 수임 후 기사 status 업데이트가 트랜잭션 밖 |
| R3 | CF 비트랜잭션 processed 플래그 | index.ts L1241 | shared_calls processed=true가 비원자적 → 이중 처리 가능 |
| R4 | Call Detector 오프라인 큐 없음 | CallDetectorService | 네트워크 끊김 시 전화 감지 데이터 유실 |

### MEDIUM (모니터링)
| # | 이슈 | 위치 | 설명 |
|---|------|------|------|
| R5 | SharedPreferences 동시 접근 | 전 앱 | 멀티스레드 환경에서 읽기/쓰기 충돌 가능 |
| R6 | Firestore 오프라인 미설정 | 전 앱 | enablePersistence 미호출 → 오프라인 시 읽기 실패 |
| R7 | Driver App 부팅 자동시작 없음 | driver_app | BOOT_COMPLETED 리시버 미구현 → 재부팅 시 수동 실행 필요 |

---

## 테스트 환경 구성

### 필요 기기
| 역할 | 기기 | 앱 | 비고 |
|------|------|------|------|
| Detector 1 | 사무실 전화기 1 | call_detector | 전화 감지 |
| Detector 2 | 사무실 전화기 2 | call_detector | 전화 감지 |
| Detector 3 | 사무실 전화기 3 | call_detector | 전화 감지 |
| Manager | SM_A325N | call_manager | 배차 관리 |
| Driver 1 | SM_G996N | driver_app | 기사 1 |
| Driver 2 | (추가 기기) | driver_app | 기사 2 (가능 시) |
| Customer | (추가 기기) | customer_app | 고객 (가능 시) |

### 사전 준비
1. 모든 기기에 배터리 최적화 제외 설정
2. Firebase Console에서 Firestore 콘솔 열어두기 (실시간 모니터)
3. `adb logcat` 2개 터미널 연결 (Manager + Driver)

---

## Phase A: 네트워크 장애 시나리오 (4건)

### A1. Call Detector 네트워크 끊김 중 전화 수신
**리스크**: R4 (Call Detector 오프라인 큐 없음)

**절차**:
1. Detector 기기의 WiFi/데이터 끄기
2. 사무실 번호로 전화 걸기
3. 전화 종료 후 15초 대기
4. WiFi/데이터 켜기

**확인 항목**:
- [ ] 오프라인 중 전화 감지가 되는가?
- [ ] 오프라인 중 Firestore 쓰기가 큐잉되는가, 유실되는가?
- [ ] 네트워크 복구 후 콜이 자동 생성되는가?
- [ ] logcat에서 에러 메시지 확인

**예상 결과**: Firestore 오프라인 미설정 시 → **콜 유실** (enablePersistence 미호출)
**코드 위치**: `CallDetectorService.kt` - saveCallToFirestore()

---

### A2. Driver App 운행 중 네트워크 끊김
**리스크**: R6 (Firestore 오프라인 미설정)

**절차**:
1. 기사가 콜 수락 후 IN_PROGRESS 상태 진입
2. Driver 기기의 WiFi/데이터 끄기
3. 기사가 "운행 완료" 버튼 터치
4. 10초 대기 후 WiFi/데이터 켜기

**확인 항목**:
- [ ] 오프라인 중 completeCall() 호출 시 에러 표시되는가?
- [ ] 아니면 Firestore 로컬 캐시에 저장되는가?
- [ ] 네트워크 복구 후 상태 전이가 정상 반영되는가?
- [ ] 정산 데이터에 누락 없는가?
- [ ] OfflineScreen UI가 표시되는가?

**예상 결과**: Driver App에는 NetworkMonitor + OfflineScreen이 있으므로 UI는 표시됨.
단, Firestore 쓰기 성공 여부는 오프라인 캐시 설정에 따라 다름.
**코드 위치**: `DriverViewModel.kt` completeCall(), `NetworkMonitor.kt`

---

### A3. Manager App 배차 중 네트워크 끊김
**리스크**: R6

**절차**:
1. 새 콜이 WAITING 상태로 도착
2. Manager 기기의 WiFi/데이터 끄기
3. 기사 선택 후 배차 버튼 터치
4. 10초 대기 후 WiFi/데이터 켜기

**확인 항목**:
- [ ] 트랜잭션 실패 에러가 사용자에게 표시되는가?
- [ ] 콜이 WAITING 상태로 유지되는가?
- [ ] 네트워크 복구 후 재배차 가능한가?
- [ ] 이중 배차가 발생하지 않는가?

**코드 위치**: `DashboardViewModel.kt` assignCallToDriver()

---

### A4. 고객 앱 콜 요청 후 네트워크 끊김
**리스크**: R6

**절차**:
1. 고객 앱에서 대리운전 호출
2. 호출 직후 WiFi/데이터 끄기
3. 30초 대기
4. WiFi/데이터 켜기

**확인 항목**:
- [ ] 호출이 Firestore에 정상 저장되었는가?
- [ ] 오프라인 중 상태 업데이트(ASSIGNED 등) FCM을 놓치는가?
- [ ] 네트워크 복구 후 현재 콜 상태를 정상 조회하는가? (getActiveCall fallback)
- [ ] FCM 미수신 시 폴링/실시간 리스너로 보정되는가?

**코드 위치**: `CallService.kt` requestCall(), getActiveCall()

---

## Phase B: FCM 수신 시나리오 (5건)

### B1. 앱 포그라운드에서 FCM 수신
**절차**:
1. Driver App을 화면에 띄워둠
2. Manager에서 배차 실행
3. Driver App 화면 변화 확인

**확인 항목**:
- [ ] LocalBroadcast로 즉시 UI 업데이트 되는가?
- [ ] 시스템 알림 대신 인앱 팝업이 표시되는가?
- [ ] 팝업에서 수락/거절 버튼이 정상 동작하는가?

**코드 위치**: `MyFirebaseMessagingService.kt` (driver_app) onMessageReceived() → isAppInForeground() 분기

---

### B2. 앱 백그라운드에서 FCM 수신
**절차**:
1. Driver App 실행 후 홈 버튼으로 백그라운드 전환
2. Manager에서 배차 실행
3. 시스템 알림 확인

**확인 항목**:
- [ ] 시스템 알림(heads-up)이 표시되는가?
- [ ] 알림 터치 시 앱으로 이동하는가?
- [ ] 잠금화면에서 알림이 표시되는가? (VISIBILITY_PUBLIC)
- [ ] DriverForegroundService가 정상 시작되는가?

**코드 위치**: `MyFirebaseMessagingService.kt` → DriverForegroundService 시작, IMPORTANCE_HIGH 채널

---

### B3. 앱 Kill 후 FCM 수신
**리스크**: 앱 프로세스 종료 후 FCM 처리

**절차**:
1. Driver App 실행 후 최근 앱 목록에서 스와이프하여 Kill
2. 1분 대기
3. Manager에서 배차 실행
4. 30초 대기

**확인 항목**:
- [ ] FCM이 시스템에 의해 수신되는가?
- [ ] 시스템 알림이 표시되는가?
- [ ] 알림 터치 시 앱이 정상 시작되는가?
- [ ] 앱 시작 후 배차된 콜 정보가 정상 표시되는가?
- [ ] FCM 토큰이 유효한가? (retryPendingFcmToken 확인)

**코드 위치**: `MyFirebaseMessagingService.kt`, MainActivity.onCreate() → retryPendingFcmToken()

---

### B4. 기기 재부팅 후 서비스 자동 시작
**리스크**: R7 (Driver App BOOT_COMPLETED 미구현)

**절차**:
1. 각 기기 재부팅
2. 잠금 해제 후 30초 대기 (앱 수동 실행 X)
3. 각 앱의 서비스 상태 확인

**확인 항목**:
| 앱 | 자동 시작 예상 | 확인 |
|----|---------------|------|
| Call Detector | O (BootCompletedReceiver 있음) | [ ] |
| Call Manager | O (BootReceiver 있음, 로그인 상태일 때) | [ ] |
| Driver App | **X** (BootReceiver 없음) | [ ] |

- [ ] Call Detector: "service_stopped_by_user" 플래그에 따라 동작 확인
- [ ] Call Manager: 로그아웃 상태에서 재부팅 시 서비스 미시작 확인
- [ ] **Driver App: 재부팅 후 수동 실행 필요 → 기사에게 안내 필요**

**코드 위치**:
- `call_detector/BootCompletedReceiver.kt`
- `call_manager/receiver/BootReceiver.kt`
- driver_app: 미구현

---

### B5. 배터리 최적화(Doze) 환경에서 FCM 수신
**절차**:
1. 기기를 충전기에서 분리
2. 화면 끄고 30분 방치 (Doze 진입)
3. Manager에서 배차 실행
4. FCM 수신 시간 측정

**확인 항목**:
- [ ] Doze 모드에서 FCM high-priority 메시지가 즉시 전달되는가?
- [ ] 배터리 최적화 제외 설정이 유지되고 있는가?
- [ ] 지연이 발생한다면 몇 초/분인가?

**참고**: FCM high-priority는 Doze에서도 전달되어야 하지만, OEM별 차이 존재
**코드 위치**: `MyFirebaseMessagingService.kt`, AndroidManifest.xml 권한 선언

---

## Phase C: 전화 감지 시나리오 (4건)

### C1. 동시 전화 수신 - 3대 Detector 중복 검증 ★★★
**리스크**: R1 (CRITICAL - 3대 중복 콜 생성)

**절차**:
1. Firestore Console에서 calls 컬렉션 모니터링
2. 사무실 번호로 전화 1통 걸기
3. 3대 Detector의 logcat 동시 모니터링
4. Firestore에서 생성된 콜 문서 수 확인

**확인 항목**:
- [ ] 콜 문서가 **정확히 1건**만 생성되는가?
- [ ] 아니면 2~3건 중복 생성되는가?
- [ ] 중복 감지 로직(DUPLICATE_CALL_CHECK_MS)이 동작하는가?
- [ ] 3대 중 어떤 기기가 콜을 생성했는가? (deviceName 필드)

**예상 위험**:
```
Phone D1: query("555-1234", last 10sec) → 0건 → add() 실행
Phone D2: query("555-1234", last 10sec) → 0건 → add() 실행  ← 동시!
Phone D3: query("555-1234", last 10sec) → 0건 → add() 실행  ← 동시!
```
→ query-then-write가 원자적이지 않으므로 3건 중복 가능

**코드 위치**: `CallDetectorService.kt` - 중복 체크 로직 (query → add 패턴)

---

### C2. 빠른 연속 전화 (10초 내 2통)
**절차**:
1. 사무실 번호로 전화 1통 걸고 바로 끊기
2. 5초 후 다른 번호로 전화 1통 걸기
3. Firestore 확인

**확인 항목**:
- [ ] 서로 다른 번호 → 2건 정상 생성
- [ ] 같은 번호로 5초 내 재전화 → 중복 필터링 동작 확인
- [ ] DUPLICATE_CALL_CHECK_MS 값이 적절한가?

---

### C3. 부재중 전화 (벨 3회 후 끊김)
**절차**:
1. 사무실 번호로 전화 걸고 벨 3회만 울리고 끊기
2. Detector가 전화를 감지하는지 확인
3. 콜 문서가 생성되는지 확인

**확인 항목**:
- [ ] 짧은 벨에도 전화 감지가 되는가?
- [ ] 전화 상태 변화(RINGING → IDLE) 감지 타이밍 확인
- [ ] 제외번호 필터가 정상 동작하는가?

**코드 위치**: `CallDetectorService.kt` - PhoneStateListener / TelephonyCallback

---

### C4. 통화 중 다른 전화 수신 (Call Waiting)
**절차**:
1. Detector 기기에서 통화 중 상태 만들기
2. 다른 번호로 전화 걸기 (캐치콜)
3. 두 번째 전화가 감지되는지 확인

**확인 항목**:
- [ ] 캐치콜이 별도 콜로 감지되는가?
- [ ] 기존 통화에 영향이 있는가?
- [ ] 상태 변화가 올바른가? (OFFHOOK → RINGING → OFFHOOK)

---

## Phase D: 동시성 시나리오 (5건)

### D1. 두 Manager가 동시에 같은 콜 배차 시도
**절차**:
1. WAITING 상태 콜 1건 생성
2. 두 기기에서 call_manager 실행 (또는 한 기기에서 빠르게 2회 터치)
3. 동시에 다른 기사에게 배차 시도

**확인 항목**:
- [ ] runTransaction이 이중 배차를 방지하는가?
- [ ] 한 쪽은 성공, 다른 쪽은 에러 메시지 표시
- [ ] 에러 메시지가 사용자에게 친절한가? ("이미 배차된 콜입니다")
- [ ] 기사 상태가 정확한가? (한 명만 ASSIGNED)

**코드 위치**: `DashboardViewModel.kt` assignCallToDriver() - runTransaction

---

### D2. 기사 수락과 Manager 취소 동시 발생
**절차**:
1. 콜을 기사에게 배차 (ASSIGNED)
2. 기사가 "수락" 버튼 터치하는 동시에 Manager가 "취소" 터치
3. 최종 상태 확인

**확인 항목**:
- [ ] 두 트랜잭션 중 하나만 성공하는가?
- [ ] 성공한 쪽의 상태가 일관성 있는가?
- [ ] 실패한 쪽에 에러 메시지가 표시되는가?
- [ ] 기사 상태(ACCEPTED vs WAITING)가 콜 상태와 일치하는가?

**코드 위치**:
- `DriverViewModel.kt` acceptCall() - runTransaction (status==ASSIGNED 확인)
- `DashboardViewModel.kt` cancelCall() - runTransaction (status 확인)

---

### D3. 공유콜 이중 수임 테스트
**리스크**: R3 (processed 플래그 비트랜잭션)

**절차**:
1. Manager에서 콜을 "공유" 처리
2. 다른 사무실 두 곳에서 동시에 "수임" 시도

**확인 항목**:
- [ ] claimSharedCall 트랜잭션이 이중 수임을 방지하는가?
- [ ] shared_calls 문서의 status가 정확히 한 번만 CLAIMED로 변경되는가?
- [ ] processed 플래그가 비원자적이라 CF 이중 실행이 발생하는가?
- [ ] 최종적으로 하나의 사무실에만 콜이 복사되는가?

**코드 위치**:
- `DashboardViewModel.kt` claimSharedCallWithDetails() - runTransaction
- `index.ts` onSharedCallClaimed L1241 - processed=true (비트랜잭션)

---

### D4. 정산 동시 제출 (같은 기사 2번 제출)
**절차**:
1. 기사가 정산 화면에서 "제출" 버튼 빠르게 2회 터치
2. Firestore에서 settlement 문서 확인

**확인 항목**:
- [ ] 정산이 1건만 생성되는가?
- [ ] UI에서 더블탭 방지가 동작하는가?
- [ ] 두 번째 요청이 에러 처리되는가?

**코드 위치**: `DriverViewModel.kt` submitDailySettlement()

---

### D5. Customer 더블탭 방지 테스트
**절차**:
1. 고객 앱에서 "대리운전 호출" 버튼 빠르게 2회 터치
2. Firestore에서 콜 문서 확인

**확인 항목**:
- [ ] UI 더블탭 방지가 동작하는가? (10차 시뮬에서 수정됨)
- [ ] 콜이 1건만 생성되는가?
- [ ] 두 번째 터치 시 적절한 피드백 제공

**코드 위치**: Customer App - 호출 화면 (10차 스트레스 테스트에서 수정된 부분)

---

## Phase E: 앱 Kill & 복구 시나리오 (3건)

### E1. Driver App Kill 후 운행 중 콜 복구
**절차**:
1. 기사가 콜 수락 → IN_PROGRESS 상태
2. 최근 앱 목록에서 Driver App 강제 종료
3. Driver App 재실행

**확인 항목**:
- [ ] 재실행 시 기존 IN_PROGRESS 콜이 자동 복구되는가?
- [ ] 기사 상태가 ON_TRIP으로 유지되는가?
- [ ] "운행 완료" 버튼이 바로 사용 가능한가?

**코드 위치**: `DriverViewModel.kt` - init 블록, assignedCallsListener

---

### E2. Call Manager Kill 후 콜 목록 복구
**절차**:
1. Call Manager에서 WAITING 콜 3건 + ASSIGNED 콜 2건 확인
2. 최근 앱 목록에서 Call Manager 강제 종료
3. Call Manager 재실행

**확인 항목**:
- [ ] 재실행 시 모든 콜이 정상 표시되는가?
- [ ] Firestore 스냅샷 리스너가 재연결되는가?
- [ ] Call Detector 서비스가 영향받는가? (별도 프로세스 확인)

---

### E3. Call Detector Service Kill 복구
**절차**:
1. Call Detector 서비스 실행 중
2. `adb shell am force-stop com.designated.calldetector`
3. 30초 대기
4. 전화 걸어보기

**확인 항목**:
- [ ] START_STICKY에 의해 서비스가 자동 재시작되는가?
- [ ] 재시작 후 전화 감지가 정상 동작하는가?
- [ ] Foreground notification이 복구되는가?
- [ ] 재시작까지 걸리는 시간은?

**코드 위치**: `CallDetectorService.kt` onStartCommand() → START_STICKY

---

## 테스트 실행 순서 (권장)

| 순서 | Phase | 예상 시간 | 우선도 |
|------|-------|----------|--------|
| 1 | **C1** (3대 중복 감지) | 10분 | ★★★ CRITICAL |
| 2 | **A1** (Detector 오프라인) | 10분 | ★★★ |
| 3 | **B3** (앱 Kill FCM) | 10분 | ★★ |
| 4 | **B4** (재부팅 자동시작) | 15분 | ★★ |
| 5 | **D1** (이중 배차) | 10분 | ★★ |
| 6 | **D2** (수락/취소 경합) | 10분 | ★★ |
| 7 | **A2** (Driver 오프라인) | 10분 | ★★ |
| 8 | **D3** (공유콜 이중수임) | 10분 | ★★ |
| 9 | **B1, B2** (FCM 포/백그라운드) | 10분 | ★ |
| 10 | **E1~E3** (Kill 복구) | 15분 | ★ |
| 11 | **C2~C4** (전화 감지 변형) | 15분 | ★ |
| 12 | **A3, A4** (Manager/Customer 오프라인) | 10분 | ★ |
| 13 | **D4, D5** (더블탭) | 5분 | ★ |
| 14 | **B5** (Doze 모드) | 40분 | △ |

**총 예상 시간: 약 3시간**

---

## 테스트 결과 기록 템플릿

```
### [시나리오 ID] 결과
- 일시: YYYY-MM-DD HH:MM
- 기기:
- 결과: PASS / FAIL / PARTIAL
- 상세:
  -
- logcat 주요 로그:
  ```

  ```
- Firestore 스크린샷: (있으면)
- 후속 조치:
```

---

## 발견 시 즉시 조치 가이드

### R1 중복 콜 발견 시 (CRITICAL)
→ CallDetectorService의 중복 체크를 Firestore Transaction 또는
   deterministic document ID (phoneNumber + timestamp 기반)로 변경

### R4 오프라인 콜 유실 발견 시
→ 옵션 1: Firestore enablePersistence() 활성화
→ 옵션 2: Room DB 로컬 큐 + WorkManager 동기화 (Driver App 패턴 차용)

### R7 Driver App 재부팅 후 미시작
→ BootCompletedReceiver 추가 (call_detector 패턴 참고)
→ 단, 기사가 의도적으로 앱을 종료한 경우 존중 (플래그 체크)

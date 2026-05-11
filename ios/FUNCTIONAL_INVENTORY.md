# 기사앱 기능 인벤토리 (iOS 포팅 기준)

> **목적**: 기사앱의 모든 기능을 라이프사이클 단계별로 전수 인벤토리. 콜매니저·손님앱과의 연동 포인트, 각 단계의 취소 경로, 오프라인 동작 포함.
> **산출 시점**: Phase 0 (kotlin-expert 작성, 2026-04-15)
> **다음 단계**: 사용자 + 중재자가 각 항목을 기본/보강으로 분류 (Phase A)

---

## 평가 차원

각 라이프사이클 단계에 대해:
- **Happy Path**: 정상 네트워크 + 정상 흐름
- **Cancellation**: 발생 가능한 모든 취소 (관리자/기사/손님/타임아웃)
- **Offline**: 네트워크 끊김 시 동작 (자동 큐잉 / 명시적 처리 / 불가능)

분류 라벨 (Phase A에서 채움):
- **B** (Base) — 기본 기능, 1차 출시 필수
- **R1** (Reinforcement priority 1) — 1차 보강, Phase 1 운영 후 우선 도입
- **R2** (Reinforcement priority 2) — 2차 보강, 데이터 기반 결정
- **D** (Defer / 미적용 검토) — Phase 2에서도 도입 보류 가능

---

## 1. 라이프사이클 단계 (콜 1순환)

### 1.1 콜매니저 배차 → 기사앱 수신
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Happy Path | 콜매니저가 `calls/{id}` 문서에 `assignedDriverId` + `status=ASSIGNED` 설정 → CF `oncallassigned`가 대상 기사 FCM 토큰 조회 후 `call_assigned` data-only push 발송 → 기사앱 `MyFirebaseMessagingService.onMessageReceived`에서 `messageType=="call_assigned"` 분기 처리 | `functions/src/index.ts:478` (CF), `MyFirebaseMessagingService.kt:148-176` | **B** | B 가능: FCM iOS SDK + APNs Time Sensitive(`interruptionLevel = .timeSensitive`). R: VoIP push + CallKit reportNewIncomingCall + Live Activity push-to-start로 강화 |
| Cancellation - 관리자 취소 | 콜매니저가 콜 문서 `status=CANCELED` 변경 → `onCallStatusChanged` CF가 기사에게 `call_cancelled` FCM → `MyFirebaseMessagingService.kt:177-188`에서 `ACTION_CALL_CANCELLED` LocalBroadcast + "콜 취소" 시스템 알림 | `functions/src/index.ts:1810`, `MyFirebaseMessagingService.kt:177-188` | **B** | B 가능: UNUserNotificationCenter alert + NotificationCenter post(내부 브로드캐스트). R: VoIP 시나리오 시 CXProvider.reportCall(..., endedAt:) 로 CallKit UI 강제 종료 |
| Cancellation - 타임아웃 (3분) | CF 스케줄러가 매 1분 실행, `ASSIGNED` 상태가 3분 초과 시 콜을 `WAITING`로 복귀 + 기사를 `WAITING` 복귀 + 관리자 FCM. 기사앱은 별도 UI 처리 없음(리스너가 `assignedCalls` 목록에서 제거) | `functions/src/index.ts:3457` (`checkAssignedTimeout`) | **B** | B 가능: 서버측 로직이라 iOS 영향 없음. Firestore 리스너 AsyncStream이 목록에서 자동 제거 |
| Offline - 기사 폰 | FCM 미수신 → 앱이 콜 자체를 인지 못 함. 앱 진입 시 `loadCurrentActiveCall()`이 Firestore `whereIn(status, [ASSIGNED, ACCEPTED, IN_PROGRESS, AWAITING_SETTLEMENT])` 1회 조회로 복구 | `DriverViewModel.kt:238-267` | **B** | B 가능: 앱 foreground 진입 시 `.task {}`에서 1회 `getDocuments().await()` 조회. 구조 동일 |
| Offline - CF/서버 | CF 트리거 자체는 Firestore 문서 변경 시 자동 재실행(Firebase 내부 재시도). FCM 발송 실패는 로깅만, 재시도 없음 (`simulation_verification_v2.md:60`: "FCM 실패가 콜 흐름을 차단하면 안 됨") |  | **B** | iOS 무관 (서버 측) |

### 1.2 기사 수락 → 콜매니저+손님앱 통지
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Happy Path | `NewCallPopup`/`HomeScreen`에서 수락 버튼 → `acceptCall(callId)`. 낙관적 UI(ACCEPTED + PREPARING 즉시 반영) → 백그라운드 Firestore 트랜잭션 | `DriverViewModel.kt:371-475` | **B** | B 가능: @Observable uiState의 copy-write + async Firestore transaction. 낙관적 UI → rollback 패턴 Topic 2 합의와 일치 |
| Firestore 트랜잭션 (콜+기사 상태) | 단일 `runTransaction`으로 call.status ASSIGNED→ACCEPTED + driver.status →PREPARING. 콜이 ASSIGNED 아니면 `CALL_NOT_ASSIGNABLE` 예외. `_isAccepting` 플래그로 중복 클릭 방지 | `DriverViewModel.kt:427-452` | **B** | B 가능: `Firestore.firestore().runTransaction { tx, err in ... }` async wrapper. `_isAccepting` → @Observable var + guard |
| 콜매니저 FCM 통지 | `onCallStatusChanged` CF가 before=ASSIGNED/after=ACCEPTED 감지 → 매니저 `managerTokens`에 FCM 발송 | `functions/src/index.ts:1810-1891` | **B** | iOS 무관 (서버 측 CF) |
| 손님앱 FCM 통지 (DRIVER_ASSIGNED) | CF `oncallassigned`가 배차 시점에 손님에게 DRIVER_ASSIGNED FCM 전송 (`type: "DRIVER_ASSIGNED"`) | `functions/src/index.ts:616` | **B** | iOS 무관 (CF → 손님앱). 손님앱 iOS 포팅 시 별도 평가 필요 |
| Cancellation - 트랜잭션 실패 | catch 블록에서 UI 롤백: ACCEPTED→ASSIGNED 복구 + `newCallPopup` 재설정 + `errorMessage` 설정. `_isAccepting=false` finally 보장 | `DriverViewModel.kt:456-473` | **B** | B 가능: do/catch + defer { isAccepting = false }. uiState prevState 저장 후 복원 |
| Offline - 트랜잭션 큐잉 | Firestore SDK의 offline persistence가 자동 큐잉(커밋 대기). 앱은 성공 낙관 처리하고 네트워크 복구 시 자동 flush. 단 트랜잭션은 일반 write보다 큐잉 특성이 다름(읽기 필요) — 오프라인에서는 즉시 실패 가능 **확인 필요** |  | **B?** | B 가능: Firebase iOS SDK도 동일 offline persistence. 트랜잭션 오프라인 실패 동작도 동일(확인 필요). R: PendingSync(SwiftData) 큐로 후방어 |

### 1.3 기사 거절 → 콜 WAITING 복귀
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Happy Path (LockScreen 거절) | `LockScreenActivity.rejectCallDirectly()` 정의되어 있으나 **현재 UI에서 호출되지 않음**. CLAUDE.md 3/18 업데이트로 거절 버튼 제거, "확인" 버튼만 → MainActivity 이동 후 앱 내 처리 | `LockScreenActivity.kt:205-247` (dead code) | **D** | dead code — iOS 이관 대상 아님 |
| Happy Path (앱 내 거절) | `rejectCall(callId)` → `performFirestoreUpdate` 블록 내 단일 트랜잭션: call.status=WAITING + assignedDriverId=null + rejectedByDriver=uid, driver.status=WAITING | `DriverViewModel.kt:477-518` | **B** | B 가능: async Firestore transaction. R(VoIP 채택 시): CXEndCallAction delegate에서 이 경로 호출하여 CallKit 거절 UI 일관성 |
| 콜매니저 통지 | `onCallStatusChanged` CF (ACCEPTED→WAITING 또는 ASSIGNED→WAITING) → 매니저 FCM | `functions/src/index.ts:1810` | **B** | iOS 무관 (CF) |
| Offline 시 처리 | 거절 트랜잭션은 Firestore offline 시 **즉시 실패 가능성**. 현재 에러 핸들링은 공통 `performFirestoreUpdate` 내 try/catch (`DriverViewModel.kt:1014-1028`). 재시도 없음 | `DriverViewModel.kt:1014-1028` | **B** | B 가능(동일). R: SwiftData PendingSync 큐로 재시도 가능하나 거절은 시효성 있어 유지 의미 제한적 |

### 1.4 운행 시작 (PREPARING → ON_TRIP)
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Happy Path | `TripPreparationScreen`에서 출발지/도착지/경유지/요금 입력 후 "운행 시작" 버튼 → `startDriving(...)`. 낙관적 UI 후 Firestore 트랜잭션 | `DriverViewModel.kt:580-638` | **B** | B 가능: SwiftUI Form + @Observable uiState. 주소검색은 Kakao/네이버 지도 SDK iOS 버전 필요(2.7 참조) |
| Firestore 트랜잭션 | 단일 트랜잭션으로 call.status=IN_PROGRESS + departure_set/destination_set/waypoints_set/fare_set/trip_summary + driver.status=ON_TRIP | `DriverViewModel.kt:632-635` | **B** | B 가능: runTransaction iOS SDK 동일 |
| 콜매니저 통지 | `onCallStatusChanged` CF가 ACCEPTED→IN_PROGRESS 감지 → 매니저 FCM | `functions/src/index.ts:1810` | **B** | iOS 무관 (CF) |
| 손님앱 통지 (DRIVER_ARRIVING/ON_TRIP) | `onCallStatusChanged` CF 내부에서 고객 FCM도 함께 전송(`functions/src/index.ts:1931`). 실제 type 값은 **확인 필요** (CLAUDE.md 3/24: CUST-03 ACCEPTED/IN_PROGRESS FCM 추가 작업 이력 있음) | `functions/src/index.ts:1931` | **B** | iOS 무관 (CF → 손님앱) |
| Cancellation - 운행 직전 취소 (cancelTrip) | `cancelTrip(callId, reason)` → call.status=CANCELLED_BY_DRIVER, driver.status=WAITING. CLAUDE.md 3/24 변경: HOLD → CANCELLED_BY_DRIVER(확정 취소, 재배차 없음) | `DriverViewModel.kt:526-565` | **B** | B 가능: async transaction 단순 포팅 |
| 손님 취소 시 처리 (CANCELLED_BY_CUSTOMER) | 손님앱이 ACCEPTED/PREPARING 단계까지 취소 가능(3/24 확장). 기사앱은 `call_cancelled` FCM 수신 → `handleCallCancelled(callId)` → activeCall/newCallPopup/assignedCalls 정리 + `errorMessage`="고객이 콜을 취소했습니다" | `DriverViewModel.kt:907-922` | **B** | B 가능: FCM delegate에서 NotificationCenter post → @MainActor @Observable Store가 uiState 정리 |
| Offline 시 처리 | Firestore SDK 자동 큐잉 전제. UI는 즉시 IN_PROGRESS 반영 후 실제 커밋은 복구 시점 | — | **B** | B 가능(동일 SDK 동작) |

### 1.5 운행 중 (ON_TRIP)
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| 위치 추적 (사용 여부 확인) | `FusedLocationProviderClient` 주입되어 있으나 **실제 주기 추적 사용 없음**. `getAddressFromLocation`이 Geocoder 용도로만 호출 | `DriverViewModel.kt:150, 868-887` | **B** | B 가능: CoreLocation `CLGeocoder.reverseGeocodeLocation` 1회 호출. 주기 추적 없으면 Background Mode `location` 불필요 |
| 상태 표시 UI | `InProgressScreen.kt`에서 activeCall 정보 표시 + "운행 완료" 버튼. 실시간 위치/지도 없음 | `ui/screens/home/InProgressScreen.kt` | **B** | B 가능: SwiftUI View 단순 |
| Cancellation - 손님 취소 | IN_PROGRESS 단계: 손님앱에서는 취소 UI 없음(3/24 기준 ACCEPTED/PREPARING까지만). 만약 CF/매니저 개입으로 `CANCELLED_BY_CUSTOMER` 오면 `handleCallCancelled`가 activeCall null 처리 | `DriverViewModel.kt:907-922` | **B** | B 가능(동일 패턴) |
| Cancellation - 기사 운행 취소 | `cancelTrip`이 ON_TRIP 단계에서도 동일하게 작동 (상태 검증 없이 덮어씀) | `DriverViewModel.kt:526-565` | **B** | B 가능 |
| Offline - 위치 buffer | 위치 추적 없으므로 버퍼 불필요 | — | **D** | iOS 무관 |

### 1.6 운행 완료 (ON_TRIP → AWAITING_SETTLEMENT)
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Happy Path (completeCall) | "운행 완료" 버튼 → `completeCall(callId)`. 낙관적 UI(activeCall→null, callForSettlement 설정) 후 `callRef.update(status=AWAITING_SETTLEMENT)` 단일 업데이트 (트랜잭션 아님, 기사 상태 변경 없음) | `DriverViewModel.kt:640-668` | **B** | B 가능: `try await ref.updateData(...)` |
| 콜매니저 통지 | `onCallStatusChanged` CF → 매니저 FCM | `functions/src/index.ts:1810` | **B** | iOS 무관 |
| 손님앱 통지 (RIDE_COMPLETED) | 별도 CF `notifyCustomerOnComplete`이 IN_PROGRESS→COMPLETED 감지 시 발송(AWAITING_SETTLEMENT 단계에서는 아님). 즉 손님 RIDE_COMPLETED는 1.7 정산 제출 후 발송됨 | `functions/src/index.ts:1681-1803` | **B** | iOS 무관 |
| Offline 시 처리 | Firestore SDK 자동 큐잉 | — | **B** | B 가능(동일) |

### 1.7 정산 입력 → 제출 (AWAITING_SETTLEMENT → COMPLETED)
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Happy Path (confirmAndFinalizeTrip) | 정산 다이얼로그에서 결제방식/현금액/포인트 입력 후 확정 → 콜 문서에 fare/paymentMethod/status=COMPLETED/creditAmount/pointsUsed/completedAt/finalFare 쓰기 + 기사 status=WAITING 단일 트랜잭션 | `DriverViewModel.kt:670-808` | **B** | B 가능: Firestore transaction async |
| 결제 방식별 처리 (현금/이체/외상/포인트/현금+포인트) | 현금: cashReceived=fare. 현금+포인트: cashReceived=입력액, creditAmount=max(0, fare-points-cash). 외상/이체: creditAmount=fare. 포인트: 포인트는 CF가 별도 적립 처리(CLAUDE.md 3/24 BUG-D12) | `DriverViewModel.kt:708-716` | **B** | B 가능: 순수 함수(struct SettlementCalc)로 분리 권장 |
| 정산 공식 적용 (officeDeposit, driverShare, finalDeposit, realDeposit) | 콜마다 officeDeposit=fare×ratio/100, driverShare=fare-officeDeposit. `_todaySettlement` 로컬 집계 실시간 업데이트. ratio는 `_depositRatio`(기본 60, Firestore office 문서에서 로드) | `DriverViewModel.kt:742-769` | **B** | B 가능: 순수 함수로 이관 단순 |
| 통합 제출 (isIntegration, PENDING_CONFIRM 재제출) | `submitDailySettlement(isIntegration)` 별도 함수. 이전 dailySettlement.status==PENDING_CONFIRM이면 재제출 → 기존 + 추가분 병합(originalCarryOver/originalTripCount/originalTotalFare/originalRealDeposit 보존) | `DriverViewModel.kt:1348` (함수 시작), CLAUDE.md §정산 | **B** | B 가능: 로직 동일. SwiftData SettlementCache에 중간 저장 |
| 콜매니저 통지 (SETTLEMENT_FINALIZED → SETTLEMENT_CONFIRMED/REJECTED) | 기사 제출 → 매니저 FCM. 매니저 확인 → 기사 FCM `SETTLEMENT_CONFIRMED`. 반려 → `SETTLEMENT_REJECTED` → 기사 재제출 가능. 수신 처리는 `MyFirebaseMessagingService.kt:207-228` | `MyFirebaseMessagingService.kt:189-228` | **B** | B 가능: UNUserNotificationCenter + NotificationCenter post |
| Offline - PendingSync 큐 | `SettlementRepository`가 `PendingSyncEntity`(Room, pending_syncs 테이블)에 append → 온라인 복구 시 `syncCallToFirestore`로 flush. 단 기사앱 통상 플로우에서 오프라인 감지해 큐로 라우팅하는 경로 **명시적 호출처 확인 필요** | `SettlementRepository.kt:81, 112, 275` | **R1** | B 가능: SwiftData `@Model PendingSync` + `@ModelActor SyncWorker` (Topic 3 합의). Kotlin의 호출처 누락 확인 필요는 iOS에서도 동일 점검 필요 |
| WorkManager sync 정책 (15분 주기 + 네트워크 복구 트리거) | `SettlementSyncWorker`: OneTimeSync(KEEP), PeriodicSync(15분, KEEP), OnNetworkAvailable(REPLACE) 3종. 각각 `NetworkType.CONNECTED` 제약 + 3회 지수 백오프 | `worker/SettlementSyncWorker.kt:58-129` | **R1** | B 부분 가능: iOS엔 WorkManager "반드시 실행" 등가 없음. `BGTaskScheduler` (보조) + `NWPathMonitor` 온라인 복귀 즉시 drain + foreground 진입 시 즉시 drain이 1차. **구현 공수: 신규 BGProcessingTask 등록 + Info.plist 선언** |
| 매니저 반려 시 재제출 | `SETTLEMENT_REJECTED` FCM 수신 시 `ACTION_SETTLEMENT_REJECTED` LocalBroadcast + 시스템 알림(navigateTo=settlement). 재제출은 일반 정산 플로우 동일 | `MyFirebaseMessagingService.kt:218-228` | **B** | B 가능: UNNotificationCategory + userInfo["navigateTo"] 딥링크 |

### 1.8 이월금 처리 (CARRYOVER)
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| 이월금 리스너 (carryOverListener) | **유일한 Firestore 실시간 리스너** (`driverRef.addSnapshotListener`). carryOver 서브필드 감지 후 `_carryOver` + `_dailySettlementStatus` StateFlow 업데이트 | `DriverViewModel.kt:1261-1314` | **B** | B 가능: AsyncStream wrapper(`continuation.onTermination`에서 `.remove()`). ScenePhase 백그라운드 시 pause 필요(Topic 2 합의) |
| transferCarryOver (매니저) → CARRYOVER_TRANSFERRED FCM | 매니저 측 정산 이체 확정 시 CF 또는 직접 쓰기로 기사 문서 carryOver.status=TRANSFERRED. 이때 `CARRYOVER_TRANSFERRED` FCM 별도 발송 → 기사앱에서 "미수령금 이체 알림" 시스템 알림 표시(navigateTo=settlement) | `MyFirebaseMessagingService.kt:229-237`, 매니저/CF 측 경로 **확인 필요** | **B** | B 가능: UNNotification + deep link |
| confirmReceiveCarryOver (기사) → SETTLED | 기사가 "수령 확인" 탭 → `confirmReceiveCarryOver(onResult)` → 기사 문서 carryOver.status=SETTLED로 업데이트. 리스너가 StateFlow 즉시 반영 | `DriverViewModel.kt:1314-1347` | **B** | B 가능: async updateData |
| 일일 정산 vs 이월금 통합 표시 | `HistorySettlementScreen`에서 `carryOver` + `todaySettlement` 2개 StateFlow 동시 구독해 통합 카드로 표시 | `ui/home/HistorySettlementScreen.kt:76, 82` | **B** | B 가능: @Observable Store 2개 → SwiftUI에서 단일 뷰 합성 |

### 1.9 WAITING 복귀 → 다음 콜 대기
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| 기사 상태 WAITING 변경 | 각 운행 완료/취소/거절 트랜잭션 내부에서 driver.status=WAITING로 원자적 변경 (별도 호출 없음) | `DriverViewModel.kt:731, 554, 504` | **B** | B 가능: 동일 트랜잭션 내 포함 |
| Presence 유지 (online/background/offline) | `PresenceManager.setStatus(ONLINE)` — 포그라운드 복귀 시 ONLINE. 백그라운드 진입 시 BACKGROUND. 연결 끊김 시 `onDisconnect`가 서버측 OFFLINE 설정 | `service/PresenceManager.kt:94-127` | **B** | B 가능: Firebase Realtime DB iOS SDK가 `onDisconnectSetValue` 동일 지원. ScenePhase 관측으로 foreground/background 전환 추적. 단 iOS는 "앱 종료" 시점에 onDisconnect 발동 시점이 Android보다 지연될 수 있음(기존 CF 스케줄러 보호 구조가 유효) |
| 콜매니저 인지 (DRIVER_STATUS_UPDATE) | `onDriverStatusChange` CF가 driver.status 변경 감지 → 매니저 FCM DRIVER_STATUS_UPDATE. 콜매니저 Room DB는 기존 레코드 상태만 UPDATE(새 기사 INSERT 안 됨, CLAUDE.md 제약) | `functions/src/index.ts:4668` | **B** | iOS 무관 (CF) |

---

## 2. 횡단 기능 (라이프사이클 외부)

### 2.1 로그인·회원가입
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| 로그인 (Firebase Auth + collectionGroup) | `signInWithEmailAndPassword(email, password)` → 성공 시 `checkPendingStatusAndProceed` → pending_drivers 체크(있으면 signOut "승인 대기") → `findDriverDocumentAndSaveInfo`가 `collectionGroup("designated_drivers") where authUid==uid` 조회 → 문서 경로에서 provinceId/cityId/officeId 파싱 + SharedPreferences 저장 + approvalStatus==APPROVED 확인 + 기사 status=ONLINE + FCM 토큰 등록 | `LoginViewModel.kt:70-266` | **B** | B 가능: Firebase Auth iOS SDK `signIn(withEmail:password:)` async. collectionGroup iOS SDK 동일. UserDefaults 저장 |
| 자동 로그인 | `SecurePreferencesManager`(EncryptedSharedPreferences)에 identifier/password 저장. `LoginViewModel.init`에서 autoLogin=true이면 저장된 자격증명으로 자동 `login()` 호출 | `LoginViewModel.kt:54-68` | **B** | B 가능: **Keychain Services**(EncryptedSharedPreferences 등가). kSecClassGenericPassword + kSecAttrAccessibleAfterFirstUnlock |
| 회원가입 + 승인 대기 (pending_drivers) | `SignUpViewModel.signUp()` → `createUserWithEmailAndPassword` → `pending_drivers/{uid}` 문서 생성(선택된 province/city/office 정보 + 이름/전화). 매니저 승인 시 designated_drivers로 이동 + approvalStatus=APPROVED | `SignUpViewModel.kt:164-202` | **B** | B 가능: Firebase Auth `createUser` async + Firestore setData |
| 비밀번호 찾기 | `ForgotPasswordScreen.kt` + LoginViewModel(혹은 별도 컴포넌트)에서 `sendPasswordResetEmail` 호출. 세부 로직 **확인 필요** | `ui/login/ForgotPasswordScreen.kt` | **B** | B 가능: `Auth.auth().sendPasswordReset(withEmail:)` |
| 로그아웃 + cleanup | `LoginViewModel.logout()` → `auth.signOut()`. PresenceManager.onLogout 호출 경로 **확인 필요**(현재 signOut 시 authStateListener가 stopListeners + uiState 초기화, PresenceManager 별도 호출 누락 가능) | `LoginViewModel.kt:282`, `DriverViewModel.kt:177-186` | **B** | B 가능: `try Auth.auth().signOut()` + Store들 stop() + Keychain clear + Realtime DB presence offline 명시 설정 |

### 2.2 푸시 메커니즘
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| FCM 토큰 등록 (APNs in iOS) | `MyFirebaseMessagingService.onNewToken` → 성공: SharedPreferences pending 제거 + driver 문서에 fcmToken 저장. 실패: pending 토큰 SharedPreferences 유지 → 앱 시작 시 `retryPendingFcmToken` 재시도 | `MyFirebaseMessagingService.kt:27-68, 77-108` | **B** | B 가능: `Messaging.messaging().delegate` + `didReceiveRegistrationToken`. APNs → FCM token bridging은 Firebase가 처리. 재시도는 UserDefaults pending 토큰 저장으로 동일 패턴 |
| call_assigned 처리 | 포그라운드: LocalBroadcast `ACTION_SHOW_CALL_DIALOG` → HomeScreen 다이얼로그. 백그라운드: `showNotification` + `setFullScreenIntent(LockScreenActivity)`. 항상 `DriverForegroundService` startForegroundService + Delivery ACK 전송 | `MyFirebaseMessagingService.kt:148-176, 250-333` | **B** | B 가능(1차 Phase 1): UNUserNotificationCenter Time Sensitive alert + willPresent/didReceive delegate 분기. 포그라운드: .task 내부 AsyncStream 이벤트 채널로 다이얼로그. **"FullScreenIntent" 직접 등가 없음** — 잠금화면 풀스크린은 불가. R(Phase 2): VoIP push + CallKit reportNewIncomingCall + Live Activity push-to-start로 강화. **구현 공수: CF 측 APNs VoIP 채널 신규 구축 필요** |
| call_cancelled 처리 | LocalBroadcast `ACTION_CALL_CANCELLED` + 시스템 알림 "콜 취소" 항상 표시. 포그라운드/백그라운드 무관 | `MyFirebaseMessagingService.kt:177-188` | **B** | B 가능: UNNotification + NotificationCenter post. R: CallKit 활성 시 `provider.reportCall(with:endedAt:reason:.remoteEnded)` 추가 |
| SETTLEMENT_FINALIZED | LocalBroadcast `ACTION_SETTLEMENT_FINALIZED`(sessionDate/totalCount/totalFare) + 전용 `settlement_channel`로 알림 | `MyFirebaseMessagingService.kt:189-206, 335-379` | **B** | B 가능: UNNotificationCategory "settlement" |
| SETTLEMENT_CONFIRMED | LocalBroadcast + 시스템 알림(navigateTo=settlement) "정산 확인 완료. 퇴근할 수 있습니다" | `MyFirebaseMessagingService.kt:207-217` | **B** | B 가능: userInfo["navigateTo"] → SceneDelegate/AppRouter 딥링크 |
| SETTLEMENT_REJECTED | LocalBroadcast + 시스템 알림(navigateTo=settlement) "재제출해주세요" | `MyFirebaseMessagingService.kt:218-228` | **B** | B 가능(동일 패턴) |
| CARRYOVER_TRANSFERRED | 시스템 알림만(navigateTo=settlement) "이체가 완료되었습니다". LocalBroadcast 없음 | `MyFirebaseMessagingService.kt:229-237` | **B** | B 가능 |
| Delivery ACK (acknowledgeNotification) | call_assigned 수신 시 data.notificationId가 있으면 Firebase Functions Callable `acknowledgeNotification` 호출. ACK 미수신 집계/대시보드는 없음 | `MyFirebaseMessagingService.kt:150-152, 384-396`, `functions/src/index.ts:242` | **B** | B 가능: Functions iOS SDK `httpsCallable("acknowledgeNotification").call(...)`. 단 VoIP push 수신 시엔 CallKit 리포트 후에 호출해야 함(지연 금지) |
| 포그라운드 vs 백그라운드 분기 | `DriverApplication.isInForeground`(ProcessLifecycleOwner 기반)로 판정. 포그라운드: 시스템 알림 생략하고 앱 내 다이얼로그만. 백그라운드: 시스템 알림 + FullScreenIntent | `DriverApplication.kt:20-35`, `MyFirebaseMessagingService.kt:244-248` | **B** | B 가능: UNUserNotificationCenterDelegate `willPresent` (foreground) vs `didReceive` (background/tap). ScenePhase 병행 |
| 잠금화면 처리 (LockScreenActivity 등가) | `setShowWhenLocked(true) + setTurnScreenOn(true) + requestDismissKeyguard`. FLAG_KEEP_SCREEN_ON. 3초 간격 반복 알림음(MediaPlayer) + 무한 반복 진동 패턴[0,500,200,500]. "확인" 버튼 1개 → MainActivity 점프 | `LockScreenActivity.kt:62-195` | **R1** | **Phase 1: 불가 (Android 특수 처리)**. iOS는 앱이 잠금화면에 직접 풀스크린 Activity를 띄울 수 없음. Time Sensitive는 배너 수준. R(Phase 2): **VoIP+CallKit**이 유일한 "잠금화면 풀스크린 + 반복 ringtone"를 제공(시스템 수신전화 UI). 단 custom layout 불가(출발지/요금 대신 `CXHandle` 문자열만). Live Activity로 정보 보강 |
| 앱 종료 상태 처리 | FCM data-only push가 OS에 의해 서비스를 instantiate → `onMessageReceived` 실행. DriverForegroundService 기동 가능. 단 "로그인 체크 누락 시 무시"(`MyFirebaseMessagingService.kt:128`) | `MyFirebaseMessagingService.kt:125-131` | **R1** | **Phase 1: 제약 있음**. 사용자가 앱을 강제 종료(swipe up kill)한 상태에선 일반 APNs/FCM은 알림 표시만 가능, 앱 코드 실행 불가. Firestore/ACK 불가. R(Phase 2): **VoIP push가 유일 경로**로 앱 강제 기동 + 코드 실행 + CallKit 리포트 가능 |

### 2.3 Presence
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Realtime DB 연결 (.info/connected) | `FirebaseDatabase.getReference(".info/connected")` 구독 → connected=true일 때 onDisconnect 등록 + ONLINE 설정 | `PresenceManager.kt:54-88` | **B** | B 가능: Firebase Database iOS SDK `Database.database().reference(withPath: ".info/connected").observe(.value)` 동일 |
| online/background/offline 전환 | Status 3단계 enum. `setStatus` 호출 시 `{status, lastSeen: SERVER_TIMESTAMP}` 쓰기. `onAppForeground`/`onAppBackground`/`onLogout` API | `PresenceManager.kt:37-127` | **B** | B 가능: ScenePhase 관측으로 foreground/background 분기 + setValue. `ServerValue.timestamp` 동일 |
| onDisconnect → offline | 연결 이벤트 수신 시마다 onDisconnect 재설정 (연결 끊김 시 서버측이 자동 OFFLINE 쓰기) | `PresenceManager.kt:68-73` | **B** | B 가능: `ref.onDisconnectSetValue(...)`. 단 iOS 앱 종료 시점이 Android와 다를 수 있어 CF 스케줄러 보호가 iOS에서 더 중요 |
| CF 스케줄러 (checkAssignedTimeout) 의존 | 매 1분 기사 presence 조회로 상태 보호. ASSIGNED+offline+타임아웃 → WAITING, ACCEPTED/PREPARING+offline → 매니저 FCM, IN_PROGRESS+10분 offline → firstOfflineAt + 매니저 FCM. 이 외 online/background는 동일 취급 | `functions/src/index.ts:3457` | **B** | iOS 무관 (CF). iOS presence 정확도 낮을 수 있으니 이 보호가 더 중요 |
| 네트워크 끊김 배너 (isConnected UI) | `PresenceManager._isConnected` StateFlow → HomeScreen에 빨간 배너 | `PresenceManager.kt:34-35` + HomeScreen | **R1** | B 가능: `NWPathMonitor` 또는 `.info/connected` 관측 → @Observable 노출 |

### 2.4 백그라운드 유지
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Foreground Service (영구 활성) | `DriverForegroundService` + 저우선 상태 알림 채널(IMPORTANCE_LOW, "기사앱 실행 중"). `startForeground(NOTIFICATION_ID, notification)`로 영구 알림 유지. FCM 수신 시 `newCallAssignedIntent`로 intent routing | `service/DriverForegroundService.kt:36-190` | **D** | **불가: iOS엔 "영구 Foreground Service" 등가 없음**. Background Mode(voip/audio/location)은 실제 기능 사용 중일 때만 허용. R 최대치: VoIP push로 필요 시점에만 wakeup + Live Activity로 잠금화면 상주 UI. **설계 변경 필수**: 영구 실행 전제 제거하고 이벤트 기반(push wakeup)으로 재설계 |
| BootReceiver (재부팅 시 재시작) | `BOOT_COMPLETED` 수신 시 `DriverForegroundService` 시작. `BootReceiver.kt:17-` | `receiver/BootReceiver.kt:11-` | **D** | **불가: iOS엔 BOOT_COMPLETED 등가 없음**. 사용자가 앱을 1회 실행해야 푸시 수신 상태로 전환. 재부팅 후 자동 재시작 구조 불가. R 최대치: VoIP push 수신 시점에 앱 강제 기동(재부팅 후 첫 VoIP push는 전달됨) |
| WAKE_LOCK | `AndroidManifest.xml:23` 권한 선언. 실제 직접 사용 코드는 `LockScreenActivity.FLAG_KEEP_SCREEN_ON` 간접 사용(화면 점등 유지) | `AndroidManifest.xml:23`, `LockScreenActivity.kt:114` | **R2** | iOS: CallKit이 자동 화면 점등 처리. `UIApplication.shared.isIdleTimerDisabled = true`로 앱 내부 화면 유지 가능 |
| Background Modes (iOS 등가) | iOS 포팅 대상 — 현재 Kotlin 쪽은 해당 없음 | — | **B** | Info.plist `UIBackgroundModes` 조합: **remote-notification**(FCM), **voip**(R 시), **audio**(선택, CallKit ringtone). location은 주기 추적 없으면 불필요. fetch는 BGTaskScheduler 대체 |

### 2.5 오프라인·동기화
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| Firestore 자동 offline persistence | Firebase SDK 기본 활성(특별 설정 없음 추정). write는 로컬 큐에 쌓여 복구 시 flush. 단 runTransaction은 읽기가 필요해 오프라인 실패 가능 **확인 필요** | — | **B** | B 가능: Firebase Firestore iOS SDK도 기본 persistence ON (`FirestoreSettings.isPersistenceEnabled`). 트랜잭션 오프라인 동작은 iOS도 동일 확인 필요 |
| 정산 PendingSync 큐 | Room `pending_syncs` 테이블 + `PendingSyncEntity`(JSON blob + status/retryCount). `SettlementRepository.addPendingSync` 경로 및 호출처 **확인 필요** — 현재 조사로는 syncCallToFirestore 3곳만 발견 | `data/local/PendingSyncEntity.kt` + `data/repository/SettlementRepository.kt` | **R1** | B 가능: SwiftData `@Model PendingSync` + `@ModelActor SyncWorker` (Topic 3 합의). JSON blob은 Codable payload + `Data` 저장 |
| WorkManager 주기 sync (15분) | `enqueuePeriodicSync` — NetworkType.CONNECTED + 지수 백오프. KEEP 정책으로 중복 방지 | `worker/SettlementSyncWorker.kt:85-109` | **R2** | **B 부분 가능**: `BGTaskScheduler` + `BGProcessingTaskRequest(requiresNetworkConnectivity: true)`. 단 WorkManager 수준 "반드시 실행" 보장 없음 — 시스템이 시점 판단. 1차 보호는 **foreground 진입 시 즉시 drain**. 지수 백오프는 PendingSync `attempt` 필드 + cooldown 로직으로 직접 구현 |
| 네트워크 복구 시 즉시 sync | `enqueueOnNetworkAvailable` — REPLACE 정책. 호출처(`MainActivity`/`DriverApplication`)에서 NetworkCallback 연동 **확인 필요** | `worker/SettlementSyncWorker.kt:114-129` | **R1** | B 가능: `NWPathMonitor` pathUpdateHandler에서 `.satisfied` 전환 감지 → SyncWorker.drain 호출 |
| 7일 정산 캐시 | `settlement_cache` 테이블 — sessionDate PK, version Long, metadataJson/totalsJson/callsJson blob. `deleteOldCache(threshold)` 쿼리로 7일 초과 캐시 제거. version 필드로 Firestore 문서 버전 미러링 | `data/local/SettlementCacheEntity.kt`, `data/local/SettlementDao.kt:116-117` | **B** | B 가능: SwiftData `@Model SettlementCache { @Attribute(.unique) var sessionDate: String; var version: Int64; var metadata: Data; ... }`. FetchDescriptor predicate로 7일 초과 삭제 |
| 충돌 해결 (server-wins on read + version compare) | `checkAndSync`: remoteVersion > localVersion → 원격 전체 덮어쓰기. 같거나 작으면 로컬 유지. 업로드 시 `version = session.metadata.version + 1` optimistic write | `data/repository/SettlementRepository.kt:145, 194-228` | **B** | B 가능: 순수 함수 로직. 언어 독립 |

### 2.6 보안·자격증명
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| EncryptedSharedPreferences (iOS Keychain 등가) | `SecurePreferencesManager`로 감싼 EncryptedSharedPreferences. 자동로그인 identifier/password 저장 | `util/SecurePreferencesManager.kt` + `LoginViewModel.kt:42-68` | **B** | B 가능: Keychain Services (`SecItemAdd/Copy/Update/Delete`). kSecAttrAccessible = `.afterFirstUnlockThisDeviceOnly` 권장 |
| 자동로그인 크리덴셜 | identifier(이메일 또는 전화) + password 저장. toggleAutoLogin(false) 시 clear | `LoginViewModel.kt:270-274` | **B** | B 가능: Keychain 항목 add/delete |
| officeId·provinceId·cityId 캐시 (UserDefaults 등가) | SharedPreferences(`Constants.PREFS_NAME`)에 PREF_KEY_PROVINCE_ID/CITY_ID/OFFICE_ID 저장. FCM 서비스·LockScreen·DriverViewModel 전부 이 값 참조 | `data/Constants.kt` + 각 위치 |  **B** | B 가능: `UserDefaults.standard`. App Group 필요 시(Live Activity/App Clip 공유) App Group UserDefaults |

### 2.7 부가 기능
| 항목 | 설명 | 코드 위치 | 분류 | iOS 평가 |
|------|------|----------|------|---------|
| 운행 내역 조회 (HistorySettlementScreen) | uiState + carryOver + tripHistoryList + todaySettlement 통합 표시. 운행내역 카드 + 일일 정산 합계 + 이월금 카드. 정산 액션 버튼(업무마감/수령확인 등) | `ui/home/HistorySettlementScreen.kt` | **B** | B 가능: SwiftUI View + 복수 @Observable Store 참조 |
| 콜 상세 화면 (CallDetailsScreen) | `loadCallDetails(callId)` → `_callDetailsState` StateFlow → 상세 표시. 주로 완료 콜 상세 조회용 | `DriverViewModel.kt:329`, `ui/details/CallDetailsScreen.kt` | **B** | B 가능: NavigationStack + async `.task { try await repo.load(callId) }` |
| 추천 QR (ReferralQRScreen) | 기사 개인 추천 QR 표시 (고객 앱 유치용). 세부 로직 **확인 필요** | `ui/screens/home/ReferralQRScreen.kt` | **R2** | B 가능: CoreImage `CIFilter.qrCodeGenerator()` |
| 설정 화면 | 자동로그인 토글, 로그아웃, 알림 설정 등. 비밀번호 변경 포함 가능 | `ui/home/SettingsScreen.kt` | **B** | B 가능: SwiftUI Form |
| 주소 검색 (AddressSearchHelper) | 외부 주소 검색 API 호출(CoroutineScope detached 패턴 — anti-pattern). TripPreparation에서 사용 | `util/AddressSearchHelper.kt:32` | **B** | B 가능: URLSession async. **Kakao/네이버 지도 주소검색 API iOS SDK 연동 필요** — 현재 Android 기준 어느 API인지 확인 필요. detached 패턴은 iOS에선 `Task {}` (inherit context)로 교체 권장(Topic 4 합의) |

---

## 3. 콜매니저 ↔ 기사앱 연동 포인트 (양방향)

### 3.1 콜매니저 → 기사앱 (Manager가 기사앱을 트리거)
| 이벤트 | 메커니즘 | Firestore 변경 | FCM 발송 | 기사앱 처리 | 분류 | iOS 평가 |
|---|---|---|---|---|---|---|
| 배차 (driver 지정) | Manager DashboardViewModel 트랜잭션 | call: status=WAITING→ASSIGNED + assignedDriverId, driver: WAITING→ASSIGNED | CF `oncallassigned`: `call_assigned` data-only high priority (notificationId 포함) | `MyFirebaseMessagingService.onMessageReceived` call_assigned 분기 → FullScreenIntent/LocalBroadcast + Delivery ACK | **B** | B 부분 가능(Phase 1): Time Sensitive notification만. FullScreenIntent 등가 불가. R(Phase 2): VoIP+CallKit + Live Activity |
| 콜 취소 (관리자) | Manager 호출 `notifyDriverCancellation` Callable + call.status=CANCELED 쓰기 | call: status→CANCELED | CF `notifyDriverCancellation`: 기사에게 `call_cancelled` | `handleCallCancelled(callId)` → UI 정리 + errorMessage | **B** | B 가능: UNNotification + NotificationCenter post. R: CallKit 활성 콜 강제 종료 |
| 정산 확인 | Manager confirmDailySettlement — dailySettlements.status=CONFIRMED | dailySettlement status 변경 | CF가 기사에게 `SETTLEMENT_CONFIRMED` | LocalBroadcast + 알림 "퇴근할 수 있습니다" | **B** | B 가능 |
| 정산 반려 | Manager rejectDailySettlement — dailySettlement.status=REJECTED | 동일 | `SETTLEMENT_REJECTED` FCM | LocalBroadcast + 알림 "재제출해주세요" | **B** | B 가능 |
| 이월금 이체 | Manager transferCarryOver — driver.carryOver.status=TRANSFERRED + 이체정보 | driver 문서 carryOver 서브 | `CARRYOVER_TRANSFERRED` FCM | 시스템 알림 + 이월금 리스너가 UI 자동 갱신 | **B** | B 가능: AsyncStream 리스너가 자동 반영 |

### 3.2 기사앱 → 콜매니저 (기사 행동이 매니저 화면에 반영)
| 이벤트 | 메커니즘 | Firestore 변경 | CF 트리거 | 매니저 화면 갱신 | 분류 | iOS 평가 |
|---|---|---|---|---|---|---|
| 수락 | `acceptCall` 단일 트랜잭션 | call: ASSIGNED→ACCEPTED, driver: ASSIGNED→PREPARING | `onCallStatusChanged` (ACCEPTED) + `onDriverStatusChange` (PREPARING) | 매니저 FCM `CALL_STATUS_UPDATE` + `DRIVER_STATUS_UPDATE` → Room DB UPDATE | **B** | B 가능: runTransaction iOS SDK. R(VoIP): CXAnswerCallAction → 동일 경로 호출 |
| 거절 | `rejectCall` 단일 트랜잭션 | call: ASSIGNED→WAITING + rejectedByDriver, driver: ASSIGNED→WAITING | `onCallStatusChanged` | 매니저 FCM → Room UPDATE, 재배차 가능 상태 표시 | **B** | B 가능. R: CXEndCallAction → 동일 경로 |
| 운행 시작 | `startDriving` 단일 트랜잭션 | call: ACCEPTED→IN_PROGRESS + departure/destination/fare, driver: PREPARING→ON_TRIP | `onCallStatusChanged` | FCM → 매니저 화면에 운행 정보 반영 | **B** | B 가능 |
| 운행 완료 | `completeCall` 단일 update(트랜잭션 아님) | call: IN_PROGRESS→AWAITING_SETTLEMENT | `onCallStatusChanged` | 정산 대기 표시 | **B** | B 가능 |
| 정산 제출 | `confirmAndFinalizeTrip` 단일 트랜잭션 + CF `onCallCompletedUpdateSettlement` | call: AWAITING_SETTLEMENT→COMPLETED + fare/paymentMethod/creditAmount, driver: ON_TRIP→WAITING | `onCallStatusChanged` + `onCallCompletedUpdateSettlement`(settlementSessions 자동 생성) | 매니저 FCM + settlementSessions 반영 | **B** | B 가능 |
| 이월금 수령 확인 | `confirmReceiveCarryOver` → driver 문서 carryOver.status=SETTLED | driver carryOver 서브 | `onDriverStatusChange` 또는 별도 리스너 (**확인 필요**) | 매니저 화면 정산 완료 표시 | **B** | B 가능 |
| 상태 변경 (WAITING/ONLINE/OFFLINE) | `updateDriverStatus` 단일 update. PresenceManager는 Realtime DB별도 쓰기 | driver.status 변경 (Firestore) | `onDriverStatusChange` | 매니저 FCM `DRIVER_STATUS_UPDATE` → Room UPDATE | **B** | B 가능. iOS presence 정확도는 CF 스케줄러 보호에 의존 |

---

## 4. 손님앱 ↔ 기사앱 연동 포인트 (CF 경유)

### 4.1 기사앱 → 손님앱 (기사 행동이 손님 화면에 반영)
| 이벤트 | 기사 트리거 | CF | 손님앱 수신 | 분류 | iOS 평가 |
|---|---|---|---|---|---|
| 기사 배정 | (매니저 배차 트리거) 콜 문서 assignedDriverId 설정 | `oncallassigned` — 손님 FCM 토큰 customerInfo/{phone}에서 조회 | `type: "DRIVER_ASSIGNED"` FCM → 팝업 + 진동 | **B** | 기사앱 iOS 측 직접 영향 없음. 손님앱 iOS 포팅 시 별도 평가 |
| 기사 출발 (ACCEPTED 또는 PREPARING) | `acceptCall` | `onCallStatusChanged` (ACCEPTED→IN_PROGRESS 사이 반영) | 3/24 CUST-03 수정으로 FCM 추가됨. type 값 **확인 필요** | **B** | 기사앱 iOS 측 변경 없음(기존 트랜잭션만 호출) |
| 기사 도착 | (별도 UI 이벤트 없음, IN_PROGRESS=기사 출발 후 도착까지 포함) | 동일 | 동일 | **B** | iOS 무관 |
| 운행 완료 | `confirmAndFinalizeTrip` (status→COMPLETED) | `notifyCustomerOnComplete` (IN_PROGRESS→COMPLETED 감지) | `RIDE_COMPLETED` FCM + 포인트 적립 알림(POINTS_EARNED) | **B** | 기사앱 iOS 측 변경 없음 |
| 콜 취소 (기사 측) | `cancelTrip` (status→CANCELLED_BY_DRIVER) | `onCallCancelledByDriver` | `CALL_CANCELLED` FCM + 제목/본문 + 매니저 FCM 동시 | **B** | B 가능: cancelTrip 동일 포팅 |

### 4.2 손님앱 → 기사앱 (손님 행동이 기사 화면에 반영)
| 이벤트 | 손님 트리거 | CF | 기사앱 수신 | 분류 | iOS 평가 |
|---|---|---|---|---|---|
| 콜 취소 (손님 측, WAITING/ASSIGNED/ACCEPTED/PREPARING 단계) | 손님앱 CallService.cancelCall(status→CANCELLED_BY_CUSTOMER). 3/24 확장: ACCEPTED/PREPARING까지 허용 | `onCallStatusChanged`: 기사에게 `call_cancelled` | `MyFirebaseMessagingService` call_cancelled 분기 → `handleCallCancelled(callId)` + 알림 | **B** | B 가능: UNNotification + NotificationCenter. R: CallKit 활성 콜 강제 종료 |
| 콜 요청 (신규 콜 — 매니저 경유) | 손님앱 신규 콜 생성 (calls/{callId} status=WAITING) | 매니저 화면 실시간 리스너 감지, CF 별도 없음(매니저 수동 배차) | 기사는 매니저가 배차(4.1)할 때까지 대기 | **B** | 기사앱 iOS 측 영향 없음 |

---

## 5. 분류 결과 (Phase A, kotlin-expert 2026-04-15)

### 통계
- **B**: 63건
- **R1**: 5건
- **R2**: 3건
- **D**: 4건
- **B?** (사용자 확인 필요): 1건

### Phase 1 MVP Scope — B로 분류된 항목 전수

**§1 라이프사이클 (1순환)**:
- 1.1 배차 수신: Happy Path · 관리자 취소 · 3분 타임아웃 · Offline 기사폰(앱 진입 1회 조회) · Offline CF
- 1.2 수락: Happy Path · Firestore 트랜잭션(콜+기사) · 콜매니저 FCM · 손님앱 DRIVER_ASSIGNED · 트랜잭션 실패 롤백 · (오프라인 트랜잭션 큐잉은 B?)
- 1.3 거절: 앱 내 거절(runTransaction) · 콜매니저 통지 · Offline(공통 에러 핸들러)
- 1.4 운행 시작: Happy Path · 트랜잭션 · 콜매니저/손님앱 통지 · cancelTrip · CANCELLED_BY_CUSTOMER 수신 · Offline
- 1.5 운행 중: 위치 일회 geocoding · 상태 UI · 양측 취소 처리
- 1.6 운행 완료: completeCall update · 콜매니저/손님앱 통지 · Offline
- 1.7 정산 제출: confirmAndFinalizeTrip · 결제방식 5종 · 정산 공식 · 통합 제출(isIntegration) · 매니저 통지 · 반려 재제출
- 1.8 이월금: carryOverListener · transferCarryOver FCM · confirmReceiveCarryOver · 통합 표시
- 1.9 WAITING 복귀: 상태 변경 · Presence 유지 · CF DRIVER_STATUS_UPDATE

**§2 횡단 기능**:
- 2.1 로그인·자동로그인(Keychain)·회원가입·비밀번호 찾기·로그아웃
- 2.2 FCM 6종 수신 + 토큰 등록 + Delivery ACK + 포그라운드/백그라운드 분기 (단 "잠금화면 풀스크린"·"앱 종료 상태" 2건은 R1)
- 2.3 Presence 5종 전부 (단 "네트워크 끊김 배너 UI"는 R1)
- 2.4 Background Modes 설정 (Info.plist remote-notification)
- 2.5 Firestore offline persistence · 7일 정산 캐시 · 충돌 해결 (단 PendingSync 큐·네트워크 복구 즉시 sync는 R1)
- 2.6 Keychain 자동로그인 · UserDefaults 사무실 캐시
- 2.7 HistorySettlement · CallDetails · Settings · 주소검색 (단 ReferralQR은 R2)

**§3 콜매니저 ↔ 기사앱**: 모든 양방향 12이벤트 B

**§4 손님앱 ↔ 기사앱**: 모든 7이벤트 B (Phase 1에서 기사앱이 호출하는 트랜잭션·FCM 수신 로직 모두 포함, 손님앱 iOS 포팅은 별도)

### 1차 보강 (R1) — Phase 1 운영 후 우선 도입
1. **잠금화면 풀스크린 처리 (LockScreen 등가)** — VoIP+CallKit + Live Activity. Phase 1은 Time Sensitive 배너만
2. **앱 종료 상태 콜 깨움** — VoIP push만이 유일 경로. Phase 1은 사용자가 앱 최소 1회 실행 전제
3. **정산 PendingSync 큐 (SwiftData)** — Firestore 자동 큐가 불충분한 장기 오프라인 대응
4. **WorkManager OnNetworkAvailable 등가 (NWPathMonitor 기반 즉시 drain)** — 온라인 복귀 시 PendingSync flush
5. **네트워크 끊김 배너 UI** — PresenceManager.isConnected 등가. 기사 UX 직접 영향이나 사이클 필수는 아님

### 2차 보강 (R2) — 데이터 기반 Phase 2 결정
1. **WorkManager 15분 주기 sync (BGTaskScheduler)** — iOS는 시스템 시점 판단이라 보장 약함. 1차는 foreground drain로 대체
2. **WAKE_LOCK 화면 강제 점등** — CallKit이 대체하거나 `isIdleTimerDisabled`로 대체 가능. 독립 항목 아님
3. **추천 QR (ReferralQRScreen)** — 콜 사이클과 무관. 고객 유치 기능이라 초기 출시 후 추가 가능

### 미적용 검토 (D) — iOS에서 구조적 불가 또는 의미 없음
1. **LockScreenActivity.rejectCallDirectly() dead code** — Android에서도 미사용. 이관 대상 아님
2. **Foreground Service 영구 활성** — iOS 불가. 설계 자체를 "이벤트 기반 wakeup"으로 전환
3. **BootReceiver 자동 재시작** — iOS 불가. 사용자가 앱 1회 실행 필요
4. **Offline 위치 buffer** — 현 Kotlin 앱도 주기 위치추적 미사용이라 버퍼 불필요

### 확인 필요 (B?)
1. **§1.2 Offline 트랜잭션 큐잉**: Firestore runTransaction이 오프라인에서 즉시 실패하는지 Android/iOS 모두 재확인 필요. "즉시 실패 → 사용자에게 에러 표시"가 MVP 범위인지 사용자 판단 필요. PendingSync 큐(R1)로 덮을지 결정도 연관

---

## 부록: Phase 0 조사 중 발견 사항

### 코드와 문서 불일치 / 확인 필요 항목
1. **`LockScreenActivity.rejectCallDirectly()` dead code**: 거절 로직이 정의되어 있으나 UI에서 호출되지 않음. CLAUDE.md 3/18 업데이트로 거절 버튼 제거되어 "확인"만 존재. 코드 정리 필요.
2. **FCM 토큰 저장 경로(Flutter)**: `memory/plan_flutter_driver_app.md:78-80` — Flutter fcm_service.dart가 `regions/{regionId}/offices/{officeId}/designated_drivers/{driverId}`로 저장 중. Kotlin은 `provinces/.../cities/.../offices/.../designated_drivers`로 저장. iOS 포팅 시 **Kotlin 경로로 통일**.
3. **CUST-03 type 값 미문서화**: ACCEPTED/IN_PROGRESS 단계의 손님 FCM type 이름이 `CALL_STATUS_UPDATE` / `DRIVER_ARRIVING` 중 어느 쪽인지 CF 코드에서 확인 필요.
4. **트랜잭션 offline 동작**: Firestore runTransaction은 읽기가 필수라 오프라인에서 즉시 실패 가능. 이에 대한 재시도·에러 메시지 정책이 `performFirestoreUpdate` 공통 핸들러 수준에만 있고 각 호출 사이트에서 오프라인 회복성이 일관되지 않음.
5. **PendingSync 큐 실제 라우팅 경로**: `SettlementRepository.addPendingSync` 호출처를 찾기 어려움. 정산 제출의 오프라인 경로가 실제 어떤 조건에서 큐로 빠지는지(자동 감지? 예외 캐치?) 명시적 호출 구조 불명.
6. **로그아웃 시 PresenceManager.onLogout 호출 누락 가능**: `LoginViewModel.logout`이 `auth.signOut()`만 호출. `DriverViewModel.authStateListener`가 stopListeners는 하지만 PresenceManager 처리는 확인 필요.

### 기사앱에만 있고 iOS에서 특별 설계 필요한 것 (Kotlin 의존)
- Foreground Service (영구 알림으로 프로세스 보호) — iOS 대응 없음, BGTaskScheduler로는 등가 불가
- FullScreenIntent (잠금 우회 풀스크린 Activity) — iOS 대응 없음 (Topic 5 논의 중)
- BootReceiver (재부팅 후 자동 서비스 시작) — iOS는 앱 자동 재기동 불가
- LocalBroadcastManager — iOS 쪽은 NotificationCenter/Combine/Observable로 대체
- EncryptedSharedPreferences — iOS Keychain으로 직접 매핑 가능
- WAKE_LOCK + KEEP_SCREEN_ON — iOS `UIApplication.isIdleTimerDisabled`로 일부 대체, 잠금 우회는 불가
- Geocoder (주소 → 좌표) — iOS CLGeocoder 매핑 가능

### 기사앱에 없지만 iOS에서 추가 고려 필요
- Push 권한 Provisional vs Granted 분리 (Android는 알림 권한 1회)
- APNs 인증서·VoIP 인증서 관리 (Firebase Console 등록)
- App Lifecycle에서 `scene(_:willConnectTo:)` 복잡성 (Android ProcessLifecycleOwner보다 세분화)
- Background Modes 선택 — Remote Notifications 필수, VoIP는 Topic 5 합의 대기

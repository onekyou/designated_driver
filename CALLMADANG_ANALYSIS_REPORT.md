# 콜마당(CallMadang) 종합 분석 보고서

> **분석일**: 2026-02-24
> **분석 방법**: Agent Teams 5명 병렬 분석 (Call Detector / Call Manager / Driver App / Customer App / Firebase)
> **총 발견 이슈**: Critical 14건, High 17건, Medium 17건, Low 9건

---

## 목차

1. [크로스 모듈 연동 문제](#1-크로스-모듈-연동-문제)
2. [Call Detector 분석](#2-call-detector-분석)
3. [Call Manager 분석](#3-call-manager-분석)
4. [Driver App 분석](#4-driver-app-분석)
5. [Customer App 분석](#5-customer-app-분석)
6. [Firebase + Cloud Functions 분석](#6-firebase--cloud-functions-분석)
7. [전체 이슈 심각도별 요약](#7-전체-이슈-심각도별-요약)
8. [우선 수정 권장 순서](#8-우선-수정-권장-순서)

---

## 1. 크로스 모듈 연동 문제

개별 앱 분석에서 발견된 이슈들 중 **여러 모듈이 교차하면서 발생하는 문제**입니다.

### CROSS-01. [Critical] Detector vs Manager 배차 시 기사 상태 불일치

- **Detector**: 배차 시 기사 상태를 `"ON_TRIP"`으로 설정
- **Manager**: 배차 시 기사 상태를 `"ASSIGNED"`로 설정
- **Driver App**: `"ASSIGNED"` 상태를 기대하고 콜 수락/거절 UI를 표시
- **결과**: Detector에서 배차하면 기사가 수락 과정 없이 바로 ON_TRIP이 되어, 기사 앱에서 콜 정보를 제대로 인지하지 못할 수 있음

### CROSS-02. [Critical] 4개 앱 간 CallStatus 상태 체계 불일치

- **Detector**: `WAITING, PENDING, MATCHED, DISPATCHED, COMPLETED, CANCELLED`
- **Manager**: `WAITING, SHARED_WAITING, CLAIMED, PENDING, ASSIGNED, ACCEPTED, PICKUP_COMPLETE, IN_PROGRESS, AWAITING_SETTLEMENT, COMPLETED, SHARED_OUT, CANCELED, CANCELLED, HOLD, UNKNOWN`
- **Driver App**: Manager와 유사하지만 일부 차이
- **Customer App**: FCM 메시지 type으로만 상태 판단
- **결과**: 같은 콜이 앱마다 다른 상태로 인식될 수 있고, 향후 상태 기반 로직 추가 시 버그 발생 필연적

### CROSS-03. [Critical] Customer App만 FCM 100% 의존 → 상태 일관성 파괴

- **Manager/Detector**: Firestore 직접 조회 + Room DB → 정확한 상태
- **Driver App**: 낙관적 업데이트 + 1회 조회 → 부분적
- **Customer App**: FCM만 의존 (실시간 리스너 비활성화) → FCM 누락 시 상태 멈춤
- **결과**: 같은 콜에 대해 4개 앱이 서로 다른 상태를 보여줄 수 있음. 특히 농촌 지역 네트워크 불안정 시 Customer App이 가장 취약

### CROSS-04. [Critical] 보안 규칙 개방으로 전체 앱 데이터 격리 파괴

- **RTDB**: `read: true, write: true` → Presence 데이터 완전 노출
- **Firestore calls**: `allow create: if true` → 인증 없이 콜 생성
- **Firestore calls read**: `isAuthenticated()` → 모든 사무실 콜 열람
- **customerPoints**: `allow write: if isAuthenticated()` → 다른 고객 포인트 조작
- **결과**: 4개 앱 모두에서 다른 사무실 데이터 접근 가능. 10개 사무실 데이터 격리가 사실상 무의미

### CROSS-05. [Critical] 내장 Detector vs 독립 Detector 코드 동기화 실패

- **독립 Detector**: `wasRinging` 발신전화 방어 적용, ExcludeNumber 체크, SMS 비활성화
- **내장 Detector (Manager 내)**: 위 3가지 모두 미적용
- **결과**: 같은 "Detector 기능"이 앱에 따라 다르게 동작. Manager 폰에서 발신 전화가 콜로 오등록, 개인 전화가 고객 콜로 등록

### CROSS-06. [High] 비원자적 Firestore 업데이트 (전 앱 공통)

- **Detector**: 콜 업데이트 + 기사 상태 변경 = 별도 요청
- **Manager**: cancelCall, completeCall = 별도 요청
- **Driver App**: cancelTrip, startDriving, completeCall = 별도 요청
- **결과**: 부분 업데이트 시 콜 상태와 기사 상태 불일치. 네트워크 불안정 환경에서 빈번히 발생 예상

### CROSS-07. [High] rejectCall() 비어있음 + Manager 취소 시 기사 상태 미복구 → 재배차 완전 불가

- **Driver App**: `rejectCall()` 본문이 비어있어 기사가 콜 거절 불가
- **Manager**: `cancelCall()` 시 기사 상태를 복구하지 않음
- **HOLD → 재배차** 전용 워크플로우 없음
- **결과**: 한 번 배차된 콜은 기사가 거절할 수 없고, 관리자가 취소해도 기사가 "배차됨" 상태로 영구 고정. 재배차 자체가 시스템적으로 불가능

---

## 2. Call Detector 분석

### [Critical] CD-01. 마감 시 RINGING + IDLE 이중 콜 생성

- **파일**: `CallDetectorService.kt:187-200` (RINGING) + `CallDetectorService.kt:117-162` (IDLE)
- **문제**: 마감(CLOSED) 상태에서 전화가 오면:
  1. RINGING 상태에서 `checkOfficeStatusForQuickResponse()` → 2초 후 `createSharedCallFromRinging()`으로 shared_calls에 콜 생성
  2. 통화 종료 시 IDLE 상태에서 `checkOfficeStatusAndSaveCall()` → `createSharedCall()`로 shared_calls에 또 콜 생성
- **영향**: 같은 전화에 대해 shared_calls에 2개의 문서가 생성. 다른 사무실에서 동일 고객 콜을 2번 보게 됨.

### [Critical] CD-02. Detector와 Manager 간 CallStatus enum 불일치

- **파일**: `call_detector/.../data/CallStatus.kt` vs `call_manager/.../data/CallStatus.kt`
- **문제**: Detector에는 `ASSIGNED` 상태가 enum에 없지만 하드코딩된 문자열 `"ASSIGNED"`를 직접 사용. 두 앱의 상태 체계가 완전히 다름.
- **영향**: 상태 전이 혼란, 코드 가독성 저하, 향후 버그 발생 위험.

### [Critical] CD-03. Detector 배차 시 기사 상태를 "ON_TRIP"으로 설정

- **파일**: `DispatchActivity.kt:172`
- **문제**: Manager는 `"ASSIGNED"`로 설정하는데 Detector는 `"ON_TRIP"`으로 설정.
- **영향**: Driver App이 ASSIGNED를 기대하는 로직이 있으면 Detector 배차 콜은 기사 수락 과정을 건너뜀.

### [High] CD-04. SharedPreferences 5개 혼용

- **문제**: `"detector_config"`, `"CallDetectorPrefs"`, `"call_detector_prefs"`, `"call_detector_auth"`, `"call_detector_login_prefs"` 5개가 혼용됨.
- **영향**: Settings 화면에서 사무실 변경해도 CallDetectorService가 이전 설정 사용 가능.

### [High] CD-05. 배차 실패 시 사용자 피드백 없음

- **파일**: `DispatchActivity.kt:160-167`
- **문제**: Firestore 업데이트 실패 시 로그만 남기고 사용자에게 피드백 없음. `finish()`가 즉시 호출되어 결과를 기다리지 않음.
- **영향**: 네트워크 불안정 시 배차가 실패해도 인지 불가.

### [High] CD-06. 배차 시 Firestore 트랜잭션 미사용

- **파일**: `DispatchActivity.kt:141-198`
- **문제**: 콜 업데이트 + 기사 상태 변경이 트랜잭션 아닌 별도 요청. 두 Detector에서 동시에 같은 기사에게 배차 가능.
- **영향**: 동일 기사에 중복 배차. Detector + Manager 혼용 시 발생 확률 높음.

### [High] CD-07. startDispatchDialog에 callId 누락

- **파일**: `DispatchActivity.kt:333-356`
- **문제**: 정적 메서드에 callId 파라미터가 없어 호출 시 항상 새 문서 생성 경로로 진입.

### [High] CD-08. 비밀번호 SharedPreferences 평문 저장

- **파일**: `LoginViewModel.kt:130-133`
- **문제**: 자동 로그인을 위해 이메일/비밀번호가 평문으로 저장.
- **영향**: 루팅된 기기나 백업에서 관리자 계정 탈취 가능.

### [Medium] CD-09. Android 9 (API 28) 전화번호 확인 불가

- **파일**: `CallReceiver.kt:55-57`
- **문제**: Android 9에서 전화번호를 가져올 수 없어 앱이 전혀 작동하지 않음. 사용자에게 안내 없음.

### [Medium] CD-10. 마감→운영 전환 시 중복 콜 (다른 컬렉션에)

- **문제**: RINGING에서 마감 확인 후 shared_call 생성, IDLE까지 사무실이 OPEN으로 바뀌면 일반 call도 생성.

### [Medium] CD-11. 콜 생성 실패 시 임시 ID로 팝업

- **파일**: `CallDetectorService.kt:786-796`
- **문제**: Firestore 저장 실패 시 `temp_` ID로 팝업. 이 상태에서 배차하면 완전히 실패.

### [Medium] CD-12. shared_calls 루트 레벨 컬렉션 → 격리 어려움

- **문제**: shared_calls가 사무실 하위가 아닌 루트 레벨이라 Security Rules에서 사무실별 격리가 복잡.

### [Medium] CD-13. Sentry DSN 하드코딩

- **파일**: `CallDetectorApplication.kt:93`

### [Low] CD-14. BootCompletedReceiver SharedPreferences 불일치

- **문제**: `"call_detector_prefs"`에서 `device_name` vs `"detector_config"`에서 `deviceName`.

### [Low] CD-15. System.exit(0) 사용

- **파일**: `MainActivity.kt:287`

### 백그라운드 종료 시 전화 감지 누락 가능성

**현재 방어 메커니즘**: Foreground Service (START_STICKY), BootCompletedReceiver, 배터리 최적화 예외

**여전히 존재하는 위험**:
- Samsung 등 제조사 자체 배터리 최적화 우회 불가 (특히 SM-A325N Galaxy A32)
- CallScreeningService ROLE이 다른 앱에 의해 변경될 수 있음
- Doze 모드에서 BroadcastReceiver 지연 가능

---

## 3. Call Manager 분석

### [Critical] CM-01. 내장 Detector에 wasRinging 발신전화 방어 로직 누락

- **파일**: `call_manager/.../service/CallDetectorService.kt:158-166`
- **문제**: 독립 Detector에서 수정된 발신전화 방어 버그가 Manager 내장 Detector에 미반영.
- **영향**: Manager 폰에서 발신 전화가 "수신" 콜로 잘못 등록.

### [Critical] CM-09. cancelCall()이 Firestore만 업데이트, 로컬 DB 미업데이트

- **파일**: `DashboardViewModel.kt:792-811`
- **문제**: Local-First 아키텍처에서 UI는 Room DB를 구독하는데, cancelCall은 Firestore만 변경. FCM이 와야 UI 반영.
- **영향**: 네트워크 불안정 시 취소해도 UI에 즉시 반영 안 됨.

### [High] CM-02. 내장 Detector에 ExcludeNumber(개인번호) 체크 누락

- **파일**: `call_manager/.../service/CallDetectorService.kt:112-184`
- **영향**: Manager 폰 개인 전화가 모두 고객 콜로 등록.

### [High] CM-10. 취소 시 배정된 기사 상태 미복구

- **파일**: `DashboardViewModel.kt:809-811`
- **문제**: `completeCall`은 기사 상태를 WAITING으로 복구하지만, `cancelCall`은 안 함.
- **영향**: 기사가 ASSIGNED/ON_TRIP 상태로 영구 고정 → 새 콜 배차 불가.

### [High] CM-11. 재배차(HOLD → WAITING → 재배차) 전용 워크플로우 없음

- **문제**: HOLD 상태 콜을 WAITING으로 되돌리는 함수/UI 없음. 재배차 자체가 불가능.
- **현재 가능 흐름**: 콜 취소 → 새 콜 생성 → 새 콜에 기사 배차 (수동)

### [Medium] CM-03. SharedPreferences 키 불일치

- **문제**: Manager는 `"call_manager_prefs"`, Detector는 `"detector_config"`. 코드 동기화 시 혼란.

### [Medium] CM-04. SMS 자동 발송 비활성화 불일치

- **문제**: 독립 Detector는 SMS 비활성화, Manager 내장 Detector는 활성 상태. 동일 상황에서 다른 동작.

### [Medium] CM-06. shared_calls allSharedCallsListener에 limit 없음

- **파일**: `DashboardViewModel.kt:365-419`
- **영향**: 10개 사무실 활발 운영 시 데이터 양 선형 증가, Firestore 비용 증가.

### [Low] CM-05. CallReceiver IDLE 초기화 타이밍 차이

- **문제**: Manager는 1초 지연, Detector는 즉시. 1초 내 다음 전화 시 경합 조건.

### [Low] CM-07. 콜 목록 시간 필터가 메모리에서 수행

- **파일**: `CallRepository.kt:86-95`
- **문제**: Room DB에서 전체 가져온 후 메모리 필터링. 현재 규모에서는 문제 없음.

### [Low] CM-08. LazyColumn contentType 미지정

- 현재 규모에서 문제 없음.

---

## 4. Driver App 분석

### [Critical] DR-01. rejectCall() 함수가 완전히 비어있음

- **파일**: `DriverViewModel.kt:459-462`
- **문제**: `fun rejectCall(callId: String) { viewModelScope.launch { } }` - 본문 비어있음.
- **영향**: 기사가 콜을 거절할 수 없음. 콜이 "유령 배차" 상태로 영구 유지.

### [Critical] DR-02. acceptCall() 낙관적 업데이트 시 롤백 메커니즘 부재

- **파일**: `DriverViewModel.kt:366-457`
- **문제**: 즉시 로컬 UI를 ACCEPTED로 변경 후 Firestore transaction 실행. 실패해도 UI는 ACCEPTED 유지.
- **영향**: 기사는 수락된 것으로 보이지만 서버는 ASSIGNED. 두 기사가 동시에 같은 콜 수락 상황 발생 가능.

### [High] DR-03. Race Condition - 수락 실패 시 에러 메시지 없음

- **파일**: `DriverViewModel.kt:422-446`
- **문제**: 이미 다른 기사가 수락한 콜이면 Log.w만 찍고 UI에는 수락된 것처럼 표시.

### [High] DR-04. 네트워크 끊김 후 재연결 시 상태 동기화 없음

- **문제**: Firestore 리스너 제거됨 (비용 절감: 월 $414 → $0.03). NetworkMonitor 클래스 존재하지만 ViewModel에서 미사용.
- **영향**: 운행 중 네트워크 끊기고 Manager가 콜 취소해도 기사 앱은 인지 못함.

### [High] DR-05. cancelTrip()에서 비원자적 Firestore 업데이트

- **파일**: `DriverViewModel.kt:470-506`
- **문제**: 콜 문서와 기사 문서를 별도 `update().await()`로 처리.
- **영향**: 부분 업데이트 시 콜은 HOLD이지만 기사는 ACCEPTED/ON_TRIP으로 불일치.

### [High] DR-06. startDriving(), completeCall()도 동일한 비원자적 업데이트

- **파일**: `DriverViewModel.kt:521-607`

### [Medium] DR-07. confirmAndFinalizeTrip()에서 포인트 실패해도 정산 진행

- **파일**: `DriverViewModel.kt:609-748`
- **문제**: 포인트 처리 실패 시 사용자에게 알림 없이 정산만 진행.

### [Medium] DR-08. LockScreenActivity "거절" 후 Firestore 상태 미변경

- **파일**: `LockScreenActivity.kt:80-86`
- **문제**: 거절 버튼은 Activity만 종료. Firestore 콜 상태는 ASSIGNED 유지. (DR-01과 동일 근본 원인)

### [Medium] DR-09. 오프라인 로그인 시 비밀번호 미검증

- **파일**: `LoginViewModel.kt:130-150`, `SessionManager.kt`
- **문제**: 캐시된 세션으로 이메일만 일치하면 로그인 허용.

### [Medium] DR-10. Presence 시스템과 DriverStatus 이중 관리

- **문제**: RTDB Presence(online/background/offline) vs Firestore DriverStatus(ONLINE/OFFLINE/WAITING/ASSIGNED 등)가 독립 동작.

### [Low] DR-11. FCM 브로드캐스트 리시버가 onPause에서 해제

- **파일**: `MainActivity.kt:301-328`
- 매우 드문 엣지 케이스.

### [Low] DR-12. SharedPreferences에 trip_history JSON 문자열 저장

- 현재 규모에서 문제 없으나 확장 시 주의.

### Detector 배차 vs Manager 배차 수신 동일성

- **동일함**: 두 경우 모두 Firestore 콜 문서가 ASSIGNED로 변경 → 동일한 Cloud Function 트리거 → 동일한 FCM. 기사 앱은 배차 출처를 구분하지 않음.

---

## 5. Customer App 분석

### [Critical] CU-01. 실시간 콜 상태 모니터링 없음 - FCM에만 100% 의존

- **파일**: `MainViewModel.kt:486-521`
- **문제**: `monitorCallStatus_DEPRECATED()` 실시간 리스너가 주석 처리. FCM은 delivery guarantee 없음.
- **영향**: 기사 배정/운행 완료/취소를 전혀 인지하지 못할 수 있음. "콜 요청됨"에서 영구 멈춤.

### [Critical] CU-02. ViewModel이 remember로 재생성되어 상태 손실

- **파일**: `HomeScreen.kt:84-102`
- **문제**: `remember(customerInfo, stepService)` 키로 생성. stepService 바인딩 변경 시 ViewModel 재생성 → 모든 상태 초기화.
- **영향**: 콜 상태 팝업 사라짐, 배정된 기사 정보 사라짐.

### [High] CU-03. 콜 취소 후 재배차 시 "재배차 중" 중간 상태 없음

- **파일**: `MainViewModel.kt:396-416`
- **영향**: 고객이 콜 완전 취소로 오해 → 다른 사무실에 전화 → 중복 콜.

### [High] CU-04. 포인트 차감 후 콜 실패 시 포인트 미복구

- **파일**: `PointService.kt:110-186`, `MainViewModel.kt:232-321`
- **영향**: 포인트만 사라지고 콜은 생성 안 됨 (데이터 손실).

### [High] CU-05. 테스트용 하드코딩이 프로덕션에 존재

- **파일**: `MainActivity.kt:442-450`
- **문제**: 사무실 정보 없으면 양평 VIP 사무실로 자동 설정.
- **영향**: 정보 없이 설치한 모든 고객이 잘못된 사무실로 연결.

### [High] CU-06. Firestore Security Rules - 고객 앱 관련 과도한 권한

- **문제**: calls `read: isAuthenticated()`, `create: if true`, customerPoints `write: isAuthenticated()`

### [Medium] CU-07. 앱 종료/백그라운드 시 콜 상태 복원 불가

- **문제**: callStatus가 메모리(mutableStateOf)에만 저장. 프로세스 킬 시 상태 사라짐.

### [Medium] CU-08. loadBannerAds() 중복 호출

- **파일**: `MainViewModel.kt:139, 361`
- **영향**: 동일 Firestore 리스너 2개 생성.

### [Medium] CU-09. awardPointsForCompletedRide()에서 callId가 항상 null

- **파일**: `MainViewModel.kt:579-698`
- **문제**: DEPRECATED 로직이지만 재사용 시 포인트 적립 미동작.

### [Medium] CU-10. FCM 토큰 갱신 시 사용자 정보 부재로 저장 실패

- **파일**: `MyFirebaseMessagingService.kt:296-329`
- **영향**: 프로필 설정 완료 후에도 FCM 토큰이 저장되지 않아 알림 수신 불가.

### [Medium] CU-11. OfficeSelectionScreen에 테스트 버튼 존재

- **파일**: `OfficeSelectionScreen.kt:77-89`

### [Medium] CU-12. 로그아웃 기능이 동작하지 않음

- **파일**: `MainActivity.kt:663-667`
- **문제**: 로그만 남기고 실제 로그아웃 안 함.

### [Low] CU-13. Province 이름 하드코딩

- **파일**: `MainViewModel.kt:183-188`

### [Low] CU-14. StatusChip에서 WAITING 상태 누락

- **파일**: `CallHistoryScreen.kt:406-413`

### [Low] CU-15. parseAndSaveReferrer에서 IndexOutOfBoundsException 가능

- **파일**: `MainActivity.kt:249-250`

### 공통 관점 분석 요약

| 관점 | 평가 | 비고 |
|------|------|------|
| 동시성 (10개 사무실) | 문제 없음 | 사무실별 attributed node 구조로 분리 |
| 네트워크 불안정 | **Critical** | FCM 100% 의존, 오프라인 큐잉 없음 |
| 에러 UX | 양호 | 에러 메시지 Card, 시스템 알림 존재. FCM 미수신 시 무한 대기 가능 |
| 데이터 격리 | **High 위험** | Firestore rules 임시 코드로 격리 무효화 |
| 상태 일관성 | **Critical 위험** | FCM 누락 시 고객 앱만 다른 상태 |

---

## 6. Firebase + Cloud Functions 분석

### [Critical] FB-01. Realtime Database 보안 규칙 완전 개방

- **파일**: `database.rules.json:1-6`
- **문제**: `.read: true, .write: true`
- **영향**: Presence 데이터 완전 노출/조작 가능. 10개 사무실 전체.

### [Critical] FB-02. designated_drivers collectionGroup 과도하게 개방

- **파일**: `firestore.rules:78-80`
- **문제**: `allow read: if isAuthenticated();` - "임시 디버깅용" 주석이지만 프로덕션 배포 상태.
- **영향**: 인증된 사용자면 모든 사무실 기사 정보 조회 가능.

### [Critical] FB-03. calls 컬렉션 읽기 과도하게 개방

- **파일**: `firestore.rules:126-129`
- **문제**: `isAuthenticated()` - "임시 디버깅용"
- **영향**: 모든 사무실 콜 정보(고객 전화번호, 주소, 요금) 열람 가능.

### [Critical] FB-04. calls 컬렉션 create 완전 개방

- **파일**: `firestore.rules:132`
- **문제**: `allow create: if true;`
- **영향**: 인증 없이 가짜 콜 대량 생성 가능. 서비스 거부 공격.

### [Critical] FB-05. offices collectionGroup 과도하게 개방

- **파일**: `firestore.rules:89-91`
- **영향**: 인증된 사용자면 모든 사무실 정보 조회 가능.

### [High] FB-06. 콜 업데이트 규칙 논리 오류 (Dead Code)

- **파일**: `firestore.rules:169-173`
- **문제**: `status == "SHARED" && status == "WAITING"` - 동시에 성립 불가. Dead code.

### [High] FB-07. `request.auth == null` 패턴 남용 (18곳 이상)

- **문제**: Admin SDK 우회용이지만 실제로는 미인증 클라이언트에게 접근 허용.
- **영향**: REST API로 포인트, 정산, shared_calls 등 직접 조작 가능.

### [High] FB-08. customerPoints / customerInfo 과도한 쓰기 권한

- **파일**: `firestore.rules:258-263, 289-293`
- **문제**: `allow write: if isAuthenticated()`

### [High] FB-09. attributions 무제한 생성

- **파일**: `firestore.rules:229`
- **문제**: `allow create: if true;`

### [High] FB-10. attributionTokens 읽기 규칙 `|| true`

- **파일**: `firestore.rules:375`
- **문제**: `|| true` 때문에 앞 조건들 무의미.

### [Medium] FB-11. 다중 Cloud Functions 트리거 경쟁 조건

- **문제**: 같은 콜 문서 업데이트 시 5-6개 함수 동시 실행. 중복 FCM 가능성.

### [Medium] FB-12. 포인트 트랜잭션 쿼리 제한

- **파일**: `functions/src/handlers/points.ts:43-52`
- **영향**: 동시 공유콜 완료 시 트랜잭션 충돌 가능.

### [Medium] FB-13. 정산 세션 배열 기반 설계

- **파일**: `functions/src/handlers/settlement.ts:137`
- **영향**: Firestore 문서 크기 제한(1MB). 하루 수백 건이면 한계.

### [Medium] FB-14. ACK 기반 알림 재전송 최소 1분 간격

- **파일**: `functions/src/index.ts:151-209`
- **영향**: 대리운전 실시간성에 1분 지연은 현장에서 문제.

### [Medium] FB-15. 오프라인 기사에게 배정 후 자동 재배정 없음

- **파일**: `functions/src/index.ts:434-445`

### [Medium] FB-16. 공유콜 상태 동기화 비원자적 처리

- **파일**: `functions/src/index.ts:1843-1938`
- **영향**: 수락 사무실과 원사무실 콜 상태 불일치 가능.

### [Medium] FB-17. crashlytics-monitor.js 구버전 필드명 사용

- **파일**: `functions/crashlytics-monitor.js:152`
- **문제**: `associatedRegionId` vs 현재 `associatedProvinceId`.

### [Low] FB-18. finalizeWorkDay 인증 검증 미흡

- **파일**: `functions/src/finalizeWorkDay.ts:7`
- **문제**: auth 존재만 확인, 사무실 관리자인지 미검증.

### 긍정적인 점

1. 포인트 시스템: 트랜잭션 + 중복 방지 체크 잘 되어 있음
2. 상태 머신: 콜 상태 전환 규칙이 세밀하게 정의됨
3. FCM 토큰 자동 정리: 무효 토큰 감지/제거 로직 각 함수에 포함
4. 정산 불일치 감지: 원본 콜과 정산 세션 간 불일치 자동 감지 시스템 존재
5. 상세한 로깅으로 디버깅 용이

---

## 7. 전체 이슈 심각도별 요약

### Critical (즉시 수정 필요) - 14건

| # | 출처 | ID | 문제 |
|---|------|----|------|
| 1 | Cross | CROSS-01 | Detector/Manager 배차 시 기사 상태 불일치 (ON_TRIP vs ASSIGNED) |
| 2 | Cross | CROSS-02 | CallStatus enum 4개 앱 간 불일치 |
| 3 | Cross | CROSS-03 | Customer App FCM 100% 의존, 실시간 리스너 비활성화 |
| 4 | Firebase | FB-01 | RTDB 보안 규칙 `read/write: true` 완전 개방 |
| 5 | Firebase | FB-02 | designated_drivers collectionGroup 과도한 읽기 |
| 6 | Firebase | FB-03 | calls 컬렉션 읽기 `isAuthenticated()` 임시 디버깅 코드 |
| 7 | Firebase | FB-04 | calls `allow create: if true` |
| 8 | Firebase | FB-05 | offices collectionGroup 과도한 읽기 |
| 9 | Manager | CM-01 | 내장 Detector에 wasRinging 방어 로직 누락 |
| 10 | Manager | CM-09 | cancelCall()이 로컬 DB 미업데이트 |
| 11 | Detector | CD-01 | 마감 시 RINGING + IDLE 이중 콜 생성 |
| 12 | Driver | DR-01 | rejectCall() 함수 본문 비어있음 |
| 13 | Driver | DR-02 | acceptCall() 낙관적 업데이트 롤백 없음 |
| 14 | Customer | CU-02 | ViewModel remember 재생성으로 상태 손실 |

### High (중요) - 17건

| # | 출처 | ID | 문제 |
|---|------|----|------|
| 1 | Cross | CROSS-06 | 비원자적 Firestore 업데이트 (전 앱) |
| 2 | Cross | CROSS-07 | rejectCall 불가 + 취소 시 기사 상태 미복구 → 재배차 불가 |
| 3 | Manager | CM-02 | 내장 Detector에 ExcludeNumber 체크 누락 |
| 4 | Manager | CM-10 | 취소 시 배정된 기사 상태 미복구 |
| 5 | Manager | CM-11 | 재배차 전용 워크플로우 없음 |
| 6 | Detector | CD-04 | SharedPreferences 5개 혼용 |
| 7 | Detector | CD-05 | 배차 실패 시 사용자 피드백 없음 |
| 8 | Detector | CD-06 | 배차 시 Firestore 트랜잭션 미사용 |
| 9 | Detector | CD-07 | startDispatchDialog에 callId 누락 |
| 10 | Detector | CD-08 | 비밀번호 SharedPreferences 평문 저장 |
| 11 | Driver | DR-03 | Race condition - 수락 실패 시 에러 메시지 없음 |
| 12 | Driver | DR-04 | 네트워크 재연결 시 상태 동기화 없음 |
| 13 | Driver | DR-05/06 | cancelTrip/startDriving/completeCall 비원자적 |
| 14 | Customer | CU-03 | 취소 후 "재배차 중" 중간 상태 없음 |
| 15 | Customer | CU-04 | 포인트 차감 후 콜 실패 시 포인트 미복구 |
| 16 | Customer | CU-05 | 테스트용 하드코딩 (양평 VIP 사무실) 프로덕션 존재 |
| 17 | Firebase | FB-07 | `request.auth == null` 패턴 남용 (18곳) |

### Medium - 17건

| # | 출처 | ID | 문제 |
|---|------|----|------|
| 1 | Manager | CM-03 | SharedPreferences 키 불일치 |
| 2 | Manager | CM-04 | SMS 자동 발송 비활성화 불일치 |
| 3 | Manager | CM-06 | shared_calls allSharedCallsListener limit 없음 |
| 4 | Detector | CD-09 | Android 9 전화번호 확인 불가 |
| 5 | Detector | CD-10 | 마감→운영 전환 시 중복 콜 |
| 6 | Detector | CD-11 | 콜 생성 실패 시 임시 ID 팝업 |
| 7 | Detector | CD-12 | shared_calls 루트 레벨 격리 어려움 |
| 8 | Detector | CD-13 | Sentry DSN 하드코딩 |
| 9 | Driver | DR-07 | 포인트 실패해도 정산 진행, 알림 없음 |
| 10 | Driver | DR-08 | LockScreen 거절 후 Firestore 미변경 |
| 11 | Driver | DR-09 | 오프라인 로그인 비밀번호 미검증 |
| 12 | Driver | DR-10 | Presence/DriverStatus 이중 관리 |
| 13 | Customer | CU-07 | 앱 종료 시 콜 상태 복원 불가 |
| 14 | Customer | CU-08 | loadBannerAds() 중복 호출 |
| 15 | Customer | CU-10 | FCM 토큰 갱신 시 저장 실패 |
| 16 | Customer | CU-11 | 테스트 버튼 프로덕션 노출 |
| 17 | Customer | CU-12 | 로그아웃 기능 미동작 |

### Low - 9건

| # | 출처 | ID | 문제 |
|---|------|----|------|
| 1 | Manager | CM-05 | CallReceiver IDLE 초기화 타이밍 차이 |
| 2 | Manager | CM-07 | 콜 목록 시간 필터 메모리 수행 |
| 3 | Manager | CM-08 | LazyColumn contentType 미지정 |
| 4 | Detector | CD-14 | BootCompletedReceiver SharedPreferences 불일치 |
| 5 | Detector | CD-15 | System.exit(0) 사용 |
| 6 | Driver | DR-11 | FCM 리시버 onPause 해제 |
| 7 | Driver | DR-12 | trip_history JSON 문자열 저장 |
| 8 | Customer | CU-13 | Province 이름 하드코딩 |
| 9 | Customer | CU-14 | StatusChip WAITING 상태 누락 |

---

## 8. 우선 수정 권장 순서

### Phase 1: 보안 긴급 패치 (즉시)

1. RTDB 보안 규칙 설정 (`read/write: true` 제거)
2. Firestore `allow create: if true` 제거
3. 디버깅용 `isAuthenticated()` 임시 코드 제거
4. `request.auth == null` 패턴 정리
5. `attributionTokens` 규칙의 `|| true` 제거

### Phase 2: 데이터 무결성 (1주 내)

6. Detector 배차 시 기사 상태를 `ASSIGNED`로 통일
7. CallStatus enum 통합 (공통 모듈 또는 상수 정의)
8. rejectCall() 구현
9. 마감 시 이중 콜 생성 방지 (중복 체크 로직)
10. cancelCall()에서 기사 상태 복구 추가
11. cancelCall()에서 로컬 DB도 업데이트

### Phase 3: 안정성 개선 (2주 내)

12. 주요 상태 변경에 Firestore 트랜잭션 적용 (배차, 취소, 완료)
13. Customer App에 Firestore 리스너 fallback 추가
14. Driver App acceptCall() 롤백 메커니즘
15. 내장 Detector에 wasRinging, ExcludeNumber 로직 동기화
16. 배차 실패 시 사용자 피드백 UI
17. 재배차(HOLD → WAITING) 워크플로우 구현

### Phase 4: UX 및 코드 품질 (3주 내)

18. 테스트용 하드코딩 제거 (양평 VIP 사무실, 테스트 버튼)
19. ViewModel remember → viewModel() 패턴 전환
20. 포인트 차감/콜 실패 시 포인트 복구 로직
21. SharedPreferences 통합 정리
22. 비밀번호 평문 저장 → EncryptedSharedPreferences

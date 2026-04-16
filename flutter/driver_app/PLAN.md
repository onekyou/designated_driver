# 기사앱 Flutter 단독 iOS Phase 1 MVP

> **목적**: Flutter `driver_app_flutter/` (Phase 1~5 완료)에 iOS 빌드 추가하여 **Mac 없이 Codemagic 기반 App Store 정식 출시**.
> **App Clip**: 불필요 (기사는 정식 설치 사용자)
> **작성일**: 2026-04-16

---

## Phase 1 목표

### 한 줄 정의
**기사가 iOS Flutter 앱으로 배차 수신·운행·정산을 완결하고 WAITING으로 복귀할 수 있다.**

### 완료 기준 (전부 충족해야 Phase 1 완료)

1. **콜 사이클 1순환 iOS 실기기 검증** (TestFlight 설치)
   - 콜매니저(Android)가 iOS 기사 지정 → iOS 기사앱 배차 알림 수신
   - 기사 수락 → 콜매니저·손님앱 상태 갱신 인지
   - 운행 시작 → 콜매니저·손님앱 통지
   - 운행 완료 → 정산 입력 → 제출 → 관리자 확인 → WAITING 복귀
   - 이월금 transferCarryOver → confirmReceiveCarryOver → SETTLED

2. **로그인·자동로그인 iOS 실기기 검증**
   - 최초 로그인 → flutter_secure_storage 저장
   - 앱 재시작 → 자동 로그인 → 이전 상태 복원
   - 로그아웃 → 자격증명 삭제 + Presence offline

3. **FCM 6종 수신 + Delivery ACK** (iOS Time Sensitive)
   - call_assigned, call_cancelled, SETTLEMENT_FINALIZED, SETTLEMENT_CONFIRMED, SETTLEMENT_REJECTED, CARRYOVER_TRANSFERRED
   - 포그라운드 / 백그라운드 / 잠금(배너) 수신 확인
   - `acknowledgeNotification` Cloud Function 호출 확인
   - **선결**: CF payload에 `apns: {payload: {aps: {'content-available': 1}}}` 추가 (의제 4)

4. **취소 경로 모든 분기 + 기사 인지 UX**
   - 관리자 취소(CANCELED) → call_cancelled FCM → UI 자동 정리
   - 기사 거절 → WAITING 복귀
   - 기사 운행 취소 → CANCELLED_BY_DRIVER
   - 손님 취소(ACCEPTED/PREPARING 단계) → "고객이 콜을 취소했습니다" Snackbar + 홈 이동
   - 3분 타임아웃 → CF가 call_cancelled FCM 전송

5. **정산 흐름 전수**
   - 5가지 결제 방식 (현금 / 이체 / 외상 / 포인트 / 현금+포인트)
   - 정산 공식 (officeDeposit, driverShare, finalDeposit, realDeposit) 검증
   - 통합 제출 (isIntegration, PENDING_CONFIRM 재제출)
   - 매니저 반려 → 재제출 경로

6. **Presence**
   - Firebase Realtime DB `.info/connected` → online/background/offline
   - `onDisconnect` → offline 자동 설정
   - 로그아웃 cleanup
   - CF `checkAssignedTimeout` 스케줄러 동작 전제 (서버측, 영향 없음)

7. **Firestore 자동 오프라인 큐잉 작동 확인**
   - 단기 오프라인(수 분) 후 복귀 시 로컬 write 정상 반영

8. **TestFlight Internal/External 파일럿**
   - Internal: 기사 5~10명, 즉시 배포
   - External: Apple 외부 베타 심사 통과 후
   - **운영 데이터 수집** (REINFORCEMENT/TRIGGERS.md 측정 항목)

---

## Phase 1 범위 (포함 — B)

### 라이프사이클
1. 배차 수신 (잠금·백그라운드 포함, 앱 강제 종료 제외 — R1)
2. 기사 수락·거절 → Firestore 트랜잭션
3. 운행 시작·완료 → 양쪽 통지
4. 정산 입력·제출·반려·재제출
5. 이월금 처리
6. WAITING 복귀

### 알림 (iOS)
- APNs 등록 + FCM 토큰 Firestore 저장
- FCM 6종 수신 + 핸들러
- **iOS Time Sensitive Notification** (interruptionLevel = .timeSensitive, Focus 우회)
- Delivery ACK Cloud Function 호출

### 앱 기본
- Phone Auth/이메일 로그인 (Firebase Auth)
- 자동로그인 (flutter_secure_storage)
- shared_preferences (사무실 정보 캐시)
- Presence 기본 (online/background/offline)
- Firestore 자동 offline persistence

### 화면 (Flutter Phase 1~5 기구현)
- 로그인·회원가입·비밀번호 찾기
- 홈 (대기·배차·운행 상태 분기)
- 콜 상세
- 운행 내역 + 정산 + 업무마감
- 설정
- 주소 검색 (카카오 API)

### 콜매니저·손님앱 연동
- 기사앱 → CF → 콜매니저 매니저 FCM (서버 작동)
- 기사앱 → CF → 손님앱 FCM (서버 작동)
- 손님앱 취소 → CF → 기사앱 call_cancelled (수신)

---

## Phase 1 범위 (제외 — iOS R1/D)

### iOS 구조적 불가 (D)
- ❌ **LockScreen 커스텀 풀스크린 UI** → R1 CallKit 부분 복원 가능, 심사 리스크 (`flutter_callkit_incoming`)
- ❌ **Foreground Service 영구 활성** → iOS 등가 없음. 이벤트 기반 wakeup으로 재설계
- ❌ **BootReceiver 자동 재시작** → iOS 정책상 불가
- ❌ **3초 반복 알림음** → Time Sensitive 1회로 축소. R1 CallKit 도입 시 시스템 링톤 반복

### iOS R1 (운영 데이터 발동)
- R1_LOCKSCREEN: VoIP Push + CallKit + Live Activity (`flutter_callkit_incoming`)
- R1_APP_KILLED: VoIP push로 강제 종료 앱 깨움
- R1_PENDING_SYNC: sqflite/drift 기반 정산 오프라인 큐
- R1_NETWORK_BANNER: connectivity_plus 기반 끊김 배너

### iOS R2
- R2_BG_SYNC: workmanager로 BGTaskScheduler 주기 sync (실행 보장 없음)
- R2_REFERRAL_QR: 추천 QR 화면

---

## Android 기능 퇴보 방지

**Flutter Android 빌드는 기존 Kotlin 기능을 완전 보존**:
- LockScreenActivity (Kotlin 네이티브) → MethodChannel로 유지
- ForegroundService (`flutter_foreground_task`) → Android 동작
- BootReceiver (Kotlin 네이티브) → AndroidManifest 등록 유지
- WAKE_LOCK → AndroidManifest 권한 유지

iOS에서 빠지는 기능이 Android에서도 빠지지 않도록 **빌드 타임 플래그가 아닌 런타임 분기**(`Platform.isIOS`)로 처리.

---

## 기술 스택

- **언어**: Dart 3.x
- **Flutter SDK**: 3.41+ (현재 `/c/Users/kala1/flutter_sdk/` 3.41.6)
- **상태 관리**: Riverpod 2.x StateNotifier
- **데이터 모델**: Freezed
- **라우팅**: GoRouter
- **Firebase SDK**: cloud_firestore, firebase_auth, firebase_messaging, firebase_database, cloud_functions, firebase_core
- **로컬 저장**: shared_preferences + flutter_secure_storage (의제 12에서 sqflite/drift 추가 결정)
- **iOS 알림**: flutter_local_notifications (Time Sensitive)
- **백그라운드**: flutter_foreground_task (Android only, iOS no-op)
- **iOS 최소 버전**: 의제 1 결정 (사전 권장 iOS 15)
- **Android 최소 SDK**: 24 (Android 7.0)
- **CI/CD**: Codemagic (codemagic.yaml)

---

## 패키지 ID (의제 2 결정 후 확정)

- **Android 현재**: `com.designated.driverapp.app`
- **iOS 현재**: `com.designated.driverAppFlutter` (Bundle ID 불일치)
- **결정 옵션**: 유지+즉시교체 / 새 ID 병행+듀얼토큰 / 새 ID 즉시교체

---

## 매핑 문서 구성 (MVP/ 아래)

팀 의제 결정 후 작성:

| 파일 | 내용 | 주 작성자 |
|------|------|----------|
| `OVERVIEW.md` | 본 문서의 결정 정리 | 중재자 |
| `MODELS.md` | Kotlin data class → Dart Freezed 매핑 | kotlin-expert |
| `ENUMS.md` | CallStatus, DriverStatus, DailySettlementStatus | kotlin-expert |
| `NOTIFIERS.md` | DriverWorkflowNotifier·LoginNotifier 등 Riverpod 스펙 | kotlin-expert |
| `SCREENS.md` | Flutter 위젯 명세 | kotlin-expert |
| `FIRESTORE.md` | 트랜잭션 6곳·이월금 리스너·Repository | kotlin-expert |
| `FCM.md` | iOS Time Sensitive·6종 핸들링·Delivery ACK | flutter-expert |
| `AUTH.md` | 로그인 flow + 자동로그인 마이그레이션 | kotlin-expert |
| `PLATFORM_CHANNELS.md` | LockScreen/ForegroundService Android-only 채널 | flutter-expert |
| `BUILD.md` | codemagic.yaml + Info.plist + Privacy Manifest | flutter-expert |

**최종 검토**: flutter-expert가 kotlin-expert 초안에 Flutter 관용구 수정 + Codemagic 빌드 가능성 검증.

---

## 출시 후 전환

Phase 1 → **TestFlight Internal 파일럿** → **External 베타** → **App Store Release** → **운영 데이터 수집** → `REINFORCEMENT/TRIGGERS.md` 발동 기준 충족 시 R1 도입.

---

## 참조

- 마스터 플랜: `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md`
- `flutter/WORKING_DOC.md` — 의제 14개
- `flutter/FUNCTIONAL_INVENTORY.md` — 3컬럼 분류 (Android/iOS/Flutter 매핑)
- `ios/SHARED_LOGIC.md` — Firestore·상태·정산 공식
- `memory/plan_flutter_driver_app.md` — Flutter Phase 1~5 구현 이력
- `driver_app_flutter/lib/` — 현재 구현
- `driver_app/app/src/main/java/com/designated/driverapp/` — Kotlin 원본 참조

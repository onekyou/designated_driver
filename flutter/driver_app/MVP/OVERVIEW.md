# Flutter 기사앱 Phase 1 MVP — 개요

> **목적**: Flutter `driver_app_flutter/` (Phase 1~5 완료)에 iOS 빌드 추가하여 **Mac 없이 Codemagic 기반 App Store 정식 출시**. 본 문서는 팀 의제 결정(WORKING_DOC.md §5) 11건을 Phase 6 실행 가능 사양으로 종합.
> **작성일**: 2026-04-16
> **참조**: `flutter/WORKING_DOC.md`, `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md`

---

## Phase 1 한 줄 정의

**기사가 iOS Flutter 앱으로 배차 수신·운행·정산을 완결하고 WAITING으로 복귀할 수 있다.**

---

## 완료 기준 8종

1. **콜 사이클 1순환 iOS 실기기 검증** (TestFlight 설치 기반)
   - 콜매니저(Android) → CF `oncallassigned` → iOS Flutter 기사앱 Time Sensitive 배너 수신
   - 수락 → Firestore 트랜잭션 → 콜매니저·손님앱 상태 갱신
   - 운행 시작 → 운행 완료 → 정산 입력 → 제출 → 매니저 확인 → WAITING 복귀
   - 이월금 `transferCarryOver` → `confirmReceiveCarryOver` → SETTLED
2. **로그인·자동로그인 iOS 실기기 검증**
   - Firebase Auth currentUser 자동 상속 (첫 실행 시)
   - 세션 만료 시 재로그인 + 이메일 사전 채움 UX
   - 로그아웃 → credentials 삭제 + Presence offline
3. **FCM 6종 수신 + Delivery ACK** (iOS Time Sensitive `interruptionLevel`)
4. **취소 경로 모든 분기** (관리자/기사/손님/타임아웃) + 기사 인지 UX
5. **정산 흐름 전수** (5 결제방식 × 정산 공식 + 통합 제출 + 매니저 반려 재제출)
6. **Presence 기존 `presence_service.dart` 그대로 iOS 동작**
7. **Firestore 자동 오프라인 큐잉** (단기 오프라인 복구)
8. **TestFlight Internal/External 파일럿** + 측정 인프라 4종 지표 수집

---

## 의제 결정 11건 Phase 6 반영 매핑

| 의제 | 결정 | Phase 6 실행 항목 |
|------|------|------------------|
| **1** iOS 15.0 | `Podfile` deployment target `'12.0'` → `'15.0'` (line 2, 47) | **즉시 착수 가능** |
| **4** apns 블록 | CF `oncallassigned` payload 수정 (`functions/src/index.ts:560-573`) + CF 28곳+ audit | **서버 별도 과제** (Phase 6 빌드 전 완료) |
| **5** FCM 필드명 | `fcmToken` 유지 + `fcmTokenPlatform` 메타 필드 추가 | `fcm_service.dart` 토큰 저장 시 `Platform.isIOS ? 'ios' : 'android'` 1줄 추가 |
| **6** Privacy Manifest | Info.plist 사유 문자열 4종 + `PrivacyInfo.xcprivacy` + Podfile `permission_handler` 매크로 | 3개 파일 신규/수정 |
| **7** LockScreen | Time Sensitive MVP + Feature Flag 사전 설계 | `fcm_service.dart:157-160` `interruptionLevel` 추가 + `feature_flags.dart` + `incoming_call_service.dart` 신규 |
| **8** Foreground Service | 기존 `presence_service.dart` 1:1 유지 | **신규 작업 없음** |
| **9** 자동로그인 | Firebase Auth currentUser 자동 상속 + 이메일 사전 채움 | 로그인 화면 수정 + flutter_secure_storage iOS accessibility |
| **10** 손님앱 전환 | 기사앱은 같은 applicationId 유지 | Kotlin 기사앱은 별도. Flutter iOS 신규 Bundle ID는 의제 2 결정 후 |
| **11** 측정 인프라 | CF `acceptanceEvents` + 기사 `platform` 필드 + Flutter Analytics/Crashlytics 필수 | pubspec 4종 의존성 추가 + main.dart Crashlytics 초기화 |
| **12** 로컬 저장소 | MVP는 shared_preferences + Firestore offline. sqflite는 R1 | **신규 작업 없음** |
| **14** 기반 정책 | Dart 3 sealed class, Riverpod ^2.5.1, FVM 즉시, App Bundle, Freezed required | `.fvmrc` 신규 + 점진 마이그레이션 |

---

## Phase 1 범위 (포함 — B)

### 라이프사이클 (`flutter/FUNCTIONAL_INVENTORY.md` §1.1~§1.9)
- 배차 수신 (잠금·백그라운드 포함, 앱 강제 종료 제외 — R1)
- 기사 수락·거절 → Firestore 트랜잭션
- 운행 시작·완료 → 양쪽 통지
- 정산 입력·제출·반려·재제출
- 이월금 처리
- WAITING 복귀

### 알림 (iOS Time Sensitive)
- APNs 등록 + FCM 토큰 Firestore 저장 (`fcmToken` + `fcmTokenPlatform`)
- FCM 6종 수신 + 핸들러 (call_assigned / call_cancelled / SETTLEMENT_FINALIZED / SETTLEMENT_CONFIRMED / SETTLEMENT_REJECTED / CARRYOVER_TRANSFERRED)
- `interruptionLevel: .timeSensitive`로 Focus 우회
- Delivery ACK Cloud Function 호출

### 앱 기본
- Phone Auth / 이메일 로그인 (Firebase Auth)
- 자동로그인 (flutter_secure_storage + Firebase Auth currentUser 상속)
- shared_preferences (사무실 정보 캐시)
- Presence (기존 `presence_service.dart`)
- Firestore 자동 offline persistence

### 측정 인프라 (의제 11 필수)
- Firebase Analytics + Crashlytics (신규 도입)
- `acceptanceEvents` 이벤트 로깅 (CF 측 완료 전제)
- 최소 지표 4종: 수락률, Time-To-Accept, FCM 도달률, Offline 판정 빈도

### 화면 (Flutter Phase 1~5 기구현, iOS 빌드만 추가)
- 로그인·회원가입·비밀번호 찾기
- 홈 (대기·배차·운행 상태 분기)
- 콜 상세
- 운행 내역 + 정산 + 업무마감
- 설정
- 주소 검색 (카카오 API)

---

## Phase 1 범위 (제외 — iOS R1/D, Phase 2)

### iOS 구조적 불가 (D)
- ❌ **LockScreen 커스텀 풀스크린 UI** → R1 CallKit 심사 실측 (Feature Flag 준비됨)
- ❌ **Foreground Service 영구 활성** → onDisconnect + ScenePhase로 대체
- ❌ **BootReceiver 자동 재시작** → iOS 정책상 불가
- ❌ **3초 반복 알림음** → Time Sensitive 1회로 축소

### iOS R1 (운영 데이터 발동)
- R1_LOCKSCREEN: VoIP Push + CallKit + Live Activity (`flutter_callkit_incoming`, 심사 리스크)
- R1_APP_KILLED: VoIP push 강제 종료 앱 깨움
- R1_PENDING_SYNC: sqflite/drift 기반 정산 오프라인 큐
- R1_NETWORK_BANNER: `connectivity_plus` 끊김 배너

### iOS R2
- R2_BG_SYNC: `workmanager` BGTaskScheduler (실행 보장 없음)
- R2_REFERRAL_QR: 추천 QR 화면
- R2_WAKE_LOCK 대체: `wakelock_plus`
- R2_HOTFIX_NEEDED: Shorebird OTA (Crashlytics 기반 발동)

### Phase 2 (Mac 도착 후 — Swift App Clip Swift 타겟, 의제 13)
- 손님앱 전용. 기사앱에는 없음

---

## Android 기능 퇴보 방지 원칙

Flutter Android 빌드는 기존 Kotlin 기능을 **완전 보존**:
- LockScreenActivity (Kotlin 네이티브) → `lock_screen_service.dart` MethodChannel로 유지
- ForegroundService (`flutter_foreground_task`) → Android 동작 (iOS no-op 확정)
- BootReceiver (Kotlin 네이티브) → AndroidManifest 등록 유지
- WAKE_LOCK → AndroidManifest 권한 유지

**런타임 분기 원칙**: `Platform.isIOS` / `Platform.isAndroid` 런타임 체크. 빌드 타임 flavor 아님.

---

## 기술 스택 (의제 1 + 14 결정 반영)

| 항목 | 결정 | 근거 의제 |
|------|------|----------|
| 언어 | Dart 3.x (SDK `^3.5.4`) — sealed class + patterns 적극 | 14-A |
| Flutter SDK | 3.41.6 FVM 고정 | 14-C |
| iOS deployment | 15.0 (Podfile 상향 필수) | 1 |
| Android minSdk | 24 (기존 유지) | — |
| 상태 관리 | Riverpod 2.x `^2.5.1` | 14-B |
| 데이터 모델 | Freezed `required` + nullable `?` + `@Default()` | 14-G |
| 라우팅 | GoRouter | — |
| 로컬 저장 | shared_preferences + flutter_secure_storage (sqflite R1) | 12, 9 |
| Firebase | firebase_core/auth/messaging/firestore/database/functions + **analytics/crashlytics 신규** | 11 |
| 알림 | flutter_local_notifications (Time Sensitive iOS) + firebase_messaging | 7 |
| 백그라운드 | flutter_foreground_task (Android only, iOS no-op) | 8 |
| 배포 | Android App Bundle, iOS 단일 IPA | 14-F |
| CI/CD | Codemagic (의제 3 결정 후 codemagic.yaml) | 3 |

---

## 패키지 ID (의제 2 대기)

- **Android 현재**: `com.designated.driverapp.app` (Kotlin 기사앱과 동일 패키지)
- **iOS 현재**: `com.designated.driverAppFlutter` (**불일치**, 의제 2에서 통일 결정)
- **Apple Developer 승인 후 최종 확정**

---

## Phase 6 실행 순서 (Apple 승인 독립 작업만)

### Week 0~1 (병렬)
1. **Podfile iOS 15 상향** (line 2, 47)
2. **`.fvmrc` 3.41.6 고정** + `.gitignore` 업데이트
3. **pubspec.yaml 신규 의존성 4종 추가**:
   ```yaml
   firebase_analytics: ^11.3.0
   firebase_crashlytics: ^4.1.0
   in_app_update: ^4.2.3
   firebase_remote_config: ^5.1.0
   ```
4. **`Runner/Info.plist` Permission 사유 문자열 4종 한국어** (의제 6)
5. **`Runner/PrivacyInfo.xcprivacy` 수동 작성** (의제 6)
6. **`Podfile` `post_install` `permission_handler` 매크로 설정** (의제 6)

### Week 1~2 (매핑 문서 작성 + 코드 보강)
7. `fcm_service.dart:157-160` `interruptionLevel` 추가 (의제 7)
8. `fcm_service.dart` 토큰 저장 시 `fcmTokenPlatform` 메타 필드 (의제 5)
9. `feature_flags.dart` + `incoming_call_service.dart` 신규 (의제 7)
10. `lock_screen_service.dart` `if (!Platform.isAndroid) return;` 가드 (의제 7)
11. `flutter_secure_storage` iOS `KeychainAccessibility.first_unlock_this_device` 설정 (의제 9)
12. `main.dart` Crashlytics 초기화 (의제 11)
13. 로그인 화면 이메일 사전 채움 + 비밀번호 찾기 다이얼로그 (의제 9)

### Week 2~3 (서버측 별도 과제, 중재자 또는 사용자)
14. **CF `oncallassigned` apns 블록 추가** (`functions/src/index.ts:560-573`, 의제 4)
15. **CF 전체 28곳+ audit + apns 블록 일괄 적용** (의제 4 확대)
16. **CF `acceptanceEvents` 컬렉션 + 월 집계 스케줄러** (의제 11)
17. **Firestore 보안 규칙 업데이트** (`acceptanceEvents` admin 전용)

### Week 3 이후 (Apple 승인 후)
18. 의제 2 Bundle ID 확정 + Firebase Console iOS 앱 등록
19. 의제 3 Codemagic `codemagic.yaml` 작성 + 첫 빌드
20. TestFlight Internal 업로드 + 기사 파일럿

---

## 매핑 문서 구성 (본 OVERVIEW.md 포함 10개)

| 파일 | 내용 | 주 작성자 | 상태 |
|------|------|----------|------|
| `OVERVIEW.md` | 본 문서 — 의제 결정 Phase 6 종합 | 중재자 | ✅ 완료 |
| `MODELS.md` | Kotlin data class → Freezed 매핑 (14 models) | kotlin-expert | ⏳ |
| `ENUMS.md` | CallStatus / DriverStatus / DailySettlementStatus sealed class | kotlin-expert | ⏳ |
| `NOTIFIERS.md` | DriverWorkflowNotifier ~600줄 Riverpod 스펙 | kotlin-expert | ⏳ |
| `SCREENS.md` | 12개 화면 Flutter 위젯 명세 | kotlin-expert | ⏳ |
| `FIRESTORE.md` | 트랜잭션 6곳 + 이월금 리스너 | kotlin-expert | ⏳ |
| `FCM.md` | iOS Time Sensitive + 6종 핸들링 + Delivery ACK | flutter-expert | ⏳ |
| `AUTH.md` | 로그인 + 자동로그인 마이그레이션 | kotlin-expert | ⏳ |
| `PLATFORM_CHANNELS.md` | LockScreen/ForegroundService Android-only 채널 | flutter-expert | ⏳ |
| `BUILD.md` | codemagic.yaml + Podfile + Privacy Manifest | flutter-expert | ⏳ |

**최종 검토**: flutter-expert가 kotlin-expert 초안에 Flutter 관용구 수정 + Codemagic 빌드 가능성 검증.

---

## 리스크 & 사전 완화

| 리스크 | 완화 |
|-------|------|
| Firebase Auth currentUser 자동 상속 실패 → 전원 재로그인 | 의제 9 대비 UX: 이메일 사전 채움 + 비밀번호 재설정 다이얼로그. 신뢰 손실 최소화 |
| CF apns 블록 audit 누락 → iOS 일부 FCM 무응답 | 의제 4 CF 전체 28곳+ 체크리스트 실행. Firebase Emulator 테스트 |
| 측정 인프라 없어 R1 발동 판단 불가 | 의제 11 Analytics/Crashlytics 즉시 도입 + CF `acceptanceEvents` Phase 1 출시 전 완료 |
| iOS 15 상향 후 일부 플러그인 호환성 문제 | Codemagic 첫 빌드에서 검증. 최악 iOS 16 재상향 |
| App Store 심사 리젝 (Privacy Manifest / Permission 사유) | 의제 6 체크리스트 엄수. 데모 계정 + Review Notes 준비 |
| Bundle ID 변경이 Kotlin Android 기사앱 FCM 영향 | 의제 2 결정에 따라 Android Kotlin 기사앱 교체 전략 별도 |

---

## 출시 후 측정 → R1 발동

Phase 1 출시 → TestFlight Internal(1주) → External(2주) → App Store Release → **운영 데이터 수집** → `flutter/driver_app/REINFORCEMENT/TRIGGERS.md` 발동 기준 충족 시 R1 도입.

---

## 참조

- `flutter/WORKING_DOC.md` §5 — 의제 결정 전문
- `flutter/FUNCTIONAL_INVENTORY.md` — Kotlin/Flutter/iOS 3컬럼 분류
- `flutter/driver_app/PLAN.md` — Phase 1 개요
- `flutter/driver_app/REINFORCEMENT/TRIGGERS.md` — R1/R2 발동 기준
- `ios/SHARED_LOGIC.md` — Firestore/상태/정산 언어 독립 명세
- `memory/plan_flutter_driver_app.md` — Flutter Phase 1~5 이력
- `driver_app_flutter/` — 현재 구현
- `driver_app/app/src/main/java/com/designated/driverapp/` — Kotlin 원본

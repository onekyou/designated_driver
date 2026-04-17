# Current Status — 날짜별 상태 누적

> MEMORY.md에서 분리된 상세 상태 이력. 날짜 내림차순 정렬 (최신이 위).

---

## 2026-04-17 — Apple 생태계 진입 + iOS 빌드 인프라 구축 (Phase 6 코딩 진입 직전)

**⚠️ 중요 프레이밍**: 오늘 "빌드 성공"은 **기존 3/27 코드가 iOS 컴파일된다는 껍데기 검증**이지, **iOS 대응 코드 보강이 끝난 것이 아님**. 실제 iOS 출시까지는 Phase 6 코딩 보강(7개 항목)이 남아있음. 상세: `memory/phase6_coding_remaining.md`

### 오늘 실제 달성한 것

**① Apple Developer / App Store Connect 설정**
- Apple Team ID: `VCJD377MAU`
- Bundle ID (App ID 등록): `com.designated.driverapp.app` (Android applicationId와 통일)
- Capabilities: Push Notifications 활성
- App Store Connect 앱 `콜마당 기사` 생성 (기본 언어 한국어, SKU `driverapp-ios-001`)

**② Codemagic 빌드 인프라**
- 레포 연결 완료 (`manager-direct-drive` 브랜치)
- `codemagic.yaml` 레포 루트 배치 (Build ID `69e2395f...`)
- Workflow: `Driver iOS Test Build (Phase A - No Codesign)` — `mac_mini_m2`, `flutter: 3.41.6`, `xcode: latest`, `max_build_duration: 90`
- **첫 빌드 45분 타임아웃** → max_build_duration 45→90 상향 → **재빌드 10분 37초 성공**
- Artifact: `Runner.app.zip` 15.16 MB (⚠️ 서명 없음, 아이폰 설치 불가)

**③ 스펙 문서 정리 (.md 파일만 — 런타임 영향 없음)**
- P0 9건 재검증 (`memory/p0_status_2026-04-17.md`)
- A.1/A.2/A.3/B.3/C.1 = MVP 매핑 문서(`flutter/*/MVP/*.md`)의 Dart 코드 예시 수정
- D.1 = CF 스펙 (`flutter/SERVER_TASKS.md`) 수정 — **실제 CF 배포는 아직 안 함**
- E.1(a) = 재검증으로 이미 정확 확인

**④ 메모리 정리**
- `memory/apple_ios_ids.md`: Apple 식별자 기록
- `memory/user_ios_experience.md`: 사용자 iOS 무경험 프로필
- `memory/p0_status_2026-04-17.md`: P0 재검증 결과
- `memory/phase6_coding_remaining.md`: **Phase 6 남은 코딩 작업 체크리스트 (신규)**

### 오늘 커밋 6건 (`manager-direct-drive` 푸시)

```
c60dec3a docs(memory): 2026-04-17 상태 기록 (1차, 이후 정정 커밋 추가됨)
64814dc8 fix(codemagic): max_build_duration 45 → 90
9fabf885 fix(codemagic): codemagic.yaml 레포 루트 이동
a943a1e5 fix(flutter): P0 A.1/A.2/A.3/B.3/C.1 (.md 스펙 문서) 패치
d759fdbe feat(codemagic): Phase A 파이프라인 + iOS 15.0 타깃 상향
04e54502 docs(flutter): P0 D.1 + Apple ID 등록 정보 메모리화
```

### P0 진행 상태 (정확한 해석)

| # | 대상 파일 | 상태 | 의미 |
|---|----------|------|------|
| A.1/A.2/A.3 | `flutter/driver_app/MVP/NOTIFIERS.md` | ✅ 스펙 수정 | 미래 참조용 문서 정리 |
| B.3 | `flutter/customer_app/MVP/NOTIFIERS.md` | ✅ 스펙 수정 | 손님앱 신규 포팅 시 참조 |
| C.1 | `flutter/driver_app/MVP/MODELS.md` | ✅ 스펙 수정 | 미래 참조용 |
| D.1 | `flutter/SERVER_TASKS.md` | ✅ CF 스펙 수정 | **실제 CF 배포는 ❌ 아직** |
| E.1(a) | `flutter/SERVER_TASKS.md` | ✅ 이미 정확 | — |
| B.2 | — | ✅ false positive | 이미 정의됨 (MODELS.md:481) |
| B.1 | `flutter/customer_app/MVP/MODELS.md` | 🟡 4/9 확인 | 나머지 5 필드 검증 대기 |
| E.1(b) | — | ❌ 미해결 | 별도 보안 설계 세션 필요 (phone-uid 매핑) |

### 다음 세션 우선순위 (Phase 6 코딩)

**"MVP 매핑 + P0 패치 완료 → 코딩 진입"이 원 계획**. 아직 착수 안 한 작업:

1. **서버측 (CF) 보강** — 배포만 하면 끝. 의제 4 apns + 의제 11 acceptanceEvents
2. **기사앱 iOS 코드 보강** — fcmTokenPlatform, Platform.isIOS 분기, 권한 문구
3. **Bundle ID 실제 변경** — Xcode + Firebase Console + GoogleService-Info.plist
4. **App Store Connect API Key** — Codemagic 서명 권한
5. **codemagic.yaml Phase B** — 서명 + TestFlight 업로드
6. **TestFlight 배포** — 기사 테스터 초대 + 실기기 파일럿
7. **손님앱 신규 포팅** — Phase 2 (기사앱 안정화 후)

상세 체크리스트: `memory/phase6_coding_remaining.md`

### 사용자 측 준비 (병렬 가능)
- 앱 아이콘 1024×1024 PNG no alpha
- 스크린샷 6.5"/5.5" iPhone
- 앱 설명 한국어 (프로모 250자 + description 4000자)
- 개인정보 정책 URL (호스팅 필요)
- 기사 테스터 5~10명 Apple ID 수집

---

## 2026-04-16 — Flutter 전환 결정 + Swift 네이티브 보류

**방향 전환** (master plan: `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md`)
- 배경: Mac 공수 1~2개월 지연 → 4/15 Swift 네이티브 계획 보류, Codemagic 기반 Flutter로 iOS 출시
- 기사앱: 기존 `driver_app_flutter/` Phase 1~5 재활용 + iOS 빌드만 추가
- 손님앱 Phase 1: Kotlin → Flutter 신규 포팅 (같은 applicationId로 Play Store 업데이트)
- 손님앱 Phase 2: Mac 도착 후 Swift App Clip 하이브리드 추가

**4/16 하루치 작업 (19 커밋, `70bd6a19`~`c290d198`)**
- 팀 구성: kotlin-expert + flutter-expert 2인 (`.agent-teams/flutter-expert.md` 신규)
- 의제 14개 중 **12개 결정 완료** — 미결: 의제 2(Bundle ID), 의제 3(Codemagic)
- MVP 매핑 문서 19개 (driver 10 + customer 9) + `SERVER_TASKS.md` + `FUNCTIONAL_INVENTORY.md`
- **마지막 커밋 `c290d198`**: `flutter/REVIEW_FINDINGS.md` — 교차 리뷰 P0 9건 (코딩 진입 전 수정 필수)

**사용자 측 Week 0 준비**
- 완료: Apple Developer + Codemagic 계정 + App Store Connect 앱 생성
- 남은 2가지: 앱 메타데이터(아이콘/스크린샷/설명/개인정보 URL), 기사 테스터 5~10명 Apple ID

**다음 스텝**: P0 9건 패치 → 의제 2/3 결정 → Codemagic 첫 iOS 빌드 → TestFlight Internal

**Swift 자산 (`ios/`)**: 폐기 아님, Flutter MVP 실패 시 복귀용 보류

---

## 2026-03-27 — Flutter 기사앱 Phase 1~5 구현 완료 (`940d5e6f`)

- Phase 1: 구조정리 (61파일 삭제) + Firestore 경로 수정 + DriverWorkflowNotifier
- Phase 2: 정산 시스템 (SettlementCalc + 일일마감 + 이월금)
- Phase 3: Presence + ForegroundService + LockScreenActivity + 주소검색
- Phase 4: FCM 6종 완전 대응 + Delivery ACK
- Phase 5: 회원가입/비밀번호찾기/콜상세/QR추천
- 빌드 환경: Flutter 3.41.6, AGP 8.7, Kotlin 2.1, Gradle 8.9
- Flutter SDK: `/c/Users/kala1/flutter_sdk/`
- 패키지명: `com.designated.driverapp.app`
- 남은 작업: Phase 6 (전환 전략 결정) + 실기기 테스트

---

## 2026-03-24 — 손님앱 콜 취소 소통 개선 완료 (`79c3860c`)

- 손님앱: ACCEPTED/PREPARING 상태에서도 취소 가능 (기존 WAITING/ASSIGNED만)
- 기사앱: cancelTrip() HOLD → CANCELLED_BY_DRIVER (확정 취소, 재배차 없음)
- 3개 앱 모두: 취소 시 일반 알림(notification bar) 표시
- 기사앱: 취소 시 Snackbar "고객이 콜을 취소했습니다" + 알림 클릭 시 홈 이동 (검은 화면 수정)
- CF: 취소 시 매니저 FCM 전송 + 기사 FCM에 title/body 추가
- Firestore 보안 규칙: 고객 취소(CANCELLED_BY_CUSTOMER) + 기사 취소(CANCELLED_BY_DRIVER) 허용 추가

**손님앱 익명인증 복원**: Phone Auth 필수 → Anonymous Auth 자동 로그인으로 변경

**배포 완료**
- CF `onCallCancelledByDriver` (title/body 추가 + 매니저 FCM)
- Firestore 보안 규칙 (고객/기사 취소 허용 확장)

---

## 2026-03-18 — 시뮬레이션 테스트 완료

- 순차 흐름 7일 335콜 + 10사무실 공유콜 127CP + 피크 부하 100건 ALL PASS
- 3단계 실기기 테스트 대부분 완료
  - ⏳ 전화감지 3대 중복검증 → 파일럿에서 검증
  - ⏳ 네트워크 오프라인 시나리오 → 파일럿 1주차 이후

---

## Firestore 상태 (2026-03-18 기준)
- provinces(16) + admins(1) + 사무실 `nEkf0X9g3LZtRX94Mrzu`
- VIP 사무실 `0evNgfgm3xdq0v3VTFYK`는 삭제됨
- 상세: `memory/firestore-incident.md`

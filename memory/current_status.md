# Current Status — 날짜별 상태 누적

> MEMORY.md에서 분리된 상세 상태 이력. 날짜 내림차순 정렬 (최신이 위).

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

# 잔여 작업

## ⭐ 현재 최우선 — Phase 6 iOS 출시 코딩 보강 (2026-04-17 진행)

**상세 체크리스트**: `memory/phase6_coding_remaining.md`

**한 줄 요약**: MVP 매핑 + P0 스펙 패치 + Codemagic 인프라 완료. **실제 iOS 대응 코드 보강 7개 항목 착수 전**.

우선순위 순서:
1. 서버측 CF 배포 (의제 4 apns + 의제 11 acceptanceEvents)
2. 기사앱 iOS 코드 보강 (fcmTokenPlatform, Platform.isIOS, 권한 문구)
3. Bundle ID 실제 변경 (Xcode + Firebase Console)
4. App Store Connect API Key + Codemagic 서명 통합
5. codemagic.yaml Phase B (서명 + TestFlight)
6. TestFlight 배포 + 기사 파일럿
7. 손님앱 Flutter 신규 포팅 (Phase 2)

---

## 기사앱 — iOS 인벤토리 작업 중 발견 (Phase 0, 2026-04-15)
| ID | 이슈 | 우선순위 | 설명 |
|----|------|---------|------|
| AND-01 | LockScreenActivity.rejectCallDirectly() dead code | 낮음 | 3/18 거절 버튼 제거 후 메서드만 잔존. 단순 cleanup. `LockScreenActivity.kt:205-247` |
| AND-02 | PendingSync 큐 라우팅 조건 불명 | **중간 (iOS 출시 전 검증 권장)** | `SettlementRepository.addPendingSync` 호출처 조건 분기 모호. 오프라인 정산이 실제로 큐잉되는지 검증 필요 |
| AND-03 | PresenceManager.onLogout 호출 누락 가능성 | **중간 (iOS 출시 전 수정 권장)** | `LoginViewModel.logout`이 `auth.signOut()`만 호출. presence cleanup 누락 시 다음 로그인 혼선 위험 |
| AND-04 | 손님앱 ACCEPTED/IN_PROGRESS FCM type 이름 미문서화 | 낮음 | CUST-03 작업이 어느 type 문자열을 쓰는지 CF 코드 재확인 + CLAUDE.md/SHARED_LOGIC.md 갱신 |
| AND-05 | Firestore runTransaction 오프라인 실패 정책 비일관 | 중간 (구조 개선) | `performFirestoreUpdate` 공통 핸들러만 try/catch, 호출 사이트별 회복 UX 없음. 견고성 ↑ 작업 |
| AND-06 | LockScreen 상태에서 취소 FCM 누락 | 기존 발견 (`.agent-teams/CROSS_VERIFICATION_LOG.md:51`) | 포그라운드는 정상, 백그라운드/LockScreen에서 누락. iOS도 동일 결함 막아야 |

→ **iOS 출시 전 AND-02, AND-03 우선 검증·수정 권장** (Android와 iOS가 같은 결함 공유 시 해결 비용 2배)

## 보안 강화 (6건) - 플레이스토어 배포 전 필수
| 이슈 | 내용 |
|------|------|
| NEW-12 | Callable Functions 14개 request.auth 검증 |
| SEC-C01 | 콜 생성 비인증 (firestore.rules) |
| SEC-C02 | customerPoints 소유권 미검증 |
| SEC-C03 | pointTransactions 위조 생성 방지 |
| SEC-C04 | customerInfo FCM 토큰 탈취 방지 |
| RTDB | read/write:true → 인증 기반 규칙 |

## 후순위 (2건)
| 이슈 | 내용 |
|------|------|
| NEW-08/10 | Crashlytics 설정 + 크래시 감지 |
| STL-10/11 | 구 정산 시스템 제거 + 마감 정리 |

## 미개발
- 픽업기사 앱 (CallStatus에 PICKUP_COMPLETE enum만 존재)
- Customer App QR코드 고객정보 수집
- ~~Driver App 부팅 자동시작~~ → BootReceiver 구현 완료 (`f5ab8ddc`), 실기기 테스트 필요

## 배포 상태
- Cloud Functions: 3/12 전체 재배포 완료 (40개 함수)
- 정산 스케줄러(`checkSettlementDiscrepanciesScheduled`) 삭제 완료
- 모든 앱 미배포 (call_detector, call_manager, driver_app, customer_app)
- 실기기 테스트 진행 중

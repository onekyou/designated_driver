# 잔여 작업 (대리운전 도메인)

> OS 레벨 미해결: `memory/callmadang_master_2026-04-27.md` §11 (가격·택시·확장·BtoG)
> OS 레벨 단기 액션: `memory/callmadang_master_2026-04-27.md` §12 + `memory/callmadang_checklist_2026-04-27.md`

---

## ⭐ 현재 최우선 — 콜마당 OS 자기추진 (마스터 §13.3 디버깅 우선순위)

대리운전 도메인 자체는 **양평 1개 사무실 파일럿 운영 중**, 안정 상태. 다음 단계는 OS 레벨:

1. **R3 식당 누름 빈도 측정** (KPI 1순위) — 첫 식당 깔린 시점부터 매일 카운트
2. **R1 인큐베이션 사무실 1곳** — 양평 사무실 5곳 중 적극·콜 부족 1곳 직접 코칭
3. **R4 직접 설치** — 식당 영업 시 사장님 폰 받아 현장 설치
4. **R2 N=1 영상 기록** — 첫 사건 즉시 타임랩스
5. **R5 정부 지원사업 신청 준비** — 6개월 시점 데이터로

대리 도메인의 6번째 앱 (업소용 앱) 개발이 R3·R4 가능 조건. 체크리스트 B-3 의 1순위.

---

## 보류 트랙 (대리 도메인 안)

| 트랙 | 위치 | 복귀 트리거 |
|------|------|-------------|
| Phase 6 iOS 출시 | `memory/designated_drive/ios_phase6_paused/` | 양평 자기추진 + R3 검증 통과 후 |
| Flutter 전환 | `memory/designated_drive/flutter_paused/` | 동일 |
| 대리 손님앱 MVP 재작성 | `memory/designated_drive/customer_app/` | 동일 (Anonymous Auth 핵심 기능 누락 감사 결과 반영 필요) |

H2 사장님 포털 / 박상준 첫 영업 (완료): `memory/designated_drive/owner_portal_completed/`

---

## 기사앱 — iOS 인벤토리 작업 중 발견 (Phase 0, 2026-04-15) — Phase 6 복귀 시 처리
| ID | 이슈 | 우선순위 | 설명 |
|----|------|---------|------|
| AND-01 | LockScreenActivity.rejectCallDirectly() dead code | 낮음 | 3/18 거절 버튼 제거 후 메서드만 잔존. 단순 cleanup. `LockScreenActivity.kt:205-247` |
| AND-02 | PendingSync 큐 라우팅 조건 불명 | **중간 (iOS 출시 전 검증 권장)** | `SettlementRepository.addPendingSync` 호출처 조건 분기 모호. 오프라인 정산이 실제로 큐잉되는지 검증 필요 |
| AND-03 | PresenceManager.onLogout 호출 누락 가능성 | **중간 (iOS 출시 전 수정 권장)** | `LoginViewModel.logout`이 `auth.signOut()`만 호출. presence cleanup 누락 시 다음 로그인 혼선 위험 |
| AND-04 | 손님앱 ACCEPTED/IN_PROGRESS FCM type 이름 미문서화 | 낮음 | CUST-03 작업이 어느 type 문자열을 쓰는지 CF 코드 재확인 |
| AND-05 | Firestore runTransaction 오프라인 실패 정책 비일관 | 중간 (구조 개선) | `performFirestoreUpdate` 공통 핸들러만 try/catch, 견고성 ↑ 작업 |
| AND-06 | LockScreen 상태에서 취소 FCM 누락 | 기존 발견 | 포그라운드는 정상, 백그라운드/LockScreen에서 누락. iOS도 동일 결함 막아야 |

→ **Phase 6 복귀 시 AND-02, AND-03 우선 검증·수정 권장**

---

## 보안 강화 (6건) — 플레이스토어 본격 배포 전 필수
| 이슈 | 내용 |
|------|------|
| NEW-12 | Callable Functions 14개 request.auth 검증 |
| SEC-C01 | 콜 생성 비인증 (firestore.rules) |
| SEC-C02 | customerPoints 소유권 미검증 |
| SEC-C03 | pointTransactions 위조 생성 방지 |
| SEC-C04 | customerInfo FCM 토큰 탈취 방지 |
| RTDB | read/write:true → 인증 기반 규칙 |

→ 양평 단일 사무실 파일럿 단계라 즉각 위험 ❌. T1 다음 사무실 진입 전 정리 권장.

---

## 후순위 (2건)
| 이슈 | 내용 |
|------|------|
| NEW-08/10 | Crashlytics 설정 + 크래시 감지 |
| STL-10/11 | 구 정산 시스템 제거 + 마감 정리 |

---

## 미개발 / 미완료
- ~~픽업기사 앱~~ → **2026-04-26 MVP 완성** (`memory/designated_drive/pickup_driver_app/`)
- Customer App QR코드 고객정보 수집 (대리 손님앱 — 보류, `customer_app/` 참조)
- ~~Driver App 부팅 자동시작~~ → BootReceiver 구현 완료 (`f5ab8ddc`), 실기기 테스트 필요

---

## 배포 상태 (대리 도메인)
- Cloud Functions: 41개 운영 중
- call_detector / call_manager / driver_app / pickup_driver_app: 양평 1개 사무실 + Z Flip4 파일럿 운영
- customer_app (Kotlin): 미배포 (Flutter 재작성 보류 자료 `customer_app/` 참조)
- Firebase Hosting `calldetector-5d61e.web.app/apk_downloads/` — 3 APK 배포 (배차 STT 메모 commit `f9d0f0d0`)

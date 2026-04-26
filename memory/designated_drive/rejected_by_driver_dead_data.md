---
name: rejected_by_driver_dead_data
description: rejectedByDriver 필드가 기사앱 3곳에서 write되지만 서버/관리자 UI 어디에서도 read되지 않는 dead data 현황과 후속 이슈 기록
type: project
---

# rejectedByDriver 필드 dead data + 재배차 UI 필터링 누락

**작성일**: 2026-04-18
**발견 경로**: Phase 6 ① 서버 CF 보강 작업 중, `flutter/SERVER_TASKS.md` B.4.1 스펙 검증 과정에서 필드 존재 여부 audit

## 현황

### Write 주체 (3곳, 정상 동작)
| 파일 | 라인 | 함수 | 트리거 |
|------|------|------|--------|
| `driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt` | 497 | `rejectCall()` | 기사앱 UI 거절 버튼 |
| `driver_app/app/src/main/java/com/designated/driverapp/LockScreenActivity.kt` | 238 | `rejectCallDirectly()` | 락스크린 알림 거절 액션 |
| `driver_app_flutter/lib/features/driver/presentation/notifiers/driver_workflow_notifier.dart` | 236 | `rejectCall()` | Flutter 기사앱 UI 거절 |

세 곳 모두 동일 패턴으로 Firestore transaction에 `rejectedByDriver: driverId` 기록 + `status: WAITING` + `assignedDriverId: null` 설정.

### Read 주체 (없음)
- `functions/src/` 런타임 코드: 0건
- `call_manager/` 전체: 0건 (`DashboardViewModel`, `CallRepository` 포함)
- `call_detector/` 전체: 0건 (`DispatchActivity` 포함)
- `functions/test/scenarios/02-reject-reassign.test.ts`: **저장 여부 assertion만** — 이후 재배차 필터링 동작은 검증하지 않음

즉 Kotlin/Flutter 양쪽 기사앱이 충실하게 필드를 기록하고 있지만 **어떤 consumer도 이 데이터를 읽지 않는 완벽한 dead data**. 테스트도 필드가 기록되는지만 체크하지 재배차 필터링 결과는 보지 않음.

## 거절 vs 타임아웃 구분 (설계는 명확)

| 경로 | 트리거 | 필드 기록 |
|------|--------|-----------|
| **명시적 거절** | 기사가 UI의 거절 버튼/락스크린 거절 액션 | `rejectedByDriver: uid` (기사앱) |
| **3분 타임아웃** (네트워크 두절, 크래시, 무반응 등) | CF `checkAssignedTimeout` line 3469~, 오프라인 3512 / 온라인 3552 | `timeoutRecoveredAt: serverTimestamp()` (CF), `rejectedByDriver` 미세팅 |

타임아웃을 `rejectedByDriver`에 포함하지 않는 건 **합리적 설계** — 네트워크가 복구됐을 수도 있는 기사를 재배차 후보에서 영구 제외하면 손해.

## 원래 설계 의도 (추정)

1. **주 용도**: 재배차 시 거절한 기사를 후보에서 제외 → 같은 기사에게 또 배차되는 실수 방지 (명시적 거절한 기사는 이 콜을 안 받겠다는 의사 표시니까)
2. **부 용도**: 거절 통계 집계 (iOS vs Android 수락률 비교 등 R1_LOCKSCREEN 발동 판단)

**실상**: 주 용도(UI 필터링) 누락, 부 용도(집계)는 Phase 6 ①에서 뒤늦게 구현 중.

## 재배차 기능 자체는 살아있음

"거절 = 콜 취소, 재배차 불가"가 아님을 확인:
- `call_manager/app/src/main/java/com/designated/callmanager/ui/dashboard/DashboardViewModel.kt:670` `assignCallToDriver(callInfo, driverId)` — 관리자 수동 배차 함수 정상 동작
- `DashboardViewModel.kt:607` `.whereIn("status", listOf("OPEN", "WAITING", "ASSIGNED", "IN_PROGRESS"))` — 거절되어 WAITING으로 돌아간 콜도 대시보드에 표시됨
- `call_manager/app/src/test/java/.../SettlementCalculatorTest.kt:142`, `TestSettlementFactory.kt:150`: "**거절→재배차→완료**" 시나리오가 정산 테스트에 포함됨

즉 "기사 A 거절 → 기사 B 재배차 → 완료" 는 코드상 정상 지원되는 플로우. 단, 재배차 UI가 `rejectedByDriver`를 참조하지 않아 **방금 거절한 기사에게 또 배차하는 실수를 막아주지 않음** (육안 확인에 의존).

## Phase 6 ①에서의 처리 (부분 해소)

- `functions/src/analytics/acceptanceEvents.ts` 신규 모듈이 `onCallStatusChanged`에서 `afterData.rejectedByDriver`를 read하여 `outcome: "rejected"` 이벤트로 기록
- 즉 **집계 소비처는 이번 세션에서 생김** → dead data 중 "부 용도"는 자연 해소
- **주 용도(UI 필터링)는 여전히 누락** — 후속 이슈로 남음

## 후속 이슈 (미해결)

### 1. 재배차 UI에서 rejectedByDriver 기반 필터링/시각 표시
- **범위**: `call_manager`(DashboardViewModel + 배차 다이얼로그 UI), `call_detector`(DispatchActivity)
- **작업 방식**:
  - 배차 다이얼로그에서 기사 목록을 보여줄 때, 해당 콜의 `rejectedByDriver`와 일치하는 기사는 회색 표시 또는 제외
  - 또는 관리자에게 "이 기사는 방금 거절함" 경고 배너 표시
- **우선순위**: 배차 실수가 자주 발생하는지 운영 데이터 관찰 후 판단. Phase 6 외 별도 트랙

### 2. 재배차 운영 관습 검토
- **질문**: 사장님이 실제로 거절된 콜을 다른 기사에게 재배차해서 성사시키는 경우가 있는가, 아니면 대부분 삭제/취소로 처리하는가?
- **판단 유보**: Phase 6 ① B 작업의 `monthlyStats` 집계가 몇 개월 쌓이면 "거절 후 재배차 성공률" 데이터로 확인 가능. 그 결과에 따라 재배차 기능 유지/정리 결정

## 관련 참조
- `flutter/SERVER_TASKS.md` B.4.1 — acceptanceEvents 기록 스펙
- `functions/test/scenarios/02-reject-reassign.test.ts` — 재배차 플로우 검증 (부분적)
- `memory/phase6_coding_remaining.md` ①번 — 이 dead data의 부분 해소 작업
- `CLAUDE.md` §상태 전이 맵 — CANCELLED_BY_DRIVER는 운행 취소(수락 후), 거절은 rejectCall (수락 전, rejectedByDriver 세팅)

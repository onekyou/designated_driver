---
name: 픽업앱 대시보드 WAITING 30분 컷 + 상태변경 시스템 알림
description: 2026-04-28 픽업앱 대시보드 콜카드 누적 fix — WAITING 30분 클라이언트 컷 + ADDED/MODIFIED status 변경 시 시스템 알림 발사. 검증 대기 (미커밋).
type: project
---

# 픽업앱 대시보드 fix (2026-04-28)

## 문제

사용자 보고: 픽업앱 대시보드에서 이전 카드들이 무한 누적. 진단 결과 `DashboardViewModel.kt` 쿼리에 시간 컷오프 없음 → 활성 status 5종에 갇힌 옛 콜이 계속 표시.

**Why:** stuck 콜(미배차 WAITING, 정산미완 AWAITING_SETTLEMENT 등)이 사무실 calls 에 잔류하면 픽업앱 상황판이 영원히 들고 있음.

**How to apply:** 향후 픽업앱 대시보드 동작 검토 시 이 fix 의 정책 그대로 유지. 사무실 전체 콜 표시는 의도된 설계 (`pickup_app_phase2_plan.md` §B3 — Phase 2 "내 담당" 탭이 별도 작업).

## 사용자가 좁힌 의도 (헌장 — 변경 금지)

- 활성 4종(ASSIGNED / ACCEPTED / IN_PROGRESS / AWAITING_SETTLEMENT)은 **시간 제약 없음** (운행 길어져도 끝까지 표시)
- WAITING 만 **30분 컷** (그 이상은 stuck)
- 사무실 전체 콜 보임 = 의도됨 (assignedDriverId 필터 X)
- 상태 변경 시 시스템 알림 = 사무실 전체 대상 (필터 없음)

## 적용된 변경 (3 파일)

| 파일 | 변경 |
|------|------|
| `pickup_driver_app/app/src/main/java/com/designated/pickupdriver/PickupDriverApplication.kt` | 알림 채널 `pickup_call_changes` 등록 (companion `CHANNEL_CALL_CHANGES`) |
| `pickup_driver_app/app/src/main/java/com/designated/pickupdriver/MainActivity.kt` | Android 13+ POST_NOTIFICATIONS 런타임 권한 요청 (`registerForActivityResult`) |
| `pickup_driver_app/app/src/main/java/com/designated/pickupdriver/ui/dashboard/DashboardViewModel.kt` | (1) WAITING 30분 `.filter` (companion `WAITING_CUTOFF_MS = 30 * 60 * 1000L`)<br>(2) `@ApplicationContext context` Hilt 주입<br>(3) `previousStatuses` 캐시 + `initialSnapshotProcessed` 플래그<br>(4) `handleStatusChangeNotifications(documentChanges)` — ADDED + status 변경 MODIFIED 만 발사, REMOVED 무시<br>(5) `notifyChange(callId, title, body)` — `NotificationManagerCompat.from(context).notify(callId.hashCode(), ...)` |

## 알림 정책

- ADDED → "신규 콜: 출발지 → 도착지" (단 첫 스냅샷 무시)
- MODIFIED 중 status 변경 → "[상태라벨]: 출발지 → 도착지"
- MODIFIED 중 status 외 변경 → 무시
- REMOVED → 무시 (이미 MODIFIED 단계에서 발사됨)
- 알림 ID = `callId.hashCode()` → 같은 콜 후속 변경은 update 형태로 갱신 (스팸 방지)

## 알려진 한계

- listener 구독 중 timestamp 가 고정 — 화면 켠 채로 30분 지나도 WAITING 카드 자동 제거 X. **앱 재진입 시점에만 컷** 적용. 필요 시 timer 재구독 별도 작업.
- 앱 백그라운드/종료 상태에선 listener 미동작 → 알림 안 옴. **앱 켜진 상태 한정 알림.** 백그라운드 알림은 Phase 2 §B1 (FCM) 별도 작업.
- 사무실 전체 콜 알림 → 픽업기사 무관 변경도 울림. 시끄러우면 추후 status 화이트리스트 또는 본인 관련 필터 추가.

## 빌드/설치 상태 (2026-04-28)

- ✅ Kotlin 컴파일 성공 (`compileDebugKotlin`)
- ✅ APK 빌드 성공 (`assembleDebug`)
- ✅ Z Flip4 (R3CT80K78NP) 설치 성공
- ✅ S21+ (R3CR312MB1L) 설치 성공 (사용자가 검증 위해 추가 요청)
- ⏳ **미커밋** — 검증 대기 중
- ⏳ 검증 시나리오 5종 (A~E) 사용자 직접 실행 필요

## 검증 시나리오 (사용자 실행)

```bash
$ANDROID_HOME/platform-tools/adb -s <serial> logcat -s PickupDashboardVM
```

- A: WAITING 30분+ stuck 콜 안 보임 / 활성 4종은 시간 무관 표시
- B: 신규 콜 생성 → 시스템 트레이 알림
- C: 상태 변경(ASSIGNED→ACCEPTED 등) 단계마다 알림
- D: 카드 5장 상태로 앱 재진입 → 한꺼번에 안 울림 (첫 스냅샷 침묵)
- E: 권한 거부 시 앱 정상 동작 (알림만 X)

전 시나리오 통과 시 단일 commit:
```
feat(pickup): WAITING 30m cutoff + status change notifications
```

## 보류

- 앱 백그라운드 상태 알림 → Phase 2 §B1 (`MyFirebaseMessagingService`) + §A4 (CF `onPickupAssignmentCreated`)
- WAITING 30분 자동 사라짐 (timer 재구독)
- stuck 콜 백엔드 일괄 정리 스크립트
- 알림 빈도 조정 (사무실 전체 → 픽업 관련만)

## Plan 파일

`C:\Users\kala1\.claude\plans\velvety-kindling-bumblebee.md`

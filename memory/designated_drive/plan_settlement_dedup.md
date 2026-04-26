---
name: 콜매니저 정산 중복 표시 + 미지급금 불일치 수정 계획
description: 1차 마감 기사 재로그인 시 정산 분리 — per-driver 필터 + filteredTrips 완료, 실시간 갱신 + 퇴근 후 UI 미수정
type: project
---

# 콜매니저 정산 중복/미지급금 버그 수정 (2026-03-13~14)

## 문제
정산 완료(CONFIRMED)된 기사가 재로그인 후 새 운행 시:
1. 콜매니저 기사별 섹터에서 이전 정산 콜 + 새 콜이 합산 표시
2. 미지급금(carryOver)/이체 금액에 분리가 적용되지 않음

## 근본 원인
- **콜매니저**: `offices/{o}.settlementLastCleared` (전체 마감 시에만 갱신)로 필터
- **기사앱**: `designated_drivers/{driverId}.settlementLastCleared` (기사 퇴근 시 갱신)로 필터
- 콜매니저가 기사별 마감 시점을 참조하지 않아 이전 콜이 포함됨

## 설계 원칙 (사용자 확인)
- **Room DB 삭제 금지**: 1차 콜은 Room DB에 유지. UI 계산 단에서 필터링
- **filteredTrips 방식**: `driverLastClearedMap`으로 UI에서만 필터링, 최종 업로드(clearAllTrips)는 전체 포함
- **isIntegration 변경 불필요**: 기사앱 submitDailySettlement의 통합 로직은 현재 그대로 유지

## 완료된 수정 ✅

### 1. driverLastClearedMap → StateFlow 노출
- `SettlementViewModel.kt`: `private var` → `MutableStateFlow` + public `StateFlow`
- 참조 4곳 `.value` 접근으로 변경 (line 306, 336, 442, 500)

### 2. filteredTrips 도입 — 미지급금/이체 계산 분리
- `DriverSummaryScreen.kt`: `filteredTrips = trips.filter { completedAt > driverLastClearedMap[driverId] }`
- 모든 계산(todayUnpaidByDriver, driverStats, displayedUnpaid 등) 6곳에서 `trips` → `filteredTrips` 교체

### 3. fetchCompletedCalls — per-driver Firestore 필터
- `designated_drivers` 컬렉션에서 기사별 `settlementLastCleared` 조회 → `driverLastClearedMap`
- 콜 필터링 시 `max(office-level, driver-level)` 기준 적용
- 실시간 리스너(`startCallsListener`)에도 동일 필터 적용

### 4. createSettlementSessionFromFirestore — Firestore 직접 조회 폴백
- 최종 마감 시 세션 없으면 Room DB 대신 Firestore `calls` 직접 조회

### 5. SETTLEMENT_SUBMITTED FCM — data-only 변경
- CF `onDriverSettlementSubmitted` + `SETTLEMENT_FINALIZED` 배포 완료

## 미완료 수정 ❌

### 1. driverLastClearedMap 실시간 갱신
- **문제**: `loadSettlementData()` 시 1회만 로드. 기사 퇴근 후 갱신 안 됨 → 이전 콜 잔존
- **수정**: `startCarryOverListener` (이미 designated_drivers 실시간 리스너)에서 `settlementLastCleared`도 읽어 `_driverLastClearedMap` 갱신
- **파일**: `SettlementViewModel.kt`

### 2. 퇴근 후 불필요한 UI 섹션 표시 방지
- **문제**: 기사 퇴근 → `dailySettlement` 삭제 → `hasSubmitted=false` → 미지급금/예상납입금 섹션 재표시 (-20,000원 등)
- **수정**: filteredTrips가 0건인 기사는 미지급/예상납입금 섹션 숨김
- **파일**: `DriverSummaryScreen.kt`

## 코드 시뮬레이션 결과 (3/14 검증 완료)
- ratio=60%, 초기 carryOver=30,000, 1차 3건(60,000원) + 2차 2건(40,000원)
- 이월금 이체 후 confirmReceiveCarryOver → balance=0 ✅
- 2차 운행 시 filteredTrips로 2건만 계산 ✅
- 2차 submitDailySettlement: originalCarryOver=0, 2건 기준 ✅
- 최종 clearAllTrips: CONFIRMED skip → carryOver 이중 처리 방지 ✅
- 세션 업로드: CF 독립적으로 전체 5건 기록 ✅

## 수정 파일 종합
| 파일 | 변경 | 상태 |
|------|------|------|
| `call_manager/.../SettlementViewModel.kt` | per-driver 필터 + StateFlow + Firestore 폴백 | ✅ 완료 |
| `call_manager/.../screen/DriverSummaryScreen.kt` | filteredTrips 도입 + 계산 교체 | ✅ 완료 |
| `call_manager/.../SettlementViewModel.kt` | startCarryOverListener에서 driverLastClearedMap 실시간 갱신 | ❌ 미완료 |
| `call_manager/.../screen/DriverSummaryScreen.kt` | 퇴근 후 빈 기사 UI 정리 | ❌ 미완료 |
| `functions/src/index.ts` | SETTLEMENT_SUBMITTED data-only | ✅ 배포완료 |
| `functions/src/handlers/settlement.ts` | SETTLEMENT_FINALIZED data-only | ✅ 배포완료 |

**Why:** 1차 마감 기사 재로그인 시 콜 표시/미지급금/이체가 2차분만 기준으로 동작해야 함. Room DB는 건드리지 않고 UI 계산 단에서 필터링.
**How to apply:** 미완료 2건(실시간 갱신 + 퇴근 후 UI)을 이 파일 참조하여 수정 진행.

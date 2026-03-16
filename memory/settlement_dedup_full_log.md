---
name: 정산 중복/분리 수정 전체 대화 기록
description: 2026-03-13~14 정산 중복 표시 + 미지급금 분리 작업의 전체 대화 흐름, 수정 내역, 시뮬레이션 결과, 설계 결론
type: project
---

# 정산 중복/분리 수정 — 전체 대화 및 수정 기록 (2026-03-13~14)

---

## 1. 최초 문제 보고

**증상**: 정산 완료(CONFIRMED)된 기사가 퇴근 후 재로그인하여 새 운행 시:
- 콜매니저 기사별 섹터에서 이전 1차 정산 콜 + 새 2차 콜이 합산 표시
- 미지급금(carryOver) 계산 오류 → 이체 시 정산 불일치

**근본 원인**:
- **콜매니저**: `offices/{o}.settlementLastCleared` (전체 마감 시에만 갱신)로 필터
- **기사앱**: `designated_drivers/{driverId}.settlementLastCleared` (기사 퇴근 시 갱신)로 필터
- 콜매니저가 기사별 마감 시점을 참조하지 않아 이전 콜이 포함됨

---

## 2. 초기 잘못된 접근 — Room DB 삭제

**최초 제안**: `SettlementDao.deleteByDriverBefore(driverId, timestamp)` 추가하여 Room DB에서 1차 콜 삭제

**사용자 피드백**: "room db를 삭제한다는건 말이 안돼. 기본적으로 정산시스템을 파악하지 못하고 있어."
- 콜매니저와 기사앱이 같은 Firestore 문서를 다운로드해서 사용
- 최종 업무마감 시 콜매니저가 전체를 업로드
- Room DB를 삭제하면 최종 업로드 데이터가 불완전해짐

**교훈**: Room DB는 건드리지 않고, UI 계산 단에서 필터링해야 함

---

## 3. 올바른 접근 — filteredTrips 도입

**사용자 설명**: "현재는 1차콜을 db에서 지우는게 아니라 정산이 맞쳤음을 알리고 이후 운행한 콜에 대해 정산하도록 해놨어. 문제는 그것을 미지급이나 이체금액에 적용되지 않았다는거야. 아주 심플한거야."

**핵심 이해**:
- per-driver Firestore 필터로 콜 **표시** 분리는 이미 완료됨 (fetchCompletedCallsWithDriverFilter)
- 하지만 미지급금/이체 계산은 여전히 Room DB 전체(`_settlementList`) 기준
- `filteredTrips`를 도입하여 UI 계산에만 적용하면 됨

---

## 4. 적용된 수정 내역

### 수정 A: driverLastClearedMap → StateFlow 노출 (SettlementViewModel.kt)

**변경 전**:
```kotlin
private var driverLastClearedMap: Map<String, Long> = emptyMap()
```

**변경 후**:
```kotlin
private val _driverLastClearedMap = MutableStateFlow<Map<String, Long>>(emptyMap())
val driverLastClearedMap: StateFlow<Map<String, Long>> = _driverLastClearedMap.asStateFlow()
```

참조 4곳을 `_driverLastClearedMap.value`로 변경 (line 306, 336, 442, 500)

### 수정 B: filteredTrips 도입 (DriverSummaryScreen.kt)

**추가된 코드**:
```kotlin
val driverLastClearedMap by vm.driverLastClearedMap.collectAsState()

val filteredTrips = remember(trips, driverLastClearedMap) {
    trips.filter { trip ->
        val driverCleared = driverLastClearedMap[trip.driverId] ?: 0L
        trip.completedAt > driverCleared
    }
}
```

**교체된 곳** (6곳, `trips` → `filteredTrips`):
1. `todayUnpaidByDriver` 계산 (line 64)
2. `driverStats` 계산 (line 86)
3. `totalFare` 합계 (line 110)
4. `totalCredit` 합계 (line 111)
5. `driversWithTrips` 집합 (line 128)
6. 기사 클릭 시 상세 목록 (line 176)

### 수정 C: driverLastClearedMap 실시간 갱신 (SettlementViewModel.kt, 3/14)

**문제**: `driverLastClearedMap`이 `loadSettlementData()` 시 1회만 로드. 기사 퇴근 후 갱신 안 됨.

**수정**: `startCarryOverListener` (이미 `designated_drivers` 컬렉션 실시간 리스너)에서 `settlementLastCleared`도 읽기:

```kotlin
// startCarryOverListener 내부 — 추가된 코드
val updatedClearedMap = mutableMapOf<String, Long>()

snapshots?.documents?.forEach { doc ->
    // ... 기존 코드 ...

    // 추가: settlementLastCleared 실시간 갱신
    val clearedTs = doc.getTimestamp("settlementLastCleared")?.toDate()?.time ?: 0L
    updatedClearedMap[doc.id] = clearedTs

    // ... 기존 carryOver/dailySettlement 파싱 ...
}

// 추가: 루프 종료 후 갱신
_driverLastClearedMap.value = updatedClearedMap
```

**효과**: 기사 퇴근 즉시 → 리스너 발동 → driverLastClearedMap 갱신 → filteredTrips 재계산 → 이전 콜 자동 제외 + 해당 기사 driverStats에서 제외 → 불필요한 UI 섹션(미지급금/예상납입금) 자동 숨김

---

## 5. 코드 시뮬레이션 결과 (3/14)

### 설정
- ratio = 60%, Driver 양세훈 (driverId="d1")
- 초기 carryOver = {balance: 30,000, status: PENDING}
- 1차 3건 (총 60,000원, 현금), 2차 2건 (총 40,000원, 현금+이체)

### 검증 항목별 결과

| 항목 | 결과 |
|------|------|
| 이월금 이체 후 confirmReceiveCarryOver → balance=0 | ✅ 감소 확인 |
| 2차 운행 시 filteredTrips → 2건만 UI/계산에 사용 | ✅ |
| 2차 submitDailySettlement: originalCarryOver=0, 2건 기준 | ✅ |
| 최종 clearAllTrips: CONFIRMED skip → carryOver 이중 처리 방지 | ✅ |
| 세션 업로드: CF 독립적으로 전체 5건 기록 | ✅ |
| UI와 업로드 데이터 일치 | ✅ (filteredTrips는 UI용, 세션은 전체) |

### 발견된 부수 이슈
- **UI 초기 깜빡임**: `_settlementList`(Room DB Flow)는 즉시 5건 emit, `driverLastClearedMap`은 비동기 로드 → 순간 5건 후 2건으로 전환
- → 수정 C(실시간 갱신)로 대부분 해소. 기능 오류 아닌 UX 이슈.

---

## 6. 실기기 테스트 중 발견된 문제 (3/14)

**증상**: 기사가 2,500원 운행(이체) 후 퇴근 → 콜매니저 정산 기사별에 "예상납입금 -20,000원" 표시

**원인 분석**:
1. `clearSettlement()` → `dailySettlement` 삭제 → `hasSubmitted=false`
2. line 425: `if (!hasSubmitted || isConfirmed || isRejected)` → `!false = true` → 미지급금 섹션 표시
3. 예상납입금 = `deposit - carryOverBalance - totalCredit` → carryOver가 남아있으면 음수 가능
4. 근본 원인: `driverLastClearedMap`이 갱신 안 되어 퇴근한 기사의 이전 콜이 `filteredTrips`에 잔존

**해결**: 수정 C(driverLastClearedMap 실시간 갱신)로 해결
- 퇴근 즉시 filteredTrips = 0건 → driverStats에서 제외 → DriverDetailCard 미렌더링

---

## 7. 이상적인 정산 플로우 설계 (3/14)

### 사용자 질문
"운행을 마친기사가 매니저의 부탁으로 다시 운행을 했을때 가장 이상적인 정산방법"

### 결론: 현재 코드로 충분히 지원됨

**플로우**:
```
1. 기사 1차 운행 → submit → 매니저 confirm → 이체 → 수령 확인
2. 기사 퇴근 → settlementLastCleared = now
3. 콜매니저: driverLastClearedMap 실시간 갱신 → filteredTrips 0건 → 기사 카드 숨김
4. 기사 재로그인 → 2차 운행
5. 콜매니저: filteredTrips = 2차 콜만 → 미지급금/이체 2차분만 계산
6. 기사 2차 submit → 매니저 2차 confirm
7. 최종 clearAllTrips → Room DB 전체(1차+2차) 업로드
```

**핵심**: 1차와 2차가 `driverLastClearedMap` 기준으로 UI에서 자동 분리되고, 최종 업로드는 전체 포함.

### isIntegration(기사앱 통합 제출)은 별도 케이스
- 매니저가 1차 확인을 보류(PENDING_CONFIRM 유지) → 기사 퇴근 안 하고 추가 운행 → 2차 제출 시 `isIntegration=true` → 자동 합산
- 이미 코드가 지원하므로 수정 불필요

---

## 8. 사용자 피드백 요약

| 피드백 | 교훈 |
|--------|------|
| "room db를 삭제한다는건 말이 안돼" | Room DB는 최종 업로드용. UI 필터링으로 해결 |
| "기본적으로 정산시스템을 파악하지 못하고 있어" | 콜매니저+기사앱의 데이터 흐름(다운로드→로컬사용→업로드)을 먼저 이해할 것 |
| "이부분확인한거야? 단순히 내말만 듣고 추측하는거야?" | 코드를 직접 Read/Grep으로 확인한 후에만 수정 제안 |
| "아주 심플한거야" | 복잡한 접근 대신 핵심(filteredTrips)에 집중 |

---

## 9. 수정 파일 종합

| 파일 | 변경 | 상태 |
|------|------|------|
| `call_manager/.../SettlementViewModel.kt` | driverLastClearedMap → MutableStateFlow | ✅ 완료, 미커밋 |
| `call_manager/.../SettlementViewModel.kt` | per-driver Firestore 필터 (fetchCompletedCallsWithDriverFilter) | ✅ 완료, 미커밋 |
| `call_manager/.../SettlementViewModel.kt` | startCarryOverListener에서 driverLastClearedMap 실시간 갱신 | ✅ 완료, 미커밋 |
| `call_manager/.../SettlementViewModel.kt` | createSettlementSessionFromFirestore 폴백 | ✅ 완료, 미커밋 |
| `call_manager/.../screen/DriverSummaryScreen.kt` | filteredTrips 도입 + 6곳 교체 | ✅ 완료, 미커밋 |
| `functions/src/index.ts` | SETTLEMENT_SUBMITTED data-only | ✅ 배포완료 |
| `functions/src/handlers/settlement.ts` | SETTLEMENT_FINALIZED data-only | ✅ 배포완료 |

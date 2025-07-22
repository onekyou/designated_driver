# Firestore 읽기 최적화 리포트 (2024-06)

이 문서는 최근 **콜매니저·기사·콜디텍터** 앱에서 감지된 과도한 Firestore 읽기 패턴을 정리하고, 최소화 방안을 제안합니다. 총 6개의 주요 위치를 확인했습니다.

| # | 파일 & 라인 | 현재 쿼리 | 문제점 | 최적화 제안 |
|---|-------------|-----------|----------|-------------|
| 1 | `call_manager/ui/dashboard/DashboardViewModel.kt` 209·297·311 | `calls.orderBy("timestamp").limit(100)` 실시간 리스너 | ‑ `whereEqualTo()` 필터 없음 → **모든 상태** 호출 100개씩 수신<br/>- 사무실당 호출이 많아질수록 읽기 증가 | • `whereEqualTo("status", WAITING/IN_PROGRESS…)` 등 **상태별 필터**<br/>• 완료·취소 등 불필요 상태는 클라이언트 캐시로만 유지 |
| 2 | `call_manager/ui/settlement/SettlementViewModel.kt` 235 | `settlements.whereEqualTo("settlementStatus", PENDING)` 실시간 리스너 | 인덱스 없이 `orderBy()` 사용 → 인덱스 오류(현재 제거됨) | • Cloud Console 에 복합 인덱스 생성 후 `orderBy(createdAt)` + `limit` 적용<br/>• PENDING 건수 많을 때 **paging** 필요 |
| 3 | `call_manager/ui/settlement/SettlementViewModel.kt` 293 | 전체 `settlements` 리스너(필터 없음) | 사무실 운행 누적될수록 읽기 폭증 | • 최근 **N 일** 또는 **limit(N)** 로 슬라이싱<br/>• 구간 선택형 페이징(날짜 범위) |
| 4 | `call_manager/ui/drivermanagement/DriverManagementViewModel.kt` 37 | `pending_drivers.whereEqualTo(...).orderBy("requestedAt")` *(get)* | GET 단건이지만 `limit` 없음 → 대량 신청 시 읽기 증가 | • `limit(50)` + 클라이언트 **load more** |
| 5 | `driver_app/viewmodel/DriverViewModel.kt` 267 | `completedCalls.orderBy("timestamp")` 실시간 리스너 | 완료 호출이 늘어날수록 드라이버 앱 지속 읽기 | • 완료 후 24h 지나면 **listener 제거**<br/>• 또는 `startAfter(lastKnown)` 방식 페이징 |
| 6 | `driver_app/service/DriverForegroundService.kt` 129 | `calls.orderBy("assignedTimestamp")` 리스너 | 동일 문제, 필요 이상 과도 수신 | • `whereEqualTo("driverId", myId)` 등 필터 적용<br/>• 현행 limit(??) 확인 후 보강 |

## 공통 가이드라인
1. **필터 먼저, 정렬(orderBy)·limit 나중**
2. 대량 데이터는 **addSnapshotListener → paging(or get + pagination token)** 전환
3. 완료·취소 등 장기 데이터는 **Cloud Function** 으로 BigQuery/Storage 보관 후 메인 컬렉션 분리(아카이빙)
4. 실시간 리스너는 필요 시점에만 연결, 화면 전환 시 **remove()** 호출 필수
5. Cloud Console의 **Usage** → “Total document reads” 지표 지속 모니터링

---
> 위 최적화로 예상 읽기량을 **30~60% 절감**할 수 있습니다. 단계별로 적용 후 GA4 + Firebase Usage 에서 수치를 확인해 주세요. 
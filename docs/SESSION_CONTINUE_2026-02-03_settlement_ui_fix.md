# 정산 UI 수정 - 세션 이어가기 문서

## 날짜: 2026-02-03

---

## 작업 배경

기사앱에서는 미수령금 UI가 정상 표시되지만, 콜매니저에서는 미지급금 UI가 표시되지 않는 문제.

### 핵심 원칙
1. 마감 전: 로컬에서 실시간 계산하여 UI 표시
2. 마감 시: 최종 계산 결과를 Firestore에 업데이트
3. 다음 날: Firestore에서 이월분 읽기 + 오늘 로컬 계산

---

## 완료된 수정 사항

### 1. 작업 경로 변경
- 기존: `C:/app_dev/designated_driver` (손상 가능성)
- 변경: `D:/designated_driver` (외장 드라이브, 새로 clone)

### 2. AllTripsScreen.kt (전체 탭) 수정
**경로**: `call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/AllTripsScreen.kt`

**변경 내용**:
- 기사별 오늘 미지급금 계산 로직 추가 (trips 기반)
- `DriverUnpaidSummary` 데이터 클래스 추가
- 이월분 + 오늘분 합산하여 표시
- 테이블 항상 표시 (조건 제거)
- 컬럼: 기사 / 이월 / 오늘 / 합계

### 3. DriverSummaryScreen.kt (기사별 탭) 수정
**경로**: `call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/DriverSummaryScreen.kt`

**변경 내용**:
- `todayUnpaidByDriver` 계산 로직 추가
- `DriverStat` 데이터 클래스에 `driverId` 필드 추가
- `DriverDetailCard`에 `todayUnpaid` 파라미터 추가
- 미지급 섹션 항상 표시
- 이월분 + 오늘분 합산하여 표시
- 이체하기 버튼 조건 변경: `totalUnpaid > 0`일 때 표시

---

## 미해결 문제

### 이체하기 버튼 활성화 안 됨
**현상**: 미지급금이 있어도 이체하기 버튼이 보이지 않거나 활성화 안 됨

**추정 원인**:
1. `totalUnpaid` 계산이 `Long` 타입인데 비교가 `> 0`로 되어 있음
2. `stat.driverId`가 빈 문자열일 수 있음
3. 버튼이 렌더링되지만 크기가 0이거나 보이지 않는 상태

**확인 필요 사항**:
```kotlin
// 현재 조건
if (totalUnpaid > 0) {
    val status = carryOver?.status ?: CarryOverStatus.PENDING
    when (status) {
        CarryOverStatus.PENDING -> {
            Button(...) // 이체하기
        }
        ...
    }
}
```

**디버깅 방안**:
1. `totalUnpaid` 값 로그 출력
2. `stat.driverId` 값 확인
3. 버튼 대신 Text로 테스트

---

## 오늘 미지급금 계산 로직

```kotlin
val todayUnpaidByDriver = remember(trips, ratio) {
    trips.groupBy { it.driverId }
        .filter { it.key.isNotBlank() }
        .mapValues { (_, driverTrips) ->
            val fareSum = driverTrips.sumOf { it.fare }
            val cashReceived = driverTrips.sumOf { trip ->
                when {
                    trip.paymentMethod == "현금" -> trip.fare
                    trip.paymentMethod.startsWith("현금+") -> trip.cashAmount ?: 0
                    else -> 0
                }
            }
            val driverShare = (fareSum * (100 - ratio) / 100.0).toInt()
            val realDeposit = cashReceived - driverShare
            // 음수면 미지급금 발생
            if (realDeposit < 0) -realDeposit else 0
        }
}
```

---

## 다음 세션 TODO

1. **이체하기 버튼 활성화 문제 해결**
   - 로그 추가하여 값 확인
   - 조건 검증

2. **테스트**
   - 외상 운행 후 미지급금 표시 확인
   - 이체하기 버튼 동작 확인
   - 마감 후 이월분 표시 확인

3. **커밋 & 푸시**
   - 수정 완료 후 원격에 백업

---

## 관련 파일

- `D:/designated_driver/call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/AllTripsScreen.kt`
- `D:/designated_driver/call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/DriverSummaryScreen.kt`
- `D:/designated_driver/call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/SettlementViewModel.kt`

---

## 빌드 상태

- 콜매니저: BUILD SUCCESSFUL (설치됨, 버튼 문제 있음)
- 기사앱: 수정 안 함

---

## 테스트 기기

| 기기 | 모델 | 설치된 앱 |
|------|------|----------|
| RF9R5013HEK | SM_A325N | 콜매니저 |
| R3CR312MB1L | SM_G996N | 기사앱 |

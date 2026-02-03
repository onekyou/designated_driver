# 기사앱 수정 디버깅 문서

## 날짜: 2026-02-03
## 세션 목적: 기사앱 정산 데이터 실시간 반영 및 콜매니저와 일치

---

## 1. 발생한 문제들 (해결되지 않음)

### 문제 1: tripHistory가 모든 과거 운행내역 표시
- **예상 동작**: 마감(settlementLastCleared) 이후 당일 운행내역만 표시
- **실제 동작**: 마감 후 다시 로그인해도 모든 과거 운행내역이 표시됨
- **관련 파일**: `HistorySettlementScreen.kt`

### 문제 2: 누적 미수령금 실시간 반영 안됨
- **예상 동작**: 외상 운행 완료 시 즉시 "누적 미수령금" 카드에 반영
- **실제 동작**: 운행 완료 후에도 미수령금이 업데이트되지 않음
- **관련 파일**: `DriverViewModel.kt`, `HistorySettlementScreen.kt`

### 문제 3: 마감 시 기사 알림 안됨
- **예상 동작**: 콜매니저에서 마감 시 기사앱에 푸시 알림 전송
- **실제 동작**: 알림이 오지 않음
- **관련 파일**: `functions/src/index.ts` (sendDriverNotification)

---

## 2. 수정된 내용 상세

### 2.1 커밋된 변경사항 (1d4f09fe)

#### functions/src/index.ts - sendDriverNotification 함수 추가
```typescript
export const sendDriverNotification = onCall(
  { region: "asia-northeast3" },
  async (request) => {
    const { driverId, provinceId, cityId, officeId, type, title, body } = request.data;
    // FCM 토큰 조회 및 알림 전송
  }
);
```
- **목적**: 마감 시 기사에게 푸시 알림 전송
- **상태**: 배포됨 (firebase deploy --only functions)

#### firestore.rules - 불필요한 규칙 삭제
- `drivers` 컬렉션 규칙 삭제 (designated_drivers만 사용)

---

### 2.2 커밋되지 않은 변경사항

#### DriverViewModel.kt 변경사항

**변경 1: lastClearedMillis StateFlow 추가 (라인 85-87)**
```kotlin
// 마지막 마감 시점 (최초 1회 로드, tripHistory 필터링용)
private val _lastClearedMillis = MutableStateFlow(0L)
val lastClearedMillis: StateFlow<Long> = _lastClearedMillis.asStateFlow()
```

**변경 2: loadSettlementData에서 lastClearedMillis 설정 (라인 1243)**
```kotlin
val lastClearedMillis = officeDoc.getTimestamp("settlementLastCleared")?.toDate()?.time ?: 0L
_lastClearedMillis.value = lastClearedMillis  // ← 추가됨
```

**변경 3: confirmAndFinalizeTrip에서 로컬 즉시 업데이트 (라인 643-666)**
```kotlin
// 기존: refreshSettlementData() 호출
// 변경: 로컬 StateFlow 직접 업데이트

val ratio = _depositRatio.value
val newCashReceived = when {
    paymentMethod == "현금" -> fareToSet
    paymentMethod.startsWith("현금+") -> cashAmount ?: 0
    else -> 0
}
val newDriverShare = (fareToSet * (100 - ratio) / 100.0).toInt()

_todaySettlement.update { current ->
    val updatedCashReceived = current.cashReceived + newCashReceived
    val updatedDriverShare = current.driverShare + newDriverShare
    current.copy(
        totalFare = current.totalFare + fareToSet,
        driverShare = updatedDriverShare,
        cashReceived = updatedCashReceived,
        realDeposit = updatedCashReceived - updatedDriverShare,
        tripCount = current.tripCount + 1
    )
}
```

**변경 4: driverShare 계산 로직 수정 (라인 1306)**
```kotlin
// 기존: val driverShare = (totalFare * (100 - ratio) / 100)
// 변경: val driverShare = (totalFare * (100 - ratio) / 100.0).toInt()
```
- **목적**: 콜매니저와 동일한 Double 나눗셈 사용

---

#### HistorySettlementScreen.kt 변경사항

**변경 1: loadTripHistory 함수 시그니처 변경**
```kotlin
// 기존
fun loadTripHistory(context: Context): List<String>

// 변경
fun loadTripHistory(context: Context, lastCleared: Long): List<String>
```

**변경 2: 필터링 로직 변경**
```kotlin
// 기존: 5일 기준 필터링
val fiveDaysMillis = 5 * 24 * 60 * 60 * 1000L
if (timestamp > 0L && now - timestamp <= fiveDaysMillis) { ... }

// 변경: 마감 시점 기준 필터링
if (timestamp > lastCleared) { ... }
```

**변경 3: lastClearedMillis StateFlow 구독 추가**
```kotlin
val lastClearedMillis by viewModel.lastClearedMillis.collectAsStateWithLifecycle()
```

**변경 4: LaunchedEffect로 tripHistory 재로드**
```kotlin
LaunchedEffect(lastClearedMillis) {
    tripHistory = loadTripHistory(context, lastClearedMillis)
}
```

**변경 5: 누적 미수령금 계산 로직 추가**
```kotlin
// 오늘 미지급금 계산
val todayUnpaid = if (realDeposit < 0) -realDeposit else 0
// 누적 미수령금 = 이전 잔액 + 오늘 미지급금
val totalUnpaid = (carryOver?.balance?.toInt() ?: 0) + todayUnpaid

// 기존: if (carryOver != null && carryOver!!.balance > 0)
// 변경: if (totalUnpaid > 0)
```

---

## 3. 의심되는 원인

### 원인 1: lastClearedMillis 초기값 문제
- `lastClearedMillis` StateFlow 초기값이 `0L`
- UI 렌더링 시점에 아직 `loadSettlementData()`가 호출되지 않았을 수 있음
- `timestamp > 0L`이면 모든 데이터가 필터링 통과

**검증 방법:**
```kotlin
// HistorySettlementScreen.kt에서 로그 추가
Log.d("Settlement", "lastClearedMillis: $lastClearedMillis")
Log.d("Settlement", "tripHistory size: ${tripHistory.size}")
```

### 원인 2: loadSettlementData 호출 시점
- `initializeListenersWithInfo()` 내에서 호출됨
- 기사 정보 로드 후에 호출되는데, 순서가 보장되는지 확인 필요

**확인 경로:**
1. `DriverViewModel.kt` → `initializeListenersWithInfo()`
2. `loadSettlementData()` 호출 위치 확인

### 원인 3: tripHistory timestamp 저장 방식
- SharedPreferences의 `history_list`에 `|timestamp=` 형식으로 저장
- 저장 시점의 timestamp가 올바른지 확인 필요

**확인 방법:**
```bash
adb shell "cat /data/data/com.designated.driverapp/shared_prefs/trip_history.xml"
```

### 원인 4: carryOver 로드 시점
- `carryOver`가 언제 로드되는지 확인
- `_carryOver` StateFlow가 실시간으로 업데이트되는지 확인

### 원인 5: sendDriverNotification 호출 여부
- 콜매니저에서 마감 시 이 함수를 실제로 호출하는지 확인
- Cloud Functions 로그에서 호출 기록 확인

**확인 명령:**
```bash
firebase functions:log --only sendDriverNotification
```

---

## 4. 롤백 방법

### 커밋되지 않은 변경사항 롤백
```bash
cd D:/designated_driver
git checkout -- driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt
git checkout -- driver_app/app/src/main/java/com/designated/driverapp/ui/home/HistorySettlementScreen.kt
```

### 롤백 후 재빌드
```bash
cd D:/designated_driver/driver_app
./gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. 다음 세션에서 확인할 사항

### 5.1 로그 확인
1. DriverViewModel에서 `loadSettlementData` 호출 여부 확인
2. `_lastClearedMillis.value` 설정 시점 로그
3. HistorySettlementScreen에서 `lastClearedMillis` 값 로그

### 5.2 데이터 흐름 확인
```
앱 시작
  ↓
initializeListenersWithInfo() 호출?
  ↓
loadSettlementData() 호출?
  ↓
_lastClearedMillis.value 설정?
  ↓
HistorySettlementScreen에서 collectAsStateWithLifecycle?
  ↓
LaunchedEffect(lastClearedMillis) 트리거?
  ↓
tripHistory 필터링?
```

### 5.3 SharedPreferences 데이터 확인
```bash
# trip_history 내용 확인
adb shell "run-as com.designated.driverapp cat /data/data/com.designated.driverapp/shared_prefs/trip_history.xml"
```

### 5.4 Cloud Functions 로그 확인
```bash
# sendDriverNotification 호출 기록
firebase functions:log --only sendDriverNotification | Select-Object -Last 50
```

---

## 6. 핵심 원칙 (재확인)

```
앱 시작 → 최초 1회 Firestore 로드 (calls, carryOver 등)
    ↓
로컬에서 계산/업데이트 (todaySettlement, todayUnpaid 등)
    ↓
마감 시 콜매니저가 carryOver 업로드
    ↓
다음날 다시 1회 로드 → 반복
```

- **동일한 소스(calls)** → **동일한 계산 로직** → **동일한 결과**
- 콜매니저와 기사앱이 불일치 없이 동일한 값 표시

---

## 7. 관련 파일 목록

| 파일 | 역할 | 수정 상태 |
|------|------|----------|
| `DriverViewModel.kt` | 정산 로직, StateFlow | 미커밋 |
| `HistorySettlementScreen.kt` | 정산 UI | 미커밋 |
| `functions/src/index.ts` | sendDriverNotification | 커밋됨 |
| `firestore.rules` | 권한 규칙 | 커밋됨 |
| `SettlementViewModel.kt` (콜매니저) | 비교 참조용 | - |

---

## 8. Git 상태

```bash
# 현재 브랜치
firestore-migration-backup

# 최근 커밋
1d4f09fe - fix: 정산 CarryOver 컬렉션 불일치 문제 해결

# 미커밋 변경사항
driver_app/.../DriverViewModel.kt
driver_app/.../HistorySettlementScreen.kt
.claude/settings.local.json
```

# 테스트 코드 정리 가이드

## 삭제해도 앱에 영향 없는 파일 (순수 테스트)

### call_manager
```
app/src/test/java/com/designated/callmanager/settlement/
  ├── SettlementCalculatorTest.kt      ← JVM 단위 테스트
  └── TestSettlementFactory.kt         ← 테스트 데이터 팩토리

app/src/androidTest/java/com/designated/callmanager/settlement/
  └── SettlementConsistencyTest.kt     ← Firestore 연동 테스트
```

### driver_app
```
app/src/test/java/com/designated/driverapp/settlement/
  └── SettlementCalcTest.kt            ← JVM 단위 테스트

app/src/androidTest/java/com/designated/driverapp/settlement/
  └── DriverSettlementConsistencyTest.kt ← Firestore 연동 테스트
```

### 삭제 명령
```bash
# call_manager 테스트 전체 삭제
rm -rf call_manager/app/src/test/
rm -rf call_manager/app/src/androidTest/

# driver_app 테스트 전체 삭제
rm -rf driver_app/app/src/test/
rm -rf driver_app/app/src/androidTest/
```

---

## 삭제하면 안 되는 파일 (프로덕션 코드)

이 파일들은 테스트를 위해 만들었지만, 현재 실제 화면 코드에서 사용 중.
삭제하면 빌드 에러 발생.

| 파일 | 이유 |
|------|------|
| `call_manager/.../SettlementCalculator.kt` | DriverSummaryScreen, AllTripsScreen에서 호출 |
| `driver_app/.../SettlementCalc.kt` | HistorySettlementScreen에서 호출 |

---

## 프로덕션 코드에 추가된 testTag (삭제 선택)

testTag는 앱 동작에 영향 없음 (릴리즈 빌드에서도 무해).
깔끔하게 제거하고 싶다면 아래 파일에서 `.testTag(...)` 부분만 삭제.

### DriverSummaryScreen.kt
```
Modifier.testTag("settlement_driver_name_${stat.driverId}")
Modifier.testTag("settlement_driver_count_${stat.driverId}")
Modifier.testTag("settlement_driver_totalFare_${stat.driverId}")
Modifier.testTag("settlement_driver_deposit_${stat.driverId}")
Modifier.testTag("settlement_driver_totalCredit_${stat.driverId}")
Modifier.testTag("settlement_driver_carryOver_${stat.driverId}")  ← 2곳
```
+ `import androidx.compose.ui.platform.testTag` 제거

### AllTripsScreen.kt
```
Modifier.testTag("settlement_all_totalFare")
Modifier.testTag("settlement_all_officeIncome")
Modifier.fillMaxWidth().testTag("settlement_all_creditSum")
Modifier.fillMaxWidth().testTag("settlement_all_realIncome")
```
+ `import androidx.compose.ui.platform.testTag` 제거

### HistorySettlementScreen.kt
```
Modifier.testTag("settlement_today_count")
Modifier.testTag("settlement_today_totalFare")
Modifier.testTag("settlement_today_officeDeposit")
Modifier.testTag("settlement_today_cashReceived")
Modifier.testTag("settlement_today_totalCredit")
Modifier.testTag("settlement_today_displayDeposit")
```
+ `import androidx.compose.ui.platform.testTag` 제거

---

## customer_app 테스트용 임시 변경 (원복 필수)

### 1. 초기화 흐름 전체 교체 (`MainActivity.kt` LaunchedEffect 블록)
테스트용 코드(익명인증+MAIN 직행)를 삭제하고, 주석 처리된 원본 코드의 주석을 해제.
구체적으로:
- `// 테스트용: 익명인증으로 바로 MAIN 진입` 블록 삭제
- `/* 원본 코드 ... */` 주석 해제

### 2. VIP 사무실 하드코딩 (`MainActivity.kt` LaunchedEffect 상단)
아래 블록 전체 삭제:
```kotlin
if (preferencesManager.getOfficeId() == null) {
    preferencesManager.saveOfficeInfo(
        officeId = "nEkf0X9g3LZtRX94Mrzu",
        provinceId = "gyeonggi",
        cityId = "yangpyeong"
    )
    android.util.Log.d("TestMode", "테스트용 VIP 사무실 하드코딩 적용")
}
```

### 3. 더미 customerInfo (`MainActivity.kt` 테스트 블록 내)
아래 블록 삭제:
```kotlin
customerInfo = com.designated.customer.data.model.CustomerInfo(
    id = auth.currentUser?.uid ?: "test_user",
    phoneNumber = "01000000000",
    name = "테스트고객",
    linkedOfficeId = "nEkf0X9g3LZtRX94Mrzu"
)
```

### 4. 익명인증 메서드 (`PhoneAuthViewModel.kt` 하단)
`signInAnonymously()` 메서드 전체 삭제

---

## 완전 원복하려면 (SettlementCalculator/SettlementCalc도 삭제)

1. 테스트 파일 삭제 (위 명령)
2. testTag 제거 (위 목록)
3. git에서 3개 화면 파일을 커밋 `5f87dd8c` 이전으로 되돌리기:
```bash
git checkout 3f707105 -- \
  call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/DriverSummaryScreen.kt \
  call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/AllTripsScreen.kt \
  driver_app/app/src/main/java/com/designated/driverapp/ui/home/HistorySettlementScreen.kt
```
4. SettlementCalculator.kt, SettlementCalc.kt 삭제

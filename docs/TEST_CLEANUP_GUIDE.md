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

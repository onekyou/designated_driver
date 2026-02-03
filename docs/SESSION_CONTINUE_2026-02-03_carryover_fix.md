# 정산 CarryOver 수정 - 세션 완료 문서

## 날짜: 2026-02-03

---

## 완료된 수정 사항

### 1. 컬렉션 불일치 문제 해결 (핵심)
**문제**: SettlementViewModel만 `drivers` 컬렉션 사용, 나머지는 `designated_drivers` 사용
**해결**: SettlementViewModel.kt에서 `drivers` → `designated_drivers` 변경 (4곳)

```kotlin
// 수정된 경로
.collection("designated_drivers").document(driverId)
```

**영향받은 함수:**
- `startCarryOverListener` (1170라인)
- `transferCarryOver` (1228라인)
- `cancelTransfer` (1345라인)
- `processCarryOverOnFinalize` (1386라인)

### 2. Firestore 보안 규칙 추가
**파일**: `D:/designated_driver/firestore.rules`

```javascript
match /drivers/{driverDocId} {
  allow read: if isOfficeAdmin(...) || (isAuthenticated() && resource.data.authUid == request.auth.uid);
  allow write: if isOfficeAdmin(...) || (isAuthenticated() && resource.data.authUid == request.auth.uid);
}
```
※ 참고: 컬렉션을 designated_drivers로 변경했으므로 이 규칙은 불필요할 수 있음

### 3. transferCarryOver 함수 개선
**변경 내용:**
- `update()` → `set(merge=true)` 변경 (마감 전에도 carryOver 생성 가능)
- 파라미터 추가: `driverName`, `carryOverBalance`, `todayUnpaid`
- driverRef.get() 조회 제거 (퍼미션 오류 해결)

```kotlin
fun transferCarryOver(
    driverId: String,
    driverName: String,
    carryOverBalance: Long,
    todayUnpaid: Long,
    onResult: (Boolean, String) -> Unit
)
```

### 4. 로컬 carryOverList 즉시 업데이트
**추가된 함수**: `updateLocalCarryOver()`
- 이체/취소 시 Firestore 리스너 대기 없이 로컬 UI 즉시 업데이트

### 5. processCarryOverOnFinalize 개선
**변경 내용**: TRANSFERRED 상태 유지
- 마감 시 이미 이체된 상태면 status를 TRANSFERRED로 유지
- 기사가 수령완료 눌러야 SETTLED로 변경

### 6. 디버그 코드 제거
- DriverSummaryScreen.kt에서 디버그 텍스트 제거
- Toast 메시지 정리

---

## 테스트 결과

| 항목 | 상태 |
|------|------|
| 콜매니저 이체하기 버튼 | ✅ 작동 |
| 이체 후 이체취소 버튼 변환 | ✅ 작동 |
| 기사앱 carryOver 로드 | ✅ 작동 |
| 기사앱 수령완료 버튼 | ✅ 작동 |
| 콜매니저 재로그인 시 기사이름 | ✅ 정상 표시 |

---

## 남은 문제 / TODO

### 1. FCM 알림 함수 없음
**현상**: 이체 시 기사앱에 푸시 알림 안 감
**원인**: `sendDriverNotification` Cloud Function이 존재하지 않음
**현재 대안**: 기사앱 Firestore 리스너가 실시간 감지 (앱이 열려있으면 작동)
**해결 방안**: Cloud Function 생성 필요 (선택사항)

```kotlin
// 현재 호출하는 함수 (NOT_FOUND 오류)
functions.getHttpsCallable("sendDriverNotification")
```

### 2. 로그 메시지 하드코딩
**파일**: `DriverViewModel.kt:366`
```kotlin
Log.d(TAG, "🔵 Driver Ref 경로: .../drivers/$driverId")
```
실제 경로는 designated_drivers를 사용하지만 로그 메시지에 "drivers"가 하드코딩되어 있음 (기능에는 영향 없음)

### 3. 불필요한 보안 규칙 정리 (선택)
`drivers` 컬렉션 규칙을 추가했으나, `designated_drivers`로 변경 후 불필요할 수 있음

---

## 수정된 파일 목록

1. `D:/designated_driver/call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/SettlementViewModel.kt`
2. `D:/designated_driver/call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/DriverSummaryScreen.kt`
3. `D:/designated_driver/firestore.rules`

---

## 흐름 정리

### 마감 전 이체 흐름
```
1. 외상 운행 발생 → 로컬에서 todayUnpaid 계산
2. 이체하기 클릭 → designated_drivers/{driverId}/carryOver 저장 (status: TRANSFERRED)
3. 기사앱 리스너 감지 → 수령완료 버튼 표시
4. 수령완료 클릭 → status: SETTLED, balance: 0
```

### 마감 시 흐름
```
1. 마감 버튼 클릭 → processCarryOverOnFinalize 호출
2. 기존 balance + 오늘 결과 계산
3. TRANSFERRED 상태면 유지, 아니면 PENDING/SETTLED
4. designated_drivers/{driverId}/carryOver 업데이트
```

---

## 관련 파일 경로

- **콜매니저 ViewModel**: `D:/designated_driver/call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/SettlementViewModel.kt`
- **콜매니저 UI**: `D:/designated_driver/call_manager/app/src/main/java/com/designated/callmanager/ui/settlement/screen/DriverSummaryScreen.kt`
- **기사앱 ViewModel**: `D:/designated_driver/driver_app/app/src/main/java/com/designated/driverapp/viewmodel/DriverViewModel.kt`
- **보안 규칙**: `D:/designated_driver/firestore.rules`
- **Cloud Functions**: `D:/designated_driver/functions/lib/index.js`

---

## 테스트 기기

| 기기 | 모델 | 설치된 앱 |
|------|------|----------|
| RF9R5013HEK | SM_A325N | 콜매니저 |
| R3CR312MB1L | SM_G996N | 기사앱 |

---

## 빌드 상태

- 콜매니저: BUILD SUCCESSFUL ✅
- 기사앱: BUILD SUCCESSFUL ✅
- Firestore Rules: 배포 완료 ✅

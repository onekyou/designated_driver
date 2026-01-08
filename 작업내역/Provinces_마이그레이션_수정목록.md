# Provinces/Cities 구조 전환 - 전체 수정 목록

## 📊 수정 통계
- **Kotlin 파일**: 19개 (총 90곳 수정)
- **SharedPreferences**: 5개 파일 (9곳 수정)
- **Cloud Functions**: 1개 파일 (20+ 곳 수정)
- **총 작업량**: 약 120곳 수정 필요

---

## 🎯 Phase 1: 핵심 인프라 (필수 우선 수정)

### 1.1 Repository Layer (3개 파일, 8곳)
- ✅ **CallRepository.kt** (2곳)
  - Line 72: refreshData()
  - Line 222: assignCall()

- ✅ **DriverRepository.kt** (2곳)
  - Line 64: refreshData()
  - Line 157: updateDriverStatus()

- ⚠️ **PointRepository.kt** (4곳)
  - Line 87, 157, 197, 258
  - 포인트 시스템도 provinces/cities 구조 사용

### 1.2 인증/로그인 (2개 파일, 4곳)
- 🔴 **LoginViewModel.kt** (1곳 + SharedPreferences 저장)
  - Line 113: putString("regionId") → **provinceId, cityId 저장**
  - Line 171: collection("regions")

- ⚠️ **SignUpViewModel.kt** (3곳)
  - Line 97, 171, 253
  - 회원가입 시 사무실 조회

### 1.3 메인 화면 (2개 파일, 19곳)
- 🔴 **DashboardViewModel.kt** (18곳 + SharedPreferences)
  - Line 234: getString("regionId") → **provinceId, cityId 읽기**
  - Line 1179: putString("regionId") → **provinceId, cityId 저장**
  - Line 252, 534, 574, 593, 603, 629, 657, 692, 789, 817, 850, 871, 959, 1060, 1286, 1391, 1466, 1532

- ⚠️ **DashboardScreen.kt** (1곳)
  - Line 1653: 공유콜 정보 조회

### 1.4 FCM 서비스 (2개 파일, 6곳)
- 🔴 **MyFirebaseMessagingService.kt** (1곳 + SharedPreferences)
  - Line 184: getString("regionId") → **provinceId, cityId 읽기**
  - Line 240: collection("regions")

- 🔴 **CallDetectorService.kt** (5곳 + SharedPreferences 4곳)
  - Line 129, 167, 305: getString("regionId") → **provinceId, cityId 읽기**
  - Line 771: putString("regionId") → **provinceId, cityId 저장**
  - Line 331, 527, 572, 589, 795: collection("regions")

---

## 🎯 Phase 2: 관리 기능 (중요도 중)

### 2.1 기사 관리 (2개 파일, 9곳)
- ⚠️ **DriverManagementViewModel.kt** (3곳)
  - Line 31, 54, 95

- ⚠️ **PendingDriversViewModel.kt** (6곳)
  - Line 175, 239, 291, 312, 366, 408
  - 승인 대기 기사 처리

### 2.2 정산 시스템 (1개 파일, 5곳 + SharedPreferences)
- ⚠️ **SettlementViewModel.kt** (5곳 + SharedPreferences)
  - Line 134: getString("regionId") → **provinceId, cityId 읽기**
  - Line 215, 233, 321, 374, 538

### 2.3 설정 (1개 파일, 1곳)
- ⚠️ **SettingsViewModel.kt** (1곳)
  - Line 41

### 2.4 어트리뷰션 (1개 파일, 4곳)
- ⚠️ **AttributionManagementViewModel.kt** (4곳)
  - Line 153, 169, 231, 265

---

## 🎯 Phase 3: 서비스 레이어 (백그라운드 기능)

### 3.1 고객 서비스 (1개 파일, 14곳)
- ⚠️ **CustomerService.kt** (14곳)
  - Line 33, 43, 71, 132, 149, 182, 201, 240, 257, 331, 349, 400, 433, 455

### 3.2 알림 서비스 (1개 파일, 8곳)
- ⚠️ **BasicNotificationService.kt** (8곳)
  - Line 41, 90, 133, 183, 209, 255, 281, 310

### 3.3 KPI 서비스 (1개 파일, 3곳)
- ⚠️ **BasicKPIService.kt** (3곳)
  - Line 97, 135, 187

### 3.4 어트리뷰션 읽기 서비스 (1개 파일, 7곳)
- ⚠️ **ReadOnlyAttributionService.kt** (7곳)
  - Line 30, 87, 132, 187, 231, 262, 300

### 3.5 메인 액티비티 (1개 파일, 2곳)
- ⚠️ **MainActivity.kt** (2곳)
  - Line 962, 996
  - FCM 토큰 업데이트

---

## 🎯 Phase 4: Cloud Functions (Firebase)

### 4.1 Functions 경로 수정 (1개 파일, 20+곳)
- 🔴 **functions/src/index.ts** (전체 수정)
  - **Firestore Triggers 경로 변경** (13곳):
    ```typescript
    // 기존
    document: "regions/{regionId}/offices/{officeId}/calls/{callId}"

    // 신규
    document: "provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/{callId}"
    ```

  - **Firestore 쿼리 수정** (20+곳):
    ```typescript
    // 기존
    .collection("regions").doc(regionId)

    // 신규
    .collection("provinces").doc(provinceId)
      .collection("cities").doc(cityId)
    ```

---

## 🔑 주요 변경사항

### SharedPreferences 구조 변경
```kotlin
// ❌ 기존
sharedPreferences.edit().apply {
    putString("regionId", "Hongchon")  // 또는 "yangpyong"
    putString("officeId", officeId)
    apply()
}

// ✅ 신규
sharedPreferences.edit().apply {
    putString("provinceId", "gangwon")   // 또는 "gyeonggi"
    putString("cityId", "hongchon")      // 또는 "yangpyong"
    putString("officeId", officeId)
    apply()
}
```

### Firestore 경로 변경
```kotlin
// ❌ 기존
firestore.collection("regions").document(regionId)
    .collection("offices").document(officeId)
    .collection("calls")

// ✅ 신규
firestore.collection("provinces").document(provinceId)
    .collection("cities").document(cityId)
    .collection("offices").document(officeId)
    .collection("calls")
```

### Region ID 매핑 (참고용)
```
"Hongchon" or "hongchon" → provinceId: "gangwon", cityId: "hongchon"
"yangpyong" or "Yangpyong" → provinceId: "gyeonggi", cityId: "yangpyong"
```

---

## 📝 작업 체크리스트

### Phase 1: 핵심 인프라 ✅
- [ ] CallRepository.kt
- [ ] DriverRepository.kt
- [ ] PointRepository.kt
- [ ] LoginViewModel.kt (+ SharedPreferences)
- [ ] DashboardViewModel.kt (+ SharedPreferences)
- [ ] DashboardScreen.kt
- [ ] MyFirebaseMessagingService.kt (+ SharedPreferences)
- [ ] CallDetectorService.kt (+ SharedPreferences)

### Phase 2: 관리 기능
- [ ] DriverManagementViewModel.kt
- [ ] PendingDriversViewModel.kt
- [ ] SettlementViewModel.kt (+ SharedPreferences)
- [ ] SettingsViewModel.kt
- [ ] AttributionManagementViewModel.kt

### Phase 3: 서비스 레이어
- [ ] CustomerService.kt
- [ ] BasicNotificationService.kt
- [ ] BasicKPIService.kt
- [ ] ReadOnlyAttributionService.kt
- [ ] MainActivity.kt

### Phase 4: Cloud Functions
- [ ] functions/src/index.ts (전체)

### Phase 5: 검증 및 정리
- [ ] 테스트 기기 앱 재설치 (SharedPreferences 초기화)
- [ ] 로그인 테스트 (provinceId, cityId 저장 확인)
- [ ] 콜 생성/배차 테스트
- [ ] FCM 알림 테스트
- [ ] Cloud Functions 로그 확인
- [ ] Firestore regions 컬렉션 삭제

---

## ⚠️ 주의사항

1. **일괄 치환 금지**: `regionId` 변수명은 내부 로직에서 그대로 사용 가능 (SharedPreferences와 Firestore 경로만 변경)
2. **Cloud Functions 먼저 배포**: 앱 업데이트 전에 Functions 먼저 배포해야 FCM 작동
3. **테스트 필수**: 각 Phase 완료 후 해당 기능 테스트
4. **백업 확인**: 현재 call_manager_backup 폴더에 리스너 구조 백업 보관 중

---

## 🚀 작업 순서 (추천)

1. **Phase 1 완료** → 앱 빌드 테스트
2. **Phase 4 (Cloud Functions)** → Firebase 배포
3. **Phase 2, 3 완료** → 전체 기능 테스트
4. **regions 컬렉션 삭제** → 최종 검증

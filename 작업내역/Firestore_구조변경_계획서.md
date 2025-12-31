# Firestore 구조 변경 계획서
**작성일**: 2025-12-31
**목적**: 전국 서비스 확장을 위한 도/특별시/광역시 단위 계층 구조 도입

---

## 1. 현황 분석

### 1-1. 현재 Firestore 구조
```
regions/  (지역 - 홍천군, 양평군 등)
  ├─ {regionId}/  (예: Hongchon, yangpyong)
  │   ├─ name: "홍천군"
  │   └─ offices/
  │       └─ {officeId}/
  │           ├─ name: "사무실명"
  │           ├─ phone: "010-xxxx-xxxx"
  │           ├─ calls/
  │           ├─ designated_drivers/
  │           ├─ customers/
  │           └─ ...
```

**문제점**:
- ❌ 새로운 시/군/구 추가 시 regions 문서를 수동으로 생성해야 함
- ❌ 도/특별시/광역시 개념이 없어 전국 확장 불가
- ❌ 회원가입 UI에서 "경기도 → 남양주시" 같은 계층적 선택 불가능

---

### 1-2. regions 컬렉션 사용 현황

#### 📱 **손님앱 (customer_app)** - 40+ 개 파일
**주요 사용처**:
1. **OfficeSelectionScreen.kt**: 사무실 선택 시 regions 조회
2. **MainActivity.kt**: 프로필 저장 경로 `regions/{regionId}/offices/{officeId}/customers`
3. **ProfileSetupViewModel.kt**: 프로필 생성 경로
4. **CallService.kt**: 콜 요청 경로 `regions/{regionId}/offices/{officeId}/calls`
5. **PointService.kt**: 포인트 관련 경로
6. **BannerAdService.kt**: 배너 광고 경로

**경로 패턴**:
```kotlin
firestore.collection("regions").document(regionId)
    .collection("offices").document(officeId)
    .collection("customers|calls|points|...")
```

---

#### 🔔 **콜매니저 (call_manager)** - 80+ 개 파일
**주요 사용처**:
1. **SignUpViewModel.kt** (Line 97, 171-173):
   - 지역 목록 조회: `db.collection("regions").get()`
   - 사무실 생성: `db.collection("regions").document(regionId).collection("offices").document()`

2. **LoginViewModel.kt**: 로그인 후 FCM 토큰 저장
3. **DashboardViewModel.kt**: 콜 관리, 기사 관리 모든 경로
4. **CallDetectorService.kt**: 콜 자동 감지 및 저장
5. **CustomerService.kt**: 고객 관리
6. **BasicKPIService.kt**: 통계 조회

**경로 패턴**: 손님앱과 동일

---

#### 🚗 **기사앱 (driver_app)** - 15+ 개 파일
**주요 사용처**:
1. **SignUpViewModel.kt** (Line 71, 93):
   - 지역 목록 조회: `firestore.collection("regions").get()`
   - 사무실 목록 조회: `firestore.collection("regions").document(regionId).collection("offices").get()`
   - pending_drivers 생성: `firestore.collection("pending_drivers").document(userId)`

2. **DriverViewModel.kt**: 기사 상태 관리 경로
3. **DriverForegroundService.kt**: 실시간 콜 수신 경로
4. **Constants.kt** (Line 5): `COLLECTION_REGIONS = "regions"`

**경로 패턴**: 손님앱/콜매니저와 동일

---

#### ☁️ **Cloud Functions** - 59개 사용처
**주요 파일**:
1. **index.ts**: 51개 사용
2. **handlers/points.ts**: 4개 사용
3. **finalizeWorkDay.ts**: 2개 사용
4. **pttSignaling.ts**: 2개 사용

**경로 패턴**:
```typescript
admin.firestore().collection('regions').doc(regionId)
    .collection('offices').doc(officeId)
```

---

#### 🔒 **Firestore Rules**
**현재 규칙** (Line 41-47):
```
match /regions/{regionId} {
  // 회원가입 시 지역/사무실 목록 조회 허용
  allow read: if true;
  allow write: if request.auth == null;  // Cloud Functions만
}

match /regions/{regionId}/offices/{officeId} {
  allow read: if true;  // 회원가입용
  allow write: if isOfficeAdmin(regionId, officeId);
}
```

---

## 2. 새로운 Firestore 구조

### 2-1. 제안하는 구조
```
provinces/  (도/특별시/광역시)
  ├─ {provinceId}/  (예: gangwon, gyeonggi, busan 등)
  │   ├─ name: "강원특별자치도"
  │   ├─ type: "province" | "metropolitan" | "special"
  │   └─ cities/  (시/군/구)
  │       └─ {cityId}/  (예: hongchon, namyangju, haeundae 등)
  │           ├─ name: "홍천군"
  │           ├─ type: "si" | "gun" | "gu"
  │           └─ offices/
  │               └─ {officeId}/
  │                   ├─ name: "사무실명"
  │                   ├─ phone: "010-xxxx-xxxx"
  │                   ├─ calls/
  │                   ├─ designated_drivers/
  │                   ├─ customers/
  │                   └─ ...
```

### 2-2. 전국 도/특별시/광역시 목록 (서울 제외)

**특별시/광역시 (7개)**:
- `busan`: 부산광역시
- `daegu`: 대구광역시
- `incheon`: 인천광역시
- `gwangju`: 광주광역시
- `daejeon`: 대전광역시
- `ulsan`: 울산광역시
- `sejong`: 세종특별자치시

**도 (9개)**:
- `gyeonggi`: 경기도
- `gangwon`: 강원특별자치도
- `chungbuk`: 충청북도
- `chungnam`: 충청남도
- `jeonbuk`: 전북특별자치도
- `jeonnam`: 전라남도
- `gyeongbuk`: 경상북도
- `gyeongnam`: 경상남도
- `jeju`: 제주특별자치도

---

## 3. 경로 변경 맵핑

### 3-1. 기존 경로 → 새 경로 변환

| 구분 | 기존 경로 | 새 경로 |
|------|-----------|---------|
| 지역 목록 | `regions/` | `provinces/` |
| 사무실 목록 | `regions/{regionId}/offices/` | `provinces/{provinceId}/cities/{cityId}/offices/` |
| 콜 정보 | `regions/{regionId}/offices/{officeId}/calls/` | `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/calls/` |
| 기사 정보 | `regions/{regionId}/offices/{officeId}/designated_drivers/` | `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/designated_drivers/` |
| 고객 정보 | `regions/{regionId}/offices/{officeId}/customers/` | `provinces/{provinceId}/cities/{cityId}/offices/{officeId}/customers/` |

### 3-2. 기존 데이터 매핑

| 기존 regionId | provinceId | cityId | 도/시명 | 시/군/구명 |
|---------------|------------|--------|---------|------------|
| `Hongchon` | `gangwon` | `hongchon` | 강원특별자치도 | 홍천군 |
| `yangpyong` | `gyeonggi` | `yangpyong` | 경기도 | 양평군 |

---

## 4. 수정 대상 및 작업 순서

### 4-1. 데이터 마이그레이션 (최우선)
**파일**: `functions/migrate_to_provinces.js` (신규 작성)

**작업 내용**:
1. provinces 컬렉션 생성
2. cities 서브컬렉션 생성
3. 기존 regions 데이터를 새 구조로 복사
   - `regions/Hongchon/` → `provinces/gangwon/cities/hongchon/`
   - `regions/yangpyong/` → `provinces/gyeonggi/cities/yangpyong/`
4. admins 컬렉션 업데이트
   - `associatedRegionId` → `associatedProvinceId`, `associatedCityId` 추가
5. pending_drivers 업데이트
   - `targetRegionId` → `targetProvinceId`, `targetCityId` 추가

**실행 시점**: 앱 수정 전 먼저 실행

---

### 4-2. Firestore Rules 수정
**파일**: `firestore.rules`

**변경 내용**:
```
// 기존
match /regions/{regionId} {
  allow read: if true;
  allow write: if request.auth == null;
}

match /regions/{regionId}/offices/{officeId} {
  allow read: if true;
  allow write: if isOfficeAdmin(regionId, officeId);
}

// 신규
match /provinces/{provinceId} {
  allow read: if true;
  allow write: if request.auth == null;
}

match /provinces/{provinceId}/cities/{cityId} {
  allow read: if true;
  allow write: if request.auth == null;
}

match /provinces/{provinceId}/cities/{cityId}/offices/{officeId} {
  allow read: if true;
  allow write: if isOfficeAdmin(provinceId, cityId, officeId);
}
```

**헬퍼 함수 수정**:
```javascript
function isOfficeAdmin(provinceId, cityId, officeId) {
  let adminPath = /databases/$(database)/documents/admins/$(request.auth.uid);
  return isAuthenticated()
         && exists(adminPath)
         && get(adminPath).data.associatedProvinceId == provinceId
         && get(adminPath).data.associatedCityId == cityId
         && get(adminPath).data.associatedOfficeId == officeId;
}
```

---

### 4-3. 손님앱 수정
**수정 파일 수**: 약 10개

**주요 파일**:
1. **OfficeSelectionScreen.kt & OfficeSelectionViewModel.kt**
   - 3단계 선택 UI 구현:
     1. Dropdown: 도/특별시 선택
     2. TabRow: 시/군/구 선택
     3. LazyColumn: 사무실 목록

2. **MainActivity.kt**
   - 경로 변경: `regions/{regionId}` → `provinces/{provinceId}/cities/{cityId}`

3. **ProfileSetupViewModel.kt**
   - 프로필 생성 경로 변경

4. **PreferencesManager.kt**
   - SharedPreferences 저장 항목 추가:
     ```kotlin
     fun saveOfficeInfo(provinceId, cityId, officeId)
     fun getProvinceId(): String?
     fun getCityId(): String?
     ```

5. **CallService.kt, PointService.kt, BannerAdService.kt** 등
   - 모든 Firestore 경로를 새 구조로 변경

---

### 4-4. 콜매니저 수정
**수정 파일 수**: 약 20개

**주요 파일**:
1. **SignUpViewModel.kt**
   - 지역 목록 조회: `provinces/` 컬렉션
   - 시/군/구 목록 조회: `provinces/{provinceId}/cities/`
   - 사무실 생성 경로 변경

2. **LoginViewModel.kt**
   - FCM 토큰 저장 경로 변경

3. **DashboardViewModel.kt**
   - 모든 Firestore 쿼리 경로 변경 (약 50+ 곳)

4. **CallDetectorService.kt**
   - 콜 감지 및 저장 경로 변경

5. **CustomerService.kt, BasicKPIService.kt** 등
   - 고객/통계 조회 경로 변경

---

### 4-5. 기사앱 수정
**수정 파일 수**: 약 8개

**주요 파일**:
1. **SignUpViewModel.kt**
   - 도/시 선택 UI 추가
   - pending_drivers 생성 시 `provinceId`, `cityId` 저장

2. **Constants.kt**
   ```kotlin
   const val COLLECTION_PROVINCES = "provinces"
   const val COLLECTION_CITIES = "cities"
   const val COLLECTION_OFFICES = "offices"
   ```

3. **DriverViewModel.kt, DriverForegroundService.kt**
   - 기사 상태 관리 경로 변경

4. **SharedPreferences**
   - `provinceId`, `cityId` 저장 추가

---

### 4-6. Cloud Functions 수정
**수정 파일 수**: 약 4개

**주요 파일**:
1. **index.ts** (51개 사용처)
   - 모든 `collection('regions')` → `collection('provinces')`
   - 경로 변경: `regions/{regionId}/offices/` → `provinces/{provinceId}/cities/{cityId}/offices/`

2. **handlers/points.ts** (4개 사용처)
   - 포인트 관련 경로 변경

3. **finalizeWorkDay.ts, pttSignaling.ts**
   - 각 2개씩 사용처 변경

---

## 5. 작업 단계별 상세 계획

### Phase 1: 준비 및 데이터 마이그레이션 (1일)
1. ✅ **마이그레이션 스크립트 작성**
   - `functions/migrate_to_provinces.js` 작성
   - 전체 데이터 백업

2. ✅ **테스트 환경 마이그레이션**
   - 스크립트 실행 및 검증
   - 데이터 무결성 확인

3. ✅ **Firestore Rules 배포**
   - 새 구조 규칙 추가 (기존 규칙 유지)
   - 점진적 전환 지원

---

### Phase 2: 백엔드 수정 (1일)
1. ✅ **Cloud Functions 수정**
   - index.ts 경로 변경
   - handlers/points.ts 수정
   - 기타 Function 수정

2. ✅ **Functions 배포 및 테스트**
   - 개발 환경 배포
   - API 엔드포인트 테스트

---

### Phase 3: 손님앱 수정 (1일)
1. ✅ **OfficeSelectionScreen 3단계 UI 구현**
   - Province Dropdown
   - City TabRow
   - Office LazyColumn

2. ✅ **경로 변경**
   - MainActivity, ProfileSetupViewModel 등
   - CallService, PointService 등

3. ✅ **테스트**
   - Debug APK 빌드
   - 전체 기능 테스트

---

### Phase 4: 콜매니저 수정 (1일)
1. ✅ **SignUpViewModel UI 수정**
   - Province/City 선택 추가

2. ✅ **경로 변경**
   - DashboardViewModel (대량 수정)
   - CallDetectorService
   - CustomerService 등

3. ✅ **테스트**
   - Debug APK 빌드
   - 전체 기능 테스트

---

### Phase 5: 기사앱 수정 (1일)
1. ✅ **SignUpViewModel 수정**
   - Province/City 선택 UI

2. ✅ **경로 변경**
   - DriverViewModel
   - DriverForegroundService

3. ✅ **테스트**
   - Debug APK 빌드
   - 전체 기능 테스트

---

### Phase 6: 통합 테스트 및 배포 (1일)
1. ✅ **통합 테스트**
   - 손님앱 ↔ 콜매니저 ↔ 기사앱 연동 테스트
   - 콜 생성 → 배차 → 완료 전체 플로우 테스트

2. ✅ **프로덕션 마이그레이션**
   - 실제 데이터베이스 마이그레이션
   - 모니터링

3. ✅ **앱 배포**
   - 손님앱, 콜매니저, 기사앱 동시 배포

---

## 6. 위험 요소 및 대응 방안

### 6-1. 위험 요소
1. ❌ **데이터 손실**: 마이그레이션 중 오류
2. ❌ **다운타임**: 서비스 중단
3. ❌ **경로 불일치**: 일부 경로 수정 누락
4. ❌ **기존 앱 호환성**: 구버전 앱 사용자

### 6-2. 대응 방안
1. ✅ **백업**: 마이그레이션 전 전체 Firestore 백업
2. ✅ **점진적 전환**:
   - 기존 regions 유지하면서 새 provinces 병행 운영
   - Firestore Rules에서 두 경로 모두 허용
3. ✅ **롤백 계획**:
   - 문제 발생 시 기존 regions로 즉시 복원
4. ✅ **모니터링**:
   - Firebase Console 실시간 모니터링
   - Cloud Functions 로그 확인

---

## 7. 테스트 체크리스트

### 7-1. 손님앱
- [ ] 사무실 선택: 도 → 시/군/구 → 사무실 선택 가능
- [ ] 프로필 생성: 정상 저장 및 조회
- [ ] 콜 요청: 정상 생성
- [ ] 포인트 적립/사용: 정상 동작
- [ ] FCM 알림 수신: 정상 동작

### 7-2. 콜매니저
- [ ] 회원가입: 도/시/사무실 선택 후 가입 가능
- [ ] 콜 감지: 전화 수신 시 자동 Firebase 저장
- [ ] 콜 배차: 기사 배정 정상 동작
- [ ] 고객 관리: 목록 조회 및 수정
- [ ] 통계: KPI 정상 조회

### 7-3. 기사앱
- [ ] 회원가입: 도/시/사무실 선택 후 승인 요청
- [ ] 콜 수신: 배정된 콜 실시간 수신
- [ ] 운행 시작/완료: 상태 변경 정상 동작
- [ ] 정산: 정산 내역 조회

### 7-4. Cloud Functions
- [ ] FCM 알림 발송: 정상 동작
- [ ] 포인트 정산: 정상 동작
- [ ] 통계 집계: 정상 동작

---

## 8. 예상 작업 시간

| 단계 | 작업 내용 | 예상 시간 |
|------|-----------|-----------|
| Phase 1 | 준비 및 데이터 마이그레이션 | 1일 |
| Phase 2 | Cloud Functions 수정 | 1일 |
| Phase 3 | 손님앱 수정 | 1일 |
| Phase 4 | 콜매니저 수정 | 1일 |
| Phase 5 | 기사앱 수정 | 1일 |
| Phase 6 | 통합 테스트 및 배포 | 1일 |
| **합계** | | **6일** |

---

## 9. 마이그레이션 후 장점

### 9-1. 확장성
✅ **새로운 지역 추가 간편**
- UI에서 "경기도 → 남양주시" 선택만으로 사무실 개설 가능
- 수동 regions 문서 생성 불필요

✅ **전국 서비스 확장 준비 완료**
- 16개 도/특별시/광역시 지원
- 각 도의 모든 시/군/구 추가 가능

### 9-2. 사용자 경험
✅ **직관적인 지역 선택**
- 도 → 시/군/구 → 사무실 (3단계 선택)
- 사용자가 익숙한 행정구역 체계

### 9-3. 관리 효율성
✅ **계층적 데이터 관리**
- 도 단위 통계 집계 가능
- 지역별 마케팅 전략 수립 용이

---

## 10. 결론

**배포 전 현재 시점이 구조 변경의 최적 타이밍입니다.**

이유:
1. ✅ 사용자 영향 없음 (테스트만 진행 중)
2. ✅ 데이터 마이그레이션 부담 적음
3. ✅ 전국 서비스 확장 인프라 확보
4. ✅ 나중에 바꾸는 것보다 훨씬 쉬움

**작업 진행 여부를 결정해주시면 즉시 시작하겠습니다!**

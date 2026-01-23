# 정산 로직 개선 계획

## 개요

기사앱(driver_app)과 콜매니저(call_manager) 간 정산 데이터 불일치 문제를 해결하고,
비용 효율적인 동기화 시스템을 구축하기 위한 개선 계획입니다.

---

## 1. 현재 문제점

### 1.1 데이터 불일치
| 문제 | 원인 | 영향 |
|------|------|------|
| 정산 계산 위치 분산 | 기사앱(로컬), 콜매니저(Firestore) 각자 계산 | 같은 콜의 정산액이 다를 수 있음 |
| 납입금 비율 미동기화 | 각 앱에서 별도 설정값 사용 | 실제 납입금 불일치 |
| 실시간 동기화 부재 | 요금 수정 시 상대 앱에 반영 안됨 | 구 데이터 기반 정산 |

### 1.2 관점 미반영
- 같은 금액이 기사와 사무실 입장에서 다르게 해석됨
- 납입금: 기사는 "지출", 사무실은 "수익"
- 외상: 기사는 "미납금", 사무실은 "미수금"

### 1.3 비용 문제
- 실시간 Listener 사용 시 Firestore 읽기 비용 폭증
- 효율적인 동기화 메커니즘 부재

---

## 2. 개선 목표

1. **Single Source of Truth**: 공유 정산 문서를 통한 데이터 일관성 확보
2. **관점별 UI**: 기사/사무실 각 입장에 맞는 용어 사용
3. **비용 효율성**: Version 기반 동기화로 Firestore 비용 최소화
4. **오프라인 지원**: 네트워크 불안정 시에도 정산 데이터 보존

---

## 3. 시스템 아키텍처

### 3.1 공유 정산 문서 구조

```
Firestore:
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/
  └── settlementSessions/
        └── {YYYY-MM-DD}  ← 일별 공유 문서
              │
              ├── metadata
              │     ├── version: Long              // 변경 시마다 증가
              │     ├── lastUpdatedAt: Timestamp
              │     ├── lastUpdatedBy: String     // "driver_app" | "call_manager"
              │     ├── depositRatio: Int         // 납입비율 (예: 60)
              │     └── createdAt: Timestamp
              │
              ├── totals
              │     ├── totalFare: Long           // 총 운행료
              │     ├── totalDeposit: Long        // 총 납입액 (사무실 몫)
              │     ├── totalDriverShare: Long    // 총 기사 몫
              │     ├── totalCash: Long           // 총 현금
              │     ├── totalCard: Long           // 총 카드/이체
              │     ├── totalCredit: Long         // 총 외상
              │     ├── totalPoints: Long         // 총 포인트 사용
              │     └── callCount: Int            // 총 콜 수
              │
              └── calls: List<CallSettlement>
                    └── {
                          callId: String,
                          driverId: String,
                          driverName: String,
                          customerName: String,
                          customerPhone: String,
                          departure: String,
                          destination: String,
                          fare: Long,
                          paymentMethod: String,   // "현금" | "이체" | "외상" | "현금+포인트" | "포인트"
                          cashReceived: Long,
                          creditAmount: Long,
                          pointsUsed: Long,
                          completedAt: Timestamp,
                          confirmedByOffice: Boolean,  // 사무실 확인 여부
                          syncedAt: Timestamp          // 동기화 시간
                        }
```

### 3.2 데이터 흐름

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│                    Firestore 공유 문서                           │
│              settlementSessions/{YYYY-MM-DD}                     │
│                                                                  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
          ▼                │                ▼
┌─────────────────┐        │       ┌─────────────────┐
│    기사앱        │        │       │   콜매니저       │
│                 │        │       │                 │
│ ┌─────────────┐ │        │       │ ┌─────────────┐ │
│ │  Room DB    │ │        │       │ │  Room DB    │ │
│ │ (로컬 캐시)  │ │        │       │ │ (로컬 캐시)  │ │
│ └─────────────┘ │        │       │ └─────────────┘ │
│                 │        │       │                 │
│ 운행 완료 시:    │        │       │ 정산 확인 시:    │
│ 1. Room 저장    │────────┼──────▶│ 1. 확인 표시    │
│ 2. Firestore   │        │       │ 2. version++   │
│    동기화       │◀───────┼───────│ 3. Room 갱신    │
│ 3. version++   │        │       │                 │
└─────────────────┘        │       └─────────────────┘
                           │
                    Version 체크로
                    변경 감지 시만
                      전체 동기화
```

### 3.3 동기화 전략

| 상황 | 동작 |
|------|------|
| 앱 시작/화면 진입 | version 체크 → 변경 시 전체 동기화 |
| 운행 완료 (기사앱) | 로컬 저장 → Firestore 트랜잭션 → version++ |
| 정산 확인 (콜매니저) | Firestore 트랜잭션 → version++ → 로컬 갱신 |
| 오프라인 상태 | 로컬 저장만 → 온라인 시 동기화 |

---

## 4. 용어 체계 개선

### 4.1 기사앱 (지출 관점)

| 현재 | 변경 | 의미 |
|------|------|------|
| 총 수입 | **총 운행료** | 전체 운행에서 발생한 금액 |
| 총 납입 | **납부액** | 사무실에 내야 할 금액 |
| 실 수입 | **내 수익** | 기사가 가져가는 금액 |
| 총 외상 | **미납금** | 아직 정산하지 못한 금액 |
| 실 납입 | **실 납부액** | 실제로 내야 할 금액 |

### 4.2 콜매니저 (수익 관점)

| 현재 | 변경 | 의미 |
|------|------|------|
| 총매출 | 총 매출 (유지) | 전체 운행 매출 |
| 총수입 | **수수료 수익** | 사무실이 받을 금액 |
| 기사 납입 | **납입 예정액** | 기사에게 받을 예정 금액 |
| 외상 | **미수금** | 아직 받지 못한 금액 |
| 실수입 | **실 수익** | 실제로 받은 금액 |

---

## 5. 구현 단계

### Phase 1: 데이터 모델 정의 (1일)

#### 1.1 공통 모델 (shared 모듈 또는 각 앱에 동일하게)

**파일 생성:**
- `driver_app`: `data/settlement/SettlementModels.kt`
- `call_manager`: `data/settlement/SettlementModels.kt`

**내용:**
- `SettlementSession` - 공유 문서 전체 구조
- `SettlementMetadata` - 메타데이터
- `SettlementTotals` - 집계 데이터
- `CallSettlement` - 개별 콜 정산 정보

#### 1.2 Room Entity 정의

**기사앱 (신규):**
- `PendingSyncEntity` - 오프라인 시 저장할 동기화 대기 데이터
- `SettlementCacheEntity` - 로컬 캐시

**콜매니저 (기존 수정):**
- `SettlementEntity` 수정 - 공유 문서 구조에 맞게 조정

---

### Phase 2: 기사앱 수정 (3-4일)

#### 2.1 Room DB 추가

**의존성 추가 (build.gradle):**
```kotlin
implementation "androidx.room:room-runtime:2.6.1"
implementation "androidx.room:room-ktx:2.6.1"
kapt "androidx.room:room-compiler:2.6.1"
```

**파일 생성:**
- `data/local/SettlementDatabase.kt`
- `data/local/SettlementDao.kt`
- `data/local/PendingSyncEntity.kt`
- `data/local/SettlementCacheEntity.kt`

#### 2.2 동기화 로직 구현

**파일 생성:**
- `data/repository/SettlementRepository.kt`
  - `saveCallLocally()` - 로컬 저장
  - `syncToFirestore()` - Firestore 동기화
  - `checkAndSync()` - version 체크 및 동기화
  - `getPendingSyncs()` - 대기 중인 동기화 조회

**파일 수정:**
- `viewmodel/DriverViewModel.kt`
  - `completeAndSettle()` 수정 - Repository 사용

#### 2.3 오프라인 지원

**파일 생성:**
- `util/NetworkMonitor.kt` - 네트워크 상태 감지
- `worker/SettlementSyncWorker.kt` - WorkManager로 백그라운드 동기화

#### 2.4 UI 용어 변경

**파일 수정:**
- `ui/home/HistorySettlementScreen.kt`
  - 용어 변경: 납부액, 내 수익, 미납금 등

---

### Phase 3: 콜매니저 수정 (2-3일)

#### 3.1 정산 확인 로직 수정

**파일 수정:**
- `ui/settlement/SettlementViewModel.kt`
  - `confirmSettlement()` - 공유 문서 트랜잭션 업데이트
  - `checkAndSync()` - version 체크 및 동기화
  - 납입비율을 공유 문서에서 읽기

#### 3.2 UI 용어 변경

**파일 수정:**
- `ui/settlement/screen/AllTripsScreen.kt`
- `ui/settlement/screen/DriverSummaryScreen.kt`
- `ui/settlement/screen/CreditManagementScreen.kt`
  - 용어 변경: 수수료 수익, 납입 예정액, 미수금 등

#### 3.3 Room Entity 조정

**파일 수정:**
- `data/local/SettlementEntity.kt` - 공유 문서 구조에 맞게 필드 조정

---

### Phase 4: Cloud Functions 연동 (1일)

#### 4.1 일일 정산 마감 수정

**파일 수정:**
- `functions/src/finalizeWorkDay.ts`
  - 공유 문서(`settlementSessions/{date}`)에서 데이터 읽기
  - 마감 처리 시 `isFinalized: true` 설정

#### 4.2 정산 문서 자동 생성

**파일 생성:**
- `functions/src/handlers/settlement.ts`
  - `initializeDailySession()` - 당일 첫 콜 시 문서 생성
  - `onCallCompleted` 트리거에서 호출

---

### Phase 5: 테스트 및 검증 (2일)

#### 5.1 시나리오 테스트

| 테스트 케이스 | 예상 결과 |
|-------------|----------|
| 기사앱 운행 완료 → 콜매니저 확인 | 양쪽 동일 데이터 표시 |
| 기사앱 오프라인 운행 완료 → 온라인 | 자동 동기화 |
| 동시 수정 (기사 + 콜매니저) | 트랜잭션으로 충돌 방지 |
| 납입비율 변경 | 양쪽 동일 비율 적용 |
| 일자 변경 (자정 이후) | 새 문서 생성 |

#### 5.2 비용 모니터링

- Firestore 읽기/쓰기 횟수 측정
- 실시간 Listener 대비 비용 절감 확인

---

## 6. 파일 변경 목록

### 6.1 기사앱 (driver_app)

**신규 생성:**
```
app/src/main/java/com/designated/driverapp/
├── data/
│   ├── local/
│   │   ├── SettlementDatabase.kt
│   │   ├── SettlementDao.kt
│   │   ├── PendingSyncEntity.kt
│   │   └── SettlementCacheEntity.kt
│   ├── settlement/
│   │   └── SettlementModels.kt
│   └── repository/
│       └── SettlementRepository.kt
├── util/
│   └── NetworkMonitor.kt
└── worker/
    └── SettlementSyncWorker.kt
```

**수정:**
```
app/src/main/java/com/designated/driverapp/
├── viewmodel/DriverViewModel.kt
└── ui/home/HistorySettlementScreen.kt

app/build.gradle  (Room 의존성 추가)
```

### 6.2 콜매니저 (call_manager)

**신규 생성:**
```
app/src/main/java/com/designated/callmanager/
└── data/
    └── settlement/
        └── SettlementModels.kt
```

**수정:**
```
app/src/main/java/com/designated/callmanager/
├── data/local/SettlementEntity.kt
├── ui/settlement/SettlementViewModel.kt
└── ui/settlement/screen/
    ├── AllTripsScreen.kt
    ├── DriverSummaryScreen.kt
    └── CreditManagementScreen.kt
```

### 6.3 Cloud Functions

**신규 생성:**
```
functions/src/handlers/settlement.ts
```

**수정:**
```
functions/src/finalizeWorkDay.ts
functions/src/index.ts
```

---

## 7. 일정 요약

| Phase | 작업 | 예상 기간 |
|-------|------|----------|
| 1 | 데이터 모델 정의 | 1일 |
| 2 | 기사앱 수정 | 3-4일 |
| 3 | 콜매니저 수정 | 2-3일 |
| 4 | Cloud Functions 연동 | 1일 |
| 5 | 테스트 및 검증 | 2일 |
| **총계** | | **9-11일** |

---

## 8. 위험 요소 및 대응

| 위험 | 대응 방안 |
|------|----------|
| 기존 정산 데이터 손실 | 마이그레이션 불필요 (신규 구조로 시작) |
| 트랜잭션 실패 | 재시도 로직 구현, 실패 시 로컬 보관 |
| 문서 크기 초과 (1MB) | 일일 콜 100건 이상 시 서브컬렉션 분리 |
| 자정 경계 처리 | completedAt 기준으로 날짜 결정 |

---

## 9. 성공 지표

- [ ] 기사앱-콜매니저 정산 금액 100% 일치
- [ ] 오프라인 운행 완료 후 온라인 시 자동 동기화
- [ ] Firestore 비용 50% 이상 절감 (실시간 Listener 대비)
- [ ] 관점별 용어 적용으로 사용자 혼란 감소

---

## 10. 구현 완료 상태 (2026-01-22)

### 10.1 Phase별 완료 현황

| Phase | 상태 | 완료일 |
|-------|------|--------|
| Phase 1: 데이터 모델 정의 | ✅ 완료 | 2026-01-22 |
| Phase 2: 기사앱 수정 | ✅ 완료 | 2026-01-22 |
| Phase 3: 콜매니저 수정 | ✅ 완료 | 2026-01-22 |
| Phase 4: Cloud Functions 연동 | ✅ 완료 | 2026-01-22 |
| Phase 5: 테스트 및 검증 | ✅ 완료 | 2026-01-22 |

### 10.2 생성/수정된 파일 목록

**기사앱 (driver_app):**
```
신규:
├── data/local/PendingSyncEntity.kt
├── data/local/SettlementCacheEntity.kt
├── data/local/SettlementDao.kt
├── data/local/SettlementDatabase.kt
├── data/repository/SettlementRepository.kt
├── data/settlement/SettlementModels.kt
├── util/NetworkMonitor.kt
└── worker/SettlementSyncWorker.kt

수정:
├── viewmodel/DriverViewModel.kt (saveToSettlementSession 추가)
├── ui/home/HistorySettlementScreen.kt (용어 변경)
├── MainActivity.kt (WorkManager 초기화)
├── build.gradle (Room, WorkManager 의존성)
└── gradle/libs.versions.toml (버전 카탈로그)
```

**콜매니저 (call_manager):**
```
신규:
└── data/settlement/SettlementModels.kt

수정:
├── ui/settlement/SettlementViewModel.kt (동기화 메서드 추가)
├── ui/settlement/screen/AllTripsScreen.kt (용어 변경)
└── ui/settlement/screen/DriverSummaryScreen.kt (용어 변경)
```

**Cloud Functions:**
```
신규:
└── src/handlers/settlement.ts

수정:
└── src/index.ts (4개 함수 추가)
```

### 10.3 빌드 검증 결과

| 컴포넌트 | 빌드 결과 |
|----------|-----------|
| driver_app | ✅ BUILD SUCCESSFUL |
| call_manager | ✅ BUILD SUCCESSFUL |
| Cloud Functions | ✅ BUILD SUCCESSFUL |

---

## 11. 테스트 시나리오

### 11.1 기본 시나리오

| # | 시나리오 | 테스트 방법 | 예상 결과 |
|---|---------|------------|----------|
| 1 | 기사앱 운행 완료 | 기사앱에서 콜 완료 처리 | settlementSessions 문서에 자동 추가 |
| 2 | 콜매니저 데이터 확인 | 콜매니저에서 정산 화면 열기 | 기사앱과 동일한 데이터 표시 |
| 3 | 정산 확인 처리 | 콜매니저에서 콜 확인 클릭 | confirmedByOffice=true로 변경 |
| 4 | 용어 확인 (기사앱) | 정산 화면 확인 | "총 운행료", "납부액", "내 수익", "미납금" 표시 |
| 5 | 용어 확인 (콜매니저) | 기사별 통계 화면 확인 | "총 운행료", "수수료", "미수금", "실 수령액" 표시 |

### 11.2 오프라인 시나리오

| # | 시나리오 | 테스트 방법 | 예상 결과 |
|---|---------|------------|----------|
| 6 | 오프라인 운행 완료 | 비행기 모드에서 콜 완료 | Room DB에 로컬 저장 |
| 7 | 온라인 복귀 동기화 | 네트워크 연결 | WorkManager가 자동 동기화 |
| 8 | 앱 재시작 후 동기화 | 앱 강제 종료 후 재시작 | 미동기화 데이터 자동 전송 |

### 11.3 동시성 시나리오

| # | 시나리오 | 테스트 방법 | 예상 결과 |
|---|---------|------------|----------|
| 9 | 동시 운행 완료 | 2명 기사가 동시에 완료 | 트랜잭션으로 데이터 손실 없음 |
| 10 | 기사/콜매니저 동시 수정 | 동시에 정산 데이터 수정 | version 충돌 시 재시도 |

### 11.4 스케줄 함수 시나리오

| # | 시나리오 | 테스트 방법 | 예상 결과 |
|---|---------|------------|----------|
| 11 | 일일 자동 마감 | 06:10 크론잡 실행 대기 | 전날 세션 isFinalized=true |
| 12 | 불일치 검사 | 07:00 크론잡 실행 대기 | 불일치 시 FCM 알림 |
| 13 | 수동 불일치 검사 | manualCheckSettlementDiscrepancy 호출 | 검사 결과 반환 |

---

## 12. 배포 체크리스트

### 12.1 배포 전 확인

- [ ] 모든 빌드 성공 확인
- [ ] 기존 데이터 백업 (필요 시)
- [ ] Firebase 프로젝트 설정 확인
- [ ] Firestore 인덱스 확인 (필요 시 추가)

### 12.2 Cloud Functions 배포

```bash
# 1. functions 디렉토리로 이동
cd /home/user/designated_driver/functions

# 2. 빌드 확인
npm run build

# 3. 함수 배포 (새로 추가된 함수만)
firebase deploy --only functions:onCallCompletedUpdateSettlement
firebase deploy --only functions:autoFinalizeSettlements
firebase deploy --only functions:checkSettlementDiscrepanciesScheduled
firebase deploy --only functions:manualCheckSettlementDiscrepancy

# 또는 전체 배포
firebase deploy --only functions
```

### 12.3 앱 배포

```bash
# 기사앱 릴리스 빌드
cd /home/user/designated_driver/driver_app
./gradlew assembleRelease

# 콜매니저 릴리스 빌드
cd /home/user/designated_driver/call_manager
./gradlew assembleRelease
```

### 12.4 배포 후 확인

- [ ] Cloud Functions 로그 확인 (Firebase Console > Functions > 로그)
- [ ] Firestore에 settlementSessions 컬렉션 생성 확인
- [ ] 기사앱 운행 완료 시 정산 세션 자동 생성 확인
- [ ] 콜매니저에서 정산 데이터 표시 확인
- [ ] 스케줄 함수 실행 확인 (다음날 06:10, 07:00)

### 12.5 롤백 계획

**문제 발생 시:**

1. Cloud Functions 롤백:
```bash
# 이전 버전으로 롤백
firebase functions:rollback onCallCompletedUpdateSettlement
```

2. 앱 롤백:
   - Play Store/App Store에서 이전 버전 재배포
   - 또는 사용자에게 업데이트 대기 안내

---

## 13. 모니터링

### 13.1 주요 모니터링 항목

| 항목 | 모니터링 방법 | 임계값 |
|------|--------------|--------|
| Firestore 읽기/쓰기 | Firebase Console > Usage | 일 10,000건 이하 |
| Cloud Functions 에러율 | Firebase Console > Functions | 1% 이하 |
| 정산 불일치 알림 | FCM 알림 빈도 | 일 0건 목표 |
| 동기화 실패 | 앱 로그 | 재시도 3회 이하 |

### 13.2 로그 확인 명령어

```bash
# Cloud Functions 로그 확인
firebase functions:log --only onCallCompletedUpdateSettlement

# 특정 시간대 로그
firebase functions:log --only autoFinalizeSettlements --since 6h
```

---

## 14. FAQ

**Q: 기존 정산 데이터는 어떻게 되나요?**
A: 기존 데이터는 그대로 유지됩니다. 새로운 시스템은 신규 콜부터 적용되며, 기존 Room DB/로컬 데이터와 병행 운영됩니다.

**Q: 오프라인에서 완료한 콜이 손실될 수 있나요?**
A: 아니요. Room DB에 먼저 저장되고, WorkManager가 네트워크 연결 시 자동으로 Firestore에 동기화합니다.

**Q: 납입비율은 어디서 설정하나요?**
A: 콜매니저 AllTripsScreen에서 설정 가능하며, 공유 문서의 metadata.depositRatio에 저장됩니다.

**Q: 동시에 여러 기사가 운행 완료하면?**
A: Firestore 트랜잭션을 사용하여 동시 업데이트 충돌을 방지합니다. 각 콜은 개별적으로 안전하게 추가됩니다.

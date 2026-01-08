# Flutter 고객앱 vs 원본 Kotlin 고객앱 정확한 비교 분석

**작성일**: 2025-11-18
**분석 대상**:
- 원본: `customer_app/app/` (Kotlin + Jetpack Compose)
- Flutter: `designated_customer_flutter/` (Dart + Flutter)

---

## 📊 1. 전체 완성도 비교

| 구분 | 원본 Kotlin 앱 | Flutter 앱 | 완성도 |
|------|---------------|------------|--------|
| **총 파일 수** | 약 45개 | 24개 | 53% |
| **UI 구조** | BottomNavigation (4탭) | 단일 화면 | 25% |
| **핵심 기능** | 5개 완성 | 1개 완성 | 20% |
| **데이터 모델** | 8개 | 3개 | 38% |
| **서비스 계층** | 5개 | 2개 | 40% |
| **전체 평가** | **100%** | **~35%** | 35% |

---

## 📱 2. UI 구조 비교

### 원본 Kotlin 앱 (MainNavigation.kt)

```
BottomNavigationBar (4개 탭)
├─ HOME (home)
│  ├─ 사무실명 + 포인트 잔액 (상단)
│  ├─ 만보기 카드 (StepCounterCard)
│  └─ 대리운전 호출 버튼 2개
│     ├─ 전화호출 (전화 앱 실행)
│     └─ 앱호출 (LocationBottomSheet)
│
├─ HISTORY (history)
│  ├─ 통계 요약 카드 (총 이용 횟수/요금/포인트)
│  └─ 콜 내역 리스트
│
├─ POINTS (points)
│  ├─ 포인트 카드 (잔액/등급/다음 등급)
│  ├─ 등급 안내 카드
│  └─ 포인트 사용 안내 카드
│
└─ PROFILE (profile)
   ├─ 고객 정보 카드
   └─ 앱 정보 카드
```

### Flutter 앱 (현재)

```
단일 화면 구조
└─ HomeScreen
   ├─ 사무실 정보 카드
   ├─ Attribution 정보 표시
   ├─ 대리운전 요청 버튼 → CallRequestScreen
   ├─ 콜 내역 버튼 → CallHistoryScreen
   └─ Phase 3 완료 라벨
```

**격차**: BottomNavigation 없음, 포인트/프로필 화면 없음

---

## 🎯 3. 기능별 상세 비교

### 3.1 인증 시스템

| 항목 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **인증 방식** | Anonymous Auth | Anonymous Auth | ✅ 동일 |
| **자동 로그인** | ✅ 지원 | ✅ 지원 | ✅ 동일 |
| **Phone Auth** | 코드만 있음 (미사용) | 미구현 | ⚠️ 동일 |
| **프로필 확인** | Firestore customers 조회 | ❌ 미구현 | ❌ |

**평가**: 기본 인증은 동일, 프로필 관리 없음

---

### 3.2 콜 요청 시스템

| 기능 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **출발지 입력** | ✅ 텍스트/GPS/집주소 | ✅ 텍스트/GPS | ⚠️ 집주소 없음 |
| **목적지 입력** | ✅ 텍스트/음성 | ✅ 텍스트/음성 | ✅ |
| **음성 인식** | ✅ 한국어 (STT) | ✅ 한국어 (STT) | ✅ |
| **위치 서비스** | ✅ Geocoder (주소 변환) | ⚠️ 좌표만 표시 | ⚠️ |
| **포인트 사용** | ✅ 체크박스 + 슬라이더 | ❌ 미구현 | ❌ |
| **집주소 저장** | ✅ SharedPreferences | ❌ 미구현 | ❌ |
| **콜 요청** | ✅ CustomerCall 생성 | ✅ CustomerCall 생성 | ✅ |

**Kotlin HomeScreen 상세 기능:**
```dart
// 앱호출 버튼 클릭 → LocationBottomSheet
LocationBottomSheet(
  currentLocation: String,          // 출발지 (자동/수동)
  destinationLocation: String,      // 목적지 (자동/수동/음성)
  homeAddress: String,              // 저장된 집주소
  usePoints: Boolean,               // 포인트 사용 여부
  pointsToUse: Int,                 // 사용할 포인트
  availablePoints: Int,             // 사용 가능 포인트
  onRequestCall: () -> Unit
)
```

**Flutter CallRequestScreen 현재 기능:**
```dart
CallRequestScreen(
  currentLocation: String,          // 출발지 (GPS/수동)
  destinationLocation: String,      // 목적지 (음성/수동)
  notes: String?,                   // 메모
  // ❌ 포인트 사용 기능 없음
  // ❌ 집주소 기능 없음
)
```

**평가**: 기본 콜 요청은 작동, 포인트 통합 및 집주소 기능 누락

---

### 3.3 콜 내역 (History)

| 기능 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **콜 목록 조회** | ✅ 최대 50개 | ✅ 무제한 | ✅ |
| **실시간 모니터링** | ✅ Flow | ✅ FutureProvider | ⚠️ Stream 미사용 |
| **통계 요약** | ✅ 총 횟수/요금/포인트 | ❌ 없음 | ❌ |
| **상태별 색상** | ✅ 6가지 상태 | ✅ 6가지 상태 | ✅ |
| **기사 정보** | ✅ 표시 | ✅ 표시 | ✅ |
| **포인트 표시** | ✅ 적립/사용 | ⚠️ 사용만 | ⚠️ |

**Kotlin 통계 카드:**
```kotlin
CallSummaryCard(
  totalCalls: Int,              // 총 이용 횟수
  totalFare: Int,               // 총 이용 요금
  totalPointsEarned: Int        // 총 포인트 적립
)
```

**평가**: 기본 목록 표시는 작동, 통계 기능 없음

---

### 3.4 포인트 시스템 ⭐ 가장 큰 격차

| 기능 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **포인트 조회** | ✅ PointService | ❌ 없음 | ❌ |
| **포인트 적립** | ✅ 자동 (등급별 3~9%) | ❌ 없음 | ❌ |
| **포인트 사용** | ✅ 슬라이더로 선택 | ❌ 없음 | ❌ |
| **등급 시스템** | ✅ 4단계 (BRONZE/SILVER/GOLD/VIP) | ❌ 없음 | ❌ |
| **등급 업그레이드** | ✅ 자동 (이용 횟수 기반) | ❌ 없음 | ❌ |
| **거래 내역** | ✅ PointTransaction | ❌ 없음 | ❌ |
| **실시간 모니터링** | ✅ Flow | ❌ 없음 | ❌ |
| **PointScreen** | ✅ 전용 화면 | ❌ 없음 | ❌ |

**Kotlin 포인트 시스템 구조:**

**1. CustomerPoints 모델**
```kotlin
data class CustomerPoints(
  customerId: String,
  phoneNumber: String,
  currentPoints: Int,           // 보유 포인트
  totalEarned: Int,             // 총 적립
  totalUsed: Int,               // 총 사용
  grade: CustomerGrade,         // 등급
  totalCalls: Int,              // 총 이용 횟수
  lastUpdated: Timestamp,
  createdAt: Timestamp
)
```

**2. CustomerGrade (등급 정의)**
```kotlin
enum class CustomerGrade(
  displayName: String,
  icon: String,
  pointRate: Double,            // 적립률
  minCalls: Int
) {
  BRONZE("브론즈", "🥉", 0.03, 0),    // 0회+, 3% 적립
  SILVER("실버", "🥈", 0.05, 10),     // 10회+, 5% 적립
  GOLD("골드", "🥇", 0.07, 30),       // 30회+, 7% 적립
  VIP("VIP", "⭐", 0.09, 50)         // 50회+, 9% 적립
}
```

**3. PointService 주요 메서드**
```kotlin
class PointService {
  // 포인트 조회 (없으면 자동 생성)
  suspend fun getCustomerPoints(phone): CustomerPoints

  // 실시간 모니터링
  fun observeCustomerPoints(phone): Flow<CustomerPoints>

  // 포인트 적립 (운행 완료 시 자동)
  suspend fun earnPoints(phone, callId, fare): Boolean {
    val points = getCustomerPoints(phone)
    val earnedPoints = (fare * points.grade.pointRate).toInt()

    // Firestore 트랜잭션으로 원자성 보장
    firestore.runTransaction { transaction ->
      // 1. CustomerPoints 업데이트
      currentPoints += earnedPoints
      totalEarned += earnedPoints
      totalCalls += 1

      // 2. PointTransaction 기록
      PointTransaction(
        type = EARN,
        amount = earnedPoints,
        balance = currentPoints,
        callId = callId,
        fare = fare
      )

      // 3. 등급 업그레이드 확인
      if (shouldUpdateGrade()) {
        grade = getUpdatedGrade()
      }
    }
  }

  // 포인트 사용
  suspend fun usePoints(phone, amount): Boolean {
    if (!canUsePoints(amount)) return false
    // 잔액 차감 + 거래 내역 기록
  }

  // 거래 내역 조회
  suspend fun getPointTransactions(phone, limit = 20): List<PointTransaction>

  // 실시간 거래 내역
  fun observePointTransactions(phone): Flow<List<PointTransaction>>
}
```

**4. PointTransaction (거래 내역)**
```kotlin
data class PointTransaction(
  id: String,
  customerId: String,
  type: TransactionType,        // EARN, USE, EXPIRE, CANCEL, ADMIN
  amount: Int,                  // 변경 금액
  balance: Int,                 // 거래 후 잔액
  description: String,
  callId: String?,
  fare: Int?,
  grade: String,
  timestamp: Timestamp
)
```

**5. PointScreen UI**
```kotlin
@Composable
fun PointScreen() {
  Column {
    // 포인트 카드
    PointCard(
      currentPoints: Int,
      grade: CustomerGrade,
      callsToNextGrade: Int?
    )

    // 등급 안내
    GradeInfoCard(
      grades: List<CustomerGrade>
    )

    // 포인트 사용 안내
    PointUsageInfoCard()

    // 거래 내역 (선택적)
    PointTransactionList()
  }
}
```

**Flutter 현재 상태:**
- `point_constants.dart`: 등급 정의만 있음
- 서비스, 리포지토리, 프로바이더 전부 없음
- UI 화면 없음

**평가**: **0% 구현** (정의만 있고 로직/UI 없음)

---

### 3.5 만보계 (Step Counter) ⭐ Flutter 완전 누락

| 기능 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **하드웨어 센서** | ✅ TYPE_STEP_COUNTER | ❌ 없음 | ❌ |
| **Foreground Service** | ✅ 백그라운드 실행 | ❌ 없음 | ❌ |
| **일일 걸음수** | ✅ Room DB 저장 | ❌ 없음 | ❌ |
| **세션 관리** | ✅ 시작/리셋 | ❌ 없음 | ❌ |
| **주간/월간 통계** | ✅ 자동 계산 | ❌ 없음 | ❌ |
| **StepCounterCard** | ✅ HomeScreen에 표시 | ❌ 없음 | ❌ |

**Kotlin 만보계 구조:**

**1. StepCounterService (Foreground Service)**
```kotlin
class StepCounterService : Service(), SensorEventListener {
  private val sensorManager: SensorManager
  private val stepCounterSensor: Sensor  // TYPE_STEP_COUNTER
  private val stepDetectorSensor: Sensor // TYPE_STEP_DETECTOR

  // StateFlow (실시간 상태)
  val currentSteps: StateFlow<Int>
  val sessionSteps: StateFlow<Int>
  val isSessionActive: StateFlow<Boolean>

  override fun onSensorChanged(event: SensorEvent) {
    when (event.sensor.type) {
      TYPE_STEP_COUNTER -> {
        // 부팅 이후 누적 걸음수
        val totalSteps = event.values[0].toInt()
        updateCurrentSteps(totalSteps)

        // 3초마다 또는 5걸음 이상 차이시 DB 저장
        if (shouldSave()) saveToDatabase()
      }
      TYPE_STEP_DETECTOR -> {
        // 실시간 걸음 감지
        incrementSessionSteps()
      }
    }
  }

  fun startNewSession() {
    isSessionActive = true
    sessionSteps = 0
  }

  fun resetSession() {
    sessionSteps = 0
  }
}
```

**2. Room DB (로컬 저장)**
```kotlin
@Entity(tableName = "daily_steps")
data class DailyStepData(
  @PrimaryKey
  val date: String,             // YYYY-MM-DD
  val steps: Int,
  val goal: Int = 10000,
  val updatedAt: Long
)

@Entity(tableName = "weekly_summary")
data class WeeklyStepSummary(
  @PrimaryKey
  val weekKey: String,          // YYYY-Wnn
  val totalSteps: Int,
  val avgSteps: Int,
  val daysActive: Int
)

@Entity(tableName = "monthly_summary")
data class MonthlyStepSummary(
  @PrimaryKey
  val monthKey: String,         // YYYY-MM
  val totalSteps: Int,
  val avgSteps: Int,
  val daysActive: Int
)
```

**3. StepCounterCard (HomeScreen)**
```kotlin
@Composable
fun StepCounterCard(
  currentSteps: Int,            // 센서 실시간
  sessionSteps: Int,            // 세션 임시
  isSessionActive: Boolean,
  onStartSession: () -> Unit,
  onResetSession: () -> Unit,
  onShowDetail: () -> Unit
) {
  Card {
    // 진행률 표시 (10,000보 기준)
    CircularProgressIndicator(progress = currentSteps / 10000f)

    // 걸음수 숫자
    Text("$currentSteps 걸음")

    // 세션 버튼
    if (isSessionActive) {
      Button("리셋") { onResetSession() }
    } else {
      Button("시작") { onStartSession() }
    }

    // 상세 보기
    IconButton { onShowDetail() }
  }
}
```

**4. StepDetailBottomSheet**
```kotlin
@Composable
fun StepDetailBottomSheet(
  dailyData: DailyStepData?,
  weeklyData: WeeklyStepSummary?,
  monthlyData: MonthlyStepSummary?
) {
  Column {
    // 일일 통계
    DailyStatsSection(dailyData)

    // 주간 통계
    WeeklyStatsSection(weeklyData)

    // 월간 통계
    MonthlyStatsSection(monthlyData)
  }
}
```

**평가**: **0% 구현** (전체 기능 누락)

---

### 3.6 프로필 관리

| 기능 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **프로필 화면** | ✅ ProfileScreen | ❌ 없음 | ❌ |
| **고객 정보 표시** | ✅ 이름/전화/지역/사무실 | ❌ 없음 | ❌ |
| **사무실 정보** | ✅ 전화/계좌 정보 | ⚠️ Attribution에만 | ⚠️ |
| **앱 버전** | ✅ 표시 | ❌ 없음 | ❌ |

**평가**: 프로필 화면 없음

---

### 3.7 FCM 푸시 알림

| 기능 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **FCM 토큰 저장** | ✅ Firestore | ❌ 없음 | ❌ |
| **기사 배정 알림** | ✅ DRIVER_ASSIGNED | ❌ 없음 | ❌ |
| **운행 완료 알림** | ✅ RIDE_COMPLETED | ❌ 없음 | ❌ |
| **콜 취소 알림** | ✅ CALL_CANCELLED | ❌ 없음 | ❌ |
| **브로드캐스트 연동** | ✅ LocalBroadcast | ❌ 없음 | ❌ |
| **알림 클릭 처리** | ✅ Intent extras | ❌ 없음 | ❌ |

**Kotlin FCM 구조:**
```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {
  override fun onMessageReceived(message: RemoteMessage) {
    val type = message.data["type"]

    when (type) {
      "DRIVER_ASSIGNED" -> {
        val callId = message.data["callId"]
        val driverName = message.data["driverName"]
        val driverPhone = message.data["driverPhone"]
        val vehicleNumber = message.data["vehicleNumber"]

        // 브로드캐스트 전송 (MainViewModel에서 수신)
        sendDriverAssignedBroadcast(...)

        // 알림 표시
        showNotification("기사 배정", "$driverName 기사님이 배정되었습니다")
      }

      "RIDE_COMPLETED" -> {
        val fare = message.data["fare"]?.toInt()
        val pointsUsed = message.data["pointsUsed"]?.toInt()

        sendRideCompletedBroadcast(fare, pointsUsed)
        showNotification("운행 완료", "포인트가 적립되었습니다")
      }

      "CALL_CANCELLED" -> {
        sendCallCancelledBroadcast()
        showNotification("콜 취소", "기사가 운행을 취소했습니다")
      }
    }
  }
}
```

**평가**: **0% 구현**

---

## 📂 4. 데이터 모델 비교

### 4.1 구현된 모델

| 모델 | 원본 Kotlin | Flutter | 필드 일치도 |
|------|------------|---------|------------|
| **CustomerCall** | ✅ 19개 필드 | ✅ 19개 필드 | 100% ✅ |
| **CallState** | ✅ 6개 상태 | ✅ 6개 상태 | 100% ✅ |
| **DriverInfo** | ✅ 5개 필드 | ✅ 5개 필드 | 100% ✅ |
| **AttributionResult** | ✅ | ✅ | 100% ✅ |

### 4.2 누락된 모델

| 모델 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **CustomerPoints** | ✅ 8개 필드 | ❌ 없음 | ❌ |
| **CustomerGrade** | ✅ enum (4개) | ⚠️ 상수만 | ⚠️ |
| **PointTransaction** | ✅ 9개 필드 | ❌ 없음 | ❌ |
| **CustomerInfo** | ✅ 14개 필드 | ⚠️ auth/UserModel (간소화) | ⚠️ |
| **DailyStepData** | ✅ Room Entity | ❌ 없음 | ❌ |
| **WeeklyStepSummary** | ✅ Room Entity | ❌ 없음 | ❌ |
| **MonthlyStepSummary** | ✅ Room Entity | ❌ 없음 | ❌ |
| **CallStatus** | ✅ UI 모델 | ❌ 없음 | ❌ |

---

## 🛠️ 5. 서비스/리포지토리 비교

### 5.1 구현된 서비스

| 서비스 | 원본 Kotlin | Flutter | 메서드 일치도 |
|--------|------------|---------|-------------|
| **CallRepository** | ✅ 6개 메서드 | ✅ 6개 메서드 | 100% ✅ |
| **AttributionRepository** | ✅ | ✅ | 100% ✅ |
| **LocationService** | ✅ Geocoder | ⚠️ 좌표만 | 50% ⚠️ |

### 5.2 누락된 서비스

| 서비스 | 원본 Kotlin | Flutter | 상태 |
|--------|------------|---------|------|
| **PointService** | ✅ 6개 메서드 | ❌ 없음 | ❌ |
| **StepCounterService** | ✅ Foreground Service | ❌ 없음 | ❌ |
| **MyFirebaseMessagingService** | ✅ FCM | ❌ 없음 | ❌ |
| **StepRepository** | ✅ Room DAO | ❌ 없음 | ❌ |

---

## 🎨 6. UI 컴포넌트 비교

### 6.1 구현된 화면

| 화면 | 원본 Kotlin | Flutter | 완성도 |
|------|------------|---------|--------|
| **HomeScreen** | ✅ 만보기+포인트+호출 | ⚠️ 호출 버튼만 | 30% |
| **CallRequestScreen** | ✅ 포인트 통합 | ⚠️ 기본 기능 | 70% |
| **CallHistoryScreen** | ✅ 통계 포함 | ⚠️ 목록만 | 60% |

### 6.2 누락된 화면

| 화면 | 원본 Kotlin | Flutter | 상태 |
|------|------------|---------|------|
| **MainNavigation** | ✅ BottomNav 4탭 | ❌ 없음 | ❌ |
| **PointScreen** | ✅ 포인트/등급/내역 | ❌ 없음 | ❌ |
| **ProfileScreen** | ✅ 고객/사무실 정보 | ❌ 없음 | ❌ |
| **PointHistoryScreen** | ✅ 거래 내역 | ❌ 없음 | ❌ |

### 6.3 누락된 컴포넌트

| 컴포넌트 | 원본 Kotlin | Flutter | 상태 |
|----------|------------|---------|------|
| **StepCounterCard** | ✅ | ❌ | ❌ |
| **PointCard** | ✅ | ❌ | ❌ |
| **PointUsageSection** | ✅ | ❌ | ❌ |
| **LocationBottomSheet** | ✅ 포인트 통합 | ⚠️ 간소화 | ⚠️ |
| **StepDetailBottomSheet** | ✅ | ❌ | ❌ |
| **CallStatusDialog** | ✅ 실시간 모니터링 | ❌ | ❌ |

---

## 📊 7. 기능별 우선순위 분석

### 7.1 핵심 기능 (반드시 필요)

| 순위 | 기능 | 원본 | Flutter | 중요도 | 난이도 |
|------|------|------|---------|--------|--------|
| 1 | **BottomNavigation** | ✅ | ❌ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| 2 | **포인트 시스템** | ✅ | ❌ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| 3 | **FCM 푸시 알림** | ✅ | ❌ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ |
| 4 | **프로필 관리** | ✅ | ❌ | ⭐⭐⭐⭐ | ⭐⭐ |
| 5 | **통계 기능** | ✅ | ❌ | ⭐⭐⭐⭐ | ⭐⭐ |

### 7.2 보조 기능 (선택적)

| 순위 | 기능 | 원본 | Flutter | 중요도 | 난이도 |
|------|------|------|---------|--------|--------|
| 6 | **만보계** | ✅ | ❌ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| 7 | **집주소 저장** | ✅ | ❌ | ⭐⭐⭐ | ⭐ |
| 8 | **Geocoding** | ✅ | ⚠️ | ⭐⭐ | ⭐⭐ |

---

## 🚀 8. Phase 4 개발 계획 (정확한 로드맵)

### 8.1 목표

**현재**: 35% 완성
**Phase 4 목표**: 75% 완성
**최종 목표 (Phase 5-6)**: 95% 완성

---

### 8.2 Phase 4: 핵심 기능 보완 (2-3주)

#### **Week 1: BottomNavigation + 기본 화면 구조 (5일)**

**Day 1: MainNavigation 구조**
```
작업:
1. lib/core/navigation/main_navigation.dart 생성
2. BottomNavigationBar 구현 (4탭)
   - HOME, HISTORY, POINTS, PROFILE
3. 탭별 라우팅 설정
4. 현재 HomeScreen, CallHistoryScreen 통합

예상 시간: 4시간
완성도: 35% → 40%
```

**Day 2-3: 포인트 데이터 모델 + Repository**
```
작업:
1. lib/features/points/models/
   - customer_points.dart (8개 필드)
   - customer_grade.dart (enum 4개)
   - point_transaction.dart (9개 필드)

2. lib/features/points/repositories/
   - point_repository.dart
     ✓ getCustomerPoints()
     ✓ earnPoints()
     ✓ usePoints()
     ✓ getPointTransactions()

3. Firestore 경로:
   regions/{regionId}/offices/{officeId}/customerPoints/{phoneNumber}
   regions/{regionId}/offices/{officeId}/pointTransactions/{transactionId}

예상 시간: 8시간
완성도: 40% → 50%
```

**Day 4-5: 포인트 Provider + 기본 로직**
```
작업:
1. lib/features/points/providers/
   - point_provider.dart
     ✓ pointRepositoryProvider
     ✓ customerPointsProvider (FutureProvider)
     ✓ pointTransactionsProvider
     ✓ PointNotifier (StateNotifier)

2. 핵심 메서드 구현:
   - 등급 계산 로직 (fromCallCount)
   - 포인트 적립 계산 (등급별 비율)
   - 포인트 사용 가능 여부 체크

예상 시간: 6시간
완성도: 50% → 55%
```

---

#### **Week 2: 포인트 UI + FCM 기초 (5일)**

**Day 1-2: PointScreen 구현**
```
작업:
1. lib/features/points/screens/point_screen.dart
   - PointCard: 현재 포인트/등급/진행률
   - GradeInfoCard: 등급별 혜택 안내
   - PointUsageInfoCard: 사용 방법 안내

2. lib/features/points/widgets/
   - point_card.dart
   - grade_badge.dart
   - grade_info_card.dart

예상 시간: 8시간
완성도: 55% → 60%
```

**Day 3: HomeScreen 개선 (포인트 통합)**
```
작업:
1. HomeScreen 상단에 포인트 잔액 표시
2. CallRequestScreen에 포인트 사용 UI 추가
   - 체크박스: "포인트 사용"
   - Slider: 사용할 포인트 선택
   - 잔액 표시
   - 할인 금액 계산 미리보기

예상 시간: 4시간
완성도: 60% → 63%
```

**Day 4-5: FCM 기초 설정**
```
작업:
1. pubspec.yaml 추가:
   - firebase_messaging: ^14.7.10

2. lib/core/services/fcm_service.dart
   - FCM 토큰 요청
   - Firestore에 토큰 저장
   - 기본 메시지 수신 핸들러

3. MainActivity.kt (Android):
   - 알림 권한 요청
   - 알림 채널 생성

예상 시간: 6시간
완성도: 63% → 67%
```

---

#### **Week 3: 프로필 + 통계 + FCM 완성 (5일)**

**Day 1-2: ProfileScreen 구현**
```
작업:
1. lib/features/profile/models/
   - customer_info.dart (14개 필드)

2. lib/features/profile/screens/profile_screen.dart
   - 고객 정보 카드 (이름/전화/지역/사무실)
   - 사무실 정보 카드 (전화/계좌)
   - 앱 정보 카드 (버전)

3. lib/features/profile/repositories/
   - profile_repository.dart
     ✓ getCustomerInfo()
     ✓ updateCustomerInfo()

예상 시간: 7시간
완성도: 67% → 71%
```

**Day 3: CallHistoryScreen 개선 (통계 추가)**
```
작업:
1. 통계 요약 카드 추가:
   - 총 이용 횟수
   - 총 이용 요금
   - 총 포인트 적립

2. 통계 계산 로직:
   - List<CustomerCall>에서 집계
   - CallSummary 모델 생성

예상 시간: 4시간
완성도: 71% → 73%
```

**Day 4-5: FCM 푸시 알림 완성**
```
작업:
1. lib/core/services/fcm_service.dart 확장
   - DRIVER_ASSIGNED 처리
   - RIDE_COMPLETED 처리
   - CALL_CANCELLED 처리

2. 알림 클릭 시 화면 이동:
   - Deep link 처리
   - 해당 탭으로 자동 이동

3. 브로드캐스트 대체:
   - Provider 상태 업데이트로 UI 반영

예상 시간: 6시간
완성도: 73% → 75%
```

---

### 8.3 Phase 5: UI/UX 개선 + 세부 기능 (1-2주)

**목표: 75% → 90%**

**주요 작업:**
1. 집주소 저장 기능 (SharedPreferences)
2. Geocoding 개선 (geocoding 패키지)
3. 포인트 거래 내역 화면
4. 실시간 콜 상태 모니터링 다이얼로그
5. 로딩/에러 상태 개선
6. 애니메이션 추가

---

### 8.4 Phase 6: 만보계 + 고급 기능 (선택적, 2-3주)

**목표: 90% → 95%**

**주요 작업:**
1. 만보계 구현 (pedometer 패키지 또는 health 패키지)
2. 로컬 DB (sqflite) - 만보기 데이터
3. 만보기 UI (StepCounterCard)
4. 주간/월간 통계
5. 게임 기능 (선택적, 원본도 비활성화)

---

## 📋 9. 우선순위별 구현 체크리스트

### ⭐⭐⭐⭐⭐ 최우선 (Phase 4)

- [ ] **BottomNavigation 구조** (0.5일)
  - [ ] MainNavigation.dart 생성
  - [ ] 4탭 라우팅 설정
  - [ ] 기존 화면 통합

- [ ] **포인트 시스템** (3일)
  - [ ] CustomerPoints 모델
  - [ ] CustomerGrade enum
  - [ ] PointTransaction 모델
  - [ ] PointRepository
  - [ ] PointService 로직
  - [ ] PointProvider

- [ ] **PointScreen UI** (1.5일)
  - [ ] PointCard 위젯
  - [ ] GradeInfoCard
  - [ ] PointUsageInfoCard

- [ ] **HomeScreen 포인트 통합** (0.5일)
  - [ ] 상단 포인트 잔액 표시
  - [ ] CallRequestScreen에 포인트 사용 UI

- [ ] **FCM 푸시 알림** (2일)
  - [ ] FCM 토큰 저장
  - [ ] 기사 배정 알림
  - [ ] 운행 완료 알림
  - [ ] 알림 클릭 처리

- [ ] **ProfileScreen** (1.5일)
  - [ ] CustomerInfo 모델
  - [ ] ProfileRepository
  - [ ] ProfileScreen UI

- [ ] **통계 기능** (0.5일)
  - [ ] CallSummary 계산
  - [ ] CallHistoryScreen에 통계 카드 추가

---

### ⭐⭐⭐ 중요 (Phase 5)

- [ ] **집주소 저장** (0.5일)
  - [ ] SharedPreferences 연동
  - [ ] 빠른 입력 버튼

- [ ] **Geocoding 개선** (0.5일)
  - [ ] geocoding 패키지 추가
  - [ ] 좌표 → 주소 변환

- [ ] **포인트 거래 내역** (1일)
  - [ ] PointHistoryScreen
  - [ ] Transaction 리스트

- [ ] **실시간 모니터링** (1일)
  - [ ] CallStatusDialog
  - [ ] 콜 상태 실시간 추적

---

### ⭐⭐ 선택적 (Phase 6)

- [ ] **만보계** (3-5일)
  - [ ] pedometer/health 패키지
  - [ ] StepCounterService
  - [ ] sqflite 로컬 DB
  - [ ] StepCounterCard UI
  - [ ] 통계 화면

---

## 📊 10. 최종 완성도 예상

| Phase | 기간 | 완성도 | 주요 기능 |
|-------|------|--------|----------|
| **Phase 1-3 (완료)** | 4주 | 35% | 인증, Attribution, 콜 요청 기본 |
| **Phase 4 (계획)** | 2-3주 | 75% | BottomNav, 포인트, FCM, 프로필 |
| **Phase 5 (계획)** | 1-2주 | 90% | UI/UX 개선, 세부 기능 |
| **Phase 6 (선택)** | 2-3주 | 95% | 만보계, 고급 기능 |

**최소 목표**: Phase 4 완료 시 **75%** (실용적 사용 가능)
**권장 목표**: Phase 5 완료 시 **90%** (원본과 거의 동등)
**완벽 목표**: Phase 6 완료 시 **95%** (원본 초과)

---

## 🎯 11. 즉시 착수 권장 작업

**다음 세션에서 시작할 작업:**

```
1. MainNavigation 구조 생성 (1시간)
2. 4개 탭 기본 화면 연결 (1시간)
3. CustomerPoints 모델 생성 (1시간)
4. PointRepository 기초 구현 (2시간)
```

**첫날 목표**: BottomNavigation + 포인트 모델 완성
**첫주 목표**: 포인트 시스템 로직 완성
**2주 목표**: 포인트 UI + FCM 기초
**3주 목표**: 프로필 + 통계 + FCM 완성

---

## 📝 12. 참고사항

**Firebase 프로젝트**: calldetector-5d61e
**대상 기기**: SM G996N (Android 15)
**Flutter 버전**: 확인 필요
**원본 앱 파일 수**: 약 45개
**현재 Flutter 파일 수**: 24개

**원본 앱 비활성 기능:**
- 게임 (DrinkingGameMenuScreen 등) - 주석 처리됨

**Flutter 패키지 추가 필요:**
- firebase_messaging: ^14.7.10 (FCM)
- sqflite: ^2.3.0 (로컬 DB, Phase 6)
- pedometer: ^4.0.1 (만보계, Phase 6)
- geocoding: ^2.1.1 (주소 변환, Phase 5)

---

**작성자**: Claude
**최종 수정**: 2025-11-18
**다음 단계**: Phase 4 Week 1 Day 1 시작

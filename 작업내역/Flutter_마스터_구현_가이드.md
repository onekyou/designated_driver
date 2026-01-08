# Flutter 고객앱 마스터 구현 가이드

**작성일**: 2025-11-18
**목적**: 원본 Kotlin 앱을 다시 보지 않고도 Flutter로 완전 구현 가능
**현재 완성도**: 35%
**최종 목표**: 95%

---

## 📑 목차

1. [현재 구현 상황](#1-현재-구현-상황)
2. [원본 앱 핵심 코드 참조](#2-원본-앱-핵심-코드-참조)
3. [기능별 구현 매핑 테이블](#3-기능별-구현-매핑-테이블)
4. [Phase별 작업 계획 및 의존성](#4-phase별-작업-계획-및-의존성)
5. [빠른 참조 가이드](#5-빠른-참조-가이드)

---

## 1. 현재 구현 상황

### 1.1 완료된 파일 (✅ 100%)

| Flutter 파일 | 원본 Kotlin 파일 | 상태 | 비고 |
|-------------|-----------------|------|------|
| `lib/features/auth/repositories/auth_repository.dart` | MainActivity.kt (익명 인증 부분) | ✅ 100% | Anonymous Auth |
| `lib/features/attribution/repositories/attribution_repository.dart` | AttributionMatchingService.kt | ✅ 100% | 핑거프린트 매칭 |
| `lib/features/call/models/call_model.dart` | data/model/CustomerCall.kt | ✅ 100% | 19개 필드 동일 |
| `lib/features/call/models/call_state.dart` | data/model/CallStatus.kt | ✅ 100% | 6개 상태 |
| `lib/features/call/models/driver_info.dart` | data/model/DriverInfo.kt | ✅ 100% | 5개 필드 |
| `lib/features/call/repositories/call_repository.dart` | service/CallService.kt | ✅ 100% | requestCall, cancelCall, getHistory |

### 1.2 부분 완료 (⚠️ 50-99%)

| Flutter 파일 | 원본 Kotlin 파일 | 완성도 | 누락 기능 |
|-------------|-----------------|--------|----------|
| `lib/features/home/screens/home_screen.dart` | ui/main/HomeScreen.kt | 30% | 만보기, 포인트 표시, 집주소 저장 |
| `lib/features/call/screens/call_request_screen.dart` | ui/main/HomeScreen.kt + LocationBottomSheet | 70% | 포인트 사용, 집주소 빠른 입력 |
| `lib/features/call/screens/call_history_screen.dart` | ui/history/CallHistoryScreen.kt | 60% | 통계 요약 카드 |
| `lib/features/call/providers/location_provider.dart` | service/LocationService.kt | 50% | 주소 변환 (좌표만 표시) |

### 1.3 미구현 (❌ 0%)

#### 서비스 계층
- `service/PointService.kt` → ❌ 없음
- `service/StepCounterService.kt` → ❌ 없음
- `service/MyFirebaseMessagingService.kt` → ❌ 없음

#### UI 계층
- `ui/navigation/MainNavigation.kt` → ❌ 없음
- `ui/point/PointScreen.kt` → ❌ 없음
- `ui/profile/ProfileScreen.kt` → ❌ 없음
- `ui/main/MainViewModel.kt` → ❌ 없음 (상태 관리)

#### 데이터 모델
- `data/model/CustomerPoints.kt` → ❌ 없음
- `data/model/PointTransaction.kt` → ❌ 없음
- `data/model/CustomerInfo.kt` → ⚠️ auth/UserModel (간소화)
- `data/model/DailyStepData.kt` → ❌ 없음

#### 위젯/컴포넌트
- `ui/components/StepCounterCard.kt` → ❌ 없음
- `ui/components/PointCard.kt` → ❌ 없음
- `ui/components/PointUsageSection.kt` → ❌ 없음

---

## 2. 원본 앱 핵심 코드 참조

### 2.1 포인트 시스템 (PointService.kt)

#### 포인트 적립 로직
```kotlin
// 원본 Kotlin
suspend fun earnPoints(
    phoneNumber: String,
    callId: String,
    fare: Int,
    description: String? = null
): Boolean {
    val points = getCustomerPoints(phoneNumber) ?: return false

    // 등급별 적립 계산
    val earnAmount = points.calculateEarnPoints(fare)
    // BRONZE: fare * 0.03
    // SILVER: fare * 0.05
    // GOLD: fare * 0.07
    // VIP: fare * 0.09

    val newBalance = points.currentPoints + earnAmount
    val newTotalCalls = points.totalCalls + 1

    // 등급 자동 업그레이드
    val newGrade = CustomerGrade.fromCallCount(newTotalCalls)
    // 0-9회: BRONZE
    // 10-29회: SILVER
    // 30-49회: GOLD
    // 50회+: VIP

    // Firestore 트랜잭션 (원자성 보장)
    firestore.runTransaction { transaction ->
        // CustomerPoints 업데이트
        transaction.set(
            pointsRef,
            updatedPoints.copy(
                currentPoints = newBalance,
                totalEarned = points.totalEarned + earnAmount,
                totalCalls = newTotalCalls,
                grade = newGrade
            ).toMap()
        )

        // PointTransaction 기록
        transaction.set(
            transactionRef,
            PointTransaction(
                type = TransactionType.EARN,
                amount = earnAmount,
                balance = newBalance,
                callId = callId,
                fare = fare,
                grade = newGrade.name
            ).toMap()
        )
    }.await()
}
```

**Flutter 구현 시:**
```dart
Future<bool> earnPoints({
  required String phoneNumber,
  required String callId,
  required int fare,
  String? description,
}) async {
  final points = await getCustomerPoints(phoneNumber);
  if (points == null) return false;

  // 등급별 적립 계산
  final earnAmount = points.calculateEarnPoints(fare);
  final newBalance = points.currentPoints + earnAmount;
  final newTotalCalls = points.totalCalls + 1;
  final newGrade = CustomerGrade.fromCallCount(newTotalCalls);

  // Firestore 트랜잭션
  await _firestore.runTransaction((transaction) async {
    final pointsRef = _firestore
        .collection('regions').doc(regionId)
        .collection('offices').doc(officeId)
        .collection('customerPoints')
        .doc(phoneNumber);

    final transactionRef = _firestore
        .collection('regions').doc(regionId)
        .collection('offices').doc(officeId)
        .collection('pointTransactions')
        .doc();

    transaction.set(pointsRef, points.copyWith(
      currentPoints: newBalance,
      totalEarned: points.totalEarned + earnAmount,
      totalCalls: newTotalCalls,
      grade: newGrade,
    ).toMap());

    transaction.set(transactionRef, PointTransaction(
      id: transactionRef.id,
      customerId: phoneNumber,
      type: TransactionType.earn,
      amount: earnAmount,
      balance: newBalance,
      callId: callId,
      fare: fare,
      grade: newGrade.name,
    ).toMap());
  });

  return true;
}
```

**Firestore 경로:**
```
regions/{regionId}/offices/{officeId}/customerPoints/{phoneNumber}
regions/{regionId}/offices/{officeId}/pointTransactions/{transactionId}
```

---

#### 포인트 사용 로직
```kotlin
// 원본 Kotlin
suspend fun usePoints(
    phoneNumber: String,
    amount: Int,
    description: String = "포인트 사용"
): Boolean {
    val points = getCustomerPoints(phoneNumber) ?: return false

    // 잔액 확인
    if (!points.canUsePoints(amount)) {
        return false
    }

    val newBalance = points.currentPoints - amount

    // Firestore 트랜잭션
    firestore.runTransaction { transaction ->
        transaction.set(
            pointsRef,
            points.copy(
                currentPoints = newBalance,
                totalUsed = points.totalUsed + amount
            ).toMap()
        )

        transaction.set(
            transactionRef,
            PointTransaction(
                type = TransactionType.USE,
                amount = -amount,  // 음수로 표시
                balance = newBalance,
                description = description
            ).toMap()
        )
    }.await()
}
```

---

### 2.2 만보계 시스템 (StepCounterService.kt)

#### 센서 사용
```kotlin
// 원본 Kotlin
class StepCounterService : Service(), SensorEventListener {
    private val sensorManager: SensorManager
    private var stepCounterSensor: Sensor?  // TYPE_STEP_COUNTER
    private var stepDetectorSensor: Sensor? // TYPE_STEP_DETECTOR

    private val _currentSteps = MutableStateFlow(0)
    val currentSteps: StateFlow<Int> = _currentSteps

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                // 즉시 반응 (UI 업데이트용)
                Sensor.TYPE_STEP_DETECTOR -> {
                    if (isWalkingOrRunning) {
                        _currentSteps.value++
                    }
                }

                // 정확한 총 걸음수 (3초마다 보정)
                Sensor.TYPE_STEP_COUNTER -> {
                    val totalSteps = it.values[0].toInt()
                    val accurateSteps = totalSteps - initialSteps

                    // 2걸음 이상 차이나면 보정
                    if (abs(accurateSteps - _currentSteps.value) >= 2) {
                        _currentSteps.value = accurateSteps
                    }

                    // DB 저장 (5걸음 이상 차이 OR 3초 경과)
                    saveStepsIfNeeded(accurateSteps)
                }
            }
        }
    }
}
```

**Flutter 구현 방법:**

**Android (MethodChannel)**:
```kotlin
// Android Native (MainActivity.kt)
class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.designated.customer/steps"
    private val EVENT_CHANNEL = "com.designated.customer/step_events"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startStepCounter" -> {
                        startStepCounterService()
                        result.success(true)
                    }
                }
            }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL)
            .setStreamHandler(object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    stepCounterService.currentSteps.collectLatest { steps ->
                        events?.success(steps)
                    }
                }
            })
    }
}
```

**Flutter (Dart)**:
```dart
class StepCounterService {
  static const platform = MethodChannel('com.designated.customer/steps');
  static const eventChannel = EventChannel('com.designated.customer/step_events');

  Stream<int> get stepsStream => eventChannel
      .receiveBroadcastStream()
      .map((steps) => steps as int);

  Future<void> startListening() async {
    await platform.invokeMethod('startStepCounter');
  }
}
```

**iOS (HealthKit)**:
```dart
import 'package:health/health.dart';

class StepCounterService {
  final Health _health = Health();

  Future<void> initialize() async {
    await _health.requestAuthorization([HealthDataType.STEPS]);
  }

  Stream<int> observeSteps() async* {
    while (true) {
      final now = DateTime.now();
      final midnight = DateTime(now.year, now.month, now.day);

      final data = await _health.getHealthDataFromTypes(
        midnight,
        now,
        [HealthDataType.STEPS],
      );

      final totalSteps = data.fold<int>(
        0,
        (sum, point) => sum + (point.value as num).toInt(),
      );

      yield totalSteps;
      await Future.delayed(Duration(seconds: 3));
    }
  }
}
```

---

### 2.3 FCM 푸시 알림 (MyFirebaseMessagingService.kt)

#### 메시지 타입별 처리
```kotlin
// 원본 Kotlin
override fun onMessageReceived(message: RemoteMessage) {
    val type = message.data["type"]

    when (type) {
        "DRIVER_ASSIGNED" -> {
            val callId = message.data["callId"]
            val driverName = message.data["driverName"] ?: "기사"
            val driverPhone = message.data["driverPhone"] ?: ""
            val vehicleNumber = message.data["vehicleNumber"] ?: ""
            val driverId = message.data["driverId"] ?: ""

            // LocalBroadcast 전송 (UI 업데이트용)
            sendDriverAssignedBroadcast(callId, driverName, ...)

            // 알림 표시
            showNotification("기사 배정", "$driverName 기사님이 배정되었습니다")
        }

        "RIDE_COMPLETED" -> {
            val fare = message.data["fare"]?.toInt() ?: 0
            val pointsUsed = message.data["pointsUsed"]?.toInt() ?: 0

            sendRideCompletedBroadcast(fare, pointsUsed)
            showNotification("운행 완료", "포인트가 적립되었습니다")
        }

        "CALL_CANCELLED" -> {
            sendCallCancelledBroadcast()
            showNotification("콜 취소", "기사가 운행을 취소했습니다")
        }
    }
}
```

**Flutter 구현:**
```dart
class FirebaseMessagingHandler {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;

  Future<void> initialize() async {
    // 권한 요청
    await _messaging.requestPermission();

    // 토큰 저장
    final token = await _messaging.getToken();
    await _saveTokenToFirestore(token);

    // 포그라운드 메시지 처리
    FirebaseMessaging.onMessage.listen((message) {
      final type = message.data['type'];

      switch (type) {
        case 'DRIVER_ASSIGNED':
          _handleDriverAssigned(message.data);
          break;
        case 'RIDE_COMPLETED':
          _handleRideCompleted(message.data);
          break;
        case 'CALL_CANCELLED':
          _handleCallCancelled(message.data);
          break;
      }
    });

    // 백그라운드/종료 상태 메시지
    FirebaseMessaging.onMessageOpenedApp.listen((message) {
      // 알림 클릭 시 처리
      _navigateToScreen(message.data);
    });
  }

  void _handleDriverAssigned(Map<String, dynamic> data) {
    // Riverpod/GetX/Provider로 상태 업데이트
    final ref = ProviderContainer().read(callProvider.notifier);
    ref.updateDriverInfo(
      callId: data['callId'],
      driverName: data['driverName'],
      driverPhone: data['driverPhone'],
      vehicleNumber: data['vehicleNumber'],
    );

    // 다이얼로그 표시
    showDriverAssignedDialog(data);
  }

  void _handleRideCompleted(Map<String, dynamic> data) {
    final fare = int.parse(data['fare'] ?? '0');
    final pointsUsed = int.parse(data['pointsUsed'] ?? '0');

    // 포인트 적립 팝업
    showPointsEarnedDialog(fare, pointsUsed);
  }
}
```

---

### 2.4 MainViewModel 상태 관리

#### MainUiState 구조
```kotlin
// 원본 Kotlin
data class MainUiState(
    // 위치
    val currentLocation: String = "",
    val destinationLocation: String = "",
    val homeAddress: String = "",
    val isLoadingLocation: Boolean = false,

    // 콜 상태
    val callStatus: CallStatus? = null,
    val isLoadingCall: Boolean = false,

    // 포인트
    val customerPoints: CustomerPoints? = null,
    val usePoints: Boolean = false,
    val pointsToUse: Int = 0,
    val showPointsEarnedDialog: Boolean = false,
    val earnedPoints: Int = 0,

    // 만보기
    val currentStepsRealtime: Int = 0,
    val currentSessionSteps: Int = 0,
    val isSessionActive: Boolean = false,
    val stepData: DailyStepData? = null,
    val showStepDetail: Boolean = false,

    // UI 상태
    val showLocationCard: Boolean = false,
    val showHomeAddressDialog: Boolean = false,

    // 사무실 정보
    val officeName: String = "",
    val regionName: String = "",

    // 에러
    val error: String? = null
) {
    val canRequestCall: Boolean
        get() = currentLocation.isNotEmpty() &&
                destinationLocation.isNotEmpty() &&
                callStatus?.state !in listOf(REQUESTED, ASSIGNED, IN_PROGRESS)
}
```

**Flutter 구현 (Riverpod):**
```dart
@freezed
class HomeUiState with _$HomeUiState {
  const factory HomeUiState({
    @Default('') String currentLocation,
    @Default('') String destinationLocation,
    @Default('') String homeAddress,
    @Default(false) bool isLoadingLocation,
    CallStatus? callStatus,
    @Default(false) bool isLoadingCall,
    CustomerPoints? customerPoints,
    @Default(false) bool usePoints,
    @Default(0) int pointsToUse,
    @Default(false) bool showPointsEarnedDialog,
    @Default(0) int earnedPoints,
    @Default(0) int currentStepsRealtime,
    @Default(0) int currentSessionSteps,
    @Default(false) bool isSessionActive,
    DailyStepData? stepData,
    @Default(false) bool showStepDetail,
    @Default(false) bool showLocationCard,
    @Default(false) bool showHomeAddressDialog,
    @Default('') String officeName,
    @Default('') String regionName,
    String? error,
  }) = _HomeUiState;
}

class HomeNotifier extends StateNotifier<HomeUiState> {
  HomeNotifier(this._callService, this._pointService, this._stepService)
      : super(const HomeUiState());

  final CallService _callService;
  final PointService _pointService;
  final StepCounterService _stepService;

  Future<void> requestCall() async {
    // 포인트 사용 처리
    int usedPoints = 0;
    if (state.usePoints && state.pointsToUse > 0) {
      final success = await _pointService.usePoints(
        amount: state.pointsToUse,
      );
      if (success) usedPoints = state.pointsToUse;
    }

    // 콜 생성
    final call = CustomerCall(
      currentLocation: state.currentLocation,
      destinationLocation: state.destinationLocation,
      pointsUsed: usedPoints,
      // ...
    );

    final callId = await _callService.requestCall(call);

    state = state.copyWith(
      callStatus: CallStatus(callId: callId, state: CallState.requested),
      usePoints: false,
      pointsToUse: 0,
      showLocationCard: false,
    );
  }
}

final homeProvider = StateNotifierProvider<HomeNotifier, HomeUiState>((ref) {
  return HomeNotifier(
    ref.watch(callServiceProvider),
    ref.watch(pointServiceProvider),
    ref.watch(stepServiceProvider),
  );
});
```

---

## 3. 기능별 구현 매핑 테이블

| 기능 | 원본 파일 | 원본 라인 | Flutter 파일 | 상태 | Phase | 우선순위 |
|------|----------|----------|-------------|------|-------|---------|
| **BottomNavigation** | MainNavigation.kt | 전체 | ❌ 없음 | 0% | 4A | ⭐⭐⭐⭐⭐ |
| **HomeScreen 레이아웃** | HomeScreen.kt | 50-200 | home_screen.dart | 30% | 4A | ⭐⭐⭐⭐⭐ |
| **StepCounterCard** | StepCounterCard.kt | 전체 | ❌ 없음 | 0% | 4A | ⭐⭐⭐⭐⭐ |
| **PointScreen** | PointScreen.kt | 전체 | ❌ 없음 | 0% | 4A | ⭐⭐⭐⭐⭐ |
| **ProfileScreen** | ProfileScreen.kt | 전체 | ❌ 없음 | 0% | 4A | ⭐⭐⭐⭐⭐ |
| **포인트 조회** | PointService.kt:getCustomerPoints() | 45-70 | ❌ 없음 | 0% | 4C | ⭐⭐⭐⭐⭐ |
| **포인트 적립** | PointService.kt:earnPoints() | 120-180 | ❌ 없음 | 0% | 4C | ⭐⭐⭐⭐⭐ |
| **포인트 사용** | PointService.kt:usePoints() | 200-250 | ❌ 없음 | 0% | 4C | ⭐⭐⭐⭐⭐ |
| **등급 계산** | CustomerGrade.kt:fromCallCount() | 30-40 | point_constants.dart | 50% | 4C | ⭐⭐⭐⭐⭐ |
| **콜 요청** | CallService.kt:requestCall() | 50-80 | call_repository.dart | 100% | 3 | ✅ |
| **콜 취소** | CallService.kt:cancelCall() | 90-110 | call_repository.dart | 100% | 3 | ✅ |
| **콜 내역** | CallService.kt:getHistory() | 120-150 | call_repository.dart | 100% | 3 | ✅ |
| **통계 계산** | CallHistoryViewModel.kt | 80-120 | ❌ 없음 | 0% | 4C | ⭐⭐⭐⭐ |
| **FCM 토큰 저장** | MainActivity.kt:saveFcmToken() | 310-330 | ❌ 없음 | 0% | 4C | ⭐⭐⭐⭐⭐ |
| **FCM 수신** | MyFirebaseMessagingService.kt | 전체 | ❌ 없음 | 0% | 4C | ⭐⭐⭐⭐⭐ |
| **만보기 센서** | StepCounterService.kt | 전체 | ❌ 없음 | 0% | 6 | ⭐⭐⭐ |
| **집주소 저장** | PreferencesManager.kt | 80-100 | ❌ 없음 | 0% | 5 | ⭐⭐⭐ |
| **Geocoding** | LocationService.kt:getAddress() | 50-80 | location_provider.dart | 50% | 5 | ⭐⭐⭐ |

---

## 4. Phase별 작업 계획 및 의존성

### Phase 4A: UI 골격 완성 (3-4일)

**목표**: 모든 화면 레이아웃 완성 (기능은 하드코딩)

#### 의존성 그래프
```
main.dart
  └─> MainNavigation (생성 필요) ⭐ 시작점
       ├─> HomeScreen (수정 필요)
       │    └─> StepCounterCard (생성 필요)
       │
       ├─> CallHistoryScreen (기존 사용)
       │
       ├─> PointScreen (생성 필요) ⭐ 의존성 없음
       │    ├─> PointCard (생성 필요)
       │    └─> GradeInfoCard (생성 필요)
       │
       └─> ProfileScreen (생성 필요) ⭐ Attribution 의존
```

**작업 순서:**
1. MainNavigation 생성 (다른 모든 화면의 컨테이너)
2. PointScreen + 위젯들 생성 (독립적)
3. ProfileScreen 생성 (Attribution 읽기만)
4. HomeScreen 개선 + StepCounterCard

**패키지 추가:**
```yaml
dependencies:
  url_launcher: ^6.2.2       # 전화 앱 실행
  package_info_plus: ^5.0.1  # 앱 버전 정보
```

**완료 기준:**
- [ ] 4개 탭 전환 가능
- [ ] 모든 화면 레이아웃 존재
- [ ] 빌드 성공
- [ ] 하드코딩 데이터로 UI 표시

---

### Phase 4B: 통합 테스트 (2-3일)

**목표**: 랜딩페이지 연동 및 Firebase 검증

#### 테스트 체크리스트

**1. 랜딩페이지 연동 테스트**
- [ ] QR 코드 스캔
- [ ] APK 다운로드
- [ ] 설치
- [ ] 실행 → Attribution 매칭
- [ ] 사무실명 표시 확인
- [ ] 전화번호 입력 → HomeScreen

**2. Firebase 연동 검증**
- [ ] Firestore 읽기 권한
- [ ] Firestore 쓰기 권한
- [ ] 콜 생성 → 콜매니저에서 확인
- [ ] 콜 취소 → 상태 변경 확인
- [ ] 콜 내역 조회

**3. 기본 플로우**
- [ ] 앱 실행 → 전화번호 입력 → 메인 화면
- [ ] 콜 요청 → Firestore 저장
- [ ] 콜 내역 → 목록 표시
- [ ] 4개 탭 전환
- [ ] 오류 수정

---

### Phase 4C: 기능 단계적 구현 (7-10일)

**목표**: 하드코딩 제거, 실제 기능 구현

#### 구현 순서 및 의존성

**1단계: 포인트 데이터 모델 (1일)**
```
CustomerPoints 모델 생성
  ├─> CustomerGrade enum (이미 있음, 개선)
  ├─> PointTransaction 모델
  └─> Firestore 변환 메서드

의존성: 없음 (독립적)
```

**2단계: PointRepository (1일)**
```
PointRepository 생성
  ├─> getCustomerPoints()
  ├─> createCustomerPoints()
  └─> getPointTransactions()

의존성: CustomerPoints 모델
Firestore 경로: regions/{regionId}/offices/{officeId}/customerPoints/
```

**3단계: PointService 로직 (1-2일)**
```
PointService 생성
  ├─> earnPoints() ⭐ 복잡 (Firestore 트랜잭션)
  ├─> usePoints() ⭐ 복잡 (잔액 확인)
  └─> observeCustomerPoints()

의존성: PointRepository
주의: Firestore runTransaction 사용
```

**4단계: PointProvider (0.5일)**
```
Riverpod Provider 생성
  ├─> pointServiceProvider
  ├─> customerPointsProvider (FutureProvider)
  └─> PointNotifier (StateNotifier)

의존성: PointService
```

**5단계: PointScreen UI 연결 (0.5일)**
```
PointScreen 수정
  ├─> customerPointsProvider 연결
  ├─> 실제 포인트 표시
  └─> 등급/진행률 계산

의존성: PointProvider
```

**6단계: HomeScreen 포인트 통합 (1일)**
```
HomeScreen 수정
  ├─> 상단 포인트 잔액 표시
  └─> CallRequestScreen 포인트 사용 UI

CallRequestScreen 수정
  ├─> 포인트 사용 체크박스
  ├─> Slider (포인트 선택)
  ├─> 할인 금액 계산
  └─> 콜 요청 시 usePoints() 호출

의존성: PointService, CallService
```

**7단계: FCM 기초 설정 (1일)**
```
FirebaseMessagingHandler 생성
  ├─> 권한 요청
  ├─> 토큰 저장 (Firestore)
  └─> 기본 메시지 수신

AndroidManifest.xml 수정
  ├─> 알림 권한 추가
  └─> Service 등록

의존성: firebase_messaging 패키지
Firestore 경로: regions/{regionId}/offices/{officeId}/customerInfo/{phone}
```

**8단계: FCM 메시지 처리 (1일)**
```
FirebaseMessagingHandler 확장
  ├─> DRIVER_ASSIGNED 처리 → 다이얼로그
  ├─> RIDE_COMPLETED 처리 → 포인트 적립 팝업
  └─> CALL_CANCELLED 처리 → 콜 상태 제거

의존성: PointService (포인트 적립), CallProvider
```

**9단계: ProfileScreen 데이터 연결 (0.5일)**
```
CustomerInfo 모델 개선
ProfileRepository 생성
ProfileScreen UI 연결

의존성: FirebaseAuth, Attribution
```

**10단계: 통계 기능 (0.5일)**
```
CallSummary 계산 로직
CallHistoryScreen 통계 카드 추가

의존성: CallRepository
```

#### 총 소요 시간: 7-10일

---

### Phase 5: UI/UX 개선 (1-2주)

**1. 집주소 저장 (0.5일)**
```
PreferencesService 생성 (SharedPreferences)
HomeScreen: 집주소 빠른 입력 버튼
HomeAddressDialog

의존성: shared_preferences 패키지
```

**2. Geocoding 개선 (0.5일)**
```
LocationService 개선
좌표 → 주소 변환 (geocoding 패키지)

의존성: geocoding 패키지
```

**3. 포인트 거래 내역 (1일)**
```
PointHistoryScreen
Transaction 리스트
필터링 (적립/사용)

의존성: PointService
```

**4. 실시간 콜 모니터링 (1일)**
```
CallStatusDialog
실시간 상태 추적 (Stream)
ETA 표시

의존성: CallService (observeCustomerCalls)
```

**5. 로딩/에러 개선 (0.5일)**
```
전역 로딩 인디케이터
에러 메시지 표준화
재시도 로직

의존성: 없음
```

**6. 애니메이션 추가 (0.5일)**
```
페이지 전환 애니메이션
카드 fade-in
포인트 카운터 애니메이션

의존성: flutter_animate 패키지
```

---

### Phase 6: 만보계 (선택적, 2-3주)

**Android 구현 (1주)**
```
1. Native Service (Kotlin)
   └─> StepCounterService.kt 포팅
   └─> MethodChannel/EventChannel

2. Flutter 연결
   └─> StepCounterService (Dart)
   └─> Stream 연결

3. UI 연결
   └─> StepCounterCard 활성화
```

**iOS 구현 (1주)**
```
1. HealthKit 설정
   └─> Info.plist 권한

2. Flutter 연결
   └─> health 패키지

3. UI 연결
   └─> 동일한 StepCounterCard
```

**로컬 DB (sqflite) (0.5주)**
```
1. StepDatabase 생성
2. DailyStepData, WeeklyStepSummary 테이블
3. 통계 계산 로직
```

**통계 화면 (0.5주)**
```
StepDetailBottomSheet
일일/주간/월간 차트
```

---

## 5. 빠른 참조 가이드

### 5.1 Firestore 경로 완전 정리

```
regions/{regionId}/
└── offices/{officeId}/
    ├── calls/{callId}
    │   ├── id: String
    │   ├── phoneNumber: String
    │   ├── currentLocation: String (출발지)
    │   ├── destinationLocation: String (목적지)
    │   ├── timestamp: int (밀리초)
    │   ├── status: String (REQUESTED/ASSIGNED/IN_PROGRESS/COMPLETED/CANCELLED)
    │   ├── driverId: String?
    │   ├── fare: int?
    │   ├── pointsUsed: int (사용한 포인트)
    │   ├── finalFare: int? (할인 후 요금)
    │   ├── discountAmount: int (할인 금액)
    │   ├── customerGrade: String
    │   └── isAppCustomer: bool
    │
    ├── customerPoints/{phoneNumber}
    │   ├── customerId: String (phoneNumber)
    │   ├── phoneNumber: String
    │   ├── currentPoints: int (보유 포인트)
    │   ├── totalEarned: int (총 적립)
    │   ├── totalUsed: int (총 사용)
    │   ├── grade: String (BRONZE/SILVER/GOLD/VIP)
    │   ├── totalCalls: int (총 이용 횟수)
    │   ├── lastUpdated: Timestamp
    │   └── createdAt: Timestamp
    │
    ├── pointTransactions/{transactionId}
    │   ├── id: String
    │   ├── customerId: String
    │   ├── type: String (EARN/USE/EXPIRE/CANCEL/ADMIN)
    │   ├── amount: int (적립: 양수, 사용: 음수)
    │   ├── balance: int (거래 후 잔액)
    │   ├── description: String
    │   ├── callId: String?
    │   ├── fare: int?
    │   ├── grade: String
    │   └── timestamp: Timestamp
    │
    └── customerInfo/{phoneNumber}
        ├── phoneNumber: String
        ├── name: String
        ├── fcmToken: String (FCM 토큰)
        ├── officePhone: String
        ├── bankName: String
        ├── accountNumber: String
        ├── accountHolder: String
        ├── homeAddress: String (집주소)
        └── registeredAt: Timestamp
```

---

### 5.2 등급 시스템 완전 정리

| 등급 | 아이콘 | 적립률 | 필요 횟수 | 색상 |
|------|--------|--------|----------|------|
| BRONZE | 🥉 | 3% | 0회 이상 | 0xFFCD7F32 |
| SILVER | 🥈 | 5% | 10회 이상 | 0xFFC0C0C0 |
| GOLD | 🥇 | 7% | 30회 이상 | 0xFFFFD700 |
| VIP | ⭐ | 9% | 50회 이상 | 0xFFFF6B6B |

**등급 계산 로직:**
```dart
static CustomerGrade fromCallCount(int callCount) {
  if (callCount >= 50) return CustomerGrade.vip;
  if (callCount >= 30) return CustomerGrade.gold;
  if (callCount >= 10) return CustomerGrade.silver;
  return CustomerGrade.bronze;
}
```

**포인트 적립 계산:**
```dart
int calculateEarnPoints(int fare) {
  return (fare * pointRate).toInt();
}

// 예시:
// BRONZE: 10,000원 * 0.03 = 300P
// SILVER: 10,000원 * 0.05 = 500P
// GOLD: 10,000원 * 0.07 = 700P
// VIP: 10,000원 * 0.09 = 900P
```

---

### 5.3 FCM 메시지 타입 정리

| 타입 | 데이터 필드 | 처리 방법 |
|------|------------|----------|
| **DRIVER_ASSIGNED** | callId, driverName, driverPhone, vehicleNumber, driverId | 다이얼로그 표시, 콜 상태 업데이트 |
| **RIDE_COMPLETED** | callId, fare, pointsUsed | 포인트 적립 팝업, 콜 상태 → COMPLETED |
| **CALL_CANCELLED** | callId, cancelReason | 콜 상태 제거, 알림 |

**Flutter 처리 예시:**
```dart
FirebaseMessaging.onMessage.listen((message) {
  final type = message.data['type'];

  switch (type) {
    case 'DRIVER_ASSIGNED':
      showDialog(
        context: context,
        builder: (context) => DriverAssignedDialog(
          driverName: message.data['driverName'],
          driverPhone: message.data['driverPhone'],
          vehicleNumber: message.data['vehicleNumber'],
        ),
      );
      break;

    case 'RIDE_COMPLETED':
      final fare = int.parse(message.data['fare']);
      final pointsUsed = int.parse(message.data['pointsUsed']);

      // 포인트 적립 (백그라운드에서 자동으로 서버가 처리)
      // UI: 팝업만 표시
      showDialog(
        context: context,
        builder: (context) => PointsEarnedDialog(
          fare: fare,
          pointsUsed: pointsUsed,
        ),
      );
      break;

    case 'CALL_CANCELLED':
      // 콜 상태 제거
      ref.read(callProvider.notifier).clearCallStatus();

      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('기사가 운행을 취소했습니다')),
      );
      break;
  }
});
```

---

### 5.4 필수 패키지 목록

#### Phase 4A (UI 골격)
```yaml
dependencies:
  url_launcher: ^6.2.2         # 전화 앱 실행
  package_info_plus: ^5.0.1    # 앱 버전 정보
```

#### Phase 4C (기능 구현)
```yaml
dependencies:
  firebase_messaging: ^14.7.10  # FCM 푸시
  uuid: ^4.0.0                  # UUID 생성 (Transaction ID)
```

#### Phase 5 (UI/UX 개선)
```yaml
dependencies:
  shared_preferences: ^2.2.2    # 집주소 저장
  geocoding: ^2.1.1             # 주소 변환
  flutter_animate: ^4.3.0       # 애니메이션
```

#### Phase 6 (만보계)
```yaml
dependencies:
  # Android
  sensors_plus: ^4.0.0          # 센서 접근 (보조용)
  # iOS
  health: ^10.0.0               # HealthKit
  # 공통
  sqflite: ^2.3.0               # 로컬 DB
```

---

### 5.5 다음 세션 시작 명령어

**재부팅 후:**
```bash
cd C:\app_dev\designated_driver\designated_customer_flutter
code .
```

**첫 작업:**
```
1. 작업내역/Phase4A_UI골격완성_작업계획.md 열기
2. Day 1 시작: MainNavigation 생성
3. 문서의 코드 복사 → 파일 생성
4. 저장 후 빌드
```

---

### 5.6 체크리스트 템플릿

**Phase 4A 완료 체크리스트:**
- [ ] MainNavigation.dart 생성
- [ ] BottomNavigationBar 4탭 작동
- [ ] HomeScreen에 StepCounterCard 추가
- [ ] PointScreen 레이아웃 완성
- [ ] ProfileScreen 레이아웃 완성
- [ ] 4개 탭 전환 확인
- [ ] 빌드 성공
- [ ] 실제 기기 실행 확인

**Phase 4C 완료 체크리스트:**
- [ ] CustomerPoints 모델 생성
- [ ] PointRepository 구현
- [ ] PointService earnPoints() 구현
- [ ] PointService usePoints() 구현
- [ ] PointProvider 생성
- [ ] PointScreen 데이터 연결
- [ ] HomeScreen 포인트 표시
- [ ] CallRequestScreen 포인트 사용 UI
- [ ] FCM 토큰 저장
- [ ] FCM 메시지 수신 (3가지 타입)
- [ ] ProfileScreen 데이터 연결
- [ ] CallHistoryScreen 통계 카드

---

## 📌 중요 참고사항

### 원본 앱과의 차이점
1. **만보기**: 원본은 Foreground Service, Flutter는 Phase 6에서 구현
2. **브로드캐스트**: 원본은 LocalBroadcast, Flutter는 Riverpod 상태 업데이트
3. **Room DB**: 원본은 Room, Flutter는 sqflite (Phase 6)
4. **Compose**: 원본은 Jetpack Compose, Flutter는 Material3

### 호환성 유지 필수
1. **Firestore 경로**: 원본과 100% 동일 (콜매니저 호환)
2. **FCM 메시지 타입**: 동일 (서버 코드 공유)
3. **포인트 계산 로직**: 동일 (등급별 비율)
4. **CustomerCall 필드**: 동일 (19개 필드)

### 테스트 환경
- **Firebase 프로젝트**: calldetector-5d61e
- **대상 기기**: SM G996N (Android 15)
- **최소 SDK**: 21
- **대상 SDK**: 34

---

**작성자**: Claude
**최종 수정**: 2025-11-18
**버전**: 1.0
**다음 작업**: Phase 4A Day 1 시작

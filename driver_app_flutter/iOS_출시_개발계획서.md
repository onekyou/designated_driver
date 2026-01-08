# Flutter 기사 앱 iOS 출시 개발 계획서

**프로젝트**: driver_app_flutter (대리운전 기사 앱)
**현재 완성도**: 35-40% (Domain/Data 85%, Presentation 20%)
**목표**: iOS App Store 출시 준비 완료
**작성일**: 2025-12-29

---

## 📊 현황 분석

### 1. 현재 완성 상태

#### ✅ 완성된 부분 (85%)
- **Clean Architecture 골격**: 완벽하게 구축됨
- **Domain Layer**: 100% (Entity, UseCase, Repository Interface)
- **Data Layer**: 75% (DataSource, Repository Impl, Firebase 연동)
- **Core Infrastructure**: 100% (DI, Error, Network)
- **Auth 기능**: 80% (로그인/로그아웃 작동)

#### ⚠️ 부분 완성 (40-70%)
- **Call Feature**: Domain/Data 완성, Presentation 없음
- **Trip Feature**: Domain/Data 완성, Presentation 없음
- **Driver Feature**: Domain/Data 완성, Presentation 없음
- **Customer Feature**: Domain/Data 완성, Presentation 없음
- **Location Feature**: 구조만, 실제 구현 TODO

#### ❌ 미완성 (0-20%)
- **Presentation Layer**: Provider, State 거의 없음
- **UI Screens**: 3/8만 완성 (로그인, 스플래시, 홈 스텁)
- **Widgets**: 완전히 비어있음
- **Utils**: 완전히 비어있음
- **FCM 푸시 알림**: 없음
- **QR 코드 생성**: 없음

### 2. 네이티브 앱과의 기능 비교

| 기능 | 네이티브 앱 | Flutter 앱 | 격차 |
|------|-----------|-----------|------|
| 로그인/인증 | ✅ 100% | ✅ 80% | 회원가입, 비밀번호 찾기 |
| 콜 배정 수신 | ✅ FCM + Service | ❌ 없음 | FCM, Foreground Service |
| 콜 수락/거절 | ✅ 완성 | ❌ UI 없음 | CallProvider, UI |
| 운행 준비 | ✅ 완성 | ❌ UI 없음 | TripProvider, UI |
| 운행 중 관리 | ✅ 완성 | ❌ UI 없음 | UI |
| 정산 처리 | ✅ 포인트 통합 | ❌ UI 없음 | UI, 포인트 연동 |
| 운행 내역 | ✅ 완성 | ❌ UI 없음 | UI |
| QR 코드 | ✅ ZXing | ❌ 없음 | qr_flutter 패키지 |
| 위치 서비스 | ✅ GPS | ❌ TODO | geolocator |
| 음성 입력 | ✅ 완성 | ❌ 없음 | speech_to_text |
| 주소 검색 | ✅ Kakao API | ❌ 없음 | Kakao API 연동 |

**결론**: 네이티브 앱 대비 약 **60%의 기능이 부족**

### 3. iOS 출시를 위한 필수 기능

#### 절대 필수 (P0)
1. ✅ 로그인/로그아웃
2. ❌ FCM 푸시 알림 (콜 배정)
3. ❌ 콜 수락/거절
4. ❌ 운행 준비 화면
5. ❌ 운행 중 화면
6. ❌ 정산 화면

#### 매우 중요 (P1)
7. ❌ 운행 내역
8. ❌ 위치 서비스 (GPS)
9. ❌ QR 코드 생성
10. ❌ 고객 포인트 조회

#### 중요 (P2)
11. ❌ 회원가입
12. ❌ 음성 입력
13. ❌ 주소 검색 (Kakao)
14. ❌ Foreground Service

---

## 🎯 개발 로드맵

### 타임라인 개요

```
Phase 1: Firebase 설정          [1일]   ✅ Windows 가능
Phase 2: Presentation 기반      [2일]   ✅ Windows 가능
Phase 3: 콜 관리 시스템          [3일]   ✅ Windows 가능
Phase 4: 운행 관리 시스템        [3일]   ✅ Windows 가능
Phase 5: 정산 시스템            [2일]   ✅ Windows 가능
Phase 6: 부가 기능              [2일]   ✅ Windows 가능
Phase 7: iOS 설정 & 빌드        [1일]   ⚠️ Mac 필요
Phase 8: 최종 테스트 & 배포     [2일]   ⚠️ Mac 필요
────────────────────────────────────────────────
총 예상 기간: 16일 (약 3주)
```

---

## 📋 Phase별 상세 계획

---

## Phase 1: Firebase 설정 및 기본 구성 (1일)

### 목표
- Firebase 완전 연동
- iOS 권한 설정
- 필수 패키지 추가

### 작업 내용

#### 1-1. firebase_options.dart 생성
**문제**: 현재 파일이 없음

**해결**:
```bash
# FlutterFire CLI 사용 (Mac 권장)
flutter pub global activate flutterfire_cli
flutterfire configure

# 또는 수동 생성 (고객 앱 참고)
```

**예상 결과**:
```dart
// lib/firebase_options.dart
class DefaultFirebaseOptions {
  static FirebaseOptions get currentPlatform {
    if (kIsWeb) return web;
    switch (defaultTargetPlatform) {
      case TargetPlatform.android: return android;
      case TargetPlatform.iOS: return ios;
      // ...
    }
  }

  static const FirebaseOptions ios = FirebaseOptions(
    apiKey: 'YOUR_IOS_API_KEY',
    appId: 'YOUR_IOS_APP_ID',
    messagingSenderId: 'YOUR_SENDER_ID',
    projectId: 'calldetector-5d61e',  // 고객 앱과 동일 프로젝트
    storageBucket: 'calldetector-5d61e.firebasestorage.app',
    iosBundleId: 'com.designated.driverapp',
  );
}
```

#### 1-2. iOS Info.plist 권한 추가
**파일**: `ios/Runner/Info.plist`

**추가 권한**:
```xml
<!-- 위치 권한 (운행 추적) -->
<key>NSLocationWhenInUseUsageDescription</key>
<string>운행 중 출발지와 목적지 위치를 기록하기 위해 필요합니다.</string>

<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>백그라운드에서도 위치를 추적하여 안전한 운행을 지원합니다.</string>

<!-- 음성 인식 권한 -->
<key>NSMicrophoneUsageDescription</key>
<string>음성으로 주소와 요금을 입력하실 수 있습니다.</string>

<key>NSSpeechRecognitionUsageDescription</key>
<string>음성 인식으로 주소 입력을 도와드립니다.</string>

<!-- 전화 걸기 권한 -->
<key>NSPhotoLibraryUsageDescription</key>
<string>QR 코드를 저장하기 위해 필요합니다.</string>

<!-- FCM 푸시 알림 -->
<key>UIBackgroundModes</key>
<array>
  <string>fetch</string>
  <string>remote-notification</string>
  <string>location</string>
</array>

<!-- 앱 이름 -->
<key>CFBundleDisplayName</key>
<string>대리운전 기사</string>
```

#### 1-3. pubspec.yaml 패키지 추가
**추가 필요한 패키지**:
```yaml
dependencies:
  # 위치 서비스 (현재 주석 처리됨)
  geolocator: ^10.1.0

  # FCM 푸시 알림
  firebase_messaging: ^14.7.9

  # QR 코드 생성
  qr_flutter: ^4.1.0

  # 음성 인식
  speech_to_text: ^6.5.1

  # 전화 걸기
  url_launcher: ^6.2.2

  # 주소 검색 (Kakao)
  dio: ^5.4.0

  # 로컬 알림
  flutter_local_notifications: ^16.3.0
```

#### 1-4. Android AndroidManifest.xml 확인
**확인 사항**:
- ✅ Foreground Service 권한
- ✅ 위치 권한
- ✅ 알림 권한
- ✅ 음성 인식 권한

**추가 필요**:
```xml
<!-- Foreground Service 타입 지정 (Android 14+) -->
<service
    android:name=".DriverForegroundService"
    android:foregroundServiceType="dataSync|location"
    android:exported="false" />
```

### 성공 기준
- [ ] `firebase_options.dart` 파일 생성 완료
- [ ] iOS Info.plist에 4가지 권한 추가
- [ ] pubspec.yaml에 7개 패키지 추가
- [ ] `flutter pub get` 성공

### 예상 시간: 4시간

---

## Phase 2: Presentation 기반 구축 (2일)

### 목표
- Provider 패턴 구현
- State 관리 구조 구축
- 네비게이션 완성

### 작업 내용

#### 2-1. CallProvider 구현
**파일**: `lib/features/call/presentation/providers/call_provider.dart`

**기능**:
```dart
@riverpod
class CallNotifier extends _$CallNotifier {
  @override
  Future<CallState> build() async {
    // 초기 상태 로드
    return const CallState.initial();
  }

  // 배정된 콜 조회
  Future<void> fetchAssignedCall() async {
    state = const AsyncValue.loading();
    final result = await ref.read(getAssignedCallUseCaseProvider).call(NoParams());
    result.fold(
      (failure) => state = AsyncValue.error(failure, StackTrace.current),
      (call) => state = AsyncValue.data(CallState.loaded(call)),
    );
  }

  // 콜 수락
  Future<void> acceptCall(String callId) async { /* ... */ }

  // 콜 거절
  Future<void> rejectCall(String callId, String reason) async { /* ... */ }
}

@freezed
class CallState with _$CallState {
  const factory CallState.initial() = _Initial;
  const factory CallState.loading() = _Loading;
  const factory CallState.loaded(Call? call) = _Loaded;
  const factory CallState.error(String message) = _Error;
}
```

#### 2-2. TripProvider 구현
**파일**: `lib/features/trip/presentation/providers/trip_provider.dart`

**기능**:
```dart
@riverpod
class TripNotifier extends _$TripNotifier {
  // 운행 시작
  Future<void> startTrip(StartTripParams params) async { /* ... */ }

  // 운행 완료
  Future<void> completeTrip(String tripId) async { /* ... */ }

  // 정산 처리
  Future<void> settleTrip(SettleTripParams params) async { /* ... */ }
}
```

#### 2-3. DriverProvider 구현
**파일**: `lib/features/driver/presentation/providers/driver_provider.dart`

**기능**:
```dart
@riverpod
class DriverNotifier extends _$DriverNotifier {
  // 기사 상태 조회
  Future<void> fetchDriverStatus() async { /* ... */ }

  // 상태 변경 (ONLINE, WAITING, ON_TRIP, etc.)
  Future<void> updateStatus(DriverStatus status) async { /* ... */ }
}
```

#### 2-4. LocationProvider 구현
**파일**: `lib/features/location/presentation/providers/location_provider.dart`

**기능**:
```dart
@riverpod
class LocationNotifier extends _$LocationNotifier {
  // 현재 위치 조회
  Future<Position> getCurrentPosition() async {
    final result = await ref.read(getCurrentLocationUseCaseProvider).call(NoParams());
    return result.fold(
      (failure) => throw Exception(failure.message),
      (position) => position,
    );
  }

  // 주소 → 좌표
  Future<Position> getCoordinatesFromAddress(String address) async { /* ... */ }

  // 좌표 → 주소
  Future<String> getAddressFromCoordinates(double lat, double lng) async { /* ... */ }
}
```

#### 2-5. 네비게이션 완성
**파일**: `lib/core/navigation/app_router.dart`

**라우트 추가**:
```dart
@riverpod
GoRouter goRouter(GoRouterRef ref) {
  return GoRouter(
    initialLocation: '/splash',
    routes: [
      GoRoute(path: '/splash', builder: (context, state) => const SplashScreen()),
      GoRoute(path: '/login', builder: (context, state) => const LoginScreen()),

      // 메인 화면들
      GoRoute(path: '/home', builder: (context, state) => const HomeScreen()),
      GoRoute(path: '/call-details/:callId', builder: (context, state) {
        final callId = state.pathParameters['callId']!;
        return CallDetailsScreen(callId: callId);
      }),
      GoRoute(path: '/history', builder: (context, state) => const HistorySettlementScreen()),
      GoRoute(path: '/qr', builder: (context, state) => const ReferralQRScreen()),

      // 인증 화면들
      GoRoute(path: '/signup', builder: (context, state) => const SignUpScreen()),
      GoRoute(path: '/forgot-password', builder: (context, state) => const ForgotPasswordScreen()),
    ],
  );
}
```

### 성공 기준
- [ ] 4개 Provider 구현 완료 (Call, Trip, Driver, Location)
- [ ] Freezed로 State 클래스 생성
- [ ] GoRouter에 모든 라우트 추가
- [ ] 빌드 에러 없음

### 예상 시간: 2일 (16시간)

---

## Phase 3: 콜 관리 시스템 구현 (3일)

### 목표
- 콜 배정 수신 (FCM)
- 콜 수락/거절 UI
- 콜 상세 정보 화면

### 작업 내용

#### 3-1. FCM 서비스 구현
**파일**: `lib/services/fcm_service.dart`

**기능**:
```dart
class FcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;

  Future<void> initialize({
    required String regionId,
    required String officeId,
    required String driverId,
  }) async {
    // 권한 요청
    await _messaging.requestPermission();

    // FCM 토큰 가져오기
    final token = await _messaging.getToken();

    // Firestore에 토큰 저장
    await FirebaseFirestore.instance
        .collection('regions/$regionId/offices/$officeId/designated_drivers')
        .doc(driverId)
        .update({'fcmToken': token});

    // 토큰 갱신 리스너
    _messaging.onTokenRefresh.listen((newToken) {
      // Firestore 업데이트
    });
  }

  // Foreground 메시지 리스너
  void listenToForegroundMessages(void Function(RemoteMessage) onMessage) {
    FirebaseMessaging.onMessage.listen(onMessage);
  }

  // Background 메시지 핸들러
  static Future<void> backgroundMessageHandler(RemoteMessage message) async {
    // 새 콜 배정 알림 표시
  }
}
```

**main.dart 수정**:
```dart
@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  await FcmService.backgroundMessageHandler(message);
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);

  // Background handler 등록
  FirebaseMessaging.onBackgroundMessage(_firebaseMessagingBackgroundHandler);

  runApp(const MyApp());
}
```

#### 3-2. HomeScreen 완성 (상태별 화면 전환)
**파일**: `lib/screens/home_screen.dart`

**구조**:
```dart
class HomeScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final driverState = ref.watch(driverNotifierProvider);
    final callState = ref.watch(callNotifierProvider);

    return Scaffold(
      appBar: _buildAppBar(context, ref),
      body: driverState.when(
        data: (driver) {
          // 기사 상태에 따라 화면 전환
          switch (driver.status) {
            case DriverStatus.online:
            case DriverStatus.waiting:
              return _WaitingScreen();
            case DriverStatus.assigned:
              return _CallAssignedPopup();
            case DriverStatus.accepted:
              return _TripPreparationScreen();
            case DriverStatus.onTrip:
              return _InProgressScreen();
            default:
              return _ErrorScreen();
          }
        },
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => _ErrorScreen(message: error.toString()),
      ),
    );
  }
}
```

#### 3-3. 신규 콜 배정 팝업
**파일**: `lib/screens/home_screen.dart` 내부

**UI 구성**:
```dart
class _CallAssignedPopup extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final call = ref.watch(callNotifierProvider).value;

    return AlertDialog(
      title: const Text('신규 콜 배정'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // 고객 정보
          Text('고객: ${call?.customerName}'),
          Text('전화: ${call?.phoneNumber}'),
          Text('출발: ${call?.customerAddress}'),
          Text('도착: ${call?.destination}'),

          // 공유콜 배지
          if (call?.callType == 'SHARED')
            const Chip(
              label: Text('공유콜'),
              backgroundColor: Colors.green,
            ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => ref.read(callNotifierProvider.notifier).rejectCall(call!.id, '기타'),
          child: const Text('거절'),
        ),
        ElevatedButton(
          onPressed: () => ref.read(callNotifierProvider.notifier).acceptCall(call!.id),
          child: const Text('수락'),
        ),
      ],
    );
  }
}
```

#### 3-4. CallDetailsScreen 구현
**파일**: `lib/screens/call_details_screen.dart`

**기능**:
- 콜 상세 정보 표시
- 고객 전화 걸기 버튼
- 콜 상태 타임라인
- 경로 정보

### 성공 기준
- [ ] FCM 푸시 알림 수신 작동
- [ ] 신규 콜 팝업 표시
- [ ] 수락/거절 버튼 작동
- [ ] HomeScreen 상태별 화면 전환
- [ ] CallDetailsScreen 완성

### 예상 시간: 3일 (24시간)

---

## Phase 4: 운행 관리 시스템 구현 (3일)

### 목표
- 운행 준비 화면
- 운행 중 화면
- 위치 서비스 통합

### 작업 내용

#### 4-1. TripPreparationScreen 구현
**파일**: `lib/screens/trip_preparation_screen.dart`

**UI 구성**:
```dart
class TripPreparationScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<TripPreparationScreen> createState() => _TripPreparationScreenState();
}

class _TripPreparationScreenState extends ConsumerState<TripPreparationScreen> {
  final _departureController = TextEditingController();
  final _destinationController = TextEditingController();
  final _waypointsController = TextEditingController();
  final _fareController = TextEditingController();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('운행 준비')),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            children: [
              // 출발지 입력
              _buildDepartureField(),

              // 도착지 입력
              _buildDestinationField(),

              // 경유지 입력
              _buildWaypointsField(),

              // 요금 입력
              _buildFareField(),

              // 고객 전화 버튼
              _buildCallCustomerButton(),

              // 운행 시작 버튼
              ElevatedButton(
                onPressed: _startTrip,
                child: const Text('운행 시작'),
              ),

              // 취소 버튼
              OutlinedButton(
                onPressed: _cancelCall,
                child: const Text('콜 취소'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildDepartureField() {
    return Row(
      children: [
        Expanded(
          child: TextField(
            controller: _departureController,
            decoration: const InputDecoration(
              labelText: '출발지',
              prefixIcon: Icon(Icons.my_location),
            ),
          ),
        ),
        // 현재 위치 버튼
        IconButton(
          icon: const Icon(Icons.gps_fixed),
          onPressed: _loadCurrentLocation,
        ),
        // 음성 입력 버튼
        IconButton(
          icon: const Icon(Icons.mic),
          onPressed: () => _startVoiceInput('departure'),
        ),
      ],
    );
  }

  Future<void> _loadCurrentLocation() async {
    final position = await ref.read(locationNotifierProvider.notifier).getCurrentPosition();
    final address = await ref.read(locationNotifierProvider.notifier)
        .getAddressFromCoordinates(position.latitude, position.longitude);
    setState(() {
      _departureController.text = address;
    });
  }

  Future<void> _startVoiceInput(String field) async {
    // speech_to_text 패키지 사용
  }

  Future<void> _startTrip() async {
    await ref.read(tripNotifierProvider.notifier).startTrip(
      StartTripParams(
        callId: widget.callId,
        departure: _departureController.text,
        destination: _destinationController.text,
        waypoints: _waypointsController.text,
        fare: int.parse(_fareController.text),
      ),
    );

    // 운행 중 화면으로 전환
  }
}
```

#### 4-2. InProgressScreen 구현
**파일**: `lib/screens/in_progress_screen.dart`

**UI 구성**:
```dart
class InProgressScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trip = ref.watch(tripNotifierProvider).value;

    return Scaffold(
      appBar: AppBar(title: const Text('운행 중')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            // 운행 정보 카드
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('출발지: ${trip?.departure}'),
                    Text('도착지: ${trip?.destination}'),
                    if (trip?.waypoints != null)
                      Text('경유지: ${trip?.waypoints}'),
                    Text('예상 요금: ${trip?.fare}원'),
                  ],
                ),
              ),
            ),

            const Spacer(),

            // 운행 완료 버튼
            ElevatedButton(
              onPressed: () => _completeTrip(context, ref),
              child: const Text('운행 완료'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _completeTrip(BuildContext context, WidgetRef ref) async {
    await ref.read(tripNotifierProvider.notifier).completeTrip(trip.id);
    // 정산 화면으로 전환
  }
}
```

#### 4-3. Location 실제 구현
**파일**: `lib/features/location/data/datasources/location_remote_data_source_impl.dart`

**geolocator 통합**:
```dart
class LocationRemoteDataSourceImpl implements LocationRemoteDataSource {
  @override
  Future<Position> getCurrentPosition() async {
    // 권한 확인
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }

    // 현재 위치 조회
    return await Geolocator.getCurrentPosition(
      desiredAccuracy: LocationAccuracy.high,
    );
  }

  @override
  Future<Position> getCoordinatesFromAddress(String address) async {
    // Geocoding API 연동 (Kakao 또는 Google)
    final response = await dio.get(
      'https://dapi.kakao.com/v2/local/search/address.json',
      queryParameters: {'query': address},
      options: Options(headers: {'Authorization': 'KakaoAK YOUR_REST_API_KEY'}),
    );

    final data = response.data['documents'][0];
    return Position(
      latitude: double.parse(data['y']),
      longitude: double.parse(data['x']),
      // ...
    );
  }

  @override
  Future<String> getAddressFromCoordinates(double latitude, double longitude) async {
    // Reverse Geocoding API
    final response = await dio.get(
      'https://dapi.kakao.com/v2/local/geo/coord2address.json',
      queryParameters: {'x': longitude, 'y': latitude},
      options: Options(headers: {'Authorization': 'KakaoAK YOUR_REST_API_KEY'}),
    );

    return response.data['documents'][0]['address']['address_name'];
  }
}
```

### 성공 기준
- [ ] TripPreparationScreen 완성
- [ ] 현재 위치 버튼 작동
- [ ] 음성 입력 작동
- [ ] 운행 시작 성공
- [ ] InProgressScreen 표시
- [ ] 운행 완료 성공
- [ ] Location 서비스 실제 작동

### 예상 시간: 3일 (24시간)

---

## Phase 5: 정산 시스템 구현 (2일)

### 목표
- 정산 팝업 구현
- 포인트 시스템 통합
- 운행 내역 화면

### 작업 내용

#### 5-1. SettlementSummaryPopup 구현
**파일**: `lib/widgets/settlement_summary_popup.dart`

**UI 구성**:
```dart
class SettlementSummaryPopup extends ConsumerStatefulWidget {
  final Trip trip;

  @override
  ConsumerState<SettlementSummaryPopup> createState() => _SettlementSummaryPopupState();
}

class _SettlementSummaryPopupState extends ConsumerState<SettlementSummaryPopup> {
  late int _fare;
  int _pointsUsed = 0;
  String _paymentMethod = 'cash';
  int? _cashAmount;

  @override
  void initState() {
    super.initState();
    _fare = widget.trip.fare;
    _loadCustomerPoints();
  }

  Future<void> _loadCustomerPoints() async {
    final call = ref.read(callNotifierProvider).value;
    if (call?.isAppCustomer == true) {
      final points = await ref.read(customerNotifierProvider.notifier)
          .getCustomerPoints(call!.phoneNumber);
      setState(() {
        // 포인트 정보 표시
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('정산'),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            // 운행 정보 요약
            _buildTripSummary(),

            // 요금 수정
            _buildFareEdit(),

            // 앱 회원 포인트 카드
            if (widget.trip.isAppCustomer)
              _buildPointsCard(),

            // 결제 방법 선택
            _buildPaymentMethodSelector(),

            // 현금+포인트 입력
            if (_paymentMethod == 'cash_and_points')
              _buildCashAmountInput(),

            // 최종 결제 금액
            _buildFinalAmount(),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: const Text('취소'),
        ),
        ElevatedButton(
          onPressed: _settleTrip,
          child: const Text('정산 완료'),
        ),
      ],
    );
  }

  Widget _buildPointsCard() {
    return Card(
      color: Colors.green.shade50,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          children: [
            const Text('앱 회원 포인트', style: TextStyle(fontWeight: FontWeight.bold)),
            Text('보유: ${_customerPoints?.currentPoints ?? 0}P'),
            Text('등급: ${_customerPoints?.grade ?? "BRONZE"}'),
            TextField(
              decoration: const InputDecoration(labelText: '사용 포인트'),
              keyboardType: TextInputType.number,
              onChanged: (value) {
                setState(() {
                  _pointsUsed = int.tryParse(value) ?? 0;
                });
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _settleTrip() async {
    await ref.read(tripNotifierProvider.notifier).settleTrip(
      SettleTripParams(
        tripId: widget.trip.id,
        fare: _fare,
        paymentMethod: _paymentMethod,
        pointsUsed: _pointsUsed,
        cashAmount: _cashAmount,
      ),
    );

    Navigator.pop(context);
    // 완료 메시지
  }
}
```

#### 5-2. HistorySettlementScreen 구현
**파일**: `lib/screens/history_settlement_screen.dart`

**UI 구성**:
```dart
class HistorySettlementScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final trips = ref.watch(tripHistoryProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('운행 내역 & 정산')),
      body: Column(
        children: [
          // 정산 요약 카드
          _buildSettlementSummaryCard(trips),

          // 운행 내역 리스트
          Expanded(
            child: ListView.builder(
              itemCount: trips.length,
              itemBuilder: (context, index) {
                final trip = trips[index];
                return _buildTripItem(trip);
              },
            ),
          ),

          // 정산 저장 버튼
          Padding(
            padding: const EdgeInsets.all(16),
            child: ElevatedButton(
              onPressed: () => _saveSettlement(context, ref, trips),
              child: const Text('정산 내역 저장'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSettlementSummaryCard(List<Trip> trips) {
    final totalFare = trips.fold<int>(0, (sum, trip) => sum + trip.fare);
    final totalDeposit = (totalFare * 0.3).toInt(); // 30% 납입
    final totalCredit = trips
        .where((t) => t.paymentMethod != 'cash')
        .fold<int>(0, (sum, trip) => sum + trip.fare);
    final realIncome = totalFare - totalDeposit;

    return Card(
      margin: const EdgeInsets.all(16),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text('총 운행: ${trips.length}건'),
            Text('총 요금: ${totalFare}원'),
            Text('총 납입: ${totalDeposit}원'),
            Text('총 외상: ${totalCredit}원'),
            Text('실 수입: ${realIncome}원',
                 style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
          ],
        ),
      ),
    );
  }
}
```

#### 5-3. CustomerProvider 구현
**파일**: `lib/features/customer/presentation/providers/customer_provider.dart`

**기능**:
```dart
@riverpod
class CustomerNotifier extends _$CustomerNotifier {
  // 포인트 조회 (캐시 포함)
  Future<CustomerPoints?> getCustomerPoints(String phoneNumber) async {
    final result = await ref.read(getCustomerPointsUseCaseProvider).call(phoneNumber);
    return result.fold(
      (failure) => null,
      (points) => points,
    );
  }
}
```

### 성공 기준
- [ ] SettlementSummaryPopup 완성
- [ ] 포인트 조회 및 사용 작동
- [ ] 결제 방법 선택 작동
- [ ] 정산 완료 성공
- [ ] HistorySettlementScreen 완성
- [ ] 정산 요약 계산 정확

### 예상 시간: 2일 (16시간)

---

## Phase 6: 부가 기능 구현 (2일)

### 목표
- QR 코드 생성
- 회원가입/비밀번호 찾기
- 음성 입력
- Widgets 라이브러리

### 작업 내용

#### 6-1. ReferralQRScreen 구현
**파일**: `lib/screens/referral_qr_screen.dart`

**기능**:
```dart
class ReferralQRScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final driver = ref.watch(driverNotifierProvider).value;
    final qrUrl = driver?.referralQrUrl ?? '';

    return Scaffold(
      appBar: AppBar(title: const Text('고객 추천하기')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // QR 코드 생성
            QrImageView(
              data: qrUrl,
              version: QrVersions.auto,
              size: 300.0,
            ),

            const SizedBox(height: 24),

            // URL 표시
            Text(
              qrUrl,
              style: const TextStyle(fontSize: 12),
              textAlign: TextAlign.center,
            ),

            const SizedBox(height: 16),

            // 추천 통계 (준비중)
            const Card(
              child: Padding(
                padding: EdgeInsets.all(16),
                child: Text('추천 통계 준비 중...'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
```

#### 6-2. SignUpScreen 구현
**파일**: `lib/screens/signup_screen.dart`

**기능**:
- 이메일, 비밀번호, 이름, 전화번호 입력
- Firebase Auth 회원가입
- Firestore에 기사 정보 저장 (approvalStatus: PENDING)
- 승인 대기 안내

#### 6-3. ForgotPasswordScreen 구현
**파일**: `lib/screens/forgot_password_screen.dart`

**기능**:
- 이메일 입력
- Firebase Auth `sendPasswordResetEmail` 호출
- 성공 메시지 표시

#### 6-4. VoiceInputHelper 구현
**파일**: `lib/utils/voice_input_helper.dart`

**기능**:
```dart
class VoiceInputHelper {
  final SpeechToText _speechToText = SpeechToText();

  Future<bool> initialize() async {
    return await _speechToText.initialize();
  }

  Future<String?> startListening() async {
    if (!await _speechToText.initialize()) {
      return null;
    }

    final completer = Completer<String?>();

    _speechToText.listen(
      onResult: (result) {
        if (result.finalResult) {
          completer.complete(result.recognizedWords);
        }
      },
      localeId: 'ko_KR',
    );

    return completer.future;
  }

  // 한국어 숫자 변환 (예: "만오천" → "15000")
  String convertKoreanNumberToDigits(String text) {
    // TODO: 네이티브 앱 로직 참고
  }
}
```

#### 6-5. Widgets 라이브러리 구축
**파일**: `lib/widgets/`

**생성할 위젯**:
- `custom_text_field.dart`: 재사용 가능한 입력 필드
- `loading_overlay.dart`: 로딩 오버레이
- `error_dialog.dart`: 에러 다이얼로그
- `call_info_card.dart`: 콜 정보 카드
- `trip_info_card.dart`: 운행 정보 카드

### 성공 기준
- [ ] ReferralQRScreen QR 코드 생성
- [ ] SignUpScreen 회원가입 작동
- [ ] ForgotPasswordScreen 이메일 전송
- [ ] VoiceInputHelper 음성 인식 작동
- [ ] 5개 재사용 위젯 생성

### 예상 시간: 2일 (16시간)

---

## Phase 7: iOS 설정 & 빌드 (1일) - **Mac 필요**

### 목표
- iOS 빌드 환경 완성
- CocoaPods 설치
- Debug/Release 빌드 성공

### 작업 내용

#### 7-1. CocoaPods 설치
```bash
cd ios
pod install
cd ..
```

#### 7-2. Xcode 설정
1. Xcode에서 `ios/Runner.xcworkspace` 열기
2. Signing & Capabilities 설정
   - Team 선택
   - Bundle Identifier: `com.designated.driverapp`
   - Push Notifications 추가
   - Background Modes 추가 (Remote notifications, Location updates, Background fetch)

3. GoogleService-Info.plist 확인
   - Target Membership 체크

#### 7-3. Debug 빌드 테스트
```bash
flutter build ios --debug
```

#### 7-4. 시뮬레이터 테스트
```bash
flutter run
```

**테스트 체크리스트**:
- [ ] 로그인 성공
- [ ] FCM 토큰 등록
- [ ] 위치 권한 요청
- [ ] 음성 인식 권한 요청
- [ ] 알림 권한 요청

### 성공 기준
- [ ] CocoaPods 설치 성공
- [ ] Xcode 빌드 성공
- [ ] 시뮬레이터 실행 성공
- [ ] 모든 권한 요청 정상 작동

### 예상 시간: 1일 (8시간)

---

## Phase 8: 최종 테스트 & 배포 (2일) - **Mac 필요**

### 목표
- 전체 플로우 테스트
- Release 빌드
- TestFlight 업로드

### 작업 내용

#### 8-1. 전체 플로우 테스트
**시나리오**:
1. 로그인
2. FCM 푸시 알림으로 콜 배정 수신
3. 콜 수락
4. 운행 준비 (출발지/도착지/요금 입력)
5. 운행 시작
6. 운행 완료
7. 정산 처리 (포인트 사용)
8. 운행 내역 확인

#### 8-2. Release 빌드
**Android**:
```bash
# Keystore 생성 (Phase 5 참고)
flutter build appbundle --release
```

**iOS**:
1. Xcode에서 Archive 생성
2. App Store Connect 업로드

#### 8-3. TestFlight 배포
1. App Store Connect에서 빌드 확인
2. 베타 테스터 추가
3. TestFlight 베타 테스트

### 성공 기준
- [ ] 전체 플로우 테스트 통과
- [ ] Android AAB 생성 성공
- [ ] iOS Archive 생성 성공
- [ ] TestFlight 업로드 성공

### 예상 시간: 2일 (16시간)

---

## 🎯 Windows vs Mac 작업 구분

### ✅ Windows에서 가능 (Phase 1-6)
- Firebase 설정
- Dart 코드 작성 (Providers, Screens, Widgets)
- Android 빌드 및 테스트
- firebase_options.dart 수동 생성 가능 (고객 앱 참고)

### ⚠️ Mac 필수 (Phase 7-8)
- iOS 빌드
- CocoaPods 설치
- Xcode Archive
- TestFlight 업로드

---

## 📊 진행 상황 체크리스트

### Phase 1: Firebase 설정 (1일)
- [ ] firebase_options.dart 생성
- [ ] iOS Info.plist 권한 추가
- [ ] pubspec.yaml 패키지 추가
- [ ] flutter pub get 성공

### Phase 2: Presentation 기반 (2일)
- [ ] CallProvider 구현
- [ ] TripProvider 구현
- [ ] DriverProvider 구현
- [ ] LocationProvider 구현
- [ ] GoRouter 완성

### Phase 3: 콜 관리 (3일)
- [ ] FcmService 구현
- [ ] HomeScreen 상태별 전환
- [ ] CallAssignedPopup 구현
- [ ] CallDetailsScreen 구현
- [ ] FCM 푸시 수신 테스트

### Phase 4: 운행 관리 (3일)
- [ ] TripPreparationScreen 구현
- [ ] InProgressScreen 구현
- [ ] Location 실제 구현 (geolocator)
- [ ] 음성 입력 구현
- [ ] 운행 플로우 테스트

### Phase 5: 정산 (2일)
- [ ] SettlementSummaryPopup 구현
- [ ] HistorySettlementScreen 구현
- [ ] 포인트 조회/사용 구현
- [ ] 정산 계산 정확성 테스트

### Phase 6: 부가 기능 (2일)
- [ ] ReferralQRScreen 구현
- [ ] SignUpScreen 구현
- [ ] ForgotPasswordScreen 구현
- [ ] VoiceInputHelper 구현
- [ ] Widgets 라이브러리 구축

### Phase 7: iOS 빌드 (1일) - Mac
- [ ] CocoaPods 설치
- [ ] Xcode 설정
- [ ] Debug 빌드 성공
- [ ] 시뮬레이터 테스트

### Phase 8: 배포 (2일) - Mac
- [ ] 전체 플로우 테스트
- [ ] Release 빌드
- [ ] TestFlight 업로드

---

## 🚨 위험 요소 및 대응

### 1. 개발 기간 초과 위험 (높음)
**원인**: Presentation Layer가 거의 비어있음 (60% 이상 구현 필요)

**대응**:
- UI 재사용 컴포넌트 먼저 구축
- 네이티브 앱 코드 적극 참고
- 필수 기능 우선 (P0 → P1 → P2)

### 2. FCM 푸시 알림 문제 (중간)
**원인**: iOS Background 제약, APNs 인증서

**대응**:
- Firebase Console에서 APNs 인증 키 미리 등록
- Background Modes 권한 정확히 설정
- 테스트 충분히 수행

### 3. Location 서비스 문제 (중간)
**원인**: iOS 위치 권한 정책 엄격

**대응**:
- Info.plist 권한 설명 명확히 작성
- Always 권한 신중히 요청
- 백그라운드 위치 추적 필수성 검토

### 4. Kakao API 연동 (낮음)
**원인**: REST API 키 필요

**대응**:
- Kakao Developers에서 앱 등록
- REST API 키 발급
- 환경 변수로 관리 (.env)

---

## 💡 권장 사항

### 1. 네이티브 앱 코드 적극 활용
- UI 레이아웃, 색상, 텍스트 그대로 복사
- 비즈니스 로직 Dart로 변환
- 시간 절약 가능

### 2. 테스트 주도 개발
- Provider 먼저 작성 → 테스트 → UI 구현
- 각 Phase 완료 시 통합 테스트
- 버그 조기 발견

### 3. 점진적 기능 추가
- MVP (Minimum Viable Product) 먼저 완성
- P0 기능 완성 → 배포
- P1, P2 기능은 업데이트로 추가

### 4. 코드 생성 도구 활용
- Freezed: 불변 객체 자동 생성
- Riverpod Generator: Provider 자동 생성
- build_runner: 코드 생성 자동화

---

## 📚 참고 자료

### 네이티브 앱
- `C:\app_dev\designated_driver\driver_app`
- 모든 UI, 로직, 구조 참고 가능

### 고객 앱 Flutter
- `C:\app_dev\designated_driver\designated_customer_flutter`
- FCM, Location, Firebase 연동 참고

### 문서
- `작업내역/` 폴더: 포인트 시스템, Attribution 등
- Firebase Console: Firestore 규칙, Functions

---

## 🎯 최종 목표

**iOS App Store 출시 준비 완료**

1. ⏳ Phase 1-6: Windows에서 완료 (13일)
2. ⏳ Phase 7-8: Mac에서 완료 (3일)
3. ⏳ TestFlight 베타 테스트
4. ⏳ App Store 제출

**예상 총 소요 시간**: 16일 (약 3주)

---

**작성일**: 2025-12-29
**작성자**: Claude Code
**프로젝트**: driver_app_flutter v1.0.0

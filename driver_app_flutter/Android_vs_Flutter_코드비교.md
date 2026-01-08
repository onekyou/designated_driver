# Android Kotlin 앱 vs Flutter 앱 상세 비교

## 📂 프로젝트 구조 비교

### Android 앱 (driver_app)
```
driver_app/app/src/main/java/com/designated/driver/
├── MainActivity.kt                              # 앱 진입점
├── DriverApplication.kt                         # Hilt Application
│
├── ui/                                          # UI 레이어
│   ├── home/
│   │   ├── HomeScreen.kt                        # Compose UI
│   │   └── HomeViewModel.kt                     # ViewModel
│   ├── call/
│   │   ├── CallAssignedScreen.kt
│   │   └── CallViewModel.kt
│   └── trip/
│       ├── TripPreparationScreen.kt
│       ├── InProgressScreen.kt
│       └── TripViewModel.kt
│
├── data/                                        # Data 레이어
│   ├── repository/
│   │   ├── CallRepositoryImpl.kt
│   │   └── TripRepositoryImpl.kt
│   ├── remote/
│   │   └── FirestoreDataSource.kt
│   └── model/
│       ├── CallDto.kt
│       └── TripDto.kt
│
├── domain/                                      # Domain 레이어
│   ├── repository/
│   │   ├── CallRepository.kt (interface)
│   │   └── TripRepository.kt (interface)
│   ├── usecase/
│   │   ├── GetAssignedCallUseCase.kt
│   │   └── AcceptCallUseCase.kt
│   └── model/
│       ├── Call.kt (data class)
│       └── Trip.kt (data class)
│
└── service/
    ├── MyFirebaseMessagingService.kt            # FCM
    └── LocationTrackingService.kt               # 위치 추적
```

### Flutter 앱 (driver_app_flutter)
```
driver_app_flutter/lib/
├── main.dart                                    # 앱 진입점 + FCM 백그라운드
│
├── screens/                                     # UI 레이어 (단순)
│   ├── home_screen_new.dart                     # 3개 화면 통합
│   ├── login_screen.dart
│   └── splash_screen.dart
│
├── features/                                    # Feature 기반 구조
│   ├── call/
│   │   ├── domain/
│   │   │   ├── entities/
│   │   │   │   └── call.dart                    # Freezed entity
│   │   │   ├── repositories/
│   │   │   │   └── call_repository.dart (abstract)
│   │   │   └── usecases/
│   │   │       ├── get_assigned_call_usecase.dart
│   │   │       └── accept_call_usecase.dart
│   │   ├── data/
│   │   │   ├── repositories/
│   │   │   │   └── call_repository_impl.dart
│   │   │   ├── datasources/
│   │   │   │   └── call_remote_datasource.dart
│   │   │   └── models/
│   │   │       └── call_model.dart
│   │   └── presentation/
│   │       └── providers/
│   │           ├── call_state.dart              # Freezed state
│   │           └── call_notifier.dart           # Riverpod notifier
│   │
│   ├── trip/
│   │   └── (동일 구조)
│   │
│   ├── driver/
│   │   └── (동일 구조)
│   │
│   └── auth/
│       └── (동일 구조)
│
├── services/                                    # 공통 서비스
│   ├── fcm_service.dart                         # FCM
│   └── location_service.dart (예정)             # 위치 추적
│
└── core/                                        # 공통 유틸
    ├── di/
    │   └── injection.dart                       # GetIt DI
    ├── routes.dart                              # 네비게이션
    └── theme.dart                               # 테마
```

---

## 🔄 상태 관리 비교

### 1. 콜 상태 정의

#### Android (Kotlin)
```kotlin
// CallViewModel.kt
sealed class CallUiState {
    object Initial : CallUiState()
    object Loading : CallUiState()
    data class Loaded(val call: Call?) : CallUiState()
    data class Error(val message: String) : CallUiState()
}

class CallViewModel @Inject constructor(
    private val getAssignedCallUseCase: GetAssignedCallUseCase,
    private val acceptCallUseCase: AcceptCallUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CallUiState>(CallUiState.Initial)
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    fun fetchAssignedCall() {
        viewModelScope.launch {
            _uiState.value = CallUiState.Loading

            when (val result = getAssignedCallUseCase()) {
                is Result.Success -> {
                    _uiState.value = CallUiState.Loaded(result.data)
                }
                is Result.Error -> {
                    _uiState.value = CallUiState.Error(result.message)
                }
            }
        }
    }

    fun acceptCall(callId: String) {
        viewModelScope.launch {
            acceptCallUseCase(callId)
        }
    }
}
```

#### Flutter (Dart)
```dart
// call_state.dart
@freezed
class CallState with _$CallState {
  const factory CallState.initial() = _Initial;
  const factory CallState.loading() = _Loading;
  const factory CallState.loaded(Call? assignedCall) = _Loaded;
  const factory CallState.error(String message) = _Error;
}

// call_notifier.dart
class CallNotifier extends StateNotifier<CallState> {
  final GetAssignedCallUseCase _getAssignedCallUseCase;
  final AcceptCallUseCase _acceptCallUseCase;

  CallNotifier({
    required GetAssignedCallUseCase getAssignedCallUseCase,
    required AcceptCallUseCase acceptCallUseCase,
  }) : _getAssignedCallUseCase = getAssignedCallUseCase,
       _acceptCallUseCase = acceptCallUseCase,
       super(const CallState.initial());

  Future<void> fetchAssignedCall() async {
    state = const CallState.loading();

    final result = await _getAssignedCallUseCase(NoParams());

    result.fold(
      (failure) => state = CallState.error(failure.message),
      (call) => state = CallState.loaded(call),
    );
  }

  Future<void> acceptCall(String callId) async {
    final result = await _acceptCallUseCase(
      AcceptCallParams(callId: callId)
    );

    result.fold(
      (failure) => state = CallState.error(failure.message),
      (call) => state = CallState.loaded(call),
    );
  }
}

// Provider 정의
final callNotifierProvider =
    StateNotifierProvider<CallNotifier, CallState>((ref) {
  return CallNotifier(
    getAssignedCallUseCase: sl(),
    acceptCallUseCase: sl(),
  );
});
```

**주요 차이:**
- Android: `StateFlow` + `MutableStateFlow`
- Flutter: `StateNotifier` + Riverpod `StateNotifierProvider`
- Android: sealed class로 상태 정의
- Flutter: Freezed annotation으로 자동 생성

---

## 🎨 UI 작성 비교

### 2. 콜 대기 화면

#### Android (Jetpack Compose)
```kotlin
// HomeScreen.kt
@Composable
fun WaitingScreen(
    onRefreshClick: () -> Unit,
    onQRClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = Color(0xFFFFB000),
                modifier = Modifier.size(80.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "새로운 콜을 기다리고 있습니다...",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onRefreshClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFFB000)
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("배차 확인")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onQRClick,
                border = BorderStroke(1.dp, Color(0xFFFFB000))
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("고객 추천하기")
            }
        }
    }
}

// HomeViewModel에서 사용
@Composable
fun HomeRoute(
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    HomeScreen(
        uiState = uiState,
        onRefresh = { viewModel.fetchAssignedCall() }
    )
}
```

#### Flutter
```dart
// home_screen_new.dart
class _WaitingScreen extends ConsumerWidget {
  final dynamic session;

  const _WaitingScreen({required this.session});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      color: const Color(0xFF121212),
      child: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(
              Icons.access_time,
              size: 80,
              color: Color(0xFFFFB000),
            ),
            const SizedBox(height: 24),
            const Text(
              '새로운 콜을 기다리고 있습니다...',
              style: TextStyle(
                fontSize: 20,
                color: Colors.white70,
              ),
            ),
            const SizedBox(height: 40),
            ElevatedButton.icon(
              onPressed: () {
                ref.read(callNotifierProvider.notifier).fetchAssignedCall();
              },
              icon: const Icon(Icons.refresh),
              label: const Text('배차 확인'),
              style: ElevatedButton.styleFrom(
                padding: const EdgeInsets.symmetric(
                  horizontal: 32,
                  vertical: 16,
                ),
              ),
            ),
            const SizedBox(height: 16),
            OutlinedButton.icon(
              onPressed: () {
                Navigator.pushNamed(context, AppRoutes.referralQR);
              },
              icon: const Icon(Icons.qr_code),
              label: const Text('고객 추천하기'),
              style: OutlinedButton.styleFrom(
                foregroundColor: const Color(0xFFFFB000),
                side: const BorderSide(color: Color(0xFFFFB000)),
                padding: const EdgeInsets.symmetric(
                  horizontal: 32,
                  vertical: 16,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

// HomeScreen에서 사용
class HomeScreen extends ConsumerStatefulWidget {
  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authNotifierProvider);
    final driverState = ref.watch(driverNotifierProvider);

    return authState.when(
      authenticated: (session) {
        return driverState.when(
          loaded: (driver) => _buildBodyByDriverStatus(driver, session),
          loading: () => const CircularProgressIndicator(),
          error: (message) => Text('에러: $message'),
        );
      },
      // ...
    );
  }
}
```

**주요 차이:**
- Android: `@Composable` 함수, `Modifier` 체이닝
- Flutter: `Widget` 클래스, 생성자 체이닝
- Android: `collectAsStateWithLifecycle()` 로 StateFlow 구독
- Flutter: `ref.watch()` 로 Provider 구독

---

## 📡 FCM 구현 비교

### 3. FCM 서비스

#### Android (Kotlin)
```kotlin
// MyFirebaseMessagingService.kt
class MyFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationManager: NotificationManager

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val notificationType = remoteMessage.data["type"]

        when (notificationType) {
            "CALL_ASSIGNED" -> {
                showCallNotification(
                    title = remoteMessage.notification?.title ?: "새 콜",
                    body = remoteMessage.notification?.body ?: "",
                    data = remoteMessage.data
                )

                // CallRepository 업데이트
                CoroutineScope(Dispatchers.IO).launch {
                    callRepository.refreshAssignedCall()
                }
            }
            "CALL_CANCELLED" -> {
                // 취소 처리
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Firestore에 토큰 저장
        CoroutineScope(Dispatchers.IO).launch {
            driverRepository.updateFcmToken(token)
        }
    }

    private fun showCallNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}

// AndroidManifest.xml
<service
    android:name=".service.MyFirebaseMessagingService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

#### Flutter (Dart)
```dart
// fcm_service.dart
class FcmService {
  final FirebaseMessaging _messaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications =
      FlutterLocalNotificationsPlugin();

  static const String notificationTypeCallAssigned = 'CALL_ASSIGNED';
  static const String notificationTypeCallCancelled = 'CALL_CANCELLED';

  Future<void> initialize({
    required String regionId,
    required String officeId,
    required String driverId,
  }) async {
    // 권한 요청
    NotificationSettings settings = await _messaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );

    if (settings.authorizationStatus == AuthorizationStatus.authorized) {
      // FCM 토큰 가져오기
      String? token = await _messaging.getToken();
      if (token != null) {
        await _saveTokenToFirestore(
          regionId: regionId,
          officeId: officeId,
          driverId: driverId,
          token: token,
        );
      }

      // 토큰 갱신 리스너
      _messaging.onTokenRefresh.listen((newToken) {
        _saveTokenToFirestore(
          regionId: regionId,
          officeId: officeId,
          driverId: driverId,
          token: newToken,
        );
      });
    }

    // 로컬 알림 초기화
    await _initializeLocalNotifications();
  }

  void listenToForegroundMessages(
      void Function(RemoteMessage) onMessage) {
    FirebaseMessaging.onMessage.listen((RemoteMessage message) {
      // 로컬 알림 표시
      _showLocalNotification(message);

      // 콜백 호출
      onMessage(message);
    });
  }

  void listenToMessageOpenedApp(
      void Function(RemoteMessage) onMessageOpened) {
    // Background에서 알림 클릭
    FirebaseMessaging.onMessageOpenedApp.listen(onMessageOpened);

    // Terminated에서 알림 클릭
    _messaging.getInitialMessage().then((message) {
      if (message != null) {
        onMessageOpened(message);
      }
    });
  }

  Future<void> _showLocalNotification(RemoteMessage message) async {
    const AndroidNotificationDetails androidDetails =
        AndroidNotificationDetails(
      'driver_call_channel',
      '콜 배정 알림',
      importance: Importance.high,
      priority: Priority.high,
    );

    await _localNotifications.show(
      message.hashCode,
      message.notification?.title ?? '새로운 알림',
      message.notification?.body ?? '',
      const NotificationDetails(android: androidDetails),
    );
  }
}

// main.dart - Background Handler
@pragma('vm:entry-point')
Future<void> _firebaseMessagingBackgroundHandler(
    RemoteMessage message) async {
  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform
  );
  debugPrint('[FCM Background] 메시지 수신: ${message.messageId}');
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  // Background Handler 등록
  FirebaseMessaging.onBackgroundMessage(
    _firebaseMessagingBackgroundHandler
  );

  runApp(const ProviderScope(child: DriverApp()));
}
```

**주요 차이:**
- Android: Service 클래스 상속, AndroidManifest 등록 필요
- Flutter: 함수 기반, main.dart에서 핸들러 등록
- Android: NotificationManager 직접 사용
- Flutter: flutter_local_notifications 플러그인 사용
- 공통: Foreground/Background/Terminated 모두 처리

---

## 🗄️ Repository 패턴 비교

### 4. CallRepository

#### Android (Kotlin)
```kotlin
// domain/repository/CallRepository.kt (interface)
interface CallRepository {
    suspend fun getAssignedCall(driverId: String): Result<Call?>
    suspend fun acceptCall(callId: String, driverId: String): Result<Call>
    suspend fun rejectCall(callId: String, reason: String): Result<Unit>
    suspend fun completeCall(callId: String, fare: Int): Result<Call>
}

// data/repository/CallRepositoryImpl.kt
class CallRepositoryImpl @Inject constructor(
    private val remoteDataSource: CallRemoteDataSource,
    private val localDataSource: CallLocalDataSource
) : CallRepository {

    override suspend fun getAssignedCall(driverId: String): Result<Call?> {
        return try {
            val callDto = remoteDataSource.getAssignedCall(driverId)
            Result.Success(callDto?.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun acceptCall(
        callId: String,
        driverId: String
    ): Result<Call> {
        return try {
            val callDto = remoteDataSource.acceptCall(callId, driverId)
            Result.Success(callDto.toDomain())
        } catch (e: Exception) {
            Result.Error(e.message ?: "Unknown error")
        }
    }
}

// data/remote/CallRemoteDataSource.kt
class CallRemoteDataSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    suspend fun getAssignedCall(driverId: String): CallDto? {
        return firestore.collection("calls")
            .whereEqualTo("assignedDriverId", driverId)
            .whereEqualTo("status", "ASSIGNED")
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(CallDto::class.java)
    }
}
```

#### Flutter (Dart)
```dart
// domain/repositories/call_repository.dart (abstract)
abstract class CallRepository {
  Future<Either<Failure, Call?>> getAssignedCall(String driverId);
  Future<Either<Failure, Call>> acceptCall(String callId, String driverId);
  Future<Either<Failure, void>> rejectCall(String callId, String reason);
  Future<Either<Failure, Call>> completeCall(String callId, int fare);
}

// data/repositories/call_repository_impl.dart
class CallRepositoryImpl implements CallRepository {
  final CallRemoteDataSource remoteDataSource;

  CallRepositoryImpl({required this.remoteDataSource});

  @override
  Future<Either<Failure, Call?>> getAssignedCall(String driverId) async {
    try {
      final callModel = await remoteDataSource.getAssignedCall(driverId);
      return Right(callModel?.toEntity());
    } on ServerException catch (e) {
      return Left(ServerFailure(e.message));
    } catch (e) {
      return Left(ServerFailure('Unknown error'));
    }
  }

  @override
  Future<Either<Failure, Call>> acceptCall(
    String callId,
    String driverId,
  ) async {
    try {
      final callModel = await remoteDataSource.acceptCall(callId, driverId);
      return Right(callModel.toEntity());
    } on ServerException catch (e) {
      return Left(ServerFailure(e.message));
    }
  }
}

// data/datasources/call_remote_datasource.dart
class CallRemoteDataSource {
  final FirebaseFirestore firestore;

  CallRemoteDataSource({required this.firestore});

  Future<CallModel?> getAssignedCall(String driverId) async {
    final snapshot = await firestore
        .collection('calls')
        .where('assignedDriverId', isEqualTo: driverId)
        .where('status', isEqualTo: 'ASSIGNED')
        .get();

    if (snapshot.docs.isEmpty) return null;

    return CallModel.fromFirestore(snapshot.docs.first);
  }
}
```

**주요 차이:**
- Android: `Result<T>` (Success/Error)
- Flutter: `Either<Failure, T>` (Left/Right) - dartz 패키지
- Android: nullable 반환 + try-catch
- Flutter: Either로 에러 처리, 명시적 예외 타입
- 공통: Domain/Data 레이어 분리

---

## 🏗️ 의존성 주입 비교

### 5. DI 설정

#### Android (Hilt)
```kotlin
// di/AppModule.kt
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideCallRemoteDataSource(
        firestore: FirebaseFirestore
    ): CallRemoteDataSource {
        return CallRemoteDataSource(firestore)
    }

    @Provides
    @Singleton
    fun provideCallRepository(
        remoteDataSource: CallRemoteDataSource
    ): CallRepository {
        return CallRepositoryImpl(remoteDataSource)
    }
}

// di/UseCaseModule.kt
@Module
@InstallIn(ViewModelComponent::class)
object UseCaseModule {

    @Provides
    fun provideGetAssignedCallUseCase(
        repository: CallRepository
    ): GetAssignedCallUseCase {
        return GetAssignedCallUseCase(repository)
    }
}

// ViewModel에서 사용
@HiltViewModel
class CallViewModel @Inject constructor(
    private val getAssignedCallUseCase: GetAssignedCallUseCase,
    private val acceptCallUseCase: AcceptCallUseCase
) : ViewModel()
```

#### Flutter (GetIt)
```dart
// core/di/injection.dart
final sl = GetIt.instance;

Future<void> initializeDependencies() async {
  // Firebase
  final firestore = FirebaseFirestore.instance;
  sl.registerLazySingleton(() => firestore);

  // DataSources
  sl.registerLazySingleton<CallRemoteDataSource>(
    () => CallRemoteDataSource(firestore: sl()),
  );

  // Repositories
  sl.registerLazySingleton<CallRepository>(
    () => CallRepositoryImpl(remoteDataSource: sl()),
  );

  // UseCases
  sl.registerLazySingleton(() => GetAssignedCallUseCase(sl()));
  sl.registerLazySingleton(() => AcceptCallUseCase(sl()));
  sl.registerLazySingleton(() => RejectCallUseCase(sl()));
}

// main.dart
void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  await Firebase.initializeApp(
    options: DefaultFirebaseOptions.currentPlatform,
  );

  await di.initializeDependencies();  // DI 초기화

  runApp(const ProviderScope(child: DriverApp()));
}

// Notifier에서 사용
final callNotifierProvider =
    StateNotifierProvider<CallNotifier, CallState>((ref) {
  return CallNotifier(
    getAssignedCallUseCase: sl(),  // GetIt에서 가져오기
    acceptCallUseCase: sl(),
  );
});
```

**주요 차이:**
- Android: Hilt (annotation 기반, 컴파일 타임)
- Flutter: GetIt (코드 기반, 런타임)
- Android: `@Inject` annotation으로 자동 주입
- Flutter: `sl()` 함수로 수동 가져오기
- Android: ViewModelComponent, SingletonComponent 등 스코프 분리
- Flutter: Singleton만 주로 사용

---

## 📱 화면 전환 비교

### 6. Navigation

#### Android (Navigation Component)
```kotlin
// nav_graph.xml
<navigation>
    <fragment
        android:id="@+id/homeFragment"
        android:name="com.designated.driver.ui.home.HomeFragment" />

    <fragment
        android:id="@+id/callAssignedFragment"
        android:name="com.designated.driver.ui.call.CallAssignedFragment" />

    <action
        android:id="@+id/action_home_to_callAssigned"
        app:destination="@id/callAssignedFragment" />
</navigation>

// HomeFragment.kt
findNavController().navigate(
    R.id.action_home_to_callAssigned,
    bundleOf("callId" to callId)
)
```

#### Flutter
```dart
// core/routes.dart
class AppRoutes {
  static const String splash = '/';
  static const String login = '/login';
  static const String home = '/home';
  static const String referralQR = '/referral-qr';
  static const String historySettlement = '/history-settlement';

  static Map<String, WidgetBuilder> getRoutes() {
    return {
      splash: (context) => const SplashScreen(),
      login: (context) => const LoginScreen(),
      home: (context) => const HomeScreen(),
      referralQR: (context) => const ReferralQRScreen(),
      historySettlement: (context) => const HistorySettlementScreen(),
    };
  }
}

// 화면 전환
Navigator.pushNamed(context, AppRoutes.referralQR);

// 파라미터 전달
Navigator.pushNamed(
  context,
  AppRoutes.callDetail,
  arguments: {'callId': callId},
);
```

**주요 차이:**
- Android: XML 기반 Navigation Graph
- Flutter: 코드 기반 Route 정의
- Android: NavController로 관리
- Flutter: Navigator로 관리

---

## 📊 요약 비교표

| 항목 | Android (driver_app) | Flutter (driver_app_flutter) |
|------|---------------------|------------------------------|
| **언어** | Kotlin | Dart |
| **UI** | Jetpack Compose | Flutter Widgets |
| **상태관리** | StateFlow + ViewModel | StateNotifier + Riverpod |
| **DI** | Hilt (annotation) | GetIt (manual) |
| **비동기** | Coroutines + Flow | async/await + Future/Stream |
| **에러처리** | Result<T> sealed class | Either<L, R> dartz |
| **불변객체** | data class | Freezed annotation |
| **FCM** | Service 상속 | 함수 + 플러그인 |
| **네비게이션** | Navigation Component | Navigator + Routes |
| **화면구조** | 화면별 별도 파일 | 1개 파일에 통합 가능 |
| **플랫폼** | Android 전용 | iOS + Android |

---

## 🎯 코드 이식 가이드

### Android → Flutter 변환 시 주의사항

1. **Coroutines → async/await**
   ```kotlin
   // Android
   viewModelScope.launch {
       val result = repository.getData()
   }

   // Flutter
   Future<void> fetchData() async {
     final result = await repository.getData();
   }
   ```

2. **StateFlow → StateNotifier**
   ```kotlin
   // Android
   private val _state = MutableStateFlow(State.Initial)
   val state: StateFlow<State> = _state.asStateFlow()

   // Flutter
   class MyNotifier extends StateNotifier<State> {
     MyNotifier() : super(State.initial());
   }
   ```

3. **Hilt @Inject → GetIt sl()**
   ```kotlin
   // Android
   @Inject lateinit var useCase: UseCase

   // Flutter
   final useCase = sl<UseCase>();
   ```

4. **Composable → Widget**
   ```kotlin
   // Android
   @Composable
   fun MyScreen() { }

   // Flutter
   class MyScreen extends StatelessWidget {
     @override
     Widget build(BuildContext context) { }
   }
   ```

---

**작성일:** 2025-12-30
**목적:** Android 앱 로직을 Flutter로 이식 시 참조

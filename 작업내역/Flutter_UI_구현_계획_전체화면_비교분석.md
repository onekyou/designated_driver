# Flutter 기사앱 UI 구현 계획 - 전체 화면 비교 분석

**작성일**: 2025년 1월 (재부팅 후 작업 재개를 위한 문서)
**목적**: Android 기사앱과 Flutter 기사앱의 전체 화면을 비교 분석하고 UI 우선 구현 계획 수립

---

## 📋 목차

1. [전략 및 접근 방식](#전략-및-접근-방식)
2. [화면별 비교 분석](#화면별-비교-분석)
3. [UI 컴포넌트 인벤토리](#ui-컴포넌트-인벤토리)
4. [구현 우선순위](#구현-우선순위)
5. [단계별 구현 계획](#단계별-구현-계획)
6. [기술 스택 및 패턴](#기술-스택-및-패턴)

---

## 🎯 전략 및 접근 방식

### UI 우선 개발 전략

1. **Phase 1: UI 완성** (Mock 데이터 사용)
   - 모든 화면의 UI를 Android 앱과 동일하게 구현
   - 하드코딩된 Mock 데이터로 화면 렌더링
   - 네비게이션 플로우 완성
   - 사용자 인터랙션 구현 (버튼 클릭, 폼 입력 등)

2. **Phase 2: 기능 연결**
   - UseCase/Repository 계층 구현
   - Riverpod Provider 연결
   - Firebase 통신 구현

3. **Phase 3: iOS 빌드 및 테스트**
   - iOS 설정 및 권한 추가
   - MacCloud 빌드
   - 테스트 및 최적화

### 현재 상태 요약

**완료된 작업**:
- ✅ Clean Architecture 구조 (Domain/Data/Presentation 계층)
- ✅ Dependency Injection (GetIt)
- ✅ AuthNotifier & AuthState (Riverpod + Freezed)
- ✅ Theme & Routes 설정
- ✅ SplashScreen (완전 구현 - AuthNotifier 사용)
- ✅ LoginScreen (완전 구현 - AuthNotifier 사용)
- ✅ HomeScreen (기본 UI 완성 - 구식 AuthService 사용, 마이그레이션 필요)

**미구현 화면** (플레이스홀더 상태):
- ❌ SignUpScreen
- ❌ ForgotPasswordScreen
- ❌ ReferralQRScreen
- ❌ CallDetailsScreen
- ❌ HistorySettlementScreen
- ❌ OfflineScreen (Android에는 있으나 Flutter에 파일 자체 없음)
- ❌ WaitingScreen (Android에는 있으나 Flutter에 파일 자체 없음)
- ❌ TripPreparationScreen (Android에는 있으나 Flutter에 파일 자체 없음)
- ❌ InProgressScreen (Android에는 있으나 Flutter에 파일 자체 없음)

---

## 📱 화면별 비교 분석

### 1. SplashScreen (스플래시 화면)

#### Android 구현 (SplashActivity.kt)
- **레이아웃**: 중앙 정렬, 앱 아이콘 표시
- **기능**: Firebase 초기화, 자동 로그인 체크, 세션 복원
- **전환**: 자동 로그인 성공 → HomeScreen, 실패 → LoginScreen

#### Flutter 구현 (splash_screen.dart)
- **상태**: ✅ **완전 구현**
- **레이아웃**:
  ```dart
  - Icon(Icons.local_taxi, size: 100)
  - Text('기사앱')
  - CircularProgressIndicator
  ```
- **기능**: AuthNotifier의 autoLogin() 호출, AuthState 리스닝
- **전환**: authenticated → home, unauthenticated/error → login
- **개선사항**: 없음 (완벽하게 구현됨)

---

### 2. LoginScreen (로그인 화면)

#### Android 구현 (LoginScreen.kt)
```kotlin
// UI 요소
- TextField: 이메일 또는 전화번호
- TextField: 비밀번호 (visibility toggle)
- Checkbox: 자동 로그인
- Button: 로그인
- TextButton: 비밀번호 찾기
- TextButton: 회원가입
```

#### Flutter 구현 (login_screen.dart)
- **상태**: ✅ **완전 구현**
- **레이아웃**: Android와 동일
  ```dart
  - TextField: 이메일/전화번호 (_emailController)
  - TextField: 비밀번호 (_passwordController, _obscurePassword)
  - Checkbox: 자동 로그인 (_autoLogin)
  - ElevatedButton: 로그인 (loading 시 CircularProgressIndicator)
  - TextButton: 비밀번호 찾기 (AppRoutes.forgotPassword)
  - TextButton: 회원가입 (AppRoutes.signUp)
  ```
- **기능**:
  - AuthNotifier 사용
  - ref.listen으로 AuthState 변화 감지
  - 에러 처리: SnackBar 표시
  - 로딩 상태: 버튼 비활성화
- **개선사항**: 없음 (완벽하게 구현됨)

---

### 3. SignUpScreen (회원가입 화면)

#### Android 구현 (SignUpScreen.kt)
```kotlin
// UI 요소
Column {
  TextField: 이메일
  TextField: 비밀번호
  TextField: 비밀번호 확인
  TextField: 이름
  TextField: 전화번호

  // 지역 선택
  Dropdown: 지역 (regions 컬렉션에서 로드)

  // 사무실 선택 (선택된 지역에 따라 필터링)
  Dropdown: 사무실 (offices 컬렉션에서 로드, regionId 필터)

  Button: 회원가입
  TextButton: 이미 계정이 있으신가요? 로그인
}

// 기능
- 실시간 유효성 검사
- 비밀번호 일치 확인
- 이메일 중복 확인
- 지역 선택 시 사무실 목록 업데이트
- Firebase Auth + Firestore에 사용자 정보 저장
```

#### Flutter 구현 (signup_screen.dart)
- **상태**: ❌ **완전 미구현**
- **현재 코드**: 플레이스홀더 ("구현 예정" 텍스트만 표시)
- **필요한 작업**:
  1. UI 구현:
     ```dart
     - TextFormField: email (_emailController)
     - TextFormField: password (_passwordController)
     - TextFormField: passwordConfirm (_passwordConfirmController)
     - TextFormField: name (_nameController)
     - TextFormField: phone (_phoneController)
     - DropdownButtonFormField<String>: region (_selectedRegionId)
     - DropdownButtonFormField<String>: office (_selectedOfficeId)
     - ElevatedButton: 회원가입
     - TextButton: 로그인으로 이동
     ```
  2. Mock 데이터 (Phase 1):
     ```dart
     final mockRegions = [
       {'id': 'region1', 'name': '강원도'},
       {'id': 'region2', 'name': '서울'},
     ];
     final mockOffices = {
       'region1': [
         {'id': 'office1', 'name': '홍천사무실'},
         {'id': 'office2', 'name': '춘천사무실'},
       ],
       'region2': [
         {'id': 'office3', 'name': '강남사무실'},
       ],
     };
     ```
  3. 상태 관리:
     - 지역 선택 시 사무실 목록 필터링
     - 폼 유효성 검사
     - 로딩 상태 표시

---

### 4. ForgotPasswordScreen (비밀번호 찾기)

#### Android 구현 (ForgotPasswordScreen.kt)
```kotlin
// UI 요소
Column {
  Text: "등록된 이메일 주소를 입력하세요"
  TextField: 이메일
  Button: 비밀번호 재설정 이메일 보내기
}

// 기능
- Firebase Auth의 sendPasswordResetEmail() 사용
- 성공 시 안내 메시지 표시
- 이메일 전송 확인 후 로그인 화면으로 이동
```

#### Flutter 구현 (forgot_password_screen.dart)
- **상태**: ❌ **완전 미구현**
- **현재 코드**: 플레이스홀더
- **필요한 작업**:
  1. UI 구현:
     ```dart
     - Text: 안내 문구
     - TextFormField: email (_emailController)
     - ElevatedButton: 이메일 전송
     - loading 상태 표시
     ```
  2. Mock 기능 (Phase 1):
     ```dart
     void _handlePasswordReset() {
       // UI 플로우만 구현 (실제 이메일 전송 없음)
       ScaffoldMessenger.of(context).showSnackBar(
         SnackBar(content: Text('비밀번호 재설정 이메일이 전송되었습니다 (Mock)')),
       );
       Navigator.pop(context); // 로그인 화면으로
     }
     ```

---

### 5. HomeScreen (메인 홈 화면)

#### Android 구현 (HomeScreen.kt)
```kotlin
// 복합 화면: 기사 상태에 따라 다른 화면 표시
Scaffold(
  topBar: TopAppBar {
    title: officeName (Firestore에서 로드)
    actions: [
      IconButton: 운행내역/정산 (HistorySettlementScreen)
      IconButton: 로그아웃 (확인 다이얼로그 → 로그아웃)
    ]
  },
  body: when(driverStatus) {
    DriverStatus.OFFLINE -> OfflineScreen()
    DriverStatus.WAITING -> WaitingScreen()
    DriverStatus.TRIP_PREPARATION -> TripPreparationScreen()
    DriverStatus.IN_PROGRESS -> InProgressScreen()
  }
)

// DriverStatus 전환:
// OFFLINE → (Go Online 클릭) → WAITING
// WAITING → (콜 배차 받음) → TRIP_PREPARATION
// TRIP_PREPARATION → (운행 시작) → IN_PROGRESS
// IN_PROGRESS → (운행 완료) → WAITING
```

#### Flutter 구현 (home_screen.dart)
- **상태**: ⚠️ **부분 구현** (구식 패턴 사용)
- **현재 레이아웃**:
  ```dart
  Scaffold(
    appBar: AppBar(
      title: '기사앱' (고정값, Android는 사무실명)
      actions: [
        IconButton: 로그아웃 (AlertDialog 확인)
      ]
    ),
    body: SingleChildScrollView {
      // 기사 정보 카드
      Card {
        CircleAvatar: 이름 첫 글자
        Text: driverName
        Text: email
        Container: 온라인/오프라인 상태 배지
        Divider
        InfoRow: 지역
        InfoRow: 사무실
      }

      // 기능 버튼 그리드 (2x2)
      GridView {
        FeatureCard: 콜 목록 (TODO)
        FeatureCard: 운행 내역 (TODO)
        FeatureCard: 정산 (TODO)
        FeatureCard: 설정 (TODO)
      }
    }
  )
  ```
- **문제점**:
  1. **구식 패턴 사용**: AuthService와 SessionService를 직접 사용 (AuthNotifier를 사용해야 함)
  2. **Android와 구조 다름**: Android는 상태별 화면 전환, Flutter는 단순 기능 버튼 그리드
  3. **미구현 기능**: 모든 버튼이 "준비중" SnackBar만 표시

- **필요한 작업**:
  1. **AuthNotifier로 마이그레이션**:
     ```dart
     // Before (현재)
     final AuthService _authService = AuthService();
     final SessionService _sessionService = SessionService();
     UserSession? _session;

     // After (수정 필요)
     class HomeScreen extends ConsumerStatefulWidget {
       // ref.watch(authNotifierProvider) 사용
       // ref.read(authNotifierProvider.notifier).logout() 사용
     }
     ```

  2. **Android와 동일한 상태별 화면 구조 구현**:
     ```dart
     // DriverStatus enum 추가 (domain/entities/)
     enum DriverStatus { offline, waiting, tripPreparation, inProgress }

     // HomeScreen에서 상태별 화면 표시
     Widget build(BuildContext context) {
       final authState = ref.watch(authNotifierProvider);
       final driverStatus = ref.watch(driverStatusProvider); // 새로 구현 필요

       return Scaffold(
         appBar: AppBar(
           title: Text(session.officeName), // Firestore에서 로드
           actions: [
             IconButton(
               icon: Icon(Icons.history),
               onPressed: () => Navigator.pushNamed(context, AppRoutes.historySettlement),
             ),
             IconButton(
               icon: Icon(Icons.logout),
               onPressed: _logout,
             ),
           ],
         ),
         body: _buildBodyByStatus(driverStatus),
       );
     }

     Widget _buildBodyByStatus(DriverStatus status) {
       switch (status) {
         case DriverStatus.offline:
           return OfflineScreen();
         case DriverStatus.waiting:
           return WaitingScreen();
         case DriverStatus.tripPreparation:
           return TripPreparationScreen();
         case DriverStatus.inProgress:
           return InProgressScreen();
       }
     }
     ```

  3. **TopAppBar 수정**:
     - 타이틀을 사무실명으로 변경 (Firestore 로드)
     - 운행내역/정산 버튼 추가

---

### 6. OfflineScreen (오프라인 상태 화면)

#### Android 구현 (OfflineScreen.kt)
```kotlin
// 간단한 화면
Column(
  modifier = Modifier.fillMaxSize(),
  verticalArrangement = Arrangement.Center,
  horizontalAlignment = Alignment.CenterHorizontally
) {
  Icon(Icons.PowerOff, size = 80.dp, tint = Color.Gray)
  Spacer(height = 24.dp)
  Text("오프라인 상태", fontSize = 24.sp, fontWeight = FontWeight.Bold)
  Spacer(height = 16.dp)
  Text("근무를 시작하려면 온라인 전환하세요", color = Color.Gray)
  Spacer(height = 32.dp)
  Button(
    text = "근무 시작 (온라인 전환)",
    onClick = { viewModel.goOnline() }
  )
}

// 기능
- "근무 시작" 버튼 클릭 → Firestore의 drivers/{driverId} 문서 업데이트
  - status: "offline" → "waiting"
  - isOnline: false → true
  - lastOnlineAt: Timestamp.now()
```

#### Flutter 구현
- **상태**: ❌ **파일 자체 없음**
- **필요한 작업**:
  1. 파일 생성: `lib/screens/offline_screen.dart`
  2. UI 구현 (Mock 데이터):
     ```dart
     class OfflineScreen extends StatelessWidget {
       const OfflineScreen({super.key});

       @override
       Widget build(BuildContext context) {
         return Center(
           child: Column(
             mainAxisAlignment: MainAxisAlignment.center,
             children: [
               Icon(Icons.power_settings_new, size: 80, color: Colors.grey),
               SizedBox(height: 24),
               Text(
                 '오프라인 상태',
                 style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
               ),
               SizedBox(height: 16),
               Text(
                 '근무를 시작하려면 온라인 전환하세요',
                 style: TextStyle(color: AppTheme.textSecondary),
               ),
               SizedBox(height: 32),
               ElevatedButton(
                 onPressed: () {
                   // Phase 1: Mock 동작
                   ScaffoldMessenger.of(context).showSnackBar(
                     SnackBar(content: Text('온라인 전환됨 (Mock)')),
                   );
                 },
                 child: Text('근무 시작 (온라인 전환)'),
               ),
             ],
           ),
         );
       }
     }
     ```

---

### 7. WaitingScreen (대기 중 화면)

#### Android 구현 (WaitingScreen.kt)
```kotlin
Column(
  modifier = Modifier.fillMaxSize().padding(16.dp),
  verticalArrangement = Arrangement.Center,
  horizontalAlignment = Alignment.CenterHorizontally
) {
  // 대기 중 애니메이션 (파동 효과)
  AnimatedWaveCircle()

  Spacer(height = 24.dp)
  Text("콜 대기 중...", fontSize = 24.sp, fontWeight = FontWeight.Bold)
  Spacer(height: 16.dp)
  Text("새로운 콜이 배차되면 알림을 받습니다", color = Color.Gray)

  Spacer(height: 48.dp)

  // 버튼들
  OutlinedButton(
    text = "배차 대기 콜 확인",
    icon = Icons.List,
    onClick = { /* 배차 대기 중인 콜 목록 표시 */ }
  )

  Spacer(height: 16.dp)

  OutlinedButton(
    text = "추천 QR 코드",
    icon = Icons.QrCode,
    onClick = { navController.navigate(AppRoutes.referralQR) }
  )

  Spacer(height: 32.dp)

  // 오프라인 전환 버튼
  TextButton(
    text = "근무 종료 (오프라인 전환)",
    onClick = { viewModel.goOffline() }
  )
}

// 기능
- FCM 알림 대기 (새로운 콜 배차 시)
- "배차 대기 콜 확인": Firestore 쿼리 (calls 컬렉션, status == 'pending', assignedDriverId == currentDriverId)
- "추천 QR 코드": ReferralQRScreen으로 네비게이션
- "근무 종료": Firestore 업데이트 (status: "waiting" → "offline")
```

#### Flutter 구현
- **상태**: ❌ **파일 자체 없음**
- **필요한 작업**:
  1. 파일 생성: `lib/screens/waiting_screen.dart`
  2. UI 구현 (Phase 1 - Mock):
     ```dart
     class WaitingScreen extends StatelessWidget {
       const WaitingScreen({super.key});

       @override
       Widget build(BuildContext context) {
         return Center(
           child: Padding(
             padding: EdgeInsets.all(16),
             child: Column(
               mainAxisAlignment: MainAxisAlignment.center,
               children: [
                 // 대기 애니메이션 (간단한 CircularProgressIndicator 또는 Lottie)
                 SizedBox(
                   width: 120,
                   height: 120,
                   child: CircularProgressIndicator(
                     strokeWidth: 8,
                     color: AppTheme.colorWaiting,
                   ),
                 ),
                 SizedBox(height: 24),
                 Text(
                   '콜 대기 중...',
                   style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                 ),
                 SizedBox(height: 16),
                 Text(
                   '새로운 콜이 배차되면 알림을 받습니다',
                   style: TextStyle(color: AppTheme.textSecondary),
                 ),
                 SizedBox(height: 48),

                 // 배차 대기 콜 확인 버튼
                 OutlinedButton.icon(
                   icon: Icon(Icons.list),
                   label: Text('배차 대기 콜 확인'),
                   onPressed: () {
                     // Phase 1: Mock
                     ScaffoldMessenger.of(context).showSnackBar(
                       SnackBar(content: Text('배차 대기 콜 없음 (Mock)')),
                     );
                   },
                 ),

                 SizedBox(height: 16),

                 // 추천 QR 코드 버튼
                 OutlinedButton.icon(
                   icon: Icon(Icons.qr_code),
                   label: Text('추천 QR 코드'),
                   onPressed: () {
                     Navigator.pushNamed(context, AppRoutes.referralQR);
                   },
                 ),

                 SizedBox(height: 32),

                 // 근무 종료 버튼
                 TextButton(
                   child: Text('근무 종료 (오프라인 전환)'),
                   onPressed: () {
                     // Phase 1: Mock
                     ScaffoldMessenger.of(context).showSnackBar(
                       SnackBar(content: Text('오프라인 전환됨 (Mock)')),
                     );
                   },
                 ),
               ],
             ),
           ),
         );
       }
     }
     ```

---

### 8. TripPreparationScreen (운행 준비 화면)

#### Android 구현 (TripPreparationScreen.kt)
```kotlin
// 가장 복잡한 화면
Column(
  modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll()
) {
  // 1. 출발지 입력
  Card {
    Row {
      Icon(Icons.MyLocation)
      TextField(
        label = "출발지",
        value = departureAddress,
        readOnly = true // GPS로만 입력
      )
      IconButton(
        icon = Icons.GpsFixed,
        onClick = { getCurrentLocation() } // GPS로 현재 위치 가져오기
      )
    }
  }

  Spacer(height = 16.dp)

  // 2. 목적지 입력
  Card {
    Row {
      Icon(Icons.Place)
      TextField(
        label = "목적지",
        value = destinationAddress,
        onValueChange = { /* 실시간 주소 검색 */ }
      )
      IconButton(
        icon = Icons.Mic,
        onClick = { startVoiceInput() } // 음성 입력
      )
      IconButton(
        icon = Icons.Search,
        onClick = { openAddressSearch() } // 주소 검색 다이얼로그
      )
    }
  }

  Spacer(height = 16.dp)

  // 3. 경유지 추가 (동적 리스트)
  Card {
    Column {
      waypoints.forEachIndexed { index, waypoint ->
        Row {
          Icon(Icons.AddLocation)
          TextField(
            label = "경유지 ${index + 1}",
            value = waypoint.address
          )
          IconButton(
            icon = Icons.Close,
            onClick = { removeWaypoint(index) }
          )
        }
      }

      TextButton(
        text = "+ 경유지 추가",
        onClick = { addWaypoint() }
      )
    }
  }

  Spacer(height = 16.dp)

  // 4. 요금 입력
  Card {
    Row {
      Icon(Icons.AttachMoney)
      TextField(
        label = "요금 (원)",
        value = fare,
        keyboardType = KeyboardType.Number
      )
    }
  }

  Spacer(height: 32.dp)

  // 5. 운행 시작 버튼
  Button(
    text = "운행 시작",
    modifier = Modifier.fillMaxWidth(),
    enabled = departureAddress.isNotEmpty && destinationAddress.isNotEmpty && fare.isNotEmpty,
    onClick = { startTrip() }
  )

  Spacer(height: 16.dp)

  // 6. 콜 거부 버튼
  OutlinedButton(
    text = "콜 거부",
    modifier = Modifier.fillMaxWidth(),
    onClick = { showRejectDialog() }
  )
}

// 기능
- GPS 위치: 디바이스 현재 위치 → Geocoding API로 주소 변환
- 음성 입력: Android Speech Recognition
- 주소 검색: 카카오/네이버 지도 API 또는 Firestore의 자주 사용하는 주소
- 경유지 추가/삭제: 동적 리스트
- 운행 시작: Firestore 업데이트
  - calls/{callId}: status = "in_progress", startedAt = Timestamp.now()
  - drivers/{driverId}: status = "in_progress", currentCallId = callId
- 콜 거부: Firestore 업데이트
  - calls/{callId}: status = "rejected", rejectedAt = Timestamp.now()
  - drivers/{driverId}: status = "waiting"
```

#### Flutter 구현
- **상태**: ❌ **파일 자체 없음**
- **필요한 작업**:
  1. 파일 생성: `lib/screens/trip_preparation_screen.dart`
  2. UI 구현 (Phase 1 - Mock):
     ```dart
     class TripPreparationScreen extends StatefulWidget {
       const TripPreparationScreen({super.key});

       @override
       State<TripPreparationScreen> createState() => _TripPreparationScreenState();
     }

     class _TripPreparationScreenState extends State<TripPreparationScreen> {
       final _departureController = TextEditingController();
       final _destinationController = TextEditingController();
       final _fareController = TextEditingController();
       List<TextEditingController> _waypointControllers = [];

       @override
       Widget build(BuildContext context) {
         return Scaffold(
           appBar: AppBar(title: Text('운행 준비')),
           body: SingleChildScrollView(
             padding: EdgeInsets.all(16),
             child: Column(
               children: [
                 // 1. 출발지 입력
                 Card(
                   child: Padding(
                     padding: EdgeInsets.all(12),
                     child: Row(
                       children: [
                         Icon(Icons.my_location, color: AppTheme.primaryColor),
                         SizedBox(width: 12),
                         Expanded(
                           child: TextField(
                             controller: _departureController,
                             decoration: InputDecoration(
                               labelText: '출발지',
                               border: InputBorder.none,
                             ),
                             readOnly: true,
                           ),
                         ),
                         IconButton(
                           icon: Icon(Icons.gps_fixed),
                           onPressed: () {
                             // Phase 1: Mock GPS
                             setState(() {
                               _departureController.text = '강원도 홍천군 홍천읍 (Mock GPS)';
                             });
                           },
                         ),
                       ],
                     ),
                   ),
                 ),

                 SizedBox(height: 16),

                 // 2. 목적지 입력
                 Card(
                   child: Padding(
                     padding: EdgeInsets.all(12),
                     child: Row(
                       children: [
                         Icon(Icons.place, color: Colors.red),
                         SizedBox(width: 12),
                         Expanded(
                           child: TextField(
                             controller: _destinationController,
                             decoration: InputDecoration(
                               labelText: '목적지',
                               border: InputBorder.none,
                             ),
                           ),
                         ),
                         IconButton(
                           icon: Icon(Icons.mic),
                           onPressed: () {
                             // Phase 1: Mock 음성 입력
                             ScaffoldMessenger.of(context).showSnackBar(
                               SnackBar(content: Text('음성 입력 기능 준비중 (Mock)')),
                             );
                           },
                         ),
                         IconButton(
                           icon: Icon(Icons.search),
                           onPressed: () {
                             // Phase 1: Mock 주소 검색
                             ScaffoldMessenger.of(context).showSnackBar(
                               SnackBar(content: Text('주소 검색 기능 준비중 (Mock)')),
                             );
                           },
                         ),
                       ],
                     ),
                   ),
                 ),

                 SizedBox(height: 16),

                 // 3. 경유지 리스트
                 Card(
                   child: Padding(
                     padding: EdgeInsets.all(12),
                     child: Column(
                       children: [
                         ..._waypointControllers.asMap().entries.map((entry) {
                           int index = entry.key;
                           TextEditingController controller = entry.value;
                           return Row(
                             children: [
                               Icon(Icons.add_location, color: Colors.orange),
                               SizedBox(width: 12),
                               Expanded(
                                 child: TextField(
                                   controller: controller,
                                   decoration: InputDecoration(
                                     labelText: '경유지 ${index + 1}',
                                     border: InputBorder.none,
                                   ),
                                 ),
                               ),
                               IconButton(
                                 icon: Icon(Icons.close),
                                 onPressed: () {
                                   setState(() {
                                     _waypointControllers.removeAt(index);
                                   });
                                 },
                               ),
                             ],
                           );
                         }).toList(),

                         TextButton.icon(
                           icon: Icon(Icons.add),
                           label: Text('경유지 추가'),
                           onPressed: () {
                             setState(() {
                               _waypointControllers.add(TextEditingController());
                             });
                           },
                         ),
                       ],
                     ),
                   ),
                 ),

                 SizedBox(height: 16),

                 // 4. 요금 입력
                 Card(
                   child: Padding(
                     padding: EdgeInsets.all(12),
                     child: Row(
                       children: [
                         Icon(Icons.attach_money, color: AppTheme.colorOnline),
                         SizedBox(width: 12),
                         Expanded(
                           child: TextField(
                             controller: _fareController,
                             decoration: InputDecoration(
                               labelText: '요금 (원)',
                               border: InputBorder.none,
                             ),
                             keyboardType: TextInputType.number,
                           ),
                         ),
                       ],
                     ),
                   ),
                 ),

                 SizedBox(height: 32),

                 // 5. 운행 시작 버튼
                 SizedBox(
                   width: double.infinity,
                   child: ElevatedButton(
                     onPressed: _canStartTrip() ? () {
                       // Phase 1: Mock 운행 시작
                       ScaffoldMessenger.of(context).showSnackBar(
                         SnackBar(content: Text('운행 시작됨 (Mock)')),
                       );
                     } : null,
                     style: ElevatedButton.styleFrom(
                       backgroundColor: AppTheme.colorOnline,
                       padding: EdgeInsets.symmetric(vertical: 16),
                     ),
                     child: Text('운행 시작'),
                   ),
                 ),

                 SizedBox(height: 16),

                 // 6. 콜 거부 버튼
                 SizedBox(
                   width: double.infinity,
                   child: OutlinedButton(
                     onPressed: () {
                       _showRejectDialog();
                     },
                     style: OutlinedButton.styleFrom(
                       foregroundColor: Colors.red,
                       padding: EdgeInsets.symmetric(vertical: 16),
                     ),
                     child: Text('콜 거부'),
                   ),
                 ),
               ],
             ),
           ),
         );
       }

       bool _canStartTrip() {
         return _departureController.text.isNotEmpty &&
                _destinationController.text.isNotEmpty &&
                _fareController.text.isNotEmpty;
       }

       void _showRejectDialog() {
         showDialog(
           context: context,
           builder: (context) => AlertDialog(
             title: Text('콜 거부'),
             content: Text('이 콜을 거부하시겠습니까?'),
             actions: [
               TextButton(
                 onPressed: () => Navigator.pop(context),
                 child: Text('취소'),
               ),
               TextButton(
                 onPressed: () {
                   Navigator.pop(context);
                   ScaffoldMessenger.of(context).showSnackBar(
                     SnackBar(content: Text('콜이 거부되었습니다 (Mock)')),
                   );
                 },
                 style: TextButton.styleFrom(foregroundColor: Colors.red),
                 child: Text('거부'),
               ),
             ],
           ),
         );
       }

       @override
       void dispose() {
         _departureController.dispose();
         _destinationController.dispose();
         _fareController.dispose();
         for (var controller in _waypointControllers) {
           controller.dispose();
         }
         super.dispose();
       }
     }
     ```

---

### 9. InProgressScreen (운행 중 화면)

#### Android 구현 (InProgressScreen.kt)
```kotlin
Column(
  modifier = Modifier.fillMaxSize().padding(16.dp)
) {
  // 1. 운행 정보 카드
  Card {
    Column {
      // 출발지
      Row {
        Icon(Icons.MyLocation)
        Text("출발지:", fontWeight = FontWeight.Bold)
        Text(call.departureAddress)
      }

      Divider()

      // 경유지 (있는 경우)
      call.waypoints.forEach { waypoint ->
        Row {
          Icon(Icons.AddLocation)
          Text("경유지:", fontWeight = FontWeight.Bold)
          Text(waypoint.address)
        }
      }

      Divider()

      // 목적지
      Row {
        Icon(Icons.Place)
        Text("목적지:", fontWeight = FontWeight.Bold)
        Text(call.destinationAddress)
      }

      Divider()

      // 요금
      Row {
        Icon(Icons.AttachMoney)
        Text("요금:", fontWeight = FontWeight.Bold)
        Text("${call.fare.toNumberFormat()}원")
      }
    }
  }

  Spacer(height = 24.dp)

  // 2. 운행 시간 표시
  Card {
    Row {
      Icon(Icons.Timer)
      Text("운행 시간:", fontWeight = FontWeight.Bold)
      Text(elapsedTime) // "00:23:45" (실시간 업데이트)
    }
  }

  Spacer(weight = 1f)

  // 3. 운행 완료 버튼
  Button(
    text = "운행 완료",
    modifier = Modifier.fillMaxWidth(),
    onClick = { showCompleteDialog() }
  )
}

// 기능
- 타이머: startedAt부터 현재 시간까지 경과 시간 실시간 표시
- 운행 완료:
  - 확인 다이얼로그 표시
  - Firestore 업데이트:
    - calls/{callId}: status = "completed", completedAt = Timestamp.now(), duration = elapsedMinutes
    - drivers/{driverId}: status = "waiting", currentCallId = null, totalTrips += 1
  - 정산 데이터 생성: settlements 컬렉션에 문서 추가
```

#### Flutter 구현
- **상태**: ❌ **파일 자체 없음**
- **필요한 작업**:
  1. 파일 생성: `lib/screens/in_progress_screen.dart`
  2. UI 구현 (Phase 1 - Mock):
     ```dart
     class InProgressScreen extends StatefulWidget {
       const InProgressScreen({super.key});

       @override
       State<InProgressScreen> createState() => _InProgressScreenState();
     }

     class _InProgressScreenState extends State<InProgressScreen> {
       // Mock 데이터
       final mockCall = {
         'departureAddress': '강원도 홍천군 홍천읍',
         'destinationAddress': '강원도 춘천시 춘천역',
         'waypoints': [
           {'address': '홍천 터미널'},
         ],
         'fare': 45000,
         'startedAt': DateTime.now().subtract(Duration(minutes: 23)),
       };

       late Timer _timer;
       String _elapsedTime = '00:00:00';

       @override
       void initState() {
         super.initState();
         _startTimer();
       }

       void _startTimer() {
         _timer = Timer.periodic(Duration(seconds: 1), (timer) {
           final elapsed = DateTime.now().difference(mockCall['startedAt'] as DateTime);
           setState(() {
             _elapsedTime = _formatDuration(elapsed);
           });
         });
       }

       String _formatDuration(Duration duration) {
         String twoDigits(int n) => n.toString().padLeft(2, '0');
         return '${twoDigits(duration.inHours)}:${twoDigits(duration.inMinutes.remainder(60))}:${twoDigits(duration.inSeconds.remainder(60))}';
       }

       @override
       Widget build(BuildContext context) {
         return Padding(
           padding: EdgeInsets.all(16),
           child: Column(
             children: [
               // 1. 운행 정보 카드
               Card(
                 child: Padding(
                   padding: EdgeInsets.all(16),
                   child: Column(
                     children: [
                       // 출발지
                       _buildInfoRow(
                         icon: Icons.my_location,
                         label: '출발지',
                         value: mockCall['departureAddress'] as String,
                       ),
                       Divider(),

                       // 경유지
                       ...(mockCall['waypoints'] as List).map((waypoint) {
                         return Column(
                           children: [
                             _buildInfoRow(
                               icon: Icons.add_location,
                               label: '경유지',
                               value: waypoint['address'] as String,
                             ),
                             Divider(),
                           ],
                         );
                       }).toList(),

                       // 목적지
                       _buildInfoRow(
                         icon: Icons.place,
                         iconColor: Colors.red,
                         label: '목적지',
                         value: mockCall['destinationAddress'] as String,
                       ),
                       Divider(),

                       // 요금
                       _buildInfoRow(
                         icon: Icons.attach_money,
                         iconColor: AppTheme.colorOnline,
                         label: '요금',
                         value: '${(mockCall['fare'] as int).toStringAsFixed(0)}원',
                       ),
                     ],
                   ),
                 ),
               ),

               SizedBox(height: 24),

               // 2. 운행 시간 표시
               Card(
                 child: Padding(
                   padding: EdgeInsets.all(16),
                   child: Row(
                     children: [
                       Icon(Icons.timer, color: AppTheme.primaryColor),
                       SizedBox(width: 12),
                       Text(
                         '운행 시간:',
                         style: TextStyle(fontWeight: FontWeight.bold),
                       ),
                       SizedBox(width: 8),
                       Text(
                         _elapsedTime,
                         style: TextStyle(
                           fontSize: 20,
                           fontWeight: FontWeight.bold,
                           color: AppTheme.primaryColor,
                         ),
                       ),
                     ],
                   ),
                 ),
               ),

               Spacer(),

               // 3. 운행 완료 버튼
               SizedBox(
                 width: double.infinity,
                 child: ElevatedButton(
                   onPressed: _showCompleteDialog,
                   style: ElevatedButton.styleFrom(
                     backgroundColor: AppTheme.colorOnline,
                     padding: EdgeInsets.symmetric(vertical: 16),
                   ),
                   child: Text('운행 완료', style: TextStyle(fontSize: 18)),
                 ),
               ),
             ],
           ),
         );
       }

       Widget _buildInfoRow({
         required IconData icon,
         required String label,
         required String value,
         Color? iconColor,
       }) {
         return Row(
           crossAxisAlignment: CrossAxisAlignment.start,
           children: [
             Icon(icon, color: iconColor ?? AppTheme.primaryColor),
             SizedBox(width: 12),
             Text(
               '$label:',
               style: TextStyle(fontWeight: FontWeight.bold),
             ),
             SizedBox(width: 8),
             Expanded(
               child: Text(value),
             ),
           ],
         );
       }

       void _showCompleteDialog() {
         showDialog(
           context: context,
           builder: (context) => AlertDialog(
             title: Text('운행 완료'),
             content: Text('운행을 완료하시겠습니까?'),
             actions: [
               TextButton(
                 onPressed: () => Navigator.pop(context),
                 child: Text('취소'),
               ),
               TextButton(
                 onPressed: () {
                   Navigator.pop(context);
                   ScaffoldMessenger.of(context).showSnackBar(
                     SnackBar(content: Text('운행이 완료되었습니다 (Mock)')),
                   );
                 },
                 child: Text('완료'),
               ),
             ],
           ),
         );
       }

       @override
       void dispose() {
         _timer.cancel();
         super.dispose();
       }
     }
     ```

---

### 10. ReferralQRScreen (추천 QR 코드 화면)

#### Android 구현 (ReferralQRScreen.kt)
```kotlin
Column(
  modifier = Modifier.fillMaxSize().padding(16.dp),
  horizontalAlignment = Alignment.CenterHorizontally
) {
  Text(
    "고객 추천 QR 코드",
    fontSize = 24.sp,
    fontWeight = FontWeight.Bold
  )

  Spacer(height = 16.dp)

  Text(
    "고객님께 이 QR 코드를 스캔하도록 안내하세요",
    color = Color.Gray
  )

  Spacer(height = 32.dp)

  // QR 코드 표시
  Card(
    modifier = Modifier.size(300.dp)
  ) {
    QRCodeImage(
      data = generateReferralUrl(), // "https://app.com/referral?driverId=xxx"
      size = 280.dp
    )
  }

  Spacer(height: 24.dp)

  // 추천 링크 복사
  OutlinedButton(
    text = "추천 링크 복사",
    icon = Icons.Link,
    onClick = {
      copyToClipboard(generateReferralUrl())
      showToast("추천 링크가 복사되었습니다")
    }
  )

  Spacer(height: 16.dp)

  // 추천 통계
  Card {
    Column {
      Text("내 추천 통계", fontWeight = FontWeight.Bold)
      Divider()
      Row {
        Text("추천한 고객:")
        Text("${stats.referredCustomers}명")
      }
      Row {
        Text("추천 포인트:")
        Text("${stats.referralPoints}P")
      }
    }
  }
}

// 기능
- generateReferralUrl(): 기사 ID를 포함한 고유 URL 생성
- QR 코드 라이브러리로 URL을 QR 이미지로 변환
- 추천 통계: Firestore에서 drivers/{driverId}/referralStats 로드
```

#### Flutter 구현 (referral_qr_screen.dart)
- **상태**: ❌ **미구현** (플레이스홀더)
- **필요한 작업**:
  1. UI 구현 (Phase 1 - Mock):
     ```dart
     import 'package:qr_flutter/qr_flutter.dart'; // pubspec.yaml에 추가 필요

     class ReferralQRScreen extends StatelessWidget {
       const ReferralQRScreen({super.key});

       // Mock 데이터
       String get mockReferralUrl => 'https://app.com/referral?driverId=MOCK123';

       @override
       Widget build(BuildContext context) {
         return Scaffold(
           appBar: AppBar(title: Text('추천 QR 코드')),
           body: Padding(
             padding: EdgeInsets.all(16),
             child: Column(
               mainAxisAlignment: MainAxisAlignment.center,
               children: [
                 Text(
                   '고객 추천 QR 코드',
                   style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                 ),
                 SizedBox(height: 16),
                 Text(
                   '고객님께 이 QR 코드를 스캔하도록 안내하세요',
                   style: TextStyle(color: AppTheme.textSecondary),
                 ),
                 SizedBox(height: 32),

                 // QR 코드
                 Card(
                   child: Padding(
                     padding: EdgeInsets.all(20),
                     child: QrImageView(
                       data: mockReferralUrl,
                       version: QrVersions.auto,
                       size: 250,
                     ),
                   ),
                 ),

                 SizedBox(height: 24),

                 // 링크 복사 버튼
                 OutlinedButton.icon(
                   icon: Icon(Icons.link),
                   label: Text('추천 링크 복사'),
                   onPressed: () {
                     // Phase 1: Mock 복사
                     ScaffoldMessenger.of(context).showSnackBar(
                       SnackBar(content: Text('추천 링크가 복사되었습니다 (Mock)')),
                     );
                   },
                 ),

                 SizedBox(height: 16),

                 // 추천 통계 카드
                 Card(
                   child: Padding(
                     padding: EdgeInsets.all(16),
                     child: Column(
                       crossAxisAlignment: CrossAxisAlignment.start,
                       children: [
                         Text(
                           '내 추천 통계',
                           style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                         ),
                         Divider(),
                         Row(
                           mainAxisAlignment: MainAxisAlignment.spaceBetween,
                           children: [
                             Text('추천한 고객:'),
                             Text('12명', style: TextStyle(fontWeight: FontWeight.bold)),
                           ],
                         ),
                         SizedBox(height: 8),
                         Row(
                           mainAxisAlignment: MainAxisAlignment.spaceBetween,
                           children: [
                             Text('추천 포인트:'),
                             Text('24,000P', style: TextStyle(fontWeight: FontWeight.bold, color: AppTheme.colorOnline)),
                           ],
                         ),
                       ],
                     ),
                   ),
                 ),
               ],
             ),
           ),
         );
       }
     }
     ```

---

### 11. CallDetailsScreen (콜 상세 화면)

#### Android 구현 (CallDetailsScreen.kt)
```kotlin
// Arguments: callId (String)
Scaffold(
  topBar: TopAppBar { title = "콜 상세정보" }
) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll()
  ) {
    // 1. 콜 정보 카드
    Card {
      Column {
        // 콜 번호
        Row {
          Text("콜 번호:", fontWeight = FontWeight.Bold)
          Text(call.callNumber)
        }

        Divider()

        // 고객 전화번호
        Row {
          Icon(Icons.Phone)
          Text("고객 번호:", fontWeight = FontWeight.Bold)
          Text(call.customerPhone)
          IconButton(
            icon = Icons.Call,
            onClick = { makePhoneCall(call.customerPhone) }
          )
        }

        Divider()

        // 출발지/목적지
        Row {
          Icon(Icons.MyLocation)
          Text("출발지:", fontWeight = FontWeight.Bold)
          Text(call.departureAddress)
        }
        Row {
          Icon(Icons.Place)
          Text("목적지:", fontWeight = FontWeight.Bold)
          Text(call.destinationAddress)
        }

        Divider()

        // 요금 (있는 경우)
        if (call.fare != null) {
          Row {
            Icon(Icons.AttachMoney)
            Text("요금:", fontWeight = FontWeight.Bold)
            Text("${call.fare.toNumberFormat()}원")
          }
        }

        Divider()

        // 접수 시간
        Row {
          Icon(Icons.AccessTime)
          Text("접수 시간:", fontWeight = FontWeight.Bold)
          Text(call.createdAt.toFormattedString())
        }
      }
    }

    Spacer(height = 24.dp)

    // 2. 액션 버튼들 (콜 상태에 따라 다름)
    when (call.status) {
      CallStatus.PENDING -> {
        // 배차 대기 중
        Button(
          text = "콜 수락",
          modifier = Modifier.fillMaxWidth(),
          onClick = { acceptCall() }
        )

        Spacer(height = 16.dp)

        OutlinedButton(
          text = "거부",
          modifier = Modifier.fillMaxWidth(),
          onClick = { rejectCall() }
        )
      }

      CallStatus.ACCEPTED -> {
        // 운행 준비
        Button(
          text = "운행 시작",
          modifier = Modifier.fillMaxWidth(),
          onClick = { startTrip() }
        )
      }

      CallStatus.IN_PROGRESS -> {
        // 운행 중
        Text("현재 운행 중입니다", color = Color.Green)
      }

      CallStatus.COMPLETED -> {
        // 완료됨
        Text("완료된 콜입니다", color = Color.Gray)
      }
    }
  }
}

// 기능
- Firestore에서 calls/{callId} 문서 로드
- 콜 수락: Firestore 업데이트 (status = "accepted", acceptedAt = now)
- 콜 거부: Firestore 업데이트 (status = "rejected")
- 운행 시작: TripPreparationScreen으로 네비게이션
- 전화 걸기: Android Intent (ACTION_DIAL)
```

#### Flutter 구현 (call_details_screen.dart)
- **상태**: ❌ **미구현** (플레이스홀더)
- **필요한 작업**:
  1. UI 구현 (Phase 1 - Mock):
     ```dart
     class CallDetailsScreen extends StatelessWidget {
       final String? callId;

       const CallDetailsScreen({super.key, this.callId});

       // Mock 데이터
       final mockCall = {
         'callNumber': 'C-20250118-001',
         'customerPhone': '010-1234-5678',
         'departureAddress': '강원도 홍천군 홍천읍 희망로 123',
         'destinationAddress': '강원도 춘천시 춘천역',
         'fare': 45000,
         'createdAt': DateTime.now().subtract(Duration(minutes: 15)),
         'status': 'pending', // pending, accepted, in_progress, completed
       };

       @override
       Widget build(BuildContext context) {
         return Scaffold(
           appBar: AppBar(title: Text('콜 상세정보')),
           body: SingleChildScrollView(
             padding: EdgeInsets.all(16),
             child: Column(
               children: [
                 // 1. 콜 정보 카드
                 Card(
                   child: Padding(
                     padding: EdgeInsets.all(16),
                     child: Column(
                       children: [
                         // 콜 번호
                         _buildInfoRow(
                           label: '콜 번호',
                           value: mockCall['callNumber'] as String,
                         ),
                         Divider(),

                         // 고객 전화번호
                         Row(
                           children: [
                             Icon(Icons.phone, color: AppTheme.primaryColor),
                             SizedBox(width: 12),
                             Text('고객 번호:', style: TextStyle(fontWeight: FontWeight.bold)),
                             SizedBox(width: 8),
                             Expanded(child: Text(mockCall['customerPhone'] as String)),
                             IconButton(
                               icon: Icon(Icons.call),
                               onPressed: () {
                                 // Phase 1: Mock 전화 걸기
                                 ScaffoldMessenger.of(context).showSnackBar(
                                   SnackBar(content: Text('전화 걸기 기능 준비중 (Mock)')),
                                 );
                               },
                             ),
                           ],
                         ),
                         Divider(),

                         // 출발지
                         _buildAddressRow(
                           icon: Icons.my_location,
                           label: '출발지',
                           value: mockCall['departureAddress'] as String,
                         ),

                         // 목적지
                         _buildAddressRow(
                           icon: Icons.place,
                           label: '목적지',
                           value: mockCall['destinationAddress'] as String,
                           iconColor: Colors.red,
                         ),
                         Divider(),

                         // 요금
                         _buildInfoRow(
                           icon: Icons.attach_money,
                           label: '요금',
                           value: '${mockCall['fare']}원',
                         ),
                         Divider(),

                         // 접수 시간
                         _buildInfoRow(
                           icon: Icons.access_time,
                           label: '접수 시간',
                           value: _formatDateTime(mockCall['createdAt'] as DateTime),
                         ),
                       ],
                     ),
                   ),
                 ),

                 SizedBox(height: 24),

                 // 2. 액션 버튼들
                 _buildActionButtons(context, mockCall['status'] as String),
               ],
             ),
           ),
         );
       }

       Widget _buildInfoRow({
         IconData? icon,
         required String label,
         required String value,
       }) {
         return Row(
           crossAxisAlignment: CrossAxisAlignment.start,
           children: [
             if (icon != null) ...[
               Icon(icon, color: AppTheme.primaryColor),
               SizedBox(width: 12),
             ],
             Text('$label:', style: TextStyle(fontWeight: FontWeight.bold)),
             SizedBox(width: 8),
             Expanded(child: Text(value)),
           ],
         );
       }

       Widget _buildAddressRow({
         required IconData icon,
         required String label,
         required String value,
         Color? iconColor,
       }) {
         return Row(
           crossAxisAlignment: CrossAxisAlignment.start,
           children: [
             Icon(icon, color: iconColor ?? AppTheme.primaryColor),
             SizedBox(width: 12),
             Expanded(
               child: Column(
                 crossAxisAlignment: CrossAxisAlignment.start,
                 children: [
                   Text('$label:', style: TextStyle(fontWeight: FontWeight.bold)),
                   SizedBox(height: 4),
                   Text(value),
                 ],
               ),
             ),
           ],
         );
       }

       Widget _buildActionButtons(BuildContext context, String status) {
         switch (status) {
           case 'pending':
             return Column(
               children: [
                 SizedBox(
                   width: double.infinity,
                   child: ElevatedButton(
                     onPressed: () {
                       ScaffoldMessenger.of(context).showSnackBar(
                         SnackBar(content: Text('콜을 수락했습니다 (Mock)')),
                       );
                     },
                     style: ElevatedButton.styleFrom(
                       backgroundColor: AppTheme.colorOnline,
                       padding: EdgeInsets.symmetric(vertical: 16),
                     ),
                     child: Text('콜 수락'),
                   ),
                 ),
                 SizedBox(height: 16),
                 SizedBox(
                   width: double.infinity,
                   child: OutlinedButton(
                     onPressed: () {
                       ScaffoldMessenger.of(context).showSnackBar(
                         SnackBar(content: Text('콜을 거부했습니다 (Mock)')),
                       );
                     },
                     style: OutlinedButton.styleFrom(
                       foregroundColor: Colors.red,
                       padding: EdgeInsets.symmetric(vertical: 16),
                     ),
                     child: Text('거부'),
                   ),
                 ),
               ],
             );

           case 'accepted':
             return SizedBox(
               width: double.infinity,
               child: ElevatedButton(
                 onPressed: () {
                   ScaffoldMessenger.of(context).showSnackBar(
                     SnackBar(content: Text('운행 시작 (Mock)')),
                   );
                 },
                 style: ElevatedButton.styleFrom(
                   backgroundColor: AppTheme.colorOnline,
                   padding: EdgeInsets.symmetric(vertical: 16),
                 ),
                 child: Text('운행 시작'),
               ),
             );

           case 'in_progress':
             return Card(
               color: Colors.green[50],
               child: Padding(
                 padding: EdgeInsets.all(16),
                 child: Row(
                   children: [
                     Icon(Icons.check_circle, color: Colors.green),
                     SizedBox(width: 12),
                     Text('현재 운행 중입니다', style: TextStyle(color: Colors.green[700])),
                   ],
                 ),
               ),
             );

           case 'completed':
             return Card(
               color: Colors.grey[200],
               child: Padding(
                 padding: EdgeInsets.all(16),
                 child: Row(
                   children: [
                     Icon(Icons.done_all, color: Colors.grey),
                     SizedBox(width: 12),
                     Text('완료된 콜입니다', style: TextStyle(color: Colors.grey[700])),
                   ],
                 ),
               ),
             );

           default:
             return SizedBox.shrink();
         }
       }

       String _formatDateTime(DateTime dateTime) {
         return '${dateTime.year}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')} '
                '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}';
       }
     }
     ```

---

### 12. HistorySettlementScreen (운행내역/정산 화면)

#### Android 구현 (HistorySettlementScreen.kt)
```kotlin
Scaffold(
  topBar: TopAppBar { title = "운행내역 / 정산" }
) {
  Column {
    // 1. 탭 선택 (운행내역 / 정산)
    TabRow(
      selectedTabIndex = selectedTab,
      tabs = [
        Tab(text = "운행내역"),
        Tab(text = "정산")
      ]
    )

    // 2. TabContent
    when (selectedTab) {
      0 -> HistoryTab()
      1 -> SettlementTab()
    }
  }
}

// HistoryTab (운행내역)
@Composable
fun HistoryTab() {
  Column {
    // 날짜 필터
    Row {
      DatePicker(
        label = "시작일",
        value = startDate,
        onValueChange = { startDate = it }
      )
      DatePicker(
        label = "종료일",
        value = endDate,
        onValueChange = { endDate = it }
      )
      Button(text = "조회", onClick = { loadHistory() })
    }

    // 운행 내역 리스트
    LazyColumn {
      items(trips) { trip ->
        Card {
          Column {
            Row {
              Text("콜 번호: ${trip.callNumber}")
              Spacer()
              Text("${trip.fare.toNumberFormat()}원", fontWeight = FontWeight.Bold)
            }

            Row {
              Icon(Icons.MyLocation)
              Text(trip.departureAddress)
            }
            Row {
              Icon(Icons.Place)
              Text(trip.destinationAddress)
            }

            Row {
              Icon(Icons.AccessTime)
              Text(trip.completedAt.toFormattedString())
            }
          }
        }
      }
    }
  }
}

// SettlementTab (정산)
@Composable
fun SettlementTab() {
  Column {
    // 정산 기간 선택
    DropdownMenu(
      label = "정산 기간",
      options = ["이번 주", "이번 달", "지난 달", "직접 선택"],
      onSelect = { loadSettlement(it) }
    )

    // 정산 요약 카드
    Card {
      Column {
        Row {
          Text("총 운행 건수:")
          Text("${settlement.totalTrips}건", fontWeight = FontWeight.Bold)
        }

        Divider()

        Row {
          Text("총 운행 요금:")
          Text("${settlement.totalFare.toNumberFormat()}원", fontWeight = FontWeight.Bold)
        }

        Divider()

        Row {
          Text("수수료 (${settlement.commissionRate}%):")
          Text("-${settlement.commission.toNumberFormat()}원", color = Color.Red)
        }

        Divider()

        Row {
          Text("실 정산액:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
          Text(
            "${settlement.netAmount.toNumberFormat()}원",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Green
          )
        }
      }
    }

    // 정산 상세 리스트
    LazyColumn {
      items(settlement.trips) { trip ->
        Card {
          Row {
            Column {
              Text(trip.callNumber)
              Text(trip.completedAt.toFormattedString(), fontSize = 12.sp)
            }
            Spacer()
            Text("${trip.fare.toNumberFormat()}원")
          }
        }
      }
    }

    // 정산 신청 버튼 (미정산 상태인 경우)
    if (!settlement.isSettled) {
      Button(
        text = "정산 신청",
        modifier = Modifier.fillMaxWidth(),
        onClick = { requestSettlement() }
      )
    }
  }
}

// 기능
- Firestore 쿼리:
  - 운행내역: calls 컬렉션 (driverId == currentDriver, status == "completed", date range)
  - 정산: settlements 컬렉션 (driverId == currentDriver, period)
- 정산 신청: Firestore에 정산 요청 문서 생성, 관리자 알림
```

#### Flutter 구현 (history_settlement_screen.dart)
- **상태**: ❌ **미구현** (플레이스홀더)
- **필요한 작업**:
  1. UI 구현 (Phase 1 - Mock):
     ```dart
     class HistorySettlementScreen extends StatefulWidget {
       const HistorySettlementScreen({super.key});

       @override
       State<HistorySettlementScreen> createState() => _HistorySettlementScreenState();
     }

     class _HistorySettlementScreenState extends State<HistorySettlementScreen>
         with SingleTickerProviderStateMixin {
       late TabController _tabController;

       // Mock 데이터
       final mockTrips = [
         {
           'callNumber': 'C-20250118-001',
           'departureAddress': '홍천읍 희망로 123',
           'destinationAddress': '춘천시 춘천역',
           'fare': 45000,
           'completedAt': DateTime.now().subtract(Duration(hours: 2)),
         },
         {
           'callNumber': 'C-20250118-002',
           'departureAddress': '홍천읍 중앙로 456',
           'destinationAddress': '홍천터미널',
           'fare': 15000,
           'completedAt': DateTime.now().subtract(Duration(hours: 5)),
         },
       ];

       final mockSettlement = {
         'totalTrips': 15,
         'totalFare': 450000,
         'commissionRate': 20,
         'commission': 90000,
         'netAmount': 360000,
         'isSettled': false,
       };

       @override
       void initState() {
         super.initState();
         _tabController = TabController(length: 2, vsync: this);
       }

       @override
       Widget build(BuildContext context) {
         return Scaffold(
           appBar: AppBar(
             title: Text('운행내역 / 정산'),
             bottom: TabBar(
               controller: _tabController,
               tabs: [
                 Tab(text: '운행내역'),
                 Tab(text: '정산'),
               ],
             ),
           ),
           body: TabBarView(
             controller: _tabController,
             children: [
               _buildHistoryTab(),
               _buildSettlementTab(),
             ],
           ),
         );
       }

       Widget _buildHistoryTab() {
         return Column(
           children: [
             // 날짜 필터
             Padding(
               padding: EdgeInsets.all(16),
               child: Row(
                 children: [
                   Expanded(
                     child: OutlinedButton(
                       onPressed: () {
                         // Phase 1: Mock 날짜 선택
                         ScaffoldMessenger.of(context).showSnackBar(
                           SnackBar(content: Text('날짜 선택 기능 준비중 (Mock)')),
                         );
                       },
                       child: Text('시작일: 2025-01-01'),
                     ),
                   ),
                   SizedBox(width: 8),
                   Expanded(
                     child: OutlinedButton(
                       onPressed: () {
                         // Phase 1: Mock 날짜 선택
                         ScaffoldMessenger.of(context).showSnackBar(
                           SnackBar(content: Text('날짜 선택 기능 준비중 (Mock)')),
                         );
                       },
                       child: Text('종료일: 2025-01-18'),
                     ),
                   ),
                   SizedBox(width: 8),
                   ElevatedButton(
                     onPressed: () {
                       ScaffoldMessenger.of(context).showSnackBar(
                         SnackBar(content: Text('조회 완료 (Mock)')),
                       );
                     },
                     child: Text('조회'),
                   ),
                 ],
               ),
             ),

             // 운행 내역 리스트
             Expanded(
               child: ListView.builder(
                 padding: EdgeInsets.all(16),
                 itemCount: mockTrips.length,
                 itemBuilder: (context, index) {
                   final trip = mockTrips[index];
                   return Card(
                     margin: EdgeInsets.only(bottom: 12),
                     child: Padding(
                       padding: EdgeInsets.all(16),
                       child: Column(
                         crossAxisAlignment: CrossAxisAlignment.start,
                         children: [
                           Row(
                             mainAxisAlignment: MainAxisAlignment.spaceBetween,
                             children: [
                               Text(
                                 trip['callNumber'] as String,
                                 style: TextStyle(fontWeight: FontWeight.bold),
                               ),
                               Text(
                                 '${trip['fare']}원',
                                 style: TextStyle(
                                   fontSize: 16,
                                   fontWeight: FontWeight.bold,
                                   color: AppTheme.colorOnline,
                                 ),
                               ),
                             ],
                           ),
                           SizedBox(height: 8),
                           Row(
                             children: [
                               Icon(Icons.my_location, size: 16, color: Colors.grey),
                               SizedBox(width: 4),
                               Expanded(child: Text(trip['departureAddress'] as String)),
                             ],
                           ),
                           SizedBox(height: 4),
                           Row(
                             children: [
                               Icon(Icons.place, size: 16, color: Colors.red),
                               SizedBox(width: 4),
                               Expanded(child: Text(trip['destinationAddress'] as String)),
                             ],
                           ),
                           SizedBox(height: 8),
                           Row(
                             children: [
                               Icon(Icons.access_time, size: 16, color: Colors.grey),
                               SizedBox(width: 4),
                               Text(
                                 _formatDateTime(trip['completedAt'] as DateTime),
                                 style: TextStyle(fontSize: 12, color: Colors.grey),
                               ),
                             ],
                           ),
                         ],
                       ),
                     ),
                   );
                 },
               ),
             ),
           ],
         );
       }

       Widget _buildSettlementTab() {
         return SingleChildScrollView(
           padding: EdgeInsets.all(16),
           child: Column(
             children: [
               // 정산 기간 선택
               DropdownButtonFormField<String>(
                 decoration: InputDecoration(
                   labelText: '정산 기간',
                   border: OutlineInputBorder(),
                 ),
                 value: '이번 달',
                 items: ['이번 주', '이번 달', '지난 달', '직접 선택']
                     .map((period) => DropdownMenuItem(
                           value: period,
                           child: Text(period),
                         ))
                     .toList(),
                 onChanged: (value) {
                   ScaffoldMessenger.of(context).showSnackBar(
                     SnackBar(content: Text('$value 선택됨 (Mock)')),
                   );
                 },
               ),

               SizedBox(height: 24),

               // 정산 요약 카드
               Card(
                 child: Padding(
                   padding: EdgeInsets.all(16),
                   child: Column(
                     children: [
                       _buildSettlementRow(
                         '총 운행 건수',
                         '${mockSettlement['totalTrips']}건',
                       ),
                       Divider(),
                       _buildSettlementRow(
                         '총 운행 요금',
                         '${mockSettlement['totalFare']}원',
                       ),
                       Divider(),
                       _buildSettlementRow(
                         '수수료 (${mockSettlement['commissionRate']}%)',
                         '-${mockSettlement['commission']}원',
                         valueColor: Colors.red,
                       ),
                       Divider(),
                       _buildSettlementRow(
                         '실 정산액',
                         '${mockSettlement['netAmount']}원',
                         labelStyle: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                         valueStyle: TextStyle(
                           fontSize: 18,
                           fontWeight: FontWeight.bold,
                           color: AppTheme.colorOnline,
                         ),
                       ),
                     ],
                   ),
                 ),
               ),

               SizedBox(height: 24),

               // 정산 신청 버튼
               if (!(mockSettlement['isSettled'] as bool))
                 SizedBox(
                   width: double.infinity,
                   child: ElevatedButton(
                     onPressed: () {
                       _showSettlementRequestDialog();
                     },
                     style: ElevatedButton.styleFrom(
                       backgroundColor: AppTheme.colorOnline,
                       padding: EdgeInsets.symmetric(vertical: 16),
                     ),
                     child: Text('정산 신청', style: TextStyle(fontSize: 16)),
                   ),
                 ),
             ],
           ),
         );
       }

       Widget _buildSettlementRow(
         String label,
         String value, {
         TextStyle? labelStyle,
         TextStyle? valueStyle,
         Color? valueColor,
       }) {
         return Padding(
           padding: EdgeInsets.symmetric(vertical: 8),
           child: Row(
             mainAxisAlignment: MainAxisAlignment.spaceBetween,
             children: [
               Text(label, style: labelStyle ?? TextStyle(fontSize: 14)),
               Text(
                 value,
                 style: valueStyle ?? TextStyle(
                   fontSize: 14,
                   fontWeight: FontWeight.bold,
                   color: valueColor,
                 ),
               ),
             ],
           ),
         );
       }

       void _showSettlementRequestDialog() {
         showDialog(
           context: context,
           builder: (context) => AlertDialog(
             title: Text('정산 신청'),
             content: Text('정산을 신청하시겠습니까?\n관리자가 확인 후 처리됩니다.'),
             actions: [
               TextButton(
                 onPressed: () => Navigator.pop(context),
                 child: Text('취소'),
               ),
               TextButton(
                 onPressed: () {
                   Navigator.pop(context);
                   ScaffoldMessenger.of(context).showSnackBar(
                     SnackBar(content: Text('정산 신청이 완료되었습니다 (Mock)')),
                   );
                 },
                 child: Text('신청'),
               ),
             ],
           ),
         );
       }

       String _formatDateTime(DateTime dateTime) {
         return '${dateTime.year}-${dateTime.month.toString().padLeft(2, '0')}-${dateTime.day.toString().padLeft(2, '0')} '
                '${dateTime.hour.toString().padLeft(2, '0')}:${dateTime.minute.toString().padLeft(2, '0')}';
       }

       @override
       void dispose() {
         _tabController.dispose();
         super.dispose();
       }
     }
     ```

---

## 🧩 UI 컴포넌트 인벤토리

### 공통 컴포넌트 (모든 화면에서 재사용)

1. **CustomButton**
   - ElevatedButton, OutlinedButton, TextButton 스타일 통일

2. **CustomTextField**
   - TextField with consistent styling
   - 아이콘 지원
   - 에러 메시지 표시

3. **InfoCard**
   - Card with padding and consistent elevation

4. **LoadingIndicator**
   - CircularProgressIndicator with overlay

5. **CustomAppBar**
   - AppBar with consistent styling

6. **ConfirmDialog**
   - 재사용 가능한 확인 다이얼로그

### 특수 컴포넌트

1. **QR Code Generator** (ReferralQRScreen)
   - `qr_flutter` 패키지 사용

2. **Date Picker** (HistorySettlementScreen)
   - Flutter의 기본 DatePicker 사용

3. **Tab View** (HistorySettlementScreen)
   - TabController + TabBar + TabBarView

4. **Timer Display** (InProgressScreen)
   - 실시간 경과 시간 표시

5. **Dynamic List** (TripPreparationScreen)
   - 경유지 추가/삭제 기능

---

## 📊 구현 우선순위

### **우선순위 1: 핵심 화면 (즉시 구현)**

1. ✅ SplashScreen (완료)
2. ✅ LoginScreen (완료)
3. ⚠️ HomeScreen (마이그레이션 필요)
4. ❌ OfflineScreen
5. ❌ WaitingScreen

**이유**: 앱 진입 → 로그인 → 메인 화면 → 상태별 화면 전환 플로우가 가장 중요

### **우선순위 2: 운행 관련 화면**

6. ❌ TripPreparationScreen
7. ❌ InProgressScreen
8. ❌ CallDetailsScreen

**이유**: 실제 업무 프로세스 핵심 기능

### **우선순위 3: 부가 기능 화면**

9. ❌ HistorySettlementScreen
10. ❌ ReferralQRScreen
11. ❌ SignUpScreen
12. ❌ ForgotPasswordScreen

**이유**: 부가 기능이지만 사용자 경험에 중요

---

## 📅 단계별 구현 계획

### **Phase 1: UI 완성 (Mock 데이터) - 예상 소요: 3-5일**

#### Step 1.1: HomeScreen 마이그레이션 (0.5일)
- [ ] AuthService/SessionService 제거
- [ ] AuthNotifier로 전환
- [ ] AppBar 타이틀을 사무실명으로 변경
- [ ] 상태별 화면 전환 구조 구현 (DriverStatus enum)
- [ ] 운행내역 버튼 추가

#### Step 1.2: 상태별 화면 구현 (1일)
- [ ] OfflineScreen 생성 및 UI 구현
- [ ] WaitingScreen 생성 및 UI 구현
- [ ] Mock 상태 전환 로직 (버튼 클릭 시)

#### Step 1.3: 운행 관련 화면 구현 (1.5일)
- [ ] TripPreparationScreen 생성 및 UI 구현
  - [ ] 출발지/목적지/경유지 입력 필드
  - [ ] 동적 경유지 리스트
  - [ ] 요금 입력
  - [ ] 운행 시작/콜 거부 버튼
- [ ] InProgressScreen 생성 및 UI 구현
  - [ ] 운행 정보 표시
  - [ ] 타이머 구현
  - [ ] 운행 완료 버튼
- [ ] CallDetailsScreen UI 구현
  - [ ] 콜 정보 표시
  - [ ] 상태별 액션 버튼

#### Step 1.4: 부가 기능 화면 구현 (1.5일)
- [ ] HistorySettlementScreen UI 구현
  - [ ] TabBar (운행내역/정산)
  - [ ] 날짜 필터
  - [ ] 리스트 표시
  - [ ] 정산 요약 카드
- [ ] ReferralQRScreen UI 구현
  - [ ] `qr_flutter` 패키지 추가
  - [ ] QR 코드 생성
  - [ ] 추천 통계 표시
- [ ] SignUpScreen UI 구현
  - [ ] 폼 필드들
  - [ ] 지역/사무실 드롭다운
- [ ] ForgotPasswordScreen UI 구현
  - [ ] 이메일 입력 필드

#### Step 1.5: 네비게이션 및 상태 관리 Mock 구현 (0.5일)
- [ ] Routes 업데이트 (새로운 화면들 추가)
- [ ] Mock DriverStatusProvider 구현
- [ ] 화면 간 네비게이션 테스트

#### Step 1.6: 테스트 및 UI 조정 (0.5일)
- [ ] 모든 화면 Android APK 빌드 및 테스트
- [ ] Android 앱과 UI 비교 확인
- [ ] 디자인 조정 및 버그 수정

**Milestone 1 완료**: 모든 화면 UI 완성, Mock 데이터로 작동

---

### **Phase 2: 기능 연결 (실제 데이터) - 예상 소요: 5-7일**

#### Step 2.1: Domain Layer 구현 (2일)
- [ ] Entity 정의
  - [ ] Driver, Call, Settlement, ReferralStats
- [ ] UseCase 구현
  - [ ] UpdateDriverStatusUseCase
  - [ ] GetCallDetailsUseCase
  - [ ] AcceptCallUseCase
  - [ ] StartTripUseCase
  - [ ] CompleteTripUseCase
  - [ ] GetTripHistoryUseCase
  - [ ] RequestSettlementUseCase
  - [ ] GenerateReferralCodeUseCase

#### Step 2.2: Data Layer 구현 (2일)
- [ ] Repository 구현체
  - [ ] DriverRepositoryImpl
  - [ ] CallRepositoryImpl
  - [ ] SettlementRepositoryImpl
- [ ] Firestore DataSource
  - [ ] 쿼리 로직 구현
  - [ ] 실시간 리스너 구현

#### Step 2.3: Presentation Layer (Riverpod Provider) (2일)
- [ ] Provider 구현
  - [ ] driverStatusProvider
  - [ ] callDetailsProvider
  - [ ] tripHistoryProvider
  - [ ] settlementProvider
  - [ ] referralStatsProvider
- [ ] StateNotifier 구현
  - [ ] TripNotifier
  - [ ] SettlementNotifier

#### Step 2.4: UI와 기능 연결 (1-2일)
- [ ] 모든 화면에서 Provider 연결
- [ ] Mock 데이터 제거
- [ ] 실제 Firestore 데이터로 교체
- [ ] 에러 처리 추가

#### Step 2.5: Firebase 통합 (1일)
- [ ] FCM 알림 구현 (새 콜 배차)
- [ ] Firebase Storage (프로필 이미지, 필요 시)
- [ ] Firestore Security Rules 확인

**Milestone 2 완료**: 모든 기능이 실제 Firebase 데이터와 연결되어 작동

---

### **Phase 3: iOS 빌드 및 테스트 - 예상 소요: 2-3일**

#### Step 3.1: iOS 설정 (1일)
- [ ] ios/Runner/Info.plist 권한 추가
  - [ ] NSLocationWhenInUseUsageDescription
  - [ ] NSMicrophoneUsageDescription (음성 입력)
  - [ ] NSPhoneCallUsageDescription (전화 걸기)
- [ ] Firebase iOS 설정
  - [ ] GoogleService-Info.plist 추가
  - [ ] APNs 인증 키 설정
- [ ] CocoaPods 의존성 설치

#### Step 3.2: MacCloud 빌드 (0.5일)
- [ ] MacCloud 서비스 연결
- [ ] iOS 빌드 실행
- [ ] .ipa 파일 생성

#### Step 3.3: iOS 테스트 (1-1.5일)
- [ ] TestFlight 배포
- [ ] iOS 디바이스 테스트
- [ ] iOS 특유 버그 수정
- [ ] UI/UX 조정

**Milestone 3 완료**: iOS 앱 배포 가능 상태

---

## 🛠 기술 스택 및 패턴

### 아키텍처
- **Clean Architecture**: Domain / Data / Presentation 계층 분리
- **State Management**: Riverpod (StateNotifier + Provider)
- **Code Generation**: Freezed (Entity, State 클래스)

### 주요 패키지
```yaml
dependencies:
  flutter:
    sdk: flutter

  # State Management
  flutter_riverpod: ^2.4.9

  # Code Generation
  freezed_annotation: ^2.4.1
  json_annotation: ^4.8.1

  # Firebase
  firebase_core: ^2.24.2
  firebase_auth: ^4.15.3
  firebase_firestore: ^4.13.6
  firebase_messaging: ^14.7.9

  # Storage
  flutter_secure_storage: ^9.0.0

  # DI
  get_it: ^7.6.4

  # QR Code
  qr_flutter: ^4.1.0

  # Others
  intl: ^0.19.0 # 날짜/숫자 포맷팅

dev_dependencies:
  build_runner: ^2.4.6
  freezed: ^2.4.5
  json_serializable: ^6.7.1
```

### 디자인 패턴
1. **Repository Pattern**: Data Layer 추상화
2. **Provider Pattern**: 상태 관리 및 DI
3. **Factory Pattern**: Entity 생성 (Freezed)
4. **Observer Pattern**: Firestore 실시간 리스너

### Riverpod 패턴 예시
```dart
// Provider 정의
final driverStatusProvider = StateNotifierProvider<DriverStatusNotifier, DriverStatus>((ref) {
  final repository = ref.read(driverRepositoryProvider);
  return DriverStatusNotifier(repository);
});

// StateNotifier
class DriverStatusNotifier extends StateNotifier<DriverStatus> {
  final DriverRepository _repository;

  DriverStatusNotifier(this._repository) : super(DriverStatus.offline) {
    _init();
  }

  Future<void> _init() async {
    final status = await _repository.getDriverStatus();
    state = status;
  }

  Future<void> goOnline() async {
    await _repository.updateStatus(DriverStatus.waiting);
    state = DriverStatus.waiting;
  }

  // ...
}

// UI에서 사용
class HomeScreen extends ConsumerWidget {
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final status = ref.watch(driverStatusProvider);

    return Scaffold(
      body: _buildBodyByStatus(status),
    );
  }
}
```

---

## 🔄 작업 재개 시 체크리스트

재부팅 후 작업을 재개할 때 다음 단계를 따르세요:

### 1. 환경 확인
- [ ] Flutter SDK 경로 확인: `C:\src\flutter\bin`
- [ ] Android 디바이스 연결 확인: `flutter devices`
- [ ] VS Code / Android Studio 실행

### 2. 현재 상태 파악
- [ ] 이 문서 읽기
- [ ] `git status`로 변경사항 확인
- [ ] 가장 최근 완료한 Step 확인

### 3. 다음 작업 시작
- [ ] **Phase 1 - Step 1.1부터 시작** (HomeScreen 마이그레이션)
- [ ] 각 Step 완료 시마다 APK 빌드 및 테스트
- [ ] 작업 내용을 이 문서에 체크 표시

### 4. 빌드 및 테스트
```bash
# Flutter clean (필요 시)
flutter clean
flutter pub get

# APK 빌드
cd android
./gradlew assembleDebug --no-daemon

# 디바이스 설치
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📝 중요 노트

### Android vs Flutter 주요 차이점
1. **HomeScreen 구조**
   - Android: 상태별 화면 전환 (Offline/Waiting/TripPrep/InProgress)
   - Flutter(현재): 단순 기능 버튼 그리드
   - **수정 필요**: Android 구조로 변경

2. **State Management**
   - Android: ViewModel + StateFlow
   - Flutter: Riverpod (StateNotifier + Provider)

3. **Firebase 통신**
   - Android: Kotlin Coroutines
   - Flutter: async/await (Dart Future)

### 개발 시 주의사항
1. **하드코딩 금지**: 모든 데이터는 Mock 또는 Firestore에서 로드
2. **에러 처리**: 모든 비동기 작업에 try-catch 추가
3. **로딩 상태**: 모든 네트워크 요청 시 로딩 인디케이터 표시
4. **일관성**: Android 앱과 UI/UX 동일하게 유지

---

## ✅ 진행 상황 추적

### 완료된 작업 (2025-01-18 기준)
- [x] Clean Architecture 구조 설정
- [x] Riverpod State Management 설정
- [x] AuthNotifier & AuthState 구현
- [x] SplashScreen 완성
- [x] LoginScreen 완성
- [x] HomeScreen 기본 UI (마이그레이션 필요)
- [x] Theme 설정
- [x] Routes 설정
- [x] Android APK 빌드 성공

### 진행 중인 작업
- [ ] Phase 1 - Step 1.1: HomeScreen 마이그레이션

### 다음 작업
- [ ] Phase 1 - Step 1.2: OfflineScreen, WaitingScreen 구현

---

**마지막 업데이트**: 2025-01-18
**다음 작업**: Phase 1 - Step 1.1 (HomeScreen 마이그레이션)
**예상 완료일**: Phase 1 (2025-01-23), Phase 2 (2025-01-30), Phase 3 (2025-02-02)

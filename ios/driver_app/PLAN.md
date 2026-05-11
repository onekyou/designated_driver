# iOS 기사앱 (Swift) 개발 계획

## 개요
Kotlin 기사앱의 로직을 Swift로 포팅. Firestore/FCM/Presence 등 백엔드는 동일.

---

## Kotlin → Swift 파일 매핑

### ViewModel / 비즈니스 로직
| Kotlin | Swift (예상) | 핵심 로직 |
|--------|-------------|----------|
| `DriverViewModel.kt` (~1,600줄) | `DriverViewModel.swift` | 콜 수락/거절, 운행, 정산, FCM 처리 |
| `DriverScreenUiState.kt` | `DriverState.swift` | UI 상태 모델 |
| `LoginViewModel.kt` | `LoginViewModel.swift` | 인증 + collectionGroup 쿼리 |
| `SettlementCalc` (Flutter) | `SettlementCalc.swift` | 정산 순수 함수 |

### 화면 (~8개)
| 화면 | Kotlin 참조 | 핵심 기능 |
|------|------------|----------|
| LoginScreen | `ui/login/LoginScreen.kt` | 이메일 로그인 + 자동로그인 |
| SignUpScreen | `ui/login/SignUpScreen.kt` | 3단계 드롭다운 (시도/시군구/사무실) |
| ForgotPasswordScreen | `ui/login/ForgotPasswordScreen.kt` | 비밀번호 재설정 메일 |
| HomeScreen | `ui/home/HomeScreen.kt` | 대기/배차/운행 상태별 표시 |
| NewCallPopup | HomeScreen 내 오버레이 | 신규 콜 수락/거절 |
| HistorySettlementScreen | `ui/home/HistorySettlementScreen.kt` | 운행내역 + 정산 + 업무마감 |
| CallDetailsScreen | `ui/details/CallDetailsScreen.kt` | 콜 상세 정보 |
| ReferralQRScreen | `ui/screens/home/ReferralQRScreen.kt` | 고객 추천 QR |
| SettingsScreen | `ui/home/SettingsScreen.kt` | 설정 |

### 서비스
| Kotlin | Swift (예상) | 역할 |
|--------|-------------|------|
| `MyFirebaseMessagingService.kt` | `AppDelegate` + `UNUserNotificationCenter` | FCM 6종 처리 |
| `PresenceManager.kt` | `PresenceManager.swift` | Firebase Realtime DB |
| `DriverForegroundService.kt` | Background Modes + BGTaskScheduler | 백그라운드 유지 |
| `LockScreenActivity.kt` | `UNNotificationContentExtension` 또는 VoIP Push | 잠금화면 알림 |

---

## iOS 특수 사항

### 잠금화면 콜 알림 (Android LockScreen 대응)
- iOS에는 Activity 개념 없음
- **방안 1**: Critical Alert (의료/안전 앱용, Apple 승인 필요)
- **방안 2**: VoIP Push + CallKit (대리운전에 적합할 수 있음)
- **방안 3**: Time Sensitive Notification (iOS 15+) + 풍부한 알림 UI
- → VoIP Push + CallKit이 가장 현실적 (전화 수신 UI 활용)

### 백그라운드 유지 (Android Foreground Service 대응)
- iOS Background Modes: `remote-notification`, `voip`, `location`
- BGTaskScheduler로 주기적 작업
- Silent Push로 앱 깨우기
- → Android만큼 자유롭지 않으나, FCM + Silent Push 조합으로 커버 가능

### Presence
- Firebase Realtime DB는 iOS에서도 동일하게 동작
- `onDisconnect()` 지원됨
- AppDelegate `applicationDidEnterBackground` → background 상태 전환

---

## 로직 참조 (Flutter 코드, 순수 함수 추출본)

| Flutter 파일 | 참조 로직 |
|-------------|----------|
| `driver_workflow_notifier.dart` | 콜 수락/거절/운행/정산 전체 플로우 |
| `driver_screen_ui_state.dart` | UI 상태 모델 (TodaySettlement, TripHistoryItem, DriverCarryOver) |
| `settlement_calc.dart` | 정산 공식 순수 함수 (officeDeposit, driverShare, adjustedDeposit 등) |
| `fcm_service.dart` | FCM 6종 핸들링 패턴 + 채널 분리 |
| `presence_service.dart` | Realtime DB Presence 패턴 |

---

## 기술 스택 (예상)

- **UI**: SwiftUI
- **아키텍처**: MVVM (ObservableObject + @Published)
- **Firebase**: firebase-ios-sdk (Swift Package Manager)
- **네트워크**: 기본 URLSession (Firestore SDK가 처리)
- **로컬 저장**: UserDefaults (세션) + Keychain (자격증명)
- **최소 타겟**: iOS 16.0

---

## 개발 순서 (제안)

1. 프로젝트 설정 + Firebase 연동
2. 로그인/회원가입 (Firebase Auth)
3. HomeScreen + 콜 상태 표시
4. 콜 수락/거절 (Firestore 트랜잭션)
5. 운행 시작/완료
6. 정산 (HistorySettlementScreen)
7. FCM 수신 + 알림
8. Presence + 백그라운드 처리
9. 잠금화면 알림 (VoIP Push / Critical Alert)

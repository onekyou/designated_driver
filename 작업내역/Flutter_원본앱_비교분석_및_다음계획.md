# Flutter 고객앱 vs 원본 Kotlin 앱 비교 분석 및 다음 단계 계획

**작성일**: 2025-11-17
**현재 상태**: Phase 3 완료 (콜 요청 시스템)

---

## 📊 1. 기능 비교표

| 기능 영역 | 원본 Kotlin 앱 | Flutter 앱 (현재) | 완성도 | 우선순위 |
|-----------|----------------|-------------------|--------|----------|
| **인증** | Phone Auth (SMS) | ✅ 익명 인증 | 50% | 🔴 높음 |
| **사무실 연결** | QR/딥링크/수동 | ✅ Attribution 시스템 | 90% | 🟡 중간 |
| **콜 생성** | GPS + 실시간 모니터링 | ✅ GPS + 음성 입력 | 80% | 🟡 중간 |
| **콜 내역** | 통계 + 상세 내역 | ✅ 기본 리스트 | 60% | 🟡 중간 |
| **포인트 시스템** | 적립/사용/할인 완성 | ❌ 모델만 존재 | 10% | 🔴 높음 |
| **등급 시스템** | Bronze/Silver/Gold/VIP | ❌ 미구현 | 0% | 🟢 낮음 |
| **네비게이션** | BottomNav (4탭) | ❌ 단일 화면 | 0% | 🔴 높음 |
| **프로필** | 프로필 화면 완성 | ❌ 미구현 | 0% | 🟡 중간 |
| **실시간 업데이트** | Firebase 리스너 | ✅ Provider 준비됨 | 70% | 🟡 중간 |
| **FCM 푸시** | 90% 준비 | ❌ 미구현 | 0% | 🟢 낮음 |
| **백버튼 처리** | 완전 구현 | ❌ 미구현 | 0% | 🟢 낮음 |

---

## 🎯 2. 핵심 차이점 분석

### 2.1 인증 시스템 차이 ⚠️ **중요**

**원본 Kotlin 앱:**
```kotlin
// Firebase Phone Authentication
FirebaseAuth.getInstance()
    .signInWithPhoneNumber(phoneNumber, callbacks)
// SMS 6자리 코드 자동 읽기
```

**현재 Flutter 앱:**
```dart
// Firebase 익명 인증
await FirebaseAuth.instance.signInAnonymously();
// 전화번호는 SharedPreferences에만 저장
```

**문제점:**
- 전화번호가 실제 인증되지 않음
- Firebase user.uid와 전화번호가 연결되지 않음
- 보안 취약

**해결 방안:** Phase 4에서 Firebase Phone Auth로 전환 필요

---

### 2.2 포인트 시스템 차이 ⚠️ **중요**

**원본 Kotlin 앱:**
```kotlin
// 완전한 포인트 시스템
- CustomerPoints 모델 (currentPoints, totalEarned, totalUsed)
- PointTransaction 컬렉션 (모든 거래 내역)
- CustomerGrade enum (등급별 적립률)
- 포인트 사용 → 실제 요금 할인
- 실시간 포인트 모니터링
```

**현재 Flutter 앱:**
```dart
// 모델만 존재, 실제 로직 없음
class CustomerCall {
  final int pointsUsed;      // 필드만 있음
  final int? finalFare;      // 사용되지 않음
  final int discountAmount;  // 계산 안 됨
}
// ❌ CustomerPoints 모델 없음
// ❌ PointService 없음
// ❌ 포인트 UI 없음
```

**격차:** 90% 미구현

---

### 2.3 UI/UX 구조 차이 ⚠️ **중요**

**원본 Kotlin 앱:**
```
MainActivity
└── MainNavigation (BottomNavigation)
    ├── 🏠 홈 (HomeScreen)
    │   ├── 사무실 정보 카드
    │   ├── 포인트 카드
    │   ├── 위치 입력
    │   ├── 포인트 사용 섹션
    │   └── 실시간 콜 상태
    ├── 📋 이용내역 (CallHistoryScreen)
    │   ├── 월별 통계 요약
    │   └── 상세 내역 리스트
    ├── ⭐ 포인트 (PointScreen)
    │   ├── 포인트 카드
    │   ├── 등급별 혜택
    │   └── 포인트 내역
    └── 👤 내정보 (ProfileScreen)
        ├── 사용자 정보
        ├── 연결된 사무실
        └── 로그아웃
```

**현재 Flutter 앱:**
```
MaterialApp
└── HomeScreen (단일 화면)
    ├── 사무실 정보 (attribution)
    ├── ❌ 포인트 카드 (없음)
    ├── ❌ 네비게이션 없음
    └── 버튼들만 존재
        ├── 대리운전 요청하기 → CallRequestScreen
        └── 콜 내역 보기 → CallHistoryScreen
```

**격차:** 네비게이션 구조 자체가 다름

---

## 📋 3. 다음 단계 우선순위별 계획

### 🔴 Phase 4: 핵심 기능 보완 (2-3주)

**4.1 Firebase Phone Auth 전환** ⭐ 최우선
- [ ] firebase_auth Phone Auth 구현
- [ ] SMS 인증 코드 입력 UI
- [ ] 전화번호 → Firebase UID 매핑
- [ ] 기존 익명 인증 마이그레이션

**4.2 포인트 시스템 구현** ⭐ 핵심 기능
- [ ] CustomerPoints 모델 생성
- [ ] PointService 구현
  - [ ] 포인트 조회 (getCustomerPoints)
  - [ ] 포인트 적립 (earnPoints)
  - [ ] 포인트 사용 (usePoints)
  - [ ] 포인트 내역 조회 (getPointHistory)
- [ ] PointRepository 구현
- [ ] PointProvider (Riverpod) 구현
- [ ] 실시간 포인트 모니터링 (Stream)

**4.3 BottomNavigation 구조 구현** ⭐ UX 개선
- [ ] MainNavigation widget 생성
- [ ] BottomNavigationBar 구현
- [ ] 4개 탭 구조
  - [ ] 홈 (HomeScreen 개선)
  - [ ] 이용내역 (기존 CallHistoryScreen 개선)
  - [ ] 포인트 (PointScreen 신규)
  - [ ] 내정보 (ProfileScreen 신규)

---

### 🟡 Phase 5: UI/UX 완성 (1-2주)

**5.1 HomeScreen 개선**
- [ ] 포인트 카드 추가
- [ ] 포인트 사용 섹션 추가
- [ ] 실시간 콜 상태 카드 개선
- [ ] 레이아웃 오버플로우 수정 (14px)

**5.2 CallHistoryScreen 개선**
- [ ] 월별 통계 요약 카드 추가
- [ ] 포인트 사용 내역 표시
- [ ] 요금 정보 상세화 (원래요금/할인/최종요금)

**5.3 PointScreen 신규 생성**
- [ ] 포인트 카드 UI
- [ ] 등급 시스템 UI (Bronze/Silver/Gold/VIP)
- [ ] 등급별 혜택 안내
- [ ] 포인트 내역으로 이동 버튼

**5.4 ProfileScreen 신규 생성**
- [ ] 사용자 정보 표시
- [ ] 연결된 사무실 정보
- [ ] 앱 정보 및 버전
- [ ] 로그아웃 기능

**5.5 PointHistoryScreen 신규 생성**
- [ ] 포인트 거래 내역 리스트
- [ ] 적립/사용 구분 UI
- [ ] 날짜별 그룹핑

---

### 🟢 Phase 6: 고급 기능 (추후)

**6.1 실시간 콜 모니터링 개선**
- [ ] 활성 콜 실시간 추적
- [ ] 기사 정보 표시
- [ ] 콜 상태 변화 애니메이션

**6.2 FCM 푸시 알림**
- [ ] FCM 토큰 등록
- [ ] 콜 상태 변경 알림
- [ ] 기사 배정 알림
- [ ] 백그라운드 메시지 처리

**6.3 UX 개선**
- [ ] 백버튼 핸들링 (BackHandler)
- [ ] 로딩 인디케이터 개선
- [ ] 에러 메시지 표준화
- [ ] 애니메이션 추가

---

## 📐 4. 구현 로드맵

### 4주차 계획 (Phase 4)

**Week 1: Phone Auth 전환**
```
Day 1-2: Firebase Phone Auth 구현
Day 3-4: SMS 인증 UI 구현
Day 5: 기존 데이터 마이그레이션
```

**Week 2: 포인트 시스템 기초**
```
Day 1-2: CustomerPoints 모델 + Repository
Day 3-4: PointService 구현
Day 5: Provider 연결 및 테스트
```

**Week 3: BottomNavigation**
```
Day 1-2: 네비게이션 구조 구현
Day 3-4: 4개 탭 기본 화면 생성
Day 5: 네비게이션 통합 테스트
```

**Week 4: 포인트 UI 구현**
```
Day 1-2: HomeScreen 포인트 카드
Day 3-4: PointScreen 구현
Day 5: 통합 테스트
```

---

## 🎯 5. 최종 목표

### 5.1 기능 완성도 목표: 95%
- ✅ Phase 1-3: 기본 구조 (40%)
- 🔄 Phase 4: 핵심 기능 보완 → 70%
- 🔄 Phase 5: UI/UX 완성 → 90%
- 🔄 Phase 6: 고급 기능 → 95%

### 5.2 원본 앱과의 동등성
- **필수**: Phone Auth, 포인트 시스템, BottomNavigation
- **권장**: 실시간 모니터링, 통계, 프로필
- **선택**: FCM 푸시, 백버튼 핸들링

---

## 🚀 6. 즉시 착수할 작업

### 6.1 다음 세션에서 시작할 것 ⭐

**우선순위 1: BottomNavigation 구조 먼저** (1-2일)
→ 이유: UI 구조가 바뀌면 모든 화면 영향, 먼저 틀을 잡아야 함

**우선순위 2: 포인트 시스템** (3-4일)
→ 이유: 핵심 기능이며 다른 화면들이 이에 의존

**우선순위 3: Phone Auth** (2-3일)
→ 이유: 보안은 중요하지만 기능 개발과 병행 가능

### 6.2 권장 개발 순서

```
1. MainNavigation 구조 생성 (0.5일)
   └── BottomNavigationBar만 구현, 화면은 임시

2. 4개 탭 기본 화면 생성 (0.5일)
   └── HomeScreen(기존), HistoryScreen(기존), PointScreen(신규), ProfileScreen(신규)

3. 포인트 모델/서비스 구현 (2일)
   └── CustomerPoints, PointService, PointRepository

4. 포인트 UI 구현 (2일)
   └── HomeScreen 포인트 카드, PointScreen 상세 화면

5. Phone Auth 전환 (2-3일)
   └── firebase_auth Phone Auth, SMS 인증 UI

6. 나머지 UI 개선 (2-3일)
   └── 통계, 프로필, 세부 개선
```

---

## 📝 7. 코드 구조 비교

### 7.1 원본 Kotlin 앱 구조
```
app/
├── data/
│   ├── model/
│   │   ├── CustomerCall.kt
│   │   ├── CustomerPoints.kt ✨
│   │   ├── CustomerGrade.kt ✨
│   │   └── PointTransaction.kt ✨
│   ├── repository/
│   │   ├── CallRepository.kt
│   │   └── PointRepository.kt ✨
│   └── service/
│       ├── CallService.kt
│       └── PointService.kt ✨
├── ui/
│   ├── navigation/
│   │   └── MainNavigation.kt ✨
│   ├── screen/
│   │   ├── HomeScreen.kt
│   │   ├── CallHistoryScreen.kt
│   │   ├── PointScreen.kt ✨
│   │   ├── ProfileScreen.kt ✨
│   │   └── PointHistoryScreen.kt ✨
│   └── component/
│       ├── PointCard.kt ✨
│       └── GradeIndicator.kt ✨
└── viewmodel/
    ├── MainViewModel.kt
    └── PointViewModel.kt ✨
```

### 7.2 현재 Flutter 앱 구조
```
lib/
├── features/
│   ├── auth/
│   │   ├── models/user_model.dart ✅
│   │   ├── providers/auth_provider.dart ✅
│   │   └── screens/phone_input_screen.dart ✅
│   ├── attribution/
│   │   ├── models/attribution_result.dart ✅
│   │   └── providers/attribution_provider.dart ✅
│   ├── call/
│   │   ├── models/
│   │   │   ├── call_model.dart ✅
│   │   │   ├── call_state.dart ✅
│   │   │   └── driver_info.dart ✅
│   │   ├── providers/
│   │   │   ├── call_provider.dart ✅
│   │   │   └── location_provider.dart ✅
│   │   ├── repositories/
│   │   │   └── call_repository.dart ✅
│   │   └── screens/
│   │       ├── call_request_screen.dart ✅
│   │       └── call_history_screen.dart ✅
│   ├── home/
│   │   └── screens/home_screen.dart ✅
│   ├── ❌ points/ (미구현)
│   │   ├── models/
│   │   │   ├── customer_points.dart
│   │   │   ├── customer_grade.dart
│   │   │   └── point_transaction.dart
│   │   ├── providers/
│   │   │   └── point_provider.dart
│   │   ├── repositories/
│   │   │   └── point_repository.dart
│   │   ├── services/
│   │   │   └── point_service.dart
│   │   └── screens/
│   │       ├── point_screen.dart
│   │       └── point_history_screen.dart
│   ├── ❌ profile/ (미구현)
│   │   └── screens/profile_screen.dart
│   └── ❌ navigation/ (미구현)
│       └── main_navigation.dart
└── core/
    ├── providers/app_provider.dart ✅
    └── theme/app_theme.dart ✅
```

### 7.3 추가 필요 파일 목록

**Phase 4에서 생성할 파일들:**
```
lib/features/
├── points/
│   ├── models/
│   │   ├── customer_points.dart          ⭐ 필수
│   │   ├── customer_grade.dart           ⭐ 필수
│   │   └── point_transaction.dart        ⭐ 필수
│   ├── providers/
│   │   └── point_provider.dart           ⭐ 필수
│   ├── repositories/
│   │   └── point_repository.dart         ⭐ 필수
│   ├── services/
│   │   └── point_service.dart            ⭐ 필수
│   └── screens/
│       ├── point_screen.dart             🟡 중요
│       └── point_history_screen.dart     🟡 중요
├── profile/
│   └── screens/
│       └── profile_screen.dart           🟡 중요
└── navigation/
    └── main_navigation.dart              ⭐ 필수
```

---

## ✅ 8. 결론 및 권장사항

### 8.1 현재 상태 평가
- **완성도**: 약 40% (기본 구조만 완성)
- **원본 대비**: 약 45% 수준
- **격차**: 포인트 시스템, 네비게이션 구조, Phone Auth

### 8.2 다음 세션 시작점

**즉시 시작할 것:**
1. ✅ MainNavigation 구조 생성 (BottomNavigationBar)
2. ✅ 4개 탭 기본 화면 뼈대 생성
3. ✅ CustomerPoints/CustomerGrade 모델 생성

**목표:**
- Phase 4 시작: BottomNavigation + 포인트 시스템 기초
- 2-3주 후: 원본 앱의 70-80% 수준 도달

### 8.3 최종 목표 (4주 후)
- ✅ Firebase Phone Auth
- ✅ 완전한 포인트 시스템
- ✅ 4개 탭 네비게이션
- ✅ 실시간 콜 모니터링
- ✅ 원본 앱과 동등한 수준 (90%+)

---

**다음 세션 시작 명령어:**
```
"Phase 4 시작: MainNavigation 구조와 BottomNavigationBar 구현해줘"
```

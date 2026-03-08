# customer-analyst 지속성 문서

**마지막 업데이트**: 2026-03-07
**담당**: Customer App 전체 분석 (콜마당 팀)
**상태**: 세션 3에서 집중 분석 완료 (CUST-01~10, SEC-C01~C05), 주요 4건 수정 완료

---

## 1. 담당 범위

Customer App: 고객용 Android 앱. 콜 요청/취소, 기사 배정 알림, 포인트 시스템.
- Jetpack Compose + Firebase 기반
- 100% FCM 의존 (실시간 Firestore 리스너 주석 처리됨)
- 포인트 시스템: Cloud Functions에서 단일화 완료 (Phase 1에서 수정)

---

## 2. 핵심 파일 목록

### 비즈니스 로직

| 파일 | 경로 | 역할 | 줄수 |
|------|------|------|------|
| MainViewModel.kt | `customer_app/.../ui/main/MainViewModel.kt` | 콜 요청/취소, FCM 수신, 포인트 | ~1,038 |
| CallService.kt | `customer_app/.../service/CallService.kt` | Firestore 콜 CRUD | ~142 |
| PointService.kt | `customer_app/.../service/PointService.kt` | 포인트 조회/적립/사용 | ~312 |

### FCM/알림

| 파일 | 경로 | 역할 | 줄수 |
|------|------|------|------|
| MyFirebaseMessagingService.kt | `customer_app/.../service/MyFirebaseMessagingService.kt` | FCM 4개 타입 처리 | ~330 |

### 인증

| 파일 | 경로 | 역할 |
|------|------|------|
| PhoneAuthViewModel.kt | `customer_app/.../ui/auth/PhoneAuthViewModel.kt` | 전화번호 인증 |
| AnonymousAuthViewModel.kt | `customer_app/.../ui/auth/AnonymousAuthViewModel.kt` | 익명 인증 |
| ProfileSetupViewModel.kt | `customer_app/.../ui/profile/ProfileSetupViewModel.kt` | 프로필 설정 |

### 모델/상수

| 파일 | 경로 | 역할 |
|------|------|------|
| CallStatus.kt | `customer_app/.../ui/main/CallStatus.kt` | 콜 상태 enum |
| CustomerCall.kt | `customer_app/.../data/model/CustomerCall.kt` | 콜 데이터 클래스 |
| CustomerPoints.kt | `customer_app/.../data/model/CustomerPoints.kt` | 포인트 데이터 |
| PointTransaction.kt | `customer_app/.../data/model/PointTransaction.kt` | 포인트 거래 |

### UI

| 파일 | 경로 | 역할 |
|------|------|------|
| HomeScreen.kt | `customer_app/.../ui/main/HomeScreen.kt` | 메인 홈 화면 |
| MainNavigation.kt | `customer_app/.../ui/navigation/MainNavigation.kt` | 네비게이션 |
| PointScreen.kt | `customer_app/.../ui/point/PointScreen.kt` | 포인트 화면 |
| CallHistoryScreen.kt | `customer_app/.../ui/history/CallHistoryScreen.kt` | 콜 이력 |

### 기타 서비스

| 파일 | 경로 | 역할 |
|------|------|------|
| LocationService.kt | `customer_app/.../service/LocationService.kt` | 위치 서비스 |
| PresenceManager.kt | `customer_app/.../service/PresenceManager.kt` | RTDB Presence |
| BannerAdService.kt | `customer_app/.../service/BannerAdService.kt` | 배너 광고 |
| StepCounterService.kt | `customer_app/.../service/StepCounterService.kt` | 만보기 |

---

## 3. 고객이 보는 상태 흐름

```
콜 요청 -> REQUESTED (로컬)
    | FCM: CALL_RECEIVED (전화콜 시)
기사 배정 -> ASSIGNED (FCM: DRIVER_ASSIGNED)
    | FCM: CALL_STATUS_UPDATE (기사 수락/운행시작) -- Phase 1에서 추가됨
운행 완료 -> COMPLETED (FCM: RIDE_COMPLETED)
취소 -> CANCELED (FCM: CALL_CANCELLED) -- Phase 1에서 수정됨
```

---

## 4. FCM 매핑 (Cloud Functions -> Customer App)

| 함수명 | FCM type | Customer 처리 | 상태 |
|--------|----------|-------------|------|
| oncallassigned | DRIVER_ASSIGNED | O (팝업+진동) | 정상 |
| notifyCustomerOnPhoneCall | CALL_RECEIVED | O (알림) | 정상 |
| notifyCustomerOnComplete | RIDE_COMPLETED | O (팝업) | 정상 |
| notifyCustomerOnComplete (포인트) | POINTS_EARNED | X 미처리 | 후순위 |
| onCallCancelledByDriver | CALL_CANCELLED | O (팝업 제거) | 수정완료 (CANCELED 조건 추가) |
| notifyCustomerOnOfficeClosed | OFFICE_CLOSED | X 미처리 | 후순위 |
| (Phase 1 추가) | CALL_STATUS_UPDATE | O | 수정완료 (ACCEPTED/IN_PROGRESS) |

---

## 5. 발견 이슈 및 수정 현황

### 수정 완료 (Phase 1~3에서 해결)

| ID | 심각도 | 이슈 | 수정 내용 |
|----|--------|------|----------|
| CUST-01 | Critical | 관리자 취소 시 Customer FCM 미전달 | onCallCancelledByDriver에 "CANCELED" 조건 추가 |
| CUST-02 | Critical | 포인트 차감 후 콜 생성 실패 시 복구 없음 | requestCall() catch에서 refundPoints() 호출 |
| CUST-04 | High | FCM 유일 경로 + fallback 없음 | 앱 포그라운드 진입 시 Firestore 1회 조회 추가 |
| BUG-D12/CUST-10 | Critical | 3자 이중 포인트 적립 | CF 일원화, Customer/Driver 적립 제거 |
| CUST-03 | Critical | ACCEPTED/IN_PROGRESS FCM 없음 | onCallStatusChanged에서 Customer FCM 추가 |
| CUST-06/NEW-13 | High | CANCELLED/CANCELED 철자 불일치 | 취소 상태 문자열 통일 |

### 잔여 이슈 (후순위/보안)

| ID | 심각도 | 이슈 | 상태 |
|----|--------|------|------|
| CUST-05 | High | POINTS_EARNED FCM 미처리 (포그라운드) | 후순위 |
| CUST-07 | High | FCM 토큰 저장 실패 시 재시도 없음 | 후순위 (onNewToken+앱시작으로 사실상 커버) |
| CUST-12 | Medium | OFFICE_CLOSED FCM 미처리 | 후순위 |
| SEC-C01 | Critical | 콜 생성 비인증 (if true) | 보안 (플레이스토어 배포 전 필수) |
| SEC-C02 | High | customerPoints 소유권 미검증 | 보안 |
| SEC-C03 | High | pointTransactions 위조 생성 방지 | 보안 |
| SEC-C04 | High | customerInfo FCM 토큰 탈취 방지 | 보안 |

---

## 6. 크로스 검증 대상

### manager-analyst (Call Manager)
- 배차/취소 시 Customer FCM 전송 여부
- 콜 상태 전이와 Customer 알림 매핑

### driver-analyst (Driver App)
- 운행 상태 변경 시 Customer 알림 연계
- 포인트 적립 단일화 (CF) 검증

### firebase-analyst (Cloud Functions + Firebase)
- FCM 트리거 함수와 Customer 처리 매핑
- 보안 규칙 (SEC-C01~C04)
- 포인트 CF 로직

### detector-analyst (Call Detector)
- 콜 생성 시 Customer 알림 연계

---

## 7. 상세 분석 참조
- `.agent-teams/customer-app-analysis.md` - 세션 3 집중 분석 결과 (17건 상세)

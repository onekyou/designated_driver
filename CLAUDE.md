# 대리운전 통합 플랫폼 프로젝트 문서

## 프로젝트 개요

이 프로젝트는 지방 대리운전 회사를 위한 통합 관리 플랫폼입니다. 현재 5명의 대리기사와 3명의 픽업기사가 근무하는 환경에서, 3대의 전화기로 들어오는 고객 호출을 효율적으로 관리하고 배차하는 시스템입니다.

### 주요 목표
- 무작위로 걸려오는 호출 전화를 자동으로 감지하고 Firebase에 저장
- 관리자가 실시간으로 호출을 확인하고 기사에게 배차
- 기사 앱을 통한 운행 상태 관리 및 정산
- 다중 사무실 지원 및 콜 공유 시스템 구축

## 시스템 아키텍처

### 전체 구성
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Call Detector  │     │  Call Manager   │     │   Driver App    │
│   (Android)     │     │   (Android)     │     │   (Android)     │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                         │
         └───────────────────────┴─────────────────────────┘
                                 │
                         ┌───────┴────────┐
                         │   Firebase     │
                         │  (Firestore)   │
                         └───────┬────────┘
                                 │
                         ┌───────┴────────┐
                         │Cloud Functions │
                         └────────────────┘
```

### 데이터 흐름
1. **콜 발생**: 고객이 사무실 전화로 전화
2. **콜 감지**: Call Detector 앱이 전화를 감지하고 Firebase에 정보 저장
3. **콜 확인**: Call Manager 앱에서 실시간으로 새로운 콜 확인
4. **배차**: 관리자가 대기 중인 기사 중 한 명을 선택하여 배차
5. **알림**: FCM을 통해 기사 앱에 푸시 알림 전송
6. **운행 관리**: 기사가 콜을 수락하고 운행 상태를 업데이트
7. **정산**: 운행 완료 후 정산 처리

## Firebase 데이터 구조

### Firestore 컬렉션 구조
```
firestore/
├── admins/                     # 관리자 정보
│   └── {adminId}/
│       ├── createdAt
│       ├── name
│       ├── associatedRegionId
│       ├── associatedOfficeId
│       ├── email
│       └── fcmToken
│
├── pending_drivers/            # 가입 승인 대기 기사
│   └── {userId}/
│       ├── name
│       ├── phoneNumber
│       ├── targetRegionId
│       └── targetOfficeId
│
├── regions/                    # 지역 정보
│   └── {regionId}/
│       ├── name
│       └── offices/            # 사무실 정보
│           └── {officeId}/
│               ├── name
│               ├── calls/      # 호출 정보 (핵심 데이터)
│               │   └── {callId}/
│               │       ├── phoneNumber
│               │       ├── customerName
│               │       ├── status
│               │       ├── assignedDriverId
│               │       ├── departure_set
│               │       ├── destination_set
│               │       ├── fare_set
│               │       └── ...
│               │
│               ├── designated_drivers/  # 대리 기사
│               │   └── {driverId}/
│               │       ├── name
│               │       ├── phoneNumber
│               │       ├── authUid
│               │       ├── status
│               │       └── fcmToken
│               │
│               ├── points/              # 포인트 정보
│               │   └── points/
│               │       ├── balance
│               │       └── updatedAt
│               │
│               └── dailySettlements/    # 일일 정산
│                   └── {dateId}/
│                       └── sessions/
│
├── shared_calls/               # 공유 콜
│   └── {sharedCallId}/
│       ├── status (OPEN/CLAIMED/COMPLETED)
│       ├── sourceRegionId
│       ├── sourceOfficeId
│       ├── targetRegionId
│       ├── claimedOfficeId
│       └── ...
│
└── point_transactions/         # 포인트 거래 내역
    └── {txId}/
```

### 보안 규칙 주요 내용
- 관리자는 자신이 속한 사무실의 데이터만 접근 가능
- 기사는 자신의 정보와 배정된 콜만 접근 가능
- 콜 생성은 누구나 가능 (Call Detector용)
- 포인트 문서는 서버(Cloud Functions)만 수정 가능

## 각 앱의 역할과 주요 기능

### 1. Call Detector (전화 감지 앱)
**패키지**: `com.example.calldetector`

**주요 기능**:
- 수신/발신 전화 감지
- Firebase에 콜 정보 자동 업로드
- 사무실 상태에 따른 전송 경로 분기
- 백그라운드 서비스로 상시 실행

**핵심 컴포넌트**:
- `CallReceiver`: 전화 상태 변경 감지
- `CallDetectorService`: Firebase 업로드 처리
- `DetectorConfigViewModel`: 설정 관리

### 2. Call Manager (관리자용 앱)
**패키지**: `com.designated.callmanager`

**주요 기능**:
- 실시간 콜 목록 확인 및 관리
- 기사 배차 및 상태 관리
- 정산 관리 시스템
- 공유 콜 처리
- 포인트 관리
- FCM 푸시 알림

**핵심 화면**:
- `DashboardScreen`: 메인 대시보드
- `SettlementTabHost`: 정산 관리 탭
- `SharedCallSettingsScreen`: 공유 콜 설정
- `DriverManagementScreen`: 기사 관리

**주요 데이터 모델**:
- `CallInfo`: 콜 정보
- `DriverInfo`: 기사 정보
- `PointsInfo`: 포인트 정보
- `SettlementData`: 정산 데이터

### 3. Driver App (기사용 앱)
**패키지**: `com.designated.driverapp`

**주요 기능**:
- 배차된 콜 수신 및 수락
- 운행 상태 관리 (대기→배차→수락→운행중→완료)
- 출발지/도착지/요금 설정
- 운행 이력 조회
- FCM 푸시 알림

**핵심 화면**:
- `HomeScreen`: 메인 화면 (상태별 UI)
- `WaitingScreen`: 대기 상태
- `TripPreparationScreen`: 운행 준비
- `InProgressScreen`: 운행 중
- `HistorySettlementScreen`: 운행 이력

**상태 관리**:
- `DriverViewModel`: 중앙 상태 관리
- `DriverStatus`: 기사 상태 enum

## Cloud Functions

### 주요 함수

1. **oncallassigned**
   - 콜이 기사에게 배정될 때 FCM 알림 전송
   - 무효한 FCM 토큰 자동 정리

2. **onSharedCallClaimed**
   - 공유 콜이 수락될 때 처리
   - CLAIMED 단계: 대상 사무실 calls 컬렉션에 복사
   - COMPLETED 단계: 포인트 정산 처리

3. **onSharedCallCreated**
   - 새로운 공유 콜 생성 시 관련 사무실에 알림

4. **sendNewCallNotification**
   - 새로운 콜(WAITING 상태) 생성 시 관리자에게 알림

5. **finalizeWorkDay**
   - 일일 정산 마감 처리
   - 정산 세션 생성

## 주요 비즈니스 로직 흐름

### 1. 일반 콜 처리 흐름
```
1. 콜 생성 (Call Detector)
   - status: WAITING
   - phoneNumber, timestamp 등 기본 정보

2. 배차 (Call Manager)
   - 관리자가 기사 선택
   - status: WAITING → ASSIGNED
   - assignedDriverId 설정

3. 수락 (Driver App)
   - 기사가 콜 수락
   - status: ASSIGNED → ACCEPTED

4. 운행 준비 (Driver App)
   - 출발지, 도착지, 요금 설정
   - departure_set, destination_set, fare_set 업데이트

5. 운행 시작 (Driver App)
   - status: ACCEPTED → IN_PROGRESS

6. 운행 완료 (Driver App)
   - status: IN_PROGRESS → AWAITING_SETTLEMENT
   - 결제 방법 선택

7. 정산 확인 (Call Manager)
   - status: AWAITING_SETTLEMENT → COMPLETED
```

### 2. 공유 콜 처리 흐름
```
1. 사무실 마감 시 콜 공유
   - shared_calls에 문서 생성
   - status: OPEN

2. 다른 사무실에서 수락
   - status: OPEN → CLAIMED
   - 대상 사무실 calls에 복사

3. 운행 완료 시
   - status: CLAIMED → COMPLETED
   - 포인트 정산 (10% 수수료)
```

## 개발 환경 설정

### 필수 요구사항
- Android Studio (최신 버전)
- Node.js 16+ (Cloud Functions)
- Firebase CLI
- Kotlin 1.8+

### Firebase 설정
1. Firebase 프로젝트 생성
2. Android 앱 3개 등록
3. `google-services.json` 각 앱에 추가
4. Firestore 보안 규칙 배포
5. Cloud Functions 배포

### 앱별 설정

**Call Detector**:
- 전화 상태 권한 필요
- 백그라운드 실행 권한
- 오버레이 권한

**Call Manager**:
- 알림 권한
- 오버레이 권한 (팝업용)

**Driver App**:
- 알림 권한
- 위치 권한 (선택)

## 주의사항 및 제약사항

### 권한 관련
- Android 13+ 에서는 알림 권한 명시적 요청 필요
- 백그라운드 실행 제한 고려
- 배터리 최적화 예외 설정 권장

### 데이터 일관성
- Firestore 트랜잭션 사용으로 동시성 문제 해결
- 상태 전환은 정해진 순서대로만 가능
- 포인트 변경은 서버 사이드에서만 처리

### 성능 고려사항
- 실시간 리스너는 필요한 경우에만 사용
- 대량 데이터 쿼리 시 페이징 처리
- FCM 토큰 주기적 갱신

### 보안 사항
- 사용자 인증 필수
- 역할 기반 접근 제어
- 민감한 정보 암호화 저장

### 확장성
- 다중 사무실 지원 구조
- 지역별 독립적 운영 가능
- 향후 픽업 기사 무전 기능 추가 예정

## 디버깅 팁

### 로그 확인
- Logcat에서 태그별 필터링
- Cloud Functions 로그는 Firebase Console에서 확인

### 일반적인 문제 해결
1. FCM 알림 미수신: 토큰 갱신 확인
2. 상태 업데이트 실패: 보안 규칙 확인
3. 백그라운드 실행 중단: 배터리 최적화 설정

### 테스트 환경
- Firebase Emulator Suite 활용
- 실제 기기에서 테스트 권장
- 다양한 Android 버전 테스트

## 개발 현황 및 향후 계획

### 완료된 단계
1. **Phase 1 완료**: 단일 사무실 MVP
   - 기본 콜 접수 및 배차 시스템
   - 기사 앱 연동

2. **Phase 2 완료**: 운영 효율화
   - 호출 상태 관리 고도화
   - 정산 시스템 구축
   - FCM 푸시 알림 시스템

3. **Phase 3 완료**: 다중 사무실 및 콜 공유
   - 다중 사무실 지원 구조 구현
   - 공유 콜 시스템 (OPEN → CLAIMED → COMPLETED)
   - 포인트 기반 정산 시스템
   - 사무실 간 10% 수수료 자동 처리

### 현재 구현된 주요 기능
- **콜 공유 시스템**: 마감된 사무실의 콜을 다른 사무실에서 처리
- **포인트 시스템**: 충전, 차감, 거래 내역 관리
- **일일 정산**: 세션 기반 정산 관리
- **실시간 동기화**: 모든 앱 간 실시간 데이터 동기화

### 향후 개발 계획
4. **Phase 4 예정**: 고급 기능
   - 고급 통계 및 리포트 (사무실별, 기사별, 기간별)
   - 고객 관리 (CRM) 기능
   - 대시보드 분석 도구
   
5. **Phase 5 예정**: 픽업 기사 무전 시스템
   - Agora SDK를 활용한 실시간 음성 통신
   - 픽업 기사 전용 무전기 앱
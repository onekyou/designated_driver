# 콜마당 Agent Teams 전체 상황

> 마지막 업데이트: 2026-02-24 세션5 종료 시점

## 프로젝트 아키텍처

대리운전 사무실용 B2B SaaS. 4개 Android 앱 + Firebase 기반.

```
Call Detector ──┐
                ├── Firestore (provinces/cities/offices 구조)
Call Manager ───┤    ├── Cloud Functions (index.ts ~2100줄 + handlers/)
                ├── RTDB (Presence)
Driver App ─────┤    └── FCM (Push)
                │
Customer App ───┘
```

- **Call Detector**: 전화 감지 + 배차 (관리자 멀티폰)
- **Call Manager**: 전체 통제, 콜 접수/배정, Detector 기능 내장
- **Driver App**: 기사용, 콜 수락/거절/운행/정산
- **Customer App**: 고객용, 상태 확인 (FCM 100% 의존)
- **Firestore 구조**: `provinces/{pId}/cities/{cId}/offices/{oId}/` (attributed node)
- **상태 흐름**: WAITING → ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED

## Agent Teams 구성

| 팀원 | 담당 | 크로스 검증 대상 |
|------|------|----------------|
| **detector-analyst** | Call Detector | manager, driver |
| **manager-analyst** | Call Manager | detector, driver, customer |
| **driver-analyst** | Driver App | manager, detector, customer |
| **firebase-analyst** | Firestore/RTDB/Cloud Functions | 전 팀원 |
| **customer-analyst** | Customer App | manager, driver, firebase |
| **team-lead** | 종합 조율, 보고서 작성 | - |

## 완료된 분석 (세션 1~4)

### 세션 1: V2 코드 분석 + 크로스 검증
- 4개 앱 전체 코드 리뷰, 상태 전이 매핑, 크로스 검증 프레임워크 구축
- CROSS-01~07, NEW-08~15, BUG-D08~D12 발견

### 세션 2: 코드 수정 + 재검증
- 4건 코드 수정 (아래 "수정 완료" 참조)
- CD-01 마감 이중 공유콜 방지

### 세션 3: Customer App 집중 분석
- CUST-01~10 발견, SEC-C01~C05 보안 이슈 발견
- 상세: `.agent-teams/customer-app-analysis.md`

### 세션 4: 정산 로직 크로스 검증
- STL-01~11 발견 (Manager↔Driver↔CF 3-Way 대조)
- 시나리오 A~E 시뮬레이션 완료
- 상세: `.agent-teams/settlement-simulation.md`

### 세션 5 (현재): STL-09 수정 시도 → 토큰 부족으로 문서화만 완료
- STL-09 수정 방향 settlement-simulation.md에 기록
- 파일럿 운영 가이드 추가 (전체내역 초기화 전 CONFIRMED 필수)

## 수정 완료 (4건)

| 이슈 | 수정 내용 | 파일 |
|------|----------|------|
| CROSS-01 | 기사 상태 4자 통일 ("ASSIGNED") | DispatchActivity.kt:172,275 / index.ts:1198 |
| CROSS-07 일부 | cancelCall() 기사 WAITING 복구 + FCM | DashboardViewModel.kt:809-868 / index.ts:4368-4431 |
| Driver App 취소 수신 | "call_cancelled" 전체 구현 | Constants.kt / MyFirebaseMessagingService.kt / DriverViewModel.kt / MainActivity.kt |
| CD-01 | 마감 시 RINGING+IDLE 이중 공유콜 생성 방지 | CallDetectorService.kt:50,190,691-700,907 |

## 전체 미해결 이슈 현황

### 통합 집계 (중복 제거)

| 심각도 | 건수 | 항목 |
|--------|------|------|
| **Critical** | 11건 | NEW-11(이중배차), NEW-12(Callable인증), CROSS-07(rejectCall), NEW-08(crashlytics), NEW-10(크래시감지), BUG-D11(공유콜ASSIGNED취소잠김), CUST-01(관리자취소FCM), CUST-02(포인트비원자적), BUG-D12(3자이중포인트), SEC-C01(콜생성비인증), **STL-09(carryOver이중계산)** |
| **High** | 13건 | 공유콜필터, CROSS-02(enum불일치), CROSS-05(Detector동기화), CUST-03(ACCEPTED/IN_PROGRESS FCM없음), CUST-04(100%FCM의존), CUST-07(토큰재시도없음), SEC-C02(포인트소유권), SEC-C03(거래위조), SEC-C04(FCM토큰탈취), CUST-06(CANCELLED철자), **STL-01(외상/이체 creditAmount 미기록)**, **STL-02(Manager pointsUsed=0)**, **STL-03(submitDailySettlement settlementLastCleared 미갱신)** |
| **Medium** | 11건 | CROSS-06(비원자적), NEW-13(CANCELED철자), NEW-15(ASSIGNED타임아웃없음), BUG-D08(하향), 백그라운드취소, LockScreen취소, reopenSharedCall보안, **STL-04(totalFare≠부분합)**, **STL-05(반올림 불일치)**, **STL-06(settlementLastCleared 2-Way경로)**, **STL-07(매니저확인 후 dailySettlement 누락)** |
| **Low** | 3건 | approveDriver한글(데드코드), acknowledgeNotification인증, **STL-08(현금+포인트 creditAmount 의미혼동)** |
| **Info** | 2건 | **STL-10(구/신 정산 시스템 공존)**, **STL-11(자동/수동 finalize 공존)** |
| **합계** | **40건** | Critical 11 + High 13 + Medium 11 + Low 3 + Info 2 |

### 운영 긴급
- Cloud Functions 소스/빌드 불일치: `firebase deploy --only functions` 필요 (src 수정 반영 안 됨)
- **⚠️ STL-09 임시 운영 가이드**: 전체내역 초기화 전에 **반드시 기사별 정산 확인(CONFIRMED) 먼저** 완료할 것

### 오탐 확정 (3건)
- BUG-D09: 필드명 불일치 (의도적 설계)
- NEW-09: doc.id vs authUid (등록 흐름에서 보장)
- isOfficeAdmin 경로 불일치 (top-level admins 3곳 일치)

### 해소 확정 (4건)
- BUG-D05: cancelCall FCM 전송 추가
- CROSS-01: 기사 상태 4자 통일
- CD-01 (NEW-05): 마감 시 RINGING+IDLE 이중 공유콜 생성 방지
- Driver App 취소 수신: call_cancelled 전체 구현

## 수정 Phase 로드맵

### Phase 1: 보안 긴급 패치 (미착수)
- [ ] Callable Functions 14개에 request.auth 검증 추가
- [ ] RTDB rules `read/write: true` → 인증 기반 규칙으로 변경
- [ ] Firestore rules `request.auth == null` 패턴 정리
- [ ] device_status/device_alerts 컬렉션 보안 규칙 추가

### Phase 2: 데이터 무결성 (미착수)
- [ ] **STL-09 (Critical)**: processCarryOverOnFinalize에 realDeposit 반영 (Driver submitDailySettlement 공식과 통일) ← **다음 세션 최우선**
- [ ] NEW-11: 배차 시 Firestore transaction + status=="WAITING" 체크
- [ ] BUG-D12: 포인트 컬렉션명 통일 (또는 Driver App 포인트 로직 제거)
- [ ] CROSS-07: rejectCall() 구현
- [ ] NEW-13: CANCELED/CANCELLED 철자 통일

### Phase 3: 기능 개선 (미착수)
- [ ] CROSS-05: 내장 vs 독립 Detector 코드 동기화
- [ ] 공유콜 OPEN vs SHARED 필터 수정
- [ ] 백그라운드/LockScreen 취소 처리
- [ ] crashlytics-monitor 필드명 수정 (provinces 구조 반영)

## 상세 문서 목록
- `.agent-teams/CROSS_VERIFICATION_LOG.md` - 크로스 검증 기록 (세션 1~4)
- `.agent-teams/customer-app-analysis.md` - Customer App 분석 결과
- `.agent-teams/settlement-simulation.md` - 정산 로직 크로스 검증 + 시나리오 시뮬레이션
- `.agent-teams/REVIVAL_PROMPT.md` - 다음 세션 부활 프롬프트
- `.agent-teams/detector-analyst.md` 등 - 팀원별 분석 문서

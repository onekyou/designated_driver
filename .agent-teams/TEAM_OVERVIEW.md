# 콜마당 Agent Teams 전체 상황

> 마지막 업데이트: 2026-04-11 재검증 완료

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
- **Customer App**: 고객용, 상태 확인 (FCM + Firestore fallback)
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

### 홈페이지 검토팀 (2026-04-10~)

| 팀원 | 담당 | 컨텍스트 파일 |
|------|------|--------------|
| **homepage-tech** | HTML/CSS/JS 코드 품질, 반응형, 성능, 접근성, SEO | `.agent-teams/homepage-tech.md` |
| **homepage-design** | UI/UX 디자인 평가, 타 웹사이트 벤치마킹, 트렌드 분석 | `.agent-teams/homepage-design.md` |
| **리드 (메인)** | 두 팀원 결과 취합, 종합 평가, 우선순위 결정 | - |

## 완료된 분석 (세션 1~5 + 4/11 재검증)

### 세션 1: V2 코드 분석 + 크로스 검증
- 4개 앱 전체 코드 리뷰, 상태 전이 매핑, 크로스 검증 프레임워크 구축

### 세션 2: 코드 수정 + 재검증
- 4건 코드 수정 (CROSS-01, CROSS-07 일부, Driver 취소, CD-01)

### 세션 3: Customer App 집중 분석
- CUST-01~10, SEC-C01~C05 발견

### 세션 4: 정산 로직 크로스 검증
- STL-01~11 발견 (Manager↔Driver↔CF 3-Way 대조)

### 세션 5: STL-09 문서화

### 4/11 재검증 (손님앱 수정 계기)
- 손님앱 수정 2건 (Install Referrer 파싱 + 지역명 동적 조회) 검증
- 7일 350콜 시뮬레이션 ALL PASS (180 CP / 0 FAIL)
- **전체 40건 미해결 이슈 실제 코드 기준 재검증 완료**
- 결과: 해소 19건, 오탐 7건, 심각도 하향 11건, 조치 권장 4건

## 수정 완료 (4건 + 2건)

| 이슈 | 수정 내용 | 파일 |
|------|----------|------|
| CROSS-01 | 기사 상태 4자 통일 ("ASSIGNED") | DispatchActivity.kt / index.ts |
| CROSS-07 일부 | cancelCall() 기사 WAITING 복구 + FCM | DashboardViewModel.kt / index.ts |
| Driver App 취소 수신 | "call_cancelled" 전체 구현 | Constants.kt / MyFirebaseMessagingService.kt / DriverViewModel.kt / MainActivity.kt |
| CD-01 | 마감 시 RINGING+IDLE 이중 공유콜 생성 방지 | CallDetectorService.kt |
| 손님앱 Referrer 파싱 | `=` 없는 파라미터 크래시 방지 (mapNotNull) | customer_app/MainActivity.kt:215-218 |
| 손님앱 지역명 | 하드코딩 → Firestore provinces 동적 조회 | customer_app/MainViewModel.kt:162-175 |

## 전체 이슈 현황 (2026-04-11 재검증 기준)

### 해소 확정 (19건)

| 이슈 | 근거 |
|------|------|
| CROSS-01 | DriverStatus enum PREPARING 추가, 하드코딩 해소 |
| CROSS-07 | rejectCall() 트랜잭션 기반 구현 완료 (DriverViewModel + LockScreenActivity) |
| CD-01 | 마감 시 이중 공유콜 방지 |
| Driver 취소 수신 | call_cancelled 전체 구현 |
| BUG-D09 | 필드명 불일치 (의도적 설계) |
| NEW-09 | doc.id vs authUid (등록 흐름에서 보장) |
| isOfficeAdmin | top-level admins 3곳 일치 |
| NEW-08 | 4개 앱 Crashlytics 설정 완료 |
| NEW-10 | onCallDetectorCrash CF + CrashReportService 구현 |
| NEW-11 | runTransaction + status 체크로 이중배차 방지 |
| NEW-15 | checkAssignedTimeout CF 매 1분 스케줄 실행 |
| CUST-01 | CF가 관리자 취소(CANCELED)도 고객 FCM 전송 |
| CUST-03 | CF onCallStatusChanged가 ACCEPTED/IN_PROGRESS 시 고객 FCM 전송 |
| BUG-D11 | cancelTrip()이 CANCELLED_BY_DRIVER 사용 + 보안 규칙 허용 |
| BUG-D12 | 기사앱 포인트 로직 완전 제거, CF 단일화 |
| STL-09 | calculatedCarryOver 사용으로 이중계산 원천 차단 |
| STL-07 | 트랜잭션 내 원자적 처리로 dailySettlement 누락 불가 |
| BUG-D01 | rejectCall() DriverViewModel에 완전 구현 |
| BUG-D02~D04 | PREPARING enum 추가, HOLD 미사용, runTransaction 사용 |

### 오탐 확정 (7건)

| 이슈 | 근거 |
|------|------|
| CUST-06 | CANCELED/CANCELLED 철자는 의도적 상태 구분 (관리자/기사/고객) |
| STL-04 | totalFare 전체 합산 후 계산, 부분합 불일치 없음 |
| STL-05 | Math.floor(CF) = .toInt()(Kotlin) — 양수 범위 동일 |
| CROSS-05 일부 | ExcludeNumber 미연동은 오탐 (이미 구현). wasRinging 부재만 유효 |
| STL-06 | settlementLastCleared는 1-Way (CF→Firestore), 2-Way 우려 오탐 |
| STL-10 | 구/신 정산 공존 아님. dailySettlements 컬렉션 CF에 없음, settlementSessions만 사용 |
| STL-11 | 자동/수동 finalize는 isFinalized 체크로 충돌 없는 정상 설계 |

### 유효 — 급하지 않음 (조치 권장, 파일럿 이후)

| 이슈 | 심각도 | 내용 | 비고 |
|------|--------|------|------|
| RTDB rules | Medium | `.read/.write: true` → `auth != null` 제한 필요 | 보안 |
| NEW-12 | Low | onCall 함수 17개 인증 없음 | 파일럿 전 앱 내부 호출 전용, 장기 |
| SEC-C01 | Low | 콜 생성 무인증 (Detector 구조적 한계) | CF callable 이전 시 해결 |
| SEC-C02~C04 | Low | 포인트/FCM 토큰 보안 | 익명인증 환경에서 실행 난이도 높음 |
| CROSS-05 | Medium | 내장 Detector wasRinging 부재 (발신 오감지 가능) | ExcludeNumber는 구현됨 |
| STL-01 | High | 관리자 직접 완료 시 creditAmount 미설정 경로 가능 | 현재 기사앱 100% 경유라 실질 영향 없음 |
| CROSS-02 | Low | enum 개수 차이 (Firestore 상태값 문자열은 호환) | 정보성 |
| STL-02 | Medium | pointsUsed Firestore 동적 읽기 확인, 실기기 검증 권장 | 하드코딩 아님 |
| NEW-13 | Low | CANCELED/CANCELLED 혼용. CF cleanup은 4가지 모두 처리 | 완전 통일은 중기 과제 |
| CUST-02 | Low | 포인트 차감 + 콜 생성 비원자적. 재시도 + CF 환불로 보완 | 실질 피해 가능성 매우 낮음 |
| CUST-04 | Low | 100% FCM 의존. restoreActiveCall()로 Firestore fallback 존재 | 앱 재시작으로 복구 |
| CUST-07 | Low | FCM 토큰 재시도 없음. 앱 시작마다 토큰 재저장으로 자연 복구 | |
| BUG-D08 | Low | 백그라운드 ghost state. refreshActiveCallStatus()로 보완 | |
| 백그라운드/LockScreen 취소 | Low | refreshActiveCallStatus + 알림바 취소 알림으로 보완 | |

### 집계

| 구분 | 건수 |
|------|------|
| 해소 확정 | 19건 |
| 오탐 확정 | 7건 |
| 유효 (급하지 않음) | 14건 (Critical 0, High 1, Medium 3, Low 10) |
| **합계** | **40건** |

## 상세 문서 목록
- `.agent-teams/CROSS_VERIFICATION_LOG.md` - 크로스 검증 기록 (세션 1~4)
- `.agent-teams/customer-app-analysis.md` - Customer App 분석 결과
- `.agent-teams/settlement-simulation.md` - 정산 로직 크로스 검증 + 시나리오 시뮬레이션
- `.agent-teams/REVIVAL_PROMPT.md` - 다음 세션 부활 프롬프트
- `.agent-teams/detector-analyst.md` 등 - 팀원별 분석 문서

# 최종 검증 마스터 플랜 (3단계)

## Context
실전 운영 전 전체 시스템 검증. 3단계로 점진적으로 범위를 넓혀가며
정산 로직 → 앱 UI → 실제 전화 감지까지 완전 검증.

---

## 1단계: TypeScript 시뮬레이션 ✅ 완료
- Firebase Emulator 위 정산 로직 정합성 검증
- 7일 350콜, 26개 체크포인트
- 매니저 ↔ 기사 외상 불일치 발견 → 수정 완료
- **결과: 정산 계산 로직 3자 일치 확인**

---

## 2단계: Compose UI Test (자동화, 팀원 4명 + 실기기 3대)

### 목적
실제 Kotlin 코드 + 실제 UI 렌더링 + Firestore Emulator 연동 검증

### 팀 구성
| 팀원 | 담당 | 기기 |
|------|------|------|
| firebase-tester | Firestore Emulator + CF 트리거 검증 | - |
| manager-tester | call_manager UI Test | SM-G996N (R3CR312MB1L) |
| detector-tester | call_detector + driver_app(기사1) | SM-S901N (R5CT41TJZFP) |
| driver-tester | driver_app(기사2) + customer_app | SM-F721N (R3CT80K78NP) |

### 검증 범위
- ViewModel 로직 (계산 결과 직접 assert)
- UI에 표시되는 실제 텍스트 (testTag로 검증)
- Firestore 데이터 일치
- Room DB 캐시 정합성
- 앱 킬 → 복구

### 검증 불가 (3단계에서 커버)
- 실제 전화 수신 감지 (BroadcastReceiver)
- 실제 FCM 푸시 수신
- GPS 위치 / 네트워크 불안정 / 도즈모드

### 시나리오 (7일 × 50콜 = 350콜, 47개 체크포인트)

#### Day 1 — 정상 플로우 (50콜)
| 시나리오 | 콜수 | 결제 | 검증 |
|----------|------|------|------|
| 전화수신→배차→수락→운행→완료 | 15 | 현금 | 콜/기사 상태 전이 |
| 앱콜→배차→완료 | 10 | 현금 | 앱콜 경로 |
| 이체 결제 | 8 | 이체 | totalCredit=fare |
| 현금+포인트 혼합 | 7 | 현금+포인트 | 포인트 외상 포함 |
| 전액 포인트 | 5 | 포인트 | cashReceived=0 |
| 외상 결제 | 5 | 외상 | creditAmount 정확 |
**CP**: 콜상태전이, 기사상태전이, 결제별정산3자일치, 수입내역정확

#### Day 2 — 취소 + 거절 + 타임아웃 (50콜)
| 시나리오 | 콜수 | 핵심 |
|----------|------|------|
| 정상 완료 | 20 | 기본 흐름 |
| 기사 거절→재배차→완료 | 5 | A거절→B완료, 정산B귀속 |
| 관리자 취소 CANCELED | 5 | 기사 WAITING 복구 |
| 기사 취소 CANCELLED_BY_DRIVER | 5 | 기사 WAITING 복구 |
| 고객 취소 CANCELLED_BY_CUSTOMER | 5 | 정산 미포함 |
| 배차 타임아웃 3분 | 5 | CF→WAITING→재배차 |
| HOLD→재배차 | 5 | 최종기사 귀속 |
**CP**: 거절후복구, 3종취소정산미포함, 타임아웃재배차귀속

#### Day 3 — 퇴근 + 재로그인 + 이체 타이밍 (50콜)
| 시나리오 | 콜수 | 핵심 |
|----------|------|------|
| A,B 1차운행+마감 | 15 | carryOver 발생 |
| A 퇴근→매니저이체→재출근→수령 | - | 오프라인 TRANSFERRED |
| A 2차운행 | 10 | filteredTrips=2차만 |
| B 운행중 매니저이체→수령→마감 | 5 | 마감전이체, originalCarryOver=0 |
| C,D,E 일반운행 | 20 | 정상 흐름 |
**CP**: carryOver발생, 퇴근후이체, 재출근UI반영, 2차filteredTrips, 마감전이체분리

#### Day 4 — 거절/재제출 + 통합정산 (50콜)
| 시나리오 | 콜수 | 핵심 |
|----------|------|------|
| 전원 정상운행 | 20 | 기본 흐름 |
| A 마감→거절→재제출→승인 | 10 | REJECTED→재제출 |
| C 2회거절→3회차승인 | - | 다중거절 데이터보존 |
| B 1차마감(미확인)→추가운행→통합제출 | 10 | isIntegration |
| D,E 일반 | 10 | 정상 흐름 |
**CP**: 거절→재제출정확, 다중거절carryOver, 통합정산mergedTrips, 원본값보존

#### Day 5 — 동시성 + 앱 킬/복구 (50콜)
| 시나리오 | 콜수 | 핵심 |
|----------|------|------|
| 5건 동시배차×5라운드 | 25 | 세션 중복 방지 |
| 기사앱 강제종료→재시작 | - | 진행중 콜 복구 |
| 매니저앱 강제종료→재시작 | - | refreshData 복구 |
| 배차중 앱종료→타임아웃 | 5 | 3분후 WAITING |
| 연속 빠른 콜 (30초 간격) | 10 | 세션 누락 없음 |
| 정상운행 | 10 | 마무리 |
**CP**: 동시완료무결성, 기사앱복구, 매니저앱복구, 연속콜누락없음

#### Day 6 — 공유콜 + 특수상황 (50콜)
| 시나리오 | 콜수 | 핵심 |
|----------|------|------|
| 정상운행 | 20 | 기본 흐름 |
| 업무마감→공유콜 생성 | 5 | shared_calls OPEN |
| 0원 콜 | 5 | fare=0 정산 |
| 소액 5,000원 | 5 | 소액 정산 |
| 고액 200,000원 | 5 | 고액 정산 |
| 동일기사 연속10건 | 10 | 대량 정확성 |
**CP**: 공유콜생성, 0원/소액/고액정산, 대량연속누락없음

#### Day 7 — 최종 정리 + 전원 마감 (50콜)
| 시나리오 | 콜수 | 핵심 |
|----------|------|------|
| 이월금 있는 상태로 운행 | 15 | Day6 이월 공제 |
| 다양한 결제 혼합 | 15 | 현금+이체+포인트+혼합+외상 |
| 전원 마감→확인→이체→수령 | 20 | 전원 SETTLED |
| 업무마감 finalizeSession | - | isFinalized=true |
**CP**: 이월공제, 혼합결제최종, 전원balance=0, 데이터무결성, 업무마감

---

## 3단계: 실제 전화 수동 테스트 (사람이 직접)

### 목적
2단계에서 검증 불가능한 실제 물리적 환경 테스트

### 테스트 항목

#### A. 전화 수신 감지 (call_detector)
| # | 시나리오 | 방법 | 검증 |
|---|---------|------|------|
| A1 | 일반 전화 수신 | 다른 폰으로 S22에 전화 | BroadcastReceiver 감지 → 콜 생성 |
| A2 | 부재중 전화 | 울린 후 끊기 | 감지 여부 (짧은 벨) |
| A3 | 연속 2통 (다른 번호) | 5초 간격 2통 | 중복 방지 (5초+10초 체크) |
| A4 | 동일 번호 연속 | 같은 번호로 2통 | 중복 감지 작동 |
| A5 | 3대 동시 감지 | S22+S21+가용기기 동시 | Firestore 쿼리 중복 방지 |

#### B. FCM 실제 푸시 (프로덕션 Firebase)
| # | 시나리오 | 검증 |
|---|---------|------|
| B1 | 배차 FCM → 기사 포그라운드 | 콜 수신 팝업 즉시 표시 |
| B2 | 배차 FCM → 기사 백그라운드 | FullScreenIntent 알림 |
| B3 | 배차 FCM → 기사 앱 킬 상태 | data-only FCM 수신 |
| B4 | 정산확인 FCM → 기사 | CONFIRMED 알림 |
| B5 | 매니저 취소 FCM → 기사 | 취소 알림 + 콜 정보 제거 |

#### C. 네트워크/배터리
| # | 시나리오 | 검증 |
|---|---------|------|
| C1 | 운행 중 Wi-Fi 끊김 → LTE | 상태 전이 유지 |
| C2 | 비행기모드 5초 → 복구 | Firestore 오프라인 큐 처리 |
| C3 | 배터리 절약 모드 | 포그라운드 서비스 유지 |
| C4 | 도즈모드 진입 | FCM 고우선순위 수신 |

#### D. End-to-End 풀 사이클 (5콜)
| # | 시나리오 |
|---|---------|
| D1 | 실제 전화→감지→배차→수락→운행→완료→정산→마감→이체→수령 |
| D2 | 실제 전화→감지→배차→거절→재배차→완료 |
| D3 | 실제 전화→감지→배차→관리자취소 |
| D4 | 앱콜(customer_app)→배차→완료→정산 |
| D5 | 전화→감지→배차→기사앱킬→재시작→수락→완료 |

### 실행 방법
- **사람이 직접 전화를 걸고**, 각 기기에서 결과 확인
- 팀원(Claude)이 Firestore 데이터 검증 보조
- 예상 소요: 2~3시간

---

## 전체 일정

| 단계 | 내용 | 상태 | 예상 소요 |
|------|------|------|----------|
| 1단계 | TypeScript 정산 시뮬레이션 | ✅ 완료 | - |
| 2단계-기반 | testTag 추가 + androidTest 설정 | 미시작 | 2~3시간 |
| 2단계-코드 | Compose UI Test 코드 작성 | 미시작 | 4~5시간 |
| 2단계-실행 | 팀원 4명 실기기 테스트 | 미시작 | 2~3시간 |
| 3단계 | 실제 전화 수동 테스트 | 미시작 | 2~3시간 |
| (배포전) | Firebase Test Lab 호환성 | 미시작 | 30분 |

---

## 수정 대상 파일 요약

### 2단계 기반 구축
| 파일 | 변경 |
|------|------|
| `call_manager/.../DriverSummaryScreen.kt` | testTag 추가 |
| `call_manager/.../AllTripsScreen.kt` | testTag 추가 |
| `driver_app/.../HistorySettlementScreen.kt` | testTag 추가 |
| `call_manager/app/build.gradle` | coroutines-test 의존성 |
| `driver_app/app/build.gradle` | coroutines-test 의존성 |
| `call_manager/app/src/androidTest/...` | 신규 - SettlementConsistencyTest.kt |
| `driver_app/app/src/androidTest/...` | 신규 - DriverSettlementConsistencyTest.kt |
| `call_detector/app/src/androidTest/...` | 신규 - DetectorConsistencyTest.kt |

### 이미 완료된 수정
| 파일 | 변경 |
|------|------|
| `driver_app/.../DriverViewModel.kt` | totalCredit 포인트 외상 처리 ✅ |
| `call_manager/.../SettlementViewModel.kt` | driverLastClearedMap 실시간 갱신 ✅ |
| `call_manager/.../DriverSummaryScreen.kt` | filteredTrips 도입 ✅ |

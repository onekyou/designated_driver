# 2단계: 순차적 흐름 테스트 플랜

## 현재 상태 (2026-03-16)

### 완료된 것
- ✅ 1단계 TypeScript 시뮬레이션 (7일 350콜, 26 CP 통과)
- ✅ 계산 로직 추출 (SettlementCalculator.kt, SettlementCalc.kt) — 커밋 `5f87dd8c`
- ✅ testTag 16개 추가 (3개 화면) — 커밋 `5f87dd8c`
- ✅ JVM Unit Test (call_manager 27개 + driver_app 전체 통과) — 커밋 `5f87dd8c`
- ✅ androidTest (Firestore 연동 + 자동 로그인) — 커밋 `124df476`
- ✅ 팀원 개별 테스트 실행 (24/28 통과, 4건 권한 문제)
- ✅ customer_app 임시 수정 원복 완료
- ✅ Firestore 테스트 데이터 345건 정리 완료

### 미완료 — 여기서 이어서 진행
- ❌ **순차적 흐름 시뮬레이션** (핵심 — 앱 간 연쇄 동작 검증)
- ❌ 앱 킬 → 복구 검증

### 팀원이 생성한 파일 (유지, 미커밋)
| 파일 | 결과 |
|------|------|
| `call_manager/app/src/androidTest/.../SettlementUITest.kt` | 5/5 통과 |
| `call_detector/app/src/androidTest/.../DetectorConsistencyTest.kt` | 7/7 통과 |
| `driver_app/app/src/androidTest/.../Driver1CallDispatchTest.kt` | 6/7 (1건 권한) |
| `driver_app/app/src/androidTest/.../HistorySettlementTagVerificationTest.kt` | 3/3 통과 |
| `customer_app/app/src/androidTest/.../AppCallFlowTest.kt` | 3/6 (3건 권한) |
| `functions/scripts/test-day1-7.js` | 76/76 CP + cleanup 기능 |

---

## 할 일: 순차적 흐름 시뮬레이션

### 목적
각 앱을 따로 테스트하는 게 아니라, **손님앱→콜매니저→기사앱→콜매니저** 순서대로 Firestore 데이터를 조작하면서 전체 흐름이 정상인지 검증.

### 방식
- 하나의 Node.js 스크립트 (`functions/scripts/sequential-flow-test.js`)
- 기존 `firestore-util.js`의 REST API 패턴 재사용
- Day별로 나누어 실행: `node sequential-flow-test.js --day 1`
- 팀원 불필요 — 혼자 실행
- 각 Day 종료 시 cleanup 옵션 제공

### 1콜 순차 흐름
```
1. [손님앱] calls/{id} 생성 (WAITING, createdFrom=customer_app)
   → assert: status=WAITING

2. [CF] 콜매니저 FCM 대상 확인
   → assert: managerTokens 존재

3. [콜매니저] 기사 배차 (ASSIGNED, assignedDriverId 설정)
   → assert: status=ASSIGNED, 기사 status 변경

4. [CF] oncallassigned → 기사 FCM
   → assert: 트리거 발동

5. [기사앱] 수락→운행→완료 (ACCEPTED→IN_PROGRESS→AWAITING_SETTLEMENT→COMPLETED)
   → assert: 각 단계 status, completedAt 설정

6. [CF] onCallCompletedUpdateSettlement → 정산 세션
   → assert: settlementSessions/{date} 업데이트

7. [콜매니저] 정산 확인
   → assert: 계산 일치

8. [기사앱] 마감 제출
   → assert: PENDING_CONFIRM

9. [콜매니저] 마감 확인 → 이체
   → assert: CONFIRMED → TRANSFERRED

10. [기사앱] 수령 확인
    → assert: SETTLED
```

### Day 1 — 정상 플로우 (50콜)
- 현금 15건, 앱콜 현금 10건, 이체 8건, 현금+포인트 7건, 포인트 5건, 외상 5건
- CP: 콜상태전이, 기사상태전이, 결제별정산3자일치, 수입내역정확

### Day 2 — 취소 + 거절 + 타임아웃 (50콜)
- 정상 20건, 거절→재배차 5건, 관리자취소 5건, 기사취소 5건, 고객취소 5건, 타임아웃 5건, HOLD→재배차 5건
- CP: 거절후복구, 3종취소정산미포함, 타임아웃재배차귀속

### Day 3 — 퇴근 + 재출근 + 이체 타이밍 (50콜)
- 1차운행+마감 15건, 퇴근→이체→재출근→수령, 2차운행 10건, 마감전이체 5건, 일반 20건
- CP: carryOver발생, 퇴근후이체, 재출근UI반영, 2차filteredTrips, 마감전이체분리

### Day 4 — 거절/재제출 + 통합정산 (50콜)
- 정상 20건, 마감→거절→재제출 10건, 2회거절→3회차승인, 통합정산 10건, 일반 10건
- CP: 거절→재제출정확, 다중거절carryOver, 통합정산mergedTrips, 원본값보존

### Day 5 — 동시성 + 앱 킬/복구 (50콜)
- 동시배차 25건, 앱 킬→복구 시뮬레이션, 연속 빠른 콜 10건, 정상 10건
- CP: 동시완료무결성, 연속콜누락없음

### Day 6 — 공유콜 + 특수상황 (50콜)
- 정상 20건, 공유콜 5건, 0원 5건, 소액 5건, 고액 5건, 동일기사 연속 10건
- CP: 공유콜생성, 0원/소액/고액정산, 대량연속누락없음

### Day 7 — 최종 정리 + 전원 마감 (50콜)
- 이월금 운행 15건, 혼합결제 15건, 전원 마감→확인→이체→수령 20건
- CP: 이월공제, 혼합결제최종, 전원balance=0, 데이터무결성, 업무마감

---

## 참조 파일
| 파일 | 용도 |
|------|------|
| `functions/scripts/firestore-util.js` | REST API 유틸 (재사용) |
| `functions/scripts/test-day1-7.js` | 기존 350콜 스크립트 (참고용) |
| `memory/plan_final_test_master.md` | 전체 마스터 플랜 |
| `docs/TEST_CLEANUP_GUIDE.md` | 테스트 코드 정리/원복 가이드 |
| `CLAUDE.md` | 상태 전이 맵, 정산 공식 |

## Firestore 경로
- 사무실: `provinces/gyeonggi/cities/yangpyeong/offices/nEkf0X9g3LZtRX94Mrzu`
- 기사: `designated_drivers/{uid}`
- 콜: `calls/{callId}`
- 정산: `settlementSessions/{date}`
- 공유콜: `shared_calls/{callId}`

## 기기 현황 (설계 기준)
| 기기 | 앱 |
|------|-----|
| SM-G996N (S21+, R3CR312MB1L) | call_manager, driver_app |
| SM-S901N (S22, R5CT41TJZFP) | call_detector, driver_app |
| SM-F721N (Z Flip4, R3CT80K78NP) | driver_app, customer_app |

## 새 챗에서 시작하려면
```
memory/plan_sequential_flow_test.md 읽고 Day 1부터 진행해줘
```

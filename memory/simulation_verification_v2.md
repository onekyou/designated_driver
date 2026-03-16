# 2차 블라인드 시뮬레이션 검증 결과 (2026-03-07)

## 개요
- 시뮬레이션: 하루 50콜 x 7일 = 350콜, 다양한 정산 시나리오
- 분석팀: detector-analyst, manager-analyst, driver-analyst, firebase-analyst
- 총 보고: 54건 (P0:12, P1:19, P2:23)
- 크로스 검증 결과: 실제 이슈 3+1건, 오탐 39건, 설계 의도 5건, 경미/수용 6건

## 검증된 오탐 (향후 동일 지적 시 참조)

### Firestore 트랜잭션 직렬화 관련 (가장 빈번한 오탐 원인)
| 지적 내용 | 오탐 사유 |
|-----------|----------|
| 동시 배차 레이스 컨디션 (Manager P0-2) | `transaction.get(driverRef)` 읽기 → Firestore가 트랜잭션 직렬화. 두 번째 트랜잭션은 재시도 시 ASSIGNED 상태를 보고 DRIVER_NOT_AVAILABLE |
| 공유콜 포인트 이중 처리 (Firebase P0-1) | 멱등성 키 문서에 대한 `tx.get()` → 트랜잭션 직렬화가 중복 방지 |
| 정산 세션 콜 중복 추가 (Firebase P0-2) | `addCallToSettlementSession`이 `db.runTransaction` 내에서 중복 체크+추가 = 원자적 |
| processCarryOverOnFinalize 중복 계산 (Manager P1-5) | CONFIRMED 스킵 + 트랜잭션 직렬화로 이중 계산 방지 |
| 기사당 콜 수 제한 없음 (Manager P1-6) | 배차 트랜잭션에서 기사 상태를 읽으므로, 이미 ASSIGNED면 실패 |
| 고객 포인트 적립 동시 요청 (Firebase P1-4) | 멱등성 키(earn_{phone}_{callId}) + 트랜잭션 = 안전 |

**핵심 원리**: Firestore runTransaction은 읽은 문서가 커밋 전에 변경되면 자동 재시도한다. 따라서 같은 문서를 읽고 쓰는 두 트랜잭션은 직렬화된다.

### Detector 3대 = 3대 별도 폰 관련
| 지적 내용 | 오탐 사유 |
|-----------|----------|
| CallReceiver/Service 동기화 문제 (Detector P0-2) | 3대 Detector는 3대 별도 폰. 프로세스 간 static 변수 공유 없음 |
| sharedCallCreatedFromRinging 원자성 (Detector P1-4) | 인스턴스 변수는 폰당 1개. cross-device 경합 없음 |
| CallScreeningService 정적 변수 경합 (Detector P2-1) | 같은 이유. 각 폰의 독립 프로세스 |

### 이미 수정된 건 (1차 시뮬레이션에서 수정 완료)
| 지적 내용 | 수정 이력 |
|-----------|----------|
| 3대 Detector 중복 콜 (Detector P1-1) | D-1(클라이언트) + A-3(서버) 이중 중복 체크 |
| RINGING 2초 대기 취약성 (Detector P1-3) | sharedCallCreatedFromRinging 플래그 + Firestore 중복 쿼리 |
| FCM 동시 콜 처리 (Driver P1-1) | B-4: 팝업 큐잉 + pendingCallCount + dismissNewCallPopup시 다음 콜 표시 |
| RINGING→OFFHOOK 스킵 (Detector P0-1) | D-3: lastRingingTime 기반 판단 |

### 정산 로직 정확성 확인
| 지적 내용 | 오탐 사유 |
|-----------|----------|
| 미제출 기사 정산 누적 오류 (Manager P0-3) | loadTodaySettlement가 settlementLastCleared 이후 모든 COMPLETED 콜 쿼리 → Day4+Day5 합산 정확 |
| realDeposit 계산 불일치 (Driver P0-2) | 로컬 계산과 Firestore 저장 값 추적 결과 일관됨 |
| 앱 재시작 시 정산 손실 (Driver P0-3) | loadTodaySettlement가 Firestore에서 재쿼리하므로 정확히 복구 |
| carryOver 스냅샷 불일치 (Driver P1-2) | Firestore 스냅샷은 point-in-time 일관성 보장 |
| submitDailySettlement 통합 로직 (Driver P1-3) | isIntegration은 PENDING_CONFIRM일 때만 true. 미제출 후 제출은 정상 경로 |
| 미제출 기사 carryOver 미갱신 (Manager P1-4) | confirmDailySettlement에서 미제출 시 기존 balance 유지 = 정확한 동작 |
| processCarryOverOnFinalize 미구현 (Firebase P0-3) | Manager 앱 SettlementViewModel.kt:1440에 구현됨. CF가 아닌 클라이언트 측 |

### 기타 오탐
| 지적 내용 | 오탐 사유 |
|-----------|----------|
| CallRepository fire & forget (Manager P1-1) | DashboardViewModel에서 runTransaction 먼저 실행, Room DB는 그 후 갱신 |
| sourceSharedCallId 검증 부재 (Manager P1-2) | `.takeIf { it.isNotBlank() }`(line 890)로 빈 문자열→null 변환 처리됨 |
| 비동기 작업 타이밍 (Detector P0-3) | serviceScope.launch의 클로저가 phoneNumber를 캡처. 리셋과 무관하게 안전 |

## 설계 의도 (의도적 구현)
| 항목 | 설계 근거 |
|------|----------|
| FCM 실패 시 배차 유지 (Manager P0-1) | 롤백보다 현실적. 재시도 3회 + "직접 연락해주세요" 스낵바로 관리자 인지 |
| FCM 전송 실패 에러 미전파 (Firebase P1-2) | 재시도+로깅으로 충분. FCM 실패가 콜 흐름을 차단하면 안 됨 |
| 발신번호 비공개 미처리 (Detector P2-2) | 번호 없이는 배차 불가. 폰에서 직접 대응 |
| 부재중 전화 미기록 (Detector P2-7) | 받지 않은 전화는 시스템 등록 불필요. RINGING→IDLE 시 OFFHOOK 미설정이므로 처리 안 됨 |
| Settlement 불일치 자동 복구 없음 (Firebase P2-1) | 감지+알림만 제공, 자동 복구는 위험 (잘못된 데이터로 자동 수정 가능) |

## 경미/수용 (파일럿에서 무시 가능)
| 항목 | 사유 |
|------|------|
| timestampClient 시간 동기화 (Detector P1-2) | 10초 윈도우가 5초 클럭 드리프트 커버 |
| ASSIGNED 타임아웃 비트랜잭션 (Firebase P1-3) | 1분 주기 재실행으로 자동 복구 |
| 공유콜 상태 동기화 (Firebase P1-1) | 실패 시 로그 기록, 정산 세션은 별도 트리거로 처리 |
| carryOverList 리스너 타이밍 (Manager P1-3) | 일시적 UI 깜박임 수준 |
| callsCache 메모리 누적 (Manager P2-4) | 350건 수준은 문제 없음 |
| 타임존 6시 기준 (Driver P2-4) | Calendar.getInstance()는 로컬 타임존, 한국 단일 시간대 |

## 실제 수정 필요 이슈 (수정 계획에 반영)
| # | 이슈 | 심각도 | 내용 |
|---|------|--------|------|
| 1 | rejectCall 비트랜잭션 | P1 | 콜+기사 업데이트를 단일 트랜잭션으로 |
| 2 | cancelTrip 비트랜잭션 | P1 | 동일 패턴 |
| 3 | confirmAndFinalizeTrip 비트랜잭션 | P1 | 콜 COMPLETED + 기사 WAITING 원자적 |
| 4 | acceptCall UI 롤백 없음 | P1 | catch에서 UI 상태 복원 |
| 5 | carryOver TRANSFERRED 표시 | P2 | balance=0이어도 TRANSFERRED면 표시 |
| 6 | reopenSharedCall 문서 확인 | P2 | 존재 여부 확인 + 에러 로깅 |

# 6/16 검증 트랙 시작 + 콜드스타트/앱진입 배차 수락 버그 수정 (커밋 503cf4b1 push됨)

## 검증 트랙 (6월 신규기능) — 원규씨 발의
- 원규씨: "기본 작업 끝났고 실 동작 검증 필요. 원칙 = 최소개입으로 그들이 자유롭게 앱 사용." 6월 신규(PTT·9-B·알림·가제티어·정산단순화)를 검증 1순위로 선택.
- 검증 3겹: ① 코드 정합 감사(팀원) ② 런타임 시뮬(테스트사무실 1004) ③ 기기 핸즈온. PTT는 실폰 검증 충분 → 생략 가능(원규씨).
- 검증 시나리오 **v2**(픽업 포함, 콜 생애 8단계: 출근→전화콜→배차+PTT→픽업이송→운행시작(PTT차단)→운행완료→마감→정산확인) 정리됨 = 다음 검증 기준선.
- 픽업앱 = 읽기전용 모니터(액션버튼 없음, CallCard 경로·요금만). 조율은 전부 PTT. 픽업-콜 매칭 시스템에 없음(매니저 PTT 지정).

## 배차 수락 버그 수정 (커밋 `503cf4b1`, driver_app 4파일, push됨)
- **현상(오래된 묵은 버그)**: 화면오프 배차 → 알림클릭/앱진입 → 수락해도 운행준비화면 안 가고 메인(WaitingScreen), "배차확인→수락" 여러 번 반복해야 함. 포그라운드에선 정상이라 평소 안 보임.
- **뒷단**: `acceptCall`이 인메모리 `assignedCalls`에만 의존 → 콜드스타트 직후 콜 못 찾아(로그 "찾은 콜: null") activeCall 미세팅 → 메인 머묾. 수정 = 2단계 트랜잭션이 읽는 callSnapshot을 CallInfo로 파싱·반환해 activeCall 보장 세팅(`runTransaction<CallInfo?>`). 정상경로는 `activeCall?.id==callId` skip.
- **앞단**: 앱 열기만 하면(알림 탭 없이) 미수락 배차 수락팝업 자동. `call_assigned` FCM 수신 시 callId를 로컬 prefs(`PREF_KEY_PENDING_DISPATCH`) 기록 → `MainActivity.onResume`에서 pending 있으면 기존 `handleNotificationCallId` 경로 호출 → 수락/거절/무효 콜 시 `clearPendingDispatch()`. **Firestore 읽기 추가 0**(로컬 플래그, driver_app 비용최적화 정합).
- 플립폰(ZFlip4 R3CT80K78NP) 실폰 검증 통과. **다른 단말(S21+·S22)은 미설치 — 플립폰만 최신.**

## 검증 중 발견한 미결 항목 (다음 세션)
1. ⚠️ **정산 `onDriverSettlementSubmitted` 트리거**: functions가 `dailySettlement.status`(서브객체) 필드를 검사하는데 그 필드가 없음(실제 변경은 `drivers/{id}.status` 최상위) → 조건 항상 false → **트리거 불발 의심**(매니저가 정산제출 FCM 못 받을 수 있음). 정적 분석 단계 — **런타임 확인 필요**.
2. **운행중(IN_PROGRESS) 취소 시 요금 0**: cancelTrip(CANCELLED_BY_DRIVER)·cancelCall(CANCELED) 둘 다 정산 세션 미반영(onCallCompletedUpdateSettlement는 COMPLETED만). 데이터는 안 깨짐(기사 WAITING 복구 정상). **부분요금 받을지 = 정책 판단 대기**(원규씨).
3. **기사 상태 enum 과다**: ONLINE≈WAITING(둘 다 가용, functions 라벨 동일·9-B 한묶음) 통합 후보 / ACCEPTED 불일치(driver_app엔 있고 call_manager엔 없어 UNKNOWN으로 떨어짐, 잠재버그) / PENDING_CONFIRM = 정산 정보가 dailySettlement에 이미 있는데 기사 status에 중복(배차차단+enforcement 게이트 4곳 의존이라 정산중심부 동시수술 = 위험, 보류). 매니저 정산미확인 식별은 status 아닌 `confirmedAt==null` 사용.
4. **알림음 "정산대기"(SETTLEMENT_SUBMITTED) 정리** 보류. (단 #1 불발이면 이미 안 울릴 수도.)

## 검증 방법론 교훈 (정적분석↔실동작 갭)
- **정적 코드분석이 실동작과 크게 어긋남**: `STATUS_CHANGE` FCM이 발신처 없어 "죽은 코드"로 보였으나, 운행시작/완료 소리+팝업은 **in-app 경로(DashboardViewModel Firestore 리스너)**로 실제 작동(원규씨 "잘 된다" 확인). "기사 운행 취소" 9-B도 자동 아니라 원규씨가 수락로직 확인하느라 **수동 취소**한 것. → **logcat 실측 + 실사용자 확인으로만 확정**. 원규씨 최초 "실 동작 검증 필요" 직관이 정확.
- driver_app `assignedCallsListener`/`driverStatusListener`/`completedCallsListener`는 **변수 선언·remove만 있고 addSnapshotListener 미등록(죽은 변수)** → CLAUDE.md "활성콜 실시간 리스너 안 씀"이 맞음. (단 알림 경로 of `call_assigned` FCM + onResume + checkForPendingDispatch 수동조회로 콜 처리)
- 클코 학습: 코드에서 못 찾았다고 "안 된다" 단정 금지(STATUS_CHANGE 두 번 오판). 발신↔수신 갭은 런타임으로.

## 클코 실수 (이번 세션, 원규씨 "누락 오염 검토" 반복으로 교정됨)
- "콜드스타트(프로세스 죽음)"로 단정 → 실제는 앱 백그라운드 생존(PID 유지). 화면오프 ≠ 앱 죽음.
- "리스너가 activeCall 덮음" 추정 → 원규씨 "리스너 아니지 않냐" 지적, 실제는 죽은 변수 + 수동 취소.
- 수정 범위를 뒷단만 보고 앞단(자동 수락팝업) 누락 → 검토로 발견.
- 교훈: 매 수정 전 원규씨식 "누락·오염 검토"가 실제로 오판을 잡았다.

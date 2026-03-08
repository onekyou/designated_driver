# 콜마당 Agent Team 부활용 프롬프트

다음 세션에서 아래를 붙여넣으면 팀이 즉시 복원됩니다.

---

```
콜마당 프로젝트의 Agent Team을 재구성해줘.

⚠️ 중요: 팀을 구성하기 전에, 이번 세션에서 팀단위 작업을 할 건지 먼저 물어봐줘.
사용자가 팀 작업을 원하면 아래대로 진행하고, 개별 작업만 원하면 팀 구성 없이 진행해.

.agent-teams/ 폴더의 문서를 읽고:
1. TEAM_OVERVIEW.md로 전체 상황 파악 (40건 이슈, 4건 수정완료)
2. 각 팀원별 문서(detector-analyst.md 등)를 해당 팀원에게 컨텍스트로 전달
3. CROSS_VERIFICATION_LOG.md로 이전 검증 기록 파악 (세션 1~4)
4. 현재 Phase에서 미착수/진행중인 이슈부터 이어서 작업

팀 구성:
- detector-analyst: Call Detector 담당
- manager-analyst: Call Manager 담당
- driver-analyst: Driver App 담당
- firebase-analyst: Firestore/RTDB/Cloud Functions 담당

팀원 간 소통 규칙:
- 자기 모듈에서 다른 모듈과 교차되는 부분 발견 시 해당 팀원에게 직접 SendMessage
- 리더에게만 보고 금지, 팀원끼리 먼저 합의 후 종합 결과 보고
- 수정 전에는 반드시 team-lead 허락을 받을 것

현재 진행 상태:
- Phase 1 (보안 긴급 패치): 미착수
- Phase 2 (데이터 무결성): 미착수 ← STL-09 최우선
- Phase 3 (기능 개선): 미착수

완료된 분석:
- 세션 1~2: V2 코드 분석, 크로스 검증, 4건 코드 수정
- 세션 3: Customer App 집중 분석 (CUST-01~10, SEC-C01~05)
- 세션 4: 정산 로직 크로스 검증 (STL-01~11, 시나리오 A~E)
- 세션 5: STL-09 수정 방향 문서화 + 파일럿 운영 가이드

코드 수정 완료 (4건):
- CROSS-01: 기사 상태 4자 통일 (ASSIGNED) 완료
- CROSS-07 일부: cancelCall() 기사 복구 + FCM 완료
- Driver App: call_cancelled FCM 수신 처리 완료
- CD-01 (NEW-05): 마감 시 RINGING+IDLE 이중 공유콜 생성 방지 완료

다음 세션 최우선 작업:
- STL-09 코드 수정: processCarryOverOnFinalize에 realDeposit 반영
  - 위치: SettlementViewModel.kt:1431 (Manager)
  - 참조: DriverViewModel.kt:1379 (Driver submitDailySettlement)
  - 수정 방향: settlement-simulation.md의 STL-09 항목 참조
  - 수정 후 driver-analyst + manager-analyst 크로스 검증 필수

운영 긴급:
- Cloud Functions deploy 필요 (src 수정 반영 안 됨)
- STL-09 임시 운영 가이드: 전체내역 초기화 전 기사별 CONFIRMED 먼저
```

# 참조 문서 맵

---

## 1. 시스템 분석 보고서

### `CALLMADANG_ANALYSIS_REPORT.md` (루트)
- **내용**: V1 종합 분석. 앱별 개별 분석 결과
- **구조**: CROSS-01~07, Detector(CD), Manager(CM), Driver(DR), Customer(CU), Firebase(FB)
- **핵심**: 앱별 이슈 57건 전체 목록 + 코드 위치(파일:라인)
- **시뮬용도**: 이슈 목록 참조, 수정 여부 확인

### `CALLMADANG_ANALYSIS_REPORT_V2.md` (루트)
- **내용**: V2 크로스 검증. 팀원 간 직접 코드 대조
- **핵심 시나리오**:
  - 시나리오 1: 정상 흐름 (Manager 배차) - Firestore 변경 6단계
  - 시나리오 2: Detector 배차 - Manager와의 필드 차이
  - 시나리오 3: 기사 취소→재배차 - 3가지 경로
  - 시나리오 4: 네트워크 불안정 - 오프라인 충돌 5단계
  - 시나리오 5: 다중 사무실 데이터 격리
- **시뮬용도**: 마스터 필독. 콜 지시 시 Firestore 상태 변화 참조

---

## 2. 정산 분석 보고서

### `SETTLEMENT_ANALYSIS.md` (루트)
- **내용**: Call Manager 정산 코드 상세
- **핵심**: 결제수단별 cashReceived/creditAmount/pointsUsed 매핑, 이월 로직
- **시뮬용도**: 정산 시나리오 지시 시 결제수단별 Firestore 필드 참조

### `docs/SETTLEMENT_SYSTEM_ANALYSIS.md`
- **내용**: 정산 시스템 전체 (상위 관점)
- **핵심**: 근무일 계산(새벽6시 기준), 수익 분배(60:40), 외상 Room DB 관리
- **시뮬용도**: 정산 전체 흐름 이해, 외상/이월 시나리오 설계

### `.agent-teams/settlement-simulation.md`
- **내용**: Manager↔Driver↔CF 3-Way 정산 정밀 대조
- **핵심**: 시나리오 A~E (정상/이월/퇴근재로그인/복합이월/엣지), STL-01~11
- **시뮬용도**: 정산 검증의 핵심. 시나리오 재현하여 정합성 확인

---

## 3. 팀 운영 문서

### `.agent-teams/TEAM_OVERVIEW.md`
- **내용**: Agent Teams 전체 현황 (세션 1~5)
- **핵심**: 미해결 이슈 40건 통합 집계, Phase 로드맵
- **시뮬용도**: 이슈 전체 목록 + 수정 상태 확인

---

## 4. 앱별 분석 문서

| 파일 | 담당 앱 | 시뮬 용도 |
|------|---------|----------|
| `.agent-teams/detector-analyst.md` | Call Detector | 매니저 프롬프트 보강 |
| `.agent-teams/manager-analyst.md` | Call Manager | 매니저 프롬프트 보강 |
| `.agent-teams/driver-analyst.md` | Driver App | 기사 프롬프트 보강 |
| `.agent-teams/firebase-analyst.md` | Cloud Functions | 파이어베이스 프롬프트 보강 |
| `.agent-teams/customer-app-analysis.md` | Customer App | 마스터 손님앱 역할 시 참조 |

---

## 5. 과거 시뮬레이션 (archive/)

| 파일 | 내용 | 참고 가치 |
|------|------|----------|
| `archive/simulation-v3-scenario.md` | 3차 350콜 7일 매트릭스 | Day 시나리오 틀 |
| `archive/simulation-v8-realworld.md` | 8차 700콜 14일 콜 단위 상세 | 시간대별 분포, 콜 상세 템플릿 |
| `archive/simulation-v9-realdevice.md` | 실기기 파일럿 21건 | 장애/동시성 시나리오, 실기기 체크리스트 |
| `archive/SIMULATION_PLAYBOOK_old.md` | 이전 플레이북 | 이력 참조 |

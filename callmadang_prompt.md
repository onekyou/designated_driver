# 콜마당 Agent Teams 통합 분석 프롬프트

아래를 Claude Code에 붙여넣으세요.

---

콜마당(CallMadang) 프로젝트의 잠재적 문제를 분석해줘. Agent Team을 만들어서 병렬로 진행해.

## 프로젝트 개요
대리운전 사무실용 B2B SaaS. 4개 안드로이드 앱 + Firebase 기반.
- Call Detector: 전화 감지 + 배차 가능 (관리자 멀티폰 용도)
- Call Manager: 전체 상황 통제, 콜 접수/배정, Call Detector 기능 내장
- Driver App: 기사용, 콜 수락/거절/완료
- Customer App: 고객용, 상태 확인
- Firestore "attributed node" 구조: 각 사무실이 자체 데이터 소유
- 상태 흐름: 접수 → 배차중 → 운행중 → 완료
- Call Detector에서 배차하면 즉시 Manager에 "배차중"으로 반영됨

## Agent Team 구성 (5명)

### Agent 1: Call Detector 분석
- Detector의 전화 감지 → 배차까지 전체 로직
- Detector에서 배차한 데이터와 Manager에서 배차한 데이터의 Firestore 구조 일치 여부
- 백그라운드 앱 종료 시 전화 감지 누락 가능성

### Agent 2: Call Manager 분석
- 내장된 Detector 기능과 독립 Detector 앱의 코드 일관성
- 10개 사무실 동시 사용 시 콜 목록 렌더링 성능
- 기사 취소 → 재배차 상태 전이의 완전성

### Agent 3: Driver App 분석
- Detector 배차와 Manager 배차를 동일하게 수신하는지
- 콜 수락 시 race condition (여러 기사에게 동시 알림 갔을 때)
- 운행 중 네트워크 끊김 → 재연결 시 상태 동기화

### Agent 4: Customer App 분석
- 전체 상태 흐름이 실시간 반영되는지
- 기사 취소/재배차 시 고객 화면 갱신 정확성
- 에러 시 무한 로딩이나 빈 화면 없이 안내하는지

### Agent 5: Firestore + Cloud Functions 분석
- 보안 규칙: 10개 사무실 데이터 완전 격리 검증
- 동시 쓰기 시 트랜잭션 충돌 가능성
- Firestore 리스너가 다수 사무실 동시 사용 시 누락 없는지
- Cloud Functions의 에러 핸들링과 재시도 로직

## 모든 에이전트 공통 관점
분석 시 반드시 아래 시나리오를 기준으로 점검할 것:
1. **동시성**: 10개 사무실이 동시에 콜을 처리하는 상황
2. **네트워크 불안정**: 양평 등 농촌 지역, 끊김/지연 빈번
3. **에러 UX**: 문제 발생 시 사용자에게 명확한 안내가 되는지
4. **데이터 격리**: 다른 사무실 데이터에 절대 접근 불가한지
5. **상태 일관성**: 같은 콜에 대해 4개 앱이 항상 동일한 상태를 보는지

## 결과물
각 에이전트는 발견한 잠재 문제를 심각도(Critical/High/Medium/Low)로 분류해서 보고.
최종적으로 크로스 모듈 연동 문제를 포함한 종합 보고서를 작성해줘.

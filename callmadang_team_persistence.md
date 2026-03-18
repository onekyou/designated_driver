# 콜마당 Agent Teams 지속성 문서 생성 프롬프트

아래를 Claude Code에 붙여넣으세요.

---

프로젝트 루트에 `.agent-teams/` 폴더를 만들고, 다음 문서들을 생성해줘. 이 문서들은 컴퓨터 종료 후 Agent Teams를 다시 띄울 때 각 팀원이 자기 역할과 현재 상태를 즉시 파악하기 위한 것이야.

## 1. `.agent-teams/TEAM_OVERVIEW.md` — 팀 전체 상황

포함할 내용:
- 콜마당 프로젝트 아키텍처 요약 (4개 앱 + Firebase 구조)
- Agent Teams 구성 (4명 + 리더 역할 정의)
- 팀원 간 소통 규칙 (SendMessage로 크로스 검증, 리더에게만 보고 금지)
- 전체 이슈 현황 (Critical/High/Medium/Low 건수)
- 수정 Phase 로드맵과 현재 진행 상태

## 2. 각 팀원별 컨텍스트 문서

### `.agent-teams/detector-analyst.md`
- 담당 범위: Call Detector 앱 전체
- 핵심 파일 목록과 각 파일의 역할
- 크로스 검증 대상 팀원과 확인 포인트
- 지금까지 발견한 이슈 목록 (상태: 완료/진행중/미착수)
- 수정 이력 (CROSS-01: ON_TRIP→ASSIGNED 완료 등)
- 다른 팀원에게 받은/보낸 검증 요청과 결과

### `.agent-teams/manager-analyst.md`
- 담당 범위: Call Manager 앱 전체
- 핵심 파일 목록과 각 파일의 역할
- 크로스 검증 대상 팀원과 확인 포인트
- 지금까지 발견한 이슈 목록 (상태: 완료/진행중/미착수)
- 수정 이력
- 내장 Detector vs 독립 Detector 차이점 목록 (CROSS-05)
- 다른 팀원에게 받은/보낸 검증 요청과 결과

### `.agent-teams/driver-analyst.md`
- 담당 범위: Driver App 전체
- 핵심 파일 목록과 각 파일의 역할
- 크로스 검증 대상 팀원과 확인 포인트
- 지금까지 발견한 이슈 목록 (상태: 완료/진행중/미착수)
- 수정 이력
- FCM 타입별 처리 현황 테이블
- 다른 팀원에게 받은/보낸 검증 요청과 결과

### `.agent-teams/firebase-analyst.md`
- 담당 범위: Firestore 규칙 + Cloud Functions + RTDB
- 핵심 파일 목록과 각 파일의 역할
- 크로스 검증 대상: 전 팀원
- 보안 규칙 전체 문제 목록과 수정 상태
- request.auth == null 패턴 18곳 목록
- 비원자적 업데이트 함수 목록 (원자성 OK/NG 표시)
- Cloud Functions 트리거 매핑 (어떤 Firestore 변경 → 어떤 함수 → 어떤 FCM)
- 다른 팀원에게 받은/보낸 검증 요청과 결과

## 3. `.agent-teams/CROSS_VERIFICATION_LOG.md` — 크로스 검증 기록

포함할 내용:
- 날짜별 크로스 검증 요청/응답 기록
- 어떤 팀원이 어떤 팀원에게 무엇을 확인 요청했고 결과가 어땠는지
- 시나리오 시뮬레이션 결과 요약

## 4. `.agent-teams/REVIVAL_PROMPT.md` — 팀 부활용 프롬프트 템플릿

다음 세션에서 이 프롬프트를 쓰면 팀을 바로 복원할 수 있도록 템플릿 작성:

```
콜마당 프로젝트의 Agent Team을 재구성해줘.

.agent-teams/ 폴더의 문서를 읽고:
1. TEAM_OVERVIEW.md로 전체 상황 파악
2. 각 팀원별 문서(detector-analyst.md 등)를 해당 팀원에게 컨텍스트로 전달
3. CROSS_VERIFICATION_LOG.md로 이전 검증 기록 파악
4. 현재 Phase에서 미착수/진행중인 이슈부터 이어서 작업

팀원 간 소통 규칙:
- 자기 모듈에서 다른 모듈과 교차되는 부분 발견 시 해당 팀원에게 직접 SendMessage
- 리더에게만 보고 금지, 팀원끼리 먼저 합의 후 종합 결과 보고
```

## 중요
- 각 문서에는 "마지막 업데이트 시각"을 포함해줘
- 수정된 코드의 정확한 파일 경로와 라인 번호를 기록해줘
- 미해결 이슈에는 다음 단계(next step)를 명시해줘

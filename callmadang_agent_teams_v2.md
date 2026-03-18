# 콜마당 Agent Teams 2차 분석 - 크로스 검증 + 시뮬레이션

아래를 Claude Code에 붙여넣으세요.

---

## 사전 조건
이전 분석에서 Critical 13건, High 17건의 잠재 문제가 발견되었습니다.
이번에는 Agent Team 팀원 간 직접 소통을 통해 크로스 검증하고, 핵심 시나리오를 코드 레벨로 시뮬레이션합니다.

## 중요: 팀원 간 소통 규칙
- 각 팀원은 팀 config를 읽어 다른 팀원의 존재를 파악할 것
- 자기 모듈에서 다른 모듈과 교차되는 부분을 발견하면, 해당 팀원에게 직접 SendMessage로 확인 요청할 것
- 확인 요청을 받은 팀원은 자기 코드에서 검증 후 결과를 회신할 것
- 리더에게만 보고하지 말고, 팀원끼리 먼저 합의한 후 종합 결과를 리더에게 보고할 것

## 1차 보고서 핵심 발견 (전 팀원 필독)

CROSS-01: Detector 배차 시 기사 상태 ON_TRIP, Manager는 ASSIGNED → Driver App 불일치
CROSS-02: CallStatus enum이 4개 앱에서 모두 다름
CROSS-03: Customer App만 FCM 100% 의존, 실시간 리스너 비활성화
CROSS-04: Firestore/RTDB 보안 규칙 완전 개방 (read/write: true)
CROSS-05: 내장 Detector vs 독립 Detector 코드 동기화 실패 (wasRinging, ExcludeNumber 누락)
CROSS-06: 전 앱에서 비원자적 Firestore 업데이트
CROSS-07: rejectCall() 비어있음 + cancelCall() 시 기사 상태 미복구

## Agent Team 구성 (4명 + 리더)

### 팀원 1: detector-analyst
담당: Call Detector
크로스 검증 대상: manager-analyst, driver-analyst
필수 확인사항:
- Detector가 배차 시 Firestore에 쓰는 정확한 필드명, 값, 경로를 manager-analyst에게 공유하고 Manager 쪽 읽기 코드와 일치하는지 확인 요청
- Detector 배차 후 Driver App에 전달되는 데이터 구조를 driver-analyst에게 공유하고 수신 코드와 일치하는지 확인 요청
- 내장 Detector(Manager 내)와 독립 Detector의 코드를 라인 단위로 비교해서 차이점 목록 작성

### 팀원 2: manager-analyst
담당: Call Manager
크로스 검증 대상: detector-analyst, driver-analyst, customer-analyst
필수 확인사항:
- Manager가 콜 상태를 변경할 때 쓰는 Firestore 경로와 필드를 driver-analyst, customer-analyst에게 공유
- cancelCall() 실행 시 변경되는 모든 Firestore 문서를 나열하고, driver-analyst에게 기사 상태 복구가 되는지 확인 요청
- Manager의 CallStatus enum 목록을 전 팀원에게 공유하고 각자의 enum과 매핑 가능한지 확인 요청

### 팀원 3: driver-analyst
담당: Driver App
크로스 검증 대상: manager-analyst, detector-analyst, customer-analyst
필수 확인사항:
- acceptCall() 시 Firestore에 쓰는 상태값을 manager-analyst, customer-analyst에게 공유
- rejectCall()이 진짜 비어있는지 확인하고, 비어있다면 어떤 상태 전이가 누락되는지 manager-analyst와 논의
- FCM 수신 시 파싱하는 필드명을 manager-analyst가 FCM 발송 시 보내는 필드명과 대조

### 팀원 4: firebase-analyst
담당: Firestore 규칙 + Cloud Functions
크로스 검증 대상: 전 팀원
필수 확인사항:
- 현재 보안 규칙에서 사무실 간 데이터 격리가 실제로 되는지, 각 팀원에게 자기 앱의 쿼리 패턴을 받아서 규칙과 대조
- Cloud Functions에서 상태 변경 시 트리거되는 함수 목록을 전 팀원에게 공유하고, 각자의 기대와 일치하는지 확인

## 시뮬레이션: 5가지 핵심 시나리오

모든 팀원이 협력하여 아래 시나리오를 코드 레벨로 추적합니다.
각 단계에서 "어떤 함수가 호출되고 → Firestore 어떤 경로에 어떤 값이 쓰이고 → 다른 앱의 어떤 리스너가 받고 → UI에 어떻게 반영되는지"를 구체적으로 추적하세요.

### 시나리오 1: 정상 흐름 (Manager 배차)
고객 전화 수신 → Manager가 콜 접수 → 기사 배차 → 기사 수락 → 픽업 → 운행 → 완료
→ 각 단계에서 4개 앱이 보는 상태값을 나열하고 불일치 여부 확인

### 시나리오 2: Detector 배차 흐름
고객 전화 수신 → Detector가 감지 → Detector에서 직접 배차 → Manager 화면 반영 → 기사 수락 → 완료
→ 시나리오 1과 Firestore 데이터 구조가 동일한지 비교

### 시나리오 3: 기사 취소 → 재배차
배차 완료 → 기사가 취소 (또는 관리자가 취소) → 콜 상태 복원 → 다른 기사에게 재배차
→ 각 단계에서 기사 상태, 콜 상태가 정확히 복원되는지 추적
→ rejectCall()이 비어있는 상태에서 이 흐름이 가능한지 검증

### 시나리오 4: 네트워크 불안정
기사가 운행 중 네트워크 끊김 → 오프라인 상태에서 상태 변경 시도 → 재연결
→ Firestore 오프라인 캐시 동작, 재연결 시 동기화 순서, 충돌 해결 방식 추적

### 시나리오 5: 동시 사용 (10개 사무실)
사무실 A와 사무실 B가 동시에 콜 생성 → 각각 기사 배차 → 데이터 격리 확인
→ Firestore 쿼리가 자기 사무실 데이터만 가져오는지 코드와 보안 규칙 모두에서 검증

## 결과물
1. 시나리오별 데이터 흐름도 (함수명 → Firestore 경로 → 리스너 → UI)
2. 각 시나리오에서 발견된 끊김/불일치 지점 목록
3. 1차 보고서 CROSS-01~07에 대한 팀원 간 크로스 검증 결과 (확인됨/오탐/추가발견)
4. 심각도 재평가 (실제 시뮬레이션 기반)
5. 수정 권장사항 (수정 시 다른 앱에 미치는 영향 포함)

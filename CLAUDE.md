# 개발 환경

## 로컬 환경

| 항목 | 내용 |
|------|------|
| **경로** | `C:\Users\kala1\designated_driver` |
| **SSD** | Samsung 980 Pro 1TB |
| **역할** | 코드 수정, 빌드, 테스트, commit/push 모두 로컬에서 수행 |

## 세션 명령어

| 말하면 | 실행 내용 |
|--------|----------|
| **"시작해줘"** 또는 **"깃풀해줘"** | `git pull origin <현재브랜치>` |
| **"종료해줘"** 또는 **"깃체크해줘"** | `bash git-check.sh` → 상태 확인 |
| **"시뮬레이션해줘"** | `.agent-teams/simulation/PLAYBOOK.md` 읽기 → `STATE.md` 확인 → 즉시 실행 |

### Claude 실행 규칙

**시작 시:**
1. `git pull origin <현재브랜치>` 실행
2. `bash git-check.sh` 실행

**종료 시:**
1. `bash git-check.sh` 실행
2. [SAFE] → "안전하게 종료 가능" 안내
3. [WARNING] → 로컬 변경사항 있음 → commit/push 필요 여부 확인

## 워크플로우
```
[로컬] 코드 수정 → 빌드 → 기기 설치 → 테스트 → commit → push
```

### 연결된 기기
- **SM_A325N** (RF9R5013HEK): call_manager 설치
- **SM_G996N** (R3CR312MB1L): driver_app 설치

### 현재 브랜치
- `firestore-migration-backup`

---

# 실행원칙
1. 모든 대답은 속도에 연연하지말고 심사숙고해서 두번 이상 검토해서 내놓을것
2. 하드코딩은 절대 안돼 항상 정석으로 진행할것
3. 수정전에는 항상 허락을 구할 것
4. 요구한것 이상의 수정을 하지말것. 요구한것에 도움이 되는것은 제안을 하고 허락을 구할 것. 임의로 수정하지말것.
5. 정확한 답이 아닌 경우 혹은 모호한 경우에는 외부검색을 통해 근접한 대답을 추론하여 실행전 사실대로 말해 허락을 구할 것 


# 대리운전 통합 플랫폼 프로젝트 문서

## 프로젝트 개요

이 프로젝트는 지방 대리운전 회사를 위한 통합 관리 플랫폼입니다. 현재 5명의 대리기사와 3명의 픽업기사가 근무하는 환경에서, 3대의 전화기로 들어오는 고객 호출을 효율적으로 관리하고 배차하는 시스템입니다.

### 주요 목표
- 무작위로 걸려오는 호출 전화를 자동으로 감지하고 Firebase에 저장
- 관리자가 실시간으로 호출을 확인하고 기사에게 배차
- 기사 앱을 통한 운행 상태 관리 및 정산
- 다중 사무실 지원 및 콜 공유 시스템 구축

## 시스템 아키텍처

### 전체 구성
```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Call Detector  │     │  Call Manager   │     │   Driver App    │
│   (Android)     │     │   (Android)     │     │   (Android)     │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                         │
         └───────────────────────┴─────────────────────────┘
                                 │
                         ┌───────┴────────┐
                         │   Firebase     │
                         │  (Firestore)   │
                         └───────┬────────┘
                                 │
                         ┌───────┴────────┐
                         │Cloud Functions │
                         └────────────────┘
```

### 데이터 흐름
1. **콜 발생**: 고객이 사무실 전화로 전화
2. **콜 감지**: Call Detector 앱이 전화를 감지하고 Firebase에 정보 저장
3. **콜 확인**: Call Manager 앱에서 실시간으로 새로운 콜 확인
4. **배차**: 관리자가 대기 중인 기사 중 한 명을 선택하여 배차
5. **알림**: FCM을 통해 기사 앱에 푸시 알림 전송
6. **운행 관리**: 기사가 콜을 수락하고 운행 상태를 업데이트
7. **정산**: 운행 완료 후 정산 처리

---

# 프로젝트 현황 (2026-03-07 기준)

## 코드 리뷰 및 수정 현황
5개 세션에 걸쳐 4개 앱 + Cloud Functions 전체 크로스 검증 완료.
이후 5단계 순차 수정 진행.

| 구분 | 건수 |
|------|------|
| 전체 이슈 | 40건 |
| 수정 완료 | 25건 |
| 오탐 확정 | 3건 |
| 해소 확정 | 4건 |
| 잔여 (보안+후순위) | 8건 |

## 수정 완료 Phase별 요약
- **Phase 1** (파일럿 품질): CUST-01, CUST-02, CUST-04, BUG-D12
- **Phase 2** (파일럿 안정성): BUG-D11, CUST-06/NEW-13, CROSS-05
- **Phase 3** (정산 정확성): STL-01, STL-02, STL-05, STL-08
- **Phase 5** (기능 개선): CROSS-06, CUST-03, NEW-15
- **이전 수정**: CROSS-01, CROSS-07, Driver취소수신, CD-01, STL-09, STL-03, NEW-11

## CF 배포 상태
- Cloud Functions 배포 완료 (41개 함수, 2026-03-07)
- 주요 신규 함수: `notifyDriverCancellation`, `checkAssignedTimeout`

## 잔여 작업
- **보안 강화 6건**: 플레이스토어 정식 배포 전 필수 (NEW-12, SEC-C01~C04, RTDB)
- **후순위 2건**: Crashlytics(NEW-08/10), 구 정산 정리(STL-10/11)
- 파일럿 테스트에는 현재 상태로 진행 가능

## Agent Teams 문서
상세 분석 결과는 `.agent-teams/` 폴더 참조:
- `TEAM_OVERVIEW.md` - 전체 현황 + 이슈 목록
- `REVIVAL_PROMPT.md` - 팀 재구성 프롬프트
- `settlement-simulation.md` - 정산 크로스 검증 결과
- `customer-app-analysis.md` - Customer App 분석 결과
- `CROSS_VERIFICATION_LOG.md` - 크로스 검증 기록

---

# Agent Teams 운영 규칙

## 팀 구성
| 팀원 | 담당 | 컨텍스트 파일 |
|------|------|--------------|
| detector-analyst | call_detector 앱 | `.agent-teams/detector-analyst.md` |
| manager-analyst | call_manager 앱 | `.agent-teams/manager-analyst.md` |
| driver-analyst | driver_app 앱 | `.agent-teams/driver-analyst.md` |
| firebase-analyst | Cloud Functions + Firebase | `.agent-teams/firebase-analyst.md` |

## 필수 규칙
1. **팀 해체 금지**: 사용자가 "팀 해체해" 또는 "팀 삭제해"라고 명시하기 전까지 팀 유지
2. **팀원 소환 시**: 반드시 자기 담당 컨텍스트 파일 + `TEAM_OVERVIEW.md`를 먼저 읽을 것
3. **임무 완료 후**: 결과를 보고하고 다음 지시 대기 (임의 행동 금지)
4. **"준비해줘" = 구성/정리까지만**. 실행은 별도 명령을 기다릴 것

## 현재 세션 에이전트 ID (resume용)
> 세션마다 갱신 필요
- detector-analyst: ab4f47f5efa6b1002
- manager-analyst: a36255af2f5e379eb
- driver-analyst: a21fbb2d596a79496
- firebase-analyst: aa4c6ce7f42b6ab9e


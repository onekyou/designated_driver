# Designated Driver Platform - 로드맵

## 프로젝트 한줄 요약
지방 대리운전 회사 통합 관리 플랫폼. 관리자 1명이 폰 3대로 콜 수신 + 배차 + 픽업을 처리하는 환경.

## 현재 상태 (2026-03-12 기준)
- **실기기 테스트 진행 중**: 3대 기기 연결, 배차/정산 흐름 테스트
- **기사 2명**: 양세훈 + 고양이
- **콜 18건**, 정산 세션 2개 (3/10, 3/11) 생성
- **정산 불일치 스케줄러 삭제 + CF 배포 완료** (3/12, 불필요한 비용 발생 방지)

### 최근 해결된 이슈 (3/11~3/12)
- **RTDB Presence 인증 에러**: CF 서비스계정에 `firebasedatabase.admin` IAM 역할 추가로 해결 (`56d71830`)
- **콜매니저 이중팝업**: fromCallManager 매핑 추가 + 시간필터 세분화 (`c8bc3955`)
- **내부호출 팝업 통합**: InternalCallAssignDialog 제거, NewCallAssignmentDialog로 통합 (`9cc1cd8b`)
- **업무마감↔로그아웃 분리**: 마감 후 매니저 확인 대기, 거절/재제출 기능 추가 (`232e4f73`)
- **정산 스케줄러 삭제**: `checkSettlementDiscrepanciesScheduled` 제거, 수동검사만 유지 (3/12 미커밋)

### Firestore 상태
- provinces(16) + admins(1) + 사무실 `nEkf0X9g3LZtRX94Mrzu`
- VIP 사무실 `0evNgfgm3xdq0v3VTFYK`는 삭제됨

## Repo
- 경로: `C:\Users\kala1\designated_driver`
- 브랜치: `firestore-migration-backup`
- 앱: call_detector, call_manager, driver_app, customer_app, functions

## Build
- JAVA_HOME: `/c/Program Files/Android/Android Studio/jbr`
- ANDROID_HOME: `/c/Users/kala1/AppData/Local/Android/Sdk`
- ANDROID_HOME 환경변수 필수 (local.properties sdk.dir이 다른 사용자로 되어있음)

## 상황별 참조 파일

| 상황 | 참조 파일 |
|------|----------|
| 프로젝트 구조/특징/핵심코드 이해 | `memory/project-characteristics.md` |
| 이슈 수정 이력/시뮬레이션 결과 확인 | `memory/issue-history.md` |
| 잔여 작업/배포 상태 확인 | `memory/pending-work.md` |
| 정산 상세 분석 | `memory/settlement_analysis.md` |
| CF 미사용 함수 목록 | `memory/unused_functions.md` |
| Firestore 접근 유틸 | `functions/scripts/firestore-util.js` |
| 팀 운영 현황/시뮬레이션 시나리오 | `.agent-teams/TEAM_OVERVIEW.md` |
| 실기기 테스트 체크리스트 | `.agent-teams/simulation/archive/simulation-v9-realdevice.md` |
| Firebase Emulator 테스트 | `.agent-teams/emulator/` (진입점: `PLAYBOOK.md`) |
| 4주 대규모 시뮬레이션 코드 | `functions/test/weekly-sim/` |
| 시스템 종합 분석 V2 (데이터 흐름) | `CALLMADANG_ANALYSIS_REPORT_V2.md` |
| 정산 시스템 전체 분석 | `docs/SETTLEMENT_SYSTEM_ANALYSIS.md` |

## 사용자 규칙
- 모든 대답은 심사숙고해서 두번 이상 검토
- 하드코딩 절대 금지
- **수정 전 항상 허락**
- 요구한것 이상의 수정 금지
- 불확실하면 외부검색 후 사실대로 말할 것

## ★ 수정 전 필수 절차 (반복 실수 방지)
- **기능 수정 시 해당 기능의 모든 진입 경로를 Grep으로 먼저 찾을 것**
- 예: 팝업 수정 → `_showNewCallPopup.value = true`를 Grep → 모든 경로(리스너, FCM, showCallDialog 등) 확인
- 예: 상태 변경 수정 → 해당 StateFlow를 설정하는 모든 위치 확인
- **각 경로의 코드를 읽은 후에만 수정 계획을 세울 것**
- 한 경로만 보고 "이게 원인이다"라고 단정하지 말 것
- 추측으로 수정하면 반드시 빠뜨리는 경로가 생김

## 팀 운영 규칙
- **팀 해체 금지**: "팀 해체해"라고 명시하기 전까지 유지
- **팀원 종료 금지**: shutdown_request 보내지 말 것 (hook으로 차단됨)
- 팀원에게 임무 부여 후 결과 대기, 임의 행동 금지
- **"준비해줘" = 구성/정리까지만. 실행은 별도 명령 대기**

## 세션 명령어
| 말하면 | 실행 |
|--------|------|
| "시작해줘" / "깃풀해줘" | `git pull origin <현재브랜치>` |
| "종료해줘" / "깃체크해줘" | `bash git-check.sh` |
| "시뮬레이션해줘" | `.agent-teams/simulation/PLAYBOOK.md` 읽기 → `STATE.md` 확인 → 즉시 실행 |

## Hook 설정
- PreToolUse hook: SendMessage에 shutdown_request 포함 시 차단

## 취소 상태 규칙
- `CANCELED` = 관리자 취소
- `CANCELLED_BY_DRIVER` = 기사 취소
- `CANCELLED_BY_CUSTOMER` = 고객 취소
- `HOLD` = 재배차 대기

## 연결된 기기
| 기기 | 시리얼 | 설치 앱 |
|------|--------|---------|
| SM-G996N S21+ | R3CR312MB1L | call_manager, driver_app |
| SM-S901N S22 | R5CT41TJZFP | call_detector, driver_app |
| SM-F721N Z Flip4 | R3CT80K78NP | driver_app, customer_app |

## 진행 중 플랜
| 파일 | 내용 |
|------|------|
| `memory/plan_settlement_dedup.md` | 정산 중복/미지급금 분리 — per-driver 필터 + filteredTrips + 실시간 갱신 완료, 미커밋 |
| `memory/settlement_dedup_full_log.md` | 정산 분리 작업 전체 대화 기록 — 문제→잘못된접근→올바른수정→시뮬레이션→실기기테스트→설계결론 |

## 완료된 플랜
| 파일 | 내용 |
|------|------|
| `memory/plan_auto_login.md` | 콜매니저+기사앱 자동로그인 구현 완료 (8d4f8aaa, 3bbbce7b) |

## 피드백
| 파일 | 내용 |
|------|------|
| `memory/feedback_save_plans.md` | 플랜모드 논의 결과 반드시 메모리에 저장할 것 (3/13 미저장 사고) |

## Firestore 사고 복구 참조
- 복구 스크립트: `functions/scripts/restoreAll.js`
- 지역 초기화 스크립트: `functions/scripts/initProvinces.ts`
- 사고 상세/대책: `memory/firestore-incident.md`

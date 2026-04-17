# Designated Driver Platform - 로드맵

> ⚠️ **메모리 저장 정책** (CLAUDE.md §메모리 저장 정책 참조)
> - **단일 원본**: `C:\Users\kala1\designated_driver\memory\` (git 추적)
> - **B 폴더** (`.claude/projects/.../memory/`)에는 **MEMORY.md만 존재해야 함**
> - 신규 토픽 파일은 반드시 프로젝트 `memory/` 절대경로로 저장
> - 이 MEMORY.md 편집 후 B로도 동일 내용 복사: `cp A/MEMORY.md B/MEMORY.md`

## 프로젝트 한줄 요약
지방 대리운전 회사 통합 관리 플랫폼. 관리자 1명이 폰 3대로 콜 수신 + 배차 + 픽업을 처리하는 환경.

## 대시보드 (2026-04-17 기준)
- **방향**: Flutter 전환 (Swift 보류) — iOS 출시는 Codemagic 기반 5~7주
- **최근 마일스톤**: 4/16 Flutter 매핑 문서 27개 + 의제 12/14 결정, P0 9건 정리
- **다음 스텝**: P0 9건 패치 → 의제 2/3 결정 → Codemagic 첫 iOS 빌드
- **사용자 Week 0 잔여**: 앱 메타데이터 + 기사 테스터 Apple ID 수집
- **날짜별 상태 상세**: `memory/current_status.md`

## 상황별 참조 파일

| 상황 | 참조 파일 |
|------|----------|
| 현재 상태 / 날짜별 이력 | `memory/current_status.md` |
| 프로젝트 구조/특징/핵심코드 | `memory/project-characteristics.md` |
| 이슈 수정 이력 / 시뮬레이션 결과 | `memory/issue-history.md` |
| 잔여 작업 / 배포 상태 | `memory/pending-work.md` |
| 정산 상세 분석 | `memory/settlement_analysis.md` |
| 작업일지 (날짜별 상세) | `memory/worklogs/` |
| CF 미사용 함수 목록 | `memory/unused_functions.md` |
| Firestore 사고 / 현재 상태 | `memory/firestore-incident.md` |
| Firestore 접근 유틸 | `functions/scripts/firestore-util.js` |
| 팀 운영 / 시뮬레이션 시나리오 | `.agent-teams/TEAM_OVERVIEW.md` |
| 실기기 테스트 체크리스트 | `.agent-teams/simulation/archive/simulation-v9-realdevice.md` |
| Firebase Emulator 테스트 | `.agent-teams/emulator/` (진입점: `PLAYBOOK.md`) |
| 4주 대규모 시뮬레이션 코드 | `functions/test/weekly-sim/` |
| 시스템 종합 분석 V2 | `CALLMADANG_ANALYSIS_REPORT_V2.md` |
| 정산 시스템 전체 분석 | `docs/SETTLEMENT_SYSTEM_ANALYSIS.md` |
| 홈페이지 | `memory/reference_homepage.md` |

## 진행 중 플랜

### Flutter 전환 (4/16~, 최우선)
| 파일 | 내용 |
|------|------|
| `C:\Users\kala1\.claude\plans\bubbly-cuddling-hopcroft.md` | 마스터 플랜 (타임라인 5~7주) |
| `flutter/README.md` | Flutter 전환 전략 + Swift 재고 배경 |
| `flutter/WORKING_DOC.md` | 의제 14개 논의 누적 + §5 결정 12건 |
| `flutter/FUNCTIONAL_INVENTORY.md` | 3컬럼 매핑 (Android/iOS/Flutter) |
| `flutter/SHARED_LOGIC.md` | 언어 독립 로직 명세 포인터 |
| `flutter/SERVER_TASKS.md` | CF/Firestore 서버측 작업 종합 |
| `flutter/REVIEW_FINDINGS.md` | 교차 리뷰 P0 9건 — **코딩 진입 전 필수** |
| `flutter/driver_app/MVP/` | 기사앱 매핑 문서 10개 + PLAN.md |
| `flutter/customer_app/MVP/` | 손님앱 매핑 문서 9개 + PLAN.md |
| `memory/plan_flutter_driver_app.md` | Flutter Phase 1~5 구현 이력 (로직 참조) |
| `.agent-teams/kotlin-expert.md` + `flutter-expert.md` | Flutter 전환 팀 (idle) |

### Swift 네이티브 (보류 자산, Flutter MVP 실패 시 복귀)
| 파일 | 내용 |
|------|------|
| `ios/README.md` | iOS Swift 전략 |
| `ios/WORKING_DOC.md` | Swift 논의 누적 Phase 0~B |
| `ios/SHARED_LOGIC.md` | 언어 독립 명세 (재활용) |
| `ios/driver_app/MVP/` | Swift 매핑 문서 8개 |
| `ios/driver_app/REINFORCEMENT/TRIGGERS.md` | R1/R2 발동 기준 |
| `ios/customer_app/PLAN.md` | Swift App Clip 설계 |
| `.agent-teams/swift-expert.md` | swift-expert 페르소나 |

### 기타
| 파일 | 내용 |
|------|------|
| `memory/plan_pickup_driver_app.md` | 픽업기사앱 기획 (3/17 승인) |
| `memory/plan_homepage_showcase.md` | 홈페이지 앱 쇼케이스 데모 WIP (4/7) |
| `memory/plan_settlement_dedup.md` | 정산 중복/미지급금 분리 — 미커밋 |
| `memory/settlement_dedup_full_log.md` | 정산 분리 전체 대화 기록 |

## 완료된 플랜
| 파일 | 내용 |
|------|------|
| `memory/plan_auto_login.md` | 콜매니저+기사앱 자동로그인 완료 |
| `memory/plan_sequential_flow_test.md` | 7일 335콜 ALL PASS |
| `memory/plan_final_test_master.md` | 3단계 마스터 검증 완료 |
| `.agent-teams/simulation/manager-direct-drive.md` | 관리자 직접운행 MVP v5 시뮬레이션 |

## 시뮬레이션 스크립트
| 파일 | 용도 | 결과 |
|------|------|------|
| `functions/scripts/sequential-flow-test.js` | 7일 350콜 순차 흐름 | 89 CP ALL PASS |
| `functions/scripts/multi-office-test.js` | 10사무실 공유콜 교차+취소 | 127 CP ALL PASS |
| `functions/scripts/peak-load-test.js` | 100건 동시 CF 트랜잭션 | 30 CP ALL PASS |

## 비즈니스 모델
- `memory/project_business_model.md` — 사무실 독립형, 고객 귀속, 구독료만
- `memory/project_expansion_vision.md` — 대리운전 → 식당/배달앱 확장

## 확장 계획
| 파일 | 내용 |
|------|------|
| `memory/project_scale_target.md` | 최종 목표 2000개 사무실 |
| `memory/office-expansion-plan.md` | Presence 분리 + 스케줄러 최적화 (100개 초과 시) |
| `memory/project_flutter_migration_strategy.md` | Kotlin→Flutter 단계적 전환 전략 |

## 자주 쓰는 단축 정보

### 연결된 기기
| 기기 | 시리얼 | 설치 앱 |
|------|--------|---------|
| SM-G996N S21+ | R3CR312MB1L | call_manager, driver_app |
| SM-S901N S22 | R5CT41TJZFP | call_detector, driver_app |
| SM-F721N Z Flip4 | R3CT80K78NP | driver_app, customer_app |

### 세션 명령어
| 말하면 | 실행 |
|--------|------|
| "시작해줘" / "깃풀해줘" | `git pull origin <현재브랜치>` |
| "종료해줘" / "깃체크해줘" | `bash git-check.sh` |
| "시뮬레이션해줘" | `.agent-teams/simulation/PLAYBOOK.md` 읽기 → `STATE.md` 확인 → 즉시 실행 |

### 취소 상태 규칙
- `CANCELED` = 관리자 취소
- `CANCELLED_BY_DRIVER` = 기사 취소 (cancelTrip, 3/24 HOLD에서 변경)
- `CANCELLED_BY_CUSTOMER` = 고객 취소 (3/24 ACCEPTED/PREPARING까지 확장)
- `HOLD` = 재배차 대기 (CF 코드 잔류, 기사앱에서 더 이상 생성하지 않음)

### 팀 운영 규칙
- **팀 해체 금지**: "팀 해체해"라고 명시하기 전까지 유지
- **팀원 종료 금지**: shutdown_request 보내지 말 것 (hook으로 차단됨)
- "준비해줘" = 구성/정리까지만. 실행은 별도 명령 대기

### 사용자 규칙
- 모든 대답은 심사숙고해서 두번 이상 검토
- 하드코딩 절대 금지
- **수정 전 항상 허락**
- 요구한것 이상의 수정 금지
- 불확실하면 외부검색 후 사실대로 말할 것

### Hook 설정
- PreToolUse hook: SendMessage에 shutdown_request 포함 시 차단

## 피드백
| 파일 | 내용 |
|------|------|
| `memory/feedback_grep_all_paths.md` | 수정 전 모든 진입 경로 Grep 필수 |
| `memory/feedback_save_plans.md` | 플랜모드 논의 결과 반드시 메모리 저장 |
| `memory/feedback_simplest_fix.md` | 최소 수정 우선 원칙 |
| `memory/feedback_customer_app_install.md` | 손님앱 ADB 설치 금지, SharedPrefs 주입 |
| `memory/customer_app_auth_analysis.md` | 손님앱 Anonymous Auth + QR 전용 온보딩 |

## 기타 포인터
- `memory/pending-work-customer-app.md` — 손님앱 잔여 작업
- `memory/firestore-incident.md` — Firestore 사고 복구 + 현재 상태
- 복구 스크립트: `functions/scripts/restoreAll.js`
- 지역 초기화: `functions/scripts/initProvinces.ts`

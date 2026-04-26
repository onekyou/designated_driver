# designated_drive — 대리운전 도메인 (콜마당 첫 사용 케이스)

> 콜마당 OS 의 첫 사용 케이스. 양평 1개 사무실 파일럿 운영 중.
> OS 정체성: `memory/callmadang_master_2026-04-27.md`
> 즉시 실행: `memory/callmadang_checklist_2026-04-27.md`

## 4앱 (운영 중)
| 앱 | 사용자 | 핵심 기능 |
|---|---|---|
| call_detector | 사무실 서브폰 | 전화 콜 0.5초 내 자동 인식 |
| call_manager | 사무실 매니저 | 배차·정산·관제 통합 |
| driver_app | 기사 | 콜 수락·운행·정산 |
| customer_app | 손님 (대리) | QR 귀속·호출·포인트 |

## 5번째 앱 (운영 중, 2026-04-26 추가)
| 앱 | 사용자 | 핵심 기능 |
|---|---|---|
| pickup_driver_app | 픽업 기사 | read-only 대시보드, 콜매니저 미러링 |

## 도메인 메모리 진입점

### 운영 핵심
- `current_status.md` — 날짜별 상태
- `project-characteristics.md` — 5앱 코드 맵 (함수/파일/라인)
- `pending-work.md` — 잔여 작업 (마스터 §11~12 와 정합)
- `issue-history.md` — 이슈 수정 이력

### 정산
- `settlement_analysis.md` / `DRIVER_APP_SETTLEMENT_ANALYSIS.md` / `firebase-settlement-analysis.md`
- `plan_settlement_dedup.md` / `settlement_dedup_full_log.md` / `plan_settlement_consistency_sim.md`

### 시뮬레이션
- `plan_sequential_flow_test.md` (7일 350콜)
- `plan_final_test_master.md` (3단계 마스터 검증)
- `plan_realdevice_test.md` / `simulation_verification_v2.md`

### 최근 기능
- `plan_dispatch_memo_2026-04-24.md` — 배차 STT 메모
- `dispatch_memo_speaker_context.md` — 메모 화자 컨텍스트
- `plan_voice_parsing_external_data_2026-04-26.md` — 보이스 파싱 외부 데이터 흡수
- `plan_auto_login.md` — 자동 로그인

### 기타 운영
- `firestore-incident.md` / `unused_functions.md` / `rejected_by_driver_dead_data.md`
- `office-expansion-plan.md` (100개 초과 시)
- `reference_homepage.md` / `plan_homepage_showcase.md` / `worklogs/`

## 하위 폴더 (보류/완료/특화)
- `pickup_driver_app/` — 5번째 앱 플랜 (Phase 2 준비)
- `customer_app/` — 대리 손님앱 (보류, MVP 재작성 보류 자료)
- `flutter_paused/` — Flutter 전환 (보류, 양평 정합성 입증 후 복귀)
- `ios_phase6_paused/` — iOS 출시 Phase 6 (보류, 동일 트리거)
- `owner_portal_completed/` — H2 사장님 포털 (배포 완료, 변경 없음)

## SUPERSEDED 자료
- `project_business_model.md` — 마스터 §3.1, §3.4, §10.4 흡수 (Shopify 비유 1줄만 미흡수)
- `project_expansion_vision.md` — 마스터 §1.3 환상 ③, §7 시간차 파이프라인으로 대체
- `project_scale_target.md` — 마스터 §1.3 환상 ③ 으로 대체
- `strategy_pivot_2026-04-21.md` — 마스터로 대체 (병렬 낚싯대 폐기)

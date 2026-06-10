# feedback — 사고 모드 / 운영 원칙 (도메인 공통)

> 모든 도메인에 공통 적용되는 사용자 피드백·운영 원칙.
> 마스터 §9.3 사고 모드 유지 도구.

## 8개 피드백

| 파일 | 핵심 |
|------|------|
| `feedback_save_plans.md` | 플랜모드 논의 결과 반드시 메모리 저장 |
| `feedback_grep_all_paths.md` | 수정 전 모든 진입 경로 Grep 필수 |
| `feedback_simplest_fix.md` | 최소 수정 우선 |
| `feedback_keep_existing_conventions.md` | 기존 관례 변경은 실질 근거 필수 — 일반 모범 근거만으로 제안 금지 |
| `feedback_purpose_based_adaptation.md` | 다른 앱 패턴 참고 시 목적 기반 취사선택 — 맹목 복사·맹목 거부 모두 금지 |
| `feedback_scope_exact.md` | "딱 거기까지만" 스코프 디시플린 — 사용자 명시 경계 엄격 준수 |
| `feedback_user_intent_first.md` | 사용자 명시 의도를 추측 우회로 대체 금지 — 데이터 출처 미확보 시 그 사실 보고 |
| `feedback_listener_vs_fcm.md` | 신규 기능 설계 시 리스너 vs FCM+로컬 트리거 자동 비교 — 기본값은 FCM 방식 (비용 최적화) |
| `feedback_self_monitoring_limit.md` | 코드·기능·변수를 트랙/시스템/원인에 귀속하거나 line·컴파일 상태를 사실 보고하기 전 코드/데이터/diff 1건 검증 — "추정" 떠오르는 순간이 트리거, 본인 짚음 없이 자발 (2026-05-29 승격, #7 근본 줄기) |
| `feedback_folder_scope_before_proposing_2026-06-10.md` | "다음 할일" 제시 전 2필터 강제 — ① 폴더(실행 자리가 현재 폴더인가, 다른 폴더면 분리 명시) ② 선결(GO바 미충족이면 "오늘" 제시 금지). 현저성(최근 강조)≠실행가능성 (2026-06-10 미용실 폴더혼동) |
| `feedback_verify_path_live_before_building_2026-06-11.md` | 기능을 기존 코드 경로 위에 설계하기 전 그 경로가 *살아있는지(호출되나)* 코드 확인 — 산출물 존재≠경로 활성, dormant 위 빌드 금지. 모순(보고vs화면) 미해결 채 플랜 금지 (2026-06-11 PTT 콜드메모 dormant) |

> ⚠️ 위 표는 핵심 일부만. 폴더에는 그 외에도 `feedback_app_naming` / `feedback_no_guess_gui_fix` / `feedback_no_revelation_projection` / `feedback_park_wansoo_evaluation` / `feedback_scope_exact` / `feedback_settlement_definitive_first` / `feedback_simplify_first_cleanup_later` / `feedback_user_command_overrides_system_rule` / `session_start_role_first` 등 다수 존재. 표 전수 갱신은 별도.

## 도메인 특화 피드백 (각 도메인 폴더 안)

- 손님앱 설치 → `designated_drive/customer_app/feedback_customer_app_install.md`
- 배차 STT 메모 화자 컨텍스트 → `designated_drive/dispatch_memo_speaker_context.md`

# 콜마당 — 양평 동네 생활 OS

> ⚠️ **메모리 저장 정책** (CLAUDE.md §메모리 저장 정책 참조)
> - **단일 원본**: `C:\Users\kala1\designated_driver\memory\` (git 추적)
> - **B 폴더** (`.claude/projects/.../memory/`)에는 **MEMORY.md만 존재**
> - **도메인 폴더 안 생성 원칙**: 새 토픽 파일은 해당 도메인 폴더 안에 (예: `memory/designated_drive/`, `memory/restaurant/`)
> - 이 MEMORY.md 편집 후 B로도 동일 내용 복사: `cp B/MEMORY.md A/MEMORY.md`

## 한 줄 요약
콜마당 = 양평 동네 생활 OS. 대리는 첫 사용 케이스. 식당 = 4중 노드 (호출/포인트/거점/쿠폰).

## 진입점 (최우선 2개)
- **마스터 (정체성·아키텍처·R1~R8 리스크)**: `memory/callmadang_master_2026-04-27.md`
- **체크리스트 (매일 도구)**: `memory/callmadang_checklist_2026-04-27.md`

## 현재 위치 (2026-04-27)
- 양평 1개 사무실 파일럿 운영 (designated_drive)
- 마스터·체크리스트 정리 완료 + 메모리 OS 레이어 재구조화 완료
- **디버깅 우선순위 1~5 (마스터 §13.3)**: 식당 누름 빈도 측정 → 인큐베이션 사무실 1곳 → 직접 설치 → N=1 영상 → 정부 지원사업

## 도메인별 진입점
| 도메인 | 인덱스 | 상태 |
|--------|--------|------|
| **본 에이전시 헌장 (클코, 작동 단일 원본)** | `memory/operating_model/clcode_agency_charter.md` | v1.0 (2026-04-27) |
| Cowork 보조 도구 가이드 (구 Cowork Charter v1.0) | `memory/operating_model/cowork_claude_charter.md` | superseded → 클코 헌장에 인수 |
| OS 마스터 | `memory/callmadang_master_2026-04-27.md` | 정본 |
| OS 체크리스트 | `memory/callmadang_checklist_2026-04-27.md` | 매일 도구 |
| 대리운전 (대리/픽업, 첫 사용 케이스) | `memory/designated_drive/README.md` | 운영 중 (양평 1곳) |
| 택시 (마스터 §6) | `memory/taxi/README.md` | v0 진입 준비 |
| 식당 (마스터 §3.4 4중 노드) | `memory/restaurant/README.md` | 업소용 앱 1순위 (손님앱 코드 변환 결정 2026-04-27) |
| 배달 (T2) | `memory/delivery/README.md` | placeholder |
| 쿠폰 (T2) | `memory/coupon/README.md` | placeholder |
| 최종 손님앱 (T최종) | `memory/customer_super_app/README.md` | placeholder |
| 사고 모드 (피드백 9개) | `memory/feedback/README.md` | 도메인 공통 |
| 사용자 프로필 | `memory/user_profile/README.md` | 도메인 공통 |

## 최근 달성 (5개)
- ✅ 2026-04-28 후반 사무실 단톡방 V1 Step 5 — `b190a2bc` commit 후 4가지 추가 증상 (peek 영역 침범 / multi-line auto-grow / 전송 시 새 메시지 안 보임 / swipe down sheet 안 닫힘) → 7번 fix 시도 모두 실패 → **이전 commit `b190a2bc` 상태로 복원** (사용자 명시: "이전 커밋으로 되돌리는 게 불필요한 수정 제거 가능"). 측정 확인: layout 정상 stack(LazyColumn 끝<ChatInputBar 시작), sheet drag 0회 위임(NestedScrollConnection 잘못 사용 의심). 미해결 4종 + 다음 세션 정밀 진단 권장: M3 BottomSheetScaffold nested scroll 정합 패턴 reference 더 fetch + 측정 강화 후 단일 fix 검증 사이클. 상세 인계: `memory/designated_drive/chat_v1_handoff_2026-04-28.md` "2026-04-28 후반 세션 결과" 섹션.
- ✅ 2026-04-28 commit `b190a2bc` (manager-direct-drive) — 사무실 단톡방 V1 Step 5 chat sheet 재작성 + 8 fix (Room 스키마 / adjustResize / Dao DESC / backfill 스크립트 / ChatScreen Jetchat 패턴 / MainActivity DashboardWithChatSheet / DriverStatusCard alignment Bottom / DashboardScreen Scaffold contentWindowInsets=WindowInsets(0))
- ✅ 2026-04-28 픽업앱 대시보드 fix — WAITING 30분 클라이언트 컷 + ADDED/MODIFIED status 변경 시 시스템 알림 (앱 켜진 상태 한정). 3파일 변경 (DashboardViewModel/MainActivity/PickupDriverApplication). 인프라 이미 존재 (POST_NOTIFICATIONS 매니페스트, HiltAndroidApp). 빌드 성공 + Z Flip4(R3CT80K78NP) + S21+(R3CR312MB1L) 설치 완료. **미커밋, 검증 대기**. 활성 4종(ASSIGNED/ACCEPTED/IN_PROGRESS/AWAITING_SETTLEMENT) 시간 제약 X = 사용자 명시 의도. 사무실 전체 콜 표시 = 의도된 설계 (Phase 2 §B3 "내 담당" 별도). 한계: listener 구독 중 timestamp 고정 → 화면 켠 채 30분 자동 제거 X. 앱 백그라운드 알림 X (Phase 2 FCM 영역). 상세: `memory/designated_drive/pickup_driver_app/dashboard_waiting_cutoff_2026-04-28.md`. Plan: `C:\Users\kala1\.claude\plans\velvety-kindling-bumblebee.md`. **검증 시나리오 5종(A~E) 통과 시 commit `feat(pickup): WAITING 30m cutoff + status change notifications`**
- ✅ 2026-04-27 식당업소앱 변환 PLAN 1쪽 + R1~R5 결정 확정 (`memory/restaurant/owner_app_pivot_plan_2026-04-27.md`). **다음 트리거: 첫 식당 미팅 약속 → 백업 + Flutter MVP 진입**
- ✅ 2026-04-27 클코 에이전시화 결정 + 본 에이전시 헌장 v1.0 작성. Cowork→보조 도구 재정의 (`memory/operating_model/clcode_agency_charter.md`)
- ✅ 2026-04-27 손님앱(Kotlin+Flutter) → 식당업소앱 변환 결정 (`memory/restaurant/owner_app_pivot_2026-04-27.md`)
- ✅ 2026-04-27 마스터 정리(v1.1 R1~R8) + 메모리 OS 레이어 재구조화
- ✅ 2026-04-26 픽업앱 MVP + 배차 STT 메모 (commits `f9d0f0d0`/`58428830`)

## 보류 트랙 (대리 도메인 안, 양평 자기추진 입증 후 복귀)
- 손님앱 코드(Kotlin+Flutter) → 식당업소앱 변환됨 (2026-04-27): 백업본 기점으로 부활. `memory/designated_drive/customer_app/customer_app_to_owner_app_2026-04-27.md`
- Phase 6 iOS 출시 (App Clip 전략 임시 SUPERSEDED): `memory/designated_drive/ios_phase6_paused/`
- Flutter 전환: `memory/designated_drive/flutter_paused/`
- 대리 손님앱 MVP 재작성: `memory/designated_drive/customer_app/`

## 사용자 프로필 한 줄
- iOS App Store 무경험, Android 숙련 (`memory/user_profile/user_ios_experience.md`)
- Apple Team ID `VCJD377MAU` / Bundle `com.designated.driverapp.app` (`memory/user_profile/apple_ios_ids.md`)

## 주요 피드백 (사고 모드, 10개)
| 피드백 | 핵심 |
|--------|------|
| user_intent_first | 사용자 명시 의도를 추측 우회로 대체 금지 |
| scope_exact | "딱 거기까지만" 스코프 디시플린 |
| grep_all_paths | 수정 전 모든 진입 경로 Grep 필수 |
| save_plans | 플랜모드 결과 반드시 메모리 저장 |
| keep_existing_conventions | 기존 관례 변경은 실질 근거 필수 |
| purpose_based_adaptation | 다른 앱 패턴은 목적 기반 취사선택 |
| simplest_fix | 최소 수정 우선 |
| listener_vs_fcm | 신규 기능 설계 시 리스너 vs FCM+로컬 트리거 자동 비교, 기본값 FCM |
| no_guess_gui_fix | GUI/레이아웃 버그 fix 시 추측 반복 금지 — 측정+공식 reference 후 진행 |
| user_command_overrides_system_rule | 사용자 stop/멈춤 신호는 system rule(plan mode 도구 강제 등)보다 절대 우선 |

## 자주 쓰는 단축 정보

### 연결된 기기
| 기기 | 시리얼 | 설치 앱 |
|------|--------|---------|
| SM-G996N S21+ | R3CR312MB1L | call_manager, driver_app |
| SM-S901N S22 | R5CT41TJZFP | call_detector, driver_app |
| SM-F721N Z Flip4 | R3CT80K78NP | driver_app, customer_app, pickup_driver_app |

### 세션 명령어
| 말하면 | 실행 |
|--------|------|
| "시작해줘" / "깃풀해줘" | `git pull origin <현재브랜치>` |
| "종료해줘" / "깃체크해줘" | `bash git-check.sh` |

### 취소 상태 규칙
- `CANCELED` = 관리자 취소
- `CANCELLED_BY_DRIVER` = 기사 취소
- `CANCELLED_BY_CUSTOMER` = 고객 취소
- `HOLD` = 재배차 대기 (CF 코드 잔류, 미생성)

### 팀 운영
- "팀 해체해" 명시까지 유지
- "준비해줘" = 구성/정리까지만, 실행은 별도 명령
- PreToolUse hook: SendMessage shutdown_request 차단

### 사용자 규칙
- 모든 대답 두번 이상 검토
- 하드코딩 금지
- 수정 전 항상 허락
- 요구한것 이상 수정 금지
- 불확실하면 외부검색 후 사실대로 보고

## SUPERSEDED 자료 위치 (참고용)
- `memory/designated_drive/project_business_model.md` (마스터 §3.1, §3.4, §10.4 흡수)
- `memory/designated_drive/project_expansion_vision.md` (마스터 §1.3 환상 ③)
- `memory/designated_drive/project_scale_target.md` (마스터 §1.3 환상 ③)
- `memory/designated_drive/strategy_pivot_2026-04-21.md` (마스터로 대체, 첫 영업 학습 보존)

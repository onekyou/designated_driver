# Designated Driver Platform - 로드맵

> ⚠️ **메모리 저장 정책** (CLAUDE.md §메모리 저장 정책 참조)
> - **단일 원본**: `C:\Users\kala1\designated_driver\memory\` (git 추적)
> - **B 폴더** (`.claude/projects/.../memory/`)에는 **MEMORY.md만 존재해야 함**
> - 신규 토픽 파일은 반드시 프로젝트 `memory/` 절대경로로 저장
> - 이 MEMORY.md 편집 후 B로도 동일 내용 복사: `cp A/MEMORY.md B/MEMORY.md`

## 프로젝트 한줄 요약
지방 대리운전 회사 통합 관리 플랫폼. 관리자 1명이 폰 3대로 콜 수신 + 배차 + 픽업을 처리하는 환경.

## 대시보드 (2026-04-21 기준)
- **방향**: 🚨 **전략 피벗 — 병렬 낚싯대** (2026-04-21 첫 영업 후). 대리는 최소 유지, 필라테스/미용실 Tier 1 demand test 병렬. iOS 출시·손님앱 MVP 재작성 **보류**. 세부: `memory/strategy_pivot_2026-04-21.md`
- **현재 위치**: 🎯 **첫 대면 영업 완료 (박상준/양평) + 전략 피벗** (2026-04-21) — 테스터 친구 조언 3건: 병렬 낚시대 / 20~40대 타겟 / 귀속노드가 진짜 자산. Tier 1 랜딩 2종(필라테스, 미용실) 제작이 다음 단계. 영업 중 아이디 오타 현장 체크리스트 필요. 2026-04-22 캐나다 VFX 친구(아바타 CG팀 경력) 방문 → 시나리오 파일럿 논의 예정. **Phase 6 iOS 출시 관련 작업은 피벗 전까지 보류**.
- **최근 달성**:
  - ✅ 2026-04-24 (픽업기사앱 MVP 완성 + 실기기 검증 + commit `58428830`): `pickup_driver_app/` 5번째 앱 스캐폴딩 + 빌드 + Z Flip4 설치 + 로그인/회원가입/읽기전용 대시보드 엔드투엔드 동작 확인. driver_app fork 기반, applicationId `com.designated.pickupapp` (Firebase Console 기존 등록 재사용). **서버측 변경 0** — 기존 `approveDriver()` 가 `driverType="픽업기사"` → pickup_drivers 자동 라우팅, firestore.rules 이미 완비. Dashboard: 진행중 콜 5상태 snapshot listener 단일 + `departure_set → waypoints_set → destination_set` headlineSmall+Bold 경로 + 요금 포맷. **구조**: 순수 Firestore snapshot listener (FCM/Room 없음, 앱 열려있을 때만 실시간). 사용자 요청: Phase 2 준비 — 세부 로드맵: `memory/pickup_app_phase2_plan.md`. MVP 플랜: `C:\Users\kala1\.claude\plans\tranquil-puzzling-squirrel.md`
  - ✅ 2026-04-23 (정산 fix 커밋 + 실기기 테스트 준비): 여러 세션 미커밋 WIP 였던 **이월금(carryOver) 중복 합산** 5 파일 fix 확정 commit (`607c6bb7`). 공통 원리: `balance` 확정 시점(TRANSFERRED/SETTLED/CONFIRMED/PENDING_CONFIRM+balance>0) 판정해 재가산·재공제 단락. driver_app → S21+/S22/Z Flip4 설치 완료, call_detector → S21+ 설치 (동일기기 공존 주의). 1004 사무실(`RUbeBEvGGYP5wMhJHhMF`, yangpyeong) 제로 베이스: calls(10)+settlementSessions(1) 삭제 + 2기사 carryOver=0 리셋 + dailySettlement 필드 삭제. 신규 스크립트: `functions/scripts/reset-office-1004.js`. 3 시나리오 실기기 검증 대기 중. 세부: `memory/current_status.md` + 플랜 `C:\Users\kala1\.claude\plans\reactive-dazzling-wind.md`
  - 🎯 2026-04-21 (전략 피벗 + 첫 영업): 박상준 사장님 대면 설치 방문 → 테스터 친구 조언 흡수 → **병렬 낚싯대 Tier 구조** 확정 (Tier 1 랜딩 demand test 병렬, Tier 2부터 신호 강한 1개만). 후보: 필라테스/요가 PT ⭐ / 미용실·네일샵 / 대리 유지. 제외: 카페·식당·학원. 1주 실행 계획 확정. VFX 친구 방문 대비. 세부: `memory/strategy_pivot_2026-04-21.md`
  - ✅ 2026-04-20 저녁 (실테스트 통과): S22 에서 **call_detector + call_manager 동일기기 충돌** 발견 → call_detector 제거로 배차 팝업 정상화. FCM 알림 미도달은 테스트 중 토큰 꼬임 → 재로그인으로 managerTokens+admins.fcmToken 동기화되어 해결. Firestore 검증: 두 컬렉션 fcmToken 일치 확인. call_manager APK v1.0.2 업로드 + public/ hosting 재배포. LoginViewModel 에러 메시지 명확화 + SignUpScreen 초대 토큰 붙여넣기 버튼 추가. **오진단 정정**: admins.fcmToken 실제로 정상 저장됨 (MyFirebaseMessagingService.onNewToken Phase 1), onDriverSignupRequest CF 정상 작동. **미해결**: CallDetectorService.kt:684 `fromCallManager=true` 삭제는 오진단이었음 — 롤백 검토 필요 (긴급도 낮음, 별도 기기 분리 시 영향 없음). 세부: `memory/session_2026-04-20_evening_test.md`
  - ✅ 2026-04-20 (H2 배포 완료): CF 3개 배포(`approveOfficeApplication` 재설계, `redeemDownloadToken`/`registerOwner` 신규), firestore.rules 배포(`downloadInvites` 차단), head-manager-web 배포(`/owner/download` 신규 + `/owner/login` email+비번 재작성), call_manager v1.0.1 APK 업로드(이전 v1.0.0 isLatest=false 플립), `functions/scripts/cleanup-owner.js` 신규(백업+삭제 REST 스크립트), 박상준님 4종 문서 삭제(Auth/admins/office/office_applications) + 전량 백업 JSON 저장. 설계 문서: `memory/h2_invite_signup_2026-04-20.md`
  - ✅ 2026-04-20 (H2 코드 완료): `approveOfficeApplication` 재설계(Auth 생성 제거, `downloadInvites/{token}` 발급), `redeemDownloadToken`·`registerOwner` CF 신규, `/owner/download` 공개 다운로드 페이지 신규, `/owner/login` email+비번 재작성, `applications/detail` 승인 결과 UI(inviteUrl 복사), call_manager SignUp 재설계(토큰 필드 + `registerOwner` CF 호출), call_detector 수정 0, firestore.rules `downloadInvites` 차단 블록, `functions/scripts/inject-password.js` 박상준님 마이그레이션 스크립트. 박상준님 포함 신규 사장님 모두 앱/웹 단일 계정 진입 가능. 세부: `memory/h2_invite_signup_2026-04-20.md`. 플랜: `C:\Users\kala1\.claude\plans\joyful-waddling-zephyr.md`
  - 🚨 2026-04-20 (갭 발견, 해소): call_detector + call_manager Android 앱은 `signInWithEmailAndPassword` 인데 `approveOfficeApplication` CF는 비번 없이 계정 생성 (2026-04-19 재설계) → 박상준(첫 실사용자) 및 향후 모든 신규 사장님이 두 앱에 로그인 불가. **H2 재설계로 해소** (위 항목 참조). 기록: `memory/call_detector_manager_oauth_gap_2026-04-20.md`
  - 2026-04-20: 총관리자 로그인 Google OAuth 전환 + 첫 실사용자 등록 — `/login` Google OAuth 재작성(배포), 본인 Gmail(`onekyou71@gmail.com` UID `vK9X9...`) OFFICE_OWNER → HEAD_MANAGER 승격, 레거시 `vip@naver.com` HEAD_MANAGER 문서+Auth 계정 삭제, 테스트 application/office 정리, 첫 실사용자 **박상준/총알대리/양평**(`a01077420507@gmail.com` UID `hpKCSyX7...`) 승인 완료. 진단 유틸 `functions/scripts/diagnose-admin.js` 추가. 세부: `memory/head_manager_login_fix_2026-04-20.md`
  - 2026-04-19 (이어서⁶): 사장님 포털 운영 인프라 구축 — Storage 활성화(asia-northeast3) + `storage.rules` + OAuth 기반 APK 업로드 유틸(`functions/scripts/upload-apk-release.js`) + `functions/scripts/set-custom-claims.js` + CF `approveOfficeApplication` Gmail 기반 재설계(비번 제거 + emailVerified:true) + `/owner/login` Google OAuth 전환(전화번호 폼 삭제) + `/owner/dashboard` UI 다듬기(버튼 세로 배열, 콜매니저 2줄) + Sidebar "사장님 대시보드" 링크 추가 + 테스트 사장님 계정 정리 + call_manager/call_detector v1.0.0 초기 업로드. **미해결**: `getApkDownloadUrl` signBlob IAM 권한(콘솔 수동 필요). 세부: `memory/owner_portal_setup_2026-04-19.md`
  - 🚨 2026-04-19 (긴급): Flutter MVP ↔ Kotlin 매핑 불일치 통합 감사 완료 — 19개 MVP 전수 감사, **High 4 / Medium 7 / Low 4**. Customer: `recoverCustomerAccount` CF 누락 + `linkWithCredential` 오류(Kotlin 은 `signInWithCredential`) + `checkPhoneNumberDuplicate` Phase 2 미룸. Driver: `checkPendingStatusAndProceed` 누락(승인 대기 계정 진입 가능) + 자동로그인 옵션 A/B 불일치. 근본 원인 = "의제 N 결정 = 개선안" 이라는 이름의 Kotlin 이탈. 사용자 판단 "코틀린 매핑이 진짜 목적" 확정. 보고서: `memory/customer_app_auth_mismatch_audit_2026-04-19.md`. Chunk 5 영구 대기. 다음 세션: driver_app_flutter 기존 구현 감사(읽기만) → MVP 재작성 → Chunk 4 패치 → Chunk 5 재설계 (옵션 A 순차)
  - 2026-04-19 (이어서⁵): 손님앱 Week 1~3 Chunk 5 완료 — ProfileNotifier 본체(loadProfile + updateProfile 2a 리팩토링 + reloadOfficeInfo + acceptTerms + updateHomeAddress + ref.listen uid watch) + AuthNotifier `_registerFcmToken` Firestore 저장 활성화 + ProfileUiState error/isSaving 필드 추가 + 테스트 18 신규. **111/111 PASS**. 호출 순서 검증(saveCallOrder), 부분 실패 테스트(subclass override). **순환 의존 회피** — AuthNotifier 는 ProfileNotifier 대신 SharedPreferences 직접 읽음. CF checkPhoneNumberDuplicate 제외(복원 트리거: 30일/100명/문의1건)
  - 2026-04-19 (이어서⁴): 손님앱 Week 1~3 Chunk 4 완료 — AuthNotifier 본체(Anonymous signIn + Phone Auth verifyPhoneNumber/verifyCode + linkWithCredential 핵심 + credential-already-in-use fallback + FCM 토큰 구독/등록 골격) + fcm_token_payload 헬퍼(기사앱 패턴, isIos 주입) + formatKoreanPhoneNumber(010→+82) + mocktail/firebase_auth_mocks/mock_exceptions 테스트 16 신규. **92/92 PASS**. NDK 28.2 upgrade로 APK 빌드 2배 단축(75s). Crashlytics 생략(기사앱 선례)
  - 2026-04-19 (이어서³): 손님앱 Week 1~3 Chunk 3 완료 — UI State 4종(call/point/profile/auth) Freezed + core/providers.dart(Firebase singletons + SharedPrefs + SecureStorage) + core/routing(GoRouter 11 routes + MainShell BottomNav 4탭 + PlaceholderScreen) + AuthNotifier 껍데기(생성자+update stubs+UnimplementedError) + main.dart Firebase 부팅. **76/76 PASS**. ProfileUiState는 FCM 경로용 provinceId/cityId/officeId 3필드 선제 포함. smoke test는 MockFirebaseAuth/FakeFirestore/mocktail messaging — Chunk 4 본격 테스트 디딤돌
  - 2026-04-19 (이어서²): 손님앱 Week 1~3 Chunk 2 완료 (commit `0d284b1f`) — Freezed 5 모델 (CustomerPoints/PointTransaction/BannerAdData/CustomerCall/CustomerInfo) + build_runner 10 generated + 29 신규 assertions. 64/64 PASS. CustomerCall 3중복 필드 + @JsonKey assignedDriverId 매핑 + timestamp 양방향 전수 검증
  - 2026-04-19 (이어서): 손님앱 Week 1~3 Chunk 1 완료 (commit `b4d68ae6` + `e59d13fa`) — Enums 3 (CallState sealed / CustomerGrade / TransactionType) + Converters 3 (Timestamp/Grade/TxType) + unit test 34/34 PASS. MVP 경로 20곳 feature-based 구조로 이관
  - 2026-04-19: 손님앱 Flutter 스캐폴딩 완료 (commit `69295495`) — `customer_app_flutter/` 신규, Bundle ID 통일(`com.designated.customer.app`), Android APK 빌드 PASS. ENUMS/MODELS enum 드리프트 해소(commit `1e909768`)
  - 2026-04-18 오후: Phase 6 ② Day 4 Phase A 완료 (commit `fab1050c`) — Flutter 기사앱 platform 저장 + Codemagic iOS Simulator integration_test 경로 구축
  - 2026-04-18 오전: Phase 6 ① 완료 (commit `745070fa`) — FCM 34곳 apns + acceptanceEvents 집계 + rules 신규 블록. 프로덕션 배포 완료
  - 2026-04-17: Apple 생태계 진입 (Team `VCJD377MAU`, Bundle ID `com.designated.driverapp.app`) + Codemagic no-codesign 빌드 성공 + P0 스펙 패치
- **Phase 6 남은 코딩 7개** (체크리스트: `memory/phase6_coding_remaining.md`):
  1. ✅ 서버측 CF 보강 (FCM apns + acceptanceEvents + rules 신규) — 2026-04-18 완료
  2. 기사앱 iOS 코드 보강 + Kotlin platform 저장 + backfill + rules 기존 규칙 확장 (②의 통합 범위)
  3. Bundle ID 실제 변경 (Xcode + Firebase Console)
  4. ASC API Key + Codemagic 서명
  5. codemagic.yaml Phase B (서명 + TestFlight)
  6. TestFlight 배포 + 파일럿
  7. 손님앱 신규 포팅 (Phase 2) — 🚨 **감사 결과 MVP 재작성 선행 필수** (High 4건). Chunk 1~4 원격 반영 완료, Chunk 5 로컬 보존(미커밋). 세부: `memory/customer_app_auth_mismatch_audit_2026-04-19.md` §5
- **사용자 Week 0 잔여**: 앱 아이콘/스크린샷/설명/개인정보 URL + 기사 테스터 Apple ID + S22 알림 권한 설정 점검 (✅ 2026-04-19 손님앱 iOS Firebase Console 등록 + GoogleService-Info.plist 배치 완료 — commit `7c06f6cd`)
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
| 🚨 `memory/customer_app_auth_mismatch_audit_2026-04-19.md` | **MVP ↔ Kotlin 매핑 불일치 통합 감사 + 수정 플랜 — 다음 세션 최우선 진입점** |
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
| `memory/plan_pickup_driver_app.md` | 픽업기사앱 구 기획 (3/17 승인) — Phase 2 참조용 |
| `memory/pickup_app_phase2_plan.md` | 픽업기사앱 Phase 2 로드맵 (MVP 완성 후 확장 플랜, 2026-04-24) |
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

## 사용자 프로필
- [iOS 경험](memory/user_ios_experience.md) — App Store 출시 무경험, Android는 숙련. iOS 용어는 기초부터 + Android 비유
- [Apple iOS 식별자](memory/apple_ios_ids.md) — Team ID `VCJD377MAU`, Bundle ID `com.designated.driverapp.app`

## P0 진행 상태 + Phase 6 체크리스트
- 🎯 [전략 피벗 — 병렬 낚싯대 ⭐⭐⭐](memory/strategy_pivot_2026-04-21.md) — **2026-04-21 첫 영업 후 확정**. Phase 6·손님앱 MVP 재작성 **모두 보류**. Tier 1 랜딩 2종(필라테스·미용실) 우선
- ✅ [H2 사장님 가입/로그인 재설계](memory/h2_invite_signup_2026-04-20.md) — 2026-04-20 코드 완료, 배포 대기 (IAM signBlob 선행). 관련: RESOLVED `memory/call_detector_manager_oauth_gap_2026-04-20.md`, SUPERSEDES `memory/owner_portal_setup_2026-04-19.md`
- 🚨 [MVP Kotlin 매핑 불일치 감사 ⭐⭐](memory/customer_app_auth_mismatch_audit_2026-04-19.md) — **2026-04-19 긴급 발견**. Flutter 진행 전 MVP 재작성 선행 필수
- [Phase 6 남은 코딩 ⭐](memory/phase6_coding_remaining.md) — iOS 출시 전 7개 항목 체크리스트
- [Phase 6 Day 4 Phase B RFC ⭐](memory/phase6_day4_phaseB_rfc.md) — 아이폰 도착 시 즉시 착수 가능한 5건 수정 스니펫 + Entitlement + Codemagic Phase B yaml
- [P0 재검증 2026-04-17](memory/p0_status_2026-04-17.md) — MVP 매핑 스펙 문서(.md) 패치 현황. 런타임 영향 없음, Phase 6 진입 시 실제 코드에 반영 필요

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
- [rejectedByDriver dead data](memory/rejected_by_driver_dead_data.md) — 기사앱 write 3곳 / 모든 consumer read 0 / 재배차 UI 필터링 누락 (Phase 6 ①에서 집계 소비처 생김, UI 필터링은 후속)
- 복구 스크립트: `functions/scripts/restoreAll.js`
- 지역 초기화: `functions/scripts/initProvinces.ts`

# 클로드코드 → Cowork Claude 업무 보고 (2026-04-26)

작성자: 클로드코드 (CTO/개발팀장 역할)
대상: Cowork Claude (COO/총괄)
브랜치: `manager-direct-drive` @ `aed3e17e`
3개월 commit 수: **249건** (2026-01-26 ~ 2026-04-26)

> 형식 원칙 준수: 사실(F)·평가(E)·추측(P) 분리. 추측은 "추측:" 라벨.

---

## 1. 최근 3개월 작업 history

### 1-1. 시기별 큰 흐름

| 기간 | 핵심 작업 | 커밋 대표 |
|------|-----------|-----------|
| 2026-01말 ~ 2월 | **정산 시스템 재설계** (calls 기반 통합, carryOver 도입) | `f130a4d7` 이월 정산 / `2dad24a2` 재설계 |
| 2026-02 ~ 03 | **ACK + Presence 시스템 Phase 1~2** (FCM 도착 확인 / Realtime DB) | `ef8fcec9` / `36e06ed6` |
| 2026-03 ~ 04 초 | **마감·이체·외상 통합** + 정산 UI 통합, FCM 화면깨움, 네트워크 가드 등 잔버그 수렴 | `b4268dc4` 정산 UI 통합 / `e59b9edc` FCM 화면깨움 |
| 2026-04-15 ~ 17 | **iOS 포팅 준비**(`ios/` 폴더 분석 문서 대량) + carryOver 중복 합산 fix | `607c6bb7` carryOver fix |
| 2026-04-17 ~ 19 | **Phase 6 (iOS 출시 트랙)** 본격화: driver_app_flutter Day1~4 + 1차 검증, customer_app_flutter Week0~3 | `745070fa` ~ `177ea310` 다수 |
| 2026-04-19 ~ 20 | **Flutter↔Kotlin 매핑 감사** + customer_app_flutter Firebase 구성 | `4183357e` 매핑 감사 |
| 2026-04-20 ~ 21 | **H2 사장님 포털 + APK 다운로드 인프라** + 4월 21일 박상준/양평 첫 대면 영업 | `8cb07470` owner portal |
| 2026-04-22 | 쿠폰앱 기획안 작성 (코드 X) | (untracked `쿠폰앱/`) |
| 2026-04-23 | **메인 홈페이지 UX 개편** (토글 검색·대형 추가 버튼) + Auth-gate 작업 시작 | `300efb68` |
| 2026-04-24 | **픽업기사앱 MVP 스캐폴딩** + **배차 STT 메모 파싱** (콜매니저·디텍터 양쪽 포팅) | `58428830` / `f9d0f0d0` / `ae89b970` |
| 2026-04-25 ~ 26 | **OS 재구조화** (마스터 v1.1 + 메모리 도메인 폴더) + 4앱 폰트 스케일 1.0 고정 + 손님앱→식당업소앱 변환 결정 | `dbbb70b7` / `37ae7d5a` / `aed3e17e` |

### 1-2. 큰 의사결정의 맥락

**(F) 브랜치를 `manager-direct-drive`로 옮긴 이유**
- 원래 `firestore-migration-backup`에서 작업했지만, 2026년 초 "관리자 직접운행" 기능(기사 없을 때 관리자가 콜 처리 + 정산)이 추가되면서 분기.
- 시뮬레이션 결과는 `.agent-teams/simulation/manager-direct-drive-result.md`에 정리됨 (격리/합류/정합성 3축 PASS).
- 이후 모든 후속 작업(Phase 6, 픽업앱, STT, 홈페이지, owner portal, OS 재구조화)이 이 브랜치 위에 누적.
- 결과적으로 `manager-direct-drive`가 사실상의 trunk가 됨. master와는 **341 커밋** 격차.
- **(E) 문제**: CLAUDE.md "개발 환경" 섹션은 여전히 `firestore-migration-backup`이라 적혀 있음 → 정정 필요(아직 수정 보류, 허락 못 받음).

**(F) 마스터 §10의 6앱 외에 작업한 영역**
- `ios/` (untracked, 2026-04-15~) — Kotlin → Swift 포팅 인벤토리·계획 문서. driver_app/MVP, customer_app/PLAN, REINFORCEMENT 등. Phase 6 일환으로 시작됐다가 보류.
- `customer_app_flutter/` (tracked, Phase 6 ②) — Week 0~3 chunk 4까지 완료. iOS Firebase 구성 마침. 그 후 Flutter 트랙 자체가 보류 → 2026-04-27 식당업소앱 변환 결정.
- `head_manager_web/` (tracked, Next.js) — 총관리자 웹 + 사장님 포털 (Google 로그인 + APK 다운로드 페이지).
- `homepage/` (untracked) — 콜마당 공식 홈페이지 + auth-gate (Basic Auth) — `public/index.html`에 1746줄 이식 작업 진행 중.
- `pickup_driver_app/` (tracked) — driver_app fork. 2026-04-24 MVP 완성.
- `쿠폰앱/` (untracked) — 기획안 v0.1만 존재. 코드 0줄.
- `heart/` (untracked) — 원규씨 개인 회고록 (4-14 술 대화 메모) 1개 파일.

---

## 2. 현재 진행 중

### 2-1. In-flight (커밋 안 된 작업, 26 files / +3156 / -1267)

| 영역 | 파일 | 상태 | 추측/평가 |
|------|------|------|-----------|
| **call_manager 회원가입 흐름** | `LoginViewModel.kt`, `SignUpScreen.kt`, `SignUpViewModel.kt`, `CallDetectorService.kt`, `CallManagerPermissionManager.kt` | 가입/로그인 흐름 + 권한 요청 변경 | (E) H2 사장님 포털과 연계된 신규 가입(registerOwner) 플로우로 추측. 구체 의도는 cowork와 미상의. |
| **customer_app_flutter** | `auth_notifier.dart`, `profile_ui_state.dart` + `profile_ui_state.freezed.dart` + 새 `profile/presentation/notifiers/` 폴더 | Profile notifier 신규 + auth 보강 | (F) Phase 6 트랙 — 보류 결정(2026-04-27) 직전까지의 미완. **(E) 식당업소앱 변환 결정에 따라 이 변경은 직접 쓸모없음** — 다만 Freezed/Riverpod 패턴 그대로 재활용 가능. |
| **homepage + auth-gate** | `homepage/public/index.html`, `public/index.html` (+1746줄!), `homepage/auth-gate/`, `functions/src/index.ts`(+express+basicAuth secrets) | 홈페이지 본문 대량 이식 + 인증 게이트 작성 | (F) Cloud Functions에 `HOMEPAGE_USER`/`HOMEPAGE_PASS` Secret 정의 + express 라우팅 추가. **(E) 가장 큰 미커밋 덩어리. 이게 안 들어가면 다른 작업 cherry-pick도 어려움.** |
| **head_manager_web 사장님 로그인** | `owner/layout.tsx`, `owner/login/page.tsx`, `applications/detail/page.tsx`, `login/page.tsx` | UX 보강 | (E) 사장님 포털 첫 영업 후 피드백 반영으로 추측. |
| **functions/src/index.ts** | `memoText` 필드 + 공유콜 spread + approveOfficeApplication H2 주석 | 부수적 보강 | (F) 동작 변경 없는 주석/필드. 안전. |
| **firestore.rules / firebase.json** | 변경 있음 | 미확인 | (E) homepage 호스팅 + auth-gate 라우팅 관련 추측. |

**(E) 평가 — in-flight의 정직한 모습**:
- 서로 다른 4개 트랙(가입 흐름·Flutter·homepage·사장님 웹)이 같은 워킹 카피에 쌓였다. 한 commit으로 묶기엔 의미가 다 다름.
- 정상 흐름이라면 트랙별로 분리 commit해야 했지만, 영업·UI·OS 재구조화가 동시에 들어오면서 정리 못 한 채 쌓였다. **빚.**

### 2-2. 다음 commit 후보
1. **homepage + auth-gate + functions express** — 한 묶음 (내용·의존 관계 일치)
2. **call_manager 가입 흐름** — H2 owner 가입 플로우. 별도 commit
3. **head_manager_web owner UI 패치** — 별도 commit
4. **customer_app_flutter** — 식당업소앱 변환 결정으로 **버려짐**. 보류 폴더로 이동 또는 그대로 두고 stash 권장(추측).

### 2-3. 막혀 있는 것
- (F) 막힘 없음. 단, **위 4트랙을 commit하기 전에 cowork claude의 정리 방향 결정이 필요**. 임의로 묶었다 풀기 비용 큼.

---

## 3. 알려진 이슈·기술부채

### 3-1. 알려진 버그
- **AND-01 ~ AND-06** (driver_app, `memory/designated_drive/pending-work.md` 참조) — Phase 6 iOS 복귀 시 처리 예정. AND-02 (PendingSync 큐)·AND-03 (Presence cleanup) 우선.
- **CallManager 새 기사 INSERT 누락** — pendingDrivers 승인 후 Room DB INSERT 안 됨. `refreshData()` 수동 호출 필요. CLAUDE.md §앱별 데이터 아키텍처에 명시.
- **CF `oncallassigned` 오탐 실행** — 관리자 직접운행에서 `assignedDriverId="MANAGER"` 트리거. 실질 영향 없으나 logger.error 1건/콜 오염. 1줄 가드 추가 보류.

### 3-2. 미완성 기능
- **Phase 6 iOS 출시** — driver_app_flutter Day1~4 + customer_app_flutter Week0~3까지 진행 후 보류. 위치: `memory/designated_drive/ios_phase6_paused/`.
- **Flutter 전환** — 위와 묶임. `memory/designated_drive/flutter_paused/`.
- **대리 손님앱 MVP 재작성** — Anonymous Auth + QR 매칭 핵심 기능 누락 감사 후 보류. 2026-04-27 식당업소앱으로 변환 결정. `memory/designated_drive/customer_app/` + `memory/restaurant/owner_app_pivot_2026-04-27.md`.
- **쿠폰앱** — 기획안 v0.1만. 코드 0줄.
- **택시 v0** — 메모리상 placeholder 단계.

### 3-3. 죽은 코드 후보
- `LockScreenActivity.rejectCallDirectly()` — 거절 버튼 제거됐으나 메서드 잔존 (AND-01).
- `customer_app/` (Kotlin 원본, 미배포) — Flutter로 재작성 시도 후 다시 식당업소앱으로 피벗. 백업본 있음. **(E) 변환 시 어디까지 살릴지 미정.**
- `driver_app_flutter/`, `customer_app_flutter/` — Phase 6 보류로 사실상 활동 정지. 식당업소앱이 Flutter 기반이면 일부 인프라 재활용 가능. 추측: 식당업소앱은 Kotlin 베이스 권장(call_manager fork) — 변환 결정 메모 `owner_app_pivot_2026-04-27.md` 확인 필요.
- `ios/` — 모두 Markdown 문서. Swift 코드 0줄. Phase 6 복귀 시까지 동결.
- google-services.json.bak 3개 — 어떤 시점에서의 설정 백업. 남길지 정리할지 미결정.

### 3-4. 정리 필요한 영역
- **untracked 미커밋 폴더 정리** — `ios/`, `homepage/auth-gate/`, `쿠폰앱/`, `heart/`, `.agent-teams/swift-expert.md`, `.agent-teams/homepage-{design,tech}.md`, `head_manager_web/app/owner/download/`, `functions/scripts/{cleanup,copy-homepage,diagnose-admin,inject-password,reset-test-data}.js`. 각각 의도적인지·추가 작업 필요한지 판단 필요.
- **루트의 옛 문서들** — `CALLMADANG_ANALYSIS_REPORT.md`, `CALLMADANG_ANALYSIS_REPORT_V2.md`, `RESTORATION_GUIDE.md`, `SETTLEMENT_ANALYSIS.md`, `FCM_알림_시스템_분석.md`, `리스너_FCM_전환_마스터플랜.md`, `잠재적_문제점_분석_보고서.md`, `🎯 Firebase Crashlytics 통합 완료!.txt`, `## Firebase 데이터 구조.txt` — 모두 2-4월 분석 산출물. 메모리 OS 도입 후 사실상 deprecated. 정리 또는 `_deprecated/` 이동 권장.
- **screenshot 파일 7개** — `s21_screenshot{,2,3,4}.png`, `s22_screenshot{,2}.png`, `driver_app/screenshots_cropped/`, `driver_app/스크린샷/` — Play Store 등록용으로 추측. 트래킹 미정.

---

## 4. 작업 환경

### 4-1. 현재 브랜치 `manager-direct-drive` 사용 이유
- (F) 위 §1-2에서 설명. 사실상의 trunk. master에 머지된 적 없음(341 커밋 격차).
- (E) **권장**: master로 한 번 큰 머지 정리 + 향후는 manager-direct-drive를 master로 만들거나(rename), 신규 브랜치 끊고 manager-direct-drive를 deprecate.

### 4-2. 어마어마한 unstaged 변경사항
- (F) 26 files / +3156 / -1267. 4개 트랙이 섞임 (§2-1 표 참조).
- (E) **intentional 진행 중 작업 60% + 미정리 40%** 추측. 가장 큰 덩어리는 homepage(+1746줄) — 실작업물.
- (E) 권장: 트랙별 분리 commit 4건으로 풀어서 push. 식당업소앱 변환 후 customer_app_flutter는 stash 또는 폐기.

### 4-3. 마스터에 안 잡힌 폴더 — 각각의 정체

| 폴더 | 무엇 | 살아있는가 |
|------|------|------------|
| `ios/` (driver_app/MVP, customer_app, FUNCTIONAL_INVENTORY 등) | Kotlin → Swift 포팅 인벤토리·계획 (MD 전용, Swift 코드 0줄) | **동결** (Phase 6 보류 중). 복귀 트리거: 양평 자기추진 + R3 검증 통과 |
| `customer_app/` (Kotlin) | 원본 손님앱. 미배포 | **deprecated** — 식당업소앱으로 변환 결정 (2026-04-27) |
| `driver_app_flutter/` | Phase 6 ② 포팅 트랙 | **동결** (보류) |
| `customer_app_flutter/` | Phase 6 손님앱 Flutter | **동결** + in-flight 변경 일부 (§2-1) |
| `쿠폰앱/` | 기획안 v0.1만 | **기획만**. 코드 없음 |
| `homepage/` + `homepage/auth-gate/` | 콜마당 공식 홈페이지 + Basic Auth 게이트 | **활성** (in-flight 작업 중) |
| `heart/` | 4-14 술 대화 회고 1개 (개인 메모) | (E) 실코드 X. 원규씨 개인 영역. 손대지 말 것 |
| `head_manager_web/` | 총관리자 웹 + 사장님 포털 (Next.js) | **활성** — 첫 영업 시 사용 |
| `pickup_driver_app/` | 픽업기사 앱 | **활성** — 2026-04-24 MVP 완성, Flip4 운영 |
| `.bak` 파일들 (`google-services.json.bak` 3개) | 어느 시점 설정 백업 | (E) 정리 후보. 의도성 미확인 |

---

## 5. 새 총괄이 꼭 알아야 할 5가지

### 5-1. 손님앱 변환 결정 (2026-04-27, 가장 최근)
- (F) 손님앱(Kotlin+Flutter 양쪽) 보류 → **식당업소앱**으로 변환 결정. `memory/restaurant/owner_app_pivot_2026-04-27.md`.
- (E) **마스터 §10의 6앱 구성도가 이걸로 바뀜**. 6번째 앱이 더 이상 손님앱이 아니라 식당업소앱.
- (E) cowork claude는 이 결정을 메모리에는 반영했으나 CLAUDE.md "앱별 데이터 아키텍처"의 customer_app 섹션은 그대로다 → 정리 필요.

### 5-2. 브랜치명과 CLAUDE.md 불일치
- (F) CLAUDE.md "개발 환경" 표는 `firestore-migration-backup`. 실제는 `manager-direct-drive`.
- (E) 사소해 보이나 신규 세션 진입 시 혼란 유발. 정정 권장.

### 5-3. 시뮬레이션 자료
- (F) `.agent-teams/simulation/` 안에 day-a/b/c, manager-direct-drive 시나리오, PLAYBOOK.md, STATE.md 존재. 7일 335콜 + 100건 동시 경합 시뮬 PASS 기록.
- (E) 신규 기능 검증 표준이 시뮬레이션 → 실기기 테스트 순서. 이 흐름을 cowork claude도 받아들여야 일관성 유지됨. 명령어: "시뮬레이션해줘".

### 5-4. 4앱 폰트 스케일 고정 (2026-04-26, `37ae7d5a`)
- (F) 디바이스 시스템 글꼴 크기 변동에도 4앱 텍스트 일관성 유지를 위해 fontScale=1.0 강제.
- (E) call_detector / call_manager / driver_app / pickup_driver_app 4개 모두 적용. 추후 신규 앱(식당업소앱 등) 추가 시 동일 패턴 의무화.

### 5-5. STT 메모 (2026-04-24)
- (F) `f9d0f0d0` 콜매니저 STT 파싱 → `ae89b970` 콜디텍터 동형 포팅 → `a2071f21` Phase B Kiwi 데이터 흡수.
- (E) 양평 사장님 박상준의 "받아쓰기 빨리 되면 좋겠다" 피드백 직접 반영. **현장 피드백 → 코드 = 1주 이내** 사례. cowork claude도 이 사이클 인지 필요.

---

## 6. 새 총괄에게 권하고 싶은 것

### 6-1. 우선순위 (지금 다음 손볼 곳) — **권장**

1. **in-flight unstaged 정리** — 4개 트랙 분리 commit (homepage / call_manager 가입 / head_manager_web / customer_app_flutter 폐기 결정). **이게 안 풀리면 다음 작업 시 conflict 위험.**
2. **CLAUDE.md 동기화** — 브랜치명, 6번째 앱(식당업소앱), customer_app 섹션 정리.
3. **식당업소앱 출범 준비** — 마스터 §13.3 R3·R4의 직접 의존 무기. 0순위 신규 코드.
4. **homepage + auth-gate 라이브** — 첫 영업 도구. (E) 4-21 첫 영업이 1회로 끝났으니, 다음 영업 사이클 전 안정화 필요.

### 6-2. 효율적 협업 방식 (Cowork-클코 분담)

**권장 분담**:
- **Cowork Claude (총괄/COO)**: 메모리 OS 관리, 마스터·체크리스트 갱신, 우선순위 의사결정, 영업·전략 회고 정리, 분석가 4명 활용 지시.
- **클로드코드 (CTO)**: 코드 수정·빌드·테스트·commit·push, 시뮬레이션 실행, 실기기 테스트, 버그 추적, in-flight 정리.
- **인터페이스**: `.agent-teams/bridge/` 폴더에서 briefings ↔ results 비동기 교환. 본 보고서가 첫 사례.

**(E) 한 가지 주의**: cowork claude가 코드 영역까지 직접 손댈 경우(예: in-flight 정리, CLAUDE.md 자동 수정) 클코의 commit과 충돌 가능. **코드 변경은 클코로, 메모리·문서는 cowork로** 명확한 경계가 필요.

### 6-3. 분석가 4명(detector/manager/driver/firebase) 활용

**(F) 현재 상태**: `.agent-teams/`에 4명 컨텍스트 파일 + flutter-expert, kotlin-expert, swift-expert, customer-analyst, settlement-simulation 등 잠자는 분석가 다수.
**(E) 평가**:
- 4명은 각자 자기 앱 코드 맵을 가장 잘 안다 → **버그 분석 1차 진단**에 적합.
- 단, "임무 완료 후 다음 지시 대기" 규칙 + "팀 해체 명시까지 유지" 규칙으로 실행 비용이 있음.
- 가장 효율적: cowork claude가 **버그/이슈 보고를 받으면 일단 분석가에게 진단 요청** → 진단 결과를 받아 코드 작업 지시서 작성 → 클로드코드 실행. 즉 cowork가 가운데서 라우팅.
- (E) 비추: 신규 기능 설계는 분석가가 잘 못함(앱 안만 봄). 신규는 cowork + 클코 직접 협의 권장.

---

## 부록: 정직한 회고

### 잘된 것
- 정산 시스템 carryOver 중복 fix (4-23) — 단일 commit으로 핵심 정확성 확보.
- 픽업기사앱 MVP를 driver_app fork + 1commit 스캐폴딩으로 단기 완성.
- STT 메모 콜매니저↔디텍터 동형 포팅 + Kiwi 데이터 Phase B로 점진 강화 — 현장 피드백 → 코드 사이클 빠름.
- OS 재구조화 (마스터 v1.1 + 도메인 폴더) — cowork claude의 결정. 클코는 실행만.

### 못한 것 / 정직하게 헷갈리는 것
- **in-flight 26 files를 commit 안 하고 끌고 있음**. 사이즈가 커지면서 분리 비용이 점점 올라가는 중. **빚.**
- **CLAUDE.md 브랜치명 불일치**를 알면서도 정정 안 함 — 허락 룰 + 우선순위 미정으로 보류.
- Phase 6 (Flutter, iOS) 트랙 보류 결정 후 폴더 정리 안 함. customer_app_flutter는 식당업소앱 변환 결정으로 사실상 폐기 라인이지만, in-flight 변경은 여전히 워킹 카피에 있음.
- 손님앱 → 식당업소앱 변환 결정의 코드 영향(Kotlin 베이스인지 Flutter 베이스인지)을 아직 확인 못 함. 메모(`owner_app_pivot_2026-04-27.md`)를 새 세션에서 정독 필요.

### 추측이라고 명시
- (P) **homepage in-flight 작업의 최종 목표** — 단순 컨텐츠 이식인지, auth-gate를 통한 사장님 다운로드 페이지로의 이행인지 명확히 못 봄. cowork claude 확인 필요.
- (P) **call_manager 가입 흐름 변경**의 정확한 의도 — H2 사장님 포털과 연계로 추측하나 RegisterOwner 화면 흐름이 customer 가입과 어떻게 분기하는지 코드만으론 확신 못 함.
- (P) **쿠폰앱 우선순위** — 기획안 v0.1만 있고 마스터 §10은 T2 placeholder. 식당업소앱(1순위) 다음 순서로 추측.

---

**보고 끝.** Cowork Claude의 통합 + 다음 지시(Step 2: Founder Discovery, Step 3: Operating Model) 대기.

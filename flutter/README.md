# Flutter 전환 전략 (2026-04-16 확정)

## 최종 아키텍처

| 앱 | Android | iOS | 귀속 방식 |
|---|---|---|---|
| **기사앱** | Flutter (Phase 1~5 기구현) | Flutter 단독 출시 | — (App Clip 불필요) |
| **손님앱 Phase 1** | Flutter (신규 포팅) | Flutter (신규 포팅) | 수동 사무실 코드 입력 |
| **손님앱 Phase 2** | Flutter (그대로) | Flutter + Swift App Clip 하이브리드 | App Clip QR (Mac 도착 후) |

---

## 결정 배경

### 왜 Swift 네이티브가 아닌 Flutter인가 (2026-04-16 재결정)

**이전 결정 변경**: `ios/README.md`(2026-04-15)는 "기사앱·손님앱 모두 Swift 네이티브 확정"이었으나, **Mac 공수에 1~2개월 소요**되는 상황에서 iOS 출시를 더 이상 늦출 수 없어 Flutter 전환으로 재조정.

**핵심 근거**:

1. **Mac 없이 iOS 정식 출시 가능** — Codemagic CI로 5~7주 안에 TestFlight + App Store 정식 출시 완결. Mac 대기 2개월보다 빠름.
2. **Flutter 기사앱 Phase 1~5 이미 완료** — `driver_app_flutter/` 검증된 코드 재활용. Swift 재작성 시 이 자산 폐기.
3. **iOS 플랫폼 제약은 언어 무관** — LockScreen 풀스크린 / Foreground Service 영구 / BootReceiver는 Swift로도 동일 불가. 기능 타협 없음.
4. **단일 코드베이스 회복** — Android/iOS Flutter 통합으로 단독 개발자 유지 부담 최소화.
5. **Swift 학습 범위는 App Clip 한정** — 손님앱 Phase 2에서 ~500줄, Mac 도착 후 진행. 전체 학습 부담 회피.

### Swift 계획(`ios/`)의 처리

**보류이지 폐기 아님**. Flutter iOS MVP가 심각한 문제(App Store 리젝, FCM 도달 실패 등)에 봉착하면 Swift 네이티브로 복귀 가능. `ios/` 디렉토리의 매핑 문서 8개 + REINFORCEMENT TRIGGERS는 자산으로 보존.

### 로직 참조 소스

| 소스 | 경로 | 용도 |
|------|------|------|
| Kotlin 기사앱 | `driver_app/app/src/main/java/com/designated/driverapp/` | 1차 참조 (검증된 프로덕션 코드) |
| Flutter 기사앱 | `driver_app_flutter/lib/` | **현재 구현** — Phase 6 iOS 빌드 진입 |
| Kotlin 손님앱 | `customer_app/app/src/main/java/com/designated/customer/` | 손님앱 Flutter 포팅 원본 |
| Cloud Functions | `functions/` | 서버 로직 (41개 CF, 플랫폼 무관) |
| ios/SHARED_LOGIC.md | 프로젝트 루트 | 언어 독립 명세 (Firestore, 상태, 정산, FCM) |
| CLAUDE.md | 프로젝트 루트 | 프로젝트 전체 구조 |

---

## 공통 선행 조건 (Mac 없이도 가능)

- [ ] Apple Developer Program 등록 ($99/year) — Windows에서 가능
- [ ] **Codemagic 계정** (무료 티어 월 500분) — GitHub 연동
- [ ] App Store Connect 앱 신규 생성 — 웹 UI
- [ ] Bundle ID 확정 (의제 2 결정 후)
- [ ] APNs Authentication Key (.p8) — Apple Developer 웹에서 발급
- [ ] Firebase iOS 앱 등록 (Firebase Console)
- [ ] 기사 테스터 5~10명 Apple ID 수집 (TestFlight Internal)

**Mac 도착 후 추가**:
- [ ] Xcode 최신 버전 설치
- [ ] iPhone 실기기 1대 (App Clip 디버깅용)
- [ ] Associated Domains 설정 (App Clip AASA)

---

## Mac 확보 방침 (변경)

이전 `ios/README.md`(2026-04-15) "Mac mini M1+ 구입 확정, 클라우드 배제"는 **Phase 2 App Clip 작업에만 적용**.

Phase 1 (기사앱 + 손님앱 Flutter 출시)는 Mac 없이 Codemagic으로 완결. **Mac 도착 시점 = Phase 2 App Clip 착수 시점**.

| 옵션 | 가격대 | 권장도 |
|------|-------|-------|
| Mac mini M4 16GB 신품 (교육할인) | 85~95만원 | ★★★ 최장수명 |
| Mac mini M2 중고 | 55~70만원 | ★★☆ 균형 |
| Mac mini M1 중고 8GB/256GB | 40~50만원 | ★★☆ 최소 예산 |
| MacinCloud Xcode 플랜 | $50/월 | ★★☆ 단기 보강 (Phase 2까지) |
| Intel Mac mini | — | ✗ 금지 |

---

## 타임라인 (Mac 없이 긴급 출시 기준)

상세는 `bubbly-cuddling-hopcroft.md` §9 참조.

- **Week 0~2**: Codemagic 설정 + 팀 의제 논의
- **Week 2~4**: 매핑 문서 + Phase 6 iOS 빌드 smoke test
- **Week 4~5**: TestFlight Internal 파일럿
- **Week 5~7**: External 베타 + App Store 심사 + Release
- **Mac 도착 후 +1~3주**: Phase 2 App Clip Swift 추가

---

## 폴더 구조

```
flutter/
├── README.md                       ← 이 파일 (전체 전략)
├── SHARED_LOGIC.md                 ← ios/SHARED_LOGIC.md 포인터 (단일 원본)
├── FUNCTIONAL_INVENTORY.md         ← 3컬럼(Android/iOS/Flutter 매핑) + 기사앱·손님앱
├── WORKING_DOC.md                  ← 의제 14개 + 누적 결정
├── driver_app/
│   ├── PLAN.md                     ← Flutter 단독 iOS Phase 1 MVP
│   ├── MVP/                        ← 팀 논의 후 매핑 문서 작성
│   └── REINFORCEMENT/
│       └── TRIGGERS.md             ← R1/R2 발동 기준
└── customer_app/
    ├── PLAN.md                     ← Phase 1 Flutter + Phase 2 App Clip Swift
    ├── MVP/                        ← 팀 논의 후 매핑 문서 작성
    └── REINFORCEMENT/
        └── TRIGGERS.md
```

---

## 참고 문서

- `bubbly-cuddling-hopcroft.md` — 본 전환의 마스터 플랜 (`C:\Users\kala1\.claude\plans\`)
- `ios/SHARED_LOGIC.md` — 언어 독립 명세 (재활용)
- `ios/FUNCTIONAL_INVENTORY.md` — B/R1/R2/D 분류 근거 (3컬럼 재구성 시 iOS 컬럼으로 활용)
- `memory/plan_flutter_driver_app.md` — Flutter Phase 1~5 구현 이력

# iOS 앱 개발 전략 (2026-03-27 확정)

## 최종 아키텍처

| 앱 | Android | iOS | 귀속 방식 |
|---|---|---|---|
| **기사앱** | Kotlin (기존 유지) | Swift (신규) | — |
| **손님앱** | Kotlin (기존 유지) | Swift + App Clip (신규) | App Clip (QR → 즉시 실행) |

## 결정 배경

### 왜 Flutter가 아닌 네이티브인가

1. **검증된 코드를 버리지 않는다** — Kotlin 기사앱은 95% 완성, 실기기 테스트 완료, 정산/배차/FCM 모든 엣지케이스 처리됨. Flutter 전환은 90%의 Android 기사를 미검증 코드로 옮기는 리스크.
2. **10%를 위해 100%를 바꾸지 않는다** — iPhone 기사는 소수. Swift iOS 앱만 새로 만들면 Android 기사에게 영향 없음.
3. **플랫폼 기능 100% 접근** — LockScreen, Foreground Service, Presence 등 네이티브가 우회 없이 직접 구현 가능.
4. **App Clip은 네이티브 전용** — iOS 손님앱의 핵심 기능(QR → 즉시 사용)은 Swift에서만 자연스러움.
5. **언어가 아닌 로직이 핵심** — Kotlin/Swift/Flutter 모두 같은 Firestore, 같은 트랜잭션, 같은 FCM, 같은 정산 공식을 호출. 로직은 이미 완성됨.

### 기사앱 iOS = Swift 확정 (2026-04-15)

재검토 후 기사앱 iOS도 **Swift 네이티브로 확정**. Flutter 재고 불가.

**결정적 근거**: Flutter의 유일한 가치는 "Android + iOS 단일 코드베이스"인데, 본 프로젝트는 Android 기사앱을 **Kotlin으로 유지**하기로 확정됨. 따라서 iOS를 Flutter로 가면:
- Android=Kotlin, iOS=Flutter 교차 유지 → Flutter의 "한 코드" 이점 0
- **Kotlin + Flutter + Swift 3개 스택**을 단독 개발자가 유지해야 함 (손님앱 iOS는 Swift 확정이므로)

Swift로 가면:
- Android=Kotlin, iOS=Swift → 깔끔한 2스택
- 기사앱 iOS ↔ 손님앱 iOS 간 지식/도구/인증서/빌드 파이프라인 **완전 공유**
- Mac 구입이 확정됐으므로 Swift 진입장벽(유일한 단점) 해소
- CallKit/VoIP Push/Live Activities 등 iOS 전용 기능 공식·안정적

**Flutter 기사앱(`driver_app_flutter/` Phase 1~5) 처리**: 폐기 아님. Swift 포팅 시 **로직 참조 문서**로 활용 (순수 함수 추출본이 가장 정제된 형태). `driver_app/PLAN.md`의 "로직 참조" 섹션에 이미 반영됨.

### 로직 참조 소스

| 소스 | 경로 | 용도 |
|------|------|------|
| Kotlin 기사앱 | `driver_app/app/src/main/java/com/designated/driverapp/` | 1차 참조 (검증된 프로덕션 코드) |
| Flutter 기사앱 | `driver_app_flutter/lib/` | 2차 참조 (로직 정리본, 순수 함수 추출됨) |
| Kotlin 손님앱 | `customer_app/app/src/main/java/com/designated/customer/` | 손님앱 참조 |
| Cloud Functions | `functions/` | 서버 로직 (41개 CF) |
| CLAUDE.md | 프로젝트 루트 | 데이터 구조 + 상태 전이 + 정산 공식 |

## 공통 선행 조건

- [ ] Apple Developer 계정 ($99/year)
- [ ] **Mac mini (Apple Silicon M1 이상) 확보** — Intel Mac 및 클라우드 Mac 루트는 배제 (아래 "Mac 확보 방침" 참조)
- [ ] Xcode 최신 버전
- [ ] iPhone 실기기 1대 (CallKit/VoIP Push 테스트용, 중고 SE도 가능)
- [ ] APNs 인증서 (Push Notification)
- [ ] Associated Domains 설정 (App Clip용 AASA 파일)
- [ ] Firebase iOS 앱 등록 (Firebase Console)
  - 기사앱: `com.designated.driver.ios` (가칭)
  - 손님앱: `com.designated.customer.ios` (가칭)
  - 손님앱 App Clip: `com.designated.customer.ios.Clip`

## Mac 확보 방침 (2026-04-15 확정)

App Store **정식 출시 + 장기 유지보수가 목표**이므로 클라우드 Mac / CI 전용 루트는 배제.

### 배제 사유
- iOS 시뮬레이터 개발 불가 → 매 빌드 15분 대기, 개발 속도 치명적
- CallKit/VoIP Push는 Xcode 실기기 디버깅 필수 → 클라우드로 불가능
- 심사 리젝 당일 대응 어려움 (CI는 하루 단위로 느려짐)
- 1년 누적 비용이 중고 Mac 가격을 넘어섬 + 자산이 남지 않음
- Intel Mac은 Xcode/macOS 지원 종료 임박 → 구매 금지

### 권장 구성
| 옵션 | 가격대 | 권장도 |
|------|-------|-------|
| Mac mini M4 16GB 신품 (교육할인) | 85~95만원 | ★★★ 최장수명 |
| Mac mini M2 중고 | 55~70만원 | ★★☆ 균형 |
| Mac mini M1 중고 8GB/256GB | 40~50만원 | ★★☆ 최소 예산 |
| Intel Mac mini (연식 무관) | — | ✗ 금지 |

## 타임라인

- Android 3대 마일스톤 (최종 테스트 → 명함+홈페이지 → Play Store)과 **병렬 진행**
- Windows = Android/CF/홈페이지 개발, Mac = iOS 빌드/심사 전용으로 역할 분리

## 폴더 구조

```
ios/
├── README.md              ← 이 파일 (전체 전략)
├── SHARED_LOGIC.md        ← 공통 로직 명세 (Firestore, 상태전이, 정산, FCM)
├── driver_app/
│   └── PLAN.md            ← iOS 기사앱 (Swift) 계획
└── customer_app/
    └── PLAN.md            ← iOS 손님앱 (Swift + App Clip) 계획
```

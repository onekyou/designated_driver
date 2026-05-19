# 솔로 트랙 조사 — 1인 기사 통합 단말 (2026-05-19)

> ⚠️ **5/19 본인 결정으로 트랙 자체 동결**: `memory/coupon/pivot_2026-05-19.md`
> 6개월 후 쿠폰앱 PoC 결과에 따라 부활/폐기. 본 조사 결과는 그때 인용 자산.

## 조사 목적 (당시)

시스템 이원화 검토:
- **사무실 운영 그룹**: 기존 5앱 MVP 유지 (call_detector / call_manager / driver_app / pickup_driver_app / customer_app)
- **1인 그룹** (2인1조 기사+픽업): **콜매니저+기사앱 통합 단일 단말** 별도 제공

## A. 외부 조사 핵심 — 로지·카카오

### 시장 위치
| | 카카오T 대리 | 로지(소프트) |
|---|---|---|
| 종합 점유율 | 25~50% (앱콜 시장 99%) | 40~70% (전화콜 시장 70~90%) |
| 1인 기사 가입 | **정식 지원**, 초기비용 0원 | **불가** (사무실 가입 의무) |
| 수수료 | 운임 20% 원천징수 | 20% + 월 9만(보험) + 월 1.5만(프로그램) + 가입비/충전금 |
| 콜 분배 | 본사 직배 알고리즘 + 등급제(그린→블루→레드→퍼플) | 다중 사무실 공유콜 풀 |
| 단일 단말? | ✅ 콜수신·운행·정산 1앱 종결 | ✅ (사무실 매개) |
| 픽업 정식 모드 | ❌ (부부 2인1조 비공식) | ❌ |
| 본인콜 등록 | ❌ | ❌ |
| 멀티호밍 | 표준 관행 (화면분할 동시 구동) | 동일 |

### 핵심 관찰
1. **시장 표준이 이미 단일 단말** — 1인 기사 통합 단말은 진입 자격, 혁신이 아님
2. **멀티호밍 표준** — lock-in 시장 아님, 콜 풀 보강 시장
3. **카카오 콜마너 인수 (2019~2020)** — "카카오 vs 로지"가 아닌 "카카오 연합 vs 로지 연합" 2강 구도
4. **법적 인정** — 픽업기사 특수형태근로종사자 판결(2023) 있으나 플랫폼은 정식 분리 안 함

## B. 우리 차별점 3개 (시장 표준에 없는 자리)

| 차별점 | 시장 표준 | 콜마당 |
|---|---|---|
| **수수료 모델** | 20% 원천징수 | **구독료(월정액)만, 콜 무수수료** |
| **본인콜 등록** | 양쪽 모두 외부거래 (보험·세금 기사 개인 책임) | **앱 정식 등록 + 보험 자동 적용 + 통계** |
| **픽업 단말 정식** | 비공식 부부 운영 | **2인1조 페어링 + 픽업 콜 라우팅 + 수익 5:5 자동 분배** |

### 매출 시뮬레이션
월 매출 360만원 (월 25일 × 8콜 × 평균 18,000원) 기준:
- 카카오 순수입: 288만 (수수료 -72만)
- 로지 순수입: 약 275만 (수수료+프로그램비+보험료)
- **콜마당 (월 20만 구독료 가정) 순수입: 340만** → 카카오 대비 +52만/월

## C. 내부 코드 조사 — call_manager + driver_app 통합 가능성

### 호환성 매트릭스
| 항목 | 결론 |
|---|---|
| compileSdk / minSdk / Compose 버전 | **완벽 일치** |
| 정산 모델 (SettlementModels) | **양 앱 동일 스펙** (의도적 공유) |
| 채팅 모델 (LocalChatMessage / ChatViewModel) | **완벽 호환** |
| DriverStatus enum | 거의 동일 (driver_app에 ACCEPTED/PENDING_CONFIRM 추가) |
| CallInfo | driver_app만 존재 → 통합앱은 driver_app 모델 채용 |

### 통일 필요 항목
| 항목 | call_manager | driver_app | 통합 전략 |
|---|---|---|---|
| DI Framework | 수동 (DatabaseProvider) | Hilt | **Hilt로 통일** (call_manager 마이그레이션) |
| Navigation | sealed class Screen | AppNavigation object | 통합 object 신규 |
| Room DB | CallManagerDatabase v7 | SettlementDatabase + ChatAppDatabase | **UnifiedAppDatabase v1 신규** |
| SharedPreferences | `"login_prefs"` | `Constants.PREFS_NAME` (`"app_prefs"`) | **app_prefs 통일** |
| FCM 토큰 | admins/{uid} (Firestore 미저장) | designated_drivers/{uid}.fcmToken | **양쪽 저장** |

### 사무실 경로 결단 (가장 중요)
- 현재: `provinces/{p}/cities/{c}/offices/{o}/calls/{callId}` (사무실 필수)
- 1인 기사 = 사무실 없음 → **두 가지 안**
  - **안 1 (권장)**: `offices/SOLO_{provinceCode}/` 가상사무실 1개 → 기존 콜/정산 로직 그대로 재사용
  - 안 2: `solo_drivers/{driverId}` 신규 컬렉션 → rules·functions 광범위 수정 (비권장)

### 픽업 통합
- **권장: 별도 앱 유지** (pickup_driver_app)
- 1인 그룹도 결국 2단말 (통합앱 + pickup_driver_app)
- 코드 재사용 + 역할 명확

### 통합 공수 추정
- Hilt 도입 1주
- Database 통합 2주
- UI 통합 3주
- FCM/rules 1주
- 테스트/마이그레이션 2주
- **합계: 약 9주**

## D. 위험 3가지

1. **콜 풀 부족** — 카카오·로지 콜 풀이 압도적
   - 완화: 멀티호밍 정식 허용 (우리 앱 + 카카오 동시 구동 가능 명시)
   - 양평 1곳 데이터로 검증 후 확장
2. **보험 처리 미정** — 카카오는 본사 부담, 우리는 미정
   - 완화: 외부 보험사 직접 계약 + 운행시작 트리거 정합 / KB 건당 대리보험 자동 갱신 모델 검토
3. **타깃 정확도** — 카카오 퍼플 등급 lock-in 풀기 어려움
   - 완화: 타깃 = **신규 진입 기사 + 사무실 노예화 거부하는 시니어 + 양평 2인1조 부부**

## E. 진입 권장 (당시 5/19 본인 결정 직전)

3가지 가설 1~2주 진단 후 9주 코드 통합:
1. **SOLO 가상사무실 경로 설계** — `offices/SOLO_{provinceCode}/` 1개 vs `solo_drivers` 신규 — Cloud Functions 영향 범위 1주 측정
2. **콜 풀 가설** — 1인 기사가 양평에서 본인콜만으로 월 25콜 채울 수 있는가 (= 식당 4중 노드가 콜 진입로가 될 수 있는가)
3. **구독료 책정** — 카카오 break-even 월 10~20만원 / 보험비 별도 협의 — 1인 기사 인터뷰 3건으로 wtp 검증

> 위 권장은 5/19 본인 결정(콜마당 휴면 + 쿠폰앱 우선)으로 **동결**.
> 6개월 후 쿠폰앱 PoC 결과 + 콜마당 → 쿠폰앱 API 모듈 흡수 시점에 재검토.

## 출처 (외부 조사)

- [디지털투데이 카카오·로지 시장 분석](https://www.digitaltoday.co.kr/news/articleView.html?idxno=245775)
- [국민일보 앱시장 99% 카카오](https://www.kmib.co.kr/article/view.asp?arcid=0924268815)
- [민들레 대리기사 이중부담](https://www.mindlenews.com/news/articleView.html?idxno=9262)
- [민들레 등급제 명칭 문제](https://www.mindlenews.com/news/articleView.html?idxno=9402)
- [카카오모빌리티 기사 모집](https://service.kakaomobility.com/driver/recruiting/)
- [카카오T 대리 초보 5단계 매뉴얼](https://driver.kakao.com/notices/674)
- [카카오 기사 보상제](https://driver.kakao.com/d/driver_reward)
- [가온기사 부부 2인1조 수입 분석](https://www.gaondriver.com/)
- [주차몽 2인1조 운영방법](https://boozamong.com/대리운전-2인-1조/)
- [마중법률 픽업기사 특수형태근로종사자 판결](https://majunglaw.kr/press/대리운전-픽업기사도-특수형태근로종사자%EF%A4%9C-업/)
- [브런치 대리운전 운영 시스템](https://brunch.co.kr/@@2JfR/42)
- [드라이브유 KB 건당 대리보험](https://driveu.co.kr/entry/로지-건당-대리보험-갱신-및-보험료-안내)
- [Google Play 로지소프트대리운전](https://play.google.com/store/apps/details?id=com.logisoft.SmartDual1)
- [Google Play 카카오T 대리 기사용](https://play.google.com/store/apps/details?id=com.kakao.wheel.driver)

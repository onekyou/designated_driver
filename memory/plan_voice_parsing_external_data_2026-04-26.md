---
name: 보이스 파싱 외부 데이터 흡수 보강 플랜
description: 콜매니저/콜디텍터의 STT 메모 파서를 오픈소스 한국어 NLP 데이터·사전·룰셋만 흡수해 강화하는 플랜 (API/라이브러리 통합 0). 2026-04-26 사용자 "해줘" 승인.
type: project
---

# 보이스 파싱 외부 데이터 흡수 보강 — 최종 플랜

> 2026-04-26 사용자 승인 ("해줘"). Phase A 부터 시작 권장. 다른 Phase 는 별도 승인 받고 진행.
>
> **진행 상태 (2026-04-26)**:
> - ✅ **Phase A 완료** — VoiceInputHelper "만+천" 버그 fix + 단위 테스트 28×2 PASS
> - ✅ **Phase B 완료** — Kiwi morphemes.txt (LGPL) 추출 → 출발지 변형(에서/서), 도착지(까지), 종결어미 13개 흡수
> - ✅ **Phase B-ext 완료** — 큐레이션 추가 (우리말샘 방언 까정/꺼정 + 거치다 활용 거쳐/거치 + 격식체 결합 입니다/이에요). Option A 적용으로 사장님(관리자) 발화 부적합 종결어미 제거.
> - 미커밋. APK 미배포.
>
> **🚨 중요 시나리오 발견 (Phase B-ext 도중)**: 콜매니저/콜디텍터의 STT 메모는 **관리자(사장님) 발화** — 손님 발화 아님.
> - 사장님이 손님 통화 후 정보 종합해서 STT 입력
> - 손님 발화 전제 종결어미(가요/갑니다/주세요/갑시다/부탁드립니다/오세요 등) 거의 안 등장
> - 사장님 적합 평서/격식체만 보존: 습니다/어요/에요/네요/예요/거든요/입니다/이에요
> - 향후 Phase 진행 시 이 시나리오 가정으로 룰/테스트 설계 필수
>
> **최종 마커 데이터** (35개 변형 → 16개 사장님 적합):
> - DEPARTURE: 에서, 서 (Kiwi)
> - DESTINATION: 까지 (Kiwi) + 까정, 꺼정 (우리말샘 방언)
> - WAYPOINT: 경유 + 거쳐, 거치 (거치다 VV 활용)
> - SENTENCE_ENDINGS: 8개 (Kiwi 6 + 큐레이션 2)
>
> **유실된 시도**: Phase A 후 Tier 2 추측 변형 plan 작성 → 사용자 정정 후 폐기 → Phase B 로 재정렬. 피드백: `memory/feedback_user_intent_first.md`. Phase B-ext 도중 손님 발화 가정 잘못 → Option A 정리.
>
> **Phase B 산출물**:
> - `functions/scripts/fetch-kiwi-data.sh` — Kiwi GitHub 다운로드
> - `functions/scripts/extract-kiwi-markers.js` — 추출 + 큐레이션 + Option A 화이트리스트
> - `functions/scripts/.kiwi-cache/morphemes.txt` (4.6MB, .gitignore 권장)
> - `call_manager + call_detector/app/src/main/assets/marker_variants.txt` — 감사용
> - `call_manager + call_detector/.../util/CallMemoParser.kt` — Set 기반, 출처 주석 명시
> - `call_manager + call_detector/.../util/CallMemoParserTest.kt` — 27 케이스 (양쪽 sync, 모두 PASS)
>
> **Plan 파일**: `C:\Users\kala1\.claude\plans\twinkly-purring-snowglobe.md` (사용자 승인됨)

## 배경
- 박상준 사장님 첫 영업 (2026-04-21) 후 STT 배차 메모 파싱 (`call_manager` + `call_detector` 의 `CallMemoParser.kt`) 은 작동 중이지만, 단일 사무실 데이터로 룰을 짜기엔 표본 부족.
- 사용자 결정: **외부 오픈소스 자료를 흡수해 일반화된 보강** 진행.
- 사용자 명시 제외: API 호출, Cloud Functions 신규, 외부 라이브러리 통합(Kiwi 바이너리/soynlp/hanspell 의존). **데이터/사전/룰셋만 우리 코드로 가져옴**.

## 절대 제약
- ❌ Cloud Functions 신규
- ❌ API 호출 (Gemini/Claude/CLOVA/Tmap 등)
- ❌ 외부 라이브러리 통합 (Kiwi 바이너리, soynlp, hanspell 코드 의존)
- ✅ 오픈소스 데이터/사전/룰셋만 → 우리 코틀린 코드/리소스로 흡수
- ✅ 모두 오프라인 동작
- ✅ APK 크기 영향 최소

## 모든 Phase 공통 규칙 (Drift 방지)

`CallMemoParser.kt` + `VoiceInputHelper.kt` 는 **call_manager 와 call_detector 두 앱에 동일 사본**으로 존재. 어떤 변경이든 두 앱 동시 수정 + 두 앱 동시 빌드/테스트가 의무. 한 앱만 수정해서 머지하는 일 없도록 PR/커밋 단위에서 관리.

확인된 사본 위치:
- `call_manager/app/src/main/java/com/designated/callmanager/util/CallMemoParser.kt`
- `call_detector/app/src/main/java/com/designated/calldetector/util/CallMemoParser.kt`
- `call_manager/app/src/main/java/com/designated/callmanager/util/VoiceInputHelper.kt`
- `call_detector/app/src/main/java/com/designated/calldetector/util/VoiceInputHelper.kt`

## 발견된 알려진 버그 (Phase A 의 fix 대상)

`VoiceInputHelper.convertKoreanNumberToDigit` (라인 151-157)
- "이만오천원" 발화 시 한글 숫자 치환 후 "2만5천원" → "만" 으로 split → `restPart="5천원"` → `filter { isDigit() }` 로 천 단위 무시 → `2*10000 + 5 = 20005`
- 결과: **"이만오천원" → 25000 이 아니라 20005 로 저장됨**
- "삼만오천원", "사만이천원" 등 **만+천/백/십 조합 모든 발화가 같은 버그**

---

## Phase 별 세부

### Phase A — 토대 (외부 자료 무관)
1. **숫자 변환 버그 fix**: `VoiceInputHelper.convertKoreanNumberToDigit` 의 "만 + 천/백/십" 조합 처리 수정. 검증: "이만오천원" → 25000.
2. **테스트 인프라 통일**: call_manager 는 settlement 테스트 있음. call_detector 는 ExampleUnitTest 하나뿐 → JUnit 인프라만 셋업 (build.gradle 의 `testImplementation 'junit:junit:4.13.2'` 이미 있음 확인됨).
3. **회귀 테스트 백필**: 두 앱 모두 `CallMemoParserTest.kt` + `VoiceInputHelperTest.kt` 신설. 현재 동작 케이스 15~20개 + 알려진 버그 케이스 5개 잠금.
4. **검증**: 양쪽 앱 `./gradlew test` 통과.
- **작업량**: 4~6시간
- **리스크**: 0
- **외부 자료**: 불필요

### Phase D-init — AI Hub 데이터 신청 (critical path 조기 착수)
- aihub.or.kr 회원가입 + "한국어 콜센터 음성" 데이터셋 신청. 승인 영업일 기준 2~5일.
- 신청만 걸어두고 다른 Phase 병렬 진행.

### Phase E — 도로명주소 사전 흡수 (병렬 가능, A 끝나면 시작)
1. 행안부(`juso.go.kr`) 도로명주소 전체 DB 다운로드.
2. 박상준님 영업지(경기도 양평군) 부분 추출 스크립트 → `functions/scripts/extract-address-dict.js` (재현 가능하게 보존).
3. 추출 결과를 `app/src/main/assets/address_dict_yangpyeong.txt` 임베드 (수십 KB).
4. 사무실 확장 시 다른 시·군 추가 가능한 구조: `assets/address_dict_<region>.txt` + SharedPreferences `current_region` 으로 런타임 선택.
5. `CallMemoParser` 에 도착지 정규화 단계 추가: STT 토큰을 사전과 부분매칭 → "용문" → "양평군 용문면".
6. 검증: 단위 테스트 + 박상준 사장님 발화 샘플.
- **작업량**: 1.5~2일
- **리스크**: 낮음
- **외부 자료**: 행안부 도로명주소 (공공누리)

### Phase F — 한국어 숫자 / STT 후처리 룰 흡수 (병렬 가능)
1. hanspell·soynlp 의 STT 후처리 룰 코드 참고만 (의존성 0).
2. 한국어 숫자 누락 케이스 보강: 고유어("다섯/여섯/일곱"), 수사 모호어("십수만/수백만" 모호 시 폴백 X), 띄어쓰기 변형("2 만 5 천 원").
3. 위키낱말사전 한국어 숫자 페이지 자료 참고.
4. 검증: 숫자 변환 단위 테스트 30~50 케이스 추가.
- **작업량**: 1.5~2일
- **리스크**: 중간 (룰 충돌 가능 — 우선순위 명시)
- **외부 자료**: hanspell·soynlp GitHub (참고만), 위키낱말사전, NIKL

### Phase B — Kiwi 사전 흡수 (조사·어미 변형의 데이터 기반 확장)
1. `bab2min/Kiwi` GitHub 의 `model/` 폴더 사전 파일 분석 (LGPL — 데이터 참고만이므로 코드 의존 없음).
2. 추출 스크립트: `functions/scripts/extract-kiwi-markers.js` — 조사 사전(JKB/JKO/JKS 등)에서 "에서/까지/경유" 의미군 변형 + 빈도 가중치 추출.
3. 우리 룰의 마커 매칭을 `MARKER_VARIANTS: Map<MarkerKind, List<Pair<String, Int>>>` 구조로 일반화. 빈도 상위 N (e.g. 30개) 컷.
4. 짧은 변형 가드 (e.g. 단일 음절 "서") — 토큰 길이/위치 조건.
5. 종결어미 변형 ("갑니다/가요/갈게요/가주세요") → 파싱 전 종결어미 trim 함수.
6. 검증: Phase A 단위 테스트 회귀 통과 + Kiwi 사전에서 추출한 변형 발화 50~100개 새 테스트.
- **작업량**: 2~3일
- **리스크**: 중간 (변형이 너무 많으면 오인식 ↑ → 빈도 컷 + 가드)
- **외부 자료**: Kiwi `model/` (LGPL 데이터)

### Phase C — 모두의 말뭉치 / 방언 사전 흡수
1. NIKL 모두의 말뭉치(`corpus.korean.go.kr`) 회원가입, 구어 말뭉치 + 방언 사전 추출.
2. **상업 사용 라이선스 재확인** (변동성 있음 — 도입 직전 약관 확인 필수).
3. 방언 마커 ("까정/꺼정/거시기" 등) 빈도 추출 → Phase B 의 `MARKER_VARIANTS` 에 누적.
4. 검증: 방언 발화 샘플 테스트.
- **작업량**: 1~1.5일
- **리스크**: 낮음 (B 의 인프라 위에 데이터만 추가)
- **외부 자료**: NIKL 모두의 말뭉치 + 방언 사전

### Phase D — AI Hub 콜센터 발화 검증 (사용자 우려 직접 해결)
1. 데이터 승인되면 텍스트 부분만 추출.
2. 검증 스크립트: `functions/scripts/validate-parser-against-aihub.js` — 발화 텍스트를 우리 파서(현재 룰 + B/C 적용 버전)에 통과시켜 실패율 측정.
3. 실패 케이스를 카테고리 분류 (마커 누락 / 어순 파괴 / 자연어화 등).
4. 카테고리별 빈도 상위에 따라:
   - 마커 변형 부족 → Phase B/C 보강 추가
   - 마커 누락 자체 → Phase G (폴백) 활성화 검토
   - 어순 파괴 → 룰 한계 인정. 데이터로 천장 위치 확정.
5. 검증: 보강 후 재측정. 커버리지 % 추적.
- **작업량**: 2~3일 (승인 후, 승인 대기 시간 별도)
- **리스크**: 낮음
- **외부 자료**: AI Hub "한국어 콜센터 음성"

### Phase G — 마커 없는 발화 폴백 (보수적, D 결과 의존)
1. Phase D 분석에서 "마커 없는 발화" 빈도 측정.
2. 임계 (예: 5%) 이상이면 폴백 활성화. 그 이하면 보류.
3. 폴백 가드 (오인식 방지):
   - 토큰 정확히 3개
   - 마지막 토큰이 한글/아라비아 숫자만으로 구성
   - 앞 두 토큰 중 1개 이상이 Phase E 의 도로명주소 사전과 매칭
4. 검증: D 데이터로 폴백 정밀도/재현율 측정.
- **작업량**: 1~2일
- **리스크**: 높음 (오인식 = 잘못된 데이터 저장)
- **외부 자료**: D + E 의존

---

## 의존성 / Critical Path

```
[지금]
  ├─ Phase A (4~6h, 토대) ──────────────────────────────┐
  ├─ Phase D-init (신청만, 0.5h) ─→ ⏳ 승인 대기 ─────┐  │
  ├─ Phase E (1.5~2일, 독립) ─────────────────────────┤  │
  └─ Phase F (1.5~2일, 독립) ─────────────────────────┤  │
                                                       │  │
[A 완료 후]                                            │  │
  └─ Phase B (2~3일, A 위에) ─────────────────────────┤  │
     └─ Phase C (1~1.5일, B 위에) ────────────────────┤  │
                                                       │  │
[D 데이터 도착 후]                                     │  │
  └─ Phase D-analysis (2~3일) ─→ Phase G 결정 ────────┘  │
                                                          │
[모든 Phase 종료 게이트]                                  │
  └─ 회귀 테스트 + 커버리지 측정 ←──────────────────────┘
```

## 산출물 / 코드 변경 범위

| 카테고리 | 위치 | 변경 |
|---|---|---|
| 파서 본체 | `call_manager/.../util/CallMemoParser.kt` | 수정 |
| 파서 본체 (사본) | `call_detector/.../util/CallMemoParser.kt` | 수정 (동기) |
| 숫자 변환 | `call_manager/.../util/VoiceInputHelper.kt` | 수정 |
| 숫자 변환 (사본) | `call_detector/.../util/VoiceInputHelper.kt` | 수정 (동기) |
| 단위 테스트 | `call_manager/app/src/test/.../util/*Test.kt` | 신규 |
| 단위 테스트 | `call_detector/app/src/test/.../util/*Test.kt` | 신규 + 인프라 |
| 사전 데이터 (임베드) | `call_manager + call_detector/app/src/main/assets/*.txt` | 신규 |
| 추출 스크립트 (재현용) | `functions/scripts/extract-*.js` | 신규 (런타임 영향 0) |
| 검증 스크립트 | `functions/scripts/validate-parser-against-aihub.js` | 신규 (런타임 영향 0) |

## 작업량 누적

| 도달 지점 | 시간 |
|---|---|
| Phase A | 4~6시간 |
| A + E | +1.5~2일 |
| A + E + F | +3~4일 |
| A + B | +2~3일 |
| A + B + C | +3~4.5일 |
| A + E + F + B + C | +5~7일 |
| 위 + D 분석 | +7~10일 (AI Hub 승인 시간 별도) |
| 위 + G | +8~12일 |

## 효과 천장 (이 라인의 한계)

도달 가능: 표준 마커 + 모든 변형, 방언, 종결어미 다양성, 도로명주소 정규화, 숫자 표기 다양성, 마커 없는 단순 발화 (가드 한정).

도달 불가: 어순 파괴 발화 ("시장 가요 용문에서"), 자연어/완전 비정형 ("잠깐 양평 들렀다 시장 가요"). → 이 영역은 룰 기반 한계.

## 안전장치

- 모든 Phase 종료 시 **회귀 테스트 통과 + 커버리지 % 측정** 의무화. 떨어지면 머지 금지.
- 실기기 테스트는 사장님이 수동 진행 (APK 산출 후).
- 라이선스 재확인 (특히 NIKL 상업 사용) 도입 직전 의무.
- 두 앱 동기 수정 강제. 한 쪽만 수정한 PR 금지.

## 명시적 비포함 (이 플랜의 경계)

- LLM 호출 / Cloud Functions 신규 / 외부 API 도입 — 모두 이 플랜에서 제외.
- Kiwi 바이너리 통합 / soynlp·hanspell 의존성 추가 — 모두 제외.
- STT 엔진 변경 (Naver CLOVA 등) — 제외.
- 기사앱 / 손님앱 / 픽업기사앱 변경 — 제외 (콜매니저·콜디텍터 한정).
- 지도 API 도입 (별건) — 제외.

---

## 승인 / 진행 상태

- **2026-04-26**: 사용자 "해줘" 승인. Phase A 부터 시작.
- **세션 범위**: Phase A 완료 후 다른 Phase 별 승인 받고 진행.
- **현재 상태**: 플랜 메모리 저장 완료. Phase A 코드 수정 진행 전 — 별도 시작 승인 대기.

## 관련 자료
- 현 파서 구현: `memory/plan_dispatch_memo_2026-04-24.md`
- 사용자 피드백: `memory/feedback_save_plans.md` (플랜 메모리 저장 의무)
- 사용자 피드백: `memory/feedback_grep_all_paths.md` (수정 전 모든 진입 경로 Grep)
- 전략 컨텍스트: `memory/strategy_pivot_2026-04-21.md` (대리 최소 유지 모드)

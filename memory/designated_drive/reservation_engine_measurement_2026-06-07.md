---
name: 통화예약 입력엔진 W경로 측정 결과 + UX 설계 (2026-06-07 세션)
description: 싼 STT(faster-whisper)→Gemini W경로를 10건 녹음으로 실측 — 10/10 정확·거짓양성0·STT손실 맥락복원 실증·콜당~1원. + 통화끝→자동써머리→[확인]한탭→예약 UX 확정. 코드자산·환경이슈·다음단계 인계.
type: project
---

# 통화예약 입력엔진 — W경로 측정 결과 + UX 설계 (2026-06-07)

> 플랜: [[reservation_engine_plan_2026-06-07]] (W경로 측정 슬라이스, 승인됨) / 전략: [[reservation_engine_strategy_2026-06-07]] (행동0=거인 돌팔매).
> 이 세션 = 그 플랜의 **실행+실측**. 결론: **W경로(싼 STT+텍스트)는 통한다 — 강하게.**
> 승인 plan 파일: `C:\Users\kala1\.claude\plans\functional-weaving-crown.md`

## 한 줄
faster-whisper(large-v3-turbo) 전사 → Gemini(2.5-flash) 텍스트 채점 = **10건 양역할 녹음에서 10/10 정확, 거짓양성 0, STT 오류를 Gemini가 맥락복원, 콜당 ~1원.** 매끈샘플이라 천장편향(상한해석) — 다음은 현장 거친샘플.

## 측정 결과 (10건, 녹음 = `call_manager/recordings/통화 녹음 양원규_260607_*.m4a`)
| # | 종류 | 결과 |
|---|------|------|
| 01 표준예약 | 커트·펌/내일3시→06-08 15:00/번호정확 | ✅만점 |
| 02 정보많음 | 염색·클리닉·커트/토요일→06-13/2명 | ✅만점 |
| 03 당일 | 남성컷/20분뒤→22:25:57 | ✅만점 |
| 04 변경 | intent=change/모레6시→06-09/신규오인❌ | ✅만점 |
| 05 취소 | intent=cancel (datetime만 사소) | ✅거의만점 |
| 06 거친예약 | 사투리·말겹침에도 디지털펌·클리닉·화요일2시반 다 잡음 | ✅만점 |
| 07 대리 | **업종자동판별=designated_driver**/양평역→옥천면/25000 | ✅만점 |
| 08 문의 | isReservation=**false**/헛예약❌ | ✅만점 |
| 09 거래처 | false/not_reservation/헛예약❌ | ✅만점 |
| 10 잘못걸림 | false/헛예약❌ | ✅만점 |

**핵심 지표**: 예약/비예약 10/10 · intent 10/10 · **거짓양성(헛예약) 0건**(최중요) · 시각정규화 정확(내일·모레·다음주화요일·20분뒤·이번주토요일, 일요일 6/7 앵커 요일계산까지) · 업종판별 정확.

## ★ 원규씨 가설 실증 (싼 길이 통하는 이유)
**07번: STT가 "옥천명"으로 오전사 → Gemini가 맥락으로 "옥천면" 복원.** "STT 좀 틀려도 AI가 맥락으로 극복"(6/7 발화)이 실제로 일어남 → **STT가 완벽할 필요 없음** = 더 싸고 가벼운 STT로 가도 됨. W경로의 존재 근거 입증.

## 단가 (W경로 = 싼 이유)
10건 합계 입력 4,906 / 출력 2,081 토큰. 건당 평균 입력 ~491. gemini-2.5-flash 기준 **콜당 약 1원** → ₩10,000/업소 단위경제 여유 통과.

## 전사 품질 (large-v3-turbo CPU)
핵심정보 거의 완벽: 번호 `010-2345-6789`·요금 `25,000`·시간·서비스 정확. 거친샘플(06)도 핵심 다 잡고 사투리는 표준어로 정규화. 경미오류=고유명사(박지훈→지윤/옥천면→명/OO상사→규치상사). 6/4 small("41.13")보다 turbo가 명확히 우수("41-3").

## 속도 실측 (★ "기다림" 질문의 답)
- **전사**: 29.3초 통화 → **66초** (PC CPU·int8·large-turbo). 모델캐시 후 2회차도 65초 = 대부분 순수전사. CPU+큰모델이라 느림(실서비스엔 안 씀).
- **Gemini 채점**: 건당 **~1.5초**(첫건만 8.5초 콜드스타트).
- **실서비스 추정**: 클라우드STT/서버GPU=통화후 ~5~10초 / 온폰 작은모델=10~30초. 작은모델 OK(맥락복원 실증).
- **결론**: 66초는 측정용 느린설정 탓. 그리고 **백그라운드 처리라 사용자 체감대기 ≈ 0**(통화끝나면 알아서, 곧 써머리).

## ★ UX 설계 결정 (이 세션 확정)
**통화끝 → [자동 "분석 중" 배너, 탭0] → 써머리 자동출현 → [확인] 한 탭 → 예약 등록(거기서 수정 가능)**
- 파싱 결과가 *바로* 스케줄로 직행 ❌ → **써머리 보고→확인→예약** 한 단계 둠. 이유: ①헛예약 치명(확인=정확도 안전장치) ②"확인"은 입력노동 아닌 검토 = 행동0에 *수렴*(완전0 아니어도 가장 가까운 안전점) ③초기 신뢰 부트스트랩(통제권 사장 유지=KT봇과 반대, 관계보존).
- 원규씨 "분석할까요? 팝업→로딩→써머리" 제안의 직관(멍한 기다림 해소=로딩 보여주기) = 맞음. 단 **"분석할까요?" 확인탭은 빼고 자동시작+진행배너로** → 탭 1번 절약(매통화 묻기=행동↑). 배너는 *보여주기만*.
- 비예약(08~10)은 isReservation=false라 **써머리에 안 띄움** → 볼 것도 없음(행동↓).
- 확인 미뤄도 **정보 유실0**(녹음+파싱 쌓임=블랙박스). 바쁘면 백그라운드+"확인할 예약 N건" 배지/알림.
- 확인은 **한 탭**으로: 확신 높으면 한 줄요약+[확인], 애매한 필드만 강조해 거기만 수정.
- **점진적 자동화 여지**: 신뢰 쌓이면 "확신 95%+ 자동등록·알림만, 애매한 것만 확인"으로 확인마저 축소. 지금은 전부 확인이 안전.
- "분석할까요?" 팝업은 **프라이버시 스위치**(사적통화 제외)로만 가치 — "예약관련만 자동 / 기본자동 끄기설정".

## 코드 자산 (이 세션 신규/수정 — functions/scripts/)
- **신규 `transcribe-whisper.py`**: faster-whisper 전사. large-v3-turbo→medium→small 폴백. 폴더 주면 *.m4a 일괄(모델1회로드). `language=ko, vad_filter`. `<녹음>.txt` 저장. 실행 `python scripts/transcribe-whisper.py "<파일|폴더>"`.
- **신규 `score-batch.mjs`**: 폴더 일괄 W경로 채점. 삼성파일명 `_YYMMDD_HHMMSS` → recordedAt 자동파싱. C경로는 `--audio`로만(기본off). thinkingBudget0. 실행 `node scripts/score-batch.mjs "<폴더>"`.
- **수정 `test-reservation-standalone.mjs`**: 단건 W·C 나란히 채점 + 토큰단가 + finishReason. 인자 `<m4a> [recordedAt] [transcript.txt]`(txt 있으면 W). thinkingBudget0. W_ideal=수기txt 먹이면 측정.
- 출력계약·원본로직: `functions/src/handlers/reservation.ts`(parseReservation onCall, **미배포** — 측정엔 standalone/batch로 충분, 앱호출/현장데모 시 deploy).
- 측정대본+정답표(ground truth): `C:\Users\kala1\OneDrive\Desktop\통화측정_대본_2026-06-07.md` (녹음 전 정답 고정 = 채점오염 차단).

## ⚠️ 환경 이슈 (다음 세션 주의)
- **로컬 PC에서 Vertex/Gemini 호출 node 스크립트는 `dangerouslyDisableSandbox: true` 필수.** sandbox면 `UND_ERR_HEADERS_TIMEOUT`(fetch failed).
- **C경로(오디오 업로드)는 로컬 PC에서 불안정**: 884KB base64를 us-central1/global에 못 올려 헤더타임아웃(5분). us-central1·global 둘 다. → **로컬 PC 한정** 문제(production reservation.ts는 GCP 내부망이라 무관). W경로(텍스트)는 가벼워 정상(1.5초). C 천장비교는 GCS경유 업로드(페이로드↓)나 앱deploy(GCP내부망)로 별도 트랙.
- gcloud CLI는 Windows Python 별칭 충돌로 막힘 → firebase CLI 사용.
- faster-whisper 1.2.1 설치됨(Python 3.14, `C:/Python314/python.exe`). 모델캐시 `~/.cache/huggingface/hub/`. Vertex ADC 작동 확인됨.

## GO 바 (플랜 절차3 — 매끈샘플이라 잠정)
이 매끈셋은 사실상 100%/0%라 바 정하기엔 쉬움. **제안: 사활필드 적중 ≥90%, 거짓양성 ≤5% (현장 거친샘플 기준)**. 원규씨 최종확정 대기.

## 다음 단계
1. **현장 거친 샘플 재검증** (실제 미용실 통화 or 더 거친 자가샘플) — 천장편향 보정, GO바 실측. 🚩선결 = 타깃살롱 안드로이드+자동녹음 비율 현장실사([[reservation_engine_strategy_2026-06-07]]).
2. (W충분 재확인 시) **production STT 경로 결정** — 온폰 작은모델 vs 클라우드 vs 서버GPU (속도·비용·정확도 균형, UX 기다림에도 직결). 작은모델 가능성 높음(맥락복원).
3. **써머리 확인 UX 구현** (위 설계) — coupon_app salon_booking 모듈 or call_manager 검증UI 확장.
4. (선택) C경로 천장비교 살리기 — GCS경유 or parseReservation deploy.
5. 미배포 `parseReservation` = 앱호출/현장데모 필요시 deploy + Vertex IAM(aiplatform.user).

## 클코 학습
- 측정 첫발에서 "C경로 GREEN"을 천장 확인으로 착각할 뻔 → 실제 진짜기준은 **수기 ground truth**(C아님). 덕에 C 네트워크 막혀도 측정 본령(W vs 정답) 진행 가능했음.
- standalone이 한 경로 throw 시 전체 죽던 것 → 경로별 try/catch(runSafe)로 견고화. 측정도구는 부분실패에도 결과 건져야.

---
name: 통화녹음 예약 입력엔진 검증 슬라이스 플랜
description: 통화녹음→싼STT→Gemini 예약파싱 = 공통 입력엔진. 콜매니저에서 검증 후 쿠폰앱 salon_booking 모듈로 이식. W(STT텍스트→Gemini) vs C(오디오→Gemini) 채점 슬라이스.
type: project
---

# 통화녹음 → 예약 입력엔진 (싼 STT 귀 + Gemini 뇌)

> 척추 = 원규씨 최종 플랜(2026-06-07) + 클코 검토 보강 5건 + 06-07 STT 조사 발견.
> **역할 분담(2026-06-07 확정)**: 실제 코딩은 콜마당(designated_driver) 폴더에서 진행. 클코(이 트랙)는 *설계 조언 + 플랜 기록* 담당.
> 관련: [[call_recording_ai_parse_2026-06-03]](6/3 권한우회 발견·STT 데모), [[ptt_implementation_2026-06-02]], coupon_app/BUILD_PLAN.md §9.3-C(미용실 통화 자동 예약 비서), coupon_app/app/lib/types.ts(출력 계약).

## Context (왜)
전화로 들어오는 아날로그 예약(미용·대리·택시·식당)을 구조화하는 **공통 입력엔진**. 도착지 = 로컬마루 예약 기본 모듈(BUILD_PLAN §9.3-C). 웹은 손님 표면이라 통화녹음 접근 불가 → 네이티브 `call_manager`에 **격리된 확장 기능**으로 빌드(코어=번호+블랙박스+PTT 무간섭), 로마는 **데이터 계약으로만** 연결. **콜매니저에서 검증 → 문제없으면 쿠폰앱 이식**(원규씨 2026-06-07).

**수렴한 기준:**
- 이해가 제품 본체. 규칙 파서 불가 → 뇌는 반드시 **Gemini**(프롬프트만 바꿔 전 업종·롱테일).
- 비싼 건 이해가 아니라 음성 토큰 → 음성을 텍스트로 바꿔 Gemini에.
- 귀 = 싼 STT(저장 파일 전사), 비용≈0 지향.
- 하이브리드: 평소 텍스트 경로, 저신뢰 콜만 음성→Gemini 승급(둘 다 뇌 Gemini).
- 마스터 제약 = ₩10,000/업소 단위경제. 이해 깎아 비용 맞추기 금지.

## 이미 있는 것 (검증된 사실)
- faster-whisper 데모: 실녹음→small→AI파싱 작동(거친 출력). 규칙파서는 날것 대화 못 풂, AI는 보정 = AI 뇌 우위 실증.
- 양자(손님+상담원) 음성 둘 다 캡처 확인.
- 파일명→번호·시각 = 권한 0 확실(저장연락처=이름만·번호숨김 불가 = 한계).
- `CallMemoParser.kt`(+테스트) = 폴백 자산 보존. 현 `VoiceInputHelper.kt` = 마이크 전용 SpeechRecognizer(파일 입력 미지원).
- ✅ **Part 0 로마 계약 = 반영 확인됨**(`coupon_app/app/lib/types.ts`: `ModuleType += 'salon_booking'`, `BookingEventType='booking.request'`, `Participants.customer?` 옵셔널, `SalonBookingPayload`). 엔진 출력 JSON = 이 모양(문서 합의, 코드 의존 0). rules 가드·재연결 = write 단계 연기.

## 핵심 실험 (검증 슬라이스)
같은 통화를 **수기 ground truth 대비** 두 경로로 채점:
- **W경로(목표)**: 오디오 → 싼 STT(전사) → Gemini(텍스트) → 예약 JSON
- **C경로(천장 후보)**: 오디오 → Gemini(멀티모달) → 예약 JSON

싼 STT는 싼 순으로(Whisper 고정 아님): ① `EXTRA_AUDIO_SOURCE` → ② OneUI 내장 전사 → ③ 온폰 Whisper → ④ 클라우드. 어느 게 되든 같은 Gemini 프롬프트로(뇌는 항상 Gemini). STT에 도메인 phrase-hint(서비스명·지명) 레버.

> ★ 조사 결과(2026-06-07) — **STT 출처는 측정 게이트가 아니라 production 최적화**:
> - `EXTRA_AUDIO_SOURCE`: 인식기 미지원 시 *마이크를 그냥 열어버림*(파일 무시) + raw PCM 기대(.m4a→PCM 디코딩 필요) = 투자 큼·불확실.
> - ML Kit GenAI Speech(`AudioSource.fromPfd`, 파일 입력 공식 지원)는 **on-device AICore/Gemini Nano 기반 → S21+/S22 미지원**(S24+/Pixel8+부터) → 우리 단말 불가.
> - 온폰 Whisper: WhisperKitAndroid(m4a·한국어·오프라인 됨, deprecated→argmax-sdk-kotlin/LiteRT 이전 중), whisper_android(TFLite, 16kHz mono WAV 변환 필요). 됨 but 모델 번들+변환 무거움.
> - **★ 첫 발 재정렬**: 무거운 온폰 STT 통합 먼저 ❌. **측정 엔진(Gemini W/C 채점 Cloud Function)부터** 세우고, W 입력 transcript는 측정용 *확실한 소스*(클라우드 STT 또는 PC faster-whisper large-v3 — 6/4 검증)로 채워 **W vs C 격차부터** 본다. 온폰 싼 STT 가능 여부는 "W 충분"이 수치로 확인된 *뒤* 별도 트랙.

## 측정 설계 (측정 전 못박기)
- **GO/NO-GO 바**: 살롱 사활 필드(datetime·service·callerPhone) 적중 ≥X%, 거짓양성 ≤Y%일 때 W 단독 출시. X·Y는 **첫 5건 보고 원규씨와 확정 후 고정**(사후 합리화 금지).
- **필드 가중**: datetime·service·phone=사활 / intent=분류 / partySize·notes=부차. 가중 채점.
- **첫 일 = "예약인가 아닌가"**: 테스트셋에 비예약 콜(취소·변경·문의·잘못걸림·거래처) 포함 → 헛예약 안 만드는지(거짓양성률). intent='booking.request'는 확신 시만.
- **샘플 현실성**: 본인 양역할=깔끔=천장 편향 → 거친 샘플(잡음·사투리·말겹침) 섞고, 매끈 점수는 상한으로만 해석.
- **산출**: STT 전사 정확도 + 뇌별 가중 적중률 + 거짓양성률 + confidence + 콜당 단가 → 가장 싼 충분 조합.
> 보강②: 비예약 3~4건은 *방향 신호*지 통계 확정 아님(유형 커버리지지 빈도 아님).
> 보강⑤: C는 천장 *후보*지 정답 아님. 정답=수기 ground truth. STT가 특정 발화를 더 정확히 잡으면 W가 C를 넘을 수도 → 둘 다 정답 대비 채점.

## 범위 — 지금 / 나중
**지금(슬라이스, 격리)**: 측정 엔진(Gemini W/C 채점) · 녹음 접근·파일명→번호 · 수동 선택 UI · ground-truth 채점표(비예약 포함). 코어 무간섭, 독립 패키지 `…reservation`, 권한 화면 내 격리.
**나중(수치 후)**: 온폰 싼 STT 통합 · 자동감지(FileObserver)·하이브리드 라우팅·실제 로마 event write·콜 프리필·Play 배포(Data Safety: 손님 음성 PII / Call Log "alternative methods" 회색지대 회피 — 별 트랙).

## 작업
**Part 0 — 로마 계약 [coupon_app, ✅완료]**: types.ts 반영 확인됨(위).

**Part 1 — 녹음 접근 + 파일명→번호 [call_manager]**
신규 `reservation/data/`: `RecordingFile.kt` · `RecordingFilenameParser.kt`(정규식 `(.+?)_(\d{6})_(\d{6})\.m4a`, object+JVM테스트, 전화 정규식 복사로 의존성0) · `CallRecordingRepository.kt`(MediaStore.Audio RELATIVE_PATH LIKE 'Recordings/Call/%' + SAF 폴백). 권한 `READ_MEDIA_AUDIO`(+`READ_EXTERNAL_STORAGE maxSdk32`), 화면 내 격리 요청. 삼성 자동녹음 ON 1회 안내.

**Part 2 — 이해 경로 (측정 엔진 우선) [functions]**
- `functions/src/handlers/reservation.ts`(+index.ts 1줄, ptt.ts 형틀: onCall ne3 300s 1GiB + auth 가드). 입력 `{gcsUri, transcript?, recordedAt}` — 한 콜로 W(transcript→Gemini)·C(gcsUri 오디오→Gemini) **동시 채점**(둘 다 받음). `recordedAt`=상대시각 정규화 앵커(필수). Vertex `gemini-2.5-flash`, location `us-central1`, **평탄 responseSchema(union 금지)**. 처리 후 삭제+lifecycle 24h. (SDK `@google-cloud/vertexai` vs `@google/genai` 배포 전 확인.)
- 클라이언트 `ReservationParseClient.kt` — `PttRecorder.kt` 업로드+삭제 · `WalletViewModel.kt` 예외분기 재사용.
- STT(W 입력): 측정 단계 = 확실한 소스(클라우드/PC whisper). 온폰 통합은 나중.
> 보강③: 양자 음성 한 텍스트로 뭉침(화자분리 없음). 지금 불필요 — W가 C에 크게 못 미칠 때 켤 레버로만.

**Part 3 — 테스트 UI + 진입 [call_manager]**
`reservation/ui/`: 목록(파일명·번호·시각)→1건→[채점]→**W vs C 나란히**(필드·confidence·transcript·지연·단가·비예약 판정). `MainActivity` Screen enum(L123)+when(L445~)+back(L419~) 각 1블록, `SettingsScreen` SettingsNavigationItem(L225~)에 "녹음 파싱 검증(beta)". 분리 시 ~5줄 제거.

## 재사용 자산
ptt.ts(onCall 형틀) · index.ts(L50 export) · PttRecorder.kt(Storage 업로드+삭제) · WalletViewModel.kt(Functions 예외, `Firebase.functions("asia-northeast3")`) · VoiceInputHelper.kt(전화 정규식 `extractPhoneNumber`/`formatPhoneNumber` 복사) · CallMemoParser.kt+테스트(폴백) · coupon_app types.ts(출력 계약).

## GCP 1회 셋업 (원규씨 직접 — 클라우드/Vertex 경로 시)
```bash
gcloud services enable aiplatform.googleapis.com --project=calldetector-5d61e
gcloud projects add-iam-policy-binding calldetector-5d61e \
  --member="serviceAccount:$(gcloud projects describe calldetector-5d61e --format='value(projectNumber)')-compute@developer.gserviceaccount.com" \
  --role="roles/aiplatform.user"
```

## Verification (E2E, S21+ R3CR312MB1L)
1. 삼성 자동녹음 ON. 미용 위주(+대리 참고) 양역할 샘플 — 예약 5~7건 + 비예약 3~4건 + 거친 샘플 일부, 각 정답 수기.
2. 측정 엔진(reservation.ts) deploy → 본인 통화 transcript로 W/C 채점 호출 → JSON 확인.
3. 첫 5건 보고 GO 바(X·Y) 확정·고정 → 전 샘플 가중 채점표(사활 적중률+거짓양성률+confidence+단가) → W 충분 여부 결론.
4. (W 충분 시) 온폰 싼 STT 통합 트랙 진입.

## 리스크 / 결정 지점
- W 이해도 < C → 하이브리드(저신뢰만 C) 또는 더 큰 STT. 슬라이스가 선을 수치로.
- 거짓양성(헛예약) = 가장 위험. 비예약 콜 채점이 잡음.
- 자가생성 샘플 천장 편향 — 거친 샘플 보정, 매끈 점수는 상한 해석.
- 스키마 union 금지 · 리전(ne3 Gemini 없을 수 있음→us-central1, 첫 호출 확인) · Whisper hallucination(무음 환청, 채점 시 감시).
- PII: 측정은 본인 통화만·즉시삭제·인프로젝트. 음성 폰 안 떠남=production(W 단독·온폰STT) 속성, 측정 중엔 C 위해 업로드. production=Data Safety 별 트랙.
- 🚩 **살롱 사장 안드로이드+자동녹음 = 가용 시장 게이트.** 한국 삼성 자동녹음 기본 OFF(사장이 켜야)+기기/지역 제약 → "안드로이드 비율"만이 아니라 "자동녹음 켜는 비율"까지 좁힘. **코드 착수 전 타깃 살롱 5곳 단말 실사 = 선결.** (콜매니저=매니저 안드로이드라 무관.)
- 셋업 — 자동녹음 ON·권한 1회 = 온보딩(완전 행동 0 아님).

# 온폰STT 측정 프로브 결과 — S21+(Exynos 2100), whisper.cpp (2026-06-09)

[[reservation_engine_pilot_verify_2026-06-09]] 의 "온폰 검증 지금" 결정 실행. 플랜 `~/.claude/plans/prancy-discovering-spark.md`.
**핵심: 무거운 앱(NDK) 통합 없이 CLI 프로브 + 기존 PC 채점기로 3대 질문을 다 답함**(rabbithole 회피).

## 방법
- whisper.cpp(ggml) arm64-v8a 크로스컴파일(NDK25) → `whisper-cli` + .so 를 S21+(R3CR312MB1L=Exynos2100) `/data/local/tmp` 에 push, `adb shell` 실행. GPU 백엔드 없음 → **CPU+NEON 전용**.
- 6/7 측정 10건(test01~10, 정답 대본 = `call_manager/recordings/통화측정_대본_2026-06-07.md`)을 faster_whisper.decode_audio 로 16kHz WAV 변환 → 온폰 전사.
- 온폰 small 텍스트를 `functions/scripts/score-batch.mjs`(Gemini 2.5-flash, 6/7과 동일 채점기)로 채점.

## 1) 실현성 — ✅ 됨
whisper.cpp 가 Exynos 2100 에서 한국어 통화녹음을 전사함. 모델은 이미지 없이 폰에 직접 번들 가능.

## 2) 속도 (11.3초 클립 기준, t=6)
| 모델 | 크기 | total | **RTF** | 1분 통화 환산 |
|---|---|---|---|---|
| large-v3-turbo q5_0 | 547MB | 46.6초 | **4.1×** | ~4분 |
| small q5_1 | 181MB | 14.0초 | **1.24×** | ~75초 |
- 병목 = encode(turbo 43초 / small 9.2초). CPU라 그럼. GPU/NNAPI 가속은 이 빌드에 없음.

## 3) 정확도 — ★ 베팅의 정밀한 답
**온폰 turbo = 서버 faster-whisper 와 동일 품질**(같은 모델·다른 런타임=무손실):
- test07 "옥천명"(=옥천면, Gemini 복원가능) ✓ / test05 "토요일" ✓ — 서버 6/7 과 동일. → **[확정] 엔진 등가가 온폰에서도 성립**(whisper.cpp 런타임 스왑이 정확도 안 깎음).

**온폰 small(빠름) = 안전속성은 유지, 핵심필드 3개 파괴**(Gemini 채점 결과):
| test | 정답 | small 결과 | 판정 |
|---|---|---|---|
| 01 표준예약 | 010-2345-6789 | phone=010-2345-67**8**(끝자리 소실) | ❌ 번호 |
| 02 염색2명 | 토11시·2명 | 정확 | ✅ |
| 03 당일컷 | 20분뒤 | 정확 | ✅ |
| 04 변경 | change | **intent=change**(신규오인❌) | ✅ |
| 05 취소 | cancel·토11시 | intent=cancel✓ / datetime "**툴**11시"(토요일 소실) | 안전✓·시각❌ |
| 06 거친 | 다음주화 2시반 디지털펌+클리닉 | 정확 | ✅ 거친데도 |
| 07 대리 | 양평역→옥천면·25000 | from·fare✓ / **to="5천 명"**(옥천면 소실) | ❌ 목적지 |
| 08 문의 | false | false | ✅ 헛예약0 |
| 09 거래처 | false | false | ✅ 헛예약0 |
| 10 잘못걸림 | false | false | ✅ 헛예약0 |

- **헛예약0 = 완벽**(08·09·10 false, 04/05 신규예약 오인 없음). small 이어도 *안전 속성*은 유지.
- **깨진 3건 = small STT 정보 파괴**(복원 불가): 번호 끝자리·시각(토요일→툴)·대리 목적지(옥천면→5천명). turbo 는 셋 다 정확.
- 결론: **"STT 틀려도 Gemini 복원" = 복원 가능한 오류엔 통함(옥천명→옥천면, 모래→모레)·정보 파괴엔 깨짐.** 모델 작을수록 파괴형 오류 ↑.

## 함의 → `[열림] STT 위치` 좁힘
- 온폰 **기술적으로 가능 + turbo면 서버 정확도 = PII-안전·행동0 골격 성립**. 단 **Exynos2100 CPU turbo = RTF 4.1×**(1분 통화 4분, 백그라운드라 체감은 줄지만 긴 통화 부담).
- **small(1.24×)은 빠르나 대리 목적지·시각·번호 파괴 → 대리엔 부적합.** (미용 예약은 대체로 견딤.)
- 남은 갈래: ① 온폰 turbo + 4× 백그라운드 감수 ② medium 등 중간 모델 RTF~2× 탐색 ③ GPU/NNAPI 가속(이 빌드 미포함) ④ 신형폰(Exynos2400+/Snapdragon NPU=WhisperKit) ⑤ 서버STT 유지(turbo+GPU=빠름+정확, PII 비용). **결정은 PII vs 지연 vs 서버비용 = 원규씨 전략판단.**
- production 앱 NDK 통합(Phase 2)은 위 갈래 정해진 뒤. 지금은 측정으로 충분.

## ★ 전략 재정리 (원규씨 지적, 세션 말미) — 온폰 속도 ≠ 보편 답
- "내 폰(S21+)을 빠르게"는 **보편 답 못 됨**: 온폰 속도 = 기기별(칩·코어·GPU). 속도 레버일수록 덜 이식됨 — **GPU/NPU 가속은 Mali/Adreno/Exynos/Snapdragon 별개 백엔드**라 한 폰 튜닝이 옆 폰에 무용. 사장폰 = 제각각+흔히 저사양 → 핵심 플로우가 사람마다 다르게 작동 = 치명적 불일치.
- **보편 STT 바닥 = 클라우드**(기기 무관·모두 동일): faster-whisper(지금·검증) / 카카오(보류). **온폰 = 미래 베팅(NPU 표준화 2~3년) or 하이브리드(능력폰=온폰 PII안전, 약한폰=클라우드 폴백)**. "정확도는 폰서도 됨"은 입증돼 은행 보관.
- ⏸ 기기별 속도 튜닝(스레드/Vulkan/medium) 측정은 **중단**(보편성 없음). 했어도 그 폰 수치일 뿐. → production 가속·NDK통합은 미래/하이브리드 정해진 뒤.

## 다음(내일)
- 🚩 **최우선 = 양평 실손님 자동녹음(field GO바)** — 6/7~9 자가녹음 천장편향 벗어나기. 파일럿은 서버STT로 검증 계속.
- (선택) 카카오 i Cloud STT vs faster-whisper 측정(클라우드 층 정리) — 같은 10샘플로. B2B API 접근 확인 선행.
- 온폰 = 하이브리드 설계는 더 뒤.

## 자산
프로브 산출물 = `C:\Users\kala1\onphone_stt_probe\`(whisper.cpp arm64 빌드·turbo/small 모델·result_small.txt) + 폰 `/data/local/tmp/wprobe/`. 측정 전용, 커밋 X. 내일 재사용 가능(빌드/모델 그대로).

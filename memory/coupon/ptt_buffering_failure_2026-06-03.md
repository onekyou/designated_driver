---
name: ptt-buffering-failure-2026-06-03
description: PTT 음성 버퍼링(talk-anytime) PoC — Step1 게이트에서 구조적 막다른 길로 폐기. 진짜 레버=워밍창 연장
metadata:
  type: project
---

# PTT 음성 버퍼링 PoC — 게이트 실패·폐기 (2026-06-03)

**결론 한 줄**: 음성 버퍼링(외부 오디오 소스)은 **콜드 연결시간을 영구 재생지연으로 전환**할 뿐 실시간을 못 만든다. 음질도 외부소스 APM 상실로 저하. **폐기.** 교차 실시간의 진짜 레버 = *워밍창 연장*(내장 마이크 유지). 현재 최선 = `ptt-coldstart` 빌드(음성메모+라이브).

**관련**: [[ptt-coldstart-voicememo-2026-06-02]], [[ptt_screenoff_send_2026-06-02]](§3 버퍼링 보류였음), [[ptt_implementation_2026-06-02]]
**브랜치**: 코드는 `ptt-buffering`(commit `8c3b4310`, manager-direct-drive/ptt-coldstart 기점, **로컬·미푸시**)에 기록 보존. 메인 라인은 `ptt-coldstart`(마지막 푸시)로 복귀.
**plan**: `C:\Users\kala1\.claude\plans\curious-tumbling-brooks.md`(이 PoC의 4-step 플랜, Step1 게이트에서 중단)

---

## 1. 왜 시도했나 (동기)

본인 결정(6/2): 양방향보다 **버퍼링 우선**. 동기 2개:
- 콜드(백그라운드/Doze 복귀) 첫 발화가 연결까지 ~5초 버튼 hold 필요 → 운전 중 무리.
- 2차 비프가 *실제 연결 시점*에 울려 타이밍 제각각(웜 ~0.45s / 콜드 ~1.4s) → "언제 말하지" 혼란.

가설: 누르는 즉시 말하고(대기 0) → 마이크를 우리가 캡처해 로컬 버퍼에 담다가 → 연결되는 순간 흘려보내 첫 음절 무유실. 그러면 콜드/라이브가 한 경로 + 고정 비프 가능. **본인이 "나누지 말라"던 단일 경로의 실현으로 기대.**

## 2. 무엇을 만들었나 (설계 B = 단일 외부 소스)

- **신규** `call_manager/.../service/PttAudioCapturer.kt`: `AudioRecord`(VOICE_COMMUNICATION, 16kHz mono PCM16) → **선입선출 보존버퍼**(오버라이트 링 아님, 첫 음절 보존) → `frameSink`. press 즉시 캡처(gate 닫힘 적재) → `openGate()`(수신자 합류) flush → 실시간. 30s 상한 오버플로 가드.
- `PTTManager.kt` 라이브 경로 전환(콜드 음성메모 경로는 폴백 유지):
  - `ensureEngine`: `createCustomAudioTrack(Constants.AudioTrackType.AUDIO_TRACK_MIXABLE, AudioTrackConfig{enableLocalPlayback=false})`
  - join 옵션: `publishMicrophoneTrack=false` + `publishCustomAudioTrack=true` + `publishCustomAudioTrackId=trackId`
  - press: `capturer.start{ data -> engine.pushExternalAudioFrame(data,0,16000,1,TWO_BYTES_PER_SAMPLE,trackId) }`
  - `fireReady`: `muteLocalAudioStream(false)` → `capturer.openGate()`(버퍼 flush)
  - `stopTransmit`/`release`: 캡처 정지 + `destroyCustomAudioTrack`
- Agora `io.agora.rtc:voice-sdk:4.5.2`(io.agora.rtc2.*) — 외부 오디오 API **존재**(full-sdk 교체 불필요).

## 3. 게이트에서 드러난 실패 (3가지, 실측)

### ★ 실패 1 — 구조적: 버퍼 flush = 영구 재생지연 (실시간 불가, 핵심)
- openGate에서 쌓인 backlog를 한꺼번에 push → Agora가 정상속도로 재생 → **backlog 크기만큼 발화 내내 지연 고정**.
- **backlog 크기 = 콜드 연결시간**. logcat 실측 flush = **1340ms / 4160ms**. 즉 콜드는 1.3~4초 영구 지연 = **음성녹음과 같은 체감**.
- 본인 평가: "이전 실시간 느낌 사라지고 오히려 음성녹음만큼 딜레이". → 버퍼링은 *연결시간을 재생지연으로 옮겼을 뿐*, 실시간을 만들지 못함. 이건 튜닝으로 안 변하는 성질(물리).

### 실패 2 — 마이크 충돌 (무음 캡처)
- Agora 엔진이 채널 진입 시 마이크를 점유 → 우리 `AudioRecord`가 **무음(0) 캡처**. logcat `capture amp(max)=0`(이후 4,7,10 = 사실상 침묵).
- `pushExternalAudioFrame ret=0`(push 자체는 정상). 즉 push·트랙·버퍼는 정상인데 *원천 소리가 0*.
- 완화 시도: `engine.enableLocalAudio(false)`(Agora 마이크 캡처 OFF, 수신측 패턴). 단 이게 MIXABLE 트랙 처리에 영향 줄 위험 + 검증 미완.
- *진단 함정*: 첫 5프레임만 로깅 → press 직후(발화 전) 침묵을 측정해 오판 소지. → 1초 창 피크 미터로 교체(`capture peak(1s)`). 발화 중 레벨 측정이 정답.

### 실패 3 — 음질: 외부 소스 APM 상실
- Agora는 외부 소스를 "이미 처리된 음원"으로 간주 → 내장 **ANS(소음제거)/AGC(자동음량)** 미적용 위험 → 차소음 그대로 + 음량 저하("1/3 소리"). 내장 마이크 경로는 APM 자동 적용이라 완벽했음.
- 레버 후보(미검증): ①AUDIO_TRACK_MIXABLE ②AI denoise 확장 `libagora_ai_denoise_extension.so` ③VOICE_COMMUNICATION 캡처(OS/HW NS). 실패1(지연)이 본질이라 음질 레버까지 안 감.

## 4. 근본 진단 — "실시간 느낌"의 출처

- 교차 실시간 = 말할 때 **수신자가 채널에 상주(웜)**해야 함. 이전에 느낀 실시간 = 내장 마이크 *웜* 케이스.
- logcat상 테스트 발화가 8초 간격 → **워밍창 5초 초과 → 매번 콜드** → 매번 음성녹음급 지연. 즉 *워밍창이 짧아서* 대화가 콜드로 떨어진 것.
- **첫 콜드 발화 지연은 아키텍처(FCM 깨우기+Trigger Join) 내재 한계** — 수신측 Doze 깨우기 ~1.3s+. 제거 유일 방법 = 상시 접속(always-join) = 비용+배터리(본인 거부).

## 5. 결론·방향 (재결정)

- **버퍼링(외부 소스) 폐기.** 잘못된 레버: 콜드 지연을 재생지연으로 전환(실시간 X) + APM 상실(음질 ↓) + 마이크 충돌. 웜은 내장마이크로 이미 실시간이라 개선 0.
- **진짜 레버 = 워밍창 연장**(미시도): 대화 중 채널을 살려두면(발화마다 leave 타이머 리셋, 유휴 30~60s 후에만 leave) 대화 턴이 웜 → **교차 실시간**, 내장 마이크 음질 유지. 비용은 대화 시간만큼만. **단 첫 콜드 1발은 여전히 지연**(음성메모가 처리).
- **현재 최선 = `ptt-coldstart` 빌드**(콜드=음성메모 hold0 / 웜=라이브 실시간). 양평 실사용 데이터로 판단.
- 단말 복구: S21+(R3CR312MB1L)에 깨진 버퍼링 PoC가 깔렸었음 → `ptt-coldstart` 빌드 재설치로 원복.

## 6. 기술 자산 (재사용 가치 — 버퍼링 아니어도)

- **Agora voice-sdk 4.5.2 커스텀 오디오 API 확정**: `createCustomAudioTrack(AudioTrackType.AUDIO_TRACK_MIXABLE, AudioTrackConfig)` → trackId / `pushExternalAudioFrame(byte[], timestamp, sampleRate, channels, Constants.BytesPerSample.TWO_BYTES_PER_SAMPLE, trackId)`(ret 0=성공) / `options.publishCustomAudioTrack=true`+`publishCustomAudioTrackId` / `destroyCustomAudioTrack`. import `io.agora.rtc2.audio.AudioTrackConfig`.
- **마이크 충돌 교훈**: Agora 엔진 활성 중 별도 AudioRecord는 무음 위험 → `enableLocalAudio(false)`로 Agora 마이크 해제 필요(단 커스텀 트랙 영향 검증 필요).
- **진단 교훈**: 오디오 레벨 진단은 *발화 중* 측정해야 함(첫 프레임만 보면 press 직후 침묵 오판). 1초 창 피크 미터 사용.
- 코드 전체: `ptt-buffering` 브랜치 commit `8c3b4310`.

## 7. 클코 학습

- **본인 직관 추종이 항상 옳진 않음**: 본인의 "단일 경로 통합" 직관에 빠르게 동조해 버퍼링으로 진입했으나, *물리적으로 콜드 지연은 못 없앤다*는 걸 PoC 전에 더 강하게 짚었어야. 버퍼링은 "지연을 옮길 뿐"이라는 본질을 설명은 했으나(딜레이=연결시간), 그게 *체감상 음성녹음급*이 된다는 결론까지 미리 못 내림.
- **게이트 우선 설계는 옳았음**: 플랜이 Step1을 "차소음 게이트, 막히면 재결정"으로 둔 덕에 큰 구현 전에 폐기 결정. PoC-first가 손실 최소화.
- **"실시간"의 정의를 코드 진입 전 합의했어야**: 본인이 원한 건 *교차 실시간*(대화 왕복)이고 그건 *웜 상주* 문제지 *버퍼* 문제가 아니었음. 목표를 레버에 정확히 매핑 못 한 게 우회의 원인.

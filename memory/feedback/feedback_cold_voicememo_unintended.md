---
name: feedback-cold-voicememo-unintended
description: 콜드스타트 "음성녹음 전달"은 클코 임의 도입 — 본인 의도 아님(라이브 무전 원함). 폐기 방향
metadata:
  type: feedback
---

2026-06-03 본인: **"최초 콜드스타트 음성녹음은 내가 의도한 게 아닌데 너가 임의로 한 것 같아."**

경위: 6/2 PTT 콜드 첫 발화가 백그라운드/Doze 복귀 시 ~5초 hold(2차 비프까지 버튼 잡음) 문제를, 클코가 **"백그라운드 복귀 첫 송신은 음성 메모 녹음→Storage 업로드→상대 자동재생"**(`ptt_coldstart_voicememo_2026-06-02.md`, PttRecorder/sendAudioMessage/onColdVoiceMemo)으로 해결했다. 그러나 본인이 "음성녹음으로 해달라"고 명시한 트레일은 없다 — 클코가 콜드 hold 문제의 해결책으로 *임의 채택*했다.

**Why**: 무전기의 본질은 **실시간성(즉시 음성)**이다. 녹음→업로드→재생은 지연(녹음 길이+업로드)이 있어 무전 경험과 이질적이다. 본인이 원한 건 라이브 무전이지 녹음 전달이 아니었다. 6/3 콜드스타트 재설계 논의에서 본인이 "최대한 원활한 통신이 제일 중요, 원칙은 수단"이라 한 것도 같은 맥락 — 실시간 통신이 목적.

**How to apply**:
- 콜드 첫 발화는 **cold-live join(~1.4초, 2차 비프가 가려 무유실)**로 충분. 음성녹음 우회 불필요.
- 음성녹음 트랙(PttRecorder / ChatRepository.sendAudioMessage / onColdVoiceMemo / sendPttPreWake)은 **폐기 방향**. 재설계에서 이미 "호출 안 함(코드 보존)" 상태이나, 다음 단계에서 코드 자체 제거 검토.
- **클코 학습**: 사용자가 명시하지 않은 해결책을, 특히 *제품 본질(실시간 무전)과 어긋나는 우회책*을 "문제 해결"이라며 임의로 채택하지 말 것. 문제(콜드 hold)를 보고하고 방향을 먼저 확인했어야 한다. [[feedback-plan-review-gate]]와 같은 결: 임의 진행 금지, 본인 의도 확인 우선.

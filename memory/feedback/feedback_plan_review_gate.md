---
name: feedback-plan-review-gate
description: plan 승인 전 항상 스스로 "누락·오염" 코드 검증을 먼저 수행하고 반영한 뒤 ExitPlanMode 할 것
metadata:
  type: feedback
---

원규씨는 ExitPlanMode 직후 바로 승인하지 않고 **"누락 오염 검토해줘"**를 반복 요구한다 (2026-06-03 PTT 1단계·2단계 연속, 매번 승인 거부 후 동일 요청).

**Why**: 서브에이전트(Plan/Explore)가 만든 plan은 그럴듯해 보여도 가정에 구멍이 있다 — 실제로 6/3에 발견된 것: ① 수신측 콜드 음성메모 자동재생 **누락**(송신만 설계), ② driver_app 템플릿 복붙 시 단일엔진 `release()` 이중 호출 **오염**, ③ Hilt 스코프 우회로 생기는 `sendPttVoiceMemo` **dead code**, ④ 마이크 미사용 서비스의 microphone FGS가 권한 미허가 시 **크래시**. plan을 글로만 검토하면 이런 건 안 잡히고, **critical 파일을 직접 Read해 가정을 대조**해야만 드러난다.

**How to apply**: ExitPlanMode를 부르기 **전에** 항상 자체 검토 게이트를 돈다 —
- **누락**: 양방향/송수신처럼 대칭이 있는 기능은 반대 방향(수신측·역호출·정리 경로)이 plan에 다 있는지. functions가 이미 보내는 필드를 수신측이 읽는지.
- **오염**: fork/복붙한 코드의 생명주기(release/destroy/onDestroy), 권한 전제(FGS type↔런타임 권한), DI 스코프(Hilt vs 수동 싱글톤), Room migration 버전+addMigrations 쌍, prefs 키 정합, dead code.
- 발견 즉시 plan 파일에 반영하고, "누락 N건·오염 M건" 형태로 보고한 뒤 ExitPlanMode.

즉 원규씨가 "검토해줘"라고 말하기 전에 이미 검토가 끝나 있어야 한다. 관련: [[clcode_agency_charter]] 무한 책임 원칙.

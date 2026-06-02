---
name: ptt-coldstart-voicememo-2026-06-02
description: PTT 콜드스타트 해소 — 콜드 음성메모+포그라운드 라이브, 브랜치 ptt-coldstart 완료(E2E 검증)
metadata:
  type: project
---

# PTT 콜드스타트 해소 — 음성 메모 트랙 완료 (2026-06-02)

**상태**: ✅ 코드 완성 + E2E 단말 검증 + functions 배포(프로덕션). **브랜치 `ptt-coldstart`**(manager-direct-drive 기점, worktree `C:\Users\kala1\dd-ptt-coldstart`에서 작업). master PR 미생성(다음).
**관련**: [[ptt_implementation_2026-06-02]](라이브 1방향), [[adb-monkey-changes-autorotate]], plan `C:\Users\kala1\.claude\plans\witty-spinning-wand.md`

## 문제 → 해결
매니저 PTT 콜드 첫 발화가 **백그라운드/화면오프 복귀 시 ~5초 hold**(2차 비프까지 버튼 잡음). 매니저=운전 중 사용이라 무리.
→ **백그라운드 복귀 첫 송신만 음성 메모**(녹음→채팅 첨부→기사 자동재생, hold 0). **포그라운드는 라이브 유지**(간격 벌어져도 ~수초, 견딜만 — 본인 확정). 라이브 양방향 녹음·STT는 범위 외(블랙박스 참고만).

## 커밋 흐름 (8개, 모두 push 전 로컬)
1. `d7e7ecfc` 콜드 음성메모 기반(녹음 MediaRecorder→Storage chat_audio→chat_messages audio 첨부→onChatMessageCreated FCM→기사 DriverForegroundService.playVoiceMemo 자동재생). 스키마: call_manager Room v8→9, driver v2→3. firestore.rules+storage.rules audio. functions chat.ts audio 필드.
2. `3914b184` 트리거를 `currentChannel==null && fromBackground`로 한정(포그라운드 cold-live 복원). MainActivity pttFromBackground 플래그.
3. `ab5a4c75` 효과음 통화모드 가청(playCue inCall→USAGE_VOICE_COMMUNICATION_SIGNALLING + ensureEngine setDefaultAudioRoutetoSpeakerphone) + 플래그 체류시간 견고화.
4. `da59ed29` PTT 직후 onStop 무시(pttLastActivityAt, 통화모드 유발 onStop에 라이브 안 깨짐).
5. `f49ee35e` ★ MainActivity configChanges — **회전 시 액티비티 재생성 방지**(연속 라이브 깨짐 진범이었음).
6. `d861902e` MainActivity screenOrientation=portrait(세로 고정).
7. `eb51798a` pre-wake — 콜드 녹음 시작 시 sendPttPreWake(기사 Doze 선행 깨우기, minInstances 미설정=비용0, 단발). driver ptt_prewake no-op 분기(최종 else 앞).
8. `e789bc61` functions lib 재빌드 정합.

## E2E 검증 (S21+ 매니저 / Z Flip4 기사)
- 콜드(백그라운드 복귀)=음성메모 hold0 ✓ / 포그라운드 연속=라이브 유지 ✓ / 효과음 스피커 가청 ✓ / 세로 고정(회전 무관) ✓ / **기사 화면오프 자동재생** ✓(목적 핵심) / pre-wake 음성메시지보다 8.7초 먼저 도달 ✓.

## 핵심 진단 자산 (재발 시)
- **연속 라이브 깨짐 진범 = 화면 회전**(폰 기울임→auto-rotate→MainActivity 재생성→플래그 리셋). NFC·근접센서는 오진이었음. configChanges로 해결.
- 라이브 PTT는 `MODE_IN_COMMUNICATION`(통화모드)로 잡혀 ① RingtoneManager 효과음 억제(→VOICE_COMMUNICATION_SIGNALLING+스피커) ② 통화 중 시스템 onStop 유발(→PTT 직후 onStop 무시).
- adb `monkey`가 시스템 자동회전 강제로 켬 → 테스트는 `am start` 사용([[adb-monkey-changes-autorotate]]).

## pre-wake 정직한 한계
이득 = 마지막 Doze 깨우기 ~1-3초(단발은 ~10s FCM 창 내 메시지 도착 시만). 긴 녹음 지연 대부분은 *녹음 길이 자체*(voice-memo 모델상 불변) — 근본은 live 스트리밍(별도 트랙).

## 다음 세션 진입 후보
① **master PR**(`gh pr create`, 8 commit) ② S22(R5CT41TJZFP) driver_app install(재연결 시) ③ pickup_driver_app: audio 메시지에 "[음성]" 알림만 뜸(재생 X) — 픽업 PTT 수신 별도 트랙 ④ 라이브 양방향(기사 PTT 대답) ⑤ 라이브 발화 블랙박스 녹음(Agora startAudioRecording) ⑥ pre-wake periodic(긴 녹음, 과투자라 보류).

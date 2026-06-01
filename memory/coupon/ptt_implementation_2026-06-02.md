# PTT 구현 — PoC + UX 증분 작동·커밋·배포 완료 (2026-06-02)

**상태**: ✅ E2E 검증 + 3 commit push + functions 배포. 일방 브로드캐스트(매니저→일반기사) 루프 완성.
**관련 plan**: `C:\Users\kala1\.claude\plans\harmonic-sparking-hedgehog.md`(부모), `ptt_phase2_design_2026-06-01.md`(설계), `curried-petting-prism.md`(증분)
**관련 메모**: [[ptt_volume_button_principle]], [[ptt_operation_scenario]], [[ptt_market_replacement_card]], [[ptt_pivot_other_tracks_2026-05-25]], [[principle_user_action_minimum]]

## 커밋 (branch manager-direct-drive, push 완료)
- `0bf9ebe4` functions — `generateAgoraToken`(RtcTokenBuilder uid, 2025-08 템플릿 복구) + `sendPttWake`(designated_drivers data-only high FCM type=ptt_dispatch) + `buildMulticastPttWakePayload` + **minInstances:1** + agora-token
- `20c02fc2` call_manager 송신 — PTTManager(Agora LIVE_BROADCASTING/broadcaster) + 탭→hold-to-talk + 2비프 + 발화중 배너 + onResume prewarm + onPause 안전종료
- `0fb15cd2` driver_app 수신 — PttAudioManager(broadcaster role, 마이크 미publish) + ptt_dispatch 분기 + 종료 오버톤

## 아키텍처 (확정)
- **FCM wake + Agora RTC Trigger Join**. RTM 폐기(과거 "Not connected" 레이어). 시그널링 전부 FCM+RTC.
- 발화 시 매니저 join → `sendPttWake`로 사무실 기사 FCM wake → 기사 fast-join → 음성.
- Agora: project `DesignatedDriver-PTT`, App ID `e5aae3aa18484cd2a1fed0018cfb15bd`(공개값, 클라 임베드), Certificate = **Firebase Secret Manager `AGORA_APP_CERTIFICATE`만**(평문 저장 X). ⚠️ 다수 사무실 실배포 전 Certificate 1회 rotate 권장(6/2 채팅 노출).

## UX 모델 (본인 공동설계, 무전기 관행 복원)
- **탭 후 hold-to-talk**: 볼륨다운 톡(arm) → 0.5s 내 다시 눌러 유지=발화 → 손 떼면 종료. **stuck-on 0**(떼면 무조건 끝). onPause 안전종료(포커스 상실 시 ACTION_UP 유실 대비).
- **시작 2비프 = 연결창**: press 1차 비프(연결중 배너) → 수신측 합류(onUserJoined) 시 2차 비프(발화중 배너) + **마이크 라이브**. 매니저는 *2차 비프 후 발화* → 첫 음절 유실 0. 마이크는 합류 후에만 publish(join은 muteLocalAudioStream(true)로).
- **종료 1비프 = 오버톤**: 손 뗌 → 매니저 로컬 + 수신측(매니저 mute 감지 onRemoteAudioStateChanged STOPPED/REMOTE_MUTED, wasStarted 가드)에서 비프. 듣는 기사가 "끝"을 인지(양방향 시 대답 신호). 효과음 = `R.raw.ptt_start`(채팅음, 양 앱 존재).
- **5초 종료-기준 워밍창**: 손 뗌 후 ~5초 채널 유지(매니저+수신측 leave 앵커=발화종료 기준). 5초 내 재발화는 즉시(remoteUsers>0), 그 뒤는 ~1.4초 재연결.

## 측정치 (E2E, S21+ 송신 / Z Flip4 수신 화면꺼짐)
- 콜드 첫 발화 press→2차 비프(연결창) **~1.4초** (onUserJoined 구동, 3초 타임아웃 아님)
- 웜 재발화(기사 채널 상주) **~0.45초** (remoteUsers>0 즉시-ready)
- `sendPttWake` 웜 **0.4초**(minInstances 효과, 콜드는 2초였음)

## ★ 핵심 함정·학습 3건
1. **broadcaster role 함정 (가장 큼)**: LIVE_BROADCASTING에서 `onUserJoined`는 *broadcaster 합류 시에만* 울림. 수신측을 AUDIENCE로 두면 매니저쪽 onUserJoined 영영 안 와 → **매번 3초 타임아웃**(첫 증상: "1·2차 비프 간격 너무 큼"). → 수신측도 **CLIENT_ROLE_BROADCASTER**(마이크 미publish라 실질 수신전용)로 해야 ready 신호 옴 + remoteUsers 추적 작동.
2. **getApplication()/applicationContext가 onCreate에서 null 반환**(이 S21+ 단말). Firebase는 ContentProvider 자동초기화라 별도 작동→헷갈림. → 엔진은 **발화 시점의 살아있는 Activity 컨텍스트**로 생성(onCreate/lazy 회피). onResume prewarm도 컨텍스트 안전 시점.
3. **콜드 5.5초 = Cloud Function 콜드스타트 × 2직렬 호출**(토큰+wake). minInstances로 정상화. 추가 단축은 1콜 통합+토큰 FCM 동봉(향후).

## 비용 (본인 판단)
- Agora 음성 ≈ 1.3원/인·분. 5초 워밍창 = 단일 사무실 월 ~2,600원(무시 가능), 2000사무실 ~520만원. **꼬리는 비용 다이얼·상수라 스케일 가서 데이터로 튜닝**. 지연은 비프가 가려 UX 무관 — 워밍의 진짜 가치는 *대화 중 재-wake(FCM 최약 고리) 회피·안정성*.

## 단말
- S21+ R3CR312MB1L: call_manager(송신) / Z Flip4 R3CT80K78NP: driver_app(수신). S22 R5CT41TJZFP 미연결(이번 세션).

## ★ 운영 전제 정정 (2026-06-02 본인) — 매니저 = 운전 중 사용 → screen-off 송신 필수
- **매니저는 픽업을 병행**, 즉 **운전 중에 콜매니저로 PTT**. 거치형 사무실 단말이 *아님*. "편하게 사무실에서 할 거면 앱을 왜 써" — 과거에 "무리해서" screen-off 볼륨 캡처를 시도한 이유.
- ⚠️ **현재 구현(dispatchKeyEvent)은 foreground 전용** → 화면 켜진 채 앱 열어야만 송신. **실사용(운전 중 화면 off)과 미스매치.** `FLAG_KEEP_SCREEN_ON`은 답 아님(운전 중 화면 못 켜둠).
- **Play Store 정책 우려 무효**: call_manager는 권한 때문에 **홈페이지 사이드로드 전용** → Accessibility 등 민감 권한 *자유롭게 사용 가능*.
- **과거 screen-off 송신은 성공한 자산**: "송신측 완전 꺼짐+볼륨다운 2회 성공"(`6ae77f74`·`17bcd8b3` AccessibilityPermissionHelper+BackgroundPTTService). 1월 제거는 *수신측 RTM "Not connected"* 때문 — **이번에 FCM+Agora로 수신측 해결됨** → 검증된 송신측 자산 복구 가능.
- **기술적 본질**: foreground 밖(화면 off OR 다른 앱)에서 볼륨키 캡처는 *전역 후크*만 가능 — 더 가벼운 Android API 없음. "화면만 키기"·"백그라운드만"도 같은 후크 필요(샷컷 없음).
  - **Accessibility** (`FLAG_REQUEST_FILTER_KEY_EVENTS`+`onKeyEvent`): 신뢰성↑·OEM무관, 단 1회 설정 권한 + 삼성 절전이 끄는 재허용 마찰.
  - **MediaSession 원격볼륨**: 특수권한 0(가벼움), 단 활성 세션 충돌(운전 중 내비·음악)·OEM 변동으로 불안정.
  - **블루투스 이어피스 버튼**: 운전 중 정석(Zello 방식), Accessibility 불필요·신뢰성↑, 단 하드웨어 의존.
- **다음 증분(별도 트랙)**: screen-off 송신 복구 — ① dispatchKeyEvent는 foreground 폴백 유지 ② Accessibility Service 추가(과거 `17bcd8b3` 패턴) → PTTManager.start/stopTransmit 연결. 캡처 후엔 *무음 송신*이 운전자에 유리(화면 안 봄)라 "화면 깨우기"는 불필요·열위.

## Out of scope (별도 트랙)
- **PTT 양방향 + 역할 분리**(본인 6/2 결정): call_manager **수신** 추가 + pickup_app **송수신** + sendPttWake 매니저·픽업 토큰 팬아웃. 현재는 *일방 브로드캐스트*. 오버톤은 양방향 시 대답 신호로 살아남.
- functions 1콜 통합 + 토큰 FCM 동봉(수신측 round-trip 제거) / 종료 전용 효과음 리소스 / 시스템 오버레이(앱 밖 배너) / 정산 이월 단순화 / call_detector PTT 연동 / 상태명명 재설계.

## 다음 세션 진입 후보
① master로 PR ② 양방향+픽업 트랙 설계 ③ 정산 단순화 ④ Certificate rotate(실배포 전) ⑤ S22 포함 3단말 E2E + 오버톤 청취 재확인

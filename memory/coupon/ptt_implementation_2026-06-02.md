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

## 3단말 E2E 후속 검증 + 마무리 처리 (2026-06-02 낮 세션) — ⑤ 통과 / ④⑤① 종료
- **단말 재설치**: S22(R5CT41TJZFP) driver_app이 **5/18 구버전**(PTT 수신 코드 없음) 발견 → 최신 install. S21+/Z Flip4도 동일 빌드로 맞춤. gradle up-to-date(소스 무변경) = APK가 HEAD(어제 PTT 커밋) 코드와 동일 확인.
- ✅ **⑤ 1:1 발화 검증 완료** (S21+ 송신 → Z Flip4 수신, logcat 증거):
  - 송신측 PTTManager: joinChannel(155ms) → sendPttWake 완료 → `onUserJoined remoteUsers=1` → `fireReady: TALKING (mic live)`. 콜드 press→ready **~1.6초**(어제 ~1.4초 일치), 2회 발화 모두 정상.
  - 수신측 PttAudioManager(Z Flip4): onWake joinChannel → 수신 join 성공(148ms) → 송신자 입장 → **매니저 발화 시작 감지 → 발화 종료 감지 → 오버톤+5초 재앵커** → 송신자 이탈. **종료 오버톤까지 전 흐름 정상.**
- ⚠️ **fan-out(remoteUsers=2) 미검증 — 버그 아님**: S22·Z Flip4가 **같은 기사 계정**(본인 확인) → `designated_drivers/{authUid}` 문서 1개 → fcmToken을 나중 단말(Z Flip4)이 덮어씀 → sendPttWake가 1토큰만 wake(`remoteUsers=1`, S22 PttAudioManager 로그 0건). 증거 정합: 송신 onUserJoined uid=2025089136 = Z Flip4 join uid. **실운영은 기사별 다른 계정**이라 각자 fcmToken→각자 wake. 멀티캐스트는 FCM 토큰 배열 동일 경로라 **1:1 검증으로 충분 판단**(본인 "1:1로 충분").
- **④ Certificate rotate skip**(본인): 노출 Certificate(`d410...`)로 가능한 건 채널명 추측+토큰 위조 무단참여+Agora 사용량 정도 → 양평 파일럿 위험·비용 무시 가능. **"다수 사무실 실배포 전 1회"** 체크리스트로만 남김(지금 안 함). App ID는 공개값, Certificate만 서버 secret → rotate는 functions secret+재배포만(앱 재빌드 불필요).
- **① master PR 보류**(본인): **master 2025-09-01 방치(9개월), merge-base 2025-07-22**. manager-direct-drive와 **1,970파일 / +367,720 / -5,756** 차이(빌드산출물·APK·functions/lib 포함 추정). 실질 메인 = manager-direct-drive. PTT 3커밋은 **이미 origin push(local↔origin 0/0)로 기록 완료** → ①의 목적(작업 기록 고정) 이미 달성, 유실 0. 9개월 분기 정리는 PTT 마무리와 별개 큰 결정이라 **별도 트랙**으로 분리.
- → **PTT 코드 트랙 종료. "현재 빌드로 양평 실사용 → 데이터로 판단"**([[ptt_screenoff_send_2026-06-02]] §다음) 단계 진입.

## ★ 2026-06-03 본인 결정 — 실사용 데이터 트랙 *보류* + 양방향 송수신 *우선 진입*
- **실사용 데이터 수집 트랙 보류**: 6/2~6/3 "PTT 코드 트랙 종료 → 양평 실사용 데이터로 판단" 단계를 *보류*. 데이터 기반 재결정(트리거·비프모델·버퍼링·screen-off)은 양방향 완성 후로 미룸.
- **양방향 송수신 우선 진입**: 현재 일방 브로드캐스트(매니저→일반기사 수신)를 양방향+역할분리로 확장. 범위 = call_manager **수신** 추가 + pickup_app **송수신** + sendPttWake **매니저·픽업 토큰 팬아웃**. 오버톤은 양방향 대답 신호로 살아남(설계 이미 정합).
- **기점**: `ptt-coldstart`(= manager-direct-drive 라이브 + 콜드 8commit, origin push됨). 양방향은 이 위에 쌓아 분기 회피.
- 9단계 블랙박스 9-A(발화 기록)는 양방향 완성이 선행 → 양방향 후 블랙박스 진입.

### 양방향 1단계 구현 완료 (2026-06-03, push+배포+S21 install) — E2E 물리검증 대기
- **plan**: `C:\Users\kala1\.claude\plans\majestic-swinging-gosling.md` (검토로 누락 2건/오염 1건 보강). 브랜치 `ptt-coldstart`.
- **commit 3개 push**: ① `96142111` functions sendPttWake/PreWake 3컬렉션 팬아웃(collectOfficePttTokens 헬퍼 = managerTokens+designated+pickup, 발신자 제외, 에코 1차 차단) ② `d5c889d0` call_manager 단일엔진 송수신 통합 ③ `4a1a32ad` fix(pttManager by lazy 크래시 해소).
- **단일엔진 통합 (Agora 싱글톤 제약)**: PTTManager에 수신 흡수 — EngineMode(TX/RX) 플래그로 eventHandler 분기(onRemoteAudioStateChanged/onUserOffline은 `mode==RX` 가드) + LISTENING 상태 + onWake(수신 fast-join, mic 미publish/enableLocalAudio(false)) + 송신 전환 시 enableLocalAudio(true) 복구 + scheduleLeave 공용. 신규 PttReceiverService(ptt_dispatch→onWake 화면off 수신 + 콜드 음성메모 playVoiceMemo, ★onDestroy release 금지). MyFirebaseMessagingService ptt_dispatch/prewake 분기 + alwaysProcessTypes + handleChatMessage 콜드 음성메모 자동재생. CallManagerApplication 단일 pttManager(by lazy). manifest FOREGROUND_SERVICE_MICROPHONE + PttReceiverService.
- **검토 보강(승인 거부 후 "누락 오염 검토")**: ① [누락] call_manager handleChatMessage 콜드 음성메모 자동재생 0였음 → driver_app 패턴 이식(functions는 audio 필드 이미 송출 chat.ts:215) ② [누락] PttReceiverService 라이브+콜드 둘 다 ③ [오염] PttReceiverService.onDestroy release 금지(driver DriverForegroundService:194와 다른 점 — 단일엔진 공유).
- **functions 배포**: sendPttWake/sendPttPreWake production(asia-northeast3, --force minInstances:1).
- **단말**: S21+(R3CR312MB1L) call_manager install 성공, 시작 크래시 fix 후 정상(RtcEngine created). driver_app/pickup_app 무변경(회귀 0).
- **★ 크래시 학습**: Application 필드 `val x = Manager()` 즉시 생성은 Application 인스턴스화(onCreate 이전, FirebaseApp 미초기화) 시점 실행 → 생성자가 Firebase 접근하면 크래시. by lazy로 첫 접근 지연. (MainActivity 소유 땐 onCreate 이후라 무사고였음 — Application으로 승격 시 초기화 타이밍 함정).
- **E2E 물리검증 대기**: A(단일엔진 무결성 logcat RtcEngine 1회) / B(회귀 — S21 매니저→Z Flip4 driver 수신) / C(매니저 수신 — 매니저 2대 필요, S22 미연결). 본인 결정: C는 2단계(pickup 송수신)와 합쳐 픽업→매니저 진짜 양방향으로 검증.
- **2단계 (다음)**: pickup_app 송수신 fork (Agora SDK + 통합 PTTManager fork + ptt_dispatch 분기 + 수신 FGS + RECORD_AUDIO + 볼륨버튼 + 배너).

### 양방향 2단계 구현 완료 (2026-06-03, push+빌드+install) — E2E 물리검증 대기
- **plan**: `C:\Users\kala1\.claude\plans\majestic-swinging-gosling.md`(2단계로 갱신). 커밋 7개 push(`bfeb6a57`~`35d12e33`).
- **commit 0 `bfeb6a57`**: call_manager PttReceiverService microphone FGS 권한 가드(검토 보강) — 수신/재생은 마이크 미사용 → RECORD_AUDIO 미허가 시 type 생략(SecurityException 크래시 회피). 픽업 fork 전 원본 정합.
- **commit 1~6 (pickup)**: ① Agora voice-sdk:4.5.2 + firebase-functions 의존성 + RECORD_AUDIO/FGS_MICROPHONE 권한 + PttReceiverService 등록 ② service fork(PttRecorder 복사 / PTTManager senderName "픽업기사" / PttReceiverService prefs키 픽업·권한가드) + PickupDriverApplication getInstance+pttManager by lazy ③ Room v3→v4 무손실 MIGRATION_3_4(chat_messages audio 4컬럼, calls 보존) + ChatMessageDao.markAudioSent ④ audio 송수신(ChatRepository uploadChatAudio/sendAudioMessage senderRole=PICKUP_DRIVER + onRemoteMessageReceived/loadInitialMessages audio 파싱) ⑤ FCM ptt_dispatch/prewake 분기 + handleChatMessage audio 자동재생 ⑥ MainActivity dispatchKeyEvent hold-to-talk + 콜백(콜드 송신 ChatRepository 직접·Hilt 우회) + getOfficeInfoForPtt(픽업 prefs) + 배너.
- **검토 보강 2건(승인 거부 후)**: ① [오염] PttReceiverService microphone FGS 권한 가드(수신은 마이크 미사용·권한 거부 픽업 크래시 회피, call_manager 원본도 commit 0) ② [오염] 픽업 ChatViewModel.sendPttVoiceMemo dead code — Hilt 스코프상 MainActivity→ChatRepository 직접이라 미추가.
- **핵심 구조 차이 처리**: ① Hilt — 픽업 @HiltAndroidApp + ChatViewModel hiltViewModel(NavBackStackEntry 스코프) → 콜드 송신 @Singleton ChatRepository 직접 주입 우회, PTTManager는 PickupDriverApplication.getInstance() 비-Hilt 싱글톤 ② prefs 키 — 픽업 pickup_driver_prefs/pref_province_id(call_manager login_prefs/provinceId와 다름) PttReceiverService·getOfficeInfoForPtt 정합 ③ Room 단일 DB(chat+calls) destructive → v3→v4 + addMigrations 둘 다.
- **빌드+단말**: pickup BUILD SUCCESSFUL(6분, Agora 첫 다운로드) + Z Flip4(R3CT80K78NP) install 정상 시작(by lazy + Room migration 크래시 0). call_manager 권한가드 재빌드 + S22(R5CT41TJZFP)/Z Flip4 install. driver_app은 PICKUP_DRIVER→"픽업기사" 매핑 보유라 픽업 발화 수신 회귀 0.
- **E2E 물리검증 대기**: 픽업↔매니저 양방향(픽업 발화→매니저/기사 수신 / 매니저 발화→픽업 수신 / 콜드 음성메모 양방향). 단말 S21+(매니저,현 미연결)/Z Flip4(픽업)/S22(매니저 or 기사). 1단계 검증 C(매니저 수신)도 이 단계서 함께.
- ✅ **2단계 E2E 양방향 1차 통과(6/3 낮)**: 픽업↔매니저 라이브+콜드음성메모 양방향 로그 확인(15:25~26). 단일엔진 RX→TX 전환·prefs 정합 정상.

### 콜드스타트 "원활통신" 재설계 — 수신 무음 회귀로 롤백 필요 (2026-06-03, 종료 체크포인트)
- **plan**: `C:\Users\kala1\.claude\plans\majestic-swinging-gosling.md`(콜드스타트 재설계로 갱신). 브랜치 `ptt-coldstart` 최신 `24504d33`.
- **본인 지침**: "제일 중요한 건 최대한 원활한 통신, 원칙(listener최소화/비용)은 수단."
- **재설계 commit** `f0a8d60b`(PTTManager WARM+RX→TX 즉시전환+콜드녹음제거+워밍창45s) + `b1a1cc2e`(MainActivity 콜드판정 제거) + `24504d33`(wip: enableLocalAudio 토글 제거).
- 🔴 **회귀 버그(미해결)**: WARM 채널 재사용 시 매니저/픽업 **상호 수신 무음**(콜백 `수신: 발화 시작 감지`는 옴, 오디오 X). driver_app(매번 신규 join)은 정상. enableLocalAudio(false) 제거해도 무음 → **WARM 채널 재사용(updateChannelMediaOptions pub 토글 포함)이 Agora remote 수신을 깸**. 첫 E2E가 됐던 건 워밍창 5s 짧아 실질 매번 신규 join이었기 때문.
- ★ **다음 세션 진입(롤백)**: WARM/워밍창연장(45s)/updateChannelMediaOptions 재설계 **롤백** → 2단계 검증 동작(매 발화 신규 join, 수신 항상 정상) 복원 + **콜드녹음(음성메모) 폐기**(본인 의도 아니었음 — [[feedback-cold-voicememo-unintended]]). 결과 목표 = 첫 E2E "잘돼" 양방향 + 콜드녹음 4~6s 지연 제거(cold-live ~1.4s, 2차비프 가림). 워밍창 0초 욕심은 수신을 깨므로 포기.
- **★ 콜드 음성녹음 = 클코 임의 도입(본인 의도 아님)**: 6/2 콜드 hold 문제를 클코가 음성메모로 임의 해결. 본인은 라이브 무전 원함(녹음 전달 아님). 음성녹음 트랙 폐기 방향. [[feedback-cold-voicememo-unintended]].
- **단말 상태**: S21+/S22/Z Flip4에 WARM 재설계 빌드 설치됨(수신 무음 버그 버전). 롤백 빌드로 재설치 필요.

## 다음 세션 진입 후보 (PTT 코드 트랙 종료 후) — ⚠️ 실사용 데이터 트랙은 위 6/3 결정으로 *보류*
- ~~양평 실사용 데이터 수집~~ → **보류**(6/3). 양방향 완성 후 재개.
- 코드 후보: **② 양방향+픽업 트랙 ← 6/3 우선 진입** / ③ 정산 단순화 / master 9개월 분기 정리(별도 세션) / Certificate rotate(다수 사무실 실배포 직전).

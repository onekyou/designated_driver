# 콜마당 — 양평 동네 생활 OS

> ⚠️ **메모리 저장 정책** (CLAUDE.md §메모리 저장 정책 참조)
> - **단일 원본**: `C:\Users\kala1\designated_driver\memory\` (git 추적)
> - **B 폴더** (`.claude/projects/.../memory/`)에는 **MEMORY.md만 존재**
> - **도메인 폴더 안 생성 원칙**: 새 토픽 파일은 해당 도메인 폴더 안에 (예: `memory/designated_drive/`, `memory/restaurant/`)
> - 이 MEMORY.md 편집 후 B로도 동일 내용 복사: `cp B/MEMORY.md A/MEMORY.md`

> ★ **결정은 먼저 결정 대장 확인**: `memory/_decisions.md`(전역) + `memory/<도메인>/_decisions.md`. `[확정]`은 변경시 입증 필요 — 서사에 묻힌 결정을 재해석/뒤집기 금지. (SessionStart hook이 자동주입하나, hook 없는 환경 대비 포인터.) 정책 = CLAUDE.md §결정 기록 정책.

> 🟥 **폴더 작업 규율 (2026-06-10 명시·2026-06-11 재확인·반드시)**: **이 폴더(designated_driver)에서는 특별 지시가 없는 한 대리운전 앱만 다룬다.** 본류(통화예약·미용·식당)는 **다른 폴더(coupon_app)에서 진행 중**. "오늘 할일?"에 미용실/본류를 끌어오지 말 것 — 여기 제시 후보 = 대리운전 항목만. 통화예약 엔진 자산은 부품창고로 보존만. (CLAUDE.md 궁극방향 L4 + `feedback_folder_scope_before_proposing_2026-06-10`.)
> 🚩 **현 대리 방향 = 보급 준비 (2026-06-11 원규씨 지시)**: 대리 "도구" 설계 ≈8~9할 완성(콜운영·PTT통신·콜드단축·배차알림·블랙박스 9-A/9-B·PTT→텍스트·정산단순화 본체 다 섬). **✅ 6/11 완료**: push·수동마감 봉인(`472e16fa`)·functions deploy(LIVE 정합). **✅ 6/12 완료**: 정산 P7 게이트 #1~4(`54e163d0`)·정산 **업무마감 배너문구+미확인정산 보존**(`82e71ced`, 게이트#4 누수차단)·**PTT→텍스트 pickup 포팅**(미커밋 — whisper JNI/.so/PttTranscriber 이식+STT조각+sendPttText+세로고정portrait+small모델·S22 install, call_manager 동등). **✅ 6/13 완료(push `7f346af4`)**: **알림 정리 3앱** — ptt효과음 PTT전용 격리·나머지 기본알림음 통일(채널ID 버전업, dumpsys 실측) / 콜매니저 표기 축소(**새콜 무음**·기사 ON·OFF·운행시작·완료만 알림, 콜취소·기사승인·신규회원·전달실패·업무마감은 기본음 유지) `4f625eed` / 픽업·기사 알림음 `10982fce`(기사앱은 이미 기본음, 죽은 ptt채널만 삭제) / **픽업 콜표기 = 콜매니저식 12h 롤링 윈도우**(기사 미마감 박제 콜이 생성 12h 후 자동 제외 + 1분 ticker 자동갱신, 10시경계 `fe8ef8d3`→대체 `7f346af4`). 전부 클라전용·functions 무변경. **⛳ 남은 보급 준비**: ~~★알림 표기 억제 로직 런타임 미검증~~ **✅6/15 검증완료**(S21+ `functions/scripts/test-notification-suppress.js`로 type별 data-only FCM 직접전송→6케이스 logcat `CallManager_FCM` 실측: 새콜·WAITING·IN_PROGRESS=무음(`알림 생략(데이터만)`) / ONLINE·OFFLINE·취소=기본음(`showNotification 호출 시작`) = 설계대로. dumpsys+12h DB 실측에 더해 억제동작 닫힘 → 남은 건 실 파일럿폰 설치뿐) · 실 파일럿폰 설치(개발 3단말만 설치됨) · 9-B Commit 2 실콜검증 · 공유콜 잔여 3트리거 · ~~★PTT 전사 지명 가제티어 보정~~ **✅6/15 1차완료**(지명만 — `GazetteerCorrector` util 두 앱, `settings/gazetteer.places` 사전+자모거리+"헷갈리면 보존", 어절 접두매칭으로 조사 보존. 실폰검증: '양평력으로'→'양평역으로' 교정·오교정0·정상어보존·픽업 사전로드OK. rules `settings/gazetteer` read 완화 배포. 한계=whisper 2자모 오전사("양통")는 보존(미교정)=whisper품질 별트랙. 기사명교정·콜자동축적·비행정지명시드=후속. 상세 결정대장 PTT→텍스트 항목). · **✅6/15 운행중 PTT음성 수신차단**(대리기사 콜 IN_PROGRESS 중 `PttAudioManager.onWake` join skip=음성 차단·채팅 전사 텍스트는 블랙박스 유지·운행완료 버튼부터 복구. `DriverViewModel` collector가 activeCall.status 관찰→prefs 미러링(취소·재시작 자동). driver_app만·functions 무변경. 실폰 검증 통과. 결정대장 "운행 중 PTT 수신 차단" 항목). screen-off 송신은 유일한 본질 미결이나 보급 준비 우선(원규씨). 상세=결정대장 `designated_drive/_decisions.md` PTT→텍스트 항목·"정산 단순화"·"알림 정리".

## 7/5 ★★ 방향: 공유콜 지역 OS 전환(이익 먼저, 편의 후속) + rules 대봉인(request.auth==null 오해 정정)
- **★ 전환 확정(원규씨)**: 공유콜 위주 앱을 앞단으로(사무실 도입 동기 = 콜/이익이지 편의 아님) → 기존 편의(PTT·정산 등)는 후속 정착층. 콜 올린 사무실이 수수료를 포인트로 회수, 콜마당=전용계좌/환전 원장(구독료 원칙 정합). **5팀 전수 점검 결과: 엔진(게시→수임→배차→완료→10% zero-sum 회수→환전신청 wallet.ts) 이미 end-to-end 구현·작동** — 전환은 신축 아닌 보강. 남은 정책 4 = 잔액게이트·요율(10% 하드코딩)·공유범위(같은 시/도)·충전방식. 상세=결정대장 "공유콜 지역 OS 전환/보안" + 5팀 점검 보고(이 세션).
- **★★ rules 치명 오해 발견·정정(`87b219af` push·배포·실측)**: `request.auth==null` = "CF 전용" 아니라 **비로그인 공개**(Admin SDK는 rules 우회 — 공식문서 확인). 22+곳이 비로그인에 열려 있었음(포인트 자가충전·환전 승인 조작·초대코드 공개rw·admins 공개read 등). allow 분기 25곳 잠금(`if false`, 코드 삭제 0=블라인드)+수임 claimedOfficeId=본인 사무실 검증+emergency_alerts 정상화. 비로그인 REST 부정경로 6/6 차단 실측. **서버 전용 = `if false`가 정석 — "CF 허용하려면 null 추가" 회귀 금지.** ⛳잔여=실폰 회귀(수임 CLAIMED·정산 제출·채팅, 기기 연결 시). 롤백=`firestore.rules.bak-20260705`.
- **위생(같은 커밋)**: settings.local.json 시크릿 라인 삭제+untrack(커밋 이력엔 없었음 — hygiene 오판 git 실측 정정) / **명함주소.txt가 calldetector 호스팅 라이브 공개였음(200)**→OneDrive `콜마당_PII보관_20260705/` 이동+재배포 404 / recordingS(통화녹음 PII)·마늘밭·bak gitignore. 팀 오판 2건 결정대장 기록(detector "회수경로 없음"=잔액게이트 오독 — 실제는 onSharedCallCompleted서 정상 회수).

## 6/19 ★★ 온폰 모델 자동 다운로드 = 원격 보급 케이블 의존 해소 (+ PTT 단일누름 검증 + Gemini vs whisper 측정)
- **★ PTT 전사 모델 자동 다운로드 완성·실폰검증·push `ce661e3b`** — 보급 최대 병목 해소. 온폰 whisper 모델(190MB)을 `adb push` 없이 **Firebase Storage `ptt_models/`에서 첫 실행(로그인) 시 자동 다운로드**. 이제 **호스팅 APK 설치→로그인→모델 자동 다운→PTT 전사→채팅** = 케이블·개발자모드·adb push 전부 불필요. `ModelDownloader`(filesDir 없으면 getFile→크기검증→.part→원자적 rename·멱등)+`ModelDownloadService`(**포그라운드 dataSync** — 백그라운드 네트워크 스로틀에도 완주, 코루틴버전 백그라운드 3.4MB정지→포그라운드서비스로 81MB진행 검증). 두 앱(call_mgr·pickup). WhisperTranscriber 전사경로 무변경. storage.rules `ptt_models/` 인증read 배포. 실폰 E2E(S21·S22): 모델삭제→자동복원(정확크기 190085487+885098)→**발화"수연이 분성으로 가세요" 온폰전사(콜드0·~10초)→가제티어→채팅 무음INSERT** 전 경로 통과. 결정대장 "온폰 whisper + 모델 자동 다운로드" 항목.
- **★ 서버 STT(Cloud Run) 경로 시도→기각**: transcribePtt callable 구현·배포·실폰까지 갔으나 **콜드스타트 75초**(large-turbo CPU)가 Firebase callable 70초 타임아웃 초과→채팅 무반응. 원규씨 "서버가 더 복잡, 온폰 자동다운로드가 낫다"→온폰 채택. `transcribePtt` 함수는 **배포된 채 휴면 보존**(예약엔진 STT 패턴 자산), 앱 ServerTranscriber/swap은 git 원복.
- **★ Gemini vs faster-whisper 측정(실통화 6건)→PTT용 whisper 유지 확정**: 짧은 대리호출(s5)에서 **Gemini 환각 루프**("데려다줄게" 수백회·토큰폭발 24원·구간유실) / whisper 폭주0·우아한오청. 깨끗·긴통화는 Gemini 약우세지만 PTT(짧고 거친 무전)는 whisper. 결정대장 "[확정 보강] PTT 측정". (서버STT도 엔진은 whisper 유지 = 위치만 바뀜, [확정] 위반 아님.)
- **✅ PTT 단일누름(볼륨키 hold-to-talk) 실폰검증 통과·push `c44068b0`+docs** — adb keyevent 스모크+원규씨 손으로 홀드·동시누름 확인. 결정대장 "인앱 PTT 트리거 단일 누름".
- **★ 블랙박스 system 메시지 푸시→풀 전환 완성·실폰E2E·배포·push `c29a1527`**(원규씨 "블랙박스 언제까지 늘어놓을 수 없어 + read 폭발?"). 조사=**read는 이미 효율적**(Local-First Room캐시+신규 FCM push+7일 cleanup, 일상 read≈0). 진짜 비용=`onChatMessageCreated`가 시스템 메시지(콜당~6건)마다 토큰3컬렉션 read+FCM 팬아웃. → **type:"system"만 풀 전용**(서버 `if(type==="system")return` 팬아웃 skip + 클라3앱 `syncSince` 채팅 시트 열 때 증분 풀, watermark SharedPrefs 풀에만 전진·`getMaxCreatedAt` 초기화). ptt·일반채팅=푸시 유지. **★누락검토 성과**: 트리거가 VM.init(Activity 1회)이면 1회만 풀리는 치명버그→시트 expand `LaunchedEffect`로 정정. E2E(S21+): 팬아웃skip·FCM미수신·시트열기 풀INSERT·재열람 신규0(watermark) 통과. **효과=블랙박스 늘려도 비용 불변.** 결정대장 "블랙박스 system 푸시→풀".
- **⛳ 남은 보급 준비**: 현장 클린 재설치(폰 확보 시 — 이제 모델 adb push 불요, APK만) · 9-B Commit 2 실콜검증 · 공유콜 잔여 3트리거. (전사 정확도=가제티어 사전 보강 별트랙.)

## 6/17 ★ 파일럿 사무실 정체성 확정 + 총알대리 가제티어 시드 완료 + 현장 클린 재설치 런북
- **★ 실 파일럿 = `OyLNNY8GbFExPHHLkuMK` ("총알대리", gyeonggi/yangpyeong, status=active, 대리2+픽업2명)** — 원규씨 확인. `nEkf0X9g3LZtRX94Mrzu`(양평 활성)·`RUbeBEvGGYP5wMhJHhMF`(1004)는 **원규씨 테스트용·보급 대상 아님**. 과거 메모리/결정대장이 "양평 실사무실=nEkf"로 보급 대상 오지목 → 정정(결정대장 "사무실 로스터" + 가제티어 보급 인계 줄 갱신).
- **✅ 총알대리 가제티어 시드 완료(2026-06-17)**: `node functions/scripts/seed-gazetteer.js OyLNNY8GbFExPHHLkuMK`(province/city 기본값 gyeonggi/yangpyeong 정합) → before=문서없음→22개 지명 신규생성. 콜 운영 무영향(멱등 merge).
- **★ 파일럿 4앱 업데이트 방식 = 현장 클린 재설치(uninstall→install)** — 원규씨 결정 "(A) 사무실 보존+클린 재설치, 무거운 APK 모델번들은 다음". 근거(코드 검증): ① 4앱 Room DB 전부 `fallbackToDestructiveMigration` → in-place 업데이트도 **크래시 위험 0**(최악=로컬캐시 리셋→Firestore 재동기), 클린 재설치면 마이그레이션 아예 우회 ② **whisper 모델(190MB) 자동 다운로드 미구현**(`PttTranscriber.kt:17` "후속", 모델 없으면 graceful=전사만 비활성·크래시X) → PTT→텍스트 켜려면 **현장 `adb push` 필수**(원격 APK 다운로드로 불가) ③ 모델 APK 번들/다운로더는 **다중 사무실 보급 시**(케이블 없는 원격설치 필요해질 때) 착수.
- **현장 런북**: 0)폰3대(매니저/픽업/대리) 1)기존앱 삭제 2)새APK 설치(매니저=call_detector+call_manager/픽업=pickup/대리=driver) 3)**모델 adb push=call_manager·pickup만**(driver=수신·렌더만 모델불요) — `ggml-small-q5_1.bin`+`ggml-silero`VAD→filesDir(`MSYS_NO_PATHCONV=1` 주의) 4)각앱 사무실 로그인(officeId 재기록) 5)시드(✅완료) 6)검증=PTT음성+전사+테스트콜1건. ⚠️PTT앱 폰당1개·같은office채널.
- **APK 현황(6/17 대조)**: 4앱 산출물 전부 "빌드→폰검증→커밋" 패턴이라 내용상 현재 코드 일치(call_manager·pickup APK가 가제티어 커밋 18분 전이나 working tree 빌드라 코드 포함). 그래도 **방문 직전 4앱 클린 재빌드 권장**(싸구려 보험). 브랜치 `feature/reservation-engine-poc` 미푸시 0·미커밋 코드 0(전부 push됨).
- **남은 보급 준비(총알대리 한정)**: 위 현장 설치(폰 확보 시) · 9-B Commit 2 실콜검증 · 공유콜 잔여 3트리거 · (다중 보급 시) 모델 APK 번들.
- **★ PTT 트리거 단일누름 변경(6/17, call_manager+pickup, 미커밋→이번 종료 커밋)**: 더블탭 폐기 → **볼륨키(업/다운 무관) 누르기 시작=즉시 발화, 떼면 종료**(동시누름 안전). `dispatchKeyEvent`에서 `pttFirstTapUpTime`/`pttPendingFirstTapDown` 제거+`pttPressedKeys` 집합 도입. 오염검토 통과·컴파일/재빌드 통과(call_mgr 17:27·pickup 17:29 APK). ✅**실폰 런타임 검증 통과(6/19)**: S21+(call_mgr)·S22(pickup) install→adb keyevent 스모크(업/다운 둘 다 시작/종료 1쌍·중복0 로그 실측)+원규씨 손으로 홀드·동시누름 직접 확인 통과 → push `c44068b0`(코드)+`15bb3c8c`(docs). 부작용=MainActivity 화면서 볼륨 소리조절 불가(전용폰 OK). 상세=결정대장 "인앱 PTT 트리거 단일 누름".
- **4앱 APK 현황(6/17)**: call_manager 71MB·pickup 49MB = PTT변경 재빌드 / call_detector 34MB·driver_app 156MB = up-to-date(코드 현재). 전부 방문 install 준비됨(방문 직전 재빌드는 선택).

## 한 줄 요약
콜마당 = 양평 동네 생활 OS. 대리는 첫 사용 케이스. 식당 = 4중 노드 (호출/포인트/거점/쿠폰).

## 진입점 (최우선 2개)
- **마스터 (정체성·아키텍처·R1~R8 리스크)**: `memory/callmadang_master_2026-04-27.md`
- **체크리스트 (매일 도구)**: `memory/callmadang_checklist_2026-04-27.md`

## 현재 위치 (2026-04-27)
- 양평 1개 사무실 파일럿 운영 (designated_drive)
- 마스터·체크리스트 정리 완료 + 메모리 OS 레이어 재구조화 완료
- **디버깅 우선순위 1~5 (마스터 §13.3)**: 식당 누름 빈도 측정 → 인큐베이션 사무실 1곳 → 직접 설치 → N=1 영상 → 정부 지원사업

## 6/4 ★ 대리운전 = 쿠폰앱 모듈 흡수 방향 확정 (독립 앱 폐기) — 본 세션 코드 0, 본문은 쿠폰앱 독립 프로젝트로 인계
- **방향 확정**: 대리운전을 독립 앱으로 키우지 않고 **간소화해 쿠폰앱 모듈로 흡수**. 처음 질문("대리를 쿠폰앱 모듈로") → 6/3 영업 발견 거쳐 확정. [[pivot-2026-05-19]] 흡수 + [[call-broker-bundle-2026-05-25]] 협상카드 재정의의 귀결.
- **"간소화" 정의**: ① 양평 기존 5앱 = 손대지 않음(동결 보존·영업 카드용) ② 쿠폰앱에 "기사 역할" 한 층을 **얇게 신규로** 신축(driver_app 이식 ❌). 본인 흡수 규율("도장만큼 쉬울 때만") 통과 조건.
- **6/3 영업 발견**: 모든 업종 공통 니즈=손님. 대리·택시 기사=**움직이는 도장 거점**. 동기=**지명(단골)**, 할인은 미끼. "지금이라도 사용 가능"=현장 수요 신호. 가져올 것=도장+할인발급+지명끈 / 버릴 것=배차·정산·콜디텍터·PTT.
- **토대 준비됨(쿠폰앱 코드)**: 이벤트에 `participants.driver?` 슬롯 이미 비어있음(토대 안 뜯고 도장 이벤트 기록 가능). 끈(`Bond`)=customerId↔shopId뿐 → **유일한 새 조각=`Bond`에 `targetType:"shop"|"driver"` 한 갈래**. 6/2까지 손님 전 루프 닫힘(카카오OAuth·NFC실측·쿠폰사용재설계 production 라이브).
- **다음(쿠폰앱 독립 프로젝트)**: 기사 모듈 플랜(역할 진입/Bond 지명갈래/손님 재호출=매니저 안 거치는 가장 가벼운 형태) → **N=1=기사 1명**. 할인 재원은 그다음. 본 designated_driver 5앱 안 엶.
- **상세/인계**: `memory/coupon/driver_module_pivot_2026-06-04.md`. 본 결론은 **메인 세션(하나의 자리)** 에서 본격 의논 예정(6/4 명시). 본문 상세는 쿠폰앱 독립 프로젝트 메모리에 기록(분기 방지), 그 경로는 본 세션 미확보.

## 6/3~04 ★★★ 콜캐치 권한 우회 = 연락처 확보(녹음 파일명) — 파싱은 확장 레이어 (본인 "획기적 전환", 실측+정책 GREEN)
- **★ 중심(6/4 본인 정정)**: 알맹이는 *파싱이 아니라* **연락처 정보를 `READ_CALL_LOG` 없이 얻는 것**. 콜캐치 목적 자체가 번호 확보였고 그게 사이드로드 감옥 원인. 삼성 자동녹음 **파일명에 번호 박힘** → `READ_MEDIA_AUDIO`(일반권한)로 읽으면 끝. 미지발신자=raw번호✓(배차 대부분)/저장연락처=이름(번호는 READ_CONTACTS로, 코어엔 불필요)/번호숨김=불가(READ_CALL_LOG도 불가). 코어=**블랙박스(녹음)+번호(파일명)+PTT(음성 직접전달)**, 권한 0·로컬우선.
- **파싱(STT→AI→14필드)=확장 레이어**: 폭넓은 확장성(자동티켓·사후검색·손님이력) 주지만 라이브 루프엔 불필요. 블랙박스가 정보 유실 0 만들어 파싱이 "캡처"→"사후검색"으로 강등.
- **배포 차원 해제(진짜 무게)**: 사이드로드 감옥 → **Play 정식 배포**. 정책 실독 GREEN: 통화녹음금지(2022)=녹음행위만, Call Log=READ_CALL_LOG만, 미디어제한=IMAGES/VIDEO만(AUDIO 제외). 회색지대 1=파일명 번호추출(Data Safety 선언). iOS=콜매니저 안드로이드 전용이라 비이슈.
- **S21+ 실측(Android15/OneUI7)**: `/sdcard/Recordings/Call/*.m4a` + MediaStore 양쪽 인덱싱 + 파일명 번호·시각. 가독✅(SAF 폴백). **STT 데모**: 보람푸드 녹음 1건 faster-whisper(small) 전사 성공 — 지명 후보(센트롤시티·아신길) 추출, 출발/목적·콜종류는 21초 단편이라 추정. 핵심=녹음→STT→AI파싱 실데이터 작동 확인. (HF 대용량모델 throttle로 small까지만, large/클라우드면 더 정확.) 온디바이스 STT 경로=Android13+ EXTRA_AUDIO_SOURCE(파일 입력) or OneUI 내장 전사.
- **비용/로컬**: 콜당 ~5원(로컬 STT+AI)~45원(클라우드). 폰 내장 STT+온디바이스 소형LLM이면 완전 로컬·0원. 단 S21+/S22는 Gemini Nano 미지원→앱에 모델 번들 필요.
- **남은 미지수**: ① STT/AI 정확도(large-v3/클라우드 재측정) ② 온디바이스 소형LLM 파싱 정확도(로컬우선 성립?) ③ 실제 Play 심사+Data Safety. **정식 설계 시 코어 vs 확장 분리**.
- **상세**: `memory/designated_drive/call_recording_ai_parse_2026-06-03.md` / 세션요약: `memory/designated_drive/session_2026-06-04_call_recording_bypass.md` / 클코 학습: `memory/feedback/feedback_rabbithole_scope_2026-06-04.md`

## 6/7 통화녹음→예약 입력엔진 검증 슬라이스 플랜 (콜매니저 검증→쿠폰앱 salon_booking 이식)
- 6/3 권한우회의 다음 단계 = "전화로 오는 모든 아날로그 예약(미용·대리·택시)" 공통 입력엔진. 통화녹음→**싼STT(W)** vs **오디오직접(C)** 2경로를 Gemini로 채점해 "W가 C천장에 근접하나" 측정. ★첫발=**측정엔진(Gemini W/C 함수) 먼저**, 온폰STT는 "W충분" 확인 후(EXTRA_AUDIO_SOURCE 불확실·MLkit GenAI는 S21/S22불가·온폰Whisper 무거움 — STT출처=production최적화지 측정게이트 아님). 로마 계약(salon_booking·booking.request·SalonBookingPayload)=coupon_app/app/lib/types.ts **반영완료**. 역할: 코딩=콜매니저폴더, 클코=설계조언+기록. 🚩선결=타깃살롱 안드로이드+자동녹음 비율 현장실사.
- **★ 전략(거인과의 싸움, 6/7 사적대화)**: 도장+통화예약 둘다 **사용자행동0** = 거인(KT통화비서·네이버)이 규모→표준화→사용자행동요구 구조라 *양립불가*한 단 하나의 비대칭=돌팔매. 해자는 LLM(KT-MS GPT-4o 보유)·"수수료0"(거인 미끼우회)·"귀속"(상인 언어 아님)이 아니라 **행동0 + 이익언어(단골재방문/노동0/수수료0) + 정면 안붙고 선점**. 명중조건 3=통화예약정확도(헛예약0)·도장 NFC체감0·거인 오기전 선점속도. KT AI통화비서=봇응대(손님행동↑·단순화한정), 우리=사람통화유지+사후파싱(관계보존). 3년 5앱=헛발질 아닌 부품창고+"행동0" 도달의 토대. 최초질문답=무조건❌ 통한다✅(변수는 "완성"=정확도·체감0·속도 셋, 다 실행에 달림).
- **상세**: `memory/designated_drive/reservation_engine_plan_2026-06-07.md` / 전략: `memory/designated_drive/reservation_engine_strategy_2026-06-07.md`

## 6/7 (저녁) ★★ W경로 측정 실행 = 통한다(강하게) + 써머리확인 UX 확정 — 코드 3개 미커밋
- **측정 결과**: faster-whisper(large-v3-turbo) 전사 → Gemini(2.5-flash) 텍스트채점, **10건 양역할 녹음 10/10 정확 · intent 10/10 · 거짓양성(헛예약) 0건(최중요) · 시각정규화 정확 · 업종자동판별 정확 · 콜당 ~1원**. ★**가설 실증**: 07번 STT "옥천명"(오전사) → Gemini가 맥락으로 "옥천면" 복원 = "STT 좀 틀려도 AI가 극복"(싼길 근거). 단 본인 양역할=매끈=천장편향 → 점수는 상한해석, 다음=현장 거친샘플.
- **UX 확정**: 통화끝 → **자동 "분석중" 배너(탭0)** → 써머리 자동출현 → **[확인] 한 탭** → 예약등록(수정가능). 파싱→바로스케줄 ❌(헛예약 치명, 확인=정확도 안전장치+신뢰부트스트랩, "확인"은 검토라 행동0 수렴). "분석할까요?"팝업은 빼고(매통화 묻기=행동↑) 프라이버시 스위치로만. 비예약은 써머리에 안 띄움. 신뢰 쌓이면 점진 자동화(확신95%+ 자동등록).
- **코드자산(functions/scripts/, 미커밋)**: 신규 `transcribe-whisper.py`(폴더일괄·폴백사다리), 신규 `score-batch.mjs`(폴더 W채점·파일명→recordedAt), 수정 `test-reservation-standalone.mjs`(W·C나란히·토큰단가). 대본+정답=`OneDrive/Desktop/통화측정_대본_2026-06-07.md`. 녹음=`call_manager/recordings/`.
- **⚠️환경**: 로컬PC Vertex호출 node = `dangerouslyDisableSandbox:true` 필수(아니면 HEADERS_TIMEOUT). C경로(오디오업로드)는 로컬PC 헤더타임아웃(884KB→us-central1/global 둘다 실패)=로컬한정(production GCP내부망 무관). W경로(텍스트)는 정상 1.5초. 속도: PC CPU turbo 29초통화→전사66초(실서비스엔 클라우드/GPU/작은모델로 5~30초, 백그라운드라 체감0).
- **상세/인계**: `memory/designated_drive/reservation_engine_measurement_2026-06-07.md` (다음단계는 6/8 재조정으로 갱신 — 아래 6/8 항목 참조)

## 6/8 ★ 다음 할일 재조정 + 비전 정정 (CLAUDE.md 최상위 박음, 두 repo 푸시)
- **★ 다음 할일 = 콜매니저(대리)에 통화예약 엔진 붙이기 = 첫 실전 파일럿** (플랜모드 설계부터). **W경로 확정**(C vs W 끝 — C후퇴 금지), STT=**서버STT**(앱 가벼움, 온폰 STT통합은 나중). 단계: parseReservation deploy(+Vertex IAM)→콜매니저 reservation 골격(현 미작동) 실연결→써머리확인UX(통화끝→자동배너→[확인]한탭→콜입력)→기존 콜워크플로(NewCallInputDialog) 접점→**양평 대리통화 현장검증**(거친샘플·GO바 여기서 확보). 거친현장은 PC자가샘플 아닌 실파일럿에서만 나옴.
- **세 트랙 = 엔진 하나 + 세 표면**: 대리(현장 성숙)→택시(같은 출발·도착·요금, 공짜)→미용(화면만, 도장·손님거점 나중). 분산 = "엔진 세 번 만드는 중복"만 막으면 됨.
- **★ 비전 정정**: 최종 = 손님·상인 둘 다 **앱**. 웹앱 = **완충지대**(NFC→웹 마찰0 진입→앱 정착). **행동0 ≠ 앱 회피**(앱 최종이되 그 안 노동0). 클코가 "비-앱"으로 거꾸로 읽은 것 정정 + coupon_app 기록공백("웹앱"까지만 명시) 메움. 🟡미정=웹앱→앱 전환 트리거.
- **궁극 위계**: 로컬마루(coupon_app)=최상위 OS / designated_driver=검증장+부품창고 / 통화예약=사장측 행동0 축(도장=손님측 짝). 두 폴더 오갈 때 "콜매니저 최상위" 착각 금지. → designated_driver/CLAUDE.md 최상위 + coupon_app/CLAUDE.md "제품형태" 박음.
- **커밋/푸시**: designated_driver `8db987c5`(CLAUDE.md)+`e61f0a0d`(측정코드3+measurement메모리) / coupon_app `bab976f`(CLAUDE.md, feature/reservation-contract).

## 6/8 (밤) ★ 파일럿 코드+배포 완료 — 작동 게이트=원규씨 IAM 2개 + 폰 검증 (다음 세션 픽업)
- 위 "다음 할일"을 플랜모드 설계→구현→production 배포까지 완료. 플랜 `~/.claude/plans/jolly-meandering-firefly.md`, 브랜치 `feature/reservation-engine-poc` push(`39c056b0`, 6커밋).
- **W경로+서버STT 구현**: 서버STT=**faster-whisper(검증자산) Cloud Run**(`transcribe-service-60275310305.asia-northeast3.run.app`). Google STT는 .m4a미지원·한국어미검증이라 안 씀(통념회귀 교훈). `stt.ts`=교체지점, `reservation.ts` mode="W" 분기(레거시 보존). 콜매니저 `ReservationToCall`(매퍼, trip_summary/isSummaryConfirmed 안 건드림=정산오염방지)+`ReservationConfirmDialog`([확인]=콜생성)+`ReservationInbox`+설정진입.
- **배포 완료**: Cloud Run ✅ / functions `.env`+parseReservation ✅ / 콜매니저 APK S21+ install ✅.
- **⛳ 미완(작동 게이트)**: ① 🚩원규씨 IAM 2개(run.invoker+aiplatform.user — 클코는 가드레일로 IAM 자가확대 불가) ② 폰 검증(설정→통화로예약입력→탭→써머리확인→콜생성, rawTranscript·헛예약0·GO바) ③ 선결: 양평 매니저폰 실손님 자동녹음 쌓이는지(6/7 10건=자가녹음 천장편향).
- **상세/인계**: `memory/designated_drive/reservation_engine_pilot_deploy_2026-06-08.md`. 클코 학습 2건: `feedback_overask_confirm_2026-06-08`(과잉질문 금지)·`feedback_convention_over_verified_2026-06-08`(검증자산>통념).

## 6/9 ★ 폰 검증 통과(파싱 정확) + 단골=저장연락처 정정 → READ_CONTACTS 폐기, 번호출처=업소DB
- **작동 게이트 클리어**: IAM `aiplatform.user` 부여(run.invoker는 프로젝트레벨 기보유) + **"녹음 업로드 실패"=storage.rules 미배포**(6/8 functions만 배포) → `firebase deploy --only storage`로 해결. W경로 end-to-end 실작동.
- **폰 실연 결과**: 출발·목적지·시각·업종 **다 정확 + STT p=1.00 + 헛예약0**. 미충족=전화번호뿐. **속도**: 웜 18초녹음→~30초(CPU 전사가 병목, 콜드스타트 아님). production GPU면 ~5초+백그라운드라 체감0 → 검증 결론에 무관.
- **★ 전화번호 = 6/3 전제 뒤집힘**: 번호는 STT 아닌 **파일명**에서 나오는데, 삼성은 저장연락처면 '이름'·미지발신자면 'raw번호'를 박음. **원규씨 정정 "대부분 단골(저장된 이들)이 콜"** → 6/3 "미지발신자 다수" 틀림 → 다수 콜은 파일명에 이름만.
- **READ_CONTACTS 폐기**(사실확인): Play 2026.4 Contact정책 — Contact Picker(매번 탭)가 기본 대안, 자동 백그라운드 조회(우리 용도)=가장 거절나기 쉬운 칸. **행동0이 곧 승인의 적**. 6/3 "권한0" 본능이 (다른 이유로) 옳았음. → 대체 = **업소 고객DB(귀속) 이름매칭 + 첫통화 1회 보정 시드**(폰 연락처 아님, 귀속 해자 강화). 구현은 GO 후.
- **남은 진짜 GO바**: 양평 매니저폰 **실손님 거친 통화**(6/7~9는 자가녹음 천장편향). 파일럿은 현 상태 검증 계속(번호는 사장 [확인] 보정). **← 최우선(field).**
- **★ 온폰STT 검증을 *지금* 병렬 측정으로 승격**(6/7~8 "온폰=나중/최적화" 갱신): 천장 확인 → 다음은 실제 골격(PII-안전·행동0). 베팅("STT틀려도 Gemini복원")을 더 나쁜 온폰STT로 돌려 진짜 입증. 엔진=**whisper.cpp(ggml)**=faster-whisper와 **같은 모델 weights·다른 런타임**(구글류 모델교체 아님=[확정] 위반 아님). S21+(Exynos2100) 측정 프로브. ⚠️병렬 측정이지 실손님 GO바 대체 아님(rabbithole 경계).
- **상세**: `memory/designated_drive/reservation_engine_pilot_verify_2026-06-09.md` · 결정대장 `_decisions.md`([열림] STT위치 = 온폰 지금승격 + 번호/식별 4건) · 온폰 프로브 플랜 `~/.claude/plans/prancy-discovering-spark.md` · 클코 학습 `feedback_verify_before_asserting_2026-06-09`.

## 6/9 (밤) ★ 온폰STT 프로브 실측 완료 + 전략 재정리(온폰 속도≠보편답) — 내일 이어서
- **프로브 실행**(앱 NDK 통합 없이 whisper.cpp CLI를 NDK로 크로스컴파일→`adb shell`로 S21+/Exynos2100 직접 측정 + 6/7 10샘플을 기존 `score-batch.mjs`로 Gemini 채점). 결과: ✅실현성(한국어 전사 OK) / **온폰 turbo=서버 정확도(런타임 등가, "옥천면·토요일" 정확)지만 RTF 4.1×(느림)** / **small RTF 1.24×(빠름)이나 핵심필드 파괴**(옥천면→"5천명", 토요일→"툴", 번호 끝자리 — 단 헛예약0·intent 안전속성은 유지). → "STT틀려도 Gemini복원"은 *복원가능 오류엔 통하나 정보파괴엔 깨짐*.
- **★ 전략 재정리(원규씨 지적)**: "내 폰 빠르게"는 **보편 답 못 됨** — 온폰 속도=기기별, GPU/NPU 가속은 칩마다 별개 백엔드(Mali/Adreno/Exynos/Snapdragon)라 이식 안 됨. 사장폰 제각각+저사양 → 불일치 치명. **보편 STT 바닥 = 클라우드**(faster-whisper 지금/카카오 보류). **온폰 = 미래 베팅(NPU 표준화) or 하이브리드(능력폰=온폰, 약한폰=클라우드 폴백), 보편 베이스라인 아님.** 기기별 속도튜닝 ⏸중단.
- **카카오 STT**: 클라우드 STT 존재(카카오i Cloud General/Custom STT, 파일업로드 지원). 단 클라우드라 PII는 서버와 동일 범주 = 온폰 대안 아니라 faster-whisper와 경쟁. 엔진교체라 [확정] 위반 — 측정 후 판단(보류).
- **내일 최우선 = 🚩양평 실손님 자동녹음(field GO바)**. 파일럿은 서버STT 검증 계속. (선택) 카카오 vs faster-whisper 측정.
- **상세**: `memory/designated_drive/reservation_engine_onphone_probe_2026-06-09.md`. 프로브 자산 `C:\Users\kala1\onphone_stt_probe\` + 폰 `/data/local/tmp/wprobe/`(내일 재사용).
- **클코 학습**: `feedback_generalizability_before_optimizing_2026-06-09`(한 기기 최적화≠보편답, 최적화 전 이식성부터 물어라).
- **누락/오염 점검 반영(세션 말미)**: ① verify메모 "다음"·결정대장 [열림] = 아침"온폰 승격/종착" → 저녁"온폰≠보편"으로 정밀화 명시(재해석 차단) ② 결정대장에 카카오 보류 라인 추가([잠정], 엔진교체라 측정 후 판단) ③ 위 클코 학습 기록.

## 6/9 (밤2) ★★ 방향 전환 — 콜매니저(대리) → coupon_app 미용실 모듈 (통화 자동입력까지 coupon_app 안에)
- **콜매니저 막힘**: 통화종료(IDLE)에서 콜매니저가 **이미 대리 콜을 자동생성**(CallReceiver→CallDetectorService) → 예약엔진 콜과 충돌·중복 + 양평 운영(동결) 깰 위험. + 수동 트리거(매통화 골라 분석)=행동0 위배·매니저가 안 씀. → 6/8 "콜매니저(대리) 첫 파일럿" **폐기**(결정대장 도장).
- **★ 전환**: 통화예약 엔진 = **coupon_app(로컬마루) 미용실 모듈**로 직행. 근거 ① 대리 충돌 회피(미용=백지) ② **미용실 현장 확보**(원규씨가 사장께 선물 약속 = 거친 통화 **GO바 해결**, 양평 실손님 대체) ③ 종착지 직접=이식 단계 생략. [[reservation_engine_measurement_2026-06-07]] L77 "엔진은 로컬마루 앱 모듈로 이식" 이미 박혀 [확정] 위계 위반 아님(앞당김).
- **엔진/표면 분해**: 엔진(통화캡처+STT+파싱)=designated_driver 검증 부품. **손님 표면**(예약 카드)=coupon_app 웹(`/me` BondCard). **사장 표면**(통화캡처·확인·스케줄러·알림)=**사장용 네이티브 코틀린 앱**(`android_ref/` 직접 완성, 웹 래핑 아님 — 통화캡처 네이티브 필수 + 코틀린 자산 보유). STT=W경로 local-maru 재배포. (2026-06-09 정정: 'Capacitor 하이브리드'→사장 네이티브 코틀린/손님 웹 분리.)
- **플랫폼 사실**: coupon_app=`local-maru`(별도 Firebase, designated_driver=`calldetector-5d61e`와 다름→STT 재배포 필요) Next.js 웹+PWA, Capacitor 미도입. salon_booking 계약(`types.ts` SalonBookingPayload)·`/me`·`/shop`·BondCard 토대 있음. BUILD_PLAN §9.3 "미용 통화 예약 비서=안드로이드 전용" 이미 메모.
- **자산 복사**: designated_driver 엔진 부품(STT 백엔드·통화캡처 코틀린 참조·검증기록 6·측정샘플) → `coupon_app/reservation_engine/` 복사해 거기서 통합(크로스 Firestore보다 단순). 원본 보존(designated_driver=부품창고).
- **6/8·6/9 "콜매니저 첫 파일럿/양평 실손님 GO바" 항목 = 역사 보존, 본 항목이 supersede.** 결정대장 갱신 완료. 플랜 `~/.claude/plans/cheerful-pondering-harbor.md`.

## 6/9 (밤3) ★★ CLAUDE.md 드리프트 교정 — 대리=PTT 복원, 통화예약=미용·식당 한정
- **발견(정독)**: CLAUDE.md 궁극방향(6/8 L5) "통화예약=미용·대리·택시 공통 입력층"이 [[principle_user_action_minimum]](5/25 L15·42) "대리 매니저=STT 화면검토조차 위반, PTT 유일 정합"과 **충돌**. → 6/8이 통화예약을 *대리에까지 잘못 확장*하며 5/25 PTT 결론을 덮은 드리프트. 그래서 6/3~9 대리 통화예약(콜매니저)에 매달려 어제 충돌로 막힘 = 우연 아님(대리는 PTT 자리).
- **정정**: 업종이 도구를 가른다 — **대리·택시=PTT**(위임 대상 기사 있음, 매니저 콜카드 0필드, 양방향 구현됨) / **미용·식당=통화예약 STT**(위임 대상 없고 예약 데이터 추출 목적, coupon_app). 통화예약은 "대리 공통층"이 아님.
- **갱신**: CLAUDE.md 궁극방향 L5 + 전역 `_decisions.md` [확정] 추가. 이번 세션 "대리 통화예약 폐기→미용"(밤2)의 진짜 근거 = 충돌 회피가 아니라 이 드리프트 교정.
- **PTT 구현 실태(코드 확인)**: 통신 인프라 완성(매니저·픽업 양방향, 기사 수신, 알림 Data Push 통합, 블랙박스 9-A 자동재생). ❌미완 = ★PTT 발화↔콜 연결(빈카드 자동생성/음성명령 "○○기사 가") + screen-off 송신(운전중 필수) + 정산 단순화 P5~P8 + 블랙박스 9-B. → **"PTT가 콜 운영을 대체하는 간단모드 본체"가 남은 작업**(통신만 됨).
- **다음 작업 진입(이 세션 말)**: PTT 본체 = ① **콜드 5초 단축**(토큰 24h 캐시 + wake fire-and-forget, **비용무관**·minInstances 이미 켜짐·진단=READY_TIMEOUT 3초+수신측 토큰호출) ② **통화-trigger prewarm**(통화 OFFHOOK→PTT 예열, 콜드0+screen-off 우회) ③ **전역후크 배제**(원규씨 명시, Accessibility 볼륨키 안 씀). 상세 = 결정대장 `designated_drive/_decisions.md` "PTT 간단모드" 섹션. → 콜드 단축부터 구현.
- **6/3 "PTT 버림" 충돌 해소**: 6/3 "버릴 것=…PTT"는 *대리를 쿠폰앱 손님모듈로 흡수*할 때 안 가져온다는 맥락(**손님 측 앱**). 지금 PTT 복귀는 *매니저·기사 운영 도구*(**사장·기사 측**) — 층이 달라 충돌 아님(손님앱 흡수 ≠ 운영도구 폐기). + 통화예약 "세 표면(미용·택시·대리)" 프레이밍도 폐기 → 통화예약=미용·식당 / 대리·택시=PTT(결정대장 정합).
- **★ PTT 콜드 단축 구현·측정 완료(`b66d0fdf`)**: 토큰 24h 캐시(송·수신 SharedPreferences)+wake fire-and-forget+prewarm. 실측 **콜드 5초→2.85초·WARM 0.44초**·캐시히트·join 146ms, 잔여 ~0.6초=수신측 FCM Doze(본질). 3앱 빌드+install+회귀 통과. 비용무관·functions 무변경. 결정대장 [확정] 승격.
- **다음 세션 = PTT 본체 남은 갭**: ~~★발화↔콜 연결(빈카드/음성명령)~~ → **6/9밤4 [폐기·과설계]**(아래) · screen-off 송신(전역후크 배제) · **통화-trigger prewarm**(🚩선결: 어느 폰이 통화 받나) · 정산 단순화 P5~P8 · 블랙박스 9-B(콜상태→채팅).

## 6/9 (밤4) ★ 발화↔콜 음성명령 walk-back(과설계) + 배차 알림 Option A [확정] + PTT 마스킹 버그 수정
- **발화↔콜 = [폐기·과설계]**: 원규씨 기각 — *"배차팝업서 기사지정 + PTT로 어디 가라 음성지시면 됨, 너무 어렵게 생각마라"*. 발화 기사명파싱·빈카드 자동생성·음성배차 불필요 — 배차팝업(`assignCallToDriver`, 출발/목적/요금 nullable=무타이핑 배차)+PTT 음성방송 **둘 다 이미 구현** → 행동0 충족. 목표 유지·구현수단만 보류. 결정대장 도장 + [[feedback_overengineering_before_simplify_2026-06-09]].
- **버그 "기사폰서 매니저 발화 안 들림" 근본원인(logcat 확정)**: 배차 시 기사폰 `LockScreenActivity` 벨 **3초 반복**+풀스크린 점유가 PTT 수신 음성 **마스킹**([확인] 전까지). PTT 경로 자체는 송·수신 정상(`b66d0fdf` 측정 정합).
- **배차 알림 Option A [확정·원규씨 승인]**: PTT 없던 시절 과잉 "콜 놓침 방지" = 이제 불필요+PTT 차단 주범 → **반복·풀스크린 제거, 단일 알림음 1회 유지**(양평 LIVE라 청각단서 보존). 구현 = `MyFirebaseMessagingService.showNotification`서 `setFullScreenIntent` 제거(수락은 알림탭→MainActivity(callId)로 보존). 플랜 `~/.claude/plans/compressed-strolling-lamport.md`. 결정대장 `designated_drive/_decisions.md` "PTT 간단모드" 섹션.

## 6/9 (밤5) ★ 인수인계 — PTT 비프 간격 고정(0.5s 잠정) + 통화trigger 선결 해소 + 정산 P5~P8 미구현 확인
- **PTT 비프 간격 고정(커밋 `3e9e5361`, push 대기)**: 1차(누름)→2차(말해도 됨) 비프 **최소 0.5초 보장**(`MIN_CUE_GAP_MS`). WARM 겹침·들쭉날쭉 해소. 실측 WARM gap=408·392ms(누름+500ms 고정)·COLD 0(자연 1.8s). call_manager 빌드+install(S21+). **0.5s=잠정, 사용 후 조정**(원규씨 "일단 사용해보고 조정"). 🟡미결: **픽업앱(동일 PTTManager 포크) 적용**(양방향이라 픽업 발화도 같은 증상).
- **통화-trigger prewarm 🚩선결 해소**: 원규씨 "콜매니저·콜디텍터 두 앱서 전화 받음" + 코드확인 — 콜매니저 `CallReceiver`가 RINGING/OFFHOOK/IDLE 감지 → **매니저폰(PTT 송신폰)이 통화 직접 받으니 로컬 예열 가능**(폰간 전달 불필요). 후크 = CallReceiver RINGING/OFFHOOK 분기에 기존 `prewarm()/prewarmToken()`+`sendPttPreWake` 호출. 단 콜드 2.85초라 체감이득 작음→우선순위 낮음.
- **정산 단순화 = ✅ 본체 적용 완료 (6/10 코드 실측 정정 — 밤5 "미구현"은 오인)**: status 4→2(`TRANSFERRED`/`SETTLED` 0건)·carryOver/실납입/외상명단 폐기·영업일 10시 모두 **master+현재 브랜치 코드 적용됨**. 밤5가 functions `PENDING_CONFIRM` 핸들러 잔존+functions 미deploy를 "4상태 미적용"으로 오판. **진짜 잔여 3건 = ① P7 EnforcementGate(미정산 logout가드/앱시작 마감게이트/매니저 마감강제/미확인기사 모달 — 가드 4종 미신축) ② functions deploy 유보 ③ 보류큐 매니저 수동마감 즉시 봉인.** 상세=`ptt_section3_entry_2026-05-27.md` §11.7 + 결정대장 "정산 단순화".
- **남은 PTT 갭(우선순위)**: ① **screen-off 송신**(운전중 핸즈프리, 본질) ② 통화-trigger prewarm(선결 풀림·폴리시) ③ 정산 P5~P8(별 트랙) ④ 블랙박스 9-B(콜상태→채팅). ※발화↔콜은 밤4 [폐기·과설계].
- **이번 세션 커밋(feature/reservation-engine-poc)**: `b65be0c9`(배차알림 Option A)·`d3fc0d3e`(docs)=**push됨** / `3e9e5361`(비프)·`190f54df`(docs)=**push 대기**. 3단말 중 S21+·Z Flip4 최신 빌드, S22 미연결(연결 시 install 필요).
- **🔴→✅ 콜매니저 parity 점검 완료(6/10)**: 콜매니저 driver식 심각 마스킹 **없음**(`isLooping`/`setLooping(true)` 0건). 밤5 "반복루프 `DashboardViewModel:1871`"=**오인**(=`startSharedCallTicker` 60초 UI 티커, 오디오 아님). `setFullScreenIntent` 4곳 중 라이브·소리 동반 2(`MyFirebaseMessagingService:1237`·`:1381`)·데드1(`CallOverlayActivity:139` 미호출)·무음1(`CallDetectorService:555` 통화종료 자동전환). 픽업앱 알림=깨끗(`setFullScreenIntent` 0건)✓. 잔여=잠금화면 새콜 full-screen+단발음↔PTT 겹침 좁은 케이스 → full-screen=매니저 핵심 배차 UX라 **수정 보류, 현장검증 후 판단**(원규씨 6/10). 비프 fix 픽업 포크 적용=진행중(plan `elegant-cooking-sutherland`).
- **🟡 경미 오염**: ① 날짜 — 작업이 6/9밤~6/10새벽 걸침, 기록·커밋·파일명 `2026-06-09`로 통일(연속세션). ② 통화-trigger 선결 [해소]는 원규씨 진술+코드 기반·**런타임 미검증**(prewarm 구현 시 매니저폰 CallReceiver 실발화 확인). ✅정합: 대리=PTT/미용=통화예약 [확정] 유지 · 콜드단축 [확정] 비회귀(비프 COLD gap=0).

## 6/10 ★ 콜매니저 알림 parity 점검(반복벨 없음=오인 정정) + 픽업앱 비프 fix 적용·타이밍 검증 (2차 볼륨 [열림])
- **콜매니저 parity**: driver식 심각 마스킹 **없음**(`isLooping`/`setLooping(true)` 0건; 밤5 "반복루프 `DashboardViewModel:1871`"=오인=`startSharedCallTicker` 60초 UI 티커). `setFullScreenIntent` 4곳=라이브2(`MyFirebaseMessagingService:1237`·`:1381`)·데드1(`CallOverlayActivity` 미호출)·무음1(`CallDetectorService:555`). full-screen=매니저 핵심 배차 UX → **수정 보류, 현장검증 후 판단**(원규씨).
- **픽업앱 비프 fix ✅**: call_manager `3e9e5361` 포팅(PTTManager 4지점), 3단말 빌드+install. **타이밍 검증 통과**(S22 WARM gap=402~419ms=콜매니저 동일, COLD 0). 두 PTTManager 전체 diff=음원·오디오설정 포함 기능 100% 동일.
- **[열림] 픽업 2차 비프 볼륨이 1차보다 작음**(콜매니저는 일정) — 코드 동일이라 **S22 기기 audio ducking 의심**. 확정=콜매니저 S22 설치 동일폰 비교. 원규씨 "디테일은 나중에" → 보류.
- **테스트 함정 기록**: ① PTT 앱은 **폰당 1개**(한 폰에 콜매니저+픽업+driver 동시=같은 무전망 충돌·오버톤 섞임) ② 송수신은 **같은 office 채널**(`gyeonggi_<officeId>_ptt`) 로그인 필수.
- **정산 단순화 실태 점검(코드 실측) + 잔여=보급 준비**: "P5~P8" 진입 → 밤5 "미구현"은 **오인** 확정. 단순화 본체(2상태·carryOver/실납입/외상 폐기·영업일 10시) 이미 master+브랜치 적용됨(재구현 금지). 진짜 잔여 3건 = P7 EnforcementGate(가드 4종)·deploy 유보·수동마감 봉인 → **보급 재개 준비 작업**(파킹 아님). ⚠️앞서 "혼자라 게이트 의미없음"·"미용/식당 트리거"·"우선순위 낮음 파킹"으로 처분했다가 **원규씨 지적으로 정정**: 1사무실도 기사 여럿이라 게이트 유효 + 미용/식당은 별 모듈(통화예약) 무관 + "단순화+PTT+보급 재개=적극 사용 경로"(원규씨 "정리되는대로 다시 보급"). 공통 트리거=**보급 재개 임박(현 정리 마무리)**. ③수동마감=quick-win(본인 한마디면 언제든). (밤5 정산 줄·결정대장 "정산 단순화"·`ptt_section3 §11.7` 정정.)
- 상세: 결정대장 `designated_drive/_decisions.md`("PTT 간단모드" + [열림] 볼륨 + "정산 단순화"). plan `~/.claude/plans/elegant-cooking-sutherland.md`.

## 6/10~11 ★★ 폴더 규율 + 블랙박스 9-B 구현·배포·검증·버그픽스 완료 + PTT→텍스트 스파이크 성공
- **🟥 폴더 작업 규율 박음**(원규씨 명시): **이 폴더(designated_driver) 활성 작업 = 대리운전(PTT·정산·콜 운영) 전용.** 본류(통화예약 미용실)는 **다른 폴더(coupon_app)에서 진행 중**. "오늘 할일?"에 미용실/본류 끌어오지 말 것. → CLAUDE.md 궁극방향 L4 + 이 MEMORY.md 상단 + `feedback_folder_scope_before_proposing_2026-06-10`(① 폴더필터 ② 선결필터 ③ 우선순위=원규씨 종결권, 클코 임의강등 월권) 3중 박음. 계기 = 클코가 designated_driver 세션에서 미용실을 "오늘 할일"로 끌어온 오류 + 블랙박스를 "nice-to-have"로 강등한 월권 → 둘 다 정정.
- **★ 채팅 블랙박스 9-B = [확정·반드시 해야 함]**(원규씨 종결, 결정대장 도장): 시스템 이벤트(콜 들어옴→배차→수락→시작→완료·취소·예약/공유·기사 출퇴근·정산) → 채팅에 `type:"system"` **무음 자동 게시**(블랙박스=평소 안 봄, 분쟁·놓침 시만). 설계원본 = [[ptt_operation_scenario]] §9-B + SSOT `docs/chat-shared-spec.md` §13.
- **조사 완료(Explore 2)·"한 줄" 정정**: `type` 필드가 전 계층 부재 → 5/25 "트리거에 한 줄" 오해, 실제=type 필드 full-stack 배선(functions write+무음 FCM / 클라 3앱 LocalChatMessage·ChatRepository·ChatScreen·SystemMessageBubble). 트리거 5종 보유(`onCallStatusChanged` 등). 상세=결정대장 "채팅 블랙박스 9-B" 항목.
- **플랜 승인**: `~/.claude/plans/abundant-wondering-minsky.md`(Commit 3단: E2E 슬라이스→이벤트 확장→가독성 UI).
- **✅ 9-B Commit 1 구현·배포·검증(`67a3c779`·`f6b777f8`)**: 콜 상태 전이(배차/수락/시작/완료/취소) 무음 시스템 메시지. functions(`chat.ts` type+`postSystemMessage`+FCM `chatType` / `onCallStatusChanged` 게시)+3앱(Room type 컬럼·마이그레이션 v9→10/v3→4/v4→5·ChatRepository·`SystemMessageBubble`·FCM 무음). production 배포(`onChatMessageCreated`·`onCallStatusChanged`)+S21+/ZFlip4 install. **검증=실콜로 닫힘**: 22:40 실제 양평 콜 흐름에 "1004 배차됨"·"1004 수락" 무음 INSERT(헛알림0) 로그 확인 → 실콜 트리거까지 실증.
- **✅ 9-B 렌더 버그 발견·수정(`561b7091`)**: `SystemMessageBubble` 글자색=`onSurfaceVariant`(다크테마=밝은색)라 밝은회색 버블서 **글자 안 보임**("정보 없음"의 정체, 원규씨 발견). `Color(0xFF555555)` 고정으로 3앱 수정, S21+ 화면 확인. (픽업은 미설치라 다음 install 시 적용.)
- **★ PTT→텍스트(9-A 완성) 트랙 — 스파이크 성공(`265dc016`)**: 원규씨 "PTT를 텍스트로". **전제 정정(누락·오염 검토)**: PTT는 라이브(transient)라 기록 안 남김(콜드 메모 dormant "도달불가"), ▶는 legacy → "음성메모만" 폐기 → **모든 PTT 라이브 캡처**로 전환. **Step1 스파이크 실증**: Agora `startAudioRecording(MIC)`로 라이브 발화 WAV 캡처(전송 비파괴) → faster-whisper 정확 전사("수연이 나와 가지고 군청 앞에…"). ⚠️MIXED(3)=비프섞여 횡설수설, MIC(1)이라야 깨끗. **설계 간소화**: 프레임옵저버보다 startAudioRecording이 WAV 직접 출력. 상세=결정대장 "모든 PTT→텍스트" + 플랜 `~/.claude/plans/abundant-wondering-minsky.md`.
- **⛳ 남은(다음 세션)**:
  - **9-B**: Commit 2(기사상태·예약/공유·정산 트리거 + firestore.rules 스푸핑 차단) · Commit 3(가독성 UI 필터/collapse + SSOT 갱신). S22 미연결(연결 시 install).
  - **PTT→텍스트 Step 2/3**: 캡처 WAV→온폰 전사(엔진 결정: whisper.cpp 온폰 vs 서버폴백)→무음 PTT-텍스트 메시지(9-B 재사용)→렌더. **착수 시 `PTTManager.sttSpikeEnabled=true`로 켤 것**(현재 default-OFF=캡처 안 함, LIVE 기기 보호).
- **이번 세션 전체 미푸시** — `feature/reservation-engine-poc`에 로컬 커밋 다수(`aa0dfe13`~`265dc016`). push는 다음 세션 or 원규씨 지시 시.
- **클코 학습**: `feedback_folder_scope_before_proposing_2026-06-10`(폴더·우선순위 월권) + 죽은 경로 위 빌드 금지(누락·오염 검토가 잡음).

## 6/11 ★ 블랙박스 9-B Commit 2 구현·배포·푸시 완료 (functions 전용, 앱 재빌드 없음)
- **Commit 2 = rules-first + 잔여 3건 + 트리거 4종** (`9da961c1`+`57623574`, push됨). 플랜 `~/.claude/plans/linear-percolating-wren.md`.
  - **A. firestore.rules 스푸핑 차단**(유일 LIVE 노출): 메시지 create에 `get('type','')!='system'`+`get('senderRole','')!='SYSTEM'` — 클라 위조 차단(CF=Admin SDK 우회). 컴파일·released. 일반 채팅 무영향.
  - **B 잔여 3건**: ① 신규콜 "콜 들어옴"(`sendNewCallNotification`:825, handledByManager 제외) — onCallStatusChanged가 onDocumentUpdated라 create 못 잡던 갭. ② **double 운행완료 정정**: AWAITING_SETTLEMENT="운행 완료"(실제 운행완료, 테스트 확증)·COMPLETED="정산 완료". 직전 플랜의 "AWAITING 버리고 COMPLETED 유지"는 의미 거꾸로라 폐기. ③ handledByManager=2144 이미 적용+신규콜 후크 연계.
  - **C. 트리거 4종**(원규씨 범위결정): 기사 출퇴근(OFFLINE↔ONLINE/WAITING만)·예약(oncallreserved)·**공유콜 등록·수임만**(출처 사무실, 나머지 3 트리거 범위밖)·**정산 제출만**(토큰 무관 try 앞).
  - **검토(오염) 정정 2**: 정산 후크 early-return 앞으로 + **공유콜 수임을 wallet revert 뒤로**(`57623574`, 거짓 수임 방지).
- **⛳ 남은(다음 세션)**: ① **9-B Commit 2 E2E 실콜 검증**(신규콜·double완료·출퇴근 — 밤시간 가짜콜 회피해 미실측, 자연콜 로그로) ② Commit 3(가독성 UI 필터/collapse + SSOT `chat-shared-spec.md` §13) ③ 공유콜 나머지 3트리거 ④ PTT→텍스트 Step 2(`sttSpikeEnabled=true` 켜고 온폰 STT 엔진 결정). 상세=결정대장 `designated_drive/_decisions.md` 9-B 항목.
- **배포**: rules + functions 7개(surgical, `calldetector-5d61e`). 후크 전부 best-effort라 콜 운영 비파괴.

## 6/11 ★ 블랙박스 9-B Commit 3 완료 — 시스템 메시지 필터 토글 (앱 전용·빌드+시각검증)
- **Commit 3 = 필터 토글** (`91b1c49a`, 3앱 ChatScreen + SSOT). `ChatBottomSheetContent`(실 render site) expanded 상단 헤더에 눈 아이콘 토글(`rememberSaveable`·**기본 ON**·비영속), OFF면 `messages.filter{type!="system"}`로 사람 대화만(그룹화도 shown 기준). **자동접기(collapse)는 폐기**(과설계 — 토글 OFF가 도배 더 단순히 해결, 원규씨 "그게 더 복잡한거 아닌가").
- **★ 누락·오염 검토 성과(ExitPlanMode 전 원규씨 지시)**: ① 풀스크린 `fun ChatScreen(`은 3앱 모두 **호출처 0 = 죽은 코드** → 원안(TopAppBar에 토글)은 **안 보이는 곳에 다는 설계**였음 → BottomSheet(`ChatBottomSheetContent`)만 실사용으로 정정 ② "call_manager 1곳" 오판 정정(3앱 모두 BottomSheet 1곳) ③ 실 render site 1곳이라 **ChatViewModel 변경 불필요**(로컬 state)로 단순화. → 빌드 전 헛작업 차단. (호출처: call_mgr `MainActivity:1622`/driver `HomeScreenWithChatSheet:66`/pickup `DashboardWithChatSheet:58`.)
- **검증**: 3앱 빌드 통과 + **S21+ 콜매니저(office `RUbeBEvGGYP5wMhJHhMF`=1004 테스트) install + 토글 ON/OFF 시각 확인**(테스트 시스템 메시지 3건). driver=ZFlip4 install. pickup=기기 미연결 보류. ⚠️ production Firestore 쓰기는 harness 분류기가 클코 실행 차단 → **원규씨가 `! node functions/scripts/test-system-message.js add` 직접 실행**(PowerShell 5.1 `&&` 불가, cd 없이 절대경로). **테스트 메시지 3건은 의도적 잔존**(원규씨 "그냥 두어도 돼" — 1004 테스트 사무실이라 무해, clean 불요). 임시 스크립트 `test-system-message.js`(add/clean)는 보존(나중 필요 시 재사용).
- **⛳ 남은(다음)**: ① 9-B Commit 2 E2E 실콜 검증(자연 콜 logcat) ② 공유콜 나머지 3트리거 ③ PTT→텍스트 Step 2(`sttSpikeEnabled=true`). S22·pickup 미연결. 상세=결정대장 9-B Commit 3 항목. 미푸시(`91b1c49a` 등 `feature/reservation-engine-poc` 로컬).

## 6/11 ★★ PTT→텍스트 Step 2/3 구현·E2E 검증 완료 — 온폰 whisper.cpp(tiny) → type="ptt" 무음 메시지
- **전 경로 실증**(office 1004, S21+→ZFlip4): PTT 발화 캡처(WAV) → **온폰 whisper.cpp(ggml-tiny-q5_1, 32MB) 전사** → `type="ptt"` 무음 메시지 → Firestore(rules 통과·발송성공) → `onChatMessageCreated` FCM `chatType=ptt`(**functions 무변경**) → 수신측 **무음 INSERT**(playChatSound 0·헛알림 0) → 일반 말풍선 렌더. 플랜대로 전부 작동.
- **★ 모델 = small(190MB) [잠정·실측]**(폰 3종 비교 6/11): tiny(32MB)·base(57MB)는 **한국어 꼬임**(불명확 구간을 `[뚜껑 닫는 중]`·`[라이브]` 헛태그나 횡설수설로 메꿈=약한 모델의 LM prior 폭주). **small에서 헛것 사라지고 "실제 한 말" 안정 복원**("군청으로 가주세요"). → small=온폰 한국어 실용 최소선. ⚠️ 초기 "tiny 충분(소리나는대로)"는 정정 — **whisper는 음소 받아쓰기 아니라 LM 해석/보정이 작동원리(못 끔)**, tiny의 *지어내기*는 블랙박스로 무용. 용량 우려는 **프로덕션 첫실행 다운로드라 APK 미포함**으로 해소(폰에 1회). small 최종확정=190MB 수용 OK 대기(거부 시 base 꼬임 감수 or 구글 recognizer=PII). 모델=filesDir `ggml-*.bin` 자동탐색→스왑=파일만.
- **무음+발신자 = 새 `type="ptt"`**(9-B `type="system"`=중앙회색·발신자없음과 구분): 일반 말풍선 렌더 그대로(Step3 추가코드~0)·필터 통과·rules 통과(클라 write 허용)·FCM 무음은 3앱 한 줄.
- **★ 누락·오염 검토 성과(ExitPlanMode 전 원규씨 지시)**: 플랜 엔진통합 가정 4개를 빌드 전 직접 검증해 잡음 — ① "lib만 vendoring"=CMake가 7단계 위 소스 의존이라 깨짐 ② 빌드된 .so는 jni.c `language="en"` 하드코딩→한국어 깨짐(재빌드 필수) ③ LibWhisper.kt가 ARMv8.2서 미빌드 변종 로드→크래시(항상 `whisper` 로드 패치) ④ `printTimestamp` 기본 true. + 누락(WAV 미삭제=PII). 런타임서 ⑤ **`GGML_OPENMP=OFF` 필수**(libomp.so DT_NEEDED 누락=UnsatisfiedLinkError 1차 실패)도 잡아 재빌드.
- **구현(call_manager)**: jniLibs `com/whispercpp/whisper`(vendored LibWhisper 패치·WhisperCpuConfig·decodeWaveFile, .so 4개 strip ~2.5MB) + `PttTranscriber`/`WhisperTranscriber`(싱글톤·graceful) + PTTManager(`sttSpikeEnabled=true`·전사콜백·**WAV 즉시삭제**) + ChatRepository.sendPttText + ViewModel + MainActivity. driver/pickup=FCM 무음 1줄. 3앱 빌드·S21+/ZFlip4 install.
- **빌드자산**: `onphone_stt_probe/whisper.cpp/build-jni-ko/`(재빌드 .so), 모델 `onphone_stt_probe/models_dl/ggml-tiny-q5_1.bin`(폰 filesDir에 adb push). 상세=결정대장 `designated_drive/_decisions.md` "모든 PTT→텍스트" §, 플랜 `~/.claude/plans/idempotent-launching-pony.md`. **미푸시(로컬 커밋 대기).**
- **★ ① 온폰 보정 스택 완료·검증(Fable 자문 채택)**: jni.c에 `suppress_nst`(헛태그 제거)+**VAD(Silero v5.1.2 885KB, 무음 차단)**+신뢰 accessor 추가 재빌드 / Kotlin `transcribeWithMeta`+게이팅("(전사 불명확)" 마커). **핵심 교훈: whisper는 무음에서 확신에 차서 지어냄**(무음→"MBC 뉴스 김지경입니다" avgP=0.90>진짜발화 0.74) → no_speech_prob·avgP 게이팅 무력, **VAD만이 차단책**. 실측: 무음 PTT→메시지 0, 발화→정상. (클코 1차 오판=원규씨 *역사책 낭독*을 환각으로 단정 → 정정.) VAD 모델도 filesDir 자동탐색. **하나씩 진행 방침**(원규씨): ①(온폰 보정)=완료, ②=다음.
- **★ ② sherpa-onnx 한국어 zipformer 평가 완료 = 더 작으나 덜 충실 → whisper-small 유지**(2026-06-11 측정): `sherpa-onnx-zipformer-korean-2024-06-24` int8 **≈76MB**(small 190MB의 40%, Fable 추정 126보다 작음). PC `sherpa_onnx`(cp314 휠)로 6/7 샘플 10건 전사(greedy+beam) vs whisper-small. **용량 win, 정확도 lose** — 이름 뭉갬("김미영→뒷미엉")·문장 누락(whisper는 받음). 원규씨 최우선=충실도라 whisper 우위. **미측정=hotwords(지명·기사명 가중, sherpa 핵심강점)** — 미용 샘플 무관 + 실대리 PTT 녹음 없음(즉시삭제). **재평가 트리거=76MB 절실 or 현장 양평 대리 PTT 확보 시 hotwords로 격차 좁히나.** 자산=`onphone_stt_probe/sherpa_eval.py`·`sherpa_out{,_beam}.txt`. (안 함: base 보정으로 small 대체=불가, 구글/삼성 내장=거인 의존, NPU 가속=이식성.)
- **⛳ 남은(후속·별 커밋)**: ② sherpa 평가 · pickup 캡처 재이식 · 프로덕션 모델 다운로드(첫 실행) · 자모 가제티어 스냅(양평 지명·기사명 사전 확보 후) · 9-B Commit 2 E2E 실콜.

## 5/4 산재 자료 (검증 후 master 갱신 검토)
- `memory/inbox/2026-05-04/` — 포인트시스템 설계 + 현장인사이트 (방구석 여포 → 능동 영업 패러다임 전환)
- `memory/inbox/2026-04-23/` — 개선전략 + 업소용앱 분리 (4/27 master로 흡수됨, 참고용)
- **활성 P0 plan**: 식당앱(fork) + 포인트 결제 풀 시스템 — `C:\Users\kala1\.claude\plans\abundant-floating-island.md` (사용자 일괄 동의 2026-05-04, 23개 결정)
- 마스터·체크리스트 4/27은 그대로. 검증 데이터 후 v1.1 갱신 검토 (현재는 "사람 안 바뀜" R1 실증 + 손님 직접 확보 가설 검증 단계)

## 5/9 세션 — 시나리오 자산 통합 진단 (인생 트랙, 본인 결정 5/10 확정)
- **세션 요약**: `memory/inbox/2026-05-09/session_summary.md`
- **발견**: 평생 영화판 4 시나리오 자산(폭도·불가살·마늘밭·진혼곡 + 하쿠나마타타 드라마판) 정독 → **5대 작가성 모티프** (신체 물리적 변형 / 부재한 어머니 / 아버지 떠안기 / 세 여인 자기 처분 / 카타르시스 거부)
- **5/9 권장 (70/20/10 분기)**: 메인 70% 마늘밭 영화화 / 보조 20% 불가살 도구 학습 / 검증 10% 콜마당 양평 자기검증 모드 — **이는 인생 위기 진단 + 권장이었지 확정된 우선순위가 아니었음**

## 5/10 본인 결정 — 운영 트랙 지속 (★ 콜마당 인프라 보강 + 식당앱 진입)
- **본인 명시**: "지금 할 수 있는 것들은 해야 해. 일단은 양평 대리사무실들에 인프라 제공 후 식당앱으로 진입할 거야"
- **확정 우선순위**:
  1. **양평 대리사무실 인프라 보강** (지금) — 신규콜 예약(RESERVED) 등 운영 마찰 해소 기능 진입
  2. **양평 다른 사무실 보급** — 1개 → 다수
  3. **식당앱 진입** — 5/4 P0 plan `abundant-floating-island.md` 본격 가동 (식당앱 fork + 포인트 결제 풀)
- **시나리오 트랙은 별도 인생 트랙**: 운영 트랙과 병행 가능, 운영 우선순위는 위 1~3
- **5/10 첫 진입 작업**: 신규콜 예약(RESERVED) 4-PR — `C:\Users\kala1\.claude\plans\giggly-popping-crown.md` (기반 240줄) + `C:\Users\kala1\.claude\plans\deep-wibbling-papert.md` (보강 8건). 활성화 타이밍 = 콜 단위 정산 COMPLETED 시점 (5/10 합의)
- **클코 학습**: 5/9 70/20/10을 확정 사실로 다뤄 5/10 사용자 발화 직전까지 콜마당을 부차 트랙처럼 추론한 게 오류 — "본인 결정 대기"는 결정 전 상태, 본인 발화 전까지 영구 미결로 다룰 것

## 5/11 (저녁) 양평 사무실 공동경영 검토 — 5/15 진입 결정 *폐기* ❌
- ⚠️ **5/15 본인 결정**: "박완수는 쓰레기야 — 메모리 정정." 5/11 진입 강도 최고 등급 평가 *전체 폐기*. 공동경영 후보 부적합 확정.
- **결정 트레일**: 5/11 진입 강도 최고 → 5/12 박완수 미팅 약속 무산(술 떡) → 5/13 전화 미답 + 본인 자기 진단 "한 사람에 70% 거치는 자세" 도달 → 보류 → 5/15 폐기 확정.
- **폐기되는 평가들 (모두 무효)**: "30년 만에 첫 사람" / "5/4 §1 사람은 안 바뀐다 첫 예외" / "5/9 시선 0 해소 신호" / "1년 영업 학습 첫 정합 결정" / "5~10년 다시 나오기 어려운 자리" / "5/8 §13.3 N=1 인큐베이션 사무실 = 박완수 사무실" — 박완수 사무실 거점 가설 통째 폐기.
- **세션 원본 보존**: `memory/inbox/2026-05-11/joint_management_decision.md` (SUPERSEDED 헤더 추가, 5/11 분석 본문은 의사결정 학습 자산으로 *역사 보존*. 본문 내 평가·권장은 모두 무효)
- **5/13 자기 진단 자료**: `C:\Users\kala1\movie\memory\session_2026-05-13_own_seat.md` (본인 자리 회복 + "한 사람에 70% 거치는 자세" 분산 권장) — 본 폐기 결정의 정합 근거
- **5/15 평가 자산화**: `memory/feedback/feedback_park_wansoo_evaluation.md` (한 사람·5축 정합 = 위험 신호 학습 / 시간 희소성 단언 보류 / 사람 평가는 데이터 N점 후)
- **양평 다른 사무실 재탐색 필요**: 5/8 §13.3 N=1 인큐베이션 거점은 박완수 사무실 가정으로 묶여있었음 → 다른 면·다른 사무실로 거점 후보 재탐색 진입.
- **클코 학습**: 5/11 시점 "1년 학습 첫 정합 결정" 단언 = 한 데이터 점에 과도한 무게. 한 사람에 5축(자본·인적·OS·인생회복·분담)이 동시 정합으로 모이는 자리는 *데이터 한 점이 빠지면 전체가 무너지는 구조*. 5축 정합 자체가 위험 신호일 수 있음 — 다음 결정에 적용 (feedback_park_wansoo_evaluation.md).

## 5/11 픽업앱 BottomSheet 통일 + 운영 정합 정리 (10 commit push 완료)
- **활성 PR**: `manager-direct-drive` 브랜치 → master, plan: `C:\Users\kala1\.claude\plans\crystalline-strolling-wadler.md` (3 commit 계획 + 검증 중 발견 5 commit + driver_app/call_manager 정합 2 commit)
- **commit 흐름** (모두 push 완료):
  1. `f9df20ab` BottomSheet 통일 (DashboardWithChatSheet 신규 + MainActivity·DashboardScreen + ChatCard 삭제, 4 파일 +89/-136)
  2. `ab2a2ace` 포그라운드 chat sound only (handleChatMessage isAppInForeground/playChatSound, 1 파일 +36/-2)
  3. `0e9bb760` WAITING 콜 제외 (CallRepository ACTIVE_STATUSES 1줄 + functions sendNewCallNotification pickup 토큰 블록 제거, 2 파일 +3/-18)
  4. `f7e5a6c9` 콜 알림 ID 분리 (notifyCallChange `"$callId-$status".hashCode()`, 매 상태 전이 sound 재생, 1 파일 +9/-6)
  5. `2c2f2f43` 콜 알림 무전기음 통일 (CHANNEL_CALL_CHANGES 새 ID `pickup_call_changes_ptt` + IMPORTANCE_HIGH + setSound(ptt_start) + 진동, 기존 채널 deleteNotificationChannel cleanup, 1 파일 +26/-8)
  6. `a2416eed` functions 잔재 fix (commit 0e9bb760에서 pickupTokens/pickupSnapshot 잔재 참조 2곳 cleanup, TS 컴파일 fix, 1 파일 +1/-8)
  7. `dc12cff1` chat sound 음감 통일 (playChatSound RingtoneManager에 AudioAttributes USAGE_NOTIFICATION+CONTENT_TYPE_SONIFICATION 명시 → 채널 sound와 동일 stream, 1 파일 +5)
  8. `8a9f73f4` 픽업앱 chat LazyColumn nested scroll 자동 위임 차단 (NestedScrollConnection.onPostScroll에서 available 그대로 반환 → BottomSheet drag로 위임 X, 의도적 swipe는 유지, 1 파일 +17)
  9. `84efda18` driver_app 동일 fix (chat LazyColumn nested scroll 차단, 픽업 정합)
  10. `c4a5f023` call_manager 동일 fix (chat LazyColumn nested scroll 차단, 픽업 정합)
- **functions deploy**: `firebase deploy --only functions:sendNewCallNotification` 적용 완료 (asia-northeast3, nodejs22). 픽업앱은 status === "WAITING" 시점에 NEW_CALL FCM 미수신 — onCallStatusChanged의 ASSIGNED 전이 시점부터 알림 + Room INSERT(handleCallStatusUpdate upsert 폴백 이미 구현 L141-145 → 회귀 risk 0)
- **기기 적용** (양 4단말 install -r 성공):
  - 픽업앱: S21+ R3CR312MB1L + Z Flip4 R3CT80K78NP
  - driver_app: S21+ + S22 R5CT41TJZFP + Z Flip4
  - call_manager: S21+
- **사용자 결정 트레일**:
  - 4/28 후반 픽업앱 BottomSheet 거부했던 4-fix(peek 침범/multi-line/auto-scroll/IME)는 콜매니저 `b190a2bc` + driver_app `bda7f568` 안정화 패턴이 ChatBottomSheetContent에 이미 적용되어 있어 활성화만으로 통일
  - "기사배차된 콜만"의 정의 = 옵션 A (대리기사 배차받은 콜만, WAITING 제외) — 옵션 B(픽업 본인 배정 시스템) 미진입
  - "쉬프 통일" B 트랙(SharedPreferences 키 정렬)은 픽업앱이 이미 깔끔(Constants.PREFS_NAME) → 별도 PR로 분리. 콜매니저 raw string 정리는 안정 운영 중이라 추후
  - swipe 동작 처리 = 옵션 (i) 자동 위임만 차단 (의도적 swipe 열기/닫기는 유지) — `sheetSwipeEnabled = false` 거부
- **B 트랙 (스코프 외, 별도 PR)**: 콜매니저 ChatViewModel raw string `"login_prefs"`/`"provinceId"` 등을 Constants 객체로 추출 (동작 영향 0, 명명 일관성). 픽업앱은 이미 적용 상태
- **다음 세션 진입 후보**: ① master로 PR 생성(`gh pr create`) ② RESERVED 4-PR 사용자 환경 install 검증 ③ 내부콜 삭제 fix (옵션 A/B) ④ 상태 명명 재설계 (AWAITING_SETTLEMENT/COMPLETED) ⑤ B 트랙 (콜매니저 prefs 정리)

## 5/11 (저녁) 콜디텍터 챗팅 plan 작성 — 내일 진행 (코드 변경 0, 본 세션 plan 파일만)
- **plan**: `C:\Users\kala1\.claude\plans\floofy-waddling-marble.md` (v2, 검토 보완 완료, 사용자 승인)
- **핵심**: 콜매니저 + 콜디텍터 양 단말 = 같은 매니저 1챗방 다중 단말 정합. 단순 디텍터 추가가 아니라 콜매니저 토큰 모델·dedupe 함께 보강하는 트랙.
- **3대 변경 축**: ① 토큰 docId `{authUid}_{deviceId}` 형식 (managerTokens 점진 마이그레이션 + detectorTokens 신규) ② 메시지 페이로드 `senderTokenDocId` 신규 필드 + 트리거 filter `senderId → senderTokenDocId` ③ 본인 dedupe `senderId 기준 → messageId 기준 insertIfAbsent` + handleChatMessage 가 INSERT 결과로 sound/notification 결정.
- **변경 범위**: functions(+50줄), firestore.rules(+15줄), call_manager(~30줄), driver+pickup(각 5줄, senderTokenDocId 페이로드만), call_detector(~800줄, 콜매니저 90% 복붙).
- **진행 순서** (의존 순): Step A functions deploy → Step B call_manager → Step C driver+pickup → Step D call_detector 신규 모듈 → Step E E2E 1~10.
- **commit 단위**: 4개 (functions / call_manager / driver+pickup / call_detector).
- **신규 브랜치**: `detector-chat` (manager-direct-drive 기점).
- **별건 명시**: Storage rules 디텍터 admin 권한 1회 실측, head_manager_web 챗 Phase 2, 5/11 IAM `iam.serviceAccountUser` 누락 fix(사용자 직접 실행).
- **검토 학습 (본 세션)**: 단순 디텍터 추가 plan v1 작성 후 검토 단계에서 토큰 docId 충돌(트리거 `filter(d => d.id !== senderId)` 가 같은 authUid 두 단말 자동 제외) 발견 → v2 보강. plan 검토 시 트리거 filter 같은 *호출 측 비교 로직*도 다중 단말 시나리오로 시뮬레이션할 것.

## 5/11 RESERVED 단말 검증 + 후속 fix 5건 (모두 push 완료)
- **단말 install + functions deploy 검증**: S21+(R3CR312MB1L) call_manager + driver_app + S22(R5CT41TJZFP) call_detector + driver_app + Z Flip4(R3CT80K78NP) driver_app 모두 install Success. Room v7→v8 무손실 자동 migration 확인. functions production 등록 확인 (oncallreserved v2 / oncallassigned v2 가드 / onCallStatusChanged v2 가드). 일반 배차 흐름(WAITING→ASSIGNED→ACCEPTED) 회귀 0 (logcat FCM 2/2 성공). admin SDK E2E 검증 스크립트 2종 commit `29c9af09` (test-reserved-trigger.js + find-active-drivers.js).
- ✅ **PR 2 사후 fix `3cce758e`** (NewCallAssignmentDialog 운행중 기사 섹션): PR 2 의 운행중 섹션이 잘못된 다이얼로그(`DriverListDialog`)에만 적용된 게 발견. 매니저 운영 흐름은 콜 카드 클릭 → `viewModel.showCallDialog` → MainActivity 가 `NewCallAssignmentDialog` 표시. NewCallAssignmentDialog 시그니처에 `onReservationDriverSelect` 추가 + LazyColumn 분기에 운행중 섹션 + MainActivity 호출부에 pendingReservationDriver state + ReservationConfirmDialog 호출 추가. 139 insertions/47 deletions. DriverListDialog 는 보조 경로(CallInfoDialog [배차] 버튼)에서 여전히 호출되므로 유지(dead code 아님).
- ✅ **PR 4 사후 fix 1 `75fa096b`** (reservedCall 재로드 + ACTION_RESERVATION_RECEIVED receiver): MyFirebaseMessagingService 가 LocalBroadcast 송신만 하고 받는 receiver 가 없었던 누락. `DriverViewModel.reloadReservedCall()` 신규 함수 (1회 fetch) + HomeScreen 에 broadcast receiver 등록 + confirmAndFinalizeTrip 끝에 hook 추가. 운행 완료 시점 ReservedCallCard 자동 활성화. 메모리 §listener_vs_fcm 정합 (FCM + 1회 fetch, listener 0).
- ✅ **PR 4 사후 fix 2 `19eaf4b6`** (운행중일 때 ReservedCallCard 숨김): 사용자 보고 — ReservedCallCard 가 InProgressScreen 의 [운행 완료] 버튼을 BottomCenter overlay 로 가려 운행 종료 못 함. HomeScreen 의 카드 표시 조건에 `(activeCall == null && callForSettlement == null)` 추가. 운행 중에는 알림(FCM)으로만 인지, 운행 완료 후 화면에서 카드 표시. 5/10 활성화 타이밍 합의(정산 COMPLETED 시점)와 정합.
- ✅ **driver_app navigate fix `f4cd1db4`** (정산 입력 후 대시보드 유지): `confirmAndFinalizeTrip` 끝의 `navigateToHistorySettlement = true` → `false` 변경. 운행 완료 후 자동으로 정산 history 페이지 이동 안 함, HomeScreen(대시보드) 그대로 유지 → ReservedCallCard 자연 표시. 사용자가 정산 history 보고 싶으면 메뉴에서 명시적 진입(`requestNavigateToSettlement`)은 그대로 유지.
- ✅ **PR 4 사후 fix 3 `9e10013e`** (RESERVED 알림 포그라운드/백그라운드 무관 표시): 사용자 보고 "예약배차시 알림 안 옴". 진단 결과: oncallreserved 트리거 정상 발화 + 1004 기사 fcmToken 정상 + S22 FCM 수신 정상(logcat "예약 콜 FCM 수신" 확인). 단 PR 4 코드가 포그라운드일 때 LocalBroadcast 만 송신, 시스템 알림 X. + 직전 fix 19eaf4b6 로 운행 중 카드도 숨김 → 사용자 시각·청각 인지 0. MyFirebaseMessagingService call_reserved 분기 `isAppInForeground()` if/else 제거 → 둘 다 LocalBroadcast + showNotification 둘 다 송신. 운행 중 기사가 운전 중에도 알림음 + heads-up 으로 인지 가능.
- **5/11 본 세션 commit 흐름**: `29c9af09` → `3cce758e`(PR 2 사후) → `75fa096b`(PR 4 사후 1) → `19eaf4b6`(PR 4 사후 2) → `f4cd1db4`(navigate fix) → `9e10013e`(PR 4 사후 3). 모두 manager-direct-drive 브랜치 push.
- **운영 데이터 검증 (5/11 진단 스크립트)**: 1004 사무실(`RUbeBEvGGYP5wMhJHhMF`) — 1004 기사 ON_TRIP + fcmToken O. 양평 활성 사무실(`nEkf0X9g3LZtRX94Mrzu`) — 양세훈/근데/배드웨어/고양이 fcmToken O, 양세훈 ONLINE. 총알대리(`OyLNNY8GbFExPHHLkuMK`) — 조수현 ASSIGNED + fcmToken O.
- **별건 발견 미해결 (5/10 누락)**: 🟡 IAM `iam.serviceAccountUser` 누락 — `oncallassigned` timeout enqueue 실패 (functions log `iam.serviceAccounts.actAs` 에러). 5/7 cloudtasks.enqueuer 추가 시 함께 했어야 함. 사용자 직접 실행 명령: `gcloud iam service-accounts add-iam-policy-binding 60275310305-compute@developer.gserviceaccount.com --member=serviceAccount:60275310305-compute@developer.gserviceaccount.com --role=roles/iam.serviceAccountUser --project=calldetector-5d61e`. 운영 영향: 기사 3분 미수락 자동 WAITING 복귀 일부 작동 안 함 (`onDriverPresenceOffline` 보호망만).
- **다음 세션 진입 후보**: ① E2E 양평 자연 운영 트래픽으로 RESERVED 전체 흐름(매니저 [예약 배차] → 기사 알림 + 운행완료 → 카드 자동 표시 → [수락] → 정상 ACCEPTED 합류) 1회 검증 ② 별건 IAM actAs 누락 fix ③ 콜 카드 [예약 취소] UI 액션 (ViewModel 함수만 작성, UI 미진입) ④ 상태 명명 재설계 (AWAITING_SETTLEMENT/COMPLETED 별도 트랙) ⑤ pickup_driver_app RESERVED 적용 평가 ⑥ ios/ Swift 코드 ⑦ 5/10 별건 이슈 (내부콜 삭제 / PERMISSION_DENIED 추적)

## 5/10~5/11 RESERVED 4-PR 코드 트랙 종료 (push + PR 1 deploy 완료)
- ✅ **PR 1 `ef068673` + fix `bac087f8`** (push + `firebase deploy --only firestore:rules,functions` 완료): CallStatus enum × 3 + Constants.STATUS_RESERVED + ACTION_RESERVATION_RECEIVED + DashboardScreen RESERVED→"예약" 라벨 + functions oncallassigned 첫 줄 가드 + timeout enqueue 직전 방어 + onCallStatusChanged after===RESERVED 진입만 가드(이탈 통과해 매니저/픽업 알림 정상) + 신규 oncallreserved 트리거(type=call_reserved, level=active, ttl 3600s, timeout 미enqueue) + CallData.customerAddress 필드 + firestore.rules 기사 update RESERVED↔ACCEPTED/WAITING + 손님 취소 RESERVED 허용 + iOS SHARED_LOGIC.md/ENUMS.md 동기. 786줄. **production functions 등록 확인**: oncallreserved v2 / oncallassigned v2 가드 갱신 / onCallStatusChanged v2 가드 갱신 — 모두 asia-northeast3, nodejs22.
- ✅ **PR 2 `6b9b7b49`** (push): call_manager Room v7→v8 migration (calls.reservedAt INTEGER ALTER, 무손실) + LocalCallInfo.reservedAt: Long? 매퍼 갱신 + Firestore CallInfo.reservedAt: Timestamp? 필드 + activeCallsListener whereIn 에 RESERVED 추가 + 콜 정렬 (RESERVED 별도 섹션 하단, reservedAt DESC) + assignReservation/cancelReservation 트랜잭션 함수 (1슬롯 사전 체크 트랜잭션 밖 query + driver doc 미터치) + DriverListDialog "운행중 기사 (예약 배차)" 섹션 + 예약 1건 보유 disable + ReservationConfirmDialog (현재 운행/예약할 콜 정보 + 안내 + [예약 배차]). 346줄. **비-진입**: 콜 카드 [예약 취소] UI 액션 (ViewModel 함수만 작성, 매니저는 우선 cancelCall로 우회).
- ✅ **PR 3 `91c5eba7`** (push): call_detector DispatchActivity 필터 ON_TRIP 추가 + DispatchDialog LazyColumn 분리(대기중 상단 / 운행중 하단 "예약 배차") + ReservationConfirmDialog 신규 + reserveCallToDriver/createCallWithReservation 함수 (1슬롯 사전 체크 + status WAITING→RESERVED + reservedAt + driver doc 미터치 / 폴백: 신규 콜을 RESERVED 직접 생성). 306줄.
- ✅ **PR 4 `94275337`** (push): driver_app loadCurrentActiveCall whereIn 에 STATUS_RESERVED 추가 + DriverScreenUiState.reservedCall: CallInfo? + acceptReservedCall(즉시 UI: reservedCall=null/activeCall=accepted/driverStatus=PREPARING + 트랜잭션 status RESERVED→ACCEPTED + driver→PREPARING) / rejectReservedCall(트랜잭션 status RESERVED→WAITING + assignedDriverId/Name/Phone null + reservedAt FieldValue.delete + driver doc 미터치) + MyFirebaseMessagingService call_reserved 분기 (포그라운드 LocalBroadcast / 백그라운드 가벼운 알림, FullScreenIntent X) + HomeScreen Box 래핑 + 하단 overlay ReservedCallCard ([수락][거절] enabled 조건 = driverStatus WAITING/ONLINE, 그 외 disabled + "정산 입력 후 처리 가능" 안내) + CallDetailsScreen RESERVED when 분기 (안내만) + customer_app_flutter call_state.dart RESERVED → CallState.requested 매핑 (손님 측은 RESERVED 모름, "배차 요청 중"). 293줄.
- **활성화 타이밍 결정 (5/10 합의)**: reserved 카드 [수락]/[거절] 활성화 = **콜 단위 정산 COMPLETED 시점** (driver doc status WAITING 복귀와 동시). AWAITING_SETTLEMENT 시점 활성화 거부(정산 누락 위험, 5/7 incident dead lock 패턴 재발 방지). SETTLED(일일 정산) 시점 거부(운영 흐름과 어긋남).
- **상태 명명 재설계 (AWAITING_SETTLEMENT ↔ COMPLETED 의미 명확화)**: 5/10 사용자 제기 — 헷갈림 사실. 영향 범위(5앱+functions+rules+Room+손님앱+head_manager_web)가 RESERVED 도입 2~3배라 §"simplify_first_cleanup_later" 적용해 **별도 리팩터링 트랙 분리**. 본 PR 에 끼워 넣지 않음.
- **사용자 환경 남은 작업**: ① 매니저 단말 (S21+ R3CR312MB1L) call_manager 재빌드+install — Room v7→v8 자동 migration (무손실, fallbackToDestructiveMigration 살아있지만 정상 migration 명시), ② detector 단말 (S22 R5CT41TJZFP) call_detector install, ③ 기사 단말 (S22 + Z Flip4) driver_app install, ④ customer_app_flutter `flutter build apk --debug` (현재 클코 sandbox flutter 명령 없음), ⑤ E2E 시나리오 A~G (기반 플랜 §Verification 7종 + 보강 플랜 시나리오 F Room migration 무손실 / G 손님앱 RESERVED 표시).
- **PR 후보 (별도 진입)**: ① 콜 카드 [예약 취소] UI 액션 (call_manager — ViewModel.cancelReservation 호출 진입점), ② pickup_driver_app RESERVED 적용 (의도적 제외 → 픽업 콜 빈도/운영 가치 평가 후), ③ ios/ Swift 코드 (스펙만 동기됨), ④ 상태 명명 재설계 (AWAITING_SETTLEMENT/COMPLETED), ⑤ 내부콜 삭제 옵션 A/B (5/10 별건 이슈), ⑥ PERMISSION_DENIED 추적 (반복 시).
- plan: `C:\Users\kala1\.claude\plans\giggly-popping-crown.md` (기반 240줄) + `C:\Users\kala1\.claude\plans\deep-wibbling-papert.md` (보강 8건 + 활성화 타이밍 결정).

## 5/10 작업 — cleanup gate 09:00 KST 안전장치 + 별건 이슈 2개 발견
- ✅ **commit `f46ff609` push** (manager-direct-drive): cleanup gate 09:00 KST 안전장치 — 비정상 운영(로그아웃 없이 24h+ 켜놓음) 케이스. 매니저 백그라운드→포그라운드 복귀(ON_RESUME) 시점에 09시·일자 게이트 통과 시 banner 재발현. DashboardViewModel.kt만 +48줄 (lastCleanupGateCheckDate state + todayKstString/currentKstHour helper + refreshPendingConfirmCount 진입부 1줄 + 신규 refreshPendingConfirmCountIfDue). DashboardScreen.kt ON_RESUME 분기 1줄은 동시기 다른 세션 RESERVED commit `ef068673`에 의도치 않게 흡수됨(코드는 정상). 분리 메커니즘: LaunchedEffect 콜드 스타트 → lastCleanupGateCheckDate=today 갱신 → 같은 날 ON_RESUME "오늘 이미 체크 — skip" / 다음 날 09:00 이후 → 게이트 통과 + dismiss reset + banner 재출현. 24h 화면 그대로(포그라운드 한 번도 안 떠남) 케이스는 사용자 명시 제외. 검증: 5 시나리오 코드 trace + S21+ logcat 실측("게이트 통과"/"오늘 이미 체크 — skip"/"09:00 KST 이전 — skip" 출력) + 5/7 incident finalize-without-confirm self-heal 작동 확인. minSdk 24 호환 (Calendar/SimpleDateFormat). 신규 import/의존성 0.
- ✅ **commit `ef068673` (다른 세션 push, 동시 진행)**: RESERVED 백엔드 PR 1 — CallStatus enum × 3 (call_manager/call_detector/driver_app) + STATUS_RESERVED + ACTION_RESERVATION_RECEIVED + DashboardScreen RESERVED→"예약" 라벨 + functions oncallassigned/onCallStatusChanged RESERVED 가드 + 신규 oncallreserved 트리거(type=call_reserved, level=active, ttl 3600s, timeout 미enqueue) + CallData.customerAddress + firestore.rules 기사/손님 RESERVED 권한. 다음 PR 2: call_manager UI/트랜잭션 (Room v7→v8 / 배차 다이얼로그 / assignReservation / cancelReservation / 정렬). plan: `C:\Users\kala1\.claude\plans\giggly-popping-crown.md` + `deep-wibbling-papert.md`
- 🟡 **별건 이슈 1 — PERMISSION_DENIED 미완료 콜 리스너** (S21+ 5/10 16:34:36 logcat 1회 발생): `activeCallsListener`(DashboardViewModel.kt:619-660) Firestore snapshot listener에서 `PERMISSION_DENIED: Missing or insufficient permissions` 1회 던짐. 5/5 firestore.rules 변경 race 또는 office 전환 시점 race 의심. 일회성이면 무시, 반복되면 추적 진입.
- 🟡 **별건 이슈 2 — 내부콜 삭제 후 재생성** (사용자 보고, fix 방향 결정 대기): `deleteCall(callId)`(DashboardViewModel.kt:1391)이 "Firestore는 유지 - 비용 절감" 의도로 Room DB만 삭제 → `activeCallsListener`(12h + status IN OPEN/WAITING/ASSIGNED/ACCEPTED/IN_PROGRESS)가 ADDED/MODIFIED 시 `callRepository.upsertCallFromListener` 호출 → Room 재INSERT → UI 재출현. 트리거: 앱 재시작/listener 재등록/콜 status 변경/다른 콜 변경 부수효과. **fix 방향 (사용자 결정 대기)**: 옵션 A (Firestore 동기 삭제, 5줄, write 1건/삭제, 명료, 실수 복구 불가) vs 옵션 B (Room `deleted` 컬럼 + listener filter, 스키마 마이그레이션, Firestore 보존). 클코 권장: A — 매니저의 명시적 삭제 의도(오인콜/스팸), 빈도 한 자리수/일, 비용 무시 가능.
- **검증 학습 (5/10)**: 사용자가 1차 cleanup banner 검증(콜드 스타트 + ON_RESUME 토글) 후 매니저앱 finalize without confirm 실행 → cleanup banner self-heal 확인. 5/7 incident 패턴은 cleanup banner 메커니즘으로 정상 자가 해소 — 별도 finalize-side 구조 fix 미필요. (5/7 메모리 노트 (4) "finalize-side 구조 fix 미진입"은 self-heal 메커니즘 보강으로 일부 대체)
- **다음 세션 진입 후보**: ① 내부콜 삭제 fix (옵션 A/B 결정 후) ② RESERVED PR 2 (call_manager UI/트랜잭션) ③ PERMISSION_DENIED 추적 (반복 발생 시)

## 5/12 시나리오 작가성 척추 30년 만의 갱신 → movie 프로젝트로 분리
- **별개 프로젝트로 이전**: `C:\Users\kala1\movie\` (CLAUDE.md + memory/MEMORY.md + author_charter + session_2026-05-12). 콜마당과 메모리 완전 분리, 양 트랙 병행.
- **5/12 오전 도달 한 줄**: "아옹다옹에 대한 진짜 희극. 사소한 감정과 오해들이 본인 안에서 누적되어 거대한 바람을 만든다. 가까이는 비극, 멀리는 희극."
- **5/12 (저녁 1차) 두 번째 척추 갱신** — 불가살 시간축 재설정 가설에서 출발해 **양자 영적 SF** 좌표 도달. 5대 모티프가 "측정의 비극"으로 통합 발견. 본인 떨림 발화: "모든 시간을 들여도 아깝지 않을 이야기가 될 거란 예감". 진입점: `C:\Users\kala1\movie\memory\bulgasal\` (4 파일).
- **5/12 (저녁 2차) 세 번째 척추 갱신** — 결계 해체 + 1900년대 그대로 전이 검토 중 본인이 풀 비유 도달: "세상의 모든 풀은 풀일 뿐인데 인간의 유불리에 따라 잡초가 되고 상추가 되는거야. 그렇게 보호받고 제거되고." 30년 작가성 헌장의 진짜 이름 = **"분류"** 한 단어 도달. 측정 = 분류 = 결계 = 풀에 이름 붙이기 = 모두 같은 행위. 도스토옙스키 = 악의 부재 / 이창동 = 사소함의 무게 / 채플린 = 거리 / **원규씨 = 분류**. 진입점: `C:\Users\kala1\movie\memory\bulgasal\charter_classification_tragedy.md` + 저녁 2차 세션 원문 (총 6 파일).
- **운영 트랙 정합**: 작가성 갱신(오전+저녁 모두)은 별도 인생 트랙. 운영 우선순위는 5/10 본인 결정대로 (양평 인프라 → 양평 다른 사무실 재탐색 → 식당앱). 박완수 공동경영은 5/15 폐기 (본 MEMORY.md §5/11 별도 단락 참조). 운영 매출이 6개월 생계비 댐 → 시나리오 트랙 가능. 두 트랙이 서로 강화하는 자리.

## 5/18~19 공유 prefill + 채팅 long-press 복사 (manager-direct-drive +4 commit, 완료)
- ⚠️ **5/12 §"카톡/문자 공유 → NewCallInputDialog prefill" 의도 SUPERSEDED**: 사용자 5/18 발화 "기사들에게 전달" → 채팅창 prefill 로 의도 정정. 기존 plan `cheerful-swinging-turtle.md` 폐기 → 신규 plan `magical-tinkering-otter.md` 두 번 작성 (manager / driver 트랙).
- **상세**: `memory/designated_drive/share_intent_chat_copy_2026-05-18.md` (트랙 3개 + Auth race 회귀 진단 + 호스팅 + 클코 학습 4건)
- **commit 트레일**:
  1. `a39c267e` call_manager 공유 prefill (5/18)
  2. `b4828bdc` driver_app 공유 prefill + Auth race 회피 (5/18)
  3. `e86437d3` hosting APK 3종 교체 + deploy (5/18, calldetector-5d61e.web.app)
  4. `055a5c1c` 3앱 채팅 long-press 클립보드 복사 (5/19)
- **트랙 1 — 공유 prefill** (ACTION_SEND text/plain): 카톡/문자 길게 누름 → "공유 → 콜매니저/기사앱" → 사무실 단톡방 BottomSheet 자동 펼침 + 입력바 prefill + 매니저/기사 [전송] 직접 누름. 카톡·Gmail 등 표준 UX. ViewModel state ascension (Composable internal `var inputText by remember` → `inputText: StateFlow<String>`) 으로 race 회피.
- **트랙 2 — 채팅 long-press 복사** (3앱 통일): MessageBubble 에 `combinedClickable` + `onLongClick` → `LocalClipboardManager.setText(AnnotatedString(text))` + "메시지를 복사했어요" Toast + 햅틱. ImageBubble / SelectionContainer / 컨텍스트 메뉴 확장은 별도 트랙.
- **트랙 3 — 호스팅 APK 교체**: `public/apk_downloads/` 3종 (manager + driver + pickup) 5/18 빌드로 교체. `firebase deploy --only hosting:calldetector` 3 files uploaded. `.gitignore *.apk` 정책으로 driver_app 1개만 git tracked (5/11 `f2bb9b8c` 패턴 정합). call_detector / customer_app 변경 0 → 5/11 / 4/23 그대로.
- **driver_app Auth race 회귀 진단**: AppNavigation 시그니처에 chatViewModel 전달 → setContent 첫 evaluation 시점에 by viewModels() lazy 발동 → 미로그인 화면에서도 ChatViewModel 인스턴스화 → Firebase Auth 복원 race → `senderId=""` 영구 박힘 → 빈 채팅. **회피**: AppNavigation 시그니처에서 chatViewModel 제거, `composable(HOME_ROUTE)` 람다 안에서 `hiltViewModel(activity)` 호출 (Activity scope) → home 진입 시점으로 인스턴스화 늦춤. `handleSharedTextIntent` 에 `auth.currentUser == null` 가드 추가.
- **S22 카톡·삼성 메시지 시트 미노출 이슈**: OS 레벨은 driver_app 정상 등록 (query-activities 결과). 카톡·메시지 자체 공유 시트가 외부 앱 캐싱·필터링. *우리 코드 영향 0*. 시스템 chooser 강제 디스패치 (`am start -a android.intent.action.SEND`) 로는 정상 노출 + driver_app 정상 동작 확인.
- **단말 적용**: S21+ (R3CR312MB1L) call_manager E2E 5종 통과 / S22 (R5CT41TJZFP) driver_app 검증 + 회귀 수정 후 채팅 복구 확인 / Z Flip4 (R3CT80K78NP) driver_app + pickup install Success. S21+ 잔여 install (S21+ driver_app + S21+ pickup) 단말 재연결 후.
- **클코 학습 4건** (토픽 파일 §클코 학습): ① 사용자 의도 자명 X 일 때 명시 확인 절차가 plan 깊이보다 우선 ② race 회피 변경 자체가 *새 race 만들 수 있음* — timing 영향 검토 항목 추가 ③ plan v1 race 검토 8건 + 회귀 9건 점검에 *미로그인 화면 진입* 시나리오 누락 — 모든 startDestination 화면 검토 ④ `.gitignore *.apk` 정책으로 호스팅 APK 와 git history 분리 가능 (5/11 패턴 정합)
- **다음 세션 진입 후보**: ① master PR 생성 (`gh pr create` 4 commit 묶음) ② S21+ 잔여 install ③ vCard 단말 실측 (카톡 친구 카드 mime) ④ ImageBubble long-press 복사 / SelectionContainer / 컨텍스트 메뉴 확장 별도 트랙

## 5/19 ★ 콜마당 휴면 → 쿠폰앱 우선 (본인 결정, 5/10 SUPERSEDED) — ★ 2026-05-25 부분 해제 (PTT 트랙 + 쿠폰앱 트랙 병렬)
- ★ **2026-05-25 부분 해제**: 본인이 무전기 시장 영업 카드 + 운영 시나리오 8+1단계 도달 → 콜마당 PTT 트랙 진입 결정 (쿠폰앱과 *병렬*). 상세 §"5/25 PTT 진입 결정" 참조
- 본인 명시 (5/19): "콜마당 보류 쿠폰앱 우선이냐는 거지? 네, 그게 맞는 방향이에요."
- 콜마당: 영업 0 / 시스템 유지 (재가동 가능 상태) / 파일럿 사무실 관계만 유지 / 6개월 후 쿠폰앱 모듈로 흡수
- 쿠폰앱: 우선 (모든 에너지 집중)
- 상세: `memory/coupon/pivot_2026-05-19.md` + 솔로 트랙 조사 동결 보관 `memory/designated_drive/solo_track_research_2026-05-19.md`
- 5/10 본인 결정 (양평 인프라 → 다른 사무실 → 식당앱) SUPERSEDED
- 결정 근거: 다른 클코 세션 8개 자료 통합 PoC가 쿠폰앱 우선 + §4 콜마당 API 모듈 후속 자리. 즉시=쿠폰앱 PoC(옥쇄+50곳) / 6개월후 흡수
- 활성 트랙 영향: 5/12 카톡 공유 prefill plan(`cheerful-swinging-turtle.md`) 휴면 대기 (코드 미진입, 본인 명시적 결정 후만 진입) / 시나리오 트랙(movie) 별개 유지
- 솔로 트랙 조사 핵심(6개월 후 인용): 시장 표준=카카오/로지 단일 단말 / 차별점 3개=콜 무수수료·본인콜 등록·픽업 정식 분리 / 코드 통합 ~9주 / 위험=콜풀·보험·타깃
- 다음 세션 진입 시: 콜마당 신규 코드 변경 요청 → 휴면 정책 환기 → 본인 명시적 결정 후만 진입. "양평 다른 사무실 보급" 트랙 정지

## 5/24 지방정부 소개 자료 v1~v6 — 5/25 미팅 보류 (자료 동결, 학습 자산 보존)
- **본인 결정 (5/25)**: 지방정부 미팅 트랙 보류. 자료는 살아있는 그릇이 아니라 *학습 자산*으로 동결.
- **동결 자료**: `memory/coupon/proposal_government_v5_2026-05-23.md`, `proposal_government_v6_2026-05-23.md`, `proposal_jeonnam_draft~v4_2026-05-23.md` (총 6개, 1,990 줄)
- v6 활성 자료였음 (3대 강점 카드·3트랙 확장) — 재가동 시 v6부터, 그 전 단계 자료는 의도 변천 학습용
- **다음 세션 진입 시**: 지방정부·전남·미팅 관련 요청 → 보류 정책 환기 → 본인 명시적 결정 후만 자료 갱신/v7 진입

## 5/25 콜 브로커 모델 — 쿠폰앱 식당 영업의 협상 카드로 재정의 (5/19 부분 해제, 정교화)
- **상세**: `memory/coupon/call_broker_bundle_2026-05-25.md`
- **본인 진짜 저의 노출**: "쿠폰앱 영업 시 해당 식당을 묶어서 할까라는 저의가 깔려있어" → 콜 브로커 = 독립 수익 사업 X, **식당 영업 협상 카드**
- **재무 골격** (양평 단독): 콜당 단가 2만원 / 사무실 공유료 15%=3,000원 (시장 관행, 협상 0) / 식당 5%=1,000원 / 본인 10%=2,000원 / 양평 일일 <20콜 → 월 <120만원 → 단독 수익 사업 미달
- **광고 비효율 확정**: CAC 1.5만 → 손익분기 7.5콜. 광고 채널은 체리피커 LTV 낮음. 식당 거점 진입 = LTV 높은 단골 채널. 광고 예산 → 식당 보급 예산 이동
- **양평 = N=1 증명 거점** (수익 X). 콜 브로커는 본질적 광역 모델. 양평에서 식당 4중 노드 작동 데이터(식당 매출 도움 / 손님 식당 권유 진입 / 식당 권유 행동) 모으기
- **식당 영업 매뉴얼 (묶음 깔기, 멘트 단계적)**: 설치 1회 묶음 (쿠폰+콜 5%+포인트) / 멘트 = 1단 쿠폰(진입, 5분, 그 자리 결정) → 2단 콜 환원("사장님 통해 가입한 손님 평생 대리비 5% 자동 입금" — 카카오·망고 못 따라옴, 깔린 후 임팩트) → 3단 포인트(1~2주 후 깊이)
- **식당 4중 노드 비전 처음으로 영업 멘트로 실증** — 마스터 §3.4 추상 비전 → 구체 영업 멘트 ("사장님 통해 가입한 손님 평생 대리비 5% 자동 입금"). 전제는 customer_app Install Referrer 영구 귀속(이미 깔림) + 식당앱 콜 환원 대시보드(5/4 P0 plan 그릇에 추가)
- **5/19 정합**: 충돌 X. 콜마당 단독 부활 X, *식당앱 그릇 안 한 줄기*로 재진입. 쿠폰앱 본 트랙 유지. 광역 보급 정지 유지
- **클코 학습 4건** (토픽 파일 §클코 학습): ① "최소 운영 파이프라인" 단어 그대로 받지 말 것 — 진짜 자동 유입 메커니즘 짚기 ② 본인 결론과 기존 plan 같은 자리면 *추상화 연결*, 부정 없이 확장 ③ 광고비 단가 모델은 *우리 마진 구조*와 안 어울릴 수 있음 — 단위 경제 확인 ④ 본인이 *추가로 속마음 꺼내는 순간* = 결정 본질. 첫 발화에 묶이지 말 것
- **다음 세션 진입 후보**: ① 양평 식당 1곳 영업 시도 (1단 쿠폰 진입) ② 식당앱 콜 환원 대시보드 추가 (5/4 P0 그릇) ③ 콜 브로커 인프라 점검 — 손님 웹앱 + 매니저 수동 + shared_calls 메인 동선 plan ④ 영업 매뉴얼 1버전 작성

## 5/25 ★ PTT 진입 결정 + 콜마당 운영 모델 재정의 (5/19 부분 해제, 병렬 진행)
- **plan**: `C:\Users\kala1\.claude\plans\harmonic-sparking-hedgehog.md` (Phase 1 코드 실측 + 시나리오 8+1 + 작업 범위 5~7주, **본인 승인 + Phase 1 추가 Explore 진입 X 명시**)
- **본인 결정** (자다 일어나 타진): 쿠폰앱 우선 5/19 결정 *부분 해제*. 콜마당 PTT 트랙 + 쿠폰앱 트랙 *병렬 진행*
- **결정 핵심 사실**:
  - 매니저 회귀 본질 = 14필드 수기 입력 (신규 콜 5 + 직접운행 9). 나머지 11개 액션은 이미 탭 기반
  - 기사 인프라 80% 완비 (FCM 라우터 + LockScreenActivity + Foreground Service + 권한 모두 선언 + VoiceInputHelper)
  - 과거 PTT 3.5단계 도달 (8월 commit `6ae77f74` 볼륨버튼 dispatchKeyEvent 완전 제어). 1월 `e5685e66` 5,419줄 제거. *신규 구현 권장* (5개월 경과 + RTM 채널 미완성)
- **운영 시나리오 8+1**: 콜디텍터 자동 배차 → 매니저 PTT 지시 → 기사 손님 통화 → 콜 카드 자율 입력 → 매니저 확인(기본 정상 가정) → 운행 중 PTT 중단 → 매니저+픽업만 송수신 → Data Push 알림 통합 → +채팅 블랙박스(STT 무료, 양방향 인지 보험)
- **무전기 시장 영업 카드**: 4대 월 16만원 → 콜마당 무료 대체. 영업 진입 1분 멘트. *5/19 영업 채널 부재 축 해소*
- **PTT 이전 콜마당 수정 전면 보류** (10여 트랙): cheerful-swinging-turtle / floofy-waddling-marble / 콜 브로커 인프라 / 콜 카드 예약 취소 UI / 픽업 RESERVED / 상태 명명 재설계 / 내부콜 삭제 fix / IAM 누락 / PERMISSION_DENIED 등. **상세**: `memory/coupon/ptt_pivot_other_tracks_2026-05-25.md`
- **유효 트랙 (PTT와 함께)**: ① 간소화 (PTT 자연 결과) ② 정산 이월 단순화 (하루 완결, 매니저 최소 개입 — 5/7 incident 근본 해결)
- **자산화 파일** (`memory/coupon/`):
  - [[ptt_volume_button_principle]] — 볼륨버튼 PTT = 실제 무전기 UX 동등성 (Zello 시범 불가, native만 가능)
  - [[ptt_market_replacement_card]] — 무전기 16만원 시장 영업 카드 (두 트랙 공통 무기)
  - [[ptt_operation_scenario]] — 시나리오 8+1 + 채팅 블랙박스 + UI 대체 매트릭스
  - [[ptt_pivot_other_tracks_2026-05-25]] — 전면 보류 + 유효 트랙 + 정산 단순화 원칙
- **작업 범위**: 5~7주 (단일 개발자 풀타임) — 본인 결정 후 진입
- **다음 세션 진입 시**: ① 본인 plan 재검토 (내일) ② 본인 명시적 결정 후 Phase 1 추가 Explore (콜디텍터 자동 배차 / 콜매니저 알림 매핑 / 콜 카드 작성 흐름 / 권한 분리 / 정산 코드 자리) ③ Phase 2 Plan agent ④ 코드 구현 진입

## 6/11~12 ★ P7 정산 강제 게이트(EnforcementGate) 구현 — 게이트 #1~4 + 부수수정, #5 폐기 (미커밋·기기검증 일부)
- **보급 준비 1순위** P7 = 정산 미마감/미확인 운영 실수 차단. 별 모듈 `enforcement/`(정산 읽기 전용·SRP). 플랜 `~/.claude/plans/woolly-bouncing-hippo.md`. 설계원본 [[settlement_redesign_2026-05-27]] §4·5·9·10.
- **#1/#2 LogoutGuard(driver)**: HomeScreen 로그아웃 버튼 — 미정산(`tripCount>0 && status≠PENDING_CONFIRM`)이면 차단. 퇴근하기(PENDING_CONFIRM) 종료 보존. (HistorySettlement 로그아웃=죽은코드라 불요.)
- **#3 DailyCloseGate(driver)**: VM `needsDailyClose`=**이전 영업일(10시 경계 이전) 미정산 잔존**만 true(단순 tripCount>0이면 근무중 오발동→교정) + AppNavigation 정산화면 1회 강제. **로그검증 통과**(오늘 운행 trips=1→needsDailyClose=false).
- **#4 ManagerUnconfirmedModal(call_manager)**: 미확인 기사 닫기 불가 Dialog(기사별 [정산확인]/[개별검토], [전체확인] 없음). 모달=`hasSubmitted && !isConfirmed`. **confirm=삭제 아니라 `dailySettlement.confirmedAt` 도장**(원규씨 "확인하니 내역 사라지네" 지적으로 1차 삭제구현 교정, 설계 §9.2 복귀) + PENDING_CONFIRM일 때만 WAITING 해제. 실삭제=영업마감만. 기사별 탭 `✅정산확인 완료` 표식.
- **★ 부수수정**: 기사 `clearSettlement`(퇴근하기)의 `dailySettlement` **조기삭제 제거**(원규씨 "콜매니저서 확인도 없이 사라지네") → 퇴근 후에도 매니저 확인 가능. **로그검증**: 1004 업무마감→퇴근(OFFLINE) 후 매니저 `dailySettlements:1` 유지.
- **#5 ManagerCloseGate(10시 마감잠금) = [폐기·원규씨]**: auto-finalize cron(10:10)이 `isFinalized` 서버봉인 자동처리(기사 dailySettlement 안 건드림→#4 무관) + #4가 확인강제 + "매니저 마감누락은 우리 책임 아님·최소개입·단순화"(원규씨) + 10시후 clearAllTrips 날짜불일치 데드락 회피. 소프트리마인더도 안 함.
- **요구확정(원규씨)**: 미확인분 다음세션까지 남고 로그인시 확인 안 하면 업무진행0(닫기불가 모달=의도) / 데이터삭제는 영업마감만.
- **상태**: 두 앱 빌드성공, driver→ZFlip4·call_manager→S21+ install. **미커밋**(`feature/reservation-engine-poc`).
- **검증 실태(누락·오염 검토로 정밀화)**: ✅crash0(양앱) · ✅#3 *조건*(trips=1→needsDailyClose=false 오발동없음) · ✅퇴근 후 요약보존(dailySettlements:1) · ✅#4 모달 표시(원규씨 화면확인). **❌미검증**: #1/#2 로그아웃 *차단*(로그에 차단시도 없음) · #3 *리다이렉트*(이전영업일 시나리오 미발생) · #4 confirm *도장*(구버전 삭제코드로 눌러봐 새코드 미검증) · 영업마감 삭제.
- **다음 최우선 검증**: 새 사이클로 ① #1/#2 미정산 로그아웃 차단 ② #4 [정산확인]→기사별 내역+`✅확인` 보존(삭제 안 됨) ③ 영업마감 때만 삭제. (1004 이전분은 구버전 삭제코드로 증발=흔적.) 상세=결정대장 [[_decisions]] "P7 EnforcementGate" + "#5 폐기".

## 6/1~6/2 ★ PTT 구현 완료 + 3단말 E2E 검증 → 양평 실사용 데이터 수집 단계 (코드 트랙 종료)
- **상세**: [[ptt_implementation_2026-06-02]] + [[ptt_screenoff_send_2026-06-02]] (코드 3 commit + docs, branch manager-direct-drive push 완료)
- ✅ **일방 브로드캐스트 완성** (매니저→일반기사): FCM wake + Agora RTC Trigger Join (RTM 폐기). `0bf9ebe4`(functions Agora토큰+sendPttWake+minInstances:1) / `20c02fc2`(call_manager 송신 hold-to-talk+2비프+발화배너) / `0fb15cd2`(driver_app 수신+종료오버톤).
- ✅ **6/2 3단말 E2E 1:1 통과**: S21+ 송신→Z Flip4 수신 logcat 전 흐름 정상(콜드 press→ready ~1.6초, 종료 오버톤 포함). S22는 5/18 구버전이라 최신 재설치. **fan-out 미검증=버그 아님**(두 수신기 같은 기사 계정→fcmToken 1개 덮어씀; 실운영은 기사별 다른 계정→각자 wake).
- **마무리 처리**(본인): ④ Certificate rotate **skip**(파일럿 위험 무시 가능, "다수 사무실 실배포 전 1회"만) / ① master PR **보류**(master 2025-09-01 방치 9개월·1,970파일 차이, PTT는 이미 origin push로 기록 완료→유실0; 9개월 분기 정리는 별도 세션).
- → **PTT 코드 트랙 종료.** screen-off 송신/비프모델/음성버퍼링 전부 **"현재 빌드로 양평 실사용 → 데이터 후 재결정"**. 코드 후보(데이터 후): 양방향+픽업 / 정산 이월 단순화 / master 분기 정리(별도 세션).
- **클코 학습**: 5/19 휴면이 *기술적 발목 + 영업 발목* 두 축 동시 해소 신호가 한 번에 도달하면 부분 해제. 단 *모든 보류 트랙*에 재진입 신호 아님 — PTT 트랙만 명시. 기타 트랙 진입 시도 시 본 결정 환기 필수
- **5/25 본 turn 후속 결정 (자다 일어나 타진 + 정밀화)**:
  - **구독료 3만원** (본인 명시). 무전기 16만원 대비 13만원 절감 카드
  - **2000사무실까지 Agora 유지** (본인 명시). 안정성·서버 관리 회피·사업 집중 우선. LiveKit Self-hosted 마이그레이션 손익분기는 500사무실 시점이지만 *본인 거부*. 옵션 보존 (향후 자리)
  - **콜마당 전체 인프라 비용 (2000사무실, Agora 유지)**: 약 620만원/월 (Agora가 96% 비중). 사무실당 3,100원. 매출 6,000만원, 마진 5,380만원 = **마진율 89.7% 평탄** (pay-as-you-go 변동비 100%, 규모 효과 없음)
  - **영업 카드 ≠ 제품 정체성** (본인 통찰: "콜마당이 아닌 무전기를 파는 느낌"). 영업 멘트 단계화 = 1단 무전기 비용 대체(진입 5분) / 2단 제품 본질(콜 카드 자동·매니저 부담 0·정산 하루) / 3단 OS 정체성(식당 4중 노드·콜 무수수료·동네 OS). 5/25 §콜 브로커 단계화 패턴 정합. **1단에서 멈추면 콜마당이 싼 무전기 회사로 인식 → 6개월 후 경쟁자에 무너짐**
  - **시스템 이벤트 → 채팅 블랙박스 자동 게시** 추가 (시나리오 9-B). 배차·기사 상태·콜 상태 전이 모두 채팅에 자동 기록. Cloud Functions 트리거에 `chat_messages.add({type:"system"})` 한 줄. 비용 노이즈 (양평 무료 한도 안). 외부 PTT 앱이 못 하는 자리 — 콜마당 자체 인프라라서 가능
  - **Data Push 라우터 정합 정정**: 채팅도 *기존 listener 재사용*이 아니라 *Data Push 라우터 안*. 콜마당 listener 최소화 정책 유지 (driver_app carryOverListener 1개만)
  - **클코 학습 4건** (메모 2 §클코 학습): ① 영업 카드 ≠ 제품 정체성 분리 ② 본인 결정 기준 = 시간·정신·안정성 (사업가 사고) ③ pay-as-you-go SaaS 마진율 ↑는 변동비→고정비 전환 필요 ④ 클코 오염 정정 (listener 재사용 표현 위반)
  - **상세**: `memory/coupon/ptt_market_replacement_card.md` (대폭 보강) + `ptt_operation_scenario.md` (시스템 이벤트 + Data Push 정합 + 영업 연결 추가)

## 5/27 정산 재설계 세션 — 블랙아웃 복원 + 본인 결정 누적 (★ PTT plan §3 실행 매트릭스)
- **본 세션**: 컴퓨터 블랙아웃 후 마지막 세션(5/25 19:55 db9cb9ba) 복원으로 시작 → 5/25 settlement_logic_definitive 33KB 정독 + 검증 → 본인 결정 누적 → 책임 분리 정정 → STT 자산 재배치 결정
- **자료 3종 (책임 분리)**:
  - 코드 사실 단일 출처: `memory/designated_drive/settlement_logic_definitive_2026-05-25.md` (§1~9 + §13~14, 본문 정정 반영)
  - **재설계 단일 출처**: `memory/designated_drive/settlement_redesign_2026-05-27.md` (본인 결정 + Zero-base 모델 + 강제 게이트 인벤토리 + 코드 모듈 분리 원칙 + 5/7 incident 후보 + 잠재 문제 + PTT plan §3 연결)
  - 세션 인계 스냅샷: `memory/designated_drive/settlement_session_2026-05-27.md` (블랙아웃 복원·명문화 검증·결정 누적·미완료 작업·클코 학습 5건)
- **명문화 검증 결과**: 5/25 작업이 코드 정독 기반 (Read 12 / Grep 3 / 검증 근거 파일 10 중 8 ✅ Read, 2 △ Grep). DriverViewModel + index.ts 직접 정독 → 라인 자리 100% 정확 + 누락 디테일 3건 정정 반영 (definitive §6.2 + §6.5 + §7.2)
- **본인 두 원칙**: ① 최소 개입 ② 다음 날 새로 시작
- **본인 결정 (Q1~Q3 + Q2-2)**:
  - Q1 영업일 **오전 10시** (코드 새벽 6시 → 변경, calculateWorkDate + autoFinalize cron)
  - Q2 강제 게이트 설계 — 실수 패턴 12개 차단 ("원인 특정 부차, 모든 경우 차단이 주")
  - Q2-2 **이체·수령 절차 폐기** (transferCarryOver / confirmReceiveCarryOver / carryOver.status 머신 / 매니저-기사 송금 절차 코드 밖)
  - Q3 **그날 끝 Zero-base 모델** (carryOver 변수 자체 코드에서 완전 제거)
- **책임 분리 결정 (본인 통찰 "누더기 우려" + SRP)**: 정산 로직 ↔ 차단 기능 분리. 2 파일 분리 (definitive 사실 + redesign 설계) + 코드 모듈 분리 (SettlementCalculator/Calc/ts ↔ EnforcementGate 모듈 신규) + definitive §10/§11/§12 redesign 이전 (5/27 추가 정정)
- **STT 자산 재배치 결정**: 내부콜 STT/음성메모 → PTT 발화로 대체 → 해제된 자산(CallMemoParser Phase B-ext 완료 + Firebase Storage 음성메모)을 **공유콜 업로드 흐름으로 전환**. 자산 폐기 0, 자리 이동만. 채팅 블랙박스(PTT plan §9)는 별개 트랙
- **강제 게이트 인벤토리** (실수 패턴 12 × 게이트):
  - ✅ 자연 해소 2 (#6, #7 — 이체·수령 폐기)
  - 🟢 이미 작동 2 (#8 calculateWorkDate+isFinalized, #10 rejectDailySettlement)
  - 🟡 부분 작동 3 (#3 driver.status=PENDING_CONFIRM 배차 차단 *이미*, #4 cleanup banner, #11 cron 있음)
  - 🔴 신규 4 (#1 logout 가드, #2 logout 차단, #5 매니저 일괄 마감 강제, #9 #5에 묶음)
- **강제 게이트 본인 결정 (5/27 확정)**:
  - **#4 모달화 강도** = 옵션 A 닫기 X 모달 + (a) 개별 정산확인 + (b) 개별 검토. [전체 확인] 1버튼 제거. 본인 발화 "정산확인은 최소개입과 상관없잖아 — 매니저 본질 책임"
  - **#5 영업일 종료 강도** = X1 (10시 즉시 잠금, 알림 X). 새벽 2-4시 마감 패턴이라 grace 불필요
  - **#12 기사 수치 조작 검증** ✅ **폐기** — 코드 정독 결과 자동 검증 자리 자체가 없음 (서버 calls 기반 자동 계산, 기사 입력값은 `realDeposit` 1개뿐, 정상 변동값)
  - **§11.5 "현금+포인트" 코드 버그** ✅ **폐기** — 코드 정독 결과 변수 이름만 다르고 결과 동일. cashAmount=null/0 케이스 동일 처리
- **PTT plan §3 매트릭스 세부 결정 (5/27 후반)**:
  - **dailySettlement.status 단순화** = 옵션 B (2상태 SUBMITTED ↔ CONFIRMED, 도장 1개). REJECTED 별도 상태 X, 재제출 시 덮어쓰기
  - **confirmDailySettlement** = 옵션 A (축소). 매니저 [정산확인] 액션 유지(#4 정합), 부수 효과(이체 알림 등) 제거
  - **외상 채권 Firestore 동기** = PTT §3 안 자연 자리 (별도 트랙 X). 신규 컬렉션 `offices/{o}/creditPersons/{phoneNumber}` + Room↔Firestore 동기 로직. 이유: 외상 명단은 *그날 무관*, 받을 때까지 살아있어야. 현재 Room 로컬만 → 매니저 디바이스 분실 시 외상 명단 휘발 위험. driver_app PendingSyncEntity 손실 빈도 작은 자리도 함께 검토
- **클코 학습 (본 세션 추측 패턴 적발 2건)**: #12 + §11.5 모두 클코의 *변수 이름·UI 자리만 보고 추측 패턴*. 본인 "실제 코드 확인해봐" 지시 → 폐기. 5/25 §피드백 실효성 입증 (코드 그대로 명문화 → 추측 X)
- **코드 진입 시점**: 5/19 결정 ("PTT 이전 콜마당 수정 전면 보류") 유지. 본 결정들은 *문서 차원*만, 코드 변경 0. 실제 코드 작업은 PTT plan `harmonic-sparking-hedgehog.md` §3 진입 시점에 일괄

## 5/27 (★ 본 세션) PTT §3 코드 진입 P3·P4 + P1 폐기 결정
- **plan**: `C:\Users\kala1\.claude\plans\zippy-bubbling-kettle.md` (Plan 모드 본인 승인, 자연어 위주 7 commit 진입 흐름)
- **본 세션 commit 2건** (manager-direct-drive, push 대기):
  - `cf7226ac` feat(settlement): PTT §3 P3·P4 — 영업일 시간 6시 → 10시 (functions cron + handler workDate + 매니저 SettlementViewModel + 기사 DriverViewModel, 8 파일 +18/-38)
  - `be012355` docs(memory): PTT §3 진입 결정 + P1 폐기 결정 (`ptt_section3_entry_2026-05-27.md` 신규 +120, §7 P1 폐기 결정 본문 포함)
- **본인 의문 적발 P1 폐기**: 외상 Firestore 동기 자리 진입 → 빌드 OK 후 본인 "외상 왜 동기? 비용은?" 의문 제기 → 클코 *self-loop + 비용 + 양평 현재 정산 사용 X* 자리 인정 → 4파일 rollback. 외상 백업 본질은 살아있되 미래 식당앱 + 다른 사무실 보급 시점에 자연 재고.
- **plan agent 누락 1자리 발견**: call_manager SettlementViewModel.kt:1029 `getTodaySessionDate()` 자리. P3·P4 commit에 함께 포함
- **클코 학습 누적** (`ptt_section3_entry_2026-05-27.md` §7.5):
  - plan agent 결과 = 기술 차원만. 운영 차원 (비용·self-loop·현재 사용도·미래 활용·PTT 활성 후 정합·보급 정합) 사전 검토 클코 책임
  - 본인 발화 *현상*만 받고 *원인 임의 추측* 금지. 명시 이유 직접 인용 또는 물어볼 것
  - 본인 결정 자리 *임의 해석 폐기 권장* 금지. 본인 결정 *그대로 살아있다*가 default
  - *부분 의문 → 전체 폐기 비약* 금지
- **본인 자리 남은 작업**:
  - functions deploy = `firebase deploy --only functions:autoFinalizeSettlements` 등 calculateWorkDate 호출자 함수 모두 (정확한 함수 자리는 다음 세션 Grep 후 안내)
  - 양평 단말 install 선택 (정산 현재 사용 X, install 우선순위 작음)
  - git push 본인 OK 자리 (5/27 종료 시점 push 안 함, 다음 세션 진입 시 결정)
- **다음 세션 진입**: P5 (정산 상태 4개 → 2개 `SUBMITTED ↔ CONFIRMED` + REJECTED 폐기 + confirmDailySettlement 축소). plan `zippy-bubbling-kettle.md` §"2) P5 commit" 본문 참조

## 5/27 (★ 후속 세션) Hosting 정리 14.34 GB 회수 + 다음 세션 P5 진입 안내 (본인 요청)
- **본인 의문 두 자리 사실 정정**: ① 함수 대대적 변경? = YES (P5~P8 settlement.ts 절반 폐기 + EnforcementGate 신규 + PTT §시나리오 8 Data Push 라우터) ② Functions가 Hosting 용량? = **NO, 별개 회계** (Functions 본문 71 MB vs Hosting 14.8 GB)
- **14.8 GB 진짜 정체**: Hosting 배포 히스토리 누적 (calldetector-5d61e 166 versions × APK 220 MB = 13.8 GB)
- **Hosting REST API 일괄 DELETE 진행**: ADC token + `x-goog-user-project` 헤더 + 보존 정책 = 각 사이트 live + 직전 DEPLOY 2개씩. **206/206 성공, 14.34 GB 회수, 166초**. 보존 6 versions (~513 MB). calldetector 164 / callmadang-web 33 / head-manager 9 삭제
- **콘솔 반영**: 통상 수 분~수십 분 지연. 14.8 GB → ~500 MB, 무료 4.8 GB 한도 안 안전
- **부수 권장 (별건 트랙, 미결)**: APK hosting 동봉 정책 = (A) 분기별 정리 cron vs (B) APK Firebase Storage/GitHub Release 분리. 본인 결정 자리
- **P3·P4 deploy 유보 결정**: 본인 의문 "현재 deploy 의미?" → 클코 사실 검증 → 유보 권장 채택 (P5~P8 묶음으로 통합). commit `cf7226ac` 이미 origin push 완료 (0/0 동기화 확인). 양평 정산 실 사용 X → 6시→10시 운영 impact 0
- **다음 세션 P5 진입 안내**: 상세 본문 `memory/designated_drive/ptt_section3_entry_2026-05-27.md` §8.2 (P5 본질 + 코드 자리 4개 + 진입 흐름 5단계 + 비용·운영 사전 검토 + 본인 두 원칙 정합 점검). 다음 세션 시작 시 본 단락 + §8.2 정독 후 본인에게 설명 자리 마련

## 5/28 PTT §3 commit 1 ✅ — 확인/이체/거절 함수 + UI + FCM + cleanup gate 폐기
- **commit `e7403ded`** (manager-direct-drive, 13 files / +152 / -1622 / 순감 1470줄, push X — plan §10 정합 = 4 commit 모두 완성 후 한 묶음)
- **본 세션 자체 검증 4건 통과**: git diff vs §9.1 명단 일치 / Grep §9.2 line 정확 + 누락 1건 발견 (DriverViewModel.kt:1533 `_carryOver` 호출자, commit 2 자리) / functions npm run build ✅ (이전 세션 unused import `buildFcmPayload` 1줄 fix) / call_manager + driver_app assembleDebug ✅
- **plan**: `C:\Users\kala1\.claude\plans\refactored-tickling-codd.md`. 다음 세션 진입: commit 2 (enum + data class). 자체 검증 의무·line 재검증 4건: `memory/designated_drive/ptt_section3_entry_2026-05-27.md` §9 (§9.1.1 본 세션 결과 + §9.1.2 commit hash + §9.7 자체 검증 + §9.4 #7 학습)
- **본 commit 후 잔존** (commit 2 자연 흡수): SettlementViewModel.kt:740-756 unused 자리 (`driverShare` / `cashReceived` / `driverTotalFare` / `driverDeposit` — `clearDailySettlement` 호출만 살리려 forEach 자리 살림, 컴파일 warning 만)
- **클코 학습 §9.4 #7 (본 세션 핵심)**: 자기 모니터링 한계 = 본인 짚음 의존 (plan 권위 부정 / "자기 자기" 발화 톤 / 인계 line 자동 shift 미반영 / 본인 답 자리 자기 회고로 메움). §9.5 피드백 4종 승격 권장 (다음 세션 첫 turn 본인 결정)

## 5/29~30 PTT §3 commit 2·3·4·5 ✅ — enum/data class + 외상 폐기 + 기사 정정 + 실납입/환급 폐기 (1~5 전부 push 완료, rules deploy 완료)
- **commit 2 `3b3d808b`** (16 files +113/-1695, 순감 1582): enum(DailySettlementStatus/CarryOverStatus/SettlementFilter) + DriverCarryOver* data class + DriverDailySettlement 16→8필드 + SettlementCalc carryOver 3종 폐기 + carryOver UI·계산 정리 + dead state(_carryOver 등) 회귀 수정. 양 앱 main+test BUILD SUCCESSFUL.
- **commit 3 `6defedb1`** (16 files +7/-1059, 순감 1052): **외상 전체 폐기(옵션1)** — Room v7→v8(CreditPersonEntity/CreditEntryEntity 제거 + creditDao 폐기) + dead 체인 5파일(SettlementDatabase/SettlementCacheRepository/dao.SettlementDao/entity.CreditEntity·SettlementEntity) + per-call 처리(PendingSettlementsScreen 탭1 / CreditManagementScreen 탭4 / CreditDialog) + SettlementTabHost 3탭(전체/기사별/일일) + MainActivity initTab 2→1 + ViewModel 외상 자리 전부 + AllTripsScreen 이체/외상 경고. **driver_app 무변경**(creditAmount는 미수금 통계용 유지), **directRunTrips 동결**(§10.4). 양 앱 main+test BUILD SUCCESSFUL(실측).
- **push X / deploy X** — 4 commit 묶음 완성 후 1회(plan §10). commit 1·2·3 = `e7403ded`·`3b3d808b`·`6defedb1`.
- **commit 4 ✅ `f14ab6d6` (push+rules deploy 완료)**: #2 기사 콜 정정 모달+`updateCallPaymentMethod`(현금/외상/이체 3종, 제출 전·포인트 무관 게이트, Firestore 기록 후 refreshSettlementData 재로드) + #3 firestore.rules(기사 본인 COMPLETED 콜 결제필드 화이트리스트) + #5 매니저 dailySettlement 재로드 복원(기존 designated_drivers fetch 재사용, 리스너 0 / DriverSummaryScreen 제출 요약 표시) + #6 메모리. **#4(autoFinalizeSettlementSessions)는 이미 단순=무변경**. 양 앱 main+test BUILD SUCCESSFUL(실측). **#1 결제 다이얼로그·directRun은 보류 유지**. ★오염 검토 결정 A: settlementSessions·dailySettlement 정정 전파 안 함(제출 전 정정만 허용→제출본 정합, 라이브 통계는 calls 직접이라 반영) → 함수 변경 0. 상세 = ptt §11.4.
- **#1 분리 근거(ptt §11.2-A)**: HomeScreen 5버튼/pointsUsed = **기존 손님 적립 포인트 결제**(식당 4중노드 + customer_app/points.js, **쿠폰앱 아님**) + 스코프 밖 + 회귀 위험 + 설계 충돌(§3.2 vs §9.4). ⚠️ 최초 "쿠폰 트랙" 단정 = 클코 오류(§9.4 #7, 정정+feedback_self_monitoring_limit.md 승격).
- **commit 5 ✅ `7fdd9314` (push 완료)**: **실납입/환급 개념 폐기** → 기사 정산 "납입금(net=사무실몫−외상, 부호) 한 줄"(실납입 카드/정정/환급·미납 폐기) + `submitDailySettlement` net 기록·settlementDiff=0 + 매니저 #5 net 표시 + SettingsScreen/FCM 라벨. **★단말 검증 발견·수정**: `SettlementDao` `IGNORE→REPLACE`(매니저 callsListener가 기사 정정을 Room 교체 못하던 버그 — 이제 실시간 반영). 방식=Option B(realDeposit/settlementDiff 필드 유지·값 단순화)라 functions·rules·Room 스키마 무변경. 양 앱 빌드+단말(기사 net 추종/매니저 실시간 교체) 검증 ✅. 상세 = ptt §11.5.
- **commit 1~5 전부 push 완료**: `e7403ded`·`3b3d808b`·`6defedb1`·`f14ab6d6`·`7fdd9314` (rules deploy=commit4 1회). **다음 = 보류 큐**(ptt §11.6 — ★매니저 수동 업무마감 즉시 봉인(isFinalized)·#1 결제 다이얼로그·cosmetic·redesign 문서 정정). **진입 가이드 단일 출처**: ptt §11.4·§11.5·§11.6·§11.2-A·§11.3. plan: `woolly-fluttering-feather.md`.

## 6/1 PTT 진입 준비 세션 (코드 변경 0 — 메모리·plan 정합화만)
- **볼륨버튼 블랙아웃 정체 확정**: 송신측은 완전 꺼짐+볼륨다운 2회로 PTT 전환 *성공*(자산). 수신측 Doze가 과거 "Not connected"(`23a975b4`)의 진짜 지점. 상세 [[ptt_volume_button_principle]] §"과거 실패의 정확한 정체"
- **알림 wake 패러다임 확정 (본인 교정)**: 발화 시 high-priority FCM Data Push로 수신측 wake → Agora *Trigger Join*. always-join은 listener 최소화 철학 위반이라 *배제*. 1순위 위험 = wake→fast-join 핸드셰이크 신뢰성. ⚠️ 클코가 상시 join을 메인 권장한 오류 → 본인 적발 → feedback `paradigm_over_analogy` 박음(피드백 12→13개)
- **함수 개수 점검**: ~68개(CLAUDE.md "41개" outdated). *개수 자체는 비용·할당량 무관*(gen2 호출 기반 과금). 테스트/일회성 4개(testFcmMessage/migratePickupDrivers/migrateExistingOfficesWallet/backfillChatMembers) PTT commit에 *편입 보류*(본인 결정). 명세 = plan `C:\Users\kala1\.claude\plans\unified-sniffing-peacock.md`. 쿠폰앱 분리는 functions 무관(coupon 함수 0개)
- **PTT 자료 정합화 4/10→8/10**: 마스터 plan `harmonic-sparking-hedgehog.md`에 "★2026-06-01 갱신" 섹션 역병합 + `ptt_operation_scenario` §8 음성 전달 모델 1문장 + `ptt_market_replacement_card` 비용표 각주(620만원=상시 join 가정, Trigger Join 시 하향, Phase 2 재계산). **단일 진입점 = 마스터 plan(6/1 섹션) + [[ptt_volume_button_principle]]**
- **다음 세션 진입**: Phase 2 Plan agent (8+1 시나리오 → 코드 구현 매핑, **FCM wake→fast-join 핸드셰이크 신뢰성 1순위**). 무거운 설계라 신선한 세션 권장. "Phase 2 진입해줘" 한마디로 시작

## 6/2 ★ PTT 구현 완료 — PoC + UX 증분 작동·커밋·배포 (일방 브로드캐스트 루프)
- **상세**: `memory/coupon/ptt_implementation_2026-06-02.md`
- **3 commit push** (manager-direct-drive): `0bf9ebe4` functions(generateAgoraToken+sendPttWake+minInstances+agora-token) / `20c02fc2` call_manager 송신(hold-to-talk+2비프+발화중 배너) / `0fb15cd2` driver_app 수신(ptt_dispatch wake→fast-join+종료 오버톤). functions 배포 완료.
- **검증** (S21+ 송신/Z Flip4 수신 화면꺼짐): 콜드 ~1.4초 / 웜 즉시 / "Not connected" 0. minInstances로 sendPttWake 2초→0.4초.
- **UX 모델**(본인 공동설계): 탭→hold-to-talk(떼면 종료, stuck-on 0) / 시작 2비프=연결창(2차 비프=onUserJoined ready, 마이크는 합류 후만 라이브→첫 음절 0) / 종료 1비프=오버톤(양쪽, 무전 관행) / 5초 종료-기준 워밍창(비용 다이얼·상수, 지연은 비프가 가림, 진짜 가치=재-wake 회피 안정성).
- **★ 함정 3건**: ① **broadcaster role**(LIVE_BROADCASTING audience는 매니저 onUserJoined 미발화→3초 타임아웃, 수신측도 broadcaster+마이크미publish로 해결) ② **getApplication()/applicationContext가 onCreate에서 null**(이 S21+ 단말)→엔진은 발화시점 Activity 컨텍스트로 생성 ③ 콜드 5.5초=함수 콜드스타트×2직렬→minInstances.
- **Agora**: project DesignatedDriver-PTT, App ID `e5aae3aa…`(공개), Certificate=Secret Manager만. ⚠️ 다수 사무실 실배포 전 rotate 권장.
- **★ 운영 전제 정정(6/2 본인)**: 매니저는 픽업 병행 = **운전 중 사용** → screen-off 송신 *필수*(거치형 아님). 현재 dispatchKeyEvent foreground 전용은 실사용 미스매치. call_manager 홈페이지 사이드로드라 **Accessibility 권한 자유**(Play Store 정책 무관). 과거 screen-off 송신(Accessibility `17bcd8b3`)은 *성공 자산*, 제거는 수신측 RTM 실패 때문(이번 해결) → **복구 가능**. foreground 밖 볼륨키 캡처는 전역 후크만 가능(샷컷 없음): Accessibility(신뢰↑·재허용 마찰) / MediaSession(권한0·충돌) / BT 이어피스 버튼(운전 정석). 상세 [[ptt_implementation_2026-06-02]].
- **★ screen-off 송신 = 보류(데이터로 판단)** + **콜드스타트 정리**: 상세 [[ptt_screenoff_send_2026-06-02]]. ① 스코프 정정: 일반기사=수신만(화면off 이미 작동·설정0), 송수신=콜매니저+픽업=내부직원 소수(개인폰+내비 운전 중). ② 트리거 전부 흠: Accessibility(확실하나 제한설정+삼성 재비활성 부담, 본인도 헛갈림) / MediaSession(내비 충돌 탈락) / 일반 BT버튼(모호) / 전용 BT PTT버튼(확실·하드웨어 2~3만원). → **강제 Accessibility 안 함**, 현재 빌드(foreground 볼륨키 송신)로 양평 실사용 후 "달리며 송신 정말 막히나" 데이터로 결정. ③ 콜드스타트("잠긴 시간↑→연결↑") = 함수 콜드 아님(minInstances 제거됨), **수신측 Doze**. 레버 = **배터리 최적화 예외 허용**(1탭, 가벼움). 2비프가 이미 가려 *느림 체감이지 실패 아님*.
- **별도 트랙(미진입)**: 양방향+픽업 / 함수 1콜 통합+토큰 FCM 동봉 / 정산 단순화 / call_detector 연동 / 상태명명 재설계.
- **다음 후보**: 양평 실사용(달리며 송신 빈도·Doze 콜드 체감 데이터) / master PR / 양방향+픽업 설계 / S22 포함 3단말 + 오버톤 청취 재확인 / Certificate rotate.

## 도메인별 진입점
| 도메인 | 인덱스 | 상태 |
|--------|--------|------|
| **본 에이전시 헌장 (클코, 작동 단일 원본)** | `memory/operating_model/clcode_agency_charter.md` | v1.0 (2026-04-27) |
| **★ 사용자 행동 최소 = 제1원칙 (OS 전체 기준선)** | `memory/operating_model/principle_user_action_minimum.md` | 확정 (2026-05-25) |
| Cowork 보조 도구 가이드 (구 Cowork Charter v1.0) | `memory/operating_model/cowork_claude_charter.md` | superseded → 클코 헌장에 인수 |
| OS 마스터 | `memory/callmadang_master_2026-04-27.md` | 정본 |
| OS 체크리스트 | `memory/callmadang_checklist_2026-04-27.md` | 매일 도구 |
| 대리운전 (대리/픽업, 첫 사용 케이스) | `memory/designated_drive/README.md` | 운영 중 (양평 1곳) |
| 택시 (마스터 §6) | `memory/taxi/README.md` | v0 진입 준비 |
| 식당 (마스터 §3.4 4중 노드) | `memory/restaurant/README.md` | 업소용 앱 1순위 (손님앱 코드 변환 결정 2026-04-27) |
| 배달 (T2) | `memory/delivery/README.md` | placeholder |
| 쿠폰 (T2) | `memory/coupon/README.md` | placeholder |
| 최종 손님앱 (T최종) | `memory/customer_super_app/README.md` | placeholder |
| 사고 모드 (피드백 9개) | `memory/feedback/README.md` | 도메인 공통 |
| 사용자 프로필 | `memory/user_profile/README.md` | 도메인 공통 |
| **시나리오 (인생 트랙, 별개 프로젝트)** | `C:\Users\kala1\movie\CLAUDE.md` | 작가성 척추 5/12 갱신, 콜마당과 메모리 완전 분리 |

## 최근 달성 (5개)
- ✅ 2026-05-07 **운영 fix 3종 + 직전 commit 2건 반영** (`ae2ab55b` + `993681b0` push 완료, IAM/인덱스/트리거는 직접 실행). (1) **`ae2ab55b` Cloud Tasks 이벤트 기반 timeout** — `checkAssignedTimeout` 매분 전국 풀스캔(reads 25만/일) 제거 → `oncallassigned` 트리거에서 1분 deferred task enqueue → 단일 콜 검증. `functions/src/handlers/timeout.ts` 신규(checkSingleCallAssignedTimeout 활성, presence/in-progress 분기 휴면 — index.ts export 주석), `oncallassigned` 안에 `enqueueAssignedTimeoutTask` try/catch FCM 보호. ~330줄 폴링 코드 삭제. 효과: reads/일 25만→~150-300 (99.9%↓). 이전 세션이 우려했던 `functions/src/index.ts 380줄 미커밋`은 이 commit에 흡수됨(확인 완료). (2) **`993681b0` cleanup gate banner** — 매니저 dashboard 진입(=재로그인 시점)에 PENDING_CONFIRM 잔류 정산 자동 인지 + 1클릭 일괄 확정. 5/7 incident "마감대기중 잔존" self-heal. `DashboardViewModel.refreshPendingConfirmCount` + `confirmAllPendingDailySettlements` + sticky banner UI([전체 확정][개별 검토][나중에]) + `SettlementViewModel` filter 어제 PENDING_CONFIRM 매니저 화면 잔류(휴무 기사 dead lock 해소). 일괄 confirm 시 driver doc `dailySettlement.calculatedCarryOver` 사용(통합 패턴 정확값). driver_app/server 변경 0. (3) **5/7 운영 fix 3종 (오늘 실행)** — ① IAM: `gcloud projects add-iam-policy-binding calldetector-5d61e --member=serviceAccount:60275310305-compute@developer.gserviceaccount.com --role=roles/cloudtasks.enqueuer --condition=None` (gen2 default Compute SA). **사용자 직접 실행 필수** (sandbox 권한 상승 차단). 효과: `oncallassigned` task enqueue silent fail 해소, 3분 미수락 자동 WAITING 복귀 작동. ② Firestore composite index: `gcloud firestore indexes composite create --collection-group=calls --query-scope=COLLECTION --field-config="field-path=status,order=ascending" --field-config="field-path=completedAt,order=descending"` (**PowerShell 콤마 파싱 회피로 따옴표 필수** — sandbox는 production index 생성도 차단). 기존 `firestore.indexes.json` 정의는 `completedAt:ASC` 였으나 archiveOldCalls 코드는 `.orderBy("completedAt","desc")` → **방향 어긋남이 진짜 원인** (직전 메모리 기록 "ASC+ASC"는 잘못이었음). 결과: `status:ASC + completedAt:DESC` READY. ③ archiveOldCalls 즉시 트리거: `gcloud scheduler jobs run firebase-schedule-archiveOldCalls-asia-northeast3 --location=asia-northeast3 --project=calldetector-5d61e` exit 0 → 4/29~5/6 8일 누적 백그라운드 정리. (4) **남은 작업**: 5/5 후속 사용자 환경 5단계(flutterfire configure / build_runner / Z Flip4 식당앱 빌드+설치 / S21+ call_manager 재빌드 + wallet UI 검증 / Plan §10 통합 E2E 14종) + 미커밋 70개 중 homepage 인증 게이트 트랙(14일 dangling, `homepage/auth-gate/` + `functions/scripts/copy-homepage.js` + `functions/lib/public/` + firebase.json hosting + express deps) 별도 commit 또는 격리 결정. (5) **PR 후보 (별도 세션 미진입)**: `finalizeSettlementSession` 안에 PENDING_CONFIRM 일괄 CONFIRMED 처리(개별 검증 건너뛰기 위험 평가 필요) — `993681b0` cleanup banner로 일부 self-heal됐으나 finalize-side 구조 fix는 미진입.
- ✅ 2026-05-05 **콜마당 P0 PR 1+2+3 + H2 코드 작업 완료** (10 commits push, plan: `C:\Users\kala1\.claude\plans\abundant-floating-island.md`). 코드 트랙 전부 종료 — 5/6 후 사용자 환경 작업만 남음. **H2 사장님 가입/로그인 (Invite-Token + Email/Password)** 미커밋 누적 commit `2fe5a088`(9 파일 +633/-517) — 메모리 `h2_invite_signup_2026-04-20.md`에 완료로 기록되어 있던 코드를 PR 1 `1f0576be` registerOwner wallet 초기화 후 호출자 측 동기화. **PR 1**: firestore.rules + restaurant.ts(callable 4종) + wallet.ts(callable 3종) + migrateExistingOfficesWallet + points.ts(processRestaurantCallPayout/checkOfficeWalletForClaim) + index.ts(registerOwner wallet 초기화 + onSharedCallClaimed 잔액 검증[**식당앱 콜 sourceRestaurantId 한정** — 마이그레이션 윈도우 안전] + onSharedCallCompleted paymentMethod 분기). **PR 2**: restaurant_app_flutter fork(`com.designated.customer.app` 임시 유지) + features/auth 단순화(Phone Auth 제거, FCM 토큰 → restaurants/{rid}) + features/restaurant(entities/repository/4 notifiers/6 screens + service + payment_picker) + go_router redirect(ids null → /signup) + Android queries DIAL + iOS LSApplicationQueriesSchemes tel. **PR 4 사실상 통합 완료** (payment_method_picker UI + paymentMethod 분기 backend·frontend 모두 작성됨). **PR 3 (call_manager wallet UI)** commits `c07fe798`(7 파일 +1059/-2) + `e420e213`(review fix +14/-2) — WalletViewModel/WalletScreen/DepositGuideScreen/WithdrawalRequestScreen + MainActivity Screen enum 3종 + DashboardScreen TopBar 지갑 아이콘 + firestore.rules `system_config/{docId}` 추가. **Plan 정정 (코드 실태 발견)**: MainDashboardScreen + AppNavGraph는 dead code → 손대지 않음, 진입은 DashboardScreen TopBar로. PointRepository(357줄) 이미 완비 → UI만 작성. **assembleDebug BUILD SUCCESSFUL**. **5/6 의존성 재검토 (2026-05-05)**: 원래 "5/6 후"로 미뤘던 작업 모두 임시 deposit_account 값으로 **오늘 일괄 처리 가능**. 진짜 5/6 의존은 "양평 첫 식당 실 영업 시작" 1건뿐. **오늘 일괄 처리 (8단계)**: ① `firebase deploy --only functions,firestore:rules` 직후 `migrateExistingOfficesWallet` 호출 (양평 사무실 +30,000) ② Firebase Console 식당앱 신규 등록 → google-services.json + GoogleService-Info.plist 교체 ③ applicationId/namespace + Kotlin source 디렉토리/package + iOS Bundle ID 6곳 → `com.designated.restaurantapp.app` ④ `flutter pub run build_runner build --delete-conflicting-outputs` (freezed/g 생성) ⑤ **`node functions/scripts/set-deposit-account.js 농협 000-0000-0000-00 "콜마당 (임시)" [010-XXXX-XXXX]`** — 임시값 set (멱등 admin SDK 스크립트). 내일 진짜 인자로 재실행 1회 → 4 필드 즉시 교체, call_manager 화면 재진입 시 자동 반영(앱 재시작/재배포 X) ⑥ Z Flip4(R3CT80K78NP) 식당앱 빌드+설치 ⑦ S21+(R3CR312MB1L) call_manager 재빌드+설치 + wallet UI 단위 검증 ⑧ Plan §10 통합 E2E 14종. **commits**: `1f0576be`(PR 1 백엔드) → `cefa9291`(PR 1 잔액 fix) → `8bf592b8`(PR 2 WIP fork+Step 2a/2b) → `cde9d665`(Step 2c.1 state+notifiers) → `c243fcdf`(Step 2c.2 화면 6+service) → `ab95c955`(Step 2d 라우팅) → `1e15a678`(Step 2e tel: queries) → `639218bb`(docs MEMORY) → `2fe5a088`(H2 일괄) → `c07fe798`(PR 3 wallet UI) → `e420e213`(PR 3 review fix). **알려진 제약 (PR 3 commit 메시지에 기재)**: WalletViewModel/ChatViewModel은 SharedPrefs 1회 read 패턴 → 동일 활동에서 다른 사무실로 재로그인 시 stale ID 가능 (기존 패턴, PR 3 범위 밖). DashboardViewModel + WalletViewModel 둘 다 PointRepository 별도 인스턴스 → 동일 사무실 listener 2개 (limit 10, 비용 미미). **5/5 후속 (commit `e1409c64`, 4 파일 +108/-9, push 완료)**: 1단계 — `firebase deploy --except functions:homepageGate,hosting,storage,firestore,database` 후 firestore:rules 별도 deploy(firestore.indexes.json messages collectionGroup single-field 인덱스 충돌 회피) + `--except functions:homepageGate` 가 firebase CLI에서 무시되어 homepageGate 부수 update됨(firebase.json hosting 미커밋이라 calllink.io.kr 정적 hosting 그대로 → 사용자 영향 0) + `functions/scripts/run-migrate-offices-wallet.js` 신규(admin SDK + ADC, callable 동일 멱등 키 `signup_bonus_${officeId}` → 추후 callable 재호출 시 skip) → 5개 사무실 SIGNUP_BONUS 적립(양평 `nEkf0X9g3LZtRX94Mrzu` 31,900→61,900, `3j8hFZYvqx92T0IyCpeI`/`BqFCRiCTYwiXnSCTEA8D`/`OyLNNY8GbFExPHHLkuMK`총알대리/`RUbeBEvGGYP5wMhJHhMF`1004 모두 0→30,000). 3단계 — restaurant_app_flutter 패키지 ID 일괄 교체(`com.designated.customer.app` → `com.designated.restaurantapp.app`): build.gradle.kts namespace+applicationId, project.pbxproj 5곳(main 2 + RunnerTests 3), MainActivity.kt 폴더 이동(customer_app_flutter → restaurantapp/app)+package 선언, GoogleService-Info.plist는 flutterfire가 덮어쓸 예정이라 손대지 않음. 5단계 — set-deposit-account.js 임시값 set 완료(농협/000-0000-0000-00/콜마당 (임시)/010-0000-0000), 내일 진짜 인자로 1회 재실행하면 4 필드 즉시 교체. **사용자 환경 인계 (남은 5단계)**: 2단계(`flutterfire configure --project=calldetector-5d61e`, flutter SDK + flutterfire_cli 필요), 4단계(`flutter pub run build_runner build --delete-conflicting-outputs`), 6단계(Z Flip4 R3CT80K78NP `flutter run -d R3CT80K78NP`), 7단계(S21+ R3CR312MB1L call_manager 재빌드+설치 + wallet UI 검증 — 잔액·deposit_account·withdrawalRequests PENDING 3종), 8단계(Plan §10 통합 E2E 14종). **인계 위험**: functions/src/index.ts 380줄 미커밋 발견(시작 git status에 누락) — 빌드 산출물 lib/index.js와 production은 동기화됨, 다음 세션에서 src diff 검토 후 commit 필요. **다음 세션 정리 사안**: homepage 인증 게이트 트랙(4/23 시작, 12일 방치, dangling — `homepage/auth-gate/`+`functions/scripts/copy-homepage.js`+`functions/lib/public/`+firebase.json hosting rewrites+functions/package.json express deps+functions/src/index.ts homepageGate, secrets HOMEPAGE_USER/PASS는 이미 등록됨 추정 deploy 통과) → 별도 commit 또는 격리 결정. 미커밋 누적 83개 중 P0 무관 다수(homepage 트랙/H2 후속/customer_app profile·auth/agent-teams 신규자료/디바이스 캡처/ios 신규 폴더/heart 등). **5/7 incident 진단 (코드 변경 0, fact 기반 결론)**: 사용자 보고 "기사앱 정산 화면에 사용한 모든 건수가 어제부터 한꺼번에 보임 + 매니저 업무마감 후에도 기사는 마감대기중 잔존". (1) **5/5 deploy 영향 = 0** — 검증 fact: ① `archiveOldCalls` 인덱스 부족 에러는 4/29~5/6 매일 동일 (5/5 deploy 이전부터 8일째 깨짐) ② PR 1 firestore.rules `calls` 권한 변경 0건 (system_config만 추가) ③ driver_app 코드 변경 0 ④ settlement.ts 변경 0 ⑤ `onCallCompletedUpdateSettlement` 5/6 정상 (8건 시간순 처리, 한꺼번에 X) ⑥ `autoFinalizeSettlements` 5/5 21:10 Processed:0 정상. (2) **"마감대기중" 진짜 원인 = 시스템 설계 함정** — 매니저 측 마감 액션 2단계 분리: ① `finalizeSettlementSession`(SettlementViewModel.kt:1202) → CF `finalizeSettlementAndNotifyDrivers`/`notifyDriversSettlementFinalized`(settlement.ts:394-464) = **FCM 알림만 전송, driver 문서 update 0**. ② `confirmDailySettlement(driverId)`(SettlementViewModel.kt:1860) = `dailySettlement.status` → CONFIRMED 로 transaction.update. driver_app `carryOverListener`(DriverViewModel.kt:1332)는 `designated_drivers/{driverId}` 실시간 listener로 `dailySettlement.status` 만 본다 → 매니저가 ① 만 누르고 ②를 안 하면 driver 문서 PENDING_CONFIRM 잔존 → driver_app "마감대기중" 표시 그대로. (3) **즉시 조치**: call_manager 정산 화면 → 각 기사별 "정산 확인" 버튼 1회씩 → driver_app 즉시 반영. (4) **중장기 PR 후보** (별도 세션, 미진입): `finalizeSettlementSession` 안에 PENDING_CONFIRM 모든 기사 일괄 CONFIRMED update — 단 개별 검증 건너뛰기 위험으로 결정 필요. 또는 UI 흐름 개선("미확인 기사 N명 있음" 경고). (5) **부수 정리 사안** (별도, deploy 무관): `archiveOldCalls` 인덱스 부족 4/29~5/6 매일 실패 — Firebase Console 클릭 1회로 `calls collectionGroup status ASC + completedAt ASC` composite 인덱스 생성 시 다음 KST 11:00 또는 `gcloud scheduler jobs run firebase-schedule-archiveOldCalls-asia-northeast3 --location=asia-northeast3 --project=calldetector-5d61e` 즉시 트리거. (6) driver_app `loadTodaySettlement`(DriverViewModel.kt:1592) 동작 사실: `calls/` 컬렉션에서 `assignedDriverId == 본인 + status == COMPLETED + completedAt > settlementLastCleared` 1회 fetch → settlementLastCleared 미갱신/매니저가 마감 한두번만 했으면 그 이후 모든 콜이 정상적으로 누적 표시되는 정상 동작.
- ✅ 2026-04-29 **chat V1 이미지 업로드 (V1.1) 운영 가능 + 총알대리 사무실 chat 인프라 적용** (6 commit `036cb234` functions + `33b7b19b` rules + `1b5e1c3b` storage fix + `6ccab90c` call_manager + `97ddb27c` driver_app + `b48268be` pickup) — 카톡 통합 검토→거부 후 chat V1 강화 노선 첫 단계. 디자인: 갤러리만(카메라 X), 단일 이미지, **긴 변 max 640px JPEG 60% 비율 유지**(crop X), **EXIF orientation 처리**(ExifInterface + Matrix), **7일 cleanup**(Local-first), **PickVisualMedia 권한 불필요**. 구조: ChatRepository.uploadChatImage(@ApplicationContext + 30초 타임아웃) + sendImageMessage(uploading sentinel) + isEmptyInOffice 가드 / Firestore message에 imageUrl/imagePath/imageWidth/imageHeight 4 필드 / Storage path `provinces/{p}/cities/{c}/offices/{o}/chat_images/{messageId}.jpg` / FCM body "[사진]" 분기 + payload 4필드 추가(number→string) / cleanup CF Firestore delete 먼저 → Storage delete (Promise.allSettled) / ChatScreen ImageBubble(uploading SENDING/FAILED/AsyncImage 분기 + height=0 가드 + aspectRatio) + FullScreenImageViewer(Dialog) + ChatPeekPreviewBar [사진] 분기. **storage.rules cross-service `firestore.exists()` wildcard substitution 한계 발견** → chat_images에서 cross-service 제거 + 인증만(보안: firestore.rules가 chat 메시지 create 가드). **Local-first 강화**: call_manager loadInitialMessages 매 시작 → Room empty 시만 (사무실당 월 1500 read 제거). driver/pickup ChatViewModel init에 신규 `if (isEmptyInOffice) loadInitialMessages` 추가(destructive migration 후 자동 복구). **운영작업**: backfill `OyLNNY8GbFExPHHLkuMK`(총알대리 — 매니저 1+기사 2+픽업 1 = 4명 등록) + APK 3개 hosting deploy(public/apk_downloads/). 검증: 3앱×3앱 송수신 매트릭스 + EXIF 회전 + 풀스크린 OK. **driver_app 빌드 hidden bug 발견**: build.gradle.kts(보조)와 build.gradle(활성) 둘 다 존재 → 의존성은 build.gradle에 추가해야 컴파일됨. plan: `C:\Users\kala1\.claude\plans\sequential-roaming-raccoon.md`
- ✅ 2026-04-29 **픽업앱 Dashboard FCM + Room 전환 완료** (commits `ad94b9e9` functions + `db4fb0c8` 픽업앱, push 완료) — Phase 2 §"알림기반 로컬 트리거" 구조로 재편. **스코프 제약 해제** (기존 플랜 §"기존 MVP 대시보드 유지"를 사용자 결정으로 깸). 구조: Room calls 테이블 (AppDatabase v2) = 단일 진실 / FCM NEW_CALL·CALL_STATUS_UPDATE = 트리거 / 첫 진입 1회 refreshData / UI = Room Flow 단일 구독. **백그라운드/종료 상태에서도 시스템 트레이 알림 도달**. 신규 3 (LocalCall/CallDao/CallRepository) + 수정 4 (Constants/AppDatabase/MyFirebaseMessagingService/DashboardViewModel). 서버측: sendNewCallNotification + onCallStatusChanged 에 pickup_drivers 토큰 합치기 + payload 필드 확장(status/customerAddress/timestamp 등) + 무효 토큰 정리. WAITING 30분 컷 일관성: FCM 측(stuck 콜 INSERT/알림 둘 다 skip) + Dao 측(SQL 컷오프). logout() 에 clearAllCallsInOffice 추가. 결정: shared_calls 스코프 밖 / commit 분리(functions 먼저) / driver_app `call_assigned` 충돌 0(else 분기). plan: `C:\Users\kala1\.claude\plans\functional-stargazing-kitten.md`. **다음 단계 후보**: C1+A4(콜매니저 [픽업 배정] UI + onPickupAssignmentCreated CF) → B3("내 담당" 탭) → B6(자동로그인). 사용자 "필요할 때" 진입.
- ✅ 2026-04-28 후반-2 **driver_app + call_manager chat 운영 가능화** (11 commit push 완료, `bda7f568`~`42552143`+ `a705863d`) — driver_app BOM 업그레이드(Compose 2024.04.01/Kotlin 1.9.22/Firebase BOM 33) + Room ChatAppDatabase v1 + ChatRepository(Hilt) + ChatViewModel + ChatScreen + HomeScreenWithChatSheet wrapper + FCM NEW_CHAT_MESSAGE 분기 + chat_messages_ptt 채널 + logout fcmToken 삭제 + HomeScreen Scaffold `contentWindowInsets=WindowInsets(0)` (BottomSheet peek 영역 가림 해결). 추가: call_manager + driver_app `handleChatMessage` 포그라운드 시 `playChatSound()` 호출(시스템 알림 skip 미터치, sound만 직접 재생 — 다른 알림 영향 0). pickup_driver_app은 사용자 별도 commit `a705863d`로 ChatCard 50:50 통합 + IME padding + FCM 토큰. **3앱 동시 운영 가능**. 검증: S22 driver chat sheet 표시 + S21+/S22 포그라운드 chat sound 정상.
- ✅ 2026-04-28 후반 픽업앱 사무실 단톡방 V1 통합 (commit `a705863d`) — Dashboard 50:50 분할(콜 위 / ChatCard 아래) + IME padding + FCM 토큰 등록(LoginViewModel + onNewToken). **BottomSheet 패턴 거부 → 카드 패턴 채택**으로 call_manager 미해결 4종 회피. 동반 운영작업: (a) `backfill-chat-members.js gyeonggi yangpyeong RUbeBEvGGYP5wMhJHhMF --execute` 1회 실행 (PICKUP_DRIVER 1명 등록), (b) chat 관련 6개 CF 배포 (`onChatMessageCreated` + `scheduledChatMessageCleanup` + `backfillChatMembers` + `onChatSync*` 3종) — 모두 첫 배포. **검증 결과**: 픽업→콜매니저 송수신 ✓, 콜매니저→픽업 송수신은 픽업 로그아웃→재로그인 후 fcmToken 등록되어야 작동 (사용자가 재로그인해야 함). plan: `C:\Users\kala1\.claude\plans\harmonic-percolating-lamport.md`
- ✅ 2026-04-28 후반 사무실 단톡방 V1 — chat 알림음 ptt 효과음 적용 (`CHAT_MESSAGE_CHANNEL_ID = "chat_messages_ptt"` 신규 채널 + `R.raw.ptt_start` setSound). call_manager 단독. **다음 세션 Step 6 = driver_app + pickup_driver_app 이식** (handoff 문서 §"Step 6 이식 가이드" 정독 필수)
- ✅ 2026-04-28 후반 사무실 단톡방 V1 Step 5 — `b190a2bc` commit 후 4가지 추가 증상 (peek 영역 침범 / multi-line auto-grow / 전송 시 새 메시지 안 보임 / swipe down sheet 안 닫힘) → 7번 fix 시도 모두 실패 → **이전 commit `b190a2bc` 상태로 복원** (사용자 명시: "이전 커밋으로 되돌리는 게 불필요한 수정 제거 가능"). 측정 확인: layout 정상 stack(LazyColumn 끝<ChatInputBar 시작), sheet drag 0회 위임(NestedScrollConnection 잘못 사용 의심). 미해결 4종 + 다음 세션 정밀 진단 권장: M3 BottomSheetScaffold nested scroll 정합 패턴 reference 더 fetch + 측정 강화 후 단일 fix 검증 사이클. 상세 인계: `memory/designated_drive/chat_v1_handoff_2026-04-28.md` "2026-04-28 후반 세션 결과" 섹션.
- ✅ 2026-04-28 commit `b190a2bc` (manager-direct-drive) — 사무실 단톡방 V1 Step 5 chat sheet 재작성 + 8 fix (Room 스키마 / adjustResize / Dao DESC / backfill 스크립트 / ChatScreen Jetchat 패턴 / MainActivity DashboardWithChatSheet / DriverStatusCard alignment Bottom / DashboardScreen Scaffold contentWindowInsets=WindowInsets(0))
- ✅ 2026-04-28 픽업앱 대시보드 fix — WAITING 30분 클라이언트 컷 + ADDED/MODIFIED status 변경 시 시스템 알림 (앱 켜진 상태 한정). 3파일 변경 (DashboardViewModel/MainActivity/PickupDriverApplication). 인프라 이미 존재 (POST_NOTIFICATIONS 매니페스트, HiltAndroidApp). 빌드 성공 + Z Flip4(R3CT80K78NP) + S21+(R3CR312MB1L) 설치 완료. **미커밋, 검증 대기**. 활성 4종(ASSIGNED/ACCEPTED/IN_PROGRESS/AWAITING_SETTLEMENT) 시간 제약 X = 사용자 명시 의도. 사무실 전체 콜 표시 = 의도된 설계 (Phase 2 §B3 "내 담당" 별도). 한계: listener 구독 중 timestamp 고정 → 화면 켠 채 30분 자동 제거 X. 앱 백그라운드 알림 X (Phase 2 FCM 영역). 상세: `memory/designated_drive/pickup_driver_app/dashboard_waiting_cutoff_2026-04-28.md`. Plan: `C:\Users\kala1\.claude\plans\velvety-kindling-bumblebee.md`. **검증 시나리오 5종(A~E) 통과 시 commit `feat(pickup): WAITING 30m cutoff + status change notifications`**
- ✅ 2026-04-27 식당업소앱 변환 PLAN 1쪽 + R1~R5 결정 확정 (`memory/restaurant/owner_app_pivot_plan_2026-04-27.md`). **다음 트리거: 첫 식당 미팅 약속 → 백업 + Flutter MVP 진입**
- ✅ 2026-04-27 클코 에이전시화 결정 + 본 에이전시 헌장 v1.0 작성. Cowork→보조 도구 재정의 (`memory/operating_model/clcode_agency_charter.md`)
- ✅ 2026-04-27 손님앱(Kotlin+Flutter) → 식당업소앱 변환 결정 (`memory/restaurant/owner_app_pivot_2026-04-27.md`)
- ✅ 2026-04-27 마스터 정리(v1.1 R1~R8) + 메모리 OS 레이어 재구조화
- ✅ 2026-04-26 픽업앱 MVP + 배차 STT 메모 (commits `f9d0f0d0`/`58428830`)

## 보류 트랙 (대리 도메인 안, 양평 자기추진 입증 후 복귀)
- 손님앱 코드(Kotlin+Flutter) → 식당업소앱 변환됨 (2026-04-27): 백업본 기점으로 부활. `memory/designated_drive/customer_app/customer_app_to_owner_app_2026-04-27.md`
- Phase 6 iOS 출시 (App Clip 전략 임시 SUPERSEDED): `memory/designated_drive/ios_phase6_paused/`
- Flutter 전환: `memory/designated_drive/flutter_paused/`
- 대리 손님앱 MVP 재작성: `memory/designated_drive/customer_app/`

## 사용자 프로필 한 줄
- iOS App Store 무경험, Android 숙련 (`memory/user_profile/user_ios_experience.md`)
- Apple Team ID `VCJD377MAU` / Bundle `com.designated.driverapp.app` (`memory/user_profile/apple_ios_ids.md`)

## 주요 피드백 (사고 모드, 13개)
| 피드백 | 핵심 |
|--------|------|
| user_intent_first | 사용자 명시 의도를 추측 우회로 대체 금지 |
| scope_exact | "딱 거기까지만" 스코프 디시플린 |
| grep_all_paths | 수정 전 모든 진입 경로 Grep 필수 |
| save_plans | 플랜모드 결과 반드시 메모리 저장 |
| keep_existing_conventions | 기존 관례 변경은 실질 근거 필수 |
| purpose_based_adaptation | 다른 앱 패턴은 목적 기반 취사선택 |
| simplest_fix | 최소 수정 우선 |
| listener_vs_fcm | 신규 기능 설계 시 리스너 vs FCM+로컬 트리거 자동 비교, 기본값 FCM |
| no_guess_gui_fix | GUI/레이아웃 버그 fix 시 추측 반복 금지 — 측정+공식 reference 후 진행 |
| user_command_overrides_system_rule | 사용자 stop/멈춤 신호는 system rule(plan mode 도구 강제 등)보다 절대 우선 |
| app_naming | "기사앱"=driver_app(대리), "픽업앱"=pickup_driver_app. 발화 모호 시 AskUserQuestion (`memory/feedback/feedback_app_naming.md`) |
| simplify_first_cleanup_later | 큰 간소화 PR 대기 중일 때 작은 정리 PR을 끼워 넣지 말 것. Audit은 스냅샷 보고서로 보존 (`memory/feedback/feedback_simplify_first_cleanup_later.md`) |
| no_revelation_projection | 사용자가 이미 알고 있던 것을 클코의 첫 인지로 투영해 "드물게 도달한 자리" 식 신비화 금지. 사용자 톤(웃긴건/그냥/재미있게)에 답변 톤 정합. 별 5개 자제 (`memory/feedback/feedback_no_revelation_projection.md`) |
| paradigm_over_analogy | 신규 기능 설계 시 프로젝트 패러다임(listener_vs_fcm·Data Push·listener 최소화) 먼저 적용. 도메인 비유의 UX 목표 ≠ 구현 아키텍처. 패러다임 위반 옵션을 "본인 결정 자리"로 올리지 말 것 (`memory/feedback/feedback_paradigm_over_analogy_2026-06-01.md`) |

## 자주 쓰는 단축 정보

### 연결된 기기
| 기기 | 시리얼 | 설치 앱 |
|------|--------|---------|
| SM-G996N S21+ | R3CR312MB1L | call_manager, driver_app |
| SM-S901N S22 | R5CT41TJZFP | call_detector, driver_app |
| SM-F721N Z Flip4 | R3CT80K78NP | driver_app, customer_app, pickup_driver_app |

### 세션 명령어
| 말하면 | 실행 |
|--------|------|
| "시작해줘" / "깃풀해줘" | `git pull origin <현재브랜치>` |
| "종료해줘" / "깃체크해줘" | `bash git-check.sh` |

### 취소 상태 규칙
- `CANCELED` = 관리자 취소
- `CANCELLED_BY_DRIVER` = 기사 취소
- `CANCELLED_BY_CUSTOMER` = 고객 취소
- `HOLD` = 재배차 대기 (CF 코드 잔류, 미생성)

### Chat member 등록 (2026-04-29 확인)
- **자동 등록 완비**: `registerOwner` CF (매니저) + `onChatSyncDesignatedDriver` / `onChatSyncPickupDriver` 트리거 (기사 승인) — chat CF 6개 배포(2026-04-28 후반) 이후 신규 가입자는 100% 자동
- **수동 backfill 1회 필요한 케이스**: 트리거 deploy(2026-04-28) 이전부터 운영 중이던 기존 사무실 (마이그레이션). 명령: `node functions/scripts/backfill-chat-members.js <provinceId> <cityId> <officeId> --execute` (PATCH set-merge, 멱등)
- 헬퍼 단일 진입점: `functions/src/handlers/chat.ts:34` `addChatMember()`

### 팀 운영
- "팀 해체해" 명시까지 유지
- "준비해줘" = 구성/정리까지만, 실행은 별도 명령
- PreToolUse hook: SendMessage shutdown_request 차단

### 사용자 규칙
- 모든 대답 두번 이상 검토
- 하드코딩 금지
- 수정 전 항상 허락
- 요구한것 이상 수정 금지
- 불확실하면 외부검색 후 사실대로 보고

## 5/31 ★ 클코 거짓 귀속 사건 (feedback — 무조건 보존)
- **상세**: `memory/feedback/feedback_no_false_attribution_2026-05-31.md`
- 사용자는 "어디까지했지/다음할것"만 물었고 원래 계획은 정산 검증. 클코가 AskUserQuestion으로 "보안 조사" 선택지를 *스스로 만들어* 던진 뒤, 사용자의 선택을 "사용자가 보안을 요청했다"로 둔갑 + 하지 않은 발화를 따옴표 인용 → 보안 하네스 폭주. 사용자 적발
- 적용: ① AskUserQuestion 선택 ≠ 사용자 발화 ② 트랙 전환은 사용자 명시 자유 발화에서만 ③ 인용 전 user 메시지 출처 확인 ④ "솔직하게"는 사실 재검증이지 메타 선언 아님

## 5/31 정산 deploy — stash 의존성 분리로 부팅 크래시 (해결됨) + feedback
- **install ✅**: call_manager→S21+(R3CR312MB1L) / driver_app→Z Flip4(R3CT80K78NP) Success. Room v7→v8 자동 마이그레이션(logcat 미확인, 다음 세션)
- **deploy ✅**: firestore.rules + functions(정산 재설계) production 반영 완료. 폐기 함수 `notifyDriverSettlementResult` 삭제 확인 + autoFinalize/onCallCompletedUpdateSettlement/finalizeSettlementAndNotifyDrivers/homepageGate 정상 등록
- **사고**: 클코가 `stash push -- functions`로 정산-무관 변경 분리 시 `functions/package.json`의 express/express-basic-auth 의존성을 함께 빼버림 → 커밋된 src/index.ts(homepageGate)가 require하는 모듈 누락 → GCP 8080 listen 실패(41함수 전부). 클코 오진단("commit 2 손상") 후 로컬 tsc 에러 원문(TS2307 express-basic-auth)으로 진짜 원인 확정 → stash에서 package.json/lock만 복원 → 로컬 부팅 검증 통과 → deploy 성공. 상세 [[feedback-stash-dependency-split]]
- ⚠️ **미커밋 잔여**: functions/package.json + package-lock(stash 복원분, src와 정합) + functions/lib/*(build 산출물) + memory/MEMORY.md(feedback) — 커밋 권장. .claude/settings.local.json은 제외. stash@{0}은 나머지(손님앱인증/headweb/포인트지갑식당) 보유한 채 유지
- **다음 세션**: ① 정산 E2E (콜 생성→배차→운행→정산→매니저확인, 양평 한가한 시간) ② Room v7→v8 logcat 확인 ③ 미커밋 커밋 정리
- 부산물(보안 감사)은 사용자 결정으로 전부 정리됨 (`security_audit/` 폴더 + 토픽파일 + plan 삭제). firestore.rules 미변경이라 코드 흔적 0

## 6/3 ★ PTT 음성 버퍼링 PoC — 게이트 실패·폐기 (워밍창이 진짜 레버)
- **상세**: `memory/coupon/ptt_buffering_failure_2026-06-03.md`
- 본인 결정(6/2) 버퍼링 우선 → Step1 차소음/실시간 게이트에서 **구조적 막다른 길** 판명·폐기. 코드는 `ptt-buffering` 브랜치 commit `8c3b4310`(로컬·미푸시) 보존. 메인=`ptt-coldstart`(마지막 푸시) 복귀, S21+ 정상빌드 재설치 완료
- **실패 본질**: 버퍼 flush = 콜드 연결시간(1.3~4s)을 *영구 재생지연*으로 전환 → 음성녹음급 지연, 실시간 X. + 외부소스라 Agora APM(ANS/AGC) 상실(음질↓) + 마이크 충돌(enableLocalAudio false 필요). 웜은 내장마이크로 이미 실시간이라 개선 0
- **진짜 레버 = 워밍창 연장**(미시도): 대화 중 채널 살려두면(leave 타이머 리셋, 유휴 30~60s) 대화 턴 웜→교차 실시간, 내장마이크 음질 유지. 단 첫 콜드 1발 지연은 아키텍처(FCM wake) 내재 한계(상시접속만 제거 가능, 본인 거부)
- **현재 최선 = `ptt-coldstart` 빌드**(콜드=음성메모 hold0 / 웜=라이브 실시간). 양평 실사용 데이터로 판단
- 기술자산: voice-sdk 4.5.2 커스텀오디오 API 확정 — 버퍼링 아니어도 재사용 가치

## 6/3 ★ 본인 결정 — 실사용 데이터 트랙 *보류* + 양방향 송수신 *우선 진입*
- **실사용(양평 데이터 수집) 트랙 전체 보류**: 6/2~6/3 "코드 트랙 종료→데이터로 판단"(트리거·비프모델·버퍼링·screen-off) 일괄 보류. 양방향 완성 후 재개. 메모리 반영: `ptt_implementation_2026-06-02.md` §"2026-06-03 본인 결정" + `ptt_screenoff_send_2026-06-02.md` 상태줄
- **양방향 송수신 우선 진입**: 일방 브로드캐스트 → 양방향+역할분리. 범위 = call_manager **수신** 추가 + pickup_app **송수신** + sendPttWake **매니저·픽업 토큰 팬아웃**. 기점 `ptt-coldstart`.
- **1단계 완료 (6/3, push+배포+S21 install)**: commit 3개(`96142111` functions 팬아웃 / `d5c889d0` call_manager 단일엔진 송수신 통합 / `4a1a32ad` by lazy 크래시 fix). functions 배포(sendPttWake/PreWake). Agora 싱글톤 제약으로 PTTManager에 수신 흡수(EngineMode TX/RX) + 신규 PttReceiverService(라이브+콜드). 검토로 누락2(콜드 음성메모 수신 재생/서비스 라이브+콜드)·오염1(싱글톤 release) 보강. **E2E 물리검증 대기**(A무결성/B회귀=S21→ZFlip4 / C매니저수신=2단계 pickup과 합쳐 검증). plan `majestic-swinging-gosling.md`. 상세 [[ptt_implementation_2026-06-02]] §"양방향 1단계 구현 완료". **2단계=pickup_app 송수신 fork**(plan 동일 파일에 2단계로 갱신).
- **2단계 완료 (6/3, push+빌드+install)**: pickup_app PTT 송수신 fork 7커밋(`bfeb6a57`~`35d12e33`). commit 0=call_manager PttReceiverService 권한가드(검토 보강). pickup=Agora/functions 의존성+권한 / service fork(senderName "픽업기사"·prefs키 픽업·by lazy) / Room v3→v4 무손실 migration / audio 송수신 / FCM ptt 분기 / MainActivity 송신배선(콜드=ChatRepository 직접·Hilt 우회). 검토 보강 2: PttReceiverService 권한가드·ChatViewModel dead code 제외. pickup BUILD OK + Z Flip4 install 정상(크래시 0). **E2E 물리검증 대기**(픽업↔매니저 양방향). 상세 [[ptt_implementation_2026-06-02]] §"양방향 2단계 구현 완료".
- **클코 학습 (6/3)**: 원규씨는 plan 승인 전 항상 "누락 오염 검토" 요구(1·2단계 연속) → ExitPlanMode 전 자체 코드 검증 게이트 의무화. `memory/feedback/feedback_plan_review_gate.md` ([[feedback-plan-review-gate]]).

## 6/3 (저녁) ★ 콜드스타트 "원활통신" 재설계 — 수신 무음 회귀, 롤백 대기 (종료) — ⚠️ SUPERSEDED (6/6 정정)
- **2단계 E2E 양방향 1차 통과**(픽업↔매니저 라이브+콜드음성메모, 15:25~26). 이후 콜드스타트 재설계 진입.
- **재설계**(commit `f0a8d60b`/`b1a1cc2e`/`24504d33`, branch ptt-coldstart): WARM 워밍창 + RX→TX updateChannelMediaOptions 즉시전환 + 콜드녹음 제거 + 워밍창 45s. 본인 지침 "최대한 원활한 통신 우선".
- 🔴 **회귀(미해결)**: WARM 채널 재사용 시 매니저/픽업 **상호 수신 무음**(콜백은 옴, 오디오 X). driver_app(신규 join)만 정상. → WARM 재사용이 Agora remote 수신을 깸. enableLocalAudio 아님.
- ★ **다음 세션(롤백)**: WARM/워밍창45s/updateChannelMediaOptions 롤백 → 2단계 검증동작(매 발화 신규 join, 수신 정상) 복원 + **콜드녹음 폐기**. 목표=첫 E2E 양방향 + 콜드녹음 4~6s 제거(cold-live ~1.4s). 상세 [[ptt_implementation_2026-06-02]] §"콜드스타트 재설계".
- ★★ **콜드 음성녹음은 클코 임의 도입(본인 의도 아님)** — 본인 "최초 콜드스타트 음성녹음은 내가 의도한 게 아닌데 너가 임의로". 무전 본질=실시간, 녹음전달 아님. 음성녹음 트랙 폐기. `memory/feedback/feedback_cold_voicememo_unintended.md` ([[feedback-cold-voicememo-unintended]]).
- **단말**: S21+/S22/Z Flip4에 수신무음 버그 버전 설치됨 — 롤백 빌드 재설치 필요.

## 6/6 ★★★ "WARM 수신 무음 회귀"는 오진 — 실제 원인 3건 규명·수정·머지 완료 (롤백 안 함)
- 6/3 "WARM 재사용이 Agora 수신을 깬다" 진단 **틀림**. 3단말 실측+로그 교차로 진짜 원인 3건 분리. **WARM/45s 워밍창 롤백 불필요**. 단말 3대 검증 통과. 상세: [[ptt_implementation_2026-06-02]] §"2026-06-06 정정".
- **①음량 들쭉(들리다 말다)** = 라우트 미고정(이어피스 플립). `onJoinChannelSuccess`에서 `setEnableSpeakerphone(true)` 1회 강제(driver는 `setDefaultAudioRoutetoSpeakerphone`도 누락). STARTING 분기 재설정은 *재생 중 라우트 리셋=끊김* 유발하므로 금지.
- **②중간 끊김(진범)** = 3자+ 메시 버그. `onRemoteAudioStateChanged`가 uid 미구분 → 발화 안 하는 다른 broadcaster의 `STOPPED reason=5(REMOTE_MUTED)`를 발화자 종료로 오인. 수정: STARTING에서 **발화자 uid 래칭(speakingUid)**, STOPPED/onUserOffline은 그 uid만 종료. 로그 확정.
- **③앱 계속 중단됨** = `PttReceiverService`가 microphone 타입 FGS를 백그라운드(FCM wake) 시작 → SecurityException(targetSDK 36, 화면off 수신). 수신은 마이크 미사용 → 타입 제거 + startForeground 가드.
- **커밋/머지**: `3afea559`(FGS)+`4c7da7aa`(라우트+uid 래칭) ptt-coldstart push → **머지 `e41486a0` ptt-coldstart→manager-direct-drive 단일화**(43파일 +2650, 충돌 0). plan `cozy-jingling-spindle.md`.
- **잔여(선택)**: keep.audiosessiontype 파라미터 보존(끊김 원인 아님, 효과 미측정) / 2단계 지연(토큰 선발급 캐시+wake fire-and-forget) 미진입 / master 9개월 분기 정리 별도.
- **클코 학습**: 6/3 "WARM이 수신을 깬다"를 *단정*하고 롤백 plan을 세웠으나, 6/6 게이트형 진단(원인 단정 금지·라우트 락 베이스라인 후 로그 귀속)으로 오진 판명. 2자→3자 환경 차이가 메시 버그를 가렸음. 단정 전 실측·로그 교차 필수.

## 6/16 ★ 검증 트랙 시작 + 콜드스타트/앱진입 배차 수락 버그 수정 (커밋 503cf4b1 push됨)
- **검증 트랙 발의(원규씨)**: 6월 신규기능 실동작 검증(원칙=최소개입·자유사용). 검증 3겹(①코드 정합감사 ②런타임 시뮬 테스트사무실1004 ③기기 핸즈온, PTT는 실폰검증 충분→생략). **시나리오 v2**(픽업 포함 콜생애 8단계: 출근→전화콜→배차+PTT→픽업이송→운행시작(PTT차단)→운행완료→마감→정산확인) = 다음 검증 기준선. 픽업앱=읽기전용 모니터(액션버튼0, 조율은 PTT, 픽업-콜 매칭 시스템에 없음).
- **버그 수정(driver_app 4파일, push)**: 화면오프 배차→알림클릭/앱진입→수락해도 운행준비 안가고 메인, 배차확인 여러번(오래된 묵은버그). **뒷단**=acceptCall이 인메모리 assignedCalls 의존→콜드스타트 못찾음(activeCall 미세팅)→2단계 트랜잭션 callSnapshot을 CallInfo로 파싱·반환해 보장세팅(`runTransaction<CallInfo?>`). **앞단**=앱 열면(onResume) 미수락배차 수락팝업 자동(`call_assigned` FCM→로컬 prefs `PREF_KEY_PENDING_DISPATCH`→수락/거절/무효시 `clearPendingDispatch`, **Firestore 읽기0**). 플립폰(ZFlip4) 검증통과·**S21+/S22 미설치(플립폰만 최신)**.
- **미결(다음세션)**: ①⚠️정산 `onDriverSettlementSubmitted` 트리거가 `dailySettlement.status`(없는 필드) 검사→불발의심(매니저 정산제출 FCM 안올수도, 미확정·런타임확인필요) ②운행중취소 요금0(정산미반영, 부분요금=정책판단 대기) ③기사상태 enum과다(ONLINE≈WAITING통합후보·ACCEPTED불일치 잠재버그·PENDING_CONFIRM 정산중복, 정산중심부라 보류) ④알림음 정산대기 정리보류.
- **교훈**: 정적분석↔실동작 갭 큼 — STATUS_CHANGE가 죽은코드로 보였으나 운행시작/완료 소리+팝업은 **in-app(DashboardViewModel Firestore 리스너)** 경로로 작동, "기사 운행취소"는 자동아니라 원규씨 수동취소. logcat 실측+실사용 확인으로만 확정. 원규씨 최초 "실 동작 검증 필요" 직관 정확. driver_app assignedCallsListener 등=죽은변수(미등록). 상세=`memory/designated_drive/session_2026-06-16_verification_dispatch_fix.md`.

## SUPERSEDED 자료 위치 (참고용)
- `memory/designated_drive/project_business_model.md` (마스터 §3.1, §3.4, §10.4 흡수)
- `memory/designated_drive/project_expansion_vision.md` (마스터 §1.3 환상 ③)
- `memory/designated_drive/project_scale_target.md` (마스터 §1.3 환상 ③)
- `memory/designated_drive/strategy_pivot_2026-04-21.md` (마스터로 대체, 첫 영업 학습 보존)

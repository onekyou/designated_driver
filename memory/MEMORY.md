# 콜마당 — 양평 동네 생활 OS

> ⚠️ **메모리 저장 정책**: 단일 원본 = `C:\Users\kala1\designated_driver\memory\`(git 추적). B 폴더(.claude/projects/.../memory/)에는 MEMORY.md만. 새 토픽 파일은 도메인 폴더 안에. 이 파일 편집 후 A로 복사: `cp B/MEMORY.md A(memory/)/MEMORY.md`. **인덱스는 한 줄/항목, 상세는 토픽 파일** — 2026-07-05 용량 초과로 압축, **전체 원문 = [[memory_index_archive_2026-07-05]]** (`memory/designated_drive/memory_index_archive_2026-07-05.md`).

> ★ **결정은 먼저 결정 대장**: `memory/_decisions.md`(전역) + `memory/designated_drive/_decisions.md`. [확정] 뒤집기 = 입증 먼저. 서사에 묻힌 결정 재해석 금지.

> 🟥 **폴더 규율(2026-06-10)**: 이 폴더 = **대리운전 전용**. 본류(통화예약·미용·식당)는 coupon_app에서. "오늘 할일" 후보 = 대리 항목만.

## 한 줄 요약
콜마당 = 양평 동네 생활 OS. 대리가 첫 케이스. 로컬마루(coupon_app)가 최상위, 이 폴더 = 검증장+부품창고.

## 진입점
- 마스터: `memory/callmadang_master_2026-04-27.md` / 체크리스트: `memory/callmadang_checklist_2026-04-27.md`
- 대리 도메인: `memory/designated_drive/README.md` + `_decisions.md` / 코드맵: `project-characteristics.md`

## ★★ 현재 방향 (2026-07-05) — 공유콜 지역 OS 전환
- **전환 확정(원규씨)**: 공유콜 위주 앱을 앞단으로(도입 동기=콜/이익, 편의 아님) → 기존 편의(PTT·정산)는 후속 정착층. 콜 올린 사무실이 수수료를 포인트 회수, 콜마당=전용계좌/환전 원장(구독료 원칙 유지).
- **5팀 전수 점검**: 엔진(게시→수임→배차→완료→A+10%/B−10% zero-sum 회수→환전신청 wallet.ts) **이미 end-to-end 구현·작동** — 전환은 보강. 남은 정책 4 = 잔액게이트·요율(10% 하드코딩)·공유범위(같은 시/도)·충전방식. 갭: detector 공유콜에 출발/도착/요금 필드 없음(빈 FCM)·완료/회수 통지 푸시 없음·실충전(PG) 없음.
- **★ rules 대봉인(`87b219af` push·배포·실측)**: `request.auth==null`="CF 전용" 아니라 **비로그인 공개**(Admin SDK는 rules 우회 — 공식문서+REST 실측). 22+곳 열려 있었음(포인트 자가충전·환전승인 조작·초대코드 공개rw 등) → allow 분기 25곳 `if false` 잠금(코드 삭제 0)+수임 claimedOfficeId=본인 사무실 검증+emergency_alerts 정상화. 비로그인 REST 6/6 차단 실측. **서버 전용 = `if false`가 정석, null 추가 회귀 금지.** ⛳잔여=실폰 회귀(수임·정산제출·채팅). 롤백=`firestore.rules.bak-20260705`. 상세=결정대장 "공유콜 지역 OS 전환/보안".
- **위생(같은 커밋)**: settings.local.json 시크릿 삭제+untrack(이력엔 없었음) / 명함주소.txt 호스팅 라이브 공개였음→OneDrive `콜마당_PII보관_20260705/` 이동+재배포 404 / recordingS·마늘밭·bak gitignore.
- **비효율 지도(점검 부산물, 이월)**: 정산 완료콜 전량 read·shared_calls 청소부재·매분 스캔·fork 복제(call_mgr↔pickup 코드 2벌)·데드코드·팀 컨텍스트(.agent-teams) 낡음·master 10개월 정체(587앞/16뒤).

## 파일럿/사무실 로스터 [확정 6/17]
- **실 파일럿 = `OyLNNY8GbFExPHHLkuMK` "총알대리"**(gyeonggi/yangpyeong). `nEkf0X9g3LZtRX94Mrzu`(양평)·`RUbeBEvGGYP5wMhJHhMF`(1004) = 원규씨 테스트용, 보급 대상 아님.

## 대리 도구 현황 (6월 보급 준비 완료분 — 상세 전부 [[memory_index_archive_2026-07-05]]+결정대장)
- 완료: 콜운영·PTT 양방향+단일누름(볼륨 hold)·콜드 2.85s·배차알림 Option A·블랙박스 9-A/9-B(무음 시스템메시지+필터토글)·PTT→텍스트(온폰 whisper small+VAD+가제티어+모델 자동다운로드=케이블 불요)·운행중 PTT수신차단·정산 단순화(2상태+P7 게이트 4종)·알림 정리 3앱·픽업 12h 롤링·system 메시지 푸시→풀(비용 불변).
- ⛳ 남은 보급 준비: 현장 클린 재설치(APK만, 폰 확보 시)·9-B Commit 2 실콜검증·공유콜 잔여 3트리거(=블랙박스 로깅 3종, 배차 갭 아님).
- STT [확정]: 엔진=faster-whisper(서버)/whisper.cpp small(온폰 PTT). Gemini는 짧은 무전서 환각 폭주(6/19 측정)라 PTT엔 whisper 유지. 통화예약(미용·식당)=coupon_app.

## 타임라인 인덱스 (상세 = 아카이브 + 각 토픽 파일)
- **6/19**: 온폰 모델 자동 다운로드(`ce661e3b`, Storage ptt_models/ 포그라운드 dataSync)·서버STT 기각(콜드 75s)·Gemini vs whisper 측정·PTT 단일누름 검증(`c44068b0`)·블랙박스 system 푸시→풀(`c29a1527`).
- **6/20**: 기사앱 stale 콜 수락팝업 무한루프 차단 하드닝(`78533cdf`)·secret 스크립트 gitignore(`156a4265`).
- **6/17**: 총알대리 확정+가제티어 시드 22지명·현장 클린 재설치 런북·PTT 단일누름 구현.
- **6/16**: 검증 트랙 시작+콜드스타트 배차수락 버그 수정(`503cf4b1`). 미결: onDriverSettlementSubmitted 트리거 불발 의심·운행중취소 요금0·기사상태 enum 과다.
- **6/15**: 알림 억제 런타임 검증(S21+ 6케이스)·가제티어 1차(GazetteerCorrector)·운행중 PTT 수신차단.
- **6/11~13**: P7 정산게이트(`54e163d0`)·9-B Commit 1~3·PTT→텍스트 온폰 스파이크→small 확정·알림 정리 3앱(`7f346af4` 등)·push 수동마감 봉인(`472e16fa`).
- **6/9~10 (밤 시리즈)**: CLAUDE.md 드리프트 교정(**대리·택시=PTT / 미용·식당=통화예약 STT** [확정])·발화↔콜 음성명령 [폐기·과설계]·배차알림 Option A·PTT 콜드단축(`b66d0fdf`)·비프 간격 0.5s·콜매니저 parity 점검(반복벨 없음=오인 정정)·정산 단순화 "미구현"은 오인(본체 적용됨).
- **6/9 (밤2)**: 통화예약 엔진 = coupon_app 미용실 모듈로 전환(콜매니저 파일럿 [폐기] — 대리 콜 자동생성과 충돌).
- **6/7~9**: W경로 측정 10/10·헛예약0(~1원/콜)·써머리 [확인] UX·콜매니저 파일럿 배포·폰 검증 통과·READ_CONTACTS 폐기·온폰 프로브(turbo 정확하나 RTF 4.1×, "온폰 속도≠보편답").
- **6/3~4**: 콜캐치 권한 우회=녹음 파일명에서 번호(READ_MEDIA_AUDIO만)·Play 정식배포 길·파싱=확장 레이어. / 대리=쿠폰앱 모듈 흡수 방향(손님 측; 운영도구와 별개 층).
- **6/1~3**: PTT 구현 완료(FCM wake+Agora, 3커밋)·3단말 E2E·양방향 1·2단계·버퍼링 PoC 폐기(워밍창이 레버)·6/6 "WARM 수신무음" 오진 정정(진범=3자 메시 uid 미래칭)+머지 `e41486a0`.
- **5/27~30**: 정산 재설계(영업일 10시·Zero-base·이체수령 폐기·2상태)+PTT §3 commit 1~5 전부 push(외상 폐기·기사 정정·실납입 폐기, 순감 ~5천줄).
- **5/25**: PTT 진입 결정(5/19 부분 해제)·운영 시나리오 8+1·무전기 16만원 영업 카드·구독료 3만원·2000사무실 Agora 유지·행동0 제1원칙(`principle_user_action_minimum`).
- **5/19**: 콜마당 휴면→쿠폰앱 우선(이후 PTT 트랙만 부분 해제). / **5/24** 지방정부 자료 v1~v6 동결.
- **5/18~19**: 공유 prefill+채팅 long-press 복사(4커밋)·driver Auth race 회피.
- **5/15**: 박완수 공동경영 [폐기]("쓰레기" — 5/11 평가 전체 무효, `feedback_park_wansoo_evaluation`).
- **5/10~11**: RESERVED 예약배차 4-PR+사후 fix 6·cleanup gate 09시·픽업 BottomSheet 통일 10커밋·콜디텍터 챗 plan(`floofy-waddling-marble`, 보류).
- **5/12**: 시나리오 작가성(분류) → `C:\Users\kala1\movie\`로 완전 분리(인생 트랙).
- **5/4~5/7**: 식당앱 P0 plan(`abundant-floating-island`)+wallet/포인트 백엔드+Cloud Tasks timeout(reads 99.9%↓)+5/7 incident 진단(마감 2단계 함정→cleanup banner self-heal).
- **4/27~29**: 마스터 v1.1·클코 에이전시 헌장·chat V1(이미지·3앱)·픽업 FCM+Room 전환.

## 자주 쓰는 것
- **기기**: S21+ R3CR312MB1L(call_manager·driver) / S22 R5CT41TJZFP(call_detector·driver) / Z Flip4 R3CT80K78NP(driver·customer·pickup).
- **세션 명령**: "시작해줘/깃풀해줘"=git pull / "종료해줘/깃체크해줘"=bash git-check.sh.
- **취소 상태**: CANCELED=관리자 / CANCELLED_BY_DRIVER=기사 / CANCELLED_BY_CUSTOMER=고객 / HOLD=재배차 대기(미생성).
- **PTT 테스트 함정**: PTT 앱은 폰당 1개·같은 office 채널 로그인 필수.
- **Chat member**: 자동 등록 완비(registerOwner+onChatSync*), 구사무실만 `backfill-chat-members.js` 1회.
- **Firestore 유틸**: `functions/scripts/firestore-util.js`(Admin SDK — rules 검증엔 부적합 주의).

## 사용자 규칙 (실행원칙 요약)
두번 검토·하드코딩 금지·수정 전 허락·요구 이상 수정 금지·불확실은 검색 후 사실 보고·코드 수정=플랜모드 먼저·플랜 승인 전 "누락 오염 검토" 요구가 관례.

## 피드백 (사고 모드 — 이름만, 본문 `memory/feedback/`)
user_intent_first / scope_exact / grep_all_paths / save_plans / keep_existing_conventions / purpose_based_adaptation / simplest_fix / listener_vs_fcm(기본값 FCM) / no_guess_gui_fix / user_command_overrides_system_rule / app_naming(기사앱=driver, 픽업앱=pickup) / simplify_first_cleanup_later / no_revelation_projection / paradigm_over_analogy / no_false_attribution(5/31) / plan_review_gate / cold_voicememo_unintended / rabbithole_scope / overask_confirm / convention_over_verified / verify_before_asserting / generalizability_before_optimizing / overengineering_before_simplify / folder_scope_before_proposing / self_monitoring_limit / verify_install_on_user_device / stash_dependency_split.

## 보류/SUPERSEDED (재진입은 본인 명시 결정 후만)
- 보류: iOS Phase 6 / Flutter 전환 / 대리 손님앱 MVP / 콜디텍터 챗 / 콜카드 예약취소 UI / 상태명명 재설계 / screen-off 송신(유일 본질 미결, 데이터로 판단) / APK hosting 정리 정책.
- SUPERSEDED: 5/10 우선순위(→5/19)·5/11 박완수(→5/15 폐기)·6/8 콜매니저 통화예약 파일럿(→6/9 미용 모듈)·project_business_model/expansion_vision(마스터 흡수).

## 사용자 프로필
- iOS 무경험·Android 숙련. Apple Team `VCJD377MAU` / Bundle `com.designated.driverapp.app`.

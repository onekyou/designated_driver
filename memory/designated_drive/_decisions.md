# designated_drive 결정 대장 (대리운전 도메인 + 통화예약 엔진)

> **서사 아님 — 결정만.** 지위 규칙은 전역 `memory/_decisions.md` 참조.
> 서사·경위는 토픽 파일([[...]])에. 여기는 "지금 무엇이 확정/잠정/열림인가"만.

## 통화예약 입력엔진

### [확정·변경시 입증] STT 엔진 = faster-whisper (large-v3-turbo)
- 근거: [[reservation_engine_measurement_2026-06-07]] — `.m4a` 직접 처리 + 한국어 정확(STT가 "옥천명"으로 틀린 걸 Gemini가 "옥천면"으로 복원) 검증 통과(10건, 6/7)
- 바꾸려면: **다른 엔진(구글 STT 등)으로 가려면 "faster-whisper를 못 쓸 이유"를 먼저 증명.** ※"구글이 표준/쉽다"는 통념은 입증이 아님 — 검증된 faster-whisper가 기본값.
- **`[잠정·보류]` 카카오 i Cloud STT(General/Custom, 파일업로드 지원) — 검토했으나 보류**(2026-06-09): 클라우드라 PII는 서버 faster-whisper와 *동일 범주*(온폰 대안 아님). 잠재 강점 = 관리형(콜드스타트 제거)·Custom STT 도메인 단어 학습("옥천면"). 단 **엔진 교체 = [확정] 위반이라 입증 필요** → "관리형이라 편함"만으론 부족, **같은 10샘플로 faster-whisper와 채점 비교 후** 판단. 미측정이므로 기본값은 faster-whisper 유지. (과거 구글STT 드리프트와 같은 함정 경계.)

### [열림] faster-whisper 실행 *위치* — 온폰 검증을 *지금* 측정으로 승격(2026-06-09)
- 온폰 vs 서버 컨테이너 vs 클라우드 GPU 중 어디서 돌릴지. **엔진은 확정, 위치만 열림.**
- ⚠️ 위치가 열렸다고 *엔진 교체*(구글 등)가 허용되는 것 아님 — 위 [확정] 위반.
- **`[잠정]` 온폰 검증 = 지금 활성 측정으로 승격 → ✅실행 완료(같은 날)·아래 "전략 재정리"로 정밀화**: 6/7~8 "온폰=나중/production최적화" 프레이밍 갱신. 근거 = ① 서버 천장 확인됨 ② 온폰만이 PII-안전 + 행동0 골격 ③ 핵심 베팅("STT 틀려도 Gemini 복원")은 *더 나쁜 온폰STT*로 돌려야 진짜 입증. → 측정 프로브 [[reservation_engine_onphone_probe_2026-06-09]]. ⚠️이 줄의 "실제 골격/종착" 프레이밍은 아래 전략 재정리("온폰≠보편")로 *정밀화됨* — 단독 재해석 금지.
- **온폰 엔진 = whisper.cpp(ggml), faster-whisper(CTranslate2)와 *같은 Whisper 모델 weights·다른 런타임***. 온폰에서 CTranslate2 실행 사실상 불가라 whisper.cpp가 온폰 Whisper 엔진. **구글류 *모델* 교체 아님 = [확정] 위반 아님.** 프로브 1차 임무 = 런타임 스왑이 정확도 안 깎나(온폰 whisper.cpp turbo ≈ 서버 faster-whisper turbo) 측정.
- ⚠️ **우선순위 가드**: 온폰 프로브 = *병렬 측정*. **최우선은 거친 통화 GO바**(field) — 단 대상이 양평 실손님 → **미용실 현장**으로 변경됨(2026-06-09 밤2 전환, 아래 "통화예약 = coupon_app 미용실 모듈" 블록). 온폰이 GO바를 대체·지연시키지 않게(`feedback_rabbithole_scope_2026-06-04`).
- **프로브 결과(2026-06-09, S21+/Exynos2100, [[reservation_engine_onphone_probe_2026-06-09]])**: ✅실현성 OK / **온폰 turbo = 서버 정확도(런타임 등가 성립)지만 RTF 4.1×**(느림) / **small RTF 1.24×(빠름)이나 대리목적지·시각·번호 파괴**(헛예약0·intent 안전속성은 유지).
- **★ 전략 재정리(2026-06-09, 원규씨 지적) — 온폰 속도는 보편 답이 못 됨**: 온폰 속도는 *기기별*(칩·코어·GPU). **속도 레버일수록 덜 이식됨**(GPU/NPU = Mali/Adreno/Exynos/Snapdragon 별개 백엔드 — 한 폰 튜닝이 옆 폰엔 무용). 사장폰 = 제각각+흔히 저사양 → 핵심 플로우가 사람마다 다르게 작동=치명적 불일치. ∴ "내 폰 가속 측정"은 그 폰에서만 참, 보편 답 아님.
  - **`[잠정→방향]` 보편 STT 바닥 = 클라우드**(기기 무관·모두 동일): 지금 faster-whisper(검증), 카카오는 보류(나중 측정). **온폰 = 미래 베팅(NPU 폰 전반 표준화 2~3년) / 하이브리드(능력폰=온폰 PII안전, 약한폰=클라우드 폴백) — 보편 베이스라인 아님.** "정확도는 폰에서도 됨"은 은행에 보관(입증됨).
  - ⏸ **기기별 속도 튜닝 중단**(이식 안 됨). production NDK통합·가속 측정 = 미래/하이브리드 정해진 뒤.

### [확정] W경로(STT 텍스트 → Gemini)가 통한다
- 근거: [[reservation_engine_measurement_2026-06-07]] — 10/10 정확, 거짓양성(헛예약) 0, 콜당 ~1원
- C경로(오디오 직접 → Gemini)는 천장 비교용·후순위(W가 더 쌈, 로컬 네트워크 이슈). C로 후퇴 금지.
- 바꾸려면: W가 현장 거친 통화에서 못 미친다는 데이터

### [잠정] 써머리 확인 UX = 통화끝 → 자동 배너 → [확인] 한 탭 → 입력
- 설계는 합의됐으나 *아직 구현 전*이라 [잠정](실제 동작으로 검증되면 [확정] 승격).
- 파싱 → 바로 스케줄 직행 ❌ (헛예약 안전장치 + 신뢰 부트스트랩). "확인"은 입력노동 아닌 검토라 행동0에 수렴.
- 근거: [[reservation_engine_measurement_2026-06-07]]
- 바꾸려면: 확인 단계가 행동0을 더 해친다는 근거

## 파일럿 / 진행

### [폐기·2026-06-09 밤2·대리 콜 자동생성 충돌 + 미용실 현장 확보 + 종착지 직접 → 미용 모듈 직행] 콜매니저(대리)에 통화예약 엔진 붙이기 = 첫 실전 파일럿
- 폐기 사유: 콜매니저는 통화종료(IDLE)에서 이미 대리 콜을 자동생성(CallReceiver→CallDetectorService) → 예약엔진 콜 생성과 **충돌·중복** + 양평 운영(동결 보존) 깰 위험. + 수동 트리거(매통화 골라 분석)는 행동0 위배·매니저가 안 씀.
- 대체 → 아래 미용 모듈 블록들.

### [잠정] 통화예약 엔진 = coupon_app(로컬마루) 미용실 모듈
- 근거 3: ① 대리 콜 충돌 회피(미용=백지) ② **미용실 현장 확보**(사장 선물 약속 = 거친 통화 GO바 해결) ③ 종착지(coupon_app) 직접 = 이식 단계 생략.
- 위계 정합: [[reservation_engine_measurement_2026-06-07]] L77 "엔진은 로컬마루 앱 모듈로 이식(본진=coupon_app)" 이미 박힘 → [확정] 위계 위반 아님(이식 앞당김).
- 바꾸려면: 미용실 현장 무산 or 대리 충돌이 다른 방식으로 풀려 대리가 더 빠른 검증지가 되면.

### [잠정] 엔진 / 표면 분해 + 통화 자동입력까지 coupon_app 안에
- **엔진**(통화캡처+STT+파싱) = designated_driver 검증 부품(여기). **표면**(예약 스케줄러·손님 카드) = coupon_app 웹(BondCard 패턴, React). ※여기서 코틀린/Flutter로 표면 만들면 React 재작성 이식이라 낭비 → 표면은 종착지에 직접.
- **통화 자동입력까지 coupon_app 안에**(원규씨 결정) = **사장용 네이티브 안드로이드 앱(코틀린)**. 통화캡처·확인 팝업·예약 스케줄러·알림을 사장 앱 안에. `android_ref/` 코틀린(통화캡처 + ReservationInbox UI 이미 보유)을 **직접 완성** — Capacitor 등 웹 래핑 아님(통화캡처=안드로이드 네이티브 필수 + 코틀린 자산 직접 활용이 빠름). **손님 표면**(예약 카드)만 웹(`/me`, BondCard). STT는 W경로(Cloud Run+Gemini) local-maru 재배포.
- 검증 단계 = 엔진 부품을 coupon_app으로 복사해 통합, GO 후 사장 네이티브 앱.
- **(2026-06-09 정정)**: 기존 "Capacitor 하이브리드 단일앱"에서 → **사장=네이티브 코틀린 / 손님=웹** 분리로. android_ref 자산 직접 완성이 웹 래핑보다 빠름. (reservation_engine/README.md 정합.)

### [확정] 미용실 파일럿 현장 확보 = GO바 대상
- 원규씨 2026-06-09: 미용실 1곳 "하겠다" + 선물로 만들어주기로 약속 → 거친 실손님 통화 = 거기서 확보(6/7~9 자가녹음 천장편향 벗어나는 진짜 field GO바).
- 바꾸려면: 그 미용실이 무산되면.

### [잠정] 엔진 자산 → coupon_app/reservation_engine/ 복사
- designated_driver 통화예약 부품(STT 백엔드·통화캡처 코틀린 참조·검증기록·측정샘플) → coupon_app 복사해 거기서 통합(크로스 프로젝트 Firestore보다 단순, 종착지 직접). 원본 보존(designated_driver=부품창고).
- 상세 = 플랜 `~/.claude/plans/cheerful-pondering-harbor.md`.

### [잠정] 통화예약 표면 = 미용·식당 (대리·택시는 PTT — 별 도구, 2026-06-09 밤3 재정의)
- **통화예약 STT 엔진의 표면 = 미용·식당**(예약 데이터 추출형). 미용 첫 타자(현장 확보=사장 선물).
- ⚠️ **"엔진 하나 + 세 표면(미용·택시·대리)" 프레이밍 폐기** — 그건 통화예약을 대리·택시까지 확장한 6/8 드리프트(아래 "PTT 간단모드" 섹션 + 전역 `_decisions.md` [확정] "업종이 도구를 가른다"). **대리·택시 = PTT**(위임형 별 도구)라 **통화예약 표면에 대리·택시는 안 들어감**.
- 분산 위험 = 통화예약(미용·식당)은 표면만 교체 / PTT(대리·택시)는 별 엔진. 두 도구를 헷갈려 섞지 말 것.

## 전화번호 / 손님 식별

### [확정·변경시 입증] 콜 발신자의 다수 = 단골(=폰에 저장된 연락처)
- 원규씨 현장 단언(2026-06-09): "대부분 단골손님, 즉 저장된 이들이 콜을 부른다."
- 함의: 삼성 자동녹음 파일명은 **저장연락처면 '이름'**(은결아빠·쿠쿠전자)을, 미지발신자면 'raw 번호'를 박음 → **다수 콜은 파일명에 번호가 안 뜨고 이름이 뜸**.
- 바꾸려면: 현장 콜 분포 데이터가 "미지발신자 다수"로 나오면.

### [폐기·2026-06-09·Play 2026.4 Contact정책 심사난관 + 행동0 충돌 → 업소DB 매칭으로 대체] READ_CONTACTS로 이름→번호 자동조회
- 6/3 메모리의 "저장연락처 번호는 READ_CONTACTS로(코어엔 불필요)" 전제를 **두 겹으로 폐기**:
  ① **전제 오류**: 6/3은 "미지발신자 다수"를 가정했으나 실제는 단골(저장연락처) 다수 → 번호는 "불필요"가 아니라 "다수에서 필요".
  ② **Play 심사 난관**(사실확인 2026-06-09): 2026.4 신설 Contact Permissions 정책 — 선언폼+승인 필요(2026.10.28 강제), **Android Contact Picker(사용자가 매번 탭해 고름)가 불충분함을 기술입증**해야 통과. "거래상대 고르기(choosing a contact to transact with)"는 명시적 거절 사례. **자동 백그라운드 조회(우리 용도)는 가장 거절나기 쉬운 칸** — 행동0(무탭)이 곧 승인의 적.
- 근거: [[reservation_engine_pilot_verify_2026-06-09]] (Play 정책 원문 출처 포함)

### [잠정] 전화번호 출처 = 업소 고객DB(귀속) 이름매칭 + 첫통화 1회 보정 시드 (폰 연락처 아님)
- 단골 = 귀속(손님↔업소 끈)이면 번호의 진짜 출처는 **업소 자신의 기록**(`customers/{uid}`·`customerInfo/{phone}`)이어야 — 폰(구글 생태계)이 아니라 업소가 관계데이터 소유 = 귀속 해자 강화.
- 부트스트랩: 첫 통화만 사장이 [확인]에서 번호 보정(시드) → 업소DB 저장 → 이후 동일 단골 콜은 이름매칭으로 자동(행동0 수렴). 쓸수록 단골 관계가 깊어짐.
- 잠정인 이유: 방향은 확정(READ_CONTACTS 폐기 대체재)이나 **아직 구현·검증 전**. GO 후 설계.
- 바꾸려면: 이름매칭 충돌(동명이인)·미등록 단골 비율이 실용성을 깨면.

### [확정] 첫 파일럿 파싱 정확도 = 통과 (전화번호 제외, 그것도 설계상 예정 지점)
- 2026-06-09 폰 실연: storage 규칙 배포 후 W경로 end-to-end 작동. **출발·목적지·시각·업종·헛예약0 모두 정확**, STT p=1.00. 미충족은 전화번호뿐인데 그건 버그 아니라 위 단골=저장연락처 구조.
- ⚠️ 단 6/7~9 샘플은 본인 자가녹음(천장편향) — **진짜 GO바 = 거친 통화 (대상 변경: 양평 실손님 → 미용실 현장, 2026-06-09 밤2)**는 미확보(다음).
- 근거: [[reservation_engine_pilot_verify_2026-06-09]]

## PTT 간단모드 (대리·택시 사장측 행동0 — 통화예약과 별 트랙)

### [잠정] 다음 작업 = PTT 간단모드 본체 (대리 트랙)
- CLAUDE.md 드리프트 교정(대리=PTT)의 후속. **통신 인프라는 완성**(매니저·픽업 양방향·기사 수신·알림 Data Push 통합·블랙박스 9-A 자동재생), **콜 운영 배선이 남음**.
- 본체 시나리오: 통화 중 매니저 무전 1마디 → ① 기사 음성 수신 ② 빈 콜카드 자동생성+음성첨부 → 기사 자율입력 → 매니저 문제만 검토. 매니저 수기 14필드→0.
- 갭(2026-06-09 밤4 갱신): ~~★발화↔콜 음성명령/빈카드 자동생성~~ → **[폐기·과설계] 아래 블록 참조** + screen-off 송신 + (콜드 단축 ✅완료 `b66d0fdf`) + 정산 단순화 P5~P8 + 블랙박스 9-B(콜상태→채팅 자동기록).

### [확정·반드시 해야 함·2026-06-10 원규씨 명시] 채팅 블랙박스 9-B = 시스템 이벤트 자동 채팅 게시
- **결정**: 채팅 블랙박스 9-B(배차·기사상태·콜 상태 전이 → 채팅에 `type:"system"` 자동 게시)는 **반드시 해야 하는 작업**. 원규씨 종결 — "블랙박스는 (클코가) 판정하는 게 아니야, 반드시 해야 하는 거야." ⚠️ 클코가 이를 "인지 보험이라 nice-to-have"로 강등한 판정 **기각**(우선순위 판정 = 원규씨 종결권, [[feedback-folder-scope-before-proposing-2026-06-10]] ③).
- **상태**: 9-A(음성 PTT 발화 → 채팅 자동기록+기사 자동재생) = ✅완성. **9-B(시스템 이벤트) = 미완 = 이 작업.**
- **차별화 근거(메모리)**: "외부 PTT 앱이 못 하는 자리 — 콜마당 자체 인프라라서 가능"([[ptt-operation-scenario]] 시나리오 9-B). 양방향 인지 보험 + 사후 추적.
- **설계 원본(스펙 이미 확정)**: [[ptt-operation-scenario]] §9-B + SSOT `docs/chat-shared-spec.md` §13("시스템 메시지 V2" 예고). 대상=콜 전단계(들어옴→배차→수락→시작→완료)+취소+예약/공유+기사 출퇴근+정산 **전부**(원규씨 "맞아" 확인, 트리밍 X). 알림=Data Push **무음**(Room INSERT만, 소리·배너 0 — 평소 안 보고 분쟁·놓침 시만 봄). UI=중앙 회색 작은글씨+필터토글+collapse.
- **★조사 완료(2026-06-10, Explore 2)·"한 줄" 오해 정정**: ① 채팅 저장=`chatRoom/main/messages` ② **`type` 필드가 전 계층 부재**(Firestore·FCM·Room·렌더) → 5/25 "한 줄"은 채팅 구현 전 가정, 실제=type 필드 full-stack 배선 ③ functions는 채팅 직접 write 안 함(클라가 씀)→시스템 메시지는 functions 신규 write+무음 FCM ④ 트리거 5종 보유(`onCallStatusChanged` index.ts:2109·`oncallassigned`:522·`onCallCancelledByDriver`:3292·`onDriverStatusChange`:4841·`oncallreserved`:2029) ⑤ 클라 3앱 채팅 구조 동일(앱당 ~3파일: LocalChatMessage+ChatRepository+ChatScreen, 픽업은 audio 미지원).
- **플랜(승인)**: `~/.claude/plans/abundant-wondering-minsky.md` — Commit 3단(E2E 슬라이스→이벤트 확장→가독성 UI).
- **✅ Commit 1 완료·배포·E2E검증(2026-06-10, `67a3c779`)**: 콜 상태 전이(배차/수락/시작/완료/취소)를 무음 시스템 메시지로. **구현**: functions `chat.ts`(type 필드+`postSystemMessage` 헬퍼+FCM `chatType` 키)+`index.ts` `onCallStatusChanged` 게시 / 3앱 동일 패턴(Room type 컬럼+마이그레이션 call_mgr v9→10·driver v3→4·pickup v4→5 / ChatRepository read / ChatScreen `SystemMessageBubble` 중앙회색 / FCM 무음 분기). **배포**: `onChatMessageCreated`·`onCallStatusChanged` production. **검증**: 양평 사무실 채팅에 test 시스템 메시지 직접 write→S21+(call_mgr)·ZFlip4(driver) 둘 다 `무음 INSERT`(playChatSound 미호출=헛알림0)+Room INSERT 확인 후 삭제. Room 마이그레이션 무크래시. ⚠️ `onCallStatusChanged`→`postSystemMessage` *실콜 트리거*는 미실측(밤시간 LIVE라 가짜콜 회피)—다음 실콜에 발화, 코드는 read 검증.
- **✅ Commit 2 완료·배포(2026-06-11, `9da961c1`+`57623574`, push됨, functions 전용=앱 재빌드 불필요)**: rules-first(유일 LIVE 노출) + 잔여 3건 + 트리거 4종.
  - **A. `firestore.rules` 스푸핑 차단**: 메시지 create 규칙에 `request.resource.data.get('type','') != 'system'` + `get('senderRole','') != 'SYSTEM'` AND절 — 클라 system 위조 차단(CF=Admin SDK 우회). 컴파일·released 확인. 일반 채팅 무영향(클라는 type 미설정).
  - **B 잔여 3건**: ① 신규콜 미기록 → `sendNewCallNotification`(index.ts:825, onDocumentCreated)에 "콜 들어옴" 게시(중복삭제 가드 뒤·handledByManager 제외). `onCallStatusChanged`는 onDocumentUpdated라 create 못 잡던 갭. ② **double 운행완료 정정** → `AWAITING_SETTLEMENT`="운행 완료"(실제 운행완료, 테스트 `Driver1CallDispatchTest` L228 확증)·`COMPLETED`="정산 완료"(L348). 직전 플랜의 "AWAITING 버리고 COMPLETED 유지"는 의미 거꾸로라 폐기. ③ handledByManager 제외=`onCallStatusChanged:2144` 이미 적용 확인 + 신규콜 후크에 동일 가드 연계.
  - **C. 트리거 4종**(원규씨 범위결정 2026-06-11): 기사 출퇴근(`onDriverStatusChange`:4871 — OFFLINE↔ONLINE/WAITING만, 잦은 전이 제외) · 예약(`oncallreserved`:2029 RESERVED 진입 단일점) · **공유콜 등록·수임만**(`onSharedCallCreated`/`onSharedCallClaimed`, 출처 사무실 게시. 나머지 3 트리거=범위 밖) · **정산 제출만**(`onDriverSettlementSubmitted`, 매니저 토큰 무관 try 앞에서 항상 기록. 확인/마감=범위 밖).
  - **검토 정정 2(오염)**: ① 정산 후크를 토큰 early-return 앞으로(항상 기록) ② **공유콜 수임 메시지를 wallet revert(식당앱콜 잔액부족 1397 return) 뒤로 이동**(`57623574`)=거짓 수임 기록 방지. 대리 교차사무실은 wallet 면제라 동작 동일.
  - 후크 전부 best-effort(`postSystemMessage` 자체 try/catch) → 콜 운영 비파괴. 배포=rules + functions 7개(surgical).
  - ⚠️ **E2E 실콜 검증 미완(다음)**: 신규콜 "콜 들어옴"·double완료 1건·출퇴근 등 = 밤시간 LIVE 가짜콜 회피 → 자연 발생 콜 로그로 확인(Commit 1과 동일). 코드/컴파일/배포는 검증됨.
- **✅ Commit 3 완료·빌드·시각검증(2026-06-11, `91b1c49a`, 앱 전용)**: 시스템 메시지 **필터 토글**(자동 collapse는 과설계라 폐기 — 토글 OFF가 도배를 더 단순히 해결, 원규씨). `ChatBottomSheetContent`(실 render site) expanded 상단 헤더에 눈 아이콘 토글(`rememberSaveable`·기본 ON·비영속), OFF면 `messages.filter{type!="system"}`로 사람 대화만(그룹화도 shown 기준). 3앱 동일. **★누락·오염 검토 성과**: 풀스크린 `fun ChatScreen(`은 3앱 모두 호출처 0 = **죽은 코드** → 원안(TopAppBar에 토글)은 안 보이는 곳에 다는 설계였음, BottomSheet만 실사용으로 정정 + VM 변경 불필요(로컬 state)로 단순화. SSOT `chat-shared-spec.md` §15 시스템 메시지 절 신규(§13 V2미포함서 이동). 3앱 빌드 통과 + S21+/ZFlip4 install + **S21+ 콜매니저 토글 ON/OFF 시각 확인**(테스트 시스템 메시지 3건 주입→확인→clean). pickup=기기 미연결 install 보류.
- **⛳ 남은 작업(다음 세션)**: ① 9-B Commit 2 **E2E 실콜 검증**(자연 콜 logcat — 신규콜/double완료/출퇴근) ② 공유콜 나머지 3트리거(취소/동기/완료 — 교차사무실 게시 정책 정한 뒤) ③ PTT→텍스트 Step 2(`sttSpikeEnabled=true`). S22·pickup 기기 미연결(연결 시 install). 임시 `functions/scripts/test-system-message.js`(테스트 메시지 add/clean) 보존. 주입한 테스트 메시지 3건은 **의도적 잔존**(원규씨 "그냥 두어도 돼" — 1004 테스트 사무실 무해, clean 불요).

### [확정·2026-06-09·원규씨 명시] 화면오프 발화 입력 = 전역후크(Accessibility 볼륨키 캡처) 배제
- 화면 꺼진 채 볼륨키 전역 캡처(Accessibility)는 **안 씀**(삼성 절전 재허용 마찰 = 행동0 적).
- 통화없는 화면오프 능동발화의 입력 수단은 미결(블루투스 등 후속). 주력 = 통화-trigger prewarm(아래).

### [확정·2026-06-09 구현·측정통과] 콜드스타트 단축 = 토큰 캐시 + wake fire-and-forget (★비용 무관)
- **진단(코드 직접 확인 2026-06-09)**: `generateAgoraToken`·`sendPttWake` 둘 다 이미 `minInstances:1`(ptt.ts L24·L120 — 함수 콜드 주범 아님). 5초 = ① 송신측 직렬 왕복(토큰 await→join→wake await) + ② **READY_TIMEOUT 3초**(`PTTManager` L46) — 수신측이 `onWake`에서 또 `generateAgoraToken` 호출(PTTManager L288)해 join 느림 → `remoteUsers>0` 지연.
- **대안**: Agora 토큰 24h 캐시(송·수신 양쪽 함수 왕복 제거 → **수신측 join 가속 → READY_TIMEOUT 병목까지 완화**) + wake fire-and-forget(응답 안 기다리고 join; fireReady는 onUserJoined/타임아웃이 결정 L209·244). → 5초 → ~1~1.5초(수신측 FCM Doze 도달만, 비프 흡수).
- **비용 무관 확정**: Agora는 채널 접속시간 과금 / 이 대안은 접속시간 불변(토큰발급 비과금, 함수 호출 오히려 감소). 비용 레버는 통화prewarm·워밍창(CONV_WARM_MS 45s)·minInstances쪽(별개).
- **실측(2026-06-09, `b66d0fdf`, S21+→Z Flip4)**: 토큰캐시 히트(송·수신 SharedPreferences `ptt_token_cache`)·join 146/143ms·**콜드 5초→2.85초·WARM 0.44초**. 잔여 ~0.6초 = 수신측 FCM Doze wake(통화prewarm 없이는 본질 하한, 비프 흡수). 3앱 빌드+install+회귀 통과. 🟡사소(별트랙): 첫 발화 시 `ensureToken` 2회 호출(dispatch+prewake 추정, 무해).

### [잠정] 통화-trigger prewarm (통화 OFFHOOK → PTT 예열)
- 통화 캐치 시 PTT 채널 예열 + 수신측 미리 깨움 → **통화 직후 발화 콜드 0** + 통화화면 켜진 상태라 **screen-off 우회**. 매니저 발화는 거의 통화 직후라 적중률 높음.
- 기존 `CallReceiver`(PHONE_STATE)→`CallDetectorService` 재사용(콜매니저에 이미 완비). 비용 = 통화 길이만큼 채널 접속(다이얼 가능).
- ~~🚩선결: 어느 폰이 통화 받나~~ → **[해소·2026-06-09 밤5]**: 원규씨 확인 "콜매니저·콜디텍터 두 앱에서 전화 받음" + 코드 확인 — 콜매니저 `receiver/CallReceiver.kt`가 RINGING/OFFHOOK/IDLE 다 감지(수신전화→`CallDetectorService`). **매니저폰(=PTT 송신폰)이 통화를 직접 받으니 로컬 예열 가능**(폰간 전달 불필요). 후크 자리 = `CallReceiver`의 RINGING/OFFHOOK 분기에 기존 `PTTManager.prewarm()/prewarmToken()` + `sendPttPreWake`(Cloud Function) 호출만 얹기. RINGING(울리는 순간)부터 잡혀 리드타임 충분.
- ⚠️ 우선순위: 콜드 이미 2.85초라 prewarm 체감이득 작음 → screen-off 송신(운전중)이 더 본질적일 수 있음(미결).

### [잠정·2026-06-09 밤5·구현·측정통과, 값은 사용 후 조정] PTT 비프 1차→2차 최소 간격 0.5초 고정
- 문제: WARM 빠른 연결에서 1차(누름)·2차(말해도 됨) 비프가 ~0.04초로 붙어 **겹침 + 간격 들쭉날쭉**. (삐 1번이면 사람들이 1차에 바로 말해버려 안 됨 — 2차 후 발화 습관이라 비프 2개는 유지.)
- 해결: `PTTManager.fireReady`가 1차 후 **최소 `MIN_CUE_GAP_MS`(500ms) 보장** 뒤 2차 비프+마이크 라이브. 느린 연결(COLD)은 자연 소요시간대로(추가 대기 0). 커밋 `3e9e5361`.
- 실측(S21+): WARM gap=408·392ms(누름+500ms 고정), COLD gap=0(자연 1.8s). ✅ 빠를 때 항상 같은 텀.
- **잠정인 이유**: 0.5초 = 잠정값, **사용해보고 조정**(원규씨). 미세조정 = `MIN_CUE_GAP_MS` 한 줄.
- **✅ 픽업기사 앱 적용 완료(2026-06-10)**: 동일 PTTManager 포크에 비프 fix 포팅(`MIN_CUE_GAP_MS`+`cuePressTime`+`startTransmit` 기록+`fireReady` 비동기 delay). 두 `PTTManager.kt` 전체 diff = **기능 코드·음원(`ptt_start.m4a` MD5 동일·1.185s)·오디오설정 100% 동일**(패키지명·senderName "매니저"↔"픽업기사"만 차이). 3단말 빌드+install. **타이밍 검증 통과(S22)**: WARM gap=402·409·413·419ms(콜매니저 408·392ms와 동일), COLD gap=0. 간격 fix 정상.
- ⚠️ **테스트 함정(2026-06-10 기록)**: ① PTT 앱은 **폰당 1개**여야 함 — 한 폰에 콜매니저+픽업+driver 동시 실행 시 같은 Agora 무전망에서 서로 수신·오버톤 울려 "충돌"로 들림(실배치는 폰 분리). ② 송수신 전달은 **같은 office(채널 `gyeonggi_<officeId>_ptt`)** 로그인 필수 — 다른 office면 채널명 달라 안 닿음.

### [열림·2026-06-10·디테일 나중·기기 의심] 픽업 2차 비프 볼륨이 1차보다 작음 (콜매니저는 일정)
- 증상: 픽업(S22) WARM 발화 시 2차 비프(말해도 됨)가 1차(누름)보다 작게 들림 → "비프 일정하지 않음". 콜매니저(S21+)는 일정.
- 코드·음원·오디오설정 = 콜매니저와 **검증상 동일** → 가장 유력 = **S22 기기가 통화중(Agora 통신모드 active) 신호음(USAGE_VOICE_COMMUNICATION_SIGNALLING)을 더 죽임**(1차는 채널 hot 전, 2차는 hot 후라 ducking 차이). 콜매니저 "일정"은 다른 기기(S21+) 관찰 = 기기 변수 미통제.
- **확정 미완(다음)**: 콜매니저를 **S22에 깔아 같은 폰 비교** → 콜매니저도 S22서 2차 작으면 기기 확정(픽업 코드 정상, 무수정). 일정하면 픽업 차이 재조사. ⚠️ 공유 `playCue` 수정은 잘 도는 콜매니저까지 영향 → 기기 판정 후에만.
- 원규씨 2026-06-10: "디테일은 나중에" → 보류.

### [폐기·과설계·2026-06-09 밤4·원규씨 기각] 발화↔콜 = 음성명령 STT·기사명 매칭·빈카드 자동생성
- 폐기 사유: 원규씨 명시 — *"배차팝업에서 기사 지정 + PTT로 어디 가라 음성지시면 됨. 너무 어렵게 생각하는거 아닌가?"* → 발화에서 기사명 파싱(SpeechRecognizer/CallMemoParser)·자동 빈카드 생성·음성배차 = **과설계**.
- **실제 필요 = 둘 다 *이미 구현·작동***: ① 배차팝업(`NewCallAssignmentDialog`→`assignCallToDriver`)에서 기사 탭 — 출발/목적/요금 **안 쳐도 배차됨**(필드 nullable) ② PTT 음성방송으로 "어디로 가라" 전달. → 매니저 타이핑 0 = **행동0이 이미 충족**(음성파싱 없이).
- ⚠️ 위 "다음 작업" 본체 시나리오의 "빈카드 자동생성+음성첨부"는 *비전 표현*일 뿐 — 그 행동0은 popup배차(무타이핑)+PTT로 달성. **음성명령/기사명매칭/빈카드 자동생성 코드는 짓지 않음.** 필요 재등장 시 재평가(목표는 유지, 구현수단만 보류).
- 클코 학습: [[feedback_overengineering_before_simplify_2026-06-09]] (단순화 신호 전 과설계 금지).

### [확정·2026-06-09 밤4·원규씨 승인] 배차 알림 = 반복·풀스크린 제거 + 단일 알림음 1회 (PTT가 긴급성 보강)
- **근본원인(logcat 확정, S21+→Z Flip4)**: 배차 시 기사폰 `LockScreenActivity`가 벨 **3초 반복**(`startAlertSound`)+진동 루프+풀스크린 점유 → [확인] 누르기 전까지 **매니저 PTT 수신 음성을 마스킹** = "기사폰서 발화 안 들림"의 정체. PTT 경로 자체는 송·수신 정상(`b66d0fdf` 측정과 정합).
- **결정**: PTT 없던 시절 "콜 놓침 방지"용 과잉 알림 → PTT 도입으로 불필요 + PTT 차단 주범 → **반복·풀스크린 제거, 단일 알림음 1회는 유지**(양평 LIVE라 청각 단서 보존, PTT 안 한 콜 놓침 방지). 화면 강제기상 포기 = 원규씨 승인 트레이드오프(긴급성은 PTT 보강).
- 구현: `MyFirebaseMessagingService.showNotification()`에서 `setFullScreenIntent` 제거(=LockScreenActivity 미기동). 수락 흐름은 알림 탭→MainActivity(callId)로 보존(LockScreen [확인]과 동일 경로). 상세 = 플랜 `~/.claude/plans/compressed-strolling-lamport.md`.
- **✅ parity 점검 완료(2026-06-10) — 콜매니저 driver식 심각 마스킹 *없음*, 수정 보류**: 수정은 `driver_app`만이나, 코드 대조 결과 콜매니저엔 driver 마스킹 주범(LockScreenActivity 3초 반복벨)이 **없음**(`isLooping=true`/`setLooping(true)` **0건**). ⚠️ 밤5의 "반복루프 `DashboardViewModel:1871`"는 **오인** — 그 줄 = `startSharedCallTicker`의 **60초 UI 티커**(공유콜 목록 새로고침), 오디오 아님. `setFullScreenIntent`는 4곳이나 라이브·소리 동반은 2곳뿐: `MyFirebaseMessagingService:1237`(NEW_CALL/공유콜·단발음)·`:1381`(커스텀 공유콜·DEFAULT_ALL). 나머지 = `CallOverlayActivity:139`=**데드코드**(`startOverlay` 미호출) + `CallDetectorService:555`=**무음**(`setSound(null,null)`, 통화종료 자동전환=화면만). 픽업앱=`setFullScreenIntent` 0건(깨끗)✓.
  - **잔여 위험 = 잠금화면에서 새콜 full-screen+단발음(2곳)이 PTT 수신과 겹치는 좁은 케이스뿐**(driver식 반복벨 마스킹은 코드상 불성립). 콜매니저 full-screen 새콜 팝업 = **매니저 핵심 배차 UX**(driver의 순수 콜놓침방지와 다름) → **수정 보류, 현장검증(매니저가 픽업 발화 받을 때 실제 덮나) 후 판단**(원규씨 2026-06-10). 덮는 게 확인되면 PTT 수신중 한정 ducking 등 surgical 적용 검토(driver식 full-screen 일괄 제거는 배차 UX 리스크).
- 바꾸려면: 단일 알림음으로 기사가 실제 콜을 놓치는 현장 데이터가 나오면(그땐 PTT 의무화 or 중간 강도 재도입).

## 정산 단순화 (PTT §3 트랙)

### [확정·2026-06-10 코드 실측] 정산 단순화 *본체* = 적용 완료 (master + 현재 브랜치 둘 다)
- 적용된 것: status **4→2**(`PENDING_CONFIRM↔CONFIRMED`, `TRANSFERRED`/`SETTLED` 0건) · **carryOver(이월금) 폐기** · 실납입/환급/미납 개념 폐기(납입금 net 한 줄) · 매니저 외상명단 처리 폐기 · 영업일 **6시→10시**(functions `settlement.ts:63` + 앱 `SettlementViewModel.kt:290·895`·`DriverViewModel.kt:1514` 모두 `< 10`). = `manager-direct-drive` commit 1~5 내용, master·`feature/reservation-engine-poc` 둘 다 포함(심볼 실측).
- `functions/index.ts`의 carryOver = `:5299` **stale 주석 1줄뿐**(기능 아님).
- ⚠️ **밤5(6/9) 메모 "P5~P8 미구현·4상태+carryOver 잔존" = 오인**(정정 2026-06-10). functions의 `PENDING_CONFIRM` 핸들러 잔존 + functions 미deploy를 "4상태 미적용"으로 오판한 것. 실제 단순화 본체는 코드에 다 들어가 있음. **다음 세션이 "P5~P8 재구현"으로 헛발질 금지** — 재구현 시 중복·회귀.
- 근거: [[ptt-section3-entry-2026-05-27]] §11.7(실측 스탬프).

### [잠정·2026-06-10 처분 정정] 정산 잔여 3건 = 보급 재개 준비 작업 (파킹 아님)
**★ 2026-06-10 정정**: 앞서 "정산 실사용 X → 우선순위 낮음·파킹"으로 처분했으나 **틀림**(원규씨 지적). ① "혼자 쓰니 강제 게이트 의미 없음"은 오류 — **1사무실도 기사 여럿**이라 게이트는 기사·매니저에 적용돼 유효. ② "미용/식당 정산 쓰면" 트리거는 카테고리 오류 — 미용/식당은 통화예약(coupon_app) **별 모듈, 대리 정산 무관**. ③ "실사용 X"는 destiny 아닌 출발점 — **단순화(완료)+PTT 간소화+테스트 사무실 보급 재개 = 적극 사용으로 가는 경로**(원규씨 "정리되는대로 다시 보급할 거야"). 안 쓰는 이유를 없애려 단순화한 건데 "안 쓰니 보류"는 자기모순.
→ **공통 트리거 = "정리되는대로 보급 재개"** 그 단계. **★ 2026-06-11 원규씨 지시: "보급 준비로 가야 해(디테일 다듬으며)" → 이 잔여 3건이 지금 착수 방향.** 잔여는 *파킹*이 아니라 *보급 준비 작업*:

- **① P7 EnforcementGate (강제 게이트) — [구현 완료·기기 미검증·2026-06-11]**: 별 모듈 `enforcement/`로 4종 중 **3종 구현 + 1종 폐기**. 플랜 `~/.claude/plans/woolly-bouncing-hippo.md`. 정산 로직 불변(읽기 전용), 유일 신규 쓰기 = 매니저 confirm 1개.
  - **#1/#2 LogoutGuard (driver)**: HomeScreen 로그아웃 버튼 가드 — 미정산(`tripCount>0 && status≠PENDING_CONFIRM`)이면 차단 안내. 퇴근하기(PENDING_CONFIRM) 정상 종료 보존. (HistorySettlement 로그아웃 다이얼로그=오프너 없는 죽은 코드라 가드 불요.)
  - **#3 DailyCloseGate (driver)**: `DriverViewModel.needsDailyClose`(읽기 전용 — **이전 영업일 10시 경계 이전 미정산 잔존**만 true. 단순 tripCount>0이면 근무 중 오발동→운영 파괴라 교정) + AppNavigation `DailyCloseRedirect`로 정산화면 1회 강제.
  - **#4 ManagerUnconfirmedModal (call_manager)**: 미확인 기사 닫기 불가 Dialog(기사별 [정산확인]/[개별 검토], [전체 확인] 없음). 모달 = `hasSubmitted && !isConfirmed`만. MainActivity 모달 오버레이(VM init이 login_prefs로 목록 자동 로드). **로그 검증(2026-06-12)**: 기사 1004 업무마감(PENDING_CONFIRM)→퇴근(OFFLINE) 후에도 매니저 `dailySettlements:1` 로드 = "확인 전 증발" 수정 실증.
  - **★ confirm = 삭제 아니라 도장 (원규씨 지적으로 2차 수정)**: 1차 구현이 `confirmDriverDailySettlement`에서 dailySettlement **삭제** → [정산확인] 시 기사별 내역 증발(업무마감도 안 했는데). 원규씨 "정산확인하니 내역 사라지네" → 설계(§9.2 "도장이지 삭제 아님")로 교정 = `dailySettlement.confirmedAt` 도장만(중첩 필드 update) + **PENDING_CONFIRM일 때만 WAITING 해제**(퇴근 OFFLINE 기사 안 되살림). 모델에 `confirmedAt`+`isConfirmed`, 기사별 탭 `✅정산확인 완료` 표식. **실 삭제는 영업마감(clearDailySettlement)만**. 데이터 보존.
  - **★ 부수 수정(원규씨 승인)**: 기사 `clearSettlement`(퇴근하기)가 `dailySettlement`을 조기 삭제 → 매니저 확인 전 증발(게이트 #4 토대 붕괴)하던 **잠재 결함 수정** = 퇴근하기에서 삭제 제거(삭제는 매니저 confirm/영업마감만). 원규씨 발견("콜매니저서 확인도 없이 사라지네").
  - **요구 확정(원규씨)**: ① 미확인분은 다음 세션까지 남아야 + 로그인 시 확인 안 하면 업무 진행 0(닫기 불가 모달 = 의도대로) ② 데이터 삭제는 영업마감만.
  - 빌드·assembleDebug 성공, driver→ZFlip4 / call_manager→S21+ install. **미커밋**. **남은 검증(다음)**: 새 사이클로 [정산확인]→기사별 내역+`✅확인` 보존, 영업마감 때만 삭제 확인(1004 이전분은 구버전 삭제코드로 이미 증발=테스트 흔적).

### [폐기·2026-06-11·원규씨 결정·cron 대체 + 최소개입 + 단순화] P7 게이트 #5 ManagerCloseGate (10시 매니저 마감 강제 잠금)
- **폐기 사유**: ① **auto-finalize cron(10:10)이 어제 세션 `isFinalized` 봉인을 서버에서 자동 처리** (`functions/.../handlers/settlement.ts` `autoFinalizeSettlementSessions` — *봉인만*, 기사 dailySettlement는 안 건드림 → 게이트 #4 무력화 안 함). ② 기사별 정산확인 강제는 **게이트 #4가 독립 담당**. ③ 원규씨: "매니저가 마감 습관적으로 건너뛰는 건 우리 책임 아님 + 최대한 개입 줄이고 로직 단순화." ④ 10시 이후 `clearAllTrips`가 *오늘 키* 봉인하는 날짜 불일치 데드락도 회피.
- 소프트 리마인더(배너)도 **안 함**(그것도 개입). #5 코드 아예 안 지음(`ManagerCloseGate.kt`/call_manager `EnforcementGate.kt` 미생성).
- 바꾸려면: cron이 못 미더운(서버 장애 빈발) 데이터 or 매니저 수동마감 housekeeping(Room 정리·office lastCleared)이 실제 운영 깨는 근거. ⚠️ housekeeping ②③은 매니저가 *평소처럼* 마감하면 됨 — 폐기는 "마감 행위"가 아니라 "10시 강제 타이밍"만 없앤 것.
- **② functions deploy — ✅[완료·2026-06-11]**: `firebase deploy --only functions --force`(minInstances 과금=기존 PTT 콜드단축, 신규 증가 0). `calldetector-5d61e` LIVE 전 함수 정합 — 9-B 트리거 4종·정산(autoFinalizeSettlements·10시)·parseReservation 포함 "Deploy complete!". 서버를 현재 앱(정산 단순화 10시)과 정합시킴.
- **③ 매니저 수동 [업무마감] 즉시 봉인 (§11.6 #1) — ✅[완료·2026-06-11·`472e16fa`]**: `clearAllTrips`(SettlementViewModel.kt:650 Firestore 쓰기 블록)에 `settlementSessions/{calculateWorkDate(closingTime)}.metadata.isFinalized=true` merge set 추가. 영업일 키=functions calculateWorkDate(10시)와 동일, merge라 기존 세션 보존. 빌드·S21+ install·push. ⚠️ 실 봉인 동작 E2E(업무마감→isFinalized 확인)는 정산 세션 필요라 자연 사용 시 확인.
- ⚠️ 셋 다 "정산 단순화 본체"가 아니라 *그 위 강제/배포/봉인 보강*. 본체는 [확정] 완료 — 재구현 금지.
- ⚠️ **전제 = 보급 재개 = 양평 다른 사무실/타지역 보급**. 메모리엔 5/19~5/27 "보급 정지"(쿠폰 우선)로 기록 → 본 [잠정]은 그 정지가 "정리되는대로" 풀린다는 원규씨 2026-06-10 방향에 의존. 정지가 길어지면 타이밍만 밀림(처분 framing은 유효).

## 모든 PTT → 텍스트 블랙박스 (9-A 완성, 채팅 블랙박스 트랙)

### [확정·2026-06-10/11 원규씨] PTT 발화를 텍스트로 = 모든 PTT 라이브 캡처, 온폰 STT, 부정확 허용
- **전제(원규씨)**: 음성=정확 원본(이미 라이브로 들음), **텍스트=맥락파악·사후검색용 → 부정확 허용**. ∴ 비싼 서버 과잉, **비용0·PII 온폰**(음성이 구글로 안 감=거인 회피 정합)이 정답.
- **★ 전제 정정(누락·오염 검토에서 발견)**: PTT는 현재 **라이브(Agora·transient)라 기록을 안 남김** — 콜드 음성메모 경로는 `PTTManager.kt`의 `recordPending`/`onColdVoiceMemo`가 **"도달 불가(보존)" = dormant**(2026-06-03 "포그라운드 항상 라이브" 재설계). 채팅 ▶ 음성들 = **legacy**. → **"음성 메모만 전사" [폐기]**(죽은 경로) → **모든 PTT 라이브 캡처**로 전환(원규씨 선택).
- 범위 = 모든 PTT(라이브 warm 포함). 음성 첨부는 선택, **텍스트가 목적**. 송신앱만 캡처=**call_manager**(+pickup). driver=수신·렌더만(PTT 송신無).

### [확정·2026-06-11 스파이크 실증] 캡처 = Agora `startAudioRecording(MIC)` → WAV (프레임 옵저버보다 단순)
- **Step 1 스파이크 성공(S21+, `265dc016`)**: `fireReady`(mic live)서 `startAudioRecording(AudioRecordingConfiguration{fileRecordOption=AUDIO_FILE_RECORDING_MIC(1), 16kHz})` → cache WAV, `stopTransmit`서 정지. **전송 비파괴**(PTT 정상 WARM 전환). 실발화 "수연이 나와 가지고 군청 앞에 있는 그 저기 집…" faster-whisper **정확 전사** → 라이브PTT→텍스트 체인 성립.
- ⚠️ **MIXED(3) 금지**: 비프·플레이백 섞여 STT 횡설수설("둘 다운아"). **MIC(1)=마이크 원본**이라야 깨끗.
- API 사실: `io.agora.rtc2.internal.AudioRecordingConfiguration`(no-arg+필드 `filePath/sampleRate/codec/fileRecordOption/quality/recordingChannel`), `Constants.AUDIO_FILE_RECORDING_MIC=1`. SDK=`voice-sdk:4.5.2`(classes.jar 빈 wrapper, 실클래스=`voice-rtc-basic` `agora-rtc-sdk.jar`). `registerAudioFrameObserver`/`setRecordingAudioFrameParameters`도 존재(라이브 STT 시 대안).
- **★ 설계 간소화**: 플랜의 "프레임 옵저버"보다 `startAudioRecording`이 **WAV 직접 출력** → 파일 기반 STT에 그대로 투입. 캡처 방식 이걸로 확정.

### [확정·2026-06-11 구현·E2E검증] Step 2/3 = 온폰 whisper.cpp(tiny) → type="ptt" 무음 메시지 (완료)
- **엔진 = ① 온폰 whisper.cpp ggml 확정** (②③ 폐기 — ②Android recognizer=한국어/PII 불확실, ③서버=PII 서버행·온폰[확정] 위반). PII 온폰·비용0·기기독립.
- **모델 = small(ggml-small-q5_1, 190MB) [잠정·실측 비교]**(2026-06-11 폰 3종 비교): tiny(32MB)·base(57MB)는 **한국어가 꼬임** — 불명확 구간을 `[뚜껑 닫는 중]`·`[라이브]` 같은 **비음성 헛태그**나 횡설수설("왜 가끔 엉뚱한 말을")로 메꿈(약한 음향모델=언어모델 prior 폭주). **small에서 헛것 거의 사라지고 "실제 한 말"이 안정 복원**("군청으로 가주세요"). → **small = 온폰 한국어 실용 최소선**(그 아래는 꼬임=용량↔정확도 실제 바닥). turbo(574MB)=천장(불요).
  - ⚠️ 원규씨 초기 "tiny로 충분(소리나는 대로)" → 실측 후 정정: **whisper는 음소 받아쓰기가 아니라 LM 기반 "해석/보정"이 작동원리(끌 수 없음)**. 약한 모델=틀린 보정(지어냄), 강한 모델=맞는 보정(실제 한 말 복원). "보정 없는 순수 발음"은 음소인식기인데 "ㄱㅜㄴㅊㅓㅇ"식이라 비실용 → 아무도 안 씀. ∴ "부정확 허용"이어도 tiny의 *지어내기*는 블랙박스로 무용 → small 필요.
  - 용량 우려(원규씨 "너무 크지 않나"): **프로덕션=첫 실행 1회 다운로드라 APK 자체엔 미포함**(설치본 경량 유지). 190MB는 폰에 1회 받는 파일. 모델은 `filesDir`의 `ggml-*.bin` 자동탐색(가장 큰 것) → 스왑=파일만 교체(코드 0).
  - 잠정인 이유: small 확정은 원규씨 최종 OK 대기(190MB 수용 여부). 거부 시 대안 = base(꼬임 감수) or 구글 on-device recognizer(가벼움·단 PII 클라우드 폴백 + 역시 해석).
- **[확정·2026-06-11 구현·검증] ① 온폰 보정 스택 (small에 얹음, 전부 온폰·$0)**: Fable 자문 채택. jni.c에 ⓐ `suppress_nst=true`(`[뚜껑 닫는 중]` 비음성 헛태그 제거) ⓑ **VAD(Silero v5.1.2, 885KB) — 무음을 whisper에 먹이기 전 차단** ⓒ 신뢰 게이팅용 accessor(`no_speech_prob`·avg token p) 추가 후 재빌드. Kotlin=`transcribeWithMeta`(신뢰지표 동반)·`WhisperTranscriber` 게이팅(신뢰 낮으면 "(전사 불명확)" 마커). VAD 모델도 filesDir 자동탐색(`ggml-silero-*`, whisper 모델 후보서 제외).
  - **★ 실측 핵심 교훈**: **whisper는 무음에서 "확신에 차서" 지어냄** — 완전 무음 PTT가 "MBC 뉴스 김지경입니다"를 nsp=0.00·avgP=**0.90**(진짜 발화 0.74보다 높음)로 출력 → **no_speech_prob·avgP 신뢰 게이팅으로는 절대 못 잡음**. **VAD가 유일한 차단책**(무음→0 세그먼트→빈 텍스트→메시지 미게시). 실측 검증: 무음 PTT→`raw=''`→메시지 0, 발화→정상 전사. (⚠️ 클코 1차 오판=원규씨가 *역사책 낭독*한 걸 "환각"으로 단정 → 정정. 진짜 무음 환각은 그 다음 실측으로 확증.)
  - suppress_nst·게이팅은 보조, **VAD가 주력**. 비프 트림·자모 가제티어 스냅·숫자 정규화는 미적용(후속, 양평 지명·기사명 사전 확보 후).
- **[확정·2026-06-11 측정] ② sherpa-onnx 한국어 zipformer 평가 = 더 작으나 덜 충실 → whisper-small 유지**:
  - 측정: `sherpa-onnx-zipformer-korean-2024-06-24`(offline, KsponSpeech 학습) int8 다운로드(encoder 70.8+decoder 2.8+joiner 2.6 = **≈76MB**, whisper-small 190MB의 40%·Fable 추정 126MB보다 작음). PC `sherpa_onnx` 1.13.2(cp314 휠 존재)로 6/7 미용 샘플 10건 전사(greedy+modified_beam_search) → whisper-small(`result_small.txt`)과 비교. 스크립트=`onphone_stt_probe/sherpa_eval.py`, 출력 `sherpa_out{,_beam}.txt`.
  - **결과: 용량 win(76MB), 정확도 lose** — sherpa가 이름·고유명사 더 뭉갬("김미영→뒷미엉", "두피 클리닉→두께 클린이기")·문장 통째 누락(whisper는 받음). beam이 일부 교정(2명·뿌리염색)했으나 격차 잔존. 원규씨 최우선=충실도라 whisper-small 우위.
  - **미측정 = sherpa hotwords(지명·기사명 가중)** — sherpa의 핵심 강점인데 미용 샘플엔 무관 + 실제 대리 PTT 녹음 파일 없음(즉시삭제). 대리 샘플(test07)은 둘 다 "어디까지→오천 명" 빗나감, 단 sherpa가 "대리운전·양평역·그랜저"는 잡음.
  - **결정: whisper-small 유지**(190MB는 첫실행 다운로드라 APK 무영향으로 흡수). **재평가 트리거 = 76MB가 절실하거나, 현장 양평 대리 PTT 녹음 확보 시 sherpa+hotwords로 격차 좁혀지는지.** 그 전엔 whisper-small.
- **무음+발신자 = 새 `type="ptt"`**(9-B `type="system"`=중앙회색·발신자없음과 구분): 렌더는 기존 일반 말풍선이 그대로 처리(Step 3 추가코드 ~0), 필터(`!="system"`) 통과해 항상 보임, rules(text+type!=system+role!=SYSTEM) 통과해 클라 write 허용, `onChatMessageCreated`가 `chatType=msg.type` 전파(functions 무변경), 3앱 FCM 무음 분기에 `||=="ptt"` 한 줄.
- **구현(call_manager)**: jniLibs `com/whispercpp/whisper`(vendored LibWhisper.kt=항상 generic `whisper` 로드 패치·WhisperCpuConfig·decodeWaveFile) + `PttTranscriber`/`WhisperTranscriber`(싱글톤·graceful) + PTTManager(`sttSpikeEnabled=true`·stopSttCapture 전사콜백·**WAV 즉시삭제**) + ChatRepository.sendPttText + ViewModel + MainActivity onPttTranscript. driver/pickup=FCM 무음 1줄.
- **.so 재빌드 사실(누락·오염 검토로 잡음)**: ① jni.c `params.language="en"→"ko"` 후 android lib CMakeLists(`examples/whisper.android/lib`, WHISPER_LIB_DIR=7단계위 whisper.cpp루트)를 NDK 25.1·arm64-v8a로 재빌드(빌드된 build-android .so는 JNI 없음=순수 whisper) ② **`GGML_OPENMP=OFF` 필수** — ON이면 libggml-cpu.so가 `libomp.so` DT_NEEDED→런타임 UnsatisfiedLinkError(1차 실패 원인) ③ generic `libwhisper.so`만 포함→LibWhisper.kt를 v8fp16 변종 분기 제거·항상 `whisper` 로드(미포함 변종 크래시 회피) ④ strip 후 4 .so ~2.5MB.
- **E2E 검증(office 1004, S21+→ZFlip4)**: 캡처 ret=0 → tiny 로드(4스레드) → 전사(거침: 군청→"군총") → sendPttText 발송성공 → 수신 ZFlip4 `chatType=ptt` **무음 INSERT**(playChatSound 0). 전 경로 작동. 정확도는 tiny라 거칠지만 채택(위 근거).
- 빌드자산: `onphone_stt_probe/whisper.cpp/build-jni-ko/`(재빌드 .so), 모델 `onphone_stt_probe/models_dl/ggml-tiny-q5_1.bin`, 폰 `filesDir`에 adb push(프로덕션=첫실행 다운로드는 후속).
- 플랜: `~/.claude/plans/idempotent-launching-pony.md`(누락·오염 교정판). **미푸시(로컬 커밋 대기).**
- **⛳ 남은(후속·별 커밋)**: ~~pickup 캡처 재이식~~ **✅완료(2026-06-12, 아래 항목)** · 프로덕션 모델 다운로드(첫 실행) · 🎙 전사 표식(선택) · WAV 헤더가 비표준이면 decodeWaveFile 보정(현 작동=표준 가정 통과).

### [확정·2026-06-12 실측·미커밋] PTT→텍스트 pickup 포팅 완료 + 전사 정확도 = whisper small 엔진 공통 한계(지명 약점)
- **pickup 포팅 완료**(call_manager → pickup_driver_app, surgical): ① whisper JNI(LibWhisper·WhisperCpuConfig)+PttTranscriber+jniLibs 4 .so 복사 ② PTTManager STT 조각(sttSpikeEnabled·startSttCapture@fireReady·stopSttCapture@stopTransmit·onPttTranscript) ③ ChatRepository.sendPttText(type="ptt") ④ MainActivity onPttTranscript 후크+sendPttTextViaRepo(ROLE_PICKUP_DRIVER, senderName "픽업기사") ⑤ build.gradle abiFilters 'arm64-v8a'+jniLibs.useLegacyPackaging. + **세로고정**(manifest portrait — pickup엔 원래 screenOrientation 없었음, call_manager는 있음). 빌드·S22(R5CT41TJZFP) install·small+VAD 모델 안착. 수신측 무음(`chatType=="ptt"`)·Room type 컬럼·렌더 = 9-B에서 이미 완비(2차 누락검토 통과).
- **★ 전사 정확도 = 엔진 공통 한계(pickup 무죄)**: small 정상 로드(logcat `ggml-small-q5_1.bin (181MB)`)인데도 "양평역"→**"양평력/양통력"**. **call_manager(S21+ small)도 동일** — 21:01 발화 raw `수연이 양평력으로 가`. 즉 두 폰 동일 small 엔진이라 동일하게 깸(포팅 정상). **일반 문장은 잘 잡고(예 "형님 그 수연이 도착하면 시장으로 가주세요") 지명·고유명사만 핀포인트로 깸**(양평역·포천("포청")·분청 등). = whisper 한국어 지명 약점(결정대장 기존 기록과 일치).
- **→ [확정·2026-06-15 구현·실폰검증] 지명 가제티어(사전) 후처리 1차 = 지명만, 자모 거리 + 헷갈리면 보존**:
  - **구조**: `GazetteerCorrector`(util, 두 앱 복사본) — `ChatRepository.sendPttText`의 `scope.launch{}` 안에서 `cleanText` 교정 → Room/Firestore 둘 다 반영. 사전 = `settings/gazetteer.places`(1회 캐시 로드). `LOW_CONF_MARKER`("(전사 불명확)") public 노출해 corrector가 스킵. 시드 = `functions/scripts/seed-gazetteer.js`(양평 1읍11면+역 22개, 1004 사무실 시드 완료).
  - **알고리즘**: 어절 **접두 부분매칭**(조사 보존 — "양평력으로"→"양평역으로") + **한글 자모(초·중·종성) 단위 Levenshtein**(음절론 "양평력"이 양평읍·양평역 동률이라 모호, 자모로 양평역 d1<양평읍 d3로 풀림) + **"헷갈리면 보존"**(최단 d≤`MAX_JAMO_DIST`=1 AND 차순위 d>`AMBIG_RADIUS`=2일 때만 교정). 임계 [잠정]·실측 보정.
  - **실폰 검증(S21+·S22, 2026-06-15)**: `[gazetteer] '양평력으로'→'양평역으로' d=1 second=3` 교정 성공 / 정상어("원기"·"오는 길"·"군청") 전부 보존 / **오교정 0** / 픽업도 `[loadPlaces] 22개 로드`(rules 완화 정상). PC 로직검증: "양통역"(양동역 d1·양평역 d2 충돌)→보존(오교정 회피), 옥천명→옥천면, LOW_CONF 스킵.
  - **★ 핵심 한계(실측)**: whisper가 같은 "양평역"을 들쭉날쭉 전사 — 1자모 오류(양평**력**)면 교정되나 2자모(양**통**)면 보존(미교정). 가제티어는 "조금 틀림"만 고침. 심한 오전사·"양통"류는 **whisper 품질 영역**(엔진/프롬프트, 별 트랙). 오교정(틀린 지명 박제)은 "헷갈리면 보존"으로 차단 = 안전 우선.
  - **rules 변경**: `firestore.rules` `match /settings/gazetteer { allow read: if isAuthenticated() }`(픽업기사 read용, write는 admin). 배포 완료(원규씨 승인). 다른 settings는 그대로 admin 전용.
  - **범위 밖(후속)**: 기사명 교정(designated_drivers 권한·동기화 별도), 콜 출발/목적지 자동축적, "군청" 등 비행정지명 시드(사장 운영 추가), whisper initial_prompt. sherpa-onnx+hotwords 재평가도 후속(엔진 교체라 측정 후).
- **도구 학습**: 클코 Bash의 `adb push`가 깨진 원인 = **Git Bash MSYS 경로 자동변환**(`/data/local/tmp`→`C:/Program Files/Git/data/local/tmp`로 둔갑, "1 file pushed" 가짜 성공). **`MSYS_NO_PATHCONV=1` 접두로 복구** → 클코 도구로 push 가능(이전엔 됐던 게 이 변환 때문에 이 세션 깨졌던 것). adb shell/install/run-as는 영향 없음(파일 인자 없어서).
- **미커밋**: `feature/reservation-engine-poc` 로컬(코드 A~E + 세로고정). E2E 발화까지 실연됨(전사 체인 작동, 정확도만 지명 보정 대기).

## 알림 정리 (소리·표기 트랙)

### [확정·2026-06-13 push `4f625eed`/`10982fce`/`7f346af4`] 3앱 알림음 정리 + 콜매니저 표기 축소 + 픽업 콜표기 12h 롤링
- **계기(원규씨)**: 콜매니저 알림이 너무 많고 모든 알림음이 동일(=ptt 효과음이 채팅·콜에까지 새서 무전처럼 들림).
- **알림음 [확정]**: `R.raw.ptt_start`(ptt 효과음)는 **PTT 본체(PTTManager.playCue) 전용**. 채팅(playChatSound·채널)·콜·상태 알림은 전부 **기본 알림음**(`RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)`). 공유콜만 알람음 유지. 사운드 바꾸는 채널은 **ID 버전업 + 구채널 deleteNotificationChannel**(안드로이드는 채널 생성 시 사운드 1회 고정 → ID 안 바꾸면 기존 폰 미반영). dumpsys 실측 확인. 3앱(call_manager·pickup·driver). driver는 이미 기본음이라 죽은 `chat_messages_ptt` 채널만 삭제.
  - "모든 알림음 동일" 원인 규명: 구 채널들이 빌드마다 재배정되는 **리소스ID(2131…)를 사운드 URI로 박제** → 한 리소스로 collapse. 이번엔 안정적 시스템 URI(`content://settings/system/...`)로 전환해 재발 차단.
- **콜매니저 표기 축소 [확정·원규씨 결정]**: **새 콜(NEW_CALL)=데이터만(무음)** / 기사 상태변경=`ONLINE`(출근)·`OFFLINE`(퇴근)만 알림(대기중·배차·운행중·정산대기 데이터만) / 운행상태=운행시작·운행완료만(기사수락·정산대기 데이터만) / **콜취소·기사승인·신규회원·전달실패·업무마감제출=기본음 알림 유지**(처음 전부 무음화 후 원규씨가 이 5종 복원 지시). DB저장·브로드캐스트는 유지, `showNotification`만 제거. (status 문자열은 functions·driver `DriverStatus.kt`로 교차확인: 출근=ONLINE/퇴근=OFFLINE/대기중복귀=WAITING.)
  - **새콜 무음화 안전성 [코드추론·미검증]**: NEW_CALL FCM 알림은 원래도 조건 `!isAppInForeground() && fromCallDetector!=true && fromCallManager!=true`일 때만 떴음 → **양평 정상 흐름(콜디텍터/콜매니저가 콜 생성)은 별도 풀스크린·대시보드가 처리**하므로 무음화 영향 적을 것. 단 코드 추론이라 실콜 1회로 "매니저가 새 콜 인지하는지" 확정 권장.
- **픽업 콜표기 = 콜매니저식 12h 롤링 윈도우 [확정]** (영업일 10시 경계 `fe8ef8d3` **폐기**→`7f346af4` 대체): 기사가 운행완료 미입력한 박제 콜이 활성상태(IN_PROGRESS·정산대기 등)로 무기한 표시되던 문제. call_manager `CallRepository.getCallsFlow`의 **생성 후 12시간 메모리 필터** 패턴 채택 — `combine(DAO Flow, 1분 ticker)`로 `now-12h` 필터 → 앱 켠 채여도 자동 갱신. DB복사본 주입검증(11h·now 표시 / 13h·25h 숨김). **10시 경계 폐기 사유**: 원규씨가 콜매니저 기존 패턴(롤링 12h)에 맞추라 지시. 표기 필터만(Firestore/Room 행 보존).
  - 범위 밖: 픽업 refreshData의 Firestore측 시간필터(`whereIn+whereGreaterThan`=복합색인 필요·런타임 위험) → 후속. 박제 콜 **원천 정리(기사 강제 마감)**는 정산 게이트 별 트랙.
- **✅ 억제 동작 런타임 검증 완료(2026-06-15, S21+ R3CR312MB1L)**: 사운드 채널(dumpsys)·12h 필터(DB)에 더해 **표기 억제 동작도 실 FCM으로 확정**. `functions/scripts/test-notification-suppress.js`(매니저 토큰에 type별 data-only FCM 직접 전송, Firestore 무변경)로 6케이스 logcat(`CallManager_FCM`) 실측 — NEW_CALL→`[handleNewCall] 알림 생략(데이터만)`(무음) / DRIVER_STATUS WAITING·IN_PROGRESS→`알림 생략(데이터만)`(무음) / DRIVER_STATUS ONLINE·OFFLINE→`showNotification 호출 시작`(기본음) / CALL_STATUS CANCELED→`취소 알림 표시`+showNotification(기본음). 전부 설계 기대대로. → "[코드추론·미검증]"의 코드추론 부분 닫힘.
- **⛳ 남은 미검증 = 실 파일럿폰 설치만**(개발 3단말 S21+·S22·ZFlip4만 설치). 억제 로직 자체는 검증 종료. 전부 클라전용·functions 무변경(영향 반경 작음).
- **클코 학습**: 픽업 빌드를 ZFlip4에만 깔고 "검증 완료"라 했으나 원규씨 실사용 단말은 S22 → 옛 ptt음 빌드 잔존으로 혼선 → `feedback_verify_install_on_user_device_2026-06-13` ([[feedback-verify-install-on-user-device-2026-06-13]]).

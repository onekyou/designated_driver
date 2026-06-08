# 통화예약 엔진 파일럿 — 폰 검증 + 단골/번호 구조 정정 (2026-06-09)

[[reservation_engine_pilot_deploy_2026-06-08]] 의 작동 게이트를 폰에서 통과시키고, 그 과정에서
"전화번호" 한 필드가 6/3 전제(미지발신자 다수)를 뒤집는 단서로 드러나 구조를 정정한 세션.

## 1) 작동 게이트 클리어
- **IAM**: `run.invoker` 는 compute SA가 프로젝트 레벨로 이미 보유. **`aiplatform.user` 만 없어 추가 부여**(원규씨 gcloud 세션). → 함수가 Cloud Run(STT)+Gemini 호출 가능.
- **에러 "녹음 업로드 실패"**: 원인 = **storage.rules 미배포**. 6/8엔 functions·Cloud Run만 배포, storage 규칙 누락. `reservation_test/{uid}` 쓰기 규칙은 로컬에 있었으나 production 반영 안 됨 → `firebase deploy --only storage` 로 해결. (서버 로그 0건 = 요청이 callable 이전 단계에서 막힘을 보고 진단.)

## 2) W경로 end-to-end 실작동 + 속도 실측
- 두 건 모두 성공: `POST /transcribe 200 OK`, STT `p=1.00`(81·109자), Gemini 파싱 정상.
- **시간**(faster-whisper large-v3-turbo, Cloud Run **CPU 2코어·GPU 없음**, beam_size=5, compute_type=int8):
  - 콜드(서버 꺼짐): 14초 녹음 → ~50초 (모델로딩 31초 + 전사 18초)
  - 웜: 18초 녹음 → ~30초 (콜드 0, **순수 전사가 오디오의 ~1.6배** = CPU 병목)
  - **병목은 콜드스타트가 아니라 CPU 전사**. production GPU면 18초 녹음 → 전사 1~3초(10~20배), min-instances=1로 콜드 0 → **end-to-end ~5초**. 게다가 백그라운드라 사장 체감 0.
  - → 속도는 production 최적화 문제, 검증 결론에 영향 없음.

## 3) ★ 전화번호 — 6/3 전제가 뒤집힌 지점
- 폰 실연: 써머리에 **출발·목적지·시각·업종 다 정확, 전화번호만 빔**.
- 코드는 정상: 번호 = `callerPhone(엔진) ?: parsed?.phone(파일명) ?: ""`. 통화 중 번호 미언급(정상) + 파일명 추출 실패가 겹침.
- 폰 실제 파일명 확인 → **저장연락처는 '이름', 미지발신자만 'raw 번호'**:
  - `통화 녹음 양원규_..._m4a`(저장) → 번호 없음 / `통화 녹음 01081254055_..._m4a`(미지) → 010-8125-4055 추출됨.
- **★ 원규씨 정정**: "대부분 단골(=저장된 이들)이 콜을 부른다." → 6/3 "미지발신자 다수" 전제 **틀림**. 다수 콜에서 파일명은 이름만 줌 → 번호는 다른 경로 필요.

## 4) READ_CONTACTS 폐기 — Play 심사 사실확인 (추정 금지)
- 처음엔 "권한 하나면 단골 행동0 산다, 싼 거래"로 기울었으나 **사실확인 후 폐기**.
- **Play 2026.4 신설 Contact Permissions 정책**: 선언폼+승인 필요(2026.10.28 강제). **Android Contact Picker(매번 사용자 탭)가 기본 대안**, 그게 기술적으로 불충분함을 입증해야 READ_CONTACTS 부여. "거래상대 고르기"는 명시 거절 사례. **자동 백그라운드 이름→번호 조회(우리 용도)는 가장 거절나기 쉬움**.
- **아이러니**: 우리의 행동0(자동·무탭)이 곧 승인의 적. picker 받으면 행동0 상실, 행동0 지키면 거절. 둘 다 손해 → 6/3 "권한0" 본능이 (다른 이유로) 옳았음.
- 출처: [Restricted Permissions/minimum scope](https://support.google.com/googleplay/android-developer/answer/16935362?hl=en) · [Sensitive Info permissions](https://support.google.com/googleplay/android-developer/answer/16558241?hl=en) · [Declare permissions](https://support.google.com/googleplay/android-developer/answer/9214102?hl=en)

## 5) 대체 = 업소 고객DB(귀속) 이름매칭 + 첫통화 보정 시드 → [[_decisions]] 박음
- 번호 출처 = 폰 연락처 아니라 **업소 자기 기록**(customers/customerInfo). 첫 통화 1회 사장 보정(시드) → 이후 단골 콜 자동. 귀속 해자 강화. 구현은 GO 후.

## 다음
- 🚩 **양평 매니저폰 실손님 통화 자동녹음**으로 거친 샘플 검증(6/7~9는 자가녹음 천장편향 = 진짜 GO바 아님). **← 최우선(field).**
- **온폰STT 검증 = 6/9 같은 날 실행 완료** → [[reservation_engine_onphone_probe_2026-06-09]]. ⚠️ **이 줄을 "온폰을 지금 더 해야 한다"로 재해석 금지** — 이미 측정했고 결론이 "온폰=실제 골격/종착"에서 **"온폰 ≠ 보편 답"으로 정밀화**됨:
  - 결과: 실현성✅ / 온폰 turbo=서버정확도(런타임 등가)지만 RTF 4.1× / small 빠르나(1.24×) 핵심필드 파괴.
  - **★ 전략 정밀화(원규씨 지적)**: 온폰 속도=기기종속(GPU/NPU 가속 칩마다 별개 백엔드·사장폰 제각각+저사양) → **"내 폰 빠르게"는 보편 답 아님.** **보편 STT 바닥 = 클라우드**(faster-whisper 지금/카카오 보류). **온폰 = 미래(NPU 표준화) or 하이브리드(능력폰=온폰, 약한폰=클라우드 폴백), 보편 베이스라인 아님.** 기기별 속도튜닝 ⏸중단.
- 파일럿은 현 상태로 검증 계속(번호는 사장 [확인]에서 보정). 자동번호(업소DB)·속도(GPU)는 GO 후 트랙.

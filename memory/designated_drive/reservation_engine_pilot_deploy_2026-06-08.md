# 통화예약 엔진 → 콜매니저(대리) 첫 실전 파일럿 — 코드+배포 완료 (2026-06-08)

[[reservation_engine_measurement_2026-06-07]] 의 다음 단계(6/8 재조정 "콜매니저에 엔진 붙이기")를
플랜모드로 설계→구현→배포까지 완료. 플랜: `C:\Users\kala1\.claude\plans\jolly-meandering-firefly.md`.
브랜치 `feature/reservation-engine-poc`, push 완료(`39c056b0`).

## 무엇을 만들었나 (W경로 + 서버STT, 수동 트리거)
- **서버 STT = faster-whisper (검증 자산)** — Cloud Run 서비스 `transcribe_service/`(main.py·Dockerfile·requirements).
  검증된 `functions/scripts/transcribe-whisper.py` 로직 재사용(language=ko·vad·beam5, 모델 이미지 번들).
  ★ Google STT 안 씀 — .m4a 미지원·한국어 미검증. 통념 회귀였음([[feedback-convention-over-verified-2026-06-08]]).
- `functions/src/handlers/stt.ts` = `transcribeAudio(gcsUri)` Cloud Run 인증호출 = **STT 유일 교체지점**.
- `functions/src/handlers/reservation.ts` = `mode:"W"` 분기(gcsUri→서버전사→Gemini). 레거시 측정동작 보존.
- 콜매니저: `reservation/data/ReservationToCall.kt`(엔진출력→CallInfo 매퍼, ⚠️trip_summary/isSummaryConfirmed 안 건드림=정산 오염방지) + `reservation/ui/ReservationConfirmDialog.kt`(써머리확인, [확인]=콜생성) + `ReservationInboxScreen/ViewModel`(녹음목록→분석중→확인→createCall) + 설정 진입 "통화로 예약 입력" + ReservationParseClient `mode` 파라미터.

## 배포 상태 (production, calldetector-5d61e)
- ✅ Cloud Run: **`https://transcribe-service-60275310305.asia-northeast3.run.app`** (--no-allow-unauthenticated, 4Gi/2cpu)
- ✅ `functions/.env` = `TRANSCRIBE_SERVICE_URL=...`(gitignore 처리, 머신로컬). functions 빌드+`parseReservation` 배포 완료.
- ✅ 콜매니저 APK 빌드 + S21+(R3CR312MB1L) install 완료.

## ⛳ 미완 — 다음 세션 픽업 (작동 게이트)
1. **🚩 원규씨 IAM 2개** (클코는 하네스 가드레일로 IAM 자가확대 불가 — 사용자 직접):
   - `gcloud run services add-iam-policy-binding transcribe-service --region asia-northeast3 --member=serviceAccount:60275310305-compute@developer.gserviceaccount.com --role=roles/run.invoker --project calldetector-5d61e`
   - `gcloud projects add-iam-policy-binding calldetector-5d61e --member=serviceAccount:60275310305-compute@developer.gserviceaccount.com --role=roles/aiplatform.user` (측정 때 했으면 skip)
   - **이게 빠지면** 함수가 Cloud Run/Gemini 호출 실패 → 분석 에러.
2. **폰 검증**(S21+): 설정→"통화로 예약 입력"→최근통화 탭→"분석중"→써머리확인→[확인]→대시보드 콜 생성. `rawTranscript`(STT 받아쓴 글)·파싱정확도·헛예약0 보고 **GO바** 판단.
3. **선결 확인**(코드로 못 풂): 양평 office 매니저폰에 실손님 통화 자동녹음이 실제로 쌓이는지(6/7 10건은 본인 자가녹음=천장편향). 안 켜졌으면 자동녹음 ON 안내 선행.

## 알려진 리스크/메모
- PII: 실손님 오디오가 Google Cloud(STT+Gemini)로 외부전송·즉시삭제. 수동이라 고른 통화만(덜 위험). 이식 전 정식 Data Safety는 별 트랙.
- STT 무게: faster-whisper Cloud Run 콜드스타트/이미지크기. 파일럿은 min-instances=0+백그라운드로 흡수. 진짜 문제면 `stt.ts`만 교체(검증 baseline 유지 우선).
- 첫 증분 = 수동 트리거. 자동(통화종료→배너, CallReceiver 토대 있음)은 다음 증분. 택시·미용 표면도 다음(엔진 하나·표면 셋).
- 종착 = GO 후 coupon_app(로컬마루) 앱 모듈 이식. 콜매니저는 검증장.

## 클코 학습(이번 세션)
- [[feedback-overask-confirm-2026-06-08]] 이미 정해진/자명한 걸 confirm으로 되묻지 말 것.
- [[feedback-convention-over-verified-2026-06-08]] 검증 자산 두고 통념으로 드리프트 말 것(구글STT 헛발).

# PTT 운영 시나리오 + 채팅 블랙박스 (8+1단계)

**확정**: 2026-05-25 (본인 자다 일어나 타진)
**관련 plan**: `C:\Users\kala1\.claude\plans\harmonic-sparking-hedgehog.md`
**관련 메모**: [[ptt_volume_button_principle]], [[ptt_market_replacement_card]], [[ptt_pivot_other_tracks_2026-05-25]], [[principle_user_action_minimum]]

## 결정: 쿠폰앱과 병렬 진행 (5/19 휴면 부분 해제)

콜마당 PTT 트랙 + 쿠폰앱 트랙 *동시*. 영업 카드 ([[ptt_market_replacement_card]])는 두 트랙 공통 무기.

## PTT 운영 시나리오 8단계 (본인 발화)

1. 전화 옴 → 콜디텍터 캐치 → **자동 배차** (기존 코드 활용)
2. 매니저 → PTT 지시 ("○○ 기사, 어디로, 누구 태우라")
3. 기사 → 콜 정보로 손님 통화
4. 기사 → 손님 만남 → 출발지·목적지·요금 *기재 + 전송*
5. 매니저 → 확인 → 이상 시 PTT *수정 지시* (**기본 정상 가정** — 문제 신호만 본다)
6. 운행 중 → 기사 PTT 송신 *중단* (안전), 문제 시 *전화 통화*
7. PTT 권한: **매니저 + 픽업기사**만 송수신. 일반 기사는 *수신 전용*
8. **Data Push로 알림 통합** — 현재 콜매니저 알림 폭주 (NEW_CALL/DRIVER_STATUS_UPDATE/CALL_STATUS_UPDATE/STATUS_CHANGE/NEW_SHARED_CALL/DRIVER_APPROVAL_REQUEST 6종+) 해소

> **음성 전달 모델 (2026-06-01 명시)**: PTT 음성도 *상시 채널 join 아님*. 발화 시 high-priority FCM Data Push로 수신측 wake → Agora **Trigger Join** (발화 동안만 접속, Mute + 30s leave). 무전기 동등성(이질감 0)은 wake→fast-join 핸드셰이크 신뢰성으로 확보. always-join은 listener 최소화 철학 위반이라 배제. 상세 [[ptt_volume_button_principle]] §"기술적 본질 + 해결 방향".

## 9단계: 채팅 블랙박스 (PTT 발화 + 시스템 이벤트 자동 게시)

본 단계는 *Data Push 라우터 위*에 놓임 (시나리오 8번과 같은 그릇, 별도 listener X). 본인 정책 정합 — 콜마당은 listener 최소화 (driver_app carryOverListener 1개만, 그 외 1회 fetch + Data Push).

**작동 메커니즘 (Data Push 패러다임)**:
```
이벤트 발생 → Firestore chat_messages.add()
  ↓ Cloud Function 트리거
  ↓ FCM Data Push (priority=high)
클라이언트 onMessageReceived() → Room INSERT (listener 0)
```

### 9-A. PTT 발화 → 채팅 자동 게시

- 매니저/픽업 PTT 발화 → Android `SpeechRecognizer` (**무료**) → 텍스트 변환 → `chat_messages.add({type: "ptt", senderId, text, audioUrl})` → 음성 파일 Firebase Storage 첨부
- 본인 발화: "거의 블랙박스 느낌이야"

### 9-B. 시스템 이벤트 → 채팅 자동 게시 (확장, 2026-05-25 본인 발화)

콜마당이 이미 모든 상태 변경을 Firestore에 기록 + Cloud Functions 트리거 (`onCallStatusChanged` 등) 운영 중. 각 트리거에 `chat_messages.add({type: "system", ...})` 한 줄 추가.

대상 이벤트:
- 콜 상태 전이 (WAITING→ASSIGNED→ACCEPTED→IN_PROGRESS→AWAITING_SETTLEMENT→COMPLETED)
- 콜 취소 (CANCELED / CANCELLED_BY_DRIVER / CANCELLED_BY_CUSTOMER)
- 예약 (RESERVED) / 공유 (SHARED_WAITING / CLAIMED)
- 기사 상태 (ONLINE/PREPARING/ON_TRIP/WAITING/OFFLINE)
- 정산 입력 / 일일 정산

채팅창 예시:
```
[시스템] 10:23 김기사 출근
[시스템] 10:45 신규 콜 #C-1234 (가락동 → 잠실)
[매니저] 김기사 가  [▶ 음성]
[시스템] 10:46 김기사 배차됨
[시스템] 10:48 김기사 수락
[시스템] 11:05 운행 시작 (가락동 → 잠실, 2만원)
[김기사] 5분 후 도착  [▶ 음성]
[시스템] 11:25 운행 완료
[시스템] 11:26 김기사 복귀
```

### 블랙박스 용도 (양방향 인지 보험)

- ① **매니저 사후 감사·시시비비** — "○○동 가라고 했었나?" 자동 기록. 분쟁 해결 인프라
- ② **기사 본인이 놓친 정보 확인** — 운전 중 수신만(7번 단계), 집중·잡음·이어폰 끊김으로 못 들었을 때 차 세우고 채팅 거슬러 본다. 정보 누락 0

매니저·기사 *모두 평소 채팅 안 봄*. PTT 음성·시스템 이벤트 즉시 인지. 놓쳤을 때만·문제 시에만 본다 → 양방향 *행동 최소 원칙* 정합 ([[principle_user_action_minimum]]).

시나리오 7번(기사 수신 전용)의 *안전망*이 9번(채팅 블랙박스). 두 단계 상호 보강.

### UI 분리 (가독성)

시스템 이벤트 양이 PTT 발화보다 많음 (사무실당 일 ~260개). 가독성 위해:
- `type: "system"` UI 시각 분리 (회색·작은 글씨)
- 필터 토글 ("음성만 보기" / "전체 보기")
- 시스템 메시지 자동 collapse 옵션

### 비용 0

- SpeechRecognizer 무료 (Google 온디바이스+클라우드 혼합)
- 음성 파일 45MB/월/사무실 (Firebase Storage 5GB 무료 한도 1%)
- 시스템 이벤트 = 텍스트만 → Storage 0
- Firestore writes 추가 ~7,800/월/사무실 (양평 무료 한도 안)
- Cloud STT($7/월) 폐기

### 외부 PTT 앱이 못 하는 자리

같은 그릇(`chat_messages` 컬렉션 + Data Push 라우터)에 음성+텍스트+시스템 이벤트+블랙박스 통합. Zello 같은 외부 PTT 앱은 채팅 별도·PTT 별도. 콜마당 자체 인프라라서만 가능.

## 시나리오 핵심 의미

- 매니저 *콜 카드 작성 완전 폐기* (콜디텍터 자동 + 기사 자율)
- 매니저 = 음성 지시 + 검토 + 수정만
- 운행 중 안전 (PTT 일방향)
- 권한 분리 (혼선 방지)
- 알림 통합 (현재 폭주 해소)
- 블랙박스 보존 (분쟁 + 이력)

## 매니저 UI 대체 매트릭스

| 자리 | 현재 매니저 행동 | PTT 후 |
|------|---------------|--------|
| 신규 콜 입력 | 5필드 | PTT 발화 → 빈 콜 카드 자동 생성 + 음성 첨부 |
| 배차 | 1탭 | PTT "○○기사 가" |
| 취소 | 1탭 | PTT "취소" |
| 직접운행 | 9필드 | PTT 발화 + 결제 1탭 |
| 운행중 모니터링 | 화면 조회 | PTT 채널 자동 청취 |
| 정산 확정/이체/채팅/로그인 | 탭 유지 | PTT 부적합, 그대로 |

수기 입력 **14필드 → 0필드** (100% 폐기). 매니저 회귀 본질 원인 해소.

## 약한 자리 (정밀화 시 짚을)

- "매니저 확인 → 이상 시 PTT 수정" (5번 단계)이 *매니저 인지 부담을 재발*시킬 수 있음
- 해결: **기본 정상 가정**. 기사 입력 = 신뢰. 매니저는 *문제 신호*만 본다 (요금 비정상 / 출발지 GPS 불일치 / 미입력 N분 경과 → Data Push 알림으로만)
- 매니저가 *콜마다 검토*하지 않음. *예외 케이스만*

## 영업 연결 (1/2/3단 단계화)

본 운영 시나리오는 영업 *2단 (제품 본질)*과 *3단 (OS 정체성)*의 코어. 1단 (무전기 비용 대체) 진입 후 *시나리오 8+1 자체*가 영업 멘트의 본질. 단계화 상세 → [[ptt_market_replacement_card]] §"영업 멘트 단계화 (3단)"

본인 통찰 (2026-05-25): "결국 콜마당이 아닌 무전기를 파는 느낌도 있다" → 영업 카드 ≠ 제품 정체성 분리. 본 시나리오가 *제품 정체성*. 영업 카드는 *진입 5분 멘트*만.

## 코드 통합 자리 (요약)

상세는 plan 파일 §Critical Files 참조:
- 매니저 액션: `call_manager/.../DashboardViewModel.kt` (createEmptyCallAndShowAssignment / assignCallToDriver / cancelCall / completeAsManager)
- 기사 FCM: `driver_app/.../MyFirebaseMessagingService.kt` (신규 type "ptt_dispatch" 분기 추가)
- 잠금화면: `driver_app/.../LockScreenActivity.kt` (볼륨버튼 hook 통합)
- 콜디텍터 자동 배차: `call_detector/.../DispatchActivity.kt`
- 정산 단순화: `call_manager/.../SettlementViewModel.kt` ([[ptt_pivot_other_tracks_2026-05-25]])

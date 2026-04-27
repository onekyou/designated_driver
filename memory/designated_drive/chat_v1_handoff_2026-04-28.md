# 사무실 단톡방 채팅 V1 — Step 5 UI 진입 직전 인계 (2026-04-28)

> 다음 세션 첫 응답 전에 본 문서 + PLAN + SPEC 정독 권장.
> "시작해줘" 트리거로 들어온 클코가 어디서부터 이어갈지 즉시 파악할 수 있게 작성.

## 완료 상태

### Commit 7290ab17 (origin/manager-direct-drive)
`feat(chat): 사무실 단톡방 V1 — CF + 보안규칙 + 데이터 레이어`

완료된 단계:
- ✅ Phase A: 메모리 저장 (PLAN + 양평 단톡방 데이터 + V2 후보)
- ✅ Step 1: `docs/chat-shared-spec.md` SSOT
- ✅ Step 2: `firestore.rules` chatRoom 매치 블록 + collectionGroup messages 인덱스
- ✅ Step 3: `functions/src/handlers/chat.ts` (onChatMessageCreated + 90일 cleanup + backfill + 동기화 트리거 4개)
- ✅ Step 4: `registerOwner`에 매니저 자동 멤버 등록 + 기사/admin 동기화 트리거
- ✅ Step 5 데이터 레이어: AppDatabase v5→v6 + LocalChatMessage + ChatMessageDao + ChatRepository + Application 주입

빌드 검증:
- TypeScript: `npx tsc --noEmit` 통과
- Firebase rules: `firebase deploy --only firestore:rules --dry-run` 통과
- Kotlin: `./gradlew :app:compileDebugKotlin` 통과

## 다음 작업 — Step 5 UI

### A. ChatViewModel (call_manager)
- 파일: `call_manager/app/src/main/java/com/designated/callmanager/ui/chat/ChatViewModel.kt` (신규)
- 의존성: `application.chatRepository` 사용
- StateFlow:
  - `messages: StateFlow<List<LocalChatMessage>>` (chatRepository.getMessagesFlow에서 stateIn)
  - `latestMessage: StateFlow<LocalChatMessage?>` (BottomSheet peek 미리보기용)
- 액션:
  - `sendMessage(text)` → chatRepository.sendMessage(...)
  - `retryMessage(message: LocalChatMessage)` → chatRepository.retryMessage(message)
- init 블록에서 chatRepository.loadInitialMessages(p, c, o) 1회 호출 (.get 50건 reconcile)
- SharedPreferences "login_prefs"에서 provinceId/cityId/officeId 로드 (DashboardViewModel.kt:107~326 패턴 참고)
- senderName/senderRole = admins/{uid} 문서에서 (또는 SharedPreferences 캐시)

### B. ChatScreen (Compose)
- 파일: `call_manager/app/src/main/java/com/designated/callmanager/ui/chat/ChatScreen.kt` (신규)
- 구조 (스펙 §12):
  - TopAppBar (← + "{사무실명} 단톡방")
  - LazyColumn(reverseLayout = true) — 메시지 리스트
  - 입력바 (TextField + 전송 버튼)
- 메시지 버블:
  - 본인 메시지: 우측 정렬, 파스텔 노란색 배경 (카톡 패턴)
  - 타인 메시지: 좌측 정렬, 흰색/회색 배경
  - 5분 그룹화: 같은 발신자 연속 메시지면 첫 메시지에만 이름+역할 표시
  - 마지막 메시지 옆 시간 (예: 오후 8:32)
- sendStatus 표시:
  - SENDING ✓회색
  - SENT ✓파랑
  - FAILED ✗빨강 + 탭하면 retry
- 발신자 이름 표시 형식: `박상준 (매니저)` / `조수현 (대리기사)` / `신청환 (픽업기사)`

### C. MainActivity BottomSheetScaffold 통합
- 파일: `call_manager/app/src/main/java/com/designated/callmanager/MainActivity.kt`
- Material 3 `BottomSheetScaffold` (또는 `Scaffold` + `ModalBottomSheet`)
- **3단계 sheet 상태** (스펙 §11):
  - peek (56dp): `💬 박상준: 통완` 카드 (마지막 메시지, 카운트 없음)
  - PartiallyExpanded (50%, 기본 펼침): 사용자가 탭 → 절반 펼쳐 채팅창 + 메인 동거
  - Expanded (95%): 사용자가 더 끌어올리면 거의 풀스크린
- **새 메시지 도착**: peek 56→80dp animateTo (살짝 부풀음) + 진동 1회. 알림음은 시스템 알림이 처리 (앱 백그라운드 시)
- sheetContent = ChatScreen
- 메인 콘텐츠는 기존 NavHost (Dashboard)

### D. MyFirebaseMessagingService 분기 (call_manager)
- 파일: `call_manager/app/src/main/java/com/designated/callmanager/service/MyFirebaseMessagingService.kt`
- 65~75행 `alwaysProcessTypes` set에 `"NEW_CHAT_MESSAGE"` 추가
- 128~184행 when 블록 마지막에 분기 추가:
  ```kotlin
  "NEW_CHAT_MESSAGE" -> handleChatMessage(remoteMessage)
  ```
- 신규 함수 `handleChatMessage`:
  - payload → `application.chatRepository.onRemoteMessageReceived(payload)`
  - **본인 senderId 메시지면 skip** (Repository에서 이미 처리)
  - **앱 백그라운드일 때만** showNotification (chat_messages 채널). 포그라운드는 BottomSheet 부풀음으로 처리

### E. NotificationChannel `chat_messages` 등록
- 위치: `MyFirebaseMessagingService.kt:413~` `createNotificationChannels()` 함수에 추가
  - 또는 신규 헬퍼 클래스로 분리해도 OK
- 사양 (스펙 §10, **콜 채널과 차별화 필수**):
  - id: `"chat_messages"`
  - importance: `IMPORTANCE_HIGH` (콜은 MAX, 채팅은 HIGH)
  - default 알림음: `RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)` (콜은 알람음)
  - 진동 ON
  - **setBypassDnd(false)** (콜은 true — 채팅은 DND 우회 X)
  - lockscreenVisibility: VISIBILITY_PUBLIC

### F. 로그아웃 시 fcmToken 삭제
- 파일: `call_manager/.../ui/login/LoginViewModel.kt` (현재 사전 WIP 있음 — 주의)
- 또는 별도 로그아웃 진입점
- 로그아웃 시점에:
  - `provinces/{p}/cities/{c}/offices/{o}/managerTokens/{adminUid}` 문서 삭제
  - 또는 `fcmToken` 필드만 FieldValue.delete()
- ⚠️ LoginViewModel.kt가 WIP 상태(다른 미완성 작업 포함)라 채팅 외 변경은 절대 건드리지 말 것. 토큰 삭제 단계 1개만 추가

## 핵심 주의사항

### 1. Compose BOM 호환
- call_manager BOM = 2024.04.01 → Material 3 BottomSheetScaffold 사용 가능
- driver_app BOM = 2023.10.01 → 같은 BottomSheet API 호환되는지 Step 6 시점에 검증

### 2. WIP 파일 건드리지 말 것
사용자 다른 작업이 unstaged 상태로 남아있는 파일들 — 채팅 통합 시 다음 파일들의 **기존 변경에 손대지 말 것**:
- `call_manager/.../service/CallDetectorService.kt`
- `call_manager/.../ui/dashboard/DashboardViewModel.kt` (이건 사용자 commit으로 들어감 — 추가 변경 시 신중)
- `call_manager/.../ui/login/LoginViewModel.kt`
- `call_manager/.../ui/signup/SignUpScreen.kt`, `SignUpViewModel.kt`
- `call_manager/.../util/CallManagerPermissionManager.kt`
- `customer_app_flutter/`, `head_manager_web/`, `homepage/`, `public/`
- `firebase.json`, `functions/package*.json`

→ 채팅 작업 중 이 파일들에 코드 추가 필요 시 사용자에게 먼저 보고. 자동 수정 금지.

### 3. 알림 차별화 디테일
- **콜 (NEW_CALL)**: IMPORTANCE_MAX + 알람음(TYPE_ALARM 또는 status_alert.raw) + DND 우회 + 진동 패턴 1초/0.5초 반복
- **채팅 (NEW_CHAT_MESSAGE)**: IMPORTANCE_HIGH + 일반 알림음(TYPE_NOTIFICATION) + DND 우회 X + 단일 진동
- 사용자가 채널별 시스템 설정에서 음소거 가능

### 4. 페이로드 키 일관성
chat-shared-spec.md §2.1 참조. CF에서 보낸 `type=NEW_CHAT_MESSAGE`, `messageId`, `senderId`, `senderName`, `senderRole`, `text`, `createdAt`, `clientCreatedAt`, `provinceId`, `cityId`, `officeId` 모두 string. 클라이언트에서 그대로 받아 chatRepository.onRemoteMessageReceived로 전달.

### 5. messageId Optimistic UI 충돌 방지
chatRepository.sendMessage가 이미 Firestore docId 사전 발급 사용. ViewModel/UI 단에서는 messageId 직접 생성 X.

## Commit 단위 권장

Step 5 UI를 한 commit으로 묶기보다 자연스러운 체크포인트로 분할 권장:

1. **commit 1**: 알림 채널 + MyFirebaseMessagingService NEW_CHAT_MESSAGE 분기 (E + D)
2. **commit 2**: ChatViewModel + ChatScreen 기본 (A + B 텍스트 메시지만)
3. **commit 3**: ChatScreen UI 디테일 (5분 그룹화, sendStatus UI, 카톡 패턴)
4. **commit 4**: MainActivity BottomSheetScaffold 통합 (C)
5. **commit 5**: 로그아웃 fcmToken 삭제 (F) + Step 5 마무리

각 commit 후 빌드 검증 (`./gradlew :app:compileDebugKotlin`) → 안전 상태 유지.

## 검증 시나리오 (Step 5 끝나면)

3 기기 (S21+ R3CR312MB1L, S22 R5CT41TJZFP, Z Flip4 R3CT80K78NP) 중 S21+ 만 우선 사용:
- backfillChatMembers 1회 호출 (firebase functions:shell)
- S21+에서 메시지 발송 → Firestore 콘솔에서 messages 도큐먼트 생성 확인
- BottomSheet peek → 탭 → 50% → 95% 전환 정상
- Optimistic UI ✓회색 → ✓파랑 전환 1초 내
- 비행기 모드 → ✗빨강 → 비행기 OFF + ✗ 탭 → ✓파랑 전환

driver_app/pickup_driver_app 검증은 Step 6/7 끝난 후.

## 참고 파일

- 플랜 본문: `memory/designated_drive/chat_feature_plan_2026-04-28.md` (rev3, 401줄)
- 공유 스펙 (3 앱 SSOT): `docs/chat-shared-spec.md` (215줄)
- 양평 실측 데이터: `memory/designated_drive/yangpyeong_chat_data_2026-04-28.md`
- V2 후보 (콜 카드 임베드): `memory/designated_drive/chat_v2_call_embed_idea_2026-04-28.md`
- 신규 사고 모드: `memory/feedback/feedback_listener_vs_fcm.md`

## 이미 작성된 코드 진입점

API:
```kotlin
// CallManagerApplication에서
application.chatRepository  // ChatRepository 인스턴스

// ChatRepository 사용
chatRepository.getMessagesFlow(provinceId, cityId, officeId)        // Flow<List<LocalChatMessage>>
chatRepository.getLatestMessageFlow(provinceId, cityId, officeId)   // Flow<LocalChatMessage?>
chatRepository.sendMessage(p, c, o, senderId, senderName, senderRole, text)
chatRepository.retryMessage(message: LocalChatMessage)
chatRepository.onRemoteMessageReceived(payload: Map<String, String>)  // FCM에서 호출
chatRepository.loadInitialMessages(p, c, o)                         // 첫 진입 시 .get() 1회
```

LocalChatMessage 필드:
```kotlin
id, provinceId, cityId, officeId,
senderId, senderName, senderRole,    // ROLE_MANAGER / ROLE_DESIGNATED_DRIVER / ROLE_PICKUP_DRIVER
text, createdAt, clientCreatedAt,
sendStatus  // SEND_STATUS_SENDING / SENT / FAILED
```

## 마지막 메모

데이터 레이어 + 백엔드 끝났고 UI만 남음. UI는 ChatRepository만 잘 호출하면 되니까 비교적 직관적. BottomSheet 통합이 가장 큰 구조적 변화 — 신중히.

Step 5 끝나면 Step 6 (driver_app), Step 7 (pickup_driver_app), Step 8-10 (통합 테스트) 순.
전체 진행률: ~30% (4/13 작업).

# 사무실 단톡방 채팅 V1 — Step 5 UI 완료 (2026-04-28 후반 갱신)

> 다음 세션 첫 응답 전에 본 문서 + PLAN + SPEC 정독 권장.
> "시작해줘" 트리거로 들어온 클코가 어디서부터 이어갈지 즉시 파악할 수 있게 작성.

## ⚠️ 2026-04-28 후반 세션 결과 (commit `b190a2bc` 후)

### 사용자 추가 보고 4종
1. **본문 multi-line 입력 시 글자 안 보임** — 사용자: "메시지 입력 시 위로 치고 올라가야 하는데 위가 막혀있어 아래로 작성됨"
2. **Peek 상태에서 시스템 nav bar 영역에 chat 침범** — 사용자: "비활성 상태에도 nav bar에 chat 보임"
3. **메시지 전송 시 새 메시지 안 보임** (시각적으로 입력창 뒤에 생성)
4. **Expanded 상태에서 swipe down으로 sheet 안 닫힘** — drag handle 영역만 작동, list 영역은 두 번 swipe 필요

### 7번 fix 시도 모두 실패 → 이전 commit `b190a2bc` 상태로 복원

| # | 시도 fix | 결과 |
|---|----------|------|
| 1 | ChatInputBar Row `verticalAlignment = Bottom` (CenterVertically→Bottom) | 무효 |
| 2 | ChatBottomSheetContent peek/expanded 완전 분리 (`if (!isExpanded) PeekColumn else ExpandedColumn`) | sheet 활성화 자체 안 됨 |
| 3 | 외곽 Column `fillMaxHeight()` (0.95f 제거) + Spacer weight | sheet 활성화 복원, 다른 증상 그대로 |
| 4 | 외곽 Column에 `imePadding()` 추가 + Row `imePadding` 제거 (사용자 직관 "본문 페이지가 메시지 입력란 위에 한정 안 됨") | 효과 없음 |
| 5 | `rememberLazyListState` + `LaunchedEffect(messages.size) animateScrollToItem(0)` | (전송 시 자동 스크롤은 의도 — 별도 fix로 보존 검토) |
| 6 | `NestedScrollConnection` 적용 (LazyColumn에) | **잘못된 사용** — `Offset(0f, available.y)` 반환이 swipe consume → sheet drag 차단 |
| 7 | `sheetDragHandle` 활성화 (default M3 drag handle) + `sheetContainerColor` Transparent 제거 | drag handle 영역만 sheet drag 작동, list 영역 미해결 |

### 측정 결과 (logcat onGloballyPositioned + onSizeChanged + sheet state)
- **layout 자체 정상 stack**: 키보드 ON 시 OuterColumn(0,0)/1080x1374 + LazyColumn(24,12)/1032x1155 + ChatInputBar(24,1194)/1032x168. ChatInputBar(1194)가 LazyColumn 끝(1167) 아래에 정확히 정렬 — 침범 0
- **TextField height 1줄만 측정**: text length 0↔1 반복, lines=1만. multi-line 시나리오 측정 못 함 (사용자 시나리오에서 enter로 multi-line 입력 안 함 또는 enter 동작 자체 의문)
- **sheet drag 0회 위임 측정**: `b190a2bc` 상태에서 사용자 모든 swipe 시도가 sheet drag로 0번 전달. NestedScrollConnection 잘못 사용 + sheetContainerColor=Transparent + drag handle null 조합 의심
- **drag handle 활성화 후 sheet drag 작동 시작** — 다만 list 영역에서는 nested scroll 자동 위임 안 됨

### 복원 결정 (사용자 명시 2026-04-28 후반)
- 사용자: "현재 상태가 이전 커밋과 큰 차이 없음. 오히려 이전 커밋으로 되돌리는 게 불필요한 수정 제거 가능"
- `git restore` 로 ChatScreen.kt + MainActivity.kt 만 `b190a2bc` 상태로 복원 (다른 트랙 WIP 그대로)

### 미해결 (다음 세션 정밀 진단 필요)

#### A. BottomSheetScaffold + LazyColumn nestedScroll 정합 패턴
- 현재(b190a2bc): drag handle null + sheetContainerColor Transparent → sheet drag 자체 미작동 측정 확인됨
- WebFetch reference: "LazyColumn이 sheet content 내에서는 자동 nested scroll 위임 안 됨 — 명시적 NestedScrollConnection 구현 필요"
- 정합 적용 위치 미확정 (LazyColumn에 적용 ≠ 외곽 Column에 적용. nestedScroll modifier 동작 정확히 이해 필요)
- 비교 대상: M3 1.4.0-alpha14+ `lineLimits.MultiLine` API or `ModalBottomSheet` 변경 검토

#### B. 본문 multi-line auto-grow
- legacy `TextField(value, onValueChange, maxLines=3, singleLine=false)` API + `Modifier.weight(1f)` 조합
- 공식 docs: "weight(1f)이 height 차지 강제 → TextField auto-grow와 상충 가능"
- 진단: TextField 자체에 `onSizeChanged` 측정 → multi-line 시 height 자라는지 확인 + `Modifier.weight(1f, fill = false)` 또는 `BasicTextField` 교체 시도

#### C. Peek 상태 nav bar 영역 침범
- 외곽 Column `background(surfaceContainer).navigationBarsPadding()` 순서 — Compose modifier chain (background → padding) 표준 권장이지만 결과적으로 background가 nav inset 영역까지 색칠됨
- 의도된 design? 또는 nav inset 영역에 색칠 안 하려면 순서 반전 — sheet의 시각 hit area와 visual 영역 분리 가능

#### D. 자동 스크롤 (전송 시)
- `rememberLazyListState` + `LaunchedEffect(messages.size) animateScrollToItem(0)` 패턴 — reverseLayout=true 정합 권장 패턴
- 단독 fix로 검토 (다른 7번 fix와 분리)

### 다음 세션 작업 권장
1. 본 문서 + `b190a2bc` commit 정독
2. plan mode + WebFetch 정밀 reference (M3 BottomSheetScaffold nested scroll API + multi-line TextField alpha API)
3. 측정 강화 후 단일 fix 검증 사이클 (한 번에 하나만 변경 후 검증)
4. PERMISSION_DENIED on `loadInitialMessages` 처리 (firestore.rules 배포 거부 사유)
5. **Step 6: driver_app + pickup_driver_app chat 이식** (사용자 명시 2026-04-28 후반)

## Step 6 — driver_app + pickup_driver_app 이식 가이드 (다음 세션 우선)

### 이식 범위 (call_manager Step 5 commit `b190a2bc` 기준)

| 영역 | call_manager 파일 (reference) | driver_app / pickup_driver_app 이식 위치 |
|------|------------------------------|------------------------------------------|
| 데이터 레이어 | `data/local/AppDatabase.kt` (v5→v6) + `LocalChatMessage.kt` + `ChatMessageDao.kt` + `data/repository/ChatRepository.kt` | 동일 구조, package 변경 |
| ViewModel | `ui/chat/ChatViewModel.kt` | senderRole 고정값 변경 (driver_app="DESIGNATED_DRIVER", pickup_driver_app="PICKUP_DRIVER") |
| Compose UI | `ui/chat/ChatScreen.kt` (`ChatBottomSheetContent` + `ChatPeekPreviewBar` + `ChatInputBar` + `ChatMessageRow`) | 그대로 이식 |
| MainActivity 통합 | `MainActivity.kt` (`DashboardWithChatSheet`) | driver_app 메인 화면 / pickup_driver_app 메인 화면에 통합 |
| FCM 알림 | `service/MyFirebaseMessagingService.kt` (NEW_CHAT_MESSAGE 분기 + `chat_messages_ptt` 채널 + ptt 효과음) | 동일 패턴 + 알림음 (raw resource) |
| Manifest | `AndroidManifest.xml` `windowSoftInputMode="adjustResize"` | 메인 Activity에 동일 적용 |
| 로그아웃 | `MainActivity.logoutAndExit()` (managerTokens 삭제) | 각 앱은 다른 collection (designated_drivers/pickup_drivers의 fcmToken 필드) |
| 알림음 raw | `res/raw/ptt_start.m4a` | 동일 파일 복사 |

### 호환성 체크 (선행)

⚠️ **driver_app Compose BOM = 2023.10.01** — Material 3 BottomSheetScaffold 호환 여부 확인 필수
- 비호환 시: BOM 업그레이드 또는 Material 3 `ModalBottomSheet`으로 대체
- pickup_driver_app도 BOM 확인

### 이식 전 권장 (call_manager 미해결 4종 먼저 정밀 진단)

이식 전에 call_manager의 미해결 4종 (위 미해결 섹션 A~D) 정밀 진단 + fix가 안정되면 그 fix를 driver_app/pickup_driver_app에도 동시 이식. 그렇지 않으면 같은 증상이 3 앱에서 동일 발생 → 3중 디버깅.

### 단계적 이식 순서 권장
1. call_manager 미해결 4종 정밀 진단 + fix 검증 (한 번에 하나씩)
2. 안정된 ChatBottomSheetContent / ChatInputBar / NestedScroll 패턴 확정
3. driver_app Compose BOM 호환성 확인 → 필요 시 업그레이드
4. driver_app에 데이터 레이어 → ViewModel → UI → MainActivity 순서 이식
5. pickup_driver_app에도 동일 패턴 이식
6. 3 앱 동시 동작 검증 (S21+/S22/Z Flip4)

### Step 6 알림음
- `res/raw/ptt_start.m4a` 파일 driver_app + pickup_driver_app `res/raw/`에 복사
- `MyFirebaseMessagingService` (또는 동등) 의 chat 채널 ID = `chat_messages_ptt` (call_manager와 동일 ID 사용 가능 — 앱별 채널은 패키지 단위 분리됨)



## 완료 상태

### Step 5 UI (call_manager) — 2026-04-28 5 commit 추가 완료

| # | Commit | 내용 |
|---|---|---|
| 1 | `ad6f11a7` | FCM NEW_CHAT_MESSAGE 분기 + chat_messages 알림 채널 (IMPORTANCE_HIGH, default sound, DND 우회 X) |
| 2 | `3651f97d` | ChatViewModel + ChatScreen 기본 (SharedPreferences p/c/o, admins/{uid}.name) |
| 3 | `215d937d` | UI 디테일 (5분 그룹화, sendStatus ✓회색/✓파랑/✗빨강+retry, 시간 "오후 8:32") |
| 4 | `d01e5b63` | BottomSheetScaffold 통합 (DashboardWithChatSheet, peek 56dp + Expanded) |
| 5 | `6ef6770b` | 로그아웃 시 managerTokens/{uid} 삭제 (logoutAndExit best-effort) |

각 commit 후 `./gradlew :app:compileDebugKotlin` 통과. WIP 파일 (LoginViewModel, SignUp 등) 미터치.

### V1.5로 deferred (이번 세션 결정)
- **50% 중간 anchor**: Material 3 BottomSheetScaffold 기본은 peek+Expanded 2-state. 50% 추가하려면 AnchoredDraggable 커스텀 구현 필요 → 운영 학습 후 재검토
- **새 메시지 도착 시 peek 56→80 부풀음 + 진동**: 시각/촉각 피드백. V1.5 안전 보강 시 추가

### Step 1~4 (기존, commit 7290ab17 origin/manager-direct-drive)
- ✅ Phase A: 메모리 저장 (PLAN + 양평 단톡방 데이터 + V2 후보)
- ✅ Step 1: `docs/chat-shared-spec.md` SSOT
- ✅ Step 2: `firestore.rules` chatRoom 매치 블록 + collectionGroup messages 인덱스
- ✅ Step 3: `functions/src/handlers/chat.ts` (onChatMessageCreated + 90일 cleanup + backfill + 동기화 트리거 4개)
- ✅ Step 4: `registerOwner`에 매니저 자동 멤버 등록 + 기사/admin 동기화 트리거
- ✅ Step 5 데이터 레이어: AppDatabase v5→v6 + LocalChatMessage + ChatMessageDao + ChatRepository + Application 주입

빌드 검증:
- TypeScript: `npx tsc --noEmit` 통과
- Firebase rules: `firebase deploy --only firestore:rules --dry-run` 통과
- Kotlin (call_manager): `./gradlew :app:compileDebugKotlin` 통과 (각 commit별)

## ⚠️ Step 5 잔여 작업 (다음 세션 첫 우선) — 2026-04-28 후반 갱신

### 후속 결정 (RTDB 검토 후 Firestore 유지)
- 사용자가 원래 RTDB 의도였으나 Firestore로 구축 발견 → 전환 검토
- 분석 결과: V1 패턴(FCM+1회 fetch)에서 RTDB 강점(listener) 활용 안 됨, 비용 거의 동등(최적화 시), 마이그레이션 6~8h, 이중 SDK 부담 → **Firestore 유지 확정**
- 향후 별도 트랙: since-timestamp pagination 최적화 (확장 단계 reads 90% 감소)

### 적용된 모든 fix (working tree, 미커밋)

#### Functional fixes (검증 통과/안전)
1. ✅ **Room 스키마 fix**: `LocalChatMessage`에 `@ColumnInfo(defaultValue="SENT")` + `@Entity(indices=[idx_chat_messages_office_time])` — Migration_5_6 hash 일치 (crash 해결)
2. ✅ **AndroidManifest**: MainActivity에 `windowSoftInputMode="adjustResize"` 추가
3. ✅ **ChatMessageDao 정렬**: `ORDER BY createdAt ASC, clientCreatedAt ASC` → `DESC, DESC` (LazyColumn reverseLayout=true와 정합, 카톡 스타일)
4. ✅ **functions/scripts/backfill-chat-members.js**: 신규 (1004 사무실 chatRoom/main/members 매니저+기사 2명 백필 1회 실행)

#### Chat sheet UI (Jetchat 패턴 + 책갈피 디자인)
5. ✅ **ChatScreen.kt**:
   - imports: `background`, `imePadding`, `navigationBarsPadding`, `RoundedCornerShape`, `TextField`, `TextFieldDefaults`
   - LazyColumn `verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom)` (두 군데)
   - `ChatBottomSheetContent(viewModel, isExpanded: Boolean = false)`: 외곽 Column에 `fillMaxHeight(0.95f) + background(surfaceContainer) + navigationBarsPadding()`. peek bar는 `if (!isExpanded)` 조건부 표시
   - `ChatPeekPreviewBar`: 책갈피(width 110dp / height 20dp / `RoundedCornerShape(topStart=10dp, topEnd=10dp)` / `surfaceContainerHighest` 색 / amber primary 텍스트 "💬 채팅" / shadowElevation 6dp) + peek 카드(fillMaxWidth / `surfaceContainerHigh` / topStart/topEnd 12dp 둥근 / shadowElevation 2dp / Box height 56dp + horizontal 16dp padding + 메시지 미리보기)
   - `ChatInputBar`: TextField filled (indicator 모두 `Color.Transparent`, maxLines 3, placeholder "메시지 입력") + `imePadding()` (navigationBarsPadding은 외곽 Column에서 처리)
6. ✅ **MainActivity.kt** `DashboardWithChatSheet`:
   - `rememberBottomSheetScaffoldState()` + `LaunchedEffect(sheetTargetValue)` 키보드 close 트리거 (`PartiallyExpanded` 또는 `Hidden` 시 `keyboardController.hide()`)
   - `WindowInsets.navigationBars.getBottom(density).toDp()` 동적 측정
   - `BottomSheetScaffold(scaffoldState, sheetPeekHeight = 76.dp + navInsetDp, sheetDragHandle = null, sheetContainerColor = Transparent, sheetShadowElevation = 0.dp)`
   - `ChatBottomSheetContent(chatViewModel, isExpanded = sheetTargetValue == SheetValue.Expanded)`

#### Dashboard layout (외곽 inset 중복 제거)
7. ✅ **DashboardScreen.kt** `DriverStatusCard` 안 Row: `verticalAlignment = Alignment.CenterVertically` → `Alignment.Bottom` (기사 1004 줄을 카드 안 아래쪽 정렬, chat 인접)
8. ✅ **DashboardScreen.kt** Scaffold: `contentWindowInsets = WindowInsets(0)` 추가 — Scaffold가 자체 nav inset 처리 안 하게 (BottomSheetScaffold가 sheetPeekHeight=76+nav로 이미 처리, 중복 제거). **이전 "기사카드와 chat 사이 큰 검은 빈 영역 144px"의 진짜 원인. 사용자가 직접 지적 ("세 카드를 감싸고 있는 큰 카드의 여백")으로 확정**

### 잔여 (다음 세션 첫 작업)

#### 1. 검증 (S21+) — contentWindowInsets fix 효과 확인 필요
빌드 + 설치 완료. 디바이스 검증 미완료. 시각적 시나리오:
- 호출목록/공유콜/기사카드/chat 책갈피 모두 정상 표시 + 비율 그대로
- 기사카드 아래 검은 빈 영역(이전 144px)이 사라짐 = chat 책갈피와 인접
- TextField 탭 → 키보드 ON: 입력바 키보드 위 / 메시지 list 안 사라짐
- sheet 내림 → 키보드 자동 close
- 책갈피: 회색 + amber 텍스트 자연 디자인

#### 2. Firestore PERMISSION_DENIED on `loadInitialMessages`
- chatRoom 백필 완료 (`functions/scripts/backfill-chat-members.js` 1회 실행)
- `loadInitialMessages`에서 PERMISSION_DENIED → firestore.rules production 미배포로 추정
- **차단**: 사용자가 `firebase deploy --only firestore:rules` 거부 (이전 세션). **다음 세션에서 거부 사유 확인 → 배포 가능 여부 결정**

#### 3. 검증 통과 시 단일 commit
```
fix(chat): IME UX + 책갈피 카드 디자인 + 키보드 close + Scaffold inset 중복 제거 (Jetchat 패턴)

- ChatBottomSheetContent: 외곽 Column nav padding + isExpanded 조건부 peek
- ChatPeekPreviewBar: 책갈피(회색+amber 텍스트) + 카드 통합 디자인
- ChatInputBar: TextField(filled) + imePadding chain
- MainActivity DashboardWithChatSheet: scaffoldState + 키보드 close + sheet config (transparent/76dp+nav peek/null drag handle)
- DashboardScreen DriverStatusCard 안 Row: alignment Bottom
- DashboardScreen Scaffold: contentWindowInsets = WindowInsets(0) — nav 중복 제거
- LocalChatMessage Room 스키마 fix
- ChatMessageDao 정렬 ASC→DESC
- AndroidManifest adjustResize
- functions/scripts/backfill-chat-members.js (신규 운영 도구)
```

### 이번 세션 학습 (다음 세션 클코 주의)

1. **git checkout 절대 금지** — uncommitted 변경 손실 위험 (이번 세션에 ChatScreen.kt + MainActivity.kt 변경 손실, 메모리 기반 재작성 필요했음)
2. **GUI 디버깅 추측 fix 반복 금지** — 측정/공식 reference (Jetchat 등) 후 진행 (`memory/feedback/feedback_no_guess_gui_fix.md`)
3. **"외곽 padding/wrapper" 의심** — 사용자가 "여백"이라고 할 때 카드 안 빈 공간만 보지 말고 외곽 Scaffold contentWindowInsets / paddingValues 중복 등도 확인
4. **plan workflow 따르기** — 코드 수정 전 plan 모드 + read-only 진단 + plan agent 검증 후 실행

## 다음 작업 — Step 6 (driver_app) 진입 (Step 5 잔여 후)

call_manager Step 5 완료. 다음은 driver_app에 동일 구조 이식:
- ChatRepository (driver_app 데이터 레이어)
- ChatViewModel + ChatScreen (driver_app용, senderRole=DESIGNATED_DRIVER 고정)
- BottomSheetScaffold 통합 (driver_app MainActivity)
- FCM NEW_CHAT_MESSAGE 분기 + chat_messages 채널 (driver_app FCM service)
- 로그아웃 시 driver_app FCM token 삭제

⚠️ **driver_app Compose BOM = 2023.10.01** — Material 3 BottomSheetScaffold 호환 여부 확인 필수. 비호환 시 BOM 업그레이드 또는 Material 3 ModalBottomSheet으로 대체 검토.

## 디바이스 검증 (Step 5 → Step 6 전 권장)

3 기기 (S21+ R3CR312MB1L, S22 R5CT41TJZFP, Z Flip4 R3CT80K78NP) 중 **call_manager 단독 검증**:
- backfillChatMembers 1회 호출 (firebase functions:shell): `backfillChatMembers({provinceId, cityId, officeId})`
- S21+(call_manager 설치 기기) 로그인 → Dashboard 진입
- BottomSheet peek 56dp 보임 → 위로 끌어올리기 → Expanded 전환
- 입력바에서 메시지 발송 → Firestore 콘솔 `messages/` 도큐먼트 생성 확인
- Optimistic UI: 발송 직후 ✓회색 → 1초 내 ✓파랑 전환
- 비행기 모드 → 발송 → ✗빨강 → 비행기 OFF + ✗ 탭 → ✓파랑 전환
- 다른 기기에서 답장 시 (단, Step 6/7 후): peek 미리보기 갱신
- 로그아웃("종료") 시 Firestore managerTokens/{uid} 문서 삭제 확인

## Archive — Step 5 진입 전 인계 (이하는 참고용 원본 보존)

---

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

---
name: share-intent-chat-copy-2026-05-18
description: 5/18~19 manager-direct-drive +4 commit — 카톡/문자 공유 → 사무실 단톡방 prefill (3앱) + 채팅 메시지 long-press 복사 (3앱 통일) + driver_app Auth race 회귀 진단·회피 + 호스팅 APK 교체.
metadata:
  type: project
---

# 5/18~19 공유 prefill + 채팅 long-press 복사 (manager-direct-drive +4 commit)

## 운영 마찰 → 해소

매니저·기사가 손님 카톡/문자에서 받은 콜 정보를 콜매니저·기사앱에 옮길 때 한 글자씩 다시 타이핑하는 마찰. 또 사무실 단톡방 메시지를 외부 앱에 옮길 때 복사 불가. 두 마찰 동시 해소.

## commit 트레일 (4 commit, master 대비 +4)

| commit | 내용 |
|--------|------|
| `a39c267e` | call_manager 공유 prefill (5/18) |
| `b4828bdc` | driver_app 공유 prefill + Auth race 회피 (5/18) |
| `e86437d3` | hosting APK 3종 교체 + deploy (5/18) |
| `055a5c1c` | 3앱 채팅 long-press 클립보드 복사 (5/19) |

## 트랙 1 — 공유 prefill (ACTION_SEND text/plain)

### 사용자 의도 정정 트레일 (5/18, 두 번 plan 폐기)
- v0: 신규 콜 등록 다이얼로그 prefill — 사용자 발화로 폐기 ("기사들에게 전달")
- v1: 사무실 단톡방 채팅창 입력바 prefill + 매니저 [전송] 수동 → 확정

수동 [전송] = 카톡·Gmail·라인·X·인스타그램 DM 표준 UX (오공유 방지). 자동 전송 거부.

### 패턴 (각 앱 동일)

1. `AndroidManifest.xml` MainActivity 블록에 `ACTION_SEND text/plain` intent-filter 추가
2. `ChatViewModel` 에 `inputText: StateFlow<String>` + `shouldExpandSheet: StateFlow<Boolean>` state 추가 (Composable internal `var inputText by remember` → ViewModel state ascension, race 회피)
3. `ChatBottomSheetContent` 의 `inputText` 를 `viewModel.inputText.collectAsState()` 로 마이그레이션
4. `MainActivity` 에 ACTION_SEND 분기 → `chatViewModel.setInputText(text)` + `requestExpandSheet()`
5. `Dashboard/HomeScreenWithChatSheet` 에 `LaunchedEffect(shouldExpand)` → `scaffoldState.bottomSheetState.expand()` + consume

### driver_app 특이점 — Hilt + Auth race 회귀

**문제**: AppNavigation 시그니처에 `chatViewModel` 직접 전달 → `setContent` 의 첫 evaluation 시점에 `by viewModels<ChatViewModel>()` lazy 발동 → 미로그인 화면에서도 ChatViewModel 인스턴스화 → Firebase Auth 복원 race → `senderId=""` 영구 박힘 → `isReady=false` → messages flow `emptyList()` 영구 반환 → **빈 채팅 회귀**.

S22 logcat 결정적 단서:
```
W ChatViewModel: [init] 필수 정보 부족 - provinceId=gyeonggi, cityId=yangpyeong,
                 officeId=RUbeBEvGGYP5wMhJHhMF, senderId=
```

**회피**: `AppNavigation` 시그니처에서 chatViewModel 제거. `composable(HOME_ROUTE)` 람다 안에서 `hiltViewModel(activity)` 호출 (Activity scope 명시) → ChatViewModel 인스턴스화를 home route 진입 시점으로 늦춤 = Auth 복원 완료 후. MainActivity 의 `by viewModels<ChatViewModel>()` 와 같은 ViewModelStoreOwner (Activity) → 동일 인스턴스 보장.

`handleSharedTextIntent` 에 `auth.currentUser == null` 가드 추가 — 미로그인 시 state 세팅 무시.

### 적용 단말 검증
- S21+ (R3CR312MB1L) call_manager — E2E 5종 통과 (5/18)
- S22 (R5CT41TJZFP) driver_app — adb 강제 디스패치 검증 `[handleSharedTextIntent] length=27` (5/18) + 회귀 수정 후 채팅 복구 확인 (5/19)
- Z Flip4 (R3CT80K78NP) driver_app + pickup — install Success

### 카톡·삼성 메시지 공유 시트 미노출 이슈 (S22)

OS 레벨은 driver_app 정상 등록 (`adb shell cmd package query-activities -a android.intent.action.SEND -t "text/plain"` 결과에 `com.designated.driverapp.app` 노출). 그러나 카톡·삼성 메시지 자체 공유 시트는 driver_app 표시 안 함.

원인: 카톡·메시지 앱이 자체 공유 시트 사용 + 외부 앱 캐싱 또는 화이트리스트. 시스템 chooser 강제 디스패치 (`am start -a android.intent.action.SEND`) 로는 정상 노출 확인.

**우리 코드 영향 0**. 카톡 측 동작. 사용자는 다른 발신 앱(Chrome, Samsung Notes 등 시스템 chooser 호출 앱)에서는 정상 공유 가능.

## 트랙 2 — 채팅 메시지 long-press 복사 (3앱 통일)

### 진단

3앱 `ChatScreen.kt` 의 `private fun MessageBubble` 이 `SelectionContainer` 없이 단순 `Text` composable → long-press → 시스템 "복사" 메뉴 안 뜸, 드래그 선택 불가.

### 사용자 결정 (AskUserQuestion 5/18)
- UX: **카톡식 long-press → 전체 메시지 클립보드 + Toast** (SelectionContainer 거부)
- 범위: **call_manager + driver_app + pickup_driver_app 모두**

### 패턴 (3앱 동일)

`combinedClickable` modifier + `onLongClick`:
- `LocalClipboardManager.setText(AnnotatedString(text))`
- `Toast.makeText(context, "메시지를 복사했어요", LENGTH_SHORT).show()`
- `onClick = {}` 빈 람다 (짧은 탭 무동작, 카톡 정합)
- 햅틱 진동 자동 (`combinedClickable` 표준)
- ImageBubble 변경 안 함 (사진 long-press 별도 트랙)

### 적용 단말 검증
- S22 또는 Z Flip4 driver_app/pickup 1차 검증 통과 (5/19)
- S21+ 미연결로 call_manager + S21+ driver_app/pickup install 미진행 (사용자 환경 후속)

## 트랙 3 — 호스팅 APK 교체 (calldetector-5d61e.web.app)

5/11 commit `f2bb9b8c` 패턴 따라 `public/apk_downloads/` 의 3종 APK 교체 (call_manager + driver_app + pickup_driver_app, 5/18 빌드). call_detector / customer_app 변경 0 → 5/11 / 4/23 그대로 유지.

`firebase deploy --only hosting:calldetector` → 3 files uploaded ✅. CDN 즉시 반영.

`.gitignore *.apk` 정책으로 driver_app-debug.apk 1개만 git tracked (5/11 패턴 정합), 나머지 disk-only deploy.

## 비-진입 사항 (별도 트랙)

- **vCard mime (`text/x-vcard`)** — 카톡 연락처 카드 공유 케이스. 단말 실측 후 v1.1
- **사진/이미지 공유 수신** (`image/*`) — 별도 mime + Storage 업로드 흐름
- **자동 [전송]** — 오공유 위험 + 검수 여지 0 → 거부 유지
- **외부 공유 텍스트 자동 파싱** (전화번호/출발지/도착지/요금 추출) — 현재 의도는 *복붙*. v1.1 검토
- **ImageBubble long-press 복사** (사진 URL 클립보드)
- **SelectionContainer** (일부 텍스트만 선택) — 카톡도 long-press 우선
- **컨텍스트 메뉴 확장** (답장/삭제/공유)
- **head_manager_web 채팅 복사**
- **call_detector ACTION_SEND** — 수동 콜 입력 UI 없음
- **customer_app_flutter ACTION_SEND** — 운영 시나리오 없음
- **S21+ 잔여 install** (call_manager + S21+ driver_app + S21+ pickup) — 단말 연결 후

## 클코 학습 (5/18~19)

1. **두 번 plan 폐기 → 의도 정정** — 사용자 발화 "기사들에게 전달" 받기 전까지 NewCallInputDialog prefill 방향으로 plan 작성. 사용자 의도가 자명하지 않은 첫 단계에서 *사용자 의도 명시 확인* 절차가 plan 깊이보다 우선.

2. **race 검토는 timing 변경 자체가 race를 만들 수 있음** — race 회피로 ViewModel state ascension 했는데, 그 변경 (`by viewModels()` MainActivity 멤버 + AppNavigation 시그니처 전달) 이 ChatViewModel 인스턴스화 timing 을 setContent 첫 evaluation 으로 앞당겨 *새 race* (Auth 복원 미완) 를 만듦. *변경의 timing 영향* 도 race 검토 항목에 포함할 것.

3. **5번 회귀 후보 검토에서 "미로그인 화면에서 ViewModel 평가 trigger" 누락** — plan v1 race 검토 8건 + 회귀 9건 점검에 *미로그인 화면 진입* 시나리오 미포함. 단말 검증 후 발견. 다음 plan 에서는 *모든 startDestination 화면에서 의도된 평가만 발동하는지* 검토 항목 추가.

4. **호스팅 APK 와 git history 분리 가능성** — `.gitignore *.apk` 정책으로 driver_app 1개만 추적, 나머지 disk-only. 5/11 패턴 정합. 단 commit 메시지에 "4종 deploy" 명시하면 그 시점 호스팅 상태 추적 가능.

## 다음 세션 진입 후보

1. **master PR 생성** (`gh pr create` — 4 commit 묶음)
2. **S21+ 연결 후 잔여 install** (call_manager + S21+ driver_app + S21+ pickup)
3. **vCard 단말 실측** (카톡 친구 정보 카드 공유 mime 확인)
4. **별도 트랙** (SelectionContainer / 컨텍스트 메뉴 확장 / ImageBubble 복사 등)

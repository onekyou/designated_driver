# 콜마당 사무실 단톡방 채팅 — V1 MVP 구현 플랜 (rev3 — 최종)

## Context (왜)
- **문제**: 양평 1호 사무실 피드백 — 콜마당 외에 카톡으로 콜 관련 소통 이중 사용 → 번거로움. 박상준씨 22:03 발언 *"두 가지를 쓰려니 둘 다 체크를 해야 돼서요"*
- **핵심 정체성**: 단톡방 = **운행 중 매니저 ↔ 기사 양방향 전달 채널**. 운행 중 사용이 1순위 use case (운행 안전 보강은 V1.5 별도 트랙)
- **확정 범위**: 사무실당 단톡방 1개. 매니저(들) + 대리기사 + 픽업기사 모두 한 방. 텍스트만 MVP. **메시지 90일 후 자동 삭제** (PII 누적 방지)
- **아키텍처 원칙**: FCM + 로컬 DB 트리거 (Firestore 실시간 리스너 금지 — 콜마당 비용 최적화 정책, driver_app $414/월 절감 사례)
- **체감 속도**: 카톡과 동일(둘 다 FCM 기반), Optimistic UI로 보내는 쪽 0초

---

## 1. Firestore 데이터 모델

### 경로
```
provinces/{p}/cities/{c}/offices/{o}/chatRoom/main/messages/{messageId}
provinces/{p}/cities/{c}/offices/{o}/chatRoom/main/members/{userId}
```
- `messageId` = `firestore.collection(...).document().id` (Firebase가 충돌 0 보장). 클라이언트가 미리 발급 후 동일 ID로 set → Optimistic UI 충돌 방지
- 단일 문서 `chatRoom/main` 아래 messages 서브컬렉션 → V2 다중 채널 확장 여지

### 메시지 필드
```
id, senderId, senderName, senderRole (MANAGER/DESIGNATED_DRIVER/PICKUP_DRIVER),
text (≤2000자), createdAt (serverTimestamp), clientCreatedAt (epoch ms), status="SENT"
```
- senderName/senderRole 비정규화 (조회 비용 절감, 카톡과 동일 동작)

### members (멤버십 화이트리스트만)
```
chatRoom/main/members/{userId} = { userId, role, joinedAt }
```
- **lastReadAt 필드 없음** — 읽음 추적 자체를 V1에서 제거 (Firestore write 0)
- 멤버 문서는 보안 규칙용 화이트리스트로만 사용
- 안 읽음 카운트/표시 V1에 없음. 새 메시지 인지는 BottomSheet 부풀음 + 진동 + 알림음으로 처리

### 페이지네이션
- 채팅방 첫 진입 시: `.orderBy("createdAt", DESC).limit(50).get()` — 최근 50건
- 위로 스크롤: 50건씩 추가 (`startAfter(lastDoc)`)
- 로컬 DB 무한 누적 방지: 1000건 초과 시 오래된 것부터 트림 (V1.5)
- text 2000자 제한 (FCM 4KB 페이로드 한계 대응, UTF-8 한글 3바이트 고려 시 약 1300자 안전선)

### 보안 규칙 (firestore.rules 365-367행 직전, offices 블록 내부)
**중요**: 멤버 문서 생성을 클라이언트가 직접 못 하게 막음. CF만 admin 권한으로 생성/삭제. 그렇지 않으면 임의 사용자가 타 사무실 화이트리스트에 자기를 등록해 도청 가능 → **보안 사고**.

```
match /chatRoom/{roomId} {
  function isChatMember() {
    return exists(/databases/$(database)/documents/provinces/$(p)/cities/$(c)/offices/$(o)/chatRoom/main/members/$(request.auth.uid));
  }
  allow read: if isChatMember();
  allow write: if false;

  match /messages/{messageId} {
    allow read: if isChatMember();
    allow create: if isChatMember()
                  && request.resource.data.senderId == request.auth.uid
                  && request.resource.data.text is string
                  && request.resource.data.text.size() in 1..2000;
    allow update, delete: if false;
  }
  match /members/{userId} {
    allow read: if isChatMember();
    // CF만 admin 권한으로 create/delete. 사용자 write 전부 봉쇄
    allow write: if false;
  }
}
```

**docId-authUid 매칭**: members 문서의 `{userId}` = `request.auth.uid`로 통일 (CF가 admin 권한으로 설정). designated_drivers/pickup_drivers의 docId는 별개여도 무관.

---

## 2. 공유 모듈 결정 — 코드 복제 + 명세서

**근거**: 콜마당 기존 관례(3 앱 독립). Compose BOM 갭(call_manager 2024.04.01 vs driver_app 2023.10.01) 정렬 비용 vs 공유 클래스 ~30줄. 모듈화 트랜잭션 비용 > 코드 중복 비용.

**대신**: `docs/chat-shared-spec.md` 1장 = `ChatMessage` 모델 + FCM payload 키 + `MessageRole` enum 단일 출처.
- **`docs/` 폴더는 프로젝트 루트에 신규 생성 필요** (현재 없음)

---

## 3. call_manager 변경

### Room v5 → v6 (`AppDatabase.kt`)
- entities에 `LocalChatMessage::class` 추가 (line 14-23 근처)
- `MIGRATION_5_6`: `CREATE TABLE chat_messages (..., sendStatus TEXT DEFAULT 'SENDING')` 추가
- `chatMessageDao()` abstract 추가
- ⚠️ **라인 번호(14-23, 47, 121)는 Plan agent 추측이라 구현 시 직접 Read 후 정확한 위치 정정 필요**

### 신규 파일 (5개)
- `data/local/LocalChatMessage.kt`, `ChatMessageDao.kt`
- `data/repository/ChatRepository.kt`
- `ui/chat/ChatViewModel.kt`, `ChatScreen.kt`

### 알림 채널 신규 — `chat_messages`
- IMPORTANCE_HIGH (NEW_CALL의 MAX보다 한 단계 아래)
- **default 알림음 ON** (RingtoneManager.TYPE_NOTIFICATION) + 진동 ON
- DND 우회 X (콜과 차별 — 콜은 알람음 + DND 우회, 채팅은 일반 알림음)
- 등록 위치: `MyApp.onCreate()` 또는 채널 매니저

### FCM 분기 (`MyFirebaseMessagingService.kt:65-75, 128-184`)
- `alwaysProcessTypes`에 `"NEW_CHAT_MESSAGE"` 추가
- when 블록에 `"NEW_CHAT_MESSAGE" -> handleChatMessage(remoteMessage)` 추가
- `handleChatMessage`: payload → ChatRepository INSERT (본인 senderId면 skip, 이미 Optimistic INSERT 됨) → **앱 백그라운드일 때만** `chat_messages` 채널 시스템 알림. 포그라운드는 BottomSheet 부풀음으로 충분

### Optimistic UI 상태 + 실패 처리
- `sendStatus`: SENDING → SENT (Firestore write 성공) → FAILED (실패)
- UI: SENDING ✓회색, SENT ✓파랑, FAILED ✗빨강 + 탭하면 재시도
- 비행기모드/네트워크 오류 시 FAILED 정착, 사용자 수동 재시도

### 로그아웃 시 토큰 삭제
- `LoginViewModel`/로그아웃 진입점에서 `managerTokens/{adminUid}` 문서 삭제 단계 추가

### UI 진입점 — BottomSheet 패턴 (3 앱 통일)
- **`BottomSheetScaffold`** (Material 3) 적용. `MainActivity.kt` NavHost에서 메인 화면(Dashboard 등)을 `sheetContent = ChatScreen`으로 감쌈
- **3단계 sheet 상태**:
  - peek (56dp): 카드 1개 형태로 접힘 — `💬 박상준: 통완` (마지막 메시지, 카운트 없음)
  - **PartiallyExpanded (50%, 기본 펼침)**: 사용자가 카드 탭 → 절반 펼쳐 채팅창 절반 + 메인 콘텐츠 절반 동거. 매니저 멀티태스킹 핵심
  - Expanded (95%): 사용자가 더 끌어올리면 거의 풀스크린
- **새 메시지 도착**: peek 56→80dp animateTo (살짝 부풀음) + **default 알림음** + 진동 1회
- 풀스크린 ChatScreen 구조: TopBar(← + "양평지점 단톡방") + LazyColumn(reverseLayout=true, 카톡 패턴 5분 그룹화 + 본인 우/타인 좌) + 입력바
- 채팅방 진입 시 멤버 문서 update 없음 (lastReadAt 자체 미사용)

---

## 4. driver_app 변경

### 별도 `ChatDatabase` (SettlementDatabase에 얹지 않음)
**근거**: 명칭 정합성, 마이그레이션 회귀 위험 격리. Hilt @Provides로 충돌 없음.

### 신규 파일 (6개)
- `data/local/chat/ChatDatabase.kt` (v1), `ChatMessageEntity.kt`(sendStatus 필드 포함), `ChatMessageDao.kt`
- `data/repository/ChatRepository.kt` (Hilt @Singleton)
- `ui/chat/ChatViewModel.kt` (@HiltViewModel), `ChatScreen.kt`
- `di/ChatModule.kt`

### 알림 채널 신규 — `chat_messages`
- IMPORTANCE_HIGH + default 알림음 + 진동 (call_manager와 동일)
- `call_assigned` 채널과 분리 → 운행 알림 우선순위 보존
- 등록 위치: `DriverApplication.onCreate()` 또는 `MyFirebaseMessagingService` 초기화

### FCM 분기 (`MyFirebaseMessagingService.kt:130-250` 246-248행 근처)
`else if (messageType == "NEW_CHAT_MESSAGE")` 분기 추가 → ChatRepository INSERT + LocalBroadcast + **앱 백그라운드일 때 `chat_messages` 채널 알림**

### Optimistic UI + 실패 처리
call_manager와 동일 (SENDING/SENT/FAILED + 탭 재시도)

### 로그아웃 시 토큰 삭제
`DriverViewModel.signOut` 또는 로그아웃 진입점에서 `designated_drivers/{driverDocId}.fcmToken` FieldValue.delete()

### UI 진입점 — BottomSheet 패턴 (3 앱 통일)
- `MainActivity.kt`에 `BottomSheetScaffold` 적용 — `HomeScreen` + 운행 중 화면(`InProgressScreen` 등) 모두 sheet로 감쌈
- peek 56dp + 새 메시지 시 80dp animateTo + 알림음 + 진동
- **운행 중 자동 숨김 X (사용자 명시 결정 2026-04-28)** — 운행 중에도 BottomSheet 정상 표시. 핵심 use case 자체가 운행 중 매니저 ↔ 기사 소통이라 숨기면 안 됨. 운행 안전 보강 기능(보이스 자동 읽기 등)은 V1.5 별도 트랙
- 풀스크린 ChatScreen: 카톡 패턴 (5분 그룹화, 본인 우/타인 좌, 첫 메시지에만 이름+역할)

---

## 5. pickup_driver_app 신규 인프라 (작업량 최대)

### 의존성 추가 (`pickup_driver_app/app/build.gradle:54-103`)
- Room runtime/ktx/compiler 추가 (driver_app 버전 정렬)

### FirebaseMessagingService 신설
- 신규 `service/PickupFirebaseMessagingService.kt`
- `onNewToken` → `pickup_drivers/{driverDocId}.fcmToken` set (collectionGroup으로 docRef 찾기 — `LoginViewModel.kt:92-137` 패턴)
- `onMessageReceived` → NEW_CHAT_MESSAGE 분기 + `chat_messages` 채널 알림
- `AndroidManifest.xml:1-31`에 `<service>` 등록 (MESSAGING_EVENT intent-filter)
- `LoginViewModel.kt:127-133` SharedPreferences 저장 직후 토큰 저장 단계 추가

### 알림 채널
- pickup_driver_app은 기존 채널 없음 → 신규 `chat_messages` (IMPORTANCE_HIGH + default 알림음 + 진동)
- V2 픽업 콜 알림 추가 시 별도 채널

### ChatDatabase + Chat MVC
- driver_app과 동일 구조 (Hilt 패턴, sendStatus 포함)

### 로그아웃 시 토큰 삭제
`pickup_drivers/{docId}.fcmToken` FieldValue.delete()

### UI 진입점 — BottomSheet 패턴 (3 앱 통일)
- `MainActivity.kt`에 `BottomSheetScaffold` — `DashboardScreen`을 sheet로 감쌈
- 3앱 동일 패턴 (peek 56dp + 새 메시지 시 80dp + 알림음 + 진동, PartiallyExpanded 50% 기본)
- 풀스크린 ChatScreen 카톡 패턴

---

## 6. Cloud Functions

### 파일 분리 — `functions/src/handlers/chat.ts` 신규
**근거**: index.ts 5400+ 라인. handlers/ 폴더 패턴 이미 존재 (`points.ts`, `settlement.ts`). 일관성 유지.

### 6.1 `onChatMessageCreated` 트리거
```ts
export const onChatMessageCreated = onDocumentCreated({
  region: "asia-northeast3",
  document: "provinces/{p}/cities/{c}/offices/{o}/chatRoom/main/messages/{messageId}",
  // cold start 대응 검토. minInstances:1 추가 시 월 ~$5~10. V1은 0으로 시작 후 측정
}, async (event) => { ... });
```
- `index.ts` 16-18행 utils import 영역에 `export { onChatMessageCreated } from "./handlers/chat";`

### 6.2 토큰 수집
1. **매니저**: `provinces/{p}/cities/{c}/offices/{o}/managerTokens` 전체 → `fcmToken` (index.ts:3537-3547 패턴)
2. **대리기사**: `designated_drivers` where `fcmToken != null`
3. **픽업기사**: `pickup_drivers` where `fcmToken != null` (V1 미배포 시 빈 배열)
4. **sender 본인 토큰 제외** (마지막에 `.filter(t => t !== senderFcmToken)`)

### 6.3 발송
- `sendEachForMulticast(buildMulticastFcmPayload({...}, tokens))` (`utils/fcmPayload.ts` 활용)
- payload data: `type=NEW_CHAT_MESSAGE`, messageId, senderId, senderName, senderRole, text, createdAt, clientCreatedAt, p/c/o
- **android.notification.channelId = `chat_messages`** (콜 알림 채널과 분리)
- priority high (운영 채널 정당성, 단 channel importance가 HIGH라 MAX와 차별)
- 실패 토큰 정리: `messaging/registration-token-not-registered` 코드면 토큰 문서 삭제

### 6.4 멤버 자동 동기화 (보안 핵심)

**모두 `handlers/chat.ts`에 추가**:

#### 가입 승인 트리거
- **위치**: 기존 `approveOfficeApplication` (index.ts:5545+) / `approveDriver` 류 함수 안에 후속 단계 추가 (별도 트리거 X, 기존 함수 내부 추가)
- 매니저 가입 승인: `chatRoom/main/members/{adminUid}` set `{userId, role: "MANAGER", joinedAt: now}`
- 대리기사 가입 승인 (`approveDriver` driverType="대리기사"): set with role="DESIGNATED_DRIVER"
- 픽업기사 가입 승인 (driverType="픽업기사"): role="PICKUP_DRIVER"

#### 거부/탈퇴 트리거
- 회원 거부/탈퇴 시 chat member 문서 + fcmToken 정리
- 기존 거부 함수 안에 후속 단계 추가

#### `backfillChatMembers` — 일회성 마이그레이션 함수 (Callable)
- 현재 운영 중인 양평 사무실에 이미 가입된 매니저/기사들을 chat members에 일괄 등록
- 배포 직후 1회 호출 → 후속 가입자는 위 트리거가 처리
- **호출 방식**: `firebase functions:shell` 또는 Cloud Console에서 콜마당 admin 계정으로 호출. context.auth 검증 (admins/{uid} 문서 존재 + 본인이 매니저인지 확인). 매개변수: `{ provinceId, cityId, officeId }`. 멱등성 보장 (이미 멤버이면 skip)

### 6.5 PII 자동 삭제 — Scheduled Function

#### `scheduledChatMessageCleanup`
```ts
export const scheduledChatMessageCleanup = onSchedule({
  schedule: "every day 04:00",
  timeZone: "Asia/Seoul",
  region: "asia-northeast3",
}, async () => { ... });
```
- 매일 새벽 4시 실행
- 모든 office의 `chatRoom/main/messages` 중 `createdAt < now - 90일` 문서 삭제
- collectionGroup 쿼리, 500건씩 batch
- **근거**: 채팅 본문에 고객 전화/주소 PII 포함 가능성 → 90일 보존 (콜 데이터 정책과 정합)

---

## 7. 작업 단계 + 추정

| Step | 작업 | 의존성 | 추정 |
|---|---|---|---|
| 1 | `docs/chat-shared-spec.md` (SSOT) | — | 0.5d |
| 2 | firestore.rules + 에뮬레이터 테스트 | 1 | 1d |
| 3 | `functions/src/handlers/chat.ts` (onChatMessageCreated + 멤버 동기화 + 90일 cleanup + backfill) + emulator 검증 | 1 | 2.5d |
| 4 | 기존 `approveOfficeApplication`/`approveDriver` 함수 안에 chat member 생성 + 거부/탈퇴 트리거 | 3 | 0.5d |
| 5 | call_manager: Room v6 + Chat MVC + FCM + BottomSheet + 알림 채널 + 로그아웃 토큰 삭제 + 실패 UI | 1, 3 | 3.5d |
| 6 | driver_app: ChatDatabase + Chat MVC + FCM + BottomSheet + 알림 채널 + 로그아웃 토큰 삭제 + 실패 UI | 1, 3 | 3d |
| 7 | pickup_driver_app: Room 신설 + FCM Service 신설 + 토큰 저장 + Chat MVC + BottomSheet + 알림 채널 + 실패 UI | 1, 3 | 8.5d |
| 8 | 3앱 합동 통합 테스트 (시나리오 1-15) | 4-7 | 2d |
| 9 | 보안 규칙 회귀 (멤버 우회 침투 테스트 포함) + Firestore 비용 측정 1일 | 8 | 1d |
| 10 | `backfillChatMembers` 운영 호출 + 90일 cleanup 첫 실행 모니터링 | 9 | 0.5d |
| **합** | | | **~23일 (약 4.5주)** |

### 단축 옵션 (V1.0 → V1.1)
**pickup_driver_app 픽업기사 채팅을 V1.1로 분리** → V1.0 = 매니저 ↔ 대리기사 양방. **8.5일 절감 → ~3주**. 픽업기사가 사무실 운영에서 비중 작으면 검토 권장.

### V1.1 이미지 첨부 — V2로 유지
2026-04-28 단톡방 데이터의 사진 공유 = 기사 외부 개인콜 공유였음. 콜마당 단톡방 도입 후 그 패턴 자체가 감소 예상 → 이미지는 V2로. V1·V1.1은 텍스트만.

---

## 8. 검증 (E2E)

### 테스트 기기
| 기기 | 시리얼 | 앱 |
|---|---|---|
| S21+ | R3CR312MB1L | call_manager |
| S22 | R5CT41TJZFP | driver_app |
| Z Flip4 | R3CT80K78NP | pickup_driver_app |

### 로그캣
```bash
ADB="$ANDROID_HOME/platform-tools/adb"
$ADB -s R3CR312MB1L logcat -v threadtime "MyFirebaseMsg:V" "ChatViewModel:V" "ChatRepository:V" "*:E"
$ADB -s R5CT41TJZFP logcat -v threadtime "MyFirebase*:V" "ChatViewModel:V" "*:E"
$ADB -s R3CT80K78NP logcat -v threadtime "PickupFirebase*:V" "ChatViewModel:V" "*:E"
firebase functions:log --only onChatMessageCreated --lines 100
```

### 시나리오
1. 매니저 → 전체 push (S21+ 발신, S22/Flip4 시스템 알림 + BottomSheet 부풀음 + 알림음 도착, CF 로그 `tokens.length=2` 확인)
2. 대리기사 → 전체 (senderRole=DESIGNATED_DRIVER 표시 확인)
3. 픽업기사 ↔ 매니저
4. 백그라운드 + 도즈 모드 (S22 화면 끄고 5분 대기 후 메시지 → high priority FCM 도착)
5. **운행 중 BottomSheet 정상 동작** — driver_app InProgressScreen에서도 sheet 표시 + 알림 + 답장 가능 (운행 중 사용 핵심 use case 검증)
6. Optimistic UI 상태 전이 (비행기 모드 → 메시지 ✓회색(SENDING) → 비행기 OFF → ✓파랑(SENT))
7. 메시지 전송 실패 + 재시도 (비행기 모드 유지 + 1분 timeout → ✗빨강(FAILED) → 비행기 OFF + ✗ 탭 → ✓파랑 전환)
8. 재시작 후 메시지 유지 (Room 로드, Firestore read 0건 확인 — Network Inspector)
9. 권한 거부 (다른 사무실 매니저로 messages 직접 read → PERMISSION_DENIED)
10. **멤버 우회 침투 테스트** — 일반 기사 계정으로 타 사무실 `chatRoom/main/members/{본인UID}` 직접 set 시도 → PERMISSION_DENIED (CF만 생성 가능 검증)
11. **알림 채널 분리 검증** — NEW_CALL 알림 + NEW_CHAT_MESSAGE 알림 동시 발생 → 콜 알림은 알람음 + IMPORTANCE_MAX, 채팅은 default 알림음 + IMPORTANCE_HIGH 차별 확인
12. **회원 가입 → 자동 멤버 등록** — pending_drivers 신규 가입 → 매니저 승인 → CF가 chatRoom/main/members/{authUid} 자동 생성 → 신규 기사 앱에서 채팅방 즉시 보임
13. **회원 거부/탈퇴 → 멤버 삭제** — 거부/탈퇴 → chat member doc 삭제 → 채팅방 access 못 함 (PERMISSION_DENIED)
14. **90일 PII cleanup** — scheduled 함수 수동 트리거 (firebase functions:shell) → 90일 이전 메시지 삭제 확인
15. **BottomSheet 3단계 동작** — peek(56dp) → 탭 → PartiallyExpanded(50%) → 위로 끌기 → Expanded(95%) → 아래로 끌기 → peek 복귀 정상 동작

---

## 9. Critical Files
- `C:\Users\kala1\designated_driver\functions\src\index.ts` (export 추가, 기존 `approveOfficeApplication`/`approveDriver` 안에 chat member 생성 단계 추가)
- `C:\Users\kala1\designated_driver\functions\src\handlers\chat.ts` (신규 — onChatMessageCreated, 멤버 동기화 헬퍼, scheduledChatMessageCleanup, backfillChatMembers)
- `C:\Users\kala1\designated_driver\firestore.rules` (chatRoom 규칙 추가, 멤버 write 전부 봉쇄)
- `C:\Users\kala1\designated_driver\docs\chat-shared-spec.md` (신규 — 3앱 SSOT)
- `C:\Users\kala1\designated_driver\call_manager\app\src\main\java\com\designated\callmanager\data\local\AppDatabase.kt` (v5→v6)
- `C:\Users\kala1\designated_driver\call_manager\app\src\main\java\com\designated\callmanager\service\MyFirebaseMessagingService.kt` (NEW_CHAT_MESSAGE 분기 + chat 채널 알림)
- call_manager NotificationChannel 등록 위치 (`MyApp` 또는 채널 매니저)
- call_manager `MainActivity.kt` (BottomSheetScaffold 적용)
- `C:\Users\kala1\designated_driver\driver_app\app\src\main\java\com\designated\driverapp\MyFirebaseMessagingService.kt` (NEW_CHAT_MESSAGE 분기 + chat 채널 알림)
- driver_app NotificationChannel 등록 위치 (`DriverApplication.onCreate()`)
- driver_app `MainActivity.kt` (BottomSheetScaffold 적용 — InProgressScreen 포함 모든 화면)
- `C:\Users\kala1\designated_driver\pickup_driver_app\app\src\main\AndroidManifest.xml` (Service 등록)
- `C:\Users\kala1\designated_driver\pickup_driver_app\app\src\main\java\com\designated\pickupapp\service\PickupFirebaseMessagingService.kt` (신규)
- pickup_driver_app `MainActivity.kt` (BottomSheetScaffold 적용)
- 각 앱 로그아웃 진입점 (fcmToken 삭제 단계 추가)
- 신규 Chat MVC 파일들 (call_manager 5개, driver_app 6개, pickup_driver_app 6개)

---

## 10. 알려진 위험 + 트레이드오프

1. **pickup_driver_app 인프라 신설 8.5일** — V1.1 분리로 단축 가능
2. **docId-authUid 불일치** — 멤버 화이트리스트 + CF 자동 생성으로 안전하게 우회
3. **메시지 양 증가 시 페이지네이션 (V1.5)** — LazyColumn 1만건 시 메모리 압박, 룸 트림 V1.5
4. **FCM high priority 정당성** — 운영 채널이라 정당, 빈도 폭주 시 V1.5에서 throttle
5. **Optimistic UI 충돌** — 동일 messageId set merge, 서버 createdAt이 정렬 권위
6. **senderName denormalize** — 닉네임 변경 후 옛 메시지는 옛 이름 (카톡과 동일)
7. **CF 비용 추정** — 50 사무실 × 100 메시지/일 = 25만 write/일 ≈ 일 $0.45 + 채팅방 진입 read (50건 × 5명 × 10진입/일 × 50사무실 = 1.25M read/일 ≈ 일 $0.75). 합 ~$1.2/일 = 약 $36/월. 수용 가능
8. **CLAUDE.md 스코프 준수** — `approveOfficeApplication`/`approveDriver` 함수 안에 chat member 생성 단계 추가는 불가피한 보안 필수 변경
9. **PII 90일 보존 한계** — 90일 미만 사고 시 노출 가능. 콜 데이터 정책과 정합. 30일로 짧게 갈지 V1.5 검토
10. **CF cold start** — 첫 메시지 5~10초 지연 가능. minInstances:1 (월 ~$5~10) vs 수용. V1은 0 시작 후 측정
11. **운행 중 BottomSheet 표시로 인한 운전 산만 위험** — 사용자 명시 결정으로 자동 숨김 X. V1.5에서 보이스 자동 읽기 등 안전 보강 별도 트랙
12. **메시지 신고/삭제 부재** — V1: `update, delete: if false`. 부적절 발언 시 매니저 삭제 권한 V2 검토
13. **알림 클릭 → 채팅방 진입 deep link** — V1 작업 범위 (네비게이션 진입점)
14. **Firebase Auth 미가입 사용자** — pending_drivers 단계는 chat member 미생성. 승인 후에만 chat 입장 (의도된 설계)
15. **읽음 표시 부재** — 의도된 단순화 (Firestore write 0). 운영 학습 후 V1.5에 본인 미읽음 카운트만 로컬 추가 검토
16. **시스템 메시지 부재** — 카톡 단톡방의 "X님이 입장했습니다/나갔습니다" 안내 V1에 없음. 사용자는 새 멤버 첫 메시지로만 인지. 사무실 규모 작아 영향 미미. V2 검토

---

## 11. 메모리 저장 (실행 직후)
- PLAN 본문을 `memory/designated_drive/chat_feature_plan_2026-04-28.md`에 보관 (CLAUDE.md "플랜모드 결과 반드시 메모리 저장" 원칙)
- MEMORY.md 최근 달성에 추가
- 별도: 양평 사무실 단톡방 실측 데이터를 `memory/designated_drive/yangpyeong_chat_data_2026-04-28.md`에 보관 (V1.1/V2 결정 시 참조용)
- 별도: V2 후보 "메시지 안에 콜 카드 임베드" 아이디어를 `memory/designated_drive/chat_v2_call_embed_idea_2026-04-28.md`에 보관

---

## 12. 모든 결정 요약

### 데이터/보안
- 사무실당 단톡방 1개, 텍스트만, 90일 자동 삭제
- members 화이트리스트 (CF만 생성/삭제, 클라이언트 write 0)
- messageId = Firestore docId (충돌 0 보장)
- 페이지네이션: 50건 + 위로 50씩
- text 최대 2000자 (FCM 4KB 한계)

### 흐름
- FCM + 로컬 DB 트리거 (Firestore 실시간 리스너 X)
- Optimistic UI 3상태 (SENDING/SENT/FAILED + ✗ 재시도)
- 가입/탈퇴 시 CF가 멤버 자동 동기화

### UI (3 앱 통일)
- BottomSheetScaffold, 3단계 (peek 56dp → 탭 → 절반 50% → 위 끌기 → 95%)
- peek 미리보기: `💬 [발신자]: [메시지]` (카운트 없음)
- 새 메시지 도착: peek 부풀음 + default 알림음 + 진동
- 풀스크린 ChatScreen: 카톡 패턴 (5분 그룹화, 본인 우/타인 좌)
- driver_app 운행 중 자동 숨김 X (운행 중 사용이 핵심 use case)

### 알림
- 신규 `chat_messages` 채널: IMPORTANCE_HIGH + default 알림음 + 진동
- 콜 채널 (IMPORTANCE_MAX + 알람음 + DND 우회)과 차별
- 앱 백그라운드일 때만 시스템 알림. 포그라운드는 BottomSheet 부풀음 활용

### 읽음 표시
- 본인 미읽음 카운트, 타인 "1" 표시 둘 다 V1 OFF
- 새 메시지 인지는 부풀음/알림음/진동으로 처리
- V1.5 운영 후 본인 미읽음 카운트만 로컬 추가 검토 (서버 영향 0)

### 운영
- 90일 PII 자동 삭제 scheduled 함수
- backfillChatMembers 일회성 호출
- 로그아웃 시 fcmToken 삭제 (3 앱)
- minInstances 옵션 V1은 0, 운영 측정 후 결정

### 추정
- **~4.5주** (V1.0 / V1.1 분리 시 V1.0 = ~3주)

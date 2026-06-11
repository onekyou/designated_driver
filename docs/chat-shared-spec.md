# 콜마당 사무실 단톡방 채팅 — 공유 스펙 (SSOT)

> **단일 출처 (Single Source of Truth)** — call_manager · driver_app · pickup_driver_app 3 앱이 본 문서를 보고 동일한 데이터 모델 / FCM 페이로드 / Enum을 정의한다. 코드 복제 패턴이지만 본 문서가 원본.
> 변경 시: 3 앱 + Cloud Functions 코드를 동시에 갱신해야 한다.
> 관련 플랜: `memory/designated_drive/chat_feature_plan_2026-04-28.md`

## 1. Firestore 데이터 모델

### 1.1 메시지 컬렉션 경로
```
provinces/{provinceId}/cities/{cityId}/offices/{officeId}/chatRoom/main/messages/{messageId}
```
- `chatRoom/main`은 단일 문서. V1은 사무실당 1방.
- V2 다중 채널 확장 시 `chatRoom/{roomId}` 추가 가능.

### 1.2 메시지 문서 스키마
| 필드 | 타입 | 설명 |
|---|---|---|
| `id` | string | = messageId. 클라이언트 발급 (`firestore.collection(...).document().id`). docId와 동일 |
| `senderId` | string | Firebase Auth UID |
| `senderName` | string | 발신자 표시 이름 (비정규화). 매니저는 admin 닉네임, 기사는 driver name |
| `senderRole` | enum string | `MANAGER` / `DESIGNATED_DRIVER` / `PICKUP_DRIVER` |
| `text` | string | 메시지 본문. 1~2000자 (UTF-8 한글 3바이트 기준 ~1300자 안전선) |
| `createdAt` | Timestamp | `FieldValue.serverTimestamp()`. 권위 정렬 기준 |
| `clientCreatedAt` | number (epoch ms) | Optimistic UI 임시 정렬용 보조 |
| `status` | enum string | V1은 `"SENT"` 고정 (V2: DELIVERED, READ 등) |

### 1.3 멤버 화이트리스트 (보안용)
```
provinces/{p}/cities/{c}/offices/{o}/chatRoom/main/members/{userId}
```
| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | string | `request.auth.uid`. docId와 동일 |
| `role` | enum string | `MANAGER` / `DESIGNATED_DRIVER` / `PICKUP_DRIVER` |
| `joinedAt` | Timestamp | 멤버 등록 시점 |

⚠️ **lastReadAt 필드 없음**. 읽음 추적은 V1에서 제거 (Firestore write 0). 본인 미읽음 카운트도 V1엔 표시 안 함.

⚠️ **클라이언트는 members 컬렉션에 절대 write 못함**. CF가 admin 권한으로 가입 승인/탈퇴 시 자동 set/delete.

## 2. FCM 페이로드 스펙

### 2.1 새 메시지 푸시 (`type=NEW_CHAT_MESSAGE`)
data 페이로드 키 (모두 string, FCM data 페이로드는 string-only):

| 키 | 값 | 설명 |
|---|---|---|
| `type` | `"NEW_CHAT_MESSAGE"` | 메시지 타입 분기 키 (각 앱 FCM Service when 분기) |
| `messageId` | string | 메시지 docId |
| `senderId` | string | 발신자 UID |
| `senderName` | string | 발신자 이름 |
| `senderRole` | `"MANAGER"` / `"DESIGNATED_DRIVER"` / `"PICKUP_DRIVER"` | |
| `text` | string | 메시지 본문 (≤ 2000자) |
| `createdAt` | string (ISO 8601 또는 epoch ms) | 서버 시간 |
| `clientCreatedAt` | string (epoch ms) | 클라이언트 시간 |
| `provinceId` | string | 사무실 식별 |
| `cityId` | string | |
| `officeId` | string | |

### 2.2 Android notification 옵션
- `notification.channelId` = `"chat_messages"` (콜 채널과 분리)
- `android.priority` = `"high"` (도즈 모드 대응)
- `level` (utils/fcmPayload.ts에서 사용) = `"active"` (콜의 `time-sensitive`보다 한 단계 낮음)
- TTL: 24시간 (`ttlSeconds: 86400`) — 채팅이라 메시지 만료 너무 짧으면 안 됨

### 2.3 페이로드 크기 제한
FCM data 페이로드 한계 = 4KB. 위 11개 키 + text 2000자(UTF-8 한글 ~6KB일 수 있음) → **text를 한글 1300자로 제한 권장**.
구현: 클라이언트 입력바에서 한글 1자=3바이트 가정 카운터. 단순화: 글자 수 2000자 cap → 실제 한글 메시지 길이 그 이하면 안전.

## 3. Kotlin Enum (3 앱 동일 정의)

```kotlin
// 각 앱 ui/chat/ 또는 data/chat/ 패키지에 동일 정의
enum class MessageRole(val firestoreValue: String) {
    MANAGER("MANAGER"),
    DESIGNATED_DRIVER("DESIGNATED_DRIVER"),
    PICKUP_DRIVER("PICKUP_DRIVER");

    companion object {
        fun fromFirestoreValue(value: String): MessageRole? =
            values().firstOrNull { it.firestoreValue == value }
    }
}

enum class SendStatus {
    SENDING,  // Optimistic INSERT, Firestore write 진행 중
    SENT,     // Firestore write 성공
    FAILED    // Firestore write 실패. 사용자 ✗ 탭 시 재시도
}
```

## 4. Kotlin 데이터 클래스 (3 앱 동일 정의)

```kotlin
data class ChatMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val senderRole: MessageRole,
    val text: String,
    val createdAt: Long,         // epoch ms (서버 시간)
    val clientCreatedAt: Long,   // epoch ms (클라이언트 시간)
    val sendStatus: SendStatus = SendStatus.SENT  // 로컬 한정 필드. Firestore에는 status="SENT"만 저장
)
```

## 5. CF driverType ↔ MessageRole 매핑

기존 `approveDriver` 함수가 받는 `driverType`은 한글:
- `"대리기사"` → `senderRole = "DESIGNATED_DRIVER"`
- `"픽업기사"` → `senderRole = "PICKUP_DRIVER"`

매니저는 `approveOfficeApplication` 트리거에서 직접 `"MANAGER"` 설정.

## 6. messageId 생성 규칙

- Kotlin: `firestore.collection("...messages").document().id` (Firebase가 글로벌 unique 보장)
- 클라이언트가 미리 발급 → Optimistic INSERT 시 동일 ID로 로컬 Room INSERT → Firestore set 시 동일 ID 사용 → 중복 방지

## 7. text 검증 규칙

| 단계 | 규칙 |
|---|---|
| 클라이언트 입력바 | 1~2000자, trim 후 빈 문자열 거부 |
| Firestore 보안 규칙 | `text is string && text.size() in 1..2000` |
| Cloud Function | 별도 검증 X (이미 보안 규칙 통과) |

## 8. 페이지네이션

- 최초 진입: `.orderBy("createdAt", DESC).limit(50).get()`
- 위로 스크롤: `.orderBy("createdAt", DESC).startAfter(lastDoc).limit(50).get()`
- 로컬 Room에 누적 1000건 초과 시 오래된 것부터 트림 (V1.5)

## 9. 메시지 보존 정책

- 90일 (Cloud Function `scheduledChatMessageCleanup` 매일 새벽 4시 KST 실행)
- `createdAt < now - 90일` 문서를 collectionGroup 쿼리로 일괄 삭제 (500건 batch)
- 근거: 채팅 본문에 고객 PII 포함 가능성 (전화/주소/차량번호/외상정보 등)

## 10. 알림 채널 사양 (3 앱 동일)

```kotlin
NotificationChannel(
    id = "chat_messages",
    name = "단톡방 메시지",
    importance = NotificationManager.IMPORTANCE_HIGH  // 콜 채널 MAX와 차별
).apply {
    description = "사무실 단톡방 메시지 알림"
    enableVibration(true)
    setSound(
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
        AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .build()
    )
    setBypassDnd(false)  // 콜과 차별 (콜은 true)
}
```

## 11. BottomSheet UI 사양 (3 앱 통일)

| 상태 | 높이 | 트리거 |
|---|---|---|
| `Hidden` | 0 | V1 미사용 (driver_app 운행 중 자동 숨김 X) |
| `peek` | 56dp | 기본 상태. 카드 1개 (마지막 메시지 미리보기) |
| `peek (확장 부풀음)` | 80dp | 새 메시지 도착 시 1초간 + 알림음 + 진동, 그 후 56dp 복귀 |
| `PartiallyExpanded` | 50% | 사용자가 카드 탭 또는 위로 끌기 |
| `Expanded` | 95% | 사용자가 PartiallyExpanded에서 더 위로 끌기 |

peek 미리보기 텍스트 형식: `💬 [발신자]: [메시지 1줄]` (카운트 배지 없음)

## 12. 채팅 메시지 UI 사양

- LazyColumn `reverseLayout = true` (최신 메시지 하단)
- 같은 발신자가 5분 안에 연속 메시지 → 첫 메시지에만 발신자 이름 + 역할 표시. 이후 메시지는 말풍선만
- 본인 메시지: 우측 정렬 + 색상 차별 (예: 파스텔 노란색 — 카톡 패턴)
- 타인 메시지: 좌측 정렬 + 흰색 또는 회색
- 시간 표시: 메시지 그룹의 마지막 메시지 옆에만 (예: 오후 8:32)
- 본인 메시지 sendStatus 표시: SENDING ✓회색 / SENT ✓파랑 / FAILED ✗빨강

## 13. V1 미포함 (V1.5/V2 검토 후보)

- 이미지/사진 첨부 (V2)
- 음성 메시지 (V2+)
- 메시지 답장/인용 (V2)
- 메시지 삭제/신고 (V2)
- 본인 미읽음 카운트 (V1.5 운영 학습 후)
- ~~시스템 메시지 (입퇴장 안내 등) (V2)~~ → **구현됨 (블랙박스 9-B, §15 참조)**
- 콜 카드 임베드 (V2 차별 가치, `chat_v2_call_embed_idea_2026-04-28.md`)
- 외부 게스트 임시 초대 (V2)
- 운행 중 음성 자동 읽기 (V1.5 안전 보강)
- 운행 중 IN_PROGRESS 자동 무음 옵션 (V1.5)

## 14. 구현 체크리스트 (3 앱 공통)

각 앱이 본 스펙을 따라 구현 시:
- [ ] `MessageRole` enum 동일 정의
- [ ] `SendStatus` enum 동일 정의
- [ ] `ChatMessage` 데이터 클래스 동일 필드
- [ ] FCM 페이로드 키 (1.2.1) 모두 처리
- [ ] `chat_messages` NotificationChannel 동일 설정
- [ ] BottomSheetScaffold 3단계 (peek/PartiallyExpanded/Expanded)
- [ ] messageId Firestore docId 사용
- [ ] text 길이 검증 (1~2000자)
- [ ] Optimistic INSERT (sendStatus=SENDING) → Firestore set → 성공 시 SENT, 실패 시 FAILED
- [ ] 본인 senderId 메시지는 FCM 받아도 로컬 INSERT skip (이미 Optimistic INSERT 됨)
- [ ] 로그아웃 시 fcmToken 삭제

## 15. 시스템 메시지 (블랙박스 9-B)

콜 흐름·기사 상태·정산 이벤트를 단톡방에 **무음 자동 기록**(블랙박스 = 평소 안 봄, 분쟁·놓침 시만 봄).

### 16.1 데이터
- 메시지 문서에 `type` 필드 추가: 일반 메시지 = 미설정(또는 `"user"`), 시스템 메시지 = `"system"`, `senderRole = "SYSTEM"`.
- **작성 주체 = Cloud Functions(Admin SDK)만.** 클라는 절대 `type:"system"` write 못함 — `firestore.rules`가 메시지 create 시 `type != "system"` && `senderRole != "SYSTEM"` 강제(스푸핑 차단). CF는 rules 우회.
- 헬퍼 = `functions/src/handlers/chat.ts` `postSystemMessage(provinceId, cityId, officeId, text)`. best-effort(try/catch) — 콜 운영 비파괴.

### 16.2 트리거 (CF)
- 콜 상태 전이: `onCallStatusChanged`(배차/수락/운행시작/**운행완료(AWAITING_SETTLEMENT)**/**정산완료(COMPLETED)**/취소/보류). `handledByManager`(직접운행) 제외.
- 신규콜: `sendNewCallNotification`("콜 들어옴", handledByManager 제외).
- 기사 출퇴근: `onDriverStatusChange`(OFFLINE↔ONLINE/WAITING만).
- 예약: `oncallreserved`(RESERVED 진입). 공유콜: 등록(`onSharedCallCreated`)·수임(`onSharedCallClaimed`, wallet revert 통과 후). 정산 제출: `onDriverSettlementSubmitted`.

### 16.3 알림 / 렌더
- **무음**: 시스템 메시지 FCM은 `chatType` 키로 분기 → 클라가 소리·배너 없이 Room INSERT만(`playChatSound` 미호출).
- 렌더: `ChatMessageRow`에서 `type=="system"` → `SystemMessageBubble`(중앙 정렬·회색 버블·고정 짙은 회색 글씨 `0xFF555555`, 발신자/정렬/시간그룹 무시).
- **필터 토글(Commit 3)**: `ChatBottomSheetContent`(실 render site) expanded 상단 헤더에 표시/숨김 토글(`rememberSaveable`, **기본 ON**·비영속). OFF면 `messages.filter { type != "system" }`로 사람 대화만. 3앱 동일.
- 적용 앱: call_manager · driver_app · pickup_driver_app 3앱. (풀스크린 `ChatScreen`은 미사용 죽은 코드 — `ChatBottomSheetContent`만 실사용.)

## 16. 참고

- 플랜 본문: `memory/designated_drive/chat_feature_plan_2026-04-28.md`
- 양평 단톡방 실측 데이터: `memory/designated_drive/yangpyeong_chat_data_2026-04-28.md`
- V2 콜 카드 임베드 아이디어: `memory/designated_drive/chat_v2_call_embed_idea_2026-04-28.md`

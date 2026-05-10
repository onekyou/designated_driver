# ENUMS — 상태·타입 enum 매핑 (Phase 1 MVP)

> **목적**: 기사앱에서 사용하는 모든 enum을 Swift로 이관할 때 필요한 raw value·Codable 스펙 정리.
> **핵심 원칙**: Firestore 필드 문자열과 **raw value를 완전히 일치**시킬 것. Kotlin `enum class(val value: String)` 관용구를 Swift `enum: String`으로 1:1 이관.

---

## 원본 소스

| Swift enum | Kotlin 파일:라인 | Firestore 사용 여부 |
|---|---|---|
| `CallStatus` | `model/CallInfo.kt:11-25` | Y (`status` 필드) |
| `DriverStatus` | `model/DriverStatus.kt:3-33` | Y (`status` 필드) |
| `DailySettlementStatus` | `data/settlement/SettlementModels.kt:288-293` | Y (`dailySettlement.status`) |
| `CarryOverStatus` | `data/settlement/SettlementModels.kt:279-283` | Y (`carryOver.status`) |
| `SyncStatus` | `data/settlement/SettlementModels.kt:224-229` | N (로컬 전용, R1 SwiftData) |
| `DriverApprovalStatus` | `model/DriverModel.kt:9-13` | Y (`approvalStatus` 필드) |
| `PaymentMethod` | (Kotlin 코드에 enum 정의 없음, 문자열 리터럴 사용) | Y (`paymentMethod` 필드) |
| `CustomerGrade` | (String "BRONZE"/... 만 사용) | Y (`grade` 필드) |
| `CallStatusConstant` | `data/Constants.kt:41-47` (STATUS_* 상수) | — (CallStatus와 통합됨) |

---

## 요약

- **CallStatus**: Kotlin은 `(firestoreValue, displayName)` 2파라미터. Swift는 raw value + `var displayName: String { ... }` computed property로 분리.
- **DriverStatus**: Kotlin `(value: String)`. Swift `String raw value`로 직접 매핑. 9값(UNKNOWN 포함).
- **DailySettlementStatus / CarryOverStatus / SyncStatus / DriverApprovalStatus**: Kotlin raw enum class(파라미터 없음). `.name`이 직렬화 값으로 쓰임. Swift는 raw value로 명시 정의.
- **PaymentMethod**: Kotlin은 **enum으로 정의 안 됨**. 코드 전반에서 문자열 비교(`"현금"`, `"이체"`, `"외상"`, `"현금+포인트"`, `"포인트"`). Swift는 **신규 enum 도입 권장** (버그 방지). Firestore에는 한글 raw value 그대로 씀.
- **CustomerGrade**: 동일하게 문자열만 사용. enum 도입 권장.
- **취소 상태 문자열**: `CANCELED` / `CANCELLED_BY_DRIVER` / `CANCELLED_BY_CUSTOMER` — Kotlin CallStatus enum에는 `CANCELLED`(value=`CANCELED`) 1개만 있고, `BY_DRIVER`/`BY_CUSTOMER`는 **문자열 리터럴**로만 쓰임. Swift도 enum 확장이 필요.

---

## 매핑 테이블

### CallStatus
Kotlin `model/CallInfo.kt:11-25`:

| Swift case | raw value (Firestore) | displayName |
|---|---|---|
| `.waiting` | `"WAITING"` | "대기중" |
| `.assigned` | `"ASSIGNED"` | "배차완료" |
| `.reserved` | `"RESERVED"` | "예약" (운행중 기사에게 다음 콜 약속, 5/10 추가) |
| `.accepted` | `"ACCEPTED"` | "수락" |
| `.inProgress` | `"IN_PROGRESS"` | "운행중" |
| `.awaitingSettlement` | `"AWAITING_SETTLEMENT"` | "정산대기" |
| `.completed` | `"COMPLETED"` | "운행완료" |
| `.canceled` | `"CANCELED"` | "취소" |
| `.cancelledByDriver` | `"CANCELLED_BY_DRIVER"` | "기사 취소" (신규) |
| `.cancelledByCustomer` | `"CANCELLED_BY_CUSTOMER"` | "고객 취소" (신규) |
| `.sharedWaiting` | `"SHARED_WAITING"` | "공유콜 대기" (공유콜 — 기사앱에서 수신만) |
| `.claimed` | `"CLAIMED"` | "수임됨" (공유콜) |

**주의**: Kotlin enum에는 `CANCELLED_BY_DRIVER`/`CANCELLED_BY_CUSTOMER`/`SHARED_WAITING`/`CLAIMED`이 없지만, 실제 Firestore·CF·cancelTrip 코드에서 raw string으로 사용됨(`DriverViewModel.kt:542`, `handleCallCancelled` 경로). Swift에서는 **enum 확장으로 명시**해 문자열 오타 방지.

`fromRaw`: `CallStatus(rawValue: string) ?? .waiting` (Kotlin fallback 동일).

---

### DriverStatus
Kotlin `model/DriverStatus.kt:3-33` (9값):

| Swift case | raw value | displayName |
|---|---|---|
| `.online` | `"ONLINE"` | "온라인" |
| `.offline` | `"OFFLINE"` | "오프라인" |
| `.waiting` | `"WAITING"` | "대기중" |
| `.assigned` | `"ASSIGNED"` | "배정됨" |
| `.accepted` | `"ACCEPTED"` | "수락함" |
| `.preparing` | `"PREPARING"` | "운행준비" |
| `.onTrip` | `"ON_TRIP"` | "운행중" |
| `.pendingConfirm` | `"PENDING_CONFIRM"` | "정산대기" |
| `.unknown` | `"UNKNOWN"` | "알수없음" |

`fromString(nilable)` fallback → `.unknown` (Kotlin 동일, case-insensitive 매칭은 Swift에서 rawValue 직접 매핑으로 단순화 권장).

---

### DailySettlementStatus
Kotlin `SettlementModels.kt:288-293` (4값, enum.name 직렬화):

| Swift case | raw value |
|---|---|
| `.working` | `"WORKING"` |
| `.pendingConfirm` | `"PENDING_CONFIRM"` |
| `.confirmed` | `"CONFIRMED"` |
| `.rejected` | `"REJECTED"` |

fromMap fallback → `.working`.

---

### CarryOverStatus
Kotlin `SettlementModels.kt:279-283` (3값):

| Swift case | raw value |
|---|---|
| `.pending` | `"PENDING"` |
| `.transferred` | `"TRANSFERRED"` |
| `.settled` | `"SETTLED"` |

fromMap fallback → `.pending`.

---

### SyncStatus (R1, 로컬 전용)
Kotlin `SettlementModels.kt:224-229` (4값):

| Swift case | raw value |
|---|---|
| `.pending` | `"PENDING"` |
| `.syncing` | `"SYNCING"` |
| `.synced` | `"SYNCED"` |
| `.failed` | `"FAILED"` |

SwiftData `PendingSync.status`에 String으로 저장.

---

### DriverApprovalStatus
Kotlin `model/DriverModel.kt:9-13` (3값):

| Swift case | raw value |
|---|---|
| `.pending` | `"PENDING"` |
| `.approved` | `"APPROVED"` |
| `.rejected` | `"REJECTED"` |

---

### PaymentMethod (신규 enum 도입 권장)
Kotlin 코드 내 문자열 리터럴만 존재. `DriverViewModel.kt:699-716`, `SettlementTotals.addCall` 등에서 비교:

| Swift case | raw value (한글, Firestore 저장값) |
|---|---|
| `.cash` | `"현금"` |
| `.transfer` | `"이체"` |
| `.credit` | `"외상"` |
| `.points` | `"포인트"` |
| `.cashPlusPoints` | `"현금+포인트"` |

**근거**: 한글 값이 Firestore에 그대로 저장되어 왔고, 콜매니저·손님앱·CF 모두 동일 비교. 변경 시 파급 크므로 raw value는 한글 유지.

---

### CustomerGrade (신규 enum 도입 권장)
SHARED_LOGIC §10 "고객 포인트/등급"의 4단계. Kotlin `CustomerPoints.grade: String`:

| Swift case | raw value | 적립률 |
|---|---|---|
| `.bronze` | `"BRONZE"` | 3% |
| `.silver` | `"SILVER"` | 5% |
| `.gold` | `"GOLD"` | 7% |
| `.vip` | `"VIP"` | 9% |

**적립률은 CF 측 로직**이므로 기사앱에서는 표시용으로만 사용. Swift enum에 `earnRate` computed property 추가 가능(읽기 전용).

---

### Broadcast Action (LocalBroadcast → iOS Notification.Name)
Kotlin `Constants.kt:56-60` (5개). Swift는 `NotificationCenter.default`의 `Notification.Name` 상수로 이관:

| Swift Notification.Name | Kotlin action |
|---|---|
| `.showCallDialog` | `ACTION_SHOW_CALL_DIALOG` |
| `.callCancelled` | `ACTION_CALL_CANCELLED` |
| `.settlementFinalized` | `ACTION_SETTLEMENT_FINALIZED` |
| `.settlementConfirmed` | `ACTION_SETTLEMENT_CONFIRMED` |
| `.settlementRejected` | `ACTION_SETTLEMENT_REJECTED` |

---

### FCM Message Type
SHARED_LOGIC §5 명시된 6종. 메시지 `data["type"]`으로 구분. Swift enum 도입 권장:

| Swift case | raw value |
|---|---|
| `.callAssigned` | `"call_assigned"` |
| `.callReserved` | `"call_reserved"` (5/10 추가, 운행중 기사에게 예약 콜 알림) |
| `.callCancelled` | `"call_cancelled"` |
| `.settlementFinalized` | `"SETTLEMENT_FINALIZED"` |
| `.settlementConfirmed` | `"SETTLEMENT_CONFIRMED"` |
| `.settlementRejected` | `"SETTLEMENT_REJECTED"` |
| `.carryoverTransferred` | `"CARRYOVER_TRANSFERRED"` |

**주의**: `call_assigned`/`call_cancelled`만 소문자+언더바, 정산/이월금 계열은 전부 대문자. Kotlin 원본 그대로 유지(`MyFirebaseMessagingService.kt:148, 177, 189, 207, 218, 229`).

---

## 의사 스펙 (Swift)

```swift
// MARK: - CallStatus
enum CallStatus: String, Codable, CaseIterable, Sendable {
    case waiting = "WAITING"
    case assigned = "ASSIGNED"
    case reserved = "RESERVED"
    case accepted = "ACCEPTED"
    case inProgress = "IN_PROGRESS"
    case awaitingSettlement = "AWAITING_SETTLEMENT"
    case completed = "COMPLETED"
    case canceled = "CANCELED"
    case cancelledByDriver = "CANCELLED_BY_DRIVER"
    case cancelledByCustomer = "CANCELLED_BY_CUSTOMER"
    case sharedWaiting = "SHARED_WAITING"
    case claimed = "CLAIMED"

    var displayName: String {
        switch self {
        case .waiting: return "대기중"
        case .assigned: return "배차완료"
        case .reserved: return "예약"
        case .accepted: return "수락"
        case .inProgress: return "운행중"
        case .awaitingSettlement: return "정산대기"
        case .completed: return "운행완료"
        case .canceled: return "취소"
        case .cancelledByDriver: return "기사 취소"
        case .cancelledByCustomer: return "고객 취소"
        case .sharedWaiting: return "공유콜 대기"
        case .claimed: return "수임됨"
        }
    }
}

// MARK: - DriverStatus
enum DriverStatus: String, Codable, CaseIterable, Sendable {
    case online = "ONLINE"
    case offline = "OFFLINE"
    case waiting = "WAITING"
    case assigned = "ASSIGNED"
    case accepted = "ACCEPTED"
    case preparing = "PREPARING"
    case onTrip = "ON_TRIP"
    case pendingConfirm = "PENDING_CONFIRM"
    case unknown = "UNKNOWN"

    var displayName: String {
        switch self {
        case .online: return "온라인"
        case .offline: return "오프라인"
        case .waiting: return "대기중"
        case .assigned: return "배정됨"
        case .accepted: return "수락함"
        case .preparing: return "운행준비"
        case .onTrip: return "운행중"
        case .pendingConfirm: return "정산대기"
        case .unknown: return "알수없음"
        }
    }

    /// String → enum 안전 변환. nil/미정의 raw value는 .unknown.
    /// 주의: 이름 `init(from:)`은 Decodable 합성과 충돌 위험이 있어
    ///       static factory 사용. Codable 동작은 raw value 자동 매핑이 처리.
    static func from(_ string: String?) -> DriverStatus {
        DriverStatus(rawValue: string ?? "") ?? .unknown
    }
}

// MARK: - DailySettlementStatus
enum DailySettlementStatus: String, Codable, Sendable {
    case working = "WORKING"
    case pendingConfirm = "PENDING_CONFIRM"
    case confirmed = "CONFIRMED"
    case rejected = "REJECTED"
}

// MARK: - CarryOverStatus
enum CarryOverStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case transferred = "TRANSFERRED"
    case settled = "SETTLED"
}

// MARK: - SyncStatus (R1)
enum SyncStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case syncing = "SYNCING"
    case synced = "SYNCED"
    case failed = "FAILED"
}

// MARK: - DriverApprovalStatus
enum DriverApprovalStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case approved = "APPROVED"
    case rejected = "REJECTED"
}

// MARK: - PaymentMethod (신규)
// Firestore에 한글 raw value 그대로 저장 — Kotlin/CF/콜매니저 모두 동일.
// "+" 문자는 String raw value에 안전하게 사용 가능.
enum PaymentMethod: String, Codable, CaseIterable, Sendable {
    case cash = "현금"
    case transfer = "이체"
    case credit = "외상"
    case points = "포인트"
    case cashPlusPoints = "현금+포인트"
}

// MARK: - CustomerGrade (신규)
// 적립률은 표시용. 실 계산은 CF가 수행 (notifyCustomerOnComplete).
// 부동소수 비교 회피 위해 percentage(Int)도 노출.
enum CustomerGrade: String, Codable, Sendable {
    case bronze = "BRONZE"
    case silver = "SILVER"
    case gold = "GOLD"
    case vip = "VIP"

    var earnRatePercent: Int {
        switch self {
        case .bronze: return 3
        case .silver: return 5
        case .gold: return 7
        case .vip: return 9
        }
    }

    var earnRate: Double { Double(earnRatePercent) / 100.0 }
}

// MARK: - FCMMessageType
// 주의: Kotlin 원본이 call_*는 소문자+언더바, SETTLEMENT_*/CARRYOVER_*는 대문자.
//      절대 통일하지 말 것 — CF/콜매니저와 raw value 일치 필수.
enum FCMMessageType: String, Codable, Sendable {
    case callAssigned = "call_assigned"
    case callReserved = "call_reserved"
    case callCancelled = "call_cancelled"
    case settlementFinalized = "SETTLEMENT_FINALIZED"
    case settlementConfirmed = "SETTLEMENT_CONFIRMED"
    case settlementRejected = "SETTLEMENT_REJECTED"
    case carryoverTransferred = "CARRYOVER_TRANSFERRED"
}

// MARK: - Notification.Name (LocalBroadcast 등가)
extension Notification.Name {
    static let showCallDialog        = Notification.Name("com.designated.driverapp.showCallDialog")
    static let callCancelled         = Notification.Name("com.designated.driverapp.callCancelled")
    static let settlementFinalized   = Notification.Name("com.designated.driverapp.settlementFinalized")
    static let settlementConfirmed   = Notification.Name("com.designated.driverapp.settlementConfirmed")
    static let settlementRejected    = Notification.Name("com.designated.driverapp.settlementRejected")
}
```

---

## iOS 특수 사항

1. **Raw value 변경 절대 금지**: Firestore·CF·콜매니저 모두 같은 문자열 기대. 특히 PaymentMethod 한글 값, FCM Type의 대소문자 혼용(`call_assigned` vs `SETTLEMENT_FINALIZED`).
2. **Codable 자동 지원**: `String` raw value enum은 Swift Codable 자동 대응. 커스텀 init 필요 없음. 한글·`+`·기타 ASCII-외 문자도 raw value에 안전.
3. **Fallback 동작**: Kotlin은 `entries.find { ... } ?: WAITING` 패턴. Swift `RawRepresentable` init은 unknown 값에서 `nil` 반환 → 호출부에서 `?? .waiting` 처리. **Decode 시 unknown raw value를 만나면 throw됨** — Firestore 서버에 신규 상태가 추가됐는데 앱이 못 따라간 경우 모델 디코드 실패 가능. 대비: 호출부에서 Codable 실패 처리 + analytics 로깅
4. **`init(from:)` 명명 충돌 회피**: `init(from string: String?)`는 Swift `Decodable.init(from decoder:)` 합성과 시그니처는 다르나 가독성 혼동 위험. **static factory `from(_:)`로 변경**(DriverStatus). Codable은 raw value 매핑이 자동 처리하므로 별도 init 불요.
5. **`Sendable` 명시**: Swift 6 strict concurrency / actor 경계에서 enum이 Sendable이어야 안전 전송. raw value String enum은 자동 Sendable이지만 문서 명시로 의도 표현.
6. **CANCELLED_BY_DRIVER/CUSTOMER Kotlin enum 미정의**: iOS 이관 시 enum에 명시함으로써 **문자열 오타 제로 보장**.
7. **SHARED_WAITING/CLAIMED**: 공유콜 플래그. 기사앱에서 직접 상태전이는 하지 않지만 조회에서 만날 수 있음 → enum 포함.
8. **`Notification.Name` 문자열 네임스페이스**: Android 액션과 1:1로 일치시킬 필요 없음. 하지만 Crashlytics·로깅에서 식별 용이하도록 동일 prefix(`com.designated.driverapp.`) 유지 권장.
9. **CallStatus 철자 혼용 주의**: Kotlin 원본이 `CANCELED`(미국식 단일 L)와 `CANCELLED_BY_DRIVER`(영국식 더블 L)를 섞어 사용. **raw value는 절대 통일하지 말 것** — 서버·CF가 기대하는 문자열 그대로. Swift case 이름은 raw value를 따라 `canceled` / `cancelledByDriver`로 분기 표기됨(혼란 시 주석으로 명시).

---

## 체크리스트

- [ ] CallStatus enum 11 case (+ cancelledByDriver/Customer/sharedWaiting/claimed 확장)
- [ ] DriverStatus enum 9 case + static factory `from(_:)` (init 충돌 회피)
- [ ] 모든 enum에 `Sendable` 채택 (Swift 6 strict concurrency 대비)
- [ ] DailySettlementStatus / CarryOverStatus / SyncStatus / DriverApprovalStatus
- [ ] PaymentMethod enum 신규 (한글 rawValue 5종)
- [ ] CustomerGrade enum 신규 + `earnRatePercent`(Int) + `earnRate`(Double) computed properties
- [ ] FCMMessageType `Codable` 채택 (기본 dictionary 매핑 외 확장 대비)
- [ ] FCMMessageType enum (rawValue 대소문자 원본 준수)
- [ ] Notification.Name 5개 static 상수
- [ ] CallStatus·DriverStatus에 `displayName` computed property
- [ ] Codable 자동 적용 확인 (raw value String이면 자동)
- [ ] PaymentMethod 비교 코드가 모두 enum 사용으로 교체되는지 점검 (VIEWMODELS.md·FIRESTORE.md 구현 시)

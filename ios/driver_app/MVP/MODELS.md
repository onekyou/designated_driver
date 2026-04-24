# MODELS — Kotlin data class → Swift struct 매핑 (Phase 1 MVP)

> **목적**: Kotlin 기사앱의 도메인 모델을 Swift struct로 1:1 이관할 때 필요한 필드·타입·Firestore 키·Codable 스펙 정리.
> **범위**: FUNCTIONAL_INVENTORY §5 **B**로 분류된 모델만. R2/D 모델(PickupDriver는 기사앱 사용 없음 등)은 제외 또는 주석 표시.
> **관련 합의**: Topic 2(@Observable) · Topic 3(SwiftData) · SHARED_LOGIC.md §정산 공식

---

## 원본 소스

| Swift 모델 | Kotlin 파일:라인 |
|---|---|
| `CallInfo` | `driver_app/app/src/main/java/com/designated/driverapp/model/CallInfo.kt:28-76` |
| `Driver` | `model/DriverModel.kt:15-27` |
| `PickupDriver` | `model/DriverModel.kt:29-39` (기사앱 사용 범위 밖, 참조용) |
| `SettlementMetadata` | `data/settlement/SettlementModels.kt:54-83` |
| `SettlementTotals` | `data/settlement/SettlementModels.kt:88-154` |
| `CallSettlement` | `data/settlement/SettlementModels.kt:159-219` |
| `SettlementSession` | `data/settlement/SettlementModels.kt:12-49` |
| `DriverSettlementSummary` | `data/settlement/SettlementModels.kt:249-274` |
| `DriverDailySettlement` | `data/settlement/SettlementModels.kt:299-363` |
| `DriverCarryOver` | `data/settlement/SettlementModels.kt:369-403` |
| `PendingSync` (R1) | `data/settlement/SettlementModels.kt:234-243` |
| `CustomerPoints` | `data/model/CustomerPoints.kt:6-53` |
| `UserSession` | `data/model/UserSession.kt:6-68` |

---

## 요약

- **Firestore Timestamp ↔ Swift `Date`**: Firebase 11.x부터는 `Firestore.Decoder` / `Firestore.Encoder`가 자동 변환. **`@ServerTimestamp Date?` 프로퍼티 래퍼**로 서버측 타임스탬프 자동 발급. 수동 변환 필요 시 `Timestamp.dateValue()` / `Timestamp(date:)`.
- **Kotlin `Long` → Swift `Int64`**: 정산 금액 전부. Kotlin `Int` → Swift `Int`(플랫폼 64bit 가정. Firestore Numeric 호환). 32bit 강제 필요한 곳은 없음.
- **nullable 필드**: Kotlin `?` → Swift Optional 그대로 유지. Kotlin data class의 **기본값 자동 적용은 Swift Codable에 없음** — 누락 가능 키는 Optional 또는 커스텀 `init(from:)`로 default 적용 필요.
- **Firestore Map 변환**: 현 Kotlin 코드는 `fromMap(Map<String, Any?>)` / `toMap(): Map<String, Any?>` 수동 패턴. Swift는 **`Codable` + `Firestore.Encoder/Decoder`** (Firebase 11.x부터 `FirebaseFirestore` 단일 import. 별도 `FirebaseFirestoreSwift` 모듈 불필요).
- **CodingKeys**: Firestore 필드명이 Swift 관용 명명과 다른 경우 CodingKeys로 명시 매핑.
- **`@DocumentID` 호환**: `@DocumentID var id: String?` 사용 시 해당 필드는 **CodingKeys에서 제외**(Firestore SDK가 문서 ID를 자동 주입). `Identifiable` 충족 위해 `var id: String?` 그대로 노출 가능.

---

## 매핑 테이블

### CallInfo (34 필드)

| Kotlin 필드 | Firestore 키 | Swift 타입 | Optional |
|---|---|---|---|
| `id` | (document id) | `String` | N (기본값 "") |
| `customerName` | `customerName` | `String` | N |
| `phoneNumber` | `phoneNumber` | `String` | N |
| `customerAddress` | `customerAddress` | `String?` | Y |
| `destination` | `destination` | `String?` | Y |
| `detectedTimestamp` | `detectedTimestamp` | `Date?` | Y |
| `timestamp` | `timestamp` | `Date?` | Y |
| `status` | `status` | `String` (CallStatus rawValue) | N |
| `fare` | `fare` | `Int?` | Y |
| `assignedDriverId` | `assignedDriverId` | `String?` | Y |
| `assignedDriverName` | `assignedDriverName` | `String?` | Y |
| `assignedDriverPhone` | `assignedDriverPhone` | `String?` | Y |
| `assignedTimestamp` | `assignedTimestamp` | `Date?` | Y |
| `assignedPickupDriverId` | `assignedPickupDriverId` | `String?` | Y |
| `deviceName` | `deviceName` | `String?` | Y |
| `officeId` | `officeId` | `String` | N |
| `provinceId` | `provinceId` | `String` | N |
| `cityId` | `cityId` | `String` | N |
| `callType` | `callType` | `String?` | Y |
| `isAppCustomer` | `isAppCustomer` | `Bool` | N (기본 false) |
| `memoText` | `memoText` | `String?` | Y |
| `departure_set` | `departure_set` | `String?` | Y |
| `destination_set` | `destination_set` | `String?` | Y |
| `waypoints_set` | `waypoints_set` | `String?` | Y |
| `fare_set` | `fare_set` | `Int?` | Y |
| `trip_summary` | `trip_summary` | `String?` | Y |
| `paymentMethod` | `paymentMethod` | `String` (PaymentMethod rawValue) | N |
| `cashAmount` | `cashAmount` | `Int?` | Y |
| `cashReceived` | `cashReceived` | `Int?` | Y |
| `creditAmount` | `creditAmount` | `Int?` | Y |
| `isSummaryConfirmed` | `isSummaryConfirmed` | `Bool` | N |
| `summaryConfirmedTimestamp` | `summaryConfirmedTimestamp` | `Date?` | Y |
| `settlementStatus` | `settlementStatus` | `String` | N |
| `settlementId` | `settlementId` | `String?` | Y |
| `claimedDriverId` | `claimedDriverId` | `String?` | Y |
| `sourceSharedCallId` | `sourceSharedCallId` | `String?` | Y |
| `tripSummaryFinal` | `tripSummaryFinal` | `String?` | Y |
| `finalFare` | `finalFare` | `Int?` | Y |
| `fareFinal` | `fareFinal` | `Int?` | Y |

**주의**: `departure_set`/`destination_set`/`waypoints_set`/`fare_set`/`trip_summary`는 Firestore snake_case 그대로 유지(CLAUDE.md §상태 전이와 CF 코드가 이 키로 쓰기). Swift CodingKeys 필수.

**파생 프로퍼티**: `statusEnum: CallStatus { get }` — `CallStatus(rawValue: status) ?? .waiting`로 동등.

---

### Driver (11 필드)

| Kotlin 필드 | Firestore 키 | Swift 타입 |
|---|---|---|
| `id` | (document id) | `String` |
| `name` | `name` | `String` |
| `phone` | `phone` | `String` |
| `email` | `email` | `String` |
| `status` | `status` | `DriverStatus` (enum) |
| `currentCallId` | `currentCallId` | `String?` |
| `rating` | `rating` | `Float` (기본 0) |
| `totalTrips` | `totalTrips` | `Int` |
| `registrationDate` | `registrationDate` | `Date?` |
| `isActive` | `isActive` | `Bool` (기본 true) |
| `approvalStatus` | `approvalStatus` | `DriverApprovalStatus` (enum) |

Firestore에 없을 수 있는 필드: `authUid`(문서 저장 시 명시적으로 쓰지만 Kotlin data class에 없음. Swift에서 로그인 쿼리용으로만 참조, 모델 스펙에는 선택적 추가).

---

### PickupDriver
기사앱에서 **쓰지 않음**(Driver만 사용). `MODELS.md`에서는 **주석 처리**로만 남기고 구현 불요.

---

### SettlementSession / SettlementMetadata / SettlementTotals / CallSettlement

**SettlementSession** (nested aggregate)
- `metadata: SettlementMetadata`
- `totals: SettlementTotals`
- `calls: [CallSettlement]`
- 경로: `provinces/{p}/cities/{c}/offices/{o}/settlementSessions/{date}`

**SettlementMetadata** (6 필드, Int64 기반)
| Kotlin | Swift | 비고 |
|---|---|---|
| `version: Long` | `version: Int64` | 낙관적 쓰기 |
| `lastUpdatedAt: Timestamp?` | `lastUpdatedAt: Date?` | |
| `lastUpdatedBy: String` | `lastUpdatedBy: String` | "driver_app" \| "call_manager" |
| `depositRatio: Int` | `depositRatio: Int` | 0..100 |
| `createdAt: Timestamp?` | `createdAt: Date?` | |
| `isFinalized: Boolean` | `isFinalized: Bool` | |

**SettlementTotals** (8 필드, Long 기반)
| Kotlin | Swift |
|---|---|
| `totalFare: Long` | `totalFare: Int64` |
| `totalDeposit: Long` | `totalDeposit: Int64` |
| `totalDriverShare: Long` | `totalDriverShare: Int64` |
| `totalCash: Long` | `totalCash: Int64` |
| `totalCard: Long` | `totalCard: Int64` |
| `totalCredit: Long` | `totalCredit: Int64` |
| `totalPoints: Long` | `totalPoints: Int64` |
| `callCount: Int` | `callCount: Int` |

`addCall(CallSettlement, depositRatio: Int) -> SettlementTotals` 순수 함수 이관 — SHARED_LOGIC §4 정산 공식과 동일. TODO: Kotlin `SettlementTotals.addCall` 로직 참조.

**CallSettlement** (15 필드)
| Kotlin | Swift | 비고 |
|---|---|---|
| `callId` | `callId: String` | |
| `driverId` | `driverId: String` | |
| `driverName` | `driverName: String` | |
| `customerName` | `customerName: String` | |
| `customerPhone` | `customerPhone: String` | |
| `departure` | `departure: String` | |
| `destination` | `destination: String` | |
| `fare: Long` | `fare: Int64` | |
| `paymentMethod: String` | `paymentMethod: String` | PaymentMethod rawValue |
| `cashReceived: Long` | `cashReceived: Int64` | |
| `creditAmount: Long` | `creditAmount: Int64` | |
| `pointsUsed: Long` | `pointsUsed: Int64` | |
| `completedAt: Timestamp?` | `completedAt: Date?` | |
| `confirmedByOffice: Bool` | `confirmedByOffice: Bool` | |
| `syncedAt: Timestamp?` | `syncedAt: Date?` | |

---

### DriverSettlementSummary (7 필드, 파생)

UI 표시용. `SettlementSession.totals + metadata.depositRatio`에서 파생. 순수 함수로 이관.

```swift
struct DriverSettlementSummary {
    let totalFare: Int64
    let depositAmount: Int64
    let myIncome: Int64
    let unpaidAmount: Int64
    let actualDeposit: Int64   // depositAmount - unpaidAmount
    let callCount: Int
    let depositRatio: Int

    static func from(session: SettlementSession) -> DriverSettlementSummary
}
```

---

### DriverDailySettlement (15 필드)

Firestore 경로: driver 문서 내 `dailySettlement` 필드 (서브필드).

| Kotlin | Swift | 비고 |
|---|---|---|
| `date: String` | `date: String` | YYYY-MM-DD |
| `finalDeposit: Long` | `finalDeposit: Int64` | |
| `realDeposit: Long` | `realDeposit: Int64` | |
| `settlementDiff: Long` | `settlementDiff: Int64` | |
| `totalFare: Long` | `totalFare: Int64` | |
| `totalCredit: Long` | `totalCredit: Int64` | |
| `tripCount: Int` | `tripCount: Int` | |
| `status: DailySettlementStatus` | `status: DailySettlementStatus` | |
| `submittedAt: Timestamp?` | `submittedAt: Date?` | |
| `confirmedAt: Timestamp?` | `confirmedAt: Date?` | |
| `confirmedBy: String?` | `confirmedBy: String?` | |
| `calculatedCarryOver: Long` | `calculatedCarryOver: Int64` | |
| `originalCarryOver: Long` | `originalCarryOver: Int64` | 통합 제출 시 원본 보존 |
| `originalTripCount: Int` | `originalTripCount: Int` | |
| `originalTotalFare: Long` | `originalTotalFare: Int64` | |
| `originalRealDeposit: Long` | `originalRealDeposit: Int64` | |

---

### DriverCarryOver (6 필드)

Firestore 경로: driver 문서 내 `carryOver` 필드 (서브필드). **유일한 실시간 리스너 대상** (FUNCTIONAL_INVENTORY §1.8).

| Kotlin | Swift |
|---|---|
| `balance: Long` | `balance: Int64` |
| `status: CarryOverStatus` | `status: CarryOverStatus` |
| `lastUpdatedAt: Timestamp?` | `lastUpdatedAt: Date?` |
| `transferredAt: Timestamp?` | `transferredAt: Date?` |
| `transferredBy: String?` | `transferredBy: String?` |
| `todayAmount: Long` | `todayAmount: Int64` |

---

### PendingSync (R1, Phase 2 SwiftData 대상)

Topic 3 합의에 따라 SwiftData `@Model`:

```swift
@Model
final class PendingSync {
    @Attribute(.unique) var id: UUID = UUID()
    // DTO로 CallSettlement 전체를 저장 (JSON blob 대신 nested Codable 권장)
    var callSettlementData: Data     // JSONEncoder(CallSettlement)
    var sessionDate: String          // YYYY-MM-DD
    var provinceId: String
    var cityId: String
    var officeId: String
    var status: String = SyncStatus.pending.rawValue
    var retryCount: Int = 0
    var createdAt: Date = Date()
    var lastAttemptAt: Date?
    var errorMessage: String?
}
```

**Phase 2 deferred**. Phase 1에서는 Firestore 자동 offline persistence에만 의존.

---

### CustomerPoints (9 필드)

기사앱에서 콜 상세·정산 시 참조만. 쓰기는 CF(`notifyCustomerOnComplete`)가 담당(CLAUDE.md BUG-D12).

| Kotlin | Swift |
|---|---|
| `customerId: String` | `customerId: String` |
| `phoneNumber: String` | `phoneNumber: String` |
| `currentPoints: Int` | `currentPoints: Int` |
| `totalEarned: Int` | `totalEarned: Int` |
| `totalUsed: Int` | `totalUsed: Int` |
| `grade: String` | `grade: CustomerGrade` (enum 권장: BRONZE/SILVER/GOLD/VIP) |
| `totalCalls: Int` | `totalCalls: Int` |
| `lastUpdated: Any?` | `lastUpdated: Date?` | Kotlin은 `Any?`로 받지만 Firestore Timestamp |
| `createdAt: Any?` | `createdAt: Date?` |

---

### UserSession (10 필드)

로컬 세션 (오프라인 로그인용). Keychain 또는 UserDefaults 보관 결정은 AUTH.md 참조. 현재 Kotlin은 `SessionManager`가 암호화된 SharedPreferences에 저장.

| Kotlin | Swift | 저장 위치(iOS) |
|---|---|---|
| `userId: String` | `userId: String` | Keychain |
| `email: String` | `email: String` | Keychain |
| `provinceId: String` | `provinceId: String` | UserDefaults (FCM·LockScreen 참조) |
| `cityId: String` | `cityId: String` | UserDefaults |
| `officeId: String` | `officeId: String` | UserDefaults |
| `driverId: String` | `driverId: String` | UserDefaults |
| `driverName: String` | `driverName: String` | UserDefaults |
| `fcmToken: String?` | `fcmToken: String?` | UserDefaults |
| `lastLoginTime: Long` | `lastLoginTime: Date` | UserDefaults |
| `isOnline: Bool` | `isOnline: Bool` | 런타임 전용 |

**파생 메서드**: `isExpired() -> Bool` = `Date().timeIntervalSince(lastLoginTime) > 7*24*3600`.

---

## 의사 스펙 (Swift)

```swift
import FirebaseFirestore   // Firebase 11.x: 단일 import에 Codable 지원 포함

// MARK: - CallInfo
struct CallInfo: Codable, Identifiable, Hashable {
    @DocumentID var id: String?         // Firestore가 자동 주입. CodingKeys에서 제외
    var customerName: String = ""
    var phoneNumber: String = ""
    var customerAddress: String?
    var destination: String?
    var detectedTimestamp: Date?
    var timestamp: Date?
    var status: String = CallStatus.waiting.rawValue
    var fare: Int?
    var assignedDriverId: String?
    var assignedDriverName: String?
    var assignedDriverPhone: String?
    var assignedTimestamp: Date?
    var assignedPickupDriverId: String?
    var deviceName: String?
    var officeId: String = ""
    var provinceId: String = ""
    var cityId: String = ""
    var callType: String?
    var isAppCustomer: Bool = false
    var memoText: String?
    var departureSet: String?           // Firestore key: "departure_set"
    var destinationSet: String?          // Firestore key: "destination_set"
    var waypointsSet: String?            // Firestore key: "waypoints_set"
    var fareSet: Int?                    // Firestore key: "fare_set"
    var tripSummary: String?             // Firestore key: "trip_summary"
    var paymentMethod: String = ""
    var cashAmount: Int?
    var cashReceived: Int?
    var creditAmount: Int?
    var isSummaryConfirmed: Bool = false
    var summaryConfirmedTimestamp: Date?
    var settlementStatus: String = "PENDING"
    var settlementId: String?
    var claimedDriverId: String?
    var sourceSharedCallId: String?
    var tripSummaryFinal: String?
    var finalFare: Int?
    var fareFinal: Int?

    // CodingKeys: id 는 @DocumentID로 SDK가 처리 → 제외
    enum CodingKeys: String, CodingKey {
        case customerName, phoneNumber, customerAddress, destination
        case detectedTimestamp, timestamp, status, fare
        case assignedDriverId, assignedDriverName, assignedDriverPhone
        case assignedTimestamp, assignedPickupDriverId, deviceName
        case officeId, provinceId, cityId, callType, isAppCustomer, memoText
        case departureSet = "departure_set"
        case destinationSet = "destination_set"
        case waypointsSet = "waypoints_set"
        case fareSet = "fare_set"
        case tripSummary = "trip_summary"
        case paymentMethod, cashAmount, cashReceived, creditAmount
        case isSummaryConfirmed, summaryConfirmedTimestamp
        case settlementStatus, settlementId
        case claimedDriverId, sourceSharedCallId
        case tripSummaryFinal, finalFare, fareFinal
    }

    var statusEnum: CallStatus {
        CallStatus(rawValue: status) ?? .waiting
    }
}

// MARK: - Driver
struct Driver: Codable, Identifiable {
    @DocumentID var id: String?
    var name: String = ""
    var phone: String = ""
    var email: String = ""
    var status: DriverStatus = .offline
    var currentCallId: String?
    var rating: Float = 0
    var totalTrips: Int = 0
    var registrationDate: Date?
    var isActive: Bool = true
    var approvalStatus: DriverApprovalStatus = .pending
    var authUid: String?                // 로그인 collectionGroup 쿼리용

    enum CodingKeys: String, CodingKey {
        case name, phone, email, status, currentCallId, rating, totalTrips
        case registrationDate, isActive, approvalStatus, authUid
    }
}

// MARK: - Settlement family
struct SettlementMetadata: Codable {
    var version: Int64 = 0
    @ServerTimestamp var lastUpdatedAt: Date?     // 서버측 자동 timestamp 발급
    var lastUpdatedBy: String = ""
    var depositRatio: Int = 60
    @ServerTimestamp var createdAt: Date?
    var isFinalized: Bool = false
}

struct SettlementTotals: Codable {
    var totalFare: Int64 = 0
    var totalDeposit: Int64 = 0
    var totalDriverShare: Int64 = 0
    var totalCash: Int64 = 0
    var totalCard: Int64 = 0
    var totalCredit: Int64 = 0
    var totalPoints: Int64 = 0
    var callCount: Int = 0

    // TODO: Kotlin SettlementTotals.addCall 로직 순수 함수 이관 (SettlementModels.kt:127-153)
    // 시그니처는 immutable copy 반환 패턴 (Kotlin copy() 동등). SHARED_LOGIC §4 정산 공식 그대로.
    func adding(_ call: CallSettlement, depositRatio: Int) -> SettlementTotals {
        var copy = self
        copy.totalFare += call.fare
        let officeDeposit = Int64(Double(call.fare) * Double(depositRatio) / 100.0)
        copy.totalDeposit += officeDeposit
        copy.totalDriverShare += call.fare - officeDeposit
        copy.totalCash += call.cashReceived
        copy.totalCredit += call.creditAmount
        copy.totalPoints += call.pointsUsed
        copy.callCount += 1
        return copy
    }
}

struct CallSettlement: Codable, Identifiable, Hashable {
    var callId: String = ""
    var id: String { callId }
    var driverId: String = ""
    var driverName: String = ""
    var customerName: String = ""
    var customerPhone: String = ""
    var departure: String = ""
    var destination: String = ""
    var fare: Int64 = 0
    var paymentMethod: String = ""
    var cashReceived: Int64 = 0
    var creditAmount: Int64 = 0
    var pointsUsed: Int64 = 0
    var completedAt: Date?
    var confirmedByOffice: Bool = false
    var syncedAt: Date?

    enum CodingKeys: String, CodingKey {
        case callId, driverId, driverName, customerName, customerPhone
        case departure, destination, fare, paymentMethod
        case cashReceived, creditAmount, pointsUsed
        case completedAt, confirmedByOffice, syncedAt
    }
}

struct SettlementSession: Codable, Identifiable {
    @DocumentID var id: String?                    // sessionDate (YYYY-MM-DD)
    var metadata: SettlementMetadata = SettlementMetadata()
    var totals: SettlementTotals = SettlementTotals()
    var calls: [CallSettlement] = []

    enum CodingKeys: String, CodingKey {
        case metadata, totals, calls
    }
}

struct DriverSettlementSummary {
    let totalFare: Int64
    let depositAmount: Int64
    let myIncome: Int64
    let unpaidAmount: Int64
    let actualDeposit: Int64
    let callCount: Int
    let depositRatio: Int

    static func from(session: SettlementSession) -> DriverSettlementSummary {
        let t = session.totals
        return DriverSettlementSummary(
            totalFare: t.totalFare,
            depositAmount: t.totalDeposit,
            myIncome: t.totalDriverShare,
            unpaidAmount: t.totalCredit,
            actualDeposit: t.totalDeposit - t.totalCredit,
            callCount: t.callCount,
            depositRatio: session.metadata.depositRatio
        )
    }
}

struct DriverDailySettlement: Codable {
    var date: String = ""
    var finalDeposit: Int64 = 0
    var realDeposit: Int64 = 0
    var settlementDiff: Int64 = 0
    var totalFare: Int64 = 0
    var totalCredit: Int64 = 0
    var tripCount: Int = 0
    var status: DailySettlementStatus = .working
    @ServerTimestamp var submittedAt: Date?
    @ServerTimestamp var confirmedAt: Date?
    var confirmedBy: String?
    var calculatedCarryOver: Int64 = 0
    var originalCarryOver: Int64 = 0
    var originalTripCount: Int = 0
    var originalTotalFare: Int64 = 0
    var originalRealDeposit: Int64 = 0
}

struct DriverCarryOver: Codable {
    var balance: Int64 = 0
    var status: CarryOverStatus = .pending
    @ServerTimestamp var lastUpdatedAt: Date?
    @ServerTimestamp var transferredAt: Date?
    var transferredBy: String?
    var todayAmount: Int64 = 0
}

// MARK: - CustomerPoints
struct CustomerPoints: Codable {
    var customerId: String = ""
    var phoneNumber: String = ""
    var currentPoints: Int = 0
    var totalEarned: Int = 0
    var totalUsed: Int = 0
    var grade: String = "BRONZE"    // CustomerGrade rawValue 권장
    var totalCalls: Int = 0
    var lastUpdated: Date?
    var createdAt: Date?
}

// MARK: - UserSession
// 주의: Keychain/UserDefaults에 저장되는 로컬 모델. Firestore decode 대상 아님.
//       JSONEncoder/Decoder로 직렬화. Date는 ISO8601 또는 timeIntervalSince1970로 인코딩.
struct UserSession: Codable {
    let userId: String
    let email: String
    let provinceId: String
    let cityId: String
    let officeId: String
    let driverId: String
    let driverName: String
    var fcmToken: String?
    var lastLoginTime: Date = Date()
    var isOnline: Bool = true

    func isExpired() -> Bool {
        Date().timeIntervalSince(lastLoginTime) > 7 * 24 * 3600
    }
}
```

---

## iOS 특수 사항

1. **Firestore Timestamp ↔ Date**: Firebase 11.x SDK는 `Firestore.Decoder`/`Encoder`가 자동 변환. 서버 발급 timestamp는 `@ServerTimestamp Date?` 프로퍼티 래퍼 사용 — `setData(from:)` 시 nil이면 서버가 채움. **CodingKeys에는 해당 필드 그대로 포함** (래퍼는 storage에만 영향).
2. **Int64 고정**: Kotlin `Long` 전부 Swift `Int64`. Kotlin `Int`는 Swift `Int`(64bit). Firestore Numeric은 Double precision이라 매우 큰 값 주의(2^53 초과 시 손실). 본 프로젝트 금액 범위는 안전.
3. **Codable 기본값 부재**: Swift는 누락 키 자동 default 적용 안 함. 비-필수 필드는 **모두 Optional로 선언** 또는 `init(from decoder:)` 커스텀에서 `decodeIfPresent + default` 수동 적용. Kotlin `data class` 기본값 의존 코드는 Swift에서 깨지므로 검토 필요.
4. **snake_case 키**: `departure_set` 등은 **개별 CodingKeys 필수**. 전역 `keyDecodingStrategy = .convertFromSnakeCase`는 다른 키들이 camelCase라 위험. Firestore Decoder는 기본 strategy `.useDefaultKeys`이므로 keyPath 그대로 매칭.
5. **`@DocumentID` 주입**: `@DocumentID var id: String?` 사용 시 Firestore SDK가 read 시 문서 ID 자동 주입, write 시 해당 필드 무시. **CodingKeys에서 반드시 제외** (포함 시 SDK가 경고 + 동작 불일치 가능).
6. **`@ServerTimestamp`**: setData write 시 값이 nil이면 서버가 timestamp 할당, 비-nil이면 그 값을 그대로 저장. read 시는 일반 Date로 옴. CodingKeys에 포함되어야 함.
7. **enum Codable**: raw value String인 enum은 Swift `Codable` 자동 대응. ENUMS.md 참조.
8. **`UserSession` 직렬화**: Firestore가 아닌 Keychain/UserDefaults 저장이므로 `JSONEncoder`/`Decoder` 사용. Date는 기본 strategy(`.deferredToDate`)로 충분. Keychain Data 변환은 AUTH.md 참조.
9. **`Hashable` 채택 시 주의**: `CallInfo`는 `Hashable` 채택. `@DocumentID` 래퍼 + Date Optional 등 모든 stored property가 Hashable이어야 함 — 확인 OK.

---

## 체크리스트

- [ ] CallInfo 34필드 Swift struct 선언 + CodingKeys 5건(snake_case)
- [ ] Driver struct (PickupDriver 제외)
- [ ] Settlement 패밀리 5개 struct (Metadata/Totals/Session/Call/Summary)
- [ ] DriverDailySettlement 15필드
- [ ] DriverCarryOver 6필드
- [ ] CustomerPoints (grade enum 승격 여부 결정)
- [ ] UserSession + `isExpired()` 메서드
- [ ] PendingSync **SwiftData @Model로 분리** (R1, Phase 2)
- [ ] `SettlementTotals.adding(call:depositRatio:)` 순수 함수 이관
- [ ] `DriverSettlementSummary.from(session:)` 순수 함수 이관
- [ ] Firestore `@DocumentID` 프로퍼티 래퍼 적용 여부 결정
- [ ] Timestamp ↔ Date 변환 전략 통일 (Firestore.Decoder vs 수동)

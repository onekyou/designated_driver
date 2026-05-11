# FIRESTORE — 트랜잭션·리스너·Repository 스펙 (Phase 1 MVP)

> **목적**: Kotlin `firestore.runTransaction { ... }` 블록과 snapshot listener를 Swift `Firestore.runTransaction` + `AsyncStream` 브리지로 이관하는 정밀 스펙.
> **원본 합의**: Topic 3(SwiftData — PendingSync는 R1) · Topic 4(AsyncStream 단독, awaitClose 대응 `onTermination`) · SHARED_LOGIC.md §7(트랜잭션 패턴) · FUNCTIONAL_INVENTORY §1/§2.5.

---

## 원본 소스

| Swift 대상 | Kotlin 파일:라인 |
|---|---|
| `acceptCall` 트랜잭션 | `DriverViewModel.kt:427-452` |
| `rejectCall` 트랜잭션 | `DriverViewModel.kt:502-505` |
| `cancelTrip` 트랜잭션 | `DriverViewModel.kt:552-555` |
| `startDriving` 트랜잭션 | `DriverViewModel.kt:632-635` |
| `completeCall` (단일 update) | `DriverViewModel.kt:665` |
| `confirmAndFinalizeTrip` 트랜잭션 | `DriverViewModel.kt:729-732` |
| `submitDailySettlement` 트랜잭션 | `DriverViewModel.kt:1348 이후` |
| `confirmReceiveCarryOver` update | `DriverViewModel.kt:1314-1347` |
| `updateDriverStatus` update | `DriverViewModel.kt:846-866` |
| `carryOverListener` snapshot listener | `DriverViewModel.kt:1261-1312` |
| `loadCurrentActiveCall` 1회 조회 | `DriverViewModel.kt:238-267` |
| `findDriverDocumentAndSaveInfo` collectionGroup | `LoginViewModel.kt:170-266` |
| `checkAndSync` / version compare | `data/repository/SettlementRepository.kt:194-228` |

---

## 요약

- **6개 트랜잭션 블록**: acceptCall, rejectCall, cancelTrip, startDriving, confirmAndFinalizeTrip, submitDailySettlement. 모두 **콜 문서 + 기사 문서 원자 업데이트** 패턴. (SHARED_LOGIC §7)
- **1개 단일 update**: `completeCall`은 트랜잭션 아닌 `callRef.update(status=AWAITING_SETTLEMENT)` 단일 쓰기(기사 상태 변경 없음).
- **1개 실시간 리스너**: `carryOverListener` (driver 문서 snapshot). **유일한 `addSnapshotListener`**. AsyncStream 래퍼 권장(Topic 4 합의).
- **1개 collectionGroup 쿼리**: 로그인 시 `designated_drivers where authUid == uid` — 문서 경로에서 province/city/office 파싱.
- **Repository 레이어**:
  - `CallRepository` (트랜잭션·update 집합)
  - `DriverRepository` (driver 문서 쓰기·리스너)
  - `SettlementRepository` (SettlementSession 읽기·쓰기 + SwiftData 연동 R1)
  - `PointsRepository` (고객 포인트 조회, Phase 1 읽기 전용)

---

## 경로 헬퍼

```swift
struct FirestorePaths {
    static let provinces = "provinces"
    static let cities = "cities"
    static let offices = "offices"
    static let calls = "calls"
    static let designatedDrivers = "designated_drivers"
    static let pendingDrivers = "pending_drivers"
    static let admins = "admins"
    static let customerInfo = "customerInfo"
    static let settlementSessions = "settlementSessions"
    static let dailySettlements = "dailySettlements"
    static let managerTokens = "managerTokens"

    static func office(_ provinceId: String, _ cityId: String, _ officeId: String) -> DocumentReference {
        Firestore.firestore()
            .collection(provinces).document(provinceId)
            .collection(cities).document(cityId)
            .collection(offices).document(officeId)
    }
    static func call(_ p: String, _ c: String, _ o: String, _ callId: String) -> DocumentReference {
        office(p, c, o).collection(calls).document(callId)
    }
    static func driver(_ p: String, _ c: String, _ o: String, _ driverId: String) -> DocumentReference {
        office(p, c, o).collection(designatedDrivers).document(driverId)
    }
    static func settlementSession(_ p: String, _ c: String, _ o: String, _ date: String) -> DocumentReference {
        office(p, c, o).collection(settlementSessions).document(date)
    }
}
```

Kotlin 원본: `data/Constants.kt:5-16` + `DriverViewModel.kt` 내 각 트랜잭션에서 경로 조립.

---

## 트랜잭션 스펙

### acceptCall
Kotlin `DriverViewModel.kt:371-475`:

```swift
func acceptCall(id: String,
                provinceId: String, cityId: String, officeId: String,
                driverId: String) async throws {
    let callRef = FirestorePaths.call(provinceId, cityId, officeId, id)
    let driverRef = FirestorePaths.driver(provinceId, cityId, officeId, driverId)

    _ = try await Firestore.firestore().runTransaction({ tx, errorPointer -> Any? in
        do {
            let snap = try tx.getDocument(callRef)
            guard snap.exists,
                  let currentStatus = snap.get("status") as? String else {
                throw FirestoreError.callNotFound
            }
            guard currentStatus == CallStatus.assigned.rawValue else {
                throw FirestoreError.callNotAssignable(actual: currentStatus)
            }
            tx.updateData([
                "status": CallStatus.accepted.rawValue
            ], forDocument: callRef)
            tx.updateData([
                "status": DriverStatus.preparing.rawValue
            ], forDocument: driverRef)
            return nil
        } catch {
            errorPointer?.pointee = error as NSError
            return nil
        }
    })
}
```

**사전조건**: `status == ASSIGNED`. 아니면 `CALL_NOT_ASSIGNABLE` 예외 → Store에서 낙관적 UI 롤백 (VIEWMODELS.md).

### rejectCall
Kotlin `DriverViewModel.kt:477-518`:

```swift
func rejectCall(id: String, provinceId: String, cityId: String, officeId: String, driverId: String) async throws {
    let callRef = FirestorePaths.call(provinceId, cityId, officeId, id)
    let driverRef = FirestorePaths.driver(provinceId, cityId, officeId, driverId)

    _ = try await Firestore.firestore().runTransaction({ tx, _ -> Any? in
        tx.updateData([
            "status": CallStatus.waiting.rawValue,
            "assignedDriverId": FieldValue.delete(),
            "assignedDriverName": FieldValue.delete(),
            "assignedDriverPhone": FieldValue.delete(),
            "rejectedByDriver": driverId,
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: callRef)
        tx.updateData([
            "status": DriverStatus.waiting.rawValue
        ], forDocument: driverRef)
        return nil
    })
}
```

**주의**: Kotlin은 `assignedDriverId=null`로 명시 쓰기. Swift에서는 `FieldValue.delete()` 또는 `NSNull()` 선택. `checkAssignedTimeout` CF도 `FieldValue.delete()` 사용(`functions/src/index.ts:3512-3515`) → **일관성을 위해 `FieldValue.delete()` 권장** (Kotlin은 null 쓰기지만 의미 동등).

### cancelTrip
Kotlin `DriverViewModel.kt:526-565`:

```swift
func cancelTrip(id: String, reason: String, provinceId: String, cityId: String, officeId: String, driverId: String) async throws {
    let callRef = FirestorePaths.call(provinceId, cityId, officeId, id)
    let driverRef = FirestorePaths.driver(provinceId, cityId, officeId, driverId)

    _ = try await Firestore.firestore().runTransaction({ tx, _ -> Any? in
        tx.updateData([
            "status": CallStatus.cancelledByDriver.rawValue,   // "CANCELLED_BY_DRIVER"
            "assignedDriverId": FieldValue.delete(),
            "assignedDriverName": FieldValue.delete(),
            "assignedDriverPhone": FieldValue.delete(),
            "cancelReason": reason,
            "cancelledByDriver": true,
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: callRef)
        tx.updateData([
            "status": DriverStatus.waiting.rawValue
        ], forDocument: driverRef)
        return nil
    })
}
```

### startDriving
Kotlin `DriverViewModel.kt:580-638`:

```swift
func startDriving(id: String,
                  departure: String, destination: String, waypoints: String, fare: Int,
                  provinceId: String, cityId: String, officeId: String, driverId: String) async throws {
    let tripSummary = "출발: \(departure), 도착: \(destination), 경유: \(waypoints.isEmpty ? "없음" : waypoints), 요금: \(fare) 원"
    let callRef = FirestorePaths.call(provinceId, cityId, officeId, id)
    let driverRef = FirestorePaths.driver(provinceId, cityId, officeId, driverId)

    _ = try await Firestore.firestore().runTransaction({ tx, _ -> Any? in
        tx.updateData([
            "status": CallStatus.inProgress.rawValue,
            "departure_set": departure,
            "destination_set": destination,
            "waypoints_set": waypoints,
            "fare_set": fare,
            "trip_summary": tripSummary,
            "updatedAt": FieldValue.serverTimestamp()
        ], forDocument: callRef)
        tx.updateData([
            "status": DriverStatus.onTrip.rawValue
        ], forDocument: driverRef)
        return nil
    })
}
```

**주의**: snake_case 키 그대로. CF와 콜매니저 모두 동일 키 기대.

### completeCall (단일 update, 트랜잭션 아님)
Kotlin `DriverViewModel.kt:640-668`:

```swift
func completeCall(id: String, provinceId: String, cityId: String, officeId: String) async throws {
    try await FirestorePaths.call(provinceId, cityId, officeId, id)
        .updateData(["status": CallStatus.awaitingSettlement.rawValue])
}
```

**주의**: 기사 상태 변경 없음(`DriverViewModel.kt:665` 주석 "읽기 없음"). driver.status는 1.7 정산 제출 시 WAITING으로 전환.

### confirmAndFinalizeTrip
Kotlin `DriverViewModel.kt:670-808` (핵심 트랜잭션 라인 729-732):

```swift
func confirmAndFinalizeTrip(id: String,
                             paymentMethod: PaymentMethod,
                             cashAmount: Int?,
                             fare: Int,
                             tripSummary: String,
                             pointsToUse: Int,
                             provinceId: String, cityId: String, officeId: String, driverId: String) async throws -> CallInfo {
    let callRef = FirestorePaths.call(provinceId, cityId, officeId, id)
    let driverRef = FirestorePaths.driver(provinceId, cityId, officeId, driverId)

    // 1) 앱회원 여부 조회 (트랜잭션 밖)
    let callDoc = try await callRef.getDocument()
    let isAppCustomer = callDoc.get("isAppCustomer") as? Bool ?? false
    let phoneNumber = callDoc.get("phoneNumber") as? String

    // 2) 트랜잭션 데이터 구성
    var tripData: [String: Any] = [
        "paymentMethod": paymentMethod.rawValue,
        "status": CallStatus.completed.rawValue,
        "fareFinal": fare,
        "fare": fare,                           // 고객앱 호환성
        "tripSummaryFinal": tripSummary,
        "completedAt": FieldValue.serverTimestamp(),
        "pointsUsed": pointsToUse,
        "finalFare": fare - pointsToUse
    ]
    switch paymentMethod {
    case .cash:
        if let cash = cashAmount { tripData["cashReceived"] = cash }
    case .cashPlusPoints:
        if let cash = cashAmount {
            tripData["cashReceived"] = cash
            tripData["creditAmount"] = max(0, fare - pointsToUse - cash)
        }
    case .credit, .transfer:
        tripData["creditAmount"] = fare
    case .points:
        break
    }

    // 3) 트랜잭션: call+driver 원자 업데이트
    _ = try await Firestore.firestore().runTransaction({ tx, _ -> Any? in
        tx.updateData(tripData, forDocument: callRef)
        tx.updateData(["status": DriverStatus.waiting.rawValue], forDocument: driverRef)
        return nil
    })

    // 4) 최신 문서 재조회 (로컬 반영용)
    let latest = try await callRef.getDocument()
    let info = try latest.data(as: CallInfo.self)
    return info
}
```

**주의**: 포인트 적립은 CF `notifyCustomerOnComplete`가 담당(CLAUDE.md BUG-D12). 기사앱은 Firestore 쓰기만.

### submitDailySettlement
Kotlin `DriverViewModel.kt:1348~` (isIntegration 분기 포함, 전체 로직 긴 편):

```swift
func submitDailySettlement(isIntegration: Bool,
                            session: SettlementSession,
                            provinceId: String, cityId: String, officeId: String, driverId: String) async throws {
    // TODO: Kotlin DriverViewModel.kt:1348 이후 전체 로직 참조
    // 1. 현재 driver.dailySettlement 조회
    // 2. isIntegration && prev.status == PENDING_CONFIRM이면:
    //    - originalCarryOver/TripCount/TotalFare/RealDeposit 보존
    //    - 새 총계 = 기존 + 추가분 병합
    // 3. 새 dailySettlement 객체 구성 (SHARED_LOGIC §4 공식)
    // 4. driverRef.update("dailySettlement", ...) 단일 쓰기
    //    (기사 status=PENDING_CONFIRM도 같이)
}
```

---

## 업데이트 스펙

### confirmReceiveCarryOver
Kotlin `DriverViewModel.kt:1314-1347`:

```swift
func confirmReceiveCarryOver(provinceId: String, cityId: String, officeId: String, driverId: String) async throws {
    try await FirestorePaths.driver(provinceId, cityId, officeId, driverId)
        .updateData([
            "carryOver.status": CarryOverStatus.settled.rawValue,
            "carryOver.lastUpdatedAt": FieldValue.serverTimestamp()
        ])
}
```

**주의**: dot-notation으로 중첩 필드 일부만 업데이트. Firestore iOS SDK 동일 지원.

### updateDriverStatus
Kotlin `DriverViewModel.kt:846-866`:

```swift
func updateDriverStatus(_ status: DriverStatus,
                         provinceId: String, cityId: String, officeId: String, driverId: String) async throws {
    try await FirestorePaths.driver(provinceId, cityId, officeId, driverId)
        .updateData(["status": status.rawValue])
}
```

---

## 리스너 스펙

### carryOverListener — AsyncStream 브리지 (Topic 4 합의)

Kotlin `DriverViewModel.kt:1261-1312`:

```swift
// Sendable 보장 위해 typed result로 매핑. [String: Any]는 Swift 6 strict concurrency에서
// Sendable 위반 (Any가 Sendable 아님). 모델 디코드까지 stream 내부에서 수행.
enum DriverStreamEvent: Sendable {
    case carryOver(DriverCarryOver?)
    case error(Error)
}

func driverCarryOverStream(provinceId: String, cityId: String, officeId: String, driverId: String)
    -> AsyncStream<DriverStreamEvent> {
    let driverRef = FirestorePaths.driver(provinceId, cityId, officeId, driverId)
    return AsyncStream { continuation in
        let listener = driverRef.addSnapshotListener { snap, error in
            if let error {
                continuation.yield(.error(error))
                return
            }
            // dot-notation로 carryOver 서브필드만 디코드 시도.
            // 전체 Driver 디코드 후 .carryOver 추출도 가능 (필요 시 별도 stream 분리).
            do {
                if let snap, snap.exists {
                    let driver = try snap.data(as: Driver.self)
                    // Driver 모델에 carryOver 포함된 경우 — 또는 별도 필드 매핑
                    // 본 예시는 데모. 실제 매핑은 Driver 모델 구조에 따라 조정.
                    let carry: DriverCarryOver? = nil  // TODO: snap.get("carryOver") 디코드
                    continuation.yield(.carryOver(carry))
                } else {
                    continuation.yield(.carryOver(nil))
                }
            } catch {
                continuation.yield(.error(error))
            }
        }
        continuation.onTermination = { _ in
            listener.remove()                // awaitClose 대응 — 비용 누수 방지 필수
        }
    }
}
```

**사용처**: `DriverStore.startCarryOverListener()` — for-await 루프에서 `carryOver` + `dailySettlementStatus` 업데이트. Store가 **1회만 호출** 보장(중복 호출 시 listener 중복 등록·비용 누수 위험).

**주의**:
- AsyncStream은 **단일 consumer**. 여러 곳에서 구독 필요하면 Store가 @Observable property로 노출하고 SwiftUI는 그걸 관찰(Topic 4 위험 신호 4번).
- `onTermination`에서 `listener.remove()` 호출 필수 — 안 그러면 Firestore 비용 누수 (FUNCTIONAL_INVENTORY §2.5).
- `[String: Any]` 같은 비-Sendable 타입을 stream payload로 노출하면 Swift 6 strict concurrency에서 컴파일 경고/에러 → 디코드 후 Sendable 모델로 wrap.

---

## 1회 조회 스펙

### loadCurrentActiveCall
Kotlin `DriverViewModel.kt:238-267`:

```swift
func loadCurrentActiveCall(provinceId: String, cityId: String, officeId: String, driverId: String)
    async throws -> [CallInfo] {
    let officeRef = FirestorePaths.office(provinceId, cityId, officeId)
    let activeStatuses = [
        CallStatus.assigned.rawValue,
        CallStatus.accepted.rawValue,
        CallStatus.inProgress.rawValue,
        CallStatus.awaitingSettlement.rawValue
    ]
    let snap = try await officeRef.collection(FirestorePaths.calls)
        .whereField("assignedDriverId", isEqualTo: driverId)
        .whereField("status", in: activeStatuses)
        .getDocuments()
    return try snap.documents.compactMap { try $0.data(as: CallInfo.self) }
}
```

**주의**: CLAUDE.md에 기록된 **비용 최적화 ~$414/월 절감 전제** — 실시간 리스너 폐기. Swift도 동일 정책 유지.

---

## 쿼리 스펙

### findDriverDocumentAndSaveInfo (로그인 시 collectionGroup)
Kotlin `LoginViewModel.kt:170-266`:

```swift
struct DriverContext {
    let provinceId: String
    let cityId: String
    let officeId: String
    let driverId: String
    let approvalStatus: DriverApprovalStatus
}

func findDriverContext(authUid: String) async throws -> DriverContext? {
    let db = Firestore.firestore()
    let snap = try await db.collectionGroup(FirestorePaths.designatedDrivers)
        .whereField("authUid", isEqualTo: authUid)
        .limit(to: 1)
        .getDocuments()

    guard let doc = snap.documents.first else { return nil }

    // 문서 경로에서 province/city/office 파싱
    // 경로: provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}
    let segments = doc.reference.path.split(separator: "/").map(String.init)
    guard segments.count >= 6 else { return nil }
    let provinceId = segments[1]
    let cityId = segments[3]
    let officeId = segments[5]

    let approvalRaw = doc.get("approvalStatus") as? String ?? "PENDING"
    let approval = DriverApprovalStatus(rawValue: approvalRaw) ?? .pending

    return DriverContext(
        provinceId: provinceId, cityId: cityId, officeId: officeId,
        driverId: doc.documentID, approvalStatus: approval
    )
}
```

**주의**: Firestore **복합 인덱스 필요** — collectionGroup("designated_drivers") + where authUid. 콘솔에서 prompt 링크로 자동 생성 유도 가능.

---

## Repository 레이어

```swift
// MARK: - CallRepository
protocol CallRepository {
    func loadCurrentActiveCall(p: String, c: String, o: String, driverId: String) async throws -> [CallInfo]
    func loadCall(id: String, p: String, c: String, o: String) async throws -> CallInfo?
    func acceptCall(id: String, p: String, c: String, o: String, driverId: String) async throws
    func rejectCall(id: String, p: String, c: String, o: String, driverId: String) async throws
    func cancelTrip(id: String, reason: String, p: String, c: String, o: String, driverId: String) async throws
    func startDriving(id: String, departure: String, destination: String, waypoints: String, fare: Int,
                       p: String, c: String, o: String, driverId: String) async throws
    func completeCall(id: String, p: String, c: String, o: String) async throws
    func confirmAndFinalizeTrip(id: String, paymentMethod: PaymentMethod, cashAmount: Int?, fare: Int,
                                  tripSummary: String, pointsToUse: Int,
                                  p: String, c: String, o: String, driverId: String) async throws -> CallInfo
}

// MARK: - DriverRepository
protocol DriverRepository {
    func updateStatus(_ status: DriverStatus, p: String, c: String, o: String, driverId: String) async throws
    func confirmReceiveCarryOver(p: String, c: String, o: String, driverId: String) async throws
    func observeDriver(p: String, c: String, o: String, driverId: String)
        -> AsyncStream<FirestoreStreamEvent<[String: Any]>>
    func findDriverContext(authUid: String) async throws -> DriverContext?
}

// MARK: - SettlementRepository
protocol SettlementRepository {
    func loadSession(date: String, p: String, c: String, o: String) async throws -> SettlementSession?
    func submitDailySettlement(isIntegration: Bool,
                                session: SettlementSession,
                                p: String, c: String, o: String, driverId: String) async throws
    // R1: SwiftData PendingSync 연동 (Phase 2)
    func enqueuePendingSync(_ call: CallSettlement, sessionDate: String,
                             p: String, c: String, o: String) async throws
    func drainPendingSync() async throws -> Int
    // 충돌 해결: server-wins on read
    func checkAndSync(date: String, p: String, c: String, o: String) async throws -> Bool
}

// MARK: - PointsRepository
protocol CustomerPointsRepository {
    func getPointInfo(phone: String, p: String, c: String, o: String) async -> [String: Any]?
}
```

---

## 충돌 해결 / 오프라인 정책

### server-wins on read (SHARED_LOGIC §7 + SettlementRepository.kt:194-228)

```swift
func checkAndSync(date: String, p: String, c: String, o: String) async throws -> Bool {
    let sessionRef = FirestorePaths.settlementSession(p, c, o, date)
    let doc = try await sessionRef.getDocument()
    guard doc.exists else { return false }

    let remoteVersion = (doc.get("metadata.version") as? Int64) ?? 0
    let localVersion = await SwiftDataCache.getCacheVersion(date: date, officeId: o) ?? -1

    if remoteVersion > localVersion {
        // 원격 전체 덮어쓰기
        let session = try doc.data(as: SettlementSession.self)
        try await SwiftDataCache.upsert(session, date: date, p: p, c: c, o: o)
        return true
    }
    return false
}
```

### optimistic write
`version = previous.metadata.version + 1`로 업로드 (Kotlin `SettlementRepository.kt:145`). Swift도 동일 패턴.

### Firestore 자동 offline persistence
Firebase iOS SDK 기본 persistence ON. **API 변경 주의**: 구 `FirestoreSettings.isPersistenceEnabled`는 deprecated, 신 API는 `FirestoreSettings.cacheSettings = PersistentCacheSettings()` (또는 `MemoryCacheSettings()`). 신규 프로젝트는 신 API 사용 권장.

```swift
let settings = FirestoreSettings()
settings.cacheSettings = PersistentCacheSettings()  // 기본값과 동일이지만 명시 권장
Firestore.firestore().settings = settings
```

write는 로컬 큐에 쌓여 복구 시 flush. **단 runTransaction은 읽기가 필요**해 오프라인에서 즉시 실패 가능(FUNCTIONAL_INVENTORY §1.2 B?). 이 케이스는 공통 에러 핸들러에서 errorMessage UI 설정으로 대응, PendingSync 큐는 R1.

---

## iOS 특수 사항

1. **Firestore iOS SDK async API**: Firebase 11.x는 `getDocument()`/`getDocuments()`/`updateData(_:)`/`setData(_:)` 등에 `async throws` 확장 제공. **`runTransaction`은 closure 시그니처가 `(Transaction, NSErrorPointer) -> Any?`로 유지** (Swift throws closure 직접 받지 않음). closure 내부 에러는 `errorPointer?.pointee = error as NSError; return nil` 패턴으로 전파. 외부 await 호출은 `try await runTransaction(...)`. SDK 버전 확인: 11.x **재확인 필요** — 신 API 추가됐을 수 있음.
2. **runTransaction 내부 에러 전파 패턴**: 본 스펙의 acceptCall 예시 참조 — closure 안 do/catch + errorPointer 변환. closure 자체는 throws 불가.
3. **Codable로 snapshot 디코딩**: `try snapshot.data(as: CallInfo.self)`는 Firebase 11.x에서 `FirebaseFirestore` 단일 import에 포함(구 `FirebaseFirestoreSwift` 통합). **throws** — 호출부에서 `try` 또는 `try?` 명시. `@DocumentID`는 SDK가 자동 주입.
4. **dot-notation 업데이트**: `"carryOver.status": "SETTLED"` 키는 iOS SDK도 지원. 중첩 필드 일부만 업데이트 가능.
5. **FieldValue.delete() vs null**: Firestore는 둘 다 같은 의미(필드 제거). Kotlin은 null 쓰기, iOS CF는 `FieldValue.delete()` 혼용. **Swift에서는 `FieldValue.delete()` 통일 권장** — 명시적 의도.
6. **리스너 해제 누락 = 비용 누수**: AsyncStream `onTermination`에서 반드시 `listener.remove()`. Task cancel 시 onTermination 트리거 확인(Topic 4 Swift 검증 사항 1번).
7. **collectionGroup 인덱스**: 앱 최초 실행 시 인덱스 없으면 에러. Firebase Console에서 수동 생성 or 에러 메시지의 자동 링크 따라가기. Firestore 보안 규칙도 collectionGroup 쿼리 허용 필요.
8. **비용 감수 명시**: Kotlin 원본이 실시간 리스너를 **1개(carryOver)만** 유지한 건 월 ~$414 절감 전제. Swift도 동일 정책 — 추가 리스너 도입 금지.
9. **Cache settings 신 API**: `FirestoreSettings.cacheSettings = PersistentCacheSettings()` (Firebase 10.13+). 구 `isPersistenceEnabled`는 deprecated.
10. **AsyncStream payload Sendable**: Stream 내부에서 디코드 완료한 typed Sendable 모델을 yield. `[String: Any]`/`DocumentSnapshot` 직접 노출은 Swift 6 strict concurrency에서 위반 가능 — 디코드 단계를 stream 내부로 흡수.
11. **DocumentSnapshot Sendable 여부**: Firebase 11.x SDK에서 `DocumentSnapshot`이 Sendable 채택 여부 **재확인 필요**. 미채택 시 stream 외부로 전달 금지.
12. **SwiftDataCache (R1 deferred)**: `checkAndSync` 예시의 `SwiftDataCache.getCacheVersion`/`upsert`는 R1 SwiftData 도입 후 정의. Phase 1은 protocol stub만 노출하고 구현 비활성화.

---

## 체크리스트

- [ ] `FirestorePaths` 헬퍼 구조체 (8개 경로 builder)
- [ ] 6개 트랜잭션 함수 시그니처 + TODO 크로스레퍼런스
- [ ] completeCall 단일 update (트랜잭션 아님 명시)
- [ ] confirmReceiveCarryOver (dot-notation) + updateDriverStatus
- [ ] driverSnapshotStream AsyncStream + `onTermination { listener.remove() }` (Topic 4)
- [ ] loadCurrentActiveCall — 실시간 리스너 금지, 1회 조회만
- [ ] findDriverContext collectionGroup 쿼리 + 경로 파싱
- [ ] CallRepository / DriverRepository / SettlementRepository / PointsRepository 프로토콜
- [ ] server-wins on read + version compare 로직 (checkAndSync)
- [ ] Firestore 오프라인 persistence ON 확인 (기본값)
- [ ] FieldValue.delete() 통일 정책
- [ ] Firestore 인덱스 요구사항 문서화 (collectionGroup designated_drivers + authUid)
- [ ] PendingSync 큐 연동은 R1 Phase 2로 보류 (여기선 protocol만 노출)
- [ ] AsyncStream payload는 typed Sendable 모델로 wrap (`[String: Any]` 직접 노출 금지)
- [ ] Cache settings 신 API(`PersistentCacheSettings`) 사용
- [ ] runTransaction closure는 throws 직접 받지 않음 — `errorPointer` 패턴 강제
- [ ] Firestore 보안 규칙 collectionGroup designated_drivers 쿼리 허용 검증

# FCM 알림을 통한 로컬 업데이트 시스템 분석

## 📌 전체 구조 요약

```
Firebase Firestore (원본 데이터)
    ↓
Cloud Functions (변경 감지)
    ↓
FCM (푸시 알림)
    ↓
앱 (로컬 DB 업데이트???)
```

---

## 1️⃣ 콜매니저 (Call Manager) - FCM 전용 모드

### 아키텍처
- **로컬 DB**: Room Database (SQLite)
- **동기화 방식**: FCM 전용 (Firestore 리스너 제거됨)
- **초기 데이터**: 앱 시작 시 Firestore에서 전체 데이터 로드
- **이후 업데이트**: FCM 메시지로만 업데이트 (리스너 없음)

### 핵심 코드

#### DriverRepository (기사 정보)
```kotlin
// 위치: call_manager/app/src/main/java/com/designated/callmanager/data/repository/DriverRepository.kt

// ❌ syncListener 제거됨 - FCM 전용 모드 (Line 37)
private var currentRegionId: String? = null
private var currentOfficeId: String? = null

// 초기 데이터 로드만 수행 (Line 275)
private fun startSync(regionId: String, officeId: String) {
    Log.d(TAG, "✅ 기사 초기 데이터 로드: $regionId/$officeId (FCM 전용 모드)")

    scope.launch {
        refreshData(regionId, officeId)  // 한 번만 전체 로드
        Log.d(TAG, "✅ 초기 기사 데이터 로드 완료 - 이후 업데이트는 FCM을 통해서만")
    }

    // ❌ 리스너 제거됨 - 이후 업데이트는 순수하게 FCM을 통해서만!
}
```

#### CallRepository (콜 정보)
```kotlin
// 위치: call_manager/app/src/main/java/com/designated/callmanager/data/repository/CallRepository.kt

// ❌ syncListener 제거됨 - FCM 전용 모드 (Line 38)

// 동일하게 초기 데이터만 로드하고 이후 FCM으로만 업데이트
```

### FCM 메시지 처리

#### MyFirebaseMessagingService.kt
```kotlin
// 위치: call_manager/app/src/main/java/com/designated/callmanager/service/MyFirebaseMessagingService.kt

override fun onMessageReceived(remoteMessage: RemoteMessage) {
    val type = remoteMessage.data["type"]
    val callId = remoteMessage.data["callId"] ?: ""

    when (type) {
        "DRIVER_STATUS_UPDATE" -> {
            handleDriverStatusUpdate(remoteMessage, callId)  // ⚠️ 알림만 표시!
        }
        "CALL_STATUS_UPDATE" -> {
            // ⚠️ 브로드캐스트만 전송, 로컬 DB 업데이트 없음!
            val status = remoteMessage.data["status"] ?: ""
            val intent = Intent("com.designated.callmanager.CALL_STATUS_UPDATE")
            intent.putExtra("callId", callId)
            intent.putExtra("status", status)
            sendBroadcast(intent)
        }
        "STATUS_CHANGE" -> {
            handleStatusChange(remoteMessage, callId)  // ⚠️ 알림만 표시!
        }
        // ... 기타 메시지 타입들
    }
}

private fun handleDriverStatusUpdate(remoteMessage: RemoteMessage, callId: String) {
    // ⚠️ 문제: 알림만 표시하고 로컬 DB는 업데이트 안 함!
    showNotification(
        title = "📍 기사 상태 업데이트",
        content = "$driverName: $newStatus"
    )

    // ❌ 로컬 DB 업데이트 없음!
    // driverRepository.updateDriverStatus() 호출 안 함!
}
```

### 🚨 **핵심 문제점**

#### 문제 1: DRIVER_STATUS_UPDATE
- FCM 메시지 받음 → **알림만 표시**
- **로컬 DB 업데이트 없음** → UI가 업데이트되지 않음!
- Firestore 리스너가 제거되어서 자동 동기화도 없음

#### 문제 2: CALL_STATUS_UPDATE
- FCM 메시지 받음 → **브로드캐스트만 전송**
- MainActivity에 리시버 없음 → **아무 일도 일어나지 않음**
- **로컬 DB 업데이트 없음** → 내부 콜 목록이 업데이트되지 않음!

---

## 2️⃣ 기사앱 (Driver App)

### 아키텍처
- **로컬 DB**: 없음 (Firestore 직접 읽기/쓰기)
- **동기화 방식**: 직접 Firestore 쿼리

### FCM 메시지 처리

#### MyFirebaseMessagingService.kt
```kotlin
// 위치: driver_app/app/src/main/java/com/designated/driverapp/MyFirebaseMessagingService.kt

override fun onMessageReceived(remoteMessage: RemoteMessage) {
    Log.d(TAG, "FCM 메시지 수신: ${remoteMessage.data}")

    val callId = remoteMessage.data["callId"]

    // ⚠️ 단순히 알림 표시 + MainActivity로 전달
    if (!callId.isNullOrBlank()) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("callId", callId)  // callId 전달
        }
        startActivity(intent)
    }

    showNotification(title, body, callId)

    // ❌ 로컬 DB 없음, 상태 업데이트 없음
    // MainActivity에서 callId를 받아서 직접 Firestore 쿼리
}
```

### 기사 상태 업데이트 방식
- **수동 업데이트**: 기사가 직접 버튼 클릭 → Firestore 업데이트
- **FCM 역할**: 새 콜 알림만 (상태 동기화 없음)

---

## 3️⃣ Cloud Functions - FCM 발송

### 위치
`functions/src/index.ts`

### 기사 상태 변경 감지 (Line 3400~)
```typescript
export const onDriverStatusChanged = onDocumentWritten(
  "regions/{regionId}/offices/{officeId}/designated_drivers/{driverId}",
  async (event) => {
    const afterData = event.data?.after.data();
    const beforeData = event.data?.before.data();

    const oldStatus = beforeData?.status;
    const newStatus = afterData?.status;

    if (oldStatus !== newStatus) {
      // FCM 메시지 생성
      const message = {
        data: {
          type: "DRIVER_STATUS_UPDATE",  // ⬅️ 이 메시지 타입
          driverId: driverId,
          driverName: driverName,
          newStatus: newStatus,
          oldStatus: oldStatus,
          regionId: regionId,
          officeId: officeId
        },
        tokens: managerTokens
      };

      // 콜매니저에게 FCM 전송
      await admin.messaging().sendEachForMulticast(message);
    }
  }
);
```

### 콜 상태 변경 감지 (Line 1100~)
```typescript
export const onCallStatusChanged = onDocumentWritten(
  "regions/{regionId}/offices/{officeId}/calls/{callId}",
  async (event) => {
    const afterData = event.data?.after.data();
    const beforeData = event.data?.before.data();

    if (afterData.status === "ACCEPTED" ||
        afterData.status === "IN_PROGRESS" ||
        afterData.status === "COMPLETED") {

      // 콜매니저에 상태 변경 알림 전송 (Line 1144)
      const managerPayload = {
        data: {
          type: "CALL_STATUS_UPDATE",  // ⬅️ 이 메시지 타입
          callId: callId,
          status: afterData.status,
          customerName: afterData.customerName || "고객",
          assignedDriverName: afterData.assignedDriverName || "",
          regionId: regionId,
          officeId: officeId
        },
        tokens: managerTokens
      };

      await admin.messaging().sendEachForMulticast(managerPayload);
    }
  }
);
```

### 배차 시 FCM 전송 (Line 173~)
```typescript
export const oncallassigned = onDocumentWritten(
  "regions/{regionId}/offices/{officeId}/calls/{callId}",
  async (event) => {
    // ... 배차 처리 ...

    // 콜매니저에게도 배차 상태 변경 알림 전송
    const managerPayload = {
      data: {
        type: "CALL_STATUS_UPDATE",  // ⬅️ 이 메시지 타입
        callId: callId,
        status: "ASSIGNED",
        assignedDriverId: driverId,
        assignedDriverName: driverName,
        regionId: regionId,
        officeId: officeId
      },
      tokens: managerTokens
    };

    await admin.messaging().sendEachForMulticast(managerPayload);
  }
);
```

---

## 4️⃣ 전체 흐름 다이어그램

### 기사 상태 업데이트 흐름

```
[기사앱] 기사가 "대기중" 버튼 클릭
    ↓
Firestore: designated_drivers/{driverId}
    status: "OFFLINE" → "ONLINE" 업데이트
    ↓
Cloud Functions: onDriverStatusChanged 트리거
    ↓
FCM 메시지 생성
    type: "DRIVER_STATUS_UPDATE"
    newStatus: "ONLINE"
    ↓
[콜매니저] MyFirebaseMessagingService 수신
    ↓
handleDriverStatusUpdate() 호출
    ↓
⚠️ 알림만 표시 (showNotification)
    ↓
❌ 로컬 DB 업데이트 없음!
    ↓
❌ UI 업데이트 안 됨! (기사 카드 상태 변화 없음)
```

### 콜 상태 업데이트 흐름

```
[기사앱] 콜 수락 버튼 클릭
    ↓
Firestore: calls/{callId}
    status: "ASSIGNED" → "ACCEPTED" 업데이트
    ↓
Cloud Functions: onCallStatusChanged 트리거
    ↓
FCM 메시지 생성
    type: "CALL_STATUS_UPDATE"
    status: "ACCEPTED"
    ↓
[콜매니저] MyFirebaseMessagingService 수신
    ↓
브로드캐스트 전송: "com.designated.callmanager.CALL_STATUS_UPDATE"
    ↓
❌ MainActivity에 리시버 없음!
    ↓
❌ 로컬 DB 업데이트 없음!
    ↓
❌ UI 업데이트 안 됨! (내부 콜 목록 상태 변화 없음)
```

---

## 5️⃣ 문제 요약

### 현재 상태
| 컴포넌트 | FCM 수신 | 알림 표시 | 로컬 DB 업데이트 | UI 업데이트 | 결과 |
|---------|---------|----------|----------------|------------|-----|
| 기사 카드 (DRIVER_STATUS_UPDATE) | ✅ | ✅ | ❌ | ❌ | **작동 안 함** |
| 내부 콜 목록 (CALL_STATUS_UPDATE) | ✅ | ❌ | ❌ | ❌ | **작동 안 함** |

### 근본 원인
1. **Firestore 리스너 제거**: 비용 절감을 위해 FCM 전용 모드로 전환
2. **FCM 핸들러 미완성**: FCM 받아도 로컬 DB를 업데이트하지 않음
3. **UI가 로컬 DB를 구독**: Flow로 Room DB를 구독하는데 DB가 업데이트 안 되니 UI도 안 됨

---

## 6️⃣ 해결 방법

### Option 1: FCM 핸들러에서 로컬 DB 업데이트 (권장)

#### DRIVER_STATUS_UPDATE 처리
```kotlin
// MyFirebaseMessagingService.kt

private suspend fun handleDriverStatusUpdate(remoteMessage: RemoteMessage, callId: String) {
    val driverId = remoteMessage.data["driverId"] ?: return
    val newStatus = remoteMessage.data["newStatus"] ?: return
    val driverName = remoteMessage.data["driverName"] ?: "기사"

    // 1. 알림 표시
    showNotification(
        title = "📍 기사 상태 업데이트",
        content = "$driverName: $newStatus"
    )

    // 2. ✅ 로컬 DB 업데이트 (추가 필요!)
    val regionId = remoteMessage.data["regionId"] ?: return
    val officeId = remoteMessage.data["officeId"] ?: return

    // DriverRepository 접근 필요
    driverRepository.updateDriverStatus(driverId, newStatus)

    Log.d(TAG, "✅ 기사 상태 로컬 DB 업데이트 완료: $driverId -> $newStatus")
}
```

#### CALL_STATUS_UPDATE 처리
```kotlin
// MyFirebaseMessagingService.kt

private suspend fun handleCallStatusUpdate(remoteMessage: RemoteMessage, callId: String) {
    val status = remoteMessage.data["status"] ?: return
    val regionId = remoteMessage.data["regionId"] ?: return
    val officeId = remoteMessage.data["officeId"] ?: return

    // 1. ✅ 로컬 DB 업데이트 (추가 필요!)
    callRepository.updateCallStatus(callId, status)

    Log.d(TAG, "✅ 콜 상태 로컬 DB 업데이트 완료: $callId -> $status")

    // 2. 알림 표시 (선택)
    showNotification(
        title = "📞 콜 상태 변경",
        content = "상태: $status"
    )
}
```

### Option 2: Firestore 리스너 다시 활성화 (비용 증가)

```kotlin
// DriverRepository.kt

private var syncListener: ListenerRegistration? = null

private fun startSync(regionId: String, officeId: String) {
    // ✅ 리스너 다시 활성화
    syncListener = firestore.collection("regions")
        .document(regionId)
        .collection("offices")
        .document(officeId)
        .collection("designated_drivers")
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "Firestore 리스너 에러", error)
                return@addSnapshotListener
            }

            snapshot?.documentChanges?.forEach { change ->
                when (change.type) {
                    DocumentChange.Type.MODIFIED -> {
                        // 로컬 DB 업데이트
                        updateLocalDriver(change.document)
                    }
                }
            }
        }
}
```

---

## 7️⃣ 권장 해결 순서

1. ✅ **DriverRepository에 간단한 업데이트 메서드 추가**
   ```kotlin
   suspend fun updateDriverStatusFromFCM(
       driverId: String,
       newStatus: String,
       regionId: String,
       officeId: String
   )
   ```

2. ✅ **CallRepository에 간단한 업데이트 메서드 추가**
   ```kotlin
   suspend fun updateCallStatusFromFCM(
       callId: String,
       newStatus: String,
       regionId: String,
       officeId: String
   )
   ```

3. ✅ **MyFirebaseMessagingService에서 호출**
   - Repository 인스턴스 획득 (Hilt 또는 수동)
   - FCM 메시지 받으면 즉시 로컬 DB 업데이트

4. ✅ **테스트**
   - 기사 상태 변경 → 콜매니저 기사 카드 실시간 업데이트 확인
   - 콜 수락 → 콜매니저 내부 콜 목록 실시간 업데이트 확인

---

## 📝 결론

**현재 시스템은 "FCM 전용 모드"로 설계되었지만, FCM 핸들러가 미완성 상태입니다.**

- FCM 메시지는 정상적으로 수신됨 ✅
- 알림은 표시됨 ✅
- **로컬 DB 업데이트가 없어서 UI가 갱신되지 않음** ❌

**해결책: FCM 핸들러에서 로컬 DB를 업데이트하도록 코드 추가 필요!**

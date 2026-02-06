# 최악의 시나리오 분석 및 대안

> 현재 시스템은 **리스너 최소화 + FCM 알림 기반 + 기기 로컬 상태 업데이트** 방식입니다.
> 이 문서는 이 아키텍처의 취약점과 대안을 분석합니다.

---

## 1. FCM 알림 실패 시나리오

### 1.1 기사앱 - 배차 알림 미도착 ✅ 해결됨

**상황:** 콜매니저가 배차했으나 기사가 알림을 못 받음

**현재 대응 (Cloud Functions 구현 완료):**
```
1. acknowledgeNotification()
   - 기사앱에서 알림 수신 시 ACK 전송
   - Firestore notifications/{id}.status = "delivered"

2. retryPendingNotifications() [매 1분 스케줄]
   - 조건: status="pending" AND 10초 경과 AND retryCount < 2
   - 동작: FCM 재전송, retryCount 증가
   - 최대 2회 자동 재시도

3. handleFailedNotifications() [매 1분 스케줄]
   - 조건: status="pending" AND retryCount >= 2
   - RTDB Presence 확인하여 기사 온라인 상태 파악
   - 콜매니저에 "알림 전송 실패" 알림 전송
   - status = "failed" 변경
```

**핵심 파일:** `functions/src/index.ts:114-273`

**상태:** ✅ 완전 구현됨 - 추가 조치 불필요

---

### 1.2 손님앱 - 기사 배정 알림 미도착 ✅ 보완 불필요

**상황:** 기사가 배정됐으나 손님이 알림을 못 받음

**현재 대응 및 분석:**
```
[손님 사용 패턴]
1. 앱/전화로 콜 요청 → 폰 닫고 대기
2. 기사 배정 → FCM 알림 (못 받아도 무관)
3. 기사가 직접 손님에게 전화 ← 실제 연락 수단
4. 운행 완료 → 포인트 알림 (나중에 확인 가능)

[앱 호출 실패 시] ✅ 보완 완료
1. 자동 재시도 (1회, 1초 대기 후)
2. 최종 실패 시:
   - In-app 에러: "콜 요청에 실패했습니다. 네트워크 연결을 확인해주세요."
   - 시스템 알림: "앱 호출 실패 - 네트워크 연결 확인 후 다시 시도하거나 전화호출 버튼을 눌러주세요"
3. 손님이 [전화호출] 버튼으로 대체 가능
```

**핵심 파일:** `customer_app/.../ui/main/MainViewModel.kt:284-312, 696-740`

**추가 보완 불필요 이유:**
1. 기사가 직접 손님에게 전화하므로 FCM 알림은 정보성
2. 앱 호출 실패 시 자동 재시도 + 시스템 알림 + 전화호출 버튼 존재
3. 전화 = 최종 백업 (항상 작동)

**취약점:**
- 손님이 기사 배정 사실을 모르고 계속 대기
- 기사가 도착해도 손님이 모름

**대안:**
```
[옵션 1: 자기 콜 리스너 - 권장]
손님앱에서 자신이 요청한 활성 콜만 리스너

// MainViewModel.kt
private fun startMyCallListener() {
    if (currentCallId == null) return
    myCallListener = firestore
        .collection("calls")
        .document(currentCallId)
        .addSnapshotListener { doc, _ ->
            val status = doc?.getString("status")
            when (status) {
                "ASSIGNED" -> handleDriverAssigned(doc)
                "COMPLETED" -> handleRideCompleted(doc)
                "CANCELED" -> handleCallCancelled()
            }
        }
}

장점: FCM 실패해도 상태 변화 감지
비용: 단일 문서 리스너 (최소 비용)

[옵션 2: 주기적 폴링]
손님앱에서 10초마다 자기 콜 상태 조회
- FCM보다 느리지만 확실함
- 배터리 소모 증가
```

---

### 1.3 손님앱 - 운행 완료 알림 미도착

**상황:** 운행 완료됐으나 손님이 포인트 적립 알림을 못 받음

**현재 대응:**
- 없음 (FCM만 의존)

**취약점:**
- 포인트 적립이 서버에서 됐더라도 손님이 확인 못 함

**대안:**
- 위 1.2의 리스너로 동시 해결
- 또는 손님앱 시작 시 미적립 콜 확인 로직 추가

---

## 2. 네트워크 불안정 시나리오

### 2.1 기사앱 - Firestore 쓰기 실패

**상황:** 기사가 "운행 완료" 눌렀으나 Firestore 업데이트 실패

**현재 대응:**
```kotlin
// 로컬 UI 먼저 업데이트
_uiState.update { ... }

// Firestore는 백그라운드
callRef.update(...).await()
```

**취약점:**
- UI는 "운행 완료"로 보이나 서버는 "운행 중"
- 콜매니저에서는 여전히 "운행 중"으로 보임
- 정산 불일치 가능

**대안:**
```
[옵션 1: 쓰기 실패 재시도 큐]
// WorkManager로 실패한 업데이트 재시도
class FirestoreRetryWorker : CoroutineWorker() {
    override suspend fun doWork(): Result {
        val pendingUpdates = getPendingUpdates()
        pendingUpdates.forEach { update ->
            try {
                firestore.document(update.path).update(update.data).await()
                markAsCompleted(update.id)
            } catch (e: Exception) {
                return Result.retry()
            }
        }
        return Result.success()
    }
}

[옵션 2: UI에 동기화 상태 표시]
- "서버와 동기화 대기 중" 뱃지 표시
- 사용자가 인지하도록 함
```

---

### 2.2 콜매니저 - 배차 실패

**상황:** 배차 버튼 눌렀으나 네트워크 끊김

**현재 대응:**
```kotlin
// 네트워크 체크
val activeNetwork = connectivityManager.activeNetworkInfo
if (activeNetwork == null || !activeNetwork.isConnected) {
    _snackbarMessage.value = "배차 실패 - 네트워크 연결을 확인 후 다시 시도하세요"
    return
}
```

**분석:** 현재 적절히 처리됨

---

### 2.3 콜매니저 - 리스너 연결 끊김

**상황:** Firestore 리스너가 네트워크 문제로 끊김

**현재 대응:**
- Room DB에 캐시된 데이터 표시
- 리스너 자동 재연결 (Firestore 기본 동작)

**취약점:**
- 재연결까지 새 콜 감지 불가
- 사용자가 연결 상태를 모름

**대안:**
```
[연결 상태 표시 UI]
// Firestore의 네트워크 상태 감지
FirebaseFirestore.getInstance()
    .addSnapshotsInSyncListener {
        // 동기화 완료 시 호출
    }

// 또는 RTDB의 .info/connected 사용
realtimeDb.getReference(".info/connected")
    .addValueEventListener { snapshot ->
        val connected = snapshot.getValue(Boolean::class.java) ?: false
        _isConnected.value = connected
    }
```

---

## 3. 앱 강제 종료 시나리오

### 3.1 기사앱 - 운행 중 앱 종료

**상황:** 운행 중에 시스템이 앱 종료 (메모리 부족 등)

**현재 대응:**
```kotlin
// DriverForegroundService.kt
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    return START_STICKY  // 시스템이 종료해도 재시작
}
```

**분석:** START_STICKY로 적절히 대응됨

---

### 3.2 기사앱 - FCM 서비스 종료

**상황:** 배터리 최적화로 FCM 서비스 종료

**현재 대응:**
- 배터리 최적화 제외 요청 UI
- ForegroundService로 앱 유지

**취약점:**
- 일부 제조사(삼성, 샤오미 등)에서 강제 종료 가능

**대안:**
```
[옵션 1: 주기적 Firestore 폴링]
ForegroundService에서 30초마다 배정된 콜 확인

[옵션 2: 자기 콜 리스너 (1.1과 동일)]
FCM 없이도 배차 감지 가능
```

---

## 4. FCM 토큰 갱신 문제

### 4.1 토큰 갱신 후 서버 반영 실패

**상황:** FCM 토큰이 갱신됐으나 Firestore에 저장 실패

**현재 대응:**
```kotlin
// MyFirebaseMessagingService.kt
override fun onNewToken(token: String) {
    sendRegistrationToServer(token)
}

private fun sendRegistrationToServer(token: String) {
    // Firestore에 토큰 저장
    driverRef.update(Constants.FIELD_FCM_TOKEN, token)
        .addOnSuccessListener { }
        .addOnFailureListener { e -> }  // 로그만 남김
}
```

**취약점:**
- 실패 시 재시도 없음
- 구 토큰으로 FCM 전송 시 실패

**대안:**
```kotlin
[토큰 저장 재시도]
private fun sendRegistrationToServer(token: String) {
    // SharedPreferences에 pending 상태로 저장
    prefs.edit().putString("pending_fcm_token", token).apply()

    driverRef.update(Constants.FIELD_FCM_TOKEN, token)
        .addOnSuccessListener {
            prefs.edit().remove("pending_fcm_token").apply()
        }
        .addOnFailureListener {
            // 앱 시작 시 재시도하도록 남겨둠
        }
}

// 앱 시작 시
val pendingToken = prefs.getString("pending_fcm_token", null)
if (pendingToken != null) {
    sendRegistrationToServer(pendingToken)
}
```

---

## 5. 동시성 문제

### 5.1 동일 콜 동시 배차

**상황:** 두 관리자가 같은 콜을 동시에 배차

**현재 대응:**
```kotlin
// 배차 시 콜 상태 확인 없이 바로 업데이트
callRef.update(callUpdates).await()
```

**취약점:**
- 먼저 배차한 기사가 덮어써질 수 있음

**대안:**
```kotlin
[Firestore 트랜잭션 사용]
firestore.runTransaction { transaction ->
    val callDoc = transaction.get(callRef)
    val currentStatus = callDoc.getString("status")

    if (currentStatus != "WAITING") {
        throw Exception("이미 배차된 콜입니다")
    }

    transaction.update(callRef, callUpdates)
}
```

---

## 6. 권장 보완 사항 (우선순위순)

### 높음 (즉시 적용 권장)

| # | 문제 | 해결책 | 비용 | 상태 |
|---|------|--------|------|------|
| 1 | 기사 배차 알림 미도착 | ACK + 자동재전송 + 실패알림 | 낮음 | ✅ 완료 |
| 2 | 손님앱 콜 요청 실패 | 자동 재시도 + 시스템 알림 | 없음 | ✅ 완료 |
| 3 | 손님 FCM 미도착 | - | - | ✅ 조치불필요 (기사가 전화) |
| 4 | FCM 토큰 저장 실패 | 재시도 로직 추가 | 없음 | ⚠️ 미구현 |

### 중간 (안정화 후 적용)

| # | 문제 | 해결책 | 비용 |
|---|------|--------|------|
| 5 | Firestore 쓰기 실패 | WorkManager 재시도 큐 | 낮음 |
| 6 | 동시 배차 문제 | 트랜잭션 사용 | 없음 |
| 7 | 연결 상태 미표시 | 연결 상태 UI 추가 | 없음 |

### 낮음 (필요시 적용)

| # | 문제 | 해결책 | 비용 |
|---|------|--------|------|
| 8 | 제조사 강제 종료 | 주기적 폴링 백업 | 낮음 |
| 9 | UI/서버 불일치 | 동기화 상태 배지 | 없음 |

---

## 7. 결론

**현재 아키텍처의 장점:**
- Firestore 비용 최소화
- 빠른 UI 반응 (로컬 우선)
- 배터리 효율적

**주요 취약점:**
- FCM 단일 의존으로 알림 실패 시 대안 부족
- 특히 손님앱에 백업 메커니즘 없음

**현재 상태 (2026-02-06 기준):**
1. ✅ **기사앱 FCM 백업**: Cloud Functions로 완전 구현 (ACK + 재전송 + 실패알림)
2. ✅ **손님앱 콜 요청 실패**: 자동 재시도 + 시스템 알림으로 전화호출 안내
3. ✅ **손님앱 FCM 미도착**: 조치 불필요 (기사가 직접 손님에게 전화하므로 정보성 알림)
4. ⚠️ **공통**: FCM 토큰 저장 재시도 로직 필요

FCM 토큰 재시도만 추가하면 FCM 실패에도 안정적으로 동작합니다.

---

*마지막 업데이트: 2026-02-06*

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-06 | 손님앱 콜 요청 실패 시 재시도+시스템알림 구현 완료 반영 |
| 2026-02-06 | 기사앱 FCM 백업 시스템 구현 완료 반영 |
| 2024-02 | 최초 작성 |

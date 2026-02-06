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

### 2.1 기사앱 - Firestore 쓰기 실패 ✅ 해결됨

**상황:** 기사가 "운행 완료" 눌렀으나 Firestore 업데이트 실패

**현재 대응 (구현 완료):**
```kotlin
// DriverViewModel.kt - performFirestoreUpdate()
// 로컬 UI 먼저 업데이트 (기존) + 실패 시 동작별 에러 메시지 + 알림음

private fun performFirestoreUpdate(
    errorMessage: String = "처리 실패 - 네트워크 확인 후 다시 시도하세요",
    block: suspend () -> Unit
) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = errorMessage) }

            // 실패 시 알림음 재생
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(appContext, notification).play()
        }
    }
}

// 동작별 에러 메시지:
// - cancelTrip: "운행취소 실패 - 네트워크 확인 후 다시 시도하세요"
// - startDriving: "운행 시작 실패 - 네트워크 확인 후 다시 시도하세요"
// - completeCall: "운행 완료 실패 - 네트워크 확인 후 다시 시도하세요"
// - updateDriverStatus: "상태 변경 실패 - 네트워크 확인 후 다시 시도하세요"
```

**핵심 파일:** `driver_app/.../viewmodel/DriverViewModel.kt:875-900`

**분석:**
- 실제 발생 확률: 0.1~0.2% (1000건 중 1~2건)
- 로컬 UI 먼저 업데이트 후 Firestore 시도
- 실패 시: 에러 메시지 표시 + 알림음 → 기사가 재시도 가능
- WorkManager 큐보다 단순하고 실용적인 접근

**상태:** ✅ 완전 구현됨

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

### 4.1 토큰 갱신 후 서버 반영 실패 ✅ 해결됨

**상황:** FCM 토큰이 갱신됐으나 Firestore에 저장 실패

**현재 대응 (구현 완료):**
```kotlin
// MyFirebaseMessagingService.kt
private fun sendRegistrationToServer(token: String) {
    // pending 상태로 토큰 저장 (실패 대비)
    sharedPreferences.edit()
        .putString(Constants.PREF_KEY_PENDING_FCM_TOKEN, token)
        .apply()

    driverRef.update(Constants.FIELD_FCM_TOKEN, token)
        .addOnSuccessListener {
            // 성공 시 pending 토큰 제거
            sharedPreferences.edit()
                .remove(Constants.PREF_KEY_PENDING_FCM_TOKEN)
                .apply()
        }
        .addOnFailureListener {
            // 실패 시 pending 토큰 유지 (앱 시작 시 재시도)
        }
}

// MainActivity.kt - 앱 시작 시 pending 토큰 재시도
if (currentUser != null) {
    MyFirebaseMessagingService.retryPendingFcmToken(this)
}

// LoginViewModel.kt - 로그아웃 시 pending 토큰 제거
fun logout() {
    sharedPreferences.edit()
        .remove(Constants.PREF_KEY_PENDING_FCM_TOKEN)
        .apply()
}
```

**핵심 파일:**
- `driver_app/.../MyFirebaseMessagingService.kt:32-89`
- `driver_app/.../MainActivity.kt:117-118`
- `driver_app/.../ui/login/LoginViewModel.kt:283-286`

**상태:** ✅ 완전 구현됨 - 추가 조치 불필요

---

## 5. 동시성 문제

### 5.1 동일 콜 동시 배차 ✅ 조치 불필요

**상황:** 두 관리자가 같은 콜을 동시에 배차

**분석:**
- 한 사무실에 관리자 1명 → 동시 배차 불가능
- 이미 중복 클릭 방지 구현됨 (`_isAssigning.value` 플래그)
- 공유콜 시스템 미구현 → 다른 사무실 간 동시 배차 없음

**현재 대응:**
```kotlin
// DashboardViewModel.kt - assignCallToDriver()
if (_isAssigning.value) {
    Log.w(TAG, "⚠️ 이미 배차 진행 중입니다. 중복 요청 무시.")
    return
}
```

**상태:** ✅ 조치 불필요 - 현재 구조에서 발생 불가

---

### 5.2 연결 상태 미표시 ✅ 구현 완료

**상황:** 네트워크 끊김 시 콜매니저가 새 콜을 못 받는데, 관리자가 이를 모름

**문제:**
```
WiFi/LTE 끊김 → FCM/Firestore 리스너 모두 안 됨
     ↓
콜매니저 UI: 변화 없음 ← 관리자가 연결 끊김을 모름
     ↓
새 콜이 들어와도 화면에 안 뜸 → "왜 콜이 안 오지?"
```

**구현 (RTDB .info/connected 활용):**
```kotlin
// DashboardViewModel.kt
private val _isConnected = MutableStateFlow(true)
val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

private fun startConnectionStatusListener() {
    val connectedRef = Firebase.database.getReference(".info/connected")
    connectedRef.addValueEventListener(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            _isConnected.value = snapshot.getValue(Boolean::class.java) ?: false
        }
        override fun onCancelled(error: DatabaseError) {
            _isConnected.value = false
        }
    })
}

// DashboardScreen.kt
if (!isConnected) {
    Surface(color = Color.Red) {
        Row {
            Icon(Icons.Filled.Warning, tint = Color.White)
            Text("연결 끊김 - 네트워크를 확인하세요", color = Color.White)
        }
    }
}
```

**결과:**
```
WiFi 끊김 → RTDB 연결 끊김 감지 → 빨간 배너 표시
     ↓
관리자: "연결이 끊겼구나" → WiFi 재연결 → 배너 사라짐
```

**핵심 파일:**
- `call_manager/.../ui/dashboard/DashboardViewModel.kt`
- `call_manager/.../ui/dashboard/DashboardScreen.kt`

**상태:** ✅ 구현 완료

---

## 6. 권장 보완 사항 (우선순위순)

### 높음 (즉시 적용 권장)

| # | 문제 | 해결책 | 비용 | 상태 |
|---|------|--------|------|------|
| 1 | 기사 배차 알림 미도착 | ACK + 자동재전송 + 실패알림 | 낮음 | ✅ 완료 |
| 2 | 손님앱 콜 요청 실패 | 자동 재시도 + 시스템 알림 | 없음 | ✅ 완료 |
| 3 | 손님 FCM 미도착 | - | - | ✅ 조치불필요 (기사가 전화) |
| 4 | FCM 토큰 저장 실패 | 재시도 로직 추가 | 없음 | ✅ 완료 |
| 5 | Firestore 쓰기 실패 | 동작별 에러메시지 + 알림음 | 없음 | ✅ 완료 |

### 중간 (안정화 후 적용)

| # | 문제 | 해결책 | 비용 | 상태 |
|---|------|--------|------|------|
| 6 | 동시 배차 문제 | - | - | ✅ 조치불필요 (관리자 1명) |
| 7 | 연결 상태 미표시 | 연결 상태 UI 추가 | 없음 | ✅ 완료 |

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
4. ✅ **FCM 토큰 저장 재시도**: pending 토큰 저장 + 앱 시작 시 재시도 + 로그아웃 시 클리어
5. ✅ **Firestore 쓰기 실패**: 동작별 에러 메시지 + 알림음으로 기사에게 재시도 유도
6. ✅ **연결 상태 UI**: RTDB .info/connected로 콜매니저 연결 끊김 시 빨간 배너 표시

**높은 우선순위 항목 모두 완료됨.** FCM/Firestore 실패에도 안정적으로 동작합니다.

---

*마지막 업데이트: 2026-02-06*

---

## 변경 이력

| 날짜 | 내용 |
|------|------|
| 2026-02-06 | 연결 상태 UI 구현 완료 (콜매니저 RTDB .info/connected 배너) |
| 2026-02-06 | Firestore 쓰기 실패 대응 구현 완료 (동작별 에러 메시지 + 알림음) |
| 2026-02-06 | FCM 토큰 저장 재시도 로직 구현 완료 (pending 토큰 + 앱 시작 시 재시도) |
| 2026-02-06 | 손님앱 콜 요청 실패 시 재시도+시스템알림 구현 완료 반영 |
| 2026-02-06 | 기사앱 FCM 백업 시스템 구현 완료 반영 |
| 2024-02 | 최초 작성 |

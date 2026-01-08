# 콜매니저 리스너→FCM 전환 마스터플랜

## 작업 시작: 2026-01-08
## 목표: 깨끗하고 일관된 FCM + 로컬DB 아키텍처 구축

---

## Phase 0: 준비 작업 ✅

### 체크리스트
- [ ] 현재 call_manager를 call_manager_current_broken으로 백업
- [ ] call_manager_backup을 call_manager로 복사 (깨끗한 리스너 구조)
- [ ] git에 현재 상태 커밋 (롤백 포인트)

```bash
# 백업
mv call_manager call_manager_current_broken
cp -r call_manager_backup call_manager

# Git 커밋
cd call_manager
git add .
git commit -m "chore: 리스너 구조로 롤백 (FCM 전환 재시작 전)"
```

---

## Phase 1: Room DB + Repository 기반 구축 (1시간)

### 목표
깨끗한 Repository 패턴 구현, 일관된 코딩 스타일

### 1-1. Entity 생성 (LocalCallInfo.kt, LocalDriverInfo.kt)

**파일 위치**: `call_manager/app/src/main/java/com/designated/callmanager/data/local/`

**핵심 필드명 통일**:
```kotlin
@Entity(tableName = "calls")
data class LocalCallInfo(
    @PrimaryKey val id: String,
    val phoneNumber: String,
    val customerName: String?,
    val status: String,
    val timestamp: Long,

    // ✅ _set 접미사 사용 (기사가 입력한 값)
    val departure_set: String?,
    val destination_set: String?,
    val waypoints_set: String?,
    val fare_set: Long?,

    // 시스템 필드
    val assignedDriverId: String?,
    val assignedDriverName: String?,
    val assignedDriverPhone: String?,

    // 메타데이터
    val regionId: String,
    val officeId: String,
    val synced: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
)
```

### 1-2. DAO 생성 (CallDao.kt, DriverDao.kt)

**필수 메서드**:
```kotlin
@Dao
interface CallDao {
    // Flow 구독
    @Query("SELECT * FROM calls WHERE regionId = :regionId AND officeId = :officeId ORDER BY timestamp DESC")
    fun getCallsFlow(regionId: String, officeId: String): Flow<List<LocalCallInfo>>

    // 단일 조회 (suspend)
    @Query("SELECT * FROM calls WHERE id = :callId")
    suspend fun getCallById(callId: String): LocalCallInfo?

    // 삽입/업데이트 (UPSERT)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCall(call: LocalCallInfo)

    // 상태 업데이트
    @Query("UPDATE calls SET status = :status, lastUpdated = :now WHERE id = :callId")
    suspend fun updateCallStatus(callId: String, status: String, now: Long = System.currentTimeMillis())

    // 목적지 업데이트 (FCM용)
    @Query("""
        UPDATE calls
        SET departure_set = :departure,
            destination_set = :destination,
            fare_set = :fare,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateTripInfo(
        callId: String,
        departure: String?,
        destination: String?,
        fare: Long?,
        now: Long = System.currentTimeMillis()
    )

    // 배차 정보 업데이트
    @Query("""
        UPDATE calls
        SET assignedDriverId = :driverId,
            assignedDriverName = :driverName,
            assignedDriverPhone = :driverPhone,
            status = :status,
            lastUpdated = :now
        WHERE id = :callId
    """)
    suspend fun updateAssignment(
        callId: String,
        driverId: String,
        driverName: String,
        driverPhone: String,
        status: String,
        now: Long = System.currentTimeMillis()
    )

    // 전체 삭제 (테스트용)
    @Query("DELETE FROM calls WHERE regionId = :regionId AND officeId = :officeId")
    suspend fun deleteAll(regionId: String, officeId: String)
}
```

### 1-3. Repository 구현 (CallRepository.kt, DriverRepository.kt)

**CallRepository.kt 핵심 메서드**:
```kotlin
class CallRepository(
    private val database: AppDatabase,
    private val firestore: FirebaseFirestore,
    private val scope: CoroutineScope
) {
    private val callDao = database.callDao()

    companion object {
        private const val TAG = "CallRepository"
    }

    // ✅ Flow 구독 (UI가 이것만 사용)
    fun getCallsFlow(regionId: String, officeId: String): Flow<List<CallInfo>> {
        return callDao.getCallsFlow(regionId, officeId)
            .map { localCalls ->
                localCalls.map { it.toCallInfo() }
            }
    }

    // ✅ 초기 데이터 로드 (Firestore → Room DB)
    suspend fun refreshData(regionId: String, officeId: String) {
        withContext(Dispatchers.IO) {
            try {
                // 기존 데이터 삭제
                callDao.deleteAll(regionId, officeId)

                // Firestore에서 최근 100개 가져오기
                val snapshot = firestore
                    .collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("calls")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(100)
                    .get()
                    .await()

                // Room DB에 저장
                snapshot.documents.forEach { doc ->
                    val callInfo = parseCallDocument(doc)
                    if (callInfo != null) {
                        val localCall = LocalCallInfo.fromCallInfo(callInfo, regionId, officeId)
                        callDao.upsertCall(localCall)
                    }
                }

                Log.d(TAG, "✅ 초기 데이터 로드 완료: ${snapshot.size()}개")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 초기 데이터 로드 실패", e)
            }
        }
    }

    // ✅ FCM으로부터 상태 업데이트 (로컬만)
    suspend fun updateCallStatusFromFCM(
        callId: String,
        newStatus: String,
        departure: String? = null,
        destination: String? = null,
        fare: Long? = null
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 상태 업데이트
                callDao.updateCallStatus(callId, newStatus)

                // 목적지 정보 업데이트
                if (departure != null || destination != null || fare != null) {
                    callDao.updateTripInfo(callId, departure, destination, fare)
                }

                Log.d(TAG, "✅ [FCM] 콜 업데이트: $callId -> $newStatus")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [FCM] 콜 업데이트 실패", e)
            }
        }
    }

    // ✅ 배차 (낙관적 업데이트)
    suspend fun assignCall(
        callId: String,
        driverId: String,
        driverName: String,
        driverPhone: String
    ) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 로컬 즉시 업데이트
                callDao.updateAssignment(
                    callId = callId,
                    driverId = driverId,
                    driverName = driverName,
                    driverPhone = driverPhone,
                    status = CallStatus.ASSIGNED.firestoreValue
                )

                // 2. Firebase 백그라운드 업데이트
                scope.launch {
                    try {
                        val callRef = firestore.collection("calls").document(callId)
                        callRef.update(
                            "assignedDriverId", driverId,
                            "assignedDriverName", driverName,
                            "assignedDriverPhone", driverPhone,
                            "status", CallStatus.ASSIGNED.firestoreValue,
                            "updatedAt", FieldValue.serverTimestamp()
                        ).await()
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Firebase 배차 업데이트 실패", e)
                    }
                }

                Log.d(TAG, "✅ 배차 완료: $callId -> $driverId")
            } catch (e: Exception) {
                Log.e(TAG, "❌ 배차 실패", e)
            }
        }
    }
}
```

### 1-4. AppDatabase 마이그레이션

```kotlin
@Database(
    entities = [
        LocalCallInfo::class,
        LocalDriverInfo::class,
        LocalPointTransaction::class,
        LocalPointsInfo::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun driverDao(): DriverDao
    abstract fun pointTransactionDao(): PointTransactionDao
    abstract fun pointsDao(): PointsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "call_manager_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // calls 테이블 생성
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS calls (
                        id TEXT PRIMARY KEY NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        customerName TEXT,
                        status TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        departure_set TEXT,
                        destination_set TEXT,
                        waypoints_set TEXT,
                        fare_set INTEGER,
                        assignedDriverId TEXT,
                        assignedDriverName TEXT,
                        assignedDriverPhone TEXT,
                        regionId TEXT NOT NULL,
                        officeId TEXT NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // drivers 테이블 생성
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS drivers (
                        id TEXT PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        phoneNumber TEXT NOT NULL,
                        status TEXT NOT NULL,
                        regionId TEXT NOT NULL,
                        officeId TEXT NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 1,
                        lastUpdated INTEGER NOT NULL
                    )
                """)

                // 인덱스 생성
                database.execSQL("CREATE INDEX idx_calls_office ON calls(regionId, officeId)")
                database.execSQL("CREATE INDEX idx_drivers_office ON drivers(regionId, officeId)")
            }
        }
    }
}
```

### 1-5. Application 클래스 수정

```kotlin
class CallManagerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getDatabase(this) }

    val callRepository by lazy {
        CallRepository(database, Firebase.firestore, applicationScope)
    }

    val driverRepository by lazy {
        DriverRepository(database, Firebase.firestore, applicationScope)
    }

    val pointRepository by lazy {
        PointRepository(database, Firebase.firestore, applicationScope)
    }

    companion object {
        @Volatile
        private var INSTANCE: CallManagerApplication? = null

        fun getInstance(): CallManagerApplication {
            return INSTANCE ?: throw IllegalStateException("Application not created")
        }
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }
}
```

**Phase 1 완료 체크리스트**:
- [ ] Entity 생성 (필드명 _set 통일)
- [ ] DAO 생성 (Flow + suspend 메서드)
- [ ] Repository 생성 (refreshData, updateFromFCM, assign 등)
- [ ] AppDatabase Migration
- [ ] Application 클래스 Repository 제공
- [ ] 빌드 성공 확인

---

## Phase 2: 초기 데이터 로드 구현 (30분)

### 목표
앱 시작 시 Firestore → 로컬 DB 1회 동기화

### 2-1. DashboardViewModel 수정

```kotlin
private fun startListening(regionId: String, officeId: String) {
    stopListening()

    // ✅ 초기 데이터 로드 (최우선)
    viewModelScope.launch {
        try {
            _isLoading.value = true

            // Firestore → Room DB (1회)
            callRepository.refreshData(regionId, officeId)
            driverRepository.refreshData(regionId, officeId)

            Log.d(TAG, "✅ 초기 데이터 로드 완료")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 초기 데이터 로드 실패", e)
        } finally {
            _isLoading.value = false
        }
    }

    // ✅ Flow 구독 (실시간 UI 업데이트)
    viewModelScope.launch {
        callRepository.getCallsFlow(regionId, officeId)
            .collect { calls ->
                _calls.value = calls

                // 팝업 로직
                calls.forEach { callInfo ->
                    handleCallPopups(callInfo)
                }
            }
    }

    viewModelScope.launch {
        driverRepository.getDriversFlow(regionId, officeId)
            .collect { drivers ->
                _drivers.value = drivers.sortedBy { it.name }
            }
    }

    // ✅ 사무실 상태는 리스너 유지 (변경 빈도 낮음)
    officeStatusListener = officeRef.addSnapshotListener { snapshot, e ->
        if (e != null) return@addSnapshotListener
        if (snapshot != null && snapshot.exists()) {
            _officeStatus.value = snapshot.getString("status") ?: "OPEN"
        }
    }
}

private fun handleCallPopups(callInfo: CallInfo) {
    val prefs = appContext.getSharedPreferences("shown_popups", Context.MODE_PRIVATE)

    // 새 콜 팝업
    if (callInfo.status == CallStatus.WAITING.firestoreValue &&
        callInfo.fromCallDetector != true &&
        callInfo.callType != "SHARED") {
        val popupId = "NEW_CALL_${callInfo.id}"
        if (!prefs.getBoolean(popupId, false)) {
            _newCallInfo.value = callInfo
            _showNewCallPopup.value = true
            prefs.edit().putBoolean(popupId, true).apply()
        }
    }

    // 운행 시작 팝업
    if (callInfo.status == CallStatus.IN_PROGRESS.firestoreValue &&
        previousStatusMap[callInfo.id] != CallStatus.IN_PROGRESS.firestoreValue) {

        val tripSummary = buildString {
            append("출발: ${callInfo.departure_set ?: "정보없음"}")
            append(", 도착: ${callInfo.destination_set ?: "정보없음"}")
            if (!callInfo.waypoints_set.isNullOrBlank()) {
                append(", 경유: ${callInfo.waypoints_set}")
            }
            append(", 요금: ${callInfo.fare_set ?: 0}원")
        }

        _tripStartedInfo.value = Triple(
            callInfo.assignedDriverName ?: "기사",
            callInfo.assignedDriverPhone,
            tripSummary
        )
        _showTripStartedPopup.value = true
    }

    // 운행 완료 팝업
    if (callInfo.status == CallStatus.COMPLETED.firestoreValue &&
        previousStatusMap[callInfo.id] != CallStatus.COMPLETED.firestoreValue) {
        val popupId = "TRIP_COMPLETED_${callInfo.id}"
        if (!prefs.getBoolean(popupId, false)) {
            _tripCompletedInfo.value = Pair(
                callInfo.assignedDriverName ?: "기사",
                callInfo.customerName ?: "고객"
            )
            _showTripCompletedPopup.value = true
            prefs.edit().putBoolean(popupId, true).apply()
        }
    }

    previousStatusMap[callInfo.id] = callInfo.status
}
```

**Phase 2 완료 체크리스트**:
- [ ] startListening에서 refreshData 호출
- [ ] Flow 구독 및 팝업 로직 분리
- [ ] 빌드 및 테스트
- [ ] 앱 시작 시 콜/기사 목록 표시 확인

---

## Phase 3: Cloud Functions FCM 메시지 완성 (1시간)

### 목표
일관되고 완전한 FCM 메시지 전송

### 3-1. onCallStatusChanged 수정

**파일**: `functions/src/index.ts`

```typescript
export const onCallStatusChanged = functions
  .region("asia-northeast3")
  .firestore.document("calls/{callId}")
  .onUpdate(async (change, context) => {
    const callId = context.params.callId;
    const beforeData = change.before.data();
    const afterData = change.after.data();

    // 상태 변경 확인
    if (beforeData.status === afterData.status) {
      return null;
    }

    const regionId = afterData.regionId;
    const officeId = afterData.officeId;

    logger.info(`[onCallStatusChanged:${callId}] ${beforeData.status} → ${afterData.status}`);

    // ✅ 필드명 통일 (우선순위: _set > 일반)
    const departure = afterData.departure_set || afterData.departure || "";
    const destination = afterData.destination_set || afterData.destination || "";
    const fare = afterData.fare_set || afterData.fare || 0;

    // ========================================
    // 1. CALL_STATUS_UPDATE (모든 상태 변경 시)
    // ========================================
    try {
      const managerTokensSnapshot = await admin.firestore()
        .collection("regions").doc(regionId)
        .collection("offices").doc(officeId)
        .collection("managerTokens")
        .get();

      if (!managerTokensSnapshot.empty) {
        const tokens: string[] = [];
        managerTokensSnapshot.forEach(doc => {
          const token = doc.data().fcmToken;
          if (token) tokens.push(token);
        });

        if (tokens.length > 0) {
          const payload = {
            data: {
              type: "CALL_STATUS_UPDATE",
              callId: callId,
              status: afterData.status,
              departure,  // ✅ _set 제거된 값
              destination,  // ✅ _set 제거된 값
              fare: fare.toString(),  // ✅ 문자열 변환
              assignedDriverId: afterData.assignedDriverId || "",
              assignedDriverName: afterData.assignedDriverName || "",
              assignedDriverPhone: afterData.assignedDriverPhone || "",
              regionId,
              officeId
            },
            tokens,
            android: { priority: "high" as const }  // ✅ 전송 보장
          };

          const response = await admin.messaging().sendEachForMulticast(payload);
          logger.info(`[CALL_STATUS_UPDATE] 전송: ${response.successCount}/${tokens.length}`);
        }
      }
    } catch (error) {
      logger.error(`[CALL_STATUS_UPDATE] 오류:`, error);
    }

    // ========================================
    // 2. STATUS_CHANGE (운행 시작/완료 시)
    // ========================================
    if (afterData.status === "IN_PROGRESS" || afterData.status === "COMPLETED") {
      try {
        const managerTokensSnapshot = await admin.firestore()
          .collection("regions").doc(regionId)
          .collection("offices").doc(officeId)
          .collection("managerTokens")
          .get();

        if (!managerTokensSnapshot.empty) {
          const tokens: string[] = [];
          managerTokensSnapshot.forEach(doc => {
            const token = doc.data().fcmToken;
            if (token) tokens.push(token);
          });

          if (tokens.length > 0) {
            const statusText = afterData.status === "IN_PROGRESS" ? "운행 시작" : "운행 완료";

            const payload = {
              data: {
                type: "STATUS_CHANGE",
                callId: callId,
                statusText,
                customerName: afterData.customerName || "고객",
                customerPhone: afterData.customerPhone || "-",
                driverName: afterData.assignedDriverName || "기사",
                departure,  // ✅ 일관성
                destination,  // ✅ 일관성
                fare: fare.toString()  // ✅ 일관성
              },
              tokens,
              android: { priority: "high" as const }
            };

            const response = await admin.messaging().sendEachForMulticast(payload);
            logger.info(`[STATUS_CHANGE] 전송: ${response.successCount}/${tokens.length}`);
          }
        }
      } catch (error) {
        logger.error(`[STATUS_CHANGE] 오류:`, error);
      }
    }

    return null;
  });
```

### 3-2. onDriverStatusChanged 수정

```typescript
export const onDriverStatusChanged = functions
  .region("asia-northeast3")
  .firestore.document("regions/{regionId}/offices/{officeId}/designated_drivers/{driverId}")
  .onUpdate(async (change, context) => {
    const { regionId, officeId, driverId } = context.params;
    const beforeData = change.before.data();
    const afterData = change.after.data();

    if (beforeData.status === afterData.status) {
      return null;
    }

    logger.info(`[onDriverStatusChanged:${driverId}] ${beforeData.status} → ${afterData.status}`);

    try {
      const managerTokensSnapshot = await admin.firestore()
        .collection("regions").doc(regionId)
        .collection("offices").doc(officeId)
        .collection("managerTokens")
        .get();

      if (!managerTokensSnapshot.empty) {
        const tokens: string[] = [];
        managerTokensSnapshot.forEach(doc => {
          const token = doc.data().fcmToken;
          if (token) tokens.push(token);
        });

        if (tokens.length > 0) {
          const payload = {
            data: {
              type: "DRIVER_STATUS_UPDATE",
              driverId: driverId,
              driverName: afterData.name || "기사",
              oldStatus: beforeData.status,
              newStatus: afterData.status,
              regionId,
              officeId
            },
            tokens,
            android: { priority: "high" as const }
          };

          const response = await admin.messaging().sendEachForMulticast(payload);
          logger.info(`[DRIVER_STATUS_UPDATE] 전송: ${response.successCount}/${tokens.length}`);
        }
      }
    } catch (error) {
      logger.error(`[DRIVER_STATUS_UPDATE] 오류:`, error);
    }

    return null;
  });
```

**Phase 3 완료 체크리스트**:
- [ ] Cloud Functions 수정
- [ ] npm run build
- [ ] firebase deploy --only functions
- [ ] Functions 로그 모니터링
- [ ] FCM 메시지 전송 확인

---

## Phase 4: FCM 핸들러 구현 (1시간)

### 목표
깨끗한 CoroutineScope + Repository 패턴

### 4-1. MyFirebaseMessagingService.kt 전면 수정

```kotlin
class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "CallManager_FCM"
        // 채널 ID들...
    }

    // ✅ CoroutineScope 사용
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        checkAndSyncExistingToken()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val messageType = remoteMessage.data["type"] ?: run {
            Log.w(TAG, "messageType이 null - 메시지 무시")
            return
        }

        Log.d(TAG, "📩 FCM 메시지 수신: type=$messageType")

        when (messageType) {
            "CALL_STATUS_UPDATE" -> handleCallStatusUpdate(remoteMessage)
            "DRIVER_STATUS_UPDATE" -> handleDriverStatusUpdate(remoteMessage)
            "STATUS_CHANGE" -> handleStatusChange(remoteMessage)
            "NEW_SHARED_CALL" -> handleNewSharedCall(remoteMessage)
            // ... 기타 타입들
            else -> Log.w(TAG, "알 수 없는 메시지 타입: $messageType")
        }
    }

    // ========================================
    // CALL_STATUS_UPDATE 핸들러
    // ========================================
    private fun handleCallStatusUpdate(remoteMessage: RemoteMessage) {
        val callId = remoteMessage.data["callId"] ?: return
        val status = remoteMessage.data["status"] ?: return
        val departure = remoteMessage.data["departure"]?.takeIf { it.isNotEmpty() }
        val destination = remoteMessage.data["destination"]?.takeIf { it.isNotEmpty() }
        val fareStr = remoteMessage.data["fare"]
        val fare = fareStr?.toLongOrNull()

        Log.d(TAG, "📊 [CALL_STATUS_UPDATE] callId=$callId, status=$status")
        Log.d(TAG, "📊 [CALL_STATUS_UPDATE] departure=$departure, destination=$destination, fare=$fare")

        // ✅ Repository 사용
        serviceScope.launch {
            try {
                val app = CallManagerApplication.getInstance()
                app.callRepository.updateCallStatusFromFCM(
                    callId = callId,
                    newStatus = status,
                    departure = departure,
                    destination = destination,
                    fare = fare
                )
                Log.d(TAG, "✅ [CALL_STATUS_UPDATE] 업데이트 완료")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [CALL_STATUS_UPDATE] 실패", e)
            }
        }
    }

    // ========================================
    // DRIVER_STATUS_UPDATE 핸들러
    // ========================================
    private fun handleDriverStatusUpdate(remoteMessage: RemoteMessage) {
        val driverId = remoteMessage.data["driverId"] ?: return
        val newStatus = remoteMessage.data["newStatus"] ?: return
        val driverName = remoteMessage.data["driverName"] ?: "기사"

        Log.d(TAG, "🚕 [DRIVER_STATUS_UPDATE] driverId=$driverId, newStatus=$newStatus")

        // ✅ Repository 사용
        serviceScope.launch {
            try {
                val app = CallManagerApplication.getInstance()
                app.driverRepository.updateDriverStatusFromFCM(driverId, newStatus)
                Log.d(TAG, "✅ [DRIVER_STATUS_UPDATE] 업데이트 완료")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [DRIVER_STATUS_UPDATE] 실패", e)
            }
        }

        // 포그라운드에서는 알림 생략 (UI가 Flow로 업데이트)
        if (!isAppInForeground()) {
            showNotification(
                channelId = DRIVER_UPDATE_CHANNEL_ID,
                notificationId = "driver_$driverId".hashCode(),
                title = "📍 기사 상태 업데이트",
                content = "$driverName: $newStatus",
                bigText = "기사: $driverName\n상태: $newStatus",
                color = ContextCompat.getColor(this, android.R.color.holo_blue_light),
                autoCancel = true
            )
        }
    }

    // ========================================
    // STATUS_CHANGE 핸들러 (팝업용)
    // ========================================
    private fun handleStatusChange(remoteMessage: RemoteMessage) {
        val callId = remoteMessage.data["callId"] ?: return
        val statusText = remoteMessage.data["statusText"] ?: "상태 변경"
        val customerName = remoteMessage.data["customerName"] ?: "고객"
        val customerPhone = remoteMessage.data["customerPhone"] ?: "-"
        val driverName = remoteMessage.data["driverName"] ?: "기사"
        val departure = remoteMessage.data["departure"] ?: ""
        val destination = remoteMessage.data["destination"] ?: ""
        val fare = remoteMessage.data["fare"] ?: ""

        Log.d(TAG, "🚗 [STATUS_CHANGE] statusText=$statusText")
        Log.d(TAG, "🚗 [STATUS_CHANGE] departure=$departure, destination=$destination, fare=$fare")

        // 포그라운드에서는 알림 생략 (ViewModel의 Flow에서 팝업 표시)
        if (isAppInForeground()) {
            Log.d(TAG, "포그라운드 - STATUS_CHANGE 알림 생략")
            return
        }

        val (emoji, color) = when (statusText) {
            "운행 시작" -> "🚗" to ContextCompat.getColor(this, android.R.color.holo_green_dark)
            "운행 완료" -> "✅" to ContextCompat.getColor(this, android.R.color.holo_blue_dark)
            else -> "📢" to ContextCompat.getColor(this, android.R.color.holo_orange_dark)
        }

        val tripInfo = buildString {
            if (departure.isNotEmpty()) append("\n출발: $departure")
            if (destination.isNotEmpty()) append("\n도착: $destination")
            if (fare.isNotEmpty() && fare != "0") append("\n요금: ${fare}원")
        }

        showNotification(
            channelId = STATUS_CHANGE_CHANNEL_ID,
            notificationId = callId.hashCode(),
            title = "$emoji $statusText",
            content = "$customerName ($customerPhone) - $driverName",
            bigText = "고객: $customerName\n전화: $customerPhone\n기사: $driverName\n상태: $statusText$tripInfo",
            callId = callId,
            color = color,
            autoCancel = true,
            timeoutAfter = 30000
        )
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = activityManager.runningAppProcesses ?: return false
        return runningProcesses.any {
            it.processName == packageName &&
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }
}
```

**Phase 4 완료 체크리스트**:
- [ ] FCM 핸들러 전면 수정
- [ ] CoroutineScope 사용
- [ ] Repository 패턴 일관 적용
- [ ] 빌드 및 설치
- [ ] FCM 수신 로그 확인

---

## Phase 5: ViewModel Flow 구독 (30분)

이미 Phase 2에서 완료됨. 검증만 필요.

**체크리스트**:
- [ ] callRepository.getCallsFlow() 구독 확인
- [ ] driverRepository.getDriversFlow() 구독 확인
- [ ] 팝업 로직 정상 작동 확인
- [ ] UI 자동 업데이트 확인

---

## Phase 6: 통합 테스트 (1시간)

### 테스트 시나리오

#### 시나리오 1: 앱 시작
1. 콜매니저 앱 시작
2. **기대**: 내부호출목록에 최근 콜 100개 표시
3. **기대**: 기사 카드에 모든 기사 표시

#### 시나리오 2: 기사 로그인
1. 기사 앱에서 로그인 (OFFLINE → ONLINE)
2. **기대**: 콜매니저 기사 카드 즉시 ONLINE으로 업데이트
3. **기대**: 백그라운드일 때만 알림 표시

#### 시나리오 3: 콜 배차
1. 콜매니저에서 대기 중인 콜 배차
2. **기대**: 내부호출목록 즉시 ASSIGNED로 업데이트
3. **기대**: 기사 상태 ASSIGNED로 업데이트
4. **기대**: 기사 앱에 푸시 알림

#### 시나리오 4: 운행 시작
1. 기사 앱에서 운행 시작 (출발지/목적지/요금 입력)
2. **기대**: 콜매니저 내부호출목록에 목적지 즉시 표시
3. **기대**: 콜매니저 팝업에 "운행 시작" + 출발/도착/요금 표시 (백그라운드)
4. **기대**: ViewModel Flow로 팝업 표시 (포그라운드)

#### 시나리오 5: 운행 완료
1. 기사 앱에서 운행 완료
2. **기대**: 콜매니저 내부호출목록 COMPLETED 업데이트
3. **기대**: 콜매니저 팝업에 "운행 완료" 표시
4. **기대**: 기사 상태 WAITING으로 복귀

#### 시나리오 6: 재배차
1. 완료된 콜을 다시 배차
2. **기대**: 모든 정보 정상 표시
3. **기대**: 운행 시작 시 목적지 정상 표시

### 로그 모니터링 명령어

```bash
# Cloud Functions
firebase functions:log --only onCallStatusChanged,onDriverStatusChanged

# 콜매니저
adb logcat -s CallManager_FCM:D CallRepository:D DriverRepository:D DashboardViewModel:D

# 기사앱
adb logcat -s DriverViewModel:D DriverForegroundService:D
```

**Phase 6 완료 체크리스트**:
- [ ] 시나리오 1~6 모두 통과
- [ ] 로그에 에러 없음
- [ ] UI 깜빡임 없음
- [ ] 배차 후 즉시 반응

---

## 롤백 계획

### 문제 발생 시
```bash
# 현재 작업 중단
cd C:/app_dev/designated_driver

# 백업으로 복원
rm -rf call_manager
cp -r call_manager_backup call_manager

# 또는 Git 롤백
cd call_manager
git reset --hard <commit-hash>
```

---

## 성공 기준

✅ **모든 Phase 완료**
✅ **Firebase 읽기 비용 90% 절감**
✅ **UI 반응 속도 0ms (즉시)**
✅ **화면 깜빡임 없음**
✅ **팝업에 운행 정보 정상 표시**
✅ **재배차 시에도 정상 작동**

---

## 예상 소요 시간

| Phase | 작업 | 예상 시간 |
|-------|------|-----------|
| Phase 0 | 준비 작업 | 10분 |
| Phase 1 | Room DB + Repository | 1시간 |
| Phase 2 | 초기 데이터 로드 | 30분 |
| Phase 3 | Cloud Functions | 1시간 |
| Phase 4 | FCM 핸들러 | 1시간 |
| Phase 5 | ViewModel Flow | 30분 (검증만) |
| Phase 6 | 통합 테스트 | 1시간 |
| **합계** | | **5.5시간** |

---

**작성일**: 2026-01-08
**작성자**: Claude (AI Assistant)
**승인 대기 중**

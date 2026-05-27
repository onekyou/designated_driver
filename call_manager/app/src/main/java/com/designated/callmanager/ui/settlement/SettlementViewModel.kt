package com.designated.callmanager.ui.settlement

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.data.SessionInfo
import com.designated.callmanager.data.SettlementBackup
import com.designated.callmanager.data.repository.SettlementBackupRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.*
import com.designated.callmanager.data.local.CallManagerDatabase
import com.designated.callmanager.data.local.SettlementEntity
import com.designated.callmanager.data.local.SettlementRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.designated.callmanager.data.local.SessionEntity
import com.designated.callmanager.data.local.CreditPersonEntity
import com.designated.callmanager.data.local.CreditEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.designated.callmanager.data.settlement.SettlementSession
import com.designated.callmanager.data.settlement.CallSettlement
import com.designated.callmanager.data.settlement.SettlementMetadata
import com.designated.callmanager.data.settlement.SettlementTotals
import com.designated.callmanager.data.settlement.CarryOverStatus
import com.designated.callmanager.data.settlement.DailySettlementStatus
import com.designated.callmanager.data.settlement.DriverCarryOver
import com.designated.callmanager.data.settlement.DriverCarryOverSummary
import com.designated.callmanager.data.settlement.DriverDailySettlement
import com.designated.callmanager.data.settlement.DriverDailySettlementSummary
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

class SettlementViewModel(application: Application) : AndroidViewModel(application) {

    private val _settlementList = MutableStateFlow<List<SettlementData>>(emptyList())
    val settlementList: StateFlow<List<SettlementData>> = _settlementList

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val firestore = FirebaseFirestore.getInstance()

    private var currentProvinceId: String? = null
    private var currentCityId: String? = null
    private var currentOfficeId: String? = null
    private var lastClearedMillisCache: Long = 0L
    private val prefs = getApplication<Application>().getSharedPreferences("settlement_prefs", Context.MODE_PRIVATE)

    private val _clearedDates = MutableStateFlow<Set<String>>(emptySet())
    val clearedDates: StateFlow<Set<String>> = _clearedDates

    private val _allTripsCleared = MutableStateFlow(false)
    val allTripsCleared: StateFlow<Boolean> = _allTripsCleared

    private val _officeShareRatio = MutableStateFlow(60)
    val officeShareRatio: StateFlow<Int> = _officeShareRatio

    private val _sessionList = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessionList: StateFlow<List<SessionInfo>> = _sessionList

    private var sessionsListener: ListenerRegistration? = null
    private var callsListener: ListenerRegistration? = null
    private var callsListener2: ListenerRegistration? = null  // completedAt 기준 리스너
    private var managerDirectListener: ListenerRegistration? = null

    // 관리자 직접운행 오늘 집계 (정산 세션과 별개)
    data class ManagerDirectStats(
        val count: Int = 0,
        val totalFare: Long = 0,
        val cash: Long = 0,
        val card: Long = 0,
        val credit: Long = 0
    )
    private val _managerDirectStats = MutableStateFlow(ManagerDirectStats())
    val managerDirectStats: StateFlow<ManagerDirectStats> = _managerDirectStats.asStateFlow()

    // 직접운행 콜을 SettlementData 형태로 노출 — 대기/전체 탭 합산용
    private val _directRunTrips = MutableStateFlow<List<SettlementData>>(emptyList())
    val directRunTrips: StateFlow<List<SettlementData>> = _directRunTrips.asStateFlow()

    // office 문서의 settlementLastCleared 실시간 값 (업무 마감 시 갱신됨)
    private val _officeLastCleared = MutableStateFlow(0L)
    // 직접운행 raw 캐시 — officeLastCleared 변경 시 재필터링
    private var directRunRawCache: List<SettlementData> = emptyList()
    private var officeLastClearedListener: ListenerRegistration? = null

    // 기사별 settlementLastCleared 맵 (driverId → millis)
    // 개별 기사 정산 완료(퇴근) 시 갱신된 기사별 마감 시점
    private val _driverLastClearedMap = MutableStateFlow<Map<String, Long>>(emptyMap())
    val driverLastClearedMap: StateFlow<Map<String, Long>> = _driverLastClearedMap.asStateFlow()

    private val database = CallManagerDatabase.getInstance(getApplication())
    private val repository = SettlementRepository(database)
    private val creditDao = database.creditDao()

    // 세션별 직접운행 건수 (일일 탭 뱃지용)
    val managerCountsBySession: StateFlow<Map<String, Int>> = repository.dao
        .flowManagerCountsBySession()
        .map { list -> list.associate { it.sessionId to it.count } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // 백업/복원 관련
    private val backupRepository = SettlementBackupRepository(getApplication())

    // 백업 상태 관리
    sealed class BackupState {
        object Idle : BackupState()
        object Loading : BackupState()
        data class Success(val message: String) : BackupState()
        data class Error(val error: String) : BackupState()
    }

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val _backupList = MutableStateFlow<List<SettlementBackup>>(emptyList())
    val backupList: StateFlow<List<SettlementBackup>> = _backupList.asStateFlow()

    private val _hasCloudBackups = MutableStateFlow(false)
    val hasCloudBackups: StateFlow<Boolean> = _hasCloudBackups.asStateFlow()

    // 이월 정산 (기사별 미지급금) 관련 StateFlow
    private val _carryOverList = MutableStateFlow<List<DriverCarryOverSummary>>(emptyList())
    val carryOverList: StateFlow<List<DriverCarryOverSummary>> = _carryOverList.asStateFlow()

    // 일일 정산 (기사별 업무마감) 관련 StateFlow
    private val _dailySettlementList = MutableStateFlow<List<DriverDailySettlementSummary>>(emptyList())
    val dailySettlementList: StateFlow<List<DriverDailySettlementSummary>> = _dailySettlementList.asStateFlow()

    // 정산 확인 중 (중복 클릭 방지)
    private val _isConfirming = MutableStateFlow(false)
    val isConfirming: StateFlow<Boolean> = _isConfirming.asStateFlow()

    init {
        viewModelScope.launch {
            repository.flowActive().collect { entities ->
                _settlementList.value = entities.map { it.toData() }
                _allTripsCleared.value = entities.isEmpty()
            }
        }
        viewModelScope.launch {
            repository.flowSessions().collect { sess ->
                _sessionList.value = sess.map { SessionInfo(it.sessionId, null, it.totalFare, it.totalTrips) }
            }
        }
        viewModelScope.launch {
            creditDao.getAllCreditPersons().collect { entities ->
                val creditPersonsWithEntries = entities.map { entity ->
                    CreditPerson(
                        id = entity.id,
                        name = entity.name,
                        phone = entity.phone,
                        memo = entity.memo,
                        amount = entity.totalAmount,
                        entries = emptyList()
                    )
                }
                _creditPersons.value = creditPersonsWithEntries

                entities.forEach { entity ->
                    launch {
                        val entries = creditDao.getCreditEntriesByPerson(entity.id).map { entryEntity ->
                            CreditEntry(
                                date = entryEntity.date,
                                departure = entryEntity.departure,
                                destination = entryEntity.destination,
                                amount = entryEntity.amount
                            )
                        }
                        _creditPersons.value = _creditPersons.value.map { person ->
                            if (person.id == entity.id) {
                                person.copy(entries = entries)
                            } else person
                        }
                    }
                }
            }
        }

        val loginPrefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val province = loginPrefs.getString("provinceId", null)
        val city = loginPrefs.getString("cityId", null)
        val office = loginPrefs.getString("officeId", null)
        if (!province.isNullOrBlank() && !city.isNullOrBlank() && !office.isNullOrBlank()) {
            loadSettlementData(province, city, office)
            startOfficeLastClearedListener(province, city, office)
            startManagerDirectListener(province, city, office)
        }
    }

    /**
     * office 문서의 settlementLastCleared 실시간 감시.
     * 업무 마감 시 CF가 이 필드를 갱신하면 직접운행 트립 필터가 즉시 반영됨.
     */
    private fun startOfficeLastClearedListener(provinceId: String, cityId: String, officeId: String) {
        officeLastClearedListener?.remove()
        officeLastClearedListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .addSnapshotListener { doc, e ->
                if (e != null || doc == null) return@addSnapshotListener
                val ts = doc.getTimestamp("settlementLastCleared")?.toDate()?.time ?: 0L
                if (_officeLastCleared.value != ts) {
                    _officeLastCleared.value = ts
                    // 직접운행 raw 캐시 재필터링
                    applyDirectRunFilter()
                }
            }
    }

    private fun applyDirectRunFilter() {
        val cutoff = _officeLastCleared.value
        _directRunTrips.value = directRunRawCache
            .filter { it.completedAt > cutoff }
            .sortedByDescending { it.completedAt }
    }

    /**
     * 관리자 직접운행 오늘 집계 리스너.
     * handledByManager==true 콜을 실시간 감시하여 위젯에 반영.
     */
    private fun startManagerDirectListener(provinceId: String, cityId: String, officeId: String) {
        managerDirectListener?.remove()
        managerDirectListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("handledByManager", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "managerDirect listener error", e)
                    return@addSnapshotListener
                }
                val today = getTodaySessionDate()
                var count = 0
                var fare = 0L
                var cash = 0L
                var card = 0L
                var credit = 0L
                val trips = mutableListOf<SettlementData>()

                snapshots?.documents?.forEach { doc ->
                    val ct = doc.getTimestamp("completedAt")?.toDate()?.time ?: return@forEach
                    val workDate = calculateWorkDate(ct)
                    val f = doc.getLong("fareFinal") ?: doc.getLong("fare_set") ?: 0L
                    val pm = doc.getString("paymentMethod") ?: ""
                    val c = doc.getLong("cashReceived") ?: 0L
                    val cr = doc.getLong("creditAmount") ?: 0L

                    // 오늘 집계 카드용
                    if (workDate == today) {
                        count++
                        fare += f
                        credit += cr
                        when (pm) {
                            "현금" -> cash += f
                            "이체", "카드" -> card += f
                            "현금+포인트" -> cash += c
                        }
                    }

                    // 대기/전체 탭용 SettlementData 변환 (filter는 applyDirectRunFilter에서 적용)
                    trips.add(
                        SettlementData(
                            callId = doc.id,
                            driverName = "관리자",
                            customerName = doc.getString("customerName") ?: "",
                            departure = doc.getString("departure_set") ?: "",
                            destination = doc.getString("destination_set") ?: "",
                            waypoints = doc.getString("waypoints_set") ?: "",
                            fare = f.toInt(),
                            paymentMethod = pm,
                            cardAmount = null,
                            cashAmount = c.toInt(),
                            creditAmount = cr.toInt(),
                            pointsUsed = (doc.getLong("pointsUsed") ?: 0L).toInt(),
                            completedAt = ct,
                            driverId = "MANAGER",
                            regionId = currentProvinceId ?: "",
                            officeId = currentOfficeId ?: "",
                            workDate = workDate
                        )
                    )
                }
                _managerDirectStats.value = ManagerDirectStats(count, fare, cash, card, credit)
                directRunRawCache = trips
                applyDirectRunFilter()
            }
    }

    fun updateOfficeShareRatio(newRatio: Int) {
        val validRatio = newRatio.coerceIn(30, 90)
        _officeShareRatio.value = validRatio

        // Firestore에도 저장
        val province = currentProvinceId
        val city = currentCityId
        val office = currentOfficeId
        if (province != null && city != null && office != null) {
            firestore.collection("provinces").document(province)
                .collection("cities").document(city)
                .collection("offices").document(office)
                .update("depositRatio", validRatio)
                .addOnSuccessListener {
                    Log.d("SettlementViewModel", "depositRatio 저장 완료: $validRatio")
                }
                .addOnFailureListener { e ->
                    Log.e("SettlementViewModel", "depositRatio 저장 실패", e)
                }
        }
    }

    data class CreditEntry(
        val date: String,
        val departure: String,
        val destination: String,
        val amount: Int
    )

    data class CreditPerson(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val phone: String,
        val memo: String = "",
        val amount: Int = 0,
        val entries: List<CreditEntry> = emptyList()
    )

    private val _creditPersons = MutableStateFlow<List<CreditPerson>>(emptyList())
    val creditPersons: StateFlow<List<CreditPerson>> = _creditPersons

    fun addOrIncrementCredit(name: String, phone: String, addAmount: Int, detail: CreditEntry? = null) {
        if (addAmount <= 0) return

        viewModelScope.launch {
            if (detail != null) {
                creditDao.addOrIncrementCredit(
                    name = name,
                    phone = phone,
                    amount = addAmount,
                    customerName = name,
                    driverName = "미지정",
                    date = detail.date,
                    departure = detail.departure,
                    destination = detail.destination
                )
            } else {
                creditDao.addOrIncrementCredit(name, phone, addAmount)
            }
        }
    }

    fun reduceCredit(id: String, reduceAmount: Int) {
        viewModelScope.launch {
            creditDao.decrementCreditAmount(id, reduceAmount)
            val person = creditDao.getAllCreditPersons().first().find { it.id == id }
            if (person?.totalAmount == 0) {
                creditDao.deleteCreditPersonById(id)
            }
        }
    }

    private fun calculateWorkDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        if (calendar.get(Calendar.HOUR_OF_DAY) < 10) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    fun loadSettlementData(provinceId: String, cityId: String, officeId: String) {
        currentProvinceId = provinceId
        currentCityId = cityId
        currentOfficeId = officeId
        val localKey = "${provinceId}_${cityId}_${officeId}_lastCleared"
        lastClearedMillisCache = prefs.getLong(localKey, 0L)

        _isLoading.value = true
        _error.value = null

        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .get()
            .addOnSuccessListener { officeDoc ->
                val lastClearedMillis = officeDoc.getTimestamp("settlementLastCleared")?.toDate()?.time ?: 0L

                // 분배비율 읽기 (기본값 60)
                val savedRatio = officeDoc.getLong("depositRatio")?.toInt() ?: 60
                _officeShareRatio.value = savedRatio.coerceIn(30, 90)

                fetchCompletedCalls(provinceId, cityId, officeId, lastClearedMillis)

            }
            .addOnFailureListener { e ->
                _error.value = e.localizedMessage
                _isLoading.value = false
            }
    }

    private fun fetchCompletedCalls(provinceId: String, cityId: String, officeId: String, lastCleared: Long) {
        val effectiveLastCleared = maxOf(lastCleared, lastClearedMillisCache)

        // 1단계: 기사별 settlementLastCleared 조회
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers")
            .get()
            .addOnSuccessListener { driversSnapshot ->
                val driverClearedMap = mutableMapOf<String, Long>()
                driversSnapshot.documents.forEach { doc ->
                    val driverCleared = doc.getTimestamp("settlementLastCleared")?.toDate()?.time ?: 0L
                    driverClearedMap[doc.id] = driverCleared
                }
                _driverLastClearedMap.value = driverClearedMap
                Log.d("SettlementViewModel", "Driver settlementLastCleared map loaded: ${driverClearedMap.size} drivers")

                // 2단계: COMPLETED 콜 조회
                fetchCompletedCallsWithDriverFilter(provinceId, cityId, officeId, effectiveLastCleared)
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to load driver settlementLastCleared", e)
                // 실패 시 기존 방식(office-level만)으로 폴백
                fetchCompletedCallsWithDriverFilter(provinceId, cityId, officeId, effectiveLastCleared)
            }
    }

    private fun fetchCompletedCallsWithDriverFilter(provinceId: String, cityId: String, officeId: String, effectiveLastCleared: Long) {
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener { result ->
                val trips = result.documents.mapNotNull { doc ->
                    try {
                        // 관리자 직접운행 콜은 정산 목록에서 제외 (별도 위젯 집계)
                        if (doc.getBoolean("handledByManager") == true) return@mapNotNull null

                        val completedTimestamp = doc.getTimestamp("completedAt")?.toDate()?.time
                            ?: doc.getTimestamp("updatedAt")?.toDate()?.time
                            ?: System.currentTimeMillis()

                        // 기사별 settlementLastCleared와 office-level 중 더 큰 값으로 필터
                        val driverId = doc.getString("assignedDriverId") ?: ""
                        val driverCleared = _driverLastClearedMap.value[driverId] ?: 0L
                        val cutoff = maxOf(effectiveLastCleared, driverCleared)

                        if (completedTimestamp <= cutoff) return@mapNotNull null // 필터링

                        val fareAmount = doc.getLong("fareFinal")?.toInt()
                            ?: doc.getLong("fare_set")?.toInt()
                            ?: 0

                        val cashReceived = doc.getLong("cashReceived")?.toInt()
                        val creditAmount = doc.getLong("creditAmount")?.toInt()
                        val pointsUsed = doc.getLong("pointsUsed")?.toInt()

                        SettlementData(
                            callId = doc.id,
                            driverName = doc.getString("assignedDriverName") ?: "N/A",
                            customerName = doc.getString("customerName") ?: "N/A",
                            departure = doc.getString("departure_set") ?: "N/A",
                            destination = doc.getString("destination_set") ?: "N/A",
                            waypoints = doc.getString("waypoints_set") ?: "",
                            fare = fareAmount,
                            paymentMethod = doc.getString("paymentMethod") ?: "N/A",
                            cardAmount = null,
                            cashAmount = cashReceived,  // null 허용하여 포인트 계산 정확도 향상
                            creditAmount = creditAmount ?: 0,
                            pointsUsed = pointsUsed ?: 0,
                            completedAt = completedTimestamp,
                            driverId = doc.getString("assignedDriverId") ?: "",
                            regionId = provinceId,
                            officeId = officeId,
                            workDate = calculateWorkDate(completedTimestamp)
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                viewModelScope.launch {
                    val newTrips = trips.filter { trip ->
                        repository.dao.existsById(trip.callId) == 0
                    }
                    if (newTrips.isNotEmpty()) {
                        repository.insertAll(newTrips.map { SettlementEntity.fromData(it) })
                        // 외상 자동 등록 제거 — 대기 탭에서 "외상 등록" 버튼으로 관리자가 수동 확정
                    }
                }
                // 만약 사용자가 "전체내역 초기화" 후 새 콜이 도착하면 자동으로 리스트를 다시 보여주기 위해 플래그 해제
                if (trips.isNotEmpty()) {
                    _allTripsCleared.value = false
                }
                _isLoading.value = false
            }
            .addOnSuccessListener {
                startCallsListener(provinceId, cityId, officeId, effectiveLastCleared)
            }
            .addOnFailureListener { e ->
                _error.value = e.localizedMessage
                _isLoading.value = false
            }
    }

    /** 신규 COMPLETED 콜에 대한 실시간 리스너 */
    private fun startCallsListener(provinceId: String, cityId: String, officeId: String, sinceMillis: Long) {
        callsListener?.remove()
        callsListener2?.remove()
        val baseQuery = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "COMPLETED")

        callsListener = baseQuery
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .whereGreaterThan("updatedAt", Date(sinceMillis))
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                val newEntities = snapshots?.documentChanges?.mapNotNull { dc ->
                    if (dc.type != com.google.firebase.firestore.DocumentChange.Type.ADDED &&
                        dc.type != com.google.firebase.firestore.DocumentChange.Type.MODIFIED) return@mapNotNull null
                    val doc = dc.document
                    try {
                        if (doc.getBoolean("handledByManager") == true) return@mapNotNull null

                        val completedTimestamp = doc.getTimestamp("completedAt")?.toDate()?.time
                            ?: doc.getTimestamp("updatedAt")?.toDate()?.time
                            ?: System.currentTimeMillis()

                        // 기사별 settlementLastCleared 필터
                        val driverId = doc.getString("assignedDriverId") ?: ""
                        val driverCleared = _driverLastClearedMap.value[driverId] ?: 0L
                        if (completedTimestamp <= driverCleared) return@mapNotNull null

                        val fareAmount = doc.getLong("fareFinal")?.toInt() ?: doc.getLong("fare_set")?.toInt() ?: 0

                        SettlementEntity(
                            callId = doc.id,
                            driverName = doc.getString("assignedDriverName") ?: "N/A",
                            customerName = doc.getString("customerName") ?: "N/A",
                            departure = doc.getString("departure_set") ?: "N/A",
                            destination = doc.getString("destination_set") ?: "N/A",
                            waypoints = doc.getString("waypoints_set") ?: "",
                            fare = fareAmount,
                            paymentMethod = doc.getString("paymentMethod") ?: "N/A",
                            cardAmount = doc.getLong("cardAmount")?.toInt(),
                            cashAmount = doc.getLong("cashReceived")?.toInt(),
                            creditAmount = doc.getLong("creditAmount")?.toInt() ?: 0,
                            completedAt = completedTimestamp,
                            driverId = driverId,
                            regionId = provinceId,
                            officeId = officeId,
                            workDate = calculateWorkDate(completedTimestamp)
                        )
                    } catch (ex: Exception) {
                        null
                    }
                } ?: emptyList()

                if (newEntities.isNotEmpty()) {
                    viewModelScope.launch {
                        repository.insertAll(newEntities)
                        _allTripsCleared.value = false
                    }
                }
            }

        callsListener2 = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "COMPLETED")
            .orderBy("completedAt", Query.Direction.ASCENDING)
            .whereGreaterThan("completedAt", Date(sinceMillis))
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }

                val newEntities = snapshots?.documentChanges?.mapNotNull { dc ->
                    if (dc.type != com.google.firebase.firestore.DocumentChange.Type.ADDED &&
                        dc.type != com.google.firebase.firestore.DocumentChange.Type.MODIFIED) return@mapNotNull null
                    val doc = dc.document
                    try {
                        if (doc.getBoolean("handledByManager") == true) return@mapNotNull null

                        val completedTimestamp = doc.getTimestamp("completedAt")?.toDate()?.time
                            ?: System.currentTimeMillis()

                        // 기사별 settlementLastCleared 필터
                        val driverId = doc.getString("assignedDriverId") ?: ""
                        val driverCleared = _driverLastClearedMap.value[driverId] ?: 0L
                        if (completedTimestamp <= driverCleared) return@mapNotNull null

                        val fareAmount = doc.getLong("fareFinal")?.toInt() ?: doc.getLong("fare_set")?.toInt() ?: 0

                        SettlementEntity(
                            callId = doc.id,
                            driverName = doc.getString("assignedDriverName") ?: "N/A",
                            customerName = doc.getString("customerName") ?: "N/A",
                            departure = doc.getString("departure_set") ?: "N/A",
                            destination = doc.getString("destination_set") ?: "N/A",
                            waypoints = doc.getString("waypoints_set") ?: "",
                            fare = fareAmount,
                            paymentMethod = doc.getString("paymentMethod") ?: "N/A",
                            cardAmount = doc.getLong("cardAmount")?.toInt(),
                            cashAmount = doc.getLong("cashReceived")?.toInt(),
                            creditAmount = doc.getLong("creditAmount")?.toInt() ?: 0,
                            completedAt = completedTimestamp,
                            driverId = doc.getString("assignedDriverId") ?: "",
                            regionId = provinceId,
                            officeId = officeId,
                            workDate = calculateWorkDate(completedTimestamp)
                        )
                    } catch (ex: Exception) {
                        null
                    }
                } ?: emptyList()

                if (newEntities.isNotEmpty()) {
                    viewModelScope.launch {
                        val reallyNewEntities = newEntities.filter { entity ->
                            repository.dao.existsById(entity.callId) == 0
                        }
                        if (reallyNewEntities.isNotEmpty()) {
                            repository.insertAll(reallyNewEntities)
                            _allTripsCleared.value = false
                            // 외상 자동 등록 제거 — 대기 탭에서 관리자가 수동 확정
                        }
                    }
                }
            }
    }

    /** 실시간 세션 카드 리스너 */
    private fun startSessionsListener(regionId: String, officeId: String) = Unit

    override fun onCleared() {
        super.onCleared()
        sessionsListener?.remove()
        callsListener?.remove()
        callsListener2?.remove()
        managerDirectListener?.remove()
        officeLastClearedListener?.remove()
    }

    fun clearLocalSettlement() {
        val now = System.currentTimeMillis()
        updateLastClearedTimestamp(now)
        _allTripsCleared.value = true
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    fun clearSettlementForDate(workDate: String) {
        _clearedDates.value = _clearedDates.value + workDate
        viewModelScope.launch {
            repository.deleteWorkDate(workDate)
        }
    }

    fun clearAllTrips() {
        val trips = _settlementList.value
        val mgrTrips = _directRunTrips.value
        if (trips.isEmpty() && mgrTrips.isEmpty()) return

        // 일일 세션 집계에는 실기사 + 직접운행 모두 포함
        val totalTrips = trips.size + mgrTrips.size
        val totalFare  = (trips.sumOf { it.fare } + mgrTrips.sumOf { it.fare }).toLong()
        val newSessionId = System.currentTimeMillis().toString()
        val closingTime = System.currentTimeMillis()
        val ratio = _officeShareRatio.value

        viewModelScope.launch {
            repository.insertSession(
                SessionEntity(
                    sessionId   = newSessionId,
                    endAt       = closingTime,
                    totalTrips  = totalTrips,
                    totalFare   = totalFare
                )
            )
            repository.markTripsFinalized(trips.map { it.callId }, newSessionId)

            // 직접운행 트립도 Room settlements에 세션 소속으로 기록 (일일 탭 상세보기용)
            if (mgrTrips.isNotEmpty()) {
                val mgrEntities = mgrTrips.map { trip ->
                    SettlementEntity.fromData(trip).copy(
                        isFinalized = true,
                        sessionId = newSessionId
                    )
                }
                repository.insertAll(mgrEntities)
            }

            // office 문서 settlementLastCleared 갱신 → 직접운행 리스너가 재필터링해서 리스트에서 제거
            val p = currentProvinceId
            val c = currentCityId
            val o = currentOfficeId
            if (p != null && c != null && o != null) {
                firestore.collection("provinces").document(p)
                    .collection("cities").document(c)
                    .collection("offices").document(o)
                    .update("settlementLastCleared", com.google.firebase.Timestamp.now())
                    .addOnFailureListener { e ->
                        Log.e("SettlementViewModel", "office settlementLastCleared 갱신 실패", e)
                    }
            }

            // ✅ 기사별 미지급금 계산 및 carryOver 저장
            val driverTrips = trips.groupBy { it.driverId }
            driverTrips.forEach { (driverId, driverTripList) ->
                if (driverId.isBlank()) return@forEach

                // 기사 몫 계산
                val driverTotalFare = driverTripList.sumOf { it.fare }
                val driverDeposit = (driverTotalFare * ratio / 100)
                val driverShare = driverTotalFare - driverDeposit

                // 현금 수령액 계산
                val cashReceived = driverTripList.sumOf { trip ->
                    when {
                        trip.paymentMethod == "현금" -> trip.fare
                        trip.paymentMethod.startsWith("현금+") -> trip.cashAmount ?: 0
                        else -> 0
                    }
                }

                // ✅ dailySettlement 초기화 (다음 세션을 위해)
                clearDailySettlement(driverId)
            }
        }

        // 마감 시간을 DashboardViewModel이 사용할 수 있도록 기록
        val province = currentProvinceId
        val city = currentCityId
        val office = currentOfficeId
        if (province != null && city != null && office != null) {
            val closingPrefs = getApplication<Application>().getSharedPreferences("closing_times", Context.MODE_PRIVATE)
            closingPrefs.edit()
                .putLong("last_closing_time_${province}_${city}_${office}", closingTime)
                .apply()
        }

        _allTripsCleared.value = true
        _clearedDates.value    = emptySet()
    }

    suspend fun getTripsForSession(sessionId: String): List<SettlementData> =
        repository.flowTripsBySession(sessionId).first().map { it.toData() }

    /**
     * lastClearedMillisCache 값을 갱신하고 SharedPreferences 에도 저장한다.
     */
    private fun updateLastClearedTimestamp(ts: Long) {
        lastClearedMillisCache = ts
        val p = currentProvinceId ?: return
        val c = currentCityId ?: return
        val o = currentOfficeId ?: return
        val key = "${p}_${c}_${o}_lastCleared"
        prefs.edit().putLong(key, ts).apply()
    }

    private val _creditedTripIds = MutableStateFlow<Set<String>>(emptySet())
    val creditedTripIds: StateFlow<Set<String>> = _creditedTripIds.asStateFlow()

    fun markTripCredited(callId: String) {
        _creditedTripIds.value = _creditedTripIds.value + callId
    }

    /**
     * 고객 전화번호를 비동기로 가져오는 헬퍼 (간이 버전)
     * 현재 slim ViewModel에는 calls 컬렉션을 직접 조회하는 기능이 없으므로
     * 임시로 Room 캐시에서 검색하거나 null 콜백.
     */
    fun fetchPhoneForCall(callId: String, cb: (String?) -> Unit) {
        val province = currentProvinceId
        val city = currentCityId
        val office = currentOfficeId
        if(province==null || city==null || office==null) { cb(null); return }
        firestore.collection("provinces").document(province)
            .collection("cities").document(city)
            .collection("offices").document(office)
            .collection("calls").document(callId)
            .get()
            .addOnSuccessListener { snap -> cb(snap.getString("phoneNumber")) }
            .addOnFailureListener { cb(null) }
    }

    // ====== 백업/복원 기능 ======

    /**
     * 정산 데이터를 클라우드에 백업
     */
    fun backupSettlements() {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            _backupState.value = BackupState.Error("사무실 정보가 설정되지 않았습니다")
            return
        }

        val settlements = _settlementList.value
        if (settlements.isEmpty()) {
            _backupState.value = BackupState.Error("백업할 정산 데이터가 없습니다")
            return
        }

        viewModelScope.launch {
            _backupState.value = BackupState.Loading

            try {
                val result = backupRepository.backupSettlements(provinceId, cityId, officeId, settlements)

                if (result.isSuccess) {
                    _backupState.value = BackupState.Success("${settlements.size}건의 정산 데이터를 백업했습니다")
                    checkCloudBackups() // 백업 목록 새로고침
                    Log.d("SettlementViewModel", "백업 성공: ${result.getOrNull()}")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "알 수 없는 오류"
                    _backupState.value = BackupState.Error("백업 실패: $error")
                    Log.e("SettlementViewModel", "백업 실패", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                _backupState.value = BackupState.Error("백업 중 오류 발생: ${e.message}")
                Log.e("SettlementViewModel", "백업 예외", e)
            }
        }
    }

    /**
     * 클라우드에서 정산 데이터 복원
     */
    fun restoreSettlements(backupId: String? = null) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            _backupState.value = BackupState.Error("사무실 정보가 설정되지 않았습니다")
            return
        }

        viewModelScope.launch {
            _backupState.value = BackupState.Loading

            try {
                val result = backupRepository.restoreSettlements(provinceId, cityId, officeId, backupId)

                if (result.isSuccess) {
                    val restoredSettlements = result.getOrNull() ?: emptyList()

                    // 앱 삭제/재설치 시 전체 복원을 위해 기존 데이터 모두 삭제
                    repository.clearAll()

                    restoredSettlements.forEach { settlement ->
                        val entity = SettlementEntity.fromData(settlement)
                        repository.addTrip(entity)
                    }

                    _backupState.value = BackupState.Success("${restoredSettlements.size}건의 정산 데이터를 복원했습니다")
                    Log.d("SettlementViewModel", "복원 성공: ${restoredSettlements.size}건")
                } else {
                    val error = result.exceptionOrNull()?.message ?: "알 수 없는 오류"
                    _backupState.value = BackupState.Error("복원 실패: $error")
                    Log.e("SettlementViewModel", "복원 실패", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                _backupState.value = BackupState.Error("복원 중 오류 발생: ${e.message}")
                Log.e("SettlementViewModel", "복원 예외", e)
            }
        }
    }

    /**
     * 클라우드 백업 존재 여부 확인
     */
    fun checkCloudBackups() {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            return
        }

        viewModelScope.launch {
            try {
                val hasBackups = backupRepository.hasBackups(provinceId, cityId, officeId)
                _hasCloudBackups.value = hasBackups

                // 백업 목록도 로드
                backupRepository.getBackupList(provinceId, cityId, officeId).collect { backups ->
                    _backupList.value = backups
                }

            } catch (e: Exception) {
                Log.e("SettlementViewModel", "백업 확인 실패", e)
                _hasCloudBackups.value = false
            }
        }
    }

    /**
     * 특정 백업 삭제
     */
    fun deleteBackup(backupId: String) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            return
        }

        viewModelScope.launch {
            try {
                val result = backupRepository.deleteBackup(provinceId, cityId, officeId, backupId)

                if (result.isSuccess) {
                    _backupState.value = BackupState.Success("백업이 삭제되었습니다")
                    checkCloudBackups() // 목록 새로고침
                } else {
                    val error = result.exceptionOrNull()?.message ?: "알 수 없는 오류"
                    _backupState.value = BackupState.Error("백업 삭제 실패: $error")
                }
            } catch (e: Exception) {
                _backupState.value = BackupState.Error("백업 삭제 중 오류: ${e.message}")
                Log.e("SettlementViewModel", "백업 삭제 예외", e)
            }
        }
    }

    /**
     * 백업 상태 초기화
     */
    fun clearBackupState() {
        _backupState.value = BackupState.Idle
    }

    /**
     * 앱 업데이트 감지 시 백업 제안
     */
    fun checkForAppUpdateAndSuggestBackup() {
        val prefs = getApplication<Application>().getSharedPreferences("app_version_prefs", Context.MODE_PRIVATE)
        val currentVersion = getApplication<Application>().packageManager
            .getPackageInfo(getApplication<Application>().packageName, 0).versionCode
        val lastVersion = prefs.getInt("last_version", 0)

        if (currentVersion > lastVersion && _settlementList.value.isNotEmpty()) {
            // 앱이 업데이트되었고 정산 데이터가 있는 경우
            _backupState.value = BackupState.Success("앱이 업데이트되었습니다. 정산 데이터를 백업하시겠습니까?")

            // 버전 정보 업데이트
            prefs.edit().putInt("last_version", currentVersion).apply()
        }
    }

    // ====== 공유 정산 문서 동기화 기능 (기사앱과 연동) ======

    private val _sharedSessionVersion = MutableStateFlow(0L)
    val sharedSessionVersion: StateFlow<Long> = _sharedSessionVersion.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    sealed class SyncState {
        object Idle : SyncState()
        object Syncing : SyncState()
        data class Success(val message: String) : SyncState()
        data class Error(val error: String) : SyncState()
    }

    /**
     * 공유 정산 문서 경로 생성
     */
    private fun getSharedSessionPath(provinceId: String, cityId: String, officeId: String, sessionDate: String): String {
        return "provinces/$provinceId/cities/$cityId/offices/$officeId/settlementSessions/$sessionDate"
    }

    /**
     * 오늘 날짜의 세션 ID 생성 (근무일 기준)
     */
    private fun getTodaySessionDate(): String {
        val calendar = Calendar.getInstance()
        // 오전 10시 이전이면 전날로 처리
        if (calendar.get(Calendar.HOUR_OF_DAY) < 10) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    /**
     * 공유 정산 문서에서 데이터 동기화 (기사앱이 업로드한 데이터 확인)
     * 버전 기반 동기화 - 변경이 있을 때만 가져옴
     */
    fun checkAndSyncFromSharedSession() {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            Log.w("SettlementViewModel", "Office info not set, skipping sync")
            return
        }

        val sessionDate = getTodaySessionDate()
        val sessionRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("settlementSessions").document(sessionDate)

        viewModelScope.launch {
            _syncState.value = SyncState.Syncing

            sessionRef.get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val serverVersion = doc.getLong("metadata.version") ?: 0L
                        val localVersion = _sharedSessionVersion.value

                        if (serverVersion > localVersion) {
                            // 새 버전이 있음 - 동기화 필요
                            val session = SettlementSession.fromDocument(doc)
                            if (session != null) {
                                processSharedSession(session, sessionDate)
                                _sharedSessionVersion.value = serverVersion
                                _syncState.value = SyncState.Success("동기화 완료: ${session.calls.size}건")
                                Log.d("SettlementViewModel", "Synced from shared session v$serverVersion: ${session.calls.size} calls")
                            } else {
                                _syncState.value = SyncState.Error("세션 파싱 실패")
                            }
                        } else {
                            _syncState.value = SyncState.Idle
                            Log.d("SettlementViewModel", "Already up to date (v$localVersion)")
                        }
                    } else {
                        _syncState.value = SyncState.Idle
                        Log.d("SettlementViewModel", "No shared session for $sessionDate")
                    }
                }
                .addOnFailureListener { e ->
                    _syncState.value = SyncState.Error("동기화 실패: ${e.message}")
                    Log.e("SettlementViewModel", "Sync failed", e)
                }
        }
    }

    /**
     * 공유 세션의 콜 데이터를 로컬에 반영
     */
    private fun processSharedSession(session: SettlementSession, sessionDate: String) {
        viewModelScope.launch {
            session.calls.forEach { call ->
                // 이미 존재하는 콜인지 확인
                if (repository.dao.existsById(call.callId) == 0) {
                    // 새 콜 추가
                    val entity = SettlementEntity(
                        callId = call.callId,
                        driverName = call.driverName,
                        customerName = call.customerName,
                        departure = call.departure,
                        destination = call.destination,
                        waypoints = "",
                        fare = call.fare.toInt(),
                        paymentMethod = call.paymentMethod,
                        cardAmount = null,
                        cashAmount = if (call.cashReceived > 0) call.cashReceived.toInt() else null,
                        creditAmount = call.creditAmount.toInt(),
                        completedAt = call.completedAt?.toDate()?.time ?: System.currentTimeMillis(),
                        driverId = call.driverId,
                        regionId = currentProvinceId ?: "",
                        officeId = currentOfficeId ?: "",
                        workDate = sessionDate
                    )
                    repository.addTrip(entity)
                    Log.d("SettlementViewModel", "Added call from shared session: ${call.callId}")
                }
            }
        }
    }

    /**
     * 콜 정산 확인 처리 (사무실에서 확인)
     * Firestore 트랜잭션을 사용하여 원자적으로 처리
     */
    fun confirmSettlement(callId: String, onResult: (Boolean, String) -> Unit) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            onResult(false, "사무실 정보가 설정되지 않았습니다")
            return
        }

        val sessionDate = getTodaySessionDate()
        val sessionRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("settlementSessions").document(sessionDate)

        viewModelScope.launch {
            firestore.runTransaction { transaction ->
                val doc = transaction.get(sessionRef)

                if (!doc.exists()) {
                    throw Exception("정산 세션이 존재하지 않습니다")
                }

                val session = SettlementSession.fromDocument(doc)
                    ?: throw Exception("세션 파싱 실패")

                // 해당 콜 찾기
                val callIndex = session.calls.indexOfFirst { it.callId == callId }
                if (callIndex == -1) {
                    throw Exception("해당 콜을 찾을 수 없습니다")
                }

                // 콜 리스트 업데이트 (확인 처리)
                val updatedCalls = session.calls.toMutableList()
                val confirmedCall = updatedCalls[callIndex].copy(
                    confirmedByOffice = true,
                    syncedAt = Timestamp.now()
                )
                updatedCalls[callIndex] = confirmedCall

                // 메타데이터 업데이트 (버전 증가)
                val updatedMetadata = session.metadata.copy(
                    version = session.metadata.version + 1,
                    lastUpdatedAt = Timestamp.now(),
                    lastUpdatedBy = "call_manager"
                )

                // 트랜잭션 업데이트
                transaction.update(sessionRef, mapOf(
                    "calls" to updatedCalls.map { it.toMap() },
                    "metadata" to updatedMetadata.toMap()
                ))

                // 로컬 버전 업데이트
                _sharedSessionVersion.value = updatedMetadata.version

                callId // 성공 시 반환값
            }.addOnSuccessListener {
                Log.d("SettlementViewModel", "Call $callId confirmed successfully")
                onResult(true, "확인 완료")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to confirm call $callId", e)
                onResult(false, e.message ?: "확인 실패")
            }
        }
    }


    /**
     * 로컬 데이터로 정산 세션 생성
     */
    private fun createSettlementSessionFromLocalData(sessionDate: String): SettlementSession {
        val trips = _settlementList.value.filter { it.workDate == sessionDate }
        val ratio = _officeShareRatio.value

        val totalFare = trips.sumOf { it.fare.toLong() }
        val totalDeposit = (totalFare * ratio / 100)
        val totalDriverShare = totalFare - totalDeposit
        val totalCash = trips.sumOf { trip ->
            when {
                trip.paymentMethod == "현금" -> trip.fare.toLong()
                trip.paymentMethod.startsWith("현금+") -> trip.cashAmount?.toLong() ?: 0L
                else -> 0L
            }
        }
        val totalCard = trips.filter { it.paymentMethod == "이체" || it.paymentMethod == "카드" }.sumOf { it.fare.toLong() }
        val totalCredit = trips.sumOf { it.creditAmount.toLong() }

        val calls = trips.map { trip ->
            CallSettlement(
                callId = trip.callId,
                driverId = trip.driverId,
                driverName = trip.driverName,
                customerName = trip.customerName,
                customerPhone = "",
                departure = trip.departure,
                destination = trip.destination,
                fare = trip.fare.toLong(),
                paymentMethod = trip.paymentMethod,
                cashReceived = trip.cashAmount?.toLong() ?: 0L,
                creditAmount = trip.creditAmount.toLong(),
                pointsUsed = trip.pointsUsed.toLong(),
                completedAt = Timestamp(Date(trip.completedAt)),
                confirmedByOffice = true,
                syncedAt = Timestamp.now()
            )
        }

        return SettlementSession(
            metadata = SettlementMetadata(
                version = 1,
                lastUpdatedAt = Timestamp.now(),
                lastUpdatedBy = "call_manager",
                depositRatio = ratio,
                createdAt = Timestamp.now(),
                isFinalized = true
            ),
            totals = SettlementTotals(
                totalFare = totalFare,
                totalDeposit = totalDeposit,
                totalDriverShare = totalDriverShare,
                totalCash = totalCash,
                totalCard = totalCard,
                totalCredit = totalCredit,
                totalPoints = trips.sumOf { it.pointsUsed.toLong() },
                callCount = trips.size
            ),
            calls = calls
        )
    }

    /**
     * Firestore calls 컬렉션에서 직접 조회하여 정산 세션 생성 (폴백용)
     * Room DB가 per-driver 필터로 불완전할 수 있으므로 Firestore를 원본으로 사용
     */
    private fun createSettlementSessionFromFirestore(
        provinceId: String,
        cityId: String,
        officeId: String,
        sessionDate: String,
        onComplete: (SettlementSession?) -> Unit
    ) {
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener { result ->
                val ratio = _officeShareRatio.value
                val calls = result.documents.mapNotNull { doc ->
                    try {
                        if (doc.getBoolean("handledByManager") == true) return@mapNotNull null

                        val completedTimestamp = doc.getTimestamp("completedAt")?.toDate()?.time
                            ?: doc.getTimestamp("updatedAt")?.toDate()?.time
                            ?: return@mapNotNull null
                        val workDate = calculateWorkDate(completedTimestamp)
                        if (workDate != sessionDate) return@mapNotNull null

                        val fareAmount = doc.getLong("fareFinal")
                            ?: doc.getLong("fare_set")
                            ?: 0L

                        CallSettlement(
                            callId = doc.id,
                            driverId = doc.getString("assignedDriverId") ?: "",
                            driverName = doc.getString("assignedDriverName") ?: "N/A",
                            customerName = doc.getString("customerName") ?: "N/A",
                            customerPhone = doc.getString("customerPhone") ?: "",
                            departure = doc.getString("departure_set") ?: "N/A",
                            destination = doc.getString("destination_set") ?: "N/A",
                            fare = fareAmount,
                            paymentMethod = doc.getString("paymentMethod") ?: "N/A",
                            cashReceived = doc.getLong("cashReceived") ?: 0L,
                            creditAmount = doc.getLong("creditAmount") ?: 0L,
                            pointsUsed = doc.getLong("pointsUsed") ?: 0L,
                            completedAt = Timestamp(Date(completedTimestamp)),
                            confirmedByOffice = true,
                            syncedAt = Timestamp.now()
                        )
                    } catch (e: Exception) {
                        null
                    }
                }

                if (calls.isEmpty()) {
                    onComplete(null)
                    return@addOnSuccessListener
                }

                val totalFare = calls.sumOf { it.fare }
                val totalDeposit = totalFare * ratio / 100
                val totalDriverShare = totalFare - totalDeposit
                val totalCash = calls.sumOf { call ->
                    when {
                        call.paymentMethod == "현금" -> call.fare
                        call.paymentMethod.startsWith("현금+") -> call.cashReceived
                        else -> 0L
                    }
                }
                val totalCard = calls.filter { it.paymentMethod == "이체" || it.paymentMethod == "카드" }.sumOf { it.fare }
                val totalCredit = calls.sumOf { it.creditAmount }
                val totalPoints = calls.sumOf { it.pointsUsed }

                val session = SettlementSession(
                    metadata = SettlementMetadata(
                        version = 1,
                        lastUpdatedAt = Timestamp.now(),
                        lastUpdatedBy = "call_manager",
                        depositRatio = ratio,
                        createdAt = Timestamp.now(),
                        isFinalized = true
                    ),
                    totals = SettlementTotals(
                        totalFare = totalFare,
                        totalDeposit = totalDeposit,
                        totalDriverShare = totalDriverShare,
                        totalCash = totalCash,
                        totalCard = totalCard,
                        totalCredit = totalCredit,
                        totalPoints = totalPoints,
                        callCount = calls.size
                    ),
                    calls = calls
                )
                onComplete(session)
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to create session from Firestore", e)
                // 최종 폴백: 로컬 데이터 사용
                val localSession = createSettlementSessionFromLocalData(sessionDate)
                onComplete(if (localSession.calls.isEmpty()) null else localSession)
            }
    }

    /**
     * 동기화 상태 초기화
     */
    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    // ====== 이월 정산 (기사별 미지급금) 관련 기능 ======

    /**
     * 업무마감 시 기사의 dailySettlement 초기화
     * 다음 세션에서 "확인완료" 상태가 남아있지 않도록 함
     */
    private fun clearDailySettlement(driverId: String) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) return

        val driverRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)

        viewModelScope.launch {
            driverRef.update(
                mapOf(
                    "dailySettlement" to com.google.firebase.firestore.FieldValue.delete()
                )
            ).addOnSuccessListener {
                Log.d("SettlementViewModel", "DailySettlement cleared for driver $driverId")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to clear dailySettlement", e)
            }
        }
    }

}
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
    private var carryOverListener: ListenerRegistration? = null

    // 기사별 settlementLastCleared 맵 (driverId → millis)
    // 개별 기사 정산 완료(퇴근) 시 갱신된 기사별 마감 시점
    private var driverLastClearedMap: Map<String, Long> = emptyMap()

    private val database = CallManagerDatabase.getInstance(getApplication())
    private val repository = SettlementRepository(database)
    private val creditDao = database.creditDao()

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
            startCarryOverListener(province, city, office)
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

        if (calendar.get(Calendar.HOUR_OF_DAY) < 6) {
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
                driverLastClearedMap = driverClearedMap
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
                        val completedTimestamp = doc.getTimestamp("completedAt")?.toDate()?.time
                            ?: doc.getTimestamp("updatedAt")?.toDate()?.time
                            ?: System.currentTimeMillis()

                        // 기사별 settlementLastCleared와 office-level 중 더 큰 값으로 필터
                        val driverId = doc.getString("assignedDriverId") ?: ""
                        val driverCleared = driverLastClearedMap[driverId] ?: 0L
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

                        newTrips.forEach { trip ->
                            if (trip.creditAmount > 0) {
                                val creditDetail = CreditEntry(
                                    date = calculateWorkDate(trip.completedAt),
                                    departure = trip.departure,
                                    destination = trip.destination,
                                    amount = trip.creditAmount
                                )
                                addOrIncrementCredit(
                                    name = trip.customerName,
                                    phone = "",
                                    addAmount = trip.creditAmount,
                                    detail = creditDetail
                                )
                            }
                        }
                    } else {
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
                        val completedTimestamp = doc.getTimestamp("completedAt")?.toDate()?.time
                            ?: doc.getTimestamp("updatedAt")?.toDate()?.time
                            ?: System.currentTimeMillis()

                        // 기사별 settlementLastCleared 필터
                        val driverId = doc.getString("assignedDriverId") ?: ""
                        val driverCleared = driverLastClearedMap[driverId] ?: 0L
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
                        val completedTimestamp = doc.getTimestamp("completedAt")?.toDate()?.time
                            ?: System.currentTimeMillis()

                        // 기사별 settlementLastCleared 필터
                        val driverId = doc.getString("assignedDriverId") ?: ""
                        val driverCleared = driverLastClearedMap[driverId] ?: 0L
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

                            reallyNewEntities.forEach { entity ->
                                if (entity.creditAmount > 0) {
                                    val creditDetail = CreditEntry(
                                        date = entity.workDate,
                                        departure = entity.departure,
                                        destination = entity.destination,
                                        amount = entity.creditAmount
                                    )
                                    addOrIncrementCredit(
                                        name = entity.customerName,
                                        phone = "",
                                        addAmount = entity.creditAmount,
                                        detail = creditDetail
                                    )
                                }
                            }
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
        carryOverListener?.remove()
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
        if (trips.isEmpty()) return

        val totalTrips = trips.size
        val totalFare  = trips.sumOf { it.fare }.toLong()
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

                // 실납입금 = 현금수령 - 기사몫
                // 양수: 기사가 사무실에 납부 / 음수: 사무실이 기사에게 지급
                val todayResult = cashReceived - driverShare

                // 누적 미지급금에 반영 (양수면 감소, 음수면 증가)
                Log.d("SettlementViewModel", "기사 $driverId 오늘 정산: $todayResult 원 (양수=납부, 음수=미지급)")
                processCarryOverOnFinalize(driverId, todayResult.toLong())

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
        // 새벽 6시 이전이면 전날로 처리
        if (calendar.get(Calendar.HOUR_OF_DAY) < 6) {
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
     * 전체 정산 세션 마감 처리 및 기사 알림
     * 사무실에서 일일 정산 마감 시 호출
     * Cloud Function을 통해 마감 처리하고 로그인 상태의 기사에게만 알림 전송
     */
    fun finalizeSettlementSession(onResult: (Boolean, String) -> Unit) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            onResult(false, "사무실 정보가 설정되지 않았습니다")
            return
        }

        val sessionDate = getTodaySessionDate()

        // 세션이 없는 경우 먼저 생성
        val sessionRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("settlementSessions").document(sessionDate)

        viewModelScope.launch {
            sessionRef.get().addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    // 세션이 없으면 Firestore calls에서 직접 조회하여 생성
                    createSettlementSessionFromFirestore(provinceId, cityId, officeId, sessionDate) { session ->
                        if (session != null) {
                            sessionRef.set(session.toMap())
                                .addOnSuccessListener {
                                    callFinalizeFunction(provinceId, cityId, officeId, sessionDate, onResult)
                                }
                                .addOnFailureListener { e ->
                                    Log.e("SettlementViewModel", "Failed to create session", e)
                                    onResult(false, "세션 생성 실패: ${e.message}")
                                }
                        } else {
                            onResult(false, "정산 데이터가 없습니다")
                        }
                    }
                } else {
                    // 세션이 있으면 바로 Cloud Function 호출
                    callFinalizeFunction(provinceId, cityId, officeId, sessionDate, onResult)
                }
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to check session", e)
                onResult(false, "세션 확인 실패: ${e.message}")
            }
        }
    }

    /**
     * Cloud Function 호출하여 마감 처리 및 기사 알림
     */
    private fun callFinalizeFunction(
        provinceId: String,
        cityId: String,
        officeId: String,
        sessionDate: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val functions = Firebase.functions("asia-northeast3")

        val data = hashMapOf(
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "sessionDate" to sessionDate
        )

        functions.getHttpsCallable("finalizeSettlementAndNotifyDrivers")
            .call(data)
            .addOnSuccessListener { result ->
                val response = result.getData() as? Map<*, *>
                val success = response?.get("success") as? Boolean ?: false

                if (success) {
                    val sent = (response?.get("sent") as? Number)?.toInt() ?: 0
                    val skipped = (response?.get("skipped") as? Number)?.toInt() ?: 0
                    val wasRefinalized = response?.get("wasRefinalized") as? Boolean ?: false

                    val message = if (sent > 0) {
                        if (wasRefinalized) {
                            "재마감 완료! ${sent}명의 기사에게 알림 전송됨"
                        } else {
                            "마감 완료! ${sent}명의 기사에게 알림 전송됨"
                        }
                    } else {
                        "마감 완료 (로그인 중인 기사 없음)"
                    }

                    Log.d("SettlementViewModel", "Finalize success: sent=$sent, skipped=$skipped, wasRefinalized=$wasRefinalized")
                    onResult(true, message)
                } else {
                    val error = response?.get("error") as? String ?: "알 수 없는 오류"
                    Log.e("SettlementViewModel", "Finalize failed: $error")
                    onResult(false, error)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Cloud Function call failed", e)
                onResult(false, "서버 오류: ${e.message}")
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
     * 기사별 이월 정산 및 일일 정산 데이터 실시간 리스너
     * drivers 컬렉션에서 carryOver, dailySettlement 필드를 감시
     */
    private fun startCarryOverListener(provinceId: String, cityId: String, officeId: String) {
        carryOverListener?.remove()

        val today = getTodaySessionDate()

        // drivers 컬렉션에서 해당 사무실 소속 기사들 감시
        carryOverListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "CarryOver listener error", e)
                    return@addSnapshotListener
                }

                val carryOvers = mutableListOf<DriverCarryOverSummary>()
                val dailySettlements = mutableListOf<DriverDailySettlementSummary>()

                snapshots?.documents?.forEach { doc ->
                    val driverId = doc.id
                    val driverName = doc.getString("name") ?: "이름없음"

                    // carryOver 파싱
                    val carryOverMap = doc.get("carryOver") as? Map<String, Any?>
                    val carryOver = DriverCarryOver.fromMap(carryOverMap)

                    // dailySettlement 파싱
                    @Suppress("UNCHECKED_CAST")
                    val dailySettlementMap = doc.get("dailySettlement") as? Map<String, Any?>
                    val dailySettlement = DriverDailySettlement.fromMap(dailySettlementMap)

                    // 디버그: 리스너 재발동 시 각 기사의 carryOver 상태 확인
                    Log.d("SettlementViewModel", "CarryOver listener - driver=$driverName($driverId), balance=${carryOver.balance}, status=${carryOver.status}")

                    // carryOver가 있는 경우 리스트에 추가 (SETTLED도 포함하여 null fallback 방지)
                    if (carryOver.balance != 0L || carryOver.status == CarryOverStatus.TRANSFERRED || carryOver.status == CarryOverStatus.SETTLED) {
                        carryOvers.add(
                            DriverCarryOverSummary(
                                driverId = driverId,
                                driverName = driverName,
                                balance = carryOver.balance,
                                todayAmount = carryOver.todayAmount,
                                status = carryOver.status,
                                transferredAt = carryOver.transferredAt
                            )
                        )
                    }

                    // 오늘 날짜의 dailySettlement가 있고, 마감/확인/거절 상태인 경우 리스트에 추가
                    if (dailySettlement.date == today &&
                        (dailySettlement.status == DailySettlementStatus.PENDING_CONFIRM ||
                         dailySettlement.status == DailySettlementStatus.CONFIRMED ||
                         dailySettlement.status == DailySettlementStatus.REJECTED)) {
                        dailySettlements.add(
                            DriverDailySettlementSummary(
                                driverId = driverId,
                                driverName = driverName,
                                dailySettlement = dailySettlement,
                                carryOverBalance = carryOver.balance,
                                carryOverStatus = carryOver.status
                            )
                        )
                    }
                }

                _carryOverList.value = carryOvers.sortedByDescending { it.balance }
                _dailySettlementList.value = dailySettlements.sortedByDescending { it.dailySettlement?.submittedAt }

                Log.d("SettlementViewModel", "CarryOver list updated: ${carryOvers.size} drivers")
                Log.d("SettlementViewModel", "DailySettlement list updated: ${dailySettlements.size} drivers")
            }
    }

    /**
     * 이체하기 - 기사에게 미지급금 이체 처리
     * 상태를 TRANSFERRED로 변경하고 이체 시간 기록
     * @param driverName 기사 이름 (FCM 알림용)
     * @param carryOverBalance 기존 이월분 (Firestore에서 읽은 값)
     * @param todayUnpaid 오늘 로컬에서 계산된 미지급금
     */
    fun transferCarryOver(
        driverId: String,
        driverName: String,
        carryOverBalance: Long,
        todayUnpaid: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            onResult(false, "사무실 정보가 설정되지 않았습니다")
            return
        }

        val loginPrefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val adminId = loginPrefs.getString("adminId", null) ?: "unknown"

        val driverRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)

        // 총 미지급금 = 기존 이월분 + 오늘분
        val totalBalance = carryOverBalance + todayUnpaid

        // set + merge 사용 (carryOver 필드가 없어도 생성됨)
        val carryOverData = mapOf(
            "carryOver" to mapOf(
                "balance" to totalBalance,
                "todayAmount" to todayUnpaid,
                "status" to CarryOverStatus.TRANSFERRED.name,
                "transferredAt" to Timestamp.now(),
                "transferredBy" to adminId,
                "lastUpdatedAt" to Timestamp.now()
            )
        )

        viewModelScope.launch {
            driverRef.set(carryOverData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener {
                    Log.d("SettlementViewModel", "CarryOver transferred for driver: $driverId, balance=$totalBalance")

                    // 로컬 carryOverList 즉시 업데이트 (리스너 대기 없이)
                    updateLocalCarryOver(driverId, driverName, totalBalance, todayUnpaid, CarryOverStatus.TRANSFERRED)

                    // FCM 알림 전송
                    sendCarryOverNotification(provinceId, cityId, officeId, driverId, driverName, totalBalance)

                    onResult(true, "이체 완료")
                }.addOnFailureListener { e ->
                    Log.e("SettlementViewModel", "Failed to transfer carryOver", e)
                    onResult(false, "이체 실패: ${e.message}")
                }
        }
    }

    /**
     * 로컬 carryOverList 즉시 업데이트
     */
    private fun updateLocalCarryOver(
        driverId: String,
        driverName: String,
        balance: Long,
        todayAmount: Long,
        status: CarryOverStatus
    ) {
        val currentList = _carryOverList.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.driverId == driverId }

        val newSummary = DriverCarryOverSummary(
            driverId = driverId,
            driverName = driverName,
            balance = balance,
            todayAmount = todayAmount,
            status = status,
            transferredAt = if (status == CarryOverStatus.TRANSFERRED) Timestamp.now() else null
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = newSummary
        } else {
            currentList.add(newSummary)
        }

        _carryOverList.value = currentList.sortedByDescending { it.balance }
        Log.d("SettlementViewModel", "Local carryOverList updated: $driverId -> $status")
    }

    /**
     * 미지급금 이체 FCM 알림 전송
     */
    private fun sendCarryOverNotification(
        provinceId: String,
        cityId: String,
        officeId: String,
        driverId: String,
        driverName: String,
        amount: Long
    ) {
        val functions = Firebase.functions("asia-northeast3")

        val data = hashMapOf(
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "driverId" to driverId,
            "type" to "CARRYOVER_TRANSFERRED",
            "title" to "미수령금 이체 알림",
            "body" to "${"%,d".format(amount)}원이 이체되었습니다. 확인 후 수령완료를 눌러주세요."
        )

        functions.getHttpsCallable("sendDriverNotification")
            .call(data)
            .addOnSuccessListener { result ->
                Log.d("SettlementViewModel", "CarryOver notification sent to $driverName")
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to send carryOver notification", e)
            }
    }

    /**
     * 이체취소 - 이체 처리를 취소하고 PENDING 상태로 되돌림
     */
    fun cancelTransfer(driverId: String, onResult: (Boolean, String) -> Unit) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            onResult(false, "사무실 정보가 설정되지 않았습니다")
            return
        }

        val driverRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)

        viewModelScope.launch {
            driverRef.update(
                mapOf(
                    "carryOver.status" to CarryOverStatus.PENDING.name,
                    "carryOver.transferredAt" to null,
                    "carryOver.transferredBy" to null,
                    "carryOver.lastUpdatedAt" to Timestamp.now()
                )
            ).addOnSuccessListener {
                Log.d("SettlementViewModel", "CarryOver transfer cancelled for driver: $driverId")

                // 로컬 carryOverList 즉시 업데이트
                val existing = _carryOverList.value.find { it.driverId == driverId }
                if (existing != null) {
                    updateLocalCarryOver(driverId, existing.driverName, existing.balance, existing.todayAmount, CarryOverStatus.PENDING)
                }

                onResult(true, "이체 취소됨")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to cancel transfer", e)
                onResult(false, "취소 실패: ${e.message}")
            }
        }
    }

    /**
     * 마감 시 미지급금 누적 처리
     * 단, 이미 업무마감 정산확인(CONFIRMED)이 완료된 기사는 건너뜀
     * (confirmDailySettlement에서 이미 carryOver가 처리됨)
     * todayResult: 양수 = 기사가 납부 (미지급 감소), 음수 = 사무실이 지급해야 함 (미지급 증가)
     */
    fun processCarryOverOnFinalize(driverId: String, todayResult: Long) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) return

        val driverRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)

        viewModelScope.launch {
            firestore.runTransaction { transaction ->
                val doc = transaction.get(driverRef)

                @Suppress("UNCHECKED_CAST")
                val dailySettlementMap = doc.get("dailySettlement") as? Map<String, Any?>
                val dailySettlementStatus = dailySettlementMap?.get("status") as? String

                // CONFIRMED/REJECTED: carryOver 재처리 불필요
                if (dailySettlementStatus == DailySettlementStatus.CONFIRMED.name ||
                    dailySettlementStatus == DailySettlementStatus.REJECTED.name) {
                    Log.d("SettlementViewModel", "기사 $driverId: 정산 ${dailySettlementStatus}, carryOver 재처리 건너뜀")
                    return@runTransaction null
                }

                val carryOverMap = doc.get("carryOver") as? Map<String, Any?>
                val currentBalance = (carryOverMap?.get("balance") as? Long) ?: 0L
                val currentStatusStr = carryOverMap?.get("status") as? String

                val currentStatus = try {
                    currentStatusStr?.let { CarryOverStatus.valueOf(it) }
                } catch (e: Exception) { null }

                // PENDING_CONFIRM: 기사가 제출한 calculatedCarryOver 직접 사용
                // 미제출: trip 데이터만으로는 carryOver 변동이 0이므로 변경 없음
                val finalBalance = if (dailySettlementStatus == DailySettlementStatus.PENDING_CONFIRM.name) {
                    val calculatedCarryOver = (dailySettlementMap?.get("calculatedCarryOver") as? Long) ?: currentBalance
                    Log.d("SettlementViewModel", "CarryOver 계산: 기사 $driverId PENDING_CONFIRM → calculatedCarryOver=$calculatedCarryOver 사용 (기존=$currentBalance)")
                    calculatedCarryOver
                } else {
                    Log.d("SettlementViewModel", "CarryOver 계산: 기사 $driverId 미제출 → 변경 없음 (기존=$currentBalance)")
                    currentBalance
                }

                // 상태 결정: TRANSFERRED 상태는 유지 (기사가 수령완료 눌러야 변경됨)
                val newStatus = when {
                    finalBalance == 0L -> CarryOverStatus.SETTLED.name
                    currentStatus == CarryOverStatus.TRANSFERRED -> CarryOverStatus.TRANSFERRED.name  // 이체됨 상태 유지
                    else -> CarryOverStatus.PENDING.name
                }

                // set + merge 사용 (carryOver 필드가 없어도 생성됨)
                val carryOverData = mapOf(
                    "carryOver" to mapOf(
                        "balance" to finalBalance,
                        "todayAmount" to 0L,
                        "lastUpdatedAt" to Timestamp.now(),
                        "status" to newStatus
                    )
                )
                transaction.set(driverRef, carryOverData, com.google.firebase.firestore.SetOptions.merge())
            }.addOnSuccessListener {
                Log.d("SettlementViewModel", "CarryOver updated for driver $driverId: result=$todayResult")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to update carryOver", e)
            }
        }
    }

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

    /**
     * 기사 일일 정산 확인 처리
     * 1. dailySettlement.status를 CONFIRMED로 변경
     * 2. 정산 차액을 carryOver.balance에 반영
     * @param driverId 기사 ID
     * @param settlementDiff 정산 차액 (양수=환급금, 음수=미납금)
     */
    fun confirmDailySettlement(
        driverId: String,
        settlementDiff: Long,
        onResult: (Boolean, String) -> Unit
    ) {
        // 중복 클릭 방지
        if (_isConfirming.value) {
            Log.w("SettlementViewModel", "⚠️ 이미 정산 확인 진행 중입니다. 중복 요청 무시.")
            return
        }

        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            onResult(false, "사무실 정보가 설정되지 않았습니다")
            return
        }

        val loginPrefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val adminId = loginPrefs.getString("adminId", null) ?: "unknown"

        val driverRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)

        _isConfirming.value = true
        viewModelScope.launch {
            firestore.runTransaction { transaction ->
                val doc = transaction.get(driverRef)

                // 기사앱이 계산한 calculatedCarryOver를 직접 사용 (중복 계산 방지)
                @Suppress("UNCHECKED_CAST")
                val dailySettlementMap = doc.get("dailySettlement") as? Map<String, Any?>
                val newBalance = if (dailySettlementMap != null) {
                    (dailySettlementMap["calculatedCarryOver"] as? Long) ?: 0L
                } else {
                    // 미제출 기사: 기존 carryOver.balance 유지
                    @Suppress("UNCHECKED_CAST")
                    val carryOverMap = doc.get("carryOver") as? Map<String, Any?>
                    (carryOverMap?.get("balance") as? Long) ?: 0L
                }

                // carryOver 상태 결정
                val newCarryOverStatus = when {
                    newBalance > 0 -> CarryOverStatus.PENDING.name  // 환급 대기
                    newBalance < 0 -> CarryOverStatus.PENDING.name  // 미납 (실제로는 사무실이 기사에게 받아야 함)
                    else -> CarryOverStatus.SETTLED.name            // 정산 완료
                }

                val updateData = mapOf(
                    "dailySettlement.status" to DailySettlementStatus.CONFIRMED.name,
                    "dailySettlement.confirmedAt" to Timestamp.now(),
                    "dailySettlement.confirmedBy" to adminId,
                    "carryOver.balance" to newBalance,
                    "carryOver.status" to newCarryOverStatus,
                    "carryOver.lastUpdatedAt" to Timestamp.now()
                )

                transaction.update(driverRef, updateData)

                Log.d("SettlementViewModel", "Confirming daily settlement: driver=$driverId, newBalance=$newBalance")

                newBalance // 반환값
            }.addOnSuccessListener { newBalance ->
                Log.d("SettlementViewModel", "Daily settlement confirmed for driver $driverId, new balance=$newBalance")

                // 로컬 리스트 즉시 업데이트
                val currentList = _dailySettlementList.value.toMutableList()
                val index = currentList.indexOfFirst { it.driverId == driverId }
                if (index >= 0) {
                    val existing = currentList[index]
                    currentList[index] = existing.copy(
                        dailySettlement = existing.dailySettlement?.copy(
                            status = DailySettlementStatus.CONFIRMED,
                            confirmedAt = Timestamp.now(),
                            confirmedBy = adminId
                        ),
                        carryOverBalance = newBalance as Long
                    )
                    _dailySettlementList.value = currentList
                }

                _isConfirming.value = false
                onResult(true, "확인 완료")

                // 기사에게 FCM 전송 (Cloud Function 호출)
                notifyDriverSettlementResult(driverId, "CONFIRMED")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to confirm daily settlement", e)
                _isConfirming.value = false
                onResult(false, "확인 실패: ${e.message}")
            }
        }
    }

    /**
     * 정산 거절 (기사가 재제출해야 함)
     * - dailySettlement.status → REJECTED
     * - carryOver는 변경하지 않음 (원래값 유지)
     */
    fun rejectDailySettlement(
        driverId: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) {
            onResult(false, "사무실 정보가 설정되지 않았습니다")
            return
        }

        val driverRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverId)

        viewModelScope.launch {
            firestore.runTransaction { transaction ->
                val doc = transaction.get(driverRef)

                @Suppress("UNCHECKED_CAST")
                val dailySettlementMap = doc.get("dailySettlement") as? Map<String, Any?>
                val currentStatus = dailySettlementMap?.get("status") as? String

                if (currentStatus != DailySettlementStatus.PENDING_CONFIRM.name) {
                    throw Exception("마감 대기 상태가 아닙니다 (현재: $currentStatus)")
                }

                // dailySettlement.status만 REJECTED로 변경 (carryOver 변경 없음)
                transaction.update(driverRef, mapOf(
                    "dailySettlement.status" to DailySettlementStatus.REJECTED.name,
                    "dailySettlement.rejectedAt" to Timestamp.now()
                ))
            }.addOnSuccessListener {
                Log.d("SettlementViewModel", "Daily settlement rejected for driver $driverId")

                // 로컬 리스트 즉시 업데이트
                val currentList = _dailySettlementList.value.toMutableList()
                val index = currentList.indexOfFirst { it.driverId == driverId }
                if (index >= 0) {
                    val existing = currentList[index]
                    currentList[index] = existing.copy(
                        dailySettlement = existing.dailySettlement?.copy(
                            status = DailySettlementStatus.REJECTED
                        )
                    )
                    _dailySettlementList.value = currentList
                }

                onResult(true, "거절 완료")

                // 기사에게 FCM 전송
                notifyDriverSettlementResult(driverId, "REJECTED")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to reject daily settlement", e)
                onResult(false, "거절 실패: ${e.message}")
            }
        }
    }

    /**
     * 기사에게 정산 확인/거절 FCM 전송 (Cloud Function 호출)
     */
    private fun notifyDriverSettlementResult(driverId: String, result: String) {
        val provinceId = currentProvinceId ?: return
        val cityId = currentCityId ?: return
        val officeId = currentOfficeId ?: return

        viewModelScope.launch {
            try {
                val data = hashMapOf(
                    "driverId" to driverId,
                    "result" to result,
                    "provinceId" to provinceId,
                    "cityId" to cityId,
                    "officeId" to officeId
                )
                FirebaseFunctions.getInstance()
                    .getHttpsCallable("notifyDriverSettlementResult")
                    .call(data)
                    .await()
                Log.d("SettlementViewModel", "FCM sent: $result for driver $driverId")
            } catch (e: Exception) {
                // FCM 전송 실패는 치명적이지 않음 (Firestore 리스너로 감지 가능)
                Log.w("SettlementViewModel", "FCM 전송 실패 (무시 가능): ${e.message}")
            }
        }
    }
}
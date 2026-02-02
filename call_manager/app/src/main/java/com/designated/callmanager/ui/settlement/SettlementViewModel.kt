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
import android.util.Log
import com.designated.callmanager.data.settlement.SettlementSession
import com.designated.callmanager.data.settlement.CallSettlement
import com.designated.callmanager.data.settlement.SettlementMetadata
import com.designated.callmanager.data.settlement.SettlementTotals
import com.designated.callmanager.data.settlement.CarryOverStatus
import com.designated.callmanager.data.settlement.DriverCarryOver
import com.designated.callmanager.data.settlement.DriverCarryOverSummary
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
    private var carryOverListener: ListenerRegistration? = null

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
        _officeShareRatio.value = newRatio.coerceIn(30, 90)
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

                fetchCompletedCalls(provinceId, cityId, officeId, lastClearedMillis)

            }
            .addOnFailureListener { e ->
                _error.value = e.localizedMessage
                _isLoading.value = false
            }
    }

    private fun fetchCompletedCalls(provinceId: String, cityId: String, officeId: String, lastCleared: Long) {
        val effectiveLastCleared = maxOf(lastCleared, lastClearedMillisCache)

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

                        if (completedTimestamp <= effectiveLastCleared) return@mapNotNull null // 필터링

                        val fareAmount = doc.getLong("fareFinal")?.toInt()
                            ?: doc.getLong("fare_set")?.toInt()
                            ?: 0

                        val cashReceived = doc.getLong("cashReceived")?.toInt()
                        val creditAmount = doc.getLong("creditAmount")?.toInt()

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
                        repository.insertAll(newEntities)
                        _allTripsCleared.value = false
                    }
                }
            }

        firestore.collection("provinces").document(provinceId)
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
                    // 세션이 없으면 로컬 데이터로 먼저 생성
                    val newSession = createSettlementSessionFromLocalData(sessionDate)
                    sessionRef.set(newSession.toMap())
                        .addOnSuccessListener {
                            // 세션 생성 후 Cloud Function 호출
                            callFinalizeFunction(provinceId, cityId, officeId, sessionDate, onResult)
                        }
                        .addOnFailureListener { e ->
                            Log.e("SettlementViewModel", "Failed to create session", e)
                            onResult(false, "세션 생성 실패: ${e.message}")
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
                    val alreadyFinalized = response?.get("alreadyFinalized") as? Boolean ?: false

                    val message = if (alreadyFinalized) {
                        "이미 마감된 세션입니다"
                    } else if (sent > 0) {
                        "마감 완료! ${sent}명의 기사에게 알림 전송됨"
                    } else {
                        "마감 완료 (로그인 중인 기사 없음)"
                    }

                    Log.d("SettlementViewModel", "Finalize success: sent=$sent, skipped=$skipped")
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
        val totalCash = trips.filter { it.paymentMethod == "현금" }.sumOf { it.fare.toLong() }
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
                pointsUsed = 0L,
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
                totalPoints = 0L,
                callCount = trips.size
            ),
            calls = calls
        )
    }

    /**
     * 동기화 상태 초기화
     */
    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    // ====== 이월 정산 (기사별 미지급금) 관련 기능 ======

    /**
     * 기사별 이월 정산 데이터 실시간 리스너
     * drivers 컬렉션에서 carryOver 필드가 있는 기사들을 감시
     */
    private fun startCarryOverListener(provinceId: String, cityId: String, officeId: String) {
        carryOverListener?.remove()

        // drivers 컬렉션에서 해당 사무실 소속 기사들 감시
        carryOverListener = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("drivers")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "CarryOver listener error", e)
                    return@addSnapshotListener
                }

                val carryOvers = snapshots?.documents?.mapNotNull { doc ->
                    val carryOverMap = doc.get("carryOver") as? Map<String, Any?>
                    if (carryOverMap == null || (carryOverMap["balance"] as? Long ?: 0L) == 0L) {
                        return@mapNotNull null
                    }

                    val carryOver = DriverCarryOver.fromMap(carryOverMap)
                    DriverCarryOverSummary(
                        driverId = doc.id,
                        driverName = doc.getString("name") ?: "이름없음",
                        balance = carryOver.balance,
                        todayAmount = carryOver.todayAmount,
                        status = carryOver.status,
                        transferredAt = carryOver.transferredAt
                    )
                } ?: emptyList()

                _carryOverList.value = carryOvers.sortedByDescending { it.balance }
                Log.d("SettlementViewModel", "CarryOver list updated: ${carryOvers.size} drivers")
            }
    }

    /**
     * 이체하기 - 기사에게 미지급금 이체 처리
     * 상태를 TRANSFERRED로 변경하고 이체 시간 기록
     */
    fun transferCarryOver(driverId: String, onResult: (Boolean, String) -> Unit) {
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
            .collection("drivers").document(driverId)

        viewModelScope.launch {
            driverRef.update(
                mapOf(
                    "carryOver.status" to CarryOverStatus.TRANSFERRED.name,
                    "carryOver.transferredAt" to Timestamp.now(),
                    "carryOver.transferredBy" to adminId,
                    "carryOver.lastUpdatedAt" to Timestamp.now()
                )
            ).addOnSuccessListener {
                Log.d("SettlementViewModel", "CarryOver transferred for driver: $driverId")
                onResult(true, "이체 완료")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to transfer carryOver", e)
                onResult(false, "이체 실패: ${e.message}")
            }
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
            .collection("drivers").document(driverId)

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
                onResult(true, "이체 취소됨")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to cancel transfer", e)
                onResult(false, "취소 실패: ${e.message}")
            }
        }
    }

    /**
     * 마감 시 미지급금 누적 처리
     * 오늘 미지급금을 누적 잔액에 추가
     */
    fun processCarryOverOnFinalize(driverId: String, todayUnpaid: Long) {
        if (todayUnpaid <= 0) return

        val provinceId = currentProvinceId
        val cityId = currentCityId
        val officeId = currentOfficeId

        if (provinceId == null || cityId == null || officeId == null) return

        val driverRef = firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("drivers").document(driverId)

        viewModelScope.launch {
            firestore.runTransaction { transaction ->
                val doc = transaction.get(driverRef)
                val carryOverMap = doc.get("carryOver") as? Map<String, Any?>
                val currentBalance = (carryOverMap?.get("balance") as? Long) ?: 0L

                transaction.update(driverRef, mapOf(
                    "carryOver.balance" to currentBalance + todayUnpaid,
                    "carryOver.todayAmount" to 0L,
                    "carryOver.lastUpdatedAt" to Timestamp.now(),
                    "carryOver.status" to CarryOverStatus.PENDING.name
                ))
            }.addOnSuccessListener {
                Log.d("SettlementViewModel", "CarryOver accumulated for driver $driverId: +$todayUnpaid")
            }.addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Failed to accumulate carryOver", e)
            }
        }
    }
}
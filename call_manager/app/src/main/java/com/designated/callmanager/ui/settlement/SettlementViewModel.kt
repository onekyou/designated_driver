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
}
package com.designated.callmanager.ui.settlement

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.data.SessionInfo
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

class SettlementViewModel(application: Application) : AndroidViewModel(application) {

    private val _settlementList = MutableStateFlow<List<SettlementData>>(emptyList())
    val settlementList: StateFlow<List<SettlementData>> = _settlementList

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val firestore = FirebaseFirestore.getInstance()

    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    private var lastClearedMillisCache: Long = 0L
    private val prefs = getApplication<Application>().getSharedPreferences("settlement_prefs", Context.MODE_PRIVATE)

    // 일일정산 탭에서 초기화된 날짜 목록 (로컬에만 반영)
    private val _clearedDates = MutableStateFlow<Set<String>>(emptySet())
    val clearedDates: StateFlow<Set<String>> = _clearedDates

    // 전체내역 탭을 초기화한 여부 (로컬 세션)
    private val _allTripsCleared = MutableStateFlow(false)
    val allTripsCleared: StateFlow<Boolean> = _allTripsCleared

    // 사무실 수익 비율 (퍼센트) - 기본 60
    private val _officeShareRatio = MutableStateFlow(40)
    val officeShareRatio: StateFlow<Int> = _officeShareRatio

    // 업무 마감 세션 카드 리스트
    private val _sessionList = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessionList: StateFlow<List<SessionInfo>> = _sessionList

    // 세션 리스너
    private var sessionsListener: ListenerRegistration? = null
    private var callsListener: ListenerRegistration? = null

    private val database = CallManagerDatabase.getInstance(getApplication())
    private val repository = SettlementRepository(database)
    private val creditDao = database.creditDao()

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
                Log.d("SettlementViewModel", "📋 Sessions list size=${_sessionList.value.size}")
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
                        entries = emptyList() // 초기에는 빈 리스트
                    )
                }
                _creditPersons.value = creditPersonsWithEntries
                
                // 각 person의 entries를 별도로 로드
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
                        // 해당 person의 entries 업데이트
                        _creditPersons.value = _creditPersons.value.map { person ->
                            if (person.id == entity.id) {
                                person.copy(entries = entries)
                            } else person
                        }
                    }
                }
            }
        }

        // 로그인 정보에서 regionId/officeId 를 읽어 초기 로드
        val loginPrefs = getApplication<Application>().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val region = loginPrefs.getString("regionId", null)
        val office = loginPrefs.getString("officeId", null)
        if (!region.isNullOrBlank() && !office.isNullOrBlank()) {
            loadSettlementData(region, office)
        }
    }

    fun updateOfficeShareRatio(newRatio: Int) {
        _officeShareRatio.value = newRatio.coerceIn(30, 90)
    }

    // 외상인 관리 데이터
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
                    customerName = name, // 외상 관리에서는 name이 고객명
                    driverName = "미지정", // 기사명이 없는 경우
                    date = detail.date,
                    departure = detail.departure,
                    destination = detail.destination
                )
                Log.d("SettlementViewModel", "💾 외상 데이터베이스 저장: $name - ${addAmount}원 (${detail.departure}→${detail.destination})")
            } else {
                creditDao.addOrIncrementCredit(name, phone, addAmount)
                Log.d("SettlementViewModel", "💾 외상 데이터베이스 저장: $name - ${addAmount}원 (상세정보 없음)")
            }
        }
    }

    fun reduceCredit(id: String, reduceAmount: Int) {
        viewModelScope.launch {
            creditDao.decrementCreditAmount(id, reduceAmount)
            // 금액이 0이 된 경우 삭제
            val person = creditDao.getAllCreditPersons().first().find { it.id == id }
            if (person?.totalAmount == 0) {
                creditDao.deleteCreditPersonById(id)
            }
        }
    }

    // 대리운전 업무일 계산 함수 (오전 6시 기준으로 날짜 구분)
    private fun calculateWorkDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        // 오전 6시 이전이면 전날 업무일로 계산
        if (calendar.get(Calendar.HOUR_OF_DAY) < 6) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    fun loadSettlementData(regionId: String, officeId: String) {
        currentRegionId = regionId
        currentOfficeId = officeId
        // Load local pref
        val localKey = "${regionId}_${officeId}_lastCleared"
        lastClearedMillisCache = prefs.getLong(localKey, 0L)

        _isLoading.value = true
        _error.value = null
        
        Log.d("SettlementViewModel", "🔍 Loading settlement data for region: $regionId, office: $officeId")
        
        // 1) Get lastCleared timestamp
        firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .get()
            .addOnSuccessListener { officeDoc ->
                val lastClearedMillis = officeDoc.getTimestamp("settlementLastCleared")?.toDate()?.time ?: 0L

                // 2) Fetch completed calls
                fetchCompletedCalls(regionId, officeId, lastClearedMillis)

                // 실시간 세션 리스너는 로컬 DB로 대체 (Firebase 호출 제거)
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "💥 Error loading office info", e)
                _error.value = e.localizedMessage
                _isLoading.value = false
            }
    }

    private fun fetchCompletedCalls(regionId: String, officeId: String, lastCleared: Long) {
        val effectiveLastCleared = maxOf(lastCleared, lastClearedMillisCache)

        firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "COMPLETED")
            .get()
            .addOnSuccessListener { result ->
                Log.d("SettlementViewModel", "📊 Found ${result.documents.size} completed calls (raw)")
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
                            cashAmount = cashReceived ?: 0,
                            creditAmount = creditAmount ?: 0,
                            completedAt = completedTimestamp,
                            driverId = doc.getString("assignedDriverId") ?: "",
                            regionId = regionId,
                            officeId = officeId,
                            workDate = calculateWorkDate(completedTimestamp)
                        )
                    } catch (e: Exception) {
                        Log.e("SettlementViewModel", "❌ Error mapping document ${doc.id}", e)
                        null
                    }
                }

                // ➡ Room 캐시에 저장하여 오프라인/재사용 지원 (중복 방지)
                viewModelScope.launch {
                    val newTrips = trips.filter { trip ->
                        repository.dao.existsById(trip.callId) == 0
                    }
                    if (newTrips.isNotEmpty()) {
                        repository.insertAll(newTrips.map { SettlementEntity.fromData(it) })
                        Log.d("SettlementViewModel", "💾 저장된 새로운 정산 데이터: ${newTrips.size}건")
                        
                        // 외상이 있는 콜들을 외상 관리에 추가
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
                                    phone = "", // 전화번호 정보가 없는 경우
                                    addAmount = trip.creditAmount,
                                    detail = creditDetail
                                )
                                Log.d("SettlementViewModel", "💳 외상 추가: ${trip.customerName} - ${trip.creditAmount}원 (${trip.departure}→${trip.destination})")
                            }
                        }
                    } else {
                        Log.d("SettlementViewModel", "✅ 모든 데이터가 이미 캐시에 존재함")
                    }
                }
                // 만약 사용자가 "전체내역 초기화" 후 새 콜이 도착하면 자동으로 리스트를 다시 보여주기 위해 플래그 해제
                if (trips.isNotEmpty()) {
                    _allTripsCleared.value = false
                }
                _isLoading.value = false
                Log.d("SettlementViewModel", "🎉 Loaded ${trips.size} settlement records after filter")
            }
            .addOnSuccessListener {
                // After initial load, start realtime listener for new completed calls
                startCallsListener(regionId, officeId, effectiveLastCleared)
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "💥 Error loading settlement data", e)
                _error.value = e.localizedMessage
                _isLoading.value = false
            }
    }

    /** 신규 COMPLETED 콜에 대한 실시간 리스너 */
    private fun startCallsListener(regionId: String, officeId: String, sinceMillis: Long) {
        callsListener?.remove()
        // 1차 시도: updatedAt 필드 기반 리스너 (일반적으로 항상 존재)
        val baseQuery = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "COMPLETED")

        // Firestore는 range 필터 필드에 orderBy가 필요하다.
        // 일부 문서는 updatedAt 이 null 인 경우가 있어 completedAt 로 다시 시도할 수 있도록 두 단계 리스너를 설정한다.

        callsListener = baseQuery
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .whereGreaterThan("updatedAt", Date(sinceMillis))
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "💥 Calls listener error", e)
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
                            regionId = regionId,
                            officeId = officeId,
                            workDate = calculateWorkDate(completedTimestamp)
                        )
                    } catch (ex: Exception) {
                        Log.e("SettlementViewModel", "❌ Error mapping new completed call", ex)
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

        // 2차 시도: updatedAt 이 없는 문서 대비용 completedAt 기반 리스너 (필요 시만 실행)
        // Firestore snapshotListener 중첩은 비용 크지 않음. 두 리스너 모두 중복 insert 시 onConflict=REPLACE 로 해결.
        firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "COMPLETED")
            .orderBy("completedAt", Query.Direction.ASCENDING)
            .whereGreaterThan("completedAt", Date(sinceMillis))
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "💥 Calls listener(2) error", e)
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
                            regionId = regionId,
                            officeId = officeId,
                            workDate = calculateWorkDate(completedTimestamp)
                        )
                    } catch (ex: Exception) {
                        Log.e("SettlementViewModel", "❌ Error mapping new completed call(2)", ex)
                        null
                    }
                } ?: emptyList()

                if (newEntities.isNotEmpty()) {
                    viewModelScope.launch {
                        // 중복 방지: 이미 존재하지 않는 것만 삽입
                        val reallyNewEntities = newEntities.filter { entity ->
                            repository.dao.existsById(entity.callId) == 0
                        }
                        if (reallyNewEntities.isNotEmpty()) {
                            repository.insertAll(reallyNewEntities)
                            _allTripsCleared.value = false
                            Log.d("SettlementViewModel", "🔄 실시간으로 추가된 정산 데이터: ${reallyNewEntities.size}건")
                            
                            // 실시간으로 들어온 외상 데이터 처리
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
                                    Log.d("SettlementViewModel", "🔄💳 실시간 외상 추가: ${entity.customerName} - ${entity.creditAmount}원")
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
            Log.d("SettlementViewModel", "🗑️ 모든 정산 데이터 삭제됨")
        }
    }

    // 특정 업무일(workDate)에 해당하는 내역만 초기화 (로컬 목록에서 제거)
    fun clearSettlementForDate(workDate: String) {
        _clearedDates.value = _clearedDates.value + workDate
        viewModelScope.launch {
            repository.deleteWorkDate(workDate)
        }
    }

    fun clearAllTrips() {
        val trips = _settlementList.value          // 현재 ‘전체내역’에 보이는 트립
        if (trips.isEmpty()) return                // 0건이면 그대로 종료

        val totalTrips = trips.size
        val totalFare  = trips.sumOf { it.fare }.toLong()
        val newSessionId = System.currentTimeMillis().toString()

        viewModelScope.launch {
            // 2-① 세션 카드 Room 저장
            repository.insertSession(
                SessionEntity(
                    sessionId   = newSessionId,
                    endAt       = System.currentTimeMillis(),
                    totalTrips  = totalTrips,
                    totalFare   = totalFare
                )
            )
            // 2-② 해당 콜들을 isFinalized=1 로 마킹(→ flowActive 에서 제외)
            repository.markTripsFinalized(trips.map { it.callId }, newSessionId)
        }

        // UI 플래그 갱신
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
        val r = currentRegionId ?: return
        val o = currentOfficeId ?: return
        val key = "${r}_${o}_lastCleared"
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
        val region = currentRegionId
        val office = currentOfficeId
        if(region==null || office==null) { cb(null); return }
        firestore.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("calls").document(callId)
            .get()
            .addOnSuccessListener { snap -> cb(snap.getString("phoneNumber")) }
            .addOnFailureListener { cb(null) }
    }
}
package com.designated.callmanager.ui.settlement

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.SettlementData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

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
    private val _officeShareRatio = MutableStateFlow(60)
    val officeShareRatio: StateFlow<Int> = _officeShareRatio

    fun updateOfficeShareRatio(newRatio: Int) {
        _officeShareRatio.value = newRatio.coerceIn(30, 90)
    }

    // 외상인 관리 데이터
    data class CreditPerson(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val phone: String,
        val memo: String = "",
        val amount: Int = 0
    )

    private val _creditPersons = MutableStateFlow<List<CreditPerson>>(emptyList())
    val creditPersons: StateFlow<List<CreditPerson>> = _creditPersons

    fun addOrIncrementCredit(name: String, phone: String, addAmount: Int) {
        if (phone.isBlank() || addAmount <= 0) return
        val existing = _creditPersons.value.find { it.phone == phone }
        _creditPersons.value = if (existing != null) {
            _creditPersons.value.map {
                if (it.phone == phone) it.copy(amount = it.amount + addAmount) else it
            }
        } else {
            _creditPersons.value + CreditPerson(name = name, phone = phone, amount = addAmount)
        }
    }

    fun reduceCredit(id: String, reduceAmount: Int) {
        _creditPersons.value = _creditPersons.value.map {
            if (it.id == id) it.copy(amount = (it.amount - reduceAmount).coerceAtLeast(0)) else it
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

                _settlementList.value = trips.sortedByDescending { it.completedAt }
                _isLoading.value = false
                Log.d("SettlementViewModel", "🎉 Loaded ${trips.size} settlement records after filter")
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "💥 Error loading settlement data", e)
                _error.value = e.localizedMessage
                _isLoading.value = false
            }
    }

    fun clearLocalSettlement() {
        // AllTrips 탭만 비우도록 플래그 설정 (서버/공유 데이터는 유지)
        _allTripsCleared.value = true
    }

    // 특정 업무일(workDate)에 해당하는 내역만 초기화 (로컬 목록에서 제거)
    fun clearSettlementForDate(workDate: String) {
        _clearedDates.value = _clearedDates.value + workDate
    }
}
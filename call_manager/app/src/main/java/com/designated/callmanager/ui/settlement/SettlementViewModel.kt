package com.designated.callmanager.ui.settlement

import androidx.lifecycle.ViewModel
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// 운행내역 데이터 모델(필요 필드만 우선 정의)
// 정산 데이터 모델 (기존 TripSummary 활용, 필요시 필드 조정)
data class SettlementData(
    val callId: String = "",
    val driverId: String = "",
    val driverName: String = "",
    val customerName: String = "",
    val phoneNumber: String = "",
    val departure: String = "",
    val destination: String = "",
    val fare: Int = 0,
    val paymentMethod: String = "",
    val cashAmount: Int? = null,
    val completedAt: Long = 0L
)

class SettlementViewModel : ViewModel() {
    // calls 컬렉션 기반 정산 내역
    private val _settlementList = MutableStateFlow<List<SettlementData>>(emptyList())
    val settlementList: StateFlow<List<SettlementData>> = _settlementList

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // calls 컬렉션에서 '완료' 상태의 데이터 불러오기
    fun loadSettlementData() {
    

    
        _isLoading.value = true
        _error.value = null
        firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("calls")
            .whereEqualTo("status", "완료")
            .get()
            .addOnSuccessListener { result ->
                val trips = result.documents.mapNotNull { doc ->
                    try {
                        val paymentMethodValue = doc.getString("paymentMethod") ?: ""
                        SettlementData(
                            callId = doc.id,
                            driverId = doc.getString("assignedDriverId") ?: "",
                            driverName = doc.getString("assignedDriverName") ?: "",
                            customerName = doc.getString("customerName") ?: "",
                            phoneNumber = doc.getString("phoneNumber") ?: "",
                            departure = doc.getString("departure_set") ?: "",
                            destination = doc.getString("destination_set") ?: "",
                            fare = (doc.getLong("fare") ?: 0L).toInt(), // fare_set과 동일하다면 fare 사용
                            paymentMethod = paymentMethodValue,
                            cashAmount = doc.getLong("cashAmount")?.toInt(),
                            completedAt = doc.getTimestamp("summaryConfirmedTimestamp")?.toDate()?.time ?: 0L
                    ).also { data ->
                        Log.d("SettlementViewModel", "Loaded and mapped callId: ${data.callId}, paymentMethod: ${data.paymentMethod}")
                    }
                    } catch (e: Exception) {
                        Log.e("SettlementViewModel", "Error mapping document ${doc.id}", e)
                    null
                }    }
                _settlementList.value = trips.sortedByDescending { it.completedAt }
                _isLoading.value = false
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "Error loading settlement data", e)
            _error.value = e.localizedMessage
                _isLoading.value = false
            }
    }
    // regionId, officeId는 실제 앱에서는 MainActivity 등에서 주입받도록 개선 필요
    private val regionId = "yangpyong" // 실제 값으로 수정
    private val officeId = "office_2" // 실제 값으로 수정

    private val firestore = FirebaseFirestore.getInstance()

}
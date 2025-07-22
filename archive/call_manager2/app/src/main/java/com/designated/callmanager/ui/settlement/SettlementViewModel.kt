package com.designated.callmanager.ui.settlement

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.SettlementData
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.designated.callmanager.data.SettlementInfo
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import java.text.SimpleDateFormat
import java.util.*

class SettlementViewModel(application: Application) : AndroidViewModel(application) {

    private val _settlementList = MutableStateFlow<List<SettlementData>>(emptyList())
    val settlementList: StateFlow<List<SettlementData>> = _settlementList

    // 전체 내역용 별도 상태 추가
    private val _allSettlementList = MutableStateFlow<List<SettlementData>>(emptyList())
    val allSettlementList: StateFlow<List<SettlementData>> = _allSettlementList

    private val _allTripsCleared = MutableStateFlow(true)
    val allTripsCleared: StateFlow<Boolean> = _allTripsCleared

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val firestore = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("asia-northeast3")

    private var settlementsListener: ListenerRegistration? = null
    private var allSettlementsListener: ListenerRegistration? = null
    private var creditsListener: ListenerRegistration? = null

    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null

    // 외상인 관리 데이터
    data class CreditPerson(
        val id: String, // Firestore document ID (customerKey)
        val name: String,
        val phone: String,
        val memo: String = "",
        val amount: Int = 0
    )
    private val _creditPersons = MutableStateFlow<List<CreditPerson>>(emptyList())
    val creditPersons: StateFlow<List<CreditPerson>> = _creditPersons


    fun init(regionId: String, officeId: String) {
        if (currentRegionId == regionId && currentOfficeId == officeId) return

        currentRegionId = regionId
        currentOfficeId = officeId

        fetchAllSettlements(regionId, officeId)
        fetchCredits(regionId, officeId)
    }

    // 대리운전 업무일 계산 함수 (오전 6시 기준으로 날짜 구분)
    private fun calculateWorkDate(timestamp: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        // 새벽 6시 이전이면 전날로 계산
        if (calendar.get(Calendar.HOUR_OF_DAY) < 6) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }

    // 기존 settlement 문서에 customerPhone이 없는 경우 원본 call 문서에서 업데이트
    private fun updateMissingCustomerPhones(regionId: String, officeId: String) {
        // 모든 settlement 문서를 가져와서 customerPhone이 null이거나 빈 문자열인 경우 업데이트
        firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("settlements")
            .get()
            .addOnSuccessListener { settlements ->
                settlements.documents.forEach { settlementDoc ->
                    val customerPhone = settlementDoc.getString("customerPhone")
                    val callId = settlementDoc.getString("callId")
                    
                    // customerPhone이 null이거나 빈 문자열인 경우에만 업데이트
                    if ((customerPhone == null || customerPhone.isBlank()) && callId != null) {
                        Log.d("SettlementViewModel", "🔍 Settlement ${settlementDoc.id}에 customerPhone이 없음. callId: $callId 에서 조회 중...")
                        
                        // 원본 call 문서에서 phoneNumber 가져오기
                        firestore.collection("regions").document(regionId)
                            .collection("offices").document(officeId)
                            .collection("calls").document(callId)
                            .get()
                            .addOnSuccessListener { callDoc ->
                                val phoneNumber = callDoc.getString("phoneNumber")
                                if (phoneNumber != null && phoneNumber.isNotBlank()) {
                                    // settlement 문서에 customerPhone 업데이트
                                    settlementDoc.reference.update("customerPhone", phoneNumber)
                                        .addOnSuccessListener {
                                            Log.d("SettlementViewModel", "✅ Updated customerPhone for settlement ${settlementDoc.id}: $phoneNumber")
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("SettlementViewModel", "❌ Failed to update customerPhone for settlement ${settlementDoc.id}", e)
                                        }
                                } else {
                                    Log.w("SettlementViewModel", "⚠️ No phoneNumber found in call document $callId")
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("SettlementViewModel", "❌ Failed to get call document $callId", e)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "❌ Failed to query settlements for customerPhone update", e)
            }
    }

    // loadSettlementData 함수 수정 - 데이터 로드 후 누락된 전화번호 업데이트
    fun loadSettlementData(regionId: String, officeId: String) {
        currentRegionId = regionId
        currentOfficeId = officeId

        _isLoading.value = true
        _error.value = null
        
        // 기존 listener 제거
        settlementsListener?.remove()
        allSettlementsListener?.remove()
        creditsListener?.remove()
        
        // 새로운 listener 시작
        startSettlementsListener(regionId, officeId)
        startAllSettlementsListener(regionId, officeId)
        startCreditsListener(regionId, officeId)
        
        // 누락된 customerPhone 클라이언트 업데이트는 권한 오류가 발생하므로 중단 (백엔드 마이그레이션 필요)
    }

    private fun startSettlementsListener(regionId: String, officeId: String) {
        settlementsListener?.remove()

        settlementsListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("settlements")
            .whereEqualTo("settlementStatus", Constants.SETTLEMENT_STATUS_PENDING)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "💥 settlements listener error", e)
                    _error.value = e.localizedMessage
                    _isLoading.value = false
                    return@addSnapshotListener
                }

                val settlements = mutableListOf<SettlementData>()
                snapshots?.documents?.forEach { doc ->
                    try {
                        val info = doc.toObject(SettlementInfo::class.java)
                        info?.let {
                            // 정산대기 탭은 초기화를 적용하지 않음
                            
                            // customerPhone이 비어있는 경우만 로깅
                            if (it.customerPhone.isNullOrBlank()) {
                                Log.d("SettlementViewModel", "⚠️ Settlement ${doc.id}의 customerPhone이 비어있음. callId=${it.callId}")
                            }
                            
                            // map to existing SettlementData for UI 재사용
                            settlements.add(SettlementData(
                                callId = it.callId ?: "",
                                settlementId = doc.id,
                                driverName = it.driverName ?: "N/A",
                                customerName = it.customerName ?: "",
                                customerPhone = it.customerPhone ?: "",
                                departure = it.departure ?: "",
                                destination = it.destination ?: "",
                                waypoints = it.waypoints ?: "",
                                fare = it.fareFinal?.toInt() ?: it.fare?.toInt() ?: 0,
                                paymentMethod = it.paymentMethod ?: "",
                                cardAmount = null,
                                cashAmount = it.cashAmount?.toInt(),
                                creditAmount = it.creditAmount?.toInt() ?: 0,
                                completedAt = it.completedAt?.toDate()?.time ?: it.createdAt?.toDate()?.time ?: 0L,
                                driverId = it.driverId ?: "",
                                regionId = regionId,
                                officeId = officeId,
                                settlementStatus = it.settlementStatus ?: "",
                                workDate = calculateWorkDate(it.completedAt?.toDate()?.time ?: System.currentTimeMillis())
                            ))
                        }
                    } catch (ex: Exception) {
                        Log.e("SettlementViewModel", "Error parsing settlement ${doc.id}", ex)
                    }
                }

                _settlementList.value = settlements
                _isLoading.value = false
            }
    }

    // ADD: credits 실시간 리스너
    private fun startCreditsListener(regionId: String, officeId: String) {
        creditsListener?.remove()

        creditsListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("credits")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "credits listener error", e)
                    return@addSnapshotListener
                }
                if (snapshots == null) return@addSnapshotListener

                val list = snapshots.documents.map { doc ->
                    CreditPerson(
                        id = doc.id,
                        name = doc.getString("name") ?: doc.id,
                        phone = doc.getString("phone") ?: "",
                        memo = doc.getString("memo") ?: "",
                        amount = (doc.getLong("totalOwed") ?: 0L).toInt()
                    )
                }.sortedByDescending { it.amount }

                _creditPersons.value = list
            }
    }

    private fun fetchCredits(regionId: String, officeId: String) {
        creditsListener?.remove()
        creditsListener = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("credits")
            .orderBy("totalOwed", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "💥 credits listener error", e)
                    return@addSnapshotListener
                }

                val list = snapshots?.documents?.mapNotNull { doc ->
                    try {
                        CreditPerson(
                            id = doc.id,
                            name = doc.getString("name") ?: "이름없음",
                            phone = doc.getString("phone") ?: "",
                            memo = doc.getString("memo") ?: "",
                            amount = doc.getLong("totalOwed")?.toInt() ?: 0
                        )
                    } catch (ex: Exception) {
                        Log.e("SettlementViewModel", "Error parsing credit person ${doc.id}", ex)
                        null
                    }
                } ?: emptyList()

                _creditPersons.value = list
            }
    }

    // 전체 내역 리스너 추가
    private fun startAllSettlementsListener(regionId: String, officeId: String) {
        allSettlementsListener?.remove()

        val query = firestore.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("settlements")
            .whereEqualTo("isFinalized", false) // 마감되지 않은 내역만 가져오기

        allSettlementsListener = query
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("SettlementViewModel", "Listen failed.", e)
                    _error.value = "전체 정산 내역을 불러오는데 실패했습니다."
                    return@addSnapshotListener
                }

                val settlements = snapshots?.mapNotNull {
                    it.toObject(SettlementInfo::class.java).toSettlementData(it.id)
                } ?: emptyList()

                // 날짜순으로 정렬 (최신순)
                val sorted = settlements.sortedByDescending { it.completedAt }
                _allSettlementList.value = sorted
                _allTripsCleared.value = sorted.isEmpty()
            }
    }

    override fun onCleared() {
        super.onCleared()
        settlementsListener?.remove()
        allSettlementsListener?.remove()
        creditsListener?.remove()
    }

    fun clearAllTrips() {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return

        _isLoading.value = true
        functions
            .getHttpsCallable("finalizeDailySettlement")
            .call(mapOf("regionId" to region, "officeId" to office))
            .addOnCompleteListener { task ->
                _isLoading.value = false
                if (task.isSuccessful) {
                    Log.d("SettlementViewModel", "일일 정산 마감 함수 호출 성공")
                    // 리스너가 자동으로 목록을 갱신하므로 별도 처리 필요 없음
                } else {
                    Log.e("SettlementViewModel", "일일 정산 마감 함수 호출 실패", task.exception)
                    _error.value = "업무 마감 처리 중 오류가 발생했습니다."
                }
            }
    }

    // 특정 업무일(workDate)에 해당하는 내역만 초기화 (로컬 목록에서 제거)
    fun clearSettlementForDate(workDate: String) {
        // This function is no longer needed as all settlement data is managed by the listener.
        // The listener will automatically update the list when finalized.
    }

    // ---------------- 정산 완료 처리 ----------------
    fun markSettlementSettled(settlementId: String) {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return
        firestore.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("settlements").document(settlementId)
            .update("settlementStatus", Constants.SETTLEMENT_STATUS_SETTLED)
            .addOnSuccessListener { Log.d("SettlementViewModel", "✅ settlement $settlementId marked SETTLED") }
            .addOnFailureListener { e -> Log.e("SettlementViewModel", "❌ failed to mark settled", e) }
    }

    // ---------------- 정산 정정(CORRECTED) ----------------
    fun correctSettlement(original: SettlementData, newFare: Int, newPaymentMethod: String) {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return

        val settlementsCol = firestore.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("settlements")

        firestore.runTransaction { tx ->
            // 1) 원본 문서를 CORRECTED 로 표기
            val origRef = settlementsCol.document(original.settlementId)
            tx.update(origRef, mapOf(
                "settlementStatus" to "CORRECTED",
                "correctedAt" to com.google.firebase.Timestamp.now()
            ))

            // 2) 수정본 새 문서 생성 (PENDING -> 바로 SETTLED)
            val newRef = settlementsCol.document()
            val newDoc = hashMapOf(
                "callId" to original.callId,
                "driverId" to original.driverId,
                "driverName" to original.driverName,
                "customerName" to original.customerName,
                "departure" to original.departure,
                "destination" to original.destination,
                "waypoints" to original.waypoints,
                "fare" to newFare,
                "paymentMethod" to newPaymentMethod,
                "officeId" to office,
                "regionId" to region,
                "settlementStatus" to "SETTLED",
                "createdAt" to com.google.firebase.Timestamp.now(),
                "callType" to "SHARED" // 원본 그대로, 없으면 NORMAL by default
            )
            tx.set(newRef, newDoc)
        }.addOnSuccessListener {
            Log.d("SettlementViewModel", "✅ correction completed for ${original.settlementId}")
        }.addOnFailureListener { e ->
            Log.e("SettlementViewModel", "❌ correction failed", e)
        }
    }

    // helper to sanitize key (동일 로직: Cloud Function)
    private fun sanitizeKey(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(80)
    }

    fun addOrIncrementCredit(name: String, phone: String, addAmount: Int, memo: String = "") {
        if (addAmount <= 0) return
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return

        if (phone.isBlank()) {
            _error.value = "전화번호를 입력해 주세요"
            return
        }

        // --- 안전한 고객 Key 생성 ---
        fun buildCustomerKey(rawName: String, rawPhone: String): String {
            var key = sanitizeKey(rawName.ifBlank { rawPhone })
            // key 가 모두 '_' 이면 Firestore 가 INVALID_ARGUMENT 로 거부 → fallback 처리
            if (key.replace("_", "").isEmpty()) {
                key = sanitizeKey(rawPhone)
                if (key.replace("_", "").isEmpty()) {
                    key = "cust_${System.currentTimeMillis()}"
                }
            }
            return key
        }
        val customerKey = buildCustomerKey(name, phone)

        val creditsRef = firestore.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("credits").document(customerKey)

        firestore.runTransaction { tx ->
            tx.set(
                creditsRef,
                mapOf(
                    "name" to name.ifBlank { phone },
                    "phone" to phone,
                    "memo" to memo,
                    "totalOwed" to com.google.firebase.firestore.FieldValue.increment(addAmount.toLong()),
                    "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.addOnSuccessListener {
            Log.d("SettlementViewModel", "✅ credit +$addAmount for $customerKey")
        }.addOnFailureListener { e ->
            Log.e("SettlementViewModel", "❌ failed to add credit", e)
            _error.value = "외상 등록 실패: ${e.message ?: "알 수 없음"}"
        }
    }

    fun reduceCredit(customerKey: String, reduceAmount: Int) {
        if (reduceAmount <= 0) return
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return

        val creditsRef = firestore.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("credits").document(customerKey)

        firestore.runTransaction { tx ->
            val snap = tx.get(creditsRef)
            val prev = snap.getLong("totalOwed") ?: 0L
            val newTotal = prev - reduceAmount
            if (newTotal <= 0) {
                tx.delete(creditsRef)
            } else {
                tx.update(creditsRef, mapOf(
                    "totalOwed" to newTotal,
                    "lastUpdated" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                ))
            }
        }.addOnSuccessListener {
            Log.d("SettlementViewModel", "✅ credit -$reduceAmount for $customerKey")
        }.addOnFailureListener { e ->
            Log.e("SettlementViewModel", "❌ failed to reduce credit", e)
        }
    }

    // UI 에서 호출: 에러 초기화
    fun clearError() {
        _error.value = null
    }

    // UI 에서 호출: 오류 설정
    fun setError(msg: String) {
        _error.value = msg
    }

    // 콜 문서에서 전화번호 조회 후 콜백
    fun fetchPhoneForCall(callId: String, onResult: (String?) -> Unit) {
        val region = currentRegionId ?: return onResult(null)
        val office = currentOfficeId ?: return onResult(null)
        
        Log.d("SettlementViewModel", "🔍 fetchPhoneForCall 시작: callId=$callId, region=$region, office=$office")
        
        firestore.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("calls").document(callId)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val phoneNumber = snap.getString("phoneNumber")
                    Log.d("SettlementViewModel", "✅ fetchPhoneForCall 성공: callId=$callId, phoneNumber=$phoneNumber")
                    onResult(phoneNumber)
                } else {
                    Log.w("SettlementViewModel", "⚠️ fetchPhoneForCall: call 문서가 존재하지 않음: callId=$callId")
                    onResult(null)
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "❌ fetchPhoneForCall 실패: callId=$callId", e)
                onResult(null)
            }
    }

    private fun loadOfficeShareRatio(regionId: String, officeId: String) {
        // 기본값 60으로 설정 (필요시 Firebase에서 로드)
        // This function is no longer needed as the state flow is removed.
    }

    fun initialize(regionId: String, officeId: String) {
        currentRegionId = regionId
        currentOfficeId = officeId
        
        _isLoading.value = true
        startSettlementsListener(regionId, officeId)
        startAllSettlementsListener(regionId, officeId)
        startCreditsListener(regionId, officeId)
        // loadOfficeShareRatio(regionId, officeId) // This line was removed as per the new_code, as the state flow is removed.
    }

    private fun SettlementInfo.toSettlementData(docId: String): SettlementData? {
        return SettlementData(
            callId = this.callId ?: return null,
            settlementId = docId,
            driverName = this.driverName ?: "",
            customerName = this.customerName ?: "",
            customerPhone = this.customerPhone ?: "",
            departure = this.departure ?: "",
            destination = this.destination ?: "",
            waypoints = this.waypoints ?: "",
            fare = (this.fareFinal ?: this.fare ?: 0).toInt(),
            paymentMethod = this.paymentMethod ?: "",
            cardAmount = null,
            cashAmount = this.cashAmount?.toInt(),
            creditAmount = this.creditAmount?.toInt() ?: 0,
            completedAt = this.completedAt?.toDate()?.time ?: 0,
            driverId = this.driverId ?: "",
            regionId = this.regionId ?: "",
            officeId = this.officeId ?: "",
            settlementStatus = this.settlementStatus ?: Constants.SETTLEMENT_STATUS_PENDING,
            workDate = calculateWorkDate(this.completedAt?.toDate()?.time ?: 0),
            isFinalized = this.isFinalized ?: false
        )
    }
}
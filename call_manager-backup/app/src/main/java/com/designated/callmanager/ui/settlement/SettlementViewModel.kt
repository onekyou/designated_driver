package com.designated.callmanager.ui.settlement

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.designated.callmanager.data.Constants
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.data.SettlementInfo
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.*

private val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

data class CreditPerson(
    val id: String,
    val name: String,
    val phone: String,
    val memo: String,
    val amount: Int
)

class SettlementViewModel(application: Application) : AndroidViewModel(application) {

    /* ①  UI-State */
    private val _settlementList      = MutableStateFlow<List<SettlementData>>(emptyList())    // 정산 대기
    private val _allSettlementList   = MutableStateFlow<List<SettlementData>>(emptyList())    // 전체 내역
    private val _allTripsCleared     = MutableStateFlow(true)                                 // 전체내역 탭 비어있는지
    private val _isLoading           = MutableStateFlow(false)
    private val _error               = MutableStateFlow<String?>(null)
    private val _creditPersons       = MutableStateFlow<List<CreditPerson>>(emptyList())
    private val _officeShareRatio    = MutableStateFlow(60)

    val settlementList  : StateFlow<List<SettlementData>> = _settlementList
    val allSettlementList: StateFlow<List<SettlementData>> = _allSettlementList
    val allTripsCleared : StateFlow<Boolean>                = _allTripsCleared
    val isLoading       : StateFlow<Boolean>                = _isLoading
    val error           : StateFlow<String?>                = _error
    val creditPersons   : StateFlow<List<CreditPerson>>     = _creditPersons
    val officeShareRatio: StateFlow<Int>                    = _officeShareRatio

    /* ②  Firebase */
    private val db          = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("asia-northeast3")
    private var settlementsListener    : ListenerRegistration? = null
    private var allSettlementsListener: ListenerRegistration? = null
    private var creditsListener        : ListenerRegistration? = null

    /* ③  현재 지역/사무실 */
    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    // todayWorkDate 는 매 호출 시점에 동적으로 계산하도록 변경 → 고정 변수 제거

    /* ──────────────────────────────────────────────── */
    /* 초기화 */
    /* ──────────────────────────────────────────────── */
    fun initialize(regionId: String, officeId: String) {
        // regionId / officeId 가 비어있으면 SharedPreferences 의 값을 사용, 없으면 초기화 보류
        val appContext = getApplication<Application>().applicationContext
        val prefs      = appContext.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

        val finalRegionId = regionId.takeIf { it.isNotBlank() } ?: prefs.getString("regionId", null)
        val finalOfficeId = officeId.takeIf { it.isNotBlank() } ?: prefs.getString("officeId", null)

        if (finalRegionId.isNullOrBlank() || finalOfficeId.isNullOrBlank()) {
            // 아직 로그인/사무실 정보가 없는 상태 – 리스너 시작 없이 대기
            println("[SettlementViewModel] regionId / officeId 가 설정되지 않아 리스너를 시작하지 않습니다.")
            return
        }

        if (currentRegionId == finalRegionId && currentOfficeId == finalOfficeId) return

        currentRegionId = finalRegionId
        currentOfficeId = finalOfficeId

        startSettlementsListener(finalRegionId, finalOfficeId)
        startAllSettlementsListener(finalRegionId, finalOfficeId)
        startCreditsListener(finalRegionId, finalOfficeId)
        loadOfficeShareRatio(finalRegionId, finalOfficeId)
    }

    /* 업무일(06:00 컷) 계산 */
    fun calculateWorkDate(ts: Long): String {
        val cal = Calendar.getInstance(KST).apply { timeInMillis = ts }
        // 업무일 컷 기준을 06:00 → 13:00 로 변경 (13:00 ~ 다음날 12:59까지 동일 업무일)
        if (cal[Calendar.HOUR_OF_DAY] < 13) cal.add(Calendar.DAY_OF_MONTH, -1)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).apply {
            timeZone = KST
        }
        return sdf.format(cal.time)
    }

    // Alias for legacy call sites
    private fun workDate(ts: Long): String = calculateWorkDate(ts)

    /* ──────────────────────────────────────────────── */
    /* 실시간 리스너  */
    /* ──────────────────────────────────────────────── */
    private fun startSettlementsListener(r: String, o: String) {
        settlementsListener?.remove()
        settlementsListener = db.collection("regions").document(r)
            .collection("offices").document(o)
            .collection("settlements")
            .whereEqualTo("settlementStatus", Constants.SETTLEMENT_STATUS_PENDING)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { _error.value = e.localizedMessage; return@addSnapshotListener }
                _settlementList.value = snap?.documents?.mapNotNull {
                    it.toObject(SettlementInfo::class.java)?.toData(it.id)
                } ?: emptyList()
            }
    }

    private fun startAllSettlementsListener(r: String, o: String) {
        allSettlementsListener?.remove()
        allSettlementsListener = db.collection("regions").document(r)
            .collection("offices").document(o)
            .collection("settlements")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, e ->
                if (e != null) { _error.value = "전체내역 읽기 실패"; return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull {
                    it.toObject(SettlementInfo::class.java)?.toData(it.id)
                } ?: emptyList()
                val currentWorkDate = calculateWorkDate(System.currentTimeMillis())
                println("[SettlementViewModel] rawSize=${list.size}, currentWorkDate=$currentWorkDate")
                // 오늘 workDate & 미마감만 표시
                val activeList = list.filter { !it.isFinalized && it.workDate == currentWorkDate }
                _allSettlementList.value = activeList.sortedByDescending { it.completedAt }
                _allTripsCleared.value   = activeList.isEmpty()
                println("[SettlementViewModel] allSettlementsListener update: total=${list.size}, active=${activeList.size}")
            }
    }

    private fun startCreditsListener(r: String, o: String) {
        creditsListener?.remove()
        creditsListener = db.collection("regions").document(r)
            .collection("offices").document(o)
            .collection("credits")
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map {
                    CreditPerson(
                        id    = it.id,
                        name  = it.getString("name") ?: it.id,
                        phone = it.getString("phone") ?: "",
                        memo  = it.getString("memo") ?: "",
                        amount= (it.getLong("totalOwed") ?: 0L).toInt()
                    )
                } ?: emptyList()
                _creditPersons.value = list.sortedByDescending { it.amount }
            }
    }

    override fun onCleared() {
        super.onCleared()
        settlementsListener?.remove()
        allSettlementsListener?.remove()
        creditsListener?.remove()
    }

    /* ──────────────────────────────────────────────── */
    /* ‘업무 마감’ */
    fun clearAllTrips() {
        val r = currentRegionId ?: run { _error.value = "지역 ID가 없습니다."; return }
        val o = currentOfficeId ?: run { _error.value = "사무실 ID가 없습니다."; return }
        _isLoading.value = true

        // 1) 로컬 Firestore 배치로 즉시 마감 – 네트워크만 연결돼 있으면 동작
        finalizeLocally(r, o)

        // 2) (선택) Cloud Function 호출 – 백엔드 집계용. 실패해도 UI에는 영향 없음
        functions.getHttpsCallable("finalizeDailySettlement")
            .call(mapOf("regionId" to r, "officeId" to o))
            .addOnCompleteListener { _isLoading.value = false }
    }

    /** Cloud Function 실패 시 로컬에서 마감 처리 */
    private fun finalizeLocally(regionId: String, officeId: String) {
        Log.w("SettlementViewModel", "Cloud Function 실패 – 로컬 finalize 시도")
        val settlementsCol = db.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("settlements")

        val currentWorkDate = calculateWorkDate(System.currentTimeMillis())
        settlementsCol
            .get()
            .addOnSuccessListener { snap ->
                val pendingDocs = snap.documents.filter { doc ->
                    val fin = doc.getBoolean("isFinalized") ?: false
                    if (!fin) {
                        val completedTs = doc.getTimestamp("completedAt")?.toDate()?.time ?: 0L
                        val wd = calculateWorkDate(completedTs)
                        Log.d("SettlementViewModel", "check doc ${doc.id}: isFinalized=$fin, workDate=$wd (will finalize)")
                    }
                    !fin
                }
                if (pendingDocs.isEmpty()) {
                    _error.value = "마감 대상 운행이 없습니다."; return@addOnSuccessListener
                }

                var totalFare = 0
                var totalTrips = 0
                val batch = db.batch()

                for (doc in pendingDocs) {
                    val fare = (doc.getLong("fareFinal") ?: doc.getLong("fare") ?: 0L).toInt()
                    totalFare += fare
                    totalTrips += 1
                    batch.update(doc.reference, "isFinalized", true)
                }

                val dateStr = calculateWorkDate(System.currentTimeMillis())
                val dailyRef = db.collection("regions").document(regionId)
                    .collection("offices").document(officeId)
                    .collection("dailySettlements").document(dateStr)

                val dailyData = hashMapOf<String, Any>(
                    "totalFare" to FieldValue.increment(totalFare.toLong()),
                    "totalTrips" to FieldValue.increment(totalTrips.toLong()),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(dailyRef, dailyData, SetOptions.merge())

                batch.commit()
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Log.d("SettlementViewModel", "로컬 finalize 성공 – finalized $totalTrips trips")
                        } else {
                            Log.e("SettlementViewModel", "⚠️ 로컬 finalize 실패", task.exception)
                            _error.value = "업무 마감 실패: ${task.exception?.localizedMessage}"
                        }
                    }
            }
            .addOnFailureListener { e -> _error.value = "업무 마감 실패: ${e.localizedMessage}" }
    }

    /* ──────────────────────────────────────────────── */
    /* 정산 완료 / 외상 관리 */
    /* ──────────────────────────────────────────────── */
    fun markSettlementSettled(id: String) {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return
        db.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("settlements").document(id)
            .update("settlementStatus", Constants.SETTLEMENT_STATUS_SETTLED)
            .addOnSuccessListener { Log.d("SettlementViewModel", "✅ settlement $id marked SETTLED") }
            .addOnFailureListener { e -> Log.e("SettlementViewModel", "❌ failed to mark settled", e) }
    }

    private fun sanitizeKey(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(80)
    }

    fun addOrIncrementCredit(name: String, phone: String, addAmount: Int, memo: String = "") {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return
        if(phone.isBlank()){
            _error.value = "전화번호가 없는 고객은 외상 등록을 할 수 없습니다."
            return
        }
        val docId = sanitizeKey(phone)

        val creditRef = db.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("credits").document(docId)

        db.runTransaction { transaction ->
            val snapshot = transaction.get(creditRef)
            if (snapshot.exists()) {
                transaction.update(creditRef, "totalOwed", FieldValue.increment(addAmount.toLong()))
            } else {
                val data = hashMapOf(
                    "name" to name,
                    "phone" to phone,
                    "memo" to memo,
                    "totalOwed" to addAmount.toLong(),
                    "createdAt" to FieldValue.serverTimestamp()
                )
                transaction.set(creditRef, data)
            }
            null
        }.addOnSuccessListener {
            Log.d("SettlementViewModel", "Credit added/incremented successfully for $phone")
        }.addOnFailureListener { e ->
            Log.e("SettlementViewModel", "Failed to add/increment credit", e)
            _error.value = "외상 등록에 실패했습니다."
        }
    }

    fun reduceCredit(creditId: String, reduceAmount: Int) {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return
        if (creditId.isBlank()){
            _error.value = "외상 고객 ID가 없습니다."
            return
        }

        val creditRef = db.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("credits").document(creditId)

        creditRef.update("totalOwed", FieldValue.increment(-reduceAmount.toLong()))
            .addOnSuccessListener { Log.d("SettlementViewModel", "Credit reduced successfully.") }
            .addOnFailureListener { e -> _error.value = "외상 회수 처리에 실패했습니다." }
    }

    fun fetchPhoneForCall(callId: String, cb: (String?)->Unit) {
        val region = currentRegionId ?: return cb(null)
        val office = currentOfficeId ?: return cb(null)

        db.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("calls").document(callId)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    cb(snap.getString("phoneNumber"))
                } else {
                    cb(null)
                }
            }
            .addOnFailureListener { cb(null) }
    }

    private fun loadOfficeShareRatio(regionId: String, officeId: String) {
        db.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .get()
            .addOnSuccessListener {
                val ratio = it.getLong("shareRatio")?.toInt() ?: 60
                _officeShareRatio.value = ratio
            }
    }

    fun updateOfficeShareRatio(newRatio: Int) {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return
        _officeShareRatio.value = newRatio // 로컬 즉시 반영
        db.collection("regions").document(region)
            .collection("offices").document(office)
            .update("shareRatio", newRatio)
            .addOnFailureListener { e -> Log.e("SettlementViewModel", "Failed to update share ratio", e) }
    }

    fun clearError() {
        _error.value = null
    }

    fun setError(msg: String) {
        _error.value = msg
    }

    private fun SettlementInfo.toData(docId: String): SettlementData = SettlementData(
        callId            = callId ?: "",
        settlementId    = docId,
        driverName      = driverName ?: "",
        customerName    = customerName ?: "",
        customerPhone   = customerPhone ?: "",
        departure       = departure ?: "",
        destination     = destination ?: "",
        waypoints       = waypoints ?: "",
        fare            = (fareFinal ?: fare ?: 0L).toInt(),
        paymentMethod   = paymentMethod ?: "",
        cardAmount      = null,
        cashAmount      = cashAmount?.toInt(),
        creditAmount    = creditAmount?.toInt() ?: 0,
        completedAt     = completedAt?.toDate()?.time ?: 0L,
        driverId        = driverId ?: "",
        regionId        = regionId ?: "",
        officeId        = officeId ?: "",
        settlementStatus= settlementStatus ?: Constants.SETTLEMENT_STATUS_PENDING,
        workDate        = workDate(completedAt?.toDate()?.time ?: 0L),
        isFinalized     = isFinalized ?: false
    )
}
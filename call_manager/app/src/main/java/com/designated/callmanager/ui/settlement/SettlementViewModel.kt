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
// FirebaseFunctions import 제거 – 세션 기능 삭제됨
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
    private val _dailySummary        = MutableStateFlow<com.designated.callmanager.data.DailySummary?>(null)
    private val _finalizedList       = MutableStateFlow<List<SettlementData>>(emptyList())
    private val _rawSettlements   = MutableStateFlow<List<SettlementData>>(emptyList())
    private val _dailySessions = MutableStateFlow<List<com.designated.callmanager.data.SessionInfo>>(emptyList())
    // 세션 ID 로직 제거 – workDate + isFinalized 필터만 사용

    val settlementList  : StateFlow<List<SettlementData>> = _settlementList
    val allSettlementList: StateFlow<List<SettlementData>> = _allSettlementList
    val finalizedList: StateFlow<List<SettlementData>> = _finalizedList
    val allTripsCleared : StateFlow<Boolean>                = _allTripsCleared
    val isLoading       : StateFlow<Boolean>                = _isLoading
    val error           : StateFlow<String?>                = _error
    val creditPersons   : StateFlow<List<CreditPerson>>     = _creditPersons
    val officeShareRatio: StateFlow<Int>                    = _officeShareRatio
    val dailySummary: StateFlow<com.designated.callmanager.data.DailySummary?> = _dailySummary
    val dailySessions: StateFlow<List<com.designated.callmanager.data.SessionInfo>> = _dailySessions
    val rawSettlementList: StateFlow<List<SettlementData>> = _rawSettlements

    /* ②  Firebase */
    private val db          = FirebaseFirestore.getInstance()
    // Cloud Functions 호출 제거 – 로컬 배치만 사용
    private var settlementsListener    : ListenerRegistration? = null
    private var allSettlementsListener: ListenerRegistration? = null
    private var creditsListener        : ListenerRegistration? = null
    private var dailySummaryListener : ListenerRegistration? = null

    /* ③  현재 지역/사무실 */
    private var currentRegionId: String? = null
    private var currentOfficeId: String? = null
    // todayWorkDate 는 매 호출 시점에 동적으로 계산하도록 변경 → 고정 변수 제거

    private val prefs = application.getSharedPreferences("login_prefs", android.content.Context.MODE_PRIVATE)
    private var lastClearTs: Long = prefs.getLong("lastClearTs", 0L)

    /* ──────────────────────────────────────────────── */
    /*  Auth 상태 변화 감시 → 리스너 정리 / 재시작     */
    /* ──────────────────────────────────────────────── */
    private val auth = com.google.firebase.auth.FirebaseAuth.getInstance()

    private val authStateListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        if (user == null) {
            // 로그아웃 → 모든 Firestore 리스너 해제
            removeAllListeners()
            Log.d("SettlementViewModel", "AuthStateListener: signed OUT, listeners removed")
        } else {
            // 로그인 완료 → region/office가 세팅돼 있으면 리스너 재시작
            val r = currentRegionId
            val o = currentOfficeId
            if (!r.isNullOrBlank() && !o.isNullOrBlank()) {
                restartListeners()
                Log.d("SettlementViewModel", "AuthStateListener: signed IN, listeners restarted")
            }
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    /* ──────────────────────────────────────────────── */
    /* 초기화 */
    /* ──────────────────────────────────────────────── */
    fun initialize(regionId: String, officeId: String) {
        // regionId / officeId 가 비어있으면 SharedPreferences 의 값을 사용, 없으면 초기화 보류
        val appContext = getApplication<Application>().applicationContext
        val prefs      = appContext.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)

        val finalRegionId = regionId.takeIf { it.isNotBlank() } ?: prefs.getString("regionId", null)
        val finalOfficeId = officeId.takeIf { it.isNotBlank() } ?: prefs.getString("officeId", null)

        Log.d("SettlementViewModel", "SharedPreferences에서 읽은 값: regionId=${prefs.getString("regionId", null)}, officeId=${prefs.getString("officeId", null)}")
        Log.d("SettlementViewModel", "파라미터로 받은 값: regionId=$regionId, officeId=$officeId")
        
        if (finalRegionId.isNullOrBlank() || finalOfficeId.isNullOrBlank()) {
            // 아직 로그인/사무실 정보가 없는 상태 – 리스너 시작 없이 대기
            println("[SettlementViewModel] regionId / officeId 가 설정되지 않아 리스너를 시작하지 않습니다.")
            return
        }

        if (currentRegionId == finalRegionId && currentOfficeId == finalOfficeId) return

        Log.d("SettlementViewModel", "초기화 - 최종 사용할 지역/사무실: $finalRegionId/$finalOfficeId")
        
        currentRegionId = finalRegionId
        currentOfficeId = finalOfficeId

        startSettlementsListener(finalRegionId, finalOfficeId)
        startAllSettlementsListener(finalRegionId, finalOfficeId)
        startCreditsListener(finalRegionId, finalOfficeId)
        loadOfficeShareRatio(finalRegionId, finalOfficeId)
        startDailySessionsListener(finalRegionId, finalOfficeId)
    }

    // 세션 기능 제거 – loadCurrentSession 불필요

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
        val today = calculateWorkDate(System.currentTimeMillis())
        settlementsListener = db.collection("regions").document(r)
            .collection("offices").document(o)
            .collection("settlements")
            .whereEqualTo("workDate", today)
            .whereEqualTo("isFinalized", false) // 미마감만
            // 외상·이체 결제건만 정산 대기에 포함
            .whereIn("paymentMethod", listOf("이체", "외상"))
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.EXCLUDE) { snap, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "settlementsListener 오류: ${e.message}", e)
                    _error.value = "정산 대기 목록 읽기 실패: ${e.localizedMessage}"
                    return@addSnapshotListener
                }
                _settlementList.value = snap?.documents?.mapNotNull {
                    it.toObject(SettlementInfo::class.java)?.toData(it.id)
                }?.filter { !it.isFinalized } ?: emptyList()
                Log.d("SettlementViewModel", "settlementsListener: ${_settlementList.value.size}개 PENDING 운행")
            }
    }

    private fun startAllSettlementsListener(r: String, o: String) {
        allSettlementsListener?.remove()
        val today = calculateWorkDate(System.currentTimeMillis())
        allSettlementsListener = db.collection("regions").document(r)
            .collection("offices").document(o)
            .collection("settlements")
            .whereEqualTo("workDate", today)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.EXCLUDE) { snap, e ->
                if (e != null) {
                    Log.e("SettlementViewModel", "allSettlementsListener 오류: ${e.message}", e)
                    _error.value = "전체 내역 읽기 실패: ${e.localizedMessage}"
                    return@addSnapshotListener
                }
                // DEBUG: 원본 DocumentSnapshot에서 isFinalized 필드 값 확인
                snap?.documents?.forEach { doc ->
                    val rawFin = doc.getBoolean("isFinalized")
                    Log.d("SettlementDebug", "doc ${doc.id} raw isFinalized=${rawFin}")
                }
                val allTodayRaw = snap?.documents?.mapNotNull {
                    it.toObject(SettlementInfo::class.java)?.toData(it.id)
                } ?: emptyList()

                // unfiltered 목록은 히스토리 탭용으로 별도 보관
                _rawSettlements.value = allTodayRaw.sortedByDescending { it.completedAt }

                val sessionSettlements = allTodayRaw.filter { it.completedAt >= lastClearTs }

                val pending   = sessionSettlements.filter { !it.isFinalized }
                val finalized = sessionSettlements.filter { it.isFinalized }

                _settlementList.value   = pending.sortedByDescending { it.completedAt }
                _finalizedList.value    = finalized.sortedByDescending { it.completedAt }
                _allSettlementList.value = sessionSettlements.sortedByDescending { it.completedAt }

                _allTripsCleared.value = pending.isEmpty()

                Log.d("SettlementViewModel", "allSettlementsListener: pending=${pending.size}, finalized=${finalized.size}")
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

    private fun startDailySessionsListener(r: String, o: String) {
        dailySummaryListener?.remove()
        val today = calculateWorkDate(System.currentTimeMillis())
        val sessCol = db.collection("regions").document(r)
            .collection("offices").document(o)
            .collection("dailySettlements").document(today)
            .collection("sessions")

        dailySummaryListener = sessCol.orderBy("endAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map {
                    com.designated.callmanager.data.SessionInfo(
                        sessionId = it.id,
                        endAt = it.getTimestamp("endAt"),
                        totalFare = it.getLong("totalFare") ?: 0,
                        totalTrips = (it.getLong("totalTrips") ?: 0L).toInt()
                    )
                } ?: emptyList()
                _dailySessions.value = list
            }
    }

    override fun onCleared() {
        super.onCleared()
        removeAllListeners()
        auth.removeAuthStateListener(authStateListener)
    }

    /* ──────────────────────────────────────────────── */
    /* ‘업무 마감’ */
    fun clearAllTrips() {
        val r = currentRegionId ?: run { _error.value = "지역 ID가 없습니다."; return }
        val o = currentOfficeId ?: run { _error.value = "사무실 ID가 없습니다."; return }
        _isLoading.value = true

        // 🔌 리스너 일시 해제 – 마감 처리 동안 UI 갱신 방지
        settlementsListener?.remove(); settlementsListener = null
        allSettlementsListener?.remove(); allSettlementsListener = null

        finalizeLocally(r, o)  // 로컬 배치로만 처리 – Cloud Function 호출 제거
    }

    /** Cloud Function 실패 시 로컬에서 마감 처리 */
    private fun finalizeLocally(regionId: String, officeId: String) {
        Log.w("SettlementViewModel", "Cloud Function 실패 – 로컬 finalize 시도")
        
        // 현재 사용자의 권한 확인
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Log.e("SettlementViewModel", "❌ 현재 로그인된 사용자가 없습니다")
            _error.value = "로그인이 필요합니다"
            return
        }
        
        Log.d("SettlementViewModel", "현재 사용자 UID: ${currentUser.uid}")
        Log.d("SettlementViewModel", "대상 지역/사무실: $regionId/$officeId")
        
        // 관리자 문서 확인
        db.collection("admins").document(currentUser.uid).get()
            .addOnSuccessListener { adminDoc ->
                if (adminDoc.exists()) {
                    val adminRegion = adminDoc.getString("associatedRegionId")
                    val adminOffice = adminDoc.getString("associatedOfficeId")
                    Log.d("SettlementViewModel", "Admin 문서: region=$adminRegion, office=$adminOffice")
                    if (adminRegion != regionId || adminOffice != officeId) {
                        Log.e("SettlementViewModel", "❌ 권한 불일치! Admin 문서와 현재 사무실이 다릅니다")
                    }
                } else {
                    Log.e("SettlementViewModel", "❌ Admin 문서가 존재하지 않습니다: /admins/${currentUser.uid}")
                }
            }
            .addOnFailureListener { e ->
                Log.e("SettlementViewModel", "❌ Admin 문서 읽기 실패", e)
            }
        
        val settlementsCol = db.collection("regions").document(regionId)
            .collection("offices").document(officeId)
            .collection("settlements")

        Log.d("SettlementViewModel", "🔍 settlements 컬렉션 조회 시작: $regionId/$officeId")
        
        settlementsCol
            .whereEqualTo("workDate", calculateWorkDate(System.currentTimeMillis()))
            .get()
            .addOnSuccessListener { snap ->
                Log.d("SettlementViewModel", "✅ settlements 컬렉션 조회 성공: ${snap.documents.size}개 문서")
                
                Log.d("SettlementViewModel", "🔍 문서 필터링 시작...")
                var totalDocs = 0
                var unfinalizedDocs = 0
                val currentWorkDate = calculateWorkDate(System.currentTimeMillis())
                
                val pendingDocs = snap.documents.filter { doc ->
                    totalDocs++
                    val fin = doc.getBoolean("isFinalized") ?: false
                    val status = doc.getString("settlementStatus") ?: ""
                    val completedTs = doc.getTimestamp("completedAt")?.toDate()?.time ?: 0L
                    val wd = calculateWorkDate(completedTs)
                    
                    Log.d("SettlementViewModel", "문서 ${doc.id}: isFinalized=$fin, status=$status, workDate=$wd")
                    
                    if (!fin) {
                        unfinalizedDocs++
                        Log.d("SettlementViewModel", "check doc ${doc.id}: isFinalized=$fin, status=$status, workDate=$wd (will finalize)")
                    }
                    !fin
                }
                
                Log.d("SettlementViewModel", "📊 필터링 결과: 전체=$totalDocs, 미마감=$unfinalizedDocs, 대상=${pendingDocs.size}")
                
                if (pendingDocs.isEmpty()) {
                    Log.w("SettlementViewModel", "⚠️ 마감 대상 운행이 없습니다")
                    // 마감 대상이 없어도 UI 클리어 (새로운 업무 시작 준비)
                    _settlementList.value = emptyList()
                    _allSettlementList.value = emptyList()
                    _finalizedList.value = emptyList()
                    lastClearTs = System.currentTimeMillis()
                    prefs.edit().putLong("lastClearTs", lastClearTs).apply()
                    _allTripsCleared.value = true
                    _error.value = "이미 업무가 마감되었습니다. 새로운 업무를 시작하세요."
                    _isLoading.value = false // 🛠️ 로딩 종료
                    Log.d("SettlementViewModel", "✨ 이미 마감된 상태 - UI 클리어됨")
                    restartListeners() // 리스너 복구 누락 수정
                    return@addOnSuccessListener
                }

                Log.d("SettlementViewModel", "💡 마감 대상 운행 ${pendingDocs.size}개 발견")
                
                var totalFare = 0
                var totalTrips = 0
                val batch = db.batch()

                for (doc in pendingDocs) {
                    // 업데이트 전 상태 확인
                    Log.d("SettlementViewModel", "업데이트 전 ${doc.id}: isFinalized=${doc.getBoolean("isFinalized")}, status=${doc.getString("settlementStatus")}")
                    val fare = (doc.getLong("fareFinal") ?: doc.getLong("fare") ?: 0L).toInt()
                    totalFare += fare
                    totalTrips += 1
                    // 마감 시 isFinalized 플래그만 먼저 업데이트 (권한 문제 방지)
                    batch.update(doc.reference, "isFinalized", true)
                    Log.d("SettlementViewModel", "Batch에 추가: ${doc.id} -> isFinalized=true")
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

                Log.d("SettlementViewModel", "🚀 Batch commit 시작...")
                batch.commit()
                    .addOnCompleteListener { task ->
                        _isLoading.value = false // 🛠️ 로딩 종료 (성공/실패 공통)
                        if (task.isSuccessful) {
                            Log.d("SettlementViewModel", "로컬 finalize 성공 – finalized $totalTrips trips")
                            
                            // 업무 마감 성공 시 즉시 UI 클리어
                            _settlementList.value = emptyList()
                            _allSettlementList.value = emptyList()
                            _finalizedList.value = emptyList()
                            lastClearTs = System.currentTimeMillis()
                            prefs.edit().putLong("lastClearTs", lastClearTs).apply()
                            _allTripsCleared.value = true
                            Log.d("SettlementViewModel", "✨ 업무 마감 완료 - UI 클리어됨")
                            
                            // 업데이트 후 상태 확인을 위해 문서들을 다시 읽어봄
                            pendingDocs.forEach { doc ->
                                doc.reference.get().addOnSuccessListener { updatedDoc ->
                                    Log.d("SettlementViewModel", "업데이트 후 ${doc.id}: isFinalized=${updatedDoc.getBoolean("isFinalized")}, status=${updatedDoc.getString("settlementStatus")}")
                                }
                            }
                            
                            // Firestore 서버에 쓰기 확정될 때까지 대기 후 리스너 재등록
                            db.waitForPendingWrites().addOnSuccessListener {
                                val currentRegion = currentRegionId
                                val currentOffice = currentOfficeId
                                if (currentRegion != null && currentOffice != null) {
                                    startSettlementsListener(currentRegion, currentOffice)
                                    startAllSettlementsListener(currentRegion, currentOffice)
                                    Log.d("SettlementViewModel", "🔄 리스너 재시작 완료 (pendingWrites flushed)")
                                }
                            }

                            Log.d("SettlementViewModel", "�� Batch commit 성공")

                            // ▶ 세션 문서 기록 (/dailySettlements/{workDate}/sessions)
                            val workDate = calculateWorkDate(System.currentTimeMillis())
                            val sessionsCol = db.collection("regions").document(regionId)
                                .collection("offices").document(officeId)
                                .collection("dailySettlements").document(workDate)
                                .collection("sessions")

                            val sessionData = hashMapOf(
                                "endAt"        to com.google.firebase.Timestamp.now(),
                                "totalFare"     to totalFare,
                                "totalTrips"    to pendingDocs.size,
                                "lastClearTs"   to lastClearTs
                            )

                            sessionsCol.add(sessionData)
                                .addOnSuccessListener { Log.d("SettlementViewModel", "✅ session 카드 생성 완료") }
                                .addOnFailureListener { e -> Log.e("SettlementViewModel", "❌ session 카드 생성 실패", e) }
                        } else {
                            val exception = task.exception
                            Log.e("SettlementViewModel", "⚠️ 로컬 finalize 실패: ${exception?.javaClass?.simpleName}", exception)
                            when {
                                exception?.message?.contains("PERMISSION_DENIED") == true -> {
                                    Log.e("SettlementViewModel", "💥 권한 거부: 관리자 설정을 확인하세요")
                                    _error.value = "권한이 없습니다. 관리자 설정을 확인하세요."
                                }
                                exception?.message?.contains("FAILED_PRECONDITION") == true -> {
                                    Log.e("SettlementViewModel", "💥 전제조건 실패: 인덱스 또는 규칙 문제")
                                    _error.value = "데이터베이스 설정 문제입니다."
                                }
                                else -> {
                                    _error.value = "업무 마감 실패: ${exception?.localizedMessage}"
                                }
                            }
                        }
                        // 실패/성공과 무관하게 리스너 복구 (대기 중이던 경우 제외)
                        if(!task.isSuccessful){ restartListeners() }
                    }
            }
            .addOnFailureListener { e -> 
                Log.e("SettlementViewModel", "❌ settlements 컬렉션 조회 실패", e)
                _error.value = "업무 마감 실패: ${e.localizedMessage}"
                _isLoading.value = false // 🛠️ 로딩 종료
                restartListeners() // 쿼리 실패 시에도 리스너 복구
            }
    }

    /* ──────────────────────────────────────────────── */
    /* 정산 완료 / 외상 관리 */
    /* ──────────────────────────────────────────────── */
    fun markSettlementSettled(id: String) {
        val region = currentRegionId ?: return
        val office = currentOfficeId ?: return
        val settleRef = db.collection("regions").document(region)
            .collection("offices").document(office)
            .collection("settlements").document(id)

        settleRef.update(mapOf(
            "settlementStatus" to Constants.SETTLEMENT_STATUS_SETTLED,
            "isFinalized"      to true
        ))
            .addOnSuccessListener { Log.d("SettlementViewModel", "✅ settlement $id marked SETTLED") }
            .addOnFailureListener { e -> Log.e("SettlementViewModel", "❌ failed to mark settled", e) }

        // 서버에 쓰기가 확정된 이후 리스트 갱신 (중복 업데이트 방지)
        db.waitForPendingWrites().addOnSuccessListener {
            // 서버에서 값 확인 후 제거
            settleRef.get(com.google.firebase.firestore.Source.SERVER).addOnSuccessListener { snap ->
                val fin = snap.getBoolean("isFinalized") == true
                if (fin) {
                    _settlementList.update { list -> list.filterNot { it.settlementId == id } }
                    _allSettlementList.update { list -> list.filterNot { it.settlementId == id } }
                } else {
                    // 한번 더 보강 업데이트 시도
                    settleRef.update("isFinalized", true)
                }
            }
        }
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
        isFinalized     = isFinalized
    )

    // openNewSession / closeCurrentSession 기능 제거 – 단순화

    private fun restartListeners() {
        val r = currentRegionId ?: return
        val o = currentOfficeId ?: return
        startSettlementsListener(r, o)
        startAllSettlementsListener(r, o)
        Log.d("SettlementViewModel", "🔄 리스너 재시작 (helper)")
    }

    /** 공통 리스너 해제 */
    private fun removeAllListeners() {
        settlementsListener?.remove(); settlementsListener = null
        allSettlementsListener?.remove(); allSettlementsListener = null
        creditsListener?.remove(); creditsListener = null
        dailySummaryListener?.remove(); dailySummaryListener = null
    }
}
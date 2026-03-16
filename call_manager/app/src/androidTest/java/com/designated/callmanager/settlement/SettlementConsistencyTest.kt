package com.designated.callmanager.settlement

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.settlement.SettlementCalculator
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 Firestore에 연결하여 정산 계산 일관성을 검증하는 Instrumented Test
 * - 테스트 시작 시 자동 로그인 (재설치 후에도 동작)
 * - Firestore의 COMPLETED 콜을 조회하여 SettlementCalculator 결과와 비교
 */
@RunWith(AndroidJUnit4::class)
class SettlementConsistencyTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var provinceId: String
    private lateinit var cityId: String
    private lateinit var officeId: String
    private val RATIO = 60

    companion object {
        private const val TEST_EMAIL = "vip@naver.com"
        private const val TEST_PASSWORD = "236767"
    }

    @Before
    fun setup() {
        firestore = FirebaseFirestore.getInstance()

        // 자동 로그인 (재설치 후에도 동작)
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Log.d("SettlementTest", "로그인 시도: $TEST_EMAIL")
            Tasks.await(auth.signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD))
            assertNotNull("로그인 실패", auth.currentUser)

            // 로그인 후 admins에서 사무실 정보 조회
            val uid = auth.currentUser!!.uid
            val adminDoc = Tasks.await(firestore.collection("admins").document(uid).get())
            val associatedOfficeId = adminDoc.getString("associatedOfficeId") ?: ""
            assertTrue("admins 문서에 associatedOfficeId 없음", associatedOfficeId.isNotBlank())

            // 사무실 문서에서 provinceId/cityId 추출
            val officeQuery = Tasks.await(
                firestore.collectionGroup("offices")
                    .whereEqualTo("__name__", associatedOfficeId)
                    .limit(1)
                    .get()
            )

            // collectionGroup 대신 직접 경로 파싱
            // associatedOfficeId 형식: offices/{officeId} 또는 순수 officeId
            // admins 문서에서 전체 경로 정보도 확인
            val adminProvinceId = adminDoc.getString("provinceId") ?: ""
            val adminCityId = adminDoc.getString("cityId") ?: ""

            if (adminProvinceId.isNotBlank() && adminCityId.isNotBlank()) {
                provinceId = adminProvinceId
                cityId = adminCityId
                officeId = associatedOfficeId
            } else {
                // SharedPreferences fallback
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                val prefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                provinceId = prefs.getString("provinceId", "") ?: ""
                cityId = prefs.getString("cityId", "") ?: ""
                officeId = prefs.getString("officeId", "") ?: ""
            }

            // SharedPreferences에 저장 (다음 테스트를 위해)
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("provinceId", provinceId)
                .putString("cityId", cityId)
                .putString("officeId", officeId)
                .apply()
        } else {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
            provinceId = prefs.getString("provinceId", "") ?: ""
            cityId = prefs.getString("cityId", "") ?: ""
            officeId = prefs.getString("officeId", "") ?: ""
        }

        assertTrue("provinceId가 비어있음", provinceId.isNotBlank())
        assertTrue("cityId가 비어있음", cityId.isNotBlank())
        assertTrue("officeId가 비어있음", officeId.isNotBlank())
    }

    private fun getCallsCollection() =
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")

    private fun documentToSettlementData(doc: com.google.firebase.firestore.DocumentSnapshot): SettlementData? {
        val status = doc.getString("status") ?: return null
        if (status != "COMPLETED") return null

        return SettlementData(
            callId = doc.id,
            driverName = doc.getString("driverName") ?: "",
            customerName = doc.getString("customerName") ?: "",
            departure = doc.getString("departure") ?: "",
            destination = doc.getString("destination") ?: "",
            fare = (doc.getLong("fare") ?: 0).toInt(),
            paymentMethod = doc.getString("paymentMethod") ?: "현금",
            cardAmount = doc.getLong("cardAmount")?.toInt(),
            cashAmount = doc.getLong("cashAmount")?.toInt(),
            creditAmount = (doc.getLong("creditAmount") ?: 0).toInt(),
            pointsUsed = (doc.getLong("pointsUsed") ?: 0).toInt(),
            completedAt = doc.getTimestamp("completedAt")?.toDate()?.time ?: 0L,
            driverId = doc.getString("driverId") ?: ""
        )
    }

    // ===== Test 1: Firestore 데이터와 계산기 결과 일치 =====

    @Test
    fun firestoreTrips_calculatorConsistency() {
        val snapshot = Tasks.await(
            getCallsCollection()
                .whereEqualTo("status", "COMPLETED")
                .limit(100)
                .get()
        )

        val trips = snapshot.documents.mapNotNull { documentToSettlementData(it) }

        if (trips.isEmpty()) {
            // 완료된 콜이 없으면 테스트 건너뜀 (데이터 없는 상태)
            return
        }

        // 기사별 통계 계산 (driverId가 있는 콜만)
        val validTrips = trips.filter { it.driverId.isNotBlank() }
        val stats = SettlementCalculator.calculateDriverStats(validTrips, RATIO)
        assertTrue("기사 통계가 생성되어야 함", stats.isNotEmpty())

        // 체크포인트 1: 기사별 deposit = fareSum × ratio / 100
        stats.forEach { stat ->
            val expectedDeposit = SettlementCalculator.calculateOfficeDeposit(stat.totalFare, RATIO)
            assertEquals("${stat.name}: deposit = totalFare × $RATIO%", expectedDeposit, stat.deposit)
        }

        // 체크포인트 2: realDeposit = deposit - totalCredit
        stats.forEach { stat ->
            assertEquals("${stat.name}: realDeposit = deposit - totalCredit",
                stat.deposit - stat.totalCredit, stat.realDeposit)
        }

        // 체크포인트 3: 기사별 콜수 합 = 유효 콜수
        assertEquals("기사별 콜수 합 = 유효 콜수", validTrips.size, stats.sumOf { it.count })

        // 체크포인트 4: 기사별 totalFare 합 = 유효 totalFare
        assertEquals("기사별 totalFare 합 = 유효 totalFare", validTrips.sumOf { it.fare }, stats.sumOf { it.totalFare })
    }

    // ===== Test 2: 결제방법별 분류 정합성 =====

    @Test
    fun firestoreTrips_paymentBreakdownConsistency() {
        val snapshot = Tasks.await(
            getCallsCollection()
                .whereEqualTo("status", "COMPLETED")
                .limit(100)
                .get()
        )

        val trips = snapshot.documents.mapNotNull { documentToSettlementData(it) }
            .filter { it.driverId.isNotBlank() }
        if (trips.isEmpty()) return

        val breakdown = SettlementCalculator.calculatePaymentBreakdown(trips)

        // totalFare 일치
        assertEquals("breakdown.totalFare = 콜 합계", trips.sumOf { it.fare }, breakdown.totalFare)

        // realIncome 항등식 검증
        val officeDeposit = SettlementCalculator.calculateOfficeDeposit(breakdown.totalFare, RATIO)
        val driverShare = breakdown.totalFare - officeDeposit
        val driverDeposit = SettlementCalculator.calculateDriverDeposit(breakdown.cashSum, driverShare)
        val realIncome = SettlementCalculator.calculateRealIncome(driverDeposit, breakdown.bankSum, breakdown.creditSum, breakdown.pointSum)

        val expected = officeDeposit - breakdown.totalFare + breakdown.cashSum + breakdown.bankSum + breakdown.creditSum - breakdown.pointSum
        assertEquals("realIncome 항등식", expected, realIncome)
    }

    // ===== Test 3: 미지급금 계산 정합성 =====

    @Test
    fun firestoreTrips_unpaidCalculationConsistency() {
        val snapshot = Tasks.await(
            getCallsCollection()
                .whereEqualTo("status", "COMPLETED")
                .limit(100)
                .get()
        )

        val trips = snapshot.documents.mapNotNull { documentToSettlementData(it) }
            .filter { it.driverId.isNotBlank() }
        if (trips.isEmpty()) return

        val stats = SettlementCalculator.calculateDriverStats(trips, RATIO)
        val unpaid = SettlementCalculator.calculateTodayUnpaidByDriver(trips, RATIO)

        // realDeposit < 0인 기사에게만 미지급금 발생
        stats.forEach { stat ->
            val driverUnpaid = unpaid[stat.driverId] ?: 0
            if (stat.realDeposit >= 0) {
                assertEquals("${stat.name}: realDeposit >= 0 → 미지급금 0", 0, driverUnpaid)
            } else {
                assertEquals("${stat.name}: 미지급금 = -realDeposit", -stat.realDeposit, driverUnpaid)
            }
        }
    }

    // ===== Test 4: 테스트 콜 생성 → 계산 → 삭제 =====

    @Test
    fun writeTestCalls_verifyCalculation_cleanup() {
        val testCallIds = mutableListOf<String>()

        try {
            // 1. 테스트 콜 5건 생성
            val testCalls = listOf(
                mapOf("fare" to 20000L, "paymentMethod" to "현금", "cashAmount" to 20000L, "driverName" to "테스트기사", "driverId" to "test_driver_001"),
                mapOf("fare" to 25000L, "paymentMethod" to "이체", "driverName" to "테스트기사", "driverId" to "test_driver_001"),
                mapOf("fare" to 30000L, "paymentMethod" to "현금+포인트", "cashAmount" to 20000L, "driverName" to "테스트기사", "driverId" to "test_driver_001"),
                mapOf("fare" to 15000L, "paymentMethod" to "외상", "creditAmount" to 15000L, "driverName" to "테스트기사", "driverId" to "test_driver_001"),
                mapOf("fare" to 10000L, "paymentMethod" to "포인트", "driverName" to "테스트기사", "driverId" to "test_driver_001")
            )

            val callsRef = getCallsCollection()
            testCalls.forEach { callData ->
                val data = callData.toMutableMap<String, Any?>()
                data["status"] = "COMPLETED"
                data["completedAt"] = com.google.firebase.Timestamp.now()
                data["customerName"] = "테스트고객"
                data["departure"] = "테스트출발"
                data["destination"] = "테스트도착"
                data["isTestData"] = true

                val docRef = Tasks.await(callsRef.add(data))
                testCallIds.add(docRef.id)
            }

            // 2. 방금 생성한 콜 읽기
            val snapshot = Tasks.await(
                callsRef.whereEqualTo("isTestData", true)
                    .whereEqualTo("driverId", "test_driver_001")
                    .get()
            )
            val trips = snapshot.documents.mapNotNull { documentToSettlementData(it) }

            assertEquals("테스트 콜 5건 생성됨", 5, trips.size)

            // 3. 계산 검증
            val totalFare = trips.sumOf { it.fare } // 20000+25000+30000+15000+10000 = 100000
            assertEquals("totalFare = 100,000", 100000, totalFare)

            val stats = SettlementCalculator.calculateDriverStats(trips, RATIO)
            assertEquals("테스트기사 1명", 1, stats.size)

            val stat = stats.first()
            assertEquals("콜수 = 5", 5, stat.count)
            assertEquals("totalFare = 100,000", 100000, stat.totalFare)

            // deposit = 100000 × 60% = 60000
            assertEquals("deposit = 60,000", 60000, stat.deposit)

            // totalCredit = 0(현금) + 25000(이체) + 10000(현금포인트 포인트부분) + 15000(외상) + 10000(포인트) = 60000
            assertEquals("totalCredit = 60,000", 60000, stat.totalCredit)

            // realDeposit = 60000 - 60000 = 0
            assertEquals("realDeposit = 0", 0, stat.realDeposit)

        } finally {
            // 4. 테스트 데이터 정리
            testCallIds.forEach { id ->
                Tasks.await(getCallsCollection().document(id).delete())
            }
        }
    }
}

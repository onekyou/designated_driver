package com.designated.driverapp.settlement

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.designated.driverapp.data.Constants
import com.designated.driverapp.data.settlement.SettlementCalc
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실제 Firestore에 연결하여 기사앱 정산 계산 일관성을 검증하는 Instrumented Test
 * - 테스트 시작 시 자동 로그인 (재설치 후에도 동작)
 * - Firestore의 COMPLETED 콜을 조회하여 SettlementCalc 결과와 비교
 */
@RunWith(AndroidJUnit4::class)
class DriverSettlementConsistencyTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var provinceId: String
    private lateinit var cityId: String
    private lateinit var officeId: String
    private val RATIO = 60

    companion object {
        // Z Flip4 기사 계정
        private const val TEST_EMAIL = "vvip2d@naver.com"
        private const val TEST_PASSWORD = "236767"
    }

    @Before
    fun setup() {
        firestore = FirebaseFirestore.getInstance()

        // 자동 로그인
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Log.d("DriverSettlementTest", "로그인 시도: $TEST_EMAIL")
            Tasks.await(auth.signInWithEmailAndPassword(TEST_EMAIL, TEST_PASSWORD))
            assertNotNull("로그인 실패", auth.currentUser)

            // collectionGroup으로 기사 문서 찾기 → provinceId/cityId/officeId 추출
            val uid = auth.currentUser!!.uid
            val driverQuery = Tasks.await(
                firestore.collectionGroup("designated_drivers")
                    .whereEqualTo("authUid", uid)
                    .limit(1)
                    .get()
            )

            if (driverQuery.documents.isNotEmpty()) {
                val driverDoc = driverQuery.documents.first()
                // 경로: provinces/{p}/cities/{c}/offices/{o}/designated_drivers/{uid}
                val path = driverDoc.reference.path.split("/")
                // path = [provinces, {p}, cities, {c}, offices, {o}, designated_drivers, {uid}]
                if (path.size >= 6) {
                    provinceId = path[1]
                    cityId = path[3]
                    officeId = path[5]
                }
            }

            // SharedPreferences에 저장
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(Constants.PREF_KEY_PROVINCE_ID, provinceId)
                .putString(Constants.PREF_KEY_CITY_ID, cityId)
                .putString(Constants.PREF_KEY_OFFICE_ID, officeId)
                .apply()
        } else {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            provinceId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, "") ?: ""
            cityId = prefs.getString(Constants.PREF_KEY_CITY_ID, "") ?: ""
            officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, "") ?: ""
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

    // ===== Test 1: 기본 정산 계산 일관성 =====

    @Test
    fun basicSettlementCalc_formulaConsistency() {
        val snapshot = Tasks.await(
            getCallsCollection()
                .whereEqualTo("status", "COMPLETED")
                                .limit(100)
                .get()
        )

        if (snapshot.isEmpty) return

        // 각 콜별로 계산 일관성 검증
        var totalFare = 0
        var totalCredit = 0

        snapshot.documents.forEach { doc ->
            val fare = (doc.getLong("fare") ?: 0).toInt()
            val paymentMethod = doc.getString("paymentMethod") ?: "현금"
            val cashAmount = doc.getLong("cashAmount")?.toInt()

            totalFare += fare

            // 외상 계산
            val credit = when {
                paymentMethod == "현금" -> 0
                paymentMethod == "현금+포인트" -> {
                    val cash = cashAmount ?: 0
                    if (cash > 0) fare - cash else fare
                }
                else -> fare
            }
            totalCredit += credit
        }

        // SettlementCalc 공식 검증
        val officeDeposit = SettlementCalc.calculateOfficeDeposit(totalFare, RATIO)
        val driverShare = SettlementCalc.calculateDriverShare(totalFare, officeDeposit)
        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit)

        // 기본 항등식
        assertEquals("officeDeposit + driverShare = totalFare",
            totalFare, officeDeposit + driverShare)
        assertEquals("rawFinalDeposit = officeDeposit - totalCredit",
            officeDeposit - totalCredit, rawFinalDeposit)
    }

    // ===== Test 2: 이월금 계산 시나리오 =====

    @Test
    fun carryOverCalc_variousScenarios() {
        // 시나리오 1: 이월 없음
        val raw1 = 12000
        val adjusted1 = SettlementCalc.calculateAdjustedDeposit(raw1, 0)
        val remaining1 = SettlementCalc.calculateRemainingCarryOver(raw1, 0)
        assertEquals("이월 없음: 조정납입 = 원래값", raw1, adjusted1)
        assertEquals("이월 없음: 잔여 = 0", 0, remaining1)

        // 시나리오 2: 미수령금 일부 공제
        val raw2 = 12000
        val carryOver2 = 5000
        val adjusted2 = SettlementCalc.calculateAdjustedDeposit(raw2, carryOver2)
        val remaining2 = SettlementCalc.calculateRemainingCarryOver(raw2, carryOver2)
        val used2 = SettlementCalc.calculateUsedFromCarryOver(raw2, carryOver2)
        assertEquals("미수령금 공제: 조정납입 = 7000", 7000, adjusted2)
        assertEquals("미수령금 공제: 잔여 = 0", 0, remaining2)
        assertEquals("미수령금 공제: 공제액 = 5000", 5000, used2)

        // 시나리오 3: 미납금 추가
        val raw3 = 12000
        val carryOver3 = -3000
        val adjusted3 = SettlementCalc.calculateAdjustedDeposit(raw3, carryOver3)
        val remaining3 = SettlementCalc.calculateRemainingCarryOver(raw3, carryOver3)
        assertEquals("미납금 추가: 조정납입 = 15000", 15000, adjusted3)
        assertEquals("미납금 상쇄: 잔여 = 9000", 9000, remaining3)

        // 시나리오 4: 음수 납입 + 양수 이월
        val raw4 = -8000
        val carryOver4 = 5000
        val adjusted4 = SettlementCalc.calculateAdjustedDeposit(raw4, carryOver4)
        val remaining4 = SettlementCalc.calculateRemainingCarryOver(raw4, carryOver4)
        assertEquals("음수납입+양수이월: 조정납입 = 0", 0, adjusted4)
        assertEquals("음수납입+양수이월: 잔여 = 13000", 13000, remaining4)
    }

    // ===== Test 3: Firestore 데이터로 전체 플로우 검증 =====

    @Test
    fun firestoreData_fullSettlementFlow() {
        val snapshot = Tasks.await(
            getCallsCollection()
                .whereEqualTo("status", "COMPLETED")
                .limit(50)
                .get()
        )

        if (snapshot.isEmpty) return

        // 기사별 그룹핑
        val driverGroups = snapshot.documents.groupBy { it.getString("driverId") ?: "" }
            .filter { it.key.isNotBlank() }

        driverGroups.forEach { (driverId, docs) ->
            var fareSum = 0
            var creditSum = 0

            docs.forEach { doc ->
                val fare = (doc.getLong("fare") ?: 0).toInt()
                val paymentMethod = doc.getString("paymentMethod") ?: "현금"
                val cashAmount = doc.getLong("cashAmount")?.toInt()

                fareSum += fare
                creditSum += when {
                    paymentMethod == "현금" -> 0
                    paymentMethod == "현금+포인트" -> {
                        val cash = cashAmount ?: 0
                        if (cash > 0) fare - cash else fare
                    }
                    else -> fare
                }
            }

            val officeDeposit = SettlementCalc.calculateOfficeDeposit(fareSum, RATIO)
            val driverShare = SettlementCalc.calculateDriverShare(fareSum, officeDeposit)
            val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, creditSum)

            // 항등식 검증
            assertTrue("기사 $driverId: officeDeposit >= 0", officeDeposit >= 0)
            assertEquals("기사 $driverId: totalFare 분배",
                fareSum, officeDeposit + driverShare)
            assertEquals("기사 $driverId: rawFinalDeposit",
                officeDeposit - creditSum, rawFinalDeposit)

            // 이월금 0 기준 조정납입 검증
            val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, 0)
            if (rawFinalDeposit >= 0) {
                assertEquals("기사 $driverId: 양수 → 그대로", rawFinalDeposit, adjusted)
            } else {
                assertEquals("기사 $driverId: 음수 → 0", 0, adjusted)
            }
        }
    }

    // ===== Test 4: 혼합 결제 시나리오 계산 검증 (Firestore write 불필요) =====

    @Test
    fun mixedPayment_calculationVerify() {
        // 현금 3건 + 이체 2건 가정
        // totalFare = 20000+25000+30000+18000+22000 = 115000
        val totalFare = 115000
        // totalCredit = 0+0+0+18000+22000 = 40000 (이체 전액 외상)
        val totalCredit = 40000

        val officeDeposit = SettlementCalc.calculateOfficeDeposit(totalFare, RATIO)
        assertEquals("officeDeposit = 69000", 69000, officeDeposit)

        val driverShare = SettlementCalc.calculateDriverShare(totalFare, officeDeposit)
        assertEquals("driverShare = 46000", 46000, driverShare)

        val rawFinalDeposit = SettlementCalc.calculateRawFinalDeposit(officeDeposit, totalCredit)
        assertEquals("rawFinalDeposit = 29000", 29000, rawFinalDeposit)

        // 이월 없는 경우
        val adjusted = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, 0)
        assertEquals("adjustedDeposit = 29000", 29000, adjusted)

        // 이월 10000 있는 경우
        val adjustedWithCarryOver = SettlementCalc.calculateAdjustedDeposit(rawFinalDeposit, 10000)
        assertEquals("이월공제 후 adjustedDeposit = 19000", 19000, adjustedWithCarryOver)
    }
}

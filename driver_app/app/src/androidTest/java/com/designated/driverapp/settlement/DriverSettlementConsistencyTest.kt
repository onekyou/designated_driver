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

}

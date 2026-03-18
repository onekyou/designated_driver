package com.designated.driverapp.dispatch

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.designated.driverapp.data.Constants
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * driver_app 기사1 (vipd@naver.com) 콜 수신 + 상태 전이 검증
 * SM-S901N (S22) 기기에서 실행
 *
 * Firestore 보안규칙 준수:
 * - 콜 create: anyone (allow create: if true)
 * - 콜 update: assignedDriverId == auth.uid + 허용된 상태 전이만
 * - 콜 delete: admin만 가능 → 기사 테스트에서는 COMPLETED로 마킹하여 정리
 * - 기사 문서 write: authUid == auth.uid
 *
 * 테스트 시나리오:
 * - 기사 문서 확인 → 수락 → 운행시작 → 운행완료 → 거절 → 전체 플로우
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Driver1CallDispatchTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var provinceId: String
    private lateinit var cityId: String
    private lateinit var officeId: String
    private lateinit var driverDocId: String
    private lateinit var driverAuthUid: String

    companion object {
        private const val TAG = "Driver1DispatchTest"
        private const val DRIVER1_EMAIL = "vipd@naver.com"
        private const val DRIVER1_PASSWORD = "236767"
    }

    @Before
    fun setup() {
        firestore = FirebaseFirestore.getInstance()

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Log.d(TAG, "기사1 로그인 시도: $DRIVER1_EMAIL")
            Tasks.await(auth.signInWithEmailAndPassword(DRIVER1_EMAIL, DRIVER1_PASSWORD))
            assertNotNull("로그인 실패", auth.currentUser)
        }

        driverAuthUid = auth.currentUser!!.uid

        val driverQuery = Tasks.await(
            firestore.collectionGroup("designated_drivers")
                .whereEqualTo("authUid", driverAuthUid)
                .limit(1)
                .get()
        )

        assertTrue("기사 문서를 찾을 수 없음", driverQuery.documents.isNotEmpty())

        val driverDoc = driverQuery.documents.first()
        driverDocId = driverDoc.id
        val path = driverDoc.reference.path.split("/")
        assertTrue("경로 세그먼트 >= 6", path.size >= 6)
        provinceId = path[1]
        cityId = path[3]
        officeId = path[5]

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(Constants.PREF_KEY_PROVINCE_ID, provinceId)
            .putString(Constants.PREF_KEY_CITY_ID, cityId)
            .putString(Constants.PREF_KEY_OFFICE_ID, officeId)
            .apply()

        Log.d(TAG, "Setup 완료: driver=$driverDocId, province=$provinceId, city=$cityId, office=$officeId")
    }

    private fun callsCollection() =
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")

    private fun driverDocRef() =
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers").document(driverDocId)

    /**
     * 테스트 콜 생성 헬퍼 — 지정된 상태로 생성하고 assignedDriverId를 현재 기사로 설정
     * Firestore 보안규칙: calls create는 anyone 허용
     */
    private fun createTestCall(status: String, phone: String): com.google.firebase.firestore.DocumentReference {
        val callData = hashMapOf<String, Any>(
            "phoneNumber" to phone,
            "customerName" to "테스트_$status",
            "status" to status,
            "assignedDriverId" to driverAuthUid,
            "assignedDriverName" to "기사1",
            "timestamp" to FieldValue.serverTimestamp(),
            "detectedTimestamp" to FieldValue.serverTimestamp(),
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "deviceName" to "테스트기기",
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis(),
            "fromCallDetector" to true,
            "isAppCustomer" to false,
            "expireAt" to com.google.firebase.Timestamp(
                java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
            )
        )

        return Tasks.await(callsCollection().add(callData))
    }

    /**
     * 정리: 콜을 COMPLETED로 마킹 (기사는 delete 권한 없음)
     * AWAITING_SETTLEMENT → COMPLETED 전이는 보안규칙에서 허용됨
     */
    private fun markCallCompleted(callRef: com.google.firebase.firestore.DocumentReference) {
        try {
            val doc = Tasks.await(callRef.get())
            val currentStatus = doc.getString("status") ?: ""
            // AWAITING_SETTLEMENT → COMPLETED만 기사에게 허용됨
            if (currentStatus == "AWAITING_SETTLEMENT") {
                Tasks.await(callRef.update(mapOf(
                    "status" to "COMPLETED",
                    "completedTimestamp" to FieldValue.serverTimestamp()
                )))
            }
            // 다른 상태의 콜은 정리하지 않음 (admin이 나중에 정리)
        } catch (e: Exception) {
            Log.w(TAG, "콜 정리 실패 (무시): ${e.message}")
        }
    }

    // ===== Test 1: 기사 문서 존재 + 기본 필드 검증 =====

    @Test
    fun test01_driverDocumentExists() {
        val doc = Tasks.await(driverDocRef().get())
        assertTrue("기사 문서 존재", doc.exists())

        val name = doc.getString("name") ?: ""
        val authUid = doc.getString("authUid") ?: ""
        assertTrue("기사 이름 비어있지 않음", name.isNotBlank())
        assertEquals("authUid 일치", driverAuthUid, authUid)

        Log.d(TAG, "✅ 기사 문서 검증: name=$name, authUid=$authUid, status=${doc.getString("status")}")
    }

    // ===== Test 2: 콜 수락 (ASSIGNED → ACCEPTED, 기사 PREPARING) =====

    @Test
    fun test02_callAcceptTransition() {
        Tasks.await(driverDocRef().update("status", "ASSIGNED"))

        val callRef = createTestCall("ASSIGNED", "010-2222-${System.currentTimeMillis() % 10000}")
        Log.d(TAG, "수락 테스트 콜 생성: ${callRef.id}")

        // 수락 트랜잭션 (DriverViewModel.acceptCall 로직 재현)
        Tasks.await(firestore.runTransaction { transaction ->
            val call = transaction.get(callRef)
            assertEquals("콜 ASSIGNED", "ASSIGNED", call.getString("status"))
            transaction.update(callRef, mapOf(
                "status" to "ACCEPTED",
                "acceptedTimestamp" to FieldValue.serverTimestamp()
            ))
            transaction.update(driverDocRef(), "status", "PREPARING")
        })

        val updatedCall = Tasks.await(callRef.get())
        assertEquals("콜 ACCEPTED", "ACCEPTED", updatedCall.getString("status"))

        val updatedDriver = Tasks.await(driverDocRef().get())
        assertEquals("기사 PREPARING", "PREPARING", updatedDriver.getString("status"))

        Log.d(TAG, "✅ 수락 전이 검증 통과")

        // 정리: 기사 상태 복원
        Tasks.await(driverDocRef().update("status", "WAITING"))
    }

    // ===== Test 3: 운행시작 (ACCEPTED → IN_PROGRESS, 기사 ON_TRIP) =====

    @Test
    fun test03_tripStartTransition() {
        Tasks.await(driverDocRef().update("status", "PREPARING"))

        val callRef = createTestCall("ACCEPTED", "010-3333-${System.currentTimeMillis() % 10000}")
        Log.d(TAG, "운행시작 테스트 콜 생성: ${callRef.id}")

        Tasks.await(firestore.runTransaction { transaction ->
            val call = transaction.get(callRef)
            assertEquals("콜 ACCEPTED", "ACCEPTED", call.getString("status"))
            transaction.update(callRef, mapOf(
                "status" to "IN_PROGRESS",
                "startTimestamp" to FieldValue.serverTimestamp()
            ))
            transaction.update(driverDocRef(), "status", "ON_TRIP")
        })

        val updatedCall = Tasks.await(callRef.get())
        assertEquals("콜 IN_PROGRESS", "IN_PROGRESS", updatedCall.getString("status"))

        val updatedDriver = Tasks.await(driverDocRef().get())
        assertEquals("기사 ON_TRIP", "ON_TRIP", updatedDriver.getString("status"))

        Log.d(TAG, "✅ 운행시작 전이 검증 통과")
        Tasks.await(driverDocRef().update("status", "WAITING"))
    }

    // ===== Test 4: 운행완료 (IN_PROGRESS → AWAITING_SETTLEMENT, 기사 WAITING) =====

    @Test
    fun test04_tripCompleteTransition() {
        Tasks.await(driverDocRef().update("status", "ON_TRIP"))

        val callRef = createTestCall("IN_PROGRESS", "010-4444-${System.currentTimeMillis() % 10000}")
        Log.d(TAG, "운행완료 테스트 콜 생성: ${callRef.id}")

        val fare = 25000
        Tasks.await(firestore.runTransaction { transaction ->
            val call = transaction.get(callRef)
            assertEquals("콜 IN_PROGRESS", "IN_PROGRESS", call.getString("status"))
            transaction.update(callRef, mapOf(
                "status" to "AWAITING_SETTLEMENT",
                "fare" to fare,
                "paymentMethod" to "현금",
                "cashReceived" to fare,
                "completedTimestamp" to FieldValue.serverTimestamp()
            ))
            transaction.update(driverDocRef(), "status", "WAITING")
        })

        val updatedCall = Tasks.await(callRef.get())
        assertEquals("콜 AWAITING_SETTLEMENT", "AWAITING_SETTLEMENT", updatedCall.getString("status"))
        assertEquals("fare 일치", fare.toLong(), updatedCall.getLong("fare"))
        assertEquals("paymentMethod 현금", "현금", updatedCall.getString("paymentMethod"))

        val updatedDriver = Tasks.await(driverDocRef().get())
        assertEquals("기사 WAITING 복귀", "WAITING", updatedDriver.getString("status"))

        Log.d(TAG, "✅ 운행완료 전이 검증 통과")

        // AWAITING_SETTLEMENT → COMPLETED 마킹 (정리)
        markCallCompleted(callRef)
    }

    // ===== Test 5: 기사 거절 (ASSIGNED → WAITING) =====

    @Test
    fun test05_driverRejectCallTransition() {
        Tasks.await(driverDocRef().update("status", "ASSIGNED"))

        val callRef = createTestCall("ASSIGNED", "010-5555-${System.currentTimeMillis() % 10000}")
        Log.d(TAG, "거절 테스트 콜 생성: ${callRef.id}")

        // 거절: ASSIGNED → WAITING (보안규칙에서 허용)
        Tasks.await(firestore.runTransaction { transaction ->
            val call = transaction.get(callRef)
            assertEquals("콜 ASSIGNED", "ASSIGNED", call.getString("status"))
            transaction.update(callRef, mapOf(
                "status" to "WAITING",
                "assignedDriverId" to FieldValue.delete(),
                "assignedDriverName" to FieldValue.delete(),
                "assignedDriverPhone" to FieldValue.delete(),
                "assignedTimestamp" to FieldValue.delete()
            ))
            transaction.update(driverDocRef(), "status", "WAITING")
        })

        val updatedCall = Tasks.await(callRef.get())
        assertEquals("콜 WAITING 복귀", "WAITING", updatedCall.getString("status"))

        val updatedDriver = Tasks.await(driverDocRef().get())
        assertEquals("기사 WAITING 복귀", "WAITING", updatedDriver.getString("status"))

        Log.d(TAG, "✅ 거절 후 복귀 검증 통과")
    }

    // ===== Test 6: 전체 플로우 (ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED) =====

    @Test
    fun test06_fullCallLifecycle() {
        Tasks.await(driverDocRef().update("status", "ASSIGNED"))

        val callRef = createTestCall("ASSIGNED", "010-9876-${System.currentTimeMillis() % 10000}")
        Log.d(TAG, "전체 플로우 테스트 콜 생성: ${callRef.id}")

        val driverDoc = Tasks.await(driverDocRef().get())
        val driverName = driverDoc.getString("name") ?: ""

        // Step 1: 수락 (ASSIGNED → ACCEPTED)
        Tasks.await(firestore.runTransaction { transaction ->
            val call = transaction.get(callRef)
            assertEquals("Step1: 콜 ASSIGNED", "ASSIGNED", call.getString("status"))
            transaction.update(callRef, mapOf(
                "status" to "ACCEPTED",
                "acceptedTimestamp" to FieldValue.serverTimestamp()
            ))
            transaction.update(driverDocRef(), "status", "PREPARING")
        })
        assertEquals("Step1: 콜 ACCEPTED", "ACCEPTED", Tasks.await(callRef.get()).getString("status"))
        assertEquals("Step1: 기사 PREPARING", "PREPARING", Tasks.await(driverDocRef().get()).getString("status"))

        // Step 2: 운행시작 (ACCEPTED → IN_PROGRESS)
        Tasks.await(firestore.runTransaction { transaction ->
            transaction.update(callRef, mapOf(
                "status" to "IN_PROGRESS",
                "startTimestamp" to FieldValue.serverTimestamp()
            ))
            transaction.update(driverDocRef(), "status", "ON_TRIP")
        })
        assertEquals("Step2: 콜 IN_PROGRESS", "IN_PROGRESS", Tasks.await(callRef.get()).getString("status"))
        assertEquals("Step2: 기사 ON_TRIP", "ON_TRIP", Tasks.await(driverDocRef().get()).getString("status"))

        // Step 3: 운행완료 (IN_PROGRESS → AWAITING_SETTLEMENT)
        val fare = 30000
        Tasks.await(firestore.runTransaction { transaction ->
            transaction.update(callRef, mapOf(
                "status" to "AWAITING_SETTLEMENT",
                "fare" to fare,
                "paymentMethod" to "현금",
                "cashReceived" to fare,
                "completedTimestamp" to FieldValue.serverTimestamp()
            ))
            transaction.update(driverDocRef(), "status", "WAITING")
        })
        assertEquals("Step3: 콜 AWAITING_SETTLEMENT", "AWAITING_SETTLEMENT", Tasks.await(callRef.get()).getString("status"))
        assertEquals("Step3: 기사 WAITING", "WAITING", Tasks.await(driverDocRef().get()).getString("status"))

        // Step 4: 정산완료 (AWAITING_SETTLEMENT → COMPLETED)
        Tasks.await(callRef.update(mapOf(
            "status" to "COMPLETED",
            "completedTimestamp" to FieldValue.serverTimestamp()
        )))
        assertEquals("Step4: 콜 COMPLETED", "COMPLETED", Tasks.await(callRef.get()).getString("status"))

        Log.d(TAG, "✅ 전체 플로우 검증 통과: ASSIGNED → ACCEPTED → IN_PROGRESS → AWAITING_SETTLEMENT → COMPLETED")
    }

    // ===== Test 7: 기사 상태 직접 변경 검증 (ONLINE ↔ WAITING) =====

    @Test
    fun test07_driverStatusDirectUpdate() {
        // 기사는 자신의 status를 직접 변경할 수 있어야 함
        Tasks.await(driverDocRef().update("status", "ONLINE"))
        assertEquals("ONLINE", Tasks.await(driverDocRef().get()).getString("status"))

        Tasks.await(driverDocRef().update("status", "WAITING"))
        assertEquals("WAITING", Tasks.await(driverDocRef().get()).getString("status"))

        Log.d(TAG, "✅ 기사 상태 직접 변경 검증 통과")
    }
}

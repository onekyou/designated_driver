package com.designated.customer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * customer_app 앱콜 요청 흐름 검증 (Instrumented Test)
 *
 * 테스트 흐름:
 * 1. 익명인증 → Firestore 콜 생성 (WAITING)
 * 2. 콜 상태 확인 (WAITING)
 * 3. 콜 취소 (CANCELLED_BY_CUSTOMER)
 * 4. 취소된 콜 재조회 확인
 * 5. 테스트 데이터 정리
 *
 * 대상 사무실: gyeonggi/yangpyeong/nEkf0X9g3LZtRX94Mrzu
 */
@RunWith(AndroidJUnit4::class)
class AppCallFlowTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private val provinceId = "gyeonggi"
    private val cityId = "yangpyeong"
    private val officeId = "nEkf0X9g3LZtRX94Mrzu"
    private val testPhoneNumber = "01099990000"  // 테스트 전용 번호

    private val createdCallIds = mutableListOf<String>()

    companion object {
        private const val TAG = "AppCallFlowTest"
    }

    @Before
    fun setup() {
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // 익명인증 (customer_app 테스트모드와 동일)
        if (auth.currentUser == null) {
            Log.d(TAG, "익명인증 시도")
            Tasks.await(auth.signInAnonymously())
            assertNotNull("익명인증 실패", auth.currentUser)
        }
        Log.d(TAG, "인증 완료: uid=${auth.currentUser?.uid}")
    }

    @After
    fun cleanup() {
        // 테스트에서 생성한 콜 삭제
        createdCallIds.forEach { callId ->
            try {
                Tasks.await(
                    getCallsCollection().document(callId).delete()
                )
                Log.d(TAG, "테스트 콜 삭제: $callId")
            } catch (e: Exception) {
                Log.w(TAG, "테스트 콜 삭제 실패: $callId", e)
            }
        }
    }

    private fun getCallsCollection() =
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")

    // ===== Test 1: 앱콜 생성 (WAITING 상태) =====

    @Test
    fun appCall_createWithWaitingStatus() {
        val callRef = getCallsCollection().document()
        val callId = callRef.id
        createdCallIds.add(callId)

        val callData = createTestCallData(callId)

        // 콜 생성
        Tasks.await(callRef.set(callData))
        Log.d(TAG, "콜 생성 완료: $callId")

        // 생성된 콜 조회
        val doc = Tasks.await(callRef.get())
        assertTrue("콜 문서 존재", doc.exists())
        assertEquals("status=WAITING", "WAITING", doc.getString("status"))
        assertEquals("createdFrom=customer_app", "customer_app", doc.getString("createdFrom"))
        assertTrue("isAppCustomer=true", doc.getBoolean("isAppCustomer") == true)
        assertEquals("phoneNumber 일치", testPhoneNumber, doc.getString("phoneNumber"))
        assertEquals("provinceId 일치", provinceId, doc.getString("provinceId"))
        assertEquals("cityId 일치", cityId, doc.getString("cityId"))
        assertEquals("officeId 일치", officeId, doc.getString("officeId"))

        Log.d(TAG, "앱콜 생성 검증 완료")
    }

    // ===== Test 2: 고객 취소 플로우 (WAITING → CANCELLED_BY_CUSTOMER) =====

    @Test
    fun appCall_cancelByCustomer() {
        // 콜 생성
        val callRef = getCallsCollection().document()
        val callId = callRef.id
        createdCallIds.add(callId)

        Tasks.await(callRef.set(createTestCallData(callId)))
        Log.d(TAG, "취소 테스트용 콜 생성: $callId")

        // 콜 상태 확인 (WAITING)
        val beforeDoc = Tasks.await(callRef.get())
        assertEquals("취소 전 status=WAITING", "WAITING", beforeDoc.getString("status"))

        // 고객 취소 (CallService.cancelCall 로직 재현)
        Tasks.await(firestore.runTransaction { transaction ->
            val snapshot = transaction.get(callRef)
            val currentStatus = snapshot.getString("status") ?: ""

            // WAITING/ASSIGNED만 취소 가능
            assertTrue("취소 가능 상태: $currentStatus",
                currentStatus == "WAITING" || currentStatus == "ASSIGNED")

            transaction.update(callRef, "status", "CANCELLED_BY_CUSTOMER")
        })

        // 취소 후 상태 확인
        val afterDoc = Tasks.await(callRef.get())
        assertEquals("취소 후 status=CANCELLED_BY_CUSTOMER",
            "CANCELLED_BY_CUSTOMER", afterDoc.getString("status"))

        Log.d(TAG, "고객 취소 검증 완료")
    }

    // ===== Test 3: 중복 취소 방지 =====

    @Test
    fun appCall_duplicateCancelPrevented() {
        // 콜 생성 + 취소
        val callRef = getCallsCollection().document()
        val callId = callRef.id
        createdCallIds.add(callId)

        Tasks.await(callRef.set(createTestCallData(callId)))
        Tasks.await(callRef.update("status", "CANCELLED_BY_CUSTOMER"))

        // 이미 취소된 콜에 대한 재취소 시도
        var exceptionThrown = false
        try {
            Tasks.await(firestore.runTransaction { transaction ->
                val snapshot = transaction.get(callRef)
                val currentStatus = snapshot.getString("status") ?: ""

                if (currentStatus == "CANCELED" || currentStatus == "CANCELLED_BY_DRIVER" ||
                    currentStatus == "CANCELLED_BY_CUSTOMER") {
                    throw IllegalStateException("ALREADY_CANCELLED")
                }

                transaction.update(callRef, "status", "CANCELLED_BY_CUSTOMER")
            })
        } catch (e: Exception) {
            exceptionThrown = true
            Log.d(TAG, "중복 취소 차단: ${e.message}")
        }

        assertTrue("중복 취소 시 예외 발생", exceptionThrown)

        // 상태 불변 확인
        val doc = Tasks.await(callRef.get())
        assertEquals("상태 불변", "CANCELLED_BY_CUSTOMER", doc.getString("status"))

        Log.d(TAG, "중복 취소 방지 검증 완료")
    }

    // ===== Test 4: 콜 데이터 필드 완전성 (콜매니저 호환) =====

    @Test
    fun appCall_fieldsCompatibleWithCallManager() {
        val callRef = getCallsCollection().document()
        val callId = callRef.id
        createdCallIds.add(callId)

        Tasks.await(callRef.set(createTestCallData(callId)))

        val doc = Tasks.await(callRef.get())
        assertTrue("콜 문서 존재", doc.exists())

        // 콜매니저가 필요로 하는 필수 필드 확인
        assertNotNull("id 필드", doc.getString("id"))
        assertNotNull("phoneNumber 필드", doc.getString("phoneNumber"))
        assertNotNull("status 필드", doc.getString("status"))
        assertNotNull("timestamp 필드", doc.getTimestamp("timestamp"))
        assertNotNull("customerAddress 필드", doc.getString("customerAddress"))
        assertNotNull("departure 필드", doc.getString("departure"))
        assertNotNull("destination 필드", doc.getString("destination"))
        assertNotNull("createdFrom 필드", doc.getString("createdFrom"))
        assertNotNull("customerName 필드", doc.getString("customerName"))
        assertNotNull("customerGrade 필드", doc.getString("customerGrade"))

        // departure_set, destination_set (기사앱 호환)
        assertNotNull("departure_set 필드", doc.getString("departure_set"))
        assertNotNull("destination_set 필드", doc.getString("destination_set"))

        // isAppCustomer 확인
        assertTrue("isAppCustomer=true", doc.getBoolean("isAppCustomer") == true)

        // 포인트 필드
        assertEquals("pointsUsed 기본값=0", 0L, doc.getLong("pointsUsed") ?: -1L)

        Log.d(TAG, "콜매니저 호환 필드 검증 완료")
    }

    // ===== Test 5: 포인트 사용 콜 생성 =====

    @Test
    fun appCall_withPointsUsed() {
        val callRef = getCallsCollection().document()
        val callId = callRef.id
        createdCallIds.add(callId)

        val callData = createTestCallData(callId, pointsUsed = 3000)
        Tasks.await(callRef.set(callData))

        val doc = Tasks.await(callRef.get())
        assertEquals("pointsUsed=3000", 3000L, doc.getLong("pointsUsed"))
        assertEquals("customerGrade", "BRONZE", doc.getString("customerGrade"))

        Log.d(TAG, "포인트 사용 콜 검증 완료")
    }

    // ===== Test 6: 취소 불가 상태 (IN_PROGRESS) =====

    @Test
    fun appCall_cannotCancelInProgress() {
        val callRef = getCallsCollection().document()
        val callId = callRef.id
        createdCallIds.add(callId)

        Tasks.await(callRef.set(createTestCallData(callId)))
        // 상태를 IN_PROGRESS로 변경 (기사가 운행 중인 상황)
        Tasks.await(callRef.update("status", "IN_PROGRESS"))

        // 취소 시도
        var cancelFailed = false
        try {
            Tasks.await(firestore.runTransaction { transaction ->
                val snapshot = transaction.get(callRef)
                val currentStatus = snapshot.getString("status") ?: ""

                if (currentStatus != "WAITING" && currentStatus != "ASSIGNED") {
                    throw IllegalStateException("CANNOT_CANCEL: status=$currentStatus")
                }

                transaction.update(callRef, "status", "CANCELLED_BY_CUSTOMER")
            })
        } catch (e: Exception) {
            cancelFailed = true
            Log.d(TAG, "IN_PROGRESS 취소 차단: ${e.message}")
        }

        assertTrue("IN_PROGRESS 상태 취소 불가", cancelFailed)

        // 상태 불변 확인
        val doc = Tasks.await(callRef.get())
        assertEquals("IN_PROGRESS 유지", "IN_PROGRESS", doc.getString("status"))

        Log.d(TAG, "취소 불가 상태 검증 완료")
    }

    // ===== Helper =====

    private fun createTestCallData(
        callId: String,
        pointsUsed: Int = 0
    ): Map<String, Any?> {
        val now = System.currentTimeMillis()
        return mapOf(
            "id" to callId,
            "phoneNumber" to testPhoneNumber,
            "officeId" to officeId,
            "provinceId" to provinceId,
            "cityId" to cityId,
            "customerAddress" to "테스트 출발지",
            "departure" to "테스트 출발지",
            "destination" to "테스트 도착지",
            "departure_set" to "테스트 출발지",
            "destination_set" to "테스트 도착지",
            "timestamp" to Timestamp(now / 1000, ((now % 1000) * 1000000).toInt()),
            "status" to "WAITING",
            "assignedDriverId" to null,
            "estimatedArrivalTime" to null,
            "fare" to null,
            "notes" to null,
            "createdFrom" to "customer_app",
            "customerId" to testPhoneNumber,
            "customerName" to "테스트고객",
            "customerGrade" to "BRONZE",
            "isAppCustomer" to true,
            "pointsUsed" to pointsUsed,
            "finalFare" to null,
            "discountAmount" to 0
        )
    }
}

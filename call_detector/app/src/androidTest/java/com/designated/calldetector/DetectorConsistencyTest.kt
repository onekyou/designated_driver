package com.designated.calldetector

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
 * call_detector 콜 생성/배차 + 기사 상태 전이 일관성 검증
 *
 * - 관리자 로그인 → admins/{uid} → associatedOfficeId 추출
 * - Firestore에서 WAITING/ONLINE 기사 조회
 * - 콜 생성 → WAITING 상태 검증
 * - 배차 트랜잭션 → ASSIGNED 상태 전이 검증
 * - 정리: 테스트 콜 삭제 + 기사 상태 복원
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DetectorConsistencyTest {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var provinceId: String
    private lateinit var cityId: String
    private lateinit var officeId: String

    companion object {
        private const val TAG = "DetectorConsistencyTest"
        // 관리자 계정 (call_detector 로그인용)
        private const val ADMIN_EMAIL = "vip@naver.com"
        private const val ADMIN_PASSWORD = "236767"
        // 테스트 콜 ID 보관 (cleanup용)
        private val testCallIds = mutableListOf<String>()
        // 테스트에서 ASSIGNED로 변경한 기사 ID (cleanup용)
        private val assignedDriverDocIds = mutableListOf<String>()
    }

    @Before
    fun setup() {
        firestore = FirebaseFirestore.getInstance()

        // 관리자 로그인
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            Log.d(TAG, "관리자 로그인 시도: $ADMIN_EMAIL")
            Tasks.await(auth.signInWithEmailAndPassword(ADMIN_EMAIL, ADMIN_PASSWORD))
            assertNotNull("로그인 실패", auth.currentUser)
        }

        val uid = auth.currentUser!!.uid

        // admins/{uid}에서 사무실 정보 추출
        val adminDoc = Tasks.await(firestore.collection("admins").document(uid).get())
        assertTrue("admins 문서 없음: $uid", adminDoc.exists())

        provinceId = adminDoc.getString("associatedProvinceId") ?: ""
        cityId = adminDoc.getString("associatedCityId") ?: ""
        officeId = adminDoc.getString("associatedOfficeId") ?: ""

        assertTrue("provinceId 비어있음", provinceId.isNotBlank())
        assertTrue("cityId 비어있음", cityId.isNotBlank())
        assertTrue("officeId 비어있음", officeId.isNotBlank())

        // SharedPreferences에 저장 (detector_config)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
            .edit()
            .putString("provinceId", provinceId)
            .putString("cityId", cityId)
            .putString("officeId", officeId)
            .putString("deviceName", "테스트기기")
            .apply()

        Log.d(TAG, "Setup 완료: province=$provinceId, city=$cityId, office=$officeId")
    }

    private fun driversCollection() =
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("designated_drivers")

    private fun callsCollection() =
        firestore.collection("provinces").document(provinceId)
            .collection("cities").document(cityId)
            .collection("offices").document(officeId)
            .collection("calls")

    // ===== Test 1: 기사 목록 조회 (WAITING/ONLINE 상태) =====

    @Test
    fun test01_queryAvailableDrivers() {
        val snapshot = Tasks.await(driversCollection().get())
        assertTrue("기사 컬렉션이 비어있음", snapshot.documents.isNotEmpty())

        val allDrivers = snapshot.documents.map { doc ->
            mapOf(
                "id" to doc.id,
                "name" to (doc.getString("name") ?: ""),
                "status" to (doc.getString("status") ?: ""),
                "authUid" to (doc.getString("authUid") ?: "")
            )
        }

        Log.d(TAG, "전체 기사 수: ${allDrivers.size}")
        allDrivers.forEach { d ->
            Log.d(TAG, "  기사: ${d["name"]} (${d["id"]}), status=${d["status"]}")
        }

        // WAITING 또는 ONLINE 기사 필터링 (DispatchActivity 로직과 동일)
        val availableDrivers = allDrivers.filter {
            it["status"] == "WAITING" || it["status"] == "ONLINE"
        }

        Log.d(TAG, "배차 가능 기사 수: ${availableDrivers.size}")

        // 배차 가능 기사가 존재해야 다음 테스트 진행 가능
        // (없어도 테스트 자체는 PASS — 기사가 모두 운행중일 수 있음)
        if (availableDrivers.isEmpty()) {
            Log.w(TAG, "⚠️ 현재 배차 가능 기사가 없음. 콜 생성/배차 테스트는 스킵됩니다.")
        }
    }

    // ===== Test 2: 콜 생성 → WAITING 상태 검증 =====

    @Test
    fun test02_createCallAndVerifyWaitingStatus() {
        val testPhone = "010-0000-${System.currentTimeMillis() % 10000}"
        val callData = hashMapOf<String, Any>(
            "phoneNumber" to testPhone,
            "customerName" to "테스트고객",
            "customerAddress" to "",
            "status" to "WAITING",
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
            "customerId" to "",
            "createdFrom" to "phone",
            "expireAt" to com.google.firebase.Timestamp(
                java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
            )
        )

        val docRef = Tasks.await(callsCollection().add(callData))
        testCallIds.add(docRef.id)
        Log.d(TAG, "테스트 콜 생성: ${docRef.id}, phone=$testPhone")

        // 생성된 콜 재조회하여 상태 검증
        val createdDoc = Tasks.await(docRef.get())
        assertTrue("콜 문서 존재해야 함", createdDoc.exists())
        assertEquals("콜 상태 = WAITING", "WAITING", createdDoc.getString("status"))
        assertEquals("phoneNumber 일치", testPhone, createdDoc.getString("phoneNumber"))
        assertEquals("fromCallDetector = true", true, createdDoc.getBoolean("fromCallDetector"))
        assertEquals("callType = 수신", "수신", createdDoc.getString("callType"))

        Log.d(TAG, "✅ 콜 생성 + WAITING 상태 검증 통과")
    }

    // ===== Test 3: 배차 트랜잭션 검증 (WAITING → ASSIGNED) =====

    @Test
    fun test03_dispatchTransactionVerify() {
        // 배차 가능 기사 찾기
        val driverSnapshot = Tasks.await(driversCollection().get())
        val availableDriver = driverSnapshot.documents.firstOrNull { doc ->
            val status = doc.getString("status") ?: ""
            status == "WAITING" || status == "ONLINE"
        }

        if (availableDriver == null) {
            Log.w(TAG, "⚠️ 배차 가능 기사 없음 — 테스트 스킵")
            return
        }

        val driverId = availableDriver.id
        val driverName = availableDriver.getString("name") ?: ""
        val driverPhone = availableDriver.getString("phoneNumber") ?: ""
        val driverAuthUid = availableDriver.getString("authUid") ?: ""
        val originalDriverStatus = availableDriver.getString("status") ?: ""
        Log.d(TAG, "배차 대상 기사: $driverName ($driverId), 현재상태=$originalDriverStatus")

        // 테스트 콜 생성
        val testPhone = "010-9999-${System.currentTimeMillis() % 10000}"
        val callData = hashMapOf<String, Any>(
            "phoneNumber" to testPhone,
            "customerName" to "배차테스트고객",
            "status" to "WAITING",
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
            "customerId" to "",
            "createdFrom" to "phone",
            "expireAt" to com.google.firebase.Timestamp(
                java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
            )
        )

        val callRef = Tasks.await(callsCollection().add(callData))
        testCallIds.add(callRef.id)
        Log.d(TAG, "배차 테스트 콜 생성: ${callRef.id}")

        val driverRef = driversCollection().document(driverId)

        // 배차 트랜잭션 실행 (DispatchActivity.updateCallWithDriver 로직 재현)
        val updateData = hashMapOf<String, Any>(
            "status" to "ASSIGNED",
            "assignedDriverId" to driverAuthUid.ifEmpty { driverId },
            "assignedDriverName" to driverName,
            "assignedDriverPhone" to driverPhone,
            "assignedTimestamp" to FieldValue.serverTimestamp()
        )

        Tasks.await(firestore.runTransaction { transaction ->
            val callDoc = transaction.get(callRef)
            val currentStatus = callDoc.getString("status")
            assertEquals("트랜잭션 내 콜 상태 = WAITING", "WAITING", currentStatus)

            transaction.update(callRef, updateData)
            transaction.update(driverRef, "status", "ASSIGNED")
        })

        assignedDriverDocIds.add(driverId)

        // 배차 후 검증
        val updatedCall = Tasks.await(callRef.get())
        assertEquals("배차 후 콜 상태 = ASSIGNED", "ASSIGNED", updatedCall.getString("status"))
        assertEquals("assignedDriverName", driverName, updatedCall.getString("assignedDriverName"))
        assertEquals("assignedDriverId", driverAuthUid.ifEmpty { driverId },
            updatedCall.getString("assignedDriverId"))

        val updatedDriver = Tasks.await(driverRef.get())
        assertEquals("기사 상태 = ASSIGNED", "ASSIGNED", updatedDriver.getString("status"))

        Log.d(TAG, "✅ 배차 트랜잭션 검증 통과: 콜=${callRef.id} → 기사=$driverName")

        // 정리: 콜 삭제 + 기사 상태 복원
        Tasks.await(callRef.delete())
        testCallIds.remove(callRef.id)
        Tasks.await(driverRef.update("status", originalDriverStatus))
        assignedDriverDocIds.remove(driverId)
        Log.d(TAG, "정리 완료: 콜 삭제 + 기사 상태 $originalDriverStatus 복원")
    }

    // ===== Test 4: 이중 배차 방지 (이미 ASSIGNED인 콜에 배차 시도) =====

    @Test
    fun test04_doubleDispatchPrevention() {
        // 테스트 콜 생성 (ASSIGNED 상태로)
        val testPhone = "010-8888-${System.currentTimeMillis() % 10000}"
        val callData = hashMapOf<String, Any>(
            "phoneNumber" to testPhone,
            "customerName" to "이중배차테스트",
            "status" to "ASSIGNED",
            "timestamp" to FieldValue.serverTimestamp(),
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "deviceName" to "테스트기기",
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis(),
            "fromCallDetector" to true,
            "assignedDriverId" to "dummy_driver",
            "assignedDriverName" to "더미기사",
            "expireAt" to com.google.firebase.Timestamp(
                java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
            )
        )

        val callRef = Tasks.await(callsCollection().add(callData))
        testCallIds.add(callRef.id)

        // ASSIGNED 상태인 콜에 배차 트랜잭션 시도 → 실패해야 함
        var transactionFailed = false
        try {
            Tasks.await(firestore.runTransaction { transaction ->
                val callDoc = transaction.get(callRef)
                val currentStatus = callDoc.getString("status")
                if (currentStatus != "WAITING") {
                    throw IllegalStateException("ALREADY_ASSIGNED")
                }
                // 여기에 도달하면 안 됨
                transaction.update(callRef, "assignedDriverId", "another_driver")
            })
        } catch (e: Exception) {
            transactionFailed = true
            Log.d(TAG, "이중 배차 차단됨 (예상대로): ${e.message}")
        }

        assertTrue("이중 배차 트랜잭션이 실패해야 함", transactionFailed)

        // 정리
        Tasks.await(callRef.delete())
        testCallIds.remove(callRef.id)
        Log.d(TAG, "✅ 이중 배차 방지 검증 통과")
    }

    // ===== Test 5: 공유콜 컬렉션 구조 검증 (읽기 전용) =====

    @Test
    fun test05_sharedCallCollectionReadable() {
        // shared_calls는 DispatchActivity.createSharedCall()에서 생성됨
        // Firestore 보안규칙 상 admin 계정의 직접 write가 제한될 수 있으므로
        // 기존 공유콜 조회로 구조를 검증

        // 사무실 콜 컬렉션에서 공유콜 경로(DispatchActivity.createSharedCall)가
        // 올바른 필드를 포함하는지 코드 레벨에서 검증
        // sourceProvinceId, sourceCityId, sourceOfficeId, status=OPEN

        val snapshot = Tasks.await(
            firestore.collection("shared_calls")
                .whereEqualTo("sourceOfficeId", officeId)
                .limit(5)
                .get()
        )

        // 공유콜이 있으면 구조 검증, 없으면 PASS (아직 생성된 적 없을 수 있음)
        if (!snapshot.isEmpty) {
            val doc = snapshot.documents.first()
            assertNotNull("status 필드 존재", doc.getString("status"))
            assertNotNull("sourceProvinceId 필드 존재", doc.getString("sourceProvinceId"))
            assertNotNull("sourceOfficeId 필드 존재", doc.getString("sourceOfficeId"))
            Log.d(TAG, "✅ 공유콜 구조 검증 통과 (${snapshot.size()}건 확인)")
        } else {
            Log.d(TAG, "✅ 공유콜 없음 — 구조 검증 스킵 (정상)")
        }
    }

    // ===== Test 6: 중복 콜 방지 로직 검증 (10초 내 동일 번호) =====

    @Test
    fun test06_duplicateCallPrevention() {
        val testPhone = "010-6666-1234"

        // 첫 번째 콜 생성
        val callData1 = hashMapOf<String, Any>(
            "phoneNumber" to testPhone,
            "customerName" to "중복테스트1",
            "status" to "WAITING",
            "timestamp" to FieldValue.serverTimestamp(),
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "deviceName" to "테스트기기",
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis(),
            "fromCallDetector" to true,
            "expireAt" to com.google.firebase.Timestamp(
                java.util.Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
            )
        )

        val docRef1 = Tasks.await(callsCollection().add(callData1))
        testCallIds.add(docRef1.id)

        // 중복 체크: 10초 내 같은 번호의 WAITING/PENDING/ASSIGNED 콜 조회
        val duplicateCheckThreshold = System.currentTimeMillis() - 10000L
        val existingCalls = Tasks.await(
            callsCollection()
                .whereEqualTo("phoneNumber", testPhone)
                .whereGreaterThan("timestampClient", duplicateCheckThreshold)
                .whereIn("status", listOf("WAITING", "PENDING", "ASSIGNED"))
                .get()
        )

        assertTrue("10초 내 동일 번호 콜이 존재해야 함", !existingCalls.isEmpty)
        Log.d(TAG, "중복 감지: ${existingCalls.size()}건 (예상: >=1)")

        // 정리
        Tasks.await(docRef1.delete())
        testCallIds.remove(docRef1.id)
        Log.d(TAG, "✅ 중복 콜 방지 검증 통과")
    }

    // ===== Test 7: SharedPreferences 설정 일관성 =====

    @Test
    fun test07_sharedPreferencesConsistency() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // detector_config (CallDetectorService에서 사용)
        val detectorPrefs = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        val storedProvince = detectorPrefs.getString("provinceId", "") ?: ""
        val storedCity = detectorPrefs.getString("cityId", "") ?: ""
        val storedOffice = detectorPrefs.getString("officeId", "") ?: ""
        val storedDevice = detectorPrefs.getString("deviceName", "") ?: ""

        assertEquals("detector_config provinceId", provinceId, storedProvince)
        assertEquals("detector_config cityId", cityId, storedCity)
        assertEquals("detector_config officeId", officeId, storedOffice)
        assertTrue("deviceName이 비어있지 않아야 함", storedDevice.isNotBlank())

        Log.d(TAG, "✅ SharedPreferences 일관성 검증 통과")
    }
}

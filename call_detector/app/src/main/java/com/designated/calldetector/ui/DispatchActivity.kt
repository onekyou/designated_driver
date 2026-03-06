package com.designated.calldetector.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase

class DispatchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callId = intent.getStringExtra("EXTRA_CALL_ID") // Firebase document ID
        val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER") ?: ""
        val contactName = intent.getStringExtra("EXTRA_CONTACT_NAME")
        val contactAddress = intent.getStringExtra("EXTRA_CONTACT_ADDRESS")
        val provinceId = intent.getStringExtra("EXTRA_PROVINCE_ID") ?: ""
        val cityId = intent.getStringExtra("EXTRA_CITY_ID") ?: ""
        val officeId = intent.getStringExtra("EXTRA_OFFICE_ID") ?: ""
        val deviceName = intent.getStringExtra("EXTRA_DEVICE_NAME") ?: ""

        if (phoneNumber.isEmpty()) {
            finish()
            return
        }

        setContent {
            CallDetectorAppTheme {
                var drivers by remember { mutableStateOf<List<DriverInfo>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }

                // Firestore 실시간 리스너로 기사 상태 구독
                DisposableEffect(provinceId, cityId, officeId) {
                    val db = FirebaseFirestore.getInstance()
                    val driversPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers"

                    // 모든 기사를 가져와서 클라이언트에서 필터링 (실시간 업데이트)
                    val listenerRegistration: ListenerRegistration = db.collection(driversPath)
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                Log.e("DispatchActivity", "기사 목록 리스너 에러", e)
                                isLoading = false
                                return@addSnapshotListener
                            }

                            val allDrivers = mutableListOf<DriverInfo>()
                            snapshot?.documents?.forEach { doc ->
                                val name = doc.getString("name")
                                val status = doc.getString("status") ?: ""
                                // WAITING 또는 ONLINE 상태만 필터링
                                if (name != null && (status == "WAITING" || status == "ONLINE")) {
                                    allDrivers.add(
                                        DriverInfo(
                                            id = doc.id,
                                            name = name,
                                            status = status,
                                            phone = doc.getString("phoneNumber") ?: "",
                                            authUid = doc.getString("authUid") ?: ""
                                        )
                                    )
                                }
                            }

                            drivers = allDrivers
                            isLoading = false
                            Log.d("DispatchActivity", "기사 목록 업데이트: ${allDrivers.size}명")
                        }

                    onDispose {
                        listenerRegistration.remove()
                        Log.d("DispatchActivity", "기사 목록 리스너 해제")
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    DispatchDialog(
                        callInfo = CallInfo(
                            phoneNumber = phoneNumber,
                            customerName = contactName,
                            customerAddress = contactAddress
                        ),
                        availableDrivers = drivers,
                        onDriverSelect = { driver ->
                            if (callId != null) {
                                // Firebase ID가 있으면 기존 문서 업데이트
                                updateCallWithDriver(callId, driver, provinceId, cityId, officeId)
                            } else {
                                // ID가 없으면 새 문서 생성 (폴백)
                                createCallWithDriver(phoneNumber, contactName, contactAddress, driver, provinceId, cityId, officeId, deviceName)
                            }
                            finish()
                        },
                        onHold = {
                            // 나중에는 이미 WAITING 상태로 저장되어 있으므로 별도 처리 불필요
                            finish()
                        },
                        onDelete = {
                            if (callId != null) {
                                // Firebase ID가 있으면 해당 문서 삭제
                                deleteCall(callId, provinceId, cityId, officeId)
                            }
                            finish()
                        },
                        onShare = {
                            if (callId != null) {
                                // 기존 콜 삭제 후 공유콜 생성
                                deleteCall(callId, provinceId, cityId, officeId)
                            }
                            createSharedCall(phoneNumber, contactName, contactAddress, provinceId, cityId, officeId, deviceName)
                            finish()
                        },
                        onDismiss = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * Firebase ID를 사용해 기존 콜 문서를 업데이트 (중복 방지)
     */
    private fun updateCallWithDriver(
        callId: String,
        driver: DriverInfo,
        provinceId: String,
        cityId: String,
        officeId: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val callRef = db.document("provinces/$provinceId/cities/$cityId/offices/$officeId/calls/$callId")
        val driverRef = db.collection("provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers").document(driver.id)

        val updateData = hashMapOf<String, Any>(
            "status" to "ASSIGNED",
            "assignedDriverId" to driver.authUid.ifEmpty { driver.id },
            "assignedDriverName" to driver.name,
            "assignedDriverPhone" to driver.phone,
            "assignedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        // 트랜잭션으로 status==WAITING 확인 후 배차 (이중 배차 방지)
        db.runTransaction { transaction ->
            val callDoc = transaction.get(callRef)
            val currentStatus = callDoc.getString("status")
            if (currentStatus != "WAITING") {
                throw IllegalStateException("ALREADY_ASSIGNED")
            }
            transaction.update(callRef, updateData)
            transaction.update(driverRef, "status", "ASSIGNED")
        }.addOnSuccessListener {
            Log.d("DispatchActivity", "Call updated with driver: ${driver.name}")

            // 트랜잭션 성공 시에만 FCM 알림 전송
            val driverAuthUid = driver.authUid
            if (driverAuthUid.isNotBlank()) {
                val functions = Firebase.functions("asia-northeast3")
                val data = hashMapOf(
                    "callId" to callId,
                    "driverAuthUid" to driverAuthUid,
                    "provinceId" to provinceId,
                    "cityId" to cityId,
                    "officeId" to officeId,
                    "customerName" to "",
                    "departure" to ""
                )
                Log.d("DispatchActivity", "Cloud Function 호출: $data")
                functions.getHttpsCallable("notifyDriverAssignment")
                    .call(data)
                    .addOnSuccessListener { result ->
                        Log.d("DispatchActivity", "기사 알림 전송 성공: ${result.data}")
                    }
                    .addOnFailureListener { fcmError ->
                        Log.e("DispatchActivity", "기사 알림 전송 실패: ${fcmError.message}", fcmError)
                    }
            } else {
                Log.w("DispatchActivity", "기사 authUid가 없어 FCM 알림 전송 불가")
            }
        }.addOnFailureListener { e ->
            Log.e("DispatchActivity", "Failed to update call", e)
            if (e.message?.contains("ALREADY_ASSIGNED") == true) {
                Toast.makeText(applicationContext, "이미 다른 기사에게 배차된 콜입니다", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(applicationContext, "배차 실패 - 네트워크를 확인해주세요", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 폴백용: Firebase ID가 없을 때 새 콜 생성 (기존 방식)
     */
    private fun createCallWithDriver(
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        driver: DriverInfo,
        provinceId: String,
        cityId: String,
        officeId: String,
        deviceName: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val callPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/calls"

        val callData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "customerName" to (contactName ?: phoneNumber),
            "detectedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "provinceId" to provinceId,
            "cityId" to cityId,
            "officeId" to officeId,
            "deviceName" to deviceName,
            "status" to "ASSIGNED",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis(),
            "assignedDriverId" to driver.authUid.ifEmpty { driver.id },
            "assignedDriverName" to driver.name,
            "assignedDriverPhone" to driver.phone,
            "assignedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        contactAddress?.let { callData["customerAddress"] = it }

        // 콜 문서 생성
        db.collection(callPath).add(callData)
            .addOnSuccessListener { docRef ->
                Log.d("DispatchActivity", "새 콜 생성: ${docRef.id}")

                // FCM 알림 전송 (Cloud Function 호출)
                val driverAuthUid = driver.authUid
                if (driverAuthUid.isNotBlank()) {
                    val functions = Firebase.functions("asia-northeast3")
                    val data = hashMapOf(
                        "callId" to docRef.id,
                        "driverAuthUid" to driverAuthUid,
                        "provinceId" to provinceId,
                        "cityId" to cityId,
                        "officeId" to officeId,
                        "customerName" to (contactName ?: ""),
                        "departure" to ""
                    )
                    Log.d("DispatchActivity", "Cloud Function 호출: $data")
                    functions.getHttpsCallable("notifyDriverAssignment")
                        .call(data)
                        .addOnSuccessListener { result ->
                            Log.d("DispatchActivity", "✅ 기사 알림 전송 성공: ${result.data}")
                        }
                        .addOnFailureListener { e ->
                            Log.e("DispatchActivity", "❌ 기사 알림 전송 실패: ${e.message}", e)
                        }
                } else {
                    Log.w("DispatchActivity", "⚠️ 기사 authUid가 없어 FCM 알림 전송 불가")
                }
            }
            .addOnFailureListener { e ->
                Log.e("DispatchActivity", "콜 생성 실패", e)
            }

        // 기사 상태를 ASSIGNED로 변경 (Manager와 동일하게 통일)
        val driverPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers"
        db.collection(driverPath).document(driver.id)
            .update("status", "ASSIGNED")
    }

    // createCallOnHold 함수 삭제됨 - 이미 CallDetectorService에서 WAITING 상태로 콜이 생성되므로 중복 생성 방지

    /**
     * 콜 문서 삭제
     */
    private fun deleteCall(
        callId: String,
        provinceId: String,
        cityId: String,
        officeId: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val callPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/calls/$callId"

        db.document(callPath)
            .delete()
            .addOnSuccessListener {
                android.util.Log.d("DispatchActivity", "Call deleted: $callId")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DispatchActivity", "Failed to delete call", e)
            }
    }

    private fun createSharedCall(
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        provinceId: String,
        cityId: String,
        officeId: String,
        deviceName: String
    ) {
        val db = FirebaseFirestore.getInstance()
        // 공유콜은 루트 레벨의 shared_calls 컬렉션에 저장
        val sharedCallsPath = "shared_calls"

        val sharedCallData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "customerName" to (contactName ?: phoneNumber),
            "sharedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "status" to "SHARED",
            "sourceProvinceId" to provinceId,
            "sourceCityId" to cityId,
            "sourceOfficeId" to officeId,
            "deviceName" to deviceName,
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis()
        )

        contactAddress?.let { sharedCallData["customerAddress"] = it }

        db.collection(sharedCallsPath).add(sharedCallData)
    }

    companion object {
        fun startDispatchDialog(
            context: Context,
            phoneNumber: String,
            contactName: String?,
            contactAddress: String?,
            provinceId: String,
            cityId: String,
            officeId: String,
            deviceName: String
        ) {
            val intent = Intent(context, DispatchActivity::class.java).apply {
                putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                putExtra("EXTRA_CONTACT_NAME", contactName)
                putExtra("EXTRA_CONTACT_ADDRESS", contactAddress)
                putExtra("EXTRA_PROVINCE_ID", provinceId)
                putExtra("EXTRA_CITY_ID", cityId)
                putExtra("EXTRA_OFFICE_ID", officeId)
                putExtra("EXTRA_DEVICE_NAME", deviceName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun CallDetectorAppTheme(content: @Composable () -> Unit) {
    val darkColorScheme = androidx.compose.material3.darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFFFB000),
        onPrimary = androidx.compose.ui.graphics.Color.Black,
        primaryContainer = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
        onPrimaryContainer = androidx.compose.ui.graphics.Color.White,
        secondary = androidx.compose.ui.graphics.Color(0xFF03DAC6),
        onSecondary = androidx.compose.ui.graphics.Color.Black,
        background = androidx.compose.ui.graphics.Color(0xFF121212),
        onBackground = androidx.compose.ui.graphics.Color.White,
        surface = androidx.compose.ui.graphics.Color(0xFF1E1E1E),
        onSurface = androidx.compose.ui.graphics.Color.White,
        surfaceVariant = androidx.compose.ui.graphics.Color(0xFF2A2A2A),
        onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFB0B0B0),
        outline = androidx.compose.ui.graphics.Color(0xFF404040),
        error = androidx.compose.ui.graphics.Color(0xFFCF6679),
        onError = androidx.compose.ui.graphics.Color.Black
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

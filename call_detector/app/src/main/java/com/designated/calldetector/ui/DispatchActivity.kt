package com.designated.calldetector.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

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

                LaunchedEffect(Unit) {
                    drivers = loadAvailableDrivers(provinceId, cityId, officeId)
                    isLoading = false
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

    private suspend fun loadAvailableDrivers(provinceId: String, cityId: String, officeId: String): List<DriverInfo> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val driversPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers"

            // WAITING 또는 ONLINE 상태의 기사들을 모두 가져오기
            val waitingSnapshot = db.collection(driversPath)
                .whereEqualTo("status", "WAITING")
                .get()
                .await()

            val onlineSnapshot = db.collection(driversPath)
                .whereEqualTo("status", "ONLINE")
                .get()
                .await()

            val allDrivers = mutableListOf<DriverInfo>()

            // WAITING 상태 기사들 추가
            waitingSnapshot.documents.forEach { doc ->
                val name = doc.getString("name")
                if (name != null) {
                    allDrivers.add(
                        DriverInfo(
                            id = doc.id,
                            name = name,
                            status = doc.getString("status") ?: "WAITING",
                            phone = doc.getString("phoneNumber") ?: ""
                        )
                    )
                }
            }

            // ONLINE 상태 기사들 추가
            onlineSnapshot.documents.forEach { doc ->
                val name = doc.getString("name")
                if (name != null) {
                    allDrivers.add(
                        DriverInfo(
                            id = doc.id,
                            name = name,
                            status = doc.getString("status") ?: "ONLINE",
                            phone = doc.getString("phoneNumber") ?: ""
                        )
                    )
                }
            }

            allDrivers
        } catch (e: Exception) {
            emptyList()
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
        val callPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/calls/$callId"

        // 기존 콜 문서 업데이트
        val updateData = hashMapOf<String, Any>(
            "status" to "ASSIGNED",
            "assignedDriverId" to driver.id,
            "assignedDriverName" to driver.name,
            "assignedDriverPhone" to driver.phone,
            "assignedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.document(callPath)
            .update(updateData)
            .addOnSuccessListener {
                android.util.Log.d("DispatchActivity", "Call updated with driver: ${driver.name}")
            }
            .addOnFailureListener { e ->
                android.util.Log.e("DispatchActivity", "Failed to update call", e)
            }

        // 기사 상태를 ON_TRIP으로 변경
        val driverPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers"
        db.collection(driverPath).document(driver.id)
            .update("status", "ON_TRIP")
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
            "assignedDriverId" to driver.id,
            "assignedDriverName" to driver.name,
            "assignedDriverPhone" to driver.phone,
            "assignedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        contactAddress?.let { callData["customerAddress"] = it }

        // 콜 문서 생성
        db.collection(callPath).add(callData)

        // 기사 상태를 ON_TRIP으로 변경
        val driverPath = "provinces/$provinceId/cities/$cityId/offices/$officeId/designated_drivers"
        db.collection(driverPath).document(driver.id)
            .update("status", "ON_TRIP")
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

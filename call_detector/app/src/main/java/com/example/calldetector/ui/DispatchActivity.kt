package com.example.calldetector.ui

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
        
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] DispatchActivity onCreate 시작!")
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] Intent: $intent")
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] Intent extras: ${intent.extras}")
        
        val phoneNumber = intent.getStringExtra("EXTRA_PHONE_NUMBER") ?: ""
        val contactName = intent.getStringExtra("EXTRA_CONTACT_NAME")
        val contactAddress = intent.getStringExtra("EXTRA_CONTACT_ADDRESS")
        val regionId = intent.getStringExtra("EXTRA_REGION_ID") ?: ""
        val officeId = intent.getStringExtra("EXTRA_OFFICE_ID") ?: ""
        val deviceName = intent.getStringExtra("EXTRA_DEVICE_NAME") ?: ""
        
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] 추출된 데이터:")
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] 전화번호: '$phoneNumber'")
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] 연락처명: '$contactName'")
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] 주소: '$contactAddress'")
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] 지역ID: '$regionId', 사무실ID: '$officeId'")
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] 디바이스: '$deviceName'")
        
        if (phoneNumber.isEmpty()) {
            android.util.Log.e("DispatchActivity", "❌ [DEBUG] phoneNumber가 비어있음! Activity 종료")
            finish()
            return
        }
        
        android.util.Log.d("DispatchActivity", "🚀 [DEBUG] setContent 시작!")
        
        setContent {
            android.util.Log.d("DispatchActivity", "🔍 [DEBUG] setContent 내부 - Compose UI 시작")
            CallDetectorAppTheme {
                android.util.Log.d("DispatchActivity", "🔍 [DEBUG] CallDetectorAppTheme 내부")
                var drivers by remember { mutableStateOf<List<DriverInfo>>(emptyList()) }
                var isLoading by remember { mutableStateOf(true) }
                
                LaunchedEffect(Unit) {
                    android.util.Log.d("DispatchActivity", "🔍 [DEBUG] LaunchedEffect 시작 - 기사 로딩")
                    drivers = loadAvailableDrivers(regionId, officeId)
                    android.util.Log.d("DispatchActivity", "🔍 [DEBUG] 기사 로딩 완료 - ${drivers.size}명")
                    isLoading = false
                }
                
                if (isLoading) {
                    android.util.Log.d("DispatchActivity", "🔄 [DEBUG] 로딩 중 - CircularProgressIndicator 표시")
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    android.util.Log.d("DispatchActivity", "🎯 [DEBUG] DispatchDialog 생성!")
                    DispatchDialog(
                        callInfo = CallInfo(
                            phoneNumber = phoneNumber,
                            customerName = contactName,
                            customerAddress = contactAddress
                        ),
                        availableDrivers = drivers,
                        onDriverSelect = { driver ->
                            createCallWithDriver(phoneNumber, contactName, contactAddress, driver, regionId, officeId, deviceName)
                            finish()
                        },
                        onHold = {
                            createCallOnHold(phoneNumber, contactName, contactAddress, regionId, officeId, deviceName)
                            finish()
                        },
                        onDelete = {
                            // 삭제 - Firestore 업로드 안함
                            finish()
                        },
                        onShare = {
                            createSharedCall(phoneNumber, contactName, contactAddress, regionId, officeId, deviceName)
                            finish()
                        },
                        onDismiss = {
                            finish()
                        }
                    )
                }
            }
        }
        
        android.util.Log.d("DispatchActivity", "✅ [DEBUG] onCreate 완료!")
    }
    
    override fun onStart() {
        super.onStart()
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] onStart 호출됨")
    }
    
    override fun onResume() {
        super.onResume()
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] onResume 호출됨 - 화면에 표시됨!")
    }
    
    override fun onPause() {
        super.onPause()
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] onPause 호출됨")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("DispatchActivity", "🔍 [DEBUG] onDestroy 호출됨 - Activity 종료")
    }
    
    private suspend fun loadAvailableDrivers(regionId: String, officeId: String): List<DriverInfo> {
        return try {
            val db = FirebaseFirestore.getInstance()
            val driversPath = "regions/$regionId/offices/$officeId/designated_drivers"
            val snapshot = db.collection(driversPath)
                .whereEqualTo("status", "ONLINE")
                .get()
                .await()
            
            snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("name") ?: return@mapNotNull null
                val status = doc.getString("status") ?: "UNKNOWN"
                val phone = doc.getString("phone") ?: ""
                
                DriverInfo(
                    id = doc.id,
                    name = name,
                    status = status,
                    phone = phone
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun createCallWithDriver(
        phoneNumber: String, 
        contactName: String?, 
        contactAddress: String?,
        driver: DriverInfo, 
        regionId: String, 
        officeId: String,
        deviceName: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val callPath = "regions/$regionId/offices/$officeId/calls"
        
        val callData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "customerName" to (contactName ?: phoneNumber),
            "detectedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "regionId" to regionId,
            "officeId" to officeId,
            "deviceName" to deviceName,
            "status" to "ASSIGNED",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis(),
            "assignedDriverId" to driver.id,
            "assignedDriverName" to driver.name,
            "assignedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        
        contactAddress?.let { callData["customerAddress"] = it }
        
        // 콜 문서 생성
        db.collection(callPath).add(callData)
        
        // 기사 상태를 ON_TRIP으로 변경
        val driverPath = "regions/$regionId/offices/$officeId/designated_drivers"
        db.collection(driverPath).document(driver.id)
            .update("status", "ON_TRIP")
    }
    
    private fun createCallOnHold(
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        regionId: String,
        officeId: String,
        deviceName: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val callPath = "regions/$regionId/offices/$officeId/calls"
        
        val callData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "customerName" to (contactName ?: phoneNumber),
            "detectedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "regionId" to regionId,
            "officeId" to officeId,
            "deviceName" to deviceName,
            "status" to "WAITING",
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "callType" to "수신",
            "timestampClient" to System.currentTimeMillis()
        )
        
        contactAddress?.let { callData["customerAddress"] = it }
        
        db.collection(callPath).add(callData)
    }
    
    private fun createSharedCall(
        phoneNumber: String,
        contactName: String?,
        contactAddress: String?,
        regionId: String,
        officeId: String,
        deviceName: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val sharedCallsPath = "regions/$regionId/offices/$officeId/shared_calls"
        
        val sharedCallData = hashMapOf<String, Any>(
            "phoneNumber" to phoneNumber,
            "customerName" to (contactName ?: phoneNumber),
            "sharedTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "status" to "SHARED",
            "regionId" to regionId,
            "officeId" to officeId,
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
            regionId: String,
            officeId: String,
            deviceName: String
        ) {
            val intent = Intent(context, DispatchActivity::class.java).apply {
                putExtra("EXTRA_PHONE_NUMBER", phoneNumber)
                putExtra("EXTRA_CONTACT_NAME", contactName)
                putExtra("EXTRA_CONTACT_ADDRESS", contactAddress)
                putExtra("EXTRA_REGION_ID", regionId)
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
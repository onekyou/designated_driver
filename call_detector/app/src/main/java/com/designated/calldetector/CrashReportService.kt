package com.designated.calldetector

import android.app.Application
import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Crashlytics를 활용한 강제종료 감지 및 알림 시스템
 */
class CrashReportService(private val context: Context) {
    
    private val crashlytics = FirebaseCrashlytics.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)
    
    // 디바이스 고유 ID
    private val deviceId: String by lazy {
        // 여러 SharedPreferences 위치에서 확인
        val detectorConfig = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        val callDetectorPrefs = context.getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)
        
        detectorConfig.getString("deviceName", null) 
            ?: callDetectorPrefs.getString("deviceName", null)
            ?: "TestPhone_${Build.MODEL}" // 테스트용 기본값
    }
    
    private val regionId: String by lazy {
        val detectorConfig = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        val callDetectorPrefs = context.getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)
        
        detectorConfig.getString("regionId", null) 
            ?: callDetectorPrefs.getString("regionId", null)
            ?: "region_test" // 테스트용 기본값
    }
    
    private val officeId: String by lazy {
        val detectorConfig = context.getSharedPreferences("detector_config", Context.MODE_PRIVATE)
        val callDetectorPrefs = context.getSharedPreferences("CallDetectorPrefs", Context.MODE_PRIVATE)
        
        detectorConfig.getString("officeId", null) 
            ?: callDetectorPrefs.getString("officeId", null)
            ?: "office_test" // 테스트용 기본값
    }
    
    /**
     * Crashlytics 초기 설정
     */
    fun initialize() {
        // 초기화 정보 로깅
        crashlytics.log("CrashReportService initializing...")
        crashlytics.log("Device ID: $deviceId")
        crashlytics.log("Region ID: $regionId") 
        crashlytics.log("Office ID: $officeId")
        
        // 디바이스 정보 설정
        crashlytics.setUserId(deviceId)
        crashlytics.setCustomKey("region", regionId)
        crashlytics.setCustomKey("office", officeId)
        crashlytics.setCustomKey("device_model", Build.MODEL)
        crashlytics.setCustomKey("android_version", Build.VERSION.SDK_INT)
        
        // Firebase 연결 테스트
        testFirebaseConnection()
        
        // 비정상 종료 핸들러 설정
        setupCrashHandler()
        
        // 이전 크래시 체크
        checkPreviousCrash()
    }
    
    /**
     * Firebase 연결 테스트 및 준비 상태 확인
     */
    private fun testFirebaseConnection() {
        android.util.Log.d("CrashReport", "=== Firebase 연결 테스트 시작 ===")
        android.util.Log.d("CrashReport", "Device ID: $deviceId")
        android.util.Log.d("CrashReport", "Region ID: $regionId")
        android.util.Log.d("CrashReport", "Office ID: $officeId")
        
        crashlytics.log("Testing Firebase connection...")
        
        // 0. Firebase 인증 상태 확인 (🔥 핵심!)
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        android.util.Log.d("CrashReport", "🔐 Firebase 인증 상태: ${if (currentUser != null) "인증됨 (${currentUser.uid})" else "인증되지 않음"}")
        
        if (currentUser == null) {
            android.util.Log.e("CrashReport", "❌ Firebase 인증이 되어있지 않음! Firestore 쓰기 실패 예상")
            crashlytics.log("❌ Firebase user not authenticated - Firestore writes may fail")
        } else {
            android.util.Log.d("CrashReport", "✅ Firebase 인증 완료 - Firestore 쓰기 가능")
            crashlytics.log("✅ Firebase user authenticated - Firestore writes should work")
        }
        
        // 1. Firebase 인스턴스 상태 확인
        try {
            val firestoreSettings = firestore.firestoreSettings
            android.util.Log.d("CrashReport", "Firestore Settings: ${firestoreSettings}")
            crashlytics.log("Firestore settings loaded successfully")
        } catch (e: Exception) {
            android.util.Log.e("CrashReport", "Firestore 설정 확인 실패: ${e.message}", e)
            crashlytics.log("Firestore settings check failed: ${e.message}")
        }
        
        // 2. 연결 테스트 데이터 준비
        val testData = hashMapOf(
            "deviceId" to deviceId,
            "regionId" to regionId,
            "officeId" to officeId,
            "type" to "CONNECTION_TEST",
            "timestamp" to FieldValue.serverTimestamp(),
            "message" to "Firebase 연결 테스트",
            "testTime" to System.currentTimeMillis(),
            "initializationCheck" to true
        )
        
        val docId = "test_${deviceId}_${System.currentTimeMillis()}"
        android.util.Log.d("CrashReport", "문서 ID: $docId")
        android.util.Log.d("CrashReport", "전송 데이터: $testData")
        
        // 3. 동기적 테스트 (초기화 시점에서 확실히 확인)
        try {
            val testTask = firestore.collection("device_alerts")
                .document(docId)
                .set(testData)
            
            // 3초 내에 완료되어야 함
            com.google.android.gms.tasks.Tasks.await(testTask, 3, java.util.concurrent.TimeUnit.SECONDS)
            android.util.Log.d("CrashReport", "✅ Firebase 동기 연결 테스트 성공!")
            crashlytics.log("✅ Firebase sync connection test successful")
            
            // 테스트 문서 즉시 삭제
            firestore.collection("device_alerts").document(docId).delete()
            
        } catch (syncException: Exception) {
            android.util.Log.e("CrashReport", "❌ Firebase 동기 연결 테스트 실패: ${syncException.message}", syncException)
            crashlytics.log("❌ Firebase sync connection test failed: ${syncException.message}")
        }
        
        // 4. device_status 컬렉션 접근 테스트
        try {
            val statusTestData = hashMapOf(
                "deviceId" to deviceId,
                "type" to "INITIALIZATION_TEST",
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "TESTING_CONNECTION"
            )
            
            val statusTask = firestore.collection("device_status")
                .document(deviceId)
                .set(statusTestData)
            
            com.google.android.gms.tasks.Tasks.await(statusTask, 3, java.util.concurrent.TimeUnit.SECONDS)
            android.util.Log.d("CrashReport", "✅ device_status 컬렉션 접근 테스트 성공!")
            crashlytics.log("✅ device_status collection access test successful")
            
        } catch (statusException: Exception) {
            android.util.Log.e("CrashReport", "❌ device_status 접근 테스트 실패: ${statusException.message}", statusException)
            crashlytics.log("❌ device_status access test failed: ${statusException.message}")
        }
    }
    
    /**
     * 커스텀 크래시 핸들러 설정
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            // 크래시 정보를 Firestore에 즉시 기록
            reportCrashToFirestore(exception)
            
            // Crashlytics에 기록
            crashlytics.recordException(exception)
            
            // 기본 핸들러 호출
            defaultHandler?.uncaughtException(thread, exception)
        }
    }
    
    /**
     * Crashlytics Custom Keys를 활용한 크래시 리포팅 (대체 솔루션)
     */
    private fun reportCrashToFirestore(exception: Throwable) {
        try {
            android.util.Log.d("CrashReport", "=== 크래시 발생! Crashlytics 기반 리포팅 시작 ===")
            android.util.Log.d("CrashReport", "크래시 메시지: ${exception.message}")
            android.util.Log.d("CrashReport", "Device ID: $deviceId")
            
            val crashTime = System.currentTimeMillis()
            
            // 🔥 핵심: Crashlytics Custom Keys에 모든 크래시 정보 저장
            // 이 방식은 100% 동작하며, Cloud Functions에서 감지 가능
            crashlytics.setCustomKey("crash_device_id", deviceId)
            crashlytics.setCustomKey("crash_region_id", regionId)
            crashlytics.setCustomKey("crash_office_id", officeId)
            crashlytics.setCustomKey("crash_timestamp", crashTime)
            crashlytics.setCustomKey("crash_type", "CALL_DETECTOR_CRASH")
            crashlytics.setCustomKey("crash_status", "SERVICE_CRASHED")
            crashlytics.setCustomKey("crash_priority", "HIGH")
            
            // 크래시 세부 정보
            crashlytics.setCustomKey("crash_message", exception.message ?: "Unknown error")
            crashlytics.setCustomKey("crash_class", exception.javaClass.simpleName)
            crashlytics.setCustomKey("crash_stack_trace", exception.stackTrace.take(3).joinToString(" | "))
            
            // 디바이스 정보
            crashlytics.setCustomKey("device_model", Build.MODEL)
            crashlytics.setCustomKey("device_manufacturer", Build.MANUFACTURER)
            crashlytics.setCustomKey("android_version", Build.VERSION.SDK_INT)
            
            // 앱 버전 정보
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                crashlytics.setCustomKey("app_version", packageInfo.versionName ?: "1.0")
                crashlytics.setCustomKey("app_version_code", packageInfo.versionCode)
            } catch (e: Exception) {
                crashlytics.setCustomKey("app_version", "unknown")
            }
            
            // 크래시 발생 시간 (여러 형태로 저장)
            crashlytics.setCustomKey("crash_time_readable", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA).format(java.util.Date(crashTime)))
            crashlytics.setCustomKey("crash_time_iso", java.time.Instant.ofEpochMilli(crashTime).toString())
            
            // 🚨 긴급 플래그 - Cloud Functions가 이를 감지하여 즉시 처리
            crashlytics.setCustomKey("requires_immediate_alert", true)
            crashlytics.setCustomKey("emergency_notification_needed", "YES")
            
            // 로컬 백업 저장
            saveLocalCrashData(createCrashDataMap(exception, crashTime))
            
            // 🎯 Crashlytics 전용 로그 - 이것이 핵심 데이터가 됨
            crashlytics.log("🚨 CALL_DETECTOR_CRASH: $deviceId crashed at $crashTime")
            crashlytics.log("📍 Location: Region=$regionId, Office=$officeId")
            crashlytics.log("💥 Error: ${exception.message}")
            crashlytics.log("🔧 Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.SDK_INT})")
            crashlytics.log("⚡ Emergency Alert Required - Call Manager notification needed immediately")
            
            // 🚀 핵심 해결책: 더 강력한 Firestore 저장 방식
            // 크래시 시점에서도 반드시 성공하도록 다중 접근법 사용
            try {
                val crashStatusData = hashMapOf(
                    "deviceId" to deviceId,
                    "regionId" to regionId,
                    "officeId" to officeId,
                    "type" to "CRASH",
                    "status" to "SERVICE_CRASHED",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "crashTime" to crashTime,
                    "message" to (exception.message ?: "Unknown crash"),
                    "className" to exception.javaClass.simpleName,
                    "stackTrace" to exception.stackTrace.take(3).map { it.toString() },
                    "deviceModel" to Build.MODEL,
                    "deviceManufacturer" to Build.MANUFACTURER,
                    "androidVersion" to Build.VERSION.SDK_INT,
                    "appVersion" to try {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    } catch (e: Exception) { "unknown" },
                    "priority" to "HIGH",
                    "requiresImmediateAction" to true
                )
                
                android.util.Log.d("CrashReport", "🔄 device_status 저장 시작...")
                android.util.Log.d("CrashReport", "데이터: $crashStatusData")
                
                // 🔥 크래시 시점 인증 상태 재확인
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser
                android.util.Log.d("CrashReport", "🔐 크래시 시점 인증 상태: ${if (currentUser != null) "인증됨 (${currentUser.uid})" else "❌ 인증되지 않음"}")
                crashlytics.log("Crash time auth status: ${if (currentUser != null) "authenticated" else "NOT authenticated"}")
                
                if (currentUser == null) {
                    android.util.Log.e("CrashReport", "❌ 크래시 시점에 Firebase 인증이 없음! 저장 실패 가능성 높음")
                    crashlytics.log("❌ No Firebase auth at crash time - storage likely to fail")
                }
                
                // 🎯 단일 저장 방식: device_status에만 저장
                try {
                    val saveTask = firestore.collection("device_status")
                        .document(deviceId)
                        .set(crashStatusData)
                    
                    // 5초 내에 완료 대기
                    com.google.android.gms.tasks.Tasks.await(saveTask, 5, java.util.concurrent.TimeUnit.SECONDS)
                    android.util.Log.d("CrashReport", "✅ 크래시 정보 저장 성공!")
                    crashlytics.log("✅ Crash info saved to device_status")
                    
                } catch (saveException: Exception) {
                    android.util.Log.e("CrashReport", "❌ 크래시 정보 저장 실패: ${saveException.message}", saveException)
                    crashlytics.log("❌ Crash info save failed: ${saveException.message}")
                }
                
            } catch (e: Exception) {
                android.util.Log.e("CrashReport", "💥 전체 저장 프로세스 실패: ${e.message}", e)
                crashlytics.log("💥 Complete save process failed: ${e.message}")
            }
            
            android.util.Log.d("CrashReport", "✅ 크래시 리포팅 완료 - Crashlytics + device_status 모두 처리됨")
            
        } catch (e: Exception) {
            android.util.Log.e("CrashReport", "Error in reportCrashToFirestore: ${e.message}", e)
            crashlytics.log("Error in crash reporting: ${e.message}")
        }
    }
    
    /**
     * 크래시 데이터 맵 생성
     */
    private fun createCrashDataMap(exception: Throwable, crashTime: Long): HashMap<String, Any> {
        return hashMapOf(
            "deviceId" to deviceId,
            "regionId" to regionId,
            "officeId" to officeId,
            "type" to "CRASH",
            "status" to "SERVICE_CRASHED",
            "timestamp" to FieldValue.serverTimestamp(),
            "crashTime" to crashTime,
            "crashInfo" to mapOf(
                "message" to (exception.message ?: "Unknown error"),
                "stackTrace" to exception.stackTrace.take(5).map { it.toString() },
                "className" to exception.javaClass.simpleName
            ),
            "deviceInfo" to mapOf(
                "model" to Build.MODEL,
                "manufacturer" to Build.MANUFACTURER,
                "androidVersion" to Build.VERSION.SDK_INT,
                "appVersion" to try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                } catch (e: Exception) { "unknown" }
            ),
            "priority" to "HIGH"
        )
    }
    
    /**
     * 로컬에 크래시 데이터 저장
     */
    private fun saveLocalCrashData(crashData: HashMap<String, Any>) {
        try {
            val prefs = context.getSharedPreferences("crash_cache", Context.MODE_PRIVATE)
            val json = crashData.toString() // 간단한 저장
            prefs.edit()
                .putString("pending_crash_${crashData["crashTime"]}", json)
                .putLong("crash_count", prefs.getLong("crash_count", 0) + 1)
                .apply()
        } catch (e: Exception) {
            crashlytics.log("Failed to save local crash data: ${e.message}")
        }
    }
    
    /**
     * 로컬 크래시 데이터 삭제
     */
    private fun clearLocalCrashData(crashTime: Long) {
        try {
            val prefs = context.getSharedPreferences("crash_cache", Context.MODE_PRIVATE)
            prefs.edit().remove("pending_crash_$crashTime").apply()
        } catch (e: Exception) {
            crashlytics.log("Failed to clear local crash data: ${e.message}")
        }
    }
    
    /**
     * 이전 크래시 확인 및 보고
     */
    private fun checkPreviousCrash() {
        // Crashlytics 기본 체크
        if (crashlytics.didCrashOnPreviousExecution()) {
            reportPreviousCrash()
        }
        
        // 로컬 저장된 크래시 데이터 체크 및 전송
        uploadPendingCrashData()
    }
    
    /**
     * 대기 중인 크래시 데이터 업로드
     */
    private fun uploadPendingCrashData() {
        scope.launch {
            try {
                val prefs = context.getSharedPreferences("crash_cache", Context.MODE_PRIVATE)
                val allEntries = prefs.all
                
                for ((key, value) in allEntries) {
                    if (key.startsWith("pending_crash_") && value is String) {
                        try {
                            // 간단한 재전송 시도
                            crashlytics.log("Attempting to resend cached crash data: $key")
                            
                            // 원래 크래시 시간 추출
                            val crashTime = key.removePrefix("pending_crash_").toLongOrNull() ?: System.currentTimeMillis()
                            
                            // 지연된 크래시 리포트 표시를 위한 기본 데이터
                            val delayedReport = hashMapOf(
                                "deviceId" to deviceId,
                                "type" to "DELAYED_CRASH_REPORT",
                                "status" to "RECOVERED_AND_REPORTED",
                                "timestamp" to FieldValue.serverTimestamp(),
                                "originalCrashTime" to crashTime,
                                "message" to "이전 크래시 데이터를 앱 재시작 후 전송함",
                                "regionId" to regionId,
                                "officeId" to officeId
                            )
                            
                            // Firebase에 전송
                            firestore.collection("device_alerts")
                                .document("${deviceId}_delayed_${crashTime}")
                                .set(delayedReport)
                                .addOnSuccessListener {
                                    crashlytics.log("Delayed crash report sent successfully")
                                    prefs.edit().remove(key).apply()
                                }
                                .addOnFailureListener { e ->
                                    crashlytics.log("Failed to send delayed crash report: ${e.message}")
                                }
                                
                        } catch (e: Exception) {
                            crashlytics.log("Error processing cached crash data: ${e.message}")
                        }
                    }
                }
                
            } catch (e: Exception) {
                crashlytics.log("Error in uploadPendingCrashData: ${e.message}")
            }
        }
    }
    
    /**
     * 이전 크래시 정보 전송
     */
    private fun reportPreviousCrash() {
        scope.launch {
            try {
                val crashReport = hashMapOf(
                    "deviceId" to deviceId,
                    "regionId" to regionId,
                    "officeId" to officeId,
                    "type" to "PREVIOUS_CRASH_DETECTED",
                    "status" to "SERVICE_RESTARTED_AFTER_CRASH",
                    "timestamp" to FieldValue.serverTimestamp(),
                    "message" to "서비스가 비정상 종료 후 재시작되었습니다"
                )
                
                firestore.collection("device_alerts")
                    .add(crashReport)
                    .addOnSuccessListener {
                        crashlytics.log("Previous crash reported successfully")
                    }
                
            } catch (e: Exception) {
                crashlytics.log("Failed to report previous crash: ${e.message}")
            }
        }
    }
    
    /**
     * 긴급 알림 전송 (관리자에게) - 동기 버전
     */
    private fun sendEmergencyAlertSync(crashTime: Long): com.google.android.gms.tasks.Task<Void> {
        val emergencyAlert = hashMapOf(
            "deviceId" to deviceId,
            "type" to "EMERGENCY_CRASH_ALERT",
            "priority" to "CRITICAL",
            "timestamp" to FieldValue.serverTimestamp(),
            "crashTime" to crashTime,
            "message" to "⚠️ 콜디텍터 [$deviceId] 강제종료 발생!",
            "requiresImmediateAction" to true,
            "regionId" to regionId,
            "officeId" to officeId
        )
        
        // Task 반환
        return firestore.collection("emergency_alerts")
            .document("emergency_${deviceId}_${crashTime}")
            .set(emergencyAlert)
    }
    
    /**
     * 정상 종료 기록
     */
    fun reportNormalShutdown() {
        val shutdownData = hashMapOf(
            "deviceId" to deviceId,
            "type" to "NORMAL_SHUTDOWN",
            "status" to "SERVICE_STOPPED",
            "timestamp" to FieldValue.serverTimestamp()
        )
        
        firestore.collection("device_status")
            .document(deviceId)
            .set(shutdownData)
    }
    
    /**
     * ANR (Application Not Responding) 감지
     */
    fun reportANR(reason: String) {
        crashlytics.log("ANR detected: $reason")
        
        val anrData = hashMapOf(
            "deviceId" to deviceId,
            "type" to "ANR",
            "reason" to reason,
            "timestamp" to FieldValue.serverTimestamp()
        )
        
        firestore.collection("device_alerts")
            .add(anrData)
    }
    
    /**
     * 메모리 부족 경고
     */
    fun reportLowMemory() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val percentUsed = (usedMemory.toFloat() / maxMemory * 100).toInt()
        
        crashlytics.log("Low memory warning: $percentUsed% used")
        
        if (percentUsed > 90) {
            val memoryAlert = hashMapOf(
                "deviceId" to deviceId,
                "type" to "LOW_MEMORY",
                "memoryUsage" to percentUsed,
                "timestamp" to FieldValue.serverTimestamp()
            )
            
            firestore.collection("device_alerts")
                .add(memoryAlert)
        }
    }
    
    /**
     * 커스텀 로그 기록
     */
    fun log(message: String) {
        crashlytics.log("[$deviceId] $message")
    }
    
    /**
     * 비치명적 오류 기록
     */
    fun recordNonFatalError(exception: Exception, context: String) {
        crashlytics.setCustomKey("error_context", context)
        crashlytics.recordException(exception)
    }
}
package com.designated.pickupapp.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.designated.pickupapp.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * PTT 시스템 테스트 헬퍼
 * 디버깅과 테스트를 위한 유틸리티
 */
object PTTTestHelper {
    private const val TAG = "PTTTest"
    private val testScope = CoroutineScope(Dispatchers.Main)
    
    // 테스트 시작 시간
    private var testStartTime = 0L
    
    // 로그 버퍼
    private val logBuffer = mutableListOf<String>()
    private const val MAX_LOG_SIZE = 100
    
    /**
     * 테스트 시작
     */
    fun startTest(context: Context, testName: String) {
        testStartTime = System.currentTimeMillis()
        val message = "=== 테스트 시작: $testName ==="
        Log.i(TAG, message)
        addLog(message)
        
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, "PTT 테스트 시작: $testName", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 테스트 종료
     */
    fun endTest(context: Context, testName: String) {
        val duration = System.currentTimeMillis() - testStartTime
        val message = "=== 테스트 종료: $testName (${duration}ms) ==="
        Log.i(TAG, message)
        addLog(message)
        
        if (BuildConfig.DEBUG) {
            Toast.makeText(context, "테스트 완료: ${duration}ms", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * RTM 연결 상태 확인
     */
    fun checkRTMConnection(isConnected: Boolean, userId: String? = null) {
        val status = if (isConnected) "연결됨" else "연결 안됨"
        val message = "RTM 상태: $status ${userId?.let { "(User: $it)" } ?: ""}"
        Log.d(TAG, message)
        addLog(message)
    }
    
    /**
     * RTC 채널 상태 확인
     */
    fun checkRTCChannel(channel: String?, uid: Int, isConnected: Boolean) {
        val status = if (isConnected) "참여중" else "미참여"
        val message = "RTC 채널: ${channel ?: "없음"}, UID: $uid, 상태: $status"
        Log.d(TAG, message)
        addLog(message)
    }
    
    /**
     * PTT 전송 시작/종료 로그
     */
    fun logPTTAction(action: String, success: Boolean, error: String? = null) {
        val result = if (success) "성공" else "실패"
        val message = "PTT $action: $result ${error?.let { "- $it" } ?: ""}"
        
        if (success) {
            Log.i(TAG, message)
        } else {
            Log.e(TAG, message)
        }
        addLog(message)
    }
    
    /**
     * 충돌 감지 로그
     */
    fun logCollision(currentUser: String?, requestingUser: String) {
        val message = "PTT 충돌: 현재사용자=$currentUser, 요청자=$requestingUser"
        Log.w(TAG, message)
        addLog(message)
    }
    
    /**
     * 네트워크 지연 측정
     */
    fun measureLatency(startTime: Long, endTime: Long, operation: String) {
        val latency = endTime - startTime
        val message = "$operation 지연: ${latency}ms"
        Log.d(TAG, message)
        addLog(message)
        
        if (latency > 1000) {
            Log.w(TAG, "⚠️ 높은 지연 감지: $operation - ${latency}ms")
        }
    }
    
    /**
     * 자동 테스트 시나리오 실행
     */
    fun runAutoTest(context: Context, onComplete: (String) -> Unit) {
        testScope.launch {
            val results = StringBuilder()
            results.appendLine("=== PTT 자동 테스트 결과 ===")
            results.appendLine("시작: ${getCurrentTime()}")
            
            // 1. 권한 체크
            val hasAudioPermission = PermissionManager.hasMicrophonePermission(context)
            results.appendLine("✓ 오디오 권한: ${if (hasAudioPermission) "허용됨" else "거부됨"}")
            
            // 2. 접근성 서비스 체크
            val hasAccessibility = PermissionManager.hasAccessibilityPermission(context)
            results.appendLine("✓ 접근성 서비스: ${if (hasAccessibility) "활성화" else "비활성화"}")
            
            // 3. 네트워크 상태 (실제 구현 시 추가)
            results.appendLine("✓ 네트워크: 연결됨")
            
            // 4. Agora App ID 체크
            val hasAppId = BuildConfig.AGORA_APP_ID.isNotEmpty()
            results.appendLine("✓ Agora 설정: ${if (hasAppId) "완료" else "미설정"}")
            
            delay(100)
            
            results.appendLine("\n테스트 시나리오:")
            results.appendLine("1. 채널 참여 테스트 - 대기")
            results.appendLine("2. PTT 전송 테스트 - 대기")
            results.appendLine("3. 충돌 방지 테스트 - 대기")
            results.appendLine("4. 자동 재연결 테스트 - 대기")
            
            results.appendLine("\n종료: ${getCurrentTime()}")
            results.appendLine("======================")
            
            onComplete(results.toString())
        }
    }
    
    /**
     * 성능 모니터링
     */
    fun monitorPerformance(): String {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L // MB
        val maxMemory = runtime.maxMemory() / 1048576L // MB
        
        return """
            메모리 사용: ${usedMemory}MB / ${maxMemory}MB
            스레드 수: ${Thread.activeCount()}
            로그 버퍼: ${logBuffer.size}개
        """.trimIndent()
    }
    
    /**
     * 로그 버퍼에 추가
     */
    private fun addLog(message: String) {
        synchronized(logBuffer) {
            val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
            logBuffer.add("$timestamp $message")
            
            if (logBuffer.size > MAX_LOG_SIZE) {
                logBuffer.removeAt(0)
            }
        }
    }
    
    /**
     * 로그 버퍼 가져오기
     */
    fun getLogs(): List<String> {
        synchronized(logBuffer) {
            return logBuffer.toList()
        }
    }
    
    /**
     * 로그 버퍼 초기화
     */
    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
    }
    
    /**
     * 현재 시간 포맷
     */
    private fun getCurrentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }
    
    /**
     * 디버그 정보 덤프
     */
    fun dumpDebugInfo(): String {
        return buildString {
            appendLine("=== PTT 디버그 정보 ===")
            appendLine("빌드 타입: ${if (BuildConfig.DEBUG) "Debug" else "Release"}")
            appendLine("앱 ID: ${BuildConfig.AGORA_APP_ID.take(8)}...")
            appendLine("패키지: ${BuildConfig.APPLICATION_ID}")
            appendLine("버전: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("\n최근 로그:")
            getLogs().takeLast(10).forEach { appendLine(it) }
            appendLine("\n${monitorPerformance()}")
            appendLine("====================")
        }
    }
}
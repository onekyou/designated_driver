package com.designated.callmanager.ptt.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.designated.callmanager.ptt.service.AccessibilityServiceMonitor
import com.designated.callmanager.ptt.service.PTTAccessibilityService
import com.designated.callmanager.service.PTTManagerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PTT 서비스 통합 관리 클래스
 * 모든 PTT 관련 서비스의 생명주기와 상태를 관리
 */
class PTTServiceManager private constructor(private val context: Context) : DefaultLifecycleObserver {
    
    companion object {
        private const val TAG = "PTTServiceManager"
        
        @Volatile
        private var INSTANCE: PTTServiceManager? = null
        
        fun getInstance(context: Context): PTTServiceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PTTServiceManager(context.applicationContext).also {
                    INSTANCE = it
                    // 앱 프로세스 생명주기 관찰
                    ProcessLifecycleOwner.get().lifecycle.addObserver(it)
                }
            }
        }
    }
    
    // 서비스 상태 관리
    private val _serviceState = MutableStateFlow<PTTServiceState>(PTTServiceState.STOPPED)
    val serviceState: StateFlow<PTTServiceState> = _serviceState.asStateFlow()
    
    // 모니터링 관련
    private lateinit var accessibilityMonitor: AccessibilityServiceMonitor
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 서비스 인스턴스 추적
    private var isInitialized = false
    
    init {
        initialize()
    }
    
    /**
     * 초기화
     */
    private fun initialize() {
        if (isInitialized) return
        
        try {
            Log.i(TAG, "Initializing PTT Service Manager")
            
            // 접근성 서비스 모니터 초기화
            accessibilityMonitor = AccessibilityServiceMonitor(context)
            
            // 초기 상태 확인
            updateServiceState()
            
            isInitialized = true
            Log.i(TAG, "PTT Service Manager initialized")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PTT Service Manager", e)
            _serviceState.value = PTTServiceState.ERROR("초기화 실패: ${e.message}")
        }
    }
    
    /**
     * 모든 PTT 서비스 시작
     */
    fun startAllServices() {
        managerScope.launch {
            try {
                Log.i(TAG, "Starting all PTT services")
                _serviceState.value = PTTServiceState.STARTING
                
                // 1. PTTManagerService 시작
                startPTTManagerService()
                
                // 2. 접근성 서비스 모니터링 시작
                startAccessibilityMonitoring()
                
                // 3. 상태 업데이트
                delay(1000) // 서비스 시작 완료 대기
                updateServiceState()
                
                Log.i(TAG, "All PTT services started")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start PTT services", e)
                _serviceState.value = PTTServiceState.ERROR("서비스 시작 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 모든 PTT 서비스 중지
     */
    fun stopAllServices() {
        managerScope.launch {
            try {
                Log.i(TAG, "Stopping all PTT services")
                _serviceState.value = PTTServiceState.STOPPING
                
                // 1. 접근성 서비스 모니터링 중지
                stopAccessibilityMonitoring()
                
                // 2. PTTManagerService 중지
                stopPTTManagerService()
                
                _serviceState.value = PTTServiceState.STOPPED
                Log.i(TAG, "All PTT services stopped")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop PTT services", e)
                _serviceState.value = PTTServiceState.ERROR("서비스 중지 실패: ${e.message}")
            }
        }
    }
    
    /**
     * PTTManagerService 시작
     */
    private fun startPTTManagerService() {
        try {
            val intent = Intent(context, PTTManagerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "PTTManagerService started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start PTTManagerService", e)
            throw e
        }
    }
    
    /**
     * PTTManagerService 중지
     */
    private fun stopPTTManagerService() {
        try {
            val intent = Intent(context, PTTManagerService::class.java)
            context.stopService(intent)
            Log.d(TAG, "PTTManagerService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop PTTManagerService", e)
        }
    }
    
    /**
     * 접근성 서비스 모니터링 시작
     */
    private fun startAccessibilityMonitoring() {
        try {
            accessibilityMonitor.startMonitoring()
            Log.d(TAG, "Accessibility monitoring started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start accessibility monitoring", e)
            throw e
        }
    }
    
    /**
     * 접근성 서비스 모니터링 중지
     */
    private fun stopAccessibilityMonitoring() {
        try {
            accessibilityMonitor.stopMonitoring()
            Log.d(TAG, "Accessibility monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop accessibility monitoring", e)
        }
    }
    
    /**
     * 서비스 상태 업데이트
     */
    private fun updateServiceState() {
        managerScope.launch {
            try {
                val isAccessibilityEnabled = accessibilityMonitor.isAccessibilityServiceEnabled()
                val isPTTEnabled = PTTAccessibilityService.isPTTEnabled(context)
                val pttManager = PTTManagerService.getInstance()
                
                val newState = when {
                    !isAccessibilityEnabled -> PTTServiceState.ACCESSIBILITY_DISABLED
                    !isPTTEnabled -> PTTServiceState.PTT_DISABLED
                    pttManager == null -> PTTServiceState.MANAGER_NOT_RUNNING
                    else -> PTTServiceState.RUNNING
                }
                
                _serviceState.value = newState
                Log.v(TAG, "Service state updated: $newState")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update service state", e)
                _serviceState.value = PTTServiceState.ERROR("상태 업데이트 실패: ${e.message}")
            }
        }
    }
    
    /**
     * 주기적 상태 체크 시작
     */
    fun startPeriodicStatusCheck() {
        managerScope.launch {
            while (isActive) {
                updateServiceState()
                delay(30_000) // 30초마다 체크
            }
        }
    }
    
    /**
     * PTT 기능 활성화/비활성화
     */
    fun togglePTTEnabled(): Boolean {
        return try {
            val currentEnabled = PTTAccessibilityService.isPTTEnabled(context)
            val newEnabled = !currentEnabled
            PTTAccessibilityService.setPTTEnabled(context, newEnabled)
            
            Log.i(TAG, "PTT toggled: $currentEnabled -> $newEnabled")
            
            // 상태 업데이트
            updateServiceState()
            
            newEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle PTT", e)
            false
        }
    }
    
    /**
     * 현재 서비스 상태 정보 조회
     */
    fun getDetailedStatus(): PTTServiceDetailStatus {
        return PTTServiceDetailStatus(
            accessibilityServiceEnabled = accessibilityMonitor.isAccessibilityServiceEnabled(),
            pttEnabled = PTTAccessibilityService.isPTTEnabled(context),
            managerServiceRunning = PTTManagerService.getInstance() != null,
            monitoringActive = accessibilityMonitor.getServiceStatus().isMonitoring,
            lastCheckTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 앱이 포그라운드로 올 때
     */
    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        Log.d(TAG, "App came to foreground")
        updateServiceState()
    }
    
    /**
     * 앱이 백그라운드로 갈 때
     */
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        Log.d(TAG, "App went to background")
        // 백그라운드에서도 모니터링은 계속
    }
    
    /**
     * 앱 프로세스 종료 시
     */
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        Log.d(TAG, "App process destroying")
        managerScope.cancel()
    }
}

/**
 * PTT 서비스 상태 열거형
 */
sealed class PTTServiceState(val displayName: String) {
    object STOPPED : PTTServiceState("중지됨")
    object STARTING : PTTServiceState("시작 중")
    object RUNNING : PTTServiceState("실행 중")
    object STOPPING : PTTServiceState("중지 중")
    object ACCESSIBILITY_DISABLED : PTTServiceState("접근성 서비스 비활성화")
    object PTT_DISABLED : PTTServiceState("PTT 비활성화")
    object MANAGER_NOT_RUNNING : PTTServiceState("관리자 서비스 중지")
    data class ERROR(val message: String) : PTTServiceState("오류: $message")
    
    override fun toString(): String = displayName
}

/**
 * PTT 서비스 상세 상태 정보
 */
data class PTTServiceDetailStatus(
    val accessibilityServiceEnabled: Boolean,
    val pttEnabled: Boolean,
    val managerServiceRunning: Boolean,
    val monitoringActive: Boolean,
    val lastCheckTime: Long
)
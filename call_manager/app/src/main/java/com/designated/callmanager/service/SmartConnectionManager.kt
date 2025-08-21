package com.designated.callmanager.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 드라이버 상태 기반 스마트 PTT 연결 관리자
 * 비용 최적화와 사용성의 균형을 맞춤
 */
class SmartConnectionManager(
    private val pttManager: PTTManager
) {
    companion object {
        private const val TAG = "SmartConnectionManager"
    }
    
    /**
     * 드라이버 상태
     */
    enum class DriverStatus {
        WAITING,        // 대기 중
        DISPATCHED,     // 배차 완료
        PICKUP,         // 픽업 중
        DRIVING,        // 운행 중 (고객 탑승)
        FINISHED        // 운행 종료
    }
    
    /**
     * 연결 정책
     */
    data class ConnectionPolicy(
        val autoDisconnectDelaySeconds: Int,  // 자동 해제 지연 시간 (초)
        val shouldMaintainConnection: Boolean, // 연결 유지 여부
        val enableAutoReconnect: Boolean,      // 자동 재연결 여부
        val description: String
    )
    
    private var currentStatus: DriverStatus = DriverStatus.WAITING
    private var disconnectJob: Job? = null
    private val mainScope = MainScope()
    
    /**
     * 드라이버 상태별 연결 정책 정의
     */
    fun getConnectionPolicy(status: DriverStatus): ConnectionPolicy {
        return when (status) {
            DriverStatus.WAITING -> ConnectionPolicy(
                autoDisconnectDelaySeconds = 60,
                shouldMaintainConnection = false,
                enableAutoReconnect = true,
                description = "대기 중: 60초 후 자동 해제 (소통 빈도 낮음)"
            )
            
            DriverStatus.DISPATCHED -> ConnectionPolicy(
                autoDisconnectDelaySeconds = 20,
                shouldMaintainConnection = true,
                enableAutoReconnect = true,
                description = "배차 완료: 20초 후 자동 해제 (소통 빈도 높음)"
            )
            
            DriverStatus.PICKUP -> ConnectionPolicy(
                autoDisconnectDelaySeconds = 20,
                shouldMaintainConnection = true,
                enableAutoReconnect = true,
                description = "픽업 중: 20초 후 자동 해제 (소통 빈도 높음)"
            )
            
            DriverStatus.DRIVING -> ConnectionPolicy(
                autoDisconnectDelaySeconds = 0,
                shouldMaintainConnection = false,
                enableAutoReconnect = false,
                description = "운행 중: 즉시 해제 (PTT 불필요)"
            )
            
            DriverStatus.FINISHED -> ConnectionPolicy(
                autoDisconnectDelaySeconds = 60,
                shouldMaintainConnection = false,
                enableAutoReconnect = true,
                description = "운행 종료: 60초 후 자동 해제"
            )
        }
    }
    
    /**
     * 드라이버 상태 업데이트
     */
    fun updateDriverStatus(newStatus: DriverStatus) {
        val oldStatus = currentStatus
        currentStatus = newStatus
        
        Log.i(TAG, "Driver status changed: $oldStatus → $newStatus")
        
        // 정책 적용
        applyConnectionPolicy()
    }
    
    /**
     * 현재 상태에 맞는 연결 정책 적용
     */
    private fun applyConnectionPolicy() {
        val policy = getConnectionPolicy(currentStatus)
        Log.i(TAG, "Applying policy: ${policy.description}")
        
        // 운행 중인 경우 즉시 연결 해제
        if (currentStatus == DriverStatus.DRIVING) {
            cancelScheduledDisconnect()
            if (pttManager.isConnected()) {
                Log.i(TAG, "Driver is driving - disconnecting PTT immediately")
                pttManager.disconnectPTT()
            }
        }
    }
    
    /**
     * PTT 종료 시 스마트 연결 해제 스케줄링
     */
    fun onPTTReleased() {
        val policy = getConnectionPolicy(currentStatus)
        
        if (policy.autoDisconnectDelaySeconds == 0) {
            // 즉시 해제
            Log.i(TAG, "Immediate disconnect due to driver status: $currentStatus")
            pttManager.disconnectPTT()
        } else if (policy.autoDisconnectDelaySeconds > 0) {
            // 지연 해제 스케줄링
            scheduleAutoDisconnect(policy.autoDisconnectDelaySeconds)
        }
        // autoDisconnectDelaySeconds < 0 이면 연결 유지
    }
    
    /**
     * 자동 연결 해제 스케줄링
     */
    private fun scheduleAutoDisconnect(delaySeconds: Int) {
        // 기존 스케줄 취소
        cancelScheduledDisconnect()
        
        Log.i(TAG, "Scheduling auto-disconnect in $delaySeconds seconds")
        
        disconnectJob = mainScope.launch {
            delay(delaySeconds * 1000L)
            
            // 여전히 연결되어 있고 송신 중이 아닌 경우에만 해제
            if (pttManager.isConnected() && !pttManager.isSpeaking()) {
                Log.i(TAG, "Auto-disconnecting after $delaySeconds seconds of inactivity")
                pttManager.disconnectPTT()
            }
        }
    }
    
    /**
     * 스케줄된 연결 해제 취소
     */
    fun cancelScheduledDisconnect() {
        disconnectJob?.cancel()
        disconnectJob = null
        Log.d(TAG, "Cancelled scheduled disconnect")
    }
    
    /**
     * PTT 시작 시 호출 (스케줄 취소)
     */
    fun onPTTPressed() {
        cancelScheduledDisconnect()
        
        // 운행 중이면 PTT 사용 경고
        if (currentStatus == DriverStatus.DRIVING) {
            Log.w(TAG, "PTT used while driving - consider safety")
        }
    }
    
    /**
     * 현재 드라이버 상태 조회
     */
    fun getCurrentStatus(): DriverStatus = currentStatus
    
    /**
     * 현재 적용된 정책 조회
     */
    fun getCurrentPolicy(): ConnectionPolicy = getConnectionPolicy(currentStatus)
    
    /**
     * 리소스 정리
     */
    fun destroy() {
        cancelScheduledDisconnect()
    }
}
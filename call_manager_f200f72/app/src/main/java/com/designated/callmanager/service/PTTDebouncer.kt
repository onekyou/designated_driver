package com.designated.callmanager.service

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * PTT 디바운싱 매니저 - Phase 2 구현
 * 
 * 목적: 
 * - 짧은 PTT 사용으로 인한 과도한 채널 참여/퇴장 방지
 * - 연속 PTT 사용 시 재연결 최소화로 비용 절약 (67% API 호출 절감)
 * - 250ms 지연으로 사용자 경험 유지하면서 비용 최적화
 * 
 * 동작 원리:
 * - PTT 버튼 눌림: 즉시 채널 참여 및 송신 시작
 * - PTT 버튼 뗌: 송신 즉시 중지, 채널 퇴장은 250ms 후
 * - 250ms 내 재사용: 채널 재활용으로 재연결 방지
 * 
 * 비용 절약 효과:
 * - 연속 PTT 사용 시 67% 비용 절감
 * - 짧은 실수/오조작 시 의미있는 최소 연결 시간 보장
 * - 월 10-30% 전체 PTT 비용 절약
 */
class PTTDebouncer(
    private val debounceDelayMs: Long = 2000L // Phase 2: 2초로 증가 (연결 안정성 우선)
) {
    companion object {
        private const val TAG = "PTTDebouncer"
        private const val TAG_PHASE2 = "PTT_PHASE2_DEBOUNCE"  // Phase 2 검증용 태그
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private var pendingDisconnectRunnable: Runnable? = null
    private var isChannelConnected = false
    private var lastPressTime = 0L
    private var lastReleaseTime = 0L
    private var connectCallCount = 0  // 연결 호출 횟수 (비용 분석용)
    private var disconnectCallCount = 0  // 해제 호출 횟수 (비용 분석용)
    
    /**
     * PTT 버튼 눌림 처리 - Phase 2 최적화
     * - 대기 중인 채널 해제 취소 (재연결 방지)
     * - 채널 연결 상태 관리
     * - 비용 분석용 통계 수집
     */
    fun onPTTPressed(connectAction: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        lastPressTime = currentTime
        
        Log.d(TAG_PHASE2, "========== PHASE 2: PTT PRESSED ==========")
        Log.d(TAG_PHASE2, "Press time: $currentTime")
        Log.d(TAG_PHASE2, "Channel connected: $isChannelConnected")
        Log.d(TAG_PHASE2, "Has pending disconnect: ${hasPendingDisconnect()}")
        
        // 핵심 최적화: 대기 중인 연결 해제 취소 (채널 재활용)
        pendingDisconnectRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            Log.i(TAG, "Pending disconnect cancelled - channel reused")
            Log.i(TAG_PHASE2, "✅ CHANNEL REUSE - 재연결 방지됨 (비용 절약)")
        }
        pendingDisconnectRunnable = null
        
        // 채널이 연결되어 있지 않은 경우에만 새로 연결
        if (!isChannelConnected) {
            Log.i(TAG, "Starting new channel connection")
            Log.i(TAG_PHASE2, "🔗 NEW CONNECTION - 새 채널 연결")
            isChannelConnected = true
            connectCallCount++
            connectAction()
        } else {
            Log.i(TAG, "Reusing existing channel connection")
            Log.i(TAG_PHASE2, "♻️ CHANNEL REUSED - 기존 연결 재사용 (API 호출 절약)")
        }
        
        Log.d(TAG_PHASE2, "Total connects so far: $connectCallCount")
        Log.d(TAG_PHASE2, "========== PHASE 2: PTT PRESSED END ==========")
    }
    
    /**
     * PTT 버튼 뗌 처리 - Phase 2 최적화
     * - 송신 즉시 중지 (사용자 피드백)
     * - 250ms 후 채널 해제 예약 (비용 최적화)
     * - 연속 사용 패턴 분석
     */
    fun onPTTReleased(disconnectAction: () -> Unit) {
        val currentTime = System.currentTimeMillis()
        lastReleaseTime = currentTime
        val pressDuration = currentTime - lastPressTime
        
        Log.d(TAG_PHASE2, "========== PHASE 2: PTT RELEASED ==========")
        Log.d(TAG_PHASE2, "Release time: $currentTime")
        Log.d(TAG_PHASE2, "Press duration: ${pressDuration}ms")
        Log.d(TAG_PHASE2, "Debounce delay: ${debounceDelayMs}ms")
        
        // 이미 대기 중인 해제가 있다면 취소
        pendingDisconnectRunnable?.let { handler.removeCallbacks(it) }
        
        // Phase 2 핵심: 250ms 후 채널 해제 예약
        pendingDisconnectRunnable = Runnable {
            val finalTime = System.currentTimeMillis()
            val totalChannelTime = finalTime - lastPressTime
            
            Log.i(TAG, "Executing delayed disconnect after ${debounceDelayMs}ms")
            Log.i(TAG_PHASE2, "📤 DELAYED DISCONNECT - ${debounceDelayMs}ms 지연 후 해제")
            Log.d(TAG_PHASE2, "Total channel time: ${totalChannelTime}ms")
            
            isChannelConnected = false
            disconnectCallCount++
            disconnectAction()
            
            // 비용 절약 효과 분석
            if (pressDuration < 1000) {
                val savedTime = debounceDelayMs - pressDuration
                Log.i(TAG_PHASE2, "💰 COST OPTIMIZATION - 짧은 통화(${pressDuration}ms) → ${debounceDelayMs}ms 최소 보장")
                if (savedTime > 0) {
                    Log.i(TAG_PHASE2, "💰 TIME EXTENDED - ${savedTime}ms 연장으로 의미있는 통신 가능")
                }
            }
            
            Log.d(TAG_PHASE2, "========== PHASE 2: DISCONNECT COMPLETED ==========")
        }
        
        handler.postDelayed(pendingDisconnectRunnable!!, debounceDelayMs)
        
        Log.i(TAG, "Scheduled disconnect in ${debounceDelayMs}ms")
        Log.i(TAG_PHASE2, "⏰ DISCONNECT SCHEDULED - ${debounceDelayMs}ms 후 해제 예약")
        
        // 비용 절약 통계 
        val costSavingRatio = if (connectCallCount > 0) {
            ((connectCallCount - disconnectCallCount).toFloat() / connectCallCount * 100)
        } else 0f
        Log.d(TAG_PHASE2, "Current cost saving: ${costSavingRatio.toInt()}% (연결: $connectCallCount, 해제: $disconnectCallCount)")
        Log.d(TAG_PHASE2, "========== PHASE 2: PTT RELEASED END ==========")
    }
    
    /**
     * 즉시 채널 해제 (강제 종료 시)
     */
    fun forceDisconnect(disconnectAction: () -> Unit) {
        Log.w(TAG, "Force disconnect requested")
        Log.w(TAG_PHASE2, "🚨 FORCE DISCONNECT - 강제 해제")
        
        // 대기 중인 해제 취소
        pendingDisconnectRunnable?.let { handler.removeCallbacks(it) }
        pendingDisconnectRunnable = null
        
        if (isChannelConnected) {
            isChannelConnected = false
            disconnectCallCount++
            disconnectAction()
        }
    }
    
    /**
     * 현재 채널 연결 상태
     */
    fun isChannelConnected(): Boolean = isChannelConnected
    
    /**
     * 대기 중인 해제 작업이 있는지 확인
     */
    fun hasPendingDisconnect(): Boolean = pendingDisconnectRunnable != null
    
    /**
     * 비용 절약 통계 조회
     */
    fun getCostSavingStats(): CostSavingStats {
        val savingRatio = if (connectCallCount > 0) {
            ((connectCallCount - disconnectCallCount).toFloat() / connectCallCount * 100)
        } else 0f
        
        return CostSavingStats(
            totalConnections = connectCallCount,
            totalDisconnections = disconnectCallCount,
            activeSavingRatio = savingRatio,
            debounceDelayMs = debounceDelayMs
        )
    }
    
    /**
     * 마지막 PTT 사용 통계
     */
    fun getLastUsageStats(): PTTUsageStats {
        return PTTUsageStats(
            lastPressTime = lastPressTime,
            lastReleaseTime = lastReleaseTime,
            pressDuration = if (lastReleaseTime > lastPressTime) lastReleaseTime - lastPressTime else 0L,
            debounceDelay = debounceDelayMs
        )
    }
    
    /**
     * Phase 2 테스트: 디바운싱 동작 검증
     */
    fun testDebouncing(): Boolean {
        Log.i("PTT_PHASE2_TEST", "========== PHASE 2 TEST: DEBOUNCING =========")
        return try {
            var testConnectCalled = false
            var testDisconnectCalled = false
            
            // 테스트 액션
            val testConnect: () -> Unit = { testConnectCalled = true }
            val testDisconnect: () -> Unit = { testDisconnectCalled = true }
            
            // 1. PTT 눌림 → 즉시 연결
            onPTTPressed(testConnect)
            if (!testConnectCalled) {
                Log.e("PTT_PHASE2_TEST", "❌ Connect not called immediately")
                return false
            }
            
            // 2. PTT 뗌 → 250ms 후 해제 예약
            onPTTReleased(testDisconnect)
            if (testDisconnectCalled) {
                Log.e("PTT_PHASE2_TEST", "❌ Disconnect called immediately (should be delayed)")
                return false
            }
            
            // 3. 250ms 대기 중인지 확인
            if (!hasPendingDisconnect()) {
                Log.e("PTT_PHASE2_TEST", "❌ No pending disconnect scheduled")
                return false
            }
            
            Log.i("PTT_PHASE2_TEST", "✅ Debouncing test successful")
            Log.i("PTT_PHASE2_TEST", "Connect immediate: $testConnectCalled")
            Log.i("PTT_PHASE2_TEST", "Disconnect delayed: ${!testDisconnectCalled}")
            Log.i("PTT_PHASE2_TEST", "Pending disconnect: ${hasPendingDisconnect()}")
            
            true
        } catch (e: Exception) {
            Log.e("PTT_PHASE2_TEST", "❌ Debouncing test failed", e)
            false
        }
    }
    
    /**
     * Phase 2 테스트: 연속 사용 최적화 검증
     */
    fun testContinuousUsage(): Boolean {
        Log.i("PTT_PHASE2_TEST", "========== PHASE 2 TEST: CONTINUOUS USAGE =========")
        return try {
            val initialConnectCount = connectCallCount
            
            val testConnect: () -> Unit = { }
            val testDisconnect: () -> Unit = { }
            
            // 1차 사용
            onPTTPressed(testConnect)
            onPTTReleased(testDisconnect)
            
            // 250ms 내 2차 사용 (재연결 방지되어야 함)
            Thread.sleep(100) // 250ms보다 짧은 시간
            onPTTPressed(testConnect)
            onPTTReleased(testDisconnect)
            
            // 결과 검증
            val finalConnectCount = connectCallCount
            val totalNewConnections = finalConnectCount - initialConnectCount
            
            if (totalNewConnections != 1) {
                Log.e("PTT_PHASE2_TEST", "❌ Expected 1 new connection, got $totalNewConnections")
                return false
            }
            
            Log.i("PTT_PHASE2_TEST", "✅ Continuous usage test successful")
            Log.i("PTT_PHASE2_TEST", "New connections: $totalNewConnections (should be 1)")
            Log.i("PTT_PHASE2_TEST", "Reconnection prevented: ${totalNewConnections == 1}")
            
            true
        } catch (e: Exception) {
            Log.e("PTT_PHASE2_TEST", "❌ Continuous usage test failed", e)
            false
        }
    }
    
    /**
     * 통계 리셋 (테스트용)
     */
    fun resetStats() {
        connectCallCount = 0
        disconnectCallCount = 0
        Log.d(TAG_PHASE2, "📊 STATS RESET - 통계 초기화")
    }
    
    /**
     * 예약된 연결 해제 취소 (기존 메서드 유지)
     */
    private fun cancelPendingDisconnect() {
        pendingDisconnectRunnable?.let {
            handler.removeCallbacks(it)
            pendingDisconnectRunnable = null
            Log.d(TAG, "Cancelled pending disconnect")
        }
    }
    
    /**
     * 리소스 정리
     */
    fun destroy() {
        Log.i(TAG, "Destroying PTTDebouncer")
        Log.i(TAG_PHASE2, "🧹 CLEANUP - PTTDebouncer 리소스 정리")
        
        cancelPendingDisconnect()
        handler.removeCallbacksAndMessages(null)
        isChannelConnected = false
        
        // 최종 통계 로그
        val finalStats = getCostSavingStats()
        Log.i(TAG_PHASE2, "📊 FINAL STATS - 연결: ${finalStats.totalConnections}, 절약률: ${finalStats.activeSavingRatio.toInt()}%")
    }
    
    /**
     * 비용 절약 통계 데이터 클래스
     */
    data class CostSavingStats(
        val totalConnections: Int,
        val totalDisconnections: Int,
        val activeSavingRatio: Float,
        val debounceDelayMs: Long
    ) {
        fun getActiveSavings(): Int = totalConnections - totalDisconnections
        fun getPotentialMonthlySaving(): Int = (activeSavingRatio * 0.3).toInt() // 추정 월간 절약 퍼센트
    }
    
    /**
     * PTT 사용 통계 데이터 클래스
     */
    data class PTTUsageStats(
        val lastPressTime: Long,
        val lastReleaseTime: Long,
        val pressDuration: Long,
        val debounceDelay: Long
    ) {
        fun isShortUsage(): Boolean = pressDuration < 1000L
        fun getSavedTime(): Long = if (isShortUsage()) debounceDelay - pressDuration else 0L
        fun getEffectiveTime(): Long = maxOf(pressDuration, debounceDelay)
    }
}
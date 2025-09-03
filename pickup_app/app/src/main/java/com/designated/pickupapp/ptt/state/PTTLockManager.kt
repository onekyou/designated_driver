package com.designated.pickupapp.ptt.state

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.util.Log

/**
 * PTT 충돌 방지를 위한 Lock Manager
 * RTM 메시지를 통해 다른 사용자의 PTT 상태를 추적하고 충돌 방지
 */
class PTTLockManager {
    private val TAG = "PTTLockManager"
    
    // 현재 PTT를 사용 중인 사용자 ID
    @Volatile
    private var currentTransmitterId: String? = null
    
    // 타임스탬프로 우선순위 결정
    @Volatile
    private var currentTransmitterTimestamp: Long = 0L
    
    // 동시 접근 방지를 위한 Mutex
    private val mutex = Mutex()
    
    /**
     * PTT 시작 가능 여부 확인
     * @param userId 요청한 사용자 ID
     * @param timestamp 요청 타임스탬프
     * @return true면 PTT 시작 가능, false면 다른 사용자가 사용 중
     */
    suspend fun canStartPTT(userId: String, timestamp: Long = System.currentTimeMillis()): Boolean {
        return mutex.withLock {
            when {
                // 아무도 사용 중이 아님
                currentTransmitterId == null -> {
                    Log.d(TAG, "PTT 사용 가능 - 아무도 사용 중 아님")
                    true
                }
                // 자신이 이미 사용 중
                currentTransmitterId == userId -> {
                    Log.d(TAG, "PTT 사용 가능 - 이미 자신이 사용 중")
                    true
                }
                // 타임아웃 체크 (10초 이상 응답 없으면 해제)
                System.currentTimeMillis() - currentTransmitterTimestamp > 10000 -> {
                    Log.w(TAG, "PTT 타임아웃 - 이전 사용자: $currentTransmitterId")
                    currentTransmitterId = null
                    currentTransmitterTimestamp = 0L
                    true
                }
                // 다른 사용자가 사용 중
                else -> {
                    Log.w(TAG, "PTT 사용 불가 - $currentTransmitterId 사용 중")
                    false
                }
            }
        }
    }
    
    /**
     * PTT 시작 등록
     * @param userId 사용자 ID
     * @param timestamp 시작 타임스탬프
     * @return true면 등록 성공, false면 실패 (다른 사용자가 사용 중)
     */
    suspend fun acquirePTTLock(userId: String, timestamp: Long = System.currentTimeMillis()): Boolean {
        return mutex.withLock {
            if (canStartPTTInternal(userId, timestamp)) {
                currentTransmitterId = userId
                currentTransmitterTimestamp = timestamp
                Log.i(TAG, "PTT Lock 획득: $userId at $timestamp")
                true
            } else {
                Log.w(TAG, "PTT Lock 획득 실패: $userId (현재 사용자: $currentTransmitterId)")
                false
            }
        }
    }
    
    /**
     * PTT 종료 등록
     * @param userId 사용자 ID
     */
    suspend fun releasePTTLock(userId: String) {
        mutex.withLock {
            if (currentTransmitterId == userId) {
                Log.i(TAG, "PTT Lock 해제: $userId")
                currentTransmitterId = null
                currentTransmitterTimestamp = 0L
            } else {
                Log.w(TAG, "PTT Lock 해제 실패 - 다른 사용자: 요청=$userId, 현재=$currentTransmitterId")
            }
        }
    }
    
    /**
     * 다른 사용자의 PTT 상태 업데이트 (RTM 메시지 수신 시)
     * @param userId 사용자 ID
     * @param isTransmitting 전송 중 여부
     * @param timestamp 타임스탬프
     */
    suspend fun updateRemoteUserStatus(
        userId: String,
        isTransmitting: Boolean,
        timestamp: Long = System.currentTimeMillis()
    ) {
        mutex.withLock {
            if (isTransmitting) {
                // 다른 사용자가 PTT 시작
                if (currentTransmitterId == null || timestamp < currentTransmitterTimestamp) {
                    // 아무도 없거나, 더 빠른 타임스탬프면 우선권
                    currentTransmitterId = userId
                    currentTransmitterTimestamp = timestamp
                    Log.d(TAG, "원격 사용자 PTT 시작: $userId")
                }
            } else {
                // 다른 사용자가 PTT 종료
                if (currentTransmitterId == userId) {
                    currentTransmitterId = null
                    currentTransmitterTimestamp = 0L
                    Log.d(TAG, "원격 사용자 PTT 종료: $userId")
                }
            }
        }
    }
    
    /**
     * 현재 PTT 사용자 확인
     * @return 현재 PTT 사용 중인 사용자 ID (없으면 null)
     */
    fun getCurrentTransmitter(): String? = currentTransmitterId
    
    /**
     * PTT 사용 가능 여부 (내부용)
     */
    private fun canStartPTTInternal(userId: String, timestamp: Long): Boolean {
        return when {
            currentTransmitterId == null -> true
            currentTransmitterId == userId -> true
            System.currentTimeMillis() - currentTransmitterTimestamp > 10000 -> {
                currentTransmitterId = null
                currentTransmitterTimestamp = 0L
                true
            }
            else -> false
        }
    }
    
    /**
     * 상태 초기화
     */
    suspend fun reset() {
        mutex.withLock {
            Log.d(TAG, "PTT Lock Manager 초기화")
            currentTransmitterId = null
            currentTransmitterTimestamp = 0L
        }
    }
}
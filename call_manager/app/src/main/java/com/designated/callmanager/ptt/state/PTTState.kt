package com.designated.callmanager.ptt.state

/**
 * PTT 시스템의 상태를 나타내는 sealed class
 * UI Layer와 Service Layer 간의 상태 동기화를 위해 사용
 */
sealed class PTTState {
    /**
     * PTT 시스템이 연결되지 않은 상태
     */
    object Disconnected : PTTState()
    
    /**
     * PTT 시스템이 연결 중인 상태
     */
    object Connecting : PTTState()
    
    /**
     * PTT 시스템이 채널에 연결된 상태
     * @param channel 연결된 채널명
     * @param uid 사용자의 고유 ID
     */
    data class Connected(
        val channel: String,
        val uid: Int
    ) : PTTState()
    
    /**
     * PTT 시스템에 오류가 발생한 상태
     * @param message 오류 메시지
     * @param code 오류 코드 (optional)
     */
    data class Error(
        val message: String,
        val code: Int? = null
    ) : PTTState()
    
    /**
     * 다른 사용자가 말하고 있는 상태
     * @param uid 말하는 사용자의 고유 ID
     * @param volume 음량 레벨 (0-255)
     */
    data class UserSpeaking(
        val uid: Int,
        val volume: Int
    ) : PTTState()
    
    /**
     * 본인이 PTT를 누르고 있는 상태
     * @param isTransmitting 전송 중 여부
     */
    data class Transmitting(
        val isTransmitting: Boolean
    ) : PTTState()
}

/**
 * PTT 명령을 나타내는 sealed class
 * UI에서 Service로 전달되는 명령
 */
sealed class PTTCommand {
    /**
     * PTT 초기화 명령
     */
    object Initialize : PTTCommand()
    
    /**
     * 채널 참여 명령
     * @param channel 참여할 채널명 (null이면 기본 채널)
     */
    data class JoinChannel(val channel: String? = null) : PTTCommand()
    
    /**
     * 채널 나가기 명령
     */
    object LeaveChannel : PTTCommand()
    
    /**
     * PTT 송신 시작 명령
     */
    object StartTransmit : PTTCommand()
    
    /**
     * PTT 송신 중지 명령
     */
    object StopTransmit : PTTCommand()
    
    /**
     * 자동 채널 참여 명령 (FCM으로부터)
     * @param channel 참여할 채널명
     * @param senderUid 요청한 사용자의 UID
     */
    data class AutoJoin(
        val channel: String,
        val senderUid: Int
    ) : PTTCommand()
    
    /**
     * 서비스 종료 명령
     */
    object Shutdown : PTTCommand()
}

/**
 * 토큰 요청 결과
 */
sealed class TokenResult {
    data class Success(
        val token: String,
        val uid: Int
    ) : TokenResult()
    
    data class Failure(
        val error: Throwable
    ) : TokenResult()
}

/**
 * PTT 이벤트 (Service에서 UI로 전달)
 */
sealed class PTTEvent {
    /**
     * 연결 성공 이벤트
     */
    data class ConnectionSuccess(
        val channel: String,
        val uid: Int
    ) : PTTEvent()
    
    /**
     * 연결 실패 이벤트
     */
    data class ConnectionFailed(
        val reason: String
    ) : PTTEvent()
    
    /**
     * 사용자 참여 이벤트
     */
    data class UserJoined(
        val uid: Int,
        val userName: String? = null
    ) : PTTEvent()
    
    /**
     * 사용자 나감 이벤트
     */
    data class UserLeft(
        val uid: Int
    ) : PTTEvent()
    
    /**
     * 네트워크 품질 변경 이벤트
     */
    data class NetworkQualityChanged(
        val quality: NetworkQuality
    ) : PTTEvent()
}

/**
 * 네트워크 품질 상태
 */
enum class NetworkQuality {
    EXCELLENT,
    GOOD,
    POOR,
    BAD,
    VERY_BAD,
    DOWN
}
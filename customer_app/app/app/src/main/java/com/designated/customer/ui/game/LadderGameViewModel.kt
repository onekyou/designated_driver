package com.designated.customer.ui.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * 사다리 게임 상태
 */
enum class LadderGameState {
    SETUP,          // 참가자 설정
    READY,          // 준비 완료
    FLIPPING,       // 사다리 뒤집는 중
    SETTING_PRIZES, // 술잔/카드 설정
    ANIMATING,      // 사다리 애니메이션
    RESULT          // 결과 표시
}

/**
 * 게임 모드
 */
enum class GameMode {
    DRINK,          // 사다리 술게임
    BILL_PAYMENT    // 술값 내기
}

/**
 * 사다리 게임 UI 상태
 */
data class LadderGameUiState(
    val gameMode: GameMode = GameMode.DRINK,
    val gameState: LadderGameState = LadderGameState.SETUP,
    val playerCount: Int = 2,
    val selectedPositions: List<Int> = emptyList(),
    val prizePositions: List<Int> = emptyList(), // 술이 담긴 잔 위치 (or 당첨 카드)
    val ladderPaths: List<Int> = emptyList(), // 각 시작 위치의 최종 도착 위치
    val currentAnimatingPath: Int = -1,
    val winners: List<Int> = emptyList(),
    val isFlipped: Boolean = false,
    // 술값 내기 전용
    val timerMinutes: Int = 60,
    val winnerCount: Int = 1, // 예선 통과 인원
    val isPlayoffMode: Boolean = false // 결선 모드
)

class LadderGameViewModel : ViewModel() {

    var uiState by mutableStateOf(LadderGameUiState())
        private set

    /**
     * 게임 모드 설정
     */
    fun setGameMode(mode: GameMode) {
        uiState = LadderGameUiState(gameMode = mode)
    }

    /**
     * 참가 인원 설정
     */
    fun setPlayerCount(count: Int) {
        if (count in 2..10) {
            uiState = uiState.copy(
                playerCount = count,
                selectedPositions = emptyList(),
                prizePositions = emptyList()
            )
        }
    }

    /**
     * 타이머 설정 (분 단위)
     */
    fun setTimerMinutes(minutes: Int) {
        if (minutes > 0) {
            uiState = uiState.copy(timerMinutes = minutes)
        }
    }

    /**
     * 예선 통과 인원 설정 (술값 내기 모드)
     */
    fun setWinnerCount(count: Int) {
        if (count in 1 until uiState.playerCount) {
            uiState = uiState.copy(winnerCount = count)
        }
    }

    /**
     * 레디 버튼 클릭 - 사다리 뒤집기
     */
    fun onReadyClicked() {
        viewModelScope.launch {
            // 사다리 뒤집기 애니메이션
            uiState = uiState.copy(gameState = LadderGameState.FLIPPING)
            delay(800)

            // 사다리 경로 생성
            val paths = generateLadderPaths(uiState.playerCount)

            uiState = uiState.copy(
                gameState = LadderGameState.SETTING_PRIZES,
                isFlipped = true,
                ladderPaths = paths
            )
        }
    }

    /**
     * 술잔/카드 위치 선택 토글
     */
    fun togglePrizePosition(position: Int) {
        val currentPrizes = uiState.prizePositions.toMutableList()

        if (position in currentPrizes) {
            currentPrizes.remove(position)
        } else {
            currentPrizes.add(position)
        }

        uiState = uiState.copy(prizePositions = currentPrizes)
    }

    /**
     * 스타트 버튼 클릭 - 게임 시작
     */
    fun onStartClicked() {
        viewModelScope.launch {
            uiState = uiState.copy(gameState = LadderGameState.ANIMATING)

            // 각 경로를 순차적으로 애니메이션
            for (i in 0 until uiState.playerCount) {
                uiState = uiState.copy(currentAnimatingPath = i)
                delay(500)
            }

            // 당첨자 찾기
            val winners = findWinners()

            uiState = uiState.copy(
                gameState = LadderGameState.RESULT,
                winners = winners,
                currentAnimatingPath = -1
            )
        }
    }

    /**
     * 결선 시작 (술값 내기 모드)
     */
    fun startPlayoff() {
        val playoffPlayers = uiState.winners.size

        uiState = LadderGameUiState(
            gameMode = GameMode.BILL_PAYMENT,
            gameState = LadderGameState.SETUP,
            playerCount = playoffPlayers,
            winnerCount = 1,
            isPlayoffMode = true
        )
    }

    /**
     * 게임 리셋
     */
    fun resetGame() {
        uiState = LadderGameUiState(gameMode = uiState.gameMode)
    }

    /**
     * 사다리 경로 생성 알고리즘
     * 각 시작 위치에서 최종 도착 위치를 랜덤하게 결정
     */
    private fun generateLadderPaths(playerCount: Int): List<Int> {
        val destinations = (0 until playerCount).toMutableList()
        destinations.shuffle()
        return destinations
    }

    /**
     * 당첨자 찾기
     * prizePositions에 있는 위치로 도착하는 시작 위치를 찾음
     */
    private fun findWinners(): List<Int> {
        val winners = mutableListOf<Int>()

        for (startPos in 0 until uiState.playerCount) {
            val endPos = uiState.ladderPaths[startPos]
            if (endPos in uiState.prizePositions) {
                winners.add(startPos)
            }
        }

        return winners
    }

    /**
     * 타이머 시작 (술값 내기 모드)
     */
    fun startTimer() {
        viewModelScope.launch {
            val totalSeconds = uiState.timerMinutes * 60

            // 타이머가 끝날 때까지 대기
            delay(totalSeconds * 1000L)

            // 자동으로 게임 시작
            onStartClicked()
        }
    }
}

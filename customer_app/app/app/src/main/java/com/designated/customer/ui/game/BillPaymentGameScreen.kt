package com.designated.customer.ui.game

import androidx.compose.animation.core.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillPaymentGameScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LadderGameViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.setGameMode(GameMode.BILL_PAYMENT)
    }

    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isPlayoffMode) "술값 내기 - 결선"
                        else "술값 내기"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (uiState.gameState) {
                LadderGameState.SETUP -> {
                    BillPaymentSetupSection(
                        playerCount = uiState.playerCount,
                        timerMinutes = uiState.timerMinutes,
                        winnerCount = uiState.winnerCount,
                        isPlayoffMode = uiState.isPlayoffMode,
                        onPlayerCountChange = viewModel::setPlayerCount,
                        onTimerChange = viewModel::setTimerMinutes,
                        onWinnerCountChange = viewModel::setWinnerCount,
                        onReadyClick = viewModel::onReadyClicked
                    )
                }

                LadderGameState.FLIPPING -> {
                    FlippingAnimation()
                }

                LadderGameState.SETTING_PRIZES -> {
                    CardSettingSection(
                        playerCount = uiState.playerCount,
                        selectedCards = uiState.prizePositions,
                        winnerCount = uiState.winnerCount,
                        onCardToggle = viewModel::togglePrizePosition,
                        onStartClick = {
                            viewModel.onStartClicked()
                            viewModel.startTimer()
                        },
                        canStart = uiState.prizePositions.size == uiState.winnerCount,
                        timerMinutes = uiState.timerMinutes
                    )
                }

                LadderGameState.ANIMATING -> {
                    TimerAnimationSection(
                        playerCount = uiState.playerCount,
                        timerMinutes = uiState.timerMinutes,
                        ladderPaths = uiState.ladderPaths,
                        prizePositions = uiState.prizePositions
                    )
                }

                LadderGameState.RESULT -> {
                    BillPaymentResultSection(
                        winners = uiState.winners,
                        isPlayoffMode = uiState.isPlayoffMode,
                        onPlayAgain = viewModel::resetGame,
                        onStartPlayoff = viewModel::startPlayoff
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun BillPaymentSetupSection(
    playerCount: Int,
    timerMinutes: Int,
    winnerCount: Int,
    isPlayoffMode: Boolean,
    onPlayerCountChange: (Int) -> Unit,
    onTimerChange: (Int) -> Unit,
    onWinnerCountChange: (Int) -> Unit,
    onReadyClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isPlayoffMode) "결선 설정" else "게임 설정",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 참가 인원 설정
        if (!isPlayoffMode) {
            Text(text = "참가 인원", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { if (playerCount > 2) onPlayerCountChange(playerCount - 1) }) {
                    Text("-")
                }

                Card(
                    modifier = Modifier.width(100.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "${playerCount}명",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Button(onClick = { if (playerCount < 10) onPlayerCountChange(playerCount + 1) }) {
                    Text("+")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 예선 통과 인원 설정
            Text(text = "예선 통과 인원", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { if (winnerCount > 1) onWinnerCountChange(winnerCount - 1) }) {
                    Text("-")
                }

                Card(
                    modifier = Modifier.width(100.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Text(
                        text = "${winnerCount}명",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Button(onClick = {
                    if (winnerCount < playerCount - 1) onWinnerCountChange(winnerCount + 1)
                }) {
                    Text("+")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 타이머 설정
            Text(text = "타이머 (분)", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(onClick = { if (timerMinutes > 1) onTimerChange(timerMinutes - 1) }) {
                    Text("-")
                }

                Card(
                    modifier = Modifier.width(120.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Text(
                        text = "${timerMinutes}분",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }

                Button(onClick = { if (timerMinutes < 180) onTimerChange(timerMinutes + 1) }) {
                    Text("+")
                }
            }
        } else {
            Text(
                text = "결선은 즉시 진행됩니다",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onReadyClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("준비 완료", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FlippingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "flip")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .size(200.dp)
                .rotate(rotation),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "사다리 준비 중...",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CardSettingSection(
    playerCount: Int,
    selectedCards: List<Int>,
    winnerCount: Int,
    onCardToggle: (Int) -> Unit,
    onStartClick: () -> Unit,
    canStart: Boolean,
    timerMinutes: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "당첨 카드를 선택하세요",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = "(정확히 ${winnerCount}개 선택)",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 카드 목록
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0 until playerCount) {
                CardButton(
                    position = i,
                    isSelected = i in selectedCards,
                    onClick = { onCardToggle(i) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        if (canStart) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "${timerMinutes}분 후에 자동으로 결과가 나옵니다",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Button(
            onClick = onStartClick,
            enabled = canStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("스타트", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CardButton(
    position: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedCard(
            onClick = onClick,
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(
                2.dp,
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            ),
            colors = CardDefaults.outlinedCardColors(
                containerColor = if (isSelected)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surface
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "카드",
                    modifier = Modifier.size(32.dp),
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${position + 1}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TimerAnimationSection(
    playerCount: Int,
    timerMinutes: Int,
    ladderPaths: List<Int>,
    prizePositions: List<Int>
) {
    var remainingSeconds by remember { mutableStateOf(timerMinutes * 60) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "타이머 진행 중...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // 타이머 표시
        Card(
            modifier = Modifier
                .size(200.dp)
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = CircleShape
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${remainingSeconds / 60}:${String.format("%02d", remainingSeconds % 60)}",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "남음",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // 사다리 시각화
        LadderVisualizationWithTimer(
            playerCount = playerCount,
            prizePositions = prizePositions
        )
    }
}

@Composable
private fun LadderVisualizationWithTimer(
    playerCount: Int,
    prizePositions: List<Int>
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .padding(16.dp)
    ) {
        val spacing = size.width / (playerCount + 1)
        val lineColor = Color.Gray

        // 세로 라인 그리기
        for (i in 0 until playerCount) {
            val x = spacing * (i + 1)
            drawLine(
                color = lineColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 4f
            )
        }

        // 가로 라인 그리기
        val horizontalLineCount = playerCount * 2
        for (i in 0 until horizontalLineCount) {
            val startX = spacing * (i % (playerCount - 1) + 1)
            val endX = spacing * (i % (playerCount - 1) + 2)
            val y = size.height * (i + 1) / (horizontalLineCount + 1)

            drawLine(
                color = lineColor,
                start = Offset(startX, y),
                end = Offset(endX, y),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun BillPaymentResultSection(
    winners: List<Int>,
    isPlayoffMode: Boolean,
    onPlayAgain: () -> Unit,
    onStartPlayoff: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        if (isPlayoffMode) {
            // 결선 결과 - 최종 당첨자
            Text(
                text = "술값 지불자!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(24.dp))

            winners.forEach { winner ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = "${winner + 1}번 위치",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onPlayAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("다시 하기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        } else {
            // 예선 결과
            Text(
                text = "예선 통과!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            winners.forEach { winner ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = "${winner + 1}번 위치",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 결선 시작 버튼
            Button(
                onClick = onStartPlayoff,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("결선 시작", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onPlayAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("처음부터 다시", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

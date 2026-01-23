package com.designated.customer.ui.game

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LadderDrinkGameScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LadderGameViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.setGameMode(GameMode.DRINK)
    }

    val uiState = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사다리 술게임") },
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
                    SetupSection(
                        playerCount = uiState.playerCount,
                        onPlayerCountChange = viewModel::setPlayerCount,
                        onReadyClick = viewModel::onReadyClicked
                    )
                }

                LadderGameState.FLIPPING -> {
                    FlippingAnimation()
                }

                LadderGameState.SETTING_PRIZES -> {
                    PrizeSettingSection(
                        playerCount = uiState.playerCount,
                        selectedPrizes = uiState.prizePositions,
                        onPrizeToggle = viewModel::togglePrizePosition,
                        onStartClick = viewModel::onStartClicked,
                        canStart = uiState.prizePositions.isNotEmpty()
                    )
                }

                LadderGameState.ANIMATING -> {
                    LadderAnimationSection(
                        playerCount = uiState.playerCount,
                        ladderPaths = uiState.ladderPaths,
                        currentAnimatingPath = uiState.currentAnimatingPath,
                        prizePositions = uiState.prizePositions
                    )
                }

                LadderGameState.RESULT -> {
                    ResultSection(
                        winners = uiState.winners,
                        onPlayAgain = viewModel::resetGame
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
private fun SetupSection(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    onReadyClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "참가 인원을 선택하세요",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 인원 선택 (2~10명)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { if (playerCount > 2) onPlayerCountChange(playerCount - 1) }) {
                Text("-")
            }

            Card(
                modifier = Modifier.width(120.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "${playerCount}명",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Button(onClick = { if (playerCount < 10) onPlayerCountChange(playerCount + 1) }) {
                Text("+")
            }
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
private fun PrizeSettingSection(
    playerCount: Int,
    selectedPrizes: List<Int>,
    onPrizeToggle: (Int) -> Unit,
    onStartClick: () -> Unit,
    canStart: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "술을 채울 잔을 선택하세요",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        Text(
            text = "(여러 개 선택 가능)",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 소주잔 목록
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            for (i in 0 until playerCount) {
                SojuGlassButton(
                    position = i,
                    isSelected = i in selectedPrizes,
                    onClick = { onPrizeToggle(i) }
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

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
private fun SojuGlassButton(
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
            shape = CircleShape,
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
                    Icons.Default.Star,
                    contentDescription = "소주잔",
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
private fun LadderAnimationSection(
    playerCount: Int,
    ladderPaths: List<Int>,
    currentAnimatingPath: Int,
    prizePositions: List<Int>
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = "사다리 타는 중...",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // 간단한 사다리 시각화
        LadderVisualization(
            playerCount = playerCount,
            ladderPaths = ladderPaths,
            currentAnimatingPath = currentAnimatingPath,
            prizePositions = prizePositions
        )
    }
}

@Composable
private fun LadderVisualization(
    playerCount: Int,
    ladderPaths: List<Int>,
    currentAnimatingPath: Int,
    prizePositions: List<Int>
) {
    val animatedProgress by animateFloatAsState(
        targetValue = if (currentAnimatingPath >= 0) 1f else 0f,
        animationSpec = tween(500),
        label = "progress"
    )

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

        // 가로 라인 그리기 (랜덤 위치)
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
private fun ResultSection(
    winners: List<Int>,
    onPlayAgain: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "당첨!",
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

        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("다시 하기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

package com.designated.customer.ui.game

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrinkingGameMenuScreen(
    onNavigateToLadderDrink: () -> Unit,
    onNavigateToBillPayment: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("술게임") },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "게임 모드를 선택하세요",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            // 사다리 술게임 버튼
            GameModeCard(
                icon = Icons.Default.Star,
                title = "사다리 술게임",
                description = "즉시 사다리를 타서 술을 마실 사람을 결정합니다",
                onClick = onNavigateToLadderDrink
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 술값 내기 게임 버튼
            GameModeCard(
                icon = Icons.Default.Person,
                title = "술값 내기",
                description = "타이머를 설정하고 시간이 끝나면 술값을 낼 사람을 결정합니다",
                onClick = onNavigateToBillPayment
            )
        }
    }
}

@Composable
private fun GameModeCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = description,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

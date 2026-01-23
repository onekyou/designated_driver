package com.designated.callmanager.ui.settlement

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 새 정산관리 메인 화면.
 * 탭만 담당하고 실제 화면은 Stub(추후 작성).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementTabHost(
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    val pages = listOf("전체", "대기", "기사별", "일일", "외상")
    var selected by remember { mutableStateOf(0) }

    // 활성 탭: 밝은 노랑, 비활성 탭: 채도 낮은 색상
    val activeColor = Color(0xFFFFB000)       // 밝은 노랑/주황
    val inactiveTextColor = Color(0xFF888888) // 채도 낮은 회색

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("정산관리") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Filled.Home, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1A1A1A), titleContentColor = Color.White)
            )
        },
        containerColor = Color(0xFF121212)
    ) { pad ->
        Column(Modifier.padding(pad)) {
            TabRow(
                selectedTabIndex = selected,
                containerColor = Color(0xFF2A2A2A),
                contentColor = activeColor,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selected]),
                        color = activeColor
                    )
                }
            ) {
                pages.forEachIndexed { i, t ->
                    val isSelected = selected == i
                    Tab(
                        selected = isSelected,
                        onClick = { selected = i },
                        text = {
                            Text(
                                t,
                                color = if (isSelected) activeColor else inactiveTextColor
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            when (selected) {
                0 -> com.designated.callmanager.ui.settlement.screen.AllTripsScreen()
                1 -> com.designated.callmanager.ui.settlement.screen.PendingSettlementsScreen()
                2 -> com.designated.callmanager.ui.settlement.screen.DriverSummaryScreen()
                3 -> com.designated.callmanager.ui.settlement.screen.DailySessionScreen()
                4 -> com.designated.callmanager.ui.settlement.screen.CreditManagementScreen()
            }
        }
    }
}


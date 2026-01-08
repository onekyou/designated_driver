package com.designated.customer.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.designated.customer.ui.main.HomeScreen
import com.designated.customer.ui.history.CallHistoryScreen
import com.designated.customer.ui.point.PointScreen
import com.designated.customer.ui.profile.ProfileScreen
// 술게임 관련 import 주석처리
// import com.designated.customer.ui.game.DrinkingGameMenuScreen
// import com.designated.customer.ui.game.LadderDrinkGameScreen
// import com.designated.customer.ui.game.BillPaymentGameScreen

enum class NavTab(
    val title: String,
    val icon: ImageVector,
    val route: String
) {
    HOME("홈", Icons.Default.Home, "home"),
    HISTORY("이용내역", Icons.Default.List, "history"),
    POINTS("포인트", Icons.Default.Star, "points"),
    PROFILE("내정보", Icons.Default.Person, "profile")
}

// 게임 화면 라우트 - 주석처리
/*
enum class GameRoute {
    NONE,
    MENU,
    LADDER_DRINK,
    BILL_PAYMENT
}
*/

@Composable
fun MainNavigation(
    regionId: String,
    officeId: String,
    phoneNumber: String,
    customerInfo: com.designated.customer.data.model.CustomerInfo?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }
    // var gameRoute by remember { mutableStateOf(GameRoute.NONE) }  // 게임 라우트 주석처리

    // 알림 클릭 시 HOME 탭으로 강제 이동하기 위한 브로드캐스트 수신
    val context = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                // 알림 클릭 시 HOME 탭으로 이동
                selectedTab = NavTab.HOME
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction("com.designated.customer.NAVIGATE_TO_HOME")
        }
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
            .registerReceiver(receiver, filter)

        onDispose {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context)
                .unregisterReceiver(receiver)
        }
    }

    // 백버튼 핸들링: 홈 탭이 아닐 때 홈으로 이동
    BackHandler(enabled = selectedTab != NavTab.HOME) {
        selectedTab = NavTab.HOME
    }

    /* 게임 화면 네비게이션 - 주석처리
    // 백버튼 핸들링: 게임 화면 -> 홈, 홈 탭이 아닐 때 홈으로 이동
    BackHandler(enabled = gameRoute != GameRoute.NONE || selectedTab != NavTab.HOME) {
        if (gameRoute != GameRoute.NONE) {
            gameRoute = GameRoute.NONE
        } else {
            selectedTab = NavTab.HOME
        }
    }

    // 게임 화면이 열려있을 때는 게임 화면 표시
    if (gameRoute != GameRoute.NONE) {
        when (gameRoute) {
            GameRoute.MENU -> {
                DrinkingGameMenuScreen(
                    onNavigateToLadderDrink = { gameRoute = GameRoute.LADDER_DRINK },
                    onNavigateToBillPayment = { gameRoute = GameRoute.BILL_PAYMENT },
                    onBackClick = { gameRoute = GameRoute.NONE }
                )
            }
            GameRoute.LADDER_DRINK -> {
                LadderDrinkGameScreen(
                    onBackClick = { gameRoute = GameRoute.MENU }
                )
            }
            GameRoute.BILL_PAYMENT -> {
                BillPaymentGameScreen(
                    onBackClick = { gameRoute = GameRoute.MENU }
                )
            }
            else -> {}
        }
    } else {
    */
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavTab.values().forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        ) { paddingValues ->
            when (selectedTab) {
                NavTab.HOME -> {
                    HomeScreen(
                        modifier = Modifier.padding(paddingValues),
                        regionId = regionId,
                        officeId = officeId,
                        phoneNumber = phoneNumber,
                        customerInfo = customerInfo,
                        onNavigateToDrinkingGame = { /* 게임 네비게이션 비활성화 */ }
                    )
                }
                NavTab.HISTORY -> {
                    CallHistoryScreen(
                        phoneNumber = phoneNumber,
                        regionId = regionId,
                        officeId = officeId,
                        onBackClick = { selectedTab = NavTab.HOME },
                        showBackButton = false,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                NavTab.POINTS -> {
                    PointScreen(
                        phoneNumber = phoneNumber,
                        regionId = regionId,
                        officeId = officeId,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
                NavTab.PROFILE -> {
                    ProfileScreen(
                        phoneNumber = phoneNumber,
                        regionId = regionId,
                        officeId = officeId,
                        onLogout = onLogout,
                        modifier = Modifier.padding(paddingValues)
                    )
                }
            }
        }
    // } // 게임 화면 else 블록 종료 주석 - 주석처리됨
}
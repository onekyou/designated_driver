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


@Composable
fun MainNavigation(
    provinceId: String,
    cityId: String,
    officeId: String,
    phoneNumber: String,
    customerInfo: com.designated.customer.data.model.CustomerInfo?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

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
                    provinceId = provinceId,
                    cityId = cityId,
                    officeId = officeId,
                    phoneNumber = phoneNumber,
                    customerInfo = customerInfo
                )
            }
            NavTab.HISTORY -> {
                CallHistoryScreen(
                    phoneNumber = phoneNumber,
                    provinceId = provinceId,
                    cityId = cityId,
                    officeId = officeId,
                    onBackClick = { selectedTab = NavTab.HOME },
                    showBackButton = false,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            NavTab.POINTS -> {
                PointScreen(
                    phoneNumber = phoneNumber,
                    provinceId = provinceId,
                    cityId = cityId,
                    officeId = officeId,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            NavTab.PROFILE -> {
                ProfileScreen(
                    phoneNumber = phoneNumber,
                    provinceId = provinceId,
                    cityId = cityId,
                    officeId = officeId,
                    onLogout = onLogout,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
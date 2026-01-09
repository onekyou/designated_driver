package com.designated.callmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import com.designated.callmanager.ui.login.LoginScreen
import com.designated.callmanager.ui.main.MainDashboardScreen
import com.designated.callmanager.ui.pendingdrivers.PendingDriversScreen

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object MainDashboard : Screen("main_dashboard")
    object PendingDrivers : Screen("pending_drivers")
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginComplete = { _, _, _ ->
                    navController.navigate(Screen.MainDashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { /* TODO: 회원가입 화면 이동 로직 */ },
                onNavigateToPasswordReset = { /* TODO: 비밀번호 재설정 화면 이동 로직 */ }
            )
        }
        composable(Screen.MainDashboard.route) {
             MainDashboardScreen(
                 onNavigateToPendingDrivers = {
                     navController.navigate(Screen.PendingDrivers.route)
                 },
             )
        }
        composable(Screen.PendingDrivers.route) {
             PendingDriversScreen(
                 provinceId = "default", // TODO: 실제 provinceId 전달
                 cityId = "default", // TODO: 실제 cityId 전달
                 officeId = "default", // TODO: 실제 officeId 전달
                 onNavigateBack = { navController.popBackStack() }
             )
        }
    }
}
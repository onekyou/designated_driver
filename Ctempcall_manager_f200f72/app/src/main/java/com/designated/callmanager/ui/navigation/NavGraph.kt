package com.designated.callmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import com.designated.callmanager.ui.login.LoginScreen // 가정: 로그인 화면
import com.designated.callmanager.ui.main.MainDashboardScreen // 가정: 메인 대시보드 화면
import com.designated.callmanager.ui.pendingdrivers.PendingDriversScreen

// 네비게이션 라우트 정의
sealed class Screen(val route: String) {
    object Login : Screen("login")
    object MainDashboard : Screen("main_dashboard")
    object PendingDrivers : Screen("pending_drivers")
    // 다른 화면 라우트 추가...
}

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Login.route // 앱 시작 화면
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Login.route) {
            // 로그인 화면 구현
            LoginScreen(
                onLoginComplete = { _, _ ->
                    // 로그인 성공 시 메인 대시보드로 이동 (기존 스택 지우고)
                    navController.navigate(Screen.MainDashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onNavigateToSignUp = { /* TODO: 회원가입 화면 이동 로직 */ },
                onNavigateToPasswordReset = { /* TODO: 비밀번호 재설정 화면 이동 로직 */ }
            )
        }
        composable(Screen.MainDashboard.route) {
            // 메인 대시보드 화면 구현
             MainDashboardScreen(
                 onNavigateToPendingDrivers = {
                     navController.navigate(Screen.PendingDrivers.route)
                 },
                 // CallManagement 제거됨
                 // 다른 네비게이션 액션 추가...
             )
        }
        composable(Screen.PendingDrivers.route) {
             // 기사 가입 승인 화면
             PendingDriversScreen(
                 // ViewModel은 여기서 주입되거나 Hilt 등을 통해 주입될 수 있음
                 // viewModel = hiltViewModel() 또는 viewModel()
                 onNavigateBack = { navController.popBackStack() } // 뒤로가기
             )
        }
        // 실시간 호출 관리 화면은 제거됨
        // 다른 화면 composable 추가...
    }
} 
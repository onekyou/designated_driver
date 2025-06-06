package com.designated.driverapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.designated.driverapp.ui.login.LoginScreen
import com.designated.driverapp.ui.login.ForgotPasswordScreen
import com.designated.driverapp.ui.home.HomeScreen // Import the HomeScreen

// Define navigation routes
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val FORGOT_PASSWORD_ROUTE = "forgot_password"
    // Add other destinations here
}

@Composable
fun AppNavigation(
    startDestination: String = AppDestinations.LOGIN_ROUTE // 기본값은 로그인 화면
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onLoginSuccess = {
                    // Navigate to home and clear back stack
                    navController.navigate(AppDestinations.HOME_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                        launchSingleTop = true // Avoid multiple copies of home screen
                    }
                },
                onNavigateToSignUp = { /* TODO: Handle sign up navigation if needed */ },
                onNavigateToPasswordReset = { 
                    navController.navigate(AppDestinations.FORGOT_PASSWORD_ROUTE)
                }
            )
        }
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(navController = navController) // NavController 전달
        }
        composable(AppDestinations.FORGOT_PASSWORD_ROUTE) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onResetSuccess = { navController.popBackStack() }
            )
        }
        // Add other composables for different screens here
    }
} 
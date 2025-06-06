package com.designated.driverapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.designated.driverapp.ui.login.LoginScreen
import com.designated.driverapp.ui.login.ForgotPasswordScreen
import com.designated.driverapp.ui.home.HomeScreen
import com.designated.driverapp.ui.login.SignUpScreen
import com.designated.driverapp.ui.home.DriverViewModel
import com.designated.driverapp.ui.home.HistorySettlementScreen

// Define navigation routes
object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val FORGOT_PASSWORD_ROUTE = "forgot_password"
    const val SIGNUP_ROUTE = "signup"
    const val HISTORY_SETTLEMENT_ROUTE = "history_settlement"
}

@Composable
fun AppNavigation(
    startDestination: String
) {
    val navController = rememberNavController()
    val driverViewModel: DriverViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                onLoginSuccess = { regionId, officeId, driverId ->
                    driverViewModel.initializeListenersWithInfo(regionId, officeId, driverId)
                    navController.navigate(AppDestinations.HOME_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate(AppDestinations.SIGNUP_ROUTE)
                },
                onNavigateToPasswordReset = {
                    navController.navigate(AppDestinations.FORGOT_PASSWORD_ROUTE)
                }
            )
        }
        composable(AppDestinations.HOME_ROUTE) {
            HomeScreen(navController, driverViewModel)
        }
        composable(AppDestinations.FORGOT_PASSWORD_ROUTE) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onResetSuccess = { navController.popBackStack() }
            )
        }
        composable(AppDestinations.SIGNUP_ROUTE) {
            SignUpScreen(
                onSignUpSuccess = {
                    navController.navigate(AppDestinations.LOGIN_ROUTE) {
                        popUpTo(AppDestinations.LOGIN_ROUTE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        composable(AppDestinations.HISTORY_SETTLEMENT_ROUTE) {
            HistorySettlementScreen(
                navController = navController,
                viewModel = driverViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        // Add other composables for different screens here
    }
} 
package com.designated.driverapp.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.navArgument
import com.designated.driverapp.ui.login.LoginScreen
import com.designated.driverapp.ui.login.ForgotPasswordScreen
import com.designated.driverapp.ui.home.HomeScreen
import com.designated.driverapp.ui.login.SignUpScreen
import com.designated.driverapp.viewmodel.DriverViewModel
import com.designated.driverapp.ui.home.HistorySettlementScreen
import com.designated.driverapp.ui.details.CallDetailsScreen
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

object AppDestinations {
    const val LOGIN_ROUTE = "login"
    const val HOME_ROUTE = "home"
    const val FORGOT_PASSWORD_ROUTE = "forgot_password"
    const val SIGNUP_ROUTE = "signup"
    const val HISTORY_SETTLEMENT_ROUTE = "history_settlement"
    const val CALL_DETAILS_ROUTE = "call_details"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    driverViewModel: DriverViewModel
) {
    val uiState by driverViewModel.uiState.collectAsState()

    LaunchedEffect(uiState.newCallPopup, uiState.navigateToHome) {
        if (uiState.newCallPopup != null && uiState.navigateToHome) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute == AppDestinations.HISTORY_SETTLEMENT_ROUTE) {
                navController.navigate(AppDestinations.HOME_ROUTE) {
                    launchSingleTop = true
                }
            }
            driverViewModel.onNavigateToHomeHandled()
        }
    }

    LaunchedEffect(uiState.navigateToHistorySettlement) {
        if (uiState.navigateToHistorySettlement) {
            navController.navigate(AppDestinations.HISTORY_SETTLEMENT_ROUTE) {
                launchSingleTop = true
            }
            driverViewModel.onNavigateToHistorySettlementHandled()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                driverViewModel = driverViewModel,
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

        composable(
            route = "${AppDestinations.CALL_DETAILS_ROUTE}/{callId}",
            arguments = listOf(navArgument("callId") { type = NavType.StringType })
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId")
            if (callId != null) {
                CallDetailsScreen(navController = navController, viewModel = driverViewModel, callId = callId)
            }
        }
    }
}
package com.designated.driverapp.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.navArgument
import com.designated.driverapp.ui.login.LoginScreen
import com.designated.driverapp.ui.login.ForgotPasswordScreen
import com.designated.driverapp.ui.home.HomeScreen
import com.designated.driverapp.ui.chat.ChatViewModel
import com.designated.driverapp.ui.chat.HomeScreenWithChatSheet
import com.designated.driverapp.ui.login.SignUpScreen
import com.designated.driverapp.viewmodel.DriverViewModel
import com.designated.driverapp.model.DriverStatus
import com.designated.driverapp.enforcement.DailyCloseRedirect
import com.designated.driverapp.ui.home.HistorySettlementScreen
import com.designated.driverapp.ui.home.SettingsScreen
import com.designated.driverapp.ui.details.CallDetailsScreen
import com.designated.driverapp.ui.screens.home.ReferralQRScreen
import com.designated.driverapp.data.Constants
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
    const val REFERRAL_QR_ROUTE = "referral_qr"
    const val SETTINGS_ROUTE = "settings"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    driverViewModel: DriverViewModel,
) {
    val uiState by driverViewModel.uiState.collectAsState()

    LaunchedEffect(uiState.navigateToHome) {
        if (uiState.navigateToHome) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != AppDestinations.HOME_ROUTE) {
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

    // P7 게이트 #3 DailyCloseGate — 이전 영업일 미마감이 남아 있으면 정산화면으로 강제 이동.
    // needsDailyClose(이전 영업일 잔존, VM 계산) + 아직 제출 전(PENDING_CONFIRM 아님)일 때만.
    val needsDailyClose by driverViewModel.needsDailyClose.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    DailyCloseRedirect(
        needsDailyClose = needsDailyClose && uiState.driverStatus != DriverStatus.PENDING_CONFIRM,
        isOnSettlementRoute = currentRoute == AppDestinations.HISTORY_SETTLEMENT_ROUTE,
        onRedirect = { driverViewModel.requestNavigateToSettlement() }
    )

    NavHost(navController = navController, startDestination = startDestination) {
        composable(AppDestinations.LOGIN_ROUTE) {
            LoginScreen(
                driverViewModel = driverViewModel,
                onLoginSuccess = { provinceId, cityId, officeId, driverId ->
                    driverViewModel.initializeListenersWithInfo(provinceId, cityId, officeId, driverId)
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
            // ChatViewModel 인스턴스화를 home route 진입 시점으로 늦춤 (Auth 복원 race 회피).
            // Activity scope 명시 — MainActivity 의 by viewModels() 와 동일 ViewModelStoreOwner → 같은 인스턴스 보장.
            val activity = LocalContext.current as ComponentActivity
            val chatViewModel: ChatViewModel = hiltViewModel(activity)
            HomeScreenWithChatSheet(navController, driverViewModel, chatViewModel)
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
            // 뒤로가기 처리는 HistorySettlementScreen 내부 단일 BackHandler에서 needsDailyClose 조건부로 수행.
            // (이전: 여기 무동작 BackHandler + 화면 내부 popBackStack BackHandler 이중등록 → 충돌로 게이트 무력화/왕복)
            HistorySettlementScreen(
                navController = navController,
                viewModel = driverViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(AppDestinations.SETTINGS_ROUTE) {
            SettingsScreen(
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

        composable(AppDestinations.REFERRAL_QR_ROUTE) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val provinceId = prefs.getString(Constants.PREF_KEY_PROVINCE_ID, "") ?: ""
            val cityId = prefs.getString(Constants.PREF_KEY_CITY_ID, "") ?: ""
            val officeId = prefs.getString(Constants.PREF_KEY_OFFICE_ID, "") ?: ""
            val driverId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

            ReferralQRScreen(
                driverId = driverId,
                provinceId = provinceId,
                cityId = cityId,
                officeId = officeId,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
package com.designated.driverapp.ui.chat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.designated.driverapp.ui.home.HomeScreen
import com.designated.driverapp.viewmodel.DriverViewModel
import kotlinx.coroutines.launch

/**
 * HomeScreen 위에 사무실 단톡방 BottomSheet를 동거시키는 래퍼.
 * call_manager `DashboardWithChatSheet` 패턴 그대로 이식.
 *
 * V1: peek + Expanded 2-state. Material 3 BottomSheetScaffold 기본.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenWithChatSheet(
    navController: NavHostController,
    driverViewModel: DriverViewModel,
    chatViewModel: ChatViewModel,
) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetTargetValue = scaffoldState.bottomSheetState.targetValue
    val isExpanded = sheetTargetValue == SheetValue.Expanded
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val closeSheet: () -> Unit = {
        coroutineScope.launch { scaffoldState.bottomSheetState.partialExpand() }
    }
    LaunchedEffect(sheetTargetValue) {
        if (sheetTargetValue == SheetValue.PartiallyExpanded || sheetTargetValue == SheetValue.Hidden) {
            keyboardController?.hide()
        }
    }
    // 외부(ACTION_SEND) 에서 sheet 펼침 요청 감지 — 카톡 등 공유 수신 시 자동 expand
    val shouldExpand by chatViewModel.shouldExpandSheet.collectAsState()
    LaunchedEffect(shouldExpand) {
        if (shouldExpand) {
            scaffoldState.bottomSheetState.expand()
            chatViewModel.consumeExpandRequest()
        }
    }
    val density = LocalDensity.current
    val navInsetDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            ChatBottomSheetContent(viewModel = chatViewModel, isExpanded = isExpanded)
        },
        sheetPeekHeight = 76.dp + navInsetDp,
        sheetDragHandle = null,
        sheetContainerColor = Color.Transparent,
        sheetShadowElevation = 0.dp,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            HomeScreen(
                navController = navController,
                viewModel = driverViewModel,
                isSheetExpanded = isExpanded,
                onCloseSheet = closeSheet,
            )
        }
    }
}

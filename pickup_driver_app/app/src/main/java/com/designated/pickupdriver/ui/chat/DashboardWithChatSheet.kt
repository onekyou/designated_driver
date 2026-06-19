package com.designated.pickupdriver.ui.chat

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.designated.pickupdriver.ui.dashboard.DashboardScreen
import kotlinx.coroutines.launch

/**
 * Dashboard 위에 사무실 단톡방 BottomSheet를 동거시키는 래퍼.
 * call_manager `DashboardWithChatSheet`(MainActivity:1391-1440) +
 * driver_app `HomeScreenWithChatSheet` 패턴 그대로 이식.
 *
 * V1: peek + Expanded 2-state. Material 3 BottomSheetScaffold.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardWithChatSheet(
    onLogout: () -> Unit,
) {
    val chatViewModel: ChatViewModel = hiltViewModel()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetTargetValue = scaffoldState.bottomSheetState.targetValue
    val isExpanded = sheetTargetValue == SheetValue.Expanded
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sheetTargetValue) {
        if (sheetTargetValue == SheetValue.PartiallyExpanded || sheetTargetValue == SheetValue.Hidden) {
            keyboardController?.hide()
        }
    }
    // 블랙박스 열람(시트 펼침) 시 시스템 메시지 증분 풀 — 시스템 메시지는 FCM 미푸시라 여기서 동기화. 평소 안 열면 read 0.
    LaunchedEffect(isExpanded) {
        if (isExpanded) chatViewModel.syncMessages()
    }
    BackHandler(enabled = isExpanded) {
        coroutineScope.launch { scaffoldState.bottomSheetState.partialExpand() }
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
            DashboardScreen(onLogout = onLogout)
        }
    }
}

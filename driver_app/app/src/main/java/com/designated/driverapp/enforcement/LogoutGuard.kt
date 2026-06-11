package com.designated.driverapp.enforcement

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 게이트 #1/#2 LogoutGuard — 미정산 상태에서 로그아웃/앱종료를 시도하면 뜨는 차단 안내.
 *
 * 실제 차단 판정은 [EnforcementGate.isDriverUnsettled], 호출처(HomeScreen 로그아웃 버튼)에서
 * 미정산이면 종료 다이얼로그 대신 이 안내를 띄운다.
 */
@Composable
fun LogoutBlockedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("종료할 수 없습니다") },
        text = { Text("정산하지 않은 운행 내역이 있습니다.\n먼저 [업무마감]을 진행해 주세요.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    )
}

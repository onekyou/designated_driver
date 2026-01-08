package com.designated.callmanager.ui

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.designated.callmanager.MainActivity
import com.designated.callmanager.ui.theme.CallManagerTheme

/**
 * 공유콜 알림 중간팝업 Activity
 * Full-Screen Intent로 실행되어 사용자에게 수락/취소 선택권 제공
 */
class SharedCallAcceptActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHARED_CALL_ID = "sharedCallId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_CUSTOM_MESSAGE = "customMessage"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 화면 켜짐 및 잠금화면 위에 표시
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val sharedCallId = intent.getStringExtra(EXTRA_SHARED_CALL_ID) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "🔄 새로운 공유콜!"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "공유콜이 도착했습니다"
        val customMessage = intent.getStringExtra(EXTRA_CUSTOM_MESSAGE) ?: body

        setContent {
            CallManagerTheme {
                SharedCallAcceptDialog(
                    title = title,
                    message = customMessage,
                    onAccept = {
                        // 수락 시 - 알림 제거 후 배차팝업으로 이동
                        clearNotification(sharedCallId)
                        openMainActivityWithSharedCall(sharedCallId)
                        finish()
                    },
                    onCancel = {
                        // 취소 시 - 알림만 제거하고 종료
                        clearNotification(sharedCallId)
                        finish()
                    }
                )
            }
        }
    }

    private fun clearNotification(sharedCallId: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = sharedCallId.hashCode()
        notificationManager.cancel(notificationId)
    }

    private fun openMainActivityWithSharedCall(sharedCallId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "ACTION_SHOW_SHARED_CALL"
            putExtra("sharedCallId", sharedCallId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
}

@Composable
fun SharedCallAcceptDialog(
    title: String,
    message: String,
    onAccept: () -> Unit,
    onCancel: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 제목
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                // 내용
                Text(
                    text = message,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 취소 버튼
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("취소", fontWeight = FontWeight.Medium)
                    }

                    // 수락 버튼
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("수락", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}
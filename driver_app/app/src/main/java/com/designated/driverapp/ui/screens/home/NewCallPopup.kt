package com.designated.driverapp.ui.screens.home

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.designated.driverapp.model.CallInfo

@Composable
fun NewCallPopup(
    callInfo: CallInfo,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    pendingCallCount: Int = 0
) {
    val context = LocalContext.current

    // 팝업 표시 시 알림음 1회 재생, 사라지면 정지/해제
    DisposableEffect(Unit) {
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val mediaPlayer = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, alarmUri)
                isLooping = false
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("NewCallPopup", "알림음 재생 실패", e)
            null
        }

        onDispose {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        }
    }

    Dialog(onDismissRequest = { /* 바깥 클릭으로 닫히지 않음 */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "새로운 콜",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "새로운 호출이 들어왔습니다.",
                    style = MaterialTheme.typography.bodyLarge
                )

                // 대기 중인 다른 배차 콜이 있으면 개수 표시
                if (pendingCallCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "대기 중인 콜 ${pendingCallCount}건",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onAccept,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("수락")
                }
            }
        }
    }
}
package com.designated.callmanager.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.designated.callmanager.util.CallMemoParser
import com.designated.callmanager.util.ParsedMemo
import com.designated.callmanager.util.VoiceInputHelper

/**
 * 배차 메모 편집 다이얼로그.
 *
 * 진입 경로: 정보카드 프리뷰 [수정] 탭 → 이 다이얼로그
 *
 * 키보드 편집 + 마이크 버튼(탭=STT) 지원. 완료 시 [onConfirm]에 원문 텍스트 + 파싱된 [ParsedMemo] 전달.
 * [customerAddress] 는 STT 파싱 시 출발지 폴백 용도로 전달.
 */
@Composable
fun MemoInputDialog(
    initialText: String?,
    customerAddress: String? = null,
    onConfirm: (String, ParsedMemo) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val voiceHelper = remember { VoiceInputHelper(context) }
    var text by remember { mutableStateOf(initialText ?: "") }
    var isListening by remember { mutableStateOf(false) }
    val previewParsed by remember(text) {
        mutableStateOf(CallMemoParser.parse(text, customerAddress))
    }

    // STT recognizer leak 방지
    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.destroy()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("메모 수정") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("메모") },
                    placeholder = { Text("예: 시장에서 용문 이만오천원") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = {
                            if (!isListening) {
                                isListening = true
                                voiceHelper.startListening { recognized ->
                                    text = recognized
                                    isListening = false
                                }
                            } else {
                                voiceHelper.stopListening()
                                isListening = false
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                            contentDescription = if (isListening) "음성 입력 중" else "음성 입력 시작",
                            tint = if (isListening) Color.Red else Color.Unspecified
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isListening) "말씀하세요..." else "마이크를 눌러 음성으로 입력",
                        color = if (isListening) Color.Red else Color.Unspecified
                    )
                }

                // 파싱 프리뷰 (실시간) — 매니저가 저장 전 확인 가능
                if (previewParsed.hasStructuredFields) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "📋 ${previewParsed.toPreview()}",
                        color = Color.Blue
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = CallMemoParser.parse(text, customerAddress)
                onConfirm(text, parsed)
            }) {
                Text("완료")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

package com.designated.callmanager.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.local.LocalChatMessage
import com.designated.callmanager.data.repository.ChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 사무실 단톡방 화면 (call_manager)
 *
 * 동작 (스펙 §11 §12):
 *  - LazyColumn(reverseLayout=true): 인덱스 0=가장 최신, N=가장 오래됨
 *  - 5분 그룹화: 같은 발신자 연속 메시지면 그룹 첫 메시지에만 이름+역할 표시,
 *    그룹 마지막 메시지에만 시간 표시
 *  - 본인=우측 노란색 + sendStatus(✓회색/✓파랑/✗빨강) / 타인=좌측 회색
 *  - FAILED 메시지는 탭 시 retry
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
) {
    val messages by viewModel.messages.collectAsState()
    val currentUserId = viewModel.currentUserId
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("사무실 단톡방") },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                }
            }
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items = messages, key = { _, msg -> msg.id }) { index, msg ->
                // reverseLayout=true: index+1 = 시간상 이전, index-1 = 시간상 이후
                val prevMsg = messages.getOrNull(index + 1)
                val nextMsg = messages.getOrNull(index - 1)

                val showName = prevMsg == null ||
                    prevMsg.senderId != msg.senderId ||
                    (msg.createdAt - prevMsg.createdAt) > GROUP_THRESHOLD_MS
                val showTime = nextMsg == null ||
                    nextMsg.senderId != msg.senderId ||
                    (nextMsg.createdAt - msg.createdAt) > GROUP_THRESHOLD_MS

                ChatMessageRow(
                    message = msg,
                    isOwn = msg.senderId == currentUserId,
                    showName = showName,
                    showTime = showTime,
                    onRetry = { viewModel.retryMessage(msg) },
                )
            }
        }

        HorizontalDivider()

        ChatInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                val trimmed = inputText.trim()
                if (trimmed.isNotEmpty()) {
                    viewModel.sendMessage(trimmed)
                    inputText = ""
                }
            }
        )
    }
}

@Composable
private fun ChatMessageRow(
    message: LocalChatMessage,
    isOwn: Boolean,
    showName: Boolean,
    showTime: Boolean,
    onRetry: () -> Unit,
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn) Color(0xFFFFF59D) else Color(0xFFEEEEEE)
    val timeText = formatTime(message.createdAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (showName) 4.dp else 0.dp),
        horizontalAlignment = alignment,
    ) {
        if (!isOwn && showName) {
            Text(
                text = formatSender(message.senderName, message.senderRole),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isOwn) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isOwn) {
                // 본인: [상태/시간 좌측] [버블 우측]
                if (showTime) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(end = 4.dp, bottom = 2.dp),
                    )
                }
                SendStatusIndicator(
                    sendStatus = message.sendStatus,
                    onRetry = onRetry,
                )
                MessageBubble(text = message.text, color = bubbleColor)
            } else {
                // 타인: [버블 좌측] [시간 우측]
                MessageBubble(text = message.text, color = bubbleColor)
                if (showTime) {
                    Text(
                        text = timeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(text: String, color: Color) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.widthIn(max = 280.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.Black,
        )
    }
}

@Composable
private fun SendStatusIndicator(
    sendStatus: String,
    onRetry: () -> Unit,
) {
    val (symbol, color, clickable) = when (sendStatus) {
        LocalChatMessage.SEND_STATUS_SENDING -> Triple("✓", Color.Gray, false)
        LocalChatMessage.SEND_STATUS_SENT -> Triple("✓", Color(0xFF1E88E5), false)
        LocalChatMessage.SEND_STATUS_FAILED -> Triple("✗", Color.Red, true)
        else -> Triple("", Color.Transparent, false)
    }
    if (symbol.isEmpty()) return
    Text(
        text = symbol,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .padding(end = 4.dp, bottom = 2.dp)
            .then(if (clickable) Modifier.clickable { onRetry() } else Modifier),
    )
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("메시지 입력") },
            singleLine = false,
            maxLines = 4,
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onSend,
            enabled = value.trim().isNotEmpty(),
        ) {
            Text("전송")
        }
    }
}

/**
 * BottomSheetScaffold에서 사용할 단톡방 내용물.
 *
 * 구조:
 *  - 상단 56dp peek bar (latestMessage 미리보기) — sheet peek 상태에서 유일하게 보임
 *  - 그 아래 LazyColumn 메시지 리스트 + 입력바 — sheet expanded 시 노출
 *
 * 주의: sheet가 충분히 expand되어야 LazyColumn weight(1f)가 영역 확보.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBottomSheetContent(
    viewModel: ChatViewModel = viewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val latestMessage by viewModel.latestMessage.collectAsState()
    val currentUserId = viewModel.currentUserId
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        ChatPeekPreviewBar(latestMessage = latestMessage, currentUserId = currentUserId)

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            reverseLayout = true,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(items = messages, key = { _, msg -> msg.id }) { index, msg ->
                val prevMsg = messages.getOrNull(index + 1)
                val nextMsg = messages.getOrNull(index - 1)
                val showName = prevMsg == null ||
                    prevMsg.senderId != msg.senderId ||
                    (msg.createdAt - prevMsg.createdAt) > GROUP_THRESHOLD_MS
                val showTime = nextMsg == null ||
                    nextMsg.senderId != msg.senderId ||
                    (nextMsg.createdAt - msg.createdAt) > GROUP_THRESHOLD_MS

                ChatMessageRow(
                    message = msg,
                    isOwn = msg.senderId == currentUserId,
                    showName = showName,
                    showTime = showTime,
                    onRetry = { viewModel.retryMessage(msg) },
                )
            }
        }

        HorizontalDivider()

        ChatInputBar(
            value = inputText,
            onValueChange = { inputText = it },
            onSend = {
                val trimmed = inputText.trim()
                if (trimmed.isNotEmpty()) {
                    viewModel.sendMessage(trimmed)
                    inputText = ""
                }
            }
        )
    }
}

/**
 * Sheet peek 상태(56dp)에서 보이는 미리보기 카드.
 * "💬 [발신자]: [메시지 1줄]" 형식 (스펙 §11)
 */
@Composable
private fun ChatPeekPreviewBar(
    latestMessage: LocalChatMessage?,
    currentUserId: String,
) {
    val previewText = when {
        latestMessage == null -> "💬 사무실 단톡방 (메시지 없음)"
        latestMessage.senderId == currentUserId -> "💬 나: ${latestMessage.text}"
        else -> "💬 ${latestMessage.senderName}: ${latestMessage.text}"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = previewText,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
    }
}

private const val GROUP_THRESHOLD_MS = 5 * 60 * 1000L  // 5분

private val timeFormatter = SimpleDateFormat("a h:mm", Locale.KOREA)

private fun formatTime(epochMs: Long): String =
    timeFormatter.format(Date(epochMs))

private fun formatSender(name: String, role: String): String {
    val roleKorean = when (role) {
        ChatRepository.ROLE_MANAGER -> "매니저"
        ChatRepository.ROLE_DESIGNATED_DRIVER -> "대리기사"
        ChatRepository.ROLE_PICKUP_DRIVER -> "픽업기사"
        else -> ""
    }
    return if (roleKorean.isEmpty()) name else "$name ($roleKorean)"
}

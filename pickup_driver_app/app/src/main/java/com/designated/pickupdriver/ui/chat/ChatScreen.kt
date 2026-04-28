package com.designated.pickupdriver.ui.chat

import android.util.Log
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.designated.pickupdriver.data.local.LocalChatMessage
import com.designated.pickupdriver.data.repository.ChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 사무실 단톡방 화면 (pickup_driver_app)
 * call_manager b190a2bc 그대로 이식, package + ROLE 라벨만 변경.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
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
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
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
            .padding(horizontal = 8.dp)
            .onSizeChanged { size ->
                Log.d("ChatInputBar", "Row size: ${size.width}x${size.height} (text length=${value.length}, lines=${value.count { it == '\n' } + 1})")
            }
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                Log.d("ChatLayout", "ChatInputBar pos=(${pos.x.toInt()}, ${pos.y.toInt()}) size=${coords.size.width}x${coords.size.height} text=\"${value.take(20)}\"")
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onSizeChanged { size ->
                    Log.d("ChatInputBar", "TextField size: ${size.width}x${size.height} (text length=${value.length}, lines=${value.count { it == '\n' } + 1})")
                },
            placeholder = { Text("메시지 입력") },
            singleLine = false,
            maxLines = 3,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatBottomSheetContent(
    viewModel: ChatViewModel = hiltViewModel(),
    isExpanded: Boolean = false,
) {
    val messages by viewModel.messages.collectAsState()
    val latestMessage by viewModel.latestMessage.collectAsState()
    val currentUserId = viewModel.currentUserId
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .imePadding()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                Log.d("ChatLayout", "OuterColumn pos=(${pos.x.toInt()}, ${pos.y.toInt()}) size=${coords.size.width}x${coords.size.height} isExpanded=$isExpanded")
            },
    ) {
        if (!isExpanded) {
            ChatPeekPreviewBar(latestMessage = latestMessage, currentUserId = currentUserId)
            Spacer(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        Log.d("ChatLayout", "LazyColumn pos=(${pos.x.toInt()}, ${pos.y.toInt()}) size=${coords.size.width}x${coords.size.height} msgCount=${messages.size}")
                    },
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
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
}

/**
 * Sheet peek 상태에서 보이는 미리보기 카드 (책갈피 통합 디자인).
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier
                .width(110.dp)
                .height(20.dp),
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 6.dp,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "💬 채팅",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
        ) {
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
    }
}

/**
 * Dashboard에 임베드되는 카드형 단톡방 (BottomSheet 안 씀).
 * DashboardScreen 50:50 분할의 아래 절반에 사용.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatCard(
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsState()
    val currentUserId = viewModel.currentUserId
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Text(
                    text = "💬 사무실 단톡방",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
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
}

private const val GROUP_THRESHOLD_MS = 5 * 60 * 1000L

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

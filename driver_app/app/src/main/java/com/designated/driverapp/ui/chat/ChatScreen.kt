package com.designated.driverapp.ui.chat

import android.media.MediaPlayer
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.designated.driverapp.data.local.LocalChatMessage
import com.designated.driverapp.data.repository.ChatRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 사무실 단톡방 화면 (driver_app)
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
    var fullScreenUrl by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.sendImageMessage(it) } }

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
                    onImageClick = { url -> fullScreenUrl = url },
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
            },
            onPickImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
        )
    }

    FullScreenImageViewer(url = fullScreenUrl, onDismiss = { fullScreenUrl = null })
}

@Composable
private fun ChatMessageRow(
    message: LocalChatMessage,
    isOwn: Boolean,
    showName: Boolean,
    showTime: Boolean,
    onRetry: () -> Unit,
    onImageClick: (String) -> Unit = {},
) {
    val alignment = if (isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwn) Color(0xFFFFF59D) else Color(0xFFEEEEEE)
    val timeText = formatTime(message.createdAt)
    val isImage = !message.imageUrl.isNullOrEmpty()
    val isAudio = !message.audioUrl.isNullOrEmpty()

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
                when {
                    isAudio -> AudioBubble(
                        audioUrl = message.audioUrl!!,
                        durationMs = message.audioDurationMs,
                        sendStatus = message.sendStatus,
                        color = bubbleColor,
                    )
                    isImage -> ImageBubble(
                        imageUrl = message.imageUrl!!,
                        width = message.imageWidth ?: 640,
                        height = message.imageHeight ?: 640,
                        sendStatus = message.sendStatus,
                        onClick = onImageClick,
                    )
                    else -> MessageBubble(text = message.text, color = bubbleColor)
                }
            } else {
                when {
                    isAudio -> AudioBubble(
                        audioUrl = message.audioUrl!!,
                        durationMs = message.audioDurationMs,
                        sendStatus = message.sendStatus,
                        color = bubbleColor,
                    )
                    isImage -> ImageBubble(
                        imageUrl = message.imageUrl!!,
                        width = message.imageWidth ?: 640,
                        height = message.imageHeight ?: 640,
                        sendStatus = message.sendStatus,
                        onClick = onImageClick,
                    )
                    else -> MessageBubble(text = message.text, color = bubbleColor)
                }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(text: String, color: Color) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    Surface(
        color = color,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .widthIn(max = 280.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(text))
                    Toast.makeText(context, "메시지를 복사했어요", Toast.LENGTH_SHORT).show()
                },
            ),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.Black,
        )
    }
}

/**
 * 이미지 메시지 버블.
 * - "uploading://" sentinel + SENDING: 회색 박스 + spinner
 * - "uploading://" sentinel + FAILED: 회색 박스 + ❌ + "전송 실패"
 * - 정상 URL: AsyncImage + aspectRatio + 클릭 시 풀스크린
 */
@Composable
private fun ImageBubble(
    imageUrl: String,
    width: Int,
    height: Int,
    sendStatus: String,
    onClick: (String) -> Unit,
) {
    if (imageUrl == LocalChatMessage.UPLOADING_SENTINEL) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            if (sendStatus == LocalChatMessage.SEND_STATUS_FAILED) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "❌", fontSize = 32.sp)
                    Text(
                        text = "전송 실패",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                CircularProgressIndicator()
            }
        }
    } else {
        val ratio = if (height > 0) width.toFloat() / height else 1f
        AsyncImage(
            model = imageUrl,
            contentDescription = "사진",
            modifier = Modifier
                .widthIn(max = 240.dp)
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick(imageUrl) },
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color.Gray.copy(alpha = 0.2f)),
            error = ColorPainter(Color.Red.copy(alpha = 0.2f)),
        )
    }
}

/**
 * 음성 메모 버블 (PTT 콜드 발화 수신). 자동재생과 별개로 ▶ 탭 → 수동 재청취.
 */
@Composable
private fun AudioBubble(
    audioUrl: String,
    durationMs: Long?,
    sendStatus: String,
    color: Color,
) {
    if (audioUrl == LocalChatMessage.UPLOADING_SENTINEL) {
        Box(
            modifier = Modifier
                .size(width = 120.dp, height = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            if (sendStatus == LocalChatMessage.SEND_STATUS_FAILED) {
                Text(text = "❌ 전송 실패", color = Color.Red, style = MaterialTheme.typography.bodySmall)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }
        }
    } else {
        var isPlaying by remember { mutableStateOf(false) }
        val player = remember { MediaPlayer() }
        DisposableEffect(Unit) {
            onDispose { runCatching { player.release() } }
        }
        Surface(
            color = color,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.widthIn(max = 220.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable {
                        if (isPlaying) {
                            runCatching { player.stop(); player.reset() }
                            isPlaying = false
                        } else {
                            runCatching {
                                player.reset()
                                player.setDataSource(audioUrl)
                                player.setOnPreparedListener { it.start(); isPlaying = true }
                                player.setOnCompletionListener { isPlaying = false; runCatching { it.reset() } }
                                player.setOnErrorListener { _, _, _ -> isPlaying = false; true }
                                player.prepareAsync()
                            }
                        }
                    },
            ) {
                Text(text = if (isPlaying) "⏹" else "▶", fontSize = 20.sp, color = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "🎙 ${formatAudioDuration(durationMs)}", color = Color.Black)
            }
        }
    }
}

private fun formatAudioDuration(ms: Long?): String {
    val totalSec = ((ms ?: 0L) / 1000).toInt()
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * 풀스크린 이미지 뷰어.
 */
@Composable
private fun FullScreenImageViewer(url: String?, onDismiss: () -> Unit) {
    if (url.isNullOrEmpty()) return
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = "사진 풀스크린",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
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
    onPickImage: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .onSizeChanged { size ->
                Log.d("ChatInputBar", "Row size: ${size.width}x${size.height} (text length=${value.length}, lines=${value.count { it == '\n' } + 1})")
            }
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                Log.d("ChatLayout", "ChatInputBar pos=(${pos.x.toInt()}, ${pos.y.toInt()}) size=${coords.size.width}x${coords.size.height} text=\"${value.take(20)}\"")
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        IconButton(onClick = onPickImage) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "이미지 첨부",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
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
    val inputText by viewModel.inputText.collectAsState()
    var fullScreenUrl by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // LazyColumn 잔여 스크롤이 BottomSheet drag로 자동 위임되는 것 차단
    // (이전 대화 보다가 의도치 않게 sheet 닫히는 문제 해결, 의도적 swipe는 sheet 자체에서 유지)
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = available
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.sendImageMessage(it) } }

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
                    .nestedScroll(nestedScrollConnection)
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
                        onImageClick = { url -> fullScreenUrl = url },
                    )
                }
            }

            HorizontalDivider()

            ChatInputBar(
                value = inputText,
                onValueChange = { viewModel.setInputText(it) },
                onSend = {
                    val trimmed = inputText.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.sendMessage(trimmed)
                        viewModel.clearInputText()
                    }
                },
                onPickImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
        }
    }

    FullScreenImageViewer(url = fullScreenUrl, onDismiss = { fullScreenUrl = null })
}

@Composable
private fun ChatPeekPreviewBar(
    latestMessage: LocalChatMessage?,
    currentUserId: String,
) {
    val previewText = when {
        latestMessage == null -> "💬 사무실 단톡방 (메시지 없음)"
        !latestMessage.imageUrl.isNullOrEmpty() -> {
            val who = if (latestMessage.senderId == currentUserId) "나" else latestMessage.senderName
            "💬 $who: [사진]"
        }
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

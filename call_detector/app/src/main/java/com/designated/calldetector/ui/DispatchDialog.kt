package com.designated.calldetector.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.designated.calldetector.util.CallMemoParser
import com.designated.calldetector.util.ParsedMemo
import com.designated.calldetector.util.VoiceInputHelper
import kotlinx.coroutines.launch

// 기사 정보 데이터 클래스
data class DriverInfo(
    val id: String,
    val name: String,
    val status: String,
    val phone: String = "",
    val authUid: String = ""
)

// 콜 정보 데이터 클래스
data class CallInfo(
    val phoneNumber: String,
    val customerName: String? = null,
    val customerAddress: String? = null
)

/**
 * 정보카드 STT 메모 상태.
 * Idle → (long press) → Recording → (release) → Saving → Idle
 */
enum class MemoSttState { Idle, Recording, Saving }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DispatchDialog(
    callInfo: CallInfo,
    availableDrivers: List<DriverInfo>,
    onDriverSelect: (DriverInfo) -> Unit,
    onHold: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    onMemoUpdate: (suspend (String, ParsedMemo) -> Unit)? = null
) {
    val context = LocalContext.current
    val voiceHelper = remember { VoiceInputHelper(context) }
    val coroutineScope = rememberCoroutineScope()

    var memoSttState by remember { mutableStateOf(MemoSttState.Idle) }
    var currentMemoText by remember { mutableStateOf<String?>(null) }
    var currentParsed by remember { mutableStateOf<ParsedMemo?>(null) }
    var showMemoEditDialog by remember { mutableStateOf(false) }

    // STT 진행/저장 중에는 배차/삭제/공유/나중에 버튼 disable
    val actionsDisabled = memoSttState != MemoSttState.Idle

    // RECORD_AUDIO 런타임 권한 launcher — MainActivity 를 거치지 않고
    // DispatchActivity 가 직접 뜨는 경로(통화 종료 → SYSTEM_ALERT_WINDOW 바로 실행)를
    // 지원하기 위해 이 Composable 내부에서 직접 요청.
    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(
                context,
                "마이크 권한 허용됨. 다시 길게 눌러 메모를 입력하세요",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                context,
                "마이크 권한이 필요합니다 (STT 메모)",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // STT recognizer leak 방지 — 팝업 dismiss 시 자동 destroy
    DisposableEffect(Unit) {
        onDispose {
            voiceHelper.destroy()
        }
    }

    // 메모 수정 다이얼로그 (정보카드 프리뷰 탭 진입)
    if (showMemoEditDialog && onMemoUpdate != null) {
        MemoInputDialog(
            initialText = currentMemoText,
            customerAddress = callInfo.customerAddress,
            onConfirm = { text, parsed ->
                currentMemoText = text
                currentParsed = parsed
                showMemoEditDialog = false
                coroutineScope.launch {
                    try {
                        onMemoUpdate(text, parsed)
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            "메모 수정 실패: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onDismiss = { showMemoEditDialog = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새로운 호출 접수", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 호출 정보 카드 — 길게 누르면 STT 메모 시작
                val cardBgColor = when (memoSttState) {
                    MemoSttState.Recording -> Color(0xFFD32F2F) // 빨간 톤
                    MemoSttState.Saving -> Color(0xFF757575)    // 회색 톤
                    MemoSttState.Idle -> MaterialTheme.colorScheme.primary
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (onMemoUpdate != null) {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = {
                                        // 권한 체크 — 없으면 요청 후 종료 (사용자가 허용 후 다시 롱프레스)
                                        val hasAudioPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (!hasAudioPerm) {
                                            audioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            return@combinedClickable
                                        }
                                        if (memoSttState == MemoSttState.Idle) {
                                            memoSttState = MemoSttState.Recording
                                            voiceHelper.startListening { recognized ->
                                                memoSttState = MemoSttState.Saving
                                                currentMemoText = recognized
                                                val parsed = CallMemoParser.parse(
                                                    text = recognized,
                                                    customerAddress = callInfo.customerAddress,
                                                    voiceHelper = voiceHelper
                                                )
                                                currentParsed = parsed
                                                coroutineScope.launch {
                                                    try {
                                                        onMemoUpdate(recognized, parsed)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(
                                                            context,
                                                            "메모 저장 실패: ${e.message}",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    } finally {
                                                        memoSttState = MemoSttState.Idle
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            } else Modifier
                        ),
                    colors = CardDefaults.cardColors(containerColor = cardBgColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = callInfo.phoneNumber,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        callInfo.customerName?.let {
                            Text(text = it, color = Color.White)
                        }
                        callInfo.customerAddress?.let {
                            Text(text = it, color = Color.White)
                        }

                        // 메모 프리뷰 / STT 상태 표시
                        if (onMemoUpdate != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            when (memoSttState) {
                                MemoSttState.Recording -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Mic,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "말씀하세요...",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                MemoSttState.Saving -> {
                                    Text(
                                        text = "💾 저장 중...",
                                        color = Color.White
                                    )
                                }
                                MemoSttState.Idle -> {
                                    val parsedSnapshot = currentParsed
                                    val hasAnyContent = !currentMemoText.isNullOrBlank() ||
                                        (parsedSnapshot?.hasStructuredFields == true)
                                    if (hasAnyContent) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { showMemoEditDialog = true },
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "메모 수정",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Column {
                                                if (parsedSnapshot?.hasStructuredFields == true) {
                                                    Text(
                                                        text = "📋 ${parsedSnapshot.toPreview()}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                                if (!currentMemoText.isNullOrBlank()) {
                                                    Text(
                                                        text = "📝 ${currentMemoText}",
                                                        color = Color.White.copy(alpha = 0.85f),
                                                        maxLines = 2,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = "💡 길게 눌러 메모 추가 (STT)",
                                            color = Color.White.copy(alpha = 0.7f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 대기중인 기사 목록
                if (availableDrivers.isNotEmpty()) {
                    Text("대기중인 기사 선택:", fontWeight = FontWeight.Medium)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(availableDrivers) { driver ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !actionsDisabled) {
                                        onDriverSelect(driver)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (actionsDisabled)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else
                                        MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = driver.name,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = when(driver.status) {
                                            "WAITING" -> "대기중"
                                            "BUSY" -> "운행중"
                                            else -> driver.status
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (driver.status == "WAITING") Color.Green else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "현재 대기중인 기사가 없습니다.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    enabled = !actionsDisabled,
                    onClick = onHold
                ) {
                    Text("나중에")
                }

                TextButton(
                    enabled = !actionsDisabled,
                    onClick = onShare
                ) {
                    Text("공유")
                }

                TextButton(
                    enabled = !actionsDisabled,
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("삭제")
                }
            }
        }
    )
}

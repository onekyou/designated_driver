package com.designated.callmanager.reservation.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.reservation.data.RecordingFile

/**
 * 통화예약 인박스 (production 플로우). 측정 하니스 ReservationTestScreen 과 분리.
 *
 * 6/8 파일럿 UX: 최근 통화녹음 선택 → "분석 중" → 써머리확인 다이얼로그 → [확인] → 실제 콜 생성.
 * 권한은 화면 내 독립 요청(격리).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationInboxScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReservationInboxViewModel = viewModel(),
) {
    val context = LocalContext.current
    val recordings by viewModel.recordings.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadRecordings()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadRecordings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("통화로 예약 입력") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "최근 통화를 누르면 AI가 예약 내용을 분석합니다. (삼성 '통화 자동 녹음' 필요)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            if (!hasPermission) {
                Button(onClick = { permLauncher.launch(audioPermission) }) { Text("녹음 접근 허용") }
                Spacer(Modifier.height(8.dp))
            }

            when (val s = uiState) {
                is InboxUiState.Analyzing -> {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 16.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("분석 중: ${s.name}")
                    }
                }
                is InboxUiState.NotReservation -> {
                    Text("예약 통화가 아닙니다: ${s.name}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("목록으로") }
                }
                is InboxUiState.Created -> {
                    Text("콜이 등록되었습니다${if (s.phone.isNotBlank()) " (${s.phone})" else ""}.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        viewModel.reset()
                        viewModel.loadRecordings()
                    }) { Text("목록으로") }
                }
                is InboxUiState.Error -> {
                    Text("오류: ${s.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("다시") }
                }
                is InboxUiState.Confirm -> {
                    // 목록은 그대로 두고 확인 다이얼로그 오버레이.
                    RecordingList(recordings, hasPermission) { /* 분석 중엔 클릭 무시 */ }
                    ReservationConfirmDialog(
                        reservation = s.reservation,
                        parsed = s.recording.parsed,
                        onConfirm = { viewModel.confirmAndCreate(it) },
                        onDismiss = { viewModel.reset() },
                    )
                }
                InboxUiState.Idle -> {
                    RecordingList(recordings, hasPermission) { viewModel.analyze(it) }
                }
            }
        }
    }
}

@Composable
private fun RecordingList(
    recordings: List<RecordingFile>,
    hasPermission: Boolean,
    onClick: (RecordingFile) -> Unit,
) {
    if (recordings.isEmpty()) {
        Text(
            if (hasPermission) "통화녹음이 없습니다."
            else "녹음 접근을 허용하면 최근 통화가 뜹니다.",
            style = MaterialTheme.typography.bodyMedium
        )
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(recordings) { rf ->
                RecordingRow(rf) { onClick(rf) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun RecordingRow(rf: RecordingFile, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(rf.parsed?.phone ?: rf.parsed?.label ?: rf.displayName, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${rf.parsed?.localDateTime ?: rf.displayName} · ${rf.sizeBytes / 1024}KB",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * 녹음 파싱 검증 화면 (내부 측정 하니스 — beta).
 * 통화녹음 1건 선택 → 업로드 → parseReservation → W/C 결과. 정답과 대조해 채점.
 * 권한은 화면 내 독립 요청(CallManagerPermissionManager 무관 — 격리).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReservationTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReservationTestViewModel = viewModel(),
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
    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.onPickedUri(it) }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) viewModel.loadRecordings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("녹음 파싱 검증 (beta)") },
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
                "삼성 '통화 자동 녹음'이 켜져 있어야 목록이 뜹니다. 안 보이면 [파일 직접 선택].",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasPermission) {
                    Button(onClick = { permLauncher.launch(audioPermission) }) { Text("녹음 접근 허용") }
                }
                OutlinedButton(onClick = { safLauncher.launch(arrayOf("audio/*")) }) { Text("파일 직접 선택") }
            }
            Spacer(Modifier.height(12.dp))

            when (val s = uiState) {
                is ReservationTestUiState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("분석 중: ${s.name}")
                    }
                }
                is ReservationTestUiState.Success -> {
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ReservationResultCard(s.recording, s.result)
                    }
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("목록으로") }
                }
                is ReservationTestUiState.Error -> {
                    Text("오류: ${s.message}", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.reset() }) { Text("다시") }
                }
                ReservationTestUiState.Idle -> {
                    if (recordings.isEmpty()) {
                        Text(
                            if (hasPermission) "통화녹음이 없습니다. [파일 직접 선택]으로 시도하세요."
                            else "녹음 접근을 허용하면 목록이 뜹니다.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(recordings) { rf ->
                                RecordingRow(rf) { viewModel.parse(rf) }
                                HorizontalDivider()
                            }
                        }
                    }
                }
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
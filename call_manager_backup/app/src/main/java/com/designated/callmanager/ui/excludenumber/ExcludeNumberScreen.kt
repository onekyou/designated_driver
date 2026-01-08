package com.designated.callmanager.ui.excludenumber

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.ExcludeNumberItem
import com.designated.callmanager.data.ExcludeNumberManager
import com.designated.callmanager.data.RestoreResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExcludeNumberScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContactSelection: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: ExcludeNumberViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ExcludeNumberItem?>(null) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showBackupInfoDialog by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    val contactPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onNavigateToContactSelection()
        } else {
            Toast.makeText(context, "연락처 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadExcludeNumbers()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("개인번호 관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                actions = {

                    var showMenu by remember { mutableStateOf(false) }

                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "메뉴")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("백업") },
                            onClick = {
                                showMenu = false
                                scope.launch {
                                    if (viewModel.backupNumbers()) {
                                        val count = uiState.totalCount
                                        Toast.makeText(context, "백업 완료: ${count}개 번호가 백업되었습니다", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "백업 실패", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("복원") },
                            onClick = {
                                showMenu = false
                                showRestoreDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("백업 정보") },
                            onClick = {
                                showMenu = false
                                showBackupInfoDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("전체 삭제 (테스트)") },
                            onClick = {
                                showMenu = false
                                showClearAllDialog = true
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {

            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFF9C27B0),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "추가")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFF121212))
        ) {

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF2A2A2A),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("이름 또는 번호로 검색", color = Color.Gray) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "총 ${uiState.totalCount}개의 개인번호",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )

                        TextButton(
                            onClick = {
                                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        ) {
                            Text(
                                "전화번호부에서 선택",
                                color = Color(0xFFFFAB00)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.filteredNumbers.isNotEmpty()) {

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(uiState.filteredNumbers) { item ->
                        ExcludeNumberItem(
                            item = item,
                            onDeleteClick = { showDeleteDialog = item }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddNumberDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { phoneNumber ->
                viewModel.addExcludeNumber(phoneNumber)
                Toast.makeText(context, "개인번호로 추가되었습니다", Toast.LENGTH_SHORT).show()
                showAddDialog = false
            }
        )
    }

    showDeleteDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("삭제 확인", color = Color.White) },
            containerColor = Color(0xFF2A2A2A),
            text = {
                val displayText = if (item.name != null) {
                    "${item.name} (${item.displayNumber})"
                } else {
                    item.displayNumber
                }
                Text("$displayText 를 개인번호에서 제거하시겠습니까?", color = Color.White)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeExcludeNumber(item)
                        Toast.makeText(context, "개인번호에서 제거되었습니다", Toast.LENGTH_SHORT).show()
                        showDeleteDialog = null
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("취소")
                }
            }
        )
    }

    if (showRestoreDialog) {
        if (viewModel.hasBackupFile()) {
            AlertDialog(
                onDismissRequest = { showRestoreDialog = false },
                title = { Text("데이터 복원", color = Color.White) },
                containerColor = Color(0xFF2A2A2A),
                text = {
                    Text("백업 파일에서 개인번호 목록을 복원하시겠습니까?\n현재 데이터와 병합됩니다.", color = Color.White)
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                when (val result = viewModel.restoreNumbers()) {
                                    is RestoreResult.Success -> {
                                        Toast.makeText(
                                            context,
                                            "복원 완료: ${result.totalCount}개 번호 (새로운 번호: ${result.newCount}개)",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                    is RestoreResult.FileNotFound -> {
                                        Toast.makeText(context, "백업 파일을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                                    }
                                    is RestoreResult.EmptyFile -> {
                                        Toast.makeText(context, "백업 파일이 비어있습니다", Toast.LENGTH_SHORT).show()
                                    }
                                    is RestoreResult.Error -> {
                                        Toast.makeText(context, "복원 실패: ${result.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                                showRestoreDialog = false
                            }
                        }
                    ) {
                        Text("복원")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRestoreDialog = false }) {
                        Text("취소")
                    }
                }
            )
        } else {
            showRestoreDialog = false
            Toast.makeText(context, "백업 파일이 존재하지 않습니다", Toast.LENGTH_SHORT).show()
        }
    }

    if (showBackupInfoDialog) {
        val hasBackup = viewModel.hasBackupFile()
        val lastBackupTime = viewModel.getLastBackupTime()
        val currentCount = uiState.totalCount

        val message = if (hasBackup && lastBackupTime > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val backupTimeStr = dateFormat.format(Date(lastBackupTime))

            "백업 파일: 존재함\n마지막 백업: $backupTimeStr\n현재 개인번호: ${currentCount}개\n\n자동 백업은 개인번호 추가/삭제 시 자동으로 수행됩니다."
        } else {
            "백업 파일: 없음\n현재 개인번호: ${currentCount}개\n\n개인번호를 추가하면 자동으로 백업이 생성됩니다."
        }

        AlertDialog(
            onDismissRequest = { showBackupInfoDialog = false },
            title = { Text("백업 정보", color = Color.White) },
            text = { Text(message, color = Color.White) },
            containerColor = Color(0xFF2A2A2A),
            confirmButton = {
                TextButton(onClick = { showBackupInfoDialog = false }) {
                    Text("확인")
                }
            }
        )
    }

    if (showClearAllDialog) {
        val count = uiState.totalCount
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("전체 삭제 확인", color = Color.White) },
            text = { Text("모든 개인번호($count 개)를 삭제하시겠습니까?\n\n이 작업은 되돌릴 수 없습니다.\n(테스트용 기능)", color = Color.White) },
            containerColor = Color(0xFF2A2A2A),
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllNumbers()
                        Toast.makeText(context, "모든 개인번호가 삭제되었습니다", Toast.LENGTH_SHORT).show()
                        showClearAllDialog = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun ExcludeNumberItem(
    item: ExcludeNumberItem,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF2A2A2A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                if (item.name != null) {
                    Text(
                        text = item.name,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = item.displayNumber,
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }

            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "삭제",
                    tint = Color.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun AddNumberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("개인번호 추가", color = Color.White) },
        text = {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("전화번호") },
                placeholder = { Text("010-1234-5678", color = Color.Gray) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray
                )
            )
        },
        containerColor = Color(0xFF2A2A2A),
        confirmButton = {
            TextButton(
                onClick = {
                    if (phoneNumber.isNotBlank()) {
                        onConfirm(phoneNumber)
                    }
                },
                enabled = phoneNumber.isNotBlank()
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@Composable
fun RestoreDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("백업 복원") },
        text = {
            Text("백업 파일에서 개인번호를 복원하시겠습니까?\n현재 데이터는 백업 데이터로 교체됩니다.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("복원")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
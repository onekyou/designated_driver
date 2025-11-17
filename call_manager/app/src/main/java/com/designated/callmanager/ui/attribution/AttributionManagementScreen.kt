package com.designated.callmanager.ui.attribution

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.designated.callmanager.data.OfficeSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttributionManagementScreen(
    regionId: String,
    officeId: String,
    viewModel: AttributionManagementViewModel = viewModel(),
    onNavigateBack: () -> Unit
) {
    val attributionStats by viewModel.attributionStats.collectAsStateWithLifecycle()
    val kpiMetrics by viewModel.kpiMetrics.collectAsStateWithLifecycle()
    val recentAttributions by viewModel.recentAttributions.collectAsStateWithLifecycle()
    val officeSettings by viewModel.officeSettings.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LaunchedEffect(regionId, officeId) {
        if (regionId.isNotBlank() && officeId.isNotBlank()) {
            viewModel.loadAttributionData(regionId, officeId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("회원관리") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshData(regionId, officeId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "새로고침")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading && attributionStats == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 내 사무실 현황 섹션
                item {
                    OfficeStatusSection(kpiMetrics = kpiMetrics)
                }

                // QR 코드 관리 섹션
                item {
                    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsStateWithLifecycle()
                    val officePhone by viewModel.officePhone.collectAsStateWithLifecycle()
                    val bankName by viewModel.bankName.collectAsStateWithLifecycle()
                    val accountNumber by viewModel.accountNumber.collectAsStateWithLifecycle()
                    val accountHolder by viewModel.accountHolder.collectAsStateWithLifecycle()
                    var showEditDialog by remember { mutableStateOf(false) }
                    val context = androidx.compose.ui.platform.LocalContext.current

                    QRCodeManagementSection(
                        officeSettings = officeSettings,
                        qrCodeBitmap = qrCodeBitmap,
                        onDownloadQR = { viewModel.downloadQRCode() },
                        onShareQR = { viewModel.shareQRCode() },
                        onEditOfficeInfo = { showEditDialog = true }
                    )

                    if (showEditDialog) {
                        OfficeInfoEditDialog(
                            phone = officePhone,
                            bankName = bankName,
                            accountNumber = accountNumber,
                            accountHolder = accountHolder,
                            onPhoneChange = { viewModel.updatePhoneInput(it) },
                            onBankNameChange = { viewModel.updateBankNameInput(it) },
                            onAccountNumberChange = { viewModel.updateAccountNumberInput(it) },
                            onAccountHolderChange = { viewModel.updateAccountHolderInput(it) },
                            onDismiss = { showEditDialog = false },
                            onSave = {
                                viewModel.updateOfficeInfo(
                                    regionId = regionId,
                                    officeId = officeId,
                                    phone = officePhone,
                                    bank = bankName,
                                    account = accountNumber,
                                    holder = accountHolder,
                                    onSuccess = {
                                        showEditDialog = false
                                        android.widget.Toast.makeText(
                                            context,
                                            "사무실 정보가 업데이트되었습니다",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    onError = { error ->
                                        android.widget.Toast.makeText(
                                            context,
                                            "오류: $error",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                            },
                            isLoading = isLoading
                        )
                    }
                }

                // 랜딩 페이지 관리 섹션 (읽기 전용)
                item {
                    LandingPageManagementSection(
                        currentUrl = officeSettings?.landingPageUrl ?: ""
                    )
                }

                // 회원관리 통계 섹션 (읽기 전용)
                item {
                    MemberManagementStatsSection(
                        stats = attributionStats
                    )
                }

                // 최근 매칭 결과 섹션
                item {
                    RecentAttributionMatchesSection(
                        attributions = recentAttributions
                    )
                }

                // 초대 코드 섹션 제거됨 (기능 미구현)
            }
        }
    }
}

@Composable
fun OfficeStatusSection(kpiMetrics: com.designated.callmanager.service.BasicKPI?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "내 사무실 현황",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (kpiMetrics != null) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        listOf(
                            SimpleStatusItem("총 회원", "${kpiMetrics.totalCustomers}명"),
                            SimpleStatusItem("활성 회원", "${kpiMetrics.activeCustomers}명"),
                            SimpleStatusItem("이번 달 콜", "${kpiMetrics.totalCalls}건"),
                            SimpleStatusItem("앱 콜", "${String.format("%.1f", kpiMetrics.appCallRatio)}%")
                        )
                    ) { item ->
                        SimpleStatusCard(item)
                    }
                }
            } else {
                Text(
                    "현황 데이터를 불러오는 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SimpleStatusCard(item: SimpleStatusItem) {
    Card(
        modifier = Modifier.width(120.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                item.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                item.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun QRCodeManagementSection(
    officeSettings: OfficeSettings?,
    qrCodeBitmap: android.graphics.Bitmap?,
    onDownloadQR: () -> Unit,
    onShareQR: () -> Unit,
    onEditOfficeInfo: () -> Unit
) {
    var showQRDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "QR 코드 관리",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR 코드 표시 영역
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally)
                    .border(
                        2.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(8.dp)
                    )
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .clickable(enabled = qrCodeBitmap != null) { showQRDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (qrCodeBitmap != null) {
                    Image(
                        bitmap = qrCodeBitmap.asImageBitmap(),
                        contentDescription = "QR 코드",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "QR 코드 없음",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR 코드 액션 버튼들
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDownloadQR,
                    modifier = Modifier.weight(1f),
                    enabled = officeSettings?.qrCode?.isNotEmpty() == true
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("다운로드")
                }
                OutlinedButton(
                    onClick = onShareQR,
                    modifier = Modifier.weight(1f),
                    enabled = officeSettings?.qrCode?.isNotEmpty() == true
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("공유")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 사무실 정보 수정 버튼
            Button(
                onClick = onEditOfficeInfo,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("사무실 정보 수정")
            }
        }
    }

    // QR 코드 확대 다이얼로그
    if (showQRDialog && qrCodeBitmap != null) {
        QRCodeEnlargedDialog(
            qrCodeBitmap = qrCodeBitmap,
            onDismiss = { showQRDialog = false }
        )
    }
}

@Composable
fun LandingPageManagementSection(
    currentUrl: String
) {
    // URL 편집 기능 제거 - 읽기 전용

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "랜딩 페이지 관리",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "현재 URL:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                currentUrl.ifEmpty { "설정되지 않음" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (currentUrl.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            // URL 편집 기능 제거됨 - 자동 설정된 URL만 표시
        }
    }
}

@Composable
fun MemberManagementStatsSection(
    stats: com.designated.callmanager.service.AttributionStats?
) {
    // 임계값 설정 기능 제거 - 읽기 전용

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "회원관리 통계",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                // 임계값 설정 버튼 제거됨
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (stats != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("총 회원수", "${stats.totalAttributions}건")
                    StatItem("활성 회원수", "${stats.highScoreAttributions}건")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("평균 점수", "${String.format("%.1f", stats.averageScore)}점")
                    StatItem("현재 임계값", "${stats.threshold}점")
                }
            } else {
                Text(
                    "통계 데이터를 불러오는 중...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    // 임계값 설정 다이얼로그 제거됨
}

// InviteCodeSection 제거됨 (초대코드 기능 미구현)

@Composable
fun StatItem(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

// 데이터 클래스들
data class SimpleStatusItem(
    val label: String,
    val value: String
)



@Composable
fun RecentAttributionMatchesSection(
    attributions: List<com.designated.callmanager.service.AttributionMatch>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PersonSearch,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "최근 매칭 결과",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "최근 10건",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (attributions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "최근 매칭 결과가 없습니다",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                attributions.forEach { attribution ->
                    AttributionMatchItem(attribution = attribution)
                    if (attribution != attributions.last()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AttributionMatchItem(attribution: com.designated.callmanager.service.AttributionMatch) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 매칭 점수 배지
        AttributionScoreBadge(score = attribution.attributionScore)

        Spacer(modifier = Modifier.width(12.dp))

        // 고객 정보
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                attribution.customerName.ifEmpty { "이름 없음" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                attribution.customerPhone,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // 기사 추천 정보 표시
            if (!attribution.referralDriverName.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PersonAdd,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "추천: ${attribution.referralDriverName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 매칭 소스 배지
        AttributionSourceBadge(source = attribution.attributionSource)

        Spacer(modifier = Modifier.width(8.dp))

        // 매칭 시간
        Column(
            horizontalAlignment = Alignment.End
        ) {
            Text(
                java.text.SimpleDateFormat("MM.dd", java.util.Locale.getDefault())
                    .format(attribution.matchedAt.toDate()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(attribution.matchedAt.toDate()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AttributionScoreBadge(score: Int) {
    val (color, text) = when {
        score >= 80 -> Pair(Color(0xFF4CAF50), "높음")
        score >= 60 -> Pair(Color(0xFFFF9800), "보통")
        else -> Pair(Color(0xFFFF5722), "낮음")
    }

    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            )
            .border(
                1.dp,
                color.copy(alpha = 0.3f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${score}점",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AttributionSourceBadge(source: String) {
    val (text, color) = when (source) {
        "qr_scan" -> "QR 스캔" to Color(0xFF4CAF50)
        "landing" -> "랜딩페이지" to Color(0xFF2196F3)
        "referral" -> "추천" to Color(0xFFFF9800)
        "manual" -> "수동" to Color(0xFF9C27B0)
        else -> "기타" to MaterialTheme.colorScheme.outline
    }

    Box(
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(8.dp)
            )
            .border(
                1.dp,
                color.copy(alpha = 0.3f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}


@Composable
fun QRCodeEnlargedDialog(
    qrCodeBitmap: android.graphics.Bitmap,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기")
            }
        },
        title = {
            Text(
                "QR 코드",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrCodeBitmap.asImageBitmap(),
                    contentDescription = "확대된 QR 코드",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfficeInfoEditDialog(
    phone: String,
    bankName: String,
    accountNumber: String,
    accountHolder: String,
    onPhoneChange: (String) -> Unit,
    onBankNameChange: (String) -> Unit,
    onAccountNumberChange: (String) -> Unit,
    onAccountHolderChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    isLoading: Boolean
) {
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() }
    ) {
        Card {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "사무실 정보 수정",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "수정 후 QR 코드가 자동으로 재생성됩니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("사무실 전화번호") },
                    placeholder = { Text("예: 031-123-4567") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = bankName,
                    onValueChange = onBankNameChange,
                    label = { Text("입금 은행") },
                    placeholder = { Text("예: 국민은행") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = onAccountNumberChange,
                    label = { Text("계좌번호") },
                    placeholder = { Text("예: 123-456-789012") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = accountHolder,
                    onValueChange = onAccountHolderChange,
                    label = { Text("예금주") },
                    placeholder = { Text("예: 홍길동") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = onSave,
                        modifier = Modifier.weight(1f),
                        enabled = !isLoading && phone.isNotBlank() &&
                                 bankName.isNotBlank() &&
                                 accountNumber.isNotBlank() &&
                                 accountHolder.isNotBlank()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("저장")
                        }
                    }
                }
            }
        }
    }
}

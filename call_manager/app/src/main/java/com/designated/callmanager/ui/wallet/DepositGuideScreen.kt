package com.designated.callmanager.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DepositGuideScreen(
    viewModel: WalletViewModel,
    onNavigateBack: () -> Unit,
) {
    val account by viewModel.depositAccount.collectAsState()
    val loadState by viewModel.depositLoadState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (account == null) {
            viewModel.loadDepositAccount()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("입금 안내") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val state = loadState) {
                is DepositLoadState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Spacer(Modifier.height(64.dp))
                        CircularProgressIndicator()
                    }
                }
                is DepositLoadState.Error -> {
                    ErrorCard(
                        message = state.message,
                        onRetry = { viewModel.loadDepositAccount() },
                    )
                }
                else -> {
                    val current = account
                    if (current != null) {
                        AccountCard(account = current, context = context)
                        Spacer(Modifier.height(16.dp))
                        InstructionsCard()
                        if (!current.contactPhone.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            ContactCard(phone = current.contactPhone, context = context)
                        }
                    } else if (state is DepositLoadState.Idle) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Spacer(Modifier.height(64.dp))
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(account: DepositAccount, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "콜마당 본부 입금계좌",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            AccountField(label = "은행명", value = account.bankName, copyable = false, context = context)
            Spacer(Modifier.height(8.dp))
            AccountField(
                label = "계좌번호",
                value = account.accountNumber,
                copyable = true,
                context = context,
            )
            Spacer(Modifier.height(8.dp))
            AccountField(label = "예금주", value = account.accountHolder, copyable = false, context = context)
        }
    }
}

@Composable
private fun AccountField(
    label: String,
    value: String,
    copyable: Boolean,
    context: Context,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.width(72.dp),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 13.sp,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        if (copyable) {
            IconButton(onClick = {
                copyToClipboard(context, label, value)
            }) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "$label 복사")
            }
        }
    }
}

@Composable
private fun InstructionsCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "입금 절차",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            BulletLine("1. 위 계좌로 충전할 금액을 이체합니다.")
            BulletLine("2. 콜마당 본부에 입금자명·금액을 알려주세요.")
            BulletLine("3. 본부 확인 후 잔액에 반영됩니다 (수동 처리, 영업일 기준).")
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(
                "1원 = 1포인트. 5,000P 미만이면 식당 콜 수임이 차단되니 여유롭게 충전하세요.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun ContactCard(phone: String, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "콜마당 본부",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(phone, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Button(onClick = {
                runCatching {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                    context.startActivity(intent)
                }.onFailure {
                    Toast.makeText(context, "전화 앱을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("전화")
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRetry) {
                    Text("다시 시도")
                }
            }
        }
    }
}

@Composable
private fun BulletLine(text: String) {
    Text(text, fontSize = 14.sp, modifier = Modifier.padding(vertical = 2.dp))
}

private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService<ClipboardManager>()
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "$label 복사됨", Toast.LENGTH_SHORT).show()
}

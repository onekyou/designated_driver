package com.designated.callmanager.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val krw: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawalRequestScreen(
    viewModel: WalletViewModel,
    onNavigateBack: () -> Unit,
) {
    val pointsInfo by viewModel.pointsInfo.collectAsState()
    val withdrawalState by viewModel.withdrawalState.collectAsState()
    val balance = pointsInfo?.balance ?: 0

    var amountText by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountHolder by remember { mutableStateOf("") }

    val isSubmitting = withdrawalState is WithdrawalUiState.Submitting
    val errorMessage = (withdrawalState as? WithdrawalUiState.Error)?.message
    val successState = withdrawalState as? WithdrawalUiState.Success

    LaunchedEffect(Unit) {
        viewModel.resetWithdrawalState()
    }

    if (successState != null) {
        AlertDialog(
            onDismissRequest = { /* 차단 */ },
            title = { Text("출금 신청 완료") },
            text = {
                Column {
                    Text("총관리자 확인 후 입금 처리됩니다.")
                    if (successState.requestId.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "신청번호: ${successState.requestId.take(12)}…",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetWithdrawalState()
                    onNavigateBack()
                }) { Text("확인") }
            },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("출금 신청") },
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
            BalanceLine(balance = balance)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    amountText = input.filter { it.isDigit() }.take(9)
                },
                label = { Text("출금 금액 (P)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it.take(20) },
                label = { Text("은행명") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = accountNumber,
                onValueChange = { input ->
                    accountNumber = input.filter { it.isDigit() || it == '-' }.take(30)
                },
                label = { Text("계좌번호") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = accountHolder,
                onValueChange = { accountHolder = it.take(20) },
                label = { Text("예금주") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            )

            if (errorMessage != null) {
                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        errorMessage,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val amount = amountText.toIntOrNull() ?: 0
                    viewModel.submitWithdrawal(
                        amount = amount,
                        bankName = bankName,
                        accountNumber = accountNumber,
                        accountHolder = accountHolder,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White,
                    )
                } else {
                    Text("출금 신청")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "신청 후 총관리자(콜마당 본부)가 사무실 계좌로 실 입금한 뒤 처리합니다.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun BalanceLine(balance: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "출금 가능 잔액",
            color = MaterialTheme.colorScheme.outline,
            fontSize = 13.sp,
        )
        Box {
            Text(
                "${krw.format(balance)} P",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

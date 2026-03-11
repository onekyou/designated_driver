package com.designated.customer.ui.auth

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhoneAuthScreen(
    modifier: Modifier = Modifier,
    onAuthSuccess: (String) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context as Activity

    val viewModel: PhoneAuthViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val uiState = viewModel.uiState

    // 인증 성공 시 전화번호 반환
    LaunchedEffect(uiState.isVerified) {
        if (uiState.isVerified) {
            onAuthSuccess(uiState.phoneNumber)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (onBack != null && !uiState.isCodeSent) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Start
            ) {
                TextButton(onClick = onBack) {
                    Text("< 뒤로")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "전화번호 인증",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (!uiState.isCodeSent) {
            // 전화번호 입력 단계
            Text(
                text = "휴대폰 번호를 입력해주세요",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = uiState.phoneNumber,
                onValueChange = viewModel::updatePhoneNumber,
                label = { Text("전화번호") },
                placeholder = { Text("01012345678") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            Button(
                onClick = { viewModel.sendVerificationCode(activity) },
                enabled = !uiState.isLoading && uiState.phoneNumber.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("인증번호 발송")
                }
            }
        } else {
            // 인증번호 입력 단계
            Text(
                text = "${uiState.phoneNumber}로\n인증번호가 발송되었습니다",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            OutlinedTextField(
                value = uiState.verificationCode,
                onValueChange = viewModel::updateVerificationCode,
                label = { Text("인증번호") },
                placeholder = { Text("6자리 인증번호") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                singleLine = true
            )

            Button(
                onClick = { viewModel.verifyCode() },
                enabled = !uiState.isLoading && uiState.verificationCode.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("인증하기")
                }
            }

            TextButton(
                onClick = { viewModel.sendVerificationCode(activity) },
                enabled = !uiState.isLoading
            ) {
                Text("인증번호 재발송")
            }
        }

        // 에러 표시
        uiState.error?.let { error ->
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
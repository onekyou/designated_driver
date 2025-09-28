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
import com.designated.customer.service.AttributionService
import com.designated.customer.util.FingerprintManager

@Composable
fun PhoneAuthScreen(
    modifier: Modifier = Modifier,
    onAuthSuccess: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    // 임시로 ViewModel을 여기서 생성
    val viewModel = remember {
        PhoneAuthViewModel(
            fingerprintManager = FingerprintManager(context),
            attributionService = AttributionService()
        )
    }
    val uiState = viewModel.uiState

    // 인증 성공 시 메인 화면으로 이동
    LaunchedEffect(uiState.attributionResult) {
        if (uiState.attributionResult?.success == true) {
            uiState.attributionResult.officeId?.let { officeId ->
                onAuthSuccess(officeId)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 제목
        Text(
            text = "전화번호 인증",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "안전한 서비스 이용을 위해\n전화번호 인증이 필요합니다",
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        if (!uiState.isCodeSent) {
            // 전화번호 입력 단계
            PhoneNumberInput(
                phoneNumber = uiState.phoneNumber,
                onPhoneNumberChange = viewModel::updatePhoneNumber,
                onSendCode = { viewModel.sendVerificationCode(activity) },
                isLoading = uiState.isLoading
            )
        } else {
            // 인증 코드 입력 단계
            VerificationCodeInput(
                phoneNumber = uiState.phoneNumber,
                verificationCode = uiState.verificationCode,
                onCodeChange = viewModel::updateVerificationCode,
                onVerifyCode = viewModel::verifyCode,
                onResendCode = { viewModel.sendVerificationCode(activity) },
                isLoading = uiState.isLoading,
                isVerified = uiState.isVerified
            )
        }

        // 에러 메시지
        if (uiState.error != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = uiState.error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // 어트리뷰션 결과 처리
        if (uiState.isVerified && uiState.attributionResult != null) {
            AttributionResultView(
                result = uiState.attributionResult,
                onSelectOffice = viewModel::selectOffice
            )
        }
    }
}

@Composable
private fun PhoneNumberInput(
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onSendCode: () -> Unit,
    isLoading: Boolean
) {
    OutlinedTextField(
        value = phoneNumber,
        onValueChange = onPhoneNumberChange,
        label = { Text("전화번호") },
        placeholder = { Text("010-1234-5678") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(modifier = Modifier.height(24.dp))

    Button(
        onClick = onSendCode,
        enabled = !isLoading && phoneNumber.isNotEmpty(),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Text("인증번호 전송")
        }
    }
}

@Composable
private fun VerificationCodeInput(
    phoneNumber: String,
    verificationCode: String,
    onCodeChange: (String) -> Unit,
    onVerifyCode: () -> Unit,
    onResendCode: () -> Unit,
    isLoading: Boolean,
    isVerified: Boolean
) {
    Text(
        text = "$phoneNumber 로\n인증번호가 전송되었습니다",
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 24.dp)
    )

    OutlinedTextField(
        value = verificationCode,
        onValueChange = onCodeChange,
        label = { Text("인증번호") },
        placeholder = { Text("6자리 숫자") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = !isVerified
    )

    Spacer(modifier = Modifier.height(16.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onResendCode,
            enabled = !isLoading && !isVerified,
            modifier = Modifier.weight(1f)
        ) {
            Text("재전송")
        }

        Button(
            onClick = onVerifyCode,
            enabled = !isLoading && !isVerified && verificationCode.isNotEmpty(),
            modifier = Modifier.weight(1f)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else if (isVerified) {
                Text("✓ 인증완료")
            } else {
                Text("인증하기")
            }
        }
    }

    if (isVerified) {
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✓ 전화번호 인증 완료",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "사무실 연결 중...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun AttributionResultView(
    result: com.designated.customer.data.model.AttributionResult,
    onSelectOffice: (String) -> Unit
) {
    Spacer(modifier = Modifier.height(24.dp))

    when {
        result.success -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✓ 사무실 연결 완료",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "사무실 ID: ${result.officeId}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (result.score != null) {
                        Text(
                            text = "매칭 점수: ${result.score}점",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        result.requiresManualConfirmation -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "수동 확인 필요",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "사무실: ${result.officeId}",
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "매칭 점수: ${result.score}점 (확신도: ${result.confidence})",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { /* 다른 사무실 선택 */ },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("다른 선택")
                        }

                        Button(
                            onClick = { result.officeId?.let(onSelectOffice) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("확인")
                        }
                    }
                }
            }
        }

        result.requiresManualEntry -> {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "사무실을 찾을 수 없습니다",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "이용하시려는 사무실을 직접 선택해주세요",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { /* 사무실 목록 표시 */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("사무실 선택")
                    }
                }
            }
        }
    }
}
package com.designated.callmanager.ui.signup

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * 클립보드에서 초대 토큰 추출.
 * - 전체 URL 이면 ?t= 또는 &t= 뒤 문자열 반환
 * - base64url 형식의 단독 토큰이면 그대로 반환
 * - 그 외는 null
 */
private fun extractInviteTokenFromClipboard(text: String?): String? {
    if (text.isNullOrBlank()) return null
    val trimmed = text.trim()
    val urlMatch = Regex("""[?&]t=([A-Za-z0-9_-]+)""").find(trimmed)
    if (urlMatch != null) return urlMatch.groupValues[1]
    // URL 아니고 bare 토큰 형태 (base64url, 20자 이상)
    if (Regex("""^[A-Za-z0-9_-]{20,}$""").matches(trimmed)) return trimmed
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = viewModel(
        factory = SignUpViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    onSignUpSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(signUpState) {
        when (val state = signUpState) {
            is SignUpState.Success -> {
                Toast.makeText(context, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                onSignUpSuccess()
            }
            is SignUpState.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Short
                    )
                }
                viewModel.resetSignUpState()
            }
            else -> { /* Idle, Loading 등은 UI 요소에서 처리 */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("사장님 회원가입") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 안내 메시지
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "총관리자로부터 받은 초대 링크/토큰으로 가입합니다.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "이메일과 비밀번호는 사장님이 직접 설정하시며, 이후 웹/앱 모두에서 동일하게 사용됩니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                label = { Text("이메일") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                label = { Text("비밀번호 (6자 이상)") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = if (passwordVisible) "Hide password" else "Show password")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            OutlinedTextField(
                value = viewModel.confirmPassword,
                onValueChange = { viewModel.confirmPassword = it },
                label = { Text("비밀번호 확인") },
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(imageVector = image, contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            OutlinedTextField(
                value = viewModel.inviteToken,
                onValueChange = { viewModel.inviteToken = it.trim() },
                label = { Text("초대 토큰") },
                placeholder = { Text("총관리자에게 받은 링크/토큰 붙여넣기") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                            val token = extractInviteTokenFromClipboard(clipText)
                            if (token != null) {
                                viewModel.inviteToken = token
                                Toast.makeText(context, "토큰을 붙여넣었습니다", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "클립보드에 유효한 토큰/URL이 없습니다",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = signUpState !is SignUpState.Loading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentPaste,
                            contentDescription = "클립보드에서 붙여넣기"
                        )
                    }
                },
                supportingText = {
                    Text("초대 URL 또는 토큰을 복사한 뒤 오른쪽 📋 아이콘을 누르면 자동 입력됩니다.")
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.signUp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            ) {
                if (signUpState is SignUpState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("회원가입")
                }
            }
        }
    }
}

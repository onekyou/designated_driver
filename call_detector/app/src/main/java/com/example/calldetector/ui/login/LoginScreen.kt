package com.example.calldetector.ui.login

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    onLoginComplete: (regionId: String, officeId: String) -> Unit, // 로그인 완료 콜백
    onNavigateToPasswordReset: () -> Unit // 비밀번호 찾기 화면 이동 콜백
) {
    val loginState by viewModel.loginState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // 로그인 상태 변경 감지 (오류 표시, 성공 시 콜백 호출)
    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Short
                )
                viewModel.resetLoginState() // 오류 표시 후 상태 초기화
            }
            is LoginState.Success -> {
                onLoginComplete(state.regionId, state.officeId) // 콜백 호출
                viewModel.resetLoginState() // 성공 처리 후 상태 초기화
            }
            else -> { /* Idle, Loading */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("콜디텍터 관리자 로그인", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    label = { Text("이메일") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = viewModel.password,
                    onValueChange = { viewModel.password = it },
                    label = { Text("비밀번호") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        IconButton(onClick = { showPassword = !showPassword }){
                            Icon(imageVector = image, contentDescription = if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 자동 로그인 체크박스
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = viewModel.autoLogin,
                        onCheckedChange = { viewModel.toggleAutoLogin(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.onBackground,
                            checkmarkColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Text(
                        text = "자동 로그인",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // 로그인 버튼
                Button(
                    onClick = { viewModel.login() },
                    enabled = loginState != LoginState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    if (loginState == LoginState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), 
                            color = MaterialTheme.colorScheme.background, 
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("로그인")
                    }
                }

                // 회원가입 버튼 제거됨

                // 비밀번호 찾기 버튼
                TextButton(
                    onClick = onNavigateToPasswordReset,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("비밀번호를 잊으셨나요?")
                }
            }
        }
    }
}
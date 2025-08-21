package com.designated.callmanager.ui.login

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onLoginComplete: (regionId: String, officeId: String) -> Unit, // 새로운 콜백 추가
    onNavigateToSignUp: () -> Unit, // 회원가입 화면 이동 콜백
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
                viewModel.resetLoginState() // 성공 처리 후 상태 초기화 추가!
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
                verticalArrangement = Arrangement.spacedBy(12.dp) // Reduced spacing slightly
            ) {
                Text("관리자 로그인", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp)) // Add some space after title

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
                            Icon(imageVector  = image, contentDescription = if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Add auto-login checkbox (align to left)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = viewModel.autoLogin,
                        onCheckedChange = { viewModel.toggleAutoLogin(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary, // DeepYellow from theme
                            uncheckedColor = MaterialTheme.colorScheme.onBackground,
                            checkmarkColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Text(
                        text = "자동 로그인",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Login Button (White background)
                Button(
                    onClick = { viewModel.login() },
                    enabled = loginState != LoginState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground, // Use light grey from theme
                        contentColor = MaterialTheme.colorScheme.background // Use dark text color
                    )
                ) {
                    if (loginState == LoginState.Loading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.background, strokeWidth = 2.dp)
                    } else {
                        Text("로그인")
                    }
                }

                // Sign Up Button (White background)
                Button(
                    onClick = onNavigateToSignUp,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    Text("회원가입")
                }

                // Password Reset Text Button
                TextButton(
                    onClick = onNavigateToPasswordReset,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary // DeepYellow from theme
                    )
                ) {
                    Text("비밀번호를 잊으셨나요?")
                }
            }
        }
    }
}
package com.designated.driverapp.ui.login

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
import com.designated.driverapp.ui.login.LoginViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: (regionId: String, officeId: String, driverId: String) -> Unit,
    onNavigateToPasswordReset: () -> Unit,
    onNavigateToSignUp: () -> Unit 
) {
    val loginState by viewModel.loginState.collectAsState()
    var showPassword by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is LoginState.Error -> {
                errorMessage = state.message
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetLoginState()
            }
            is LoginState.Success -> {
                errorMessage = null
                onLoginSuccess(state.regionId, state.officeId, state.driverId)
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
                Text("기사님 로그인", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    label = { Text("이메일 또는 전화번호") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && viewModel.email.isBlank(),
                    enabled = loginState !is LoginState.Loading
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
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(imageVector = image, contentDescription = if (showPassword) "Hide password" else "Show password")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && viewModel.password.isBlank(),
                    enabled = loginState !is LoginState.Loading
                )

                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = viewModel.autoLogin,
                        onCheckedChange = { viewModel.toggleAutoLogin(it) },
                        enabled = loginState !is LoginState.Loading,
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary, 
                            uncheckedColor = MaterialTheme.colorScheme.onBackground,
                            checkmarkColor = MaterialTheme.colorScheme.background
                        )
                    )
                    Text(
                        text = "자동 로그인 (아이디/비번 기억)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Button(
                    onClick = {
                        if (viewModel.email.isBlank() || viewModel.password.isBlank()) {
                            errorMessage = "이메일(또는 전화번호)과 비밀번호를 모두 입력해주세요."
                        } else {
                            errorMessage = null
                            viewModel.login()
                        }
                    },
                    enabled = loginState !is LoginState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground, 
                        contentColor = MaterialTheme.colorScheme.background 
                    )
                ) {
                    if (loginState == LoginState.Loading) {
                        Box(
                            modifier = Modifier.size(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.background,
                                strokeWidth = 2.dp,
                                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                            )
                        }
                    } else {
                        Text("로그인")
                    }
                }

                TextButton(
                    onClick = onNavigateToPasswordReset,
                    enabled = loginState !is LoginState.Loading,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary 
                    )
                ) {
                    Text("비밀번호를 잊으셨나요?")
                }

                TextButton(
                    onClick = onNavigateToSignUp,
                    enabled = loginState !is LoginState.Loading,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("아직 계정이 없으신가요? 회원가입 신청")
                }
            }
        }
    }
} 
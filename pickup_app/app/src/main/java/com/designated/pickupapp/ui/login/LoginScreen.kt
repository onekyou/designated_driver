package com.designated.pickupapp.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log

@Composable
fun LoginScreen(
    loginViewModel: LoginViewModel = hiltViewModel(),
    onLoginSuccess: (regionId: String, officeId: String, driverId: String) -> Unit,
    onNavigateToPasswordReset: () -> Unit,
    onNavigateToSignUp: () -> Unit 
) {
    val loginState by loginViewModel.loginState.collectAsState()
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
                loginViewModel.resetLoginState()
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
                Text("픽업 기사님 로그인", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = loginViewModel.email,
                    onValueChange = { loginViewModel.email = it },
                    label = { Text("이메일 또는 전화번호") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && loginViewModel.email.isBlank(),
                    enabled = loginState !is LoginState.Loading
                )

                OutlinedTextField(
                    value = loginViewModel.password,
                    onValueChange = { loginViewModel.password = it },
                    label = { Text("비밀번호") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        val image = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                        val description = if (showPassword) "비밀번호 숨기기" else "비밀번호 보기"

                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(imageVector = image, contentDescription = description)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && loginViewModel.password.isBlank(),
                    enabled = loginState !is LoginState.Loading
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { loginViewModel.login() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loginState !is LoginState.Loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (loginState is LoginState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("로그인")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onNavigateToSignUp,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = loginState !is LoginState.Loading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("회원가입")
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onNavigateToPasswordReset,
                    enabled = loginState !is LoginState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("비밀번호를 잊으셨나요?")
                }
            }
        }
    }
}
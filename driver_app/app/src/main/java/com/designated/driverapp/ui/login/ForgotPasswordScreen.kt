package com.designated.driverapp.ui.login

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// 상태 관리를 위한 sealed 클래스
sealed class PasswordResetState {
    object Idle : PasswordResetState()
    object Loading : PasswordResetState()
    object Success : PasswordResetState()
    data class Error(val message: String) : PasswordResetState()
}

class ForgotPasswordViewModel : ViewModel() {
    private val TAG = "ForgotPasswordViewModel"
    private val auth: FirebaseAuth = Firebase.auth

    private val _resetState = MutableStateFlow<PasswordResetState>(PasswordResetState.Idle)
    val resetState: StateFlow<PasswordResetState> = _resetState

    var email by mutableStateOf("")

    fun resetPassword() {
        if (email.isBlank()) {
            _resetState.value = PasswordResetState.Error("이메일을 입력해주세요.")
            return
        }

        _resetState.value = PasswordResetState.Loading
        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                Log.d(TAG, "비밀번호 재설정 메일 전송 완료: $email")
                _resetState.value = PasswordResetState.Success
            } catch (e: Exception) {
                Log.e(TAG, "비밀번호 재설정 메일 전송 실패", e)
                _resetState.value = PasswordResetState.Error(e.message ?: "비밀번호 재설정 메일 전송에 실패했습니다.")
            }
        }
    }

    fun resetState() {
        _resetState.value = PasswordResetState.Idle
    }

    class Factory : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ForgotPasswordViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ForgotPasswordViewModel() as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel = viewModel(
        factory = ForgotPasswordViewModel.Factory()
    ),
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit
) {
    val resetState by viewModel.resetState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resetState) {
        when (val state = resetState) {
            is PasswordResetState.Error -> {
                errorMessage = state.message
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
                viewModel.resetState()
            }
            is PasswordResetState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "비밀번호 재설정 메일이 전송되었습니다. 이메일을 확인해주세요.",
                    duration = SnackbarDuration.Long
                )
                onResetSuccess()
            }
            else -> { /* Idle, Loading */ }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("비밀번호 재설정") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로 가기"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "비밀번호를 잊으셨나요?",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Text(
                    "계정에 등록된 이메일을 입력하시면 비밀번호 재설정 링크를 보내드립니다.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = viewModel.email,
                    onValueChange = { viewModel.email = it },
                    label = { Text("이메일") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage != null && viewModel.email.isBlank()
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
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { viewModel.resetPassword() },
                    enabled = resetState != PasswordResetState.Loading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (resetState == PasswordResetState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("비밀번호 재설정 메일 보내기")
                    }
                }
            }
        }
    }
} 
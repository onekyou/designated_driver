package com.designated.callmanager.ui.signup

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = viewModel(
        factory = SignUpViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    onSignUpSuccess: () -> Unit, // Callback for successful sign-up
    onNavigateBack: () -> Unit // Callback to navigate back (e.g., to login)
) {
    val context = LocalContext.current
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()

    val regions by viewModel.regions.collectAsStateWithLifecycle()
    val offices by viewModel.offices.collectAsStateWithLifecycle()

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var expandedRegion by remember { mutableStateOf(false) }
    var expandedOffice by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Handle SignUpState changes (show messages, navigate on success)
    LaunchedEffect(signUpState) {
        when (val state = signUpState) {
            is SignUpState.Success -> {
                Toast.makeText(context, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                onSignUpSuccess() // Navigate after success
            }
            is SignUpState.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = state.message,
                        duration = SnackbarDuration.Short
                    )
                }
                viewModel.resetSignUpState() // Reset state after showing error
            }
            else -> { /* Idle, Loading, etc. Handled by UI elements */ }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("관리자 회원가입") },
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
                .verticalScroll(rememberScrollState()), // Make column scrollable
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                label = { Text("이메일") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading // Disable when loading
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
                    IconButton(onClick = { passwordVisible = !passwordVisible }){
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
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }){
                        Icon(imageVector = image, contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

             OutlinedTextField(
                value = viewModel.adminName,
                onValueChange = { viewModel.adminName = it },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Region Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedRegion,
                onExpandedChange = { expandedRegion = !expandedRegion },
                 modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.selectedRegion?.name ?: "지역 선택",
                    onValueChange = {}, // Read only
                    readOnly = true,
                    label = { Text("지역") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRegion) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(), // Important for menu positioning
                    enabled = signUpState !is SignUpState.Loading && signUpState !is SignUpState.LoadingRegions
                )
                ExposedDropdownMenu(
                    expanded = expandedRegion,
                    onDismissRequest = { expandedRegion = false },
                     modifier = Modifier.fillMaxWidth()
                ) {
                    if (signUpState is SignUpState.LoadingRegions) {
                         DropdownMenuItem(
                            text = { Text("지역 목록 로딩 중...") },
                            onClick = { },
                            enabled = false
                        )
                    } else {
                        regions.forEach { region ->
                            DropdownMenuItem(
                                text = { Text(region.name) },
                                onClick = {
                                    viewModel.onRegionSelected(region)
                                    expandedRegion = false
                                }
                            )
                        }
                    }
                }
            }

            // Office Dropdown (Enabled only when region is selected)
             ExposedDropdownMenuBox(
                expanded = expandedOffice,
                onExpandedChange = { if (viewModel.selectedRegion != null) expandedOffice = !expandedOffice }, // Enable only if region selected
                 modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.selectedOffice?.name ?: "사무실 선택",
                    onValueChange = {}, // Read only
                    readOnly = true,
                    label = { Text("사무실") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedOffice) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = viewModel.selectedRegion != null && signUpState !is SignUpState.Loading && signUpState !is SignUpState.LoadingOffices
                )
                ExposedDropdownMenu(
                    expanded = expandedOffice,
                    onDismissRequest = { expandedOffice = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                     if (viewModel.selectedRegion == null) {
                         DropdownMenuItem(
                            text = { Text("지역을 먼저 선택해주세요.") },
                            onClick = { },
                            enabled = false
                        )
                    } else if (signUpState is SignUpState.LoadingOffices) {
                         DropdownMenuItem(
                            text = { Text("사무실 목록 로딩 중...") },
                            onClick = { },
                            enabled = false
                        )
                    } else if (offices.isEmpty()) {
                         DropdownMenuItem(
                            text = { Text("선택 가능한 사무실이 없습니다.") },
                            onClick = { },
                            enabled = false
                        )
                    } else {
                        offices.forEach { office ->
                            DropdownMenuItem(
                                text = { Text(office.name) },
                                onClick = {
                                    viewModel.onOfficeSelected(office)
                                    expandedOffice = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.signUp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState != SignUpState.Loading &&
                          signUpState != SignUpState.LoadingRegions &&
                          signUpState != SignUpState.LoadingOffices // Disable while any loading
            ) {
                if (signUpState == SignUpState.Loading) {
                     CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("회원가입")
                }
            }
        }
    }
} 
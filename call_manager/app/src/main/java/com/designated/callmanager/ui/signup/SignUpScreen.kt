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
    onSignUpSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()

    val provinces by viewModel.provinces.collectAsStateWithLifecycle()
    val cities by viewModel.cities.collectAsStateWithLifecycle()

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var expandedProvince by remember { mutableStateOf(false) }
    var expandedCity by remember { mutableStateOf(false) }
    var expandedBank by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState()),
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

            // 도/시 선택
            ExposedDropdownMenuBox(
                expanded = expandedProvince,
                onExpandedChange = { expandedProvince = !expandedProvince },
                 modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.selectedProvince?.name ?: "도/시 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("도/시") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProvince) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = signUpState !is SignUpState.Loading && signUpState !is SignUpState.LoadingRegions
                )
                ExposedDropdownMenu(
                    expanded = expandedProvince,
                    onDismissRequest = { expandedProvince = false },
                     modifier = Modifier.fillMaxWidth()
                ) {
                    if (signUpState is SignUpState.LoadingRegions) {
                         DropdownMenuItem(
                            text = { Text("지역 목록 로딩 중...") },
                            onClick = { },
                            enabled = false
                        )
                    } else {
                        provinces.forEach { province ->
                            DropdownMenuItem(
                                text = { Text(province.name) },
                                onClick = {
                                    viewModel.onProvinceSelected(province)
                                    expandedProvince = false
                                }
                            )
                        }
                    }
                }
            }

            // 시/군/구 선택 (도 타입일 때만 표시, 광역시/특별자치시는 자동 선택)
            if (viewModel.selectedProvince?.type == "do") {
                ExposedDropdownMenuBox(
                    expanded = expandedCity,
                    onExpandedChange = { expandedCity = !expandedCity },
                     modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = viewModel.selectedCity?.name ?: "시/군/구 선택",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("시/군/구") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCity) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        enabled = viewModel.selectedProvince != null && signUpState !is SignUpState.Loading
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCity,
                        onDismissRequest = { expandedCity = false },
                         modifier = Modifier.fillMaxWidth()
                    ) {
                        if (cities.isEmpty()) {
                             DropdownMenuItem(
                                text = { Text("먼저 도/시를 선택하세요") },
                                onClick = { },
                                enabled = false
                            )
                        } else {
                            cities.forEach { city ->
                                DropdownMenuItem(
                                    text = { Text(city.name) },
                                    onClick = {
                                        viewModel.onCitySelected(city)
                                        expandedCity = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

             OutlinedTextField(
                value = viewModel.officeName,
                onValueChange = { viewModel.officeName = it },
                label = { Text("사무실 이름") },
                placeholder = { Text("예: 바로콜 대리운전") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            OutlinedTextField(
                value = viewModel.officePhone,
                onValueChange = { viewModel.officePhone = it },
                label = { Text("사무실 전화번호") },
                placeholder = { Text("예: 031-123-4567") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            ExposedDropdownMenuBox(
                expanded = expandedBank,
                onExpandedChange = { expandedBank = it && signUpState !is SignUpState.Loading },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.bankName.ifEmpty { "은행 선택" },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("입금 은행") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedBank) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = signUpState !is SignUpState.Loading
                )
                ExposedDropdownMenu(
                    expanded = expandedBank,
                    onDismissRequest = { expandedBank = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    KoreanBanks.banks.forEach { bank ->
                        DropdownMenuItem(
                            text = { Text(bank) },
                            onClick = {
                                viewModel.bankName = bank
                                expandedBank = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = viewModel.accountNumber,
                onValueChange = { viewModel.accountNumber = it },
                label = { Text("계좌번호") },
                placeholder = { Text("예: 123-456-789012") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            OutlinedTextField(
                value = viewModel.confirmAccountNumber,
                onValueChange = { viewModel.confirmAccountNumber = it },
                label = { Text("계좌번호 확인") },
                placeholder = { Text("계좌번호를 다시 입력하세요") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading,
                isError = viewModel.confirmAccountNumber.isNotEmpty() && viewModel.accountNumber != viewModel.confirmAccountNumber,
                supportingText = {
                    if (viewModel.confirmAccountNumber.isNotEmpty() && viewModel.accountNumber != viewModel.confirmAccountNumber) {
                        Text("계좌번호가 일치하지 않습니다", color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            OutlinedTextField(
                value = viewModel.accountHolder,
                onValueChange = { viewModel.accountHolder = it },
                label = { Text("예금주") },
                placeholder = { Text("예: 홍길동") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState !is SignUpState.Loading
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.signUp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = signUpState != SignUpState.Loading &&
                          signUpState != SignUpState.LoadingRegions
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
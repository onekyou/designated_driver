package com.designated.pickupapp.ui.signup

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = hiltViewModel(),
    onSignUpSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()
    val regions by viewModel.regions.collectAsStateWithLifecycle()
    val offices by viewModel.offices.collectAsStateWithLifecycle()
    
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    var regionExpanded by remember { mutableStateOf(false) }
    var officeExpanded by remember { mutableStateOf(false) }
    
    val isLoadingRegions = signUpState is SignUpState.LoadingRegions
    val isLoadingOffices = signUpState is SignUpState.LoadingOffices  
    val isSigningUp = signUpState is SignUpState.Loading
    
    // 디버그 로그
    LaunchedEffect(signUpState, regions.size, offices.size) {
        android.util.Log.d("SignUpScreen", "상태 변경: signUpState=$signUpState, regions=${regions.size}, offices=${offices.size}")
        android.util.Log.d("SignUpScreen", "계산된 로딩 상태 - isLoadingRegions=$isLoadingRegions, isLoadingOffices=$isLoadingOffices, isSigningUp=$isSigningUp")
    }

    LaunchedEffect(signUpState) {
        when (val state = signUpState) {
            is SignUpState.Success -> {
                snackbarHostState.showSnackbar(
                    message = "회원가입이 완료되었습니다. 관리자 승인을 기다려주세요.",
                    duration = SnackbarDuration.Long
                )
                onSignUpSuccess()
            }
            is SignUpState.Error -> {
                snackbarHostState.showSnackbar(
                    message = state.message,
                    duration = SnackbarDuration.Long
                )
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("픽업 기사 회원가입 신청") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isSigningUp) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 이메일
            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                label = { Text("이메일") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningUp
            )

            // 비밀번호
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                label = { Text("비밀번호 (6자리 이상)") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showPassword) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningUp
            )

            // 비밀번호 확인
            OutlinedTextField(
                value = viewModel.confirmPassword,
                onValueChange = { viewModel.confirmPassword = it },
                label = { Text("비밀번호 확인") },
                singleLine = true,
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(
                            imageVector = if (showConfirmPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showConfirmPassword) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningUp
            )

            // 이름
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("이름") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningUp
            )

            // 전화번호
            OutlinedTextField(
                value = viewModel.phoneNumber,
                onValueChange = { viewModel.phoneNumber = it },
                label = { Text("전화번호") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningUp
            )

            // 지역 선택
            ExposedDropdownMenuBox(
                expanded = regionExpanded,
                onExpandedChange = { shouldExpand ->
                    android.util.Log.d("SignUpScreen", "지역 드롭다운 클릭: shouldExpand=$shouldExpand, isLoadingRegions=$isLoadingRegions, isSigningUp=$isSigningUp, regions.size=${regions.size}")
                    // 간단한 조건으로 변경 - 지역 데이터가 있고 로딩중이 아닐 때만
                    if (regions.isNotEmpty() && !isSigningUp) {
                        regionExpanded = shouldExpand
                        android.util.Log.d("SignUpScreen", "지역 드롭다운 상태 변경: $regionExpanded")
                    } else {
                        android.util.Log.d("SignUpScreen", "지역 드롭다운 상태 변경 차단됨 - regions.isEmpty=${regions.isEmpty()}, isSigningUp=$isSigningUp")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.selectedRegion?.name ?: "지역 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("소속 지역") },
                    trailingIcon = {
                        if (regions.isEmpty()) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded)
                    },
                    enabled = regions.isNotEmpty() && !isSigningUp,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = regionExpanded && regions.isNotEmpty(),
                    onDismissRequest = { regionExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    regions.forEach { region ->
                        DropdownMenuItem(
                            text = { Text(region.name) },
                            onClick = {
                                android.util.Log.d("SignUpScreen", "지역 선택됨: ${region.name} (${region.id})")
                                viewModel.onRegionSelected(region)
                                regionExpanded = false
                            }
                        )
                    }
                    if (regions.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("지역을 불러올 수 없습니다.") },
                            onClick = { },
                            enabled = false
                        )
                    }
                }
            }

            // 사무실 선택
            ExposedDropdownMenuBox(
                expanded = officeExpanded,
                onExpandedChange = {
                    if (viewModel.selectedRegion != null && !isLoadingOffices && !isSigningUp) {
                        officeExpanded = !officeExpanded
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.selectedOffice?.name ?: "사무실 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("소속 사무실") },
                    trailingIcon = {
                        if (isLoadingOffices) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else ExposedDropdownMenuDefaults.TrailingIcon(expanded = officeExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = viewModel.selectedRegion != null && !isLoadingOffices && !isSigningUp
                )
                ExposedDropdownMenu(
                    expanded = officeExpanded && !isLoadingOffices,
                    onDismissRequest = { officeExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    offices.forEach { office ->
                        DropdownMenuItem(
                            text = { Text(office.name) },
                            onClick = {
                                viewModel.onOfficeSelected(office)
                                officeExpanded = false
                            }
                        )
                    }
                    if (offices.isEmpty() && !isLoadingOffices && viewModel.selectedRegion != null) {
                        DropdownMenuItem(
                            text = { Text("선택 가능한 사무실이 없습니다.") },
                            onClick = { },
                            enabled = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 회원가입 버튼
            Button(
                onClick = { viewModel.signUp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningUp
            ) {
                if (isSigningUp) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("회원가입 신청")
                }
            }

            // 취소 버튼
            TextButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSigningUp
            ) {
                Text("이미 계정이 있으신가요? 로그인")
            }
        }
    }
}
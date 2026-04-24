package com.designated.pickupdriver.ui.login

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    viewModel: SignUpViewModel = viewModel(
        factory = SignUpViewModel.Factory(LocalContext.current.applicationContext as Application)
    ),
    onSignUpSuccess: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val signUpState by viewModel.signUpState.collectAsStateWithLifecycle()
    val provinces by viewModel.provinces.collectAsStateWithLifecycle()
    val cities by viewModel.cities.collectAsStateWithLifecycle()
    val offices by viewModel.offices.collectAsStateWithLifecycle()

    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var provinceExpanded by remember { mutableStateOf(false) }
    var cityExpanded by remember { mutableStateOf(false) }
    var officeExpanded by remember { mutableStateOf(false) }

    val isLoadingProvinces = signUpState == SignUpState.LoadingProvinces
    val isLoadingCities = signUpState == SignUpState.LoadingCities
    val isLoadingOffices = signUpState == SignUpState.LoadingOffices
    val isSigningUp = signUpState == SignUpState.Loading

    LaunchedEffect(signUpState) {
        when (val state = signUpState) {
            is SignUpState.Success -> {
                Toast.makeText(context, "가입 신청 완료! 관리자 승인을 기다려주세요.", Toast.LENGTH_LONG).show()
                onSignUpSuccess()
            }
            is SignUpState.Error -> {
                snackbarHostState.showSnackbar(message = state.message)
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("픽업기사 회원가입 신청") },
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
            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                label = { Text("이메일") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !isSigningUp,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                label = { Text("비밀번호 (6자리 이상)") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                enabled = !isSigningUp,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = viewModel.confirmPassword,
                onValueChange = { viewModel.confirmPassword = it },
                label = { Text("비밀번호 확인") },
                singleLine = true,
                visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (showConfirmPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                        Icon(imageVector = image, contentDescription = null)
                    }
                },
                enabled = !isSigningUp,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            OutlinedTextField(
                value = viewModel.name,
                onValueChange = { viewModel.name = it },
                label = { Text("이름") },
                singleLine = true,
                enabled = !isSigningUp,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = viewModel.phoneNumber,
                onValueChange = { viewModel.phoneNumber = it },
                label = { Text("전화번호") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !isSigningUp,
                modifier = Modifier.fillMaxWidth()
            )

            // 시/도
            ExposedDropdownMenuBox(
                expanded = provinceExpanded,
                onExpandedChange = {
                    if (!isLoadingProvinces && !isSigningUp) provinceExpanded = !provinceExpanded
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.selectedProvince?.name ?: "시/도 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("시/도") },
                    trailingIcon = {
                        if (isLoadingProvinces) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded)
                    },
                    enabled = !isLoadingProvinces && !isSigningUp,
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = provinceExpanded && !isLoadingProvinces,
                    onDismissRequest = { provinceExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    provinces.forEach { province ->
                        DropdownMenuItem(
                            text = { Text(province.name) },
                            onClick = {
                                viewModel.onProvinceSelected(province)
                                provinceExpanded = false
                            }
                        )
                    }
                }
            }

            // 시/군/구
            ExposedDropdownMenuBox(
                expanded = cityExpanded,
                onExpandedChange = {
                    if (viewModel.selectedProvince != null && !isLoadingCities && !isSigningUp) {
                        cityExpanded = !cityExpanded
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = viewModel.selectedCity?.name ?: "시/군/구 선택",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("시/군/구") },
                    trailingIcon = {
                        if (isLoadingCities) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else ExposedDropdownMenuDefaults.TrailingIcon(expanded = cityExpanded)
                    },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    enabled = viewModel.selectedProvince != null && !isLoadingCities && !isSigningUp
                )
                ExposedDropdownMenu(
                    expanded = cityExpanded && !isLoadingCities,
                    onDismissRequest = { cityExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    cities.forEach { city ->
                        DropdownMenuItem(
                            text = { Text(city.name) },
                            onClick = {
                                viewModel.onCitySelected(city)
                                cityExpanded = false
                            }
                        )
                    }
                    if (cities.isEmpty() && !isLoadingCities && viewModel.selectedProvince != null) {
                        DropdownMenuItem(
                            text = { Text("선택 가능한 시/군/구가 없습니다.") },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }

            // 사무실
            ExposedDropdownMenuBox(
                expanded = officeExpanded,
                onExpandedChange = {
                    if (viewModel.selectedCity != null && !isLoadingOffices && !isSigningUp) {
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
                    enabled = viewModel.selectedCity != null && !isLoadingOffices && !isSigningUp
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
                    if (offices.isEmpty() && !isLoadingOffices && viewModel.selectedCity != null) {
                        DropdownMenuItem(
                            text = { Text("선택 가능한 사무실이 없습니다.") },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.signUp() },
                enabled = !isSigningUp && !isLoadingProvinces && !isLoadingCities && !isLoadingOffices,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSigningUp) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Text("가입 신청하기")
                }
            }
        }
    }
}

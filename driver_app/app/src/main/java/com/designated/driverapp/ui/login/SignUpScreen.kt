package com.designated.driverapp.ui.login

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.clickable
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
    val regions by viewModel.regions.collectAsStateWithLifecycle()
    val offices by viewModel.offices.collectAsStateWithLifecycle()

    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var regionExpanded by remember { mutableStateOf(false) }
    var officeExpanded by remember { mutableStateOf(false) }

    val isLoadingRegions = signUpState == SignUpState.LoadingRegions
    val isLoadingOffices = signUpState == SignUpState.LoadingOffices
    val isSigningUp = signUpState == SignUpState.Loading

    Log.d("SignUpScreen", "Recomposing - State: $signUpState, isLoadingRegions: $isLoadingRegions, isSigningUp: $isSigningUp")

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
                 title = { Text("기사 회원가입 신청") },
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
            OutlinedTextField(value = viewModel.email, onValueChange = { viewModel.email = it }, label = { Text("이메일") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), enabled = !isSigningUp, modifier = Modifier.fillMaxWidth())
             OutlinedTextField(value = viewModel.password, onValueChange = { viewModel.password = it }, label = { Text("비밀번호 (6자리 이상)") }, singleLine = true, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
                 val image = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                 IconButton(onClick = { showPassword = !showPassword }) {
                     Icon(imageVector = image, contentDescription = if (showPassword) "Hide password" else "Show password")
                 }
             }, enabled = !isSigningUp, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
             OutlinedTextField(value = viewModel.confirmPassword, onValueChange = { viewModel.confirmPassword = it }, label = { Text("비밀번호 확인") }, singleLine = true, visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
                 val image = if (showConfirmPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                 IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                     Icon(imageVector = image, contentDescription = if (showConfirmPassword) "Hide password" else "Show password")
                 }
             }, enabled = !isSigningUp, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
             OutlinedTextField(value = viewModel.name, onValueChange = { viewModel.name = it }, label = { Text("이름") }, singleLine = true, enabled = !isSigningUp, modifier = Modifier.fillMaxWidth())
             OutlinedTextField(value = viewModel.phoneNumber, onValueChange = { viewModel.phoneNumber = it }, label = { Text("전화번호") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), enabled = !isSigningUp, modifier = Modifier.fillMaxWidth())

            ExposedDropdownMenuBox(
                expanded = regionExpanded,
                onExpandedChange = { shouldExpand ->
                    Log.d("SignUpScreen", "Region onExpandedChange triggered. Current expanded: $regionExpanded, Should change to: $shouldExpand")
                    val currentState = viewModel.signUpState.value
                    val canChange = currentState !is SignUpState.LoadingRegions && currentState !is SignUpState.Loading
                    Log.d("SignUpScreen", "Region canChange based on CURRENT VM state ($currentState): $canChange")
                    if (canChange) {
                        regionExpanded = shouldExpand
                        Log.d("SignUpScreen", "Region expanded state changed to: $regionExpanded")
                    } else {
                        Log.d("SignUpScreen", "Region expansion change blocked by loading state.")
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
                        if (isLoadingRegions) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        else ExposedDropdownMenuDefaults.TrailingIcon(expanded = regionExpanded)
                    },
                    enabled = !isLoadingRegions && !isSigningUp,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                        .also { Log.d("SignUpScreen", "Region Dropdown Enabled: ${!isLoadingRegions && !isSigningUp}") }
                )
                ExposedDropdownMenu(
                    expanded = regionExpanded && !isLoadingRegions,
                    onDismissRequest = { regionExpanded = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    regions.forEach { region ->
                        DropdownMenuItem(
                            text = { Text(region.name) },
                            onClick = {
                                viewModel.onRegionSelected(region)
                                regionExpanded = false
                            }
                        )
                    }
                }
            }

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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.signUp() },
                enabled = !isSigningUp && !isLoadingRegions && !isLoadingOffices,
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
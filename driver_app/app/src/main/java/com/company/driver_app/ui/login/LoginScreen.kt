package com.company.driver_app.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen() {
    // 다크모드 색상 설정
    val backgroundColor = Color(0xFF121212) // 다크모드 배경색
    val deepYellow = Color(0xFFFFD700) // 딥엘로우 색상

    // 상태 변수
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // UI 구성
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "로그인",
                color = deepYellow,
                fontSize = 24.sp
            )
            BasicTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp)
                    .fillMaxWidth(0.8f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White)
            )
            BasicTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier
                    .background(Color(0xFF1E1E1E))
                    .padding(8.dp)
                    .fillMaxWidth(0.8f),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(color = Color.White)
            )
            Button(
                onClick = { /* 로그인 로직 */ },
                colors = ButtonDefaults.buttonColors(containerColor = deepYellow)
            ) {
                Text(text = "로그인", color = Color.Black)
            }
        }
    }
}

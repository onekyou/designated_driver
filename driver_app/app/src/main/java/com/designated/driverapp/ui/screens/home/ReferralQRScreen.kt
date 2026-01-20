package com.designated.driverapp.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralQRScreen(
    driverId: String,
    provinceId: String,
    cityId: String,
    officeId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var driverName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Firestore에서 기사 정보 가져오기
    LaunchedEffect(driverId) {
        try {
            val doc = FirebaseFirestore.getInstance()
                .collection("provinces").document(provinceId)
                .collection("cities").document(cityId)
                .collection("offices").document(officeId)
                .collection("designated_drivers").document(driverId)
                .get()
                .await()

            if (doc.exists()) {
                qrUrl = doc.getString("referralQrUrl")
                driverName = doc.getString("name") ?: ""

                if (qrUrl.isNullOrEmpty()) {
                    errorMessage = "QR 코드 URL이 생성되지 않았습니다.\n관리자에게 문의하세요."
                }
            } else {
                errorMessage = "기사 정보를 찾을 수 없습니다."
            }
        } catch (e: Exception) {
            errorMessage = "QR 코드를 불러오는 중 오류가 발생했습니다."
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("고객 추천하기") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Black,
                    titleContentColor = androidx.compose.ui.graphics.Color.White,
                    navigationIconContentColor = androidx.compose.ui.graphics.Color.White
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator()
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                qrUrl != null -> {
                    // 제목
                    Text(
                        text = "고객 추천 QR 코드",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$driverName 기사님",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // QR 코드
                    QRCodeImage(
                        url = qrUrl!!,
                        modifier = Modifier
                            .size(280.dp)
                            .background(
                                androidx.compose.ui.graphics.Color.White,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 안내 문구
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "💡 사용 방법",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "1. 운행 완료 후 고객에게 QR을 보여주세요\n" +
                                       "2. 고객이 카메라로 스캔하면 앱 설치 페이지로 이동합니다\n" +
                                       "3. 가입한 고객은 자동으로 회원님께 연결됩니다\n" +
                                       "4. 추천 실적은 포인트로 환산됩니다",
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 22.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 통계 정보 (선택사항 - 추후 구현)
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("추천 고객", style = MaterialTheme.typography.bodySmall)
                                Text("준비중", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("이번 달", style = MaterialTheme.typography.bodySmall)
                                Text("준비중", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QRCodeImage(url: String, modifier: Modifier = Modifier) {
    val bitmap = remember(url) {
        generateQRCode(url, 512, 512)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = modifier
        )
    } else {
        Text("QR 코드 생성 실패", color = MaterialTheme.colorScheme.error)
    }
}

private fun generateQRCode(content: String, width: Int, height: Int): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        bitmap
    } catch (e: Exception) {
        null
    }
}

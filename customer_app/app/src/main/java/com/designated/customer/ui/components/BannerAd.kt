package com.designated.customer.ui.components

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.designated.customer.data.model.BannerAdData

/**
 * 배너 광고 컴포저블
 * Firebase Firestore에서 가져온 배너 데이터를 표시
 * 
 * @param bannerData 배너 데이터 (null이면 표시하지 않음)
 * @param onClick 클릭 시 호출되는 콜백 (linkUrl 전달)
 * @param modifier Modifier
 */
@Composable
fun BannerAd(
    bannerData: BannerAdData?,
    onClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // bannerData가 null이거나 비활성화 상태면 표시하지 않음
    if (bannerData == null || !bannerData.isActive) {
        return
    }

    // 텍스트 색상 회색 고정
    val textColor = Color.Gray

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp)
            .clickable {
                // 클릭 시 동작
                if (bannerData.linkUrl.isNotEmpty()) {
                    // 추후 WebView나 외부 브라우저로 연결할 수 있도록 콜백 호출
                    onClick(bannerData.linkUrl)
                } else {
                    // linkUrl이 없으면 Toast 메시지만 표시
                    Toast.makeText(
                        context,
                        "광고 배너입니다. 곧 서비스가 시작됩니다!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
        shape = RoundedCornerShape(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // 이미지가 있으면 이미지 표시, 없으면 텍스트 표시
            if (bannerData.imageUrl.isNotEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bannerData.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = bannerData.text,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // 텍스트만 표시
                Text(
                    text = bannerData.text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

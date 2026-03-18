package com.designated.customer.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun TermsAgreementScreen(
    onTermsAgreed: (termsVersion: String, marketingConsent: Boolean) -> Unit,
    onViewTerms: () -> Unit,
    onViewPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TermsAgreementViewModel = viewModel()
) {
    val state = viewModel.state

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Text(
            text = "서비스 이용 동의",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "서비스 이용을 위해\n아래 약관에 동의해 주세요",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // 전체 동의
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.toggleAll(!viewModel.allAgreed) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = viewModel.allAgreed,
                    onCheckedChange = { viewModel.toggleAll(it) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "전체 동의",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(16.dp))

        // 이용약관 동의 (필수)
        TermsRow(
            label = "[필수] 이용약관 동의",
            checked = state.termsAgreed,
            onCheckedChange = { viewModel.toggleTerms(it) },
            onViewDetail = onViewTerms
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 개인정보처리방침 동의 (필수)
        TermsRow(
            label = "[필수] 개인정보처리방침 동의",
            checked = state.privacyAgreed,
            onCheckedChange = { viewModel.togglePrivacy(it) },
            onViewDetail = onViewPrivacy
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 마케팅 수신 동의 (선택)
        TermsRow(
            label = "[선택] 마케팅 정보 수신 동의",
            checked = state.marketingAgreed,
            onCheckedChange = { viewModel.toggleMarketing(it) },
            onViewDetail = null
        )

        Spacer(modifier = Modifier.weight(1f))

        // 다음 버튼
        Button(
            onClick = {
                onTermsAgreed(
                    TermsAgreementViewModel.TERMS_VERSION,
                    state.marketingAgreed
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = viewModel.allRequiredAgreed
        ) {
            Text("다음")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TermsRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onViewDetail: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        if (onViewDetail != null) {
            IconButton(onClick = onViewDetail) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "전문보기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

package com.designated.callmanager.enforcement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.designated.callmanager.data.settlement.DriverDailySettlementSummary
import java.text.NumberFormat
import java.util.Locale

/**
 * P7 게이트 #4 ManagerUnconfirmedModal — 미확인 정산(업무마감 제출했으나 매니저 미확인) 기사
 * **닫기 불가** 모달. 매니저가 각 기사를 [정산확인]하거나 [개별 검토]로 콜 단위 확인해야
 * 다른 화면을 쓸 수 있게 강제. [전체 확인] 버튼 없음(대충 퉁치기 방지, settlement_redesign §5.1).
 *
 * 정산 데이터는 읽기만 — 실제 [정산확인] 쓰기는 SettlementViewModel.confirmDriverDailySettlement.
 */
@Composable
fun ManagerUnconfirmedModal(
    drivers: List<DriverDailySettlementSummary>,
    onConfirm: (driverId: String) -> Unit,
    onReview: (driverId: String) -> Unit,
) {
    Dialog(
        onDismissRequest = { /* 닫기 불가 */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = MaterialTheme.shapes.large, color = Color(0xFF1E1E1E)) {
            Column(Modifier.padding(20.dp).fillMaxWidth()) {
                Text(
                    "미확인 정산 ${drivers.size}건",
                    fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "기사 정산을 확인해야 다른 화면을 사용할 수 있습니다.",
                    color = Color(0xFFBBBBBB), fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    drivers.forEach { d ->
                        DriverUnconfirmedRow(
                            summary = d,
                            onConfirm = { onConfirm(d.driverId) },
                            onReview = { onReview(d.driverId) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

private val won: NumberFormat = NumberFormat.getNumberInstance(Locale.KOREA)

@Composable
private fun DriverUnconfirmedRow(
    summary: DriverDailySettlementSummary,
    onConfirm: () -> Unit,
    onReview: () -> Unit,
) {
    val ds = summary.dailySettlement
    Surface(shape = MaterialTheme.shapes.medium, color = Color(0xFF2A2A2A)) {
        Column(Modifier.padding(12.dp).fillMaxWidth()) {
            Text(summary.driverName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            if (ds != null) {
                Text(
                    "운행 ${ds.tripCount}건 · 총 ${won.format(ds.totalFare)}원 · 납입 ${won.format(ds.finalDeposit)}원",
                    color = Color(0xFFCCCCCC), fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onReview, modifier = Modifier.weight(1f)) {
                    Text("개별 검토")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("정산확인")
                }
            }
        }
    }
}

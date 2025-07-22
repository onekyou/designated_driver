package com.designated.callmanager.ui.settlement

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.designated.callmanager.data.SettlementData
import com.designated.callmanager.ui.theme.Red500


@Composable
fun SettlementScreen(viewModel: SettlementViewModel) {
    val context = LocalContext.current
    val settlementList by viewModel.settlementList.collectAsState()
    val allSettlementList by viewModel.allSettlementList.collectAsState()
    val creditPersons by viewModel.creditPersons.collectAsState()

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("정산대기", "전체내역", "기사별", "외상 고객")

    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val allTripsCleared by viewModel.allTripsCleared.collectAsState()

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("업무 마감 확인") },
            text = { Text("오늘의 정산 내역을 마감하고, 별도로 보관하시겠습니까? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllTrips()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text("마감하기")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }


    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTabIndex) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title) }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        when (selectedTabIndex) {
            0 -> SettlementPendingList(settlementList) {
                viewModel.markSettlementSettled(it)
            }
            1 -> AllSettlementsList(
                allSettlementList,
                onClearClick = { showClearConfirmDialog = true },
                isCleared = allTripsCleared,
                onCorrectClick = { original, newFare, newPayment ->
                    viewModel.correctSettlement(original, newFare, newPayment)
                }
            )
            2 -> DriverSettlementList(allSettlementList)
            3 -> CreditManagementScreen(
                persons = creditPersons,
                onAddCredit = { name, phone, amount, memo ->
                    viewModel.addOrIncrementCredit(name, phone, amount, memo)
                },
                onReduceCredit = { personId, amount ->
                    viewModel.reduceCredit(personId, amount)
                }
            )
        }
    }

    LaunchedEffect(error) {
        error?.let {
            // 여기에 에러 메시지를 사용자에게 보여주는 로직 (e.g., Toast, Snackbar) 추가
            viewModel.clearError()
        }
    }
}

@Composable
fun AllSettlementsList(
    settlements: List<SettlementData>,
    onClearClick: () -> Unit,
    isCleared: Boolean,
    onCorrectClick: (SettlementData, Int, String) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = onClearClick,
                enabled = !isCleared,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Red500,
                    disabledContainerColor = Color.Gray
                )
            ) {
                Text("전체내역 업무마감")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(settlements) { settlement ->
                SettlementItem(settlement, onCorrectClick)
            }
        }
    }
}


// ---------------- CorrectionDialog ----------------
@Composable
fun CorrectionDialog(original: SettlementData, onConfirm: (Int, String) -> Unit, onDismiss: () -> Unit) {
    var fareInput by remember { mutableStateOf(original.fare.toString()) }
    var paymentMethod by remember { mutableStateOf(original.paymentMethod) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("정산 정정", color = Color.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = fareInput,
                    onValueChange = { fareInput = it.filter { c -> c.isDigit() } },
                    label = { Text("요금", color = Color.White) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = paymentMethod,
                    onValueChange = { paymentMethod = it },
                    label = { Text("결제방법", color = Color.White) },
                    textStyle = LocalTextStyle.current.copy(color = Color.White)
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val newFare = fareInput.toIntOrNull() ?: original.fare
                onConfirm(newFare, paymentMethod)
            }) { Text("확인") }
        },
        dismissButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF555555))) {
                Text("취소")
            }
        },
        containerColor = Color(0xFF2A2A2A)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditDetailDialog(credit: SettlementViewModel.CreditPerson, onClose:()->Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${credit.name} 외상 상세") },
        text  = { Text("금액: ${credit.amount}\n전화: ${credit.phone}\n메모: ${credit.memo}") },
        confirmButton = {
            Button(onClick = { /* 공유 로직 */ }) { Text("공유") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("닫기") }
        }
    )
}



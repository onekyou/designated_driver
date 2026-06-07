package com.designated.callmanager.reservation.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.designated.callmanager.data.CallInfo
import com.designated.callmanager.data.Constants
import com.designated.callmanager.reservation.data.ParsedRecordingName
import com.designated.callmanager.reservation.data.UniversalReservation

/**
 * 써머리확인 다이얼로그 — 통화예약 엔진 출력으로 **미리 채워진** 편집 카드.
 *
 * 6/8 UX: 파싱→바로등록 금지(헛예약 치명). 사장이 [확인] 한 탭 = 검토 안전장치 + 콜 생성.
 * [확인] = 콜 생성 자체(별도 확인 플래그 없음). 운행/정산 필드는 건드리지 않음.
 *
 * @param reservation 엔진 파싱 결과
 * @param parsed 파일명 파싱(전화번호 폴백)
 * @param onConfirm 보정된 CallInfo 전달 → 호출부(ViewModel)가 createCall
 */
@Composable
fun ReservationConfirmDialog(
    reservation: UniversalReservation,
    parsed: ParsedRecordingName?,
    onConfirm: (CallInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    // 엔진값을 편집 필드 초기값으로. 사장이 보정 가능.
    var phone by remember {
        mutableStateOf(reservation.callerPhone?.takeIf { it.isNotBlank() } ?: parsed?.phone ?: "")
    }
    var departure by remember { mutableStateOf(reservation.from ?: "") }
    var destination by remember { mutableStateOf(reservation.to ?: "") }
    var fare by remember { mutableStateOf(reservation.fare?.toString() ?: "") }
    var memo by remember {
        mutableStateOf(
            listOfNotNull(
                reservation.datetimeText?.takeIf { it.isNotBlank() }?.let { "예약시각: $it" },
                reservation.service?.takeIf { it.isNotBlank() }?.let { "서비스: $it" },
                reservation.partySize?.let { "인원: ${it}명" },
                reservation.notes?.takeIf { it.isNotBlank() },
            ).joinToString(" / ")
        )
    }

    val confidencePct = (reservation.confidence * 100).toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("통화 예약 확인") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("AI 분석 결과입니다. 확인·수정 후 [확인]을 누르면 콜이 등록됩니다. (신뢰도 ${confidencePct}%)")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("전화번호") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = departure,
                    onValueChange = { departure = it },
                    label = { Text("출발") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("도착") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = fare,
                    onValueChange = { v -> fare = v.filter(Char::isDigit) },
                    label = { Text("요금") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모(시각·서비스·비고)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val callInfo = CallInfo(
                    phoneNumber = phone.trim(),
                    status = Constants.STATUS_WAITING,
                    departure_set = departure.trim().ifBlank { null },
                    destination_set = destination.trim().ifBlank { null },
                    fare_set = fare.trim().toLongOrNull(),
                    memoText = memo.trim().ifBlank { null },
                    callType = "예약",
                    createdFrom = "call_recording",
                    fromCallManager = true,
                )
                onConfirm(callInfo)
            }) { Text("확인") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

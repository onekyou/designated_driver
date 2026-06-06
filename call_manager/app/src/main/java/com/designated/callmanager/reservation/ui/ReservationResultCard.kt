package com.designated.callmanager.reservation.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.designated.callmanager.reservation.data.ParseResult
import com.designated.callmanager.reservation.data.PathResult
import com.designated.callmanager.reservation.data.RecordingFile

/** W(싼 STT·목표) vs C(오디오·천장) 결과를 나란히. 정답(수기)과 대조해 채점표 작성용. */
@Composable
fun ReservationResultCard(recording: RecordingFile, result: ParseResult) {
    Column(Modifier.fillMaxWidth()) {
        Text(recording.displayName, style = MaterialTheme.typography.titleSmall)
        recording.parsed?.let {
            Text(
                "파일명 번호: ${it.phone ?: it.label} · ${it.localDateTime}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(8.dp))
        PathCard("C — 오디오 직접 (천장)", result.c)
        Spacer(Modifier.height(8.dp))
        PathCard("W — 싼 STT 텍스트 (목표)", result.w)
    }
}

@Composable
private fun PathCard(label: String, path: PathResult?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            if (path == null) {
                Text("(이 경로는 전송 안 함)", style = MaterialTheme.typography.bodySmall)
                return@Column
            }
            val r = path.reservation
            Spacer(Modifier.height(4.dp))
            if (!r.isReservation) {
                Text("⛔ 예약 아님 (intent=${r.intent})", color = MaterialTheme.colorScheme.error)
            } else {
                Field("업종", r.moduleType)
                Field("의도", r.intent)
                r.service?.let { Field("시술/서비스", it) }
                (r.datetimeIso?.takeIf { s -> s.isNotBlank() } ?: r.datetimeText)?.let { Field("시각", it) }
                r.partySize?.let { Field("인원", it.toString()) }
                r.callerPhone?.let { Field("전화", it) }
                r.from?.let { Field("출발", it) }
                r.to?.let { Field("도착", it) }
                r.fare?.let { Field("요금", it.toString()) }
                r.notes?.let { Field("메모", it) }
            }
            Field("confidence", String.format("%.2f", r.confidence))
            Text("${path.latencyMs}ms", style = MaterialTheme.typography.labelSmall)
            r.rawTranscript?.let { transcript ->
                var expanded by remember { mutableStateOf(false) }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "전사 접기" else "전사 보기 (STT 품질)")
                }
                if (expanded) Text(transcript, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Field(key: String, value: String) {
    Row(Modifier.padding(vertical = 1.dp)) {
        Text("$key  ", style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

package com.designated.callmanager.ui.settlement.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.designated.callmanager.data.SettlementData
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

@Composable
fun CreditDialog(
    trip: SettlementData,
    initialPhone: String = "",
    onDismiss: () -> Unit,
    onRegister: (name: String, phone: String, amount: Int) -> Unit
) {
    var name by remember { mutableStateOf(trip.customerName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var amountText by remember { mutableStateOf(trip.fare.toString()) }
    var memo by remember { mutableStateOf("") }

    val nameFocusRequester = remember { FocusRequester() }
    val phoneFocusRequester = remember { FocusRequester() }
    val amountFocusRequester = remember { FocusRequester() }
    val memoFocusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("외상 등록") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("고객명") },
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { phoneFocusRequester.requestFocus() }
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("전화번호") },
                    modifier = Modifier.fillMaxWidth().focusRequester(phoneFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { amountFocusRequester.requestFocus() }
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("금액") },
                    modifier = Modifier.fillMaxWidth().focusRequester(amountFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { memoFocusRequester.requestFocus() }
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모(선택)") },
                    modifier = Modifier.fillMaxWidth().focusRequester(memoFocusRequester),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val amt = amountText.toIntOrNull() ?: 0
                            if (amt > 0) onRegister(name, phone, amt) else onDismiss()
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amt = amountText.toIntOrNull() ?: 0
                    if (amt > 0) onRegister(name, phone, amt) else onDismiss()
                }
            ) { Text("등록") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
} 
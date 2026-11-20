package com.jojings.ringtonemaker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jojings.ringtonemaker.Busy

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val ok = value.text.isNotBlank()
    PopDialog(onDismiss = onDismiss, title = title, color = PopColors.Paper) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (it.text.length <= 60) value = it },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Black, fontSize = 18.sp, color = PopColors.Ink),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PopColors.Pink,
                unfocusedBorderColor = PopColors.Ink,
                cursorColor = PopColors.Pink,
                focusedContainerColor = PopColors.Yellow.copy(alpha = 0.25f),
                unfocusedContainerColor = PopColors.Yellow.copy(alpha = 0.15f),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (ok) onConfirm(value.text.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PopButton("Cancel", onDismiss, Modifier.weight(1f), color = PopColors.Paper)
            PopButton(confirm, { onConfirm(value.text.trim()) }, Modifier.weight(1f), color = PopColors.Lime, enabled = ok)
        }
    }
}

@Composable
fun BusyOverlay(busy: Busy, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(PopColors.Ink.copy(alpha = 0.6f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        PopSurface(Modifier.padding(32.dp), color = PopColors.Paper, shadow = 7.dp) {
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SpinningRecord()
                Spacer(Modifier.height(16.dp))
                Text(busy.label, fontWeight = FontWeight.Black, fontSize = 20.sp, color = PopColors.Ink)
                busy.progress?.let { p ->
                    Spacer(Modifier.height(12.dp))
                    PopProgressBar(p, color = PopColors.Pink)
                    Spacer(Modifier.height(6.dp))
                    Text("${(p * 100).toInt()}%", fontWeight = FontWeight.Black, color = PopColors.Purple)
                }
                Spacer(Modifier.height(16.dp))
                PopButton("Cancel", onCancel, color = PopColors.Yellow)
            }
        }
    }
}

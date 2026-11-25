package com.example.smsapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.smsapp.viewmodel.SmsViewModel

// The UI edits the draft. MainActivity handles the permission request.
@Composable
fun SendSmsScreen(model: SmsViewModel, onSend: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = model.phone,
            onValueChange = { model.phone = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Phone number") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true
        )
        OutlinedTextField(
            value = model.body,
            onValueChange = { model.body = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Message") },
            minLines = 3
        )
        Button(
            onClick = onSend,
            enabled = !model.sending && model.phone.isNotBlank() && model.body.isNotBlank()
        ) { Text("Send SMS") }
        if (model.sendStatus.isNotEmpty()) Text(model.sendStatus)
        HorizontalDivider()
        Text("Latest sent SMS", style = MaterialTheme.typography.titleMedium)
        val message = model.latestSent
        if (message == null) {
            Text("No SMS sent yet.")
        } else {
            Text("To ${message.phone}")
            Text(message.body)
        }
    }
}

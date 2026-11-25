package com.example.smsapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.smsapp.viewmodel.SmsViewModel

@Composable
fun ReceiveSmsScreen(
    model: SmsViewModel,
    receiveAllowed: Boolean,
    notificationsAllowed: Boolean,
    permissionNotice: String,
    onReceiveChange: (Boolean) -> Unit,
    onNotificationsChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PermissionSwitch("Receive SMS", receiveAllowed, onReceiveChange)
        PermissionSwitch("App notifications", notificationsAllowed, onNotificationsChange)
        if (permissionNotice.isNotEmpty()) Text(permissionNotice)

        HorizontalDivider()
        Text("Latest received SMS", style = MaterialTheme.typography.titleMedium)
        val message = model.latest
        if (message == null) {
            Text("No SMS received yet.")
        } else {
            Text("From ${message.phone}")
            Text(message.body)
        }
        HorizontalDivider()
        Button(onClick = model::sync, enabled = !model.syncing) { Text("Sync SMS statistics") }
        if (model.syncStatus.isNotEmpty()) Text(model.syncStatus)
    }
}

@Composable
private fun PermissionSwitch(label: String, allowed: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ${if (allowed) "Allowed" else "Off"}")
        Switch(
            checked = allowed,
            onCheckedChange = onChange,
            modifier = Modifier.semantics { contentDescription = label }
        )
    }
}

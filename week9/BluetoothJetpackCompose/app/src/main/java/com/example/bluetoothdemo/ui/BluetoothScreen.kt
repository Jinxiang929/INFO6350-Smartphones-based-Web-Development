package com.example.bluetoothdemo.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetoothdemo.model.BluetoothUiState
import com.example.bluetoothdemo.model.ConnectionPhase
import com.example.bluetoothdemo.model.DeviceUi
import com.example.bluetoothdemo.viewmodel.BluetoothViewModel

private val LightColors = lightColorScheme(
    primary = Color(0xFF1648B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE7FF),
    onPrimaryContainer = Color(0xFF102A63),
    secondaryContainer = Color(0xFFE5EDFB),
    onSecondaryContainer = Color(0xFF223858),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF14233C),
    surface = Color.White,
    onSurface = Color(0xFF14233C),
    onSurfaceVariant = Color(0xFF45556D),
    outline = Color(0xFF6B7B94),
    outlineVariant = Color(0xFFD6DFED)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC5FF),
    onPrimary = Color(0xFF0D285E),
    primaryContainer = Color(0xFF203D78),
    onPrimaryContainer = Color(0xFFF0F4FF),
    secondaryContainer = Color(0xFF263956),
    onSecondaryContainer = Color(0xFFDEE9FF),
    background = Color(0xFF101724),
    onBackground = Color(0xFFE8EEFA),
    surface = Color(0xFF172132),
    onSurface = Color(0xFFE8EEFA),
    onSurfaceVariant = Color(0xFFBDC9DF),
    outline = Color(0xFF94A4BF),
    outlineVariant = Color(0xFF34445E)
)

@Composable
fun BluetoothScreen(viewModel: BluetoothViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    val connected = state.connectionPhase == ConnectionPhase.CONNECTED
    val canStartAction =
        state.connectionPhase == ConnectionPhase.IDLE && state.pairingAddress == null

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onPermissionResult() }

    val enableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.onEnableResult() }

    val discoverableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onDiscoverableResult(result.resultCode > 0)
    }

    fun withPermissions(action: () -> Unit) {
        val missing = viewModel.missingPermissions()
        if (missing.isEmpty()) {
            action()
        } else {
            permissionLauncher.launch(missing)
        }
    }

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = colors.background,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
                contentAlignment = Alignment.TopCenter
            ) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "title") {
                        Text(
                            text = "Bluetooth Classroom Demo",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item(key = "connection") {
                        ConnectionCard(
                            state = state,
                            canStart = canStartAction,
                            onStart = { withPermissions { viewModel.startServer() } },
                            onStop = viewModel::disconnect
                        )
                    }

                    if (connected) {
                        item(key = "composer") {
                            MessageComposer(state, viewModel::onDraftChanged, viewModel::send)
                        }
                    }

                    item(key = "setup") {
                        Panel {
                            SectionTitle("Bluetooth setup")

                            OutlinedButton(
                                onClick = {
                                    withPermissions {
                                        enableLauncher.launch(
                                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Turn Bluetooth ON")
                            }

                            OutlinedButton(
                                onClick = {
                                    withPermissions {
                                        discoverableLauncher.launch(
                                            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                                                .putExtra(
                                                    BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                                                    300
                                                )
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Make device discoverable")
                            }

                            FilledTonalButton(
                                onClick = { withPermissions { viewModel.scan() } },
                                enabled = canStartAction && !state.isScanning,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (state.isScanning) "Scanning..." else "Scan nearby devices"
                                )
                            }
                        }
                    }

                    item(key = "paired-title") {
                        Column {
                            SectionTitle("Paired devices")
                            TextButton(
                                onClick = { withPermissions { viewModel.loadPaired() } },
                                enabled = canStartAction
                            ) {
                                Text("Get paired devices")
                            }
                        }
                    }
                    if (state.pairedDevices.isEmpty()) {
                        item(key = "paired-empty") {
                            EmptyState("Pair a nearby device or load your existing pairs.")
                        }
                    }
                    items(state.pairedDevices, key = { "paired-${it.address}" }) { device ->
                        DeviceCard(device, "Tap to connect", canStartAction) {
                            withPermissions { viewModel.connect(device) }
                        }
                    }

                    item(key = "nearby-title") {
                        SectionTitle("Nearby devices")
                    }
                    if (state.nearbyDevices.isEmpty()) {
                        item(key = "nearby-empty") {
                            EmptyState(
                                if (state.isScanning) {
                                    "Looking for discoverable devices..."
                                } else {
                                    "Make the other phone discoverable, then scan."
                                }
                            )
                        }
                    }
                    items(state.nearbyDevices, key = { "nearby-${it.address}" }) { device ->
                        val alreadyPaired = state.pairedDevices.any { it.address == device.address }
                        val hint = when {
                            state.pairingAddress == device.address -> "Pairing..."
                            alreadyPaired -> "Paired. Connect from Paired devices."
                            else -> "Tap to pair"
                        }
                        DeviceCard(device, hint, canStartAction && !alreadyPaired) {
                            withPermissions { viewModel.pair(device) }
                        }
                    }

                    if (!connected) {
                        item(key = "composer") {
                            MessageComposer(state, viewModel::onDraftChanged, viewModel::send)
                        }
                    }

                    item(key = "received-title") {
                        SectionTitle("Received messages")
                    }
                    if (state.receivedMessages.isEmpty()) {
                        item(key = "received-empty") {
                            EmptyState("Messages from the other phone will appear here.")
                        }
                    }
                    items(
                        count = state.receivedMessages.size,
                        key = { index -> "received-${state.receivedMessages.lastIndex - index}" }
                    ) { index ->
                        val message = state.receivedMessages[state.receivedMessages.lastIndex - index]
                        Surface(
                            modifier = Modifier.fillMaxWidth(0.9f),
                            shape = RoundedCornerShape(
                                topStart = 6.dp, topEnd = 18.dp,
                                bottomStart = 18.dp, bottomEnd = 18.dp
                            ),
                            color = colors.secondaryContainer,
                            contentColor = colors.onSecondaryContainer
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    state: BluetoothUiState,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val title = when (state.connectionPhase) {
        ConnectionPhase.IDLE -> when {
            state.pairingAddress != null -> "Pairing..."
            state.isScanning -> "Scanning nearby devices"
            else -> "Not connected"
        }
        ConnectionPhase.CONNECTING -> "Connecting..."
        ConnectionPhase.LISTENING -> "Waiting for a connection"
        ConnectionPhase.CONNECTED -> state.connectedDevice?.let { "Connected to $it" }
            ?: "Connected"
        ConnectionPhase.STOPPING -> "Stopping..."
    }
    val stopLabel = when (state.connectionPhase) {
        ConnectionPhase.CONNECTING -> "Cancel connection"
        ConnectionPhase.LISTENING -> "Stop server"
        ConnectionPhase.STOPPING -> "Stopping..."
        ConnectionPhase.IDLE, ConnectionPhase.CONNECTED -> "Disconnect"
    }
    val showProgress = state.isScanning || state.pairingAddress != null ||
            state.connectionPhase == ConnectionPhase.CONNECTING ||
            state.connectionPhase == ConnectionPhase.LISTENING ||
            state.connectionPhase == ConnectionPhase.STOPPING

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(text = state.status, style = MaterialTheme.typography.bodyMedium)
            }

            if (showProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.connectionPhase == ConnectionPhase.IDLE) {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start server / wait for incoming connection")
                }
            } else {
                OutlinedButton(
                    onClick = onStop,
                    enabled = state.connectionPhase != ConnectionPhase.STOPPING,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = colors.onPrimaryContainer
                    )
                ) {
                    Text(stopLabel)
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    state: BluetoothUiState,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    val connected = state.connectionPhase == ConnectionPhase.CONNECTED

    Panel {
        SectionTitle("Send a message")
        OutlinedTextField(
            value = state.draft,
            onValueChange = onDraftChanged,
            singleLine = true,
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )
        if (!connected) {
            Text(
                text = "Connect to another phone to send your message.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(
            onClick = onSend,
            enabled = connected && state.draft.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("TEST – SEND STRING")
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceUi,
    hint: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = colors.onSurfaceVariant
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) colors.primary else colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun EmptyState(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium
    )
}
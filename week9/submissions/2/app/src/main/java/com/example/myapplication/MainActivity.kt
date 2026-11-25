package com.example.myapplication2

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var bluetoothStatus by mutableStateOf("Loading")
    private var visibilityStatus by mutableStateOf("Loading")
    private var statusHint by mutableStateOf("Use this app to control Bluetooth and device visibility.")
    private var pendingSilentReset by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = requiredPermissions().all { results[it] == true || hasPermission(it) }
            if (allGranted) {
                refreshUiState()
            } else {
                bluetoothStatus = "Permission required"
                visibilityStatus = "Permission required"
                statusHint = "Bluetooth permissions are required to control Bluetooth and silent mode."
            }
        }

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            statusHint = if (result.resultCode > 0) {
                "The device is discoverable for up to ${result.resultCode} seconds."
            } else {
                "Discoverable mode was cancelled. The device stays silent."
            }
            refreshUiState()
        }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (pendingSilentReset && state == BluetoothAdapter.STATE_OFF) {
                        pendingSilentReset = false
                        val restarted = requestBluetoothEnabled(true)
                        statusHint = if (restarted) {
                            "Bluetooth is restarting so the device can return to silent mode."
                        } else {
                            "Bluetooth turned off, but the system rejected the restart request."
                        }
                    }
                    refreshUiState()
                }

                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED -> refreshUiState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureBluetoothPermissionsIfNeeded()
        refreshUiState()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothControlScreen(
                        bluetoothStatus = bluetoothStatus,
                        visibilityStatus = visibilityStatus,
                        hint = statusHint,
                        modifier = Modifier.padding(innerPadding),
                        onToggleBluetooth = { toggleBluetooth() },
                        onToggleSilentMode = { toggleSilentMode() },
                        onRefresh = { refreshUiState() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            bluetoothReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED)
            }
        )
    }

    override fun onStop() {
        unregisterReceiver(bluetoothReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun ensureBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val missingPermissions = requiredPermissions().filterNot(::hasPermission)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyList()
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredBluetoothPermissions(): Boolean = requiredPermissions().all(::hasPermission)

    private fun refreshUiState() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            bluetoothStatus = "Unsupported"
            visibilityStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasRequiredBluetoothPermissions()) {
            bluetoothStatus = "Permission required"
            visibilityStatus = "Permission required"
            statusHint = "Grant Bluetooth permissions before reading state or visibility."
            return
        }
        val isEnabled = readBluetoothEnabled(adapter) ?: run {
            bluetoothStatus = "Unavailable"
            visibilityStatus = "Unavailable"
            statusHint = "The system rejected access to the Bluetooth state."
            return
        }
        val scanMode = readBluetoothScanMode(adapter) ?: run {
            bluetoothStatus = if (isEnabled) "On" else "Off"
            visibilityStatus = "Unavailable"
            statusHint = "The system rejected access to the visibility state."
            return
        }
        bluetoothStatus = if (isEnabled) "On" else "Off"
        visibilityStatus = when (scanMode) {
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> "Discoverable"
            BluetoothAdapter.SCAN_MODE_CONNECTABLE -> "Silent"
            else -> "Bluetooth off"
        }
        statusHint = when {
            !isEnabled -> "Turn Bluetooth on before changing silent mode."
            scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE ->
                "Other devices can see this device right now. Switching back to silent mode restarts Bluetooth."
            else -> "This device can look for others, but other devices cannot discover it."
        }
    }

    private fun toggleBluetooth() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            bluetoothStatus = "Unsupported"
            visibilityStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasRequiredBluetoothPermissions()) {
            ensureBluetoothPermissionsIfNeeded()
            statusHint = "Bluetooth permission is required before the app can toggle Bluetooth."
            return
        }
        val isEnabled = readBluetoothEnabled(adapter) ?: run {
            statusHint = "The system rejected access to the Bluetooth state."
            refreshUiState()
            return
        }
        val success = requestBluetoothEnabled(!isEnabled)
        statusHint = if (success) "Bluetooth toggle request sent." else "The system rejected the Bluetooth toggle request."
    }

    private fun toggleSilentMode() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            visibilityStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasRequiredBluetoothPermissions()) {
            ensureBluetoothPermissionsIfNeeded()
            statusHint = "Bluetooth permission is required before the app can change silent mode."
            return
        }
        val isEnabled = readBluetoothEnabled(adapter) ?: run {
            statusHint = "The system rejected access to the Bluetooth state."
            refreshUiState()
            return
        }
        if (!isEnabled) {
            statusHint = "Turn Bluetooth on first."
            refreshUiState()
            return
        }
        val scanMode = readBluetoothScanMode(adapter) ?: run {
            statusHint = "The system rejected access to the visibility state."
            refreshUiState()
            return
        }
        if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            pendingSilentReset = true
            val disabled = requestBluetoothEnabled(false)
            statusHint = if (disabled) "Switching back to silent mode." else {
                pendingSilentReset = false
                "The system rejected the silent mode reset."
            }
            return
        }
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        discoverableLauncher.launch(discoverableIntent)
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    private fun readBluetoothEnabled(adapter: BluetoothAdapter): Boolean? {
        if (!hasRequiredBluetoothPermissions()) return null
        return try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            null
        }
    }

    private fun readBluetoothScanMode(adapter: BluetoothAdapter): Int? {
        if (!hasRequiredBluetoothPermissions()) return null
        return try {
            adapter.scanMode
        } catch (_: SecurityException) {
            null
        }
    }

    private fun requestBluetoothEnabled(enabled: Boolean): Boolean {
        val adapter = getBluetoothAdapter() ?: return false
        if (!hasRequiredBluetoothPermissions()) return false
        return try {
            if (enabled) adapter.enable() else adapter.disable()
        } catch (_: SecurityException) {
            false
        }
    }
}

@Composable
private fun BluetoothControlScreen(
    bluetoothStatus: String,
    visibilityStatus: String,
    hint: String,
    modifier: Modifier = Modifier,
    onToggleBluetooth: () -> Unit,
    onToggleSilentMode: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Bluetooth Power", style = MaterialTheme.typography.headlineMedium)
        Text(text = bluetoothStatus, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
        Text(text = "Visibility", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
        Text(text = visibilityStatus, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = hint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
        Button(onClick = onToggleBluetooth) { Text("Toggle Bluetooth") }
        Button(onClick = onToggleSilentMode, modifier = Modifier.padding(top = 12.dp)) { Text("Toggle Silent Mode") }
        Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Refresh Status") }
    }
}

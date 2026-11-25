package com.example.myapplication3

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
    private var scanStatus by mutableStateOf("Not scanned yet")
    private var statusHint by mutableStateOf("Use this app to control Bluetooth and scan for nearby devices.")
    private var isScanning by mutableStateOf(false)
    private val devices = mutableStateListOf<String>()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = requiredPermissions().all { results[it] == true || hasPermission(it) }
            if (allGranted) {
                refreshBluetoothStatus()
            } else {
                bluetoothStatus = "Permission required"
                scanStatus = "Permission required"
                statusHint = "Bluetooth and scan permissions are required for this app."
            }
        }

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    isScanning = true
                    scanStatus = "Scanning"
                    statusHint = "Searching for nearby Bluetooth devices."
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    isScanning = false
                    scanStatus = if (devices.isEmpty()) "No devices found" else "Scan finished"
                    statusHint = if (devices.isEmpty()) {
                        "No nearby Bluetooth devices were reported."
                    } else {
                        "Scan finished. ${devices.size} device(s) found."
                    }
                }

                BluetoothAdapter.ACTION_STATE_CHANGED -> refreshBluetoothStatus()

                BluetoothDevice.ACTION_FOUND -> {
                    val device = extractBluetoothDevice(intent) ?: return
                    val label = formatDevice(device) ?: return
                    if (!devices.contains(label)) {
                        devices.add(label)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureBluetoothPermissionsIfNeeded()
        refreshBluetoothStatus()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothScanScreen(
                        bluetoothStatus = bluetoothStatus,
                        scanStatus = scanStatus,
                        hint = statusHint,
                        devices = devices,
                        isScanning = isScanning,
                        modifier = Modifier.padding(innerPadding),
                        onToggleBluetooth = { toggleBluetooth() },
                        onScanDevices = { startScan() },
                        onRefresh = { refreshBluetoothStatus() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            discoveryReceiver,
            IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
            }
        )
    }

    override fun onStop() {
        cancelDiscoveryIfNeeded()
        unregisterReceiver(discoveryReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshBluetoothStatus()
    }

    private fun ensureBluetoothPermissionsIfNeeded() {
        val missingPermissions = requiredPermissions().filterNot(::hasPermission)
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun requiredPermissions(): List<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredPermissions(): Boolean = requiredPermissions().all(::hasPermission)

    private fun refreshBluetoothStatus() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            bluetoothStatus = "Unsupported"
            scanStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasRequiredPermissions()) {
            bluetoothStatus = "Permission required"
            scanStatus = "Permission required"
            statusHint = "Grant Bluetooth and scan permissions before toggling Bluetooth or scanning."
            return
        }
        val enabled = readBluetoothEnabled(adapter) ?: run {
            bluetoothStatus = "Unavailable"
            statusHint = "The system rejected access to the Bluetooth state."
            return
        }
        bluetoothStatus = if (enabled) "On" else "Off"
        if (!enabled) {
            scanStatus = "Bluetooth off"
            isScanning = false
            statusHint = "Turn Bluetooth on before scanning for nearby devices."
        } else if (!isScanning && devices.isEmpty()) {
            scanStatus = "Not scanned yet"
            statusHint = "Bluetooth is on. You can start scanning now."
        }
    }

    private fun toggleBluetooth() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            bluetoothStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasRequiredPermissions()) {
            ensureBluetoothPermissionsIfNeeded()
            statusHint = "Bluetooth permission is required before the app can toggle Bluetooth."
            return
        }
        val enabled = readBluetoothEnabled(adapter) ?: run {
            statusHint = "The system rejected access to the Bluetooth state."
            refreshBluetoothStatus()
            return
        }
        if (enabled) {
            cancelDiscoveryIfNeeded()
            devices.clear()
            scanStatus = "Bluetooth off"
        }
        val success = requestBluetoothEnabled(!enabled)
        statusHint = if (success) "Bluetooth toggle request sent." else "The system rejected the Bluetooth toggle request."
    }

    private fun startScan() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            scanStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasRequiredPermissions()) {
            ensureBluetoothPermissionsIfNeeded()
            statusHint = "Bluetooth and scan permissions are required before scanning."
            return
        }
        val enabled = readBluetoothEnabled(adapter) ?: run {
            statusHint = "The system rejected access to the Bluetooth state."
            refreshBluetoothStatus()
            return
        }
        if (!enabled) {
            scanStatus = "Bluetooth off"
            statusHint = "Turn Bluetooth on first."
            return
        }
        cancelDiscoveryIfNeeded()
        devices.clear()
        val started = try {
            adapter.startDiscovery()
        } catch (_: SecurityException) {
            false
        }
        if (started) {
            isScanning = true
            scanStatus = "Scanning"
            statusHint = "Searching for nearby Bluetooth devices."
        } else {
            isScanning = false
            scanStatus = "Scan failed"
            statusHint = "The system rejected the scan request. The device or emulator may not support discovery."
        }
    }

    private fun cancelDiscoveryIfNeeded() {
        val adapter = getBluetoothAdapter() ?: return
        if (!hasRequiredPermissions()) return
        try {
            if (adapter.isDiscovering) adapter.cancelDiscovery()
        } catch (_: SecurityException) {
        }
        isScanning = false
    }

    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        if (!hasRequiredPermissions()) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }
        } catch (_: SecurityException) {
            null
        }
    }

    private fun formatDevice(device: BluetoothDevice): String? {
        if (!hasRequiredPermissions()) return null
        return try {
            val name = device.name?.takeIf { it.isNotBlank() } ?: "Unknown device"
            "$name\n${device.address}"
        } catch (_: SecurityException) {
            null
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    private fun readBluetoothEnabled(adapter: BluetoothAdapter): Boolean? {
        if (!hasRequiredPermissions()) return null
        return try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            null
        }
    }

    private fun requestBluetoothEnabled(enabled: Boolean): Boolean {
        val adapter = getBluetoothAdapter() ?: return false
        if (!hasRequiredPermissions()) return false
        return try {
            if (enabled) adapter.enable() else adapter.disable()
        } catch (_: SecurityException) {
            false
        }
    }
}

@Composable
private fun BluetoothScanScreen(
    bluetoothStatus: String,
    scanStatus: String,
    hint: String,
    devices: List<String>,
    isScanning: Boolean,
    modifier: Modifier = Modifier,
    onToggleBluetooth: () -> Unit,
    onScanDevices: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Bluetooth Power", style = MaterialTheme.typography.headlineMedium)
        Text(text = bluetoothStatus, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
        Text(text = "Scan Status", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 24.dp))
        Text(text = scanStatus, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))
        Text(
            text = hint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
        Button(onClick = onToggleBluetooth) { Text("Toggle Bluetooth") }
        Button(onClick = onScanDevices, modifier = Modifier.padding(top = 12.dp)) {
            Text(if (isScanning) "Scan Again" else "Scan Nearby Devices")
        }
        Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp)) { Text("Refresh Status") }
        if (devices.isEmpty()) {
            Text(text = "No nearby Bluetooth devices found yet.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(devices) { device ->
                    Text(text = device, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}

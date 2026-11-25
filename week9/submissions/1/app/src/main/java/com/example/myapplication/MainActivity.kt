package com.example.myapplication

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
    private var statusHint by mutableStateOf("Use this app to control the Bluetooth power state.")

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                refreshBluetoothStatus()
            } else {
                bluetoothStatus = "Permission required"
                statusHint = "BLUETOOTH_CONNECT was denied, so the app cannot toggle Bluetooth."
            }
        }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshBluetoothStatus()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ensureBluetoothPermissionIfNeeded()
        refreshBluetoothStatus()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothControlScreen(
                        status = bluetoothStatus,
                        hint = statusHint,
                        modifier = Modifier.padding(innerPadding),
                        onToggleBluetooth = { toggleBluetooth() },
                        onRefresh = { refreshBluetoothStatus() }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(bluetoothReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
    }

    override fun onStop() {
        unregisterReceiver(bluetoothReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshBluetoothStatus()
    }

    private fun ensureBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!hasBluetoothPermission()) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun refreshBluetoothStatus() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            bluetoothStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasBluetoothPermission()) {
            bluetoothStatus = "Permission required"
            statusHint = "Grant BLUETOOTH_CONNECT to read and change the Bluetooth state."
            return
        }
        val isEnabled = readBluetoothEnabled(adapter) ?: run {
            bluetoothStatus = "Unavailable"
            statusHint = "The system rejected access to the Bluetooth state."
            return
        }
        bluetoothStatus = if (isEnabled) "On" else "Off"
        statusHint = "Tap the button below to toggle Bluetooth."
    }

    private fun toggleBluetooth() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            bluetoothStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasBluetoothPermission()) {
            ensureBluetoothPermissionIfNeeded()
            statusHint = "Bluetooth permission is required before the app can toggle Bluetooth."
            return
        }
        val isEnabled = readBluetoothEnabled(adapter) ?: run {
            statusHint = "The system rejected access to the Bluetooth state."
            refreshBluetoothStatus()
            return
        }
        val success = requestBluetoothEnabled(!isEnabled)
        statusHint = if (success) {
            "Bluetooth toggle request sent."
        } else {
            "The system rejected the Bluetooth toggle request."
        }
    }

    private fun hasBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun getBluetoothAdapter(): BluetoothAdapter? {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return bluetoothManager?.adapter
    }

    private fun readBluetoothEnabled(adapter: BluetoothAdapter): Boolean? {
        if (!hasBluetoothPermission()) return null
        return try {
            adapter.isEnabled
        } catch (_: SecurityException) {
            null
        }
    }

    private fun requestBluetoothEnabled(enabled: Boolean): Boolean {
        val adapter = getBluetoothAdapter() ?: return false
        if (!hasBluetoothPermission()) return false
        return try {
            if (enabled) adapter.enable() else adapter.disable()
        } catch (_: SecurityException) {
            false
        }
    }
}

@Composable
private fun BluetoothControlScreen(
    status: String,
    hint: String,
    modifier: Modifier = Modifier,
    onToggleBluetooth: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Bluetooth Power", style = MaterialTheme.typography.headlineMedium)
        Text(text = status, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
        Text(
            text = hint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
        )
        Button(onClick = onToggleBluetooth) { Text("Toggle Bluetooth") }
        Button(onClick = onRefresh, modifier = Modifier.padding(top = 12.dp)) { Text("Refresh Status") }
    }
}

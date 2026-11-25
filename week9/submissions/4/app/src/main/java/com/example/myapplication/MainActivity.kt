package com.example.myapplication4

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var bluetoothStatus by mutableStateOf("Loading")
    private var connectionStatus by mutableStateOf("Waiting for connection")
    private var statusHint by mutableStateOf("Turn on Bluetooth, pair both devices, then start chatting.")
    private var outgoingMessage by mutableStateOf("")
    private var hasRequestedDiscoverableThisSession = false
    private val pairedDevices = mutableStateListOf<PairedDeviceItem>()
    private val messages = mutableStateListOf<ChatMessage>()

    private lateinit var chatService: BluetoothChatService

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                refreshBluetoothState()
                refreshPairedDevices()
                startListening()
            } else {
                bluetoothStatus = "Permission required"
                connectionStatus = "Permission required"
                statusHint = "BLUETOOTH_CONNECT was denied, so Bluetooth chat is unavailable."
            }
        }

    private val discoverableLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            hasRequestedDiscoverableThisSession = true
            statusHint = "Discoverable mode request sent. Complete pairing from the other device."
        }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshBluetoothState()
                refreshPairedDevices()
                startListening()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        chatService = BluetoothChatService(
            adapterProvider = { getBluetoothAdapter() },
            hasPermission = { hasBluetoothPermission() },
            callbacks = object : BluetoothChatService.Callbacks {
                override fun onListening() {
                    runOnUiThread {
                        connectionStatus = "Waiting for connection"
                        statusHint = "Waiting for another paired device to connect."
                    }
                }

                override fun onConnected(deviceName: String) {
                    runOnUiThread {
                        connectionStatus = "Connected: $deviceName"
                        statusHint = "Connection established. You can now exchange messages."
                    }
                }

                override fun onMessageReceived(deviceName: String, message: String) {
                    runOnUiThread {
                        messages.add(ChatMessage(deviceName, message))
                    }
                }

                override fun onDisconnected(reason: String) {
                    runOnUiThread {
                        connectionStatus = "Disconnected"
                        statusHint = reason
                    }
                }

                override fun onError(message: String) {
                    runOnUiThread {
                        statusHint = message
                    }
                }
            }
        )

        ensureBluetoothPermissionIfNeeded()
        refreshBluetoothState()
        refreshPairedDevices()
        startListening()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BluetoothChatScreen(
                        bluetoothStatus = bluetoothStatus,
                        connectionStatus = connectionStatus,
                        hint = statusHint,
                        pairedDevices = pairedDevices,
                        messages = messages,
                        outgoingMessage = outgoingMessage,
                        modifier = Modifier.padding(innerPadding),
                        onToggleBluetooth = { toggleBluetooth() },
                        onRefreshPaired = { refreshPairedDevices() },
                        onMakeDiscoverable = { requestDiscoverable() },
                        onConnectDevice = { connectToDevice(it) },
                        onMessageChange = { outgoingMessage = it },
                        onSendMessage = { sendMessage() }
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
        refreshBluetoothState()
        refreshPairedDevices()
        startListening()
        requestDiscoverableIfNeeded()
    }

    override fun onDestroy() {
        chatService.stop()
        super.onDestroy()
    }

    private fun ensureBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!hasBluetoothPermission()) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun refreshBluetoothState() {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            bluetoothStatus = "Unsupported"
            connectionStatus = "Unsupported"
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasBluetoothPermission()) {
            bluetoothStatus = "Permission required"
            connectionStatus = "Permission required"
            statusHint = "Grant BLUETOOTH_CONNECT to read Bluetooth state and create chat connections."
            return
        }
        val enabled = readBluetoothEnabled(adapter) ?: run {
            bluetoothStatus = "Unavailable"
            statusHint = "The system rejected access to the Bluetooth state."
            return
        }
        bluetoothStatus = if (enabled) "On" else "Off"
        if (!enabled) {
            hasRequestedDiscoverableThisSession = false
            connectionStatus = "Bluetooth off"
            statusHint = "Turn Bluetooth on, pair another device, then open this app on both devices."
        }
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
        val enabled = readBluetoothEnabled(adapter) ?: run {
            statusHint = "The system rejected access to the Bluetooth state."
            refreshBluetoothState()
            return
        }
        if (enabled) {
            chatService.stop()
            pairedDevices.clear()
            messages.clear()
            hasRequestedDiscoverableThisSession = false
            connectionStatus = "Bluetooth off"
        }
        val success = requestBluetoothEnabled(!enabled)
        statusHint = if (success) "Bluetooth toggle request sent." else "The system rejected the Bluetooth toggle request."
    }

    private fun refreshPairedDevices() {
        pairedDevices.clear()
        val adapter = getBluetoothAdapter() ?: return
        if (!hasBluetoothPermission()) return
        val enabled = readBluetoothEnabled(adapter) ?: return
        if (!enabled) return
        try {
            adapter.bondedDevices.sortedBy { it.name ?: it.address }.forEach { device ->
                pairedDevices.add(
                    PairedDeviceItem(
                        name = device.name?.takeIf { it.isNotBlank() } ?: "Unknown device",
                        address = device.address
                    )
                )
            }
        } catch (_: SecurityException) {
            statusHint = "The system rejected access to the paired device list."
        }
    }

    private fun requestDiscoverable() {
        if (!hasBluetoothPermission()) {
            ensureBluetoothPermissionIfNeeded()
            statusHint = "Bluetooth permission is required before entering discoverable mode."
            return
        }
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        discoverableLauncher.launch(intent)
    }

    private fun requestDiscoverableIfNeeded() {
        if (hasRequestedDiscoverableThisSession) return
        val adapter = getBluetoothAdapter() ?: return
        if (!hasBluetoothPermission()) return
        val enabled = readBluetoothEnabled(adapter) ?: return
        if (!enabled) return
        requestDiscoverable()
    }

    private fun connectToDevice(item: PairedDeviceItem) {
        val adapter = getBluetoothAdapter()
        if (adapter == null) {
            statusHint = "This device does not provide a Bluetooth adapter."
            return
        }
        if (!hasBluetoothPermission()) {
            ensureBluetoothPermissionIfNeeded()
            statusHint = "Bluetooth permission is required before connecting to a paired device."
            return
        }
        val enabled = readBluetoothEnabled(adapter) ?: run {
            statusHint = "The system rejected access to the Bluetooth state."
            return
        }
        if (!enabled) {
            statusHint = "Turn Bluetooth on first."
            return
        }
        try {
            val device = adapter.getRemoteDevice(item.address)
            connectionStatus = "Connecting: ${item.name}"
            statusHint = "Connecting to ${item.name}."
            chatService.connect(device)
        } catch (_: IllegalArgumentException) {
            statusHint = "The selected device address is invalid."
        } catch (_: SecurityException) {
            statusHint = "The system rejected the Bluetooth connection request."
        }
    }

    private fun sendMessage() {
        val message = outgoingMessage.trim()
        if (message.isEmpty()) return
        val sent = chatService.send(message)
        if (sent) {
            messages.add(ChatMessage("Me", message))
            outgoingMessage = ""
        } else {
            statusHint = "Message send failed. Connect to another device first."
        }
    }

    private fun startListening() {
        val adapter = getBluetoothAdapter() ?: return
        if (!hasBluetoothPermission()) return
        val enabled = readBluetoothEnabled(adapter) ?: return
        if (enabled) chatService.startListening()
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
private fun BluetoothChatScreen(
    bluetoothStatus: String,
    connectionStatus: String,
    hint: String,
    pairedDevices: List<PairedDeviceItem>,
    messages: List<ChatMessage>,
    outgoingMessage: String,
    modifier: Modifier = Modifier,
    onToggleBluetooth: () -> Unit,
    onRefreshPaired: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onConnectDevice: (PairedDeviceItem) -> Unit,
    onMessageChange: (String) -> Unit,
    onSendMessage: () -> Unit
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Bluetooth Chat", style = MaterialTheme.typography.headlineMedium)
        Text(text = bluetoothStatus, style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 12.dp))
        Text(text = connectionStatus, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
        Text(
            text = hint,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
        )
        Button(onClick = onToggleBluetooth) { Text("Toggle Bluetooth") }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRefreshPaired, modifier = Modifier.weight(1f)) { Text("Refresh Paired Devices") }
            Button(onClick = onMakeDiscoverable, modifier = Modifier.weight(1f)) { Text("Allow Discovery") }
        }
        Text(text = "Paired Devices", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (pairedDevices.isEmpty()) {
            Text(
                text = "No paired devices found. Complete pairing in the system Bluetooth settings first.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(pairedDevices) { device ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onConnectDevice(device) }.padding(vertical = 10.dp)
                    ) {
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text(device.address, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        Text(text = "Messages", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(messages) { message ->
                Text(text = "${message.sender}: ${message.body}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
        OutlinedTextField(
            value = outgoingMessage,
            onValueChange = onMessageChange,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = { Text("Type a message") }
        )
        Button(onClick = onSendMessage, modifier = Modifier.padding(top = 12.dp)) { Text("Send Message") }
    }
}

private data class PairedDeviceItem(val name: String, val address: String)

private data class ChatMessage(val sender: String, val body: String)

private class BluetoothChatService(
    private val adapterProvider: () -> BluetoothAdapter?,
    private val hasPermission: () -> Boolean,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onListening()
        fun onConnected(deviceName: String)
        fun onMessageReceived(deviceName: String, message: String)
        fun onDisconnected(reason: String)
        fun onError(message: String)
    }

    @Volatile private var acceptThread: AcceptThread? = null
    @Volatile private var connectThread: ConnectThread? = null
    @Volatile private var connectedThread: ConnectedThread? = null

    fun startListening() {
        if (!hasPermission()) return
        if (acceptThread != null || connectedThread != null) return
        val adapter = adapterProvider() ?: return
        val thread = AcceptThread(adapter)
        acceptThread = thread
        thread.start()
        callbacks.onListening()
    }

    fun connect(device: BluetoothDevice) {
        if (!hasPermission()) {
            callbacks.onError("Bluetooth permission is required before connecting.")
            return
        }
        stopConnectThread()
        val thread = ConnectThread(device)
        connectThread = thread
        thread.start()
    }

    fun send(message: String): Boolean {
        val thread = connectedThread ?: return false
        return thread.write(message)
    }

    fun stop() {
        stopAcceptThread()
        stopConnectThread()
        stopConnectedThread()
    }

    private fun onSocketConnected(socket: BluetoothSocket, fallbackName: String) {
        stopAcceptThread()
        stopConnectThread()
        stopConnectedThread()
        val thread = ConnectedThread(socket)
        connectedThread = thread
        thread.start()
        callbacks.onConnected(thread.remoteName ?: fallbackName)
    }

    private fun stopAcceptThread() { acceptThread?.cancel(); acceptThread = null }
    private fun stopConnectThread() { connectThread?.cancel(); connectThread = null }
    private fun stopConnectedThread() { connectedThread?.cancel(); connectedThread = null }

    private inner class AcceptThread(adapter: BluetoothAdapter) : Thread() {
        private val serverSocket: BluetoothServerSocket? = try {
            if (!hasPermission()) null else adapter.listenUsingRfcommWithServiceRecord(APP_NAME, APP_UUID)
        } catch (_: IOException) {
            callbacks.onError("Failed to create the server socket.")
            null
        } catch (_: SecurityException) {
            callbacks.onError("The system rejected the Bluetooth server request.")
            null
        }

        override fun run() {
            val socket = try {
                serverSocket?.accept()
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                callbacks.onError("The system rejected the incoming connection request.")
                null
            }
            if (socket != null) onSocketConnected(socket, "Remote device")
        }

        fun cancel() {
            try {
                serverSocket?.close()
            } catch (_: IOException) {
            }
        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? = try {
            if (!hasPermission()) null else device.createRfcommSocketToServiceRecord(APP_UUID)
        } catch (_: IOException) {
            callbacks.onError("Failed to create the connection socket.")
            null
        } catch (_: SecurityException) {
            callbacks.onError("The system rejected the Bluetooth connection request.")
            null
        }

        override fun run() {
            val adapter = adapterProvider()
            try {
                if (hasPermission()) adapter?.cancelDiscovery()
                socket?.connect()
                if (socket != null) {
                    val name = try {
                        device.name ?: device.address
                    } catch (_: SecurityException) {
                        device.address
                    }
                    onSocketConnected(socket, name)
                } else {
                    callbacks.onError("No valid Bluetooth socket is available.")
                }
            } catch (_: IOException) {
                callbacks.onDisconnected("Connection failed. Make sure both devices are paired and the app is open on both.")
                cancel()
                startListening()
            } catch (_: SecurityException) {
                callbacks.onDisconnected("The system rejected the Bluetooth connection.")
                cancel()
            }
        }

        fun cancel() {
            try {
                socket?.close()
            } catch (_: IOException) {
            }
        }
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val reader = try { BufferedReader(InputStreamReader(socket.inputStream)) } catch (_: IOException) { null }
        private val writer = try { PrintWriter(socket.outputStream, true) } catch (_: IOException) { null }
        val remoteName: String? = try {
            if (hasPermission()) socket.remoteDevice?.name ?: socket.remoteDevice?.address else null
        } catch (_: SecurityException) {
            null
        }

        override fun run() {
            try {
                while (true) {
                    val line = reader?.readLine() ?: break
                    callbacks.onMessageReceived(remoteName ?: "Remote device", line)
                }
            } catch (_: IOException) {
                callbacks.onDisconnected("The Bluetooth connection was lost.")
            } finally {
                cancel()
                connectedThread = null
                startListening()
            }
        }

        fun write(message: String): Boolean {
            return try {
                writer?.println(message)
                writer?.checkError() == false
            } catch (_: Exception) {
                false
            }
        }

        fun cancel() {
            try {
                socket.close()
            } catch (_: IOException) {
            }
        }
    }

    companion object {
        private const val APP_NAME = "BluetoothChatApp"
        private val APP_UUID: UUID = UUID.fromString("0f14b64b-d8f4-42fe-b8b4-2894cb9f6e91")
    }
}

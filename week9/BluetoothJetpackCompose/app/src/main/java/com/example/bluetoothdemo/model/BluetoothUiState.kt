package com.example.bluetoothdemo.model

data class DeviceUi(
    val name: String,
    val address: String
)

enum class ConnectionPhase {
    IDLE, CONNECTING, LISTENING, CONNECTED, STOPPING
}

data class BluetoothUiState(
    val pairedDevices: List<DeviceUi> = emptyList(),
    val nearbyDevices: List<DeviceUi> = emptyList(),
    val receivedMessages: List<String> = emptyList(),
    val connectedDevice: String? = null,
    val connectionPhase: ConnectionPhase = ConnectionPhase.IDLE,
    val isScanning: Boolean = false,
    val pairingAddress: String? = null,
    val draft: String = "Hello from Jetpack Compose Bluetooth!",
    val status: String = "Enable Bluetooth to begin."
)
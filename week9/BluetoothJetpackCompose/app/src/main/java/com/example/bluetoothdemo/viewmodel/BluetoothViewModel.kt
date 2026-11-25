package com.example.bluetoothdemo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetoothdemo.data.BluetoothRepository
import com.example.bluetoothdemo.data.ConnectionEvent
import com.example.bluetoothdemo.data.DiscoveryEvent
import com.example.bluetoothdemo.model.BluetoothUiState
import com.example.bluetoothdemo.model.ConnectionPhase
import com.example.bluetoothdemo.model.DeviceUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAIRING_TIMEOUT_MS = 30_000L

class BluetoothViewModel(
    private val repository: BluetoothRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(BluetoothUiState())
    val uiState = _uiState.asStateFlow()

    private var connectionJob: Job? = null
    private var sendJob: Job? = null
    private var pairingTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            repository.discoveryEvents().collect { event ->
                when (event) {
                    is DiscoveryEvent.Found -> _uiState.update { state ->
                        if (!state.isScanning ||
                            state.nearbyDevices.any { it.address == event.device.address }
                        ) {
                            state
                        } else {
                            state.copy(nearbyDevices = state.nearbyDevices + event.device)
                        }
                    }

                    DiscoveryEvent.Finished -> _uiState.update { state ->
                        if (state.isScanning) {
                            state.copy(isScanning = false, status = "Scan finished.")
                        } else {
                            state
                        }
                    }

                    is DiscoveryEvent.BondChanged -> updateBond(event.device, event.bonded)

                    is DiscoveryEvent.BluetoothChanged -> {
                        if (event.enabled) {
                            setStatus("Bluetooth is ON.")
                        } else {
                            cancelPairingTimeout()
                            _uiState.update {
                                it.copy(
                                    pairedDevices = emptyList(),
                                    nearbyDevices = emptyList(),
                                    pairingAddress = null
                                )
                            }
                            stopConnection("Bluetooth is OFF.")
                        }
                    }
                }
            }
        }
    }

    fun missingPermissions(): Array<String> = repository.missingPermissions()

    fun onPermissionResult() {
        setStatus(
            if (missingPermissions().isEmpty()) "Permissions granted. Tap the action again."
            else "Bluetooth permissions are required."
        )
    }

    fun onEnableResult() {
        setStatus(if (repository.isEnabled()) "Bluetooth is ON." else "Bluetooth remains OFF.")
    }

    fun onDiscoverableResult(accepted: Boolean) {
        setStatus(
            if (accepted) "Discoverability enabled temporarily."
            else "Discoverability declined."
        )
    }

    fun loadPaired() {
        try {
            val devices = repository.loadPaired()
            _uiState.update {
                it.copy(
                    pairedDevices = devices,
                    status = "Found ${devices.size} paired device(s). Tap one to connect."
                )
            }
        } catch (e: Exception) {
            setStatus(e.message ?: "Could not load paired devices.")
        }
    }

    fun scan() {
        val state = _uiState.value
        if (state.isScanning || state.pairingAddress != null ||
            state.connectionPhase != ConnectionPhase.IDLE
        ) return

        try {
            repository.scan()
            _uiState.update {
                it.copy(
                    isScanning = true,
                    nearbyDevices = emptyList(),
                    status = "Scanning nearby devices..."
                )
            }
        } catch (e: Exception) {
            setStatus(e.message ?: "Could not start discovery.")
        }
    }

    fun pair(device: DeviceUi) {
        val state = _uiState.value
        if (state.pairingAddress != null || state.connectionPhase != ConnectionPhase.IDLE) return

        _uiState.update {
            it.copy(
                pairingAddress = device.address,
                status = "Pairing with ${device.name}. Confirm the Android prompt."
            )
        }

        try {
            val alreadyPaired = repository.pair(device.address)
            _uiState.update { it.copy(isScanning = false) }

            if (alreadyPaired) {
                updateBond(device, bonded = true)
            } else {
                startPairingTimeout(device)
            }
        } catch (e: Exception) {
            cancelPairingTimeout()
            _uiState.update {
                it.copy(
                    pairingAddress = null,
                    isScanning = false,
                    status = e.message ?: "Could not start pairing."
                )
            }
        }
    }

    private fun startPairingTimeout(device: DeviceUi) {
        cancelPairingTimeout()

        pairingTimeoutJob = viewModelScope.launch {
            delay(PAIRING_TIMEOUT_MS)
            pairingTimeoutJob = null

            _uiState.update { state ->
                if (state.pairingAddress == device.address) {
                    state.copy(
                        pairingAddress = null,
                        status = "Pairing timed out. Check the Android prompt or tap to retry."
                    )
                } else {
                    state
                }
            }
        }
    }

    private fun cancelPairingTimeout() {
        pairingTimeoutJob?.cancel()
        pairingTimeoutJob = null
    }

    private fun updateBond(device: DeviceUi, bonded: Boolean) {
        val requestedHere = _uiState.value.pairingAddress == device.address
        if (requestedHere) cancelPairingTimeout()

        _uiState.update { state ->
            val otherPairedDevices = state.pairedDevices.filterNot {
                it.address == device.address
            }

            state.copy(
                pairedDevices = if (bonded) {
                    (otherPairedDevices + device).sortedBy { it.name }
                } else {
                    otherPairedDevices
                },
                pairingAddress = if (requestedHere) null else state.pairingAddress,
                status = when {
                    !requestedHere -> state.status
                    bonded -> "Paired with ${device.name}. Select it under Paired devices."
                    else -> "Pairing canceled or failed. Tap the device to try again."
                }
            )
        }
    }

    fun connect(device: DeviceUi) {
        runConnection(
            phase = ConnectionPhase.CONNECTING,
            status = "Connecting to ${device.name}..."
        ) { repository.connect(device.address) }
    }

    fun startServer() {
        runConnection(
            phase = ConnectionPhase.LISTENING,
            status = "Starting server..."
        ) { repository.startServer() }
    }

    fun disconnect() {
        val status = when (_uiState.value.connectionPhase) {
            ConnectionPhase.LISTENING -> "Server stopped."
            ConnectionPhase.CONNECTING -> "Connection canceled."
            ConnectionPhase.CONNECTED -> "Disconnected."
            ConnectionPhase.IDLE, ConnectionPhase.STOPPING -> return
        }
        stopConnection(status)
    }

    private fun stopConnection(status: String) {
        val connection = connectionJob
        val sending = sendJob
        val hasPendingWork = connection?.isCompleted == false || sending?.isCompleted == false

        _uiState.update {
            it.copy(
                connectionPhase = if (hasPendingWork) {
                    ConnectionPhase.STOPPING
                } else {
                    ConnectionPhase.IDLE
                },
                connectedDevice = null,
                isScanning = false,
                status = status
            )
        }

        sending?.cancel()
        connection?.cancel()
        repository.disconnect()

        if (hasPendingWork) {
            viewModelScope.launch {
                connection?.join()
                sending?.join()
                _uiState.update { it.copy(connectionPhase = ConnectionPhase.IDLE) }
            }
        }
    }

    fun onDraftChanged(text: String) {
        _uiState.update { it.copy(draft = text) }
    }

    fun send() {
        val state = _uiState.value
        if (state.connectionPhase != ConnectionPhase.CONNECTED || state.draft.isBlank()) return
        if (sendJob?.isCompleted == false) return

        sendJob = viewModelScope.launch {
            try {
                val count = repository.send(state.draft)
                setStatus("Sent $count bytes.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                setStatus("Send failed: ${e.message}")
            }
        }
    }

    private fun runConnection(
        phase: ConnectionPhase,
        status: String,
        events: () -> Flow<ConnectionEvent>
    ) {
        val state = _uiState.value
        if (state.connectionPhase != ConnectionPhase.IDLE || state.pairingAddress != null) return
        if (connectionJob?.isCompleted == false) return

        val connectionEvents = events()
        _uiState.update {
            it.copy(connectionPhase = phase, isScanning = false, status = status)
        }

        connectionJob = viewModelScope.launch {
            try {
                connectionEvents.collect { event ->
                    when (event) {
                        ConnectionEvent.Listening ->
                            setStatus("Server listening. Connect from the other phone.")

                        is ConnectionEvent.Connected -> _uiState.update {
                            it.copy(
                                connectionPhase = ConnectionPhase.CONNECTED,
                                connectedDevice = event.name,
                                status = "Connected. You can send a message."
                            )
                        }

                        is ConnectionEvent.Received -> _uiState.update {
                            it.copy(receivedMessages = it.receivedMessages + event.text)
                        }
                    }
                }
                setStatus("Connection closed.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ensureActive()
                val fallback = "Bluetooth connection failed."
                setStatus(e.message ?: fallback)
            } finally {
                sendJob?.cancel()
                _uiState.update {
                    it.copy(
                        connectedDevice = null,
                        connectionPhase = if (it.connectionPhase == ConnectionPhase.STOPPING) {
                            ConnectionPhase.STOPPING
                        } else {
                            ConnectionPhase.IDLE
                        }
                    )
                }
            }
        }
    }

    private fun setStatus(status: String) {
        _uiState.update { it.copy(status = status) }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}
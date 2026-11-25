package com.example.bluetoothdemo.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.bluetoothdemo.model.DeviceUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.IOException
import java.util.UUID

sealed interface DiscoveryEvent {
    data class Found(val device: DeviceUi) : DiscoveryEvent
    data object Finished : DiscoveryEvent
    data class BluetoothChanged(val enabled: Boolean) : DiscoveryEvent
    data class BondChanged(val device: DeviceUi, val bonded: Boolean) : DiscoveryEvent
}

sealed interface ConnectionEvent {
    data object Listening : ConnectionEvent
    data class Connected(val name: String) : ConnectionEvent
    data class Received(val text: String) : ConnectionEvent
}

@SuppressLint("MissingPermission")
class BluetoothRepository(context: Context) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val appUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val openSockets = mutableSetOf<Closeable>()
    private var connectionGeneration = 0L
    private var closed = false

    @Volatile
    private var connectedSocket: BluetoothSocket? = null

    private val writeMutex = Mutex()

    fun missingPermissions(): Array<String> {
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return required.filterNot(::hasPermission).toTypedArray()
    }

    fun isEnabled(): Boolean = try {
        adapter?.isEnabled == true
    } catch (_: SecurityException) {
        false
    }

    fun discoveryEvents(): Flow<DiscoveryEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device = deviceFrom(intent) ?: return
                        trySend(DiscoveryEvent.Found(device.toDeviceUi()))
                    }

                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        trySend(DiscoveryEvent.Finished)
                    }

                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = deviceFrom(intent) ?: return
                        val bonded = when (
                            intent.getIntExtra(
                                BluetoothDevice.EXTRA_BOND_STATE,
                                BluetoothDevice.ERROR
                            )
                        ) {
                            BluetoothDevice.BOND_BONDED -> true
                            BluetoothDevice.BOND_NONE -> false
                            else -> return
                        }
                        trySend(DiscoveryEvent.BondChanged(device.toDeviceUi(), bonded))
                    }

                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        when (
                            intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR
                            )
                        ) {
                            BluetoothAdapter.STATE_ON ->
                                trySend(DiscoveryEvent.BluetoothChanged(true))

                            BluetoothAdapter.STATE_OFF ->
                                trySend(DiscoveryEvent.BluetoothChanged(false))
                        }
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }

        ContextCompat.registerReceiver(
            appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
        awaitClose { appContext.unregisterReceiver(receiver) }
    }

    fun loadPaired(): List<DeviceUi> =
        readyAdapter().bondedDevices.map { it.toDeviceUi() }.sortedBy { it.name }

    fun scan() {
        val bluetooth = readyAdapter()
        check(bluetooth.isDiscovering || bluetooth.startDiscovery()) {
            "Discovery did not start."
        }
    }

    // Returns true if the device was already paired.
    // Otherwise the result arrives through DiscoveryEvent.BondChanged.
    fun pair(address: String): Boolean {
        val bluetooth = readyAdapter()
        val device = bluetooth.getRemoteDevice(address)
        cancelDiscoverySafely()

        return when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> true
            BluetoothDevice.BOND_BONDING -> false
            else -> {
                check(
                    device.createBond() ||
                            device.bondState == BluetoothDevice.BOND_BONDING ||
                            device.bondState == BluetoothDevice.BOND_BONDED
                ) { "Could not start pairing." }
                device.bondState == BluetoothDevice.BOND_BONDED
            }
        }
    }

    fun connect(address: String): Flow<ConnectionEvent> {
        val generation = synchronized(openSockets) { connectionGeneration }

        return flow<ConnectionEvent> {
            val bluetooth = readyAdapter()
            bluetooth.cancelDiscovery()
            val device = bluetooth.getRemoteDevice(address)
            check(device.bondState == BluetoothDevice.BOND_BONDED) {
                "Pair this device before connecting."
            }

            useSocket(device.createRfcommSocketToServiceRecord(appUuid), generation) { client ->
                client.connect()
                readSession(client, generation)
            }
        }.flowOn(Dispatchers.IO)
    }

    fun startServer(): Flow<ConnectionEvent> {
        val generation = synchronized(openSockets) { connectionGeneration }

        return flow<ConnectionEvent> {
            val bluetooth = readyAdapter()
            bluetooth.cancelDiscovery()

            val listener = bluetooth.listenUsingRfcommWithServiceRecord(
                "BluetoothClassroomDemo", appUuid
            )
            useSocket(listener, generation) { server ->
                emit(ConnectionEvent.Listening)
                useSocket(server.accept(), generation) { client ->
                    closeQuietly(server)
                    readSession(client, generation)
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun send(text: String): Int {
        val client = checkNotNull(connectedSocket) { "Connect to a device first." }

        return withContext(Dispatchers.IO) {
            writeMutex.withLock {
                check(connectedSocket === client) { "The connection has ended." }
                val bytes = (text + "\n").toByteArray(Charsets.UTF_8)
                client.outputStream.write(bytes)
                client.outputStream.flush()
                bytes.size
            }
        }
    }

    fun disconnect() {
        releaseSockets(permanently = false)
        cancelDiscoverySafely()
    }

    fun close() {
        releaseSockets(permanently = true)
        cancelDiscoverySafely()
    }

    private fun releaseSockets(permanently: Boolean) {
        val sockets = synchronized(openSockets) {
            if (permanently) closed = true
            connectionGeneration++
            connectedSocket = null
            openSockets.toList().also { openSockets.clear() }
        }
        sockets.forEach(::closeQuietly)
    }

    private fun cancelDiscoverySafely() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.BLUETOOTH_ADMIN
        }

        if (hasPermission(permission)) {
            try {
                adapter?.cancelDiscovery()
            } catch (_: SecurityException) {
                // Permission may have changed since the check.
            }
        }
    }

    private suspend fun FlowCollector<ConnectionEvent>.readSession(
        client: BluetoothSocket,
        generation: Long
    ) {
        synchronized(openSockets) {
            checkCurrentConnection(generation)
            connectedSocket = client
        }

        try {
            emit(ConnectionEvent.Connected(client.remoteDevice.toDeviceUi().name))
            client.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    emit(ConnectionEvent.Received(line))
                }
            }
        } finally {
            synchronized(openSockets) {
                if (connectedSocket === client) connectedSocket = null
            }
        }
    }

    private suspend fun <T : Closeable> useSocket(
        socket: T,
        generation: Long,
        block: suspend (T) -> Unit
    ) {
        try {
            synchronized(openSockets) {
                checkCurrentConnection(generation)
                openSockets.add(socket)
            }
            block(socket)
        } finally {
            closeQuietly(socket)
            synchronized(openSockets) { openSockets.remove(socket) }
        }
    }

    // Call while holding the openSockets lock.
    private fun checkCurrentConnection(generation: Long) {
        if (closed || generation != connectionGeneration) {
            throw CancellationException("Bluetooth connection was stopped.")
        }
    }

    private fun closeQuietly(socket: Closeable) {
        try {
            socket.close()
        } catch (_: IOException) {
            // Continue cleaning up the other sockets.
        }
    }

    private fun readyAdapter(): BluetoothAdapter {
        check(missingPermissions().isEmpty()) { "Grant Bluetooth permissions first." }
        val bluetooth = checkNotNull(adapter) { "Bluetooth is unavailable on this device." }
        check(bluetooth.isEnabled) { "Enable Bluetooth first." }
        return bluetooth
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) ==
                PackageManager.PERMISSION_GRANTED

    private fun BluetoothDevice.toDeviceUi(): DeviceUi {
        val label = try {
            name ?: "Unknown device"
        } catch (_: SecurityException) {
            "Unknown device"
        }
        return DeviceUi(label, address)
    }

    @Suppress("DEPRECATION")
    private fun deviceFrom(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
}
package com.eiyooooo.adblink.adb

import com.eiyooooo.adblink.config.Preferences
import com.eiyooooo.adblink.config.SystemServices.usbManager
import com.eiyooooo.adblink.data.DeviceRepository
import com.eiyooooo.adblink.model.Device
import com.eiyooooo.adblink.model.connection.ConnectionAttempt
import com.eiyooooo.adblink.model.connection.ConnectionEndpoint
import com.eiyooooo.adblink.model.connection.ConnectionHost
import com.eiyooooo.adblink.model.connection.ConnectionResult
import com.eiyooooo.adblink.model.connection.ConnectionSession
import com.eiyooooo.adblink.model.connection.ConnectionState
import com.eiyooooo.adblink.model.connection.ConnectionStatus
import com.eiyooooo.adblink.model.connection.ConnectionTarget
import com.eiyooooo.adblink.model.connection.ConnectionTransport
import com.eiyooooo.adblink.model.connection.ConnectionType
import com.eiyooooo.adblink.model.connection.connectedStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal class AdbConnectionManager(
    private val adbKeyPair: AdbKeyPair,
    private val adbScope: CoroutineScope
) {

    private val _connectionSessions = MutableStateFlow<Map<String, ConnectionSession>>(emptyMap())
    val connectionSessions: StateFlow<Map<String, ConnectionSession>> = _connectionSessions

    fun connectDevice(device: Device) {
        _connectionSessions.value[device.uuid]?.job?.cancel()

        val job = adbScope.launch {
            val currentJob = coroutineContext[Job]
            updateSession(device.uuid) { session ->
                session.connection?.also { safeCloseConnection(it) }
                ConnectionSession(
                    status = ConnectionStatus.Connecting,
                    activeTarget = null,
                    attempt = null,
                    connection = null,
                    job = currentJob,
                    lastEndpoint = null,
                    lastHost = null,
                    lastError = null
                )
            }

            try {
                var connection: AdbConnection? = null
                var shouldAbortAttempts = false
                var connected = false
                var transport: ConnectionTransport? = null
                var lastFailureReason: ConnectionState = ConnectionState.CONNECTION_FAILED_UNKNOWN
                var successfulEndpoint: ConnectionEndpoint? = null
                var successfulHost: ConnectionHost? = null

                if (device.usbDevice != null) {
                    updateSession(device.uuid) { session ->
                        session.copy(attempt = ConnectionAttempt.Usb)
                    }
                    val result = tryCreateAndConnect(
                        "USB",
                        device.uuid,
                        { markAwaitingAuthorization(device.uuid) },
                        { AdbConnection.create(usbManager, device.usbDevice, adbKeyPair) }
                    )
                    when (result) {
                        is ConnectionResult.Success -> {
                            connection = result.connection
                            connected = true
                            transport = ConnectionTransport.USB
                        }

                        is ConnectionResult.Failure -> {
                            lastFailureReason = result.reason
                            if (result.reason == ConnectionState.CONNECTION_FAILED_UNAUTHORIZED) {
                                Timber.d("Aborting further connection attempts for device ${device.uuid} due to unauthorized failure")
                                shouldAbortAttempts = true
                            }
                        }
                    }
                }

                if (!connected && !shouldAbortAttempts) {
                    val sortedEndpoints = device.connectionEndpoints.sortedWith(
                        compareByDescending<ConnectionEndpoint> { it.lastUsedTime }.thenBy {
                            when (it.type) {
                                ConnectionType.TLS -> 0
                                ConnectionType.TCP -> 1
                            }
                        }
                    )

                    val availableHosts = device.hosts.mapNotNull { host ->
                        val trimmed = host.host.trim()
                        trimmed.takeIf { it.isNotBlank() }?.let { host.copy(host = it) }
                    }.distinctBy { host -> host.host.lowercase() }

                    if (availableHosts.isEmpty() && sortedEndpoints.isNotEmpty()) {
                        lastFailureReason = ConnectionState.CONNECTION_FAILED_HOST_UNREACHABLE
                    }

                    for (endpoint in sortedEndpoints) {
                        if (connected || shouldAbortAttempts) break

                        for (host in availableHosts) {
                            if (connected || shouldAbortAttempts) break

                            val result = tryCreateAndConnect(
                                "${endpoint.type.name} ${host.host}:${endpoint.port}",
                                device.uuid,
                                { markAwaitingAuthorization(device.uuid) },
                                {
                                    updateSession(device.uuid) { session ->
                                        session.copy(
                                            attempt = ConnectionAttempt.Network(
                                                ConnectionTarget(
                                                    host = host.host,
                                                    port = endpoint.port,
                                                    type = endpoint.type
                                                )
                                            )
                                        )
                                    }
                                    AdbConnection.create(host.host, endpoint.port, adbKeyPair)
                                }
                            )
                            when (result) {
                                is ConnectionResult.Success -> {
                                    connection = result.connection
                                    connected = true
                                    successfulEndpoint = endpoint
                                    successfulHost = host
                                    transport = when (endpoint.type) {
                                        ConnectionType.TLS -> ConnectionTransport.TLS
                                        ConnectionType.TCP -> ConnectionTransport.TCP
                                    }
                                }

                                is ConnectionResult.Failure -> {
                                    lastFailureReason = result.reason
                                    if (result.reason == ConnectionState.CONNECTION_FAILED_UNAUTHORIZED) {
                                        Timber.d("Aborting further connection attempts for device ${device.uuid} due to unauthorized failure")
                                        shouldAbortAttempts = true
                                    }
                                }
                            }
                        }
                    }
                }

                if (connected && connection?.isConnectionEstablished == true && transport != null) {
                    val target = if (transport != ConnectionTransport.USB && successfulEndpoint != null && successfulHost != null) {
                        ConnectionTarget(
                            host = successfulHost.host,
                            port = successfulEndpoint.port,
                            type = successfulEndpoint.type
                        )
                    } else {
                        null
                    }

                    if (transport != ConnectionTransport.USB && successfulEndpoint != null) {
                        updateEndpointLastUsedTime(device, successfulEndpoint, successfulHost)
                    }

                    connection.bindConnectionListener(device.uuid)
                    updateSession(device.uuid) { session ->
                        session.copy(
                            status = ConnectionStatus.Connected(transport),
                            activeTarget = target,
                            attempt = null,
                            connection = connection,
                            job = null,
                            lastEndpoint = successfulEndpoint,
                            lastHost = successfulHost,
                            lastError = null
                        )
                    }
                    Timber.d("Device ${device.uuid} connected successfully via ${transport.name}")

                    if (device.isUnidentified) {
                        connection.identifyDevice(device) { updated ->
                            DeviceRepository.updateDevice(device) {
                                updated
                            }
                        }
                    }
                } else {
                    safeCloseConnection(connection)
                    updateSession(device.uuid) { session ->
                        session.copy(
                            status = ConnectionStatus.Failed(lastFailureReason),
                            activeTarget = null,
                            attempt = null,
                            connection = null,
                            job = null,
                            lastEndpoint = null,
                            lastHost = null,
                            lastError = lastFailureReason
                        )
                    }
                    Timber.w("All connection methods failed for device ${device.uuid}, last failure: ${lastFailureReason.name}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to connect device ${device.uuid}")
                updateSession(device.uuid) { session ->
                    session.copy(
                        status = ConnectionStatus.Failed(ConnectionState.CONNECTION_FAILED_UNKNOWN),
                        activeTarget = null,
                        attempt = null,
                        connection = null,
                        job = null,
                        lastEndpoint = null,
                        lastHost = null,
                        lastError = ConnectionState.CONNECTION_FAILED_UNKNOWN
                    )
                }
            }
        }

        updateSession(device.uuid) { session ->
            session.copy(job = job)
        }
    }

    fun reconnectDevice(device: Device) {
        val session = _connectionSessions.value[device.uuid]
        val currentConnection = session?.connection
        val currentState = session?.status?.toConnectionState()

        // Check if device connection method has changed or connection is broken
        val isConnected = currentState in connectedStateList
        val shouldReconnect = when {
            currentState == ConnectionState.CONNECTING_AWAITING_AUTHORIZATION -> {
                Timber.d("Reconnect decision for ${device.uuid}: awaiting authorization")
                false
            }

            currentConnection == null -> {
                Timber.d("Reconnect decision for ${device.uuid}: no active connection")
                true
            }

            !isConnected -> {
                Timber.d("Reconnect decision for ${device.uuid}: not in connected state (${currentState?.name ?: "unknown"})")
                true
            }

            !currentConnection.isConnected -> {
                Timber.d("Reconnect decision for ${device.uuid}: connection not connected")
                true
            }

            !currentConnection.isConnectionEstablished -> {
                Timber.d("Reconnect decision for ${device.uuid}: connection not established")
                true
            }

            session.status is ConnectionStatus.Connected &&
                    session.status.transport != ConnectionTransport.USB &&
                    session.lastHost != null &&
                    !device.hosts.any { it.key == session.lastHost.key } -> {
                Timber.d("Reconnect decision for ${device.uuid}: last host no longer available")
                true
            }

            session.status is ConnectionStatus.Connected &&
                    session.status.transport != ConnectionTransport.USB &&
                    session.lastEndpoint != null &&
                    !device.connectionEndpoints.any { it.key == session.lastEndpoint.key } -> {
                Timber.d("Reconnect decision for ${device.uuid}: last endpoint no longer available")
                true
            }

            else -> {
                Timber.d("Reconnect decision for ${device.uuid}: connection unchanged")
                false
            }
        }

        if (shouldReconnect) {
            Timber.d("Device connection needs update for ${device.uuid}, reconnecting")
            disconnectDevice(device.uuid)
            connectDevice(device)
        } else {
            Timber.d("Device connection unchanged for ${device.uuid}, keeping existing connection")
        }
    }

    fun disconnectDevice(deviceUuid: String) {
        val session = _connectionSessions.value[deviceUuid]
        session?.job?.cancel()
        safeCloseConnection(session?.connection)

        updateSession(deviceUuid) {
            ConnectionSession(status = ConnectionStatus.Disconnected)
        }
        Timber.d("Disconnected device $deviceUuid")
    }

    private fun updateEndpointLastUsedTime(
        device: Device,
        successfulEndpoint: ConnectionEndpoint,
        usedHost: ConnectionHost?
    ) {
        adbScope.launch {
            try {
                val updatedEndpoints = device.connectionEndpoints.map { endpoint ->
                    if (endpoint.key == successfulEndpoint.key) {
                        endpoint.withUpdatedTime()
                    } else {
                        endpoint
                    }
                }
                val updatedHosts = usedHost?.let { host ->
                    device.hosts.map { existingHost ->
                        if (existingHost.key == host.key) {
                            existingHost.withUpdatedTime()
                        } else {
                            existingHost
                        }
                    }
                } ?: device.hosts

                val updatedDevice = device.copy(
                    connectionEndpoints = updatedEndpoints,
                    hosts = updatedHosts
                )
                DeviceRepository.updateDevice(device) { updatedDevice }
                val hostInfo = usedHost?.host?.let { "$it:" } ?: ""
                Timber.d("Updated last used time for endpoint ${successfulEndpoint.type.name} $hostInfo${successfulEndpoint.port}")
            } catch (e: Exception) {
                val hostInfo = usedHost?.host?.let { "$it:" } ?: ""
                Timber.w(e, "Failed to update last used time for endpoint ${successfulEndpoint.type.name} $hostInfo${successfulEndpoint.port}")
            }
        }
    }

    private fun AdbConnection.bindConnectionListener(deviceUuid: String) =
        setConnectionListener { e ->
            updateSession(deviceUuid) { session ->
                if (session.connection != this) {
                    session
                } else {
                    session.copy(
                        status = ConnectionStatus.Failed(ConnectionState.CONNECTION_LOST),
                        activeTarget = null,
                        attempt = null,
                        connection = null,
                        job = null,
                        lastEndpoint = null,
                        lastHost = null,
                        lastError = ConnectionState.CONNECTION_LOST
                    )
                }
            }
            safeCloseConnection(this)
            Timber.w(e, "Connection lost for device $deviceUuid")
        }

    private fun safeCloseConnection(connection: AdbConnection?) {
        connection ?: return
        adbScope.launch(Dispatchers.IO) {
            try {
                connection.close()
            } catch (e: Exception) {
                Timber.e(e, "Error closing connection")
            }
        }
    }

    private inline fun updateSession(deviceUuid: String, block: (ConnectionSession) -> ConnectionSession) {
        _connectionSessions.update { sessions ->
            val current = sessions[deviceUuid] ?: ConnectionSession()
            sessions + (deviceUuid to block(current))
        }
    }

    private fun markAwaitingAuthorization(deviceUuid: String) {
        updateSession(deviceUuid) { session ->
            session.copy(status = ConnectionStatus.AwaitingAuthorization)
        }
    }

    companion object {
        private suspend fun tryCreateAndConnect(
            connectionType: String,
            deviceUuid: String,
            onAwaitingAuth: () -> Unit,
            createConnection: () -> AdbConnection
        ) = withContext(Dispatchers.IO) {
            try {
                Timber.d("Attempting $connectionType connection for device $deviceUuid (first attempt with auth check)")
                val connection = createConnection()

                // First attempt: check if authorization is required
                if (connection.connect(Preferences.adbConnectionTimeout.toLong(), TimeUnit.SECONDS, true)) {
                    Timber.d("$connectionType connection successful for device $deviceUuid")
                    ConnectionResult.Success(connection)
                } else {
                    Timber.d("$connectionType connection failed for device $deviceUuid - timeout on first attempt")
                    connection.close()
                    ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_TIMEOUT)
                }
            } catch (e: AdbAuthenticationFailedException) {
                Timber.d(e, "$connectionType connection failed for device $deviceUuid - authentication required, trying second attempt")
                // Authorization required, try second attempt
                try {
                    val connection = createConnection()
                    onAwaitingAuth()

                    if (connection.connect(Preferences.adbConnectionTimeout.toLong(), TimeUnit.SECONDS, false)) {
                        Timber.d("$connectionType connection successful for device $deviceUuid on second attempt")
                        ConnectionResult.Success(connection)
                    } else {
                        Timber.d("$connectionType connection failed for device $deviceUuid - timeout on second attempt")
                        connection.close()
                        ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_UNAUTHORIZED)
                    }
                } catch (e2: Exception) {
                    Timber.d(e2, "$connectionType second connection attempt failed for device $deviceUuid")
                    ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_UNAUTHORIZED)
                }
            } catch (e: AdbPairingRequiredException) {
                Timber.d(e, "$connectionType connection failed for device $deviceUuid - pairing required")
                ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_PAIRING_REQUIRED)
            } catch (e: SocketTimeoutException) {
                Timber.d(e, "$connectionType connection failed for device $deviceUuid - socket timeout")
                ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_TIMEOUT)
            } catch (e: TimeoutException) {
                Timber.d(e, "$connectionType connection failed for device $deviceUuid - timeout")
                ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_TIMEOUT)
            } catch (e: ConnectException) {
                Timber.d(e, "$connectionType connection failed for device $deviceUuid - connect exception")
                ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_HOST_UNREACHABLE)
            } catch (e: UnknownHostException) {
                Timber.d(e, "$connectionType connection failed for device $deviceUuid - unknown host")
                ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_HOST_UNREACHABLE)
            } catch (e: Exception) {
                Timber.d(e, "$connectionType connection failed for device $deviceUuid - unknown error")
                ConnectionResult.Failure(ConnectionState.CONNECTION_FAILED_UNKNOWN)
            }
        }

        private suspend fun AdbConnection.identifyDevice(
            device: Device,
            onDeviceIdentified: suspend (Device) -> Unit
        ) = withContext(Dispatchers.IO) {
            try {
                Timber.d("Identifying device ${device.uuid}")

                val deviceBrand = runAdbCmd("getprop ro.product.brand").trim()
                val deviceName = runAdbCmd("getprop ro.product.model").trim()
                val deviceSerial = runAdbCmd("getprop ro.serialno").trim()

                Timber.d("Device identification for ${device.uuid}: brand=$deviceBrand, name=$deviceName, serial=$deviceSerial")

                val updatedDevice = device.copy(
                    isUnidentified = false,
                    deviceBrand = deviceBrand.takeIf { it.isNotEmpty() } ?: device.deviceBrand,
                    deviceName = deviceName.takeIf { it.isNotEmpty() } ?: device.deviceName,
                    deviceSerial = deviceSerial.takeIf { it.isNotEmpty() } ?: device.deviceSerial,
                    name = device.name.takeIf { it.isNotEmpty() } ?: "$deviceBrand $deviceName".trim()
                )

                onDeviceIdentified(updatedDevice)
                Timber.i("Successfully identified device ${device.uuid}: $deviceBrand $deviceName")

            } catch (e: Exception) {
                Timber.e(e, "Failed to identify device ${device.uuid}")
            }
        }
    }
}

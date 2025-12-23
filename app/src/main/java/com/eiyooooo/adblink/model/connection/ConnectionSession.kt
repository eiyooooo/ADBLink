package com.eiyooooo.adblink.model.connection

import com.eiyooooo.adblink.adb.AdbConnection
import kotlinx.coroutines.Job

enum class ConnectionTransport {
    USB,
    TLS,
    TCP
}

data class ConnectionTarget(
    val host: String,
    val port: Int,
    val type: ConnectionType
)

sealed class ConnectionStatus {
    data object Disconnected : ConnectionStatus()
    data object Connecting : ConnectionStatus()
    data object AwaitingAuthorization : ConnectionStatus()
    data class Connected(val transport: ConnectionTransport) : ConnectionStatus()
    data class Failed(val reason: ConnectionState) : ConnectionStatus()

    fun toConnectionState(): ConnectionState {
        return when (this) {
            is Connected -> {
                when (transport) {
                    ConnectionTransport.USB -> ConnectionState.CONNECTED_USB
                    ConnectionTransport.TLS -> ConnectionState.CONNECTED_TLS
                    ConnectionTransport.TCP -> ConnectionState.CONNECTED_TCP
                }
            }

            Connecting -> ConnectionState.CONNECTING
            AwaitingAuthorization -> ConnectionState.CONNECTING_AWAITING_AUTHORIZATION
            Disconnected -> ConnectionState.DISCONNECTED
            is Failed -> reason
        }
    }
}

sealed class ConnectionResult {
    data class Success(val connection: AdbConnection) : ConnectionResult()
    data class Failure(val reason: ConnectionState) : ConnectionResult()
}

sealed class ConnectionAttempt {
    data object Usb : ConnectionAttempt()
    data class Network(val target: ConnectionTarget) : ConnectionAttempt()
}

data class ConnectionSession(
    val status: ConnectionStatus = ConnectionStatus.Disconnected,
    val activeTarget: ConnectionTarget? = null,
    val attempt: ConnectionAttempt? = null,
    val connection: AdbConnection? = null,
    val job: Job? = null,
    val lastEndpoint: ConnectionEndpoint? = null,
    val lastHost: ConnectionHost? = null,
    val lastError: ConnectionState? = null
)

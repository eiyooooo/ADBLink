package com.eiyooooo.adblink.entity

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_USB,
    CONNECTED_TLS,
    CONNECTED_TCP
}

val connectedStateList = listOf(
    ConnectionState.CONNECTED_USB,
    ConnectionState.CONNECTED_TLS,
    ConnectionState.CONNECTED_TCP
)

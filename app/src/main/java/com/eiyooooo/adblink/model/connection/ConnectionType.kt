package com.eiyooooo.adblink.model.connection

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionType {
    TCP,
    TLS
}
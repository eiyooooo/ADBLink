package com.eiyooooo.adblink.entity

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionType {
    TCP,
    TLS
}

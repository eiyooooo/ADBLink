package com.eiyooooo.adblink.data

import kotlinx.serialization.Serializable

@Serializable
data class HostPort(
    val host: String,
    val port: Int
)

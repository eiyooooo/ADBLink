package com.eiyooooo.adblink.data

import com.eiyooooo.adblink.entity.ConnectionType
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionEndpoint(
    val host: String,
    val port: Int,
    val type: ConnectionType,
    val lastUsedTime: Long = 0L
) {

    fun withUpdatedTime(time: Long = System.currentTimeMillis()): ConnectionEndpoint {
        return this.copy(lastUsedTime = time)
    }
}

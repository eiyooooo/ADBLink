package com.eiyooooo.adblink.data

import com.eiyooooo.adblink.entity.ConnectionType
import kotlinx.serialization.Serializable

@Serializable
data class ConnectionEndpoint(
    val port: Int,
    val type: ConnectionType,
    val lastUsedTime: Long = 0L,
    val manuallyAdded: Boolean = false
) {

    val key = "${type.name}:$port"

    fun withUpdatedTime(time: Long = System.currentTimeMillis()): ConnectionEndpoint {
        return this.copy(lastUsedTime = time)
    }
}

fun normalizeEndpointList(vararg endpointLists: List<ConnectionEndpoint>): List<ConnectionEndpoint> {
    val capacity = endpointLists.sumOf { it.size }.coerceAtLeast(16)
    val map = LinkedHashMap<String, ConnectionEndpoint>(capacity)

    for (list in endpointLists) {
        for (ep in list) {
            if (ep.port in 1..65535) {
                val key = ep.key
                val prev = map[key]
                if (prev == null) {
                    map[key] = ep
                } else {
                    if (ep.lastUsedTime > prev.lastUsedTime) {
                        map[key] = prev.copy(
                            lastUsedTime = ep.lastUsedTime,
                            manuallyAdded = prev.manuallyAdded || ep.manuallyAdded
                        )
                    } else if (!prev.manuallyAdded && ep.manuallyAdded) {
                        map[key] = prev.copy(manuallyAdded = true)
                    }
                }
            }
        }
    }

    return map.values.toList()
}

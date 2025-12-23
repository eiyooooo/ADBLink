package com.eiyooooo.adblink.model.connection

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionHost(
    val host: String,
    val lastUsedTime: Long = 0L,
    val manuallyAdded: Boolean = false
) {

    val key = host.trim().lowercase()

    fun withUpdatedTime(time: Long = System.currentTimeMillis()): ConnectionHost {
        return copy(lastUsedTime = time)
    }
}

fun normalizeHostList(vararg hostLists: List<ConnectionHost>): List<ConnectionHost> {
    val capacity = hostLists.sumOf { it.size }.coerceAtLeast(16)
    val map = LinkedHashMap<String, ConnectionHost>(capacity)

    for (list in hostLists) {
        for (host in list) {
            val trimmed = host.host.trim()
            if (trimmed.isEmpty()) continue
            val key = trimmed.lowercase()
            val existing = map[key]
            if (existing == null) {
                map[key] = if (trimmed === host.host) {
                    host
                } else {
                    host.copy(host = trimmed)
                }
            } else {
                if (host.lastUsedTime > existing.lastUsedTime) {
                    map[key] = existing.copy(
                        lastUsedTime = host.lastUsedTime,
                        manuallyAdded = existing.manuallyAdded || host.manuallyAdded
                    )
                } else if (!existing.manuallyAdded && host.manuallyAdded) {
                    map[key] = existing.copy(manuallyAdded = true)
                }
            }
        }
    }

    return map.values.toList()
}

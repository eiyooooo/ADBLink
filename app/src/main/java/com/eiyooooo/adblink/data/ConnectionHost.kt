package com.eiyooooo.adblink.data

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

fun normalizeHostList(vararg hostList: List<ConnectionHost>): List<ConnectionHost> {
    val allHosts = hostList.flatMap { it }

    val normalized = mutableListOf<ConnectionHost>()
    val indexByKey = mutableMapOf<String, Int>()

    for (host in allHosts) {
        val trimmed = host.host.trim()
        if (trimmed.isEmpty()) continue

        val sanitized = host.copy(host = trimmed)
        val key = trimmed.lowercase()
        val existingIndex = indexByKey[key]
        if (existingIndex == null) {
            indexByKey[key] = normalized.size
            normalized.add(sanitized)
        } else {
            val existing = normalized[existingIndex]
            normalized[existingIndex] = existing.copy(
                host = sanitized.host,
                lastUsedTime = maxOf(existing.lastUsedTime, sanitized.lastUsedTime),
                manuallyAdded = existing.manuallyAdded || sanitized.manuallyAdded
            )
        }
    }

    return normalized
}

package com.eiyooooo.adblink.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import kotlin.system.measureTimeMillis

fun getIp(): Pair<ArrayList<String>, ArrayList<String>> {
    val ipv4Addresses = ArrayList<String>()
    val ipv6Addresses = ArrayList<String>()
    try {
        val networkInterfaces = NetworkInterface.getNetworkInterfaces()
        while (networkInterfaces.hasMoreElements()) {
            val inetAddresses = networkInterfaces.nextElement().getInetAddresses()
            while (inetAddresses.hasMoreElements()) {
                val inetAddress = inetAddresses.nextElement()
                if (!inetAddress.isLoopbackAddress) {
                    if (inetAddress is Inet4Address) {
                        inetAddress.hostAddress?.let {
                            ipv4Addresses.add(it)
                        }
                    } else if (inetAddress is Inet6Address && !inetAddress.isLinkLocalAddress) {
                        ipv6Addresses.add("[" + inetAddress.hostAddress + "]")
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    return ipv4Addresses to ipv6Addresses
}

suspend fun InetAddress.isReachableLocallySuspend(timeout: Int = 2000): Boolean = withContext(Dispatchers.IO) {
    try {
        if (!isSiteLocalAddress) return@withContext false
        isReachable(timeout)
    } catch (e: Exception) {
        Timber.d(e, "Failed to check: $hostAddress reachability")
        false
    }
}

fun String.isValidHostAddress(): Boolean {
    if (isBlank()) return false

    val cleanHost = if (startsWith("[") && endsWith("]")) {
        substring(1, length - 1)
    } else {
        this
    }

    val ipv4Pattern = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")
    if (ipv4Pattern.matches(cleanHost)) return true

    val domainPattern = Regex("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$|^localhost$")
    if (domainPattern.matches(cleanHost)) return true

    return try {
        InetAddress.getByName(cleanHost) is Inet6Address
    } catch (_: Exception) {
        false
    }
}

fun String.isValidPort(): Boolean {
    if (isBlank()) return false
    val port = toIntOrNull() ?: return false
    return port in 0..65535
}

suspend fun testLatency(
    ipAddress: String,
    port: Int,
    timeoutMs: Int = 3000
): IpLatency = withContext(Dispatchers.IO) {
    try {
        val latency = measureTimeMillis {
            Socket().use { socket ->
                socket.connect(InetAddress.getByName(ipAddress).let {
                    java.net.InetSocketAddress(it, port)
                }, timeoutMs)
            }
        }
        IpLatency(ipAddress, latency, true)
    } catch (_: Exception) {
        try {
            val latency = measureTimeMillis {
                val inetAddress = InetAddress.getByName(ipAddress)
                val isReachable = inetAddress.isReachable(timeoutMs)
                if (!isReachable) {
                    throw Exception("Host unreachable")
                }
            }
            IpLatency(ipAddress, latency, true)
        } catch (_: Exception) {
            IpLatency(ipAddress, -1, false)
        }
    }
}

data class IpLatency(
    val ipAddress: String,
    val latencyMs: Long,
    val isReachable: Boolean
) {
    val latencyLevel: LatencyLevel
        get() = when {
            !isReachable -> LatencyLevel.UNREACHABLE
            latencyMs < 50 -> LatencyLevel.EXCELLENT
            latencyMs < 100 -> LatencyLevel.GOOD
            latencyMs < 200 -> LatencyLevel.FAIR
            latencyMs < 500 -> LatencyLevel.POOR
            else -> LatencyLevel.VERY_POOR
        }
}

enum class LatencyLevel {
    /** < 50ms */
    EXCELLENT,

    /** 50-100ms */
    GOOD,

    /** 100-200ms */
    FAIR,

    /** 200-500ms */
    POOR,

    /** > 500ms */
    VERY_POOR,
    UNREACHABLE
}

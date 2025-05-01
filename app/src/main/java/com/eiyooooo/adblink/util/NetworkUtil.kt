package com.eiyooooo.adblink.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

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
                        inetAddress.getHostAddress()?.let {
                            ipv4Addresses.add(it)
                        }
                    } else if (inetAddress is Inet6Address && !inetAddress.isLinkLocalAddress()) {
                        ipv6Addresses.add("[" + inetAddress.getHostAddress() + "]")
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

    val ipv4Pattern = Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\$")
    if (ipv4Pattern.matches(cleanHost)) return true

    val domainPattern = Regex("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}\$|^localhost\$")
    if (domainPattern.matches(cleanHost)) return true

    return try {
        InetAddress.getByName(cleanHost) is Inet6Address
    } catch (e: Exception) {
        false
    }
}

fun String.isValidPort(): Boolean {
    if (isBlank()) return false
    val port = toIntOrNull() ?: return false
    return port in 0..65535
}

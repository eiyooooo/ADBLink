package com.eiyooooo.adblink.util

import java.net.Inet4Address
import java.net.Inet6Address
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

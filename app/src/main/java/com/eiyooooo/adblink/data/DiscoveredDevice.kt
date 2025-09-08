package com.eiyooooo.adblink.data

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions

enum class AdbServiceType(val value: String) {
    ADB_TCP("_adb._tcp"),
    ADB_TLS_CONNECT("_adb-tls-connect._tcp"),
    ADB_TLS_PAIRING("_adb-tls-pairing._tcp");

    companion object {
        fun fromString(serviceType: String): AdbServiceType {
            return entries.find { it.value == serviceType } ?: ADB_TCP
        }
    }
}

data class DiscoveredDevice(
    val deviceSerial: String,
    val serviceName: String,
    val serviceType: AdbServiceType,
    val hostAddresses: List<String>,
    val port: Int,
    val serviceInfo: NsdServiceInfo
) {

    companion object {
        /**
         * Extracts device serial from service name
         * Expected format: adb-{serial}-{suffix} or adb-{serial}
         */
        fun extractSerialFromServiceName(serviceName: String): String? {
            return Regex("""adb-([^-]+)""").find(serviceName)?.groupValues?.get(1)
        }

        fun fromNsdServiceInfo(serviceInfo: NsdServiceInfo): DiscoveredDevice? {
            val deviceSerial = extractSerialFromServiceName(serviceInfo.serviceName) ?: return null

            val hostAddresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)
            ) {
                serviceInfo.hostAddresses.mapNotNull { it.hostAddress }
            } else {
                @Suppress("DEPRECATION")
                listOfNotNull(serviceInfo.host?.hostAddress)
            }

            return DiscoveredDevice(
                deviceSerial = deviceSerial,
                serviceName = serviceInfo.serviceName,
                serviceType = AdbServiceType.fromString(serviceInfo.serviceType),
                hostAddresses = hostAddresses,
                port = serviceInfo.port,
                serviceInfo = serviceInfo
            )
        }
    }
}

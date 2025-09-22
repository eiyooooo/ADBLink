package com.eiyooooo.adblink.adb.discover

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import timber.log.Timber

data class DiscoveredDevice(
    val deviceSerial: String,
    val serviceName: String,
    val serviceType: AdbDiscoverServiceType,
    val hostAddresses: List<String>,
    val port: Int,
    val serviceInfo: NsdServiceInfo
) {

    companion object {
        /**
         * Extracts device serial from service name
         * Expected format: adb-{serial}-{suffix} or adb-{serial}
         */
        private fun extractSerialFromServiceName(serviceName: String): String? {
            return Regex("""adb-([^-]+)""").find(serviceName)?.groupValues?.get(1)
        }

        fun fromNsdServiceInfo(serviceInfo: NsdServiceInfo): DiscoveredDevice? {
            val deviceSerial = extractSerialFromServiceName(serviceInfo.serviceName) ?: run {
                Timber.w("Failed to extract device serial from service name: ${serviceInfo.serviceName}")
                return null
            }

            val hostAddresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                        && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.TIRAMISU) >= 7)
            ) {
                serviceInfo.hostAddresses.mapNotNull { it.hostAddress }
            } else {
                @Suppress("DEPRECATION")
                listOfNotNull(serviceInfo.host?.hostAddress)
            }

            if (hostAddresses.isEmpty()) {
                Timber.w("No valid host addresses found for device: $deviceSerial")
                return null
            }

            val serviceType = AdbDiscoverServiceType.fromNsdServiceInfo(serviceInfo) ?: run {
                Timber.w("Unknown service type: ${serviceInfo.serviceType}")
                return null
            }

            return DiscoveredDevice(
                deviceSerial = deviceSerial,
                serviceName = serviceInfo.serviceName,
                serviceType = serviceType,
                hostAddresses = hostAddresses,
                port = serviceInfo.port,
                serviceInfo = serviceInfo
            )
        }
    }
}

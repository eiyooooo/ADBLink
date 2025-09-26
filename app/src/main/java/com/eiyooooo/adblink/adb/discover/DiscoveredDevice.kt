package com.eiyooooo.adblink.adb.discover

import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.ext.SdkExtensions
import com.eiyooooo.adblink.data.ConnectionEndpoint
import com.eiyooooo.adblink.entity.ConnectionType
import timber.log.Timber

data class DiscoveredDevice(
    val deviceSerial: String,
    val serviceName: String,
    val connectionEndpoints: List<ConnectionEndpoint>,
    val serviceTypes: Set<AdbDiscoverServiceType>
) {

    fun mergeWith(other: DiscoveredDevice): DiscoveredDevice {
        val mergedEndpoints = (connectionEndpoints + other.connectionEndpoints)
            .distinctBy { Triple(it.host, it.port, it.type) }
            .tlsFirst()

        val preferredServiceName = when {
            other.connectionEndpoints.any { it.type == ConnectionType.TLS } -> other.serviceName
            connectionEndpoints.any { it.type == ConnectionType.TLS } -> serviceName
            else -> other.serviceName.ifBlank { serviceName }
        }

        return copy(
            serviceName = preferredServiceName,
            connectionEndpoints = mergedEndpoints,
            serviceTypes = serviceTypes + other.serviceTypes
        )
    }

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

            val connectionType = when (serviceType) {
                AdbDiscoverServiceType.ADB_TCP -> ConnectionType.TCP
                AdbDiscoverServiceType.ADB_TLS_CONNECT,
                AdbDiscoverServiceType.ADB_TLS_PAIRING -> ConnectionType.TLS
            }

            val endpoints = hostAddresses.map { host ->
                ConnectionEndpoint(
                    host = host,
                    port = serviceInfo.port,
                    type = connectionType
                )
            }.distinctBy { Triple(it.host, it.port, it.type) }
                .tlsFirst()

            if (endpoints.isEmpty()) {
                Timber.w("No valid connection endpoints found for device: $deviceSerial")
                return null
            }

            return DiscoveredDevice(
                deviceSerial = deviceSerial,
                serviceName = serviceInfo.serviceName,
                connectionEndpoints = endpoints,
                serviceTypes = setOf(serviceType)
            )
        }
    }
}

private fun List<ConnectionEndpoint>.tlsFirst(): List<ConnectionEndpoint> {
    if (isEmpty()) {
        return this
    }
    val containsTls = any { it.type == ConnectionType.TLS }
    val containsNonTls = any { it.type != ConnectionType.TLS }
    if (!containsTls || !containsNonTls) {
        return this
    }
    return sortedWith(compareBy { it.type != ConnectionType.TLS })
}
